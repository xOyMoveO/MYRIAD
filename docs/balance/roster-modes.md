# 全阵容与对局模式验证

樱隙前线 3v3 是万象的角色平衡基准。5v5 使用相同的角色、技能、购点与购物规则，另外锁定三路地图的供给版本。1v1 和 2v2 使用所选单线地图，按规则可用性验证。

**正式数值已接入，3v3 整局体验待验。**

## 角色与樱隙战场

四批正式值汇齐 15 名角色、120 个唯一候选技能及 120 套合法终局分支。零预置固定正向起手、默认姿态与各角色的冻结配点。每套在独自交锋和队友先控、常规延迟和零额外延迟下重跑。每个角色分别记录 1.5 秒伤害、6 秒均伤、净行动损失、实际有效盾疗的最小值、最大值、对应分支和动作轨迹。分阶段场景另测 Lv4、Lv12、Lv20 交锋，队友先控与独自出手分别取数。强制等级及预算是场景条件，真实购买时点见经济账本。

本地 Fabric 服务端用实际战场实体取样。先以同预算的 Q／普攻复跑全员清线，再用合法的分支、技能轮转、力量或智慧购点及有／无己方兵线进一步观察主清线位。清营与推塔同时记录角色、目标、发生条件及逐项生效的结算。地图表用配对快照列出各类型生效值、队伍奖励和选择理由。外塔、高地塔与水晶对应樱隙前线配置，中塔是三档建筑合成对照。

冻结账本的 14 案按 V2 与 V3 配对重跑。四条全程脚本再用记录的营地坐标、实际合成清营用时、首刷重生和路线时间重新取样。回城、清营、补刀、击杀、助攻、塔赏、补给与购点分别记录。樱隙前线配置使用 V3，兵线原经验份额乘 3.6，野区原基础经验乘 3.2，金币与工资沿用 V2。主动收入占比仍待实图观测及后续选值。对线空间、回城选择、兵线控制和对局策略由真人补充。

## 5v5 与少人开局

新开局的三路地图使用 `RIFT_V1`。规则快照的 `modeProfiles.rift_5v5` 登记兵线、营地、金钱猪、塔赏、工资及属性灵脉。属性灵脉提供 5% 对应属性、每脉每 5 秒向在线队员各发 1 金和 1 经验。检查点保存开战时的供给版本。缺少版本的早期检查点按旧标准供给解读。

5v5 的独立经济报告比较单人对线、打野／游走、双人路／支援及顺逆风。0／1.5／3 条属性灵脉用作控脉敏感度，地图点位、路径和对局结果依据随报告提供。22–28 分钟完局及 22–26 分钟发育目标用于观察。合成服务端的结算证据和 L2 逐笔复演随配对发行保存。

少人局走队列、15 秒提议、选角、准备、开战和重开的实际服务端路径。少人专项记录每局人数和锁定席位、500 初始金、买入时的报价、Lv20 上限、零助攻单杀、离线席位的分母及回归后的状态。强度参考取樱隙六人对局，少人局采用规则可用性豁免。

## 复跑

使用 JDK 25。将环境变量指向各次归档目录。阵容基线目录保存 #105 发行，`CHERRY_V2_RELEASE` 指向此前的联合正式发行，冻结战场目录保存 #109 联合发行和同版战场报告。

```sh
bash ./gradlew build
python tools/fabric/prepare-ci-release.py
python tools/fabric/run-ci-gametests.py balance_roster_114_lane balance_roster_114_camps \
  balance_roster_114_towers mode_supply_rift mode_supply_cherry balance_small_modes
python tools/balance/replay-roster-114.py --baseline "$ROSTER_BASELINE" \
  --release build/ci-release --output build/balance/roster
python tools/balance/replay-battlefield-114.py --baseline "$CHERRY_V2_RELEASE" \
  --release build/ci-release --engine build/reports/fabric-ci --output build/balance/battlefield
python tools/balance/replay-cherry-114.py --release build/ci-release \
  --previous-release "$CHERRY_V2_RELEASE" \
  --frozen-battlefield-release "$BATTLEFIELD_BASELINE/paired" \
  --frozen-battlefield "$BATTLEFIELD_BASELINE/battlefield-report.json" \
  --lane-engine build/reports/fabric-ci/balance_roster_114_lane --output build/balance/cherry
python tools/balance/replay-mode-economy.py --mode 5v5 --baseline "$CHERRY_V2_RELEASE" \
  --candidate build/ci-release --output build/balance/rift
```

报告里的 L1 静态、L2 经济、L3 连招、L4 引擎和真人来源分别给出适用条件。已测的引擎专项同时保留匹配记录、stdout 及用例 XML。一级确定差异、二级参考偏离、三级试测与覆盖缺口都可随人工数值保存、构建、试玩、合并和发布。

## 真人验收记录

在匹配 JAR 与快照的正式樱隙前线上，用六个真实客户端从正式开战打到水晶被摧毁。记录地图配置、参赛者角色、实际完局时间、18 和 20 分钟各席位等级与购点、工资与主动收入分项、实际阵亡和复活，以及团战、清线、清营、推进和商店反馈。时间参考为 16–20 分钟，等级参考为 Lv18–20，终局购点参考为 12–16，主动收入参考为 60%–70%，终局复活参考为 42–45 秒。该次反馈应引用其实际配对制品和完整对局记录。
