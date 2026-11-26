# 万象负面状态图标

[原色与灰度联系表](contact-sheet.png) 包含放大图及 32、16、12、10 像素原尺寸预览。独立纹理位于 [GUI sprite 目录](../../src/main/resources/assets/myriad/textures/gui/sprites/control_status)。每张为 32 × 32 像素的透明 sRGB PNG，计时环由界面单独绘制。

[实机背景缩放样张](scene-samples.png) 在天空、深色墙面和多人交战画面的原帧取样上离线排版，便于对照 12 与 10 像素、原色与灰度。

| 状态 | 文件 | 本版用途 |
| --- | --- | --- |
| 眩晕 / Stunned | stun.png | 英雄头顶 |
| 沉默 / Silenced | silence.png | 英雄头顶 |
| 禁锢 / Rooted | root.png | 英雄头顶，普通与铆钉禁锢共用 |
| 普通移速减速 | move_slow.png | 可复用 |
| 缓时 | action_slow.png | 可复用 |
| 击飞／浮空 | airborne.png | 可复用，绑定时区分瞬时事件和持续浮空 |
| 击退 | knockback.png | 可复用，瞬时位移 |
| 敌方牵引 | pull.png | 可复用，用于敌对牵引 |
| 标记 | marked.png | 可复用，须核对技能的可见与失效规则 |
| 灼烧 | burn.png | 可复用，用于附着目标的效果 |
| 致盲 | blind.png | 素材储备 |
| 黑暗 | darkness.png | 素材储备 |
| 中毒 | poison.png | 素材储备 |
| 流血 | bleed.png | 素材储备 |
| 易伤 | vulnerable.png | 素材储备 |
| 减疗 | heal_reduction.png | 素材储备 |
