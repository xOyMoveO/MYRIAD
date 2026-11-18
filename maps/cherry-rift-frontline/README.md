# 樱隙前线

樱隙前线是一张面向 Minecraft Java 版 26.3、约 320 × 240 格的浮岛建筑地图。布局包含贯穿主岛的中路、两侧的庭园和工业野区、两端水晶后的复活泉，以及通过双桥抵达的首领岛。

从中路两侧可进入樱树遗迹庭院和白石拱廊花园。前往野区时，可绕游铜管泵房与竹林石灯庭。

野怪以静态展示实体呈现，防御塔和水晶为建筑布景。配置对局时，需按本仓库的地图设施编辑流程另行设置路线、阵营设施和营地。

## 打开地图

1. 下载 [`cherry-rift-frontline.zip`](cherry-rift-frontline.zip)。
2. 将压缩包解压到 Minecraft 26.3 实例的 `saves/`。确认解压出的存档目录内有 `level.dat`、`resourcepacks/resources.zip` 和 `datapacks/cherry_rift_fountains.zip`。
3. 在单人游戏中打开解压出的樱隙前线存档。存档自带资源包和数据包。打开后可查看六种自定义野怪模型并使用复活泉。

![Fabric 26.3 中的樱隙前线实机全景，毁灭在左，守护在右](preview.jpg)

## 复活泉

沿中路朝主岛两端走，经过水晶即可抵达复活泉。守护（蓝色）泉位于 `(-153, 81, 39)`，毁灭（红色）泉位于 `(153, 81, -39)`。

在主世界中，生存或创造模式的玩家进入任一泉心半径 6 格的球形范围即可设置个人重生点。范围内每 2 秒恢复 4 点生命值，离开后停止额外恢复。死亡后会在最近到访的泉水重生，朝向中路。重生点会随存档保留，也可通过原版的床重新设置。未使用复活泉或床时，仍使用世界出生点。

## 编辑资源

- [Blockbench 模型](../../art_source/cherry-rift-frontline/)内嵌像素贴图。包含庭园野怪 `dusk_fox`、`frost_hare`、`jade_beast`，工业野怪 `heavy_walker`、`scout_bot`，以及首领 `rift_crab`。
- [地图资源包](resource-pack/)保留 Minecraft 26.3 读取的物品定义、几何和贴图。地图自带的 `resourcepacks/resources.zip` 对应这份资源包。
- [复活泉数据包](datapack/)保存了存档内的数据包源码。编辑后，将目录内容压缩成 `cherry_rift_fountains.zip`，确保 `pack.mcmeta` 位于压缩包根目录，替换存档副本 `datapacks/` 下的同名文件。

地图、原创模型和贴图遵循[项目许可范围](../../src/main/resources/META-INF/LICENSE-SCOPE.txt)。
