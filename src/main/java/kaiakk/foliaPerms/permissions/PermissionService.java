package kaiakk.foliaPerms.permissions;

import kaiakk.foliaPerms.FoliaPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core permission system logic and data management.
 * Handles user/group permissions with caching and async operations.
 * Supports group inheritance (a group can inherit permissions from parent groups)
 * and a default group that all players implicitly belong to.
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
        
        // Ensure the default group always exists in memory
        ensureDefaultGroupExists();
        
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
                ensureDefaultGroupExists();
                plugin.getLogger().info("Loaded " + users.size() + " users and " + groups.size() + " groups from permissions.yml");
                if (callback != null) {
                    try { callback.run(); } catch (Throwable t) { kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Exception in load callback", t); }
                }
            });
        });
    }

    /**
     * Ensures the default group exists in the groups map.
     * This is called after every load operation.
     */
    private void ensureDefaultGroupExists() {
        groups.computeIfAbsent(YamlStorage.DEFAULT_GROUP_NAME, k -> {
            plugin.getLogger().info("Created implicit default group '" + YamlStorage.DEFAULT_GROUP_NAME + "' in memory.");
            return new GroupData(YamlStorage.DEFAULT_GROUP_NAME);
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
            copy.getParentsMutable().addAll(orig.getParentsMutable());
            groupsSnapshot.put(key, copy);
        }

        // Use the Folia/Paper AsyncScheduler which works on both platforms.
        // Bukkit.getScheduler().runTaskAsynchronously() is not available on Folia.
        plugin.getLogger().fine("Scheduling async permissions save.");
        Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
            try {
                storage.save(usersSnapshot, groupsSnapshot);
                plugin.getLogger().fine("Async save completed successfully.");
            } catch (IOException ex) {
                plugin.getLogger().severe("Async save failed: " + ex.getMessage());
            }
        });
    }

    public UserData getOrCreateUser(UUID id) {
        UserData ud = users.computeIfAbsent(id, UserData::new);
        // Ensure every user has exactly one group.
        // New users start in the default group.
        // If a user has somehow ended up with no groups, re-assign to default.
        if (ud.getGroups().isEmpty()) {
            ud.addGroup(YamlStorage.DEFAULT_GROUP_NAME);
            GroupData gd = getOrCreateGroup(YamlStorage.DEFAULT_GROUP_NAME);
            gd.addMember(id.toString());
            plugin.getLogger().fine("Auto-assigned user " + id + " to default group.");
        }
        // Enforce exactly one group: if a user somehow has more than one group,
        // collapse to just the first one (excluding default if possible).
        if (ud.getGroups().size() > 1) {
            String primary = ud.getGroups().stream()
                .filter(g -> !g.equals(YamlStorage.DEFAULT_GROUP_NAME))
                .findFirst()
                .orElse(YamlStorage.DEFAULT_GROUP_NAME);
            // Remove all groups, then add back only the primary
            for (String oldGroup : new java.util.HashSet<>(ud.getGroups())) {
                ud.removeGroup(oldGroup);
                GroupData oldGd = groups.get(oldGroup.toLowerCase());
                if (oldGd != null) oldGd.removeMember(id.toString());
            }
            ud.addGroup(primary);
            GroupData primaryGd = getOrCreateGroup(primary);
            primaryGd.addMember(id.toString());
            plugin.getLogger().fine("Collapsed user " + id + " to single group: " + primary);
        }
        return ud;
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

    /**
     * Gets or creates a group. Unlike createGroup, this ensures the group
     * is initialized even if it doesn't exist yet.
     */
    private GroupData getOrCreateGroup(String name) {
        return groups.computeIfAbsent(name.toLowerCase(), GroupData::new);
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


    /**
     * Adds a user to a group.
     * <p>
     * <strong>Single-group enforcement:</strong> A user can only belong to
     * exactly one group at a time. Adding a user to a new group will
     * automatically remove them from their current group (including the
     * default group). This ensures every user has precisely one primary group.
     * </p>
     */
    public void addUserToGroup(UUID id, String group) {
        if (group == null) return;
        String newGroupKey = group.toLowerCase();
        
        UserData ud = getOrCreateUser(id);
        
        // Find the user's current group(s) before we change anything
        Set<String> currentGroups = new HashSet<>(ud.getGroups());
        
        // If user is already in this exact group, nothing to do
        if (currentGroups.size() == 1 && currentGroups.contains(newGroupKey)) {
            plugin.getLogger().fine("User " + id + " is already in group '" + group + "'.");
            return;
        }
        
        // Step 1: Remove user from ALL current groups
        for (String oldGroup : currentGroups) {
            ud.removeGroup(oldGroup);
            GroupData oldGd = groups.get(oldGroup.toLowerCase());
            if (oldGd != null) {
                oldGd.removeMember(id.toString());
                plugin.getLogger().fine("Removed user " + id + " from previous group '" + oldGroup + "'.");
            }
        }
        
        // Step 2: Add user to the new group (this becomes their ONLY group)
        ud.addGroup(newGroupKey);
        GroupData gd = createGroup(group);
        gd.addMember(id.toString());
        plugin.getLogger().info("User " + id + " is now in group '" + group + "' (single-group mode).");
        
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
     * Removes a user from a group.
     * <p>
     * Users cannot be left group-less. If the user's only group is being
     * removed, they will be automatically re-assigned to the default group.
     * Users in the default group cannot be removed from it.
     * </p>
     */
    public void removeUserFromGroup(UUID id, String group) {
        if (group == null) return;
        String key = group.toLowerCase();
        
        UserData ud = getUser(id);
        if (ud == null) {
            plugin.getLogger().warning("Cannot remove user " + id + " from group: user not found.");
            return;
        }
        
        // Check if the user is actually in this group
        if (!ud.getGroups().contains(key)) {
            plugin.getLogger().warning("User " + id + " is not in group '" + group + "'.");
            return;
        }
        
        // If user is only in the default group, prevent removal
        if (key.equals(YamlStorage.DEFAULT_GROUP_NAME) && ud.getGroups().size() == 1) {
            plugin.getLogger().warning("Cannot remove user " + id + " from the default group (they must always have a group).");
            return;
        }
        
        // Remove from the specified group
        ud.removeGroup(key);
        GroupData gd = groups.get(key);
        if (gd != null) {
            gd.removeMember(id.toString());
        }
        plugin.getLogger().info("Removed user " + id + " from group '" + group + "'.");
        
        // If user now has no groups, auto-assign back to default
        if (ud.getGroups().isEmpty()) {
            ud.addGroup(YamlStorage.DEFAULT_GROUP_NAME);
            GroupData defaultGd = getOrCreateGroup(YamlStorage.DEFAULT_GROUP_NAME);
            defaultGd.addMember(id.toString());
            plugin.getLogger().info("Auto-assigned user " + id + " back to default group after removal from '" + group + "'.");
        }
        
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
     * Falls back to the default group if the player has no explicit groups.
     * Resolves group inheritance chains recursively.
     */
    public boolean hasPermission(UUID id, String node) {
        if (node == null) return false;
        String normalized = node.toLowerCase();
        
        // Collect all applicable group names for this user
        Set<String> groupsToCheck = new HashSet<>();
        
        UserData ud = users.get(id);
        if (ud != null) {
            // Direct permission check (user-specific override)
            if (ud.getPermissions().contains(normalized)) return true;
            
            // Wildcard check for direct user permissions
            if (ud.getPermissions().contains(normalized + ".*")) return true;
            
            // Add user's explicit groups
            groupsToCheck.addAll(ud.getGroups());
        }
        
        // If the user has no groups at all, fall back to the default group
        if (groupsToCheck.isEmpty()) {
            groupsToCheck.add(YamlStorage.DEFAULT_GROUP_NAME);
        }
        
        // Check all groups with inheritance resolution
        return checkAnyGroupPermission(groupsToCheck, normalized);
    }

    /**
     * Checks if any of the given groups (or their ancestors via inheritance)
     * grant the specified permission node.
     */
    private boolean checkAnyGroupPermission(Set<String> groupNames, String node) {
        // Use a visited set to detect cycles across the entire inheritance graph
        Set<String> visited = ConcurrentHashMap.newKeySet();
        for (String g : groupNames) {
            if (checkGroupPermissionRecursive(g, node, visited)) return true;
        }
        return false;
    }

    /**
     * Recursively checks a group and all its parent (ancestor) groups
     * for the given permission node, with cycle detection.
     */
    private boolean checkGroupPermissionRecursive(String groupName, String node, Set<String> visited) {
        String key = groupName.toLowerCase();
        GroupData gd = groups.get(key);
        if (gd == null) return false;
        
        // Cycle detection
        if (!visited.add(key)) return false;
        
        // Check this group's own permissions
        if (gd.getPermissions().contains(node)) return true;
        if (gd.getPermissions().contains(node + ".*")) return true;
        
        // Recursively check parent groups (ancestors)
        for (String parentName : gd.getParents()) {
            if (checkGroupPermissionRecursive(parentName, node, visited)) return true;
        }
        
        return false;
    }

    /**
     * Checks if a group (including inherited permissions) has a specific permission.
     * This is a public convenience method that internally uses the recursive check.
     */
    public boolean groupHasPermission(String name, String node) {
        if (name == null || node == null) return false;
        Set<String> visited = ConcurrentHashMap.newKeySet();
        return checkGroupPermissionRecursive(name, node.toLowerCase(), visited);
    }

    /**
     * Checks if a group has a permission directly (without inheritance).
     */
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

    // ──────────────────────────────────────────────
    //  Group Inheritance Management
    // ──────────────────────────────────────────────

    /**
     * Sets a group to inherit permissions from a parent group.
     *
     * @param groupName the name of the child group
     * @param parentName the name of the parent group to inherit from
     * @return true if the inheritance was added successfully,
     *         false if it would create a circular dependency
     */
    public boolean addGroupInheritance(String groupName, String parentName) {
        if (groupName == null || parentName == null) return false;
        String childKey = groupName.toLowerCase();
        String parentKey = parentName.toLowerCase();
        
        // Prevent self-inheritance
        if (childKey.equals(parentKey)) {
            plugin.getLogger().warning("Cannot set a group to inherit from itself: " + groupName);
            return false;
        }
        
        GroupData child = createGroup(groupName);
        
        // Check for circular dependency before adding
        if (wouldCreateCycle(childKey, parentKey)) {
            plugin.getLogger().warning("Cannot add inheritance: " + groupName + " -> " + parentName + " would create a circular dependency.");
            return false;
        }
        
        child.addParent(parentName);
        plugin.getLogger().info("Group '" + groupName + "' now inherits from '" + parentName + "'.");
        
        // Refresh all online players
        try {
            if (plugin instanceof FoliaPerms) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    var fp = (FoliaPerms) plugin;
                    fp.refreshAllAttachments();
                });
            }
        } catch (Throwable ignored) {}
        
        return true;
    }

    /**
     * Removes an inheritance relationship.
     *
     * @param groupName the child group
     * @param parentName the parent group to stop inheriting from
     */
    public void removeGroupInheritance(String groupName, String parentName) {
        if (groupName == null || parentName == null) return;
        GroupData gd = groups.get(groupName.toLowerCase());
        if (gd != null) {
            gd.removeParent(parentName);
            plugin.getLogger().info("Group '" + groupName + "' no longer inherits from '" + parentName + "'.");
            
            // Refresh all online players
            try {
                if (plugin instanceof FoliaPerms) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        var fp = (FoliaPerms) plugin;
                        fp.refreshAllAttachments();
                    });
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Checks if adding an inheritance edge (child -> parent) would create a cycle.
     * Uses DFS from parent upwards: if we can reach childKey from parentKey,
     * then adding childKey -> parentKey would create a cycle.
     */
    private boolean wouldCreateCycle(String childKey, String parentKey) {
        // Start from parentKey and walk UP the inheritance tree.
        // If we encounter childKey, there's a cycle.
        Set<String> visited = ConcurrentHashMap.newKeySet();
        return canReach(parentKey, childKey, visited);
    }

    /**
     * DFS helper: returns true if 'target' is reachable from 'current' via parent links.
     */
    private boolean canReach(String current, String target, Set<String> visited) {
        if (!visited.add(current)) return false;
        GroupData gd = groups.get(current);
        if (gd == null) return false;
        for (String parent : gd.getParents()) {
            if (parent.equals(target) || canReach(parent, target, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the ordered inheritance chain for a group (breadth-first).
     * The list starts with the group itself, followed by parents, grandparents, etc.
     */
    public List<String> getGroupInheritanceChain(String groupName) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = ConcurrentHashMap.newKeySet();
        collectInheritanceChain(groupName, chain, visited);
        return chain;
    }

    private void collectInheritanceChain(String groupName, List<String> chain, Set<String> visited) {
        String key = groupName.toLowerCase();
        if (!visited.add(key)) return;
        chain.add(key);
        GroupData gd = groups.get(key);
        if (gd != null) {
            for (String parent : gd.getParents()) {
                collectInheritanceChain(parent, chain, visited);
            }
        }
    }

    /**
     * Deletes a group from the system.
     * <p>
     * The default group ({@value YamlStorage#DEFAULT_GROUP_NAME}) <strong>cannot</strong> be
     * deleted under any circumstances. All users who were members of the deleted
     * group will be re-assigned to the default group, preserving the invariant
     * that every user has exactly one group.
     * </p>
     *
     * @param name the name of the group to delete
     * @return true if the group was deleted successfully;
     *         false if it was the protected default group or did not exist
     */
    public boolean deleteGroup(String name) {
        if (name == null) return false;
        String key = name.toLowerCase();

        // Protect the default group from deletion
        if (key.equals(YamlStorage.DEFAULT_GROUP_NAME)) {
            plugin.getLogger().warning("Attempted to delete the default group '" + YamlStorage.DEFAULT_GROUP_NAME + "' – operation blocked.");
            return false;
        }

        GroupData gd = groups.get(key);
        if (gd == null) {
            plugin.getLogger().warning("Group '" + name + "' does not exist, cannot delete.");
            return false;
        }

        // Remove all users from this group and re-assign them to default
        for (String memberUuidStr : new java.util.HashSet<>(gd.getMembers())) {
            try {
                UUID memberId = UUID.fromString(memberUuidStr);
                UserData ud = users.get(memberId);
                if (ud != null) {
                    ud.removeGroup(key);
                }
                // If user has no groups left, assign to default
                if (ud != null && ud.getGroups().isEmpty()) {
                    ud.addGroup(YamlStorage.DEFAULT_GROUP_NAME);
                    getOrCreateGroup(YamlStorage.DEFAULT_GROUP_NAME).addMember(memberUuidStr);
                    plugin.getLogger().fine("Re-assigned user " + memberId + " to default group after group '" + name + "' was deleted.");
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid member UUID '" + memberUuidStr + "' in group '" + name + "'.");
            }
        }

        // Remove the group from the groups map
        groups.remove(key);
        plugin.getLogger().info("Group '" + name + "' has been deleted. All members re-assigned to default group.");

        // Refresh all players
        try {
            if (plugin instanceof FoliaPerms) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    var fp = (FoliaPerms) plugin;
                    fp.refreshAllAttachments();
                });
            }
        } catch (Throwable ignored) {}

        return true;
    }

    public Map<UUID, UserData> getUsers() {
        return users;
    }

    public Map<String, GroupData> getGroups() {
        return groups;
    }
}