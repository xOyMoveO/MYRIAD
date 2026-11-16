# MCPFabric 主线整合与权限复核

2026-11-16。独立分支 `chore/mcpfabric-client-integration-20261116`。`origin/feat/roster13-manual-acceptance` 的 `c233f752299916bef2c48d158a6829457d9541d8` 已包含 `feat/mcpfabric-myriad-input` 的 `b689dff65e6e9284dd57cd05aff605a538d5d48c`；旧 PR #30 合入的是验收分支。本轮从 c233f75 合并固定主线 fe95e20，保留上述历史，自动合并无冲突。随后主线移动至 ac98b0e，由新 PR 对当前 main 计算合并结果。

## 范围与权限

相对固定主线的原始独有差异为16个 MCPFabric 工具、skill、构建接线和历史证据文件，游戏 `src/main` 没有差异。普通发行仍使用 NeoForge26.2.0.59；只有专用测试启动单次指定26.2.0.88。

补齐专用适配的能力边界：

- `myriad.state` 遵守 `enableVision`。
- 界面已打开时，原始键盘和鼠标都先检查 `enableUiControl`，并继续保留 player-control 与 STOP 校验。
- 关闭观察能力后，成功输入仍返回成功确认，只含 `heldInputs` 与输入路径；不会在副作用发生后才报观察权限错误，也不附带角色、坐标或技能状态。
- `myriad.release` 始终可清理本适配的输入，但只回传 `heldInputs`。

连接仍限制127.0.0.1与Bearer认证，不使用全局自动发现。已检查本轮公开差异，未发现私钥、固定令牌或认证配置；JAR、配置、缓存、依赖、日志及测试世界均不跟踪。已有公开历史GIF不变。

## 本轮验证

- 普通 `build --offline --no-configuration-cache` 通过（11秒）；发行JAR为27,096,849字节，不含 `/acceptance/` 或 `dev/mcpfabric/` 类。
- 专用26.2.0.88测试配置成功编译 `compileMcpAdapterJava` 并启动客户端；测试构建输出通过外部init脚本放在独立忽略目录，没有覆盖普通发行产物。
- 4个JavaScript模块语法、Python AST、Bash语法通过；声明依赖与锁文件、已有本机SDK版本一致。MCP握手实际列出54个工具。
- 共享索引32的5,059个对象逐项校验大小与SHA1通过，复用 `~/.local/share/minecraft/assets`，没有下载对象资源。SDK、MCPFabric JAR和Gradle依赖复用已有副本；模组JAR的SHA512与 `installation.json` 一致。
- 在本轮专用一次性 `MCPPermission-QA` 世界，通过真实MCP服务关闭vision/UI开关验证：状态读取被拒绝；游戏内Escape成功只回确认，不含完整状态；暂停菜单内第二次Escape被UI权限拒绝且仍停留在暂停菜单；释放回执严格为 `{"heldInputs":0}`。测试世界仅复制此前任务生成的GameTest fixture，未打开正式用户存档。前一次检查在世界仍loading时提前判断，修正等待条件后上述检查通过。
- 测试结束已释放输入并结束专用客户端会话。没有重新验收角色视觉、多人、缓时或实战平衡。

详细本地日志位于 `/private/tmp/myriad-mcp-integration-evidence-20261116/`，不随PR发布。
