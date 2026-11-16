package dev.royalarms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Special artifacts: single-use-style utility items that work by right-click or /ability1. */
public final class ArtifactManager {

    private record Snapshot(Location loc, double health) { }

    private final RoyalArms plugin;
    private final NamespacedKey key;
    private final Map<UUID, Deque<Snapshot>> history = new HashMap<>();

    public ArtifactManager(RoyalArms plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "artifact");
    }

    public ItemStack create(Artifact a) {
        ItemStack item = new ItemStack(a.material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta lm) {
            lm.setColor(Color.fromRGB(25, 25, 25));
        }
        meta.displayName(Component.text(a.displayName, a.color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        if (plugin.getConfig().getBoolean("custom-models.enabled", true)) {
            NamespacedKey model = NamespacedKey.fromString("royalarms:" + a.id);
            if (model != null) meta.setItemModel(model);
        }
        List<Component> lore = new ArrayList<>();
        lore.add(line(a.description, NamedTextColor.GRAY));
        if (a.cooldownSeconds > 0) {
            lore.add(line("Cooldown: " + plugin.artifactCooldownSeconds(a) + "s", NamedTextColor.DARK_GRAY));
        }
        lore.add(line("Artifact. Also usable with /ability1.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, a.id);
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public Artifact artifactOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? null : Artifact.byId(id);
    }

    private static void msg(Player p, String text) {
        p.sendActionBar(Component.text(text, NamedTextColor.RED));
    }

    /** Uses the artifact in the main hand. Returns true if it fired. */
    public boolean use(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        Artifact a = artifactOf(hand);
        if (a == null) return false;

        if (a == Artifact.PHOENIX_FEATHER) {
            p.sendActionBar(Component.text("Passive: keep it in your inventory", a.color));
            return false;
        }
        String ckey = "artifact:" + a.id;
        long left = plugin.cooldownLeftMs(p, ckey);
        if (left > 0) {
            msg(p, String.format("%s recharging: %.1fs", a.displayName, left / 1000.0));
            return false;
        }

        boolean ok = switch (a) {
            case CLOAK_OF_SHADOWS -> cloak(p);
            case JUMBO_ROCKET -> rocket(p);
            case GALE_ROD -> gale(p);
            case CHRONO_CLOCK -> rewind(p);
            case TRACKER_COMPASS -> track(p);
            case BUCKET_OF_MALK -> malk(p);
            case BUCKET_OF_GREED -> greed(p);
            case PHOENIX_FEATHER -> false;
        };
        if (ok) plugin.setCooldown(p, ckey, plugin.artifactCooldownSeconds(a));
        return ok;
    }

    // ---------- abilities ----------

    private boolean cloak(Player p) {
        int sec = plugin.getConfig().getInt("artifacts.cloak_of_shadows.seconds", 15);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, sec * 20, 0, false, false, true));
        plugin.state().cloakUntil.put(p.getUniqueId(), System.currentTimeMillis() + sec * 1000L);
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
        p.sendActionBar(Component.text("Cloaked for " + sec + "s - attacking breaks it", NamedTextColor.DARK_GRAY));
        return true;
    }

    private boolean rocket(Player p) {
        p.setVelocity(new Vector(0, 1.8, 0));
        p.setFallDistance(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0));
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 40, 0.3, 0.1, 0.3, 0.1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        return true;
    }

    private boolean gale(Player p) {
        Vector dir = p.getLocation().getDirection().clone();
        dir.setY(0);
        if (dir.lengthSquared() < 0.0001) {
            msg(p, "Look forward to dash");
            return false;
        }
        for (Player o : p.getWorld().getNearbyPlayers(p.getLocation(), 3)) {
            if (o.equals(p) || o.getGameMode() == GameMode.SPECTATOR) continue;
            Vector away = o.getLocation().toVector().subtract(p.getLocation().toVector());
            if (away.lengthSquared() < 0.0001) continue;
            away.setY(0);
            away.normalize().multiply(0.9);
            away.setY(0.3);
            o.setVelocity(away);
        }
        Vector v = dir.normalize().multiply(1.6);
        v.setY(0.35);
        p.setVelocity(v);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.4, 0.2, 0.4, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
        return true;
    }

    /** Called about once a second while a player carries a Chrono Clock. */
    public void record(Player p) {
        Deque<Snapshot> h = history.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        h.addLast(new Snapshot(p.getLocation().clone(), p.getHealth()));
        while (h.size() > 5) h.removeFirst();
    }

    public void clear(UUID id) {
        history.remove(id);
    }

    private boolean rewind(Player p) {
        Deque<Snapshot> h = history.get(p.getUniqueId());
        if (h == null || h.size() < 2) {
            msg(p, "Not enough time recorded yet - carry the clock a few seconds");
            return false;
        }
        Snapshot s = h.peekFirst();
        if (!s.loc().getWorld().equals(p.getWorld())) {
            msg(p, "You changed worlds - nothing to rewind to");
            return false;
        }
        p.teleport(s.loc());
        p.setFallDistance(0);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        p.setHealth(Math.min(cap, Math.max(p.getHealth(), s.health())));
        history.remove(p.getUniqueId());
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 50, 0.4, 0.6, 0.4, 0.2);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        return true;
    }

    private boolean malk(Player p) {
        p.setFoodLevel(20);
        p.setSaturation(Math.min(20f, p.getSaturation() + 10f));
        PotionEffectType[] bad = {PotionEffectType.POISON, PotionEffectType.WITHER,
                PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS,
                PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE,
                PotionEffectType.LEVITATION, PotionEffectType.DARKNESS};
        for (PotionEffectType t : bad) p.removePotionEffect(t);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
        p.sendActionBar(Component.text("Malk! Hunger restored, bad effects cleansed", NamedTextColor.WHITE));
        return true;
    }

    private boolean greed(Player p) {
        double radius = plugin.getConfig().getDouble("artifacts.bucket_of_greed.radius", 12);
        int pulled = 0;
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
            if (e instanceof Item item) {
                item.teleport(p.getLocation());
                pulled++;
            }
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 400, 0));
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.5, 0.6, 0.5, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1f, 1f);
        p.sendActionBar(Component.text("Greed! Pulled " + pulled + " item" + (pulled == 1 ? "" : "s"),
                NamedTextColor.YELLOW));
        return true;
    }

    private boolean track(Player p) {
        double range = plugin.getConfig().getDouble("artifacts.tracker_compass.range", 150);
        Player best = null;
        double bestD = range * range;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || !o.getWorld().equals(p.getWorld())
                    || o.getGameMode() == GameMode.SPECTATOR) continue;
            double d = o.getLocation().distanceSquared(p.getLocation());
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        if (best == null) {
            msg(p, "No players within " + (int) range + " blocks");
            return false;
        }
        Vector to = best.getLocation().toVector().subtract(p.getLocation().toVector());
        double targetYaw = Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        double diff = targetYaw - p.getLocation().getYaw();
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        String side = diff > 0 ? "right" : "left";
        double ad = Math.abs(diff);
        String dir;
        if (ad < 22.5) dir = "ahead";
        else if (ad < 67.5) dir = "ahead " + side;
        else if (ad < 112.5) dir = side;
        else if (ad < 157.5) dir = "behind " + side;
        else dir = "behind";
        p.sendActionBar(Component.text(best.getName() + " - " + (int) Math.sqrt(bestD) + " blocks, " + dir,
                NamedTextColor.GREEN));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        return true;
    }
}
