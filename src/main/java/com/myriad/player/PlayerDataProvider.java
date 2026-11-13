package com.myriad.player;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.myriad.Myriad;
import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.GameRules;
import com.myriad.game.SpiritFlow;
import com.myriad.network.NetworkSetup;
import com.myriad.registry.ModAttributes;

/**
 * 玩家数据 Attachment 注册与全部游戏事件（NeoForge 事件总线）。
 * 数据以 Attachment 形式附加在玩家实体上（getData 自动创建并缓存同一实例）。
 */
@EventBusSubscriber(modid = Myriad.MODID)
public final class PlayerDataProvider {
    private PlayerDataProvider() {}

    /** 玩家数据 Attachment（跟随实体，getData 首次自动创建并缓存） */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Myriad.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerData>> PLAYER_DATA =
            ATTACHMENTS.register("player_data", () -> AttachmentType.builder(() -> new PlayerData()).serialize(PlayerData.CODEC.fieldOf("state")).build());

    /** Incoming 阶段记录颜色/暴击，Post 阶段按真正生命损失发送飘字。队列兼容伤害事件内的嵌套伤害。 */
    private static final java.util.Map<UUID, java.util.ArrayDeque<PendingDamageFloat>> PENDING_DAMAGE_FLOATS =
            new java.util.HashMap<>();
    /** 同一死亡流程可能被嵌套伤害重复触发；短窗口内只结算一次 K/D、赏金和播报。 */
    private static final java.util.Map<UUID, Long> LAST_PLAYER_DEATH_EVENT = new java.util.HashMap<>();

    private record PendingDamageFloat(UUID attacker, int type, boolean crit, boolean skill, String abilitySource, boolean direct) { }

    private static PendingDamageFloat pollDamageFloat(LivingEntity target) {
        java.util.ArrayDeque<PendingDamageFloat> queue = PENDING_DAMAGE_FLOATS.get(target.getUUID());
        if (queue == null) return null;
        PendingDamageFloat pending = queue.pollFirst();
        if (queue.isEmpty()) PENDING_DAMAGE_FLOATS.remove(target.getUUID());
        return pending;
    }

    private static void sendActualDamageFloat(LivingEntity target, PendingDamageFloat pending, float actualDamage) {
        if (pending == null || actualDamage <= 0 || !(target.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(pending.attacker());
        if (attacker == null) return;
        // Confirm the primary contact before on-hit passives can recursively deal secondary damage.
        com.myriad.combat.CombatImpactFeedback.confirmed(attacker,target,pending.type(),pending.crit(),
                pending.skill()&&!pending.direct(),actualDamage,false);
        if(!pending.skill())com.myriad.character.skills.yingxiao.rework.NightBasicVisuals.confirmed(attacker,target);
        if(target instanceof ServerPlayer victim && attacker!=victim && GameManager.get()!=null
                && GameManager.get().factionOf(attacker)!=null
                && GameManager.get().factionOf(attacker)!=GameManager.get().factionOf(victim)) {
            victim.getPersistentData().putString("myriad_last_hero",attacker.getUUID().toString());
            victim.getPersistentData().putLong("myriad_last_hero_at",level.getGameTime());
            com.myriad.game.LaneSystem.onHeroAttacked(victim, attacker);
        }
        com.myriad.combat.LifestealSystem.onActualDamage(attacker, target, actualDamage, pending.type(), pending.skill());
        com.myriad.character.ability.AbilityRuntime.hit(attacker, target, actualDamage, !pending.skill(),
                pending.abilitySource(), pending.direct(), pending.type());
        com.myriad.character.SkillPresentation.hit(attacker, target, pending.crit());
        if (GameManager.get() != null) GameManager.get().recordDamage(attacker, target, actualDamage);
        long hitNow = level.getGameTime();
        if (hitNow - attacker.getPersistentData().getLongOr("myriad_hit_feedback", -10) >= 2) {
            attacker.getPersistentData().putLong("myriad_hit_feedback", hitNow);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(attacker,
                    new com.myriad.network.UiFeedbackPayload(pending.crit() ? 6 : 5));
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(attacker,
                new NetworkSetup.DamageFloatPayload(pending.type(), Math.max(1, Math.round(actualDamage)), pending.crit(),
                        target.getX(), target.getY() + target.getBbHeight() * .8, target.getZ()));
    }

    /** 获取玩家数据（自动创建并缓存，同一实体始终同一实例） */
    public static PlayerData get(LivingEntity player) {
        return ((IAttachmentHolder) player).getData(PLAYER_DATA);
    }

    /** 将玩家数据与对局状态同步到客户端 */
    public static void syncToClient(ServerPlayer player) {
        PlayerData data = get(player);
        if (data == null) {
            Myriad.LOGGER.info("[sync] skip (no data): {}", player.getName().getString());
            return;
        }
        GameManager manager = GameManager.get();
        int xpToNext = LevelSystem.xpToNext(data.getLevel());
        // 属性快照（descriptionId -> 当前值，供客户端属性面板显示；模组属性不在 syncable 列表，需显式收集）
        java.util.Map<String, Double> attrs = new java.util.HashMap<>();
        java.util.List<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>> attrList =
                java.util.List.of(
                        net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                        net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                        com.myriad.registry.ModAttributes.PHYSICAL_ATTACK,
                        com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE,
                        com.myriad.registry.ModAttributes.MAGIC_RESISTANCE,
                        com.myriad.registry.ModAttributes.ATTACK_SPEED,
                        com.myriad.registry.ModAttributes.SPELL_POWER,
                        com.myriad.registry.ModAttributes.CRIT_CHANCE,
                        com.myriad.registry.ModAttributes.CRIT_DAMAGE,
                        com.myriad.registry.ModAttributes.PHYSICAL_LIFESTEAL,
                        com.myriad.registry.ModAttributes.TENACITY,
                        com.myriad.registry.ModAttributes.FLAT_ARMOR_PEN,
                        com.myriad.registry.ModAttributes.PERCENT_ARMOR_PEN,
                        com.myriad.registry.ModAttributes.FLAT_MAGIC_PEN,
                        com.myriad.registry.ModAttributes.PERCENT_MAGIC_PEN,
                        com.myriad.registry.ModAttributes.MAX_MANA,
                        com.myriad.registry.ModAttributes.MANA_REGEN,
                        com.myriad.registry.ModAttributes.COOLDOWN_REDUCTION,
                        com.myriad.registry.ModAttributes.HEALTH_REGEN,
                        com.myriad.registry.ModAttributes.SPELL_LIFESTEAL);
        for (net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder : attrList) {
            attrs.put(holder.value().getDescriptionId(), player.getAttributeValue(holder));
        }
        // 属性面板中的最大生命使用模组生命池，不能显示被原版截断后的 1024。
        attrs.put(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.value().getDescriptionId(),
                (double)data.getModMaxHealth());
        NetworkSetup.sendToPlayer(player, new NetworkSetup.PlayerDataPayload(
                data.getLevel(),
                data.getXp(),
                xpToNext,
                data.getGold(),
                (int) data.getMana(),
                data.getCarrying(),
                NetworkSetup.factionId(data.getFaction()),
                manager == null ? 0 : manager.getXuanxiaoScore(),
                manager == null ? 0 : manager.getYoumingScore(),
                manager == null ? 0 : NetworkSetup.phaseId(manager.getPhase()),
                data.getCharacterId() == null ? "" : data.getCharacterId(),
                NetworkSetup.cooldownOf(data, CharacterType.SLOT_Q),
                NetworkSetup.cooldownOf(data, CharacterType.SLOT_W),
                NetworkSetup.cooldownOf(data, CharacterType.SLOT_E),
                NetworkSetup.cooldownOf(data, CharacterType.SLOT_R),
                NetworkSetup.cooldownOf(data, CharacterType.SLOT_F),
                com.myriad.game.TrainingMode.isEnabled(),
                com.myriad.game.TrainingMode.isNoCooldown(),
                com.myriad.game.TrainingMode.isNoGoldCost(),
                com.myriad.game.TrainingMode.isNoMana(),
                data.getBuffs(),
                takenByOthers(player),
                data.getSkillPoints(),
                data.getPlacementType(),
                data.getSkillLevels(),
                data.isChanneling(),
                data.getChannelTicks(),
                com.myriad.game.ModState.isEnabled(),
                manager == null ? -1
                        : (int) manager.getRemainingTicks(player.level().getGameTime()),
                java.util.Arrays.asList(data.getEquipmentSlots()),
                attrs,
                data.getSwordQi(),
                data.getFlyingSwords(),
                com.myriad.combat.BasicAttackSystem.cooldownRemaining(player),
                com.myriad.combat.BasicAttackSystem.cooldownTotal(player),
                player.getHealth(),
                data.getModMaxHealth(),
                data.getModShield()));
        Myriad.LOGGER.debug("[sync] -> {}@{}: lv={} char={} faction={} phase={}",
                player.getName().getString(), System.identityHashCode(player), data.getLevel(), data.getCharacterId(),
                data.getFaction(), manager == null ? "-" : manager.getPhase());
    }

    /** 各角色是否已被"其他玩家"占用（选择职业界面展示用） */
    public static java.util.Map<String, Boolean> takenByOthers(ServerPlayer self) {
        java.util.Map<String, Boolean> taken = new java.util.HashMap<>();
        for (CharacterType type : CharacterType.values()) {
            taken.put(type.getId(), false);
        }
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) self.level();
        if (level.getServer() == null) {
            return taken;
        }
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p == self) {
                continue;
            }
            PlayerData d = get(p);
            if (d != null && d.getCharacterId() != null) {
                taken.put(d.getCharacterId(), true);
            }
        }
        return taken;
    }

    /** 选择角色（UI 与 /myriad char 命令共用）：每个角色仅一人可选，校验后设置并同步 */
    public static void chooseCharacter(ServerPlayer player, CharacterType type) {
        if (!com.myriad.game.ModState.isEnabled()) {
            player.sendSystemMessage(Component.translatable("message.myriad.mod_off"));
            return;
        }
        if (type == null) {
            player.sendSystemMessage(Component.translatable("message.myriad.invalid_character"));
            return;
        }
        PlayerData data = get(player);
        if (data == null) {
            return;
        }
        if (takenByOthers(player).getOrDefault(type.getId(), false)) {
            player.sendSystemMessage(Component.translatable("message.myriad.character_taken",
                    Component.translatable(type.nameKey())));
            return;
        }
        if (GameManager.get() != null && GameManager.get().getPhase() == GameManager.Phase.PICKING) {
            // 开局选人阶段：必须轮到该玩家且角色未被选（服务端统一轮次/唯一性校验）
            if (!GameManager.get().onPick(player, type)) {
                player.sendSystemMessage(Component.translatable("message.myriad.pick_not_your_turn"));
            }
            return;
        }

        ModShieldSystem.clearFor(player);
        if (com.myriad.game.TrainingMode.isEnabled()
                && !type.getId().equalsIgnoreCase(data.getCharacterId() == null ? "" : data.getCharacterId())) {
            data.refundAllSkillPointsForTraining();
        }
        com.myriad.character.ability.AbilityRuntime.clear(player);
        data.setCharacterId(type.getId());
        // 开局/切角即获得 1 个技能点（至少 1 点，不叠加）
        data.setSkillPoints(Math.max(1, data.getSkillPoints()));
        player.sendSystemMessage(Component.translatable("message.myriad.character_set",
                Component.translatable(type.nameKey())));
        applyChosenCharacter(player, data);
        syncToClient(player);
    }

