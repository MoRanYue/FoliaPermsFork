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
            send(sender, ColorConverter.colorize("&cYou don't have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            send(sender, ColorConverter.colorize("&eFoliaPerms: simple permission manager. /fperm help"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (service == null) {
            plugin.getLogger().severe("PermissionService is not initialized; command disabled.");
            send(sender, ColorConverter.colorize("&cInternal error: permission service unavailable."));
            return true;
        }

        try {
            switch (sub) {
                case "help":
                    send(sender, ColorConverter.colorize("&e===== FoliaPerms Commands ====="));
                    send(sender, ColorConverter.colorize("&e/fperm editor &7- Open the permission editor GUI"));
                    send(sender, ColorConverter.colorize("&e/fperm reload &7- Reload permissions from file"));
                    send(sender, ColorConverter.colorize("&e/fperm gather &7- Gather permissions from all plugins"));
                    send(sender, ColorConverter.colorize("&e/fperm refresh &7- Refresh all permission attachments"));
                    send(sender, ColorConverter.colorize("&e--- User Commands ---"));
                    send(sender, ColorConverter.colorize("&e/fperm user addperm <player> <perm>"));
                    send(sender, ColorConverter.colorize("&e/fperm user removeperm <player> <perm>"));
                    send(sender, ColorConverter.colorize("&e/fperm user addgroup <player> <group>"));
                    send(sender, ColorConverter.colorize("&e/fperm user removegroup <player> <group>"));
                    send(sender, ColorConverter.colorize("&e--- Group Commands ---"));
                    send(sender, ColorConverter.colorize("&e/fperm group create <name>"));
                    send(sender, ColorConverter.colorize("&e/fperm group addperm <name> <perm>"));
                    send(sender, ColorConverter.colorize("&e/fperm group adduser <name> <player>"));
                    send(sender, ColorConverter.colorize("&e/fperm group removeuser <name> <player>"));
                    send(sender, ColorConverter.colorize("&e--- Inheritance Commands ---"));
                    send(sender, ColorConverter.colorize("&e/fperm group setinherit <group> <parent> &7- Make group inherit from parent"));
                    send(sender, ColorConverter.colorize("&e/fperm group removeinherit <group> <parent> &7- Remove inheritance"));
                    send(sender, ColorConverter.colorize("&e/fperm group inheritance <group> &7- Show inheritance chain"));
                    send(sender, ColorConverter.colorize("&e/fperm group perms <group> &7- Show all effective permissions (incl. inherited)"));
                    send(sender, ColorConverter.colorize("&e--- Other Commands ---"));
                    send(sender, ColorConverter.colorize("&e/fperm check <player> <perm>"));
                    send(sender, ColorConverter.colorize("&e/fperm listperms <player>"));
                    break;
                case "editor":
                    if (!(sender instanceof org.bukkit.entity.Player)) {
                        send(sender, ColorConverter.colorize("&cThe editor can only be opened by a player in-game."));
                        break;
                    }
                    kaiakk.foliaPerms.gui.EditorGui.openMain((org.bukkit.entity.Player) sender, plugin);
                    break;
                case "gather":
                    try {
                        service.gatherRegisteredPermissions(plugin);
                        plugin.refreshAllAttachments();
                        int count = service.getRegisteredPermissions().size();
                        send(sender, ColorConverter.colorize("&aGathered " + count + " permissions from plugins."));
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to run /fperm gather: " + e.getMessage());
                        send(sender, ColorConverter.colorize("&cFailed to gather permissions: " + e.getMessage()));
                    }
                    break;
                case "reload":
                    service.load();
                    send(sender, ColorConverter.colorize("&aPermissions reloaded."));
                    break;
                case "refresh":
                    try {
                        plugin.refreshAllAttachments();
                        send(sender, ColorConverter.colorize("&aRefreshed permission attachments."));
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to refresh attachments: " + e.getMessage());
                        send(sender, ColorConverter.colorize("&cFailed to refresh attachments: " + e.getMessage()));
                    }
                    break;
                case "user":
                    if (args.length < 4) {
                        send(sender, ColorConverter.colorize("&eUsage: /fperm user addperm|removeperm|addgroup|removegroup <player> <perm|group>"));
                        break;
                    }
                    String action = args[1].toLowerCase();
                    String playerName = args[2];
                    String perm = args[3];
                    try {
                        var op = Bukkit.getOfflinePlayer(playerName);
                        if (op == null) {
                            send(sender, ColorConverter.colorize("&cCould not resolve player: " + playerName));
                            break;
                        }
                        var id = op.getUniqueId();
                        if (id == null) {
                            var online = Bukkit.getPlayerExact(playerName);
                            if (online != null) id = online.getUniqueId();
                        }
                        if (id == null) {
                            send(sender, ColorConverter.colorize("&cCould not determine UUID for player: " + playerName));
                            break;
                        }

                        if (action.equals("addperm")) {
                            service.addUserPermission(id, perm);
                            plugin.getPermissionService().saveAsync();
                            send(sender, ColorConverter.colorize("&aAdded permission " + perm + " to " + playerName));
                        } else if (action.equals("removeperm")) {
                            service.removeUserPermission(id, perm);
                            plugin.getPermissionService().saveAsync();
                            send(sender, ColorConverter.colorize("&aRemoved permission " + perm + " from " + playerName));
                        } else if (action.equals("addgroup")) {
                            service.addUserToGroup(id, perm);
                            plugin.getPermissionService().saveAsync();
                            send(sender, ColorConverter.colorize("&aAdded " + playerName + " to group " + perm));
                        } else if (action.equals("removegroup")) {
                            service.removeUserFromGroup(id, perm);
                            plugin.getPermissionService().saveAsync();
                            send(sender, ColorConverter.colorize("&aRemoved " + playerName + " from group " + perm));
                        } else {
                            send(sender, ColorConverter.colorize("&cUnknown user action: " + action));
                        }
                    } catch (Exception e) {
                        kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Exception handling /fperm user", e);
                        send(sender, ColorConverter.colorize("&cInternal error while processing user command."));
                    }
                    break;
                case "group":
                    if (args.length < 2) {
                        send(sender, ColorConverter.colorize("&eUsage: /fperm group create|delete|addperm|adduser|removeuser|setinherit|removeinherit|inheritance|perms <args>"));
                        break;
                    }
                    try {
                        String gaction = args[1].toLowerCase();
                        if (gaction.equals("create")) {
                            if (args.length < 3) { send(sender, ColorConverter.colorize("Usage: /fperm group create <name>")); break; }
                            service.createGroup(args[2]);
                            plugin.getPermissionService().saveAsync();
                            send(sender, ColorConverter.colorize("&aGroup created: " + args[2]));
                        } else if (gaction.equals("addperm")) {
                            if (args.length < 4) { send(sender, ColorConverter.colorize("&eUsage: /fperm group addperm <name> <perm>")); break; }
                            service.addGroupPermission(args[2], args[3]);
                            plugin.getPermissionService().saveAsync();
                            send(sender, ColorConverter.colorize("&aAdded permission " + args[3] + " to group " + args[2]));
                        } else if (gaction.equals("adduser")) {
                            if (args.length < 4) { send(sender, ColorConverter.colorize("&eUsage: /fperm group adduser <name> <player>")); break; }
                            String gname = args[2];
                            var target = Bukkit.getOfflinePlayer(args[3]);
                            if (target == null || target.getUniqueId() == null) {
                                send(sender, ColorConverter.colorize("&cCould not resolve player: " + args[3]));
                                break;
                            }
                            service.addUserToGroup(target.getUniqueId(), gname);
                            plugin.getPermissionService().saveAsync();
                            send(sender, ColorConverter.colorize("&aAdded " + args[3] + " to group " + gname));
                        } else if (gaction.equals("delete")) {
                            if (args.length < 3) { send(sender, ColorConverter.colorize("&eUsage: /fperm group delete <name>")); break; }
                            String gdel = args[2];
                            if (gdel.equalsIgnoreCase("default")) {
                                send(sender, ColorConverter.colorize("&cThe default group cannot be deleted."));
                                break;
                            }
                            boolean deleted = service.deleteGroup(gdel);
                            if (deleted) {
                                plugin.getPermissionService().saveAsync();
                                send(sender, ColorConverter.colorize("&aGroup '" + gdel + "' deleted. Members re-assigned to default group."));
                            } else {
                                send(sender, ColorConverter.colorize("&cCould not delete group '" + gdel + "'. It may not exist or is protected."));
                            }
                        } else if (gaction.equals("removeuser")) {
                            if (args.length < 4) { send(sender, ColorConverter.colorize("&eUsage: /fperm group removeuser <name> <player>")); break; }
                            String gname2 = args[2];
                            var target2 = Bukkit.getOfflinePlayer(args[3]);
                            if (target2 == null || target2.getUniqueId() == null) {
                                send(sender, ColorConverter.colorize("&cCould not resolve player: " + args[3]));
                                break;
                            }
                            service.removeUserFromGroup(target2.getUniqueId(), gname2);
                            plugin.getPermissionService().saveAsync();
                            send(sender, ColorConverter.colorize("&aRemoved " + args[3] + " from group " + gname2));
                        } else if (gaction.equals("setinherit")) {
                            if (args.length < 4) { send(sender, ColorConverter.colorize("&eUsage: /fperm group setinherit <group> <parent>")); break; }
                            boolean success = service.addGroupInheritance(args[2], args[3]);
                            if (success) {
                                plugin.getPermissionService().saveAsync();
                                send(sender, ColorConverter.colorize("&aGroup '" + args[2] + "' now inherits from '" + args[3] + "'."));
                            } else {
                                send(sender, ColorConverter.colorize("&cFailed to set inheritance. Check for circular dependencies."));
                            }
                        } else if (gaction.equals("removeinherit")) {
                            if (args.length < 4) { send(sender, ColorConverter.colorize("&eUsage: /fperm group removeinherit <group> <parent>")); break; }
                            service.removeGroupInheritance(args[2], args[3]);
                            plugin.getPermissionService().saveAsync();
                            send(sender, ColorConverter.colorize("&aGroup '" + args[2] + "' no longer inherits from '" + args[3] + "'."));
                        } else if (gaction.equals("inheritance")) {
                            if (args.length < 3) { send(sender, ColorConverter.colorize("&eUsage: /fperm group inheritance <group>")); break; }
                            var chain = service.getGroupInheritanceChain(args[2]);
                            send(sender, ColorConverter.colorize("&eInheritance chain for '" + args[2] + "':"));
                            if (chain.isEmpty()) {
                                send(sender, ColorConverter.colorize(" &7- (no inheritance)"));
                            } else {
                                for (String g : chain) {
                                    send(sender, ColorConverter.colorize(" &7- " + g));
                                }
                            }
                            // Also show parents directly
                            var gd = service.getGroup(args[2]);
                            if (gd != null && !gd.getParents().isEmpty()) {
                                send(sender, ColorConverter.colorize("&eDirect parents: " + String.join(", ", gd.getParents())));
                            }
                        } else if (gaction.equals("perms")) {
                            if (args.length < 3) { send(sender, ColorConverter.colorize("&eUsage: /fperm group perms <group>")); break; }
                            var gd = service.getGroup(args[2]);
                            if (gd == null) {
                                send(sender, ColorConverter.colorize("&cGroup not found: " + args[2]));
                                break;
                            }
                            send(sender, ColorConverter.colorize("&ePermissions for group '" + args[2] + "':"));
                            // Direct permissions
                            if (gd.getPermissions().isEmpty()) {
                                send(sender, ColorConverter.colorize(" &7- (no direct permissions)"));
                            } else {
                                send(sender, ColorConverter.colorize(" &bDirect permissions:"));
                                for (String p : gd.getPermissions()) {
                                    send(sender, ColorConverter.colorize(" &7- " + p));
                                }
                            }
                            // Inherited permissions
                            var inherited = gd.getAllInheritedPermissions(service.getGroups());
                            inherited.removeAll(gd.getPermissions()); // Only show inherited ones
                            if (!inherited.isEmpty()) {
                                send(sender, ColorConverter.colorize(" &bInherited permissions (from parents):"));
                                for (String p : inherited) {
                                    send(sender, ColorConverter.colorize(" &7- " + p));
                                }
                            }
                        } else {
                            send(sender, ColorConverter.colorize("&cUnknown group action: " + gaction));
                        }
                    } catch (Exception e) {
                        kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Exception handling /fperm group", e);
                        send(sender, ColorConverter.colorize("&cInternal error while processing group command."));
                    }
                    break;
                case "check":
                    if (args.length < 3) { send(sender, ColorConverter.colorize("&eUsage: /fperm check <player> <perm>")); break; }
                    OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                    boolean ok = service.hasPermission(t.getUniqueId(), args[2]);
                    send(sender, ColorConverter.colorize(args[1] + (ok ? " &aHAS " : " &cDOES NOT HAVE ") + args[2]));
                    break;
                case "listperms":
                    if (args.length < 2) { send(sender, ColorConverter.colorize("&eUsage: /fperm listperms <player>")); break; }
                    try {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                        if (target == null || target.getUniqueId() == null) {
                            send(sender, ColorConverter.colorize("&cCould not resolve player: " + args[1]));
                            break;
                        }
                        var perms = service.getAllowedPermissions(target.getUniqueId());
                        if (perms.isEmpty()) {
                            send(sender, ColorConverter.colorize("&e" + args[1] + " has no registered permissions (or none gathered)."));
                        } else {
                            send(sender, ColorConverter.colorize("&ePermissions for " + args[1] + ":"));
                            for (String p : perms) send(sender, ColorConverter.colorize(" - " + p));
                        }
                    } catch (Exception e) {
                        kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Exception during listperms", e);
                        send(sender, ColorConverter.colorize("&cInternal error while listing permissions."));
                    }
                    break;
                default:
                    send(sender, ColorConverter.colorize("&eUnknown subcommand. Use /fperm help"));
            }
        } catch (Exception ex) {
            kaiakk.foliaPerms.internal.ErrorHandler.handle(plugin, "Unhandled exception while executing /fperm", ex);
            send(sender, ColorConverter.colorize("&cInternal error while executing command."));
        }

        return true;
    }
}