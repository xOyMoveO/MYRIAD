package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
import com.myriad.player.PlayerDataProvider;
import com.myriad.character.CharacterType;
import com.myriad.network.LuoxiaBowPosePayload.Phase;
import com.myriad.registry.ModAttributes;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Server-owned press/release state; no client-supplied charge, damage, or target. */
public final class LuoxiaBow {
    public static final int PRESS = -120, RELEASE = -121, CANCEL = -122;
    public static final int NORMAL_WORK = 11;
    private static final Identifier FULL_WALK = Identifier.fromNamespaceAndPath("myriad", "luoxia_full_draw");
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();
    private static final class State {
        ActionTime.Progress draw;
        net.minecraft.server.level.ServerLevel level;
        int gesture;
        long lastSent = Long.MIN_VALUE;
        long fastUntil;
        int sequence;
        boolean fast, full;
        double minimum = NORMAL_WORK, maximum = NORMAL_WORK;
    }
    private LuoxiaBow() {}

    public static boolean drawing(ServerPlayer p) {
        var state = STATES.get(p);
        return state != null && state.draw != null;
    }

    public static double drawRate(double currentAttackSpeed, double actionRate) {
        return Math.clamp(currentAttackSpeed / CharacterType.LUOXIA.initialAs(), .25, 4) * actionRate;
    }

    public static double minimumWork(int quickRank) {
        return quickRank > 0 ? ChaseDusk.drawTicks(quickRank) + 5 : NORMAL_WORK;
    }

    private static int passive(State state) {
        return state.fast ? 0 : state.maximum > state.minimum ? 1 : -1;
    }

    private static void drawPose(ServerPlayer p, State state) {
        LuoxiaBowPose.send(p, state.gesture, passive(state), Phase.DRAW, state.draw.completed(),
                state.minimum, state.maximum, p.getLookAngle());
        state.lastSent = p.level().getGameTime();
    }

    private static Cast presentation(ServerPlayer p) {
        return AbilityRuntime.context(p, FirstGroupSkills.node(LuoxiaRework.ID, 2));
    }

    private static boolean allowed(ServerPlayer p) {
        var data = PlayerDataProvider.get(p);
        return AbilityRuntime.valid(p) && LuoxiaRework.holdsBow(p) && BasicAttackSystem.canAttackNow(p, data)
                && data.getBuff("g1_stun") == 0 && !AbilityRuntime.channeling(p) && !sequence(p);
    }

    // Used by server-side callers which already own the input sequence.
    public static void input(ServerPlayer p, int action) {
        var state = STATES.get(p);
        int token = state == null ? 1 : state.gesture + (action == PRESS ? 1 : 0);
        input(p, action, token);
    }

