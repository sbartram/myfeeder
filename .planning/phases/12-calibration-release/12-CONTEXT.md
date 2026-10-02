# Phase 12: Calibration & Release - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Extend the read-only calibration replay with the CAL-02 report sections: dormant engagement, topics at the engagement cap, the tier histogram with engagement on and off, and a simulated backfill from stars and boards. Once prod has enough engagement, tune `open-weight`, `save-weight` and `cap` (CAL-03), commit them in yaml, write the calibration note with keep-or-revisit calls on ENG-F4 and ENG-F5, release v0.3.1 (Phases 9–11 plus the tuning) to prod, prove that engagement shows in prod, and finish the CLAUDE.md documentation (SC-4).

Not in this phase:
- building ENG-F4 (scoring dormant articles) or ENG-F5 (a real backfill). Both are only recorded calls.
- re-tuning the tier thresholds (70 / 22) or the near-miss cutoff (0.35)
- the Raindrop resilience todo

</domain>

<decisions>
## Implementation Decisions

### Carried forward (settled earlier; do not reopen)
- The calibrated build ships as **v0.3.1**, a patch bump with plain `./gradlew release`. Never move or re-tag an existing tag (v0.3.0 is at db5acd7).
- Tuning goes in committed yaml only: main `application.yaml`, plus identical literals in the test yaml or the tuned value in `application-dev.yaml`, as `DevProfileConfigTest` requires. Never use Helm `--set` or env overrides (v0.2.1 D-14).
- The replay stays read-only (`default_transaction_read_only=on`). Its blend lines stay byte-for-byte copies of `InterestScoreQueries`, so `InterestCalibrationReplaySqlTest` must stay green. New SQL aliases and section labels must pass the write-keyword check.
- Startup validation is unchanged: cap 0, or 0 ≤ open < save < 1 and 0 < cap < learned-cap (20) (`ENGAGEMENT_INVALID`). Current values: 0.25 / 0.5 / 8 (Phase 9 D-01).
- The calibration note holds article ids, `topic_<id>` keys and numbers only. It never includes topic names, descriptions or profile text, because the origin repo is public. Raw TSVs stay outside the repo (under `$HOME/.cache/...`).
- There is no new Flyway migration. V7 already holds the whole v0.3.0 schema.

