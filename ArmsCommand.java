package dev.royalarms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ArmsCommand implements CommandExecutor, TabCompleter {

    private final RoyalArms plugin;

    public ArmsCommand(RoyalArms plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            msg(sender, "Usage: /arms give <weapon> [player] [locked] | /arms power <0-3> | "
                    + "/arms timer <seconds> <title|stop> | /arms altar | /arms shrine | /arms gui",
                    NamedTextColor.YELLOW);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (args.length < 2) {
                    msg(sender, "Usage: /arms give <weapon> [player] [locked]", NamedTextColor.YELLOW);
                    return true;
                }
                Artifact art = Artifact.byId(args[1]);
                if (art != null) {
                    String an = null;
                    for (int i = 2; i < args.length; i++) an = args[i];
                    Player at = an != null ? Bukkit.getPlayer(an)
                            : (sender instanceof Player sp ? sp : null);
                    if (at == null) {
                        msg(sender, "Player not found.", NamedTextColor.RED);
                        return true;
                    }
                    at.getInventory().addItem(plugin.artifacts().create(art));
                    msg(sender, "Gave " + art.displayName + " to " + at.getName(), NamedTextColor.GREEN);
                    return true;
                }
                Weapon w = Weapon.byId(args[1]);
                if (w == null) {
                    msg(sender, "Unknown weapon. Options: " + weaponIds(), NamedTextColor.RED);
                    return true;
                }
                boolean locked = false;
                String playerName = null;
                for (int i = 2; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("locked")) locked = true;
                    else playerName = args[i];
                }
                Player target = playerName != null ? Bukkit.getPlayer(playerName)
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    msg(sender, "Player not found.", NamedTextColor.RED);
                    return true;
                }
                target.getInventory().addItem(plugin.items().create(w, !locked));
                msg(sender, "Gave " + w.displayName + (locked ? " (locked)" : " (unlocked)")
                        + " to " + target.getName(), NamedTextColor.GREEN);
            }
            case "power" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Players only.", NamedTextColor.RED);
                    return true;
                }
                if (args.length < 2) {
                    msg(sender, "Usage: /arms power <0-3>", NamedTextColor.YELLOW);
                    return true;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (plugin.items().weaponOf(hand) == null) {
                    msg(sender, "Hold a RoyalArms weapon.", NamedTextColor.RED);
                    return true;
                }
                try {
                    plugin.items().setPower(hand, Integer.parseInt(args[1]));
                    p.getInventory().setItemInMainHand(hand);
                } catch (NumberFormatException ex) {
                    msg(sender, "Power must be 0-3.", NamedTextColor.RED);
                }
            }
            case "timer" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
                    plugin.timers().stop();
                    msg(sender, "Timer stopped.", NamedTextColor.GREEN);
                    return true;
                }
                if (args.length < 3) {
                    msg(sender, "Usage: /arms timer <seconds> <title>", NamedTextColor.YELLOW);
                    return true;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    msg(sender, "Seconds must be a number.", NamedTextColor.RED);
                    return true;
                }
                if (seconds < 1) {
                    msg(sender, "Seconds must be at least 1.", NamedTextColor.RED);
                    return true;
                }
                String title = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.timers().start(seconds, title);
            }
            case "gui" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Players only.", NamedTextColor.RED);
                    return true;
                }
                new ArsenalGui(plugin).open(p);
            }
            case "altar", "shrine" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Players only.", NamedTextColor.RED);
                    return true;
                }
                Weapon chosen = args.length >= 2 ? Weapon.byId(args[1]) : null;
                if (args.length >= 2 && chosen == null) {
                    msg(sender, "Unknown weapon. Options: " + weaponIds(), NamedTextColor.RED);
                    return true;
                }
                if (chosen == null) {
                    Weapon[] all = Weapon.values();
                    chosen = all[new java.util.Random().nextInt(all.length)];
                }
                Block ground = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
                if (args[0].equalsIgnoreCase("altar")) {
                    plugin.structures().buildAltar(ground, chosen);
                    msg(sender, "Altar of the " + chosen.displayName + " built.", NamedTextColor.GREEN);
                } else {
                    plugin.structures().buildShrine(ground, new java.util.Random(), chosen);
                    msg(sender, "Shrine of the " + chosen.displayName + " built.", NamedTextColor.GREEN);
                }
            }
            default -> msg(sender, "Unknown subcommand.", NamedTextColor.RED);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "power", "timer", "altar", "shrine", "gui"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Weapon w : Weapon.values()) out.add(w.id);
            for (Artifact a : Artifact.values()) out.add(a.id);
        } else if (args.length == 2
                && (args[0].equalsIgnoreCase("altar") || args[0].equalsIgnoreCase("shrine"))) {
            for (Weapon w : Weapon.values()) out.add(w.id);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("timer")) {
            out.add("stop");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("power")) {
            out.addAll(List.of("0", "1", "2", "3"));
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            out.add("locked");
        }
        String prefix = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return out;
    }

    private String weaponIds() {
        List<String> ids = new ArrayList<>();
        for (Weapon w : Weapon.values()) ids.add(w.id);
        for (Artifact a : Artifact.values()) ids.add(a.id);
        return String.join(", ", ids);
    }

    private void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }
}
