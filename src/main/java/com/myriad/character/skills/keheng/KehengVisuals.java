package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
/** State-confirmed recipes; the shared distributor retains visibility filtering. */
public final class KehengVisuals {
    private KehengVisuals(){}
    public static void warning(AbilityRuntime.Cast c,Vec3 at,double radius){AbilityVisuals.emit(c,SEAL,at.add(0,.04,0),at.add(0,1,0),radius,359,5,false);}
    public static void field(AbilityRuntime.Cast c,Vec3 at,double radius,boolean moving){AbilityVisuals.emit(c,SEAL,at.add(0,.04,0),at.add(0,1,0),radius,moving?1:0,5,false);}
    public static void corridor(AbilityRuntime.Cast c,Vec3 at,Vec3 end){AbilityVisuals.emit(c,CHARGE,at.add(0,.04,0),end.add(0,.04,0),1.5,0,5,false);}
    public static void penetrate(AbilityRuntime.Cast c,Vec3 from,Vec3 to){AbilityVisuals.emit(c,BEAM,from,to,1.5,0,7,false);}
    public static void impact(AbilityRuntime.Cast c,Vec3 at){AbilityVisuals.emit(c,BURST,at,at.add(c.forward(1)),.55,0,5,false);}
    public static void mark(AbilityRuntime.Cast c,LivingEntity target,int stacks){
        if(stacks==3)AbilityVisuals.following(c,BURST,target,.65,3,5);
        else AbilityVisuals.following(c,SEAL,target,.38,stacks,5);
    }
    public static void pulse(AbilityRuntime.Cast c,Vec3 at,double radius){AbilityVisuals.emit(c,ARC,at.add(0,.15,0),at.add(0,1,0),radius,0,5,false);}
    public static void end(AbilityRuntime.Cast c,Vec3 at,double radius){AbilityVisuals.emit(c,BURST,at.add(0,.1,0),at.add(0,1,0),radius,1,6,false);}
}
