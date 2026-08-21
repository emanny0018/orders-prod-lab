package com.manny.orders;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@WebServlet("/session-history")
public class SessionHistoryServlet extends HttpServlet {

    private String esc(String s) {
        if (s == null) return "";

        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String formatDuration(
            Timestamp start,
            Timestamp end) {

        if (start == null) return "N/A";

        LocalDateTime a =
            start.toLocalDateTime();

        LocalDateTime b =
            end == null
                ? LocalDateTime.now()
                : end.toLocalDateTime();

        Duration d =
            Duration.between(a, b);

        long seconds =
            Math.max(0, d.getSeconds());

        long days =
            seconds / 86400;

        seconds %= 86400;

        long hours =
            seconds / 3600;

        seconds %= 3600;

        long minutes =
            seconds / 60;

        seconds %= 60;

        if (days > 0) {
            return days + "d " +
                   hours + "h " +
                   minutes + "m " +
                   seconds + "s";
        }

        if (hours > 0) {
            return hours + "h " +
                   minutes + "m " +
                   seconds + "s";
        }

        return minutes + "m " +
               seconds + "s";
    }

    private String statusClass(
            String status) {

        if (status == null) {
            return "";
        }

        if ("ACTIVE".equalsIgnoreCase(status)) {
            return "warn";
        }

        if ("DESTROYED".equalsIgnoreCase(status) ||
            "ABANDONED_ON_RESTART"
                .equalsIgnoreCase(status)) {

            return "neutral";
        }

        return "neutral";
    }

    @Override
    protected void doGet(
            HttpServletRequest req,
            HttpServletResponse resp)
            throws IOException {

        String sessionId =
            req.getParameter("id");

        if (sessionId == null ||
            sessionId.isBlank()) {

            resp.setStatus(400);
            resp.setContentType(
                "text/plain;charset=UTF-8");

            resp.getWriter().println(
                "Missing session id.");

            return;
        }

        boolean download =
            "txt".equalsIgnoreCase(
                req.getParameter("format")
            );

        if (download) {
            renderTextReport(
                sessionId,
                resp);
        } else {
            renderHtml(
                sessionId,
                resp);
        }
    }


    // ========================================================
    // HTML VIEW
    // ========================================================

    private void renderHtml(
            String sessionId,
            HttpServletResponse resp)
            throws IOException {

        resp.setContentType(
            "text/html;charset=UTF-8");

        PrintWriter out =
            resp.getWriter();

        SessionSummary s =
            loadSession(sessionId);

        if (s == null) {

            resp.setStatus(404);

            out.println(
                "<h1>Session not found</h1>");

            return;
        }

        RequestStats stats =
            loadRequestStats(sessionId);

        TransactionStats txStats =
            loadTransactionStats(sessionId);

        String encoded =
            URLEncoder.encode(
                sessionId,
                StandardCharsets.UTF_8
            );

        String health =
            calculateHealth(
                stats,
                txStats);

        out.println("""
        <!DOCTYPE html>
        <html>
        <head>

        <title>Application Session History</title>

        
        
        <style>
        :root {
            --bg:#020617;
            --panel:rgba(8,15,32,.88);
            --panel2:rgba(15,23,42,.82);
            --line:rgba(56,189,248,.20);
            --cyan:#22d3ee;
            --blue:#38bdf8;
            --green:#4ade80;
            --amber:#fbbf24;
            --red:#fb7185;
            --purple:#c084fc;
            --text:#e2e8f0;
            --muted:#94a3b8;
        }

        * {
            box-sizing:border-box;
        }

        html {
            scroll-behavior:smooth;
        }

        body {
            margin:0;
            padding:0;
            min-height:100vh;
            color:var(--text);
            font-family:
                Inter,
                ui-sans-serif,
                system-ui,
                -apple-system,
                BlinkMacSystemFont,
                "Segoe UI",
                sans-serif;

            background:
                radial-gradient(circle at 15% 20%,
                    rgba(14,165,233,.13), transparent 28%),
                radial-gradient(circle at 82% 15%,
                    rgba(168,85,247,.10), transparent 26%),
                radial-gradient(circle at 70% 80%,
                    rgba(34,197,94,.08), transparent 30%),
                linear-gradient(135deg,#020617 0%,#07111f 48%,#020617 100%);

            background-attachment:fixed;
            overflow-x:hidden;
        }

        body::before {
            content:"";
            position:fixed;
            inset:0;
            pointer-events:none;
            z-index:-2;
            opacity:.42;

            background-image:
                linear-gradient(rgba(56,189,248,.06) 1px,transparent 1px),
                linear-gradient(90deg,rgba(56,189,248,.06) 1px,transparent 1px);

            background-size:48px 48px;

            animation:gridMove 22s linear infinite;
        }

        body::after {
            content:"";
            position:fixed;
            inset:-30%;
            z-index:-3;
            pointer-events:none;

            background:
                conic-gradient(
                    from 180deg,
                    transparent,
                    rgba(34,211,238,.04),
                    transparent,
                    rgba(168,85,247,.04),
                    transparent
                );

            animation:radar 28s linear infinite;
        }

        @keyframes gridMove {
            from { transform:translateY(0); }
            to   { transform:translateY(48px); }
        }

        @keyframes radar {
            from { transform:rotate(0deg); }
            to   { transform:rotate(360deg); }
        }

        @keyframes pulse {
            0%,100% {
                box-shadow:
                    0 0 8px rgba(74,222,128,.35),
                    0 0 20px rgba(74,222,128,.08);
            }
            50% {
                box-shadow:
                    0 0 16px rgba(74,222,128,.75),
                    0 0 34px rgba(74,222,128,.18);
            }
        }

        @keyframes marqueeGlow {
            0%,100% {
                text-shadow:
                    0 0 10px rgba(34,211,238,.40),
                    0 0 26px rgba(34,211,238,.12);
            }
            50% {
                text-shadow:
                    0 0 18px rgba(34,211,238,.80),
                    0 0 45px rgba(34,211,238,.25);
            }
        }

        a {
            color:#7dd3fc;
            text-decoration:none;
        }

        a:hover {
            color:#bae6fd;
            text-decoration:none;
        }

        body > .toolbar,
        body > .hero,
        body > .section {
            width:min(1500px,calc(100% - 44px));
            margin-left:auto;
            margin-right:auto;
        }

        .toolbar {
            display:flex;
            justify-content:space-between;
            align-items:center;
            gap:14px;

            margin-top:22px;
            margin-bottom:18px;

            padding:13px 16px;

            border:1px solid rgba(56,189,248,.18);
            border-radius:14px;

            background:rgba(2,6,23,.72);
            backdrop-filter:blur(18px);

            box-shadow:
                0 12px 40px rgba(0,0,0,.28),
                inset 0 1px 0 rgba(255,255,255,.04);
        }

        .toolbar > a:first-child::before {
            content:"⌂  ";
            color:var(--cyan);
        }

        .btn {
            display:inline-flex;
            align-items:center;
            gap:8px;

            padding:10px 15px;

            color:#e0f2fe;
            font-weight:800;
            font-size:12px;
            letter-spacing:.07em;
            text-transform:uppercase;

            border:1px solid rgba(34,211,238,.42);
            border-radius:9px;

            background:
                linear-gradient(
                    135deg,
                    rgba(8,145,178,.30),
                    rgba(30,64,175,.25)
                );

            box-shadow:
                inset 0 0 20px rgba(34,211,238,.05),
                0 0 20px rgba(34,211,238,.05);

            transition:.2s ease;
        }

        .btn::before {
            content:"⇩";
            font-size:15px;
        }

        .btn:hover {
            transform:translateY(-1px);
            border-color:rgba(34,211,238,.8);
            box-shadow:0 0 25px rgba(34,211,238,.15);
            color:white;
        }

        .hero {
            position:relative;
            overflow:hidden;

            margin-bottom:24px;
            padding:34px;

            border:1px solid rgba(56,189,248,.24);
            border-radius:22px;

            background:
                linear-gradient(
                    135deg,
                    rgba(15,23,42,.96),
                    rgba(2,6,23,.90)
                );

            box-shadow:
                0 24px 70px rgba(0,0,0,.40),
                inset 0 1px 0 rgba(255,255,255,.05);
        }

        .hero::before {
            content:"";
            position:absolute;
            left:0;
            top:0;
            width:100%;
            height:3px;

            background:
                linear-gradient(
                    90deg,
                    transparent,
                    var(--cyan),
                    var(--purple),
                    var(--green),
                    transparent
                );

            box-shadow:0 0 22px rgba(34,211,238,.55);
        }

        .hero::after {
            content:"LIVE TROUBLESHOOTING TELEMETRY";
            position:absolute;
            right:26px;
            top:22px;

            padding:7px 11px;

            color:#86efac;
            font-family:ui-monospace,SFMono-Regular,Menlo,monospace;
            font-size:10px;
            font-weight:900;
            letter-spacing:.14em;

            border:1px solid rgba(74,222,128,.28);
            border-radius:999px;

            background:rgba(22,101,52,.12);
        }

        h1 {
            margin:8px 0 8px;

            font-size:clamp(30px,4vw,58px);
            line-height:1;
            letter-spacing:.10em;
            font-weight:950;

            color:#f8fafc;

            animation:marqueeGlow 4s ease-in-out infinite;
        }

        h1::before {
            content:"◈ ";
            color:var(--cyan);
        }

        h2 {
            display:flex;
            align-items:center;
            gap:10px;

            margin:0 0 15px;

            color:#f1f5f9;
            font-size:19px;
            letter-spacing:.04em;
        }

        h2::before {
            content:"◆";
            color:var(--cyan);
            font-size:13px;
            filter:drop-shadow(0 0 6px rgba(34,211,238,.7));
        }

        h3 {
            color:#f8fafc;
        }

        .hero > p {
            max-width:900px;
            color:#94a3b8;
            font-size:14px;
            line-height:1.7;
        }

        .hero > p::before {
            content:"SYSTEM TRACE // ";
            color:#38bdf8;
            font-family:ui-monospace,SFMono-Regular,Menlo,monospace;
            font-weight:800;
        }

        .session-id {
            position:relative;

            margin-top:6px;
            padding:15px 17px;

            color:#a5f3fc;
            font-family:
                ui-monospace,
                SFMono-Regular,
                Menlo,
                Monaco,
                Consolas,
                monospace;

            font-size:14px;
            letter-spacing:.03em;
            word-break:break-all;

            border:1px solid rgba(34,211,238,.20);
            border-left:3px solid var(--cyan);
            border-radius:9px;

            background:rgba(2,6,23,.75);

            box-shadow:
                inset 0 0 24px rgba(34,211,238,.025);
        }

        .grid {
            display:grid;
            grid-template-columns:
                repeat(auto-fit,minmax(185px,1fr));

            gap:12px;
            margin-top:18px;
        }

        .box {
            position:relative;
            overflow:hidden;

            min-height:92px;
            padding:15px;

            border:1px solid rgba(148,163,184,.15);
            border-radius:12px;

            background:
                linear-gradient(
                    145deg,
                    rgba(15,23,42,.82),
                    rgba(2,6,23,.72)
                );

            box-shadow:
                inset 0 1px 0 rgba(255,255,255,.025);

            transition:
                transform .18s ease,
                border-color .18s ease,
                box-shadow .18s ease;
        }

        .box:hover {
            transform:translateY(-2px);
            border-color:rgba(56,189,248,.35);
            box-shadow:0 10px 30px rgba(0,0,0,.24);
        }

        .box::after {
            content:"";
            position:absolute;
            left:0;
            bottom:0;
            height:2px;
            width:100%;

            background:
                linear-gradient(
                    90deg,
                    var(--cyan),
                    transparent
                );

            opacity:.35;
        }

        .label {
            margin-bottom:7px;

            color:#64748b;
            font-size:10px;
            font-weight:900;
            letter-spacing:.12em;
            text-transform:uppercase;
        }

        .value {
            color:#f8fafc;
            font-size:17px;
            font-weight:850;
            word-break:break-word;
        }

        .good {
            color:#86efac !important;
        }

        .bad {
            color:#fda4af !important;
        }

        .warn {
            color:#fde68a !important;
        }

        .neutral {
            color:#cbd5e1 !important;
        }

        .hero .value.good {
            display:inline-block;
            padding:5px 10px;
            border:1px solid rgba(74,222,128,.30);
            border-radius:999px;
            background:rgba(22,101,52,.15);
            animation:pulse 2.6s infinite;
        }

        .hero .value.bad {
            display:inline-block;
            padding:5px 10px;
            border:1px solid rgba(251,113,133,.38);
            border-radius:999px;
            background:rgba(159,18,57,.16);
        }

        .section {
            position:relative;

            margin-bottom:22px;
            padding:22px;

            border:1px solid rgba(148,163,184,.13);
            border-radius:18px;

            background:
                linear-gradient(
                    145deg,
                    rgba(8,15,32,.88),
                    rgba(2,6,23,.84)
                );

            box-shadow:
                0 16px 45px rgba(0,0,0,.25),
                inset 0 1px 0 rgba(255,255,255,.025);

            backdrop-filter:blur(14px);
        }

        .section > p {
            color:#94a3b8;
            line-height:1.65;
            font-size:13px;
        }

        .trace {
            position:relative;
            overflow:auto;

            max-height:900px;

            padding:24px;

            color:#cbd5e1;
            font-family:
                ui-monospace,
                SFMono-Regular,
                Menlo,
                Monaco,
                Consolas,
                monospace;

            font-size:12px;
            line-height:1.75;

            border:1px solid rgba(34,211,238,.18);
            border-radius:14px;

            background:
                linear-gradient(
                    180deg,
                    rgba(0,5,15,.98),
                    rgba(2,6,23,.98)
                );

            box-shadow:
                inset 0 0 60px rgba(14,165,233,.035),
                0 16px 40px rgba(0,0,0,.25);
        }

        .trace::before {
            content:"●  ●  ●    EXECUTION TRACE";
            display:block;

            margin:-24px -24px 20px;
            padding:10px 15px;

            color:#64748b;
            font-size:10px;
            letter-spacing:.12em;

            border-bottom:1px solid rgba(148,163,184,.12);
            background:#060b16;
        }

        .trace .time {
            color:#67e8f9;
        }

        .trace .ok {
            color:#86efac;
            font-weight:800;
        }

        .trace .err {
            color:#fda4af;
            font-weight:900;
        }

        .trace .component {
            color:#fde68a;
            font-weight:900;
        }

        table {
            width:100%;
            margin:0;
            border-collapse:separate;
            border-spacing:0;

            color:#cbd5e1;
            font-size:12px;

            border:1px solid rgba(148,163,184,.12);
            border-radius:12px;

            background:rgba(2,6,23,.62);
            overflow:hidden;
        }

        th {
            position:sticky;
            top:0;
            z-index:2;

            padding:12px 10px;

            color:#7dd3fc;
            font-size:10px;
            font-weight:900;
            letter-spacing:.08em;
            text-transform:uppercase;
            text-align:left;

            border-bottom:1px solid rgba(56,189,248,.18);

            background:#0b1220;
        }

        td {
            padding:11px 10px;

            border-bottom:1px solid rgba(148,163,184,.08);

            text-align:left;
            vertical-align:top;
        }

        tr:last-child td {
            border-bottom:none;
        }

        tbody tr {
            transition:background .15s ease;
        }

        tbody tr:hover {
            background:rgba(14,165,233,.055);
        }

        code {
            color:#a5f3fc;
            font-size:11px;
            word-break:break-all;
        }

        /* ----------------------------------------------------
           INFRASTRUCTURE VISUAL STRIP
           ---------------------------------------------------- */

        .hero .grid::before {
            content:"CLIENT  ◉────▶  HTTP  ◉────▶  TOMCAT  ◉────▶  JVM  ◉────▶  JDBC  ◉────▶  POSTGRESQL  ◉────▶  STRIPE";
            grid-column:1 / -1;

            display:block;
            overflow-x:auto;

            margin:4px 0 7px;
            padding:13px 16px;

            color:#67e8f9;
            white-space:nowrap;

            font-family:
                ui-monospace,
                SFMono-Regular,
                Menlo,
                monospace;

            font-size:11px;
            font-weight:900;
            letter-spacing:.09em;
            text-align:center;

            border:1px solid rgba(34,211,238,.14);
            border-radius:10px;

            background:
                linear-gradient(
                    90deg,
                    rgba(8,145,178,.07),
                    rgba(15,23,42,.50),
                    rgba(126,34,206,.06)
                );

            box-shadow:
                inset 0 0 28px rgba(34,211,238,.025);
        }

        /* ----------------------------------------------------
           TROUBLESHOOTING SECTION EMPHASIS
           ---------------------------------------------------- */

        .section:last-of-type {
            border-color:rgba(251,191,36,.17);
        }

        .section:last-of-type h2::before {
            content:"⚠";
            color:#fbbf24;
        }

        /* ----------------------------------------------------
           SCROLLBAR
           ---------------------------------------------------- */

        ::-webkit-scrollbar {
            width:9px;
            height:9px;
        }

        ::-webkit-scrollbar-track {
            background:#020617;
        }

        ::-webkit-scrollbar-thumb {
            background:#1e3a5f;
            border-radius:10px;
        }

        ::-webkit-scrollbar-thumb:hover {
            background:#075985;
        }

        /* ----------------------------------------------------
           MOBILE
           ---------------------------------------------------- */

        @media (max-width:800px) {
            body > .toolbar,
            body > .hero,
            body > .section {
                width:calc(100% - 20px);
            }

            .toolbar {
                align-items:flex-start;
                flex-direction:column;
            }

            .hero {
                padding:24px 18px;
            }

            .hero::after {
                position:static;
                display:inline-block;
                margin-bottom:12px;
            }

            h1 {
                font-size:30px;
                line-height:1.15;
            }

            .section {
                padding:15px;
                overflow-x:auto;
            }

            .trace {
                padding:18px;
            }

            .trace::before {
                margin:-18px -18px 18px;
            }
        }
        
/* ASH_MARQUEE_V5 */
.ashmq{width:100%;overflow:hidden;margin:10px 0 22px;padding:15px 0;border-top:1px solid rgba(56,189,248,.22);border-bottom:1px solid rgba(56,189,248,.22);background:linear-gradient(90deg,rgba(2,6,23,.10),rgba(8,47,73,.25),rgba(76,29,149,.20),rgba(2,6,23,.10))}
.ashtrack{display:inline-block;min-width:max-content;white-space:nowrap;padding-left:100%;animation:ashmove 18s linear infinite}.ashtext{display:inline-block;padding-right:100px;font-size:clamp(26px,4vw,52px);font-weight:950;letter-spacing:.18em;background:linear-gradient(90deg,#fff,#67e8f9,#c4b5fd,#fff);-webkit-background-clip:text;color:transparent}.ashmq:hover .ashtrack{animation-play-state:paused}@keyframes ashmove{from{transform:translateX(0)}to{transform:translateX(-100%)}}

</style>



        </head>

        <body>
        """);

        out.println(
            "<div class='toolbar'>");

        out.println(
            "<a href='observability'>" +
            "← Back to Application Observability" +
            "</a>");

        out.println(
            "<a class='btn' href='" +
            "session-history?id=" +
            encoded +
            "&format=txt'>" +
            "Download ASH Report" +
            "</a>");

        out.println("</div>");


        // ----------------------------------------------------
        // SESSION HEADER
        // ----------------------------------------------------

        out.println(
            "<div class='hero'>");

        out.println(
            "<div class='ashmq'><div class='ashtrack'><span class='ashtext'>APPLICATION SESSION HISTORY &nbsp;&nbsp;◆&nbsp;&nbsp; APPLICATION SESSION HISTORY &nbsp;&nbsp;◆&nbsp;&nbsp;</span></div></div>");

        out.println(
            "<p>" +
            "Chronological application execution " +
            "history for one HTTP session." +
            "</p>");

        out.println(
            "<div class='label'>" +
            "FULL HTTP SESSION / JSESSIONID" +
            "</div>");

        out.println(
            "<div class='session-id'>" +
            esc(sessionId) +
            "</div>");

        out.println(
            "<div class='grid'>");

        metric(
            out,
            "User",
            safe(s.username),
            "");

        metric(
            out,
            "Session Status",
            safe(s.status),
            statusClass(s.status));

        metric(
            out,
            "Overall Health",
            health,
            "HEALTHY".equals(health)
                ? "good"
                : "bad");

        metric(
            out,
            "Created",
            String.valueOf(s.createdAt),
            "");

        metric(
            out,
            "Last Seen",
            String.valueOf(s.lastSeenAt),
            "");

        metric(
            out,
            "Ended",
            s.endedAt == null
                ? "Still Active"
                : s.endedAt.toString(),
            "");

        metric(
            out,
            "Session Age / Duration",
            formatDuration(
                s.createdAt,
                s.endedAt),
            "");

        metric(
            out,
            "Recorded Requests",
            Long.toString(
                stats.totalRequests),
            "");

        out.println("</div>");
        out.println("</div>");


        // ----------------------------------------------------
        // PERFORMANCE SUMMARY
        // ----------------------------------------------------

        out.println(
            "<div class='section'>");

        out.println(
            "<h2>Session Performance Summary</h2>");

        out.println(
            "<div class='grid'>");

        metric(
            out,
            "HTTP 2xx",
            Long.toString(stats.http2xx),
            stats.http2xx > 0
                ? "good"
                : "");

        metric(
            out,
            "HTTP 3xx",
            Long.toString(stats.http3xx),
            "");

        metric(
            out,
            "HTTP 4xx",
            Long.toString(stats.http4xx),
            stats.http4xx > 0
                ? "warn"
                : "good");

        metric(
            out,
            "HTTP 5xx",
            Long.toString(stats.http5xx),
            stats.http5xx > 0
                ? "bad"
                : "good");

        metric(
            out,
            "Average Latency",
            stats.avgMs + " ms",
            "");

        metric(
            out,
            "P50 Latency",
            stats.p50Ms + " ms",
            "");

        metric(
            out,
            "P95 Latency",
            stats.p95Ms + " ms",
            "");

        metric(
            out,
            "P99 Latency",
            stats.p99Ms + " ms",
            "");

        metric(
            out,
            "Maximum Latency",
            stats.maxMs + " ms",
            stats.maxMs >= 1000
                ? "warn"
                : "");

        metric(
            out,
            "Slow Requests ≥1s",
            Long.toString(
                stats.slowRequests),
            stats.slowRequests > 0
                ? "warn"
                : "good");

        metric(
            out,
            "Failed Requests",
            Long.toString(
                stats.failedRequests),
            stats.failedRequests > 0
                ? "bad"
                : "good");

        metric(
            out,
            "Distinct Endpoints",
            Long.toString(
                stats.uniqueEndpoints),
            "");

        out.println("</div>");
        out.println("</div>");


        // ----------------------------------------------------
        // RESOURCE / EXECUTION SUMMARY
        // ----------------------------------------------------

        out.println(
            "<div class='section'>");

        out.println(
            "<h2>Execution Resource History</h2>");

        out.println(
            "<div class='grid'>");

        metric(
            out,
            "Tomcat Workers Used",
            Long.toString(
                stats.uniqueTomcatThreads),
            "");

        metric(
            out,
            "JVM Thread IDs Used",
            Long.toString(
                stats.uniqueJvmThreads),
            "");

        metric(
            out,
            "PostgreSQL Backends Used",
            Long.toString(
                stats.uniqueDbPids),
            "");

        metric(
            out,
            "Client Addresses",
            Long.toString(
                stats.uniqueClients),
            "");

        metric(
            out,
            "Business Transactions",
            Long.toString(
                txStats.total),
            "");

        metric(
            out,
            "Passed Transactions",
            Long.toString(
                txStats.passed),
            txStats.passed > 0
                ? "good"
                : "");

        metric(
            out,
            "Failed Transactions",
            Long.toString(
                txStats.failed),
            txStats.failed > 0
                ? "bad"
                : "good");

        metric(
            out,
            "Stripe Transactions",
            Long.toString(
                txStats.stripeTransactions),
            "");

        out.println("</div>");
        out.println("</div>");


        // ----------------------------------------------------
        // STACK-TRACE-LIKE SESSION EXECUTION TRACE
        // ----------------------------------------------------

        out.println(
            "<div class='section'>");

        out.println(
            "<h2>Complete Session Execution Trace</h2>");

        out.println(
            "<p>" +
            "Read from top to bottom like an " +
            "execution stack/history. Indentation shows " +
            "which technical resources participated." +
            "</p>");

        out.println(
            "<div class='trace'>");

        out.println(
            "<span class='time'>" +
            esc(String.valueOf(s.createdAt)) +
            "</span> " +
            "<span class='component'>" +
            "SESSION_CREATED" +
            "</span><br>");

        out.println(
            "│<br>" +
            "├── Session ID: " +
            esc(sessionId) +
            "<br>");

        out.println(
            "├── User: " +
            esc(safe(s.username)) +
            "<br>");

        renderUnifiedTrace(
            sessionId,
            out);

        out.println("│<br>");

        if (s.endedAt != null) {

            out.println(
                "└── <span class='time'>" +
                esc(String.valueOf(
                    s.endedAt)) +
                "</span> " +
                "<span class='component'>" +
                "SESSION_ENDED" +
                "</span><br>");

            out.println(
                "&nbsp;&nbsp;&nbsp;&nbsp;" +
                "└── Final status: " +
                esc(safe(s.status)) +
                "<br>");

        } else {

            out.println(
                "└── <span class='component'>" +
                "SESSION_CURRENTLY_ACTIVE" +
                "</span><br>");
        }

        out.println("</div>");
        out.println("</div>");


        // ----------------------------------------------------
        // BUSINESS TRANSACTIONS
        // ----------------------------------------------------

        renderTransactions(
            sessionId,
            out);


        // ----------------------------------------------------
        // ENDPOINT DISTRIBUTION
        // ----------------------------------------------------

        renderEndpointStats(
            sessionId,
            out);


        // ----------------------------------------------------
        // THREAD / DB PROCESS HISTORY
        // ----------------------------------------------------

        renderResourceHistory(
            sessionId,
            out);


        // ----------------------------------------------------
        // ANOMALIES
        // ----------------------------------------------------

        renderAnomalies(
            sessionId,
            out);


        out.println("""
        </body>
        </html>
        """);
    }


    // ========================================================
    // UNIFIED TIMELINE
    // ========================================================

    private void renderUnifiedTrace(
            String sessionId,
            PrintWriter out) {

        String sql =
            """
            SELECT
              event_time,
              source_type,
              event_name,
              detail,
              request_id,
              method,
              uri,
              http_status,
              duration_ms,
              thread_name,
              jvm_thread_id,
              db_backend_pid,
              correlation_id,
              stripe_session_id,
              error_class,
              success
            FROM
            (
              SELECT
                r.request_time
                  AS event_time,

                'HTTP'
                  AS source_type,

                'HTTP_REQUEST'
                  AS event_name,

                NULL::text
                  AS detail,

                r.request_id,
                r.method,
                r.uri,
                r.http_status,
                r.duration_ms,
                r.thread_name,
                r.jvm_thread_id,
                r.db_backend_pid,
                r.correlation_id,
                r.stripe_session_id,
                r.error_class,

                CASE
                  WHEN r.http_status >= 500
                    THEN false
                  ELSE true
                END
                  AS success

              FROM app_request_history r

              WHERE r.session_id = ?

              UNION ALL

              SELECT
                e.event_time,
                'TX_EVENT'
                  AS source_type,
                e.event_type
                  AS event_name,
                e.detail,
                e.request_id,
                NULL::varchar
                  AS method,
                NULL::varchar
                  AS uri,
                NULL::integer
                  AS http_status,
                e.duration_ms,
                e.tomcat_thread_name
                  AS thread_name,
                e.jvm_thread_id,
                e.db_backend_pid,
                e.correlation_id,
                e.stripe_session_id,
                NULL::varchar
                  AS error_class,
                e.success

              FROM app_transaction_events e

              WHERE e.http_session_id = ?

                 OR e.correlation_id IN
                    (
                      SELECT
                        t.correlation_id

                      FROM app_transactions t

                      WHERE
                        t.http_session_id = ?
                    )
            ) x

            ORDER BY
              event_time,
              source_type
            """;

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(sql)) {

            ps.setString(
                1,
                sessionId);

            ps.setString(
                2,
                sessionId);

            ps.setString(
                3,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                int count = 0;

                while (rs.next()) {

                    count++;

                    Timestamp time =
                        rs.getTimestamp(1);

                    String source =
                        rs.getString(2);

                    String eventName =
                        rs.getString(3);

                    String detail =
                        rs.getString(4);

                    String requestId =
                        rs.getString(5);

                    String method =
                        rs.getString(6);

                    String uri =
                        rs.getString(7);

                    Integer status =
                        (Integer)
                            rs.getObject(8);

                    Long duration =
                        (Long)
                            rs.getObject(9);

                    String thread =
                        rs.getString(10);

                    Long jvmTid =
                        (Long)
                            rs.getObject(11);

                    Integer dbPid =
                        (Integer)
                            rs.getObject(12);

                    String correlation =
                        rs.getString(13);

                    String stripeSession =
                        rs.getString(14);

                    String error =
                        rs.getString(15);

                    Boolean success =
                        (Boolean)
                            rs.getObject(16);

                    String branch =
                        (count % 2 == 0)
                            ? "├──"
                            : "├──";

                    out.println(
                        branch +
                        " <span class='time'>" +
                        esc(String.valueOf(time)) +
                        "</span> ");

                    out.println(
                        "<span class='" +
                        (Boolean.FALSE.equals(success)
                            ? "err"
                            : "ok") +
                        "'>" +
                        esc(eventName) +
                        "</span><br>");

                    if ("HTTP".equals(source)) {

                        out.println(
                            "│&nbsp;&nbsp;&nbsp;" +
                            "├── HTTP: " +
                            esc(safe(method)) +
                            " " +
                            esc(safe(uri)) +
                            "<br>");

                        out.println(
                            "│&nbsp;&nbsp;&nbsp;" +
                            "├── Request ID: " +
                            esc(safe(requestId)) +
                            "<br>");

                        out.println(
                            "│&nbsp;&nbsp;&nbsp;" +
                            "├── Status: " +
                            (status == null
                                ? ""
                                : status) +
                            "<br>");

                        out.println(
                            "│&nbsp;&nbsp;&nbsp;" +
                            "├── Duration: " +
                            (duration == null
                                ? ""
                                : duration + " ms") +
                            "<br>");

                    } else {

                        out.println(
                            "│&nbsp;&nbsp;&nbsp;" +
                            "├── Component Event: " +
                            esc(safe(detail)) +
                            "<br>");
                    }

                    out.println(
                        "│&nbsp;&nbsp;&nbsp;" +
                        "├── EXECUTION CONTEXT<br>");

                    out.println(
                        "│&nbsp;&nbsp;&nbsp;" +
                        "│&nbsp;&nbsp;&nbsp;" +
                        "├── Tomcat worker: " +
                        esc(safe(thread)) +
                        "<br>");

                    out.println(
                        "│&nbsp;&nbsp;&nbsp;" +
                        "│&nbsp;&nbsp;&nbsp;" +
                        "├── JVM thread ID: " +
                        (jvmTid == null
                            ? ""
                            : jvmTid) +
                        "<br>");

                    out.println(
                        "│&nbsp;&nbsp;&nbsp;" +
                        "│&nbsp;&nbsp;&nbsp;" +
                        "└── PostgreSQL backend PID: " +
                        (dbPid == null
                            ? ""
                            : dbPid) +
                        "<br>");

                    if (correlation != null) {

                        String txUrl =
                            "transaction-trace?id=" +
                            URLEncoder.encode(
                                correlation,
                                StandardCharsets.UTF_8
                            );

                        out.println(
                            "│&nbsp;&nbsp;&nbsp;" +
                            "├── Business TX: " +
                            "<a href='" +
                            txUrl +
                            "' style='color:#93c5fd'>" +
                            esc(correlation) +
                            "</a><br>");
                    }

                    if (stripeSession != null) {

                        out.println(
                            "│&nbsp;&nbsp;&nbsp;" +
                            "├── Stripe session: " +
                            esc(stripeSession) +
                            "<br>");
                    }

                    if (error != null) {

                        out.println(
                            "│&nbsp;&nbsp;&nbsp;" +
                            "└── <span class='err'>" +
                            "ERROR: " +
                            esc(error) +
                            "</span><br>");
                    }

                    out.println("│<br>");
                }

                if (count == 0) {

                    out.println(
                        "└── No request history " +
                        "recorded for session.<br>");
                }
            }

        } catch (Exception e) {

            out.println(
                "<span class='err'>" +
                "TRACE QUERY FAILED: " +
                esc(e.getMessage()) +
                "</span><br>");
        }
    }


