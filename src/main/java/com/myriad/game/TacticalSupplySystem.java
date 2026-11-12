package com.myriad.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModItems;

/** 对局消耗品、陷阱与视野饰品。所有探测与触发均由服务端判定。 */
public final class TacticalSupplySystem {
    private TacticalSupplySystem() {}

    private static final int BLOOD_TOTAL = 40;
    private static final int MANA_TOTAL = 50;
    private static final int WARD_DURATION = 120 * 20;
    private static final int TRAP_DURATION = 90 * 20;
    private static final double WARD_RADIUS = 20.0;
    private static final double WARD_CAST_RADIUS = 6.0;
    private static final double TRUE_SIGHT_RADIUS = 12.0;
    private static final double TRAP_RADIUS = 2.5;

    private static final Map<UUID, Integer> BLOOD_REMAINING = new HashMap<>();
    private static final Map<UUID, Integer> MANA_REMAINING = new HashMap<>();
    private static final Map<UUID, Long> TRUE_SIGHT_UNTIL = new HashMap<>();
    private static final List<Deployable> DEPLOYABLES = new ArrayList<>();

    public static List<String> supplyIds() {
        return List.of("blood_pill", "mana_pill", "spirit_snare", "scout_totem", "true_sight_charm");
    }

    public static boolean isSupply(String id) {
        return offer(id) != null;
    }

    public static int supplyPrice(String id) {
        SupplyOffer offer = offer(id);
        return offer == null ? 0 : offer.price;
    }

