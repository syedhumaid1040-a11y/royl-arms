package dev.royalarms;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /ability1 and /ability2: cast without sneaking (handy for Bedrock and hotkeys). */
public final class AbilityCommand implements CommandExecutor {

    private final RoyalArms plugin;
    private final int slot;

    public AbilityCommand(RoyalArms plugin, int slot) {
        this.plugin = plugin;
        this.slot = slot;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (slot == 1 && plugin.artifacts().artifactOf(p.getInventory().getItemInMainHand()) != null) {
            plugin.artifacts().use(p);
            return true;
        }
        plugin.abilities().cast(p, slot);
        return true;
    }
}
