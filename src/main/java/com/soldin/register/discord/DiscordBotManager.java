package com.soldin.register.discord;

import com.soldin.register.SoldinRegister;
import com.soldin.register.model.DsLink;
import com.soldin.register.model.UserRecord;
import com.soldin.register.storage.Storage;
import com.soldin.register.telegram.LinkCodeManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.util.List;
import java.util.UUID;

/** Discord bot for 2FA confirmations (DM-based commands). */
public class DiscordBotManager {

    private final SoldinRegister plugin;
    private final Storage storage;
    private final YamlConfiguration cfg;
    private final List<Long> admins;
    private JDA jda;

    public DiscordBotManager(SoldinRegister plugin, File dsConfigFile) {
        this.plugin = plugin;
        this.storage = plugin.storage();
        this.cfg = YamlConfiguration.loadConfiguration(dsConfigFile);
        this.admins = cfg.getLongList("admins");
    }

    public boolean isEnabled() {
        String token = cfg.getString("token", "").trim();
        return cfg.getBoolean("enabled", true) && !token.isEmpty();
    }

    public void start() {
        if (!isEnabled()) return;
        String token = cfg.getString("token", "").trim();
        try {
            jda = JDABuilder.createDefault(token)
                    .addEventListeners(new Listener())
                    .build();
            plugin.getLogger().info("[SoldinRegister] Discord 2FA бот запущен.");
        } catch (LoginException e) {
            plugin.getLogger().warning("[SoldinRegister] Discord login failed: " + e.getMessage());
            jda = null;
        }
    }

    public void shutdown() {
        if (jda != null) {
            try { jda.shutdownNow(); } catch (Exception ignored) {}
            jda = null;
        }
    }

    private boolean isAdmin(long dsId) {
        return admins != null && admins.contains(dsId);
    }

    private String buildHelp(long dsId) {
        boolean admin = isAdmin(dsId);
        String header = cfg.getString("messages.help_header", "Команды Discord-бота:");
        List<String> userCmds = cfg.getStringList("help.user_commands");
        if (userCmds.isEmpty()) {
            userCmds = java.util.Arrays.asList(
                    "/start - старт",
                    "/help - помощь",
                    "/2fa - как привязать",
                    "/code <код> - привязать",
                    "/unlink - отвязать"
            );
        }
        List<String> adminCmds = cfg.getStringList("help.admin_commands");
        if (adminCmds.isEmpty()) {
            adminCmds = java.util.Arrays.asList(
                    "/accs - список привязок",
                    "/unlink <ник> - отвязать игрока",
                    "/kick <ник> - кикнуть игрока"
            );
        }
        List<String> serverCmds = cfg.getStringList("help.server_commands");
        if (serverCmds.isEmpty()) {
            serverCmds = java.util.Arrays.asList(
                    "/register <пароль>",
                    "/login <пароль> [код2FA]",
                    "/changepass <старый> <новый>",
                    "/soldinregister 2fa ds"
            );
        }

        StringBuilder sb = new StringBuilder(header).append("\n\n");
        sb.append(cfg.getString("messages.help_user_section", "Пользовательские:")).append("\n");
        for (String s : userCmds) sb.append("• ").append(s).append("\n");
        if (admin) {
            sb.append("\n").append(cfg.getString("messages.help_admin_section", "Админские:")).append("\n");
            for (String s : adminCmds) sb.append("• ").append(s).append("\n");
        }
        sb.append("\n").append(cfg.getString("messages.help_server_section", "Команды на сервере:")).append("\n");
        for (String s : serverCmds) sb.append("• ").append(s).append("\n");
        return sb.toString();
    }

    /** Sends a login confirmation request to the linked Discord user. */
    public void sendLoginRequest(UUID uuid, String playerName, String ip) {
        if (!isEnabled() || jda == null) return;
        DsLink link = storage.getDsLinkByUUID(uuid);
        if (link == null) return;

        String template = cfg.getString("messages.login_request", "⚠️ Попытка входа\nНик: %player%\nIP: %ip%");
        String text = template.replace("%player%", playerName).replace("%ip%", ip);
        User u = jda.getUserById(link.dsId);
        if (u == null) return;
        u.openPrivateChannel().queue(ch ->
                ch.sendMessage(text)
                        .setActionRow(
                                Button.success("allow:" + uuid, cfg.getString("messages.button_allow", "Разрешить")),
                                Button.danger("deny:" + uuid, cfg.getString("messages.button_deny", "Отклонить"))
                        ).queue(),
                err -> {}
        );
    }

    private class Listener extends ListenerAdapter {

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) return;
            if (!event.isFromType(net.dv8tion.jda.api.entities.channel.ChannelType.PRIVATE)) return;

            String text = event.getMessage().getContentRaw().trim();
            long dsId = event.getAuthor().getIdLong();

            if (text.equals("/start")) {
                event.getChannel().sendMessage(cfg.getString("messages.start",
                        "Привязка к Minecraft-аккаунту:\n1) На сервере: /soldinregister 2fa ds\n2) Здесь: /code <код>"))
                        .queue();
                return;
            }

            if (text.equals("/help")) {
                event.getChannel().sendMessage(buildHelp(dsId)).queue();
                return;
            }

