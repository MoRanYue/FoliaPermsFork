# FoliaPerms 本地化功能实施方案

## 概述

为 FoliaPerms 权限管理插件添加完整的本地化支持，覆盖玩家聊天栏消息、容器 GUI 文本和服务端控制台文本。本地化配置文件存放于 `plugins/FoliaPerms/locales/` 目录，用户通过主配置文件 `config.yml` 中的 `language` 字段指定使用的语言。

---

## 架构设计

### 1. 新增文件

```
src/main/java/kaiakk/foliaPerms/internal/LocaleManager.java   # 本地化管理器
src/main/resources/config.yml                                   # 默认配置文件（新增 language 字段）
src/main/resources/locales/en_us.yml                           # 英文语言文件
src/main/resources/locales/zh_cn.yml                           # 中文语言文件
```

### 2. LocaleManager 核心设计

**类路径**: [`kaiakk.foliaPerms.internal.LocaleManager`](src/main/java/kaiakk/foliaPerms/internal/LocaleManager.java)

**功能**:
- 从 `plugins/FoliaPerms/locales/<language>.yml` 加载 YAML 格式的语言配置
- 提供 `getString(String key, Object... args)` 方法，支持 `{0}`、`{1}` 等占位符替换
- 提供 `getColoredString(String key, Object... args)` 方法，返回经过 `ColorConverter.colorize()` 处理后的文本
- 当指定的语言文件不存在时，自动回退到 `en_us` 作为默认语言
- 缓存语言数据，避免重复 I/O
- 提供 `reload()` 方法用于热重载

**核心方法签名**:
```java
public class LocaleManager {
    public LocaleManager(FoliaPerms plugin);
    public void load(String language);                    // 加载指定语言
    public void reload();                                  // 重新加载当前语言
    public String getString(String key, Object... args);   // 获取原始文本
    public String getColoredString(String key, Object... args); // 获取已着色文本
    public String getCurrentLanguage();                    // 获取当前语言代码
    public boolean hasKey(String key);                     // 检查键是否存在
}
```

### 3. 配置文件变更

**config.yml**（首次运行自动生成）:
```yaml
# FoliaPerms 配置文件
# 语言设置：填写 locales 目录中的文件名（不含扩展名）
# 例如：en_us、zh_cn
language: en_us
```

**创建方式**: 在 [`FoliaPerms.java`](src/main/java/kaiakk/foliaPerms/FoliaPerms.java) 的 `onEnable()` 中调用 `saveDefaultConfig()` 生成默认配置文件。

### 4. 本地化键命名规范

使用点号分隔的层级结构，按模块分组：

```
# 根分类
console.*        # 服务端控制台消息
chat.*           # 玩家聊天栏消息
gui.*            # GUI 容器文本
error.*          # 错误消息

# 子分类
console.plugin.*       # 插件生命周期消息
console.permission.*   # 权限操作日志
console.storage.*      # 存储相关消息
console.player.*       # 玩家事件日志

chat.help.*            # 帮助菜单
chat.success.*         # 成功消息
chat.error.*           # 错误消息
chat.info.*            # 信息消息

gui.main.*             # 主菜单
gui.target-list.*      # 目标列表
gui.permission-editor.* # 权限编辑界面
gui.inheritance-editor.* # 继承编辑界面
```

### 5. 消息格式支持

- **颜色代码**: 使用 `&` 前缀的 Minecraft 颜色代码（如 `&a`、`&c`、`&e`、`&7` 等），通过 `ColorConverter.colorize()` 渲染
- **占位符**: 使用 `{0}`、`{1}`、`{2}` 等数字索引占位符，支持动态参数替换
- **消息中引用玩家/组名等动态内容时使用占位符**

### 6. 集成方式

在 [`FoliaPerms.java`](src/main/java/kaiakk/foliaPerms/FoliaPerms.java) 中：
1. 添加 `private LocaleManager localeManager;` 字段
2. 在 `onEnable()` 中：`saveDefaultConfig()` → 读取 `language` 字段 → 初始化 `LocaleManager`
3. 提供 `public LocaleManager getLocaleManager()` 或 `public String tl(String key, Object... args)` 便捷方法
4. 将 `LocaleManager` 实例传递给所有需要本地化的组件

