package dev.leafmc.bridge;

import org.bukkit.plugin.java.JavaPlugin;

public final class LeafDiscordBridgePlugin extends JavaPlugin {
    private DiscordBotService bot;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        final Config c;
        try {
            c = Config.load(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("LeafDiscordBridge disabled: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        LiteBansService l = new LiteBansService(c);
        BetterTeamsService t = new BetterTeamsService(c);
        if (c.liteBans() && !l.available())
            getLogger().warning("LiteBans command group disabled: dependency unavailable.");
        if (c.betterTeams() && !t.available())
            getLogger().warning("BetterTeams command group disabled: dependency unavailable.");
        bot = new DiscordBotService(this, c, new AuthorizationService(c), new AuditLogService(getLogger()),
                new MinecraftCommandService(c.maxGive()), l, t);
        try {
            bot.start();
        } catch (Exception e) {
            getLogger().severe(
                    "Discord startup failed (token omitted): " + e.getClass().getSimpleName() + ": " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bot != null)
            bot.stop();
    }
}