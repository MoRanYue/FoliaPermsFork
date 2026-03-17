package kaiakk.foliaPerms.gui;

import kaiakk.foliaPerms.FoliaPerms;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MainMenuHolder implements InventoryHolder {
    private final FoliaPerms plugin;

    public MainMenuHolder(FoliaPerms plugin) {
        this.plugin = plugin;
    }

    public FoliaPerms getPlugin() {
        return plugin;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
