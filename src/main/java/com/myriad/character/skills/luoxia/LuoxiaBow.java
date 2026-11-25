package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
import com.myriad.player.PlayerDataProvider;
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
    private static final int RECOVERY = 5;
    private static final Identifier FULL_WALK = Identifier.fromNamespaceAndPath("myriad", "luoxia_full_draw");
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();
    private static final class State {
        ActionTime.Progress draw, recovery;
        long fastUntil;
        int sequence;
        boolean fast, full;
        double minimum = 6;
    }
    private LuoxiaBow() {}

    public static int cooldownTotal() { return RECOVERY; }
    public static int remaining(ServerPlayer player) {
        State state = STATES.get(player);
        return state == null || state.recovery == null ? 0 : (int)Math.ceil(RECOVERY - state.recovery.completed());
    }

    private static Cast presentation(ServerPlayer p) {
        return AbilityRuntime.context(p, FirstGroupSkills.node(LuoxiaRework.ID, 2));
    }

    private static boolean allowed(ServerPlayer p) {
        var data = PlayerDataProvider.get(p);
        return AbilityRuntime.valid(p) && LuoxiaRework.holdsBow(p) && BasicAttackSystem.canAttackNow(p, data)
                && data.getBuff("g1_stun") == 0 && !AbilityRuntime.channeling(p) && !sequence(p);
    }

    public static void input(ServerPlayer p, int action) {
        if (action == CANCEL) { cancelDraw(p); return; }
        if (action != PRESS && action != RELEASE) return;
        if (!allowed(p)) { cancelDraw(p); return; }
        tick(p);
        long now = p.level().getGameTime();
        State state = STATES.computeIfAbsent(p, ignored -> new State());
        if (action == PRESS) {
            if (state.draw != null || state.recovery != null) return;
            int rank = LuoxiaRework.rank(p, 0);
            state.fast = rank > 0 && state.fastUntil > now;
            state.minimum = state.fast ? ChaseDusk.drawTicks(rank) : 6;
            state.draw = new ActionTime.Progress(now);
            p.startUsingItem(InteractionHand.MAIN_HAND);
            if (state.fast) LuoxiaVfx.sound(p, SoundEvents.ARROW_SHOOT, .23f, 1.62f);
            return;
        }
        if (state.draw == null) return;
        double progress = state.draw.completed();
        boolean fast = state.fast;
        int steadyRank = LuoxiaRework.rank(p, 1);
        boolean full = steadyRank > 0 && progress >= SteadyDusk.FULL_TICKS;
        cancelDraw(p);
        if (progress + 1E-9 < state.minimum) return;
        state.fastUntil = 0;
        state.recovery = new ActionTime.Progress(now);
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
        double rate = ActionTime.multiplier(p.getUUID(), now);
        if (state.recovery != null && state.recovery.advance(now, rate, RECOVERY)) {
            state.recovery = null;
            PlayerDataProvider.syncToClient(p);
        }
        if (state.draw == null) return;
        if (!allowed(p)) { cancelDraw(p); return; }
        state.draw.advance(now, rate, SteadyDusk.FULL_TICKS);
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
            if (s.sequence != c.castId() || LuoxiaSkill.interrupted(c)) { s.sequence = 0; return false; }
            while (next[0] < count && progress + 1E-9 >= start + next[0] * spacing) emit.accept(next[0]++);
            if (next[0] < count) return true;
            s.sequence = 0;
            return false;
        });
    }

    public static void cancelDraw(ServerPlayer p) {
        State state = STATES.get(p);
        if (state == null || state.draw == null) return;
        state.draw = null;
        state.full = false;
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(FULL_WALK);
        if (p.isUsingItem() && p.getUseItem().is(com.myriad.registry.ModItems.GUIXIA_BOW.get())) p.stopUsingItem();
    }

    public static void clear(ServerPlayer p) {
        cancelDraw(p);
        State s = STATES.remove(p);
        if (s != null) s.sequence = 0;
    }
}
