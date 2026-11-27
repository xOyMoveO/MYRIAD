package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;

import static com.myriad.character.skills.sulan.rework.WindCastAnimation.*;

/** Sulan's hand-space keyframes. Gameplay continues to use the existing server and vanilla timing. */
final class WindHandMotion {
    record Pose(Vec3 grip, Vec3 shaft, Vec3 free, double palmUp, double point) {
        Pose mix(Pose next, double t) {
            return new Pose(grip.lerp(next.grip, t), shaft.lerp(next.shaft, t), free.lerp(next.free, t),
                    palmUp + (next.palmUp - palmUp) * t, point + (next.point - point) * t);
        }
    }
    private static final Vec3 HIDDEN = new Vec3(-.65, -1.27, -.9);
    static final Pose IDLE = pose(.477, -.451, -.937, .54, .81, -.20);
    private static final Pose DOWN_RIGHT = pose(.49, -.51, -1.12, .28, -.41, -.87);
    private static final Pose CUT_1_READY = pose(.60, -.30, -1.32, .44, .43, -.82);
    private static final Pose CUT_1_END = pose(-.30, -.55, -1.45, -.53, -.34, -.79);
    private static final Pose CUT_2_READY = pose(-.33, -.56, -1.38, -.58, -.38, -.78);
    private static final Pose CUT_2_END = pose(.49, -.32, -1.43, .49, .43, -.79);
    private static final Pose CUT_3_READY = free(pose(.63, -.36, -1.33, .81, .02, -.59), -.66, -.55, -1.0, 0, 0);
    private static final Pose CUT_3_END = free(pose(-.38, -.40, -1.46, -.80, -.03, -.60), -.77, -.57, -1.04, 0, 0);
    private static final Pose[] CUT_START = {CUT_1_READY, CUT_2_READY, CUT_3_READY};
    private static final Pose[] CUT_END = {CUT_1_END, CUT_2_END, CUT_3_END};
    private static final Pose[] CUT_CONTACT = {
            pose(.09, -.40, -1.58, .02, -.08, -1),
            pose(.07, -.45, -1.58, .03, -.07, -1),
            free(pose(.09, -.38, -1.62, .02, .01, -1), -.71, -.55, -1.03, 0, 0)
    };
    private static final Pose THRUST_READY = pose(.62, -.55, -1.0, .03, .08, -.99);
    private static final Pose THRUST = pose(.21, -.34, -1.68, -.07, .08, -.99);
    private static final Pose THRUST_LOW = pose(.28, -.44, -1.53, -.06, -.17, -.98);
    private static final Pose MARK_READY = free(DOWN_RIGHT, -.49, -.50, -1.09, 0, .3);
    private static final Pose MARK = free(DOWN_RIGHT, -.28, -.27, -1.62, 0, 1);
    private static final Pose WALL_READY = free(pose(.42, -.59, -1.16, -.12, -.49, -.86), -.54, -.58, -1.12, 1, 0);
    private static final Pose WALL_UP = free(pose(.53, -.27, -1.45, .35, .55, -.77), -.36, -.36, -1.44, 1, 0);
    private static final Pose WALL_END = free(pose(.65, -.31, -1.31, .52, .57, -.64), -.48, -.41, -1.24, 1, 0);
    private static final Pose THROW_READY = free(pose(.67, -.37, -1.21, .85, .06, -.52), -.67, -.54, -1.02, 0, 0);
    private static final Pose THROW_END = free(pose(-.32, -.45, -1.49, -.71, -.11, -.69), -.70, -.63, -1.03, 0, 0);
    private static final Pose CHARGE_READY = free(pose(.57, -.54, -1.19, .49, .05, -.87), -.42, -.52, -1.24, .35, 0);
    private static final Pose CHARGE = free(pose(.69, -.67, -1.06, .48, -.15, -.86), -.34, -.55, -1.48, .45, 0);
    private static final Pose DASH = free(pose(.58, -.53, -1.31, .17, .05, -.98), -.57, -.61, -1.1, 0, 0);
    private static final Pose DASH_END = free(pose(.71, -.43, -1.26, .79, .18, -.59), -.68, -.47, -1.25, 0, 0);
    private static final Pose DOMAIN_READY = free(pose(.36, -.55, -1.27, -.85, -.05, -.52), -.23, -.46, -1.29, .75, 0);
    private static final Pose DOMAIN_OPEN = free(pose(.69, -.37, -1.36, .88, .14, -.45), -.70, -.36, -1.44, .45, 0);

