# Phase 12: Calibration & Release - Pattern Map

**Mapped:** 2026-10-02
**Files analyzed:** 13
**Analogs found:** 13 / 13 (every file extends itself or copies a Phase 7/8 artifact)

All analog paths below are git-tracked (`git ls-files` checked for `scripts/*`, `application-dev.yaml`).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `scripts/interest-calibration-replay.sh` (modify) | utility (CLI driver) | batch | itself, lines 17-77 | exact |
| `scripts/interest-calibration-replay.sql` (modify: 7 new sections + `eng_articles`) | utility (read-only SQL) | batch / transform | itself: `summary` (34-54), `top` (56-60), `votes` (91), `learned` (94) | exact |
| `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java` (modify) | test (drift guard + ProcessBuilder) | file-I/O | itself | exact |
| `src/test/java/.../repository/InterestCalibrationReplayRunTest.java` (new, optional) | test (Testcontainers + host psql) | batch | `InterestScoreQueriesZeroEngagementTest` / `InterestScoreQueriesEngagementTest` (DB fixtures) + `runDriver` (lines 398-412) | role-match |
| `src/main/resources/application.yaml` (modify, only if tuned; fix stale 07 path + "Phase 12" comments) | config | n/a | itself lines 41-58 | exact |
| `src/test/resources/application-dev.yaml` (modify, only if tuned) | config | n/a | itself lines 22-28 (`tiers.neutral: 22` D-14 block) | exact |
| `src/test/resources/application.yaml` | config | n/a | NOT changed (keep 0.25/0.5/8, Pitfall 4) | n/a |
| `src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java` (modify `shippedMainYamlStarts`) | test | n/a | itself lines 47-61 | exact |
| `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java` (comments only, lines ~91, ~126) | config | n/a | itself | exact |
| `.planning/phases/12-calibration-release/12-CALIBRATION.md` (new) | doc | n/a | `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md` | exact |
| `.planning/phases/12-calibration-release/12-RELEASE.md` or "Shipped in 0.3.1" section | doc | n/a | `.planning/phases/08-engagement-capture/08-RELEASE.md` + `08-05-PLAN.md` | exact |
| `CLAUDE.md` (lines ~188, ~219 + tuning levers) | doc | n/a | itself § Tier thresholds and tuning | exact |
| `.planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md` + `.planning/STATE.md` (WR-02 -> fixed) | doc | n/a | existing disposition rows | exact |

## Pattern Assignments

### `scripts/interest-calibration-replay.sh` (utility, batch)

**Analog:** itself. Keep the order: shape regex -> numeric regex -> integer regex -> (NEW) awk range -> password -> connect.

Existing validate-before-connect (lines 29-52):
```bash
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
...
if [[ -z "${MYFEEDER_PG_PASSWORD:-}" ]]; then
  echo "MYFEEDER_PG_PASSWORD is required" >&2
  exit 2
fi
```
Changes:
- Candidate regex becomes `^[0-9]+:[0-9]+:[0-9]+(:[0-9]+(\.[0-9]+)?:[0-9]+(\.[0-9]+)?:[0-9]+(\.[0-9]+)?)?$` (RESEARCH Pattern 1). Keep the `invalid candidate: <x>` text: `driverRejectsANonNumericCandidate` / `driverRejectsANonNumericEngagementValue` assert it.
- Add `engagement_ok` awk function (RESEARCH Code Examples, mirrors `MyfeederProperties.java:107-110`) with fixed stderr text `invalid engagement constants: ...`, placed after line 47, before line 49.
- Run loop (lines 67-77): `IFS=: read -r pp high neutral open save cap`, default each from env, pass `-v engagementOpenWeight="$open"` etc. (keep the exact `-v <name>=` strings; `driverPassesEveryBlendParameter` greps for them). Output name `replay-pp${pp}-hi${high}-ne${neutral}-op${open}-sv${save}-cap${cap}.tsv`. Optionally write `$out.tmp` then `mv`.
- Update header usage (lines 2-14) and default `OUT_DIR` comment if changed to phase12.

### `scripts/interest-calibration-replay.sql` (utility, read-only batch)

