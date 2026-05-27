package kaiakk.foliaPerms.permissions;

import kaiakk.foliaPerms.FoliaPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core permission system logic and data management.
 * Handles user/group permissions with caching and async operations.
 * Version: 1.13.0
 */
public class PermissionService {
    private final JavaPlugin plugin;
    private final YamlStorage storage;

    private final Map<UUID, UserData> users = new ConcurrentHashMap<>();
    private final Map<String, GroupData> groups = new ConcurrentHashMap<>();
    private final java.util.Set<String> registeredPermissions = ConcurrentHashMap.newKeySet();
    
    // Cache for sorted permissions (for UI efficiency)
    private List<String> cachedSortedPermissions = null;

    public PermissionService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.storage = new YamlStorage(plugin);
    }

    public void load() {
        users.clear();
        groups.clear();
        Map<UUID, UserData> loadedUsers = storage.loadUsers();
        Map<String, GroupData> loadedGroups = storage.loadGroups();
        users.putAll(loadedUsers);
        groups.putAll(loadedGroups);
        plugin.getLogger().info("Loaded " + users.size() + " users and " + groups.size() + " groups from permissions.yml");
        if (!users.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (UUID id : users.keySet()) {
                if (count++ < 5) sb.append(id.toString()).append(", ");
            }
            if (users.size() > 5) sb.append("... and ").append(users.size() - 5).append(" more");
            plugin.getLogger().fine("Loaded user UUIDs: " + sb.toString());
        }
        if (!groups.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String g : groups.keySet()) {
                if (count++ < 5) sb.append(g).append(", ");
            }
            if (groups.size() > 5) sb.append("... and ").append(groups.size() - 5).append(" more");
            plugin.getLogger().fine("Loaded groups: " + sb.toString());
        }
    }

    public void loadAsync(Runnable callback) {
        plugin.getLogger().info("Scheduling async permissions load (background thread)");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, UserData> loadedUsers = storage.loadUsers();
            Map<String, GroupData> loadedGroups = storage.loadGroups();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                users.clear();
                groups.clear();
                users.putAll(loadedUsers);
                groups.putAll(loadedGroups);
                plugin.getLogger().info("Loaded " + users.size() + " users and " + groups.size() + " groups from permissions.yml");
                if (callback != null) {
                    try { callback.run(); } catch (Throwable t) { kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Exception in load callback", t); }
                }
            });
        });
    }

    public void gatherRegisteredPermissions(org.bukkit.plugin.Plugin plugin) {
        registeredPermissions.clear();
        cachedSortedPermissions = null; // Invalidate cache
        
        var pm = plugin.getServer().getPluginManager();
        for (org.bukkit.permissions.Permission p : pm.getPermissions()) {
            if (p == null) continue;
            registeredPermissions.add(p.getName());
            if (p.getChildren() != null) {
                registeredPermissions.addAll(p.getChildren().keySet());
            }
        }

        try {
            Object server = plugin.getServer();
            java.lang.reflect.Method getCommandMap = server.getClass().getMethod("getCommandMap");
            Object commandMap = getCommandMap.invoke(server);
            if (commandMap != null) {
                java.lang.reflect.Field knownField = null;
                Class<?> cmClass = commandMap.getClass();
                while (cmClass != null) {
                    try {
                        knownField = cmClass.getDeclaredField("knownCommands");
                        break;
                    } catch (NoSuchFieldException ignored) {
                        cmClass = cmClass.getSuperclass();
                    }
                }
                if (knownField != null) {
                    knownField.setAccessible(true);
                    Object known = knownField.get(commandMap);
                    if (known instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, org.bukkit.command.Command> knownMap = (java.util.Map<String, org.bukkit.command.Command>) known;
                        for (org.bukkit.command.Command cmd : knownMap.values()) {
                            if (cmd == null) continue;
                            String perm = cmd.getPermission();
                            if (perm != null && !perm.isBlank()) registeredPermissions.add(perm);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            kaiakk.foliaPerms.internal.ErrorHandler.warn(plugin, "Could not gather command-map permissions!", t);
        }
    }

    /**
     * Gets registered permissions, sorted and cached for UI efficiency.
     */
    public List<String> getRegisteredPermissionsSorted() {
        if (cachedSortedPermissions == null) {
            cachedSortedPermissions = registeredPermissions.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
        }
        return cachedSortedPermissions;
    }

    public java.util.Set<String> getRegisteredPermissions() {
        return java.util.Collections.unmodifiableSet(registeredPermissions);
    }

    public java.util.Set<String> getAllowedPermissions(UUID id) {
        var result = new java.util.HashSet<String>();
        for (String node : registeredPermissions) {
            if (hasPermission(id, node)) result.add(node);
        }
        return result;
    }

    public void save() throws IOException {
        storage.save(users, groups);
    }

    public void saveAsync() {
        Map<UUID, UserData> usersSnapshot = new HashMap<>();
        for (Map.Entry<UUID, UserData> e : users.entrySet()) {
            UUID id = e.getKey();
            UserData orig = e.getValue();
            UserData copy = new UserData(id);
            copy.getPermissions().addAll(orig.getPermissions());
            copy.getGroups().addAll(orig.getGroups());
            usersSnapshot.put(id, copy);
        }

        Map<String, GroupData> groupsSnapshot = new HashMap<>();
        for (Map.Entry<String, GroupData> e : groups.entrySet()) {
            String key = e.getKey();
            GroupData orig = e.getValue();
            GroupData copy = new GroupData(key);
            copy.getPermissions().addAll(orig.getPermissions());
            copy.getMembers().addAll(orig.getMembers());
            groupsSnapshot.put(key, copy);
        }

        plugin.getLogger().fine("Scheduling async permissions save.");
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    storage.save(usersSnapshot, groupsSnapshot);
                    plugin.getLogger().fine("Async save completed successfully.");
                } catch (IOException ex) {
                    plugin.getLogger().severe("Async save failed: " + ex.getMessage());
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Async scheduler unavailable, falling back to background thread: " + t.getMessage());
            Thread thr = new Thread(() -> {
                try {
                    storage.save(usersSnapshot, groupsSnapshot);
                    plugin.getLogger().fine("Background thread save completed.");
                } catch (IOException ex) {
                    plugin.getLogger().severe("Async save failed: " + ex.getMessage());
                }
            }, "FoliaPerms-Save");
            thr.setDaemon(true);
            thr.start();
        }
    }

    public UserData getOrCreateUser(UUID id) {
        return users.computeIfAbsent(id, UserData::new);
    }

    public UserData getUser(UUID id) {
        return users.get(id);
    }

    public void addUserPermission(UUID id, String node) {
        String normalized = node == null ? null : node.toLowerCase();
        if (normalized == null) return;
        getOrCreateUser(id).addPermission(normalized);
        registeredPermissions.add(normalized);
        cachedSortedPermissions = null; // Invalidate cache
        plugin.getLogger().info("Added permission '" + normalized + "' to user " + id.toString());
        try {
            if (plugin instanceof FoliaPerms) {
                var fp = (FoliaPerms) plugin;
                var player = fp.getServer().getPlayer(id);
                if (player != null) {
                    if (Bukkit.isPrimaryThread()) {
                        fp.refreshPlayerAttachment(player);
                    } else {
                        try {
                            plugin.getServer().getScheduler().runTask(plugin, () -> fp.refreshPlayerAttachment(player));
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public void removeUserPermission(UUID id, String node) {
        UserData ud = getUser(id);
        if (ud != null) ud.removePermission(node);
        plugin.getLogger().info("Removed permission '" + node + "' from user " + id.toString());
        try {
            if (plugin instanceof FoliaPerms) {
                var fp = (FoliaPerms) plugin;
                var player = fp.getServer().getPlayer(id);
                if (player != null) {
                    if (Bukkit.isPrimaryThread()) {
                        fp.refreshPlayerAttachment(player);
                    } else {
                        try {
                            plugin.getServer().getScheduler().runTask(plugin, () -> fp.refreshPlayerAttachment(player));
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public GroupData createGroup(String name) {
        String key = name.toLowerCase();
        return groups.computeIfAbsent(key, GroupData::new);
    }

    public GroupData getGroup(String name) {
        if (name == null) return null;
        return groups.get(name.toLowerCase());
    }

    public void addGroupPermission(String name, String node) {
        if (node == null) return;
        String normalized = node.toLowerCase();
        GroupData gd = createGroup(name);
        gd.addPermission(normalized);
        registeredPermissions.add(normalized);
        cachedSortedPermissions = null; // Invalidate cache
        plugin.getLogger().info("Added group permission '" + normalized + "' to group " + name);
        try {
            if (plugin instanceof FoliaPerms) {
                JavaPlugin p = plugin;
                plugin.getServer().getScheduler().runTask(p, () -> {
                    var fp = (FoliaPerms) plugin;
                    fp.refreshAllAttachments();
                });
            }
        } catch (Throwable ignored) {}
    }


    public void addUserToGroup(UUID id, String group) {
        UserData ud = getOrCreateUser(id);
        ud.addGroup(group);
        GroupData gd = createGroup(group);
        gd.addMember(id.toString());
        try {
            if (plugin instanceof FoliaPerms) {
                JavaPlugin p = plugin;
                plugin.getServer().getScheduler().runTask(p, () -> {
                    var fp = (FoliaPerms) plugin;
                    var player = fp.getServer().getPlayer(id);
                    if (player != null) fp.refreshPlayerAttachment(player);
                });
            }
        } catch (Throwable ignored) {}
    }

    public void removeUserFromGroup(UUID id, String group) {
        if (group == null) return;
        String key = group.toLowerCase();
        UserData ud = getUser(id);
        if (ud != null) ud.removeGroup(group);
        GroupData gd = groups.get(key);
        if (gd != null) gd.removeMember(id.toString());
        plugin.getLogger().info("Removed user " + id + " from group " + group);
        try {
            if (plugin instanceof FoliaPerms) {
                JavaPlugin p = plugin;
                plugin.getServer().getScheduler().runTask(p, () -> {
                    var fp = (FoliaPerms) plugin;
                    var player = fp.getServer().getPlayer(id);
                    if (player != null) fp.refreshPlayerAttachment(player);
                });
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Checks if a player has a permission.
     * Supports wildcard permissions (e.g., "plugin.*")
     */
    public boolean hasPermission(UUID id, String node) {
        if (node == null) return false;
        String normalized = node.toLowerCase();
        UserData ud = users.get(id);
        if (ud != null) {
            // Direct permission check
            if (ud.getPermissions().contains(normalized)) return true;
            
            // Wildcard check
            if (ud.getPermissions().contains(normalized + ".*")) return true;
            
            // Check groups
            for (String g : ud.getGroups()) {
                if (checkGroupPermission(g, normalized)) return true;
            }
        }
        return false;
    }

    /**
     * Helper method to check group permissions recursively.
     */
    private boolean checkGroupPermission(String groupName, String node) {
        GroupData gd = groups.get(groupName.toLowerCase());
        if (gd != null) {
            if (gd.getPermissions().contains(node)) return true;
            if (gd.getPermissions().contains(node + ".*")) return true;
        }
        return false;
    }

    public boolean groupHasDirectPermission(String name, String node) {
        if (name == null || node == null) return false;
        GroupData gd = groups.get(name.toLowerCase());
        return gd != null && gd.getPermissions().contains(node.toLowerCase());
    }

    public boolean userHasDirectPermission(UUID id, String node) {
        if (id == null || node == null) return false;
        UserData ud = users.get(id);
        return ud != null && ud.getPermissions().contains(node.toLowerCase());
    }

    public void removeGroupPermission(String name, String node) {
        if (name == null || node == null) return;
        GroupData gd = groups.get(name.toLowerCase());
        if (gd != null) gd.removePermission(node.toLowerCase());
        plugin.getLogger().info("Removed permission '" + node + "' from group " + name);
        try {
            if (plugin instanceof FoliaPerms) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    var fp = (FoliaPerms) plugin;
                    fp.refreshAllAttachments();
                });
            }
        } catch (Throwable ignored) {}
    }

    public Map<UUID, UserData> getUsers() {
        return users;
    }

    public Map<String, GroupData> getGroups() {
        return groups;
    }
}