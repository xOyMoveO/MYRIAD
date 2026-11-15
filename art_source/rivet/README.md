# 铆钉表现资源

`model_reference_approved.png` 是本轮用户确认的原创造型参考，由内置图像生成工具制作；它表达冲击锤、战术锚点和持握体量方向，不是实际模型或游戏截图。正式资产按该图还原冲击砧、三道锤面横槽、通风机壳、开放活塞笼、侧置细液压管，以及墙锚的四角黄夹爪、锁定活塞与分层中空索环。圆柱、倒角与小型装甲结构按 Minecraft 可编辑立方体作了取舍。图中的重装工程兵仅为比例示意，本轮没有新增角色皮肤或人体模型。

## 模型与材质

| 作者规格／可编辑模型 | 立方体数 | 运行缓存有效面 | 本地 +Y 最前端 |
|---|---:|---:|---:|
| `hammer.spec.json` / `hammer.bbmodel` | 26 | 130 | 27.5 |
| `anchor.spec.json` / `anchor.bbmodel` | 24 | 117 | 8.5 |
| `hammer_lod.spec.json` / `hammer_lod.bbmodel` | 22 | 110 | 27.5 |
| `anchor_lod.spec.json` / `anchor_lod.bbmodel` | 13 | 68 | 8.5 |

四套模型共享原创 256×256 不透明材质 `rivet_metal.png`，区分石墨机壳、钢结构、银色活塞、黄铜安全端帽、橡胶握柄与少量冷青指示。锤面三槽、通风口、螺栓、握柄横纹和局部划痕使用绑定到实际表面的 UV 细节，不以随机色点代替材质。BBModel 均内嵌相同 PNG，可独立移动和编辑；发行纹理分别位于 `textures/item/rivet_metal.png` 和 `textures/particle/vfx/rivet_metal.png`。

`tools/assets/generate_rivet_models.py` 调用项目 BBModel 技能的结构化规格生成器，导出普通物品模型 `models/item/rivet_hammer.json`、既有物品入口 `items/rivet_hammer.json` 和 `vfx/rivet_models_mesh.json`。源码模型保留完整可编辑面；运行缓存仅剔除完全被其他不透明立方体覆盖的轴对齐内面，不裁掉孔洞或猜测部分遮挡。远景模型保留相同端点、锤面、活塞笼和牵引孔；省略细管、活塞环、锁钉及前缘的小层次。世界表现根据距离／细节等级选用两级模型，第一人称使用完整物品模型。

坐标契约为 `source_length=28`、握柄轴 `X=Z=0`、抓握中心 `(0,5.5,0)`；冲击砧位于 `-X`，活塞笼位于 `+X`。锚的背板为 `Y=0`，`+Y` 指向墙外，索环孔沿此方向开放；四面竖墙使用法线和向上基向量，背板离墙 0.015 格。第三人称物品缩放为 0.88，按 Minecraft 26.2 的物品居中、XYZ 旋转与左手修正补偿握点；背包图标单独居中并缩小。第一人称握点、动作与正常渲染入口由 `RivetWeaponView` 使用，未启动游戏验证这些视角。

## 离线模型证据

- `hammer_preview.png`、`anchor_preview.png` 及两张 `*_lod_preview.png`：实际 BBModel 立方体、内嵌 UV 和材质的等轴预览。
- `hammer_detail_preview.png`：从发行 Java 物品模型和发行 PNG 重建的侧面、打击面与框架后视图，可检查三槽和机构空间。
- `anchor_wall_preview.png`、`anchor_four_wall_normals.png`：实际锚模型在预览墙夹具上的竖装、正视及四面法线；墙仅为离线示意夹具。
- `model_lod_comparison.png`：相同尺度下的完整／远景模型。
- `../../docs/verification/rivet33/models-before-after.png`：原 PR #33 与本轮资产在相同相机、相同尺度下的前后对照；旧资产由修改前的原始 BBModel 与内嵌贴图渲染。

复现需要 Python 与 Pillow，不会下载资源或启动客户端：

```bash
python tools/assets/generate_rivet_models.py
python tools/assets/render_rivet_models.py --output art_source/rivet
# 要生成前后对照，再提供原始 art_source/rivet 的保存目录：
python tools/assets/render_rivet_models.py --before /path/to/original/rivet --output /path/to/evidence
```

生成时已检查规格、正尺寸、原始坐标保真、UV 绑定／边界、唯一 UUID、内嵌贴图、完全不透明 alpha、有效面预算和缓存源指纹。离线图片已经查看；它们不能证明游戏里的视角、遮挡、听感、手感或运行性能。参考图、作者规格、BBModel、SVG、预览与辅助工具均位于发行资源目录以外，不加入 JAR。

## 图标、特效与声音来源

- `icon_source.png` 由内置图像生成工具生成，4列×2行；行优先为驻地装甲、破阵节拍、冲击锤、战术锚点、应急撑架、牵引索、固守协议、破城冲击。`icon_export.py` 逐格使用独立测量的完整边框裁切，输出不透明64×64 RGB PNG。原图、原尺寸及放大预览均已查看，八个图标的尺寸／RGB检查通过。
- `tow_release_source.png` 使用内置图像编辑工具保留原牵引索的材质与边框，制作松索时独立的开钩／分离钢索图标，输出 `rivet_tow_cable_2.png`，在真实牵引可取消窗口切换。
- 专属几何入口 `RivetVfx` 使用13号主题，组合实体锚、抬锤与挥锤、双色锚击状态、钢索、撑架、固守边界及冲锋提示。原创高清中性白 alpha 纹理 `vfx/rivet_hd_compression.{svg,png}` 与 `vfx/rivet_hd_friction.{svg,png}` 分别提供冲压破片与摩擦流，生成源为 `tools/vfx/GenerateRivetTextures.java`，运行时按黄铜／冷青色调制；少量火星作为像素修饰。
- 声音使用项目既有 Foley 的钢刃和石质撞击素材，按动作阶段调整音高与音量；没有新增外部录音、配音或生成波形，许可沿用原资源记录。

构建与游戏规则验证见本轮专项记录。未安装正式实例，客户端视觉和视角留待人工验收。
