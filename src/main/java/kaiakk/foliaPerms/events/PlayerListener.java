package kaiakk.foliaPerms.events;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.internal.ColorConverter;
import kaiakk.foliaPerms.permissions.YamlStorage;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

/**
 * Handles player-specific events for FoliaPerms.
 * Version: 0.1.0+26.1.2
 *
 * On join, ensures every player is assigned to the default group
 * if they have no existing group assignment.
 */
public class PlayerListener implements Listener {
    private final FoliaPerms plugin;

    public PlayerListener(FoliaPerms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Ensure the player has the default group if not assigned to any group
        var permService = plugin.getPermissionService();
        if (permService != null) {
            var userData = permService.getUser(player.getUniqueId());
            if (userData == null || userData.getGroups().isEmpty()) {
                // This triggers getOrCreateUser which auto-assigns the default group
                permService.getOrCreateUser(player.getUniqueId());
                plugin.getLogger().info("Auto-assigned player " + player.getName() + " to the default group.");
            }
        }
        
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
