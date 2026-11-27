# 成熟 MOBA 与英雄对战游戏的统一数值平衡框架与属性估值方法调研

> **关联工单**：`xOyMoveO/MYRIAD#89`  
> **调研分支**：`research/balance-mature-game-frameworks`（调研产物，不合并至 `main`）  
> **适用对象**：《万象》（MYRIAD）全局数值架构、属性购买曲线与角色/技能分支平衡设计

---

## 摘要

本报告面向一级权威来源（Riot Games、Valve、Blizzard、Hi-Rez Studios 官方开发博客/补丁说明/GDC 演讲，官方 Wiki 数学机制文档，以及 Ian Schreiber、David Sirlin 的量化平衡理论），系统拆解成熟 MOBA 与英雄对战游戏如何统一治理全局数值平衡，并针对《万象》特有结构（**无传统装备、五属性按 $360 \times 1.2^n$ 指数递增购买 + 被动/主要/终极二选一互斥分支**）提炼数学约束与反模式清单。

核心结论可概括为三条架构原则：

1. **体验锚点必须先于单技能数值存在，且全局调优必须联动耦合系统**：成熟项目以分阶段 TTK（击杀耗时）、职业子原型（Subclass）胜负窗口与 PvE 节拍为顶层边界，再向下分配单英雄/单技能预算；一旦调整基础坦度或输出，必须同步修正治疗/护盾、野怪/防御塔与控制时长（如 LoL 12.10、SMITE 9.5、OW2 S9、Dota 2 7.33）。
2. **属性估值须区分“线性标尺”与“超线性陷阱”，非伤害机制按行动经济与可靠度折现入账**：攻击、法强、生命、双抗（EHP）、技能急速（Ability Haste）可用统一等效金币或预算分横向锚定；而硬控、软控、缓时、位移、射程、前摇命中率与条件触发必须通过**期望值折现、对手行动时间剥夺与可控度溢价**折算进同一强度预算（Power Budget），避免“机制免费白嫖”。
3. **指数成本曲线可抑制单属性无脑堆叠，但必须消除超线性属性并强制互斥分支维度正交**：$360 \times 1.2^n$ 的凸成本能与线性增益属性形成双重递减保护，但若遇到**固定百分比冷却缩减（CDR）**这类凸收益属性或**攻击多乘区 vs 防御加法成长**的剪刀差，中后期仍会崩坏；互斥技能分支必须遵循 Blizzard 的**主题分层（Themed Tiers）**与非传递情境分工，防止纯上位替代与“机制补齐税”。

---

## 一、成熟游戏一级实践对照表

| 维度 | Riot《英雄联盟》（LoL） | Valve《Dota 2》 | Valve《Deadlock》 | Blizzard《风暴英雄》（HotS） | Blizzard《守望先锋 2》（OW2） | Hi-Rez《SMITE》 | 腾讯天美《王者荣耀》（HoK） |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **顶层平衡治理** | **Champion Balance Framework**（Average/Skilled/Elite/Pro 四档位；任一超标即削，全档偏弱才加强；Pro 局看 Presence）[^riot-bf][^riot-bf-2020] | 围绕高水平/TI 生态的非对称强特色平衡，定期以大版本重置机制通胀（如 7.33 全面减控）[^dota-733] | 持续数据迭代的英雄射击+MOBA混合平衡；通过商店分类投资门槛平衡流派 | 无装备、全队共享等级；通过统一等级成长斜率 + 分层天赋树约束全期节奏[^hots-2015] | 按职责（Tank/Damage/Support）与段位胜率调优，赛季级系统重构对抗爆发/奶量极端化[^ow2-s9] | 基于胜率、投降率与对局时长监控，通过版本级全局底盘调整控制 TTK[^smite-95] | 大盘胜率/出场/禁用 + 经济差-胜率拟合，结合单属性等效金币体系维持跨定位对齐 |
| **成长与经济架构** | 等级成长 + 金币装备；2024 Split 3 削减传说装备 5–15% 性价比，把强度预算还给英雄本体[^riot-2024s3] | 三主属性 + 全才英雄 + 金币装备 + 天赋树 + 魔晶/A杖 | 灵魂（Souls）购买分档装备（Weapon/Vitality/Spirit 投资里程碑）+ Boon 等级成长 + 技能点分阶解锁[^dl-boon] | **无装备系统**：全英雄统一每级约 **+4% 指数成长**（$1.04^{L-1}$），1/4/7/10/13/16/20 级解锁互斥天赋[^hots-2015] | 局内无经济/装备成长，纯靠基础数值、技能冷却与终极充能成本控制对局曲线[^ow2-s9] | 等级基础属性 + 金币装备；强调基础属性与装备加成的比例控制[^smite-95] | 等级基础成长 + 金币装备 + 铭文前置属性 |
| **防御与 EHP 公式** | 双曲减伤 $\frac{R}{100+R}$，每 1 点抗性线性增加 **+1% 基础生命 EHP**[^lol-wiki-gold] | 7.27+ 护甲减伤 $\frac{0.06A}{1+0.06\|A\|}$，每 1 点护甲线性增加 **+6% 物理 EHP**；魔抗/闪避**独立乘算**[^dota-armor][^dota-mr] | 子弹/魂力抗性全部采用**独立乘算叠堆** $1-\prod(1-r_i)$，防止叠加逼近 100% 免疫[^dl-res] | 基础护甲直接按百分比减伤并设上下限（如 -25% ~ +75%），避免无装备下坦度无限叠加 | 区分白血、护甲（按固定/比例减免小额高频伤害）与可回复护盾，构造反制克制关系 | 双曲减伤 $\frac{P}{100+P}$，9.5 补丁全神明基础生命/物理抗性一次上调 **+40%**[^smite-95] | 双曲减伤 $\frac{R}{R+602}$，随护甲增高减伤率递减但 EHP 保持线性增长 |
| **冷却与移速约束** | 2021 季前赛用线性**技能急速（AH）**取代指数收益的 CDR；移速设 **415（×0.8）与 490（×0.5）双软上限**[^riot-s11][^lol-wiki-ms] | 冷却缩减多来源采用乘算/不叠加规则；移速有硬上限（默认 550，除非解除上限机制） | 冷却缩减、持续时间与范围增幅广泛采用乘算递减（Diminishing Returns）[^dl-res] | 冷却主要锚定在基础技能与特定天赋节点，禁止无限制堆叠通用冷却属性 | 技能冷却严格固定为设计师静态预算，不提供通用局内减 CD 购买 | 装备 CDR 设严格硬上限（40%），避免无窗口循环 | 装备冷却缩减设 40% 硬上限 |
| **控制与非伤害治理** | 区分韧性（Tenacity）可减控与击飞/压制等不可减控；强调 Skillshot 替代指向性控制提供反制[^riot-sub] | 状态抗性（Status Resistance）缩减减益时长；**7.33 补丁全域削减几乎所有控制时长**对抗控制膨胀[^dota-733][^dota-sr] | 控制多与高阶技能点或高价主动装备绑定，强调立体机动规避与打断窗口 | 区分硬控与软控，严格限制单英雄同时拥有高机动、高爆发与稳定硬控 | 逐步削减非坦克职责的硬控，以防低 TTK 叠加连控导致零交互挫败 | **9.7 补丁显性化 DR 系统**：15 秒窗口最多 4 层、每层 -20% 时长，并区分普通硬控（+1 层）与不可减免的 **True Hard CC**（+2 层）[^smite-97] | 韧性靴等反制手段 + 区分支配/击飞与常规眩晕 |
| **代表性全局 TTK 干预** | **12.10 Durability Update**：全英雄 +70 基础生命、+14 成长生命、+1.2 成长护甲、+0.8 成长魔抗，同步全面削弱治疗/护盾/重伤并加强防御塔[^riot-1210] | 7.20 改护甲公式并同步重调塔/兵/基地护甲；7.27 回调为 0.06 标准线；7.33 降控拉长交互窗口[^dota-720][^dota-727] | 持续按经济阶段重平衡 Weapon vs Spirit 成长斜率与抵抗穿透收益 | **2015.11 统一 4% 指数成长重构**：消除 1–10 级线性差值过大导致的早期滚雪球，同步重调兵线/野怪/建筑与复活时间[^hots-2015] | **Season 9（2024.02）**：全员 +25~+100 HP、全武器弹道变宽提高命中稳定性、输出位新增 **-20% 减疗被动**、全员终极充能 +10%[^ow2-s9] | **9.5 TTK Update（2022.05）**：全神明 +40% 基础生命与物抗、+28% 魔抗，同步大幅增强防御塔、小兵与野区 Boss[^smite-95] | 版本级全英雄双抗成长/穿透装备系数微调，控制秒人速度与坦克坦度平衡 |

