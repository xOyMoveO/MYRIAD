package com.myriad.character.skills.shuo;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.game.VisionSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import com.myriad.character.ability.AbilityRuntime;
import net.minecraft.world.entity.LivingEntity;
/** Authored basic/skill recipes; functional reticles survive, legacy generic skill art stays retired. */
public final class ShuoVisuals {
    private ShuoVisuals(){}
    public static boolean allowsSkill(int kind){return kind==RETICLE||kind==INVALID;}
    public static AbilityVfxPayload basicRecipe(int owner,Vec3 eyes,Vec3 forward,boolean drawn,long tick){
        return basicRecipe(owner,eyes,forward,drawn,com.myriad.network.ShuoViewPayload.DRAW,tick);
    }
    public static AbilityVfxPayload basicRecipe(int owner,Vec3 eyes,Vec3 forward,boolean drawn,int action,long tick){
        var f=forward.lengthSqr()<1E-8?new Vec3(0,0,1):forward.normalize();
        var at=eyes.add(f.scale(1.25)).add(0,-.38,0);
        int variant=!drawn?1:action==com.myriad.network.ShuoViewPayload.BASIC_RISE?2:action==com.myriad.network.ShuoViewPayload.BASIC_SWEEP?3:0;
        return new AbilityVfxPayload(owner,-1,10,variant,ARC,at,at.add(f),2.9f,ShuoVfx.BASIC,variant>=2?8:drawn?6:7,tick,false);
    }
    public static void basic(ServerPlayer p,boolean wasDrawn,int action){
        var packet=basicRecipe(p.getId(),p.getEyePosition(),p.getLookAngle(),wasDrawn,action,p.level().getGameTime());
        for(var viewer:p.level().players())if(viewer==p||viewer.position().distanceToSqr(packet.from())<48*48
            &&VisionSystem.canSee(viewer,p)&&VisionSystem.canSeePosition(viewer,packet.from()))PacketDistributor.sendToPlayer(viewer,packet);
    }
    /** New authored skill recipes bypass the retired generic theme-10 effects deliberately. */
    public static AbilityVfxPayload recipe(int owner,int anchor,int index,int signal,Vec3 from,Vec3 to,float radius,int life,long now){
        int kind=switch(signal){case ShuoSkillVfx.CHARGING->CHARGE;case ShuoSkillVfx.LEFT,ShuoSkillVfx.RIGHT,ShuoSkillVfx.DOWN->ARC;
            case ShuoSkillVfx.TRACE,ShuoSkillVfx.CHASE,ShuoSkillVfx.RIFT->BEAM;case ShuoSkillVfx.GUARDING,ShuoSkillVfx.MARK->SHIELD;default->BURST;};
        return new AbilityVfxPayload(owner,anchor,10,index,kind,from,to,radius,signal,life,now,false);
    }
    public static void send(ServerPlayer p,AbilityVfxPayload packet){
        if(!packet.valid())return;
        for(var viewer:p.level().players()){
            if(viewer.position().distanceToSqr(packet.from())>Math.pow(48+packet.radius(),2))continue;
            if(viewer!=p&&(!VisionSystem.canSee(viewer,p)||!VisionSystem.canSeePosition(viewer,packet.from())))continue;
            if(packet.anchor()>=0&&p.level().getEntity(packet.anchor()) instanceof LivingEntity t&&!VisionSystem.canSee(viewer,t))continue;
            PacketDistributor.sendToPlayer(viewer,packet);
        }
    }
    public static void cancel(ServerPlayer p,int index){
        if(index<0)return;
        var packet=new AbilityVfxPayload(p.getId(),-1,10,index,CANCEL,Vec3.ZERO,Vec3.ZERO,0,1,1,p.level().getGameTime(),false);
        for(var viewer:p.level().players())PacketDistributor.sendToPlayer(viewer,packet);
    }
    public static void charge(AbilityRuntime.Cast c,int ticks){
        var at=c.player.position();send(c.player,recipe(c.player.getId(),c.player.getId(),c.skill.index(),ShuoSkillVfx.CHARGING,at,at.add(c.forward(1)),1,ticks,c.now()));
    }
    public static void guard(ServerPlayer p){
        var at=p.position().add(0,1.05,0);var f=p.getLookAngle().multiply(1,0,1).normalize();
        if(f.lengthSqr()<.01)f=new Vec3(0,0,1);
        send(p,recipe(p.getId(),p.getId(),3,ShuoSkillVfx.GUARDING,at,at.add(f),1.2f,2,p.level().getGameTime()));
    }
    public static void slash(AbilityRuntime.Cast c,double range,double degrees,int style){
        slashAt(c,c.player.position(),c.forward(1),range,degrees,style);
    }
    public static void slashAt(AbilityRuntime.Cast c,Vec3 origin,Vec3 forward,double range,double degrees,int style){
        var at=origin.add(0,.9,0);
        send(c.player,recipe(c.player.getId(),-1,c.skill.index(),ShuoSkillVfx.LEFT+Math.clamp(style,0,2),at,at.add(forward.normalize().scale(degrees/30)),(float)range,7,c.now()));
    }
    public static void rift(AbilityRuntime.Cast c,Vec3 origin,Vec3 forward,double length,double width){
        var at=origin.add(0,.9,0);
        send(c.player,recipe(c.player.getId(),-1,c.skill.index(),ShuoSkillVfx.RIFT,at,at.add(forward.normalize().scale(length)),(float)width,6,c.now()));
    }
    public static void hit(AbilityRuntime.Cast c,LivingEntity t){contact(c.player,c.skill.index(),t,.60f);}
    public static void contact(ServerPlayer p,int index,LivingEntity target,float size){
        var at=target.position().add(0,target.getBbHeight()*.55,0);
        send(p,recipe(p.getId(),-1,index,ShuoSkillVfx.CONTACT,at,at.add(p.getLookAngle()),size,7,p.level().getGameTime()));
    }
    public static void mark(AbilityRuntime.Cast c,LivingEntity target){
        var at=target.position().add(0,target.getBbHeight()*.55,0);
        send(c.player,recipe(c.player.getId(),target.getId(),c.skill.index(),ShuoSkillVfx.MARK,at,at.add(c.forward(1)),.8f,20,c.now()));
    }
    public static void body(ServerPlayer p,int index,int signal,int life){
        var f=p.getLookAngle().multiply(1,0,1).normalize();if(f.lengthSqr()<.01)f=new Vec3(0,0,1);
        var at=p.position().add(0,signal==ShuoSkillVfx.PARRY?1.05:.85,0).add(f.scale(signal==ShuoSkillVfx.PARRY?.9:0));
        send(p,recipe(p.getId(),p.getId(),index,signal,at,at.add(f),.6f,life,p.level().getGameTime()));
    }
    public static void trackMotion(AbilityRuntime.Cast c,boolean exposed){
        if(!AbilityRuntime.moving(c.player))return;
        final Vec3[] previous={c.player.position()};
        final Vec3[] direction={Vec3.ZERO};final boolean[] started={false};
        c.whileActive(24,age->{
            if(c.data.getBuff("g1_stun")>0||c.data.getBuff("g1_root")>0)return false;
            var now=c.player.position();if(now.distanceToSqr(previous[0])>.0004){
                // Do not bridge a teleport/forced relocation with a giant fake streak.
                if(now.distanceToSqr(previous[0])>9)return false;
                var from=previous[0].add(0,.75,0);var to=now.add(0,.75,0);
                direction[0]=to.subtract(from).normalize();
                float width=c.skill.index()==2?.55f:c.skill.index()==6?1.05f:.85f;
                if(!started[0]){
                    send(c.player,recipe(c.player.getId(),-1,c.skill.index(),ShuoDashVfx.DEPART,from,from.add(direction[0]),width,7,c.now()));started[0]=true;
                }
                // Only measured movement is drawn; a blocked dash never paints a path through the wall.
                send(c.player,recipe(c.player.getId(),-1,c.skill.index(),exposed?ShuoSkillVfx.CHASE:ShuoSkillVfx.TRACE,
                    from,to,width,7,c.now()));previous[0]=now;
            }
            boolean moving=AbilityRuntime.moving(c.player);
            if(!moving&&started[0]){
                var at=now.add(0,.75,0);
                send(c.player,recipe(c.player.getId(),-1,c.skill.index(),ShuoDashVfx.ARRIVE,at,at.add(direction[0]),c.skill.index()==6?1.05f:.65f,8,c.now()));
            }
            return moving;
        });
    }
}
