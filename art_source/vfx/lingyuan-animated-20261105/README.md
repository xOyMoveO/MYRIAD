# 凌渊：多帧像素画叠层特效 / 2026-11-05

沿用玩家确认的青蓝、紫色流光与金色符纹主题。素材由内置图像生成工具制作，不是第三方素材，也不是将一帧图机械旋转/移动后冒充逐帧动画。角色骨骼动作仍暂停。

## 游戏素材与播放

- 六张RGBA图集，均为4列×2行，左至右、上至下共8帧；单格256×256，128×128原生像素细节最近邻放大；中心支点(0.5,0.5)。共48张不同的绘制帧。
- slash月牙：100ms/帧，左凸右开；stream流光：75ms/帧，头右尾左；seal符阵：150ms/帧；filament细丝：125ms/帧；motes碎光：175ms/帧。五类各自循环，不同交叉层错开相位。
- impact命中：一次性点亮→爆开→碎散→消失，8帧映射该次特效实际寿命，不按世界时间循环。图集预览使用75ms/帧，只用于观察绘制阶段，不改变游戏技能时长。
- 游戏资源：`src/main/resources/assets/myriad/textures/particle/vfx/lingyuan_anim_*.png`；材质44..47改指图集，新增48细丝、49碎光，原0..43不动。客户端按每个实例选取UV格子，不使用所有实例共享的mcmeta自动动画；无需新增网络字段，协议仍7。
- 剑体保留186块精细模型及48块完整LOD；手持武器不改。多帧不会同时绘制8倍面片，原近/远预算及总帧预算保留。

## 源文件与规范化

- `raw/*_selected.png`为选用的生成源图；旧v1为迭代参考，不在游戏中加载。生成图为1774×887，通过整张最近邻校正为1776×888，再按已知4×2网格等分。
- `tools/vfx/prepare_lingyuan_flipbooks.py`只做确定性图集规范化与打包：统一移除alpha<8的近乎不可见边缘杂点，保留alpha8..254的半透明；每套8帧共用同一个居中缩放，不独立裁切或对齐每帧。原始生成图不覆盖。最终每格至少12px全透明边缘。
- `accepted/frames/`为单帧；`accepted/frames.json`记录行列、支点、时长、每帧范围与SHA256；`accepted/*.gif`为素材播放预览，`accepted/contact-sheet.png`为深色、白色、草地色和紫色四底检查板。
- 游戏实际几何离线预览在`E:/mod/outputs/lingyuan-flipbooks-20261105`。动态图使用服务端测试实际导出的Quad和UV、固定镜头和固定比例；不是Minecraft客户端录像。飞剑栏目将位置固定以检查连续短包的纹理相位，命中栏目播放后留出8tick空白再重播。
- 构建和专项：`build/lingyuan-flipbooks-final-20261105.log`；6图集48帧、透明边缘、循环/一次性UV、跨包相位、月牙朝向、既有角色/motion/持续伤害检查通过。未自动安装，未进行客户端/多人/FPS验收。

## 接受评估

六套均通过8帧计数、无重复绘制帧、RGBA、1024×512、4×2可整除、透明边缘与不跨格检查。视觉上月牙亮刃支点固定，内部卷动；流光亮结错开；符阵不改变主体轮廓但亮纹逐段推进；细丝亮结绕行；碎光依次闪烁；命中体积有明确增长和解体。白雾版、跨格版未接入。四底检查没有大面积白色底雾。

按可读性30、主题/像素画风25、时间连续性25、透明清洁度20评估，六套均达到4/5或以上（约80/100）；命中最终阶段的稀疏和面积变化是有意消散，不将其误判为缺帧。实际场景中遮挡、亮度、距离与帧率仍需客户端验证。

## 完整生成提示词组成（内置工具，transparent_background=true）

### 最终月牙/流光：公共提示 + 对应段落

公共提示：
> Use case: stylized-concept. Production sprite sheet for a pixel-painted eastern fantasy Minecraft skill. Transparent PNG, FOUR columns by TWO rows, exactly eight equal square cells, no labels, no checkerboard or painted backdrop. Every complete effect INCLUDING every glow and stray pixel must fit inside the central 60% of its cell with a completely empty alpha-zero gutter. Effects must be SMALL isolated motifs on this very wide sheet. SAME emitter, scale, camera and orientation in all eight frames. Crisp colored pixel clusters, cyan / cobalt / violet and tiny gold glints. No fog, mist, diffuse white wash, broad glow clouds, background, or squared 3D blocks. Eight successive phases of actual internal flowing texture deformation, not rotated copies. Loop frame 8 approaches frame 1.

月牙：
> Animate a crescent energy blade. All eight frames: rounded CONVEX FRONT LEFT, opening and both points RIGHT. Delicate thin white cutting rim, turquoise curls sweeping upward round the rim, small violet filaments peel backward. Not a full disc or circle. Preserve the same anchored outer cutting edge, change internal curls and wisps across eight successive frames.

流光：
> Animate an interwoven horizontal sword energy trail. Broad bright head always RIGHT, taper LEFT. Thin blue and violet ribbons split and reconnect, ivory energy knots travel right-to-left in successive phases. Same stable silhouette and head, varying interior waveform. No sword model, no crescent.

