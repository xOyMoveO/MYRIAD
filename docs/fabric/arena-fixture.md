# Fabric 26.3 双模式合成地图复现

#43 唯一玩法来源为 `main@8da79ddc329492155cea44f3166c97f0e37d86fc`。本目录的配置是从零编写的合成样本，不是正式/原始地图或其副本。技术验证不需要原图；原图导入、逐点对照和原图整局全部由人执行。全部视觉、听感、手感、流畅性与平衡仍为人工待验。

## 基线对应

| 能力 | 生产入口与保持的规则 |
|---|---|
| 共享大厅、模式选择 | `LobbySystem` 读取世界根目录 `myriad_lobby.json`；`/myriad arena 5v5|3v3`、队伍界面和设施编辑器调用 `MatchArena`；仅 WAITING 可切换，管理员权限在命令/接收器复核 |
| 分队、选角、准备 | `/myriad team`、队伍界面开始；自动均分或使用手动分队；每方上限5/3、双方共享14角色池且不能重复；先选一人，再双方各连续两人；选完入泉，200 tick后开战 |
| 地图路由 | 5v5 配置保留世界根路径；3v3 为 `myriad_arenas/3v3/`；大厅、玩家个人 HUD 设置和新对局检查点均不混入这五份地图配置 |
| 兵线、建筑 | `LaneSystem`：当前基线有塔时以双方基地、塔序和拐点生成实际路线，手动路径点仍读写保留；无塔时使用手动点或基地兜底。首波70秒、后续40秒；分层拆塔后水晶可受普攻伤害，技能伤害不拆建筑 |
| 草丛、野区、资源 | `VisionSystem`、`MonsterManager`、`GameManager`：小怪30秒、中怪60秒、大型首领8分钟；5v5三座小灵脉可占领，两座大型候选仅保存，基线大型灵脉暂停；3v3资源系统可明确关闭 |
| 生命周期 | 胜利后下一 tick 回大厅，重开重新选角；关闭玩法清理临时对象。新 Fabric 检查点 `myriad_fabric_match.json`；旧 `myriad_match.json` 不导入。独立服务器恢复新对局；本地/LAN主持会话结束并在下次打开时关闭玩法 |

本次修复两处实际平台/生命周期差异：世界与玩家列表未就绪时不恢复设施；待处理的新局重置先执行，再写入管理员重新分配的阵营。命令分队与既有界面统一在 WAITING 接受。

地图文件的坐标模式与基线一致：仅主世界 `minecraft:overworld`，没有可跨维度导入的字段。不要把额外 `dimension` JSON 字段当作已支持的异维度地图。现有历史阵营键 `xuanxiao/youming` 保留其蓝红阵营归属，不重写已标定数据。

## 可分发样本

输入文件在 [`src/gametest/resources/arena-fixture`](../../src/gametest/resources/arena-fixture)。12份 JSON 只含地图点位、共享大厅和模式选择，不含地形、玩家 NBT、角色/技能进度、金币、等级、个人 HUD 或任何进行中的对局。发行主 JAR 排除整个 GameTest 源集和样本。

| 样本 | 5v5 | 3v3 |
|---|---:|---:|
| 主世界边界，所有设施 y=64 | x=0…160，z=0…128 | x=256…416，z=0…128 |
| 线路/手动路径点/拐点 | 3 / 18 / 2 | 1 / 6 / 0 |
| 塔/泉水/水晶 | 18 / 2 / 2 | 4 / 2 / 2 |
| 草丛/小怪营地/中怪营地/随机首领点 | 1 / 1 / 1 / 1 | 1 / 1 / 1 / 1 |
| 小灵脉/大型候选/小地图角 | 3 / 2 / 4 | 0 / 0 / 4 |

