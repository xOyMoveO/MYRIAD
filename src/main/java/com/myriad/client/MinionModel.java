package com.myriad.client;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
/** Geometry from art/models/minions/*.spec.json; joint motion is evaluated locally each frame. */
public final class MinionModel extends EntityModel<MinionRenderState> {
    public MinionModel(){super(create());}
    private static ModelPart create(){
        MeshDefinition mesh=new MeshDefinition();
        PartDefinition kind0=mesh.getRoot().addOrReplaceChild("kind0",CubeListBuilder.create(),PartPose.offset(0,24,0));
        PartDefinition k0_left_leg=kind0.addOrReplaceChild("left_leg",CubeListBuilder.create(),PartPose.offset(-2.5F,-5F,0));
        PartDefinition k0_right_leg=kind0.addOrReplaceChild("right_leg",CubeListBuilder.create(),PartPose.offset(2.5F,-5F,0));
        PartDefinition k0_left_arm=kind0.addOrReplaceChild("left_arm",CubeListBuilder.create(),PartPose.offset(-6.5F,-14F,0));
        PartDefinition k0_right_arm=kind0.addOrReplaceChild("right_arm",CubeListBuilder.create(),PartPose.offset(6.5F,-14F,0));
        kind0.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-5F,-16F,-3F,10F,9F,6F),PartPose.ZERO);
        kind0.addOrReplaceChild("waist",CubeListBuilder.create().texOffs(32,0).addBox(-4F,-7F,-2.5F,8F,2F,5F),PartPose.ZERO);
        k0_left_leg.addOrReplaceChild("leg_L",CubeListBuilder.create().texOffs(0,0).addBox(-1.5F,0F,-2F,3F,5F,4F),PartPose.ZERO);
        k0_right_leg.addOrReplaceChild("leg_R",CubeListBuilder.create().texOffs(0,0).addBox(-1.5F,0F,-2F,3F,5F,4F),PartPose.ZERO);
        kind0.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-3F,-21F,-2.5F,6F,5F,5F),PartPose.ZERO);
        kind0.addOrReplaceChild("brow",CubeListBuilder.create().texOffs(32,0).addBox(-3.5F,-21F,-3F,7F,1F,6F),PartPose.ZERO);
        kind0.addOrReplaceChild("eye_L",CubeListBuilder.create().texOffs(128,0).addBox(-2F,-19F,-3F,1.5F,1F,0.5F),PartPose.ZERO);
        kind0.addOrReplaceChild("eye_R",CubeListBuilder.create().texOffs(128,0).addBox(0.5F,-19F,-3F,1.5F,1F,0.5F),PartPose.ZERO);
        kind0.addOrReplaceChild("chest_plate",CubeListBuilder.create().texOffs(64,0).addBox(-3.5F,-15F,-4F,7F,5F,1F),PartPose.ZERO);
        kind0.addOrReplaceChild("core",CubeListBuilder.create().texOffs(128,0).addBox(-1F,-14F,-4.5F,2F,3F,0.5F),PartPose.ZERO);
        kind0.addOrReplaceChild("shoulder_L",CubeListBuilder.create().texOffs(64,0).addBox(-9F,-16F,-3.5F,5F,3F,7F),PartPose.ZERO);
        kind0.addOrReplaceChild("shoulder_R",CubeListBuilder.create().texOffs(64,0).addBox(4F,-16F,-3.5F,5F,3F,7F),PartPose.ZERO);
        k0_left_arm.addOrReplaceChild("arm_L",CubeListBuilder.create().texOffs(0,0).addBox(-2F,1F,-2F,4F,6F,4F),PartPose.ZERO);
        k0_right_arm.addOrReplaceChild("arm_R",CubeListBuilder.create().texOffs(0,0).addBox(-2F,1F,-2F,4F,6F,4F),PartPose.ZERO);
        k0_left_arm.addOrReplaceChild("gauntlet_L",CubeListBuilder.create().texOffs(32,0).addBox(-2.5F,6F,-3F,5F,4F,6F),PartPose.ZERO);
        k0_right_arm.addOrReplaceChild("gauntlet_R",CubeListBuilder.create().texOffs(32,0).addBox(-2.5F,6F,-3F,5F,4F,6F),PartPose.ZERO);
        k0_left_arm.addOrReplaceChild("knuckle_L",CubeListBuilder.create().texOffs(96,0).addBox(-2F,7F,-4F,4F,2F,1F),PartPose.ZERO);
        k0_right_arm.addOrReplaceChild("knuckle_R",CubeListBuilder.create().texOffs(96,0).addBox(-2F,7F,-4F,4F,2F,1F),PartPose.ZERO);
        PartDefinition kind1=mesh.getRoot().addOrReplaceChild("kind1",CubeListBuilder.create(),PartPose.offset(0,24,0));
        PartDefinition k1_left_leg=kind1.addOrReplaceChild("left_leg",CubeListBuilder.create(),PartPose.offset(-2.5F,-5F,0));
        PartDefinition k1_right_leg=kind1.addOrReplaceChild("right_leg",CubeListBuilder.create(),PartPose.offset(2.5F,-5F,0));
        PartDefinition k1_left_arm=kind1.addOrReplaceChild("left_arm",CubeListBuilder.create(),PartPose.offset(-6.5F,-14F,0));
        PartDefinition k1_right_arm=kind1.addOrReplaceChild("right_arm",CubeListBuilder.create(),PartPose.offset(6.5F,-14F,0));
        kind1.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-5F,-16F,-3F,10F,9F,6F),PartPose.ZERO);
        kind1.addOrReplaceChild("waist",CubeListBuilder.create().texOffs(32,0).addBox(-4F,-7F,-2.5F,8F,2F,5F),PartPose.ZERO);
        k1_left_leg.addOrReplaceChild("leg_L",CubeListBuilder.create().texOffs(0,0).addBox(-1.5F,0F,-2F,3F,5F,4F),PartPose.ZERO);
        k1_right_leg.addOrReplaceChild("leg_R",CubeListBuilder.create().texOffs(0,0).addBox(-1.5F,0F,-2F,3F,5F,4F),PartPose.ZERO);
        kind1.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-3F,-21F,-2.5F,6F,5F,5F),PartPose.ZERO);
        kind1.addOrReplaceChild("brow",CubeListBuilder.create().texOffs(32,0).addBox(-3.5F,-21F,-3F,7F,1F,6F),PartPose.ZERO);
        kind1.addOrReplaceChild("eye_L",CubeListBuilder.create().texOffs(128,0).addBox(-2F,-19F,-3F,1.5F,1F,0.5F),PartPose.ZERO);
        kind1.addOrReplaceChild("eye_R",CubeListBuilder.create().texOffs(128,0).addBox(0.5F,-19F,-3F,1.5F,1F,0.5F),PartPose.ZERO);
        kind1.addOrReplaceChild("chest_plate",CubeListBuilder.create().texOffs(64,0).addBox(-3.5F,-15F,-4F,7F,5F,1F),PartPose.ZERO);
        kind1.addOrReplaceChild("core",CubeListBuilder.create().texOffs(128,0).addBox(-1F,-14F,-4.5F,2F,3F,0.5F),PartPose.ZERO);
        kind1.addOrReplaceChild("shoulder_L",CubeListBuilder.create().texOffs(64,0).addBox(-9F,-16F,-3.5F,5F,3F,7F),PartPose.ZERO);
        kind1.addOrReplaceChild("shoulder_R",CubeListBuilder.create().texOffs(64,0).addBox(4F,-16F,-3.5F,5F,3F,7F),PartPose.ZERO);
        k1_left_arm.addOrReplaceChild("arm_L",CubeListBuilder.create().texOffs(0,0).addBox(-2F,1F,-2F,4F,6F,4F),PartPose.ZERO);
        k1_right_arm.addOrReplaceChild("arm_R",CubeListBuilder.create().texOffs(0,0).addBox(-2F,1F,-2F,4F,6F,4F),PartPose.ZERO);
        k1_right_arm.addOrReplaceChild("arm_cannon",CubeListBuilder.create().texOffs(32,0).addBox(-2F,3F,-9F,4F,4F,10F),PartPose.ZERO);
        k1_right_arm.addOrReplaceChild("bow_wings",CubeListBuilder.create().texOffs(96,0).addBox(-5F,4F,-8F,10F,1F,2F),PartPose.ZERO);
        k1_right_arm.addOrReplaceChild("muzzle",CubeListBuilder.create().texOffs(128,0).addBox(-1F,4F,-10F,2F,2F,1F),PartPose.ZERO);
        kind1.addOrReplaceChild("back_ammo",CubeListBuilder.create().texOffs(32,0).addBox(-2.5F,-15F,3F,5F,6F,3F),PartPose.ZERO);
        PartDefinition kind2=mesh.getRoot().addOrReplaceChild("kind2",CubeListBuilder.create(),PartPose.offset(0,24,0));
        PartDefinition k2_left_leg=kind2.addOrReplaceChild("left_leg",CubeListBuilder.create(),PartPose.offset(-2.5F,-5F,0));
        PartDefinition k2_right_leg=kind2.addOrReplaceChild("right_leg",CubeListBuilder.create(),PartPose.offset(2.5F,-5F,0));
        PartDefinition k2_left_arm=kind2.addOrReplaceChild("left_arm",CubeListBuilder.create(),PartPose.offset(-6.5F,-14F,0));
        PartDefinition k2_right_arm=kind2.addOrReplaceChild("right_arm",CubeListBuilder.create(),PartPose.offset(6.5F,-14F,0));
        kind2.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-5F,-16F,-3F,10F,9F,6F),PartPose.ZERO);
        kind2.addOrReplaceChild("waist",CubeListBuilder.create().texOffs(32,0).addBox(-4F,-7F,-2.5F,8F,2F,5F),PartPose.ZERO);
        k2_left_leg.addOrReplaceChild("leg_L",CubeListBuilder.create().texOffs(0,0).addBox(-1.5F,0F,-2F,3F,5F,4F),PartPose.ZERO);
        k2_right_leg.addOrReplaceChild("leg_R",CubeListBuilder.create().texOffs(0,0).addBox(-1.5F,0F,-2F,3F,5F,4F),PartPose.ZERO);
        kind2.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-3F,-21F,-2.5F,6F,5F,5F),PartPose.ZERO);
        kind2.addOrReplaceChild("brow",CubeListBuilder.create().texOffs(32,0).addBox(-3.5F,-21F,-3F,7F,1F,6F),PartPose.ZERO);
        kind2.addOrReplaceChild("eye_L",CubeListBuilder.create().texOffs(128,0).addBox(-2F,-19F,-3F,1.5F,1F,0.5F),PartPose.ZERO);
        kind2.addOrReplaceChild("eye_R",CubeListBuilder.create().texOffs(128,0).addBox(0.5F,-19F,-3F,1.5F,1F,0.5F),PartPose.ZERO);
        kind2.addOrReplaceChild("chest_plate",CubeListBuilder.create().texOffs(64,0).addBox(-3.5F,-15F,-4F,7F,5F,1F),PartPose.ZERO);
        kind2.addOrReplaceChild("core",CubeListBuilder.create().texOffs(128,0).addBox(-1F,-14F,-4.5F,2F,3F,0.5F),PartPose.ZERO);
        kind2.addOrReplaceChild("shoulder_L",CubeListBuilder.create().texOffs(64,0).addBox(-9F,-16F,-3.5F,5F,3F,7F),PartPose.ZERO);
        kind2.addOrReplaceChild("shoulder_R",CubeListBuilder.create().texOffs(64,0).addBox(4F,-16F,-3.5F,5F,3F,7F),PartPose.ZERO);
        k2_left_arm.addOrReplaceChild("arm_L",CubeListBuilder.create().texOffs(0,0).addBox(-2F,1F,-2F,4F,6F,4F),PartPose.ZERO);
        k2_right_arm.addOrReplaceChild("arm_R",CubeListBuilder.create().texOffs(0,0).addBox(-2F,1F,-2F,4F,6F,4F),PartPose.ZERO);
        k2_right_arm.addOrReplaceChild("staff",CubeListBuilder.create().texOffs(96,0).addBox(0.5F,-4F,-4F,1F,17F,1F),PartPose.ZERO);
        k2_right_arm.addOrReplaceChild("staff_crown",CubeListBuilder.create().texOffs(64,0).addBox(-1F,-6F,-5F,4F,4F,3F),PartPose.ZERO);
        k2_right_arm.addOrReplaceChild("staff_core",CubeListBuilder.create().texOffs(128,0).addBox(0F,-5F,-6F,2F,2F,1F),PartPose.ZERO);
        kind2.addOrReplaceChild("crest",CubeListBuilder.create().texOffs(96,0).addBox(-1F,-25F,-1F,2F,4F,2F),PartPose.ZERO);
        kind2.addOrReplaceChild("robe",CubeListBuilder.create().texOffs(64,0).addBox(-4.5F,-7F,-3F,9F,4F,6F),PartPose.ZERO);
        return LayerDefinition.create(mesh,160,32).bakeRoot();
    }
    @Override public void setupAnim(MinionRenderState s){
        super.setupAnim(s);
        for(int i=0;i<3;i++){
            ModelPart body=root.getChild("kind"+i);
            body.visible=i==s.style%3;
            if(!body.visible)continue;
            float walk=s.moving?(float)Math.sin(s.ageInTicks*.55F)*.55F:0;
            body.getChild("left_leg").xRot=walk;body.getChild("right_leg").xRot=-walk;
            body.getChild("left_arm").xRot=-walk*.65F;body.getChild("right_arm").xRot=walk*.65F;
            if(s.moving)body.y-=Math.abs((float)Math.sin(s.ageInTicks*.55F))*.45F;
            if(s.attackTicks>0){
                float t=1-s.attackTicks/10F;
                float swing=(float)Math.sin(Math.PI*t);
                body.getChild("right_arm").xRot=i==0?-1.9F*swing:i==1?-.5F*swing:-1.4F*swing;
                if(i==0)body.getChild("left_arm").xRot=-1.3F*swing;
                if(i==1)body.getChild("right_arm").z=swing*.9F;
                body.z-=swing*.5F;
            }
        }
    }
}
