package com.myriad.character.ability;

/** Stable sprite indices: first seven are the existing white point and night materials. */
public final class AbilityVfxMaterials {
    private AbilityVfxMaterials(){}
    public static final int THUNDER_BOLT=7,THUNDER_SLASH=8,THUNDER_SCORCH=9,THUNDER_SEAL=10,
            FIRE_FLAME=11,FIRE_SLASH=12,FIRE_CRACK=13,FIRE_SMOKE=14,
            INK_TALISMAN=15,INK_SEAL=16,INK_BIND=17,INK_BREAK=18,
            WIND_BLADE=19,WIND_STREAK=20,WIND_SIGIL=21,WIND_IMPACT=22;
    public static final int SWORD_BLADE=23,SWORD_SLASH=24,SWORD_SIGIL=25,SWORD_TRAIL=26,
            ICE_SHARD=27,ICE_CREST=28,ICE_CRACK=29,ICE_MIST=30,
            HERB_LEAF=31,HERB_LOTUS=32,HERB_SEAL=33,HERB_DEW=34,
            WAR_BLADE=35,WAR_SLASH=36,WAR_CRACK=37,WAR_DUST=38,
            SPIRIT_CRANE=39,SPIRIT_KNOT=40,SPIRIT_SEAL=41,SPIRIT_THREAD=42;
    public static final int LINGYUAN_PIXEL_MODEL=43;
    public static final int LINGYUAN_PIXEL_SLASH=44,LINGYUAN_PIXEL_STREAM=45,LINGYUAN_PIXEL_SEAL=46,LINGYUAN_PIXEL_IMPACT=47;
    public static final int LINGYUAN_PIXEL_FILAMENT=48,LINGYUAN_PIXEL_MOTES=49;
    public static final int NIGHT_GRAY_BLADE=50,NIGHT_GRAY_SLASH=51,NIGHT_GRAY_WAKE=52,
            NIGHT_GRAY_SMOKE=53,NIGHT_GRAY_SEAL=54,NIGHT_GRAY_IMPACT=55;
    public static final int NIGHT_NEEDLE_MODEL=56;
    public static final int THUNDER_PIXEL_SLASH=57,THUNDER_PIXEL_BOLT=58,THUNDER_PIXEL_IMPACT=59,
            THUNDER_PIXEL_SEAL=60,THUNDER_PIXEL_CORONA=61,THUNDER_PIXEL_PILLAR=62;
    public static final int THUNDER_PROJECTILE_MODEL=63;
    public static final int FENJUE_PIXEL_SLASH=64,FENJUE_PIXEL_FLAME=65,FENJUE_PIXEL_BURST=66,
            FENJUE_PIXEL_RING=67,FENJUE_PIXEL_STREAM=68,FENJUE_PIXEL_EMBERS=69;
    public static final int FENJUE_CRISP_SLASH=70;
    public static final int FENJUE_HD_FIRE=71,FENJUE_GROUND=0;
    public static final int FENJUE_FLOW_RING=72,FENJUE_FLOW_FIELD=73;
    public static final int FENJUE_HIT=74;
    public static final int SHUO_SLASH=75;
    public static final int SHUO_GUARD=76,SHUO_STREAM=77,SHUO_IMPACT=78;
    public static final int KEHENG_HD_RING=79,KEHENG_HD_ARC=80,KEHENG_HD_STREAM=81,KEHENG_HD_IMPACT=82,
            KEHENG_HD_VEIL=83,KEHENG_HD_SHIELD=84,KEHENG_HD_MOTES=85,KEHENG_HD_SPARK=86;
    public static final int YIKE_HD_RING=87,YIKE_HD_ARC=88,YIKE_HD_STREAM=89,YIKE_HD_IMPACT=90,
            YIKE_HD_VEIL=91,YIKE_HD_SHIELD=92,YIKE_HD_MOTES=93,YIKE_HD_SPARK=94;
    public static final int KEHENG_PROJECTILE_MODEL=95,YIKE_PROJECTILE_MODEL=96;
    public static final int RIVET_MODEL=97;
    public static final int MAX=RIVET_MODEL;
    public static boolean flameGround(int material){return material==FENJUE_GROUND||material==FENJUE_FLOW_RING||material==FENJUE_FLOW_FIELD;}
}
