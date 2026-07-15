# Development guide

## Source authority

Git commits are the source authority. A local clone is a disposable working copy; generated binaries and runtime clients are not source records.

Recommended solo workflow:

```powershell
git switch main
git pull --ff-only
git switch -c work/short-description

# edit and test
.\gradlew.bat clean test

git add -p
git diff --cached
git commit -m "Describe the behavior change"
git switch main
git merge --ff-only work/short-description
git push origin main
```

If a branch cannot be fast-forwarded, inspect the divergence rather than force-pushing `main`.

## Java

The build requires JDK 17 or newer to run and uses a Java 17 toolchain for compilation. Confirm the active runtime:

```powershell
java -version
.\gradlew.bat --version
```

Use the Gradle Wrapper committed to the repository. Do not rely on a machine-global Gradle installation.

## Build outputs

Module archives remain under standard Gradle directories:

```text
seed/build/libs/Potato_Seed_Internal.jar
seed-agent/build/libs/Potato_Seed.jar
updater/build/libs/Potato_Updater.jar
seed-installer/build/Potato_Seed_Installer.exe
```

`tools/build-release.ps1` collects public products in ignored `dist/` and generates `SHA256SUMS.txt`.

## Configuration

Never add production credentials or private endpoints to a tracked file. Public JSON shapes belong in `config/examples/`. Machine-specific values belong in environment variables or ignored `*.local.*` files.

The tracked endpoint profile uses the reserved `https://example.invalid` host. Private builds can set `POTATO_SYNC_BASE_URL` or copy `config/endpoints.example.properties` to the ignored `config/endpoints.local.properties`. The environment variable takes precedence. Java resources and the Windows installer receive the selected value during the build, so private endpoints never need to appear in tracked source.

The production deployment channel is not part of normal build/test commands.

## Design invariants

- The Java Agent must complete before Minecraft/Fabric initialization continues.
- `launcher_dir` is intentional and must remain supported.
- Metadata-derived paths must not leave their declared root.
- Download verification happens before apply.
- Local success state is committed only after all required finalization succeeds.
- Window-close paths must not leave avoidable child Java processes behind.
- Optional mods are controlled by `mods_control.json`, not by filesystem guessing.

## Repository hygiene

Before committing:

```powershell
git status --short
git diff --check
git ls-files | Select-String -Pattern '\.(jar|exe|class|log|zip)$'
```

No output from the last command is expected except `gradle/wrapper/gradle-wrapper.jar` when checking JARs separately.
