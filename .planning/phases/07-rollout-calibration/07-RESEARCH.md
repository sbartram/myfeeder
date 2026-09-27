# Phase 7: Rollout & Calibration - Research

**Researched:** 2026-09-27
**Domain:** Release engineering (axion-release, Helm/k3s, Flyway rollback safety), launch observation of a rate-limited external API (Resilience4j), SQL-based score calibration, config-driven UI tiers, docs
**Confidence:** HIGH for code/config facts and Flyway rollback (probed); MEDIUM for prod-side facts (the cluster and prod Postgres were not reachable from the research sandbox)

## Summary

Phase 7 is mostly operations. The code work is small: tier thresholds become server config served on `/api/interest/status`, and a few lines of Jev event logging (recommended, see below). Everything else is a release, an observed backfill, a read-only SQL replay against prod, and a CLAUDE.md update. The main risks are process risks: releasing from the wrong worktree, the axion pre-release checks, a partial rubric at backfill start, a watch script that has nothing to grep, and calibration targets the real distribution cannot reach.

Five findings change how this should be planned:
1. **The prod logs today contain no line to grep for 429 retries or breaker transitions.** The retry interval function in `TypeSafeConfig` logs nothing. A 429 that exhausts its retries is logged only at DEBUG (`ArticleScoringService`). Nothing subscribes to breaker events. A 60s status poll can also miss an OPEN period that lasts only 60s. D-05 requires the breaker to stay CLOSED for the whole backfill, and D-06 greps the logs. Both need a small INFO/WARN event logger for the `jev` retry and breaker, shipped in 0.2.0.
2. **D-03 is safe.** A throwaway-Postgres probe ran Flyway 11.14.1, the version Boot 4.0.8 manages for both 0.1.24 and HEAD. Running 0.1.24's V1–V5 against a V1–V6 database migrates and validates cleanly: V6 shows as `FUTURE_SUCCESS` with a WARN. Without the default `*:future` pattern, validation fails. v0.1.24 has no `spring.flyway.*` override.
3. **The release must run from the `main` worktree at `/Volumes/data2/scottb/dev/bartram/myfeeder`.** That worktree currently has a **staged `.envrc` change**, and axion's `verifyRelease` refuses to release with staged changes. Tag `v0.1.24` is not reachable from `sbartram/main`, so axion there reads `0.1.24-sbartram-main-SNAPSHOT`. After the `--no-ff` merge, `main` sees `v0.1.24`, and `-Prelease.versionIncrementer=incrementMinor` yields `0.2.0`.
4. **The replay can run the app's blend SQL verbatim.** psql interpolates `-v profilePoints=… -v learnRate=… -v learnedCap=…` into the exact `:name` placeholders used by `InterestScoreQueries`. This was probed: the reflected `blendCte(UNREAD_SCOPE)` text ran unchanged and gave 91 at profile-points 100 and 61 at 60 for a fixture where 75+16 and 45+16 are expected. A tiny drift-guard unit test keeps the file equal to the Java text.
5. **The Phase 3 spike predicts compression at the low end, not clustering at the top.** With profile-points 100, the highest real article scored 57.5, and most scored below 5.1 (topics under-fire). Under 70/40 almost nothing would be "high", and the D-10 target (high 10–20%, neutral 30–40%) may be unreachable for "neutral" if most articles sit near 0. The candidate grid must lower the thresholds or raise profile-points. Lowering them, as CONTEXT's "60/80/100" example does for profile-points, goes the wrong way.

**Primary recommendation:** Run it as four plans:
- Wave 1 (code, on `sbartram/main`): tier config on `/status` plus a React context for the badge, Jev event logging, the replay SQL plus its drift guard, and the CLAUDE.md docs.
- Wave 2 (ops): release 0.2.0 from the main worktree, `pg_dump`, deploy with the key, and a watched backfill into `07-BACKFILL.md`.
- Wave 3: replay, then `07-CALIBRATION.md`, then the user checkpoint.
- Wave 4: commit the tuned values (main yaml plus the dev overlay) and release 0.2.1.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (locked by prior phases, not re-discussed)
- **Blend (05/06):** `raw = ROUND(100·p·(profile-points/100) + Σ m_t·w_t, 6)`, `m_t = max(0,(noul−0.5)·2)`, `w_t = clamp(base + learned, −50, +50)` with sign clamp, `learned = clamp(learn-rate·Σ vote·m_t, ±learned-cap)`. The existing constants are `myfeeder.interest.blend.{profile-points: 100, learn-rate: 2, learned-cap: 20}` in `application.yaml`. All of it is **query-time**: changing constants needs no Jev re-score.
- **One source of truth:** `InterestScoreQueries` drives the sort, the badge and the breakdown. The client never recomputes scores.
- **Tiers today:** ≥70 high, 40–69 neutral, <40 muted, hardcoded in `src/main/frontend/src/utils/interest.ts` `tierOf`.
- **Pipeline (04):** a single-thread `jev-score` executor (`myfeeder.interest.concurrency: 1`), a sweep every `PT2M` enqueueing ≤ `sweep-batch-size: 50`, eligibility = unread and inside `window-days: 14`. The sweep pauses when unconfigured, in cold start, or with the breaker OPEN. The `jev` breaker is the outer aspect (one outcome per logical call) with auto OPEN→HALF_OPEN. Exactly one retry layer: Resilience4j `jev` retry (3 attempts, 429 `retry-after-ms` honored ≤10s), SDK `retry.max-retries: 0`.
- **`/api/interest/status`** is append-only: `{configured, breakerState, coldStart, eligibleUnscored, failed}`. New fields are appended and existing ones are never renamed.
- **Deploy pipeline (root CLAUDE.md):** release before bootJar, `clean bootJar`, `docker build --provenance=false`, push, `./deploy.sh $VERSION`. `deploy.sh` already takes the optional `MYFEEDER_TYPESAFE_API_KEY` (02-04), and checksum/secret rolls the pod.
- **Git:** merge `sbartram/main` → `main` with `--no-ff` before releasing.

#### Release & key rollout (OPS-01)
- **D-01:** Release as **0.2.0** (minor bump for a new schema, a new external dependency and a new view): `./gradlew release` with axion's minor incrementer (e.g. `-Prelease.versionIncrementer=incrementMinor`), not the default patch.
- **D-02:** **The key goes live on the first deploy.** Set `MYFEEDER_TYPESAFE_API_KEY` for the 0.2.0 deploy. Scoring stays idle until the user saves a profile/topic in prod (cold start), so the user controls when the backfill starts. No dark deploy.
- **D-03:** Take a **one-off `pg_dump` of the prod myfeeder DB before the 0.2.0 deploy.** Rollback = `helm rollback` to 0.1.24, and restore only if needed. V6 is additive. The planner should verify that Flyway on 0.1.24 tolerates an applied-but-unknown V6 (default `ignoreMigrationPatterns: *:future`).
- **D-04:** **Ship with the open review warnings.** 06-REVIEW WR-01..04, the 06-UI-REVIEW a11y items and 03-REVIEW WR-01..05 are non-blocking and are fixed after launch, not in this phase.

#### Launch backfill observation (OPS-01)
- **D-05:** **Pass bar:** `eligibleUnscored` drains to ~0 (only new arrivals remain), `breakerState` stays CLOSED for the whole backfill, and any 429s in the logs were absorbed by the `jev` retry, with no FAILED rows caused by them. A few isolated 429s are acceptable.
- **D-06:** **Observation = a Claude-run watch script.** It's a throwaway loop, not app code: `kubectl port-forward`/curl `/api/interest/status` plus `kubectl logs` grepped for 429 / breaker transitions. It records counts over time as evidence in the phase dir (e.g. `07-BACKFILL.md`). No Actuator metrics are added.
- **D-07:** **Concurrency stays at 1** during and after launch. Raise it only if drain time actually becomes a complaint, which is not planned here.
- **D-08:** **429/breaker fallback = throttle the sweep via config:** lower `sweep-batch-size` and/or lengthen `sweep-delay`, keep the single Resilience4j `jev` retry, and let auto half-open resume. Do **not** swap to the SDK retry layer. (Note: at ~2.6s/call, 50 per 2 min already roughly matches single-thread throughput, so a lower batch size is what actually caps the rate.)

#### Calibration method (OPS-02)
- **D-09:** **A read-only SQL replay against prod pg.** A committed analysis SQL/script replays the blend over stored nouls/profile scores for several candidate constant sets and prints, for each set, the tier histogram and percentiles over scored unread articles. Results go into `07-CALIBRATION.md` in the phase dir. No app endpoint is added.
- **D-10:** **Target:** among scored unread articles, **high ≈ 10–20%**, neutral ≈ 30–40%, the rest muted. High must stay a real "read this" signal.
- **D-11:** **Quality check:** for the proposed constants, the replay also lists the **top 20 and bottom 20 article titles**. Claude proposes the constants and the **user approves** them after reviewing those lists (a human checkpoint) before they ship.
- **D-12:** **Timing:** calibrate **once, after the launch backfill drains**. Tune `profile-points` and the tiers. Leave `learn-rate`/`learned-cap` at 2/20 unless the available vote data already shows a clear problem (a few days of votes is too little to tune them).