---

## 二、顶层体验锚点如何向下分解到角色与单技能边界

成熟团队**绝不从单技能“拍脑袋填伤害”开始做平衡**，而是采用“**顶层体验锚点 $\to$ 职业子原型边界 $\to$ 单英雄强度预算 $\to$ 单技能数值与机制配额 $\to$ 线上分层反馈闭环**”的五层漏斗。

### 2.1 四维顶层体验锚点与全局联动守恒律

顶层体验锚点定义了一场对局在不同时间点“应该打成什么样”，核心由四组定量指标组成：

1. **分阶段 TTK（Time to Kill）走廊**：
   - 规定同经济条件下，不同进攻原型（爆发刺客 / 持续射手 / 阵地法师 / 战士）击杀不同防守原型（脆皮 / 半肉 / 纯坦）的**期望耗时区间**与**所需技能轮次**。
   - 例如：对线期（早期）通常要求“一套完整基础连招打掉脆皮 45%–65% 血量，需两轮换血或配合打野/斩杀才能击杀”，防止一级秒杀破坏对线博弈；中后期刺客满套含大招打脆皮 TTK 落在 1.5–2.5 秒（必须保留闪避/净化/保命响应窗口），而持续输出打坦克需要 6–12 秒以上。
2. **爆发（Burst）vs 持续伤害（Sustained DPS）配比**：
   - 若爆发占比过高，战斗沦为“先手即蒸发”，治疗、持续拉扯与残局运营全部失效；若持续伤害与治疗占比过高，则退化为“站桩互殴无击杀点”。
   - **关键约束**：爆发型技能的 DPS（伤害/冷却）必须显著低于持续型输出，换取极高的**短窗伤害密度（Damage in 1.5s）**；反之，持续输出型英雄在短窗内不能轻易秒人，但 4 秒后的累计输出曲线反超爆发英雄。
3. **PvE 节拍锚点（清线 / 打野 / 推塔）**：
   - 清线所需技能轮次（如法师 1 套基础技能清掉远程兵的时间点）、单人/双人速刷大型中立资源耗时、以及满血推掉外塔所需的波次，直接决定转线频率与对局时长。
4. **跨阶段成长一致性（消除“前强后废”或“早期滚雪球崩盘”）**：
   - Blizzard 在 2015 年 11 月的《风暴英雄》全局成长重构中给出了一手范例[^hots-2015]：在旧的加法固定成长下，前期等级差造成的百分比属性差距极大（如 4 级对 2 级），导致早期一血后迅速滚雪球碾压。Blizzard 将几乎所有英雄的生命与伤害统一改为每级约 **+4% 复利成长**（$S(L) = S_1 \times 1.04^{L-1}$），确保任意阶段领先 1 级都稳定对应约 4% 的数值优势，同时同步重算小兵、雇佣兵、建筑血攻与复活计时。

> **一级实践揭示的“全局联动守恒律”**：  
> 当顶层 TTK 失控需要整体修正时，**不能只动单一变量**。四次里程碑级跨作品大改（LoL 12.10[^riot-1210]、SMITE 9.5[^smite-95]、OW2 Season 9[^ow2-s9]、Dota 2 7.20[^dota-720]）全部验证了同一联动法则：
> - **抬高全员血量/双抗 $\implies$ 必须同步削弱治疗/护盾或引入减疗**（因为双抗按乘法放大了每一点治疗/护盾的 EHP，LoL 12.10 全面削弱护盾/治疗与重伤，OW2 S9 直接给所有输出位加入 -20% 减疗被动）；
> - **抬高全员坦度 $\implies$ 必须同步提高防御塔、野怪与百分比伤害监管**（LoL 12.10 与 SMITE 9.5 均大幅强化防御塔与野区 Boss，并第一时间热修百分比生命伤害异常值，防止越塔无成本与坦克无限拉扯）；
> - **拉长 TTK $\implies$ 必须同步校准命中可靠性与控制时长**（OW2 S9 在加血的同时增大弹道判定以降低“刮痧挫败感”；Dota 2 7.33 则在长年机制膨胀后统一削减全域控制时长以归还玩家操作权[^dota-733]）。

### 2.2 从顶层锚点向职业子原型与单技能边界的分解

Riot Games 在《Dev Blog: Classes & Subclasses》与《Taking Another Look at Subclasses》中展示了将顶层体验分解为**职业子原型契约（Subclass Contract）**的方法[^riot-sub]：每个子原型明确规定**“擅长什么（强项预算）”**与**“必须付出的代价（弱项税）”**。

| 职业子原型范例 | 核心体验定位 | 必须满足的单套件上界（输出/功能） | 必须承担的结构弱项税（扣减预算项） |
| :--- | :--- | :--- | :--- |
| **爆发法师（Burst Mage）** | 短窗秒杀脆弱目标 | 1 套含 R 连招在 1.5–2.5s 内对同经济脆皮输出 ≥ 100% EHP | 冷却真空期长、持续 DPS 低、位移受限、身板脆弱 |
| **炮台法师（Artillery Mage）** | 超远安全距离消耗 | 射程显著高于常规交战半径，远程 Poke 压力高 | **极低命中可靠度**（慢速/延时非指向技能）、近身自保与基础生命极低 |
| **刺客（Assassin）** | 高机动切入斩杀后排 | 高机动入场+脱离能力，单体斩杀线高 | 缺乏稳定群控、清线/持续打坦能力弱、容错依赖切入时机 |
| **重装战士（Juggernaut）** | 近身压制与换血霸主 | 近身极高可靠度伤害 + 优秀基础 EHP | 缺乏远距离稳定位移，易被风筝（用机动性换取近身可靠度） |
| **开团坦克（Vanguard）** | 强开团与控制链发起 | 高可靠度群体硬控 + 极高基础 EHP 成长 | 低单挑爆发伤害、低技能属性倍率（防止出输出装变秒人战士） |

