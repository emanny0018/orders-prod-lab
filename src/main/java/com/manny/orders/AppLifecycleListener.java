package com.manny.orders;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;
import java.sql.PreparedStatement;

@WebListener
public class AppLifecycleListener
        implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {

        /*
         * Any session still marked ACTIVE from an older JVM
         * cannot still be a live HttpSession after a WAR/JVM restart.
         */
        try (Connection conn = Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     UPDATE app_sessions
                     SET
                       status = 'ABANDONED_ON_RESTART',
                       ended_at = CURRENT_TIMESTAMP
                     WHERE status = 'ACTIVE'
                     """)) {

            int updated = ps.executeUpdate();

            System.out.println(
                "OBSERVABILITY_STARTUP stale_sessions_closed=" +
                updated);

        } catch (Exception e) {

            System.err.println(
                "OBSERVABILITY_STARTUP_FAILED " +
                e.getMessage());
        }
    }
}