#### Tier configurability (OPS-02)
- **D-13:** Tier thresholds move to **server config**: `myfeeder.interest.blend.tiers.high` (default 70) and `.neutral` (default 40) in `application.yaml` (and the `MyfeederProperties` binding). They are **served to the client** by an existing interest GET. The preferred option is appending fields to `/api/interest/status` under the append-only rule; the exact shape is Claude's discretion. `tierOf` takes the thresholds from that query, with 70/40 as the fallback until it loads. Tuning = a config change, no frontend constant edit. — **Reversibility:** costly — the new status/config field becomes a contract that `InterestBadge`/`tierOf` and their tests consume, and status fields are append-only and never renamed.
- **D-14:** **Tuned constants ship via source control.** Commit them to main `application.yaml` with a comment citing `07-CALIBRATION.md`, keep the test yaml + `application-dev.yaml` overlay consistent so `DevProfileConfigTest` passes, and release as **0.2.1** through the normal pipeline. No Helm env overrides for blend/tier values.

### Claude's Discretion
- **OPS-03 CLAUDE.md content (not discussed):** document the app-owned `TypeSafeClient` bean (`config/TypeSafeConfig`, Supplier-only key, Jev-only Reactor Netty transport), the single retry layer (Resilience4j `jev` retry, SDK retries 0, 429 retry-after handling), the `jev-score` scoring executor (concurrency/queue/discard, sweep cadence and batch cap, gates), the eligibility window, and the new tier config. Also fold in the Raindrop todo's doc items: Package Structure listing `config/TypeSafeConfig` and the Jev integration classes, plus `MYFEEDER_TYPESAFE_API_KEY` as an optional `deploy.sh` variable in the Deployment section. Check for stale statements (e.g. "Spring AI Anthropic starter… not yet used", the Resilience4j aspect-order text already fixed in 04).
- The exact `/status` (or other GET) field shape for the tiers, and how `useInterestStatus`/`InterestBadge` get them in views where status isn't polled.
- The watch-script mechanics, polling interval, and how the evidence is summarized.
- The candidate constant sets for the replay (e.g. profile-points 60/80/100 × tier pairs) and the replay SQL structure. It should reuse the `InterestScoreQueries` CTE text or mirror it exactly so the replay matches what the app computes.
- The prod DB access path for the replay and `pg_dump` (e.g. psql to `pg.bartram.org` from the LAN or a `kubectl run` pod), always read-only for the replay.
- Soak length after the 0.2.0 deploy and the post-deploy smoke checks (startup logs, Priority view, a preview call).

### Deferred Ideas (OUT OF SCOPE)
- Fixing the open review warnings (06-REVIEW WR-01..04, 06-UI-REVIEW a11y items, 03-REVIEW WR-01..05, 02-REVIEW WR-04/05): after launch, via `/gsd-code-review --fix` or quick tasks (D-04).
- Tuning `learn-rate`/`learned-cap` from real vote data: after about a week or more of use. Not in this phase unless the data already shows a clear problem (D-12).
- Raising scoring concurrency above 1: only if drain time becomes a complaint (D-07).
- Actuator/Micrometer metrics for scoring and the breaker: rejected in favor of a throwaway watch script (D-06).
- A `lastError`/auth-failure field on `/status` (04 deferred): still deferred.
- **Tune Raindrop resilience** todo: the Raindrop retry/backoff tuning stays a quick task. Only its CLAUDE.md doc items are taken into OPS-03.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| OPS-01 | The feature is released and deployed with a live key; the launch backfill runs without 429 storms or an open circuit | §Release mechanics (axion, worktree, verifyRelease), §Rollback safety (Flyway probe, helm history), §pg_dump, §Backfill observation (the log gap, event logger, watch script, evidence SQL), §Throttle levers, Pitfalls 1–7 |
| OPS-02 | Blend constants (profile weight, learning rate, cap, badge tiers) are configurable and tuned against the real score distribution | §Tier config design (properties → status record → context → `tierOf`), §Calibration replay (verbatim CTE via psql vars, drift guard, candidate grid, output), §D-14 yaml placement (dev overlay), Pitfalls 8–11 |
| OPS-03 | CLAUDE.md documents the new Jev behaviors and gotchas (app-owned client bean, single retry layer, scoring executor, eligibility window) | §OPS-03 CLAUDE.md delta: the stale and missing lines with the source facts to cite |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Java/JVM builds use Gradle (Kotlin DSL); never Maven. [VERIFIED: CLAUDE.md]
- Spring Data JDBC, not JPA; `@Query` for custom SQL. [VERIFIED: CLAUDE.md]
- Jackson 3: `tools.jackson.databind.*` for databind; annotations stay `com.fasterxml.jackson.annotation.*`. [VERIFIED: CLAUDE.md]
- BOM-managed versions for Spring AI and Spring Cloud. No version on individual deps. [VERIFIED: CLAUDE.md]
- Release order: `./gradlew release` **before** `./gradlew clean bootJar`; `docker build --provenance=false`; push; `./deploy.sh $VERSION` with an explicit version; `rollout status`; logs. Never use `bootBuildImage` (Docker 29 containerd diffID bug). [VERIFIED: CLAUDE.md §Deployment/Gotchas]
- `./gradlew release` pushes through the `gitPushRelease` Exec task (`git push --follow-tags origin HEAD`). Don't replace it with axion auth. The git-lfs `locksverify false` fix is already documented. [VERIFIED: build.gradle.kts:88-100]
- The test yaml plus `application-dev.yaml` must resolve every main key to main's value, and no test may activate `dev` (`DevProfileConfigTest`). Never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev` in a shell that runs `./gradlew test`. [VERIFIED: DevProfileConfigTest.java:78-97; CLAUDE.md Gotchas]
- Frontend type-check with `npx tsc -b` (not `tsc --noEmit`). A `vi.mock` of a module is full replacement, so prefer the `importOriginal` spread. [VERIFIED: CLAUDE.md]
- Zustand persist: a new preference default does not reach existing users. Not relevant if tiers come from the server, which is another reason to keep them out of `preferencesStore`. [VERIFIED: CLAUDE.md]
- Status fields are append-only. Error text is fixed and never echoes TypeSafe messages or bodies. [VERIFIED: CLAUDE.md §Interest Ranking/GlobalExceptionHandler]
- Global git rules: non-trivial work happens on a feature branch (`sbartram/main` here), merges to main use `--no-ff`, and push only on request. Subagents never `git checkout <sha>`. [VERIFIED: ~/.claude/CLAUDE.md §0.5]
- The GSD workflow applies to repo edits. [VERIFIED: .claude/CLAUDE.md]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tier thresholds (source of truth) | API / Backend (`MyfeederProperties` → `InterestStatus`) | Browser (fallback 70/40 only) | D-13: tuning is a config change; the client never owns constants |
| Tier classification of a badge | Browser (`tierOf`) | — | Presentation only; the score itself comes from `InterestScoreQueries` |
| Blend and score values | Database (SQL CTE in `InterestScoreQueries`) | API | One source of truth; the replay reuses the same SQL text |
| Calibration replay | Database (read-only psql session) | Ops workstation (shell driver) | D-09: no app endpoint |
| Jev retry/breaker observability | API / Backend (Resilience4j event consumers → logs) | Ops (watch script greps logs) | The events exist only in-process; logs are the D-06 channel |
| Backfill progress | API (`/api/interest/status`) | Database (evidence queries) | Existing contract; DB queries add failure breakdown |
| Release/deploy/rollback | Ops (git/axion, Docker, Helm/k3s) | — | No app change |
| Pre-deploy backup | Ops (`pg_dump` from LAN workstation) | Database | D-03 |

## Standard Stack

No new libraries. Everything below is already on the classpath or the workstation.

### Core (already in project)
| Library/Tool | Version | Purpose | Evidence |
|---------|---------|---------|--------------|
| axion-release Gradle plugin | 1.21.1 | Tag-derived versioning; `release` with CLI incrementer | [VERIFIED: build.gradle.kts:5 `id("pl.allegro.tech.build.axion-release") version "1.21.1"`] |
| Flyway (via Boot BOM) | 11.14.1 | Migrations; rollback tolerance | [VERIFIED: spring-boot-dependencies-4.0.8.pom:58 `<flyway.version>11.14.1</flyway.version>`; same Boot 4.0.8 at v0.1.24 per `git show v0.1.24:build.gradle.kts` line 3] |
| Resilience4j | 2.3.0 (resilience4j-spring-boot3, via Spring Cloud 2025.1.3) | `jev` breaker/retry + event publishers for logging | [VERIFIED: runtime classpath jar names `resilience4j-retry-2.3.0.jar`, `resilience4j-circuitbreaker-2.3.0.jar`, `resilience4j-spring-boot3-2.3.0.jar`] |
| TanStack Query | ^5.103.2 | Status query; per-observer staleTime/refetchInterval | [VERIFIED: src/main/frontend/package.json:18] |
| React | ^19.3.0 | `createContext` for tier thresholds | [VERIFIED: package.json:20] |
| Vitest | ^4.1.11 | Frontend tests | [VERIFIED: package.json:43] |