落实到**单技能设计边界**时，每个技能被分配一个明确的角色职责（例如：主清线/Poke 技能、保命位移技能、核心控制/开团技能、终极斩杀或阵地技能），并受三条硬边界约束：
1. **单套连招伤害封顶**：在给定等级与经济节点下，$\sum \text{可靠命中期望伤害} + \text{普攻穿插}$ 不得越过所属子原型的 TTK 下限；
2. **成长曲线分档隔离**：高可靠度单段主输出允许较高的等级成长，而易命中多目标、持续高覆盖、附带强硬控或可叠刷的护盾/治疗必须压低成长斜率（与《万象》在 `docs/技能成长与输入设计确认.md` 中对 1.35 / 1.25 / 1.15 成长档的划分思路高度吻合）；
3. **线上多段位闭环校验**：Riot 的 **Champion Balance Framework**（2019 提出、2020 迭代）与 Greg Street 在 GDC 2017 的演讲指出，同一机制在不同操作水平的玩家手中价值迥异[^riot-bf][^riot-bf-2020][^gdc-street]。因此不能只看全服平均胜率，必须拆成多档位（Average / Skilled / Elite / Pro），执行**非对称干预规则**：只要在**任意一个档位**突破胜率/禁用率（或职业赛场的 Presence 选禁率）红线即强制削弱；只有当英雄在**全部档位**都偏弱时才给予加强。

---

## 三、统一属性估值与强度预算（Power Budget）数学范式

### 3.1 统一标尺：等效金币（Gold Efficiency）与归一化预算分

要横向比较不同属性、不同英雄与不同技能分支的强度，必须把所有异构维度映射到同一标尺。在有装备系统中该标尺是**等效金币（Gold Efficiency）**；在无装备系统中则是等价的**归一化预算分（Power Budget Points, PBP）**。

Riot《英雄联盟》官方 Wiki 长期维护的标准锚定法是以**最便宜的纯单属性基础件**定义各属性的基准单价[^lol-wiki-gold]：

$$\text{Gold Efficiency} = \frac{\sum_k q_k \cdot v_k}{\text{Cost}} \times 100\%$$

其中 $q_k$ 为第 $k$ 项属性数量，$v_k$ 为该属性的基准锚定单价。以下是 LoL 官方 Wiki 公开的典型基准锚点（作为跨属性相对权重参考）：

| 基础属性 | 锚定基础件 | 基准单价（Gold / 1 单位） | 相对权重启发（以 100g 计） |
| :--- | :--- | :---: | :--- |
| 攻击力（AD） | Long Sword（350g / 10 AD） | **35.0g** | 2.86 AD |
| 法术强度（AP） | Amplifying Tome（400g / 20 AP） | **20.0g** | 5.00 AP（单点 AP 便宜于 AD，因 AD 同时加成普攻与物理技能） |
| 最大生命值（HP） | Ruby Crystal（400g / 150 HP） | **2.67g** | 37.5 HP |
| 护甲（Armor） | Cloth Armor（300g / 15 Armor） | **20.0g** | 5.00 Armor |
| 技能急速（Ability Haste） | Glowing Mote（250g / 5 AH） | **50.0g** | 2.00 AH（+2% 施法频率） |
| 治疗与护盾强度（H&S Power） | Forbidden Idol 锚定口径 | **~50g / 1%** | +2% 治疗/护盾放大 |

> **关键设计洞见**：  
> 基准单价不是静态常数，它必须隐含**属性之间的结构乘数关系**。例如 1 AD 比 1 AP 贵 75%，是因为 AD 对所有物理普攻是 100% 实时加成，而 AP 受技能冷却与命中率约束；若某游戏中技能普遍高倍率且低冷却，则法强与技能急速的相对单价就必须相应上调。

---

### 3.2 核心战斗属性的数学等价模型

#### 1. 生命与双抗：有效生命（EHP）线性边际定律与最优血抗比

成熟 MOBA 的抗性减伤公式几乎全部收敛到**双曲减伤模型**，其核心目的不是“让堆抗性收益递减”，恰恰相反，是为了让**每 1 点抗性对有效生命值（EHP）的贡献保持严格线性**[^dota-armor][^lol-wiki-gold]：

$$\text{DR}(R) = \frac{R}{R + K} \quad \Longrightarrow \quad \text{EHP}(H, R) = \frac{H}{1 - \text{DR}(R)} = H \cdot \left(1 + \frac{R}{K}\right)$$

- **LoL / SMITE**：$K = 100$，每 +1 点护甲/魔抗恒定增加 **+1% 当前最大生命值**的对应 EHP。
- **王者荣耀**：$K = 602$，抗性数值量级更大，但每 +1 点护甲仍恒定增加 $\frac{1}{602} \approx 0.166\%$ 物理 EHP。
- **Dota 2（7.27+）**：$\text{DR}(A) = \frac{0.06 A}{1 + 0.06 |A|}$（等价于 $K = \frac{1}{0.06} \approx 16.67$），每 +1 点护甲恒定提供 **+6% 物理 EHP**[^dota-727][^dota-armor]。Valve 在 7.20 曾短暂尝试 $\frac{0.052 A}{0.9 + 0.048 A}$ 以调整中段护甲曲线，引发全建筑/野怪重平衡并在 7.27 回归经典的 0.06 线性 EHP 标尺[^dota-720][^dota-727]。
- **多来源百分比减伤的防溢出乘算（Dota 2 魔抗 / Deadlock 双抗）**：当抗性直接以百分比给出（如装备直接写“+25% 子弹抗性”）而非点数 $R$ 时，Valve 在 Dota 2 与 Deadlock 中均强制采用**独立乘算**[^dota-mr][^dl-res]：

$$R_{\text{total}} = 1 - \prod_{i=1}^{m}(1 - r_i)$$

由此保证无论堆叠多少百分比抗性装备，承受伤害倍率 $\prod (1 - r_i)$ 永远严格大于 0，彻底杜绝“100% 物理/法术免疫”的数值崩溃。

**最优血抗配比定理（拉格朗日极大化）**：  
若单位生命价格为 $c_H$，单位抗性价格为 $c_R$，在给定防御预算 $B = c_H \Delta H + c_R \Delta R$ 下最大化单类型 EHP $H \cdot (1 + R/K)$，一阶最优条件满足：

$$\frac{\partial \text{EHP} / \partial H}{c_H} = \frac{\partial \text{EHP} / \partial R}{c_R} \quad \Longleftrightarrow \quad \frac{H}{K + R} = \frac{c_R}{c_H}$$

这说明**血量与抗性互为乘数杠杆**：低血量时买抗性效率低，高血量时买抗性效率高；如果系统只允许购买抗性而不提供可购生命（或相反），随着英雄升级自带生命增长，购买防御的边际 EHP 收益会自动随等级抬升——**这是无生命购买项的系统必须重点盯防的后期坦克膨胀点**。

#### 2. 冷却缩减（CDR）vs 技能急速（Ability Haste）：线性化革命

传统百分比冷却缩减（CDR）存在致命的**超线性凸收益陷阱**：

$$\text{CD} = \text{CD}_0 \cdot (1 - \text{CDR}) \quad \Longrightarrow \quad \text{Cast Rate Multiplier} = \frac{1}{1 - \text{CDR}}$$

