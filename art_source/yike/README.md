# 虹列：可编辑模型与素材来源

本目录保存伊珂武器的概念参考、原始贴图及 Blockbench 源模型。正式游戏资产为同源导出的 `models/item/hong_lie*.json`、`textures/item/hong_lie.png`；三瓣、主架与聚焦环独立，运行时按服务端认可的展开进度组合。

- 全长约 1.30 格；模型握区中心 `(8,8,8)`，暴露握柄约 0.34 格；三瓣铰轴在握区前方 `5.5/16` 格。
- 携行态三瓣收拢；定轨三瓣各展开 30°，聚焦环前移约 0.084 格；游走保留上瓣收拢、两侧半展开并有独立轻微导轨位移。
- 第一人称使用短前臂与皮肤手掌，实际握区和武器共用变换；第三人称由独立炮口认可方向设置持握手臂，武器正向沿手臂轴。
- `hong_lie*.spec.json` 是现有几何逆向后的编辑规格，`hong_lie*.bbmodel` 内嵌与运行时一致的 256×256 材质贴图，移动到其他目录后仍能打开显示颜色。源模型 UV 使用贴图像素坐标，运行时 JSON 按贴图尺寸转换到 0—16。
- 材质区分深紫缠柄、珊瑚红护瓣、紫罗兰内衬、黄铜/淡金接件与暖白细晶。八段聚焦环保持真实镂空，环段接合；三条细导轨延长到聚焦环最大前移处，闭合时保留短导轨端，展开时环沿细轨前移。细轨厚度、握区、拆件局部原点和现有展开参数保留。
- 武器独立生成入口为 `python tools/assets/generate_yike_weapon.py`，依赖 Pillow 及项目本地 `minecraft-ai-generate-bbmodel` skill 的规格编译器。它读取本目录规格，保留细部的六位小数精度，再生成四个源模型、四个运行时模型与共用贴图；不会修改图标或音频。
- `python tools/assets/render_yike_weapon.py` 使用 `tools/assets/render_bbmodel.py` 对真实六面 UV、立方体、原点及旋转进行软件栅格渲染，按现有运行时拆件变换展示携行闭合、游走半展和定轨展开。它不启动游戏。
- `hong_lie_model_preview.png` 为三种姿态及拆件总览；`hong_lie_closed_preview.png`、`hong_lie_mobile_preview.png`、`hong_lie_deployed_preview.png` 为单态图；`hong_lie_parts_preview.png` 为拆件；`hong_lie_muzzle_preview.png` 为炮口顶视与斜视。它们是实际着色源模型预览，不是概念伪图或游戏内截图。
- `hong_lie.bbmodel` / `hong_lie.json` 保留物品栏静态三瓣包围盒表示；持握姿态使用 frame/petal/ring 三个独立模型按 120° 组装。上述姿态预览展示实际持握拆件组合。
- `tools/assets/generate_yike_assets.py` 保留原有17个声音的合成入口，并调用武器独立入口；旧占位图标绘制段已移除，当前图标源位于本目录。运行该旧总入口会重写原有音频，依赖 Pillow、soundfile；原有声音为单声道 Ogg Vorbis，没有外部采样包，本轮没有重制音频。
- 八个技能图标独立保存：`icon_source.png` 为原始图源；`icon_export.py` 记录完整提示词和裁切坐标；`icon_contact_sheet_native.png` 与 `icon_contact_sheet.png` 分别供原尺寸和放大检查。运行时图标目录为 `src/main/resources/assets/myriad/textures/gui/sprites/skill_icons/generated/yike/`，不由旧总生成器覆盖。
- 本轮安装于临时虚拟环境 `/private/tmp/yike-asset-env` 的依赖仅用于素材制作，不是游戏运行依赖。
- `hong_lie_concept.png` 来自内置图像生成工具，保留为历史造型参考；实际模型以上述模型预览为准。当前只完成静态资产检查，第一/第三人称握持、遮挡、展开时序和客户端画面仍待单独验收。

概念生成完整提示词：

> Use case: stylized-concept. Create an exact production concept sheet for an ORIGINAL fantasy game weapon 'Prismatic Array' for MYRIAD. Neutral charcoal studio backdrop. Show the SAME slender 1.25-meter deployable arcane artillery STAFF twice in large three-quarter views: LEFT folded carrying configuration 0.20m wide, RIGHT deployed configuration three petals open to 0.65m wide. Add matching small side, top and grip orthographic views at bottom. Keep geometry perfectly consistent across states. Lower half long dark-violet cylindrical handle with a clearly exposed one-hand grip, pale-gold collars and counterweight. Upper elongated head: THREE physically connected hinged coral-red outer petals with violet inner panels, open focusing framework ALONG THE LONG AXIS of the staff, thin warm-white crystal core, hollow pale-gold mechanical focusing rings and rails aligned with long axis; front ring slides forward when deployed. Fine brass bevels, ivory inlays, controlled crisp pixel-painted surface detail suitable for Minecraft cuboid modeling. Readable connector hinges, no floating unconnected parts. No rifle stock, no trigger, no magazine, no scope, no wooden staff, no fat block cannon, no huge glow. Complete uncropped weapon and all views. No character, no hand, no words, no UI, no watermark. Very restrained internal emission so actual shape is fully readable.
