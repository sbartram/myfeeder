---
phase: 12-calibration-release
verified: 2026-10-02T23:35:00Z
status: passed
score: 10/12 must-haves verified
covered_files:
  - .planning/phases/12-calibration-release/12-01-PLAN.md
  - .planning/phases/12-calibration-release/12-01-SUMMARY.md
  - .planning/phases/12-calibration-release/12-02-PLAN.md
  - .planning/phases/12-calibration-release/12-02-SUMMARY.md
  - .planning/phases/12-calibration-release/12-03-PLAN.md
  - .planning/phases/12-calibration-release/12-03-SUMMARY.md
  - .planning/phases/12-calibration-release/12-04-PLAN.md
  - .planning/phases/12-calibration-release/12-04-SUMMARY.md
  - .planning/phases/12-calibration-release/12-05-PLAN.md
  - .planning/phases/12-calibration-release/12-05-SUMMARY.md
  - .planning/phases/12-calibration-release/12-CALIBRATION.md
  - CLAUDE.md
  - scripts/interest-calibration-replay.sh
  - scripts/interest-calibration-replay.sql
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/MyfeederPropertiesValidationTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplayRunTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
  - src/test/resources/application.yaml

covered_digest: "v2:sha256:ff6d65cd34aa900e820b6f3767fc0102a6d0245c0f7439b5dcdd84c5d81cd045"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "SC-3 second clause: once at least one engaged, SCORED, unvoted article matches a non-negative topic above noul 0.5, re-run $HOME/.cache/myfeeder-phase12/sc3.sh (read-only replay at 0.25/0.5/8 plus GET /api/articles/{id} for 3 to 5 engaged ids)"
    expected: "Replay badge equals the API interestScore on every id, and at least one id's interestBreakdown has a TOPIC row with engagementWeight > 0, which the 'Why N?' popover renders as '+x.x engaged'"
    why_human: "Cannot be proven today. Prod /api/interest/topics/learned reports engagementLearned 0.0 on all 10 topics, and the 5 D-13 ids carry OPEN_ORIGINAL engagement but only a PROFILE breakdown row (best topic noul at most 0.10). Waiting for data is the only way to observe it. Otherwise, accept the deviation with the override below"
  - test: "Backstop truth (12-05): about a day after the 2026-10-02T22:43Z deploy, run the read-only replay at 100:70:22:0.25:0.5:8 and 100:70:22:0.25:0.5:0 against prod and compare summary high_pct"
    expected: "|high_pct(cap 8) - high_pct(cap 0)| <= 5.0"
    why_human: "verification: backstop (non-inferable); needs a prod replay at a later time with MYFEEDER_PG_PASSWORD; presence of code cannot prove it"
  - test: "Confirm the judgment-tier prohibitions (12-02 to 12-05) hold: no write to prod Postgres, no backfill row written, no tier or near-miss tuning, no Helm/env blend override, no force-push or tag move, no secret printed or committed"
    expected: "All hold. The verifier's non-authoritative LLM-judge verdict is that each holds (see Prohibitions below)"
    why_human: "Judgment-tier prohibitions need explicit human resolution in interactive verify (ADR-550 D4). Secret non-disclosure in session logs is not observable from the repo"
---

# Phase 12: Calibration & Release Verification Report

