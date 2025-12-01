package kaiakk.foliaPerms.permissions;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class YamlStorage {
    private final JavaPlugin plugin;
    private final File file;

    public YamlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "permissions.yml");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
    }

    public Map<UUID, UserData> loadUsers() {
        Map<UUID, UserData> users = new HashMap<>();
        if (!file.exists()) return users;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.isConfigurationSection("users")) return users;
        for (String key : cfg.getConfigurationSection("users").getKeys(false)) {
            UUID id = null;
            try {
                id = UUID.fromString(key);
            } catch (Exception ex) {
                try {
                    var off = plugin.getServer().getOfflinePlayer(key);
                    if (off != null) id = off.getUniqueId();
                } catch (Exception ignored) {
                }
            }
            if (id == null) continue;
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
        return users;
    }

    public Map<String, GroupData> loadGroups() {
        Map<String, GroupData> groups = new HashMap<>();
        if (!file.exists()) return groups;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.isConfigurationSection("groups")) return groups;
        for (String key : cfg.getConfigurationSection("groups").getKeys(false)) {
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
            groups.put(key.toLowerCase(), gd);
        }
        return groups;
    }

    public void save(Map<UUID, UserData> users, Map<String, GroupData> groups) throws IOException {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("users", null);
        cfg.set("groups", null);

        for (Map.Entry<UUID, UserData> e : users.entrySet()) {
            String path = "users." + e.getKey().toString();
            cfg.set(path + ".permissions", e.getValue().getPermissions().stream().toList());
            cfg.set(path + ".groups", e.getValue().getGroups().stream().toList());
        }

        for (Map.Entry<String, GroupData> e : groups.entrySet()) {
            String key = e.getKey().toLowerCase();
            String path = "groups." + key;
            cfg.set(path + ".permissions", e.getValue().getPermissions().stream().toList());
            cfg.set(path + ".members", e.getValue().getMembers().stream().toList());
        }

        cfg.save(file);
    }
}