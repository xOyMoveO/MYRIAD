# 全角色伤害成长与界面验收

## 伤害

本次核对 15 名角色的普攻和 120 个已登记候选技能。按技能、分支、伤害段和取样时点列出的新旧公式、零购买基线、单点与高投资结果见[前十名角色台账](role-damage-first-ten.md)和[后五名角色台账](role-damage-last-five.md)。台账也收录独立治疗、护盾、控制、条件增伤与其他没有直接伤害的候选。

| 减伤前样例 | 零购买 | 一点力量 | 一点智慧 |
| --- | ---: | ---: | ---: |
| 墨尘、青萝、云笙普通魔法弹 | 65 | 65 | 75 |
| 寒泽普通魔法弹 | 78 | 78 | 90 |
| 赤霄一级断岳斩 | 93 | 100.5 | 93 |
| 朔一级藏锋，满刀势附伤 | 52 | 58 | 52 |
| 朔一级见切，成功格挡后的拔刀附伤 | 70 | 77.5 | 70 |

`damage_growth` 的表驱动检查覆盖前十名的 60 份、后五名的 45 份伤害公式。它还执行 58 个服务器实扣血场景，包括四名法师的发射快照、临时物攻与法强、近战、伊珂飞行普攻、落霞普攻箭和飞羽连弦、天刑物理普攻触发的法术附伤、云笙纸鹤的延迟命中，以及凌渊每柄飞剑的独立扣血。测试固定暴击和抗性，分别核对减伤前公式与实际扣血。

焚诀一级「焚天火海」在服务器实际落地后命中一次，随后脉冲四次。五段全中的减伤前合计为零购买 314、一点力量 326、一点智慧 314。凌渊两柄飞剑对法抗 25 的目标协攻，零购买合计实际扣血 21.76，一点智慧后扣血 25.60。

## 角色面板与聊天

角色面板在 427×240、320×180、960×540 逻辑像素下分别宽 367、296、640。960×540 时选中技能后显示 192 像素宽的详情列。布局专项检查了 108 种布局组合与 3,456 个悬浮说明状态。聊天另有 35 项布局与时间检查。[对局聊天实现记录](role-combat-chat-20261125.md) · [双客户端操作与前后截图](role-panel-chat-client.md)

## 复核入口

构建使用 Minecraft 26.3、Fabric Loader 0.20.1 和 JDK 25。离线构建及发行包命名空间检查通过。下面列出的 17 项服务端 GameTest 全部通过，各自的 XML 包含相应名称的用例。每项单独启动 JVM，本次具名日志和 XML 保存在构建目录的 `verification/role-damage-panel-chat/` 下。

主包 `myriad-0.1.0.jar` 为 35,490,380 字节，SHA256 为 `14a18067461593088c5c9c2d3c4df98e9867fd6653f1260beefe46ecb664f219`。双客户端终验使用同一摘要的包。

```sh
bash ./gradlew --offline build
python tools/check_namespace.py --jar build/libs/myriad-0.1.0.jar
for suite in damage_growth first_group melee_arc melee_assist projectile_basic_assist \
    chixiao_opening_pace luoxia yike rivet shuo_pilot roster_a roster_b keheng \
    training_ui hero_action_time match_economy world_time
do
    bash ./gradlew --offline runGameTestServer -PmyriadGameTests -PgameTestFilter="myriad:$suite" || break
done
```
