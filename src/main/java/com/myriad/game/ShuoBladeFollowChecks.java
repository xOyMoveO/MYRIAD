package com.myriad.game;
import com.myriad.client.firstperson.*;
import com.myriad.character.skills.shuo.*;
import com.myriad.network.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
final class ShuoBladeFollowChecks {
    static void verify(GameTestHelper h){
        verifyHeldHook(h);
        int count=0;var covered=new HashSet<Integer>();
        for(int action:new int[]{1,2,3,4,5,6,7,8,9})for(boolean drawn:new boolean[]{false,true})for(boolean mirror:new boolean[]{false,true}){
            var motion=new ShuoMotion();motion.accept(new ShuoViewPayload(action,10,100,1),100,0,drawn);
            for(int i=0;i<101;i++){
                double now=100+i/10.0;var mesh=ShuoSkillTrail.build(motion,now,mirror);count++;
                h.assertTrue(mesh.size()<=AttackTrailMesh.MAX_QUADS,"Attached light plus swept trail stays within the existing camera budget");
                if(!mesh.isEmpty())covered.add(action);
                for(var q:mesh){
                    h.assertTrue(Set.of(0,75,77).contains(q.material())&&q.alpha()>0&&q.alpha()<=1,"Existing HD materials and finite alpha only");
                    h.assertTrue(q.startU()>=0&&q.endU()<=1&&q.startV()>=0&&q.endV()<=1,"Native full-image UVs stay bounded");
                    for(var p:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Double.isFinite(p.x)&&Double.isFinite(p.y)&&p.z<-.055,"No near-camera crossing");
                }
                var pose=motion.frame(now,drawn);var mat=ShuoRig.blade(pose,mirror);
                for(double f:new double[]{0,.25,.75,1}){
                    var actual=mat.transformPosition(ShuoBladeLight.point(f));var attached=ShuoBladeLight.at(mat,f);
                    h.assertTrue(attached.distanceTo(new Vec3(actual.x,actual.y,actual.z))<1E-7,"Every light sample comes from the exact current blade matrix");
                }
            }
            h.assertTrue(ShuoSkillTrail.build(motion,110,mirror).isEmpty(),"Attached light disappears when its action expires");
            motion.reset();h.assertTrue(ShuoSkillTrail.build(motion,105,mirror).isEmpty(),"Cancellation/reset leaves no frozen blade trail");
        }
        h.assertTrue(covered.equals(Set.of(1,2,3,4,5,6,7,8,9)),"Every active skill action, including guard, charge and return, has blade/throat light");
        h.assertTrue(ShuoBladeLight.exposure(ShuoRig.frame(3,.5,false))<.005&&ShuoBladeLight.exposure(ShuoRig.frame(10,0,false))<.005,"Closed guard/idle cannot expose a luminous blade through its sheath");
        h.assertTrue(ShuoBladeLight.exposure(ShuoRig.frame(10,0,true))>.99,"Drawn blade has full edge light");
        var activity=new ShuoBladeActivity();
        var first=ShuoVisuals.recipe(1,1,5,ShuoSkillVfx.LEFT,Vec3.ZERO,new Vec3(0,0,3),3,7,100);
        var other=ShuoVisuals.recipe(1,1,3,ShuoSkillVfx.GUARDING,Vec3.ZERO,new Vec3(0,0,1),1,2,100);
        activity.receive(first,100);activity.receive(other,100);h.assertTrue(activity.opacity(1,101)>0,"World blade edge activity follows actual received skill phases");
        activity.receive(new AbilityVfxPayload(1,-1,10,3,12,Vec3.ZERO,Vec3.ZERO,0,1,1,101,false),101);
        h.assertTrue(activity.opacity(1,101)>0,"Cancel another skill does not erase a still-active blade lease");
        h.assertTrue(activity.opacity(1,107)==0,"No residual world blade after absolute TTL");
        activity.receive(first,110);h.assertTrue(activity.opacity(1,110)==0,"Expired late packets cannot revive the blade");
        activity.receive(first,100);activity.receive(new AbilityVfxPayload(1,-1,0,0,12,Vec3.ZERO,Vec3.ZERO,0,0,1,101,false),101);
        h.assertTrue(activity.opacity(1,101)==0,"Death/role/reset global cancellation clears world blade leases");
        activity.receive(first,100);activity.clear();h.assertTrue(activity.opacity(1,100)==0,"World change clears attached effects");
        System.out.println("[MYRIAD-SHUO-BLADE-FOLLOW] PASS: "+count+" skill pose/stance/mirror samples, exact authored edge/matrix, sheathed clipping, lifetime/cancellation and existing material budgets");
    }
    private static void verifyHeldHook(GameTestHelper h){
        // The client-only injection ABI is checked against the real client JAR by previewShuoBlade.
        try(var in=ShuoBladeFollowChecks.class.getResourceAsStream("/assets/myriad/models/item/shuo_sword.json");var reader=new java.io.InputStreamReader(in)){
            var model=net.minecraft.client.resources.model.cuboid.CuboidModel.fromStream(reader);
            for(boolean left:new boolean[]{false,true}){
                var stack=new com.mojang.blaze3d.vertex.PoseStack();
                model.transforms().getTransform(left?net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND:net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(left,stack.last());
                for(double f:new double[]{0,.25,.75,1}){
                    var edge=ShuoBladeLight.point(f);var p=stack.last().pose().transformPosition(edge);
                    h.assertTrue(p.isFinite(),"Third-person edge uses the same packaged display transform as the real item");
                }
            }
        }catch(Exception e){throw new IllegalStateException("Third-person model transform",e);}
    }
}
