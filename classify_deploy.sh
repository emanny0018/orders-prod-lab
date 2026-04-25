#!/usr/bin/env bash
set -euo pipefail

LOG_FILE="$(ls -t /opt/tomcat/logs/catalina.*.log | head -1)"
APP_URL="http://localhost:8080/orders/"

ALARM_PATTERNS=(
  "Parse fatal error"
  "Parse error in application web.xml file"
  "Marking this application unavailable"
  "One or more components marked the context as not correctly configured"
  "startup failed due to previous errors"
  "OutOfMemoryError"
  "ClassNotFoundException"
  "NoClassDefFoundError"
)

ANOMALY_PATTERNS=(
  "SEVERE"
  "Exception"
  "ERROR"
  "FATAL"
  "Caused by:"
)

IGNORE_PATTERNS=(
  "A context path must either be an empty string"
)

should_ignore() {
  local line="$1"
  local pattern
  for pattern in "${IGNORE_PATTERNS[@]}"; do
    [[ "$line" == *"$pattern"* ]] && return 0
  done
  return 1
}

echo "Using log file: $LOG_FILE"
echo

alarm_count=0
anomaly_count=0

echo "=== Matching alarm lines ==="
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  should_ignore "$line" && continue

  for pattern in "${ALARM_PATTERNS[@]}"; do
    if [[ "$line" == *"$pattern"* ]]; then
      echo "$line"
      alarm_count=$((alarm_count + 1))
      break
    fi
  done
done < "$LOG_FILE"

echo
echo "=== Matching anomaly lines ==="
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  should_ignore "$line" && continue

  for pattern in "${ANOMALY_PATTERNS[@]}"; do
    if [[ "$line" == *"$pattern"* ]]; then
      echo "$line"
      anomaly_count=$((anomaly_count + 1))
      break
    fi
  done
done < "$LOG_FILE"

echo
echo "=== HTTP check ==="
http_code="$(curl -s -o /tmp/orders_http.out -w "%{http_code}" "$APP_URL" || true)"
echo "URL: $APP_URL"
echo "HTTP code: $http_code"
echo

echo "=== Summary ==="
echo "alarm_count=$alarm_count"
echo "anomaly_count=$anomaly_count"

if (( alarm_count > 0 )) || [[ "$http_code" != "200" && "$http_code" != "204" && "$http_code" != "302" ]]; then
  echo "FINAL_RESULT=ALARM_KEEP_HC_DISABLED"
elif (( anomaly_count > 0 )); then
  echo "FINAL_RESULT=READINESS_CONFIRMED_WITH_ANOMALIES"
else
  echo "FINAL_RESULT=READINESS_CONFIRMED"
fi
