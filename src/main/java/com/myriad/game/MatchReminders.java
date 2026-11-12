package com.myriad.game;

import com.myriad.player.PlayerDataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Short, throttled reminders, scoped to the current match rather than login time. */
public final class MatchReminders {
    private MatchReminders() {}
    public static void tick(ServerPlayer player) {
        GameManager manager = GameManager.get();
        if (manager == null || manager.getPhase() != GameManager.Phase.PLAYING) return;
        var data = PlayerDataProvider.get(player);
        if (data == null || data.getCharacterId() == null || player.isSpectator()) return;
        long now = player.level().getGameTime();
        long round = manager.getMatchStartTime();
        var tag = player.getPersistentData();
        if (tag.getLongOr("myriad_reminder_round", -1) != round) {
            tag.putLong("myriad_reminder_round", round);
            tag.putLong("myriad_reminder_next", 0);
        }
        if (now < tag.getLongOr("myriad_reminder_next", 0)) return;
        boolean empty = java.util.Arrays.stream(data.getEquipmentSlots()).allMatch(id -> id == null || id.isBlank());
        boolean shop = empty && data.getGold() >= 450 && manager.getMatchElapsedTicks(now) < 2400;
        boolean points = data.getSkillPoints() > 0;
        if (!shop && !points) return;
        Component message = Component.translatable(shop && points ? "message.myriad.reminder.both"
                : shop ? "message.myriad.reminder.shop" : "message.myriad.reminder.skills");
        player.sendSystemMessage(message, true);
        tag.putLong("myriad_reminder_next", now + 600);
    }
}
