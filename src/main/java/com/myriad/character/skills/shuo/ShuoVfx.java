package com.myriad.character.skills.shuo;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityVfxGeometry.ARC;
/** Basic-only marker separates the new effect from every retired skill recipe. */
public final class ShuoVfx {
    public static final int BASIC=300;
    private ShuoVfx(){}
    public static boolean owns(AbilityVfxPayload p){return p.valid()&&p.theme()==10&&p.kind()==ARC&&p.angle()==BASIC&&p.variant()<4;}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(ShuoSkillVfx.owns(p))return ShuoSkillVfx.build(p,age,low);
        if(!owns(p)||!Float.isFinite(age)||age<0||age>=p.life())return List.of();
        float progress=p.variant()>=2?(age/9f-.18f)/.52f:(age-(p.variant()==1?3:0))/(p.life()-(p.variant()==1?3:0));
        var f=p.to().subtract(p.from()).normalize();if(f.lengthSqr()<1E-8)f=new Vec3(0,0,1);
        var side=f.cross(new Vec3(0,1,0));if(side.lengthSqr()<1E-8)side=new Vec3(1,0,0);side=side.normalize();
        var up=side.cross(f).normalize();
        var right=side.scale(.94).subtract(up.scale(.34));
        var vertical=side.scale(.34).add(up.scale(.94)).add(f.scale(.12));
        if(p.variant()==2){right=side.scale(-.87).subtract(up.scale(.49));vertical=side.scale(-.49).add(up.scale(.87)).add(f.scale(.12));}
        if(p.variant()==3){right=side.scale(-1).add(up.scale(.06));vertical=side.scale(.06).add(up).add(f.scale(.12));}
        return ShuoSlashMesh.build(p.from(),right,vertical,1.55,.88,progress,low);
    }
}
