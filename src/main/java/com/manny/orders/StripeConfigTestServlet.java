package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

@WebServlet("/stripe-config-test")
public class StripeConfigTestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");

        try {
            String key = SecretReader.getStripeSecretKey();

            if (!key.startsWith("sk_test_")) {
                resp.setStatus(500);
                resp.getWriter().println("STRIPE_CONFIG_INVALID: key does not start with sk_test_");
                return;
            }

            resp.getWriter().println("STRIPE_CONFIG_OK");
            resp.getWriter().println("Key prefix: " + key.substring(0, 12) + "...");
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().println("STRIPE_CONFIG_FAILED: " + e.getMessage());
        }
    }
}