            if (text.equals("/2fa")) {
                event.getChannel().sendMessage(cfg.getString("messages.2fa",
                        "Чтобы привязать аккаунт:\n1) На сервере: /soldinregister 2fa ds\n2) Здесь: /code <код>"))
                        .queue();
                return;
            }

            if (text.startsWith("/code ")) {
                String code = text.substring(6).trim();
                UUID uuid = LinkCodeManager.consume(code);
                if (uuid == null) {
                    event.getChannel().sendMessage(cfg.getString("messages.invalid_code", "Неверный или просроченный код.")).queue();
                    return;
                }

                if (cfg.getBoolean("linking.mutually_exclusive", true)) {
                    com.soldin.register.model.TgLink tg = storage.getTgLinkByUUID(uuid);
                    if (tg != null) {
                        event.getChannel().sendMessage(cfg.getString("messages.blocked_by_telegram",
                                "Этот Minecraft уже привязан к Telegram. Discord привязать нельзя.")).queue();
                        return;
                    }
                }

                DsLink existing = storage.getDsLinkByDsId(dsId);
                if (existing != null) {
                    event.getChannel().sendMessage(cfg.getString("messages.already_linked",
                            "Этот Discord уже привязан к аккаунту.")).queue();
                    return;
                }

                storage.saveOrUpdateDsLink(new DsLink(uuid, dsId, null, 0L));
                event.getChannel().sendMessage(cfg.getString("messages.link_ok",
                        "Minecraft-аккаунт успешно привязан.")).queue();
                return;
            }

            if (text.equals("/unlink")) {
                DsLink link = storage.getDsLinkByDsId(dsId);
                if (link == null) {
                    event.getChannel().sendMessage(cfg.getString("messages.no_link", "Нет привязанного аккаунта.")).queue();
                    return;
                }
                storage.deleteDsLinkByDsId(dsId);
                event.getChannel().sendMessage(cfg.getString("messages.unlink_ok", "Аккаунт отвязан.")).queue();
                return;
            }

            if (isAdmin(dsId)) {
                handleAdminCommands(event, text);
            }
        }

        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            String id = event.getComponentId();
            long dsId = event.getUser().getIdLong();
            if (id == null) return;

            if (id.startsWith("allow:")) {
                UUID uuid = UUID.fromString(id.substring("allow:".length()));
                DsLink link = storage.getDsLinkByUUID(uuid);
                if (link != null && link.dsId == dsId) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.approveDiscordLogin(uuid));
                    event.reply(cfg.getString("messages.login_approved", "Вход подтверждён."))
                            .setEphemeral(true).queue();
                } else {
                    event.reply(cfg.getString("messages.not_allowed", "Недоступно."))
                            .setEphemeral(true).queue();
                }
            } else if (id.startsWith("deny:")) {
                UUID uuid = UUID.fromString(id.substring("deny:".length()));
                DsLink link = storage.getDsLinkByUUID(uuid);
                if (link != null && link.dsId == dsId) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.denyDiscordLogin(uuid));
                    event.reply(cfg.getString("messages.login_denied", "Вход отклонён."))
                            .setEphemeral(true).queue();
                } else {
                    event.reply(cfg.getString("messages.not_allowed", "Недоступно."))
                            .setEphemeral(true).queue();
                }
            }
        }

        private void handleAdminCommands(MessageReceivedEvent event, String text) {
            long dsId = event.getAuthor().getIdLong();
            if (text.equals("/accs")) {
                List<DsLink> all = storage.getAllDsLinks();
                if (all.isEmpty()) {
                    event.getChannel().sendMessage(cfg.getString("messages.admin_no_accounts", "Нет привязанных аккаунтов.")).queue();
                    return;
                }
                String header = cfg.getString("messages.admin_accounts_header", "Список привязанных аккаунтов:");
                StringBuilder sb = new StringBuilder(header).append("\n");
                for (DsLink l : all) {
                    UserRecord u = storage.getByUUID(l.uuid);
                    String name = (u != null ? u.name : l.uuid.toString());
                    sb.append(name).append(" → ").append(l.dsId).append("\n");
                }
                event.getChannel().sendMessage(sb.toString()).queue();
                return;
            }

            if (text.startsWith("/unlink ")) {
                String name = text.substring(8).trim();
                UUID uuid = null;
                Player p = Bukkit.getPlayerExact(name);
                if (p != null) uuid = p.getUniqueId();
                else {
                    UserRecord ur = storage.getByName(name);
                    if (ur != null) uuid = ur.uuid;
                }
                if (uuid == null) {
                    event.getChannel().sendMessage(cfg.getString("messages.player_not_found", "Игрок не найден.")).queue();
                    return;
                }
                storage.deleteDsLinkByUUID(uuid);
                String msg = cfg.getString("messages.admin_unlink_ok", "Аккаунт %nick% отвязан.")
                        .replace("%nick%", name);
                event.getChannel().sendMessage(msg).queue();
                return;
            }

            if (text.startsWith("/kick ")) {
                String name = text.substring(6).trim();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayerExact(name);
                    if (p != null && p.isOnline()) {
                        p.kickPlayer(cfg.getString("messages.admin_kick_reason", "Кикнут через Discord админом."));
                    }
                });
                event.getChannel().sendMessage(
                        cfg.getString("messages.admin_kick_try", "Попытка кикнуть %nick%")
                                .replace("%nick%", name)
                ).queue();
            }
        }
    }
}
