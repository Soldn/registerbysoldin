package com.soldin.register.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.soldin.register.SoldinRegister;
import com.soldin.register.model.TgLink;
import com.soldin.register.model.UserRecord;
import com.soldin.register.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class TelegramBotManager {

    private final SoldinRegister plugin;
    private final Storage storage;
    private final TelegramBot bot;
    private final YamlConfiguration cfg;
    private final List<Long> admins;

    private String buildHelp(long tgId) {
        boolean isAdmin = admins.contains(tgId);
        String header = cfg.getString("messages.help_header", "Команды Telegram-бота:");
        java.util.List<String> userCmds = cfg.getStringList("help.user_commands");
        if (userCmds.isEmpty()) {
            userCmds = java.util.Arrays.asList(
                    "/start - старт",
                    "/help - помощь",
                    "/2fa - как привязать",
                    "/code <код> - привязать",
                    "/unlink - отвязать"
            );
        }
        java.util.List<String> adminCmds = cfg.getStringList("help.admin_commands");
        if (adminCmds.isEmpty()) {
            adminCmds = java.util.Arrays.asList(
                    "/accs - список привязок",
                    "/unlink <ник> - отвязать игрока",
                    "/kick <ник> - кикнуть игрока"
            );
        }
        java.util.List<String> serverCmds = cfg.getStringList("help.server_commands");
        if (serverCmds.isEmpty()) {
            serverCmds = java.util.Arrays.asList(
                    "/register <пароль>",
                    "/login <пароль> [код2FA]",
                    "/changepass <старый> <новый>",
                    "/soldinregister 2fa tg"
            );
        }

        StringBuilder sb = new StringBuilder(header).append("\n\n");
        sb.append(cfg.getString("messages.help_user_section", "Пользовательские:")).append("\n");
        for (String s : userCmds) sb.append("• ").append(s).append("\n");

        if (isAdmin) {
            sb.append("\n").append(cfg.getString("messages.help_admin_section", "Админские:")).append("\n");
            for (String s : adminCmds) sb.append("• ").append(s).append("\n");
        }

        sb.append("\n").append(cfg.getString("messages.help_server_section", "Команды на сервере:")).append("\n");
        for (String s : serverCmds) sb.append("• ").append(s).append("\n");
        return sb.toString();
    }

    public TelegramBotManager(SoldinRegister plugin, File tgConfigFile) {
        this.plugin = plugin;
        this.storage = plugin.storage();
        this.cfg = YamlConfiguration.loadConfiguration(tgConfigFile);

        String token = cfg.getString("token", "").trim();
        if (token.isEmpty()) {
            plugin.getLogger().warning("[SoldinRegister] tg.yml: token пустой, Telegram-бот не будет запущен.");
            this.bot = null;
            this.admins = java.util.Collections.emptyList();
            return;
        }

        this.bot = new TelegramBot(token);
        this.admins = cfg.getLongList("admins");
    }

    public boolean isEnabled() {
        return bot != null && cfg.getBoolean("enabled", true);
    }

    public void start() {
        if (!isEnabled()) return;

        bot.setUpdatesListener(updates -> {
            for (Update u : updates) {
                try {
                    handleUpdate(u);
                } catch (Exception e) {
                    plugin.getLogger().warning("[SoldinRegister] Telegram update error: " + e.getMessage());
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        plugin.getLogger().info("[SoldinRegister] Telegram 2FA бот запущен.");
    }

    public void shutdown() {
        if (bot != null) {
            try {
                bot.removeGetUpdatesListener();
            } catch (Exception ignored) {}
        }
    }

    private void handleUpdate(Update u) {
        if (u.message() != null) {
            handleMessage(u.message());
        }
        if (u.callbackQuery() != null) {
            handleCallback(u.callbackQuery());
        }
    }

    private void handleMessage(Message msg) {
        if (msg.text() == null) return;
        String text = msg.text().trim();
        long tgId = msg.from().id();

        if (text.equals("/start")) {
            bot.execute(new SendMessage(tgId,
                    cfg.getString("messages.start",
                            "Привязка к Minecraft-аккаунту:\n1) На сервере: /soldinregister 2fa tg\n2) Здесь: /code <код>")));
            return;
        }

        if (text.equals("/help")) {
            bot.execute(new SendMessage(tgId, buildHelp(tgId)));
            return;
        }

        if (text.equals("/2fa")) {
            bot.execute(new SendMessage(tgId,
                    cfg.getString("messages.2fa",
                            "Чтобы привязать аккаунт:\n1) На сервере: /soldinregister 2fa tg\n2) Здесь: /code <код>")));
            return;
        }

        if (text.startsWith("/code ")) {
            String code = text.substring(6).trim();
            UUID uuid = LinkCodeManager.consume(code);
            if (uuid == null) {
                bot.execute(new SendMessage(tgId,
                        cfg.getString("messages.invalid_code", "Неверный или просроченный код.")));
                return;
            }

            // mutual exclusivity with Discord (configurable)
            if (cfg.getBoolean("linking.mutually_exclusive", true)) {
                com.soldin.register.model.DsLink ds = storage.getDsLinkByUUID(uuid);
                if (ds != null) {
                    bot.execute(new SendMessage(tgId,
                            cfg.getString("messages.blocked_by_discord", "Этот Minecraft уже привязан к Discord. Telegram привязать нельзя.")));
                    return;
                }
            }

            TgLink existing = storage.getTgLinkByTgId(tgId);
            if (existing != null) {
                bot.execute(new SendMessage(tgId,
                        cfg.getString("messages.already_linked", "Этот Telegram уже привязан к аккаунту.")));
                return;
            }

            TgLink link = new TgLink(uuid, tgId, null, 0L);
            storage.saveOrUpdateTgLink(link);

            String ok = cfg.getString("messages.link_ok", "Minecraft-аккаунт успешно привязан.");
            bot.execute(new SendMessage(tgId, ok));
            return;
        }

        if (text.equals("/unlink")) {
            TgLink link = storage.getTgLinkByTgId(tgId);
            if (link == null) {
                bot.execute(new SendMessage(tgId,
                        cfg.getString("messages.no_link", "Нет привязанного аккаунта.")));
                return;
            }
            storage.deleteTgLinkByTgId(tgId);
            bot.execute(new SendMessage(tgId,
                    cfg.getString("messages.unlink_ok", "Аккаунт отвязан.")));
            return;
        }

        if (admins.contains(tgId)) {
            handleAdminCommands(tgId, text);
        }
    }

    private void handleCallback(CallbackQuery cb) {
        String data = cb.data();
        long tgId = cb.from().id();
        if (data == null) return;

        if (data.startsWith("allow:")) {
            String su = data.substring("allow:".length());
            UUID uuid = UUID.fromString(su);
            TgLink link = storage.getTgLinkByUUID(uuid);
            if (link != null && link.tgId == tgId) {
                // Bukkit API calls must run on main thread
                Bukkit.getScheduler().runTask(plugin, () -> plugin.approveTelegramLogin(uuid));
                String ok = cfg.getString("messages.login_approved", "Вход подтверждён.");
                bot.execute(new SendMessage(tgId, ok));
            }
        } else if (data.startsWith("deny:")) {
            String su = data.substring("deny:".length());
            UUID uuid = UUID.fromString(su);
            TgLink link = storage.getTgLinkByUUID(uuid);
            if (link != null && link.tgId == tgId) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.denyTelegramLogin(uuid));
                String msg = cfg.getString("messages.login_denied", "Вход отклонён.");
                bot.execute(new SendMessage(tgId, msg));
            }
        }
    }

    public void sendLoginRequest(UUID uuid, String playerName, String ip) {
        if (!isEnabled()) return;
        TgLink link = storage.getTgLinkByUUID(uuid);
        if (link == null) return;

        String template = cfg.getString("messages.login_request",
                "⚠️ Попытка входа\nНик: %player%\nIP: %ip%");
        String text = template.replace("%player%", playerName).replace("%ip%", ip);

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(
                new InlineKeyboardButton(cfg.getString("messages.button_allow", "Разрешить")).callbackData("allow:" + uuid),
                new InlineKeyboardButton(cfg.getString("messages.button_deny", "Отклонить")).callbackData("deny:" + uuid)
        );

        bot.execute(new SendMessage(link.tgId, text).replyMarkup(kb));
    }

    private void handleAdminCommands(long tgId, String text) {
        if (text.equals("/accs")) {
            List<TgLink> all = storage.getAllTgLinks();
            if (all.isEmpty()) {
                bot.execute(new SendMessage(tgId,
                        cfg.getString("messages.admin_no_accounts", "Нет привязанных аккаунтов.")));
                return;
            }
            String header = cfg.getString("messages.admin_accounts_header", "Список привязанных аккаунтов:");
            StringBuilder sb = new StringBuilder(header).append("\n");
            for (TgLink l : all) {
                UserRecord u = storage.getByUUID(l.uuid);
                String name = (u != null ? u.name : l.uuid.toString());
                sb.append(name).append(" → ").append(l.tgId).append("\n");
            }
            bot.execute(new SendMessage(tgId, sb.toString()));
            return;
        }

        if (text.startsWith("/unlink ")) {
            String name = text.substring(8).trim();
            UUID uuid = null;
            Player p = Bukkit.getPlayerExact(name);
            if (p != null) {
                uuid = p.getUniqueId();
            } else {
                UserRecord ur = storage.getByName(name);
                if (ur != null) uuid = ur.uuid;
            }
            if (uuid == null) {
                bot.execute(new SendMessage(tgId,
                        cfg.getString("messages.player_not_found", "Игрок не найден.")));
                return;
            }
            storage.deleteTgLinkByUUID(uuid);
            String msg = cfg.getString("messages.admin_unlink_ok", "Аккаунт %nick% отвязан.")
                    .replace("%nick%", name);
            bot.execute(new SendMessage(tgId, msg));
            return;
        }

        if (text.startsWith("/kick ")) {
            String name = text.substring(6).trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayerExact(name);
                if (p != null && p.isOnline()) {
                    p.kickPlayer(cfg.getString("messages.admin_kick_reason", "Кикнут через Telegram админом."));
                }
            });
            bot.execute(new SendMessage(tgId,
                    cfg.getString("messages.admin_kick_try", "Попытка кикнуть %nick%")
                            .replace("%nick%", name)));
        }
    }
}
