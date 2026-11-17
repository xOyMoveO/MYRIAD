# #40 Fabric 26.3 客观验证记录（2026-11-16）

本记录只授予客观实现/执行结论。全部视觉、听感、手感、流畅性和平衡仍待人验收；原地图及原地图整局按用户补充交由人验收。本轮仅运行新建隔离测试世界，没有索取、加载或改动原地图/正式实例。

## 被测边界与工具

功能基线：`8da79ddc329492155cea44f3166c97f0e37d86fc`。目标组合：Minecraft26.3正式、Loader0.20.1、Fabric API0.166.2+26.3、Loom1.18.4、Wrapper9.7.0、JDK25.0.3；macOS arm64。版本和原双JAR已核对官方元数据及摘要，复用本机缓存。共享索引34全部5149项size/SHA1通过；只补141对象7,598,449字节，索引32及既有对象保留。

生产源码全部编译，没有将其余角色/地图源码exclude以构造试点成功。既有81份检查/回归源移至独立`src/gametest`，所有原断言保留；客户端专属管线/模型断言移至物理客户端执行，未降低required Mixin要求或删合法性断言。发行包排除这些测试源、fixture、适配工具、凭据与世界。

## 已执行结果

| 检查 | 实际结果与边界 |
|---|---|
| `bash ./gradlew build` | 主源码、全部保留GameTest源码、发行构建成功；不等于全部专项自动运行 |
| `myriad:melee_arc` | 独立Fabric GameTest 1/1通过 |
| `myriad:first_group` | 保留的服务端聚合1/1通过，含公共合约、技能图谱/资源、原角色/朔执行断言；客户端断言另测 |
| `myriad:shuo_pilot` | 1/1通过：真实生产packet codecs→已注册C2S/S2C接收器，学习/点数与分支拒绝，资源/CD、失败请求、10行动tick起手、碰撞步进、60/40伤害、原音频/表现事件；实体save→临时磁盘NBT→load及死亡COPY_FROM回调。其玩家/传输是GameTest fixture，不冒充键鼠或真实客户端 |
| 物理客户端客观检查 | 4组2339断言通过：管线4、朔手臂模型388、收鞘握持1939、持握变换8。原数值/矩阵/管线约束，[原始JSON](evidence/client-checks-20261116.json)；不代表图形观感通过 |
| 独立服务器 | 在`run-fabric-server/fabric-pilot-new`新世界、回环25576启动；实际服务器进程独立于两个客户端 |
| 两个真实客户端 | MCPFabric0.10.0+26.3与独立源码适配仅安装隔离目录，两端实际进入同服新世界，协议18configuration握手及生产同步实际工作 |
| 正式选角/学习 | SDL原生鼠标选朔及确认，C键打开修行界面，点击一闪与学习按钮；服务端同步`shuo_yishan=1`、技能点1→0。未直接改技能等级或发送伪操作结果 |
| 原生E释放 | 基础智慧30、rank1、对方双防0；法力700→620、CD基线220。首段71.4+延迟段47.6=119=`95+.8*30`，原charge/draw/impact声音提交STARTED，两端表现事件具有同一服务端started时点 |
| 真实位移 | 修复后主推进6.96417格，满足固定源码`7-.04`结束阈值；后续保留原`finish`速度×.12和原版摩擦，最终7.36810格。没有改成瞬移或硬停7，也不把尾段惯性藏进误差 |
| 原生失败请求 | CD中再次E没有重复80消耗或再次charge/draw。训练模式按基线允许未学习常规技能，原生Q实际按规则耗40，未将其误记为拒绝 |
| 新JVM保存/重连 | 正常`save-all`/`stop`后重新启动，两个真实客户端重连只读确认：朔、lv1、一闪rank1、点0、法力与enabled/training读回；entity ID换新、heldInputs0，未继承旧连接状态 |