- 从 0% 堆到 10% CDR，施法频率仅提升 $\frac{1}{0.9} - 1 = 11.1\%$；
- 从 40% 堆到 50% CDR，同样花 10% CDR 的预算，施法频率却从 $1.67\times$ 跃升到 $2.00\times$（**相对增幅高达 +20%**，边际回报几乎翻倍）；
- 从 50% 堆到 60% CDR，施法频率再跃升 **+25%**。

这迫使旧版 LoL、SMITE、王者荣耀不得不引入 40% 硬上限并严格限制冷却词条投放。Riot 在 2021 季前赛彻底废除 CDR，改用**技能急速（Ability Haste, AH）**[^riot-s11][^lol-wiki-gold]：

$$\text{CD} = \frac{\text{CD}_0}{1 + \text{AH}/100} \quad \Longrightarrow \quad \text{Cast Rate Multiplier} = 1 + \frac{\text{AH}}{100}$$

每 +1 点 AH 恒定提供 **+1% 基础施法频率**，在数学结构上与攻速（Attack Speed）和双抗 EHP 完全同构——既可线性定价，又无需担心高堆叠时出现边际爆炸。

#### 3. 移速（Movement Speed）：分段软上限防风筝破局

移速在表面上是“跑得更快”，本质上是**交战选择权（Engagement Agency）+ 技能闪避率 + 地图转线经济**的复合乘数。一旦移速超过某一阈值，近战将永远摸不到远程、线性非指向技能彻底失效。因此 LoL 对移速实施**上下双边软上限**[^lol-wiki-ms]：

$$\text{MS}_{\text{eff}} = \begin{cases} \text{MS}_{\text{raw}}, & \text{MS}_{\text{raw}} \le 415 \\ 415 + 0.8 \cdot (\text{MS}_{\text{raw}} - 415), & 415 < \text{MS}_{\text{raw}} \le 490 \\ 475 + 0.5 \cdot (\text{MS}_{\text{raw}} - 490), & \text{MS}_{\text{raw}} > 490 \end{cases}$$

同时在减速端（$<220$）也设置软下限保护，确保玩家在极端减速下不至于完全锁死为 0 移速。

#### 4. 攻速、护盾与治疗的统一 EHP/DPS 折算

- **攻速（Attack Speed）**：对普攻型角色，有效普攻 DPS 为 $\text{DPS}_{\text{AA}} = \text{AD} \cdot \text{AS}_{\text{base}} \cdot (1 + \text{AS}_{\text{bonus}}) \cdot (1 + p_{\text{crit}}(M_{\text{crit}} - 1))$。由于攻速与攻击力呈乘法耦合，对任意单点投入，两者互为乘数杠杆（与血抗同构）。
- **护盾（Shield）**：折算为**带有效吸收率的临时 EHP**：
  $$\Delta \text{EHP}_{\text{shield}} = S \cdot \left(1 + \frac{R_{\text{eff}}}{K}\right) \cdot \eta_{\text{util}}$$
  其中 $\eta_{\text{util}} \in [0.55, 0.90]$ 为吸收效率折扣（护盾持续越短、触发越被动、越容易被敌方停火等过期，则 $\eta_{\text{util}}$ 越低；瞬发且玩家主动按键开启的护盾 $\eta_{\text{util}} \to 0.85\text{–}0.90$）。
- **治疗（Healing）**：在时长为 $T$ 的交战中，总治疗量 $H_{\text{heal}}$ 的等效防御价值为：
  $$\Delta \text{EHP}_{\text{heal}} = H_{\text{heal}} \cdot \left(1 + \frac{R_{\text{eff}}}{K}\right) \cdot (1 - \omega_{\text{overheal}}) \cdot (1 - \text{GW})$$
  其中 $\omega_{\text{overheal}}$ 为溢出治疗浪费率，$\text{GW}$ 为环境重伤/减疗覆盖率。注意此处**双抗直接放大了护盾与治疗的价值**——这也是为什么在预算模型中，**给高双抗坦克加 100 点护盾/治疗，其真实强度远高于给脆皮加 100 点**。

---

### 3.3 非伤害机制的定量估值模型（Power Budget 核心难点）

根据 Ian Schreiber《Game Balance Concepts》（Level 3 成本曲线、Level 4 期望概率、Level 6 情境平衡）[^schreiber-l3][^schreiber-l4][^schreiber-l6]以及 Riot / Valve / Hi-Rez 的实战拆解，非伤害机制之所以经常导致“数值看起来不高但角色超模”，是因为策划忽略了它们的**隐性预算成本**。将非伤害机制定量纳入同一强度预算，必须使用以下五套折算范式：

#### 1. 控制机制（硬控 / 软控 / 缓时类机制）

- **硬控（眩晕 / 冰冻 / 击飞 / 压制）—— 行动经济与集火增益双项估值**：
  硬控同时做三件事：① 剥夺目标 $d$ 秒内的全部输出与逃生行动；② 将我方后续技能的命中率从 $p_{\text{hit}}$ 抬升至接近 $1.0$；③ 强制打断目标的引导/起手技能。其预算等价伤害值为：

  $$V_{\text{hardCC}}(d) = d \cdot \Big(\text{DPS}_{\text{target}}^{\text{ref}} + \lambda_{\text{team}} \cdot \text{DPS}_{\text{ally}}^{\text{ref}} \cdot (1 - \bar{p}_{\text{hit}})\Big) + V_{\text{interrupt}} \cdot p_{\text{int}}$$

  - 在 1v1 基准下（$\lambda_{\text{team}} = 1$），1 秒稳定硬控至少等价于“1 秒目标基准 DPS + 己方 1 个高收益技能的必中溢价”；在 5v5 团战中（$\lambda_{\text{team}} \in [2, 3.5]$），**群体硬控的预算价值随预期命中人数 $N_{\text{target}}$ 呈超线性增长**（控制 2 人以上的瞬时减员收益远高于 2 倍单控）。
  - **不可减免硬控溢价（SMITE True Hard CC 规则）**：受韧性/状态抗性/DR 缩减的普通硬控乘以系数 $1.0$；而击飞、强制击退/拉扯、压制等**无视韧性且强制改变站位**的“真硬控”（SMITE 9.7 直接赋予其 2 倍 DR 层数消耗[^smite-97]），在强度预算上须施加 **$1.3\times\text{–}1.6\times$ 位移控制溢价**。

- **软控（减速 / 沉默 / 禁锢 / 缴械）—— 职责有效削减系数 $\alpha_{\text{role}}$**：
  软控不完全冻结目标，但会按比例削弱目标的战斗效能，估值公式为：

  $$V_{\text{softCC}}(d) = d \cdot \bar{\alpha}_{\text{eff}} \cdot V_{\text{hardCC}}(1\text{s})$$

  | 软控类型 | 对纯近战战士 $\alpha$ | 对技能型法师 $\alpha$ | 对普攻型远程 $\alpha$ | 典型预算折算系数 $\bar{\alpha}_{\text{eff}}$ |
  | :--- | :---: | :---: | :---: | :---: |
  | 强减速（40%–60%） | 0.55 | 0.30 | 0.35 | **0.35 – 0.45**（且叠加我方技能命中率提升） |
  | 禁锢（Root，可施法不可移） | 0.75 | 0.25 | 0.35 | **0.45 – 0.55**（含打断位移/阻止走位躲技能） |
  | 沉默（Silence，可移不可施法） | 0.40 | 0.80 | 0.20 | **0.50 – 0.65**（含打断持续引导与逃生位移） |
  | 缴械（Disarm） | 0.45 | 0.05 | 0.85 | **0.40 – 0.50** |

