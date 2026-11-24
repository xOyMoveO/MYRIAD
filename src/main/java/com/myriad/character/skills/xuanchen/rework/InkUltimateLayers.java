package com.myriad.character.skills.xuanchen.rework;

import com.myriad.character.ability.AbilityMeshBuilder;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Two different field silhouettes: four raised banners versus eight low closure points. */
final class InkUltimateLayers {
    private InkUltimateLayers() {}

    static void field(AbilityMeshBuilder g,AbilityVfxPayload p,double radius,boolean warning){
        Vec3 at=p.from();
        boolean heaven=p.variant()==6;
        int phase=(int)(g.age/3);
        for(int i=0;i<8;i++){
            double a=(i+.5)*Math.PI/4;
            Vec3 normal=new Vec3(Math.cos(a),0,Math.sin(a)),tangent=side(normal);
            Vec3 mark=at.add(normal.scale(radius*.924)).add(0,.015,0);
            double width=radius*.382;
            double reveal=warning&&!heaven?Math.clamp(g.progress*1.55-i*.065,.16,1):1;
            double strength=warning?.66:heaven?.88:.72;
            g.frame(mark,tangent,normal,width*reveal,.28,INK_HD_BRUSH,phase+i,strength);
            g.frame(mark.add(0,.017,0),tangent,normal,width*reveal,.38,
                    heaven?INK_ARRAY_WAVE:INK_BOUNDARY_WEAVE,phase+i,strength*.96);
            if(warning)continue;
            if(!heaven){
                Vec3 foot=at.add(normal.scale(radius*.94)).add(0,.043,0);
                g.frame(foot,tangent,normal,.42,.29,INK_HD_STAMP,3,.63);
                if(!g.reduced)g.frame(foot.add(0,.043,0),tangent,UP,.41,.05,INK_HD_FIBERS,phase+i,.56);
                continue;
            }
            if((i&1)!=0||!InkVfxGeometry.bannerField(p))continue;
            int flag=i/2;
            double rise=Math.clamp((g.age-flag*1.6)/7,0,1);
            if(rise<=0)continue;
            Vec3 foot=at.add(normal.scale(radius*.87));
            g.frame(foot.add(0,.045,0),tangent,normal,.67,.61,INK_HD_STAMP,1,.84*rise);
            g.frame(foot.add(0,.06,0),tangent,normal,.76,.72,INK_HD_GILT,phase+flag,.82*rise);
            InkModels.banner(g,foot,normal,true,rise);
            Vec3 crown=foot.add(0,1.85*rise,0);
            g.frame(crown.add(normal.scale(.11)),tangent,UP,.48,.16*rise,INK_HD_GILT,phase+flag,.48);
            if(!g.reduced)g.frame(crown.add(tangent.scale(.49)).add(normal.scale(.04)),
                    tangent,UP,.14,.24*rise,INK_HD_FIBERS,phase+flag,.40);
        }
    }

    static void close(AbilityMeshBuilder g,Vec3 at,Vec3 axis,double radius){
        int phase=(int)(g.age*.68);
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4;
            Vec3 normal=new Vec3(Math.cos(a),0,Math.sin(a)),tangent=side(normal);
            double travel=1-.53*Math.clamp(g.progress*1.45,0,1);
            Vec3 p=at.add(normal.scale(radius*travel)).add(0,.105,0);
            g.frame(p,tangent,UP,.56,.28,INK_BOUNDARY_WEAVE,phase+i,.94);
            if(!g.reduced){
                g.frame(p.add(0,.28*g.progress,0),tangent,UP,.4,.32,INK_HD_SCRAPS,phase+i,.82);
                g.frame(p.add(0,.16,0),tangent,UP,.49,.39,INK_HD_GILT,phase+i,.80);
            }
        }
        for(int i=0;i<2;i++){
            Vec3 direction=i==0?axis:side(axis);
            g.frame(at.add(0,.08+i*.02,0),direction,side(direction),radius*(.82-.29*g.progress),
                    .36,INK_HD_BRUSH,phase+i,.96);
            g.frame(at.add(0,.095+i*.02,0),direction,side(direction),radius*(.82-.29*g.progress),
                    .49,INK_BOUNDARY_WEAVE,phase+i,.99);
        }
        g.frame(at.add(0,.14,0),X,Z,.68+g.progress*.37,.68+g.progress*.37,INK_HD_GILT,phase,.91);
    }
}
