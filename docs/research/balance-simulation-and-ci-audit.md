# 游戏工业界离线数值模拟、单一真源与自动化平衡审计实践调研

> **关联工单**：`xOyMoveO/MYRIAD#90`（`wayfinder:research`，归属总图 `#88`）
> **调研范围**：人工大规模实测之前的离线数值建模、经济与构筑求解、离散事件战斗模拟、单一真源（SSOT）工程模式、CI 平衡门禁与“设计豁免”治理。
> **资料原则**：所有结论均追溯至一级权威来源（GDC 演讲录像/档案、工作室官方工程博客与开发者专栏、开源模拟引擎官方仓库与文档、同行评审论文与框架官方文档）。

---

## 摘要（Executive Summary）

1. **离线验证必须分层，且中小团队的投入产出拐点落在“L2 经济/构筑求解 + 轻量 L3 连招离散事件模拟”**：工业界普遍将人工大规模实测前的验证分为四层——**L1 静态解析预算、L2 时间轴经济与最优构筑求解、L3 离散事件战斗/循环模拟、L4 引擎无头实跑校验**。L1 + L2 可在毫秒至秒级拦截 70%–80% 的结构性数值溢出、曲线断裂、主导策略（Dominant Strategy）与经济雪球失控；轻量 L3（单英雄/固定连招模板的离散事件队列）擅长捕获冷却错位、资源枯竭、状态叠加断档与阈值跳变；而重度 L4（带空间走位、博弈决策或深度强化学习的完整对战）在中小团队进入边际收益骤降区——构建与维护成本呈数量级上升，且 AI 策略与真人博弈存在系统性分布偏差。因此 L4 应收敛为**引擎权威语义抽检（如 Fabric GameTest）**，而非试图替代策划的自动平衡机器。
2. **既有代码库落地单一真源（SSOT）的最优路径是“混合分层 + 构建期快照导出”，而非一次性全量重构为纯 JSON**：纯数据驱动（JSON/KV）虽便于跨工具共享，但复杂技能机制容易在配置文件里长出难以维护的“半成品脚本语言”；纯代码声明则导致离线工具与 UI 文案持续漂移。工业界（如 Factorio `--dump-data`、Riot `.bin.json` + 令牌插值、Path of Exile 数据导出管线）的成熟做法是：**纯数值与曲线参数逐步收口为带 Schema 校验的数据文件，复杂机制仍由强类型代码实现，但在构建期自动导出全量机读快照（Snapshot JSON）供离线分析器消费，同时以参数令牌插值或快照反向校验锁死 UI 技能描述**。
3. **CI 平衡门禁能否长期存活，取决于“硬红线极简 + 有效误报率受控 + 显式可审计的设计豁免”**：Google Tricorder 的大规模工程实证表明，开发者眼中的“误报”是任何他们不打算修复的告警（Effective False Positive），告警噪音一旦抬头，整个检查系统就会被静默忽略。成熟游戏团队严格区分**阻断 CI 的硬红线**（Schema/引用完整、数学定义域、极端边界与代码-快照一致性）与**软性诊断告警**（预算偏离、帕累托支配、曲线拐点异常），并通过版本化的**设计豁免清单（Allowlist）**显式记录刻意非对称机制的策划理由、受控放宽区间、责任人与复审期限，避免平衡工具压死角色特色或沦为无人阅读的噪音。

---

## 一、离线验证分层架构与投入产出（ROI）拐点

工业界在玩家大规模实战与线上遥测（Live Telemetry）之前，并非直接跳到“让 AI 打一万局”，而是按**抽象程度由高到低、执行速度由快到慢、保真度由低到高**构建四层漏斗。每一层解决不同类别的失衡问题，并具备截然不同的工程性价比。

### 1.1 四层离线验证能力与 ROI 对照表

