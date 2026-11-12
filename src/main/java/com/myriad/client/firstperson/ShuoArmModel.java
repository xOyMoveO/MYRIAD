package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.Direction;

/** Rigid skin hand and short straight forearm. No elbow, segmented deformation or camera anchor. */
public final class ShuoArmModel {
    public static final float CUT_Y=4;
    private ShuoArmModel(){}
    public static ModelPart bake(boolean slim,boolean left){
        String side=left?"left":"right";
        var source=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot().getChild(side+"_arm");
        var body=new ArrayList<ModelPart.Cube>();var sleeve=new ArrayList<ModelPart.Cube>();
        source.visit(new PoseStack(),(pose,path,index,cube)->(path.isEmpty()?body:sleeve).add(crop(cube)));
        return new ModelPart(body,Map.of(side+"_sleeve",new ModelPart(sleeve,Map.of())));
    }
    private static ModelPart.Cube crop(ModelPart.Cube source){
        // The new proximal cap is forearm skin, not the old shoulder/shirt top texture.
        float[] cap=capUv(source);
        var cube=new ModelPart.Cube(0,0,source.minX,CUT_Y,source.minZ,source.maxX-source.minX,
            source.maxY-CUT_Y,source.maxZ-source.minZ,0,0,0,false,64,64,EnumSet.allOf(Direction.class));
        for(int f=0;f<source.polygons.length;f++){
            var face=source.polygons[f];var vertices=new ModelPart.Vertex[4];
            for(int i=0;i<4;i++){
                var v=face.vertices()[i];float y=Math.max(CUT_Y,v.y()),u=v.u(),uv=v.v();
                if(y!=v.y())for(var edge:face.vertices())if(edge.x()==v.x()&&edge.z()==v.z()&&edge.y()!=v.y()){
                    float t=(y-v.y())/(edge.y()-v.y());u=v.u()+(edge.u()-v.u())*t;uv=v.v()+(edge.v()-v.v())*t;break;
                }
                if(face.normal().y()<-.5f){u=cap[0];uv=cap[1];}
                vertices[i]=new ModelPart.Vertex(v.x(),y,v.z(),u,uv);
            }
            cube.polygons[f]=new ModelPart.Polygon(vertices,face.normal());
        }
        return cube;
    }
    private static float[] capUv(ModelPart.Cube source){
        for(var face:source.polygons)if(face.normal().z()<-.5f){
            float u=0,v=0;int count=0;
            for(var a:face.vertices())for(var b:face.vertices())if(a.y()<CUT_Y&&b.y()>CUT_Y&&a.x()==b.x()&&a.z()==b.z()){
                float t=(CUT_Y-a.y())/(b.y()-a.y());u+=a.u()+(b.u()-a.u())*t;v+=a.v()+(b.v()-a.v())*t;count++;
            }
            if(count>0)return new float[]{u/count,v/count};
        }
        throw new IllegalStateException("Player arm has no vertical skin edge at the forearm cut");
    }
}
