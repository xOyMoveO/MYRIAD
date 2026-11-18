package com.myriad.game;

/** Communication only: no target entity is stored or followed. */
public enum TeamSignalKind {
    ATTACK(0xFFFF8F64, 1.35f), RETREAT(0xFFFF646F, .75f), GATHER(0xFF75E8DD, 1.05f),
    HELP(0xFFFFD46B, 1.55f), MISSING(0xFFC7A0FF, .9f), DANGER(0xFFFFB65B, .6f),
    OBJECTIVE(0xFF80CFFF, 1.2f);
    public final int color; public final float pitch;
    TeamSignalKind(int color, float pitch) { this.color=color; this.pitch=pitch; }
    public String key() { return "signal.myriad."+name().toLowerCase(java.util.Locale.ROOT); }
    public static boolean valid(int id) { return id>=0&&id<values().length; }
}
