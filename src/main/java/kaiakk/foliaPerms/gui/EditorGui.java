package kaiakk.foliaPerms.gui;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.internal.ColorConverter;
import kaiakk.foliaPerms.permissions.GroupData;
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
import java.util.stream.Collectors;

/**
 * GUI factory for the FoliaPerms permission editor.
 * Version: 0.1.0+26.1.2
 */
public class EditorGui {

    public static void openMain(Player player, FoliaPerms plugin) {
        Inventory inv = Bukkit.createInventory(new MainMenuHolder(plugin), GuiConstants.MAIN_MENU_SIZE,
                plugin.tl("gui.main.title"));

        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GuiConstants.MAIN_MENU_SIZE; i++) inv.setItem(i, pane);

        inv.setItem(GuiConstants.SLOT_GROUPS, makeItem(Material.CHEST,
                plugin.tl("gui.main.groups.name"),
                plugin.tl("gui.main.groups.lore")));
        inv.setItem(GuiConstants.SLOT_PLAYERS, makeItem(Material.PLAYER_HEAD,
                plugin.tl("gui.main.players.name"),
                plugin.tl("gui.main.players.lore")));

        player.openInventory(inv);
    }

    public static void openTargetList(Player player, FoliaPerms plugin, boolean isGroup) {
        PermissionService service = plugin.getPermissionService();

        int contentCount = isGroup ? service.getGroups().size() : Bukkit.getOnlinePlayers().size();
        int rows = Math.max(2, Math.min(6, (int) Math.ceil((double) contentCount / 9.0) + 1));
        int size = rows * 9;

        String title = isGroup
                ? plugin.tl("gui.target-list.groups-title")
                : plugin.tl("gui.target-list.players-title");
        Inventory inv = Bukkit.createInventory(new TargetListHolder(plugin, isGroup), size, title);

        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = size - 9; i < size; i++) inv.setItem(i, pane);
        inv.setItem(size - GuiConstants.LIST_BACK_OFFSET, makeItem(Material.BARRIER,
                plugin.tl("gui.target-list.back.name"),
                plugin.tl("gui.target-list.back.lore")));

        if (isGroup) {
            int slot = 0;
            for (String name : service.getGroups().keySet()) {
                if (slot >= size - 9) break;
                GroupData gd = service.getGroup(name);
                String parentInfo = (gd != null && !gd.getParents().isEmpty())
                        ? plugin.tl("gui.target-list.group.lore-inherits", String.join(", ", gd.getParents()))
                        : plugin.tl("gui.target-list.group.lore-no-inheritance");
                inv.setItem(slot++, makeItem(Material.CHEST, "&e" + name,
                        plugin.tl("gui.target-list.group.lore-edit"),
                        parentInfo));
            }
        } else {
            int slot = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (slot >= size - 9) break;
                inv.setItem(slot++, makePlayerHead(p, plugin));
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
        String rawTitle = plugin.tl("gui.permission-editor.title", displayName);
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
            String statusLine = has
                    ? plugin.tl("gui.permission-editor.granted")
                    : plugin.tl("gui.permission-editor.not-granted");
            inv.setItem(i - start, makeItem(mat, "&f" + perm, statusLine));
        }

        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = GuiConstants.FOOTER_START; i < GuiConstants.PERMISSIONS_PAGE_SIZE; i++) {
            inv.setItem(i, pane);
        }

        if (currentPage > 0)
            inv.setItem(GuiConstants.BUTTON_BACK, makeItem(Material.ARROW,
                    plugin.tl("gui.permission-editor.previous.name"),
                    plugin.tl("gui.permission-editor.previous.lore", currentPage)));

        inv.setItem(GuiConstants.BUTTON_CENTER, makeItem(Material.PAPER,
                plugin.tl("gui.permission-editor.page.name", currentPage + 1, totalPages),
                plugin.tl("gui.permission-editor.page.lore", allPerms.size())));

        inv.setItem(GuiConstants.BUTTON_EXIT, makeItem(Material.BARRIER,
                plugin.tl("gui.permission-editor.back.name"),
                plugin.tl("gui.permission-editor.back.lore")));

        // Inheritance management button (only for groups)
        if (isGroup) {
            GroupData gd = service.getGroup(targetId);
            String inheritsFrom = (gd != null && !gd.getParents().isEmpty())
                    ? plugin.tl("gui.permission-editor.inheritance.lore-current", String.join(", ", gd.getParents()))
                    : plugin.tl("gui.permission-editor.inheritance.lore-none");
            inv.setItem(GuiConstants.BUTTON_INHERITANCE, makeItem(Material.IRON_BARS,
                    plugin.tl("gui.permission-editor.inheritance.name"),
                    inheritsFrom,
                    plugin.tl("gui.permission-editor.inheritance.lore-click")));
        }

        if (currentPage < totalPages - 1)
            inv.setItem(GuiConstants.BUTTON_NEXT, makeItem(Material.ARROW,
                    plugin.tl("gui.permission-editor.next.name"),
                    plugin.tl("gui.permission-editor.next.lore", currentPage + 2)));

        player.openInventory(inv);
    }

    /**
     * Opens the inheritance editor for a specific group.
     */
    public static void openInheritanceEditor(Player player, FoliaPerms plugin,
                                              String groupName, int page) {
        PermissionService service = plugin.getPermissionService();
        List<String> allGroups = new ArrayList<>(service.getGroups().keySet());
        // Remove self from the list
        allGroups.remove(groupName.toLowerCase());

        int totalPages = Math.max(1, (int) Math.ceil((double) allGroups.size() / GuiConstants.INHERITANCE_PER_PAGE));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * GuiConstants.INHERITANCE_PER_PAGE;
        int end = Math.min(start + GuiConstants.INHERITANCE_PER_PAGE, allGroups.size());

        String rawTitle = plugin.tl("gui.inheritance-editor.title", groupName);
        if (rawTitle.length() > 32) rawTitle = rawTitle.substring(0, 32);
        String title = ColorConverter.colorize(rawTitle);
        Inventory inv = Bukkit.createInventory(
                new InheritanceEditorHolder(plugin, groupName, currentPage),
                GuiConstants.INHERITANCE_PAGE_SIZE, title);

        GroupData gd = service.getGroup(groupName);
        List<String> currentParents = (gd != null)
                ? new ArrayList<>(gd.getParents())
                : new ArrayList<>();

        for (int i = start; i < end; i++) {
            String candidateGroup = allGroups.get(i);
            boolean isParent = currentParents.contains(candidateGroup);

            Material mat = isParent ? Material.LIME_DYE : Material.RED_DYE;
            String statusLine = isParent
                    ? plugin.tl("gui.inheritance-editor.parent")
                    : plugin.tl("gui.inheritance-editor.not-parent");
            inv.setItem(i - start, makeItem(mat, "&f" + candidateGroup, statusLine));
        }

        // Footer
        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = GuiConstants.FOOTER_START; i < GuiConstants.INHERITANCE_PAGE_SIZE; i++) {
            inv.setItem(i, pane);
        }

        if (currentPage > 0)
            inv.setItem(GuiConstants.BUTTON_BACK, makeItem(Material.ARROW,
                    plugin.tl("gui.inheritance-editor.previous.name"),
                    plugin.tl("gui.inheritance-editor.previous.lore", currentPage)));

        inv.setItem(GuiConstants.BUTTON_CENTER, makeItem(Material.PAPER,
                plugin.tl("gui.inheritance-editor.page.name", currentPage + 1, totalPages),
                plugin.tl("gui.inheritance-editor.page.lore-1"),
                plugin.tl("gui.inheritance-editor.page.lore-2", currentParents.isEmpty() ? "none" : String.join(", ", currentParents))));

        inv.setItem(GuiConstants.BUTTON_EXIT, makeItem(Material.BARRIER,
                plugin.tl("gui.inheritance-editor.back.name"),
                plugin.tl("gui.inheritance-editor.back.lore")));

        if (currentPage < totalPages - 1)
            inv.setItem(GuiConstants.BUTTON_NEXT, makeItem(Material.ARROW,
                    plugin.tl("gui.inheritance-editor.next.name"),
                    plugin.tl("gui.inheritance-editor.next.lore", currentPage + 2)));

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

    private static ItemStack makePlayerHead(Player p, FoliaPerms plugin) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(p);
            meta.setDisplayName(ColorConverter.colorize("&b" + p.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(ColorConverter.colorize(plugin.tl("gui.target-list.player.lore-edit")));
            lore.add(ColorConverter.colorize(plugin.tl("gui.target-list.player.lore-uuid", p.getUniqueId())));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }
}
