package dev.royalarms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WeaponListener implements Listener {

    private final RoyalArms plugin;

    public WeaponListener(RoyalArms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.timers().show(e.getPlayer());
        plugin.packs().sendTo(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.state().clear(e.getPlayer().getUniqueId());
    }

    /** Right-click = ability 1, sneak + right-click = ability 2. */
    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.items().weaponOf(e.getItem()) == null) return;

        Player p = e.getPlayer();
        // let players still open chests, doors, etc. with a weapon in hand
        Block clicked = e.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !p.isSneaking()) return;

        if (plugin.abilities().cast(p, p.isSneaking() ? 2 : 1)) {
            e.setCancelled(true);
        }
    }

    /** Melee hits: charge power and apply passives. */
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        UUID id = p.getUniqueId();
        CombatState st = plugin.state();
        if (st.suppressed.contains(id)) return; // ability damage

        ItemStack item = p.getInventory().getItemInMainHand();
        Weapon w = plugin.items().weaponOf(item);
        if (w == null) return;

        boolean smash = p.getFallDistance() > 1.5f
                && (w == Weapon.SHOCKWAVE || w == Weapon.VOID_HAMMER);

        // power charging: a smash fills a whole bar, otherwise count hits
        int perBar = Math.max(1, plugin.getConfig().getInt("power.hits-per-bar", 5));
        int counter = st.hitCounter.getOrDefault(id, 0) + (smash ? perBar : 1);
        if (counter >= perBar) {
            counter = 0;
            if (plugin.items().power(item) < 3) {
                plugin.items().setPower(item, plugin.items().power(item) + 1);
                p.getInventory().setItemInMainHand(item);
            }
        }
        st.hitCounter.put(id, counter);

        if (plugin.items().effectiveStage(item) < 1) return; // passives locked

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        switch (w) {
            case SHOCKWAVE -> {
                if (!smash) return;
                st.addBlitz(id);
                if (st.active(st.chainArmed, id)) {
                    st.chainArmed.remove(id);
                    plugin.abilities().chain(p, victim);
                }
            }
            case LIFESTEALER -> {
                if (st.active(st.lifestealUntil, id)) {
                    double heal = e.getDamage() * plugin.getConfig()
                            .getDouble("weapons.lifestealer.heal-percent", 0.3);
                    AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                    double cap = max == null ? 20 : max.getValue();
                    p.setHealth(Math.min(cap, p.getHealth() + heal));
                }
                int steals = st.bloodSteals.getOrDefault(id, 0);
                if (st.active(st.bloodUntil, id) && steals < 4) {
                    st.bloodSteals.put(id, steals + 1);
                    e.setDamage(e.getDamage() + 2);
                    p.setAbsorptionAmount(Math.min(8, p.getAbsorptionAmount() + 2));
                }
                if (rnd.nextDouble() < plugin.getConfig().getDouble("weapons.lifestealer.venom-chance", 0.15)) {
                    PotionEffectType[] bad = {PotionEffectType.POISON,
                            PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS};
                    victim.addPotionEffect(new PotionEffect(bad[rnd.nextInt(bad.length)], 60, 0));
                }
            }
            case VOID_HAMMER -> {
                if (rnd.nextDouble() < plugin.getConfig().getDouble("weapons.void_hammer.levitation-chance", 0.15)) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0));
                }
            }
            case PRIMAL_SPEAR -> {
                // Agility: forward lunge on hits
                Vector fwd = p.getLocation().getDirection().clone();
                fwd.setY(0);
                if (fwd.lengthSquared() > 0.0001) {
                    p.setVelocity(p.getVelocity().add(fwd.normalize().multiply(0.35)));
                }
            }
            default -> { }
        }
    }

    /** 3 unique player kills unlock a weapon stage by stage. */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        if (!plugin.items().unlockEnabled()) return;

        ItemStack item = killer.getInventory().getItemInMainHand();
        Weapon w = plugin.items().weaponOf(item);
        if (w == null) return;

        if (plugin.items().recordKill(item, victim.getUniqueId())) {
            killer.getInventory().setItemInMainHand(item);
            int stage = plugin.items().stage(item);
            String what = switch (stage) {
                case 1 -> "passive unlocked";
                case 2 -> "ability 1 unlocked";
                default -> "ability 2 unlocked - fully awakened";
            };
            killer.sendMessage(Component.text(w.displayName + ": " + what + " (" + stage + "/3 kills)",
                    NamedTextColor.GOLD));
        }
    }

    /** Warden Whisperer: immune to attacks (including sonic booms) from Wardens. */
    @EventHandler(ignoreCancelled = true)
    public void onWardenDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Warden)) return;
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (plugin.items().weaponOf(hand) == Weapon.WARDEN_BATTLEAXE
                && plugin.items().effectiveStage(hand) >= 1) {
            e.setCancelled(true);
        }
    }

    /** Warden Whisperer: Wardens ignore the Warden Battleaxe's holder. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof Warden)) return;
        if (!(e.getTarget() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (plugin.items().weaponOf(hand) == Weapon.WARDEN_BATTLEAXE
                && plugin.items().effectiveStage(hand) >= 1) {
            e.setCancelled(true);
        }
    }
}
