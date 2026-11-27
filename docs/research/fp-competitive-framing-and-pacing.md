# 快节奏竞技第一人称近战与弓的姿势构图与节奏调研

- **关联地图**：Wayfinder #72《第一人称无手姿势与动画体系规划》
- **对应工单**：#73《调研快节奏竞技第一人称近战与弓的姿势构图与节奏》
- **调研范围**：快节奏竞技与高机动第一人称游戏（*Overwatch*、*Team Fortress 2* / Source 引擎、*Destiny*、*Warhammer: Vermintide 2*、*Ghostrunner*、*Mirror's Edge* 等）的第一人称持械构图、瞬时响应近战动作节奏与弓箭姿态张力设计。
- **证据口径**：优先采信一手公开资料（GDC 官方录像与讲义、Valve 开源生产代码 `ValveSoftware/source-sdk-2013`、开发者官方博客、*Game Anim* 与 *Game Feel* 原作者专文、樱井政博官方开发频道）。凡属结合万象「纯无手第一人称」约束所做的工程推演，均在文中显式标明，不与一手来源混淆。

---

## 核心结论速览

1. **构图与视野安全区**：高机动竞技第一人称必须将**中心准星与中上视野留作绝对清洁区**，武器本体锚定在下缘与右/左侧边角；通过**独立 Viewmodel FOV / 受控纵深透视**防近距畸变，并借武器斜向轴线（**待机纵深角**为正，尖端指向场景深处与准星方向）形成视线引导与“握持感”，避免像贴在镜头前的竖直立牌。
2. **极短前摇下的大开大合**：竞技手感要求输入到视觉爆发压缩在极短窗口内（1–3 帧 / < 100ms 级别），因此不能靠拉长前摇演重量；真正“干脆又不小气”的做法是**把重量与气势后移到高速跨屏大弧面、刃面朝向翻转、接触点短顿挫（Hitstop / Hold）与可打断收招（Early-Out Recovery）**，并在连段时从上一刀收势直接起手形成左右交替动画链。
3. **第一人称弓箭构图与张力**：竞技第一人称弓普遍在第一人称**倾斜持弓（Cant Angle）**（哪怕第三人称仍为立弓），用倾角避开中轴与左侧盲区；拉弓过程拆为清晰的**拉弦阻力曲线 → 满弓微张力稳持（可选超时颤抖）→ 蓄能瞬释与阻尼回弹 → 取消收弓**四态，通过纵深拉距与回弹层级体现轻重箭差异，而不偏移真实准星。

---

## 一、屏幕构图、视野安全区、占屏比与纵深透视倾角

### 1.1 中心准星安全区与负空间引导线

在快节奏多人对抗中，战场信息高度密集，第一人称武器首先是**取景框的边缘框架（Framing Device）**，其次才是角色个性载体。

- **清洁中心与引导线（*Overwatch*）**：Blizzard 动画师 Matthew Boehm 在 GDC 2017 演讲《*Animation Bootcamp: The First Person Animation of Overwatch*》中强调，*Overwatch* 的第一人称构图严格管理画面**负空间（Negative Space）**，绝不能让武器在已经很混乱的战场里制造额外视觉噪音；武器与持械姿态的轮廓线应主动**把玩家视线引导向屏幕中心准星**，而不是抢夺中心焦点。Boehm 亦指出早期部分大体积武器（如黑百合步枪迭代早期）因右侧占屏过大遮挡视野，团队反复削减轮廓占位并向外缘与下方微调位置以保住主视线与侧翼察觉能力[^1]。
- **竞技视野优先的极简边角策略（Valve *Team Fortress 2*）**：Valve 在 *Meet Your Match* 更新中为 TF2 正式加入极简第一人称模型模式 `tf_use_min_viewmodels`[^2]。从 Valve 公开的 `source-sdk-2013` 源码可见（`src/game/shared/tf/tf_viewmodel.cpp`），该机制并非粗暴隐藏武器，而是给每件武器定义独立的局部偏移向量 `GetViewmodelOffset()`，沿相机的 `(forward, right, up)` 三轴把武器**压低并推向画面下缘边角**，从而在保留动作反馈的同时最大化释放战场周边视野[^3]。
- **高危移动态的动态让位（Bungie *Destiny*）**：Bungie 高级动画师 David Helsby 在 GDC 2015 演讲《*The Art of First Person Animation for Destiny*》中说明，第一人称屏幕空间是稀缺资源：武器在冲刺、滑铲、瞄准等状态间会主动下压、回收或贴近边角，既保证目标捕捉视野，又避免移动时大块几何体持续扫过准星附近造成压迫与视觉疲劳[^4]。

