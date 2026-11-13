# 万象 MCPFabric 测试环境

本项目使用 MCPFabric 0.10.0 的 NeoForge 26.2 模组和同版本 MCP 连接服务。文件来源、版本和模组 SHA512 见 `installation.json`。

## 使用

重启开发助手应用以载入项目 `.codex/config.toml`，然后从项目根目录启动：

```bash
bash tools/mcpfabric/launch-client.sh
```

这会运行 `runMcpClient`，测试目录是 `run-mcpfabric/`。只在该客户端新建测试世界；原有正式实例和存档不参与。MC 资源固定存放在 `~/.local/share/minecraft/assets`，原来的用户级及项目级 Gradle 资源路径均链接到这里。项目级安装隔离模组、连接配置与测试世界，通用游戏资源无需重复下载。

启动前会校验资源索引及每个文件的大小、SHA1，成功后跳过 `downloadAssets` 任务。资源缺失或损坏时会停止并列出缺失项，先从已有缓存恢复，必要时只补齐对应文件。项目缓存被清理后会自动恢复链接和启动所需的资源路径记录；固定资源目录不属于临时目录或清缓存范围。

连接检查：

```bash
node tools/mcpfabric/check-connection.mjs --tools-only
node tools/mcpfabric/check-connection.mjs
node tools/mcpfabric/check-connection.mjs --capture
```

三个命令分别检查 MCP 工具加载、实际游戏连接、进入世界后的截图。截图保存在 `run-mcpfabric/screenshots/mcp-connection.png`。调用 `$mcpfabric-client-acceptance` 可使用项目的操作指引。

## 环境与边界

- 需要 Java 25 和 Node.js ≥22.16。服务依赖安装在本目录的 `node_modules/`，版本由 `package-lock.json` 锁定；恢复依赖使用 `npm ci --ignore-scripts`。
- 项目常规 NeoForge 版本仍为 26.2.0.59。测试启动脚本单次指定 26.2.0.88，以满足 MCPFabric JAR 的最低版本要求，并启用二进制开发模式，避开该版本的 `HolderSet.contents()` 源码重编译错误。
- 项目配置只连接 `run-mcpfabric/config/mcpfabric.config.json` 指定的本机桥接，不使用用户目录的自动发现。令牌只保存在忽略的测试目录，不输出到日志或跟踪文件。
- 界面操作已开启；直接方块编辑、上游自主任务和记忆运行时关闭。命令、客户端输入、画面观察和批处理脚本可用。
- 测试启动配置额外编译 `client-adapter/java/`，将万象专用输入与连拍方法注册到 MCPFabric 原有的认证桥接。普通构建和发布 JAR 不包含这份适配。

项目 skill 是本次为万象编写的指引，上游仓库没有附带可直接安装的 `SKILL.md`。模组及连接服务来自 [MCPFabric 上游](https://github.com/denfry/mcpfabric)，NeoForge 模组来自其 [Modrinth 发布](https://modrinth.com/mod/mcpfabric/version/719fUXc7)。

## 本次验证（2026-11-13）

已启动 MC 26.2 / NeoForge 26.2.0.88，加载万象与 MCPFabric 0.10.0；MCP 握手列出 49 个工具，实际桥接、界面读取、界面按钮点击和批处理运行成功。用户点击创建世界后，已通过 MCP 捕获并实际查看 `run-mcpfabric/screenshots/mcp-connection.png`。安装检查记录位于 `run-mcpfabric/installation-smoke.json`。

原版创建世界界面未开放给上游 UI 工具，需要首次手动完成；以后可通过 `list_worlds` / `join_world` 重新进入这个测试世界。本次未验证万象技能按键与完整特效流程。误下载的重复资源已清理，共享资源的 5,059 个文件已逐项校验 SHA1。

随后已将资源原件移到用户长期目录，旧路径保留链接，迁移后 5,059 个文件再次校验通过。离线启动预演成功，任务列表不包含 `downloadAssets`；临时环境检查确认项目缓存链接与启动路径记录可重建，资源损坏时会阻止启动。

## 项目专用工具

项目服务代理保留上游工具，并增加下列五个工具，共 54 个。修改服务后重启 MCP 连接；修改 Java 适配后需重新加载测试客户端。

| 工具 | 用途 |
|---|---|
| `myriad_state` | 读取角色、技能等级、法力、冷却、坐标和实际输入事件计数 |
| `myriad_input` | 经 Minecraft 原生处理函数发送技能按键、鼠标或 GUI 坐标点击；可同时开始特效连拍 |
| `myriad_release_inputs` | 释放适配持有的输入，恢复临时借用的按键绑定 |
| `myriad_capture` | 开始有限数量的 PNG 连拍 |
| `myriad_capture_status` | 读取连拍进度、每帧状态和图片路径 |

示例（在已进入的测试世界中）：

```bash
node tools/mcpfabric/call-tool.mjs myriad_state
node tools/mcpfabric/call-tool.mjs myriad_input '{"kind":"key","key":"key.myriad.skill_e","captureFrames":30}'
node tools/mcpfabric/call-tool.mjs myriad_capture_status '{"captureId":"上一步返回的 captureId"}'
```

按键以实际注册的 `KeyMapping` 名称指定，例如 `key.myriad.info`、`key.myriad.skill_e`；原始键使用 `key.keyboard.escape`。指定映射时临时借用空闲键，以避免未绑定或与原版移动键冲突，释放后恢复，不保存到按键设置。`holdTicks` 范围 1–20；停止控制、切世界或打开界面时也会释放。鼠标 `button` 为 0 左、1 右、2 中；GUI 点击还需提供界面缩放坐标 `x`、`y`。

`captureFrames` 范围 1–40，`intervalTicks` 范围 1–10。帧保存在 `run-mcpfabric/screenshots/myriad/<captureId>/`，轮询状态时写入 `capture.json`。GPU 读回会影响实际间隔，以每帧 `clientTick` 为准。

已通过朔“一闪”的第一、第三人称实测，各记录 30 帧；正常界面学习、技能键释放、右键回鞘、法力扣除、冷却和位移均观察到。详细证据与范围见 [验证记录](../../docs/verification/mcpfabric-shuo-20261113.md)。