    /** 选择角色后应用角色专属初始属性（并重算成长/装备），供选角流程复用 */
    public static void applyChosenCharacter(ServerPlayer player, PlayerData data) {
        CharacterType type = CharacterType.byId(data.getCharacterId());
        if (type == null || !com.myriad.game.ModState.isEnabled()) {
            return;
        }
        CharacterType.applyInitialAttributes(player, type);
        LevelSystem.applyLevelAttributes(player, data.getLevel());
        data.resetEquipApplied();
        com.myriad.player.EquipmentSystem.apply(player, data);
        PlayerHealthSystem.fill(player);
        if (com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId()))
            data.setMana((float)player.getAttributeValue(ModAttributes.MAX_MANA));
        applyWeaponLock(player, data);
    }

    /** 技能升级：普通技能与大招共用技能点（每级 +1）；
     * 小技能最高 4 级；大招在角色 4/8/12 级解锁升级，最高 3 级；
     * 单个技能等级不能超过其他技能等级之和（防止只加一个技能）。 */
    public static void upgradeSkill(ServerPlayer player, String skillId) {
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        PlayerData data = get(player);
        if (data == null || data.getCharacterId() == null) {
            return;
        }
        CharacterType type = CharacterType.byId(data.getCharacterId());
        if (type == null) {
            return;
        }
        if (com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())) {
            if (!com.myriad.game.TrainingMode.isEnabled()
                    && (GameManager.get() == null || GameManager.get().getPhase() != GameManager.Phase.PLAYING)) return;
            String error = com.myriad.character.rework.FirstGroupSkills.learnError(
                    data.getCharacterId(), data.getSkillLevels(), data.getSkillPoints(), data.getLevel(), skillId);
            if (error != null) { player.sendSystemMessage(Component.translatable(error)); return; }
            data.setSkillLevel(skillId, data.skillLevel(skillId) + 1);
            data.setSkillPoints(data.getSkillPoints() - 1);
            syncToClient(player);
            return;
        }
        Skill skill = null;
        for (int slot = 0; slot < CharacterType.SKILL_SLOTS; slot++) {
            Skill s = type.skill(slot);
            if (s != null && s.getId().equals(skillId)) {
                skill = s;
                break;
            }
        }
        if (skill == null) {
            return;
        }
        boolean ultimate = skill.isUltimate();
        int current = data.skillLevel(skillId);
        // 15 点技能点正好分配为 Q/W/E 各 4 级、R 3 级。
        int maxLevel = ultimate ? com.myriad.game.GameRules.ULTIMATE_MAX : 4;
        if (current >= maxLevel) {
            player.sendSystemMessage(Component.translatable("message.myriad.skill_max"));
            return;
        }
        // 大招：需达到对应门槛等级才能升下一级
        if (ultimate) {
            int[] gates = com.myriad.game.GameRules.ULTIMATE_GATES;
            int need = current + 1 < gates.length ? gates[current + 1] : Integer.MAX_VALUE;
            if (data.getLevel() < need) {
                player.sendSystemMessage(Component.translatable(
                        "message.myriad.ultimate_level_gate", need));
                return;
            }
        }
        // Q/W/E 三个小技能互相制约：当前技能等级达到另外两个小技能之和后，
        // 必须先给其它小技能加点。全零时允许投入本局第一个技能点；大招只受等级门槛限制。
        if (!ultimate) {
            int otherSmallSkillSum = 0;
            boolean smallSkill = false;
            for (int slot = CharacterType.SLOT_Q; slot <= CharacterType.SLOT_E; slot++) {
                Skill other = type.skill(slot);
                if (other == null) continue;
                if (other.getId().equals(skillId)) smallSkill = true;
                else otherSmallSkillSum += data.skillLevel(other.getId());
            }
            if (smallSkill && !(current == 0 && otherSmallSkillSum == 0)
                    && current >= otherSmallSkillSum) {
                player.sendSystemMessage(Component.translatable("message.myriad.skill_sum_limit"));
                return;
            }
        }
        if (data.getSkillPoints() <= 0) {
            player.sendSystemMessage(Component.translatable("message.myriad.no_skill_points"));
            return;
        }
        data.setSkillPoints(data.getSkillPoints() - 1);
        data.setSkillLevel(skillId, current + 1);
        player.sendSystemMessage(Component.translatable("message.myriad.skill_upgraded",
                Component.translatable(skill.nameKey()), current + 1));
        syncToClient(player);
    }

    /** 布阵：左键确认（1=墨灵护阵，2=传送阵，3=熔岩牢笼，4=绝对零度） */
    public static void confirmPlacement(ServerPlayer player) {
        PlayerData data = get(player);
        if (data == null || !com.myriad.game.ModState.isEnabled()) {
            return;
        }
        CharacterType type = CharacterType.byId(data.getCharacterId());
        if (data.getPlacementType() >= 100 && com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())) {
            com.myriad.character.ability.AbilityRuntime.confirm(player);
            return;
        }
        if (data.getPlacementType() == 1) {
            if (type != CharacterType.XUANCHEN) {
                return;
            }
            com.myriad.character.skills.xuanchen.MoYangHuZhenSkill.confirm(player, data);
        } else if (data.getPlacementType() == 2) {
            if (type != CharacterType.XUANCHEN) {
                return;
            }
            com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.confirm(player, data);
        } else if (data.getPlacementType() == 3) {
            if (type != CharacterType.FENJUE) {
                return;
            }
            com.myriad.character.skills.fenjue.RongYanLaoLongSkill.confirm(player, data);
        } else if (data.getPlacementType() == com.myriad.character.skills.hanze.HanzeSkills.ABSOLUTE_ZERO_PLACEMENT) {
            if (type != CharacterType.HANZE) {
                return;
            }
            com.myriad.character.skills.hanze.HanzeSkills.confirm(player, data);
        } else if (data.getPlacementType() == com.myriad.character.skills.qingluo.QingLuoSkills.HUXIN_PLACEMENT
                && type == CharacterType.QINGLUO) {
            com.myriad.character.skills.qingluo.QingLuoSkills.confirmW(player, data);
        } else if (data.getPlacementType() == com.myriad.character.skills.chixiao.ChiXiaoSkills.POJUN_PLACEMENT
                && type == CharacterType.CHIXIAO) {
            com.myriad.character.skills.chixiao.ChiXiaoSkills.confirmR(player, data);
        } else if ((data.getPlacementType() == com.myriad.character.skills.yunsheng.YunShengSkills.BEACON_PLACEMENT
                || data.getPlacementType() == com.myriad.character.skills.yunsheng.YunShengSkills.CHORUS_PLACEMENT)
                && type == CharacterType.YUNSHENG) {
            com.myriad.character.skills.yunsheng.YunShengSkills.confirmPlacement(player, data);
        }
    }

    /** 布阵：右键取消（服务端按布阵类型分派） */
    public static void cancelPlacement(ServerPlayer player) {
        if (get(player).getPlacementType() >= 100 && com.myriad.character.rework.FirstGroupSkills.active(get(player).getCharacterId())) {
            com.myriad.character.ability.AbilityRuntime.cancel(player);
            return;
        }
        PlayerData data = get(player);
        if (data == null || !com.myriad.game.ModState.isEnabled()) {
            return;
        }
        CharacterType type = CharacterType.byId(data.getCharacterId());
        if (data.getPlacementType() == 1) {
            if (type != CharacterType.XUANCHEN) {
                return;
            }
            com.myriad.character.skills.xuanchen.MoYangHuZhenSkill.cancel(player, data);
        } else if (data.getPlacementType() == 2) {
            if (type != CharacterType.XUANCHEN) {
                return;
            }
            com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.cancel(player, data);
        } else if (data.getPlacementType() == 3) {
            if (type != CharacterType.FENJUE) {
                return;
            }
            com.myriad.character.skills.fenjue.RongYanLaoLongSkill.cancel(player, data);
        } else if (data.getPlacementType() == com.myriad.character.skills.hanze.HanzeSkills.ABSOLUTE_ZERO_PLACEMENT) {
            if (type != CharacterType.HANZE) {
                return;
            }
            com.myriad.character.skills.hanze.HanzeSkills.cancel(player, data);
        } else if (data.getPlacementType() == com.myriad.character.skills.qingluo.QingLuoSkills.HUXIN_PLACEMENT
                && type == CharacterType.QINGLUO) {
            com.myriad.character.skills.qingluo.QingLuoSkills.cancelW(player, data);
        } else if (data.getPlacementType() == com.myriad.character.skills.chixiao.ChiXiaoSkills.POJUN_PLACEMENT
                && type == CharacterType.CHIXIAO) {
            com.myriad.character.skills.chixiao.ChiXiaoSkills.cancelR(player, data);
        } else if ((data.getPlacementType() == com.myriad.character.skills.yunsheng.YunShengSkills.BEACON_PLACEMENT
                || data.getPlacementType() == com.myriad.character.skills.yunsheng.YunShengSkills.CHORUS_PLACEMENT)
                && type == CharacterType.YUNSHENG) {
            com.myriad.character.skills.yunsheng.YunShengSkills.cancelPlacement(player, data);
        }
    }

    /** Credit a tower/minion last hit to the last enemy hero that dealt real damage within ten seconds. */
    private static net.minecraft.world.entity.Entity creditedKiller(ServerPlayer victim, net.minecraft.world.entity.Entity source) {
        if (!(source instanceof com.myriad.game.LaneMinionEntity)
                && !(source instanceof com.myriad.game.DefenseTowerEntity)) return source;
        long age=victim.level().getGameTime()-victim.getPersistentData().getLongOr("myriad_last_hero_at",-1000);
        if(age<0||age>200)return source;
        try {
            var p=victim.level().getServer().getPlayerList().getPlayer(UUID.fromString(victim.getPersistentData().getStringOr("myriad_last_hero","")));
            var m=GameManager.get();
            return p!=null&&p.level()==victim.level()&&m!=null&&m.factionOf(p)!=null
                    &&m.factionOf(p)!=m.factionOf(victim)?p:source;
        } catch(IllegalArgumentException ignored){return source;}
    }

    // ---- 对局生命周期 ----

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        SESSION_PLAYERS.clear();
        GameManager.create();
        com.myriad.game.MatchArena.initialize(event.getServer());
        GameManager.get().setServer(event.getServer());
        com.myriad.game.MonsterManager.initialize(event.getServer());
        com.myriad.game.LaneSystem.initialize(event.getServer());
        com.myriad.game.VisionSystem.initialize(event.getServer());
        com.myriad.game.MapSystem.initialize(event.getServer());
        com.myriad.game.MatchProgress.load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        com.myriad.game.MatchProgress.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        com.myriad.character.ability.AbilityRuntime.clearAll();
        com.myriad.game.VanillaSession.clear();
        com.myriad.game.BaseRules.clear();
        SESSION_PLAYERS.clear();
        PENDING_DAMAGE_FLOATS.clear();
        LAST_PLAYER_DEATH_EVENT.clear();
        com.myriad.combat.KnockbackBuffer.clearAll();
        com.myriad.game.MapSystem.shutdown();
        com.myriad.game.VisionSystem.shutdown();
        com.myriad.game.LaneSystem.shutdown();
        com.myriad.game.MonsterManager.shutdown();
        com.myriad.game.MatchArena.shutdown();
        GameManager.destroy();
        com.myriad.game.HeroRespawnSystem.clear();
        com.myriad.game.ModState.setEnabled(false);
        com.myriad.game.TrainingMode.setEnabled(false);
        com.myriad.game.TrainingMode.setNoCooldown(false);
        com.myriad.game.TrainingMode.setNoGoldCost(false);
        com.myriad.game.TrainingMode.setNoMana(false);
    }

    /** 服务端每 tick：对局状态、选人轮次与野怪重生。 */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        com.myriad.character.ability.AbilityRuntime.cleanup();
        if (event.getServer().overworld().getGameTime() % 20 == 0) com.myriad.game.MatchTeams.sync(event.getServer());
        var distances = event.getServer().getPlayerList();
        if (com.myriad.game.ModState.isEnabled()) {
            if (distances.getViewDistance() != 10) distances.setViewDistance(10);
            if (distances.getSimulationDistance() != 8) distances.setSimulationDistance(8);
        }
        for(ServerPlayer player:event.getServer().getPlayerList().getPlayers())com.myriad.game.BaseRules.tick(player);
        GameManager manager = GameManager.get();
        if (manager != null && com.myriad.game.ModState.isEnabled()) {
            com.myriad.network.MatchTabPayload.sync(event.getServer());
            manager.tick(event.getServer(), event.getServer().overworld().getGameTime());
            manager.checkTimeout(event.getServer().overworld().getGameTime());
            com.myriad.game.MonsterManager.tick(event.getServer().overworld());
            com.myriad.game.LaneSystem.tick(event.getServer().overworld());
            com.myriad.game.MapSystem.tick(event.getServer().overworld());
            com.myriad.game.TacticalSupplySystem.tick(event.getServer().overworld());
        }
        // 即使模组刚被关闭也要继续发送一次“关闭迷雾”，避免客户端保留旧可见列表。
        if (manager != null) com.myriad.game.VisionSystem.tick(event.getServer().overworld());
        if (event.getServer().overworld().getGameTime() % 200 == 0) com.myriad.game.MatchProgress.save(event.getServer());
    }

    // ---- Tab 列表：阵营前缀 + K/D + 当局进行时间 ----

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        // A null custom name lets vanilla apply the current scoreboard team's color/prefix.
        if (!com.myriad.game.ModState.isEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerData data = get(player);
        Faction faction = data == null ? null : data.getFaction();
        int color;
        if (faction == Faction.XUANXIAO) {
            color = 0xFFFF5555;
        } else if (faction == Faction.YOUMING) {
            color = 0xFF55AAFF;
        } else {
            color = 0xFFFFFFFF;
        }
        net.minecraft.network.chat.MutableComponent display = Component.empty();
        if (faction != null) {
            display.append(Component.literal("[").withStyle(
                            net.minecraft.network.chat.Style.EMPTY.withColor(color)))
                    .append(faction.displayName().copy().withStyle(
                            net.minecraft.network.chat.Style.EMPTY.withColor(color)))
                    .append(Component.literal("] ").withStyle(
                            net.minecraft.network.chat.Style.EMPTY.withColor(color)));
        }
        display.append(Component.literal(player.getName().getString()).withStyle(
                net.minecraft.ChatFormatting.WHITE));
        GameManager manager = GameManager.get();
        if (manager != null && (manager.getPhase() == GameManager.Phase.PLAYING
                || manager.getPhase() == GameManager.Phase.ENDED)) {
            display.append(Component.literal("  " + manager.killsOf(player) + "/" + manager.deathsOf(player))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        event.setDisplayName(display);
    }

    // ---- 玩家加入/离开 ----

    private static final java.util.Set<UUID> SESSION_PLAYERS = new java.util.HashSet<>();

    /** Reset every participant once per hosted world session, including late joiners. */
    public static void resetForHostSession(ServerPlayer player) {
        player.getPersistentData().remove("myriad_deaths_without_kill");
        player.getPersistentData().remove("myriad_last_hero");
        player.getPersistentData().remove("myriad_last_hero_at");

        player.getPersistentData().remove("myriad_hawk_ready");
        player.getPersistentData().remove("myriad_chill_until");
        player.getPersistentData().remove("myriad_corrode_until");
        player.getPersistentData().remove("myriad_fear_until");
        player.setData(PLAYER_DATA, new PlayerData());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        player.removeAllEffects();
        player.setAbsorptionAmount(0);
        player.clearFire();
        player.resetFallDistance();
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.experienceLevel = 0;
        player.totalExperience = 0;
        player.experienceProgress = 0;
        CharacterType.applyVanillaAttributes(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5);
        player.refreshTabListName();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Myriad.LOGGER.info("[login] PlayerLoggedInEvent fired for {}",
                event.getEntity().getName().getString());
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_PLAYER_DEATH_EVENT.remove(player.getUUID());
            RetiredRuneCleanup.clean(player);
            if (!com.myriad.game.ModState.isEnabled()) {
                com.myriad.game.VanillaSession.restore(player);
                com.myriad.game.FullGameReset.onLogin(player);
                if (GameManager.get() != null) GameManager.get().onPlayerJoin(player);
                syncToClient(player);
                return;
            }
            com.myriad.game.VanillaSession.capture(player);
            com.myriad.game.FullGameReset.onLogin(player);
            if (!player.level().getServer().isDedicatedServer() && SESSION_PLAYERS.add(player.getUUID())
                    && (GameManager.get() == null || GameManager.get().getPhase() == GameManager.Phase.WAITING)) {
                resetForHostSession(player);
            }
            GameManager manager = GameManager.get();
            if (manager != null) {
                manager.onPlayerJoin(player);
            }
            PlayerData data = get(player);
            if (data != null) {
                data.ensureLevelRewards(data.getLevel());
            }
            if (com.myriad.game.ModState.isEnabled()) {
                CharacterType.applyInitialAttributes(player,
                        CharacterType.byId(data == null ? null : data.getCharacterId()));
            } else {
                // 模组关闭：重置为原版基础属性
                CharacterType.applyVanillaAttributes(player);
            }
            // 先重置基础属性（会清 modifier），再应用角色成长 + 装备加成
            if (data != null) {
                LevelSystem.applyLevelAttributes(player, data.getLevel());
                data.resetEquipApplied();
                com.myriad.player.EquipmentSystem.apply(player, data);
                RetiredRuneCleanup.clean(player);
                applyWeaponLock(player, data);
            }
            syncToClient(player);
        }
    }

    /** 四只大型野怪的阵营共享增益；属性修饰器在到期时自动移除。 */
    private static void applyBossBuffs(ServerPlayer player, PlayerData data) {
        long now = player.level().getGameTime();
        applyTimedModifier(player, ModAttributes.PHYSICAL_ATTACK, "myriad:buff_azure_horn_attack",
                0.15, data.getAzureHornUntil() > now);
        applyTimedModifier(player, ModAttributes.SPELL_POWER, "myriad:buff_vermilion_flame_spell",
                0.15, data.getVermilionFlameUntil() > now);
        applyTimedModifier(player, Attributes.MOVEMENT_SPEED, "myriad:buff_gale_roc_speed",
                0.15, data.getGaleRocUntil() > now);
        applyTimedModifier(player, Attributes.MOVEMENT_SPEED, "myriad:buff_gold_wind_speed",
                0.10, data.getGoldWindUntil() > now);
        applyTimedModifier(player, Attributes.ATTACK_SPEED, "myriad:buff_gale_roc_attack_speed",
                0.20, data.getGaleRocUntil() > now);
        applyTimedFlatModifier(player, ModAttributes.MANA_REGEN, "myriad:buff_spring_well_mana_regen",
                8.0, data.getSpringWellUntil() > now);
        if (data.getAzureHornUntil() > now) data.setBuff("azure_horn", (int) (data.getAzureHornUntil() - now)); else data.setBuff("azure_horn", 0);
        if (data.getVermilionFlameUntil() > now) data.setBuff("vermilion_flame", (int) (data.getVermilionFlameUntil() - now)); else data.setBuff("vermilion_flame", 0);
        if (data.getGaleRocUntil() > now) data.setBuff("gale_roc", (int) (data.getGaleRocUntil() - now)); else data.setBuff("gale_roc", 0);
        if (data.getGoldWindUntil() > now) data.setBuff("gold_wind", (int) (data.getGoldWindUntil() - now)); else data.setBuff("gold_wind", 0);
        if (data.getSpringWellUntil() > now) data.setBuff("spring_well", (int) (data.getSpringWellUntil() - now)); else data.setBuff("spring_well", 0);
        if (data.getObsidianTortoiseUntil() > now) {
            data.setBuff("obsidian_tortoise", (int) (data.getObsidianTortoiseUntil() - now));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.RESISTANCE, 25, 0, false, false, true));
        } else data.setBuff("obsidian_tortoise", 0);
    }

    private static void applyTimedModifier(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            String idText, double amount, boolean active) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        Identifier id = Identifier.parse(idText);
        if (active && !instance.hasModifier(id)) {
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!active && instance.hasModifier(id)) {
            instance.removeModifier(id);
        }
    }

    private static void applyTimedFlatModifier(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            String idText, double amount, boolean active) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        Identifier id = Identifier.parse(idText);
        if (active && !instance.hasModifier(id)) {
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        } else if (!active && instance.hasModifier(id)) {
            instance.removeModifier(id);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.myriad.character.ability.AbilityRuntime.clear(player);
            LAST_PLAYER_DEATH_EVENT.remove(player.getUUID());
            com.myriad.game.BaseRules.logout(player);
            com.myriad.combat.KnockbackBuffer.clear(player.getUUID());
            com.myriad.game.TacticalSupplySystem.clearRegeneration(player.getUUID());
            com.myriad.character.skills.yingxiao.YingShaSkill.clearFor(player.getUUID());
            com.myriad.character.skills.tianxing.LeiJieZhanSkill.clearFor(player.getUUID());
            com.myriad.character.skills.xuanchen.LingFuSkill.clearFor(player.getUUID());
            com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.clearFor(player.getUUID());
            com.myriad.combat.MagicBullet.clearFor(player.getUUID());
            com.myriad.combat.BasicAttackSystem.clearFor(player.getUUID());
            com.myriad.character.skills.xuanchen.MoYangHuZhenSkill.clearFor(player);
            com.myriad.character.skills.xuanchen.ZhouTianFuZhenSkill.clearFor(player);
            com.myriad.character.skills.fenjue.LieYanZhanSkill.clearFor(player.getUUID());
            com.myriad.character.skills.fenjue.RongYanLaoLongSkill.clearFor(player);
            com.myriad.character.skills.fenjue.FenTianHuoHaiSkill.clearFor(player.getUUID());
            com.myriad.character.skills.fenjue.FenJueBaoFaSkill.clearFor(player.getUUID());
            com.myriad.character.skills.fenjue.FenJuePassiveSkill.clearFor(player.getUUID());
            com.myriad.character.skills.sulan.SuLanPassiveSkill.clearFor(player.getUUID());
            com.myriad.character.skills.sulan.JiFengTuCiSkill.clearFor(player.getUUID());
            com.myriad.character.skills.sulan.LingFengBiaoJiSkill.clearFor(player.getUUID());
            com.myriad.combat.WindWallSystem.clearFor(player.getUUID());
            com.myriad.combat.SilenceSystem.clearFor(player.getUUID());
            com.myriad.combat.FrozenControlSystem.clearFor(player.getUUID());
            com.myriad.character.skills.sulan.YuFengQianLiSkill.clearFor(player.getUUID());
            com.myriad.character.skills.hanze.HanzeSkills.clearFor(player.getUUID());
            com.myriad.character.skills.qingluo.QingLuoSkills.clearFor(player.getUUID());
            com.myriad.character.skills.chixiao.ChiXiaoSkills.clearFor(player.getUUID());
            com.myriad.character.skills.yunsheng.YunShengSkills.clearFor(player);

            ModShieldSystem.clearFor(player.getUUID());
            if (GameManager.get() != null) {
                GameManager.get().onPlayerQuit(player);
            }
        }
    }

    /** 模组重新开启前清除在线玩家的临时技能状态；实体本身由 ModEntityTracker 统一回收。 */
    public static void clearModGeneratedState(net.minecraft.server.MinecraftServer server) {
        com.myriad.character.ability.AbilityRuntime.clearAll();
        com.myriad.game.RecallSystem.clear();

        PENDING_DAMAGE_FLOATS.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            com.myriad.character.skills.yingxiao.YingShaSkill.clearFor(id);
            com.myriad.character.skills.tianxing.LeiJieZhanSkill.clearFor(id);
            com.myriad.character.skills.xuanchen.LingFuSkill.clearFor(id);
            com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.clearFor(id);
            com.myriad.combat.MagicBullet.clearFor(id);
            com.myriad.combat.BasicAttackSystem.clearFor(id);
            com.myriad.character.skills.xuanchen.MoYangHuZhenSkill.clearFor(player);
            com.myriad.character.skills.xuanchen.ZhouTianFuZhenSkill.clearFor(player);
            com.myriad.character.skills.fenjue.LieYanZhanSkill.clearFor(id);
            com.myriad.character.skills.fenjue.RongYanLaoLongSkill.clearFor(player);
            com.myriad.character.skills.fenjue.FenTianHuoHaiSkill.clearFor(id);
            com.myriad.character.skills.fenjue.FenJueBaoFaSkill.clearFor(id);
            com.myriad.character.skills.fenjue.FenJuePassiveSkill.clearFor(id);
            com.myriad.character.skills.sulan.SuLanPassiveSkill.clearFor(id);
            com.myriad.character.skills.sulan.JiFengTuCiSkill.clearFor(id);
            com.myriad.character.skills.sulan.LingFengBiaoJiSkill.clearFor(id);
            com.myriad.combat.WindWallSystem.clearFor(id);
            com.myriad.combat.SilenceSystem.clearFor(id);
            com.myriad.combat.FrozenControlSystem.clearFor(id);
            com.myriad.character.skills.sulan.YuFengQianLiSkill.clearFor(id);
            com.myriad.character.skills.hanze.HanzeSkills.clearFor(id);
            com.myriad.character.skills.lingyuan.LingYuanSkills.clearFor(id);
            com.myriad.character.skills.qingluo.QingLuoSkills.clearFor(id);
            com.myriad.character.skills.chixiao.ChiXiaoSkills.clearFor(id);
            com.myriad.character.skills.yunsheng.YunShengSkills.clearFor(player);

            ModShieldSystem.clearFor(player);
        }
        ModShieldSystem.clearAll();
        com.myriad.game.MonsterManager.clearGenerated(server);
        com.myriad.game.JiarenManager.clearGenerated(server);
        com.myriad.game.TacticalSupplySystem.clear();
    }

    /** 玩家重生（实体克隆）时复制全部数据（装备/技能等级/冷却等），并重建属性加成 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player newPlayer = event.getEntity();
        PlayerData oldData = get(original);
        PlayerData newData = get(newPlayer);
        if (oldData == null || newData == null) {
            return;
        }
        newData.copyStateFrom(oldData);
        Myriad.LOGGER.info("[clone] {} data copied (faction={}, char={}, equips={})",
                newPlayer.getName().getString(), oldData.getFaction(), oldData.getCharacterId(),
                oldData.getEquipmentSlots().length);
        if (newPlayer instanceof ServerPlayer serverPlayer) {
            if (com.myriad.game.ModState.isEnabled() && newData.getCharacterId() != null) {
                CharacterType.applyInitialAttributes(serverPlayer,
                        CharacterType.byId(newData.getCharacterId()));
                LevelSystem.applyLevelAttributes(serverPlayer, newData.getLevel());
                newData.resetEquipApplied();
                com.myriad.player.EquipmentSystem.apply(serverPlayer, newData);
                RetiredRuneCleanup.clean(serverPlayer);
                PlayerHealthSystem.fill(serverPlayer);
                applyWeaponLock(serverPlayer, newData);
            } else {
                CharacterType.applyVanillaAttributes(serverPlayer);
            }
            syncToClient(serverPlayer);
            serverPlayer.refreshTabListName();
        }
    }

    // ---- 每 tick 逻辑：法力回复 + 技能冷却 + 脱战被动 + 灵源汲取/携带 ----

    /** 影刹脱战移速加成的修饰符 id */
    private static final net.minecraft.resources.Identifier OUT_OF_COMBAT_SPEED =
            Identifier.fromNamespaceAndPath(Myriad.MODID, "yingxiao_out_of_combat_speed");
    /** 脱离战斗 5 秒判定 */
    private static final long OUT_OF_COMBAT_TICKS = 100;
    /** “生命恢复”属性在战斗状态下仅保留10%，技能治疗、吸血与泉水不走该倍率。 */
    private static final double IN_COMBAT_HEALTH_REGEN_MULTIPLIER = 0.10;

    /**
     * 将内部的每秒生命回复换算为整数脉冲。正常情况固定每5秒结算并向下取整；
     * 5秒总量不足1时改为每 ceil(1/每秒回复) 秒恢复1点，杜绝小数生命回复。
     */
    public static int healthRegenPulseAmount(double healthPerSecond) {
        if (!Double.isFinite(healthPerSecond) || healthPerSecond <= 0) return 0;
        double perFiveSeconds = healthPerSecond * 5.0;
        return perFiveSeconds >= 1.0 ? Math.max(1, (int)Math.floor(perFiveSeconds)) : 1;
    }

    public static int healthRegenPulseTicks(double healthPerSecond) {
        if (!Double.isFinite(healthPerSecond) || healthPerSecond <= 0) return Integer.MAX_VALUE;
        if (healthPerSecond * 5.0 >= 1.0) return 5 * 20;
        long seconds = Math.max(1L, (long)Math.ceil(1.0 / healthPerSecond));
        return seconds >= Integer.MAX_VALUE / 20L ? Integer.MAX_VALUE : (int)(seconds * 20L);
    }

    private static boolean isInCombat(ServerPlayer player, PlayerData data) {
        return data.getLastCombatGameTime() >= 0
                && player.level().getGameTime() - data.getLastCombatGameTime() <= OUT_OF_COMBAT_TICKS;
    }

    /** 每个角色的初始武器（无法破坏）：物品栏第一格锁定，无法移动/拖动
     *  墨尘/寒泽=木棒法器，其他角色使用对应的近战初始武器。 */
    private static void applyWeaponLock(ServerPlayer player, PlayerData data) {
        if (data.getCharacterId() == null || !com.myriad.game.ModState.isEnabled()) {
            return;
        }
        net.minecraft.world.item.ItemStack weapon = initialWeapon(data);
        net.minecraft.world.item.ItemStack offhand = player.getOffhandItem();
        if (isInitialWeapon(offhand)) {
            net.minecraft.world.item.ItemStack recovered = offhand.copy();
            net.minecraft.world.item.ItemStack displaced = player.getInventory().getItem(0).copy();
            player.getInventory().setItem(0, recovered);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    isInitialWeapon(displaced) ? net.minecraft.world.item.ItemStack.EMPTY : displaced);
        }
        net.minecraft.world.item.ItemStack slot0 = player.getInventory().getItem(0);
        if (!slot0.is(weapon.getItem())) {
            // 替换品放回背包；背包满则掉落在地（可见，不消失）
            if (!slot0.isEmpty()) {
                if (!player.getInventory().add(slot0)) {
                    player.drop(slot0, false);
                }
            }
            player.getInventory().setItem(0, weapon);
        } else {
            // 已是初始武器：仅确保名字/不可破坏正确（用户拖到别处也会带名字）
            slot0.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    initialWeaponName(data.getCharacterId()));
            slot0.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
            slot0.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        }
        ensureTacticalMap(player);
    }

    /** 宗门战图是对局基础工具：可自由换手，但丢失后会自动补回。 */
    private static void ensureTacticalMap(ServerPlayer player) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(com.myriad.registry.ModItems.TACTICAL_MAP.get())) return;
        }
        if (player.getOffhandItem().is(com.myriad.registry.ModItems.TACTICAL_MAP.get())) return;
        net.minecraft.world.item.ItemStack map = new net.minecraft.world.item.ItemStack(
                com.myriad.registry.ModItems.TACTICAL_MAP.get());
        if (inv.getItem(8).isEmpty()) inv.setItem(8, map);
        else if (!inv.add(map)) player.drop(map, false);
    }

    /** 专属初始武器模型，无法破坏；名字/颜色按角色区分。 */
    private static net.minecraft.world.item.ItemStack initialWeapon(PlayerData data) {
        String charId = data == null ? null : data.getCharacterId();
        net.minecraft.world.item.ItemStack weapon = new net.minecraft.world.item.ItemStack(
                com.myriad.registry.ModItems.initialWeaponItem(charId));
        // 初始剑仅保留外观；移除原版武器属性（尤其横扫伤害比），所有平砍伤害由模组公式接管。
        weapon.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        weapon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                initialWeaponName(charId));
        weapon.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        return weapon;
    }

    /** 初始武器名字与颜色（按角色区分：墨尘=墨杖黑色，夜影=影刺金色，天刑=雷霆剑蓝色） */
    private static net.minecraft.network.chat.Component initialWeaponName(String characterId) {

        if ("xuanchen".equalsIgnoreCase(characterId)) {
            return Component.translatable("item.myriad.zhenqi")
                    .withStyle(net.minecraft.ChatFormatting.BLACK)
                    .withStyle(net.minecraft.ChatFormatting.BOLD);
        }
        if ("hanze".equalsIgnoreCase(characterId)) {
            return Component.translatable("item.myriad.shuangbing")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF8FE8FF));
        }
        if ("tianxing".equalsIgnoreCase(characterId)) {
            return Component.translatable("item.myriad.leitingjian")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF55AAFF));
        }
        if ("fenjue".equalsIgnoreCase(characterId)) {
            return Component.translatable("item.myriad.lieyanjian")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFF7F27));
        }
        if ("sulan".equalsIgnoreCase(characterId)) {
            return Component.translatable("item.myriad.fengren")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF55F0B0));
        }
        if ("lingyuan".equalsIgnoreCase(characterId)) {
            return Component.translatable("item.myriad.durandal")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFBFE8FF));
        }
        if ("qingluo".equalsIgnoreCase(characterId)) {
            return Component.translatable("item.myriad.qingluo_staff")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF7FE6A1));
        }
        if ("chixiao".equalsIgnoreCase(characterId)) {
            return Component.translatable("item.myriad.chixiao_blade")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFF5A38));
        }
        if ("yunsheng".equalsIgnoreCase(characterId)) {
            return Component.translatable("item.myriad.yunsheng_bell")
                    .withStyle(net.minecraft.ChatFormatting.BOLD)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF9EFFF2));
        }

        if ("shuo".equalsIgnoreCase(characterId)) return Component.translatable("item.myriad.shuo_sword").withStyle(net.minecraft.ChatFormatting.WHITE);
        return Component.translatable("item.myriad.yingci")
                .withStyle(net.minecraft.ChatFormatting.GOLD)
                .withStyle(net.minecraft.ChatFormatting.BOLD);
    }

    /** 开局重置初始物品：仅保留初始武器和不可消耗的宗门战图。 */
    public static void resetInitialInventory(ServerPlayer player) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 1; i < inv.getContainerSize(); i++) {
            inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
        inv.setItem(0, initialWeapon(get(player)));
        inv.setItem(8, new net.minecraft.world.item.ItemStack(
                com.myriad.registry.ModItems.TACTICAL_MAP.get()));
    }

    /** 锁定初始武器：禁止 Q 键/扔出（打开物品栏或热键均无法丢弃） */
    @SubscribeEvent
    public static void onItemToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = event.getEntity().getItem();
        if (stack.is(com.myriad.registry.ModItems.TACTICAL_MAP.get())) {
            event.setCanceled(true);
            event.getEntity().discard();
            net.minecraft.world.entity.player.Inventory inv = event.getPlayer().getInventory();
            boolean alreadyHasMap = event.getPlayer().getOffhandItem().is(
                    com.myriad.registry.ModItems.TACTICAL_MAP.get());
            for (int i = 0; i < inv.getContainerSize() && !alreadyHasMap; i++) {
                alreadyHasMap = inv.getItem(i).is(com.myriad.registry.ModItems.TACTICAL_MAP.get());
            }
            if (!alreadyHasMap) {
                if (inv.getItem(8).isEmpty()) inv.setItem(8, stack);
                else if (!inv.add(stack)) {
                    net.minecraft.world.item.ItemStack displaced = inv.getItem(8);
                    inv.setItem(8, stack);
                    event.getPlayer().drop(displaced, false);
                }
            }
            return;
        }
        if (!isInitialWeapon(stack)) {
            return;
        }
        event.setCanceled(true);
        event.getEntity().discard();
        net.minecraft.world.entity.player.Player player = event.getPlayer();
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        net.minecraft.world.item.ItemStack slot0 = inv.getItem(0);
        if (!slot0.isEmpty() && !isInitialWeapon(slot0)) {
            if (!inv.add(slot0)) {
                player.drop(slot0, false);
            }
        }
        inv.setItem(0, stack);
    }

    /** F 键交换前由服务端拦截：专属初始武器不可进入副手。 */
    @SubscribeEvent
    public static void onSwapHands(
            net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent.Hands event) {
        if (!com.myriad.game.ModState.isEnabled()
                || !(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (isInitialWeapon(event.getItemSwappedToOffHand())) {
            event.setCanceled(true);
        }
    }

    /** 是否为已注册的专属初始武器。 */
    public static boolean isInitialWeapon(net.minecraft.world.item.ItemStack stack) {
        return com.myriad.registry.ModItems.isInitialWeaponItem(stack)
                && stack.has(net.minecraft.core.component.DataComponents.UNBREAKABLE);
    }

    /** 玩家脚下生成一圈粒子光环（buff 显示用） */
    private static void spawnBuffRing(ServerPlayer player,
            net.minecraft.core.particles.ParticleOptions particle, int unusedColor) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        double x = player.getX();
        double y = player.getY() + 0.15;
        double z = player.getZ();
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2.0 * i / 24.0;
            level.sendParticles(particle, x + Math.cos(angle) * 1.3, y, z + Math.sin(angle) * 1.3,
                    1, 0, 0, 0, 0);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!com.myriad.game.ModState.isEnabled()) return;
        if (player.tickCount % 20 == 0) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SATURATION, -1, 0, false, false));
            LevelSystem.addXp(player, get(player), 0);
            com.myriad.game.MatchReminders.tick(player);
        }
        if (com.myriad.game.HeroRespawnSystem.tick(player)) {
            syncToClient(player);
            return;
        }
        PlayerData data = get(player);
        if (data == null) {
            return;
        }
        if (com.myriad.game.ModState.isEnabled()) ModShieldSystem.tick(player);
        // 法力回复
        if (player.tickCount % 20 == 0) {
            double regen = player.getAttributeValue(ModAttributes.MANA_REGEN);
            double max = player.getAttributeValue(ModAttributes.MAX_MANA);
            data.setMana((float) Math.min(max, data.getMana() + regen));
            // 灵石灌顶：脱战 +10 法力回复、+20% 冷却缩减（90 秒）
            long nowTick = player.level().getGameTime();
            if (nowTick < data.getSpiritRuneUntil()) {
                AttributeInstance cdr = player.getAttribute(ModAttributes.COOLDOWN_REDUCTION);
                if (cdr != null) {
                    net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse("myriad:buff_spirit_cdr");
                    if (!cdr.hasModifier(id)) {
                        cdr.addTransientModifier(new AttributeModifier(
                                id, 20.0, AttributeModifier.Operation.ADD_VALUE));
                    }
                }
                boolean inCombat2 = isInCombat(player, data);
                if (!inCombat2 && player.getHealth() > 0) {
                    double maxManaNow = player.getAttributeValue(ModAttributes.MAX_MANA);
                    data.setMana((float) Math.min(maxManaNow, data.getMana() + 10));
                }
                // 右侧 buff 显示（剩余秒）
                if (player.tickCount % 20 == 0) {
                    data.setBuff("spirit_rune", (int) (data.getSpiritRuneUntil() - nowTick));
                    // 脚下蓝色光环（粒子圈）
                    spawnBuffRing(player, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, 0xFF55AAFF);
                }
            } else {
                AttributeInstance cdr = player.getAttribute(ModAttributes.COOLDOWN_REDUCTION);
                if (cdr != null) {
                    cdr.removeModifier(net.minecraft.resources.Identifier.parse("myriad:buff_spirit_cdr"));
                }
                data.setBuff("spirit_rune", 0);
            }
        // 荒古灌顶：攻击减速（90 秒），右侧 buff 显示
        if (nowTick < data.getBeastRuneUntil()) {
            if (player.tickCount % 20 == 0) {
                data.setBuff("beast_rune", (int) (data.getBeastRuneUntil() - nowTick));
                // 脚下红色光环（粒子圈）
                spawnBuffRing(player, net.minecraft.core.particles.ParticleTypes.FLAME, 0xFFFF5555);
            }
        } else {
            data.setBuff("beast_rune", 0);
        }
        // 生命回复：以整数脉冲结算。脱战100%，战斗中10%；技能治疗、吸血和泉水不受影响。
        double hpRegen = player.getAttributeValue(ModAttributes.HEALTH_REGEN);
        if (isInCombat(player, data)) hpRegen *= IN_COMBAT_HEALTH_REGEN_MULTIPLIER;
        int regenInterval = healthRegenPulseTicks(hpRegen);
        if (hpRegen > 0 && regenInterval != Integer.MAX_VALUE && player.tickCount % regenInterval == 0
                && player.getHealth() < player.getMaxHealth()) {
            float before = player.getHealth();
            int pulse = healthRegenPulseAmount(hpRegen);
            com.myriad.combat.CombatTelemetry.heal(player,
                    Math.min(pulse, player.getMaxHealth() - before), "regeneration");
            int actual = Math.max(0, (int)Math.ceil(player.getHealth() - before));
            if (actual > 0) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new NetworkSetup.HealFloatPayload(actual));
        }
        }
        // 技能冷却递减
        applyBossBuffs(player, data);
        data.tickCooldowns();
        // 技能附加效果时长递减；本 tick 结束时统一同步 HUD。
        data.tickBuffs();
        // 清理旧版暗影猎手的脱战加速（包括热更新及切换角色后残留的修饰符）。
        AttributeInstance shadowSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (shadowSpeed != null && shadowSpeed.hasModifier(OUT_OF_COMBAT_SPEED)) {
            shadowSpeed.removeModifier(OUT_OF_COMBAT_SPEED);
        }
        // Tab 原版名称无需逐 tick 重建；自定义 HUD 数据在本方法末尾逐 tick 同步。
        if (player.tickCount % 20 == 0) {
            player.refreshTabListName();
        }
        // 初始武器锁定（每 tick 强制恢复，无法移动/丢弃）
        applyWeaponLock(player, data);
        // 原版攻击速度同步模组攻速（移除物品 modifier，实际攻击频率 = 模组攻速值）
        if (player.tickCount % 20 == 0) {
            AttributeInstance vanillaAs = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            if (vanillaAs != null) {
                for (net.minecraft.world.entity.ai.attributes.AttributeModifier m
                        : new java.util.ArrayList<>(vanillaAs.getModifiers())) {
                    vanillaAs.removeModifier(m.id());
                }
                vanillaAs.setBaseValue(player.getAttributeValue(ModAttributes.ATTACK_SPEED));
            }
        }

        // 装备特殊机制（疾风靴脱战移速）
        com.myriad.player.EquipmentEffects.tick(player);
        com.myriad.character.ability.AbilityRuntime.tick(player);
        com.myriad.combat.WindWallSystem.tick(player, data);
        com.myriad.combat.SilenceSystem.tick(player.level().getGameTime());
        com.myriad.combat.FrozenControlSystem.tick(player, player.level());
        if (!com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())) {
        // 影袭冲刺推进
        com.myriad.character.skills.yingxiao.YingXiSkill.tickDash(player, data);
        // 影杀飞刀推进
        com.myriad.character.skills.yingxiao.YingShaSkill.tickDagger(player, data);
        // 天刑技能推进（闪雷攻速buff / 雷劫斩蓄力 / 天雷护体脉冲 / 九天神雷落雷）
        com.myriad.character.skills.tianxing.ShanLeiSkill.tick(player, data);
        com.myriad.character.skills.tianxing.LeiJieZhanSkill.tick(player, data);
        com.myriad.character.skills.tianxing.TianLeiHuTiSkill.tick(player, data);
        com.myriad.character.skills.tianxing.JiuTianShenLeiSkill.tick(player, data);
        // 墨尘技能推进（灵缚锁链 / 传送阵 / 墨灵护阵布阵与阵法 / 周天覆阵）
        com.myriad.character.skills.xuanchen.LingFuSkill.tickChain(player, data);
        com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.tick(player, data);
        com.myriad.character.skills.xuanchen.MoYangHuZhenSkill.tick(player, data);
        com.myriad.character.skills.xuanchen.ZhouTianFuZhenSkill.tick(player, data);
        // 焚诀技能推进（横扫烈焰斩 / 灼烧 / 熔岩牢笼火墙与布阵 / 焚天火海 / 焚诀爆发蓄力 / 被动焚天战意）
        com.myriad.character.skills.fenjue.LieYanZhanSkill.tick(player, data);
        com.myriad.character.skills.fenjue.RongYanLaoLongSkill.tick(player, data);
        com.myriad.character.skills.fenjue.FenTianHuoHaiSkill.tick(player, data);
        com.myriad.character.skills.fenjue.FenJueBaoFaSkill.tick(player, data);
        com.myriad.character.skills.fenjue.FenJuePassiveSkill.tick(player, data);
        // 素岚技能推进（风行者被动 / 疾风突刺 / 灵风标记 / 风墙 / 御风千里）
        com.myriad.character.skills.sulan.SuLanPassiveSkill.tick(player, data);
        com.myriad.character.skills.sulan.JiFengTuCiSkill.tick(player, data);
        com.myriad.character.skills.sulan.LingFengBiaoJiSkill.tick(player, data);
        com.myriad.character.skills.sulan.YuFengQianLiSkill.tick(player, data);
        com.myriad.character.skills.lingyuan.LingYuanSkills.tick(player, data);
        com.myriad.character.skills.hanze.HanzeSkills.tick(player, data);
        com.myriad.character.skills.qingluo.QingLuoSkills.tick(player, data);
        com.myriad.character.skills.chixiao.ChiXiaoSkills.tick(player, data);
        com.myriad.character.skills.yunsheng.YunShengSkills.tick(player, data);
        }

        // 法师魔法弹推进
        com.myriad.combat.MagicBullet.tickBullets(player, data);
        SpiritFlow.tick(player);
        // 所有角色资源、技能窗口、冷却、生命、法力、金币与 HUD 计数均在逻辑推进后逐 tick 校正。
        syncToClient(player);
    }

    // ---- 传送阵交互（墨尘 W：队友右键传送） ----

    /** 右键隐形盔甲架 = 墨尘传送阵：传送至另一个传送阵或己方基地传送门 */
    @SubscribeEvent
    public static void onEntityInteract(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getTarget() instanceof LivingEntity living
                && com.myriad.game.JiarenManager.isJiaren(living.getUUID())) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new NetworkSetup.OpenJiarenSettingsPayload(living.getId(),
                            (float) com.myriad.game.JiarenManager.physicalDefense(living),
                            (float) com.myriad.game.JiarenManager.magicDefense(living)));
            event.setCanceled(true);
            return;
        }
        if (!(event.getTarget() instanceof net.minecraft.world.entity.decoration.ArmorStand stand)) {
            return;
        }
        com.myriad.character.skills.xuanchen.ChuanSongZhenSkill.onInteract(player, stand);
    }

    /** 法师手持初始武器右键：向前发射魔法弹（伤害 = 法术强度×0.5，射程 10 格，攻速决定发射间隔） */
    @SubscribeEvent
    public static void onRightClickItem(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        if (com.myriad.game.TacticalSupplySystem.tryUse(player, event.getItemStack())) {
            return;
        }
        if (player.isUsingItem()) {
            return;
        }
        com.myriad.combat.MagicBullet.tryFire(player, event.getItemStack());
    }

    /** 准星指向方块时仍允许侦灵图腾按选点规则放置，并阻止方块交互吞掉右键。 */
    @SubscribeEvent
    public static void onRightClickBlock(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !com.myriad.game.ModState.isEnabled()
                || !event.getItemStack().is(com.myriad.registry.ModItems.SCOUT_TOTEM.get())) {
            return;
        }
        if (com.myriad.game.TacticalSupplySystem.tryUse(player, event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    // ---- 伤害：打断汲取 + 影刹被动挂钩 ----

    // ---- 攻击/伤害事件 ----

    /** 近战允许原版点选攻击，远程仍禁止原版近战包绕过各自的弹体与冷却。 */
    @SubscribeEvent
    public static void onPlayerAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
                &&!com.myriad.combat.VanillaMeleeAttack.prepare(player,event.getTarget()))event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        float amountBeforeTargetMitigation = event.getAmount();
        PendingDamageFloat damageFloat = null;
        if (entity.level().isClientSide()) {
            return;
        }

        if (event.getAmount() > 0) {
            com.myriad.game.VisionSystem.revealCombatant(entity);
            com.myriad.game.VisionSystem.revealCombatant(event.getSource().getEntity());
        }
        // Second-batch legacy resonance is retired; new protection uses source-isolated mod shields.
        // 溺尸与幻翼仅作为野怪载体，不继承原版日照燃烧及其持续火焰伤害。
        if (com.myriad.game.MonsterManager.ignoresSunlightFire(
                com.myriad.game.MonsterManager.typeOf(entity))
                && (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)
                        || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE))) {
            event.getContainer().setNewDamage(0.0f);
            entity.clearFire();
            return;
        }
        // 记录双方战斗时间（暗影猎手脱战判定）
        long gameTime = entity.level().getGameTime();
        PlayerData victim = get(entity);
        if (victim != null && victim.getCharacterId() != null) {
            victim.setLastCombatGameTime(gameTime);
        }
        if (entity instanceof ServerPlayer victimPlayer) {
            // 青萝【万木回春】：首次低血量保护的一秒无敌优先于其他减伤结算。
            if (!com.myriad.character.rework.FirstGroupSkills.active(get(victimPlayer).getCharacterId())
                    && com.myriad.character.skills.qingluo.QingLuoSkills.interceptDamage(victimPlayer, event)) {
                return;
            }
            PlayerData qingLuoVictim = get(victimPlayer);
            if (qingLuoVictim != null && !com.myriad.character.rework.FirstGroupSkills.active(qingLuoVictim.getCharacterId()) && CharacterType.byId(qingLuoVictim.getCharacterId()) == CharacterType.QINGLUO) {
                com.myriad.character.skills.qingluo.QingLuoSkills.onQingLuoIncoming(victimPlayer, event);
            }
            if (qingLuoVictim != null && !com.myriad.character.rework.FirstGroupSkills.active(qingLuoVictim.getCharacterId()) && CharacterType.byId(qingLuoVictim.getCharacterId()) == CharacterType.CHIXIAO) {
                com.myriad.character.skills.chixiao.ChiXiaoSkills.onIncomingDamage(victimPlayer, event);
            }

            // 凌渊【化形为剑】：剑势期间格挡首次伤害并反制；放在所有减伤结算之前，保证本次完全免伤。
            PlayerData swordVictim = get(victimPlayer);
            if (swordVictim != null
                    && !com.myriad.character.rework.FirstGroupSkills.active(swordVictim.getCharacterId())
                    && CharacterType.byId(swordVictim.getCharacterId()) == CharacterType.LINGYUAN
                    && swordVictim.getBuff("huaxingweijian") > 0 && event.getAmount() > 0) {
                com.myriad.character.skills.lingyuan.LingYuanSkills.onSwordFormBlocked(
                        victimPlayer, event.getSource().getEntity());
                event.getContainer().setNewDamage(0.0F);
            }
            var incomingContext = com.myriad.combat.DamageCalc.peekSkillHit(entity);
            if(event.getAmount()>0&&com.myriad.character.skills.shuo.ShuoCombat.parry(victimPlayer,event.getSource(),incomingContext==null?null:incomingContext.kind()))
                event.getContainer().setNewDamage(0);
            event.getContainer().setNewDamage((float) (event.getAmount()
                    * com.myriad.character.ability.AbilityRuntime.incoming(victimPlayer,
                    incomingContext == null ? null : incomingContext.kind())));

            // 装备特殊机制：兽皮足物理减伤 6%
            com.myriad.player.EquipmentEffects.onIncomingDamage(victimPlayer, event);
            // 灵盾：伤害命中即触发破盾反馈（无延迟：与伤害同一时刻音效+金色特效）
            PlayerData victimData = get(victimPlayer);
            if (victimData != null && victimData.getBuff("lingdun") > 0
                    && event.getAmount() > 0 && victimData.getModShield() > 0) {
                if (victimPlayer.level() instanceof net.minecraft.server.level.ServerLevel sLevel) {
                    sLevel.playSound(null, victimPlayer.getX(), victimPlayer.getY(), victimPlayer.getZ(),
                            net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.0f);
                    sLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            victimPlayer.getX(), victimPlayer.getY() + 1.0, victimPlayer.getZ(),
                            16, 0.4, 0.8, 0.4, 0.08);
                }
            }
            // 天刑【天雷护体】：护体期间减伤 20%
            if (victimData != null && victimData.getBuff("tianleihuti") > 0 && event.getAmount() > 0) {
                event.getContainer().setNewDamage(event.getAmount() * 0.8f);
            }
            // 墨尘【墨灵护阵】/【周天覆阵】：友方阵内减伤 30% / 25%
            if (event.getAmount() > 0
                    && com.myriad.character.skills.xuanchen.MoYangHuZhenSkill.inAllyZone(victimPlayer)) {
                event.getContainer().setNewDamage(event.getAmount() * 0.7f);
            } else if (event.getAmount() > 0
                    && com.myriad.character.skills.xuanchen.ZhouTianFuZhenSkill.inAllyZone(victimPlayer)) {
                event.getContainer().setNewDamage(event.getAmount() * 0.75f);
            }
        }
        // 野怪受伤：按伤害量累计仇恨（仇恨 = 玩家造成的伤害）
        if (event.getSource().getEntity() instanceof LivingEntity attackerEntity
                && com.myriad.game.MonsterManager.isMonster(entity)) {
            com.myriad.game.MonsterManager.onAttacked(entity, attackerEntity, event.getAmount());
        }
        // 自定义普攻以 1 点占位伤害进入 Incoming，后续会重算完整物攻伤害。
        // 记录目标侧免伤对占位伤害形成的比例，避免完整伤害覆盖前面已经结算的角色与装备免伤。
        float targetMitigationScale = amountBeforeTargetMitigation > 0.0F
                ? Math.max(0.0F, event.getAmount() / amountBeforeTargetMitigation)
                : 0.0F;
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            com.myriad.combat.DamageCalc.SkillHitContext skillContext =
                    com.myriad.combat.DamageCalc.consumeSkillHit(entity);
            boolean skillHit = skillContext != null;
            float basicAttackMultiplier = com.myriad.combat.DamageCalc
                    .consumeBasicAttackMultiplier(attacker, entity);
            boolean customBasicAttack = basicAttackMultiplier > 0.0F;
            if (skillHit && !customBasicAttack && entity instanceof com.myriad.game.DefenseTowerEntity) {
                event.setCanceled(true);
                return;
            }
            boolean crit = skillContext != null && skillContext.crit();
            com.myriad.combat.DamageCalc.Kind skillKind = skillContext == null ? null : skillContext.kind();
            // 普通近战攻击：伤害 = 物攻 × 攻击强度 × (0.95~1.05 随机)；跳劈 +10% 伤害；模组暴击率触发暴击（×暴伤）
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) && !skillHit) {
                // 自定义左键普攻不使用 vanilla attackStrengthTicker；有效命中恒按 100% 物攻结算。
                boolean vanillaMelee=!customBasicAttack&&com.myriad.combat.VanillaMeleeAttack.usesVanilla(
                        CharacterType.byId(get(attacker).getCharacterId()));
                float strength = customBasicAttack ? 1.0f : vanillaMelee
                        ?com.myriad.combat.VanillaMeleeAttack.consumeStrength(attacker,entity)
                        :attacker.getAttackStrengthScale(0.5f);
                // 法师平砍：物攻 ≤ 10 正常全额伤害，> 10 后衰减为 10 + (物攻-10)×0.05
                float ad = (float) attacker.getAttributeValue(
                        com.myriad.registry.ModAttributes.PHYSICAL_ATTACK);
                PlayerData attackData = get(attacker);
                if (com.myriad.combat.MagicBullet.isMage(attacker) && ad > 10.0f
                        && !com.myriad.character.rework.FirstGroupSkills.active(attackData == null ? null : attackData.getCharacterId())) {
                    ad = 10.0f + (ad - 10.0f) * 0.05f;
                }
                float phys = ad * (vanillaMelee?com.myriad.combat.VanillaMeleeAttack.damageScale(strength):Math.max(0.2f,strength));
                float roll = 0.95f + attacker.getRandom().nextFloat() * 0.10f;
                // 跳劈（下落攻击）：伤害 +10%
                if (attacker.fallDistance > 0.0f) {
                    phys *= 1.10f;
                }
                // 模组暴击判定（角色当前暴击率）
                double chance = Math.max(0.0, Math.min(100.0,
                        attacker.getAttributeValue(com.myriad.registry.ModAttributes.CRIT_CHANCE)

                               ));
                if (crit || (chance > 0 && attacker.getRandom().nextDouble() * 100.0 < chance)) {
                    double critDmg = com.myriad.combat.DamageCalc.effectiveCritDamage(attacker);
                    phys *= (float) (critDmg / 100.0);
                    crit = true;
                }
                float finalBasic = (float) (phys * roll);

                finalBasic = com.myriad.combat.DamageCalc.applyMitigation(attacker, entity, finalBasic,
                        com.myriad.combat.DamageCalc.Kind.PHYSICAL);
                finalBasic = com.myriad.combat.DamageCalc.applyMonsterCritReduction(entity, finalBasic, crit);
                event.getContainer().setNewDamage(Math.max(1.0f, finalBasic) * targetMitigationScale);
                PlayerData basicAttackerData = get(attacker);

                // Preserve explicit ranged falloff/skill multipliers; weak-release scaling is retired.
                if (customBasicAttack && basicAttackMultiplier < 1.0F) {
                    event.getContainer().setNewDamage(event.getAmount() * basicAttackMultiplier);
                }
            }
            event.getContainer().setNewDamage((float) (event.getAmount()
                    * com.myriad.character.ability.AbilityRuntime.modifyDamage(attacker, entity,
                    event.getAmount(), customBasicAttack || !skillHit,
                    com.myriad.character.ability.AbilityRuntime.sourceSkill(),
                    com.myriad.character.ability.AbilityRuntime.sourceDirect(), skillKind)));
            // 荒古灌顶是野怪奖励，不属于已移除的符石系统。
            PlayerData beastData = get(attacker);
            if (event.getAmount() > 0 && com.myriad.character.ability.AbilityRuntime.sourceDirect()
                    && beastData != null && attacker.level().getGameTime() < beastData.getBeastRuneUntil()
                    && entity.isAlive() && !(entity instanceof com.myriad.game.DefenseTowerEntity)) {
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 0, false, false));
            }
            PlayerData attackerData = get(attacker);
            if (attackerData != null) {
                attackerData.setLastCombatGameTime(gameTime);
                if (CharacterType.byId(attackerData.getCharacterId()) == CharacterType.YINGXIAO
                        && !com.myriad.character.rework.FirstGroupSkills.active(attackerData.getCharacterId())) {
                    // 暗影猎手：普攻和技能背击统一增加30%，不额外生成伤害事件。
                    if (event.getAmount() > 0
                            && !(entity instanceof com.myriad.game.DefenseTowerEntity)
                            && !isAlly(attacker, entity) && isBehind(attacker, entity)) {
                        event.getContainer().setNewDamage(event.getAmount() * 1.30f);
                    }
                    // 保留化影命中破隐，但移除旧被动的150%破隐增伤。
                    if (attackerData.isStealthBonus() && attacker.hasEffect(MobEffects.INVISIBILITY)) {
                        attackerData.setStealthBonus(false);
                        attacker.removeEffect(MobEffects.INVISIBILITY);
                        attacker.removeEffect(MobEffects.SPEED);
                    }
                }
                // 天刑被动【雷殛之体】：保留雷电附伤和连锁溅射，每第三次普攻追加物攻百分比真实伤害。
                if (CharacterType.byId(attackerData.getCharacterId()) == CharacterType.TIANXING
                        && !com.myriad.character.rework.FirstGroupSkills.active(attackerData.getCharacterId())
                        && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                        && !skillHit && event.getAmount() > 0 && entity.isAlive()) {
                    float adNow = (float) attacker.getAttributeValue(
                            com.myriad.registry.ModAttributes.PHYSICAL_ATTACK);
                    float normalDamage = event.getAmount();
                    com.myriad.combat.DamageCalc.dealDamage(attacker, entity, 3.0F + .25F * adNow,
                            com.myriad.combat.DamageCalc.Kind.MAGIC);
                    net.minecraft.server.level.ServerLevel lightningLevel = attacker.level();
                    lightningLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            entity.getX(), entity.getY() + entity.getBbHeight() * .6, entity.getZ(),
                            12, .3, .3, .3, .1);
                    LivingEntity splashTarget = lightningLevel.getEntitiesOfClass(LivingEntity.class,
                            entity.getBoundingBox().inflate(3, 2, 3),
                            other -> other != entity && other != attacker
                                    && com.myriad.combat.SkillTargets.alive(other)
                                    && !isAlly(attacker, other)
                                    && !(other instanceof com.myriad.game.DefenseTowerEntity)
                                    && other.distanceToSqr(entity) <= 9).stream()
                            .min(java.util.Comparator.comparingDouble(other -> other.distanceToSqr(entity)))
                            .orElse(null);
                    if (splashTarget != null) {
                        com.myriad.combat.DamageCalc.dealDamage(attacker, splashTarget, normalDamage * .10F,
                                com.myriad.combat.DamageCalc.Kind.MAGIC);
                        spawnChainLightning(lightningLevel,
                                entity.position().add(0, entity.getBbHeight() * .6, 0),
                                splashTarget.position().add(0, splashTarget.getBbHeight() * .6, 0));
                    }
                    int cnt = attackerData.getPassiveAttackCount() + 1;
                    attackerData.setPassiveAttackCount(cnt);
                    if (cnt % 3 == 0 && entity.isAlive()) {
                        // 3% + 角色等级%的物攻真实伤害：1级为4%，15级为18%。
                        float trueDamage = adNow * (0.03f + Math.max(1, attackerData.getLevel()) * 0.01f);
                        com.myriad.combat.DamageCalc.dealDamage(attacker, entity, trueDamage,
                                com.myriad.combat.DamageCalc.Kind.TRUE);
                    }
                }
                // 素岚被动【风灵】：移动 100 格后，下次普攻附带风系伤害（12 + 成长攻击力倍率）
                if (CharacterType.byId(attackerData.getCharacterId()) == CharacterType.SULAN
                        && !com.myriad.character.rework.FirstGroupSkills.active(attackerData.getCharacterId())
                        && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                        && !skillHit && event.getAmount() > 0 && entity.isAlive()
                        && com.myriad.character.skills.sulan.SuLanPassiveSkill.consumeWindStrike(attacker)) {
                    float adNow = (float) attacker.getAttributeValue(
                            com.myriad.registry.ModAttributes.PHYSICAL_ATTACK);
                    float wind = 12.0f + (0.30f + 0.03f * Math.max(0, attackerData.getLevel() - 1)) * adNow;
                    com.myriad.combat.DamageCalc.dealDamage(attacker, entity, wind,
                            com.myriad.combat.DamageCalc.Kind.PHYSICAL);
                    if (entity.level() instanceof net.minecraft.server.level.ServerLevel sLevel) {
                        sLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                                entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                                16, 0.4, 0.4, 0.4, 0.05);
                        sLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                                entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                                8, 0.3, 0.3, 0.3, 0.03);
                    }
                }
                // 寒泽被动【霜寒领域】：每次伤害叠一层寒霜；4 层冰冻；减速增伤与冰冻处决。
                if (CharacterType.byId(attackerData.getCharacterId()) == CharacterType.HANZE
                        && !com.myriad.character.rework.FirstGroupSkills.active(attackerData.getCharacterId())
                        && event.getAmount() > 0 && entity.isAlive()) {
                    event.getContainer().setNewDamage(event.getAmount()
                            * com.myriad.character.skills.hanze.HanzeSkills.onFrostDomainDamage(attacker, entity));
                }
                // 凌渊【剑心通明】：普通攻击获得剑罡；【破绽】使凌渊伤害提高 15%。
                if (CharacterType.byId(attackerData.getCharacterId()) == CharacterType.LINGYUAN
                        && !com.myriad.character.rework.FirstGroupSkills.active(attackerData.getCharacterId())
                        && event.getAmount() > 0 && entity.isAlive()) {
                    com.myriad.character.skills.lingyuan.LingYuanSkills.rememberAttackTarget(attacker, entity);
                    if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) && !skillHit) {
                        com.myriad.character.skills.lingyuan.LingYuanSkills.onBasicAttack(attacker, entity,
                                event.getAmount());
                    }
                    if (com.myriad.character.skills.lingyuan.LingYuanSkills.isFlawed(entity)) {
                        event.getContainer().setNewDamage(event.getAmount() * 1.15F);
                    }
                }
                // 赤霄【破阵战意】：对同一敌方英雄的连续有效伤害叠层。
                if (CharacterType.byId(attackerData.getCharacterId()) == CharacterType.CHIXIAO
                        && !com.myriad.character.rework.FirstGroupSkills.active(attackerData.getCharacterId())
                        && event.getAmount() > 0 && entity.isAlive()) {
                    com.myriad.character.skills.chixiao.ChiXiaoSkills.onDamageDealt(attacker, entity);
                }

            }
            // 素岚【灵风标记】：目标受到伤害 +10%（普攻与技能均生效）
            if (event.getAmount() > 0
                    && com.myriad.character.skills.sulan.LingFengBiaoJiSkill.isMarked(entity)) {
                event.getContainer().setNewDamage(event.getAmount() * 1.1f);
            }
            // 伤害飘字（仅对攻击者可见）：物理=红 / 法术=蓝 / 真实=白
            int type = com.myriad.client.DamageFloater.TYPE_PHYSICAL;
            if (skillKind != null) {
                // 技能伤害按实际伤害类型区分颜色
                type = switch (skillKind) {
                    case MAGIC -> com.myriad.client.DamageFloater.TYPE_MAGIC;
                    case TRUE -> com.myriad.client.DamageFloater.TYPE_TRUE;
                    default -> com.myriad.client.DamageFloater.TYPE_PHYSICAL;
                };
            } else if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                    || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) {
                type = com.myriad.client.DamageFloater.TYPE_MAGIC;
            }
            // Contact audio belongs after actual HP/shield loss, not an unconfirmed crit roll.
            // 此处仍处于 Incoming 阶段，只记录显示上下文；真正数值在 LivingDamageEvent.Post
            // 或独立生命池扣除后发送，避免减伤、吸收与防御导致飘字大于实际掉血。
            damageFloat = new PendingDamageFloat(attacker.getUUID(), type, crit, skillHit && !customBasicAttack,
                    com.myriad.character.ability.AbilityRuntime.sourceSkill(),
                    com.myriad.character.ability.AbilityRuntime.sourceDirect() );
            // 攻击目标生命显示（非玩家实体，玩家自身血条已有显示）
            if (entity.isAlive() && !(entity instanceof ServerPlayer)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(attacker,
                        new NetworkSetup.TargetHealthPayload(entity.getId(),
                                (float) com.myriad.combat.DamageCalc.defenseValue(entity,
                                        com.myriad.combat.DamageCalc.Kind.PHYSICAL),
                                (float) com.myriad.combat.DamageCalc.defenseValue(entity,
                                        com.myriad.combat.DamageCalc.Kind.MAGIC)));
            }
        }
        // 模组护盾在全部攻防修正完成后吸收最终伤害；生命扣除阶段不再依赖原版吸收生命。
        if (event.getAmount() > 0.0f) {
            float absorbed = ModShieldSystem.consume(entity, event.getAmount());
            if (absorbed > 0.0f) {
                if(damageFloat!=null&&event.getSource().getEntity() instanceof ServerPlayer shieldAttacker)
                    com.myriad.combat.CombatImpactFeedback.confirmed(shieldAttacker,entity,damageFloat.type(),
                            damageFloat.crit(),damageFloat.skill()&&!damageFloat.direct(),absorbed,true);
                if (entity instanceof ServerPlayer shieldedPlayer) {
                    com.myriad.combat.CombatTelemetry.recordShieldDamage(
                            shieldedPlayer, absorbed, event.getSource().getEntity());
                }
                com.myriad.game.BaseRules.combat(entity,event.getSource().getEntity());
                event.getContainer().setNewDamage(Math.max(0.0f, event.getAmount() - absorbed));
            }
        }

        // 野怪：使用模组生命池（原版血量上限 1024），仅在真实生命归零时交由原版完成死亡事件。
        if (com.myriad.game.MonsterManager.isMonster(entity) && event.getAmount() > 0) {
            if (event.getSource().getEntity() instanceof ServerPlayer jungler
                    && !com.myriad.character.rework.FirstGroupSkills.active(get(jungler).getCharacterId())
                    && java.util.Arrays.asList(get(jungler).getEquipmentSlots()).contains("jungle_blade"))
                event.getContainer().setNewDamage(event.getAmount() * 1.30f);
            float before = com.myriad.game.MonsterManager.modHealth(entity);
            boolean lethal = com.myriad.game.MonsterManager.damageModHealth(entity, event.getAmount());
            float actual = Math.max(0.0F, before - com.myriad.game.MonsterManager.modHealth(entity));
            if(actual>0)com.myriad.game.BaseRules.combat(entity,event.getSource().getEntity());
            sendActualDamageFloat(entity, damageFloat, actual);
            damageFloat = null;
            if (event.getSource().getEntity() instanceof ServerPlayer ap) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(ap,
                        new NetworkSetup.JiarenHpPayload(entity.getId(),
                                com.myriad.game.MonsterManager.modHealth(entity),
                                com.myriad.game.MonsterManager.modMaxHealth(entity)));
            }
            // 非致命伤害完全由模组生命池吸收；致命时放行足够高的原版伤害触发既有奖励/重生链路。
            event.getContainer().setNewDamage(lethal ? 2048.0f : 0.0f);
        }
        // 防御塔与兵线单位：最终伤害进入独立生命池，友军和环境伤害无效。
        boolean laneManaged = com.myriad.game.LaneSystem.isManaged(entity);
        float laneHealthBefore = laneManaged ? com.myriad.game.LaneSystem.health(entity) : 0.0F;
        com.myriad.game.LaneSystem.absorbIncomingDamage(entity, event.getSource(), event);
        if (laneManaged) {
            float actual = Math.max(0.0F, laneHealthBefore - com.myriad.game.LaneSystem.health(entity));
            if(actual>0)com.myriad.game.BaseRules.combat(entity,event.getSource().getEntity());
            sendActualDamageFloat(entity, damageFloat, actual);
            damageFloat = null;
        }
        // 假人（训练目标）：使用 mod 血量（原版血量上限 1024），只扣 mod 血、阻止原版扣血/死亡，
        // 并把 mod 血量同步给攻击者显示
        if (com.myriad.game.JiarenManager.isJiaren(entity.getUUID()) && event.getAmount() > 0) {
            float real = event.getAmount();
            if(real>0)com.myriad.game.BaseRules.combat(entity,event.getSource().getEntity());
            float before = com.myriad.game.JiarenManager.hp(entity.getUUID());
            com.myriad.game.JiarenManager.damage(entity, real);
            event.getContainer().setNewDamage(0.0f);
            sendActualDamageFloat(entity, damageFloat, Math.min(real, before));
            damageFloat = null;
            if (event.getSource().getEntity() instanceof ServerPlayer ap) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(ap,
                        new NetworkSetup.JiarenHpPayload(entity.getId(),
                                com.myriad.game.JiarenManager.hp(entity.getUUID()),
                                com.myriad.game.JiarenManager.MAX_HP));
            }
        }
        // 仅当本次伤害仍会进入原版生命扣除链时排队；全额免伤或护盾完全吸收不会留下陈旧上下文。
        if (damageFloat != null && event.getAmount() > 0.0F) {
            PENDING_DAMAGE_FLOATS.computeIfAbsent(entity.getUUID(), id -> new java.util.ArrayDeque<>())
                    .addLast(damageFloat);
        }
        SpiritFlow.onDamage(entity);
        if (event.getAmount() > 0 && entity instanceof ServerPlayer defendedPlayer
                && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            com.myriad.game.LaneSystem.defendPlayer(defendedPlayer, attacker);
        }
    }

    /** 所有原版生命实体在护甲、减伤、格挡、吸收生命与生命扣除完成后，以实际生命损失显示飘字。 */
    @SubscribeEvent
    public static void onDamageApplied(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        // 原版默认受击无敌帧较长，会吞掉高频技能与攻速收益；模组对局统一缩短为 3 tick。
        if (!event.getEntity().level().isClientSide() && com.myriad.game.ModState.isEnabled()
                && event.getHealthDamage() > 0) {
            event.getEntity().invulnerableTime = 3;
        }
        if(event.getHealthDamage()>0)com.myriad.game.BaseRules.combat(event.getEntity(),event.getSource().getEntity());
        if (event.getEntity().level().isClientSide() || !com.myriad.game.ModState.isEnabled()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer victimPlayer) {
            com.myriad.combat.CombatTelemetry.recordDamage(
                    victimPlayer, event.getHealthDamage(), event.getSource().getEntity());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(victimPlayer,
                    new NetworkSetup.HurtFeedbackPayload(event.getHealthDamage(), victimPlayer.getMaxHealth()));
        }
        // 这些目标已在 Incoming 阶段按各自独立生命池的真实差值发送并消费上下文。
        if (com.myriad.game.MonsterManager.isMonster(entity)
                || com.myriad.game.LaneSystem.isManaged(entity)
                || com.myriad.game.JiarenManager.isJiaren(entity.getUUID())) return;

        sendActualDamageFloat(entity, pollDamageFloat(entity), event.getHealthDamage());
    }

    /** 攻击者是否在目标背后 */
    private static boolean isBehind(LivingEntity attacker, LivingEntity target) {
        Vec3 toAttacker = attacker.position().subtract(target.position());
        double dist = toAttacker.horizontalDistanceSqr();
        if (dist < 1.0E-4) {
            return false;
        }
        // 使用水平朝向，避免目标抬头低头或双方高度差改变背击判定。
        Vec3 look = Vec3.directionFromRotation(0, target.getYRot());
        return look.x * toAttacker.x + look.z * toAttacker.z < -1.0E-6;
    }

    /** 阵营判定：玩家、兵线单位和防御塔均遵守宗门友军规则。 */
    private static boolean isAlly(ServerPlayer caster, LivingEntity other) {
        GameManager manager = GameManager.get();
        if (manager == null) {
            return false;
        }
        Faction a = manager.factionOf(caster);
        Faction b = com.myriad.game.LaneSystem.factionOf(other);
        return a != null && a == b;
    }

    /** 连锁闪电：在被攻击者与被溅射者之间生成一条粗壮曲折的白蓝闪电 */
    private static void spawnChainLightning(net.minecraft.server.level.ServerLevel level, Vec3 from, Vec3 to) {
        int segments = 8;
        Vec3[] pts = new Vec3[segments + 1];
        pts[0] = from;
        pts[segments] = to;
        double lateral = Math.max(0.3, from.distanceTo(to) * 0.15);
        for (int i = 1; i < segments; i++) {
            Vec3 base = from.lerp(to, (double) i / segments);
            double bend = (level.getRandom().nextDouble() * 2.0 - 1.0) * lateral;
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            pts[i] = base.add(Math.cos(angle) * bend, 0, Math.sin(angle) * bend);
        }
        for (int i = 0; i < segments; i++) {
            Vec3 a = pts[i];
            Vec3 b = pts[i + 1];
            Vec3 seg = b.subtract(a);
            Vec3 right = new Vec3(-seg.z, 0, seg.x);
            if (right.lengthSqr() < 0.001) {
                right = new Vec3(0, 0, 1);
            } else {
                right = right.normalize();
            }
            int n = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.3));
            for (int k = 0; k <= n; k++) {
                Vec3 p = a.lerp(b, (double) k / n);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.005);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        p.x + right.x * 0.25, p.y, p.z + right.z * 0.25, 1, 0, 0, 0, 0);
                if (k % 3 == 0) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                if (k % 4 == 0) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                            p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    // ---- 死亡、击杀奖励 ----

    /**
     * 模组对局中的普通伤害不附带原版击退。明确设计了击退、牵引或推行的技能
     * 直接通过 KnockbackBuffer.apply / motion 施加，因此不会被这里误拦截。
     */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (!com.myriad.game.ModState.isEnabled() || event.getEntity().level().isClientSide()) return;

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        com.myriad.combat.KnockbackBuffer.clear(event.getEntity().getUUID());
        ModShieldSystem.clearFor(event.getEntity().getUUID());
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            long deathTick = player.level().getGameTime();
            Long previousDeath = LAST_PLAYER_DEATH_EVENT.put(player.getUUID(), deathTick);
            if (previousDeath != null && deathTick - previousDeath <= 10L) {
                event.setCanceled(true);
                return;
            }
            com.myriad.character.ability.AbilityRuntime.clear(player);

            com.myriad.game.TacticalSupplySystem.clearRegeneration(player.getUUID());
            ModShieldSystem.clearFor(player);
            GameManager manager = GameManager.get();
            if (com.myriad.game.TrainingMode.isEnabled()) {
                event.setCanceled(true);
                player.setCamera(player);
                player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                player.fallDistance = 0;
                player.clearFire();
                player.invulnerableTime = 20;
                PlayerHealthSystem.fill(player);
                get(player).setMana((float)player.getAttributeValue(ModAttributes.MAX_MANA));
                syncToClient(player);
                return;
            }

            net.minecraft.world.entity.Entity credited = creditedKiller(player, event.getSource().getEntity());
            if (manager != null) {
                manager.recordHeroDeath(player, credited);
            }
            if (manager != null && credited instanceof ServerPlayer killer) {
                manager.onHeroKill(killer, player);
            }
            if (credited instanceof ServerPlayer killer) {

                if (killer != player && killer.isAlive() && !killer.isSpectator()
                        && CharacterType.byId(get(killer).getCharacterId()) == CharacterType.FENJUE
                        && !com.myriad.character.rework.FirstGroupSkills.active(get(killer).getCharacterId())
                        && get(killer).getFaction() != null && get(killer).getFaction() != get(player).getFaction()) {
                    com.myriad.combat.CombatTelemetry.heal(killer,
                            Math.max(0, killer.getMaxHealth() - killer.getHealth()) * .30F, "kill");
                }
            }
            player.getPersistentData().putInt("myriad_deaths_without_kill",
                    Math.min(20,player.getPersistentData().getIntOr("myriad_deaths_without_kill",0)+1));
            player.getPersistentData().remove("myriad_last_hero");
            player.getPersistentData().remove("myriad_last_hero_at");
            if (com.myriad.game.TrainingMode.isEnabled()
                    || manager != null && manager.getPhase() == GameManager.Phase.PLAYING) {
                event.setCanceled(true);
                com.myriad.game.HeroRespawnSystem.begin(player);
            }
            return;
        }
        // 野怪死亡：先发放击杀奖励（依赖存活标记），再清理标记 + 安排重生
        if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                && com.myriad.game.MonsterManager.isMonster(living)) {
            if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                com.myriad.game.MonsterManager.onKill(killer, living);
            }
            com.myriad.game.MonsterManager.onDeath(living);
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                && com.myriad.game.LaneSystem.isManaged(living)) {

            com.myriad.game.LaneSystem.onDeath(living, event.getSource().getEntity());
            return;
        }

        // 焚诀被动【焚天战意】：每击杀一名敌方玩家，回复自身 30% 已损失生命值
        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && event.getEntity() instanceof ServerPlayer victim
                && CharacterType.byId(get(killer).getCharacterId()) == CharacterType.FENJUE
                && !com.myriad.character.rework.FirstGroupSkills.active(get(killer).getCharacterId())) {
            GameManager mgr = GameManager.get();
            boolean enemy = mgr == null
                    || mgr.factionOf(killer) != mgr.factionOf(victim);
            if (enemy && killer.isAlive()) {
                float lost = killer.getMaxHealth() - killer.getHealth();
                if (lost > 0) {
                    com.myriad.combat.CombatTelemetry.heal(killer, lost * 0.30f, "kill");
                    killer.level().sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                            killer.getX(), killer.getY() + 1.0, killer.getZ(),
                            24, 0.4, 0.4, 0.4, 0.06);
                }
            }
        }
    }

    // ---- 命令 ----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("myriad")
                .then(Commands.literal("jiaren")
                        .then(Commands.literal("create").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.myriad.game.JiarenManager.create(player);
                            return 1;
                        }))
                        .then(Commands.literal("friendly").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.myriad.game.JiarenManager.createFriendly(player);
                            return 1;
                        }))
                        .then(Commands.literal("remove").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.myriad.game.JiarenManager.removeNearby(player);
                            return 1;
                        })))
                .then(Commands.literal("mob")
                        .then(Commands.literal("spawnpoint")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            for (com.myriad.game.MonsterType t
                                                                    : com.myriad.game.MonsterType.values()) {
                                                                if (!t.isLargeBoss()) builder.suggest(t.getId());
                                                            }
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> configureMonsterSpawnPoint(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "name"),
                                                                StringArgumentType.getString(ctx, "type"), false)))))
                                .then(Commands.literal("setlarge")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> configureMonsterSpawnPoint(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"), null, true))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (var point : com.myriad.game.MonsterManager.spawnPointInfos()) {
                                                        builder.suggest(point.id());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> removeMonsterSpawnPoint(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.literal("list")
                                        .executes(ctx -> listMonsterSpawnPoints(ctx.getSource())))
                                )
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (com.myriad.game.MonsterType t : com.myriad.game.MonsterType.values()) {
                                        builder.suggest(t.getId());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    com.myriad.game.MonsterType type =
                                            com.myriad.game.MonsterType.byId(StringArgumentType.getString(ctx, "type"));
                                    if (type == null) {
                                        ctx.getSource().sendFailure(Component.translatable("message.myriad.invalid_monster"));
                                        return 0;
                                    }
                                    com.myriad.game.MonsterManager.spawn(
                                            (net.minecraft.server.level.ServerLevel) player.level(),
                                            player.blockPosition(), type);
                                    return 1;
                                })))
                .then(Commands.literal("arena").requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.myriad.arena_selected",
                                    com.myriad.game.MatchArena.active().displayName()), false);
                            return 1;
                        })
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.List.of("5v5", "3v3"), builder))
                                .executes(ctx -> {
                                    if (!com.myriad.game.MatchArena.select(ctx.getSource().getServer(),
                                            StringArgumentType.getString(ctx, "mode"))) {
                                        ctx.getSource().sendFailure(Component.translatable("message.myriad.arena_unavailable"));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.myriad.arena_selected",
                                            com.myriad.game.MatchArena.active().displayName()), true);
                                    broadcastArenaTeams(ctx.getSource().getServer());
                                    return 1;
                                })))
                .then(Commands.literal("start").requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)).executes(ctx -> {
                    GameManager manager = GameManager.get();
                    if (manager != null) {
                        manager.startMatch(ctx.getSource().getServer());
                    }
                    return 1;
                }))
                .then(Commands.literal("restart").requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)).executes(ctx -> {
                    GameManager manager = GameManager.get();
                    if (manager != null) {
                        manager.restart(ctx.getSource().getServer());
                    }
                    return 1;
                }))
                .then(Commands.literal("reset").requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)).executes(ctx -> {
                    if (!com.myriad.game.ModState.isEnabled() || GameManager.get() == null) {
                        ctx.getSource().sendFailure(Component.translatable("message.myriad.reset_requires_mod"));
                        return 0;
                    }
                    try {
                        com.myriad.game.FullGameReset.execute(ctx.getSource().getServer());
                    } catch (java.io.IOException error) {
                        Myriad.LOGGER.error("Cannot persist full reset generation", error);
                        ctx.getSource().sendFailure(Component.translatable("message.myriad.reset_failed"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatable("message.myriad.reset_complete"), true);
                    return 1;
                }))
                .then(buildObjectiveCommand())
                .then(buildLaneCommand())
                .then(buildBrushCommand())
                .then(Commands.literal("mod")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .then(Commands.literal("on").executes(ctx -> {
                            com.myriad.game.ModState.setEnabled(true, ctx.getSource().getServer());
                            ctx.getSource().getServer().getPlayerList().setViewDistance(10);
                            ctx.getSource().getServer().getPlayerList().setSimulationDistance(8);
                            for (ServerPlayer participant : ctx.getSource().getServer().getPlayerList().getPlayers())
                                com.myriad.game.LobbySystem.teleportToLobby(participant);
                            broadcastModState(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            com.myriad.game.ModState.setEnabled(false);
                            if (GameManager.get() != null) GameManager.get().resetToLobby(ctx.getSource().getServer(), false);
                            com.myriad.game.TrainingMode.setEnabled(false);
                            com.myriad.game.TrainingMode.setNoCooldown(false);
                            com.myriad.game.TrainingMode.setNoGoldCost(false);
                            com.myriad.game.TrainingMode.setNoMana(false);
                            broadcastModState(ctx.getSource().getServer());
                            for (var p : ctx.getSource().getServer().getPlayerList().getPlayers()) com.myriad.game.VanillaSession.restore(p);
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "message.myriad.mod_status",
                                    com.myriad.game.ModState.isEnabled()
                                            ? Component.translatable("message.myriad.mod_on")
                                            : Component.translatable("message.myriad.mod_off")), false);
                            return 1;
                        }))
                .then(Commands.literal("status").executes(ctx -> {
                    GameManager manager = GameManager.get();
                    if (manager == null) {
                        ctx.getSource().sendFailure(Component.translatable("message.myriad.no_game"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatable("message.myriad.status",
                            manager.getPhase().name(), manager.getXuanxiaoScore(),
                            GameRules.TEAM_TARGET_SCORE, manager.getYoumingScore()), false);
                    return 1;
                }))
                .then(Commands.literal("team")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("xuanxiao");
                                    builder.suggest("youming");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer target = ctx.getSource().getPlayerOrException();
                                    return assignTeamById(ctx.getSource(), target,
                                            StringArgumentType.getString(ctx, "faction"));
                                })
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            return assignTeamById(ctx.getSource(), target,
                                                    StringArgumentType.getString(ctx, "faction"));
                                        }))))
                .then(Commands.literal("char")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String id : CharacterType.ids()) {
                                        builder.suggest(id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CharacterType type = CharacterType.byId(StringArgumentType.getString(ctx, "id"));
                                    chooseCharacter(player, type);
                                    return 1;
                                })))
                .then(Commands.literal("train")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .then(Commands.literal("on").executes(ctx -> {
                            com.myriad.game.TrainingMode.setEnabled(true);
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.myriad.train_on"), false);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            com.myriad.game.TrainingMode.setEnabled(false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.myriad.train_off"), false);
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.myriad.train_status",
                                    com.myriad.game.TrainingMode.isEnabled()), false);
                            return 1;
                        }))
                .then(Commands.literal("supply")
                        .then(Commands.literal("buy")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String id : com.myriad.game.TacticalSupplySystem.supplyIds()) {
                                                builder.suggest(id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> com.myriad.game.TacticalSupplySystem.buy(
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "id")) ? 1 : 0))))
                .then(Commands.literal("gold")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    PlayerData data = get(player);
                                    if (data == null) {
                                        return 0;
                                    }
                                    data.setGold(IntegerArgumentType.getInteger(ctx, "amount"));
                                    player.sendSystemMessage(Component.translatable("message.myriad.gold_set",
                                            data.getGold()));
                                    syncToClient(player);
                                    return 1;
                                }))));
    }

    private static int configureMonsterSpawnPoint(CommandSourceStack source, String name, String typeId,
            boolean largeRandom) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.level() != source.getServer().overworld()) {
            source.sendFailure(Component.translatable("message.myriad.mob_spawnpoint_overworld"));
            return 0;
        }
        com.myriad.game.MonsterType type = largeRandom ? null
                : com.myriad.game.MonsterType.byId(typeId);
        if (!largeRandom && (type == null || type.isLargeBoss())) {
            source.sendFailure(Component.translatable("message.myriad.invalid_monster"));
            return 0;
        }
        if (!com.myriad.game.MonsterManager.setSpawnPoint(
                name, player.blockPosition(), type, largeRandom)) {
            source.sendFailure(Component.translatable("message.myriad.mob_spawnpoint_invalid_name"));
            return 0;
        }
        String shownType = largeRandom ? "large_random" : type.getId();
        source.sendSuccess(() -> Component.translatable("message.myriad.mob_spawnpoint_set",
                name, shownType, player.getBlockX(), player.getBlockY(), player.getBlockZ()), false);
        return 1;
    }

    private static int removeMonsterSpawnPoint(CommandSourceStack source, String name) {
        if (!com.myriad.game.MonsterManager.removeSpawnPoint(name)) {
            source.sendFailure(Component.translatable("message.myriad.mob_spawnpoint_missing", name));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.myriad.mob_spawnpoint_removed", name), false);
        return 1;
    }

    private static int listMonsterSpawnPoints(CommandSourceStack source) {
        var points = com.myriad.game.MonsterManager.spawnPointInfos();
        if (points.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.myriad.mob_spawnpoint_empty"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("message.myriad.mob_spawnpoint_list", points.size()), false);
        for (var point : points) {
            source.sendSuccess(() -> Component.literal("- " + point.id() + " [" + point.typeId() + "] "
                    + point.pos().getX() + " " + point.pos().getY() + " " + point.pos().getZ()), false);
        }
        return points.size();
    }

    private static int configureVeinSpawn(CommandSourceStack source, boolean large, boolean clear, int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        GameManager manager = GameManager.get();
        if (manager == null) return 0;
        ServerPlayer player = source.getPlayerOrException();
        if (!manager.setSpiritVeinSpawn(large, slot, clear ? null : player.blockPosition())) {
            source.sendFailure(Component.translatable("message.myriad.settings_only_before"));
            return 0;
        }
        Component veinName = large ? Component.translatable("message.myriad.vein_large")
                : Component.translatable("message.myriad.vein_"
                        + GameManager.SpiritAffinity.bySlot(slot).id());
        if (clear) {
            source.sendSuccess(() -> Component.translatable("message.myriad.vein_spawn_cleared",
                    veinName), false);
        } else {
            source.sendSuccess(() -> Component.translatable("message.myriad.vein_spawn_set",
                    veinName, player.getBlockX(), player.getBlockY(), player.getBlockZ()), false);
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildObjectiveCommand() {
        var command = Commands.literal("objective")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR));
        for (boolean clear : new boolean[] {false, true}) {
            var operation = Commands.literal(clear ? "clear" : "set");
            for (GameManager.SpiritAffinity affinity : GameManager.SpiritAffinity.values()) {
                operation.then(Commands.literal(affinity.id()).executes(ctx -> configureVeinSpawn(
                        ctx.getSource(), false, clear, affinity.slot())));
            }
            operation.then(Commands.literal("large")
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1,
                                    GameManager.LARGE_VEIN_CANDIDATE_COUNT))
                            .executes(ctx -> configureVeinSpawn(ctx.getSource(), true, clear,
                                    IntegerArgumentType.getInteger(ctx, "slot")))));
            command.then(operation);
        }
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLaneCommand() {
        var command = Commands.literal("lane")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR));
        for (boolean clear : new boolean[] {false, true}) {
            var operation = Commands.literal(clear ? "clear" : "set");
            operation.then(Commands.literal("crystal")
                    .then(Commands.argument("faction", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("xuanxiao"); builder.suggest("youming");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> configureLaneCrystal(ctx.getSource(), clear,
                                    StringArgumentType.getString(ctx, "faction")))));
            operation.then(Commands.literal("path")
                    .then(Commands.argument("lane", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("top"); builder.suggest("mid"); builder.suggest("bottom");
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("slot", IntegerArgumentType.integer(1,
                                            com.myriad.game.LaneSystem.MAX_PATH_POINTS))
                                    .executes(ctx -> configureLanePath(ctx.getSource(), clear,
                                            StringArgumentType.getString(ctx, "lane"),
                                            IntegerArgumentType.getInteger(ctx, "slot"))))));
            operation.then(Commands.literal("tower")
                    .then(Commands.argument("faction", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("xuanxiao"); builder.suggest("youming");
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("lane", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        builder.suggest("top"); builder.suggest("mid"); builder.suggest("bottom");
                                        return builder.buildFuture();
                                    })
                                    .then(Commands.argument("tier", IntegerArgumentType.integer(1,
                                                    com.myriad.game.LaneSystem.TOWERS_PER_LANE))
                                            .executes(ctx -> configureLaneTower(ctx.getSource(), clear,
                                                    StringArgumentType.getString(ctx, "faction"),
                                                    StringArgumentType.getString(ctx, "lane"),
                                                    IntegerArgumentType.getInteger(ctx, "tier")))))));
            command.then(operation);
        }
        command.then(Commands.literal("list").executes(ctx -> listLaneLayout(ctx.getSource())));
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBrushCommand() {
        return Commands.literal("brush")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("corner", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("pos1"); builder.suggest("pos2");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> configureBrushCorner(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "corner"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (var brush : com.myriad.game.VisionSystem.brushInfos()) {
                                        builder.suggest(brush.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> removeBrush(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list").executes(ctx -> listBrushes(ctx.getSource())));
    }

    private static int configureBrushCorner(CommandSourceStack source, String name, String cornerId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int corner = "pos1".equalsIgnoreCase(cornerId) ? 1 : "pos2".equalsIgnoreCase(cornerId) ? 2 : 0;
        ServerPlayer player = source.getPlayerOrException();
        if (corner == 0 || !com.myriad.game.VisionSystem.setBrushCorner(
                name, corner, player.blockPosition())) {
            source.sendFailure(Component.translatable("message.myriad.brush_set_failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.myriad.brush_corner_set",
                name, corner, player.getBlockX(), player.getBlockY(), player.getBlockZ()), false);
        return 1;
    }

    private static int removeBrush(CommandSourceStack source, String name) {
        if (!com.myriad.game.VisionSystem.removeBrush(name)) {
            source.sendFailure(Component.translatable("message.myriad.brush_remove_failed", name));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.myriad.brush_removed", name), false);
        return 1;
    }

    private static int listBrushes(CommandSourceStack source) {
        var brushes = com.myriad.game.VisionSystem.brushInfos();
        source.sendSuccess(() -> Component.translatable("message.myriad.brush_list", brushes.size()), false);
        for (var brush : brushes) {
            source.sendSuccess(() -> Component.literal("- " + brush.id() + ": "
                    + brush.min().getX() + " " + brush.min().getY() + " " + brush.min().getZ()
                    + " -> " + brush.max().getX() + " " + brush.max().getY() + " " + brush.max().getZ()), false);
        }
        return Math.max(1, brushes.size());
    }

    private static int configureLanePath(CommandSourceStack source, boolean clear, String laneId, int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        com.myriad.game.LaneSystem.Lane lane = com.myriad.game.LaneSystem.Lane.byId(laneId);
        if (lane == null) {
            source.sendFailure(Component.translatable("message.myriad.lane_invalid"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        boolean success = clear
                ? com.myriad.game.LaneSystem.clearPathPoint(lane, slot)
                : com.myriad.game.LaneSystem.setPathPoint(lane, slot, player.blockPosition());
        if (!success) {
            source.sendFailure(Component.translatable("message.myriad.settings_only_before"));
            return 0;
        }
        if (clear) {
            source.sendSuccess(() -> Component.translatable("message.myriad.lane_path_cleared",
                    Component.translatable("lane.myriad." + lane.id()), slot), false);
        } else {
            source.sendSuccess(() -> Component.translatable("message.myriad.lane_path_set",
                    Component.translatable("lane.myriad." + lane.id()), slot,
                    player.getBlockX(), player.getBlockY(), player.getBlockZ()), false);
        }
        return 1;
    }

    private static int configureLaneTower(CommandSourceStack source, boolean clear,
            String factionId, String laneId, int tier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Faction faction = com.myriad.game.LaneSystem.parseFaction(factionId);
        com.myriad.game.LaneSystem.Lane lane = com.myriad.game.LaneSystem.Lane.byId(laneId);
        if (faction == null || lane == null) {
            source.sendFailure(Component.translatable("message.myriad.lane_invalid"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        boolean success = clear
                ? com.myriad.game.LaneSystem.clearTowerPoint(faction, lane, tier)
                : com.myriad.game.LaneSystem.setTowerPoint(faction, lane, tier, player.blockPosition());
        if (!success) {
            source.sendFailure(Component.translatable("message.myriad.settings_only_before"));
            return 0;
        }
        if (clear) {
            source.sendSuccess(() -> Component.translatable("message.myriad.lane_tower_cleared",
                    faction.displayName(), Component.translatable("lane.myriad." + lane.id()), tier), false);
        } else {
            source.sendSuccess(() -> Component.translatable("message.myriad.lane_tower_set",
                    faction.displayName(), Component.translatable("lane.myriad." + lane.id()), tier,
                    player.getBlockX(), player.getBlockY(), player.getBlockZ()), false);
        }
        return 1;
    }

    private static int configureLaneCrystal(CommandSourceStack source, boolean clear, String factionId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Faction faction = Faction.byId(factionId);
        if (faction == null) {
            source.sendFailure(Component.translatable("message.myriad.lane_invalid"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        if (player.level() != source.getServer().overworld()) {
            source.sendFailure(Component.translatable("message.myriad.building_overworld_only"));
            return 0;
        }
        boolean success = clear ? com.myriad.game.LaneSystem.clearCrystalPoint(faction)
                : com.myriad.game.LaneSystem.setCrystalPoint(faction, player.blockPosition());
        if (!success) {
            source.sendFailure(Component.translatable("message.myriad.settings_only_before"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(clear ? "message.myriad.crystal_cleared"
                : "message.myriad.crystal_set", faction.displayName(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ()), false);
        return 1;
    }

    private static int listLaneLayout(CommandSourceStack source) {
        var lines = com.myriad.game.LaneSystem.layoutLines();
        source.sendSuccess(() -> Component.translatable("message.myriad.lane_layout_list", lines.size()), false);
        for (var line : lines) source.sendSuccess(() -> Component.literal("- " + line.text()), false);
        return Math.max(1, lines.size());
    }

    private static void assignTeam(ServerPlayer player, Faction faction) {
        PlayerData data = get(player);
        if (data != null) {
            data.setFaction(faction);
        }
        if (GameManager.get() != null) {
            GameManager.get().onPlayerJoin(player);
        }
        player.refreshTabListName();
        syncToClient(player);
    }

    private static int assignTeamById(CommandSourceStack source, ServerPlayer player, String factionId) {
        Faction faction = Faction.byId(factionId);
        if (faction == null) {
            source.sendFailure(Component.translatable("message.myriad.invalid_faction"));
            return 0;
        }
        assignTeam(player, faction);
        return 1;
    }

    // ---- 游戏设置 UI（服务端处理） ----

    /** 发送当前游戏设置 */
    public static void sendGameSettings(ServerPlayer requester) {
        java.util.Map<String, Integer> settings = new java.util.HashMap<>();
        settings.put("matchDuration", com.myriad.game.GameRules.MATCH_DURATION_SECONDS);
        settings.put("targetScore", com.myriad.game.GameRules.TEAM_TARGET_SCORE);
        settings.put("spiritPerExtract", com.myriad.game.GameRules.SPIRIT_PER_EXTRACT);
        settings.put("channelTicks", com.myriad.game.GameRules.CHANNEL_TICKS);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(requester,
                new NetworkSetup.GameSettingsPayload(settings));
    }

    /** 修改游戏设置（仅开局前、管理员；值域约束后应用） */
    public static void updateGameSetting(ServerPlayer admin, String key, int value) {
        if (!admin.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
            return;
        }
        GameManager manager = GameManager.get();
        if (manager != null && manager.getPhase() != GameManager.Phase.WAITING) {
            admin.sendSystemMessage(Component.translatable("message.myriad.settings_only_before"));
            return;
        }
        switch (key) {
            case "matchDuration" -> com.myriad.game.GameRules.MATCH_DURATION_SECONDS =
                    Math.max(60, Math.min(value, 7200));
            case "targetScore" -> com.myriad.game.GameRules.TEAM_TARGET_SCORE =
                    Math.max(100, Math.min(value, 100000));
            case "spiritPerExtract" -> com.myriad.game.GameRules.SPIRIT_PER_EXTRACT =
                    Math.max(1, Math.min(value, 1000));
            case "channelTicks" -> com.myriad.game.GameRules.CHANNEL_TICKS =
                    Math.max(20, Math.min(value, 600));
            default -> {
                return;
            }
        }
        sendGameSettings(admin);
    }

    // ---- 装备系统 ----

    /** 商店购买装备 */
    public static void buyEquipment(ServerPlayer player, String equipId) {
        PlayerData data = get(player);
        boolean bought = data != null && com.myriad.game.ModState.isEnabled()
                && com.myriad.player.EquipmentSystem.buy(player, data, equipId);
        com.myriad.network.UiFeedbackPayload.purchase(player, bought);
        if (bought) {
            syncToClient(player);
        }
    }

    /** 商店购买消耗品、陷阱或视野饰品。 */
    public static void buySupply(ServerPlayer player, String supplyId) {
        boolean bought = com.myriad.game.ModState.isEnabled()
                && com.myriad.game.TacticalSupplySystem.buy(player, supplyId);
        com.myriad.network.UiFeedbackPayload.purchase(player, bought);
    }

    /** 出售装备（返还 70% 价格） */
    public static void sellEquipment(ServerPlayer player, int slot) {
        PlayerData data = get(player);
        if (data == null || !com.myriad.game.ModState.isEnabled()) {
            return;
        }
        if (com.myriad.player.EquipmentSystem.sell(player, data, slot)) {
            syncToClient(player);
        }
    }

    /** 合成装备（组件原价抵消） */
    public static void craftEquipment(ServerPlayer player, String equipId) {
        PlayerData data = get(player);
        if (data == null || !com.myriad.game.ModState.isEnabled()) {
            return;
        }
        if (com.myriad.player.EquipmentSystem.buy(player, data, equipId)) {
            syncToClient(player);
        }
    }

    /** 训练模式 GUI 操作（仅训练模式）：升级、金币、宗门与训练开关。 */
    public static void trainAction(ServerPlayer player, String type, boolean value) {
        if (!com.myriad.game.TrainingMode.isEnabled()) {
            return;
        }
        PlayerData data = get(player);
        if (data == null) {
            return;
        }
        switch (type) {
            case "level" -> {
                LevelSystem.addXp(player, data, LevelSystem.xpToNext(data.getLevel()));
            }
            case "gold" -> {
                data.addGold(value ? 10000 : 1000);
                player.sendSystemMessage(Component.translatable("message.myriad.gold_set", data.getGold()));
            }
            case "faction_xuanxiao" -> {
                assignTeam(player, Faction.XUANXIAO);
                player.sendSystemMessage(Component.translatable(
                        "message.myriad.team_assigned", Faction.XUANXIAO.displayName()));
            }
            case "faction_youming" -> {
                assignTeam(player, Faction.YOUMING);
                player.sendSystemMessage(Component.translatable(
                        "message.myriad.team_assigned", Faction.YOUMING.displayName()));
            }
            case "nocooldown" -> com.myriad.game.TrainingMode.setNoCooldown(value);
            case "nogold" -> com.myriad.game.TrainingMode.setNoGoldCost(value);
            case "nomana" -> com.myriad.game.TrainingMode.setNoMana(value);
            default -> {
                return;
            }
        }
        syncToClient(player);
    }

    /** 模组开关广播：通知全体并同步客户端状态；关闭时重置全体为原版属性，开启时恢复模组初始属性 */
    private static void broadcastModState(MinecraftServer server) {
        Component message = Component.translatable(
                com.myriad.game.ModState.isEnabled()
                        ? "message.myriad.mod_enabled_broadcast"
                        : "message.myriad.mod_disabled_broadcast");
        server.getPlayerList().broadcastSystemMessage(message, false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 先彻底重置（清除全部 modifier + 恢复注册默认值），再应用目标属性集
            CharacterType.applyVanillaAttributes(player);
            if (com.myriad.game.ModState.isEnabled()) {
                PlayerData d = get(player);
                CharacterType.applyInitialAttributes(player,
                        CharacterType.byId(d == null ? null : d.getCharacterId()));
                if (d != null) {
                    LevelSystem.applyLevelAttributes(player, d.getLevel());
                    d.resetEquipApplied();
                    com.myriad.player.EquipmentSystem.apply(player, d);
                    RetiredRuneCleanup.clean(player);
                }
            }
            player.refreshTabListName();
            syncToClient(player);
        }
    }

    // ---- 开局管理 UI（服务端处理） ----

    /** 发送在线玩家与宗门列表（factionId 0=未分配 1=玄霄宗 2=幽冥宗） */
    public static void sendTeamList(ServerPlayer requester) {
        java.util.Map<String, Integer> teams = new java.util.HashMap<>();
        MinecraftServer server = ((net.minecraft.server.level.ServerLevel) requester.level()).getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerData d = get(p);
            Faction f = d == null ? null : d.getFaction();
            teams.put(p.getName().getString(), f == null ? 0 : (f == Faction.XUANXIAO ? 1 : 2));
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(requester,
                new NetworkSetup.TeamListPayload(teams, com.myriad.game.MatchArena.active().id,
                        com.myriad.game.MatchArena.available(server, com.myriad.game.MatchArena.Mode.DUEL)));
    }

    private static void broadcastArenaTeams(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendTeamList(player);
    }

    public static void selectArenaByAdmin(ServerPlayer admin, String arenaId) {
        if (!admin.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
            admin.sendSystemMessage(Component.translatable("message.myriad.no_permission"));
            sendTeamList(admin);
            return;
        }
        MinecraftServer server = admin.level().getServer();
        if (!com.myriad.game.MatchArena.select(server, arenaId))
            admin.sendSystemMessage(Component.translatable("message.myriad.arena_unavailable"));
        broadcastArenaTeams(server);
    }

    /** UI 中把玩家拖入阵营 */
    public static void setTeamByAdmin(ServerPlayer admin, String playerName, int factionId) {
        if (!admin.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
            return;
        }
        if (GameManager.get() == null || GameManager.get().getPhase() != GameManager.Phase.WAITING
                || factionId < 0 || factionId > 2) return;
        ServerPlayer target = null;
        MinecraftServer server = ((net.minecraft.server.level.ServerLevel) admin.level()).getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getName().getString().equals(playerName)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            return;
        }
        Faction faction = factionId == 1 ? Faction.XUANXIAO : factionId == 2 ? Faction.YOUMING : null;
        assignTeam(target, faction);
        sendTeamList(admin);
    }

    /** UI 开始游戏：mode 0=均衡随机分配后开始，1=按当前手动分配开始 */
    public static void startMatchFromUi(ServerPlayer admin, int mode, String arenaId) {
        Myriad.LOGGER.info("[ui-start] mode={} by {} perm={}",
                mode, admin.getName().getString(),
                admin.permissions().hasPermission(Permissions.COMMANDS_MODERATOR));
        if (!admin.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
            admin.sendSystemMessage(Component.translatable("message.myriad.no_permission"));
            return;
        }
        GameManager manager = GameManager.get();
        if (manager == null) {
            Myriad.LOGGER.info("[ui-start] no manager");
            return;
        }
        MinecraftServer server = ((net.minecraft.server.level.ServerLevel) admin.level()).getServer();
        if (mode < 0 || mode > 1 || !com.myriad.game.MatchArena.select(server, arenaId)) {
            admin.sendSystemMessage(Component.translatable("message.myriad.arena_unavailable"));
            sendTeamList(admin);
            return;
        }
        if (mode == 0) {
            manager.startMatch(server);
        } else {
            // 手动模式：未分配玩家自动补幽冥宗，双方必须均有玩家。
            manager.startMatchWithCurrent(server);
        }
        sendTeamList(admin);
    }
}
