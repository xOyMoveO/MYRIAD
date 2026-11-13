package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
/** Common presentation policy only; every candidate owns its own effects. */
abstract class KehengSkill extends AbilitySkill {
    KehengSkill(Definition d){super(d);}
    @Override public String displayName(){return net.minecraft.network.chat.Component.translatable("skill.myriad."+getId()+".name").getString();}
    @Override public String description(){return net.minecraft.network.chat.Component.translatable("skill.myriad."+getId()+".description").getString();}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(AbilityRuntime.Cast c){if(!isPassive())KehengAudio.play(c.player,"cast");}
}
