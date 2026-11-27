# 万象（MYRIAD）15 角色第一人称代码盘点与旧版录屏痛点基线

- **调研工单**：[#76 盘点万象现有 15 角色第一人称代码与旧版录屏痛点基线](https://github.com/xOyMoveO/MYRIAD/issues/76)（所属地图：[#72](https://github.com/xOyMoveO/MYRIAD/issues/72)）
- **源码基线**：`abe5f75`（`feat/melee-first-person-motion`，Minecraft **26.3** / Fabric Loader **0.20.1** / Fabric API **0.166.2+26.3** / Java **25**，见 `gradle.properties:1-10`）
- **实机回看对照**：`outputs/无手臂新版联测-20261127/回看/九人近战与归霞弓.mp4`（45 秒分屏对比：左屏右主手·无手臂·副手空闲，右屏左主手·无手臂·副手持火把）
- **领域术语遵循**：`GLOSSARY.md`（第一人称无手臂版、待机纵深角、普攻接触姿态（HIT）、近战输入缓冲、炮台姿态等）

---

## 执行摘要

1. **15 名正式角色已全部接入自定义第一人称管线，无一退回原版渲染**，但实现高度碎片化：13 个独立 `*View` 类按硬编码顺序串联分发（`MyriadClient.java:352-365`），姿态数学分裂为**欧拉角**、**基向量/四元数**、**混合矩阵**三套互不兼容的范式，左主手镜像更是存在 **4 种**互异写法，`equipProgress` 处理也不一致（7 个角色响应、6 个忽略）。
2. **第一人称无手臂版（PR #70）仅在渲染末尾“砍掉手臂绘制”**（`FirstPersonArmVisibility.hidePlayerArms()`，见 `MyriadHandRendererMixin.java:21-24` 与各 `*View`/`WeaponHands` 内的 `return`），**未对任何角色的待机构图、支点位置或挥砍轨迹做无手臂适配**。这直接放大了四个定量可证的画面痛点：
   - **立牌待机（12/15 角色）**：长剑/长刀/法杖/战刃/重锤/弓的**待机纵深角**集中在 `0.0° ~ +20.5°`（甚至 `rivet` 为 `-11.9°` 倒向玩家、`yunsheng` 为 `±6.4°`），刀身离竖直仅 `9.4° ~ 22.3°`，握点横向偏心距窄至 `gripNDC.x = +0.23 ~ +0.46`（70° 手部 FOV 下），刀尖直插屏幕右上 `tipNDC.y = +0.32 ~ +0.82`。
   - **空中自转无支点**：全部 15 角色均以**掌心/握点 `(0,0,0)`** 为刚体局部旋转原点（无手臂隐藏后失去肩-肘-腕几何锚点）；尤其 `fenjue` 因 `FlameDaoRig.java:18` 对 `x/y` 施加 `0.38× / 0.35×` 坐标压缩，实际掌心位移仅 `0.40 ~ 0.43` 格，`x` 始终停在 `+0.03 ~ +0.45` 未过屏中轴，呈现最明显的“原地自转”。
   - **动作幅度偏小（部分角色）**：`keheng` 刺击掌心总行程 `0.403` 格、轴向偏转仅 `9.0°`；`fenjue` 掌心行程被压缩至 `0.400 ~ 0.431` 格；`rivet` 锤击掌心位移 `0.478` 格；`shuo` 横扫 `BASIC_SWEEP` 掌心位移 `0.440` 格、轴向仅 `55.1°`。
   - **线性短起手 + 极长平缓收招**：全部 9 名近战普攻均绑定 `MeleeStrokeTiming`（`HIT = 0.85` tick ≈ `42.5 ms`，`END_OF_CUT = 2.35` tick ≈ `117.5 ms`，总时长 `clamp(cadence * 1.20 + 3, 6, 72)`）。在基准攻速 `0.70`（`cadence = 28.57` tick，总时长 `37.29` tick ≈ `1864 ms`）下，起手蓄力仅 `0.33 ~ 0.59` tick（`16.5 ~ 29.5 ms`，不到 1 个逻辑 tick），而 **92.2% ~ 94.6% 的动画时间（`34.4 ~ 35.3` tick）都在平缓漂回待机**，速度比高达 `24× ~ 64×`；同时 `MeleeStrokeTiming.path()` 本身为分段线性映射，无独立顿帧/惯性回弹。
3. **MC 26.3 / Fabric 渲染接入边界清晰**：第一人称手部由 `GameRenderer.renderItemInHand` 使用固定 **70° 垂直 FOV**（`Camera.calculateHudFov`）渲染，并在调用 `FirstPersonHandsAndItemsRenderer.submitArmWithItem` 前已经叠加 `bobHurt`、`bobView` 与原版视角滞后 `(viewRot - bob) * 0.1f`。若未来引入极克制的**纯视觉相机微联动（含微滚转 Roll）**，不可走现有 `MyriadCameraMixin`（`Camera.setRotation(FF)V` 硬编码 `roll = 0f` 且会改动视锥前向），而应挂接在 `GameRenderer.bobHurt`（或 `renderLevel` / `renderItemInHand` 的 `PoseStack` 阶段），做到与 `LocalPlayer` 朝向、`Minecraft.pick()` 射线及准星完全解耦。

---

## 一、15 名正式角色第一人称全量盘点

### 1.1 总入口与分发机制

- **手部渲染拦截**：`MyriadHandRendererMixin` 注入 `FirstPersonHandsAndItemsRenderer.submitArmWithItem` 的 `HEAD`（`src/main/java/com/myriad/mixin/MyriadHandRendererMixin.java:26-34`），构造 `HandRenderContext` 并调用 `MyriadClient.onRenderHand(event)`（`src/main/java/com/myriad/MyriadClient.java:333-350`）。
  - 若 `MAIN_HAND` 处于**近战输入缓冲**抬手预备态（`MeleeAttackBuffer.preparation`，`src/main/java/com/myriad/client/MeleeAttackBuffer.java:134-143`），先对整个 `PoseStack` 施加平移 `(side * 0.025 * prep, +0.065 * prep, +0.055 * prep)`（`MyriadClient.java:344-346`），再分发至 `renderWeaponHand(event)`。
  - `renderWeaponHand` 按固定顺序依次轮询 **13 个 `*View` 类**（`MyriadClient.java:352-365`），命中后由该 `*View` 调用 `event.setCanceled(true)` 接管渲染。
  - 兜底分支（`MyriadClient.java:366-374`）仅在 13 个 View 全部返回 `false` 时生效：对非原版近战角色在冷却未就绪时隐藏武器；对 15 名正式角色正常第一人称战斗态而言，**均已被前 13 个 View 覆盖，不走原版渲染**（仅在打开 GUI、`isUsingItem()`、`isDestroying()` 挖方块、旁观/阵亡观战、或手中非初始武器时退回原版）。
- **无手臂隐藏门控**：`FirstPersonArmVisibility.hidePlayerArms()`（`src/main/java/com/myriad/client/firstperson/FirstPersonArmVisibility.java:11-19`）在模组启用、本地玩家第一人称且非旁观/复活观战时返回 `true`。
  - 原版空手/地图手：由 `MyriadHandRendererMixin.myriad$hideVanillaHand` 在 `FirstPersonHandsAndItemsRenderer.renderPlayerHand` `HEAD` 取消（`MyriadHandRendererMixin.java:21-24`）。
  - 自定义手臂网格：13 个 `*View` 及 `WeaponHands.draw` 均在提交 `ModelPart` 前检查 `if (FirstPersonArmVisibility.hidePlayerArms()) return;`。
- **普攻客户端预演入口**：`MyriadClient.predictAttack`（`MyriadClient.java:115-130`）分两段触发 9 名近战角色的第一人称普攻预演：
  - 先行调用（自行校验取消/攻速/方块命中）：`NightDaggerView.onAttack`、`ThunderSwordView.onAttack`、`FlameDaoView.onAttack`、`ShuoView.onAttack`、`WindBladeView.onAttack`；
  - 后行统一校验后调用（要求 `getAttackStrengthScale(0.5f) >= 1` 且非非辅助方块命中）：`LingyuanHandView.attack`、`WarSpiritHandView.attack`（仅赤霄）、`KehengView.attack`、`RivetWeaponView.attack`。
  - 其余 6 名非原版近战角色（`VanillaMeleeAttack.usesVanilla == false`，见 `src/main/java/com/myriad/combat/VanillaMeleeAttack.java:30-35`）：4 名法师（`xuanchen`、`hanze`、`qingluo`、`yunsheng`）右键发弹（仅墨尘有客户端起手预演 `InkFlagView.prepareBasic()`，`MyriadClient.java:292`）；`yike` 左键发 `BasicAttackPayload(-1)` 等服务端 `WeaponPosePayload` + `YikeStatePayload`；`luoxia` 左键走 `LuoxiaInput.press` 预测起拉 + 服务端 `LuoxiaBowPosePayload` 确认。

---

### 1.2 15 角色全量对照表（武器、类、模型约定、旋转范式、状态同步、镜像与副手）

> 注：所有武器物品均在 `src/main/java/com/myriad/registry/ModItems.java:43-66, 105-127` 注册与映射；所有自定义第一人称渲染均使用 `ItemDisplayContext.NONE`（绕过 JSON `display.firstperson_righthand` 变换，但仍经由 Minecraft 物品管线内置的 `(-0.5, -0.5, -0.5)` 居中，即模型像素 `(8, 8, 8)` 为局部原点 `(0, 0, 0)`）。

| # | 角色（ID） | 武器物品（`ModItems`） | 第一人称 View / Motion / Rig 类 | 模型包围盒（像素）与局部握点/轴约定 | 姿态/旋转实现方式 | 动作触发与同步来源 | 左主手镜像与 `equipProgress` | 副手持物（如火把）处理 |
|---|---|---|---|---|---|---|---|---|
| 1 | 影刹·夜影 (`yingxiao`) | `YINGCI`（渲染替换模型 `yingxiao_shadow_dagger`，`NightDaggerView.java:27,114-117`） | `NightDaggerView` / `NightDaggerMotion` / `NightDaggerRig` + `NightSkillMotion` / `NightSkillHandRenderer` / `NightSkillHandVfx` / `NightBasicTrail` | `X[6.95,9.05] Y[6.08,17.48] Z[7.58,8.42]`；握点 `(8,8,8)`，`+Y` 为刃向，默认反手再经 `blade` 绕 Z 转 `±(90°-grip)` | **欧拉角**：`T(x*side,y,z) · Rx(pitch) · Ry(yaw*side) · Rz(roll*side) · Ry(wrist*side) · S(0.74)` + `blade`: `Rz(side*(90-grip)) · Ry(side*5) · S(1.121)`（`NightDaggerRig.java:11-16,26-30`）；关键帧 `smoothstep` 插值 | **普攻**：纯客户端预演（`onAttack` -> `NightDaggerMotion`，3 连段）；**技能**：服务端 `NightSkillViewPayload`（`NetworkClientSetup.java:52-53`） | 符号翻转欧拉角；**忽略 `equipProgress`** | `OFF_HAND` 空手时取消渲染；持火把/地图返回 `false` 保留原版；技能副手仅在 `offhandItem.isEmpty()` 绘制（`NightDaggerView.java:85-93,128`） |
| 2 | 雷部·天刑 (`tianxing`) | `LEITINGJIAN` (`leitingjian`) | `ThunderSwordView` / `ThunderSwordMotion` / `ThunderSwordRig` + `ThunderSkillMotion` / `ThunderSkillRig` / `ThunderSkillHandRenderer` / `ThunderSkillHandVfx` / `ThunderBasicTrail` | `X[4.50,11.50] Y[2.50,30.50] Z[7.50,8.44]`；握点 `(8,8,8)`，`+Y` 为剑身，`±X` 为双刃，刃尖 `y=1.40625` 格（`30.5` 像素） | **欧拉角 + 速度导刃自旋**：`wrist` 用 `Rx·Ry·Rz` 欧拉角，`blade` 固定 `Rz(±65°)` 后按有限差分速度投影算 `atan2` 绕 `Y` 轴扭转对准挥向（`ThunderSwordRig.java:21-26,45-68`）；关键帧 `smoothstep` | **普攻**：客户端预演（`onAttack` -> `ThunderSwordMotion`，3 连段）；**技能**：服务端 `ThunderSkillViewPayload` + 放置态 `placementType==106` 预瞄（`ThunderSwordView.java:41-46,55-59`） | 符号翻转欧拉角 + 刃面角参考翻半圈；**读取 `equipProgress`**（`-0.48*e`），挥砍期调用 `holdEquipped()`（`ThunderSwordView.java:88-89`） | `OFF_HAND` 仅在技能需副手且空手时取消；持火把返回 `false` 保留原版（`ThunderSwordView.java:81,91`） |
| 3 | 火部·焚诀 (`fenjue`) | `LIEYANJIAN` (`lieyanjian`) | `FlameDaoView` / `FlameDaoMotion` / `FlameDaoRig` + `FlameSlashMotion` / `FlameStepMotion` / `FlameSkillMotion` / `FlameSkillRig` / `FlameSkillHandVfx` / `FlameBasicTrail` / `FlameStepTrail` | `X[5.94,10.06] Y[4.75,30.28] Z[7.59,8.41]`；握点 `(8,8,8)`，`+Y` 刀身，**单刃仅 `+X` 开锋**，刀尖 `y=1.390625` 格 | **欧拉角（含 X/Y 压缩）+ 速度导刃**：`palm` 施加 `0.23+0.38x`、`-0.58+0.35y` 压缩（`FlameDaoRig.java:16-19`）；`wrist` 用 `Rx·Ry·Rz`；`FlameDaoMotion` 用 **Catmull-Rom/Hermite 三次样条**插值（`FlameDaoMotion.java:57-62`）；`blade` 绕 `Y` 轴导刃（`FlameDaoRig.java:47-77`） | **普攻**：客户端预演（3 连段）；**技能**：`FlameSkillViewPayload` + `AbilityVfxPayload`（`FlameSlashMotion`/`FlameStepMotion`，`NetworkClientSetup.java:44-45,148-151`）+ `placementType==104` 预瞄 | 符号翻转欧拉角；**读取 `equipProgress`**（`-0.48*e`），挥砍期调用 `holdEquipped()`（`FlameDaoView.java:114`） | 技能态且空手才取消 `OFF_HAND`；持火把返回 `false` 保留原版（`FlameDaoView.java:100-103,110,135`） |
| 4 | 玄阵·墨尘 (`xuanchen`) | `ZHENQI` (`zhenqi`) | `InkFlagView` / `InkHandMotion` / `InkHandRig` / `InkHandEffects` | `X[5.24,11.55] Y[3.65,27.16] Z[6.42,8.79]`；握点 `(8,8,8)`，`+Y` 旗杆轴 | **四元数轴对齐 + 绕轴滚转**：`Quaternionf().rotationTo(0,1,0, dir).rotateY(twist)`（`InkHandRig.java:17-19,28-33`），关键帧对 `(grip, axis, palm, twist, palmRoll)` 做 `smoothstep` | **法术弹与 Q/W**：右键/按键客户端短预演 `prepareBasic`/`prepareSkill`（`MyriadClient.java:292,735`），服务端 `AbilityVfxPayload`（`InkCastAnimation`）确认（`AbilityMeshParticle.java:106-111`） | **`HandedView.weapon`** 矩阵共轭反射 `S(-1,1,1)·M·S(1,1,-1)`（保正行列式）；**读取 `equipProgress`**（`-0.48*e`），不调 `holdEquipped()` | `OFF_HAND` 仅在 `palm.y>-1.14` 且空手时取消；持火把返回 `false` 保留原版（`InkFlagView.java:112-116`） |
| 5 | 风部·素岚 (`sulan`) | `FENGREN` (`fengren`) | `WindBladeView` / `WindHandMotion` / `WindHandRig` / `WindHandVfx` | `X[5.03,11.13] Y[4.54,31.99] Z[6.84,9.15]`；握点 `(8,8,8)`，`+Y` 刃轴，刃根 `(0.057,0.196,0)`，刃尖 `(0.17,1.499,0)`（`WindHandRig.java:12-13`） | **正交基向量矩阵 + 速度导刃**：`basis(palm, edge, shaft)` 直接填列向量，其中 `edge` 在 `reference/face` 平面内按 `rollOffset` 旋转对准刀尖切向速度（`WindHandRig.java:21-80,111-118`）；普攻挥斩前段 `t²`、后段 `1-(1-t)³` | **普攻**：客户端预演（`onAttack` -> `WindHandMotion`，3 连段）；**技能**：按键预演 `WindBladeView.prepare`（`MyriadClient.java:734`）+ 服务端 `AbilityVfxPayload`（`WindCastAnimation`）确认（`AbilityMeshParticle.java:112-116`） | **`HandedView.weapon`** 矩阵共轭反射；**读取 `equipProgress`**（`-0.48*e`），挥砍期调用 `holdEquipped()`（`WindBladeView.java:136-138`） | `OFF_HAND` 仅在 `free.y>-1.15` 且空手时取消；持火把返回 `false` 保留原版（`WindBladeView.java:128-132`） |
| 6 | 御剑宗师·凌渊 (`lingyuan`) | `LINGYUAN_SWORD` (`lingyuan_sword`) | `LingyuanHandView` / `LingyuanHandMotion` / `LingyuanHandRig` + `MeleeBladeLight.SILVER` | `X[3.50,12.50] Y[4.26,27.30] Z[6.50,9.50]`；握点 `(8,8,8)`，`+Y` 剑尖（`y=27.3` 像素 -> `1.20625` 格），`±X` 双刃（`LingyuanHandRig.java:7-10`） | **正交基向量矩阵 + 双刃最短角导刃**：`basis(grip, across, axis)` 直接填列，双刃取 `IEEEremainder(..., π)` 最短半周导刃（`LingyuanHandRig.java:16-32,55-59`）；起手用二次 Bézier `from→ready→beat` | **普攻**：客户端预演（`LingyuanHandView.attack`，主动忽略服务端 `BASIC` 重复包，见 `LingyuanHandView.java:84`）；**技能**：纯服务端 `AbilityVfxPayload`（`LingyuanHandCues`，`AbilityMeshParticle.java:96-100`） | 向量 X 取反 + 右手叉积重建基矩阵（`LingyuanHandRig.java:31,50,55-58`）；**读取 `equipProgress`**，挥砍期调用 `holdEquipped()`（`LingyuanHandView.java:101-102`） | `OFF_HAND` 仅在 `palm.y>-1.19` 且空手时取消；持火把返回 `false` 保留原版（`LingyuanHandView.java:94-98`） |
| 7 | 霜部·寒泽 (`hanze`) | `SHUANGBING` (`shuangbing`) | `StaffHandView` / `StaffHandMotion(true)` / `StaffHandRig` | `X[5.24,11.11] Y[-8.56,30.77] Z[6.62,9.38]`；握点 `(8,8,8)`，长杖沿 `Y` 轴贯穿上下 | **四元数轴对齐 + 绕轴滚转**：`Quaternionf().rotationTo(0,1,0, dir).rotateY(roll)`，缩放 `0.69*1.06=0.7314`（`StaffHandRig.java:9,18-21,42-47`）；出招段二次 Bézier，回位 `smoothstep` | **普攻（法术弹）与全部技能**：**纯服务端驱动**，全部经 `AbilityVfxPayload`（`StaffHandCues`，`AbilityMeshParticle.java:101-105`），无客户端点击预演（见 `StaffHandMotion.java:172` 注释） | 向量 X 取反 + `roll` 取反 + 四元数重算；**读取 `equipProgress`**（`-0.48*e`），不调 `holdEquipped()` | `OFF_HAND` 仅在 `palm.y>-1.20` 且空手时取消；持火把返回 `false` 保留原版（`StaffHandView.java:104-108`） |
| 8 | 丹心·青萝 (`qingluo`) | `QINGLUO_STAFF` (`qingluo_staff`) | `StaffHandView` / `StaffHandMotion(false)` / `StaffHandRig` | `X[4.03,11.97] Y[-7.87,31.80] Z[6.79,9.21]`；握点 `(8,8,8)`，长杖沿 `Y` 轴 | 同寒泽（缩放 `0.69*1.05=0.7245`，`StaffHandRig.java:10`）；节奏更舒缓（`herb` `snap=1.4t` vs `frost` `0.95t`，`StaffHandMotion.java:98-104`） | **普攻（法术弹）与全部技能**：**纯服务端驱动**，`AbilityVfxPayload`（`StaffHandCues`） | 同寒泽 | 同寒泽 |
| 9 | 破军·赤霄 (`chixiao`) | `CHIXIAO_BLADE` (`chixiao_blade`) | `WarSpiritHandView` / `WarSpiritHandMotion(true)` / `WarSpiritHandRig` + `MeleeBladeLight.COPPER` | `X[5.05,10.95] Y[0.58,31.59] Z[6.29,9.71]`；握点 `(8,8,8)`，`+Y` 刀身（刀尖 `31.585` 像素），**单刃 `+X`**（`WarSpiritHandRig.java:8,26`） | **正交基向量矩阵 + 单刃全周导刃**：`basis(grip, twist(side,face,roll), axis)`，`edge>0` 时按 `strikeTravel` 的 `atan2` 做 `2π` 单刃导刃（`WarSpiritHandRig.java:17-37`）；各段 `smoothstep` | **普攻**：客户端预演（`WarSpiritHandView.attack`，忽略服务端 `BASIC` 重复包，见 `WarSpiritHandView.java:89`）；**技能**：服务端 `AbilityVfxPayload`（`WarSpiritHandCues`，`AbilityMeshParticle.java:91-95`） | 向量 X 取反 + `roll` 取反 + 右手叉积基矩阵；**读取 `equipProgress`**，挥砍期调用 `holdEquipped()`（`WarSpiritHandView.java:99-100`） | `OFF_HAND` 始终返回 `false`（空手靠全局 `hidePlayerArms` 隐藏，持火把保留原版）；副手支持臂在 `MAIN_HAND` 内按 `offhandItem.isEmpty()` 门控（`WarSpiritHandView.java:97,114`） |
| 10 | 灵契·云笙 (`yunsheng`) | `YUNSHENG_BELL` (`yunsheng_bell`) | `WarSpiritHandView` / `WarSpiritHandMotion(false)` / `WarSpiritHandRig` | `X[1.48,15.31] Y[2.47,30.91] Z[1.48,14.52]`；**非 `(8,8,8)` 握点**：提手玉环在 `(7.8025, 30.12, 8)`（模型顶部），渲染时先平移 `(-BELL_GRIP_X, -BELL_GRIP_Y, 0)` 使铃体垂挂于掌下（`WarSpiritHandRig.java:13,34-37`） | **正交基向量矩阵 + 顶部悬点偏移**：`hand = grip + (-0.10, +0.84, -0.70)`，轴 `z` 重映射为 `0.23 + 0.55*aim.z`，缩 `0.60`，绕环心枢转 | **普攻（法术弹）与全部技能**：**纯服务端驱动**，`AbilityVfxPayload`（`WarSpiritHandCues`） | 同赤霄；**读取 `equipProgress`**，不调 `holdEquipped()` | 同赤霄 |
| 11 | 居合·朔 (`shuo`) | `SHUO_SWORD` (`shuo_sword`) + 伴生刀鞘 `SHUO_SCABBARD` (`shuo_scabbard`) | `ShuoView` / `ShuoMotion` / `ShuoRig` + `ShuoTrail` / `ShuoSkillTrail` / `ShuoBladeLight`（第三人称收鞘另有 `ShuoHeldView` / `ShuoHeldLight`） | 刀 `X[6.42,9.57] Y[5.20,26.45] Z[5.90,10.10]`，鞘 `Y[8.00,23.81]`；握点 `(8,8,8)`，握点到鞘口 `14/64`、到刀尖 `73.8/64`（`ShuoRig.java:11-15`）；渲染加 `rotateY(π)` 翻转朝向与非均匀缩放 `(1.14, 1.55, 1.14)`（`ShuoRig.java:169-170`） | **正交基向量矩阵 + 曲刃出鞘圆弧约束**：`ShuoRig.basis(origin, axis, edge, mirror)` 直接填列；收/拔刀段走 `DRAW_RADIUS` 曲刃切线通道 `passage()` 保证刀身沿鞘口滑动不穿模（`ShuoRig.java:37-56,153-170`） | **普攻**：客户端预演（`ShuoView.onAttack`，`BASIC_SWEEP` 延后 1 tick 对齐首帧，`ShuoMotion.java:39`），服务端 `ShuoViewPayload` 确认；**技能/收拔刀**：服务端 `ShuoViewPayload`（`NetworkClientSetup.java:42-43`）+ `g1_shuo_drawn` 计数器 | **列向量镜像 + 厚度 Z 取反**（`ShuoRig.java:159-161`，保正手性）；**忽略 `equipProgress`** | **唯一带副手避让位移的角色**：副手非空（如火把）时 `OFF_HAND` 返回 `false` 保留火把，同时 `MAIN_HAND` 对刀鞘整体施加 `ShuoRig.occupiedOffset` 水平移至主手侧避开火把（`ShuoView.java:55-68`） |
| 12 | 时律·刻衡 (`keheng`) | `HENGKE` (`hengke`) | `KehengView` + `WeaponBodyPose` / `WeaponHands` + `MeleeBladeLight.needle` | `X[6.02,11.47] Y[2.20,30.00] Z[7.18,8.82]`；**非 `(8,8,8)` 握点**：皮柄中点在 `Y=5.7` 像素（`GRIP_Y=(5.7-8)/16=-0.14375`，`KehengView.java:16,88`），`+Y` 剑尖 | **混合矩阵**：`wrist` 用平移 + `WeaponBodyPose.wrist` 欧拉角（`Ry·Rz·Rx`）+ `aimingTurn`；`blade` 用 `ShuoRig.basis(0, axis, +X)` + `Ry(±142°)` + `S(1.18)` + `T(0, -GRIP_Y, 0)`（`KehengView.java:78-92`） | **普攻**：客户端预演（`KehengView.attack` -> `Stroke`，屏蔽 `WeaponBodyPose` 的 `Move.RAPIER` 避免叠加，`WeaponBodyPose.java:121-127`）；**技能**：服务端 `WeaponPosePayload`（`NetworkClientSetup.java:36-37`） | 符号翻转（`side = left?-1:1`）；**忽略 `equipProgress`** | `OFF_HAND` 始终返回 `false`；持火把保留原版，`WeaponHands.support` 仅在副手空闲时显示（`KehengView.java:69,82`） |
| 13 | 虹轨·伊珂 (`yike`) | `HONG_LIE`（第一/第三人称拆为 `HONG_LIE_FRAME` + 3×`HONG_LIE_PETAL` + `HONG_LIE_RING` 分件装配，`YikeWeaponView.java:92-106`） | `YikeWeaponView` / `YikeWeaponAnimation` / `YikeClientState` + `WeaponBodyPose` / `WeaponHands` | 炮架 `X[6.60,9.38] Y[1.90,22.68] Z[6.62,9.38]`；握点 `(8,8,8)`，`+Y` 为炮管指向；三瓣在 `y=5.5/16` 处 120° 铰接展开，滑环在 `(13.2+ringSlide)/16` 处滑动旋转 | **正交基向量 + 四元数瞄准插值 + 分件欧拉铰接**：`palm/axis` 经 `Quaternionf.rotationTo` `slerp` 追踪服务端瞄准向量 `weaponAim`，叠 `WeaponBodyPose.wrist` 与后坐 `recoil/16`，再按 `YikeWeaponView.basis` 建矩阵（`YikeWeaponView.java:35-49,85-89`） | **全服务端驱动**：炮台姿态/展开/瞄准由 `YikeStatePayload`（`YikeClientState`，TTL 5 tick），开火后坐由 `onMuzzle` 触发，架枪/后坐手势由 `WeaponPosePayload` 驱动（`NetworkClientSetup.java:36-39`） | 符号翻转（`mirror = left?-1:1`）；**忽略 `equipProgress`** | `OFF_HAND` 空手时取消，持火把返回 `false` 保留原版；`WeaponHands.support` 门控副手为空（`YikeWeaponView.java:32,51`） |
| 14 | 铆钉·战场筑垒师 (`rivet`) | `RIVET_HAMMER` (`rivet_hammer`) | `RivetWeaponView` / `RivetWeaponMotion` / `RivetWeaponRig` + `MeleeBladeLight.sparks` | `X[-10.00,9.50] Y[-1.00,27.50] Z[-4.00,4.00]`；**原点偏移模型**（未以 `(8,8,8)` 居中，中心偏近 `(0, 5.5, 0)`），`RivetWeaponRig.item` 显式加 `translate(0.5, 0.5-5.5/16, 0.5)` 抵消 `NONE` 居中并令 `Y=5.5` 为握点（`RivetWeaponRig.java:9,27-29`） | **纯欧拉角**：`T(x,y,z) · Rx(pitch) · Ry(yaw) · Rz(roll) · S(0.60)`（`RivetWeaponRig.java:12-16,27-29`）；普攻下砸段用 `falling(t)=t²` 加速，其余 `smoothstep` | **普攻**：客户端预演（`RivetWeaponView.attack` -> `MOTION.basic`），命中火花由服务端 `AbilityVfxPayload`（`RivetVfx.basicContact`）触发；**技能/受击微震**：纯服务端 `AbilityVfxPayload`（`AbilityMeshParticle.java:72-73`） | **列向量镜像 + 厚度 Z 取反**（`RivetWeaponRig.java:18-22`）；**忽略 `equipProgress`** | `OFF_HAND` 始终返回 `false`（无自定义副手）；持火把保留原版（`RivetWeaponView.java:48`） |
| 15 | 落霞 (`luoxia`) | `GUIXIA_BOW`（4 态持握模型 `guixia_bow[_pulling_0..2]_held` + 自绘箭矢 `LuoxiaGeometry::nockedArrow`，`LuoxiaBowView.java:63-71,212-235`） | `LuoxiaBowView` / `LuoxiaBowMotion` / `LuoxiaPoseClient` / `LuoxiaInput` | `X[7.13,15.05..23.09] Y[-6.84,22.84] Z[6.76,9.24]`；握点 `(8,8,8)`，`+Y` 上弓臂，`+X` 拉弦方向（拉满时 `X` 延展至 `23.09`），渲染前先绕 `Y` 转 `-90°` 使 `+X` 朝向玩家 `+Z` | **纯欧拉角 + 弦长宽度补偿**：`T(side*(0.32-lift*0.64), -0.36+lift-step, -1.10-lift+recoil*0.055) · [锁瞄 Ry·Rx] · Rz(side*roll) · Ry(-90°) · S(0.74)`，弓身 X 轴按 `bowWidth()` 连续缩放衔接四档拉弓模型（`LuoxiaBowView.java:90-97,215`） | **起拉**：`LuoxiaInput.press` 客户端预测 1 帧拉弓（`LuoxiaPoseClient.begin`）；**拉满/技能/锁瞄/后坐/垫步**：服务端 `LuoxiaBowPosePayload`（`NetworkClientSetup.java:40-41`） | 符号翻转（`side = left?-1:1`），箭台偏移 `shelf = ±0.058`；**忽略 `equipProgress`** | `OFF_HAND` 空手时取消，持火把返回 `false` 保留原版；拉弦副手仅在副手为空且有 `cue` 时绘制（`LuoxiaBowView.java:79-83,106`） |

---

## 二、旧版录屏痛点的代码级定量溯源

对照回看视频 `outputs/无手臂新版联测-20261127/回看/九人近战与归霞弓.mp4`（逐秒帧已核验）与上表源码，将暴露的四大视觉/手感痛点逐一定量定位到代码行：

### 2.1 痛点一：立牌式竖直待机（纵深角过小、竖直度过高、偏心距过窄）

依据 `GLOSSARY.md:75-77` 定义：**待机纵深角** = 刃根指向刀尖的单位轴向量 $\mathbf{a}=(a_x,a_y,a_z)$ 与成像平面（XY 平面）的夹角 $\arcsin(-a_z)$（正值指向场景深处 `-Z`，负值指向玩家 `+Z`）；离竖直角 = $\arccos(|a_y|)$；屏幕投影按 MC 26.3 手部固定垂直 FOV **70°**（`Camera.calculateHudFov`）、16:9 纵横比计算归一化设备坐标 `NDC`（中心 `(0, 0)`，屏幕边缘 `±1.0`）。

| 角色 | 源码常量位置 | 待机握点 `(x, y, z)` | 武器主轴 `(ax, ay, az)` | **待机纵深角** | **离竖直角** | `gripNDC (x, y)` | `tipNDC (x, y)` | 视频帧表现（`九人近战与归霞弓.mp4`） |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `luoxia`（待机） | `LuoxiaBowView.java:91-97` | `(+0.320, -0.360, -1.100)` | `(-0.407, +0.914, 0.000)` | **`0.0°`** | `24.0°` | `(+0.23, -0.47)` | `(+0.03, +0.35)` | `00:42` 弓体紧贴成像平行面，上弓梢逼近准星正上方 |
| `rivet` | `RivetWeaponMotion.java:13` | `(+0.560, -0.560, -1.280)` | `(+0.049, +0.977, +0.205)` | **`-11.9°`** | **`12.2°`** | `(+0.35, -0.62)` | `(+0.43, +0.32)` | `00:32`/`00:35` 锤头反而向玩家后仰，像一块 T 型路牌立在右中 |
| `yunsheng`（垂铃） | `WarSpiritHandMotion.java:19` + `Rig:21,34` | `(+0.480, +0.180, -1.560)` | `(-0.122, -0.986, -0.111)` | **`+6.4°`** | **`9.5°`** | `(+0.25, +0.16)` | `(+0.17, -0.72)` | 悬环位高居准星右上方，铃身近乎铅垂悬挂 |
| `shuo`（收鞘） | `ShuoRig.java:39,63,70` | `(-0.208, +0.080, -1.161)` | `(-0.110, -0.987, +0.120)` | **`-6.9°`** | **`9.4°`** | `(-0.14, +0.10)` | `(-0.34, -2.54)` | `00:24`/`00:36` 鞘口紧贴屏幕中心偏左、几乎垂直插立 |
| `xuanchen` | `InkHandMotion.java:21` | `(+0.545, -0.455, -0.955)` | `(+0.080, +0.965, -0.251)` | **`+14.5°`** | `15.3°` | `(+0.46, -0.68)` | `(+0.42, +0.64)` | 阵旗近乎垂直竖在右中，占满上下视野 |
| `qingluo` | `StaffHandMotion.java:24` | `(+0.555, -0.495, -1.028)` | `(+0.131, +0.956, -0.262)` | **`+15.2°`** | `17.0°` | `(+0.43, -0.69)` | `(+0.43, +0.58)` | 法杖垂直立牌，与墨尘同构 |
| `fenjue` | `FlameDaoMotion.java:8` + `Rig:18` | `(+0.443, -0.482, -1.060)` | `(+0.190, +0.940, -0.284)` | **`+16.5°`** | `20.0°` | `(+0.34, -0.65)` | `(+0.39, +0.75)` | `00:08`/`00:11` 长刀笔直贯屏（占约 70% 屏高），距准星仅 34% 半宽 |
| `tianxing` | `ThunderSwordMotion.java:11` | `(+0.480, -0.475, -1.080)` | `(+0.156, +0.944, -0.290)` | **`+16.8°`** | `19.2°` | `(+0.36, -0.63)` | `(+0.38, +0.82)` | `00:07` 右屏长剑几乎从底到顶直立 |
| `lingyuan` | `LingyuanHandMotion.java:19` | `(+0.520, -0.495, -0.975)` | `(+0.120, +0.940, -0.320)` | **`+18.7°`** | `20.0°` | `(+0.43, -0.73)` | `(+0.39, +0.64)` | `00:16`/`00:19` 正面宽剑身直立，最典型立牌观感 |
| `chixiao` | `WarSpiritHandMotion.java:18` | `(+0.485, -0.520, -1.025)` | `(+0.150, +0.929, -0.339)` | **`+19.8°`** | `21.8°` | `(+0.38, -0.72)` | `(+0.37, +0.72)` | `00:23` 宽刃大刀直立贯屏 |
| `hanze` | `StaffHandMotion.java:23` | `(+0.485, -0.485, -1.020)` | `(+0.030, +0.940, -0.340)` | **`+19.9°`** | `20.0°` | `(+0.38, -0.68)` | `(+0.30, +0.51)` | 冰杖垂直立牌，顶端内倾至 `NDC.x=+0.30` |
| `shuo`（出鞘） | `ShuoRig.java:16` | `(+0.460, -0.710, -1.150)` | `(+0.160, +0.925, -0.345)` | **`+20.2°`** | `22.3°` | `(+0.32, -0.88)` | `(+0.34, +0.76)` | `00:27`/`00:40` 拔刀后仍立在右中偏内，占约 82% 屏高 |
| `sulan` | `WindHandMotion.java:16` | `(+0.495, -0.475, -0.970)` | `(+0.100, +0.931, -0.350)` | **`+20.5°`** | `21.4°` | `(+0.41, -0.70)` | `(+0.35, +0.68)` | `00:12`/`00:15` 宽青瓷风刃正对镜头直立，遮挡感强 |
| `yingxiao` | `NightDaggerMotion.java:11` + `Rig:29` | `(+0.545, -0.345, -0.890)` | `(-0.887, -0.366, -0.281)` | **`+16.3°`** | `68.5°` | `(+0.49, -0.55)` | `(+0.09, -0.73)` | `00:01` 反手横悬，虽非竖立但纵深透视弱、悬在胸前 |
| `yike`（参照） | `YikeWeaponView.java:35` | `(+0.520, -0.390, -1.050)` | `(-0.141, +0.402, -0.905)` | **`+64.8°`** | `66.3°` | `(+0.40, -0.53)` | `(+0.17, -0.02)` | 仅有的高纵深角标杆之一（炮口深指前方） |
| `keheng`（参照） | `KehengView.java:78,85` | `(+0.560, -0.420, -1.220)` | `(-0.130, +0.250, -0.960)` | **`+73.6°`** | `75.5°` | `(+0.37, -0.49)` | `(+0.09, +0.01)` | `00:28`/`00:31` 唯一不呈立牌观感的近战（纵深透视强） |

**代码根因总结**：
1. **纵深角普遍不足**：除了 `keheng`（`+73.6°`）和 `yike`（`+64.8°`），其余 13 名角色（含 8/9 近战与全部法师/射手）待机纵深角全部 $\le +20.5^\circ$（`rivet` 甚至因 `pitch=-16°, yaw=-84°, roll=-28°` 的 `Rx·Ry·Rz` 组合变成 `-11.9°` 后仰），导致武器几乎平行于投影平面，透视缩短（foreshortening）完全缺失。
2. **握点过近中轴且主轴未外斜**：握点多落在 `x = 0.44 ~ 0.56, z = -0.96 ~ -1.15`（`gripNDC.x` 仅 `0.32 ~ 0.46`），而刀剑总长乘以 `SCALE` 达 `1.2 ~ 1.8` 格，且 `ax` 接近 `0` 或略正，使整条刃身垂直贯通 `NDC.y ∈ [-0.73, +0.82]`。
3. **原本为“带手臂构图”服务的低手腕约束残留**：带手臂时为了避免前臂穿出屏幕底部或与刀刃穿模，刻意把掌心往中下方收拢、把刀身竖直抬离手臂，隐藏手臂后这一约束失去理由，反而只剩下悬浮立牌。

---

### 2.2 痛点二：隐藏手臂后的空中漂浮自转（缺乏物理支点）

1. **支点与旋转中心完全重合于掌心/握柄点**：
   - 在带手臂版本中，`WeaponHands.draw`（`WeaponHands.java:18,29-31`）、`ThunderSwordRig.handAt`（`ThunderSwordRig.java:33-40`）、`FlameDaoRig.handAt`（`FlameDaoRig.java:32-40`）、`WindHandRig.arm`（`WindHandRig.java:82-93`）、`InkHandRig.arm`、`StaffHandRig.arm`、`LingyuanHandRig.arm`、`WarSpiritHandRig.arm`、`RivetWeaponRig.hand`（`RivetWeaponRig.java:39-59`）都靠一条从**相机侧虚拟肩/肘锚点（如 `(±0.84, -1.48, -0.70)` 或 `(±0.63, -0.88, -0.16)`）连向 `palm`** 的手臂网格向玩家交代“力学支点”。
   - **但武器自身的运动学从未引入肩/肘圆弧支点约束**：15 名角色全部把武器矩阵写成 `T(palm) · R(orientation)`（绕握柄点 `(0,0,0)` 纯局部旋转）。当 `FirstPersonArmVisibility.hidePlayerArms()`（`FirstPersonArmVisibility.java:11-19`）仅把手臂 `submitModelPart` 直接 `return` 掉之后，视觉上失去了所有连接身体的线段，武器便彻底沦为绕自身某一点在空中自转的刚体。
2. **局部自转行为在无手臂下被严重放大**：
   - **夜影第三击翻刀**：`NightDaggerMotion.java:28-31` 在第 3 击中直接将 `grip` 从 `0°` 线性转至 `180°`（`NightDaggerRig.java:29` 绕局部 Z 轴纯自转），无手掌托握时匕首就像在空中自行翻跟头（视频 `00:02`–`00:03`）。
   - **焚诀掌心被压缩成原地转轴**：`FlameDaoRig.java:18` 对关键帧 `x/y` 施加 `0.38× / 0.35×` 缩放，导致 `FlameDaoMotion` 中规划的挥砍弧被压扁成握柄几乎原地不动的旋转（视频 `00:08`–`00:11`）。
   - **导刃扭转无肘腕协同**：`ThunderSwordRig.java:51-68`、`FlameDaoRig.java:60-77`、`WindHandRig.java:37-72`、`LingyuanHandRig.java:20-29`、`WarSpiritHandRig.java:25-33` 的导刃（edge alignment）全部是“保持握点固定、仅绕刃轴 `+Y` 旋转”，缺乏肩/肘弧形扫掠的联动，在慢速回位段（长达 `35` tick）尤显漂浮（视频 `00:04`–`00:07`, `00:13`–`00:14`, `00:20`–`00:22`）。

---

### 2.3 痛点三：部分角色动作幅度偏小

实测各近战角色一次普攻起手到随挥终点的**真实相机空间掌心位移**与**武器轴向扫掠角**：

| 角色 | 实测掌心位移（格） | 实测轴向扫掠角 | 空间压缩/幅度受限的代码根因 |
|---|---:|---:|---|
| **`keheng`** | **`0.403`** | **`9.0°`** | `KehengView.java:32-35,78,85-86`：`READY(0.015, 0.016, +0.06)` → `FOLLOW(-0.12, 0.025, -0.32)`，前刺 Z 行程仅 `0.38` 格，剑轴仅从 `(-0.13, 0.25, -0.96)` 偏转 `9°` 至 `(-0.23, 0.13, -0.96)`，且无蓄力后拉与收势错落（视频 `00:28`–`00:31`） |
| **`fenjue`** | **`0.400 ~ 0.431`** | 约 `80°~105°`（纯原地甩角） | `FlameDaoRig.java:18`：`palm()` 将原始关键帧 `x` 乘以 **`0.38f`**、`y` 乘以 **`0.35f`**（`0.23 + 0.38*x`），使 1.05 格的计划横向挥幅被硬压缩到仅 `0.40` 格，且握点全程停留在右半屏 `x ∈ [+0.03, +0.45]` |
| **`shuo` (`BASIC_SWEEP`)** | **`0.440`** | **`55.1°`** | `ShuoRig.java:96-99`：第 3 击横扫为保刀柄不越准星，将起点 `(-0.43,-0.32,-1.42)` 到终点 `(-0.03,-0.35,-1.60)` 限制在左半区 `0.44` 格内（视频 `00:26`） |
| **`rivet`** | **`0.478`** | 俯仰 `76°` / 偏航 `32°` | `RivetWeaponMotion.java:14-16`：重锤仅做单段小幅斜下砸（`LIFT(.64,-.42,-1.35)` → `BASIC_FOLLOW(.29,-.65,-1.58)`），且只有 1 套单调动作无连段变化（视频 `00:32`–`00:35`） |
| **`yingxiao`** | `0.540 ~ 0.773` | 欧拉角复合 | `NightDaggerMotion.java:20-31`：第 1 击刺击位移仅 `0.54` 格；因匕首总长仅 `0.59` 格且偏置在右中，屏幕有效扫掠面积小（视频 `00:00`–`00:03`） |
| `tianxing` / `sulan` / `lingyuan` / `chixiao` | `0.856 ~ 1.201` | `67° ~ 119°` | 掌心跨度相对充分，但因下一节的**超短挥击窗口（`2.35` tick）**，这 1 格位移在 60 FPS 下仅占约 **5~7 帧**，肉眼几乎来不及读清弧线即进入长达 1.7 秒的慢漂回位 |

---

### 2.4 痛点四：线性短起手（`HIT = 0.85`）与平缓长收招

全部 9 名近战角色的普攻时序均由 `MeleeStrokeTiming`（`src/main/java/com/myriad/client/firstperson/MeleeStrokeTiming.java:4-26`）与统一基准攻速 `0.70`（`FirstGroupGrowth.java:113`，同步至原版 `Attributes.ATTACK_SPEED` 见 `PlayerDataProvider.java:1202-1210`）共同决定：

```java
// MeleeStrokeTiming.java:5-25
public static final double HIT = .85;          // 0.85 tick = 42.5 ms（60FPS 下仅 2.55 帧！）
public static final double END_OF_CUT = 2.35;  // 2.35 tick = 117.5 ms（60FPS 下仅 7.05 帧！）
public static double recovery(double cadence) {
    return Double.isFinite(cadence) ? Math.clamp(cadence * 1.20 + 3, 6, 72) : 12;
}
```

在默认开局攻速 `AS = 0.70` 下：
- `cadence = player.getCurrentItemAttackStrengthDelay() = 20.0 / 0.70 = 28.57` tick（`1429 ms`）；
- `duration = MeleeStrokeTiming.recovery(28.57) = 37.29` tick（`1864 ms`）；
- 即使叠加高攻速（如 `AS = 1.30`，`cadence = 15.38` tick），`duration` 仍达 `21.46` tick（`1073 ms`）。

**逐角色定量时序拆解（基准 `AS = 0.70`, `duration = 37.29` tick）**：

| 角色与驱动模式 | 蓄力起手时长 | 到达 `HIT` 时长 | 斩击结束 `END` | **收招回位时长（占比）** | 路径速度比（起手段 / 收招段） | 关键代码缺陷 |
|---|---:|---:|---:|---:|---:|---|
| `yingxiao` (`MeleeStrokeTiming.path`) | `0.34~0.59t` (`17~30ms`) | `0.85t` | `2.35t` | **`34.94t` (`93.7%`)** | **`36.5× ~ 64.4×`** | `path()` 对 `age ∈ [0, 0.85]` 线性拉满 `0 → 0.40/0.47/0.49`，起手瞬间闪过；`[2.35, 37.29]` 慢漂回待机（`NightDaggerMotion.java:59-81`） |
| `tianxing` (`MeleeStrokeTiming.path`) | `0.42t` (`21ms`) | `0.85t` | `2.35t` | **`34.94t` (`93.7%`)** | **`47.6×`** | 仅 `21ms` 蓄力即冲过 `HIT`，随后 `1.75s` 慢漂回位（`ThunderSwordMotion.java:44-63`） |
| `fenjue` (`MeleeStrokeTiming.path`) | `0.40t` (`20ms`) | `0.85t` | `2.35t` | **`34.94t` (`93.7%`)** | **`49.3×`** | Hermite 样条被 `MeleeStrokeTiming.path()` 极端时间扭曲，前 `2.35t` 压缩进瞬闪，后 `34.94t` 拖沓（`FlameDaoMotion.java:55-76`） |
| `shuo` (`MeleeStrokeTiming.path`) | `0.00~0.54t` (`0~27ms`) | `0.85t` | `2.35t` | **`34.94t` (`93.7%`)** | **`24.0× ~ 64.2×`** | 拔刀斩 `0.54t` 内完成抽刃，随后长达 `1.75s` 慢速漂回 `READY`（`ShuoMotion.java:57-85`） |
| `sulan`（直算 `age`） | `0.36t` (`18ms`) | `0.85t` | `2.65t` | **`34.64t` (`92.9%`)** | — | 起手 `CUT_FROM=0.36t`（`18ms`，约 1 帧！），`age > 2.65t` 后长达 `34.64t` 按 `smoothstep` 缓慢回正（`WindHandMotion.java:48,165-174`） |
| `lingyuan`（直算 `age`） | `0.00t`（贝塞尔融合） | `0.85t` | `2.35t` | **`34.94t` (`93.7%`)** | — | `peak=0.85t`, `drift=2.35t` 硬写进 `Move`，收招占 `93.7%`（`LingyuanHandMotion.java:95-96,131-143`） |
| `chixiao`（直算 `age`） | `0.36t` (`18ms`) | `0.85t` | `2.80t` | **`34.49t` (`92.5%`)** | — | 蓄力仅 `0.36t`，斩完 `2.80t` 后占 `92.5%` 慢漂（`WarSpiritHandMotion.java:63-77,93-94`） |
| `keheng`（直算 `age`） | `0.33t` (`16.5ms`) | `0.85t` | `2.00t` | **`35.29t` (`94.6%`)** | — | 全场最快起手（`16.5ms`）+ 最长收招占比（`94.6%`）（`KehengView.java:39-47`） |
| `rivet`（直算 `age`） | `0.38t` (`19ms`) | `0.85t` | `2.90t` | **`34.39t` (`92.2%`)** | — | 重锤高举蓄力仅 `19ms`，完全失去重武器的蓄势重量感，下砸后拖曳 `34.39t` 慢回（`RivetWeaponMotion.java:29-38`） |

**核心成因**：
1. `MeleeStrokeTiming.HIT = 0.85` 试图让第一人称挥砍与原版“按下左键瞬间即造成伤害”的即时命中严格对齐，于是把“蓄力 + 挥出至接触”全部挤进不到 1 个逻辑 tick（`42.5 ms`）里，且 `MeleeStrokeTiming.path()` 使用**分段线性重映射**，既没有足够视觉帧展现蓄力反方向预备（anticipation），也在 `HIT` 处缺乏微顿/冲击停顿（hitstop）与过冲回弹（overshoot）。
2. 同时为了满足“单击停手后不瞬移回位、连击可顺滑衔接”的目标，`recovery(cadence)` 把整段返回时长硬绑死在 `cadence * 1.20 + 3`（覆盖整段普攻冷却 + 20% 冗余），且在 `[END_OF_CUT, duration]` 全程均匀拉长回位曲线，造成“前 0.1 秒瞬闪、后 1.7 秒匀速慢漂”的严重节奏失衡。

---

## 三、Minecraft 26.3 / Fabric 渲染接入点与平台约束

以下结论均直接核对本仓库 Mixin / 源码及 Loom 缓存中的 Minecraft 26.3 反编译字节码（`minecraft-merged-deobf-26.3.jar`）。

### 3.1 第一人称手持物品渲染管线（`HandRenderContext` & Mixins）

在 Minecraft 26.3 中，第一人称手部渲染链路如下（自上而下）：

1. **`GameRenderer.renderItemInHand(CameraRenderState, PlayerRenderState, GpuTextureView)`**：
   - 使用独立投影 `hudProjection`，其垂直 FOV 由 **`Camera.calculateHudFov(float)` 硬编码为 `70.0f`**（仅受死亡/水下畸变影响，**不受玩家世界 FOV 设置变化影响**）。
   - 新建 `PoseStack`，先乘 `cameraRenderState.viewRotationMatrix.invert()` 并向 `RenderSystem.getModelViewStack()` 压入 `viewRotationMatrix`（使手部变换落在相机局部坐标系，同时保留世界光照方向）。
   - 依次调用 `bobHurt(cameraRenderState, poseStack)` 与（若开启 `options.bobView`）`bobView(cameraRenderState, poseStack)`。
   - 调用 `firstPersonHandsAndItemsRenderer.submitHandsWithItems(partialTick, poseStack, handAndScreenSubmitNodeStorage, playerRenderState, firstPersonHandsAndItems)`。
2. **`FirstPersonHandsAndItemsRenderer.submitHandsWithItems(...)`**：
   - 在遍历主副手之前，**已先对 `PoseStack` 施加了原版视角转动滞后（sway）**：
     - `poseStack.rotateDegrees(Axis.XP, (viewXRot - xBob) * 0.1f)`
     - `poseStack.rotateDegrees(Axis.YP, (viewYRot - yBob) * 0.1f)`
   - 随后按 `handRenderSelection` 分别调用 `submitArmWithItem(..., MAIN_HAND, ...)` 与 `submitArmWithItem(..., OFF_HAND, ...)`。
3. **本仓库现有手部 Mixin 与平台封装**：
   - **`MyriadHandRendererMixin`**（`src/main/java/com/myriad/mixin/MyriadHandRendererMixin.java:18-35`）：
     - `renderPlayerHand` `HEAD`：无手臂版下直接 `ci.cancel()`，隐藏原版空手与地图皮肤手；
     - `submitArmWithItem` `HEAD`：在非望远镜态（`!hands.isScoping`）构造 `HandRenderContext(hand, item, pose, collector, light, partial, height, swing)`（`src/main/java/com/myriad/client/platform/HandRenderContext.java:9-35`），交给 `MyriadClient.onRenderHand(event)`，若 `event.isCanceled()` 则取消原版物品绘制。
   - **`ClientItemRenderer.submit`**（`src/main/java/com/myriad/client/platform/ClientItemRenderer.java:15-20`）：通过 `ItemModelResolver.updateForLiving(state, item, ItemDisplayContext.NONE, owner)` + `state.submit(pose, collector, light, NO_OVERLAY, 0)` 提交 26.3 新版 `SubmitNodeCollector` 管线。
   - **关键约束**：
     - 进入 `MyriadClient.onRenderHand` 时，`PoseStack` **已经携带** `bobView`（受用户设置开关影响）和 `xBob/yBob * 0.1f` 原版视角滞后，以及 `MyriadClient.java:345` 的近战缓冲预备平移；若未来底座引入自定义弹簧/摆动惯性，必须明确决定是**叠加**在原版 `xBob/yBob` 之上，还是在 `submitHandsWithItems` 处抑制重复摆动。
     - `HandRenderContext.holdEquipped()` 仅清零上下文对象上的 `equipProgress` 字段，只有主动读取 `event.getEquipProgress()` 的 View 才会响应。
     - 第三人称独立挂接点与第一人称隔离：`ShuoHeldLightMixin`（`ItemInHandLayer.submitArmWithItem`，`src/main/java/com/myriad/mixin/ShuoHeldLightMixin.java:12-26`）与 `YikeArmPoseMixin`（`HumanoidModel.setupAnim`，`src/main/java/com/myriad/mixin/YikeArmPoseMixin.java:7-16`）仅作用于第三人称角色模型，不干扰第一人称手部。

### 3.2 第一人称刀光与局域特效渲染（`AttackTrailRenderer` / `MeleeBladeLight` / `AttackTrailMesh`）

| 组件 | 文件路径与行号 | 工作方式与平台特性 | 当前限制 |
|---|---|---|---|
| `AttackTrailMesh` | `src/main/java/com/myriad/client/firstperson/AttackTrailMesh.java:11-62` | 纯相机空间绝对时间采样（`SAMPLES = 12`，`MAX_QUADS = 80`，`LIFE = 2` tick），无跨帧历史状态；每片同时生成主拖尾面、外缘高亮面与面向相机 `+Z` 的刀尖细线，近裁剪保护 `z <= -0.055` | 固定 `LIFE = 2` tick 与 `SAMPLES = 12` 对短促瞬闪（`0.85~2.35t`）够用；若未来拉长重武器挥弧或加顿帧，需按动作时长/速度自适应采样，否则会在顿帧处坍缩或拉断 |
| `MeleeBladeLight` | `src/main/java/com/myriad/client/firstperson/MeleeBladeLight.java:14-59` | 复用与武器渲染**完全一致的矩阵 lambda**（`DoubleFunction<Matrix4f> weapon`）反采过去时刻的 `root/tip`，确保刀光严丝合缝贴合当前帧刃轨；另提供 `needle`（刻衡刺线）与 `sparks`（铆钉火花） | 仅凌渊、赤霄、朔、刻衡、铆钉接入；夜影/天刑/焚诀/素岚各自另写了 `NightBasicTrail` / `ThunderBasicTrail` / `FlameBasicTrail` / `WindHandVfx`（底层均复用 `AttackTrailMesh`） |
| `AttackTrailRenderer` | `src/main/java/com/myriad/client/firstperson/AttackTrailRenderer.java:13-59` | 按 `q.material()` 映射到 `textures/particle/vfx/*.png` 图集，Z 深度排序后通过 26.3 `SubmitNodeCollector.submitCustomGeometry(stack, RenderTypes.entityTranslucent(texture), ...)` 全亮提交（`light = 0xF000F0`） | 使用 `RenderTypes.entityTranslucent`，**无独立加色混合（additive）通道**；深度测试与手部共用 `handAndScreenSubmitNodeStorage`，提交顺序必须在武器之后 |
| `ShuoBladeLight` | `src/main/java/com/myriad/client/firstperson/ShuoBladeLight.java:9-59` | 启动时直接解析 `shuo_sword.json` 中 `sword_blade_edge_*` 元素顶点提取真实曲刃轮廓，结合 `exposure(f)` 保证未出鞘部分不发光 | 仅限朔专用；但“从模型资产提取刃缘锚点”的思路可作为未来底座参考 |

### 3.3 第一人称相机接入点与“极克制相机微联动”的安全挂接方案

#### 现有相机相关代码与 Mixin
1. **`MyriadCameraMixin`**（`src/main/java/com/myriad/mixin/MyriadCameraMixin.java:15-28`）：
   - `@ModifyArgs` on `Camera.alignWithEntity` → `Camera.setRotation(FF)V`：经 `CameraAngles`（`CameraAngles.java:4-11`）供 `ZhoutianChannelView`（墨尘大招切第三人称侧视）与 `HanzeFrostView`（寒泽大招拉高视距）使用。
   - `@Inject(method="calculateFov", at=@At("RETURN"), cancellable=true)`：调用 `MyriadClient.computeFov`（`MyriadClient.java:540-552`），仅在冲刺 `DashController.isDashing()` 时平滑放大世界 FOV 至 `1.08×`（每帧逼近 25%，**注意：不影响 `calculateHudFov` 的 70° 手部 FOV**）。
2. **原版受击抖动禁用**：`MyriadLocalPlayerMixin`（`MyriadLocalPlayerMixin.java:15-16` -> `MyriadClient.afterPlayerTick:428`）与 `MyriadMinecraftClientMixin.myriad$suppressHurtCamera`（`MyriadMinecraftClientMixin.java:46-47` -> `MyriadClient.beforeRenderFrame:435-439`）在每 tick 末尾与每帧渲染前强行清零 `local.hurtTime = 0`，从而让 `GameRenderer.bobHurt` 跳过原版受击倾斜。

#### 若未来启用“极克制相机微联动”的平台接入评估与安全约束

核对 MC 26.3 `Camera` 与 `GameRenderer` 字节码后，**三种候选挂接点的安全性对比如下**：

| 候选挂接点 | 可调自由度 | 是否干扰准星/`pick()` 命中 | 是否受用户 `bobView` 开关影响 | 结论与约束 |
|---|---|---|---|---|
| **方案 A（推荐）：`GameRenderer.bobHurt(CameraRenderState, PoseStack)` `TAIL`（或 `renderLevel` 中 `bobHurt` 调用之后）** | **Pitch / Yaw / Roll（Z 轴微倾）+ 微位移** | **完全不干扰**：仅作用于渲染 `PoseStack`，不改动 `LocalPlayer.xRot/yRot`、`Camera.forwards`、视锥裁剪或 `Minecraft.pick()` 射线 | **不受影响**：`bobHurt` 在 `renderLevel` 与 `renderItemInHand` 中均无条件调用（不检查 `options.bobView`） | **最安全、最干净的唯一首选入口**。注意区分 `renderLevel` 与 `renderItemInHand` 两次调用（若只在 `renderLevel` 注入则世界微震而武器由自身运动学主导；若在 `bobHurt` 内注入则世界与手部同步微倾，须避免双重叠加或反相） |
| **方案 B（辅助）：`MyriadCameraMixin.myriad$dashFov`（`Camera.calculateFov` `RETURN`）** | 仅世界 FOV 标量（如 `±0.5°~1.5°` 脉冲） | **不干扰** | **不受影响** | 可安全复用现有钩子叠加极轻微的命中/蓄力呼吸感 FOV 脉冲（与 `1.08×` 冲刺倍率乘性叠合），但须保留 `hudFov = 70°` 稳定以防武器穿模 |
| **方案 C（不推荐）：扩展 `MyriadCameraMixin`（`Camera.setRotation(FF)V`）** | 仅 Yaw / Pitch（26.3 字节码中 `Quaternionf.rotationYXZ(..., 0.0f)` **写死 Roll = 0**） | 不改 `LocalPlayer` 朝向，但会改写 `Camera.forwards/up/left` 与裁剪视锥、音频监听朝向 | 不受影响 | **不支持 Roll 轴**，且改动逻辑相机基向量容易与第三人称切换（`ZhoutianChannelView`、`HanzeFrostView`、`RespawnSpectatorView`）及遮挡剔除产生副作用，不建议用于战斗微联动 |

**必须同时遵守的竞技安全硬约束**：
1. **严禁修改 `LocalPlayer.xRot / yRot`**：任何微联动都绝不可写回实体朝向，否则会污染准星瞄准、服务端移动/攻击包与 `MeleeAssistInput` 拾取。
2. **严禁遮挡或偏移准星中心**：HUD 准星固定于屏幕几何中心，世界微倾幅度必须控制在极克制区间（通常 Pitch/Yaw $\le 0.5^\circ$、Roll $\le 1.0^\circ$）并快速临界阻尼回零，且在**玩家关闭视角摇晃或新增“微联动开关”时可完全关闭**（遵循无障碍/晕 3D 保护）。
3. **仅限本人存活第一人称战斗态**：复用 `FirstPersonArmVisibility.hidePlayerArms()` 同等级门控（非旁观、非 `RespawnSpectatorView`、非 `ZhoutianChannelView`/`HanzeFrostView` 第三人称大招镜头、非 `isScoping`、非 GUI 打开态）。

---

## 四、面向后续架构设计的关键基线结论

1. **全 15 角色统一底座的迁移面很干净**：虽然现有 13 个 `*View` 内部分裂严重（4 种镜像、3 种旋转范式、2 套 `equipProgress` 约定），但全部收口于 `MyriadClient.renderWeaponHand`（`MyriadClient.java:352-365`）与 `MyriadHandRendererMixin`，不污染第三人称与服务端逻辑。
2. **构图整改优先级一目了然**：当前 12/15 角色处于“立牌待机”（纵深角 $\le 20.5^\circ$、`gripNDC.x \le 0.46`），仅有 `keheng`（`+73.6°`）和 `yike`（`+64.8°`）具备自然纵深感；其中 `rivet`（`-11.9°`）、`luoxia`（`0.0°`）、`fenjue`（`0.38×` 位移压缩 + `16.5°` 立牌）和 `sulan`/`lingyuan`/`tianxing`/`chixiao`/`shuo` 的长兵竖直立牌是最亟待重塑的一批。
3. **近战手感重塑的核心瓶颈在 `MeleeStrokeTiming` 与“掌心自转”**：只要仍把起手压进 `0.85` tick（`42.5 ms`）、收招拉成 `93%` 时长的慢漂，且旋转支点停留在无手臂后的裸掌心，任何单角色调参都无法根治“瞬闪 + 飘浮自转 + 拖沓回位”的顽疾。
4. **左主手与副手持物（火把/地图）的既有契约必须在新底座中保底**：所有 15 角色当前均已保证副手持非空物品时返回 `false` 保留原版副手渲染，其中 `ShuoView` 还额外实现了 `occupiedOffset` 避让位移；新体系必须将“正行列式手性镜像 + 副手占用避让 + `equipProgress` 一致处理”上升为底座不变量。
