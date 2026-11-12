package com.myriad.client;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
/** Geometry compiled from siege_minion_red.spec.json; native joints share the spec pivots. */
public final class SiegeMinionModel extends EntityModel<MinionRenderState>{
 public SiegeMinionModel(){super(create());}
 private static ModelPart create(){
  MeshDefinition mesh=new MeshDefinition();
  var body=mesh.getRoot().addOrReplaceChild("siege",CubeListBuilder.create(),PartPose.offset(0,24,0));
  var left_leg=body.addOrReplaceChild("left_leg",CubeListBuilder.create(),PartPose.offset(-4F,-9F,0F));
  var right_leg=body.addOrReplaceChild("right_leg",CubeListBuilder.create(),PartPose.offset(4F,-9F,0F));
  var left_arm=body.addOrReplaceChild("left_arm",CubeListBuilder.create(),PartPose.offset(-9.5F,-23F,0F));
  var right_arm=body.addOrReplaceChild("right_arm",CubeListBuilder.create(),PartPose.offset(9.5F,-23F,0F));
  body.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-7F,-24F,-5F,14F,12F,10F),PartPose.ZERO);
  body.addOrReplaceChild("waist",CubeListBuilder.create().texOffs(64,0).addBox(-6F,-12F,-4F,12F,3F,8F),PartPose.ZERO);
  body.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4F,-30F,-4F,8F,6F,8F),PartPose.ZERO);
  body.addOrReplaceChild("crown",CubeListBuilder.create().texOffs(128,0).addBox(-5F,-32F,-4.5F,10F,2F,9F),PartPose.ZERO);
  body.addOrReplaceChild("chest",CubeListBuilder.create().texOffs(64,0).addBox(-6F,-23F,-7F,12F,8F,2F),PartPose.ZERO);
  body.addOrReplaceChild("core",CubeListBuilder.create().texOffs(192,0).addBox(-2F,-21F,-8F,4F,4F,1F),PartPose.ZERO);
  body.addOrReplaceChild("eye_L",CubeListBuilder.create().texOffs(192,0).addBox(-3F,-28F,-5F,2F,1F,1F),PartPose.ZERO);
  body.addOrReplaceChild("eye_R",CubeListBuilder.create().texOffs(192,0).addBox(1F,-28F,-5F,2F,1F,1F),PartPose.ZERO);
  left_leg.addOrReplaceChild("leg_L",CubeListBuilder.create().texOffs(0,0).addBox(-3F,0F,-3.5F,6F,9F,7F),PartPose.ZERO);
  right_leg.addOrReplaceChild("leg_R",CubeListBuilder.create().texOffs(0,0).addBox(-3F,0F,-3.5F,6F,9F,7F),PartPose.ZERO);
  left_leg.addOrReplaceChild("foot_L",CubeListBuilder.create().texOffs(64,0).addBox(-3.5F,6F,-6F,7F,3F,10F),PartPose.ZERO);
  right_leg.addOrReplaceChild("foot_R",CubeListBuilder.create().texOffs(64,0).addBox(-3.5F,6F,-6F,7F,3F,10F),PartPose.ZERO);
  left_arm.addOrReplaceChild("arm_L",CubeListBuilder.create().texOffs(0,0).addBox(-2.5F,1F,-3F,5F,9F,6F),PartPose.ZERO);
  right_arm.addOrReplaceChild("arm_R",CubeListBuilder.create().texOffs(0,0).addBox(-2.5F,1F,-3F,5F,9F,6F),PartPose.ZERO);
  left_arm.addOrReplaceChild("hammer_L",CubeListBuilder.create().texOffs(64,0).addBox(-2.5F,9F,-6F,7F,8F,10F),PartPose.ZERO);
  right_arm.addOrReplaceChild("hammer_R",CubeListBuilder.create().texOffs(64,0).addBox(-4.5F,9F,-6F,7F,8F,10F),PartPose.ZERO);
  left_arm.addOrReplaceChild("shoulder_L",CubeListBuilder.create().texOffs(128,0).addBox(-2.5F,-4F,-5F,7F,4F,10F),PartPose.ZERO);
  right_arm.addOrReplaceChild("shoulder_R",CubeListBuilder.create().texOffs(128,0).addBox(-4.5F,-4F,-5F,7F,4F,10F),PartPose.ZERO);
  left_arm.addOrReplaceChild("seal_L",CubeListBuilder.create().texOffs(192,0).addBox(-1F,11F,-7F,4F,3F,1F),PartPose.ZERO);
  right_arm.addOrReplaceChild("seal_R",CubeListBuilder.create().texOffs(192,0).addBox(-3F,11F,-7F,4F,3F,1F),PartPose.ZERO);
  return LayerDefinition.create(mesh,256,64).bakeRoot();
 }
 @Override public void setupAnim(MinionRenderState s){
  super.setupAnim(s);var body=root.getChild("siege");
  float walk=s.moving?(float)Math.sin(s.ageInTicks*.36F)*.45F:0;
  body.getChild("left_leg").xRot=walk;body.getChild("right_leg").xRot=-walk;
  body.getChild("left_arm").xRot=-walk*.4F;body.getChild("right_arm").xRot=walk*.4F;
  if(s.moving)body.y-=Math.abs((float)Math.sin(s.ageInTicks*.36F))*.8F;
  if(s.attackTicks>0){float t=1-s.attackTicks/10F;float slam=(float)Math.sin(Math.PI*t);
   body.getChild("left_arm").xRot=-2.1F*slam;body.getChild("right_arm").xRot=-2.1F*slam;
   body.xRot=slam*.2F;body.y+=slam*.8F;
  }
 }
}
