
package com.soldinregister.core;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public class TOTPUtil {
    private static final SecureRandom rnd = new SecureRandom();

    public static String generateSecret() {
        byte[] buf = new byte[10];
        rnd.nextBytes(buf);
        return new Base32().encodeToString(buf).replace("=", "");
    }

    public static String buildOtpAuthURL(String issuer, String account, String secret) {
        String label = issuer + ":" + account;
        return "otpauth://totp/" + urlEncode(label) + "?secret=" + secret + "&issuer=" + urlEncode(issuer) + "&digits=6&period=30";
    }

    public static boolean verifyCode(String secret, int code, long timeMillis) {
        long t = (timeMillis / 1000L) / 30L;
        for (int i = -1; i <= 1; i++) {
            long hash = generate(secret, t + i);
            if (hash == code) return true;
        }
        return false;
    }

    private static int generate(String secret, long t) {
        try {
            Base32 base32 = new Base32();
            byte[] bytes = base32.decode(secret);
            String hexKey = Hex.encodeHexString(bytes);
            byte[] key = hexStr2Bytes(hexKey);
            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (t & 0xFF);
                t >>= 8;
            }
            SecretKeySpec signKey = new SecretKeySpec(key, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signKey);
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0xF;
            long truncatedHash = 0;
            for (int i = 0; i < 4; ++i) {
                truncatedHash <<= 8;
                truncatedHash |= (hash[offset + i] & 0xFF);
            }
            truncatedHash &= 0x7FFFFFFF;
            truncatedHash %= 1000000;
            return (int) truncatedHash;
        } catch (Exception e) {
            return -1;
        }
    }

    private static byte[] hexStr2Bytes(String hex) {
        byte[] bArray = new byte[hex.length() / 2];
        for (int i = 0; i < bArray.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(hex.substring(index, index + 2), 16);
            bArray[i] = (byte) v;
        }
        return bArray;
    }

    private static String urlEncode(String s) {
        return s.replace(" ", "%20");
    }

    public static String buildQRUrl(String otpAuthURL) {
        // Клиент откроет картинку в браузере (кликабельная ссылка в чате)
        String data = otpAuthURL.replace(":", "%3A").replace("/", "%2F").replace("?", "%3F").replace("=", "%3D").replace("&", "%26");
        return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + data;
    }
}
