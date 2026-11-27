# 万象 Fabric 26.3 客户端验证工具

当前目标为 MC 26.3 / Fabric Loader 0.20.1 / Fabric API 0.166.2+26.3、Java 25。MCPFabric 使用官方 0.10.0+26.3 Fabric JAR，大小与 SHA512 固定在 `installation.json`。先核对已有文件；仅缺少这个测试模组时，从其中的官方 URL 下载并核验，不重下共享游戏资源。

`client-adapter/` 是独立 Fabric 客户端测试模组，复用上游认证桥接及能力开关。通过 `KeyboardHandler.keyPress`、`MouseHandler.onButton` 发送真实 SDL 输入；技能、GUI 加点等仍由生产包和服务端校验。观察计数由测试 mixin 在真实处理函数入口采集。临时绑定使用未占用的 SDL F13–F24，1–20 tick 后恢复；鼠标 API 保持 0 左/1 右/2 中，内部映射到 SDL 常量。切世界、打开界面或玩家 STOP 会释放持有输入。

先准备普通生产构建，再启动两个相互独立的客户端：

```bash
bash tools/mcpfabric/launch-client.sh pilot /path/to/mcpfabric-0.10.0+26.3.jar
bash tools/mcpfabric/launch-client.sh observer /path/to/mcpfabric-0.10.0+26.3.jar
```

分别运行 `runClient` / `runObserver`，目录 `run-fabric-client/` / `run-fabric-observer/`，后者用户名为 `MyriadObserver`。桥接固定回环地址，端口为 25599 / 25600，令牌由各测试目录独立保存；关闭全局发现。第一次加入测试服由用户设置服务器条目，随后可通过 `list_worlds` / `join_world` 重连；只使用明确指定的新测试世界。不要用旧 `run-mcpfabric/` 存档或正式实例。

启动入口先完整核验 `~/.local/share/minecraft/assets` 的索引 34、5149 项资源及 Loom 别名 `26.3-34`，成功后才跳过 `downloadAssets`。Loom 缓存使用指向共享库的链接；存在不同目录或不完整资源时停止并报告，不清空缓存。

适配只单独输出到 `build/mcpfabric-adapter/myriad-acceptance-fabric26.3.jar`，运行时才复制到对应测试 `mods/`；普通 `build/libs/myriad-*.jar` 不包含适配、MCPFabric、令牌、fixture 或世界。可单独重建：

```bash
bash tools/mcpfabric/build-fabric-adapter.sh /path/to/mcpfabric-0.10.0+26.3.jar
```

Node 连接服务继续使用锁定的 0.10.0 协议。依赖已有则复用；没有时在 `tools/mcpfabric/` 运行 `npm ci --ignore-scripts`。默认连接 pilot，observer 通过环境变量选择：

```bash
node tools/mcpfabric/check-connection.mjs
node tools/mcpfabric/call-tool.mjs myriad_state
MYRIAD_MCP_RUN_DIR=run-fabric-observer node tools/mcpfabric/call-tool.mjs myriad_state
node tools/mcpfabric/call-tool.mjs myriad_input '{"kind":"key","key":"key.myriad.skill_e"}'
node tools/mcpfabric/call-tool.mjs myriad_release_inputs
```

先读 `get_status` / `myriad_state` 并确认 `aiPaused=false`；操作结束读回 `heldInputs=0`。独立适配还提供只读 RPC `myriad.presentation`，记录朔动作与能力 VFX 的生产接收时点、声音提交及返回值；这些记录不作画面或听感评价。输入回调计数只证明输入进入处理函数；学习等级、法力、冷却、位置、合法命中和服务端结果必须另行读回。截图或连续帧仅按明确指令采集，全部画面、音效听感、手感、流畅性和平衡结论由人验收。

也可不启动 Node 服务，直接调用同一认证桥接（默认 pilot）：

