# 主分支握锤优化与本地铆钉玩法合并

日期：2026-11-15。仅在独立本地分支 `codex/merge-main-rivet-grip-20261115` 操作，未推送远端、未修改本地或远端 main。

## 合并与保留范围

- 已 fetch `origin/main` 至 `353e70205526d839aa0c0fec346dbc43120c3e15`（PR #34）；其握锤动作、手臂模型、握持矩阵、客户端显示与握持检查均与该提交逐字节相同。
- 先将原有13个未提交文件完整备份到 `E:/mod/outputs/rivet-main-merge-20261115/local-checkpoint`，并在工作分支保存为 `fadd88c`；二进制补丁另存同目录 `local-before-merge.patch`。
- 合并提交 `f554b879b3c2bcbb39083ea72f748c52d6ac6ca4` 的两个父提交为 `fadd88c` 与 `353e702`。只有 CHANGELOG 同一插入位置产生冲突，两个条目均保留。`RivetVfxChecks` 自动合并，同时保留1001组透明度检查、远端2364组握持检查及其两tick入势后的进度断言；未关闭或放宽测试。
- 本地运行时、持续推行、完整牵引、光效提亮、说明、87场景及预览工具11个其余文件与合并前备份逐字节一致。R2仍10格持续推首敌，E2仍15格释放、拉至墙前；W仍8格。

## 本次验证

- 使用本机JDK25与既有离线依赖缓存，执行发行 `build`、`runRivetTestServer`、`runFirstGroupTestServer`、`runHeroActionTimeTestServer`；最终退出0、1分55秒，三项GameTest各1/1通过。新隔离测试世界，不加载用户存档。
- 铆钉87个真实输入场景、2名正式选角准备玩家、2364组握持矩阵、1024+84个生产特效阶段及透明度检查通过。
- 首轮铆钉场景已打印PASS，但因本次QA报告父目录未创建，在保存XML时报错退出1；补建目录，未修改产品代码或断言，换新隔离世界完整重跑通过。首轮 `build-tests.log` 与最终 `final-tests.log` 均保留。
- 命名空间、14角色/112候选、1806源/工具/资产文件、1535非目录JAR条目、294源及294包内资源引用审计通过；ZIP CRC通过。
- 构建前后1193个运行源码/资源SHA256一致；902个编译类与JAR逐字节一致。相对此前已安装的本地玩法版，仅4个class条目改变、3个class新增，0删除；全部资产、模型、贴图、音频及技能说明未变。
- 完整证据位于 `E:/mod/outputs/rivet-main-merge-20261115`，含三份XML、构建日志、审计、源码指纹、包差异和安装记录。

## 安装与验收边界

- 16:30:24（上海）确认指定实例未运行、路径非链接、旧包身份与哈希正确且可独占访问后，备份并原子替换 `D:/Minecraft/.minecraft/versions/xianfanjie/mods/myriad-0.1.0.jar`。
- 新包27083245字节，SHA256 `BC682C4DCE886F08C0B8BD02C5A588CFA8C87CCDD795B140D68B5BF12EC99964`，安装包、构建包和QA交付副本一致。
- 旧包备份 `E:/mod/outputs/rivet-main-merge-20261115/backup-20261115-163024/myriad-0.1.0.jar`，SHA256 `827AEDA3078BA39E1BF07E11146BCD49F907AF09E2F13AEA488A6D97EF73BECB`，可恢复。
- 其他两模组SHA256不变，没有重复本体。未修改地图、配置或玩家存档，未启动或关闭图形客户端。仍先使用新测试世界；实际视角、遮挡、手感、声音、帧率与平衡未验收。
- 远端提供的握持离线投影与历史验收记录见 [rivet-grip.md](rivet-grip.md)，不将其历史构建数据当成本次结果。
