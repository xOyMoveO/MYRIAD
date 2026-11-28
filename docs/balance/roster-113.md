# 凌渊、寒泽、墨尘与云笙首轮数值对照

[#113](https://github.com/xOyMoveO/MYRIAD/issues/113) 使用樱隙前线 3v3 `CHERRY_V2`。改前取 #111 配对构建 `b368acd`。四名角色沿用正式的基础、逐级成长、普攻、蓝耗、冷却与效果系数。「霜寒领域」修正了被另一命中插入时同次施法仍能重复计层的行为，每次施法对同一目标最多增加一层。保留整套技能的伤害、叠层门槛、间隔与单目标免疫窗。

逐项理由与复跑场景见 [凌渊](../../tools/balance/scenarios/roster-113-lingyuan-decisions.json)、[寒泽](../../tools/balance/scenarios/roster-113-hanze-decisions.json)、[墨尘](../../tools/balance/scenarios/roster-113-xuanchen-decisions.json)和[云笙](../../tools/balance/scenarios/roster-113-yunsheng-decisions.json)。每份报告把 JAR 配对快照中的改前、一至二十级当前值、候选的各阶付费和效果并列。改前未导出的字段保留覆盖差异。

## 构筑与机制

- 凌渊在无凝剑的默认姿态跑八套分支。单独复跑显式蓄剑、退出剑域、留场全跳和蓄力落剑，逐次记录合法命中。
- 寒泽按每个目标、实际施法实例和有效直接命中计层。近间隔、同施法重复和免疫中的命中分别记录。持续领域脉冲作为派生伤害，只读实际伤害与移动减速。
- 墨尘分别完成与中断已付费引导，计算法阵范围内实际少掉的血与少用的盾。法阵的 12% 和 15% 减伤共享通道取强，真伤跳过该通道。阵护优先最低当前生命比例的合格队友，比较实际护盾消耗。
- 云笙按受援人分别记录有效治疗、溢出、同源刷新产生的新增盾、后续盾消耗和有效支援触发的护盾。低压席保留满血溢出、仅刷新及较少实际吸收的观察。

四名角色各八套 Lv20 终局组合。每套都从正面、满血满蓝、冷却就绪、零预置开始，在相同初态运行独自出手、队友先控、常规操作和零额外延迟。指标为 1.5 秒爆发、6 秒持续输出、净行动损失和有效盾疗。分支比较固定另外两层与 Q/W，减去停用被比较层的共同轨迹。区间描述声明式动作表可走到的极值。传送路径、追击和位移增益带来的额外命中按覆盖缺口登记。

[范围与受援输入](../../tools/balance/scenarios/combos/multi-target-113.json)沿用樱隙三组原型命中概率、范围档、集火权重和逐人留场刻。逐个比较 1／2／3 人生效及其未施放基线，队友先控在两轨均生效。敌方伤害有集火权重，净行动损失、有效治疗和吸盾按各人真实模型值相加。主受援、副受压和低承压席按固定血线和来伤观察，三人时由施法者作为第三席。合成概率为三级提醒输入。

普通移速减速在当前服务端为同技能同施法者刷新、不同来源乘算。该现行规则用于本组撤步及移动权重，和 #98 原型的取最低倍率假设存在差异。缓时与现有硬控按各自行动限制计算。

## 樱隙战场复跑

- 清线固定 3:00 和 8:00、真实小兵、力量购点、Q 加普攻和单人进战。首轮清波从 37.75 到 68.20 秒，四名角色整体超出真人参考区间。与 #109、#111 的清线观察方向一致。#114 需并列实际智慧构筑、完整轮转、友方兵线与共同波次伤害后再归因。
- 侦察机和重装步行机由四名角色各在 Lv1、500 金、一力量及一阶 Q 加普攻独立结算。首轮八次全部击杀。墨尘与云笙中营末血较低，可用同样起点复查多怪、跑位和法力投入。
- 带兵以 3:00／10:00／18:00 的外／中／高地三个层级分别观察每名英雄和兵线的实际塔伤。中塔为合成参照，正式樱隙采用外塔与高地塔。三类战场共用规则沿用现值，后续在全阵容固定位置与资源条件下复查。

## 复跑

使用 JDK 25。先验证项目配置的 Minecraft 26.3 共享资源。每个服务端 filter 独立 JVM 串行运行，报告保存该次实际匹配、完成、通过状态和 XML 用例。

```sh
python tools/fabric/verify-assets.py --prepare-loom
bash ./gradlew --offline build
python tools/fabric/prepare-ci-release.py
python tools/fabric/run-ci-gametests.py --offline \
  roster_a roster_b balance_haste balance_roster_sword_frost balance_mage_support_113 \
  balance_roster_113_lane balance_roster_113_camps balance_roster_113_towers

python tools/balance/replay-roster-113.py \
  --baseline "$ROSTER_111_RELEASE" --release build/ci-release \
  --engine build/reports/fabric-ci --output build/balance/roster-113
```

如有本票首轮原始证据，给离线命令追加 `--baseline-engine`，输入按 filter 保存 stdout 和 XML 的目录。对局地图路线、真人清线、全局站位概率、实战支援及完整对局体感另由人取样。

合并阵容后可另跑 `ink_cast_animation` 与 `first_group`。独立墨尘专项分别在奇偶游戏刻起手，记录每次引导推进的世界刻、阶段和出包，常速下于第 16 刻完成。完整聚合也保留相同的引导轨迹。
