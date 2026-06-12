package kaiakk.foliaPerms.commands;

import kaiakk.foliaPerms.FoliaPerms;
import kaiakk.foliaPerms.permissions.PermissionService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FpermTabCompleter implements TabCompleter {
    private final FoliaPerms plugin;
    private final PermissionService service;

    public FpermTabCompleter(FoliaPerms plugin) {
        this.plugin = plugin;
        this.service = plugin.getPermissionService();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> res = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            String[] opts = new String[]{"help","reload","gather","refresh","user","group","check","listperms","editor"};
            for (String s : opts) if (s.startsWith(partial)) res.add(s);
            return res;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("user")) {
            if (args.length == 2) {
                String[] opts = new String[]{"addperm","removeperm","addgroup","removegroup"};
                for (String s : opts) if (s.startsWith(args[1].toLowerCase())) res.add(s);
                return res;
            }
            if (args.length == 3) {
                return Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 4) {
                String action = args[1].toLowerCase();
                if (action.equals("addperm") || action.equals("removeperm")) {
                    String partial = args[3] == null ? "" : args[3].toLowerCase();
                    if (service == null) return Collections.emptyList();
                    return service.getRegisteredPermissions().stream()
                            .filter(p -> p != null && p.toLowerCase().startsWith(partial))
                            .sorted()
                            .limit(25)
                            .collect(Collectors.toList());
                }
                if (action.equals("addgroup") || action.equals("removegroup")) {
                    String partial = args[3] == null ? "" : args[3].toLowerCase();
                    if (service == null) return Collections.emptyList();
                    return service.getGroups().keySet().stream().filter(g -> g.startsWith(partial)).sorted().collect(Collectors.toList());
                }
            }
            return res;
        }

        if (sub.equals("group")) {
            if (args.length == 2) {
                String[] opts = new String[]{"create","delete","addperm","adduser","removeuser","setinherit","removeinherit","inheritance","perms"};
                for (String s : opts) if (s.startsWith(args[1].toLowerCase())) res.add(s);
                return res;
            }
            if (args.length >= 3) {
                String action = args[1].toLowerCase();
                if (action.equals("create")) {
                    return res;
                }
                if (action.equals("delete")) {
                    if (args.length == 3) {
                        return service.getGroups().keySet().stream()
                                .filter(g -> !g.equalsIgnoreCase("default"))
                                .filter(g -> g.startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
                if (action.equals("addperm")) {
                    if (args.length == 3) {
                        return service.getGroups().keySet().stream().filter(g -> g.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                    }
                    if (args.length == 4) {
                        String partial = args[3] == null ? "" : args[3].toLowerCase();
                        if (service == null) return Collections.emptyList();
                        return service.getRegisteredPermissions().stream()
                                .filter(p -> p != null && p.toLowerCase().startsWith(partial))
                                .sorted()
                                .limit(25)
                                .collect(Collectors.toList());
                    }
                }
                if (action.equals("adduser")) {
                    if (args.length == 3) {
                        return service.getGroups().keySet().stream().filter(g -> g.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                    }
                    if (args.length == 4) {
                        return Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase())).collect(Collectors.toList());
                    }
                }
                if (action.equals("removeuser")) {
                    if (args.length == 3) {
                        return service.getGroups().keySet().stream().filter(g -> g.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                    }
                    if (args.length == 4) {
                        return Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase())).collect(Collectors.toList());
                    }
                }
                if (action.equals("setinherit")) {
                    if (args.length == 3) {
                        // Suggest child groups
                        return service.getGroups().keySet().stream()
                                .filter(g -> g.startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    if (args.length == 4) {
                        // Suggest parent groups (excluding self)
                        String child = args[2].toLowerCase();
                        return service.getGroups().keySet().stream()
                                .filter(g -> !g.equals(child))
                                .filter(g -> g.startsWith(args[3].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
                if (action.equals("removeinherit")) {
                    if (args.length == 3) {
                        return service.getGroups().keySet().stream()
                                .filter(g -> g.startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    if (args.length == 4) {
                        // Suggest only parents of the specified group
                        String groupName = args[2].toLowerCase();
                        var gd = service.getGroup(groupName);
                        if (gd != null) {
                            return gd.getParents().stream()
                                    .filter(p -> p.startsWith(args[3].toLowerCase()))
                                    .collect(Collectors.toList());
                        }
                    }
                }
                if (action.equals("inheritance") || action.equals("perms")) {
                    if (args.length == 3) {
                        return service.getGroups().keySet().stream()
                                .filter(g -> g.startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
            }
        }

        if (sub.equals("check")) {
            if (args.length == 2) {
                return Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 3) {
                String partial = args[2] == null ? "" : args[2].toLowerCase();
                if (service == null) return Collections.emptyList();
                return service.getRegisteredPermissions().stream()
                        .filter(p -> p != null && p.toLowerCase().startsWith(partial))
                        .sorted()
                        .limit(50)
                        .collect(Collectors.toList());
            }
        }
        if (sub.equals("listperms")) {
            if (args.length == 2) {
                return Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        return res;
    }
}