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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ArsenalGui implements InventoryHolder {

    public static final int WEAPON_FIRST_SLOT = 10;
    public static final int ARTIFACT_FIRST_SLOT = 19;
    public static final int MAX_ROW = 7;

    public static int artifactSlot(int i) {
        return i < MAX_ROW ? ARTIFACT_FIRST_SLOT + i : 28 + (i - MAX_ROW);
    }

    public static int artifactIndexAt(int slot) {
        if (slot >= ARTIFACT_FIRST_SLOT && slot < ARTIFACT_FIRST_SLOT + MAX_ROW) return slot - ARTIFACT_FIRST_SLOT;
        if (slot >= 28 && slot < 28 + MAX_ROW) return MAX_ROW + (slot - 28);
        return -1;
    }

    private final RoyalArms plugin;
    private final Inventory inv;

    public ArsenalGui(RoyalArms plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 45,
                Component.text("RoyalArms Arsenal", NamedTextColor.DARK_PURPLE));
        fill();
    }

    private void fill() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        Weapon[] weapons = Weapon.values();
        for (int i = 0; i < weapons.length && i < MAX_ROW; i++) {
            inv.setItem(WEAPON_FIRST_SLOT + i, withRecipe(plugin.items().create(weapons[i], true), weapons[i].recipe));
        }
        Artifact[] artifacts = Artifact.values();
        for (int i = 0; i < artifacts.length && i < 2 * MAX_ROW; i++) {
            inv.setItem(artifactSlot(i), withRecipe(plugin.artifacts().create(artifacts[i]), artifacts[i].recipe));
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("How it works", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        im.lore(List.of(
                line("Click any item to see its crafting recipe.", NamedTextColor.YELLOW),
                line("Weapons: land hits to fill power bars (max 3).", NamedTextColor.GRAY),
                line("Right-click: ability 1. Sneak + right-click: ability 2.", NamedTextColor.GRAY),
                line("Or use /ability1 and /ability2 (great on Bedrock).", NamedTextColor.GRAY),
                line("3 unique player kills unlock a weapon.", NamedTextColor.GRAY),
                line("Artifacts: right-click or /ability1.", NamedTextColor.GRAY),
                line("Shrines show a weapon and its recipe. Right-click the altar.", NamedTextColor.DARK_GRAY)));
        info.setItemMeta(im);
        inv.setItem(40, info);
    }

    private ItemStack withRecipe(ItemStack it, String recipe) {
        ItemMeta m = it.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (m.lore() != null) lore.addAll(m.lore());
        lore.add(Component.empty());
        lore.add(line("Recipe:", NamedTextColor.GOLD));
        lore.add(line(recipe, NamedTextColor.YELLOW));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
