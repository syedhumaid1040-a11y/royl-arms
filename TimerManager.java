package dev.royalarms;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class TimerManager {

    private final RoyalArms plugin;
    private BossBar bar;
    private BukkitTask task;
    private int total;
    private int left;
    private String title;

    public TimerManager(RoyalArms plugin) {
        this.plugin = plugin;
    }

    public void start(int seconds, String title) {
        stop();
        this.total = seconds;
        this.left = seconds;
        this.title = title;
        bar = BossBar.bossBar(render(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Bukkit.getServer().showBossBar(bar);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            left--;
            if (left <= 0) {
                Bukkit.getServer().sendMessage(
                        Component.text(this.title + " has ended!", NamedTextColor.GOLD));
                stop();
                return;
            }
            bar.name(render());
            bar.progress((float) left / total);
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            Bukkit.getServer().hideBossBar(bar);
            bar = null;
        }
    }

    public void show(Player p) {
        if (bar != null) p.showBossBar(bar);
    }

    private Component render() {
        return Component.text(String.format("%s  %02d:%02d", title, left / 60, left % 60),
                NamedTextColor.WHITE);
    }
}