### Workstation tools
| Tool | Version | Purpose |
|------|---------|---------|
| psql / pg_dump | 18.6 (Homebrew) | Read-only replay; pre-deploy dump |
| kubectl | 1.37.1 | logs, rollout, secret hash check |
| helm | v4.3.0 | deploy.sh, `helm history`/`rollback` |
| docker | 29.8.0 | Dockerfile image build |
| jq | 1.8.2 | Parse status JSON in the watch script |

**Installation:** none.

## Package Legitimacy Audit

This phase installs no external packages (npm, Maven or other). No legitimacy check needed.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                 sbartram/main (this worktree)                        main worktree
 Wave 1 code ──► tier config + event logging + replay.sql + docs ──► /Volumes/.../myfeeder
                                                                      git merge --no-ff sbartram/main
                                                                      git push origin main
                                                                      ./gradlew release -Prelease.versionIncrementer=incrementMinor  ─► tag v0.2.0 (pushed by gitPushRelease)
                                                                      ./gradlew clean bootJar ─► docker build/push :0.2.0
                                                                                    │
   pg_dump -Fc (LAN psql to pg.bartram.org) ◄── BEFORE deploy ─────────────────────┤
                                                                                    ▼
                                           ./deploy.sh 0.2.0 (MYFEEDER_TYPESAFE_API_KEY set) ─► helm rev N ─► pod 0.2.0
                                                                                    │  Flyway applies V6 (additive)
                                                                                    ▼
   watch script (every 60s) ──curl──► http://192.168.44.204/api/interest/status  {configured, breakerState, coldStart,
        │                                                                        eligibleUnscored, failed, tiers}
        ├──kubectl logs -f──► "Jev retry …" / "Jev circuit breaker …" lines (new event logger)
        └──psql (read-only)──► article_score status / last_error breakdown
                                                                                    │
   user saves profile + topics in prod ──► coldStart=false ──► sweep (≤50 / 2 min) + ingest ──► jev-score executor (1 thread)
                                                                                    │               └─► Jev (breaker outer, retry inner)
                                                                                    ▼
                                     eligibleUnscored → ~0, breaker CLOSED ──► 07-BACKFILL.md (evidence + verdict)
                                                                                    │
   replay.sh ── for each candidate (profilePoints, tierHigh, tierNeutral) ──► psql -v … -f replay.sql (verbatim blend CTE)
        └─► histogram, percentiles, top/bottom 20 ──► 07-CALIBRATION.md ──► USER CHECKPOINT (approve constants)
                                                                                    │
   commit tuned values: main application.yaml + application-dev.yaml ──► merge ──► ./gradlew release (patch) ─► 0.2.1 ─► deploy
```

### Recommended file layout (new/changed)
```
src/main/java/org/bartram/myfeeder/
├── config/MyfeederProperties.java        # + Interest.Blend.Tiers { high=70, neutral=40 }
├── config/JevEventLogging.java           # NEW (recommended): logs jev retry + breaker transitions
├── service/InterestStatus.java           # + trailing component TierThresholds tiers
├── service/TierThresholds.java           # NEW record (high, neutral)  (or nest it in InterestStatus)
└── service/InterestStatusService.java    # passes properties' tiers through
src/main/resources/application.yaml       # + myfeeder.interest.blend.tiers.{high,neutral}
src/test/resources/application.yaml       # + same keys, same default values
src/main/frontend/src/
├── api/interest.ts                       # InterestStatus + optional `tiers?: TierThresholds`
├── utils/interest.ts                     # DEFAULT_TIERS; tierOf(score, tiers = DEFAULT_TIERS)
├── utils/tierContext.ts (or hooks/)      # NEW: TierContext = createContext(DEFAULT_TIERS)
├── hooks/useInterest.ts                  # + useInterestTiers(): status query, staleTime Infinity, select tiers
├── App.tsx                               # MainLayout wraps children in <TierContext.Provider>
└── components/InterestBadge.tsx          # useContext(TierContext) → tierOf(score, tiers)
scripts/
├── interest-calibration-replay.sql       # NEW: verbatim blend CTE with psql :vars (3 statements)
└── interest-calibration-replay.sh        # NEW: loops candidate sets, read-only PGOPTIONS
src/test/java/.../repository/InterestCalibrationReplaySqlTest.java  # NEW drift guard (no Docker)
```

### Pattern 1: Tier thresholds as append-only status field
**What:** Bind `myfeeder.interest.blend.tiers.high|neutral`, then add one trailing component to the record.
```java
// MyfeederProperties.Interest.Blend (existing class, MyfeederProperties.java:52-60)
private Tiers tiers = new Tiers();
@Data
public static class Tiers {
    /** Display score at or above which a badge is "high" (inclusive). Phase 7 tunes it. */
    private int high = 70;
    /** Display score at or above which a badge is "neutral" (inclusive); below is "low". */
    private int neutral = 40;
}

// service/TierThresholds.java
public record TierThresholds(int high, int neutral) {}

// InterestStatus: append ONLY at the end (append-only contract, InterestStatus.java:17-20)
public record InterestStatus(boolean configured, String breakerState, boolean coldStart,
                             long eligibleUnscored, long failed, TierThresholds tiers) {}

