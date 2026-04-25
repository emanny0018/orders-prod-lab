#!/usr/bin/env bash
set -euo pipefail

# ---- Config ----
APP_URL="http://localhost:8080/orders/health"
LOG_GLOB="/opt/tomcat/logs/catalina.*.log"
TAIL_LINES=500

# ---- Latest Catalina log ----
LOG_FILE="$(ls -t $LOG_GLOB 2>/dev/null | head -1)"

if [[ -z "${LOG_FILE:-}" ]]; then
  echo "ERROR: No Catalina log found"
  exit 1
fi

# ---- Scan only current Tomcat run ----
START_LINE="$(grep -n "Starting service \[Catalina\]" "$LOG_FILE" | tail -1 | cut -d: -f1 || true)"

# ---- Patterns ----
IGNORE_PATTERNS=(
  "A context path must either be an empty string"
  "HeapDumpOnOutOfMemoryError"
)

CATALINA_SEVERE_PATTERNS=(
  "SEVERE"
  "ERROR"
  "FATAL"
)

# ---- Helpers ----
should_ignore() {
  local line="$1"
  for pattern in "${IGNORE_PATTERNS[@]}"; do
    [[ "$line" == *"$pattern"* ]] && return 0
  done
  return 1
}

is_http_ready() {
  local code="$1"
  [[ "$code" == "200" || "$code" == "204" || "$code" == "302" ]]
}

set_dev_hint_if_empty() {
  local failure="$1"
  local action="$2"
  local evidence="$3"

  if [[ -z "$PRIMARY_FAILURE" ]]; then
    PRIMARY_FAILURE="$failure"
    DEV_ACTION="$action"
    EVIDENCE_LINE="$evidence"
  fi
}

# ---- Evidence flags ----
DEPLOY_ERROR_EVIDENCE=0
CONTEXT_STARTUP_EVIDENCE=0
APP_EXCEPTION_ANOMALY=0
JVM_MEMORY_ANOMALY=0
CATALINA_SEVERE_ANOMALY=0

PRIMARY_FAILURE=""
DEV_ACTION=""
EVIDENCE_LINE=""

# ---- Scan logs for current-run dev/RCA evidence only ----
scan_line() {
  local line="$1"

  [[ -z "$line" ]] && return 0
  should_ignore "$line" && return 0

  if [[ "$line" == *"Parse error in application web.xml file"* || "$line" == *"Parse fatal error"* ]]; then
    DEPLOY_ERROR_EVIDENCE=1
    set_dev_hint_if_empty \
      "WEB_XML_PARSE_ERROR" \
      "Check /opt/tomcat/webapps/orders/WEB-INF/web.xml or WEB-INF/web.xml inside orders.war; XML is malformed or missing closing tags." \
      "$line"
  elif [[ "$line" == *"Error deploying web application archive"* ]]; then
    DEPLOY_ERROR_EVIDENCE=1
    set_dev_hint_if_empty \
      "WAR_DEPLOYMENT_ERROR" \
      "Check /opt/tomcat/webapps/orders.war and the extracted /opt/tomcat/webapps/orders directory." \
      "$line"
  fi

  if [[ "$line" == *"startup failed due to previous errors"* || \
        "$line" == *"Marking this application unavailable"* || \
        "$line" == *"One or more components marked the context as not correctly configured"* ]]; then
    CONTEXT_STARTUP_EVIDENCE=1
    set_dev_hint_if_empty \
      "CONTEXT_STARTUP_FAILED" \
      "Check why Tomcat marked the /orders context unavailable; review the Catalina lines before this message." \
      "$line"
  fi

  if [[ "$line" == *"ClassNotFoundException"* || "$line" == *"NoClassDefFoundError"* ]]; then
    APP_EXCEPTION_ANOMALY=1
    set_dev_hint_if_empty \
      "MISSING_CLASS_OR_DEPENDENCY" \
      "Check application dependencies packaged inside orders.war, especially WEB-INF/lib and build configuration." \
      "$line"
  elif [[ "$line" == *"Caused by:"* ]]; then
    APP_EXCEPTION_ANOMALY=1
    set_dev_hint_if_empty \
      "APPLICATION_EXCEPTION_CHAIN" \
      "Check the Caused by chain in the app/Tomcat logs to find the deepest root cause." \
      "$line"
  fi

  if [[ "$line" == *"java.lang.OutOfMemoryError"* || \
        "$line" == *"GC overhead limit exceeded"* || \
        "$line" == *"Java heap space"* || \
        "$line" == *"OutOfMemoryError: Metaspace"* || \
        "$line" == *"OutOfMemoryError: Direct buffer memory"* ]]; then
    JVM_MEMORY_ANOMALY=1
    set_dev_hint_if_empty \
      "JVM_MEMORY_PRESSURE_OR_OOM" \
      "Check heap dump, GC logs, JVM flags, and memory-heavy application code path." \
      "$line"
  fi

  if [[ "$line" == *"SEVERE"* || "$line" == *"ERROR"* || "$line" == *"FATAL"* ]]; then
    CATALINA_SEVERE_ANOMALY=1
    set_dev_hint_if_empty \
      "CATALINA_SEVERE_OR_ERROR" \
      "Check Catalina log around this line for the exact component and stack trace." \
      "$line"
  fi
}

