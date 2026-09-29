---
phase: 07-rollout-calibration
verified: 2026-09-29T00:58:00Z
status: gaps_found
score: 10/11 must-haves verified
covered_files:
  - .planning/phases/07-rollout-calibration/07-01-PLAN.md
  - .planning/phases/07-rollout-calibration/07-01-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-02-PLAN.md
  - .planning/phases/07-rollout-calibration/07-02-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-03-PLAN.md
  - .planning/phases/07-rollout-calibration/07-03-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-04-PLAN.md
  - .planning/phases/07-rollout-calibration/07-04-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-05-PLAN.md
  - .planning/phases/07-rollout-calibration/07-05-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-06-PLAN.md
  - .planning/phases/07-rollout-calibration/07-06-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-07-PLAN.md
  - .planning/phases/07-rollout-calibration/07-07-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-08-PLAN.md
  - .planning/phases/07-rollout-calibration/07-08-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-09-PLAN.md
  - .planning/phases/07-rollout-calibration/07-09-SUMMARY.md
  - .planning/phases/07-rollout-calibration/07-BACKFILL.md
  - .planning/phases/07-rollout-calibration/07-CALIBRATION.md
  - CLAUDE.md
  - scripts/interest-calibration-replay.sh
  - scripts/interest-calibration-replay.sql
  - src/main/frontend/src/App.tsx
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/components/InterestBadge.test.tsx
  - src/main/frontend/src/components/InterestBadge.tsx
  - src/main/frontend/src/hooks/useInterest.test.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/frontend/src/utils/interest.test.ts
  - src/main/frontend/src/utils/interest.ts
  - src/main/java/org/bartram/myfeeder/config/JevEventLogging.java
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
  - src/main/java/org/bartram/myfeeder/service/TierThresholds.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java
  - src/test/resources/application-dev.yaml
  - src/test/resources/application.yaml
covered_digest: "v2:sha256:5165697303069e6fb753f928833b95442f15477e8edc63a51654363c38b44262"
behavior_unverified: 0
overrides_applied: 0
gaps:
  - truth: "07-04: InterestCalibrationReplaySqlTest fails on any drift of a blend or badge copy in scripts/interest-calibration-replay.sql (the drift guard fails on a single-byte difference)"
    status: partial
    reason: "The replay SQL holds 3 copies of the unread blend CTE (summary, top, bottom) and 4 copies of the INTEREST_SCORE badge expression, but the test only asserts sql.contains(...) for each expected text, which proves at least ONE copy matches. A hand edit to the top or bottom copy, or to 3 of the 4 badge copies, leaves the test green. Today all copies ARE byte-identical (checked directly: the 3 unread 'WITH learned AS' lines are identical over all 1614 chars, and all 4 badge CASE expressions equal InterestScoreQueries.INTEREST_SCORE), and the prod cross-checks matched 5/5 twice, so the shipped calibration is sound; only the stated future-drift guarantee is not delivered. Same finding as 07-REVIEW WR-02 (disposition: open)."
    artifacts:
      - path: "src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java"
        issue: "replaysTheAppsUnreadBlendVerbatim / replaysTheWindowCrossCheckVerbatim use contains(); no count or every-copy assertion"
    missing:
      - "Assert every 'WITH learned AS' line in the SQL is one of the allowed verbatim blendCte(scope)/LEARNED_CTE texts and that the line count is 5"
      - "Assert INTEREST_SCORE occurs exactly 4 times in the SQL"
behavior_unverified_items: []
human_verification:
  - test: "Open http://192.168.44.204/priority, then Settings > Interests"
    expected: "Priority lists scored articles with badges (the cold-start call to action from the 07-06 check is superseded now that a rubric exists); the Interests dialog shows no 'not configured' notice; reader, feed tree and article list behave as before"
    why_human: "Visual confirmation of the prod UI; API checks cannot show rendering"
  - test: "In the prod Priority and article lists, look at badges scored 22-39 and 70+"
    expected: "22-39 render in the neutral colour, 70+ in the high colour, below 22 low (served tiers 70/22). A brief low-colour flash for 22-39 on first paint is the known 70/40 fallback (07-REVIEW IN-01)"
    why_human: "Tier colours are CSS rendering; the served tiers are verified by API but colour is visual"
  - test: "Confirm the judgment-tier prohibitions of 07-06..07-09 (approve-before-release gates, no prod writes by hand, no force-push/no-verify/disableChecks, no rubric text or key in committed files, no SDK retry / concurrency > 1)"
    expected: "Each holds; the verifier's non-authoritative LLM judgment found supporting evidence for all (see Prohibitions)"
    why_human: "Judgment-tier prohibitions require explicit human resolution (ADR-550 D4)"