// InterestStatusService.status(): last constructor argument
MyfeederProperties.Interest.Blend.Tiers t = properties.getInterest().getBlend().getTiers();
... new TierThresholds(t.getHigh(), t.getNeutral())
```
JSON: `"tiers":{"high":70,"neutral":40}`. Only one production construction site exists (`InterestStatusService.java:28`) [VERIFIED: grep `new InterestStatus(`]. `InterestStatusServiceTest` builds the service with `new MyfeederProperties()` (line 40), so the defaults flow through without changing it. Add one assertion for `tiers`.

### Pattern 2: Tiers reach every badge through a React context (no new observers, no test churn)
**What:** Put one status observer in `MainLayout` with `staleTime: Infinity` and `select: s => s.tiers`, and provide the thresholds through `TierContext` whose **default value is 70/40**. `InterestBadge` reads the context.
**Why this design:**
- `InterestBadge` is rendered in `PriorityList`, `ArticleList`, `BoardArticleList` and `ScoreRow` [VERIFIED: grep]. `ArticleList.test.tsx` and `BoardArticleList.test.tsx` render **without a `QueryClientProvider`** (QCP count 0) [VERIFIED: grep]. A `useQuery` inside the badge would throw "No QueryClient set" in those tests. With a context whose default is 70/40, every existing badge test (`InterestBadge.test.tsx` asserts 39→low, 40→neutral, 69→neutral, 70→high) stays green unchanged.
- TanStack v5 runs timers per observer, and an observer without `refetchInterval` never polls [CITED: tanstack/query docs, polling.md "Note on deduplication"]. A per-observer `staleTime: Infinity` means the root observer never triggers mount or focus refetches. So adding it changes none of the existing polling behavior (`useInterestStatus` keeps its 15s gate), and it does not change the status call counts asserted in `InterestsDialog.test.tsx:294` or `PriorityList.test.tsx`, which render components without `MainLayout`.
- `select` re-renders only when `tiers` changes [CITED: tanstack/query render-optimizations.md].
```tsx
// utils/interest.ts
export interface TierThresholds { high: number; neutral: number }
export const DEFAULT_TIERS: TierThresholds = { high: 70, neutral: 40 }
export function tierOf(score: number, tiers: TierThresholds = DEFAULT_TIERS): Tier {
  if (score >= tiers.high) return 'high'
  if (score >= tiers.neutral) return 'neutral'
  return 'low'
}
// hooks/useInterest.ts
export function useInterestTiers(): TierThresholds {
  const { data } = useQuery({
    queryKey: ['interest', 'status'],
    queryFn: interestApi.getStatus,
    staleTime: Infinity,
    select: (s) => s.tiers,
  })
  return data ?? DEFAULT_TIERS
}
// App.tsx MainLayout: const tiers = useInterestTiers(); return <TierContext.Provider value={tiers}>…</TierContext.Provider>
// InterestBadge: const tiers = useContext(TierContext); className={`interest-badge tier-${tierOf(score, tiers)}`}
```
Make the TS field optional (`tiers?: TierThresholds`). Existing test fixtures typed as `InterestStatus` then still compile under `npx tsc -b`, and an older or failed response still falls back. Memoize nothing extra: `select` plus structural sharing keeps the context value referentially stable.

### Pattern 3: Jev event logging (closes the observability gap D-05/D-06 depend on)
**What:** One small `@Configuration` subscribes to the existing `jev` instances' event publishers.
```java
// config/JevEventLogging.java (recommended; logs class names and numbers only, never messages or bodies)
@Slf4j
@Configuration(proxyBeanMethods = false)
class JevEventLogging {
    JevEventLogging(CircuitBreakerRegistry breakers, RetryRegistry retries) {
        CircuitBreaker jev = breakers.circuitBreaker("jev");
        jev.getEventPublisher().onStateTransition(e -> log.warn(
                "Jev circuit breaker {} (failure rate {}%, slow-call rate {}%)",
                e.getStateTransition(), jev.getMetrics().getFailureRate(), jev.getMetrics().getSlowCallRate()));
        retries.retry("jev").getEventPublisher().onRetry(e -> log.info(
                "Jev retry attempt {} after {} (waiting {} ms)",
                e.getNumberOfRetryAttempts(), e.getLastThrowable().getClass().getSimpleName(),
                e.getWaitInterval().toMillis()));
    }
}
```
API signatures confirmed in the 2.3.0 jars: `Retry.EventPublisher.onRetry(EventConsumer<RetryOnRetryEvent>)`, `RetryOnRetryEvent.getWaitInterval(): Duration`, `getNumberOfRetryAttempts(): int`, `getLastThrowable(): Throwable`, `CircuitBreaker.EventPublisher.onStateTransition(EventConsumer<CircuitBreakerOnStateTransitionEvent>)`, `getStateTransition(): CircuitBreaker.StateTransition` [VERIFIED: javap on runtime classpath]. The registry is get-or-create by name, and the `@CircuitBreaker(name="jev")`/`@Retry(name="jev")` aspects on `JevApiClientImpl.judge` (JevApiClientImpl.java:47-48) use the same instances [ASSUMED: standard Resilience4j registry semantics; the test below proves it]. Test it in the existing `JevResilienceTest` context (it already resolves `RetryRegistry`, line 395) with `OutputCaptureExtension`, which the project already uses in `TypeSafeConfigTest`/`JevApiClientImplTest`. Drive a stubbed 429 and assert the "Jev retry attempt 1 after TypeSafeRateLimitException" line. Force a transition and assert the "Jev circuit breaker CLOSED_TO_OPEN" line.

**Scope note:** D-06 says the *watch script* is throwaway and no Actuator metrics are added. This logger is neither: it is permanent app code (about 20 lines) that makes the log channel D-06 relies on exist. **Confirm it with the user or planner** (Open Question 1). Without it, D-05's "breaker CLOSED for the whole backfill" can be asserted only by status polling, which can miss a 60s OPEN window, and 429 absorption cannot be evidenced at all.

### Pattern 4: Calibration replay = verbatim app SQL + psql variables
**What:** `scripts/interest-calibration-replay.sql` holds the **exact** text `InterestScoreQueries.blendCte(UNREAD_SCOPE)` produces. Its named params `:profilePoints`, `:learnRate` and `:learnedCap` are the same tokens psql interpolates from `-v`. The tier thresholds are extra psql vars (`:tierHigh`, `:tierNeutral`).
- **Verified:** the reflected `blendCte("a.\"read\" = false")` text plus `INTEREST_SCORE` ran unchanged under `psql -v profilePoints=100 -v learnRate=2 -v learnedCap=20` against a V1–V6 schema. A fixture with profile 3/4 and one +20 topic at noul 0.9 gave `91` (75 + 0.8·20). With `profilePoints=60` it gave `61` [VERIFIED: local postgres:17-alpine probe, this session]. psql leaves `::int`/`::numeric` casts and quoted literals alone.
- A read-only transaction forbids **all** `CREATE` commands, temp tables included [CITED: postgresql.org/docs/current/sql-set-transaction.html], so there is no temp view. Repeat the verbatim CTE in each of the three statements (summary, top 20, bottom 20). Alternatively, one statement can emit per-article rows as CSV for the shell to aggregate.
- **Drift guard (no Docker):** make `blendCte` package-private (it is `private static` today, InterestScoreQueries.java:314). Add `InterestCalibrationReplaySqlTest` in package `repository` that reads `scripts/interest-calibration-replay.sql` (path relative to the project root, like `DevProfileConfigTest` does) and asserts it contains `blendCte(UNREAD_SCOPE)` and `INTEREST_SCORE` verbatim. Identical text plus identical bound values gives identical scores.

Statement skeleton (the `<BLEND>` token stands for the verbatim `blendCte(UNREAD_SCOPE)` text, pasted, not a placeholder at runtime):
```sql
-- 1) summary for one candidate set
<BLEND>, scored AS (SELECT b.article_id, b.raw_n,
  CASE WHEN b.raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END AS interest_score
  FROM blended b)
SELECT :profilePoints AS profile_points, :tierHigh AS tier_high, :tierNeutral AS tier_neutral,
       count(*) AS scored_unread,
       round(100.0 * count(*) FILTER (WHERE interest_score >= :tierHigh) / NULLIF(count(*),0), 1) AS high_pct,
       round(100.0 * count(*) FILTER (WHERE interest_score >= :tierNeutral AND interest_score < :tierHigh) / NULLIF(count(*),0), 1) AS neutral_pct,
       round(100.0 * count(*) FILTER (WHERE interest_score < :tierNeutral) / NULLIF(count(*),0), 1) AS low_pct,
       percentile_disc(ARRAY[0.10,0.25,0.50,0.60,0.75,0.80,0.85,0.90,0.95]) WITHIN GROUP (ORDER BY interest_score) AS pctl,
       count(*) FILTER (WHERE interest_score = 0) AS at_zero, count(*) FILTER (WHERE interest_score = 100) AS at_100
FROM scored;
-- 2) top 20 / 3) bottom 20: <BLEND> … SELECT interest_score, round(b.raw_n,1), a.title FROM blended b JOIN article a ON a.id = b.article_id
--    ORDER BY b.raw_n DESC, COALESCE(a.published_at, a.fetched_at) DESC, a.id DESC LIMIT 20   (ASC … for bottom)
```
Driver (`scripts/interest-calibration-replay.sh`):
```bash
export PGOPTIONS='-c default_transaction_read_only=on'   # every statement read-only (verified: CREATE is rejected)
for set in "100 70 40" "100 50 25" "125 70 40" ...; do read pp hi ne <<<"$set"
  PGPASSWORD="$MYFEEDER_PG_PASSWORD" psql -h pg.bartram.org -U myfeeder -d myfeeder -X -A -F $'\t' \
    -v ON_ERROR_STOP=1 -v profilePoints="$pp" -v learnRate=2 -v learnedCap=20 -v tierHigh="$hi" -v tierNeutral="$ne" \
    -f scripts/interest-calibration-replay.sql
done
```
`PGOPTIONS` with `default_transaction_read_only=on` rejects writes (`ERROR: cannot execute CREATE TABLE in a read-only transaction`) [VERIFIED: local probe].

### Pattern 5: Watch script (throwaway, outside the repo or in the phase dir)
```bash
C="$HOME/.cache/myfeeder-phase07"; mkdir -p "$C"; START=$(date -u +%Y-%m-%dT%H:%M:%SZ); echo "$START" > "$C/watch-start.txt"
kubectl -n myfeeder logs -f deploy/myfeeder --since-time="$START" > "$C/backfill.log" 2>&1 &   # re-attach if the pod restarts
while :; do
  printf '%s %s\n' "$(date -u +%FT%TZ)" "$(curl -sf --max-time 10 http://192.168.44.204/api/interest/status || echo '{"error":"unreachable"}')" >> "$C/status.log"
  sleep 60
