package dev.royalarms;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RoyalArms extends JavaPlugin {

    private WeaponItems items;
    private CombatState state;
    private Abilities abilities;
    private TimerManager timers;
    private AltarManager altars;
    private StructureBuilder structures;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private final Map<String, ShapedRecipe> shapedRecipes = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private ArtifactManager artifacts;
    private PackManager packs;
    private int tickCount;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new WeaponItems(this);
        state = new CombatState();
        abilities = new Abilities(this);
        artifacts = new ArtifactManager(this);
        timers = new TimerManager(this);
        altars = new AltarManager(this);
        structures = new StructureBuilder(this, altars);
        try {
            registerRecipes();
        } catch (RuntimeException ex) {
            getLogger().warning("Recipe setup hit an error, some recipes may be missing: " + ex);
        }
        packs = new PackManager(this);
        try {
            packs.start();
        } catch (RuntimeException ex) {
            getLogger().warning("Resource pack setup hit an error: " + ex);
        }

        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new AltarListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ArtifactListener(this), this);

        ArmsCommand cmd = new ArmsCommand(this);
        PluginCommand arms = getCommand("arms");
        if (arms != null) {
            arms.setExecutor(cmd);
            arms.setTabCompleter(cmd);
        }
        bind("arsenal", new ArsenalCommand(this));
        bind("recipes", new ArsenalCommand(this));
        bind("ability1", new AbilityCommand(this, 1));
        bind("ability2", new AbilityCommand(this, 2));

        Bukkit.getScheduler().runTaskTimer(this, this::tick, 5L, 5L);
        getLogger().info("RoyalArms ready: " + shapedRecipes.size() + " recipes registered.");
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand c = getCommand(name);
        if (c != null) c.setExecutor(executor);
        else getLogger().warning("Command /" + name + " is missing from plugin.yml");
    }

    @Override
    public void onDisable() {
        timers.stop();
        if (packs != null) packs.stop();
        for (NamespacedKey k : recipeKeys) {
            Bukkit.removeRecipe(k);
        }
    }

    public WeaponItems items() {
        return items;
    }

    public CombatState state() {
        return state;
    }

    public Abilities abilities() {
        return abilities;
    }

    public PackManager packs() {
        return packs;
    }

    public ArtifactManager artifacts() {
        return artifacts;
    }

    public ShapedRecipe recipeFor(String id) {
        return shapedRecipes.get(id);
    }

    public TimerManager timers() {
        return timers;
    }

    public AltarManager altars() {
        return altars;
    }

    public StructureBuilder structures() {
        return structures;
    }

    /** A fresh weapon as crafted/rewarded: locked unless kill unlocks are off. */
    public ItemStack createItem(Weapon w) {
        return items.create(w, !items.unlockEnabled());
    }

    // ---------- cooldowns ----------

    private String ckey(Player p, Weapon w, int slot) {
        return p.getUniqueId() + ":" + w.id + ":" + slot;
    }

    public long cooldownLeftMs(Player p, Weapon w, int slot) {
        Long until = cooldowns.get(ckey(p, w, slot));
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void setCooldown(Player p, Weapon w, int slot, int seconds) {
        cooldowns.put(ckey(p, w, slot), System.currentTimeMillis() + seconds * 1000L);
    }

    public int cooldownSeconds(Weapon w, int slot) {
        int fallback = slot == 1 ? 15 : 30;
        return getConfig().getInt("weapons." + w.id + ".cooldown" + slot, fallback);
    }

    public long cooldownLeftMs(Player p, String key) {
        Long until = cooldowns.get(p.getUniqueId() + ":" + key);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void setCooldown(Player p, String key, int seconds) {
        cooldowns.put(p.getUniqueId() + ":" + key, System.currentTimeMillis() + seconds * 1000L);
    }

    public int artifactCooldownSeconds(Artifact a) {
        return getConfig().getInt("artifacts." + a.id + ".cooldown", a.cooldownSeconds);
    }

    // ---------- periodic task: action bar + passives ----------

    private void tick() {
        tickCount++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            Weapon held = items.weaponOf(hand);

            // Fireborn: Fire Resistance while carrying the Afterburner
            boolean fireborn = false;
            boolean agility = false;
            boolean hasChrono = false;
            for (ItemStack it : p.getInventory().getContents()) {
                if (it == null) continue;
                if (!fireborn && items.weaponOf(it) == Weapon.AFTERBURNER && items.effectiveStage(it) >= 1) {
                    fireborn = true;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false, false));
                }
                // Agility: Speed II while the Primal Spear is carried
                if (!agility && items.weaponOf(it) == Weapon.PRIMAL_SPEAR && items.effectiveStage(it) >= 1) {
                    agility = true;
                    int amp = getConfig().getInt("weapons.primal_spear.speed-amplifier", 1);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, amp, true, false, false));
                }
                if (artifacts.artifactOf(it) == Artifact.CHRONO_CLOCK) hasChrono = true;
            }
            // Chrono Clock records your position about once a second while carried
            if (hasChrono && tickCount % 4 == 0) artifacts.record(p);

            if (held != null) {
                p.sendActionBar(statusLine(p, hand, held));
            } else {
                Artifact art = artifacts.artifactOf(hand);
                if (art != null) {
                    p.sendActionBar(artifactLine(p, art));
                } else if (tickCount % 4 == 0) {
                    // walking up to a shrine shows which weapon it holds
                    AltarManager.Altar near = altars.nearest(p.getLocation(), 8);
                    Weapon shown = near == null ? null : Weapon.byId(near.weaponId());
                    if (shown != null) {
                        p.sendActionBar(Component.text("Shrine of the " + shown.displayName
                                + " - right-click the altar for its recipe", shown.color));
                    }
                }
            }
        }
    }

    private Component statusLine(Player p, ItemStack hand, Weapon w) {
        int power = items.power(hand);
        int stage = items.effectiveStage(hand);
        StringBuilder sb = new StringBuilder(w.displayName).append("  ");
        for (int i = 0; i < 3; i++) sb.append(i < power ? "■" : "□");
        sb.append("  ");
        if (stage < 3) sb.append("Kills ").append(stage).append("/3  ");
        if (stage >= 2) sb.append(cdText("A1", cooldownLeftMs(p, w, 1)));
        if (stage >= 3) sb.append("  ").append(cdText("A2", cooldownLeftMs(p, w, 2)));
        return Component.text(sb.toString(), w.color);
    }

    private Component artifactLine(Player p, Artifact a) {
        if (a == Artifact.PHOENIX_FEATHER) return Component.text(a.displayName + "  passive", a.color);
        long left = cooldownLeftMs(p, "artifact:" + a.id);
        String text = left > 0 ? String.format("%s  %.1fs", a.displayName, left / 1000.0)
                : a.displayName + "  ready";
        return Component.text(text, a.color);
    }

    private static String cdText(String name, long leftMs) {
        return leftMs > 0 ? String.format("%s %.1fs", name, leftMs / 1000.0) : name + " ready";
    }

    // ---------- recipes ----------

    private void registerRecipes() {
        if (!getConfig().getBoolean("recipes.enabled", true)) return;

        // ---- legendary weapons: every one needs a Nether Star or an Ancient City drop ----
        addRecipe(Weapon.SHOCKWAVE, new String[]{"HNH", "BMB", "NSN"}, Map.of(
                'H', Material.HEAVY_CORE,
                'N', Material.NETHERITE_INGOT,
                'B', Material.BREEZE_ROD,
                'M', Material.MACE,
                'S', Material.NETHER_STAR));

        addRecipe(Weapon.LIFESTEALER, new String[]{"WNW", "ESE", "NTN"}, Map.of(
                'W', Material.WITHER_SKELETON_SKULL,
                'N', Material.NETHERITE_INGOT,
                'E', Material.ENCHANTED_GOLDEN_APPLE,
                'S', Material.NETHERITE_SWORD,
                'T', Material.NETHER_STAR));

        addRecipe(Weapon.WARDEN_BATTLEAXE, new String[]{"ECE", "NAN", "ESE"}, Map.of(
                'E', Material.ECHO_SHARD,
                'C', Material.SCULK_CATALYST,
                'N', Material.NETHERITE_INGOT,
                'A', Material.NETHERITE_AXE,
                'S', Material.SCULK_SHRIEKER));

        addRecipe(Weapon.PRIMAL_SPEAR, new String[]{"FVF", "NSN", "PXP"}, Map.of(
                'F', Material.FERMENTED_SPIDER_EYE,
                'V', Material.WEEPING_VINES,
                'N', Material.NETHERITE_INGOT,
                'S', Weapon.PRIMAL_SPEAR.material,
                'P', Material.PUFFERFISH,
                'X', Material.NETHER_STAR));

        addRecipe(Weapon.AFTERBURNER, new String[]{"BGB", "NSN", "BXB"}, Map.of(
                'B', Material.BLAZE_ROD,
                'G', Material.GHAST_TEAR,
                'N', Material.NETHERITE_INGOT,
                'S', Material.NETHERITE_SWORD,
                'X', Material.NETHER_STAR));

        addRecipe(Weapon.VOID_HAMMER, new String[]{"EDE", "NMN", "EXE"}, Map.of(
                'E', Material.ENDER_EYE,
                'D', Material.DRAGON_BREATH,
                'N', Material.NETHERITE_INGOT,
                'M', Material.MACE,
                'X', Material.NETHER_STAR));

        // ---- artifacts ----
        addRecipe(Artifact.CLOAK_OF_SHADOWS.id, artifacts.create(Artifact.CLOAK_OF_SHADOWS),
                new String[]{"PEP", "NXN", "PEP"}, Map.of(
                'P', Material.PHANTOM_MEMBRANE,
                'E', Material.ECHO_SHARD,
                'N', Material.NETHERITE_INGOT,
                'X', Material.NETHER_STAR));

        // from the SMP: Firework Rockets + TNT + Gunpowder / Creeper Head
        addChoiceRecipe(Artifact.JUMBO_ROCKET.id, artifacts.create(Artifact.JUMBO_ROCKET),
                new String[]{"RTR", "TGT", "RTR"}, Map.of(
                'R', new RecipeChoice.MaterialChoice(Material.FIREWORK_ROCKET),
                'T', new RecipeChoice.MaterialChoice(Material.TNT),
                'G', new RecipeChoice.MaterialChoice(Material.GUNPOWDER, Material.CREEPER_HEAD)));

        addRecipe(Artifact.PHOENIX_FEATHER.id, artifacts.create(Artifact.PHOENIX_FEATHER),
                new String[]{"BFB", "FTF", "BNB"}, Map.of(
                'B', Material.BLAZE_ROD,
                'F', Material.FEATHER,
                'T', Material.TOTEM_OF_UNDYING,
                'N', Material.NETHER_STAR));

        addRecipe(Artifact.GALE_ROD.id, artifacts.create(Artifact.GALE_ROD),
                new String[]{"WBW", "NBN", "WHW"}, Map.of(
                'W', Material.WIND_CHARGE,
                'B', Material.BREEZE_ROD,
                'N', Material.NETHERITE_INGOT,
                'H', Material.HEAVY_CORE));

        addRecipe(Artifact.CHRONO_CLOCK.id, artifacts.create(Artifact.CHRONO_CLOCK),
                new String[]{"ENE", "NCN", "ESE"}, Map.of(
                'E', Material.ECHO_SHARD,
                'N', Material.NETHERITE_INGOT,
                'C', Material.CLOCK,
                'S', Material.NETHER_STAR));

        addRecipe(Artifact.TRACKER_COMPASS.id, artifacts.create(Artifact.TRACKER_COMPASS),
                new String[]{"EDE", "NCN", "EDE"}, Map.of(
                'E', Material.ENDER_EYE,
                'D', Material.DIAMOND_BLOCK,
                'N', Material.NETHERITE_INGOT,
                'C', Material.RECOVERY_COMPASS));

        // from the SMP
        addRecipe(Artifact.BUCKET_OF_MALK.id, artifacts.create(Artifact.BUCKET_OF_MALK),
                new String[]{"SGS", "GMG", "SGS"}, Map.of(
                'S', Material.SUGAR,
                'G', Material.GOLDEN_APPLE,
                'M', Material.MILK_BUCKET));

        addRecipe(Artifact.BUCKET_OF_GREED.id, artifacts.create(Artifact.BUCKET_OF_GREED),
                new String[]{"GEG", "ELE", "GEG"}, Map.of(
                'G', Material.GOLD_BLOCK,
                'E', Material.EMERALD_BLOCK,
                'L', Material.LAVA_BUCKET));
    }

    private void addChoiceRecipe(String id, ItemStack result, String[] shape,
                                 Map<Character, RecipeChoice> ingredients) {
        try {
            NamespacedKey key = new NamespacedKey(this, id);
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(shape);
            for (Map.Entry<Character, RecipeChoice> en : ingredients.entrySet()) {
                recipe.setIngredient(en.getKey(), en.getValue());
            }
            Bukkit.addRecipe(recipe);
            recipeKeys.add(key);
            shapedRecipes.put(id, recipe);
        } catch (RuntimeException ex) {
            getLogger().warning("Could not register the recipe for " + id + ": " + ex);
        }
    }

    private void addRecipe(Weapon w, String[] shape, Map<Character, Material> ingredients) {
        addRecipe(w.id, createItem(w), shape, ingredients);
    }

    private void addRecipe(String id, ItemStack result, String[] shape, Map<Character, Material> ingredients) {
        try {
            NamespacedKey key = new NamespacedKey(this, id);
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(shape);
            for (Map.Entry<Character, Material> en : ingredients.entrySet()) {
                recipe.setIngredient(en.getKey(), en.getValue());
            }
            Bukkit.addRecipe(recipe);
            recipeKeys.add(key);
            shapedRecipes.put(id, recipe);
        } catch (RuntimeException ex) {
            getLogger().warning("Could not register the recipe for " + id + ": " + ex);
        }
    }
}
