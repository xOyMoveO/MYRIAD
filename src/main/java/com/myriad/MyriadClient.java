package com.myriad;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.myriad.client.platform.AttackInput;
import com.myriad.network.Packets;

import com.myriad.character.CharacterType;
import com.myriad.client.HudOverlay;
import com.myriad.client.CombatHudState;
import com.myriad.client.particle.SulanBladeArcParticle;
import com.myriad.client.particle.SpiritMoteParticle;
import com.myriad.client.particle.RainbowMoteParticle;
import com.myriad.client.particle.Wind1Particle;
import com.myriad.client.gui.InfoScreen;
import com.myriad.network.NetworkSetup;
import com.myriad.registry.ModParticles;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public class MyriadClient implements net.fabricmc.api.ClientModInitializer {
    private static final com.myriad.client.DeferredScreenRequest SCREEN_REQUEST=new com.myriad.client.DeferredScreenRequest();
    /** 仙凡劫专属按键分类（设置-按键控制中独立一栏） */
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category
            .register(Identifier.fromNamespaceAndPath(Myriad.MODID, "keys"));

    public static final KeyMapping KEY_SIGNAL = new KeyMapping("key.myriad.team_signal",
            InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE, KEY_CATEGORY);
    public static final KeyMapping KEY_DEATH_RECAP =
            new KeyMapping("key.myriad.death_recap", InputConstants.KEY_J, KEY_CATEGORY);

    public static final KeyMapping KEY_SKILL_Q =
            new KeyMapping("key.myriad.skill_q", InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping KEY_SKILL_W =
            new KeyMapping("key.myriad.skill_w", InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping KEY_SKILL_E =
            new KeyMapping("key.myriad.skill_e", InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping KEY_SKILL_R =
            new KeyMapping("key.myriad.skill_r", InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping KEY_SKILL_F =
            new KeyMapping("key.myriad.skill_f", InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    /** 个人信息界面 */
    public static final KeyMapping KEY_LOBBY =
            new KeyMapping("key.myriad.lobby", InputConstants.KEY_H, KEY_CATEGORY);
    public static final KeyMapping KEY_INFO =
            new KeyMapping("key.myriad.info", InputConstants.KEY_C, KEY_CATEGORY);
    public static final KeyMapping KEY_RECALL =
            new KeyMapping("key.myriad.recall", InputConstants.KEY_B, KEY_CATEGORY);
    /** 装备商店界面 */
    public static final KeyMapping KEY_SHOP =
            new KeyMapping("key.myriad.shop", InputConstants.KEY_P, KEY_CATEGORY);

    @Override
    public void onInitializeClient() {
        NetworkSetup.registerClient();
        com.myriad.client.LobbyClient.register();
        onRegisterGuiLayers();
        onRegisterKeyMappings();
        onRegisterParticleProviders();
        com.myriad.client.particle.AbilityMeshParticle.registerGroup();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.myriad.registry.ModEntities.BASE_CRYSTAL.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.myriad.registry.ModEntities.SIEGE_MINION.get(), com.myriad.client.SiegeMinionRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.myriad.registry.ModEntities.LANE_MINION.get(), com.myriad.client.MinionRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.myriad.registry.ModEntities.DEFENSE_TOWER.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.myriad.registry.ModEntities.CHERRY_RIFT_MONSTER.get(), com.myriad.client.CherryRiftMonsterRenderer::new);
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(
                MyriadClient::allowCommand);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{
            com.myriad.client.LuoxiaInput.tick(client);
            com.myriad.client.LobbyClient.tick(client);
            var screen=client.gui.screen();
            boolean chat=screen instanceof net.minecraft.client.gui.screens.ChatScreen;
            SCREEN_REQUEST.tick(client.player==null?null:client.getConnection(),client.level,chat,screen!=null&&!chat);
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> {SCREEN_REQUEST.clear();com.myriad.client.LobbyClient.clear();com.myriad.client.ClientDataHolder.resetConnection();});
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {SCREEN_REQUEST.clear();com.myriad.client.LobbyClient.clear();com.myriad.client.ClientDataHolder.resetConnection();});
    }

    /** Called at Minecraft.startAttack, including keyboard or mouse rebound attacks. */
    public static AttackInput attack(boolean allowMeleeAssist) {
        AttackInput event = new AttackInput();
        onInteractionKeyMapping(event);
        Minecraft mc = Minecraft.getInstance();
        event.setMeleeAssist(allowMeleeAssist && !event.isCanceled()
                && com.myriad.client.MeleeAssistInput.shouldAssist(mc));
        com.myriad.client.firstperson.KehengView.attack(event);
        com.myriad.client.firstperson.NightDaggerView.onAttack(event);
        com.myriad.client.firstperson.ThunderSwordView.onAttack(event);
        com.myriad.client.firstperson.FlameDaoView.onAttack(event);
        com.myriad.client.firstperson.ShuoView.onAttack(event);
        com.myriad.client.firstperson.WindBladeView.onAttack(event);
        if (event.isMeleeAssist()) com.myriad.client.MeleeAssistInput.send(mc.player);
        return event;
    }

    /**
     * Preserve native attack input for exact melee selections and route dedicated ranged attacks.
     */
    private static void onInteractionKeyMapping(AttackInput event) {
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

        if("luoxia".equals(com.myriad.client.ClientDataHolder.characterId())){
            com.myriad.client.LuoxiaInput.press(mc);
            event.setSwingHand(false);event.setCanceled(true);return;
        }
        if("yike".equals(com.myriad.client.ClientDataHolder.characterId())){
            Packets.sendToServer(new NetworkSetup.BasicAttackPayload(-1));
            event.setSwingHand(false);event.setCanceled(true);return;
        }
        AttackRay ray = findCurrentAttackTarget(mc);
        if (ray.target != null) {
            com.myriad.network.Packets.sendToServer(
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

    private static void onRegisterParticleProviders() {
        var event = net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance();
        event.register(ModParticles.ABILITY_MESH.get(), com.myriad.client.particle.AbilityMeshParticle.Provider::new);

        event.register(ModParticles.SULAN_BLADE_ARC.get(), SulanBladeArcParticle.Provider::new);
        event.register(ModParticles.SPIRIT_MOTE.get(), SpiritMoteParticle.Provider::new);

        event.register(ModParticles.RAINBOW_MOTE.get(), RainbowMoteParticle.Provider::new);
        event.register(ModParticles.WIND1_LARGE.get(), sprites ->
                new Wind1Particle.Provider(sprites, true));
        event.register(ModParticles.WIND1_SMALL.get(), sprites ->
                new Wind1Particle.Provider(sprites, false));
    }

    /** 鼠标拦截：
     *  布阵阶段：左键 = 确认布阵，右键 = 取消布阵；
     *  法师手持初始武器：右键 = 发射魔法弹（拦截原版点击，不被实体/方块交互吞掉） */
    public static void onMouseButton(com.myriad.client.platform.MouseInput event) {
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }
        if (Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        if (new com.myriad.client.platform.KeyInput(event.getAction(), event.getButton()).matches(KEY_SIGNAL)
                && com.myriad.client.TeamSignalClient.available()) {
            event.setCanceled(true);
            com.myriad.client.gui.TeamSignalWheel.open();
            return;
        }
        if (event.getButton() == InputConstants.MOUSE_BUTTON_LEFT
                && com.myriad.client.RespawnSpectatorView.isActive()) {
            event.setCanceled(true);
            Packets.sendToServer(new com.myriad.network.CycleSpectatorPayload());
            return;
        }
        int placementType = com.myriad.client.ClientDataHolder.placementType();
        if (placementType != 0) {
            if (event.getButton() == InputConstants.MOUSE_BUTTON_LEFT) {
                event.setCanceled(true);
                Packets.sendToServer(new NetworkSetup.ConfirmPlacementPayload());
            } else if (event.getButton() == InputConstants.MOUSE_BUTTON_RIGHT) {
                event.setCanceled(true);
                Packets.sendToServer(new NetworkSetup.CancelPlacementPayload());
            }
            return;
        }

        // 法师（墨尘、寒泽、青萝）手持初始武器：右键发射魔法弹（墨尘指向传送阵时保留交互）
        if (event.getButton() == InputConstants.MOUSE_BUTTON_RIGHT) {
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
                com.myriad.client.firstperson.InkFlagView.prepareBasic();
                Packets.sendToServer(new NetworkSetup.MagicBulletFirePayload());
            }
        }
    }

    /** 完全隐身：隐身状态玩家取消渲染；轮回之息复活期间自身半透明虚化 */
    public static boolean hideLiving(
            net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        if (!com.myriad.client.ClientDataHolder.modEnabled()) return false;
        var viewer = Minecraft.getInstance().player;
        if (viewer != null && viewer.isSpectator()
                && com.myriad.client.ClientDataHolder.syncedCounter("hero_respawn") <= 0) return false;
        if (com.myriad.client.ClientDataHolder.buffs().getOrDefault("hero_respawn", 0) > 0) {
            var mc = Minecraft.getInstance();
            // A dead player may watch allies, never scout enemies with the spectator camera.
            if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState deadViewAvatar
                    && mc.level != null && mc.player != null) {
                var target = mc.level.getEntity(deadViewAvatar.id);
                if (target != mc.player && (mc.player.getTeam() == null || target == null
                        || target.getTeam() != mc.player.getTeam())) {
                    return true;
                }
            } else {
                return true;
            }
        }
        if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar
                && state.entityType == net.minecraft.world.entity.EntityTypes.PLAYER
                && Minecraft.getInstance().player != null
                && avatar.id != Minecraft.getInstance().player.getId()
                && !com.myriad.client.FogOfWarClient.shouldRenderPlayer(avatar.id)) {
            return true;
        }
        if (state.isInvisible && state.entityType == net.minecraft.world.entity.EntityTypes.PLAYER) {
            return true;
        }
        return false;
    }

    /** 远程恢复期间隐藏第一人称专属武器；原版近战不隐藏。 */
    public static void onRenderHand(com.myriad.client.platform.HandRenderContext event) {
        if (com.myriad.client.firstperson.RivetWeaponView.render(event)) return;
        if (com.myriad.client.firstperson.YikeWeaponView.render(event)) return;
        if (com.myriad.client.firstperson.ShuoView.render(event)) return;
        if (com.myriad.client.firstperson.NightDaggerView.render(event)) return;
        if (com.myriad.client.firstperson.KehengView.render(event)) return;
        if (com.myriad.client.firstperson.ThunderSwordView.render(event)) return;
        if (com.myriad.client.firstperson.FlameDaoView.render(event)) return;
        if (com.myriad.client.firstperson.WindBladeView.render(event)) return;
        if (com.myriad.client.firstperson.InkFlagView.render(event)) return;
        if (com.myriad.client.firstperson.StaffHandView.render(event)) return;
        if (com.myriad.client.firstperson.LingyuanHandView.render(event)) return;
        if (com.myriad.client.firstperson.WarSpiritHandView.render(event)) return;
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
    public static boolean hideNameTag(
            net.minecraft.world.entity.Entity target) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (!com.myriad.client.ClientDataHolder.modEnabled()) return false;
        if (!(target instanceof net.minecraft.world.entity.player.Player)
                && target instanceof net.minecraft.world.entity.LivingEntity && target.hasCustomName()
                && target.getCustomName().getString().matches(".*[0-9]+\\s*/\\s*[0-9]+.*")
                && !com.myriad.network.EnemyHealthPayload.inRange(mc.player.distanceToSqr(target))) {
            return true;
        }
        if (target instanceof net.minecraft.world.entity.player.Player other && other != mc.player
                && (mc.player.getTeam() == null || mc.player.getTeam() != other.getTeam())) {
            return true;
        }
        if (!(target instanceof net.minecraft.world.entity.player.Player)
                || target == mc.player || target.level() != mc.level) return false;
        net.minecraft.world.phys.Vec3 start = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 end = target.position().add(0, target.getBbHeight() * .85 + .25, 0);
        net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                start, end, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && hit.getLocation().distanceToSqr(start) + .01 < start.distanceToSqr(end)) {
            return true;
        }
        return false;
    }

    public static void beforePlayerTick(net.minecraft.client.player.LocalPlayer local) {
            com.myriad.client.firstperson.NightDaggerView.tick();
            com.myriad.client.firstperson.ThunderSwordView.tick();
            com.myriad.client.firstperson.FlameDaoView.tick();
            com.myriad.client.firstperson.ShuoView.tick();
            com.myriad.client.firstperson.WindBladeView.tick();
            com.myriad.client.firstperson.InkFlagView.tick();
            com.myriad.client.firstperson.StaffHandView.tick();
            com.myriad.client.firstperson.LingyuanHandView.tick();
            com.myriad.client.firstperson.WarSpiritHandView.tick();
            com.myriad.client.YikeClientState.tick();
            com.myriad.client.StarPullController.tick();
            com.myriad.client.RespawnSpectatorView.enforceFirstPerson();
            lockWeaponSlot(local);
    }

    public static void afterPlayerTick(net.minecraft.client.player.LocalPlayer local) {
            com.myriad.client.DashController.tick();
            // 模组战斗禁用原版受击镜头摇晃；仅清除本地渲染计时，不干预服务端受伤与无敌帧。
            if (com.myriad.client.ClientDataHolder.modEnabled()) local.hurtTime = 0;
    }

    /**
     * 帧级兜底：受伤动画包可能在 PlayerTick.Post 之后、当前帧相机状态提取之前抵达。
     * 在 RenderFrame.Pre 清除本地 hurtTime，保证原版 bobHurt 从第一帧起就不会产生倾斜。
     */
    public static void beforeRenderFrame() {
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
    public static float computeFov(float fov) {
        var mc = Minecraft.getInstance();
        if (!com.myriad.client.ClientDataHolder.modEnabled() || mc.player == null) {
            fovCurrent = 1.0f;
            return fov;
        }
        fovFast = com.myriad.client.DashController.isDashing();
        // 平滑过渡（每帧逼近 25%）
        float target = fovFast ? 1.08f : 1.0f;
        fovCurrent += (target - fovCurrent) * 0.25f;
        if (Math.abs(fovCurrent - target) < .001f) fovCurrent = target;
        return fov * fovCurrent;
    }

    private static void onRegisterGuiLayers() {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(Myriad.MODID, "hud"), HudOverlay.INSTANCE);
        suppressStatusLayer(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HEALTH_BAR);
        suppressStatusLayer(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.FOOD_BAR);
        suppressStatusLayer(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.EXPERIENCE_LEVEL);
        suppressStatusLayer(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.INFO_BAR);
        suppressStatusLayer(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.INFO_BAR_FOREGROUND);
    }

    private static void suppressStatusLayer(Identifier id) {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.replaceElement(id, original -> (gui, delta) -> {
            if (!CombatHudState.suppressesVanillaStatus()) original.extractRenderState(gui, delta);
        });
    }

    private static void onRegisterKeyMappings() {
        for (KeyMapping key : new KeyMapping[]{KEY_SKILL_Q, KEY_SKILL_W, KEY_SKILL_E, KEY_SKILL_R,
                KEY_SKILL_F, KEY_LOBBY, KEY_INFO, KEY_SHOP, KEY_RECALL, KEY_SIGNAL, KEY_DEATH_RECAP})
            net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(key);
    }

    /** Exact GUI aliases leave the shared /myriad server command tree available. */
    private static boolean allowCommand(String command) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return true;
        Runnable open=switch(command.strip()) {
            case "myriad choose" -> ()->mc.setScreenAndShow(new com.myriad.client.gui.CharacterSelectScreen());
            case "myriad lobby" -> com.myriad.client.LobbyClient::open;
            case "myriad settings" -> ()->mc.setScreenAndShow(new com.myriad.client.gui.SettingsScreen());
            case "myriad minimap" -> ()->mc.setScreenAndShow(new com.myriad.client.gui.MinimapPositionScreen(null));
            case "myriad hud" -> ()->mc.setScreenAndShow(new com.myriad.client.gui.HudEditorScreen(null));
            case "myriad results" -> com.myriad.client.MatchResultsStore::open;
            case "myriad recap" -> com.myriad.client.DeathRecapStore::open;
            case "myriad shop" -> ()->mc.setScreenAndShow(new com.myriad.client.gui.ShopScreen());
            case "myriad train" -> ()->mc.setScreenAndShow(new com.myriad.client.gui.TrainScreen());
            default -> null;
        };
        if(open==null){if(command.strip().equals("myriad wind_test")){playWind1Test();return false;}return true;}
        // Vanilla clears ChatScreen after ALLOW_COMMAND returns. Defer until a later client tick.
        SCREEN_REQUEST.submit(mc.getConnection(),mc.level,open);
        return false;
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

    public static void onKeyInput(com.myriad.client.platform.KeyInput event) {
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }
        if (event.matches(KEY_LOBBY) && Minecraft.getInstance().gui.screen() == null) {
            com.myriad.client.LobbyClient.open();
            return;
        }
        // 初始武器锁定：选中槽 0 时抢先消费丢弃键（无法丢弃影刺）
        net.minecraft.client.player.LocalPlayer lp = Minecraft.getInstance().player;
        if (lp != null && !com.myriad.client.ClientDataHolder.characterId().isEmpty()
                && lp.getInventory().getSelectedSlot() == 0
                && event.matches(Minecraft.getInstance().options.keyDrop)) {
            Minecraft.getInstance().options.keyDrop.consumeClick();
            return;
        }
        // mod 未开启时：模组按键全部不生效
        if (!com.myriad.client.ClientDataHolder.modEnabled()) {
            return;
        }
        if (Minecraft.getInstance().gui.screen() == null) {
            if (event.matches(KEY_SIGNAL) && com.myriad.client.TeamSignalClient.available()) {
                com.myriad.client.gui.TeamSignalWheel.open();
                return;
            }
            if (event.matches(KEY_DEATH_RECAP)) {
                com.myriad.client.DeathRecapStore.open();
                return;
            }
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
            Packets.sendToServer(new NetworkSetup.RecallPayload());
            return;
        }
        // This is a PRESS event, not a client tick: consumeClick can consume a stale
        // queued press on an unrelated later key (e.g. moving after reaching 30 energy).
        for (KeyMapping key : new KeyMapping[]{KEY_SKILL_Q, KEY_SKILL_W, KEY_SKILL_E, KEY_SKILL_R, KEY_SKILL_F}) {
            while (key.consumeClick()) { }
        }
        if (event.matches(KEY_SKILL_Q)) {
            slot = CharacterType.SLOT_Q;
        } else if (event.matches(KEY_SKILL_W)) {
            slot = CharacterType.SLOT_W;
        } else if (event.matches(KEY_SKILL_E)) {
            slot = CharacterType.SLOT_E;
        } else if (event.matches(KEY_SKILL_R)) {
            slot = CharacterType.SLOT_R;
        } else if (event.matches(KEY_SKILL_F)) {
            slot = CharacterType.SLOT_F;
        }
        if (slot >= 0) {
            Minecraft mc = Minecraft.getInstance();
            // 回风步默认也绑定 W；若技能键与前进键相同，不能把这次施法按键误当成移动输入。
            boolean castKeyIsForward = slot == CharacterType.SLOT_W
                    && net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(KEY_SKILL_W)
                            .equals(net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(mc.options.keyUp));
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
            com.myriad.client.firstperson.WindBladeView.prepare(slot);
            com.myriad.client.firstperson.InkFlagView.prepareSkill(slot);
            Packets.sendToServer(new NetworkSetup.CastSkillPayload(slot, moveX, moveZ));
        }
    }
}
