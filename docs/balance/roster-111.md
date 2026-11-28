# 伊珂、朔、铆钉与赤霄首轮数值对照

[#111](https://github.com/xOyMoveO/MYRIAD/issues/111) 采用樱隙前线 3v3 的 `CHERRY_V2`。改前取 #110 完成时的配对构建 `fee6c1c`。四名角色的逐级属性、普攻、各阶成本、效果和触发条件来自构建快照。新登记的效果在改前快照里可能没有对应字段，逐项报告保留该记录差异。查值由实际技能和角色登记导出，介绍卡读取相同声明。逐项使用的理由与复跑场景见 [伊珂](../../tools/balance/scenarios/roster-111-yike-decisions.json)、[朔](../../tools/balance/scenarios/roster-111-shuo-decisions.json)、[铆钉](../../tools/balance/scenarios/roster-111-rivet-decisions.json)与[赤霄](../../tools/balance/scenarios/roster-111-chixiao-decisions.json)。

## 构筑与条件

| 角色 | 零预置起点与独立抽检 |
| --- | --- |
| 伊珂 | 普通姿态开始。炮台单发扣费、当发零冷却、真实刻重复出膛及退姿态后急速对应的普通冷却分别记时。独立专项覆盖 5／4 发、22／27 真实刻和打断。 |
| 朔 | 收鞘、零刀势开始。对攻另注入固定来伤，分别观察成功格挡反击命中、没格挡与反击空刀的伤害、控制和盾。 |
| 铆钉 | 清地起手无锚点。独立专项同一墙体验证有锚与无锚，另从 W 零付费强化起点取各阶冷却与有效布点。固守据实际伤害来源登记减伤。 |
| 赤霄 | 无蓄势、常态起手。独立专项对照开阔与墙阻时追魂踏移动的实际位置、伤害与控制，记录回程和后续普攻附伤归属。 |

[角色场景](../../tools/balance/scenarios/combos/) 分别声明距离、实际可买的配点、动作优先级和独立优势场景。每个角色八套 Lv20 构筑，均按相同无预热初态分别测试独自出手和队友先控，再跑常规操作与零额外延迟。四项指标为 1.5 秒爆发、6 秒伤害、净行动损失、有效盾疗。报告保留每条轨迹和产生极值的构筑。同层按另外两层一致的四种搭配比较，保留 A、B 与临时停用该层的共同轨迹。离线结果适用场景中声明的命中与几何条件，上下界和分支差值的有效范围随报告注明。待复测的三维路径、站位、来伤与地形按受影响指标记录。

## 固定战场样本

- 3:00、8:00 的清线探针使用实际生成小兵。伊珂沿用 #109 冻结构筑和连招。朔、铆钉、赤霄用各阶段相同的预算、力量和 Q／普攻优先序。锁定定点簇集，并在主动位移结束后回到可攻击初始点。实图路线、不同构筑和己方兵线贡献需要新取样。
- 侦察机、重装步行机由四名英雄各自用 Lv1、500 金预算、一力量、一阶 Q 加普攻独立重放。记录实际还击、掉血、尾刀归属、掉落及刷新截止。多人、整营路线和首领历史专项见 [战场候选与复跑](battlefield-scenarios.md)。
- 带兵推进固定 3:00／10:00／18:00 和外塔／中塔／高地塔，记一波真实兵线加单名英雄的贡献。伊珂发射普通炮弹，现行规则跳过防御塔，个人塔伤为零。#114 应与全阵容其它推塔路径并排复核。中塔在合成地图用来取样，正式樱隙前线为外塔与高地塔。

## 复跑

使用 JDK 25 和仓库配置的共享资源目录。每个 filter 都在独立 Fabric 服务端 JVM 串行执行，保留一份对应日志与 XML。

```sh
bash ./gradlew build
python tools/fabric/prepare-ci-release.py
python tools/fabric/run-ci-gametests.py \
  first_group balance_vertical balance_haste yike balance_shuo_chixiao rivet \
  balance_roster_lane balance_roster_camps balance_roster_towers

python tools/balance/replay-roster-111.py \
  --baseline "$ROSTER_BASELINE" --release build/ci-release \
  --engine build/reports/fabric-ci --output build/balance/roster-111
```

`ROSTER_BASELINE` 指向 #110 正式保留选择后的发行目录，内含 `release.json`、JAR 与配对快照。合成 GameTest 承担服务端规则检查，真人操作、实图推进、完整对局和跨全阵容的平衡判断另采。平衡数值差异只作提醒。
