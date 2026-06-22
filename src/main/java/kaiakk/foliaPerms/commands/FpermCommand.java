package kaiakk.foliaPerms.commands;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.permissions.PermissionService;
import kaiakk.foliaPerms.internal.ColorConverter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class FpermCommand implements CommandExecutor {
    private final FoliaPerms plugin;
    private final PermissionService service;

    public FpermCommand(FoliaPerms plugin) {
        this.plugin = plugin;
        this.service = plugin.getPermissionService();
    }

    private void send(CommandSender sender, String text) {
        if (text == null) return;
        if (sender instanceof org.bukkit.command.ConsoleCommandSender) {
            sender.sendMessage(ColorConverter.stripColor(text));
        } else {
            sender.sendMessage(text);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) && !sender.hasPermission("folia.perms")) {
            send(sender, plugin.tl("chat.error.no-permission"));
            return true;
        }

        if (args.length == 0) {
            send(sender, plugin.tl("chat.info.default-msg"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (service == null) {
            plugin.getLogger().severe("PermissionService is not initialized; command disabled.");
            send(sender, plugin.tl("chat.error.service-unavailable"));
            return true;
        }

        try {
            switch (sub) {
                case "help":
                    send(sender, plugin.tl("chat.help.header"));
                    send(sender, plugin.tl("chat.help.editor"));
                    send(sender, plugin.tl("chat.help.reload"));
                    send(sender, plugin.tl("chat.help.gather"));
                    send(sender, plugin.tl("chat.help.refresh"));
                    send(sender, plugin.tl("chat.help.user-header"));
                    send(sender, plugin.tl("chat.help.user-addperm"));
                    send(sender, plugin.tl("chat.help.user-removeperm"));
                    send(sender, plugin.tl("chat.help.user-addgroup"));
                    send(sender, plugin.tl("chat.help.user-removegroup"));
                    send(sender, plugin.tl("chat.help.group-header"));
                    send(sender, plugin.tl("chat.help.group-create"));
                    send(sender, plugin.tl("chat.help.group-delete"));
                    send(sender, plugin.tl("chat.help.group-addperm"));
                    send(sender, plugin.tl("chat.help.group-adduser"));
                    send(sender, plugin.tl("chat.help.group-removeuser"));
                    send(sender, plugin.tl("chat.help.inheritance-header"));
                    send(sender, plugin.tl("chat.help.inheritance-set"));
                    send(sender, plugin.tl("chat.help.inheritance-remove"));
                    send(sender, plugin.tl("chat.help.inheritance-show"));
                    send(sender, plugin.tl("chat.help.inheritance-perms"));
                    send(sender, plugin.tl("chat.help.other-header"));
                    send(sender, plugin.tl("chat.help.check"));
                    send(sender, plugin.tl("chat.help.listperms"));
                    break;
                case "editor":
                    if (!(sender instanceof org.bukkit.entity.Player)) {
                        send(sender, plugin.tl("chat.error.editor-only-player"));
                        break;
                    }
                    kaiakk.foliaPerms.gui.EditorGui.openMain((org.bukkit.entity.Player) sender, plugin);
                    break;
                case "gather":
                    try {
                        service.gatherRegisteredPermissions(plugin);
                        plugin.refreshAllAttachments();
                        int count = service.getRegisteredPermissions().size();
                        send(sender, plugin.tl("chat.success.gathered", count));
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to run /fperm gather: " + e.getMessage());
                        send(sender, plugin.tl("chat.error.gather-failed", e.getMessage()));
                    }
                    break;
                case "reload":
                    // Reload config and locale
                    plugin.reloadConfig();
                    String language = plugin.getConfig().getString("language", "en_us");
                    plugin.getLocaleManager().load(language);
                    // Reload permissions
                    service.load();
                    send(sender, plugin.tl("chat.success.reloaded"));
                    break;
                case "refresh":
                    try {
                        plugin.refreshAllAttachments();
                        send(sender, plugin.tl("chat.success.refreshed"));
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to refresh attachments: " + e.getMessage());
                        send(sender, plugin.tl("chat.error.refresh-failed", e.getMessage()));
                    }
                    break;
                case "user":
                    if (args.length < 4) {
                        send(sender, plugin.tl("chat.info.usage", "/fperm user addperm|removeperm|addgroup|removegroup <player> <perm|group>"));
                        break;
                    }
                    String action = args[1].toLowerCase();
                    String playerName = args[2];
                    String permArg = args[3];
                    try {
                        var op = Bukkit.getOfflinePlayer(playerName);
                        if (op == null) {
                            send(sender, plugin.tl("chat.error.player-not-found", playerName));
                            break;
                        }
                        var id = op.getUniqueId();
                        if (id == null) {
                            var online = Bukkit.getPlayerExact(playerName);
                            if (online != null) id = online.getUniqueId();
                        }
                        if (id == null) {
                            send(sender, plugin.tl("chat.error.uuid-not-found", playerName));
                            break;
                        }

                        if (action.equals("addperm")) {
                            service.addUserPermission(id, permArg);
                            plugin.getPermissionService().saveAsync();
                            send(sender, plugin.tl("chat.success.permission-added", permArg, playerName));
                        } else if (action.equals("removeperm")) {
                            service.removeUserPermission(id, permArg);
                            plugin.getPermissionService().saveAsync();
                            send(sender, plugin.tl("chat.success.permission-removed", permArg, playerName));
                        } else if (action.equals("addgroup")) {
                            service.addUserToGroup(id, permArg);
                            plugin.getPermissionService().saveAsync();
                            send(sender, plugin.tl("chat.success.user-added-group", playerName, permArg));
                        } else if (action.equals("removegroup")) {
                            service.removeUserFromGroup(id, permArg);
                            plugin.getPermissionService().saveAsync();
                            send(sender, plugin.tl("chat.success.user-removed-group", playerName, permArg));
                        } else {
                            send(sender, plugin.tl("chat.error.unknown-user-action", action));
                        }
                    } catch (Exception e) {
                        kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Exception handling /fperm user", e);
                        send(sender, plugin.tl("chat.error.internal-error-user"));
                    }
                    break;
                case "group":
                    if (args.length < 2) {
                        send(sender, plugin.tl("chat.info.usage", "/fperm group create|delete|addperm|adduser|removeuser|setinherit|removeinherit|inheritance|perms <args>"));
                        break;
                    }
                    try {
                        String gaction = args[1].toLowerCase();
                        if (gaction.equals("create")) {
                            if (args.length < 3) { send(sender, plugin.tl("chat.info.usage", "/fperm group create <name>")); break; }
                            service.createGroup(args[2]);
                            plugin.getPermissionService().saveAsync();
                            send(sender, plugin.tl("chat.success.group-created", args[2]));
                        } else if (gaction.equals("addperm")) {
                            if (args.length < 4) { send(sender, plugin.tl("chat.info.usage", "/fperm group addperm <name> <perm>")); break; }
                            service.addGroupPermission(args[2], args[3]);
                            plugin.getPermissionService().saveAsync();
                            send(sender, plugin.tl("chat.success.permission-added-group", args[3], args[2]));
                        } else if (gaction.equals("adduser")) {
                            if (args.length < 4) { send(sender, plugin.tl("chat.info.usage", "/fperm group adduser <name> <player>")); break; }
                            String gname = args[2];
                            var target = Bukkit.getOfflinePlayer(args[3]);
                            if (target == null || target.getUniqueId() == null) {
                                send(sender, plugin.tl("chat.error.player-not-found", args[3]));
                                break;
                            }
                            service.addUserToGroup(target.getUniqueId(), gname);
                            plugin.getPermissionService().saveAsync();
                            send(sender, plugin.tl("chat.success.user-added-group", args[3], gname));
                        } else if (gaction.equals("delete")) {
                            if (args.length < 3) { send(sender, plugin.tl("chat.info.usage", "/fperm group delete <name>")); break; }
                            String gdel = args[2];
                            if (gdel.equalsIgnoreCase("default")) {
                                send(sender, plugin.tl("chat.error.default-group-delete"));
                                break;
                            }
                            boolean deleted = service.deleteGroup(gdel);
                            if (deleted) {
                                plugin.getPermissionService().saveAsync();
                                send(sender, plugin.tl("chat.success.group-deleted", gdel));
                            } else {
                                send(sender, plugin.tl("chat.error.group-delete-failed", gdel));
                            }
                        } else if (gaction.equals("removeuser")) {
                            if (args.length < 4) { send(sender, plugin.tl("chat.info.usage", "/fperm group removeuser <name> <player>")); break; }
                            String gname2 = args[2];
                            var target2 = Bukkit.getOfflinePlayer(args[3]);
                            if (target2 == null || target2.getUniqueId() == null) {
                                send(sender, plugin.tl("chat.error.player-not-found", args[3]));
                                break;
                            }
                            service.removeUserFromGroup(target2.getUniqueId(), gname2);
                            plugin.getPermissionService().saveAsync();
                            send(sender, plugin.tl("chat.success.user-removed-group", args[3], gname2));
                        } else if (gaction.equals("setinherit")) {
                            if (args.length < 4) { send(sender, plugin.tl("chat.info.usage", "/fperm group setinherit <group> <parent>")); break; }
                            boolean success = service.addGroupInheritance(args[2], args[3]);
                            if (success) {
                                plugin.getPermissionService().saveAsync();
                                send(sender, plugin.tl("chat.success.inheritance-set", args[2], args[3]));
                            } else {
                                send(sender, plugin.tl("chat.error.inheritance-failed"));
                            }
                        } else if (gaction.equals("removeinherit")) {
                            if (args.length < 4) { send(sender, plugin.tl("chat.info.usage", "/fperm group removeinherit <group> <parent>")); break; }
                            service.removeGroupInheritance(args[2], args[3]);
                            plugin.getPermissionService().saveAsync();
                            send(sender, plugin.tl("chat.success.inheritance-removed", args[2], args[3]));
                        } else if (gaction.equals("inheritance")) {
                            if (args.length < 3) { send(sender, plugin.tl("chat.info.usage", "/fperm group inheritance <group>")); break; }
                            var chain = service.getGroupInheritanceChain(args[2]);
                            send(sender, plugin.tl("chat.info.inheritance-header", args[2]));
                            if (chain.isEmpty()) {
                                send(sender, plugin.tl("chat.info.inheritance-none"));
                            } else {
                                for (String g : chain) {
                                    send(sender, plugin.tl("chat.info.inheritance-entry", g));
                                }
                            }
                            // Also show parents directly
                            var gd = service.getGroup(args[2]);
                            if (gd != null && !gd.getParents().isEmpty()) {
                                send(sender, plugin.tl("chat.info.inheritance-direct", String.join(", ", gd.getParents())));
                            }
                        } else if (gaction.equals("perms")) {
                            if (args.length < 3) { send(sender, plugin.tl("chat.info.usage", "/fperm group perms <group>")); break; }
                            var gd = service.getGroup(args[2]);
                            if (gd == null) {
                                send(sender, plugin.tl("chat.error.group-not-found", args[2]));
                                break;
                            }
                            send(sender, plugin.tl("chat.info.perms-header", args[2]));
                            // Direct permissions
                            if (gd.getPermissions().isEmpty()) {
                                send(sender, plugin.tl("chat.info.perms-direct-none"));
                            } else {
                                send(sender, plugin.tl("chat.info.perms-direct-header"));
                                for (String p : gd.getPermissions()) {
                                    send(sender, plugin.tl("chat.info.perms-entry", p));
                                }
                            }
                            // Inherited permissions
                            var inherited = gd.getAllInheritedPermissions(service.getGroups());
                            inherited.removeAll(gd.getPermissions());
                            if (!inherited.isEmpty()) {
                                send(sender, plugin.tl("chat.info.perms-inherited-header"));
                                for (String p : inherited) {
                                    send(sender, plugin.tl("chat.info.perms-entry", p));
                                }
                            }
                        } else {
                            send(sender, plugin.tl("chat.error.unknown-group-action", gaction));
                        }
                    } catch (Exception e) {
                        kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Exception handling /fperm group", e);
                        send(sender, plugin.tl("chat.error.internal-error-group"));
                    }
                    break;
                case "check":
                    if (args.length < 3) { send(sender, plugin.tl("chat.info.usage", "/fperm check <player> <perm>")); break; }
                    OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                    boolean ok = service.hasPermission(t.getUniqueId(), args[2]);
                    send(sender, plugin.tl(ok ? "chat.info.check-has" : "chat.info.check-not", args[1], args[2]));
                    break;
                case "listperms":
                    if (args.length < 2) { send(sender, plugin.tl("chat.info.usage", "/fperm listperms <player>")); break; }
                    try {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                        if (target == null || target.getUniqueId() == null) {
                            send(sender, plugin.tl("chat.error.player-not-found", args[1]));
                            break;
                        }
                        var perms = service.getAllowedPermissions(target.getUniqueId());
                        if (perms.isEmpty()) {
                            send(sender, plugin.tl("chat.info.listperms-none", args[1]));
                        } else {
                            send(sender, plugin.tl("chat.info.listperms-header", args[1]));
                            for (String p : perms) send(sender, plugin.tl("chat.info.listperms-entry", p));
                        }
                    } catch (Exception e) {
                        kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Exception during listperms", e);
                        send(sender, plugin.tl("chat.error.internal-error-list"));
                    }
                    break;
                default:
                    send(sender, plugin.tl("chat.error.unknown-subcommand"));
            }
        } catch (Exception ex) {
            kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Unhandled exception while executing /fperm", ex);
            send(sender, plugin.tl("chat.error.internal-error"));
        }

        return true;
    }
}
