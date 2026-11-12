package com.myriad.character.skills.xuanchen;

import com.myriad.character.Skill;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.PlacementUtil;
import com.myriad.game.ModState;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team.CollisionRule;
import net.minecraft.world.scores.Team.Visibility;

/**
 * 玄阵·墨尘 W 技能【传送阵】：布阵模式（准星预览半径 1 格圆），左键释放/右键取消；
 * 释放后在原地与释放点各生成一个传送阵（两两绑定互通，队友右键可传送）。
 */
public class ChuanSongZhenSkill extends Skill {
   public static final int DURATION_TICKS = 160;
   public static final int MAX_ARRAYS = 4;
   public static final double USE_RANGE = 20.0;
   public static final double PLACE_RANGE = 25.0;
   private static final double PLACE_STEP = 0.25;
   private static final int TELEPORT_CD_TICKS = 40;
   private static final double AIM_RANGE = 6.0;
   public static final String ARRAY_TEAM = "myriad_array";
   private static final int DEFAULT_GLOW = -10027009;
   private static final int HIGHLIGHT_GLOW = -205;
   private static final Map<UUID, List<ChuanSongZhenSkill.ArrayEnt>> ARRAYS = new HashMap<>();
   private static final Map<UUID, Long> TELEPORT_CD = new HashMap<>();
   private static final Map<ResourceKey<?>, Vec3> CORE_POS = new HashMap<>();
   private static final Map<ResourceKey<?>, Vec3> PORTAL_POS = new HashMap<>();

   public ChuanSongZhenSkill() {
      super("chuansongzhen", 800, 180.0F);
   }

   @Override
   public int getCooldownTicks(int level) {
      return Math.max(20, 800 - Math.max(0, level - 1) * 40);
   }

