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

    private static final String HELP_TEXT =
            "[♲] Список команд:\n" +
            "- /code [КОД] — Привязать аккаунт к текущей странице\n" +
            "- /list — Список привязанных аккаунтов\n" +
            "- /changepassword [НИК] — Сбросить пароль от аккаунта\n" +
            "- /kick [НИК] — Кикнуть аккаунт с сервера\n" +
            "- /unlink [НИК] — Отвязать аккаунт";

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

        if (text.equals("/start") || text.equals("/help")) {
            String help = HELP_TEXT;

            if (admins.contains(tgId)) {
                help += "\n\n[Админ команды]\n" +
                        "- /accs или /list — Все привязанные аккаунты\n" +
                        "- /unlink [НИК] — Отвязать аккаунт\n" +
                        "- /kick [НИК] — Кикнуть игрока";
                        "- /code [КОД] — Привязать аккаунт к текущей странице\n" +
                        "- /list — Список привязанных аккаунтов\n" +
                        "- /changepassword [НИК] — Сбросить пароль от аккаунта\n" +
                        "- /kick [НИК] — Кикнуть аккаунт с сервера\n" +
                        "- /unlink [НИК] — Отвязать аккаунт";
            }

            bot.execute(new SendMessage(tgId, help));
            return;
        }

        if (text.equals("/2fa")) {
            bot.execute(new SendMessage(tgId,
                    "[♲] Для того, чтобы привязать игровой аккаунт, выполните следующие действия:\n" +
                    "1) В лобби: /tg\n" +
                    "2) Здесь: /code <код>"));
            return;
        }

        if (text.startsWith("/code ")) {
            String code = text.substring(6).trim();
            UUID uuid = LinkCodeManager.consume(code);
            if (uuid == null) {
                bot.execute(new SendMessage(tgId, "[♲] Неверный или просроченный код."));
                return;
            }

            TgLink existing = storage.getTgLinkByTgId(tgId);
            if (existing != null) {
                bot.execute(new SendMessage(tgId,
                        cfg.getString("messages.already_linked", "[♲] Этот Telegram уже привязан к аккаунту.")));
                return;
            }

            TgLink link = new TgLink(uuid, tgId, null, 0L);
            storage.saveOrUpdateTgLink(link);

            bot.execute(new SendMessage(tgId,
                    cfg.getString("messages.already_linked", "[♲] Аккаунт Soldi_ns был привязан к Текущей странице!\nВведите /help для того, чтобы увидеть список команд")));
            return;
        }

        if (text.equals("/unlink")) {
            TgLink link = storage.getTgLinkByTgId(tgId);
            if (link == null) {
                bot.execute(new SendMessage(tgId,
                        cfg.getString("messages.no_link", "[♲] Нет привязанного аккаунта.")));
                return;
            }
            storage.deleteTgLinkByTgId(tgId);
            bot.execute(new SendMessage(tgId,
                    cfg.getString("messages.unlink_ok", "[♲] Аккаунт отвязан.")));
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
            UUID uuid = UUID.fromString(data.substring(6));
            TgLink link = storage.getTgLinkByUUID(uuid);
            if (link != null && link.tgId == tgId) {
                plugin.approveTelegramLogin(uuid);
                bot.execute(new SendMessage(tgId,
                        cfg.getString("messages.login_approved", "[♲] Вход подтверждён.")));
            }
        } else if (data.startsWith("deny:")) {
            UUID uuid = UUID.fromString(data.substring(5));
            TgLink link = storage.getTgLinkByUUID(uuid);
            if (link != null && link.tgId == tgId) {
                plugin.denyTelegramLogin(uuid);
                bot.execute(new SendMessage(tgId,
                        cfg.getString("messages.login_denied", "[♲] Вход отклонён.")));
            }
        }
    }

    public void sendLoginRequest(UUID uuid, String playerName, String ip) {
        if (!isEnabled()) return;
        TgLink link = storage.getTgLinkByUUID(uuid);
        if (link == null) return;

        String text = cfg.getString("messages.login_request",
                "⚠️ Попытка входа\nНик: %player%\nIP: %ip%")
                .replace("%player%", playerName)
                .replace("%ip%", ip);

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(
                new InlineKeyboardButton("Разрешить").callbackData("allow:" + uuid),
                new InlineKeyboardButton("Отклонить").callbackData("deny:" + uuid)
        );

        bot.execute(new SendMessage(link.tgId, text).replyMarkup(kb));
    }

    private void handleAdminCommands(long tgId, String text) {

        if (text.equals("/accs") || text.equals("/list")) {
            List<TgLink> all = storage.getAllTgLinks();
            if (all.isEmpty()) {
                bot.execute(new SendMessage(tgId, "[♲] Нет привязанных аккаунтов."));
                return;
            }

            StringBuilder sb = new StringBuilder("[♲] Привязанные аккаунты:\n");
            for (TgLink l : all) {
                UserRecord u = storage.getByUUID(l.uuid);
                sb.append((u != null ? u.name : l.uuid))
                        .append(" → ")
                        .append(l.tgId)
                        .append("\n");
            }
            bot.execute(new SendMessage(tgId, sb.toString()));
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
                bot.execute(new SendMessage(tgId, "[♲] Игрок не найден."));
                return;
            }

            storage.deleteTgLinkByUUID(uuid);
            bot.execute(new SendMessage(tgId, "[♲] Аккаунт " + name + " отвязан."));
            return;
        }

        if (text.startsWith("/kick ")) {
            String name = text.substring(6).trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayerExact(name);
                if (p != null && p.isOnline()) {
                    p.kickPlayer("[♲] Кикнут через Telegram админом.");
                }
            });
            bot.execute(new SendMessage(tgId, "[♲] Попытка кикнуть " + name));
        }
    }
}
