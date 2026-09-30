#!/usr/bin/env bash
# Replays the Priority blend for one or more candidate constant sets (Phase 7, D-09 / OPS-02).
# Read-only: every psql session runs with default_transaction_read_only=on.
#
# Usage: scripts/interest-calibration-replay.sh PP:HIGH:NEUTRAL [PP:HIGH:NEUTRAL ...]
#   PP       profile points
#   HIGH     badge at or above this is high
#   NEUTRAL  badge at or above this (and below HIGH) is neutral
#
# Env: MYFEEDER_PG_PASSWORD (required), PGHOST (pg.bartram.org), PGPORT (5432), PGUSER (myfeeder),
#      PGDATABASE (myfeeder), LEARN_RATE (2), LEARNED_CAP (20), WINDOW_DAYS (14),
#      ENGAGEMENT_OPEN_WEIGHT (0.25), ENGAGEMENT_SAVE_WEIGHT (0.5), ENGAGEMENT_CAP (8),
#      OUT_DIR ($HOME/.cache/myfeeder-phase07/replay)
# Output: one tab-separated file per candidate, $OUT_DIR/replay-pp<PP>-hi<HIGH>-ne<NEUTRAL>.tsv
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "usage: $0 PP:HIGH:NEUTRAL [PP:HIGH:NEUTRAL ...]" >&2
  exit 2
fi

LEARN_RATE="${LEARN_RATE:-2}"
LEARNED_CAP="${LEARNED_CAP:-20}"
ENGAGEMENT_OPEN_WEIGHT="${ENGAGEMENT_OPEN_WEIGHT:-0.25}"
ENGAGEMENT_SAVE_WEIGHT="${ENGAGEMENT_SAVE_WEIGHT:-0.5}"
ENGAGEMENT_CAP="${ENGAGEMENT_CAP:-8}"
WINDOW_DAYS="${WINDOW_DAYS:-14}"

# Every value becomes SQL text through psql -v, so validate all of them before any connection.
for candidate in "$@"; do
  if [[ ! "$candidate" =~ ^[0-9]+:[0-9]+:[0-9]+$ ]]; then
    echo "invalid candidate: $candidate" >&2
    exit 2
  fi
done
for value in "$LEARN_RATE" "$ENGAGEMENT_OPEN_WEIGHT" "$ENGAGEMENT_SAVE_WEIGHT" "$ENGAGEMENT_CAP"; do
  if [[ ! "$value" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
    echo "invalid candidate: $value" >&2
    exit 2
  fi
done
for value in "$LEARNED_CAP" "$WINDOW_DAYS"; do
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    echo "invalid candidate: $value" >&2
    exit 2
  fi
done

if [[ -z "${MYFEEDER_PG_PASSWORD:-}" ]]; then
  echo "MYFEEDER_PG_PASSWORD is required" >&2
  exit 2
fi

export PGOPTIONS='-c default_transaction_read_only=on'
export PGPASSWORD="$MYFEEDER_PG_PASSWORD"
export PGHOST="${PGHOST:-pg.bartram.org}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-myfeeder}"
export PGDATABASE="${PGDATABASE:-myfeeder}"

OUT_DIR="${OUT_DIR:-$HOME/.cache/myfeeder-phase07/replay}"
umask 077
mkdir -p "$OUT_DIR"

SQL_FILE="$(dirname "$0")/interest-calibration-replay.sql"

for candidate in "$@"; do
  IFS=: read -r pp high neutral <<<"$candidate"
  out="$OUT_DIR/replay-pp${pp}-hi${high}-ne${neutral}.tsv"
  psql -X -A -F $'\t' -P footer=off -v ON_ERROR_STOP=1 \
    -v profilePoints="$pp" -v learnRate="$LEARN_RATE" -v learnedCap="$LEARNED_CAP" \
    -v engagementOpenWeight="$ENGAGEMENT_OPEN_WEIGHT" -v engagementSaveWeight="$ENGAGEMENT_SAVE_WEIGHT" \
    -v engagementCap="$ENGAGEMENT_CAP" \
    -v tierHigh="$high" -v tierNeutral="$neutral" -v windowDays="$WINDOW_DAYS" \
    -f "$SQL_FILE" > "$out"
  echo "replayed pp=$pp high=$high neutral=$neutral -> $out"
done
