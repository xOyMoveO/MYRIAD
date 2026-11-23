package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.AbilityMeshBuilder;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.ability.FrostHerbModels;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.ability.FrostHerbLayers.*;

public final class IceVfxGeometry {
    private IceVfxGeometry() {}

    public static boolean usesSpikeModel(AbilityVfxPayload p) {
        return p.theme()==6&&p.kind()==PILLAR&&(p.variant()==4||p.variant()==6);
    }

    public static List<Quad> build(AbilityVfxPayload p, float age, boolean reduced) {
        var b = new AbilityMeshBuilder(p,age,reduced,maxQuads(p));
        Vec3 at = p.from(), end = p.to(), forward = direction(at,end), side = side(forward);
        double clock = p.started()%240 + age;
        double r = p.kind()==TRAIL&&p.angle()==270?p.radius():Math.max(.25,p.radius());
        switch (p.kind()) {
            case FLIGHT -> {
                if (at.distanceToSqr(end) < 1e-8) break;
                Vec3 head = at.lerp(end,Math.clamp(age,0,1));
                boolean lance = p.variant()==5;
                Vec3 normal = side.cross(forward);
                double rotation = clock*(lance?.17:.41);
                Vec3 wing = side.scale(Math.cos(rotation)).add(normal.scale(Math.sin(rotation)));
                Vec3 up = wing.cross(forward);
                com.myriad.character.ability.FrostHerbModels.frost(b,head,forward,p.variant(),clock);
                double size=p.variant()==0?.71:lance?1.33:1;
                Vec3 rear = head.subtract(forward.scale((lance?.75:.31)*size));
                Vec3 tail = rear.subtract(forward.scale(.92*size));
                ribbon(b,tail,rear,wing,.20*size,FROST_HD_STREAM,clock,.96);
                ribbon(b,tail.add(up.scale(.027)),rear.add(up.scale(.027)),up,.25*size,FROST_HD_MIST,clock+2,.72);
                if(!reduced)b.frame(rear.subtract(forward.scale(.21)),forward,up,.55*size,.23*size,FROST_HD_SHARD,(int)(clock*.8+2),.87);
            }
            case TRAIL, BEAM -> {
                Vec3 up = side.cross(forward);
                ribbon(b,at,end,side,r,FROST_HD_STREAM,clock,.72);
                ribbon(b,at.add(up.scale(.024)),end.add(up.scale(.024)),up,r*1.6,FROST_HD_MIST,clock*.66+2,.66);
                if (!b.reduced) b.frame(at.lerp(end,.56),forward,up,at.distanceTo(end)*.52,r*1.12,
                        FROST_HD_SHARD,(int)(clock*.81),.63);
            }
            case SEAL -> seal(b,p,at,r,clock);
            case SHIELD -> shield(b,p.variant(),at,r,clock);
            case PILLAR -> eruption(b,p,at,end,clock);
            case CHARGE -> {
                rim(b,at,r,FROST_HD_FISSURE,clock*.74,.88);
                b.frame(at.add(0,.25,0),X,UP,.32,.36,FROST_HD_PLATE,(int)(clock),.82);
                chips(b,at,r,.5,FROST_HD_SHARD,clock,4,.83);
            }
            case BURST -> impact(b,p,at,r,clock);
            case ARC -> {
                Vec3 brace = at.add(forward.scale(.33));
                b.frame(brace,forward,UP,r*.71,r*.23,FROST_HD_SHIVER,(int)(clock),.91);
                b.frame(brace.add(side.scale(.024)),forward,side,r*.77,r*.32,FROST_HD_STREAM,(int)(clock+1),.90);
                chips(b,brace,r*.55,.60,FROST_HD_SHARD,clock,5,.93);
            }
            default -> {
                rim(b,at,r,FROST_HD_RIM,clock,.85);
                chips(b,at,r,.49,FROST_HD_SHARD,clock,4,.9);
            }
        }
        return b.result();
    }

