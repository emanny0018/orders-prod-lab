package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/transaction-trace")
public class TransactionTraceServlet extends HttpServlet {

    private String esc(String s) {
        if (s == null) return "";

        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private String money(BigDecimalLike value) {
        return value == null ? "" : value.toString();
    }

    /*
     * Tiny wrapper only so we don't introduce any
     * formatting dependency.
     */
    private static class BigDecimalLike {
        private final java.math.BigDecimal value;

        BigDecimalLike(java.math.BigDecimal value) {
            this.value = value;
        }

        public String toString() {
            return value == null
                ? ""
                : "$" + value.toPlainString();
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest req,
            HttpServletResponse resp)
            throws IOException {

        resp.setContentType(
            "text/html;charset=UTF-8");

        PrintWriter out =
            resp.getWriter();

        String correlationId =
            req.getParameter("id");

        if (correlationId == null ||
            correlationId.isBlank()) {

            resp.setStatus(400);

            out.println(
                "<h1>Missing transaction ID</h1>");

            return;
        }

        out.println("""
        <!DOCTYPE html>
        <html>
        <head>

        <title>Transaction Trace</title>

        <style>

        body {
            font-family: Arial, sans-serif;
            background:#f8fafc;
            margin:24px;
            color:#0f172a;
        }

        a {
            color:#2563eb;
            text-decoration:none;
        }

        a:hover {
            text-decoration:underline;
        }

        .back {
            margin-bottom:18px;
        }

        .hero {
            background:white;
            padding:22px;
            border-radius:12px;
            box-shadow:0 1px 5px #cbd5e1;
            margin-bottom:22px;
        }

        .tx {
            font-family:monospace;
            font-size:16px;
            word-break:break-all;
        }

        .grid {
            display:grid;
            grid-template-columns:
                repeat(auto-fit,minmax(220px,1fr));
            gap:12px;
            margin-top:18px;
        }

        .box {
            background:#f8fafc;
            border:1px solid #e2e8f0;
            padding:12px;
            border-radius:8px;
        }

        .label {
            color:#64748b;
            font-size:12px;
            margin-bottom:5px;
        }

        .value {
            font-weight:bold;
            word-break:break-all;
        }

        .pass {
            color:#15803d;
            font-weight:bold;
        }

        .fail {
            color:#b91c1c;
            font-weight:bold;
        }

        table {
            width:100%;
            border-collapse:collapse;
            background:white;
            margin-bottom:28px;
            font-size:13px;
        }

        th,td {
            padding:9px;
            border-bottom:1px solid #e2e8f0;
            text-align:left;
            vertical-align:top;
        }

        th {
            background:#e2e8f0;
        }

        code {
            font-size:12px;
            word-break:break-all;
        }

        .timeline {
            background:white;
            border-radius:12px;
            padding:20px;
            box-shadow:0 1px 5px #cbd5e1;
            margin-bottom:28px;
        }

        .event {
            border-left:4px solid #94a3b8;
            padding:4px 0 20px 18px;
            margin-left:8px;
        }

        .event:last-child {
            padding-bottom:4px;
        }

        .event-title {
            font-weight:bold;
            font-size:15px;
        }

        .event-detail {
            margin-top:6px;
            color:#475569;
            line-height:1.6;
        }

        .full {
            word-break:break-all;
        }

        
/* OBSERVABILITY_V6_TRANSACTION_THEME */
:root{--v6-cyan:#67e8f9;--v6-green:#4ade80;--v6-red:#fb7185;--v6-text:#e8f2ff;--v6-line:#7dd3fc26}
*{box-sizing:border-box}
html{background:#020617!important}
body{margin:0!important;min-height:100vh!important;color:var(--v6-text)!important;font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif!important;background:radial-gradient(circle at 10% 7%,#0c4a6e55,transparent 27%),radial-gradient(circle at 90% 12%,#5b21b655,transparent 30%),linear-gradient(135deg,#020617,#071426 48%,#020617)!important;background-attachment:fixed!important;padding:22px max(22px,2vw) 70px!important}
body:before{content:"";position:fixed;inset:0;z-index:-1;background-image:linear-gradient(#38bdf809 1px,transparent 1px),linear-gradient(90deg,#38bdf809 1px,transparent 1px);background-size:42px 42px}
a{color:#7dd3fc!important;text-decoration:none!important;font-weight:850!important}a:hover{color:#bae6fd!important;text-shadow:0 0 14px #38bdf866}
h1{color:#f8fafc!important;font-size:clamp(34px,4vw,58px)!important;line-height:1!important;letter-spacing:-.035em!important;margin:8px 0 22px!important}h2{color:#f8fafc!important;font-size:22px!important}h3{color:#dbeafe!important}p{color:#b8c9df!important;line-height:1.65!important}
body>div,.container,.shell,.page,.content,.wrapper{width:min(1720px,96%)!important;max-width:1720px!important;margin-left:auto!important;margin-right:auto!important}
.card,.panel,.section,.summary,.header,.hero,.transaction-card,.assertion,.timeline,.timeline-card{background:linear-gradient(145deg,#0f172aee,#020617f2)!important;border:1px solid var(--v6-line)!important;border-radius:18px!important;box-shadow:0 18px 55px #0005!important;color:var(--v6-text)!important}.card{padding:16px!important}.section,.panel,.summary,.assertion,.timeline{padding:20px!important;margin:18px 0!important}
.grid,.cards,.summary-grid,.details-grid,.metrics{gap:12px!important}.label,.card-label,.meta-label{color:#7890ae!important;font-size:10px!important;font-weight:900!important;letter-spacing:.09em!important;text-transform:uppercase!important}.value,.card-value,.meta-value{color:#f8fafc!important;font-weight:850!important;word-break:break-word!important}
.good,.pass,.passed,.success{color:#86efac!important}.bad,.fail,.failed,.error{color:#fda4af!important}code{color:#93c5fd!important;background:#07111f!important;border:1px solid #60a5fa22!important;border-radius:7px!important;padding:2px 6px!important}
table{width:100%!important;border-collapse:collapse!important;background:#030a15!important;color:#d5e3f3!important;border:1px solid #94a3b824!important}th{padding:11px 12px!important;text-align:left!important;background:#0b1628!important;color:#7dd3fc!important;text-transform:uppercase!important;font-size:10px!important;border-bottom:1px solid #38bdf833!important}td{padding:11px 12px!important;background:#050d19!important;color:#cbd5e1!important;border-bottom:1px solid #94a3b815!important}tr:nth-child(even) td{background:#07111f!important}tr:hover td{background:#0b1b30!important}
.timeline-item,.event,.event-card,.timeline-entry{background:#06101d!important;border:1px solid #94a3b824!important;border-left:3px solid #38bdf8!important;border-radius:13px!important;padding:15px 17px!important;margin:10px 0!important;color:#dbeafe!important}.status,.badge,.pill{display:inline-flex!important;align-items:center!important;gap:6px!important;padding:6px 10px!important;border-radius:999px!important;font-size:10px!important;font-weight:900!important}.status.pass,.status.passed,.badge.pass,.badge.passed{color:#bbf7d0!important;background:#14532d88!important;border:1px solid #22c55e99!important}.status.fail,.status.failed,.badge.fail,.badge.failed{color:#fecaca!important;background:#7f1d1d55!important;border:1px solid #ef444488!important}
hr{border:0!important;border-top:1px solid #94a3b824!important}button,.button,.btn{background:#0b1b30!important;color:#dbeafe!important;border:1px solid #38bdf844!important;border-radius:10px!important;padding:9px 13px!important;font-weight:850!important}
@media(max-width:900px){body{padding:14px!important}.grid,.cards,.summary-grid,.details-grid,.metrics{grid-template-columns:1fr!important}h1{font-size:34px!important}}


/* OBSERVABILITY_V7_FULL_DARK */
body div[style*="background:white"],body div[style*="background: white"],body div[style*="background:#fff"],body div[style*="background: #fff"],body div[style*="background:#ffffff"],body div[style*="background: #ffffff"]{background:#07111f!important;color:#e8f2ff!important;border-color:#38bdf833!important}
.grid>div,.cards>div,.summary-grid>div,.details-grid>div,.metrics>div{background:linear-gradient(145deg,#0f172a,#07111f)!important;color:#e8f2ff!important;border:1px solid #38bdf833!important;border-radius:14px!important;box-shadow:none!important}
.grid>div *,.cards>div *,.summary-grid>div *,.details-grid>div *,.metrics>div *{background-color:transparent!important}
.v7-link{color:#67e8f9!important;text-decoration:underline!important;text-underline-offset:3px}

/* OBSERVABILITY_V8_LINK_STYLE */ .thread-link,.corr-link,a[href*="thread-inspect"],a[href*="transaction-trace"],a[href*="session-history"]{color:#67e8f9!important;text-decoration:underline!important;text-underline-offset:3px!important;font-weight:900!important}</style>
        </head>

        <body>

        <div class="back">
          <a href="observability">
          ← Back to Application Observability
          </a>
        </div>
        """);

        /*
         * ------------------------------------------------
         * BUSINESS TRANSACTION HEADER
         * ------------------------------------------------
         */

        boolean found = false;

        String username = null;
        String httpSession = null;
        String stripeSession = null;
        String paymentIntent = null;
        String status = null;

        java.math.BigDecimal expected = null;
        java.math.BigDecimal stripeAmount = null;
        java.math.BigDecimal dbAmount = null;

        Timestamp started = null;
        Timestamp checkoutCreated = null;
        Timestamp webhookReceived = null;
        Timestamp dbCommitted = null;
        Timestamp completed = null;

        try (Connection conn = Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       http_session_id,
                       username,
                       stripe_session_id,
                       stripe_payment_intent,
                       expected_amount,
                       stripe_amount,
                       database_amount,
                       status,
                       started_at,
                       checkout_created_at,
                       webhook_received_at,
                       db_committed_at,
                       completed_at
                     FROM app_transactions
                     WHERE correlation_id = ?
                     """)) {

            ps.setString(
                1,
                correlationId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                if (rs.next()) {

                    found = true;

                    httpSession =
                        rs.getString(1);

                    username =
                        rs.getString(2);

                    stripeSession =
                        rs.getString(3);

                    paymentIntent =
                        rs.getString(4);

                    expected =
                        rs.getBigDecimal(5);

                    stripeAmount =
                        rs.getBigDecimal(6);

                    dbAmount =
                        rs.getBigDecimal(7);

                    status =
                        rs.getString(8);

                    started =
                        rs.getTimestamp(9);

                    checkoutCreated =
                        rs.getTimestamp(10);

                    webhookReceived =
                        rs.getTimestamp(11);

                    dbCommitted =
                        rs.getTimestamp(12);

                    completed =
                        rs.getTimestamp(13);
                }
            }

        } catch (Exception e) {

            out.println(
                "<h2>Database error: " +
                esc(e.getMessage()) +
                "</h2>");

            return;
        }

        if (!found) {

            resp.setStatus(404);

            out.println(
                "<h1>Transaction not found</h1>");

            out.println(
                "<code>" +
                esc(correlationId) +
                "</code>");

            return;
        }

        boolean passed =
            "PASSED".equalsIgnoreCase(status);

        out.println(
            "<div class='hero'>");

        out.println(
            "<h1>Transaction Trace</h1>");

        out.println(
            "<div class='tx'>" +
            esc(correlationId) +
            "</div>");

        out.println(
            "<div class='grid'>");

        box(
            out,
            "Status",
            status,
            passed ? "pass" : "fail");

        box(
            out,
            "User",
            username,
            "");

        box(
            out,
            "Expected Amount",
            expected == null
                ? ""
                : "$" + expected.toPlainString(),
            "");

        box(
            out,
            "Stripe Amount",
            stripeAmount == null
                ? ""
                : "$" +
                  stripeAmount.toPlainString(),
            "");

        box(
            out,
            "Database Amount",
            dbAmount == null
                ? ""
                : "$" +
                  dbAmount.toPlainString(),
            "");

        box(
            out,
            "HTTP Session",
            httpSession,
            "");

        box(
            out,
            "Stripe Session",
            stripeSession,
            "");

        box(
            out,
            "Payment Intent",
            paymentIntent,
            "");

        box(
            out,
            "Started",
            started == null
                ? ""
                : started.toString(),
            "");

        box(
            out,
            "Completed",
            completed == null
                ? ""
                : completed.toString(),
            "");

        out.println("</div>");
        out.println("</div>");


        /*
         * ------------------------------------------------
         * BUSINESS ASSERTION
         * ------------------------------------------------
         */

        boolean amountsMatch =
            expected != null &&
            stripeAmount != null &&
            dbAmount != null &&
            expected.compareTo(stripeAmount) == 0 &&
            stripeAmount.compareTo(dbAmount) == 0;

        out.println(
            "<div class='hero'>");

        out.println(
            "<h2>Business Assertion</h2>");

        out.println(
            "<div class='" +
            (passed && amountsMatch
                ? "pass"
                : "fail") +
            "'>");

        if (passed && amountsMatch) {

            out.println(
                "✓ PASS — Stripe payment and " +
                "database amounts match.");

        } else {

            out.println(
                "✗ ASSERTION INCOMPLETE OR FAILED");
        }

        out.println("</div>");

        out.println(
            "<p>" +
            "Expected: <b>" +
            (expected == null
                ? "N/A"
                : "$" +
                  expected.toPlainString()) +
            "</b> · Stripe: <b>" +
            (stripeAmount == null
                ? "N/A"
                : "$" +
                  stripeAmount.toPlainString()) +
            "</b> · Database: <b>" +
            (dbAmount == null
                ? "N/A"
                : "$" +
                  dbAmount.toPlainString()) +
            "</b></p>");

        out.println("</div>");


        /*
         * ------------------------------------------------
         * EVENT TIMELINE
         * ------------------------------------------------
         */

        out.println(
            "<h2>Transaction Event Timeline</h2>");

        out.println(
            "<div class='timeline'>");

        try (Connection conn = Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       event_time,
                       event_type,
                       component,
                       detail,
                       http_session_id,
                       stripe_session_id,
                       request_id,
                       tomcat_thread_name,
                       jvm_thread_id,
                       db_backend_pid,
                       duration_ms,
                       success
                     FROM app_transaction_events
                     WHERE correlation_id = ?
                     ORDER BY event_time, id
                     """)) {

            ps.setString(
                1,
                correlationId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                boolean any = false;

                while (rs.next()) {

                    any = true;

                    boolean success =
                        rs.getBoolean(12);

                    out.println(
                        "<div class='event'>");

                    out.println(
                        "<div class='event-title " +
                        (success
                            ? "pass"
                            : "fail") +
                        "'>" +
                        (success ? "✓ " : "✗ ") +
                        esc(rs.getString(2)) +
                        "</div>");

                    out.println(
                        "<div class='event-detail'>");

                    out.println(
                        "<b>Time:</b> " +
                        rs.getTimestamp(1) +
                        "<br>");

                    out.println(
                        "<b>Component:</b> " +
                        esc(rs.getString(3)) +
                        "<br>");

                    if (rs.getString(4) != null) {
                        out.println(
                            "<b>Detail:</b> " +
                            esc(rs.getString(4)) +
                            "<br>");
                    }

                    if (rs.getString(8) != null) {
                        out.println(
                            "<b>Tomcat thread:</b> " +
                            "<code>" +
                            esc(rs.getString(8)) +
                            "</code><br>");
                    }

                    long jvmTid =
                        rs.getLong(9);

                    if (!rs.wasNull()) {
                        out.println(
                            "<b>JVM thread ID:</b> " +
                            jvmTid +
                            "<br>");
                    }

                    int pid =
                        rs.getInt(10);

                    if (!rs.wasNull()) {
                        out.println(
                            "<b>PostgreSQL PID:</b> " +
                            pid +
                            "<br>");
                    }

                    long duration =
                        rs.getLong(11);

                    if (!rs.wasNull()) {
                        out.println(
                            "<b>Duration:</b> " +
                            duration +
                            " ms<br>");
                    }

                    if (rs.getString(6) != null) {
                        out.println(
                            "<b>Stripe session:</b> " +
                            "<code class='full'>" +
                            esc(rs.getString(6)) +
                            "</code><br>");
                    }

                    if (rs.getString(7) != null) {
                        out.println(
                            "<b>Request/Event ID:</b> " +
                            "<code class='full'>" +
                            esc(rs.getString(7)) +
                            "</code><br>");
                    }

                    out.println("</div>");
                    out.println("</div>");
                }

                if (!any) {

                    out.println(
                        "No transaction events recorded.");
                }
            }

        } catch (Exception e) {

            out.println(
                "EVENT ERROR: " +
                esc(e.getMessage()));
        }

        out.println("</div>");


        /*
         * ------------------------------------------------
         * HTTP TRACE
         * ------------------------------------------------
         */

        out.println(
            "<h2>Correlated HTTP Request Trace</h2>");

        out.println("""
        <table>
        <tr>
          <th>Time</th>
          <th>Request ID</th>
          <th>Method</th>
          <th>Endpoint</th>
          <th>HTTP</th>
          <th>ms</th>
          <th>Tomcat Thread</th>
          <th>JVM TID</th>
          <th>Postgres PID</th>
          <th>Session</th>
          <th>Stripe Session</th>
        </tr>
        """);

        try (Connection conn = Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       request_time,
                       request_id,
                       method,
                       uri,
                       http_status,
                       duration_ms,
                       thread_name,
                       jvm_thread_id,
                       db_backend_pid,
                       session_id,
                       stripe_session_id
                     FROM app_request_history
                     WHERE correlation_id = ?
                     ORDER BY request_time
                     """)) {

            ps.setString(
                1,
                correlationId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                while (rs.next()) {

                    out.println("<tr>");

                    out.println(
                        "<td>" +
                        rs.getTimestamp(1) +
                        "</td>");

                    out.println(
                        "<td><code>" +
                        esc(rs.getString(2)) +
                        "</code></td>");

                    out.println(
                        "<td>" +
                        esc(rs.getString(3)) +
                        "</td>");

                    out.println(
                        "<td><code>" +
                        esc(rs.getString(4)) +
                        "</code></td>");

                    out.println(
                        "<td>" +
                        rs.getInt(5) +
                        "</td>");

                    out.println(
                        "<td>" +
                        rs.getLong(6) +
                        "</td>");

                    out.println(
                        "<td><code>" +
                        esc(rs.getString(7)) +
                        "</code></td>");

                    long tid =
                        rs.getLong(8);

                    out.println(
                        "<td>" +
                        (rs.wasNull()
                            ? ""
                            : Long.toString(tid)) +
                        "</td>");

                    int pid =
                        rs.getInt(9);

                    out.println(
                        "<td>" +
                        (rs.wasNull()
                            ? ""
                            : Integer.toString(pid)) +
                        "</td>");

                    out.println(
                        "<td><code>" +
                        esc(rs.getString(10)) +
                        "</code></td>");

                    out.println(
                        "<td><code>" +
                        esc(rs.getString(11)) +
                        "</code></td>");

                    out.println("</tr>");
                }
            }

        } catch (Exception e) {

            out.println(
                "<tr><td colspan='11'>" +
                esc(e.getMessage()) +
                "</td></tr>");
        }

        out.println("</table>");


        /*
         * ------------------------------------------------
         * BUSINESS DB RECORD
         * ------------------------------------------------
         */

        out.println(
            "<h2>Business Database Evidence</h2>");

        out.println("""
        <table>
        <tr>
          <th>Sale ID</th>
          <th>Sale Status</th>
          <th>Sale Amount</th>
          <th>Payment ID</th>
          <th>Payment Status</th>
          <th>Payment Amount</th>
          <th>Stripe Session</th>
        </tr>
        """);

        try (Connection conn = Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       s.id,
                       s.sale_status,
                       s.total_amount,
                       p.id,
                       p.payment_status,
                       p.amount,
                       s.stripe_session_id
                     FROM sales s
                     JOIN payments p
                       ON p.sale_id = s.id
                     WHERE s.stripe_session_id = ?
                     """)) {

            ps.setString(
                1,
                stripeSession);

            try (ResultSet rs =
                     ps.executeQuery()) {

                while (rs.next()) {

                    out.println("<tr>");

                    out.println(
                        "<td>" +
                        rs.getLong(1) +
                        "</td>");

                    out.println(
                        "<td class='pass'>" +
                        esc(rs.getString(2)) +
                        "</td>");

                    out.println(
                        "<td>$" +
                        rs.getBigDecimal(3) +
                        "</td>");

                    out.println(
                        "<td>" +
                        rs.getLong(4) +
                        "</td>");

                    out.println(
                        "<td class='pass'>" +
                        esc(rs.getString(5)) +
                        "</td>");

                    out.println(
                        "<td>$" +
                        rs.getBigDecimal(6) +
                        "</td>");

                    out.println(
                        "<td><code>" +
                        esc(rs.getString(7)) +
                        "</code></td>");

                    out.println("</tr>");
                }
            }

        } catch (Exception e) {

            out.println(
                "<tr><td colspan='7'>" +
                esc(e.getMessage()) +
                "</td></tr>");
        }

        out.println("</table>");


        /*
         * Timing summary
         */
        out.println(
            "<div class='hero'>");

        out.println(
            "<h2>Transaction Timing</h2>");

        out.println(
            "<b>Started:</b> " +
            started + "<br>");

        out.println(
            "<b>Stripe checkout created:</b> " +
            checkoutCreated + "<br>");

        out.println(
            "<b>Webhook received:</b> " +
            webhookReceived + "<br>");

        out.println(
            "<b>Database committed:</b> " +
            dbCommitted + "<br>");

        out.println(
            "<b>Completed:</b> " +
            completed);

        out.println("</div>");

        out.println("""
        </body>
        </html>
        """);
    }

    private void box(
            PrintWriter out,
            String label,
            String value,
            String css) {

        out.println(
            "<div class='box'>" +
            "<div class='label'>" +
            esc(label) +
            "</div>" +
            "<div class='value " +
            css +
            "'>" +
            esc(value) +
            "</div></div>"
        );
    }
}