- **缓时类机制（Time Dilation，时间流速系数 $\mu \in (0, 1)$）的专项定量模型**：
  对于像《万象》“时律·刻衡”这样独具特色的**缓时机制**（将目标未完成起手、普攻恢复、自主位移与独立弹体飞行速度乘以 $\mu$，但不改变冷却与真实时间持续期），既不能简单按普通移速减速估低，也不应按完全硬控估高。其严格数学折算包含三部分：

  $$V_{\text{timeDilate}}(d, \mu) = \underbrace{(1 - \mu) \cdot d \cdot \text{DPS}_{\text{act}}^{\text{ref}}}_{\text{行动时间净剥夺}} + \underbrace{d \cdot \Delta p_{\text{dodge}}(\mu) \cdot D_{\text{incoming}}}_{\text{敌方慢速弹体/起手被我方轻易闪避}} + \underbrace{d \cdot \Delta p_{\text{hit}}(\mu) \cdot D_{\text{outgoing}}}_{\text{目标移速/位移放慢使我方必中}}$$

  - 例如当 $\mu = 0.5$ 时，目标的行动速度减半（等效每 1 秒净剥夺 $0.5$ 秒有效行动），且敌方起手/弹道时间翻倍（0.25s 起手变成 0.50s，从“难以反应”跌落至“肉眼可轻松走位/打断”），因此其真实实战压制力约为同等时长硬控的 **0.65–0.80 倍**（显著高于普通 50% 移速减速的 0.40 倍！）；
  - **关键结构约束**：① 领域型持续缓时若不吃目标韧性缩短，必须支付更高的**持续场控预算税**；② 多重缓时必须严禁叠乘（取 $\min(\mu)$），且与普通减速取强不叠乘，否则会产生近似永久停帧的死锁漏洞。

#### 2. 位移机制（Dash / Blink / 地形穿透）

在 Riot 的内部预算哲学中，机动性是最昂贵的机制之一（**Mobility Tax**）[^riot-sub][^gdc-street]。位移的定量价值拆分为三部分：

$$V_{\text{mob}} = \underbrace{p_{\text{dodge}} \cdot \bar{D}_{\text{key}}}_{\text{规避关键技能期望保命值}} + \underbrace{\frac{L_{\text{dash}}}{v_{\text{base}}} \cdot \text{DPS}_{\text{gap}}}_{\text{等效射程/入场时间压缩}} + \underbrace{V_{\text{wall}} \cdot \mathbb{I}_{\text{crossWall}}}_{\text{跨墙/脱战拓扑溢价}}$$

- **指向性位移（仅能冲向敌人）** 仅提供进攻入场价值，预算消耗约为自由方向位移的 **50%–60%**；
- **无目标限制自由位移（可进可退）** 兼顾进攻与逃生，预算消耗 **100%**；
- **瞬移（Blink，无路径碰撞/不可在途中被打断）+ 可穿墙** 需额外追加 **1.3×–1.5× 拓扑安全溢价**，通常必须通过降低基础生命、削减稳定硬控或拉长冷却来支付这笔预算。

#### 3. 射程与安全度（Range & Safety Envelope）

射程不是单纯的“多打 2 格”，它通过**先手自由换血窗口**产生复利优势：

$$t_{\text{free}} = \frac{\max(0, R_{\text{self}} - R_{\text{enemy}})}{v_{\text{enemy}}}$$

在对手走完这段距离之前，远程单位可以零风险倾泻 $t_{\text{free}} \cdot \text{DPS}_{\text{poke}}$ 的单方面输出，且控制着 $R^2$ 级别的视野与阵地面积。因此成熟 MOBA 普遍执行**“射程-坦度-可靠度守恒”**：
- 射程每跨上一个大档位，必须从**基础生命/双抗**（降低近身容错）与**技能命中可靠度**（改用更慢的弹道或延时预警，如炮台法师）两端扣减预算[^riot-sub]。

#### 4. 前摇、弹道速度与命中可靠性（Reliability Discount）

Ian Schreiber 在《Game Balance Concepts》Level 4 指出，任何带有失败概率的效果都必须按**期望价值（Expected Value）**折算[^schreiber-l4]；Riot 将其发展为 **Reliability vs. Counterplay** 光谱[^riot-sub]：

$$D_{\text{budget}} = D_{\text{nominal}} \cdot p_{\text{hit}}(t_{\text{windup}}, v_{\text{proj}}, w_{\text{hitbox}}, R)$$

| 技能形态 | 典型实战命中率 $p_{\text{hit}}$ | 允许的账面伤害倍率补偿（相对基准 1.0×） | 风险提示（高分段套利） |
| :--- | :---: | :---: | :--- |
| 指向性 / 瞬发贴身判定 | 0.95 – 1.00 | **0.85× – 1.00×** | 反制空间低，绝不可同时给高爆发 + 硬控 |
| 快速宽弹道 / 短前摇（≤0.25s）扇形 | 0.75 – 0.85 | **1.10× – 1.25×** | 常规主力输出基准形态 |
| 中速窄弹道 / 0.4–0.6s 地面预警 | 0.55 – 0.70 | **1.30× – 1.50×** | 需审计“配合自身/队友硬控必中”后的爆发上限 |
| 极长前摇（≥0.8s）/ 极窄走廊穿透 | 0.35 – 0.50 | **1.55× – 1.80×（封顶！）** | **严禁按 $1/p_{\text{hit}}$ 线性给 2.5×–3× 伤害**，否则配合硬控瞬间秒杀 |

> **可靠度折扣封顶铁律（GDC 2017 教训）**：在团队游戏中，队友的硬控或缓时可以把一个原本 $p_{\text{hit}} = 0.4$ 的高前摇技能瞬间拉升到 $p_{\text{hit}} = 0.95$。如果策划按数学倒数 $1/0.4 = 2.5\times$ 给予账面伤害补偿，高分段与开黑局就会通过“先手控 + 接超高伤害技能”完成无脑套利[^gdc-street]。因此**非大招技能的可靠度风险溢价应封顶在 1.5×–1.8× 以内**。

#### 5. 条件触发与覆盖率（Conditional Modifiers & Uptime）

Ian Schreiber 在 Level 6《Situational Balance》中强调，情境/条件触发机制（如“叠满 3 层触发”“目标血量低于 35% 斩杀”“静止 1.2 秒获得护盾”）绝不能只按“平均触发频率”简单打折[^schreiber-l6]，必须同时乘以**玩家时机可控度系数 $\gamma_{\text{control}}$**：

$$V_{\text{cond}} = V_{\text{raw}} \cdot U_{\text{uptime}} \cdot \gamma_{\text{control}}$$