    private static void seal(AbilityMeshBuilder b, AbilityVfxPayload p, Vec3 at, double r, double clock) {
        boolean warning = p.angle()==359;
        if (p.variant()==1) {
            chips(b,at,r*1.35,.55,FROST_HD_SHARD,clock,3,.71);
            b.frame(at.add(0,.031,0),X,Z,r,r,FROST_HD_PLATE,(int)(clock*.36),.68);
            return;
        }
        if (p.variant()==2) {
            rim(b,at,r,FROST_HD_FISSURE,clock*.82,.92);
            chips(b,at,r,.39,FROST_HD_SHARD,clock,4,.78);
            return;
        }
        double open = p.life()>25 ? Math.min(1,.8+b.progress*2.1) : .79+b.progress*.21;
        rim(b,at,r*open,FROST_HD_RIM,clock*.34,warning?.70:.94);
        if (p.variant()==6 || p.variant()==7 || warning) {
            double inset = p.variant()==6 ? .88 : .80;
            rim(b,at.add(0,.019,0),r*open*inset,FROST_HD_RIM,clock*.39+2,warning?.36:.47);
            if (!warning && p.variant()==7) {
                int count = b.reduced?4:7;
                for (int i=0;i<count;i++) {
                    double angle = i*Math.PI*2/count+.31;
                    Vec3 across = new Vec3(-Math.sin(angle),0,Math.cos(angle));
                    Vec3 foot = circle(at,r*(.55+.37*b.progress),angle,.27);
                    b.frame(foot,across,UP,.37+r*.032,.43,FROST_HD_PLATE,(int)(clock*.67+i),.82);
                }
            } else if (!warning && p.variant()==6) {
                chips(b,at,r*.94,.42,FROST_HD_SHARD,clock*.6,5,.69);
            }
        } else {
            rim(b,at.add(0,.028,0),r*.91,FROST_HD_FISSURE,clock*.52,.72);
        }
    }

    private static void shield(AbilityMeshBuilder b, int variant, Vec3 at, double r, double clock) {
        int count = b.reduced ? 3 : variant==7 ? 6 : 4;
        for (int i=0;i<count;i++) {
            double a = i * 2*Math.PI/count + clock*.043;
            Vec3 tangent = new Vec3(-Math.sin(a),0,Math.cos(a));
            Vec3 outward = new Vec3(Math.cos(a),0,Math.sin(a));
            Vec3 center = circle(at,r+.17,a,variant==1?.72:.84+.14*(i%2));
            double w = variant==7?.38:variant==1?.22:.30;
            Vec3 lean = UP.add(outward.scale(i%2==0?.18:-.11)).normalize();
            double h = variant==7?.65:variant==1?.38:.56;
            b.frame(center,tangent,lean,w,h,FROST_HD_PLATE,(int)(clock*.48+i),.94);
            b.frame(center.add(outward.scale(.031)).add(0,.051,0),lean,tangent,h*.87,w*1.13,
                    FROST_HD_SHIVER,(int)(clock*.73+i),.71);
            if (!b.reduced && variant!=1 && i%2==0)
                prism(b,center.add(outward.scale(.065)),lean,tangent,tangent.cross(lean),h*1.21,w*.29);
        }
        chips(b,at,r+.28,1.37,FROST_HD_SHARD,clock*.86,5,.79);
    }