共享大厅为 `(-32.5,64,64.5)`，朝向90度。固定营地、阵营、设施种类、所有坐标和各输入文件 SHA256 均由生成器写入实例根目录 `arena-fixture-manifest.json`，供 #44 或人工对比。5v5、3v3 的手工路径点不被误称为当前基线必然使用的行走路径。

```sh
python tools/fabric/create-arena-fixture.py --server-dir /absolute/new-empty-server --port 25643
```

目标必须全新或为空；生成器拒绝覆盖已有文件，不读取其他世界，不下载游戏或模组，也不代为接受 EULA。生成的新 `server.properties` 将服务器限制为 `127.0.0.1`，离线测试账号、10名上限和测试专用平坦地形，表面 y=64。仅供本机隔离验收。复用现有 Fabric Loader、26.3服务器依赖和共享资源；安装当前构建的 MYRIAD 主 JAR，然后按已有 Minecraft EULA 设置启动。不要把该测试配置覆盖到正式实例。

## 自动客观检查

分别在新进程运行；旧专项共享全局状态，不能并行聚合后声称等价。

```sh
bash ./gradlew build
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:arena_fixture
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:arena_tower_5v5
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:arena_tower_3v3
```

地图导入要求隔离启动时已设置的 `myriad.combatTests`，独立服务器专项要求生成器创建的 `synthetic-only` 实例标记；拒绝把这些配置导入任意手动启动的普通世界。

主流程使用10/6名同进程 fixture 玩家；正式命令经真实 dispatcher，开始/选角/设施拒绝经生产 C2S codec 和已注册 Fabric receiver。未直接写对局 phase、角色预约或伤害后的建筑生命来冒充开局/胜利。仅将测试世界时钟推进到已定义的计时边界，并给合法普攻较大的测试伤害以缩短拆塔；兵线执行实际导航与碰撞步进。两份塔专项让65个实际服务器 tick 自然运行，核对设施确实索敌、攻击敌方而不攻击友方。

主流程检查两模式配置修改、保存、读回、切换隔离、大厅、管理员/阶段约束、分队、轮次与唯一性、准备、兵线、泉水、草丛视野、小地图、营地与首领刷新、资源点、塔序/水晶胜负、回大厅、重开、关闭和清理。检查本地/LAN规则时直接调用实际生产的会话打开/关闭处理器；这不宣称已经运行物理客户端退出世界。

XML 为 `build/reports/fabric-gametest.xml`，下一次专项会覆盖；逐项报告在测试世界根目录 `arena-fixture-gametest.json`、`arena-fixture-tower-5v5.json`、`arena-fixture-tower-3v3.json`。报告包含实际断言和失败原因，不能只看 Gradle 退出码。

### 独立服务器实际保存/重启

```sh
python tools/fabric/create-arena-fixture.py --server-dir run-fabric-arena-fixture --port 25643
# 按已有测试环境设置 Minecraft EULA；生成器不会写 eula=true。
bash ./gradlew runArenaFixtureServer -PmyriadGameTests -ParenaFixtureStage=write
bash ./gradlew runArenaFixtureServer -PmyriadGameTests -ParenaFixtureStage=read
bash ./gradlew runArenaFixtureServer -PmyriadGameTests -ParenaFixtureStage=read-off
```

`write` 使用同一套生产流程完成两模式检查，再保存一局新的3v3及其已生成单位/营地并正常停止。第二个进程 `read` 在真实 Fabric 世界就绪事件恢复，对比模式/阶段/设施/野怪/参与者阵营，关闭玩法、清理并停止。第三个进程 `read-off` 确认关闭状态重启后仍为关闭和 WAITING，并逐点核对两模式配置。另一套全新实例的三个命令均加 `-ParenaFixtureMode=5v5`，即可独立检查5v5的实际保存/恢复；默认保存3v3。服务器启动恢复错误可能发生在测试入口之前，因此必须同时检查服务器日志、报告 `passed` 和成功标记。失败实例先保留，下一轮用新空目录，不要清空用户世界。