### 1.2 Viewmodel FOV 与世界 FOV 解耦：避免近距畸变与“立牌感”

第一人称武器紧贴近裁剪面，如果直接沿用高视野角的世界相机（竞技玩家常开 90°–110°），武器会产生严重鱼眼拉伸或显得单薄扁平。

- **双 FOV 渲染规范（Valve Source 引擎源码实证）**：在 `ValveSoftware/source-sdk-2013` 的 `src/game/client/view.cpp` 中，Viewmodel 拥有独立于世界相机的 `viewmodel_fov` 变量，**默认 54°**，官方菜单允许调节上限为 **70°**（`ConVar v_viewmodel_fov("viewmodel_fov", "54", ..., 54, true, 70)`），且会按屏幕宽屏比例进行缩放（`ScaleFOVByWidthRatio`）[^5]。较小且受控的 Viewmodel FOV 能让近处武器维持扎实的透视体积与稳定的屏幕占比，不随玩家拉高世界 FOV 而畸变。
- **第一人称与第三人称“视觉欺骗”合法化**：无论是 Bungie 在 *Destiny* 中采用的双骨架架构（第一人称独立上半身/持械骨架叠加在第三人称下半身之上）[^4]，还是 *Overwatch* 中第一人称与第三人称姿态的大胆分离（例如半藏第三人称保持正统立弓、第一人称大幅倾斜持弓；源氏/温斯顿在第一人称中按镜头可读性大幅夸张变形）[^1]，行业共识都非常一致：**第一人称姿态只对“镜头内的立体透视与可读性”负责，绝不机械照搬第三人称解剖学角度**。
- **地平线稳定与防晕约束（DICE *Mirror's Edge* & Bungie *Destiny*）**：DICE 在 *Mirror's Edge* 的 GDC 演讲《*Creating First-Person Movement for Mirror's Edge*》[^6] 与 Helsby 的 *Destiny* GDC 演讲[^4] 均指出，镜头不能生硬绑定角色骨骼旋转，否则剧烈的地平线倾斜会引发前庭-视觉冲突（3D 眩晕）。因此，节奏感与重量冲击应主要由**武器自身姿态变化 + 极克制的受限镜头微联动**承载，真实准星与地平线始终稳定。

### 1.3 动态微滞后与防穿帮（建立“握持感”的关键物理线索）

在**无手第一人称**条件下，玩家看不到前臂和手腕，如果武器完全刚性锁死在相机坐标系中，就会立刻产生“贴在屏幕上的 UI 立牌”错觉。Valve 的 Source 引擎在 `src/game/shared/baseviewmodel_shared.cpp` 与 `src/game/shared/tf/tf_viewmodel.cpp` 中给出了极具参考价值的工业做法：

- **带上限与自适应追赶的转头滞后（`CalcViewModelLag`）**：计算相机当前朝向与滞后朝向向量差，并设置最大滞后上限 `g_fMaxViewModelLag = 1.5f`；当玩家快速甩视角超过阈值时，追赶速率按比例放大（`flSpeed *= flDiff / g_fMaxViewModelLag`）[^7]。这既给了武器清晰的质量惯性，又防止高速转身时武器被甩出画面或硬切弹回。
- **镜头震动衰减**：`vieweffects->ApplyShake(vmorigin, vmangles, 0.1)` 只让 Viewmodel 继承 10% 的镜头抖动幅度[^7]，防止武器在受击或爆炸时剧烈乱跳干扰视野。

### 1.4 典型武器原型的第一人称构图规律汇总

结合上述一手案例及无手约束，不同武器原型的待机构图分化如下：

| 武器原型 | 典型参考案例 | 待机锚点（右手视角） | 目标占屏与安全区 | 待机纵深角与透视倾角 | 塑造气势与“握持感”的关键手段 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **直剑 / 长兵** | *Overwatch* 源氏（龙刃）、*Ghostrunner* 武士刀、*Vermintide 2* 长剑 | 右下象限外侧，剑柄落于画面底边下方（暗示画外握点） | 中等偏克制；剑身完全避开中心准星半径与左侧视野 | **待机纵深角为明显正值**（约 25°–45° 指向景深并略向准星聚拢），刃面微侧露反光 | 剑尖朝向景深形成透视缩短（Foreshortening），辅以呼吸与转头滞后；绝不垂直立于右侧当“电线杆” |
| **重锤 / 重器** | *Overwatch* 莱因哈特火箭重锤 | 右下偏低且更靠外缘，锤头沉于下缘三分线附近 | 体积感更强但**压低重心**，让出中上视野与准星正下方 | 长柄自右下画外斜向前上方探入景深，锤头大块面略外倾留出中心负空间 | 用“下沉的锤头重量 + 较慢的移步惯性与低频滞后”建立吨位，而非把锤头顶到屏幕中央挡路 |
| **轻刃 / 匕首** | *Overwatch* 源氏快攻短刀、轻型刺刃 | 下缘偏右（双刃则左右对称压低），更贴近镜头近端 | 占屏最小、轮廓最利落，最大化周边视野 | 前倾敏捷角或反手斜角，刃尖紧指前方攻击方向 | 靠紧凑轮廓、高响应转头跟随与极低惯性延迟表现轻盈敏捷 |
| **弓箭** | *Overwatch* 半藏风暴弓、*TF2* 猎人短弓（Huntsman） | 持弓侧偏左或偏右外缘（依持弓手约定），弓身大幅斜置 | 弓臂让开中心准星及水平瞄准带，仅保留弓梢与箭簇的轮廓框架 | **弓身横向/斜向倾角（Cant）显著**，箭杆沿景深指向准星正前方 | 靠弦-箭-弓梢构成指向准星的透视三角；待机低斜、拉弓收拢抬平，形成强烈的“备战→锁定”视觉反差 |