done
```
- Use the LB IP `192.168.44.204` directly; no port-forward is needed (phase 01-04 used it) [VERIFIED: 01-04-PLAN.md; helm values.yaml `metallb.universe.tf/loadBalancerIPs: "192.168.44.204"`].
- Summary greps: `grep -c 'Jev retry attempt' backfill.log`, `grep 'Jev circuit breaker' backfill.log`, `grep -c 'Scoring article .* failed' backfill.log`, `jq` over `status.log` for min/max `eligibleUnscored`, any `breakerState != "CLOSED"`, and `failed`.
- Evidence SQL (read-only):
  `SELECT status, count(*), count(*) FILTER (WHERE attempts > 1) AS retried FROM article_score GROUP BY status;`
  `SELECT last_error, count(*) FROM article_score WHERE status = 'FAILED' GROUP BY 1 ORDER BY 2 DESC;`
  Remaining backlog versus new arrivals: count eligible, unscored articles whose `fetched_at` is older than the watch start. It should reach 0.
- By construction, a 429 **never** produces a FAILED row: `TypeSafeRateLimitException` is transient, so no row and no attempt are written (ScoringFailure.java:30-38). "No FAILED rows caused by 429" therefore holds by design. The evidence to collect is the retry lines and the breaker staying CLOSED.

### Anti-Patterns to Avoid
- **Running `./gradlew release` on `sbartram/main`:** tag `v0.1.24` is not in its history (axion reads `0.1.24-sbartram-main-SNAPSHOT`), and the tag would land on the wrong branch.
- **A `useQuery` inside `InterestBadge`:** it adds one status observer per rendered badge, a stale-on-mount refetch per badge mount (the existing status staleTime is 0), and breaks the provider-less list tests.
- **Changing the test yaml's `profile-points`/tiers to the tuned values:** `InterestScoreQueriesTest` and others hard-code raws computed at profile-points 100 (for example "a4 133.32; a1..a3 82.2", InterestScoreQueriesTest.java:33-37) and read the constant from the test yaml. No test sets `profilePoints` itself [VERIFIED: grep returned no hits]. Put the tuned values in **main yaml + `application-dev.yaml`** instead (Pattern 6).
- **Tuning profile-points down to spread tiers:** it compresses scores further (see Pitfall 8).
- **Helm `--set` overrides or `kubectl set env` for blend/tier values:** forbidden by D-14. `kubectl set env` is acceptable only as an emergency *sweep throttle* (D-08) and must be reconciled in yaml.

### Pattern 6: Where the tuned constants go (D-14, DevProfileConfigTest-compatible)
`devOverlayResolvesEveryMainKeyToMainsValue` compares each main key against `dev.containsProperty(name) ? dev : test` (DevProfileConfigTest.java:86-89). Dev-only keys must also exist in main, except `spring.ai.typesafe.base-url` (lines 90-95). So:
- main `application.yaml`: tuned `profile-points` and `tiers.high/neutral`, with a comment citing `07-CALIBRATION.md`
- `src/test/resources/application-dev.yaml`: the **same tuned values** (they exist in main, so the second loop allows them)
- `src/test/resources/application.yaml`: unchanged (100 / 70 / 40), with a comment saying the suite pins the pre-calibration constants and the dev overlay carries main's tuned values

This passes the guard, keeps every fixture expectation valid, and gives `bootTestRun` the prod constants. For the Wave 1 tier *keys*, add `tiers.high: 70` and `tiers.neutral: 40` to both main and test yaml, the same values in both.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Replay the blend | A re-derived formula in the script | The verbatim `blendCte` SQL with psql `-v` vars | Clamps, sign clamp, ROUND-to-6 and NULL handling differ subtly; identical text makes identical scores |
| Version bump | Hand-made `git tag v0.2.0` | `./gradlew release -Prelease.versionIncrementer=incrementMinor` | axion's verifyRelease, annotated tag and `gitPushRelease` wiring |
| Retry/breaker observation | Parsing HTTP logs, polling faster | Resilience4j `EventPublisher.onRetry`/`onStateTransition` | Exact, in-process, and catches short OPEN windows |
| Read-only safety | "Be careful" | `PGOPTIONS='-c default_transaction_read_only=on'` | The server rejects any write |
| Rollback schema handling | Manual `flyway_schema_history` edits | Flyway default `*:future` (already active) | Probed: 0.1.24 starts cleanly against V6 |
| Key-in-secret check | Printing the key | `sha256` compare of `kubectl get secret … | base64 -d` with the local value | Never echoes the secret |

## Release, Rollback and Backup Mechanics (OPS-01 detail)

### axion 0.2.0 (D-01)
- The command is `./gradlew release -Prelease.versionIncrementer=incrementMinor`, which overrides the incrementer from the CLI [CITED: github.com/allegro/axion-release-plugin docs/configuration/version.md]. Preview with `-Prelease.dryRun` [CITED: docs/configuration/dry_run.md].
- **Composes with the custom wiring:** `release` has its actions cleared, `dependsOn("createRelease")`, `finalizedBy(gitPushRelease)` [VERIFIED: build.gradle.kts:88-96]. The incrementer is read when `createRelease` computes the version, so the finalizer pushes the new tag with `git push --follow-tags origin HEAD`. `--follow-tags` pushes only **annotated** tags; `v0.1.24` is annotated (it has a Tagger line), so axion's tags qualify [VERIFIED: `git show v0.1.24`].
- `verifyRelease` blocks on staged or unstaged changes and on "ahead of remote" [CITED: docs/configuration/checks.md]. So: merge, then `git push origin main`, then release. Never pass `-Prelease.disableChecks` (01-04 precedent).
- **The worktree is fixed:** `main` is checked out at `/Volumes/data2/scottb/dev/bartram/myfeeder`, and this worktree is on `sbartram/main` [VERIFIED: `git worktree list`]. `main` cannot be checked out here. The merge-base of `main` and HEAD is `388d48d`, and `v0.1.24` (29da9aa) is only on `main` [VERIFIED: `git merge-base`, `git describe` → `v0.1.23` on HEAD]. Run everything with `git -C /Volumes/.../myfeeder` or from that directory.
- **Blocker:** that worktree has a **staged** `.envrc` modification (`M  .envrc`, 2+/1−) [VERIFIED: `git -C … status`]. It must be committed, unstaged or stashed by the user before `release`. Never print it; it may reference secrets.
- 0.2.1 later: plain `./gradlew release` (default patch increment from `v0.2.0`).

### Rollback (D-03) — verified safe
- v0.1.24 sets no `spring.flyway.*` keys [VERIFIED: `git show v0.1.24:src/main/resources/application.yaml` has no `flyway` match], and its chart/configmap sets no Flyway env.
- Boot 4.0.8 `FlywayProperties` defaults `ignoreMigrationPatterns = ["*:future"]` and `validateOnMigrate = true` [VERIFIED: javap of spring-boot-flyway-4.0.8.jar constructor: `ldc "*:future"` → `ignoreMigrationPatterns`; `iconst_1` → `validateOnMigrate`]. Flyway 11.14.1's own `FlywayModel` defaults are the same [VERIFIED: javap FlywayModel].
- **Positive probe:** migrate V1–V6, then run Flyway with only 0.1.24's V1–V5 files and the Boot defaults. The migrate succeeds (`Schema "public" has a version (6) that is newer than the latest available migration (5) !` WARN, then `Schema "public" is up to date`), `info()` shows `V6 FUTURE_SUCCESS`, and `validate` is true. Negative control with `*:missing` only: `Validate failed: … Detected applied migration not resolved locally: 6.` [VERIFIED: local probe on PostgreSQL 17.11; prod server version not observed].
- V6 only creates new tables. Every FK to `article` is `ON DELETE CASCADE`, so 0.1.24's RetentionService deletes still work with V6 rows present [VERIFIED: V6__interest_scoring.sql].
- Redis: the only `@Cacheable` is `raindrop-collections` [VERIFIED: grep], so a cross-version cache-shape risk is negligible. Flush Redis only if deserialization errors appear (01-04 precedent).
- `deploy.sh` uses `--history-max 3` [VERIFIED: deploy.sh:33]. At the 0.2.0 deploy the 0.1.24 revision is the previous one; after 0.2.1 it is still within 3. A fourth deploy prunes it. Capture `helm history myfeeder -n myfeeder` in preflight to record the revision number. Either `helm rollback myfeeder <rev>` (D-03; it also restores the 0.1.24 chart and secret) or `./deploy.sh 0.1.24` (01-04 precedent) works.

### pg_dump (D-03)
- `PGPASSWORD="$MYFEEDER_PG_PASSWORD" pg_dump -h pg.bartram.org -U myfeeder -d myfeeder -Fc -f "$HOME/.cache/myfeeder-phase07/myfeeder-pre-0.2.0.dump"`, then `chmod 600` and `pg_restore --list … | head` to prove it is readable. Keep it outside git (it holds article content and integration config).
- pg_dump cannot dump a server newer than its own major version [CITED: postgresql.org/docs/current/app-pgdump.html]. The client is 18.6 [VERIFIED: `pg_dump --version`]. Preflight `psql -Atc 'show server_version'`; if the server is newer than 18, dump from a `kubectl run` pod with a matching `postgres:<major>` image.
- The access path is LAN psql to `pg.bartram.org` (HOMELAB.md: "Prod DB access: `psql -h pg.bartram.org`"; split-horizon DNS resolved to 192.168.44.206 here). Role/db: `myfeeder`/`myfeeder` [VERIFIED: helm values.yaml `externalPostgres.username: myfeeder`, `database: myfeeder`].

### Key rollout checks (D-02)
- `MYFEEDER_TYPESAFE_API_KEY` is **unset** in the research shell [VERIFIED: `test -n`]. It is a user_setup item.
- 02-REVIEW WR-05 (deferred): `helm --set` strips `\` and fails on `,`. Preflight without printing: `[[ "$MYFEEDER_TYPESAFE_API_KEY" != *[,\\]* ]]`.
- Post-deploy, without printing: `kubectl -n myfeeder get secret myfeeder-secret -o jsonpath='{.data.myfeeder-typesafe-api-key}' | base64 -d | shasum -a 256` must equal `printf %s "$MYFEEDER_TYPESAFE_API_KEY" | shasum -a 256`. Secret name: fullname `myfeeder` + `-secret` [VERIFIED: _helpers.tpl:16-17, app-secret.yaml].
- Startup log must **not** contain `TypeSafe Jev not configured; interest scoring disabled` (TypeSafeConfig.java:47). `/status` must show `configured:true, coldStart:true`.
- One billed preview call (Interests dialog → preview) proves the key authenticates *before* the user saves the rubric. A bad key otherwise shows up as a breaker OPEN (401/403 are recorded, application.yaml:72).

### Throttle levers (D-08)
- Throughput math: concurrency 1 × ~2.6s/call ≈ 23 calls/min ≈ 46 per 2 min. So `sweep-batch-size: 50` per `PT2M` is roughly saturating, and the cap is `sweep-batch-size / sweep-delay` [VERIFIED: application.yaml:36-39; 03-08 SUMMARY "~2.6s"]. Earlier research cites Jev's published limit as 1,200 RPM [CITED: .planning/research/PITFALLS.md:440], so a 429 storm at about 23 RPM is unlikely.
- Ingest-triggered scoring (`InterestScoringListener` → `ScoringQueue.submitIngested`) enqueues all new eligible articles regardless of `sweep-batch-size` [VERIFIED: ScoringQueue.java:44-50]. The single thread still serializes them.
- Emergency lever without a release: `kubectl -n myfeeder set env deploy/myfeeder MYFEEDER_INTEREST_SWEEPBATCHSIZE=20` (and/or `MYFEEDER_INTEREST_SWEEPDELAY=PT5M`). This uses relaxed env binding: uppercase, `.`→`_`, dashes removed [CITED: docs.spring.io/spring-boot/reference/features/external-config.html (relaxed binding from environment variables)]. It restarts the pod, and the next `deploy.sh` reverts it, so encode any lasting change in yaml. Emergency stop: re-run `./deploy.sh 0.2.0` with the key unset (blank key → sweep and scorer no-op).

## OPS-03: CLAUDE.md delta (root `CLAUDE.md`)

Stale or missing, with sources [VERIFIED: grep -n of CLAUDE.md in this session]:

| Line(s) | Now | Change |
|---|---|---|
| 40 | "AI: Spring AI Anthropic starter on the classpath (not yet used by any application code)" | Still true (no app code references Anthropic, grep empty). Add a sibling bullet: **Jev**: `org.springaicommunity:spring-ai-starter-typesafe:0.1.0` (explicit version, not in any BOM, build.gradle.kts:45). myfeeder owns the `TypeSafeClient` bean |
| 50 (config/) | `MyfeederProperties, RestClientConfig, SpaForwardController` | + `TypeSafeConfig` (app-owned `TypeSafeClient`, jev retry interval), `InterestScoringConfig` (`jev-score` executor), and `JevEventLogging` if added |
| 53 (repository/) | lacks the interest stores | + `ArticleScoreStore`, `ArticleFeedbackStore`, `InterestScoreQueries` |
| 54 (service/) | no interest services | + `InterestService, InterestStatusService, InterestPreviewService, InterestRescoreService, ArticleScoringService, ArticleFeedbackService, ScoringQueue, InterestScoringListener, InterestQuestions, ArticleStateBuilder, PriorityService, ScoreBreakdowns, …` |
| 55 (integration/) | Raindrop only | + `JevApiClient/JevApiClientImpl` (`@CircuitBreaker(name="jev")` + `@Retry(name="jev")`), `JevJudgment`, `JevNotConfiguredException` |
| 59 (scheduler/) | FeedPollingScheduler only | + `InterestScoringSweep` |
| 99 (Flyway list) | stops at V5 | + `V6__interest_scoring.sql` |
| 113, 128 (deploy) | needs PG + Anthropic (Raindrop optional) | + `MYFEEDER_TYPESAFE_API_KEY` optional (blank → scoring disabled, app still starts; deploy.sh:18-22). Add `-Prelease.versionIncrementer=incrementMinor` for minor releases |
| 147 (status) | `{configured, breakerState, coldStart, eligibleUnscored, failed}` | + `tiers {high, neutral}` from `myfeeder.interest.blend.tiers.*` |
| new section "Jev / scoring behaviors" | — | see list below |

New content, each fact with its source file:
- **App-owned client bean:** `TypeSafeConfig` defines `TypeSafeClient`, so the starter's `@ConditionalOnMissingBean` backs off; a blank key never crashes startup. It uses the Supplier key overload, sets `baseUrl` explicitly (no `TYPESAFE_*` env fallback), and gets a Jev-only Reactor Netty request factory on a `clone()` of the auto-configured `RestClient.Builder` (connect 5s, read = `spring.ai.typesafe.timeout` 30s) [VERIFIED: TypeSafeConfig.java:23-61].
- **Single retry layer:** `spring.ai.typesafe.retry.max-retries: 0`. Resilience4j `jev` retry has 3 attempts, 1s then 2s, and a 429 `retryAfterMs` is honored, clamped to [0, 10s] by `jevRetryInterval` [VERIFIED: application.yaml:20, 90-97; TypeSafeConfig.java:40-84]. The breaker is outer (aspect order 1 vs 2), with auto OPEN→HALF_OPEN after 60s, a slow-call threshold of 15s, and window 20/min 10 [VERIFIED: application.yaml:57-80]. Never re-enable SDK retries.
- **Executor:** bean `interestScoringExecutor` (thread prefix `jev-score-`), core = max = `myfeeder.interest.concurrency` (1), `queue-capacity` 1000, and the default abort policy is kept on purpose (`ScoringQueue` catches `TaskRejectedException` and releases the id). `defaultCandidate = false` so Boot's `applicationTaskExecutor` survives; inject by `@Qualifier(InterestScoringConfig.EXECUTOR)`. No `@Async` [VERIFIED: InterestScoringConfig.java:7-39].
- **Sweep:** `@Scheduled(fixedDelay = sweep-delay PT2M, initialDelay = sweep-initial-delay PT1M)`, enqueueing `min(queue room, sweep-batch-size 50)` newest first. It skips when unconfigured, OPEN/FORCED_OPEN or cold start; HALF_OPEN runs [VERIFIED: InterestScoringSweep.java:20-69].
- **Eligibility:** `read = false AND COALESCE(published_at, fetched_at) > now − window-days (14)`, needing no score row or a FAILED row with attempts < 3 [VERIFIED: ArticleScoreStore.java:37-42]. Transient failures (429, 5xx, connection, open breaker, missing key, 401/403) write no row and use no attempt [VERIFIED: ScoringFailure.java:25-38].
- **Tier config** and how to tune it (config only; 07-CALIBRATION.md; the replay script).
- **Throttle levers** (sweep-batch-size/sweep-delay) and the "never add an SDK retry" rule.

## Common Pitfalls

### Pitfall 1: Release blocked or mis-versioned by the worktree layout
**What goes wrong:** `release` fails on the staged `.envrc`, fails "ahead of remote", or computes the wrong version on `sbartram/main`.
**How to avoid:** Preflight in `/Volumes/data2/scottb/dev/bartram/myfeeder`: clean tree, merge `--no-ff`, `git push origin main`, `./gradlew currentVersion -q | grep 'Project version'` shows `0.1.25-SNAPSHOT`, then `release -Prelease.versionIncrementer=incrementMinor`. Assert `VERSION == 0.2.0` before any build.
**Warning signs:** `Looking for uncommitted changes.. FAILED`; a `-SNAPSHOT` version after release.

### Pitfall 2: A partial rubric at backfill start
**What goes wrong:** Any single save ends cold start (blank profile AND zero topics). The sweep runs within 2 min, and articles scored before the remaining topics are saved never get those topics' nouls (R5; `sameRubric` only guards in-flight calls, ArticleScoringService.java:144-156). Fixing that with Re-score bills the whole window again.
**How to avoid:** Have the full rubric ready (the profile and topics from the Phase 3 spike live in `$HOME/.cache/myfeeder-phase03/calibration-input.json`, outside the repo) and save profile + all topics in one quick sitting, or POST them back-to-back with a short curl script. Start the watch script **before** the first save (CONTEXT §Specifics).

### Pitfall 3: Nothing in the logs to grep
**What goes wrong:** The watch script reports "0 retries, 0 transitions" because no such log lines exist at INFO (retry interval function: no logging; transient failure: DEBUG; no breaker event consumer) [VERIFIED: grep of log calls].
**How to avoid:** Ship Pattern 3 in 0.2.0, or else raise `logging.level.org.bartram.myfeeder.service.ArticleScoringService=DEBUG` for the launch (it still won't show absorbed retries).

### Pitfall 4: A 60s status poll misses a short OPEN
**What goes wrong:** OPEN lasts `wait-duration-in-open-state: 60s`, then auto HALF_OPEN, so a 60s poll can miss it entirely.
**How to avoid:** Use the breaker transition log line as the authoritative evidence; the status poll is corroboration.

### Pitfall 5: "Drains to ~0" never reaches 0
**What goes wrong:** New arrivals and transiently failed articles keep `eligibleUnscored` above 0.
**How to avoid:** Define the pass numerically. Eligible unscored articles with `fetched_at` before watch start = 0 (read-only SQL), and `eligibleUnscored` stays roughly flat at a level comparable to one poll cycle's arrivals.

### Pitfall 6: The key is mangled by `helm --set` (WR-05, deferred)
**How to avoid:** Preflight the character check and the post-deploy sha256 comparison (see §Key rollout checks).

### Pitfall 7: Slow calls open the breaker, not 429s
**What goes wrong:** `slow-call-duration-threshold: 15s` at a 50% rate over 20 calls. A retried logical call counts as one outcome whose duration includes every attempt and wait (up to 2 × 10s 429 waits plus up to 3 × 30s timeouts), so a few retried calls read as slow.
**How to avoid:** The transition log line includes failure rate and slow-call rate (Pattern 3), so a slow-call OPEN is distinguishable. The response is the same D-08 throttle.

### Pitfall 8: Low-end compression, not top clustering
**What goes wrong:** In the Phase 3 spike, the top article scored 57.5 at profile-points 100 and "everything else below 5.1"; topics rarely cross noul 0.5 [VERIFIED: 03-CALIBRATION.md "Ranking (points)…" and Notes]. At 70/40 "high" would be nearly empty. The badge is clamped to 0..100 and negatives become 0, so a large mass sits at exactly 0.
**How to avoid:** Start from the baseline percentiles, set `high` near P80–P90 and `neutral` near P50–P60 of the display score, then check the histogram. If topics are silent, raw ≈ profile-points × p, so only the ratios high/pp and neutral/pp matter. **Prefer moving the thresholds, which never changes the sort order**, over changing profile-points, which also changes profile-vs-topic balance and, above 100, saturates many badges at 100. Report `at_zero` and `at_100` counts. If more than 60% of articles are at about 0, neutral 30–40% is unreachable without making "neutral" meaningless. Say so in 07-CALIBRATION.md and let the user choose at the checkpoint.

### Pitfall 9: The population shifts as the user reads
**What goes wrong:** Reading high articles removes them from "scored unread", so the high share falls over time.
**How to avoid:** Run the replay soon after the drain (D-12) and also report the same stats over **all SCORED articles inside the window, read or unread**, as a stability cross-check. The target (D-10) stays on unread.

### Pitfall 10: Ties in the bottom 20
**What goes wrong:** Many articles tie at raw ≤ 0, so "bottom 20" is arbitrary among them.
**How to avoid:** Order by `raw_n ASC, date DESC, id DESC` (mirror KEYED_ORDER). Show `raw_n` beside each title and note the tie count.

### Pitfall 11: Personal data in committed evidence
**What goes wrong:** 07-CALIBRATION.md or 07-BACKFILL.md leaks the profile text or topic descriptions, or the dump lands in git.
**How to avoid:** Follow the 03-CALIBRATION.md rule: titles, `topic_<id>` keys and numbers only, never the profile, descriptions or key. The dump and raw logs go in `$HOME/.cache/myfeeder-phase07/`. (Article titles were already committed in 03-CALIBRATION.md; the origin is a GitHub repo, so the user may want to review titles at the checkpoint.)

## Code Examples

See Patterns 1–5 above. All in-repo values used there are quoted from source read this session:
- `application.yaml:35-45` (verbatim): `window-days: 14`, `concurrency: 1`, `queue-capacity: 1000`, `sweep-batch-size: 50`, `sweep-delay: PT2M`, `sweep-initial-delay: PT1M`, `profile-points: 100`, `learn-rate: 2`, `learned-cap: 20` [VERIFIED].
- `application.yaml:66-71`: `wait-duration-in-open-state: 60s`, `slow-call-duration-threshold: 15s`, `slow-call-rate-threshold: 50`, `automatic-transition-from-open-to-half-open-enabled: true` [VERIFIED].
- `application.yaml:20`: `max-retries: 0`; `:90` `max-attempts: 3`; `:93` `wait-duration: 1s` (jev retry) [VERIFIED].
- `InterestScoreQueries.java:45` `UNREAD_SCOPE = "a.\"read\" = false"`; `:60-61` `INTEREST_SCORE = "CASE WHEN b.raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END"`; `:314` `private static String blendCte(String scope)` [VERIFIED].
- `utils/interest.ts:26-33` `export type Tier = 'high' | 'neutral' | 'low'` and `tierOf`: `if (score >= 70) return 'high'` / `if (score >= 40) return 'neutral'` / `return 'low'` [VERIFIED]. CSS classes `.interest-badge.tier-high|tier-neutral|tier-low` (App.css:288-300) [VERIFIED].
- `InterestStatus.java:20` `public record InterestStatus(boolean configured, String breakerState, boolean coldStart, long eligibleUnscored, long failed) {}` [VERIFIED].
- `InterestScoringConfig.java:28` `EXECUTOR = "interestScoringExecutor"`; `:37` `setThreadNamePrefix("jev-score-")` [VERIFIED].
- `TypeSafeConfig.java:41` `MAX_RETRY_AFTER_MS = 10_000L`; `:47` log text `"TypeSafe Jev not configured; interest scoring disabled"` [VERIFIED].

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Tiers hardcoded in `tierOf` | Server config on `/status`, context-fed badge | This phase | Tuning = yaml + release |
| Release with default patch increment | CLI `-Prelease.versionIncrementer=incrementMinor` for 0.2.0 | This phase | Document in CLAUDE.md |
| Rollback = `./deploy.sh <prev>` (01-04) | `helm rollback` or `deploy.sh`; both valid, schema now forward-only | V6 | `*:future` makes both safe |

**Deprecated/outdated:** CLAUDE.md line 99's Flyway list (missing V6) and the deploy lines missing `MYFEEDER_TYPESAFE_API_KEY`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Registry `circuitBreaker("jev")`/`retry("jev")` return the same instances the annotation aspects use | Pattern 3 | Logger silent; caught by the proposed OutputCapture test |
| A2 | Relaxed env binding also applies to `${myfeeder.interest.sweep-delay}` placeholders (`MYFEEDER_INTEREST_SWEEPDELAY`) | Throttle levers | The emergency delay lever doesn't take; the batch-size lever (bound via `MyfeederProperties`) still works; verify by the drain rate |
| A3 | Prod Postgres major version ≤ 18 (pg_dump 18.6 client) | pg_dump | The dump fails with a version mismatch; use a `kubectl run` pod with a matching image |
| A4 | The prod eligible backlog is a few hundred to a few thousand articles (drain about 1–2 h at ~23/min) | Throttle levers / soak | Longer watch; cost still ≈ $0.0001/article per PITFALLS.md:93 |
| A5 | The real distribution resembles the 20-article Phase 3 spike (compressed low, topics silent) | Pitfall 8 | Candidate grid must be re-centered after the baseline run (the method handles either case) |
| A6 | The user accepts adding the Jev event logger as app code in 0.2.0 | Pattern 3 | Without it, the D-05 pass bar is only partially evidenced |
| A7 | The user will commit, unstage or stash the staged `.envrc` in the main worktree | Release | `verifyRelease` fails; the plan must stop and ask |

## Open Questions

1. **Add the Jev event logger (Pattern 3) to 0.2.0?**
   - What we know: without it, retries and breaker transitions leave no INFO log line, and polling can miss a 60s OPEN.
   - Recommendation: yes. It is small, permanent and tested, and it is not an Actuator metric. Planner: include it in Wave 1 and note the D-06 interpretation for the user.
2. **The staged `.envrc` in the main worktree.**
   - Recommendation: the preflight detects it and asks the user (never commits or prints it on their behalf).
3. **Prod server version, backlog size and cluster reachability** were not observable from the research sandbox (kubectl/psql/curl to 192.168.44.x got "no route to host"; DNS resolved).
   - Recommendation: a read-only preflight task captures `show server_version`, `/api/interest/status` (the baseline `eligibleUnscored` is readable after deploy, even in cold start), `helm history`, and `/api/version`.
4. **Does the neutral target (30–40%) survive a zero-heavy distribution?**
   - Recommendation: the replay reports it honestly, and the user decides at the D-11 checkpoint.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java | build/tests | ✓ | 25.0.4 | — |
| Node/npm | frontend build/tests | ✓ | 26.10.0 / 11.19.1 | — |
| Docker | image build, Testcontainers | ✓ | 29.8.0 | — |
| kubectl | deploy verify, logs | ✓ (binary) | 1.37.1 | cluster unreachable from research sandbox; verify at execution |
| helm | deploy/rollback | ✓ | v4.3.0 | — |
| psql / pg_dump | replay, backup | ✓ | 18.6 | `kubectl run` postgres pod if server > 18 |
| jq | watch summary | ✓ | 1.8.2 | — |
| `MYFEEDER_PG_PASSWORD`, `MYFEEDER_ANTHROPIC_API_KEY`, `MYFEEDER_RAINDROP_API_TOKEN` | deploy.sh, psql | ✓ set | — | — |
| `MYFEEDER_TYPESAFE_API_KEY` | D-02 deploy | ✗ unset in this shell | — | **User must provide (blocking for 0.2.0 deploy)** |
| LAN reachability to 192.168.44.204/.206/.71 | watch, replay, deploy | not observed (sandbox) | — | Executor's shell on the LAN |

**Missing dependencies with no fallback:** `MYFEEDER_TYPESAFE_API_KEY` (user_setup).
**Missing with fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest 4 + RTL (frontend) |
| Config file | `build.gradle.kts` (`tasks.withType<Test>`), `src/main/frontend/vitest.config.ts` (setup `src/test/setup.ts`) |
| Quick run command | `./gradlew test --tests "*InterestStatus*" --tests "*DevProfileConfigTest" --tests "*InterestCalibrationReplaySqlTest" --tests "*JevResilienceTest"`; `cd src/main/frontend && npx vitest run src/utils/interest.test.ts src/components/InterestBadge.test.tsx` |
| Full suite command | `./gradlew test && cd src/main/frontend && npm test && npx tsc -b` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| OPS-02 | `tiers` bound from yaml, defaults 70/40, appended last on status | unit | `./gradlew test --tests "*InterestStatusServiceTest"` | ✅ extend |
| OPS-02 | `/api/interest/status` JSON has `tiers.high/neutral` | integration | `./gradlew test --tests "*InterestApiIntegrationTest"` (add `jsonPath("$.tiers.high").value(70)`) | ✅ extend |
| OPS-02 | main ↔ test+dev yaml parity incl. tier keys and tuned values | unit (no Docker) | `./gradlew test --tests "*DevProfileConfigTest"` | ✅ existing guard |
| OPS-02 | `tierOf(score, tiers)` honors custom thresholds, inclusive at low end | unit | `npx vitest run src/utils/interest.test.ts` | ✅ extend |
| OPS-02 | Badge uses context tiers; default 70/40 without provider | component | `npx vitest run src/components/InterestBadge.test.tsx` | ✅ extend |
| OPS-02 | `useInterestTiers` falls back to 70/40 then uses served tiers | hook | `npx vitest run src/hooks/useInterest*.test.ts` | ❌ Wave 0 (new file) |
| OPS-02 | Replay SQL is the verbatim app blend | unit (no Docker) | `./gradlew test --tests "*InterestCalibrationReplaySqlTest"` | ❌ Wave 0 |
| OPS-02 | Tuned values chosen and approved | manual (D-11 checkpoint) | `07-CALIBRATION.md` exists with histogram, percentiles, top/bottom 20, user approval line | manual |
| OPS-01 | Jev retry/breaker events are logged | integration | `./gradlew test --tests "*JevResilienceTest"` (OutputCapture) | ✅ extend |
| OPS-01 | Tag and image exist; prod runs 0.2.0 | ops smoke | `git ls-remote --exit-code --tags origin refs/tags/v0.2.0 && docker manifest inspect registry.bartram.org/bartram/myfeeder:0.2.0 >/dev/null && curl -sf http://192.168.44.204/api/version \| grep -q '"0.2.0"'` | ops |
| OPS-01 | Configured, breaker CLOSED, backlog drained | ops evidence | `curl -sf http://192.168.44.204/api/interest/status \| jq -e '.configured and .breakerState=="CLOSED"'` + `07-BACKFILL.md` series, log greps, read-only SQL | manual/ops |
| OPS-01 | 0.2.1 deployed with tuned tiers | ops smoke | `curl -sf …/api/interest/status \| jq -e '.tiers.high == <tuned>'` | ops |
| OPS-03 | CLAUDE.md documents the Jev behaviors | doc check | `grep -q 'TypeSafeConfig' CLAUDE.md && grep -q 'jev-score' CLAUDE.md && grep -q 'window-days' CLAUDE.md && grep -q 'max-retries: 0' CLAUDE.md && grep -q 'MYFEEDER_TYPESAFE_API_KEY' CLAUDE.md && grep -q 'tiers' CLAUDE.md && grep -q 'V6__interest_scoring' CLAUDE.md` | doc |