---

## 实施步骤

### Step 1: 创建 LocaleManager 类

**文件**: [`src/main/java/kaiakk/foliaPerms/internal/LocaleManager.java`](src/main/java/kaiakk/foliaPerms/internal/LocaleManager.java)

**关键实现细节**:
- 使用 `YamlConfiguration` 加载语言文件
- 语言文件存放路径: `plugin.getDataFolder() + "/locales/" + language + ".yml"`
- 内置默认语言数据，当文件不存在时使用内部默认值
- 实现 `MessageFormat` 风格的 `{0}` 占位符替换
- 线程安全（使用 ConcurrentHashMap 或只读后共享）

### Step 2: 创建默认 config.yml

**文件**: [`src/main/resources/config.yml`](src/main/resources/config.yml)

- 仅包含 `language: en_us` 字段
- 在 [`FoliaPerms.java`](src/main/java/kaiakk/foliaPerms/FoliaPerms.java) 的 `onEnable()` 中调用 `saveDefaultConfig()`

### Step 3: 创建 en_us.yml 和 zh_cn.yml

**文件**: 
- [`src/main/resources/locales/en_us.yml`](src/main/resources/locales/en_us.yml)
- [`src/main/resources/locales/zh_cn.yml`](src/main/resources/locales/zh_cn.yml)

**注意事项**:
- 首次启动时将 `src/main/resources/locales/` 下的文件复制到 `plugins/FoliaPerms/locales/`
- 语言文件使用 YAML 格式，UTF-8 编码

### Step 4: 逐文件改造为本地化调用

需要改造的文件及对应的键前缀：

| 文件 | 本地化键前缀 | 内容类型 |
|------|-------------|---------|
| [`FoliaPerms.java`](src/main/java/kaiakk/foliaPerms/FoliaPerms.java) | `console.plugin.*` | 控制台消息 |
| [`FpermCommand.java`](src/main/java/kaiakk/foliaPerms/commands/FpermCommand.java) | `chat.*` | 玩家聊天消息 |
| [`EditorGui.java`](src/main/java/kaiakk/foliaPerms/gui/EditorGui.java) | `gui.*` | GUI 容器文本 |
| [`GuiListener.java`](src/main/java/kaiakk/foliaPerms/gui/GuiListener.java) | `chat.*`, `gui.*` | 玩家消息+日志 |
| [`PlayerListener.java`](src/main/java/kaiakk/foliaPerms/events/PlayerListener.java) | `console.player.*`, `chat.*` | 控制台+玩家消息 |
| [`PermissionService.java`](src/main/java/kaiakk/foliaPerms/permissions/PermissionService.java) | `console.permission.*` | 控制台日志 |
| [`YamlStorage.java`](src/main/java/kaiakk/foliaPerms/permissions/YamlStorage.java) | `console.storage.*` | 控制台日志 |
| [`ErrorHandler.java`](src/main/java/kaiakk/foliaPerms/internal/ErrorHandler.java) | `error.*` | 错误消息 |
| [`UpdateChecker.java`](src/main/java/kaiakk/foliaPerms/internal/UpdateChecker.java) | `console.update.*` | 控制台消息 |
| [`PluginEnableListener.java`](src/main/java/kaiakk/foliaPerms/events/PluginEnableListener.java) | `console.plugin.*` | 控制台消息 |

### Step 5: 便捷方法设计

在 [`FoliaPerms.java`](src/main/java/kaiakk/foliaPerms/FoliaPerms.java) 中添加便捷方法：

```java
/**
 * 获取本地化字符串（自动着色）
 */
public String tl(String key, Object... args) {
    return localeManager != null 
        ? localeManager.getColoredString(key, args) 
        : key;
}

/**
 * 获取原始本地化字符串（不着色，用于控制台）
 */
public String tlRaw(String key, Object... args) {
    return localeManager != null 
        ? localeManager.getString(key, args) 
        : key;
}
```

---

## 键值清单（完整）