---

## 二、竞技瞬时响应下的大弧面动作、刃面朝向与连段衔接

### 2.1 核心矛盾：竞技 0–3 帧响应 vs 传统动画前摇

几乎所有一线动作/FPS 动画师都把**前摇（Anticipation）与响应性（Responsiveness）的矛盾**列为游戏动画第一难题：

- **感知阈值（Steve Swink *Game Feel*）**：Steve Swink 在 *Game Feel: The Secret Ingredient* 及同名著作中指出，玩家对控制的“即时感”依赖极短的输入-画面反馈回路（通常需压在 **< 100ms**、即 60fps 下约 1–3 帧的窗口内）；过长的起步平滑或前摇会立刻让操作显得“发粘、迟钝”[^8]。
- **设计师与动画师的经典冲突（Bungie & *Game Anim*）**：David Helsby 在 *Destiny* GDC 演讲中提到，动画师往往希望有充足前摇来表现力量，而战斗策划要求按键后 1 帧内就看到响应[^4]。Jonathan Cooper 在 *Game Anim*（Game Developer 专文《*The 12 Principles of Animation in Video Games*》）中给出明确区分：**NPC/敌人的攻击前摇（Telegraphing）应足够长以供玩家躲避格挡，但玩家自身攻击的前摇必须极致压缩**，否则角色就会显得不听使唤[^9]。
- **极限压缩下的实证（Mariel Cartwright *Skullgirls*）**：Lab Zero 首席动画师 Mariel Cartwright 在 GDC 2014《*Making Fluid and Powerful Animations For 'Skullgirls'*》中展示了极速招式（例如总计仅 6 帧的出拳）的处理方式：在严苛帧预算下，前摇被压缩到 **1 帧甚至亚帧级**，而把表现力全部集中到强辨识度的极端关键帧（Key Pose）、高速跨越轨迹与拖影/多曝光残影上[^10]。

> **对照万象当前约束**：万象 `MeleeStrokeTiming.java` 将普攻从提交输入到 **普攻接触姿态（HIT）** 锚定在 `0.85 tick`（约 42.5ms，不足 3 帧@60fps），这正处于竞技瞬时响应的黄金区间。因此，**不能为了“做大动作”去拉长到 HIT 的时间，而必须在保持 ~40–50ms 瞬时出招的前提下重构整条弧面与后段节奏**。

### 2.2 如何在极短前摇下做到“干脆又不小气”：五大工程原则

综合 Blizzard、Bungie、Valve、Fatshark、Cooper 与樱井政博的一手方法论，极短前摇下的大开大合依赖以下五个协同机制：

#### 1. “微蓄借势 → 瞬爆发”的非均匀速度曲线，消灭匀速滑动
- Jonathan Cooper 在讨论 *Slow In & Slow Out* 与 *Timing* 时指出，匀速运动（Uniform/Linear）必然显得机械轻浮；而速度与距离的剧烈反差才是“重量”的来源[^9]。
- 在 40–50ms 的极短起手窗内，不应把武器从待机点匀速推向中心，而应做**极短暂的反向借势（1 帧级微外拉/沉柄 + 迅速蓄满旋转角速度）**，随后在进入接触窗前以陡峭加速曲线**瞬时弹射出大跨度位移**。即便反向动作只有几帧的一小部分，视觉上的“逆向对比”会成倍放大随后主挥砍的爆发感。

