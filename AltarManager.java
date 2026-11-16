package dev.royalarms;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Remembers every shrine altar and which weapon it is dedicated to. */
public final class AltarManager {

    public record Altar(String world, int x, int y, int z, String weaponId) { }

    private final RoyalArms plugin;
    private final File file;
    private final Map<String, Altar> altars = new HashMap<>();

    public AltarManager(RoyalArms plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "altars.yml");
        load();
    }

    private static String key(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private void load() {
        if (!file.exists()) return;
        List<String> lines = YamlConfiguration.loadConfiguration(file).getStringList("altars");
        for (String line : lines) {
            int eq = line.lastIndexOf('=');
            String left = eq < 0 ? line : line.substring(0, eq);
            String weapon = eq < 0 ? Weapon.SHOCKWAVE.id : line.substring(eq + 1);
            String[] p = left.split(",");
            if (p.length < 4) continue;
            int n = p.length;
            try {
                String world = String.join(",", Arrays.copyOfRange(p, 0, n - 3));
                int x = Integer.parseInt(p[n - 3]);
                int y = Integer.parseInt(p[n - 2]);
                int z = Integer.parseInt(p[n - 1]);
                altars.put(left, new Altar(world, x, y, z, weapon));
            } catch (NumberFormatException ignored) {
                // skip corrupt line
            }
        }
    }

    private void save() {
        try {
            plugin.getDataFolder().mkdirs();
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, Altar> en : altars.entrySet()) {
                lines.add(en.getKey() + "=" + en.getValue().weaponId());
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("altars", lines);
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save altars.yml: " + ex.getMessage());
        }
    }

    public void register(Block core, Weapon weapon) {
        altars.put(key(core), new Altar(core.getWorld().getName(),
                core.getX(), core.getY(), core.getZ(), weapon.id));
        save();
    }

    public void unregister(Block core) {
        if (altars.remove(key(core)) != null) save();
    }

    public boolean isAltar(Block core) {
        return altars.containsKey(key(core));
    }

    public Weapon weaponAt(Block core) {
        Altar a = altars.get(key(core));
        return a == null ? null : Weapon.byId(a.weaponId());
    }

    /** The closest altar within the radius, or null. */
    public Altar nearest(Location loc, double radius) {
        if (loc.getWorld() == null) return null;
        String world = loc.getWorld().getName();
        Altar best = null;
        double bestD = radius * radius;
        for (Altar a : altars.values()) {
            if (!a.world().equals(world)) continue;
            double dx = a.x() + 0.5 - loc.getX();
            double dy = a.y() - loc.getY();
            double dz = a.z() + 0.5 - loc.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = a;
            }
        }
        return best;
    }
}
