# 十名角色的第一人称静态持械

2026 年 11 月 27 日。十人各自收近握点、减少露出的前臂，并调整腕部在屏幕上的粗细。焚诀使用直臂握柄，夜影保留拳外可见的反握短刃。朔另拍收鞘、出鞘两种待机。

[最新截图总览（一）](assets/firstperson-rest-20261127/十角色新版-1.jpg) · [最新截图总览（二）](assets/firstperson-rest-20261127/十角色新版-2.jpg) · [夜影暗处](assets/firstperson-rest-20261127/夜影暗处.jpg)

## 同机位比较

用真实 Minecraft 26.3 / Fabric 单人测试客户端拍摄。训练场站位 (0.5, 65, 0.5)，正南水平视线，偏航和俯仰均为 0°，70° FOV、Kai 皮肤、右主手、空副手。日间设为正午晴天，夜影补拍午夜普通待机。生产 JAR 在启动前复制进独立测试目录，脚本逐帧核对位置、视线、皮肤、装备与光照。

“上轮”指本次缩短前臂前已成功拍摄的 08:42:45 批次。表中可见长度沿腕部中线量至屏幕边缘或可见模型末端，腕宽取垂直于中线的横截面。数值来自 1708 × 960 原始图，按像素取整。纵深角从 #68 至当前版，用刃根到刀尖、杆轴或鞘轴的相机空间静态投影计算。

| 角色 | 可见前臂 · 上轮 → 当前（像素） | 当前占上轮 | 当前腕宽（像素） | 纵深角 · #68 → 当前 | 静态实机对照 |
| --- | ---: | ---: | ---: | ---: | --- |
| 夜影 | 190 → 101 | 53.0% | 213 | 12.0° → 2.4° | [上轮／当前](assets/firstperson-rest-20261127/yingxiao-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/yingxiao.jpg) |
| 天刑 | 162 → 86 | 52.9% | 212 | 6.1° → 2.2° | [上轮／当前](assets/firstperson-rest-20261127/tianxing-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/tianxing.jpg) |
| 焚诀 | 106 → 56 | 52.8% | 213 | 11.2° → 1.4° | [上轮／当前](assets/firstperson-rest-20261127/fenjue-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/fenjue.jpg) |
| 墨尘 | 294 → 148 | 50.3% | 206 | 31.3° → 6.9° | [上轮／当前](assets/firstperson-rest-20261127/xuanchen-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/xuanchen.jpg) |
| 素岚 | 314 → 159 | 50.6% | 207 | 43.0° → 10.0° | [上轮／当前](assets/firstperson-rest-20261127/sulan-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/sulan.jpg) |
| 凌渊 | 276 → 137 | 49.5% | 224 | 23.0° → 9.2° | [上轮／当前](assets/firstperson-rest-20261127/lingyuan-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/lingyuan.jpg) |
| 寒泽 | 289 → 154 | 53.4% | 221 | 33.9° → 6.9° | [上轮／当前](assets/firstperson-rest-20261127/hanze-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/hanze.jpg) |
| 青萝 | 248 → 132 | 53.3% | 210 | 35.5° → 10.4° | [上轮／当前](assets/firstperson-rest-20261127/qingluo-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/qingluo.jpg) |
| 赤霄 | 208 → 109 | 52.4% | 209 | 26.1° → 10.1° | [上轮／当前](assets/firstperson-rest-20261127/chixiao-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/chixiao.jpg) |
| 朔·收鞘 | 遮挡 → 58 | 不可直测 | 遮挡 | -0.9° → -0.9° | [上轮／当前](assets/firstperson-rest-20261127/shuo-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/shuo.jpg) |
| 朔·出鞘 | 148 → 77 | 51.8% | 216 | -0.6° → -1.2° | [上轮／当前](assets/firstperson-rest-20261127/shuo-drawn-arm.jpg) · [#68／当前](assets/firstperson-rest-20261127/shuo-drawn.jpg) |

朔收鞘时，上轮的腕部中线被左下 HUD 盖住。相同模型截面中心的投影约由 76 像素降至 38 像素，只能作为间接参照。当前帧的腕宽也受到 HUD 遮挡。朔出鞘使用截短前臂，截口和画面下沿之间仍可见背景，是下一轮构图要处理的地方。

夜影的 1.00×、1.15×、1.25× 均在相同姿态与手臂尺寸下试拍。相对用户指定的 #68 旧图，1.25× 是本组首个让可见刃长增长约四分之一以上的尺寸。焚诀先调刀身纵深和侧斜，再翻出宽面，并用天刑同款的直臂皮肤网格消除折腕。[夜影倍率试调](assets/firstperson-rest-20261127/夜影倍率.jpg) · [焚诀试调](assets/firstperson-rest-20261127/焚诀试调.jpg)

## 复拍

在仓库根目录运行。默认拍摄十人日间待机、朔出鞘和夜影暗处，共 12 张原图。每次生成独立目录，保存逐帧状态、#68 对照和总览。传入已完成批次作为第二个参照时，也会生成上一轮与当前的左右对照。[脚本及环境要求](../../tools/mcpfabric/README.md#第一人称固定机位截图)

```sh
python tools/mcpfabric/capture-firstperson.py
python tools/mcpfabric/capture-firstperson.py --roles fenjue,shuo --reference-run outputs/firstperson/20261127-085829-981806
```

本轮本地原图目录 `outputs/firstperson/20261127-085829-981806/`。实机采样 JAR 的 SHA-256 为 `256f0cde150069c43eb4352d6305abb763698527cbb25a97112f75188e8b4126`，已通过生产构建和命名空间检查。当前交付对照覆盖默认右主手、空副手的静态构图。最终缩臂版的左主手、高 FOV 与副手持物仍待复拍。
