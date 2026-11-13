# #28 战场局部行动适配：兵线、野怪与原版箭

日期：2026-11-13。基点 `c12e004`，独立工作分支 `feat/roster13-world-time28`。本记录只覆盖战场职责；最终十三人整合、统一检查与唯一 review 由主流程完成。

## 实际实现

- `LaneSystem.tickMinion` 与 `MonsterManager.tickMonster` 原先按 `now - lastAttackTick` 判断攻击；现在每个单位保存已完成恢复量，每次实际更新推进当前局部倍率，完成后仅结算一次。进出领域、迟针到期和来源清理均不重置已完成比例，不补算错过的攻击。正常存档快照保存并恢复该进度。
- 兵线和野怪实际使用 NoAI 加 `NpcNavigation.move` 的私有运动，单改移动属性不能覆盖该路径。缓时接在自主步长处，与已有普通减速取更强限制；不缩放实体重力、外部击退或分离碰撞修正。寻路/仇恨扫描、回血、刷新和其他真实时间截止仍使用原有时钟。
- `KehengTimeFields.managedNpcRate` 复用真实领域与现有 `NEEDLES`，支持四类兵线、六种小型及三种中型野怪；友方兵线豁免。四种大型首领和设施不进入行动缓时。免缓时没有放进伤害过滤器。
- 真实迟针测试发现兵线使用 `LaneSystem.health` 独立生命池，原版代理 HP 不因非致命伤害下降。修正刻衡的命中有效性读取后，真实 Q 对兵线扣血时可正确施加迟针；野怪仍读取 `MonsterManager.modHealth`，护盾吸收仍计有效伤害。
- 原版箭仅支持 `minecraft:arrow`（普通箭与药箭）和 `minecraft:spectral_arrow`。不纳入三叉戟、其他原版弹体或第三方任意弹体。以真实实体 owner 判敌我，ownerless 箭不虚构敌方来源；射手受迟针或首领免缓时不传递给已发箭。仍有 owner 引用的独立箭不会因射手死亡而丢失原敌我关系。
- `ArrowActionTimeMixin` 对原版飞行步进行分段推进，保留未完成步。位移沿原版步的轨迹分段扫掠，阻力与重力在原来的局部步边界推进；碰撞继续经过原版 `stepMoveAndHit`、实体命中、贯穿/偏转与命中效果链。真实物理速度没有按倍率压低，故原版按速度计算的箭伤害不会误降。出场恢复当前进度，不额外补飞行距离。
- 只需同步当前箭倍率；通过 NeoForge 已有同步 attachment `myriad:arrow_action_rate` 随实体跟踪同步，没有新增自定义网络包。飞行进度与寿命随箭实体保存。世界 tick、玩家重力和其他实体时钟不变。

## 原版依据与明确新增的寿命基线

读取了本机实际编译依赖的 Minecraft **26.2** / NeoForge **26.2.0.59** 源码：`net.minecraft.world.entity.projectile.arrow.AbstractArrow`、`Arrow`、`SpectralArrow`、`ThrownTrident`、`Projectile`。提取来源为现有 NeoForm `transformSources_8bc758a3143f700c456f90b284b24710253952b5_output.zip`，源码摘出保存在本轮外部证据目录，未复制进发行源码。

实际 `AbstractArrow.tick()` 在飞行分支处理当前位移、碰撞、空气阻力 `0.99` / 水阻力 `0.6` 与重力 `0.05`；`onHitEntity` 用真实 `getDeltaMovement().length()` 计算伤害。它**没有固定飞行最大射程或空中飞行寿命**。`life` 由扎地分支 `tickDespawn()` 累计，达到 **1200** 后清理，不是原版空中 TTL。

