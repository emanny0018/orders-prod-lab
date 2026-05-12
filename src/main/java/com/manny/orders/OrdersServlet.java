package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/orders")
public class OrdersServlet extends HttpServlet {

    private DashboardSummary getDashboardSummary(Connection conn) throws Exception {
        String cacheKey = "orders:dashboard:summary";

        try (Jedis jedis = Cache.getClient()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                return DashboardSummary.fromCacheValue(cached);
            }
        } catch (Exception ignored) {}

        try (PreparedStatement summary = conn.prepareStatement(
                "SELECT COUNT(*) total_orders, " +
                "SUM(CASE WHEN status='PAID' THEN 1 ELSE 0 END) paid_orders, " +
                "SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END) pending_orders, " +
                "SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) failed_orders, " +
                "COALESCE(SUM(price * quantity),0) revenue FROM orders")) {

            ResultSet sr = summary.executeQuery();
            sr.next();

            DashboardSummary result = new DashboardSummary(
                    sr.getInt("total_orders"),
                    sr.getInt("paid_orders"),
                    sr.getInt("pending_orders"),
                    sr.getInt("failed_orders"),
                    sr.getBigDecimal("revenue").toString()
            );

            try (Jedis jedis = Cache.getClient()) {
                jedis.setex(cacheKey, 30, result.toCacheValue());
            } catch (Exception ignored) {}

            return result;
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

        try (Connection conn = Db.getConnection()) {

            DashboardSummary dash = getDashboardSummary(conn);

            Ui.pageStart(out,
                    "Orders Command Center",
                    "Create customer purchases, track payment state, and publish fulfillment events.",
                    user);

            out.println("<div class='card-row'>");
            out.println("<div class='card'><h3>Total Orders</h3><div class='value'>" + dash.totalOrders + "</div></div>");
            out.println("<div class='card'><h3>Paid Orders</h3><div class='value'>" + dash.paidOrders + "</div></div>");
            out.println("<div class='card'><h3>Pending Orders</h3><div class='value'>" + dash.pendingOrders + "</div></div>");
            out.println("<div class='card'><h3>Revenue</h3><div class='value'>$" + dash.revenue + "</div></div>");
            out.println("</div>");

            out.println("<div class='table-card'><h2>Create Customer Purchase</h2>");
            out.println("<form action='create-order' method='post' style='display:grid;grid-template-columns:2fr 3fr 1fr 1fr auto;gap:12px;align-items:end;'>");

            out.println("<div><label>Customer</label><input name='customer' required placeholder='Customer name' style='width:100%;padding:12px;border:1px solid #cbd5e1;border-radius:10px;'></div>");

            out.println("<div><label>Product</label><select id='skuSelect' name='sku' required onchange='updatePrice()' style='width:100%;padding:12px;border:1px solid #cbd5e1;border-radius:10px;'>");
            out.println("<option value='' data-price='0'>Select product</option>");

            for (ProductCatalog.Product product : ProductCatalog.getProducts(conn)) {
                if (product.stockQuantity <= 0) {
                    out.println("<option disabled value='" + product.sku + "' data-price='" + product.unitPrice + "'>" + product.label() + " OUT OF STOCK</option>");
                } else {
                    out.println("<option value='" + product.sku + "' data-price='" + product.unitPrice + "'>" + product.label() + "</option>");
                }
            }

            out.println("</select></div>");

            out.println("<div><label>Qty</label><input id='qtyInput' name='qty' type='number' min='1' value='1' required oninput='updatePrice()' style='width:100%;padding:12px;border:1px solid #cbd5e1;border-radius:10px;'></div>");
            out.println("<div><label>Total</label><input id='totalPrice' readonly value='$0.00' style='width:100%;padding:12px;border:1px solid #cbd5e1;border-radius:10px;background:#f8fafc;font-weight:bold;'></div>");
            out.println("<button type='submit' style='padding:13px 18px;border:0;border-radius:10px;background:#2563eb;color:white;font-weight:800;'>Create</button>");

            out.println("</form>");
            out.println("""
<script>
function updatePrice() {
  const select = document.getElementById('skuSelect');
  const qty = parseInt(document.getElementById('qtyInput').value || '1');
  const price = parseFloat(select.options[select.selectedIndex]?.dataset.price || '0');
  document.getElementById('totalPrice').value = '$' + (price * qty).toFixed(2);
}
</script>
""");
            out.println("</div>");

            
            out.println("<div class='table-card'><h2>Add Product to Shopping Cart</h2>");
            out.println("<form action='cart/add' method='post' style='display:grid;grid-template-columns:3fr 1fr auto;gap:12px;align-items:end;'>");
            out.println("<div><label>Product</label><select name='sku' required style='width:100%;padding:12px;border:1px solid #cbd5e1;border-radius:10px;'>");

            for (ProductCatalog.Product product : ProductCatalog.getProducts(conn)) {
                if (product.stockQuantity <= 0) {
                    out.println("<option disabled value='" + product.sku + "'>" + product.label() + " OUT OF STOCK</option>");
                } else {
                    out.println("<option value='" + product.sku + "'>" + product.label() + "</option>");
                }
            }

            out.println("</select></div>");
            out.println("<div><label>Qty</label><input name='qty' type='number' min='1' value='1' required style='width:100%;padding:12px;border:1px solid #cbd5e1;border-radius:10px;'></div>");
            out.println("<button type='submit' style='padding:13px 18px;border:0;border-radius:10px;background:#7c3aed;color:white;font-weight:800;'>Add to Cart</button>");
            out.println("</form>");
            out.println("</div>");

            out.println("<div class='table-card'><h2>Recent Customer Orders</h2>");
            out.println("<table>");
            out.println("<tr><th>ID</th><th>Customer</th><th>SKU</th><th>Qty</th><th>Unit Price</th><th>Total</th><th>Status</th><th>Created</th></tr>");

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, customer_name, item_name, quantity, price, (quantity * price) total, status, created_at " +
                    "FROM orders ORDER BY id DESC LIMIT 75");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    String status = rs.getString("status");
                    String color = status.equals("PAID") ? "green" : status.equals("PENDING") ? "yellow" : "red";

                    out.println("<tr>");
                    out.println("<td><a href='order?id=" + rs.getInt("id") + "'>#" + rs.getInt("id") + "</a></td>");
                    out.println("<td>" + rs.getString("customer_name") + "</td>");
                    out.println("<td>" + rs.getString("item_name") + "</td>");
                    out.println("<td>" + rs.getInt("quantity") + "</td>");
                    out.println("<td>$" + rs.getBigDecimal("price") + "</td>");
                    out.println("<td>$" + rs.getBigDecimal("total") + "</td>");
                    out.println("<td>" + Ui.badge(status, color) + "</td>");
                    out.println("<td>" + rs.getTimestamp("created_at") + "</td>");
                    out.println("</tr>");
                }
            }

            out.println("</table></div>");
            Ui.footer(out);

        } catch (Exception e) {
            resp.setStatus(500);
            out.println("<h1>Orders Service Error</h1>");
            out.println("<pre>" + e.getMessage() + "</pre>");
        }
    }
}
