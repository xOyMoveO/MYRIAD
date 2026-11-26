# 万象对战体验验证（2026-11-26）

基线为 `d4c7481`，Minecraft 26.3 / Fabric，双端协议由 25 升至 26。樱隙前线实测使用仓库地图包生成的独立测试副本。GameTest 的模拟参赛者、实机鼠标键盘输入和服务器状态读数分别记录。

| 清单 | 生产入口 | 验证结果 |
| --- | --- | --- |
| 1 兵线经验 | `LaneSystem.onDeath`、`distributeMinionReward` | 实图 2v2 中，落霞连续补两只近战兵，各获 18 经验和 17 金。同队赤霄各获 14 经验，钱包两次均保持 730 金。合成对局中，首波 2 近战、2 远程，补刀者得 84 经验，近旁队友得 66。相同兵线下单人补刀者、双人组补刀者和吃线队友均在第二波达到 2 级。`lane_rewards` 另核对远距离补刀、塔尾刀、12 与 12.01 格、非参赛、掉线、旁观、死亡、跨维度、友伤和管理移除。 |
| 2 输入反馈与缓冲 | `InputFailure`、`MeleeAttackBuffer`、`VanillaMeleeAttack`、`AbilityRuntime` | 拒绝原因使用 Action Bar，同一原因间隔 12 tick，控制提示可覆盖恢复提示。客户端参数 `-Dmyriad.meleeBufferTicks=0` 可关闭缓冲。`melee_assist` 覆盖中心与擦边选择、视线、距离、恢复门槛、一次消费、到期和提示去重。实机时序见下文。 |
| 3 防御塔 | `LaneSystem.tickTower`、`onHeroAttacked`、`TowerSight`、`VisionSystem.visibleToTower` | 樱隙前线守护外塔位于 (−65.5, 83, 39.5)。基线中，进入原有青色玻璃和凝灰岩立柱后，落霞仍受伤，生命由 575 降至 509.17。新版同位置解锁，测试英雄在柱后保持 660 生命。再次露身后服务器于 tick 7043 起锁、7071 首次命中，生命降至 586.49。`tower_cover` 检查石墙、玻璃和不同高度的局部碰撞形状，也覆盖队友入草提供视野。`arena_tower_3v3`、`arena_tower_5v5` 复测无遮拦开火，`combat_regression` 复测水晶规则。 |
| 4 无责重开 | `GameManager`、`EarlyRemake`、`QueueLobby`、`LobbyScreen` | 实图 1v1 在开战第 404 tick 断开一端，另一端看到等待提示，宽限后自动回到带有「再排一局」「返回大厅」的界面。重连者回大厅时无参赛席位。服务端专项验证第 400 tick 与 401 tick、跨 90 秒的既有观察、少人侧一票通过、重连、双方离线、旧票失效、超时、首杀、拆塔、正常获胜及上场记录。 |
| 5A 助攻 | `MatchAssists`、`GameManager.recordHeroDeath`、`MatchTabPayload`、`MatchResultsScreen` | 实图 2v2 中落霞削血、赤霄击杀，双方 Tab 显示落霞 A+1、赤霄 K+1、天刑 D+1。六个真实客户端组成的 3v3 中，落霞、铆钉先后将天刑从 660 打至 591.03、547.08，赤霄收尾。同一阵亡在双方 Tab 上均显示落霞、铆钉各记一次助攻，赤霄记一次击杀，天刑记一次死亡。服务端专项覆盖 10 秒内实扣最大生命的 5%、一次生效控制、6 秒内救援最终击杀者、重复贡献、离线贡献、塔尾刀归属、复活及旧检查点。助攻不产生金币或经验。无施放者归属的旧治疗或护盾来源跳过，自疗、溢出治疗和未吸收的盾亦不计分。 |
| 5B 裂隙蟹 | `MonsterManager`、`MapSystem`、`RiftCrabMarker`、`MinimapOverlay` | 实图中两队均收到首刷 tick 9690。刷新后，首领岛近处队伍看到存活，远处队伍收到未知。落霞实射击杀后，知情队收到重生截止 tick 15787，未目击方仍收到未知且无截止时间。六端复测中知情方得到重生计划 17628，tick 17656 已看到实际刷新的蟹，远端仍收到未知。测试以原版 tick sprint 加快等待。`rift_crab_minimap` 还覆盖查看营地秒牌、重连、读档、重开和无蟹地图。 |
| 6 落霞普攻时序 | `LuoxiaInput`、`LuoxiaBow`、`LuoxiaPoseClient` | 实图双端以相同的 13 tick 持弓、松开后间隔 2 tick 再按对照。旧版靶方生命 660→591.03，仅第一箭造成伤害。新版 660→591.03→522.07，两端均见两次命中。基准普通箭工作量 11，1.5 倍及 2 倍攻速分别在第 8、6 tick 达标。基准首箭比旧版晚 5 tick。连射时移除了射后的 5 tick 锁。 |
| 7 归霞弓与技能动作 | `LuoxiaBowPose`、`LuoxiaBowMotion`、`LuoxiaBowView`、归霞弓模型和箭台 | 普攻和技能共用握点、弦心和箭台。取消清除搭箭，斜照撤步保留原拉弓进度。专项覆盖飞羽 3 次单射、千羽 3 次扇射和霞沉锁向后 7 tick 实射。第一轮联机录像中第一人称握弓手遮住了部分箭杆，调整手部尺寸、握点和前段箭长后，在第二个同版本联机环境重拍了右手与左手视角。 |
| 8 技能引导 | `ControlStatusOverlay`、`SkillChargeSystem`、`SkillChargeClient` | 上方控制列表移除引导行，保留真实控制。下方现有读条也显示千羽照野的 7 tick 短引导。缓时倍率改变时，服务端同步已完成工作量和新速率，客户端据此显示进度与剩余时间。 |

