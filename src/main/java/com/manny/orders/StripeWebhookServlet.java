package com.manny.orders;

import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

@WebServlet("/stripe/webhook")
public class StripeWebhookServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String payload = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String sigHeader = req.getHeader("Stripe-Signature");

        try {
            String endpointSecret = SecretReader.getStripeWebhookSecret();

            Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);

            System.out.println("STRIPE_WEBHOOK_RECEIVED type=" + event.getType() + " id=" + event.getId());

            // 🔥 HANDLE CHECKOUT COMPLETED
            if ("checkout.session.completed".equals(event.getType())) {

                System.out.println("CHECKOUT_COMPLETED_EVENT_PROCESSING");

                Session stripeSession = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElse(null);

                if (stripeSession == null) {
                    System.err.println("WEBHOOK_ERROR: session is null");
                    resp.setStatus(400);
                    return;
                }

                String sessionId = stripeSession.getId();
                System.out.println("CHECKOUT_SESSION_ID=" + sessionId);

                Stripe.apiKey = SecretReader.getStripeSecretKey();

                if (!"paid".equalsIgnoreCase(stripeSession.getPaymentStatus())) {
                    System.err.println("WEBHOOK_PAYMENT_NOT_PAID status=" + stripeSession.getPaymentStatus());
                    resp.setStatus(200);
                    return;
                }

                try (Connection conn = Db.getConnection()) {

                    // 🔁 Prevent duplicates
                    try (var ps = conn.prepareStatement(
                            "SELECT id FROM sales WHERE stripe_session_id = ?")) {
                        ps.setString(1, sessionId);
                        try (var rs = ps.executeQuery()) {
                            if (rs.next()) {
                                System.out.println("WEBHOOK_DUPLICATE_EVENT session=" + sessionId);
                                resp.setStatus(200);
                                return;
                            }
                        }
                    }

                    conn.setAutoCommit(false);

                    // ⚠️ Minimal insert (no session/cart dependency yet)
                    try (var ps = conn.prepareStatement(
                            "INSERT INTO sales (customer_name, payment_method, total_amount, sale_status, stripe_session_id, stripe_payment_intent, discount_amount) " +
                            "VALUES (?, 'STRIPE_CARD', ?, 'PAID', ?, ?, ?)")) {

                        ps.setString(1, "webhook_user");
                        ps.setBigDecimal(2,
                                java.math.BigDecimal.valueOf(stripeSession.getAmountTotal()).movePointLeft(2));
                        ps.setString(3, sessionId);
                        ps.setString(4, stripeSession.getPaymentIntent());
                        ps.setBigDecimal(5, java.math.BigDecimal.ZERO);

                        ps.executeUpdate();
                    }

                    try (var ps = conn.prepareStatement(
                            "INSERT INTO payments (sale_id, order_id, payment_method, amount, payment_status, transaction_ref, stripe_session_id) " +
                            "VALUES ((SELECT id FROM sales WHERE stripe_session_id = ?), NULL, 'STRIPE_CARD', ?, 'SETTLED', ?, ?)")) {

                        ps.setString(1, sessionId);
                        ps.setBigDecimal(2,
                                java.math.BigDecimal.valueOf(stripeSession.getAmountTotal()).movePointLeft(2));
                        ps.setString(3, "STRIPE-" + sessionId);
                        ps.setString(4, sessionId);

                        ps.executeUpdate();
                    }

                    conn.commit();

                    System.out.println("WEBHOOK_DB_WRITE_SUCCESS session=" + sessionId);
                }
            }

            resp.setStatus(200);
            resp.getWriter().println("WEBHOOK_OK");

        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().println("WEBHOOK_FAILED: " + e.getMessage());
            System.err.println("STRIPE_WEBHOOK_FAILED: " + e.getMessage());
        }
    }
}