以下是所有需要定义的本地化键，按模块分组：

### console.plugin.*
```yaml
console.plugin.loading: "Loading all permissions data..."
console.plugin.enabled: "FoliaPermsFork v{0} enabled successfully. Welcome to the Folia environment!"
console.plugin.disabling: "FoliaPermsFork v{0} disabling..."
console.plugin.saving: "Saving permissions..."
console.plugin.saved: "Permissions saved."
console.plugin.disabled: "FoliaPerms disabled successfully."
console.plugin.not-folia-header: "Error detected!"
console.plugin.not-folia: "FoliaPerms is a Folia-only plugin!"
console.plugin.not-folia-server: "It appears you are running a normal Bukkit/Paper/Spigot server."
console.plugin.self-disable: "This plugin will now disable itself, goodbye."
console.plugin.disabling-folia: "Disabling FoliaPerms..."
console.plugin.folia-detected: "Folia environment detected. FoliaPerms is ready to enable."
console.plugin.api-registered: "FoliaPerms API registered with ServicesManager."
console.plugin.gathered-perms: "Gathered {0} permissions from plugins."
console.plugin.permission-prefix: " - {0}"
console.plugin.and-more: " ... and {0} more"
console.plugin.attachments-initialized: "Permission attachments initialized for {0} players."
console.plugin.attachments-cleaned: "All permission attachments cleaned up."
console.plugin.error-load-perms: "Failed to load permissions data"
console.plugin.error-gather-perms: "Failed to gather registered permissions"
```

### console.permission.*
```yaml
console.permission.added-user: "Added permission '{0}' to user {1}"
console.permission.removed-user: "Removed permission '{0}' from user {1}"
console.permission.added-group: "Added group permission '{0}' to group {1}"
console.permission.removed-group: "Removed permission '{0}' from group {1}"
console.permission.group-created: "Group '{0}' has been created."
console.permission.group-deleted: "Group '{0}' has been deleted. All members re-assigned to default group."
console.permission.user-added-group: "User {0} is now in group '{1}' (single-group mode)."
console.permission.user-removed-group: "Removed user {0} from group '{1}'."
console.permission.user-auto-default: "Auto-assigned user {0} back to default group after removal from '{1}'."
console.permission.user-already-group: "User {0} is already in group '{1}'."
console.permission.user-removed-prev: "Removed user {0} from previous group '{1}'."
console.permission.inheritance-added: "Group '{0}' now inherits from '{1}'."
console.permission.inheritance-removed: "Group '{0}' no longer inherits from '{1}'."
console.permission.inheritance-cycle: "Cannot add inheritance: {0} -> {1} would create a circular dependency."
console.permission.inheritance-self: "Cannot set a group to inherit from itself: {0}"
console.permission.default-group-created: "Created implicit default group '{0}' in memory."
console.permission.default-group-protected: "Attempted to delete the default group '{0}' - operation blocked."
console.permission.group-not-exist: "Group '{0}' does not exist, cannot delete."
console.permission.group-not-found: "Group '{0}' not found."
console.permission.user-not-found: "Cannot remove user {0} from group: user not found."
console.permission.user-not-in-group: "User {0} is not in group '{1}'."
console.permission.user-cannot-remove-default: "Cannot remove user {0} from the default group (they must always have a group)."
console.permission.loaded: "Loaded {0} users and {1} groups from permissions.yml"
console.permission.saved: "Saved {0} users and {1} groups to YAML."
console.permission.loaded-users: "Loaded {0} users from YAML."
console.permission.loaded-groups: "Loaded {0} groups from YAML."
console.permission.refreshed-attachment: "Created/updated the permissions attachment for {0}"
console.permission.recalculated: "Recalculated permissions for {0}"
console.permission.updated-commands: "Updated command tree for {0}"
console.permission.error-refresh: "Failed to refresh attachment for {0}: {1}"
```

