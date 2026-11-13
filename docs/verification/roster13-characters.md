# #11／#12 两角色功能整合验证

按主流程同步的 `function-11.md`、`function-12.md` 当前功能清单和 `parent-function-scope.md` 边界执行。#2 的后置视觉条款继续保留，不将本记录作为关闭父规格的依据。

2026-11-13，独立分支 `feat/roster13-characters`，工作目录 `/private/tmp/myriad-delivery/characters`。本记录报告功能整合及实际自动化验证。两票唯一综合 review 已完成，各为 1/1，确认一项共享薄墙命中缺陷；该项已修复，实际复测及最终修复包见文末，不再安排 review。

## 来源与当前范围

- 基点 `8310a8b`；以 no-ff 保留刻衡 `e45e5403a226223eb4338a48ba2c3b2ed3bb9453`、伊珂 `90b53b14ca7d378d4812aac8a3eb4916e008b947` 的完整来历，包含伊珂炮台父提交 `c30086f`。
- 后续完整合入伊珂测试／文档 `f48990007d059da494df925898527acc9ae8303c`、`5ad5b079fcb73849ab5356cc9a4c24c34aeea555`，以及刻衡文档 `ab4eb1ea13834c6613ec29a6c7a04f51df9b2d02`。
- 功能合并点 `8f5ed2d`，初次整合受测源码 `d9bf606106e047f20d2e4599a6a4287872caa064`；唯一 review 后的修复受测源码为 `217783be608500b2bdc6076b62e50f60056a2734`。后续记录提交只修改文档，不改变受测代码／资源。
- 用户最新范围为先完成功能，视角效果统一后置，并禁止使用 computer use 在游戏中测试。本整合未启动或控制游戏客户端；已有模型、握持及VFX资产保留，其观感、音效听感和多人客户端表现没有验收结论。
- 正式选角仍为十一人。在新测试世界启用玩法与训练后，以 `/myriad char keheng` 或 `/myriad char yike` 显式进入。#28 的十一人私有时序、兵线、普通野怪、原版箭弹完整适配及十三人正式开放未执行。

## 实际整合

共享文件逐段保留两套接线：十三个登记角色／104个候选、两个成长表及专属武器；客户端初始化、图标、HUD、音效及中英文资源；VFX主题刻衡11／伊珂12、同步载荷边界与开发测试资源排除。

`AbilityRuntime` 保留刻衡来源清理、净化、普通减速取强和独立弹体空间采样，同时保留伊珂 `cooldownReady`、`cooldownTicks`、`windupWalkFactor`、`interruptPreparation` 的真实消费者。伊珂仍经公共 `projectileRate` 接缝取倍率，没有直接引用刻衡私有类。`SkillSystem` 的新版输入分派仍允许W和再次主要／终极输入到达准备取消逻辑。服务端普攻恢复仍挂在 `Player.tick`，没有退回覆盖不到玩家的 `LivingEntity.tick`。消除了两分支合并产生的重复开发角色拒绝条件。

## 初次整合实际结果（修复前时点）

JDK25：`/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`。Gradle通过既有 `gradle_serial.py` 和共享锁执行。原始证据目录 `/private/tmp/myriad-delivery/evidence-characters/` 为临时目录，可能随清理移除。

| 执行 | 实际结果 |
|---|---|
| `runKehengTestServer` | `keheng-combined-4.log`，退出0、`BUILD SUCCESSFUL`、GameTest 1/1；同一GameTest内38场景，其中原刻衡30场景和新增两角色组合8场景。 |
| `build runFirstGroupTestServer runYikeTestServer runGameTestServer`，公共任务限定 `myriad:combat_regression` | `final-functional-build.log`，退出0、`BUILD SUCCESSFUL in 15s`；新版聚合、伊珂、公共战斗回归各1/1。伊珂日志明确56个真实输入场景。XML分别位于工作树 `build/reports/first-group-gametest.xml`、`build/reports/yike-gametest.xml` 与证据目录 `combat-gametest.xml`。 |
| `python tools/check_namespace.py --jar build/libs/myriad-0.1.0.jar` | `namespace-final.log`，退出0，源码命名空间、包、JSON及发行JAR通过。 |
| `python tools/check_retired_roster.py --jar build/libs/myriad-0.1.0.jar` | `resources-final.log`，退出0；1306个源／工具／资产文件、1431个JAR条目，源树和JAR各271条自有模型／纹理／粒子／声音引用完整。 |
| 发行资源包含／排除 | 实际读取最终JAR：两个角色定义均存在；`test_instance/keheng.json`、`test_instance/yike.json`均不存在。 |
| 差异检查 | 保存前 `git diff --check`，提交前暂存差异空白检查退出0。 |

新版聚合按 `FirstGroupSkills.ROLES` 实际遍历13个登记角色并断言每人八候选、共104个独立实现；该检查的历史控制台摘要仍固定写“88 modules”，不能将这个旧摘要当成当前总登记数。正式十一人可选集仍由退役回归精确检查。

新增八个组合场景均施放真实七级迟界，通过原 `SkillSystem`／普攻入口驱动伊珂：

- μ=0.5下Q在十个真实tick后单次出膛付费；独立炮弹在施放者离开领域后仍按弹体当前位置取倍率、第二飞行tick命中。
- 固定炮台十八个真实tick完成展开，此后120真实tick到期；炮数逐发减少，22真实tick间隔期限正常推进，第二次慢起手完成前不补炮。
- 贯虹阵列二十四真实tick完成蓄势，此后仍在六十真实tick内完成六个十tick跳伤。
- 游走阵列十四真实tick展开，70%自身移动限制与50%外部行动限制同时保留；净化移除外部限制，固定十五tick免疫后重新受领域影响。
- W取消未出膛Q，只付W费用；预警在十一真实tick后完成。免费普攻的29行动tick恢复在半速领域中耗费58真实tick，早按不额外发弹。

