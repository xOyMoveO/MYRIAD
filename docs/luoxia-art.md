# 落霞 · 美术来源与首版边界

- 头像和两张技能图标源板由内置 ImageGen 生成。头像导出 256×256，八个候选图标分别裁切到自己的完整铜边，运行资源为 256×256，64×64 图标预览用于核对轮廓。
- 三种普攻和五个射箭技能各有独立的飞行模型。可编辑源文件在 `art_source/luoxia/projectiles/`，运行 `python tools/assets/generate_luoxia_arrows.py` 可重建模型、图集和运行时网格。箭尖对齐服务端弹道。尾迹和命中各有四帧 128×128 的像素贴图，由 `tools/build_luoxia_art.py` 生成，再叠加轨迹芯和碎羽。
- 归霞反曲弓采用专属立体模型，分待机、起弓、半拉、满弓四态，共用 256×256 贴图和同一握点。Blockbench 源文件位于 art_source/luoxia/bow/。沿用原有手臂动作、音效和射击判定。
- 色彩：暖白 `#FFF2CB`、晚霞金 `#FFC65D`、珊瑚 `#FF765B`、暮紫 `#624A83`。

## 已使用的生成提示词

### 头像

Use case: stylized-concept. One square game roster avatar for MYRIAD original character Luoxia, an adult female sunset courier of floating islands. Head and shoulders, thoughtful steady eyes, dark indigo hair in a practical loose braid, short layered dark violet woven travel cloak with sunset copper clasp and simple honey-amber stud, coral warm rim light. Small distant floating-island silhouettes against sunset behind her, low detail background so face reads at 24 pixels, dynamic but front three-quarter pose, crisp richly shaded premium pixel-art game portrait, meaningful pixel clusters, deep ink violet shadows, warm white and gold highlights. Full head fully inside safe margins, no object in hands, no text, no watermark, no frame.

### 追霞五图：追霞续弦、雁过霞痕、斜照撤步、飞羽连弦、千羽照野

Use case: stylized-concept. Generate one exactly horizontal 1-by-5 sprite collection, five independent equally sized square icons, centered, aligned edges, thin neutral gutters. Premium crisp hard-edged 2D pixel art for MYRIAD game UI, very dark ink-purple textured backdrop, compact dark bronze borders individually enclosing each square, amber pixel corner accents. Left-to-right exact five visual symbols: (1) two coral sunset feathers curling in succession in a dark bow-shaped wind trail, (2) two matching gold feather shapes converge onto a small amber eye-shaped seal, (3) a diagonal sweeping crest of two coral-gold feather streaks with a low offset wind curl, (4) exactly three warm-white streaks with coral tapered tails in quick succession, (5) three stacked waves of small amber and coral star trails forming a glowing fan. Warm ivory, honey gold, coral, deep muted purple. Strong readable small-size silhouettes with dense but carefully clustered pixel details. No humans, text, glyphs, hotkeys, numbers or watermark.

### 凝霞三图：凝霞满弓、一点残阳、霞沉一线

Use case: stylized-concept. The provided image is style reference only. Create NEW square icon artwork in an exact horizontal 1×3 strip, 3 equal independent squares, each with a complete narrow dark bronze border and small amber corners, matte midnight-violet background, matching premium hard-edged sunset pixel style. No text, no humans. From left to right: (1) four tiny amber light rays converging tightly into a luminous diamond focal point, precise symmetrical geometry with a violet ring; (2) a small red-gold sun disk aligned to ONE thick diagonal ivory and coral shooting-star streak, painting a long visible tapered tail; (3) one major perfectly straight ivory-gold ray crossing three vertically offset, aligned violet crystalline waypoints, slightly diagonal dynamic composition. Shapes unmistakable at 32 px. Keep complete frames with narrow readable gaps and no neighboring imagery bleeding over.

## 第二版技能图标

八张 256×256 像素插画采用浮岛晚霞背景和细铜框。五格板从左到右为手套续弦、双羽肩印、侧撤靴步、三支错拍轻箭、峡谷扇形箭幕；三格板为侧身满弓、落日重箭、穿过三道光环的水平贯线。新源板分别使用各自实测的边框坐标，导出时给原脚本加 `--detailed-icons`。

## 两项终极技能的场内 2D 轨道

这批素材叠在归霞弓和已有飞行箭模型上。两张透明母图由 ImageGen 按四个等大分区绘制，依次为蓄势、释放、飞行纹和接触碎片。千羽使用三枚向心羽尖、同点展开的窄羽冠、单根收尖金羽和小片羽屑。霞沉使用暮紫聚线、暖白直芯与双侧残霞、细直金线和有方向的碎片。共用提示要求实际箭向朝右、硬边像素簇、暖白／晚霞金／珊瑚／暮紫配色、真透明背景与分区留白。源图在 `art_source/luoxia/vfx/`，用 Pillow 运行 `python tools/assets/build_luoxia_ultimate_layers.py` 可重建十张运行图集。

各轨均为从左至右四帧，靠实际事件控制播放。发射纹从左端接入真实箭路，聚线与命中纹居中。短纹每帧 256×256，细线每帧 256×64。飞行箭的模型独立于贴图寿命。表中“每段”指服务器已经走完并裁切的弹道片段。

| 技能 ID | 贴图轨道 | 触发与枢轴 | 播放和清理 |
|---|---|---|---|
| `luoxia_thousand_feathers` | `volley_charge` | 起手时，三枚羽尖在弓前聚拢 | 随有效起手短时刷新，取消后停止 |
| 同上 | `volley_crown` | 每轮实际释放，左端对齐发射点，沿本轮中心方向展开 | 每轮独立播放一次，末轮收窄 |
| 同上 | `volley_trail` | 三枚实箭各自经过的片段，贴图中点对齐段中点 | 每段分别淡出，中央箭加一笔暖白细芯 |
| 同上 | `volley_hit` | 本轮对同一目标首次有效命中，枢轴在接触点 | 一次短闪；同轮重复接触收尾 |
| `luoxia_sunset_line` | `lance_charge` | 锁向前的弓前聚线，中心枢轴 | 起手短时刷新，锁向后转入预警 |
| 同上 | `lance_warning` | 锁向后沿真实三维射线，线段中点对齐，末端止于首个障碍 | 锁向期间刷新；释放或取消后消散 |
| 同上 | `lance_release` | 一次真实释放，左端对齐发射点，沿已锁方向 | 单次短闪 |
| 同上 | `lance_trail` | 实箭每段飞行，中点对齐当前段 | 每段独立淡出，接近墙体时随裁切缩短 |
| 同上 | `lance_hit` | 有效命中的接触点，含护盾吸收 | 每次有效命中一次短闪 |
| 两项终极技能 | `lance_graze` | 接触但伤害未生效时，枢轴在接触点；千羽同轮重复接触省略 | 暗铜色碎片，四个游戏刻内结束 |

最少粒子保留三维预警线、飞行模型、普通四帧尾迹及确认命中。两项终极技能都沿用已有技能特效包的距离、视野与跟踪条件。
