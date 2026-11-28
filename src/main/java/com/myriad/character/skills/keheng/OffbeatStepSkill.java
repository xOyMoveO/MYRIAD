package com.myriad.character.skills.keheng;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.character.ability.*;
public final class OffbeatStepSkill extends KehengSkill {
    public static final int FIELD_TICKS = 30, SHIELD_TICKS = 40, DASH_TICKS = 5;
    public static final double FIELD_RADIUS = 2, FIELD_RATE = .8, DASH_RANGE = 4.5;
    private static final SupportAmount SHIELD = new SupportAmount("shield", 35, 12.5f, .15f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    public OffbeatStepSkill(Definition d){super(d);}
    @Override public void dashPose(AbilityRuntime.Cast c, DashPose phase, net.minecraft.world.phys.Vec3 direction) {
        var movement=c.memory("step_origin");
        if(phase==DashPose.START){
            movement.point=c.player.position();
            WeaponPosePayload.travel(c,Move.STEP,5,0,direction);
        }else if(phase==DashPose.MOVING && movement.point!=null){
            float progress=(float)Math.clamp(c.player.position().subtract(movement.point).dot(direction)/4.5,0,1);
            if(progress>0)WeaponPosePayload.travel(c,Move.STEP,5,progress,direction);
        }else if(phase==DashPose.CANCEL || phase==DashPose.FINISH){
            movement.point=null;
            WeaponPosePayload.play(c.player,Move.RELAX,3);
        }
    }
    @Override public boolean canRelease(AbilityRuntime.Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void release(AbilityRuntime.Cast c){
        KehengTimeFields.ground(c,c.player.position(),FIELD_RADIUS,0,FIELD_TICKS,FIELD_RATE,0,0);
        c.shield(c.player,SHIELD.value(c),SHIELD_TICKS);KehengVisuals.watchShield(c,40);
        // The normal movement packet already carries the horizontal input direction; standing falls back to aim.
        var input=c.player.getLastClientMoveIntent().multiply(1,0,1);
        var direction=input.lengthSqr()>.001?input.normalize():c.forward(1);
        c.actionDash(direction.scale(DASH_RANGE),DASH_TICKS,0,(t,first)->{},()->{},false,false);
    }
}
