# Fabric 26.3 保留边界与公共接入

本票属于 #39 的 #40 平台/一闪试点。唯一功能基线为 `8da79ddc329492155cea44f3166c97f0e37d86fc`，不是历史策划或其他分支产物。保留 `myriad`、`com.myriad`、现有注册/资源键和 `/myriad`。仅维护 Fabric / Minecraft 26.3 正式版。

## 角色与候选归属

六个数据定义文件逐字保留，共14角色、112候选。表中实现目录相对 `src/main/java/com/myriad/`，定义文件位于 `src/main/resources/data/myriad/role_rework/`。批次登记仍由 `FirstGroupSkills` 解析，具体技能仍归角色，未新增演示技能。#40试点为 `shuo_yishan` 的两种拔/收鞘行为；保留完整源码和资源不等于其余111候选已逐项验收。

| 角色ID | 定义文件 | 技能实现归属 | 八候选ID |
|---|---|---|---|
| `yingxiao` | `first_group.json` | `character/skills/yingxiao/` | `g1_yingxiao_pa`, `g1_yingxiao_pb`, `g1_yingxiao_n1`, `g1_yingxiao_n2`, `g1_yingxiao_ma`, `g1_yingxiao_mb`, `g1_yingxiao_ua`, `g1_yingxiao_ub` |
| `tianxing` | `first_group.json` | `character/skills/tianxing/` | `g1_tianxing_pa`, `g1_tianxing_pb`, `g1_tianxing_n1`, `g1_tianxing_n2`, `g1_tianxing_ma`, `g1_tianxing_mb`, `g1_tianxing_ua`, `g1_tianxing_ub` |
| `fenjue` | `first_group.json` | `character/skills/fenjue/` | `g1_fenjue_pa`, `g1_fenjue_pb`, `g1_fenjue_n1`, `g1_fenjue_n2`, `g1_fenjue_ma`, `g1_fenjue_mb`, `g1_fenjue_ua`, `g1_fenjue_ub` |
| `xuanchen` | `first_group.json` | `character/skills/xuanchen/` | `g1_xuanchen_pa`, `g1_xuanchen_pb`, `g1_xuanchen_n1`, `g1_xuanchen_n2`, `g1_xuanchen_ma`, `g1_xuanchen_mb`, `g1_xuanchen_ua`, `g1_xuanchen_ub` |
| `sulan` | `first_group.json` | `character/skills/sulan/` | `g1_sulan_pa`, `g1_sulan_pb`, `g1_sulan_n1`, `g1_sulan_n2`, `g1_sulan_ma`, `g1_sulan_mb`, `g1_sulan_ua`, `g1_sulan_ub` |
| `lingyuan` | `second_group.json` | `character/skills/lingyuan/` | `g2_lingyuan_pa`, `g2_lingyuan_pb`, `g2_lingyuan_n1`, `g2_lingyuan_n2`, `g2_lingyuan_ma`, `g2_lingyuan_mb`, `g2_lingyuan_ua`, `g2_lingyuan_ub` |
| `hanze` | `second_group.json` | `character/skills/hanze/` | `g2_hanze_pa`, `g2_hanze_pb`, `g2_hanze_n1`, `g2_hanze_n2`, `g2_hanze_ma`, `g2_hanze_mb`, `g2_hanze_ua`, `g2_hanze_ub` |
| `qingluo` | `second_group.json` | `character/skills/qingluo/` | `g2_qingluo_pa`, `g2_qingluo_pb`, `g2_qingluo_n1`, `g2_qingluo_n2`, `g2_qingluo_ma`, `g2_qingluo_mb`, `g2_qingluo_ua`, `g2_qingluo_ub` |
| `chixiao` | `second_group.json` | `character/skills/chixiao/` | `g2_chixiao_pa`, `g2_chixiao_pb`, `g2_chixiao_n1`, `g2_chixiao_n2`, `g2_chixiao_ma`, `g2_chixiao_mb`, `g2_chixiao_ua`, `g2_chixiao_ub` |
| `yunsheng` | `second_group.json` | `character/skills/yunsheng/` | `g2_yunsheng_pa`, `g2_yunsheng_pb`, `g2_yunsheng_n1`, `g2_yunsheng_n2`, `g2_yunsheng_ma`, `g2_yunsheng_mb`, `g2_yunsheng_ua`, `g2_yunsheng_ub` |
| `shuo` | `shuo.json` | `character/skills/shuo/` | `shuo_cangfeng`, `shuo_jianqie`, `shuo_nadao`, `shuo_qiaofan`, `shuo_yishan`, `shuo_duanliu`, `shuo_wusheng`, `shuo_canyue` |
| `keheng` | `keheng.json` | `character/skills/keheng/` | `keheng_kehen`, `keheng_hengbu`, `keheng_chizhen`, `keheng_cuopaibu`, `keheng_chijie`, `keheng_suixing`, `keheng_yimiao`, `keheng_zhongke` |
| `yike` | `yike.json` | `character/skills/yike/` | `yike_rainbow_mark`, `yike_steady_barrier`, `yike_rainbow_shell`, `yike_refraction_denial`, `yike_fixed_artillery`, `yike_mobile_array`, `yike_distant_bombardment`, `yike_prismatic_beam` |
| `rivet` | `rivet.json` | `character/skills/rivet/` | `rivet_station_armor`, `rivet_breach_rhythm`, `rivet_impact_hammer`, `rivet_tactical_anchor`, `rivet_emergency_strut`, `rivet_tow_cable`, `rivet_hold_protocol`, `rivet_breach_charge` |

