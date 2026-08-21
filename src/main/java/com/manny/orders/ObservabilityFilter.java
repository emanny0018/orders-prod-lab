package com.manny.orders;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@WebFilter("/*")
public class ObservabilityFilter implements Filter {

    private static final AtomicLong trackingFailures =
        new AtomicLong();

    public static long getTrackingFailures() {
        return trackingFailures.get();
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest req) ||
            !(response instanceof HttpServletResponse resp)) {

            chain.doFilter(request, response);
            return;
        }

        long start = System.nanoTime();
        Throwable failure = null;

        try {

            chain.doFilter(request, response);

        } catch (IOException |
                 ServletException |
                 RuntimeException e) {

            failure = e;
            throw e;

        } finally {

            long durationMs =
                (System.nanoTime() - start)
                    / 1_000_000;

            String uri =
                req.getRequestURI();

            if (!uri.endsWith("/app-metrics")) {

                recordRequest(
                    req,
                    resp,
                    uri,
                    durationMs,
                    failure
                );
            }
        }
    }

    private void recordRequest(
            HttpServletRequest req,
            HttpServletResponse resp,
            String uri,
            long durationMs,
            Throwable failure) {

        HttpSession session =
            req.getSession(false);

        String sessionId =
            session == null
                ? null
                : session.getId();

        String username = null;

        if (session != null &&
            session.getAttribute("user") != null) {

            username =
                session
                    .getAttribute("user")
                    .toString();
        }

        /*
         * Normal browser requests get correlation
         * from HttpSession.
         *
         * Async/server-to-server requests such as Stripe
         * can attach correlation to the request itself.
         */
        String correlationId = null;

        Object requestCorrelation =
            req.getAttribute("correlation_id");

        if (requestCorrelation != null) {

            correlationId =
                requestCorrelation.toString();

        } else {

            correlationId =
                TraceSupport.correlationId(session);
        }

        String stripeSessionId = null;

        Object requestStripeSession =
            req.getAttribute("stripe_session_id");

        if (requestStripeSession != null) {

            stripeSessionId =
                requestStripeSession.toString();

        } else {

            stripeSessionId =
                TraceSupport.stripeSessionId(session);
        }

        String requestId =
            UUID.randomUUID().toString();

        String threadName =
            Thread.currentThread().getName();

        long jvmThreadId =
            Thread.currentThread().threadId();

        try (Connection conn =
                 Db.getConnection()) {

            conn.setAutoCommit(false);

            int backendPid;

            try (PreparedStatement ps =
                     conn.prepareStatement(
                         "SELECT pg_backend_pid()");
                 ResultSet rs =
                     ps.executeQuery()) {

                rs.next();

                backendPid =
                    rs.getInt(1);
            }

            /*
             * One row = one HTTP request.
             */
            try (PreparedStatement ps =
                     conn.prepareStatement(
                         """
                         INSERT INTO app_request_history
                         (
                           request_id,
                           session_id,
                           username,
                           method,
                           uri,
                           http_status,
                           duration_ms,
                           thread_name,
                           remote_addr,
                           db_backend_pid,
                           error_class,
                           correlation_id,
                           stripe_session_id,
                           jvm_thread_id
                         )
                         VALUES
                         (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         """)) {

                ps.setString(1, requestId);
                ps.setString(2, sessionId);
                ps.setString(3, username);
                ps.setString(4, req.getMethod());
                ps.setString(5, uri);
                ps.setInt(6, resp.getStatus());
                ps.setLong(7, durationMs);
                ps.setString(8, threadName);
                ps.setString(9, req.getRemoteAddr());
                ps.setInt(10, backendPid);

                ps.setString(
                    11,
                    failure == null
                        ? null
                        : failure
                            .getClass()
                            .getName()
                );

                ps.setString(
                    12,
                    correlationId);

                ps.setString(
                    13,
                    stripeSessionId);

                ps.setLong(
                    14,
                    jvmThreadId);

                ps.executeUpdate();
            }

            /*
             * One row = persistent HTTP session summary.
             */
            if (session != null) {

                try (PreparedStatement ps =
                         conn.prepareStatement(
                             """
                             INSERT INTO app_sessions
                             (
                               session_id,
                               username,
                               created_at,
                               last_seen_at,
                               status,
                               request_count,
                               last_method,
                               last_uri,
                               last_http_status,
                               last_duration_ms,
                               last_thread,
                               last_db_backend_pid,
                               correlation_id,
                               stripe_session_id
                             )
                             VALUES
                             (
                               ?, ?, ?, CURRENT_TIMESTAMP,
                               'ACTIVE', 1, ?, ?, ?, ?, ?, ?,
                               ?, ?
                             )

                             ON CONFLICT (session_id)
                             DO UPDATE SET

                               username =
                                 COALESCE(
                                   EXCLUDED.username,
                                   app_sessions.username
                                 ),

                               last_seen_at =
                                 CURRENT_TIMESTAMP,

                               status =
                                 'ACTIVE',

                               request_count =
                                 app_sessions.request_count + 1,

                               last_method =
                                 EXCLUDED.last_method,

                               last_uri =
                                 EXCLUDED.last_uri,

                               last_http_status =
                                 EXCLUDED.last_http_status,

                               last_duration_ms =
                                 EXCLUDED.last_duration_ms,

                               last_thread =
                                 EXCLUDED.last_thread,

                               last_db_backend_pid =
                                 EXCLUDED.last_db_backend_pid,

                               correlation_id =
                                 COALESCE(
                                   EXCLUDED.correlation_id,
                                   app_sessions.correlation_id
                                 ),

                               stripe_session_id =
                                 COALESCE(
                                   EXCLUDED.stripe_session_id,
                                   app_sessions.stripe_session_id
                                 )
                             """)) {

                    ps.setString(1, sessionId);
                    ps.setString(2, username);

                    ps.setTimestamp(
                        3,
                        new Timestamp(
                            session.getCreationTime()
                        )
                    );

                    ps.setString(
                        4,
                        req.getMethod());

                    ps.setString(
                        5,
                        uri);

                    ps.setInt(
                        6,
                        resp.getStatus());

                    ps.setLong(
                        7,
                        durationMs);

                    ps.setString(
                        8,
                        threadName);

                    ps.setInt(
                        9,
                        backendPid);

                    ps.setString(
                        10,
                        correlationId);

                    ps.setString(
                        11,
                        stripeSessionId);

                    ps.executeUpdate();
                }
            }

            conn.commit();

        } catch (Exception e) {

            trackingFailures.incrementAndGet();

            System.err.println(
                "OBSERVABILITY_DB_WRITE_FAILED" +
                " uri=" + uri +
                " thread=" + threadName +
                " correlation=" + correlationId +
                " error=" + e.getMessage()
            );
        }
    }
}
