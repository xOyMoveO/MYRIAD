package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import java.util.HashSet;
import java.util.HashMap;

/** Isolated server checks for the new graph and authoritative attribute/learning paths. */
final class FirstGroupRegressionChecks {
    private FirstGroupRegressionChecks() { }
    static void run(GameTestHelper h) {
        boolean mod = ModState.isEnabled(), training = TrainingMode.isEnabled();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        var p = (ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        boolean[] deferred={false};
        try {
            AbilityVfxChecks.run(h);
            NightGrayVfxChecks.verify(h);
            ThunderPixelVfxChecks.verify(h);
            ThunderProjectileChecks.verify(h);
            ThunderWakeChecks.verify(h);
            FenjueVfxChecks.verify(h);
            ThunderAudioChecks.verify(h);
            FenjueSlashAudioChecks.verify(h);
            FlameStepAudioChecks.verify(h);
            NightBasicNeedleChecks.verify(h);
            NightShieldChecks.verify(h);
            SharedControlChecks.start(h);
            LevelUpPresentationChecks.verify(h);
            RosterRetirementChecks.verify(h, p);
            RuneRemovalChecks.run(h, p);
            CombatImpactChecks.verify(h,p);
            VanillaMeleeChecks.verify(h);
            ActionRemovalChecks.verify(h);
            NightDaggerViewChecks.verify(h);
            ThunderSwordViewChecks.verify(h);
            FlameDaoViewChecks.verify(h);
            BasicAttackTrailChecks.verify(h);
            com.myriad.client.firstperson.WindHandChecks.verify(h);
            FlameSlashViewChecks.verify(h);
            FlameStepViewChecks.verify(h);
            FenjueMajorViewChecks.verify(h);
            FlameFissureChecks.verify(h);
            FlameGhostChecks.verify(h);
            FlameGroundChecks.verify(h);
            FlameHitChecks.verify(h);
            FlameMajorAudioChecks.start(h);
            FlameSlashAimChecks.verify(h);
            CrispFlameSlashChecks.verify(h);
            FlameSlashReleaseChecks.verify(h);
            ThunderSkillViewChecks.verify(h);
            ThunderSkillReleaseChecks.verify(h);
            ThunderThirdStrikeChecks.verify(h);
            NightSkillViewChecks.verify(h);
            NightSkillReleaseChecks.verify(h);
            SkillChoiceUiChecks.verify(h);
            CharacterChoiceUiChecks.verify(h);
            ShuoChecks.verify(h);
            PassiveGrowthChecks.verify(h);
            var classes = new HashSet<Class<?>>();
            h.assertTrue(com.myriad.character.ability.AbilityFormulaText.evaluate("45＋15(r－1)＋55%S",3,100).equals("130"),
                    "Tooltip displays the current rank and S value");
            for (String role : FirstGroupSkills.ROLES) {
                var skills = FirstGroupSkills.candidates(role);
                h.assertTrue(skills.size() == 8, "Eight independent candidates for " + role);
                var ranks = new HashMap<String,Integer>();
                for (var skill : skills) {
                    h.assertTrue(classes.add(skill.getClass()), "Each skill needs its own concrete implementation");
                    h.assertTrue(FirstGroupSkills.learnError(role, ranks, 20, 20, skill.getId()) == null,
                            "Valid candidate must be learnable");
                    ranks.put(skill.getId(), skill.maxRank());
                    h.assertTrue(FirstGroupSkills.learnError(role, ranks, 20, 20, skill.getId()) != null,
                            "Never exceed category cap");
                    ranks.clear();
                    int opposite = FirstGroupSkills.opposite(skill.index());
                    if (opposite >= 0) {
                        ranks.put(skills.get(opposite).getId(), 1);
                        h.assertTrue(FirstGroupSkills.learnError(role, ranks, 20, 20, skill.getId()) != null,
                                "Passive/major/ultimate alternatives must lock each other");
                        ranks.clear();
                    }
                }
                ranks.put(skills.get(0).getId(),5); ranks.put(skills.get(2).getId(),5);
                ranks.put(skills.get(4).getId(),7); ranks.put(skills.get(6).getId(),3);
                h.assertTrue(FirstGroupSkills.learnError(role,ranks,1,20,skills.get(3).getId()) == null,
                        "A free passive leaves five ordinary points for the other regular skill");
                var d = PlayerDataProvider.get(p);
                d.setCharacterId(role); d.setLevel(1); d.refundAllSkillPointsForTraining();
                CharacterType.applyInitialAttributes(p, CharacterType.byId(role));
                FirstGroupGrowth.initialize(p,d); PlayerHealthSystem.fill(p);
                h.assertTrue(Math.abs(d.getModMaxHealth()-FirstGroupGrowth.health(role,1)) < .01,
                        "New HP baseline must reach the actual health pool");
                int openingAttack = "luoxia".equals(role) ? 100 : 65;
                h.assertTrue(p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK) == openingAttack
                        && p.getAttributeValue(ModAttributes.SPELL_POWER) == 30, "Opening AD and spell power");
                p.setHealth(100); d.setMana(123);
                LevelSystem.addXp(p,d,LevelSystem.xpToNext(1));
                h.assertTrue(d.getLevel()==2 && d.getSkillPoints()==2, "Level-up awards exactly one skill point");
                h.assertTrue(p.getHealth()==100 && d.getMana()==123, "No refill on level-up");
                d.setLevel(20); LevelSystem.applyLevelAttributes(p,20);
                h.assertTrue(LevelSystem.maxLevel(d)==20 && Math.abs(d.getModMaxHealth()-FirstGroupGrowth.health(role,20))<.01,
                        "First batch reaches level 20 with its designed HP");
                var type = CharacterType.byId(role);
                double attackGrowth = RoleStatGrowthChecks.attackGrowth(type, 20);
                double baseAttack = openingAttack + attackGrowth;
                double spellPower = 30 + RoleStatGrowthChecks.spellGrowth(type, 20);
                h.assertTrue(p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK)==baseAttack
                        && p.getAttributeValue(ModAttributes.SPELL_POWER)==spellPower, "Defined level-20 AD and spell power");
                d.setFirstGroupAttribute("strength",2); FirstGroupGrowth.apply(p,d);
                h.assertTrue(p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK)==baseAttack + 2 * 15
                        && p.getAttributeValue(ModAttributes.SPELL_POWER)==spellPower, "Strength buys 15 physical attack per point");
                var physicalFormula=AbilityDamageFormula.bonusPhysical(45,15,.50f);
                var context=AbilityRuntime.context(p,skills.get(2));
                float baseSpell=context.spellPowerValue(45,15,.55f);
                float basePhysical=physicalFormula.value(context);
                h.assertTrue(context.totalPhysicalAttack==baseAttack+30 && context.totalSpellPower==spellPower
                        && context.bonusPhysicalAttack==attackGrowth+30 && context.purchasedSpellPower==0,
                        "Cast context distinguishes total, bonus physical and purchased spell attributes");
                d.setFirstGroupAttribute("strength",3); FirstGroupGrowth.apply(p,d);
                var strong=AbilityRuntime.context(p,skills.get(2));
                h.assertTrue(strong.spellPowerValue(45,15,.55f)==baseSpell
                        && physicalFormula.value(strong)==basePhysical+7.5f,
                        "Strength grows the physical formula independently of the spell formula");
                d.setFirstGroupAttribute("wisdom",2); FirstGroupGrowth.apply(p,d);
                h.assertTrue(p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK)==baseAttack + 3 * 15
                        && p.getAttributeValue(ModAttributes.SPELL_POWER)==spellPower+40, "Wisdom buys 20 spell power per point");
                var wise=AbilityRuntime.context(p,skills.get(2));
                h.assertTrue(wise.spellPowerValue(45,15,.55f)>baseSpell
                        && physicalFormula.value(wise)==physicalFormula.value(strong),
                        "Wisdom grows the spell formula independently of the physical formula");
                h.assertTrue(Math.abs(d.getModMaxHealth()-FirstGroupGrowth.health(role,20))<.01,
                        "Gold attributes must not increase HP");
                d.setFirstGroupAttribute("strength",0); d.setFirstGroupAttribute("wisdom",0);
                AbilityRuntime.clear(p);
            }
            h.assertTrue(classes.size()==FirstGroupSkills.ROLES.size()*8,"Every registered hero owns eight independent candidate implementations");
            var d=PlayerDataProvider.get(p); d.setCharacterId("yingxiao"); d.setLevel(20);
            d.refundAllSkillPointsForTraining();
            var pig=h.spawn(net.minecraft.world.entity.EntityTypes.PIG,new net.minecraft.core.BlockPos(3,2,3));
            pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
            pig.setHealth(1000);
            var cast=AbilityRuntime.context(p,FirstGroupSkills.node("yingxiao",2));
            for(var kind:com.myriad.combat.DamageCalc.Kind.values()) {
                pig.damageCooldownTime = 0;float hp=pig.getHealth();cast.damage(pig,100,kind);
                h.assertTrue(Math.abs(hp-pig.getHealth()-100)<.01,"No hidden damage multiplier for "+kind);
            }
            pig.discard();
            AttributeGrowthChecks.verify(h,p,d);
            String passive=FirstGroupSkills.node("yingxiao",0).getId();
            PlayerDataProvider.upgradeSkill(p,passive);
            PlayerDataProvider.upgradeSkill(p,FirstGroupSkills.node("yingxiao",1).getId());
            h.assertTrue(d.skillLevel(passive)==5 && d.getSkillPoints()==20,"Authoritative branch rejection spends no points");
            d.setMana(200); var pending=FirstGroupSkills.node("yingxiao",5);
            d.setSkillLevel(pending.getId(),1);AbilityRuntime.request(p,d,pending);AbilityRuntime.cancel(p);
            h.assertTrue(d.getMana()==200 && d.getCooldown(pending.getId())==0 && d.getPlacementType()==0,
                    "Cancelling placement must cost neither mana nor cooldown");
            FirstGroupSpatialChecks.verify(h,p,d);
            SkillChargeChecks.verify(h,p);
            SecondGroupChecks.verify(h,p);
            System.out.println("[MYRIAD-G1-G2] PASS: "+classes.size()+" independent modules across "+FirstGroupSkills.ROLES.size()+" heroes, caps, alternatives, budgets, growth, economy, persistence, cancel");
            boolean[] completed={false};
            AbilityRuntime.context(p,FirstGroupSkills.node("yingxiao",6)).later(3,true,()->{
                h.assertTrue(!com.myriad.character.ability.SkillChargeSystem.active(p),"Charge lock is released before the follow-up action");
                h.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)>0,"Completed charge restores walking speed");
                h.assertTrue(!completed[0],"Charge executes once");completed[0]=true;
            });
            HeavenSwordChecks.start(h);
            ShuoChecks.start(h);
            ChargeFlightLifecycleChecks.start(h);
            InkCastAnimationChecks.start(h);
            WindCastAnimationChecks.start(h);
            NightDashRangeChecks.start(h);
            NightExecutionChecks.start(h);
            ThunderChainChecks.start(h);
            ThunderAudioChecks.start(h);
            ThunderSkillReleaseChecks.start(h);
            FenjueReleaseChecks.start(h);
            FenjueLeapChecks.start(h);
            AbilityContractChecks.start(h);
            SecondGroupExecutionChecks.run(h,()->{AbilityRuntime.clear(p);ModState.setEnabled(mod);TrainingMode.setEnabled(training);});
            deferred[0]=true;
            h.runAfterDelay(4,()->{try{
                AbilityRuntime.tick(p);AbilityRuntime.tick(p);
                h.assertTrue(completed[0]&&!AbilityRuntime.channeling(p),"Natural completion releases the channel and executes its continuation");
                System.out.println("[MYRIAD-CHARGE-COMPLETE] PASS: real tick delay, release before continuation, single execution");
                AbilityRuntime.clear(p);
            }catch(Throwable failure){AbilityRuntime.clear(p);ModState.setEnabled(mod);TrainingMode.setEnabled(training);throw failure;}});
        } catch(Throwable failure){
            // A later scheduled check may also fail after global flags are restored.
            // Preserve the original setup failure before that secondary diagnostic.
            failure.printStackTrace();throw failure;
        } finally {
            if(!deferred[0]){AbilityRuntime.clear(p); ModState.setEnabled(mod); TrainingMode.setEnabled(training);}
        }
    }
}
