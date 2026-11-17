# 万象 · MYRIAD

万象是 Minecraft 的多题材角色团队对抗模组，原名《仙凡劫》。玩家组成两支队伍，选择角色、学习技能分支，通过兵线推进、野区争夺和团队配合，依次摧毁防御塔，最终击破敌方水晶。角色题材不限于修仙，现有角色与地图主题保留。

当前版本面向 **Minecraft 26.3 / Fabric / Java 25**，包含十四名可选角色、112 个候选技能，以及 3v3、5v5 两种对局模式。Fabric 迁移正在 [PR #45](https://github.com/xOyMoveO/MYRIAD/pull/45) 中整合；获取源码时请核对分支，合并和发布状态以仓库实际记录为准。

- [玩法与角色](#玩法与角色)
- [安装与版本要求](#安装与版本要求)
- [第一次游玩](#第一次游玩)
- [地图与存档](#地图与存档)
- [开发与构建](#开发与构建)
- [当前验证范围](#当前验证范围)
- [文档与许可](#文档与许可)

## 玩法与角色

一局从分队和轮流选角开始。双方共享角色池，同一角色只能由一名玩家选择；全员选完后进入泉水，经过十秒准备再开战。玩家在对局中获得经验和金币，用技能点学习能力，用金币强化力量、智慧、速度、防御、冷却五类属性并购买补给。力量增强普攻，智慧增强技能。

每名角色有八个候选：两个被动、两个常规技能、两个主要技能和两个终极技能。被动、主要、终极各选一个分支，两个常规技能可以同时学习。**八个候选不代表八个技能同时可用。** 具体效果、等级要求和消耗可在游戏内图鉴与技能面板查看。

| 模式 | 队伍规模 | 地图结构 |
| --- | --- | --- |
| 5v5 | 每方最多五人 | 三路兵线、分层防御塔、泉水、水晶及野区 |
| 3v3 | 每方最多三人 | 独立配置的单中路地图，保留塔序、水晶和野区目标 |

草丛与视野影响目标发现，野怪和资源点需要双方争夺。地图设施、兵线路径、草丛、营地和小地图边界均可配置，服主可以按地图安排战场。

十四名角色为：夜影、天刑、焚诀、墨尘、素岚、凌渊、寒泽、青萝、赤霄、云笙、朔、刻衡、伊珂和铆钉。例如：

| 角色 | 机制示例 |
| --- | --- |
| 居合·朔 | 在收鞘与出鞘状态间切换，用格挡反击、突进和延迟刀痕组织连招 |
| 时律·刻衡 | 布置局部缓时领域，减慢受支持目标的行动与独立弹体；技能冷却和效果到期仍按真实时间计算 |
| 虹轨·伊珂 | 在定轨炮台与游走阵列之间选择，手动瞄准并逐次开火 |
| 铆钉·战场筑垒师 | 在墙面部署战术锚点，配合牵引、队友护盾和撞墙控制作战 |

十名退役角色已退出可玩阵容。角色内部 ID、源码入口及完整名单见[协作开发说明](docs/协作开发与内部命名.md)。

## 安装与版本要求

| 组件 | 当前要求 |
| --- | --- |
| Minecraft Java 版 | `26.3` |
| Fabric Loader | `0.20.1` |
| Fabric API | `0.166.2+26.3` |
| Java | 运行至少 `25`；构建及已有验证使用 JDK 25 |
| 万象主模组 | `myriad-0.1.0.jar` |

客户端和服务端都需要安装万象与上述 Fabric API，使用一致的模组版本。NeoForge 包不能用于这个 Fabric 实例。

使用维护者明确提供的 Fabric 26.3 主 JAR；没有交付包时，按[开发与构建](#开发与构建)从对应分支自行构建。当前 GitHub **Build 只执行构建，不上传 JAR 附件，也不自动创建 Release**。不要将 `-sources.jar` 当作游戏模组安装。

1. 新建一个独立的 Minecraft 26.3 Fabric 实例；服务器也使用独立目录和新世界。
2. 将 `myriad-0.1.0.jar` 与匹配版本的 Fabric API 放入各自实例的 `mods/`，确认没有重复的万象或旧仙凡劫 JAR。
3. 用该实例启动游戏或服务器。独立服务器首次启动需按 Minecraft 要求确认 EULA，再配置端口、白名单与管理员；测试账号被白名单拦截时，在服务器控制台明确添加。
4. 进入新世界，按下一节启用玩法。仓库包含模组源码与运行资源，**不包含正式 3v3/5v5 地图存档或完整游戏实例**。

旧版本涉及内部身份及加载器迁移。先备份，勿用新包直接打开唯一一份旧世界，也不要把新旧模组装在同一个实例中。旧世界的处理见[地图与存档](#地图与存档)。

## 第一次游玩

### 没有地图：先体验角色

在允许命令的新单人世界，或给测试账号管理员权限的独立服务器上操作。启用玩法、切换训练模式及开局管理需要管理员权限；训练开关对整台服务器生效。

在游戏聊天栏依次输入：

```text
/myriad mod on
/myriad train on
/myriad char shuo
```

这会开启玩法和训练模式，并选择朔。未配置大厅时玩家留在当前位置，可以找一块安全空地练习，无须导入地图。

1. 打开“选项 → 控制 → 按键绑定”中的万象分类，为技能设置互不冲突的按键。**技能键默认未绑定**，技能面板中的 Q/W/E/R 表示槽位，不代表已绑定这些物理按键。
2. 按 `C` 打开个人信息。初选角色有一个技能点，可在技能面板中选中朔的常规技能“纳刀”并确认加点，关闭面板后按自己绑定的一技能键施放。
3. 输入 `/myriad train` 打开训练面板，可提升等级、获得金币、选择训练阵营，或开启无冷却、无限金币、无限法力。训练中换角会返还技能点，方便体验不同分支。
4. 用 `/myriad choose` 打开角色选择界面，继续体验其他角色；完成后用 `/myriad mod off` 关闭玩法及训练开关。

训练模式可跳过正式对局的施法阶段限制，但不会生成完整竞技地图。需要练习伤害时，可用 `/myriad jiaren create` 创建训练假人。

### 有地图：组织一场对局

先在副本中配置好对应模式的路线、塔、泉水、水晶等设施，再让参与者加入同一服务器。管理员在游戏内执行 `/myriad mod on`，输入 `/myriad start` 打开分队界面：

1. 选择 5v5 或已配置完成的 3v3。3v3 配置缺失或不合法时，该模式不可选。
2. 使用“随机开局”自动分队，或用界面中的“玄霄宗”“幽冥宗”按钮手动安排两队，再点击“开始游戏”。手动开局要求双方有人、人数差不超过一人，且不超出模式容量。
3. 玩家按界面轮次选角。全员完成后传送泉水，十秒准备结束进入战斗；学习技能、购买属性与补给，按塔序推进并摧毁敌方水晶。
4. 结束后查看结算。管理员可用 `/myriad restart` 重开，或 `/myriad mod off` 关闭玩法并清理本局。

`/myriad start` 在装有模组的客户端会打开管理界面；服务器控制台中的 `myriad start` 则直接请求随机分队开局。控制台命令省略开头的 `/`。

### 常用操作

| 操作 | 默认键或入口 |
| --- | --- |
| 个人信息、技能学习、属性与补给 | `C` |
| 商店 | `P` 或 `/myriad shop` |
| 回城 | `B` |
| 技能键 | 默认未绑定，需在控制设置中指定 |
| 需要选点的技能 | 进入选点后，鼠标左键确认、右键取消 |
| HUD / 小地图位置 | `/myriad hud` / `/myriad minimap` |
| 当前对局状态 / 结算 | `/myriad status` / `/myriad results` |

按键可以重绑，角色的特殊输入以技能说明为准。若界面没有同步数据，先确认服务器也安装了匹配版本的万象，且玩法已开启。

## 地图与存档

地图由地形和点位配置共同组成。管理员可在主世界、对局尚未开始时，用 `/myriad settings` 打开设置，再进入地图设施编辑器，在实际位置设置设施和边界。仓库中的[合成地图 fixture](docs/fabric/arena-fixture.md)用于技术复现，不是正式地图下载。

两种模式保留独立的五份配置：

- `myriad_lane_layout.json`：兵线路径、塔、泉水与水晶。
- `myriad_brush_zones.json`：草丛范围。
- `myriad_monster_spawns.json`：野怪与首领营地。
- `myriad_vein_spawns.json`：资源点候选。
- `myriad_minimap.json`：小地图边界。

5v5 配置位于世界根目录；3v3 位于 `myriad_arenas/3v3/`。`myriad_lobby.json` 为两模式共享大厅，`myriad_arena_selection.json` 保存模式选择。编辑后正常停止服务器，重启并核对点位、模式及设施。

沿用旧地图时，只在备份副本中迁移地图配置，并按配置重建新设施、人工核对地形和通行空间。**不导入 NeoForge 旧玩家等级、金币、角色、技能、旧个人 HUD 或旧 `myriad_match.json` 检查点**，也不要通过旧实体继续旧局。新 Fabric 玩家状态、个人 HUD 与对局使用独立数据。

独立服务器按当前支持范围恢复新的对局检查点；本地单人/LAN 世界由主持人保存退出后结束本局，重新打开不会续局。需要回退时，恢复匹配的加载器、模组、世界和配置整份备份。详细迁移与原图人工步骤见[人工移交](docs/fabric/handoff44-20261117.md)。

## 开发与构建

使用 JDK 25，在仓库根目录执行。Gradle Wrapper 已固定为 9.7.0，Loom 为 1.18.4；版本声明见 [gradle.properties](gradle.properties) 与 [build.gradle](build.gradle)。

macOS / Linux：

```sh
java -version
bash ./gradlew build
```

Windows PowerShell：

```powershell
java -version
.\gradlew.bat build
```

主包输出为 `build/libs/myriad-0.1.0.jar`。首次构建缺少依赖时需要联网；已有缓存可复用，不必清空 Gradle 或 Minecraft 资源。构建成功后可执行：

```sh
python tools/fabric/prepare-ci-release.py
```

该脚本在本机 `build/ci-release/` 整理主 JAR、同名 SHA256 文件及 `release.json`，供核对后手动安装；不会上传、发布或安装。Windows 的 `一键编译并导入Minecraft.bat` 保留旧文件名，当前也只执行 Wrapper 构建与这一步整理，不再自动导入游戏。辅助脚本使用 `python` 命令。

### 开发实例与共享资源

```sh
python tools/fabric/verify-assets.py
python tools/fabric/verify-assets.py --prepare-loom
bash ./gradlew runClient
```

本机 Minecraft 资源统一复用 `~/.local/share/minecraft/assets` 下的 `indexes/` 与 `objects/`。校验通过后，`--prepare-loom` 才为 Loom 设置索引别名和兼容链接，不下载或清除资源。若有缺项，先检查路径、链接、权限和可恢复副本，再按缺失哈希补齐；完整校验通过前不要跳过下载任务。清理项目时保留这个共享目录。

`runClient`、`runObserver`、`runServer` 分别使用 `run-fabric-client/`、`run-fabric-observer/`、`run-fabric-server/`，可用于隔离客户端、第二客户端与独立服务器。服务端仍需设置 EULA、白名单、端口和测试权限。更完整的资源、启动与工具说明见 [Fabric 开发与复现](docs/fabric/README.md)。

### 验证与协作

`build` 会编译主源码与保留的 GameTest 源码，**不会自动运行游戏内专项**。按修改范围选择专项，例如：

```sh
bash ./gradlew runGameTestServer -PmyriadGameTests -PgameTestFilter=myriad:shuo_pilot
```

专项使用独立测试实例，报告为 `build/reports/fabric-gametest.xml`，后续运行会覆盖。涉及共享训练或对局状态的专项应逐项在独立 JVM 中串行执行，核对实际匹配、完成和通过记录，不能只看 Gradle 退出码。需要保留逐项报告时可用 `python tools/fabric/run-ci-gametests.py`；过滤项与适用范围见 [Fabric 开发文档](docs/fabric/README.md)。

[GitHub Build](.github/workflows/build.yml) 在 push 和 pull request 时使用 JDK 25 执行 `bash ./gradlew build`，不另跑 GameTest，也不上传发行包。参与修改前先读 [AGENTS.md](AGENTS.md) 和[协作开发说明](docs/协作开发与内部命名.md)；每项修改使用独立工作分支，经 PR 合入 `main`，不提交构建缓存、游戏实例、存档或凭据。需求与任务通过 [GitHub Issues](https://github.com/xOyMoveO/MYRIAD/issues) 管理，操作约定见 [Issue tracker](docs/agents/issue-tracker.md)。

## 当前验证范围

Fabric 版本已有成功构建、发行主 JAR 启动、真实多客户端联机、原生选角/学习/施法、两模式核心流程及新数据保存恢复记录。3v3/5v5 流程使用从零生成的合成世界和有限真实客户端，不代表正式原图或六人、十人满员对局已经验收。

当前按基础核心范围交付，性能与细节专项暂缓。本次迁移验证中仍有未确定原因的专项失败，详见[核心交付记录](docs/fabric/final44-20261117.md)。正式地图整局、画面与模型观感、音效听感、操作手感、流畅性和平衡均由人验收；自动化结果不替代这些结论。

## 文档与许可

| 文档 | 内容 |
| --- | --- |
| [协作开发与内部命名](docs/协作开发与内部命名.md) | 角色 ID、源码入口、命名与修改边界 |
| [领域术语](GLOSSARY.md) | 候选技能、技能分支、缓时、战术锚点等用语 |
| [Fabric 开发与复现](docs/fabric/README.md) | 开发启动、专项、资源与本地发行工具 |
| [保留清单与公共契约](docs/fabric/retention-and-contracts.md) | 迁移功能基线与角色、资源范围 |
| [双模式合成地图](docs/fabric/arena-fixture.md) | 技术复现及正式地图人工清单 |
| [核心交付记录](docs/fabric/final44-20261117.md) / [人工移交](docs/fabric/handoff44-20261117.md) | 已有结果、已知问题及后续人工步骤 |

项目声明 **All Rights Reserved**，公开源码不表示授予自由使用、修改或再分发的开源许可。第三方素材各自的来源与许可见 [META-INF/licenses](src/main/resources/META-INF/licenses/)；再分发前需逐项核对。仓库中的 [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt) 仅保留模板许可，不代表全部代码和素材采用该许可。
