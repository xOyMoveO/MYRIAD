package com.myriad.game;

import com.myriad.character.*;
import com.myriad.character.ability.*;
import com.myriad.character.rework.*;
import com.myriad.player.*;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Actual second-batch server state, formulas, migration and all mesh stages; not visual acceptance. */
final class SecondGroupChecks {
 static void verify(GameTestHelper h,ServerPlayer p){
  var data=PlayerDataProvider.get(p);var saved=new PlayerData();saved.copyStateFrom(data);
  try{
   for(String role:FirstGroupSkills.SECOND_GROUP){
    AbilityRuntime.clear(p);data.setCharacterId(role);data.setLevel(20);data.refundAllSkillPointsForTraining();data.setGold(1234);
    data.setFirstGroupAttribute("strength",2);data.setFirstGroupAttribute("wisdom",3);
    String old=CharacterType.byId(role).skill(0).getId();data.setSkillLevel(old,4);data.setBuff(old,40);
    data.setMana(123);FirstGroupGrowth.initialize(p,data);
    h.assertTrue(data.skillLevel(old)==0&&data.getSkillPoints()==20,"Old skills refund exactly the current level budget");
    h.assertTrue(data.getGold()==1234&&data.firstGroupAttribute("strength")==2&&data.firstGroupAttribute("wisdom")==3,"Migration preserves gold and paid attributes");
    var candidate=FirstGroupSkills.node(role,5);data.setSkillLevel(candidate.getId(),2);data.setSkillPoints(18);
    FirstGroupGrowth.initialize(p,data);FirstGroupGrowth.initialize(p,data);
    h.assertTrue(data.skillLevel(candidate.getId())==2&&data.getSkillPoints()==18&&data.getMana()==123,"g2 ranks survive reconnect/recalculation without endless refunds");
    h.assertTrue(FirstGroupSkills.resolve(CharacterType.byId(role),2,data.getSkillLevels())==candidate,"HUD and cast use the selected major branch");
    var copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(data),PlayerData.class);
    h.assertTrue(copy.skillLevel(candidate.getId())==2,"Second-batch branch persists");
    data.refundAllSkillPointsForTraining();
    var point=FirstGroupSkills.candidates(role).stream().filter(AbilitySkill::needsPoint).findFirst().orElse(null);
    if(point!=null){data.setMana(500);AbilityRuntime.request(p,data,point);AbilityRuntime.cancel(p);
      h.assertTrue(data.getMana()==500&&data.getCooldown(point.getId())==0&&data.getPlacementType()==0,"Second-batch preview cancellation is free");}
   }
   AbilityRuntime.clear(p);data.setCharacterId("hanze");data.refundAllSkillPointsForTraining();
   CharacterType.applyInitialAttributes(p,CharacterType.HANZE);FirstGroupGrowth.initialize(p,data);
   data.setSkillLevel("g2_hanze_n2",5);var shield=AbilityRuntime.context(p,FirstGroupSkills.byId("g2_hanze_n2"));
   ModShieldSystem.clearFor(p);shield.skill.release(shield);
   float expected=shield.value(150,50,.65f);
   h.assertTrue(Math.abs(ModShieldSystem.value(p)-expected)<.01,"Frost shield is its exact formula, not four-times or vanilla absorption");
   shield.skill.release(shield);h.assertTrue(Math.abs(ModShieldSystem.value(p)-expected)<.01,"Repeated same-source frost shield does not stack");
   ModShieldSystem.clearFor(p);
   AbilityRuntime.clear(p);data.setCharacterId("chixiao");data.refundAllSkillPointsForTraining();data.setSkillLevel("g2_chixiao_ma",1);
   var parry=AbilityRuntime.context(p,FirstGroupSkills.byId("g2_chixiao_ma"));parry.skill.release(parry);
   h.assertTrue(Math.abs(AbilityRuntime.incoming(p,Kind.PHYSICAL)-.65)<.0001,"Parry reduces non-true incoming once");
   h.assertTrue(AbilityRuntime.incoming(p,Kind.TRUE)==1,"True damage bypasses parry");
   AbilityRuntime.clear(p);data.setCharacterId("qingluo");data.refundAllSkillPointsForTraining();FirstGroupGrowth.initialize(p,data);p.setHealth(100);
   var heal=AbilityRuntime.context(p,FirstGroupSkills.byId("g2_qingluo_n1"));float hp=p.getHealth();heal.heal(p,75);
   h.assertTrue(Math.abs(p.getHealth()-hp-75)<.01,"Active healing reaches actual mod health");
   heal.heal(p,100000);h.assertTrue(p.getHealth()<=p.getMaxHealth(),"Healing cannot exceed max health");
   AbilityRuntime.clear(p);data.setCharacterId("lingyuan");data.refundAllSkillPointsForTraining();data.setSkillLevel("g2_lingyuan_pa",1);
   var slain=h.spawn(net.minecraft.world.entity.EntityTypes.PIG,new net.minecraft.core.BlockPos(3,6,3));slain.setHealth(0);
   AbilityRuntime.hit(p,slain,5,true,"",true,0);
   h.assertTrue(data.getSwordQi()==10,"Lethal last hits still award Sword Qi, despite Post receiving a dead target");
   slain.discard();
   for(String role:FirstGroupSkills.SECOND_GROUP)for(var skill:FirstGroupSkills.candidates(role)){
    String desc=AbilityFormulaText.evaluate(skill.description(),skill.maxRank(),230);
    h.assertTrue(!desc.contains("%S")&&!desc.contains("r－1"),"All second-batch numeric tooltips resolve at the current rank: "+skill.getId());
   }
   mesh(h);
   System.out.println("[MYRIAD-G2] PASS: five roles, legacy migration, g2 persistence, point cancel, shield amount/nonstacking, parry, healing, formula text");
  }finally{AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);data.copyStateFrom(saved);CharacterType.applyInitialAttributes(p,CharacterType.byId(data.getCharacterId()));FirstGroupGrowth.initialize(p,data);}
 }
 static void mesh(GameTestHelper h){
  var shots=new ArrayList<Map<String,Object>>();int cases=0;
  String[] labels={"Sword","Ice","Herb","War","Spirit"};
  for(int theme=5;theme<10;theme++)for(int variant=0;variant<8;variant++)for(int kind=0;kind<12;kind++)for(float age:new float[]{0,.75f,4.5f,7.9f}){
   var packet=new AbilityVfxPayload(1,-1,theme,variant,kind,Vec3.ZERO,new Vec3(1.5,.1,0),1.2f,100,8,100,false);
   h.assertTrue(packet.valid(),"New theme accepted by wire validation");
   var mesh=AbilityVfxGeometry.build(packet,age,false);var low=AbilityVfxGeometry.build(packet,age,true);
   h.assertTrue(!mesh.isEmpty()&&mesh.size()<=AbilityVfxGeometry.maxQuads(packet)&&low.size()<=mesh.size(),"Second-batch finite/reduced mesh budget");
   for(var q:mesh){h.assertTrue(q.material()>=0&&q.material()<=AbilityVfxMaterials.MAX&&q.alpha()>0&&q.alpha()<=1,"Registered material and finite alpha");
    for(var v:new Vec3[]{q.a(),q.b(),q.c(),q.d()})h.assertTrue(AbilityTargeting.finite(v)&&v.length()<24,"Finite bounded vertices");}
   final int firstMaterial=23+(theme-5)*4;
   if(age==.75f&&variant==2&&kind==9)h.assertTrue(mesh.stream().anyMatch(q->q.material()>=firstMaterial&&q.material()<=firstMaterial+3
           ||themeMaterial(q.material(),packet.theme())),"Each role has a dedicated material family");
   if(age==.75f&&(variant==2||variant==4||variant==6||variant==7)&&Set.of(2,3,7,9).contains(kind))shots.add(Map.of("label",labels[theme-5]+" / "+variant+" / "+kind,"theme",theme,"variant",variant,"kind",kind,"age",age,"mesh",mesh));
   cases++;
  }
  for(int theme=5;theme<10;theme++){
   var warning=new AbilityVfxPayload(1,-1,theme,6,2,Vec3.ZERO,new Vec3(1,0,0),4,359,5,100,false);
   h.assertTrue(AbilityVfxGeometry.build(warning,1,false).stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).allMatch(v->v.y<.2),"Warning remains on the ground until real effect activates");
  }
  frostHerbFlight(h);
  frostSpikeModels(h);
  WarSpiritVfxChecks.verify(h);
  frostHerbPreview();
  String file=System.getProperty("myriad.secondPreview","");if(!file.isBlank())try{
   var path=java.nio.file.Path.of(file);java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,new com.google.gson.Gson().toJson(shots));
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
  LingyuanModelChecks.verify(h);
  System.out.println("[MYRIAD-G2-VFX] PASS: "+cases+" mesh stages, own textures, warning/active separation, reduced detail and vertex bounds");
 }
 private static void frostHerbPreview(){
  String destination=System.getProperty("myriad.frostHerbPreview","");
  if(destination.isBlank())return;
  int[][] contexts={
   {6,0,6,1},{6,1,7,1},{6,2,9,1},{6,3,7,1},{6,4,6,2},{6,5,9,1},{6,6,6,4},{6,7,7,1},
   {7,0,3,1},{7,1,10,3},{7,2,5,1},{7,3,9,1},{7,4,2,4},{7,5,5,1},{7,6,2,7},{7,7,7,1}
  };
  String[] labels={
   "Frost stacks","Rime armor","Ice volley","Frost shield","Absolute zero","Glacier lance","Frozen expanse","Winter bastion",
   "Kind herbalist","Stored dew","Verdant dew","Briar seed","Heart pill array","Life thread","Spring grove","Jade canopy"
  };
  var shots=new ArrayList<Map<String,Object>>();
  for(int i=0;i<contexts.length;i++){
   int[] c=contexts[i];
   int kind=c[2], life=kind==9?3:kind==2?100:9;
   double angle=kind==2?360:0;
   var end=kind==5?new Vec3(3.8,.38,.26):new Vec3(1.28,.2,.28);
   if(kind==6)end=new Vec3(.01,c[1]==4?3:2.2,0);
   float radius=kind==5?.12f:c[3];
   float age=kind==9?.77f:kind==2?12:2.6f;
   var p=new AbilityVfxPayload(1,-1,c[0],c[1],kind,Vec3.ZERO,end,radius,(float)angle,life,136,false,kind==9?7:0);
   shots.add(Map.of("label",labels[i],"theme",c[0],"variant",c[1],"kind",kind,"age",age,
       "mesh",AbilityVfxGeometry.build(p,age,false)));
  }
  try{
   var path=java.nio.file.Path.of(destination);
   java.nio.file.Files.writeString(path,new com.google.gson.Gson().toJson(shots));
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 private static void frostSpikeModels(GameTestHelper h){
  int model=AbilityVfxMaterials.FROST_PROJECTILE_MODEL;
  for(int branch:new int[]{4,6}){
   boolean expanse=branch==6;
   for(var heading:List.of(new Vec3(1,0,0),new Vec3(0,0,-1))){
    var p=new AbilityVfxPayload(1,-1,6,branch,AbilityVfxGeometry.PILLAR,Vec3.ZERO,
         heading.add(0,expanse?2.7:3,0),.75f,0,expanse?14:13,100,false);
    var rise=AbilityVfxGeometry.build(p,0,false).stream().filter(q->q.material()==model).toList();
    var active=AbilityVfxGeometry.build(p,4,false);
    var solid=active.stream().filter(q->q.material()==model).toList();
    h.assertTrue(solid.size()==(expanse?76:60)*6&&rise.size()==solid.size()
         &&AbilityVfxGeometry.build(p,4,true).stream().filter(q->q.material()==model).count()==solid.size(),
         "Both ice clusters keep every model face at full and reduced detail");
    var tip=solid.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
         .max(Comparator.comparingDouble(v->v.y)).orElseThrow();
    h.assertTrue(tip.y>2.45&&tip.y<3.3&&rise.stream().allMatch(q->q.a().y<.8)
         &&(!expanse||tip.dot(heading)>1.5),"Ice grows from the floor and expanse fins point outwards");
    h.assertTrue(active.stream().anyMatch(q->q.material()==AbilityVfxMaterials.FROST_HD_MIST)
         &&active.stream().anyMatch(q->q.material()==AbilityVfxMaterials.FROST_HD_FISSURE),
         "Ice models retain layered ground fractures and mist");
    h.assertTrue(AbilityVfxGeometry.build(p,p.life()-.5f,false).stream()
         .filter(q->q.material()==model).allMatch(q->q.alpha()<.3)
         &&AbilityVfxGeometry.build(p,p.life(),false).isEmpty(),"Solid ice fades and expires with its cast");
   }
   var warning=new AbilityVfxPayload(1,-1,6,branch,AbilityVfxGeometry.SEAL,Vec3.ZERO,
        new Vec3(0,0,1),4.5f,359,5,100,false);
   h.assertTrue(AbilityVfxGeometry.build(warning,1,false).stream().noneMatch(q->q.material()==model),
        "Ice clusters appear on activation rather than in the targeting warning");
  }
  System.out.println("[MYRIAD-HANZE-SPIKES] PASS: distinct closed ice models, outward heading, rise, layered ground effects and expiry");
 }
 private static void frostHerbFlight(GameTestHelper h){
  for(int theme:new int[]{6,7})for(int branch:theme==6?new int[]{0,2,5}:new int[]{0,3}){
   int core=theme==6?AbilityVfxMaterials.FROST_PROJECTILE_MODEL:AbilityVfxMaterials.HERB_HD_SEED_MODEL;
   for(var end:List.of(new Vec3(1.45,0,0),new Vec3(0,1.2,0),new Vec3(.8,-.55,-.7))){
    var packet=new AbilityVfxPayload(2,-1,theme,branch,AbilityVfxGeometry.FLIGHT,Vec3.ZERO,end,.22f,0,3,100,false,11);
    var first=AbilityVfxGeometry.build(packet,0,false);
    var later=AbilityVfxGeometry.build(packet,.8f,false);
    var facets=later.stream().filter(q->q.material()==core).toList();
    int boxes=theme==6?(branch==0?51:branch==5?78:66):(branch==0?63:70);
    h.assertTrue(facets.size()==boxes*6&&facets.stream().allMatch(q->q.alpha()>.9)
       &&facets.stream().mapToInt(q->(int)(q.startU()*4)).distinct().count()==4,"All exported skill/basic cuboids render in four distinct materials");
    h.assertTrue(facets.stream().allMatch(q->q.startU()>0&&q.endU()<1&&q.startU()<q.endU()
       &&q.startV()>0&&q.endV()<1&&q.startV()<q.endV()&&(int)(q.startU()*4)==(int)(q.endU()*4))
       &&facets.stream().map(q->q.endU()-q.startU()).distinct().count()>4,
       "Surface and trim UVs vary by facet and stay within their authored material");
    h.assertTrue(AbilityVfxGeometry.build(packet,.8f,true).stream().filter(q->q.material()==core).count()==boxes*6
       &&later.stream().anyMatch(q->q.material()!=core),"The expanded face budget retains the full model and 2D wake");
    var axis=end.normalize();
    var startCenter=first.stream().filter(q->q.material()==core).mapToDouble(q->q.a().dot(axis)).average().orElseThrow();
    var endCenter=facets.stream().mapToDouble(q->q.a().dot(axis)).average().orElseThrow();
    h.assertTrue(Math.abs((endCenter-startCenter)-end.length()*.8)<1e-5,"Projectile model advances along actual horizontal, vertical and sloped steps");
    if(branch!=0){
      double expectedTip=theme==7?.079*6:branch==5?.069*16:.069*8;
      var vertices=facets.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
          .map(v->v.subtract(end.scale(.8))).toList();
      double tip=vertices.stream().mapToDouble(v->v.dot(axis)).max().orElseThrow();
      h.assertTrue(Math.abs(tip-expectedTip)<1E-5,"The authored positive Z tip leads the true flight vector");
      if(theme==6&&branch==5){
       double tail=vertices.stream().mapToDouble(v->v.dot(axis)).min().orElseThrow();
       double radius=vertices.stream().mapToDouble(v->v.subtract(axis.scale(v.dot(axis))).length()).max().orElseThrow();
       h.assertTrue(Math.abs(tip-tail-.069*32.5)<1E-5&&radius<.069*4.5,"Glacier Lance retains the longer, narrower silhouette");
      }
    }
    var wake=FrostHerbLayers.wakeForFlight(packet);
    var wakeMesh=AbilityVfxGeometry.build(wake,1,false);
    h.assertTrue(wake.instance()==0&&wake.life()==5&&wakeMesh.stream().noneMatch(q->q.material()==core)
       &&wakeMesh.stream().map(AbilityVfxGeometry.Quad::material).distinct().count()>=2,"A disappearing projectile leaves only several crisp planar layers");
    var cancel=new AbilityVfxPayload(2,-1,theme,branch,AbilityVfxGeometry.CANCEL,Vec3.ZERO,Vec3.ZERO,0,2,1,101,false,11);
    var lifetime=new AbilityVisualLifetime();lifetime.end(cancel,101);
    h.assertTrue(lifetime.finished(packet,101)&&!lifetime.finished(wake,101)
       &&AbilityVisualLifetime.expired(wake,105),"Only the finite two-dimensional wake survives a projectile end");
   }
  }
  System.out.println("[MYRIAD-FROST-HERB] PASS: five authored skill/basic models along cardinal/sloped/vertical paths, tip direction, separate planar wakes, immediate body cancellation");
 }
 private static boolean themeMaterial(int material,int theme){
  return switch(theme){
   case 5 -> material==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL;
   case 6 -> material==AbilityVfxMaterials.FROST_HD_MODEL||material==AbilityVfxMaterials.FROST_PROJECTILE_MODEL;
   case 7 -> material==AbilityVfxMaterials.HERB_HD_SEED_MODEL;
   case 8 -> material>=AbilityVfxMaterials.WAR_HD_EDGE&&material<=AbilityVfxMaterials.WAR_HD_FLAKES;
   case 9 -> material>=AbilityVfxMaterials.SPIRIT_HD_RIBBON&&material<=AbilityVfxMaterials.SPIRIT_HD_CRANE_MODEL;
   default -> false;
  };
 }
}