if [[ -n "${START_LINE:-}" ]]; then
  while IFS= read -r line; do
    scan_line "$line"
  done < <(tail -n +"$START_LINE" "$LOG_FILE")
else
  while IFS= read -r line; do
    scan_line "$line"
  done < <(tail -n "$TAIL_LINES" "$LOG_FILE")
fi

# ---- HC decision: readiness endpoint only ----
HTTP_CODE="$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL" || true)"

if is_http_ready "$HTTP_CODE"; then
  HTTP_READINESS_FAILED=0
  ALARMS_PRESENT=0
else
  HTTP_READINESS_FAILED=1
  ALARMS_PRESENT=1

  if [[ -z "$PRIMARY_FAILURE" ]]; then
    PRIMARY_FAILURE="HTTP_READINESS_FAILED"
    DEV_ACTION="Check $APP_URL from the VM; then inspect Tomcat/app logs for why the health endpoint is not serving."
    EVIDENCE_LINE="curl returned HTTP $HTTP_CODE for $APP_URL"
  fi
fi

# ---- Anomalies present ----
if (( DEPLOY_ERROR_EVIDENCE || CONTEXT_STARTUP_EVIDENCE || APP_EXCEPTION_ANOMALY || JVM_MEMORY_ANOMALY || CATALINA_SEVERE_ANOMALY )); then
  ANOMALIES_PRESENT=1
else
  ANOMALIES_PRESENT=0
fi

# ---- Final decision ----
if (( ALARMS_PRESENT )); then
  FINAL_RESULT="ALARM_KEEP_HC_DISABLED"
elif (( ANOMALIES_PRESENT )); then
  FINAL_RESULT="READINESS_CONFIRMED_WITH_ANOMALIES"
else
  FINAL_RESULT="READINESS_CONFIRMED"
fi

# ---- Summary ----
if (( HTTP_READINESS_FAILED )); then
  SUMMARY="Health endpoint failed with HTTP $HTTP_CODE"
elif (( ANOMALIES_PRESENT )); then
  SUMMARY="Health endpoint passed, but current-run log anomalies need dev review"
else
  SUMMARY="Health endpoint passed cleanly"
fi

# ---- Output ----
echo "APP_URL=$APP_URL"
echo "LOG_FILE=$LOG_FILE"
echo "SCAN_START_LINE=${START_LINE:-LAST_${TAIL_LINES}_LINES}"

echo "HTTP_CODE=$HTTP_CODE"
echo "HTTP_READINESS_FAILED=$HTTP_READINESS_FAILED"
echo "ALARMS_PRESENT=$ALARMS_PRESENT"

echo "DEPLOY_ERROR_EVIDENCE=$DEPLOY_ERROR_EVIDENCE"
echo "CONTEXT_STARTUP_EVIDENCE=$CONTEXT_STARTUP_EVIDENCE"
echo "APP_EXCEPTION_ANOMALY=$APP_EXCEPTION_ANOMALY"
echo "JVM_MEMORY_ANOMALY=$JVM_MEMORY_ANOMALY"
echo "CATALINA_SEVERE_ANOMALY=$CATALINA_SEVERE_ANOMALY"
echo "ANOMALIES_PRESENT=$ANOMALIES_PRESENT"

echo "PRIMARY_FAILURE=${PRIMARY_FAILURE:-NONE}"
echo "DEV_ACTION=${DEV_ACTION:-NONE}"
echo "EVIDENCE_FILE=$LOG_FILE"
echo "EVIDENCE_LINE=${EVIDENCE_LINE:-NONE}"

echo "SUMMARY=$SUMMARY"
echo "FINAL_RESULT=$FINAL_RESULT"
