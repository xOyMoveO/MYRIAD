package com.myriad.game;

import com.myriad.character.skills.fenjue.rework.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import javax.imageio.ImageIO;
import java.util.*;

final class CrispFlameSlashChecks {
    static void verify(GameTestHelper h){
        int frames=0,minY=512,maxY=0;var colors=new HashSet<Integer>();var hashes=new HashSet<Integer>();
        try(var in=CrispFlameSlashChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/fenjue_crisp_slash.png")){
            h.assertTrue(in!=null,"Dedicated crisp slash texture is packaged");var im=ImageIO.read(in);
            h.assertTrue(im.getWidth()==2048&&im.getHeight()==1024&&im.getColorModel().hasAlpha(),"Eight 512px RGBA canvases");
            for(int f=0;f<8;f++){
                int visible=0,hash=1;
                for(int y=0;y<512;y++)for(int x=0;x<512;x++){
                    int pixel=im.getRGB(f%4*512+x,f/4*512+y),alpha=pixel>>>24;hash=31*hash+pixel;
                    h.assertTrue(alpha==0||alpha==255,"Deliberate hard-edged pixel mask, not soft smoke alpha");
                    if(alpha==0)continue;
                    h.assertTrue(x>=48&&x<464&&y>=48&&y<464,"No clipped cells or edge bleed");visible++;colors.add(pixel&0xFFFFFF);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                }
                h.assertTrue(visible>1000&&hashes.add(hash),"Distinct solid phase, not an enlarged empty or repeated frame");frames++;
                var cell=CrispFlameSlashFrames.cell(f);
                h.assertTrue(Math.abs(cell.u0()-(f%4*512+.5)/2048)<1E-7&&Math.abs(cell.v0()-(f/4*512+.5)/1024)<1E-7,"Actual 512px half-texel addressing");
            }
            h.assertTrue(colors.size()<=32,"Stable restrained pixel palette, no noisy photographic texture");
            h.assertTrue(minY>=184&&maxY<408&&FlameCrossSlashVfx.LIFT==-.12,"Shared source contour retained; the eye-origin packet is no longer lifted another 1.8 blocks");
            double[] lowest={99,99},highest={-99,-99};
            for(int f=0;f<8;f++)for(int y=184;y<408;y++)for(int x=0;x<512;x++){
                if((im.getRGB(f%4*512+x,f/4*512+y)>>>24)==0)continue;
                double t=(y/511.0-FlameCrossSlashVfx.SOURCE_TOP)/(FlameCrossSlashVfx.SOURCE_BOTTOM-FlameCrossSlashVfx.SOURCE_TOP);
                var p=FlameCrossSlashVfx.localPoint(t,x/511.0,4);
                h.assertTrue(p.y>=0&&Math.toDegrees(Math.atan2(p.y,p.x))<=120.01,"Every painted pixel lies on the upper 0..120-degree sector, no lower/full-circle fragments");
                for(int cut=0;cut<2;cut++){
                    double height=1.62+FlameCrossSlashVfx.LIFT+(cut==0?1:-1)*(p.x-4*FlameCrossSlashVfx.RADIUS*.25)*Math.sin(Math.toRadians(FlameCrossSlashVfx.CUT_ANGLE))-.025*Math.cos(Math.toRadians(FlameCrossSlashVfx.CUT_ANGLE));
                    lowest[cut]=Math.min(lowest[cut],height);highest[cut]=Math.max(highest[cut],height);
                    h.assertTrue(height>.10&&height<3.05,"Every painted pixel of BOTH cuts stays near player height, above floor and below 3.05 blocks, not at the old 5-7 block floating height");
                }
            }
            h.assertTrue(Math.abs((highest[0]+lowest[0])-(highest[1]+lowest[1]))<.25,"Mirrored strokes have matching vertical envelopes, not one floating well above the other");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        var p=FlameSlashAim.recipe(1,Vec3.ZERO,new Vec3(0,0,1),0,100);
        var all=FlameCrossSlashVfx.build(p,2,false);var mesh=all.stream().filter(q->q.material()==70).toList();
        h.assertTrue(mesh.size()>4&&mesh.size()<=96,"One curved stroke, two double-sided layers, no cross-fade ghosts");
        var a=mesh.get(0);var b=mesh.get(mesh.size()/2);
        h.assertTrue(a.a().equals(b.a())&&a.b().equals(b.b())&&a.c().equals(b.c())&&a.d().equals(b.d())
            &&a.startU()==b.startU()&&a.endU()==b.endU()&&a.startV()==b.startV()&&a.endV()==b.endV(),"Inner/outer images use exactly aligned geometry and phase");
        var axes=FlameSlashAim.basis(p.to().subtract(p.from()),p.angle());
        var diagonal=FlameCrossSlashVfx.diagonal(p,0);
        for(int i=0;i<=120;i++){
            double t=i/120.0;var p2=FlameCrossSlashVfx.localPoint(t,FlameCrossSlashVfx.sourceEdge(t)/512,4);
            h.assertTrue(Math.abs(p2.length()-4.2)<1E-6&&Math.abs(Math.toDegrees(Math.atan2(p2.y,p2.x))-i)<1E-6,"Larger true circular 120-degree upper contour, not an ellipse or a vertically stretched crescent");
        }
        h.assertTrue(FlameCrossSlashVfx.window(3.8,0,14).head()==1&&FlameCrossSlashVfx.window(3.8,0,14).tail()==0,"The entire reference arc is briefly visible before its tail erodes");
        for(var q:all){
            var normal=diagonal.cross(axes.forward()).normalize();
            h.assertTrue(Math.abs(q.b().subtract(q.a()).dot(normal))<1E-6&&Math.abs(q.d().subtract(q.a()).dot(normal))<1E-6,"Curved picture stays in the original thin XY/forward plane");
        }
        h.assertTrue(all.stream().anyMatch(q->q.material()==69)&&FlameCrossSlashVfx.build(p,2,true).stream().allMatch(q->q.material()==70),"Sparse animated ember after-flame supplements the full setting; low LOD omits it");
        System.out.println("[MYRIAD-FLAME-CRISP] PASS: "+frames+" original crisp phases, actual painted pixels in upper 120-degree sector, exact circular contour, aligned layers, ground clearance and original forward plane");
    }
}
