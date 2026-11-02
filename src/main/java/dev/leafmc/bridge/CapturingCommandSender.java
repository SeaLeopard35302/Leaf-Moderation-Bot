package dev.leafmc.bridge;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Captures command messages while delegating permissions and identity to the real console. */
final class CapturingCommandSender implements InvocationHandler {
  private static final int MAX_LINES = 25;
  private static final int MAX_CHARS = 3_500;
  private final CommandSender delegate;
  private final List<String> messages = new ArrayList<>();

  private CapturingCommandSender(CommandSender delegate) { this.delegate = delegate; }

  static Capture create(CommandSender delegate) {
    CapturingCommandSender handler = new CapturingCommandSender(delegate);
    CommandSender proxy = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
        new Class<?>[] { CommandSender.class }, handler);
    return new Capture(proxy, handler);
  }

  @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (method.getName().equals("sendMessage") && args != null) {
      for (Object argument : args) {
        if (argument instanceof String message) add(message);
        else if (argument instanceof String[] array) for (String message : array) add(message);
      }
      return null;
    }
    return method.invoke(delegate, args);
  }

  private void add(String message) {
    if (messages.size() >= MAX_LINES || message == null) return;
    String clean = ChatColor.stripColor(message).replaceAll("[\\r\\n]+", " ").strip();
    if (!clean.isBlank()) messages.add(clean);
  }

  String output() {
    String joined = String.join("\n", messages);
    return joined.length() <= MAX_CHARS ? joined : joined.substring(0, MAX_CHARS - 1) + "…";
  }

  record Capture(CommandSender sender, CapturingCommandSender handler) {}
}