**Phase Goal:** The engagement weights and cap are tuned from real production engagement by the read-only replay, and the calibrated milestone is released to production and documented
**Verified:** 2026-10-02T23:35:00Z
**Status:** human_needed
**Re-verification:** No (initial verification)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC-1: a read-only replay against prod reports dormant engagement, topics at the cap, the tier histogram with engagement on and off, and a simulated backfill | ✓ VERIFIED | `$HOME/.cache/myfeeder-phase12/replay/20261002T213738Z/` (mode-700 parent) holds the 7 D-06 grid TSVs. The cap-8 TSV has the summary, window-summary, votes, learned (10, with `eng_at_cap` and `eng_articles`), engaged (15), dormant, dormant-kind, floor, backfill-pool, backfill-summary and backfill-learned (10) sections. Its rows (`floor 13 0 f`, `dormant 16 15 13 2 1 …`, `backfill-pool 5 0 5 0`, `summary … 24.4 57.7 17.9`, and the same summary at cap 0) equal 12-CALIBRATION.md. The driver sets `PGOPTIONS=-c default_transaction_read_only=on`, and the SQL has no write keyword and no `topic_suggestion_dismissal` |
| 2 | SC-2: constants committed in yaml (main plus the dev overlay, no Helm/env), and a calibration note with results, reasoning and the ENG-F4/ENG-F5 calls | ✓ VERIFIED | Main yaml (and `v0.3.1:src/main/resources/application.yaml`) has open-weight 0.25, save-weight 0.5 and cap 8, equal to `approved-constants: open-weight=0.25 save-weight=0.5 cap=8`. The dev overlay needs no engagement keys, because the test yaml literals are identical; `DevProfileConfigTest` 4/4 green proves main resolves. `helm/` and `deploy.sh` have no blend keys, and nothing in them changed since v0.3.0. The prod deployment env has no `MYFEEDER_INTEREST_*`. The note covers Run, Data floor, Baseline, Candidates, Learned model, Dormant (ENG-F4 keep), Backfill (ENG-F5 recommend), Proposal, Verdict, Approval and the four machine lines. Caveat: the values were kept by the user's D-03 fallback on thin data and are marked `constants: revisit`. The D-04 rule on the prod data reaches the same keep independently |
| 3 | SC-3a: the calibrated release is deployed to prod with a clean startup | ✓ VERIFIED | Live checks: `/api/version` → 0.3.1 (build 22:42:15Z). `/api/interest/status` → configured true, breaker CLOSED, coldStart false, tiers 70/22. `kubectl` shows image `:0.3.1`, 1 `Started MyfeederApplication`, 0 ` ERROR ` lines in the log tail, and no `Jev circuit breaker` or `TypeSafe Jev not configured` line. Tag v0.3.1 → c3a25fe on origin; v0.3.0 (db5acd7) and v0.2.1 (5461d0a) unchanged |
| 4 | SC-3b: engaged articles in prod show engagement in their badges and "Why N?" | ? UNCERTAIN (human) | Not observable today. `/api/interest/topics/learned` reports `engagementLearned` 0.0 and `engagementAtCap` false on all 10 topics. D-13 ids 26605/26771/26595/26427/26819 carry `OPEN_ORIGINAL` engagement, but each breakdown has only a PROFILE row (max topic noul 0.01 to 0.10, below the 0.5 hinge). Replay equals API on 5/5, but with every engagement part at 0 that parity cannot tell cap 8 from cap 0. The mechanism is proven in tests: `PriorityApiIntegrationTest.engagedArticleAgreesEverywhereAfterRefresh` passed in this run. The user recorded "not provable yet". Phase 12 is the last phase, so nothing later can take this over |
| 5 | SC-4: CLAUDE.md documents the V7 schema, capture rules, extended learned model (4 rules) and tuning levers | ✓ VERIFIED | CLAUDE.md (commit 7cedef2) contains `V7__engagement.sql`, "Engagement capture (v0.3.0)" with the `recordQuietly` capture rules, "Four rules: thumbs first … separate additive cap … negative-base topics skipped … SCORED articles only", and "Engagement tuning (v0.3.1, Phase 12)" with `PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP]`, `invalid engagement constants`, every section, the D-02 floor, D-04 nudge, D-09 (ENG-F4), D-10 (ENG-F5), the yaml placement ("never in Helm"), the outcome and a pointer to 12-CALIBRATION.md |
| 6 | Replay is verbatim with the app: engaged badges equal `displayScores` at cap 8 and cap 0; the drift guard has 8 blend statements and 6 badge copies; InterestScoreQueries.java unchanged | ✓ VERIFIED | Ran `InterestCalibrationReplaySqlTest` 24/24 and `InterestCalibrationReplayRunTest` 6/6 (0 skipped, so host psql was present). `git hash-object InterestScoreQueries.java` = 807baf80…, as pinned |
| 7 | WR-02 closed: the driver refuses engagement constants that `isValid` refuses, before connecting | ✓ VERIFIED | `engagement_ok` awk runs before the password check (script lines 55-65). `driverRefusesWhatTheAppRefuses`, `driverRejectsOutOfRangeEngagementBeforeConnecting`, `driverValidatesEveryCandidateBeforeAnyConnection` and `aFailedRunLeavesNoFile` passed; the tests use PGHOST 127.0.0.1, PGPORT 1 |
| 8 | Backfill differs from the app blend only in the engaged CTE; dormant, floor and pool counts and the floor boundary (29/3 f, 30/3 t, 30/2 f) are correct | ✓ VERIFIED | `backfillDiffersFromTheAppBlendOnlyInTheEngagedCte`, `aBackfillBypassFails`, `backfillCountsStarsAndBoardsAsSavesUnderTheLiveRules`, `dormantAndKindCountsMatchTheFixture`, `floorIsMetExactlyAtThirtyCountedAndThreeTopics`, `backfillPoolCountsMatchTheFixture` and `engArticlesCountsUnvotedScoredMatches` all passed in this run |
| 9 | Replay proven faithful in prod (cap 0 replay = 0.3.0 API) before any decision; gate recorded | ✓ VERIFIED | The note records 5/5 plus 5 unsaturated ids matching. `gate-history.txt` ends `gate: unmet-fallback`, `answer (2026-10-02): fallback`, which matches the note's Approval section |
| 10 | Startup test binds the shipped values; test yaml keeps D-01 0.25/0.5/8; tiers 70/22 (main) and 70/40 (test), near-miss 0.35 unchanged | ✓ VERIFIED | `MyfeederPropertiesValidationTest` 16/16 (`shippedMainYamlStarts` asserts 0.25/0.5/8.0 and near-miss 0.35 from main yaml on disk). yaml read directly. The `MyfeederProperties.java` diff is only 2 Javadoc lines |
| 11 | Note privacy: no titles, topic names or keys | ✓ VERIFIED | In-memory check against prod: 0 of 10 topic names (whole word, case-insensitive) and 0 of 10 cross-check article titles occur in 12-CALIBRATION.md |
| 12 | Backstop (12-05): about a day after deploy, the shipped constants keep high_pct within about 5 points of cap 0 | ? insufficient_spec (human) | `verification: backstop`; needs a later prod replay. With 0 engaged topics today it would trivially hold, but it has not been observed |

