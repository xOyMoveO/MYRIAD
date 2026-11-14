# 伊珂光子飞行物：真实模型源

三种飞行物沿用虹列的白陶瓷、石墨接件、钴蓝电容和冰蓝嵌入光路。普攻是紧凑光子弹；Q及架炮共用三翼光子炮弹；远界重轰使用更宽的分段装甲重弹。模型没有照搬示例图的武器或布局。

每种均保存完整及LOD的 `*.spec.json`、可编辑 `*.bbmodel`，共用内嵌256×256贴图。完整模型为 basic 10块/60面、shell 25块/150面、heavy 31块/186面；LOD分别为5块/30面、11块/66面、12块/72面。每面都绑定实际材质像素，包含独立装甲、光路、模块、接件和稳定翼。

- 局部前向为 **+Y**，前端坐标 `tip_y=64`。运行时按真实相邻位置方向建立三维正交基，俯冲时前端朝下，不是面向相机的平面。
- 64源单位的比例尺分别映射为0.22、0.62、1.20格，保留普攻、Q、重弹的尺寸级差；不改变碰撞半径、射程或伤害。
- 生成入口：`python tools/assets/generate_yike_projectiles.py`。它读取规格，调用本地Blockbench skill生成器内嵌贴图，再用 `tools/vfx/compile_roster_projectiles.py` 编译真实六面UV。依赖Pillow及项目本地模型skill。
- 运行时网格：`src/main/resources/assets/myriad/vfx/yike_projectiles_mesh.json`；贴图：`src/main/resources/assets/myriad/textures/particle/vfx/yike_projectiles_atlas.png`。编译网格保留源文件SHA256和轻微灰度逐面明暗，不改变材质色相。
- `projectile_models_preview.png` 和三个 `*_model_preview.png` 为真实源模型预览，图中统一源单位比例；游戏中的实际尺寸级差、三维朝向、HD饰层和像素尾光见上级 `vfx/yike_projectiles_actual_uv*.png/.gif`。

飞行模型只用于一tick的已确认运动样本；`age>=1`即不再生成本体。碰撞、结束与取消沿用已有实例清理，独立尾流按原有有限TTL退尽。纹理饰层和像素光点独立于实体模型，不能用尾光补出膛或补伤害。以上均为生产姿态/网格的离线预览，未启动游戏或进行客户端实机验收。
