package kaiakk.foliaPerms.events;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.internal.ColorConverter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {
    private final FoliaPerms plugin;

    public PlayerListener(FoliaPerms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPermission("folia.perms")) {
            String welcome = ColorConverter.colorize("&eFoliaPerms active!");
            event.getPlayer().sendMessage(welcome);
        }
        try {
            plugin.refreshPlayerAttachment(event.getPlayer());
        } catch (Exception ignored) {}
    }
}