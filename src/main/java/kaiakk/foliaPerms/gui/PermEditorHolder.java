package kaiakk.foliaPerms.gui;

import kaiakk.foliaPerms.FoliaPerms;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PermEditorHolder implements InventoryHolder {
    private final FoliaPerms plugin;
    private final boolean isGroup;
    private final String targetId;
    private final int page;

    public PermEditorHolder(FoliaPerms plugin, boolean isGroup, String targetId, int page) {
        this.plugin = plugin;
        this.isGroup = isGroup;
        this.targetId = targetId;
        this.page = page;
    }

    public FoliaPerms getPlugin() {
        return plugin;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public String getTargetId() {
        return targetId;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
