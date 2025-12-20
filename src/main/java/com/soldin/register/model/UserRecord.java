package com.soldin.register.model;

import java.util.UUID;

public class UserRecord {
    public UUID uuid;
    public String name;
    public String hash;
    public String salt;
    public int iterations;
    public String ip;
    public long registeredAt;
    public long lastLogin;
    public String twoFASecret; // base32

    public UserRecord(UUID uuid, String name, String hash, String salt, int iterations, String ip, long registeredAt, long lastLogin, String twoFASecret) {
        this.uuid = uuid; this.name = name; this.hash = hash; this.salt = salt; this.iterations = iterations; this.ip = ip; this.registeredAt = registeredAt; this.lastLogin = lastLogin; this.twoFASecret = twoFASecret;
    }
}