# #11／#12 两角色功能整合验证

按主流程同步的 `function-11.md`、`function-12.md` 当前功能清单和 `parent-function-scope.md` 边界执行。#2 的后置视觉条款继续保留，不将本记录作为关闭父规格的依据。

2026-11-13，独立分支 `feat/roster13-characters`，工作目录 `/private/tmp/myriad-delivery/characters`。本记录只报告功能整合及实际自动化验证；两票综合 review 均仍为 0/1，由主流程随后统一安排一次。

## 来源与当前范围

- 基点 `8310a8b`；以 no-ff 保留刻衡 `e45e5403a226223eb4338a48ba2c3b2ed3bb9453`、伊珂 `90b53b14ca7d378d4812aac8a3eb4916e008b947` 的完整来历，包含伊珂炮台父提交 `c30086f`。
- 后续完整合入伊珂测试／文档 `f48990007d059da494df925898527acc9ae8303c`、`5ad5b079fcb73849ab5356cc9a4c24c34aeea555`，以及刻衡文档 `ab4eb1ea13834c6613ec29a6c7a04f51df9b2d02`。
- 功能合并点 `8f5ed2d`，最终受测源码 `d9bf606106e047f20d2e4599a6a4287872caa064`。本记录及入口说明随后只修改文档，不改变受测代码／资源。
- 用户最新范围为先完成功能，视角效果统一后置，并禁止使用 computer use 在游戏中测试。本整合未启动或控制游戏客户端；已有模型、握持及VFX资产保留，其观感、音效听感和多人客户端表现没有验收结论。
- 正式选角仍为十一人。在新测试世界启用玩法与训练后，以 `/myriad char keheng` 或 `/myriad char yike` 显式进入。#28 的十一人私有时序、兵线、普通野怪、原版箭弹完整适配及十三人正式开放未执行。

## 实际整合

共享文件逐段保留两套接线：十三个登记角色／104个候选、两个成长表及专属武器；客户端初始化、图标、HUD、音效及中英文资源；VFX主题刻衡11／伊珂12、同步载荷边界与开发测试资源排除。

`AbilityRuntime` 保留刻衡来源清理、净化、普通减速取强和独立弹体空间采样，同时保留伊珂 `cooldownReady`、`cooldownTicks`、`windupWalkFactor`、`interruptPreparation` 的真实消费者。伊珂仍经公共 `projectileRate` 接缝取倍率，没有直接引用刻衡私有类。`SkillSystem` 的新版输入分派仍允许W和再次主要／终极输入到达准备取消逻辑。服务端普攻恢复仍挂在 `Player.tick`，没有退回覆盖不到玩家的 `LivingEntity.tick`。消除了两分支合并产生的重复开发角色拒绝条件。

## 实际结果

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

最终包：`/private/tmp/myriad-delivery/characters/build/libs/myriad-0.1.0.jar`，25,840,849字节，SHA256 `c46332382f37c625cc3152ba47df5fec2ad2bb9e8190d04f675f800432675b7a`。两作者单票JAR校验值不能套用本包。

单票原始功能及未变模型／音频源证据分别见 [刻衡记录](roster13-keheng11.md)、[伊珂记录](yike12.md)，其中可编辑模型／发行模型一致、图标存在与音频解码结果复用作者有效证据，不等同于画面或听感验收。所有游戏验证使用隔离测试世界，未加载正式存档、安装正式实例、推送、创建PR或合并main，未恢复已取消#4兼容。当前无已知功能整合阻碍；唯一综合review待主流程安排，#28及后置视觉工作继续保留。
