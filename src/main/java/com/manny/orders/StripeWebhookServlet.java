package com.manny.orders;

import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

@WebServlet("/stripe/webhook")
public class StripeWebhookServlet
        extends HttpServlet {

    @Override
    protected void doPost(
            HttpServletRequest req,
            HttpServletResponse resp)
            throws IOException {

        String payload =
            new String(
                req.getInputStream()
                    .readAllBytes(),
                StandardCharsets.UTF_8
            );

        String sigHeader =
            req.getHeader("Stripe-Signature");

        try {

            String endpointSecret =
                SecretReader
                    .getStripeWebhookSecret();

            Event event =
                Webhook.constructEvent(
                    payload,
                    sigHeader,
                    endpointSecret
                );

            System.out.println(
                "STRIPE_WEBHOOK_RECEIVED" +
                " type=" + event.getType() +
                " id=" + event.getId()
            );

            if ("checkout.session.completed"
                    .equals(event.getType())) {

                Session stripeSession =
                    (Session)
                        event
                            .getDataObjectDeserializer()
                            .getObject()
                            .orElse(null);

                if (stripeSession == null) {

                    resp.setStatus(400);

                    resp.getWriter().println(
                        "WEBHOOK_FAILED: session null"
                    );

                    return;
                }

                String stripeSessionId =
                    stripeSession.getId();

                String correlationId = null;
                String originalHttpSessionId = null;

                if (stripeSession.getMetadata() != null) {

                    correlationId =
                        stripeSession
                            .getMetadata()
                            .get("correlation_id");

                    originalHttpSessionId =
                        stripeSession
                            .getMetadata()
                            .get("http_session_id");
                }

                /*
                 * Stripe has no browser JSESSIONID.
                 * We attach correlation to this request so
                 * ObservabilityFilter can still join it.
                 */
                req.setAttribute(
                    "correlation_id",
                    correlationId);

                req.setAttribute(
                    "stripe_session_id",
                    stripeSessionId);

                System.out.println(
                    "CHECKOUT_SESSION_ID=" +
                    stripeSessionId
                );

                System.out.println(
                    "CHECKOUT_CORRELATION_ID=" +
                    correlationId
                );

                TraceSupport.event(
                    correlationId,
                    "STRIPE_WEBHOOK_RECEIVED",
                    "STRIPE_WEBHOOK",
                    "checkout.session.completed" +
                    " event=" + event.getId(),
                    originalHttpSessionId,
                    stripeSessionId,
                    event.getId(),
                    null,
                    null,
                    true
                );

                Stripe.apiKey =
                    SecretReader
                        .getStripeSecretKey();

                if (!"paid".equalsIgnoreCase(
                        stripeSession
                            .getPaymentStatus())) {

                    TraceSupport.event(
                        correlationId,
                        "PAYMENT_NOT_PAID",
                        "STRIPE",
                        stripeSession
                            .getPaymentStatus(),
                        originalHttpSessionId,
                        stripeSessionId,
                        event.getId(),
                        null,
                        null,
                        false
                    );

                    resp.setStatus(200);
                    return;
                }

                BigDecimal amount =
                    BigDecimal
                        .valueOf(
                            stripeSession
                                .getAmountTotal())
                        .movePointLeft(2);

                try (Connection conn =
                         Db.getConnection()) {

                    /*
                     * Duplicate protection.
                     */
                    try (var ps =
                             conn.prepareStatement(
                                 """
                                 SELECT id
                                 FROM sales
                                 WHERE stripe_session_id = ?
                                 """)) {

                        ps.setString(
                            1,
                            stripeSessionId);

                        try (var rs =
                                 ps.executeQuery()) {

                            if (rs.next()) {

                                System.out.println(
                                    "WEBHOOK_DUPLICATE_EVENT" +
                                    " session=" +
                                    stripeSessionId
                                );

                                resp.setStatus(200);
                                return;
                            }
                        }
                    }

                    conn.setAutoCommit(false);

                    /*
                     * Business write #1
                     */
                    try (var ps =
                             conn.prepareStatement(
                                 """
                                 INSERT INTO sales
                                 (
                                   customer_name,
                                   payment_method,
                                   total_amount,
                                   sale_status,
                                   stripe_session_id,
                                   stripe_payment_intent,
                                   discount_amount
                                 )
                                 VALUES
                                 (
                                   ?,
                                   'STRIPE_CARD',
                                   ?,
                                   'PAID',
                                   ?,
                                   ?,
                                   ?
                                 )
                                 """)) {

                        ps.setString(
                            1,
                            "webhook_user");

                        ps.setBigDecimal(
                            2,
                            amount);

                        ps.setString(
                            3,
                            stripeSessionId);

                        ps.setString(
                            4,
                            stripeSession
                                .getPaymentIntent());

                        ps.setBigDecimal(
                            5,
                            BigDecimal.ZERO);

                        ps.executeUpdate();
                    }

                    /*
                     * Business write #2
                     */
                    try (var ps =
                             conn.prepareStatement(
                                 """
                                 INSERT INTO payments
                                 (
                                   sale_id,
                                   order_id,
                                   payment_method,
                                   amount,
                                   payment_status,
                                   transaction_ref,
                                   stripe_session_id
                                 )
                                 VALUES
                                 (
                                   (
                                     SELECT id
                                     FROM sales
                                     WHERE stripe_session_id = ?
                                   ),
                                   NULL,
                                   'STRIPE_CARD',
                                   ?,
                                   'SETTLED',
                                   ?,
                                   ?
                                 )
                                 """)) {

                        ps.setString(
                            1,
                            stripeSessionId);

                        ps.setBigDecimal(
                            2,
                            amount);

                        ps.setString(
                            3,
                            "STRIPE-" +
                            stripeSessionId);

                        ps.setString(
                            4,
                            stripeSessionId);

                        ps.executeUpdate();
                    }

                    /*
                     * Correlation/business assertion state
                     * is updated in the SAME DB transaction.
                     */
                    try (var ps =
                             conn.prepareStatement(
                                 """
                                 UPDATE app_transactions
                                 SET
                                   stripe_session_id = ?,
                                   stripe_payment_intent = ?,
                                   stripe_amount = ?,
                                   database_amount = ?,
                                   status = 'PASSED',
                                   webhook_received_at =
                                     CURRENT_TIMESTAMP,
                                   db_committed_at =
                                     CURRENT_TIMESTAMP,
                                   completed_at =
                                     CURRENT_TIMESTAMP
                                 WHERE correlation_id = ?
                                 """)) {

                        ps.setString(
                            1,
                            stripeSessionId);

                        ps.setString(
                            2,
                            stripeSession
                                .getPaymentIntent());

                        ps.setBigDecimal(
                            3,
                            amount);

                        ps.setBigDecimal(
                            4,
                            amount);

                        ps.setString(
                            5,
                            correlationId);

                        ps.executeUpdate();
                    }

                    conn.commit();

                    TraceSupport.event(
                        correlationId,
                        "DATABASE_COMMIT_SUCCESS",
                        "POSTGRESQL",
                        "sales + payments committed" +
                        " amount=" + amount,
                        originalHttpSessionId,
                        stripeSessionId,
                        event.getId(),
                        null,
                        null,
                        true
                    );

                    System.out.println(
                        "WEBHOOK_DB_WRITE_SUCCESS" +
                        " session=" +
                        stripeSessionId +
                        " correlation=" +
                        correlationId
                    );
                }
            }

            resp.setStatus(200);
            resp.getWriter().println(
                "WEBHOOK_OK");

        } catch (Exception e) {

            resp.setStatus(400);

            resp.getWriter().println(
                "WEBHOOK_FAILED: " +
                e.getMessage()
            );

            System.err.println(
                "STRIPE_WEBHOOK_FAILED: " +
                e.getMessage()
            );
        }
    }
}