| 验证层级 | 代表工业实践 / 一级工具 | 核心计算方法 | 最擅长捕获的失衡与数值 Bug | 执行耗时与维护成本 | 局限性与盲区 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **L1：静态解析预算与公式审校** | **Path of Building**（`ModStore`/`ModDB`/`Calcs.lua` 确定性依赖图）[1]；**Supercell** 攻防指标与钻石价值抽象[2]；**Ian Schreiber** 成本曲线与隐性成本模型[3] | 单次无随机性的解析公式求值、属性加权汇总、单位成本效率（DPS/Gold、EHP/Gold、伤害/蓝耗、控制秒数/冷却） | 数量级笔误（小数点错位）、单技能超模/废模、收益递增导致的后期数值爆炸、基础属性性价比严重倒挂、零购买基线偏离 | **极低**：单次计算毫秒级；脚本/表维护成本最低，每次提交均可全量执行 | 无法反映技能冷却节奏、资源回复瓶颈、Buff 覆盖率波动及多阶段经济复利 |
| **L2：时间轴经济与最优构筑求解** | **Machinations**（存量-流量图与云端蒙特卡洛预测）[4][5]；**Square Enix** *Grimms Notes Repage* 遗传算法组合搜索[6]；**GEEvo** 经济图进化平衡[7]；**Volz et al.** 多目标帕累托平衡[8] | 存量-流量时间轴推演、蒙特卡洛批量采样、线性/非线性规划（LP/NLP）、边际收益局部求导、帕累托前沿（Pareto Frontier）与敏感度分析（Sensitivity Analysis） | 经济通胀/通缩死锁、指数续购下的最优加点塌缩（唯一解）、劣势追赶机制过弱（不可逆雪球）或过强（送头反赚）、构筑被严格帕累托支配、微调参数即引爆胜率/TTK 的高敏感悬崖点 | **低至中**：Python 求解器秒级完成；模型贴近数学本质，改动一次即可重跑全曲线 | 依赖对战斗收益的抽象代理指标（如理论 DPS/EHP），容易忽略技能前摇、连招卡手与触发链时序 |
| **L3：离散事件技能循环与战斗模拟** | **SimulationCraft**（C++ 优先事件队列 + APL 动作优先级列表 + PRNG 多轮迭代 + 扰动缩放因子）[9][10]；*Evolving the Hearthstone Meta* 轻量规则对局模拟[11] | 离散事件模拟（Discrete Event Simulation, DES）：维护按时间戳排序的事件优先队列，按 APL 规则驱动施法、冷却、资源回复、持续伤害（DoT）与随机触发（Proc），通过微扰法计算属性边际权重（Scale Factors） | 冷却与公共冷却（GCD）错位、长战中蓝量/能量断档、触发链/叠层机制的非线性雪崩、爆发窗口（Burst Window）TTK 过短秒杀、不同技能分支在实战循环中的真实覆盖率失衡 | **中**：单英雄/木桩循环秒级至十秒级；若强行模拟复杂多人技能交互，则需维护独立于引擎的第二套战斗逻辑（漂移风险上升） | 脱离 3D 物理空间（弹道命中率、地形遮挡、位移拉扯、走位躲避只能靠命中系数近似） |
| **L4：引擎无头实跑与自动化探索校验** | **Riot Games** *League of Legends* 无头客户端 + 机器人 CI（日跑 ~10 万用例）[12]；**Ubisoft Reflections** *The Division* Client Bots[13]；**EA SEED** 深度强化学习自动化测试[14]；**Square Enix** *SaGa Emerald Beyond* RL 战局平衡[15]；**Fabric GameTest**[16] | 剥离渲染管线（Headless）并以固定步长加速运行真实引擎/服务端代码，配合脚本机器人、导航网格遍历或强化学习（RL）智能体运行实局并采集结构化遥测 | 离线模型与真实引擎的语义偏差（如伤害结算顺序、护盾判定、韧性叠乘、冷却缩减取整错误）、引擎级死循环/崩溃/卡死、地图可达性与碰撞漏洞、复杂机制交互的真实回归 | **高至极高**：确定性单元测试/GameTest 成本可控（分钟级）；但带博弈与空间走位的 AI/RL 对战需要极高算力、确定性隔离工程与持续重训成本 | 规则改一次 RL 模型可能失效；AI 极易利用非人类微操或局部漏洞过拟合，无法直接等价于真人体验 |

### 1.2 工业界各层的典型机理拆解

#### L1：从“拍脑袋调数”到确定性依赖图与统一估值基线
* **Path of Building（PoB）的确定性依赖图范式**：在 *Path of Exile* 这样拥有数千个天赋节点与词缀的系统中，PoB 没有盲目模拟 5 分钟战斗，而是通过 `ModList -> ModDB -> ModStore` 的三层修饰符抽象与 `Calcs.lua` 依赖图，在单次确定性遍历中把基础值、加算池（`INC`）、乘算池（`MORE`）和条件标签解析为精确的有效 DPS（eDPS）与有效生命池（EHP）[1]。这种架构使“给某属性 +10 点看边际收益”可以在毫秒内完成，且完全消除蒙特卡洛噪声。
* **Supercell 的统一价值锚点**：Supercell 设计师 Touko Tahkokallio 在 GDC 演讲 *Design in Depth at Supercell* 中指出，系统数值平衡的核心在于建立跨系统的**中间价值换算单位**（例如 *Boom Beach* 的攻防量化分、*Hay Day* 的钻石价值锚点），让设计师能在同一把尺子上审视不同资源、兵种与建筑的产出效率，而不是孤立调整单张表[2]。
* **成本曲线与隐性成本（Shadow Costs）**：Ian Schreiber 在 *Game Balance Concepts* 中进一步指出，静态预算不能只算面板费用（如蓝耗/金币），还必须显式计入**沉没成本（前置技能点/属性阶梯）**与**机会成本（互斥技能分支放弃的另一条路线）**[3]。

