# 角色信息面板与对局聊天实机验证

使用 Minecraft 26.3、Fabric Loader 0.20.1、Fabric API 0.166.2+26.3 和 Java 25。独立专服从仓库的合成场地生成，两个真实客户端使用独立目录和进程。客户端通过原生按键、鼠标、文本输入回调操作。学习、购买、阶段和物品以服务端同步状态或物品栏回读。复用并核对了共享资源索引中的 5,149 项资产。

- 改前为 `be66958`，主包 SHA256 为 `7d7509ba06acfa7b448e70fcde02b4acaafa7e2bbed5755013e442ba6b7e57f3`。
- 改后对照主包 SHA256 为 `14a18067461593088c5c9c2d3c4df98e9867fd6653f1260beefe46ecb664f219`。
- 高位报价补充场景使用阶段构建 `9a3e55e37fa9bd144c9f907054ec9216f84cec25b8e81f988472ba9639644494`。

## C 键角色信息面板

桌面窗口的 854×480 和 1920×1080 帧缓冲分别对应 427×240 和 960×540 逻辑像素。紧凑组在 1280×720 帧缓冲上通过测试适配器应用 4 倍 GUI 缩放，得到 320×180 逻辑像素。另在真实窗口中验证从紧凑组切回 427×240 时的界面状态。对照使用训练场赤霄，说明状态选取「断岳斩」。

| GUI 逻辑像素 | 外框宽度改前 → 改后 | 技能页 | 属性页 | 技能说明 |
| --- | ---: | --- | --- | --- |
| 427×240 | 267 → 367 | [前](assets/role-panel-chat/before/panel-427x240-skills.webp) · [后](assets/role-panel-chat/after/panel-427x240-skills.webp) | [前](assets/role-panel-chat/before/panel-427x240-attributes.webp) · [后](assets/role-panel-chat/after/panel-427x240-attributes.webp) | [前](assets/role-panel-chat/before/panel-427x240-tooltip.webp) · [后](assets/role-panel-chat/after/panel-427x240-tooltip.webp) |
| 320×180 | 198 → 296 | [前](assets/role-panel-chat/before/panel-320x180-skills.webp) · [后](assets/role-panel-chat/after/panel-320x180-skills.webp) | [前](assets/role-panel-chat/before/panel-320x180-attributes.webp) · [后](assets/role-panel-chat/after/panel-320x180-attributes.webp) | [前](assets/role-panel-chat/before/panel-320x180-tooltip.webp) · [后](assets/role-panel-chat/after/panel-320x180-tooltip.webp) |
| 960×540 | 480 → 640 | [前](assets/role-panel-chat/before/panel-960x540-skills.webp) · [后](assets/role-panel-chat/after/panel-960x540-skills.webp) | [前](assets/role-panel-chat/before/panel-960x540-attributes.webp) · [后](assets/role-panel-chat/after/panel-960x540-attributes.webp) | [前](assets/role-panel-chat/before/panel-960x540-tooltip.webp) · [后，192 px 详情栏](assets/role-panel-chat/after/panel-960x540-tooltip.webp) |

### 原生操作结果

- C 打开与关闭，ESC 和右上关闭按钮独立生效。页面打开期间，独立服务器的世界 tick 继续增加。以短间隔选择「断岳斩」并点击确认，一级学习成功，可用技能点从 1 降到 0。宽屏同址双击后，另一端的被动 B 升到一级。快速选择 A 再选 B 只改变选中项，确认学习 A 后再提交 B，服务器只同步 A 的等级。
- 用 Tab 聚焦属性页，Enter 切页。以合成训练钱包 500 金起步，点击力量图标后确认支付 360 金，再在补给页购买价值 60 金的回血丹。余额按 500 → 140 → 80 变化，物品栏出现 `myriad:blood_pill`。[补给页](assets/role-panel-chat/after/panel-supplies-before-buy.webp)
- 紧凑组的长「断岳斩」说明可滚到后续段落。属性页滚轮使滚动量到 120 px，拖动滚动条回到 0 px，放大实际窗口后仍停在属性页且外框宽度重算为 367 px。[滚动后的长说明](assets/role-panel-chat/after/panel-320x180-tooltip-scrolled.webp)
- 铆钉样例覆盖中文长标题和英语客户端说明。阶段构建在实际购入 55 点力量后显示下一档 8,140,760 金，钱包余额 1,959,298,351 金。[中文长标题](assets/role-panel-chat/after/panel-427x240-long-chinese-title.webp) · [英文说明](assets/role-panel-chat/after/panel-427x240-english-tool-tip.webp) · [高位报价](assets/role-panel-chat/after/panel-960x540-high-quote.webp)

## 对局聊天

