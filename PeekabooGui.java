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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Player picker for the Void Hammer's Peekaboo ability. */
public final class PeekabooGui implements InventoryHolder {

    private final Inventory inv;
    private final Map<Integer, UUID> slots = new HashMap<>();

    public PeekabooGui(List<Player> targets) {
        inv = Bukkit.createInventory(this, 27,
                Component.text("Peekaboo: pick a player", NamedTextColor.DARK_PURPLE));
        int i = 0;
        for (Player t : targets) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta sm) {
                sm.setOwningPlayer(t);
                sm.displayName(Component.text(t.getName(), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
                head.setItemMeta(sm);
            }
            inv.setItem(i, head);
            slots.put(i, t.getUniqueId());
            i++;
        }
    }

    public UUID at(int slot) {
        return slots.get(slot);
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
