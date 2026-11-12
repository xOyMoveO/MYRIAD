package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Private view-model mesh: vanilla skin/UVs and fist, with the hidden forearm continued off screen. */
public final class NightDaggerArmModel {
    private NightDaggerArmModel() { }
    public static ModelPart bake(boolean slim) {
        var source=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
        return new ModelPart(List.of(),Map.of("right_arm",arm(source,"right"),"left_arm",arm(source,"left")));
    }
    private static ModelPart arm(ModelPart root,String side) {
        List<ModelPart.Cube> hand=new ArrayList<>(),sleeve=new ArrayList<>();
        root.getChild(side+"_arm").visit(new PoseStack(),(pose,path,index,cube)->{
            var target=path.isEmpty() ? hand : sleeve;
            target.add(segment(cube,true));target.add(segment(cube,false));
        });
        return new ModelPart(hand,Map.of(side+"_sleeve",new ModelPart(sleeve,Map.of())));
    }
    private static ModelPart.Cube segment(ModelPart.Cube source,boolean fist) {
        var result=new ModelPart.Cube(0,0,source.minX,source.minY,source.minZ,
                source.maxX-source.minX,source.maxY-source.minY,source.maxZ-source.minZ,
                0,0,0,false,64,64,EnumSet.allOf(Direction.class));
        for(int f=0;f<source.polygons.length;f++) {
            var polygon=source.polygons[f];var vertices=new ModelPart.Vertex[4];
            for(int i=0;i<4;i++) {
                var v=polygon.vertices()[i];
                float cut=fist ? Math.max(6,v.y()) : Math.min(6,v.y());
                float u=v.u(),uvY=v.v();
                if(cut!=v.y()) {
                    // Split the original face/UV edge at the wrist BEFORE extending the hidden forearm.
                    for(var edge:polygon.vertices()) {
                        if(edge.x()==v.x() && edge.z()==v.z() && edge.y()!=v.y()) {
                            float t=(cut-v.y())/(edge.y()-v.y());
                            u=v.u()+(edge.u()-v.u())*t;uvY=v.v()+(edge.v()-v.v())*t;break;
                        }
                    }
                }
                float y=!fist && cut<6 ? 6+(cut-6)*3.6f : cut;
                vertices[i]=new ModelPart.Vertex(v.x(),y,v.z(),u,uvY);
            }
            result.polygons[f]=new ModelPart.Polygon(vertices,polygon.normal());
        }
        return result;
    }
}
