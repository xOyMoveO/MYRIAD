# 训练面板即时关闭修复及玻璃布局

日期2026-11-17；Fabric26.3，工程`E:/mod/MYRIAD-main-20261117`，分支`codex/training-glass-openfix-20261117`，HEAD仍`230417f07ad67055d92d4c4854d8ca50216bb702`。继承上一轮未提交训练/C面板/HUD改动，不修改另一NeoForge工作树，不提交或上传。

## 根因和处理

- 缓存的MC26.3字节码显示：ChatScreen.keyPressed在handleChatInput提交命令后，对普通closeOnSubmit路径无条件调用Gui.setScreen(null)。原ALLOW_COMMAND监听器同步setScreenAndShow，随后被原版关闭。setScreenAndShow不是延后执行；单纯client.execute也不能保证延后。
- 新增单次DeferredScreenRequest邮箱，ALLOW_COMMAND只提交lambda，END_CLIENT_TICK在聊天关闭后才构造并打开面板；仍在聊天框时等待，断线/重连、世界变化或其他界面出现则清除。全部原GUI别名统一处理，服务端命令及wind_test保留原行为。
- 另一条关闭路径是ClientDataHolder在modEnabled=false时关闭所有本项目界面。只对TrainScreen保留只读诊断界面：显示开启玩法提示并禁用训练操作；没有修改其他界面的恢复规则或服务端权限。

## 界面

- 11行独立组件左侧显示名称，右侧为状态开关或操作按钮；等级行提供减号、数字、加号、确认，所有既有训练动作保留。两种阵营切换各占一行。
- 圆角、半透明深蓝玻璃、蓝白反光边、柔和阴影和滑块式开关；这是代码绘制的玻璃风格，不声称实现真实场景折射或动态模糊。无新增贴图/材质文件。开关显示始终读服务器同步值，不伪造本地成功。
- 最大440×480GUI单位，窗口较小时缩小并可滚动，标题/关闭固定、视口外按钮隐藏且不接鼠标。C面板原背景和上一轮HUD改动保持。

## 已执行的客观验证

- build+training_ui：1/1通过，1分1秒。新增模拟原版聊天关闭顺序、等待聊天、只打开一次、重连/换世界/其他界面/主动清理取消请求的回归；6尺寸下全部11行可达、名称与右侧控件不重叠。延用96技能面板状态、1536浮窗状态、24HUD状态以及实际服务端权限/训练/等级预算/CD等断言。
- 独立JVM combat_regression：1/1通过，29秒。git diff --check、命名/包身份及版本、ZIP CRC、测试夹具排除、810生产编译类逐字节一致、1273源指纹通过。
- 相对上一已安装包，仅客户端命令入口/数据关闭规则/训练布局与两语言训练文本改变；新增3个class。服务器玩法类、C背景、模型、贴图与音效逐字节保持。
- QA：`E:/mod/outputs/training-glass-openfix-20261117`，含日志、XML、审计、指纹、同包及部署记录。
- 未启动图形客户端或操作真实聊天框，不把队列/几何/服务端测试当成物理输入或视觉验收。新世界人工待验：输入命令后持续打开、模组未启用提示、各开关的服务器回包、缩放和滚动、玻璃观感、ESC返回。

## 安装

- 包26528763字节；SHA256 `3E42D0CE3A60B50CED344BFA94E88EC268035BF1520629421A0E50697211B2D3`。
- 19:42:31确认目标未运行、路径无链接、旧包身份/哈希/独占读取、依赖和源指纹后，原子安装至`D:/minecraftmodrinth/profiles/万象MYRIAD/mods/myriad-0.1.0.jar`；安装哈希一致。
- 旧5AF28D1A…包备份`E:/mod/outputs/training-glass-openfix-20261117/backup-20261117-194231/myriad-0.1.0.jar`，可恢复。其他模组哈希不变，未修改配置/地图/存档，不启停用户游戏。`liveClientAccepted=false`。
