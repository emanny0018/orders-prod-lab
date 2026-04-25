package com.manny.orders;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {

        String username = req.getParameter("username");
        String password = req.getParameter("password");

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, username FROM app_users WHERE username = ? AND password = ?")) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    HttpSession session = req.getSession(true);
                    session.setAttribute("user", rs.getString("username"));
                    resp.sendRedirect("orders");
                } else {
                    req.setAttribute("error", "Invalid username or password");
                    req.getRequestDispatcher("login.jsp").forward(req, resp);
                }
            }

        } catch (Exception e) {
            req.setAttribute("error", "Login failed: " + e.getMessage());
            req.getRequestDispatcher("login.jsp").forward(req, resp);
        }
    }
}
