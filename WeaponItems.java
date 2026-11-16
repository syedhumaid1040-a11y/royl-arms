package dev.royalarms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creates weapon items and reads/writes their hidden state (stage, power, kills). */
public final class WeaponItems {

    private final RoyalArms plugin;
    private final NamespacedKey weaponKey;
    private final NamespacedKey stageKey;
    private final NamespacedKey powerKey;
    private final NamespacedKey victimsKey;

    public WeaponItems(RoyalArms plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "weapon");
        this.stageKey = new NamespacedKey(plugin, "stage");
        this.powerKey = new NamespacedKey(plugin, "power");
        this.victimsKey = new NamespacedKey(plugin, "victims");
    }

    public ItemStack create(Weapon w, boolean maxed) {
        ItemStack item = new ItemStack(w.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(w.displayName, w.color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        if (plugin.getConfig().getBoolean("custom-models.enabled", true)) {
            NamespacedKey model = NamespacedKey.fromString("royalarms:" + w.id);
            if (model != null) meta.setItemModel(model);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(line("Passive: " + w.passive, NamedTextColor.GRAY));
        lore.add(line("Right-click: " + w.ability1 + " (" + w.cost1 + " power)", NamedTextColor.GRAY));
        lore.add(line("Sneak + right-click: " + w.ability2 + " (" + w.cost2 + " power)", NamedTextColor.GRAY));
        lore.add(line("Land hits to charge power. Unique kills unlock it.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        var pdc = meta.getPersistentDataContainer();
        pdc.set(weaponKey, PersistentDataType.STRING, w.id);
        pdc.set(stageKey, PersistentDataType.INTEGER, maxed ? 3 : 0);
        pdc.set(powerKey, PersistentDataType.INTEGER, maxed ? 3 : 0);
        pdc.set(victimsKey, PersistentDataType.STRING, "");
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public Weapon weaponOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(weaponKey, PersistentDataType.STRING);
        return id == null ? null : Weapon.byId(id);
    }

    public boolean unlockEnabled() {
        return plugin.getConfig().getBoolean("unlock.enabled", true);
    }

    public int stage(ItemStack item) {
        return Math.max(0, Math.min(3, getInt(item, stageKey)));
    }

    /** Stage used for gating abilities: always 3 when kill unlocks are turned off. */
    public int effectiveStage(ItemStack item) {
        return unlockEnabled() ? stage(item) : 3;
    }

    public int power(ItemStack item) {
        return Math.max(0, Math.min(3, getInt(item, powerKey)));
    }

    public void setPower(ItemStack item, int value) {
        setInt(item, powerKey, Math.max(0, Math.min(3, value)));
    }

    /** Records a unique player kill. Returns true if the weapon reached a new stage. */
    public boolean recordKill(ItemStack item, UUID victim) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        String raw = pdc.getOrDefault(victimsKey, PersistentDataType.STRING, "");
        Set<String> set = new LinkedHashSet<>();
        for (String s : raw.split(",")) {
            if (!s.isBlank()) set.add(s);
        }
        if (!set.add(victim.toString())) return false;
        pdc.set(victimsKey, PersistentDataType.STRING, String.join(",", set));
        int newStage = Math.min(3, set.size());
        boolean up = newStage > Math.max(0, Math.min(3, getIntFromMeta(meta, stageKey)));
        pdc.set(stageKey, PersistentDataType.INTEGER, newStage);
        item.setItemMeta(meta);
        return up;
    }

    public List<String> victimList(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return List.of();
        String raw = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(victimsKey, PersistentDataType.STRING, "");
        return Arrays.stream(raw.split(",")).filter(s -> !s.isBlank()).toList();
    }

    private int getInt(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0;
        return getIntFromMeta(item.getItemMeta(), key);
    }

    private int getIntFromMeta(ItemMeta meta, NamespacedKey key) {
        Integer v = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private void setInt(ItemStack item, NamespacedKey key, int value) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }
}
