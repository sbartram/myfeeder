# Phase 12: Calibration & Release - Research

**Researched:** 2026-10-02
**Domain:** Read-only SQL calibration replay (bash + psql + Postgres), drift-guard JUnit tests, yaml tuning, release pipeline (axion, Docker, Helm on k3s)
**Confidence:** HIGH for tooling and release mechanics (read and executed this session); MEDIUM for prod data readiness (prod Postgres was unreachable from this session)

## Summary

This phase uses no new library, framework or package. All the work is in-repo: extend `scripts/interest-calibration-replay.sql` and `scripts/interest-calibration-replay.sh`, extend `InterestCalibrationReplaySqlTest` (the drift guard), possibly change three yaml values, write a calibration note, cut v0.3.1 and update CLAUDE.md. Every pattern already exists in the repo: the Phase 7 replay and calibration note, and the 08-05 release plan with its preflight, blocking approval, publish and verify steps. The research job was to find the constraints those patterns impose on the new work. Several of them are not obvious:

1. **The drift guard hard-codes the current shape of the replay file.** It requires exactly 5 statements that open `WITH learned AS`, in the order unread, unread, unread, window, learned. It also requires exactly 4 `INTEREST_SCORE` copies and 4 checked badge lines. Any new section that repeats the blend breaks these numbers, so the test must change in the same plan.
2. **The driver names its output file after `pp`/`high`/`neutral` only.** The D-06 grid runs 7 engagement candidates at the same 100:70:22, so as written each run would overwrite the last TSV.
3. **WR-02 reproduces.** With `ENGAGEMENT_SAVE_WEIGHT=1` the current driver passed validation and went on to connect to the database. It exited 2, which is also psql's code for a connection failure, so a test cannot tell the two apart by exit code.
4. **The integration tests read the engagement values from the test yaml.** If tuning moves the values, the tuned values must go into `application-dev.yaml`, never into the test yaml. `MyfeederPropertiesValidationTest.shippedMainYamlStarts` hard-codes 0.25/0.5/8.0 for main yaml and must be updated.
5. **Two untracked files would block the release.** axion's `verifyRelease` counted an untracked file as a blocker in Phase 8, and two untracked files sit in the working tree now.

I drafted every new SQL section (dormant, dormant-kind, floor, engaged, backfill-pool, backfill-summary, backfill-learned) and ran it under `default_transaction_read_only=on`. The target was a throwaway `postgres:latest` (18.6) container with real V1–V7 migrations and synthetic data. Every section ran without error and none contains a word from the write-keyword list. The engaged-scope badge for the same article equalled the unread-scope `top` badge (article 124: 146.9 in both), which confirms the badge does not depend on scope. The drafts are in Code Examples.

**Primary recommendation:** Plan 12-01 (autonomous, now) covers the tooling: 6-field candidate syntax `PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP]`, an awk range check that mirrors `isValid` (closes WR-02), file names that carry the engagement values, the seven new sections, and the drift-guard update with a test that only the `engaged` CTE differs. After it come a blocking data-floor gate, the grid replay and an approval of the proposed constants (yaml goes to main plus the dev overlay). Then come the CLAUDE.md update, the gated v0.3.1 release, and the D-13 API-versus-replay proof.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (settled earlier; do not reopen)
- The calibrated build ships as **v0.3.1**, a patch bump with plain `./gradlew release`. Never move or re-tag an existing tag (v0.3.0 is at db5acd7).
- Tuning goes in committed yaml only: main `application.yaml`, plus identical literals in the test yaml or the tuned value in `application-dev.yaml`, as `DevProfileConfigTest` requires. Never use Helm `--set` or env overrides (v0.2.1 D-14).
- The replay stays read-only (`default_transaction_read_only=on`). Its blend lines stay byte-for-byte copies of `InterestScoreQueries`, so `InterestCalibrationReplaySqlTest` must stay green. New SQL aliases and section labels must pass the write-keyword check.
- Startup validation is unchanged: cap 0, or 0 ≤ open < save < 1 and 0 < cap < learned-cap (20) (`ENGAGEMENT_INVALID`). Current values: 0.25 / 0.5 / 8 (Phase 9 D-01).
- The calibration note holds article ids, `topic_<id>` keys and numbers only. It never includes topic names, descriptions or profile text, because the origin repo is public. Raw TSVs stay outside the repo (under `$HOME/.cache/...`).
- There is no new Flyway migration. V7 already holds the whole v0.3.0 schema.

