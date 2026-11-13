---
name: mcpfabric-client-acceptance
description: 在万象项目中通过 MCPFabric 操作真实 Minecraft 客户端，检查 HUD、界面、模型、技能和粒子特效；也用于启动测试客户端、检查连接和收集画面证据。
---

# 万象客户端视觉验收

## 连接与环境

本项目的连接配置在 `.codex/config.toml`；启动入口、依赖版本和文件摘要见 `tools/mcpfabric/`。这些路径均相对项目根目录。先检查实际文件，不把安装记录当成客户端正在运行。

- `bash tools/mcpfabric/launch-client.sh` 启动独立客户端，使用 `run-mcpfabric/`、MC 26.2 和 NeoForge 26.2.0.88。普通项目仍使用 `gradle.properties` 中的版本。客户端已运行时复用连接，不重复启动。
- `node tools/mcpfabric/check-connection.mjs --tools-only` 验证 MCP 握手及工具清单，不需要游戏运行。
- `node tools/mcpfabric/check-connection.mjs` 检查实际游戏桥接；进入测试世界后加 `--capture` 保存一张真实截图。
- 新增 MCP 配置在重启客户端应用后加载；skill 可在后续轮次发现。工具未出现在当前对话时，可用项目内 MCP SDK 调用连接服务，不改全局配置。

MC 共享资源固定在 `~/.local/share/minecraft/assets`。启动脚本先逐项校验资源并跳过下载任务；资源缺失时先恢复已有文件，再按差异补齐。固定资源库不属于项目缓存清理范围。

仅操作 `run-mcpfabric/` 内的新建或明确指定的测试世界。正式世界的迁移、复制或覆盖遵守根目录 `AGENTS.md`；启动工具不授权改变正式实例。

## 执行视觉检查

1. 调用 `get_status`、`list_capabilities`，确认客户端侧、MC/模组版本和功能开关。使用 `get_screen` / `get_ui` 确认当前界面，进入世界后用 `get_self` 确认位置与状态。
2. 固定测试角色、技能分支/等级、目标、距离、光照与视角。需要的 `/myriad` 命令从当前源码或游戏帮助中核对；通过 `run_command` 准备场景并读回结果。
3. 按用户要检查的实际操作触发效果，再用 `screenshot` 获取 PNG 并真正查看。界面使用 `get_ui`、`click_widget`、`set_text_field`；转向和移动使用 `look`、`look_at`、`set_movement`。
4. 记录画面问题对应的场景、第一/第三人称、触发步骤和截图。修改后在相同场景重复检查。短暂特效需要连续帧或重复触发，单张截图只能证明该帧看到的表现。
5. 结束移动时调用 `stop_movement`，释放持续使用物品的状态。保留测试窗口或按用户要求退出；不关闭其他 Minecraft 实例。

## 万象专用工具

项目服务在上游工具之外增加 `myriad_state`、`myriad_input`、`myriad_release_inputs`、`myriad_capture` 和 `myriad_capture_status`。先读 `myriad_state` 确认角色、等级、法力、已学技能和冷却。

- 技能键示例：`myriad_input({kind:"key", key:"key.myriad.skill_e", captureFrames:30})`。它经原生键盘回调触发 NeoForge 事件和既有服务端施法校验，临时借用空闲键后自动恢复，适用于未绑定或与原版移动键冲突的映射。`holdTicks` 为 1–20，默认 1。
- 普通鼠标：`myriad_input({kind:"mouse", button:1})` 发送右键，可用于朔的回鞘；0 为左键、2 为中键。GUI 操作还需提供缩放后的 `x`、`y`，使用原生鼠标路径。自绘界面可能没有 `get_ui` 控件，先看截图、核对界面尺寸，再点击；不能按上一帧的旧坐标盲点。
- 原始键使用 `key.keyboard.escape` 等名称；切换视角使用映射 `key.togglePerspective`。读取回传状态确认实际视角，不能把按下瞬间的旧快照当成最终结果。
- `myriad_capture` 可单独开始连拍，也可由输入的 `captureFrames` 同步启动。范围 1–40 帧、`intervalTicks` 1–10。用返回的 `captureId` 轮询 `myriad_capture_status`，完成后实际查看 PNG；以每帧 `clientTick` 判断实际采样时序。
- 检查技能学习、法力、冷却、位移和命中结果；输入回传的事件计数只说明进入了回调，不能单独证明服务端接受施法。参数回复可能早于同步包，需读回状态或逐帧证据。
- 操作结束调用 `myriad_release_inputs` 并确认 `heldInputs=0`。界面打开、切世界和玩家 STOP 也会触发释放；继续尊重上游停止控制状态。

适配仅编译到专用测试启动配置，普通发布 JAR 不包含它。`unknown_method` 说明当前游戏未加载适配，按项目约定取得测试客户端重启授权后再加载，不绕过服务端校验。

MCP 工具尚未载入当前对话时，可调用同一服务的项目命令行入口：

```bash
node tools/mcpfabric/call-tool.mjs myriad_state
node tools/mcpfabric/call-tool.mjs myriad_input '{"kind":"key","key":"key.myriad.skill_e","captureFrames":30}'
```

已验证朔“一闪”的第一/第三人称输入与连拍，见 `docs/verification/mcpfabric-shuo-20261113.md`。其他角色、地面选点确认/取消和长按组合仍须按场景实际验证，不能沿用这次结论。测试世界首次创建界面需手动完成；现有测试世界可用 `list_worlds` / `join_world` 重新进入。

## 图像与证据

`screenshot` 需要已经进入世界，标题界面无玩家时会返回 `no_client_player`。图片返回后通过当前工具的图像输出展示，或保存到 `run-mcpfabric/screenshots/` 并用图像查看工具检查；避免输出 Base64 文本。

区分 MCP 服务可用、桥接连接成功、操作触发成功、画面已检查和游戏机制验证通过。截图不能证明伤害数值、碰撞、多人同步或完整动画时序。

## 项目隔离

连接入口从 `run-mcpfabric/config/mcpfabric.config.json` 读取认证信息，限定本机回环地址。凭据、模组 JAR、缓存、日志、截图和测试世界均在忽略目录；保留该边界，令牌不写入回复或跟踪文件。全局自动发现已关闭，避免连接到另一个实例。

来源：上游 [MCPFabric](https://github.com/denfry/mcpfabric)；本 skill 为万象项目编写，并非上游附带内容。上游游戏内 `save_skill` 是另一套功能，本项目未启用其自主任务/记忆运行时。