    public static void input(ServerPlayer p, int action, int token) {
        if (token <= 0 || (action != PRESS && action != RELEASE && action != CANCEL)) return;
        if (!LuoxiaRework.ID.equals(PlayerDataProvider.get(p).getCharacterId())) return;
        State state = STATES.computeIfAbsent(p, ignored -> new State());
        long now = p.level().getGameTime();
        if (action == PRESS) {
            if (token <= state.gesture || state.draw != null) return;
            state.gesture = token;
            if (!allowed(p)) {
                var data = PlayerDataProvider.get(p);
                if (data.getBuff("g1_stun") > 0) com.myriad.combat.InputFailure.STUN.send(p);
                else if (AbilityRuntime.channeling(p) || sequence(p)) com.myriad.combat.InputFailure.CHANNEL.send(p);
                LuoxiaBowPose.send(p, token, -1, Phase.CANCEL, 0, 1, 1, p.getLookAngle());
                return;
            }
            int rank = LuoxiaRework.rank(p, 0);
            state.fast = rank > 0 && state.fastUntil > now;
            state.minimum = minimumWork(state.fast ? rank : 0);
            state.maximum = LuoxiaRework.rank(p, 1) > 0 ? SteadyDusk.FULL_TICKS : state.minimum;
            state.level = p.level();
            state.draw = new ActionTime.Progress(now);
            p.startUsingItem(InteractionHand.MAIN_HAND);
            drawPose(p, state);
            if (state.fast) LuoxiaVfx.sound(p, SoundEvents.ARROW_SHOOT, .23f, 1.62f);
            return;
        }
        if (token != state.gesture || state.draw == null) return;
        if (action == CANCEL || !allowed(p)) { cancelDraw(p); return; }
        tick(p);
        if (state.draw == null) return;
        double progress = state.draw.completed();
        boolean fast = state.fast;
        int steadyRank = LuoxiaRework.rank(p, 1);
        boolean full = steadyRank > 0 && progress >= SteadyDusk.FULL_TICKS;
        boolean early = progress + 1E-9 < state.minimum;
        stopDraw(p, state);
        if (early) {
            LuoxiaBowPose.send(p, token, passive(state), Phase.CANCEL, 0, 1, 1, p.getLookAngle());
            LuoxiaBowPose.send(p, token, passive(state), Phase.EARLY, 0, 1, 1, p.getLookAngle());
            return;
        }
        state.fastUntil = 0;
        LuoxiaBowPose.send(p, token, full ? 1 : fast ? 0 : -1, Phase.RELEASE, 1, 1, 1, p.getLookAngle());
        float multiplier = full ? SteadyDusk.multiplier(steadyRank) : fast ? ChaseDusk.MULTIPLIER : 1;
        float damage = (float)p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK) * multiplier;
        Cast c = presentation(p);
        LuoxiaVfx.shot(c, full, fast ? 1.38f : 1.05f);
        LuoxiaArrow.launch(c, p.getLookAngle(), full ? SteadyDusk.RANGE : 18, full ? 2.8 : 1.65,
                full ? LuoxiaArrow.Shape.FULL_DRAW : fast ? LuoxiaArrow.Shape.QUICK : LuoxiaArrow.Shape.BASIC,
                true, target -> {
                    DamageCalc.dealBasicDamage(p, target, damage, DamageCalc.Kind.PHYSICAL);
                    return true;
                });
        PlayerDataProvider.syncToClient(p);
    }

    public static void tick(ServerPlayer p) {
        State state = STATES.get(p);
        if (state == null) return;
        long now = p.level().getGameTime();
        if (state.draw == null) return;
        if (p.level() != state.level || !allowed(p)) { cancelDraw(p); return; }
        double rate = drawRate(p.getAttributeValue(ModAttributes.ATTACK_SPEED), ActionTime.multiplier(p.getUUID(), now));
        double previous = state.draw.completed();
        state.draw.advance(now, rate, state.maximum);
        if (now - state.lastSent >= 10 || now != state.lastSent && (now % 2 == 0 && state.draw.completed() != previous
                || previous < state.minimum && state.draw.completed() >= state.minimum
                || previous < state.maximum && state.draw.completed() >= state.maximum)) drawPose(p, state);
        if (state.draw.completed() >= SteadyDusk.FULL_TICKS && LuoxiaRework.rank(p, 1) > 0) {
            if (!state.full) {
                state.full = true;
                var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(FULL_WALK, -.20,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                LuoxiaVfx.sound(p, SoundEvents.ARROW_SHOOT, .32f, .70f);
            }
            if ((now & 1) == 0) LuoxiaVfx.charge(presentation(p), true);
        }
    }

    static void refreshChain(ServerPlayer p, long until) {
        STATES.computeIfAbsent(p, ignored -> new State()).fastUntil = until;
        LuoxiaBowPose.send(p, 0, 0, Phase.CHAIN, 0, 1, 1, p.getLookAngle());
    }

    static boolean sequence(ServerPlayer p) {
        State s = STATES.get(p);
        return s != null && s.sequence != 0;
    }

    static void volley(Cast c, int count, int spacing, int start, IntConsumer emit) {
        State s = STATES.computeIfAbsent(c.player, ignored -> new State());
        s.sequence = c.castId();
        int[] next = {0};
        if (start == 0) { emit.accept(0); next[0] = 1; }
        c.actionWhile(start + (count - 1) * spacing, progress -> {
            if (s.sequence != c.castId()) return false;
            if (LuoxiaSkill.interrupted(c)) {
                LuoxiaBowPose.skill(c, Phase.CANCEL, 0, c.player.getLookAngle());
                s.sequence = 0;
                return false;
            }
            int previous = next[0];
            while (next[0] < count && progress + 1E-9 >= start + next[0] * spacing) emit.accept(next[0]++);
            if (previous == next[0] && next[0] < count) {
                double pulled = 1 - (start + next[0] * spacing - progress) / spacing;
                if (pulled > 0) LuoxiaBowPose.skill(c, Phase.RELOAD, pulled, c.player.getLookAngle());
            }
            if (next[0] < count) return true;
            s.sequence = 0;
            return false;
        });
    }

    public static void cancelDraw(ServerPlayer p) {
        State state = STATES.get(p);
        if (state == null || state.draw == null) return;
        LuoxiaBowPose.send(p, state.gesture, passive(state), Phase.CANCEL, 0, 1, 1, p.getLookAngle());
        stopDraw(p, state);
    }

    private static void stopDraw(ServerPlayer p, State state) {
        state.draw = null;
        state.full = false;
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(FULL_WALK);
        if (p.isUsingItem() && p.getUseItem().is(com.myriad.registry.ModItems.GUIXIA_BOW.get())) p.stopUsingItem();
    }

    public static void clear(ServerPlayer p) {
        cancelDraw(p);
        State s = STATES.remove(p);
        if (s != null) {
            s.sequence = 0;
            LuoxiaBowPose.send(p, s.gesture, -1, Phase.RESET, 0, 1, 1, p.getLookAngle());
        }
    }
}
