package com.myriad.game;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxMaterials;
import com.myriad.character.skills.yingxiao.rework.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

final class NightBasicNeedleChecks {
    static void verify(GameTestHelper h){
        var hashes=new HashSet<Integer>();
        for(int stage=0;stage<3;stage++){
            var p=NightBasicVisuals.swingRecipe(1,new Vec3(0,1.62,0),new Vec3(1,0,0),stage,100);
            h.assertTrue(p.valid()&&p.kind()==ARC&&p.life()==6&&p.angle()==301+stage,"Three compact ordinary attack recipes");
            var mesh=AbilityVfxGeometry.build(p,2,false);
            h.assertTrue(hashes.add(mesh.hashCode()),"Stab, hammer and wrist cut are visually different");
            h.assertTrue(!mesh.isEmpty()&&mesh.size()<=36,"No excessive ordinary attack particles");
            h.assertTrue(mesh.stream().allMatch(q->q.material()>=50&&q.material()<=55),"Basic uses the approved gray animated layers only");
            h.assertTrue(AbilityVfxGeometry.build(p,6,false).isEmpty(),"Basic slash expires in 0.3 seconds");
        }
        var impact=NightBasicVisuals.hitRecipe(1,new Vec3(2,1,0),new Vec3(1,0,0),100);
        h.assertTrue(impact.valid()&&impact.angle()==304,"Separate confirmed contact recipe");
        h.assertTrue(AbilityVfxGeometry.build(impact,2,false).stream().anyMatch(q->q.material()==55),"Contact includes animated split flash");
        for(var f:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(-.4,.5,.7).normalize())){
            var p=new AbilityVfxPayload(1,-1,0,2,FLIGHT,Vec3.ZERO,f.scale(1.5),.22f,0,2,100,false,7);
            for(boolean low:new boolean[]{false,true}){
                var mesh=AbilityVfxGeometry.build(p,.5f,low);
                var body=mesh.stream().filter(q->q.material()==AbilityVfxMaterials.NIGHT_NEEDLE_MODEL).toList();
                h.assertTrue(body.size()==NightNeedleModel.faces(low),"Closed model is submitted whole in either LOD");
                double min=1E9,max=-1E9,width=0;
                for(var q:body)for(var v:List.of(q.a(),q.b(),q.c(),q.d())){
                    Vec3 local=v.subtract(f.scale(.75));double d=local.dot(f);min=Math.min(min,d);max=Math.max(max,d);
                    width=Math.max(width,local.subtract(f.scale(d)).length());
                    h.assertTrue(NightVfxGeometry.bounds(p).contains(v),"Needle inside actual culling bounds");
                }
                h.assertTrue(Math.abs(max)<1E-5&&Math.abs(min+1.25)<1E-5&&width<.16,"Slender 1.25 block model, point leads, no scale drift");
                h.assertTrue(mesh.size()<=(low?80:192),"Needle plus layered wake stays in existing budgets");
            }
            h.assertTrue(AbilityVfxGeometry.build(p,1,false).stream().noneMatch(q->q.material()==56),"Never park the 3D needle between stale frames");
        }
        // This task cannot change the actual weapon or the full-charge vanilla attack route.
        var p=(net.minecraft.server.level.ServerPlayer)h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        boolean mod=ModState.isEnabled();
        try{
            var data=com.myriad.player.PlayerDataProvider.get(p);data.setCharacterId("yingxiao");
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(com.myriad.registry.ModItems.YINGCI.get()));
            ModState.setEnabled(true);h.assertTrue(NightBasicVisuals.eligible(p),"Night weapon gate");
            data.setCharacterId("lingyuan");h.assertTrue(!NightBasicVisuals.eligible(p),"No other hero gets Night basic VFX");
            data.setCharacterId("yingxiao");ModState.setEnabled(false);h.assertTrue(!NightBasicVisuals.eligible(p),"Mod off is untouched vanilla");
        }finally{NightBasicVisuals.clear(p.getUUID());p.discard();ModState.setEnabled(mod);}
        System.out.println("[MYRIAD-NIGHT-BASIC-NEEDLE] PASS: three unique basics/contact, role/mod gates; closed full/LOD needle, 4 travel directions, axis/scale/bounds/budgets, no stale body");
    }
}
