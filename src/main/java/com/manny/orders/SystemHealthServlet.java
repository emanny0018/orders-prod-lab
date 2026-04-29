package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;

@WebServlet("/health")
public class SystemHealthServlet extends HttpServlet {

    private String checkPostgres() {
        try (Connection conn = Db.getConnection()) {
            return Ui.badge("UP", "green");
        } catch (Exception e) {
            return Ui.badge("DOWN", "red");
        }
    }

    private String checkRedis() {
        try (Jedis jedis = Cache.getClient()) {
            jedis.ping();
            return Ui.badge("UP", "green");
        } catch (Exception e) {
            return Ui.badge("DOWN", "red");
        }
    }

    private String checkRabbit() {
        try {
            QueuePublisher.testConnection();
            return Ui.badge("UP", "green");
        } catch (Exception e) {
            return Ui.badge("DOWN", "red");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("login.jsp");
            return;
        }

        String user = session.getAttribute("user").toString();
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        Ui.pageStart(out, "System Health", "Platform dependency status", user);

        out.println("<div class='card-row'>");
        out.println("<div class='card'><h3>PostgreSQL</h3><div class='value'>" + checkPostgres() + "</div></div>");
        out.println("<div class='card'><h3>Redis</h3><div class='value'>" + checkRedis() + "</div></div>");
        out.println("<div class='card'><h3>RabbitMQ</h3><div class='value'>" + checkRabbit() + "</div></div>");
        out.println("<div class='card'><h3>Application</h3><div class='value'>" + Ui.badge("UP", "green") + "</div></div>");
        out.println("</div>");

        Ui.footer(out);
    }
}
