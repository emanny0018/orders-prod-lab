package com.manny.orders;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;

public class HeapLeakControlServlet extends HttpServlet {

    private static final List<byte[]> GLOBAL_LEAKS = new ArrayList<>();

    private static String mb(long bytes) {
        return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession();

        List<byte[]> sessionLeaks =
                (List<byte[]>) session.getAttribute("sessionLeaks");

        if (sessionLeaks == null) {
            sessionLeaks = new ArrayList<>();
            session.setAttribute("sessionLeaks", sessionLeaks);
        }

        String action = req.getParameter("action");
        String scope = req.getParameter("scope");

        int addMb = 10;
        try {
            addMb = Integer.parseInt(req.getParameter("mb"));
        } catch (Exception ignored) {}

        if ("add".equals(action)) {
            byte[] block = new byte[addMb * 1024 * 1024];

            if ("global".equals(scope)) {
                GLOBAL_LEAKS.add(block);
            } else {
                sessionLeaks.add(block);
            }
        }

        if ("clear-session".equals(action)) {
            sessionLeaks.clear();
            session.removeAttribute("sessionLeaks");
        }

        if ("clear-global".equals(action)) {
            GLOBAL_LEAKS.clear();
        }

        Runtime rt = Runtime.getRuntime();

        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        long free = rt.freeMemory();

        Ui.pageStart(out,
                "Heap Leak Control",
                "Simulate JVM memory leaks for testing OOM behavior.",
                "admin");

        out.println("""
<style>
.heap-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 18px;
  margin: 24px 0;
}
.heap-card {
  background: #ffffff;
  color: #111827;
  border-radius: 18px;
  padding: 26px;
  box-shadow: 0 12px 30px rgba(0,0,0,.18);
}
.heap-card h3 {
  margin: 0 0 10px 0;
  color: #64748b;
  font-size: 15px;
  text-transform: uppercase;
  letter-spacing: .04em;
}
.heap-value {
  font-size: 34px;
  font-weight: 800;
  color: #0f172a;
}
.heap-blue {
  color: #2563eb;
}
.heap-section {
  background: #ffffff;
  color: #111827;
  border-radius: 18px;
  padding: 28px;
  margin: 22px 0;
  box-shadow: 0 12px 30px rgba(0,0,0,.18);
}
.heap-section h2 {
  margin-top: 0;
}
.heap-btn {
  padding: 13px 18px;
  border: 0;
  border-radius: 10px;
  font-weight: 800;
  cursor: pointer;
  margin: 6px;
}
.heap-warn { background:#f59e0b; color:#111827; }
.heap-danger { background:#ef4444; color:white; }
.heap-safe { background:#22c55e; color:#111827; }
.heap-input {
  padding: 12px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  width: 100px;
  margin-right: 8px;
}
.heap-note {
  line-height: 1.7;
  font-size: 16px;
}
</style>
""");

        out.println("<div class='heap-grid'>");

        out.println("<div class='heap-card'>");
        out.println("<h3>Heap Used</h3>");
        out.println("<div class='heap-value heap-blue'>" + mb(used) + "</div>");
        out.println("</div>");

        out.println("<div class='heap-card'>");
        out.println("<h3>Heap Max</h3>");
        out.println("<div class='heap-value'>" + mb(max) + "</div>");
        out.println("</div>");

        out.println("<div class='heap-card'>");
        out.println("<h3>Heap Free</h3>");
        out.println("<div class='heap-value heap-blue'>" + mb(free) + "</div>");
        out.println("</div>");

        out.println("</div>");

        out.println("<div class='heap-grid'>");

        out.println("<div class='heap-card'>");
        out.println("<h3>Current Session ID</h3>");
        out.println("<div style='font-size:14px; word-break:break-all;'>" + session.getId() + "</div>");
        out.println("</div>");

        out.println("<div class='heap-card'>");
        out.println("<h3>Current Session Blocks</h3>");
        out.println("<div class='heap-value'>" + sessionLeaks.size() + "</div>");
        out.println("</div>");

        out.println("<div class='heap-card'>");
        out.println("<h3>Global JVM Blocks</h3>");
        out.println("<div class='heap-value'>" + GLOBAL_LEAKS.size() + "</div>");
        out.println("</div>");

        out.println("</div>");

        out.println("<div class='heap-section'>");
        out.println("<h2>Add Heap Memory</h2>");
        out.println("<p>This creates real byte arrays inside the JVM heap.</p>");
        out.println("<form method='get'>");
        out.println("<input type='hidden' name='action' value='add'>");
        out.println("<label><b>MB per click:</b></label> ");
        out.println("<input class='heap-input' type='number' name='mb' value='" + addMb + "' min='1' max='100'>");
        out.println("<button class='heap-btn heap-warn' name='scope' value='session'>Add to Current Session</button>");
        out.println("<button class='heap-btn heap-danger' name='scope' value='global'>Add to Global JVM Leak</button>");
        out.println("</form>");
        out.println("</div>");

        out.println("<div class='heap-section'>");
        out.println("<h2>Clear Memory References</h2>");
        out.println("<form method='get'>");
        out.println("<button class='heap-btn heap-safe' name='action' value='clear-session'>Clear Current Session Leak</button>");
        out.println("<button class='heap-btn heap-safe' name='action' value='clear-global'>Clear Global Leak</button>");
        out.println("</form>");
        out.println("</div>");

        out.println("<div class='heap-section heap-note'>");
        out.println("<h2>What This Page Is Doing</h2>");
        out.println("<p><b>Add to Current Session</b>: creates memory and stores it inside your current JSESSIONID session. This simulates a cart/session leak.</p>");
        out.println("<p><b>Add to Global JVM Leak</b>: creates memory and stores it inside a static Java list. This affects the whole Tomcat JVM and causes OOM faster.</p>");
        out.println("<p><b>Clear buttons</b>: remove references to the objects. This does not force GC immediately. It only makes the objects eligible for GC.</p>");
        out.println("<p><b>OOM path</b>: Heap Used rises → Heap Free drops → GC works harder → if objects remain referenced → Java heap space OOM.</p>");
        out.println("</div>");
    }
}
