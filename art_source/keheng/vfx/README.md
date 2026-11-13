# 刻衡动态特效预览

- `keheng_mesh_dark.png`、`keheng_mesh_grass.png`：普攻及八个候选技能的实际生产网格，分别在暗底、草地色底上离线投影。
- `keheng_dynamic_preview.gif`：40 帧、每帧 60ms；各技能采用独立阶段时间轴，长领域加速展示，保留节点、连接、生效、脉冲和结束的先后关系。
- `keheng_phase_steps.png`：迟界的节点到薄层阶段，以及随行域自然收针、打断散开的对照。
- `keheng_projectile_model_meshes.png`：水平、竖直向上、斜向下降和 LOD 的实际迟针模型面，使用运行时模型 atlas。
- `keheng_penetration_peak.png`：终刻贯穿的准备、实际出剑峰值及末端纹理放大图，区分窄实体针芯与两侧切片。

帧源为 `KehengVfx.build`，经生产 `AbilityVfxGeometry.build` 调用；共用预览器实际采样生产 PNG、每面的 UV、乘色与 alpha。预览以可复现的示例场景参数驱动实际网格，不是手绘示意或游戏截图。没有使用 GUI 进入游戏验收。

材质包含 256px 的刻度环、开口剪切弧、沿 U 轴的纹理尾流、针形命中闪、沿 V 轴的错位能量带和开孔薄盾，以及 64px 像素碎纹和星针。地印外缘按实际 alpha 支撑范围标定，保留真实技能半径与透明中心。边界、错转／错层纹理和碎光分开组合，维持原警示、生效、脉冲和结束时间。

迟针主体来自 `../projectiles/` 中的独立规格和带内嵌贴图模型，通过真实模型面生成。正常档 78 面、LOD 42 面；实体样本到 1 tick 后不再留下悬停本体，旧段尾光维持原 4 tick。终刻贯穿只沿轴向拉长针芯，横截面保持细窄。

预览入口：`tools/vfx/PreviewKehengVfx.java`，共用投影器为 `tools/vfx/PreviewRoleMeshes.java`。先编译项目，再以项目 `sourceSets.main.runtimeClasspath` 编译这两个入口；运行 `com.myriad.tools.PreviewKehengVfx art_source/keheng/vfx` 可重新输出上述文件。

运行时短刷新包只延续有限寿命，循环动画取连续世界时间；领域实例、警示已完成时间、针体端点、命中和结束均由相应生产事件提供。预览用于检查这些网格的形状和阶段，不替代客户端视角、多人延迟或真实场景可读性验收。
