package dev.leafmc.bridge;

import com.booksaw.betterTeams.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Direct API integration verified against BetterTeams 5.1.7. Team creation
 * needs an online owner because createNewTeam accepts Player.
 */
public final class BetterTeamsService {
    private final boolean available;

    public BetterTeamsService(Config c) {
        Plugin p = Bukkit.getPluginManager().getPlugin("BetterTeams");
        available = c.betterTeams() && p != null && p.isEnabled();
    }

    public boolean available() {
        return available;
    }

    private void check() {
        if (!available)
            throw new IllegalStateException("BetterTeams is missing, disabled, or disabled in this bridge.");
    }

    private Player player(String n) {
        if (!Validation.player(n))
            throw new IllegalArgumentException("Invalid player name or UUID.");
        Player p = Bukkit.getPlayer(n);
        if (p == null)
            throw new IllegalArgumentException("BetterTeams team owners and additions must be online on this version.");
        return p;
    }

    public Result create(String name, String owner) {
        check();
        if (!Team.isValidTeamName(name))
            throw new IllegalArgumentException("Name is rejected by BetterTeams' team-name rules.");
        if (Team.getTeamByName(name) != null)
            throw new IllegalArgumentException("A team with that name already exists.");
        Player p = player(owner);
        if (Team.getTeam(p) != null)
            throw new IllegalArgumentException("Owner is already in a team.");
        Team team = Team.getTeamManager().createNewTeam(name, p);
        if (team == null || team.getTeamPlayer(p) == null || team.getTeamPlayer(p).getRank() != PlayerRank.OWNER)
            return Result.fail("Team creation could not be verified",
                    "BetterTeams did not create the requested owned team.");
        return Result.ok("Team created", "Created " + team.getName() + " with owner " + p.getName() + ".");
    }

    public Result info(String name) {
        check();
        Team t = Team.getTeamByName(name);
        if (t == null)
            return Result.fail("Team not found", name);
        return Result.ok("Team information",
                t.getName() + "; members: " + t.getMembers().size() + "; open: " + t.isOpen());
    }

    public Result disband(String name) {
        check();
        Team t = Team.getTeamByName(name);
        if (t == null)
            return Result.fail("Team not found", name);
        t.disband();
        return Team.getTeamByName(name) == null ? Result.ok("Team disbanded", name)
                : Result.fail("Disband could not be verified", name);
    }

    public Result add(String name, String player) {
        check();
        Team t = Team.getTeamByName(name);
        if (t == null)
            return Result.fail("Team not found", name);
        Player p = player(player);
        if (Team.getTeam(p) != null)
            throw new IllegalArgumentException("Player is already in a team.");
        if (!t.join(p))
            return Result.fail("Could not add player", "BetterTeams rejected the join.");
        return Result.ok("Player added", p.getName() + " joined " + t.getName());
    }

    public Result setOwner(String name, String owner) {
        check();
        Team t = Team.getTeamByName(name);
        if (t == null)
            return Result.fail("Team not found", name);
        Player p = player(owner);
        TeamPlayer member = t.getTeamPlayer(p);
        if (member == null)
            throw new IllegalArgumentException("Owner must already be a member of the team.");
        t.promotePlayerToOwner(member);
        return member.getRank() == PlayerRank.OWNER
                ? Result.ok("Owner changed", p.getName() + " now owns " + t.getName())
                : Result.fail("Ownership change could not be verified", "");
    }
}
