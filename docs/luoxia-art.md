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