#### 2. 跨屏大弧面轨迹 + 过冲（Overshoot），把力量感转移到“爆发与收势”
- 樱井政博在《*Breaking Down Attack Animations*》中拆解攻击动画时指出，打击感并不只靠起手，**大幅度的收势延伸（Follow-Through）与定格姿态**同样决定攻击是否显得有力；没有足够跨度与余势的挥砍在玩家眼中就是“小气、没打实”[^11]。
- Jonathan Cooper 亦强调：相比受响应性死死锁住的前摇，**Follow-Through 拥有大得多的表现空间**——把武器挥过中心准星后继续扫向对侧画面边缘并产生明显的**过冲（Overshoot）**（例如重锤惯性下沉至左下角、长剑扫透至左侧画外边缘），再通过弹性回摆收敛，就能在不增加任何输入延迟的情况下撑起“大开大合”的气势[^9]。
- 在 *Overwatch*（莱因哈特横扫、源氏龙刃大斜斩）与 *Ghostrunner*（高速斜跨全屏的居合斩）中，挥砍轨迹均不是在右下象限局部小幅晃动，而是**明确横穿或斜穿屏幕中心带**（扫过横幅的大部分视野），同时确保刃体本身在中心准星附近以最高角速度一闪而过——既给足跨屏气势，又绝不长时间遮挡准星。

#### 3. 接触点短顿挫（Hitstop / 刻意破弧）制造“斩入感”与重量分化
- 樱井政博在《*Stop for Big Moments!*》[^12] 与《*Eight Hit Stop Techniques*》[^13] 中系统论证了**命中停顿（Hitstop）**的价值：当攻击几乎瞬发时，极容易显得轻飘飘；在接触点施加极短的冻结/减速顿挫（以及定向微震与受击反馈），能让大脑瞬间感知到“力量传递与碰撞阻力”，同时不牺牲按键响应延迟。
- Jonathan Cooper 也给出了一个极具洞察的警告：**如果整条动作轨迹自始至终都是完美平滑的弧线，反而会显得轻飘无力（light or floaty）；刻意在撞击或力竭点打破平滑弧线（Breaking the Arc）**，才能抓住眼球并塑造厚重质感[^9]。
- 注意多人联机边界：在多人对抗与延迟补偿架构下（如 Valve `tf_weaponbase_melee.cpp` 中的服务端 `lagcompensation` 与本地预测分离）[^16]，联机环境无法像单机那样冻结全局时间，但**纯本地第一人称武器视图的短顿挫（速度骤降/微停顿 + 极轻微的局部微震）完全可行**，且不影响服务端判定；万象也可借此实现“长剑脆顿 → 重锤沉顿 → 轻刃极短顿”的重量分化。

#### 4. 刃面朝向（Edge Alignment）与隐含支点/手腕翻转
- Fatshark 首席动画师 Morris 在《*Dev Blog: Animating the Grail Knight*》中详细记录了团队研读 16 世纪剑术典籍（Joachim Meyer）打磨 *Vermintide 2* 第一人称长剑动画的经验：第一人称视角下玩家离刀剑极近，**正确的刃面朝向（Edge Alignment）、护手与手腕翻转角度**直接决定武器看起来是“锋利的实战兵器”还是“僵硬的贴图木板”[^14]。
- **核心规律（尤适用于纯无手第一人称）**：
  - **斩击中刃口领跑**：沿弧线挥动时，刀刃/锤面法线必须始终对齐运动切向，避免用刀背或刀面平拍扫屏；
  - **隐含支点（Virtual Pivot）**：纯无手视角下虽然看不到手臂，但武器绕**画外远端支点（相当于虚拟手腕/肘/肩）**做圆弧公转 + 绕握柄做自转，才能呈现真实的杠杆半径与挥砍张力；若只绕模型原点原地旋转，就会立刻露出“悬空转风车”的破绽；
  - **换向必先翻刃**：从右上→左下斜斩切入左下→右上反撩时，不能原路倒放，必须在过冲末尾完成一次干脆的**轴向翻刃（Roll 反转）**，把刃口重新对准反撩方向，再切回跨屏弧线。

#### 5. 可打断收招（Early-Out Recovery）与左右交替动画链
- **提前解锁打断帧（Cooper）**：Cooper 明确指出，完整的 Follow-Through 很漂亮，但绝不能死锁玩家下一次操作；正确架构是在收招中途标定**可打断窗口（Interrupt Window / Early-Out Frame）**——若玩家不再按键，武器完整播完厚重的收招与弹簧回位；若玩家连续攻击或释放技能，则立即截断后半段收招无缝切入下一段[^9]。
- **左右对称动画链（Fatshark & *Overwatch*）**：
  - Fatshark 在 *Vermintide 2* 中明确设计“动画链（Animation Chain）”，利用可左右互换的架势（如左右 Ox Guard 与 Wrath Guard）让第一段挥砍的终点自然成为第二段挥砍的起手架势，避免每次攻击都强行拉回待机再重挥的“机械复读感”[^14]。
  - *Overwatch* 的莱因哈特火箭重锤（右→左横扫接左→右回扫，周期约 0.96s）与源氏龙刃（大斜斩左右交替，周期约 0.9s）[^15] 同理：每段挥砍的终点直接滞留在对侧过冲位，下一击在极短窗口内顺势翻刃扫回，既大幅降低了重复感，又让第二击天然自带“已蓄满势能的起始位置”，做到**零额外前摇却有超大扫击行程**。
