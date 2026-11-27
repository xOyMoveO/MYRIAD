package com.myriad.client.firstperson;

import com.myriad.client.ClientDataHolder;
import com.myriad.client.FogOfWarClient;
import com.myriad.client.YikeClientState;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;

/** Transient hand and shoulder keyframes for the cannon, rapier and three crescent cuts. */
public final class WeaponBodyPose {
    public record Pose(float lift,float sweep,float roll,float reach,float support,float step,float thrust) {
        public Pose(float lift,float sweep,float roll,float reach,float support,float step) {
            this(lift,sweep,roll,reach,support,step,0);
        }
        Pose times(double t){return new Pose((float)(lift*t),(float)(sweep*t),(float)(roll*t),
                (float)(reach*t),(float)(support*t),(float)(step*t),(float)(thrust*t));}
        Pose plus(Pose b){return new Pose(lift+b.lift,sweep+b.sweep,roll+b.roll,reach+b.reach,
                Math.max(support,b.support),step+b.step,thrust+b.thrust);}
        public Matrix4f wrist(boolean left){
            int side=left?-1:1;
            return new Matrix4f().translate(side*sweep*.045f,lift*.045f,-reach*.19f)
                    .rotateY(side*sweep*.28f).rotateZ(side*roll*.34f).rotateX(-lift*.25f);
        }
    }
    public static final Pose IDLE=new Pose(0,0,0,0,0,0);
    private static final class Track {
        final LivingEntity entity;
        final Item weapon;
        WeaponPosePayload action, accent, field;
        Pose previous=IDLE;
        Track(LivingEntity entity){this.entity=entity;weapon=entity.getMainHandItem().getItem();}
    }
    private static final Map<Integer,Track> tracks=new HashMap<>();
    private WeaponBodyPose(){}