[穿柱受伤的基线录像](assets/battle-experience/tower-pillar-before.mp4) · [相同立柱的新版复测](assets/battle-experience/tower-pillar-after.mp4) · [旧版落霞连拉](assets/battle-experience/bow-before.mp4) · [归霞弓动作、挂点与时序](../../art_source/luoxia/bow/动作索引.md)

## 客户端输入与引导

实机复现使用物理按键回调、只读客户端采样和独立服务器的伤害记录。目标被遮挡、超出射程或服务端尚未完全恢复时，沿用原有拒绝判定。

- 准星指向目标时提前 3 tick 点击没有产生缓冲攻击。提前 2 tick 时，中心或 15% 擦边点击均在客户端回满后出手一次。回满当刻的点击沿用即时攻击。
- 提前 1 tick 的试验中观察到服务端与客户端相差一刻。一组 5 次中心点击有 1 次造成第二次伤害，另 4 次未扣血。只读探针在服务器 tick 5228 捕获同类拒绝，当时恢复值为 0.9975，距离、视线、武器、控制与目标受击保护均未阻止攻击。客户端只发送一次，服务器返回恢复提示。另一次双向各增加 130 ms 延迟的试验中，提前 1、2 tick 各补中一次，提前 3 tick 没有补击。
- 实际添加 20 tick 持键或在缓冲期内连点，两者均只补一次。缓冲后打开聊天、更换武器、目标离开射程或转向空气，均没有第二次服务端攻击。交付包换目标复测中，服务器 tick 9878 接受了对另一名玩家的首刀，tick 9907 接受了按新准星对训练假人的缓存攻击，此时服务端恢复值为 1.0。参数切为 0 重启客户端后，提前 1、2 tick 的点击均没有缓存。
- 逐帧检查落霞千羽照野、一点残阳、霞沉一线和刻衡迟针，各有一个下方读条。霞沉一线通过测试位移打断后读条清除，千羽照野的 7 tick 短起手能在准星下方看到读条。同时受到云笙禁锢时，上方仍显示禁锢。正式 1v1 的独立回城读条随玩家返回泉水正常结束。截图取自实际游戏窗口。[千羽照野](assets/battle-experience/short-channel.png) · [刻衡迟针](assets/battle-experience/needle-channel.png)
- 427×240 逻辑像素下，初版技能读条压住了第五格标签。修正默认位置后，在同尺寸的 854×480 双端游戏窗口复拍。刻衡迟针、五个技能格、准星、聊天、血条和蓝条均可见。[交付包窄窗截图](assets/battle-experience/narrow-channel.png)
- 樱隙前线六端对局中，塔锁小兵时落霞向守方出手。完全被原有立柱遮住时，守方英雄被箭命中，塔持续打兵。落霞露身后再射，塔立即转锁，随后落霞由 1025 生命降至 943.80。[同一击杀的两名助攻者](assets/battle-experience/two-assists.png) · [同局水晶结算](assets/battle-experience/two-assists-results.png)

