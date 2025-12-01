package kaiakk.foliaPerms.events;

import kaiakk.foliaPerms.FoliaPerms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {
    private final FoliaPerms plugin;

    public PlayerListener(FoliaPerms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Component welcome = Component.text("FoliaPerms active!");
        event.getPlayer().sendMessage(LegacyComponentSerializer.legacySection().serialize(welcome));
        try {
            plugin.refreshPlayerAttachment(event.getPlayer());
        } catch (Exception ignored) {}
    }
}