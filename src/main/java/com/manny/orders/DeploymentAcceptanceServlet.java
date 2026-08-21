package com.manny.orders;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*; import java.sql.*; import java.lang.management.*;
import redis.clients.jedis.Jedis;
import com.rabbitmq.client.*;
@WebServlet("/deployment-acceptance")
public class DeploymentAcceptanceServlet extends HttpServlet {
 private String e(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
 private String env(String k,String d){String v=System.getenv(k);return v==null||v.isBlank()?d:v;}
 private void row(PrintWriter o,String n,String s,String ev,String action){o.println("<tr><td><b>"+e(n)+"</b></td><td class='"+s+"'><b>"+s+"</b></td><td>"+ev+"</td><td>"+e(action)+"</td></tr>");}
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
  r.setContentType("text/html;charset=UTF-8"); PrintWriter o=r.getWriter(); boolean failed=false;
  String sha=env("ORDERS_WAR_SHA256","unknown"), git=env("ORDERS_GIT_COMMIT","unknown"); HttpSession s=q.getSession(true); Thread th=Thread.currentThread();
  String db="",redis="",rabbit=""; boolean dbok=false,rok=false,mqok=false;
  try(java.sql.Connection c=Db.getConnection();PreparedStatement p=c.prepareStatement("select pg_backend_pid()")){ResultSet x=p.executeQuery();x.next();db="Backend PID "+x.getInt(1);dbok=true;}catch(Exception x){db=x.getClass().getSimpleName()+": "+x.getMessage();failed=true;}
  try(Jedis j=new Jedis(env("REDIS_HOST","localhost"),Integer.parseInt(env("REDIS_PORT","6379")))){redis="PING -> "+j.ping();rok=redis.contains("PONG");if(!rok)failed=true;}catch(Exception x){redis=x.getClass().getSimpleName()+": "+x.getMessage();failed=true;}
  try{ConnectionFactory f=new ConnectionFactory();f.setHost(env("RABBITMQ_HOST","localhost"));f.setPort(Integer.parseInt(env("RABBITMQ_PORT","5672")));f.setUsername(env("RABBITMQ_USER","guest"));f.setPassword(env("RABBITMQ_PASSWORD","guest"));try(com.rabbitmq.client.Connection c=f.newConnection();Channel ch=c.createChannel()){mqok=c.isOpen()&&ch.isOpen();rabbit="AMQP connection/channel opened";if(!mqok)failed=true;}}catch(Exception x){rabbit=x.getClass().getSimpleName()+": "+x.getMessage();failed=true;}
  String stripe=env("STRIPE_SECRET_KEY",""); String verdict=failed?"FAILED":(stripe.isBlank()?"PARTIAL":"PASSED");
  o.println("<!doctype html><html><head><title>Deployment Acceptance</title><style>body{background:#020617;color:#e5eefb;font-family:Inter,system-ui;padding:28px}a{color:#67e8f9;font-weight:900}.w{max-width:1450px;margin:auto}.box{background:#07111f;border:1px solid #38bdf833;border-radius:18px;padding:22px;margin:16px 0}.v{font-size:44px;font-weight:950}.PASS,.PASSED{color:#4ade80}.FAIL,.FAILED{color:#fb7185}.PARTIAL,.NOT_PROVEN{color:#fbbf24}table{width:100%;border-collapse:collapse}th,td{padding:12px;border-bottom:1px solid #334155;text-align:left}th{color:#7dd3fc;background:#0b1628}.muted{color:#9fb3ca;line-height:1.6}</style></head><body><div class='w'><p><a href='observability'>← Observability</a></p><div class='box'><b>DEPLOYMENT ACCEPTANCE</b><div class='v "+verdict+"'>"+verdict+"</div><p class='muted'>Read top to bottom. The first FAIL is where you start troubleshooting. A URL/login proves only part of the path; a real Business Transaction Trace is still the strongest proof of business/payment correctness.</p><p>WAR SHA-256: <code>"+e(sha)+"</code><br>Git: <code>"+e(git)+"</code></p></div><div class='box'><table><tr><th>Stage</th><th>Result</th><th>Evidence / click to correlate</th><th>Action if failed</th></tr>");
  row(o,"WAR / BUILD IDENTITY","unknown".equals(sha)?"NOT_PROVEN":"PASS",e("SHA-256 "+sha+" | Git "+git),"Redeploy with V8 artifact identity.");
  row(o,"HTTP / WAR EXECUTION","PASS","This servlet executed from the deployed orders WAR.","Check Tomcat deployment/logs.");
  row(o,"SESSION","PASS","<a href='session-history?id="+s.getId()+"'>"+e(s.getId())+"</a>","Check session lifecycle/config.");
  row(o,"TOMCAT THREAD","PASS","<a href='thread-inspect?id="+th.getId()+"'>"+e(th.getName()+" | JVM TID "+th.getId())+"</a>","Open Thread Inspector.");
  row(o,"POSTGRESQL",dbok?"PASS":"FAIL",e(db),"Check PostgreSQL service/JDBC."); row(o,"REDIS",rok?"PASS":"FAIL",e(redis),"Check redis-server and port 6379."); row(o,"RABBITMQ",mqok?"PASS":"FAIL",e(rabbit),"Check RabbitMQ service/listener/credentials.");
  row(o,"STRIPE",stripe.isBlank()?"NOT_PROVEN":"PASS",stripe.isBlank()?"No Stripe secret visible to JVM":"Stripe configuration present","Open a real Business Transaction Trace; configuration alone does not prove a payment.");
  row(o,"BUSINESS ASSERTION","NOT_PROVEN","Use a completed Business Transaction Trace for expected vs Stripe vs database totals.","Run a real test-mode business transaction.");
  o.println("</table></div><div class='box'><h2>Junior engineer mental model</h2><p class='muted'>WAR identity → HTTP code executes → session → Tomcat worker → PostgreSQL → Redis → RabbitMQ → Stripe/business transaction. Follow the ladder until the first red result. Then click the correlated Session/Thread/Transaction ID instead of guessing.</p></div></div></body></html>");
 }
}
