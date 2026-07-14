# Contributing

Issues, discussions, and pull requests are welcome.

Before submitting a change:

1. Use JDK 17 and the committed Gradle Wrapper.
2. Run `gradlew.bat clean test` on Windows or `./gradlew clean test` on another supported development system.
3. Add or update tests when behavior changes.
4. Keep generated binaries, runtime clients, logs, credentials, and third-party Minecraft assets out of Git.
5. Preserve the `game_core_dir` and `launcher_dir` path-containment guarantees.

Pull requests should briefly explain the problem, the chosen solution, and how the change was verified. Small, focused changes are easier to review, but early proposals and exploratory issues are also welcome.

By contributing, you agree that your contribution is licensed under the repository's MIT License.
