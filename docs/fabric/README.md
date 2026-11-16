# Fabric 26.3 开发与复现

本目录记录 #40 的平台接合与朔「一闪」试点；全量迁移完成条件仍属于 #39。[保留清单与公共契约](retention-and-contracts.md) 区分保留内容、试点与后续专项。

固定工具：Minecraft 26.3 正式、Fabric Loader 0.20.1、Fabric API 0.166.2+26.3、Loom 1.18.4、Gradle Wrapper 9.7.0、Java 25。构建依赖按 `gradle.properties` 固定，首次缺项时正常联网；已有缓存不清空。官方来源为 [Fabric 26.3示例](https://github.com/FabricMC/fabric-example-mod/tree/26.3) 与 [Loom1.18.4元数据](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.18.4/fabric-loom-1.18.4.module)，实际执行结果另见[验证报告](verification-20261116.md)。

```sh
java -version
bash ./gradlew build
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:melee_arc
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:first_group
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:shuo_pilot
bash ./gradlew runGameTestClient -PmyriadGameTests
```

`build` 编译主源码和所有保留GameTest源。测试mod仅在 `-PmyriadGameTests` 启用，独立目录 `run-fabric-gametest`，XML报告为 `build/reports/fabric-gametest.xml`。筛选项来自 Fabric 的 `fabric-api.gametest.filter`；默认只运行隔离的`myriad:first_group`。旧专项会修改共同训练/对局状态，逐专项显式filter、在新进程运行，不能并发聚合后宣称等价覆盖。物理客户端`runGameTestClient`启动后运行原管线/模型客观断言，报告`build/reports/fabric-client-checks.json`，不产生视觉通过结论。`arena_switch` 保留历史人工fixture入口并标为manual_only；按用户补充，实际地图及原地图整局由人验收。技术侧只在测试世界验证，不索取或加载原地图。

## 已有资源与隔离运行

Minecraft资源固定复用 `~/.local/share/minecraft/assets`，26.3索引为34，SHA1 `e99f9973047785b5a9d52eaf72020b97545053c9`。Loom使用索引别名`26.3-34`。先运行只读完整性检查；`--prepare-loom`仅在验证通过后补别名/兼容链接，不下载、不删除已有资源。

```sh
python tools/fabric/verify-assets.py
python tools/fabric/verify-assets.py --prepare-loom
bash ./gradlew runClient
bash ./gradlew runObserver
bash ./gradlew runServer
```

不同入口分别使用 `run-fabric-client`、`run-fabric-observer`、`run-fabric-server`。新测试服务器必须使用独立目录、新世界，回环地址和未占用端口；首次启动按Minecraft要求设置EULA/服务器配置。26.3默认白名单可能阻止测试账号加入，请用服务器console明确添加测试账号。不要打开或覆盖正式存档。交互运行支持 `stop` 正常保存退出，不停止用户自己的游戏。

若校验指出缺项，先检查索引、路径、链接、权限和可恢复副本，再按打印的hash仅补缺失对象；未经完整校验不使用 `-x downloadAssets`。本轮只补了141对象共7,598,449字节，5149项最终size/SHA1全通过，旧索引32保留。

发行包为 `build/libs/myriad-0.1.0.jar`，仅主源码/运行资源；独立GameTest源码、测试实例、MCP工具/adapter、配置、凭据及世界不打入主JAR。部署正式实例仍受身份迁移的安全例外限制，不自动覆盖。

图形、模型观感、动作观感、听感、手感、流畅性与平衡判断全部待人验收；日志、GameTest和工具状态只支持客观结果。
