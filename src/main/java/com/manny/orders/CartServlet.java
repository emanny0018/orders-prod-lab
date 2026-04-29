package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;

@WebServlet("/cart")
public class CartServlet extends HttpServlet {

    @SuppressWarnings("unchecked")
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("login.jsp");
            return;
        }

        String user = session.getAttribute("user").toString();
        List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        Ui.pageStart(out, "Shopping Cart", "Customer cart stored in Tomcat HTTP session.", user);

        out.println("<div class='table-card'><h2>Cart Items</h2>");
        out.println("<table>");
        out.println("<tr><th>SKU</th><th>Product</th><th>Qty</th><th>Unit Price</th><th>Total</th></tr>");

        BigDecimal grandTotal = BigDecimal.ZERO;

        if (cart != null) {
            for (CartItem item : cart) {
                grandTotal = grandTotal.add(item.total());
                out.println("<tr>");
                out.println("<td>" + item.sku + "</td>");
                out.println("<td>" + item.itemName + "</td>");
                out.println("<td>" + item.quantity + "</td>");
                out.println("<td>$" + item.unitPrice + "</td>");
                out.println("<td>$" + item.total() + "</td>");
                out.println("</tr>");
            }
        }

        out.println("</table></div>");

        out.println("<div class='card-row'>");
        out.println("<div class='card'><h3>Cart Total</h3><div class='value'>$" + grandTotal + "</div></div>");
        out.println("</div>");

        out.println("<form action='create-stripe-checkout' method='post'>");
        out.println("<button type='submit' style='padding:13px 18px;border:0;border-radius:10px;background:#16a34a;color:white;font-weight:800;'>Pay with Stripe</button>");
        out.println("</form>");

        Ui.footer(out);
    }
}
