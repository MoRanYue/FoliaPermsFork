package kaiakk.foliaPerms.events;

import kaiakk.foliaPerms.FoliaPerms;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

public class PluginEnableListener implements Listener {
    private final FoliaPerms plugin;

    public PluginEnableListener(FoliaPerms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        try {
            String name = event.getPlugin() == null ? "unknown" : event.getPlugin().getName();
            plugin.getLogger().info(plugin.tlRaw("console.plugin-enable.plugin-enabled", name));
            if (plugin.getPermissionService() != null) {
                plugin.getPermissionService().gatherRegisteredPermissions(plugin);
                plugin.refreshAllAttachments();
            }
        } catch (Throwable t) {
            kaiakk.foliaPerms.internal.ErrorHandler.warn(plugin, "Failed while handling PluginEnableEvent!", t);
        }
    }
}
