package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import java.util.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Bounded local translucent/fullbright geometry using the already packaged authored pixel atlases. */
public final class AttackTrailRenderer {
    private static final Map<Integer,Identifier> IDS=Map.ofEntries(Map.entry(0,Identifier.fromNamespaceAndPath("minecraft","textures/particle/glow.png")),Map.entry(50,id("night_gray_blade")),Map.entry(52,id("night_gray_wake")),
            Map.entry(57,id("thunder_pixel_slash")),Map.entry(58,id("thunder_pixel_bolt")),Map.entry(64,id("fenjue_pixel_slash")),Map.entry(68,id("fenjue_pixel_stream")),Map.entry(71,id("fenjue_hd_fire")),Map.entry(75,id("shuo_basic_hd")),Map.entry(77,id("shuo_stream_hd")));
    private AttackTrailRenderer(){}
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath("myriad","textures/particle/vfx/"+name+".png");}
    public static void submit(RenderHandEvent event,List<Quad> quads){
        submit(event.getPoseStack(),event.getSubmitNodeCollector(),quads);
    }
    public static void submit(com.mojang.blaze3d.vertex.PoseStack stack,net.minecraft.client.renderer.SubmitNodeCollector collector,List<Quad> quads){
        if(quads.isEmpty())return;
        var groups=new LinkedHashMap<Integer,List<Quad>>();
        // Translucent quads sorted back to front; immutable snapshots outlive this render callback.
        quads.stream().limit(AttackTrailMesh.MAX_QUADS).sorted(Comparator.comparingDouble(q->(q.a().z+q.b().z+q.c().z+q.d().z)/4))
                .forEach(q->groups.computeIfAbsent(q.material(),key->new ArrayList<>()).add(q));
        for(var group:groups.entrySet()){
            var texture=IDS.get(group.getKey());if(texture==null)continue;
            var snapshot=List.copyOf(group.getValue());
            collector.submitCustomGeometry(stack,RenderTypes.entityTranslucent(texture),(pose,buffer)->{
                for(var q:snapshot){
                    int color=((int)(Math.clamp(q.alpha(),0,1)*255)<<24)|q.color();
                    float v0=q.startV(),v1=q.endV();if(q.reverseV()){float swap=v0;v0=v1;v1=swap;}
                    var normal=q.b().subtract(q.a()).cross(q.c().subtract(q.a())).normalize();
                    vertex(buffer,pose,q.a(),q.startU(),v1,color,normal);vertex(buffer,pose,q.b(),q.endU(),v1,color,normal);
                    vertex(buffer,pose,q.c(),q.endU(),v0,color,normal);vertex(buffer,pose,q.d(),q.startU(),v0,color,normal);
                }
            });
        }
    }
    private static void vertex(VertexConsumer buffer,com.mojang.blaze3d.vertex.PoseStack.Pose pose,Vec3 p,float u,float v,int color,Vec3 normal){
        buffer.addVertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
    }
}