**Score:** 10/12 truths verified (0 present-but-behavior-unverified; 2 routed to human)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `scripts/interest-calibration-replay.sh` | 6-field candidates, `engagement_ok`, per-candidate names, tmp-then-rename | ✓ VERIFIED | Contains `invalid engagement constants`, `-v engagementOpenWeight=`, `.tmp`→`mv` |
| `scripts/interest-calibration-replay.sql` | engaged, backfill-*, dormant*, floor, backfill-pool, eng_articles | ✓ VERIFIED | 13 labelled sections, each label once |
| `InterestCalibrationReplaySqlTest.java` | drift guard, WR-02 grid, bypass and privacy tests | ✓ VERIFIED | `BLEND_STATEMENTS = 8`, `BADGE_COPIES = 6`; 24 tests green |
| `InterestCalibrationReplayRunTest.java` | real-Postgres replay proofs | ✓ VERIFIED | 6 tests green, uses `displayScores(` |
| `12-CALIBRATION.md` | note with the approved-constants line and Shipped in 0.3.1 | ✓ VERIFIED | One well-formed `approved-constants:` line; `## Shipped in 0.3.1` present |
| `src/main/resources/application.yaml` | approved constants and outcome comments | ✓ VERIFIED | References 12-CALIBRATION.md |
| `MyfeederPropertiesValidationTest.java` | `shippedMainYamlStarts` pinned to the approved values | ✓ VERIFIED | |
| `CLAUDE.md` | SC-4 docs | ✓ VERIFIED | |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| replay.sh | replay.sql | `psql -v` with the candidate's engagement values | ✓ WIRED | `-v engagementOpenWeight="$open"` etc.; `driverPassesEveryBlendParameter` green |
| ReplayRunTest | InterestScoreQueries | engaged badges vs `displayScores` | ✓ WIRED | test passed |
| ReplaySqlTest | InterestScoreQueries | `blendCte(ENGAGED_SCOPE)`, `replace(ORIGINAL_ENGAGED, BACKFILL_ENGAGED)` | ✓ WIRED | tests passed |
| 12-CALIBRATION.md | main yaml | approved-constants copied as literals | ✓ WIRED | 0.25 / 0.5 / 8 in both |
| main yaml | application-dev.yaml | DevProfileConfigTest resolution | ✓ WIRED | 4/4 green; no differing value, so no keys |
| main (merge c3a25fe) | tag v0.3.1 on origin | `./gradlew release` | ✓ WIRED | `git ls-remote` shows `refs/tags/v0.3.1^{}` = c3a25fe |
| image :0.3.1 | deploy/myfeeder | `deploy.sh 0.3.1` | ✓ WIRED | kubectl image `registry.bartram.org/bartram/myfeeder:0.3.1`; `/api/version` 0.3.1 |
| replay at shipped constants | GET /api/articles/{id} | D-13 engaged-badge parity | ⚠️ PARTIAL | 5/5 parity, but the engagement-part clause cannot be met (truth 4) |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Produces Real Data | Status |
|----------|------|--------|--------------------|--------|
| Prod "Why N?" / badge engagement part | `engagementWeight` on TOPIC rows | `InterestScoreQueries` LEARNED_CTE over prod `article_engagement` | Query is real and runs, but yields 0 for every topic because no engaged article clears a hinge | ⚠️ STATIC by data (not code): flows, value 0 |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Replay tooling, drift guard and run proofs | `./gradlew test --tests InterestCalibrationReplaySqlTest --tests InterestCalibrationReplayRunTest …` | 24/0/0 and 6/0/0 (tests/skipped/failures) | ✓ PASS |
| Yaml split and startup binding | same invocation: `DevProfileConfigTest`, `MyfeederPropertiesValidationTest` | 4/0/0 and 16/0/0 | ✓ PASS |
| Engagement visible end to end (test DB) | `PriorityApiIntegrationTest.engagedArticleAgreesEverywhereAfterRefresh` | 1/0/0 | ✓ PASS |
| Prod version | `curl /api/version` | `0.3.1` | ✓ PASS |
| Prod interest status | `curl /api/interest/status` | configured, CLOSED, 70/22 | ✓ PASS |
| Prod engagement learned | `curl /api/interest/topics/learned` | engagementLearned 0.0 on 10/10 | ? (truth 4) |