### 其余四套初次生成：公共提示 + 对应段落

公共提示：
> Create ONE game-ready transparent PNG sprite sheet: strict 4 columns x 2 rows, exactly 8 animation frames in row-major order, wide 2048x1024 canvas, equal square cells, no lines or labels. Each frame fully inside the central 70% of its cell with wide alpha-zero padding. Fixed centre, scale, orientation and camera across the 8 frames. This is a coherent ANIMATION of one effect, not 8 unrelated designs or copies of a still. Frame contents and internal silhouette must visibly evolve. Same exquisite 2D pixel-painted eastern-fantasy magic as the appearance reference: crisp clustered pixels, rich cyan/cobalt/violet/ivory palette, tiny warm-gold accents where appropriate. Roughly 128x128 native pixel detail per cell, nearest-neighbour enlarged appearance. No voxel cubes, no 3D scene, no fake checkerboard, no text, no matte colour, no floor, no blur. Genuine transparent background and transparent gaps inside the effect. Include complete effect tips and glow; nothing crossing cells. Reference image supplies ONLY appearance, not frame order.

符阵（参考旧lingyuan_pixel_seal，仅作风格参考，不作动作参考）：
> Animate an intricate golden eastern magic sigil. Front-on perfect round flat ring with a large truly transparent centre. Keep the ring outline and 4 cardinal jade ornaments at identical positions and scale in all frames. Animate energy travelling along angular runic strokes and cloud-scroll segments clockwise in 8 stages, different groups of strokes brightening and fading in sequence, little turquoise energy curls advancing along the inner rim. This is an active flowing rune animation, not just identical rotated pictures. Frame8 returns toward frame1. Rich gold/amber/ivory pixel texture, subtle cyan/violet energy, no stone 3D model.

以下三套在公共提示后增加：
> Mandatory padding: keep EVERY complete motif and all wisps/glow inside the central 62% of each cell; each cell must have a wide truly empty transparent gutter on all four sides.

命中（旧impact为风格参考）：
> One-shot 8-frame magical sword impact, with fixed central emitter and fixed maximum scale. Frame1 small compact ivory cross-spark; 2 cyan core ignites; 3 full white/cyan contact flash with small gold star rays; 4 expanding blue/violet jagged energy petals; 5 petals separate into curved pixel shards; 6 scattered fading cyan/gold streaks; 7 sparse violet wisps and sparks; 8 nearly disappeared few tiny motes. These must be successive stages with CLEAR changes in silhouette and density, NOT the same explosion 8 times. Keep empty padding even at largest frame. The reference shows only peak appearance, do not copy its peak to every frame.

细丝（旧stream为风格参考）：
> An 8-frame seamless loop of delicate orbital energy filaments for layering around a sword or a magic seal. A thin open interwoven circle of turquoise and violet silk-like wisps with tiny ivory knot highlights; transparent open centre takes at least 60% of motif diameter. Not a golden sigil, not a crescent slash, not a thick comet, no solid central glow. Two or three thin asymmetric ribbon curls travel around the rim through the eight phases, knots migrate, wisps split and rejoin; keep stable outer footprint and centre, frame8 approaching frame1. Different fine shape from the reference, but same blue/cyan/violet pixel-painting theme. Intentionally light airy supporting layer with clear pixels, not blurry fog.

碎光（旧impact为风格参考）：
> An 8-frame seamless supporting sparkle drift loop, NOT a big explosion. A sparse constellation of 6-10 small gold/ivory four-point glints, little cyan diamonds and tiny violet streaks arranged around a truly transparent empty centre. Each cell occupies the same stable central footprint with lots of transparent negative space. Across successive frames the small glints drift a short distance clockwise, brighten then shrink, different glints take turns twinkling. Thin elegant pixel clusters, no squared 3D chunks, no large central core, no ring, no crescent. Frame8 flows into frame1. Keep all tiny sparks safely within the central 62% of each cell.

细丝/命中/碎光的最终透明修订（以上原图作为edit target）：
> Edit this existing 8-frame pixel-art sprite sheet. Preserve exactly 4 columns x 2 rows, frame order, all 8 distinct animation stages, palette, orientation, and each frame's internal shape. ONLY repair boundaries and transparency: shrink EVERY complete frame motif including all glow and wisps to 60% of its cell width/height, keep its emitter centred at the SAME cell-relative point. Wide alpha-zero transparent gutters on every side of every cell; no content may touch or cross a cell boundary. No scene, no labels, no frames, no checkerboard. Keep detailed pixel clusters, no blur and no voxel blocks.

命中与碎光在修订后另附：
> CRITICAL: completely REMOVE the large white/grey cloudy backdrop and diffuse white haze between/behind the coloured strokes. Keep only the small sharp coloured cyan, blue, violet and gold pixel shapes and tiny bright centres of individual glints. The space between every ray/spark and the centre hole must be truly alpha-zero, not white/grey translucent fog. Do not erase the tiny white heart of each actual spark. Preserve the exact ignition-to-dispersal sequence.
