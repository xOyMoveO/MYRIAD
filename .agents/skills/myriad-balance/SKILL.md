---
name: myriad-balance
description: 在万象 MYRIAD 中导出或对比平衡快照、复跑 L1–L4 角色与经济场景、抽检平衡 GameTest、撰写平衡评审或移交数值变更时使用。
---

# 万象数值平衡

先锁定要回答的玩家问题，并记下固定初态：源码提交或配对快照、模式、对局时刻、等级、实际可用金币、属性购点与专精、技能分支及目标初态。

## 按任务分支查找入口

- **导出快照与配对发行**：见 [现行平衡快照](../../../docs/balance/snapshot.md)。沿生效服务端字段核对介绍卡与导出；登记名录和可计算字段分开计数。
- **对比两版规则的前后差异**：运行快照对比脚本（支持 `balance-snapshot.json` 或包含它的目录，如 `build/balance`、`build/ci-release`）：

```sh
python .agents/skills/myriad-balance/scripts/diff_snapshots.py <基线快照或目录> <候选快照或目录>
```

- **复跑全阵容、3v3/5v5 经济与 L4 抽检**：见 [场景选择与复算入口](references/scenarios.md)；现行全阵容与模式复跑命令见 [全阵容与对局模式验证](../../../docs/balance/roster-modes.md) 和 [樱隙前线 3v3 标杆](../../../docs/balance/calibration-3v3.md)。
- **归档证据或输出单议题评审卡**：见 [证据记录与报告格式](references/evidence.md)（含 `render_review.py`、模板与夜影实例）。
- **术语与长期口径**：见 [领域术语表](../../../GLOSSARY.md)、[预算与证据 ADR](../../../docs/adr/0011-balance-budget-and-evidence.md)；历史批次规划与验收算例见 [实施移交规格](../../../docs/balance/implementation-handoff.md)。

## 交付完成判据

每次平衡调参、复算或移交收口前，逐项核对：

1. **版本可追溯**：列出基线与候选的 `sourceSha`、`rulesSha256`（发行阶段核对 JAR 与快照配对 SHA-256，见 `python tools/fabric/prepare-ci-release.py`）。
2. **体感优先、三层分离**：先概括玩家最直观的玩法变化，再将明细分为「正式实改」「仅核对未变（只报汇总）」「试改未采纳」，不把核对范围混写成改动。
3. **证据与缺口分明**：标明读数属 L1–L4 还是真人实测；真实 `0` 保留，未测或缺少输入标 `null` 并说明受影响指标。
4. **执行与提醒分离**：GameTest 写清实际运行的过滤器名、XML 用例数及真实通过／失败；平衡提醒均为非阻断参考，人工设值照常保存、构建、试玩与合并。