### Data readiness gate
- **D-01:** The phase is **split**. The CAL-02 tooling (new replay sections, driver changes, drift-guard and keyword tests) is planned and executed **now**, because it needs no prod data. A **blocking checkpoint** then holds the tune, release and docs plans until the data floor is met.
- **D-02:** The data floor is count-based: at least **30 engaged SCORED articles** and at least **3 non-negative topics with nonzero `eng_raw`**. A read-only count query (or the replay's own sections) checks it at the checkpoint.
- **D-03:** If the floor is still unmet about **6 weeks after the v0.3.0 release** (around 2026-11-11), run the replay anyway and record that the data was thin. Keep 0.25 / 0.5 / 8, release v0.3.1, and mark the engagement constants "revisit" in the note.
  - **Amended 2026-10-02 (user decision):** the fallback date moves from 2026-11-11 to **2026-10-02**. Prechecks at 21:08Z and 21:21Z found counted 13/30 and topics 0/3: every counted article scores ≤ 0.10 on all non-negative topics, so engagement adds 0 to every topic and the floor's topic clause cannot be met by normal reading. All 13 would be Suggested-topic candidates, which only v0.3.1 ships. Taking the fallback now changes no badge today and lets later engagement land on topics created from suggestions; the constants stay marked "revisit".

### Tuning targets
- **D-04:** The goal is **nudge, not reshuffle**. With engagement on versus off (cap 0), the high-tier share of scored unread articles moves by **at most about 5 percentage points**. A few topics may approach the cap, but none should be pinned at it unless it was clearly engaged often. If no candidate beats the defaults on this rule, keep 0.25 / 0.5 / 8.
- **D-05:** Only the **engagement constants** are tuned. The replay reports the tiers with engagement on and off, but 70 / 22 does not move, and near-miss stays 0.35.
- **D-06:** Candidate grid, one TSV per run:
  - cap 0 (off)
  - the defaults 0.25 / 0.5 / 8
  - cap {4, 8, 12} × save {0.5, 0.75}, with open = save / 2

  That is roughly 7 runs, all at the live tiers 100 / 70 / 22.

### Backfill simulation & ENG-F4 / ENG-F5
- **D-07:** The simulated backfill treats every article with `starred = true` or any `board_article` row (Read Later included) as a **save** (save-weight), unioned with the real `article_engagement` rows. The live CTE's rules still apply: articles with a vote are excluded, only SCORED articles count, and negative-base topics are skipped.
- **D-08:** The backfill sim is a **separate, labeled section** (e.g. `backfill-sim`). Only its `engaged` CTE differs, by a UNION of star and board rows. The drift guard must prove that exactly that one CTE differs from the verbatim blend, while every other section stays byte-identical.
- **D-09:** The ENG-F4 call uses a **dormant share rule**. The note says "revisit" if dormant (engaged but never SCORED) articles make up at least **25%** of all engaged articles, and "keep" otherwise. Nothing is built for it here.
- **D-10:** The ENG-F5 call uses an **impact rule**. The note recommends a real backfill only if the simulation stays within the D-04 nudge target: high share moves by at most 5 points and few topics are pinned at the cap. Nothing is executed here; a real backfill would be its own migration or quick task.

### Release & prod proof
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope & requirements
- `.planning/ROADMAP.md` § Phase 12 — goal, success criteria SC-1..SC-4 and notes
- `.planning/REQUIREMENTS.md` — CAL-02, CAL-03; ENG-F4 and ENG-F5 (future, decided from CAL-02 data)
- `.planning/STATE.md` § Blockers/Concerns — Phase 9 WR-02 open for Phase 12

### Prior calibration (pattern to follow)
- `.planning/milestones/v0.2.1-phases/07-rollout-calibration/07-CALIBRATION.md` — v0.2.1 calibration note: structure, privacy rules, cross-check method
- `scripts/interest-calibration-replay.sh` — driver: `-v` variables, validate-before-connect, read-only session, output dir
- `scripts/interest-calibration-replay.sql` — verbatim blend sections (summary, top/bottom, window-summary, votes, learned)
- `src/test/java/org/bartram/myfeeder/repository/InterestCalibrationReplaySqlTest.java` — drift guard and keyword checks
- `src/test/java/org/bartram/myfeeder/DevProfileConfigTest.java` — main / test / dev-overlay yaml invariants

### Engagement model (what is being tuned)
- `.planning/phases/09-engagement-learning-model/09-CONTEXT.md` — D-01 defaults, D-14 no-release-until-Phase-12, the learned model rules
- `.planning/phases/09-engagement-learning-model/09-REVIEW-DISPOSITION.md` — WR-02 (the replay accepts out-of-range engagement values)
- `src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java` — `LEARNED_CTE` / blend source of truth
- `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java` — `ENGAGEMENT_INVALID` validation
- `src/main/resources/application.yaml` § `myfeeder.interest.blend.engagement`
- `CLAUDE.md` § Interest Ranking → Engagement learning, and § Tier thresholds and tuning

### Release
- `.planning/phases/08-engagement-capture/08-05-PLAN.md` and `08-RELEASE.md` — the v0.3.0 release plan, approval checkpoint and smoke pattern
- `CLAUDE.md` § Deployment → Cut a release; `deploy.sh`; `helm/myfeeder/values.yaml`
- `src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java` — `engagedArticleAgreesEverywhereAfterRefresh` (SC-3 analog in tests)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- The replay driver already passes `engagementOpenWeight` / `engagementSaveWeight` / `engagementCap` as `-v` variables (env `ENGAGEMENT_*`), and its `learned` section already reports `eng_raw`, `eng` and `eng_at_cap`. The "topics at cap" report is mostly in place already.
- Running the replay at cap 0 gives the "engagement off" histogram for free (`InterestScoreQueriesZeroEngagementTest` proves that cap 0 equals the v0.2.1 blend).
- The 07-CALIBRATION.md cross-check method (replay badge vs `GET /api/articles/{id}` `interestScore` for the top 5) is reused for D-13.

### Established Patterns
- Every replay statement repeats the full blend, because no temp objects or writes are allowed in a read-only session.
- The drift guard regenerates the expected SQL from the Java and fails on any byte difference. The backfill-sim section therefore needs an explicit, narrow exception (D-08).
- psql `-v` names equal the JdbcClient parameter names (`driverPassesEveryBlendParameter`).

### Integration Points
- The new sections go in `scripts/interest-calibration-replay.sql`, and driver changes go in `scripts/interest-calibration-replay.sh`.
- Tuned values go in `src/main/resources/application.yaml`, `src/test/resources/application.yaml` (identical literals) or `src/test/resources/application-dev.yaml`.
- Prod access needs `MYFEEDER_PG_PASSWORD` and the LAN, and Orca sessions need the Local Network permission (see memory: orca-local-network-privacy-blocks-lan-clis).

</code_context>

<specifics>
## Specific Ideas

- The data-floor check should be a quick, repeatable read-only query, so the checkpoint can be re-run cheaply until it passes.
- The "nudge" target is roughly a high-tier share delta of at most 5 points with engagement on versus off. Treat that as the decision rule, not a soft guideline.

</specifics>

<deferred>
## Deferred Ideas

- ENG-F4 (make dormant engaged articles eligible for one Jev call): decide only, from the D-09 rule; building it is a future milestone item.
- ENG-F5 (real backfill of stars and boards into `article_engagement`): decide only, from the D-10 rule; it would be its own migration or quick task.
- Re-tuning tier thresholds or near-miss with engagement on: out of scope (D-05). Revisit if the on/off histogram shows the D-10 bands drifting.

### Reviewed Todos (not folded)
- **Tune Raindrop resilience** (`.planning/todos/pending/2026-09-23-tune-raindrop-resilience-and-fix-claude-md-aspectj-note.md`): matched by keyword only and unrelated to engagement calibration. It stays a separate `/gsd-quick` task.

</deferred>

---

*Phase: 12-calibration-release*
*Context gathered: 2026-10-02*
