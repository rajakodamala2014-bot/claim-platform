#!/usr/bin/env bash
# End-to-end smoke test against a running stack: walks one claim from sign-in to SETTLED through the gateway
# and checks the portal, the notification inbox and the insights dashboards on the way.
#
#   ./deployment/smoke.sh                     # http://localhost:8000, seed password
#   GATEWAY=https://claims.example.com PASSWORD='...' ./deployment/smoke.sh
#
# Needs bash and curl only. Exits non-zero at the first failed step.
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8000}"
PASSWORD="${PASSWORD:-ChangeMe123!}"
STEP=0
BODY="$(mktemp)"
HEADERS="$(mktemp)"
trap 'rm -f "$BODY" "$HEADERS"' EXIT

fail() { echo "FAIL  $*" >&2; echo "      response: $(head -c 600 "$BODY")" >&2; exit 1; }

# call <expected-status> <description> <curl args...>   -> body in $BODY, headers in $HEADERS
call() {
  local expected="$1" what="$2"; shift 2
  STEP=$((STEP + 1))
  local status
  status="$(curl -sS -o "$BODY" -D "$HEADERS" -w '%{http_code}' "$@")" || fail "$what (no response)"
  [[ "$status" == "$expected" ]] || fail "$what: expected HTTP $expected, got $status"
  printf 'ok    %2d  %s\n' "$STEP" "$what"
}

json() { grep -o "\"$1\":\"\?[^\",}]*" "$BODY" | head -1 | sed -E 's/^"[^"]+":"?//'; }   # first value of a simple field
etag() { grep -i '^etag:' "$HEADERS" | tail -1 | sed -E 's/^[^:]+: *//; s/\r$//'; }

login() {
  call 200 "sign in as $1" -X POST "$GATEWAY/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}"
  TOKEN="$(json access_token)"
}

staff() {                                  # staff <expected> <description> <token> <path> [json body]
  local expected="$1" what="$2" token="$3" path="$4" body="${5:-}"
  local args=(-X POST "$GATEWAY$path" -H "Authorization: Bearer $token" -H "If-Match: $ETAG")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  call "$expected" "$what" "${args[@]}"
  ETAG="$(etag)"
}

echo "Smoke test against $GATEWAY"

call 200 "portal is served" "$GATEWAY/"
grep -qi '<div id="root"' "$BODY" || fail "portal page has no React root"
call 404 "actuator is not exposed on the public port" "$GATEWAY/actuator/health"
call 401 "API without a token is rejected" "$GATEWAY/claimant/v1/claims"

login priya; PRIYA="$TOKEN"
login arun;  ARUN="$TOKEN"
login meera; MEERA="$TOKEN"
login lee;   LEE="$TOKEN"

call 200 "customer lists policies (Policy system)" "$GATEWAY/claimant/v1/policies" -H "Authorization: Bearer $PRIYA"
grep -q 'SG-MTR-POL-1001' "$BODY" || fail "policy SG-MTR-POL-1001 not listed"

call 201 "customer requests an upload URL" -X POST "$GATEWAY/claimant/v1/uploads" -H "Authorization: Bearer $PRIYA" \
  -H 'Content-Type: application/json' -d '{"fileName":"dashcam.jpg","contentType":"image/jpeg","sizeBytes":11}'
UPLOAD_ID="$(json uploadId)"
UPLOAD_URL="$(json uploadUrl)"
# the URL points at PUBLIC_BASE_URL; send it to the gateway under test, keeping path and signature
call 200 "customer uploads the file to the signed URL" -X PUT "$GATEWAY/${UPLOAD_URL#*://*/}" \
  -H 'Content-Type: image/jpeg' --data-binary 'hello photo'

KEY="$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen 2>/dev/null || powershell -NoProfile -Command '[guid]::NewGuid().ToString()')"
KEY="${KEY//$'\r'/}"
INCIDENT_AT="$(date -u -d '-1 day' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v-1d +%Y-%m-%dT%H:%M:%SZ)"
call 201 "customer submits a claim (FNOL + cover check)" -X POST "$GATEWAY/claimant/v1/claims" \
  -H "Authorization: Bearer $PRIYA" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "{
    \"policyNumber\":\"SG-MTR-POL-1001\",\"lineOfBusiness\":\"MOTOR\",\"incidentAt\":\"$INCIDENT_AT\",
    \"incident\":{\"peril\":\"COLLISION\",\"description\":\"Rear-ended at the traffic lights\",\"location\":\"Orchard Road\",
      \"vehicleRegistration\":\"SBA1234X\",\"driverName\":\"Priya Nair\",\"damageDescription\":\"Rear bumper and boot\",
      \"thirdParties\":[{\"name\":\"John Lim\",\"vehicleRegistration\":\"SGX9876Z\",\"insurer\":\"NTUC Income\"}]},
    \"claimedAmount\":{\"amount\":\"6000.00\",\"currency\":\"SGD\"},\"uploadIds\":[\"$UPLOAD_ID\"],\"contact\":{\"phone\":\"+6591234567\"}}"