    // ========================================================
    // TRANSACTIONS
    // ========================================================

    private void renderTransactions(
            String sessionId,
            PrintWriter out) {

        out.println(
            "<div class='section'>");

        out.println(
            "<h2>Business Transactions " +
            "Within This Session</h2>");

        out.println("""
        <table>
        <tr>
          <th>Transaction</th>
          <th>Status</th>
          <th>Expected</th>
          <th>Stripe</th>
          <th>Database</th>
          <th>Stripe Session</th>
          <th>Started</th>
          <th>Completed</th>
        </tr>
        """);

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       correlation_id,
                       status,
                       expected_amount,
                       stripe_amount,
                       database_amount,
                       stripe_session_id,
                       started_at,
                       completed_at
                     FROM app_transactions
                     WHERE http_session_id = ?
                     ORDER BY started_at
                     """)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                while (rs.next()) {

                    String tx =
                        rs.getString(1);

                    String url =
                        "transaction-trace?id=" +
                        URLEncoder.encode(
                            tx,
                            StandardCharsets.UTF_8
                        );

                    out.println("<tr>");

                    out.println(
                        "<td><a href='" +
                        url +
                        "'><code>" +
                        esc(tx) +
                        "</code></a></td>");

                    out.println(
                        "<td>" +
                        esc(rs.getString(2)) +
                        "</td>");

                    out.println(
                        "<td>" +
                        money(
                            rs.getBigDecimal(3)) +
                        "</td>");

                    out.println(
                        "<td>" +
                        money(
                            rs.getBigDecimal(4)) +
                        "</td>");

                    out.println(
                        "<td>" +
                        money(
                            rs.getBigDecimal(5)) +
                        "</td>");

                    out.println(
                        "<td><code>" +
                        esc(rs.getString(6)) +
                        "</code></td>");

                    out.println(
                        "<td>" +
                        rs.getTimestamp(7) +
                        "</td>");

                    out.println(
                        "<td>" +
                        rs.getTimestamp(8) +
                        "</td>");

                    out.println("</tr>");
                }
            }

        } catch (Exception e) {

            out.println(
                "<tr><td colspan='8'>" +
                esc(e.getMessage()) +
                "</td></tr>");
        }

        out.println("</table>");
        out.println("</div>");
    }


    // ========================================================
    // ENDPOINT STATS
    // ========================================================

    private void renderEndpointStats(
            String sessionId,
            PrintWriter out) {

        out.println(
            "<div class='section'>");

        out.println(
            "<h2>Endpoint / Request Distribution</h2>");

        out.println("""
        <table>
        <tr>
          <th>Method</th>
          <th>Endpoint</th>
          <th>Requests</th>
          <th>Avg ms</th>
          <th>Max ms</th>
          <th>4xx</th>
          <th>5xx</th>
        </tr>
        """);

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       method,
                       uri,
                       COUNT(*) AS requests,
                       ROUND(
                         AVG(duration_ms)::numeric,
                         2
                       ) AS avg_ms,
                       MAX(duration_ms),
                       COUNT(*) FILTER
                         (
                           WHERE
                             http_status >= 400
                             AND http_status < 500
                         ) AS count_4xx,
                       COUNT(*) FILTER
                         (
                           WHERE
                             http_status >= 500
                         ) AS count_5xx
                     FROM app_request_history
                     WHERE session_id = ?
                     GROUP BY
                       method,
                       uri
                     ORDER BY
                       requests DESC,
                       uri
                     """)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                while (rs.next()) {

                    out.println("<tr>");

                    for (int i = 1;
                         i <= 7;
                         i++) {

                        out.println(
                            "<td>" +
                            esc(
                                String.valueOf(
                                    rs.getObject(i))) +
                            "</td>");
                    }

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
        out.println("</div>");
    }


