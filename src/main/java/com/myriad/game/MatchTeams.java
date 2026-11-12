package com.myriad.game;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.TeamColor;
public final class MatchTeams {
    public static void sync(MinecraftServer server) {
        if (!ModState.isEnabled()) return;
        var scoreboard = server.getScoreboard();
        for (var player : server.getPlayerList().getPlayers()) {
            var faction = ModState.isEnabled() && GameManager.get() != null ? GameManager.get().factionOf(player) : null;
            var old = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (faction == null) {
                if (old != null && old.getName().startsWith("myriad_team_")) scoreboard.removePlayerFromTeam(player.getScoreboardName());
                continue;
            }
            String id = "myriad_team_" + faction.getName();
            var team = scoreboard.getPlayerTeam(id);
            if (team == null) {
                team = scoreboard.addPlayerTeam(id);
                team.setColor(java.util.Optional.of(faction == Faction.XUANXIAO ? TeamColor.RED : TeamColor.AQUA));
                team.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
            }
            if (old != team) scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }
    }
}
