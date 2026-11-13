# combat_regression 原始基线复核

2026-11-13 在原始基点 `d08aaa9c06840c9bd0ffd0604f9742ba9106c709` 的独立 detached worktree，用 JDK 25 和新测试世界实际运行构建及旧版战斗回归，退出码 1，1 项测试、1 项失败：

```text
myriad:combat_regression: Every implemented hero needs a balance baseline on tick 3
```

#3 工作分支此前实际运行同一测试，也在同一断言失败。因此该失败早于 #3 修改，不能归因于本次重构；也不能声称旧版全量回归或失败点之后的断言通过。定位为 `CharacterBalanceChecks.java:63` 的 `CharacterType.values().length == roster.length + 1`，由 `CombatRegressionTests.java:42` 调用（行号对应原始基点）。

可在隔离的新测试工作区复现（需 JDK 25 与构建依赖；报告路径按本机选择）：

```sh
bash ./gradlew --no-daemon --console=plain build runGameTestServer '--args=net.neoforged.fml.startup.GameTestServer --tests myriad:combat_regression --universe myriad-baseline-combat-20261113 --report /private/tmp/myriad-baseline-combat.xml'
```

实际运行使用共享串行锁脚本，执行上述 Wrapper 任务及参数；未改源码或断言，未运行客户端、竞技场或正式服务器，未加载正式存档，未部署。临时基线工作区检查干净后已移除。

原始日志与 XML 保留于 `/private/tmp/myriad-implementation/baseline-combat-launch.log`、`baseline-combat.xml`；#3 对照日志为 `issue-3/build-and-legacy-combat-launch.log`。临时目录可能被清理，本文只保存已观察事实与复现入口，不冒充原日志或后续复跑。集成提交 a5f371a 没有再运行旧版全量回归。
