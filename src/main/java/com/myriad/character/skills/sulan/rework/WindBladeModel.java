package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.AbilityMeshBuilder;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxMaterials.WIND_HD_WING_MODEL;

/** Rotated, textured cuboids exported from the Blockbench asset, with +Z as the leading tip. */
final class WindBladeModel {
    private WindBladeModel(){}
    private static final int[][] FACES={
            {2,3,1,0}, {5,7,6,4}, {1,5,4,0}, {6,7,3,2}, {4,6,2,0}, {3,7,5,1}
    };
    private static final int[] SHADE={0xEBF4EC,0xE2EBE9,0xB9CCCB,0xFFFFFF,0xCBDEDB,0xE8F3EC};

    static void draw(AbilityMeshBuilder g,Vec3 at,Vec3 forward,Vec3 flightRight,double spin,double scale){
        Vec3 flightUp=forward.cross(flightRight).normalize();
        Vec3 x=flightRight.scale(Math.cos(spin)).add(flightUp.scale(Math.sin(spin))).scale(scale);
        Vec3 y=forward.cross(x);
        Vec3 z=forward.scale(scale);
        for(var part:WindBladeModelParts.PARTS){
            Vec3[] vert=new Vec3[8];
            for(int i=0;i<8;i++){
                int k=1+i*3;
                vert[i]=at.add(x.scale(part[k])).add(y.scale(part[k+1])).add(z.scale(part[k+2]));
            }
            for(int i=0;i<FACES.length;i++){
                int[] f=FACES[i];
                g.modelFace(vert[f[0]],vert[f[1]],vert[f[2]],vert[f[3]],WIND_HD_WING_MODEL,
                        (int)part[0],SHADE[i],1);
            }
        }
    }
}
