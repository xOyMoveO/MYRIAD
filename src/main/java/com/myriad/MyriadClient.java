package com.myriad;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

import com.myriad.character.CharacterType;
import com.myriad.client.HudOverlay;
import com.myriad.client.StatusOverlay;
import com.myriad.client.particle.SulanBladeArcParticle;
import com.myriad.client.particle.SpiritMoteParticle;
import com.myriad.client.particle.RainbowMoteParticle;
import com.myriad.client.particle.Wind1Particle;
import com.myriad.client.gui.InfoScreen;
import com.myriad.network.NetworkSetup;
import com.myriad.registry.ModParticles;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Myriad.MODID, dist = Dist.CLIENT)
public class MyriadClient {
    /** 仙凡劫专属按键分类（设置-按键控制中独立一栏） */
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category
            .register(Identifier.fromNamespaceAndPath(Myriad.MODID, "keys"));

    public static final KeyMapping KEY_SKILL_Q =
            new KeyMapping("key.myriad.skill_q", GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
    public static final KeyMapping KEY_SKILL_W =
            new KeyMapping("key.myriad.skill_w", GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
    public static final KeyMapping KEY_SKILL_E =
            new KeyMapping("key.myriad.skill_e", GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
    public static final KeyMapping KEY_SKILL_R =
            new KeyMapping("key.myriad.skill_r", GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
    public static final KeyMapping KEY_SKILL_F =
            new KeyMapping("key.myriad.skill_f", GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
    /** 个人信息界面 */
    public static final KeyMapping KEY_INFO =
            new KeyMapping("key.myriad.info", GLFW.GLFW_KEY_C, KEY_CATEGORY);
    public static final KeyMapping KEY_RECALL =
            new KeyMapping("key.myriad.recall", GLFW.GLFW_KEY_B, KEY_CATEGORY);
    /** 装备商店界面 */
    public static final KeyMapping KEY_SHOP =
            new KeyMapping("key.myriad.shop", GLFW.GLFW_KEY_P, KEY_CATEGORY);

    public MyriadClient(IEventBus modEventBus) {
        // 注册游戏内 HUD
        modEventBus.addListener(MyriadClient::onRegisterGuiLayers);
        // 注册自定义键位
        modEventBus.addListener(MyriadClient::onRegisterKeyMappings);
        modEventBus.addListener(MyriadClient::onRegisterParticleProviders);
        modEventBus.addListener(com.myriad.client.particle.AbilityMeshParticle::registerGroup);
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(com.myriad.registry.ModEntities.BASE_CRYSTAL.get(),net.minecraft.client.renderer.entity.NoopRenderer::new));
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(com.myriad.registry.ModEntities.SIEGE_MINION.get(), com.myriad.client.SiegeMinionRenderer::new));
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(com.myriad.registry.ModEntities.LANE_MINION.get(), com.myriad.client.MinionRenderer::new));
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(com.myriad.registry.ModEntities.DEFENSE_TOWER.get(),
                        net.minecraft.client.renderer.entity.NoopRenderer::new));
        // 按键施法（客户端事件总线）
        NeoForge.EVENT_BUS.addListener(MyriadClient::onKeyInput);
        NeoForge.EVENT_BUS.addListener(com.myriad.client.SkillChargeClient::input);
        // 客户端指令（职业选择界面等）
        NeoForge.EVENT_BUS.addListener(MyriadClient::onRegisterClientCommands);
        // 冲刺 FOV 效果（影袭等快速位移时镜头前冲感）
        NeoForge.EVENT_BUS.addListener(MyriadClient::onComputeFov);
        // 客户端冲刺推进（影袭协同，aiStep 前设置速度）
        NeoForge.EVENT_BUS.addListener(MyriadClient::onClientPlayerTick);
        // 在原版玩家输入与物理处理完成后再写入 motion，避免被 aiStep 覆盖。
        NeoForge.EVENT_BUS.addListener(MyriadClient::onClientPlayerTickPost);
        // 受伤包可能在玩家 tick 之后抵达；每帧提取相机状态前再次清除受击倾斜。
        NeoForge.EVENT_BUS.addListener(MyriadClient::onRenderFramePre);
        // 完全隐身：隐形玩家（含装备/手持物）完全不渲染
        NeoForge.EVENT_BUS.addListener(MyriadClient::onRenderLivingPre);
        // 远程使用武器显隐提示完整恢复；近战保留原版武器显示。
        NeoForge.EVENT_BUS.addListener(MyriadClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) ->
                com.myriad.client.ClientDataHolder.resetConnection());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                com.myriad.client.ClientDataHolder.resetConnection());
        // 名称标签不能穿过实体视线中的方块显示（队友共享视野也遵守这条规则）。
        NeoForge.EVENT_BUS.addListener(MyriadClient::onRenderNameTagCanRender);
        // 轮回之息：复活期间头顶俯视视角
        // 周天覆阵引导：灵魂出窍式侧视自己视角
        NeoForge.EVENT_BUS.addListener(com.myriad.client.ZhoutianChannelView::onCamera);
        // 寒泽 R 千里冰封：头顶 10 格俯视施法者
        NeoForge.EVENT_BUS.addListener(com.myriad.client.HanzeFrostView::onCamera);
        // 墨尘 E 墨灵护阵布阵：左键确认 / 右键取消（点击被消费，不再触发原版攻击/使用）
        NeoForge.EVENT_BUS.addListener(MyriadClient::onMouseButton);
        // 普攻输入由服务端接管：近战挥空同样提交挥砍，动作由确认包播放。
        NeoForge.EVENT_BUS.addListener(MyriadClient::onInteractionKeyMapping);
        // New, explicitly scoped local first-person dagger view; no shared/third-person action system.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.myriad.client.firstperson.NightDaggerView::onAttack);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.myriad.client.firstperson.ThunderSwordView::onAttack);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.myriad.client.firstperson.FlameDaoView::onAttack);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.myriad.client.firstperson.ShuoView::onAttack);
    }

    /**
     * 近战完全交还原版攻击输入；仅远程专属普攻使用自定义请求。
     */
    private static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        if (!com.myriad.client.ClientDataHolder.modEnabled()) return;
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.myriad.client.RespawnSpectatorView.isActive()) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (mc.player == null || mc.level == null || mc.gui.screen() != null
                || com.myriad.client.ClientDataHolder.characterId().isEmpty()) return;
        if(com.myriad.combat.VanillaMeleeAttack.usesVanilla(com.myriad.character.CharacterType.byId(
                com.myriad.client.ClientDataHolder.characterId()))){
            if(com.myriad.client.ClientDataHolder.placementType()!=0
                    ||com.myriad.client.ClientDataHolder.isChanneling()
                    ||mc.player.getAttackStrengthScale(.5f)<1
                    ||com.myriad.client.ClientDataHolder.buffTicks("g1_stun")>0){
                event.setSwingHand(false);event.setCanceled(true);
            }
            return;
        }

        AttackRay ray = findCurrentAttackTarget(mc);
        if (ray.target != null) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                    new NetworkSetup.BasicAttackPayload(ray.target.getId()));
        }
        // 左键仅作为模组普攻媒介；无论命中或挥空都不进入原版攻击/挖掘链。
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private static AttackRay findCurrentAttackTarget(Minecraft mc) {
        net.minecraft.client.player.LocalPlayer player = mc.player;
        double reach = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
        net.minecraft.world.phys.Vec3 start = player.getEyePosition();
        net.minecraft.world.phys.Vec3 direction = player.getLookAngle();
        if (direction.lengthSqr() < 1.0E-6) return new AttackRay(null, false);
        net.minecraft.world.phys.Vec3 end = start.add(direction.normalize().scale(reach));

        net.minecraft.world.phys.BlockHitResult block = mc.level.clip(
                new net.minecraft.world.level.ClipContext(start, end,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        double allowedDistance = reach;
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            // 方块交给原版处理（挖掘／攻击方块），同时不允许隔墙把实体视为可攻击目标。
            allowedDistance = start.distanceTo(block.getLocation());
        }

        net.minecraft.world.phys.AABB search = player.getBoundingBox()
                .expandTowards(direction.normalize().scale(allowedDistance)).inflate(1.0);
        net.minecraft.world.entity.Entity closest = null;
        double closestDistance = allowedDistance + 1.0E-4;
        for (net.minecraft.world.entity.Entity candidate : mc.level.getEntities(player, search,
                entity -> entity.isAlive() && entity.isPickable() && !entity.isSpectator())) {
            // 不再用至多滞后 5 tick 的客户端迷雾快照阻止攻击提交。
            // 隐藏目标本身不会渲染，最终能否攻击仍由服务端 VisionSystem 权威校验。
            java.util.Optional<net.minecraft.world.phys.Vec3> contact = candidate.getBoundingBox()
                    .inflate(0.10).clip(start, end);
            if (contact.isPresent()) {
                double distance = start.distanceTo(contact.get());
                if (distance <= closestDistance) {
                    closestDistance = distance;
                    closest = candidate;
                }
            }
        }
        return new AttackRay(closest, block.getType() != net.minecraft.world.phys.HitResult.Type.MISS);
    }

    private record AttackRay(net.minecraft.world.entity.Entity target, boolean blockTarget) { }

    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ABILITY_MESH.get(), com.myriad.client.particle.AbilityMeshParticle.Provider::new);

        event.registerSpriteSet(ModParticles.SULAN_BLADE_ARC.get(), SulanBladeArcParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPIRIT_MOTE.get(), SpiritMoteParticle.Provider::new);

        event.registerSpriteSet(ModParticles.RAINBOW_MOTE.get(), RainbowMoteParticle.Provider::new);
        event.registerSpriteSet(ModParticles.WIND1_LARGE.get(), sprites ->
                new Wind1Particle.Provider(sprites, true));
        event.registerSpriteSet(ModParticles.WIND1_SMALL.get(), sprites ->
                new Wind1Particle.Provider(sprites, false));
    }

    /** 鼠标拦截：
     *  布阵阶段：左键 = 确认布阵，右键 = 取消布阵；
     *  法师手持初始武器：右键 = 发射魔法弹（拦截原版点击，不被实体/方块交互吞掉） */
    private static void onMouseButton(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre event) {
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }
        if (Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        if (event.getButton() == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                && com.myriad.client.RespawnSpectatorView.isActive()) {
            event.setCanceled(true);
            ClientPacketDistributor.sendToServer(new com.myriad.network.CycleSpectatorPayload());
            return;
        }
        int placementType = com.myriad.client.ClientDataHolder.placementType();
        if (placementType != 0) {
            if (event.getButton() == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                event.setCanceled(true);
                ClientPacketDistributor.sendToServer(new NetworkSetup.ConfirmPlacementPayload());
            } else if (event.getButton() == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                event.setCanceled(true);
                ClientPacketDistributor.sendToServer(new NetworkSetup.CancelPlacementPayload());
            }
            return;
        }

        // 法师（墨尘、寒泽、青萝）手持初始武器：右键发射魔法弹（墨尘指向传送阵时保留交互）
        if (event.getButton() == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            net.minecraft.client.player.LocalPlayer lp = Minecraft.getInstance().player;
            if (lp != null && !com.myriad.client.ClientDataHolder.characterId().isEmpty()
                    && ("xuanchen".equalsIgnoreCase(com.myriad.client.ClientDataHolder.characterId())
                            || "hanze".equalsIgnoreCase(com.myriad.client.ClientDataHolder.characterId())
                            || "qingluo".equalsIgnoreCase(com.myriad.client.ClientDataHolder.characterId())
                            || "yunsheng".equalsIgnoreCase(com.myriad.client.ClientDataHolder.characterId()))
                    && isInitialWeaponClient(lp.getMainHandItem())) {
                // 准星指向传送阵盔甲架（专用队伍标记）：不拦截，走原版实体交互（传送）
                net.minecraft.world.phys.HitResult hit = Minecraft.getInstance().hitResult;
                // 训练假人的右键设置优先于法术弹，避免法师手持初始武器时无法打开界面。
                if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit
                        && entityHit.getEntity() instanceof net.minecraft.world.entity.npc.villager.Villager villager
                        && villager.hasCustomName()) {
                    String dummyName = villager.getName().getString();
                    if (dummyName.equals(net.minecraft.network.chat.Component
                                    .translatable("entity.myriad.jiaren").getString())
                            || dummyName.equals("友方灵偶")) {
                        return;
                    }
                }
                if (hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY
                        && ((net.minecraft.world.phys.EntityHitResult) hit).getEntity()
                                instanceof net.minecraft.world.entity.decoration.ArmorStand stand
                        && stand.getTeam() != null
                        && com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.ARRAY_TEAM
                                .equals(stand.getTeam().getName())) {
                    return;
                }
                event.setCanceled(true);
                ClientPacketDistributor.sendToServer(new NetworkSetup.MagicBulletFirePayload());
            }
        }
    }

    /** 完全隐身：隐身状态玩家取消渲染；轮回之息复活期间自身半透明虚化 */
    private static void onRenderLivingPre(
            net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?, ?> event) {
        if (!com.myriad.client.ClientDataHolder.modEnabled()) return;
        var viewer = Minecraft.getInstance().player;
        if (viewer != null && viewer.isSpectator()
                && com.myriad.client.ClientDataHolder.syncedCounter("hero_respawn") <= 0) return;
        net.minecraft.client.renderer.entity.state.EntityRenderState state = event.getRenderState();
        if (com.myriad.client.ClientDataHolder.buffs().getOrDefault("hero_respawn", 0) > 0) {
            var mc = Minecraft.getInstance();
            // A dead player may watch allies, never scout enemies with the spectator camera.
            if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState deadViewAvatar
                    && mc.level != null && mc.player != null) {
                var target = mc.level.getEntity(deadViewAvatar.id);
                if (target != mc.player && (mc.player.getTeam() == null || target == null
                        || target.getTeam() != mc.player.getTeam())) {
                    event.setCanceled(true);
                    return;
                }
            } else {
                event.setCanceled(true);
                return;
            }
        }
        if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar
                && state.entityType == net.minecraft.world.entity.EntityTypes.PLAYER
                && Minecraft.getInstance().player != null
                && avatar.id != Minecraft.getInstance().player.getId()
                && !com.myriad.client.FogOfWarClient.shouldRenderPlayer(avatar.id)) {
            event.setCanceled(true);
            return;
        }
        if (state.isInvisible && state.entityType == net.minecraft.world.entity.EntityTypes.PLAYER) {
            event.setCanceled(true);
        }
    }

    /** 远程恢复期间隐藏第一人称专属武器；原版近战不隐藏。 */
    private static void onRenderHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        if (com.myriad.client.firstperson.ShuoView.render(event)) return;
        if (com.myriad.client.firstperson.NightDaggerView.render(event)) return;
        if (com.myriad.client.firstperson.ThunderSwordView.render(event)) return;
        if (com.myriad.client.firstperson.FlameDaoView.render(event)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !com.myriad.client.ClientDataHolder.modEnabled()
                || !isInitialWeaponClient(event.getItemStack())) return;
        // Vanilla melee weapons remain visible during their ordinary recovery animation.
        if(com.myriad.combat.VanillaMeleeAttack.usesVanilla(com.myriad.character.CharacterType.byId(
                com.myriad.client.ClientDataHolder.characterId())))return;

        if (!com.myriad.client.BasicAttackReadiness.canAttack(mc.player)) event.setCanceled(true);
    }

    /**
     * 战争迷雾只控制“是否知道目标存在”，名称仍需满足真实视线。
     * 因此队友共享到的玩家、发光显形的玩家都可以被渲染，但隔墙时只保留实体轮廓，不渲染名字。
     */
    private static void onRenderNameTagCanRender(
            net.neoforged.neoforge.client.event.RenderNameTagEvent.CanRender event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        net.minecraft.world.entity.Entity target = event.getEntity();
        if (!com.myriad.client.ClientDataHolder.modEnabled()) return;
        if (!(target instanceof net.minecraft.world.entity.player.Player)
                && target instanceof net.minecraft.world.entity.LivingEntity && target.hasCustomName()
                && target.getCustomName().getString().matches(".*[0-9]+\\s*/\\s*[0-9]+.*")
                && !com.myriad.network.EnemyHealthPayload.inRange(mc.player.distanceToSqr(target))) {
            event.setCanRender(net.minecraft.util.TriState.FALSE);
            return;
        }
        if (target instanceof net.minecraft.world.entity.player.Player other && other != mc.player
                && (mc.player.getTeam() == null || mc.player.getTeam() != other.getTeam())) {
            event.setCanRender(net.minecraft.util.TriState.FALSE);
            return;
        }
        if (!(target instanceof net.minecraft.world.entity.player.Player)
                || target == mc.player || target.level() != mc.level) return;
        net.minecraft.world.phys.Vec3 start = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 end = target.position().add(0, target.getBbHeight() * .85 + .25, 0);
        net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                start, end, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && hit.getLocation().distanceToSqr(start) + .01 < start.distanceToSqr(end)) {
            event.setCanRender(net.minecraft.util.TriState.FALSE);
        }
    }

    private static void onClientPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof net.minecraft.client.player.LocalPlayer local) {
            com.myriad.client.firstperson.NightDaggerView.tick();
            com.myriad.client.firstperson.ThunderSwordView.tick();
            com.myriad.client.firstperson.FlameDaoView.tick();
            com.myriad.client.firstperson.ShuoView.tick();
            com.myriad.client.StarPullController.tick();
            com.myriad.client.RespawnSpectatorView.enforceFirstPerson();
            lockWeaponSlot(local);
        }
    }

    private static void onClientPlayerTickPost(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.client.player.LocalPlayer local) {
            com.myriad.client.DashController.tick();
            // 模组战斗禁用原版受击镜头摇晃；仅清除本地渲染计时，不干预服务端受伤与无敌帧。
            if (com.myriad.client.ClientDataHolder.modEnabled()) local.hurtTime = 0;
        }
    }

    /**
     * 帧级兜底：受伤动画包可能在 PlayerTick.Post 之后、当前帧相机状态提取之前抵达。
     * 在 RenderFrame.Pre 清除本地 hurtTime，保证原版 bobHurt 从第一帧起就不会产生倾斜。
     */
    private static void onRenderFramePre(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        if (!com.myriad.client.ClientDataHolder.modEnabled()) return;
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.hurtTime = 0;
    }

    /** 客户端本地武器锁：物品栏第一格每 tick 强制为初始武器（视觉上无法移动/丢弃） */
    private static void lockWeaponSlot(net.minecraft.client.player.LocalPlayer p) {
        if (!com.myriad.client.ClientDataHolder.modEnabled()) return;
        if (com.myriad.client.ClientDataHolder.characterId().isEmpty()) {
            return;
        }
        // 拖拽中/鼠标上的初始武器：放回第一格
        net.minecraft.world.inventory.AbstractContainerMenu menu = p.containerMenu;
        if (menu != null) {
            net.minecraft.world.item.ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && isInitialWeaponClient(carried)) {
                if (p.getInventory().getItem(0).isEmpty()) {
                    p.getInventory().setItem(0, carried);
                    menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
        net.minecraft.world.item.ItemStack slot0 = p.getInventory().getItem(0);
        if (!isInitialWeaponClient(slot0)) {
            if (!slot0.isEmpty()) {
                p.getInventory().add(slot0);
            }
            String charId = com.myriad.client.ClientDataHolder.characterId();
            net.minecraft.world.item.ItemStack weapon = new net.minecraft.world.item.ItemStack(
                    com.myriad.registry.ModItems.initialWeaponItem(charId));
            weapon.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
            weapon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, clientWeaponName());
            weapon.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
            p.getInventory().setItem(0, weapon);
        }
    }

    /** 客户端专属初始武器判定。 */
    private static boolean isInitialWeaponClient(net.minecraft.world.item.ItemStack stack) {
        return com.myriad.registry.ModItems.isInitialWeaponItem(stack)
                && stack.has(net.minecraft.core.component.DataComponents.UNBREAKABLE);
    }

    /** 客户端初始武器名字与颜色（按角色区分：墨尘=墨杖黑色，夜影=影刺金色，天刑=雷霆剑蓝色） */
    private static net.minecraft.network.chat.Component clientWeaponName() {
        String charId = com.myriad.client.ClientDataHolder.characterId();

        if ("xuanchen".equalsIgnoreCase(charId)) {
            return net.minecraft.network.chat.Component.translatable("item.myriad.zhenqi")
                    .withStyle(net.minecraft.ChatFormatting.BLACK)
                    .withStyle(net.minecraft.ChatFormatting.BOLD);
        }
        if ("hanze".equalsIgnoreCase(charId)) {
            return net.minecraft.network.chat.Component.translatable("item.myriad.shuangbing")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF8FE8FF));
        }
        if ("tianxing".equalsIgnoreCase(charId)) {
            return net.minecraft.network.chat.Component.translatable("item.myriad.leitingjian")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF55AAFF));
        }
        if ("fenjue".equalsIgnoreCase(charId)) {
            return net.minecraft.network.chat.Component.translatable("item.myriad.lieyanjian")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFF7F27));
        }
        if ("sulan".equalsIgnoreCase(charId)) {
            return net.minecraft.network.chat.Component.translatable("item.myriad.fengren")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF55F0B0));
        }
        if ("lingyuan".equalsIgnoreCase(charId)) {
            return net.minecraft.network.chat.Component.translatable("item.myriad.durandal")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFBFE8FF));
        }
        if ("qingluo".equalsIgnoreCase(charId)) {
            return net.minecraft.network.chat.Component.translatable("item.myriad.qingluo_staff")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF7FE6A1));
        }
        if ("chixiao".equalsIgnoreCase(charId)) {
            return net.minecraft.network.chat.Component.translatable("item.myriad.chixiao_blade")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFF5A38));
        }
        if ("yunsheng".equalsIgnoreCase(charId)) {
            return net.minecraft.network.chat.Component.translatable("item.myriad.yunsheng_bell")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF9EFFF2));
        }

        return net.minecraft.network.chat.Component.translatable("item.myriad.yingci")
                .withStyle(net.minecraft.ChatFormatting.GOLD)
                .withStyle(net.minecraft.ChatFormatting.BOLD);
    }

    /** 冲刺 FOV 目标（平滑过渡，仅冲刺状态触发） */
    private static boolean fovFast = false;
    private static float fovCurrent = 1.0f;

    /** 冲刺时 FOV 平滑放大（镜头前冲动感）；疾跑不触发 */
    private static void onComputeFov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
        var mc = Minecraft.getInstance();
        if (!com.myriad.client.ClientDataHolder.modEnabled() || mc.player == null) {
            fovCurrent = 1.0f;
            return;
        }
        fovFast = com.myriad.client.DashController.isDashing();
        // 平滑过渡（每帧逼近 25%）
        float target = fovFast ? 1.08f : 1.0f;
        fovCurrent += (target - fovCurrent) * 0.25f;
        if (Math.abs(fovCurrent - target) < .001f) fovCurrent = target;
        event.setFOV(event.getFOV() * fovCurrent);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Myriad.MODID, "hud"), HudOverlay.INSTANCE);
        // 血量条 → 文本（生命 hp/max）；饱食度条 → 文本（法力 mana/max）
        wrapStatusLayer(event, net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH, StatusOverlay.HEALTH);
        wrapStatusLayer(event, net.neoforged.neoforge.client.gui.VanillaGuiLayers.FOOD_LEVEL, StatusOverlay.MANA);
        // 原版经验等级/经验条 → 游戏等级/经验文本
        wrapStatusLayer(event, net.neoforged.neoforge.client.gui.VanillaGuiLayers.EXPERIENCE_LEVEL, StatusOverlay.LEVEL);
        wrapStatusLayer(event, net.neoforged.neoforge.client.gui.VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND,
                StatusOverlay.NOOP);
        wrapStatusLayer(event, net.neoforged.neoforge.client.gui.VanillaGuiLayers.CONTEXTUAL_INFO_BAR,
                StatusOverlay.NOOP);
    }

    private static void wrapStatusLayer(RegisterGuiLayersEvent event, Identifier id,
            net.neoforged.neoforge.client.gui.GuiLayer replacement) {
        event.wrapLayer(id, original -> (gui, delta) -> {
            if (com.myriad.client.ClientDataHolder.modEnabled()
                    && com.myriad.client.ClientDataHolder.inGame()) replacement.render(gui, delta);
            else original.render(gui, delta);
        });
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
        event.register(KEY_SKILL_Q);
        event.register(KEY_SKILL_W);
        event.register(KEY_SKILL_E);
        event.register(KEY_SKILL_R);
        event.register(KEY_SKILL_F);
        event.register(KEY_INFO);
        event.register(KEY_SHOP);
        event.register(KEY_RECALL);
    }

    /** 客户端指令：/myriad choose 打开职业选择界面；/myriad start 打开开局管理界面 */
    private static void onRegisterClientCommands(
            net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("myriad")
                .then(net.minecraft.commands.Commands.literal("choose")
                        .executes(ctx -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().setScreenAndShow(
                                        new com.myriad.client.gui.CharacterSelectScreen());
                            }
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("start")
                        .executes(ctx -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().setScreenAndShow(
                                        new com.myriad.client.gui.TeamManageScreen());
                            }
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("settings")
                        .executes(ctx -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().setScreenAndShow(
                                        new com.myriad.client.gui.SettingsScreen());
                            }
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("minimap")
                        .executes(ctx -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().setScreenAndShow(
                                        new com.myriad.client.gui.MinimapPositionScreen(null));
                            }
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("hud")
                        .executes(ctx -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().setScreenAndShow(
                                        new com.myriad.client.gui.HudEditorScreen(null));
                            }
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("results")
                        .executes(ctx -> {
                            com.myriad.client.MatchResultsStore.open();
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("shop")
                        .executes(ctx -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().setScreenAndShow(
                                        new com.myriad.client.gui.ShopScreen());
                            }
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("train")
                        .executes(ctx -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().setScreenAndShow(
                                        new com.myriad.client.gui.TrainScreen());
                            }
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("wind_test")
                        .executes(ctx -> {
                            playWind1Test();
                            return 1;
                        })));
    }

    /** 客户端预览 Wind1.efkproj 的近似效果，不发送服务器包。 */
    private static void playWind1Test() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        net.minecraft.world.phys.Vec3 center = mc.player.position().add(0.0, 0.9, 0.0);
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
        for (int i = 0; i < 20; i++) {
            double radius = 1.5 * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble() * Math.PI * 2.0;
            double x = center.x + Math.cos(angle) * radius;
            double y = center.y + (random.nextDouble() - 0.5) * 3.0;
            double z = center.z + Math.sin(angle) * radius;
            level.addParticle(ModParticles.WIND1_LARGE.get(), x, y, z,
                    1.15 + random.nextDouble() * 0.45,
                    random.nextDouble() * 6.2831855,
                    (random.nextDouble() - 0.5) * 0.18);
        }
        for (int i = 0; i < 50; i++) {
            double x = center.x + (random.nextDouble() - 0.5) * 2.0;
            double y = center.y + (random.nextDouble() - 0.5) * 2.0;
            double z = center.z + (random.nextDouble() - 0.5) * 2.0;
            level.addParticle(ModParticles.WIND1_SMALL.get(), x, y, z,
                    0.50 + random.nextDouble() * 0.35,
                    random.nextDouble() * 6.2831855,
                    (random.nextDouble() - 0.5) * 0.28);
        }
    }

    private static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }
        // 开局选人阶段：屏蔽 ESC（选择期间无法退出界面）
        if (com.myriad.client.ClientDataHolder.pickActive()
                && event.getKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            return;
        }
        // 初始武器锁定：选中槽 0 时抢先消费丢弃键（无法丢弃影刺）
        net.minecraft.client.player.LocalPlayer lp = Minecraft.getInstance().player;
        if (lp != null && !com.myriad.client.ClientDataHolder.characterId().isEmpty()
                && lp.getInventory().getSelectedSlot() == 0
                && Minecraft.getInstance().options.keyDrop.matches(event.getKeyEvent())) {
            Minecraft.getInstance().options.keyDrop.consumeClick();
            return;
        }
        // mod 未开启时：模组按键全部不生效
        if (!com.myriad.client.ClientDataHolder.modEnabled()) {
            return;
        }
        if (KEY_INFO.consumeClick()) {
            if (Minecraft.getInstance().gui.screen() == null && Minecraft.getInstance().player != null) {
                Myriad.LOGGER.info("[key] C pressed, opening InfoScreen");
                Minecraft.getInstance().setScreenAndShow(new InfoScreen());
            } else {
                Myriad.LOGGER.info("[key] C pressed but screen={} player={}",
                        Minecraft.getInstance().gui.screen() != null,
                        Minecraft.getInstance().player != null);
            }
            return;
        }
        if (KEY_SHOP.consumeClick()) {
            if (Minecraft.getInstance().gui.screen() == null && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().setScreenAndShow(
                        new com.myriad.client.gui.ShopScreen());
            }
            return;
        }
        if (Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        int slot = -1;
        if (KEY_RECALL.consumeClick()) {
            ClientPacketDistributor.sendToServer(new NetworkSetup.RecallPayload());
            return;
        }
        // This is a PRESS event, not a client tick: consumeClick can consume a stale
        // queued press on an unrelated later key (e.g. moving after reaching 30 energy).
        for (KeyMapping key : new KeyMapping[]{KEY_SKILL_Q, KEY_SKILL_W, KEY_SKILL_E, KEY_SKILL_R, KEY_SKILL_F}) {
            while (key.consumeClick()) { }
        }
        if (KEY_SKILL_Q.matches(event.getKeyEvent())) {
            slot = CharacterType.SLOT_Q;
        } else if (KEY_SKILL_W.matches(event.getKeyEvent())) {
            slot = CharacterType.SLOT_W;
        } else if (KEY_SKILL_E.matches(event.getKeyEvent())) {
            slot = CharacterType.SLOT_E;
        } else if (KEY_SKILL_R.matches(event.getKeyEvent())) {
            slot = CharacterType.SLOT_R;
        } else if (KEY_SKILL_F.matches(event.getKeyEvent())) {
            slot = CharacterType.SLOT_F;
        }
        if (slot >= 0) {
            Minecraft mc = Minecraft.getInstance();
            // 回风步默认也绑定 W；若技能键与前进键相同，不能把这次施法按键误当成移动输入。
            boolean castKeyIsForward = slot == CharacterType.SLOT_W
                    && KEY_SKILL_W.getKey().equals(mc.options.keyUp.getKey());
            float forward = (!castKeyIsForward && mc.options.keyUp.isDown() ? 1.0F : 0.0F)
                    - (mc.options.keyDown.isDown() ? 1.0F : 0.0F);
            float left = (mc.options.keyLeft.isDown() ? 1.0F : 0.0F)
                    - (mc.options.keyRight.isDown() ? 1.0F : 0.0F);
            float moveX = 0.0F;
            float moveZ = 0.0F;
            if (mc.player != null && (forward != 0.0F || left != 0.0F)) {
                net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
                net.minecraft.world.phys.Vec3 facing = new net.minecraft.world.phys.Vec3(look.x, 0, look.z);
                if (facing.lengthSqr() > 1.0E-6) {
                    facing = facing.normalize();
                    // 面朝南方(+Z)时，屏幕左侧是东方(+X)。
                    net.minecraft.world.phys.Vec3 leftward = new net.minecraft.world.phys.Vec3(
                            facing.z, 0, -facing.x);
                    net.minecraft.world.phys.Vec3 movement = facing.scale(forward).add(leftward.scale(left));
                    if (movement.lengthSqr() > 1.0E-6) {
                        movement = movement.normalize();
                        moveX = (float)movement.x;
                        moveZ = (float)movement.z;
                    }
                }
            }
            ClientPacketDistributor.sendToServer(new NetworkSetup.CastSkillPayload(slot, moveX, moveZ));
        }
    }
}
