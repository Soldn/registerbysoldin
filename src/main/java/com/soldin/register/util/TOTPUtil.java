package com.soldin.register.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class TOTPUtil {
    private static final String BASE32_ALPH = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RNG = new SecureRandom();

    public static String generateSecret() {
        byte[] bytes = new byte[20]; RNG.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static boolean verifyCode(String base32Secret, String code, int window) {
        long tm = System.currentTimeMillis() / 1000L / 30L;
        for (int w = -window; w <= window; w++) {
            String gen = generateTOTP(base32Secret, tm + w);
            if (gen.equals(code)) return true;
        }
        return false;
    }

    public static String buildOtpAuthURL(String issuer, String account, String base32Secret) {
        String label = issuer + ":" + account;
        return String.format("otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                urlEncode(label), base32Secret, urlEncode(issuer));
    }

    private static String generateTOTP(String base32Secret, long timeStep) {
        try {
            byte[] key = base32Decode(base32Secret);
            ByteBuffer bb = ByteBuffer.allocate(8); bb.putLong(timeStep); byte[] msg = bb.array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(msg);
            int offset = h[h.length-1] & 0x0F;
            int bin = ((h[offset] & 0x7f) << 24) | ((h[offset+1] & 0xff) << 16) | ((h[offset+2] & 0xff) << 8) | (h[offset+3] & 0xff);
            int otp = bin % 1000000;
            return String.format("%06d", otp);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int i = 0, index = 0, digit;
        int currByte, nextByte;
        while (i < data.length) {
            currByte = data[i] >= 0 ? data[i] : data[i] + 256;
            if (index > 3) {
                if ((i + 1) < data.length) nextByte = data[i + 1] >= 0 ? data[i + 1] : data[i + 1] + 256; else nextByte = 0;
                digit = currByte & (0xFF >> index);
                index = (index + 5) % 8;
                digit = (digit << index) | (nextByte >> (8 - index));
                i++;
            } else {
                digit = (currByte >> (8 - (index + 5))) & 0x1F;
                index = (index + 5) % 8;
                if (index == 0) i++;
            }
            out.append(BASE32_ALPH.charAt(digit));
        }
        return out.toString();
    }

    private static byte[] base32Decode(String s) {
        int i, index, offset, digit;
        byte[] bytes = new byte[s.length() * 5 / 8 + 8];
        int outOffset = 0;
        for (i = 0, index = 0, offset = 0; i < s.length(); i++) {
            int val = BASE32_ALPH.indexOf(Character.toUpperCase(s.charAt(i)));
            if (val < 0) continue;
            digit = val;
            if (index <= 3) {
                index = (index + 5) % 8;
                if (index == 0) {
                    bytes[outOffset++] |= digit;
                } else {
                    bytes[outOffset] |= digit << (8 - index);
                }
            } else {
                index = (index + 5) % 8;
                bytes[outOffset++] |= (digit >>> index);
                if (index != 0) bytes[outOffset] |= digit << (8 - index);
            }
        }
        byte[] out = new byte[outOffset];
        System.arraycopy(bytes, 0, out, 0, outOffset);
        return out;
    }

    private static String urlEncode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}