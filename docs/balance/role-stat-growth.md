# 角色固有属性成长复跑

用同一份配对 JAR 与快照核对 15 人在 Lv1–20 的固有属性。等级新增的物攻计入额外物攻，等级新增的法强进入法术伤害、盾疗及四名法师的魔法弹普攻。完整契约见 [ADR 0012](../adr/0012-role-stat-growth-budget.md)。

## 构建与引擎回归

```sh
bash ./gradlew build
python tools/balance/check-baseline.py
python tools/fabric/prepare-ci-release.py
python tools/fabric/run-ci-gametests.py damage_growth balance_damage_support balance_haste \
  balance_control_supply balance_calibration_damage calibration_keheng calibration_qingluo \
  balance_roster_114_lane
```

`damage_growth` 检查全员逐级面板、等级切换、购点与专精保留、攻速封顶和实际命中。`balance_damage_support` 检查 Lv1／Lv20 的「鞘返」和「青藤玉露」，含购点与临时修饰。各项日志和 XML 保存在 `build/reports/fabric-ci/`，核对时以各过滤器的实际用例为准。

## 平衡复跑清单

准备一份改前配对发行，将其目录设为 `GROWTH_BASELINE`。固定场景版本、开战时点、等级、预算、已购点数、分支和目标初始生命。记录两版的 `sourceSha` 和 `rulesSha256`。

```sh
python .agents/skills/myriad-balance/scripts/diff_snapshots.py "$GROWTH_BASELINE" build/balance
python tools/balance/replay-roster-114.py --baseline "$GROWTH_BASELINE" \
  --release build/ci-release --output build/balance/roster
python tools/balance/replay-combat.py --baseline "$GROWTH_BASELINE/balance-snapshot.json" \
  --snapshot build/balance/balance-snapshot.json --logs build/reports/fabric-ci --output build/balance/combat
```

对改前配对发行再运行一次全阵容复算。比较同角色、同场景的 Lv4／Lv12／Lv20 爆发、持续输出及有效盾疗，结合 Lv10 标杆复查夜影、朔、素岚的收尾线。逐击轨迹记录模型内首个致死刻和观察窗结束时的剩余生命。观察窗内未出现击杀时，首个致死刻记为空。L3 模型沿用固定来伤与行动优先级，生命回复的实际整数脉冲由引擎观察。

| 樱隙前线标杆 | 复跑入口 | 记录 |
| --- | --- | --- |
| 落霞前五分钟推塔 | `balance_calibration_damage` | 一波总推塔比例、英雄与兵线各自贡献、距 15% 参考的差值 |
| 夜影 Lv10 绕背切落霞 | `balance_calibration_damage` | 首套与每次补 A 后的剩余生命、击杀时点 |
| 刻衡三阶领域跟进 | `calibration_keheng` | 领域内行动差、敌人离场时间和队友可完成的跟进次数 |
| 青萝 R+Q 稳血线 | `calibration_qingluo` | 固定来伤下的有效治疗、实际吸收、结束血线和反击伤害 |

按[全阵容与模式验证](roster-modes.md)复跑 3v3 账本及 5v5 经济。在 3v3 输出的 `defenseTrials` 中读取相应等级、可花金币、双抗与有效承伤。比较 Lv12／Lv20 同成本的一点防御，以及第 4／8 点专精提供的固定穿透 6／10。对固定原始伤害和相同目标双抗分别记录减免后伤害，再与零穿透和零购防御的轨迹对照。

数值偏离记录为平衡提醒。真人对局结论以同版实图反馈为准。
