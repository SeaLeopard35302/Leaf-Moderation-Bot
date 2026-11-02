package dev.leafmc.bridge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;

/**
 * Immutable, validated configuration. IDs are strings because Discord
 * snowflakes exceed signed int.
 */
public record Config(String token, String guildId, boolean ephemeral, int reasonLimit, int maxGive,
    boolean moderation, boolean teams, boolean inventory, boolean status,
    boolean liteBans, boolean ipBan, boolean reasonRequired, boolean betterTeams,
    boolean audit, String auditChannel, Map<String, Access> access) {
  public record Access(Set<String> users, Set<String> roles) {
  }

  static Config load(FileConfiguration c) {
    String token = c.getString("discord.token", "").trim();
    if (token.isBlank() || token.equals("SET_ME"))
      throw new IllegalArgumentException("discord.token must be configured (it is never logged)");
    int reason = c.getInt("limits.reason-length", 256), give = c.getInt("limits.max-give-amount", 2304);
    if (reason < 1 || reason > 1024 || give < 1 || give > 2304)
      throw new IllegalArgumentException("limits.reason-length must be 1..1024 and max-give-amount 1..2304");
    Map<String, Access> map = new HashMap<>();
    List<String> baseUsers = c.getStringList("security.allowed-user-ids"),
        baseRoles = c.getStringList("security.allowed-role-ids");
    for (String group : List.of("moderation", "investigation", "team-admin", "inventory-admin", "command-admin")) {
      ConfigurationSection s = c.getConfigurationSection("permissions." + group);
      Set<String> users = new HashSet<>(baseUsers), roles = new HashSet<>(baseRoles);
      if (s != null) {
        users.addAll(s.getStringList("allowed-user-ids"));
        roles.addAll(s.getStringList("allowed-role-ids"));
      }
      map.put(group, new Access(Set.copyOf(users), Set.copyOf(roles)));
    }
    return new Config(token, c.getString("discord.guild-id", "").trim(),
        c.getBoolean("responses.ephemeral-by-default", true), reason, give,
        c.getBoolean("commands.moderation", true), c.getBoolean("commands.teams", true),
        c.getBoolean("commands.inventory", true), c.getBoolean("commands.server-status", true),
        c.getBoolean("integrations.litebans.enabled", true), c.getBoolean("integrations.litebans.allow-ipban", false),
        c.getBoolean("integrations.litebans.reason-required", true),
        c.getBoolean("integrations.betterteams.enabled", true),
        c.getBoolean("audit.enabled", true), c.getString("audit.discord-channel-id", "").trim(), Map.copyOf(map));
  }
}
