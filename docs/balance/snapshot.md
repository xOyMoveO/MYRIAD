# 现行平衡快照

`./gradlew build` 和 `./gradlew exportBalanceSnapshot` 共用导出入口。产物在 `build/balance/`，包括 `balance-snapshot.json`、逐字段覆盖报告 `balance-coverage.json` 和摘要 `balance-coverage.md`。发行时运行 `python tools/fabric/prepare-ci-release.py`。脚本验证 JAR 内绑定的快照 SHA-256，将 JAR 和快照并列写入 `build/ci-release/`，再按 `release.json` 回读两份文件。

## 读取规则

- `formatVersion` 表示结构与单位版本。版本 2 导出技能急速和购点契约，盾疗字段以 `scaling` 和 `scale` 表示指定的属性与系数。版本 1 的 `spellScale` 表示总法强系数。`sourceSha` 标明构建时检出的提交。`sourceDirty` 标明该工作区还有未提交文件。源码归档可正常构建，未带 Git 元数据时两值分别为 `unknown` 和 `null`。
- `rulesSha256` 对 `rules` 的紧凑 UTF-8 JSON 求摘要。对象键按字典序，数组保留其规则顺序。Git 信息、素材资源和文案提醒在摘要范围之外。未导出的机制应结合 `sourceSha`、成品摘要和覆盖报告追查。
- `rules.roles` 通过角色表和已绑定技能生成。每个角色含从一级开始的属性、普攻、分支组合和候选技能。技能的 `ranks` 记录实际调用的逐阶蓝耗与基础冷却。位置无关的效果标识位于 `effects[].id`，取值、本游戏版本的单位、规则来源、条件、取值时点和时基位于 `effects[].rule`。炮台姿态等效果内可以再带分字段。距离和时长统一写作 `blocks`、`ticks`，时基分别为 `real_tick`、`action_tick` 或 `not_timed`。
- `defined` 与 `zero` 都是可读定义，`zero` 表示该字段的合法数字零。`not_applicable` 和 `missing_input` 使用空值并带理由。伤害公式与名义盾疗可以独立取数。连续有效命中、实际存活、多个技能段能否相加，以及缺条件的几何、触发与 Buff 交互须按场景验证。
- `rules.battlefield` 包含真实规则定义与樱隙前线 3v3 已提交的地图配置。5v5 使用部署时的世界设施及营地配置，未提供的点位在报告中列为缺输入。野怪成长、塔保护、赏金等有明确场景的读数按其输入取样。小兵先按地图的 `tempoProfile` 读取 `minions.types.<兵种>.byTempo` 中对应的基础赏金与时间轴。原有 `baseGold`、`growthSamples` 和 `rewardSamplesBeforeCatchUpAndGlobalGold` 保留旧节奏样本。野怪首刷的各版本生命、攻击与零穿透有效生命见 `monsters.types.<种类>.combatStatsByTempo`。
- 覆盖报告包含刻衡两条[历史中文长说明](../../tools/balance/archived/zh_cn.json)的成长系数差异。提醒指出技能、语言和字段。游戏内「刻痕回响」按生效公式生成成长系数。

## 改前样本

样本对应 `3cfec8e`，该提交与玩法基线 `bae9d06` 在 `src/main/` 及 `maps/` 的内容相同。

| 场景 | 改前读数 |
| --- | --- |
| 落霞无购买，一级／二十级 | 575／2,000 生命，普通弓基础伤害 100 |
| 落霞一点残阳 | 射程 28 格，提交前起手 10 行动刻 |
| 伊珂虹轨弹一阶，正常付费模式 | 普通形态冷却 120 真实刻。定轨／游走当发冷却为零，出膛后下一发间隔 22／27 真实刻，初始 5／4 弹 |
| 刻衡刻痕回响、迟界 | 每阶伤害成长 14.4／22.5，旧中文图鉴为 12／18 |
| 刻衡三阶一秒之外 | 领域 60 真实刻，圈内适用敌方单位行动倍率 0.25 |
| 铆钉战术锚点 | 零次付费强化时可用，基础冷却 160 真实刻 |
| 樱隙前线 3v3 | 首波 700 真实刻，13 个营地配置点，中路部署一、三塔 |
| 对局基础经济 | 初始金 500，精确一次 |

复跑命令使用 JDK 25，在仓库根目录执行。发行阶段需在 Git 检出中运行。

```sh
./gradlew build
python tools/balance/check-baseline.py
python tools/fabric/prepare-ci-release.py
./gradlew exportBalanceSnapshot --rerun
./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:damage_growth
```

基线脚本保留该批的独立期望，故意调数后读数差异可单独评估。正常 Gradle 构建不运行这份基线脚本。引擎专项沿用 `FirstTenDamageFormulaChecks` 和 `LastFiveDamageFormulaChecks` 的独立期望，实际用例可从 `build/reports/fabric-gametest.xml` 读回。

## 公共战斗规则对照

[第 3 批专项与离线复算](combat-rules.md)对照两版快照、玩家说明和服务端事件。

## 落霞与伊珂纵向对照

[两个复跑场景](vertical-scenarios.md)说明如何用同一配对快照、服务端 GameTest 事件和客户端技能介绍卡核对射程及炮台条件值。

## 购物与对局经济

[第 4 批服务端结算与复跑入口](economy-runtime.md)说明分项购物资格、助攻候选池、开局锁定席位及阵亡复活规则。
