package kaiakk.foliaPerms.permissions;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GroupData {
    private final String name;
    private final Set<String> permissions = ConcurrentHashMap.newKeySet();
    private final Set<String> members = ConcurrentHashMap.newKeySet();

    public GroupData(String name) {
        this.name = name.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Set<String> getMembers() {
        return members;
    }

    public void addPermission(String node) {
        permissions.add(node.toLowerCase());
    }

    public void removePermission(String node) {
        permissions.remove(node.toLowerCase());
    }

    public void addMember(String uuid) {
        members.add(uuid);
    }

    public void removeMember(String uuid) {
        members.remove(uuid);
    }
}