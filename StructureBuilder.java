package dev.royalarms;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;

/** Builds themed weapon shrines: an altar showing the weapon and a wall showing its recipe. */
public final class StructureBuilder {

    private record Theme(Material[] floors, Material[] walls, Material pillar, Material accent,
                         Material light, Material altarFloor, Material altarPillar) { }

    private final RoyalArms plugin;
    private final AltarManager altars;
    private final NamespacedKey showcaseKey;

    public StructureBuilder(RoyalArms plugin, AltarManager altars) {
        this.plugin = plugin;
        this.altars = altars;
        this.showcaseKey = new NamespacedKey(plugin, "showcase");
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private Theme themeFor(Weapon w) {
        return switch (w) {
            case SHOCKWAVE -> new Theme(
                    new Material[]{Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER},
                    new Material[]{Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER, Material.EXPOSED_CUT_COPPER},
                    Material.EXPOSED_COPPER, Material.COPPER_BULB, Material.LIGHTNING_ROD,
                    Material.CHISELED_COPPER, Material.EXPOSED_COPPER);
            case LIFESTEALER -> new Theme(
                    new Material[]{Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS},
                    new Material[]{Material.RED_NETHER_BRICKS, Material.NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS},
                    Material.CHISELED_NETHER_BRICKS, Material.NETHER_WART_BLOCK, Material.SOUL_LANTERN,
                    Material.RED_NETHER_BRICKS, Material.CHISELED_NETHER_BRICKS);
            case WARDEN_BATTLEAXE -> new Theme(
                    new Material[]{Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_BRICKS},
                    new Material[]{Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE},
                    Material.CHISELED_DEEPSLATE, Material.SCULK, Material.SOUL_LANTERN,
                    Material.DEEPSLATE_TILES, Material.CHISELED_DEEPSLATE);
            case PRIMAL_SPEAR -> new Theme(
                    new Material[]{Material.MOSSY_STONE_BRICKS, Material.MOSS_BLOCK, Material.MOSSY_COBBLESTONE},
                    new Material[]{Material.MOSSY_STONE_BRICKS, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS},
                    Material.JUNGLE_LOG, Material.MOSS_BLOCK, Material.VERDANT_FROGLIGHT,
                    Material.MOSSY_STONE_BRICKS, Material.JUNGLE_LOG);
            case AFTERBURNER -> new Theme(
                    new Material[]{Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS,
                            Material.CRACKED_POLISHED_BLACKSTONE_BRICKS},
                    new Material[]{Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE, Material.GILDED_BLACKSTONE},
                    Material.CHISELED_POLISHED_BLACKSTONE, Material.MAGMA_BLOCK, Material.SHROOMLIGHT,
                    Material.POLISHED_BLACKSTONE_BRICKS, Material.GILDED_BLACKSTONE);
            case VOID_HAMMER -> new Theme(
                    new Material[]{Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, Material.CRYING_OBSIDIAN},
                    new Material[]{Material.PURPUR_BLOCK, Material.END_STONE_BRICKS, Material.PURPUR_PILLAR},
                    Material.PURPUR_PILLAR, Material.CRYING_OBSIDIAN, Material.END_ROD,
                    Material.OBSIDIAN, Material.PURPUR_PILLAR);
        };
    }

    /** Finds the top solid ground block at x,z (skips trees and plants). Returns null over water. */
    public Block findSurface(World w, int x, int z) {
        Block b = w.getHighestBlockAt(x, z);
        while (b.getY() > w.getMinHeight() + 1
                && (Tag.LEAVES.isTagged(b.getType())
                || Tag.LOGS.isTagged(b.getType())
                || (!b.getType().isSolid() && !b.isLiquid()))) {
            b = b.getRelative(BlockFace.DOWN);
        }
        if (b.isLiquid()) return null;
        return b;
    }

    // ---------- altar ----------

    /** Builds a 5x5 altar dedicated to a weapon, with the weapon floating above it. */
    public void buildAltar(Block ground, Weapon weapon) {
        Theme t = themeFor(weapon);
        World w = ground.getWorld();
        int cx = ground.getX();
        int g = ground.getY();
        int cz = ground.getZ();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(w, cx + dx, g, cz + dz, t.altarFloor());
                for (int dy = 1; dy <= 4; dy++) {
                    set(w, cx + dx, g + dy, cz + dz, Material.AIR);
                }
            }
        }

