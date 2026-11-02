# LeafDiscordBridge

LeafDiscordBridge is a Java 21 bytecode plugin for LeafMC 26.2 (the supplied Leaf server reports Minecraft protocol 776 and requires Java 25 at runtime). It compiles against the pinned Paper API build `1.21.11-R0.1-20260511.115010-91`, the compatible API line for the installed Leaf server. It provides a default-deny Discord slash-command bridge for LiteBans 2.18.8, BetterTeams 5.1.7, and fixed vanilla inventory commands.

## Build and install

The build is Maven because this workspace has Maven installed. Build with `mvn package`; the deployable shaded JAR is `target/leaf-discord-bridge-1.0.0.jar`. Leaf itself must run with Java 25. This project emits Java 21 bytecode, which Java 25 runs.

Copy the JAR to `plugins/`, start once, then edit `plugins/LeafDiscordBridge/config.yml`. Put the bot token only there, set a Discord guild ID for immediate development command registration, and fill allowlisted Discord IDs. Do not commit that generated configuration.

Create a Discord application and bot in the Discord Developer Portal. Invite it with `bot` and `applications.commands` scopes. The bot needs no privileged gateway intents; it only receives interactions. Give the bot channel permissions to view and respond in the intended staff channel.

## Commands

| Slash command | Options | Authorization | Integration |
|---|---|---|---|
| `/ban`, `/mute`, `/kick`, `/warn` | `player`, `reason` | moderation | LiteBans |
| `/ipban` | `player`, optional `duration`, `reason` | moderation | LiteBans, only when `allow-ipban: true` |
| `/tempban`, `/tempmute` | `player`, `duration`, `reason` | moderation | LiteBans |
| `/unban`, `/unmute`, `/unwarn` | `player` | moderation | LiteBans |
| `/history`, `/checkban`, `/checkmute`, `/staffhistory` | `player` | moderation | LiteBans |
| `/alts`, `/dupeip` | `player` | investigation | LiteBans |
| `/team-create` | `name`, `owner` | team-admin | BetterTeams |
| `/team-info` | `name` | team-admin | BetterTeams |
| `/team-disband` | `name`, `confirm:true` | team-admin | BetterTeams |
| `/team-addplayer` | `name`, `player` | team-admin | BetterTeams |
| `/team-setowner` | `name`, `owner` | team-admin | BetterTeams |
| `/mc-give` | `player`, `item`, `amount` | inventory-admin | Vanilla `minecraft:give` |
| `/mc-clear` | `player`, optional `item` | inventory-admin | Vanilla `minecraft:clear` |
| `/server-status` | none | moderation | Leaf/Bukkit |

`/ipban` remains absent until `integrations.litebans.allow-ipban` is explicitly enabled. `/unpunish` is not in LiteBans 2.18.8’s supplied command descriptor. The bridge never exposes arbitrary console commands; `unrestricted-console` remains permanently unsupported.

## Integration notes and security

The LiteBans JAR has no stable documented public API included for these operations, so this plugin invokes only its verified labels and descriptor syntax as console after token validation. It never impersonates a player or gives a Discord user OP. Bukkit’s boolean dispatch result is all LiteBans exposes through that route, so the Discord reply says “dispatched” rather than claiming the database mutation was independently verified.

BetterTeams 5.1.7 was inspected locally. Team creation uses `Team.getTeamManager().createNewTeam(name, onlinePlayer)` and verifies that the resulting member rank is `OWNER`. Owners and added players must be online because the installed public creation API takes `Player`.

Every interaction checks its allowlist before deferral and execution. Results are ephemeral by default. Reasons are one line and length limited; player, duration, and item inputs are token validated. Native inventory commands always use the explicit `minecraft:` label, so EssentialsX aliases cannot replace them. Audit records go to the server log with Discord identity, command, sanitized arguments, timestamp, and outcome.

## Live-server checklist

1. Use a non-production Discord guild and test registration after changing `guild-id`.
2. Verify an unauthorized user receives no operation and an audit failure.
3. Test each enabled LiteBans command with a disposable account; confirm LiteBans history and expiry in its database.
4. Test BetterTeams creation, ownership, owner transfer, and disband with an online player.
5. Test `mc-give` and `mc-clear` with EssentialsX installed; server logs should show the `minecraft:` labels.
6. Confirm the bot shuts down cleanly during `/stop` and that no token appears in the log.

## Troubleshooting

`discord.token must be configured` means set the token in the generated plugin configuration. A missing LiteBans or BetterTeams plugin disables only that command group. Guild registration failure normally indicates an incorrect guild ID or bot not installed in that guild. If Leaf rejects the JAR, verify the server is running Java 25 and that the shaded artifact, rather than Maven’s unshaded original artifact, was copied.