    static final double CUT_FROM = .36, HIT = .98, CUT_TO = 2.65;
    private static final int PREPARE = -1;
    private static final double SETTLE = 4;
    private record Gesture(int ability, int signal, double start, double end, Pose entry,
                           double fromProgress, double progress, Vec3 aim) {}
    private Gesture skill;
    private int combo = -1;
    private double attack = Double.NEGATIVE_INFINITY;
    private double recovery;
    private double chargeBegan;
    private Pose attackEntry = IDLE;
    private double rollStart = Double.NEGATIVE_INFINITY, canceledRoll;

    void reset() {
        skill = null;
        combo = -1;
        attack = Double.NEGATIVE_INFINITY;
        attackEntry = IDLE;
        rollStart = Double.NEGATIVE_INFINITY;
    }

    void attack(double now, double delay) {
        if (now == attack || hasSkill(now) && skill.signal != CANCEL) return;
        attackEntry = poseAt(now);
        skill = null;
        combo = now - attack > recovery + 23 ? 0 : (combo + 1) % CUT_START.length;
        attack = now;
        // Leave a little follow-through when the next recovered click arrives at the expected cadence.
        recovery = Math.clamp(delay + 2.5, 5.5, 20);
    }

    void prepare(int ability, double now) {
        if (hasSkill(now) && skill.signal != CANCEL) return;
        var ready = ready(ability);
        if (ready == null) return;
        skill = new Gesture(ability, PREPARE, now, now + 5, poseAt(now), 0, 0, Vec3.ZERO);
        combo = -1;
    }

    void cue(int ability, int signal, double now, int life, double progress, Vec3 aim) {
        if (signal == CANCEL) {
            if (hasSkill(now) && skill.ability == ability && skill.signal != CANCEL) settle(now, ability);
            return;
        }
        if (signal == WINDUP && hasSkill(now) && skill.ability == ability && skill.signal == WINDUP) {
            skill = new Gesture(ability, signal, now, now + life, skill.entry, chargeProgress(now), progress, aim);
        } else if (signal == DASH_HOLD && hasSkill(now) && skill.ability == ability && inMotion(skill.signal)) {
            skill = new Gesture(ability, DASH_START, skill.start, now + life, skill.entry, 0, 0, aim);
        } else {
            Pose entry = poseAt(now);
            if (signal == CAST && !settling(now) && (!hasSkill(now) || skill.signal != PREPARE || skill.ability != ability))
                entry = entry.mix(ready(ability), .66);
            if (signal == DASH_HOLD) {
                if (!settling(now)) entry = ability == 2 ? THRUST : DASH;
                signal = DASH_START;
            }
            int duration = signal == CAST || signal == DASH_STOP ? 9 : life;
            if (signal == WINDUP) chargeBegan = now;
            skill = new Gesture(ability, signal, now, now + duration, entry, 0, progress, aim);
        }
        combo = -1;
    }

    void interrupt(double now) {
        if (hasSkill(now)) {
            if (skill.signal != CANCEL) settle(now, skill.ability);
        } else if (combo >= 0 && now >= attack && now < attack + recovery) {
            settle(now, -1);
        } else {
            combo = -1;
        }
    }

    private void settle(double now, int ability) {
        Pose entry = poseAt(now);
        canceledRoll = WindHandRig.rollOffset(this, now);
        rollStart = now;
        skill = new Gesture(ability, CANCEL, now, now + SETTLE, entry, 0, 0, Vec3.ZERO);
        combo = -1;
    }

    double returningRoll(double now) {
        return now >= rollStart ? canceledRoll * (1 - smooth((now - rollStart) / SETTLE)) : 0;
    }

    private boolean settling(double now) {
        return hasSkill(now) && skill.signal == CANCEL || expiredHold(now);
    }

    private boolean expiredHold(double now) {
        return skill != null && now >= skill.end && now < skill.end + SETTLE
                && (skill.signal == WINDUP || inMotion(skill.signal));
    }

    boolean hasSkill(double now) { return skill != null && now >= skill.start && now < skill.end; }
    int ability(double now) { return hasSkill(now) ? skill.ability : -1; }
    int signal(double now) { return hasSkill(now) ? skill.signal : PREPARE; }
    double skillStart() { return skill.start; }
    double attackStart() { return attack; }
    int combo() { return combo; }
    double recovery() { return recovery; }

