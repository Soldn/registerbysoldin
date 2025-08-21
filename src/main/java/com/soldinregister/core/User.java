
package com.soldinregister.core;

public class User {
    public final String name;
    public final String passwordHash;
    public final String ip;
    public final String twofaSecret;

    public User(String name, String passwordHash, String ip, String twofaSecret) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.ip = ip;
        this.twofaSecret = twofaSecret;
    }
}
