package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.Direction;
import java.util.*;

/** Minecraft fist/UVs and a short proximal sleeve continuation; no detached shoulder cap or invented fingers. */
public final class RivetArmModel {
    private RivetArmModel(){}
    public static ModelPart bake(boolean slim,boolean left){
        var source=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64)
                .bakeRoot().getChild(left?"left_arm":"right_arm");
        var body=new ArrayList<ModelPart.Cube>();var sleeve=new ArrayList<ModelPart.Cube>();
        source.visit(new PoseStack(),(pose,path,index,cube)->{
            var out=path.isEmpty()?body:sleeve;out.add(segment(cube,true));out.add(segment(cube,false));
        });
        return new ModelPart(body,Map.of((left?"left":"right")+"_sleeve",new ModelPart(sleeve,Map.of())));
    }
    private static ModelPart.Cube segment(ModelPart.Cube source,boolean fist){
        var result=new ModelPart.Cube(0,0,source.minX,source.minY,source.minZ,
                source.maxX-source.minX,source.maxY-source.minY,source.maxZ-source.minZ,
                0,0,0,false,64,64,EnumSet.allOf(Direction.class));
        for(int f=0;f<source.polygons.length;f++){
            var face=source.polygons[f];var vertices=new ModelPart.Vertex[4];
            for(int i=0;i<4;i++){
                var v=face.vertices()[i];float cut=fist?Math.max(6,v.y()):Math.min(6,v.y()),u=v.u(),uv=v.v();
                if(cut!=v.y())for(var edge:face.vertices())if(edge.x()==v.x()&&edge.z()==v.z()&&edge.y()!=v.y()){
                    float t=(cut-v.y())/(edge.y()-v.y());u=v.u()+(edge.u()-v.u())*t;uv=v.v()+(edge.v()-v.v())*t;break;
                }
                // 8 original proximal pixels become 10; the four-pixel fist stays untouched.
                float y=!fist&&cut<6?6+(cut-6)*1.25f:cut;
                vertices[i]=new ModelPart.Vertex(v.x(),y,v.z(),u,uv);
            }
            result.polygons[f]=new ModelPart.Polygon(vertices,face.normal());
        }
        return result;
    }
}
