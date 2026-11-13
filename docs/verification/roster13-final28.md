# #28 十三人功能整合与测试交付

2026-11-13，独立分支 `feat/roster13-final28`，工作目录 `/private/tmp/myriad-delivery/final28`。本记录按当前 #28 功能清单及父规格的最新阶段边界执行：先完成功能，视角、握持、模型、VFX观感与游戏内听感统一后置；不使用 computer use 或其他 GUI 自动操作进行游戏测试。父规格 #2 继续开放。

## 来源与最终功能

- 基点 `c12e004` 已包含 #10/#11/#12 和共享薄墙修复 `217783b`。保留原两角色实现与有效证据，没有重新审查两票。
- 战场来源 `f54692f3216114f28f3d0dcf8b6237d1581cdbf7`，no-ff 整合点 `0bbeece`；角色来源 `77803d3c5a70cd60d3dd926d21ad916039c36b20`，no-ff 整合点 `e0ca211`。合并只在 Gradle 专项目录／fixture排除名单发生冲突，已保留三个新增专项及原入口。
- 正式入口、精确回归及最终受测源码为 `de16022`；本记录之后仅补充文档，不改变该受测 Java／资源或交付 JAR。
- 正式选角、图鉴与对局角色池共十三人：夜影、天刑、焚诀、墨尘、素岚、凌渊、寒泽、青萝、赤霄、云笙、朔、刻衡、伊珂。移除了两名新角色仅限训练的门槛，没有增加解锁条件或恢复退役角色。
- 既有聚合精确断言十三人集合、登记图谱一致及104个独立候选实现；逐角色检查成长、互斥、等级／技能点预算、经济和有效状态保存。十个退役ID的消息／命令拒绝、无专属武器回退和普通非法输入断言保留。旧日志“88 modules”改为实际遍历的角色数与独立实现数。
- 更新当前角色说明与玩家日志。十一人逐技能适用／不适用项和真实矩阵见 [角色行动记录](roster13-hero-time28.md)；兵线、野怪、首领与箭见 [战场记录](roster13-world-time28.md)。这些矩阵以实际施法、血量、motion、恢复与轨迹断言为依据，不用枚举数量冒充兼容行为。

四法师真实普攻使用 `MagicBullet`，其恢复与独立飞行已接入局部行动进度；七名近战保留 `Player.attack` 恢复路径。焚诀／朔私有攻击帧和自主运动保留已完成比例。技能冷却、回蓝、控制到期、DOT、预警、持续场地，以及伊珂120tick姿态、22／27tick出膛间隔、光束六跳均保持真实时间。免控、净化、友军／自身与来源取强沿既有规则处理。

迟针现能从兵线独立生命池识别有效命中。四种大型首领免缓时但承受合法伤害，设施保持过滤。普通箭、药箭、光灵箭使用完整原版物理与碰撞链，倍率通过 NeoForge 同步 attachment 跟随实体跟踪。原版没有固定飞行寿命或射程阈值；按本轮明确决定，仅玩法启用时新增1200行动tick／2400真实tick飞行清理，扎地仍为原版1200tick。三叉戟和第三方任意弹体不在范围。

## 实际整合验证

JDK25：`/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`。所有 Gradle 执行使用既有 `gradle_serial.py` 和 `/private/tmp/myriad-implementation/gradle.lock`，串行运行。原始证据目录 `/private/tmp/myriad-delivery/evidence-final28/` 是临时目录，可能随清理移除。

