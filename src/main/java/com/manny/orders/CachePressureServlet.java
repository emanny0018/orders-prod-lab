package com.manny.orders;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CachePressureServlet extends HttpServlet {

    public static void clearAllCaches() {
        PRODUCT_CACHE.clear();
        CUSTOMER_CACHE.clear();
    }

    public static int totalEntries() {
        return PRODUCT_CACHE.size() + CUSTOMER_CACHE.size();
    }


    private static final Map<String, byte[]> PRODUCT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> CUSTOMER_CACHE = new ConcurrentHashMap<>();

    private static String mb(long bytes) {
        return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
    }

    private static long cacheBytes(Map<String, byte[]> cache) {
        long total = 0;
        for (byte[] b : cache.values()) {
            total += b.length;
        }
        return total;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String action = req.getParameter("action");

        int entries = 1000;
        int kb = 10;

        try {
            entries = Integer.parseInt(req.getParameter("entries"));
        } catch (Exception ignored) {}

        try {
            kb = Integer.parseInt(req.getParameter("kb"));
        } catch (Exception ignored) {}

        if ("add-products".equals(action)) {
            for (int i = 0; i < entries; i++) {
                PRODUCT_CACHE.put(
                    "product-" + System.nanoTime() + "-" + i,
                    new byte[kb * 1024]
                );
            }
        }

        if ("add-customers".equals(action)) {
            for (int i = 0; i < entries; i++) {
                CUSTOMER_CACHE.put(
                    "customer-" + System.nanoTime() + "-" + i,
                    new byte[kb * 1024]
                );
            }
        }

        if ("clear-products".equals(action)) {
            PRODUCT_CACHE.clear();
        }

        if ("clear-customers".equals(action)) {
            CUSTOMER_CACHE.clear();
        }

        if ("clear-all".equals(action)) {
            PRODUCT_CACHE.clear();
            CUSTOMER_CACHE.clear();
        }

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        long free = rt.freeMemory();

        long productBytes = cacheBytes(PRODUCT_CACHE);
        long customerBytes = cacheBytes(CUSTOMER_CACHE);

        Ui.pageStart(out,
                "Cache Pressure Control",
                "Simulate unbounded application cache growth and JVM memory pressure.",
                "admin");

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
.lab-input {
  padding:12px;
  border:1px solid #cbd5e1;
  border-radius:10px;
  width:100px;
  margin-right:8px;
}
.lab-btn {
  padding:13px 18px;
  border:0;
  border-radius:10px;
  font-weight:800;
  cursor:pointer;
  margin:6px;
}
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

        out.println("<div class='lab-card'><h3>Product Cache Entries</h3><div class='lab-value'>" + PRODUCT_CACHE.size() + "</div><p>" + mb(productBytes) + "</p></div>");
        out.println("<div class='lab-card'><h3>Customer Cache Entries</h3><div class='lab-value'>" + CUSTOMER_CACHE.size() + "</div><p>" + mb(customerBytes) + "</p></div>");
        out.println("<div class='lab-card'><h3>Total Cache Memory</h3><div class='lab-value'>" + mb(productBytes + customerBytes) + "</div></div>");

        out.println("</div>");

        out.println("<div class='lab-section'>");
        out.println("<h2>Add Cache Pressure</h2>");
        out.println("<form method='get'>");
        out.println("<label><b>Entries:</b></label>");
        out.println("<input class='lab-input' type='number' name='entries' value='" + entries + "' min='1'>");
        out.println("<label><b>KB per entry:</b></label>");
        out.println("<input class='lab-input' type='number' name='kb' value='" + kb + "' min='1'>");
        out.println("<button class='lab-btn warn' name='action' value='add-products'>Add Product Cache</button>");
        out.println("<button class='lab-btn danger' name='action' value='add-customers'>Add Customer Cache</button>");
        out.println("</form>");
        out.println("</div>");

        out.println("<div class='lab-section'>");
        out.println("<h2>Clear Cache References</h2>");
        out.println("<form method='get'>");
        out.println("<button class='lab-btn safe' name='action' value='clear-products'>Clear Product Cache</button>");
        out.println("<button class='lab-btn safe' name='action' value='clear-customers'>Clear Customer Cache</button>");
        out.println("<button class='lab-btn safe' name='action' value='clear-all'>Clear All Cache</button>");
        out.println("</form>");
        out.println("</div>");

        out.println("<div class='lab-section note'>");
        out.println("<h2>What This Page Is Doing</h2>");
        out.println("<p><b>Product Cache</b>: simulates cached product/catalog data that keeps growing.</p>");
        out.println("<p><b>Customer Cache</b>: simulates cached customer/session/profile data that keeps growing.</p>");
        out.println("<p><b>Each cache entry</b> stores a byte array in JVM heap memory.</p>");
        out.println("<p><b>Production lesson</b>: cache is useful only when it has limits, eviction, TTL, and monitoring.</p>");
        out.println("<p><b>Failure path</b>: cache grows → Old Gen rises → GC cannot reclaim referenced objects → heap OOM.</p>");
        out.println("</div>");
    }
}
