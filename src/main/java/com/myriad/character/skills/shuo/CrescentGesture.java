package com.myriad.character.skills.shuo;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import com.myriad.network.CrescentInputPayload.Edge;

/** The accepted recast captures its own press. A later edge cannot change that cut. */
public final class CrescentGesture {
    private static final WeakHashMap<ServerPlayer, CrescentGesture> latest = new WeakHashMap<>();
    private record Buffered(AbilityRuntime.Cast cut, CrescentGesture press) {}
    private static final WeakHashMap<ServerPlayer, Buffered> buffered = new WeakHashMap<>();
    private final int sequence;
    private final long pressed;
    private Edge intent = Edge.PRESS;
    private static final WeakHashMap<ServerPlayer,CrescentGesture> waiting = new WeakHashMap<>();
    private boolean accepting;

    private CrescentGesture(int sequence, long pressed) {
        this.sequence = sequence;
        this.pressed = pressed;
    }
    public static void input(ServerPlayer player, int sequence, Edge edge) {
        var prior = latest.get(player);
        long now = player.level().getGameTime();
        if (edge != Edge.PRESS) {
            if (prior != null && prior.sequence == sequence && prior.intent==Edge.PRESS) prior.intent=edge;
            return;
        }
        var data = PlayerDataProvider.get(player);
        if (sequence <= 0 || prior != null && sequence <= prior.sequence || !"shuo".equals(data.getCharacterId())
                || !(FirstGroupSkills.resolve(CharacterType.SHUO, CharacterType.SLOT_R, data.getSkillLevels()) instanceof CanYueSkill)) return;
        var press = new CrescentGesture(sequence, now);
        latest.put(player, press);
        var cut = AbilityRuntime.winding(player);
        if (cut != null && cut.skill instanceof CanYueSkill && cut.memory().tag.equals("sheathed")
                && cut.memory().count <= 2 && !AbilityRuntime.controlled(player)
                && 4 * (1 - cut.windupProgress()) <= 3 * ActionTime.multiplier(player.getUUID(), now)) {
            buffered.putIfAbsent(player, new Buffered(cut, press));
            return;
        }
        press.cast(player);
    }
    private void cast(ServerPlayer player) {
        accepting = true;
        try { SkillSystem.cast(player, CharacterType.SLOT_R); }
        finally { accepting = false; }
    }
    static CrescentGesture accepted(ServerPlayer player) {
        var press = latest.get(player);
        return press != null && press.accepting ? press : null;
    }
    static boolean waiting(ServerPlayer player) {return waiting.containsKey(player);}
    void decide(AbilityRuntime.Cast cut, Consumer<Boolean> release) {
        if(intent!=Edge.PRESS){release.accept(intent==Edge.HOLD && cut.now()-pressed>=3);return;}
        // Only an outstanding edge can delay a prepared cut. Do not infer a long press
        // from packet silence or start a second windup when the gesture finally arrives.
        waiting.put(cut.player,this);
        cut.whileActive(6,age->{
            if(waiting.get(cut.player)!=this)return false;
            if(AbilityRuntime.controlled(cut.player)){
                waiting.remove(cut.player);discard(cut.player);
                CanYueSkill.display(cut,-1,0);
                ShuoCombat.cue(cut.player,com.myriad.network.ShuoViewPayload.CANCEL,0);
                return false;
            }
            if(intent==Edge.PRESS && age<6)return true;
            waiting.remove(cut.player);
            release.accept(intent==Edge.HOLD && cut.now()-pressed>=3);
            return false;
        });
    }
    static void consume(AbilityRuntime.Cast cut) {
        var pending = buffered.remove(cut.player);
        if (pending == null || pending.cut != cut || cut.now() - pending.press.pressed > 3
                || !cut.skill.recastAvailable(cut) || AbilityRuntime.controlled(cut.player)) return;
        var current = latest.put(cut.player, pending.press);
        try { pending.press.cast(cut.player); }
        finally { latest.put(cut.player, current); }
    }
    static void discard(ServerPlayer player) { buffered.remove(player); }
    static void clear(ServerPlayer player) { latest.remove(player); waiting.remove(player); discard(player); }
}
