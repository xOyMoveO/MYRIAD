# 凌渊：透明像素画特效图集

用户最终明确：像素风指画面风格，不是简单方块。采用多张二维透明贴图在三维空间交叉、旋转及分层，精细飞剑模型只作为技能主体。舍弃此前方块符柱、方块碎光、实体螺旋条及实体符环的运行时装饰；旧美术模型文件留存，不再用于当前凌渊技能的这些装饰层。

生成方式：内置图像生成工具，两次调用，一张2×2图集及一次留白修订。不使用CLI/API密钥。最终选中源图为`pixel_effect_atlas_padded.png`；`pixel_effect_atlas_source.png`为初次边界留白不足的制作过程留档，不进入游戏资源。

## 制作与输出

- 单图集包含四种素材：左上剑光、右上流光尾迹、左下金色符阵、右下命中闪光。它们是独立静态图层，不是四帧动画。
- `tools/vfx/PrepareLingyuanPixelAtlas.java`等分四格，各格保留同样中心与留白，按最近邻归一到128像素网格，再放大成256×256 RGBA。不重绘形状，不对烟气/辉光强制二值化，不加入黑底或棋盘格。
- 最终游戏资源在`src/main/resources/assets/myriad/textures/particle/vfx/`：`lingyuan_pixel_slash.png`、`lingyuan_pixel_stream.png`、`lingyuan_pixel_seal.png`、`lingyuan_pixel_impact.png`。
- 四张图均验证真实透明、部分透明、全透明边缘、至少16像素安全留白。它们使用正常透明混合，不要求加法黑底或后处理bloom。
- 运行时组合在凌渊独立的`SwordPixelLayers`和`SwordVfxGeometry`，每个技能自己的表现入口组织时机；不是将整张四格图贴到一个面上。
- 实际Java几何离线预览在`E:/mod/outputs/lingyuan-held-allskills-20261105/runtime-skill-*.png`。该预览不是Minecraft截图，不代表多人帧率测试。

最终交付：`E:/mod/outputs/仙凡劫_凌渊手持武器与像素叠层特效_20261105.jar`，27531586字节，SHA256 `F051E3609857D66B8F49DA3B7F17EFC712020A06E0C77ECC60902287F0ABB1AE`。已核验包内186块手持模型、48项Sprite及四张新贴图的逐文件哈希，构建和隔离专项通过。未自动安装；日志`build/lingyuan-layered-pixel-final-20261105.log`。

## 生成提示词（首版）

Create ONE game-ready VFX texture atlas PNG, square 1024 x 1024, true transparent background. This is a 2D pixel-art texture asset for a Minecraft eastern fantasy swordsman, NOT a 3D scene and NOT voxel cubes. Strict 2 columns by 2 rows of equal 512px cells, no drawn grid. Each effect is isolated in its own cell with at least 48 px fully transparent margins; no crossing cell edges, no clipping. Top left: one elegant sharp sweeping crescent sword slash, pointed tapered ends, white-hot edge, layered turquoise and cobalt-blue flame-like ribbons, a few violet pixel fragments. Top right: one horizontal streaming comet trail, narrow taper on the LEFT, energetic broad head on the RIGHT; several interwoven cyan, royal blue and violet wisps, fine ivory highlights, broken angular pixel clusters; NOT a sword or projectile model. Bottom left: one intricate complete round golden eastern magical sigil, exact front-on orthographic plane, transparent open centre, multiple thin segmented rings, angular decorative glyphs and cloud motifs, tiny turquoise accents. Bottom right: one energetic radial sword-hit burst, luminous small white centre, asymmetric cyan/blue sharp spikes, gold cross-spark accents, violet fragments, transparent space between rays. Art style: exquisite hand-crafted 2D pixel art, meaningful clustered pixels, crisp staircase contours, fine multicolour internal texture, approximately 128x128-native-pixel detail per cell upscaled 4x using nearest-neighbour. Rich saturated palette with strong light/dark hierarchy, no blur, no smooth airbrush, no vector-smooth contours, no cubic blocks, no 3D rendered chunks, no text, no labels, no frames, no ground, no objects other than the four VFX motifs. Alpha may be partial inside energy trails, fully zero outside every silhouette; do not draw a checkerboard background. These images will be layered on intersecting transparent planes in a 3D game.

## 留白修订提示词（选中版本）

Edit this exact four-cell game VFX atlas. ONLY fix padding and separation: keep all four existing motifs, their pixel-art appearance, colours, orientation and exact 2x2 cell assignment. Shrink EACH entire effect including its glow to 72% of its current cell width and height, centre it within its cell. Add a wide fully transparent gap all around EACH motif, including at the atlas outer edges and both internal midlines. Nothing may touch or cross any cell boundary. Preserve the complete tips and glow without clipping. Use genuinely alpha=0 transparent background, including the centre hole of the gold sigil and gaps between rays. No added objects, words, frames, checkerboard or scene. Preserve crisp pixel clusters; do not redraw as 3D cubes or blur the effects. Output one square transparent PNG texture atlas.
