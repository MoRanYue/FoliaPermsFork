package kaiakk.foliaPerms.events;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.internal.ColorConverter;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

/**
 * Handles player-specific events for FoliaPerms.
 * Version: 1.13.0
 */
public class PlayerListener implements Listener {
    private final FoliaPerms plugin;

    public PlayerListener(FoliaPerms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Welcome message for admins
        if (player.hasPermission("folia.perms")) {
            String welcome = ColorConverter.colorize("&eFoliaPerms active!");
            player.sendMessage(welcome);
        }
        
        // Apply permission attachment
        try {
            plugin.refreshPlayerAttachment(player);
            plugin.getLogger().fine("Applied permission attachment for " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply permissions to " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up permission attachment
        try {
            plugin.removePlayerAttachment(player.getUniqueId());
            plugin.getLogger().fine("Cleaned up permission attachment for " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("Error cleaning up attachment for " + player.getName() + ": " + e.getMessage());
        }
    }
}