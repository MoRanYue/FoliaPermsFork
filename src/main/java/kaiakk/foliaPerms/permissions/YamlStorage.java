package kaiakk.foliaPerms.permissions;

import kaiakk.foliaPerms.FoliaPerms;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * YAML-based storage for user and group permission data.
 * Version: 0.1.0+26.1.2
 */
public class YamlStorage {
    private final JavaPlugin plugin;
    private final File file;

    /** Name of the default group that all new players are automatically added to. */
    public static final String DEFAULT_GROUP_NAME = "default";

    public YamlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "permissions.yml");
        if (!file.getParentFile().exists()) {
            boolean created = file.getParentFile().mkdirs();
            if (created) {
                plugin.getLogger().fine(tlRaw("console.storage.created-folder", file.getParentFile()));
            }
        }
    }

    /**
     * Convenience accessor for localized console messages.
     */
    private String tlRaw(String key, Object... args) {
        if (plugin instanceof FoliaPerms) {
            return ((FoliaPerms) plugin).tlRaw(key, args);
        }
        return key;
    }

    public Map<UUID, UserData> loadUsers() {
        Map<UUID, UserData> users = new HashMap<>();
        if (!file.exists()) {
            plugin.getLogger().fine(tlRaw("console.storage.file-not-exist"));
            return users;
        }

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            if (!cfg.isConfigurationSection("users")) {
                plugin.getLogger().fine(tlRaw("console.storage.no-users"));
                return users;
            }

            var usersSection = cfg.getConfigurationSection("users");
            if (usersSection == null) return users;

            for (String key : usersSection.getKeys(false)) {
                UUID id = null;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException ex) {
                    // Try legacy player name lookup
                    try {
                        var off = plugin.getServer().getOfflinePlayer(key);
                        if (off != null) id = off.getUniqueId();
                    } catch (Exception ignored) {}
                }

                if (id == null) {
                    plugin.getLogger().warning(tlRaw("console.storage.cannot-resolve", key));
                    continue;
                }

                UserData ud = new UserData(id);
                if (cfg.isList("users." + key + ".permissions")) {
                    for (Object o : cfg.getList("users." + key + ".permissions")) {
                        ud.addPermission(String.valueOf(o));
                    }
                }
                if (cfg.isList("users." + key + ".groups")) {
                    for (Object o : cfg.getList("users." + key + ".groups")) {
                        ud.addGroup(String.valueOf(o));
                    }
                }
                users.put(id, ud);
            }

            plugin.getLogger().fine(tlRaw("console.permission.loaded-users", users.size()));
        } catch (Exception e) {
            plugin.getLogger().warning(tlRaw("console.storage.error-load", e.getMessage()));
            e.printStackTrace();
        }

        return users;
    }

    public Map<String, GroupData> loadGroups() {
        Map<String, GroupData> groups = new HashMap<>();

        try {
            FileConfiguration cfg = null;
            if (file.exists()) {
                cfg = YamlConfiguration.loadConfiguration(file);
            }

            if (cfg != null && cfg.isConfigurationSection("groups")) {
                var groupsSection = cfg.getConfigurationSection("groups");
                if (groupsSection != null) {
                    for (String key : groupsSection.getKeys(false)) {
                        GroupData gd = new GroupData(key);
                        if (cfg.isList("groups." + key + ".permissions")) {
                            for (Object o : cfg.getList("groups." + key + ".permissions")) {
                                gd.addPermission(String.valueOf(o));
                            }
                        }
                        if (cfg.isList("groups." + key + ".members")) {
                            for (Object o : cfg.getList("groups." + key + ".members")) {
                                gd.addMember(String.valueOf(o));
                            }
                        }
                        // Load inheritance: parent groups
                        if (cfg.isList("groups." + key + ".parents")) {
                            for (Object o : cfg.getList("groups." + key + ".parents")) {
                                gd.addParent(String.valueOf(o));
                            }
                        }
                        groups.put(key.toLowerCase(), gd);
                    }
                }
            }

            // Ensure the default group always exists
            if (!groups.containsKey(DEFAULT_GROUP_NAME)) {
                plugin.getLogger().info(tlRaw("console.storage.creating-default", DEFAULT_GROUP_NAME));
                GroupData defaultGroup = new GroupData(DEFAULT_GROUP_NAME);
                groups.put(DEFAULT_GROUP_NAME, defaultGroup);
            }

            plugin.getLogger().fine(tlRaw("console.permission.loaded-groups", groups.size()));
        } catch (Exception e) {
            plugin.getLogger().warning(tlRaw("console.storage.error-load", e.getMessage()));
            e.printStackTrace();
        }

        return groups;
    }

    public void save(Map<UUID, UserData> users, Map<String, GroupData> groups) throws IOException {
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            cfg.set("users", null);
            cfg.set("groups", null);

            // Save users
            for (Map.Entry<UUID, UserData> e : users.entrySet()) {
                String path = "users." + e.getKey().toString();
                cfg.set(path + ".permissions", e.getValue().getPermissions().stream().toList());
                cfg.set(path + ".groups", e.getValue().getGroups().stream().toList());
            }

            // Save groups
            for (Map.Entry<String, GroupData> e : groups.entrySet()) {
                String key = e.getKey().toLowerCase();
                String path = "groups." + key;
                cfg.set(path + ".permissions", e.getValue().getPermissions().stream().toList());
                cfg.set(path + ".members", e.getValue().getMembers().stream().toList());
                // Save inheritance: parent groups
                cfg.set(path + ".parents", e.getValue().getParentsMutable().stream().toList());
            }

            cfg.save(file);
            plugin.getLogger().fine(tlRaw("console.permission.saved-yaml", users.size(), groups.size()));
        } catch (IOException e) {
            plugin.getLogger().severe(tlRaw("console.storage.error-save", e.getMessage()));
            throw e;
        }
    }
}
