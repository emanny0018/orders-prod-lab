package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import redis.clients.jedis.Jedis;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.sql.*;

@WebServlet("/app-metrics")
public class AppMetricsServlet extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest req,
            HttpServletResponse resp) throws IOException {

        resp.setContentType(
            "text/plain; version=0.0.4; charset=UTF-8");

        PrintWriter out = resp.getWriter();

        long totalSessions = 0;
        long activeSessions = 0;
        long totalRequests = 0;
        long errors5xx = 0;
        long errors4xx = 0;
        long stripeWebhooks = 0;
        double avgDuration = 0;
        int postgresBackendPid = 0;

        int dbUp = 0;

        try (Connection conn = Db.getConnection()) {

            dbUp = 1;

            try (Statement st = conn.createStatement();
                 ResultSet rs =
                     st.executeQuery(
                         """
                         SELECT
                           (SELECT COUNT(*)
                              FROM app_sessions),
                           (SELECT COUNT(*)
                              FROM app_sessions
                              WHERE status = 'ACTIVE'),
                           (SELECT COUNT(*)
                              FROM app_request_history),
                           (SELECT COUNT(*)
                              FROM app_request_history
                              WHERE http_status >= 500),
                           (SELECT COUNT(*)
                              FROM app_request_history
                              WHERE http_status >= 400
                                AND http_status < 500),
                           (SELECT COUNT(*)
                              FROM app_request_history
                              WHERE uri LIKE '%/stripe/webhook'),
                           (SELECT COALESCE(AVG(duration_ms),0)
                              FROM app_request_history
                              WHERE request_time >
                                    CURRENT_TIMESTAMP -
                                    INTERVAL '5 minutes'),
                           pg_backend_pid()
                         """)) {

                rs.next();

                totalSessions = rs.getLong(1);
                activeSessions = rs.getLong(2);
                totalRequests = rs.getLong(3);
                errors5xx = rs.getLong(4);
                errors4xx = rs.getLong(5);
                stripeWebhooks = rs.getLong(6);
                avgDuration = rs.getDouble(7);
                postgresBackendPid = rs.getInt(8);
            }

        } catch (Exception e) {
            dbUp = 0;
        }

        int redisUp = 0;
        long redisKeys = 0;
        int catalogCached = 0;
        int dashboardCached = 0;

        try (Jedis jedis = new Jedis("localhost", 6379)) {

            if ("PONG".equalsIgnoreCase(jedis.ping())) {
                redisUp = 1;
            }

            redisKeys = jedis.dbSize();

            catalogCached =
                jedis.exists("catalog:products:v1") ? 1 : 0;

            dashboardCached =
                jedis.exists("orders:dashboard:summary") ? 1 : 0;

        } catch (Exception ignored) {
            redisUp = 0;
        }

        int rabbitUp = 0;
        long rabbitReady = 0;
        long rabbitConsumers = 0;

        try {

            ConnectionFactory factory = new ConnectionFactory();

            factory.setHost("localhost");
            factory.setPort(5672);
            factory.setUsername("guest");
            factory.setPassword("guest");

            try (com.rabbitmq.client.Connection connection =
                     factory.newConnection();
                 Channel channel = connection.createChannel()) {

                AMQP.Queue.DeclareOk queue =
                    channel.queueDeclarePassive("orders.events");

                rabbitReady = queue.getMessageCount();
                rabbitConsumers = queue.getConsumerCount();

                rabbitUp = 1;
            }

        } catch (Exception ignored) {
            rabbitUp = 0;
        }

        Runtime runtime = Runtime.getRuntime();

        long heapUsed =
            runtime.totalMemory() - runtime.freeMemory();

        long heapCommitted = runtime.totalMemory();
        long heapMax = runtime.maxMemory();

        int threadCount =
            ManagementFactory.getThreadMXBean()
                .getThreadCount();

        out.println("orders_app_up 1");

        out.println("orders_db_up " + dbUp);
        out.println(
            "orders_db_backend_pid " +
            postgresBackendPid);

        out.println(
            "orders_sessions_total " +
            totalSessions);

        out.println(
            "orders_sessions_active " +
            activeSessions);

        out.println(
            "orders_http_requests_total " +
            totalRequests);

        out.println(
            "orders_http_5xx_total " +
            errors5xx);

        out.println(
            "orders_http_4xx_total " +
            errors4xx);

        out.println(
            "orders_http_request_avg_duration_ms " +
            avgDuration);

        out.println(
            "orders_stripe_webhook_requests_total " +
            stripeWebhooks);

        out.println(
            "orders_observability_db_write_failures_total " +
            ObservabilityFilter.getTrackingFailures());

        out.println("orders_redis_up " + redisUp);
        out.println("orders_redis_keys " + redisKeys);

        out.println(
            "orders_redis_catalog_cached " +
            catalogCached);

        out.println(
            "orders_redis_dashboard_cached " +
            dashboardCached);

        out.println(
            "orders_rabbitmq_up " +
            rabbitUp);

        out.println(
            "orders_rabbitmq_orders_events_ready " +
            rabbitReady);

        out.println(
            "orders_rabbitmq_orders_events_consumers " +
            rabbitConsumers);

        out.println(
            "orders_jvm_threads " +
            threadCount);

        out.println(
            "orders_jvm_heap_used_bytes " +
            heapUsed);

        out.println(
            "orders_jvm_heap_committed_bytes " +
            heapCommitted);

        out.println(
            "orders_jvm_heap_max_bytes " +
            heapMax);
    }
}
