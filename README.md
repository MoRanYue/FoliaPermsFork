# FoliaPerms

![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)
![Folia](https://img.shields.io/badge/Folia-supported-brightgreen?style=flat-square)

**FoliaPermsFork** is a lightweight, simple permission management plugin designed specifically for **Folia** multi-threaded Minecraft servers. It provides an intuitive permission system with group management, inheritance, and a built-in GUI editor — all without external dependencies.

> ⚠️ **Warning:** This is a fork of [kaiakkyt's FoliaPerms](https://github.com/kaiakkyt/foliaperms) which added more comprehensive functions.

---

## Features

- ✅ **Default Group** — Every new player is automatically assigned to the `default` group
- ✅ **Group Inheritance** — Groups can inherit permissions from parent groups (with cycle detection)
- ✅ **Single-Group Enforcement** — Each user belongs to exactly **one** group at a time
- ✅ **Permission GUI Editor** — In-game graphical interface for managing permissions
- ✅ **Inheritance GUI Editor** — Visual management of group inheritance relationships
- ✅ **Group & User Permissions** — Add/remove permissions per user or per group
- ✅ **YAML Persistence** — All data is stored in a simple `permissions.yml` file
- ✅ **Wildcard Support** — Supports wildcard permissions (e.g., `plugin.*`)
- ✅ **Folia-Compatible** — Built from the ground up for Folia's threading model
- ✅ **No Dependencies** — Standalone, no external plugins required

---

## Commands

All commands use the `/fperm` prefix and require the `folia.perms` permission (granted to OP by default).

### General

| Command | Description |
|---------|-------------|
| `/fperm help` | Show help menu |
| `/fperm reload` | Reload permissions from file |
| `/fperm gather` | Gather permissions from all plugins |
| `/fperm refresh` | Refresh all permission attachments |

### User Management

| Command | Description |
|---------|-------------|
| `/fperm user addperm <player> <perm>` | Add a permission to a player |
| `/fperm user removeperm <player> <perm>` | Remove a permission from a player |
| `/fperm user addgroup <player> <group>` | Add a player to a group (removes from current group) |
| `/fperm user removegroup <player> <group>` | Remove a player from a group (auto-falls back to `default`) |

### Group Management

| Command | Description |
|---------|-------------|
| `/fperm group create <name>` | Create a new group |
| `/fperm group delete <name>` | Delete a group (cannot delete `default`) |
| `/fperm group addperm <name> <perm>` | Add a permission to a group |
| `/fperm group adduser <name> <player>` | Add a player to a group |
| `/fperm group removeuser <name> <player>` | Remove a player from a group |

### Inheritance Management

| Command | Description |
|---------|-------------|
| `/fperm group setinherit <group> <parent>` | Make a group inherit from a parent group |
| `/fperm group removeinherit <group> <parent>` | Remove inheritance relationship |
| `/fperm group inheritance <group>` | Show the inheritance chain |
| `/fperm group perms <group>` | Show all effective permissions (including inherited) |

### Query Commands

| Command | Description |
|---------|-------------|
| `/fperm check <player> <perm>` | Check if a player has a permission |
| `/fperm listperms <player>` | List all permissions for a player |

### GUI Editor

| Command | Description |
|---------|-------------|
| `/fperm editor` | Open the in-game permission editor GUI |

---

## GUI Editor

FoliaPerms features a full in-game GUI editor for managing permissions and inheritance without needing to type commands.

### Navigation Flow

```
Main Menu
  ├── Edit Groups
  │     └── Click a Group → Permission Editor
  │           └── [Inheritance] Button → Inheritance Editor
  │                 └── Click groups to toggle as parent
  │
  └── Edit Players
        └── Click a Player → Permission Editor
```

### Permission Editor
- Browse all registered permissions with pagination
- Green dye = granted, Red dye = not granted
- Click to toggle permissions on/off
- Navigation: Previous Page, Next Page, Back

### Inheritance Editor
- Lists all groups (except self) as toggleable items
- Green dye = current parent, Red dye = not a parent
- Click to add/remove inheritance
- Automatic cycle detection prevents circular dependencies
- Changes are persisted immediately

---

## Default Group & Inheritance

### Default Group (`default`)

- Every new player is automatically assigned to the `default` group
- The `default` group **cannot** be deleted or have all members removed from it
- If a player is removed from their last non-default group, they automatically fall back to `default`
- The `default` group is always recreated on reload if missing

### Inheritance

Groups can inherit permissions from one or more parent groups, creating a hierarchy:

```
admin
  └─ inherits from → moderator
                       └─ inherits from → default
```

- Permission resolution walks the inheritance chain recursively
- Cycle detection prevents circular inheritance (e.g., A → B → A)
- The `group inheritance` command shows the full chain
- The `group perms` command shows all effective permissions including inherited ones

---

## API Usage

FoliaPerms registers itself with the Bukkit Services Manager. You can access the API in your plugin:

```java
// Get the API instance
FoliaPermsAPI api = Bukkit.getServicesManager()
    .load(FoliaPermsAPI.class);

if (api != null) {
    // Check permissions
    boolean has = api.hasPermission(player, "some.permission");
    
    // Get player's groups
    Set<String> groups = api.getPlayerGroups(player);
    
    // Get primary group
    String primary = api.getPrimaryGroup(player);
}
```

---

## Installation

1. Download the latest JAR file on [GitHub Releases](https://github.com/kaiakkyt/foliaperms/releases)
2. Place the JAR file in your server's `plugins/` folder
3. Restart your server (or use a plugin manager)
4. Configure groups and permissions via commands or the GUI editor

> **Note:** FoliaPerms is **Folia-only**. It will not work on Bukkit/Spigot/Paper/Purpur servers.

---

## Configuration

### Main Configuration (`config.yml`)

The main configuration file `plugins/FoliaPerms/config.yml` is automatically created on first run.

```yaml
# FoliaPerms Main Configuration
#
# Language Setting
# Set the filename in the locales directory (without extension)
# e.g.: en_us, zh_cn
language: en_us
```

- **`language`**: Specifies which locale file to use from the `plugins/FoliaPerms/locales/` directory. Default is `en_us`. To switch to Simplified Chinese, set it to `zh_cn`.

### Permission Data (`permissions.yml`)

All permission data is stored in `plugins/FoliaPerms/permissions.yml`. The file is automatically created on first run and includes the `default` group.

### Example `permissions.yml`

```yaml
users:
  <player-uuid>:
    permissions:
      - some.permission
    groups:
      - admin
groups:
  default:
    permissions:
      - essentials.spawn
    members: []
    parents: []
  admin:
    permissions:
      - server.stop
      - server.*
    members:
      - <player-uuid>
    parents:
      - default
```

---

## Building from Source

```bash
git clone https://github.com/MoRanYue/FoliaPermsFork.git
cd FoliaPermsFork
./gradlew build
```

The compiled JAR will be in `build/libs/`.

### Requirements

- Java 25+
- Gradle 9.5+ (included via wrapper)

---

## License

FoliaPermsFork is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

---

## Links

- [Wiki & Documentation](https://kaiakkmc.pages.dev/docs)
- [Issue Tracker](https://github.com/MoRanYue/FoliaPermsFork/issues) ([Origin](https://github.com/kaiakkyt/foliaperms/issues))