#### L2：时间轴经济闭环、帕累托前沿与敏感度分析
* **存量-流量反馈与蒙特卡洛预测**：Joris Dormans 与 Ernest Adams 在 GDC 引入的 **Machinations** 框架将游戏经济抽象为资源池（Pools）、源（Sources）、汇（Sinks）、转换器（Converters）与条件/随机门（Gates），通过状态连线刻画正反馈（滚雪球）与负反馈（追赶/衰减）闭环，并结合云端蒙特卡洛批量模拟输出第 10/50/90 百分位资源曲线，提早暴露“10 分钟后经济锁死”或“滚雪球不可逆”等动态缺陷[4][5]。
* **最优构筑求解与帕累托支配检验**：当玩家面对多维度加点或组合选择时（例如万象的“金币五属性指数续购 + 1–20 级互斥技能分支”），单看每个选项的静态单价会失效。工业界与学术界（如 Volz et al. 的多目标自动平衡框架[8]、Rupp & Eckert 的 GEEvo 经济进化框架[7]、Square Enix 在 *Grimms Notes Repage* 面对 $10^{100}$ 级装备组合时使用的遗传算法与可视化分析[6]）普遍采用以下三类数学分析：
  1. **边际收益最优路径求解（LP / NLP / 动态规划）**：给定时间轴累计金币 $G(t)$，求解每分钟使有效输出或综合战力最大化的加点序列，检查是否存在“永远不该点某属性”的死属性，或“无脑堆单一属性”的退化最优解。
  2. **帕累托前沿（Pareto Frontier）审查**：在“爆发伤害 vs 持续 DPS vs 生存 EHP vs 控制覆盖率”等多目标空间中绘制所有英雄/技能分支的帕累托前沿。如果某个分支在所有关注维度上都严格弱于另一互斥分支（被帕累托支配），则几乎必然构成“陷阱选项”（Trap Option）[3][8]。
  3. **敏感度分析（Sensitivity Analysis）**：对关键参数（如属性续购底数 `1.2`、单点攻击/法强增量、防御塔减伤斜率、护甲公式常数）做 $\pm 5\% / \pm 10\%$ 微扰，观察最优加点路径、核心 TTK 与 16–20 分钟推进节点是否发生剧烈跳变。高敏感度参数往往意味着系统处于“悬崖边缘”，后续每次微调都极易引发连锁雪崩。

#### L3：离散事件模拟与动作优先级列表（APL）
* **SimulationCraft（SimC）架构启示**：对于技能循环、冷却穿插、资源回复与触发链（Proc）高度耦合的战斗系统，静态公式无法准确估算“真实覆盖率”。SimC 作为 *World of Warcraft* 生态最成熟的开源战斗模拟引擎，采用基于事件优先队列的离散事件模拟（DES）[9]：
  * **APL（Action Priority List）**：用声明式规则（如 `actions+=/skill_e,if=buff.mark.stack>=3&mana>=75`）描述典型连招决策，避免写死复杂 AI 状态机[9]。
  * **微扰缩放因子（Scale Factors）**：在基线仿真之上，逐个为单一属性叠加微小增量（如 `+100`）重跑同种子随机序列，用输出增量除以属性增量得到实战环境下的属性边际权重[10]。
* **适用边界**：L3 最适合**固定木桩、双人换血（1v1 站桩/周期性脱战）、或带预设命中率与受击频率的典型交战窗口（如 6 秒爆发窗、20 秒持续团战窗）**，不需要也不应该在离线模拟器里重写物理引擎。

#### L4：引擎无头实跑的真实定位——“验证引擎”而非“替代设计”
* **Riot Games 与 Ubisoft 的工程现实**：Riot 在官方技术博客 *Automated Testing for League of Legends* 中披露，其 CI 体系每天运行约 10 万条自动化测试，核心手段之一是剥离图形渲染的**无头客户端（Headless Clients）+ 自动化脚本机器人**，使开发者提交后约一小时内获得服务端-客户端交互、技能逻辑与回归问题的反馈[12]。Ubisoft Reflections 在 *The Division* 中同样用导航网格驱动的 Client Bots 自动跑通任务、排查卡死与收集性能遥测[13]。
* **EA SEED 与 Square Enix 的 RL 探索边界**：EA SEED 的论文 *Augmenting Automated Game Testing with Deep Reinforcement Learning*（arXiv:2103.15819）与 Square Enix 在 GDC 2024 的 *SaGa Emerald Beyond* 演讲展示了强化学习在自动探索地图漏洞、量化关卡难度与辅助回合制战局平衡方面的潜力[14][15]。但 EA SEED 同时指出生产部署的关键成本障碍：必须改造引擎实现**严格确定性固定步长、逻辑与表现层 RNG 彻底隔离**，且标准 RL 智能体极易过拟合单一捷径策略，需要训练多样化智能体种群才能逼近人类探索行为[14]。

### 1.3 投入产出（ROI）拐点判断

综合上述一级实践，离线平衡工具链的**边际收益曲线存在一个非常清晰的拐点**：

```text
投入产出比（拦截真实失衡 / 工程维护成本）
  ▲
  │        [L2 经济时间轴 + 构筑求解]
  │            ★ 最高 ROI 甜蜜点
  │         ／    ＼
  │  [L1 静态预算]   [轻量 L3 连招 DES]
  │     ／                ＼
  │    ／                   ＼   <── 拐点：开始重写引擎/引入空间博弈 AI
  │   ／                      ＼
  │  ／                         ───▶ [重度 L4：3D 走位 AI / RL 对战]
  └──────────────────────────────────────────────────────────▶ 工程与维护成本
                                  （但 [确定性 L4 引擎抽检/GameTest] 保持高 ROI）
```

