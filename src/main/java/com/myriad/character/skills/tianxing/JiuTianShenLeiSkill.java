package com.myriad.character.skills.tianxing;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 雷部·天刑 R 大招【九天神雷】：召唤多道天雷连续轰击大范围区域。
 */
public class JiuTianShenLeiSkill extends Skill {
   private static final int TOTAL_STRIKES = 12;
   private static final int STRIKE_INTERVAL = 10;
   private static final double RADIUS = 9.0;
   private static final double STRIKE_AOE = 2.0;
   private static final int PARALYZE_TICKS = 10;
   private static final double BOLT_HEIGHT = 18.0;
   private static final int BOLT_LIFETIME = 10;
   private static final Map<UUID, JiuTianShenLeiSkill.Cast> CASTS = new HashMap<>();
   private static final Map<UUID, List<JiuTianShenLeiSkill.Bolt>> ACTIVE_BOLTS = new HashMap<>();

   public JiuTianShenLeiSkill() {
      super("jiutianshenlei", 1400, 216.0F);
   }

   @Override
   public int getCooldownTicks(int level) {
      return Math.max(20, 1400 - Math.max(0, level - 1) * 100);
   }

   public boolean isUltimate() {
      return true;
   }

   public String levelEffects(int level) {
      return "Lv." + level + "：12道天雷（每秒2道），每道伤害 20+"
              + (40 + 15 * (level - 1)) + "%攻击力；麻痹0.5秒，施放期间免疫眩晕";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      JiuTianShenLeiSkill.Cast cast = new JiuTianShenLeiSkill.Cast();
      cast.remaining = TOTAL_STRIKES;
      cast.nextStrike = player.level().getGameTime() + 5L;
      cast.level = data.skillLevel("jiutianshenlei");
      cast.ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
      CASTS.put(player.getUUID(), cast);
      data.setBuff("jiutianshenlei", 110);
      ServerLevel level = player.level();
      level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.8F);
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      JiuTianShenLeiSkill.Cast cast = CASTS.get(player.getUUID());
      ServerLevel level = player.level();
      long now = level.getGameTime();
      if (cast != null) {
         player.removeEffect(MobEffects.SLOWNESS);
      }

      List<JiuTianShenLeiSkill.Bolt> bolts = ACTIVE_BOLTS.get(player.getUUID());
      if (bolts != null) {
         bolts.removeIf(b -> now >= b.expireTick());

         for (JiuTianShenLeiSkill.Bolt bolt : bolts) {
            for (List<Vec3> path : bolt.paths()) {
               drawThickBolt(level, path);
            }

            drawImpact(level, bolt.ground());
         }

         if (bolts.isEmpty()) {
            ACTIVE_BOLTS.remove(player.getUUID());
         }
      }

      if (cast != null) {
         if (now >= cast.nextStrike && cast.remaining > 0) {
            cast.remaining--;
            cast.strikeCount++;
            cast.nextStrike = now + 10L;
            Vec3 ground;
            if (cast.strikeCount % 3 == 0) {
               LivingEntity target = findTarget(level, player);
               if (target != null) {
                  ground = target.position().add(0.0, 0.2, 0.0);
               } else {
                  ground = randomPoint(player);
               }
            } else {
               ground = randomPoint(player);
            }

            List<List<Vec3>> paths = buildBoltPaths(level, ground.add(0.0, 18.0, 0.0), ground);
            ACTIVE_BOLTS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
               .add(new JiuTianShenLeiSkill.Bolt(paths, ground, now + 10L));
            strikeDamage(level, player, ground, cast.level, cast.ad);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.9F, 1.0F);
         }

