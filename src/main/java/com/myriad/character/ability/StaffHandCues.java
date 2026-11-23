package com.myriad.character.ability;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.Packets;
import com.myriad.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Owner-only viewmodel events carried on the existing visual channel. */
public final class StaffHandCues {
    public static final int FROST = 6, HERB = 7;
    public static final int AIM = 271, HOLD = 272, STOP = 273, CAST = 274, SELF = 275, BASIC = 276;

    private StaffHandCues() {}

    public static boolean staffTheme(int theme) {
        return theme == FROST || theme == HERB;
    }

    public static boolean owns(AbilityVfxPayload p) {
        if (!p.personal() || !staffTheme(p.theme()) || p.kind() != AbilityVfxGeometry.CHARGE
                || p.anchor() != -1 || p.angle() != (int) p.angle()) return false;
        if (p.angle() == BASIC) return p.variant() == 0;
        return p.variant() >= 2 && p.variant() <= 7 && p.angle() >= AIM && p.angle() <= SELF;
    }

    public static void select(Cast c, boolean active) {
        send(c, active ? AIM : STOP, 0, 7);
    }

    public static void charge(Cast c, boolean active) {
        if (!active || c.windupProgress() == 0 || c.now() % 2 == 0)
            send(c, active ? HOLD : STOP, c.windupProgress(), 6);
    }

    public static void release(Cast c) { release(c, false); }

    public static void release(Cast c, boolean self) {
        send(c, self ? SELF : CAST, 0, 12);
    }

    public static void volley(Cast c, int shot) {
        send(c, CAST, shot, 7);
    }

    private static void send(Cast c, int signal, double value, int life) {
        var pos = c.player.position();
        AbilityVisuals.emit(c, AbilityVfxGeometry.CHARGE, pos, pos, value, signal, life, true);
    }

    public static void basic(ServerPlayer player, ItemStack held, String role, Vec3 origin, Vec3 direction) {
        int theme;
        if ("hanze".equals(role) && held.is(ModItems.SHUANGBING.get())) theme = FROST;
        else if ("qingluo".equals(role) && held.is(ModItems.QINGLUO_STAFF.get())) theme = HERB;
        else return;
        Packets.sendToPlayer(player, new AbilityVfxPayload(player.getId(), -1, theme, 0,
                AbilityVfxGeometry.CHARGE, origin, origin.add(direction), 0, BASIC, 9,
                player.level().getGameTime(), true));
    }
}
