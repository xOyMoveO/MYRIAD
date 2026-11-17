---
name: mcpfabric-client-acceptance
description: 在万象 Fabric 项目中操作真实 Minecraft 客户端，验证原生输入、权威状态和联机同步，采集截图、音频触发与性能材料供人验收。
---

# 万象 Fabric 客户端客观验证与人工移交

先读根目录 `AGENTS.md`、`docs/agents/domain.md` 及 `tools/mcpfabric/README.md`。以当前 `gradle.properties`、启动配置和实际连接状态确认版本；目标为 Fabric / Minecraft 26.3，旧26.2记录只作历史参考。

## 准备与连接

- 先复用已运行且版本匹配的测试客户端。需新启动时，`bash tools/mcpfabric/launch-client.sh pilot /path/to/mcpfabric-0.10.0+26.3.jar` 与 `observer` 分别启动 `runClient`、`runObserver`，使用 `run-fabric-client/`、`run-fabric-observer/`。第三方测试模组的官方来源和SHA512在 `tools/mcpfabric/installation.json`；不以旧 `run-mcpfabric/` 启动正式验证。
- 启动器先核验共享资源 `~/.local/share/minecraft/assets`，仅在完整性通过后跳过下载。保留共享库，只补缺失、损坏或新版新增对象；先查缓存路径、链接、权限和可恢复副本。
- `node tools/mcpfabric/check-connection.mjs --tools-only` 查服务握手，去掉 `--tools-only` 查实际游戏桥接。`get_status`、`list_capabilities` 和 `myriad_state` 确认实例、版本、世界及 `aiPaused=false`；暂停时停止输入。
- pilot/observer桥接分别为回环25599/25600；凭据在各自忽略的测试目录，关闭全局发现。`MYRIAD_MCP_RUN_DIR=run-fabric-observer` 选择观察者；先核对实际目录和端口，避免连到另一实例。

仅操作新建、可复现的合成测试世界/fixture。正式/原始地图及其备份实图副本、原图3v3/5v5整局全部由人操作验收；提供操作清单和可选证据，不索取、读取、导入、运行或代验原图。当前fixture和人工步骤见 `docs/fabric/arena-fixture.md`。

## 原生输入与权威结果

1. 读回界面、窗口/GUI尺寸、玩家位置、角色、已学技能和冷却。固定角色、分支/等级、目标、距离、光照与视角；场景准备与正式输入分别记录。
2. `myriad_input({kind:"key",key:"key.myriad.skill_e"})` 经真实 `KeyboardHandler.keyPress`；`holdTicks` 为1–20，临时借用空闲SDL键后自动恢复。`kind:"mouse"` 的0/1/2为左/右/中键；GUI同时传缩放后的 `x`、`y`。原始键可用 `key.keyboard.escape`，视角映射为 `key.togglePerspective`。以当前适配能力列表为准，不把不存在的方法当作已执行。
3. 自绘界面没有控件树时，依据实际布局和当前尺寸确定坐标；单击/双击、按钮、滚动、重绑、确认/取消、二段和充能分别验证。输入回调计数只能证明处理函数被调用；再次读回服务端认可的学习、点数、法力、CD、位置、命中与阶段。
4. 真实联机使用独立服务器及至少两个真实客户端，记录版本/协议一致性、生产请求、双端状态、新观察者、合法目标过滤、重连、死亡复活、跨维度、新局及关闭清理。fixture玩家与短段同步各有边界，不能冒充真实双端或两模式完整流程。
5. 操作结束调用 `myriad_release_inputs` 并确认 `heldInputs=0`。切世界、打开界面和玩家STOP会释放输入；只正常停止本轮明确创建的实例，不关闭用户自己的游戏。

MCP未装载时，可用同一服务的项目入口：

```sh
node tools/mcpfabric/call-tool.mjs myriad_state
node tools/mcpfabric/call-tool.mjs myriad_input '{"kind":"key","key":"key.myriad.skill_e"}'
python tools/mcpfabric/call-rpc.py myriad.state --run-dir run-fabric-observer
python tools/mcpfabric/call-rpc.py myriad.presentation
```

`--record /path/evidence.jsonl` 记录时间、实例、参数与响应，凭据不入记录。`myriad.presentation` 是只读生产表现/声音提交观测，实际事件接收与SoundManager返回值不表示听感通过。适配只编入独立测试JAR，发行主JAR排除它和MCPFabric；`unknown_method` 时检查实际加载和版本，不绕过服务端规则。

## 材料与结论

截图/连拍只用于向人提供可比较材料。按明确要求采集 `screenshot`、`myriad_capture` 或输入的 `captureFrames`；短暂效果记录多帧及 `clientTick`，保存原始文件供人查看。截图、离线渲染、自动比较和模型评分均不能签发视觉通过。全部GUI/HUD、模型、持握、第一/第三人称动作、相机、VFX、遮挡/可读性、音效听感、手感、流畅性和平衡由人确认；没有人的结论就写“人工待验/差异待确认”。

按本次授权场景验证核心链路；性能及边缘态仅在任务要求时展开。区分“客观通过”“客观失败/受阻”“人工待验”“人工已确认”“差异待确认”，保留失败及重跑，技术交付与父规格整体验收分别声明。

来源：上游 [MCPFabric](https://github.com/denfry/mcpfabric)；本技能和测试适配由万象项目维护，上游自主任务/记忆运行时未启用。
