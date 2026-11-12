package com.myriad.game;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.skills.lingyuan.rework.SwordFlipbook;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import javax.imageio.ImageIO;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Real packaged frame pixels, atlas UVs, playback phases and continuous short packets. */
final class LingyuanFlipbookChecks {
    static void verify(GameTestHelper h){
        String[] names={"slash","stream","seal","impact","filament","motes"};
        try{
            for(String name:names){
                try(var stream=LingyuanFlipbookChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/lingyuan_anim_"+name+".png")){
                    h.assertTrue(stream!=null,"Animated material is packaged: "+name);
                    var image=ImageIO.read(stream);var hashes=new HashSet<Integer>();
                    h.assertTrue(image.getWidth()==1024&&image.getHeight()==512&&image.getColorModel().hasAlpha(),"Four by two RGBA atlas: "+name);
                    for(int i=0;i<8;i++){
                        int[] pixels=image.getRGB(i%4*256,i/4*256,256,256,null,0,256);int occupied=0;
                        hashes.add(Arrays.hashCode(pixels));
                        for(int y=0;y<256;y++)for(int x=0;x<256;x++){
                            int alpha=pixels[y*256+x]>>>24;if(alpha>0)occupied++;
                            if(x<12||y<12||x>=244||y>=244)h.assertTrue(alpha==0,"No clipped glow or neighbouring frame bleed: "+name+"/"+i);
                        }
                        h.assertTrue(occupied>0,"Every authored frame contains real artwork");
                    }
                    h.assertTrue(hashes.size()==8,"Eight genuinely different painted frames, not copies: "+name);
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        for(int material:new int[]{LINGYUAN_PIXEL_SLASH,LINGYUAN_PIXEL_STREAM,LINGYUAN_PIXEL_SEAL,LINGYUAN_PIXEL_FILAMENT,LINGYUAN_PIXEL_MOTES}){
            double step=SwordFlipbook.ticksPerFrame(material);var visited=new HashSet<Integer>();
            for(int frame=0;frame<8;frame++){
                var c=SwordFlipbook.loop(material,0,(float)(frame*step+.01),0);visited.add(c.frame());
                h.assertTrue(c.frame()==frame,"Loop visits all eight frames in authored order");
                h.assertTrue(c.u1()-c.u0()==.25f&&c.v1()-c.v0()==.5f,"Samples one cell, not the whole sheet");
                h.assertTrue(SwordFlipbook.loop(material,0,(float)(frame*step+step*8+.01),0).equals(c),"Exact wrap without skipping frames");
            }
            h.assertTrue(visited.size()==8,"No frozen material");
            for(long time:new long[]{0,11,23,79,100,1_000_000_003L,Long.MAX_VALUE-100}){
                h.assertTrue(SwordFlipbook.loop(material,time,1.25f,2).equals(SwordFlipbook.loop(material,time+1,.25f,2)),"Short packet renewal shares continuous world phase, including late worlds");
                h.assertTrue(SwordFlipbook.loop(material,time,.25f,0).frame()!=SwordFlipbook.loop(material,time,.25f,3).frame(),"Crossed layers have independent phase offsets");
            }
        }
        for(int frame=0;frame<8;frame++)h.assertTrue(SwordFlipbook.burst((frame+.1)/8).frame()==frame,"One-shot keeps ignition/bloom/dispersal order");
        h.assertTrue(SwordFlipbook.burst(-1).frame()==0&&SwordFlipbook.burst(1).frame()==7&&SwordFlipbook.burst(2).frame()==7,"Impact clamps instead of looping back to an explosion");
        var from=Vec3.ZERO;var to=new Vec3(0,0,2);
        for(int material:new int[]{LINGYUAN_PIXEL_STREAM,LINGYUAN_PIXEL_SEAL,LINGYUAN_PIXEL_FILAMENT,LINGYUAN_PIXEL_MOTES}){
            int kind=material==LINGYUAN_PIXEL_STREAM?AbilityVfxGeometry.FLIGHT:AbilityVfxGeometry.CHARGE;
            var p=new AbilityVfxPayload(1,-1,5,7,kind,from,to,1.2f,300,8,100,false);
            var q=new AbilityVfxPayload(1,-1,5,7,kind,from,to,1.2f,300,8,101,false);
            var a=uv(AbilityVfxGeometry.build(p,1.25f,false),material);
            h.assertTrue(!a.isEmpty()&&a.equals(uv(AbilityVfxGeometry.build(q,.25f,false),material)),"Actual runtime composition continues its frame across packets");
        }
        for(int i=0;i<8;i++){
            var p=new AbilityVfxPayload(1,-1,5,2,AbilityVfxGeometry.BURST,from,to,1,0,8,100,false);
            var mesh=AbilityVfxGeometry.build(p,i+.1f,false);
            var impact=mesh.stream().filter(q->q.material()==LINGYUAN_PIXEL_IMPACT).findFirst().orElseThrow();
            var c=SwordFlipbook.burst((i+.1)/8);
            h.assertTrue(impact.startU()==c.u0()&&impact.startV()==c.v0(),"Actual burst renderer uses its own local life, not global loop phase");
        }
        System.out.println("[MYRIAD-LINGYUAN-FLIPBOOK] PASS: 6 atlases / 48 unique painted frames, alpha gutters, UV cells, 5 independent loop rates, one-shot stages, cross-packet continuity");
    }
    private static List<String> uv(List<AbilityVfxGeometry.Quad> mesh,int material){
        return mesh.stream().filter(q->q.material()==material).map(q->q.startU()+","+q.startV()+","+q.endU()+","+q.endV()).toList();
    }
}
