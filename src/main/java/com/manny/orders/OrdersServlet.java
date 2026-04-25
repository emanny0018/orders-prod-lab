package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/orders")
public class OrdersServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("login.jsp");
            return;
        }

        String user = session.getAttribute("user").toString();
        resp.setContentType("text/html;charset=UTF-8");

        try (Connection conn = Db.getConnection();
             PreparedStatement summary = conn.prepareStatement(
                 "SELECT COUNT(*) total_orders, " +
                 "SUM(CASE WHEN status='PAID' THEN 1 ELSE 0 END) paid_orders, " +
                 "SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END) pending_orders, " +
                 "SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) failed_orders, " +
                 "COALESCE(SUM(price * quantity),0) revenue FROM orders");
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, customer_name, item_name, quantity, price, status, created_at " +
                 "FROM orders ORDER BY id DESC LIMIT 50")) {

            ResultSet sr = summary.executeQuery();
            sr.next();

            ResultSet rs = ps.executeQuery();

            resp.getWriter().println("""
<!DOCTYPE html>
<html>
<head>
<title>Manny Orders Platform</title>
<style>
body { margin:0; font-family:Arial,sans-serif; background:#f3f4f6; color:#111827; }
.sidebar { position:fixed; top:0; left:0; width:230px; height:100vh; background:#111827; color:white; padding:25px 18px; }
.sidebar h2 { margin-top:0; }
.sidebar a { display:block; color:#d1d5db; text-decoration:none; padding:12px 8px; border-radius:8px; margin:6px 0; }
.sidebar a:hover { background:#1f2937; color:white; }
.main { margin-left:270px; padding:30px; }
.topbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:25px; }
.card-row { display:grid; grid-template-columns:repeat(4,1fr); gap:18px; margin-bottom:25px; }
.card { background:white; padding:22px; border-radius:14px; box-shadow:0 4px 14px rgba(0,0,0,.08); }
.card h3 { margin:0; color:#6b7280; font-size:14px; }
.card .value { font-size:30px; font-weight:bold; margin-top:8px; }
.table-card { background:white; padding:22px; border-radius:14px; box-shadow:0 4px 14px rgba(0,0,0,.08); }
table { border-collapse:collapse; width:100%; }
th { background:#1f6feb; color:white; text-align:left; padding:12px; }
td { padding:12px; border-bottom:1px solid #e5e7eb; }
tr:hover { background:#f9fafb; }
.badge { padding:5px 10px; border-radius:999px; font-size:12px; font-weight:bold; }
.PAID { background:#dcfce7; color:#166534; }
.PENDING { background:#fef9c3; color:#854d0e; }
.FAILED { background:#fee2e2; color:#991b1b; }
.logout { color:#1f6feb; text-decoration:none; font-weight:bold; }
</style>
</head>
<body>
<div class="sidebar">
  <h2>Manny Platform</h2>
  <a href="orders">Orders Dashboard</a>
  <a href="health">Service Health</a>
  <a href="#">Inventory</a>
  <a href="#">Payments</a>
  <a href="#">Reports</a>
  <a href="logout">Logout</a>
</div>
<div class="main">
""");

            resp.getWriter().println("<div class='topbar'><div><h1>Orders Dashboard</h1><p>Welcome, <b>" + user + "</b>. Live data from PostgreSQL.</p></div><a class='logout' href='logout'>Logout</a></div>");

            resp.getWriter().println("<div class='card-row'>");
            resp.getWriter().println("<div class='card'><h3>Total Orders</h3><div class='value'>" + sr.getInt("total_orders") + "</div></div>");
            resp.getWriter().println("<div class='card'><h3>Paid Orders</h3><div class='value'>" + sr.getInt("paid_orders") + "</div></div>");
            resp.getWriter().println("<div class='card'><h3>Pending Orders</h3><div class='value'>" + sr.getInt("pending_orders") + "</div></div>");
            resp.getWriter().println("<div class='card'><h3>Revenue</h3><div class='value'>$" + sr.getBigDecimal("revenue") + "</div></div>");
            resp.getWriter().println("</div>");

            resp.getWriter().println("<div class='table-card'><h2>Recent Orders</h2>");
            resp.getWriter().println("<table>");
            resp.getWriter().println("<tr><th>ID</th><th>Customer</th><th>Item</th><th>Qty</th><th>Price</th><th>Status</th><th>Created</th></tr>");

            while (rs.next()) {
                String status = rs.getString("status");
                resp.getWriter().println("<tr>");
                resp.getWriter().println("<td>#" + rs.getInt("id") + "</td>");
                resp.getWriter().println("<td>" + rs.getString("customer_name") + "</td>");
                resp.getWriter().println("<td>" + rs.getString("item_name") + "</td>");
                resp.getWriter().println("<td>" + rs.getInt("quantity") + "</td>");
                resp.getWriter().println("<td>$" + rs.getBigDecimal("price") + "</td>");
                resp.getWriter().println("<td><span class='badge " + status + "'>" + status + "</span></td>");
                resp.getWriter().println("<td>" + rs.getTimestamp("created_at") + "</td>");
                resp.getWriter().println("</tr>");
            }

            resp.getWriter().println("</table></div></div></body></html>");

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().println("<h1>Orders Service Error</h1>");
            resp.getWriter().println("<pre>" + e.getMessage() + "</pre>");
        }
    }
}
