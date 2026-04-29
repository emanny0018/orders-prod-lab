package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.List;

@WebServlet("/checkout")
public class CheckoutServlet extends HttpServlet {

    @SuppressWarnings("unchecked")
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("login.jsp");
            return;
        }

        String customer = session.getAttribute("user").toString();
        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");

        if (cart == null || cart.isEmpty()) {
            resp.sendRedirect("cart");
            return;
        }

        try (Connection conn = Db.getConnection()) {
            conn.setAutoCommit(false);

            for (CartItem item : cart) {
                int orderId;
                BigDecimal amount = item.total();

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO orders (customer_name, item_name, quantity, price, status) VALUES (?, ?, ?, ?, 'PENDING')",
                        Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, customer);
                    ps.setString(2, item.sku);
                    ps.setInt(3, item.quantity);
                    ps.setBigDecimal(4, item.unitPrice);
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

                String event = "{"
                        + "\"event\":\"ORDER_CREATED\","
                        + "\"orderId\":" + orderId + ","
                        + "\"customer\":\"" + safe(customer) + "\","
                        + "\"sku\":\"" + safe(item.sku) + "\","
                        + "\"itemName\":\"" + safe(item.itemName) + "\","
                        + "\"quantity\":" + item.quantity + ","
                        + "\"unitPrice\":" + item.unitPrice + ","
                        + "\"amount\":" + amount + ","
                        + "\"status\":\"PENDING\""
                        + "}";

                QueuePublisher.publishOrderEvent(event);
            }

            conn.commit();
            session.removeAttribute("cart");

            resp.sendRedirect("orders");

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().println("CHECKOUT_FAILED: " + e.getMessage());
        }
    }

    private String safe(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
