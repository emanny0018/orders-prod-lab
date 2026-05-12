package com.manny.orders;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueuePressureServlet extends HttpServlet {

    public static void clearQueue() {
        WORK_QUEUE.clear();
    }

    public static int queueDepth() {
        return WORK_QUEUE.size();
    }


    private static final BlockingQueue<byte[]> WORK_QUEUE = new LinkedBlockingQueue<>();
    private static final AtomicBoolean CONSUMER_RUNNING = new AtomicBoolean(false);
    private static Thread consumerThread;

    private static String mb(long bytes) {
        return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
    }

    private static long queueBytes() {
        long total = 0;
        for (byte[] item : WORK_QUEUE) {
            total += item.length;
        }
        return total;
    }

    private static void startConsumer(int delayMs) {
        if (CONSUMER_RUNNING.get()) return;

        CONSUMER_RUNNING.set(true);

        consumerThread = new Thread(() -> {
            while (CONSUMER_RUNNING.get()) {
                try {
                    byte[] item = WORK_QUEUE.poll(1, TimeUnit.SECONDS);
                    if (item != null) {
                        Thread.sleep(delayMs);
                    }
                } catch (Exception ignored) {}
            }
        });

        consumerThread.setName("queue-pressure-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private static void stopConsumer() {
        CONSUMER_RUNNING.set(false);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String action = req.getParameter("action");

        int messages = 1000;
        int kb = 5;
        int delayMs = 100;

        try { messages = Integer.parseInt(req.getParameter("messages")); } catch (Exception ignored) {}
        try { kb = Integer.parseInt(req.getParameter("kb")); } catch (Exception ignored) {}
        try { delayMs = Integer.parseInt(req.getParameter("delayMs")); } catch (Exception ignored) {}

        if ("produce".equals(action)) {
            for (int i = 0; i < messages; i++) {
                WORK_QUEUE.offer(new byte[kb * 1024]);
            }
        }

        if ("start-consumer".equals(action)) {
            startConsumer(delayMs);
        }

        if ("stop-consumer".equals(action)) {
            stopConsumer();
        }

        if ("clear".equals(action)) {
            WORK_QUEUE.clear();
        }

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        long free = rt.freeMemory();

        Ui.pageStart(out,
                "Queue Pressure Control",
                "Simulate backlog when producers create work faster than consumers process it.",
                "admin");

        out.println("""
<style>
.lab-grid { display:grid; grid-template-columns:repeat(3,minmax(220px,1fr)); gap:18px; margin:24px 0; }
.lab-card { background:#fff; color:#111827; border-radius:18px; padding:26px; box-shadow:0 12px 30px rgba(0,0,0,.18); }
.lab-card h3 { margin:0 0 10px 0; color:#64748b; font-size:15px; text-transform:uppercase; }
.lab-value { font-size:32px; font-weight:800; color:#2563eb; }
.lab-section { background:#fff; color:#111827; border-radius:18px; padding:28px; margin:22px 0; box-shadow:0 12px 30px rgba(0,0,0,.18); }
.lab-input { padding:12px; border:1px solid #cbd5e1; border-radius:10px; width:110px; margin-right:8px; }
.lab-btn { padding:13px 18px; border:0; border-radius:10px; font-weight:800; cursor:pointer; margin:6px; }
.warn { background:#f59e0b; color:#111827; }
.danger { background:#ef4444; color:white; }
.safe { background:#22c55e; color:#111827; }
.note { line-height:1.7; font-size:16px; }
</style>
""");

        out.println("<div class='lab-grid'>");
        out.println("<div class='lab-card'><h3>Heap Used</h3><div class='lab-value'>" + mb(used) + "</div></div>");
        out.println("<div class='lab-card'><h3>Heap Max</h3><div class='lab-value'>" + mb(max) + "</div></div>");
        out.println("<div class='lab-card'><h3>Heap Free</h3><div class='lab-value'>" + mb(free) + "</div></div>");
        out.println("</div>");

        out.println("<div class='lab-grid'>");
        out.println("<div class='lab-card'><h3>Queue Depth</h3><div class='lab-value'>" + WORK_QUEUE.size() + "</div></div>");
        out.println("<div class='lab-card'><h3>Queue Memory</h3><div class='lab-value'>" + mb(queueBytes()) + "</div></div>");
        out.println("<div class='lab-card'><h3>Consumer Running</h3><div class='lab-value'>" + CONSUMER_RUNNING.get() + "</div></div>");
        out.println("</div>");

        out.println("<div class='lab-section'>");
        out.println("<h2>Produce Messages</h2>");
        out.println("<form method='get'>");
        out.println("<label><b>Messages:</b></label>");
        out.println("<input class='lab-input' type='number' name='messages' value='" + messages + "' min='1'>");
        out.println("<label><b>KB per message:</b></label>");
        out.println("<input class='lab-input' type='number' name='kb' value='" + kb + "' min='1'>");
        out.println("<button class='lab-btn danger' name='action' value='produce'>Produce Queue Messages</button>");
        out.println("</form>");
        out.println("</div>");

        out.println("<div class='lab-section'>");
        out.println("<h2>Consumer Control</h2>");
        out.println("<form method='get'>");
        out.println("<label><b>Delay per message ms:</b></label>");
        out.println("<input class='lab-input' type='number' name='delayMs' value='" + delayMs + "' min='1'>");
        out.println("<button class='lab-btn safe' name='action' value='start-consumer'>Start Slow Consumer</button>");
        out.println("<button class='lab-btn warn' name='action' value='stop-consumer'>Stop Consumer</button>");
        out.println("<button class='lab-btn safe' name='action' value='clear'>Clear Queue</button>");
        out.println("</form>");
        out.println("</div>");

        out.println("<div class='lab-section note'>");
        out.println("<h2>What This Page Is Doing</h2>");
        out.println("<p><b>Produce Messages</b>: creates fake work items and stores them in an in-memory queue.</p>");
        out.println("<p><b>Start Slow Consumer</b>: starts a background worker that removes messages slowly.</p>");
        out.println("<p><b>Stop Consumer</b>: stops processing, so backlog grows faster.</p>");
        out.println("<p><b>Production lesson</b>: when producers are faster than consumers, queue depth grows. That can increase memory, latency, retries, and failure risk.</p>");
        out.println("<p><b>Failure path</b>: request traffic grows → messages pile up → queue memory grows → GC pressure rises → app slows or OOMs.</p>");
        out.println("</div>");
    }
}
