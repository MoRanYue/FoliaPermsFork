package kaiakk.foliaPerms.commands;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.permissions.GroupData;
import kaiakk.foliaPerms.permissions.PermissionService;
import kaiakk.foliaPerms.permissions.UserData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class FpermCommand implements CommandExecutor {
    private final FoliaPerms plugin;
    private final PermissionService service;

    public FpermCommand(FoliaPerms plugin) {
        this.plugin = plugin;
        this.service = plugin.getPermissionService();
    }

    private void send(CommandSender sender, Component comp) {
        sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(comp));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) && !sender.hasPermission("folia.perms")) {
            send(sender, Component.text("You don't have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            send(sender, Component.text("FoliaPerms: simple permission manager. /fperm help"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (service == null) {
            plugin.getLogger().severe("PermissionService is not initialized; command disabled.");
            send(sender, Component.text("Internal error: permission service unavailable."));
            return true;
        }

        try {
            switch (sub) {
                case "help":
                    send(sender, Component.text("Usage: /fperm reload | gather | user addperm <player> <perm> | user removeperm <player> <perm> | group create <name> | group addperm <name> <perm> | group adduser <name> <player> | check <player> <perm>"));
                    break;
                case "gather":
                    try {
                        service.gatherRegisteredPermissions(plugin);
                        plugin.refreshAllAttachments();
                        int count = service.getRegisteredPermissions().size();
                        send(sender, Component.text("Gathered " + count + " permissions from plugins."));
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to run /fperm gather: " + e.getMessage());
                        send(sender, Component.text("Failed to gather permissions: " + e.getMessage()));
                    }
                    break;
                case "reload":
                    service.load();
                    send(sender, Component.text("Permissions reloaded."));
                    break;
                case "refresh":
                    try {
                        plugin.refreshAllAttachments();
                        send(sender, Component.text("Refreshed permission attachments."));
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to refresh attachments: " + e.getMessage());
                        send(sender, Component.text("Failed to refresh attachments: " + e.getMessage()));
                    }
                    break;
                case "user":
                    if (args.length < 4) {
                        send(sender, Component.text("Usage: /fperm user addperm|removeperm <player> <perm>"));
                        break;
                    }
                    String action = args[1].toLowerCase();
                    String playerName = args[2];
                    String perm = args[3];
                    try {
                        var op = Bukkit.getOfflinePlayer(playerName);
                        if (op == null) {
                            send(sender, Component.text("Could not resolve player: " + playerName));
                            break;
                        }
                        var id = op.getUniqueId();
                        if (id == null) {
                            var online = Bukkit.getPlayerExact(playerName);
                            if (online != null) id = online.getUniqueId();
                        }
                        if (id == null) {
                            send(sender, Component.text("Could not determine UUID for player: " + playerName));
                            break;
                        }

                        if (action.equals("addperm")) {
                            service.addUserPermission(id, perm);
                            plugin.getPermissionService().saveAsync();
                            var onlineTarget = Bukkit.getPlayerExact(playerName);
                            if (onlineTarget != null) {
                                plugin.refreshPlayerAttachment(onlineTarget);
                                try {
                                    onlineTarget.recalculatePermissions();
                                } catch (Throwable ignored) {}
                                try {
                                    onlineTarget.updateCommands();
                                } catch (Throwable ignored) {}
                            }
                            send(sender, Component.text("Added permission " + perm + " to " + playerName));
                        } else if (action.equals("removeperm")) {
                            service.removeUserPermission(id, perm);
                            plugin.getPermissionService().saveAsync();
                            var onlineTarget2 = Bukkit.getPlayerExact(playerName);
                            if (onlineTarget2 != null) plugin.refreshPlayerAttachment(onlineTarget2);
                            send(sender, Component.text("Removed permission " + perm + " from " + playerName));
                        } else {
                            send(sender, Component.text("Unknown user action: " + action));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Exception handling /fperm user: ");
                        plugin.getLogger().severe(e.toString());
                        for (StackTraceElement st : e.getStackTrace()) plugin.getLogger().severe("  at " + st.toString());
                        send(sender, Component.text("Internal error while processing user command."));
                    }
                    break;
                case "group":
                    if (args.length < 2) {
                        send(sender, Component.text("Usage: /fperm group create|addperm|adduser <args>"));
                        break;
                    }
                    try {
                        String gaction = args[1].toLowerCase();
                        if (gaction.equals("create")) {
                            if (args.length < 3) { send(sender, Component.text("Usage: /fperm group create <name>")); break; }
                            service.createGroup(args[2]);
                            plugin.getPermissionService().saveAsync();
                            plugin.refreshAllAttachments();
                            send(sender, Component.text("Group created: " + args[2]));
                        } else if (gaction.equals("addperm")) {
                            if (args.length < 4) { send(sender, Component.text("Usage: /fperm group addperm <name> <perm>")); break; }
                            service.addGroupPermission(args[2], args[3]);
                            plugin.getPermissionService().saveAsync();
                            plugin.refreshAllAttachments();
                            send(sender, Component.text("Added permission " + args[3] + " to group " + args[2]));
                        } else if (gaction.equals("adduser")) {
                            if (args.length < 4) { send(sender, Component.text("Usage: /fperm group adduser <name> <player>")); break; }
                            String gname = args[2];
                            var target = Bukkit.getOfflinePlayer(args[3]);
                            if (target == null || target.getUniqueId() == null) {
                                send(sender, Component.text("Could not resolve player: " + args[3]));
                                break;
                            }
                            service.addUserToGroup(target.getUniqueId(), gname);
                            plugin.getPermissionService().saveAsync();
                            var ot = Bukkit.getPlayerExact(args[3]);
                            if (ot != null) plugin.refreshPlayerAttachment(ot);
                            send(sender, Component.text("Added " + args[3] + " to group " + gname));
                        } else {
                            send(sender, Component.text("Unknown group action: " + gaction));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Exception handling /fperm group: ");
                        plugin.getLogger().severe(e.toString());
                        for (StackTraceElement st : e.getStackTrace()) plugin.getLogger().severe("  at " + st.toString());
                        send(sender, Component.text("Internal error while processing group command."));
                    }
                    break;
                case "check":
                    if (args.length < 3) { send(sender, Component.text("Usage: /fperm check <player> <perm>")); break; }
                    OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                    boolean ok = service.hasPermission(t.getUniqueId(), args[2]);
                    send(sender, Component.text(args[1] + (ok ? " HAS " : " DOES NOT HAVE ") + args[2]));
                    break;
                case "listperms":
                    if (args.length < 2) { send(sender, Component.text("Usage: /fperm listperms <player>")); break; }
                    try {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                        if (target == null || target.getUniqueId() == null) {
                            send(sender, Component.text("Could not resolve player: " + args[1]));
                            break;
                        }
                        var perms = service.getAllowedPermissions(target.getUniqueId());
                        if (perms.isEmpty()) {
                            send(sender, Component.text(args[1] + " has no registered permissions (or none gathered)."));
                        } else {
                            send(sender, Component.text("Permissions for " + args[1] + ":"));
                            for (String p : perms) send(sender, Component.text(" - " + p));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Exception during listperms: " + e.toString());
                        send(sender, Component.text("Internal error while listing permissions."));
                    }
                    break;
                default:
                    send(sender, Component.text("Unknown subcommand. Use /fperm help"));
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Unhandled exception while executing /fperm: " + ex.toString());
            for (StackTraceElement st : ex.getStackTrace()) plugin.getLogger().severe("  at " + st.toString());
            send(sender, Component.text("Internal error while executing command."));
        }

        return true;
    }
}