package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/cart/add")
public class AddToCartServlet extends HttpServlet {

    @SuppressWarnings("unchecked")
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("../login.jsp");
            return;
        }

        String sku = req.getParameter("sku");
        int qty = Integer.parseInt(req.getParameter("qty"));

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT sku, item_name, unit_price, stock_quantity FROM inventory WHERE sku = ?")) {

            ps.setString(1, sku);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(404);
                    resp.getWriter().println("SKU_NOT_FOUND");
                    return;
                }

                if (rs.getInt("stock_quantity") < qty) {
                    resp.setStatus(409);
                    resp.getWriter().println("INSUFFICIENT_STOCK");
                    return;
                }

                List<CartItem> cart = (List<CartItem>) session.getAttribute("cart");
                if (cart == null) {
                    cart = new ArrayList<>();
                    session.setAttribute("cart", cart);
                }

                cart.add(new CartItem(
                        rs.getString("sku"),
                        rs.getString("item_name"),
                        qty,
                        rs.getBigDecimal("unit_price")
                ));

                resp.sendRedirect("../cart");
            }

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().println("ADD_TO_CART_FAILED: " + e.getMessage());
        }
    }
}
