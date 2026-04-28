package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.*;

@WebServlet("/reports/sales-reconciliation-export")
public class SalesReconciliationExportServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("../login.jsp");
            return;
        }

        resp.setContentType("text/csv;charset=UTF-8");
        resp.setHeader(
                "Content-Disposition",
                "attachment; filename=sales-reconciliation-export.csv"
        );

        /*
         * Real business use case:
         * Sales + payment reconciliation export.
         *
         * Intentionally non-streaming first:
         * The app builds the full CSV in memory before returning it.
         * This is a common production anti-pattern that can cause JVM heap pressure
         * when data volume and concurrent export users increase.
         */
        StringBuilder csv = new StringBuilder(10 * 1024 * 1024);

        csv.append("order_id,customer_name,item_name,quantity,unit_price,order_amount,order_status,")
           .append("payment_id,payment_method,payment_amount,payment_status,transaction_ref,created_at\n");

        String sql = """
            SELECT
                o.id AS order_id,
                o.customer_name,
                o.item_name,
                o.quantity,
                o.price,
                (o.price * o.quantity) AS order_amount,
                o.status AS order_status,
                p.id AS payment_id,
                p.payment_method,
                p.amount AS payment_amount,
                p.payment_status,
                p.transaction_ref,
                o.created_at
            FROM orders o
            LEFT JOIN payments p ON p.order_id = o.id
            ORDER BY o.created_at DESC
        """;

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                csv.append(rs.getInt("order_id")).append(",");
                csv.append(csv(rs.getString("customer_name"))).append(",");
                csv.append(csv(rs.getString("item_name"))).append(",");
                csv.append(rs.getInt("quantity")).append(",");
                csv.append(rs.getBigDecimal("price")).append(",");
                csv.append(rs.getBigDecimal("order_amount")).append(",");
                csv.append(csv(rs.getString("order_status"))).append(",");
                csv.append(rs.getInt("payment_id")).append(",");
                csv.append(csv(rs.getString("payment_method"))).append(",");
                csv.append(rs.getBigDecimal("payment_amount")).append(",");
                csv.append(csv(rs.getString("payment_status"))).append(",");
                csv.append(csv(rs.getString("transaction_ref"))).append(",");
                csv.append(rs.getTimestamp("created_at")).append("\n");
            }

            resp.getWriter().write(csv.toString());

        } catch (OutOfMemoryError oom) {
            System.err.println("SALES_RECONCILIATION_EXPORT_OOM: " + oom.getMessage());
            throw oom;
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("SALES_RECONCILIATION_EXPORT_FAILED: " + e.getMessage());
        }
    }

    private String csv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
