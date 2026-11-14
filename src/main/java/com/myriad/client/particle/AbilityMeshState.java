package com.myriad.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Finite arbitrary quads submitted through Minecraft 26.2's normal particle pipeline. */
final class AbilityMeshState extends QuadParticleRenderState {
    private record Entry(Quad q,Vec3 camera,float u0,float u1,float v0,float v1,float nearAlpha,double depth,int light,boolean flame) {}
    private final List<Entry> mesh=new ArrayList<>();
    private boolean hasModels,hasFlame,hasGround,hasOrdinary;
    void addMesh(Quad q,Vec3 camera,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,float nearAlpha) {
        addMesh(q,camera,sprite,nearAlpha,0xF000F0);
    }
    void addMesh(Quad q,Vec3 camera,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,float nearAlpha,int light) {
        addMesh(q,camera,sprite,nearAlpha,light,false);
    }
    void addMesh(Quad q,Vec3 camera,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,float nearAlpha,int light,boolean flame) {
        float u0=sprite.getU0(),u1=sprite.getU1(),v0=sprite.getV0(),v1=sprite.getV1();
        float range=u1-u0;u1=u0+range*q.endU();u0=u0+range*q.startU();
        float vRange=v1-v0;v1=v0+vRange*q.endV();v0=v0+vRange*q.startV();
        if(q.reverseV()){float swap=v0;v0=v1;v1=swap;}
        if(q.material()==0){u0=u1=(u0+u1)*.5f;v0=v1=(v0+v1)*.5f;}
        double dx=(q.a().x+q.b().x+q.c().x+q.d().x)*.25-camera.x;
        double dy=(q.a().y+q.b().y+q.c().y+q.d().y)*.25-camera.y;
        double dz=(q.a().z+q.b().z+q.c().z+q.d().z)*.25-camera.z;
        if(mesh.size()<16384){mesh.add(new Entry(q,camera,u0,u1,v0,v1,nearAlpha,dx*dx+dy*dy+dz*dz,light,flame));
            hasGround|=flame&&com.myriad.character.ability.AbilityVfxMaterials.flameGround(q.material());
            hasFlame|=flame&&!com.myriad.character.ability.AbilityVfxMaterials.flameGround(q.material());hasOrdinary|=!flame;
            hasModels|=q.material()>=com.myriad.character.ability.AbilityVfxMaterials.KEHENG_HD_RING
                    &&q.material()<=com.myriad.character.ability.AbilityVfxMaterials.RIVET_MODEL
                    ||q.material()==com.myriad.character.ability.AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL
                    ||q.material()==com.myriad.character.ability.AbilityVfxMaterials.NIGHT_NEEDLE_MODEL
                    ||q.material()>=com.myriad.character.ability.AbilityVfxMaterials.NIGHT_GRAY_BLADE
                    &&q.material()<=com.myriad.character.ability.AbilityVfxMaterials.NIGHT_GRAY_IMPACT
                    ||q.material()>=com.myriad.character.ability.AbilityVfxMaterials.THUNDER_PIXEL_SLASH
                    &&q.material()<=com.myriad.character.ability.AbilityVfxMaterials.THUNDER_PROJECTILE_MODEL
                    ||q.material()>=com.myriad.character.ability.AbilityVfxMaterials.FENJUE_PIXEL_SLASH
                    &&q.material()<=com.myriad.character.ability.AbilityVfxMaterials.FENJUE_CRISP_SLASH;}
    }
    int remaining(){return 16384-mesh.size();}
    void sortTranslucent(){hasModels=true;}
    /** A closed sword is accepted whole, never cut off at the frame budget. */
    boolean addModelBatch(List<Quad> quads,Vec3 camera,net.minecraft.client.particle.SpriteSet sprites,float nearAlpha){
        int count=0;for(var q:quads)if(modelFaceVisible(q,camera))count++;
        if(count>remaining())return false;
        for(var q:quads)if(modelFaceVisible(q,camera))addMesh(q,camera,sprites.get(Math.clamp(q.material(),0,
                com.myriad.character.ability.AbilityVfxMaterials.MAX),com.myriad.character.ability.AbilityVfxMaterials.MAX),nearAlpha);
        return true;
    }
    private static boolean modelFaceVisible(Quad q,Vec3 camera){
        if(q.material()!=com.myriad.character.ability.AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL
                &&q.material()!=com.myriad.character.ability.AbilityVfxMaterials.NIGHT_NEEDLE_MODEL
                &&q.material()!=com.myriad.character.ability.AbilityVfxMaterials.THUNDER_PROJECTILE_MODEL
                &&q.material()!=com.myriad.character.ability.AbilityVfxMaterials.KEHENG_PROJECTILE_MODEL
                &&q.material()!=com.myriad.character.ability.AbilityVfxMaterials.YIKE_PROJECTILE_MODEL
                &&q.material()!=com.myriad.character.ability.AbilityVfxMaterials.RIVET_MODEL)return true;
        return q.b().subtract(q.a()).cross(q.c().subtract(q.a())).dot(camera.subtract(q.a()))>1E-10;
    }
    @Override public void clear(){super.clear();mesh.clear();hasModels=hasFlame=hasGround=hasOrdinary=false;}
    @Override public boolean isEmpty(){return mesh.isEmpty();}
    @Override public java.util.Set<SingleQuadParticle.Layer> layers(){
        var layers=new java.util.LinkedHashSet<SingleQuadParticle.Layer>();
        if(hasOrdinary)layers.add(SingleQuadParticle.Layer.TRANSLUCENT);
        if(hasGround)layers.add(FlameTransparentLayer.GROUND);
        if(hasFlame)layers.add(FlameTransparentLayer.LAYER);
        return java.util.Collections.unmodifiableSet(layers);
    }
    @Override public void submit(SubmitNodeCollector collector,CameraRenderState camera) {
        if(!isEmpty())collector.submitQuadParticleGroup(this);
    }
    @Override public void buildLayer(SingleQuadParticle.Layer layer,VertexConsumer builder) {
        boolean ground=layer.equals(FlameTransparentLayer.GROUND),flame=ground||layer.equals(FlameTransparentLayer.LAYER);
        if(!flame&&!layer.equals(SingleQuadParticle.Layer.TRANSLUCENT))return;
        // Closed sword meshes and overlapping ash-gray planes share one back-to-front order.
        if(hasModels||hasFlame||hasGround)mesh.sort(java.util.Comparator.comparingDouble(Entry::depth).reversed());
        for(var e:mesh) {
            if(e.flame!=flame)continue;
            if(flame&&com.myriad.character.ability.AbilityVfxMaterials.flameGround(e.q.material())!=ground)continue;
            var q=e.q;int argb=((int)(Math.clamp(q.alpha()*e.nearAlpha,0,1)*255)<<24)|q.color();
            vertex(builder,q.a(),e,argb,e.u0,e.v1);vertex(builder,q.b(),e,argb,e.u1,e.v1);
            vertex(builder,q.c(),e,argb,e.u1,e.v0);vertex(builder,q.d(),e,argb,e.u0,e.v0);
        }
    }
    private static void vertex(VertexConsumer b,Vec3 p,Entry e,int color,float u,float v) {
        Vec3 relative=p.subtract(e.camera);
        b.addVertex((float)relative.x,(float)relative.y,(float)relative.z).setUv(u,v).setColor(color).setLight(e.light);
    }
}