### console.storage.*
```yaml
console.storage.created-folder: "Created data folder: {0}"
console.storage.file-not-exist: "Permissions file does not exist yet."
console.storage.no-users: "No users section in permissions.yml"
console.storage.cannot-resolve: "Could not resolve user key: {0}"
console.storage.error-load: "Error loading from YAML: {0}"
console.storage.error-save: "Failed to save permissions YAML: {0}"
console.storage.creating-default: "Creating default group '{0}' as it did not exist."
```

### console.player.*
```yaml
console.player.auto-assigned: "Auto-assigned player {0} to the default group."
console.player.attachment-applied: "Applied permission attachment for {0}"
console.player.attachment-cleaned: "Cleaned up permission attachment for {0}"
console.player.attachment-error: "Failed to apply permissions to {0}: {1}"
console.player.attachment-cleanup-error: "Error cleaning up attachment for {0}: {1}"
```

### console.update.*
```yaml
console.update.checking: "Checking for updates..."
console.update.failed-http: "Update check failed: GitHub API returned HTTP {0}"
console.update.failed-parse: "Update check failed: unable to parse version from GitHub response."
console.update.failed: "Update check failed: {0}"
console.update.available: "New version available!"
console.update.latest: "Latest:   {0}"
console.update.current: "Current:  {0}"
console.update.download: "Download: {0}"
console.update.border: "{0}"
```

### console.plugin-enable.*
```yaml
console.plugin-enable.plugin-enabled: "Plugin enabled: {0} - gathering permissions and refreshing attachments."
```

### chat.help.*
```yaml
chat.help.header: "&e===== FoliaPerms Commands ====="
chat.help.editor: "&e/fperm editor &7- Open the permission editor GUI"
chat.help.reload: "&e/fperm reload &7- Reload permissions from file"
chat.help.gather: "&e/fperm gather &7- Gather permissions from all plugins"
chat.help.refresh: "&e/fperm refresh &7- Refresh all permission attachments"
chat.help.user-header: "&e--- User Commands ---"
chat.help.user-addperm: "&e/fperm user addperm <player> <perm>"
chat.help.user-removeperm: "&e/fperm user removeperm <player> <perm>"
chat.help.user-addgroup: "&e/fperm user addgroup <player> <group>"
chat.help.user-removegroup: "&e/fperm user removegroup <player> <group>"
chat.help.group-header: "&e--- Group Commands ---"
chat.help.group-create: "&e/fperm group create <name>"
chat.help.group-addperm: "&e/fperm group addperm <name> <perm>"
chat.help.group-adduser: "&e/fperm group adduser <name> <player>"
chat.help.group-removeuser: "&e/fperm group removeuser <name> <player>"
chat.help.inheritance-header: "&e--- Inheritance Commands ---"
chat.help.inheritance-set: "&e/fperm group setinherit <group> <parent> &7- Make group inherit from parent"
chat.help.inheritance-remove: "&e/fperm group removeinherit <group> <parent> &7- Remove inheritance"
chat.help.inheritance-show: "&e/fperm group inheritance <group> &7- Show inheritance chain"
chat.help.inheritance-perms: "&e/fperm group perms <group> &7- Show all effective permissions (incl. inherited)"
chat.help.other-header: "&e--- Other Commands ---"
chat.help.check: "&e/fperm check <player> <perm>"
chat.help.listperms: "&e/fperm listperms <player>"
```

### chat.error.*
```yaml
chat.error.no-permission: "&cYou don't have permission to use this command."
chat.error.internal-error: "&cInternal error while executing command."
chat.error.internal-error-user: "&cInternal error while processing user command."
chat.error.internal-error-group: "&cInternal error while processing group command."
chat.error.internal-error-list: "&cInternal error while listing permissions."
chat.error.service-unavailable: "&cInternal error: permission service unavailable."
chat.error.editor-only-player: "&cThe editor can only be opened by a player in-game."
chat.error.player-not-found: "&cCould not resolve player: {0}"
chat.error.uuid-not-found: "&cCould not determine UUID for player: {0}"
chat.error.group-not-found: "&cGroup not found: {0}"
chat.error.default-group-delete: "&cThe default group cannot be deleted."
chat.error.group-delete-failed: "&cCould not delete group '{0}'. It may not exist or is protected."
chat.error.inheritance-failed: "&cFailed to set inheritance. Check for circular dependencies."
chat.error.unknown-user-action: "&cUnknown user action: {0}"
chat.error.unknown-group-action: "&cUnknown group action: {0}"
chat.error.unknown-subcommand: "&eUnknown subcommand. Use /fperm help"
chat.error.gather-failed: "&cFailed to gather permissions: {0}"
chat.error.refresh-failed: "&cFailed to refresh attachments: {0}"
```

