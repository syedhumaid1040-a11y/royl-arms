package dev.royalarms;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class AltarListener implements Listener {

    private final RoyalArms plugin;

    public AltarListener(RoyalArms plugin) {
        this.plugin = plugin;
    }

    /** Right-clicking a shrine altar opens the recipe of the weapon it shows. */
    @EventHandler(priority = EventPriority.LOW)
    public void onAltarUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block core = e.getClickedBlock();
        if (core == null || core.getType() != Material.LODESTONE) return;
        if (!plugin.altars().isAltar(core)) return;

        e.setCancelled(true);
        Weapon w = plugin.altars().weaponAt(core);
        if (w == null) return;
        new RecipeGui(plugin, w).open(e.getPlayer());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.LODESTONE && plugin.altars().isAltar(e.getBlock())) {
            plugin.structures().removeShowcase(e.getBlock());
            plugin.altars().unregister(e.getBlock());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("structures.enabled", true)) return;

        World world = e.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        List<String> worlds = cfg.getStringList("structures.worlds");
        if (!worlds.isEmpty() && !worlds.contains(world.getName())) return;
        if (ThreadLocalRandom.current().nextDouble() > cfg.getDouble("structures.chance", 0.004)) return;

        Chunk chunk = e.getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;

        // place one tick later; never modify blocks inside the load event itself
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!world.isChunkLoaded(chunkX, chunkZ)) return;
            Block ground = plugin.structures().findSurface(world, x, z);
            if (ground == null) return;
            plugin.structures().buildShrine(ground, new Random());
            plugin.getLogger().info("Spawned a shrine at " + x + ", " + ground.getY() + ", " + z);
        });
    }
}
