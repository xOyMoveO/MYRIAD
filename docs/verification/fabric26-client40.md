# Fabric 26.3：#40 客户端接合与客观验证

功能基线为 `8da79ddc329492155cea44f3166c97f0e37d86fc`。本记录覆盖客户端平台接合及朔「一闪」试点，不代表14角色/112候选或两种地图对局迁移验收完成。全部画面、声音听感、操作手感、流畅性和平衡结论仍待人工验收；本轮未评分或代签视觉通过。

## 交付入口

- `MyriadClient` 使用 Fabric client entrypoint。HUD、键位、粒子/provider/group、实体渲染和客户端连接事件使用目标版本 API。
- Native keyboard/mouse、实际玩家 tick、相机、living renderer、name tag、`FirstPersonHandsAndItemsRenderer.submitArmWithItem` 通过仅客户端 mixin 接合；原有GUI及各角色模型/动作/武器视图逻辑保留。真实物品经 `ItemModelResolver` / `ItemStackRenderState` 提交。
- SDL 键码和鼠标值取 `InputConstants`；自绘选角、加点、双击、拖动等不继续比较旧 GLFW 的0/1。GUI命令只拦截完整别名，避免抢占服务端 `/myriad mod on` 等子命令。
- 新HUD设置使用 `config/myriad_fabric_hud_layout.json`，不读取或覆盖旧个人配置。连接结束/关闭玩法清除相机、技能起手和表现状态。
- `tools/mcpfabric/launch-client.sh pilot|observer <official-jar>` 校验5149项共享资源，单独构建与安装测试适配，启动两个独立客户端；详细操作见该目录README。发行JAR不包含这个测试适配、MCPFabric、凭据或世界。

## 实际环境与操作

MC26.3 / Fabric Loader0.20.1 / Fabric API0.166.2+26.3 / Java25。MCPFabric0.10.0+26.3由官方Modrinth版本 `7CFkrsmI` 取得，317405字节，SHA512固定在 `tools/mcpfabric/installation.json` 并已校验ZIP。Node服务协议复用0.10.0；本轮通过同一认证桥接的 `call-rpc.py` 记录参数与结果，不将令牌写进证据。

本轮工作树 `issue40`，独立目录 `run-fabric-client`、`run-fabric-observer`，桥接25599/25600；新建测试服世界 `fabric-pilot-new`，端口25576，仅回环离线测试。普通客户端本轮用户名 `Player787`，观察者 `MyriadObserver`。后续复现应采用固定的pilot用户名，避免Gradle默认随机身份。原正式实例、原始地图和旧玩家存档未参与。

测试平台为明确的夹具：石块 `x/z=0..20,y=79`，两名玩家Adventure模式，由服务器控制台放置和传送；阵营通过生产训练GUI按钮设置。`TrainingMode=true`，`nocooldown/nomana=false`。基线训练模式本身允许试用未学习技能，不能把训练中未学习Q释放视为非法输入校验失败；本轮一闪仍实际经GUI学习。

正式被测操作：`/myriad choose`打开选角，原生SDL鼠标点击朔卡片与确认；原生C键打开修行，自绘技能卡单击后点击学习按钮。真实生产 `UpgradeSkillPayload` 处理后客户端同步 `shuo_yishan=1`、等级1、技能点1→0。没有直接写角色/技能结果。

随后原生技能E经 `KeyboardHandler.keyPress` 进入生产施法。法力700→620，220tick冷却首采样读到218或219；冷却期间再次E未再次收费或重复charge/draw。目标物理防御0、施法者Spell Power30；目标HP从700→628.6→581.0，对应71.4+47.6=119，即95+0.8×30，之后正常回血。

两真实客户端接收到相同服务端started的起手、刀痕、轨迹和结束VFX；owner收到CHARGE/FLASH动作。两端的 `shuo_charge` / `shuo_draw` / 命中声音进入真实 `SoundManager.play` 并返回STARTED，声源player、LINEAR衰减；这里只证明调用与参数，不推出听感。原生输入结束均释放，`heldInputs=0`。