按本轮主流程确认并同步父规格的决定，玩法启用期间为上述支持箭**新增**空中 **1200 行动 tick** 上限和 **2400 真实 tick** 安全上限。前者按实际飞行倍率推进，后者不减慢；达到任一上限消散。扎地仍按原版 1200 tick，不延长。这是为缓时清理责任新增的 MYRIAD 基线，不冒称原版已有参数。没有虚构原版最大射程阈值；以保持原物理轨迹进度与逐步碰撞避免缓时增加射程。倍率 1 保留普通物理/伤害行为，长时间悬空清理是上述明确新增例外。

## 本轮实际验证

命令：

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
python /private/tmp/myriad-implementation/gradle_serial.py \
  --worktree /private/tmp/myriad-delivery/world28 \
  --log /private/tmp/myriad-delivery/evidence-world28/world-time-12.log \
  -- runWorldTimeTestServer
```

结果：**BUILD SUCCESSFUL，EXIT_CODE=0，GameTest 1/1 通过**。这是一个聚合功能测试，不是“仅有一个断言”。新建开发测试世界，不使用正式存档或外部竞技场地图。

| 真实行为覆盖 | 实际结果 |
|---|---|
| 近战、远程、法术、攻城四类兵线；九种普通/中型野怪 | 真实私有 AI 自主步长、领域减半、普通减速取强、领域叠加、来源清理、攻击恢复的中途入场/出场及恰好一次结算通过 |
| 四种大型首领 | 实体真实技能伤害生效、领域缓时豁免通过；另用真实 Q 验证青角首领命中后仍保持正常自主运动 |
| 真实 Q 起手与飞行，命中兵线、普通野怪、大型首领 | 服务端自然 tick 推进后，实际生命下降、针源倍率、私有移动/攻击恢复与清理通过；已发原版箭不继承射手针源 |
| 普通箭与光灵箭物理 | 倍率 0.5 的两个真实推进与倍率 1 的一个原版物理步位置/速度一致；半步进出保留比例 |
| 普通、药箭、光灵箭真实命中 | 普通箭伤害保持，药箭中毒与光灵箭发光仍由原版命中链产生 |
| 坡地、空中、水中、薄墙和真实跨领域边界 | 原版碰撞终点、水/空气阻力、重力、逐步碰撞及恢复无补飞行通过 |
| 自身/敌方/无 owner、多来源与死亡归属 | 友方豁免、多个 owner 取最小倍率、清理一个来源不清掉另一来源、射手死亡后已有归属仍正确通过 |
| 箭状态同步 | 服务端实际持有已登记的同步 attachment，敌箭为 0.5、友箭为 1；无 GUI 或客户端画面验收 |
| 飞行及扎地寿命 | 正常 1200 行动 tick、0.7 倍率下 1715 次推进达到行动上限、0.4 倍率 2400 真实 tick 安全清理、原版扎地 1200 tick 通过 |

兵线/野怪的确定性对照直接调用真实私有 AI 更新并控制输入与 tick 序列；箭使用真实 `Arrow` / `SpectralArrow.tick()`、实际世界方块和已加入世界的命中目标。迟针三类场景通过正式 `SkillSystem.cast`、真实四步起手和真实独立飞行，在服务端自然 tick 中验证。没有写入最终恢复量、命中结果或计算结果冒充行为验证。

证据目录：`/private/tmp/myriad-delivery/evidence-world28/`；最终日志 `world-time-12.log`，最终 XML `world_time-gametest.xml`。早期失败原样保留：一次 fixture 方法名遮蔽编译错误；NeoForge 明确拒绝修改外来实体的 SynchedEntityData，后改用官方同步 attachment；真实迟针揭示独立 HP 读取遗漏；异步 fixture 先后遇到启动兵线清理、真实脱战/仇恨与越出地板触发绕路，均调整输入布置而没有放宽生产逻辑或命中/恢复断言。

本轮未做客户端画面、视角、模型/VFX、音效听感验收；未运行缺少外部 fixture 的竞技场检查；未正式部署、加载正式存档、推送或合并 main。未自行增加 review，也未重复最终整合方负责的全套构建/十三人聚合。