The full backend suite was not re-run (the orchestrator reports 723 / 0 failures / 2 skipped; frontend 413/413).

### Probe Execution

None declared. Step 7c is not applicable.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CAL-02 | 12-01, 12-02, 12-03 | Replay reports dormant engagement, topics at the cap, histogram on/off, simulated backfill | ✓ SATISFIED | Truths 1, 6-9 |
| CAL-03 | 12-03, 12-04, 12-05 | Weights and cap tuned from prod data, committed in yaml, documented in CLAUDE.md | ✓ SATISFIED (with caveat) | Truths 2, 5, 10. Prod-data run decided keep by D-03 fallback and D-04; marked `constants: revisit` |

No orphaned requirements: REQUIREMENTS.md maps only CAL-02 and CAL-03 to Phase 12. Bookkeeping: REQUIREMENTS.md still shows both as `[ ]` / `Pending` (lines 44-45, 99-100). Phase completion should mark them.

### Prohibitions

| Plan | Prohibition | Tier | Disposition |
|------|-------------|------|-------------|
| 12-01 | No prod DB from tests | test | ✓ enforced: PGHOST 127.0.0.1 / PGPORT 1 and Testcontainers |
| 12-01 | No writes, defined objects or dismissal-table reads in the replay | test | ✓ `replayIsReadOnly`, `rankingSqlReadsEngagementButNotDismissals`; grep confirms |
| 12-01 | No change to InterestScoreQueries.java | test | ✓ hash 807baf80… unchanged |
| 12-02 | No backfill writes and no dormant eligibility | judgment | LLM-judge: holds (SELECT-only SQL, no app change); human review recommended |
| 12-02 | No names or titles in new sections | test | ✓ `newSectionsPrintNoNamesOrTitles` |
| 12-02 | Backfill diverges only in the engaged CTE | test | ✓ `backfillDiffersFromTheAppBlendOnlyInTheEngagedCte`, `aBackfillBypassFails` |
| 12-03 | No proposal while floor unmet before the fallback date | judgment | LLM-judge: holds; the fallback date was amended by the user (fe54be9) before the fallback answer |
| 12-03 | No writes to prod Postgres | judgment | LLM-judge: holds (read-only PGOPTIONS); human review recommended |
| 12-03 | No titles, topic names or keys committed | test | ✓ verifier's independent in-memory check: 0 hits |
| 12-03 | No tier or near-miss tuning; no ENG-F4 build or backfill run | judgment | LLM-judge: holds (70/22, 0.35 unchanged) |
| 12-04 | Test yaml literals, Java defaults, startup rule, tiers and near-miss unchanged | test | ✓ yaml read; `defaultsStartAndBindTheD01Values` green; Java diff is Javadoc only |
| 12-04 | No Helm, deploy.sh or env blend overrides | judgment | LLM-judge: holds (no diff since v0.3.0; no `MYFEEDER_INTEREST_*` env in prod) |
| 12-04 | No OpenWiki or .claude/CLAUDE.md edits | judgment | LLM-judge: holds (no diff in range 38c3a8e..c3a25fe) |
| 12-05 | No publish before approval; no force-push, tag move or disableChecks | judgment | LLM-judge: holds (earlier tags unchanged; approval recorded) |
| 12-05 | No untracked-file handling without the user's choice | judgment | LLM-judge: holds (choice recorded) |
| 12-05 | No secrets printed or committed | (none) | LLM-judge: the repo shows no key; session logs not observable. Human review recommended |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (phase files) | - | TBD/FIXME/XXX/TODO/HACK | none | No debt markers in any phase-modified file |
| `src/test/resources/application.yaml` | 46 | Comment says "application-dev.yaml carries main's calibrated values"; it carries no engagement keys | ℹ️ Info | Misleading comment (already 12-REVIEW IN-06) |
| 12-REVIEW-DISPOSITION.md | - | 3 warnings and 8 info findings, all `open` | ⚠️ Warning (advisory) | WR-01 (driver tests inherit operator `ENGAGEMENT_*` env), WR-02 (tier fields not range-checked), WR-03 (an interrupted run can leave a `.tmp` with prod titles in the home cache). None blocks the goal |

