package com.myriad.character.skills.xuanchen.rework;

import com.myriad.character.ability.AbilityMeshBuilder;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxMaterials.INK_HD_HEAD_MODEL;
import static com.myriad.character.ability.AbilityVfxMaterials.INK_BANNER_MODEL;

/** These closed cuboids are exported from the editable Blockbench models, not rendered as cards. */
final class InkModels {
    private InkModels() {}
    private static final int[][] FACES={
        {0,1,3,2}, {4,6,7,5}, {0,4,5,1}, {2,3,7,6}, {0,2,6,4}, {1,5,7,3}
    };
    private static final int[] SHADES={0xDDDBDF,0xF4EBDD,0xABA5A8,0xFFFFFF,0xBDB9BD,0xEEE8DF};

    static void dart(AbilityMeshBuilder b,Vec3 tip,Vec3 forward){
        Vec3 x=side(forward).scale(-1),y=forward.cross(x).normalize();
        double unit=.051;
        draw(b,InkModelParts.DART,tip.subtract(forward.scale(11*unit)),x,y,forward,unit,1);
    }

    static void slip(AbilityMeshBuilder b,Vec3 at,Vec3 forward,double scale,double t){
        Vec3 x=side(forward).scale(-1),up=forward.cross(x).normalize();
        double turn=.24*Math.sin(t*Math.PI*2);
        Vec3 y=up.scale(Math.cos(turn)).add(x.scale(Math.sin(turn)));
        x=y.cross(forward).normalize();
        draw(b,InkModelParts.SLIP,at,x,y,forward,.051*scale,1);
    }

    static void gate(AbilityMeshBuilder b,Vec3 feet,Vec3 normal){
        draw(b,InkModelParts.GATE,feet,side(normal).scale(-1),UP,normal,.0625,1);
    }

    static void banner(AbilityMeshBuilder b,Vec3 feet,Vec3 normal,boolean heaven,double rise){
        double opacity=Math.min(1,4*b.fade);
        draw(b,heaven?InkBannerParts.HEAVEN:InkBannerParts.PROTECTION,feet,
                side(normal).scale(-1),UP.scale(rise),normal,.0635,opacity,INK_BANNER_MODEL);
    }

    private static void draw(AbilityMeshBuilder b,double[][] parts,Vec3 at,Vec3 x,Vec3 y,Vec3 z,double s,double opacity){
        draw(b,parts,at,x,y,z,s,opacity,INK_HD_HEAD_MODEL);
    }

    private static void draw(AbilityMeshBuilder b,double[][] parts,Vec3 at,Vec3 x,Vec3 y,Vec3 z,double s,double opacity,int material){
        for(double[] q:parts){
            Vec3[] corner=new Vec3[8];
            for(int i=0;i<8;i++)corner[i]=at.add(x.scale(q[(i&1)!=0?3:0]*s))
                    .add(y.scale(q[(i&2)!=0?4:1]*s)).add(z.scale(q[(i&4)!=0?5:2]*s));
            for(int i=0;i<6;i++){
                int[] f=FACES[i];
                // Vertex bit order is x, y, z. Reverse to point out of the cuboid.
                if(material==INK_BANNER_MODEL){
                    int uv=6+i*4;
                    // Match Blockbench's top-left origin on the two broad seal faces.
                    int start=i==1?1:0;
                    b.modelFace(corner[f[3-start]],corner[f[(2-start)&3]],corner[f[(1-start)&3]],
                            corner[f[(-start)&3]],material,SHADES[i],opacity,
                            (float)q[uv]/512,(float)q[uv+2]/512,(float)q[uv+1]/128,(float)q[uv+3]/128,i<=1);
                }else{
                    b.modelFace(corner[f[3]],corner[f[2]],corner[f[1]],corner[f[0]],
                            material,(int)q[6],SHADES[i],opacity);
                }
            }
        }
    }
}
