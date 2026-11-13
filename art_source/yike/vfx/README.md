# 伊珂·虹列：光子阵列特效

主色为冰蓝 `#5DDEFF` 与冷白 `#F1FCFF`，结构暗面使用石墨 `#15222E`、深钴蓝 `#18456B`。紫 `#A88AFF` 只用于少量数据碎片，紫红 `#FF6AAE` 用于末发提示。

- `yike_vfx_meshes_dark.png`、`yike_vfx_meshes_grass.png`：普攻、八候选及准备／出膛／结束阶段的生产网格总览。
- `yike_vfx_stages.png`：光子冲击、重轰切面展开与光束收束的逐阶段图。
- `yike_vfx_size_hierarchy.png`：同视角、同缩放和同采样线段下的普攻、Q、重轰体量对比；总览中的各格另有各自的放大比例。
- `yike_vfx_preview.gif`：84 帧、每帧 50ms，固定视角和比例。
- `yike_vfx_frame_*.png`：GIF 关键帧，方便单独检查。

轮廓采用短鼻棱柱光子弹、固定三翼准直片、直角测距标记、分段电子环及透明钴蓝盾板。真实相邻端点尾流、已截断光束长度、原阶段时序和实例生命周期由原生产链继续提供。

预览入口为 `tools/vfx/PreviewYikeVfx.java`，调用生产 `YikeVfxGeometry.build`，通过 `PreviewRoleMeshes` 离线投影。它使用可复现的示例参数展示实际网格，不是游戏截图。以项目 `sourceSets.main.runtimeClasspath` 编译两个预览入口，再运行 `com.myriad.tools.PreviewYikeVfx art_source/yike/vfx` 即可重新生成。
