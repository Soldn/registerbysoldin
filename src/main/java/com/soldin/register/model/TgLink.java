package com.soldin.register.model;

import java.util.UUID;

public class TgLink {
    public UUID uuid;
    public long tgId;
    public String lastIp;
    public long lastConfirm;

    public TgLink(UUID uuid, long tgId, String lastIp, long lastConfirm) {
        this.uuid = uuid;
        this.tgId = tgId;
        this.lastIp = lastIp;
        this.lastConfirm = lastConfirm;
    }
}
