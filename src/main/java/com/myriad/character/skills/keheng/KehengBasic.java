package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
import net.minecraft.server.level.ServerPlayer;
/** Visual-only thin blade contact, vanilla still picks exactly one melee target. */
public final class KehengBasic {
    private KehengBasic(){}
    public static void swing(ServerPlayer p){
        if(!"keheng".equals(com.myriad.player.PlayerDataProvider.get(p).getCharacterId()))return;
        var c=AbilityRuntime.context(p,com.myriad.character.rework.FirstGroupSkills.node("keheng",2));
        AbilityVisuals.emit(c,AbilityVfxGeometry.BEAM,p.getEyePosition().add(c.forward(.3)),p.getEyePosition().add(c.forward(1.1)),.03,0,3,false);
        KehengAudio.play(p,"cast");
    }
}