- **表现与判定的解耦（Valve TF2 近战源码）**：在 Valve 的 `src/game/shared/tf/tf_weaponbase_melee.cpp` 中，`Swing()` 立即触发挥动动画与挥空音效，而真正做射线/包围盒检测的 `Smack()` 则挂在独立的延迟时点 `m_flSmackTime` 上，并带有滞后补偿与适当放宽的检测包络（`vecSwingMins/Maxs = ±18`）[^16]。这印证了万象“表现层与服务端判定解耦、表现层严格锚定 `HIT` 接触姿态”的架构合理性。

---

## 三、第一人称弓箭待机倾角、拉弦纵深、满弓张力与放箭回弹

第一人称弓箭是竞技视角里最容易“挡屏”或“失去拉力反馈”的武器类型。结合 *Overwatch*（半藏）、*Team Fortress 2*（Huntsman 源码）、*Apex Legends*（波塞克复合弓）与高机动 FPS 弓箭设计实践，可提炼出四个核心维度的明确规律：

### 3.1 待机与拉弓倾角（Bow Cant Angle）：打破垂直遮挡，用倾角编码状态

- **为什么竞技第一人称弓普遍“斜持/近横持”**：现实传统弓多为竖直握持，但在第一人称镜头中，一根竖直的长弓臂会像柱子一样直接劈开画面视野，遮挡准星一侧的横向走位目标。因此，*Overwatch* 的半藏在第三人称虽保持端庄的日式立弓姿势，**在第一人称视角中却将弓身大幅放斜（近乎横向倾斜）**，把弓臂压到画面下外侧，彻底清空准星上方与左右水平线的追踪视野[^1]。
- **倾角作为状态机信号**：
  - **待机态**：采用偏舒展的斜倾角（例如 20°–35° 左右或更低垂），弓身退居边角，不侵占主视野；
  - **拉弓瞄准态**：弓身向内微收并调整至最利落的瞄准倾角（常见 30°–60° 斜持，或特定重箭/瞬射形态的差异化倾角），让箭簇与箭杆指向屏幕中心准星的远端灭点，构建出“箭指目标”的天然瞄准引导线；
  - **技能态分化**：通过明显不同的弓身倾角（如常规斜持 vs 精准直倾 vs 散射大横倾），玩家无须看 UI 图标，仅靠余光扫到弓身倾角就能瞬间辨认当前装填的技能箭形态（万象 `LuoxiaBowMotion.java` 中已用 `shoulder` 倾角区分各技能，正是这一原则的直接体现）。

### 3.2 拉弦纵深与非线性阻力曲线：用纵深透视与音效联动“演拉力”

- **非线性蓄力曲线**：匀速直线拉弦会显得像滑轨机械，缺乏弓片弯曲的弹性阻力。成熟的第一人称弓普遍采用**非线性拉距映射**（前段迅速拉开进入拉弓形态，中后段逐渐变沉并减速锁入锚点），让玩家感受到弓臂越拉越硬的弹性势能积累。
- **纵深拉距（Z 轴与弓臂形变配合）**：
  - 在纯无手第一人称下，由于看不到拉弦的手臂，**箭矢沿视线纵深向镜头方向平滑后撤 + 箭簇回缩贴近弓窗 + 弓臂同步向内弯曲（或分阶段网格形变）** 就成为传递“拉满力道”的三大核心视觉支柱。
  - 同时，弓身整体可配合微幅前探或沉肩下压（Z/Y 微偏移），与箭尾后撤形成**双向拉扯的相对位移**，把“弦在向后绷紧、弓在向前顶住”的力学对抗做透。
- **视听同步缩放（Valve TF2 Huntsman 源码实证）**：在 `src/game/shared/tf/tf_weapon_compound_bow.cpp` 中，TF2 的 Huntsman 拉弓时（`ACT_VM_PULLBACK` -> `ACT_ITEM2_VM_CHARGE`），不仅把第一人称视图动画播放速率与攻速修正同步（`SetPlaybackRate(flRateMultiplyer)`），连拉弦音效 `Weapon_CompoundBow.SinglePull` 的**音高（Pitch）也按同一倍率缩放**（`ep.m_nPitch *= flRateMultiplyer`）[^17]。当攻速加成改变拉弓时长时，视觉拉满瞬间与音效绷紧高点严丝合缝，极大提升竞技节奏的肌肉记忆。

