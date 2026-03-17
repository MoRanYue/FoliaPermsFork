
# FoliaPerms API — Quick Reference

What it is
- Public interface: `kaiakk.foliaPerms.api.FoliaPermsAPI` (registered via Bukkit ServicesManager).

Quick runtime lookup
```java
RegisteredServiceProvider<FoliaPermsAPI> rsp = Bukkit.getServer()
	.getServicesManager().getRegistration(FoliaPermsAPI.class);
if (rsp != null) {
	FoliaPermsAPI api = rsp.getProvider();
}
```

Key methods
- `boolean hasPermission(Player p, String node)`
- `boolean hasPermission(UUID uuid, String node)`
- `Set<String> getPlayerGroups(Player p)` — returns an unmodifiable snapshot
- `String getPrimaryGroup(Player p)` — convenience, may be null

Compile vs runtime
- Compile-time: add `FoliaPerms` (or the API class) as a compile-only dependency so your code compiles.
- Runtime: place `FoliaPerms.jar` in `plugins/` so the service is registered on server start.

Ordering tip
- Add `softdepend: [FoliaPerms]` in your `plugin.yml` so FoliaPerms loads before you attempt lookup.

Threading & persistence
- Reads are safe from async threads (concurrent collections). Use `hasPermission(UUID, ...)` for async contexts.
- Writes by admin commands save asynchronously (background thread); the plugin does a sync save on shutdown.

Troubleshooting
- `rsp == null`: FoliaPerms missing, failed to enable, or lookup happened too early. Check server logs.

If you want mutation methods on the API (create/add/remove group/perm), tell me which methods you need.