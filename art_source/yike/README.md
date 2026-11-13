# 虹列：可编辑模型与素材来源

本目录保存伊珂武器的概念参考、原始贴图及 Blockbench 源模型。正式游戏资产为同源导出的 `models/item/hong_lie*.json`、`textures/item/hong_lie.png`；三瓣、主架与聚焦环独立，运行时按服务端认可的展开进度组合。

- 全长约 1.25 格；模型握区中心 `(8,8,8)`，暴露握柄约 0.34 格；三瓣铰轴在握区前方 `5.5/16` 格。
- 携行态三瓣收拢；定轨三瓣各展开 30°，聚焦环前移约 0.084 格；游走保留上瓣收拢、两侧半展开并有独立轻微导轨位移。
- 第一人称使用短前臂与皮肤手掌，实际握区和武器共用变换；第三人称由独立炮口认可方向设置持握手臂，武器正向沿手臂轴。
- `tools/assets/generate_yike_assets.py` 是模型、贴图、八图标与17个声音的原始生成代码。使用 `python`，依赖 Pillow、soundfile；声音保存为单声道 Ogg Vorbis。没有外部采样包。
- 本轮安装于临时虚拟环境 `/private/tmp/yike-asset-env` 的依赖仅用于素材制作，不是游戏运行依赖。
- `hong_lie_concept.png` 来自内置图像生成工具，作为具体造型参考；未冒充游戏内截图。当前造型按父规格三瓣/握区/聚焦环契约落地，实际客户端验收另记。

概念生成完整提示词：

> Use case: stylized-concept. Create an exact production concept sheet for an ORIGINAL fantasy game weapon 'Prismatic Array' for MYRIAD. Neutral charcoal studio backdrop. Show the SAME slender 1.25-meter deployable arcane artillery STAFF twice in large three-quarter views: LEFT folded carrying configuration 0.20m wide, RIGHT deployed configuration three petals open to 0.65m wide. Add matching small side, top and grip orthographic views at bottom. Keep geometry perfectly consistent across states. Lower half long dark-violet cylindrical handle with a clearly exposed one-hand grip, pale-gold collars and counterweight. Upper elongated head: THREE physically connected hinged coral-red outer petals with violet inner panels, open focusing framework ALONG THE LONG AXIS of the staff, thin warm-white crystal core, hollow pale-gold mechanical focusing rings and rails aligned with long axis; front ring slides forward when deployed. Fine brass bevels, ivory inlays, controlled crisp pixel-painted surface detail suitable for Minecraft cuboid modeling. Readable connector hinges, no floating unconnected parts. No rifle stock, no trigger, no magazine, no scope, no wooden staff, no fat block cannon, no huge glow. Complete uncropped weapon and all views. No character, no hand, no words, no UI, no watermark. Very restrained internal emission so actual shape is fully readable.
