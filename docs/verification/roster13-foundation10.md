# #10 公共施法机制与十名退役角色：整合验证记录

2026-11-13，对应父规格 #2、实施工单 #10。覆盖公共契约扩展、十人退役和现有十一人基线；刻衡、伊珂及全阵容缓时迁移仍分别属于 #11、#12、#28。

## 整合来源与执行边界

- 检出基点：`808d53eabbdca921a7527a3acb49505d0ff1b8a3`；分支：`feat/roster13-foundation10`。
- 公共运行时来源：`c1a81b6bb40f7761b033449471b7249fc53656bd`；退役来源：`1436316bbf5e119b0e50435ebcdf7c21b9cdd689`。以 no-ff 合并保留来历，整合点 `98fd3f835e235176e9fe757490b7805c8d103b8f`，聚合入口同时保留两组新增检查。
- 后续整合修复仅修改公共战斗测试的塔位与时间夹具；未修改生产兵线、攻城解锁或经济规则。
- Java `25.0.3`：`/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；Gradle Wrapper `9.7.0`。命令设置该 `JAVA_HOME`，串行使用既有缓存与 `/private/tmp/myriad-implementation/gradle.lock`。
- 工作目录 `/private/tmp/myriad-delivery/foundation10`。第一次测试前已实际确认其中没有 `run-first-group-test` 或 `run`；各次公共战斗测试另用新的 universe 名称。未加载正式世界，未安装正式实例，未合并或推送 main。
- 未集成被取消 #4 的 `bb4cd63`，不构造退役玩家历史状态或检查点兼容场景；有效角色保存、正常输入拒绝及游戏内生命周期仍验证。

## 实际执行与证据索引

临时原始证据目录：`/private/tmp/myriad-delivery/evidence-foundation10/`。临时目录可能被清理；下列结果为本次实际观察事实，不代表之后的源码自动通过。

| 编号 | 实际执行 | 结果与证据 |
|---|---|---|
| E1 | `bash ./gradlew --no-daemon --console=plain --offline build runFirstGroupTestServer` | 退出 0，`BUILD SUCCESSFUL in 17s`；`build-first-group.log`，`first-group-gametest.xml`：1 项、0 失败。 |
| E2 | E1 中的 `myriad:first_group` 聚合专项 | 新增 26 个施法契约场景及退役输入检查完成，既有十一人专项完成；不是 26 项独立 GameTest。日志同时出现 `MYRIAD-ABILITY-CONTRACT`、`MYRIAD-ROSTER-RETIREMENT`、`MYRIAD-G1-G2`、`MYRIAD-SHARED-CONTROL-EXPIRY`、`MYRIAD-SHARED-WALL-LIFECYCLE`、`MYRIAD-G2-EXECUTION` 的 PASS 标记及 `All 1 required tests passed`。 |
| E3 | 修复夹具后 `build runGameTestServer`，仅选择 `myriad:combat_regression` | 退出 0，`BUILD SUCCESSFUL in 7s`；`combat-regression.log`、`combat-gametest.xml`：1 项、0 失败，`All 1 required tests passed`。最终 universe 为 `myriad-issue10-combat-clock-api-20261113`。 |
| E4 | `python tools/check_retired_roster.py --jar build/libs/myriad-0.1.0.jar` | 退出 0；`retired-roster-audit.log`。扫描 1191 个源／工具／素材文件、1311 个 JAR 文件条目；源树与 JAR 各 236 条自有模型／纹理／粒子／声音引用均有目标文件。 |
| E5 | `python tools/check_namespace.py --jar build/libs/myriad-0.1.0.jar` | 退出 0；`namespace-audit.log`。命名空间、Java 包、JSON、入口、模组标识与开发测试资源排除检查通过。 |
| E6 | 对比 `808d53e` 的 6 个 `META-INF/licenses/` 文件与最终 JAR | 逐文件读取并比较字节，6/6 一致；公共商店装备无相剑匣、野怪玄甲地龟及原版钻石剑保留。 |
| E7 | `git diff --check`，并同步当前说明与玩家日志 | 退出 0；当前十一人说明见 `README.md`、`docs/协作开发与内部命名.md`，玩家变更见 `CHANGELOG.md`。 |

E3 的完整复现命令（同样设置 JDK 25 环境，在隔离检出目录使用新的 universe）：

```sh
bash ./gradlew --no-daemon --console=plain --offline build runGameTestServer '--args=net.neoforged.fml.startup.GameTestServer --tests myriad:combat_regression --universe myriad-issue10-combat-clock-api-20261113 --report /private/tmp/myriad-delivery/evidence-foundation10/combat-gametest.xml'
```

E1 后仅有测试夹具修改及文档修改；E3 实际重新构建了最终 JAR。公共施法、选角、角色行为和 E2 的聚合接线未在 E1 后改动，因此未为这两处测试夹具修复重复运行整套新版专项。

## 二十一项验收对应结果

“通过”限定为下列已实际运行的服务端行为与静态／发行审计；不包含客户端实机表现或 #28 的全员缓时适配。

| 序号 | #10 阶段与验收范围 | 实际证据与结果 |
|---|---|---|
| 1 | 阶段 1：兼容旧调用、默认 μ=1、只扩展已识别行动 | E1/E2 通过。真实 `SkillSystem` 原入口仍即时提交；既有十一人聚合行为保持，旧 `later/repeat/dash/projectile` 入口保留。 |
| 2 | 阶段 1：请求／起手／单次提交／完成／取消；提交复核资源与合法性 | E2 通过。未提交起手、连点拒绝、蓝量不足、冷却变化、分支变化、失效落点、取消后旧句柄重放、真实护盾与成功施放被动各执行一次均有行为断言。 |
| 3 | 阶段 1：来源聚合、已完成进度、真实时间与控制／清理 | E2 通过。多来源最小倍率、移除与到期、剩余进度、真实 DOT／预警／CD、禁锢允许原地施法、眩晕／沉默／强制位移取消、死亡／换角／退出／跨维度／重置入口／关闭玩法清理均执行。 |
| 4 | 阶段 1：真实入口和代表性起手／位移／弹体 | E2 通过。天刑带起手的真实施法伤害、夜影冲刺实际移动／命中、夜影原入口匕首命中、独立弹体倍率与两倍真实寿命清理均执行；同 tick 重复推进不能产生额外进度。 |
| 5 | 阶段 1：仅迁移代表性场景，角色私有适配留给 #28 | 通过范围核对。本票仅代表性接线天刑起手、夜影冲刺／匕首；未统一减慢所有延迟任务，未声称十一人私有时序全部适配。 |
| 6 | 阶段 2：离歌／沈砺专属登记、实现、输入、资产与工具删除 | E4/E5 通过，覆盖 Java、注册器、界面、网络、工具、专属物品及发行资产的名称与引用；枪瞄准消息、专属枪／弓入口和工具已删除。 |
| 7 | 阶段 2：逐 ID 拒绝选角／命令／伪造消息；共享远程输入正常 | E2 通过。十个退役 ID 逐个经过实际选角消息编解码、服务端 handler、`/myriad char` 命令及非法技能槽位，角色／武器／资源不改变；四名法师实际武器弹体伤害与重复输入恢复限制通过。 |
| 8 | 阶段 2：共有伤害／远程接触断言、共享声音和许可 | E1—E6 通过。共有伤害、实际近战与远程接触检查保留；资源引用完整，6 份许可字节未变。 |
| 9 | 阶段 3：明烛／折川完整删除 | E4/E5 通过；专属模块、束流／印记状态、粒子、声音、武器、HUD 和资产无发行引用残留。 |
| 10 | 阶段 3：有限转向、束线、视野与生命周期公共断言保留 | E2/E3 通过。`SharedControlChecks.verifyGeometry/verifyCombatContracts` 的有限转向、束线截墙、视野边界、共享伤害等断言继续运行；来源控制与普通清理仍验证。 |
| 11 | 阶段 3：逐 ID 拒绝，专属引导／印记无入口，公共位移／清理正常 | E2/E4 通过。逐 ID 拒绝与无武器 fallback；公共位移扫掠碰撞、终点、清理及起手中断检查完成。 |
| 12 | 阶段 4：观澜／洛衡／玄甲完整删除 | E4/E5 通过；专属技能、状态、公共层专属分支、物品、表现与工具删除。共享野怪“玄甲地龟”按完整名称精确保留。 |
| 13 | 阶段 4：逐 ID 拒绝选角／命令／消息／武器，非法 ID 不重映射 | E2 通过，逐 ID 检查与普通空 ID／未知 ID 拒绝均完成，`initialWeaponItem` 返回 AIR，既有合法角色与武器保持。 |
| 14 | 阶段 4：共有伤害／护盾／控制／视野／经济保持 | E1—E3 通过；真实伤害、来源护盾、风墙／冻结／沉默／净化、视野边界、属性购买预算与公共经济回归完成。 |
| 15 | 阶段 5：无相／绯羽／绾月完整删除，保留原版钻石剑 | E2/E4/E5 通过；退役 ID 无登记、候选与专属武器，钻石剑物品仍在测试玩家背包；公共商店无相剑匣不是退役专属物品。 |
| 16 | 阶段 5：解除绯羽弹体拦截与退役招架，公共普攻／受伤／清理正常 | E2—E4 通过；保留夜影、云笙历史类与 `MagicBullet` 的绯羽编译依赖已解除；实际原版近战、四名法师弹体、NPC／塔伤害及生命周期链运行。 |
| 17 | 阶段 5：共有近战／重置断言保留，逐 ID 无专属可达能力 | E1—E4 通过。近战真实 `Player.attack`、失效自定义消息拒绝、主机会话重置与有效角色持久化完成；只服务退役专属机制的断言随模块删除。 |
| 18 | 阶段 6：收束路由、字段、工具和桥，保留历史／许可／拒绝记录 | E4/E6 通过；退役词只在审计词表和拒绝断言中被明确允许。无额外生产退役兼容表；现有公共冷却 API／状态保持范围内原样。 |
| 19 | 阶段 6：登记／界面／命令／消息／状态／最终资源完整检查 | E2/E4/E5 通过；实际十一人选角及 88 候选、十人逐 ID 输入测试与源树／JAR 全路径文本检查相结合，不以枚举数量替代删除审计。 |
| 20 | 阶段 6：十一人风墙／净化／伤害／经济／重置／资源／输入回归 | E1—E6 通过；聚合中的实际风墙移动／边界／箭弹拦截／到期、沉默冻结到期、真实技能持续伤害、普通近远程输入、有效保存、重置、经济及资源链均完成。 |
| 21 | 阶段 6：角色说明、玩家日志与实际验证记录同步 | E7 完成；角色说明为十一人／88 候选，玩家日志区分本票与后续新角色／缓时交付，本记录保存实际失败、修复和成功结果及 JAR 校验值。 |

## 公共战斗回归的失败与修复记录

原始 `combat_regression` 在角色平衡断言处失败的事实继续保留于 [baseline-combat-check.md](baseline-combat-check.md)。本次删除纯退役角色的 `CharacterBalanceChecks`、`MingZhuRegressionChecks`、绯羽终结／无相专属机制检查；有限转向、束线、视野等有效共有断言由共享检查继续覆盖。有效保存和训练样例从折川迁至夜影，其余公共链断言保留。

本次实际运行至此前被基线失败挡住的后续断言时，先后观察到两处测试准备遗漏。静态对照 `808d53e` 可见同样的准备代码和生产前提已存在；未为此重跑原始基线，也不声称原始基线后续断言曾执行成功。

1. 首次失败：`Siege unlock must be faction and lane scoped on tick 28`。前一段导航测试清空了塔位；攻城测试只写“已摧毁”ID，而生产判定同时要求塔位存在。补齐双方三路三阶的合成塔位，攻城与水晶段各自配置并在段末清理，保留全部解锁／阵营／路别断言。证据：`combat-regression-initial.log`、`combat-gametest-initial.xml`。
2. 塔位修复后的失败：`Two minutes increases last-hit, shared gold, and shared experience rewards on tick 28`。新测试世界不足四分钟，将开局设为 `now-2400/4800` 得到负数，按既有规则代表尚未开局。仅在三条奖励检查中临时设置非负的世界时间，并在 `finally` 恢复时间和开局值；110／27／88／120 的奖励断言不变。证据：`combat-regression-tower-fixture.log`、`combat-gametest-tower-fixture.xml`。
3. 时间夹具修复期间，一次编译因把 setter 放在 `ServerLevel` 上失败，游戏任务没有执行；核对当前 Minecraft 类后改用 `ServerLevelData.setGameTime`。编译失败日志：`combat-clock-api-compile.log`。最终重建及实际游戏回归通过，证据 E3。

## 最终构建包

- 路径：`/private/tmp/myriad-delivery/foundation10/build/libs/myriad-0.1.0.jar`。
- 大小：25,495,830 字节。
- SHA256：`8dcb867f1651d1078e149c4e08601469df54a980ca0ba7a6b7d5c5c01b9440ad`。
- 已通过 E4/E5/E6；未安装正式实例。后续重新构建若文件变化，应重新核对本校验值，不能将它套用于其他包。

## 仍未验收的范围

- 竞技场任务配置的外部地图 fixture `E:/mod/maps/combined-reference-3v3-campgrounds-20261004-v4/Sect_Rift_25x25_Ready` 在本机不存在，实际未运行 `runArenaTestServer`。
- 客户端第一／第三人称画面、音效观感与多客户端同步未实机验收。服务器资源和几何检查不能替代这些结果。
- 刻衡／伊珂、最终十三人／104 候选、全部角色私有起手／位移／弹体与战场缓时适配未由本票交付；当前正式可选仍为十一人／88 候选。
- 正式部署、旧世界迁移与退役玩家状态／检查点兼容未执行；后者按已取消的 #4 排除。
- 本次为整合与验证工作，没有自行启动独立代码 review；本票唯一综合 review 由主流程另外安排。
