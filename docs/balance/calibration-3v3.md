# 樱隙前线首轮 3v3 标杆

[#110](https://github.com/xOyMoveO/MYRIAD/issues/110) 将战场、经济和四名英雄的标杆并排复跑。对照从 `d94b353` 的正式配对 JAR 和快照开始。对照与候选都使用 `CHERRY_V2` 的兵线、野区和塔规则。

## 已选定的公共规则

| 参数 | 对照构建 | 正式值 |
| --- | ---: | ---: |
| 每一点防御属性 | +5 物防、+5 法抗 | +5 物防、+5 法抗 |
| 单次击杀合格助攻金币池 | 101 金 | 101 金 |
| 单次击杀合格助攻经验池 | 51 经验 | 51 经验 |
| 被动工资的追赶部分 | 1 倍 | 0 倍 |

策划在并读 +5／+15 双抗、两项独立助攻池和 0／0.5／1／1.5 倍工资追赶后确定上表。工资每 100 真实刻发放一次，经全局倍率后为 14 金，阵亡等待期间照发。主动补刀与野区收益继续按锁定席位追赶。合格助攻者分别平分两池，余数按锁定名单顺序分配。

## 四个标杆

以下场景各有旧版真人目标。新读数来自本地 Fabric 服务器合成环境，脚本固定输入、预算、角色、时间和实际伤害读数。游戏结算走正式服务端入口。操作强度、地形和双方策略还需正式地图真人样本复核。

| 标杆 | 同场景判据 | 证据范围 |
| --- | --- | --- |
| 落霞 | 前五分钟，普通一波兵加英雄削去无人守满血外塔约 15% | 实际外塔逐笔掉血、箭数、兵线贡献及兵线清空时点。联合账本的 3:00 样本与 #109 的一级零购配置分别观察 |
| 夜影 | 同经济从背面输入 21A34 并命中，第一下补 A 后目标仍活，第二下击杀 | 联合账本 Lv10 双方 4,770 金基准，以及旧 Lv20／4,885 金条件。日志保留命中时的朝向、请求、蓝耗和三次余血 |
| 刻衡 | 三阶「一秒之外」罩住未交位移的撤退者，队友应在目标脱离前打出有效命中 | 同脚本施放与移除 R，记录领域生效、队友首次命中、目标脱离及行动倍率 |
| 青萝 | 持续受击的半血友军经 R 接 Q 稳住血线并恢复输出 | R 接 Q、仅 R、无支援三轨，记录有效回复、实际盾吸收、血线和有效普攻。恢复输出时点来自固定血线触发的操作脚本 |

现行英雄基础属性、逐级成长、普攻和八项技能可在每次配对生成的 L1 报告逐项查阅。逐技能保留理由与待选项见[四角色值表](../../tools/balance/scenarios/calibration-role-decisions.json)。[四角色历史反馈](https://github.com/xOyMoveO/MYRIAD/issues/96#issuecomment-7233921931)

## L1／L3 场景与多目标预算

`replay-calibration.py` 读取配对快照中的角色定义、1–20 级成长、普攻、32 个候选的蓝耗、冷却和效果。相关字段的取值时点和覆盖状态见 JSON。L1 名义段值按技能阶数计算。

每名角色枚举八套合法 Lv20 终局分支。零预置方案固定正面朝向、默认姿态及配点。独自出手使用撤步目标，队友先控从相同初始位置注入 20 真实刻眩晕。两种条件都跑常规操作和零额外延迟，共 128 条构筑轨迹。报告列出合成场景观察到的 1.5 秒爆发、6 秒均伤、净行动损失和有效治疗加实际吸盾，并给出各项对应组合。确定性动作优先级表的覆盖缺口按受影响指标登记。

[角色及通用场景](../../tools/balance/scenarios/combos/)同时提供动作优先级表和显式脚本。公共执行器处理施放、选点确认、续段、普攻、取消和等待。起手行动与按真实刻到期的冷却分别推进。夜影绕背和回返、刻衡随身圈取消、青萝救援各有脚本。L4 仅认领其日志记下的状态和动作，剩余组合为离线推演。

[多目标配置](../../tools/balance/scenarios/combos/multi-target.json)登记分散、集中、队友先控三种场景的条件命中概率、留场时长、集火权重和受援分层。逐个比较一到三人受到技能及移除技能的事件轨迹。主目标已有的队友先控在两轨都生效，控制取净行动损失。治疗受实际缺血限制，同源盾取强刷新，盾量价值按到期前实际吸收计。落霞穿透的副目标衰减取快照。报告保留各人数档和期望。概率、权重及参考线属三级试测输入。[多目标归因决议](https://github.com/xOyMoveO/MYRIAD/issues/98#issuecomment-7234506976) · [连招场景决议](https://github.com/xOyMoveO/MYRIAD/issues/99#issuecomment-7234623700)

## L2 经济账本

`replay-economy.py` 使用相同配对版本和实际候选战场记录运行 14 案。兵、野、塔按来源归账。六个主要时点记录六席余额、购点、等级、主动收入占比和对应复活时间。8:00 注入用于顺逆风反事实。第 4 批固定 V1 账本入口见[历史复演](economy-replay.md)。

```sh
python tools/balance/replay-economy.py \
  --scenario tools/balance/scenarios/economy-cherry-v2.json \
  --baseline "$CALIBRATION_BASELINE" --candidate build/ci-release \
  --battlefield-evidence "$BATTLEFIELD_EVIDENCE" \
  --decision '每点 +5 双抗，101 金与 51 经验，工资追赶 0 倍' \
  --output build/calibration/economy
```

`CALIBRATION_BASELINE` 指向 #109 的对照发行目录，含 `release.json`、JAR 与快照。 `BATTLEFIELD_EVIDENCE` 指向 #109 联合战场复算生成的 `battlefield-report.json`，用于核对实际样本与静态规则。两者来自同一对照版本。

## 构建与报告入口

在仓库根目录运行，Gradle 使用 JDK 25。每项 GameTest 在独立 JVM 串行执行。保留每项启动、匹配、通过标记、逐笔 JSON 事件及 XML。

```sh
bash ./gradlew build
python tools/fabric/prepare-ci-release.py
python tools/fabric/run-ci-gametests.py balance_calibration_damage calibration_keheng calibration_qingluo match_economy match_assists balance_haste
```

完成账本后生成总报告，把日志和 XML 一并归档。

```sh
python tools/balance/replay-calibration.py \
  --baseline "$CALIBRATION_BASELINE" --release build/ci-release \
  --economy build/calibration/economy/economy.json \
  --engine release-damage build/reports/fabric-ci/balance_calibration_damage/stdout.txt build/reports/fabric-ci/balance_calibration_damage/report.xml \
  --engine release-keheng build/reports/fabric-ci/calibration_keheng/stdout.txt build/reports/fabric-ci/calibration_keheng/report.xml \
  --engine release-qingluo build/reports/fabric-ci/calibration_qingluo/stdout.txt build/reports/fabric-ci/calibration_qingluo/report.xml \
  --output build/calibration/report
```

离线报告核对两份配对发行、账本引用的快照摘要和各专项日志。平衡提醒供策划复审。技术专项与真实六人对局的体验验证分别记录。
