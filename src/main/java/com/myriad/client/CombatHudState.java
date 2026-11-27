package com.myriad.client;

import com.myriad.MyriadClient;
import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.yike.RainbowShell;
import com.myriad.combat.VanillaMeleeAttack;
import com.myriad.network.ControlStatusPayload;
import com.myriad.network.NetworkSetup.PlayerDataPayload;
import com.myriad.network.YikeStatePayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** The personal panel's view of the latest combat state. Casting and attacks remain server-owned. */
public final class CombatHudState {
    private static final List<KeyMapping> BINDINGS = List.of(MyriadClient.KEY_SKILL_Q,
            MyriadClient.KEY_SKILL_W, MyriadClient.KEY_SKILL_E, MyriadClient.KEY_SKILL_R);

    public enum SkillStatus { READY, COOLDOWN, NO_MANA, LOCKED, UNAVAILABLE, PASSIVE }

    public record Slot(int index, Skill skill, int rank, Component binding, SkillStatus status,
            int cooldownTicks, int recastTicks, int charges, int chargeRemaining, int chargeTotal) {}

    public record Attack(float progress, boolean ready, int cooldownTicks) {}

    public record Snapshot(CharacterType character, float health, float maxHealth, float shield, float mana,
            double maxMana, boolean unlimitedMana, int level, int xp, int xpToNext, boolean maxLevel,
            int gold, int skillPoints, List<Slot> activeSlots, Slot passive, Attack attack, boolean preparing,
            ControlStatusPayload control) {}

    private CombatHudState() {}

    public static boolean suppressesVanillaStatus() {
        return eligible(ClientDataHolder.currentPlayerSnapshot(Minecraft.getInstance()));
    }

    private static boolean eligible(PlayerDataPayload data) {
        return data != null && data.modEnabled() && (data.combatActive() || data.combatPreparing())
                && FirstGroupSkills.active(data.characterId());
    }

    public static Snapshot current() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        var data = ClientDataHolder.currentPlayerSnapshot(mc);
        if (!eligible(data) || !player.isAlive() || player.isSpectator() || data.health() <= 0
                || data.maxHealth() <= 0 || data.buffs().getOrDefault("hero_respawn", 0) > 0) return null;

        double maxMana = data.attrs().getOrDefault(ModAttributes.MAX_MANA.value().getDescriptionId(), 0.0);
        if (maxMana <= 0) return null;
        var character = CharacterType.byId(data.characterId());
        var control = ControlStatusOverlay.current();
        var yike = character == CharacterType.YIKE ? YikeClientState.get(player.getId()) : null;
        boolean unlimitedMana = data.training() && data.noMana();

