package dev.royalarms;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Makes the plugin all-in-one: it hosts the Java resource pack itself, sends it to players
 * on join, and drops the Bedrock pack and Geyser mappings into Geyser's folders.
 */
public final class PackManager {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("royalarms-pack".getBytes());

    private final RoyalArms plugin;
    private HttpServer server;
    private String url;
    private byte[] sha1;

    public PackManager(RoyalArms plugin) {
        this.plugin = plugin;
    }

    public void start() {
        installGeyserFiles();

        FileConfiguration cfg = plugin.getConfig();
        String mode = cfg.getString("resource-pack.mode", "builtin").toLowerCase();
        if (!cfg.getBoolean("custom-models.enabled", true) || mode.equals("off")) return;

        if (mode.equals("external")) {
            String u = cfg.getString("resource-pack.url", "");
            String h = cfg.getString("resource-pack.sha1", "");
            if (u.isBlank() || h.isBlank()) {
                plugin.getLogger().warning("resource-pack.mode is external but url or sha1 is empty.");
                return;
            }
            try {
                sha1 = HexFormat.of().parseHex(h.trim());
                url = u.trim();
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("resource-pack.sha1 is not valid hex.");
            }
            return;
        }

        // builtin: the plugin hosts the pack that is bundled inside this jar
        byte[] data = readResource("pack/royalarms-pack-java.zip");
        if (data == null) {
            plugin.getLogger().warning("The bundled resource pack is missing from the jar.");
            return;
        }
        String host = cfg.getString("resource-pack.host", "");
        if (host.isBlank()) {
            plugin.getLogger().warning("Set resource-pack.host in config.yml to your server's public IP or "
                    + "domain so players can download the custom looks.");
            return;
        }
        int port = cfg.getInt("resource-pack.port", 8164);
        try {
            sha1 = MessageDigest.getInstance("SHA-1").digest(data);
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/royalarms.zip", ex -> {
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, data.length);
                try (var os = ex.getResponseBody()) {
                    os.write(data);
                }
            });
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "RoyalArms-pack-host");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            url = "http://" + host.trim() + ":" + port + "/royalarms.zip";
            plugin.getLogger().info("Hosting the resource pack at " + url + " (TCP port " + port + " must be open).");
        } catch (IOException | NoSuchAlgorithmException ex) {
            plugin.getLogger().warning("Could not start the resource pack host: " + ex.getMessage());
            url = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** Sends the pack to a Java player shortly after they join. */
    public void sendTo(Player p) {
        if (url == null || sha1 == null) return;
        if (p.getUniqueId().getMostSignificantBits() == 0L) return; // Floodgate (Bedrock) players
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.setResourcePack(PACK_ID, url, sha1,
                    Component.text("RoyalArms custom weapon looks"),
                    plugin.getConfig().getBoolean("resource-pack.force", false));
        }, 20L);
    }

    // ---------- Geyser (Bedrock) ----------

    private void installGeyserFiles() {
        if (!plugin.getConfig().getBoolean("geyser.auto-install", true)) return;
        File geyser = new File(plugin.getDataFolder().getParentFile(), "Geyser-Spigot");
        if (!geyser.isDirectory()) return;

        boolean changed = copyResource("pack/royalarms-pack-bedrock.mcpack",
                new File(geyser, "packs/RoyalArms-ResourcePack-Bedrock.mcpack"));
        changed |= copyResource("pack/royalarms_geyser_mappings.json",
                new File(geyser, "custom_mappings/royalarms_geyser_mappings.json"));
        if (changed) {
            plugin.getLogger().info("Installed the RoyalArms Bedrock pack and Geyser mappings. "
                    + "Restart the server so Geyser loads them.");
        }
    }

    private boolean copyResource(String resource, File dest) {
        byte[] data = readResource(resource);
        if (data == null) return false;
        try {
            if (dest.exists() && Arrays.equals(Files.readAllBytes(dest.toPath()), data)) return false;
            dest.getParentFile().mkdirs();
            Files.write(dest.toPath(), data);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write " + dest + ": " + ex.getMessage());
            return false;
        }
    }

    private byte[] readResource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException ex) {
            return null;
        }
    }
}
