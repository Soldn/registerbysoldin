package com.soldinregister.core;

import java.util.UUID;

public class Session {
    private final UUID uuid;
    private boolean registered;
    private boolean authenticated;
    private boolean requires2FA;
    private int attemptsLeft;
    private int remainingSeconds;

    public Session(UUID uuid, boolean registered, boolean requires2FA, int attempts, int timeout) {
        this.uuid = uuid;
        this.registered = registered;
        this.requires2FA = requires2FA;
        this.attemptsLeft = attempts;
        this.remainingSeconds = timeout;
    }

    public UUID getUuid() { return uuid; }
    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean r) { this.registered = r; }
    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean a) { this.authenticated = a; }
    public boolean requires2FA() { return requires2FA; }
    public void setRequires2FA(boolean r) { this.requires2FA = r; }
    public int getAttemptsLeft() { return attemptsLeft; }
    public void decAttempt() { attemptsLeft = Math.max(0, attemptsLeft - 1); }
    public int getRemainingSeconds() { return remainingSeconds; }
    public void tickSecond() { remainingSeconds = Math.max(0, remainingSeconds - 1); }
}
