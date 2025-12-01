package kaiakk.foliaPerms.permissions;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class UserData {
    private final UUID id;
    private final Set<String> permissions = ConcurrentHashMap.newKeySet();
    private final Set<String> groups = ConcurrentHashMap.newKeySet();

    public UserData(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public void addPermission(String node) {
        permissions.add(node.toLowerCase());
    }

    public void removePermission(String node) {
        permissions.remove(node.toLowerCase());
    }

    public void addGroup(String group) {
        groups.add(group.toLowerCase());
    }

    public void removeGroup(String group) {
        groups.remove(group.toLowerCase());
    }
}