**Analog:** itself.
- Header section list (lines 25-31): add a line per new section (`dormant`, `dormant-kind`, `floor`, `engaged`, `backfill-pool`, `backfill-summary`, `backfill-learned`). Never write `topic_suggestion_dismissal`, even in comments (V7EngagementMigrationTest).
- Count-only section analog = `votes` (line 91), one statement, no `WITH learned AS`:
```sql
SELECT 'votes' AS section, count(*) AS votes, count(*) FILTER (WHERE vote = 1) AS up, count(*) FILTER (WHERE vote = -1) AS down FROM article_feedback;
```
  Use the drafted `dormant`, `dormant-kind`, `floor`, `backfill-pool` SQL from 12-RESEARCH.md Code Examples verbatim.
- Blend + badge section analog = `top` (line 56-60): comment line, then the verbatim blend line, then a line carrying `, <INTEREST_SCORE> AS interest_score` exactly once. `engaged` uses `blendCte("a.id IN (SELECT g.article_id FROM article_engagement g)")`.
- Histogram analog = `summary` (lines 34-54): `, scored AS (...)` line then `SELECT '<label>' AS section ... FROM scored;` -- copy the full percentile column set into `backfill-summary`.
- `learned` (line 94) analog for `backfill-learned`: single line `LEARNED_CTE(with engaged swap) SELECT 'backfill-learned' AS section, e.id AS topic_id, ...`. Select `e.id`, never `e.name`. Append `eng_articles` as a trailing column to both.
- Generate blend lines from `InterestScoreQueries.blendCte(...)` / `LEARNED_CTE`, never hand-type.
- Labels/aliases must not match `(?i)\b(insert|update|delete|create|drop|alter|truncate|grant|copy|into)\b`; no line starting with `\`.

### `InterestCalibrationReplaySqlTest.java` (test)

**Analog:** itself.

Constants block (lines 35-40) -- add `ENGAGED_SCOPE`, `ORIGINAL_ENGAGED`, `BACKFILL_ENGAGED`, `BACKFILL`, `BACKFILL_LEARNED_SECTION` in this style:
```java
private static final String WINDOW_SCOPE =
        "COALESCE(a.published_at, a.fetched_at) > now() - :windowDays * interval '1 day'";
private static final String LEARNED_SECTION = InterestScoreQueries.LEARNED_CTE + " SELECT 'learned' AS section";
```
Core guard to edit (lines 293-334): hard-coded `isEqualTo(5)` (blend starts) -> 8; label list `containsExactly("unread","unread","unread","window","learned")` -> append `"engaged","backfill","backfill-learned"` (check `BACKFILL_LEARNED_SECTION` before `LEARNED_SECTION`); `INTEREST_SCORE` occurrences 4 -> 6; badge loop condition (`!equals(unread) && !equals(window)`) also accepts `blendCte(ENGAGED_SCOPE)` and `BACKFILL`; `badgeLines` 4 -> 6. Prefer named constants.
Copy list (lines 147-150) -- add `new Copy("engaged", blendCte(ENGAGED_SCOPE), 1)`, `new Copy("backfill", BACKFILL, 1)`, `new Copy("backfill-learned", BACKFILL_LEARNED_SECTION, 1)`. Also update badge-copy count (lines 167-168) and decoy counts (lines 241-243).
New tests: `backfillDiffersFromTheAppBlendOnlyInTheEngagedCte` (assertions in RESEARCH Pattern 3 item 5).

Driver test analog (lines 114-126) for WR-02:
```java
DriverRun run = runDriver(Map.of("MYFEEDER_PG_PASSWORD", "x", c.getKey(), c.getValue()), "100:70:40");
assertThat(run.exitCode()).as(c.getKey()).isEqualTo(2);
assertThat(run.stderr()).as(c.getKey()).contains("invalid candidate: " + c.getValue());
```
WR-02 tests must assert stderr contains `invalid engagement constants` AND `doesNotContain("psql:")` (exit code alone is ambiguous, Pitfall 2). Accept-path tests (cap 0 with open >= save; 6-field form) assert stderr contains `psql:` and pass `OUT_DIR` = JUnit `@TempDir`. `runDriver` (lines 398-412) already points at dead port `127.0.0.1:1`; reuse unchanged.

### `InterestCalibrationReplayRunTest.java` (optional, new)

**Analog:** Testcontainers repository tests (`@DataJdbcTest` + `@Import(TestcontainersConfiguration.class)`, e.g. `InterestScoreQueriesEngagementTest`) for fixtures, plus `runDriver` for invoking the script with `PGHOST/PGPORT` from the container. `assumeTrue` psql on PATH. Compare `engaged` badges to `InterestScoreQueries.displayScores`.

### `src/main/resources/application.yaml` (config)

**Analog:** itself lines 41-58 (shown above in research). If tuned: change the three `engagement:` literals, and fix the comment on line 42 to `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md`, replace "Phase 12 calibrates" / "Phase 12 tunes it" with the outcome + `12-CALIBRATION.md` pointer.

### `src/test/resources/application-dev.yaml` (config)

**Analog:** itself lines 22-28 -- the existing D-14 pattern for main-only tuned values:
```yaml
  interest:
    sweep-initial-delay: PT1M
    # D-14: main's tuned blend values (07-CALIBRATION.md); the test yaml keeps the pre-calibration constants for the suite.
    blend:
      tiers:
        neutral: 22