    // ========================================================
    // RESOURCE HISTORY
    // ========================================================

    private void renderResourceHistory(
            String sessionId,
            PrintWriter out) {

        out.println(
            "<div class='section'>");

        out.println(
            "<h2>Tomcat / JVM / PostgreSQL " +
            "Resource Reuse</h2>");

        out.println("""
        <table>
        <tr>
          <th>Tomcat Worker</th>
          <th>JVM Thread ID</th>
          <th>PostgreSQL Backend PID</th>
          <th>Requests</th>
          <th>Average ms</th>
          <th>Maximum ms</th>
        </tr>
        """);

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       thread_name,
                       jvm_thread_id,
                       db_backend_pid,
                       COUNT(*) AS requests,
                       ROUND(
                         AVG(duration_ms)::numeric,
                         2
                       ) AS avg_ms,
                       MAX(duration_ms)
                     FROM app_request_history
                     WHERE session_id = ?
                     GROUP BY
                       thread_name,
                       jvm_thread_id,
                       db_backend_pid
                     ORDER BY
                       requests DESC,
                       thread_name
                     """)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                while (rs.next()) {

                    out.println("<tr>");

                    String worker = rs.getString(1);
                    Object tidObj = rs.getObject(2);
                    String workerCell = "<code>" + esc(worker) + "</code>";
                    String tidCell = tidObj == null ? "—" : esc(String.valueOf(tidObj));
                    if (tidObj != null) {
                        long resourceTid = ((Number) tidObj).longValue();
                        String threadUrl = "thread-inspect?id=" + resourceTid +
                                "&name=" + java.net.URLEncoder.encode(
                                        worker == null ? "" : worker,
                                        java.nio.charset.StandardCharsets.UTF_8);
                        workerCell = "<a href='" + threadUrl + "'><code>" + esc(worker) + "</code></a>";
                        tidCell = "<a href='" + threadUrl + "'>" + resourceTid + "</a>";
                    }
                    out.println("<td>" + workerCell + "</td>");
                    out.println("<td>" + tidCell + "</td>");

                    out.println(
                        "<td>" +
                        rs.getObject(3) +
                        "</td>");

                    out.println(
                        "<td>" +
                        rs.getLong(4) +
                        "</td>");

                    out.println(
                        "<td>" +
                        rs.getObject(5) +
                        "</td>");

                    out.println(
                        "<td>" +
                        rs.getObject(6) +
                        "</td>");

                    out.println("</tr>");
                }
            }

        } catch (Exception e) {

            out.println(
                "<tr><td colspan='6'>" +
                esc(e.getMessage()) +
                "</td></tr>");
        }

        out.println("</table>");
        out.println("</div>");
    }


    // ========================================================
    // ANOMALIES
    // ========================================================

    private void renderAnomalies(
            String sessionId,
            PrintWriter out) {

        out.println(
            "<div class='section'>");

        out.println(
            "<h2>Troubleshooting / Anomalies</h2>");

        out.println("""
        <table>
        <tr>
          <th>Time</th>
          <th>Request</th>
          <th>HTTP</th>
          <th>ms</th>
          <th>Error</th>
          <th>Thread</th>
          <th>JVM TID</th>
          <th>PG PID</th>
        </tr>
        """);

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       request_time,
                       method || ' ' || uri,
                       http_status,
                       duration_ms,
                       error_class,
                       thread_name,
                       jvm_thread_id,
                       db_backend_pid
                     FROM app_request_history
                     WHERE session_id = ?
                       AND
                       (
                         http_status >= 400
                         OR duration_ms >= 1000
                         OR error_class IS NOT NULL
                       )
                     ORDER BY request_time
                     """)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                boolean any = false;

