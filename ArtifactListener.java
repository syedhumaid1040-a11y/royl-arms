package dev.royalarms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public final class ArtifactListener implements Listener {

    private final RoyalArms plugin;

    public ArtifactListener(RoyalArms plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.artifacts().artifactOf(e.getItem()) == null) return;

        Player p = e.getPlayer();
        Block clicked = e.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !p.isSneaking()) return;

        Artifact art = plugin.artifacts().artifactOf(e.getItem());
        boolean used = plugin.artifacts().use(p);
        // the buckets must never be poured or drunk, even while on cooldown
        if (used || art == Artifact.BUCKET_OF_MALK || art == Artifact.BUCKET_OF_GREED) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        ItemStack held = e.getPlayer().getInventory().getItem(e.getHand());
        if (plugin.artifacts().artifactOf(held) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (plugin.artifacts().artifactOf(e.getItem()) != null) e.setCancelled(true);
    }

    /** Attacking breaks the Cloak of Shadows. */
    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        CombatState st = plugin.state();
        if (!st.active(st.cloakUntil, id)) return;
        st.cloakUntil.remove(id);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.sendActionBar(Component.text("Cloak broken", NamedTextColor.DARK_GRAY));
    }

    /** Phoenix Feather: cancels one killing blow, then burns up. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        DamageCause c = e.getCause();
        if (c == DamageCause.VOID || c == DamageCause.KILL || c == DamageCause.SUICIDE) return;
        if (p.getHealth() + p.getAbsorptionAmount() - e.getFinalDamage() > 0) return;

        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (plugin.artifacts().artifactOf(it) != Artifact.PHOENIX_FEATHER) continue;

            if (it.getAmount() <= 1) p.getInventory().setItem(i, null);
            else {
                it.setAmount(it.getAmount() - 1);
                p.getInventory().setItem(i, it);
            }
            e.setCancelled(true);

            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            double cap = max == null ? 20 : max.getValue();
            p.setHealth(Math.min(cap, 6));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 160, 0));
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.3);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
            Bukkit.getServer().sendMessage(Component.text(
                    p.getName() + " was saved by a Phoenix Feather!", NamedTextColor.GOLD));
            return;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.artifacts().clear(e.getPlayer().getUniqueId());
    }
}
