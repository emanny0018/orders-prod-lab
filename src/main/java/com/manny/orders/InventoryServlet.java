package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/inventory")
public class InventoryServlet extends HttpServlet {

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

            int totalSkus = 0;
            int lowStock = 0;
            int outOfStock = 0;
            int totalUnits = 0;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) total_skus, " +
                    "SUM(CASE WHEN stock_quantity <= reorder_level THEN 1 ELSE 0 END) low_stock, " +
                    "SUM(CASE WHEN stock_quantity = 0 THEN 1 ELSE 0 END) out_of_stock, " +
                    "SUM(stock_quantity) total_units FROM inventory");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalSkus = rs.getInt("total_skus");
                    lowStock = rs.getInt("low_stock");
                    outOfStock = rs.getInt("out_of_stock");
                    totalUnits = rs.getInt("total_units");
                }
            }

            Ui.pageStart(out, "Inventory", "Warehouse stock, reorder risk, and SKU availability.", user);

            out.println("<div class='card-row'>");
            out.println("<div class='card'><h3>Total SKUs</h3><div class='value'>" + totalSkus + "</div></div>");
            out.println("<div class='card'><h3>Total Units</h3><div class='value'>" + totalUnits + "</div></div>");
            out.println("<div class='card'><h3>Low Stock</h3><div class='value'>" + lowStock + "</div></div>");
            out.println("<div class='card'><h3>Out of Stock</h3><div class='value'>" + outOfStock + "</div></div>");
            out.println("</div>");


            out.println("<div class='table-card'><h2>Inventory Risk View</h2>");
            out.println("<table>");
            out.println("<tr><th>SKU</th><th>Item</th><th>Category</th><th>Stock</th><th>Reorder Level</th><th>Status</th><th>Warehouse</th><th>Updated</th></tr>");

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT sku, item_name, category, stock_quantity, reorder_level, warehouse, updated_at " +
                    "FROM inventory ORDER BY stock_quantity ASC LIMIT 100");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    int stock = rs.getInt("stock_quantity");
                    int reorder = rs.getInt("reorder_level");

                    String status;
                    if (stock == 0) {
                        status = Ui.badge("OUT", "red");
                    } else if (stock <= reorder) {
                        status = Ui.badge("LOW", "yellow");
                    } else {
                        status = Ui.badge("OK", "green");
                    }

                    out.println("<tr>");
                    out.println("<td>" + rs.getString("sku") + "</td>");
                    out.println("<td>" + rs.getString("item_name") + "</td>");
                    out.println("<td>" + rs.getString("category") + "</td>");
                    out.println("<td>" + stock + "</td>");
                    out.println("<td>" + reorder + "</td>");
                    out.println("<td>" + status + "</td>");
                    out.println("<td>" + rs.getString("warehouse") + "</td>");
                    out.println("<td>" + rs.getTimestamp("updated_at") + "</td>");
                    out.println("</tr>");
                }
            }

            out.println("</table></div>");
            Ui.footer(out);

        } catch (Exception e) {
            resp.setStatus(500);
            out.println("<h1>Inventory Service Error</h1>");
            out.println("<pre>" + e.getMessage() + "</pre>");
        }
    }
}