### chat.success.*
```yaml
chat.success.permission-added: "&aAdded permission {0} to {1}"
chat.success.permission-removed: "&aRemoved permission {0} from {1}"
chat.success.user-added-group: "&aAdded {0} to group {1}"
chat.success.user-removed-group: "&aRemoved {0} from group {1}"
chat.success.group-created: "&aGroup created: {0}"
chat.success.group-deleted: "&aGroup '{0}' deleted. Members re-assigned to default group."
chat.success.permission-added-group: "&aAdded permission {0} to group {1}"
chat.success.inheritance-set: "&aGroup '{0}' now inherits from '{1}'."
chat.success.inheritance-removed: "&aGroup '{0}' no longer inherits from '{1}'."
chat.success.reloaded: "&aPermissions reloaded."
chat.success.refreshed: "&aRefreshed permission attachments."
chat.success.gathered: "&aGathered {0} permissions from plugins."
```

### chat.info.*
```yaml
chat.info.welcome: "&eFoliaPerms active!"
chat.info.usage: "&eUsage: {0}"
chat.info.default-msg: "&eFoliaPerms: simple permission manager. /fperm help"
chat.info.check-has: "{0} &aHAS {1}"
chat.info.check-not: "{0} &cDOES NOT HAVE {1}"
chat.info.listperms-header: "&ePermissions for {0}:"
chat.info.listperms-none: "&e{0} has no registered permissions (or none gathered)."
chat.info.inheritance-header: "&eInheritance chain for '{0}':"
chat.info.inheritance-none: " &7- (no inheritance)"
chat.info.inheritance-entry: " &7- {0}"
chat.info.inheritance-direct: "&eDirect parents: {0}"
chat.info.perms-header: "&ePermissions for group '{0}':"
chat.info.perms-direct-header: " &bDirect permissions:"
chat.info.perms-direct-none: " &7- (no direct permissions)"
chat.info.perms-entry: " &7- {0}"
chat.info.perms-inherited-header: " &bInherited permissions (from parents):"
```

### gui.main.*
```yaml
gui.main.title: "&8FoliaPerms Editor"
gui.main.groups.name: "&aEdit Groups"
gui.main.groups.lore: "&7Browse and toggle permissions for groups."
gui.main.players.name: "&bEdit Players"
gui.main.players.lore: "&7Browse online players and toggle their permissions."
```

### gui.target-list.*
```yaml
gui.target-list.groups-title: "&8Groups"
gui.target-list.players-title: "&8Online Players"
gui.target-list.back.name: "&cBack"
gui.target-list.back.lore: "&7Return to main menu."
gui.target-list.group.lore-edit: "&7Click to edit permissions & inheritance."
gui.target-list.group.lore-inherits: "&7Inherits: {0}"
gui.target-list.group.lore-no-inheritance: "&7No inheritance set"
gui.target-list.player.lore-edit: "&7Click to edit permissions."
gui.target-list.player.lore-uuid: "&8UUID: {0}"
```

### gui.permission-editor.*
```yaml
gui.permission-editor.title: "&8Perms \u00BB {0}"
gui.permission-editor.granted: "&aGRANTED  \u2714  click to remove"
gui.permission-editor.not-granted: "&cNOT GRANTED  \u2718  click to add"
gui.permission-editor.previous.name: "&ePrevious Page"
gui.permission-editor.previous.lore: "&7Go to page {0}."
gui.permission-editor.page: "&fPage {0} &7/ {1}"
gui.permission-editor.page-lore: "&7{0} total permissions registered."
gui.permission-editor.back.name: "&cBack"
gui.permission-editor.back.lore: "&7Return to target list."
gui.permission-editor.next.name: "&eNext Page"
gui.permission-editor.next.lore: "&7Go to page {0}."
gui.permission-editor.inheritance.name: "&6Inheritance"
gui.permission-editor.inheritance.lore-current: "&7Currently inherits: {0}"
gui.permission-editor.inheritance.lore-none: "&7No inheritance configured"
gui.permission-editor.inheritance.lore-click: "&eClick to manage parent groups."
```