| 执行／日志 | 实际结果 |
| --- | --- |
| `compile-delivery-permitted.log`，`compileJava` | 新增正式入口／同步专项编译通过，退出0。之前默认沙箱执行 `compile-delivery.log` 因不能写本机 Gradle wrapper 锁而未启动编译；按权限流程后成功。 |
| `final-functional-1.log`，`build runRosterDeliveryTestServer runHeroActionTimeTestServer runWorldTimeTestServer runFirstGroupTestServer ...` | 构建通过；正式入口／同步、角色126场景、战场专项各GameTest 1/1通过。新版聚合在旧焚诀夹具失败，整条命令退出1；后面的刻衡／伊珂／公共任务当时未运行，不将整条命令写成成功。 |
| `aggregate-fixture-2.log`，`runFirstGroupTestServer` | 焚诀夹具补逐tick推进后继续到朔旧归鞘夹具失败，退出1。 |
| `aggregate-fixture-3.log`，`runFirstGroupTestServer` | 两处夹具修正后，完整新版聚合GameTest 1/1、BUILD SUCCESSFUL、退出0。日志实际输出十三人／104候选、104独立实现；原天刑12tick禁锢断言保留并通过，本轮没有再现该历史失败。 |
| `final-remaining-4.log`，`build runKehengTestServer runYikeTestServer runGameTestServer`，公共任务限定 `myriad:combat_regression` | BUILD SUCCESSFUL、退出0；刻衡／组合42场景、伊珂60场景、公共战斗回归各GameTest 1/1通过，最终JAR重新构建。 |
| `namespace-final.log`，`python tools/check_namespace.py --jar build/libs/myriad-0.1.0.jar` | 退出0，源码命名空间、包、JSON与发行JAR通过。 |
| `resources-final.log`，`python tools/check_retired_roster.py --jar build/libs/myriad-0.1.0.jar` | 退出0；1314个源／工具／资产文件、1445个JAR条目；源树与JAR各271条自有模型／纹理／粒子／声音引用完整，退役专属内容检查通过。 |
| `delivery-final.log`，直接读取最终JAR | 两新角色定义存在，全部七个开发测试fixture和空测试结构均排除；大小及SHA256见下方。 |

首轮失败的具体原因是旧mock夹具没有自然 `Player.doTick`：焚诀只在+2/+3/+7等观察点调用运行时，朔只在第8tick调用私有归鞘更新。原绝对截止时间允许这种跳步，新行动进度明确不补算遗漏更新。仅给 `FlameSlashReleaseChecks` 增加逐真实tick消费者，给 `ShuoWeaponStateChecks` 补1—7tick更新；保留首刀3／次刀7、精确伤害、无早伤／重伤、目标进出、墙体、死亡／换角／关闭玩法，以及第8tick归鞘与装备不变的所有原断言。未改变生产伤害、计时、随机区间或取消语义。没有用重复运行碰运气，也没有删除有效断言。

角色126场景、战场和正式同步在两处测试夹具修正前已于最终生产代码通过；之后仅修改这些旧测试消费者，复用仍有效结果，不重复无关专项。报告XML已复制到证据目录；部分GameTest XML只有嵌套空testsuite，场景结论以带PASS、完成与退出码的实际日志为准，不能把场景数称作独立GameTest数。

## 两角色与实际状态同步

42／60场景继续覆盖两角色资源提交、真实伤害／护盾与被动去重、免伤、控制／净化、墙体／薄墙／楼板、进出与多来源、预警／姿态／逐炮间隔／光束真实跳伤、普通生命周期。刻衡S230实际伤害链为1518.5减伤前；伊珂五炮加重轰总405法力，在魔抗50目标实际结算2070.5／1.5。没有把纸面上界或功能通过称作平衡通过。

新增 `roster_delivery` 使用真实服务端玩家对象与生产选择、施法、发包及编解码入口，验证：

- 关闭训练模式时十三种角色的网络选择、对应武器与一个初始技能点；两新角色命令可用；正式对局选人按轮次和角色唯一性拒绝非法重复选择。
- 正式模式伊珂E真实展开、Q未出膛进度、重复待发输入只扣一次35蓝和一炮；真实权威HUD角色、学习数据与蓝量一致。
- 自身、近处队友和可见敌人收到一致的姿态／炮数／准备进度／真实截止／炮口数据；不可见敌人与64格外队友不收到该状态；退出清理发送空闲状态。
- 实际发送的主人和观察者状态能经生产wire codec往返。连接为测试用包捕获器，不宣称已建立多客户端网络延迟、客户端画面或听感验收；箭的同步attachment另由战场实际对象场景验证。

## 测试交付与边界

新测试用途JAR：`/private/tmp/myriad-delivery/final28/build/libs/myriad-0.1.0.jar`，**25,904,707字节**。

SHA256：`a40508c2869d3f824d79fad30743eb7a0ae4ac59387c4cb6bad1d9e5021add10`。

本轮功能实现及上述必要验证已完成，没有已知范围内功能阻碍。#28 唯一综合review当前仍为0/1，由主流程在本次固定提交后安排；本作者没有审查或派审查。父规格 #2 的后置视觉／双视角／握持／模型／VFX／听感和多人客户端表现继续开放，不以服务端通过替代。

缺少外部地图fixture，竞技场专项未运行；没有伪写通过或以它阻塞已授权功能交付。没有恢复取消的#4退役玩家历史状态／检查点兼容，没有加载正式存档、安装正式实例、推送、开PR或合并main。所有测试均在隔离开发测试世界进行。