### 3.3 满弓保持张力与疲劳/窗口反馈（Full-Draw Tension）

在快节奏竞技对抗中，“刚好拉满”的瞬间是决定命中与伤害的关键节点，玩家必须不看进度条就能凭体感知道已满弓。

- **四态清晰切换（Valve TF2 Huntsman 源码实证）**：查阅 `tf_weapon_compound_bow.cpp`（L57、L396–427、L494–502）可见，Valve 为第一人称弓设计了极其清晰的状态机[^17][^18]：
  1. **拉弦蓄力态（`ACT_ITEM2_VM_CHARGE`）**：默认 1.0 秒拉满（随攻速缩放），箭速由 1800 升至 2600 Hammer units/s，重力系数由 0.5 降至 0.1；
  2. **满弓稳持态（`ACT_ITEM2_VM_IDLE_2`）**：达到 1.0 秒满蓄后切入稳定的高张力保持态；
  3. **超时疲惫颤抖态（`ACT_ITEM2_VM_CHARGE_IDLE_3` → `ACT_ITEM2_VM_IDLE_3`）**：持续满蓄超过 `TF_ARROW_MAX_CHARGE_TIME = 5.0f` 后，视角动画主动切入剧烈手抖循环，且射出箭矢附加 `±6°` 随机偏移，直观惩罚无脑卡点久蓄；
  4. **取消收弓态（`LowerBow()` → `ACT_ITEM2_VM_DRYFIRE`）**：右键平稳松弦收箭，避免被迫放空箭暴露位置。
- **高张力微颤与满弓确认（*Apex Legends* 波塞克弓等）**：
  - Respawn 在 *Apex Legends* Legacy 更新中推出的波塞克复合弓（Bocek Compound Bow）通过短促蓄力满弓峰值 + 连续满弓节拍奖励（Deadeye's Tempo）强化拉弓-松弦的律动[^19]；
  - 结合无手第一人称特点，**抵达满弓瞬间应给一个干脆的“锁点微顿 + 弦/箭簇微小闪烁或清脆提示音”**，进入满弓稳持后叠加**极低幅、高频、不偏移真实准星的微颤（Micro-Tremor）与深呼吸起伏**——既传达满弦待发的紧绷张力，又不破坏竞技精确瞄准。

### 3.4 放箭瞬释、阻尼回弹与快/重箭分层

- **弓箭的“回弹”不同于枪械火药后坐**：枪械是膛压爆发导致枪口向后上方猛跳；弓箭放箭是**弹性势能向前瞬释**。因此最自然且有冲击力的第一人称放箭反馈是**二段式动态**：
  1. **瞬释冲击（1–2 帧）**：箭矢瞬时离弦消失/飞出，弓身因前方反作用力产生极干脆的**微向前挫 + 绕握柄轻微前倾点头（Pitch Kick）与滚转回弹**，弓弦瞬间回弹至直弦态并伴随极短余震；
  2. **阻尼回位与搭箭衔接**：弓身经弹簧阻尼迅速回稳，随即进入利落的“隐含搭箭/归位”节奏（在无手视角下可表现为弓身轻微下沉翻腕再抬起、下一支箭沿纵深利落卡入箭台），无缝衔接下一轮拉弓。
- **轻重射击的回弹层级**：快射/短抽箭采用**低振幅、快衰减回弹**以维持连射爽感；满蓄重箭或强化技能箭则叠加**更明显的弓身瞬挫、稍长的阻尼回弹与极克制的视觉 FOV/镜头微冲**，让玩家不用看伤害数字就能凭“放箭那一下的手感”分辨出这一箭的分量。

---

## 四、面向万象（MYRIAD）纯无手第一人称的直接落地原则

结合 Map #72 已确立的前提（纯无手第一人称、竞技爽快凌厉优先、不触碰服务端判定、首批三标杆为**天刑·雷纹长剑**、**铆钉·冲击锤**、**落霞·归霞弓**），将上述一手规律转译为可直接指导后续原型与规格的六条工程原则（含针对“纯无手”场景的工程推演）：

1. **建立统一的“视野安全区 + 独立纵深透视”红线**
   - 任何武器在待机与常态移动中，主体均不得侵入屏幕中心准星周围核心观察区；待机均须具备明确的**待机纵深角**（正值，尖端指向景深），并默认配齐呼吸、移步 bob、带上限的转头滞后 sway（参考 Source SDK `CalcViewModelLag` 思路）与起落惯性，从根本上消除“贴屏立牌感”。
