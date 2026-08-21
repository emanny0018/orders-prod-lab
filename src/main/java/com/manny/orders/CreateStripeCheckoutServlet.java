package com.manny.orders;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

@WebServlet("/create-stripe-checkout")
public class CreateStripeCheckoutServlet
        extends HttpServlet {

    @SuppressWarnings("unchecked")
    @Override
    protected void doPost(
            HttpServletRequest req,
            HttpServletResponse resp)
            throws IOException {

        HttpSession session =
            req.getSession(false);

        if (session == null ||
            session.getAttribute("user") == null) {

            resp.sendRedirect("login.jsp");
            return;
        }

        List<CartItem> cart =
            (List<CartItem>)
                session.getAttribute("cart");

        if (cart == null ||
            cart.isEmpty()) {

            resp.sendRedirect("cart");
            return;
        }

        String correlationId =
            TraceSupport.newCorrelationId();

        String httpSessionId =
            session.getId();

        String username =
            session
                .getAttribute("user")
                .toString();

        BigDecimal expectedAmount =
            BigDecimal.ZERO;

        for (CartItem item : cart) {

            BigDecimal lineTotal =
                item.unitPrice.multiply(
                    BigDecimal.valueOf(
                        item.quantity
                    )
                );

            expectedAmount =
                expectedAmount.add(lineTotal);
        }

        /*
         * Store correlation in Tomcat session.
         */
        session.setAttribute(
            "correlation_id",
            correlationId);

        req.setAttribute(
            "correlation_id",
            correlationId);

        try {

            /*
             * Start durable business transaction trace.
             */
            try (Connection conn =
                     Db.getConnection();
                 PreparedStatement ps =
                     conn.prepareStatement(
                         """
                         INSERT INTO app_transactions
                         (
                           correlation_id,
                           http_session_id,
                           username,
                           expected_amount,
                           status
                         )
                         VALUES
                         (?, ?, ?, ?, 'CHECKOUT_STARTED')
                         """)) {

                ps.setString(
                    1,
                    correlationId);

                ps.setString(
                    2,
                    httpSessionId);

                ps.setString(
                    3,
                    username);

                ps.setBigDecimal(
                    4,
                    expectedAmount);

                ps.executeUpdate();
            }

            TraceSupport.event(
                correlationId,
                "CHECKOUT_STARTED",
                "TOMCAT",
                "Checkout request accepted",
                httpSessionId,
                null,
                null,
                null,
                null,
                true
            );

            Stripe.apiKey =
                SecretReader.getStripeSecretKey();

            String baseUrl =
                System.getenv("APP_BASE_URL");

            if (baseUrl == null ||
                baseUrl.isBlank()) {

                baseUrl =
                    "http://localhost:8080/orders";
            }

            SessionCreateParams.Builder paramsBuilder =
                SessionCreateParams
                    .builder()
                    .setMode(
                        SessionCreateParams.Mode.PAYMENT)

                    /*
                     * THIS is the bridge across the
                     * asynchronous Stripe boundary.
                     */
                    .putMetadata(
                        "correlation_id",
                        correlationId)

                    .putMetadata(
                        "http_session_id",
                        httpSessionId)

                    .setSuccessUrl(
                        baseUrl +
                        "/checkout-success" +
                        "?session_id=" +
                        "{CHECKOUT_SESSION_ID}")

                    .setCancelUrl(
                        baseUrl +
                        "/checkout-cancel" +
                        "?session_id=" +
                        "{CHECKOUT_SESSION_ID}")

                    .setAllowPromotionCodes(true);

            for (CartItem item : cart) {

                long unitAmountCents =
                    item.unitPrice
                        .multiply(
                            BigDecimal.valueOf(100))
                        .longValue();

                paramsBuilder.addLineItem(

                    SessionCreateParams
                        .LineItem
                        .builder()

                        .setQuantity(
                            (long) item.quantity)

                        .setPriceData(

                            SessionCreateParams
                                .LineItem
                                .PriceData
                                .builder()

                                .setCurrency("usd")

                                .setUnitAmount(
                                    unitAmountCents)

                                .setProductData(

                                    SessionCreateParams
                                        .LineItem
                                        .PriceData
                                        .ProductData
                                        .builder()

                                        .setName(
                                            item.itemName)

                                        .setDescription(
                                            item.sku)

                                        .build()
                                )

                                .build()
                        )

                        .build()
                );
            }

            long stripeStart =
                System.nanoTime();

            Session stripeSession =
                Session.create(
                    paramsBuilder.build());

            long stripeDurationMs =
                (System.nanoTime() -
                 stripeStart)
                 / 1_000_000;

            String stripeSessionId =
                stripeSession.getId();

            session.setAttribute(
                "stripe_checkout_session_id",
                stripeSessionId);

            req.setAttribute(
                "stripe_session_id",
                stripeSessionId);

            try (Connection conn =
                     Db.getConnection();
                 PreparedStatement ps =
                     conn.prepareStatement(
                         """
                         UPDATE app_transactions
                         SET
                           stripe_session_id = ?,
                           status =
                             'STRIPE_CHECKOUT_CREATED',
                           checkout_created_at =
                             CURRENT_TIMESTAMP
                         WHERE correlation_id = ?
                         """)) {

                ps.setString(
                    1,
                    stripeSessionId);

                ps.setString(
                    2,
                    correlationId);

                ps.executeUpdate();
            }

            TraceSupport.event(
                correlationId,
                "STRIPE_CHECKOUT_CREATED",
                "STRIPE",
                "Stripe Checkout Session created",
                httpSessionId,
                stripeSessionId,
                null,
                null,
                stripeDurationMs,
                true
            );

            resp.sendRedirect(
                stripeSession.getUrl());

        } catch (Exception e) {

            try (Connection conn =
                     Db.getConnection();
                 PreparedStatement ps =
                     conn.prepareStatement(
                         """
                         UPDATE app_transactions
                         SET
                           status = 'FAILED',
                           last_error = ?,
                           completed_at =
                             CURRENT_TIMESTAMP
                         WHERE correlation_id = ?
                         """)) {

                ps.setString(
                    1,
                    e.getMessage());

                ps.setString(
                    2,
                    correlationId);

                ps.executeUpdate();

            } catch (Exception ignored) {}

            TraceSupport.event(
                correlationId,
                "CHECKOUT_FAILED",
                "APPLICATION",
                e.getClass().getName() +
                    ": " + e.getMessage(),
                httpSessionId,
                null,
                null,
                null,
                null,
                false
            );

            resp.setStatus(500);

            resp.getWriter().println(
                "STRIPE_CHECKOUT_FAILED: " +
                e.getMessage()
            );
        }
    }
}
