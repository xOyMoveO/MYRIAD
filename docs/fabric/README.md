# Fabric 26.3 开发与复现

本目录记录 #40 的平台接合与朔「一闪」试点、#41/#42角色迁移，以及 #43 的[双模式合成地图与生命周期复现](arena-fixture.md)；全量迁移完成条件仍属于 #39。[保留清单与公共契约](retention-and-contracts.md) 区分保留内容、试点与后续专项。

[#44最终技术记录](final44-20261117.md)汇总被测版本、复用/新增证据、发行包和已知失败；全部主观、视觉与原图工作见[人工移交](handoff44-20261117.md)。

[#42 B组角色记录](roster-b-20261116.md)列出7人/56候选、实际服务端专项、共享伤害修复及人工待验边界。

用户于2026-11-17收窄本轮范围：只以基础联机、核心对局、保存恢复和成功build收尾，性能及细节专项暂缓。以下是按任务选用的复现入口，不是本轮新增必跑清单；最终集成只留一次core build，CI仅build。

固定工具：Minecraft 26.3 正式、Fabric Loader 0.20.1、Fabric API 0.166.2+26.3、Loom 1.18.4、Gradle Wrapper 9.7.0、Java 25。构建依赖按 `gradle.properties` 固定，首次缺项时正常联网；已有缓存不清空。官方来源为 [Fabric 26.3示例](https://github.com/FabricMC/fabric-example-mod/tree/26.3) 与 [Loom1.18.4元数据](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.18.4/fabric-loom-1.18.4.module)，实际执行结果另见[验证报告](verification-20261116.md)。

```sh
java -version
bash ./gradlew build
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:melee_arc
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:first_group
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:shuo_pilot
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:roster_b
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:yike
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:rivet
bash ./gradlew runGameTestClient -PmyriadGameTests
```

`build` 编译主源码和所有保留GameTest源。测试mod仅在 `-PmyriadGameTests` 启用，独立目录 `run-fabric-gametest`，XML报告为 `build/reports/fabric-gametest.xml`。筛选项来自 Fabric 的 `fabric-api.gametest.filter`；默认只运行隔离的`myriad:first_group`。旧专项会修改共同训练/对局状态，逐专项显式filter、在新进程运行，不能并发聚合后宣称等价覆盖。物理客户端`runGameTestClient`启动后运行原管线/模型客观断言，报告`build/reports/fabric-client-checks.json`，不产生视觉通过结论。`arena_switch` 保留历史人工fixture入口并标为manual_only；按用户补充，实际地图及原地图整局由人验收。技术侧使用仓库内 `arena_fixture`、`arena_tower_5v5`、`arena_tower_3v3` 合成专项与独立服务器重启入口验证两模式，不索取或加载原地图。详见[双模式复现说明](arena-fixture.md)。

## 已有资源与隔离运行

Minecraft资源固定复用 `~/.local/share/minecraft/assets`，26.3索引为34，SHA1 `e99f9973047785b5a9d52eaf72020b97545053c9`。Loom使用索引别名`26.3-34`。先运行只读完整性检查；`--prepare-loom`仅在验证通过后补别名/兼容链接，不下载、不删除已有资源。

```sh
python tools/fabric/verify-assets.py
python tools/fabric/audit-resources.py
python tools/fabric/verify-assets.py --prepare-loom
bash ./gradlew runClient
bash ./gradlew runObserver
bash ./gradlew runServer
```

不同入口分别使用 `run-fabric-client`、`run-fabric-observer`、`run-fabric-server`。新测试服务器必须使用独立目录、新世界，回环地址和未占用端口；首次启动按Minecraft要求设置EULA/服务器配置。26.3默认白名单可能阻止测试账号加入，请用服务器console明确添加测试账号。不要打开或覆盖正式存档。交互运行支持 `stop` 正常保存退出，不停止用户自己的游戏。

若校验指出缺项，先检查索引、路径、链接、权限和可恢复副本，再按打印的hash仅补缺失对象；未经完整校验不使用 `-x downloadAssets`。本轮只补了141对象共7,598,449字节，5149项最终size/SHA1全通过，旧索引32保留。

发行包为 `build/libs/myriad-0.1.0.jar`，仅主源码/运行资源；独立GameTest源码、测试实例、MCP工具/adapter、配置、凭据及世界不打入主JAR。部署正式实例仍受身份迁移的安全例外限制，不自动覆盖。

图形、模型观感、动作观感、听感、手感、流畅性与平衡判断全部待人验收；日志、GameTest和工具状态只支持客观结果。

## GitHub Build CI

`.github/workflows/build.yml` 在 push / pull_request 时使用 JDK 25 与仓库 Gradle Wrapper，仅执行 `bash ./gradlew build`。Gradle 自身的正常 `check` 依赖保持不变；CI 不另跑 GameTest，也不设置额外发行审计或附件上传步骤。

## 本地整合验证与交付

发行审计和隔离专项保留为本地工具，供 #44 按实际验证范围单独运行：

```sh
python tools/fabric/prepare-ci-release.py
python tools/fabric/run-ci-gametests.py
# 本地已有缓存时，也可只复核指定入口：
python tools/fabric/run-ci-gametests.py --offline roster_a roster_b arena_fixture
```

`prepare-ci-release.py` 审计单一 Fabric 主 JAR 的版本、加载器身份、ZIP 和发行排除项，将 JAR、同名 `.sha256` 与 `release.json`（源码 SHA、版本、大小和摘要）写入 `build/ci-release/`。

`run-ci-gametests.py` 对每个 filter 使用独立 JVM；每次测试的 stdout、XML 和结果列表分别保存到 `build/reports/fabric-ci/<filter>/` 与 `results.json`，后一次不会覆盖前一项报告。成功同时要求 Gradle 正常退出、匹配指定的1个测试并实际完成和通过；空 XML 套件会如实记录 testcase=0，不能单独作为成功依据。失败不自动重试，后续独立专项继续运行，任一失败最终返回非零退出码。

这些服务端自动化不替代 #44 的真实独立服务器/双客户端联验，也不运行正式/原始地图，不给任何视觉或主观项目签通过。

## 本地发行核对

需要发行审计时，可在构建成功后运行 `python tools/fabric/prepare-ci-release.py`，仅在本机 `build/ci-release/` 生成生产JAR、同名SHA256与 `release.json`，不上传或发布。该脚本名称保留既有兼容入口；当前CI只做build。资源引用、语言占位符与全部音频解码审计使用 `python tools/fabric/audit-resources.py`（需要已有ffmpeg）。最终核心结果、交付摘要与用户暂缓项见#44记录。
