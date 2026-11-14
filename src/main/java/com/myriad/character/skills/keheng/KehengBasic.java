package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
import net.minecraft.server.level.ServerPlayer;
/** Visual-only thin blade contact, vanilla still picks exactly one melee target. */
public final class KehengBasic {
    private KehengBasic(){}
    public static void contact(ServerPlayer p,net.minecraft.world.entity.LivingEntity target){
        if(!"keheng".equals(com.myriad.player.PlayerDataProvider.get(p).getCharacterId()))return;
        var c=AbilityRuntime.context(p,com.myriad.character.rework.FirstGroupSkills.node("keheng",2));
        var at=target.getBoundingBox().getCenter();
        AbilityVisuals.emit(c,AbilityVfxGeometry.BURST,at,at.add(p.getLookAngle()),.3,KehengVfx.BASIC,3,false);
    }
    public static void swing(ServerPlayer p){
        if(!"keheng".equals(com.myriad.player.PlayerDataProvider.get(p).getCharacterId()))return;
        var c=AbilityRuntime.context(p,com.myriad.character.rework.FirstGroupSkills.node("keheng",2));
        AbilityVisuals.emit(c,AbilityVfxGeometry.BEAM,p.getEyePosition().add(p.getLookAngle().scale(.3)),p.getEyePosition().add(p.getLookAngle().scale(1.1)),.03,KehengVfx.BASIC,3,false);
        KehengAudio.play(p,"cast");
    }
}
