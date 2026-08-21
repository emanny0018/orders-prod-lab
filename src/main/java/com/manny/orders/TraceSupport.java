package com.manny.orders;

import jakarta.servlet.http.HttpSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.UUID;

public final class TraceSupport {

    private TraceSupport() {}

    public static String newCorrelationId() {
        return "TX-" + UUID.randomUUID();
    }

    public static String correlationId(HttpSession session) {

        if (session == null) {
            return null;
        }

        Object value =
            session.getAttribute("correlation_id");

        return value == null
            ? null
            : value.toString();
    }

    public static String stripeSessionId(HttpSession session) {

        if (session == null) {
            return null;
        }

        Object value =
            session.getAttribute(
                "stripe_checkout_session_id");

        return value == null
            ? null
            : value.toString();
    }

    public static void event(
            String correlationId,
            String eventType,
            String component,
            String detail,
            String httpSessionId,
            String stripeSessionId,
            String requestId,
            Integer suppliedBackendPid,
            Long durationMs,
            boolean success) {

        long jvmThreadId =
            Thread.currentThread().threadId();

        String threadName =
            Thread.currentThread().getName();

        try (Connection conn = Db.getConnection()) {

            Integer backendPid = suppliedBackendPid;

            if (backendPid == null) {

                try (PreparedStatement ps =
                         conn.prepareStatement(
                             "SELECT pg_backend_pid()");
                     ResultSet rs = ps.executeQuery()) {

                    rs.next();
                    backendPid = rs.getInt(1);
                }
            }

            try (PreparedStatement ps =
                     conn.prepareStatement(
                         """
                         INSERT INTO app_transaction_events
                         (
                           correlation_id,
                           event_type,
                           component,
                           detail,
                           http_session_id,
                           stripe_session_id,
                           request_id,
                           tomcat_thread_name,
                           jvm_thread_id,
                           db_backend_pid,
                           duration_ms,
                           success
                         )
                         VALUES
                         (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         """)) {

                ps.setString(1, correlationId);
                ps.setString(2, eventType);
                ps.setString(3, component);
                ps.setString(4, detail);
                ps.setString(5, httpSessionId);
                ps.setString(6, stripeSessionId);
                ps.setString(7, requestId);
                ps.setString(8, threadName);
                ps.setLong(9, jvmThreadId);

                if (backendPid == null) {
                    ps.setNull(10, Types.INTEGER);
                } else {
                    ps.setInt(10, backendPid);
                }

                if (durationMs == null) {
                    ps.setNull(11, Types.BIGINT);
                } else {
                    ps.setLong(11, durationMs);
                }

                ps.setBoolean(12, success);

                ps.executeUpdate();
            }

        } catch (Exception e) {

            System.err.println(
                "TRACE_EVENT_WRITE_FAILED" +
                " correlation=" + correlationId +
                " event=" + eventType +
                " error=" + e.getMessage()
            );
        }
    }
}
