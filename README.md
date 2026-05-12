Tomcat JVM Failure Simulation Lab
===============================================================================================================================================================
A summarized lab for testing Tomcat deployment readiness, JVM memory pressure, GC behavior, heap dumps, JFR recordings, Locust traffic, and monitoring with Prometheus/Grafana.

===============================================================================
Purpose
================================================================================
This lab helps validate how a Tomcat application behaves during:
WAR deployment
JVM heap pressure
OutOfMemoryError events
GC pressure
Session/cache/queue growth
Application readiness checks
Runtime anomalies
Recovery actions
Main Application

The application is deployed as:
orders.war
Main URL:
http://<VM_IP>:8080/orders/

Core Components
Tomcat 11
Java 17
Maven
orders.war
deployment_decider.sh
JFR
GC logs
Heap dumps
Prometheus
Grafana
JMX Exporter
Locust
Basic Workflow
cd /root/war-lab

mvn clean package

systemctl stop tomcat
rm -rf /opt/tomcat/webapps/orders*
cp target/orders.war /opt/tomcat/webapps/orders.war
chown tomcat:tomcat /opt/tomcat/webapps/orders.war
systemctl start tomcat

Verify Application
curl -I http://localhost:8080/orders/
curl -s http://localhost:8080/orders/

USEFUL COMMANDS
===============================================================================================================================================================

systemctl status tomcat
JVM Checks
PID=$(pgrep -f 'org.apache.catalina.startup.Bootstrap' | head -1)

sudo -u tomcat jcmd $PID VM.flags
sudo -u tomcat jcmd $PID VM.command_line
sudo -u tomcat jcmd $PID GC.heap_info

Garbage Collection  Monitoring
watch -n 2 "jstat -gcutil $(pgrep -f Bootstrap | head -1) 1 1"
jstat -gc $(pgrep -f Bootstrap | head -1) 5000 10
Logs
journalctl -u tomcat -f
tail -f /opt/tomcat/logs/catalina*.log
grep -Ei "SEVERE|OutOfMemory|Exception|ERROR|startup failed|Marking this application unavailable" /opt/tomcat/logs/catalina*.log
OOM / Kernel Check
dmesg -T | grep -Ei "oom|out of memory|killed process|java|tomcat"

Heap Dump Configuration
 vi /etc/systemd/system/tomcat.service
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/mnt/data/tomcat-oom/heapdump.hprof


Trigger Heap Pressure
for i in $(seq 1 12); do
  curl -s "http://localhost:8080/orders/heap?action=add&scope=global&mb=50"
  sleep 2
done

===============================================================================

JAVA FLIGHT RECORDING
===============================================================================

Use JFR for:
GC analysis
Allocation analysis
Thread analysis
Lock contention
Performance troubleshooting

Monitoring URLs
Grafana:     http://<VM_IP>:3000
Prometheus:  http://<VM_IP>:9090
JMX Metrics: http://<VM_IP>:9404/metrics
Locust UI:   http://<VM_IP>:8089

Locust
python3 -m venv /root/locust-venv
source /root/locust-venv/bin/activate
pip install locust
/root/locust-venv/bin/locust \
  -f /root/oom-locustfile.py \
  --host=http://localhost:8080

Deployment Decision Engine
cd /root/war-lab
chmod +x deployment_decider.sh
./deployment_decider.sh

Expected results:
READINESS_CONFIRMED
READINESS_CONFIRMED_WITH_ANOMALIES
ALARM_KEEP_HC_DISABLED

