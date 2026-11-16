package dev.royalarms;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum Weapon {
    SHOCKWAVE("shockwave", Material.MACE, "Shockwave", NamedTextColor.YELLOW, 1, 3,
            "Surge: lunge upward, freezing nearby enemies in place",
            "Shockwave (ultimate): next smash unleashes chain lightning across nearby targets",
            "Blitz: launch higher with every consecutive mace smash",
            "2 Heavy Core, 3 Netherite Ingot, 2 Breeze Rod, Mace, Nether Star"),
    LIFESTEALER("lifestealer", Material.NETHERITE_SWORD, "Lifestealer", NamedTextColor.RED, 1, 3,
            "Lifesteal: heals you based on the damage you deal",
            "Bloodthirsty (ultimate): temporary health-stealing strikes",
            "Venom: melee hits may inflict random negative effects",
            "2 Wither Skeleton Skull, 3 Netherite Ingot, 2 Enchanted Golden Apple, Netherite Sword, Nether Star"),
    WARDEN_BATTLEAXE("warden_battleaxe", Material.NETHERITE_AXE, "Warden Battleaxe", NamedTextColor.DARK_AQUA, 1, 2,
            "Sensor: sculk horns make every nearby enemy glow",
            "Ground Pound (ultimate): armor-piercing sonic boom, kills spread sculk",
            "Warden Whisperer: immune to Warden attacks",
            "4 Echo Shard, Sculk Catalyst, Sculk Shrieker, 2 Netherite Ingot, Netherite Axe"),
    PRIMAL_SPEAR("primal_spear", spearMaterial(), "Primal Spear", NamedTextColor.GREEN, 1, 2,
            "Throw: hurl the spear for heavy damage and Poison II",
            "Vines (ultimate): snare and pull targets toward you",
            "Agility: Speed II while carried, plus a forward lunge on hits",
            "2 Fermented Spider Eye, Weeping Vines, 2 Netherite Ingot, Netherite Spear, 2 Pufferfish, Nether Star"),
    AFTERBURNER("afterburner", Material.NETHERITE_SWORD, "Afterburner", NamedTextColor.GOLD, 1, 2,
            "Spitfire: homing magma orbs",
            "Blazing Shuriken (ultimate): piercing, returning fire shurikens",
            "Fireborn: permanent Fire Resistance while carried",
            "4 Blaze Rod, Ghast Tear, 2 Netherite Ingot, Netherite Sword, Nether Star"),
    VOID_HAMMER("void_hammer", Material.MACE, "Void Hammer", NamedTextColor.DARK_PURPLE, 1, 3,
            "Hammer Toss: throw the hammer and teleport to where it lands",
            "Peekaboo: pick a player and teleport high above them",
            "Afloat: chance on hit to apply Levitation",
            "4 Ender Eye, Dragon's Breath, 2 Netherite Ingot, Mace, Nether Star");

    public final String id;
    public final Material material;
    public final String displayName;
    public final NamedTextColor color;
    public final int cost1;
    public final int cost2;
    public final String ability1;
    public final String ability2;
    public final String passive;
    public final String recipe;

    Weapon(String id, Material material, String displayName, NamedTextColor color,
           int cost1, int cost2, String ability1, String ability2, String passive, String recipe) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.color = color;
        this.cost1 = cost1;
        this.cost2 = cost2;
        this.ability1 = ability1;
        this.ability2 = ability2;
        this.passive = passive;
        this.recipe = recipe;
    }

    /** Real spear item on versions that have it, otherwise an iron sword. */
    private static Material spearMaterial() {
        Material m = Material.matchMaterial("NETHERITE_SPEAR");
        return m != null ? m : Material.IRON_SWORD;
    }

    public static Weapon byId(String id) {
        for (Weapon w : values()) {
            if (w.id.equalsIgnoreCase(id)) return w;
        }
        return null;
    }
}
