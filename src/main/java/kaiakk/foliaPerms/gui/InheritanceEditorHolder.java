package kaiakk.foliaPerms.gui;

import kaiakk.foliaPerms.FoliaPerms;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holds state for the group inheritance editor GUI.
 * Displays all available groups and allows toggling inheritance.
 */
public class InheritanceEditorHolder implements InventoryHolder {
    private final FoliaPerms plugin;
    private final String groupName;
    private final int page;

    public InheritanceEditorHolder(FoliaPerms plugin, String groupName, int page) {
        this.plugin = plugin;
        this.groupName = groupName;
        this.page = page;
    }

    public FoliaPerms getPlugin() {
        return plugin;
    }

    public String getGroupName() {
        return groupName;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