- **被动/随机/依赖对手犯错的条件**（如“敌方恰好交完位移后命中”）：$\gamma_{\text{control}} \approx 0.8\text{–}0.9$，允许较高的数值回报；
- **玩家可主动规划对齐爆发窗的条件**（如“普攻/技能叠 3 层引爆”或“主动物攻触发”）：虽然名义覆盖率 $U_{\text{uptime}}$ 仅 30%–40%，但玩家能精准把触发帧对齐到关键换血/斩杀时刻，此时 $\gamma_{\text{control}} \in [1.2, 1.5]$，其真实预算占比通常在 **0.55×–0.75× 无条件等价效果**，切忌过度补偿数值。

---

## 四、面向万象（MYRIAD）特有结构的数学约束与反模式清单

《万象》采用了一套与传统装备流 MOBA 显著不同、更接近《风暴英雄》+《Deadlock》混合范式的双轨成长架构：
1. **经济成长轨**：无传统六神装商城，而是开放**五项独立属性购买**（力量、智慧、速度、防御、冷却），每项独立从 360 金起价，按**指数成本曲线 $P(n) = \text{round}(360 \times 1.2^n)$** 递增（总计最多买 15 点，单项最多 10 点）；
2. **技能构筑轨**：英雄拥有 1–20 级成长，且**被动（A/B）、主要技能 E（A/B）、终极技能 R（A/B）三层均采用二选一互斥分支**。

下面直接给出这套结构必须遵守的数学约束与五大高危反模式。

---

### 4.1 指数成本曲线 $P(n) = 360 \times 1.2^n$ 的数学动力学与防单属性堆叠

先看单属性第 $n+1$ 点（已有 $n$ 点）的价格演化：

| 已购点数 $n$ | 0 → 1 | 1 → 2 | 2 → 3 | 3 → 4 | 4 → 5 | 5 → 6 | 6 → 7 | 7 → 8 | 8 → 9 | 9 → 10 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **单点价格 $P(n)$** | 360 | 432 | 518 | 622 | 746 | 896 | 1075 | 1290 | 1548 | 1858 |
| **相对首点倍率 $1.2^n$** | 1.00× | 1.20× | 1.44× | 1.73× | 2.07× | 2.49× | 2.99× | 3.58× | 4.30× | 5.16× |
| **该项累计花费** | 360 | 792 | 1310 | 1932 | 2678 | 3574 | 4649 | 5939 | 7487 | 9345 |

根据 Ian Schreiber 的凸成本曲线理论[^schreiber-l3]，理性玩家的加点最优解遵循**等边际性价比法则（Equi-marginal Principle）**：每笔金币永远流向**单位金币相对战力增幅**最高的属性：

$$\text{ROI}_i(n_i) = \frac{\Delta \ln \Pi(\mathbf{n}) / \Delta n_i}{360 \times 1.2^{n_i}}$$

由此推出**三条硬数学约束**：

1. **凸成本 + 凹相对收益 = 天然防单堆（适用于线性增量属性）**：
   对于力量（$+15 A$）、智慧（$+20 S$）、防御（$+5$ 双抗）等固定增量属性，其相对边际增幅 $\frac{\Delta x}{x_0 + n\Delta x}$ 本身随 $n$ 递减，叠加分母 $1.2^n$ 的指数增长，形成**双重递减阻力**。以力量（初始 $A=65$，每点 $+15$）为例：
   - 第 1 点（360g）：相对攻击增幅 $+23.1\%$，单位千金增幅 **+64.1% / 1000g**；
   - 第 5 点（746g）：相对攻击增幅 $\frac{15}{125} = +12.0\%$，单位千金增幅暴跌至 **+16.1% / 1000g**（仅为首点的 1/4！）；
   - 第 8 点（1290g）：相对增幅 $+8.8\%$，单位千金增幅仅 **+6.8% / 1000g**（不到首点的 1/9）。
   **结论**：$1.2^n$ 极其有效地阻止了“10 点全堆单属性”的线性无脑流派（与 Deadlock 跨类投资高性价比异曲同工[^dl-boon]）。

2. **反模式 1：超线性收益属性与凸成本曲线相撞（固定百分比 CDR 陷阱）**：
   - 在万象当前设计中，冷却属性每点给 **+3% 固定冷却缩减（CDR）**，且底层属性上限 `MAX_COOLDOWN_REDUCTION = 60`。
   - 如第 3.2 节所证，CDR 的真实施法频率增幅是递增函数 $\frac{0.03}{1 - 0.03(n+1)}$：虽然单靠购买（最多 10 点 = 30% CDR）时，$1.2^n$（每点 +20% 价格）足以压过 3% CDR 的边际增速（从 +3.1% 缓升至 +4.3%），但**一旦角色技能、被动分支或地图 Buff 叠加把总 CDR 推至 45%–60%**，每点额外冷却缩减都会产生超线性收益跃迁，同时让硬控/缓时/护盾的**无缝覆盖率**直接突破阈值（例如控制时长 $\ge$ 冷却时间导致无限控）。
   - **约束建议**：将冷却属性改造为线性**技能急速（Ability Haste）**（如每点 +4~5 AH，对应 $\text{CD} = \frac{\text{CD}_0}{1+\text{AH}/100}$）；若短期保留百分比 CDR，必须把全来源总 CDR 硬上限压回 ≤ 40%，且对核心控制/保命技能设置**最小不可压缩冷却下限（Min CD Floor）**。

3. **反模式 2：首点 ROI 严重失衡导致“全员泛用副属性趋同”或“废弃属性”**：
   - 对比首点（360g）的绝对与相对收益：
     - 力量第 1 点：$A: 65 \to 80$（+23.1% 普攻/物攻基数）；
     - 智慧第 1 点：$S: 30 \to 50$（+66.7% 初始法强基数！若法术技能主要吃 $S$ 倍率而非基础值，智慧首点极强；若基础值高而 $S$ 倍率低，则首点偏弱）；
     - 速度第 1 点：攻速 $0.70 \to 0.76$（+8.6%），移速 $0.10 \to 0.1015$（+1.5%）；
     - 防御第 1 点（初始双抗 45，假设 $K=100$）：$\text{EHP}$ 增幅 $\frac{5}{145} = +3.45\%$（双类型同时生效）；
     - 冷却第 1 点（+3% CDR）：施法频率 $+3.09\%$。
   - **风险诊断**：当主属性（力量/智慧）因 $1.2^n$ 涨到第 4–5 点（622–746g）后，其单位金币增幅会迅速跌落到与防御/冷却/速度首点（360g）同一量级。如果这五项属性的首点 ROI 差距过大（例如智慧对法师首点值 3 倍于防御，或防御对全体英雄首点太便宜/太贵），就会出现两种极端：
     - **极端 A（全员趋同）**：因为主属性 4 点以后太贵，所有英雄第 5–12 点全部无脑补“前 2 点防御 + 前 2 点冷却 + 前 2 点速度”，抹平了坦克、刺客与射手的构筑差异；
     - **极端 B（属性死区）**：若防御每点 +5 双抗在 $K=100$ 下首点只给 +3.45% EHP，连力量首点的一小半都不到，就会导致哪怕坦克也完全不买防御、全员裸堆输出 → 触发下文的攻防剪刀差崩坏。
   - **约束建议**：建立**五属性 × 职业原型 ROI 平衡表**，确保：(a) 坦克/战士买防御前 3 点的 EHP/金币回报能与输出位买主属性前 3 点的 DPS/金币回报在同一量级；(b) 主属性在 1–5 点区间保持足够明显的角色流派分化，不至于过早被迫跨买无关属性。

