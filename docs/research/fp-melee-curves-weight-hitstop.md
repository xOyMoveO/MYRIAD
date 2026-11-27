# 高打击感第一人称近战的挥砍曲线、重量分化与顿挫机制调研

> 关联工单：[xOyMoveO/MYRIAD#74](https://github.com/xOyMoveO/MYRIAD/issues/74)（隶属总图 #72《第一人称无手姿势与动画体系规划》）  
> 调研范围：一手权威来源（*Warhammer 40,000: Darktide* / *Vermintide 2* 官方 Lua 逻辑源码、*Chivalry 2* 官方解包帧数据、GDC 演讲、樱井政博官方设计频道、*The Orange Duck* 弹簧数学推导、Xbox 无障碍规范 XAG 117 及 VIMS 同行评审论文），并对照《万象》（MYRIAD）现有第一人称近战实现给出可落地的工程参数与安全红线。

---

## 核心结论速览

1. **打击感不是“均匀挥过去”，而是“强速度对比 + 命中停顿 + 速度连续的过冲回弹”**：行业标杆第一人称近战将挥砍拆为 **蓄势（Windup / Anticipation）→ 爆发挥砍（Active / Release）→ 接触顿挫（Hitstop / Stickyness）→ 顺势收尾（Follow-Through）→ 回位沉降（Recovery）** 五段。万象当前共用 `MeleeStrokeTiming.HIT = 0.85 tick`（42.5 ms）的线性分段时钟与关键帧间 `smoothstep`，导致轻重武器蓄势完全趋同、内部关键帧速度归零发黏，且缺少普攻顿挫。
2. **重量分化靠时间斜率、顿挫长度与惯性沉降，绝不靠拖慢视角输入**：*Chivalry 2* 与 *Darktide* 的真实数据表明，从轻刃到重锤，前摇拉长 `1.7×–3.8×`、有效挥砍窗口同步拉宽、顿挫与黏滞感显著分层，但玩家视角响应保持实时；无手/独立武器视图模型完全可以通过局部时间轴顿挫与欠阻尼弹簧过冲做出强烈的质量差。
3. **第一人称镜头微联动必须极度克制，且严禁偏移真实准星**：反直觉的一手源码事实是——Fatshark 在 *Vermintide 2* 与 *Darktide* 中刻意将常规近战命中的通用镜头抖动振幅设为 **`0`**，仅在打中重甲弹刀时给 `0.03` 极轻高频微震，把大部分“拳感”交给第一人称武器骨架顿挫与音效。结合 VIMS 多轴旋转实验与 XAG 117 标准，万象的镜头微联动应只作用于渲染矩阵（准星逻辑零偏移），且遵守 **Roll ≤ 1.5°（红线 2.0°）、命中微震 Pitch/Yaw ≤ 1.0°、FOV 脉冲仅限重击/终结技且 ≤ 3.0°（红线 4.0°）、提供 0%–100% 独立开关**。

---

## 一、时间分布、非线性曲线与接触顿挫机制

### 1. 五阶段时间分布的一手标杆数据

#### 1.1 竞技拟真标杆：*Chivalry 2*（Torn Banner Studios）

社区直接从 *Chivalry 2* 游戏文件 `AbilitiesOverride.json` 提取的武器动作表（单位：毫秒）清晰展示了第一人称近战在 **Windup（蓄势）→ Release（有效挥砍）→ Recovery（未命中收招/归位）** 以及 **Combo（连段可切入窗口）** 与 **Thwack（命中后顿挫/硬直衔接）** 上的时间占比[1]：

| 武器（代表层级） | 招式 | Windup 蓄势 (ms) | Release 有效挥砍 (ms) | Recovery 全收招 (ms) | 完整未连段周期占比 (W : R : Rec) | Combo 连段切入 (ms) | Thwack 命中收束 (ms) |
| :--- | :--- | ---: | ---: | ---: | :---: | ---: | ---: |
| **Dagger（轻匕首）** | Light Slash | 100 | 400 | 750 | `8% : 32% : 60%` | 250 | 400 |
| **Rapier（细剑）** | Light Slash | 120 | 425 | 750 | `9% : 33% : 58%` | 200 | 900 |
| **Longsword（标准长剑）** | Light Slash | 225 | 475 | 800 | `15% : 32% : 53%` | 150 | 900 |
| **Longsword（标准长剑）** | Heavy Slash | 475 | 500 | 950 | `25% : 26% : 49%` | 150 | 950 |
| **Greatsword（重长剑/大刀）** | Light Slash | 325 | 550 | 1100 | `16% : 28% : 56%` | 175 | 1000 |
| **Greatsword（重长剑/大刀）** | Heavy Slash | 575 | 550 | 1200 | `25% : 24% : 52%` | 175 | 1200 |
| **Maul（双手重锤）** | Light Slash | 375 | 550 | 1150 | `18% : 27% : 55%` | 250 | 1450 |
| **Maul（双手重锤）** | Heavy Slash | 675 | 600 | 1300 | `26% : 23% : 50%` | 250 | 1550 |

> **关键读数**：即使在 *Chivalry 2* 这样强调拖刀（Drag）的游戏里，**收招与归位（Follow-Through + Recovery）始终占据完整挥砍周期的 50%–60%**，这印证了樱井政博的判断——没有足够份量的收尾与回位帧，挥砍就会像“没有重量的抽搐”[2]。连招之所以流畅，是因为玩家在 `Combo` 窗口（收尾前段 `150–250 ms`）就能提前衔接下一刀，用“顺势借力”跳过冗长的回正段。

#### 1.2 动作割草与高打击感标杆：*Warhammer 40,000: Darktide*（Fatshark）

Fatshark 在 *Darktide* 的 Lua 武器模板中，用 `damage_window_start`（蓄势结束/判定开始）、`damage_window_end`（爆发挥砍结束）、`chain_time`（最早可衔接下一击的时间，即实际高频连招时的有效收尾时长）与 `total_time`（完整回正到待机姿态的总时长）四组时间锚点控制节奏[3][4][5][6]：

| 武器模板（`Darktide` 一手 Lua 源码） | 代表招式 | 蓄势 `damage_window_start` (s) | 有效挥砍窗口 `end - start` (s) | 连招切入 `chain_time` (s) | 完整归位 `total_time` (s) | 连招周期内占比 (蓄势 : 爆发 : 收尾) | 命中动画 / 顿挫机制 |
| :--- | :--- | ---: | ---: | ---: | ---: | :---: | :--- |
| **Combat Knife** (`combatknife_p1_m1.lua`) | `action_left_light` | 0.24 | 0.11 (`0.24–0.35`) | 0.40 | 2.00 | `60% : 28% : 12%` | `first_person_hit_stop_anim = "attack_hit"` |
| **Combat Sword** (`combatsword_p1_m1.lua`) | `action_left_light` | 0.31 | 0.09 (`0.31–0.40`) | 0.40 | 2.00 | `78% : 22% : 0%*` | `hit_stop_anim = "hit_stop"` |
| **2H Chainsword** (`chainsword_2h_p1_m1.lua`) | `action_left_light` | 0.27 | 0.20 (`0.27–0.47`) | 0.60 | 1.30 | `45% : 33% : 22%` | `hit_left_shake` + `hit_stop` |
| **2H Chainsword** (`chainsword_2h_p1_m1.lua`) | `action_left_heavy`（蓄力释放后） | 0.17 | 0.20 (`0.17–0.37`) | 0.45 | 1.50 | `37% : 44% : 19%` | `hit_stickyness`（锯齿咬肉 `0.30s` / 特效锯 `1.00s`） |
| **2H Thunder Hammer** (`thunderhammer_2h_p1_m1.lua`) | `action_left_down_light` | 0.40 | 0.075 (`0.40–0.475`) | 0.55 | 2.00 | `73% : 14% : 13%` | `hit_left_shake` + `attack_hit` / `attack_hit_power` |
| **2H Thunder Hammer** (`thunderhammer_2h_p1_m1.lua`) | `action_left_heavy`（蓄力释放后） | 0.33 | 0.20 (`0.33–0.53`) | 0.65 | 1.75 | `51% : 31% : 18%` | `hit_left_shake` + `attack_hit_power`（重击强顿挫） |

*\*注：`Combat Sword` 轻击可在 `0.40s` 立刻接下一刀（靠连段反手动画自然承接收尾），若停手则完整回正持续至 `2.00s`；重击（`heavy`）的 `damage_window_start` 是“松开蓄力后”的爆发前摇，此前还包含 `action_melee_start_*` 的抬手蓄力段。*

> **核心发现**：在高频第一人称动作战斗中，**有效挥砍（Active Swing）在视觉上极其短促凌厉（`75–200 ms`，约占连招周期的 `15%–33%`）**，前方的蓄势压缩与后方的顺势延展/慢速回正形成了巨大的速度反差。

---

### 2. 非线性动画曲线：为什么纯线性分段 + `smoothstep` 会“发软、发黏”？

#### 2.1 一手理论依据
- **樱井政博《Breaking Down Attack Animations》**[2]：明确指出纯线性插值或平均分配时间会让攻击显得像“机械臂均匀平移”。高打击感攻击必须压缩从蓄势顶点到命中点的帧数（甚至跳帧式高速掠过），把更多帧留给**蓄势蓄能端**与**命中后的收尾姿态停留**。
- **Squirrel Eiserloh（GDC 2015《Fast and Funky 1D Nonlinear Transformations》）**[7]：系统拆解了 `[0, 1]` 归一化区间的低开销代数曲线构造，核心区分了三类基本原语：
  - `SmoothStartN(t) = t^N`（强加速，适合蓄势末端→挥砍爆发，斜率由 0 陡增至 $N$）；
  - `SmoothStopN(t) = 1 - (1 - t)^N`（强急刹，适合命中瞬间→顺势收尾，初速度极高、末端渐停）；
  - `SmoothStep3(t) = 3t^2 - 2t^3`（两端导数均为 0，适合静态开关过渡，**不适合串联在高速挥砍的中间关键帧之间**）。
- **Daniel Holden（*The Orange Duck*《Spring-It-On》）**[8]：给出了解析形式（无帧率依赖、无欧拉爆炸）的**阻尼弹簧（Damped Spring）**与**临界阻尼惯性化（Critically Damped Inertialization）**公式。相较于定长缓动函数：
  - **欠阻尼弹簧（Under-damped, $\zeta \in [0.45, 0.75]$）**天然产生一次物理可信的**顺势过冲 + 微幅回弹**，完美模拟重武器挥停时手腕/惯性吸收动量的过程；
  - **临界阻尼弹簧（Critically damped, $\zeta = 1.0$）**在最短时间内零振荡收敛回目标姿态，适合轻武器快速收招与连段打断时的速度连续过渡（不发生位置跳变或动量清零）。

#### 2.2 对照《万象》（MYRIAD）现有实现的精准诊断

检查当前仓库代码，可以清晰定位“手感不够犀利、武器重量分化弱”的三个根因：

1. **统一写死的线性三段时钟（`MeleeStrokeTiming.java`）**：
   - 无论轻匕首还是重刀/重锤，全部硬编码 `HIT = 0.85 tick`（`42.5 ms`）、`END_OF_CUT = 2.35 tick`（`117.5 ms`），且 `path(age, hit, end, duration)` 对三段均做**纯线性比例映射**。
   - 结果：从点击到命中永远只有 42.5 ms（在 60 FPS 下仅 2.5 帧），**几乎不存在肉眼可读的蓄势期**；而 2.35 tick 之后长达 `10–20 tick` 的时间全被线性摊平在缓慢回正上。
2. **关键帧间分段 `smoothstep` 导致“内部急停”（`NightDaggerMotion.java`、`ThunderSwordMotion.java`）**：
   - 两者的 `sample()` 对相邻关键帧统一套用 `u = u*u*(3-2*u)`。因为 `smoothstep` 在 $u=0$ 和 $u=1$ 处导数恒为 0，武器每经过一个中间关键帧（例如蓄势顶点 → 挥砍中段 → 斩击终点）都会**强行减速到 0 再重新加速**，产生类似“橡皮筋拉扯 / 分节蠕动”的断续感，彻底抹平了挥砍中段的瞬时切割峰值速度。
   - `FlameDaoMotion.java` 已改用三次 Hermite 样条（`2u^3 - 3u^2 + 1` 等）改善了中间关键帧的速度连续性，但因仍绑定 `MeleeStrokeTiming.path` 的固定线性时钟，缺乏蓄势蓄压、爆发陡坡与命中顿挫；`RivetWeaponMotion.java` 是目前唯一在 `[0.38, 0.85] tick` 使用 `falling(t) = t^2` 并在技能命中（`CHARGE_HIT`）加入 4 tick 正弦微震（`y - 0.018`, `z + 0.045`, `pitch + 3°`）的武器，但普通平 A 依旧缺失蓄势时长差与普攻顿挫。
3. **无普攻接触顿挫（Hitstop）与顺势弹簧过冲**：
   - 当前所有近战客户端表现层均为“纯开环播放”（Fire-and-Forget），命中实体与挥空在时间轴上毫无差异，导致“砍人和砍空气手感相同”。

#### 2.3 推荐给万象的五阶段曲线架构（适配 Minecraft 20 tps + 权威即时判定）

> **关键约束适配**：《万象》近战普攻由服务端/原版 `Player.attack` 即时结算（点击瞬间即提交），客户端不能拖慢真实攻击判定。因此视觉层可采取**“视觉前摇极速压缩 + 首帧微反冲蓄势（或连段预蓄势）+ 爆发高次曲线 + 局部 Hitstop + 过冲弹簧回位”**策略：

| 阶段 | 典型时长占比（单次挥砍视效周期内） | 推荐数学曲线 | 视觉与物理意图 |
| :--- | :---: | :--- | :--- |
| **1. 蓄势（Anticipation）** | `10%–22%`（轻刃短、重锤长；连段时复用上一刀收尾做反向借力） | `SmoothStop2`：$1-(1-t)^2$（快速拉起后在顶点略作悬停蓄压） | 制造反向位移/抬刃，给眼睛建立“蓄能方向”的预期[2][7] |
| **2. 爆发挥砍（Active Swing）** | `12%–20%`（绝对时长仅 `65–140 ms`，速度最猛） | `SmoothStart3` 或 `SmoothStart4`：$t^3 / t^4$（严禁两端减速的 `smoothstep`） | 刃尖角速度在接触点前后达到峰值，配合刀光拖尾形成凌厉斩切感[2][7] |
| **3. 接触顿挫（Hitstop）** | 仅命中触发：`25–95 ms`（局部时间轴冻结/0.05×–0.15× 慢放） | 时间尺度缩放 `dt_weapon *= s` + 高频局部微振（见下节） | 仅作用于**武器视图模型时间轴**与粒子/音效，绝不冻结世界或玩家视角输入[3][9][10] |
| **4. 顺势收尾（Follow-Through）** | `18%–25%`（紧接 Active，初速度与 Active 末端连续） | `SmoothStop3`：$1-(1-t)^3$ 或欠阻尼弹簧（$\zeta \in [0.55, 0.85]$） | 体现武器质量与惯性余势，重武器越过斩击终点产生明显过冲与小幅回弹[8] |
| **5. 回弹归位（Recovery / Settle）** | `35%–48%`（可被下一刀连段打断，用速度连续切换衔接） | 临界阻尼弹簧（$\zeta = 1.0$）或 `SmoothStep3` 叠加末端重力沉降 | 平滑回正到 Idle；重武器可先“低位沉降”再分拍提回待机位[8] |

---

### 3. 接触顿挫（Hitstop / Impact Pause）的一手设计法则

结合樱井政博《Stop for Big Moments!》[9]、《Eight Hit Stop Techniques》[10] 以及 Fatshark *Darktide* `action_sweep.lua`[11] 与 *Vermintide 2* `action_sweep.lua`[12] 的生产级代码，可提炼出多人第一人称近战顿挫的五条铁律：

1. **绝不冻结全局时钟，只做局部视图模型时间缩放**：单机格斗游戏可冻结整帧，但多人联机第一人称（如 *Darktide* / *Vermintide 2* 与 Minecraft 多人对战）不能停滞世界时钟或锁定玩家鼠标[3][11]。正确做法是**仅在本地客户端暂停/大幅降速第一人称武器动画时间轴（`anim_time_scale → 0.05–0.15`）**，并触发专属的命中回弹动画事件（`first_person_hit_stop_anim` / `hit_left_shake`）[11]。
2. **顿挫期必须伴随受击/刃身微振（Micro-Jitter），而非死板卡帧**：樱井政博在《Eight Hit Stop Techniques》中特别强调，完全静止的顿挫容易让玩家误以为掉帧或卡顿[10]；在顿挫窗口内给武器施加极小幅、高频的切向/法向抖动（例如刃身偏移 `0.008–0.022` 单位、roll 微抖 `1°–3°`），并配合清脆命中音效与火花，能立刻将“卡顿感”转化为“斩肉/砸甲的物理阻力感”。
3. **按材质/受击结果分级顿挫**：*Darktide* 在 `action_sweep.lua`（L1646–1694）里明确区分了四种命中反馈分支[11]：
   - 挥空（Whiff）：无顿挫，完整走顺势收尾；
   - 斩穿脆肉/小怪（Cleave Through）：极短顿挫或仅减速（维持割草流畅度）；
   - 斩停/满质量阻挡（`abort_attack`）：触发标准 `first_person_hit_stop_anim`；
   - 砸中重甲/护盾弹刀（`armor_aborts_attack`）：触发专属 `hit_armor_anim` 反向弹回 + 唯一允许的轻度高频镜头微震（`swing_impact_no_damage_shake`）[11][13]。
4. **重型特化武器可用“黏滞/咬肉顿挫（Hit Stickyness）”放大暴力感**：*Darktide* 的双手链锯剑在 `chainsword_2h_p1_m1.lua` 中为重击配置了 `hit_stickyness_settings_heavy`（`duration = 0.30s`、`min_sticky_time = 0.25s`、`start_anim_event = "attack_hit_stick"`、`stop_anim_event = "yank_out"`），并由 `action_sweep.lua`（L872–893）每帧把受击点相对视角的投影写入 `hit_stop_delta_x / hit_stop_delta_y`，让武器像真的“嵌进目标里再拔出来”[5][11]。万象中若有重锤猛砸、长刀重劈或蓄力终结技，可复用这一“嵌入停留→拔出回位”的视觉语言。
5. **多目标顺劈（Cleave）不叠加全额顿挫**：若一刀扫中 3 个目标，只对首个高权重目标（或护甲/格挡目标）触发一次顿挫，或后续目标按 `0.3×` 递减并设上限（例如单次挥砍累计顿挫 ≤ `100 ms`），避免群战时手感粘滞。

---

## 二、武器重量层级的可量化分化

### 1. 一手核心原则：重量感 = 惯性表达，≠ 操作迟钝

Arkane Studios（*Dark Messiah of Might and Magic* / *Dishonored*）创始人 Raphaël Colantonio 在 GDC 2007 演讲中指出，第一人称近战最大的困境是玩家看不见自己的躯干与重心，必须通过**向画面中心适度夸张的武器运动轨迹**和清晰的受击反馈来弥补深度与质量感知[14]。同时，Fatshark 在 *Vermintide 2 / Darktide* 中验证了另一条黄金准则：
- **重量感必须由武器视图模型的加速斜率、过冲回弹、停顿长度和回位沉降来承担**，而绝不能靠给鼠标转向或按键响应增加粘滞延迟来“硬凑沉重感”。
- 需注意 *Chivalry 2* 在挥砍期施加了 `turnLimitStrength`（轻匕首 `40` → 长剑 `45` → 大剑 `50–52.5`）[1] 以平衡 PVP 拖刀竞技性；万象属于高机动动作化 Minecraft 战斗，**不应限制玩家自由转头**，而应改为让**武器视图模型自身带有随重量递增的旋转跟随滞后（Sway / Lag）**来体现惯性。

### 2. 四级武器重量量化参数矩阵（面向《万象》无手武器体系）

基于 *Chivalry 2*（Dagger/Rapier → Longsword → Messer/Greatsword → Warhammer/Maul）[1] 与 *Darktide*（Combat Knife → Combat Sword → 2H Chainsword → 2H Thunder Hammer）[3][4][5][6] 的比例规律，映射至万象四档武器层级（兼容 Minecraft 20 tps 逻辑与 60–144 FPS 客户端渲染插值）：

| 量化维度 | T1：轻刃 / 细剑<br>*(夜刃、短匕、刺剑)* | T2：标准长剑<br>*(天行、制式单手/手半剑)* | T3：重长刀 / 大剑<br>*(焚诀、长柄斩刀、双刃大刀)* | T4：重锤 / 巨斧<br>*(铆钉重锤、破甲钝器)* |
| :--- | :--- | :--- | :--- | :--- |
| **竞品参照基准** | *Chiv2* Dagger/Rapier（W 100–120 ms）；*Darktide* Combat Knife（start 240 ms） | *Chiv2* Longsword（W 225 ms）；*Darktide* Combat Sword（start 310 ms） | *Chiv2* Messer/Greatsword（W 325 ms）；*Darktide* 2H Chainsword（end-start 200 ms） | *Chiv2* Maul（W 375–675 ms）；*Darktide* Thunder Hammer（start 350–433 ms） |
| **视觉蓄势占比 / 绝对时长**<br>*(在保持即时点击响应下，首刀压缩；重击/连段可扩展)* | `8%–12%`<br>普攻 `30–45 ms`（0.6–0.9 tick） | `12%–16%`<br>普攻 `45–65 ms`（0.9–1.3 tick） | `16%–20%`<br>普攻 `65–95 ms`（1.3–1.9 tick） | `20%–26%`<br>普攻 `85–125 ms`（1.7–2.5 tick）；重击可延展至 `220+ ms` |
| **爆发挥砍曲线与速度斜率** | `SmoothStart2`（$t^2$）<br>爆发段 `60–80 ms`，切入干脆、匀速感略高、几乎无拖重 | `SmoothStart3`（$t^3$）<br>爆发段 `75–95 ms`，中段明显鞭梢加速，峰值速度约为均速 `3×` | `SmoothStart3.5`（$t^{3.5}$）<br>爆发段 `95–120 ms`，起步沉稳、越过中线后猛烈撕裂 | `SmoothStart4`（$t^4$）<br>爆发段 `110–145 ms`，前半段明显克服惯性，临近落点瞬间砸落（类似 `RivetWeaponMotion` 已有 `t^2` 的强化版） |
| **局部 Hitstop 顿挫时长**<br>*(仅武器视图模型时间轴)* | 轻击命中：`20–35 ms`（1–2 帧 @60fps）<br>暴击/背刺：`45–55 ms`<br>时间缩放：`0.15×` | 普攻命中：`35–50 ms`（2–3 帧 @60fps）<br>收尾段/重击：`60–75 ms`<br>时间缩放：`0.10×` | 普攻命中：`50–70 ms`（3–4 帧 @60fps）<br>重劈/破防：`80–95 ms`<br>时间缩放：`0.08×` | 普攻命中：`65–85 ms`（4–5 帧 @60fps）<br>蓄力重砸/震地：`95–120 ms`<br>时间缩放：`0.05×` + `0.02` 刃头高频微振 |
| **顺势过冲幅度 & 阻尼比 $\zeta$**<br>*(过冲量 = 超出目标斩击终点的角度/位移比例)* | 极小过冲：`3%–6%`<br>$\zeta = 0.90–1.00$（近临界阻尼，刹停干脆利落、无回摆） | 适中过冲：`8%–12%`<br>$\zeta = 0.78–0.85$（刃尖略微甩出后被腕力拉稳） | 明显过冲：`14%–20%`<br>$\zeta = 0.65–0.75$（可见长刃惯性拉扯，带一次轻微回弹） | 强烈过冲：`20%–28%`<br>$\zeta = 0.55–0.65$（锤头惯性下坠压低，明显一次反震回弹后才稳住） |
| **回位沉降与收招姿态特征** | **直线快收**：直接沿最短弧线回待机位，无下沉停顿，连段切入极早 | **弧线顺收**：顺斩击余势绕小弧回 Guard 位，收尾停顿 `40–60 ms` | **低位泄力→提刃回正**：先沉至下侧低位停顿 `70–100 ms`，再沿斜弧拉回待机位（体现质量搬运） | **两拍沉降回提**：锤头先沉降至画面下沿停顿 `100–140 ms`，回位前半段慢（克服重力起提），后半段归位落稳带 `1°–2°` 归位微沉 |
| **视角转动的武器惯性滞后（Sway）** | 极低滞后，半衰期 `35–45 ms` | 轻度滞后，半衰期 `55–70 ms` | 中度滞后，半衰期 `80–100 ms` | 显著滞后（转动视角时锤头明显晚半拍跟上），半衰期 `110–140 ms` |

> **关键设计检验**：如果把四类武器换成同一个白色方块模型、关掉全部音效，仅看第一人称运动轨迹，玩家依然应当能从**“起势悬停时间 + 中段爆发斜率 + 命中停顿长度 + 末端下沉回弹”**一眼分辨出轻刃、长剑、重刀与重锤。

---

## 三、第一人称极克制镜头微联动、晕动症风险与安全幅度红线

### 1. 一手源码与学界证据：为什么“少即是多”？

#### 1.1 Fatshark 源码中的反直觉真相（*Vermintide 2* & *Darktide*）
直接审阅 Fatshark 的镜头特效表会发现一个极易被二手文章误解的工程事实：
- 在 *Vermintide 2*（`scripts/settings/camera_effect_settings.lua` L1613–1660）[15] 与 *Darktide*（`scripts/settings/camera/camera_effect_settings.lua` L1381–1409）[13] 中，**全部 12 档常规近战命中（`swing_impact_{up,down,left,right}_{light,medium,heavy}`）的镜头震动均指向同一个零配置**：
  ```lua
  local swing_impact_shake = {
      amplitude = 0,
      duration = 0,
      octaves = 0,
      persistance = 0,
  }
  ```
- 只有**打在重甲上未造成伤害 / 弹刀（`swing_impact_*_no_damage`）**才启用极克制的高频微震：
  ```lua
  local swing_impact_no_damage_shake = {
      amplitude = 0.03,
      duration = 0.2,
      octaves = 12,
      persistance = 1,
  }
  ```
- 而在 FOV 控制上，*Darktide*（`camera_settings.lua` L587–639、`camera_transition_templates.lua`）[16][17] 保持常态 `vertical_fov = 65°`（16:9 屏约 `97°` 水平 FOV），仅在冲刺（`sprint`）与突进（`lunge`）时通过 `math.smoothstep` / `1-(t-1)^2` 平滑过渡到 `75°`，**普通近战挥砍并不反复抽拉 FOV**。
- **工程启示**：高频近战战斗中，玩家每秒可能挥砍 1.5–2.5 次；如果每一刀命中都触发显著全屏镜头抖动或 FOV 抽拉，几分钟内就会诱发视觉疲劳与晕 3D。真正持久耐玩的方案是**“武器视图模型扛 80% 打击表现，镜头只做 20% 极克制的轴向微呼吸与重击点缀”**。

#### 1.2 Squirrel Eiserloh 的 Trauma 镜头数学模型（GDC 2016）
Squirrel Eiserloh 在《Math for Game Programmers: Juicing Your Cameras With Math》[18] 中确立了现代游戏镜头微震的标准数学架构：
1. **Trauma 标量状态（`trauma ∈ [0, 1]`）**：每次受击或重击只叠加有限的 trauma 值并封顶于 `1.0`，随后随时间线性衰减至 `0`。
2. **非线性平方/立方映射（`shake = trauma^2` 或 `trauma^3`）**：轻度叠加（如连击普攻）映射出的真实镜头位移几乎不可见，只有重击/终结技的高 trauma 才产生短促有力的“一震即收”，且收尾极其顺滑，不会突然截断。
3. **1D Perlin / 平滑噪声替代白噪声**：逐帧调用 `random()` 会导致帧率越高抖动越碎、低帧率像抽搐；用按时间连续采样的 1D 平滑噪声可保证 60 FPS 与 144 FPS 下视觉体感完全一致。
4. **3D 第一人称优先微小角向微动，慎用大幅平移抖动**：Eiserloh 指出 3D 空间内大幅镜头平移（Translation）会破坏近景物体的视差锚定，更容易引起眩晕；极小角度的围绕视角原点微旋后迅速归零更安全[18]。

#### 1.3 视觉诱发晕动症（VIMS）的一手医学与无障碍红线
- **感觉冲突机理（LaViola, ACM SIGCHI 2000）**[19]：当视觉系统接收到未经玩家主动鼠标输入产生的视场加速度或地平线旋转，而前庭系统（内耳）报告头部静止时，便会触发视觉-前庭冲突，诱发恶心、眼疲劳与定向障碍。
- **多轴旋转与横滚（Roll）的强致晕效应（Keshavarz & Hecht, 2011, PubMed: 22097636）**[20]：受控实验直接比较了单轴（仅 Pitch）、双轴（Pitch + Roll）与三轴（Pitch + Roll + Yaw）视觉旋转，证实**一旦在 Pitch 基础上叠加 Roll 横滚形成双轴/三轴复合旋转，VIMS 晕动评分会显著跃升**。这意味着第一人称近战绝对不能在每次挥砍中同时叠加大幅 Roll + Pitch + Yaw 复合晃动。
- **平台无障碍强制规范（Microsoft XAG 117 & Game Accessibility Guidelines）**[21][22][23]：
  - **XAG 117（Visual Distractions and Motion Settings）**明确要求：尽可能避免非玩家主动触发的镜头晃动、头部晃动（Head Bob）与突发视场变化，**若使用则必须提供可完全关闭或调节强度的设置项**，同时提供可调节 FOV[21]。
  - **Game Accessibility Guidelines** 指出：保持屏幕中心**静态准星作为视觉固定锚点**，并消除控制器输入与镜头运动之间的非预期偏差，是抑制第一人称 3D 眩晕最有效的手段之一[22][23]。

---

### 2. 给《万象》的镜头微联动安全红线表（零准星偏移原则）

#### 架构红线（不可违背）
- **铁律 A（准星逻辑零偏移）**：所有挥砍微滚转、命中微震、FOV 脉冲**只能注入渲染管线的相机矩阵（Camera Render View / Projection）**，**绝不允许修改 `player.getXRot() / getYRot()`**。玩家的射线检测、攻击朝向与鼠标瞄准输入必须 100% 保持原样，且中心准星 UI 绝不跟随镜头震动偏移。
- **铁律 B（可完全关闭的无障碍滑杆）**：必须提供客户端选项「近战镜头反馈强度（`0%–100%`，可单独关停）」；当设为 `0%` 时镜头保持 100% 静止，但武器视图模型的非线性曲线、过冲回弹与局部 Hitstop 仍完整保留（遵循 XAG 117[21]）。
- **铁律 C（单轴主导，禁止多轴共振）**：同一帧内避免 Roll 与 Pitch/Yaw 同时达到高幅峰值（遵循 Keshavarz & Hecht 2011[20]）；水平横斩以极轻 Roll 为主、竖劈/砸地以极轻 Pitch 微点头为主。

#### 分项安全幅度与禁用清单

| 镜头微联动通道 | 推荐安全范围（100% 档默认上限） | 绝对安全红线（不可突破） | 时长与衰减曲线 | 适用场景与禁用做法 |
| :--- | :--- | :--- | :--- | :--- |
| **挥砍顺向微滚转（Roll）** | 轻刃：`0°–0.3°`<br>标准剑：`0.4°–0.8°`<br>重刀/重锤：`0.8°–1.4°` | **≤ 2.0°**（任何连击叠加封顶） | `100–180 ms`；跟随挥砍爆发快速到达峰值，用临界阻尼弹簧平滑归零 | **适用**：横向/斜向挥砍顺向微倾，模拟躯干发力扭矩。<br>**禁用**：蓄势反向大幅甩滚、持续超过 `250 ms` 的倾斜停留、连续轻击高频左右剧烈摇摆。 |
| **挥砍顺向微点头（Pitch）** | 轻刃：`0°`<br>标准剑：`0.2°–0.4°`<br>重锤/竖劈：`0.5°–1.0°` | **≤ 1.5°** | `90–150 ms`；落点瞬间轻点后快速弹性回正 | **适用**：竖劈下斩、重锤砸地；**绝不与 >1.0° Roll 同步叠满**。 |
| **命中微震（Trauma Shake）** | 轻击常规命中：`0°`（参照 *Darktide* 策略，交由武器视图模型抖动）<br>重击/暴击/弹刀：`0.3°–0.8°`（Pitch/Yaw） | **≤ 1.2°**（Trauma 封顶 `1.0`，按 $\text{trauma}^2$ 映射） | `80–140 ms`；1D 平滑噪声采样，指数/二次快速收敛 | **适用**：重击命中、破盾/弹刀、连段终结技。<br>**禁用**：每刀轻击全屏白噪声乱抖、持续性平移震动（Translation Shake）、遮挡/抖动准星。 |
| **短时 FOV 脉冲** | 普攻轻击：`0°`（禁止）<br>蓄力重击/技能终结：`+1.5° 至 +2.5°`（爆发外扩）或蓄力微收 `-1.0°` → 命中回弹 `+1.5°` | **≤ ±3.5°**（且每秒触发频次受冷却保护，不连发抽拉） | 爆发 `40–60 ms` 达峰，`140–220 ms` `SmoothStop2` 回稳 | **适用**：重击蓄力释放、冲刺突进斩、技能终结。<br>**禁用**：普通攻速下每刀 `±5°` 以上高频缩放（典型外周光流呕吐诱因）。 |
| **镜头平移微冲（Camera Push）** | 常规普攻：`0` 格<br>极重砸地/冲锋撞击：`≤ 0.03` 格（Z 轴前冲/Y 轴微沉） | **≤ 0.05 格** | `≤ 120 ms` 临界阻尼归零 | **禁用**：将常规移动/挥砍绑定持续 Head-Bob 上下晃动。 |

---

## 四、对《万象》第一人称无手动画体系的落地建议

结合工单 #72 总图规划，建议后续原型与架构设计直接吸收以下四点：

1. **淘汰单一固定时钟 `MeleeStrokeTiming`，改为数据驱动的“五段武器手感配置（`WeaponFeelProfile`）”**：
   - 保留服务端/原版权威攻击节奏不变，客户端为每档武器配置独立的 `windupFraction / activeCurvePower / hitstopMs / hitstopTimeScale / overshootDamping / recoverySettleMs / swayHalfLifeMs`。
2. **用 `SmoothStartN` + 解析阻尼弹簧替代关键帧间 `smoothstep`**：
   - 蓄势→爆发段采用非对称加速（$t^2 \sim t^4$），消除 `NightDaggerMotion` / `ThunderSwordMotion` 的内部关键帧速度归零停顿；
   - 爆发末端→收尾→归位引入 Daniel Holden 解析阻尼弹簧（欠阻尼过冲 + 临界阻尼归位与连段无缝接管），彻底解决快速连招切换时的姿态跳变与机械感。
3. **补齐“客户端局部 Hitstop 信号通道”**：
   - 普攻命中判定确认（或客户端本地接触反馈）时，触发 `20–95 ms` 的武器视图模型局部时间缩放与高频刃身微振，区分“挥空 / 命中 / 重击或格挡弹刀”三档体感。
4. **把镜头微联动做进独立、可归零的渲染后叠加层**：
   - 以 `trauma^2` + 1D 平滑噪声驱动极克制单轴微动，严格锁死在表三安全红线内，并从第一版起同步暴露「近战镜头微动强度（0%–100%）」选项，保证零准星偏移与低晕动风险。

---

## 参考来源（全部一手/可核查原始出处）

1. **Chivalry 2 官方解包武器帧数据（`AbilitiesOverride.json` 提取）**：
   - `Chiv2-Community/chivalry2-weapons` 仓库武器定义与毫秒表（`main` 分支）：
     - Dagger: https://github.com/Chiv2-Community/chivalry2-weapons/blob/main/src/weapons/dagger.json
     - Rapier: https://github.com/Chiv2-Community/chivalry2-weapons/blob/main/src/weapons/rapier.json
     - Longsword: https://github.com/Chiv2-Community/chivalry2-weapons/blob/main/src/weapons/longsword.json
     - Messer: https://github.com/Chiv2-Community/chivalry2-weapons/blob/main/src/weapons/messer.json
     - Greatsword: https://github.com/Chiv2-Community/chivalry2-weapons/blob/main/src/weapons/greatsword.json
     - Warhammer: https://github.com/Chiv2-Community/chivalry2-weapons/blob/main/src/weapons/warhammer.json
     - Maul: https://github.com/Chiv2-Community/chivalry2-weapons/blob/main/src/weapons/maul.json
   - 交互查询工具 Polehammer：https://polehammer.net
2. **Masahiro Sakurai（樱井政博）官方频道 *Masahiro Sakurai on Creating Games***：
   - *Breaking Down Attack Animations*：https://www.youtube.com/watch?v=LewXWM7HDd8
3. ***Warhammer 40,000: Darktide*（Fatshark）Lua 武器模板 — Combat Knife**：
   - https://github.com/Aussiemon/Darktide-Source-Code/blob/master/scripts/settings/equipment/weapon_templates/combat_knives/combatknife_p1_m1.lua
4. ***Warhammer 40,000: Darktide*（Fatshark）Lua 武器模板 — Combat Sword**：
   - https://github.com/Aussiemon/Darktide-Source-Code/blob/master/scripts/settings/equipment/weapon_templates/combat_swords/combatsword_p1_m1.lua
5. ***Warhammer 40,000: Darktide*（Fatshark）Lua 武器模板 — 2H Chainsword（含 `hit_stickyness` 定义）**：
   - https://github.com/Aussiemon/Darktide-Source-Code/blob/master/scripts/settings/equipment/weapon_templates/chain_swords_2h/chainsword_2h_p1_m1.lua
6. ***Warhammer 40,000: Darktide*（Fatshark）Lua 武器模板 — 2H Thunder Hammer**：
   - https://github.com/Aussiemon/Darktide-Source-Code/blob/master/scripts/settings/equipment/weapon_templates/thunder_hammers_2h/thunderhammer_2h_p1_m1.lua
7. **Squirrel Eiserloh（SMU Guildhall），GDC 2015**：
   - *Math for Game Programmers: Fast and Funky 1D Nonlinear Transformations*：https://www.youtube.com/watch?v=mr5xkf6zSzk
8. **Daniel Holden（*The Orange Duck*）**：
   - *Spring-It-On: The Game Developer's Spring-Roll-Call*（阻尼弹簧与临界阻尼惯性化解析解）：https://theorangeduck.com/page/spring-roll-call
9. **Masahiro Sakurai（樱井政博）官方频道**：
   - *Stop for Big Moments!*（Hitstop 核心作用）：https://www.youtube.com/watch?v=OdVkEOzdCPw
10. **Masahiro Sakurai（樱井政博）官方频道**：
    - *Eight Hit Stop Techniques*（顿挫微振与分级技法）：https://www.youtube.com/watch?v=tycbMSjDDLg
11. ***Warhammer 40,000: Darktide*（Fatshark）近战挥砍执行与顿挫/黏滞源码**：
    - `action_sweep.lua`（含 `hit_stop_delta_x/y`、`hit_stickyness`、`hit_armor_anim` 分级逻辑）：https://github.com/Aussiemon/Darktide-Source-Code/blob/master/scripts/extension_systems/weapon/actions/action_sweep.lua
12. ***Warhammer: Vermintide 2*（Fatshark）近战挥砍与顿挫源码**：
    - `action_sweep.lua`：https://github.com/Aussiemon/Vermintide-2-Source-Code/blob/master/scripts/unit_extensions/weapons/actions/action_sweep.lua
13. ***Warhammer 40,000: Darktide*（Fatshark）镜头抖动与序列配置源码**：
    - `camera_effect_settings.lua`（L1381–1409，`swing_impact_shake` 振幅为 `0`、`swing_impact_no_damage_shake` 为 `0.03`）：https://github.com/Aussiemon/Darktide-Source-Code/blob/master/scripts/settings/camera/camera_effect_settings.lua
14. **Raphaël Colantonio（Arkane Studios，*Dark Messiah of Might and Magic*），GDC 2007 讲稿报道**：
    - *GDC: Arkane's Colantonio Takes On First-Person Melee*（Game Developer / 原 Gamasutra）：https://www.gamedeveloper.com/design/gdc-arkane-s-colantonio-takes-on-first-person-melee
15. ***Warhammer: Vermintide 2*（Fatshark）镜头抖动配置源码**：
    - `camera_effect_settings.lua`（L1613–1660）：https://github.com/Aussiemon/Vermintide-2-Source-Code/blob/master/scripts/settings/camera_effect_settings.lua
16. ***Warhammer 40,000: Darktide*（Fatshark）第一人称基础 FOV 与状态配置**：
    - `camera_settings.lua`：https://github.com/Aussiemon/Darktide-Source-Code/blob/master/scripts/settings/camera/camera_settings.lua
17. ***Warhammer 40,000: Darktide*（Fatshark）FOV 平滑过渡曲线源码**：
    - `camera_transition_templates.lua`：https://github.com/Aussiemon/Darktide-Source-Code/blob/master/scripts/settings/camera/camera_transition_templates.lua
18. **Squirrel Eiserloh（SMU Guildhall），GDC 2016**：
    - *Math for Game Programmers: Juicing Your Cameras With Math*（Trauma 镜头震动模型）：https://www.youtube.com/watch?v=tu-Qe66AvtY
19. **Joseph J. LaViola Jr.（2000）**：
    - *A Discussion of Cybersickness in Virtual Environments*, *ACM SIGCHI Bulletin* 32(1): 47–56：https://doi.org/10.1145/333329.333344
20. **Behrang Keshavarz & Heiko Hecht（2011）**：
    - *Axis Rotation and Visually Induced Motion Sickness: The Role of Combined Roll, Pitch, and Yaw Motion*, *Aviation, Space, and Environmental Medicine* 82(11): 1023–1029：https://doi.org/10.3357/asem.3078.2011 （PubMed: https://pubmed.ncbi.nlm.nih.gov/22097636/）
21. **Microsoft — Xbox Accessibility Guideline 117**：
    - *Visual Distractions and Motion Settings*：https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/117
22. **Game Accessibility Guidelines — 默认与可调 FOV 规范**：
    - https://gameaccessibilityguidelines.com/if-the-game-uses-field-of-view-3d-engine-only-set-an-appropriate-default-for-the-expected-viewing-environment/
23. **Game Accessibility Guidelines — 消除控制器输入与镜头运动的非预期偏差**：
    - https://gameaccessibilityguidelines.com/avoid-or-provide-option-to-disable-any-difference-between-controller-movement-and-camera-movement/