   public String levelEffects(int level) {
      return "Lv." + level + "：进入布阵（25格内预览半径1格圆，落点必须在方块上），左键释放后原地与目标点各生成传送阵（两两绑定互通，存在8秒，阵下圆环随时间缩小表示剩余时间，最多2组）；右键传送至绑定阵，指向传送阵时高亮其目标（传送有2秒公共冷却）";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      if (data.getPlacementType() == 2) {
         cancel(player, data);
      }

      data.setPlacementType(2);
      PlayerDataProvider.syncToClient(player);
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.2F);
   }

   private static ArmorStand spawnStand(ServerLevel level, Vec3 pos) {
      ArmorStand stand = (ArmorStand)EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.MOB_SUMMONED);
      if (stand == null) {
         return null;
      }

      stand.setInvisible(true);
      stand.setNoGravity(true);
      stand.setNoBasePlate(true);
      stand.setInvulnerable(true);
      stand.setPos(pos.x, pos.y, pos.z);
      level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(stand));
      Scoreboard sb = level.getScoreboard();
      PlayerTeam team = sb.getPlayerTeam("myriad_array");
      if (team == null) {
         team = sb.addPlayerTeam("myriad_array");
         team.setNameTagVisibility(Visibility.NEVER);
         team.setCollisionRule(CollisionRule.NEVER);
         team.setSeeFriendlyInvisibles(false);
      }

      sb.addPlayerToTeam(stand.getStringUUID(), team);
      return stand;
   }

   private static ItemDisplay spawnVisual(ServerLevel level, Vec3 pos) {
      if (EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof ItemDisplay display) {
         display.getSlot(0).set(new ItemStack(Items.ENDER_EYE));
         display.setBillboardConstraints(BillboardConstraints.CENTER);
         display.setGlowColorOverride(-10027009);
         display.setPos(pos.x, pos.y, pos.z);
         level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(display));
         return display;
      } else {
         return null;
      }
   }

   private static void removeEnt(ChuanSongZhenSkill.ArrayEnt ent) {
      if (ent.stand != null && ent.stand.isAlive()) {
         ent.stand.discard();
      }

      if (ent.visual != null && ent.visual.isAlive()) {
         ent.visual.discard();
      }
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      if (data.getPlacementType() == 2 && player.tickCount % 4 == 0) {
         Vec3 center = placementPoint(player);
         if (center != null) {
            drawRingTo(level, player, center, 1.0, 16, ParticleTypes.SCULK_SOUL);
            drawRingTo(level, player, center, 0.7, 10, ParticleTypes.END_ROD);
            drawRingTo(level, player, center, 1.4, 20, ParticleTypes.SOUL);
            level.sendParticles(player, ParticleTypes.SOUL, false, false, center.x, center.y + 0.5, center.z, 3, 0.15, 0.4, 0.15, 0.02);
         }
      }

      List<ChuanSongZhenSkill.ArrayEnt> list = ARRAYS.get(player.getUUID());
      if (list != null && !list.isEmpty()) {
         long now = level.getGameTime();
         list.removeIf(entx -> {
            if (now >= entx.expireTick) {
               removeEnt(entx);
               return true;
            }

            if (entx.stand != null && entx.stand.isAlive()) {
               Vec3 px = entx.stand.position();
               if (player.tickCount % 4 == 0) {
                  double frac = (entx.expireTick - now) / 160.0;
                  double r = 1.8 * frac;
                  if (r > 0.06) {
                     int n = Math.max(8, (int)(r * 14.0));
                     drawRing(level, px, r, n, ParticleTypes.SCULK_SOUL);
                     drawRing(level, px, r * 0.65, Math.max(6, n / 2), ParticleTypes.END_ROD);
                  }
               }

               if (player.tickCount % 10 == 0) {
                  drawRing(level, px, 0.9, 12, ParticleTypes.SCULK_SOUL);
                  level.sendParticles(ParticleTypes.SOUL, px.x, px.y + 0.6, px.z, 2, 0.1, 0.2, 0.1, 0.01);
               }
            }

            return false;
         });
         if (list.isEmpty()) {
            ARRAYS.remove(player.getUUID());
         } else {
            ChuanSongZhenSkill.ArrayEnt aimed = findAimedArray(player, list);

            for (ChuanSongZhenSkill.ArrayEnt ent : list) {
               if (ent.visual != null && ent.visual.isAlive()) {
                  ent.visual.setGlowingTag(false);
                  ent.visual.setGlowColorOverride(-10027009);
               }
            }

            if (aimed != null && aimed.partnerId != null) {
               for (ChuanSongZhenSkill.ArrayEnt ent : list) {
                  if (ent.stand != null && ent.stand.isAlive() && ent.stand.getUUID().equals(aimed.partnerId)) {
                     if (ent.visual != null && ent.visual.isAlive()) {
                        ent.visual.setGlowingTag(true);
                        ent.visual.setGlowColorOverride(-205);
                     }

                     if (player.tickCount % 8 == 0) {
                        Vec3 p = ent.stand.position();
                        drawRing(level, p, 0.9, 12, ParticleTypes.END_ROD);
                        level.sendParticles(ParticleTypes.SOUL, p.x, p.y + 0.7, p.z, 2, 0.1, 0.2, 0.1, 0.01);
                     }
                  }
               }
            }
         }
      }
   }

   private static ChuanSongZhenSkill.ArrayEnt findAimedArray(
      ServerPlayer player, List<ChuanSongZhenSkill.ArrayEnt> list
   ) {
      Vec3 origin = player.getEyePosition();
      Vec3 dir = player.getLookAngle();
      if (dir.lengthSqr() < 1.0E-6) {
         return null;
      }

      dir = dir.normalize();
      ChuanSongZhenSkill.ArrayEnt best = null;
      double bestT = 7.0;

      for (ChuanSongZhenSkill.ArrayEnt ent : list) {
         if (ent.stand != null && ent.stand.isAlive()) {
            double t = rayAabb(origin, dir, ent.stand.getBoundingBox());
            if (t >= 0.0 && t <= 6.0 && t < bestT) {
               bestT = t;
               best = ent;
            }
         }
      }

      return best;
   }

   private static double rayAabb(Vec3 origin, Vec3 dir, AABB box) {
      double[] o = new double[]{origin.x, origin.y, origin.z};
      double[] d = new double[]{dir.x, dir.y, dir.z};
      double[] min = new double[]{box.minX, box.minY, box.minZ};
      double[] max = new double[]{box.maxX, box.maxY, box.maxZ};
      double tNear = Double.NEGATIVE_INFINITY;
      double tFar = Double.POSITIVE_INFINITY;

      for (int i = 0; i < 3; i++) {
         if (Math.abs(d[i]) < 1.0E-8) {
            if (o[i] < min[i] || o[i] > max[i]) {
               return -1.0;
            }
         } else {
            double t1 = (min[i] - o[i]) / d[i];
            double t2 = (max[i] - o[i]) / d[i];
            if (t1 > t2) {
               double tmp = t1;
               t1 = t2;
               t2 = tmp;
            }

            tNear = Math.max(tNear, t1);
            tFar = Math.min(tFar, t2);
            if (tNear > tFar) {
               return -1.0;
            }
         }
      }

      return tNear;
   }

   private static Vec3 placementPoint(ServerPlayer player) {
      return PlacementUtil.pointOnGround(player, 25.0);
   }

   public static void confirm(ServerPlayer player, PlayerData data) {
      if (data.getPlacementType() == 2) {
         ServerLevel level = player.level();
         Vec3 center = placementPoint(player);
         if (center != null) {
            List<ChuanSongZhenSkill.ArrayEnt> list = ARRAYS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());

            while (list.size() + 2 > 4) {
               removeEnt(list.remove(0));
            }

            ChuanSongZhenSkill.ArrayEnt a = addArray(level, list, player.getUUID(), player.position().add(0.0, 0.05, 0.0));
            ChuanSongZhenSkill.ArrayEnt b = addArray(level, list, player.getUUID(), center);
            if (a.stand != null) {
               b.partnerId = a.stand.getUUID();
            }

            if (b.stand != null) {
               a.partnerId = b.stand.getUUID();
            }

            data.setPlacementType(0);
            PlayerDataProvider.syncToClient(player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.1F);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.1F);
            drawRing(level, player.position(), 1.2, 20, ParticleTypes.SCULK_SOUL);
            drawRing(level, player.position(), 0.8, 12, ParticleTypes.END_ROD);
            drawRing(level, center, 1.2, 20, ParticleTypes.SCULK_SOUL);
            drawRing(level, center, 0.8, 12, ParticleTypes.END_ROD);
         }
      }
   }

   public static void cancel(ServerPlayer player, PlayerData data) {
      if (data.getPlacementType() == 2) {
         data.setPlacementType(0);
         data.setCooldown("chuansongzhen", 0);
         PlayerDataProvider.syncToClient(player);
      }
   }

   private static ChuanSongZhenSkill.ArrayEnt addArray(
      ServerLevel level, List<ChuanSongZhenSkill.ArrayEnt> list, UUID owner, Vec3 pos
   ) {
      ChuanSongZhenSkill.ArrayEnt ent = new ChuanSongZhenSkill.ArrayEnt();
      ent.owner = owner;
      ent.expireTick = level.getGameTime() + 160L;
      ent.stand = spawnStand(level, pos);
      ent.visual = spawnVisual(level, pos.add(0.0, 0.8, 0.0));
      list.add(ent);
      return ent;
   }

   public static boolean nearOwnArray(ServerPlayer player) {
      List<ChuanSongZhenSkill.ArrayEnt> list = ARRAYS.get(player.getUUID());
      if (list != null && !list.isEmpty()) {
         Vec3 p = player.position();

         for (ChuanSongZhenSkill.ArrayEnt ent : list) {
            if (ent.stand != null && ent.stand.isAlive() && ent.stand.position().distanceToSqr(p) <= 400.0) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static Vec3 corePosition(ServerLevel level) {
      return cachedSite(level, true);
   }

   public static Vec3 portalPosition(ServerLevel level) {
      return cachedSite(level, false);
   }

   public static void cacheCorePosition(ServerLevel level, Vec3 pos) {
      CORE_POS.put(level.dimension(), pos);
   }

   public static void cachePortalPosition(ServerLevel level, Vec3 pos) {
      PORTAL_POS.put(level.dimension(), pos);
   }

   private static Vec3 cachedSite(ServerLevel level, boolean isCore) {
      ResourceKey<?> key = level.dimension();
      Vec3 cached = isCore ? CORE_POS.get(key) : PORTAL_POS.get(key);
      return cached != null ? cached : null;
   }

   public static void onInteract(ServerPlayer player, ArmorStand stand) {
      if (ModState.isEnabled()) {
         if (!ZhouTianFuZhenSkill.isChanneling(player)) {
            UUID standId = stand.getUUID();
            UUID owner = null;
            ChuanSongZhenSkill.ArrayEnt thisEnt = null;
            List<ChuanSongZhenSkill.ArrayEnt> ownerList = null;

            label104:
            for (Entry<UUID, List<ChuanSongZhenSkill.ArrayEnt>> e : ARRAYS.entrySet()) {
               Iterator my = e.getValue().iterator();

               while (true) {
                  if (my.hasNext()) {
                     ChuanSongZhenSkill.ArrayEnt ent = (ChuanSongZhenSkill.ArrayEnt)my.next();
                     if (ent.stand == null || !ent.stand.getUUID().equals(standId) || !ent.stand.isAlive()) {
                        continue;
                     }

                     owner = e.getKey();
                     thisEnt = ent;
                     ownerList = e.getValue();
                  }

                  if (owner != null) {
                     break label104;
                  }
                  break;
               }
            }

            if (owner != null && thisEnt != null) {
               ServerLevel level = player.level();
               if (!owner.equals(player.getUUID())) {
                  GameManager manager = GameManager.get();
                  Faction my = manager == null ? null : manager.factionOf(player);
                  Faction ownerF = null;
                  if (manager != null && level.getPlayerByUUID(owner) instanceof ServerPlayer ownerPlayer) {
                     ownerF = manager.factionOf(ownerPlayer);
                  }

                  if (my == null || ownerF == null || my != ownerF) {
                     player.sendSystemMessage(Component.translatable("message.myriad.array_no_dest"));
                     return;
                  }
               }

               long now = level.getGameTime();
               Long cd = TELEPORT_CD.get(player.getUUID());
               if (cd != null && now < cd) {
                  player.sendSystemMessage(Component.translatable("message.myriad.array_cd"));
               } else {
                  TELEPORT_CD.put(player.getUUID(), now + 40L);
                  Vec3 dest = null;
                  if (thisEnt.partnerId != null) {
                     for (ChuanSongZhenSkill.ArrayEnt ent : ownerList) {
                        if (ent.stand != null && ent.stand.isAlive() && ent.stand.getUUID().equals(thisEnt.partnerId)) {
                           dest = ent.stand.position();
                           break;
                        }
                     }
                  }

                  if (dest == null) {
                     dest = portalPosition(level);
                  }

                  if (dest == null) {
                     player.sendSystemMessage(Component.translatable("message.myriad.array_no_dest"));
                  } else {
                     double destX = dest.x;
                     double destY = dest.y;
                     double destZ = dest.z;
                     player.teleportTo(destX, destY + 1.0, destZ);
                     player.resetFallDistance();
                     level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
                     level.playSound(null, destX, destY, destZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
                     drawRing(level, new Vec3(destX, destY, destZ), 1.0, 20, ParticleTypes.SCULK_SOUL);
                     drawRing(level, new Vec3(destX, destY, destZ), 0.7, 12, ParticleTypes.END_ROD);
                  }
               }
            }
         }
      }
   }

   private static void drawRing(ServerLevel level, Vec3 center, double radius, int count, ParticleOptions particle) {
      for (int i = 0; i < count; i++) {
         double a = (Math.PI * 2) * i / count;
         level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private static void drawRingTo(ServerLevel level, ServerPlayer player, Vec3 center, double radius, int count, ParticleOptions particle) {
      for (int i = 0; i < count; i++) {
         double a = (Math.PI * 2) * i / count;
         level.sendParticles(
            player, particle, false, false, center.x + Math.cos(a) * radius, center.y + 0.05, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0
         );
      }
   }

   public static void clearFor(UUID owner) {
      List<ChuanSongZhenSkill.ArrayEnt> list = ARRAYS.remove(owner);
      if (list != null) {
         for (ChuanSongZhenSkill.ArrayEnt ent : list) {
            removeEnt(ent);
         }
      }
   }

   private static final class ArrayEnt {
      ArmorStand stand;
      ItemDisplay visual;
      UUID owner;
      UUID partnerId;
      long expireTick;
   }
}
