# 万象 · 角色头像提示词（2026-11-23）

用于角色选择、角色图鉴和个人信息页。统一为方形头像，保留各角色的年龄、发型、衣饰与主题色，不把科幻或工业角色画成同一套仙侠服装。本轮使用内置 ImageGen，为每个角色独立生成一张。

## 新角色怎么用

1. 把「通用提示词」原文、一个参考段落、以及新角色的「Subject」依次拼成同一条提示词。角色段只写脸型/年龄、发型、衣装轮廓、主题色及一个配件或特效；镜头、光线和裁切不要每个角色自己改。
2. 有旧头像时将它作为 Image 1 的身份参考，不让它决定新构图。首次设计角色时用「无旧头像」段；需要进一步对齐画风时，可另附本批任意成品作为风格参考，追加：`Use the additional MYRIAD portrait as a style reference only for face scale, block density, key lighting and backdrop quietness. Do not borrow that character’s face, hair, clothing or colors.`
3. 验收方图、主体单一、无文字和内嵌边框。检查 40px 下的眼睛和头部轮廓；保持原色并导出不透明 sRGB 256×256 PNG，文件名用角色内部 ID，放到 `src/main/resources/assets/myriad/textures/gui/sprites/portraits/<id>.png`。UI 通过 `portraits/<id>` 自动加载。保留在 24px 和 40px 下仍能辨认的发型/轮廓，避免只靠背景颜色区分。

## 通用提示词

> Create one new square 1024×1024, full-bleed opaque character-selection portrait for the MYRIAD Minecraft game. Single adult character, tight head-and-upper-shoulders bust, eye-level, subtle 15-degree three-quarter turn, eyes near 45% down and face near center. Leave 6–9% clear headroom and keep eyes, mouth or mask, and the defining head silhouette in the central 80%; shoulders may crop naturally. Premium handcrafted voxel portrait: crisp stepped block clusters, strongly sculpted cubic hair and material panels, restrained microtexture, warm soft key from upper-left, modest theme-colored edge light, high contrast face readable as a 40×40-pixel game avatar. Fill the square with a low-detail charcoal or deep desaturated theme backdrop, with exactly one unobtrusive visual motif beside or behind the head. Keep props peripheral and subordinate. No text, border, badge, watermark, split panel, smooth plastic skin, photographic realism, oversized glowing particles or full-body shot.

## 参考段落

**已有头像（Image 1）：**

> Input image 1 is this character’s previous portrait, an identity reference only: preserve recognizable facial structure, age, hairstyle silhouette, costume culture and signature hue. Redraw the lighting, framing and surface detail; do not copy the old exact composition.

**无旧头像：**

> This is a new portrait for an existing character concept. Use only the role directions below for identity. Match the set-wide framing and voxel treatment.

## 本轮逐角色提示词

每次实际发出的提示词 = 通用提示词 + 上面的指定参考段落 + 下列 Subject 原文。

### 夜影 · `yingxiao`

已有头像（原游戏头像作为 Image 1）。

> Subject: Charcoal-hooded male assassin, sharply layered black hair, pale face partly covered by a low charcoal cloth mask, two violet eyes clearly visible, one shoulder with dark gray overlapping leather plates and muted bronze buckle. A few restrained dark-purple shadow wisps and a small sliver of the silver-edged dagger at a lower side; stealthy, not a villain monster.

### 天刑 · `tianxing`

已有头像（原游戏头像作为 Image 1）。

> Subject: Stern young adult male thunder swordsman, midnight-blue raised ponytail held by a silver-blue clasp, cobalt blue eyes, angular white-silver and navy armor. Add one small pale-cyan lightning fork behind the shoulder and a gold-collared thunder-sword hilt on the edge; keep face neutral and focused.

### 焚诀 · `fenjue`

已有头像（原游戏头像作为 Image 1）。

> Subject: Broad-shouldered adult male fire warrior with dense copper-red swept hair, short rust beard and amber eyes. Dark maroon basalt armor with orange seams and forged brass. One controlled ember tongue behind a shoulder and a few sparks; warm orange against deep umber, never yellow inferno across the face.

### 墨尘 · `xuanchen`

已有头像（原游戏头像作为 Image 1）。

