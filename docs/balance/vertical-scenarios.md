# 落霞重箭与伊珂炮台的纵向对照

| 场景 | 固定输入 | 核对点 |
| --- | --- | --- |
| [落霞「一点残阳」](../../tools/balance/scenarios/luoxia-last-sun-range.json) | 一级一阶，无购买，四条独立无障碍航道。村民靶中心离箭矢起点轴向 27.5／28.5／31.5／32.5 格 | 基线 28 格只击中 27.5 格靶。试改 32 格额外击中 28.5／31.5 格靶，32.5 格仍未命中。判定针对靶的近缘。技能介绍卡与快照使用同一个射程字段。 |
| [伊珂「虹轨弹」](../../tools/balance/scenarios/yike-rainbow-shell-stances.json) | 一级一阶，满蓝、无购买、无训练免耗，动作倍率 1。炮台各学对应一阶 E，部署后下一刻按 Q，两发后手动收起 | 普通当发冷却 120 真实刻。定轨初始 5 弹、出膛相隔 22 刻，游走初始 4 弹、相隔 27 刻，炮台当发普通冷却均为 0。下一发允许提前 5 刻起手，最早接受出手请求分别为上一发出膛后 17／22 刻，实际出膛仍在 22／27 刻。收起后恢复普通 Q 冷却。 |

伊珂缺少弹数、真实刻间隔或起手缓冲的快照只能比较单发，对应场景标为 `single_shot_only` 并记录缺失字段。两种炮台在本场景未触发破阵、蓝量耗尽或炮台超时。事件刻是从各用例首次输入起计的真实刻，动作进度使用表中的倍率。落霞飞行按无地形遮挡、无缓时的直线特例推演。

[公共战斗规则专项](combat-rules.md)沿用伊珂的两个炮台，增加 0／4／8／12 点技能急速下普通施法、出膛与手动撤炮的逐刻对照。

## 复跑

使用 JDK 25。以下命令分别在基线检出和测试副本执行。试改时在测试副本只将 `LastSun.RANGE` 调为 32，使用同一组探针与同一场景版本，服务端命令加 `JAVA_TOOL_OPTIONS=-Dmyriad.balance.luoxia.expected_range=32`。复制证据后删除测试副本。

```sh
./gradlew build
python tools/fabric/prepare-ci-release.py
./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:balance_vertical >server.log 2>&1
cp build/reports/fabric-gametest.xml server.xml
python tools/balance/replay-vertical.py \
  --snapshot build/balance/balance-snapshot.json --coverage build/balance/balance-coverage.json \
  --server-log server.log --server-xml server.xml --release build/ci-release/release.json \
  --output build/balance/vertical
```

`--server-log` 与 `--server-xml` 可省略以单独复算离线轨迹。将客户端读数 JSON 通过 `--cards` 一并写入报告。复跑输出 JSON 中有完整输入、规则来源、逐事字段对照及额外引擎观测。保留原始日志、XML 与客户端材料时，把它们放在报告同一目录以便查阅链接。两种射程必须分别保留报告和 GameTest XML。对照报告供人工评估，常规构建不会运行回放。

技能介绍卡的生产入口是个人信息面板的悬停卡。刻衡旧中文「刻痕回响」成长 12 的说明保存在[历史原文](../../tools/balance/archived/zh_cn.json)，构建报告用 `keheng_kehen`、`zh_cn`、`damage.rankGrowth` 标记与当前 14.4 的差异。
