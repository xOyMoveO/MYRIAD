package com.myriad.client.particle;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.FogOfWarClient;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;

/** One recipe particle owns a whole authored mesh instead of dozens of networked dust particles. */
public final class AbilityMeshParticle extends SingleQuadParticle {
    public static final ParticleRenderType GROUP=new ParticleRenderType("MYRIAD_ABILITY_MESH","XM");
    private static SpriteSet sprites;
    private static int epoch;
    private static final java.util.Map<Integer,Integer> OWNER_EPOCHS=new java.util.HashMap<>();
    private static final java.util.Map<Long,Integer> SKILL_EPOCHS=new java.util.HashMap<>();
    private static final com.myriad.character.ability.AbilityVisualLifetime FLIGHTS=new com.myriad.character.ability.AbilityVisualLifetime();
    private final int bornEpoch,bornOwnerEpoch,bornSkillEpoch;
    private static long skillKey(AbilityVfxPayload p){return ((long)p.owner()<<7)|((long)p.theme()<<3)|p.variant();}
    private static AABB visualBounds(AbilityVfxPayload p){
        if(com.myriad.character.skills.shuo.ShuoSkillVfx.owns(p))return com.myriad.character.skills.shuo.ShuoSkillVfx.bounds(p);
        if(p.theme()==0)return com.myriad.character.skills.yingxiao.rework.NightVfxGeometry.bounds(p);
        if(p.theme()==1)return com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry.bounds(p);
        if(p.theme()==2)return com.myriad.character.skills.fenjue.rework.FlameVfxGeometry.bounds(p);
        if(p.theme()==11)return com.myriad.character.skills.keheng.KehengVfx.bounds(p);
        return com.myriad.character.skills.lingyuan.rework.HeavenSwordVfx.owns(p)
                ?com.myriad.character.skills.lingyuan.rework.HeavenSwordVfx.bounds(p)
                :new AABB(p.from(),p.to()).inflate(Math.max(p.radius()+.5,p.theme()==5?5:2));
    }
    private final AbilityVfxPayload recipe;
    private final FlameSurfaceProjection flameSurface=new FlameSurfaceProjection();
    private final net.minecraft.world.phys.Vec3 anchorOrigin;
    private AbilityMeshParticle(ClientLevel level,AbilityVfxPayload recipe) {
        super(level,recipe.from().x,recipe.from().y,recipe.from().z,sprites.first());
        this.recipe=recipe;var anchor=level.getEntity(recipe.anchor());anchorOrigin=com.myriad.character.skills.fenjue.rework.FlameStepVfx.anchorOrigin(recipe,anchor==null?recipe.from():anchor.position());bornEpoch=epoch;bornOwnerEpoch=OWNER_EPOCHS.getOrDefault(recipe.owner(),0);bornSkillEpoch=SKILL_EPOCHS.getOrDefault(skillKey(recipe),0);lifetime=recipe.life();
        // One-tick volley and shadow-dagger bodies interpolate from receipt, so a delayed fresh
        // packet is not already in its trail-only phase. Absolute expiry still rejects old
        // packets and bounds its lifetime; an end signal still removes it immediately.
        boolean movingBody=recipe.kind()==AbilityVfxGeometry.FLIGHT
                &&(recipe.theme()==5&&recipe.variant()==4||(recipe.theme()==0||recipe.theme()==1||recipe.theme()==11)&&recipe.instance()>0
                ||com.myriad.character.skills.yike.YikeVfxGeometry.movingBody(recipe));
        age=movingBody?0:(int)Math.clamp(level.getGameTime()-recipe.started(),0,lifetime);
        hasPhysics=false;gravity=0;xd=yd=zd=0;
        setBoundingBox(com.myriad.character.skills.fenjue.rework.FlameStepVfx.sides(recipe)&&anchor!=null
                ?visualBounds(recipe).move(anchor.position().subtract(anchorOrigin)):visualBounds(recipe));
    }
    public static void receive(AbilityVfxPayload p) {
        var mc=Minecraft.getInstance();
        if(!p.valid())return;
        if(p.kind()==AbilityVfxGeometry.CANCEL){
            com.myriad.client.firstperson.ShuoHeldLight.receive(p);
            if(p.angle()==2)FLIGHTS.end(p,mc.level==null?p.started():mc.level.getGameTime());
            else if(p.angle()==1)SKILL_EPOCHS.merge(skillKey(p),1,Integer::sum);
            else {OWNER_EPOCHS.merge(p.owner(),1,Integer::sum);SKILL_EPOCHS.keySet().removeIf(key->(key>>7)==p.owner());FLIGHTS.clearOwner(p.owner());}
            return;
        }
        if(mc.level==null||mc.player==null||sprites==null||!ClientDataHolder.modEnabled()||!p.valid())return;
        if(p.personal()&&p.owner()!=mc.player.getId())return;
        if(!p.personal()&&!FogOfWarClient.shouldRenderPlayer(p.owner())
                &&!(com.myriad.character.skills.shuo.ShuoSkillVfx.owns(p)&&p.owner()==mc.player.getId())
                &&!(p.theme()==12&&p.anchor()<0))return;
        if(p.anchor()!=mc.player.getId()&&mc.level.getEntity(p.anchor()) instanceof net.minecraft.world.entity.player.Player
                &&!FogOfWarClient.shouldRenderPlayer(p.anchor()))return;
        if(mc.player.position().distanceToSqr(p.from())>Math.pow(80+p.radius(),2))return;
        FLIGHTS.prune(mc.level.getGameTime());
        if(com.myriad.character.ability.AbilityVisualLifetime.expired(p,mc.level.getGameTime())||FLIGHTS.finished(p,mc.level.getGameTime()))return;
        com.myriad.client.firstperson.ShuoHeldLight.receive(p);
        if(p.theme()==12&&(p.angle()==com.myriad.character.skills.yike.YikeVfx.MUZZLE
                ||p.angle()==com.myriad.character.skills.yike.YikeVfx.LAST_MUZZLE))
            com.myriad.client.YikeClientState.onMuzzle(p.owner(),p.started());
        // Keep a short, purely planar record of each received thunder flight segment.
        // It is not a FLIGHT body: new samples/end signals still kill the model immediately,
        // while this absolute-TTL wake dissipates in 0.30/0.35s. Owner/skill epochs clear both.
        var wake=com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry.wakeForFlight(p);
        if(wake!=null&&!com.myriad.character.ability.AbilityVisualLifetime.expired(wake,mc.level.getGameTime()))
            mc.particleEngine.add(new AbilityMeshParticle(mc.level,wake));
        var chronometricWake=com.myriad.character.skills.keheng.KehengVfx.wakeForFlight(p);
        if(chronometricWake!=null&&!com.myriad.character.ability.AbilityVisualLifetime.expired(chronometricWake,mc.level.getGameTime()))
            mc.particleEngine.add(new AbilityMeshParticle(mc.level,chronometricWake));
        var rainbowWake=com.myriad.character.skills.yike.YikeVfxGeometry.wakeForFlight(p);
        if(rainbowWake!=null&&!com.myriad.character.ability.AbilityVisualLifetime.expired(rainbowWake,mc.level.getGameTime()))
            mc.particleEngine.add(new AbilityMeshParticle(mc.level,rainbowWake));
        mc.particleEngine.add(new AbilityMeshParticle(mc.level,p));
    }
    public static void reset(){epoch++;OWNER_EPOCHS.clear();SKILL_EPOCHS.clear();FLIGHTS.clear();com.myriad.client.firstperson.ShuoHeldLight.reset();com.myriad.client.YikeClientState.reset();}
    public static void registerGroup(RegisterParticleGroupsEvent event){event.register(GROUP,Group::new);}
    @Override public ParticleRenderType getGroup(){return GROUP;}
    @Override protected Layer getLayer(){return Layer.TRANSLUCENT;}
    @Override public void tick() {
        xo=x;yo=y;zo=z;
        var owner=level.getEntity(recipe.owner());
        if(com.myriad.character.ability.AbilityVisualLifetime.expired(recipe,level.getGameTime())||FLIGHTS.finished(recipe,level.getGameTime())){remove();return;}
        FLIGHTS.prune(level.getGameTime());
        var anchor=level.getEntity(recipe.anchor());
        if(recipe.anchor()>=0){if(anchor==null||!anchor.isAlive()){remove();return;}
            setBoundingBox(visualBounds(recipe)
                    .move(anchor.position().subtract(anchorOrigin)));}
        if(age++>=lifetime||epoch!=bornEpoch||bornOwnerEpoch!=OWNER_EPOCHS.getOrDefault(recipe.owner(),0)||bornSkillEpoch!=SKILL_EPOCHS.getOrDefault(skillKey(recipe),0)||!ClientDataHolder.modEnabled()||owner==null||!owner.isAlive())remove();
    }
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial) {
        if(!(state instanceof AbilityMeshState mesh)||bornEpoch!=epoch||bornOwnerEpoch!=OWNER_EPOCHS.getOrDefault(recipe.owner(),0)||bornSkillEpoch!=SKILL_EPOCHS.getOrDefault(skillKey(recipe),0)||!ClientDataHolder.modEnabled())return;
        if(!isAlive()||com.myriad.character.ability.AbilityVisualLifetime.expired(recipe,level.getGameTime())||FLIGHTS.finished(recipe,level.getGameTime()))return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        if(!recipe.personal()&&!FogOfWarClient.shouldRenderPlayer(recipe.owner())
                &&!(com.myriad.character.skills.shuo.ShuoSkillVfx.owns(recipe)&&recipe.owner()==mc.player.getId())
                &&!(recipe.theme()==12&&recipe.anchor()<0))return;
        if(recipe.anchor()!=mc.player.getId()&&level.getEntity(recipe.anchor()) instanceof net.minecraft.world.entity.player.Player
                &&!FogOfWarClient.shouldRenderPlayer(recipe.anchor()))return;
        double distance=camera.position().distanceTo(recipe.from());
        boolean reduced=mc.options.particles().get()!=net.minecraft.server.level.ParticleStatus.ALL||distance>32;
        boolean thunderModel=com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry.usesProjectileModel(recipe);
        if((recipe.theme()==5||thunderModel||recipe.theme()==0&&recipe.kind()==AbilityVfxGeometry.FLIGHT&&recipe.variant()==2)&&!recipe.personal()){
            int budget=recipe.theme()==5?4096:thunderModel?1200:192;
            if(mesh.remaining()<(recipe.theme()==5?256:thunderModel?320:64))return;
            reduced|=distance>20||mesh.remaining()<budget;
        }
        float near=recipe.kind()==AbilityVfxGeometry.RETICLE||recipe.kind()==AbilityVfxGeometry.INVALID?1:
                (float)Math.clamp(distance/.9,.42,1);
        if(com.myriad.player.LevelUpBeamGeometry.owns(recipe)&&mc.options.getCameraType().isFirstPerson()
                &&mc.getCameraEntity()!=null&&mc.getCameraEntity().getId()==recipe.owner())near*=.22f;
        if(recipe.theme()==1&&mc.options.getCameraType().isFirstPerson()
                &&mc.getCameraEntity()!=null&&mc.getCameraEntity().getId()==recipe.owner())
            near*=com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry.ownerOpacity(recipe.kind());
        if(recipe.theme()==2&&mc.options.getCameraType().isFirstPerson()
                &&mc.getCameraEntity()!=null&&mc.getCameraEntity().getId()==recipe.owner())
            near=com.myriad.character.skills.fenjue.rework.FlameVfxGeometry.firstPersonOpacity(recipe,near);
        if(recipe.theme()==10&&mc.options.getCameraType().isFirstPerson()&&mc.getCameraEntity()!=null&&mc.getCameraEntity().getId()==recipe.owner())
            near*=com.myriad.character.skills.shuo.ShuoDashVfx.ownerOpacity(recipe);
        if(com.myriad.character.skills.shuo.ShuoVfx.owns(recipe)&&mc.options.getCameraType().isFirstPerson()
                &&mc.getCameraEntity()==mc.player&&mc.getCameraEntity().getId()==recipe.owner())return;
        if(com.myriad.character.skills.shuo.ShuoSkillVfx.ownerHidden(recipe)&&mc.options.getCameraType().isFirstPerson()
                &&mc.getCameraEntity()!=null&&mc.getCameraEntity().getId()==recipe.owner())return;
        AbilityVfxPayload displayed=recipe;var anchor=level.getEntity(recipe.anchor());
        if(anchor!=null){var offset=anchor.getPosition(partial).subtract(anchorOrigin);
            displayed=new AbilityVfxPayload(recipe.owner(),recipe.anchor(),recipe.theme(),recipe.variant(),recipe.kind(),
                    recipe.from().add(offset),recipe.to().add(offset),recipe.radius(),recipe.angle(),recipe.life(),recipe.started(),recipe.personal(),recipe.instance());}
        float visualAge=Math.max(0,age-1+partial);
        var geometry=AbilityVfxGeometry.build(displayed,visualAge,reduced);
        if(com.myriad.player.LevelUpBeamGeometry.owns(recipe)
                ||com.myriad.character.skills.shuo.ShuoSkillVfx.owns(recipe))mesh.sortTranslucent();
        if((recipe.theme()==5||thunderModel||recipe.theme()==0&&recipe.kind()==AbilityVfxGeometry.FLIGHT&&recipe.variant()==2)&&!recipe.personal()){
            if(!mesh.addModelBatch(geometry,camera.position(),sprites,near)&&!reduced)
                mesh.addModelBatch(AbilityVfxGeometry.build(displayed,visualAge,true),camera.position(),sprites,near);
            return;
        }
        boolean majorFlame=com.myriad.character.skills.fenjue.rework.FlameMajorVfx.owns(recipe);
        for(var original:geometry){
            var q=original;
            if(majorFlame&&(com.myriad.character.ability.AbilityVfxMaterials.flameGround(q.material())
                    ||q.material()==com.myriad.character.ability.AbilityVfxMaterials.FENJUE_HD_FIRE&&recipe.kind()!=AbilityVfxGeometry.SHIELD&&recipe.kind()!=AbilityVfxGeometry.TRAIL)){
                q=flameSurface.project(q,level,displayed.from().y,com.myriad.character.ability.AbilityVfxMaterials.flameGround(q.material()));
                if(q==null)continue;
            }
            int light=0xF000F0;
            if(com.myriad.character.skills.fenjue.rework.FlameCrossSlashVfx.owns(recipe)
                    ||com.myriad.character.skills.fenjue.rework.FlameStepVfx.finish(recipe)||majorFlame){
                var center=q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25);var pos=net.minecraft.core.BlockPos.containing(center);
                int block=level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos),sky=level.getBrightness(net.minecraft.world.level.LightLayer.SKY,pos);
                light=majorFlame?com.myriad.character.skills.fenjue.rework.FlameMajorVfx.sceneLight((block<<4)|(sky<<20),com.myriad.character.ability.AbilityVfxMaterials.flameGround(q.material()))
                        :com.myriad.character.skills.fenjue.rework.FlameCrossSlashVfx.sceneLight((block<<4)|(sky<<20),q.color());
            }
            mesh.addMesh(q,camera.position(),sprites.get(Math.clamp(q.material(),0,
                    com.myriad.character.ability.AbilityVfxMaterials.MAX),com.myriad.character.ability.AbilityVfxMaterials.MAX),near,light,
                    majorFlame||com.myriad.character.skills.fenjue.rework.FlameHitVfx.owns(recipe));
        }
    }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        public Provider(SpriteSet set){sprites=set;}
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,
                double dx,double dy,double dz,RandomSource random){return null;}
    }
    private static final class Group extends ParticleGroup<AbilityMeshParticle> {
        private final AbilityMeshState state=new AbilityMeshState();
        private Group(ParticleEngine engine){super(engine);}
        @Override public boolean add(Particle particle) {
            if(!(particle instanceof AbilityMeshParticle))return false;
            var incoming=(AbilityMeshParticle)particle;
            if(incoming.recipe.theme()==11||incoming.recipe.theme()==12){
                if(particles.stream().anyMatch(old->incoming.recipe.theme()==11
                        ?com.myriad.character.skills.keheng.KehengVfx.obsolete(incoming.recipe,old.recipe)
                        :com.myriad.character.skills.yike.YikeVfxGeometry.obsolete(incoming.recipe,old.recipe)))return false;
                particles.removeIf(old->{boolean replace=incoming.recipe.theme()==11
                        ?com.myriad.character.skills.keheng.KehengVfx.replaces(incoming.recipe,old.recipe)
                        :com.myriad.character.skills.yike.YikeVfxGeometry.replaces(incoming.recipe,old.recipe);
                    if(replace)old.remove();return replace;});
            }
            if(com.myriad.character.skills.fenjue.rework.FlameMajorVfx.owns(incoming.recipe)){
                if(particles.stream().anyMatch(old->com.myriad.character.skills.fenjue.rework.FlameVisualMerge.obsolete(incoming.recipe,old.recipe)))return false;
                particles.removeIf(old->{boolean replace=com.myriad.character.skills.fenjue.rework.FlameVisualMerge.replaces(incoming.recipe,old.recipe);
                    if(replace)old.remove();return replace;});
            }
            if(com.myriad.player.LevelUpBeamGeometry.owns(incoming.recipe))
                particles.removeIf(old->{boolean same=com.myriad.player.LevelUpBeamGeometry.owns(old.recipe)&&old.recipe.owner()==incoming.recipe.owner();
                    if(same)old.remove();return same;});
            if(incoming.recipe.kind()==AbilityVfxGeometry.FLIGHT&&incoming.recipe.instance()>0){
                if(FLIGHTS.finished(incoming.recipe,incoming.level.getGameTime())||com.myriad.character.ability.AbilityVisualLifetime.expired(incoming.recipe,incoming.level.getGameTime()))return false;
                particles.removeIf(old->{boolean same=old.recipe.kind()==AbilityVfxGeometry.FLIGHT&&old.recipe.owner()==incoming.recipe.owner()
                        &&old.recipe.theme()==incoming.recipe.theme()&&old.recipe.variant()==incoming.recipe.variant()&&old.recipe.instance()==incoming.recipe.instance();
                    if(same)old.remove();return same;});
            }
            if(incoming.recipe.theme()==5&&incoming.recipe.kind()==AbilityVfxGeometry.CHARGE)
                particles.removeIf(old->{boolean same=old.recipe.theme()==5&&old.recipe.kind()==AbilityVfxGeometry.CHARGE
                        &&old.recipe.owner()==incoming.recipe.owner()&&old.recipe.variant()==incoming.recipe.variant();
                    if(same)old.remove();return same;});
            if(incoming.recipe.theme()==5&&incoming.recipe.kind()==AbilityVfxGeometry.FLIGHT&&incoming.recipe.instance()==0)
                particles.removeIf(old->{boolean contiguous=old.recipe.theme()==5&&old.recipe.kind()==AbilityVfxGeometry.FLIGHT
                        &&old.recipe.owner()==incoming.recipe.owner()&&old.recipe.variant()==incoming.recipe.variant()
                        &&old.recipe.to().distanceToSqr(incoming.recipe.from())<1E-8;
                    if(contiguous)old.remove();return contiguous;});
            if(!incoming.recipe.personal()&&incoming.recipe.kind()==AbilityVfxGeometry.WALL)
                particles.removeIf(old->{boolean same=old.recipe.owner()==incoming.recipe.owner()
                        &&old.recipe.variant()==incoming.recipe.variant()&&old.recipe.kind()==AbilityVfxGeometry.WALL;
                    if(same)old.remove();return same;});
            if(!incoming.recipe.personal()&&incoming.recipe.kind()==AbilityVfxGeometry.SEAL&&incoming.recipe.angle()!=359){
                boolean covered=particles.stream().anyMatch(old->old.recipe.owner()==incoming.recipe.owner()
                        &&old.recipe.variant()==incoming.recipe.variant()&&old.recipe.kind()==AbilityVfxGeometry.SEAL
                        &&!old.recipe.personal()&&old.recipe.life()>=20
                        &&(old.recipe.anchor()==old.recipe.owner()||old.recipe.from().distanceToSqr(incoming.recipe.from())<.0025)
                        &&Math.abs(old.recipe.radius()-incoming.recipe.radius())<.05);
                if(covered&&incoming.recipe.life()<20)return false;
            }
            if(!incoming.recipe.personal()&&incoming.recipe.anchor()>=0&&incoming.recipe.kind()==AbilityVfxGeometry.SHIELD)
                particles.removeIf(old->{boolean same=old.recipe.owner()==incoming.recipe.owner()&&old.recipe.anchor()==incoming.recipe.anchor()
                        &&old.recipe.variant()==incoming.recipe.variant()&&old.recipe.kind()==incoming.recipe.kind();
                    if(same)old.remove();return same;});
            if(incoming.recipe.personal()&&incoming.recipe.kind()!=AbilityVfxGeometry.CANCEL)
                particles.removeIf(old->{boolean same=old.recipe.personal()&&old.recipe.owner()==incoming.recipe.owner()
                        &&old.recipe.variant()==incoming.recipe.variant()
                        &&(old.recipe.kind()==incoming.recipe.kind()
                        ||((old.recipe.kind()==AbilityVfxGeometry.RETICLE||old.recipe.kind()==AbilityVfxGeometry.INVALID)
                        &&(incoming.recipe.kind()==AbilityVfxGeometry.RETICLE||incoming.recipe.kind()==AbilityVfxGeometry.INVALID)));
                    if(same)old.remove();return same;});
            if(particles.size()>=256) {
                // Reserve previews by retiring a decorative effect first.
                var disposable=particles.stream().filter(p->!p.recipe.personal()).findFirst().orElse(null);
                if(disposable==null)return false;
                disposable.remove();particles.remove(disposable);
            }
            return super.add(particle);
        }
        @Override public ParticleGroupRenderState extractRenderState(Frustum frustum,Camera camera,float partial) {
            state.clear();
            for(var particle:particles)if(particle.recipe.personal()&&frustum.isVisible(particle.getBoundingBox()))particle.extract(state,camera,partial);
            for(var particle:particles)if(!particle.recipe.personal()&&(particle.recipe.kind()==AbilityVfxGeometry.SEAL
                    ||particle.recipe.kind()==AbilityVfxGeometry.WALL||particle.recipe.kind()==AbilityVfxGeometry.CHARGE)
                    &&frustum.isVisible(particle.getBoundingBox()))particle.extract(state,camera,partial);
            for(var particle:particles)if(!particle.recipe.personal()&&particle.recipe.kind()!=AbilityVfxGeometry.SEAL
                    &&particle.recipe.kind()!=AbilityVfxGeometry.WALL&&particle.recipe.kind()!=AbilityVfxGeometry.CHARGE
                    &&frustum.isVisible(particle.getBoundingBox()))particle.extract(state,camera,partial);
            return state;
        }
    }
}
