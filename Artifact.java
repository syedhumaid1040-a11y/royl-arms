package dev.royalarms;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum Artifact {
    CLOAK_OF_SHADOWS("cloak_of_shadows", Material.LEATHER_HORSE_ARMOR, "Cloak of Shadows",
            NamedTextColor.DARK_GRAY, 90,
            "Right-click: turn invisible for 15s. Attacking breaks it.",
            "4 Phantom Membrane, 2 Echo Shard, 2 Netherite Ingot, Nether Star"),
    JUMBO_ROCKET("jumbo_rocket", Material.FIREWORK_STAR, "Jumbo Rocket",
            NamedTextColor.RED, 60,
            "Right-click: blast high into the sky, then glide down gently.",
            "4 Firework Rocket, 4 TNT, Gunpowder or Creeper Head"),
    PHOENIX_FEATHER("phoenix_feather", Material.FEATHER, "Phoenix Feather",
            NamedTextColor.GOLD, 0,
            "Passive: carry it in your inventory. A killing blow is cancelled once, then it burns up.",
            "4 Blaze Rod, 3 Feather, Totem of Undying, Nether Star"),
    GALE_ROD("gale_rod", Material.BREEZE_ROD, "Gale Rod",
            NamedTextColor.AQUA, 20,
            "Right-click: dash forward and knock back nearby players.",
            "4 Wind Charge, 2 Breeze Rod, 2 Netherite Ingot, Heavy Core"),
    CHRONO_CLOCK("chrono_clock", Material.CLOCK, "Chrono Clock",
            NamedTextColor.LIGHT_PURPLE, 120,
            "Right-click: rewind to where you were about 5 seconds ago.",
            "4 Echo Shard, 3 Netherite Ingot, Clock, Nether Star"),
    TRACKER_COMPASS("tracker_compass", Material.COMPASS, "Tracker Compass",
            NamedTextColor.GREEN, 30,
            "Right-click: shows the nearest player's distance and direction.",
            "4 Ender Eye, 2 Diamond Block, 2 Netherite Ingot, Recovery Compass"),
    BUCKET_OF_MALK("bucket_of_malk", Material.MILK_BUCKET, "Bucket of Malk",
            NamedTextColor.WHITE, 60,
            "Right-click: full hunger, big saturation and a cleanse of bad effects.",
            "Milk Bucket, 4 Golden Apple, 4 Sugar"),
    BUCKET_OF_GREED("bucket_of_greed", Material.LAVA_BUCKET, "Bucket of Greed",
            NamedTextColor.YELLOW, 60,
            "Right-click: pull nearby dropped items to you and gain Fire Resistance.",
            "Lava Bucket, 4 Gold Block, 4 Emerald Block");

    public final String id;
    public final Material material;
    public final String displayName;
    public final NamedTextColor color;
    public final int cooldownSeconds;
    public final String description;
    public final String recipe;

    Artifact(String id, Material material, String displayName, NamedTextColor color,
             int cooldownSeconds, String description, String recipe) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.color = color;
        this.cooldownSeconds = cooldownSeconds;
        this.description = description;
        this.recipe = recipe;
    }

    public static Artifact byId(String id) {
        for (Artifact a : values()) {
            if (a.id.equalsIgnoreCase(id)) return a;
        }
        return null;
    }
}
