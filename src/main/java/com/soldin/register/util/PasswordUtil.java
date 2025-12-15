package com.soldin.register.util;

import com.soldin.register.model.UserRecord;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordUtil {
    public static class HashPack { public String hash; public String salt; public int iterations; public HashPack(String h,String s,int i){hash=h;salt=s;iterations=i;} }

    private static final SecureRandom RNG = new SecureRandom();

    public static HashPack hashPassword(String password) {
        int iterations = 120000;
        byte[] salt = new byte[16]; RNG.nextBytes(salt);
        byte[] dk = pbkdf2(password.toCharArray(), salt, iterations, 256);
        return new HashPack(Base64.getEncoder().encodeToString(dk), Base64.getEncoder().encodeToString(salt), iterations);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static boolean verify(String password, UserRecord u) {
        byte[] salt = Base64.getDecoder().decode(u.salt);
        byte[] dk = pbkdf2(password.toCharArray(), salt, u.iterations, 256);
        String hash = Base64.getEncoder().encodeToString(dk);
        return constantTimeEq(hash, u.hash);
    }

    private static boolean constantTimeEq(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0; for (int i=0;i<a.length();i++) r |= a.charAt(i)^b.charAt(i); return r==0;
    }
}