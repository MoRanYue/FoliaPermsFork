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
 * Version: 0.1.0+26.1.2
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

    /**
     * Convenience accessor for localized console messages.
     */
    private String tlRaw(String key, Object... args) {
        if (plugin instanceof FoliaPerms) {
            return ((FoliaPerms) plugin).tlRaw(key, args);
        }
        return key;
    }

    /**
     * Convenience accessor for localized colored messages.
     */
    private String tl(String key, Object... args) {
        if (plugin instanceof FoliaPerms) {
            return ((FoliaPerms) plugin).tl(key, args);
        }
        return key;
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

        plugin.getLogger().info(tlRaw("console.permission.loaded", users.size(), groups.size()));
        if (!users.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (UUID id : users.keySet()) {
                if (count++ < 5) sb.append(id.toString()).append(", ");
            }
            if (users.size() > 5) sb.append("... and ").append(users.size() - 5).append(" more");
            plugin.getLogger().fine(tlRaw("console.player.uuids-loaded", sb.toString()));
        }
        if (!groups.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String g : groups.keySet()) {
                if (count++ < 5) sb.append(g).append(", ");
            }
            if (groups.size() > 5) sb.append("... and ").append(groups.size() - 5).append(" more");
            plugin.getLogger().fine(tlRaw("console.player.groups-loaded", sb.toString()));
        }
    }

    public void loadAsync(Runnable callback) {
        plugin.getLogger().info(tlRaw("console.storage.scheduling-async"));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, UserData> loadedUsers = storage.loadUsers();
            Map<String, GroupData> loadedGroups = storage.loadGroups();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                users.clear();
                groups.clear();
                users.putAll(loadedUsers);
                groups.putAll(loadedGroups);
                ensureDefaultGroupExists();
                plugin.getLogger().info(tlRaw("console.permission.loaded", users.size(), groups.size()));
                if (callback != null) {
                    try { callback.run(); } catch (Throwable t) { kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Exception in load callback", t); }
                }
            });
        });
    }

    /**
     * Ensures the default group exists in the groups map.
     */
    private void ensureDefaultGroupExists() {
        groups.computeIfAbsent(YamlStorage.DEFAULT_GROUP_NAME, k -> {
            plugin.getLogger().info(tlRaw("console.plugin.default-group-created", YamlStorage.DEFAULT_GROUP_NAME));
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

        plugin.getLogger().fine(tlRaw("console.storage.scheduling-async"));
        Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
            try {
                storage.save(usersSnapshot, groupsSnapshot);
                plugin.getLogger().fine(tlRaw("console.storage.async-save-success"));
            } catch (IOException ex) {
                plugin.getLogger().severe(tlRaw("console.storage.async-save-failed", ex.getMessage()));
            }
        });
    }

    public UserData getOrCreateUser(UUID id) {
        UserData ud = users.computeIfAbsent(id, UserData::new);
        // Ensure every user has exactly one group.
        if (ud.getGroups().isEmpty()) {
            ud.addGroup(YamlStorage.DEFAULT_GROUP_NAME);
            GroupData gd = getOrCreateGroup(YamlStorage.DEFAULT_GROUP_NAME);
            gd.addMember(id.toString());
            plugin.getLogger().fine(tlRaw("console.permission.auto-assigned", id));
        }
        // Enforce exactly one group
        if (ud.getGroups().size() > 1) {
            String primary = ud.getGroups().stream()
                .filter(g -> !g.equals(YamlStorage.DEFAULT_GROUP_NAME))
                .findFirst()
                .orElse(YamlStorage.DEFAULT_GROUP_NAME);
            for (String oldGroup : new java.util.HashSet<>(ud.getGroups())) {
                ud.removeGroup(oldGroup);
                GroupData oldGd = groups.get(oldGroup.toLowerCase());
                if (oldGd != null) oldGd.removeMember(id.toString());
            }
            ud.addGroup(primary);
            GroupData primaryGd = getOrCreateGroup(primary);
            primaryGd.addMember(id.toString());
            plugin.getLogger().fine(tlRaw("console.permission.collapsed-user", id, primary));
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
        plugin.getLogger().info(tlRaw("console.permission.added-user", normalized, id.toString()));
        try {
            if (plugin instanceof FoliaPerms) {
                ((FoliaPerms) plugin).refreshPlayer(id);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh player " + id + " after adding permission: " + e.getMessage());
        }
    }

    public void removeUserPermission(UUID id, String node) {
        UserData ud = getUser(id);
        if (ud != null) ud.removePermission(node);
        plugin.getLogger().info(tlRaw("console.permission.removed-user", node, id.toString()));
        try {
            if (plugin instanceof FoliaPerms) {
                ((FoliaPerms) plugin).refreshPlayer(id);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh player " + id + " after removing permission: " + e.getMessage());
        }
    }

    public GroupData createGroup(String name) {
        String key = name.toLowerCase();
        return groups.computeIfAbsent(key, GroupData::new);
    }

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
        plugin.getLogger().info(tlRaw("console.permission.added-group", normalized, name));
        try {
            if (plugin instanceof FoliaPerms) {
                ((FoliaPerms) plugin).refreshAllAttachments();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh attachments after adding group permission: " + e.getMessage());
        }
    }

    public void addUserToGroup(UUID id, String group) {
        if (group == null) return;
        String newGroupKey = group.toLowerCase();

        UserData ud = getOrCreateUser(id);

        Set<String> currentGroups = new HashSet<>(ud.getGroups());

        if (currentGroups.size() == 1 && currentGroups.contains(newGroupKey)) {
            plugin.getLogger().fine(tlRaw("console.permission.user-already-group", id, group));
            return;
        }

        for (String oldGroup : currentGroups) {
            ud.removeGroup(oldGroup);
            GroupData oldGd = groups.get(oldGroup.toLowerCase());
            if (oldGd != null) {
                oldGd.removeMember(id.toString());
                plugin.getLogger().fine(tlRaw("console.permission.user-removed-prev", id, oldGroup));
            }
        }

        ud.addGroup(newGroupKey);
        GroupData gd = createGroup(group);
        gd.addMember(id.toString());
        plugin.getLogger().info(tlRaw("console.permission.user-added-group", id, group));

        try {
            if (plugin instanceof FoliaPerms) {
                ((FoliaPerms) plugin).refreshPlayer(id);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh player " + id + " after adding to group: " + e.getMessage());
        }
    }

    public void removeUserFromGroup(UUID id, String group) {
        if (group == null) return;
        String key = group.toLowerCase();

        UserData ud = getUser(id);
        if (ud == null) {
            plugin.getLogger().warning(tlRaw("console.permission.user-not-found", id, group));
            return;
        }

        if (!ud.getGroups().contains(key)) {
            plugin.getLogger().warning(tlRaw("console.permission.user-not-in-group", id, group));
            return;
        }

        if (key.equals(YamlStorage.DEFAULT_GROUP_NAME) && ud.getGroups().size() == 1) {
            plugin.getLogger().warning(tlRaw("console.permission.user-cannot-remove-default", id));
            return;
        }

        ud.removeGroup(key);
        GroupData gd = groups.get(key);
        if (gd != null) {
            gd.removeMember(id.toString());
        }
        plugin.getLogger().info(tlRaw("console.permission.user-removed-group", id, group));

        if (ud.getGroups().isEmpty()) {
            ud.addGroup(YamlStorage.DEFAULT_GROUP_NAME);
            GroupData defaultGd = getOrCreateGroup(YamlStorage.DEFAULT_GROUP_NAME);
            defaultGd.addMember(id.toString());
            plugin.getLogger().info(tlRaw("console.permission.user-auto-default", id, group));
        }

        try {
            if (plugin instanceof FoliaPerms) {
                ((FoliaPerms) plugin).refreshPlayer(id);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh player " + id + " after removing from group: " + e.getMessage());
        }
    }

    public boolean hasPermission(UUID id, String node) {
        if (node == null) return false;
        String normalized = node.toLowerCase();

        Set<String> groupsToCheck = new HashSet<>();

        UserData ud = users.get(id);
        if (ud != null) {
            if (ud.getPermissions().contains(normalized)) return true;
            if (ud.getPermissions().contains(normalized + ".*")) return true;
            groupsToCheck.addAll(ud.getGroups());
        }

        if (groupsToCheck.isEmpty()) {
            groupsToCheck.add(YamlStorage.DEFAULT_GROUP_NAME);
        }

        return checkAnyGroupPermission(groupsToCheck, normalized);
    }

    private boolean checkAnyGroupPermission(Set<String> groupNames, String node) {
        Set<String> visited = ConcurrentHashMap.newKeySet();
        for (String g : groupNames) {
            if (checkGroupPermissionRecursive(g, node, visited)) return true;
        }
        return false;
    }

    private boolean checkGroupPermissionRecursive(String groupName, String node, Set<String> visited) {
        String key = groupName.toLowerCase();
        GroupData gd = groups.get(key);
        if (gd == null) return false;
        if (!visited.add(key)) return false;
        if (gd.getPermissions().contains(node)) return true;
        if (gd.getPermissions().contains(node + ".*")) return true;
        for (String parentName : gd.getParents()) {
            if (checkGroupPermissionRecursive(parentName, node, visited)) return true;
        }
        return false;
    }

    public boolean groupHasPermission(String name, String node) {
        if (name == null || node == null) return false;
        Set<String> visited = ConcurrentHashMap.newKeySet();
        return checkGroupPermissionRecursive(name, node.toLowerCase(), visited);
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
        plugin.getLogger().info(tlRaw("console.permission.removed-group", node, name));
        try {
            if (plugin instanceof FoliaPerms) {
                ((FoliaPerms) plugin).refreshAllAttachments();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh attachments after removing group permission: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    //  Group Inheritance Management
    // ──────────────────────────────────────────────

    public boolean addGroupInheritance(String groupName, String parentName) {
        if (groupName == null || parentName == null) return false;
        String childKey = groupName.toLowerCase();
        String parentKey = parentName.toLowerCase();

        if (childKey.equals(parentKey)) {
            plugin.getLogger().warning(tlRaw("console.permission.inheritance-self", groupName));
            return false;
        }

        GroupData child = createGroup(groupName);

        if (wouldCreateCycle(childKey, parentKey)) {
            plugin.getLogger().warning(tlRaw("console.permission.inheritance-cycle", groupName, parentName));
            return false;
        }

        child.addParent(parentName);
        plugin.getLogger().info(tlRaw("console.permission.inheritance-added", groupName, parentName));

        try {
            if (plugin instanceof FoliaPerms) {
                ((FoliaPerms) plugin).refreshAllAttachments();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh attachments after adding inheritance: " + e.getMessage());
        }

        return true;
    }

    public void removeGroupInheritance(String groupName, String parentName) {
        if (groupName == null || parentName == null) return;
        GroupData gd = groups.get(groupName.toLowerCase());
        if (gd != null) {
            gd.removeParent(parentName);
            plugin.getLogger().info(tlRaw("console.permission.inheritance-removed", groupName, parentName));
            try {
                if (plugin instanceof FoliaPerms) {
                    ((FoliaPerms) plugin).refreshAllAttachments();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to refresh attachments after removing inheritance: " + e.getMessage());
            }
        }
    }

    private boolean wouldCreateCycle(String childKey, String parentKey) {
        Set<String> visited = ConcurrentHashMap.newKeySet();
        return canReach(parentKey, childKey, visited);
    }

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

    public boolean deleteGroup(String name) {
        if (name == null) return false;
        String key = name.toLowerCase();

        if (key.equals(YamlStorage.DEFAULT_GROUP_NAME)) {
            plugin.getLogger().warning(tlRaw("console.permission.default-group-protected", YamlStorage.DEFAULT_GROUP_NAME));
            return false;
        }

        GroupData gd = groups.get(key);
        if (gd == null) {
            plugin.getLogger().warning(tlRaw("console.permission.group-not-exist", name));
            return false;
        }

        for (String memberUuidStr : new java.util.HashSet<>(gd.getMembers())) {
            try {
                UUID memberId = UUID.fromString(memberUuidStr);
                UserData ud = users.get(memberId);
                if (ud != null) {
                    ud.removeGroup(key);
                }
                if (ud != null && ud.getGroups().isEmpty()) {
                    ud.addGroup(YamlStorage.DEFAULT_GROUP_NAME);
                    getOrCreateGroup(YamlStorage.DEFAULT_GROUP_NAME).addMember(memberUuidStr);
                    plugin.getLogger().fine("Re-assigned user " + memberId + " to default group after group '" + name + "' was deleted.");
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid member UUID '" + memberUuidStr + "' in group '" + name + "'.");
            }
        }

        groups.remove(key);
        plugin.getLogger().info(tlRaw("console.permission.group-deleted", name));

        try {
            if (plugin instanceof FoliaPerms) {
                ((FoliaPerms) plugin).refreshAllAttachments();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh attachments after deleting group: " + e.getMessage());
        }

        return true;
    }

    public Map<UUID, UserData> getUsers() {
        return users;
    }

    public Map<String, GroupData> getGroups() {
        return groups;
    }
}