开发入口仍是 `runArenaFixtureServer -PmyriadGameTests`，不传 stage 时保持正常独立服务器运行，供 #44 接入真实客户端；不自动创建 fixture 玩家。它含测试 mod，仅用于隔离复现，发行验收应再使用主 JAR 独立服务器。

## #44 独立服务器与真实客户端

1. 创建新合成实例、核对构建摘要和 manifest；独立端口启动，连接至少两个真实客户端。需要满员测试时分别用6/10名真实参与者；同进程 fixture 通过不等于真实联机通过。
2. 控制台 `op <测试账号>`、`myriad mod on`、`myriad arena 3v3`；客户端在共享大厅通过队伍界面分队、选择手动或随机开始。也可管理员 `myriad team xuanxiao <名称>` / `myriad team youming <名称>` 后在队伍界面开始。
3. 按真实轮次选唯一角色，核对两端收到的准备/入泉/开战；检查当前模式的塔、泉、水晶、草丛、视野、边界、营地和计时刷新。实际等待首波70秒，检查按当前路线行走；合法普攻依次拆同一路塔和水晶，结束回共享大厅。
4. `myriad restart` 检查新草稿与清理；关闭后切至5v5，重复完整流程。修改点位后保存、正常 `stop`、重启比对两模式坐标。进行中新局再正常退出/重启，独立服务器应恢复受支持的状态；`myriad mod off` 后重启保持关闭。
5. 独立创建本地/LAN合成世界，退出世界后再次打开，确认没有续局；记录主持人与客户端输入、阶段和清理结果。此步骤由真实客户端整合执行，不能用“专用处理器测试通过”替代实际退出世界。

以上均是合成测试世界的客观联机检查。视觉和主观结论另记人工结果。

## 正式/原始地图人工清单

助手不索取、读取、导入、运行或代验正式/原始地图，包括其备份实图副本。下列步骤完全由人操作，缺少原图或人工结论不阻塞技术交付。

1. 先备份原件和原配置，记录版本/产物；只在可恢复的副本安装 Fabric版本，原件保持不动。不要让新旧加载器/模组同时修改同一份世界。
2. 分别盘点5v5根目录和3v3目录下五份地图配置，以及根目录大厅/模式选择；记录文件摘要、主世界、坐标、类别、阵营、路径顺序/拐点、塔层级、草丛角点、营地类型、大小灵脉候选和小地图四角。地图配置、地形方块、玩家状态、运行中对局分别记录，不互相代替。
3. 在副本只导入上述地图配置，不导入旧玩家等级/金币/角色/技能、个人 HUD 或旧 `myriad_match.json`。需要移除旧模组设施实体时，先备份、按配置重建新设施；不要通过旧实体续接旧局，不擅自改造地形。
4. 分模式切换、逐点读回、修改保存、退出重启后复核；确认大厅共享、设施不串模式、地形/通行净空仍匹配。每个模式各完成一次真实整局：分队/选角/入泉/准备/开战、兵线/野区/资源、拆塔/水晶胜负、回大厅/新局/关闭。随后分别检验新独立服务器检查点恢复和本地/LAN不续局。
5. 人工单独判断地图呈现、设施外观、界面、模型、动作、特效、音效、手感、流畅性和平衡。没有人的结论时填写“人工待验/差异待确认”。

可选证据：版本及JAR摘要、配置摘要及点位表、参与者/真实客户端数、日志、截图或录屏。可以提供已有对照材料供人比较，提交证据和提供原图均不是技术交付条件。

| 日期/执行人 | 原图副本/模式 | 版本/JAR摘要 | 配置/地形/玩家/对局分别记录 | 操作与期望 | 实际结果/差异 | 可选证据 | 人工结论 |
|---|---|---|---|---|---|---|---|
| 待填 | 3v3 或 5v5 | 待填 | 待填 | 待填 | 待填 | 可空 | 人工待验 |
