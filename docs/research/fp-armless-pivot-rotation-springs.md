# 第一人称无手/简化手部运动学调研：隐含枢轴、旋转表达与微动态叠层

> **对应工单**：[MYRIAD #75](https://github.com/xOyMoveO/MYRIAD/issues/75)（Wayfinder 地图 [#72](https://github.com/xOyMoveO/MYRIAD/issues/72)《第一人称无手姿势与动画体系规划》）  
> **目标运行栈**：Minecraft 26.3 / Fabric / `PoseStack` + JOML (`org.joml.Quaternionf`, `org.joml.Matrix4f`)  
> **调研来源原则**：全部结论均追溯至开源实现源码、数学原始论文或引擎官方文档。

---

## 核心选型摘要

| 议题 | 推荐底座方案 | 关键避免的反模式 |
|---|---|---|
| **隐含枢轴（Virtual Pivot）** | 相机空间 **两级隐含枢轴**（视锥外体侧**隐含肩枢轴**驱动挥砍大弧 + 握柄处**隐含腕枢轴**驱动甩腕/翻刃），底层经 `PoseStack#rotateAround`（矩阵共轭 $T(\mathbf{p}) R(q) T(-\mathbf{p})$）实现，辅以极端俯仰角补偿 | 直接绕模型原点自转（浮空陀螺感）；或强绑第三人称真实身体骨骼引入转身滞后、抬头穿模与视野遮挡 |
| **旋转表达与左右手镜像** | **分段架构**：大跨度斜劈/反手挥砍以**显式挥砍平面正交基 + 绕法向轴角扫掠 $\theta(u)$ + 独立翻刃角 $\phi(u)$**生成关键姿态，内部统一转为单位四元数并用带半球对齐的 `SLERP`/`NLERP` 混合；左右手镜像采用 $SO(3)$ 相似变换 $(w, +x, -y, -z)$ 与极向量 $(-p_x, p_y, p_z)$ | 欧拉角插值（万向锁、歪刃、轴序打架）；对 $>180^\circ$ 大弧直接端点 `slerp`（最短路径反杀）；Frenet 标架在直刺/拐点 $180^\circ$ 翻刃；`PoseStack.scale(-1, 1, 1)` 负缩放镜像（面绕序与光照反转） |
| **微动态与主动攻击叠层** | **五层正交管线**（基准持握 → 宏观惯性 → 主动攻击 → 命中脉冲 → 惯性化过渡），弹簧采用**帧率无关闭式解析解**（Holden / Juckett），并按动作阶段对步晃/转头滞后/呼吸施加**分通道平滑衰减掩码** | 显式 `dt * 30` 欧拉弹簧（卡顿帧发散）；把步晃、转头、挥砍混入同一未掩码累加器；在攻击帧不做微动态衰减导致准星区干扰与轨迹发飘 |

---

## 一、无手隐含枢轴（Virtual Pivot）运动学模型

### 1.1 为什么无手武器容易“像在原地空中自转”

在第一人称视图中，人脑并不是只靠看见手臂来推断发力支点，而是通过**运动学透视线索**判断枢轴位置：

- 若直接对武器局部原点 $(0, 0, 0)$ 施加纯旋转 $R(q)$，刀尖向左上移动时刀柄同步向右下反向等幅移动，角速度中心落在画面内部，视觉上呈现“失重悬浮物体绕质心自转”，完全没有挥砍重量感。
- 真实持械挥砍时，手臂相当于一根位于画面外下侧的刚性/半刚性连杆：武器握柄本身沿一个大半径圆弧做**显著平动 + 同步切向转动**，刀尖的线速度远高于刀柄，且进出画面的边缘轨迹指向画面外的肩部或肘部方向。

### 1.2 开源方案一手源码拆解与对比

对体素/Minecraft 生态及传统 FPS 方案的源码核查表明，业界处理第一人称支点存在三条清晰路线：

| 方案与一手源码 | 枢轴与坐标系实现 | 优点 | 对万象无手体系的局限 |
|---|---|---|---|
| **Better Combat + PlayerAnimator / PlayerAnimationLib**<br>([`AttackAnimationStack.java`](https://github.com/ZsoltMolnarrr/BetterCombat/blob/26.2/common/src/main/java/net/bettercombat/client/animation/AttackAnimationStack.java), [`FirstPersonMode.java`](https://github.com/KosmX/minecraftPlayerAnimator/blob/1.21/coreLib/src/main/java/dev/kosmx/playerAnim/api/firstPerson/FirstPersonMode.java), [`FirstPersonConfiguration.java`](https://github.com/KosmX/minecraftPlayerAnimator/blob/1.21/coreLib/src/main/java/dev/kosmx/playerAnim/api/firstPerson/FirstPersonConfiguration.java)) | `FirstPersonMode.THIRD_PERSON_MODEL` 复用第三人称玩家骨骼链，而 `FirstPersonConfiguration` 默认 `showRightArm = false, showLeftArm = false, showRightItem = true, showLeftItem = true`——即**隐藏手臂但保留不可见肩/躯干骨骼的父级变换**。同时在 `AttackAnimationStack#createAttackAdjustment` 中检测 `data.isFirstPersonPass()`，对 `body` 叠加 `rotationX += pitch`，并在仰视（`pitch < 0`）时额外注入 `offsetY += |sin(pitch)| * 0.5`、`offsetZ -= |sin(pitch)|` 防止武器掉出视野。 | 即使无手也有极自然的肩部大半径弧度；1P 与 3P 动作天然同源。 | 强耦合 3P 骨骼尺寸与朝向；仰俯角必须手写穿模补丁；难以针对竞技 1P 构图做屏幕级“作弊”留白。 |
| **First-Person Model**<br>([`LogicHandler.java`](https://github.com/tr7zw/FirstPersonModel/blob/main/src/main/java/dev/tr7zw/firstperson/LogicHandler.java)) | 直接在第一人称渲染真实 3P 玩家模型，为避免相机卡进头部/胸腔，`updatePositionOffset` 必须沿 `yBodyRot` 将身体整体后移（站立 `0.25f`，潜行/游泳/骑乘各需独立常量），并在 `calculateBodyRot` 中用 ±50° 阈值拖曳身体朝向。 | 完整身体沉浸感。 | 身体滞后、视角遮挡、状态边缘修补繁重，违背万象“无手清爽视野 + 竞技可读性优先”的目标。 |
| **Hold My Items (5.1.1 / Fabric 26.2)**<br>([`HeldItemRendererMixin.java`](https://github.com/ThinkofRain1213/HMI-26.2/blob/master/mod/src/main/java/com/holdmylua/source/mixin/render/HeldItemRendererMixin.java), [`M.java`](https://github.com/ThinkofRain1213/HMI-26.2/blob/master/mod/src/main/java/com/holdmylua/source/scripting/script_wrappers/M.java), [`item_pose.lua`](https://github.com/ThinkofRain1213/HMI-26.2/blob/master/mod/src/main/resources/resourcepacks/pack_test/assets/minecraft/holdmyitems/item_pose.lua)) | 留在相机空间 `ItemInHandRenderer` 中，通过 `M:rotateX/Y/Z(mat, deg, ox, oy, oz)`（底层调用 `PoseStack#rotateAround`）显式指定离轴枢轴：如工具/斧锤在握柄外下方 `(0.3 * l, -0.3/-0.4, 0)` 与 `(0, -0.2, 0)` 旋转，而提灯/吊牌把枢轴移至上方 `(0, 0.4, 0)` / `(0, 0.55, 0)` 模拟悬吊摆动。 | 相机空间完全可控、零 3P 身体遮挡、能按武器品类灵活换支点。 | HMI 的脚本把手臂/物品写在同一欧拉流水线里，且缺乏统一的肩-腕二级层级抽象。 |
| **Overwatch 第一人称解耦哲学**<br>(Matthew Boehm, GDC 2017 *The First Person Animation of Overwatch*) | 明确将第一人称视图模型与第三人称解剖结构解耦，允许在相机空间中为了画面可读性、剪影节奏与打击感“合理作弊”支点与缩放。 | 最高级别的镜头可读性与竞技响应速度。 | 需要为 1P 独立维护一套干净的运动学参数骨架。 |

### 1.3 推荐方案：相机空间两级隐含枢轴模型

综合 *Better Combat* 的“隐形肩关节杠杆”直觉与 *Hold My Items* / *Overwatch* 的“相机空间解耦控制”，万象应采用**相机空间两级隐含枢轴（Virtual Shoulder → Virtual Wrist → Weapon Grip）**。

#### 数学定义（严格对齐 JOML 与 Minecraft `PoseStack`）

JOML 的 [`Matrix4f#rotateAround(Quaternionfc quat, float ox, float oy, float oz, Matrix4f dest)`](https://github.com/JOML-CI/JOML/blob/main/src/main/java/org/joml/Matrix4f.java#L11844-L11874)（即 Minecraft `PoseStack#rotateAround` 的底层实现）执行的是标准的枢轴共轭变换：

$$M_{\text{pivot}}(\mathbf{p}, q) = T(\mathbf{p}) \cdot R(q) \cdot T(-\mathbf{p})$$

对于无手武器，将局部变换组织为嵌套的两级连杆（相机空间采用 Minecraft 右手坐标系：$+X$ 向右，$+Y$ 向上，$-Z$ 朝向前方视线）：

$$M_{\text{weapon}} = M_{\text{L0+L1}} \cdot \underbrace{T(\mathbf{p}_{\text{sh}}) \cdot R(q_{\text{sh}}) \cdot T(-\mathbf{p}_{\text{sh}})}_{\text{隐含肩枢轴（画面外体侧）}} \cdot T(\Delta \mathbf{t}_{\text{reach}}) \cdot \underbrace{T(\mathbf{p}_{\text{wr}}) \cdot R(q_{\text{wr}}) \cdot T(-\mathbf{p}_{\text{wr}})}_{\text{隐含腕枢轴（握柄抓握点）}} \cdot M_{\text{grip}}$$

各环节物理语义如下：

1. **隐含肩枢轴 $\mathbf{p}_{\text{sh}}$（画外支点）**：
   - 位于当前相机空间中**视锥下外侧、玩家肩窝大致对应处**（例如右主手参考区间 $x \in [+0.40, +0.60],\; y \in [-0.35, -0.55],\; z \in [-0.10, +0.15]$，具体按武器臂展配置）。
   - 因为它位于武器握柄之外形成半径 $|\mathbf{p}_{\text{grip}} - \mathbf{p}_{\text{sh}}| \approx 0.55 \sim 0.85$ 单位的虚拟连杆，任何施加在 $q_{\text{sh}}$ 上的旋转都会天然带动武器沿圆弧扫过屏幕，**无需手写复杂的 $(x, y, z)$ 位移曲线去“假装画弧”**。
2. **前伸/收臂平动 $\Delta \mathbf{t}_{\text{reach}}$（伸肘自由度）**：
   - 表达肩到腕的臂长伸缩（用于直刺突进、蓄力后拉、重劈探身、受击后退），对应真实人体肘关节屈伸产生的径向位移。
3. **隐含腕枢轴 $\mathbf{p}_{\text{wr}}$（握柄抓握点）**：
   - 锚定在武器模型的真实**手握点**（而非方块/包围盒中心 $(0.5, 0.5, 0.5)$）。
   - 负责蓄力亮刃、挥砍过中线时的**甩腕鞭打（wrist snap）**、反手翻刃以及命中瞬间的弹性微震。
4. **俯仰角边界补偿（Pitch Tuck）**：
   - 借鉴 Better Combat [`AttackAnimationStack.java`](https://github.com/ZsoltMolnarrr/BetterCombat/blob/26.2/common/src/main/java/net/bettercombat/client/animation/AttackAnimationStack.java#L80-L89) 与 Source SDK [`baseviewmodel_shared.cpp`](https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/shared/baseviewmodel_shared.cpp#L482-L485) 的思路：当玩家大幅抬头或低头时，对 $\mathbf{p}_{\text{sh}}$ 做小比例 $(y, z)$ 收拢，防止仰视挥砍时武器穿透近裁剪面或沉出底边。

#### 不同兵器品类的隐含枢轴配置策略

| 兵器类型 | 隐含肩枢轴特征 | 隐含腕枢轴 / 附加自由度 | 视觉支点效果 |
|---|---|---|---|
| **单手刀剑（如居合/快刃）** | 标准体侧外下方，中等杠杆半径 | 握柄底部；挥砍中后段叠加甩腕加速与翻刃 | 切入干脆、刃口轨迹锐利、收刀利落 |
| **重斧 / 战锤（如铆钉）** | 外扩且更低（更大杠杆半径） | 握柄中下段；前摇肩先动、腕滞后（drag），击中后腕部回弹 | 沉重惯性、拖曳蓄力感明显 |
| **长枪 / 长柄武器** | 后手隐含支点在右后下方，叠加显式前伸轴 $\Delta \mathbf{t}_{\text{reach}}$ | 握柄中段作为滑动导向点（前手虚枢轴），突刺几乎零肩转、纯沿导向轴推进 | 直线贯通感强，避免“挥棍子”错觉 |
| **法杖 / 仪杖 / 悬吊法器** | 靠近体侧，低幅稳重 | 法器可按 HMI 提灯逻辑将次级摆动支点设在握持端或顶冠挂点 | 仪态稳定，端头随动有物理垂坠感 |

---

## 二、旋转表达对比与左右主手镜像工程陷阱

### 2.1 四种旋转表达的全面对比

| 表达方式 | 跨屏斜劈表现 | 反手挥砍 / $>180^\circ$ 大弧 | 多层混合 / 叠层 | 工程主要缺陷与风险 |
|---|---|---|---|---|
| **欧拉角插值**（Euler XYZ / YXZ） | **差**。三轴线性插值在斜平面上耦合扭曲，刀刃中途“歪刃/画香蕉弧”；俯仰接近 $\pm 90^\circ$ 触发万向锁（Gimbal Lock）。Godot 官方文档明确建议 [*Say no to Euler angles*](https://docs.godotengine.org/en/stable/tutorials/3d/using_3d_transforms.html)。 | **极差**。反手需跨过 $\pm 180^\circ$ 角度边界，极易轴序打架或绕远圈翻转。 | 不可交换，顺序敏感。 | 仅适合在编辑器/配置文件中作为**单帧静态朝向的 authoring 输入**（随后立即转四元数），绝不应用于运行时跨姿态插值。 |
| **单位四元数 `SLERP`**<br>([Shoemake 1985](https://doi.org/10.1145/325165.325242); [JOML `Quaternionf#slerp`](https://github.com/JOML-CI/JOML/blob/main/src/main/java/org/joml/Quaternionf.java#L2489-L2509)) | **好**。沿 $S^3$ 大圆测地线等角速度旋转，无万向锁、无歪轴。 | **有条件陷阱**：内建双覆盖最短路径（`cosom < 0` 取反），导致单段旋转**恒被强制 $\le 180^\circ$**。超过 $180^\circ$ 的大挥砍会反向抄近路！ | **非交换（non-commutative）**，多轨道混合依赖顺序；含 `atan2`/`sin` 三角开销（单武器可忽略）。 | 1. 端点跨度必须 $< 150^\circ$（否则拆分中间关键帧）；<br>2. 手写时漏掉 `dot < 0` 会出现 360° 鬼畜翻转。 |
| **单位四元数 `NLERP`**<br>([Blow 2004](http://number-none.com/product/Understanding%20Slerp%2C%20Then%20Not%20Using%20It/); [JOML `Quaternionf#nlerp`](https://github.com/JOML-CI/JOML/blob/main/src/main/java/org/joml/Quaternionf.java#L2645-L2659)) | **好（短跨度）**。路径与 SLERP 完全一致（同一测地线），仅角速度在两端略慢、中段略快；相邻帧间隔 $\le 60^\circ \sim 90^\circ$ 时人眼不可分辨。 | 同 `SLERP`，单段受限于 $< 180^\circ$ 最短路径。 | **满足交换律且极快**（纯 FMA + `invsqrt`），最适合多通道权重混合与程序化微动态叠层。 | 大跨度（$>120^\circ$）双帧插值会引入肉眼可见的中间加速畸变，不宜直接替代整段挥砍轨迹。 |
| **显式轴角 / 挥砍平面正交基**<br>(Hanson & Ma 1995 / [Wang et al. 2008](https://doi.org/10.1145/1330511.1330513)) | **最佳（主动攻击轨迹）**。用挥砍平面法向 $\hat{\mathbf{n}}$ + 显式角进度 $\theta(u)$ + 独立刃面角 $\phi_{\text{edge}}(u)$ 参数化，刃口严格贴合斜劈平面。 | **天然免疫 $>180^\circ$ 反转**。$\theta(u)$ 可以平滑从 $-110^\circ$ 扫到 $+130^\circ$（跨度 $240^\circ$），反手只需翻转 $\hat{\mathbf{n}}$ 或设置 $\phi_{\text{edge}} = \pi$。 | 产出结果转为单位四元数后，即可与弹簧/惯性层无缝复合。 | 切忌使用经典 Frenet-Serret 标架（见下节陷阱 3）；需维护参考平面法向或旋转最小化标架（RMF）。 |

### 2.2 实践中的五大工程陷阱与规避准则

#### 陷阱 1：四元数双覆盖（Double Cover）导致的 360° 暴走
$q$ 与 $-q$ 表示完全相同的 3D 旋转，但在 4D 超球面上互为对跖点。在任何手工插值、弹簧误差求解或惯性化残差计算中，若未先检查 $q_0 \cdot q_1 < 0$ 并对其中一个取反，插值会绕超球面走 $>180^\circ$ 的长路，表现为武器突然原地爆转一圈。  
- **验证事实**：JOML 的 [`Quaternionf#slerp`](https://github.com/JOML-CI/JOML/blob/main/src/main/java/org/joml/Quaternionf.java#L2490-L2503) 和 [`Quaternionf#nlerp`](https://github.com/JOML-CI/JOML/blob/main/src/main/java/org/joml/Quaternionf.java#L2646-L2648) 均已内置 `scale1 = (cosom >= 0.0f) ? ... : -...`；但自定义四元数差分（$q_{\text{err}} = q_{\text{cur}} \cdot q_{\text{target}}^{-1}$）提取旋转向量（rotvec）给弹簧/惯性化时，**必须先规范化为 $q_{\text{err}}.w \ge 0$**（若 $w < 0$ 则整体取反），否则弹簧会朝反方向弹回 $360^\circ - \theta$。

#### 陷阱 2：$>180^\circ$ 跨屏大斜劈 / 反手连斩的“最短路径反杀”
即使正确做了 `dot < 0` 对齐，两点间 `SLERP`/`NLERP` 永远选择 $\le 180^\circ$ 的最短测地线。如果一次大风车斜劈或反手蓄力回斩的总扫掠角达到 $185^\circ$，只给起点和终点两个四元数关键帧，武器会瞬间改走另一侧 $175^\circ$ 的路径（从视野背后或错误象限穿出）。  
- **工程解法**：
  1. **首选**：将主动挥砍拆分为**“挥砍平面朝向 $q_{\text{plane}}$ × 绕平面法向扫掠角 `AxisAngle(n, theta(u))` × 腕部翻刃角 `AxisAngle(bladeAxis, phi(u))`”**。扫掠角 $\theta(u)$ 是标量，可任意超过 $180^\circ$，且设计师的缓动曲线（蓄力慢→过中线爆速→收刀减速）精确作用于弧面角速度。
  2. **备选（关键帧轨道）**：任何相邻关键帧夹角严格控制在 $\le 110^\circ$ 以内（至少提供 Windup → Mid-Arc → Follow-through 三帧）。

#### 陷阱 3：用 Frenet-Serret 标架沿路径定向导致的 180° 暴力翻刃
若先生成一条 3D 武器路径曲线再试图用 Frenet-Serret 标架（切向 $T$、曲率法向 $N$、副法向 $B = T \times N$）决定刀刃朝向，当路径出现**直线段**（如长枪直刺，曲率 $\kappa = 0$）或**S 形拐点**（如连招变向，曲率反向）时，$N$ 与 $B$ 会退化或瞬间翻转 $180^\circ$，导致画面里武器突然翻面。  
- **一手文献依据**：Hanson & Ma (1995, Indiana Univ. TR-425) 与 Wang, Jüttler, Zheng & Liu (2008, *ACM TOG*, [DOI: 10.1145/1330511.1330513](https://doi.org/10.1145/1330511.1330513)) 证明，**旋转最小化标架（Rotation Minimizing Frame / Bishop 平行输运标架，Double Reflection 算法）**在曲率为零和拐点处完全连续无翻转。
- **第一人称近战的极简特化**：单次挥砍本身定义在一个近似平面或缓变锥面上，因此不必每帧求解一般空间曲线 RMF——只需直接定义**挥砍平面法向 $\hat{\mathbf{n}}_{\text{plane}}$**（例如水平斩为 $(0, 1, 0)$，45° 右上向左下斜劈为倾斜单位向量），由隐含肩枢轴指向握柄的径向单位向量 $\hat{\mathbf{r}}(u)$ 与平面法向叉乘得到弧面切向 $\hat{\mathbf{t}}(u) = \hat{\mathbf{n}}_{\text{plane}} \times \hat{\mathbf{r}}(u)$，直接拼出严格右手正交基 $[\hat{\mathbf{r}}, \hat{\mathbf{t}}, \hat{\mathbf{n}}_{\text{plane}}]$，再乘上绕纵轴的翻刃角 $\phi_{\text{edge}}(u)$（正手 $0^\circ$、反手 $180^\circ$ 或斜切角）。直线突刺时改用目标瞄准方向与固定参考上向量（如 `Quaternionf#lookAlong`）即可彻底杜绝奇点。

#### 陷阱 4：乘法顺序（局部右乘 vs 相机空间左乘）混淆
JOML 的 `Quaternionf#mul(other)` 是右乘（应用在当前局部坐标轴上），而 `premul(other)`（或 `rotateLocalX/Y/Z`）是左乘（应用在父级/相机坐标轴上）。
- **硬规则**：
  - 宏观偏移（转头滞后、移步倾斜、肩部挥砍平面）必须**左乘 / 在相机空间父节点执行**；
  - 腕部翻刃、武器自转（如甩刀/转枪）必须**右乘 / 在武器局部子节点执行**。
  - 若把转头滞后右乘到已经旋转 90° 的攻击姿态之后，水平转头会变成武器局部的上下点头！

#### 陷阱 5：左右主手镜像的数学正确性与 `scale(-1, 1, 1)` 禁区
玩家切换左手主手（`HumanoidArm.LEFT`）或双持副手时，需要把右主手动作沿矢状面（$YZ$ 平面，法向为 $X$ 轴）做镜像，反射矩阵为 $M = \operatorname{diag}(-1, 1, 1)$（注意 $\det(M) = -1$，是不正当正交变换）。

- **严禁直接 `poseStack.scale(-1, 1, 1)` 渲染武器**：负缩放会反转所有三角面的绕序（winding order），在默认背面剔除下模型会内外翻转或法线照明出错，且会把非对称武器上的铭文、单侧护手、单边血槽直接左右倒置。
- **正确的 $SO(3)$ 相似变换镜像法则**（只镜像**运动学轨迹与姿态**，保持网格行列式 $\det = +1$）：
  对任意旋转矩阵 $R \in SO(3)$，其在镜像空间中的共轭旋转 $R' = M R M^{-1}$ 仍满足 $\det(R') = +1$。展开到各数学表达，得到完全一致的简洁公式：
  1. **位置 / 枢轴 / 平移（极向量 Polar Vector）**：
     $$\mathbf{p}' = M \mathbf{p} = (-p_x,\; +p_y,\; +p_z)$$
  2. **单位四元数（轴向量 / 伪向量 Pseudovector）**：
     旋转轴 $\mathbf{u}$ 在反射下遵循 $\mathbf{u}' = \det(M)\, M\mathbf{u} = (-1)(-u_x, u_y, u_z) = (+u_x, -u_y, -u_z)$，因此单位四元数 $q = (w, q_x, q_y, q_z)$ 的精确 $YZ$ 镜像为：
     $$q' = (w,\; +q_x,\; -q_y,\; -q_z)$$
     **直观校验**：绕 $X$ 轴的俯仰（Pitch）在左右镜像时方向不变（抬头仍是抬头）；绕 $Y$ 轴的偏航（Yaw）与绕 $Z$ 轴的滚转（Roll）严格反号！
  3. **轴角（Axis-Angle）**：
     $$(\hat{\mathbf{u}}, \theta) \;\mapsto\; \big((+u_x, -u_y, -u_z),\; \theta\big)$$
  4. **挥砍平面正交基**：
     将径向 $\mathbf{r}$ 与切向 $\mathbf{t}$ 作为极向量分别取 $x$ 反号得到 $\mathbf{r}', \mathbf{t}'$，再重新叉积 $\mathbf{n}' = \mathbf{r}' \times \mathbf{t}'$（等价于 $-M\mathbf{n}$，天然保持右手系 $\det = +1$）。
  5. **一手源码交叉印证**：
     - [`PlayerAnimationLibrary` `MirrorModifier.java`](https://github.com/PlayerAnimationLibrary/PlayerAnimationLibrary/blob/26.2/core/src/main/java/com/zigythebird/playeranimcore/animation/layered/modifier/MirrorModifier.java#L54-L59)（及 [`KosmX/minecraftPlayerAnimator` `MirrorModifier.java`](https://github.com/KosmX/minecraftPlayerAnimator/blob/1.21/coreLib/src/main/java/dev/kosmx/playerAnim/api/layered/modifier/MirrorModifier.java#L48-L59)）完全采用 `position.x *= -1; rotation.y *= -1; rotation.z *= -1;`；
     - Minecraft 原版 `ItemInHandRenderer`（见 [`HeldItemRendererMixin.java`](https://github.com/ThinkofRain1213/HMI-26.2/blob/master/mod/src/main/java/com/holdmylua/source/mixin/render/HeldItemRendererMixin.java#L118-L135)）亦是以 `f = right ? 1 : -1` 仅对 `translate.x`、`Axis.YP` 和 `Axis.ZP` 乘 `f`，而保持 `Axis.XP` 无符号。
  6. **非对称模型补偿**：若某武器本身网格左右不对称（例如带单侧护手或偏置挂饰），运动学层按上述公式镜像即可，网格局部初始朝向可通过武器配置中的 `leftHandGripCorrection` 四元数做一次性静态微调（绝不用负缩放）。

---

## 三、程序化微动态叠层与二阶弹簧阻尼架构

### 3.1 开源实现中的典型经验与反模式

1. **反模式：共享累加器 + 显式欧拉步进**（见 *Hold My Items* [`item_pose.lua`](https://github.com/ThinkofRain1213/HMI-26.2/blob/master/mod/src/main/resources/resourcepacks/pack_test/assets/minecraft/holdmyitems/item_pose.lua#L156-L180)）：
   - HMI 把移步速度、坠落 `fall`、潜行 `sneak`、挥剑 `swingProgress` 与视角差 `pitch - prevPitch` 全部注入同一组 `pitchSpeed`/`yawSpeed` 弹簧变量，并用显式步进 `dt * 30` 和 `pow(DAMPING, dt * 30)` 积分。
   - **隐患**：(a) 一旦客户端掉帧/GC 卡顿（`dt` 突然增大），显式二阶弹簧会剧烈过冲甚至数值爆炸；(b) 主动挥砍进度与步晃/转头混在同一阻尼器里，既无法在攻击瞬间干净让路，又会在挥砍结束后拖出不可控的残余晃动。
2. **正面教材：管线顺序、预测隔离与软追赶限幅**（见 Valve Source SDK [`baseviewmodel_shared.cpp`](https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/shared/baseviewmodel_shared.cpp#L350-L490) 与 [`basehlcombatweapon_shared.cpp`](https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/shared/hl2/basehlcombatweapon_shared.cpp#L261-L351)）：
   - **避免帧预测抖动**：`CalcViewModelView` 显式检查 `if ( !prediction->InPrediction() )` 后再更新视觉滞后（在 Minecraft 中对应：弹簧与微动态仅在客户端渲染帧 `deltaSeconds` 更新一次，绝不随 tick 补帧重复累加）。
   - **防 $\pm 180^\circ$ 偏航跳变的转头滞后**：`CalcViewModelLag` 不直接相减欧拉角，而是追踪一个滞后单位前向向量 `m_vecLastFacing`，计算 `vDifference = forward - m_vecLastFacing`；当快速甩鼠导致 `flDiff > g_fMaxViewModelLag` 时，按比例提高追赶速度 `flSpeed *= flDiff / g_fMaxViewModelLag`（**软限幅**，避免硬 clamp 导致的生硬撞墙感）。
   - **2:1 利萨如（8 字）移步晃动**：`CalcViewmodelBob` 让竖直晃动周期为 `HL2_BOB_CYCLE_MAX`（每一步一个波峰），水平晃动周期为 `2 * HL2_BOB_CYCLE_MAX`（左右脚交替各占半周期），并用 `RemapVal(speed, 0, maxSpeed, 0, 1)` 调制步进速率；且注释特别提醒**腾空时不可硬重置相位**，否则着陆瞬间画面会生硬跳变（正确做法是平滑衰减输出振幅）。
   - **需改进之处**：Valve 旧代码 `flSpeed * gpGlobals->frametime` 在高/低帧率下衰减率略有偏差，应升级为下节的精确解析解。

### 3.2 推荐的帧率无关数学原语

#### 1. 一阶精确指数阻尼（用于 Look Sway 追踪、掩码权重平滑、步幅包络）
来自 Daniel Holden [*Spring-It-On*](https://theorangeduck.com/page/spring-roll-call)（[`common.h`](https://github.com/orangeduck/Spring-It-On/blob/main/common.h#L78-L88)），以直观的**半衰期（`halflife`，秒）**替代依赖帧率的 `lerp(x, g, k * dt)`：

$$x \leftarrow \operatorname{lerp}\!\left(x,\; g,\; 1 - 2^{-\Delta t / h}\right) = \operatorname{lerp}\!\left(x,\; g,\; 1 - e^{-\frac{\ln 2}{h}\Delta t}\right)$$

在 30 FPS、60 FPS、144 FPS 与 240 FPS 下轨迹数学完全一致，且永不发散。

#### 2. 二阶阻尼弹簧闭式解析解（用于跳跃/落地缓冲、受击冲击、武器换手回弹）
来自 Ryan Juckett [*Damped Springs*](https://www.ryanjuckett.com/damped-springs/) 与 Daniel Holden [`common.h`](https://github.com/orangeduck/Spring-It-On/blob/main/common.h#L298-L418)（`spring_damper_exact_ratio` / `critical_spring_damper_exact` / `decay_spring_damper_exact`）：

- 用**阻尼比 $\zeta$**（`damping_ratio`）与**半衰期 $h$**（或固有角频率 $\omega = 2\pi f$）参数化：
  - $\zeta = 1$（**临界阻尼**，`critical_spring_damper_exact`）：最快回正且零回弹，适合**准星回归、镜头滞后、技能取消归位**，只需一次指数运算：
    $$y = \frac{2\ln 2}{h}, \quad j_0 = x - g, \quad j_1 = v + y j_0$$
    $$x' = e^{-y\Delta t}(j_0 + j_1 \Delta t) + g, \quad v' = e^{-y\Delta t}(v - y j_1 \Delta t)$$
  - $0.55 \le \zeta \le 0.85$（**轻度欠阻尼**，`spring_damper_exact_ratio`）：有一次清晰克制的过冲回弹，适合**落地沉坠、重武器收刀惯性、格挡震退**。
- **为什么优先闭式解而非 t3ssel8r 半隐式欧拉？**  
  t3ssel8r 的二阶动力学 $(f, z, r)$ 传递函数在加入初始响应零点 $r$（如 $r < 0$ 产生预备反抽）方面极富表现力，但其半隐式欧拉离散化在大 $\Delta t$ 时需额外做 $k_2$ 下界钳位或子步迭代以防不稳定；在 Minecraft 客户端帧率波动剧烈的环境下，**用闭式解析解作为底座，并通过注入初始速度冲量 $\Delta v_0$（例如落地瞬时给垂直弹簧施加 $v_y \mathrel{+}= \text{clamp}(\Delta v_{\text{land}} \cdot k,\; -v_{\max}, 0)$）即可同时获得绝对数值稳定与爽快的过冲手感**。
- **四元数弹簧**：在切空间（3D 旋转向量 $\boldsymbol{\theta} = \ln(q_{\text{cur}} q_{\text{goal}}^{-1})$，先对齐 $w \ge 0$）上运行 3 通道标量解析弹簧，再经指数映射 $\exp(\boldsymbol{\theta}') \cdot q_{\text{goal}}$ 还原为单位四元数，无万向锁且无须数值积分。

#### 3. 惯性化过渡（Inertialization）——无缝衔接离散动作与微动态
源自 David Bollo（GDC 2018 *Inertialization: High-Performance Animation Transitions*）并由 Daniel Holden 在 [`inertialization.c`](https://github.com/orangeduck/Spring-It-On/blob/main/inertialization.c) 中给出极简闭式实现：

- 传统交叉淡入淡出（Cross-Fade）在“连段中途取消 / 攻击切回待机 / 切武器”时，需要同时求值两段动画并混合，容易出现动作发粘、起手延迟或武器在两条轨迹间“抄近路穿模”。
- **惯性化做法**：切换发生的瞬间**立刻切到新目标轨道**，将旧轨道与新轨道的当前位置/姿态差及速度差写入残差通道 `(off_x, off_v)`：
  $$\text{off}_x \leftarrow (\text{src}_x + \text{off}_x) - \text{dst}_x, \qquad \text{off}_v \leftarrow (\text{src}_v + \text{off}_v) - \text{dst}_v$$
  随后每帧用 `decay_spring_damper_exact(off_x, off_v, halflife, dt)` 将残差自然衰减至 0，输出 $\text{out} = \text{dst} + \text{off}$。这样既保持**零输入延迟的即刻响应**，又彻底消灭了动作切换跳帧。

### 3.3 五层正交叠层管线与防打架设计

为了让呼吸、步晃、转头滞后、跳跃/落地弹簧与主动攻击轨迹**绝对不打架且不破坏瞄准稳定性**，建议在相机空间内建立严格分层的合成顺序（越靠外越偏宏观/相机空间，越靠内越偏武器局部）：

```text
Camera Space
 └─ [L0] 基准持握锚点 (Rest Anchor + Pitch Tuck)
     └─ [L1] 宏观微动态层 (乘以分通道衰减掩码 M_channel)
          ├─ 呼吸 (Breath: 低频微幅 Y 平移 + 极小 Pitch)
          ├─ 移步晃动 (Walk Bob: 2:1 利萨如 8 字，仅按水平速度缩放，腾空衰幅不重置相位)
          ├─ 跳跃/落地弹簧 (Jump/Land 2nd-Order Spring: Δvy 冲量驱动，轻度欠阻尼)
          └─ 转头滞后 (Look Sway: 四元数/前向差分 + 软限幅，作用于隐含肩外侧)
          └─ [L2] 主动动作层 (Active Action: 隐含肩枢轴大弧 + 伸肘平动 + 隐含腕枢轴甩腕/翻刃)
               └─ [L3] 冲击微震层 (Hitstop Micro-Shake / Recoil / Block Impulse，作用于隐含腕枢轴)
                    └─ [L4] 惯性化残差层 (Inertialization Decay，平滑吞噬状态切换跳变)
                         └─ Weapon Grip Offset → 渲染武器模型
```

#### 分通道衰减掩码表（Per-Channel Suppression Masks）

所有掩码权重在状态切换时必须经 `damper_exact`（建议半衰期 $0.04 \sim 0.08\text{s}$）平滑过渡，**严禁 0/1 阶跃跳变**：

| 当前玩家/动作状态 | 呼吸 $w_{\text{breath}}$ | 移步晃动 $w_{\text{bob}}$ | 转头滞后 $w_{\text{sway}}$ | 跳落弹簧 $w_{\text{jump}}$ | 设计意图 |
|---|---:|---:|---:|---:|---|
| **Idle / 移动巡航** | `1.00` | `1.00` | `1.00` | `1.00` | 完整生命力与重量反馈 |
| **冲刺（Sprint）** | `0.25` | `1.15` | `0.85` | `1.00` | 强调节奏感同时略收转头飘移 |
| **技能瞄准 / 蓄力选点** | `0.15` | `0.10` | `0.15` | `0.25` | **最高准星稳定性**，武器退让边缘 |
| **攻击前摇（Windup）** | `0.00` | `0.20` | `0.35` | `0.40` | 快速净空干扰，保留微弱惯性衔接 |
| **有效挥砍（Active Strike）** | `0.00` | `0.00` | `0.15` | `0.30` | **绝对保障挥砍弧面干净利落**，甩鼠不带偏刃轨；保留少量空中/落地反馈 |
| **命中顿帧（Hitstop）** | `0.00` | `0.00` | `0.05` | `0.15` | 冻结主动作并让 L3 高频震动成为唯一焦点 |
| **收招恢复（Recovery）** | `0.30 → 1.0` | `0.40 → 1.0` | `0.60 → 1.0` | `0.80 → 1.0` | 随恢复进度平滑放回微动态，通过 L4 惯性化无痕回位 |

#### 瞄准稳定性硬约束（Competitive Safeguards）

1. **只加偏移、绝不改相机**：L1–L4 所有变换只作用于第一人称武器 `PoseStack`，绝不污染真实相机准星方向（除明确策划批准的独立屏幕震动通道外）。
2. **软限幅包络（Soft Saturation）**：对转头滞后和跳落弹簧的输出施加双曲正切软限幅 $f(x) = A \tanh(x / A)$，无论鼠标 DPI 多高或从多高坠落，武器永远不会飞出屏幕或冲入中心准星禁区。
3. **统一客户端时钟源**：微动态步进统一使用单调递增的客户端帧间隔 `dt = clamp(frameDeltaSec, 0.0f, 0.05f)`，暂停界面或切出窗口时不积压巨额 `dt`。

---

## 四、面向万象（MYRIAD）的架构落地建议

结合万象当前 Fabric 26.3 客户端渲染管线（`PoseStack` + JOML），推荐后续原型阶段直接按以下最小数据抽象落地，避免回退到脚本式逐轴手调欧拉角：

1. **`VirtualPivotRig`（角色/武器静态骨架配置）**：
   - `Vector3f restOffset` / `Quaternionf restRotation`（基准右主手静态构图）；
   - `Vector3f shoulderPivot`（画外隐含肩枢轴）；
   - `Vector3f wristPivot`（武器局部握持点，与模型资产锚点统一约定）；
   - `Quaternionf leftHandGripCorrection`（仅用于非对称模型，默认为单位四元数）。
2. **`SlashArcSample`（主动攻击采样输出）**：
   - 输出 `shoulderRot`（单位四元数，可由“挥砍平面法向 + 扫掠角 $\theta(u)$”生成，支持 $>180^\circ$）、`reachOffset`（伸肘平移）、`wristRot`（甩腕/翻刃四元数）与当前阶段 `ActionPhase`（驱动 L1 掩码查表）。
3. **`HandMirrorUtil`（统一单点镜像工具）**：
   - 仅在最终提交 `PoseStack` 前一步（或统一在所有右主手数据出口处）按 `HumanoidArm.LEFT` 一次性执行：
     - 极向量：`(-x, y, z)`；
     - 四元数：`(x, -y, -z, w)`（JOML 构造顺序 `new Quaternionf(q.x, -q.y, -q.z, q.w)`）。
   - **禁止**在各招式代码里分散手写 `* sideSign`，从源头消除左右手符号遗漏。
4. **`ProceduralMotionBank`（客户端每手独立状态机）**：
   - 内置 `Spring1D` / `Spring3D`（Holden 闭式解析解）、`Damper1D`（`damper_exact`）、`InertializerPose`（位置 + 切空间旋转向量衰减）与 2:1 Walk Bob 相位器；
   - 建议在后续原型中重点实机校验三组手感参数：(a) 隐含肩枢轴的默认 $(x, y, z)$ 半径区间对 FOV 70°–110° 的适配度；(b) 斜劈/反手连招中 `lookSway` 掩码保留比例（`0.10` vs `0.25`）对跟手感的差异；(c) 落地欠阻尼弹簧 $(\zeta \approx 0.70,\; h \approx 0.12\text{s})$ 与攻击切回待机惯性化半衰期（$h \approx 0.08\text{s}$）的主观手感。

---

## 五、参考来源（一手源码、论文与官方文档）

1. **JOML (`org.joml`) 官方源码**  
   - `Matrix4f#rotateAround`（枢轴共轭变换实现）：<https://github.com/JOML-CI/JOML/blob/main/src/main/java/org/joml/Matrix4f.java#L11844-L11874>  
   - `Quaternionf#slerp`、`Quaternionf#nlerp`、`Quaternionf#rotationYXZ`、`Quaternionf#lookAlong`：<https://github.com/JOML-CI/JOML/blob/main/src/main/java/org/joml/Quaternionf.java#L2452-L2509> 、<https://github.com/JOML-CI/JOML/blob/main/src/main/java/org/joml/Quaternionf.java#L2645-L2659> 、<https://github.com/JOML-CI/JOML/blob/main/src/main/java/org/joml/Quaternionf.java#L2865-L2899>
2. **Better Combat (26.2) 与 PlayerAnimationLibrary / PlayerAnimator**  
   - `AttackAnimationStack.java`（第一人称 `body` 俯仰角耦合与偏移补偿）：<https://github.com/ZsoltMolnarrr/BetterCombat/blob/26.2/common/src/main/java/net/bettercombat/client/animation/AttackAnimationStack.java>  
   - `FirstPersonHelper.java` 与 `AbstractClientPlayerEntityMixin.java`：<https://github.com/ZsoltMolnarrr/BetterCombat/blob/26.2/common/src/main/java/net/bettercombat/client/animation/FirstPersonHelper.java> 、<https://github.com/ZsoltMolnarrr/BetterCombat/blob/26.2/common/src/main/java/net/bettercombat/mixin/client/AbstractClientPlayerEntityMixin.java>  
   - `MirrorModifier.java`（$YZ$ 平面镜像：`position.x *= -1; rotation.y *= -1; rotation.z *= -1`）：<https://github.com/PlayerAnimationLibrary/PlayerAnimationLibrary/blob/26.2/core/src/main/java/com/zigythebird/playeranimcore/animation/layered/modifier/MirrorModifier.java> 、<https://github.com/KosmX/minecraftPlayerAnimator/blob/1.21/coreLib/src/main/java/dev/kosmx/playerAnim/api/layered/modifier/MirrorModifier.java>  
   - `FirstPersonMode.java` 与 `FirstPersonConfiguration.java`（隐藏双臂保留 3P 骨骼驱动武器）：<https://github.com/KosmX/minecraftPlayerAnimator/blob/1.21/coreLib/src/main/java/dev/kosmx/playerAnim/api/firstPerson/FirstPersonMode.java> 、<https://github.com/KosmX/minecraftPlayerAnimator/blob/1.21/coreLib/src/main/java/dev/kosmx/playerAnim/api/firstPerson/FirstPersonConfiguration.java>
3. **Hold My Items (CC0 5.1.1 / Fabric 26.2 移植仓)**  
   - `HeldItemRendererMixin.java`（原版左右手镜像与 `PoseStack#rotateAround`）：<https://github.com/ThinkofRain1213/HMI-26.2/blob/master/mod/src/main/java/com/holdmylua/source/mixin/render/HeldItemRendererMixin.java>  
   - `M.java`（离轴枢轴封装）：<https://github.com/ThinkofRain1213/HMI-26.2/blob/master/mod/src/main/java/com/holdmylua/source/scripting/script_wrappers/M.java>  
   - `item_pose.lua`（分武器离轴枢轴与欧拉弹簧微动态反模式实例）：<https://github.com/ThinkofRain1213/HMI-26.2/blob/master/mod/src/main/resources/resourcepacks/pack_test/assets/minecraft/holdmyitems/item_pose.lua>
4. **First-Person Model**  
   - `LogicHandler.java`（第三人称身体后移偏移与 ±50° 头身跟随夹角）：<https://github.com/tr7zw/FirstPersonModel/blob/main/src/main/java/dev/tr7zw/firstperson/LogicHandler.java>
5. **Valve Source SDK 2013**  
   - `baseviewmodel_shared.cpp`（`CalcViewModelView` 叠层顺序、`!prediction->InPrediction()`、`CalcViewModelLag` 向量差分与软追赶限幅）：<https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/shared/baseviewmodel_shared.cpp>  
   - `basehlcombatweapon_shared.cpp`（`CalcViewmodelBob` / `AddViewmodelBob` 2:1 利萨如 8 字步晃与相位保护）：<https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/shared/hl2/basehlcombatweapon_shared.cpp>
6. **精确弹簧阻尼、惯性化与程序化动画**  
   - Daniel Holden, *Spring-It-On: The Game Developer's Spring-Roll-Call*：<https://theorangeduck.com/page/spring-roll-call>  
   - `orangeduck/Spring-It-On` 源码（`damper_exact`、`spring_damper_exact_ratio`、`critical_spring_damper_exact`、`inertialization.c`）：<https://github.com/orangeduck/Spring-It-On/blob/main/common.h> 、<https://github.com/orangeduck/Spring-It-On/blob/main/inertialization.c>  
   - Ryan Juckett, *Damped Springs*（二阶阻尼弹簧闭式解析解）：<https://www.ryanjuckett.com/damped-springs/>  
   - David Bollo (The Coalition, GDC 2018), *Inertialization: High-Performance Animation Transitions*（惯性化动画过渡原理）  
   - Matthew Boehm (Blizzard, GDC 2017), *Animation Bootcamp: The First Person Animation of Overwatch*（第一人称与第三人称模型解耦哲学）
7. **旋转数学与空间曲线标架原始文献**  
   - Ken Shoemake (1985), *Animating Rotation with Quaternion Curves*, SIGGRAPH '85, pp. 245–254：<https://doi.org/10.1145/325165.325242>  
   - Jonathan Blow (2004), *Understanding Slerp, Then Not Using It*：<http://number-none.com/product/Understanding%20Slerp%2C%20Then%20Not%20Using%20It/>  
   - Wenping Wang, Bert Jüttler, Dayue Zheng & Yang Liu (2008), *Computation of Rotation Minimizing Frames*, ACM TOG 27(1)：<https://doi.org/10.1145/1330511.1330513>（承接 Andrew J. Hanson & Hui Ma 1995, Indiana Univ. CS TR-425 *Parallel Transport Approach to Curve Framing*）  
   - Godot Engine Official Documentation, *Using 3D transforms*：<https://docs.godotengine.org/en/stable/tutorials/3d/using_3d_transforms.html>
