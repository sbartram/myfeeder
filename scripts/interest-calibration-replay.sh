#!/usr/bin/env bash
# Replays the Priority blend for one or more candidate constant sets (Phase 7, D-09 / OPS-02).
# Read-only: every psql session runs with default_transaction_read_only=on.
#
# Usage: scripts/interest-calibration-replay.sh PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP] [...]
#   PP       profile points
#   HIGH     badge at or above this is high
#   NEUTRAL  badge at or above this (and below HIGH) is neutral
#   OPEN     strength of an open (default: ENGAGEMENT_OPEN_WEIGHT)
#   SAVE     strength of a save (default: ENGAGEMENT_SAVE_WEIGHT)
#   CAP      engagement cap per topic, 0 = engagement off (default: ENGAGEMENT_CAP)
#   OPEN, SAVE and CAP are given together or not at all.
#
# Env: MYFEEDER_PG_PASSWORD (required), PGHOST (pg.bartram.org), PGPORT (5432), PGUSER (myfeeder),
#      PGDATABASE (myfeeder), LEARN_RATE (2), LEARNED_CAP (20), WINDOW_DAYS (14),
#      ENGAGEMENT_OPEN_WEIGHT (0.25), ENGAGEMENT_SAVE_WEIGHT (0.5), ENGAGEMENT_CAP (8),
#      OUT_DIR ($HOME/.cache/myfeeder-phase12/replay)
# Output: one tab-separated file per candidate,
#   $OUT_DIR/replay-pp<PP>-hi<HIGH>-ne<NEUTRAL>-op<OPEN>-sv<SAVE>-cap<CAP>.tsv
#   written to <file>.tmp and renamed only after psql succeeds, so a failed run leaves no file.
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "usage: $0 PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP] [PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP] ...]" >&2
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
  if [[ ! "$candidate" =~ ^[0-9]+:[0-9]+:[0-9]+(:[0-9]+(\.[0-9]+)?:[0-9]+(\.[0-9]+)?:[0-9]+(\.[0-9]+)?)?$ ]]; then
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

# Refuses the engagement constants the app refuses to start with (Phase 9 WR-02). Source of truth:
# MyfeederProperties.Interest.Blend.Engagement.isValid -- keep the two in step; InterestCalibrationReplaySqlTest
# compares them on a boundary grid. Runs after the numeric regexes, because awk compares non-numbers as strings.
engagement_ok() {
  awk -v o="$1" -v s="$2" -v c="$3" -v l="$LEARNED_CAP" \
    'BEGIN { exit !(c == 0 || (o >= 0 && o < s && s < 1 && c > 0 && c < l)) }'
}
for candidate in "$@"; do
  IFS=: read -r pp high neutral open save cap <<<"$candidate"
  if ! engagement_ok "${open:-$ENGAGEMENT_OPEN_WEIGHT}" "${save:-$ENGAGEMENT_SAVE_WEIGHT}" "${cap:-$ENGAGEMENT_CAP}"; then
    echo "invalid engagement constants: $candidate (cap 0, or 0 <= open < save < 1 and 0 < cap < learned-cap)" >&2
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

OUT_DIR="${OUT_DIR:-$HOME/.cache/myfeeder-phase12/replay}"
umask 077
mkdir -p "$OUT_DIR"

SQL_FILE="$(dirname "$0")/interest-calibration-replay.sql"

for candidate in "$@"; do
  IFS=: read -r pp high neutral open save cap <<<"$candidate"
  open="${open:-$ENGAGEMENT_OPEN_WEIGHT}"
  save="${save:-$ENGAGEMENT_SAVE_WEIGHT}"
  cap="${cap:-$ENGAGEMENT_CAP}"
  out="$OUT_DIR/replay-pp${pp}-hi${high}-ne${neutral}-op${open}-sv${save}-cap${cap}.tsv"
  status=0
  psql -X -A -F $'\t' -P footer=off -v ON_ERROR_STOP=1 \
    -v profilePoints="$pp" -v learnRate="$LEARN_RATE" -v learnedCap="$LEARNED_CAP" \
    -v engagementOpenWeight="$open" -v engagementSaveWeight="$save" \
    -v engagementCap="$cap" \
    -v tierHigh="$high" -v tierNeutral="$neutral" -v windowDays="$WINDOW_DAYS" \
    -f "$SQL_FILE" > "$out.tmp" || status=$?
  if [[ $status -ne 0 ]]; then
    rm -f "$out.tmp"
    exit "$status"
  fi
  mv "$out.tmp" "$out"
  echo "replayed pp=$pp high=$high neutral=$neutral open=$open save=$save cap=$cap -> $out"
done
