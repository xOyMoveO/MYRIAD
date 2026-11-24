# 守护／毁灭阵营统一

2026-11-17，工程`E:/mod/MYRIAD-main-20261117`，分支`codex/guardian-destruction-factions-20261117`；HEAD仍`230417f07ad67055d92d4c4854d8ca50216bb702`。继承训练玻璃界面、C面板和HUD的未提交工作，没有更改独立旧NeoForge工作树、提交或上传远端。

## 实现范围

- 阵营改为`Faction.GUARDIAN`／`DESTRUCTION`，输出ID为`guardian`／`destruction`，显示守护／毁灭。同步、计分和阵营常量/变量/方法名移除旧宗门术语；网络数字仍1/2、字段编码次序不变，训练动作和命令补全改新ID。联机双方需同步更新。
- 两语言移除旧宗门称呼，覆盖选角、队伍管理、训练、提示、计分、结算、泉水/防御塔/兵线命名、地图、商店和教学；素岚历史标记说明改为所有队友，不保留旧阵营限定说法。角色自身主题、技能数值及共享角色池规则保持。
- 守护蓝色、毁灭红色集中在Faction颜色定义；更新Tab、队伍管理、选角、训练按钮、结算、战术地图、泉水/灵脉/塔粒子与名字。兵线普通/攻城纹理、塔及水晶模型选择同步，不修改模型、贴图或音频文件。
- 兼容入口保留旧字符串、旧Gson枚举值、积分/占点人数字段和已摧毁防御塔键；旧玩家的生产Codec也能读入。地图可混合新旧ID，并按规范阵营去重校验；新保存输出新ID，不在安装时改写地图/配置/存档。
- 历史日志、原策划不追溯覆盖；`equipment.myriad.youming`是无关旧装备“幽冥刺”的资源ID，不是阵营，未借此任务删除装备兼容代码。旧灵源流程保持原停用边界，没有复活或重新设计旧玩法。

## 验证

- 首轮build+faction_identity：1/1通过41秒；补充生产玩家Codec读写后再次build+专项1/1通过31秒。验证新/中文ID、旧玩家/检查点、编号1/2、新写入ID、积分/胜方/已摧毁塔状态、蓝红模型、真实服务端计分板队名与颜色，以及新/旧/混合3v3配置。
- training_ui独立1/1通过29秒；最后仅补阵营按钮文字颜色后build+training_ui再次1/1通过37秒。覆盖上轮训练权限/功能与聊天延期打开、响应式布局；不是图形客户端人工验收。
- combat_regression独立1/1通过29秒；arena_fixture合成双模式独立1/1通过46秒、346断言，覆盖生产分队/选角/设施和生命周期。没有读取原地图或实际用户世界。
- 命名审计、JAR CRC、Fabric身份/依赖、测试类及夹具排除、810生产class一致性、1275源指纹、相对前一安装包差异白名单通过。两语言值不再包含旧宗门词，旧阵营语言键删除，相关所有其他美术/音效字节保持。
- 原文件保留CRLF；使用`git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check`检查，避免把既有CRLF误判为空白错误。未启动图形客户端，真实联机及视觉仍待人确认。

## 安装

- QA：`E:/mod/outputs/guardian-destruction-factions-20261117`，含日志/XML、审计/源指纹、安装脚本和记录及同包。
- 最终26530552字节，SHA256 `ED0AC2E8D42442E93FC12E6138F3E6A6FF6155C69E21C5CE23EE3DA3D8F029C7`。
- 20:13:08核对目标实例未运行、路径无链接、源码/旧包指纹、Fabric依赖及独占读取后，备份并原子替换`D:/minecraftmodrinth/profiles/万象MYRIAD/mods/myriad-0.1.0.jar`，哈希一致。
- 前一3E42D0CE…包备份`E:/mod/outputs/guardian-destruction-factions-20261117/backup-20261117-201308/myriad-0.1.0.jar`，可恢复。其他模组哈希不变，不启停游戏、不修改配置/存档/地图；`liveClientAccepted=false`。
