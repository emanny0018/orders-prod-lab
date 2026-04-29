package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;

@WebServlet("/checkout-cancel")
public class CheckoutCancelServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        HttpSession session = req.getSession(false);
        String user = "UNKNOWN";

        if (session != null && session.getAttribute("user") != null) {
            user = session.getAttribute("user").toString();
        }

        String stripeSessionId = req.getParameter("session_id");

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO checkout_attempts (customer_name, stripe_session_id, checkout_status, reason) VALUES (?, ?, 'CANCELED', ?)")) {

            ps.setString(1, user);
            ps.setString(2, stripeSessionId);
            ps.setString(3, "Customer canceled or abandoned Stripe checkout");
            ps.executeUpdate();

        } catch (Exception e) {
            System.err.println("CHECKOUT_CANCEL_RECORD_FAILED: " + e.getMessage());
        }

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        Ui.pageStart(out, "Checkout Canceled", "Payment was canceled. Cart was not charged.", user);

        out.println("<div class='table-card'>");
        out.println("<h2>Checkout Canceled</h2>");
        out.println("<p>No payment was captured.</p>");
        out.println("<p>Your cart is still available if you want to try again.</p>");
        out.println("<p><a href='cart'>Return to Cart</a> | <a href='orders'>Back to Orders</a></p>");
        out.println("</div>");

        Ui.footer(out);
    }
}