* **拐点之前（高 ROI 区）**：**L1 静态预算 + L2 时间轴经济与最优构筑/敏感度求解 + 轻量 L3 单英雄/木桩连招 DES + 确定性 L4 引擎公式抽检**。这四者共享同一份机读数值，能在无 GPU、无真人参与的纯 CI 环境中在数秒到数分钟内完成全量扫描。
* **拐点之后（边际收益骤降区）**：试图在离线模拟器中复现 3D 走位、地形遮挡、技能预判、多英雄团队配合，或为实时动作/MOBA 游戏训练多智能体 RL 来直接输出“英雄胜率”。此类方案对中小团队而言研发与重训成本极高，且每次角色机制重做都会让训练成果作废，其预测的“AI 胜率”在空间博弈类游戏里也无法直接映射为人类段位体验。

---

## 二、单一真源（Single Source of Truth, SSOT）工程模式对比

游戏开发中最隐蔽、最耗时的平衡事故往往不是公式算错，而是**三处漂移（Drift）**：服务端实际结算写一套数、客户端 UI 技能介绍卡写另一套手写文本、策划表格/离线 Python 分析脚本又维护第三套拷贝。工业界围绕既有代码库解决这一问题，主要演化出三种工程模式。

### 2.1 三种主流工程模式横评

| 维度 | 模式 A：纯数据驱动（JSON / KV / 表格直读） | 模式 B：代码声明 + 构建期/启动期导出快照 | 模式 C：混合分层（数值表驱动 + 代码机制 + 统一构建快照） |
| :--- | :--- | :--- | :--- |
| **典型工业案例** | **Valve Dota 2**（`npc_abilities.txt` KV 配置驱动服务端技能与本地化令牌）[17]；**Riot LoL**（`.bin.json` 资产数据管线 + 文本解释器变量绑定）[18]；**Bungie** 数据驱动资产依赖管线[19] | **Factorio**（Lua 原型阶段定义 `data.raw`，通过原生命令行 `--dump-data` 启动期导出完整 JSON 给外部计算器/工具，并配机读原型 API 文档）[20][21] | **Path of Exile + PoB / RePoE**（基础表与词缀结构化导出，复杂机制由引擎/计算器解释）[1]；**Machinations ↔ Google Sheets**（长表留表格，拓扑逻辑留模型双向同步）[5]；**Overwatch** ECS 数据组件与逻辑系统解耦[22] |
| **数值修改成本** | 极低（改表/改 JSON 即可热重载或重测，无需改逻辑代码） | 中（改常量需编译，但重构时 IDE 全局引用跳转无死角） | 低至中（纯数值在 JSON 中直接改，机制分支变更在代码中改） |
| **编译期类型安全** | 弱（易出现字段拼写错误、类型错配，必须依赖 JSON Schema + 代码生成器补齐） | **最强**（强类型语言编译期直接拦截缺失字段与类型错误） | **强**（JSON 经 Schema 校验并映射为强类型 Java `record`/DTO，代码侧零魔法字符串） |
| **三处漂移风险** | 若仅数据文件存在但机制公式散落在代码里，仍会出现“部分数据在表、部分硬编码在代码”的半漂移 | 运行时与离线分析器**零漂移**（快照由真实代码导出）；但若快照未纳入 CI 强校验，离线脚本可能读到过期快照 | **最低**（运行时读数据文件 + 代码常量，构建期一键聚合导出完整 `snapshot.json`，UI 描述与离线工具共同消费同一真源） |
| **复杂非对称机制表达力** | 差（硬把特殊机制塞进 JSON 会催生难以调试的“JSON 编程/DSL 泥潭”） | **极强**（任意条件判定、状态机、时序逻辑均用宿主语言表达） | **极强**（“什么数值”归数据，“怎么结算”归代码，职责边界最清晰） |
| **既有项目渐进迁移难度** | 高（要求一次性抽离全部硬编码数值与公式，前期改造风险大） | **极低**（不改现有代码结构，先加导出器即可让离线工具跑起来） | **低（最适合渐进演进）**：先用模式 B 导出快照止血，再逐批把高频调优参数下沉为模式 A 数据文件 |

### 2.2 防治 UI 技能描述漂移的工业实践

技能描述卡（Tooltip）是单一真源最容易失守的最后一公里。Riot 在早期 *League of Legends* 开发中曾因大量手写技能描述导致版本更新后频繁出现文案与真实伤害不符，后来全面转向**“数据驱动模板 + 命名变量插值”**（如在本地化模板中写 `{{ wdamage }}`，由渲染引擎直接从技能数据对象读取实时数值）[18]。Valve 的 *Dota 2* 同样通过解析 `npc_abilities` 中的 `AbilitySpecial` / `AbilityValues` 键值并在本地化字符串中通过 `%variable%` 插值生成技能说明，避免人工抄写数值[17]。