| 游戏帧缓冲 | 未打开聊天 | 打开聊天 |
| --- | --- | --- |
| 1920×1080 | [前](assets/role-panel-chat/before/chat-1080p-closed.webp) · [后](assets/role-panel-chat/after/chat-1080p-closed.webp) | [前](assets/role-panel-chat/before/chat-1080p-open.webp) · [后](assets/role-panel-chat/after/chat-1080p-open.webp) |
| 854×480 | [前](assets/role-panel-chat/before/chat-854x480-closed.webp) · [后](assets/role-panel-chat/after/chat-854x480-closed.webp) | [前](assets/role-panel-chat/before/chat-854x480-open.webp) · [后](assets/role-panel-chat/after/chat-854x480-open.webp) |
| 320×180 GUI | [前](assets/role-panel-chat/before/chat-compact-closed.webp) · [后](assets/role-panel-chat/after/chat-compact-closed.webp) | [前](assets/role-panel-chat/before/chat-compact-open.webp) · [后](assets/role-panel-chat/after/chat-compact-open.webp) |

### 双客户端结果

- 两端分别从原版输入框发送长中文和长英文，均经专服广播并读到完整正文。每条消息的首行记录一份时间，续行保留正文缩进。相同短消息 `UTC-095` 在上海端显示 `16:56:57`，在洛杉矶端显示 `00:56:57`。两端悬停均标「接收时间」，分别显示 `+08:00`、`-08:00` 与完整日期。关闭再重开仍使用首次接收的元数据。[上海端](assets/role-panel-chat/after/chat-timezone-pilot.webp) · [洛杉矶端](assets/role-panel-chat/after/chat-timezone-observer.webp)
- 常规窗口的输入框与战斗 HUD 左边缘对齐，面板和输入框相隔 6 px，历史和输入框相隔 4 px。聊天开关前后的战斗 HUD 绘制边界相同。320×180 的训练场空间使输入框放在战斗 HUD 右侧，原小地图在这一设置下与聊天共用部分区域。输入、血蓝、冷却、快捷栏和准星各自保有空间。
- 紧凑窗口有 5 行聚焦历史，滚轮上移 5 行，Page Down 后偏移为 1。切回 427×240 后使用 Page Up，偏移变为 4，原消息接收时间保持。原版滚动条用于提示当前历史位置。[紧凑窗口滚动](assets/role-panel-chat/after/chat-compact-scroll.webp)
- T、斜杠、Enter、Esc、方向键历史和鼠标划选已实点。Tab 补全 `/myriad char`，原版框提交 `/say` 后另一端收到命令正文。输入时按 Q 和点击输入框内，已学 Q 的冷却保持 0，法力未扣。专服发出的带原版组件的链接保留悬停内容，左键进入原版链接确认页后退出确认。[命令建议](assets/role-panel-chat/after/chat-1080p-command-suggestions.webp) · [链接确认页](assets/role-panel-chat/after/chat-854x480-link-prompt.webp)
- 427×240 窗口内从默认战斗 HUD 的 `(6, 92)` 拖到 `(56, 60)` 后聊天同时重新放置。继续拖到 `(56, 6)` 时输入框在左侧兜底，HUD 保持用户拖到的位置。隐藏战斗 HUD 时回到原版输入框，重置后恢复原坐标。原版聊天宽度设为 30% 时，输入框从 261 缩到 137 逻辑像素，切到「仅系统消息」后屏蔽玩家消息，恢复「显示所有」后能再收玩家消息。[顶部锚点兜底](assets/role-panel-chat/after/chat-854x480-top-hud.webp) · [HUD 隐藏](assets/role-panel-chat/after/chat-854x480-hidden-hud.webp)

- 320×180 使用 `HH:mm` 短时间，悬停仍能读日期和秒。通过原版输入层粘贴中英文文本，鼠标选中其一部分，使用系统复制与粘贴组合键将该部分追加到行末。启用原版聊天延迟后出现 1 条待显示消息，随后正常送达。调用原版清空接口后，消息列表和渲染行清零，下一条实发消息只出现一次并带新的接收时间。[紧凑时间](assets/role-panel-chat/after/chat-compact-short-timestamp.webp) · [待显示提示](assets/role-panel-chat/after/chat-854x480-queue.webp)
- 两名玩家离开训练后，大厅聊天恢复原版位置。经真实队列提议、选角和准备进入合成 1v1，双方收到对局消息。天刑阵亡进入可观战的复活等待，聊天回到原版位置。复活后同一客户端退服重连，角色席位恢复且新消息只出现一次。管理员关闭玩法后，两端回到原版位置。[正式对局](assets/role-panel-chat/after/chat-match-playing.webp) · [阵亡后观战](assets/role-panel-chat/after/chat-match-respawn-spectator.webp)

## 验收边界

截图来自真实客户端帧缓冲，以无损 WebP 保存副本。原始 PNG、测试脚本、服务端日志、桥接记录和各阶段 SHA256 在本机 `build/role-panel-chat-live/` 下。界面视觉可读性与美术判断待人工确认。

此环境使用本机离线测试身份，覆盖无签名玩家消息及接收时间。官方认证的发送时间、签名撤回、账号级静音或家长权限需要已登录账号。中文已走原版字符输入和实际发送，宿主输入法候选窗仍需人工检查。
