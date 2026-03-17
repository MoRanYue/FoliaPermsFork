package kaiakk.foliaPerms.gui;

import kaiakk.foliaPerms.FoliaPerms;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TargetListHolder implements InventoryHolder {
    private final FoliaPerms plugin;
    private final boolean isGroup;

    public TargetListHolder(FoliaPerms plugin, boolean isGroup) {
        this.plugin = plugin;
        this.isGroup = isGroup;
    }

    public FoliaPerms getPlugin() {
        return plugin;
    }

    public boolean isGroup() {
        return isGroup;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