CLAIM="$(json claimId)"
echo "          claim $(json claimNumber) ($CLAIM), status $(json status)"

call 200 "handler opens the claim workspace" "$GATEWAY/staff/v1/claims/$CLAIM" -H "Authorization: Bearer $ARUN"
ETAG="$(etag)"
staff 200 "handler picks up the claim" "$ARUN" "/staff/v1/claims/$CLAIM/assignment"
staff 200 "handler starts the assessment" "$ARUN" "/staff/v1/claims/$CLAIM/start-assessment"
staff 200 "handler sets a reserve of SGD 12,000" "$ARUN" "/staff/v1/claims/$CLAIM/reserves" \
  '{"amount":{"amount":"12000.00","currency":"SGD"},"reason":"Repair quote received"}'
staff 201 "handler records the assessment" "$ARUN" "/staff/v1/claims/$CLAIM/assessments" \
  '{"liability":"FULL","assessedAmount":{"amount":"12000.00","currency":"SGD"},"notes":"Third party at fault"}'
staff 202 "handler proposes approval above their limit (waits for a team lead)" "$ARUN" "/staff/v1/claims/$CLAIM/decisions" \
  '{"outcome":"APPROVE","amount":{"amount":"12000.00","currency":"SGD"}}'
DECISION="$(json decisionId)"
staff 200 "team lead approves the decision" "$MEERA" "/staff/v1/claims/$CLAIM/decisions/$DECISION/approval" '{"action":"APPROVE"}'
staff 201 "handler instructs the payment" "$ARUN" "/staff/v1/claims/$CLAIM/settlements" \
  '{"payee":{"name":"Priya Nair","bankCode":"DBSSSGSG","accountNumber":"123456789"},"amount":{"amount":"12000.00","currency":"SGD"}}'

STATUS=""
for _ in $(seq 1 30); do                   # Kafka round trip through the payments simulator (about 3 s)
  curl -sS -o "$BODY" "$GATEWAY/claimant/v1/claims/$CLAIM" -H "Authorization: Bearer $PRIYA"
  STATUS="$(json status)"
  [[ "$STATUS" == "SETTLED" ]] && break
  sleep 1
done
STEP=$((STEP + 1))
[[ "$STATUS" == "SETTLED" ]] || fail "claim did not reach SETTLED (still $STATUS): check payments-simulator and Kafka"
printf 'ok    %2d  %s\n' "$STEP" "payment result arrived over Kafka: claim is SETTLED"

for _ in $(seq 1 20); do                   # notifications are planned from Kafka and dispatched every second
  curl -sS -o "$BODY" "$GATEWAY/claimant/v1/notifications" -H "Authorization: Bearer $PRIYA"
  grep -q "$CLAIM" "$BODY" && break
  sleep 1
done
STEP=$((STEP + 1))
grep -q "$CLAIM" "$BODY" || fail "no notification for the claim in the customer's inbox: check notification-service"
printf 'ok    %2d  %s\n' "$STEP" "customer inbox has notifications about the claim"

call 200 "team lead reads the workload dashboard (Insights)" "$GATEWAY/staff/v1/insights/workload" -H "Authorization: Bearer $MEERA"
call 200 "manager reads the exposure dashboard (Insights)" "$GATEWAY/staff/v1/insights/exposure?market=SG" -H "Authorization: Bearer $LEE"
call 200 "staff search finds the claim (Insights read model)" "$GATEWAY/staff/v1/search?status=SETTLED&market=SG" -H "Authorization: Bearer $ARUN"
grep -q "$CLAIM" "$BODY" || fail "settled claim not in the Insights read model"
call 403 "a customer cannot use the staff surface" "$GATEWAY/staff/v1/work-queue" -H "Authorization: Bearer $PRIYA"

echo "All $STEP steps passed."
