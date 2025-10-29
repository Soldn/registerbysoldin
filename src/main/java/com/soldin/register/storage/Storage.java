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
}