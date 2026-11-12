package com.myriad.character.skills.fenjue;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 火部·焚诀 Q 技能【烈焰斩】：以自身为圆心、半径 5 格的 120° 扇形烈焰，
 * 单层对角烈焰从玩家 y+4 斜斩至 y-1（弧度向前），造成 14+60%攻击力 物理伤害；
 * 命中敌人附加灼烧（每 0.5 秒造成施法者 10% 物攻的真实伤害，持续 3 秒）。
 * 灼烧为跨技能通用效果（大招【焚天火海】也复用）。
 */
public class LieYanZhanSkill extends Skill {
   private static final double RANGE = 5.0;
   private static final double HALF_ANGLE = Math.toRadians(60.0);
   private static final Map<UUID, LieYanZhanSkill.Burn> BURNS = new HashMap<>();

   public LieYanZhanSkill() {
      super("lieyanzhan", 120, 72.0F);
   }

   public String levelEffects(int level) {
      return "Lv." + level + "：自身为中心120°扇形（半径5格），伤害 "
              + (14 + 5 * (level - 1)) + "+" + (60 + 5 * (level - 1))
              + "%攻击力 物理；命中附加灼烧（每0.5秒造成10%物攻真实伤害，3秒）";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      Vec3 look = player.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      if (flat.lengthSqr() < 0.001) {
         flat = new Vec3(0.0, 0.0, 1.0);
      }

      flat = flat.normalize();
      Vec3 left = new Vec3(flat.z, 0.0, -flat.x);
      float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
      int lv = Math.max(1, data.skillLevel("lieyanzhan"));
      float dmg = 56.0F + 20.0F * (lv - 1) + (2.40F + 0.20F * (lv - 1)) * ad;
      Vec3 pos = player.position();
      AABB box = new AABB(pos.x - 5.0, pos.y - 2.0, pos.z - 5.0, pos.x + 5.0, pos.y + 3.0, pos.z + 5.0);

      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != player && ex.isAlive() && !isAlly(player, ex))) {
         Vec3 to = new Vec3(e.getX() - pos.x, 0.0, e.getZ() - pos.z);
         double dist = to.length();
         if (!(dist > 5.0) && (!(dist > 0.001) || !(Math.acos(to.normalize().dot(flat)) > HALF_ANGLE))) {
            DamageCalc.dealSkillDamage(player, e, dmg, Kind.PHYSICAL, 0);
            applyBurn(player, e, 60);
            level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.001, 0.001, 0.001, 0.04);
         }
      }

      level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.0F);
      level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.2F);
      Vec3 leftDir = flat.scale(Math.cos(-HALF_ANGLE)).add(left.scale(Math.sin(-HALF_ANGLE)));
      Vec3 rightDir = flat.scale(Math.cos(HALF_ANGLE)).add(left.scale(Math.sin(HALF_ANGLE)));
      Vec3 start = new Vec3(pos.x + leftDir.x * 5.0, pos.y + 4.0, pos.z + leftDir.z * 5.0);
      Vec3 end = new Vec3(pos.x + rightDir.x * 5.0, pos.y - 1.0, pos.z + rightDir.z * 5.0);
      int n = 48;
      double curve = 2.4;

      for (int i = 0; i <= n; i++) {
         double t = (double)i / n;
         Vec3 p = start.lerp(end, t);
         double f = curve * Math.sin(Math.PI * t);
         level.sendParticles(ParticleTypes.FLAME, p.x + flat.x * f, p.y, p.z + flat.z * f, 2, 0.001, 0.001, 0.001, 0.03);
      }
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      tickBurn(player);
   }

   public static void applyBurn(ServerPlayer caster, LivingEntity target, int durationTicks) {
      if (target != null && com.myriad.combat.SkillTargets.alive(target)) {
         long now = caster.level().getGameTime();
         LieYanZhanSkill.Burn b = new LieYanZhanSkill.Burn();
         b.caster = caster.getUUID();
         b.until = now + durationTicks;
         b.lastTick = now;
         BURNS.put(target.getUUID(), b);
      }
   }

   public static void tickBurn(ServerPlayer player) {
      long now = player.level().getGameTime();
      Iterator<Entry<UUID, LieYanZhanSkill.Burn>> it = BURNS.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, LieYanZhanSkill.Burn> entry = it.next();
         LieYanZhanSkill.Burn b = entry.getValue();
         if (b.caster.equals(player.getUUID())) {
            if (now >= b.until) {
               it.remove();
            } else if (now - b.lastTick >= 10L) {
               if (player.level().getEntity(entry.getKey()) instanceof LivingEntity target && com.myriad.combat.SkillTargets.alive(target)) {
                  b.lastTick = now;
                  float ad = (float) player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
                  DamageCalc.dealSkillDamage(player, target, ad * .10F, Kind.TRUE, 4);
                  player.level()
                     .sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
               } else {
                  it.remove();
               }
            }
         }
      }
   }

   public static void clearFor(UUID playerId) {
      BURNS.values().removeIf(b -> b.caster.equals(playerId));
   }

   private static boolean isAlly(ServerPlayer caster, LivingEntity other) {
      if (other instanceof ServerPlayer otherPlayer) {
         GameManager manager = GameManager.get();
         if (manager == null) {
            return false;
         }

         Faction a = manager.factionOf(caster);
         Faction b = manager.factionOf(otherPlayer);
         return a != null && a == b;
      } else {
         return false;
      }
   }

   private static final class Burn {
      UUID caster;
      long until;
      long lastTick;
   }
}
