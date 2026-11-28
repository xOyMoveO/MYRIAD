# 场景选择与复算入口

## 按玩家问题固定对照条件

| 玩家问题 | 固定条件 | 观察量 |
| --- | --- | --- |
| 一套技能能否收尾 | 模式、对局时刻、双方等级与可花金币、属性购点与专精、分支、朝向、距目标距离、起手资源 | 从第一次输入到命中逐刻记账，报告首套末和每次补 A 后的剩余生命 |
| 四项极值由哪种分支产生 | 零预置下的八套合法终局组合，另列绕背、叠层、靠墙或受击等专属前提 | 分别找 1.5 秒爆发、6 秒平均输出、6 秒有效盾疗与控制覆盖的极值，列出各自组合 |
| 支援或范围效果值多少 | 同一攻击序列、队友先控和地形，一次加入、一次移除待估技能 | 实际吸收、有效补血、净多出的命中或控制；1／2／3 名英雄分别报收益，再按条件命中率求期望 |
| 这套配点何时买得到 | 收入与购买的实际时刻、逐项报价（含时段切换或专精解锁点）、补刀／团队赏金归属 | 终点预算下的最优配点，以及沿时间轴能真正购得的点数、下一点缺金和买到的时间 |
| 3v3 / 5v5 发育节奏如何 | 同一套英雄定义，分别取单人路、野区游走、双人路或支援位及灵脉控制量 | 等级、购点、主动收入占比、灵脉份额与终局复活时间；1v1／2v2 另查规则可用性 |

**同一场景复算。** 纯调参冻结场景版本。机制变化使旧动作不再合法时，保留旧版本和失效原因，在新场景版本报告合法动作。独自出手与队友先控分别展示，目标先控带来的伤害机会只归因一次。

## 现行正式工具入口

Gradle 与 GameTest 均需 **JDK 25**，在仓库根目录执行。

| 任务 | 命令或文档入口 | 说明 |
| --- | --- | --- |
| 导出快照与覆盖报告 | `bash ./gradlew exportBalanceSnapshot` | 产出 `build/balance/balance-snapshot.json`、`balance-coverage.json/.md` 与摘要 |
| 配对发行包与快照 | `python tools/fabric/prepare-ci-release.py` | 校验 JAR 内嵌快照 SHA-256 并写入 `build/ci-release/` |
| 快照前后对照 | `python .agents/skills/myriad-balance/scripts/diff_snapshots.py <基线> <候选>` | 自动分离实改值、新增导出项与未变项 |
| 复核 #105 基线样本 | `python tools/balance/check-baseline.py build/balance` | 参数为**目录**（默认 `build/balance`），勿传单文件；仅用于核对 #105 历史期望 |
| 全阵容与 3v3/5v5 复跑 | [全阵容与对局模式验证](../../../../docs/balance/roster-modes.md) | 串联 `replay-roster-114.py`、`replay-battlefield-114.py`、`replay-cherry-114.py`、`replay-mode-economy.py` |
| 四角色 3v3 标杆复跑 | [樱隙前线 3v3 标杆](../../../../docs/balance/calibration-3v3.md) | 串联 `replay-economy.py` 与 `replay-calibration.py` |
| 公共战斗规则复算 | [公共战斗规则对照](../../../../docs/balance/combat-rules.md) | `tools/balance/replay-combat.py` |

## 引擎抽检（L4）与真人实战

列出当前全部可用 GameTest 过滤器（`--help` 同步展示核心与扩展列表）：

```sh
python tools/fabric/run-ci-gametests.py --list
```

只在需要核对服务端真实结算语义时运行对应过滤器（独立 JVM 串行执行），同时核对匹配日志、完成标记与 `build/reports/fabric-ci/<过滤器>/report.xml` 中的实际用例数。真实 3v3／5v5 对局体验由人记录，保存配对制品、地图配置、兵线、目标初态及观察者判断。历史四角色归档原型仅在追溯早期数据时查阅提交 `b97a9e0`。
