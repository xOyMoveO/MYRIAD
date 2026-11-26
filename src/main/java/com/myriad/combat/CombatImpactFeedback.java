package com.myriad.combat;

import com.myriad.character.CharacterType;
import com.myriad.game.ModState;
import com.myriad.network.UiFeedbackPayload;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import com.myriad.network.Packets;

/** Called only after measured HP/shield loss; never broadcasts a hidden target's location. */
public final class CombatImpactFeedback {
    private CombatImpactFeedback(){}
    public static void confirmed(ServerPlayer attacker,LivingEntity target,int damageType,boolean critical,
                                 boolean periodic,float effective,boolean shield) {
        confirmed(attacker, target, damageType, critical, periodic, effective, shield, "");
    }

    public static void confirmed(ServerPlayer attacker,LivingEntity target,int damageType,boolean critical,
                                 boolean periodic,float effective,boolean shield,String abilitySource){
        if(!ModState.isEnabled()||!Float.isFinite(effective)||effective<=0||target==attacker
                ||!SkillTargets.hostile(attacker,target))return;
        var role=CharacterType.byId(PlayerDataProvider.get(attacker).getCharacterId());
        if(role==null)return;
        // Role-owned visuals have their own per-target budget, independent of the shared audio cap.
        com.myriad.character.skills.fenjue.rework.FlameHitVisuals.confirmed(attacker,target,critical,periodic,effective);
        if (com.myriad.character.skills.yingxiao.rework.NightAudio.ownsImpact(abilitySource)) {
            if (shield) Packets.sendToPlayer(attacker, new UiFeedbackPayload(critical ? 6 : 5));
            return;
        }
        var state=com.myriad.player.EntityState.data(attacker);long now=attacker.level().getGameTime();
        long last=state.getLongOr("myriad_impact_audio_at",Long.MIN_VALUE);
        boolean wasCritical=state.getBooleanOr("myriad_impact_audio_crit",false);
        if(!CombatImpactProfile.allow(now,last,wasCritical,critical,periodic))return;
        var profile=CombatImpactProfile.choose(role,damageType,critical,
                target instanceof com.myriad.game.DefenseTowerEntity,shield);
        state.putLong("myriad_impact_audio_at",now);state.putBoolean("myriad_impact_audio_crit",critical);
        Packets.sendToPlayer(attacker,new UiFeedbackPayload(profile.payload(critical)));
        if(shield)Packets.sendToPlayer(attacker,new UiFeedbackPayload(critical?6:5));
    }
}
