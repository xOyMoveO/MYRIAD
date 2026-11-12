package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import java.util.*;

/** Existing authored textures in a bounded local view layer. Never world-space floating particles. */
final class ThunderSkillHandRenderer {
    private static final String[] TEXTURES={"thunder_pixel_slash","thunder_pixel_bolt","thunder_pixel_impact","thunder_pixel_seal","thunder_pixel_corona","thunder_pixel_pillar"};
    private static final Identifier[] IDS=Arrays.stream(TEXTURES).map(n->Identifier.fromNamespaceAndPath("myriad","textures/particle/vfx/"+n+".png")).toArray(Identifier[]::new);
    private ThunderSkillHandRenderer(){}
    static void submit(RenderHandEvent event,PoseStack stack,List<Quad> quads){
        if(quads.isEmpty())return;
        var groups=new LinkedHashMap<Integer,List<Quad>>();
        for(var q:quads)groups.computeIfAbsent(q.material(),k->new ArrayList<>()).add(q);
        for(var group:groups.entrySet()){
            int index=group.getKey()-57;if(index<0||index>=IDS.length)continue;
            var snapshot=List.copyOf(group.getValue());
            event.getSubmitNodeCollector().submitCustomGeometry(stack,RenderTypes.entityTranslucent(IDS[index]),(pose,buffer)->{
                for(var q:snapshot){
                    int color=((int)(Math.clamp(q.alpha(),0,1)*255)<<24)|q.color();
                    float v0=q.startV(),v1=q.endV();if(q.reverseV()){float swap=v0;v0=v1;v1=swap;}
                    Vec3 normal=q.b().subtract(q.a()).cross(q.c().subtract(q.a())).normalize();
                    vertex(buffer,pose,q.a(),q.startU(),v1,color,normal);
                    vertex(buffer,pose,q.b(),q.endU(),v1,color,normal);
                    vertex(buffer,pose,q.c(),q.endU(),v0,color,normal);
                    vertex(buffer,pose,q.d(),q.startU(),v0,color,normal);
                }
            });
        }
    }
    private static void vertex(VertexConsumer buffer,PoseStack.Pose pose,Vec3 v,float u,float vv,int color,Vec3 normal){
        buffer.addVertex(pose.pose(),(float)v.x,(float)v.y,(float)v.z).setColor(color).setUv(u,vv)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
    }
}
