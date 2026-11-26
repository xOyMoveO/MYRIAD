package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Common-server geometry QA; deliberately does not assert in-game visual quality or frame rate. */
final class ElementVfxChecks {
    static void verify(GameTestHelper h){
        try(var stream=ElementVfxChecks.class.getResourceAsStream("/assets/myriad/particles/ability_mesh.json")){
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var textures=json.getAsJsonArray("textures");
            h.assertTrue(textures.size()==AbilityVfxMaterials.MAX+1,"SpriteSet count matches the stable material indices");
            for(int i=1;i<textures.size();i++){
                String id=textures.get(i).getAsString().split(":",2)[1];
                try(var png=ElementVfxChecks.class.getResourceAsStream("/assets/myriad/textures/particle/"+id+".png")){
                    h.assertTrue(png!=null,"Every custom sprite is packaged: "+id);
                    if (i == LUOXIA_ARROW_MODEL) {
                        verifyLuoxiaModelAtlas(h, javax.imageio.ImageIO.read(png));
                    } else if (i >= VOLLEY_CHARGE) {
                        var strip = javax.imageio.ImageIO.read(png);
                        boolean narrow = i == VOLLEY_TRAIL || i == LANCE_TRAIL || i == LANCE_WARNING;
                        h.assertTrue(strip.getWidth() == 1024 && strip.getHeight() == (narrow ? 64 : 256)
                                && strip.getColorModel().hasAlpha(), "Luoxia ultimate RGBA strip: " + id);
                    } else if(i>=AbilityVfxMaterials.INK_HD_PAPER){
                        var atlas=javax.imageio.ImageIO.read(png);
                        h.assertTrue(atlas.getWidth()==512&&atlas.getHeight()==128&&atlas.getColorModel().hasAlpha(),
                                "Ink and wind sprites pack four native 128px RGBA frames: "+id);
                        if(i>=INK_HD_GRAIN)verifyLayerAtlas(h,atlas,i,id);
                    }
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        int count=0;
        for(int theme=1;theme<=4;theme++)for(int branch=0;branch<8;branch++)for(int life:new int[]{2,5,8,80}){
            var p=new AbilityVfxPayload(1,-1,theme,branch,FLIGHT,Vec3.ZERO,new Vec3(1.25,.2,0),.45f,0,life,100,false);
            final int firstMaterial=theme==1?AbilityVfxMaterials.THUNDER_PIXEL_SLASH:theme==2?AbilityVfxMaterials.FENJUE_PIXEL_SLASH
                    :theme==3?AbilityVfxMaterials.INK_HD_PAPER:AbilityVfxMaterials.WIND_HD_FEATHER,
                    lastMaterial=theme==1?AbilityVfxMaterials.THUNDER_PIXEL_PILLAR:theme==2?AbilityVfxMaterials.FENJUE_PIXEL_EMBERS
                    :theme==3?AbilityVfxMaterials.INK_HD_HEAD_MODEL:AbilityVfxMaterials.WIND_HD_RING;
            final int solidMaterial=switch(theme){
                case 1->THUNDER_PIXEL_BOLT;
                case 2->FENJUE_PIXEL_STREAM;
                case 3->INK_HD_HEAD_MODEL;
                default->WIND_HD_WING_MODEL;
            };
            for(float age:new float[]{0,.5f,life*.5f,life-.01f}){
                var mesh=AbilityVfxGeometry.build(p,age,false);
                int budget=com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry.usesProjectileModel(p)
                        ?com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry.budget(p,false):192;
                h.assertTrue(!mesh.isEmpty()&&mesh.size()<=budget,"Real-life projectile stages remain finite");
                h.assertTrue(mesh.stream().anyMatch(q->q.material()==solidMaterial
                                &&q.alpha()>.8*Math.min(1,(life-age)/Math.max(1,life*.25))),
                        "Projectile keeps an opaque-core pixel ribbon or solid bevel, not only translucent wisps");
                h.assertTrue(mesh.stream().anyMatch(q->q.material()>=firstMaterial&&q.material()<=lastMaterial),
                        "Every role uses its own sprite family: theme="+theme+" variant="+branch+" age="+age);
                count++;
            }
        }
        for(int theme=1;theme<=4;theme++){
            var p=new AbilityVfxPayload(1,-1,theme,6,SEAL,Vec3.ZERO,new Vec3(1,0,0),5,359,5,100,false);
            var warning=AbilityVfxGeometry.build(p,1,false);
            double maxY=warning.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).mapToDouble(v->v.y).max().orElse(0);
            h.assertTrue(maxY<.2,"Ground warning does not pretend that an active pillar/flame field already exists");
        }
        verifyInkAndWind(h);
        exportPreview();
        System.out.println("[MYRIAD-ELEMENT-VFX] PASS: "+count+" solid projectile stages, own material families, warning/active separation, atlas resources");
    }

    private static void verifyLuoxiaModelAtlas(GameTestHelper h, java.awt.image.BufferedImage atlas) {
        h.assertTrue(atlas.getWidth() == 128 && atlas.getHeight() == 64 && !atlas.getColorModel().hasAlpha()
                && atlas.getColorModel().getNumColorComponents() == 3, "Luoxia arrows share a 128×64 opaque RGB model atlas");
        // Each of the eight 32px material swatches has a 1px border around its face UVs.
        var swatches = new HashSet<List<Float>>();
        for (int y = 0; y < 64; y += 32) {
            for (int x = 0; x < 128; x += 32)
                swatches.add(List.of((x + 1) / 128f, (y + 1) / 64f, (x + 31) / 128f, (y + 31) / 64f));
        }
        var used = new HashSet<List<Float>>();
        for (int shape = 0; shape < 8; shape++) {
            var flight = new AbilityVfxPayload(1, -1, 14, 2, FLIGHT, Vec3.ZERO, new Vec3(0,0,1), .2f, shape, 3, 0, false, 1);
            var faces = AbilityVfxGeometry.build(flight, .5f, false);
            h.assertTrue(!faces.isEmpty(), "Every arrow shape has textured faces: " + shape);
            for (var face : faces) {
                var uv = List.of(face.startU(), face.startV(), face.endU(), face.endV());
                h.assertTrue(face.material() == LUOXIA_ARROW_MODEL && swatches.contains(uv),
                        "Arrow faces sample a single material inside its border: " + shape);
                used.add(uv);
            }
        }
        h.assertTrue(used.equals(swatches), "The arrow models use all eight atlas materials");
    }

    private static void verifyLayerAtlas(GameTestHelper h,java.awt.image.BufferedImage atlas,int id,String name){
        var variants=new HashSet<Integer>();
        for(int i=0;i<4;i++){
            var samples=atlas.getRGB(i*128,0,128,128,null,0,128);
            variants.add(Arrays.hashCode(samples));
            long clear=Arrays.stream(samples).filter(pixel->(pixel>>>24)==0).count();
            if(id==INK_HD_HEAD_MODEL||id==INK_BANNER_MODEL||id==WIND_HD_WING_MODEL||id==FROST_HD_MODEL
                    ||id==FROST_PROJECTILE_MODEL||id==HERB_HD_SEED_MODEL
                    ||com.myriad.character.ability.WarSpiritModels.material(id))
                h.assertTrue(Arrays.stream(samples).allMatch(pixel->(pixel>>>24)==255),"Opaque solid facet: "+name);
            else
                h.assertTrue(clear>samples.length*.50&&clear<samples.length,"Layer has readable open space: "+name);
        }
        h.assertTrue(variants.size()==4,"All four cells have independent artwork: "+name);
    }

    private static void verifyInkProjectilePath(GameTestHelper h){
        for(var heading:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(.6,-.8,0),new Vec3(0,0,-1))){
            var flight=new AbilityVfxPayload(1,-1,3,2,FLIGHT,Vec3.ZERO,heading,.22f,0,3,100,false,10);
            var body=AbilityVfxGeometry.build(flight,.5f,false).stream().filter(q->q.material()==INK_HD_HEAD_MODEL).toList();
            double tip=body.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
                    .mapToDouble(v->v.dot(heading)).max().orElseThrow();
            h.assertTrue(body.size()==60&&Math.abs(tip-.5)<1e-6,"Positive local Z tip leads real XYZ travel");
            var wake=com.myriad.character.skills.xuanchen.rework.InkVfxGeometry.wakeForFlight(flight);
            var end=new AbilityVfxPayload(1,-1,3,2,CANCEL,Vec3.ZERO,Vec3.ZERO,0,2,1,100,false,10);
            var lifetime=new AbilityVisualLifetime();lifetime.end(end,100);
            h.assertTrue(lifetime.finished(flight,100)&&!lifetime.finished(wake,100)&&wake.life()==4
                            &&!materials(AbilityVfxGeometry.build(wake,1,false)).contains(INK_HD_HEAD_MODEL),
                    "Cancellation removes the body immediately and leaves a four-tick planar wake only");
        }
    }

    private static void verifyWindBladeModel(GameTestHelper h){
        final float radius=.45f, age=.5f;
        for(var heading:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(.6,-.8,0),new Vec3(0,0,-1))){
            for(int direction:new int[]{0,180}){
                var p=new AbilityVfxPayload(1,-1,4,5,FLIGHT,Vec3.ZERO,heading,radius,direction,3,100,false,19);
                var body=AbilityVfxGeometry.build(p,age,true).stream().filter(q->q.material()==WIND_HD_WING_MODEL).toList();
                double tip=body.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
                        .mapToDouble(v->v.dot(heading)).max().orElseThrow();
                h.assertTrue(body.size()==156&&Math.abs(tip-(.5+6*radius*.10))<1E-6,
                        "All 26 cuboids render in either direction, with the +Z ceramic tip leading XYZ travel");
                var trail=com.myriad.character.skills.sulan.rework.WindVfxGeometry.wakeForFlight(p);
                var end=new AbilityVfxPayload(1,-1,4,5,CANCEL,Vec3.ZERO,Vec3.ZERO,0,2,1,100,false,19);
                var lifetime=new AbilityVisualLifetime();lifetime.end(end,100);
                h.assertTrue(trail.life()==4&&(direction==0||trail.radius()<.18)
                                &&lifetime.finished(p,100)&&!lifetime.finished(trail,100)
                                &&!materials(AbilityVfxGeometry.build(trail,1,false)).contains(WIND_HD_WING_MODEL),
                        "Returning wake is thinner and the four-tick wake can never render another blade");
            }
        }
    }

