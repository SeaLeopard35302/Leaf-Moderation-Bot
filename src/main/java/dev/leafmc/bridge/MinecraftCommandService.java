package dev.leafmc.bridge;

import org.bukkit.*;
import org.bukkit.entity.Player;

/**
 * Executes only fixed, namespaced vanilla labels. No supplied command label
 * reaches Bukkit.
 */
public final class MinecraftCommandService {
  private final int maxGive;

  public MinecraftCommandService(int maxGive) {
    this.maxGive = maxGive;
  }

  private Player online(String target) {
    if (!Validation.player(target))
      throw new IllegalArgumentException("Target must be a Minecraft username or UUID.");
    Player p = Bukkit.getPlayer(target);
    if (p == null)
      throw new IllegalArgumentException("Target player must be online.");
    return p;
  }

  private String material(String item) {
    if (!Validation.item(item))
      throw new IllegalArgumentException("Item must be a namespaced Minecraft item ID.");
    String key = item.substring(item.indexOf(':') + 1).toUpperCase(java.util.Locale.ROOT);
    Material m = Material.matchMaterial(key);
    if (m == null || !m.isItem())
      throw new IllegalArgumentException("Unknown or non-item material: " + item);
    return item;
  }

  public Result give(String player, String item, int amount) {
    Player p = online(player);
    String safe = material(item);
    if (amount < 1 || amount > maxGive)
      throw new IllegalArgumentException("Amount must be 1.." + maxGive + ".");
    boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
        "minecraft:give " + p.getName() + " " + safe + " " + amount);
    return dispatched ? Result.ok("Items given", amount + " × " + safe + " to " + p.getName())
        : Result.fail("Vanilla command failed", "minecraft:give reported failure.");
  }

  public Result clear(String player, String item) {
    Player p = online(player);
    String command = "minecraft:clear " + p.getName();
    if (item != null && !item.isBlank())
      command += " " + material(item);
    boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    return dispatched
        ? Result.ok("Inventory cleared",
            item == null || item.isBlank() ? "Cleared " + p.getName() : "Cleared " + item + " from " + p.getName())
        : Result.fail("Vanilla command failed", "minecraft:clear reported failure.");
  }

  public String giveCommand(String p, String i, int n) {
    return "minecraft:give " + p + " " + i + " " + n;
  }

  public String clearCommand(String p, String i) {
    return "minecraft:clear " + p + (i == null || i.isBlank() ? "" : " " + i);
  }
}
