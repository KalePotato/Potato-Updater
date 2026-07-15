# Potato Updater

## 中文

Potato Updater 是一个 Minecraft 启动前更新工具。它通过 Java Agent 在游戏加载前运行更新器，根据远端清单检查并同步模组、资源包、光影包和启动器文件。

项目尚未做好分发准备，暂时不提供具体的操作说明。

项目包含：

- `seed-agent` / `seed`：启动 Java Agent 并检查更新器。
- `updater`：扫描、下载、校验和更新文件。
- `seed-installer`：安装 Seed 并配置启动参数。

构建需要 JDK 17 和 Windows：

```powershell
.\tools\build-release.ps1
```

测试和发布流程见 [`docs/`](docs/)。

## English

Potato Updater is a pre-launch updater for Minecraft. It runs through a Java Agent before the game loads, then checks a remote manifest and synchronizes managed mods, resource packs, shader packs, and launcher files.

The project is not ready for distribution, so detailed usage instructions are not currently provided.

The project contains:

- `seed-agent` / `seed`: starts the Java Agent and checks the updater.
- `updater`: scans, downloads, verifies, and updates files.
- `seed-installer`: installs Seed and configures the launch arguments.

Building requires JDK 17 and Windows:

```powershell
.\tools\build-release.ps1
```

See [`docs/`](docs/) for testing and release notes.

## License

[MIT](LICENSE)
