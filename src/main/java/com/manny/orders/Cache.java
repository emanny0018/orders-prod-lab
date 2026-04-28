package com.manny.orders;

import redis.clients.jedis.Jedis;

public class Cache {
    private static final String HOST = "localhost";
    private static final int PORT = 6379;

    public static Jedis getClient() {
        return new Jedis(HOST, PORT);
    }
}
