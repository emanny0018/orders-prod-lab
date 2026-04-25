package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;

@WebServlet("/health")
public class HealthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Connection conn = Db.getConnection()) {
            if (conn.isValid(2)) {
                resp.setStatus(200);
                resp.getWriter().write("OK");
            } else {
                resp.setStatus(500);
                resp.getWriter().write("DB_NOT_VALID");
            }
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("DB_ERROR: " + e.getMessage());
        }
    }
}
