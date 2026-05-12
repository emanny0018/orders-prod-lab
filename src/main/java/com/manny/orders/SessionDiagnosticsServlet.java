package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/sessions")
public class SessionDiagnosticsServlet extends HttpServlet {

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

        Ui.pageStart(out, "Session Diagnostics", "Tomcat HTTP session and cart retention visibility.", user);

        out.println("<div class='card-row'>");
        out.println("<div class='card'><h3>Active Sessions</h3><div class='value'>" + SessionTracker.activeSessions() + "</div></div>");
        out.println("<div class='card'><h3>Sessions With Cart</h3><div class='value'>" + SessionTracker.sessionsWithCart() + "</div></div>");
        out.println("<div class='card'><h3>Total Carts Created</h3><div class='value'>" + SessionTracker.totalCartsCreated() + "</div></div>");
        out.println("</div>");

        out.println("<div class='table-card'>");
        out.println("<h2>What This Means</h2>");
        out.println("<p>Active sessions are Tomcat JSESSIONID sessions currently retained in JVM memory.</p>");
        out.println("<p>Sessions with cart means user carts are being held in HttpSession memory.</p>");
        out.println("</div>");

        Ui.footer(out);
    }
}
