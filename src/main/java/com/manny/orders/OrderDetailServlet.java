package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/order")
public class OrderDetailServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("login.jsp");
            return;
        }

        String idParam = req.getParameter("id");

        if (idParam == null || idParam.isBlank()) {
            resp.setStatus(400);
            resp.getWriter().println("Missing order id");
            return;
        }

        resp.setContentType("text/html;charset=UTF-8");

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, customer_name, item_name, quantity, price, status, created_at " +
                     "FROM orders WHERE id = ?")) {

            ps.setInt(1, Integer.parseInt(idParam));

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(404);
                    resp.getWriter().println("Order not found");
                    return;
                }

                resp.getWriter().println("""
<!DOCTYPE html>
<html>
<head>
<title>Order Detail</title>
<style>
body { margin:0; font-family:Arial,sans-serif; background:#f3f4f6; color:#111827; }
.container { max-width:900px; margin:40px auto; background:white; padding:30px; border-radius:14px; box-shadow:0 4px 14px rgba(0,0,0,.08); }
.row { display:flex; justify-content:space-between; padding:14px 0; border-bottom:1px solid #e5e7eb; }
.label { color:#6b7280; font-weight:bold; }
.value { font-size:18px; }
.badge { padding:6px 12px; border-radius:999px; font-weight:bold; }
.PAID { background:#dcfce7; color:#166534; }
.PENDING { background:#fef9c3; color:#854d0e; }
.FAILED { background:#fee2e2; color:#991b1b; }
a { color:#1f6feb; text-decoration:none; font-weight:bold; }
</style>
</head>
<body>
<div class="container">
""");

                String status = rs.getString("status");

                resp.getWriter().println("<p><a href='orders'>← Back to Orders</a></p>");
                resp.getWriter().println("<h1>Order #" + rs.getInt("id") + "</h1>");

                resp.getWriter().println("<div class='row'><div class='label'>Customer</div><div class='value'>" + rs.getString("customer_name") + "</div></div>");
                resp.getWriter().println("<div class='row'><div class='label'>Item</div><div class='value'>" + rs.getString("item_name") + "</div></div>");
                resp.getWriter().println("<div class='row'><div class='label'>Quantity</div><div class='value'>" + rs.getInt("quantity") + "</div></div>");
                resp.getWriter().println("<div class='row'><div class='label'>Price</div><div class='value'>$" + rs.getBigDecimal("price") + "</div></div>");
                resp.getWriter().println("<div class='row'><div class='label'>Status</div><div class='value'><span class='badge " + status + "'>" + status + "</span></div></div>");
                resp.getWriter().println("<div class='row'><div class='label'>Created At</div><div class='value'>" + rs.getTimestamp("created_at") + "</div></div>");

                resp.getWriter().println("</div></body></html>");
            }

        } catch (NumberFormatException e) {
            resp.setStatus(400);
            resp.getWriter().println("Invalid order id");
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().println("Order detail error: " + e.getMessage());
        }
    }
}