```
Add a sibling `engagement:` block under `blend` with the tuned values only when they differ from 0.25/0.5/8. Test yaml stays untouched (FeedbackApiIntegrationTest / PriorityApiIntegrationTest depend on it).

### `MyfeederPropertiesValidationTest.java` (test)

**Analog:** itself, `shippedMainYamlStarts` lines 47-61:
```java
assertEngagement(ctx.getBean(MyfeederProperties.class), 0.25, 0.5, 8.0);
```
Change these literals to the tuned main values; leave `defaultsStartAndBindTheD01Values` alone.

### `12-CALIBRATION.md` (doc)

**Analog:** `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md`. Headings to mirror: `## Run`, `## Baseline`, `## Candidates`, `## Stability cross-check`, `## Learned model`, `## Proposal`, `## Verdict`, `## Approval`, `## Shipped in 0.3.1`. Drop the `Top 20` / `Bottom 20` title tables (12 note: ids, `topic_<id>`, numbers only). Add sections for Data floor (D-02/D-03), Dormant / ENG-F4 (D-09), Backfill sim / ENG-F5 (D-10, record comparison base per Pitfall 11).

### Release (`08-05-PLAN.md`, `08-RELEASE.md`)

**Analog:** `.planning/phases/08-engagement-capture/08-05-PLAN.md` (preflight -> blocking approval checkpoint -> publish -> verify) and `08-RELEASE.md` (`## Release 0.3.0`, `## Follow-up`). Drop the V7/pg_dump steps; add `git status --porcelain` empty check (two untracked `docs/superpowers/plans/...` files), `VERSION == 0.3.1` on main, `helm get values ... | jq -c '{app}'` (never print secrets), and the D-13 cross-check script from RESEARCH.

### `CLAUDE.md` (doc)

**Analog:** itself § Interest Ranking → Engagement learning (constants bullet, ~line 188) and § Tier thresholds and tuning (~line 219). Fix "identical literals in main and test yaml, with the dev overlay untouched" if tuned; fix stale 07 path; add 6-field syntax, new sections, D-02 floor, D-04/D-09/D-10 rules, outcome + pointer to `12-CALIBRATION.md`.

## Shared Patterns

### Read-only + verbatim
**Source:** `scripts/interest-calibration-replay.sh:54` (`PGOPTIONS='-c default_transaction_read_only=on'`) and the guard `InterestCalibrationReplaySqlTest.assertEveryCopyIsVerbatim` (lines 293-334). Apply to every new SQL section.

### Validate before connect, fixed stderr text, exit 2
**Source:** driver lines 29-52. Apply to the 6-field syntax and the WR-02 range check.

### Privacy
Ids and `topic_<id>` only in committed files; raw TSV/JSON under `$HOME/.cache/myfeeder-phase12/`; jq-project breakdown rows to `{topicId, engagementWeight}` (InterestBreakdown.Row carries `name`).

### Yaml placement (D-14)
Main yaml + dev overlay; test yaml frozen; `DevProfileConfigTest` enforces.

### Close tracked items in the same commit
WR-02 -> `fixed` in `09-REVIEW-DISPOSITION.md` and STATE.md blocker line, same commit as the driver fix.

## No Analog Found

None. The optional Testcontainers+psql run test has only partial analogs (DB fixture tests + `runDriver`); fall back to RESEARCH Validation Architecture.

## Metadata

**Analog search scope:** `scripts/`, `src/test/java/.../repository`, `src/test/java/.../config`, `src/main/resources`, `src/test/resources`, `.planning/milestones/v0.2.1-phases/07-*`, `.planning/phases/08-*`
**Files scanned:** 9
**Pattern extraction date:** 2026-10-02