```bash
python tools/mcpfabric/call-rpc.py info.status
python tools/mcpfabric/call-rpc.py myriad.presentation
python tools/mcpfabric/call-rpc.py myriad.state --run-dir run-fabric-observer
```

`--record /path/evidence.jsonl` 会追加时间、实例、参数和响应，凭据不进入记录。


## 第一人称固定机位截图

在仓库根目录运行以下命令。每次都把当前工作树编译为新的 JAR，启动一个独立的本地 Fabric 单人客户端，由游戏实际渲染十个角色并拍摄。朔另拍出鞘，夜影另拍午夜待机。

```sh
python tools/mcpfabric/capture-firstperson.py
python tools/mcpfabric/capture-firstperson.py --roles yingxiao,fenjue,shuo --motion
python tools/mcpfabric/capture-firstperson.py --roles fenjue --reference-run "outputs/firstperson/上一轮目录名"
```

- 默认输出在 `outputs/firstperson/<时间戳>/`。轮次目录的 `overview-*.jpg` 按基线顺序汇总新版。每个角色目录含原始 PNG、实机帧状态和与 #68 固定基线的左右对照 JPG。夜影午夜图单列。要改变输出位置，用 `--output <父目录>`；每轮各有目录。
- 使用 `--reference-run` 可将指定的已完成轮次作为第二个基线，同时保留 #68 的对照。每个静帧目录额外生成 `previous-comparison.jpg`，包括所选角色对应的午夜或出鞘图。脚本读取并核对两个轮次的角色、取景、FOV、光照、皮肤与实际像素尺寸，将参考轮次的清单与 JAR 摘要及逐图结果写入本轮清单。
- `--motion` 同时采集 26 帧原生普攻与收势。按帧的 `clientTick` 查看实际采样时刻。已安装 ffmpeg 时，会按此时间轴生成各角色的 `motion.mp4`。拔刀需要朔打中本人的训练假人，脚本会创建、移除假人并核对已同步的出鞘状态。
- 运行需要桌面图形环境、Python 3.11+、JDK 25，以及上文的完整共享游戏资源、离线 Gradle 缓存和已验证的 MCPFabric 测试模组。测试模组默认复用 pilot 的副本，亦可传 `--bridge-jar <文件>`。脚本使用本轮 JAR 快照、单独的回环端口和临时测试目录，可与既有 pilot、observer 实例并存。
- 截图基线采用仓库内 `docs/verification/assets/hero-motion/closer-grip-{1,2}.jpg`。新画面固定训练场坐标 `(0.5, 65, 0.5)`、水平朝南、70° FOV、Kai 皮肤、右主手和空副手。脚本逐帧核对这些条件以及实际武器、皮肤和光照，将游戏版本、提交和被测 JAR 的 SHA256 记入 `manifest.json`。失败时已采材料和 `client.log` 保留在本轮目录。
- 临时世界重用仓库 26.3 合成平地 fixture 的两个小型启动文件及测试地图配置，进场后由万象生成训练场。启动文件在 `firstperson/seed/`，只含世界头和固定生成设置。脚本退出时释放输入并关闭自己启动的游戏。

## 26.2 历史记录

以下为旧 NeoForge 环境的历史事实，不能作为 Fabric 26.3 的验证结果；其中旧命令和目录不得用于当前启动。

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

`myriad_state` 与截图、连拍状态遵守 `enableVision`；界面已打开时，键盘和鼠标输入都需要 `enableUiControl`，并继续遵守玩家 STOP。关闭观察能力后，成功输入只返回确认信息与 `heldInputs`，不会附带完整状态，也不会在输入已触发后再报观察权限错误。`myriad_release_inputs` 始终允许释放本适配持有的输入，只返回 `heldInputs`。

已通过朔“一闪”的第一、第三人称实测，各记录 30 帧；正常界面学习、技能键释放、右键回鞘、法力扣除、冷却和位移均观察到。详细证据与范围见 [验证记录](../../docs/verification/mcpfabric-shuo-20261113.md)。
