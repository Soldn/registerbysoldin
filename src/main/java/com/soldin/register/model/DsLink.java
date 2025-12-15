package com.soldin.register.model;

import java.util.UUID;

/** Discord link for 2FA confirmations. */
public class DsLink {
    public final UUID uuid;
    public final long dsId;
    public String lastIp;
    public long lastConfirm;

    public DsLink(UUID uuid, long dsId, String lastIp, long lastConfirm) {
        this.uuid = uuid;
        this.dsId = dsId;
        this.lastIp = lastIp;
        this.lastConfirm = lastConfirm;
    }
}
