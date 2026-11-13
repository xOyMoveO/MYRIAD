# 刻衡：迟针实体模型

`delay_needle.spec.json` 与 `delay_needle_lod.spec.json` 分别生成正常档、远处简化档；对应 `.bbmodel` 内嵌同一张 `keheng_projectiles_atlas.png`。模型采用银白细长实体刃、墨青脊线、青绿嵌件和琥珀微刻度，前端沿本地 `+Y`，尖端为 `Y=64`。

```sh
python tools/assets/generate_keheng_projectiles.py
```

入口调用项目 `minecraft-ai-generate-bbmodel` skill 的确定生成器和校验，保留嵌件所需的小数厚度；再由 `tools/vfx/compile_roster_projectiles.py` 读取真实 `.bbmodel` 六面坐标、旋转及 UV，输出运行时 `assets/myriad/vfx/keheng_projectiles_mesh.json` 和 `textures/particle/vfx/keheng_projectiles_atlas.png`。

正常档为 13 个部件、78 个真实单面；LOD 为 7 个部件、42 个真实单面。`delay_needle_normal_lod.png` 是上述源模型的带贴图预览。运行时前端绑定相邻位置采样的插值点，支持水平、竖直与斜向；旧采样到第 1 tick 后不再留下实体，碰撞／结束信号移除实体后仅保留独立有限尾光。终刻贯穿可沿轴向拉伸针芯，横截面独立保持细窄。
