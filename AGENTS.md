# Repository working rules

1. Treat this Git repository as the only source-code authority. Runtime clients, test worlds, logs, downloaded mods, resource packs, and release binaries stay outside Git.
2. Use JDK 17. Run `gradlew.bat clean test` before handing off Java changes.
3. Preserve both `game_core_dir` and `launcher_dir` support. Do not remove `launcher_dir` to simplify path handling.
4. Every path derived from remote or persisted metadata must be contained within its declared managed root. Never weaken traversal, absolute-path, symlink, or junction checks.
5. Do not deploy to production endpoints or alter remote version JSON unless the user explicitly requests a release/deployment operation.
6. Keep generated output in `build/` or `dist/`; never commit JAR, EXE, logs, diagnostics, or third-party Minecraft assets.
7. Use the external test environment selected by `POTATO_TEST_CORE`; never hardcode a personal PCL or drive path.
8. Issues and Pull Requests are welcome. Apply the same build, test, security, and repository-hygiene standards to external contributions as to maintainer changes.
