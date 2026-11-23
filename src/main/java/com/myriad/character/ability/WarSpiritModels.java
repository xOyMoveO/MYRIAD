package com.myriad.character.ability;

import net.minecraft.world.phys.Vec3;

import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** UV-mapped closed models. All leading tips use local +Z and share the segment's flight basis. */
public final class WarSpiritModels {
    private WarSpiritModels() {}
    private static final int[][] FACES={{2,3,1,0},{5,7,6,4},{1,5,4,0},{6,7,3,2},{4,6,2,0},{3,7,5,1}};
    private static final int[] SHADE={0xF5E7E1,0xE6E8EB,0xCFD6DB,0xFFFFFF,0xDEE4E7,0xF4F9FF};

    public static boolean material(int id) {
        return id>=WAR_HD_WAVE_MODEL&&id<=SPIRIT_HD_BEACON_MODEL;
    }

    public static boolean flight(com.myriad.network.AbilityVfxPayload p) {
        return p.kind()==AbilityVfxGeometry.FLIGHT&&(p.theme()==8&&p.variant()==5||p.theme()==9&&p.variant()==2);
    }

    public static boolean owns(com.myriad.network.AbilityVfxPayload p) {
        return flight(p)||p.theme()==9&&p.variant()==4&&p.kind()==AbilityVfxGeometry.SEAL
                &&p.angle()==360&&p.radius()>=3&&p.life()>20;
    }

    public static void wave(AbilityMeshBuilder b,Vec3 at,Vec3 f,Vec3 right) {
        draw(b,at,f,right,WarSpiritModelParts.WAVE,WAR_HD_WAVE_MODEL,.067,0);
    }

    public static void crane(AbilityMeshBuilder b,Vec3 at,Vec3 f,Vec3 right,double clock) {
        draw(b,at,f,right,WarSpiritModelParts.CRANE,SPIRIT_HD_CRANE_MODEL,.057,.22*Math.sin(clock*.72));
    }

    public static void beacon(AbilityMeshBuilder b,Vec3 at,Vec3 f,Vec3 right) {
        draw(b,at,f,right,WarSpiritModelParts.BEACON,SPIRIT_HD_BEACON_MODEL,.155,0);
    }

    private static void draw(AbilityMeshBuilder b,Vec3 at,Vec3 f,Vec3 right,float[][] parts,int material,double scale,double flap) {
        Vec3 x=right.scale(scale),y=f.cross(x),z=f.scale(scale);
        for(float[] part:parts) {
            Vec3[] vertices=new Vec3[8];
            // Folded wing strips articulate together; the beak, chest and tail stay rigid.
            double cx=0;
            for(int v=0;v<8;v++)cx+=part[1+v*3]/8.0;
            double theta=Math.abs(cx)>2?Math.copySign(1,cx)*flap:0;
            double root=Math.copySign(1.35,cx),c=Math.cos(theta),s=Math.sin(theta);
            for(int v=0;v<8;v++) {
                int k=1+v*3; double dx=part[k]-root,dy=part[k+1];
                vertices[v]=at.add(x.scale(root+c*dx-s*dy)).add(y.scale(s*dx+c*dy)).add(z.scale(part[k+2]));
            }
            for(int i=0;i<FACES.length;i++) {
                int[] face=FACES[i];
                b.modelFace(vertices[face[0]],vertices[face[1]],vertices[face[2]],vertices[face[3]],
                        material,(int)part[0],SHADE[i],1);
            }
        }
    }
}
