package com.soldinregister.core;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class QRUtil {
    public static void sendQRLink(Plugin plugin, Player p, String otpauthUrl) {
        String template = plugin.getConfig().getString("qr.chart-url", "https://api.qrserver.com/v1/create-qr-code/?size=256x256&data=%s");
        String qr = String.format(template, otpauthUrl.replace(":", "%3A").replace("/", "%2F").replace("?", "%3F").replace("&", "%26").replace("=", "%3D"));
        String msgUrl = plugin.getConfig().getString("messages.twofa-url", "&aURL: &b%url%").replace("%url%", otpauthUrl);
        String msgQr = plugin.getConfig().getString("messages.twofa-qr", "&aQR: &b%qr%").replace("%qr%", qr);
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msgUrl));
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msgQr));
    }
}