对于**已有大量手写公式文案**的项目，工业界通常采用两步平滑治理：
1. **过渡期（不改 UI 渲染逻辑）**：构建期导出快照后，由 CI 脚本用正则/AST 抽取技能描述中的关键数值字面量（基础伤害、每级成长、属性系数、冷却、蓝耗、持续时间）与快照做交叉比对，任何不一致直接报错。
2. **稳态期（彻底根除双写）**：将技能描述改为带占位符的模板（例如 `"造成 {base}+{perRank}(r-1)+{apRatio}S 魔法伤害"`），由服务端/客户端共享的格式化器在加载期或渲染期从结构化数据中填充数值。

---

## 三、平衡门禁与“设计豁免（Design Exceptions）”治理

自动化平衡检查器最大的失败模式不是“漏检一个 2% 的数值偏差”，而是**在上线三个月后因为噪音过多被团队彻底无视**。解决这个问题的关键在于把软件工程的静态分析治理经验与游戏设计的非对称哲学结合起来。

### 3.1 从 Google Tricorder 看告警疲劳治理

Google 在 ICSE 2015 / CACM 2018 发表的经典论文 *Tricorder: Building a Program Analysis Ecosystem*（Sadowski et al.）总结了大规模自动化检查工具成败的决定性规律[23]：

* **重新定义“误报”（Effective False Positive）**：对工具作者而言，只要规则命中了就是真阳性；但对实际使用者而言，**任何让他们看过之后选择“不修改”的告警，都是有效误报**[23]。
* **保持极低的有效误报率**：Tricorder 对所有接入检查器强制设定极高的信噪比门槛；若某条规则长期被用户点击 `"NOT USEFUL"` 忽略，工具团队必须调优阈值、补充上下文或直接下线该规则，绝不允许“带病运行”[23]。
* **嵌入评审流而非孤立仪表盘**：独立网页看板几乎没人每天打开；必须把结构化诊断直接投递到 Pull Request / 代码评审流中，只对本次变更触及的实体高亮差异，并给出可执行的修复或豁免指引[23]。
* **根因聚合与分支分级**：Ubisoft Montreal 在 *Assassin's Creed Origins* 的世界数据监控与验证实践（GDC 2018）以及现代游戏数据 CI 管线普遍强调：**按根因去重聚合**（避免修改一条全局公式时刷屏 120 条孤立报错）与**分支分级**（实验/开发分支以软告警为主，保护迭代速度；主干合并与发布分支对硬红线强制阻断）[24]。

### 3.2 CI 门禁的三级分层标准

成熟团队严格划分以下三级门禁，**宁可把不确定的平衡判断降级为软告警，也绝不用主观美学规则阻断 CI**：

| 门禁级别 | CI 行为 | 适用检查项（示例） | 判断准则 |
| :--- | :--- | :--- | :--- |
| **Tier 1：硬红线（Hard Fail / Blocking）** | **必须阻断合并** | 1. JSON Schema / 必填字段 / 引用 ID 完整性<br>2. 数学定义域与物理常识边界（NaN/Inf、负冷却、零冷却且零蓝耗的无限施法、负价格、整数溢出）<br>3. 真源一致性（构建期快照未同步、UI 技能描述字面量与权威数值不一致）<br>4. 确定性引擎回归锁（权威公式单测与 GameTest 不通过）<br>5. 极端安全护栏（如单技能 1 级基础伤害突破硬上限 300% 明显属小数点笔误） | **100% 客观、零争议、必然是 Bug 或同步失误**，几乎不需要设计讨论 |
| **Tier 2：受豁免约束的平衡门禁（Guarded Gate）** | **默认阻断或强提醒，允许通过版本化豁免清单放行** | 1. 单技能/单英雄强度预算超出同定位基准区间（如偏离 $\pm 15\%$）<br>2. 互斥技能分支出现严格帕累托支配（某分支全维度劣于另一分支）<br>3. 金币五属性最优路径中某属性在 0–20 分钟全程边际收益垫底（死属性）<br>4. 经济推演关键里程碑（如满级时间、核心属性阶梯、防御塔保护期推进速率）偏离 16–20 分钟锚点 | **大概率是无意失衡，但允许策划基于明确机制特色申请结构化豁免** |
| **Tier 3：软性诊断与趋势报告（Informational / Diff Report）** | **永不阻断，仅作为 PR 评论 / 产物附件呈现** | 1. 变更前后的数值 Diff 摘要（DPS/EHP/TTK/经济曲线变化百分比）<br>2. 全英雄帕累托前沿位次变动与敏感度热力图<br>3. L3 典型连招循环的时间轴覆盖率与蓝耗曲线图 | **辅助策划做主观取舍与权衡**，不预设单一“正确答案” |

### 3.3 “刻意非对称特色设计（设计豁免）”的治理实践

David Sirlin 在 *Balancing Multiplayer Games* 中指出，真正有趣的多人竞技游戏必然包含大量**非对称设计与不可比元素（Incomparables）**——你无法用同一条线性伤害公式同时给“时停减速场”“自动索敌炮台”“地形锚点钩索”和“高风险拔刀斩”定价[25]。同样，Riot Games 的 *Champion Balance Framework*（官方开发者博客）在建立覆盖四个玩家分层（Average / Skilled / Elite / Pro）的量化阈值后，也明确承认职业赛场特殊生态与高操作上限英雄会产生结构性例外，需要在重大赛事周期或特定机制重做窗口内通过显式治理流程进行特例处理，而不是为了满足表格统一而阉割英雄个性[26][27]。

