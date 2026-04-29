package com.manny.orders;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.util.List;

@WebServlet("/checkout-success")
public class CheckoutSuccessServlet extends HttpServlet {

    @SuppressWarnings("unchecked")
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        HttpSession httpSession = req.getSession(false);
        if (httpSession == null || httpSession.getAttribute("user") == null) {
            resp.sendRedirect("login.jsp");
            return;
        }

        String sessionId = req.getParameter("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            resp.setStatus(400);
            resp.getWriter().println("CHECKOUT_SUCCESS_FAILED: missing Stripe session_id");
            return;
        }

        String user = httpSession.getAttribute("user").toString();
        List<CartItem> cart = (List<CartItem>) httpSession.getAttribute("cart");

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            Stripe.apiKey = SecretReader.getStripeSecretKey();
            Session stripeSession = Session.retrieve(sessionId);

            if (!"paid".equalsIgnoreCase(stripeSession.getPaymentStatus())) {
                resp.setStatus(402);
                resp.getWriter().println("PAYMENT_NOT_COMPLETED: " + stripeSession.getPaymentStatus());
                return;
            }

            try (Connection conn = Db.getConnection()) {

                Integer existingSaleId = findExistingSale(conn, sessionId);
                if (existingSaleId != null) {
                    renderReceipt(out, user, existingSaleId, "Payment already recorded. Showing existing receipt.");
                    return;
                }

                if (cart == null || cart.isEmpty()) {
                    resp.setStatus(409);
                    resp.getWriter().println("CHECKOUT_SUCCESS_FAILED: payment succeeded but cart was missing from session.");
                    return;
                }

                conn.setAutoCommit(false);

                BigDecimal subtotal = BigDecimal.ZERO;
                for (CartItem item : cart) {
                    subtotal = subtotal.add(item.total());
                }

                BigDecimal stripeTotal = BigDecimal.valueOf(stripeSession.getAmountTotal()).movePointLeft(2);

                BigDecimal discount = BigDecimal.ZERO;
                if (stripeSession.getTotalDetails() != null &&
                    stripeSession.getTotalDetails().getAmountDiscount() != null) {
                    discount = BigDecimal.valueOf(stripeSession.getTotalDetails().getAmountDiscount()).movePointLeft(2);
                }

                int saleId;

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sales (customer_name, payment_method, total_amount, sale_status, stripe_session_id, stripe_payment_intent, discount_amount) " +
                        "VALUES (?, 'STRIPE_CARD', ?, 'PAID', ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, user);
                    ps.setBigDecimal(2, stripeTotal);
                    ps.setString(3, sessionId);
                    ps.setString(4, stripeSession.getPaymentIntent());
                    ps.setBigDecimal(5, discount);
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        saleId = rs.getInt(1);
                    }
                }

                for (CartItem item : cart) {
                    BigDecimal lineTotal = item.total();

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO sale_items (sale_id, sku, item_name, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?, ?)")) {
                        ps.setInt(1, saleId);
                        ps.setString(2, item.sku);
                        ps.setString(3, item.itemName);
                        ps.setInt(4, item.quantity);
                        ps.setBigDecimal(5, item.unitPrice);
                        ps.setBigDecimal(6, lineTotal);
                        ps.executeUpdate();
                    }

                    int orderId;

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO orders (sale_id, customer_name, item_name, quantity, price, status) VALUES (?, ?, ?, ?, ?, 'PAID')",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, saleId);
                        ps.setString(2, user);
                        ps.setString(3, item.sku);
                        ps.setInt(4, item.quantity);
                        ps.setBigDecimal(5, item.unitPrice);
                        ps.executeUpdate();

                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            rs.next();
                            orderId = rs.getInt(1);
                        }
                    }

                    String event = "{"
                            + "\"event\":\"ORDER_CREATED\","
                            + "\"orderId\":" + orderId + ","
                            + "\"saleId\":" + saleId + ","
                            + "\"customer\":\"" + safe(user) + "\","
                            + "\"sku\":\"" + safe(item.sku) + "\","
                            + "\"itemName\":\"" + safe(item.itemName) + "\","
                            + "\"quantity\":" + item.quantity + ","
                            + "\"unitPrice\":" + item.unitPrice + ","
                            + "\"amount\":" + lineTotal + ","
                            + "\"status\":\"PAID\""
                            + "}";

                    QueuePublisher.publishOrderEvent(event);
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payments (sale_id, order_id, payment_method, amount, payment_status, transaction_ref, stripe_session_id) " +
                        "VALUES (?, NULL, 'STRIPE_CARD', ?, 'SETTLED', ?, ?)")) {
                    ps.setInt(1, saleId);
                    ps.setBigDecimal(2, stripeTotal);
                    ps.setString(3, "STRIPE-" + sessionId);
                    ps.setString(4, sessionId);
                    ps.executeUpdate();
                }

                conn.commit();
                httpSession.removeAttribute("cart");

                renderReceipt(out, user, saleId, "Payment successful. Sale recorded, payment settled, and inventory events published.");
            }

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().println("CHECKOUT_SUCCESS_FAILED: " + e.getMessage());
        }
    }

    private Integer findExistingSale(Connection conn, String stripeSessionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM sales WHERE stripe_session_id = ?")) {
            ps.setString(1, stripeSessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        return null;
    }

    private void renderReceipt(PrintWriter out, String user, int saleId, String message) {
        Ui.pageStart(out, "Payment Receipt", message, user);
        out.println("<div class='table-card'>");
        out.println("<h2>Receipt</h2>");
        out.println("<p><b>Sale ID:</b> #" + saleId + "</p>");
        out.println("<p>" + message + "</p>");
        out.println("<p><a href='orders'>Back to Orders</a> | <a href='payments'>View Payments</a> | <a href='inventory'>View Inventory</a></p>");
        out.println("</div>");
        Ui.footer(out);
    }

    private String safe(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