2. **纯无手必须引入“画外隐含支点（Virtual Pivot）”**（无手场景工程推演）
   - 既然没有手和前臂连接画面底边，所有挥砍、翻刃与回弹都必须围绕**位于镜头下方/侧后方的虚拟握持支点**做大半径弧线运动，而不是绕武器几何中心原地旋转；这样玩家大脑会自动脑补出画外的臂展与力矩，立刻恢复“握持感”。
3. **保住极速出招（~0.85 tick 到 `HIT`），把大开大合做在“跨屏弧面 + 短顿挫 + 过冲回弹”**
   - 严守竞技瞬时响应，不拉长到**普攻接触姿态（HIT）**的起手时间；在起手极短窗口内注入微小反向借势，随即用陡峭速度曲线打出横跨/斜穿屏幕中心带的大弧面，在接触点后设置武器分化长度的短顿挫与过冲收势，并用弹簧曲线回位。
4. **连段强制“翻刃 + 左右交替动画链 + 可打断收招”**
   - 连续普攻不得每次重置回右下待机位再重复同一轨迹；前一段过冲收势直接作为下一段的起手势能，通过轴向翻刃衔接左右交替的大弧面扫击（并在收招早期开放打断切换）。
5. **首批三标杆原型的差异化设计抓手**
   - **天刑·雷纹长剑**：待机右下偏外、明显正**待机纵深角**、刃面斜露锋芒；挥砍主打**跨屏大斜弧 + 干脆翻刃 + 极短脆顿挫**，强调凌厉剑意与连段流畅度。
   - **铆钉·冲击锤**：待机压低重心、大锤头让出中上视野、更低频/沉稳的滞后与移步下沉；挥击主打**更大隐含支点半径、更猛烈的跨屏横扫/斜砸过冲、更显著的接触短顿挫与沉重回拉感**，拉开与长剑的吨位差。
   - **落霞·归霞弓**：待机大斜角让出中轴视野；拉弓沿纵深呈现**非线性拉距 + 弓臂形变 + 箭簇回缩瞄准**，满弓给“锁点微顿 + 低幅高频微颤（不偏真实准星）”，放箭按轻/满蓄/技能分级做**向前瞬释挫动 + 弹簧回稳 + 利落补箭**，并用不同弓身倾角直觉区分各技能形态。
6. **镜头微联动严守“绝不偏移真实准星 + 可关闭”边界**
   - 参考 *Destiny*、*Mirror's Edge* 与 Source 引擎 10% 抖动衰减原则，任何挥砍/重锤落地/满蓄放箭的镜头微滚转、微俯仰或 FOV 脉冲都只作极克制的视觉冲击层，绝不干扰准星落点，且预留客户端总开关。

### 反模式清单（原型验收应直接否决的陷阱）

- ❌ **立牌待机**：武器近乎垂直平行于屏幕平面（待机纵深角接近 0°），或完全刚性粘在镜头上无转头滞后。
- ❌ **原地转风车**：绕武器中心点旋转做挥砍，缺少画外隐含支点与跨屏位移，显得小气且无力。
- ❌ **匀速滑动**：从待机→`HIT`→收招使用线性或近似匀速插值，缺乏“微蓄 → 瞬爆 → 顿挫 → 弹性回位”的速度反差。
- ❌ **木板拍击**：挥砍过程中刃面不沿轨迹切向对齐，或连段反向回扫时直接倒放不翻刃。
- ❌ **竖弓劈屏**：弓箭待机或拉弓时竖直横亘在屏幕中线附近，遮挡水平追瞄视野。
- ❌ **重型延迟化**：试图用拉长按键到 `HIT` 的响应时间来“演重器”，破坏竞技手感的即时响应底线。

---

## 五、一手参考来源汇总

