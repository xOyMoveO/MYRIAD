package com.myriad.game;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.skills.shuo.*;
import com.myriad.client.firstperson.*;
import com.myriad.network.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.network.ShuoViewPayload.*;
final class ShuoSlashChecks {
    static void verify(GameTestHelper h){
        for(int kind=0;kind<=12;kind++){
            h.assertTrue(ShuoVisuals.allowsSkill(kind)==(kind==0||kind==1),"Only functional placement previews survive the old skill VFX retirement");
            if(kind==0||kind==1)continue;
            for(int variant=0;variant<8;variant++){
                var old=new AbilityVfxPayload(1,-1,10,variant,kind,Vec3.ZERO,new Vec3(0,0,1),3,120,7,0,false);
                h.assertTrue(AbilityVfxGeometry.build(old,2,false).isEmpty(),"Retired skill/legacy VFX never renders");
            }
        }
        var states=new HashSet<Integer>();int samples=0;
        for(var direction:List.of(new Vec3(0,0,1),new Vec3(1,.4,1),new Vec3(0,1,0),Vec3.ZERO))for(boolean drawn:new boolean[]{false,true})for(int action:new int[]{ShuoViewPayload.DRAW,ShuoViewPayload.BASIC_RISE,ShuoViewPayload.BASIC_SWEEP}){
            var p=ShuoVisuals.basicRecipe(1,Vec3.ZERO,direction,drawn,action,10);
            h.assertTrue(p.valid()&&ShuoVfx.owns(p),"Valid new basic-only wire recipe");
            for(int n=0;n<56;n++)for(boolean low:new boolean[]{false,true}){
                float age=p.life()*(n+.5f)/56;var mesh=AbilityVfxGeometry.build(p,age,low);
                h.assertTrue(mesh.size()<=(low?32:ShuoSlashMesh.MAX_QUADS),"Strict basic slash geometry budget");
                for(var q:mesh){
                    h.assertTrue((q.material()==75||q.material()==0)&&q.alpha()>0&&q.alpha()<=1&&q.startU()>=0&&q.endU()<=1&&q.startU()<q.endU()&&q.startV()>=0&&q.endV()<=1,"Correct HD/filament materials, alpha and UVs");
                    for(var v:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&v.distanceTo(p.from())<3,"Finite bounded world mesh in all aim directions");
                }samples++;
            }
            h.assertTrue(AbilityVfxGeometry.build(p,p.life(),false).isEmpty()&&AbilityVfxGeometry.build(p,-1,false).isEmpty(),"No residual world mesh before/after TTL");
            if(!drawn)h.assertTrue(AbilityVfxGeometry.build(p,2.5f,false).isEmpty(),"Sheathed attack light waits for extraction");
        }
        for(int stage=0;stage<16;stage++)states.add(ShuoSlashMesh.build(Vec3.ZERO,new Vec3(1,0,0),new Vec3(0,1,0),1,.6,(stage+.5f)/16,false).hashCode());
        h.assertTrue(states.size()==16,"Sixteen genuinely distinct reveal/travel/erase stages, not fade-only copies");
        for(int action=0;action<=ShuoViewPayload.MAX_ACTION;action++)for(int n=0;n<=100;n++)for(boolean drawn:new boolean[]{false,true})for(boolean mirror:new boolean[]{false,true}){
            var testMotion=new ShuoMotion();
            testMotion.accept(new ShuoViewPayload(action,9,0,1),0,0,drawn);
            var mesh=ShuoTrail.build(testMotion,n*.09,drawn,mirror,false);
            if(!ShuoViewPayload.basicAction(action))h.assertTrue(mesh.isEmpty(),"No skill, charge, resheath or guard camera VFX remains");
            h.assertTrue(mesh.size()<=AttackTrailMesh.MAX_QUADS,"Camera effect stays within renderer budget");
            for(var q:mesh)for(var v:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(v.z<-.1&&Double.isFinite(v.x)&&Double.isFinite(v.y),"No camera-plane crossing or NaN");
        }
        var combo=new ShuoBasicCombo();
        int[] sequence={ShuoViewPayload.DRAW,ShuoViewPayload.BASIC_RISE,ShuoViewPayload.BASIC_SWEEP,ShuoViewPayload.DRAW};
        for(int i=0;i<sequence.length;i++)h.assertTrue(combo.select(i*32,i!=0)==sequence[i],"Three cosmetic attacks cycle even at a slow attack speed");
        h.assertTrue(combo.select(200,true)==ShuoViewPayload.DRAW,"Three-second idle resets chain");
        h.assertTrue(combo.select(201,false)==ShuoViewPayload.DRAW,"Sheathed attack always draws instead of playing an exposed cut");
        combo.confirm(ShuoViewPayload.BASIC_RISE,202);h.assertTrue(combo.select(203,true)==ShuoViewPayload.BASIC_SWEEP,"Server confirmation repairs prediction order");
        combo.reset();h.assertTrue(combo.select(204,true)==ShuoViewPayload.DRAW,"Lifecycle reset clears sequence");
        var motion=new ShuoMotion();motion.basic(0,true);var before=motion.frame(1,true);
        motion.accept(new ShuoViewPayload(ShuoViewPayload.DRAW,9,0,1),1,1,true);
        h.assertTrue(Math.abs(motion.progress(1)-1.0/9)<1E-6,"Prediction acknowledgement never restarts attack timeline");
        motion.basic(32,true);h.assertTrue(motion.action(32)==ShuoViewPayload.BASIC_RISE,"Second local click uses rising cut");
        motion.accept(new ShuoViewPayload(ShuoViewPayload.BASIC_SWEEP,9,32,2),33,1,true);
        h.assertTrue(motion.action(33)==ShuoViewPayload.BASIC_SWEEP&&Math.abs(motion.progress(33)-1.0/9)<1E-6,"Authoritative variant corrects misprediction without duplicate animation");
        motion.basic(64,true);h.assertTrue(motion.action(64)==ShuoViewPayload.DRAW,"Corrected sequence continues at authoritative next cut");
        for(int action:new int[]{ShuoViewPayload.BASIC_RISE,ShuoViewPayload.BASIC_SWEEP}){
            var previous=ShuoRig.frame(action,0,true);
            for(int n=1;n<=400;n++){
                var f=ShuoRig.frame(action,n/400.0,true);
                h.assertTrue(f.palm().distance(previous.palm())<.04&&f.axis().distance(previous.axis())<.08,"New cuts stay continuous through anticipation, strike and recovery");
                if(n>76&&n<232){
                    var velocity=new org.joml.Vector3f(f.palm()).sub(previous.palm());
                    var bladeEdge=ShuoRig.blade(f,false).transformDirection(new org.joml.Vector3f(1,0,0)).normalize();
                    h.assertTrue(bladeEdge.dot(velocity.normalize())>.50,"New cuts lead with the actual blade edge, never slap with the broad face");
                }previous=f;
            }
            h.assertTrue(previous.palm().distance(ShuoRig.frame(ShuoViewPayload.CANCEL,0,true).palm())<1E-6,"Each cut recovers to the established drawn grip");
        }
        crescentContinuity(h);
        System.out.println("[MYRIAD-SHUO-BASIC-VFX] PASS: "+samples+" new recipe/LOD stages, 16 temporal stages, crescent draw, continued grips, cancellation and recovery");
    }
    private static void crescentContinuity(GameTestHelper h){
        var motion=new ShuoMotion();
        var idle=ShuoRig.frame(CANCEL,1,true);
        motion.accept(new ShuoViewPayload(MOON_FIRST,7,100,1),100,0,false);
        for(int i=0;i<=32;i++){
            var f=motion.frame(100+7*i/100.0,true);
            var mouthToGrip=new org.joml.Vector3f(f.palm()).sub(f.mouth());
            h.assertTrue(f.axis().dot(f.sheathAxis())>.986f && mouthToGrip.cross(f.sheathAxis()).length()<.18,
                    "Crescent draw stays on the shallow curved guide until the tip clears");
        }
        var clear=motion.frame(100+7*.32,true);
        var tip=ShuoRig.blade(clear,false).transformPosition(new org.joml.Vector3f(0,ShuoRig.MODEL_GRIP_TO_TIP,0));
        h.assertTrue(tip.sub(clear.mouth()).dot(clear.sheathAxis())<0,"Tip clears the throat before the crescent turns");
        var earlySync=new ShuoMotion();
        earlySync.accept(new ShuoViewPayload(MOON_FIRST,7,100,1),100,0,true);
        sameBlade(h,clear,earlySync.frame(100+7*.32,true),"Receiving the drawn counter first cannot skip the sheathed crescent extraction");
        var first=motion.frame(106.999,true);
        sameBlade(h,first,motion.frame(139.99,true),"First cut keeps its grip and edge through the recast window");
        sameBlade(h,first,motion.frame(140,true),"Timeout starts recovery at the held pose");
        sameBlade(h,idle,motion.frame(143,true),"Unused recast window returns to idle");
        motion.accept(new ShuoViewPayload(CHARGE,4,115,2),115,0,true);
        sameBlade(h,first,motion.frame(123,true),"A delayed release edge retains the previous cut after charge expires");
        motion.accept(new ShuoViewPayload(MOON_SECOND,7,123,3),123,0,true);
        sameBlade(h,first,motion.frame(123,true),"The accepted continuation starts at the held first cut");
        var second=motion.frame(130,true);
        sameBlade(h,second,motion.frame(136,true),"Second cut also waits at the end of its swing");
        motion.accept(new ShuoViewPayload(CANCEL,0,136,4),136,0,true);
        sameBlade(h,second,motion.frame(136,true),"Explicit cancellation preserves the last rendered grip on that frame");
        h.assertTrue(!motion.active(136)&&ShuoSkillTrail.build(motion,136,false).isEmpty()&&ShuoTrail.build(motion,136,true,false,false).isEmpty(),"Cancellation immediately stops both attack trails");
        sameBlade(h,idle,motion.frame(139,true),"Cancelled continuation settles at the drawn idle grip");
        for(int action:new int[]{MOON_THIRD,RETURNING_MOON}){
            motion.reset();motion.accept(new ShuoViewPayload(action,7,200,1),200,0,true);
            var end=motion.frame(206.999,true);
            sameBlade(h,end,motion.frame(207,true),"A finishing cut cannot snap to idle at its action timeout");
            sameBlade(h,idle,motion.frame(210,true),"The finishing cut naturally relaxes when its next cue is late");
            motion.accept(new ShuoViewPayload(SHEATHE,8,206,2),206,0,true);
            sameBlade(h,end,motion.frame(206,true),"An on-time automatic sheathe starts at the actual finishing pose");
        }
    }
    private static void sameBlade(GameTestHelper h,ShuoRig.Frame expected,ShuoRig.Frame actual,String message){
        for(boolean mirror:new boolean[]{false,true})
            h.assertTrue(ShuoRig.blade(expected,mirror).equals(ShuoRig.blade(actual,mirror),1E-4f),message);
    }
}
