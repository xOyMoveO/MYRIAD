package com.myriad.character.ability;

import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;

/** Closed projectile and ice-spike cuboids exported from the editable models. */
public final class FrostHerbModels {
    private FrostHerbModels() {}
    // Blockbench face order, with outward winding for the particle renderer's face culling.
    private static final int[][] FACES={{2,3,1,0},{7,6,4,5},{3,7,5,1},{6,2,0,4},{6,7,3,2},{0,1,5,4}};
    private static final int[] SHADES={0xEBF0F2,0xECE9E6,0xD4E3E5,0xBCCDCA,0xFFFFFF,0xBDCBD3};

    public static void frost(AbilityMeshBuilder b,Vec3 at,Vec3 forward,int variant,double tick){
        boolean pearl=variant==0;
        var model=pearl?FrostHerbModelParts.FROST_PEARL:
                variant==5?FrostHerbModelParts.GLACIER_LANCE:FrostHerbModelParts.ICE_VOLLEY;
        draw(b,at,forward,model,FROST_PROJECTILE_MODEL,pearl?.071:.069,spin(tick,variant==5?40:20));
    }

    public static void herb(AbilityMeshBuilder b,Vec3 at,Vec3 forward,int variant,double tick){
        boolean dew=variant==0;
        draw(b,at,forward,dew?FrostHerbModelParts.DEW_BUD:FrostHerbModelParts.BRIAR_SEED,
                HERB_HD_SEED_MODEL,dew?.071:.079,spin(tick,dew?40:24));
    }

    private static double spin(double tick,int period){
        return (tick%period)*Math.PI*2/period;
    }

    private static void draw(AbilityMeshBuilder b,Vec3 at,Vec3 forward,float[][] model,int material,double unit,double spin){
        Vec3 right=side(forward).scale(-1),up=forward.cross(right);
        Vec3 x=right.scale(Math.cos(spin)).add(up.scale(Math.sin(spin))).scale(unit);
        Vec3 y=forward.cross(x),z=forward.scale(unit);
        drawFaces(b,at,model,material,x,y,z,1);
    }

    public static void spikes(AbilityMeshBuilder b,Vec3 foot,Vec3 heading,double height,boolean expanse){
        double phase=Math.clamp(b.age/(expanse?3.6:2.8),0,1);
        double rise=.22+.78*(1-Math.pow(1-phase,3));
        double width=expanse?.145:.13;
        Vec3 x=UP.cross(heading).scale(width),z=heading.scale(width);
        Vec3 y=UP.scale(height*rise/(expanse?18.5:24));
        drawFaces(b,foot.add(0,-.09,0),expanse?HanzeIceSpikeParts.FROZEN_EXPANSE:HanzeIceSpikeParts.ABSOLUTE_ZERO,
                FROST_PROJECTILE_MODEL,x,y,z,b.fade);
    }

    private static void drawFaces(AbilityMeshBuilder b,Vec3 at,float[][] model,int material,Vec3 x,Vec3 y,Vec3 z,double opacity){
        for(var part:model){
            var corners=new Vec3[8];
            for(int i=0;i<8;i++){
                int k=i*3;
                corners[i]=at.add(x.scale(part[k])).add(y.scale(part[k+1])).add(z.scale(part[k+2]));
            }
            for(int i=0;i<FACES.length;i++){
                var f=FACES[i];
                int uv=24+i*4;
                b.modelFace(corners[f[0]],corners[f[1]],corners[f[2]],corners[f[3]],material,SHADES[i],opacity,
                        part[uv]/512,part[uv+2]/512,part[uv+1]/128,part[uv+3]/128,i<4);
            }
        }
    }
}
