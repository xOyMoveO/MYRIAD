# 虹列：可编辑模型与素材来源

动态预览另存于两个子目录：

- `animation/`：`fixed.gif`（定轨展开）、`mobile.gif`（游走半展）、`cancel.gif`（暂停后取消）、`shot.gif`（真实出膛回震），以及各段分帧 PNG、联系表和 `pose_trace.csv`。`tools/assets/YikeWeaponPreview.java` 调用生产动画控制器导出姿态，再由 `render_yike_animation.py` 驱动实际源模型渲染，固定画布、缩放与中心；没有另写近似动画曲线。
- `vfx/`：`yike_vfx_preview.gif`、明暗背景总览、命中／收束阶段图及关键帧。`tools/vfx/PreviewYikeVfx.java` 调用生产网格生成方法，使用 `PreviewRoleMeshes.java` 做 CPU 离线投影。

以上预览用于检查实际姿态和网格阶段，不是游戏内截图，不代表第一／第三人称遮挡、多人延迟、场景照明或游戏内观感已经验收。

当前虹列采用未来科技方向：细长便携的光子阵列发射器，白陶瓷装甲、石墨骨架、钴蓝内板与冰蓝光路，紫色/紫红仅用于小型状态灯。正式游戏资产为同源导出的 `models/item/hong_lie*.json`、`textures/item/hong_lie.png`；三翼、主架与镂空准直环独立，运行时沿用服务端认可的展开进度组合。

- 全长约 1.31 格；模型握区中心 `(8,8,8)`，绝缘防滑握把的位置和尺寸保留为 `(7.58,5.3,7.58)` 至 `(8.42,10.7,8.42)`；三翼铰轴仍在握区前方 `5.5/16` 格。
- 携行态三翼收拢；定轨三翼各展开 30°，准直环前移约 0.084 格；游走保留上翼收拢、两侧半展开并有独立轻微导轨位移。现有 Java 姿态、展开参数和碰撞规则均未改动。
- 第一人称使用短前臂与皮肤手掌，实际握区和武器共用变换；第三人称由独立炮口认可方向设置持握手臂，武器正向沿手臂轴。
- `hong_lie*.spec.json` 是现有几何逆向后的编辑规格，`hong_lie*.bbmodel` 内嵌与运行时一致的 256×256 材质贴图，移动到其他目录后仍能打开显示颜色。源模型 UV 使用贴图像素坐标，运行时 JSON 按贴图尺寸转换到 0—16。
- 材质采用白陶瓷 `#DCE8F0`、石墨 `#15222E`、深钴蓝 `#18456B`、冰蓝光路 `#5DDEFF` 与冷白芯 `#F1FCFF`；状态灯少量使用 `#A88AFF` / `#FF6AAE`。移除金边、雕花、菱纹与皮革缠绕，改为机械接缝、锁扣、嵌入灯条和绝缘防滑表面。
- 三翼外壳拆为分层陶瓷边轨和上下连接板，五条独立散热鳍片之间留真实凹槽；钴蓝内板保留独立光路。原露天细晶改为三段矩形光子模块、插座和陶瓷保护侧壳。准直环保持真镂空，并增加工业耦合锁扣与嵌入灯条；三条细轨及其行程保持，展开时环仍沿导轨前移。这些是规格中的实际体块，不是只在贴图上换色。
- 源模型块数为主架55、单翼31、准直环24、物品栏组合172；每块均由规格生成，四模型共用内嵌256×256贴图。
- 武器独立生成入口为 `python tools/assets/generate_yike_weapon.py`，依赖 Pillow 及项目本地 `minecraft-ai-generate-bbmodel` skill 的规格编译器。它读取本目录规格，保留细部的六位小数精度，再生成四个源模型、四个运行时模型与共用贴图；不会修改图标或音频。
- `python tools/assets/render_yike_weapon.py` 使用 `tools/assets/render_bbmodel.py` 对真实六面 UV、立方体、原点及旋转进行软件栅格渲染，按现有运行时拆件变换展示携行闭合、游走半展和定轨展开。它不启动游戏。
- `hong_lie_model_preview.png` 为三种姿态及拆件总览；`hong_lie_closed_preview.png`、`hong_lie_mobile_preview.png`、`hong_lie_deployed_preview.png` 为单态图；`hong_lie_parts_preview.png` 为拆件；`hong_lie_muzzle_preview.png` 为炮口顶视与斜视；`hong_lie_wing_detail_preview.png` 对照陶瓷外壳散热槽与钴蓝内板光路。它们是实际着色源模型预览，不是概念伪图或游戏内截图。
- `hong_lie.bbmodel` / `hong_lie.json` 保留物品栏静态三瓣包围盒表示；持握姿态使用 frame/petal/ring 三个独立模型按 120° 组装。上述姿态预览展示实际持握拆件组合。
- `tools/assets/generate_yike_assets.py` 保留原有17个声音的合成入口，并调用武器独立入口；旧占位图标绘制段已移除，当前图标源位于本目录。运行该旧总入口会重写原有音频，依赖 Pillow、soundfile；原有声音为单声道 Ogg Vorbis，没有外部采样包，本轮没有重制音频。
- 八个技能图标独立保存：`icon_source.png` 为原始图源；`icon_export.py` 记录完整提示词和裁切坐标；`icon_contact_sheet_native.png` 与 `icon_contact_sheet.png` 分别供原尺寸和放大检查。运行时图标目录为 `src/main/resources/assets/myriad/textures/gui/sprites/skill_icons/generated/yike/`，不由旧总生成器覆盖。
- 本轮安装于临时虚拟环境 `/private/tmp/yike-asset-env` 的依赖仅用于素材制作，不是游戏运行依赖。
- `hong_lie_concept.png` 是旧魔导炮杖方向的历史概念图，原位保留，不代表当前未来科技造型；当前外观以真实模型PNG/GIF为准。第一/第三人称握持、遮挡和客户端画面仍待单独验收。