为了既守住系统底线、又为高差异机制留出“受控特色留白”，工业界推荐用**仓库内版本化的结构化豁免清单（例如 `balance_exceptions.json`）**替代“口头免检”或“直接关掉检查器”：

```json
{
  "exceptions": [
    {
      "id": "EXC-KEHENG-R-ZONE-BUDGET",
      "target": "keheng_yimiao",
      "rule": "skill_damage_budget_ratio",
      "category": "intentional_asymmetry",
      "overrideRange": [0.55, 0.80],
      "rationale": "大招核心价值在于 4 格穹层 0.40-0.30 行动/弹体缓时与区域封锁，并非纯数值爆发；伤害预算刻意压低至同级均值 55%-80%，不以纯伤害公式对齐。",
      "counterplayOrCost": "0.65s 独立预警 + 无眩晕/不停时 + 160 高蓝耗与 60-70s 长冷却",
      "owner": "design-keheng",
      "createdAt": "2026-11-27",
      "reviewAt": "milestone:map-complete"
    }
  ]
}
```

这套治理机制有四条铁律：
1. **只放宽边界，不“无限免检”**：使用 `overrideRange` 声明受控区间（例如允许伤害预算落在 `0.55–0.80`），而不是简单的 `ignore: true`。如果未来某次改动把该技能误调成了 `0.10` 或 `2.50`，门禁依然会立刻报警。
2. **强制填写设计意图与代价（`rationale` + `counterplayOrCost`）**：豁免记录本身就是活的设计文档（类似迷你 ADR），评审 PR 时任何人都能一眼看懂“这里为什么刻意不对称，靠什么代价换取”。
3. **强制绑定复审触发点（`reviewAt`）**：支持按里程碑或日期复审；到期后自动从静默放行降级为提醒复查，防止临时妥协演变为永久僵尸豁免。
4. **陈旧豁免自动清理提醒（Stale Waiver Check）**：如果某条豁免对应的目标在后续调优中已经重新回到正常区间，CI 主动提示“该豁免已不再需要，请删除”，保持豁免表长期精简。

---

## 四、面向万象（Fabric 26.3 / Java 25 / Python 工具链）的落地建议

结合万象当前仓库的实际结构与总图 `#88` 的已定边界（不重做金币五属性与互斥分支核心养成形态、不做 3D 走位 AI 对战、以 `docs/adr/0008-map-match-tempo.md` 的 16–20 分钟对局时长为顶层锚点），建议采用**四步低侵入渐进落地路线**：

### 4.1 现状诊断（基于仓库真实代码）
* **数据分散现状**：目前技能元数据（`maxRank`、`mana`、`cooldowns`）与**手写公式文本** `description` 存放在 `src/main/resources/data/myriad/role_rework/*.json`（如 `keheng.json` 中直接手写 `"80+18(r-1)+0.50S"`）；而角色生命成长、金币五属性 `360 × 1.2^n` 续购定价、单点攻强/法强增量（`15.0`/`20.0`）写在 `FirstGroupGrowth.java`；经济缩放倍率 `GOLD_INCOME_SCALE = 1.40` 写在 `GlobalBalance.java`；防御塔前 5 分钟 65% 减伤、击杀赏金与劣势追赶公式写在 `MatchBalance.java`；伤害回归测试位于 Java 测试集，CI（`.github/workflows/build.yml`）目前仅执行 `bash ./gradlew build`。
* **核心痛点**：任何一次跨文件调数都存在“Java 结算逻辑 vs `role_rework/*.json` 冷却蓝耗 vs `description` 文本 vs 离线分析假设”四处漂移风险。

### 4.2 推荐架构：混合分层 SSOT + Gradle 快照导出 + Python 四层轻量审计

```text
[权威声明层]
  ├── Java 强类型机制与常量 (FirstGroupGrowth / MatchBalance / GlobalBalance / AbilityDamageFormula)
  └── 结构化资源 JSON (data/myriad/role_rework/*.json -> 逐步补充数值字段与描述模板)
            │
            ▼  Gradle 任务：./gradlew exportBalanceSnapshot（构建期零侵入导出）
[统一机读真源快照]
  └── build/balance/balance-snapshot.json（附 JSON Schema 版本校验）
            │
            ├──▶ [Python 离线平衡工具链] (python tools/balance/audit.py)
            │      ├── L1 静态解析预算（15 角色 120 技能强度预算、属性估值、文案字面量一致性校验）
            │      ├── L2 时间轴经济与构筑求解（0–20min 金币/经验曲线、360×1.2^n 边际收益最优加点、
            │      │     帕累托前沿、滚雪球/追赶闭环稳态与参数敏感度分析）
            │      └── 轻量 L3 离散事件模拟（单英雄典型连招 APL 木桩/6s 爆发与 20s 持续循环 DES）
            │
            ├──▶ [豁免清单与 CI 门禁] (tools/balance/balance_exceptions.json)
            │      ├── Tier 1 硬红线：阻断 GitHub Actions
            │      ├── Tier 2 豁免受控门禁：超界须调整数值或提交带理由/区间的豁免条目
            │      └── Tier 3 PR 诊断报告：自动生成数值 Diff 与曲线变化摘要
            │
            └──▶ [L4 确定性引擎抽检] (Fabric src/gametest + JUnit 回归锁)
                   └── 仅抽检关键技能与经济公式在真实 Fabric 服务端 Tick 下的结算语义一致性
```

