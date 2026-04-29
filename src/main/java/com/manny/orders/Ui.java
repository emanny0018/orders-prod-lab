package com.manny.orders;

import java.io.PrintWriter;

public class Ui {

    public static void header(PrintWriter out, String title) {
        out.println("<!DOCTYPE html>");
        out.println("<html><head>");
        out.println("<title>" + title + " - ERICMANNY INDUSTRIES</title>");
        out.println("""
<style>
* { box-sizing:border-box; }

body {
  margin:0;
  font-family: Arial, sans-serif;
  background:
    linear-gradient(rgba(2,6,23,.88), rgba(15,23,42,.88)),
    url('images/office-bg.png') center/cover fixed no-repeat;
  color:#111827;
}

.app-shell { display:flex; min-height:100vh; }

.sidebar {
  width:280px;
  min-height:100vh;
  position:fixed;
  left:0; top:0; bottom:0;
  padding:28px 20px;
  background:rgba(2,6,23,.96);
  color:white;
  box-shadow:12px 0 35px rgba(0,0,0,.35);
}

.brand-title {
  font-size:23px;
  font-weight:900;
  letter-spacing:.8px;
}

.brand-subtitle {
  font-size:12px;
  color:#cbd5e1;
  margin-top:8px;
  line-height:1.5;
}

.marquee {
  margin:22px 0;
  padding:12px;
  border-radius:12px;
  background:linear-gradient(90deg,#2563eb,#7c3aed);
  color:white;
  overflow:hidden;
  white-space:nowrap;
  font-size:12px;
  font-weight:800;
}

.marquee span {
  display:inline-block;
  animation:scroll 14s linear infinite;
}

@keyframes scroll {
  0% { transform:translateX(100%); }
  100% { transform:translateX(-100%); }
}

.nav-section {
  margin-top:22px;
  font-size:11px;
  color:#94a3b8;
  text-transform:uppercase;
  letter-spacing:1px;
}

.sidebar a {
  display:block;
  color:#e5e7eb;
  text-decoration:none;
  padding:13px 12px;
  border-radius:12px;
  margin:7px 0;
  font-size:15px;
}

.sidebar a:hover {
  background:rgba(59,130,246,.25);
  color:white;
}

.main {
  margin-left:280px;
  width:calc(100% - 280px);
  padding:32px;
}

.hero {
  background:
    linear-gradient(135deg,rgba(37,99,235,.94),rgba(79,70,229,.86)),
    url('images/office-bg.png') center/cover no-repeat;
  border-radius:24px;
  padding:32px;
  color:white;
  box-shadow:0 18px 45px rgba(0,0,0,.30);
  margin-bottom:26px;
}

.hero h1 {
  margin:0;
  font-size:34px;
}

.hero .subtitle {
  color:#dbeafe;
  margin-top:8px;
  font-size:15px;
}

.user-chip {
  margin-top:18px;
  display:inline-block;
  background:rgba(255,255,255,.18);
  padding:10px 15px;
  border-radius:999px;
}

.card-row {
  display:grid;
  grid-template-columns:repeat(4,1fr);
  gap:20px;
  margin-bottom:26px;
}

.card {
  background:rgba(255,255,255,.97);
  padding:24px;
  border-radius:20px;
  box-shadow:0 14px 35px rgba(0,0,0,.18);
}

.card h3 {
  margin:0;
  color:#64748b;
  font-size:13px;
  text-transform:uppercase;
  letter-spacing:.7px;
}

.card .value {
  font-size:32px;
  font-weight:900;
  margin-top:10px;
  color:#0f172a;
}

.table-card {
  background:rgba(255,255,255,.97);
  padding:24px;
  border-radius:20px;
  box-shadow:0 14px 35px rgba(0,0,0,.18);
  margin-bottom:26px;
}

.table-card h2 { margin-top:0; color:#0f172a; }

table { border-collapse:collapse; width:100%; border-radius:14px; overflow:hidden; }

th {
  background:#0f172a;
  color:white;
  text-align:left;
  padding:13px;
  font-size:13px;
}

td {
  padding:13px;
  border-bottom:1px solid #e5e7eb;
  font-size:14px;
}

tr:hover { background:#f8fafc; }

.badge {
  padding:6px 11px;
  border-radius:999px;
  font-size:12px;
  font-weight:900;
  display:inline-block;
}

.green { background:#dcfce7; color:#166534; }
.yellow { background:#fef9c3; color:#854d0e; }
.red { background:#fee2e2; color:#991b1b; }
.blue { background:#dbeafe; color:#1e40af; }
.gray { background:#e5e7eb; color:#374151; }

a { color:#2563eb; font-weight:800; text-decoration:none; }
</style>
</head>
<body>
""");
    }

    public static void sidebar(PrintWriter out) {
        out.println("""
<div class="sidebar">
  <div class="brand-title">ERICMANNY INDUSTRIES</div>
  <div class="brand-subtitle">Retail POS • E-commerce • Inventory • Payments • Operations</div>

  <div class="marquee"><span>LIVE OPERATIONS • ORDERS • INVENTORY • PAYMENTS • REPORTS • CACHE • QUEUE • JVM DIAGNOSTICS</span></div>

  <div class="nav-section">Business Services</div>
  <a href="orders">Orders Command Center</a>
  <a href="cart">Shopping Cart</a>
  <a href="inventory">Inventory Control</a>
  <a href="payments">Payments & Settlements</a>
  <a href="reports">Reports & Analytics</a>

  <div class="nav-section">Platform</div>
  <a href="health">System Health</a>
  <a href="logout">Logout</a>
</div>
""");
    }

    public static void pageStart(PrintWriter out, String title, String subtitle, String user) {
        header(out, title);
        out.println("<div class='app-shell'>");
        sidebar(out);
        out.println("<main class='main'>");
        out.println("<section class='hero'>");
        out.println("<h1>" + title + "</h1>");
        out.println("<div class='subtitle'>" + subtitle + "</div>");
        out.println("<div class='user-chip'>Signed in as <b>" + user + "</b></div>");
        out.println("</section>");
    }

    public static void footer(PrintWriter out) {
        out.println("</main></div></body></html>");
    }

    public static String badge(String text, String color) {
        return "<span class='badge " + color + "'>" + text + "</span>";
    }
}
