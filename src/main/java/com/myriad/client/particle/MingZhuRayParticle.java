package com.myriad.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Short server-clipped beam section: layered full-bright geometry from one particle packet. */
public final class MingZhuRayParticle extends SingleQuadParticle {
    private final Vec3 ray;
    private final boolean giant;
    private MingZhuRayParticle(ClientLevel level,double x,double y,double z,Vec3 ray,boolean giant,SpriteSet sprites) {
        super(level,x,y,z,sprites.first());this.ray=ray;this.giant=giant;
        this.lifetime=3;this.hasPhysics=false;this.gravity=0;this.xd=this.yd=this.zd=0;
        setBoundingBox(new net.minecraft.world.phys.AABB(new Vec3(x,y,z),new Vec3(x,y,z).add(ray)).inflate(1.2));
    }
    @Override protected Layer getLayer(){return Layer.TRANSLUCENT;}
    @Override public void tick(){
        this.xo=this.x;this.yo=this.y;this.zo=this.z;
        if(this.age++>=this.lifetime)this.remove();
    }
    @Override protected int getLightCoords(float partial){return 0xF000F0;}
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial) {
        Vec3 base=new Vec3(x,y,z).subtract(camera.position()),dir=ray.normalize();
        Vec3 side=dir.cross(new Vec3(0,1,0));if(side.lengthSqr()<.001)side=new Vec3(1,0,0);
        side=side.normalize();Vec3 up=side.cross(dir).normalize();
        float fade=Math.max(0,1-(age+partial)/lifetime);
        double phase=(level.getGameTime()+partial)*.42;
        int count=Math.max(1,(int)Math.ceil(ray.length()/.22));
        for(int i=0;i<=count;i++){
            Vec3 at=base.add(ray.scale(i/(double)count));
            quad(state,camera,at,giant?.38F:.19F,0xFFF9DF,fade*.8F);
            if(i%2==0){
                double angle=phase+i*.55,r=giant?.75:.25;
                Vec3 spiral=side.scale(Math.cos(angle)*r).add(up.scale(Math.sin(angle)*r));
                quad(state,camera,at.add(spiral),giant?.16F:.09F,0xFFC04F,fade);
                quad(state,camera,at.subtract(spiral),giant?.13F:.07F,0xBDEBFF,fade*.75F);
            }
        }
        Vec3 center=base.add(ray.scale(.5));
        for(int i=0;i<12;i++){
            double angle=phase*.3+i*Math.PI/6,r=giant?1.0:.38;
            quad(state,camera,center.add(side.scale(Math.cos(angle)*r)).add(up.scale(Math.sin(angle)*r)),
                    giant?.12F:.065F,0xFFD781,fade*.8F);
        }
    }
    private void quad(QuadParticleRenderState state,Camera camera,Vec3 p,float size,int rgb,float opacity){
        var q=camera.rotation();
        state.add(getLayer(),(float)p.x,(float)p.y,(float)p.z,q.x,q.y,q.z,q.w,size,
                getU0(),getU1(),getV0(),getV1(),((int)(Math.clamp(opacity,0,1)*255)<<24)|rgb,0xF000F0);
    }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;private final boolean giant;
        public Provider(SpriteSet sprites,boolean giant){this.sprites=sprites;this.giant=giant;}
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double dx,double dy,double dz,RandomSource random){
            Vec3 ray=new Vec3(dx,dy,dz);
            if(!Double.isFinite(ray.lengthSqr())||ray.lengthSqr()<.0001||ray.lengthSqr()>17)return null;
            return new MingZhuRayParticle(level,x,y,z,ray,giant,sprites);
        }
    }
}
