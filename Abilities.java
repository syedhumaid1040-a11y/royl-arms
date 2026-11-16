package dev.royalarms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class Abilities {

    private final RoyalArms plugin;

    public Abilities(RoyalArms plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private static void msg(Player p, String text) {
        p.sendActionBar(Component.text(text, NamedTextColor.RED));
    }

    private static Predicate<Entity> targets(Player p) {
        return en -> en instanceof LivingEntity && !(en instanceof ArmorStand) && !en.equals(p);
    }

    /** Damage from an ability, flagged so it doesn't trigger melee passives or charge power. */
    public void abilityDamage(LivingEntity target, double amount, Player source) {
        UUID id = source.getUniqueId();
        plugin.state().suppressed.add(id);
        try {
            target.damage(amount, source);
        } finally {
            plugin.state().suppressed.remove(id);
        }
    }

    // ---------- entry point ----------

    /** Casts ability 1 or 2 of the weapon in the main hand. Returns true if it fired. */
    public boolean cast(Player p, int slot) {
        ItemStack item = p.getInventory().getItemInMainHand();
        Weapon w = plugin.items().weaponOf(item);
        if (w == null) return false;
        if (!checkReady(p, item, w, slot)) return false;

        boolean ok = switch (w) {
            case SHOCKWAVE -> slot == 1 ? surge(p) : armChain(p);
            case LIFESTEALER -> slot == 1 ? lifesteal(p) : bloodthirsty(p);
            case WARDEN_BATTLEAXE -> slot == 1 ? sensor(p) : groundPound(p);
            case PRIMAL_SPEAR -> slot == 1 ? spearThrow(p) : vines(p);
            case AFTERBURNER -> slot == 1 ? spitfire(p) : shuriken(p);
            case VOID_HAMMER -> slot == 1 ? hammerToss(p) : peekaboo(p);
        };
        if (ok) spend(p, w, slot);
        return ok;
    }

    private boolean checkReady(Player p, ItemStack item, Weapon w, int slot) {
        int need = slot == 1 ? 2 : 3;
        int stage = plugin.items().effectiveStage(item);
        if (stage < need) {
            int more = need - stage;
            msg(p, w.displayName + " locked: " + more + " more unique kill" + (more == 1 ? "" : "s"));
            return false;
        }
        int cost = slot == 1 ? w.cost1 : w.cost2;
        if (plugin.items().power(item) < cost) {
            msg(p, "Needs " + cost + " power - land hits to charge");
            return false;
        }
        long left = plugin.cooldownLeftMs(p, w, slot);
        if (left > 0) {
            msg(p, String.format("Ability %d recharging: %.1fs", slot, left / 1000.0));
            return false;
        }
        return true;
    }

    private void spend(Player p, Weapon w, int slot) {
        ItemStack item = p.getInventory().getItemInMainHand();
        int cost = slot == 1 ? w.cost1 : w.cost2;
        plugin.items().setPower(item, plugin.items().power(item) - cost);
        p.getInventory().setItemInMainHand(item);
        plugin.setCooldown(p, w, slot, plugin.cooldownSeconds(w, slot));
    }

    // ---------- Shockwave (mace) ----------

    private boolean surge(Player p) {
        int stacks = plugin.state().blitzStacks(p.getUniqueId());
        double up = 1.0 + 0.15 * stacks;
        Vector fwd = p.getLocation().getDirection().clone();
        fwd.setY(0);
        if (fwd.lengthSquared() > 0.0001) fwd.normalize().multiply(0.3);
        else fwd = new Vector(0, 0, 0);
        p.setVelocity(new Vector(fwd.getX(), up, fwd.getZ()));

        // freeze nearby enemies in place: no movement, no jumping
        int stunTicks = cfg().getInt("weapons.shockwave.stun-seconds", 1) * 20;
        for (LivingEntity o : p.getWorld().getNearbyLivingEntities(p.getLocation(), 4)) {
            if (o.equals(p) || o instanceof ArmorStand) continue;
            o.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stunTicks, 6));
            o.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, stunTicks, 200));
        }
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.5, 0.1, 0.5, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.6f);
        return true;
    }

    private boolean armChain(Player p) {
        plugin.state().chainArmed.put(p.getUniqueId(), System.currentTimeMillis() + 10_000);
        p.sendActionBar(Component.text("Next mace smash chains lightning", NamedTextColor.YELLOW));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
        return true;
    }

    /** Chain lightning from the first smashed target to up to 3 more nearby targets. */
    public void chain(Player p, LivingEntity first) {
        double dmg = cfg().getDouble("weapons.shockwave.chain-damage", 5);
        World w = first.getWorld();
        Set<UUID> hit = new HashSet<>();
        hit.add(first.getUniqueId());
        w.strikeLightningEffect(first.getLocation());

        LivingEntity current = first;
        for (int i = 0; i < 3; i++) {
            LivingEntity next = null;
            double best = 36;
            for (LivingEntity e : w.getNearbyLivingEntities(current.getLocation(), 6)) {
                if (e.equals(p) || e instanceof ArmorStand || hit.contains(e.getUniqueId())) continue;
                double d = e.getLocation().distanceSquared(current.getLocation());
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) break;
            hit.add(next.getUniqueId());
            w.strikeLightningEffect(next.getLocation());
            abilityDamage(next, dmg, p);
            current = next;
        }
    }

    // ---------- Lifestealer (sword) ----------

    private boolean lifesteal(Player p) {
        plugin.state().lifestealUntil.put(p.getUniqueId(), System.currentTimeMillis() + 10_000);
        p.sendActionBar(Component.text("Lifesteal active for 10s", NamedTextColor.RED));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.4f, 1.6f);
        return true;
    }

    private boolean bloodthirsty(Player p) {
        plugin.state().bloodUntil.put(p.getUniqueId(), System.currentTimeMillis() + 8_000);
        plugin.state().bloodSteals.put(p.getUniqueId(), 0);
        p.sendActionBar(Component.text("Bloodthirsty: your next hits steal hearts", NamedTextColor.DARK_RED));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.4f, 0.8f);
        return true;
    }

    // ---------- Warden Battleaxe ----------

    private boolean sensor(Player p) {
        int count = 0;
        for (Player o : p.getWorld().getNearbyPlayers(p.getLocation(), 20)) {
            if (o.equals(p) || o.getGameMode() == GameMode.SPECTATOR) continue;
            o.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
            count++;
        }
        if (count == 0) {
            msg(p, "No players nearby");
            return false;
        }
        // sculk soul particles around the head for the 10s duration
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 5;
                if (ticks > 200 || !p.isOnline()) {
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.SCULK_SOUL,
                        p.getEyeLocation().add(0, 0.4, 0), 3, 0.2, 0.1, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 5L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 1f);
        return true;
    }

    private boolean groundPound(Player p) {
        double dmg = cfg().getDouble("weapons.warden_battleaxe.sonic-damage", 7);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();

        double len = 15;
        RayTraceResult b = w.rayTraceBlocks(eye, dir, len, FluidCollisionMode.NEVER, true);
        if (b != null) len = eye.toVector().distance(b.getHitPosition());

        RayTraceResult r = w.rayTraceEntities(eye, dir, len, 0.8, targets(p));
        if (r != null && r.getHitEntity() instanceof LivingEntity t) {
            len = eye.toVector().distance(r.getHitPosition());
            // sonic boom damage ignores armor
            DamageSource src = DamageSource.builder(DamageType.SONIC_BOOM)
                    .withCausingEntity(p).withDirectEntity(p).build();
            UUID id = p.getUniqueId();
            plugin.state().suppressed.add(id);
            try {
                t.damage(dmg, src);
            } finally {
                plugin.state().suppressed.remove(id);
            }
            // kills spread sculk
            if (t.isDead() || t.getHealth() <= 0) spreadSculk(t.getLocation());
        }
        for (double d = 1; d <= len; d += 1.5) {
            w.spawnParticle(Particle.SONIC_BOOM, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
        return true;
    }

    private void spreadSculk(Location center) {
        if (!cfg().getBoolean("weapons.warden_battleaxe.spread-sculk", true)) return;
        Set<Material> replaceable = Set.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
                Material.PODZOL, Material.STONE, Material.DEEPSLATE, Material.COBBLESTONE,
                Material.SAND, Material.GRAVEL);
        World w = center.getWorld();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) continue;
                Block b = w.getBlockAt(center.getBlockX() + dx, center.getBlockY() - 1, center.getBlockZ() + dz);
                if (replaceable.contains(b.getType())) b.setType(Material.SCULK, false);
            }
        }
    }

    // ---------- Primal Spear ----------

    private boolean spearThrow(Player p) {
        double dmg = cfg().getDouble("weapons.primal_spear.throw-damage", 7);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();

        double len = 20;
        RayTraceResult b = w.rayTraceBlocks(eye, dir, len, FluidCollisionMode.NEVER, true);
        if (b != null) len = eye.toVector().distance(b.getHitPosition());

        RayTraceResult r = w.rayTraceEntities(eye, dir, len, 0.6, targets(p));
        if (r != null && r.getHitEntity() instanceof LivingEntity t) {
            len = eye.toVector().distance(r.getHitPosition());
            abilityDamage(t, dmg, p);
            t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1));
        }
        for (double d = 1; d <= len; d += 1) {
            w.spawnParticle(Particle.CRIT, eye.clone().add(dir.clone().multiply(d)), 2, 0.05, 0.05, 0.05, 0);
        }
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1f);
        return true;
    }

    private boolean vines(Player p) {
        int seconds = cfg().getInt("weapons.primal_spear.vines-seconds", 3);
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceEntities(eye, eye.getDirection(), 15, 0.8, targets(p));
        if (r == null || !(r.getHitEntity() instanceof LivingEntity t)) {
            msg(p, "No target in range");
            return false;
        }
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 5));
        Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
        if (pull.lengthSquared() > 0.01) {
            pull.normalize().multiply(1.0);
            pull.setY(0.3);
            t.setVelocity(pull);
        }
        t.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, t.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1f);
        return true;
    }

    // ---------- Afterburner ----------

    private LivingEntity findTarget(Player p, double range) {
        Vector dir = p.getEyeLocation().getDirection();
        LivingEntity best = null;
        double bestDot = 0.9;
        for (LivingEntity e : p.getWorld().getNearbyLivingEntities(p.getLocation(), range)) {
            if (e.equals(p) || e instanceof ArmorStand) continue;
            Vector to = e.getLocation().add(0, 1, 0).toVector().subtract(p.getEyeLocation().toVector());
            if (to.lengthSquared() < 0.01) continue;
            double dot = dir.dot(to.normalize());
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    private void burn(LivingEntity target, double damage, Player source) {
        // burns through Fire Resistance
        target.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        abilityDamage(target, damage, source);
        target.setFireTicks(100);
    }

    private boolean spitfire(Player p) {
        final LivingEntity target = findTarget(p, 20);
        final double damage = cfg().getDouble("weapons.afterburner.orb-damage", 5);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);

        new BukkitRunnable() {
            final Location loc = p.getEyeLocation();
            Vector dir = p.getEyeLocation().getDirection();
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > 40) {
                    cancel();
                    return;
                }
                if (target != null && target.isValid()) {
                    Vector to = target.getLocation().add(0, 1, 0).toVector().subtract(loc.toVector());
                    if (to.lengthSquared() > 0.01) {
                        dir = dir.clone().multiply(0.7).add(to.normalize().multiply(0.3)).normalize();
                    }
                }
                loc.add(dir.clone().multiply(1.2));
                World w = loc.getWorld();
                if (!loc.getBlock().isPassable()) {
                    w.spawnParticle(Particle.LAVA, loc, 10, 0.2, 0.2, 0.2, 0);
                    cancel();
                    return;
                }
                w.spawnParticle(Particle.FLAME, loc, 4, 0.05, 0.05, 0.05, 0.01);
                for (LivingEntity e : w.getNearbyLivingEntities(loc, 0.8)) {
                    if (e.equals(p) || e instanceof ArmorStand) continue;
                    burn(e, damage, p);
                    w.spawnParticle(Particle.LAVA, loc, 15, 0.3, 0.3, 0.3, 0);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean shuriken(Player p) {
        final double damage = cfg().getDouble("weapons.afterburner.shuriken-damage", 6);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.4f);

        new BukkitRunnable() {
            final Location loc = p.getEyeLocation();
            final Vector dir = p.getEyeLocation().getDirection();
            int ticks = 0;
            boolean returning = false;
            final Set<UUID> hit = new HashSet<>();

            /** Piercing: every entity is burned once per throw, the blade keeps flying. */
            void pierce(World w) {
                for (LivingEntity e : w.getNearbyLivingEntities(loc, 0.9)) {
                    if (e.equals(p) || e instanceof ArmorStand) continue;
                    if (hit.add(e.getUniqueId())) burn(e, damage, p);
                }
            }

            @Override
            public void run() {
                ticks++;
                World w = loc.getWorld();
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                if (!returning) {
                    loc.add(dir.clone().multiply(1.2));
                    if (!loc.getBlock().isPassable() || ticks > 10) {
                        returning = true;
                        ticks = 0;
                        return;
                    }
                    w.spawnParticle(Particle.FLAME, loc, 3, 0.05, 0.05, 0.05, 0.01);
                    w.spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
                    pierce(w);
                } else {
                    Vector back = p.getEyeLocation().toVector().subtract(loc.toVector());
                    if (ticks > 14 || back.lengthSquared() < 1) {
                        cancel();
                        return;
                    }
                    loc.add(back.normalize().multiply(1.5));
                    w.spawnParticle(Particle.FLAME, loc, 2, 0.05, 0.05, 0.05, 0.01);
                    pierce(w);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ---------- Void Hammer (mace) ----------

    private boolean hammerToss(Player p) {
        double max = cfg().getDouble("weapons.void_hammer.toss-range", 20);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();

        RayTraceResult b = w.rayTraceBlocks(eye, dir, max, FluidCollisionMode.NEVER, true);
        double dist = b == null ? max : Math.max(0, eye.toVector().distance(b.getHitPosition()) - 0.7);

        RayTraceResult r = w.rayTraceEntities(eye, dir, dist, 0.6, targets(p));
        if (r != null && r.getHitEntity() instanceof LivingEntity t) {
            abilityDamage(t, 4, p);
            dist = Math.max(0, eye.toVector().distance(r.getHitPosition()) - 1.0);
        }

        Location dest = p.getLocation().add(dir.clone().multiply(dist));
        if (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()) {
            msg(p, "No room to land");
            return false;
        }
        for (double d = 0; d < dist; d += 1) {
            w.spawnParticle(Particle.END_ROD, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
        p.teleport(dest);
        w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        return true;
    }

    /** Opens the player picker. Power and cooldown are only spent when a target is chosen. */
    private boolean peekaboo(Player p) {
        double range = cfg().getDouble("weapons.void_hammer.peekaboo-range", 40);
        List<Player> near = p.getWorld().getNearbyPlayers(p.getLocation(), range).stream()
                .filter(o -> !o.equals(p) && o.getGameMode() != GameMode.SPECTATOR)
                .limit(27)
                .toList();
        if (near.isEmpty()) {
            msg(p, "No players nearby");
            return false;
        }
        new PeekabooGui(near).open(p);
        return false; // nothing spent yet
    }

    public void peekabooTeleport(Player p, Player target) {
        ItemStack item = p.getInventory().getItemInMainHand();
        Weapon w = plugin.items().weaponOf(item);
        if (w != Weapon.VOID_HAMMER) return;
        if (!checkReady(p, item, w, 2)) return;
        if (!target.isOnline() || !target.getWorld().equals(p.getWorld())) {
            msg(p, "That player is no longer here");
            return;
        }
        int height = cfg().getInt("weapons.void_hammer.peekaboo-height", 8);
        Location dest = null;
        for (int h = height; h >= 2; h -= 2) {
            Location test = target.getLocation().add(0, h, 0);
            if (test.getY() < test.getWorld().getMaxHeight() - 2
                    && test.getBlock().isPassable()
                    && test.clone().add(0, 1, 0).getBlock().isPassable()) {
                dest = test;
                break;
            }
        }
        if (dest == null) {
            msg(p, "No room above that player");
            return;
        }
        p.teleport(dest);
        p.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        spend(p, w, 2);
    }
}