                while (rs.next()) {

                    any = true;

                    out.println("<tr>");

                    for (int i = 1;
                         i <= 8;
                         i++) {

                        out.println(
                            "<td>" +
                            esc(
                                String.valueOf(
                                    rs.getObject(i))) +
                            "</td>");
                    }

                    out.println("</tr>");
                }

                if (!any) {

                    out.println(
                        "<tr><td colspan='8' " +
                        "class='good'>" +
                        "No HTTP errors, exceptions " +
                        "or ≥1 second requests " +
                        "recorded for this session." +
                        "</td></tr>");
                }
            }

        } catch (Exception e) {

            out.println(
                "<tr><td colspan='8'>" +
                esc(e.getMessage()) +
                "</td></tr>");
        }

        out.println("</table>");
        out.println("</div>");
    }


    // ========================================================
    // TEXT / DOWNLOAD REPORT
    // ========================================================

    private void renderTextReport(
            String sessionId,
            HttpServletResponse resp)
            throws IOException {

        SessionSummary s =
            loadSession(sessionId);

        if (s == null) {

            resp.setStatus(404);
            resp.setContentType(
                "text/plain;charset=UTF-8");

            resp.getWriter().println(
                "Session not found.");

            return;
        }

        RequestStats stats =
            loadRequestStats(sessionId);

        TransactionStats txStats =
            loadTransactionStats(sessionId);

        resp.setContentType(
            "text/plain;charset=UTF-8");

        resp.setHeader(
            "Content-Disposition",
            "attachment; filename=\"" +
            "orders-application-ash-" +
            sessionId +
            ".txt\""
        );

        PrintWriter out =
            resp.getWriter();

        out.println(
            "============================================================");
        out.println(
            "ORDERS APPLICATION SESSION HISTORY (ASH) REPORT");
        out.println(
            "============================================================");

        out.println();
        out.println(
            "NOTE:");
        out.println(
            "This is application-level ASH-style telemetry.");
        out.println(
            "It is not Oracle Database ASH.");
        out.println();

        out.println(
            "SESSION ID          : " +
            sessionId);

        out.println(
            "USER                : " +
            safe(s.username));

        out.println(
            "STATUS              : " +
            safe(s.status));

        out.println(
            "CREATED             : " +
            s.createdAt);

        out.println(
            "LAST SEEN           : " +
            s.lastSeenAt);

        out.println(
            "ENDED               : " +
            s.endedAt);

        out.println(
            "SESSION DURATION    : " +
            formatDuration(
                s.createdAt,
                s.endedAt));

        out.println(
            "HEALTH              : " +
            calculateHealth(
                stats,
                txStats));

        out.println();

        out.println(
            "============================================================");
        out.println(
            "SESSION PERFORMANCE SUMMARY");
        out.println(
            "============================================================");

        out.printf(
            "%-28s %d%n",
            "Total HTTP requests:",
            stats.totalRequests);

        out.printf(
            "%-28s %d%n",
            "HTTP 2xx:",
            stats.http2xx);

        out.printf(
            "%-28s %d%n",
            "HTTP 3xx:",
            stats.http3xx);

        out.printf(
            "%-28s %d%n",
            "HTTP 4xx:",
            stats.http4xx);

        out.printf(
            "%-28s %d%n",
            "HTTP 5xx:",
            stats.http5xx);

        out.printf(
            "%-28s %d ms%n",
            "Average latency:",
            stats.avgMs);

        out.printf(
            "%-28s %d ms%n",
            "P50 latency:",
            stats.p50Ms);

        out.printf(
            "%-28s %d ms%n",
            "P95 latency:",
            stats.p95Ms);

        out.printf(
            "%-28s %d ms%n",
            "P99 latency:",
            stats.p99Ms);

        out.printf(
            "%-28s %d ms%n",
            "Maximum latency:",
            stats.maxMs);

        out.printf(
            "%-28s %d%n",
            "Slow requests >=1s:",
            stats.slowRequests);

        out.printf(
            "%-28s %d%n",
            "Failed requests:",
            stats.failedRequests);

        out.println();

        out.println(
            "============================================================");
        out.println(
            "EXECUTION RESOURCE SUMMARY");
        out.println(
            "============================================================");

        out.printf(
            "%-28s %d%n",
            "Unique Tomcat workers:",
            stats.uniqueTomcatThreads);

        out.printf(
            "%-28s %d%n",
            "Unique JVM thread IDs:",
            stats.uniqueJvmThreads);

        out.printf(
            "%-28s %d%n",
            "Unique PostgreSQL PIDs:",
            stats.uniqueDbPids);

        out.printf(
            "%-28s %d%n",
            "Unique client addresses:",
            stats.uniqueClients);

        out.printf(
            "%-28s %d%n",
            "Business transactions:",
            txStats.total);

        out.printf(
            "%-28s %d%n",
            "Passed transactions:",
            txStats.passed);

        out.printf(
            "%-28s %d%n",
            "Failed transactions:",
            txStats.failed);

        out.println();

        out.println(
            "============================================================");
        out.println(
            "COMPLETE SESSION EXECUTION TRACE");
        out.println(
            "============================================================");

        out.println();
        out.println(
            "[" + s.createdAt + "] SESSION_CREATED");

        out.println(
            "|-- Session ID: " +
            sessionId);

        out.println(
            "|-- User: " +
            safe(s.username));

        printTextTimeline(
            sessionId,
            out);

        if (s.endedAt != null) {

            out.println();
            out.println(
                "[" + s.endedAt + "] SESSION_ENDED");

            out.println(
                "`-- Final status: " +
                safe(s.status));
        }

        out.println();

        out.println(
            "============================================================");
        out.println(
            "BUSINESS TRANSACTIONS");
        out.println(
            "============================================================");

        printTextTransactions(
            sessionId,
            out);

        out.println();

        out.println(
            "============================================================");
        out.println(
            "ANOMALIES / TROUBLESHOOTING FINDINGS");
        out.println(
            "============================================================");

        printTextAnomalies(
            sessionId,
            out);

        out.println();

        out.println(
            "============================================================");
        out.println(
            "END OF APPLICATION SESSION HISTORY REPORT");
        out.println(
            "============================================================");
    }


    private void printTextTimeline(
            String sessionId,
            PrintWriter out) {

        String sql =
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
              remote_addr,
              error_class,
              correlation_id,
              stripe_session_id
            FROM app_request_history
            WHERE session_id = ?
            ORDER BY request_time
            """;

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(sql)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                while (rs.next()) {

                    out.println();
                    out.println(
                        "[" +
                        rs.getTimestamp(1) +
                        "] HTTP_REQUEST");

                    out.println(
                        "|");

                    out.println(
                        "|-- Request ID: " +
                        safe(rs.getString(2)));

                    out.println(
                        "|-- HTTP: " +
                        safe(rs.getString(3)) +
                        " " +
                        safe(rs.getString(4)));

                    out.println(
                        "|-- Status: " +
                        rs.getObject(5));

                    out.println(
                        "|-- Duration: " +
                        rs.getObject(6) +
                        " ms");

                    out.println(
                        "|-- Client: " +
                        safe(rs.getString(10)));

                    out.println(
                        "|");

                    out.println(
                        "|-- EXECUTION CONTEXT");

                    out.println(
                        "|   |-- Tomcat worker: " +
                        safe(rs.getString(7)));

                    out.println(
                        "|   |-- JVM thread ID: " +
                        rs.getObject(8));

                    out.println(
                        "|   `-- PostgreSQL backend PID: " +
                        rs.getObject(9));

                    if (rs.getString(12) != null) {

                        out.println(
                            "|-- Business TX: " +
                            rs.getString(12));
                    }

                    if (rs.getString(13) != null) {

                        out.println(
                            "|-- Stripe session: " +
                            rs.getString(13));
                    }

                    if (rs.getString(11) != null) {

                        out.println(
                            "`-- ERROR: " +
                            rs.getString(11));
                    }
                }
            }

        } catch (Exception e) {

            out.println(
                "TRACE QUERY FAILED: " +
                e.getMessage());
        }
    }


    private void printTextTransactions(
            String sessionId,
            PrintWriter out) {

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       correlation_id,
                       status,
                       expected_amount,
                       stripe_amount,
                       database_amount,
                       stripe_session_id,
                       stripe_payment_intent,
                       started_at,
                       checkout_created_at,
                       webhook_received_at,
                       db_committed_at,
                       completed_at,
                       last_error
                     FROM app_transactions
                     WHERE http_session_id = ?
                     ORDER BY started_at
                     """)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                boolean any = false;

                while (rs.next()) {

                    any = true;

                    out.println();

                    out.println(
                        "Transaction: " +
                        rs.getString(1));

                    out.println(
                        "|-- Status: " +
                        rs.getString(2));

                    out.println(
                        "|-- Expected amount: " +
                        money(
                            rs.getBigDecimal(3)));

                    out.println(
                        "|-- Stripe amount: " +
                        money(
                            rs.getBigDecimal(4)));

                    out.println(
                        "|-- Database amount: " +
                        money(
                            rs.getBigDecimal(5)));

                    out.println(
                        "|-- Stripe session: " +
                        safe(rs.getString(6)));

                    out.println(
                        "|-- Payment intent: " +
                        safe(rs.getString(7)));

                    out.println(
                        "|-- Started: " +
                        rs.getTimestamp(8));

                    out.println(
                        "|-- Stripe checkout created: " +
                        rs.getTimestamp(9));

                    out.println(
                        "|-- Webhook received: " +
                        rs.getTimestamp(10));

                    out.println(
                        "|-- DB committed: " +
                        rs.getTimestamp(11));

                    out.println(
                        "|-- Completed: " +
                        rs.getTimestamp(12));

                    if (rs.getString(13) != null) {

                        out.println(
                            "`-- Error: " +
                            rs.getString(13));
                    }
                }

                if (!any) {

                    out.println(
                        "No business transactions " +
                        "recorded for this session.");
                }
            }

        } catch (Exception e) {

            out.println(
                "TRANSACTION QUERY FAILED: " +
                e.getMessage());
        }
    }


    private void printTextAnomalies(
            String sessionId,
            PrintWriter out) {

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       request_time,
                       method,
                       uri,
                       http_status,
                       duration_ms,
                       error_class,
                       thread_name,
                       jvm_thread_id,
                       db_backend_pid
                     FROM app_request_history
                     WHERE session_id = ?
                       AND
                       (
                         http_status >= 400
                         OR duration_ms >= 1000
                         OR error_class IS NOT NULL
                       )
                     ORDER BY request_time
                     """)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                boolean any = false;

                while (rs.next()) {

                    any = true;

                    out.println();

                    out.println(
                        "[" +
                        rs.getTimestamp(1) +
                        "]");

                    out.println(
                        "Request: " +
                        rs.getString(2) +
                        " " +
                        rs.getString(3));

                    out.println(
                        "HTTP: " +
                        rs.getObject(4));

                    out.println(
                        "Duration: " +
                        rs.getObject(5) +
                        " ms");

                    out.println(
                        "Error: " +
                        rs.getString(6));

                    out.println(
                        "Tomcat: " +
                        rs.getString(7));

                    out.println(
                        "JVM TID: " +
                        rs.getObject(8));

                    out.println(
                        "PostgreSQL PID: " +
                        rs.getObject(9));
                }

                if (!any) {

                    out.println(
                        "No HTTP 4xx/5xx, recorded " +
                        "exceptions, or requests >=1s.");
                }
            }

        } catch (Exception e) {

            out.println(
                "ANOMALY QUERY FAILED: " +
                e.getMessage());
        }
    }


    // ========================================================
    // DATA LOADERS
    // ========================================================

    private SessionSummary loadSession(
            String sessionId) {

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       username,
                       created_at,
                       last_seen_at,
                       ended_at,
                       status,
                       request_count,
                       last_method,
                       last_uri,
                       last_http_status,
                       last_duration_ms,
                       last_thread,
                       last_db_backend_pid,
                       correlation_id,
                       stripe_session_id
                     FROM app_sessions
                     WHERE session_id = ?
                     """)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                SessionSummary s =
                    new SessionSummary();

                s.username =
                    rs.getString(1);

                s.createdAt =
                    rs.getTimestamp(2);

                s.lastSeenAt =
                    rs.getTimestamp(3);

                s.endedAt =
                    rs.getTimestamp(4);

                s.status =
                    rs.getString(5);

                s.requestCount =
                    rs.getLong(6);

                s.lastMethod =
                    rs.getString(7);

                s.lastUri =
                    rs.getString(8);

                s.lastHttpStatus =
                    (Integer)
                        rs.getObject(9);

                s.lastDurationMs =
                    (Long)
                        rs.getObject(10);

                s.lastThread =
                    rs.getString(11);

                s.lastDbPid =
                    (Integer)
                        rs.getObject(12);

                s.correlationId =
                    rs.getString(13);

                s.stripeSessionId =
                    rs.getString(14);

                return s;
            }

        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }


    private RequestStats loadRequestStats(
            String sessionId) {

        RequestStats s =
            new RequestStats();

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       COUNT(*) AS total,

                       COUNT(*) FILTER
                         (
                           WHERE
                             http_status >= 200
                             AND http_status < 300
                         ) AS http_2xx,

                       COUNT(*) FILTER
                         (
                           WHERE
                             http_status >= 300
                             AND http_status < 400
                         ) AS http_3xx,

                       COUNT(*) FILTER
                         (
                           WHERE
                             http_status >= 400
                             AND http_status < 500
                         ) AS http_4xx,

                       COUNT(*) FILTER
                         (
                           WHERE
                             http_status >= 500
                         ) AS http_5xx,

                       COALESCE(
                         ROUND(
                           AVG(duration_ms)::numeric
                         ),
                         0
                       )::bigint AS avg_ms,

                       COALESCE(
                         percentile_cont(0.50)
                         WITHIN GROUP
                           (
                             ORDER BY duration_ms
                           ),
                         0
                       )::bigint AS p50_ms,

                       COALESCE(
                         percentile_cont(0.95)
                         WITHIN GROUP
                           (
                             ORDER BY duration_ms
                           ),
                         0
                       )::bigint AS p95_ms,

                       COALESCE(
                         percentile_cont(0.99)
                         WITHIN GROUP
                           (
                             ORDER BY duration_ms
                           ),
                         0
                       )::bigint AS p99_ms,

                       COALESCE(
                         MAX(duration_ms),
                         0
                       ) AS max_ms,

                       COUNT(*) FILTER
                         (
                           WHERE duration_ms >= 1000
                         ) AS slow_requests,

                       COUNT(*) FILTER
                         (
                           WHERE
                             http_status >= 400
                             OR error_class IS NOT NULL
                         ) AS failed_requests,

                       COUNT(
                         DISTINCT uri
                       ) AS unique_endpoints,

                       COUNT(
                         DISTINCT thread_name
                       ) AS unique_tomcat,

                       COUNT(
                         DISTINCT jvm_thread_id
                       ) AS unique_jvm,

                       COUNT(
                         DISTINCT db_backend_pid
                       ) AS unique_db,

                       COUNT(
                         DISTINCT remote_addr
                       ) AS unique_clients

                     FROM app_request_history

                     WHERE session_id = ?
                     """)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                rs.next();

                s.totalRequests =
                    rs.getLong(1);

                s.http2xx =
                    rs.getLong(2);

                s.http3xx =
                    rs.getLong(3);

                s.http4xx =
                    rs.getLong(4);

                s.http5xx =
                    rs.getLong(5);

                s.avgMs =
                    rs.getLong(6);

                s.p50Ms =
                    rs.getLong(7);

                s.p95Ms =
                    rs.getLong(8);

                s.p99Ms =
                    rs.getLong(9);

                s.maxMs =
                    rs.getLong(10);

                s.slowRequests =
                    rs.getLong(11);

                s.failedRequests =
                    rs.getLong(12);

                s.uniqueEndpoints =
                    rs.getLong(13);

                s.uniqueTomcatThreads =
                    rs.getLong(14);

                s.uniqueJvmThreads =
                    rs.getLong(15);

                s.uniqueDbPids =
                    rs.getLong(16);

                s.uniqueClients =
                    rs.getLong(17);
            }

        } catch (Exception e) {

            throw new RuntimeException(e);
        }

        return s;
    }


    private TransactionStats loadTransactionStats(
            String sessionId) {

        TransactionStats s =
            new TransactionStats();

        try (Connection conn =
                 Db.getConnection();
             PreparedStatement ps =
                 conn.prepareStatement(
                     """
                     SELECT
                       COUNT(*) AS total,

                       COUNT(*) FILTER
                         (
                           WHERE status = 'PASSED'
                         ) AS passed,

                       COUNT(*) FILTER
                         (
                           WHERE status = 'FAILED'
                         ) AS failed,

                       COUNT(*) FILTER
                         (
                           WHERE
                             stripe_session_id
                               IS NOT NULL
                         ) AS stripe_transactions

                     FROM app_transactions

                     WHERE http_session_id = ?
                     """)) {

            ps.setString(
                1,
                sessionId);

            try (ResultSet rs =
                     ps.executeQuery()) {

                rs.next();

                s.total =
                    rs.getLong(1);

                s.passed =
                    rs.getLong(2);

                s.failed =
                    rs.getLong(3);

                s.stripeTransactions =
                    rs.getLong(4);
            }

        } catch (Exception e) {

            throw new RuntimeException(e);
        }

        return s;
    }


    private String calculateHealth(
            RequestStats r,
            TransactionStats t) {

        if (r.http5xx > 0 ||
            r.failedRequests > 0 ||
            t.failed > 0) {

            return "ATTENTION";
        }

        return "HEALTHY";
    }


    private String money(
            java.math.BigDecimal value) {

        if (value == null) {
            return "";
        }

        return "$" +
            value.toPlainString();
    }


    private void metric(
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
            "</div>" +
            "</div>"
        );
    }


    // ========================================================
    // SIMPLE DATA OBJECTS
    // ========================================================

    private static class SessionSummary {

        String username;

        Timestamp createdAt;
        Timestamp lastSeenAt;
        Timestamp endedAt;

        String status;

        long requestCount;

        String lastMethod;
        String lastUri;

        Integer lastHttpStatus;
        Long lastDurationMs;

        String lastThread;

        Integer lastDbPid;

        String correlationId;
        String stripeSessionId;
    }


    private static class RequestStats {

        long totalRequests;

        long http2xx;
        long http3xx;
        long http4xx;
        long http5xx;

        long avgMs;
        long p50Ms;
        long p95Ms;
        long p99Ms;
        long maxMs;

        long slowRequests;
        long failedRequests;

        long uniqueEndpoints;

        long uniqueTomcatThreads;
        long uniqueJvmThreads;
        long uniqueDbPids;
        long uniqueClients;
    }


    private static class TransactionStats {

        long total;
        long passed;
        long failed;
        long stripeTransactions;
    }
}
