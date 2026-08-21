#!/usr/bin/env bash
set -Eeuo pipefail

REPO=/root/orders-prod-lab
SRC="$REPO/src/main/java/com/manny/orders"
OBS="$SRC/ObservabilityServlet.java"
ORD="$SRC/OrdersServlet.java"
ASH="$SRC/SessionHistoryServlet.java"
STAMP=$(date +%Y%m%d-%H%M%S)

cd "$REPO"

echo '============================================================'
echo ' ORDERS OBSERVABILITY - COMPLETE UPGRADE'
echo '============================================================'

for f in "$OBS" "$ORD" "$ASH"; do
  [[ -f "$f" ]] || { echo "ERROR: missing $f"; exit 1; }
done

echo '=== 1. BACKUP ==='
cp "$OBS" "$OBS.backup-$STAMP"
cp "$ORD" "$ORD.backup-$STAMP"
cp "$ASH" "$ASH.backup-$STAMP"
echo "Backup stamp: $STAMP"

echo '=== 2. PATCH SOURCE ==='
python3 <<'PY'
from pathlib import Path
import re

S=Path('/root/orders-prod-lab/src/main/java/com/manny/orders')
OBS=S/'ObservabilityServlet.java'
ORD=S/'OrdersServlet.java'
ASH=S/'SessionHistoryServlet.java'

# ---------- Observability ----------
s=OBS.read_text()

# Active sessions must also have recent telemetry.
if "CURRENT_TIMESTAMP - INTERVAL '2 minutes'" not in s:
    patterns=[
        r"WHERE\s+status\s*=\s*'ACTIVE'\s*\)\s*,",
        r"WHERE\s+status\s*=\s*'ACTIVE'\s*\),",
    ]
    done=False
    for pat in patterns:
        ns,n=re.subn(pat,
            "WHERE status='ACTIVE'\n                              AND last_seen_at > CURRENT_TIMESTAMP - INTERVAL '2 minutes'),",
            s,count=1,flags=re.I)
        if n:
            s=ns; done=True; break
    if not done:
        print('WARNING: active-session count query not matched; visual upgrade will continue.')

# Add presentation CSS while preserving existing telemetry/query code.
if 'OBS_PRESENTATION_V5' not in s:
    i=s.find('</style>')
    if i < 0:
        raise SystemExit('ERROR: ObservabilityServlet.java has no </style>. No safe visual patch possible.')
    css='''
/* OBS_PRESENTATION_V5 */
body{background:radial-gradient(circle at 12% 8%,rgba(12,74,110,.55),transparent 28%),radial-gradient(circle at 88% 12%,rgba(91,33,182,.45),transparent 30%),linear-gradient(135deg,#020617,#071426 48%,#020617)!important;background-attachment:fixed!important;color:#e5eefb!important}
body:before{content:"";position:fixed;inset:0;pointer-events:none;z-index:-1;background-image:linear-gradient(rgba(56,189,248,.035) 1px,transparent 1px),linear-gradient(90deg,rgba(56,189,248,.035) 1px,transparent 1px);background-size:42px 42px}
h1,h2,h3{color:#f8fafc!important}th{background:rgba(15,23,42,.96)!important;color:#7dd3fc!important;text-transform:uppercase;letter-spacing:.08em}td{color:#cbd5e1!important}code{color:#93c5fd!important}
.card,.panel,.metric,.metric-card,.table-card{background:linear-gradient(145deg,rgba(15,23,42,.92),rgba(2,6,23,.94))!important;border:1px solid rgba(125,211,252,.14)!important;box-shadow:0 20px 55px rgba(0,0,0,.28)!important;color:#e5eefb!important}
.obsnav{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 16px;margin:0 0 18px;border-radius:16px;background:rgba(2,6,23,.78);border:1px solid rgba(125,211,252,.15)}
.obsbrand{font-weight:950;letter-spacing:.11em;color:#e0f2fe}.obsdot{display:inline-block;width:9px;height:9px;margin-right:9px;border-radius:50%;background:#22c55e;box-shadow:0 0 18px #22c55e}
.obslinks a{display:inline-block;margin-left:7px;padding:8px 12px;border-radius:9px;text-decoration:none;color:#dbeafe;background:rgba(15,23,42,.82);border:1px solid rgba(148,163,184,.16);font-size:11px;font-weight:900}
.obshero{position:relative;overflow:hidden;padding:38px;margin-bottom:22px;border-radius:28px;border:1px solid rgba(56,189,248,.20);background:linear-gradient(110deg,rgba(2,6,23,.97),rgba(8,47,73,.82),rgba(30,27,75,.82));box-shadow:0 30px 80px rgba(0,0,0,.38)}
.obskicker{color:#7dd3fc;font-size:11px;font-weight:950;letter-spacing:.23em}.obstitle{margin:10px 0;font-size:clamp(36px,5vw,70px);line-height:.94;font-weight:950;letter-spacing:-.045em;background:linear-gradient(90deg,#fff,#7dd3fc,#c4b5fd);-webkit-background-clip:text;color:transparent}.obscopy{max-width:900px;color:#b8c9df;line-height:1.7}
.obspills{display:flex;flex-wrap:wrap;gap:9px;margin-top:18px}.obspill{padding:8px 12px;border-radius:999px;background:rgba(15,23,42,.72);border:1px solid rgba(148,163,184,.20);font-size:11px;font-weight:850}
.obsguide{margin:20px 0;padding:16px 19px;border-radius:16px;background:rgba(2,6,23,.72);border:1px solid rgba(125,211,252,.14);color:#94a3b8;font-size:12px;line-height:1.8}.green{color:#86efac}.amber{color:#fcd34d}.purple{color:#c4b5fd}
'''
    s=s[:i]+css+'\n'+s[i:]

