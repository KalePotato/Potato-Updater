# Potato

Potato 是一套面向 Minecraft/PCL 客户端的启动前资源同步系统。它通过 Java Agent 在 Minecraft 和 Fabric 初始化之前运行，检查并启动独立更新器，在游戏加载前完成受管模组、资源包、光影包和启动器资源的同步。

> 本仓库仅用于公开源代码和项目透明度展示。项目不接受 Pull Request、外部贡献、功能请求或 GitHub 技术支持。

## 架构概览

```text
PCL / Java
  -> -javaagent:Potato_Seed.jar
  -> PotatoSeedAgent.premain()
  -> SeedBootstrapRunner
  -> Potato_Updater.jar --prelaunch
  -> 远端路由与清单
  -> SHA-256 扫描、用户审阅、临时下载与部署
  -> Minecraft 继续启动
```

项目由四个产品模块组成：

- `seed-agent`：Java Agent 入口，产出 `Potato_Seed.jar`。
- `seed`：Seed 配置、Updater 获取和进程交接逻辑，由 `seed-agent` 打入最终 fat JAR。
- `updater`：Swing 更新器、清单扫描、下载、事务部署和资源包选项管理。
- `seed-installer`：Windows/PCL 安装器，负责放置 Seed 并写入启动参数。

管理员打包脚本位于 `admin-tool/`。`config/examples/` 只保存公开格式样例，不是生产发布源。

## 环境要求

- Windows 10/11：完整客户端、Installer 和 PCL 集成测试的主要平台。
- JDK 17：编译和运行 Gradle 所必需。
- Gradle Wrapper：仓库已包含，无需单独安装 Gradle。
- .NET Framework 4.x C# 编译器：构建 Windows Installer 时需要。

## 构建与测试

```powershell
.\gradlew.bat clean test
.\tools\build-release.ps1
```

发布构建输出到忽略的 `dist/`，不会进入 Git。详细流程见 [开发指南](docs/DEVELOPMENT.md)、[测试指南](docs/TESTING.md)和[发布指南](docs/RELEASE.md)。

## 目录边界

远端清单只允许使用两种虚拟根：

- `game_core_dir/...`
- `launcher_dir/...`

旧拼写 `luncher_dir/...` 仅为历史清单兼容保留。路径解析器会拒绝绝对路径、`.`/`..` 路径段、根目录逃逸及经现有符号链接或目录联接逃逸。

## 仓库政策

- 不提交 JAR、EXE、日志、诊断现场、测试客户端或第三方游戏资源。
- 不在 GitHub Actions 中自动部署生产对象存储。
- 正式发布必须由 Git 标签、GitHub Release 资产和 SHA-256 清单共同标识。
- 安全问题处理方式见 [SECURITY.md](SECURITY.md)。

## 许可

本仓库当前不是开源许可项目。除 GitHub 平台展示所需权利外，未授予复制、修改、分发或再许可权利。详见 [LICENSE](LICENSE)。
