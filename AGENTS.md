# 万象（MYRIAD）项目协作约定

- 对外中文名“万象”，英文名 `MYRIAD`；新角色题材不限于修仙。内部标识统一为 `myriad`（模组 ID、命名空间、命令 `/myriad`、NBT 及测试前缀），Java 包 `com.myriad`，入口 `Myriad` / `MyriadClient`。详情、角色 ID 对照与开发边界见 `docs/协作开发与内部命名.md`。
- 不自动迁移旧版（含原《仙凡劫》/ NeoForge）存档、玩家进度、地图设施或个人 HUD，也不可让新旧模组共存于同一实例；验证请用新测试世界，旧世界迁移须先备份并单独确认。

## 分支、PR 与文档同步

- 每项更改开始前先新建并切换到独立工作分支，不在 `main` 上直接修改或提交。
- 远端 `main` 的所有更改通过 GitHub Pull Request（PR）合并，不直接推送 `main`，也不在本地合并后直推绕过 PR。
- 创建或更新 PR 前，按当前分支的实际代码、配置和可用入口核对根目录 `README.md`；不一致则在同一 PR 中同步，已一致则不改动。根 `README.md` 面向公开读者，不写个人环境、本机路径或临时 PR 状态。

## 导航指针

- **Issue tracker**：需求、规格和实施任务记录在 `xOyMoveO/MYRIAD` 的 GitHub Issues；读取、发布或更新任务前，先读 `docs/agents/issue-tracker.md`。
- **Triage labels**：使用五个默认分流标签；应用或变更分流状态前，先读 `docs/agents/triage-labels.md`。
- **Domain docs**：采用 single-context 布局（根目录 `GLOSSARY.md` 与 `docs/adr/`）；探索代码、讨论领域术语或提出设计决策前，先读 `docs/agents/domain.md`。
- **数值平衡与快照**：涉及角色数值、购点与专精、经济或战场节奏变更时，使用 `myriad-balance` 技能，入口见 `docs/balance/snapshot.md` 与 `docs/balance/roster-modes.md`。