## 资源与其他功能

固定基线资源目录的 Git tree SHA 与数量如下。迁移复用这些目录，未生成替代素材；可用 `git diff 8da79ddc -- src/main/resources/assets/myriad src/main/resources/data/myriad/role_rework src/main/resources/META-INF/licenses` 核对。

| 保留目录 | 文件数 | 基线 Git tree SHA |
|---|---:|---|
| `src/main/resources/assets/myriad` | 614 | `500e91867e2cd16e6ad9ec7c3b0f8b545ebdcf6d` |
| `src/main/resources/data/myriad/role_rework` | 6 | `75ee4869a0834f87b100f5ff3d5baf21355c6cf8` |
| `src/main/resources/META-INF/licenses` | 8 | `52e9bdc8c31b23338c7b95e4bae716d9236fdf99` |

- 正式选角、学习、分支、成长、普攻、伤害/护盾/治疗/控制/资源：保留原实现，替换平台调用；试点实际覆盖以验证报告为准。
- 5v5/3v3点位、模式路由、地图设施、野怪、兵线、对局恢复：原代码保留，地图与对局批次后续接入/测试世界专项负责；按用户2026-11-16补充，实际地图载入和原地图整局由人验收，技术侧不索取或加载原地图。
- HUD、界面、模型、第一/第三人称动作、相机、粒子与声音：保留原资源和表现入口并改接Fabric；全部视觉、听感、手感与流畅性仍由人验收。
- 既有81份检查/回归源及地图fixture入口移入 `src/gametest/` 独立测试mod；发行主JAR不含测试mod、测试实例或世界。迁移断言保留，适配平台fixture不代表断言通过。
- 不导入旧玩家、个人HUD和旧对局状态；Fabric新玩家状态由新attachment键持久化。地图配置仍走原文件/世界作用域入口；原地图的实际导入、检查和整局由人完成，本轮不读取原地图。

## 后续批次复用的公共入口

| 契约 | 生产入口 | 平台接合与边界 |
|---|---|---|
| 玩家状态/保存/克隆 | `PlayerDataProvider.get/set`、`PlayerData.CODEC` | Fabric persistent attachment `myriad:player_data`，`ServerPlayerEvents.COPY_FROM/JOIN/LEAVE`；临时NBT使用`EntityState.data`，不读取NeoForge旧attachment |
| 施法/提交/选点 | `SkillSystem`、`AbilityRuntime`、`AbilityTargeting` | 三参数即时提交及四参数起手提交语义保留；服务端再验分支、资源、落点；未支付失败请求不扣费 |
| 局部行动/真实时间 | `ActionTime`、`Cast.windup/action*` 与 `later/repeat/whileActive` | 前者局部进度、多来源取最强；后者真实时间；服务器tick与原生Player.tick前置、connection.tickPlayer接纳位置还原后置显式接合 |
| 伤害/护盾/有效命中 | `DamageCalc`、`DamageContext`、`ModShieldSystem`、`AbilityRuntime.hit` | 原版accepted `LivingEntity/Player.actuallyHurt` 事务边界，保留三类伤害和生命实际差值；26.3 `damageCooldownTime`为受击冷却，不能误用绝对免疫`Entity.setInvulnerableTime` |
| 位移 | `Cast.actionDash/actionLeap`、`DashController` | 原扫掠碰撞和逐步motion，`syncVelocity`与正式payload；不改瞬移演示 |
| 网络 | `NetworkSetup.register/registerClient`、`Packets.sendToPlayer/sendToServer` | Fabric生产编解码/全局receiver；62条play消息（39 S2C/23 C2S）及协议18 configuration握手；双端一起发布 |
| 注册/原版属性 | `Myriad.onInitialize`、`registry/Mod*` | 原注册键直接同步注册到原版Registry；FabricDefaultAttributeRegistry精确挂玩家及原有NPC双防 |
| 表现/生命周期 | 原角色表现入口、`ServerCallbacks`、客户端原生输入mixins | 原技能事件、模型、音频保留；退出/死亡/关闭/停止的清理仍调用原职责入口 |

客户端旧spawn重定向已删除：26.3原版本身已采用 `EntitySpawnRequest(LOAD,true)` 完成原保障。旧required注入不再适用，删除不降低原版加载行为。箭行动倍率使用Fabric同步attachment，包含新观察者；其专项断言在独立测试mod保留。
