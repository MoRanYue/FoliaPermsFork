package kaiakk.foliaPerms.gui;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.internal.ColorConverter;
import kaiakk.foliaPerms.permissions.PermissionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EditorGui {

    // ---------------------------------------------------------------
    // Main menu: "Edit Groups"  |  "Edit Players"
    // ---------------------------------------------------------------
    public static void openMain(Player player, FoliaPerms plugin) {
        Inventory inv = Bukkit.createInventory(new MainMenuHolder(plugin), 27,
                ColorConverter.colorize("&8FoliaPerms Editor"));

        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        inv.setItem(11, makeItem(Material.CHEST, "&aEdit Groups",
                "&7Browse and toggle permissions for groups."));
        inv.setItem(15, makeItem(Material.PLAYER_HEAD, "&bEdit Players",
                "&7Browse online players and toggle their permissions."));

        player.openInventory(inv);
    }

    // ---------------------------------------------------------------
    // Target list: all groups or all online players
    // ---------------------------------------------------------------
    public static void openTargetList(Player player, FoliaPerms plugin, boolean isGroup) {
        PermissionService service = plugin.getPermissionService();

        int contentCount = isGroup ? service.getGroups().size() : Bukkit.getOnlinePlayers().size();
        // rows = enough for content + 1 navigation row, clamped 2-6
        int rows = Math.max(2, Math.min(6, (int) Math.ceil((contentCount) / 9.0) + 1));
        int size = rows * 9;

        String title = ColorConverter.colorize(isGroup ? "&8Groups" : "&8Online Players");
        Inventory inv = Bukkit.createInventory(new TargetListHolder(plugin, isGroup), size, title);

        // Bottom navigation row
        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = size - 9; i < size; i++) inv.setItem(i, pane);
        inv.setItem(size - 5, makeItem(Material.BARRIER, "&cBack", "&7Return to main menu."));

        if (isGroup) {
            int slot = 0;
            for (String name : service.getGroups().keySet()) {
                if (slot >= size - 9) break;
                inv.setItem(slot++, makeItem(Material.CHEST, "&e" + name,
                        "&7Click to edit permissions for this group."));
            }
        } else {
            int slot = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (slot >= size - 9) break;
                inv.setItem(slot++, makePlayerHead(p));
            }
        }

        player.openInventory(inv);
    }

    // ---------------------------------------------------------------
    // Permission editor: paged list of all registered permissions
    // Green dye  = granted   (click to remove)
    // Red dye    = not granted (click to add)
    // ---------------------------------------------------------------
    public static void openPermEditor(Player player, FoliaPerms plugin,
                                      boolean isGroup, String targetId, int page) {
        PermissionService service = plugin.getPermissionService();
        List<String> allPerms = new ArrayList<>(service.getRegisteredPermissions());
        allPerms.sort(String.CASE_INSENSITIVE_ORDER);

        final int perPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) allPerms.size() / perPage));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * perPage;
        int end = Math.min(start + perPage, allPerms.size());

        String displayName = isGroup ? targetId : resolvePlayerName(targetId);
        // Inventory title has a 32-char vanilla limit
        String rawTitle = "&8Perms \u00BB " + displayName;
        if (rawTitle.length() > 32) rawTitle = rawTitle.substring(0, 32);
        String title = ColorConverter.colorize(rawTitle);

        Inventory inv = Bukkit.createInventory(
                new PermEditorHolder(plugin, isGroup, targetId, currentPage), 54, title);

        // Permission toggle items (slots 0-44)
        for (int i = start; i < end; i++) {
            String perm = allPerms.get(i);
            boolean has = isGroup
                    ? service.groupHasDirectPermission(targetId, perm)
                    : service.userHasDirectPermission(UUID.fromString(targetId), perm);
            Material mat = has ? Material.LIME_DYE : Material.RED_DYE;
            String statusLine = has ? "&aGRANTED  \u2714  click to remove" : "&cNOT GRANTED  \u2718  click to add";
            inv.setItem(i - start, makeItem(mat, "&f" + perm, statusLine));
        }

        // Bottom navigation bar (slots 45-53)
        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);

        if (currentPage > 0)
            inv.setItem(45, makeItem(Material.ARROW, "&ePrevious Page",
                    "&7Go to page " + currentPage + "."));

        inv.setItem(48, makeItem(Material.PAPER,
                "&fPage " + (currentPage + 1) + " &7/ " + totalPages,
                "&7" + allPerms.size() + " total permissions registered."));

        inv.setItem(49, makeItem(Material.BARRIER, "&cBack",
                "&7Return to target list."));

        if (currentPage < totalPages - 1)
            inv.setItem(53, makeItem(Material.ARROW, "&eNext Page",
                    "&7Go to page " + (currentPage + 2) + "."));

        player.openInventory(inv);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private static String resolvePlayerName(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) return online.getName();
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) return name;
        } catch (IllegalArgumentException ignored) {}
        return uuidStr;
    }

    static ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorConverter.colorize(name));
            if (lore != null && lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String l : lore) {
                    if (l != null) loreList.add(ColorConverter.colorize(l));
                }
                meta.setLore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack makePlayerHead(Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(p);
            meta.setDisplayName(ColorConverter.colorize("&b" + p.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(ColorConverter.colorize("&7Click to edit permissions."));
            // UUID stored in lore for reliable lookup on click
            lore.add(ColorConverter.colorize("&8UUID: " + p.getUniqueId()));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }
}
