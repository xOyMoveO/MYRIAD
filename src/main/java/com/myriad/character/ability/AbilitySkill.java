package com.myriad.character.ability;

import com.myriad.character.Skill;
import com.myriad.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;

/** Reusable ability module: every concrete skill owns its effect logic, not a role/batch switch. */
public abstract class AbilitySkill extends Skill {
    public record Definition(String id,String role,int index,int maxRank,float mana,int[] cooldowns,
                             String name,String description,String iconAlias){
        public Definition {cooldowns=cooldowns.clone();}
        @Override public int[] cooldowns(){return cooldowns.clone();}
    }
    private final Definition definition;
    protected AbilitySkill(Definition definition){super(definition.id(),definition.cooldowns()[0],definition.mana());this.definition=definition;}
    public Definition definition(){return definition;}
    public String role(){return definition.role();} public int index(){return definition.index();}
    public int maxRank(){return definition.maxRank();} public String displayName(){return definition.name();}
    public String description(){return definition.description();} public String iconAlias(){return definition.iconAlias();}
    public int slot(){return index()<2?-1:index()<4?index()-2:index()<6?2:3;}
    @Override public boolean isPassive(){return index()<2;}
    @Override public boolean isUltimate(){return index()>=6;}
    @Override public int getCooldownTicks(int rank){int[] values=definition.cooldowns();return values[Math.min(Math.max(0,rank-1),values.length-1)];}
    @Override public String levelEffects(int rank){return "Lv."+Math.max(1,rank)+" / "+maxRank()+" · "+description();}
    @Override public final void cast(ServerPlayer player,PlayerData data){AbilityRuntime.request(player,data,this);}
    /** The concrete class owns validation and whether it needs ground-point confirmation. */
    public boolean needsPoint(){return false;}
    /** Server-side opt-in: delay payment/release by this many local action ticks. Existing skills pay immediately. */
    public int commitWindupTicks(){return 0;}
    public int inputRecoveryTicks(){return 3;}
    public float windupWalkFactor(AbilityRuntime.Cast c){return 0;}
    public boolean cooldownReady(AbilityRuntime.Cast c){return c.data.getCooldown(getId())<=0;}
    public int cooldownTicks(AbilityRuntime.Cast c){return getCooldownTicks(c.rank);}
    public boolean interruptPreparation(AbilityRuntime.Cast c){return false;}
    /** Basic availability is independent of paid rank; paid ranks still consume ordinary skill points. */
    public boolean baseAvailable(){return false;}
    public float manaCost(AbilityRuntime.Cast c){return getManaCost();}
    public boolean recastWhileMoving(){return false;}
    public boolean recastWhileRooted(){return false;}
    public boolean recastDuringChannel(){return false;}
    public boolean requiresStandingPoint(){return false;}
    public void present(AbilityRuntime.Cast cast){}
    public void preparing(AbilityRuntime.Cast c){}
    /** Opt out of the generic cast Foley when this module owns its staged sound cues. */
    public boolean hasOwnCastSound(){return false;}
    public double pointRange(AbilityRuntime.Cast c){return 12;}
    public double previewRadius(AbilityRuntime.Cast c){return 1;}
    public boolean canRelease(AbilityRuntime.Cast c){return true;}
    /** Explicit defensive exception; ordinary abilities remain blocked by stun/silence. */
    public boolean canCastWhileControlled(){return false;}
    public abstract void release(AbilityRuntime.Cast cast);
    public double modifyDamage(AbilityRuntime.Hit hit){return 1;}
    public void onHit(AbilityRuntime.Hit hit){}
    public void onCast(AbilityRuntime.Cast cast){}
    /** Actual non-passive heal/shield application; passive protection never recursively dispatches. */
    public void onSupport(AbilityRuntime.Cast cast,net.minecraft.world.entity.LivingEntity target){}
    public double incomingMultiplier(AbilityRuntime.Cast cast){return 1;}
    public void tickPassive(AbilityRuntime.Cast cast){}
    public boolean recastAvailable(AbilityRuntime.Cast cast){return false;}
    public void recast(AbilityRuntime.Cast cast){}
}
