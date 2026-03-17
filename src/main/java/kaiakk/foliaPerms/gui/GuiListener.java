package kaiakk.foliaPerms.gui;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.internal.ColorConverter;
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
        }
    }

    // ---------------------------------------------------------------
    // Main menu
    // ---------------------------------------------------------------
    private void handleMainMenu(Player player, MainMenuHolder holder, int slot) {
        FoliaPerms plugin = holder.getPlugin();
        if (slot == 11) EditorGui.openTargetList(player, plugin, true);   // groups
        else if (slot == 15) EditorGui.openTargetList(player, plugin, false); // players
    }

    // ---------------------------------------------------------------
    // Target list (groups or online players)
    // ---------------------------------------------------------------
    private void handleTargetList(Player player, TargetListHolder holder, int slot, int invSize) {
        FoliaPerms plugin = holder.getPlugin();

        // Back button is in the bottom navigation row centre
        if (slot == invSize - 5) {
            EditorGui.openMain(player, plugin);
            return;
        }
        // Ignore other navigation row slots
        if (slot >= invSize - 9) return;

        ItemStack item = e_getItem(player, slot);
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String rawName = ColorConverter.stripColor(meta.getDisplayName());

        if (holder.isGroup()) {
            EditorGui.openPermEditor(player, plugin, true, rawName, 0);
        } else {
            // Retrieve UUID from the stored lore line "UUID: <uuid>"
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    String stripped = ColorConverter.stripColor(line);
                    if (stripped.startsWith("UUID: ")) {
                        String uuidStr = stripped.substring(6).trim();
                        try {
                            UUID.fromString(uuidStr); // validate format
                            EditorGui.openPermEditor(player, plugin, false, uuidStr, 0);
                            return;
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            // Fallback: resolve by display name (online player name)
            Player target = player.getServer().getPlayerExact(rawName);
            if (target != null) {
                EditorGui.openPermEditor(player, plugin, false,
                        target.getUniqueId().toString(), 0);
            }
        }
    }

    // ---------------------------------------------------------------
    // Permission editor
    // ---------------------------------------------------------------
    private void handlePermEditor(Player player, PermEditorHolder holder,
                                  int slot, ItemStack clicked) {
        FoliaPerms plugin = holder.getPlugin();
        PermissionService service = plugin.getPermissionService();
        boolean isGroup = holder.isGroup();
        String targetId = holder.getTargetId();
        int page = holder.getPage();

        // --- Navigation row (slots 45-53) ---
        if (slot == 45 && page > 0) {
            EditorGui.openPermEditor(player, plugin, isGroup, targetId, page - 1);
            return;
        }
        if (slot == 48) return; // page indicator — no action
        if (slot == 49) {
            EditorGui.openTargetList(player, plugin, isGroup);
            return;
        }
        if (slot == 53) {
            EditorGui.openPermEditor(player, plugin, isGroup, targetId, page + 1);
            return;
        }
        if (slot >= 45) return; // other filler panes

        // --- Permission toggle (slots 0-44) ---
        if (clicked == null || clicked.getType().isAir()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String permNode = ColorConverter.stripColor(meta.getDisplayName());

        if (isGroup) {
            if (service.groupHasDirectPermission(targetId, permNode)) {
                service.removeGroupPermission(targetId, permNode);
            } else {
                service.addGroupPermission(targetId, permNode);
            }
        } else {
            UUID uuid;
            try {
                uuid = UUID.fromString(targetId);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("GuiListener: invalid UUID '" + targetId + "'");
                return;
            }
            if (service.userHasDirectPermission(uuid, permNode)) {
                service.removeUserPermission(uuid, permNode);
            } else {
                service.addUserPermission(uuid, permNode);
            }
        }

        plugin.getPermissionService().saveAsync();

        // Refresh the page in-place so the player sees the updated icons immediately
        EditorGui.openPermEditor(player, plugin, isGroup, targetId, page);
    }

    // Helper: read an item from the top inventory at a given raw slot
    private static ItemStack e_getItem(Player player, int slot) {
        return player.getOpenInventory().getTopInventory().getItem(slot);
    }
}