> Subject: A composed adult male ink-and-talisman tactician with long obsidian hair and a tall folded black scholar crown with pale jade fastening. Black and ivory robes with clean brushwork collar, one folded paper seal off to the side, subtle old gold. Use a parchment glow behind the shoulder against cool charcoal; no readable inscription.

### 素岚 · `sulan`

已有头像（原游戏头像作为 Image 1）。

> Subject: A lithe adult woman wind duelist with dark brown high ponytail, long celadon hair tie, clear jade eyes, porcelain-white and muted celadon travel robes. One narrow cool-green ceramic sword edge and short pale wind feather behind the shoulder. Strong upward hair silhouette; lively unobstructed face.

### 凌渊 · `lingyuan`

已有头像（原游戏头像作为 Image 1）。

> Subject: An experienced male swordmaster with long silver hair in a high knot, neat short silver beard, teal eyes and elegant dark teal robes layered with muted gold trim. One narrow silver jian silhouette with indigo fuller and old-gold guard behind one shoulder, and a faint blue-purple blade glint. The master looks composed and mature.

### 寒泽 · `hanze`

已有头像（原游戏头像作为 Image 1）。

> Subject: A reserved young adult male frost mage with blue eyes, silver-white hair swept down in long strands, navy coat and visibly angular blue ice shoulder pieces. Include a small sharp light-blue frost crystal at a side, dark glacier background, clean ice white-blue against navy and no full snowfall.

### 青萝 · `qingluo`

已有头像（原游戏头像作为 Image 1）。

> Subject: A gentle adult woman herbalist with dark brown twin buns, green eyes, a small jade forehead pendant and pale herb blossoms. Warm cream botanical robe with soft sage and leaf-green accents, plus a partial bronze-and-leaf staff or tiny mint seed gem beside the shoulder. Let the living greens feel warm and earthy.

### 赤霄 · `chixiao`

已有头像（原游戏头像作为 Image 1）。

> Subject: A resolute adult male heavy blade warrior with a red topknot, red-brown eyes, boxy short beard and broad hematite lamellar shoulder plates with red lacquer, copper trim and engraved brass details. A silver cleaver spine behind one shoulder. Use matte dark red and tarnished copper, without the flowing hair, flames or molten seams of Fenjue.

### 云笙 · `yunsheng`

已有头像（原游戏头像作为 Image 1）。

> Subject: A warm adult woman spirit covenant caster, long dark indigo hair and clear blue eyes, ivory hair ornaments with jade bead ties, ivory and cloud-blue cloth. A small celadon bell with gold ribs and ivory tassel at the bottom side, one tiny distant folded paper crane. Airy icy ivory-and-blue accents, gentle expression.

### 朔 · `shuo`

无旧头像。

> Subject: A quiet adult male iaido duelist with loose black fringe, an angular tied-back black topknot, a small blue-gray cloth tie and silver-gray eyes. Inky-navy wrap coat with ivory collar and a dark lacquered upright scabbard edge with a restrained silver fitting near one shoulder. No exposed blade. Thin pale-blue crescent rim accent; calm and restrained.

### 刻衡 · `keheng`

无旧头像（游戏内以前是武器占位图，因此没有将它当成人物身份参考）。

> Subject: An adult male precision time duelist with neat blue-black hair brushed back, copper-gold eyes and a fitted deep petrol short coat, brass measuring lines at the collar and a small escapement detail near one shoulder. One incomplete pale amber timing arc behind him, tiny needle motif, cool composed expression; no giant clock or medieval robe.

### 伊珂 · `yike`

无旧头像。

> Subject: An adult woman future-artillery mage, athletic and alert, with short angled deep-violet hair and a pale blue visor clipped at the temple while both eyes remain unobstructed. Short violet technical cape, light ivory armor, small coral fastening; a cropped clean white-and-blue photonic focusing fin on one lower shoulder, restrained icy cyan optics. No longrobe or full helmet.

### 铆钉 · `rivet`

无旧头像。

> Subject: A broad adult male industrial combat engineer with close-cropped rust-brown hair, a squared stubble jaw, a shaved temple line, clear amber-brown eyes, steel-blue overbuilt work armor and lifted amber safety goggles over the brow. A worn yellow collar panel and cropped squared steel hammer assembly at a lower corner, restrained cyan indicator. No large helmet, goggles do not cover his eyes.
