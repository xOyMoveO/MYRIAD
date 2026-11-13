# 刻衡动态特效预览

- `keheng_mesh_dark.png`、`keheng_mesh_grass.png`：普攻及八个候选技能的实际生产网格，分别在暗底、草地色底上离线投影。
- `keheng_dynamic_preview.gif`：40 帧、每帧 60ms；各技能采用独立阶段时间轴，长领域加速展示，保留节点、连接、生效、脉冲和结束的先后关系。
- `keheng_phase_steps.png`：迟界的节点到薄层阶段，以及随行域自然收针、打断散开的对照。

帧源为 `KehengVfx.build`，经生产 `AbilityVfxGeometry.build` 调用；预览以可复现的示例场景参数驱动这些实际网格，不是手绘示意或游戏截图。没有使用 GUI 进入游戏验收。

预览入口：`tools/vfx/PreviewKehengVfx.java`，共用投影器为 `tools/vfx/PreviewRoleMeshes.java`。先编译项目，再以项目 `sourceSets.main.runtimeClasspath` 编译这两个入口；运行 `com.myriad.tools.PreviewKehengVfx art_source/keheng/vfx` 可重新输出上述文件。

运行时短刷新包只延续有限寿命，循环动画取连续世界时间；领域实例、警示已完成时间、针体端点、命中和结束均由相应生产事件提供。预览用于检查这些网格的形状和阶段，不替代客户端视角、多人延迟或真实场景可读性验收。
