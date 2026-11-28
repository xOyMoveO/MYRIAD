package com.myriad.character.ability;

import com.myriad.character.Skill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

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
    public enum SupportEffect {
        SHIELD("shield"), HEAL("heal");

        private final String id;
        SupportEffect(String id) { this.id = id; }
        public String id() { return id; }
    }

    public enum SupportScaling { TOTAL_SPELL, BONUS_PHYSICAL }

    public record SupportAmount(SupportEffect effect, SupportScaling scaling, float base, float rankGrowth, float scale) {
        public float value(AbilityRuntime.Cast cast) {
            return value(cast.rank, cast.totalSpellPower, cast.bonusPhysicalAttack);
        }

        public float value(int rank, double totalSpellPower, double bonusPhysicalAttack) {
            double attribute = switch (scaling) {
                case TOTAL_SPELL -> totalSpellPower;
                case BONUS_PHYSICAL -> bonusPhysicalAttack;
            };
            return base + rankGrowth * (Math.max(1, rank) - 1) + scale * (float) attribute;
        }
    }

    public List<SupportAmount> supportAmounts() { return List.of(); }

    public String resolveSupportText(String text, int rank, double totalSpellPower, double bonusPhysicalAttack) {
        var amounts = supportAmounts();
        for (int index = 0; index < amounts.size(); index++)
            text = text.replace("{support" + index + "}",
                    AbilityFormulaText.amount(amounts.get(index).value(rank, totalSpellPower, bonusPhysicalAttack)));
        return text;
    }

    public List<AbilityDamageFormula> damageFormulas(){return List.of();}
    /** Named values for localized player descriptions, in the units used by the rule. */
    public Map<String, Number> descriptionFields(int rank) { return Map.of(); }

    public String resolveDescriptionFields(String template, int rank) {
        for (var field : descriptionFields(rank).entrySet()) {
            template = template.replace("{" + field.getKey() + "}",
                    AbilityFormulaText.amount(field.getValue().doubleValue()));
        }
        return template;
    }

    /** Set only when all listed segments can belong to the same cast, form and target. */
    public String singleTargetTotalKey() { return ""; }
    public String evaluatedDescription(int rank, double skillPower, double bonusPhysicalAttack){
        String mechanics = resolveDescriptionFields(description(), rank);
        return AbilityFormulaText.evaluate(resolveSupportText(mechanics, rank, skillPower, bonusPhysicalAttack), rank, skillPower);
    }
    public int slot(){return index()<2?-1:index()<4?index()-2:index()<6?2:3;}
    @Override public boolean isPassive(){return index()<2;}
    @Override public boolean isUltimate(){return index()>=6;}
    @Override public int getCooldownTicks(int rank){int[] values=definition.cooldowns();return values[Math.min(Math.max(0,rank-1),values.length-1)];}
    @Override public String levelEffects(int rank){return "Lv."+Math.max(1,rank)+" / "+maxRank()+" · "+evaluatedDescription(rank, FirstGroupGrowth.baselineSpellPower(), 0);}
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
    public final float manaCost(int rank) {
        int steps=Math.clamp(rank,1,maxRank())-1;
        return (float)(getManaCost()*(100-steps*5)/100.0);
    }
    public boolean recastWhileMoving(){return false;}
    public boolean recastWhileRooted(){return false;}
    public boolean recastDuringChannel(){return false;}
    public boolean requiresStandingPoint(){return false;}
    public void present(AbilityRuntime.Cast cast){}
    public void projectileTurned(AbilityRuntime.Cast cast, net.minecraft.world.phys.Vec3 end,
            net.minecraft.world.phys.Vec3 outboundDirection){}
    public void projectileBlockedByWind(AbilityRuntime.Cast cast, net.minecraft.world.phys.Vec3 contact) {}
    public void preparing(AbilityRuntime.Cast c){}
    public void selectionPose(AbilityRuntime.Cast c,boolean selecting){}
    public void windupPose(AbilityRuntime.Cast c,boolean winding){}
    public enum DashPose { START, MOVING, FINISH, CANCEL }
    /** Presentation only. The movement task remains authoritative for every transition. */
    public void dashPose(AbilityRuntime.Cast c,DashPose phase,net.minecraft.world.phys.Vec3 direction){}
    /** Visual flight progress only; any landing effect is still owned by the landing callback. */
    public void leapPose(AbilityRuntime.Cast c,DashPose phase,double progress){}
    /** Opt out of the generic cast Foley when this module owns its staged sound cues. */
    public boolean hasOwnCastSound(){return false;}
    public double pointRange(AbilityRuntime.Cast c){return 12;}
    public double previewRadius(AbilityRuntime.Cast c){return 1;}
    public boolean canRelease(AbilityRuntime.Cast c){return true;}
    /** Feedback for a failed canRelease check. Most skills reject an invalid target. */
    public com.myriad.combat.InputFailure releaseRejection(AbilityRuntime.Cast c) {
        return com.myriad.combat.InputFailure.TARGET;
    }
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