    private static boolean visible(LivingEntity e) {
        var mc=Minecraft.getInstance();
        return e.level()==mc.level && !e.isRemoved() && e.isAlive() && !e.isSpectator() && mc.player!=null
                && (mc.player==e || !e.isInvisibleTo(mc.player) && FogOfWarClient.shouldRenderPlayer(e.getId()));
    }
    private static boolean valid(Track t) {
        return visible(t.entity) && t.entity.getMainHandItem().is(t.weapon) && ClientDataHolder.modEnabled();
    }
    public static void tick(){tracks.values().removeIf(t->!valid(t));}
    public static void receive(WeaponPosePayload p) {
        var level=Minecraft.getInstance().level;
        if(level==null || !(level.getEntity(p.owner()) instanceof LivingEntity actor) || !visible(actor)
                || !ClientDataHolder.modEnabled() || level.getGameTime()-p.started()>p.duration()/Math.max(.1f,p.rate()))return;
        var t=tracks.get(p.owner());
        if(t==null || t.entity!=actor || !valid(t)){t=new Track(actor);tracks.put(p.owner(),t);}
        if(p.move().accent()){t.accent=p;return;}
        if(p.move()==Move.FOLLOW){t.field=p;return;}
        if(p.move()==Move.CLOSE_FIELD || p.move()==Move.DROP_FIELD)t.field=null;
        if (actor==Minecraft.getInstance().player && actor.getMainHandItem().is(ModItems.HENGKE.get())
                && p.move()!=Move.RAPIER) KehengView.clearStroke();
        t.previous=action(t,level.getGameTime());
        t.action=p;
    }
    private static double smooth(double n){n=Math.clamp(n,0,1);return n*n*(3-2*n);}
    private static Pose frame(WeaponPosePayload p,double now){
        if(p==null)return IDLE;
        double elapsed=Math.max(0,now-p.started()),t=p.progress()+elapsed*p.rate()/p.duration();
        if(t>=1)return IDLE;
        double beat=smooth(t/.20)*(1-smooth((t-.34)/.66));
        double wind=.24+.76*smooth(t);
        return switch(p.move()){
            case MARK_TICK -> new Pose(.02f,0,.12f,0,0,0).times(beat);
            case CALIBRATE,ECHO -> new Pose(.04f,0,.22f,0,.20f,0).times(beat);
            case BARRIER,FIELD_SHIELD -> new Pose(.12f,.05f,-.23f,0,.64f,0).times(beat);
            case CANNON_BASIC -> new Pose(.27f,0,.08f,-.40f,.12f,0).times(beat);
            case CANNON_SHOT -> new Pose(.42f,0,.14f,-.70f,.60f,0).times(beat);
            case FIXED_SHOT -> new Pose(.58f,0,.16f,-.85f,.95f,.16f).times(beat);
            case MOBILE_SHOT -> new Pose(.28f,.10f,.12f,-.42f,.48f,0).times(beat);
            case HEAVY_FIRE -> new Pose(.70f,0,.20f,-1,.85f,.16f).times(beat);
            case DENIAL,BOUNDARY -> new Pose(-.24f,-.3f,.30f,.27f,.86f,0).times(beat);
            case RAPIER,NEEDLE -> new Pose(.26f,-.16f,.26f,1,.22f,0,1).times(beat);
            case NEEDLE_READY -> new Pose(.24f,.17f,-.44f,-.25f,.34f,0).times(wind);
            case STEP,PURSUE -> new Pose(-.31f,.41f,-.24f,-.25f,.08f,.8f).times(beat);
            case FOLLOW_READY -> new Pose(.18f,.44f,.38f,.12f,.62f,0).times(wind);
            case FOLLOW -> new Pose(.14f,.16f,.14f,.05f,.28f,0);
            case CLOSE_FIELD -> new Pose(.35f,-.49f,-.4f,.22f,.6f,0).times(beat);
            case DOME -> new Pose(.75f,.55f,.29f,.2f,1,.16f).times(beat);
            case LINE_READY -> new Pose(.60f,-.28f,-.32f,-.15f,.85f,.16f).times(wind);
            case LINE -> new Pose(.8f,-.08f,.12f,1,.95f,.24f,1).times(beat);
            case MOON_READY -> new Pose(.13f,-.35f,.24f,-.12f,.4f,.10f).times(wind);
            case FIRST,SECOND -> {
                float side=p.move()==Move.FIRST?-1:1;
                double swing=smooth(elapsed/4);
                yield new Pose(.33f,(float)(side*(2*swing-1)),side*.44f,.36f,.16f,.18f)
                        .times(1-smooth((t-.92)/.08));
            }
            case THIRD -> new Pose((float)(.75-1.3*smooth(t/.6)),-.30f,.7f,.42f,.3f,.3f).times(1-smooth((t-.65)/.35));
            case FINAL -> new Pose(.40f,(float)(.98-1.96*smooth(t/.65)),.64f,.56f,.12f,.16f).times(1-smooth((t-.65)/.35));
            case SHEATHE -> new Pose(-.18f,-.55f,-.31f,-.35f,.22f,0).times(beat);
            case RELAX,DROP_FIELD -> IDLE;
        };
    }
    private static Pose action(Track t,double now){
        if(t.action==null)return IDLE;
        double blend=smooth((now-t.action.started())/2);
        return t.previous.times(1-blend).plus(frame(t.action,now).times(blend));
    }
    static float aimingTurn(LivingEntity actor) {
        var t=tracks.get(actor.getId());
        if(t==null || t.action==null || t.action.move()!=Move.LINE_READY && t.action.move()!=Move.LINE
                || frame(t.action,actor.level().getGameTime())==IDLE)return 0;
        return (float)Math.toRadians(Math.clamp(Mth.wrapDegrees(t.action.yaw()-actor.getYRot()),-80,80));
    }
    static Pose firstPersonRapier(LivingEntity actor,float partial) {
        var track=tracks.get(actor.getId());
        if (track!=null && valid(track) && track.action!=null && track.action.move()==Move.RAPIER) {
            double now=actor.level().getGameTime()+partial;
            return frame(track.accent,now).plus(frame(track.field,now));
        }
        return sample(actor,partial);
    }
    public static Pose sample(LivingEntity actor,float partial){
        double now=actor.level().getGameTime()+partial;
        var t=tracks.get(actor.getId());
        Pose p=t!=null&&valid(t)?action(t,now).plus(frame(t.accent,now)).plus(frame(t.field,now)):IDLE;
        boolean cannon=actor.getMainHandItem().is(ModItems.HONG_LIE.get());
        if(cannon){
            var s=YikeClientState.get(actor.getId());
            if(s!=null){
                float brace=s.mode()==1?.86f:s.mode()==2?.45f:s.mode()==3?.92f:0;
                float aim=s.windup()>=2?s.progress():0;
                p=p.plus(new Pose(aim*(s.windup()==6?.95f:.42f),s.mode()==2?.22f:0,s.steady()?.06f:0,
                        aim*.12f,Math.max(brace,aim),s.mode()==1?.28f:s.mode()==2?.12f:0));
            }
        }
        if(actor==Minecraft.getInstance().player && ClientDataHolder.placementType()!=0)
            p=p.plus(new Pose(-.17f,-.12f,.18f,0,.60f,0));
        return p;
    }
    public static void pose(HumanoidModel<?> model, HumanoidRenderState state) {
        var level=Minecraft.getInstance().level;
        if(level==null || !(state instanceof AvatarRenderState avatar) || state.isInvisible || avatar.isSpectator
                || !(level.getEntity(avatar.id) instanceof LivingEntity actor) || !visible(actor))return;
        var held=actor.getMainHandItem();
        if(!(held.is(ModItems.HONG_LIE.get())||held.is(ModItems.HENGKE.get())||held.is(ModItems.SHUO_SWORD.get())))return;
        var p=sample(actor,state.ageInTicks-(float)Math.floor(state.ageInTicks));
        boolean left=state.mainArm==HumanoidArm.LEFT;
        var main=left?model.leftArm:model.rightArm;var other=left?model.rightArm:model.leftArm;int sign=left?-1:1;
        main.xRot-=p.lift()+p.reach()*.38f;main.yRot+=p.sweep()*sign;main.zRot+=p.roll()*sign;
        var track=tracks.get(actor.getId());
        if(track!=null && track.action!=null && track.action.move()==Move.LINE_READY && frame(track.action,level.getGameTime())!=IDLE)
            main.yRot+=(float)Math.toRadians(Mth.wrapDegrees(track.action.yaw()-state.bodyRot));
        if(actor.getOffhandItem().isEmpty()){
            other.xRot-=p.support()*1.12f;other.yRot-=sign*p.support()*.58f;other.zRot+=sign*p.support()*.22f;
        }
        if(track!=null && track.action!=null && track.action.move()==Move.STEP && p.step()>0){
            double angle=Math.toRadians(Mth.wrapDegrees(track.action.yaw()-state.bodyRot));
            model.body.zRot+=Math.sin(angle)*p.step()*.25f;
            model.body.xRot+=Math.cos(angle)*p.step()*.18f;
        }
        model.body.yRot+=sign*p.sweep()*.18f;model.body.xRot+=p.step()*.12f;
        model.leftLeg.xRot+=sign*p.step()*.22f;model.rightLeg.xRot-=sign*p.step()*.22f;
    }
}