## 失败与具体修复

- `build-combined-1.log`：沙箱无法写本机Gradle缓存锁，构建未启动；随后按权限流程使用指定JDK和缓存执行。
- `build-combined-2.log`：新增组合弹体场景的目标施放者尚未加入世界实体索引，查询不到目标。只将这八个夹具的目标加入测试世界，保留精确伤害／时点断言。该次新版聚合已通过，但整体任务失败，未记作成功。
- `keheng-combined-3.log`：新增姿态到期观察点在148tick，原测试实例140tick上限先超时。将该实例预算改为180tick，不改生产姿态时长或到期断言。随后38场景通过。
- 上述两处测试修复没有掩盖或删除生产断言，也没有引入新的测试平台。组合通过后只再执行了受最终作者测试提交影响的必要最终构建／聚合／伊珂／公共回归；未重复全部既有角色证据。

## 交付包与限制

初次整合包（已由下方修复包替代）：`myriad-0.1.0.jar`，25,840,849字节，SHA256 `c46332382f37c625cc3152ba47df5fec2ad2bb9e8190d04f675f800432675b7a`。不同阶段或单票JAR校验值不可混用。

单票原始功能及未变模型／音频源证据分别见 [刻衡记录](roster13-keheng11.md)、[伊珂记录](yike12.md)，其中可编辑模型／发行模型一致、图标存在与音频解码结果复用作者有效证据，不等同于画面或听感验收。所有游戏验证使用隔离测试世界，未加载正式存档、安装正式实例、推送、创建PR或合并main，未恢复已取消#4兼容。唯一综合review结果及必要修复如下，#28及后置视觉工作继续保留。


## 唯一综合 review 与薄墙 P2 修复

主流程的唯一综合 review 冻结于 `6bbea3c`，两票各计1/1。发现一项共享P2：中心线先截到薄实体墙，但迟针和贯虹仍以目标沿三轴膨胀后的AABB决定伤害；膨胀盒可越过铁栏／玻璃板，让实际体积完全位于墙后、贴后表面0.01格的目标被命中，迟针另施加缓时。报告为 `/private/tmp/myriad-delivery/review-characters.md`。本次没有要求审查者复审，也没有新增 review。

修复提交 `217783be608500b2bdc6076b62e50f60056a2734`：在既有 `AbilityTargeting` 保留原扫掠宽度候选，再把候选接触点钳到目标原始AABB表面，对射线起点至该实际体积接触点做实体碰撞遮挡确认。贯虹保留0.625格半宽；行动弹体保留调用者给出的半径，迟针仍为0.40格。普通旧 `projectile` 路径未扩改；行动弹体路径的现有生产消费者仅迟针与夜影匕首，后者及公共施法契约由新版聚合复测。没有改技能伤害、费用、射程、计时、控制时长或其他角色碰撞逻辑。

两角色各新增四个真实施法场景：连接成面的铁栏、玻璃板后贴墙目标，无遮挡接近半径边缘目标，以及整块石墙目标。迟针分别断言墙后零伤害且行动倍率仍为1；无遮挡目标实际体积最近边缘距中心线0.39格时，仍造成95伤害并附加0.85倍率。贯虹在两种薄墙与整墙后均零跳伤；无遮挡目标最近边缘距中心线0.60格时，仍承受六跳共465伤害。原有墙体／碰撞与所有角色专项断言保留。

| 执行 | 实际结果 |
|---|---|
| 修复前新增真实施法 | `thin-cover-before-fix.log`，退出1；铁栏后束流损血465、迟针损血95，直接复现审查缺陷，不是只检查常量或几何谓词。 |
| 修复后两角色专项 | `thin-cover-after-fix.log`：刻衡／组合42场景和伊珂60场景各GameTest 1/1通过，包含全部八个新增场景。该命令整体退出1，因为同次新版聚合的天刑原有12tick禁锢断言失败；未把整体命令记作成功。 |
| 受影响新版聚合单独复测 | `thin-cover-aggregate-diagnostic.log`，退出0、GameTest 1/1、`BUILD SUCCESSFUL`；夜影行动弹体、公共施法契约及天刑原12tick禁锢断言通过。诊断只临时补充失败值消息，未改生产逻辑或断言；最终提交撤下这段临时消息，天刑文件无差异。首次聚合失败触发原因尚未确认，不声称修复了天刑或测试夹具。 |
| 最终修复包构建 | `thin-cover-final-build.log`，退出0、`BUILD SUCCESSFUL`。两角色与聚合通过后只重建最终包，不增加无关复测循环；公共 `combat_regression` 的前次有效结果继续保留，未重跑。 |
| 最终包命名空间与资源 | `thin-cover-namespace.log`、`thin-cover-resources.log` 均退出0；1306个文件、1431个JAR条目，源树／JAR各271条自有资源引用完整。 |

最终修复包：`/private/tmp/myriad-delivery/characters/build/libs/myriad-0.1.0.jar`，25,843,154字节，SHA256 `f84d114baaeea37678431555349c4b42748cf75a29c6da51c8194ea30fbdad05`。上述日志均位于本记录既有证据目录；提交前差异／暂存空白检查通过。没有GUI／computer use游戏操作、客户端验收、正式存档加载或部署。此修复完成已确认的功能缺口，原视觉后置与#28边界不变。
