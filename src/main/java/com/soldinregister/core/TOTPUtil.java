package com.soldinregister.core;

import org.apache.commons.codec.binary.Base32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

public class TOTPUtil {
    private static final SecureRandom RNG = new SecureRandom();

    public static String generateBase32Secret() {
        byte[] buf = new byte[20];
        RNG.nextBytes(buf);
        Base32 b32 = new Base32();
        return b32.encodeToString(buf).replace("=", "");
    }

    public static String buildOtpAuthURL(String issuer, String account, String secret) {
        String label = issuer + ":" + account;
        String url = "otpauth://totp/" + urlEncode(label) + "?secret=" + secret + "&issuer=" + urlEncode(issuer) + "&digits=6&period=30";
        return url;
    }

    public static boolean verifyCode(String secret, int code, int window) {
        long timeIndex = Instant.now().getEpochSecond() / 30L;
        for (int i = -window; i <= window; i++) {
            long calc = totpCode(secret, timeIndex + i);
            if (calc == code) return true;
        }
        return false;
    }

    private static long totpCode(String base32Secret, long timeIndex) {
        Base32 base32 = new Base32();
        byte[] key = base32.decode(base32Secret);
        ByteBuffer bb = ByteBuffer.allocate(8).putLong(timeIndex);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(bb.array());
            int offset = hash[hash.length - 1] & 0xF;
            long binary = ((hash[offset] & 0x7f) << 24) |
                          ((hash[offset + 1] & 0xff) << 16) |
                          ((hash[offset + 2] & 0xff) << 8) |
                          (hash[offset + 3] & 0xff);
            return binary % 1000000;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String urlEncode(String s) {
        return s.replace(" ", "%20");
    }
}