         if (cast.remaining <= 0) {
            CASTS.remove(player.getUUID());
            data.setBuff("jiutianshenlei", 0);
         }
      }
   }

   private static LivingEntity findTarget(ServerLevel level, ServerPlayer player) {
      AABB box = new AABB(player.getX() - RADIUS, player.getY() - 6.0, player.getZ() - RADIUS,
              player.getX() + RADIUS, player.getY() + 6.0, player.getZ() + RADIUS);
      List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != player && ex.isAlive() && !isAlly(player, ex));
      if (candidates.isEmpty()) {
         return null;
      }

      LivingEntity playerTarget = null;
      LivingEntity nearest = null;
      double best = Double.MAX_VALUE;

      for (LivingEntity e : candidates) {
         if (playerTarget == null && e instanceof ServerPlayer) {
            playerTarget = e;
         }

         double d = e.distanceToSqr(player);
         if (d < best) {
            best = d;
            nearest = e;
         }
      }

      return playerTarget != null ? playerTarget : nearest;
   }

   private static Vec3 randomPoint(ServerPlayer player) {
      double ang = player.getRandom().nextDouble() * Math.PI * 2.0;
      double r = 2.0 + player.getRandom().nextDouble() * (RADIUS - 2.0);
      return new Vec3(player.getX() + Math.cos(ang) * r, player.getY() + 0.2, player.getZ() + Math.sin(ang) * r);
   }

   private static void strikeDamage(ServerLevel level, ServerPlayer player, Vec3 ground, int lv, float ad) {
      float dmg = 80.0F + (1.6F + 0.60F * (lv - 1)) * ad;
      double x = ground.x;
      double y = ground.y;
      double z = ground.z;
      AABB box = new AABB(x - 2.0, y - 2.0, z - 2.0, x + 2.0, y + 4.0, z + 2.0);

      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != player && ex.isAlive() && !isAlly(player, ex))) {
         DamageCalc.dealSkillDamage(player, e, dmg, Kind.PHYSICAL, 3);
         e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 4, false, false));
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 18, 0.3, 0.3, 0.3, 0.1);
         level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 10, 0.3, 0.3, 0.3, 0.04);
      }
   }

   private static List<List<Vec3>> buildBoltPaths(ServerLevel level, Vec3 from, Vec3 to) {
      List<List<Vec3>> paths = new ArrayList<>();
      int segments = 12;
      Vec3[] pts = new Vec3[segments + 1];
      pts[0] = from;
      pts[segments] = to;
      double totalDist = from.distanceTo(to);
      double lateral = Math.max(1.0, totalDist * 0.09);

      for (int i = 1; i < segments; i++) {
         Vec3 base = from.lerp(to, (double)i / segments);
         double bend = (level.getRandom().nextDouble() * 2.0 - 1.0) * lateral;
         double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
         pts[i] = base.add(Math.cos(angle) * bend, 0.0, Math.sin(angle) * bend);
      }

      List<Vec3> main = new ArrayList<>();

      for (Vec3 p : pts) {
         main.add(p);
      }

      paths.add(main);
      int branches = 1 + level.getRandom().nextInt(2);

      for (int b = 0; b < branches; b++) {
         int seg = 3 + level.getRandom().nextInt(Math.max(1, segments - 5));
         Vec3 start = pts[seg];
         double len = 2.0 + level.getRandom().nextDouble() * 3.5;
         double a = level.getRandom().nextDouble() * Math.PI * 2.0;
         double sl = level.getRandom().nextDouble() * 0.6 + 0.3;
         Vec3 end = start.add(Math.cos(a) * len * sl, -len * 0.6, Math.sin(a) * len * sl);
         List<Vec3> branch = new ArrayList<>();
         int bseg = 4;

         for (int i = 0; i <= bseg; i++) {
            branch.add(start.lerp(end, (double)i / bseg));
         }

         paths.add(branch);
      }

      return paths;
   }

   private static void drawThickBolt(ServerLevel level, List<Vec3> pts) {
      for (int i = 0; i < pts.size() - 1; i++) {
         Vec3 a = pts.get(i);
         Vec3 b = pts.get(i + 1);
         Vec3 seg = b.subtract(a);
         double dist = seg.length();
         Vec3 n = seg.normalize();
         Vec3 right = new Vec3(-n.z, 0.0, n.x);
         if (right.lengthSqr() < 0.001) {
            right = new Vec3(0.0, 0.0, 1.0);
         } else {
            right = right.normalize();
         }

         int n2 = Math.max(1, (int)Math.ceil(dist / 0.3));

         for (int k = 0; k <= n2; k++) {
            Vec3 p = a.add(seg.scale((double)k / n2));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.005);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x + right.x * 0.35, p.y, p.z + right.z * 0.35, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x - right.x * 0.35, p.y, p.z - right.z * 0.35, 1, 0.0, 0.0, 0.0, 0.0);
            if (k % 4 == 0) {
               level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
         }
      }
   }

   private static void drawImpact(ServerLevel level, Vec3 ground) {
      double x = ground.x;
      double y = ground.y;
      double z = ground.z;
      level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 10, 0.7, 0.3, 0.7, 0.08);

      for (int i = 0; i < 28; i++) {
         double a = (Math.PI * 2) * i / 28.0;
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x + Math.cos(a) * 1.2, y + 0.1, z + Math.sin(a) * 1.2, 1, 0.0, 0.0, 0.0, 0.0);
         if (i % 5 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, x + Math.cos(a) * 1.2, y + 0.4, z + Math.sin(a) * 1.2, 1, 0.0, 0.05, 0.0, 0.01);
         }
      }
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

   private record Bolt(List<List<Vec3>> paths, Vec3 ground, long expireTick) {
   }

   private static final class Cast {
      int remaining;
      long nextStrike;
      int level;
      float ad;
      int strikeCount = 0;
   }
}
