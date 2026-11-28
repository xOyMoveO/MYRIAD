---
name: myriad-balance
description: 在万象 MYRIAD 中设计或核对角色、购点、补给和地图经济的数值变更。适用于快照迁移、同场景调参、连招与多目标分析、平衡提醒和实施移交。
---

# 万象数值平衡

先确定本次要回答的玩家问题。例如“夜影绕背后需要补几下普攻”能决定场景、输出指标和需要的证据。记下游戏提交或配对快照、模式、等级、实际可用金币、购点、分支及目标初态。

- **接入或迁移规则**时，看[实施规格的批次与底层契约](../../../docs/balance/implementation-handoff.md#实施批次)。找到生效的服务端定义，沿同一字段核对介绍卡与导出。登记名录和可计算字段分别计数。固定输入比较迁移前后，预期改变的玩法单列。
- **比较平衡方案**时，看[场景选择与复算](references/scenarios.md)。在同一版本场景上并列当前规则与候选值。八个候选技能组成八套合法终局分支，Q、W 共同可用，被动、E、R 各二选一。输出极值时写明搜索范围。
- **核对或移交读数**时，看[证据记录](references/evidence.md)。可直接填写[报告模板](assets/review-template.json)，参照[夜影实例](assets/yingxiao-review.json)。保留真实零、有效的定性反馈及尚待测量的项目。
- 三级平衡提醒都供策划判断。人工值可照常保存、构建、试玩、合并和发布。单独记录真实的编译、打包及引擎专项结果。

从仓库根目录渲染随附实例，Python 仅使用标准库。把末尾的实例路径换为自己的报告即可。

```sh
python .agents/skills/myriad-balance/scripts/render_review.py .agents/skills/myriad-balance/assets/yingxiao-review.json
```

术语采用根[领域术语表](../../../GLOSSARY.md)。预算和证据的长期口径见[架构决策](../../../docs/adr/0011-balance-budget-and-evidence.md)。
