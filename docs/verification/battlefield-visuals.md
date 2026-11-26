# 防御塔射程圈、技能点角标与落霞 2D 特效（第一轮）

2026-11-26，Minecraft 26.3、Fabric。交付包为 `myriad-0.1.0.jar`，SHA-256 为 `3ce60f7755d64b47ae6944cc5f33ab8c0d43e81094bf2eb4b2b4db4589215b97`。两套测试服及客户端加载的主 JAR 均与交付包逐字节摘要一致。[实测数值与构建校验](assets/battlefield-visuals/checks.json)

## 1. 防御塔射程圈

接近存活敌塔时，贴地虚点给出防御塔最大射程的水平边界。半径与服务器寻敌共用一处 16 格定义。正式塔在原版实体追踪数据中同步阵营，触发本地绘制。每 9 刻最多刷新最近三座敌塔，常规每塔 72 点，最少粒子每塔 36 点。双端网络协议从 28 升为 29。玩家个人快照和落霞特效包沿用原有格式。

| 实测场景 | 结果 |
|---|---|
| 3v3 场地少人局，四个真实客户端分为 2v2 | 同一座塔前，进攻方看到圈，防守方看到己方塔时没有圈。实测 72 个落点的水平半径均为 16 格，最少粒子实际留下 36 个不同位置的粒子。 |
| 进出边界与真实锁定 | 从圈外向内，24 格出现；已显示时到 26 格仍可见，26.25 格收起。服务器在 16.0 格尚未获得新目标，15.9 格获得锁定，退到 16.0 格维持原锁定，16.1 格解除。水平 15 格、高差 7 格时未锁定。立柱完全遮挡后解除，移开立柱后重新蓄力。 |
| 地形、重叠与卸载 | 下半砖的落点高 0.575 格，楼梯高侧为 1.075 格。悬崖与厚墙处留缺口，屋顶下的点落在地面。重叠时保留两个不同圆心。测试端卸载一个采样区块，余下 63 个有效落点；区块保持卸载，重新进入追踪后恢复。 |
| 阵亡、拆塔、结算 | 阵亡后相机附着到存活队友，队友附近的敌塔圈仍可见，个人 HUD 隐藏。复活后恢复。测试伤害依次拆除两塔和水晶，塔圈随实体消失；结算返回大厅及关闭玩法后，圈数均为零。 |
| 5v5 合成上路 | 一个真实客户端与九个服务端测试玩家组成十个席位，按 5v5 设施配置开战。上路一塔实测 72 点，水平半径 16 格。 |

入口为 `DefenseTowerEntity`、`LaneSystem` 和 `TowerRangeRings`。圈使用客户端原有粉尘粒子。两套地图均由仓库合成场景生成器从零创建。樱隙前线 3v3 原图依项目实图规则留待人工操作与验收。

[3v3 敌塔](assets/battlefield-visuals/enemy-tower.png) · [同塔防守方](assets/battlefield-visuals/allied-tower.png) · [5v5 上路](assets/battlefield-visuals/five-top-tower.png) · [最少粒子](assets/battlefield-visuals/minimal-tower.png) · [重叠](assets/battlefield-visuals/tower-overlap.png) · [阵亡队友视角](assets/battlefield-visuals/spectating-ally.png)

## 2. HUD 未分配技能点

`CombatHudState` 将个人同步快照中的 `skillPoints` 传给 `CombatHudOverlay`，与 C 页共用余额。完整布局将正数画在头像框内右上角。紧凑布局按字体宽度把角标放在等级左侧，蓝量文字放不下时，等级与角标一起移到下一行。角标沿用面板的缩放、移动和隐藏。

同一玩家在 920 和 460 GUI 像素宽度下分别拍摄了 0、1、3、2、0 和 12 点的 HUD 及对应 C 页。余额序列用测试场景设置；另一次从 C 页原生点击学习，成功后实收余额 3 → 2 → 1 → 0。不可学习的终极分支尝试后仍为 3 点。F1 隐藏后恢复 12 点，阵亡时 HUD 隐藏，复活恢复 3 点，断开并重新进入同一场后恢复 12 点。

[零点与两位数对照](assets/battlefield-visuals/hud-compare.png) · [完整零点](assets/battlefield-visuals/hud-full-0.png) · [完整正点](assets/battlefield-visuals/hud-full-12.png) · [紧凑零点](assets/battlefield-visuals/hud-compact-0.png) · [紧凑正点](assets/battlefield-visuals/hud-compact-12.png) · [C 页 12 点](assets/battlefield-visuals/points-panel-12.png)

## 3. 落霞两项终极技能

新增两张透明母图、十张独立运行图集，共四十帧。验证了每轨透明边缘、帧间差异、尺寸、图集索引及 JAR 内的图像内容。短层为每帧 256×256，飞行纹与预警为每帧 256×64。两项技能均保留归霞弓、实装搭箭和实际飞行箭模型。素材、技能 ID、触发、枢轴与清理条件见[落霞美术轨道表](../luoxia-art.md)。

| 技能 | 真实客户端与服务端观测 |
|---|---|
| 千羽照野 | 第 1165、1177、1189 个世界刻各放一轮，两端均收到九支独立飞行箭。提前收弓中断的另一轮只产生已发的三支箭，对手重新入视野后补播数为零。盾前贴近射击时，九条飞行片段只产生三次有效命中亮点，目标生命保持 560。 |
| 霞沉一线 | 第 2238 刻锁向，随后将落霞视角转向 38°，第 2245 刻仍沿原方向发出一箭。实测预警从 Y=65.50 上扬到 Y=67.29，止于 Z=110 的墙面。免伤目标接触为暗片，有效命中为亮片，位置分别跟随实际接触点。引导中打断时有蓄势包、飞行箭为零；最少粒子的敌方客户端仍收到锁线与一支真实箭。 |

并排录像左侧为施法者，右侧为敌方客户端，均从同场并行连拍生成。项目保留原始两个视角的独立录像，供逐段查看。

[千羽照野并排录像](assets/battlefield-visuals/thousand-feathers-pair.mp4) · [施法者](assets/battlefield-visuals/thousand-feathers-first.mp4) · [对手](assets/battlefield-visuals/thousand-feathers-enemy.mp4)

[霞沉一线并排录像](assets/battlefield-visuals/sunset-line-pair.mp4) · [施法者](assets/battlefield-visuals/sunset-line-first.mp4) · [对手](assets/battlefield-visuals/sunset-line-enemy.mp4) · [对手最少粒子](assets/battlefield-visuals/minimal-lance.mp4)

`build`、发行包命名空间检查及六项 GameTest 已通过。专项为 `luoxia`、`arena_tower_3v3`、`arena_tower_5v5`、`tower_cover`、`first_group`、`combat_regression`。其中 `first_group` 在重复使用的测试世界中曾出现焚诀既有音效时序断言失败；同一构建换全新隔离测试世界后通过。服务端原始断言与客户端画面分别记录。

本轮到两大招人工看样为止。普通攻击与其余六个候选技能的专属 2D 图层待看样确认后制作。正式实例保持手动安装，樱隙前线原图与两大招最终美术判断均待人工验收。
