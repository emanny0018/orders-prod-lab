package com.manny.orders;

import jakarta.servlet.http.*;
import java.io.*;

public class RecoveryControlServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("text/html;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession();

        String action = req.getParameter("action");

        String message = "No recovery action executed.";

        if ("clear-current-session".equals(action)) {

            try {
                session.invalidate();
                message = "Current session invalidated successfully.";
            } catch (Exception e) {
                message = "Current session already invalid.";
            }
        }

        if ("clear-other-sessions".equals(action)) {

            int cleared =
                    SessionRegistry.invalidateAllExcept(session.getId());

            message =
                    "Cleared " + cleared + " tracked sessions.";
        }

        if ("clear-cache".equals(action)) {

            CachePressureServlet.clearAllCaches();

            message =
                    "All cache pressure cleared.";
        }

        if ("clear-queue".equals(action)) {

            QueuePressureServlet.clearQueue();

            message =
                    "Queue backlog cleared.";
        }

        Runtime rt = Runtime.getRuntime();

        long used =
                rt.totalMemory() - rt.freeMemory();

        long max =
                rt.maxMemory();

        long free =
                rt.freeMemory();

        Ui.pageStart(
                out,
                "Recovery Control Center",
                "Recover from stuck sessions, queue backlog, and cache pressure.",
                "admin"
        );

        out.println("""
<style>
.lab-grid {
  display:grid;
  grid-template-columns:repeat(3,minmax(220px,1fr));
  gap:18px;
  margin:24px 0;
}

.lab-card {
  background:#ffffff;
  color:#111827;
  border-radius:18px;
  padding:26px;
  box-shadow:0 12px 30px rgba(0,0,0,.18);
}

.lab-card h3 {
  margin:0 0 10px 0;
  color:#64748b;
  font-size:15px;
  text-transform:uppercase;
}

.lab-value {
  font-size:32px;
  font-weight:800;
  color:#2563eb;
}

.lab-section {
  background:#ffffff;
  color:#111827;
  border-radius:18px;
  padding:28px;
  margin:22px 0;
  box-shadow:0 12px 30px rgba(0,0,0,.18);
}

.lab-btn {
  padding:13px 18px;
  border:0;
  border-radius:10px;
  font-weight:800;
  cursor:pointer;
  margin:6px;
}

.warn {
  background:#f59e0b;
  color:#111827;
}

.danger {
  background:#ef4444;
  color:white;
}

.safe {
  background:#22c55e;
  color:#111827;
}

.note {
  line-height:1.7;
  font-size:16px;
}
</style>
""");

        out.println("<div class='lab-section'>");
        out.println("<h2>Last Recovery Action</h2>");
        out.println("<p><b>" + message + "</b></p>");
        out.println("</div>");

        out.println("<div class='lab-grid'>");

        out.println("<div class='lab-card'>");
        out.println("<h3>Heap Used</h3>");
        out.println("<div class='lab-value'>" +
                (used / 1024 / 1024) + " MB</div>");
        out.println("</div>");

        out.println("<div class='lab-card'>");
        out.println("<h3>Heap Max</h3>");
        out.println("<div class='lab-value'>" +
                (max / 1024 / 1024) + " MB</div>");
        out.println("</div>");

        out.println("<div class='lab-card'>");
        out.println("<h3>Heap Free</h3>");
        out.println("<div class='lab-value'>" +
                (free / 1024 / 1024) + " MB</div>");
        out.println("</div>");

        out.println("</div>");

        out.println("<div class='lab-grid'>");

        out.println("<div class='lab-card'>");
        out.println("<h3>Tracked Sessions</h3>");
        out.println("<div class='lab-value'>" +
                SessionRegistry.count() + "</div>");
        out.println("</div>");

        out.println("<div class='lab-card'>");
        out.println("<h3>Queue Depth</h3>");
        out.println("<div class='lab-value'>" +
                QueuePressureServlet.queueDepth() + "</div>");
        out.println("</div>");

        out.println("<div class='lab-card'>");
        out.println("<h3>Cache Entries</h3>");
        out.println("<div class='lab-value'>" +
                CachePressureServlet.totalEntries() + "</div>");
        out.println("</div>");

        out.println("</div>");

        out.println("<div class='lab-section'>");

        out.println("<h2>Session Recovery</h2>");

        out.println("<form method='get'>");

        out.println("<button class='lab-btn warn' " +
                "name='action' value='clear-current-session'>");
        out.println("Clear Current Session");
        out.println("</button>");

        out.println("<button class='lab-btn danger' " +
                "name='action' value='clear-other-sessions'>");
        out.println("Clear Other Tracked Sessions");
        out.println("</button>");

        out.println("</form>");

        out.println("</div>");

        out.println("<div class='lab-section'>");

        out.println("<h2>Infrastructure Recovery</h2>");

        out.println("<form method='get'>");

        out.println("<button class='lab-btn safe' " +
                "name='action' value='clear-cache'>");
        out.println("Clear Cache Pressure");
        out.println("</button>");

        out.println("<button class='lab-btn safe' " +
                "name='action' value='clear-queue'>");
        out.println("Clear Queue Backlog");
        out.println("</button>");

        out.println("</form>");

        out.println("</div>");

        out.println("<div class='lab-section note'>");

        out.println("<h2>What This Page Is Doing</h2>");

        out.println("<p><b>Clear Current Session</b> removes your current session/cart state.</p>");

        out.println("<p><b>Clear Other Tracked Sessions</b> invalidates other active sessions.</p>");

        out.println("<p><b>Clear Cache Pressure</b> removes retained cache objects from heap.</p>");

        out.println("<p><b>Clear Queue Backlog</b> removes queued work items from memory.</p>");

        out.println("<p><b>Important:</b> clearing removes references but does not force immediate Full GC.</p>");

        out.println("<p>After recovery actions, observe Grafana and JVM metrics to watch memory stabilize.</p>");

        out.println("</div>");
    }
}
