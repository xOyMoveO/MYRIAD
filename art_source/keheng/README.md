# 刻衡资源与预览

- `hengke.bbmodel`：可编辑模型，贴图已内嵌；移动文件后仍可在 Blockbench 中直接显示颜色。
- `hengke.spec.json`：模型和材质的可编辑规格，基于原模型逆向，保留细刃与握区尺寸，补齐剑根、连续开口护手和微型擒纵轮连接。
- `hengke_texture.png`：64×64 像素材质图；银白细刃、墨青脊线、深青皮革、琥珀金属和青绿时晶分别绑定独立 UV 区域。
- `hengke_front.png`、`hengke_side.png`、`hengke_top.png`、`hengke_isometric.png`：从真实模型、旋转和 UV 离线渲染的正、侧、顶及斜角图；`hengke_orthographic.png` 为三视图合览，顶视图单独放大。
- `icon_source.png`：专属技能图标原图；`icon_export.py` 保留完整生成提示和裁切坐标；`icon_contact_sheet_native.png` 与 `icon_contact_sheet.png` 分别供原尺寸和放大检查。运行图标位于 `src/main/resources/assets/myriad/textures/gui/sprites/skill_icons/generated/keheng/`。
- `vfx/`：普攻及八个候选技能的生产网格动态 PNG/GIF，阶段图及再生成入口见 `vfx/README.md`。

重新生成（需要 Pillow 及项目的 `minecraft-ai-generate-bbmodel` skill）：

```sh
python tools/assets/generate_keheng_weapon.py
```

生成入口调用项目 skill 的规格校验、确定生成与模型校验，保留六位小数，覆盖默认 0.5 像素对齐以免吞掉原有 0.04 像素细线。每个部件的 UUID 固定且唯一。规格及 Blockbench 的 UV 使用 64 像素坐标，运行时 JSON 的 UV 按 `16/64` 换算；贴图同步输出到 `src/main/resources/assets/myriad/textures/item/hengke.png`，模型同步输出到 `src/main/resources/assets/myriad/models/item/hengke.json`。

剑尖沿模型本地 `+Y`，剑身窄宽沿 `X`，厚度沿 `Z`；既有第一／第三人称及界面 `display` 变换保持原值。离线图用于检查资源本身，不代表游戏内视角和握持验收。
