package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.Connection;

@WebServlet("/health")
public class HealthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");

        try (Connection conn = Db.getConnection()) {
            if (conn.isValid(2)) {
                resp.setStatus(200);
                resp.getWriter().write("OK");
            } else {
                resp.setStatus(500);
                resp.getWriter().write("DATABASE_NOT_VALID");
            }
        } catch (Exception e) {
            resp.setStatus(500);

            String msg = e.getMessage();

            if (msg != null && msg.contains("Connection to localhost:5432 refused")) {
                resp.getWriter().write("DATABASE_CONNECTIVITY_FAILED: PostgreSQL is not accepting connections on localhost:5432");
            } else if (msg != null && msg.toLowerCase().contains("password authentication failed")) {
                resp.getWriter().write("DATABASE_AUTH_FAILED: Invalid DB username or password");
            } else if (msg != null && msg.toLowerCase().contains("timeout")) {
                resp.getWriter().write("DATABASE_TIMEOUT: DB connection timed out");
            } else {
                resp.getWriter().write("DATABASE_ERROR: " + msg);
            }
        }
    }
}