    private static void verifyInkBanners(GameTestHelper h){
        double[] tops=new double[2];
        for(int variant=5;variant<=6;variant++){
            float radius=variant==5?4:6;
            var field=new AbilityVfxPayload(1,-1,3,variant,SEAL,Vec3.ZERO,new Vec3(1,0,0),
                    radius,360,80,100,false);
            var warning=new AbilityVfxPayload(1,-1,3,variant,SEAL,field.from(),field.to(),radius,359,12,100,false);
            var personal=new AbilityVfxPayload(1,-1,3,variant,SEAL,field.from(),field.to(),radius,360,2,100,true);
            h.assertTrue(!materials(AbilityVfxGeometry.build(warning,8,false)).contains(INK_BANNER_MODEL)
                            &&!materials(AbilityVfxGeometry.build(personal,1,false)).contains(INK_BANNER_MODEL)
                            &&AbilityVfxGeometry.build(field,80,false).isEmpty(),
                    "Arena banners never show during warning/targeting and leave no geometry at expiry");
            var rise=AbilityVfxGeometry.build(field,.7f,false);
            h.assertTrue(rise.stream().filter(q->q.material()==INK_BANNER_MODEL).count()==156,
                    "The first banner rises before the other three");
            for(boolean reduced:new boolean[]{true,false}){
                var mesh=AbilityVfxGeometry.build(field,20,reduced);
                var flags=mesh.stream().filter(q->q.material()==INK_BANNER_MODEL).toList();
                h.assertTrue(flags.size()==624&&mesh.size()<=AbilityVfxGeometry.maxQuads(field),
                        "All four 26-part models fit the special field budget even at full detail");
                h.assertTrue(!materials(mesh).contains(INK_HD_PAPER)&&flags.stream().allMatch(q->q.alpha()==1f),
                        "Closed opaque flags replace the old paper planes");
                var imprints=flags.stream().filter(q->q.startU()==280f/512&&q.endU()==378f/512).toList();
                h.assertTrue(imprints.size()==(variant==5?16:8)&&imprints.stream().allMatch(q->q.reverseV()
                                &&q.a().y>q.d().y&&Math.abs(q.a().y-q.b().y)<1E-8),
                        "Only the broad seal faces sample the upright emblem; thin faces stay clean");
                tops[variant-5]=height(flags);
            }
        }
        h.assertTrue(tops[0]<1.2&&tops[1]>2.0,"Protection flags retain a distinctly lower silhouette");
    }