---

# Phase 7: Rollout & Calibration Verification Report

**Phase Goal:** Interest ranking is live in production with a real key, the backlog is scored, and the scores are tuned so they mean something
**Verified:** 2026-09-29T00:58:00Z
**Status:** gaps_found (one plan-level must-have partial; all three roadmap success criteria verified against the codebase and live prod)
**Re-verification:** No, initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|---|---|---|
| 1 | SC1: released and deployed with a live key; the launch backfill drained the eligible backlog with no 429 storms and without opening the circuit (per `/api/interest/status` and logs) | ✓ VERIFIED | Live: `/api/version` = 0.2.1; `/api/interest/status` = configured true, coldStart false, CLOSED, eligibleUnscored 0, failed 0. Deployment env holds `MYFEEDER_TYPESAFE_API_KEY` (name only read). Read-only prod query: `article_score` SCORED 199, max attempts 1, model jev-1.13.0, 0 FAILED rows, 0 `TypeSafeRateLimit%` failures; Flyway latest V6 success. Raw watch evidence re-read (not the summary): `status.log` 53/53 samples `"breakerState":"CLOSED"`, `backfill.log` 0 `Jev ` lines, 0 ERROR lines; current pod log (3h) 0 `Jev retry/retries/circuit` lines. `JevEventLogging` is present in tag v0.2.0, so a zero count is meaningful. Tags v0.2.0 and v0.2.1 are on origin. |
| 2 | SC2: blend constants (profile weight, learn rate, cap, tier thresholds) are configurable and tuned against the real distribution, so badges spread across tiers | ✓ VERIFIED | `MyfeederProperties.Interest.Blend` binds profilePoints/learnRate/learnedCap/tiers.high/neutral; `InterestScoreQueries` params and `ArticleFeedbackService` read them; `InterestStatusService` serves tiers. main `application.yaml` has tiers 70/22 with a 07-CALIBRATION.md comment (also in `v0.2.1:application.yaml`). 07-CALIBRATION.md records 22 replayed candidates against 192 real scored articles and a verbatim `approve`. Independent live check: paging `/api/articles/priority` gives 187 scored unread badges at the served 70/22 -> high 15.5%, neutral 33.7%, low 50.8% (vs 15.5/20.3/64.2 at the old 70/40); inside the D-10 bands |
| 3 | SC3: CLAUDE.md documents Jev behaviors and gotchas: app-owned client bean, single retry layer, scoring executor, eligibility window | ✓ VERIFIED | `## Jev Scoring and Resilience` appears once in committed CLAUDE.md (HEAD and origin/main). Facts cross-checked against source: `TypeSafeConfig` (Supplier key, explicit baseUrl, `clone()` builder, `MAX_RETRY_AFTER_MS = 10_000L`, not-configured log text), yaml `max-retries: 0`, jev retry `max-attempts: 3` + retry-exceptions, `InterestScoringConfig` (`interestScoringExecutor`, `defaultCandidate = false`, `jev-score-`, queue capacity), `ArticleScoreStore.MAX_ATTEMPTS = 3`. Architecture Jev bullet, V6 in the Flyway list, deploy key optional and `incrementMinor` all present |
| 4 | 07-01: tiers bind from `myfeeder.interest.blend.tiers.*`, `tiers` is appended last on `InterestStatus`, and retuning is config-only | ✓ VERIFIED | `record InterestStatus(..., long failed, TierThresholds tiers)`; `getBlend().getTiers()` + `new TierThresholds(` in the service; InterestStatusServiceTest 7/7 incl. `tiersBindFromTheBlendTiersKeys` (run here); live prod serves `{70,22}` from yaml alone |
| 5 | 07-02: every InterestBadge classifies with served tiers, falls back to DEFAULT_TIERS 70/40, one non-polling root observer | ✓ VERIFIED | `useInterestTiers` (staleTime Infinity, `select: (s) => s.tiers`, `?? DEFAULT_TIERS`); `TierContext.Provider value={tiers}` in App.tsx; InterestBadge uses `useContext(TierContext)` + `tierOf(score, tiers)` (no query observer); tierOf has no numeric literals; used by ArticleList, PriorityList, BoardArticleList, ScoreRow. vitest useInterest/InterestBadge/interest: 19/19 pass (run here). Prod bundle `index-C8-FoLkO.js` contains `{high:70,neutral:40}` + createContext |
| 6 | 07-03: JevEventLogging logs retry / exhausted / breaker-transition lines with class names and numbers only | ✓ VERIFIED | Class subscribes to `retry("jev")` and `circuitBreaker("jev")`, uses `getStateTransition().name()`; JevResilienceTest 20/20 pass (run here) incl. `rateLimitRetryIsLoggedWithoutLeaking`, `exhaustedRetriesAreLogged`, `breakerTransitionsAreLogged`, `perArticleErrorsLogNoRetryLine` |
| 7 | 07-04: read-only replay driver reproduces the app blend verbatim, validates inputs, needs the password | ✓ VERIFIED | Driver exports `PGOPTIONS='-c default_transaction_read_only=on'`, rejects bad candidates with `invalid candidate:`; SQL has no DML/DDL keyword or backslash meta-command; `blendCte` is package-private with SQL unchanged. All current copies byte-identical to the app (checked directly). Prod cross-check 5/5 at 100/70/40 and 5/5 at 100/70/22 |
| 8 | 07-04: `InterestCalibrationReplaySqlTest` fails on any drift (single-byte difference) of a blend/badge copy | ✗ FAILED (partial) | Test passes (6/6), but it asserts `contains()` only. 3 unread blend copies and 4 badge copies exist; drift in the non-matching copies is not caught. See Gaps |
| 9 | 07-06: 0.2.0 released (merge, tag, image, pre-deploy dump, Flyway V6, key authenticated, clean soak) | ✓ VERIFIED | Tag v0.2.0 on origin -> 687217f; prod Flyway V6 success; dump file present in cache; 07-BACKFILL facts table consistent with raw `status.log` first sample (coldStart true, tiers 70/40, eligibleUnscored 183) |
| 10 | 07-08: calibration ran after the drain against the real distribution, with top/bottom 20 titles and an explicit user approval line | ✓ VERIFIED | 07-CALIBRATION.md: baseline and 22 candidates, stability cross-check, learned model (0 votes), top/bottom 20, `approved-constants: profile-points=100 tiers.high=70 tiers.neutral=22 learn-rate=2 learned-cap=20`; replay TSVs present in `$HOME/.cache/myfeeder-phase07/replay/` |
| 11 | 07-09: approved constants shipped via source control as 0.2.1; test yaml pinned at 100/70/40; no Helm/env override | ✓ VERIFIED | `49b9e54` is an ancestor of v0.2.1; test yaml tiers 70/40 with comment; dev overlay `tiers.neutral: 22`; `helm/` has no profile-points/tiers/INTEREST key; live deployment env has no `MYFEEDER_INTEREST_*` var; prod serves 0.2.1 with tiers 70/22 |

