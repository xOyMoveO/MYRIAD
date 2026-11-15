package com.myriad.game;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.myriad.character.skills.shuo.ShuoWeaponState;
import com.myriad.client.firstperson.ShuoHeldView;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Production closed-grip matrices and actual player arms; no gameplay or first-person pose changes. */
final class ShuoSheathedGripChecks {
    @SuppressWarnings({"rawtypes","unchecked"})
    static void verify(GameTestHelper h){
        ItemTransforms transforms;
        try(var in=ShuoSheathedGripChecks.class.getResourceAsStream("/assets/myriad/models/item/shuo_sword.json");
                var reader=new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)){
            transforms=CuboidModel.fromStream(reader).transforms();
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        var old=new PoseStack();old.mulPose(Axis.XP.rotationDegrees(-90));old.mulPose(Axis.YP.rotationDegrees(180));
        old.translate(-1f/16,2f/16,-10f/16);
        transforms.getTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).apply(true,old.last());old.translate(.5,.5,.5);
        var forward=old.last().pose();var closed=ShuoHeldView.closedGrip(transforms);
        h.assertTrue(forward.getTranslation(new Vector3f()).distance(closed.getTranslation(new Vector3f()))<1E-6,
                "Closed sword turns about the existing fist grip, without moving it");
        var before=forward.transformDirection(new Vector3f(0,1,0)).normalize();
        var after=closed.transformDirection(new Vector3f(0,1,0)).normalize();
        h.assertTrue(before.z<-.95&&after.z>.95&&Math.abs(before.y-after.y)<1E-6&&after.y>0,
                "Only the sheathed direction changes: aft instead of forward, retaining the gentle downward pitch");
        h.assertTrue(closed.determinant()>0&&Math.abs(closed.determinant()-forward.determinant())<1E-6,
                "The closed grip preserves scale and positive face winding");
        int samples=0;
        for(boolean slim:new boolean[]{false,true})for(var main:HumanoidArm.values()){
            var model=new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot(),slim);
            var state=new AvatarRenderState();state.mainArm=main;
            for(int i=0;i<=120;i++){
                float phase=i/120f*(float)(2*Math.PI);
                model.leftArm.xRot=.65f*(float)Math.sin(phase);model.leftArm.zRot=.06f*(float)Math.cos(phase);
                var hand=new PoseStack();model.translateToHand(state,HumanoidArm.LEFT,hand);
                var actual=new PoseStack();ShuoHeldView.leftHand((ArmedModel)model,state,actual,transforms);
                var matrix=actual.last().pose();var expected=new Matrix4f(hand.last().pose()).mul(closed);
                h.assertTrue(matrix.equals(expected,1E-6f),"The live attachment follows the actual left arm even with left main-hand preference");
                var grip=matrix.transformPosition(new Vector3f());
                var oldGrip=new Matrix4f(hand.last().pose()).mul(forward).transformPosition(new Vector3f());
                h.assertTrue(grip.distance(oldGrip)<1E-6,"The fist remains welded throughout walking and both skin widths");
                var direction=matrix.transformDirection(new Vector3f(0,1,0)).normalize();
                h.assertTrue(direction.z>.65&&matrix.isFinite()&&matrix.determinant()>0,
                        "The sheath remains behind the player over the sampled normal walk cycle");
                var mouth=matrix.transformPosition(new Vector3f(0,ShuoWeaponState.GRIP_TO_MOUTH,0));
                var sheath=new Matrix4f(matrix).translate(0,ShuoWeaponState.GRIP_TO_MOUTH,0);
                h.assertTrue(mouth.distance(sheath.transformPosition(new Vector3f()))<1E-6,
                        "The original blade root stays seated at the sheath mouth during the rearward hold");
                samples++;
            }
        }
        System.out.println("[MYRIAD-SHUO-REAR-GRIP] PASS: "+samples+" actual left-arm/skin/main-arm/walk samples; fixed grip, aft sheath, closed seam and positive winding; offline geometry, not live visual acceptance");
    }
}
