package com.soldinregister.util;

import org.mindrot.jbcrypt.BCrypt;

public class HashUtil {
    public static String hash(String password, int rounds) {
        String salt = BCrypt.gensalt(rounds);
        return BCrypt.hashpw(password, salt);
    }
    public static boolean check(String password, String hash) {
        try {
            return BCrypt.checkpw(password, hash);
        } catch (Exception e) {
            return false;
        }
    }
}