## 同场动作样片

第一人称为落霞本人视角，侧前方画面来自同场的另一名真实玩家。15 秒对照按两端的世界 tick 对齐，依次为普攻、飞羽连弦、千羽照野和霞沉一线。飞羽连弦两端的实射事件位于 6032、6037、6042，千羽照野三轮为 6087、6099、6111。[双视角合片](assets/battle-experience/luoxia-dual-view.mp4)

落霞八项候选分两局实际触发，包括追霞续弦、凝霞满弓、雁过霞痕、斜照撤步、飞羽连弦、一点残阳、千羽照野与霞沉一线。为体验互斥分支，测试角色先领取 1500 场景经验，再在角色界面学习对应技能。凝霞满弓样片使用临时攻速加成。用原版设置切换左主手，拍摄后恢复右主手。[左手持弓](assets/battle-experience/luoxia-left-hand.webp)

| 动作 | 持弓者 | 观察者 |
| --- | --- | --- |
| 普攻连续拉放 | [第一人称](assets/battle-experience/final-bow-first.mp4) | [侧面](assets/battle-experience/final-bow-side.mp4) |
| 千羽照野（三轮扇射） | [第一人称](assets/battle-experience/three-fans-first.mp4) | [侧面](assets/battle-experience/three-fans-side.mp4) |
| 霞沉一线 | [第一人称](assets/battle-experience/sunset-line-first.mp4) | [侧面](assets/battle-experience/sunset-line-side.mp4) |

## 构建与回归

以 JDK 25 执行 `bash ./gradlew --offline build` 和 `python tools/check_namespace.py --jar build/libs/myriad-0.1.0.jar` 均通过。交付包 SHA256 为 `84347f0cfbcdd9b4201d069cfec3ce397c393ba4be38c04c1959a91da4ef9170`。樱隙地图柱体初次对照、2v2、1v1 重开、输入时序和引导测试使用同协议的初版实测包 `d2174bfca8a0b63b760dfaacefa102719dddfdac9ff57f64ff36ef8d2276f1af`。归霞弓调整后的构建 `0ea1a0c7f8ccc0d855ba6fad26957c9af4e40bddad5c53113ab2cf1c5bc52b09` 用于六个独立客户端的 3v3、多人助攻、结算和新版动作样片。交付包在此基础上调整小窗默认技能引导条与战斗面板的间隔，并在两端和专服逐一核对摘要后复测。每场测试的客户端与专服载入同一份 JAR。

每个服务端专项独立启动 JVM，核对 Gradle 退出码、实际用例名和 XML。29 个具名专项各通过 1/1。运行命令为 `bash ./gradlew --offline runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:<专项名>`。

| 范围 | 专项名 |
| --- | --- |
| 对局、兵线、塔与蟹（12 项） | `lane_rewards`、`match_assists`、`early_remake`、`match_economy`、`tower_cover`、`arena_tower_3v3`、`arena_tower_5v5`、`rift_crab_minimap`、`arena_fixture`、`combat_regression`、`cherry_flat_lane`、`cherry_rift_camps` |
| 输入、成长、角色、训练与行动时间（17 项） | `melee_assist`、`luoxia`、`first_group`、`hero_action_time`、`projectile_basic_assist`、`damage_growth`、`combat_feedback`、`roster_a`、`roster_b`、`rivet`、`keheng`、`shuo_pilot`、`training_ui`、`world_time`、`yike`、`chixiao_opening_pace`、`melee_arc` |

初跑暴露了三处夹具问题。兵线金币对照需排除对局自然收入，跨维度用例改到 GameTest 实际加载的下界。助攻的边界用例按模拟时钟顺序发出事件。旧全局战斗回归显式配置其依赖的三路无塔路线，水晶结算按当前清空结果与胜者的行为验证。修正后重跑，以上计数只计入最终通过的具名结果。

联机结束后复核各客户端已释放输入，关闭了本次启动的专服、客户端和延迟代理。正式实例的安装仍按项目迁移约定单独处理。
