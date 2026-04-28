package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/reports")
public class ReportsServlet extends HttpServlet {

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
            Ui.pageStart(out, "Reports & Analytics", "Sales performance, payment status, inventory risk, and operational KPIs.", user);
            out.println("<div class='table-card'><h2>Sales Reconciliation Export</h2>");
            out.println("<p>Export orders joined with payment settlement data for reconciliation review.</p>");
            out.println("<a class='btn' href='reports/sales-reconciliation-export'>Download Sales Reconciliation CSV</a>");
            out.println("</div>");


            out.println("<div class='card-row'>");

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(price * quantity),0) revenue, COUNT(*) orders_count FROM orders");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                out.println("<div class='card'><h3>Gross Revenue</h3><div class='value'>$" + rs.getBigDecimal("revenue") + "</div></div>");
                out.println("<div class='card'><h3>Total Orders</h3><div class='value'>" + rs.getInt("orders_count") + "</div></div>");
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) failed_payments FROM payments WHERE payment_status='FAILED'");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                out.println("<div class='card'><h3>Failed Payments</h3><div class='value'>" + rs.getInt("failed_payments") + "</div></div>");
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) low_stock FROM inventory WHERE stock_quantity <= reorder_level");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                out.println("<div class='card'><h3>Low Stock SKUs</h3><div class='value'>" + rs.getInt("low_stock") + "</div></div>");
            }

            out.println("</div>");

            out.println("<div class='table-card'><h2>Revenue by Order Status</h2>");
            out.println("<table>");
            out.println("<tr><th>Status</th><th>Orders</th><th>Revenue</th><th>Operational Meaning</th></tr>");

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, COUNT(*) orders_count, COALESCE(SUM(price * quantity),0) revenue " +
                    "FROM orders GROUP BY status ORDER BY revenue DESC");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    String status = rs.getString("status");
                    String color = status.equals("PAID") ? "green" : status.equals("PENDING") ? "yellow" : "red";
                    String meaning = status.equals("PAID") ? "Completed sales" :
                                     status.equals("PENDING") ? "Awaiting settlement" :
                                     "Failed customer/order flow";

                    out.println("<tr>");
                    out.println("<td>" + Ui.badge(status, color) + "</td>");
                    out.println("<td>" + rs.getInt("orders_count") + "</td>");
                    out.println("<td>$" + rs.getBigDecimal("revenue") + "</td>");
                    out.println("<td>" + meaning + "</td>");
                    out.println("</tr>");
                }
            }

            out.println("</table></div>");

            out.println("<div class='table-card'><h2>Top Inventory Risk</h2>");
            out.println("<table>");
            out.println("<tr><th>SKU</th><th>Item</th><th>Category</th><th>Stock</th><th>Reorder Level</th><th>Warehouse</th></tr>");

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT sku, item_name, category, stock_quantity, reorder_level, warehouse " +
                    "FROM inventory ORDER BY stock_quantity ASC LIMIT 25");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    out.println("<tr>");
                    out.println("<td>" + rs.getString("sku") + "</td>");
                    out.println("<td>" + rs.getString("item_name") + "</td>");
                    out.println("<td>" + rs.getString("category") + "</td>");
                    out.println("<td>" + rs.getInt("stock_quantity") + "</td>");
                    out.println("<td>" + rs.getInt("reorder_level") + "</td>");
                    out.println("<td>" + rs.getString("warehouse") + "</td>");
                    out.println("</tr>");
                }
            }

            out.println("</table></div>");
            Ui.footer(out);

        } catch (Exception e) {
            resp.setStatus(500);
            out.println("<h1>Reports Service Error</h1>");
            out.println("<pre>" + e.getMessage() + "</pre>");
        }
    }
}