---

### 4.2 防止攻防曲线“剪刀差”失控（中后期 TTK 漂移约束）

在无传统装备的五属性购买体系中，最隐蔽的系统级风险是**跨阶段攻防剪刀差**：

- **攻击侧的隐藏乘法膨胀**：
  随着等级 $L$ 和属性点 $n$ 推进，输出英雄同时获得四重增益：
  1. 技能升阶基础伤害（1 → 5 阶 / 1 → 7 阶 / 大招 1 → 3 阶，且已乘以 1.15–1.35 首测成长档）；
  2. 力量/智慧加成（$A$ 或 $S$ 随购买上升）；
  3. 速度（攻速）或冷却（施法频率）的**时间轴乘法放大**；
  4. 被动升阶（1/4/7/10/13 级免费自动 5 阶！）提供的额外伤害或倍率。
- **防御侧的加法受限风险**：
  反观防御侧，英雄仅靠**升级固定生命增量**（如刻衡 $600 + 85(L-1)$，伊珂 $550 + 75(L-1)$）与**昂贵的防御购买点**（受 $1.2^n$ 递增压制，且不额外加最大生命值）。
  - 计算可得：从 1 级到 20 级，脆皮生命值膨胀约 **3.6×**（550 → 1975），若不买防御，双抗基本不涨；而攻击侧若“技能升阶（~2.2×）× 属性加成（~1.8×）× 频率/被动（~1.3×）”，总爆发/DPS 膨胀很容易超过 **5.0×**！
  - 结果就是**前期打不动、13 级后全员 1 秒互秒**的经典剪刀差塌陷。

**防剪刀差三条数学护栏**：

1. **分阶段 TTK 走廊强制回归检验**：
   在 `L = 1, 4, 8, 12, 16, 20` 六个里程碑（对应标准经济可购点数 `n = 0, 2, 5, 8, 12, 15`），用自动化脚本跑完所有“进攻角色最优加点 × 防守角色基准模型（脆皮/战士/坦克）”的**稳定命中连招占比**与 **TTK**：
   - 规定各阶段的 $\frac{\text{1 套稳定连招伤害}}{\text{同阶脆皮 EHP}}$ 上下界（如 1 级 35%–55%，4 级刚学 R 为 60%–85%，12–20 级爆发刺客封顶 95%–115%、非爆发角色封顶 65%–85%）。
2. **让防御属性具备跨阶段跟随能力，或与基础双抗成长协同**：
   如果购买防御只给固定 $+5$ 双抗而价格按 $1.2^n$ 暴涨，后期每点防御的边际 EHP 增量会被极度稀释；必须校核英雄升级自带的双抗成长（或每点防御同时附带少量生命/按比例提升抗性），使满经济纯防御构筑在 20 级时的物理/魔法 EHP 相对于 0 防御构筑有明确可感知的坦度差（行业经验：纯坦满防御 EHP 应达到同级脆皮的 **2.2×–3.0×**）。
3. **护盾/治疗与防御成长的联动审计 + 控制链 DR 护栏**：
   任何针对脆皮的全局生命/双抗回调，必须同步检查带护盾/治疗的被动与技能（遵循 LoL 12.10 / OW2 S9 守恒律）；同时为多人团战引入类似 SMITE 9.7 的**硬控/缓时递减收益（DR）或脱控保底机制**[^smite-97]，防止 2 名带控英雄在后期直接把 TTK 压缩到“零反制时间”。

---

### 4.3 互斥技能分支（被动/E/R 二选一）防“纯上位替代”法则

万象每个角色拥有**三层独立二选一分支**（被动 A/B、主要技能 E A/B、终极技能 R A/B），共构成 $2^3 = 8$ 种终局流派。这与 Blizzard《风暴英雄》的无装备天赋树面临完全相同的核心风险。结合 HotS 开发团队复盘[^hots-tiers]、Ian Schreiber《Level 9: Intransitive Mechanics》[^schreiber-l9]与 David Sirlin《Viable Options》[^sirlin-p2]，必须严防三大反模式并落地三条正交约束：

#### 三大分支反模式

1. **反模式 3：同维传递比拼（Transitive Pure-Upgrade Trap）**
   - *症状*：分支 A 和分支 B 承担相同战术职能（例如都是“对前方打伤害”，只是 A 单体倍率高、B 冷却略短）。由于都在同一维度（期望 DPS）上竞争，玩家只需按一下计算器就能解出数学最优解，另一分支出场率跌破 10% 沦为死分支。
2. **反模式 4：机制补齐税（Mandatory Tax / Illusion of Choice）**
   - *症状*：角色基础套件（Q/W/普攻/身板）存在明显运转短板（如极度缺蓝、极易被贴脸暴毙、完全无法清线），而某一层的 A 分支恰好修补了这个致命缺陷，B 分支只加数值。无论把 B 的数值吹到多高，实战中玩家都被迫交“必选税”点 A（HotS 早期凯尔萨斯、伊利丹的核心教训[^hots-tiers]）。
3. **反模式 5：跨层偏科共振（All-Survivability / All-Burst Stacking）**
   - *症状*：被动、E、R 三层分别各自放了一个“输出分支”和一个“生存/控制分支”。若不做跨层预算封顶，玩家把三层的生存/控制分支全选后，可能凑出“零交互永动坦克/无限控流”；或者三层全选爆发后，乘算叠加击穿 TTK 底线（正是 HotS 后来改用**主题分层 Themed Tiers** 禁止跨层堆满保命天赋的原因[^hots-tiers]）。

#### 三条正交防崩坏约束

1. **同层非传递正交分工（Orthogonal Situational Niche）**：
   每一对 A/B 分支必须回答**“什么局势/敌方阵容下选 A，什么局势下选 B”**，且二者至少在一个核心机制维度上正交：
   - 例如：**爆发斩杀 vs 持续拉扯**、**单体决斗 vs 团战阵地/群控**、**主动先手开团 vs 反手保排/自保**、**高风险高前摇上限 vs 高可靠度稳定下限**。
   - 检验标准（Sirlin Viable Options 测试[^sirlin-p2]）：在设计评审阶段，必须能明确写出至少两个真实对局场景——场景 1 中选 A 胜率显著高于 B，场景 2 中选 B 胜率显著高于 A；若写不出来，说明该分支存在纯上位替代风险，必须重做机制而非微调数值。
2. **同层等预算配平（Equal Power Budget across A & B）**：
   使用第 3 节的统一预算模型，强制核验同层 $\text{Budget}(A) \approx \text{Budget}(B)$。如果 A 分支多了位移、硬控、缓时、护盾或更高可靠度，其直接伤害或冷却必须显式扣税让渡给 B 分支，形成清晰的“机制换数值”交易。
3. **角色 $2 \times 2 \times 2 = 8$ 种终局组合包络审计**：
   对每个英雄的 8 种分支组合，逐一核查四项极值指标：
   - 最大 1.5s 爆发伤害（防 All-Burst 突破秒杀红线）；
   - 最大 6s 持续 DPS（防纯持续流打穿坦克与 Boss 节拍）；
   - 最大连续控制/缓时覆盖率（防 All-CC 形成单人锁死）；
   - 最大额外护盾/治疗 EHP（防 All-Sustain 变成无赖永动机）。
   如果某一层确实需要在输出与防御间二选一，则另外两层应优先锚定在**同主题横切**（例如：一层专职决定“输出形态 A/B”，另一层专职决定“功能/生存形态 A/B”），从结构上杜绝三层同向极端堆叠。