    private static void verifyInkAndWind(GameTestHelper h){
        verifyInkBanners(h);
        int stamp=AbilityVfxMaterials.INK_HD_STAMP;
        var first=mesh(3,1,SEAL,2,.43f,0,1);
        var second=mesh(3,1,BURST,-1,.75f,360,1);
        var broken=mesh(3,1,BURST,-1,.75f,360,5);
        h.assertTrue(frame(first,stamp)==0&&frame(second,stamp)==1&&frame(broken,stamp)==2,
                "Ink resonance uses separate half, completed and fractured stamp frames");
        for(int theme:new int[]{3,4}){
            var flight=mesh(theme,theme==3?2:5,FLIGHT,-1,.45f,0,.5f);
            int solid=theme==3?INK_HD_HEAD_MODEL:WIND_HD_WING_MODEL;
            var body=flight.stream().filter(q->q.material()==solid&&q.alpha()>.95).toList();
            double minY=body.stream().mapToDouble(q->Math.min(Math.min(q.a().y,q.b().y),q.c().y)).min().orElse(0);
            h.assertTrue(body.size()>=16&&height(body)-minY>.08&&height(body)>.05
                            &&flight.stream().anyMatch(q->q.material()==(theme==3?AbilityVfxMaterials.INK_HD_CHAIN:AbilityVfxMaterials.WIND_HD_FLOW)),
                    "Flight has a textured solid body and its own planar wake");
            h.assertTrue(body.stream().mapToInt(q->(int)(q.startU()*4)).distinct().count()==4,
                    "All four surface textures are actually sampled by the body");
        }
        var inkFlight=mesh(3,2,FLIGHT,-1,.22f,0,.5f);
        var windFlight=mesh(4,5,FLIGHT,-1,.45f,0,.5f);
        h.assertTrue(materials(inkFlight).containsAll(Set.of(INK_HD_CHAIN,INK_HD_GRAIN,INK_HD_GILT,INK_HD_FIBERS))
                        &&materials(windFlight).containsAll(Set.of(WIND_HD_FLOW,WIND_HD_FILAMENTS,WIND_HD_VAPOR,WIND_HD_SHARDS)),
                "Both models have independent wake, fine accents and fragment layers");
        for(int variant:new int[]{0,3}){
            var delivery=mesh(3,variant,BEAM,-1,.6f,0,4);
            h.assertTrue(delivery.stream().filter(q->q.material()==INK_HD_HEAD_MODEL).count()==42
                            &&materials(delivery).containsAll(Set.of(INK_HD_GRAIN,INK_HD_FIBERS)),
                    "A support talisman flies as folded geometry with separate powder and paper trails");
        }
        var entry=mesh(3,4,BURST,-1,.9f,90,3);
        var exit=mesh(3,4,BURST,-1,.9f,180,3);
        h.assertTrue(!entry.equals(exit)&&height(entry)>1&&height(exit)>1&&height(mesh(3,4,SEAL,-1,.9f,0,3))>1.5,
                "Portal traversal closes entry leaves and opens exit leaves within the paired frames");
        var gate=mesh(3,4,SEAL,-1,.9f,0,3);
        h.assertTrue(gate.stream().filter(q->q.material()==INK_HD_HEAD_MODEL).count()==162
                        &&height(gate)>2.4&&materials(gate).contains(INK_GATE_VEIL),
                "Portal recipes load the complete 27-cuboid frame plus a separate transparent veil");
        h.assertTrue(gate.stream().filter(q->q.material()==INK_HD_HEAD_MODEL).noneMatch(q->{
            var center=q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25);
            return Math.abs(center.x)<.60&&center.y>.25&&center.y<1.9;
        }),"Walk-through portal keeps the central sightline open");
        verifyInkProjectilePath(h);
        verifyWindBladeModel(h);
        h.assertTrue(height(mesh(3,5,SEAL,-1,4,359,2))<.1&&height(mesh(3,5,SEAL,-1,4,360,8))>.75,
                "Ink Protection Array stands its papers up only at activation");
        h.assertTrue(height(mesh(3,7,SEAL,-1,4,360,4))<.2&&height(mesh(3,7,PILLAR,2,.64f,0,4))>.3,
                "Boundary leaves low slow marks and attaches separate binding paper to the victim");
        h.assertTrue(materials(mesh(3,6,BURST,-1,6,0,4)).contains(INK_ARRAY_WAVE)
                        &&height(mesh(3,6,SEAL,-1,6,360,8))>1.6
                        &&materials(mesh(3,7,BURST,-1,4,0,4)).contains(INK_BOUNDARY_WEAVE),
                "Heaven Cover has raised banners and pulse artwork; Boundary closes with its own woven locks");
        h.assertTrue(mesh(3,0,SHIELD,2,.65f,0,3).size()<mesh(3,3,SHIELD,2,.65f,0,3).size(),
                "Passive guardian uses one slip and the active shield uses two barriers");
        var single=mesh(4,0,ARC,-1,.85f,145,3);
        var doubleCut=mesh(4,1,ARC,-1,.8f,120,3);
        h.assertTrue(doubleCut.size()>single.size(),"Wind Combo has a distinct delayed second sweep");
        h.assertTrue(materials(single).containsAll(Set.of(WIND_HD_CUT,WIND_HD_SWEEP_EDGE,WIND_HD_VAPOR,WIND_HD_SHARDS))
                        &&materials(mesh(3,6,BURST,-1,6,0,4)).containsAll(Set.of(INK_HD_BRUSH,INK_HD_GRAIN,INK_HD_FIBERS,INK_HD_GILT)),
                "A wind cut and an ink sweep retain distinct full compositions");
        h.assertTrue(height(mesh(4,3,SEAL,2,.54f,0,3))>.9&&
                !mesh(4,3,SEAL,2,.54f,0,3).equals(mesh(4,3,BURST,2,.65f,0,3)),
                "Wind mark attaches at the shoulder and closes on consumption");
        h.assertTrue(!mesh(4,5,FLIGHT,-1,.45f,0,.5f).equals(mesh(4,5,FLIGHT,-1,.45f,180,.5f))
                        &&!mesh(4,5,BURST,-1,.84f,180,3).isEmpty(),
                "Returning wind blade has opposing travel poses and a distinct foldback");
        h.assertTrue(height(mesh(4,4,WALL,-1,3,0,3))<=4.0&&height(mesh(4,4,WALL,-1,3,0,3))>3.5
                        &&height(mesh(4,4,BURST,-1,.55f,270,3))<1,
                "Wind Wall spans its real height while interception stays local");
        var following=mesh(4,7,TRAIL,2,.55f,90,3);
        var contrary=mesh(4,7,TRAIL,2,.55f,180,3);
        h.assertTrue(!following.equals(contrary)&&height(following)<.6&&height(contrary)<.6,
                "Wind Domain places contrasting buffs and slows at the actor's feet");
    }

    private static Set<Integer> materials(List<AbilityVfxGeometry.Quad> mesh){
        var result=new HashSet<Integer>();
        for(var q:mesh)result.add(q.material());
        return result;
    }

    private static int frame(List<AbilityVfxGeometry.Quad> mesh,int material){
        return mesh.stream().filter(q->q.material()==material)
                .mapToInt(q->(int)(q.startU()*4)).findFirst().orElse(-1);
    }

    private static List<AbilityVfxGeometry.Quad> mesh(int theme,int variant,int kind,int anchor,float radius,float angle,float age){
        return AbilityVfxGeometry.build(new AbilityVfxPayload(1,anchor,theme,variant,kind,Vec3.ZERO,
                new Vec3(0,0,1.5),radius,angle,12,100,false),age,false);
    }

    private static double height(List<AbilityVfxGeometry.Quad> mesh){
        return mesh.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
                .mapToDouble(v->v.y).max().orElseThrow();
    }
    private static void exportPreview(){
        String file=System.getProperty("myriad.elementPreview","");if(file.isBlank())return;
        var shots=new ArrayList<Map<String,Object>>();
        String[] roles={"Thunder","Flame","Ink","Wind"};
        for(int theme=1;theme<=4;theme++){
            int[] kinds={FLIGHT,ARC,BURST,SEAL,SHIELD,CHARGE,TRAIL,theme==4?WALL:PILLAR};
            int[] variants={theme==4?5:theme==3?2:3,2,6,theme==3?6:theme==4?7:6,theme==1?4:theme==2?7:3,theme==1?7:6,theme==2?3:2,theme==1?6:4};
            for(int index=0;index<kinds.length;index++){
                if(theme==3)continue; // Dedicated final art compositions below.
                Vec3 from=Vec3.ZERO,to=kinds[index]==PILLAR?new Vec3(0,6,0)
                        :kinds[index]==TRAIL?new Vec3(5,0,0):kinds[index]==CHARGE?new Vec3(8,0,0):new Vec3(1.5,0,0);
                double radius=switch(kinds[index]){
                    case SEAL->theme==3?6:5;
                    case WALL->3;
                    case BURST->theme==2?5:theme==3?.75:2.5;
                    case ARC->theme==2?4:1.5;
                    case TRAIL->.09;
                    case CHARGE->theme==1?1.25:.35;
                    case FLIGHT->theme==4?.45:.22;
                    default->.65;
                };
                int life=kinds[index]==FLIGHT?3:kinds[index]==SEAL?80:12;
                float age=kinds[index]==FLIGHT?.8f:kinds[index]==SEAL&&theme>=3?11:3;
                int anchor=theme==4&&kinds[index]==SEAL?2:-1;
                var p=new AbilityVfxPayload(1,anchor,theme,variants[index],kinds[index],from,to,(float)radius,105,life,100,false);
                shots.add(Map.of("label",roles[theme-1]+" / "+new String[]{"Projectile","Cut","Impact","Active field","Guard","Windup","Trail","Wall / Pillar"}[index],
                        "theme",theme,"kind",kinds[index],"variant",variants[index],"age",age,"mesh",AbilityVfxGeometry.build(p,age,false)));
            }
        }
        previewInk(shots,"Spirit Bind / origami dart",2,FLIGHT,0,.22f,.8f,3);
        previewInk(shots,"Basic attack / folded seal",0,FLIGHT,0,.20f,.65f,3);
        previewInk(shots,"Ink Shield / delivery",3,BEAM,0,.80f,3,7);
        previewInk(shots,"Portal / brass frame and ink veil",4,SEAL,0,.90f,2,6);
        previewInk(shots,"Heaven Cover / four banners",6,SEAL,360,6,20,80);
        previewInk(shots,"Heaven Cover / damage pulse",6,BURST,0,6,5,12);
        previewInk(shots,"Sealed Boundary / warning",7,SEAL,359,4,9,14);
        previewInk(shots,"Sealed Boundary / closing locks",7,BURST,0,4,3,12);
        try{var path=java.nio.file.Path.of(file);java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path,new com.google.gson.Gson().toJson(shots));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static void previewInk(List<Map<String,Object>> shots,String label,int variant,int kind,int angle,float radius,float age,int life) {
        var from=kind==BEAM||kind==FLIGHT?new Vec3(0,.85,0):Vec3.ZERO;
        var to=from.add(kind==BEAM?2.8:1.25,0,kind==BEAM?1.2:0);
        var p=new AbilityVfxPayload(1,-1,3,variant,kind,from,to,radius,angle,life,100,false);
        var mesh=new ArrayList<>(AbilityVfxGeometry.build(p,age,false));
        if(kind==BURST){
            var floor=new AbilityVfxPayload(1,-1,3,variant,SEAL,from,to,radius,360,80,100,false);
            mesh.addAll(AbilityVfxGeometry.build(floor,variant==6?28:age,false));
        }
        shots.add(Map.of("label",label,"theme",3,"kind",kind,"variant",variant,"age",age,"mesh",mesh));
    }
}
