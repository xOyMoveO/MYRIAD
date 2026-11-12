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
   float expected=shield.value(150,40,.65f);
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
  String file=System.getProperty("myriad.secondPreview","");if(!file.isBlank())try{
   var path=java.nio.file.Path.of(file);java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,new com.google.gson.Gson().toJson(shots));
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
  LingyuanModelChecks.verify(h);
  System.out.println("[MYRIAD-G2-VFX] PASS: "+cases+" mesh stages, own textures, warning/active separation, reduced detail and vertex bounds");
 }
 private static boolean themeMaterial(int material,int theme){return theme==5&&material==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL;}
}
