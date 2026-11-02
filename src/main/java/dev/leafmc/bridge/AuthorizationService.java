package dev.leafmc.bridge;

import net.dv8tion.jda.api.entities.Member;
import java.util.Set;

public final class AuthorizationService {
  private final Config config;

  public AuthorizationService(Config c) {
    config = c;
  }

  public boolean allowed(String group, String userId, Set<String> roles) {
    Config.Access access = config.access().get(group);
    if (access == null)
      return false;
    return access.users().contains(userId) || roles.stream().anyMatch(access.roles()::contains);
  }

  public boolean allowed(String group, Member member) {
    return member != null && allowed(group, member.getId(),
        member.getRoles().stream().map(r -> r.getId()).collect(java.util.stream.Collectors.toSet()));
  }
}