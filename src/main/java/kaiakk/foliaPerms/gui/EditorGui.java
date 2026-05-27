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

/**
 * GUI factory for the FoliaPerms permission editor.
 * Version: 1.13.0
 */
public class EditorGui {

    public static void openMain(Player player, FoliaPerms plugin) {
        Inventory inv = Bukkit.createInventory(new MainMenuHolder(plugin), GuiConstants.MAIN_MENU_SIZE,
                ColorConverter.colorize("&8FoliaPerms Editor"));

        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GuiConstants.MAIN_MENU_SIZE; i++) inv.setItem(i, pane);

        inv.setItem(GuiConstants.SLOT_GROUPS, makeItem(Material.CHEST, "&aEdit Groups",
                "&7Browse and toggle permissions for groups."));
        inv.setItem(GuiConstants.SLOT_PLAYERS, makeItem(Material.PLAYER_HEAD, "&bEdit Players",
                "&7Browse online players and toggle their permissions."));

        player.openInventory(inv);
    }

    public static void openTargetList(Player player, FoliaPerms plugin, boolean isGroup) {
        PermissionService service = plugin.getPermissionService();

        int contentCount = isGroup ? service.getGroups().size() : Bukkit.getOnlinePlayers().size();
        int rows = Math.max(2, Math.min(6, (int) Math.ceil((double) contentCount / 9.0) + 1));
        int size = rows * 9;

        String title = ColorConverter.colorize(isGroup ? "&8Groups" : "&8Online Players");
        Inventory inv = Bukkit.createInventory(new TargetListHolder(plugin, isGroup), size, title);

        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = size - 9; i < size; i++) inv.setItem(i, pane);
        inv.setItem(size - GuiConstants.LIST_BACK_OFFSET, makeItem(Material.BARRIER, "&cBack", "&7Return to main menu."));

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

    public static void openPermEditor(Player player, FoliaPerms plugin,
                                      boolean isGroup, String targetId, int page) {
        PermissionService service = plugin.getPermissionService();
        List<String> allPerms = new ArrayList<>(service.getRegisteredPermissionsSorted());

        int totalPages = Math.max(1, (int) Math.ceil((double) allPerms.size() / GuiConstants.PERMS_PER_PAGE));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * GuiConstants.PERMS_PER_PAGE;
        int end = Math.min(start + GuiConstants.PERMS_PER_PAGE, allPerms.size());

        String displayName = isGroup ? targetId : resolvePlayerName(plugin, targetId);
        String rawTitle = "&8Perms \u00BB " + displayName;
        if (rawTitle.length() > 32) rawTitle = rawTitle.substring(0, 32);
        String title = ColorConverter.colorize(rawTitle);

        Inventory inv = Bukkit.createInventory(
                new PermEditorHolder(plugin, isGroup, targetId, currentPage), GuiConstants.PERMISSIONS_PAGE_SIZE, title);

        for (int i = start; i < end; i++) {
            String perm = allPerms.get(i);
            boolean has = isGroup
                    ? service.groupHasDirectPermission(targetId, perm)
                    : service.userHasDirectPermission(UUID.fromString(targetId), perm);
            Material mat = has ? Material.LIME_DYE : Material.RED_DYE;
            String statusLine = has ? "&aGRANTED  \u2714  click to remove" : "&cNOT GRANTED  \u2718  click to add";
            inv.setItem(i - start, makeItem(mat, "&f" + perm, statusLine));
        }

        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = GuiConstants.FOOTER_START; i < GuiConstants.PERMISSIONS_PAGE_SIZE; i++) {
            inv.setItem(i, pane);
        }

        if (currentPage > 0)
            inv.setItem(GuiConstants.BUTTON_BACK, makeItem(Material.ARROW, "&ePrevious Page",
                    "&7Go to page " + currentPage + "."));

        inv.setItem(GuiConstants.BUTTON_CENTER, makeItem(Material.PAPER,
                "&fPage " + (currentPage + 1) + " &7/ " + totalPages,
                "&7" + allPerms.size() + " total permissions registered."));

        inv.setItem(GuiConstants.BUTTON_EXIT, makeItem(Material.BARRIER, "&cBack",
                "&7Return to target list."));

        if (currentPage < totalPages - 1)
            inv.setItem(GuiConstants.BUTTON_NEXT, makeItem(Material.ARROW, "&eNext Page",
                    "&7Go to page " + (currentPage + 2) + "."));

        player.openInventory(inv);
    }

    private static String resolvePlayerName(FoliaPerms plugin, String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) return online.getName();
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) return name;
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Could not parse UUID: " + uuidStr);
        }
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
            lore.add(ColorConverter.colorize("&8UUID: " + p.getUniqueId()));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }
}