动态拆件预览位于 `animation/`：`fixed.gif`、`mobile.gif`、`cancel.gif`、`shot.gif` 分别展示定轨展开、游走半展、暂停后取消和真实出膛回震；同名子目录各含71张 420×700 PNG，`*_contact.png` 供关键帧检查。全部使用同一相机、比例和画面中心，回震不会被逐帧重新居中抵消。

轨迹由 `tools/assets/YikeWeaponPreview.java` 调用生产 `YikeWeaponAnimation.Controller` 导出到 `animation/pose_trace.csv`，`python tools/assets/render_yike_animation.py` 用真实模型与贴图渲染为每帧20毫秒的GIF。此次按原轨迹重新渲染全部284帧及四段GIF，背景改为冷石墨色。生产动画以服务端已确认的起手进度分解机械解锁、三翼分开、准直环前移，再进入发射核心充能阶段；客户端只在相邻已确认姿态间插值，不按本地时钟猜测后续起手。移动分支在起手中也保持上翼收拢，正常退出/取消用4 tick 收拢，世界/角色/生命或武器身份失效立即清除。回震只接受真实出膛视觉事件，Q准备或取消不触发。

这些GIF是生产数学姿态驱动的离线模型预览，不包含第一/第三人称人物、地形或技能粒子，也不代表游戏内遮挡、握持和多人显示已验收。静态发射核心没有虚构位移，核心通电由技能表现的相同权威进度阶段驱动。

历史魔导炮杖概念图的完整提示词（不适用于当前造型）：

> Use case: stylized-concept. Create an exact production concept sheet for an ORIGINAL fantasy game weapon 'Prismatic Array' for MYRIAD. Neutral charcoal studio backdrop. Show the SAME slender 1.25-meter deployable arcane artillery STAFF twice in large three-quarter views: LEFT folded carrying configuration 0.20m wide, RIGHT deployed configuration three petals open to 0.65m wide. Add matching small side, top and grip orthographic views at bottom. Keep geometry perfectly consistent across states. Lower half long dark-violet cylindrical handle with a clearly exposed one-hand grip, pale-gold collars and counterweight. Upper elongated head: THREE physically connected hinged coral-red outer petals with violet inner panels, open focusing framework ALONG THE LONG AXIS of the staff, thin warm-white crystal core, hollow pale-gold mechanical focusing rings and rails aligned with long axis; front ring slides forward when deployed. Fine brass bevels, ivory inlays, controlled crisp pixel-painted surface detail suitable for Minecraft cuboid modeling. Readable connector hinges, no floating unconnected parts. No rifle stock, no trigger, no magazine, no scope, no wooden staff, no fat block cannon, no huge glow. Complete uncropped weapon and all views. No character, no hand, no words, no UI, no watermark. Very restrained internal emission so actual shape is fully readable.