#### Data readiness gate
- **D-01:** The phase is **split**. The CAL-02 tooling (new replay sections, driver changes, drift-guard and keyword tests) is planned and executed **now**, because it needs no prod data. A **blocking checkpoint** then holds the tune, release and docs plans until the data floor is met.
- **D-02:** The data floor is count-based: at least **30 engaged SCORED articles** and at least **3 non-negative topics with nonzero `eng_raw`**. A read-only count query (or the replay's own sections) checks it at the checkpoint.
- **D-03:** If the floor is still unmet about **6 weeks after the v0.3.0 release** (around 2026-11-11), run the replay anyway and record that the data was thin. Keep 0.25 / 0.5 / 8, release v0.3.1, and mark the engagement constants "revisit" in the note.

#### Tuning targets
- **D-04:** The goal is **nudge, not reshuffle**. With engagement on versus off (cap 0), the high-tier share of scored unread articles moves by **at most about 5 percentage points**. A few topics may approach the cap, but none should be pinned at it unless it was clearly engaged often. If no candidate beats the defaults on this rule, keep 0.25 / 0.5 / 8.
- **D-05:** Only the **engagement constants** are tuned. The replay reports the tiers with engagement on and off, but 70 / 22 does not move, and near-miss stays 0.35.
- **D-06:** Candidate grid, one TSV per run:
  - cap 0 (off)
  - the defaults 0.25 / 0.5 / 8
  - cap {4, 8, 12} × save {0.5, 0.75}, with open = save / 2

  That is roughly 7 runs, all at the live tiers 100 / 70 / 22.

#### Backfill simulation & ENG-F4 / ENG-F5
- **D-07:** The simulated backfill treats every article with `starred = true` or any `board_article` row (Read Later included) as a **save** (save-weight), unioned with the real `article_engagement` rows. The live CTE's rules still apply: articles with a vote are excluded, only SCORED articles count, and negative-base topics are skipped.
- **D-08:** The backfill sim is a **separate, labeled section** (e.g. `backfill-sim`). Only its `engaged` CTE differs, by a UNION of star and board rows. The drift guard must prove that exactly that one CTE differs from the verbatim blend, while every other section stays byte-identical.
- **D-09:** The ENG-F4 call uses a **dormant share rule**. The note says "revisit" if dormant (engaged but never SCORED) articles make up at least **25%** of all engaged articles, and "keep" otherwise. Nothing is built for it here.
- **D-10:** The ENG-F5 call uses an **impact rule**. The note recommends a real backfill only if the simulation stays within the D-04 nudge target: high share moves by at most 5 points and few topics are pinned at the cap. Nothing is executed here; a real backfill would be its own migration or quick task.

#### Release & prod proof
- **D-11:** **Merge `gsd/phase-11-gap-discovery` into `main` with `--no-ff` before Phase 12 execution starts**, then branch Phase 12 from main. The release itself still waits for the D-01 gate.
- **D-12:** The release has a **blocking approval checkpoint**, following 08-05. First a dry run shows the computed version, the image tag and the Helm values diff, and the user approves. Only then do `./gradlew release`, `clean bootJar`, `docker build --provenance=false`, the push and `./deploy.sh <version>` run. There is no migration this time, so it is lower risk than V7, but still gated. — **Reversibility:** one-way — a pushed release tag is never moved or re-tagged, and the image becomes the prod rollback point (Helm rollback to the v0.3.0 revision stays available).
- **D-13:** SC-3 is proven by an **API plus replay cross-check**:
  - Pick 3–5 engaged SCORED article ids.
  - `GET /api/articles/{id}` `interestScore` must equal the replay badge at the shipped constants.
  - The "Why N?" breakdown must show a nonzero `engagementWeight` on a matched topic.
  - Also confirm a clean startup in `kubectl logs` and grep for `Jev `.
  - No manual UI UAT is required.

### Claude's Discretion
- How the driver accepts engagement candidates (extend the `PP:HIGH:NEUTRAL` syntax or loop over env values). It must keep the validate-before-connect rule and **close Phase 9 WR-02**: reject out-of-range engagement values the same way the app's startup validation does.
- The exact SQL, labels and columns of the new sections (dormant, at-cap, on/off histogram, backfill-sim), within the read-only and drift-guard rules.
- The calibration note's layout, which mirrors `07-CALIBRATION.md` (Run table, Baseline, Candidates, Learned model, Decision). The note lives at `.planning/phases/12-calibration-release/12-CALIBRATION.md`.
- The CLAUDE.md audit for SC-4. Most of it is already documented (V7, capture rules, the learned model). Add the engagement tuning levers and the calibration outcome, and keep CLAUDE.md consistent with the tuned values.

### Deferred Ideas (OUT OF SCOPE)
- ENG-F4 (make dormant engaged articles eligible for one Jev call): decide only, from the D-09 rule; building it is a future milestone item.
- ENG-F5 (real backfill of stars and boards into `article_engagement`): decide only, from the D-10 rule; it would be its own migration or quick task.
- Re-tuning tier thresholds or near-miss with engagement on: out of scope (D-05). Revisit if the on/off histogram shows the D-10 bands drifting.

#### Reviewed Todos (not folded)
- **Tune Raindrop resilience** (`.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`): matched by keyword only and unrelated to engagement calibration. It stays a separate `/gsd-quick` task.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CAL-02 | The replay reports dormant (engaged-but-unscored) engagement, topics at the cap, the tier histogram with engagement on and off, and a simulated backfill | New sections `dormant`, `dormant-kind`, `floor`, `engaged`, `backfill-pool`, `backfill-summary` and `backfill-learned`, plus an `eng_articles` column on `learned` and `backfill-learned`. All were drafted and executed read-only on PG 18.6 (Code Examples). "Engagement off" is the D-06 cap-0 run of the same verbatim replay; the 6-field candidate syntax runs the whole grid in one call. Drift-guard changes are in Pattern 3 |
| CAL-03 | The engagement weights and cap are tuned from prod data, committed in yaml and documented in CLAUDE.md | The data-floor gate (`floor` row), the grid of 7 candidates, the D-04 rule and an approval checkpoint. Yaml placement: main + `application-dev.yaml`, with the test yaml kept at 0.25/0.5/8 (Pitfall 4). `MyfeederPropertiesValidationTest` must be updated, and CLAUDE.md lines 188 and 219 need edits (SC-4 audit) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

These directives apply to this phase and have the same weight as locked decisions:

- **Git:** non-trivial work goes on a feature branch; merges to main use `--no-ff`; push only when the user asks (the release approval covers the push). Never `git checkout <sha>` (detached HEAD). [CITED: ~/.claude/CLAUDE.md §0.5]
- **Release order:** `./gradlew release` (patch is the default) **before** `./gradlew clean bootJar`; take `VERSION` from `./gradlew currentVersion -q | grep 'Project version' | awk '{print $NF}'`; run `docker build --provenance=false`, `docker push` and `./deploy.sh $VERSION`, always with the version passed explicitly; then `kubectl -n myfeeder rollout status` and the logs. Never use `bootBuildImage`. [CITED: CLAUDE.md § Deployment]
- **Release push:** `release` and `pushRelease` push through the `gitPushRelease` Exec task (git CLI). If the push fails with "Unable to verify locks", the fix is `git config lfs.https://github.com/sbartram/myfeeder.git/info/lfs.locksverify false`. Never use `--no-verify`. [CITED: CLAUDE.md § Gotchas]
- **deploy.sh** passes every key through `helm --set`, so no key may contain `,` or `\`. It blanks `MYFEEDER_TYPESAFE_API_KEY`/`MYFEEDER_RAINDROP_API_TOKEN` when they are unset, which would silently disable scoring or Raindrop in prod. [VERIFIED: deploy.sh]
- **Never tune through Helm `--set` or env overrides (D-14).** Tuned values go in main `application.yaml` and `src/test/resources/application-dev.yaml`. The test yaml keeps the values its fixtures assume, and `DevProfileConfigTest` enforces the split. [CITED: CLAUDE.md § Tier thresholds and tuning]
- **Never export `SPRING_AI_TYPESAFE_*` or `SPRING_PROFILES_ACTIVE=dev`** in the shell that runs `./gradlew test` (it would call the billed Jev API). [CITED: CLAUDE.md § Gotchas]
- **The replay must never read `topic_suggestion_dismissal`.** `V7EngagementMigrationTest.rankingSqlReadsEngagementButNotDismissals` greps the whole replay file, **comments included**. [VERIFIED: V7EngagementMigrationTest.java:210-218 — `assertThat(replay).doesNotContain("topic_suggestion_dismissal");`]
- **Close tracked items in the same commit:** when a plan fixes WR-02, set it to `fixed` in `09-REVIEW-DISPOSITION.md` and update the STATE.md blocker line in the same commit. [CITED: .claude/CLAUDE.md § GSD Workflow Enforcement]
- **OpenWiki pages** are generated, so do not hand-edit them. [CITED: CLAUDE.md § OpenWiki]
- **Gradle, not Maven.** Run tests with `./gradlew test -x npmBuild -x npmInstall`, as in the 08-05 preflight. [CITED: 08-05-PLAN.md Task 1]
- **Simplicity:** no speculative abstractions; touch only what the request needs. [CITED: ~/.claude/CLAUDE.md §2-3]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Engagement and backfill simulation, dormant/floor counts | Database (read-only SQL replay) | Workstation script (bash driver) | The blend lives in SQL (`InterestScoreQueries`). The replay reproduces it verbatim and runs read-only against prod |
| Candidate validation (WR-02) | Workstation script (driver) | — | Must run before any connection; it mirrors `MyfeederProperties.Engagement.isValid` |
| Drift and read-only guard | Test suite (plain JUnit) | — | Rebuilds the expected SQL from the Java constants; no Docker |
| Tuned constants | Config (yaml) | API/Backend (`MyfeederProperties` validation at startup) | Applied at query time; startup refuses invalid values |
| Release and deploy | Workstation → registry → k3s (Helm) | — | The existing pipeline; no chart, Dockerfile or migration change since v0.3.0 |
| SC-3 proof | API/Backend (`GET /api/articles/{id}`) | Database (replay `engaged` section) | The served badge must equal the replay badge at the shipped constants |

## Standard Stack

No new dependency. The phase uses what the repo already has.

### Core
| Tool | Version (this workstation) | Purpose | Why |
|------|---------------------------|---------|-----|
| psql | 18.6 (Homebrew) [VERIFIED: `psql --version`] | Runs the replay with `-v` variables | Existing driver |
| Postgres (prod and Testcontainers) | `postgres:latest` = 18.6 [VERIFIED: throwaway container `select version()`] | Target of the replay | Same image as the tests |
| bash + BSD awk | awk 20200816 [VERIFIED: `awk --version`] | Driver validation, including float range checks | bash has no float compare; awk's `-v` numeric strings compare numerically (tested this session, see Code Examples) |
| JUnit 5 + AssertJ (via Spring Boot test starters) | BOM-managed | Drift guard, driver tests | Existing `InterestCalibrationReplaySqlTest` |
| Testcontainers PostgreSQL | BOM-managed | Optional end-to-end replay run test | `TestcontainersConfiguration` already exposes a `PostgreSQLContainer` bean |
| axion-release, Docker 29.8.1, Helm v4.3.0, kubectl v1.37.1, jq 1.8.2 | [VERIFIED: version commands] | Release pipeline and evidence | Existing pipeline |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Two runs (cap 0 vs candidate) for "engagement on/off" | One statement that builds a second "off" raw from `contrib.w_thumbs` | `w_thumbs` equals the cap-0 weight by construction, but the off formula would be hand-written SQL that the drift guard does not cover. The two-run approach is fully verbatim, `InterestScoreQueriesZeroEngagementTest` proves cap 0 equals v0.2.1, and CONTEXT already names cap 0 as the "off" histogram. **Use two runs.** |
| 6-field candidate syntax | A shell loop over `ENGAGEMENT_*` env values | A loop validates one run at a time (it breaks validate-all-before-any-connect across the grid) and still needs distinct file names. **Use the 6-field syntax.** |
| `helm diff` plugin for the D-12 values diff | `helm get values ... -o json \| jq '{app}'` plus `git diff v0.3.0 -- helm/` | The plugin is not installed [VERIFIED: `helm plugin list` is empty]. jq filtering is enough, because `app.image.tag` is the only intended change |

**Installation:** none.

## Package Legitimacy Audit

This phase installs no external package (npm, pip, cargo or Maven). The gate does not apply.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                 (workstation, LAN + Orca Local Network permission)
 candidates ──► interest-calibration-replay.sh
 PP:HI:NE[:OPEN:SAVE:CAP]  │ 1. regex every candidate + env value
                           │ 2. awk range check (mirrors isValid; WR-02)
                           │ 3. require MYFEEDER_PG_PASSWORD
                           ▼ (only now) psql, PGOPTIONS=default_transaction_read_only=on
                 interest-calibration-replay.sql  ──reads──► prod Postgres (pg.bartram.org)
                           │                                   article, article_score,
                           │                                   article_topic_score, interest_topic,
                           │                                   article_feedback(_topic),
                           │                                   article_engagement, board_article
                           ▼
        $OUT_DIR/replay-pp..-hi..-ne..-op..-sv..-cap...tsv   (one per candidate, outside repo)
                           │
          ┌────────────────┼─────────────────────────────┐
          ▼                ▼                              ▼
   floor row ──► D-02 gate   summary(cap0) vs summary ──► D-04 rule   dormant ──► D-09 (ENG-F4)
   (blocking checkpoint;     learned eng_at_cap/eng_articles         backfill-summary/-learned ──► D-10 (ENG-F5)
    D-03 fallback 11-11)              │
                                      ▼
                       proposal ──► user approval ──► yaml (main + dev overlay) ──► 12-CALIBRATION.md
                                                                   │
                                                                   ▼
               dry run (version, image tag, helm values tag only) ──► approval (D-12, one-way)
                                                                   │
                       merge --no-ff → push main → gradlew release → clean bootJar → docker build/push → deploy.sh 0.3.1
                                                                   │
                                                                   ▼
               D-13: replay `engaged` section at shipped constants  ==  GET /api/articles/{id}.interestScore
                     and a TOPIC row with engagementWeight > 0; clean startup log; grep 'Jev '
```

### Recommended Plan Structure

| Plan | Wave | Autonomous | Content |
|------|------|-----------|---------|
| 12-01 Replay tooling (CAL-02) | 1 | yes | Driver: 6-field candidates, WR-02 awk check, file names that carry the engagement values. SQL: the seven new sections plus `eng_articles`. Drift-guard updates and a backfill-only-differs test. Optional Testcontainers run test. Mark WR-02 fixed in `09-REVIEW-DISPOSITION.md` and in STATE.md |
| 12-02 Gate, grid, proposal, yaml, note (CAL-03) | 2 | no | Task 1 is the blocking `floor` gate (re-runnable; D-03 fallback on or after 2026-11-11). Then the grid replay (7 runs), the proposal with the D-04/D-09/D-10 calls, the approval checkpoint, the yaml, the validation-test update and `12-CALIBRATION.md` |
| 12-03 CLAUDE.md (SC-4) | 2 (after 12-02) | yes | Audit and edits (see the SC-4 audit), done before the release so the tag carries them |
| 12-04 Release v0.3.1 + prod proof (SC-3) | 3 | no | Preflight dry run, D-12 approval, publish, D-13 cross-check, the "Shipped in 0.3.1" section of the note |

D-11 (merge Phase 11 into main, then branch Phase 12 from main) must happen before 12-01 executes. Make it an explicit precondition or the first task of 12-01: a `--no-ff` merge in the main worktree, then `git switch -c gsd/phase-12-calibration-release main`. The current branch holds the Phase 12 context commits, and those come along with the merge.

### Pattern 1: Candidate syntax and validate-before-connect (closes WR-02)
**What:** Accept `PP:HIGH:NEUTRAL` (engagement from `ENGAGEMENT_*` env, backward compatible) or `PP:HIGH:NEUTRAL:OPEN:SAVE:CAP`. Validate every candidate's shape, every env value, and every candidate's engagement range before checking the password and before any connection.
**Order:** candidate regex → env numeric regexes (existing) → `LEARNED_CAP`/`WINDOW_DAYS` integer regex (existing) → awk range check per candidate (new) → password → connect. The awk check must come after the numeric regexes, because awk compares non-numeric strings as strings.
**Output name:** `replay-pp${pp}-hi${high}-ne${neutral}-op${open}-sv${save}-cap${cap}.tsv`.

### Pattern 2: A new section that repeats the blend
Every statement that needs badges or learned weights repeats a full verbatim blend line, because there are no temp objects in a read-only session. A section that needs only counts (`dormant`, `dormant-kind`, `floor`, `backfill-pool`) opens no learned CTE, and the drift guard checks it only through the write-keyword test. Keep those sections free of the string `WITH learned AS`, in any case.

### Pattern 3: Drift-guard changes (the most edit-heavy part)
Current hard-coded facts [VERIFIED: InterestCalibrationReplaySqlTest.java, read this session]:
- line 297-299: `assertThat(BLEND_START.matcher(code).results().count()) ... .isEqualTo(5);`
- line 309-310: `.containsExactly("unread", "unread", "unread", "window", "learned");`
- line 311-313: INTEREST_SCORE occurrences `.isEqualTo(4);`
- line 334: `assertThat(badgeLines).as("badge lines checked").isEqualTo(4);`
- lines 147-150: `new Copy("unread", ..., 3), new Copy("window", ..., 1), new Copy("learned", LEARNED_SECTION, 1)`
- lines 167-168: `isEqualTo(4); for (int i = 0; i < 4; i++)` (badge copies)
- lines 241-243: `isEqualTo(5)` / `isEqualTo(4)` (decoy variants)
- line 318: badge lines are found by `lines.get(i).equals(unread) || equals(window)`

Recommended edit:
1. Add test-side constants, as `WINDOW_SCOPE` is today:
   - `ENGAGED_SCOPE = "a.id IN (SELECT g.article_id FROM article_engagement g)"`
   - `ORIGINAL_ENGAGED` = the substring of `LEARNED_CTE` from `"engaged AS ("` up to (not including) `", eng_learned AS ("`
   - `BACKFILL_ENGAGED` = the sim CTE text (Code Examples)
   - `BACKFILL = blendCte(UNREAD_SCOPE).replace(ORIGINAL_ENGAGED, BACKFILL_ENGAGED)`
   - `BACKFILL_LEARNED_SECTION = LEARNED_CTE.replace(ORIGINAL_ENGAGED, BACKFILL_ENGAGED) + " SELECT 'backfill-learned' AS section"`
2. Replace the literal 5/4/4 with named constants. New values, if all recommended sections ship: blend-opening statements **8** (5 + engaged + backfill-summary + backfill-learned), INTEREST_SCORE copies **6** (4 + engaged + backfill-summary), badge lines **6**.
3. Label order (file order recommended): `unread, unread, unread, window, learned, engaged, backfill, backfill-learned`. Match `backfill-learned` with `startsWith(BACKFILL_LEARNED_SECTION)` and check equality first. The original `learned` check (`startsWith(LEARNED_SECTION)`) cannot falsely match a backfill line, because the two differ inside `engaged AS (`.
4. Badge-line loop: also treat lines equal to `blendCte(ENGAGED_SCOPE)` or `BACKFILL` as blend lines whose next line is a badge line.
5. New test `backfillDiffersFromTheAppBlendOnlyInTheEngagedCte`:
   - `ORIGINAL_ENGAGED` occurs exactly once in `LEARNED_CTE` and in `blendCte(UNREAD_SCOPE)`.
   - The file holds `BACKFILL` exactly once and `BACKFILL_LEARNED_SECTION` exactly once.
   - `BACKFILL_ENGAGED` ends with the original's vote-exclusion tail `" WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id) GROUP BY g.article_id)"`, which proves D-07's vote exclusion is kept.
   - `BACKFILL_ENGAGED` contains `FROM article_engagement`, which proves the real rows are unioned in.
   - A one-byte drift outside the engaged span fails `assertEveryCopyIsVerbatim`.
6. Add a `Copy` entry for `engaged` (1) and `backfill` (1) so a one-byte drift in each fails.

### Anti-Patterns to Avoid
- **Section labels or aliases containing write keywords:** the regex is `(?i)\b(insert|update|delete|create|drop|alter|truncate|grant|copy|into)\b` [VERIFIED: InterestCalibrationReplaySqlTest.java:42-43]. `created_at`/`updated_at` pass (no word boundary), but an alias like `copy`, `into_window` or `updated` fails.
- **psql meta-commands** (`\set`, `\if`): `replayIsReadOnly` forbids any code line starting with `\` (line 71). That is why "engagement off" cannot be done by re-setting `:engagementCap` mid-file.
- **`topic_suggestion_dismissal` anywhere in the replay, including comments** (Project Constraints).
- **Titles or topic names in the committed note:** the `top`/`bottom` sections still print titles into the TSV (cache only), and `eff` carries `t.name`. New sections must select `e.id`, never `e.name`. Unlike 07-CALIBRATION.md, the 12 note carries no titles at all.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| "Engagement off" scores | A hand-written w_thumbs blend | The same replay at cap 0 (`...:<open>:<save>:0`) | Verbatim, and proven equal to v0.2.1 by `InterestScoreQueriesZeroEngagementTest` |
| Float range validation in bash | `bc` or string compares | `awk -v ... 'BEGIN { exit !(...) }'` (tested) | `bc` is a new dependency; awk is present and numeric-correct for validated numeric strings |
| Backfill text in the drift guard | A second copy of the whole blend | `blendCte(UNREAD_SCOPE).replace(ORIGINAL_ENGAGED, BACKFILL_ENGAGED)` | Proves only one CTE differs (D-08) |
| Helm values diff | Rendering manifests (`helm upgrade --dry-run`, `helm get values` saved raw) | `helm -n myfeeder get values myfeeder -o json \| jq '{app}'` piped, never saved raw | User-supplied values include `secrets.*` (from `deploy.sh --set secrets.*`), and rendered manifests carry them too |
| Release pipeline | New scripts | The 08-05 plan's preflight, checkpoint and Task 3 steps, with the V7 and dump parts dropped | Proven in prod twice (0.2.1, 0.3.0) |

**Key insight:** every number in the note must come from SQL that is either byte-identical to the app (drift-guarded) or so simple (count/FILTER) that a reviewer can check it at a glance. Anything in between is a silent-drift risk.

## Runtime State Inventory

Not a rename or refactor phase, but tuning and release do touch runtime state:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `article_engagement` in prod (accumulating since 2026-09-30). No write by this phase: the replay is read-only, and the D-13 check only GETs. | None |
| Live service config | Helm release `myfeeder` rev 20 = 0.3.0, rollback rev 19 = 0.2.1, `--history-max 3` [VERIFIED: 08-RELEASE.md; deploy.sh] | Deploying 0.3.1 makes rev 21; rollback target becomes rev 20 |
| OS-registered state | None. Verified: no cron, launchd or systemd units are involved; the replay runs by hand | None |
| Secrets/env vars | The four deploy keys are set in this shell, none with `,` or `\` [VERIFIED: test -n checks, values never printed]. No `MYFEEDER_INTEREST_*` override is expected on the deployment (none was added in 0.2.1/0.3.0) | The preflight re-checks that `kubectl -n myfeeder get deploy myfeeder -o json` has no `MYFEEDER_INTEREST_*` env |
| Build artifacts | `build/libs/*.jar` may be stale; the version is axion-derived from the branch | `./gradlew clean bootJar` on main after `release` |

## Common Pitfalls

### Pitfall 1: Grid runs overwrite each other
**What goes wrong:** All 7 D-06 runs use 100:70:22, and the current name `replay-pp${pp}-hi${high}-ne${neutral}.tsv` [VERIFIED: interest-calibration-replay.sh:69] collides, so only the last run survives.
**How to avoid:** put the engagement values in the file name (Pattern 1), and use a phase-12 `OUT_DIR` (e.g. `$HOME/.cache/myfeeder-phase12/replay`). The default is still `$HOME/.cache/myfeeder-phase07/replay` [VERIFIED: line 61].

### Pitfall 2: WR-02 test cannot use the exit code alone
**What goes wrong:** psql exits 2 on a refused connection, the same code as a validation failure. Reproduced this session: `ENGAGEMENT_SAVE_WEIGHT=1` → driver exit 2 with `psql: error: connection to server at "127.0.0.1", port 1 failed: Connection refused`. The driver had also already created an empty TSV.
**How to avoid:** the WR-02 tests assert that stderr contains the new fixed text (e.g. `invalid engagement constants`) **and** does not contain `psql:`. A "cap 0 with open ≥ save is accepted" test asserts that stderr contains `psql:` (it got past validation) and sets `OUT_DIR` to a JUnit `@TempDir`, so the developer's `$HOME/.cache` never gets files. Optionally, write to `$out.tmp` and `mv` on success, so a failed run leaves no empty TSV.

### Pitfall 3: Engaged articles are mostly read, so they are absent from `top`/`bottom`/`summary`
**What goes wrong:** D-13 needs the replay badge for 3–5 engaged ids, but the existing sections cover only scored **unread** articles. An opened article is usually read.
**How to avoid:** the new `engaged` section uses a blend over `ENGAGED_SCOPE` (read or unread). The badge does not depend on scope: `blended` per article depends only on its own `contrib` plus the global `eff2`. Verified this session: article 124 gave raw 146.9 in both the `engaged` and the unread `top` output.

### Pitfall 4: Tuned values in the test yaml break integration tests
**What goes wrong:** `FeedbackApiIntegrationTest` (e.g. lines 363-364 assert an engagement part of `8.0` with `ENGAGEMENT_CAP`) and `PriorityApiIntegrationTest` (line 242: "a star adds learn-rate 2 x save 0.5 x hinge 0.9 = 0.9") read the values from the test yaml. They set nothing themselves [VERIFIED: grep shows no setCap/setSaveWeight in either].
**How to avoid:** if tuning changes any value, keep `src/test/resources/application.yaml` at 0.25/0.5/8 and add an `engagement:` block under `myfeeder.interest.blend` in `src/test/resources/application-dev.yaml`. `DevProfileConfigTest.devOverlayResolvesEveryMainKeyToMainsValue` accepts this, because the keys exist in main [VERIFIED: DevProfileConfigTest.java:79-97]. Update `MyfeederPropertiesValidationTest.shippedMainYamlStarts`, which asserts `assertEngagement(..., 0.25, 0.5, 8.0)` against main yaml [VERIFIED: MyfeederPropertiesValidationTest.java:48-61]. Leave `defaultsStartAndBindTheD01Values` (Java defaults) alone, unless the plan also changes the Java defaults (not recommended: D-01 defaults stay documented).

### Pitfall 5: Untracked files block `./gradlew release`
**What goes wrong:** axion `verifyRelease` refuses a dirty tree, and in Phase 8 it counted an untracked file (`.planning/milestone.lock`, later ignored at db5acd7) [VERIFIED: 08-RELEASE.md "Lock-ignore commit" row]. The tree now has two untracked, non-ignored files: `docs/superpowers/plans/2026-09-29-metallb-annotation-external-services.md` and `...md.tasks.json` [VERIFIED: git status; `git check-ignore` exit 1].
**How to avoid:** the release preflight asserts `git status --porcelain` is empty in the main worktree. If it is not, stop and ask the user (commit, ignore or move the files). Never use `-Prelease.disableChecks`.

### Pitfall 6: Version computed on the wrong branch
**What goes wrong:** on a branch, axion decorates the version: it reads `0.3.1-gsd-phase-11-gap-discovery-SNAPSHOT` now [VERIFIED: `./gradlew currentVersion -q`]. `deploy.sh` without an argument computes a snapshot version that matches no image.
**How to avoid:** run release and build in the worktree on `main`, and require `VERSION` = `0.3.1` exactly. Always call `./deploy.sh 0.3.1`. main must not have diverged from origin/main, and must be pushed before `release` (axion refuses "ahead of remote" [CITED: 08-05-PLAN.md context]).

### Pitfall 7: Prod is unreachable from Orca sessions
**What goes wrong:** `psql -h pg.bartram.org` failed this session with `No route to host`, while `/usr/bin/curl http://192.168.44.204/api/version` worked (it returned 0.3.0) [VERIFIED: this session]. kubectl and helm hit the same block.
**How to avoid:** every plan touching prod has a `user_setup` / first-task check. If psql, kubectl or helm fail with "no route to host" while curl works, stop and ask the user to grant Orca the macOS Local Network permission (Privacy & Security → Local Network).

### Pitfall 8: Replay vs API race during the D-13 check
**What goes wrong:** engagement is a global per-topic term, so any new engaged article scored between the replay and the GET (sweep every 2 min, ingest scoring) shifts badges of every article matching that topic.
**How to avoid:** run the replay and the GETs back to back; on a mismatch, rerun both once (the 07 cross-check method); report the ids and both values only.

### Pitfall 9: The breakdown carries topic names
**What goes wrong:** `InterestBreakdown.Row` includes `name` [VERIFIED: InterestBreakdown.java:29-31 — `Row(String kind, Long topicId, String name, Integer levelIndex, Double noul, Double hinge, Double weight, BigDecimal exact, long points, Double baseWeight, Double learnedWeight, Double thumbsWeight, Double engagementWeight)`]. Committed evidence would leak topic names.
**How to avoid:** project with jq to `{topicId, engagementWeight}` before writing anything that might be committed. Raw JSON stays under `$HOME/.cache/myfeeder-phase12/`.

### Pitfall 10: Data floor "engaged SCORED" ambiguity
**What goes wrong:** D-02 says "30 engaged SCORED articles", but the learned model also drops articles that have a vote. A count that includes voted articles overstates what the model uses.
**How to avoid:** the `floor` row reports `counted` (engaged, SCORED, no vote: what `eng_learned` actually sums) and gates on it. The `dormant` row reports `scored` and `voted` separately for transparency. Prod currently shows learned 0.0 on all 10 topics (no effective votes) [VERIFIED: `/api/interest/topics/learned` on 0.3.0], so the two counts are probably equal today.

### Pitfall 11: The D-10 comparison base is ambiguous
**What goes wrong:** "the sim stays within the D-04 nudge target" could mean backfill vs off, or backfill vs real engagement.
**How to avoid:** D-04 is defined as on versus off, so compare `backfill-summary.high_pct` with the **cap-0** `summary.high_pct` at the same tiers (the total effect with a backfill). Also report the delta against the real-engagement `summary` for context. Record the rule used in the note.

## Code Examples

All SQL below was executed this session, read-only, on `postgres:latest` (18.6) with V1–V7 applied and synthetic data. The blend lines are generated from the verbatim unread line (`scripts/interest-calibration-replay.sql:34`), not hand-typed. For the real file, generate them the same way, or from `InterestScoreQueries.blendCte(...)` / `LEARNED_CTE`.

### Driver: range check that mirrors `isValid` (tested on macOS awk)
```bash
# Source of truth: MyfeederProperties.java:107-110
#   return cap == 0 || (0 <= openWeight && openWeight < saveWeight && saveWeight < 1
#           && 0 < cap && cap < learnedCap);
engagement_ok() {
  awk -v o="$1" -v s="$2" -v c="$3" -v l="$LEARNED_CAP" \
    'BEGIN { exit !(c == 0 || (o >= 0 && o < s && s < 1 && c > 0 && c < l)) }'
}
CANDIDATE_RE='^[0-9]+:[0-9]+:[0-9]+(:[0-9]+(\.[0-9]+)?:[0-9]+(\.[0-9]+)?:[0-9]+(\.[0-9]+)?)?$'
# ... after the existing env and integer checks, before the password check:
for candidate in "$@"; do
  IFS=: read -r pp high neutral open save cap <<<"$candidate"
  if ! engagement_ok "${open:-$ENGAGEMENT_OPEN_WEIGHT}" "${save:-$ENGAGEMENT_SAVE_WEIGHT}" "${cap:-$ENGAGEMENT_CAP}"; then
    echo "invalid engagement constants: $candidate (cap 0, or 0 <= open < save < 1 and 0 < cap < learned-cap)" >&2
    exit 2
  fi
done
```
Tested results [VERIFIED: this session]: `0.25 0.5 8`, `0.375 0.75 12`, `0.9 0.5 0`, `0 0.5 8` and `0.25 0.50 19.99` are valid. `0.5 0.5 8`, `0.25 1 8`, `0.25 0.5 20` and `0.3 0.25 8` are invalid.

D-06 grid as one call (7 distinct runs; the default appears once):
```bash
OUT_DIR="$HOME/.cache/myfeeder-phase12/replay" scripts/interest-calibration-replay.sh \
  100:70:22:0.25:0.5:0 100:70:22:0.25:0.5:8 100:70:22:0.25:0.5:4 100:70:22:0.25:0.5:12 \
  100:70:22:0.375:0.75:4 100:70:22:0.375:0.75:8 100:70:22:0.375:0.75:12
```

### Engaged CTE: the original and the backfill sim
```sql
-- ORIGINAL (verbatim from InterestScoreQueries.java:124-129, as concatenated)
engaged AS (SELECT g.article_id, MAX(CASE WHEN g.kind = 'OPEN_ORIGINAL' THEN CAST(:engagementOpenWeight AS float8) ELSE CAST(:engagementSaveWeight AS float8) END) AS strength FROM article_engagement g WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id) GROUP BY g.article_id)
-- BACKFILL_ENGAGED (D-07: stars and any board row as saves, unioned with the real rows; vote exclusion kept)
engaged AS (SELECT g.article_id, MAX(g.strength) AS strength FROM (SELECT e.article_id, CASE WHEN e.kind = 'OPEN_ORIGINAL' THEN CAST(:engagementOpenWeight AS float8) ELSE CAST(:engagementSaveWeight AS float8) END AS strength FROM article_engagement e UNION ALL SELECT sa.id, CAST(:engagementSaveWeight AS float8) FROM article sa WHERE sa.starred UNION ALL SELECT ba.article_id, CAST(:engagementSaveWeight AS float8) FROM board_article ba) g WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id) GROUP BY g.article_id)
```
The sim keeps the outer alias `g`, so the `WHERE`/`GROUP BY` tail is byte-identical to the original's. Columns used: `article.starred` [VERIFIED: V1__initial_schema.sql:30 `starred BOOLEAN NOT NULL DEFAULT FALSE`] and `board_article.article_id` [VERIFIED: V2__folders_boards_and_feed_folder.sql:20-26 `article_id BIGINT NOT NULL REFERENCES article(id) ON DELETE CASCADE`, `UNIQUE (board_id, article_id)`]. The kind values come from V7 [VERIFIED: V7__engagement.sql:6 `kind TEXT NOT NULL CHECK (kind IN ('OPEN_ORIGINAL', 'STAR', 'BOARD', 'RAINDROP'))`].

### Count-only sections (no blend; drift guard checks only keywords)
```sql
-- dormant: engaged articles the learned model cannot count (no SCORED row) - ENG-F4 (D-09)
SELECT 'dormant' AS section, count(*) AS engaged, count(*) FILTER (WHERE s.status = 'SCORED') AS scored,
       count(*) FILTER (WHERE s.status = 'SCORED' AND NOT v.voted) AS counted, count(*) FILTER (WHERE v.voted) AS voted,
       count(*) FILTER (WHERE s.status IS DISTINCT FROM 'SCORED') AS dormant,
       count(*) FILTER (WHERE s.article_id IS NULL) AS dormant_no_row,
       count(*) FILTER (WHERE s.status = 'FAILED') AS dormant_failed,
       count(*) FILTER (WHERE s.status = 'SKIPPED') AS dormant_skipped,
       count(*) FILTER (WHERE s.status IS DISTINCT FROM 'SCORED' AND a."read") AS dormant_read,
       count(*) FILTER (WHERE s.status IS DISTINCT FROM 'SCORED' AND COALESCE(a.published_at, a.fetched_at) <= now() - :windowDays * interval '1 day') AS dormant_outside_window,
       round(100.0 * count(*) FILTER (WHERE s.status IS DISTINCT FROM 'SCORED') / NULLIF(count(*), 0), 1) AS dormant_pct
FROM (SELECT DISTINCT g.article_id FROM article_engagement g) e
JOIN article a ON a.id = e.article_id
LEFT JOIN article_score s ON s.article_id = e.article_id
CROSS JOIN LATERAL (SELECT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = e.article_id) AS voted) v;

-- dormant-kind: the same split per engagement kind
SELECT 'dormant-kind' AS section, g.kind, count(*) AS engaged, count(*) FILTER (WHERE s.status IS DISTINCT FROM 'SCORED') AS dormant
FROM article_engagement g LEFT JOIN article_score s ON s.article_id = g.article_id GROUP BY g.kind ORDER BY g.kind;

-- floor: the D-02 data floor (>= 30 counted engaged articles, >= 3 non-negative topics a counted article matches)
SELECT 'floor' AS section, c.counted, t.topics_with_eng, c.counted >= 30 AND t.topics_with_eng >= 3 AS floor_met
FROM (SELECT count(DISTINCT g.article_id) AS counted FROM article_engagement g JOIN article_score s ON s.article_id = g.article_id AND s.status = 'SCORED' WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id)) c,
     (SELECT count(DISTINCT ts.topic_id) AS topics_with_eng FROM article_engagement g JOIN article_score s ON s.article_id = g.article_id AND s.status = 'SCORED' JOIN article_topic_score ts ON ts.article_id = g.article_id JOIN interest_topic t ON t.id = ts.topic_id AND t.weight >= 0 WHERE ts.noul > 0.5 AND NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id)) t;

-- backfill-pool: stars and board rows the simulated backfill adds
SELECT 'backfill-pool' AS section, count(*) AS candidates, count(*) FILTER (WHERE x.engaged) AS already_engaged, count(*) FILTER (WHERE NOT x.engaged) AS added, count(*) FILTER (WHERE NOT x.engaged AND x.scored AND NOT x.voted) AS added_counted
FROM (SELECT u.article_id, EXISTS (SELECT 1 FROM article_engagement g WHERE g.article_id = u.article_id) AS engaged, EXISTS (SELECT 1 FROM article_score s WHERE s.article_id = u.article_id AND s.status = 'SCORED') AS scored, EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = u.article_id) AS voted FROM (SELECT sa.id AS article_id FROM article sa WHERE sa.starred UNION SELECT ba.article_id FROM board_article ba) u) x;
```
`floor.topics_with_eng` equals the number of non-negative topics with `eng_raw > 0` whenever open-weight and save-weight are > 0 (`eng_raw = learnRate × SUM(strength × hinge)`, hinge > 0 iff noul > 0.5; negative base → 0 [VERIFIED: InterestScoreQueries.java:139]). It is constant-free on purpose, so the gate measures the data. The status values come from V6 [VERIFIED: V6__interest_scoring.sql:25 `status TEXT NOT NULL CHECK (status IN ('SCORED', 'FAILED', 'SKIPPED'))`], and topic weights from V6 line 16 `weight INTEGER NOT NULL DEFAULT 20 CHECK (weight BETWEEN -50 AND 50)`.

Synthetic run output (shape only): `dormant 50 43 41 2 7 5 2 0 1 3 14.0`, `floor 41 3 t`, `backfill-pool 38 9 29 25`.

### Blend sections (each is a verbatim blend line, then the shown next line)
```sql
-- engaged: every engaged SCORED article's badge, read or unread (D-13 cross-check). Blend line = blendCte(ENGAGED_SCOPE)
<WITH learned AS ... AND (a.id IN (SELECT g.article_id FROM article_engagement g)) ... GROUP BY s.article_id, s.profile_score, s.profile_max_level)>
SELECT 'engaged' AS section, a.id AS article_id, CASE WHEN b.raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END AS interest_score, round(b.raw_n, 1) AS raw, a."read" AS is_read, EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = a.id) AS voted
FROM blended b JOIN article a ON a.id = b.article_id
ORDER BY b.raw_n DESC, a.id DESC;

-- backfill-summary: tier histogram with the simulated backfill. Blend line = blendCte(UNREAD_SCOPE) with ORIGINAL_ENGAGED -> BACKFILL_ENGAGED
<backfill blend line>
, scored AS (SELECT b.article_id, b.raw_n, CASE WHEN b.raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END AS interest_score FROM blended b)
SELECT 'backfill-summary' AS section, count(*) AS scored_unread,
       round(100.0 * count(*) FILTER (WHERE interest_score >= :tierHigh) / NULLIF(count(*), 0), 1) AS high_pct,
       round(100.0 * count(*) FILTER (WHERE interest_score >= :tierNeutral AND interest_score < :tierHigh) / NULLIF(count(*), 0), 1) AS neutral_pct,
       round(100.0 * count(*) FILTER (WHERE interest_score < :tierNeutral) / NULLIF(count(*), 0), 1) AS low_pct
FROM scored;

-- backfill-learned: per topic id under the simulated backfill (one line: LEARNED_CTE with the swap, then this SELECT)
<LEARNED_CTE with swap> SELECT 'backfill-learned' AS section, e.id AS topic_id, e.base, round(e.eng_raw::numeric, 3) AS eng_raw, round(e.eng::numeric, 3) AS eng, :engagementCap > 0 AND e.eng_raw >= :engagementCap AS eng_at_cap, round(e.w::numeric, 3) AS effective FROM eff2 e ORDER BY e.id;
```
The badge expression is the verbatim `INTEREST_SCORE` [VERIFIED: InterestScoreQueries.java:61-62 `"CASE WHEN b.raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END"`], and the unread scope is `UNREAD_SCOPE` [VERIFIED: line 46 `"a.\"read\" = false"`]. Each badge line keeps the `, <INTEREST_SCORE> AS interest_score` item exactly once and names `interest_score` exactly once, as the guard's badge-line check requires. For the backfill-summary, consider also selecting the full percentile columns of `summary`, so the note can compare like with like.

**Optional `eng_articles` column** (D-04 "clearly engaged often"), added at the end of the SELECT in `learned` and `backfill-learned`. The guard checks only the `startsWith(... " SELECT 'learned' AS section")` prefix, so trailing columns are allowed:
```sql
, (SELECT count(*) FROM engaged g JOIN article_score gs ON gs.article_id = g.article_id AND gs.status = 'SCORED' JOIN article_topic_score ts ON ts.article_id = g.article_id WHERE ts.topic_id = e.id AND ts.noul > 0.5) AS eng_articles
```
(Not executed this session; it is a scalar subquery over the in-scope `engaged` CTE. Run it once on the throwaway DB, as in Wave 0.)

### Data-floor gate (checkpoint `<verify>`)
```bash
f=$(ls -t "$HOME/.cache/myfeeder-phase12/replay"/replay-pp100-hi70-ne22-op0.25-sv0.5-cap8.tsv | head -1) &&
awk -F'\t' '$1=="floor" { print; ok = ($4 == "t") } END { exit !ok }' "$f"
```
On a miss before 2026-11-11, the checkpoint reports the `floor` row and stops (re-runnable). On or after 2026-11-11, apply D-03.

### D-13 cross-check (after deploy, ids and numbers only)
```bash
C="$HOME/.cache/myfeeder-phase12"; OUT_DIR="$C/replay-0.3.1" scripts/interest-calibration-replay.sh "100:70:22:$OPEN:$SAVE:$CAP"
awk -F'\t' '$1=="engaged" && $6=="f" { print $2, $3 }' "$C/replay-0.3.1/"*.tsv | head -5 | while read -r id badge; do
  j=$(/usr/bin/curl -sf "http://192.168.44.204/api/articles/$id")
  got=$(printf '%s' "$j" | jq '.interestScore')
  eng=$(printf '%s' "$j" | jq '[.interestBreakdown.rows[]? | select(.kind == "TOPIC" and (.engagementWeight // 0) > 0) | .topicId] | length')
  echo "$id replay=$badge api=$got topics_with_engagement=$eng"
done > "$C/sc3-check.txt"
```
Pass when every `replay == api` and at least one id has `topics_with_engagement > 0`. The `engaged` columns are: section, article_id, interest_score, raw, is_read, voted.

### D-12 dry-run "Helm values diff" without leaking secrets
```bash
git diff --stat v0.3.0 HEAD -- helm/ Dockerfile deploy.sh src/main/resources/db/   # expected: empty [VERIFIED empty for v0.3.0..current HEAD]
helm -n myfeeder get values myfeeder -o json | jq -c '{app}'                           # expect {"app":{"image":{"tag":"0.3.0"}}}; deploy sets 0.3.1
```
`deploy.sh` sets `--set app.image.tag="$VERSION"` and four `secrets.*` keys [VERIFIED: deploy.sh]. Never print `.secrets`. The exact JSON shape of `helm get values` is [ASSUMED] from Helm's user-supplied-values behaviour.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Replay candidates `PP:HIGH:NEUTRAL`, engagement via env only | Recommended `PP:HIGH:NEUTRAL[:OPEN:SAVE:CAP]` | This phase | One call runs and validates the whole D-06 grid |
| Driver checks only that engagement values are numeric | Mirrors `isValid` (WR-02) | This phase | A recorded winner can never fail startup |
| 07 note listed article titles | 12 note holds ids, `topic_<id>` and numbers only | CONTEXT (carried forward) | Stricter privacy |

**Stale references to fix in the SC-4 pass** (found this session):
- CLAUDE.md:219 and `src/main/resources/application.yaml:42` point at `.planning/phases/07-rollout-calibration/07-CALIBRATION.md`, which now lives at `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md` [VERIFIED: ls].
- CLAUDE.md:188: "identical literals in main and test yaml, with the dev overlay untouched". This becomes false if tuning moves any value (Pitfall 4).
- "Phase 12 calibrates/tunes" comments: main and test yaml lines 47/46 and 56/55; `MyfeederProperties.java:91` and `:126`. D-05 keeps near-miss at 0.35, so its "Phase 12 tunes it" comment should change to reflect the decision.
- `.claude/CLAUDE.md` says "no active milestone (v0.2.1 ...)". This is stale but belongs to milestone close, not this phase.

## SC-4 CLAUDE.md audit (what exists vs what to add)

| SC-4 item | Present today | Action |
|-----------|---------------|--------|
| V7 schema | Infrastructure Flyway list; Interest Ranking → Schema bullet | None |
| Capture rules | "Engagement capture (v0.3.0)" block | None |
| Extended learned model: thumbs first, separate additive cap, negative-base skip, scored only | "Engagement learning (v0.3.0, Phase 9)" (w_thumbs then w, eng cap, "Both are 0 for a topic with a negative base", "`eng_learned` counts SCORED articles only") | None, or a one-line summary listing the four rules by name |
| Engagement tuning levers | Partial (CLAUDE.md:219 mentions `ENGAGEMENT_*` and yaml placement) | Add: the 6-field candidate syntax; the new sections and what each answers; the D-02 floor; the D-04/D-09/D-10 rules; that tuned values go to main + dev overlay with the test yaml kept; the calibration outcome and values with a pointer to `12-CALIBRATION.md`; fix lines 188 and 219 |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `helm get values -o json` returns user-supplied values shaped `{app:{image:{tag}}, secrets:{...}}` | Code Examples (D-12) | Low: the jq filter shows nothing and the dry run must fall back to `git diff` plus the deploy line. Never print `.secrets` either way |
| A2 | The D-02 floor should gate on `counted` (engaged, SCORED, no vote), not on all engaged SCORED | Pitfall 10 | Low: today the two are probably equal (no effective votes). Confirm at the planning or gate checkpoint |
| A3 | D-10 compares the backfill histogram with the cap-0 (off) histogram | Pitfall 11 | Medium: it changes the ENG-F5 call. Record the rule in the note, and ask at the proposal checkpoint |
| A4 | "Clearly engaged often" (D-04) is judged from the `eng_articles` count per topic, with no fixed threshold | Code Examples | Medium: needs a user call at the proposal checkpoint (e.g. ≥ 10 contributing articles) |
| A5 | A pre-deploy pg_dump is optional for 0.3.1 (no migration; rollback to rev 20 runs on the same V7 schema) | Recommended Plan Structure | Low: the dump costs about 11 MB and a minute. Include it if the user prefers the 08-05 habit |
| A6 | The prod floor will not be met for several weeks (capture started 2026-09-30) | Summary | None for planning: the gate handles it, and the D-03 fallback date is 2026-11-11. The live count could not be read, because prod psql was blocked |

## Open Questions (RESOLVED)

1. **What is the current prod engagement volume?** RESOLVED
   - What we know: prod runs 0.3.0, with 10 topics (8 non-negative: bases 30, 30, 20, 20, 15, 20, 25, 35; 2 at -20) and learned 0.0 everywhere [VERIFIED: `/api/interest/topics/learned`, ids and weights only]. Capture started 2026-09-30.
   - What's unclear: the number of `article_engagement` rows. psql to prod was blocked in this session.
   - Recommendation: the first gate run reports the `floor` and `dormant` rows; nothing in planning depends on the number.
   - RESOLVED: the number is read at execution time, not at planning time. 12-03 Task 1 runs the read-only prod replay and writes the `floor` row (counted, topics_with_eng, floor_met) to `gate.txt` and the `dormant` row to `proposal.txt`. 12-03 Task 2 gates on it: while the D-02 floor is unmet before 2026-11-11, hold is the only outcome (D-01, D-03). No plan depends on the count.
2. **What happens to the two untracked `docs/superpowers/plans/...` files?** RESOLVED
   - Recommendation: the release preflight surfaces them and the user decides (commit, ignore or move). Never bypass `verifyRelease`.
   - RESOLVED: as recommended. 12-05 Task 1 (read-only dry run) lists each untracked path as an `untracked: <path>` line in `dry-run.txt`. 12-05 Task 2 (the D-12 blocking checkpoint) has the user choose approve-commit-untracked, approve-exclude-untracked (`.git/info/exclude`) or approve-user-moved, with plain approve accepted only when none are listed. 12-05 Task 3 applies that answer before the merge and never deletes a file, and the plan prohibits `-Prelease.disableChecks`.
3. **Should the full `summary` percentile set be repeated in `backfill-summary`?** RESOLVED
   - Recommendation: yes, for a like-for-like comparison in the note. It costs nothing to the guard.
   - RESOLVED: yes. 12-02 Task 1 builds `backfill-summary` as the summary's whole SELECT with only the label changed, so it carries the same column list (high_pct is column 6 in both, p10 to p95 in columns 9 to 17). 12-03 Task 1 uses those columns for the D-10 ENG-F5 comparison (bf_high_pct, bf_delta_off, bf_delta_real).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| psql (local) | Replay | ✓ | 18.6 | — |
| Prod Postgres reachability (pg.bartram.org:5432) | Replay against prod, gate | ✗ from this Orca session (`No route to host`) | — | User grants Orca the Local Network permission; there is no other fallback |
| App API (192.168.44.204) via `/usr/bin/curl` | D-13 | ✓ | serves 0.3.0 | — |
| kubectl / helm | Release, logs | ✓ installed (context `k3s-ansible`); reachability likely blocked the same way as psql | v1.37.1 / v4.3.0 | Local Network permission |
| helm-diff plugin | D-12 values diff | ✗ | — | `helm get values ... \| jq '{app}'` + `git diff v0.3.0 -- helm/` |
| Docker | Image build, Testcontainers | ✓ | 29.8.1 | — |
| jq / awk | Evidence, driver | ✓ | 1.8.2 / BSD 20200816 | — |
| Java / Node | Build and tests | ✓ | 25.0.4 / 26.10.0 | — |
| Deploy keys (4) | deploy.sh | ✓ all set, no `,` or `\` | — | — |
| No `SPRING_AI_TYPESAFE_*` / `SPRING_PROFILES_ACTIVE` exported | Offline test suite | ✓ (0 found) | — | — |

**Missing dependencies with no fallback:** prod LAN reachability from Orca (needs the user's one-time permission grant before the gate, the release and D-13).
**Missing dependencies with fallback:** helm-diff (jq filter).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ (Spring Boot BOM), Testcontainers for DB tests |
| Config file | `build.gradle.kts` (Gradle test task); `src/test/resources/application.yaml` |
| Quick run command | `./gradlew test -x npmBuild -x npmInstall --tests "org.bartram.myfeeder.repository.InterestCalibrationReplaySqlTest" --tests "org.bartram.myfeeder.DevProfileConfigTest" --tests "org.bartram.myfeeder.config.MyfeederPropertiesValidationTest"` (no Docker; ran green this session: 14 + 4 + 16 tests) |
| Full suite command | `./gradlew test -x npmBuild -x npmInstall` (Docker required) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CAL-02 | Every blend copy (unread ×3, window, learned, engaged, backfill, backfill-learned) is verbatim; counts 8/6/6 | unit (plain JUnit) | quick run above | ✅ extend `InterestCalibrationReplaySqlTest` |
| CAL-02 | The backfill differs from the app blend only in the `engaged` CTE, and keeps vote exclusion | unit | quick run above | ❌ new test method |
| CAL-02 | New sections are read-only (keyword check), with no `\` lines and no dismissal table | unit | quick run + `--tests "*V7EngagementMigrationTest"` (Docker) | ✅ existing tests cover new text |
| CAL-02 | The driver rejects out-of-range engagement (WR-02) before connecting; accepts cap 0; 6-field syntax; distinct file names | unit (ProcessBuilder) | quick run above | ❌ new test methods |
| CAL-02 | The replay runs end to end on a migrated DB; `engaged` badges equal `InterestScoreQueries.displayScores`; `learned` eng values equal `allTopicWeights()` | integration (Testcontainers + host psql, `assumeTrue` psql on PATH) | `./gradlew test -x npmBuild -x npmInstall --tests "*InterestCalibrationReplayRunTest"` | ❌ optional, recommended |
| CAL-02 | The replay against prod emits every section | manual-only (prod, LAN) | `scripts/interest-calibration-replay.sh 100:70:22` then `cut -f1 <tsv> \| sort -u` | n/a (checkpoint evidence) |
| CAL-03 | Main yaml starts and binds the tuned values; the dev overlay resolves main | unit | quick run above | ✅ update `shippedMainYamlStarts` if values change |
| CAL-03 | Prod serves the tuned blend: replay badge == API badge, engagementWeight > 0 (SC-3) | manual/ops (D-13 script) | the D-13 script above | n/a |

### Sampling Rate
- **Per task commit:** the quick run (seconds).
- **Per wave merge:** full suite `./gradlew test -x npmBuild -x npmInstall`.
- **Phase gate:** full suite plus `cd src/main/frontend && npx tsc -b && npx vitest run` green in the release preflight, before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] Throwaway-DB rehearsal of the full new replay (as done in this research): `docker run postgres:latest`, apply `src/main/resources/db/migration/V*.sql` in order with `psql -v ON_ERROR_STOP=1`, seed, then `MYFEEDER_PG_PASSWORD=x PGHOST=127.0.0.1 PGPORT=<port> OUT_DIR=<tmp> scripts/interest-calibration-replay.sh ...`. The driver honours PGHOST/PGPORT/PGUSER/PGDATABASE. Alternatively, make it the `InterestCalibrationReplayRunTest` above (inject the `PostgreSQLContainer` bean from `TestcontainersConfiguration` for host, port, user, password and database).
- [ ] Drift-guard constants refactor (5/4/4 literals → named counts) before the new sections land.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | (homelab app has no auth; out of scope) |
| V3 Session Management | no | — |
| V4 Access Control | partial | Prod DB access is read-only by session (`PGOPTIONS='-c default_transaction_read_only=on'`), asserted by `replayIsReadOnly` |
| V5 Input Validation | yes | The driver regex-validates every value that becomes SQL text through `psql -v`, plus the awk range check, all before any connection |
| V6 Cryptography | no | — |
| V8 Data Protection | yes | Public-repo privacy: the note and release facts hold ids and numbers only; raw TSV, JSON and logs stay in mode-700 `$HOME/.cache/myfeeder-phase12/` (the driver already sets `umask 077`) |
| V14 Configuration | yes | Keys only via env, checked with `test -n`, never printed; no `set -x` (asserted by test); tuning only via committed yaml |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection through psql `-v` interpolation | Tampering | Numeric-only regex on all six candidate fields and the env values, before connect (existing + extended) |
| Replay accidentally writing to prod | Tampering | Read-only session option; write-keyword and `\` checks in the drift guard; count-only sections are plain SELECT |
| Calibrating to constants prod refuses (WR-02) | Denial of Service | The awk mirror of `isValid`; `MyfeederPropertiesValidationTest.shippedMainYamlStarts` on the committed yaml |
| Topic names, profile text or titles leaking to the public repo | Information Disclosure | ids-only sections; jq projection of breakdowns; note content rule; titles only in cache TSVs |
| Secrets in evidence (helm values, logs) | Information Disclosure | `jq '{app}'` piped, never raw `helm get values`; `grep -qF` checks that release notes contain no key |
| Moved or re-created tags, force push | Tampering | Record the v0.3.0 SHA (`db5acd7`) in the preflight and re-verify it after release; no `--no-verify`, no `-Prelease.disableChecks` |

## Sources

### Primary (HIGH confidence; read or executed this session)
- `scripts/interest-calibration-replay.sh` (all 77 lines), `scripts/interest-calibration-replay.sql` (all 94 lines)
- `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java` (all)
- `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java:30-200, 355-421`
- `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java:15-41, 80-125`
- `src/main/resources/application.yaml`, `src/test/resources/application.yaml`, `src/test/resources/application-dev.yaml` (engagement, tiers, suggestions)
- `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java`, `MyfeederPropertiesValidationTest.java:30-61`, `V7EngagementMigrationTest.java:209-218`
- Migrations V1 (article.starred), V2 (board_article), V6 (article_score, interest_topic, article_topic_score), V7 (all)
- `src/main/java/org/bartram/myfeeder/model/InterestBreakdown.java`, `EngagementKind.java`
- `.planning/phases/08-engagement-capture/08-05-PLAN.md`, `08-RELEASE.md`; `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md`; `09-REVIEW.md` WR-02; `09-REVIEW-DISPOSITION.md`
- Executed: a throwaway postgres:latest (18.6) with V1–V7 plus synthetic data, the existing replay and all drafted sections read-only; the awk validation matrix; the WR-02 reproduction; `currentVersion`; prod `/api/version`, `/api/interest/status`, `/api/interest/topics/learned` (ids and weights only); the targeted test classes (green)

### Secondary (MEDIUM)
- None needed. No external library is involved.

### Tertiary (LOW)
- Helm `get values` JSON shape (A1).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. No new dependency; tool versions probed.
- Architecture (sections, drift guard, driver): HIGH. Read in full and the SQL executed.
- Pitfalls: HIGH. Five of them reproduced or read directly (file collision, WR-02, untracked files, branch version, Orca block).
- Prod data readiness: LOW/unknown (blocked), handled by the gate.

**Research date:** 2026-10-02
**Valid until:** 2026-11-11 (the D-03 fallback date). Re-check `git status`, prod version and Helm revisions at the release preflight in any case.
