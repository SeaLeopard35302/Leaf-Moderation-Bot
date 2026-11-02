package dev.leafmc.bridge;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.*;
import org.bukkit.*;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.*;

public final class DiscordBotService extends ListenerAdapter {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final Config c;
    private final AuthorizationService auth;
    private final AuditLogService audit;
    private final MinecraftCommandService minecraft;
    private final LiteBansService lite;
    private final BetterTeamsService teams;
    private final Set<Long> handled = ConcurrentHashMap.newKeySet();
    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LeafDiscordBridge-JDA");
        t.setDaemon(true);
        return t;
    });
    private volatile JDA jda;

    public DiscordBotService(org.bukkit.plugin.java.JavaPlugin p, Config c, AuthorizationService a,
            AuditLogService audit, MinecraftCommandService mc, LiteBansService l, BetterTeamsService t) {
        plugin = p;
        this.c = c;
        auth = a;
        this.audit = audit;
        minecraft = mc;
        lite = l;
        teams = t;
    }

    public void start() {
        connectionExecutor.submit(() -> {
            try {
                jda = JDABuilder.createDefault(c.token()).addEventListeners(this).build();
                jda.awaitReady();
                register();
                plugin.getLogger().info("Discord bot connected; slash commands registration submitted.");
            } catch (Exception e) {
                plugin.getLogger().severe("Discord connection failed (token omitted): " + e.getClass().getSimpleName()
                        + ": " + safe(e.getMessage()));
            }
        });
    }

    private List<CommandData> commands() {
        List<CommandData> x = new ArrayList<>();
        if (c.moderation() && lite.available())
            for (String n : List.of("ban", "tempban", "mute", "tempmute", "kick", "warn", "unban", "unmute", "unwarn",
                    "history", "checkban", "checkmute", "staffhistory", "alts", "dupeip")) {
                SlashCommandData d = Commands.slash(n, "LeafDiscordBridge " + n).addOption(OptionType.STRING, "player",
                        "Minecraft player or UUID", true);
                if (n.equals("tempban") || n.equals("tempmute"))
                    d.addOption(OptionType.STRING, "duration", "e.g. 7d", true);
                if (List.of("ban", "tempban", "mute", "tempmute", "kick", "warn").contains(n))
                    d.addOption(OptionType.STRING, "reason", "Reason", c.reasonRequired());
                x.add(d);
            }
        if (c.moderation() && lite.available() && c.ipBan()) {
            SlashCommandData ipban = Commands.slash("ipban", "Run LiteBans IP ban")
                    .addOption(OptionType.STRING, "player", "Minecraft player or IP", true);
            // Discord requires required options to precede every optional option.
            if (c.reasonRequired())
                ipban.addOption(OptionType.STRING, "reason", "Reason", true);
            ipban.addOption(OptionType.STRING, "duration", "Optional e.g. 7d", false);
            if (!c.reasonRequired())
                ipban.addOption(OptionType.STRING, "reason", "Reason", false);
            x.add(ipban);
        }
        if (c.teams() && teams.available()) {
            x.add(Commands.slash("team-create", "Create a BetterTeams team")
                    .addOption(OptionType.STRING, "name", "Team name", true)
                    .addOption(OptionType.STRING, "owner", "Online owner", true));
            x.add(Commands.slash("team-info", "Show team information").addOption(OptionType.STRING, "name", "Team name",
                    true));
            x.add(Commands.slash("team-disband", "Disband a team")
                    .addOption(OptionType.STRING, "name", "Team name", true)
                    .addOption(OptionType.BOOLEAN, "confirm", "Must be true", true));
            x.add(Commands.slash("team-addplayer", "Add an online player")
                    .addOption(OptionType.STRING, "name", "Team name", true)
                    .addOption(OptionType.STRING, "player", "Online player", true));
            x.add(Commands.slash("team-setowner", "Assign an existing member as owner")
                    .addOption(OptionType.STRING, "name", "Team name", true)
                    .addOption(OptionType.STRING, "owner", "Online member", true));
        }
        if (c.inventory()) {
            x.add(Commands.slash("mc-give", "Run vanilla minecraft:give")
                    .addOption(OptionType.STRING, "player", "Online player", true)
                    .addOption(OptionType.STRING, "item", "minecraft:item_id", true)
                    .addOption(OptionType.INTEGER, "amount", "1 to configured maximum", true));
            x.add(Commands.slash("mc-clear", "Run vanilla minecraft:clear")
                    .addOption(OptionType.STRING, "player", "Online player", true)
                    .addOption(OptionType.STRING, "item", "Optional minecraft:item_id", false));
        }
        if (c.status())
            x.add(Commands.slash("server-status", "Show server status"));
        return x;
    }

    private void register() {
        if (c.guildId().isBlank())
            jda.updateCommands().addCommands(commands()).queue(null, e -> plugin.getLogger()
                    .warning("Global command registration failed: " + e.getClass().getSimpleName()));
        else {
            Guild guild = jda.getGuildById(c.guildId());
            if (guild == null) {
                plugin.getLogger()
                        .warning("Guild command registration skipped: configured guild is unavailable to this bot.");
                return;
            }
            guild.updateCommands().addCommands(commands()).queue(null, e -> plugin.getLogger()
                    .warning("Guild command registration failed: " + e.getClass().getSimpleName()));
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        if (!handled.add(e.getIdLong()))
            return;
        if (handled.size() > 10000)
            handled.clear();
        String group = group(e.getName());
        if (!auth.allowed(group, e.getMember())) {
            finish(e, Result.fail("Unauthorized", "You are not authorized for this command."), group);
            return;
        }
        e.deferReply(c.ephemeral()).queue(v -> Bukkit.getScheduler().runTask(plugin, () -> {
            Result r;
            try {
                r = execute(e);
            } catch (Exception ex) {
                r = Result.fail("Request rejected", safe(ex.getMessage()));
            }
            finish(e, r, group);
        }));
    }

    private String group(String n) {
        if (n.startsWith("team-"))
            return "team-admin";
        if (n.startsWith("mc-"))
            return "inventory-admin";
        if (n.equals("alts") || n.equals("dupeip"))
            return "investigation";
        return "moderation";
    }

    private Result execute(SlashCommandInteractionEvent e) {
        String n = e.getName(), p = opt(e, "player");
        if (n.equals("mc-give"))
            return minecraft.give(p, opt(e, "item"), (int) e.getOption("amount").getAsLong());
        if (n.equals("mc-clear"))
            return minecraft.clear(p, opt(e, "item"));
        if (n.equals("server-status"))
            return Result.ok("Server status", Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers()
                    + " players; " + Bukkit.getVersion());
        if (n.equals("team-create"))
            return teams.create(opt(e, "name"), opt(e, "owner"));
        if (n.equals("team-info"))
            return teams.info(opt(e, "name"));
        if (n.equals("team-disband")) {
            if (!Boolean.parseBoolean(opt(e, "confirm")))
                throw new IllegalArgumentException("Set confirm:true to disband a team.");
            return teams.disband(opt(e, "name"));
        }
        if (n.equals("team-addplayer"))
            return teams.add(opt(e, "name"), p);
        if (n.equals("team-setowner"))
            return teams.setOwner(opt(e, "name"), opt(e, "owner"));
        if (List.of("history", "checkban", "checkmute", "staffhistory", "alts", "dupeip", "unban", "unmute", "unwarn")
                .contains(n))
            return lite.simple(n, p);
        return lite.punish(n, p, opt(e, "duration"), opt(e, "reason"));
    }

    private void finish(SlashCommandInteractionEvent e, Result r, String group) {
        audit.record(e.getUser().getName(), e.getUser().getId(), e.getName(), arguments(e),
                r.ok() ? "success" : "failure");
        EmbedBuilder b = new EmbedBuilder().setTitle(r.title()).setDescription(safe(r.detail()))
                .setColor(r.ok() ? Color.GREEN : Color.RED).addField("Moderator", e.getUser().getName(), true)
                .addField("Permission", group, true);
        if (e.isAcknowledged())
            e.getHook().editOriginalEmbeds(b.build()).queue();
        else
            e.replyEmbeds(b.build()).setEphemeral(c.ephemeral()).queue();
    }

    private static String opt(SlashCommandInteractionEvent e, String n) {
        return e.getOption(n) == null ? "" : e.getOption(n).getAsString();
    }

    private static String arguments(SlashCommandInteractionEvent e) {
        return e.getOptions().stream().map(o -> o.getName() + "=" + o.getAsString()).reduce("", (a, b) -> a + " " + b);
    }

    private static String safe(String s) {
        if (s == null)
            return "Operation failed.";
        return s.replace("@", "＠").replace("`", "ˋ").substring(0, Math.min(1800, s.length()));
    }

    public void stop() {
        connectionExecutor.shutdownNow();
        JDA x = jda;
        if (x != null)
            x.shutdown();
    }
}
