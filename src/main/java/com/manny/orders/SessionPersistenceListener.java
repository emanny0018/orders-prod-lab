package com.manny.orders;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

@WebListener
public class SessionPersistenceListener
        implements HttpSessionListener {

    @Override
    public void sessionCreated(HttpSessionEvent event) {

        HttpSession session = event.getSession();

        try (Connection conn = Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     INSERT INTO app_sessions
                     (
                       session_id,
                       created_at,
                       last_seen_at,
                       status
                     )
                     VALUES (?, ?, CURRENT_TIMESTAMP, 'ACTIVE')

                     ON CONFLICT (session_id)
                     DO UPDATE SET
                       status = 'ACTIVE',
                       ended_at = NULL,
                       last_seen_at = CURRENT_TIMESTAMP
                     """)) {

            ps.setString(1, session.getId());
            ps.setTimestamp(
                2,
                new Timestamp(session.getCreationTime()));

            ps.executeUpdate();

        } catch (Exception e) {

            System.err.println(
                "SESSION_CREATE_PERSIST_FAILED session=" +
                session.getId() +
                " error=" +
                e.getMessage());
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {

        HttpSession session = event.getSession();

        try (Connection conn = Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     UPDATE app_sessions
                     SET
                       status = 'DESTROYED',
                       ended_at = CURRENT_TIMESTAMP,
                       last_seen_at = CURRENT_TIMESTAMP
                     WHERE session_id = ?
                     """)) {

            ps.setString(1, session.getId());
            ps.executeUpdate();

        } catch (Exception e) {

            System.err.println(
                "SESSION_DESTROY_PERSIST_FAILED session=" +
                session.getId() +
                " error=" +
                e.getMessage());
        }
    }
}
