package dev.royalarms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shows the 3x3 crafting layout for one weapon or artifact. View-only. */
public final class RecipeGui implements InventoryHolder {

    public static final int BACK_SLOT = 36;
    public static final int TAKE_SLOT = 44;
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int ARROW_SLOT = 23;
    private static final int RESULT_SLOT = 24;

    private final RoyalArms plugin;
    private final Inventory inv;
    private final ItemStack giveItem;

    public RecipeGui(RoyalArms plugin, Weapon w) {
        this(plugin, w.id, w.displayName, w.color, plugin.createItem(w), plugin.items().create(w, true));
    }

    public RecipeGui(RoyalArms plugin, Artifact a) {
        this(plugin, a.id, a.displayName, a.color, plugin.artifacts().create(a), plugin.artifacts().create(a));
    }

    private RecipeGui(RoyalArms plugin, String id, String name, NamedTextColor color,
                      ItemStack result, ItemStack give) {
        this.plugin = plugin;
        this.giveItem = give;
        this.inv = Bukkit.createInventory(this, 45, Component.text(name + " - recipe", color));

        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        ShapedRecipe recipe = plugin.recipeFor(id);
        if (recipe == null) {
            inv.setItem(20, named(Material.PAPER, "No crafting recipe", NamedTextColor.RED,
                    List.of("Recipes are turned off on this server.",
                            "Find it at an altar or ask an admin.")));
        } else {
            String[] shape = recipe.getShape();
            Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    char ch = (row < shape.length && col < shape[row].length()) ? shape[row].charAt(col) : ' ';
                    RecipeChoice choice = ch == ' ' ? null : choices.get(ch);
                    inv.setItem(GRID[row * 3 + col], choice == null ? emptySlot() : ingredient(choice));
                }
            }
        }

        inv.setItem(ARROW_SLOT, named(Material.ARROW, "Crafts into", NamedTextColor.GRAY, List.of()));
        inv.setItem(RESULT_SLOT, result);
        inv.setItem(BACK_SLOT, named(Material.BARRIER, "Back to the arsenal", NamedTextColor.RED, List.of()));
    }

    private ItemStack emptySlot() {
        return named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "Empty slot", NamedTextColor.DARK_GRAY, List.of());
    }

    private ItemStack ingredient(RecipeChoice choice) {
        Material shown = Material.BARRIER;
        List<Material> options = List.of();
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            options = mc.getChoices();
            if (!options.isEmpty()) shown = options.get(0);
        }
        ItemStack it = new ItemStack(shown);
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(line(pretty(shown), NamedTextColor.WHITE));
        if (options.size() > 1) {
            lore.add(line("Any one of:", NamedTextColor.GOLD));
            for (Material o : options) lore.add(line(pretty(o), NamedTextColor.YELLOW));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack named(Material m, String name, NamedTextColor color, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(line(name, color));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(line(s, NamedTextColor.GRAY));
        if (!l.isEmpty()) meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public ItemStack giveItem() {
        return giveItem.clone();
    }

    public void open(Player p) {
        if (p.hasPermission("royalarms.admin")) {
            inv.setItem(TAKE_SLOT, named(Material.CHEST, "Take a copy (admin)", NamedTextColor.GREEN, List.of()));
        }
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