### 4.3 分阶段实施要点（供后续决策与任务票拆分参考）

1. **阶段一：零侵入打通 SSOT 快照（先止血，不重构业务代码）**
   * 在 Java 侧新增一个轻量导出入口（Gradle `JavaExec` 或测试任务 `./gradlew exportBalanceSnapshot`），直接读取运行时的 `FirstGroupGrowth`、`GlobalBalance`、`MatchBalance`、`MatchTempo`、`AbilityDamageFormula` 与 `role_rework/*.json`，输出规范化的 `balance-snapshot.json`。
   * 立即建立 `description` 字面量与快照参数的交叉校验规则，把当前最容易发生的“改了代码忘改介绍卡”变成 Tier 1 硬红线。
2. **阶段二：Python 搭建 L1 + L2 核心审计器（抢占最高 ROI 区间）**
   * 用 `python` 实现纯确定性的 L1 静态预算引擎（类似极简 PoB 依赖图，消费 `balance-snapshot.json`）与 L2 时间轴经济/五属性最优加点求解器（结合 `scipy.optimize` 或离散动态规划，显式检验 `360 × 1.2^n` 在 16–20 分钟对局时长下各属性的边际收益拐点、帕累托前沿与敏感度）。
   * 严格围绕**标杆原型（例如 2–3 名风格迥异角色：刻衡、伊珂、铆钉或朔）**验证预算模型与豁免规则的有效性，避免一上来铺满 15 角色导致规则误报失控。
3. **阶段三：按需引入轻量 L3 连招 DES，严格冻结 L4 边界**
   * 仅对存在“叠层触发 / 冷却联动 / 资源续航 / 爆发窗口 TTK”风险的技能循环，在 Python 中引入几十行到百余行级的事件优先队列（`heapq`）+ 简化 APL 配置进行 L3 模拟，不引入物理碰撞与空间走位。
   * L4 保持使用仓库已配置的 Fabric `gametest` 与现有 `FirstTenDamageFormulaChecks` / `LastFiveDamageFormulaChecks` 体系，将其明确定位为**“验证真实引擎是否忠实执行了快照里的公式”**，与离线平衡审计分工互补。
4. **阶段四：接入 GitHub Actions 并固化设计豁免流程**
   * 在 `.github/workflows/build.yml` 中新增快速 `balance-audit` 步骤（或在 `./gradlew build` 之后运行 `python tools/balance/audit.py`），确保整套离线审计耗时控制在 **10 秒以内**。
   * 同步建立 `balance_exceptions.json` 与过期/陈旧豁免检查，把刻衡缓时、伊珂炮台、铆钉锚点、朔拔刀等核心非对称特色纳入“受控放宽区间 + 策划理由 + 里程碑复审”的显式治理轨道。

---

## 五、一级权威参考资料（Primary References）

### 战斗模拟与静态/离散计算引擎
- **[1] Path of Building Community Fork（开源静态确定性构筑计算引擎）** — `src/Classes/ModStore.lua`, `ModDB.lua`, `Modules/Calcs.lua` 源码仓库：
  https://github.com/PathOfBuildingCommunity/PathOfBuilding
- **[2] Touko Tahkokallio (Supercell), GDC 2017** — *Design in Depth at Supercell: Why Great Ideas Aren't Enough*（攻防指标抽象与钻石价值体系）：
  https://gdcvault.com/play/1024934/Design-in-Depth-at-Supercell
- **[3] Ian Schreiber** — *Game Balance Concepts*（成本曲线、沉没/机会隐性成本、非传递性与平衡基础课程）：
  https://gamebalanceconcepts.wordpress.com/
- **[9] SimulationCraft Official Wiki** — *ActionLists（APL 动作优先级列表与离散事件循环规范）*：
  https://github.com/simulationcraft/simc/wiki/ActionLists
- **[10] SimulationCraft Official Wiki** — *StatsScaling（微扰法属性缩放因子与边际权重计算）*：
  https://github.com/simulationcraft/simc/wiki/StatsScaling

### 经济建模、组合优化与多目标平衡
- **[4] Ernest Adams & Joris Dormans, GDC 2012** — *Introducing Machinations: A New Way to Design Game Mechanics*：
  https://www.gdcvault.com/play/1018159/Introducing-Machinations-A-New-Way
- **[5] Machinations Official Documentation** — 存量-流量经济建模、蒙特卡洛批量预测与 Google Sheets 双向同步文档：
  https://docs.machinations.io/
- **[6] Kazuko Manabe (Square Enix), GDC 2019** — *Balancing Nightmares: An AI Approach to Balance Games with Overwhelming Amounts of Data*（$10^{100}$ 级组合空间的遗传算法平衡）：
  https://www.gdcvault.com/play/1025983/Balancing-Nightmares-An-AI-Approach
