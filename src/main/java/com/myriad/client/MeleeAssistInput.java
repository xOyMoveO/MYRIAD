package com.myriad.client;

import com.myriad.character.CharacterType;
import com.myriad.combat.MeleeAssistTargeting;
import com.myriad.combat.VanillaMeleeAttack;
import com.myriad.game.CherryRiftMonsterEntity;
import com.myriad.game.Faction;
import com.myriad.game.LaneMinionEntity;
import com.myriad.game.TrainingGround;
import com.myriad.game.TrainingGroundMap;
import com.myriad.network.NetworkSetup.MeleeAssistPayload;
import com.myriad.network.Packets;
import com.myriad.player.PlayerDataProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;

@Environment(EnvType.CLIENT)
public final class MeleeAssistInput {
    private MeleeAssistInput() {}

    public static boolean shouldAssist(Minecraft mc) {
        var player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null
                || mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.ENTITY
                || mc.getCameraEntity() != player || !ClientDataHolder.canFight()
                || !VanillaMeleeAttack.usesVanilla(CharacterType.byId(ClientDataHolder.characterId()))
                || !player.isAlive() || player.isSpectator() || player.isHandsBusy() || player.isUsingItem()
                || ClientDataHolder.buffTicks("g1_shuo_sheathing") > 0
                || !ClientPlayNetworking.canSend(MeleeAssistPayload.TYPE)) return false;
        var weapon = player.getMainHandItem();
        return PlayerDataProvider.isInitialWeapon(weapon) && weapon.isItemEnabled(mc.level.enabledFeatures())
                && !weapon.has(DataComponents.PIERCING_WEAPON) && !player.cannotAttackWithItem(weapon, 0)
                && MeleeAssistTargeting.find(player, target -> visibleOpponent(player, target)) != null;
    }

    /** Exact vanilla selections can include structures; the assist keeps its narrower target list. */
    public static boolean hasAttackTarget(Minecraft mc) {
        if (!(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)) return shouldAssist(mc);
        var player = mc.player;
        if (!(hit.getEntity() instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()
                || target.isInvisibleTo(player) || !player.hasLineOfSight(target)
                || !player.isWithinEntityInteractionRange(target, 0)
                || target instanceof Player hero && hero.isCreative()) return false;
        if (target instanceof com.myriad.game.DefenseTowerEntity) {
            var name = target.getCustomName();
            var team = ClientDataHolder.faction();
            return team != null && name != null && name.getContents() instanceof TranslatableContents text
                    && text.getArgs().length > 0 && text.getArgs()[0] instanceof net.minecraft.network.chat.Component faction
                    && !faction.equals(team.displayName());
        }
        return visibleOpponent(player, target);
    }

    private static boolean visibleOpponent(LocalPlayer player, LivingEntity target) {
        if (target.isInvisible() || target.isInvisibleTo(player)) return false;
        boolean practice = player.level().dimension().equals(TrainingGroundMap.KEY);
        if (practice && !TrainingGround.mayPreselectPracticeTarget(player, target)) return false;
        if (target instanceof Player) {
            return FogOfWarClient.shouldRenderPlayer(target.getId()) && (practice || !player.isAlliedTo(target));
        }
        if (target instanceof LaneMinionEntity minion) {
            return ClientDataHolder.faction() != (minion.style() < 3 ? Faction.GUARDIAN : Faction.DESTRUCTION);
        }
        if (target instanceof CherryRiftMonsterEntity) return true;
        var name = target.getCustomName();
        return name != null && name.getContents() instanceof TranslatableContents text
                && (text.getKey().equals("entity.myriad.jiaren") || text.getKey().startsWith("monster.myriad."));
    }

    public static void send(LocalPlayer player) {
        // Keep the normal movement and hotbar state ahead of the fieldless intent.
        player.connection.send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
        player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(),
                player.onGround(), player.horizontalCollision));
        Packets.sendToServer(new MeleeAssistPayload());
        player.resetAttackStrengthTicker();
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
    }
}
