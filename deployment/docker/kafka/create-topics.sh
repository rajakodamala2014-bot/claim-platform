#!/usr/bin/env bash
# Creates the platform topics (idempotent). Partitions/retention follow the topic design:
#   claims.claim-events           12 partitions, 30 days
#   payments.settlement-requests   6 partitions, 30 days
#   payments.payment-results       6 partitions, 30 days
#   insights.alerts                3 partitions,  7 days
#   <topic>.dlq                    same partitions as source, 14 days (one per consumed topic)
set -uo pipefail
BROKERS="${BROKERS:-redpanda:9092}"
DAY_MS=86400000

create() { # topic partitions retention_days
  local out
  if out=$(rpk -X brokers="$BROKERS" topic create "$1" -p "$2" -r 1 -c retention.ms=$(( $3 * DAY_MS )) 2>&1); then
    echo "created $1 ($2 partitions, $3d)"
  elif grep -q "TOPIC_ALREADY_EXISTS" <<<"$out"; then
    echo "exists  $1"
  else
    echo "$out" >&2
    exit 1
  fi
}

create claims.claim-events            12 30
create payments.settlement-requests    6 30
create payments.payment-results        6 30
create insights.alerts                 3 7
create claims.claim-events.dlq        12 14
create payments.payment-results.dlq    6 14
create insights.alerts.dlq             3 14
