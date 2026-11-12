# Issue tracker: GitHub

本仓库的需求、规格和实施任务记录在 GitHub Issues，使用 `gh` CLI 操作。

## 目标仓库

- 目标为 `xOyMoveO/MYRIAD`，仓库地址：https://github.com/xOyMoveO/MYRIAD。
- 在仓库根目录运行命令；首次写入前，用 `git remote -v` 和 `gh repo view --json nameWithOwner` 核对目标。
- 写操作显式使用 `--repo xOyMoveO/MYRIAD`。目标不一致时先核实；任务只写入用户指定仓库。

## Conventions

- **创建任务**：先将正文写入 UTF-8 临时文件，再运行 `gh issue create --repo xOyMoveO/MYRIAD --title "标题" --body-file <正文文件>`。多行正文、评论和更新正文都使用 `--body-file`。
- **读取任务**：`gh issue view <编号> --repo xOyMoveO/MYRIAD --json number,title,body,labels,comments`。
- **列出任务**：`gh issue list --repo xOyMoveO/MYRIAD --state open --json number,title,body,labels,assignees`，按需增加标签、状态和数量限制；完整扫描须处理分页或截断。
- **更新正文**：`gh issue edit <编号> --repo xOyMoveO/MYRIAD --body-file <正文文件>`。
- **评论**：`gh issue comment <编号> --repo xOyMoveO/MYRIAD --body-file <评论文件>`。
- **标签**：`gh issue edit <编号> --repo xOyMoveO/MYRIAD --add-label "<标签>"` 或 `--remove-label "<标签>"`；分流标签见 `triage-labels.md`。
- **关闭**：先单独发表说明评论，再执行 `gh issue close <编号> --repo xOyMoveO/MYRIAD`。任务已经关闭时，也要单独提交说明评论。

## 父子任务与阻塞关系

- **子任务**：创建时使用 `gh issue create --repo xOyMoveO/MYRIAD --parent <父任务编号> ...`，或创建后运行 `gh issue edit <父任务编号> --repo xOyMoveO/MYRIAD --add-sub-issue <子任务编号>`。
- **阻塞关系**：优先使用 GitHub 原生依赖；运行 `gh issue edit <被阻塞任务编号> --repo xOyMoveO/MYRIAD --add-blocked-by <阻塞任务编号>`。
- 旧版 CLI 缺少这些参数时，可使用 GitHub API：子任务为 `POST repos/xOyMoveO/MYRIAD/issues/<父任务编号>/sub_issues`，参数为 `sub_issue_id=<子任务数据库ID>`；依赖为 `POST repos/xOyMoveO/MYRIAD/issues/<被阻塞任务编号>/dependencies/blocked_by`，参数为 `issue_id=<阻塞任务数据库ID>`。使用 `gh api --method POST ... -F ...` 提交。
- 数据库 ID 通过 `gh api repos/xOyMoveO/MYRIAD/issues/<编号> --jq .id` 获取，与任务编号及 `node_id` 不同。
- 平台不支持父子关系时，在子任务正文顶部写 `Part of #<父任务编号>`，并在父任务正文维护任务列表；平台不支持依赖时，在被阻塞任务顶部写 `Blocked by: #<编号>, #<编号>`。
- 所有阻塞任务关闭后才可开始。原生关系的未关闭阻塞数可用 `gh api repos/xOyMoveO/MYRIAD/issues/<编号> --jq .issue_dependencies_summary.blocked_by` 查询；缺少该字段时检查实际依赖状态。

## Pull requests as a triage surface

**PRs as a request surface: no.**

## 技能用语

- “publish to the issue tracker”：创建 GitHub Issue。
- “fetch the relevant ticket”：读取对应 Issue 及评论。
- GitHub 的 Issue 与 PR 共用编号；编号类型不明确时，先用 `gh pr view` 判断是否为 PR，再读取对应对象。

## Wayfinding operations

供 `wayfinder` 使用：

- **Map**：一个标记为 `wayfinder:map` 的 Issue，正文维护 Notes、Decisions-so-far 和 Fog。
- **Child ticket**：Map 的子任务；关系及降级方案见“父子任务与阻塞关系”。按类型使用 `wayfinder:research`、`wayfinder:prototype`、`wayfinder:grilling` 或 `wayfinder:task`，不混用分流标签；需要时创建缺少的标签。
- **Blocking**：使用上面的阻塞关系规则。
- **Frontier**：按 Map 顺序检查所有未关闭子任务，选取没有未关闭阻塞任务且无人认领的第一个。
- **Claim**：开始工作前，先运行 `gh issue edit <编号> --repo xOyMoveO/MYRIAD --add-assignee @me`。
- **Resolve**：发表评论记录结果，关闭任务，再将结果摘要和链接追加到 Map 的 Decisions-so-far。