if 'APPLICATION PERFORMANCE - BUSINESS TELEMETRY' not in s:
    m=re.search(r'<body[^>]*>',s,re.I)
    if not m:
        raise SystemExit('ERROR: Observability body tag not found.')
    hero='''
<div class="obsnav"><div class="obsbrand"><span class="obsdot"></span>ORDERS TELEMETRY</div><div class="obslinks"><a href="orders">ORDERS</a><a href="observability">OBSERVABILITY</a><a href="app-metrics">RAW METRICS</a></div></div>
<div class="obshero"><div class="obskicker">APPLICATION PERFORMANCE - BUSINESS TELEMETRY</div><div class="obstitle">APPLICATION<br>OBSERVABILITY</div><div class="obscopy">End-to-end operational evidence across HTTP, Tomcat workers, JVM execution, application sessions, JDBC, PostgreSQL, Redis, RabbitMQ and Stripe payment processing.</div><div class="obspills"><span class="obspill"><span class="obsdot"></span>LIVE TELEMETRY</span><span class="obspill">PERSISTENT SESSION HISTORY</span><span class="obspill">BUSINESS TRANSACTION CORRELATION</span></div></div>
<div class="obsguide"><b>SESSION HEALTH:</b> <span class="green">ACTIVE + RECENT = HEALTHY</span> | <span class="amber">ACTIVE + STALE TELEMETRY = STALE</span> | DESTROYED = ENDED | <span class="purple">ABANDONED_ON_RESTART = RESTART TERMINATED</span></div>
'''
    s=s[:m.end()]+hero+s[m.end():]
OBS.write_text(s)
print('Observability patched.')

# ---------- Orders navigation ----------
s=ORD.read_text()
if 'OBS_NAV_V5' not in s:
    m=re.search(r'(\s*Ui\.pageStart\(out,\s*"Orders Command Center",.*?\buser\);\s*)',s,re.S)
    if not m:
        raise SystemExit('ERROR: Orders Ui.pageStart block not found.')
    nav='''
            // OBS_NAV_V5
            String observabilityContext = req.getContextPath();
            out.println("<div style='display:flex;justify-content:flex-end;margin-bottom:18px'><a href='" + observabilityContext + "/observability' style='padding:12px 18px;border-radius:12px;background:linear-gradient(135deg,#0f172a,#075985);color:white;text-decoration:none;font-weight:900;letter-spacing:.07em;box-shadow:0 12px 30px rgba(2,132,199,.20)'>&#128225; OBSERVABILITY</a></div>");
'''
    s=s[:m.end()]+nav+s[m.end():]
ORD.write_text(s)
print('Orders navigation patched.')