    /** 使用对局金币购买补给；视野饰品互斥，购买时会自动替换。 */
    public static boolean buy(ServerPlayer player, String id) {
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null || (GameManager.get() == null && !TrainingMode.isEnabled())
                || !BaseRules.canShop(player)) {
            if (data != null && ModState.isEnabled() && !BaseRules.canShop(player))
                player.sendSystemMessage(Component.translatable("message.myriad.shop_base_only"));
            return false;
        }
        SupplyOffer offer = offer(id);
        if (offer == null) {
            player.sendSystemMessage(Component.translatable("message.myriad.supply_unknown"));
            return false;
        }
        if ((offer.item == ModItems.BLOOD_PILL.get() || offer.item == ModItems.MANA_PILL.get())
                && pillCount(player) + offer.count > 5) {
            player.sendSystemMessage(Component.translatable("message.myriad.pill_limit"));
            return false;
        }
        boolean free = TrainingMode.isEnabled() && TrainingMode.isNoGoldCost();
        if (!free && data.getGold() < offer.price) {
            player.sendSystemMessage(Component.translatable("message.myriad.supply_no_gold"));
            return false;
        }
        if (offer.item == ModItems.SCOUT_TOTEM.get() || offer.item == ModItems.TRUE_SIGHT_CHARM.get()) {
            removeVisionTrinkets(player);
        }
        ItemStack bought = new ItemStack(offer.item, offer.count);
        Component itemName = bought.getHoverName();
        if (!player.getInventory().add(bought)) player.drop(bought, false);
        if (!free) data.spendGold(offer.price);
        PlayerDataProvider.syncToClient(player);
        player.sendSystemMessage(Component.translatable("message.myriad.supply_bought",
                itemName, offer.price));
        return true;
    }

    private static SupplyOffer offer(String id) {
        return switch (id) {
            case "blood_pill" -> new SupplyOffer(ModItems.BLOOD_PILL.get(), 60, 1);
            case "mana_pill" -> new SupplyOffer(ModItems.MANA_PILL.get(), 60, 1);
            case "spirit_snare" -> new SupplyOffer(ModItems.SPIRIT_SNARE.get(), 100, 1);
            case "scout_totem" -> new SupplyOffer(ModItems.SCOUT_TOTEM.get(), 150, 1);
            case "true_sight_charm" -> new SupplyOffer(ModItems.TRUE_SIGHT_CHARM.get(), 300, 1);
            default -> null;
        };
    }

    public static void clearRegeneration(UUID playerId) {
        BLOOD_REMAINING.remove(playerId);
        MANA_REMAINING.remove(playerId);
        TRUE_SIGHT_UNTIL.remove(playerId);
    }

    public static boolean tryUse(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.is(ModItems.BLOOD_PILL.get()) && !stack.is(ModItems.MANA_PILL.get())
                && !stack.is(ModItems.SPIRIT_SNARE.get()) && !stack.is(ModItems.SCOUT_TOTEM.get())
                && !stack.is(ModItems.TRUE_SIGHT_CHARM.get())) return false;
        GameManager manager = GameManager.get();
        boolean playing = manager != null && manager.getPhase() == GameManager.Phase.PLAYING;
        if (!playing && !TrainingMode.isEnabled()) {
            player.sendSystemMessage(Component.translatable("message.myriad.supply_match_only"));
            return true;
        }
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null) return true;

        if (stack.is(ModItems.BLOOD_PILL.get())) {
            if (BLOOD_REMAINING.containsKey(player.getUUID()) || player.getHealth() >= player.getMaxHealth()) {
                player.sendSystemMessage(Component.translatable("message.myriad.supply_cannot_use"));
                return true;
            }
            BLOOD_REMAINING.put(player.getUUID(), BLOOD_TOTAL);
            data.setBuff("blood_pill", 8 * 20);
            consume(player, stack);
            playUse(player, ParticleTypes.HEART);
            player.sendSystemMessage(Component.translatable("message.myriad.blood_pill_used"));
            return true;
        }
        if (stack.is(ModItems.MANA_PILL.get())) {
            float maxMana = (float) player.getAttributeValue(ModAttributes.MAX_MANA);
            if (MANA_REMAINING.containsKey(player.getUUID()) || data.getMana() >= maxMana) {
                player.sendSystemMessage(Component.translatable("message.myriad.supply_cannot_use"));
                return true;
            }
            MANA_REMAINING.put(player.getUUID(), MANA_TOTAL);
            data.setBuff("mana_pill", 5 * 20);
            consume(player, stack);
            playUse(player, ParticleTypes.ENCHANT);
            player.sendSystemMessage(Component.translatable("message.myriad.mana_pill_used"));
            return true;
        }
        if (stack.is(ModItems.TRUE_SIGHT_CHARM.get())) {
            TRUE_SIGHT_UNTIL.put(player.getUUID(), player.level().getGameTime() + 10 * 20L);
            consume(player, stack);
            player.sendSystemMessage(Component.translatable("message.myriad.true_sight_active"));
            return true;
        }
        Faction faction = manager == null ? data.getFaction() : manager.factionOf(player);
        if (faction == null && TrainingMode.isEnabled()) faction = Faction.XUANXIAO;
        Vec3 pos = stack.is(ModItems.SCOUT_TOTEM.get())
                ? totemPlacementPoint(player) : placementPoint(player);
        if (faction == null || pos == null) {
            player.sendSystemMessage(Component.translatable("message.myriad.supply_no_ground"));
            return true;
        }
        if (stack.is(ModItems.SCOUT_TOTEM.get())) {
            limitOwned(player.getUUID(), Kind.WARD, 1);
            DEPLOYABLES.add(spawnDeployable(player.level(), player, faction, pos, Kind.WARD, WARD_DURATION));
            consume(player, stack);
            player.sendSystemMessage(Component.translatable("message.myriad.scout_totem_placed"));
            return true;
        }
        if (ownedCount(player.getUUID(), Kind.TRAP) >= 3) {
            limitOwned(player.getUUID(), Kind.TRAP, 2);
        }
        DEPLOYABLES.add(spawnDeployable(player.level(), player, faction, pos, Kind.TRAP, TRAP_DURATION));
        consume(player, stack);
        player.sendSystemMessage(Component.translatable("message.myriad.spirit_snare_placed"));
        return true;
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        tickRegeneration(level, now);
        if (now % 5 == 0) tickTotemPlacementPreview(level);
        if (now % 10 != 0) return;
        for (Deployable deployable : DEPLOYABLES) {
            if (deployable.visual != null) deployable.visual.setGlowingTag(false);
        }
        Iterator<Deployable> iterator = DEPLOYABLES.iterator();
        while (iterator.hasNext()) {
            Deployable deployable = iterator.next();
            if (deployable.expireTick <= now || deployable.visual == null || !deployable.visual.isAlive()) {
                discard(deployable);
                iterator.remove();
                continue;
            }
            if (deployable.kind == Kind.WARD) tickWard(level, deployable);
            else if (triggerTrap(level, deployable)) {
                discard(deployable);
                iterator.remove();
            } else {
                hideTrapFromEnemies(level, deployable);
            }
        }
        tickTrueSight(level);
    }

    private static void tickRegeneration(ServerLevel level, long now) {
        if (now % 20 != 0) return;
        for (ServerPlayer player : level.players()) {
            enforcePillLimit(player);
            UUID id = player.getUUID();
            Integer health = BLOOD_REMAINING.get(id);
            if (health != null) {
                int amount = Math.min(5, health);
                com.myriad.combat.CombatTelemetry.heal(player, amount, "blood_pill");
                if (health <= amount || player.getHealth() >= player.getMaxHealth()) {
                    BLOOD_REMAINING.remove(id);
                    PlayerData data = PlayerDataProvider.get(player);
                    if (data != null) data.setBuff("blood_pill", 0);
                } else {
                    BLOOD_REMAINING.put(id, health - amount);
                    PlayerData data = PlayerDataProvider.get(player);
                    if (data != null) data.setBuff("blood_pill", health - amount);
                }
            }
            Integer mana = MANA_REMAINING.get(id);
            if (mana != null) {
                PlayerData data = PlayerDataProvider.get(player);
                float max = (float) player.getAttributeValue(ModAttributes.MAX_MANA);
                int amount = Math.min(10, mana);
                data.setMana(Math.min(max, data.getMana() + amount));
                data.setBuff("mana_pill", Math.max(0, mana - amount));
                PlayerDataProvider.syncToClient(player);
                if (mana <= amount || data.getMana() >= max) {
                    MANA_REMAINING.remove(id);
                    data.setBuff("mana_pill", 0);
                }
                else MANA_REMAINING.put(id, mana - amount);
            }
        }
    }

    private static void tickWard(ServerLevel level, Deployable ward) {
        AABB area = new AABB(ward.pos, ward.pos).inflate(WARD_RADIUS);
        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, area,
                p -> p.isAlive() && enemyOf(ward, p))) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, true));
        }
        level.sendParticles(ParticleTypes.END_ROD, ward.pos.x, ward.pos.y + 0.6, ward.pos.z,
                2, 0.15, 0.25, 0.15, 0.01);
    }

    private static boolean triggerTrap(ServerLevel level, Deployable trap) {
        AABB area = new AABB(trap.pos, trap.pos).inflate(TRAP_RADIUS);
        List<ServerPlayer> targets = level.getEntitiesOfClass(ServerPlayer.class, area,
                p -> p.isAlive() && enemyOf(trap, p));
        if (targets.isEmpty()) return false;
        ServerPlayer target = targets.get(0);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2, true, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, true, false, true));
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, trap.pos.x, trap.pos.y + 0.3, trap.pos.z,
                28, 0.8, 0.4, 0.8, 0.04);
        level.playSound(null, trap.pos.x, trap.pos.y, trap.pos.z,
                SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
        target.sendSystemMessage(Component.translatable("message.myriad.spirit_snare_triggered"));
        return true;
    }

    /** 陷阱实体仍由服务端统一追踪，但每个刷新周期向敌方客户端撤回实体，友方保持可见。 */
    private static void hideTrapFromEnemies(ServerLevel level, Deployable trap) {
        if (trap.visual == null || !trap.visual.isAlive()) return;
        for (ServerPlayer viewer : level.players()) {
            GameManager manager = GameManager.get();
            Faction faction = manager == null ? null : manager.factionOf(viewer);
            if (faction != null && faction != trap.faction) {
                viewer.connection.send(new ClientboundRemoveEntitiesPacket(trap.visual.getId()));
            }
        }
    }

    private static void tickTrueSight(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            long until = TRUE_SIGHT_UNTIL.getOrDefault(player.getUUID(), 0L);
            if (until <= level.getGameTime()) {
                TRUE_SIGHT_UNTIL.remove(player.getUUID());
                continue;
            }
            GameManager manager = GameManager.get();
            Faction faction = manager == null ? null : manager.factionOf(player);
            if (faction == null) continue;
            for (Deployable deployable : DEPLOYABLES) {
                if (deployable.faction != faction && deployable.pos.distanceToSqr(player.position()) <= TRUE_SIGHT_RADIUS * TRUE_SIGHT_RADIUS) {
                    deployable.visual.setGlowingTag(true);
                    level.sendParticles(ParticleTypes.WITCH, deployable.pos.x, deployable.pos.y + 0.4, deployable.pos.z,
                            2, 0.2, 0.2, 0.2, 0.01);
                }
            }
            AABB area = player.getBoundingBox().inflate(15.0);
            for (ServerPlayer enemy : level.getEntitiesOfClass(ServerPlayer.class, area,
                    p -> p.isAlive() && manager.factionOf(p) != faction)) {
                enemy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, true));
            }
        }
    }

    private static Deployable spawnDeployable(ServerLevel level, ServerPlayer owner, Faction faction,
            Vec3 pos, Kind kind, int duration) {
        ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (display != null) {
            display.getSlot(0).set(new ItemStack(kind == Kind.WARD
                    ? ModItems.SCOUT_TOTEM.get() : ModItems.SPIRIT_SNARE.get()));
            display.setItemTransform(ItemDisplayContext.FIXED);
            display.setBillboardConstraints(BillboardConstraints.CENTER);
            display.setPos(pos.x, pos.y + (kind == Kind.WARD ? 0.7 : 0.2), pos.z);
            level.addFreshEntity(ModEntityTracker.markGenerated(display));
        }
        return new Deployable(owner.getUUID(), faction, pos, level.getGameTime() + duration, kind, display);
    }

    private static Vec3 placementPoint(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        int x = (int) Math.floor(player.getX() + look.x * 3.0);
        int z = (int) Math.floor(player.getZ() + look.z * 3.0);
        int startY = player.blockPosition().getY() + 2;
        ServerLevel level = player.level();
        for (int y = startY; y >= startY - 7; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (level.getBlockState(feet).isAir() && !level.getBlockState(feet.below()).isAir()) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        return null;
    }

    /** 准星选点：仅接受玩家视线 6 格内命中的实体方块顶面。 */
    private static Vec3 totemPlacementPoint(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        for (double distance = 0.125; distance <= WARD_CAST_RADIUS; distance += 0.125) {
            BlockPos hit = BlockPos.containing(start.add(direction.scale(distance)));
            if (!level.isLoaded(hit) || !level.getBlockState(hit).isSolid()) continue;
            BlockPos feet = hit.above();
            if (level.getBlockState(feet).isAir()) return Vec3.atBottomCenterOf(feet);
            return null;
        }
        return null;
    }

    /** 仅向手持侦灵图腾的本人发送施法范围和准星落点预览。 */
    private static void tickTotemPlacementPreview(ServerLevel level) {
        GameManager manager = GameManager.get();
        boolean playing = manager != null && manager.getPhase() == GameManager.Phase.PLAYING;
        if (!playing && !TrainingMode.isEnabled()) return;
        for (ServerPlayer player : level.players()) {
            if (!player.getMainHandItem().is(ModItems.SCOUT_TOTEM.get())
                    && !player.getOffhandItem().is(ModItems.SCOUT_TOTEM.get())) continue;
            double centerY = player.getY() + 0.08;
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2.0 * i / 24.0;
                level.sendParticles(player, ParticleTypes.ENCHANT, false, false,
                        player.getX() + Math.cos(angle) * WARD_CAST_RADIUS,
                        centerY,
                        player.getZ() + Math.sin(angle) * WARD_CAST_RADIUS,
                        1, 0, 0, 0, 0);
            }
            Vec3 target = totemPlacementPoint(player);
            if (target == null) continue;
            for (int i = 0; i < 12; i++) {
                double angle = Math.PI * 2.0 * i / 12.0;
                level.sendParticles(player, ParticleTypes.END_ROD, false, false,
                        target.x + Math.cos(angle) * 0.65,
                        target.y + 0.08,
                        target.z + Math.sin(angle) * 0.65,
                        1, 0, 0, 0, 0);
            }
            level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, false, false,
                    target.x, target.y + 0.18, target.z, 5, 0.16, 0.03, 0.16, 0.01);
        }
    }

    private static boolean enemyOf(Deployable deployable, ServerPlayer player) {
        GameManager manager = GameManager.get();
        return manager != null && manager.factionOf(player) != null
                && manager.factionOf(player) != deployable.faction;
    }

    private static boolean hasItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }

    private static int pillCount(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.BLOOD_PILL.get()) || stack.is(ModItems.MANA_PILL.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 两类丹药共享五瓶携带上限；处理拾取、指令或创造模式等非商店来源。 */
    private static void enforcePillLimit(ServerPlayer player) {
        int remaining = 5;
        boolean trimmed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(ModItems.BLOOD_PILL.get()) && !stack.is(ModItems.MANA_PILL.get())) continue;
            if (remaining <= 0) {
                stack.setCount(0);
                trimmed = true;
                continue;
            }
            if (stack.getCount() > remaining) {
                stack.setCount(remaining);
                trimmed = true;
            }
            remaining -= stack.getCount();
        }
        if (trimmed) player.sendSystemMessage(Component.translatable("message.myriad.pill_limit"));
    }

    private static void removeVisionTrinkets(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.SCOUT_TOTEM.get()) || stack.is(ModItems.TRUE_SIGHT_CHARM.get())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static int ownedCount(UUID owner, Kind kind) {
        int count = 0;
        for (Deployable d : DEPLOYABLES) if (d.owner.equals(owner) && d.kind == kind) count++;
        return count;
    }

    private static void limitOwned(UUID owner, Kind kind, int keep) {
        while (ownedCount(owner, kind) > keep) {
            for (int i = 0; i < DEPLOYABLES.size(); i++) {
                Deployable d = DEPLOYABLES.get(i);
                if (d.owner.equals(owner) && d.kind == kind) {
                    discard(d);
                    DEPLOYABLES.remove(i);
                    break;
                }
            }
        }
    }

    private static void consume(ServerPlayer player, ItemStack stack) {
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
    }

    private static void playUse(ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle) {
        ServerLevel level = player.level();
        level.sendParticles(particle, player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.35, 0.5, 0.35, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.HONEY_DRINK.value(),
                SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    private static void discard(Deployable deployable) {
        if (deployable.visual != null && deployable.visual.isAlive()) deployable.visual.discard();
    }

    /** 战争迷雾读取的本阵营侦灵图腾位置快照。 */
    public static List<Vec3> wardPositions(Faction faction) {
        if (faction == null) return List.of();
        List<Vec3> result = new ArrayList<>();
        for (Deployable deployable : DEPLOYABLES) {
            if (deployable.kind == Kind.WARD && deployable.faction == faction) result.add(deployable.pos);
        }
        return List.copyOf(result);
    }

    public static double wardVisionRadiusSqr() {
        return WARD_RADIUS * WARD_RADIUS;
    }

    public static double trueSightRadiusSqr() {
        return TRUE_SIGHT_RADIUS * TRUE_SIGHT_RADIUS;
    }

    public static boolean hasActiveTrueSight(ServerPlayer player) {
        return player != null && TRUE_SIGHT_UNTIL.getOrDefault(player.getUUID(), 0L)
                > player.level().getGameTime();
    }

    public static void clear() {
        for (Deployable deployable : DEPLOYABLES) discard(deployable);
        DEPLOYABLES.clear();
        BLOOD_REMAINING.clear();
        MANA_REMAINING.clear();
        TRUE_SIGHT_UNTIL.clear();
    }

    private enum Kind { WARD, TRAP }

    private record SupplyOffer(net.minecraft.world.item.Item item, int price, int count) { }

    private record Deployable(UUID owner, Faction faction, Vec3 pos, long expireTick,
            Kind kind, ItemDisplay visual) { }
}