### gui.inheritance-editor.*
```yaml
gui.inheritance-editor.title: "&8Inheritance \u00BB {0}"
gui.inheritance-editor.parent: "&aPARENT  \u2714  click to remove"
gui.inheritance-editor.not-parent: "&cNOT PARENT  \u2718  click to add"
gui.inheritance-editor.previous.name: "&ePrevious Page"
gui.inheritance-editor.previous.lore: "&7Go to page {0}."
gui.inheritance-editor.page: "&fPage {0} &7/ {1}"
gui.inheritance-editor.page-lore-1: "&7Click groups to toggle inheritance."
gui.inheritance-editor.page-lore-2: "&8Current parents: {0}"
gui.inheritance-editor.back.name: "&cBack"
gui.inheritance-editor.back.lore: "&7Return to permission editor."
gui.inheritance-editor.next.name: "&eNext Page"
gui.inheritance-editor.next.lore: "&7Go to page {0}."
```

### chat.inheritance.* (GUI feedback messages)
```yaml
chat.inheritance.click-hint: "&7[&6FoliaPerms&7] &eClick a group to toggle inheritance."
chat.inheritance.added: "&7[&6FoliaPerms&7] &aAdded inheritance: &e{0} &7\u00AB &e{1}"
chat.inheritance.removed: "&7[&6FoliaPerms&7] &aRemoved inheritance: &e{0} &7\u00AB &e{1}"
chat.inheritance.circular: "&7[&6FoliaPerms&7] &cCannot add inheritance: would create a circular dependency."
```

### error.*
```yaml
error.prefix: "[FoliaPerms] "
error.context-separator: ": "
```

---

## 完整流程图

```mermaid
flowchart TD
    A[插件启动 onEnable] --> B[saveDefaultConfig 生成 config.yml]
    B --> C[读取 config.yml 中的 language 字段]
    C --> D[LocaleManager.load language]
    D --> E{语言文件存在?}
    E -->|是| F[从 plugins/FoliaPerms/locales/ 加载]
    E -->|否| G[从 resources/locales/ 复制默认文件]
    G --> F
    F --> H[缓存语言数据到 Map<String, String>]
    H --> I[各模块通过 FoliaPerms.tl 获取本地化文本]
    
    I --> J[FpermCommand - 聊天消息]
    I --> K[EditorGui - GUI 容器]
    I --> L[GuiListener - 交互反馈]
    I --> M[PlayerListener - 事件消息]
    I --> N[PermissionService - 日志]
    I --> O[YamlStorage - 日志]
    I --> P[ErrorHandler - 错误消息]
    I --> Q[UpdateChecker - 更新消息]
    
    R[/fperm reload] --> S[重新加载 config.yml]
    S --> T[读取新的 language 值]
    T --> D
```

---

## 关键设计决策

1. **颜色代码处理**: 语言文件中的文本使用 `&` 前缀颜色代码，通过 `getColoredString()` 自动调用 `ColorConverter.colorize()` 渲染。控制台输出使用 `getString()` 获取纯文本（通过 `ColorConverter.stripColor()` 去色）。

2. **回退机制**: 当指定的语言文件加载失败时，自动回退到 `en_us`，确保插件始终可用。

3. **占位符格式**: 使用 `{0}`、`{1}` 等数字索引占位符，通过 `java.text.MessageFormat` 或手动替换实现。

4. **文件复制**: 首次启动时将 `resources/locales/` 下的文件复制到数据目录，方便用户编辑。

5. **热重载**: `/fperm reload` 命令将同时重新加载语言配置。

6. **API 兼容性**: 保持现有 API 接口不变，不影响其他依赖 FoliaPermsAPI 的插件。
