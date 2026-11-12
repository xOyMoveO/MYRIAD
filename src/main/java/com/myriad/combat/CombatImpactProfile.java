package com.myriad.combat;

import com.myriad.character.CharacterType;

/** Shared audio vocabulary only: no skill mechanics, damage formulas or client classes. */
public enum CombatImpactProfile {
    BLADE("blade"), BLUNT("blunt"), PROJECTILE("projectile"), ARCANE("arcane"),
    CRITICAL("critical"), STRUCTURE("structure"), SHIELD("shield");
    public static final int PAYLOAD_BASE=16;
    public final String id;
    CombatImpactProfile(String id){this.id=id;}
    public int payload(boolean critical){return PAYLOAD_BASE+ordinal()*2+(critical?1:0);}
    public static boolean isPayload(int kind){return kind>=PAYLOAD_BASE&&kind<PAYLOAD_BASE+values().length*2;}
    public static CombatImpactProfile fromPayload(int kind){
        if(!isPayload(kind))throw new IllegalArgumentException("Not an impact outcome: "+kind);
        return values()[(kind-PAYLOAD_BASE)/2];
    }
    public static CombatImpactProfile choose(CharacterType role,int damageType,boolean critical,boolean structure,boolean shield){
        if(shield)return SHIELD;
        if(structure)return STRUCTURE;
        if(critical)return CRITICAL;
        if(damageType==1)return ARCANE;
        if(role==CharacterType.XUANJIA||role==CharacterType.CHIXIAO)return BLUNT;
        if(role==CharacterType.LIGE||role==CharacterType.SHENLI||role==CharacterType.WANYUE)return PROJECTILE;
        if(role!=null&&!VanillaMeleeAttack.usesVanilla(role))return ARCANE;
        return BLADE;
    }
    /** Same-tick AoE is one contact. A later crit can supersede a normal hit. */
    public static boolean allow(long now,long last,boolean wasCritical,boolean critical,boolean periodic){
        if(last==Long.MIN_VALUE||now<last)return true;
        long elapsed=now-last;
        if(elapsed<=0)return false;
        return elapsed>=(periodic?6:3)||critical&&!wasCritical;
    }
}