[^1]: Matthew Boehm (Blizzard Entertainment), *Animation Bootcamp: The First Person Animation of Overwatch*, GDC 2017 — 官方 GDC YouTube 录像：<https://www.youtube.com/watch?v=7t0hLZd_8Z4>（另参 David Gibson, *Animation Bootcamp: Overwatch: How A Hero Is Mei-d*, GDC 2016, GDC Vault：<https://www.gdcvault.com/play/1023473/Animation-Bootcamp-Intro-and-Overwatch>）
[^2]: Valve Corporation, *Team Fortress 2 Official Wiki — Viewmodels*（记录 `viewmodel_fov` 54°–70° 范围及 Meet Your Match 引入 `tf_use_min_viewmodels` 降低占屏遮挡的设计）：<https://wiki.teamfortress.com/wiki/Viewmodels>
[^3]: Valve Corporation, `ValveSoftware/source-sdk-2013` 公开源码 `src/game/shared/tf/tf_viewmodel.cpp`（`tf_use_min_viewmodels`、`GetViewmodelOffset()` 边角偏移与转头滞后插值）：<https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/shared/tf/tf_viewmodel.cpp>
[^4]: David Helsby (Bungie), *The Art of First Person Animation for Destiny*, GDC 2015 — GDC Vault：<https://www.gdcvault.com/play/1022298/The-Art-of-First-Person>
[^5]: Valve Corporation, `ValveSoftware/source-sdk-2013` 公开源码 `src/game/client/view.cpp`（独立 `viewmodel_fov` 默认 54° / 菜单范围 54°–70° 与宽屏缩放 `ScaleFOVByWidthRatio`）：<https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/client/view.cpp>
[^6]: DICE, *Creating First-Person Movement for Mirror's Edge*, GDC 2009 — GDC Vault：<https://www.gdcvault.com/play/1387/Creating-First-Person-Movement-for>
[^7]: Valve Corporation, `ValveSoftware/source-sdk-2013` 公开源码 `src/game/shared/baseviewmodel_shared.cpp`（`CalcViewModelLag` 滞后上限 `g_fMaxViewModelLag = 1.5f`、自适应追赶与 `ApplyShake(..., 0.1)` 10% 镜头抖动衰减）：<https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/shared/baseviewmodel_shared.cpp>
[^8]: Steve Swink, *Game Feel: The Secret Ingredient*, Game Developer：<https://www.gamedeveloper.com/design/game-feel-the-secret-ingredient>
[^9]: Jonathan Cooper, *The 12 Principles of Animation in Video Games*（*Game Anim: Video Game Animation Explained* 专文节选）, Game Developer：<https://www.gamedeveloper.com/production/the-12-principles-of-animation-in-video-games>
[^10]: Mariel Cartwright (Lab Zero Games), *Making Fluid and Powerful Animations For 'Skullgirls'*, GDC 2014 — 官方 GDC YouTube 录像：<https://www.youtube.com/watch?v=Mw0h9WmBlsw>
[^11]: Masahiro Sakurai (樱井政博), *Breaking Down Attack Animations [Animation]*, *Masahiro Sakurai on Creating Games* 官方频道：<https://www.youtube.com/watch?v=LewXWM7HDd8>
[^12]: Masahiro Sakurai (樱井政博), *Stop for Big Moments! [Design Specifics]*, *Masahiro Sakurai on Creating Games* 官方频道：<https://www.youtube.com/watch?v=OdVkEOzdCPw>
[^13]: Masahiro Sakurai (樱井政博), *Eight Hit Stop Techniques [Design Specifics]*, *Masahiro Sakurai on Creating Games* 官方频道：<https://www.youtube.com/watch?v=tycbMSjDDLg>
[^14]: Morris (Lead Animator, Fatshark), *Dev Blog - Animating the Grail Knight*, *Warhammer: Vermintide 2* 官网（第一人称长剑 HEMA 架势、刃面朝向、左右对称连段动画链与设计师动作库解耦）：<https://www.vermintide.com/news/dev-blog-animating-the-grail-knight>
[^15]: Blizzard Entertainment, *Overwatch Patch Notes*（官方英雄武器节奏与平衡参数归档）：<https://overwatch.blizzard.com/en-us/news/patch-notes/>
[^16]: Valve Corporation, `ValveSoftware/source-sdk-2013` 公开源码 `src/game/shared/tf/tf_weaponbase_melee.cpp`（挥动表现 `Swing` 与命中结算 `Smack` 时点解耦、延迟补偿与命中包围盒）：<https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/shared/tf/tf_weaponbase_melee.cpp>
[^17]: Valve Corporation, `ValveSoftware/source-sdk-2013` 公开源码 `src/game/shared/tf/tf_weapon_compound_bow.cpp`（Huntsman 拉弦动画、拉弦音高随攻速缩放、1 秒满蓄 / 5 秒疲劳抖动切换、取消收弓 `LowerBow`）：<https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/shared/tf/tf_weapon_compound_bow.cpp>
[^18]: Valve Corporation, *Team Fortress 2 Official Wiki — Huntsman*（蓄力时长、箭速/重力曲线与取消蓄力机制）：<https://wiki.teamfortress.com/wiki/Huntsman>
[^19]: Respawn Entertainment / EA, *Apex Legends* Bocek Compound Bow 设计机制参考（Legacy 更新引入复合弓满蓄节拍与低散射腰射定位；注：原 Legacy 补丁说明页面已由 EA 站内迁移，本文仅采信其已公开的复合弓蓄力节拍机制共识，未引用失效旧链）。
