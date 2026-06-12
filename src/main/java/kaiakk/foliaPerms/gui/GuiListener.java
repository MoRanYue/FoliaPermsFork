package kaiakk.foliaPerms.gui;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.internal.ColorConverter;
import kaiakk.foliaPerms.permissions.GroupData;
import kaiakk.foliaPerms.permissions.PermissionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * Handles GUI interactions for the FoliaPerms permission editor.
 * Version: 1.13.0
 */
public class GuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();
        InventoryHolder holder = e.getInventory().getHolder();

        if (holder instanceof MainMenuHolder) {
            e.setCancelled(true);
            handleMainMenu(player, (MainMenuHolder) holder, e.getRawSlot());
        } else if (holder instanceof TargetListHolder) {
            e.setCancelled(true);
            handleTargetList(player, (TargetListHolder) holder,
                    e.getRawSlot(), e.getInventory().getSize());
        } else if (holder instanceof PermEditorHolder) {
            e.setCancelled(true);
            handlePermEditor(player, (PermEditorHolder) holder,
                    e.getRawSlot(), e.getCurrentItem());
        } else if (holder instanceof InheritanceEditorHolder) {
            e.setCancelled(true);
            handleInheritanceEditor(player, (InheritanceEditorHolder) holder,
                    e.getRawSlot(), e.getCurrentItem());
        }
    }

    private void handleMainMenu(Player player, MainMenuHolder holder, int slot) {
        FoliaPerms plugin = holder.getPlugin();
        if (slot == GuiConstants.SLOT_GROUPS) {
            EditorGui.openTargetList(player, plugin, true);
        } else if (slot == GuiConstants.SLOT_PLAYERS) {
            EditorGui.openTargetList(player, plugin, false);
        }
    }

    private void handleTargetList(Player player, TargetListHolder holder, int slot, int invSize) {
        FoliaPerms plugin = holder.getPlugin();

        if (slot == invSize - GuiConstants.LIST_BACK_OFFSET) {
            EditorGui.openMain(player, plugin);
            return;
        }
        if (slot >= invSize - 9) return;

        ItemStack item = e_getItem(player, slot);
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            plugin.getLogger().warning("Item at slot " + slot + " has no display name");
            return;
        }

        String rawName = ColorConverter.stripColor(meta.getDisplayName());

        if (holder.isGroup()) {
            EditorGui.openPermEditor(player, plugin, true, rawName, 0);
        } else {
            // Try to extract UUID from lore first
            UUID targetUuid = extractUuidFromLore(plugin, meta);
            if (targetUuid != null) {
                EditorGui.openPermEditor(player, plugin, false, targetUuid.toString(), 0);
                return;
            }

            // Fallback: look for online player by exact name
            Player target = player.getServer().getPlayerExact(rawName);
            if (target != null) {
                EditorGui.openPermEditor(player, plugin, false,
                        target.getUniqueId().toString(), 0);
            } else {
                plugin.getLogger().warning("Could not resolve player: " + rawName);
            }
        }
    }

    /**
     * Extracts UUID from item lore. Lore is expected to contain "UUID: <uuid>" line.
     * Returns null if UUID cannot be found or parsed.
     */
    private UUID extractUuidFromLore(FoliaPerms plugin, ItemMeta meta) {
        List<String> lore = meta.getLore();
        if (lore == null) return null;

        for (String line : lore) {
            String stripped = ColorConverter.stripColor(line);
            if (stripped.startsWith("UUID: ")) {
                String uuidStr = stripped.substring(6).trim();
                try {
                    return UUID.fromString(uuidStr);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Invalid UUID in lore: " + uuidStr);
                    return null;
                }
            }
        }
        return null;
    }

    private void handlePermEditor(Player player, PermEditorHolder holder,
                                  int slot, ItemStack clicked) {
        FoliaPerms plugin = holder.getPlugin();
        PermissionService service = plugin.getPermissionService();
        if (service == null) {
            plugin.getLogger().warning("PermissionService is null in handlePermEditor");
            return;
        }

        boolean isGroup = holder.isGroup();
        String targetId = holder.getTargetId();
        int page = holder.getPage();

        // Navigation: Previous page
        if (slot == GuiConstants.BUTTON_BACK && page > 0) {
            EditorGui.openPermEditor(player, plugin, isGroup, targetId, page - 1);
            return;
        }

        // Navigation: Center button (disabled)
        if (slot == GuiConstants.BUTTON_CENTER) return;

        // Navigation: Exit to target list
        if (slot == GuiConstants.BUTTON_EXIT) {
            EditorGui.openTargetList(player, plugin, isGroup);
            return;
        }

        // Navigation: Next page
        if (slot == GuiConstants.BUTTON_NEXT) {
            EditorGui.openPermEditor(player, plugin, isGroup, targetId, page + 1);
            return;
        }

        // Inheritance management button (groups only)
        if (slot == GuiConstants.BUTTON_INHERITANCE && isGroup) {
            EditorGui.openInheritanceEditor(player, plugin, targetId, 0);
            return;
        }

        // Ignore clicks on footer area
        if (slot >= GuiConstants.FOOTER_START) return;

        if (clicked == null || clicked.getType().isAir()) {
            plugin.getLogger().fine("Clicked empty slot " + slot);
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            plugin.getLogger().warning("Clicked item at slot " + slot + " has no display name");
            return;
        }

        String permNode = ColorConverter.stripColor(meta.getDisplayName());

        try {
            togglePermission(plugin, service, isGroup, targetId, permNode);
            plugin.getPermissionService().saveAsync();
            EditorGui.openPermEditor(player, plugin, isGroup, targetId, page);
        } catch (Exception e) {
            plugin.getLogger().severe("Error toggling permission: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles clicks within the inheritance editor.
     */
    private void handleInheritanceEditor(Player player, InheritanceEditorHolder holder,
                                          int slot, ItemStack clicked) {
        FoliaPerms plugin = holder.getPlugin();
        PermissionService service = plugin.getPermissionService();
        if (service == null) {
            plugin.getLogger().warning("PermissionService is null in handleInheritanceEditor");
            return;
        }

        String groupName = holder.getGroupName();
        int page = holder.getPage();

        // Navigation: Previous page
        if (slot == GuiConstants.BUTTON_BACK && page > 0) {
            EditorGui.openInheritanceEditor(player, plugin, groupName, page - 1);
            return;
        }

        // Navigation: Center button (disabled)
        if (slot == GuiConstants.BUTTON_CENTER) {
            player.sendMessage(ColorConverter.colorize(
                    "&7[&6FoliaPerms&7] &eClick a group to toggle inheritance."));
            return;
        }

        // Navigation: Exit (back to perm editor)
        if (slot == GuiConstants.BUTTON_EXIT) {
            EditorGui.openPermEditor(player, plugin, true, groupName, 0);
            return;
        }

        // Navigation: Next page
        if (slot == GuiConstants.BUTTON_NEXT) {
            EditorGui.openInheritanceEditor(player, plugin, groupName, page + 1);
            return;
        }

        // Ignore clicks on footer area
        if (slot >= GuiConstants.FOOTER_START) return;

        if (clicked == null || clicked.getType().isAir()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String candidateGroup = ColorConverter.stripColor(meta.getDisplayName());

        try {
            // Toggle inheritance
            GroupData gd = service.getGroup(groupName);
            boolean isCurrentlyParent = gd != null && gd.getParents().contains(candidateGroup);

            if (isCurrentlyParent) {
                service.removeGroupInheritance(groupName, candidateGroup);
                player.sendMessage(ColorConverter.colorize(
                        "&7[&6FoliaPerms&7] &aRemoved inheritance: &e" + groupName + " &7\u00AB &e" + candidateGroup));
            } else {
                boolean success = service.addGroupInheritance(groupName, candidateGroup);
                if (!success) {
                    player.sendMessage(ColorConverter.colorize(
                            "&7[&6FoliaPerms&7] &cCannot add inheritance: would create a circular dependency."));
                } else {
                    player.sendMessage(ColorConverter.colorize(
                            "&7[&6FoliaPerms&7] &aAdded inheritance: &e" + groupName + " &7\u00AB &e" + candidateGroup));
                }
            }

            plugin.getPermissionService().saveAsync();
            EditorGui.openInheritanceEditor(player, plugin, groupName, page);
        } catch (Exception e) {
            plugin.getLogger().severe("Error toggling inheritance: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Toggles a permission for a user or group.
     */
    private void togglePermission(FoliaPerms plugin, PermissionService service,
                                 boolean isGroup, String targetId, String permNode) {
        if (isGroup) {
            if (service.groupHasDirectPermission(targetId, permNode)) {
                service.removeGroupPermission(targetId, permNode);
                plugin.getLogger().info("Removed permission '" + permNode + "' from group '" + targetId + "'");
            } else {
                service.addGroupPermission(targetId, permNode);
                plugin.getLogger().info("Added permission '" + permNode + "' to group '" + targetId + "'");
            }
        } else {
            UUID uuid;
            try {
                uuid = UUID.fromString(targetId);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid UUID: " + targetId, ex);
            }

            if (service.userHasDirectPermission(uuid, permNode)) {
                service.removeUserPermission(uuid, permNode);
                plugin.getLogger().info("Removed permission '" + permNode + "' from user " + uuid);
            } else {
                service.addUserPermission(uuid, permNode);
                plugin.getLogger().info("Added permission '" + permNode + "' to user " + uuid);
            }
        }
    }

    private static ItemStack e_getItem(Player player, int slot) {
        return player.getOpenInventory().getTopInventory().getItem(slot);
    }
}
