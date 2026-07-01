package kaiakk.foliaPerms;

import kaiakk.foliaPerms.api.FoliaPermsAPI;
import kaiakk.foliaPerms.commands.FpermCommand;
import kaiakk.foliaPerms.events.PlayerListener;
import kaiakk.foliaPerms.internal.LocaleManager;
import kaiakk.foliaPerms.internal.UpdateChecker;
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
 * FoliaPermsFork - A simple permission manager for Folia servers.
 * Version: 0.2.0+26.1.2
 *
 * This plugin provides:
 * - User and group-based permission management
 * - YAML-based persistence
 * - GUI editor for permissions
 * - Group inheritance support
 * - Default group for new players
 * - Folia-compatible thread-safe operations
 * - Localization support via LocaleManager
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
    private LocaleManager localeManager;
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
            getLogger().info("Enabling FoliaPermsFork v0.2.0+26.1.2...");
            getLogger().info("Loading all permissions data...");
        }
    }

    @Override
    public void onEnable() {
        // Initialize locale manager before anything else
        this.localeManager = new LocaleManager(this);

        // Save default config.yml from resources if not exists, then load
        saveDefaultConfig();
        reloadConfig();
        String language = getConfig().getString("language", "en_us");
        this.localeManager.load(language);

        getLogger().info(tlRaw("console.plugin.enabled", getPluginMeta().getVersion()));

        this.permissionService = new PermissionService(this);
        try {
            this.permissionService.load();
            getLogger().info(tlRaw("console.permission.loaded",
                    permissionService.getUsers().size(),
                    permissionService.getGroups().size()));
        } catch (Exception e) {
            kaiakk.foliaPerms.internal.ErrorHandler.handle(this, tlRaw("console.plugin.error-load-perms"), e);
        }

        if (getCommand("fperm") != null) {
            getCommand("fperm").setExecutor(new FpermCommand(this));
            getCommand("fperm").setTabCompleter(new kaiakk.foliaPerms.commands.FpermTabCompleter(this));
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new kaiakk.foliaPerms.events.PluginEnableListener(this), this);
        getServer().getPluginManager().registerEvents(new kaiakk.foliaPerms.gui.GuiListener(), this);

        getServer().getServicesManager().register(FoliaPermsAPI.class, this, this, ServicePriority.Normal);
        getLogger().info(tlRaw("console.plugin.api-registered"));

        try {
            permissionService.gatherRegisteredPermissions(this);
            getLogger().info(tlRaw("console.plugin.gathered-perms", permissionService.getRegisteredPermissions().size()));
            // Log first 10 permissions
            int count = 0;
            for (String p : permissionService.getRegisteredPermissions()) {
                if (count++ < 10) {
                    getLogger().info(" - " + p);
                }
            }
            if (permissionService.getRegisteredPermissions().size() > 10) {
                getLogger().info(tlRaw("console.plugin.and-more",
                        permissionService.getRegisteredPermissions().size() - 10));
            }
            refreshAllAttachments();
            getLogger().info(tlRaw("console.plugin.attachments-initialized", Bukkit.getOnlinePlayers().size()));
        } catch (Exception e) {
            kaiakk.foliaPerms.internal.ErrorHandler.handle(this, tlRaw("console.plugin.error-gather-perms"), e);
        }

        // Asynchronously check for updates from GitHub (if enabled in config)
        if (getConfig().getBoolean("check-updates", true)) {
            UpdateChecker.check(getPluginMeta().getVersion(), getLogger(), this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info(tlRaw("console.plugin.disabling", getPluginMeta().getVersion()));
        getLogger().info(tlRaw("console.plugin.saving"));
        if (this.permissionService != null) {
            try {
                this.permissionService.save();
                getLogger().info(tlRaw("console.plugin.saved"));
            } catch (Exception e) {
                getLogger().severe("Failed to save permissions: " + e.getMessage());
            }
        }

        // Clean up all attachments
        cleanupAllAttachments();
        getLogger().info(tlRaw("console.plugin.disabled"));
    }

    // ──────────────────────────────────────────────
    //  Localization Convenience Methods
    // ──────────────────────────────────────────────

    /**
     * Gets a colorized localized string (for in-game messages).
     *
     * @param key  the localization key
     * @param args optional placeholder arguments
     * @return the colorized localized string
     */
    public String tl(String key, Object... args) {
        if (localeManager != null) {
            return localeManager.getColoredString(key, args);
        }
        return key;
    }

    /**
     * Gets a raw (uncolored) localized string (for console/log messages).
     *
     * @param key  the localization key
     * @param args optional placeholder arguments
     * @return the raw localized string without color codes
     */
    public String tlRaw(String key, Object... args) {
        if (localeManager != null) {
            return localeManager.getStrippedString(key, args);
        }
        return key;
    }

    /**
     * Gets the LocaleManager instance.
     */
    public LocaleManager getLocaleManager() {
        return localeManager;
    }

    public PermissionService getPermissionService() {
        return this.permissionService;
    }

    // ──────────────────────────────────────────────
    //  Permission Attachment Management
    // ──────────────────────────────────────────────

    /**
     * Core refresh logic - runs synchronously on the player's region thread.
     */
    private void refreshPlayerAttachmentSync(Player player) {
        if (player == null || permissionService == null) return;
        try {
            UUID id = player.getUniqueId();
            PermissionAttachment old = attachments.remove(id);
            if (old != null) {
                try { player.removeAttachment(old); } catch (Exception ex) {
                    getLogger().warning("Error removing old attachment for " + player.getName() + ": " + ex.getMessage());
                }
            }

            PermissionAttachment attach = player.addAttachment(this);
            attachments.put(id, attach);

            getLogger().fine(tlRaw("console.permission.refreshed-attachment", player.getName()));

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
                getLogger().fine(tlRaw("console.permission.recalculated", player.getName()));
            } catch (Throwable t) {
                getLogger().warning("Failed to recalculate permissions for " + player.getName() + ": " + t.getMessage());
            }
            try {
                player.updateCommands();
                getLogger().fine(tlRaw("console.permission.updated-commands", player.getName()));
            } catch (Throwable t) {
                getLogger().warning("Failed to update command tree for " + player.getName() + ": " + t.getMessage());
            }
        } catch (Exception e) {
            getLogger().severe(tlRaw("console.permission.error-refresh", player.getName(), e.getMessage()));
        }
    }

    /**
     * Refreshes permission attachment for a specific player.
     * Uses Folia's player region scheduler to ensure thread safety.
     */
    public void refreshPlayerAttachment(Player player) {
        if (player == null || permissionService == null) return;

        try {
            player.getScheduler().run(this, scheduledTask -> {
                refreshPlayerAttachmentSync(player);
            }, null);
        } catch (Throwable t) {
            getLogger().warning("Could not schedule refresh on player region for " + player.getName() + ": " + t.getMessage());
            refreshPlayerAttachmentSync(player);
        }
    }

    /**
     * Refreshes permission attachments for all online players.
     */
    public void refreshAllAttachments() {
        Player[] onlinePlayers = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        if (onlinePlayers.length == 0) return;

        try {
            Bukkit.getGlobalRegionScheduler().run(this, scheduledTask -> {
                for (Player p : onlinePlayers) {
                    refreshPlayerAttachment(p);
                }
            });
        } catch (Throwable t) {
            getLogger().warning("Global region scheduler unavailable, falling back to direct execution: " + t.getMessage());
            for (Player p : onlinePlayers) {
                refreshPlayerAttachment(p);
            }
        }
    }

    /**
     * Convenience method to refresh a player by UUID (if online).
     */
    public void refreshPlayer(UUID playerId) {
        if (playerId == null) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            refreshPlayerAttachment(player);
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
                    getLogger().fine(tlRaw("console.permission.attachment-cleaned", playerId));
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
        getLogger().info(tlRaw("console.plugin.attachments-cleaned"));
    }

    // ──────────────────────────────────────────────
    //  FoliaPermsAPI Implementation
    // ──────────────────────────────────────────────

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
