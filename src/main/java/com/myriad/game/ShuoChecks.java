package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.shuo.*;
import com.myriad.client.firstperson.*;
import com.myriad.combat.DamageCalc;
import com.myriad.network.*;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;
import javax.imageio.ImageIO;

/** Production state/geometry and actual public cast/tick tests; not client latency acceptance. */
final class ShuoChecks {
    static ServerPlayer player(GameTestHelper h,Vec3 at,List<ShuoViewPayload> cues){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
            net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket c&&c.payload() instanceof ShuoViewPayload cue)cues.add(cue);}
        };p.connection.markClientLoaded();p.setNoGravity(true);p.setPos(at);p.setYRot(0);p.setXRot(0);
        var d=PlayerDataProvider.get(p);d.setCharacterId("shuo");d.setLevel(20);d.setMana(700);
        for(var s:FirstGroupSkills.candidates("shuo"))d.setSkillLevel(s.getId(),0);
        p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(100);return p;
    }
    static net.minecraft.world.entity.animal.pig.Pig target(GameTestHelper h,Vec3 at){
        var t=h.spawn(EntityTypes.PIG,new BlockPos(35,140,5));t.setPos(at);t.setNoAi(true);t.setNoGravity(true);
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);t.setHealth(1000);return t;
    }
    /** Explicit incoming stage isolates mock-player spawn invulnerability, not an online PVP test. */
    private static void incoming(ServerPlayer p,LivingEntity enemy,DamageCalc.Kind kind){
        DamageCalc.markSkillHit(p,false,kind,4);
        try{PlayerDataProvider.onIncomingDamage(new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p,
            new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().mobAttack(enemy),80)));}
        finally{DamageCalc.clearSkillHit(p);}
    }
    static void verify(GameTestHelper h){
        for(boolean slim:new boolean[]{false,true})for(boolean left:new boolean[]{false,true}){
            var hand=ShuoArmModel.bake(slim,left);int[] cubes={0};
            hand.visit(new com.mojang.blaze3d.vertex.PoseStack(),(pose,path,index,cube)->{
                cubes[0]++;
                for(var face:cube.polygons)for(var v:face.vertices()){
                    h.assertTrue(v.y()>=ShuoArmModel.CUT_Y&&v.y()<=10.3f,"Only rigid fist/short straight forearm; no elbow continuation");
                    h.assertTrue(v.u()>=0&&v.u()<=1&&v.v()>=0&&v.v()<=1,"Original player-skin UV remains bounded");
                }
            });h.assertTrue(cubes[0]==2,"One rigid hand plus sleeve, no segmented elbow mesh");
        }
        h.assertTrue(CharacterType.values()[CharacterType.values().length-1]==CharacterType.SHUO,"New enum appended, existing network/storage ordinals unchanged");
        int poses=0;for(int action=0;action<=ShuoViewPayload.MAX_ACTION;action++)for(int n=0;n<=100;n++)for(boolean drawn:new boolean[]{false,true})for(boolean mirror:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
            var f=ShuoRig.frame(action,n/100.0,drawn);var blade=ShuoRig.blade(f,mirror);var scabbard=ShuoRig.sheath(f,mirror);
            boolean attacking=ShuoViewPayload.basicAction(action)||action==ShuoViewPayload.FLASH||action==ShuoViewPayload.CUT_LEFT||action==ShuoViewPayload.CUT_RIGHT||action==ShuoViewPayload.CUT_DOWN||action==ShuoViewPayload.GUARD||action==ShuoViewPayload.COUNTER;
            h.assertTrue(ShuoRig.showMainHand(f,drawn)==(drawn||attacking),"Drawn blade always has a gripping hand; sheathed idle stays hand-free");
            var oldFrame=ShuoRig.basis(f.palm(),f.axis(),f.edge(),mirror);
            for(var radial:List.of(new Vector3f(1,0,0),new Vector3f(0,0,1))){
                var before=oldFrame.transformDirection(new Vector3f(radial)).normalize();
                var after=blade.transformDirection(new Vector3f(radial)).normalize();
                h.assertTrue(before.dot(after)<-.9999f,"Item radial directions turn exactly 180 degrees without changing shaft/grip");
            }
            h.assertTrue(blade.isFinite()&&scabbard.isFinite()&&blade.determinant()>.99&&scabbard.determinant()>.99,"Proper finite rotations without flipped winding");
            for(boolean off:new boolean[]{false,true}){
                boolean left=off?!mirror:mirror;var hand=ShuoRig.hand(f,off,mirror).mul(NightDaggerRig.arm(slim,left));
                var palm=hand.transformPosition(new Vector3f((slim?.5f:1)/16*(left?1:-1),.5f,0));
                var grip=(off?scabbard:blade).transformPosition(new Vector3f(0,(off?.12f:ShuoRig.HAND_GRIP_RAISE)/ShuoRig.WEAPON_LENGTH,0));
                h.assertTrue(palm.distance(grip)<1E-6,"Skin palm remains exactly on its raised hilt grasp through every pose");
            }
            if(action==ShuoViewPayload.CANCEL&&!drawn){
                var throat=blade.transformPosition(new Vector3f(0,ShuoRig.MODEL_GRIP_TO_MOUTH,0));
                h.assertTrue(throat.distance(scabbard.transformPosition(new Vector3f()))<1E-6,"Closed blade root exactly meets sheath throat");
                h.assertTrue(f.palm().y<-.10&&f.mouth().y<-.45&&f.palm().x<-.8&&!ShuoRig.showMainHand(f,false),"Longer sheathed hilt stays left/below the crosshair, with no hand; throat position unchanged");
            }poses++;
        }
        h.assertTrue(!ShuoRig.SHOW_OFF_HAND,"No scabbard-supporting off hand is rendered");
        var sheathed=ShuoRig.frame(ShuoViewPayload.CANCEL,0,false);var sheathTransform=ShuoRig.sheath(sheathed,false);
        var mouth=sheathTransform.transformPosition(new Vector3f());var tail=sheathTransform.transformPosition(new Vector3f(0,ShuoRig.MODEL_SHEATH_LENGTH,0));
        float slope=(float)Math.tan(Math.toRadians(35));float m=mouth.y-mouth.z*slope,t=tail.y-tail.z*slope;
        float visible=m/(m-t);
        h.assertTrue(sheathed.sheathAxis().y<-.95&&sheathed.sheathAxis().x<-.1&&sheathed.mouth().x<-.9,"Scabbard is near-vertical, gently tilted and low-left");
        h.assertTrue(visible>.27&&visible<.38,"At the default 70-degree view only about one third of the real sheath is visible: "+visible);
        var idle=ShuoRig.frame(ShuoViewPayload.CANCEL,0,true);var held=ShuoRig.blade(idle,false);
        var grip=held.transformPosition(new Vector3f());var tip=held.transformPosition(new Vector3f(0,ShuoRig.MODEL_GRIP_TO_TIP,0));
        h.assertTrue(grip.x>0.7f&&grip.y<-.5f&&tip.y>grip.y+1.7f&&tip.x>grip.x,"Reference stance: low-right hilt, extended upward/right blade");
        h.assertTrue(ShuoRig.MODEL_SHEATH_LENGTH>ShuoRig.MODEL_GRIP_TO_TIP-ShuoRig.MODEL_GRIP_TO_MOUTH&&ShuoRig.EXTRACT>(ShuoRig.MODEL_GRIP_TO_TIP-ShuoRig.MODEL_GRIP_TO_MOUTH)*ShuoRig.WEAPON_LENGTH,"Longer sheath houses the full blade, extraction clears its tip before the cut");
        h.assertTrue(Math.abs(tip.z-grip.z)<.15f,"Drawn blade is not foreshortened by pointing into the screen");
        for(boolean mirror:new boolean[]{false,true}){
            var edge=ShuoRig.blade(idle,mirror).transformDirection(new Vector3f(1,0,0)).normalize();
            var axis=ShuoRig.blade(idle,mirror).transformDirection(new Vector3f(0,1,0)).normalize();
            var before=ShuoRig.basis(idle.palm(),idle.axis(),idle.edge(),mirror);
            h.assertTrue(edge.dot(before.transformDirection(new Vector3f(1,0,0)).normalize())<-.9999f,"Requested 180-degree turn, including mirrored grip");
            h.assertTrue(axis.dot(before.transformDirection(new Vector3f(0,1,0)).normalize())>.9999f,"Turning the edge never tips or shortens the blade shaft");
        }
        var hand=ShuoRig.hand(idle,false,false);
        h.assertTrue(Math.abs(hand.getScale(new Vector3f()).x-.74f)<1E-6&&Math.abs(hand.getScale(new Vector3f()).y-.74f)<1E-6,"Longer weapon never elongates skin hands");
        for(boolean mirror:new boolean[]{false,true}){
            var fingers=ShuoRig.hand(idle,false,mirror).transformDirection(new Vector3f(0,1,0)).normalize();
            h.assertTrue(fingers.z<-.95f&&fingers.y>0&&fingers.y<.3f,"Wrist-to-fingers axis faces forwards/slightly upwards, not vertically along the blade");
            var palmAt=ShuoRig.hand(idle,false,mirror).transformPosition(new Vector3f());
            var hiltAt=ShuoRig.blade(idle,mirror).transformPosition(new Vector3f());
            h.assertTrue(palmAt.y>hiltAt.y+.10f&&palmAt.distance(hiltAt)<.12f,"Grasp is higher, towards the guard, without moving or resizing the weapon");
        }
        var motion=new ShuoMotion();h.assertTrue(motion.accept(new ShuoViewPayload(0,9,0,1),10,0,false),"Confirmed cue accepted");
        h.assertTrue(!motion.accept(new ShuoViewPayload(0,9,0,1),10,0,false),"Duplicate token ignored");
        h.assertTrue(!motion.active(20),"Absolute cosmetic TTL ends");
        h.assertTrue(ShuoRig.showMainHand(motion.frame(20,true),true),"Hand remains on drawn hilt after the attack timeline expires");
        h.assertTrue(!ShuoRig.showMainHand(motion.frame(20,false),false),"Hand disappears once idle state is sheathed");
        h.assertTrue(motion.accept(new ShuoViewPayload(10,0,0,2),20,0,false)&&!motion.active(20),"Cancellation is immediate");
        h.assertTrue(!new ShuoViewPayload(ShuoViewPayload.MAX_ACTION+1,20,0,1).valid()&&!new ShuoViewPayload(0,100,0,1).valid(),"Malformed cue bounds rejected");
        motion.reset();motion.accept(new ShuoViewPayload(ShuoViewPayload.DRAW,9,0,1),0,0,false);
        var previous=motion.frame(6,true);motion.accept(new ShuoViewPayload(ShuoViewPayload.SHEATHE,8,6,2),6,0,true);
        var next=motion.frame(6,true);h.assertTrue(previous.palm().distance(next.palm())<1E-6&&previous.mouth().distance(next.mouth())<1E-6,"Changing from cut to resheath starts at the real existing hand pose, not a snapped idle");
        ShuoSlashChecks.verify(h);
        ShuoWorldVfxChecks.verify(h);
        ShuoBladeFollowChecks.verify(h);
        ShuoBrightnessChecks.verify(h);
        try{
            for(String kind:List.of("sword","scabbard")){
                try(var in=ShuoChecks.class.getResourceAsStream("/assets/myriad/models/item/shuo_"+kind+".json")){
                    h.assertTrue(in!=null,"Packaged adjusted "+kind+" model");
                    var model=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    float tipY=Float.NEGATIVE_INFINITY;
                    for(var element:model.getAsJsonArray("elements")){
                        var e=element.getAsJsonObject();String name=e.get("name").getAsString();
                        var lo=e.getAsJsonArray("from");var hi=e.getAsJsonArray("to");
                        if(name.startsWith("sword_blade_"))tipY=Math.max(tipY,hi.get(1).getAsFloat());
                        if(name.equals("sword_grip_core")){
                            h.assertTrue(Math.abs(hi.get(0).getAsFloat()-lo.get(0).getAsFloat()-1.05f)<1E-5&&Math.abs(hi.get(1).getAsFloat()-lo.get(1).getAsFloat()-4.9f)<1E-5,"Real item hilt is longer and wider, not a scaled player fist");
                        }
                        if(name.equals("sword_blade_face_000")){
                            h.assertTrue(Math.abs(lo.get(1).getAsFloat()/16-.5f-ShuoRig.MODEL_GRIP_TO_MOUTH)<1E-5,"Actual compiled blade root matches closed rig mouth");
                            h.assertTrue(hi.get(2).getAsFloat()-lo.get(2).getAsFloat()>.24f,"Blade itself has additional thickness, not only the hilt");
                        }
                    }
                    if(kind.equals("sword"))h.assertTrue(Math.abs(tipY/16-.5f-ShuoRig.MODEL_GRIP_TO_TIP)<1E-5,"Actual longer item tip matches the rig's extraction length");
                    else{
                        float tailY=Float.NEGATIVE_INFINITY;
                        for(var e:model.getAsJsonArray("elements"))tailY=Math.max(tailY,e.getAsJsonObject().getAsJsonArray("to").get(1).getAsFloat());
                        h.assertTrue(Math.abs(tailY/16-.5f-ShuoRig.MODEL_SHEATH_LENGTH)<1E-5,"Actual longer sheath matches the rig's one-third-visibility calculation");
                    }
                }
            }
            for(String name:List.of("passive","shuo_jianqie","shuo_nadao","shuo_qiaofan","shuo_qiaofan_2","shuo_yishan","shuo_duanliu","shuo_wusheng","shuo_canyue","shuo_canyue_2")){
                try(var in=ShuoChecks.class.getResourceAsStream("/assets/myriad/textures/gui/sprites/skill_icons/generated/shuo/"+name+".png")){
                    h.assertTrue(in!=null,"Packaged icon "+name);var im=ImageIO.read(in);h.assertTrue(im.getWidth()==64&&im.getHeight()==64&&!im.getColorModel().hasAlpha(),"Opaque 64px complete icon");
                }
            }
            try(var in=ShuoChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/shuo_basic_hd.png")){
                var im=ImageIO.read(in);h.assertTrue(im.getWidth()==1536&&im.getHeight()==1536&&im.getColorModel().hasAlpha(),"Native HD master in transparent padded canvas, not an upscaled old atlas");
                int bright=0;for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
                    int rgb=im.getRGB(x,y),a=rgb>>>24;
                    if(x<120||y<120||x>=1416||y>=1416)h.assertTrue(a==0,"Safe transparent border, no clipped/cross-cell artwork");
                    if(a>180&&((rgb>>16)&255)>220&&((rgb>>8)&255)>220&&(rgb&255)>220)bright++;
                }h.assertTrue(bright>5000,"Substantial bright core readable against daylight");
                h.assertTrue(ShuoChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/shuo_slash.png")==null,"Retired atlas not packaged");
            }
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        System.out.println("[MYRIAD-SHUO-RIG] PASS: "+poses+" paired skin grips / closed sheath alignment, proper rotations, cue lifecycle, atlas/skill icons and finite VFX");
        var p=player(h,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(35,140,5))),new ArrayList<>());
        var d=PlayerDataProvider.get(p);var enemy=target(h,p.position().add(0,0,2));
        var basicCues=new ArrayList<ShuoViewPayload>();var basicPlayer=player(h,p.position().add(5,0,0),basicCues);
        try{
            float health=enemy.getHealth();double mana=PlayerDataProvider.get(basicPlayer).getMana();
            for(int expected:new int[]{ShuoViewPayload.DRAW,ShuoViewPayload.BASIC_RISE,ShuoViewPayload.BASIC_SWEEP,ShuoViewPayload.DRAW}){
                ShuoCombat.basic(basicPlayer);
                h.assertTrue(basicCues.getLast().action()==expected&&basicCues.getLast().ticks()==9,"Production server basic path confirms all three variants with unchanged cosmetic duration");
            }
            h.assertTrue(enemy.getHealth()==health&&PlayerDataProvider.get(basicPlayer).getMana()==mana,"Cosmetic combo adds no independent damage or mana payment");
            ShuoCombat.clear(basicPlayer);ShuoCombat.basic(basicPlayer);
            h.assertTrue(basicCues.getLast().action()==ShuoViewPayload.DRAW,"Server lifecycle cleanup resets combo");
        }finally{ShuoCombat.clear(basicPlayer);basicPlayer.discard();}
        boolean nc=TrainingMode.isNoCooldown(),nm=TrainingMode.isNoMana();TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
        try{
            var w=FirstGroupSkills.node("shuo",3);d.setSkillLevel(w.getId(),1);var c=AbilityRuntime.context(p,w);
            SkillSystem.cast(p,1);h.assertTrue(d.getMana()==650&&d.getCooldown(w.getId())==260,"Public W pays once and enters its actual CD");
            var physical=p.damageSources().mobAttack(enemy);
            enemy.setPos(p.position().add(0,0,-2));h.assertTrue(!ShuoCombat.parry(p,physical,DamageCalc.Kind.PHYSICAL),"Rear hit cannot parry");
            enemy.setPos(p.position().add(0,0,2));h.assertTrue(!ShuoCombat.parry(p,physical,DamageCalc.Kind.TRUE),"True damage is not guarded");
            h.assertTrue(!ShuoCombat.parry(p,p.damageSources().fall(),DamageCalc.Kind.PHYSICAL),"Environment not guarded");
            h.assertTrue(ShuoCombat.parry(p,physical,DamageCalc.Kind.PHYSICAL)&&!ShuoCombat.parry(p,physical,DamageCalc.Kind.PHYSICAL),"One frontal nontrue hit only");
            double mana=d.getMana();SkillSystem.cast(p,1);
            h.assertTrue(d.getMana()==mana&&!w.recastAvailable(c)&&Math.abs(enemy.getHealth()-910)<.05,"Manual counter gives exactly 90 damage, no repeated payment; actual HP="+enemy.getHealth());
            h.assertTrue(Math.abs(ModShieldSystem.value(p)-75)<.05,"Landed counter grants its separately sourced two-second shield");
            ModShieldSystem.clearFor(p); // Isolate the next incoming-damage fixture from the new counter shield.
            AbilityRuntime.clear(p);d.setBuff("g1_stun",0);ShuoCombat.guard(AbilityRuntime.context(p,w));
            ModShieldSystem.grant(p,"shuo_test",500,100);p.invulnerableTime=0;
            incoming(p,enemy,DamageCalc.Kind.TRUE);
            float shield=ModShieldSystem.value(p);h.assertTrue(Math.abs(shield-420)<.05,"Actual incoming true damage bypasses parry but uses the normal mod shield");
            p.invulnerableTime=0;incoming(p,enemy,DamageCalc.Kind.PHYSICAL);
            h.assertTrue(ModShieldSystem.value(p)==shield&&w.recastAvailable(AbilityRuntime.context(p,w)),"Actual incoming damage event preserves shield on parry and opens counter");
            p.invulnerableTime=0;incoming(p,enemy,DamageCalc.Kind.PHYSICAL);
            h.assertTrue(ModShieldSystem.value(p)<shield,"A second actual hit is not blocked by the consumed guard");ModShieldSystem.clearFor(p);
            AbilityRuntime.clear(p);d.setBuff("g1_stun",0);d.setSkillLevel(FirstGroupSkills.node("shuo",0).getId(),5);
            float base=ShuoCombat.draw(p,0,9);h.assertTrue(base==0&&ShuoCombat.drawn(p),"Empty gauge does not create bonus");
            h.assertTrue(ShuoCombat.requestSheathe(p)&&ShuoCombat.blocked(p)&&!ShuoCombat.requestSheathe(p),"Manual sheathe locks action, duplicate rejected");
            AbilityRuntime.clear(p);d.setMana(0);d.setCooldown(w.getId(),0);SkillSystem.cast(p,1);h.assertTrue(d.getCooldown(w.getId())==0&&!w.recastAvailable(AbilityRuntime.context(p,w)),"No mana never guards or creates recast");
        }finally{AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);p.discard();enemy.discard();TrainingMode.setNoCooldown(nc);TrainingMode.setNoMana(nm);}
    }
    private record Fixture(ServerPlayer p,LivingEntity target,int index,Vec3 origin,Vec3 endpoint,List<ShuoViewPayload> cues){}
    static void start(GameTestHelper h){
        ShuoFormChecks.start(h);
        ShuoWorldVfxChecks.start(h);
        ShuoMiniReworkChecks.start(h);
        ShuoWeaponStateChecks.start(h);
        ShuoDashChecks.start(h);
        ShuoSilentLineChecks.start(h);
        var fixtures=new ArrayList<Fixture>();
        for(int i=0;i<8;i++){
            var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(45,130+i*6,5)));var cues=new ArrayList<ShuoViewPayload>();
            var p=player(h,at,cues);var d=PlayerDataProvider.get(p);int node=i==0?0:i==1||i==7?5:i==2?7:i==3?4:6;
            d.setSkillLevel(FirstGroupSkills.node("shuo",node).getId(),1);var t=target(h,at.add(0,0,i==3?5:2.5));
            if(i==0)ShuoCombat.sheathe(p);else {var s=FirstGroupSkills.node("shuo",node);s.release(AbilityRuntime.context(p,s));}
            fixtures.add(new Fixture(p,t,node,at,at.add(0,0,node==4?7:12),cues));
        }
        boolean[] done={false};for(int n=1;n<=55;n++){final int age=n;h.runAfterDelay(n,()->{if(done[0])return;
            try{
                for(int i=0;i<fixtures.size();i++){
                    var f=fixtures.get(i);var p=f.p;var d=PlayerDataProvider.get(p);
                    if(i==5&&age==3)d.setBuff("g1_stun",1);
                    if(i==5&&age==4)d.setBuff("g1_stun",0);
                    if(i==6&&age==23)d.setBuff("g1_stun",2);
                    if(i==6&&age==25)d.setBuff("g1_stun",0);
                    if(i==7&&age==9)d.setBuff("g1_stun",1);
                    if(i==7&&age==10)d.setBuff("g1_stun",0);
                    if(i==1&&age==3)p.setPos(f.origin.add(0,.3,0));
                    if(i==1&&age==5)p.setPos(f.origin);
                    if(AbilityRuntime.moving(p))p.move(MoverType.SELF,AbilityTargeting.clampPrediction(p.position(),f.endpoint,p.getDeltaMovement()));
                    AbilityRuntime.tick(p);
                    if(i==0){if(age==7)h.assertTrue(ShuoCombat.drawn(p)==false&&ShuoCombat.blocked(p),"Sheathing holds the 8tick lock");
                        if(age==8)h.assertTrue(!ShuoCombat.drawn(p)&&!ShuoCombat.blocked(p),"Sheathe completes exactly once");
                        if(age==55){h.assertTrue(ShuoCombat.gauge(p)>=90,"Real per-tick sheathed accumulation");float bonus=ShuoCombat.draw(p,0,9);h.assertTrue(bonus>=75&&bonus<=80&&ShuoCombat.gauge(p)==0&&ShuoCombat.draw(p,0,9)==0,"Draw consumes one finite wisdom-scaled bonus");}}
                    if(i==1&&age==8)h.assertTrue(f.target.getHealth()==1000,"No early charged cone damage");
                    if(i==1&&age==10)h.assertTrue(Math.abs(f.target.getHealth()-883)<.05,"Nine-tick fixed lane starts with 60% of 195");
                    if(i==1&&age==16)h.assertTrue(Math.abs(f.target.getHealth()-805)<.05,"Fixed second cut restores total 195, not a duplicate full hit");
                    if(i==2&&(age==6||age==13)){var s=FirstGroupSkills.node("shuo",7);h.assertTrue(s.recastAvailable(AbilityRuntime.context(p,s)),"Manual combo window exists");s.recast(AbilityRuntime.context(p,s));}
                    if(i==2&&age==20)h.assertTrue(Math.abs(f.target.getHealth()-430)<.05,"Three manual cuts 160 + 160 + 250; no hidden repeated hits");
                    if(i==3&&age==9)h.assertTrue(p.position().distanceTo(f.origin)<1E-7,"Dash does not teleport during windup");
                    if(i==3&&age==20)h.assertTrue(p.position().distanceTo(f.endpoint)<.001&&Math.abs(f.target.getHealth()-895)<.05,"7-block bounded dash, initial 60% wound damage");
                    if(i==3&&age==24)h.assertTrue(Math.abs(f.target.getHealth()-825)<.05,"Blade wound bursts once for remaining 40%");
                    if(i==4&&age==22)h.assertTrue(f.target.getHealth()==1000,"Ultimate marks without premature damage");
                    if(i==4&&age==32)h.assertTrue(Math.abs(f.target.getHealth()-520)<.05&&!ShuoCombat.drawn(p),"Ultimate detonates 480 once after completed resheath");
                    if(i==5&&age==32)h.assertTrue(f.target.getHealth()==1000&&!AbilityRuntime.channeling(p),"Brief windup control interruption cancels damage permanently");
                    if(i==6&&age==32)h.assertTrue(f.target.getHealth()==1000,"Brief control during resheath permanently cancels detonation even after control expires");
                    if(i==7&&age==10)h.assertTrue(f.target.getHealth()==1000&&!ShuoCombat.blocked(p),"Control exactly on the final charge tick cannot leave a permanent attack lock");
                }
                if(age==55){done[0]=true;fixtures.forEach(f->{AbilityRuntime.clear(f.p);f.p.discard();f.target.discard();});System.out.println("[MYRIAD-SHUO-EXECUTION] PASS: real 55 ticks, sheath/gauge, charge/cancel, exact cones, manual combo and bounded dash/ultimate mark-resheath-detonation");}
            }catch(Throwable e){done[0]=true;fixtures.forEach(f->{AbilityRuntime.clear(f.p);f.p.discard();f.target.discard();});throw e;}
        });}
    }
}
