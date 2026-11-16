package dev.royalarms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class GuiListener implements Listener {

    private final RoyalArms plugin;

    public GuiListener(RoyalArms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();

        // Arsenal menu: click any item to see its recipe
        if (holder instanceof ArsenalGui) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof ArsenalGui)) return;

            Weapon[] weapons = Weapon.values();
            Artifact[] artifacts = Artifact.values();
            int wi = e.getSlot() - ArsenalGui.WEAPON_FIRST_SLOT;
            int ai = ArsenalGui.artifactIndexAt(e.getSlot());
            if (wi >= 0 && wi < weapons.length && wi < ArsenalGui.MAX_ROW) {
                new RecipeGui(plugin, weapons[wi]).open(p);
            } else if (ai >= 0 && ai < artifacts.length) {
                new RecipeGui(plugin, artifacts[ai]).open(p);
            }
            return;
        }

        // Recipe view: back button, and a take button for admins
        if (holder instanceof RecipeGui recipeGui) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof RecipeGui)) return;

            if (e.getSlot() == RecipeGui.BACK_SLOT) {
                new ArsenalGui(plugin).open(p);
            } else if (e.getSlot() == RecipeGui.TAKE_SLOT && p.hasPermission("royalarms.admin")) {
                p.getInventory().addItem(recipeGui.giveItem()).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                p.sendActionBar(Component.text("Copy added to your inventory", NamedTextColor.GREEN));
            }
            return;
        }

        // Void Hammer's Peekaboo player picker
        if (holder instanceof PeekabooGui peekabooGui) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof PeekabooGui)) return;
            UUID id = peekabooGui.at(e.getSlot());
            if (id == null) return;
            Player target = Bukkit.getPlayer(id);
            p.closeInventory();
            if (target != null) plugin.abilities().peekabooTeleport(p, target);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof ArsenalGui || holder instanceof RecipeGui || holder instanceof PeekabooGui) {
            e.setCancelled(true);
        }
    }
}
