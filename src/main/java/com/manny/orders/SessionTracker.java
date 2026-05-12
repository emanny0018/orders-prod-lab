package com.manny.orders;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@WebListener
public class SessionTracker implements HttpSessionListener, HttpSessionAttributeListener {

    private static final ConcurrentHashMap<String, HttpSession> sessions = new ConcurrentHashMap<>();
    private static final AtomicInteger cartsCreated = new AtomicInteger(0);

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        sessions.put(se.getSession().getId(), se.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        sessions.remove(se.getSession().getId());
    }

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
        if ("cart".equals(event.getName())) {
            cartsCreated.incrementAndGet();
        }
    }

    public static int activeSessions() {
        return sessions.size();
    }

    public static int sessionsWithCart() {
        int count = 0;
        for (HttpSession session : sessions.values()) {
            try {
                if (session.getAttribute("cart") != null) count++;
            } catch (IllegalStateException ignored) {}
        }
        return count;
    }

    public static int totalCartsCreated() {
        return cartsCreated.get();
    }
}
