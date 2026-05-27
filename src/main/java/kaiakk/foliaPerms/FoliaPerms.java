package kaiakk.foliaPerms;

import kaiakk.foliaPerms.api.FoliaPermsAPI;
import kaiakk.foliaPerms.commands.FpermCommand;
import kaiakk.foliaPerms.events.PlayerListener;
import kaiakk.foliaPerms.permissions.PermissionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FoliaPerms - A simple permission manager for Folia servers.
 * Version: 1.13.0
 * 
 * This plugin provides:
 * - User and group-based permission management
 * - YAML-based persistence
 * - GUI editor for permissions
 * - Folia-compatible thread-safe operations
 */
public final class FoliaPerms extends JavaPlugin implements FoliaPermsAPI {
    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private PermissionService permissionService;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    @Override
    public void onLoad() {
        if (!isFolia()) {
            getLogger().severe("Error detected!");
            getLogger().severe("FoliaPerms is a Folia-only plugin!");
            getLogger().severe("It appears you are running a normal Bukkit/Paper/Spigot server.");
            getLogger().severe("This plugin will now disable itself, goodbye.");
            getLogger().severe("Disabling FoliaPerms...");
            getServer().getPluginManager().disablePlugin(this);
        } else {
            getLogger().info("Folia environment detected. FoliaPerms is ready to enable.");
            getLogger().info("Enabling FoliaPerms v1.13.0...");
            getLogger().info("Loading all permissions data...");
        }
    }
    
    @Override
    public void onEnable() {
        getLogger().info("FoliaPerms v1.13.0 enabled successfully. Welcome to the Folia environment!");

        this.permissionService = new PermissionService(this);
        try {
            this.permissionService.load();
            getLogger().info("Loaded permissions data.");
        } catch (Exception e) {
            kaiakk.foliaPerms.internal.ErrorHandler.handle(this, "Failed to load permissions data", e);
        }

        if (getCommand("fperm") != null) {
            getCommand("fperm").setExecutor(new FpermCommand(this));
            getCommand("fperm").setTabCompleter(new kaiakk.foliaPerms.commands.FpermTabCompleter(this));
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new kaiakk.foliaPerms.events.PluginEnableListener(this), this);
        getServer().getPluginManager().registerEvents(new kaiakk.foliaPerms.gui.GuiListener(), this);

        getServer().getServicesManager().register(FoliaPermsAPI.class, this, this, ServicePriority.Normal);
        getLogger().info("FoliaPerms API registered with ServicesManager.");

        try {
            permissionService.gatherRegisteredPermissions(this);
            getLogger().info("Gathered " + permissionService.getRegisteredPermissions().size() + " permissions from plugins.");
            // Log first 10 permissions
            int count = 0;
            for (String p : permissionService.getRegisteredPermissions()) {
                if (count++ < 10) {
                    getLogger().info(" - " + p);
                }
            }
            if (permissionService.getRegisteredPermissions().size() > 10) {
                getLogger().info(" ... and " + (permissionService.getRegisteredPermissions().size() - 10) + " more");
            }
            refreshAllAttachments();
            getLogger().info("Permission attachments initialized for " + Bukkit.getOnlinePlayers().size() + " players.");
        } catch (Exception e) {
            kaiakk.foliaPerms.internal.ErrorHandler.handle(this, "Failed to gather registered permissions", e);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("FoliaPerms v1.13.0 disabling...");
        getLogger().info("Saving permissions...");
        if (this.permissionService != null) {
            try {
                this.permissionService.save();
                getLogger().info("Permissions saved.");
            } catch (Exception e) {
                getLogger().severe("Failed to save permissions: " + e.getMessage());
            }
        }
        
        // Clean up all attachments
        cleanupAllAttachments();
        getLogger().info("FoliaPerms disabled successfully.");
    }

    public PermissionService getPermissionService() {
        return this.permissionService;
    }

    /**
     * Refreshes permission attachment for a specific player.
     */
    public void refreshPlayerAttachment(Player player) {
        if (player == null || permissionService == null) return;
        try {
            UUID id = player.getUniqueId();
            PermissionAttachment old = attachments.remove(id);
            if (old != null) {
                try { player.removeAttachment(old); } catch (Exception ignored) {}
            }

            PermissionAttachment attach = player.addAttachment(this);
            attachments.put(id, attach);

            getLogger().fine("Created/updated the permissions attachment for " + player.getName());

            var registered = permissionService.getRegisteredPermissions();
            for (String node : registered) {
                attach.setPermission(node, false);
            }

            var allowed = permissionService.getAllowedPermissions(id);
            for (String node : allowed) {
                attach.setPermission(node, true);
            }
            try {
                player.recalculatePermissions();
                getLogger().fine("Recalculated permissions for " + player.getName());
                try {
                    player.updateCommands();
                    getLogger().fine("Updated command tree for " + player.getName());
                } catch (Throwable t) {
                    getLogger().warning("Failed to update command tree for " + player.getName() + ": " + t.getMessage());
                }
            } catch (Throwable t) {
                getLogger().warning("Failed to recalculate permissions for " + player.getName() + ": " + t.getMessage());
            }
        } catch (Exception e) {
            getLogger().severe("Failed to refresh attachment for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Refreshes permission attachments for all online players.
     */
    public void refreshAllAttachments() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshPlayerAttachment(p);
        }
    }

    /**
     * Removes a player's permission attachment (called on quit).
     */
    public void removePlayerAttachment(UUID playerId) {
        PermissionAttachment old = attachments.remove(playerId);
        if (old != null) {
            try {
                Player p = Bukkit.getPlayer(playerId);
                if (p != null) {
                    p.removeAttachment(old);
                    getLogger().fine("Removed attachment for player " + playerId);
                }
            } catch (Exception e) {
                getLogger().warning("Error removing attachment for " + playerId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Cleans up all player attachments (called on disable).
     */
    private void cleanupAllAttachments() {
        for (UUID id : attachments.keySet()) {
            removePlayerAttachment(id);
        }
        attachments.clear();
        getLogger().info("All permission attachments cleaned up.");
    }

    @Override
    public boolean hasPermission(Player player, String permissionNode) {
        if (player == null) return false;
        return this.permissionService != null && this.permissionService.hasPermission(player.getUniqueId(), permissionNode);
    }

    @Override
    public boolean hasPermission(java.util.UUID playerUuid, String permissionNode) {
        if (playerUuid == null) return false;
        return this.permissionService != null && this.permissionService.hasPermission(playerUuid, permissionNode);
    }

    @Override
    public Set<String> getPlayerGroups(Player player) {
        if (player == null || this.permissionService == null) return Collections.emptySet();
        var ud = this.permissionService.getUser(player.getUniqueId());
        if (ud == null) return Collections.emptySet();
        return Collections.unmodifiableSet(new HashSet<>(ud.getGroups()));
    }

    @Override
    public String getPrimaryGroup(Player player) {
        var groups = getPlayerGroups(player);
        return groups.stream().findFirst().orElse(null);
    }
}