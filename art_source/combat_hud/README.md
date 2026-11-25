# 左下角战斗 HUD 图集

美术源文件为 `generate_atlas.py`。运行 `python art_source/combat_hud/generate_atlas.py`（需 Pillow）生成 1024 × 1024 的 RGBA PNG。材质为万象项目原创像素图形，按 [项目素材许可](../../src/main/resources/META-INF/licenses/combat-hud-sources.txt) 保留所有权利。

[战斗 HUD 风格概念图](concept.png)使用内置图像生成工具，依据《万象｜普攻 15% 命中容错 + 左下角战斗 HUD》附件 A 的原创描述，画出低饱和樱树与古石路径的第一人称场景。提示词要求左下角为玄石、旧青铜与白玉的紧凑面板，分别显示血量、斜纹护盾、法力、五格技能、金币、普攻、经验，底部中央独立保留九格快捷栏，画面无字。下表的游戏纹理全部由源文件按整数像素独立绘制。

## 切片

资源 ID 为 `myriad:textures/gui/combat_hud.png`。坐标单位为源像素，原点在左上角，区域记为 `(x, y, 宽, 高)`。图块间至少 8 px 的全透明隔离带，最近邻采样。默认以全白绘制以保留切片本色，条槽允许乘色。

| 切片 | 像素区域 | 边距 / 用法 | 默认主色 |
| --- | --- | --- | --- |
| 面板 | (16, 16, 128, 64) | 四边各 8 px，九宫格角件固定，推荐目的边各 4 GUI px | 玄石 `#202B2E`，青铜 `#967956`，白玉 `#97B4A8` |
| 低血轮廓 | (160, 16, 128, 64) | 四边各 8 px，九宫格，与面板重叠 | `#CA6772` |
| 头像框 | (16, 96, 32, 32) | 四边各 4 px 为框，中央透明 | `#87795B` |
| 普通技能框 | (64, 96, 32, 32) | 四边各 4 px 为框，中央透明 | `#56686A` |
| 大招框 | (112, 96, 32, 32) | 四边各 4 px 为框，中央透明 | `#B69868` |
| 就绪描边 | (160, 96, 32, 32) | 叠在对应技能框上，中央透明 | `#84CCAD` |
| 生命槽 | (16, 160, 16, 16) | 4 px 左帽 / 8 px 中段 / 4 px 右帽 | 槽 `#292929`，填充 `#A74152` |
| 法力槽 | (16, 192, 16, 12) | 同生命槽 | 槽 `#292929`，填充 `#508EBA` |
| 经验槽 | (16, 224, 16, 8) | 同生命槽 | 槽 `#292929`，填充 `#8568AD` |
| 普攻槽 | (16, 248, 16, 8) | 同生命槽 | 槽 `#292929`，填充 `#77A193`，就绪 `#84CCAD` |
| 护盾斜纹 | (64, 160, 16, 16) | 横向平铺，尾块按剩余宽度裁去 | 暖金 `#D9AD54`，深棕 `#694622` |
| 锁 | (16, 288, 16, 16) | 居中取整缩放 | `#C7C9BF` |
| 缺蓝水滴 | (48, 288, 16, 16) | 居中取整缩放 | `#79CBF2` |
| 充能点 | (80, 288, 16, 16) | 居中取整缩放 | `#91D3B4` |
| 二段标 | (112, 288, 16, 16) | 居中取整缩放 | `#B89FE6` |
| 受控标 | (144, 288, 16, 16) | 居中取整缩放 | `#E59182` |
| 金币标 | (176, 288, 16, 16) | 居中取整缩放 | `#DBAB60` |

条槽中段可横向拉伸或平铺，端帽推荐 1–2 GUI px。血量、护盾范围、法力、经验、普攻、文字、图标及状态读数均由运行时绘制，缺失图集时使用 HUD 自带色块和文字。

<details>
<summary>概念图生成提示词</summary>

```text
Use case: stylized-concept. Generate one landscape 16:9 gameplay art-director proof for MYRIAD, an original block-game first-person arena combat mod. The scene is subordinate to the HUD design: a quiet muted voxel map with pale pink cherry tree canopies, an ancient squared slate path leading into a far low cubic stone outpost with a guard tower, modest moss and daylight haze. Orthogonal block geometry, visible low-resolution pixel textures, clear walkable center lane, middle-of-screen a tiny pale four-stroke crosshair. Framing resembles first-person game footage, no screenshot branding, no hand or weapon needed. Strict HUD layout: exactly one low compact rectangular personal combat panel at the extreme bottom-left with small margins, about 24% of canvas width and 13% of canvas height. A SEPARATE nine-slot row of ordinary dark squared game inventory cells sits precisely at screen bottom CENTER, with plenty of clear gap from the bottom-left panel. HUD stays contained to those two regions and the crosshair. Inside the bottom-left HUD, far-left is one compact pixel portrait framed by cut stone and a small blank badge beneath it. To the right of the portrait, the top row is one thick muted crimson health meter followed immediately by short fine ochre-gold diagonal shield hashing at the end of the filled part. Under the health meter is one noticeably thinner ice blue mana meter. Under the mana meter sit exactly five small equal square pixel art skill icons side-by-side in hard edged frames, one of the five with very restrained pale brass edging. On the right end of that same skill row, a small gold coin icon and a tiny muted jade recovery meter. A single fine muted purple experience line at the very bottom of the panel. Dark blue-green matte basalt, subdued thin oxidized bronze corner pieces and tiny rivets, minimal short ivory jade carvings, sparse muted jade ready edge. Squared notches stepped at the pixel level, straight regular silhouettes, quiet inner plate, high legibility, crisp material separation. This is a gameplay HUD visual concept. Do not add text, any letters, any numbers, simulated scribbling, lettering on the meters or keycap labels, logo, watermark, menu, vignette, screen border, score, minimap, shop, extra widgets, smoke, realistic metallic reflections, rendered glitter or bloom.
```

</details>