| 真实墙阻挡 | 原生E、同一起点，墙x3..8/y80..83/z8；最大/最终中心z6.666697，墙前净空边界7.7，墙后z10.5观察者生命始终700；正常扣80/CD220，没有隔墙命中或穿墙 |
| 关闭玩法/清理 | 正式`/myriad mod off`后，两端20HP、角色/技能清空、50mana、CD0；原生E不施放/不移动/不扣费，双方heldInputs0。最终正常保存此off状态 |

两客户端完整步骤、初次失败/修复后复测及声音/模型既有warning边界见[客户端客观记录](../verification/fabric26-client40.md)。真实客户端未覆盖受控取消和真正死亡实体克隆；这些在GameTest生产入口验证，不能冒充真实网络死亡/复活。角色资源中已有8项模型particle引用warning及打包来源MD由#44继续核对，未为了本试点改写素材。

## 最终发行摘要

- 产物：`build/libs/myriad-0.1.0.jar`，26,508,838字节。
- SHA256：`1563f0551bb8acbcc46fb0b6ff9b874b49e6d52ae9f66af7d9fe3840ba2a3c82`。
- 构建、ZIP CRC、Fabric身份/26.3依赖、命名空间与发行排除审计均实际通过，包含修复后的`ServerConnectionTickMixin`。主JAR不含GameTest检查/实例、测试adapter或世界；原614资产、6角色定义文件与8许可文件相对固定基线逐字未改。
- 最终公共回调状态重跑：[first_group XML](evidence/first-group-20261116.xml)与[shuo_pilot XML](evidence/shuo-pilot-20261116.xml)，分别1/1通过；`melee_arc`先前最小实际运行1/1通过，其后未更改该算法。
- 正式实例未安装；只创建隔离测试世界和平台/墙fixture。服务器最后正常保存off状态并`stop`退出0；三个本轮专属客户端已释放输入并关闭，桥接25599/25600不再监听。两个长驻client Gradle任务因主动SIGTERM返回143，该退出不能当运行崩溃或检查失败。


## 修复与保留的首因记录

- 26.3把原受击冷却分离为`LivingEntity.damageCooldownTime`，`Entity.setInvulnerableTime`是绝对临时免疫。最初误接后16连击断言真实失败；改回正确冷却字段后原断言通过，没有绕过伤害、护盾或合法性链。
- 原生移动流程`connection.tickPlayer`先`doTick`模拟，再`absSnapTo(firstGood)`还原已接纳位置。把施法Post回调放在`Player.tick`内部会累计随后被回滚的预测位置，一闪在实机6.162格提前结束。现于原生还原调用之后执行Post，客户端原控制器与扫掠规则保留，真实双端复测如上。
- Fabric客户端完整注册`/myriad`根会抢占服务器`mod/train`子命令。实际输入暴露后改用官方发送前事件仅消费完整GUI别名，其余正常发送服务端；原生开关/选择操作复测成功。
- 26.3使用SDL，正式鼠标按钮/按键常量已改接实际原生接口。旧客户端spawn Redirect目标已不存在，原版已直接使用`EntitySpawnRequest(LOAD,true)`提供相同保障，删除冗余失效注入，未降低其余required注入。
- 持久/同步attachment在初始化期注册，避免新JVM解码实体数据时类型尚未创建；新checkpoint/HUD文件使用独立Fabric名字，旧玩家进度、旧个人HUD与旧进行中对局不导入。原地图配置文件保留原身份。
- 原first_group中的client-only断言在Fabric dedicated禁止加载客户端类，现移至独立物理客户端测试入口。FlameSlash fixture原相对高度315加偏移越过26.3测试锚点世界上界，整体平移至240，保持墙、相对布局、动作及伤害断言。

原日志在本轮`/private/tmp/myriad-*`，可复现入口在[开发说明](README.md)。隔离专项保留为可本地复现的入口；当前GitHub CI仅执行`build`，本地专项结果不记为远端CI已通过。其余角色逐候选原生输入、地图模式专属技术专项属于后续票；这份试点记录不能推出14/112全量验收完成。
