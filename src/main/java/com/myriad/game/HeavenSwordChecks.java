package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.lingyuan.rework.*;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Real server timeline, exact body/rectangle contact and packaged moving giant-sword geometry. */
final class HeavenSwordChecks {
    private record Fixture(ServerPlayer player,Vec3 origin,List<LivingEntity> targets,DefenseTowerEntity tower){}
    static void start(GameTestHelper h){
        geometry(h);
        List<Fixture> fixtures=new ArrayList<>();
        try{
            for(int i=0;i<4;i++)fixtures.add(fixture(h,i));
            for(int index=0;index<fixtures.size();index++){
                var fixture=fixtures.get(index);var p=fixture.player;var data=PlayerDataProvider.get(p);
                if(index==0)p.setPos(fixture.origin.add(0,1.25,0));
                var skill=FirstGroupSkills.byId("g2_lingyuan_ub");
                data.setSkillLevel(skill.getId(),3);
                var cast=AbilityRuntime.context(p,skill);skill.release(cast);skill.present(cast);
                h.assertTrue(SkillChargeSystem.active(p)&&AbilityRuntime.channeling(p)&&p.getAttributeValue(Attributes.MOVEMENT_SPEED)==0,
                        "Actual Heaven Sword starts a stationary charge");
                // Deliberately look behind and up; damage and telegraph must keep the original direction.
                p.setYRot(180);p.setXRot(-80);
            }
        }catch(Throwable failure){fixtures.forEach(HeavenSwordChecks::cleanup);throw failure;}
        boolean[] done={false};
        for(int tick=1;tick<=38;tick++){
            final int elapsed=tick;
            h.runAfterDelay(tick,()->{
                if(done[0])return;
                try{
                    if(elapsed==10)PlayerDataProvider.get(fixtures.get(1).player).setBuff("g1_stun",10);
                    if(elapsed==26)PlayerDataProvider.get(fixtures.get(2).player).setBuff("g1_stun",10);
                    if(elapsed==12)AbilityRuntime.clear(fixtures.get(3).player);
                    if(elapsed==3)fixtures.getFirst().player.setPos(fixtures.getFirst().origin);
                    for(var fixture:fixtures)AbilityRuntime.tick(fixture.player);
                    var success=fixtures.getFirst();
                    if(elapsed<24)h.assertTrue(AbilityRuntime.channeling(success.player),"Actual Heaven Sword survives a 1.25m jump landing during its charge");
                    if(elapsed<30)for(var target:success.targets)h.assertTrue(target.getHealth()==1000,"No damage before the blade lands");
                    if(elapsed==25)h.assertTrue(SkillChargeSystem.active(success.player),"Chop keeps the movement lock after the charging phase");
                    if(elapsed==30||elapsed==38){
                        for(int i=0;i<success.targets.size();i++){
                            double expected=i<3?380:1000;
                            h.assertTrue(Math.abs(success.targets.get(i).getHealth()-expected)<.05,
                                    "Rank3 / S100: exactly one 620 hit inside 15x3, no damage behind/beyond/outside; target="+i+", hp="+success.targets.get(i).getHealth());
                        }
                        h.assertTrue(success.tower.getHealth()==1000,"Skill never damages a defense building");
                        h.assertTrue(!AbilityRuntime.channeling(success.player)&&!SkillChargeSystem.active(success.player)
                                &&success.player.getAttributeValue(Attributes.MOVEMENT_SPEED)>0,"Impact releases the movement lock");
                    }
                    if(elapsed==38){
                        for(int i=1;i<fixtures.size();i++){
                            var fixture=fixtures.get(i);
                            for(var target:fixture.targets)h.assertTrue(target.getHealth()==1000,"Interrupted/cleared charge or downstroke never produces delayed damage");
                            h.assertTrue(!SkillChargeSystem.active(fixture.player),"Interrupt/reset releases stationary lock");
                        }
                        done[0]=true;fixtures.forEach(HeavenSwordChecks::cleanup);
                        System.out.println("[MYRIAD-HEAVEN-SWORD] PASS: real 24+6 ticks, 15x3 body rectangle, locked aim, 620 once, no tower damage, charge/chop interrupt, reset, edge-forward sword, curved painted swipe layers, 3 CC0 recorded sweeps, full/reduced geometry and culling");
                    }
                }catch(Throwable failure){done[0]=true;fixtures.forEach(HeavenSwordChecks::cleanup);throw failure;}
            });
        }
    }
    private static Fixture fixture(GameTestHelper h,int index){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        p.connection.markClientLoaded();
        Vec3 origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7,20+index*10,7)));
        p.setPos(origin);p.setYRot(0);p.setXRot(0);p.setNoGravity(true);
        var data=PlayerDataProvider.get(p);data.setCharacterId("lingyuan");
        p.getAttribute(ModAttributes.SPELL_POWER).removeModifiers();p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(100);
        p.getAttribute(ModAttributes.CRIT_CHANCE).removeModifiers();p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        var targets=new ArrayList<LivingEntity>();
        for(Vec3 offset:List.of(new Vec3(0,0,7),new Vec3(0,0,14.95),new Vec3(1.85,0,8),
                new Vec3(2.2,0,8),new Vec3(0,0,-1.2),new Vec3(0,0,16.2))){
            var pig=h.spawn(EntityTypes.PIG,new BlockPos(2,20,2));
            pig.setPos(origin.add(offset));pig.setNoAi(true);pig.setNoGravity(true);
            pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);targets.add(pig);
        }
        var tower=h.spawn(ModEntities.DEFENSE_TOWER.get(),new BlockPos(2,20,2));tower.setPos(origin.add(0,0,10));tower.setHealth(1000);
        return new Fixture(p,origin,targets,tower);
    }
    private static void cleanup(Fixture f){AbilityRuntime.clear(f.player);f.targets.forEach(Entity::discard);f.tower.discard();f.player.discard();}
    private static void geometry(GameTestHelper h){
        sweepAudio(h);
        edgeOrientation(h);
        for(Vec3 direction:List.of(new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(-1,0,-1).normalize(),new Vec3(.3,0,.7).normalize())){
            Vec3 s=new Vec3(-direction.z,0,direction.x);
            h.assertTrue(HeavenSwordSkill.intersects(Vec3.ZERO,direction,body(direction.scale(7))),"Centered body inside rotated rectangle");
            h.assertTrue(HeavenSwordSkill.intersects(Vec3.ZERO,direction,body(direction.scale(7).add(s.scale(1.7)))),"Body overlap counts even if center is outside");
            h.assertTrue(!HeavenSwordSkill.intersects(Vec3.ZERO,direction,body(direction.scale(7).add(s.scale(2.3)))),"Outside rotated side is not a capsule hit");
            h.assertTrue(!HeavenSwordSkill.intersects(Vec3.ZERO,direction,body(direction.scale(16))),"No rounded range cap");
            h.assertTrue(!HeavenSwordSkill.intersects(Vec3.ZERO,direction,body(direction.scale(-1))),"No rear damage");
        }
        for(int kind:new int[]{CHARGE,ARC,BEAM})for(boolean low:new boolean[]{false,true}){
            int life=kind==CHARGE?4:kind==ARC?14:12;
            for(float age:new float[]{0,.5f,2.5f,Math.min(5.5f,life-.25f),life-.1f}){
                var packet=new AbilityVfxPayload(1,-1,5,7,kind,Vec3.ZERO,new Vec3(0,0,15),1.5f,kind==CHARGE?300:110,life,100,false);
                var mesh=AbilityVfxGeometry.build(packet,age,low);
                h.assertTrue(!mesh.isEmpty()&&mesh.size()<=4096,"Complete giant sword fits the existing render budget");
                for(var q:mesh)for(var vertex:List.of(q.a(),q.b(),q.c(),q.d()))
                    h.assertTrue(AbilityTargeting.finite(vertex)&&HeavenSwordVfx.bounds(packet).contains(vertex),"Culling bounds contain the entire giant sword, not just ground endpoints: kind="+kind+", age="+age+", vertex="+vertex);
            }
        }
        var charge=new AbilityVfxPayload(1,-1,5,7,CHARGE,Vec3.ZERO,new Vec3(0,0,15),1.5f,360,4,100,false);
        var chop=new AbilityVfxPayload(1,-1,5,7,ARC,Vec3.ZERO,new Vec3(0,0,15),1.5f,110,14,100,false);
        var up=AbilityVfxGeometry.build(charge,0,false);var down=AbilityVfxGeometry.build(chop,6,false);
        double highest=up.stream().filter(q->q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL)
                .flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).mapToDouble(v->v.y).max().orElseThrow();
        double furthest=down.stream().filter(q->q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL)
                .flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).mapToDouble(v->v.z).max().orElseThrow();
        h.assertTrue(highest>15&&furthest>14.8&&furthest<15.2,"17m silhouette rises behind caster then physically swings to the 15m impact endpoint");
        h.assertTrue(AbilityFormulaText.evaluate(FirstGroupSkills.byId("g2_lingyuan_ub").description(),3,100).contains("620魔法伤害"),"Current tooltip calculates the actual 620 damage");
        exportPreview();
    }
    private static void edgeOrientation(GameTestHelper h){
        for(Vec3 forward:List.of(new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(-1,0,-1).normalize())){
            for(double degrees:new double[]{-34,-18,-1,0,20,60,90,94}){
                double angle=Math.toRadians(degrees);Vec3 up=new Vec3(0,1,0);
                Vec3 blade=up.scale(Math.cos(angle)).add(forward.scale(Math.sin(angle)));
                Vec3 edge=HeavenSwordVfx.cuttingEdge(blade,forward);
                Vec3 tangent=forward.scale(Math.cos(angle)).subtract(up.scale(Math.sin(angle)));
                h.assertTrue(edge.dot(tangent)>1-1E-8&&Math.abs(edge.dot(blade))<1E-8,"The sword's actual local X cutting edge leads the overhead cut, not its broad flat");
                if(degrees<60)h.assertTrue(edge.dot(forward)>.49,"Raised sword blade edge faces forward");
            }
        }
        var packet=new AbilityVfxPayload(1,-1,5,7,ARC,Vec3.ZERO,new Vec3(0,0,15),1.5f,110,14,100,false);
        var start=AbilityVfxGeometry.build(packet,0,false);var active=AbilityVfxGeometry.build(packet,3,false);
        long startPaint=start.stream().filter(q->q.material()>=44).count(),activePaint=active.stream().filter(q->q.material()>=44).count();
        h.assertTrue(activePaint>startPaint+70,"Downstroke adds real curved multi-material painted ribbons, not only another sword model");
        var raised=new AbilityVfxPayload(1,-1,5,7,CHARGE,Vec3.ZERO,new Vec3(0,0,15),1.5f,360,4,100,false);
        Vec3 forward=new Vec3(0,0,1),up=new Vec3(0,1,0),blade=up.scale(Math.cos(Math.toRadians(-18))).add(forward.scale(Math.sin(Math.toRadians(-18))));
        Vec3 edge=HeavenSwordVfx.cuttingEdge(blade,forward),normal=edge.cross(blade).normalize(),pivot=new Vec3(0,1.2,-.6);
        var vertices=AbilityVfxGeometry.build(raised,0,false).stream().filter(q->q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL)
                .flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).map(v->v.subtract(pivot))
                .filter(v->v.dot(blade)>8&&v.dot(blade)<12).toList();
        double edgeWidth=vertices.stream().mapToDouble(v->v.dot(edge)).max().orElseThrow()-vertices.stream().mapToDouble(v->v.dot(edge)).min().orElseThrow();
        double thickness=vertices.stream().mapToDouble(v->v.dot(normal)).max().orElseThrow()-vertices.stream().mapToDouble(v->v.dot(normal)).min().orElseThrow();
        // The approved middle blade is exactly 8 source units wide / 4 deep (including raised jade).
        h.assertTrue(Math.abs(edgeWidth/thickness-2)<.05,"Actual packaged sword vertices place the 8:4 width axis in the swing plane, not its broad flat; ratio="+edgeWidth/thickness);
    }
    private static void sweepAudio(GameTestHelper h){
        try(var in=HeavenSwordChecks.class.getResourceAsStream("/assets/myriad/sounds.json")){
            var json=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var sounds=json.getAsJsonObject("lingyuan_heaven_sweep").getAsJsonArray("sounds");
            h.assertTrue(sounds.size()==3&&ModSounds.LINGYUAN_HEAVEN_SWEEP.get()!=null,"Dedicated giant-sword whoosh event has three recordings");
            var hashes=new HashSet<Integer>();
            for(var sound:sounds){
                var name=sound.getAsJsonObject().get("name").getAsString().split(":",2)[1];
                try(var ogg=HeavenSwordChecks.class.getResourceAsStream("/assets/myriad/sounds/"+name+".ogg")){
                    h.assertTrue(ogg!=null,"New recorded sword sound is in the actual game resources");
                    var bytes=ogg.readAllBytes();hashes.add(Arrays.hashCode(bytes));
                    int ident=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("\u0001vorbis");
                    h.assertTrue(ident>=0&&bytes[ident+11]==1&&java.nio.ByteBuffer.wrap(bytes,ident+12,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()==48000,
                            "Each recorded heavy sweep is mono 48kHz Vorbis");
                }
            }
            h.assertTrue(hashes.size()==3,"Sword sweep variants are three distinct recordings");
            try(var license=HeavenSwordChecks.class.getResourceAsStream("/META-INF/licenses/lingyuan-heaven-sweep.txt")){
                var notice=new String(license.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                h.assertTrue(notice.contains("artisticdude")&&notice.contains("CC0")&&notice.contains("swishes-sound-pack"),"Source author, URL and license ship with the mod");
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static void exportPreview(){
        String file=System.getProperty("myriad.heavenSwordPreview","");if(file.isBlank())return;
        var shots=new ArrayList<Map<String,Object>>();
        int[] kinds={CHARGE,CHARGE,CHARGE,ARC,ARC,ARC,BEAM,BEAM};
        float[] ages={0,0,0,0,4.5f,6,1,7},stages={0,180,360,110,110,110,0,0};
        String[] labels={"Summon 0% / behind caster","Summon 50% / behind caster","Summon 100% / 17m giant sword",
                "Downstroke start","Downstroke middle / accelerating","Contact / 15x3 footprint","Impact / layered painted sprites","Afterglow / single damage only"};
        for(int i=0;i<kinds.length;i++){
            var packet=new AbilityVfxPayload(1,-1,5,7,kinds[i],Vec3.ZERO,new Vec3(0,0,15),1.5f,stages[i],kinds[i]==CHARGE?4:kinds[i]==ARC?14:12,100,false);
            shots.add(Map.of("kind",kinds[i],"variant",i,"label",labels[i],"mesh",AbilityVfxGeometry.build(packet,ages[i],false)));
        }
        try{var path=java.nio.file.Path.of(file);java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path,new com.google.gson.Gson().toJson(shots));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static AABB body(Vec3 at){return new AABB(at.x-.3,at.y,at.z-.3,at.x+.3,at.y+1.8,at.z+.3);}
}