**Score:** 10/11 truths verified (0 present, behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `service/TierThresholds.java` | `record TierThresholds(int high, int neutral)` | ✓ VERIFIED | wired into InterestStatus/InterestStatusService |
| `service/InterestStatus.java` | tiers appended last | ✓ VERIFIED | |
| `config/MyfeederProperties.java` | `Blend.Tiers` defaults 70/40 | ✓ VERIFIED | |
| `config/JevEventLogging.java` | jev retry/breaker logging | ✓ VERIFIED | shipped in v0.2.0 and v0.2.1 |
| `scripts/interest-calibration-replay.{sh,sql}` | read-only replay | ✓ VERIFIED | used for 07-08/07-09 evidence |
| `InterestCalibrationReplaySqlTest.java` | drift guard | ⚠️ PARTIAL | guards one copy per text only (gap) |
| frontend `utils/interest.ts`, `hooks/useInterest.ts`, `api/interest.ts`, `InterestBadge.tsx`, `App.tsx` | served tiers path | ✓ VERIFIED | |
| `CLAUDE.md` | `## Jev Scoring and Resilience` | ✓ VERIFIED | |
| `07-BACKFILL.md` | `## Release 0.2.0`, `## Verdict` | ✓ VERIFIED | numbers match raw cache files |
| `07-CALIBRATION.md` | `approved-constants:`, `## Shipped in 0.2.1` | ✓ VERIFIED | |
| main `application.yaml` | tuned values + 07-CALIBRATION.md comment | ✓ VERIFIED | |

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| InterestStatusService | MyfeederProperties | `getBlend().getTiers()` | WIRED |
| application.yaml | prod `/api/interest/status` | 0.2.1 image binding | WIRED (live `{70,22}`) |
| useInterestTiers | `/api/interest/status` | `select: (s) => s.tiers` | WIRED |
| App.tsx MainLayout | InterestBadge | `TierContext.Provider value={tiers}` -> `useContext` | WIRED |
| JevEventLogging | jev RetryRegistry / CircuitBreakerRegistry | `retry("jev")`, `circuitBreaker("jev")` | WIRED |
| replay driver | replay SQL | `$(dirname "$0")/interest-calibration-replay.sql` | WIRED |
| drift test | InterestScoreQueries | `blendCte(InterestScoreQueries.UNREAD_SCOPE)` | PARTIAL (one copy) |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|---|---|---|---|---|
| InterestBadge | tier thresholds | yaml -> MyfeederProperties -> InterestStatusService -> `/status` -> useInterestTiers -> TierContext | yes (prod serves 70/22) | ✓ FLOWING |
| InterestBadge | score | `/api/articles/priority` interestScore from the SQL blend | yes (187 scored in prod) | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Prod version / status | `curl /api/version`, `/api/interest/status` | 0.2.1; configured, CLOSED, coldStart false, 0/0, tiers 70/22 | ✓ PASS |
| Tier spread in prod | page `/api/articles/priority`, bucket by served tiers | 15.5 / 33.7 / 50.8 % | ✓ PASS |
| Score rows | read-only psql on `article_score` | SCORED 199, 0 FAILED, max attempts 1 | ✓ PASS |
| Backend named tests | `./gradlew test --tests InterestCalibrationReplaySqlTest --tests JevResilienceTest --tests InterestStatusServiceTest` | 6/6, 20/20, 7/7 | ✓ PASS |
| Frontend tier tests | `npx vitest run useInterest.test.tsx InterestBadge.test.tsx interest.test.ts` | 19/19 | ✓ PASS |
| Deployment overrides | `kubectl get deploy myfeeder` env names | no `MYFEEDER_INTEREST_*` | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED (no `scripts/*/tests/probe-*.sh` exist and no plan declares one).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| OPS-01 | 07-03, 07-06, 07-07 | Released and deployed with a live key; backfill without 429 storms or open circuit | ✓ SATISFIED | Truths 1, 6, 9 |
| OPS-02 | 07-01, 07-02, 07-04, 07-08, 07-09 | Blend constants configurable and tuned against the real distribution | ✓ SATISFIED | Truths 2, 4, 5, 7, 10, 11 (truth 8 is a tooling guard gap, not a tuning gap) |
| OPS-03 | 07-05 | CLAUDE.md documents Jev behaviors and gotchas | ✓ SATISFIED | Truth 3 |

No orphaned requirements: REQUIREMENTS.md maps exactly OPS-01..03 to Phase 7 and every one is claimed by a plan.

### Prohibitions (judgment tier, non-authoritative LLM verdict, flagged for human confirmation)

| Plan | Prohibition | LLM verdict | Evidence |
|---|---|---|---|
| 07-06/07-09 | no release action before `approve` | holds (unverifiable ordering from code) | approvals recorded in SUMMARY/CALIBRATION |
| 07-06/07-07/07-08 | no prod writes by hand; read-only evidence | holds | driver enforces `default_transaction_read_only=on`; only Flyway V6 in history |
| 07-06/07-09 | no force-push, --no-verify, disableChecks, moved tag | holds | origin/main = 5461d0a (merge commit), tags annotated on origin |
| 07-06/07-07/07-08 | no key, profile text or topic descriptions in committed files | holds | profile text and all topic descriptions: 0 matches across tracked `.planning`, `src`, `scripts`, CLAUDE.md. Topic names: 0 in 07-BACKFILL.md; one 4-letter topic name appears in 07-CALIBRATION.md only inside two article titles (allowed content) |
| 07-07 | no SDK retry, concurrency stays 1 | holds | yaml `max-retries: 0`, `concurrency: 1`, no env override |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| (all phase files) | - | TBD/FIXME/XXX | none | 0 debt markers |
| `InterestCalibrationReplaySqlTest.java` | 35-40, 57-59 | contains()-only drift guard (07-REVIEW WR-02) | ⚠️ Warning -> gap | future drift of 2/3 blend and 3/4 badge copies is undetected |
| `JevEventLogging.java` | breaker lambda | recovery transitions print `-1.0%` rates (WR-01) | ⚠️ Warning | cosmetic in logs; transitions still logged |
| `MyfeederProperties` / `TierThresholds` | - | tier pair not validated (WR-03) | ⚠️ Warning | a swapped or typo pair would mis-colour badges silently |
| `utils/interest.ts` | 32 | fallback 70/40 vs shipped 70/22 (IN-01) | ℹ️ Info | brief colour flash for 22-39 until status loads |
| `CLAUDE.md` | tier-tuning bullet | attributes the tier pin to InterestScoreQueriesTest (IN-06) | ℹ️ Info | minor doc inaccuracy; the four OPS-03 topics are accurate |

### Human Verification Required

1. **Prod UI loads with a configured rubric**: open `/priority`, then Settings > Interests. Expected: badges on scored articles, no "not configured" notice, the rest of the UI unchanged. Why human: visual.
2. **Badge tier colours at 70/22**: 22-39 neutral colour, 70+ high, under 22 low. A brief first-paint low flash for 22-39 is the known fallback (IN-01). Why human: CSS rendering.
3. **Judgment-tier prohibitions**: confirm the table above. Why human: ADR-550 D4.

### Gaps Summary

The phase goal is achieved in production and the evidence is independent of the SUMMARYs: 0.2.1 runs with a live key, the 183-article legacy backlog drained in about 7 minutes with the breaker CLOSED in every sample and no Jev retry, exhausted or transition line, 199 articles are now SCORED with no FAILED rows, and the served 70/22 tiers spread live badges at 15.5 / 33.7 / 50.8 %. CLAUDE.md documents the Jev behaviors.

One plan-level must-have is only partly delivered. 07-04 promised that `InterestCalibrationReplaySqlTest` fails on any drift of the replay's blend or badge copies. It checks only that at least one copy matches (07-REVIEW WR-02, still open). All copies are identical today, so no shipped number is wrong. The gap is a durability guard for the documented tuning workflow. The fix is two test assertions (every-copy match and exact counts).

**If you accept this deviation**, add to this file's frontmatter instead of planning a gap-closure:

```yaml
overrides:
  - must_have: "07-04: InterestCalibrationReplaySqlTest fails on any drift of a blend or badge copy in scripts/interest-calibration-replay.sql"
    reason: "All copies verified byte-identical at ship time and prod cross-checks matched 5/5 twice; the every-copy assertion is tracked as 07-REVIEW WR-02"
    accepted_by: "<name>"
    accepted_at: "<ISO timestamp>"
```

---

_Verified: 2026-09-29T00:58:00Z_
_Verifier: Claude (gsd-verifier)_
