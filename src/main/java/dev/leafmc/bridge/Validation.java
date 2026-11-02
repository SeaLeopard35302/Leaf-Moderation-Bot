package dev.leafmc.bridge;

import java.time.Duration;
import java.util.*;
import java.util.regex.*;

public final class Validation {
  private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$"),
      UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
      IP = Pattern.compile("^(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)$"),
      DURATION = Pattern.compile("^(\\d{1,6})([smhdw])$");
  private static final Pattern ITEM = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_/.-]+$");

  public static boolean player(String p) {
    if (p == null)
      return false;
    if (NAME.matcher(p).matches())
      return true;
    try {
      java.util.UUID.fromString(p);
      return true;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  public static boolean playerOrIpv4(String p) {
    return player(p) || (p != null && IP.matcher(p).matches());
  }

  public static Optional<Duration> duration(String d) {
    if (d == null)
      return Optional.empty();
    Matcher m = DURATION.matcher(d.toLowerCase(Locale.ROOT));
    if (!m.matches())
      return Optional.empty();
    long n = Long.parseLong(m.group(1));
    long seconds = switch (m.group(2)) {
      case "s" -> n;
      case "m" -> n * 60;
      case "h" -> n * 3600;
      case "d" -> n * 86400;
      default -> n * 604800;
    };
    return seconds > 0 && seconds <= 315360000L ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
  }

  public static boolean item(String id) {
    return id != null && ITEM.matcher(id).matches();
  }

  public static String reason(String r, int max, boolean required) {
    String clean = r == null ? "" : r.strip();
    if ((required && clean.isBlank()) || clean.length() > max || clean.indexOf('\n') >= 0 || clean.indexOf('\r') >= 0)
      throw new IllegalArgumentException(
          required ? "A single-line reason is required and must fit the configured limit."
              : "Reason must be single-line and fit the configured limit.");
    return clean;
  }

  private Validation() {
  }
}
