package kaiakk.foliaPerms.api;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Public API for FoliaPerms. Plugins can obtain an implementation via
 * Bukkit's ServicesManager and call these methods to check or inspect permissions.
 *
 * Implementations MUST be thread-safe for Folia (concurrent reads/writes).
 * Version: 1.13.0
 */
public interface FoliaPermsAPI {

    /**
     * Check whether a player has a permission node.
     *
     * @param player the player (may be null if UUID variant is preferred)
     * @param permissionNode permission node to check
     * @return true if allowed
     */
    boolean hasPermission(Player player, String permissionNode);

    /**
     * Check whether a player (by UUID) has a permission node.
     * Useful when callers don't have a Player object.
     */
    boolean hasPermission(UUID playerUuid, String permissionNode);

    /**
     * Get all groups the player belongs to.
     *
     * @param player the player
     * @return a set of group names (lowercase). May be empty but never null.
     */
    Set<String> getPlayerGroups(Player player);

    /**
     * Get the primary group for the player. This implementation may choose
     * the first group or null if none.
     */
    String getPrimaryGroup(Player player);
}