# 铆钉第一人称持锤修正验证

日期：2026-11-15。基线：已合并的 `485ddca`。本轮没有启动图形游戏、控制客户端或安装正式实例。用户截图仅作本地诊断，未发布；以下均为实际模型、皮肤网格、生产矩阵和动作曲线的离线投影，背景是示意场景。

## 最终效果与范围

[实装基线／最终版对照](rivet-grip/firstperson-before-after.png)依次展示放松、Q起手和Q挥击；另有较大的[放松](rivet-grip/firstperson-relaxed.png)、[起手](rivet-grip/firstperson-windup.png)、[挥击](rivet-grip/firstperson-impact.png)单格及[六阶段图](rivet-grip/firstperson-motion.png)。

最终采用低握、锤头向纵深展开的单主手姿势，拳宽方向与锤柄对齐；动作中腕角限制35°。基线手部为 `ShuoArmModel` 的裁切前臂，最终使用 `RivetArmModel`：拳头保持原样，近肩段从8像素延续到10像素，UV连续。放松、起手抬锤、加速下落和缓收有不同阶段，不修改攻击、技能起手时长、伤害、费用、冷却或位移。

第三人称沿用PR #33基线。物品JSON、模型缓存、可编辑锤模型及相关生成器／作者文档均与基线逐字节一致；客户端注册入口也未改变，没有新增第三人称渲染钩子。物品JSON SHA256为 `263142e9c5d67511e8f5d8cca078c99ca2ede4d3807011ffaae4b143c7171d7b`。

## 实际验证

| 检查 | 结果 |
|---|---|
| 发行构建任务 | 完成；最终JAR与899个主源码编译类（含31个铆钉类）逐字节一致 |
| 铆钉专项 | 原断言复跑1/1；71真实输入场景＋2名正式选角／准备玩家；新增2364握持／动作矩阵样本通过 |
| 新版聚合 | 原断言复跑1/1；14角色／112候选、已有玩法及通用表现检查通过 |
| 独立第一人称投影 | 6阶段×左右手×宽／细臂×HUD60／70，共48组；掌心握柄误差最大约2.46×10⁻⁷格；靠肩外端盖可见像素全部为0，前臂均连续触及画面边缘 |
| JAR与资源 | 17个铆钉资源与源码一致，CRC通过；命名空间、1806源文件／1532 JAR文件、294／294条自有资源引用审计通过 |
| 最终源码一致性 | 验证前后1193个运行源码／资源文件SHA256不变 |

[关键输出摘录](rivet-grip/headless-validation.txt)、[铆钉XML](rivet-grip/rivet-gametest.xml)、[聚合XML](rivet-grip/first-group-gametest.xml)、[包审计](rivet-grip/package-audit.json)、[投影审计](rivet-grip/firstperson-projection-audit.json)、[命名空间](rivet-grip/namespace-audit.txt)与[引用审计](rivet-grip/retired-audit.txt)随仓库提供。

首轮组合命令在发行构建完成后，因旧夹具 `RivetChecks.dummy:289` 新造假人查询失败而退出1；新握持检查已通过。未改源码、未放宽断言，原样复跑铆钉与聚合后退出0，34秒成功。中间稿另一次聚合曾在未改的G2全窗口伤害断言失败（实际HP365）；最终相同断言通过。失败与复跑完整日志、XML均保留在本地证据目录，不把复跑通过表述为从未失败。

投影使用真实 `Camera.calculateHudFov` 约定：常态手部FOV为70，世界FOV40／70／110不会直接改变手部投影；三格去标题后像素摘要相同，另测HUD60。[左右手与臂型](rivet-grip/firstperson-handedness.png)、[FOV图](rivet-grip/firstperson-fov.png)均为离线图。工具为 `tools/firstperson/RivetGripPreview.java`，通过最终生产类调用实际 `RivetWeaponRig`／`RivetArmModel`，并在正式导图时强制检查袖端连续性。CPU投影不代替实际客户端光照、GPU剔除、视角晃动、真实HUD、手感或帧率验收。

复现构建／专项先运行共享资源预检，再用本机JDK25和完整隔离缓存执行：

```sh
python docs/verification/rivet33/verify_shared_assets.py
JAVA_HOME=<JDK25> GRADLE_USER_HOME=<隔离缓存> bash ./gradlew \
  --offline --no-daemon --no-parallel --max-workers=1 --console=plain \
  --no-configuration-cache -I docs/verification/rivet33/headless.init.gradle \
  build runRivetTestServer runFirstGroupTestServer -x downloadAssets
```

共享库 `~/.local/share/minecraft/assets` 的32号索引及5059个对象已逐一核对大小与SHA1，0缺失／损坏；原始26.2客户端JAR也与发布清单大小／SHA1一致。上述跳过下载仅在预检通过后使用；没有补下载游戏资源。

## 交付

`myriad-0.1.0.jar`：27072514字节。

SHA256：`882b354f2525112307cfbe3608c54bccc21cf481cc0a7e107dfe223fde686f2d`。

已复制到原项目被Git忽略的 `outputs/rivet-grip-20261115/myriad-0.1.0.jar`，与最终 `build/libs` 摘要一致；未覆盖旧体验包、未安装。完整本地证据位于 `/private/tmp/myriad-rivet-grip-evidence/`。实机视角和手感留待人工体验。