# ---------- ASH marquee ----------
s=ASH.read_text()
if 'ASH_MARQUEE_V5' not in s:
    i=s.find('</style>')
    if i < 0:
        raise SystemExit('ERROR: SessionHistoryServlet.java has no </style>.')
    css='''
/* ASH_MARQUEE_V5 */
.ashmq{width:100%;overflow:hidden;margin:10px 0 22px;padding:15px 0;border-top:1px solid rgba(56,189,248,.22);border-bottom:1px solid rgba(56,189,248,.22);background:linear-gradient(90deg,rgba(2,6,23,.10),rgba(8,47,73,.25),rgba(76,29,149,.20),rgba(2,6,23,.10))}
.ashtrack{display:inline-block;min-width:max-content;white-space:nowrap;padding-left:100%;animation:ashmove 18s linear infinite}.ashtext{display:inline-block;padding-right:100px;font-size:clamp(26px,4vw,52px);font-weight:950;letter-spacing:.18em;background:linear-gradient(90deg,#fff,#67e8f9,#c4b5fd,#fff);-webkit-background-clip:text;color:transparent}.ashmq:hover .ashtrack{animation-play-state:paused}@keyframes ashmove{from{transform:translateX(0)}to{transform:translateX(-100%)}}
'''
    s=s[:i]+css+'\n'+s[i:]
if 'class="ashmq"' not in s:
    repl='<div class="ashmq"><div class="ashtrack"><span class="ashtext">APPLICATION SESSION HISTORY &nbsp;&nbsp;◆&nbsp;&nbsp; APPLICATION SESSION HISTORY &nbsp;&nbsp;◆&nbsp;&nbsp;</span></div></div>'
    ns,n=re.subn(r'<h1[^>]*>\s*APPLICATION SESSION HISTORY\s*</h1>',repl,s,count=1,flags=re.I)
    if not n:
        raise SystemExit('ERROR: APPLICATION SESSION HISTORY heading not found.')
    s=ns
ASH.write_text(s)
print('ASH marquee patched.')
PY

echo '=== 3. BUILD ==='
mvn clean package

WAR=$(find target -maxdepth 1 -type f -name '*.war' ! -name '*sources*' ! -name '*javadoc*' | head -1)
[[ -n "$WAR" ]] || { echo 'ERROR: WAR not found'; exit 1; }
echo "WAR=$WAR"

echo '=== 4. DEPLOY ==='
systemctl stop tomcat
rm -rf /opt/tomcat/webapps/orders
cp "$WAR" /opt/tomcat/webapps/orders.war
systemctl start tomcat

echo '=== 5. WAIT / VERIFY ==='
READY=0
for i in $(seq 1 30); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/orders/observability || true)
  if [[ "$CODE" == 200 ]]; then READY=1; break; fi
  sleep 2
done

if [[ "$READY" != 1 ]]; then
  echo 'ERROR: Observability did not become ready.'
  journalctl -u tomcat -n 100 --no-pager
  exit 1
fi

systemctl is-active tomcat
echo "Orders HTTP:        $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/orders/orders || true)"
echo "Observability HTTP: $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/orders/observability || true)"
echo "Metrics HTTP:       $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/orders/app-metrics || true)"

SID=$(PGPASSWORD=orders_pass psql -h localhost -U orders_user -d ordersdb -Atc "SELECT session_id FROM app_sessions ORDER BY created_at DESC LIMIT 1;" 2>/dev/null || true)
if [[ -n "$SID" ]]; then
  echo "ASH HTTP:           $(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8080/orders/session-history?id=$SID" || true)"
  echo "ASH Report HTTP:    $(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8080/orders/session-history?id=$SID&format=txt" || true)"
fi

echo '=== 6. SESSION LIFECYCLE ==='
PGPASSWORD=orders_pass psql -h localhost -U orders_user -d ordersdb -P pager=off -c "
SELECT session_id,
       COALESCE(NULLIF(username,''),'(anonymous)') AS username,
       status AS lifecycle,
       CASE WHEN status <> 'ACTIVE' THEN 'NOT ACTIVE'
            WHEN last_seen_at < CURRENT_TIMESTAMP - INTERVAL '2 minutes' THEN 'STALE'
            ELSE 'HEALTHY' END AS live_health,
       request_count, created_at, last_seen_at, ended_at
FROM app_sessions
ORDER BY created_at DESC
LIMIT 10;"

echo '============================================================'
echo ' UPGRADE COMPLETE'
echo '============================================================'
echo 'Main application:'
echo 'http://192.168.64.2:8080/orders/orders'
echo
echo 'Observability:'
echo 'http://192.168.64.2:8080/orders/observability'
if [[ -n "$SID" ]]; then
  echo
  echo "Latest ASH: http://192.168.64.2:8080/orders/session-history?id=$SID"
  echo "ASH report: http://192.168.64.2:8080/orders/session-history?id=$SID&format=txt"
fi
