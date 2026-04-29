package com.manny.orders;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.List;

@WebServlet("/create-stripe-checkout")
public class CreateStripeCheckoutServlet extends HttpServlet {

    @SuppressWarnings("unchecked")
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("login.jsp");
            return;
        }

        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");

        if (cart == null || cart.isEmpty()) {
            resp.sendRedirect("cart");
            return;
        }

        try {
            Stripe.apiKey = SecretReader.getStripeSecretKey();

            String baseUrl = System.getenv("APP_BASE_URL");
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://localhost:8080/orders";
            }

            SessionCreateParams.Builder paramsBuilder =
                    SessionCreateParams.builder()
                            .setMode(SessionCreateParams.Mode.PAYMENT)
                            .setSuccessUrl(baseUrl + "/checkout-success?session_id={CHECKOUT_SESSION_ID}")
                            .setCancelUrl(baseUrl + "/checkout-cancel?session_id={CHECKOUT_SESSION_ID}")
                            .setAllowPromotionCodes(true);

            for (CartItem item : cart) {
                long unitAmountCents = item.unitPrice.multiply(java.math.BigDecimal.valueOf(100)).longValue();

                paramsBuilder.addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity((long) item.quantity)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(unitAmountCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(item.itemName)
                                                                .setDescription(item.sku)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );
            }

            Session stripeSession = Session.create(paramsBuilder.build());

            session.setAttribute("stripe_checkout_session_id", stripeSession.getId());

            resp.sendRedirect(stripeSession.getUrl());

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().println("STRIPE_CHECKOUT_FAILED: " + e.getMessage());
        }
    }
}
