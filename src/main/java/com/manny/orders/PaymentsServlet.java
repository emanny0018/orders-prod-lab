package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/payments")
public class PaymentsServlet extends HttpServlet {

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

        try (Connection conn = Db.getConnection()) {

            int total = 0;
            int settled = 0;
            int pending = 0;
            int failed = 0;
            String volume = "0.00";

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) total, " +
                    "SUM(CASE WHEN payment_status='SETTLED' THEN 1 ELSE 0 END) settled, " +
                    "SUM(CASE WHEN payment_status='PENDING' THEN 1 ELSE 0 END) pending, " +
                    "SUM(CASE WHEN payment_status='FAILED' THEN 1 ELSE 0 END) failed, " +
                    "COALESCE(SUM(amount),0) volume FROM payments");
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    total = rs.getInt("total");
                    settled = rs.getInt("settled");
                    pending = rs.getInt("pending");
                    failed = rs.getInt("failed");
                    volume = rs.getBigDecimal("volume").toString();
                }
            }

            Ui.pageStart(out, "Payments & Settlements", "Transaction settlement, failed payment tracking, and order-payment reconciliation.", user);

            out.println("<div class='card-row'>");
            out.println("<div class='card'><h3>Total Payments</h3><div class='value'>" + total + "</div></div>");
            out.println("<div class='card'><h3>Settled</h3><div class='value'>" + settled + "</div></div>");
            out.println("<div class='card'><h3>Pending</h3><div class='value'>" + pending + "</div></div>");
            out.println("<div class='card'><h3>Volume</h3><div class='value'>$" + volume + "</div></div>");
            out.println("</div>");

            out.println("<div class='table-card'><h2>Recent Payment Activity</h2>");
            out.println("<table>");
            out.println("<tr><th>Payment</th><th>Order</th><th>Method</th><th>Amount</th><th>Status</th><th>Transaction Ref</th><th>Created</th></tr>");

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, order_id, payment_method, amount, payment_status, transaction_ref, created_at " +
                    "FROM payments ORDER BY id DESC LIMIT 100");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    String status = rs.getString("payment_status");
                    String color = status.equals("SETTLED") ? "green" : status.equals("PENDING") ? "yellow" : "red";

                    out.println("<tr>");
                    out.println("<td>#" + rs.getInt("id") + "</td>");
                    out.println("<td><a href='order?id=" + rs.getInt("order_id") + "'>#" + rs.getInt("order_id") + "</a></td>");
                    out.println("<td>" + rs.getString("payment_method") + "</td>");
                    out.println("<td>$" + rs.getBigDecimal("amount") + "</td>");
                    out.println("<td>" + Ui.badge(status, color) + "</td>");
                    out.println("<td>" + rs.getString("transaction_ref") + "</td>");
                    out.println("<td>" + rs.getTimestamp("created_at") + "</td>");
                    out.println("</tr>");
                }
            }

            out.println("</table></div>");
            Ui.footer(out);

        } catch (Exception e) {
            resp.setStatus(500);
            out.println("<h1>Payments Service Error</h1>");
            out.println("<pre>" + e.getMessage() + "</pre>");
        }
    }
}
