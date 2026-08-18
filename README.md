# Orders Production Performance Lab

A Java/Tomcat application lab for studying application performance, JVM behavior, database activity, sessions, caching, messaging, load testing, failure simulation, and recovery.

## Architecture

Browser / Locust / JMeter
        |
        | HTTP :8080
        v
Apache Tomcat 11
        |
    orders.war
        |
        +---- PostgreSQL :5432
        |
        +---- Redis :6379
        |
        +---- RabbitMQ :5672

## Current Lab Stack

- Ubuntu 24.04 LTS
- Java 21
- Maven
- Apache Tomcat 11
- PostgreSQL
- Redis
- RabbitMQ
- Locust
- JMeter
- Java Flight Recorder (JFR)
- JVM diagnostic tools
- Deployment readiness scripts

Optional monitoring components:

- Prometheus
- Grafana
- JMX Exporter

## Application

Build artifact:

    target/orders.war

Tomcat deployment:

    /opt/tomcat/webapps/orders.war

Application URL:

    http://<VM_IP>:8080/orders/

Default LAB UI login:

    Username: admin
    Password: admin123

These credentials are for the isolated lab only.

## Application Areas

The lab application includes:

- Orders Command Center
- Shopping Cart
- Session Diagnostics
- Inventory Control
- Payments and Settlements
- Reports
- System Health
- Heap Leak Control
- Cache Pressure Control
- Queue Pressure Control
- Recovery Control Center

The application is intentionally designed to allow load, failure,
memory, session, database, cache, and messaging experiments.

## Build

From the project directory:

    cd /root/orders-prod-lab
    mvn clean package

The WAR is created at:

    target/orders.war

## Deploy to Tomcat

    systemctl stop tomcat
    rm -rf /opt/tomcat/webapps/orders
    rm -f /opt/tomcat/webapps/orders.war
    cp target/orders.war /opt/tomcat/webapps/orders.war
    systemctl start tomcat

Verify:

    systemctl status tomcat
    curl -I http://localhost:8080/orders/

## Required Services

Check the complete application stack:

    systemctl status tomcat
    systemctl status postgresql
    systemctl status redis-server
    systemctl status rabbitmq-server

Quick checks:

    redis-cli ping
    rabbitmq-diagnostics ping

Check ports:

    ss -lntp | grep -E '8080|5432|6379|5672'

Expected services:

    Tomcat       8080
    PostgreSQL   5432
    Redis        6379
    RabbitMQ     5672

## PostgreSQL Database

Database:

    ordersdb

Application database user:

    orders_user

The clean reproducible database definition is stored in:

    database/schema.sql

This allows the baseline database structure to be recreated on
another lab environment.

IMPORTANT:

schema.sql defines the baseline database.

It is NOT a backup of changing runtime data.

## Database Backup

Create a backup of the current live database:

    ./database/backup-database.sh

Backups are stored under:

    database/backups/

Database dump files are excluded from Git.

The distinction is:

    database/schema.sql
        = rebuild a clean lab database

    database/backups/*.dump
        = snapshot of the real database and runtime data

## JVM Diagnostics

Find the Tomcat JVM:

    PID=$(pgrep -f 'org.apache.catalina.startup.Bootstrap' | head -1)

Useful commands:

    jcmd $PID VM.flags
    jcmd $PID VM.command_line
    jcmd $PID GC.heap_info

GC monitoring:

    watch -n 2 "jstat -gcutil $PID 1 1"

    jstat -gc $PID 5000 10

## Logs

Tomcat systemd logs:

    journalctl -u tomcat -f

Tomcat logs:

    tail -f /opt/tomcat/logs/catalina*.log

Search for major application/JVM errors:

    grep -Ei \
    "SEVERE|OutOfMemory|Exception|ERROR|startup failed" \
    /opt/tomcat/logs/catalina*.log

Kernel/OOM events:

    dmesg -T | grep -Ei \
    "oom|out of memory|killed process|java|tomcat"

## Heap Dump Configuration

Heap dumps can be enabled in the Tomcat JVM configuration with:

    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/mnt/data/tomcat-oom/heapdump.hprof

Heap dumps must not be committed to Git.

## Heap Pressure Testing

Example:

    for i in $(seq 1 12); do
      curl -s \
      "http://localhost:8080/orders/heap?action=add&scope=global&mb=50"
      sleep 2
    done

Use failure controls only inside an isolated lab.

## Java Flight Recorder

JFR can be used for:

- Garbage collection analysis
- Allocation analysis
- Thread analysis
- Lock contention
- CPU analysis
- JVM/application performance troubleshooting

JFR recordings are runtime diagnostic artifacts and should not
be committed to Git.

## Locust

Create the Python environment locally:

    python3 -m venv .venv
    source .venv/bin/activate
    pip install locust

Run:

    locust \
      -f locustfile.py \
      --host=http://localhost:8080

Locust UI:

    http://<VM_IP>:8089

Python virtual environments are deliberately excluded from Git.

## JMeter

JMeter test plans included in the repository:

    orders-load-test.jmx
    session-cart-load.jmx

Generated JTL result files are excluded from Git.

## Deployment Decision Engine

Run:

    cd /root/orders-prod-lab
    chmod +x deployment_decider.sh
    ./deployment_decider.sh

Possible results include:

    READINESS_CONFIRMED
    READINESS_CONFIRMED_WITH_ANOMALIES
    ALARM_KEEP_HC_DISABLED

## Monitoring

When the optional monitoring stack is installed:

    Grafana:
    http://<VM_IP>:3000

    Prometheus:
    http://<VM_IP>:9090

    JMX Metrics:
    http://<VM_IP>:9404/metrics

    Locust:
    http://<VM_IP>:8089

## Repository Layout

    orders-prod-lab/
    |
    +-- src/                         Java application source
    |
    +-- database/
    |   +-- schema.sql               reproducible DB schema
    |   +-- backup-database.sh       DB backup utility
    |   +-- backups/                 local DB dumps (not Git)
    |
    +-- pom.xml                      Maven project
    +-- locustfile.py                Locust load tests
    +-- locust_business_failures.py  failure load tests
    +-- orders-load-test.jmx         JMeter test
    +-- session-cart-load.jmx        session/cart JMeter test
    +-- classify_deploy.sh
    +-- deployment_decider.sh
    +-- .gitignore
    +-- README.md

## Portability

The repository preserves the application source and clean database
definition.

A new environment can therefore follow this general workflow:

    Install Java 21
          |
    Install Maven
          |
    Install Tomcat 11
          |
    Install PostgreSQL
          |
    Install Redis
          |
    Install RabbitMQ
          |
    Clone this repository
          |
    Create ordersdb
          |
    Load database/schema.sql
          |
    mvn clean package
          |
    Deploy orders.war
          |
    Start services
          |
    Orders Lab UP

Runtime PostgreSQL data should be moved between environments using
PostgreSQL backups rather than Git.

## Purpose

This repository is an engineering/performance lab.

It is intended for learning and experimentation with:

- Linux
- Java/JVM
- Tomcat
- PostgreSQL
- Redis
- RabbitMQ
- HTTP sessions
- JVM threads
- garbage collection
- memory behavior
- load generation
- application dependencies
- failure simulation
- performance troubleshooting

Failure-injection functionality should never be exposed on a
production system.
