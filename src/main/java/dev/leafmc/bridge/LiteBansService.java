package dev.leafmc.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import litebans.api.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * LiteBans 2.18.8 has no stable public punishment API in the supplied artifact.
 * Uses its documented command interface with fixed labels and validated tokens.
 */
public final class LiteBansService {
  private final boolean available;
  private final Config config;

  public LiteBansService(Config c) {
    config = c;
    Plugin p = Bukkit.getPluginManager().getPlugin("LiteBans");
    available = c.liteBans() && p != null && p.isEnabled();
  }

  public boolean available() {
    return available;
  }

  public Result punish(String action, String player, String duration, String reason) {
    if (!available)
      return Result.fail("LiteBans unavailable", "LiteBans is missing, disabled, or disabled in this bridge.");
    if (!(action.equals("ipban") ? Validation.playerOrIpv4(player) : Validation.player(player)))
      throw new IllegalArgumentException("Invalid player name, UUID, or IP address.");
    boolean timed = action.equals("tempban") || action.equals("tempmute");
    boolean optionalTimed = action.equals("ipban") && !duration.isBlank();
    if ((timed || optionalTimed) && Validation.duration(duration).isEmpty())
      throw new IllegalArgumentException("Duration must be 10m, 2h, 7d, or another supported s/m/h/d/w value.");
    String safeReason = Validation.reason(reason, config.reasonLimit(), config.reasonRequired());
    String command = action + " " + player + ((timed || optionalTimed) ? " " + duration : "")
        + (safeReason.isBlank() ? "" : " " + safeReason);
    boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    return ok ? Result.ok("LiteBans command dispatched", action + " for " + player + (timed ? " for " + duration : ""))
        : Result.fail("LiteBans command failed", action + " was rejected by LiteBans.");
  }

  public Result simple(String action, String player) {
    if (!available)
      return Result.fail("LiteBans unavailable", "LiteBans is unavailable.");
    if (!Validation.player(player))
      throw new IllegalArgumentException("Invalid player name or UUID.");
    CapturingCommandSender.Capture capture = CapturingCommandSender.create(Bukkit.getConsoleSender());
    boolean ok = Bukkit.dispatchCommand(capture.sender(), action + " " + player);
    if (!ok) return Result.fail("LiteBans command failed", action + " was rejected by LiteBans.");
    String output = capture.handler().output();
    return Result.ok("LiteBans: " + action,
        output.isBlank() ? "LiteBans returned no readable output for " + player + "." : output);
  }

  /** Returns linked accounts through LiteBans' public Database API, without exposing the IP. */
  public Result alts(String player) {
    if (!available) return Result.fail("LiteBans unavailable", "LiteBans is unavailable.");
    if (!Validation.player(player)) throw new IllegalArgumentException("Invalid player name or UUID.");
    Database database = Database.get();
    UUID target = player.indexOf('-') >= 0 ? UUID.fromString(player) : database.getPlayerUUID(player);
    if (target == null) return Result.fail("Player not found", "LiteBans has no UUID record for " + player + ".");
    String ip = latestIp(target);
    if (ip == null || ip.isBlank()) return Result.ok("LiteBans: alts", "No recorded IP history is available for " + player + ".");
    Collection users = database.getUsersByIP(ip);
    List<String> names = new ArrayList<>();
    for (Object user : users) {
      try {
        UUID uuid = user instanceof UUID value ? value : UUID.fromString(String.valueOf(user));
        String name = database.getPlayerName(uuid);
        if (name != null && !name.isBlank()) names.add(name);
      } catch (IllegalArgumentException ignored) { }
    }
    names.sort(String.CASE_INSENSITIVE_ORDER);
    if (names.isEmpty()) return Result.ok("LiteBans: alts", "No linked accounts found for " + player + ".");
    String detail = "Linked accounts for " + player + " (" + names.size() + "):\n" + String.join(", ", names);
    return Result.ok("LiteBans: alts", detail.length() <= 3_500 ? detail : detail.substring(0, 3_499) + "…");
  }

  private String latestIp(UUID uuid) {
    Player online = Bukkit.getPlayer(uuid);
    if (online != null && online.getAddress() != null && online.getAddress().getAddress() != null)
      return online.getAddress().getAddress().getHostAddress();
    try (PreparedStatement statement = Database.get().prepareStatement(
        "SELECT ip FROM litebans_history WHERE uuid = ? ORDER BY date DESC LIMIT 1")) {
      statement.setString(1, uuid.toString());
      try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getString("ip") : null; }
    } catch (SQLException ignored) { return null; }
  }
}