    private static void eruption(AbilityMeshBuilder b, AbilityVfxPayload p, Vec3 at, Vec3 end, double clock) {
        double h = Math.max(.6,at.distanceTo(end));
        if (usesSpikeModel(p)) {
            boolean expanse=p.variant()==6;
            Vec3 flat=end.subtract(at).multiply(1,0,1);
            Vec3 heading=flat.lengthSqr()<1E-8?Z:flat.normalize();
            h=Math.max(.6,end.y-at.y);
            FrostHerbModels.spikes(b,at,heading,h,expanse);
            rim(b,at,expanse?1.86:1.47,FROST_HD_FISSURE,clock*.66,.95);
            if(!b.reduced) {
                Vec3 lateral=side(heading);
                ribbon(b,at.add(lateral.scale(-1.25)).add(0,.19,0),
                        at.add(lateral.scale(1.25)).add(0,.22,0),UP,.38,FROST_HD_MIST,clock,.75);
            }
        } else if (p.variant()==0) {
            b.frame(at.add(0,.079,0),X,Z,.82,.82,FROST_HD_FISSURE,(int)(clock),.90);
            b.frame(at.add(0,.44,0),X,UP,.47,.54,FROST_HD_PLATE,(int)(clock),.94);
            b.frame(at.add(0,.50,0),Z,UP,.52,.52,FROST_HD_SHIVER,(int)(clock+2),.93);
        } else {
            rim(b,at,Math.min(1.4,h*.36),FROST_HD_FISSURE,clock*.66,.95);
            int columns = b.reduced ? 2 : 4;
            for (int i=0;i<columns;i++) {
                double a = i*2.399963;
                double scale = i==0 ? 1 : .65+(i%2)*.13;
                Vec3 position = circle(at,i*.22,a,h*.36*scale);
                Vec3 lean = new Vec3(.21*Math.cos(a),1,.21*Math.sin(a)).normalize();
                Vec3 right = side(lean);
                prism(b,position,lean,right,right.cross(lean),h*.81*scale,.22*scale);
                b.frame(position,right,lean,.25*scale,h*.43*scale,FROST_HD_SHIVER,(int)(clock+i),.84);
            }
        }
        chips(b,at,.42+b.progress*.72,h*.83,FROST_HD_SHARD,clock,9,.86);
    }

    private static void impact(AbilityMeshBuilder b, AbilityVfxPayload p, Vec3 at, double r, double clock) {
        double reach = r*(.68+.24*b.progress);
        if (p.variant()==4 || p.variant()==7) {
            rim(b,at,reach,FROST_HD_FISSURE,clock*.93,.91);
            rim(b,at.add(0,.024,0),reach*1.08,FROST_HD_RIM,clock,.87);
        } else if (p.angle()>=201&&p.angle()<=203) {
            int count = (int)p.angle()-200;
            for (int i=0;i<count;i++) {
                Vec3 point = at.add((i-(count-1)*.5)*.14,.56,0);
                b.frame(point,X,UP,.11,.16,FROST_HD_SHARD,(int)(clock+i),.87);
            }
        } else {
            b.frame(at.add(0,.19,0),X,UP,.40+reach*.44,.27+reach*.26,FROST_HD_SHIVER,(int)(clock),.91);
        }
        chips(b,at,reach, p.variant()==4?1.77:.82,FROST_HD_SHARD,clock,5,.81);
    }

    /** Decorative prisms belong to the existing ground impacts, never the flight body. */
    private static void prism(AbilityMeshBuilder b, Vec3 center, Vec3 along, Vec3 right, Vec3 up, double length, double w) {
        Vec3 nose = center.add(along.scale(length*.58));
        Vec3 back = center.subtract(along.scale(length*.48));
        Vec3 shoulder = center.add(along.scale(length*.11));
        Vec3 heel = center.subtract(along.scale(length*.26));
        Vec3[] axes = {right,up,right.scale(-1),up.scale(-1)};
        for (int i=0;i<4;i++) {
            Vec3 a = shoulder.add(axes[i].scale(w)), c = shoulder.add(axes[(i+1)%4].scale(w));
            Vec3 d = heel.add(axes[i].scale(w*.68)), e = heel.add(axes[(i+1)%4].scale(w*.68));
            b.atlasTriangle(nose,a,c,FROST_HD_MODEL,i);
            b.atlasTriangle(a,d,c,FROST_HD_MODEL,(i+1)%4);
            b.atlasTriangle(c,d,e,FROST_HD_MODEL,(i+1)%4);
            b.atlasTriangle(d,back,e,FROST_HD_MODEL,(i+2)%4);
        }
    }

}
