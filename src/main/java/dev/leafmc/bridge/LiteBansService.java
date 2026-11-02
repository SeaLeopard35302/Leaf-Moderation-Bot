package dev.leafmc.bridge;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

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
}