### Sampling Rate
- **Per task commit:** the quick commands for the touched side.
- **Per wave merge:** the full suite (`./gradlew test`, `npm test`, `npx tsc -b`) before any merge to `main`.
- **Phase gate:** full suite green before each release (0.2.0 and 0.2.1), plus the ops evidence files.

### Wave 0 Gaps
- [ ] `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java`: drift guard (needs `blendCte` package-private)
- [ ] `src/main/frontend/src/hooks/useInterest.test.ts` (or `useInterestTiers.test.tsx`): fallback plus served tiers, wrapped in a `QueryClientProvider`
- [ ] Extend `JevResilienceTest` with an OutputCapture assertion for the retry and transition lines (if Pattern 3 is accepted)

## Security Domain

### Applicable ASVS Categories (L1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | single-user homelab app; unchanged |
| V3 Session Management | no | — |
| V4 Access Control | no | `/status` adds non-sensitive integers |
| V5 Input Validation | minimal | tier/blend values are typed config bindings (`int`); no request input added |
| V6 Cryptography | no | — |
| V7 Error Handling & Logging | yes | new log lines carry class names and numbers only, never exception messages, bodies or the key (Phase 2 D-06 rule) |
| V8 Data Protection | yes | dump kept outside git with `chmod 600`; the replay session is read-only; evidence docs carry titles/ids/numbers only |
| V14 Configuration | yes | key via Helm secret; the WR-05 character preflight; sha256 check instead of printing |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| API key echoed in logs, CI output or evidence | Information disclosure | Never `set -x`; `test -n` checks; hash compare; fixed-text logs |
| Accidental prod writes during calibration | Tampering | `PGOPTIONS=-c default_transaction_read_only=on` (verified) |
| Dump or personal rubric text committed | Information disclosure | `$HOME/.cache/myfeeder-phase07/`; follow the 03-CALIBRATION content rule |
| Billing runaway / 429 storm | Denial of service (self) | concurrency 1, sweep cap, breaker, emergency throttle and blank-key stop |
| Key visible on the `helm` argv (`ps`) | Information disclosure | Accepted (single-user workstation, WR-05 deferred per D-04) |