        set(w, cx + 1, g, cz, Material.CRYING_OBSIDIAN);
        set(w, cx - 1, g, cz, Material.CRYING_OBSIDIAN);
        set(w, cx, g, cz + 1, Material.CRYING_OBSIDIAN);
        set(w, cx, g, cz - 1, Material.CRYING_OBSIDIAN);

        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                set(w, cx + sx, g + 1, cz + sz, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            }
        }

        for (int sx : new int[]{-2, 2}) {
            for (int sz : new int[]{-2, 2}) {
                set(w, cx + sx, g + 1, cz + sz, t.altarPillar());
                set(w, cx + sx, g + 2, cz + sz, t.altarPillar());
                set(w, cx + sx, g + 3, cz + sz, t.altarPillar());
                set(w, cx + sx, g + 4, cz + sz, t.light());
            }
        }

        set(w, cx, g + 1, cz, Material.LODESTONE);
        Block core = w.getBlockAt(cx, g + 1, cz);
        altars.register(core, weapon);
        spawnShowcase(core, weapon);
    }

    /** A floating copy of the weapon above the altar core, with its name over it. */
    private void spawnShowcase(Block core, Weapon weapon) {
        removeShowcase(core);
        Location loc = core.getLocation().add(0.5, 1.4, 0.5);
        ItemStack stack = plugin.items().create(weapon, true);
        core.getWorld().dropItem(loc, stack, it -> {
            it.setVelocity(new Vector(0, 0, 0));
            it.setGravity(false);
            it.setPickupDelay(32767);
            it.setUnlimitedLifetime(true);
            it.setInvulnerable(true);
            it.setCanMobPickup(false);
            it.customName(Component.text(weapon.displayName, weapon.color));
            it.setCustomNameVisible(true);
            it.getPersistentDataContainer().set(showcaseKey, PersistentDataType.STRING, weapon.id);
        });
    }

    /** Removes the floating weapon and the recipe frames around an altar. */
    public void removeShowcase(Block core) {
        Location center = core.getLocation().add(0.5, 1.5, 0.5);
        for (Entity e : core.getWorld().getNearbyEntities(center, 4, 5, 4)) {
            if ((e instanceof Item || e instanceof GlowItemFrame)
                    && e.getPersistentDataContainer().has(showcaseKey, PersistentDataType.STRING)) {
                e.remove();
            }
        }
    }

    // ---------- recipe wall ----------

    private static Material firstMaterial(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice mc && !mc.getChoices().isEmpty()) {
            return mc.getChoices().get(0);
        }
        return null;
    }

    private void frame(World w, int x, int y, int z, ItemStack item, String id) {
        Location loc = new Location(w, x, y, z);
        w.spawn(loc, GlowItemFrame.class, f -> {
            f.setFacingDirection(BlockFace.NORTH, true);
            f.setItem(item, false);
            f.setFixed(true);
            f.setInvulnerable(true);
            f.setItemDropChance(0f);
            f.getPersistentDataContainer().set(showcaseKey, PersistentDataType.STRING, id);
        });
    }

    /** A wall of item frames showing the 3x3 recipe, with the weapon on a plate above it. */
    private void buildRecipeWall(World w, int cx, int g, int cz, Weapon weapon, Theme t) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                set(w, cx + dx, g + dy, cz + 3, t.walls()[0]);
            }
        }
        frame(w, cx, g + 4, cz + 2, plugin.items().create(weapon, true), weapon.id);

        ShapedRecipe recipe = plugin.recipeFor(weapon.id);
        if (recipe == null) return;
        String[] shape = recipe.getShape();
        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                char ch = (row < shape.length && col < shape[row].length()) ? shape[row].charAt(col) : ' ';
                if (ch == ' ') continue;
                Material m = firstMaterial(choices.get(ch));
                if (m == null) continue;
                // read left-to-right when you walk in from the north entrance
                frame(w, cx + 1 - col, g + 3 - row, cz + 2, new ItemStack(m), weapon.id);
            }
        }
    }

    // ---------- shrine ----------

    public void buildShrine(Block ground, Random rnd) {
        Weapon[] all = Weapon.values();
        buildShrine(ground, rnd, all[rnd.nextInt(all.length)]);
    }

    /** Builds an 11x11 themed shrine for one weapon, with an altar, recipe wall and loot chest. */
    public void buildShrine(Block ground, Random rnd, Weapon weapon) {
        Theme t = themeFor(weapon);
        World w = ground.getWorld();
        int cx = ground.getX();
        int g = ground.getY();
        int cz = ground.getZ();
        int r = 5;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int y = g - 5; y < g; y++) {
                    Block b = w.getBlockAt(cx + dx, y, cz + dz);
                    if (!b.getType().isSolid()) b.setType(t.walls()[0], false);
                }
                boolean border = Math.max(Math.abs(dx), Math.abs(dz)) == r;
                Material floor = border
                        ? t.walls()[rnd.nextInt(t.walls().length)]
                        : (rnd.nextDouble() < 0.12 ? t.accent() : t.floors()[rnd.nextInt(t.floors().length)]);
                set(w, cx + dx, g, cz + dz, floor);
                for (int dy = 1; dy <= 8; dy++) {
                    set(w, cx + dx, g + dy, cz + dz, Material.AIR);
                }
            }
        }

        // crumbling outer walls with an arched entrance on the north side
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                if (dz == -r && Math.abs(dx) <= 1) continue;
                for (int dy = 1; dy <= 4; dy++) {
                    if (rnd.nextDouble() < 1.0 - dy * 0.15) {
                        set(w, cx + dx, g + dy, cz + dz, t.walls()[rnd.nextInt(t.walls().length)]);
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            set(w, cx + dx, g + 3, cz - r, t.walls()[0]);
            set(w, cx + dx, g + 4, cz - r, t.walls()[0]);
        }
        for (int sx : new int[]{-2, 2}) {
            for (int dy = 1; dy <= 4; dy++) set(w, cx + sx, g + dy, cz - r, t.pillar());
        }

        // corner towers with a light on top
        for (int sx : new int[]{-r, r}) {
            for (int sz : new int[]{-r, r}) {
                for (int dy = 1; dy <= 6; dy++) set(w, cx + sx, g + dy, cz + sz, t.pillar());
                set(w, cx + sx, g + 7, cz + sz, t.light());
            }
        }

        buildAltar(w.getBlockAt(cx, g, cz), weapon);
        buildRecipeWall(w, cx, g, cz, weapon, t);

        // loot chest in the south-east corner
        Block chestBlock = w.getBlockAt(cx + 4, g + 1, cz + 4);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            inv.setItem(rnd.nextInt(27), new ItemStack(Material.IRON_INGOT, 3 + rnd.nextInt(6)));
            inv.setItem(rnd.nextInt(27), new ItemStack(Material.GOLD_INGOT, 2 + rnd.nextInt(5)));
            inv.setItem(rnd.nextInt(27), new ItemStack(Material.ARROW, 8 + rnd.nextInt(17)));
            if (rnd.nextDouble() < 0.6) {
                inv.setItem(rnd.nextInt(27), new ItemStack(Material.DIAMOND, 2 + rnd.nextInt(4)));
            }
            if (rnd.nextDouble() < 0.3) {
                inv.setItem(rnd.nextInt(27), new ItemStack(Material.GOLDEN_APPLE, 1));
            }
            if (rnd.nextDouble() < plugin.getConfig().getDouble("structures.artifact-chance", 0.35)) {
                Artifact[] all = Artifact.values();
                inv.addItem(plugin.artifacts().create(all[rnd.nextInt(all.length)]));
            }
        }
    }
}
