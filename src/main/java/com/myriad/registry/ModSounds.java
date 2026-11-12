package com.myriad.registry;

import com.myriad.Myriad;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 模组技能音效注册表。 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Myriad.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HANZE_PASSIVE_PROC = register("hanze_passive_proc");
    public static final DeferredHolder<SoundEvent, SoundEvent> HANZE_ICE_SPIKE_CAST = register("hanze_ice_spike_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> HANZE_FROST_SHIELD_CAST = register("hanze_frost_shield_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> HANZE_ABSOLUTE_ZERO_CHARGE = register("hanze_absolute_zero_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> HANZE_ABSOLUTE_ZERO_BURST = register("hanze_absolute_zero_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> HANZE_FROZEN_EXPANSE_CAST = register("hanze_frozen_expanse_cast");

    public static final DeferredHolder<SoundEvent, SoundEvent> UI_LEVEL_UP = register("ui_level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_PURCHASE = register("ui_purchase");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_SWING = register("combat_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINGYUAN_HEAVEN_SWEEP = register("lingyuan_heaven_sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIANXING_CHARGE = register("skill_tianxing_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIANXING_CAST = register("skill_tianxing_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIANXING_HIT = register("skill_tianxing_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIANXING_GUARD = register("skill_tianxing_guard");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIANXING_PULSE = register("skill_tianxing_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIANXING_JUDGMENT = register("skill_tianxing_judgment");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_SLASH_BLADE = register("skill_fenjue_slash_blade");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_SLASH_FIRE = register("skill_fenjue_slash_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_STEP_WIND = register("skill_fenjue_step_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_STEP_SLASH = register("skill_fenjue_step_slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_CAGE_CAST = register("skill_fenjue_cage_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_CAGE_OPEN = register("skill_fenjue_cage_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_BURST_CHARGE = register("skill_fenjue_burst_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_BURST_RELEASE = register("skill_fenjue_burst_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_SEA_JUMP = register("skill_fenjue_sea_jump");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_SEA_IMPACT = register("skill_fenjue_sea_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> FENJUE_ARMOR_IGNITE = register("skill_fenjue_armor_ignite");
    private static final java.util.Map<com.myriad.combat.CombatImpactProfile,DeferredHolder<SoundEvent,SoundEvent>> IMPACTS =
            new java.util.EnumMap<>(com.myriad.combat.CombatImpactProfile.class);
    static {
        for(var profile:com.myriad.combat.CombatImpactProfile.values())
            IMPACTS.put(profile,register("combat_impact_"+profile.id));
    }
    public static SoundEvent impact(com.myriad.combat.CombatImpactProfile profile){return IMPACTS.get(profile).get();}
    public static final DeferredHolder<SoundEvent, SoundEvent> KILL_SINGLE = register("announcement_kill_single");
    public static final DeferredHolder<SoundEvent, SoundEvent> KILL_DOUBLE = register("announcement_kill_double");
    public static final DeferredHolder<SoundEvent, SoundEvent> KILL_TRIPLE = register("announcement_kill_triple");
    public static final DeferredHolder<SoundEvent, SoundEvent> KILL_QUADRA = register("announcement_kill_quadra");
    public static final DeferredHolder<SoundEvent, SoundEvent> KILL_PENTA = register("announcement_kill_penta");

    private ModSounds() { }
    private static final java.util.Map<String,DeferredHolder<SoundEvent,SoundEvent>> SHUO=new java.util.HashMap<>();
    static {for(String cue:new String[]{"draw","sheathe","slash","parry","charge"})SHUO.put(cue,register("shuo_"+cue));}
    public static SoundEvent shuo(String cue){return SHUO.get(cue).get();}
    private static final java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> FOLEY = new java.util.HashMap<>();
    static {
        for (String family : new String[]{"blade", "wind", "fire", "ice", "water", "wood", "stone", "shot", "thunder"})
            FOLEY.put(family, register("foley_" + family));
    }
    public static SoundEvent foley(String family) { return FOLEY.get(family).get(); }
    private static final java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> MINGZHU = new java.util.HashMap<>();
    static {
        for (String action : new String[]{"orb_cast", "orb_hit", "mirror_place", "mirror_turn", "dash", "beam_start", "beam_body", "beam_end"})
            MINGZHU.put(action, register("mingzhu_" + action));
    }
    public static SoundEvent mingzhu(String action) { return MINGZHU.get(action).get(); }

    public static final DeferredHolder<SoundEvent, SoundEvent> ZHECHUAN_THROW = register("zhechuan_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZHECHUAN_OUTBOUND = register("zhechuan_outbound");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZHECHUAN_RETURN = register("zhechuan_return");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZHECHUAN_PALINDROME = register("zhechuan_palindrome");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZHECHUAN_CORRIDOR = register("zhechuan_corridor");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZHECHUAN_FINISHER = register("zhechuan_finisher");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String id) {
        return SOUND_EVENTS.register(id, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(Myriad.MODID, id)));
    }
}
