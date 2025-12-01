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
            getLogger().severe("FoliaPerms is a Folia-only plugin!");
            getLogger().severe("It appears you are running a normal Bukkit/Paper/Spigot server.");
            getLogger().severe("This plugin will now disable itself, goodbye.");
            getServer().getPluginManager().disablePlugin(this);
        } else {
            getLogger().info("Folia environment detected. FoliaPerms is ready to enable.");
            getLogger().info("Loading permissions data...");
        }
    }
    @Override
    public void onEnable() {
        getLogger().info("FoliaPerms enabled successfully. Welcome to the Folia environment!");

        this.permissionService = new PermissionService(this);
        try {
            this.permissionService.load();
            getLogger().info("Loaded permissions data.");
        } catch (Exception e) {
            getLogger().severe("Failed to load permissions data: " + e.getMessage());
        }

        if (getCommand("fperm") != null) {
            getCommand("fperm").setExecutor(new FpermCommand(this));
            getCommand("fperm").setTabCompleter(new kaiakk.foliaPerms.commands.FpermTabCompleter(this));
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getServer().getServicesManager().register(FoliaPermsAPI.class, this, this, ServicePriority.Normal);
        getLogger().info("FoliaPerms API registered with ServicesManager.");

            try {
                permissionService.gatherRegisteredPermissions(this);
                getLogger().info("Gathered " + permissionService.getRegisteredPermissions().size() + " permissions from plugins.");
                permissionService.getRegisteredPermissions().forEach(p -> getLogger().info(" - " + p));
                refreshAllAttachments();
            } catch (Exception e) {
                getLogger().severe("Failed to gather registered permissions: " + e.getMessage());
            }
    }

    @Override
    public void onDisable() {
        getLogger().info("Saving permissions...");
        if (this.permissionService != null) {
            try {
                this.permissionService.save();
                getLogger().info("Permissions saved.");
            } catch (Exception e) {
                getLogger().severe("Failed to save permissions: " + e.getMessage());
            }
        }
    }

    public PermissionService getPermissionService() {
        return this.permissionService;
    }

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

            getLogger().info("Created/updated PermissionAttachment for " + player.getName());

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
                getLogger().info("Recalculated permissions for " + player.getName());
                try {
                    player.updateCommands();
                    getLogger().info("Updated command tree for " + player.getName());
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

    public void refreshAllAttachments() {
        for (Player p : Bukkit.getOnlinePlayers()) refreshPlayerAttachment(p);
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