    double chargeProgress(double now) {
        if (!hasSkill(now) || skill.signal != WINDUP) return 0;
        return sampledProgress(now);
    }

    private double sampledProgress(double now) {
        double blend = Math.clamp((now - skill.start) * .5, 0, 1);
        return skill.fromProgress + (skill.progress - skill.fromProgress) * blend;
    }

    Pose poseAt(double now) {
        if (hasSkill(now)) return skillPose(now);
        if (expiredHold(now)) return move(skillPose(skill.end), IDLE, now - skill.end, SETTLE);
        if (combo < 0 || now < attack || now - attack >= recovery) return IDLE;
        double age = now - attack;
        if (age < CUT_FROM) return move(attackEntry, CUT_START[combo], age, CUT_FROM);
        if (age < CUT_TO) return swing(combo, age);
        return move(CUT_END[combo], IDLE, age - CUT_TO, recovery - CUT_TO);
    }

    private Pose skillPose(double now) {
        double age = now - skill.start;
        if (skill.signal == PREPARE) {
            Pose prep = move(skill.entry, ready(skill.ability), age, 1.4);
            return prep.mix(IDLE, smooth((age - 2.5) / 2.5));
        }
        if (skill.signal == CANCEL) return move(skill.entry, IDLE, age, SETTLE);
        if (skill.signal == WINDUP)
            return move(skill.entry, CHARGE_READY.mix(CHARGE, smooth(sampledProgress(now))), now - chargeBegan, 1.8);
        if (inMotion(skill.signal)) return move(skill.entry, skill.ability == 2 ? THRUST : DASH, age, 1.65);
        if (skill.signal == DASH_STOP)
            return gesture(skill.ability == 2 ? THRUST_LOW : DASH_END, IDLE, age, 1.2, 2.8);
        return switch (skill.ability) {
            case 3 -> {
                Pose aimed = new Pose(MARK.grip, MARK.shaft, MARK.free.add(skill.aim), 0, 1);
                yield gesture(aimed, aimed, age, 1.0, 3.2);
            }
            case 4 -> gesture(WALL_UP, WALL_END, age, 1.25, 4.3);
            case 5 -> gesture(THROW_END, THROW_END, age, 1.15, 2.8);
            case 7 -> gesture(DOMAIN_OPEN, DOMAIN_OPEN, age, 1.15, 3.4);
            default -> IDLE;
        };
    }

    private Pose gesture(Pose accent, Pose hold, double age, double strike, double settle) {
        if (age < strike) return cut(skill.entry, accent, age / strike);
        if (age < settle) return move(accent, hold, age - strike, settle - strike);
        return move(hold, IDLE, age - settle, 9 - settle);
    }

    private static Pose ready(int ability) {
        return switch (ability) {
            case 2 -> THRUST_READY;
            case 3 -> MARK_READY;
            case 4 -> WALL_READY;
            case 5 -> THROW_READY;
            case 7 -> DOMAIN_READY;
            default -> null;
        };
    }

    private static Pose pose(double x, double y, double z, double sx, double sy, double sz) {
        return new Pose(new Vec3(x, y, z), new Vec3(sx, sy, sz), HIDDEN, 0, 0);
    }

    private static Pose free(Pose pose, double x, double y, double z, double palmUp, double point) {
        return new Pose(pose.grip, pose.shaft, new Vec3(x, y, z), palmUp, point);
    }

    private static Pose move(Pose a, Pose b, double time, double duration) {
        return a.mix(b, smooth(time / duration));
    }

    private static Pose cut(Pose a, Pose b, double t) {
        double amount = smooth(t);
        Pose pose = a.mix(b, amount);
        // The hand leads the shaft around the body before the blade extends at contact.
        double reach = .19 * Math.sin(Math.PI * amount);
        return new Pose(pose.grip.add(0, 0, -reach), pose.shaft, pose.free, pose.palmUp, pose.point);
    }

    private static Pose swing(int combo, double age) {
        // Continue across the contact point with a consistent direction, avoiding a pause at HIT.
        double t;
        if (age < HIT) {
            t = Math.clamp((age - CUT_FROM) / (HIT - CUT_FROM), 0, 1);
            return CUT_START[combo].mix(CUT_CONTACT[combo], t * t);
        }
        t = Math.clamp((age - HIT) / (CUT_TO - HIT), 0, 1);
        return CUT_CONTACT[combo].mix(CUT_END[combo], 1 - Math.pow(1 - t, 3));
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