        var activeSlots = new ArrayList<Slot>(BINDINGS.size());
        for (int slot = 0; slot < BINDINGS.size(); slot++) {
            var skill = (AbilitySkill) FirstGroupSkills.resolve(character, slot, data.skillLevels());
            activeSlots.add(activeSlot(data, skill, yike, control, unlimitedMana));
        }
        var passive = CombatHudSkillRules.selectedPassive(data.characterId(), data.skillLevels());
        Slot passiveSlot = passive == null ? null : new Slot(BINDINGS.size(), passive, data.skillLevels().get(passive.getId()),
                Component.translatable("hud.myriad.combat.passive"), SkillStatus.PASSIVE, 0, 0, -1, 0, 0);
        return new Snapshot(character, data.health(), data.maxHealth(), data.shield(), data.mana(), maxMana,
                unlimitedMana, data.level(), data.xp(), data.xpToNext(), data.level() >= data.levelCap(), data.gold(), data.skillPoints(),
                List.copyOf(activeSlots), passiveSlot, attack(data, character, player, yike, control),
                data.combatPreparing(), control);
    }

    private static Slot activeSlot(PlayerDataPayload data, AbilitySkill skill, YikeStatePayload yike,
            ControlStatusPayload control, boolean unlimitedMana) {
        int slot = skill.slot();
        int rank = data.skillLevels().getOrDefault(skill.getId(), 0);
        boolean anchorInPreparation = CombatHudSkillRules.freePreparation(skill, data.combatPreparing());
        boolean artilleryShot = skill instanceof RainbowShell && yike != null && (yike.mode() == 1 || yike.mode() == 2);
        String chargeKey = "skill_charge_" + skill.getId();
        int charges = SkillChargeDisplay.chargeCount(skill, data.buffs());
        int chargeRemaining = ClientDataHolder.syncedRemainingTicks(data.buffs().getOrDefault(chargeKey + "_remaining", 0));
        int chargeTotal = data.buffs().getOrDefault(chargeKey + "_total", 0);
        int recastTicks = recastTicks(skill, yike, control);
        int cooldown = artilleryShot ? yike.nextShot() : CombatHudSkillRules.blockingCooldown(
                ClientDataHolder.cooldownTicks(slot), data.noCooldown(), recastTicks, charges, chargeRemaining);

        boolean inPhase = (data.combatActive() || anchorInPreparation)
                && (yike == null || yike.mode() != 3 || recastTicks > 0);
        boolean controlled = !skill.canCastWhileControlled() && (control.stun() > 0 || control.silence() > 0);
        SkillStatus status = CombatHudSkillRules.status(CombatHudSkillRules.learned(skill, rank, data.training()),
                inPhase, cooldown, recastTicks, charges,
                CombatHudSkillRules.enoughMana(skill, rank, data.mana(), unlimitedMana, anchorInPreparation, recastTicks),
                controlled);

        KeyMapping binding = BINDINGS.get(slot);
        Component key = binding.isUnbound()
                ? Component.translatable("hud.myriad.combat.slot", slot + 1) : binding.getTranslatedKeyMessage();
        return new Slot(slot, skill, rank, key, status, cooldown, recastTicks, charges, chargeRemaining, chargeTotal);
    }

    static int recastTicks(AbilitySkill skill, YikeStatePayload yike, ControlStatusPayload control) {
        int ticks = SecondCastDisplay.remainingTicks(skill.getId());
        if (skill.getId().equals("keheng_suixing")) {
            return ClientDataHolder.buffTicks("g1_keheng_follow");
        }
        if (yike != null) {
            if (skill.slot() == CharacterType.SLOT_E && (yike.mode() == 1 || yike.mode() == 2)) {
                return yike.remaining();
            }
            if (skill.getId().equals("yike_prismatic_beam")) {
                return yike.mode() == 3 ? yike.remaining()
                        : skill.getId().equals(control.chargeSkill()) ? control.charge() : 0;
            }
        }
        return ticks;
    }

    private static Attack attack(PlayerDataPayload data, CharacterType character, LocalPlayer player,
            YikeStatePayload yike, ControlStatusPayload control) {
        boolean melee = VanillaMeleeAttack.usesVanilla(character);
        int remaining = data.basicAttackCooldown();
        float progress = 1.0F - Math.clamp(remaining / (float) Math.max(1, data.basicAttackCooldownMax()), 0, 1);
        if (melee) {
            progress = player.getAttackStrengthScale(.5f);
            remaining = (int) Math.ceil((1.0F - progress) * player.getCurrentItemAttackStrengthDelay());
        }
        boolean ready = data.combatActive() && remaining <= 0 && data.placementType() == 0 && !data.channeling()
                && control.stun() == 0 && control.charge() == 0 && ClientDataHolder.buffTicks("g1_stun") == 0
                && ClientDataHolder.buffTicks("g1_shuo_sheathing") == 0
                && PlayerDataProvider.isInitialWeapon(player.getMainHandItem())
                && (yike == null || yike.mode() == 0 && yike.windup() < 0);
        return new Attack(progress, ready, remaining);
    }
}