### Human Verification Required

#### 1. Engaged articles show engagement in badges and "Why N?" (SC-3, second clause)

**Test:** Once an engaged, SCORED, unvoted article matches a non-negative topic above noul 0.5 (for example after creating a topic from a Suggested topic), re-run `$HOME/.cache/myfeeder-phase12/sc3.sh`.
**Expected:** Replay equals API on every id, and at least one TOPIC breakdown row has `engagementWeight > 0`, shown as "+x.x engaged" in "Why N?".
**Why human:** Prod data today makes it unobservable: all 10 topics have `engagementLearned` 0.0. The code path is proven on a test database (`PriorityApiIntegrationTest.engagedArticleAgreesEverywhereAfterRefresh`) and the prod blend equals the replay.

**If you accept this as met by the mechanism proof**, add to this file's frontmatter:

```yaml
overrides:
  - must_have: "The calibrated release is deployed to production with a clean startup, and engaged articles in prod show engagement in their badges and Why N?"
    reason: "Unreachable with current prod data (D-03 fallback: 0 topics with engagement). The shipped blend equals the replay on 5/5 engaged ids, and the engagement display path is proven by PriorityApiIntegrationTest.engagedArticleAgreesEverywhereAfterRefresh. Re-check with sc3.sh when the constants are revisited"
    accepted_by: "<name>"
    accepted_at: "<ISO timestamp>"
```

#### 2. Day-after nudge check (12-05 backstop)

**Test:** Around 2026-10-03T23:00Z, run `scripts/interest-calibration-replay.sh 100:70:22:0.25:0.5:8 100:70:22:0.25:0.5:0` and compare `summary` high_pct.
**Expected:** Difference <= 5.0 points.
**Why human:** Needs a later prod read with the DB password.

#### 3. Judgment-tier prohibitions

**Test:** Review the Prohibitions table above.
**Expected:** Each holds. The verifier's verdicts are non-authoritative.
**Why human:** ADR-550 D4: judgment items need explicit human resolution in interactive verify.

### Gaps Summary

No code, artifact or wiring gaps. The replay tooling is complete and proven against a real database. The prod replay ran read-only and its evidence on disk matches the note. The constants are committed in yaml and pinned by the startup test. CLAUDE.md covers every SC-4 item. v0.3.1 runs in prod with a clean startup (checked live).

One roadmap outcome stays unproven, and it is a data limit rather than a defect: no engaged article in prod matches any topic, so no badge or "Why N?" can show an engagement part yet (SC-3b). Relatedly, CAL-03's "tuned" means "decided from prod data by the agreed rules". The rules kept the D-01 defaults on thin data, marked `revisit`. The user should either accept SC-3b by override or keep it open until `sc3.sh` can pass. REQUIREMENTS.md traceability (CAL-02/CAL-03 still Pending) should be updated at phase completion.

---

_Verified: 2026-10-02T23:35:00Z_
_Verifier: Claude (gsd-verifier)_
