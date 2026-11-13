package com.myriad.game;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Path planning under NoAI, with a bounded replan cadence and ordinary collision movement. */
public final class NpcNavigation {
    public static final class Route {
        Path path; Vec3 goal; long nextPlan; int stuckTicks;
        public void clear(){path=null;goal=null;nextPlan=0;stuckTicks=0;}
    }
    public static void move(ServerLevel level,Mob mob,Route route,Vec3 goal,double step){
        long now=level.getGameTime();
        // This is the autonomous movement layer: do not scale entity velocity, gravity or external pushes.
        step*=Math.min(com.myriad.character.ability.AbilityRuntime.ordinarySlowRate(mob),
                com.myriad.character.skills.keheng.KehengTimeFields.managedNpcRate(mob,now));
        if (route.goal != null && route.goal.distanceToSqr(goal)>2.25) route.clear();
        if(now>=route.nextPlan && (route.path==null||route.path.isDone()||route.goal==null||route.goal.distanceToSqr(goal)>2.25)){
            route.path=mob.getNavigation().createPath(net.minecraft.core.BlockPos.containing(goal),0);
            // The first node is normally the cell the mob already occupies.
            // Returning to its centre on every replan causes a visible backstep.
            if (route.path != null && route.path.getNodeCount() > 1
                    && route.path.getNode(0).asBlockPos().equals(mob.blockPosition())) route.path.advance();
            route.goal=goal;route.nextPlan=now+12;
        }
        Vec3 aim=goal;
        if ((route.path == null || route.path.isDone()) && !clearWalk(mob, goal)) {
            // A missing vanilla path must not freeze a NoAI minion forever. Try a
            // short left/right sidestep, then request a fresh route next tick.
            Vec3 forward=goal.subtract(mob.position()).multiply(1,0,1);
            if(forward.lengthSqr()>.01){
                forward=forward.normalize();Vec3 side=new Vec3(-forward.z,0,forward.x);
                double sign=((mob.getId()+now/8)&1)==0?1:-1;
                Vec3 detour=side.scale(step*sign).add(forward.scale(step*.35));
                if(level.noCollision(mob,mob.getBoundingBox().move(detour))
                        && !level.noCollision(mob,mob.getBoundingBox().move(detour).move(0,-.6,0))){
                    mob.move(MoverType.SELF,detour);
                    if(mob instanceof LaneMinionEntity minion)minion.setMoving(true);
                }
            }
            route.path=null;route.nextPlan=now+2;return;
        }
        if(route.path!=null&&!route.path.isDone()){
            aim=route.path.getNextEntityPos(mob);
            if(mob.position().multiply(1,0,1).distanceToSqr(aim.multiply(1,0,1))<.04){
                route.path.advance();if(!route.path.isDone())aim=route.path.getNextEntityPos(mob);else aim=goal;
            }
        }
        Vec3 dir=aim.subtract(mob.position()).multiply(1,0,1);
        if(dir.horizontalDistanceSqr()<.01)return;
        Vec3 motion=dir.normalize().scale(Math.min(step,dir.length()));
        Vec3 before=mob.position();
        mob.move(MoverType.SELF,motion);
        if(before.distanceToSqr(mob.position())<step*step*.1){
            route.stuckTicks++;
            Vec3 up=motion.add(0,1,0);
            if(level.noCollision(mob,mob.getBoundingBox().move(up)))mob.move(MoverType.SELF,up);
            route.path=null;
            route.nextPlan=now+2;
        } else route.stuckTicks=0;
        if(mob instanceof LaneMinionEntity minion)minion.setMoving(before.distanceToSqr(mob.position())>.0001);
        float yaw=(float)(Math.toDegrees(Math.atan2(dir.z,dir.x))-90);
        mob.setYRot(yaw);mob.setYBodyRot(yaw);mob.setYHeadRot(yaw);
    }
    /** Sweep the actual body along a level segment before cutting a lane corner. */
    public static boolean clearWalk(Mob mob, Vec3 goal) {
        Vec3 delta = goal.subtract(mob.position());
        if (Math.abs(delta.y) > 1.0) return false;
        int steps = Math.max(1, (int)Math.ceil(delta.length() / .25));
        for (int i = 1; i <= steps; i++) {
            Vec3 offset = delta.scale((double)i / steps);
            var box = mob.getBoundingBox().move(offset);
            if (!mob.level().noCollision(mob, box)
                    || mob.level().noCollision(mob, box.move(0, -.6, 0))) return false;
        }
        return true;
    }
    public static boolean visible(Mob source,net.minecraft.world.entity.LivingEntity target){
        if (!(target instanceof DefenseTowerEntity)) return source.hasLineOfSight(target);
        Vec3 eye=source.getEyePosition(); var box=target.getBoundingBox();
        Vec3 center=new Vec3(target.getX(),Math.max(box.minY+.2,Math.min(box.maxY-.2,eye.y)),target.getZ());
        Vec3 edge=box.clip(eye,center).orElse(center);
        Vec3 end=edge.add(eye.subtract(edge).normalize().scale(.05));
        return source.level().clip(new net.minecraft.world.level.ClipContext(eye,end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,source))
                .getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    public static void separate(ServerLevel level,LaneMinionEntity mob){
        Vec3 correction=Vec3.ZERO;
        for(LaneMinionEntity other:level.getEntitiesOfClass(LaneMinionEntity.class,mob.getBoundingBox().inflate(1.8),e->e!=mob&&e.isAlive())){
            Vec3 delta=mob.position().subtract(other.position()).multiply(1,0,1);
            double desired=(mob.getBbWidth()+other.getBbWidth())*.5+.12, distance=delta.length();
            if(distance>=desired)continue;
            if(distance<.001){double angle=Math.min(mob.getId(),other.getId())*2.399963;delta=new Vec3(Math.cos(angle),0,Math.sin(angle)).scale(mob.getId()<other.getId()?1:-1);}
            correction=correction.add(delta.normalize().scale(Math.min(.10,(desired-distance)*.20)));
        }
        // Towers have no vanilla push AI. Keep a real body gap so minions cannot
        // stand inside the guardian and become visually/physically untargetable.
        for(DefenseTowerEntity tower:level.getEntitiesOfClass(DefenseTowerEntity.class,
                mob.getBoundingBox().inflate(2.8),e->e.isAlive())){
            Vec3 delta=mob.position().subtract(tower.position()).multiply(1,0,1);
            double desired=(mob.getBbWidth()+tower.getBbWidth())*.5+.65,distance=delta.length();
            if(distance>=desired)continue;
            if(distance<.001){double angle=mob.getId()*2.399963;delta=new Vec3(Math.cos(angle),0,Math.sin(angle));}
            correction=correction.add(delta.normalize().scale(Math.min(.18,(desired-distance)*.35)));
        }
        if(correction.lengthSqr()>.0144)correction=correction.normalize().scale(.12);
        if(correction.lengthSqr()>.000001)mob.move(MoverType.SELF,correction);
    }
}
