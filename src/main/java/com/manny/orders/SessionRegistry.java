package com.manny.orders;

import jakarta.servlet.http.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class SessionRegistry implements HttpSessionListener {

    private static final Map<String, HttpSession> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        SESSIONS.put(event.getSession().getId(), event.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        SESSIONS.remove(event.getSession().getId());
    }

    public static int count() {
        return SESSIONS.size();
    }

    public static int invalidateAllExcept(String currentSessionId) {

        int cleared = 0;

        for (Map.Entry<String, HttpSession> entry : SESSIONS.entrySet()) {

            String id = entry.getKey();

            if (id.equals(currentSessionId)) {
                continue;
            }

            try {
                entry.getValue().invalidate();
                cleared++;
            } catch (Exception ignored) {}
        }

        return cleared;
    }
}
