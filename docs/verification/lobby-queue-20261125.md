# 万象大厅、排队对局与独立训练场

交付基线为 Minecraft 26.3、Fabric Loader 0.20.1、Fabric API 0.166.2+26.3、Java 25。客户端与服务端须使用同一构建，本次网络协议为 24。服主开启玩法、选定地图后，玩家按 H 使用大厅。操作说明见[第一次游玩](../../README.md#第一次游玩)。

## 实现与复用

| 功能 | 改动入口与复用内容 |
| --- | --- |
| 排队与少人提议 | `game/QueueLobby.java` 管理各模式的候场顺位、15 秒提议和锁定名单，复用 `MatchArena` 的地图选择与配置检查。`network/LobbyNetworking.java` 传输玩家意图和个人快照。 |
| 分队、选角、准备与结算 | `game/GameManager.java` 在原有对局阶段内管理参赛者、轮次、私有预选、掉线代选、泉水准备和开战时间。原有伤害、兵线、野怪、商店、泉水和胜负系统继续结算；起始钱包与玩家的对局标识一起保存。 |
| 玩家界面 | `MyriadClient.java`、`client/LobbyClient.java`、`client/gui/LobbyScreen.java`、`DraftScreen.java`、`TrainScreen.java`、`MatchResultsScreen.java` 和 `client/HudOverlay.java` 接入 H、两套主题、轮次、训练及结算操作。复用原有角色头像、技能图鉴及玩家设置。 |
| 同服训练 | `game/TrainingGround.java` 和 `TrainingActions.java` 管理进出、练习存档、个人开关及双方 PvP 同意。假人沿用 `JiarenManager`；技能、普攻、经济和清场按玩家及世界区分。 |
| 地图与素材 | `data/myriad/dimension/training_ground.json` 登记独立世界，`data/myriad/structure/training_ground.nbt` 随包提供带标距射道的练习场。`TrainingGroundMap.java` 首次放置地图。`textures/gui/lobby/`、`textures/gui/draft/` 与 `textures/gui/sprites/{lobby,draft,match}/` 提供两套正式背景、面板、按钮、队徽、锁定角标及光带。 |
| 制作与验收 | `tools/arena-check/build_training_ground.py` 生成地形；`tools/slice_match_ui.py` 按 `art_source/match_ui/` 的原图重切部件。`ArenaFixtureChecks.java`、`TrainingGroundCombatChecks.java` 和测试客户端的输入桥接覆盖排队、并发训练与操作回读。 |

Java 入口相对 `src/main/java/com/myriad/`，发行资源相对 `src/main/resources/`。客户端纹理目录位于 `assets/myriad/` 下。完整文件列表可在本次功能提交中执行 `git show --name-only --format= e0cde42` 查看。

## 可复现命令

构建与命名空间检查：

```sh
bash ./gradlew --offline build
python tools/check_namespace.py --jar build/libs/myriad-0.1.0.jar
```

已有完整依赖缓存时可用 `--offline`。在新建的合成测试环境中分别运行以下各项。GameTest 的 XML 和专服的 JSON 会覆盖上一轮，保留每项报告后再运行下一项。

```sh
bash ./gradlew --offline runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:arena_fixture
bash ./gradlew --offline runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:training_ui
bash ./gradlew --offline runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:first_group
bash ./gradlew --offline runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:arena_tower_3v3
bash ./gradlew --offline runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:arena_tower_5v5
bash ./gradlew --offline runArenaFixtureServer -PmyriadGameTests -ParenaFixtureStage=training -ParenaFixtureMode=3v3
bash ./gradlew --offline runArenaFixtureServer -PmyriadGameTests -ParenaFixtureStage=training -ParenaFixtureMode=5v5
```

真实客户端入口为 `bash ./gradlew runClient` 和 `bash ./gradlew runObserver`；开发独立服务器为 `bash ./gradlew runServer`。测试地图的设施和启动步骤见[合成场地](../fabric/arena-fixture.md)。客户端原生按键、鼠标和截图可使用[测试桥接](../../tools/mcpfabric/README.md)。

## 自动化实测

- 合成场地 GameTest 通过 466 项检查，覆盖 1v1、2v2、3v3、5v5、15 秒退出打断、过期请求、离线代选、一次性 500 金、清场及重新排队。另开独立 JVM 完成 3v3 和 5v5 防御塔专项，分别通过 84 和 139 项检查。
- 训练 UI 和角色综合 GameTest 分别通过。角色回归同步修正了落霞的旧测试预期，按独立箭矢贴图、远程普攻与基础物攻 100 验证该角色；正式属性价格、成长和技能公式沿用原数值。
- 独立合成服务器中，正式对局与训练同时运行。3v3 及 5v5 分别通过 30、34 项训练检查，涵盖个人无冷却、练习钱包、假人归属、双方同意与撤回切磋、死亡回练习区、退出及重连恢复。
- 两套界面背景均为 960×540。15 个界面部件中有 8 份九宫格配置。已检查透明通道、缩放样张及发行包内的地图、结构和纹理。本机训练地图首次放置及已有测试存档新增训练世界均已实际加载。

## 真实客户端验收

全部使用合成对局地图和独立测试存档。原生键鼠操作通过测试桥接送入 Minecraft，金钱、角色、阶段和维度以服务器下发状态核对。最终交付包 SHA256 为 `88b5837dd5988cfeedcec3e7e60cbea7c84d6a706a9954a5cb8026f43e5f17a0`。

- **1v1 掉线与重连**。最终包的两台真实客户端先验证退队可打断提议，再由一人重新发起。第二名玩家提前私选天刑后退出游戏，另一方锁定夜影。观察到离线席位仍有 20 秒可选，随后服务器代锁天刑，进入 10 秒准备并正式开战。该玩家重连时恢复天刑和 500 金。之后双方打完该局，均自动停留在结算页；一人选择再排位于队首，另一人返回大厅。
- **2v2 与同时训练**。四名排队者由一人提议开局，按蓝红红蓝锁角，准备阶段为 0 金，开战各得 500 金。正式比赛期间另外两人可在独立世界换角、创建人偶、恢复生命和开启无冷却。夜影普攻击中训练搭档时，双方同意切磋才从 560 生命降到约 513.46，撤回同意后停止扣血。
- **胜负与再排**。为缩短合成地图测试，测试管理员提高参赛者物攻，并传送到两座塔和水晶旁，按正常塔序用普攻击破。四位参赛者自动看到结算页；一人点击再排后独占队列第一位，其余三人返回大厅，两位练习者仍留在训练场。
- **3v3 满员开局**。同一服务器和六个真实客户端使用相同的交付包。六人满员触发一局，经六手选出不同角色。关闭选角后 H 能恢复当轮状态；在 380×252 的 GUI 上成功翻页、选择寒泽并锁定。六人完成准备并各领 500 金。
- **5v5**。十个席位通过合成服务器测试，十个独立客户端同场及正式地图的十人整局仍待实机验收。截图供人工查看材质、动效与可读性，平衡和手感须在实战中评价。

[380×252 窗口的六人选角](assets/lobby-queue-v1/narrow-draft.png) · [2v2 自动结算及再排入口](assets/lobby-queue-v1/results-final.png)