## Sources

### Primary (HIGH confidence)
- Local probes this session: Flyway 11.14.1 rollback probe (V1–V6 then V1–V5, plus negative control); psql `-v` interpolation of the reflected `blendCte` SQL; `PGOPTIONS` read-only rejection; `javap` of spring-boot-flyway-4.0.8, flyway-core-11.14.1 and resilience4j 2.3.0
- In-repo sources read this session: `build.gradle.kts`, `deploy.sh`, `helm/myfeeder/*`, `application.yaml` (main/test/dev), `MyfeederProperties`, `InterestStatus(Service)`, `InterestScoreQueries`, `TypeSafeConfig`, `InterestScoringConfig`, `InterestScoringSweep`, `ScoringQueue`, `ScoringFailure`, `ArticleScoringService`, `ArticleScoreStore`, `DevProfileConfigTest`, `InterestScoreQueriesTest`, `InterestStatusServiceTest`, frontend `utils/interest.ts`, `InterestBadge(.test).tsx`, `hooks/useInterest.ts`, `api/interest.ts`, `App.tsx`; `git show v0.1.24:*`, `git worktree list`, `git merge-base`
- Context7 `/allegro/axion-release-plugin`: version.md (CLI incrementer), dry_run.md, checks.md

### Secondary (MEDIUM confidence)
- Context7 `/tanstack/query`: polling.md (per-observer timers), render-optimizations.md (`select`)
- PostgreSQL docs (pg_dump version compatibility; read-only transaction restrictions)
- Spring Boot docs (relaxed binding from environment variables)
- `.planning/research/PITFALLS.md`, `.planning/phases/03-*/03-CALIBRATION.md`, `01-04-PLAN.md` (release precedent), `02-REVIEW.md` WR-05

### Tertiary (LOW confidence)
- Prod-state assumptions A3–A5 (not observable from the sandbox)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no new dependencies; versions read from build files and jars
- Architecture (tier config, logging, replay): HIGH, APIs verified by javap and probes; context design verified against the test harness
- Release/rollback: HIGH for Flyway and axion wiring (probed/cited); MEDIUM for the prod environment (unreachable here)
- Pitfalls: HIGH for code-derived ones; MEDIUM for the distribution shape (a 20-article spike)

**Research date:** 2026-09-27
**Valid until:** 2026-10-27 (stable stack; re-check the prod facts at execution)
