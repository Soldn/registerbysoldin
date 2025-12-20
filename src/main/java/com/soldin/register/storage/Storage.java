package com.soldin.register.storage;

import com.soldin.register.model.UserRecord;
import java.util.UUID;

public interface Storage {
    void connect() throws Exception;
    void init() throws Exception;
    void close() throws Exception;

    UserRecord getByUUID(UUID uuid);
    UserRecord getByName(String name);
    void create(UserRecord r);
    void update(UserRecord r);
    void delete(UUID uuid);
    int countByIP(String ip);
    // Telegram 2FA
    com.soldin.register.model.TgLink getTgLinkByUUID(UUID uuid);
    com.soldin.register.model.TgLink getTgLinkByTgId(long tgId);
    java.util.List<com.soldin.register.model.TgLink> getAllTgLinks();
    void saveOrUpdateTgLink(com.soldin.register.model.TgLink link);
    void deleteTgLinkByUUID(UUID uuid);
    void deleteTgLinkByTgId(long tgId);

}