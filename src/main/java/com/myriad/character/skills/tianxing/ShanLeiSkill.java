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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 雷部·天刑 Q 技能【闪雷】：召唤天雷劈向目标落点，对落点周围 4 格敌人造成
 * 物理伤害；命中后自身攻速 +30%（3 秒）。
 */
public class ShanLeiSkill extends Skill {
   private static final double BLINK_RANGE = 6.0;
   private static final double AOE_RADIUS = 4.0;
   private static final int AS_BUFF_TICKS = 60;
   private static final Identifier AS_MOD = Identifier.parse("myriad:shanlei_as_buff");
   private static final Map<UUID, Long> AS_UNTIL = new HashMap<>();

   public ShanLeiSkill() {
      super("shanlei", 100, 54.0F);
   }

   public String levelEffects(int level) {
      int dmg = 15 + 5 * (level - 1);
      return "Lv." + level + "：伤害 " + dmg + "+" + (40 + 8 * (level - 1))
              + "%攻击力；闪烁6格，落地后攻速+30%（3秒）";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      Vec3 dir = player.getLookAngle().normalize();
      Vec3 start = player.getEyePosition();
      level.sendParticles(ParticleTypes.ELECTRIC_SPARK, start.x, start.y, start.z, 26, 0.4, 0.4, 0.4, 0.08);
      level.sendParticles(ParticleTypes.CRIT, start.x, start.y, start.z, 12, 0.3, 0.3, 0.3, 0.05);
      level.sendParticles(ParticleTypes.END_ROD, start.x, start.y, start.z, 10, 0.2, 0.2, 0.2, 0.04);
      Vec3 land = computeLanding(level, player, dir);
      Vec3 end = land.add(0.0, player.getEyeHeight() - 0.1, 0.0);
      int trailN = 24;

      for (int i = 1; i <= trailN; i++) {
         Vec3 p = start.lerp(end, (double)i / trailN);
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
         if (i % 3 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
         }

         if (i % 5 == 0) {
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
         }
      }

      player.teleportTo(land.x, land.y, land.z);
      player.resetFallDistance();
      drawSkyBolt(level, land);
      level.sendParticles(ParticleTypes.ELECTRIC_SPARK, land.x, land.y + 1.0, land.z, 40, 1.5, 1.0, 1.5, 0.12);
      level.sendParticles(ParticleTypes.CRIT, land.x, land.y + 1.0, land.z, 22, 1.0, 0.8, 1.0, 0.1);
      level.sendParticles(ParticleTypes.END_ROD, land.x, land.y + 1.0, land.z, 16, 0.8, 0.8, 0.8, 0.05);

      for (int i = 0; i < 44; i++) {
         double a = (Math.PI * 2) * i / 44.0;
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, land.x + Math.cos(a) * 4.0, land.y + 0.15, land.z + Math.sin(a) * 4.0, 1, 0.0, 0.0, 0.0, 0.0);
         if (i % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, land.x + Math.cos(a) * 4.0, land.y + 0.2, land.z + Math.sin(a) * 4.0, 1, 0.0, 0.05, 0.0, 0.01);
         }
      }

      level.playSound(null, land.x, land.y, land.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.2F);
      int lv = data.skillLevel("shanlei");
      float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
      float dmg = 60.0F + 20.0F * (lv - 1) + (1.6F + 0.32F * (lv - 1)) * ad;
      AABB box = new AABB(player.getX() - 4.0, player.getY() - 2.0, player.getZ() - 4.0, player.getX() + 4.0, player.getY() + 4.0, player.getZ() + 4.0);

      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != player && ex.isAlive() && !isAlly(player, ex))) {
         DamageCalc.dealSkillDamage(player, e, dmg, Kind.PHYSICAL, 0);
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 16, 0.3, 0.3, 0.3, 0.1);
         level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
      }

      AttributeInstance as = player.getAttribute(ModAttributes.ATTACK_SPEED);
      if (as != null) {
         as.removeModifier(AS_MOD);
         as.addTransientModifier(new AttributeModifier(AS_MOD,
                 0.3, Operation.ADD_MULTIPLIED_TOTAL));
      }

      AS_UNTIL.put(player.getUUID(), player.level().getGameTime() + 60L);
      data.setBuff("shanlei", 60);
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      Long until = AS_UNTIL.get(player.getUUID());
      if (until != null && player.level().getGameTime() >= until) {
         AS_UNTIL.remove(player.getUUID());
         AttributeInstance as = player.getAttribute(ModAttributes.ATTACK_SPEED);
         if (as != null) {
            as.removeModifier(AS_MOD);
         }
      }
   }

   private static void drawSkyBolt(ServerLevel level, Vec3 ground) {
      Vec3 top = ground.add(0.0, 18.0, 0.0);

      for (List<Vec3> path : buildBoltPaths(level, top, ground)) {
         drawThickBolt(level, path);
      }

      level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ground.x, ground.y + 0.2, ground.z, 12, 0.7, 0.3, 0.7, 0.08);

      for (int i = 0; i < 24; i++) {
         double a = (Math.PI * 2) * i / 24.0;
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ground.x + Math.cos(a) * 1.2, ground.y + 0.1, ground.z + Math.sin(a) * 1.2, 1, 0.0, 0.0, 0.0, 0.0);
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

   private static Vec3 computeLanding(ServerLevel level, ServerPlayer player, Vec3 dir) {
      Vec3 eye = player.getEyePosition();
      Vec3 target = eye.add(dir.scale(6.0));
      Vec3 blockHit = firstBlockHit(level, eye, target);
      if (blockHit != null) {
         target = blockHit.subtract(dir.scale(0.6));
      }

      double x = target.x;
      double z = target.z;
      double y = target.y;

      for (int i = 0; i <= 8; i++) {
         BlockPos feet = BlockPos.containing(x, y - 0.1, z);
         if (level.getBlockState(feet).isSolid()) {
            break;
         }

         y -= 0.5;
      }

      double feetY = Math.max(level.getMinY() + 1, y);

      for (int up = 0; up <= 8; up++) {
         double cy = feetY + up;
         BlockPos b1 = BlockPos.containing(x, cy, z);
         BlockPos b2 = BlockPos.containing(x, cy + 1.2, z);
         if (!level.getBlockState(b1).isSolid() && !level.getBlockState(b2).isSolid()) {
            return new Vec3(x, cy, z);
         }
      }

      return new Vec3(x, feetY, z);
   }

   private static Vec3 firstBlockHit(ServerLevel level, Vec3 from, Vec3 to) {
      double dist = from.distanceTo(to);
      int steps = Math.max(1, (int)Math.ceil(dist / 0.25));

      for (int i = 1; i <= steps; i++) {
         Vec3 p = from.lerp(to, (double)i / steps);
         BlockPos bp = BlockPos.containing(p.x, p.y, p.z);
         if (level.getBlockState(bp).isSolid()) {
            return p;
         }
      }

      return null;
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
}
