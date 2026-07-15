# Testing guide

Testing is deliberately split into three layers.

## 1. Unit and component tests

Run on every local handoff and every GitHub push:

```powershell
.\gradlew.bat clean test
.\tools\build-release.ps1
.\tools\smoke-test-products.ps1 -SkipBuild
```

Current tests cover Agent path derivation, endpoint injection, managed-root detection, path-contract rejection, DiffEngine behavior, and GUI path display helpers. The product smoke test loads Seed as a real Java Agent, starts Updater in its offline smoke mode, and starts the Installer in its non-installing smoke mode. New behavior in download, state, resource-pack, and helper flows should add tests rather than relying only on a production client.

## 2. External integration client

The Minecraft launcher test installation must live outside the repository. Select it for the current shell:

```powershell
$env:POTATO_TEST_CORE = 'C:\Potato-Test\persistent\.minecraft\versions\dev'
.\tools\deploy-test.ps1
```

The deployment script builds from the current working tree, copies only product binaries, and preserves runtime configuration and worlds. It refuses unsupported directory layouts.

Maintain two environments:

- `persistent`: long-lived daily compatibility environment;
- `clean`: disposable installation rebuilt for release candidates.

Do not commit either environment, its logs, mods, resource packs, caches, launcher files, or worlds.

## 3. Release-candidate verification

Test the exact assets produced from a Git tag, not a separately rebuilt local copy:

1. Tag CI creates a draft GitHub Release.
2. Download its JAR/EXE assets into the clean test environment.
3. Verify `SHA256SUMS.txt`.
4. Exercise first install, normal fast path, forced scan, mod replacement, deletion, resource-pack selection, failed-network recovery, close/abort, and second launch.
5. Exercise a production-scale manifest or representative local mirror outside Git.
6. Publish the draft only after the exact assets pass.

## Failure evidence

Keep raw diagnostics outside Git. If a regression fixture is needed, reduce and sanitize it before adding a small text/JSON fixture under a test resource directory. Remove usernames, absolute paths, process command lines, launcher tokens, and unrelated client data.