首次位移暴露平台回调错误：`Player.tick`内临时预测位置被26.3的`ServerGamePacketListenerImpl.tickPlayer`回滚，旧Post接合错误累计距离，实机主推进提前停止。主实现将Post移动到回滚后。相同场景复测主推进6.96417格，符合基线`7-.04`结束阈值；随后基线保留的12%残余速度及原版摩擦继续滑行，最终总位移7.36810格。不能为迎合“最终恰好7”而删除基线惯性。

服务器真实save-all/正常stop/新JVM启动后，两客户端重连只读确认角色shuo、等级1、`shuo_yishan=1`、点0与玩法/训练配置恢复；连接entity IDs变化，适配持有输入为0。该证据覆盖本次新数据，不证明旧数据迁移。

独立物理客户端 `runGameTestClient -PmyriadGameTests` 实际运行原客观断言2339项：flame pipeline4、Shuo arm model388、sheathed grip1939、held transform8。报告 `build/reports/fabric-client-checks.json`。这些数字和材质约束不是视觉验收。

本机详细过程证据：`/private/tmp/myriad-fabric26-client-tools/evidence.jsonl`、`flash-observation.jsonl`、`flash-after-motion-fix.jsonl`；启动日志 `.../myriad-fabric26-client-pilot-final.log` 与 `.../myriad-fabric26-client-observer-final.log`。原始JSONL区分每次输入与后续双端读回，保留首次失败和修复后的实际结果。

## 基线已有资源警告及后续归属

下列八个模型被26.3引擎报告 `Missing texture references ... particle`。各JSON均与固定基线逐字节一致，只有layer0纹理槽，没有particle。旧NeoForge26.2真实日志 `/private/tmp/myriad-mcp-integration-20261116/run-mcpfabric/logs/latest.log:105-120` 已有相同八项警告，故不能记为26.3迁移新增问题。

| 实际模型ID | 对应角色/归属 |
|---|---|
| `myriad:item/leitingjian` | 天刑；角色批次及#44资源审计 |
| `myriad:item/zhenqi` | 墨尘；角色批次及#44资源审计 |
| `myriad:item/qingluo_staff` | 青萝；角色批次及#44资源审计 |
| `myriad:item/hanze_ice_spike` | 寒泽；角色批次及#44资源审计 |
| `myriad:item/fengren` | 素岚；角色批次及#44资源审计 |
| `myriad:item/chixiao_blade` | 赤霄；角色批次及#44资源审计 |
| `myriad:item/lieyanjian` | 焚诀；角色批次及#44资源审计 |
| `myriad:item/shuangbing` | 寒泽；角色批次及#44资源审计 |

已确认影响为particle材质槽不能解析；本轮未据此判断完整模型画面。后续可按实际layer0补充particle引用，验证引用及运行日志，再由人判断视觉。

`assets/myriad/sounds/skill/tianxing/SOURCES.md` 也被引擎判为非法资源路径并忽略。文件与基线一致；旧Neo26.2日志同一路径第101行已有此警告。它是来源说明文档，不是声音事件或音频文件；后续#44应将来源/许可文本保留到适当的非assets位置，不能为消除警告丢弃归属信息。本轮未改素材或归属声明。

## 墙体与关闭玩法复测

在同一测试平台放置真实石墙 `x=3..8,y=80..83,z=8`，目标移到 `z=10.5`，施法者重回 `z=5.5`。通过原生右键免费回鞘后，原生E触发收鞘一闪。连续双端采样最大/最终施法者 `z=6.666697`，保持在墙前，墙后观察者HP始终700；仍扣80法力并进入正常220tick冷却。证据 `flash-wall.jsonl`。该场景不能替代所有薄墙、坡地、楼板或动态障碍。

通过正常客户端命令 `/myriad mod off` 关闭玩法后，客户端同步角色/技能空、生命上限及生命20、法力50、冷却0，位置保持不变。随后原生E进入键盘处理函数，未重新施法、扣蓝或位移；两端 `myriad.release` 均回报 `heldInputs=0`。这些是恢复状态和正式输入的客观结果；原版HUD画面本身仍待人确认。

## 尚未由本记录覆盖

死亡克隆/控制取消的真实客户端专项仍待所属任务补实际结果；旧地图、完整对局、其他角色与所有视觉及主观项目仍由其所属任务和人验收，不由本次单技能链路或2339项客观断言代替。
