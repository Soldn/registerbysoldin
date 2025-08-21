package com.soldinregister.util;

import org.apache.commons.codec.binary.Base32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class TOTPUtil {
    public static String generateSecret() {
        byte[] random = new byte[20];
        for (int i = 0; i < random.length; i++) random[i] = (byte) (Math.random() * 256);
        Base32 b32 = new Base32();
        return b32.encodeToString(random).replace("=", "");
    }

    public static boolean verifyCode(String secret, int code, long timeMillis) {
        long t = (timeMillis / 1000L) / 30L;
        for (int i = -1; i <= 1; i++) {
            int totp = generateTOTP(secret, t + i);
            if (totp == code) return true;
        }
        return false;
    }

    public static int generateTOTP(String secret, long timeWindow) {
        try {
            Base32 b32 = new Base32();
            byte[] key = b32.decode(secret);
            ByteBuffer bb = ByteBuffer.allocate(8);
            bb.putLong(timeWindow);
            byte[] data = bb.array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7f) << 24) |
                         ((hash[offset + 1] & 0xff) << 16) |
                         ((hash[offset + 2] & 0xff) << 8) |
                         (hash[offset + 3] & 0xff);
            return binary % 1_000_000;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String buildOtpAuthUrl(String issuer, String account, String secret) {
        String label = issuer + ":" + account;
        String params = "secret=" + secret + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        return "otpauth://totp/" + URLEncoder.encode(label, StandardCharsets.UTF_8) + "?" + params;
    }
}
