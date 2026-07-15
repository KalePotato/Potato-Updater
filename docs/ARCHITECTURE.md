# Potato Updater architecture

## Runtime lifecycle

```text
The game launcher starts the Minecraft JVM
  -> -javaagent:Potato_Seed.jar
  -> PotatoSeedAgent.premain()
  -> resolve gameCoreDir
  -> SeedBootstrapRunner
       -> load A_Potato_Seed/seed_config.json
       -> fetch seed.json
       -> update A_Potato_Updater/Potato_Updater.jar when required
       -> launch updater with --prelaunch and wait
  -> PotatoUpdater
       -> clean stale temporary transactions
       -> check Seed self-update
       -> fetch storage.json and Potato_Pack/list.json
       -> operationTime fast path or physical SHA-256 scan
       -> user review
       -> TaskExecutor download and apply
       -> commit potato_version.json
  -> updater exits successfully
  -> premain returns
  -> Minecraft/Fabric initialization continues
```

The Java Agent is synchronous by design. In prelaunch mode, Minecraft has not yet loaded Fabric mods, so the updater can replace mod files before they become locked or parsed.

## Modules

### `seed-agent`

Owns `Premain-Class` and the game-core bootstrap boundary. It resolves an explicit `gameCoreDir` agent argument or derives the core from the physical location of `Potato_Seed.jar`. The final Seed artifact includes the `seed` module and its runtime dependencies.

### `seed`

Owns local Seed configuration/state, Updater metadata retrieval, safe temporary download, optional SHA-256 verification, and blocking Updater process handoff.

### `updater`

Owns:

- two-root path resolution;
- remote routing and manifest retrieval;
- local state and hash cache;
- physical file comparison;
- user review UI;
- bounded parallel download with retry, stall detection, disk-space checks, and SHA-256 verification;
- direct regular-file deployment;
- staged mods deployment with backup and rollback;
- resource-pack selection in `options.txt`;
- deferred helper modes for locked Seed/mod files.

### `seed-installer`

A Windows Forms/.NET Framework installer. It downloads `Potato_Seed.jar`, removes legacy Seed placements, writes `-javaagent:Potato_Seed.jar` into supported launcher settings and compatible version JSON, invalidates generated launcher state, and optionally restarts the launcher.

## Managed paths

Manifest paths have exactly one virtual root:

- `game_core_dir`: the isolated version directory or `.minecraft` itself;
- `launcher_dir`: the directory above `.minecraft`;
- `luncher_dir`: read-only compatibility alias for historical manifests.

Resolution rejects unknown roots, absolute child paths, dot segments, lexical root escape, and escape through existing symbolic links or directory junctions.

## Update state

`A_Potato_Updater/potato_version.json` stores the last successfully processed `operationTime` and a failure bit. Matching `operationTime` plus `lastUpdateFailed=false` enables the fast path. A forced rescan removes version and hash-cache state.

The fast path is an optimization, not an integrity scan. Physical modification while the operation time remains unchanged is detected only after forced rescan or a later manifest operation.

## Deployment semantics

All downloads are prepared and verified before apply begins. Regular files are replaced sequentially and are retried on transient lock failures. Mods are staged separately; direct prelaunch apply backs up affected paths and rolls them back on failure. State is committed only after deployment and resource-pack finalization succeed.

This is retry-safe state progression, but it is not a general filesystem transaction for regular files. A failure after some regular replacements can leave a partially applied filesystem while keeping local state behind so that the next run retries.

## Remote data model

- `seed.json`: Updater URL, channel enabled flag, Updater version, optional hash.
- `seed_version.json`: Seed version and Seed download URL.
- `storage.json`: object-storage route.
- `Potato_Pack/list.json`: `operationTime`, file entries, delete zone.
- `optional_resource_packs.json`: selectable pack names.
- `mods_control.json`: optional mod paths and UI metadata.
- `info.json`: user-facing update announcement.

Examples under `config/examples/` document formats only. They are not deployment configuration.
