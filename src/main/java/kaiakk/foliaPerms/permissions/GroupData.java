package kaiakk.foliaPerms.permissions;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a permission group with support for hierarchical inheritance.
 * A group can inherit all permissions from one or more parent groups.
 * Inheritance chain is resolved at query time with cycle detection.
 */
public class GroupData {
    private final String name;
    private final Set<String> permissions = ConcurrentHashMap.newKeySet();
    private final Set<String> members = ConcurrentHashMap.newKeySet();
    private final Set<String> parents = ConcurrentHashMap.newKeySet();

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

    /**
     * Returns the set of parent group names that this group inherits from.
     * The returned set is unmodifiable for external access.
     */
    public Set<String> getParents() {
        return Collections.unmodifiableSet(parents);
    }

    /**
     * Returns the mutable set of parent group names (for internal use).
     */
    public Set<String> getParentsMutable() {
        return parents;
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

    /**
     * Adds a parent group to inherit permissions from.
     *
     * @param parentName the name of the parent group (case-insensitive)
     */
    public void addParent(String parentName) {
        if (parentName != null) {
            parents.add(parentName.toLowerCase());
        }
    }

    /**
     * Removes a parent group from the inheritance chain.
     *
     * @param parentName the name of the parent group to remove
     */
    public void removeParent(String parentName) {
        if (parentName != null) {
            parents.remove(parentName.toLowerCase());
        }
    }

    /**
     * Collects all permissions from this group and recursively from all ancestors.
     * Includes cycle detection to prevent infinite loops.
     *
     * @param allGroups a map of all groups (name -> GroupData) for resolving parents
     * @return a new Set containing all inherited permissions
     */
    public Set<String> getAllInheritedPermissions(java.util.Map<String, GroupData> allGroups) {
        Set<String> result = ConcurrentHashMap.newKeySet();
        result.addAll(permissions);
        resolveInheritedPermissions(allGroups, result, ConcurrentHashMap.newKeySet());
        return result;
    }

    /**
     * Recursively resolves permissions from parent groups with cycle detection.
     */
    private void resolveInheritedPermissions(java.util.Map<String, GroupData> allGroups,
                                              Set<String> result,
                                              Set<String> visited) {
        if (!visited.add(name)) return; // Cycle detected, skip

        for (String parentName : parents) {
            GroupData parent = allGroups.get(parentName.toLowerCase());
            if (parent != null) {
                result.addAll(parent.permissions);
                // Recurse into parent's parents
                parent.resolveInheritedPermissions(allGroups, result, visited);
            }
        }
    }
}