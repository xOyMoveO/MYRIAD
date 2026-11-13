# #12 虹轨·伊珂：实施与验证记录

2026-11-13，对应父规格 #2 与归并工单 #12。独立分支 `feat/roster13-yike12`，工作目录 `/private/tmp/myriad-delivery/yike12`。本记录首先保存服务端与资源阶段结果；客户端验收未完成，本票尚未完成，唯一综合审查仍由主流程在最终整合后执行（当前 0/1）。

## 当前实现

八个候选各有独立技能类；普通Q在出膛、主要技能在展开成功、W在成功布下地面预警、终极在开始蓄势时提交费用。普通姿态左键为免费A物理弹；两种姿态仅手动Q发炮，保留原Q等待、逐发费用、独立22／27 tick出膛间隔、有限炮口追随、W取消待发炮与退出Q冷却。终极包含真实抛物线重弹和有限转向六跳束流。虹标按实际付费施法去重，稳态按水平累计路程判断。

模型、状态同步、HUD、图标、方向几何、原创音频与清理接线已写入源码。虹列主架、三瓣和前移聚焦环为独立可编辑部件；概念图仅为造型参考。正式选角保持十一人，开发新世界先启用玩法与训练，再显式 `/myriad char yike`。最终十三人正式开放和跨角色缓时兼容属于 #28。

公共运行时增加实际技能消费的起手移动、冷却就绪／提交冷却、准备中断与主动取消钩子；`SkillSystem` 在新版技能分派后才阻挡旧技能的起手输入，允许W和再次主要／终极输入到达对应取消逻辑。没有恢复旧全局动作系统。

## 已实际执行

JDK25：`/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`。Gradle任务使用 `/private/tmp/myriad-implementation/gradle_serial.py` 与共享锁串行执行。原始证据目录 `/private/tmp/myriad-delivery/evidence-yike12/` 可能随临时目录清理。

| 执行 | 真实结果 |
|---|---|
| `runYikeTestServer`，`yike-test-6.log` | 退出0，`MYRIAD-YIKE PASS: 52 real-input cases`，GameTest 1/1通过。涵盖Q取消／低蓝／重复提交、W取消、五炮／四炮精确22／27 tick、提交点与退出冷却、禁锢／眩晕／沉默／强制位移、六跳取消、重轰撞墙、A65左键与右键无弹、有限转向、稳态累计路程、全护盾有效虹标、死亡／换角／跨维度／重置／关闭玩法清理。S230组合真实结算2070.5/1.5、总蓝耗405。 |
| 补充四个缺失验收点后 `runYikeTestServer`，`yike-test-7.log` | 退出0，56个真实输入场景、GameTest 1/1通过。新增实际束流逐tick最多2.25°／20tick精确45°、W使用区域中心视线而非施法者后续视线、楼板遮挡、球外高度不结算。仅扩充测试，生产代码未变化。 |
| `build runFirstGroupTestServer`，`build-aggregate-3.log` | 退出0，`BUILD SUCCESSFUL in 10s`；GameTest 1/1通过，既有十一人聚合和26个公共施法契约场景完成。 |
| `python tools/check_namespace.py --jar build/libs/myriad-0.1.0.jar` | 退出0；命名空间、包、JSON、发行JAR通过。 |
| `python tools/check_retired_roster.py --jar build/libs/myriad-0.1.0.jar` | 退出0；1259个源／工具／资产文件、1382个JAR条目通过；源树与JAR各261条自有资源引用均有目标。 |
| 可编辑模型与发行模型几何比较、音频解码检查 | 四个Blockbench源与模型JSON元素的from/to逐一一致（57／22／9／8个元素）；八图标、17个单声道44100Hz Ogg Vorbis文件均存在且可解码。 |
| `git diff --check` | 退出0。 |

## 已观察的失败及修复

- `yike-test-1`至`yike-test-4`保留前任开发中的编译／夹具排查证据；最终52场景通过记录为`yike-test-6.log`，不把前期失败写成通过。
- `build-aggregate-1.log`首错是`ActionRemovalChecks`精确客户端混入列表与远程攻击集合尚未包含本票的`YikeArmPoseMixin`／`yike`；失败恢复全局测试状态后产生的单tick起手次错不代表另一个已确认生产故障。检查按新的本地武器姿态与远程攻击契约最小更新后，完整聚合通过。
- `build-aggregate-2.log`因沙箱不能写本机Gradle锁而未启动构建；按权限流程后实际成功记录为`build-aggregate-3.log`。

## 阶段构建包

`build/libs/myriad-0.1.0.jar`，25,726,438字节；SHA256 `fc54166d29d9eb4f3215a0c28d2bf536cb30711ef868d2badd91323fd4d9ff67`。尚未安装正式实例，未加载正式世界、未推送、未创建PR或合并main。

## 待完成验收

客户端新世界的第一／第三人称携行与展开、炮口认可方向、HUD、逐技能VFX及声音观感仍未执行；多人远端客户端观察也未执行。服务端GameTest和静态资源检查不能替代这些结果。等待共享任务窗口释放后继续实际游戏验收，必要的视觉修复再单独提交。
