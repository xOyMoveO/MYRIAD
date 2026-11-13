package com.myriad.game;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.resources.Identifier;
import com.myriad.player.PlayerDataProvider;

/** Server-authoritative home shop and departure haste. No client position claims. */
public final class BaseRules {
    private static final Identifier HASTE=Identifier.parse("myriad:base_haste");
    private static final class State { boolean inside; Boolean shopAllowed; long until,combatUntil; }
    private static final Map<UUID,State> STATES=new HashMap<>();
    public static boolean inside(ServerPlayer player){
        var manager=GameManager.get();var faction=manager==null?null:manager.factionOf(player);
        var base=faction==null?null:LaneSystem.respawnPoint(faction);
        return player.level().dimension()==net.minecraft.world.level.Level.OVERWORLD && base!=null
                && player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(base))<=100;
    }
    public static boolean canShop(ServerPlayer player){return TrainingMode.isEnabled() || HeroRespawnSystem.isWaiting(player)
            || player.isAlive()&&!player.isSpectator()&&inside(player);}
    public static void tick(ServerPlayer player){
        RecallSystem.tick(player);
        long now=player.level().getGameTime();var s=STATES.computeIfAbsent(player.getUUID(),id->new State());
        boolean inside=inside(player);
        boolean shopAllowed = ModState.isEnabled() && canShop(player);
        if (s.shopAllowed == null || s.shopAllowed != shopAllowed) {
            s.shopAllowed = shopAllowed;
            com.myriad.network.UiFeedbackPayload.shopAccess(player, shopAllowed);
        }
        boolean active=ModState.isEnabled()&&(TrainingMode.isEnabled()||GameManager.get()!=null&&GameManager.get().getPhase()==GameManager.Phase.PLAYING);
        if(active&&s.inside&&!inside&&now>=s.combatUntil&&player.isAlive()&&!player.isSpectator())s.until=now+160;
        s.inside=inside;
        if(!active||!player.isAlive()||player.isSpectator()||now>=s.until)stop(player,s);
        else {
            var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
            if(speed!=null&&!speed.hasModifier(HASTE))speed.addTransientModifier(new AttributeModifier(HASTE,2.50,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            PlayerDataProvider.get(player).setBuff("base_haste",(int)(s.until-now));
        }
    }
    public static void combat(Entity victim,Entity attacker){
        RecallSystem.cancel(victim);RecallSystem.cancel(attacker);cancel(victim);if(attacker!=victim)cancel(attacker);
    }
    private static void cancel(Entity entity){if(entity instanceof ServerPlayer p){var s=STATES.computeIfAbsent(p.getUUID(),id->new State());s.combatUntil=p.level().getGameTime()+100;stop(p,s);}}
    private static void stop(ServerPlayer p,State s){s.until=0;var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(HASTE);PlayerDataProvider.get(p).setBuff("base_haste",0);}
    public static void logout(ServerPlayer p){RecallSystem.cancel(p);var s=STATES.remove(p.getUUID());if(s!=null)stop(p,s);}
    public static void clear(){RecallSystem.clear();STATES.clear();}
}
