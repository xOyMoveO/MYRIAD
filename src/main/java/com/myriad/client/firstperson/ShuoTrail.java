package com.myriad.client.firstperson;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.shuo.ShuoSlashMesh;
import com.myriad.network.ShuoViewPayload;
import java.util.List;
import net.minecraft.world.phys.Vec3;
/** Basic only; skills no longer reuse the old camera overlay. */
public final class ShuoTrail {
    private ShuoTrail(){}
    public static List<Quad> build(ShuoRig.Frame f,boolean mirror){
        if(!ShuoViewPayload.basicAction(f.action()))return List.of();double side=mirror?-1:1;
        if(f.action()==ShuoViewPayload.BASIC_RISE)return ShuoSlashMesh.build(new Vec3(-.04*side,-.32,-1.8),new Vec3(-.87*side,-.49,0),new Vec3(-.49*side,.87,-.10),1.12,.67,f.slashProgress(),false);
        if(f.action()==ShuoViewPayload.BASIC_SWEEP)return ShuoSlashMesh.build(new Vec3(.02*side,-.32,-1.8),new Vec3(-side,.06,0),new Vec3(.06*side,1,-.10),1.20,.48,f.slashProgress(),false);
        return ShuoSlashMesh.build(new Vec3(-.10*side,-.34,-1.8),new Vec3(side,-.24,0),new Vec3(.24*side,1,-.10),1.12,.67,f.slashProgress(),false);
    }
}