- **[7] Florian Rupp & Kai Eckert (2024)** — *GEEvo: Game Economy Generation and Balancing with Evolutionary Algorithms*（图结构游戏经济进化与模拟平衡）：
  https://arxiv.org/abs/2404.18574
- **[8] Vanessa Volz, Günter Rudolph, Boris Naujoks (GECCO 2016)** — *Demonstrating the Feasibility of Automatic Game Balancing*（代理模型与多目标帕累托自动平衡奠基论文）：
  https://arxiv.org/abs/1603.03795
- **[11] Fernando de Mesentier Silva, Rodrigo Canaan, Scott Lee, Matthew C. Fontaine, Julian Togelius, Amy K. Hoover (IEEE CoG 2019)** — *Evolving the Hearthstone Meta*（轻量对局模拟驱动的卡牌环境平衡搜索）：
  https://arxiv.org/abs/1907.01623

### 无头引擎测试、自动化探索与 Minecraft Fabric 测试基础设施
- **[12] Riot Games Technology Blog** — *Automated Testing for League of Legends*（日跑 ~10 万用例与无头客户端自动化测试管线）：
  https://technology.riotgames.com/news/automated-testing-league-legends
- **[13] Ubisoft Reflections, GDC 2018** — *Automated Testing: Using AI Controlled Players to Test 'The Division'*（Client Bots 自动跑测与遥测收集）：
  https://www.gdcvault.com/play/1025859/Automated-Testing-Using-AI-Controlled
- **[14] Joakim Bergdahl, Camilo Gordillo, Konrad Tollmar, Linus Gisslén (EA SEED, IEEE CoG 2020 / arXiv 2021)** — *Augmenting Automated Game Testing with Deep Reinforcement Learning*：
  https://arxiv.org/abs/2103.15819
- **[15] Youichiro Miyake (Square Enix), GDC 2024** — *Machine Learning Summit: 'SaGa Emerald Beyond': Balancing Battle with Reinforcement Learning AI*：
  https://www.gdcvault.com/play/1034448/Machine-Learning-Summit-SaGa-Emerald
- **[16] Fabric Official Documentation** — *Automated Testing（GameTest 与服务端集成测试框架）*：
  https://docs.fabricmc.net/develop/automated-testing

### 单一真源（SSOT）、数据管线与工具提示同步
- **[17] Valve Developer Community** — *Dota 2 Workshop Tools / Scripting / Abilities Data Driven*（`npc_abilities` 键值单一数据定义与本地化令牌插值）：
  https://developer.valvesoftware.com/wiki/Dota_2_Workshop_Tools/Scripting/Abilities_Data_Driven
- **[18] Riot Games / League of Legends Dev** — *Balance Blog Collection*（数据管线、技能描述同步与平衡治理系列）：
  https://www.leagueoflegends.com/en-us/news/dev/balance-blog-collection/
- **[19] Bungie Engineering** — 官方门户（*Destiny* 资产依赖管线与数据驱动理念）：
  https://www.bungie.net/
- **[20] Factorio Official Wiki** — *Command line parameters（`--dump-data` 原型阶段导出完整 `data.raw` JSON 真源）*：
  https://wiki.factorio.com/Command_line_parameters
- **[21] Factorio Official API Documentation** — 机读原型与运行时 API 规范（`prototype` / `runtime` JSON 规范）：
  https://lua-api.factorio.com/latest/
- **[22] Timothy Ford (Blizzard Entertainment), GDC 2017** — *Overwatch Gameplay Architecture and Netcode*（ECS 数据组件与逻辑系统解耦、确定性模拟）：
  https://www.gdcvault.com/play/1024445/Overwatch-Gameplay-Architecture-and-Netcode

### CI 门禁、告警疲劳治理与非对称平衡豁免
- **[23] Caitlin Sadowski, Jeffrey van Gogh, Ciera Jaspan, Emma Söderberg, Collin Winter (Google, ICSE 2015 / CACM 2018)** — *Tricorder: Building a Program Analysis Ecosystem*（有效误报率、评审流内嵌与告警疲劳治理）：
  https://research.google/pubs/tricorder-building-a-program-analysis-ecosystem/
- **[24] Nicholas Routhier (Ubisoft Montreal), GDC 2018** — *'Assassin's Creed Origins': Monitoring and Validation of World Design Data*（大规模游戏数据监控与自动化校验实践）：
  https://www.gdcvault.com/play/1025217/Assassin-s-Creed-Origins-Monitoring
- **[25] David Sirlin** — *Balancing Multiplayer Games*（非对称平衡、可行选项、不可比元素 Incomparables 与非传递设计）：
  https://www.sirlin.net/articles/balancing-multiplayer-games-part-1-definitions
- **[26] Riot Games Developer Blog** — */dev: Champion Balance Framework*（多段位分层量化阈值与治理规则）：
  https://www.leagueoflegends.com/en-us/news/dev/dev-champion-balance-framework/
- **[27] Riot Games Developer Blog** — */dev: Balance Framework Update*（框架迭代、职业赛场例外边界与版本治理）：
  https://www.leagueoflegends.com/en-us/news/dev/dev-balance-framework-update/