---

## 五、一级参考文献与权威出处清单

[^riot-bf]: Riot Games, */dev: Champion Balance Framework*, League of Legends Official Dev Blog (2019). https://www.leagueoflegends.com/en-us/news/dev/dev-champion-balance-framework/
[^riot-bf-2020]: Riot Games, */dev: Balance Framework Update*, League of Legends Official Dev Blog (2020-06-30). https://www.leagueoflegends.com/en-us/news/dev/dev-balance-framework-update/
[^riot-s11]: Riot Games, *Preseason 2021 Gameplay Plans* (Ability Haste linear scaling replacing CDR & Item System goals), League of Legends Official Dev Blog (2020). https://www.leagueoflegends.com/en-us/news/dev/preseason-2021-gameplay-plans/
[^riot-1210]: Riot Games, *Quick Gameplay Thoughts: 5/6 (Champion Durability Update, Patch 12.10: +70 Base HP, +14 HP/lvl, +1.2 Armor/lvl, +0.8 MR/lvl & sustain/turret adjustments)*, League of Legends Official Dev Blog (2022-05-06). https://www.leagueoflegends.com/en-us/news/dev/quick-gameplay-thoughts-5-6/
[^riot-2024s3]: Riot Games, */dev: 2024 Split 3 Gameplay Changes* (Champion vs. Item Power Budget, reducing Legendary item stat efficiency, Ability Haste & Movement Speed inflation), League of Legends Official Dev Blog (2024). https://www.leagueoflegends.com/en-us/news/dev/dev-2024-split-3-gameplay-changes/
[^riot-sub]: Riot Games, *Quick Gameplay Thoughts: Addressing Mythics* & Champion Class/Subclass Design Philosophy (Power Budget across kits/items, Reliability vs. Counterplay). https://www.leagueoflegends.com/en-us/news/dev/quick-gameplay-thoughts-addressing-mythics/
[^gdc-street]: Greg Street (Riot Games), *Balancing 'League of Legends' for Every Player, from Bronze to Bengi*, GDC 2017, GDC Vault. https://www.gdcvault.com/play/1024398/Balancing-League-of-Legends-for
[^lol-wiki-gold]: Official League of Legends Wiki (hosted/supported by Riot Games), *Gold efficiency* & *Ability haste* baseline valuation standards. https://wiki.leagueoflegends.com/en-us/Gold_efficiency
[^lol-wiki-ms]: Official League of Legends Wiki, *Movement speed* (Soft cap formulas at 415 / 490 and lower slow soft cap at 220). https://wiki.leagueoflegends.com/en-us/Movement_speed
[^dota-720]: Valve Corporation, *Dota 2 Gameplay Update 7.20* (Armor formula rework & structure/creep armor recalibration, Nov 2018). https://www.dota2.com/patches/7.20
[^dota-727]: Valve Corporation, *Dota 2 Gameplay Update 7.27* (Armor formula standardized back to $0.06 \cdot A / (1 + 0.06 |A|)$, Jun 2020). https://www.dota2.com/patches/7.27
[^dota-733]: Valve Corporation, *Dota 2: The New Frontiers Update (Patch 7.33)* (Comprehensive Disables Reduction across almost all crowd control durations, Apr 2023). https://www.dota2.com/newfrontiers
[^dota-armor]: Liquipedia Dota 2 Mechanics Reference, *Armor* (Linear +6% physical EHP per armor point & formula history). https://liquipedia.net/dota2/Armor
[^dota-mr]: Liquipedia Dota 2 Mechanics Reference, *Magic Resistance* (Multiplicative stacking $1 - \prod (1 - r_i)$). https://liquipedia.net/dota2/Magic_Resistance
[^dota-sr]: Liquipedia Dota 2 Mechanics Reference, *Status Resistance* (Crowd control duration reduction mechanics). https://liquipedia.net/dota2/Status_Resistance
[^dl-res]: Deadlock Wiki, *Damage Resistance* (Multiplicative diminishing returns stacking for Bullet & Spirit Resistance). https://deadlock.wiki/Damage_Resistance
[^dl-boon]: Deadlock Wiki, *Boon* (Level scaling & category investment progression). https://deadlock.wiki/Boon
[^hots-2015]: Blizzard Entertainment, *Heroes of the Storm Patch Notes — November 17, 2015* (Global ~4% exponential per-level scaling overhaul & PvE/respawn recalibration). https://news.blizzard.com/en-us/heroes-of-the-storm/19907297/
[^hots-tiers]: Blizzard Entertainment (*Heroes of the Storm* Design Team), *Talent Tree & Themed Tiers Design Philosophy* (preventing "all-survivability" cross-tier stacking and mandatory tax talents, official Blizzard dev commentary & May 2019 Designer AMA).
[^ow2-s9]: Blizzard Entertainment, *Director's Take & Overwatch 2 Season 9: Champions Update* (Global HP pool increase, projectile size consistency pass, -20% Damage Role healing reduction passive & +10% Ultimate cost, Feb 2024). https://overwatch.blizzard.com/en-us/news/24059015/
[^smite-95]: Hi-Rez Studios, *SMITE 9.5 "The Slipknot Update" Patch Notes* (Global +40% base HP & physical protections, +28% magical protections TTK overhaul & PvE tower/jungle buff, May 2022). https://www.smitegame.com/news/slipknot-9-5-update-notes/
[^smite-97]: Hi-Rez Studios, *SMITE 9.7 Mid-Season Update* (Visible Diminishing Returns UI, 4 stacks × 20% CCR over 15s window, Hard CC vs. True Hard CC vs. Soft CC formal classification, Jul 2022).
[^schreiber-l3]: Ian Schreiber, *Game Balance Concepts — Level 3: Transitive Mechanics and Cost Curves* (2010; expanded in *Game Balance*, Ian Schreiber & Brenda Romero, CRC Press, 2021). https://gamebalanceconcepts.wordpress.com/2010/07/21/level-3-transitive-mechanics-and-cost-curves/
[^schreiber-l4]: Ian Schreiber, *Game Balance Concepts — Level 4: Probability and Randomness* (Expected value & hit-reliability valuation). https://gamebalanceconcepts.wordpress.com/2010/07/28/level-4-probability-and-randomness/
[^schreiber-l6]: Ian Schreiber, *Game Balance Concepts — Level 6: Situational Balance* (Conditional uptime & player-agency weighting). https://gamebalanceconcepts.wordpress.com/2010/08/11/level-6-situational-balance/
[^schreiber-l9]: Ian Schreiber, *Game Balance Concepts — Level 9: Intransitive Mechanics* (Orthogonal rock-paper-scissors tradeoffs vs. strict dominance). https://gamebalanceconcepts.wordpress.com/2010/09/01/level-9-intransitive-mechanics/
[^sirlin-p2]: David Sirlin, *Balancing Multiplayer Games, Part 2: Viable Options*, Sirlin.net. https://sirlin.net/articles/balancing-multiplayer-games-part-2-viable-options
