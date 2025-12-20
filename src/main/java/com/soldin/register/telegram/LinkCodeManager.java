package com.soldin.register.telegram;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LinkCodeManager {

    private static final Map<String, UUID> waiting = new ConcurrentHashMap<>();

    public static String generate(UUID uuid) {
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        waiting.put(code, uuid);
        return code;
    }

    public static UUID consume(String code) {
        return waiting.remove(code);
    }
}
