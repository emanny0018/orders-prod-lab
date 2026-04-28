package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;

@WebServlet("/create-order")
public class CreateOrderServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String customer = req.getParameter("customer");
        String sku = req.getParameter("sku");
        int qty = Integer.parseInt(req.getParameter("qty"));

        try (Connection conn = Db.getConnection()) {
            conn.setAutoCommit(false);

            String itemName;
            BigDecimal unitPrice;
            int stock;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT item_name, unit_price, stock_quantity FROM inventory WHERE sku = ? FOR UPDATE")) {
                ps.setString(1, sku);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        resp.setStatus(400);
                        resp.getWriter().println("CREATE_ORDER_FAILED: SKU_NOT_FOUND");
                        return;
                    }

                    itemName = rs.getString("item_name");
                    unitPrice = rs.getBigDecimal("unit_price");
                    stock = rs.getInt("stock_quantity");
                }
            }

            if (stock < qty) {
                conn.rollback();
                resp.setStatus(409);
                resp.getWriter().println("CREATE_ORDER_FAILED: INSUFFICIENT_STOCK available=" + stock);
                return;
            }

            BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(qty));
            int orderId;

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO orders (customer_name, item_name, quantity, price, status) VALUES (?, ?, ?, ?, 'PENDING')",
                    Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, customer);
                ps.setString(2, sku);
                ps.setInt(3, qty);
                ps.setBigDecimal(4, unitPrice);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    orderId = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO payments (order_id, payment_method, amount, payment_status, transaction_ref) VALUES (?, 'CARD', ?, 'PENDING', ?)")) {

                ps.setInt(1, orderId);
                ps.setBigDecimal(2, amount);
                ps.setString(3, "TXN-" + orderId);
                ps.executeUpdate();
            }

            conn.commit();

            String event = "{"
                    + "\"event\":\"ORDER_CREATED\","
                    + "\"orderId\":" + orderId + ","
                    + "\"customer\":\"" + safe(customer) + "\","
                    + "\"sku\":\"" + safe(sku) + "\","
                    + "\"itemName\":\"" + safe(itemName) + "\","
                    + "\"quantity\":" + qty + ","
                    + "\"unitPrice\":" + unitPrice + ","
                    + "\"amount\":" + amount + ","
                    + "\"status\":\"PENDING\""
                    + "}";

            try {
                QueuePublisher.publishOrderEvent(event);
                System.err.println("ORDER_EVENT_PUBLISHED: " + event);
            } catch (Exception e) {
                System.err.println("ORDER_EVENT_PUBLISH_FAILED: " + e.getMessage());
            }

            resp.sendRedirect("orders");

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().println("CREATE_ORDER_FAILED: " + e.getMessage());
        }
    }

    private String safe(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
