# Contributing

Before submitting a change:

1. Use JDK 17 and the committed Gradle Wrapper.
2. Run `gradlew.bat clean test` on Windows or `./gradlew clean test` on another supported development system.
3. Add or update tests when behavior changes.
4. Keep generated binaries, runtime clients, logs, credentials, and third-party Minecraft assets out of Git.
5. Preserve the `game_core_dir` and `launcher_dir` path-containment guarantees.

Changes should briefly explain the problem, the chosen solution, and how the result was verified.

By contributing, you agree that your contribution is licensed under the repository's MIT License.
