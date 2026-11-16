package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.skills.fenjue.rework.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import javax.imageio.ImageIO;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

final class FlameGroundChecks {
    static void verify(GameTestHelper h){
        for(String name:List.of("ring","field"))try(var in=FlameGroundChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/fenjue_ground_"+name+".png")){
            var im=ImageIO.read(in);h.assertTrue(im.getWidth()==2048&&im.getHeight()==1024&&im.getColorModel().hasAlpha(),"Native-alpha eight-frame padded atlas");
            var hashes=new HashSet<Integer>();
            for(int i=0;i<8;i++){int hash=1,visible=0,empty=0;
                for(int y=0;y<512;y++)for(int x=0;x<512;x++){
                    int p=im.getRGB(i%4*512+x,i/4*512+y),a=p>>>24;hash=31*hash+p;
                    if(a>25)visible++;if(a==0)empty++;
                    if(x<30||x>=482||y<30||y>=482)h.assertTrue(a==0,"Transparent gutters keep adjacent frames out of sampled cells");
                }
                h.assertTrue(visible>30_000&&empty>120_000&&hashes.add(hash),"Distinct detailed phases with substantial transparent terrain gaps, not an opaque plate");
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        var seen=new HashSet<Integer>();
        for(int time=0;time<16;time++)seen.add(FlameGroundFlipbook.frame(time));
        h.assertTrue(seen.size()==8&&FlameGroundFlipbook.frame(16)==FlameGroundFlipbook.frame(0),"Eight real phases at ten frames/second, bounded loop");
        int cases=0;
        for(int variant=4;variant<=7;variant++)for(int kind:new int[]{SEAL,BURST,CHARGE,SHIELD})for(boolean low:new boolean[]{false,true})for(float age:new float[]{0,2,5,10,16,39,78}){
            var p=new AbilityVfxPayload(1,-1,2,variant,kind,Vec3.ZERO,new Vec3(0,0,1),5,360,80,100,false);
            var mesh=AbilityVfxGeometry.build(p,age,low);h.assertTrue(!mesh.isEmpty()&&mesh.size()<=(low?80:192),"World recipe remains visible and within LOD budget");
            h.assertTrue(mesh.stream().noneMatch(q->q.material()==FENJUE_GROUND),"No old solid fissure or rim strips in released/charging ground effects");
            var ground=mesh.stream().filter(q->flameGround(q.material())).toList();h.assertTrue(!ground.isEmpty(),"Every major skill contains authored animated ground planes");
            for(var q:ground){
                h.assertTrue(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).y>0,"Grid faces upward");
                h.assertTrue(q.startU()>=0&&q.endU()<=1&&q.startV()>=0&&q.endV()<=1&&q.endU()>q.startU()&&q.endV()>q.startV(),"Nondegenerate native atlas UVs, not one stretched centre texel");
                h.assertTrue(Math.floor((q.startU()+1E-6)*4)==Math.floor((q.endU()-1E-6)*4)
                        &&Math.floor((q.startV()+1E-6)*2)==Math.floor((q.endV()-1E-6)*2),"One selected frame per tile; never bleed into neighbours");
            }cases++;
        }
        System.out.println("[MYRIAD-FLAME-GROUND] PASS: two true-alpha eight-frame native atlases, transparent gutters/holes, fixed-grid phases/loop, "+cases+" four-skill timing/LOD cases, no old colour strips");
    }
}
