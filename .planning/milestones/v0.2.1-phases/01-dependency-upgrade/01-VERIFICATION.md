---
phase: 01-dependency-upgrade
verified: 2026-09-23T01:30:00Z
status: passed
score: 21/22 must-haves verified
covered_files:

  - .planning/REQUIREMENTS.md
  - .planning/codebase/STACK.md
  - .planning/phases/01-dependency-upgrade/01-01-PLAN.md
  - .planning/phases/01-dependency-upgrade/01-01-SUMMARY.md
  - .planning/phases/01-dependency-upgrade/01-02-PLAN.md
  - .planning/phases/01-dependency-upgrade/01-02-SUMMARY.md
  - .planning/phases/01-dependency-upgrade/01-03-PLAN.md
  - .planning/phases/01-dependency-upgrade/01-03-SUMMARY.md
  - .planning/phases/01-dependency-upgrade/01-04-PLAN.md
  - .planning/phases/01-dependency-upgrade/01-04-SUMMARY.md
  - CLAUDE.md
  - build.gradle.kts
  - src/main/frontend/package-lock.json
  - src/main/frontend/package.json
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java

covered_digest: "v1:sha256:50462b3f14933f94b3e658b16ce115244a082724b1b5736c3b007c9c32f6b35b"
behavior_unverified: 0
overrides_applied: 0
human_verification:

  - test: "Open http://192.168.44.204 in a browser. Load the feed tree, open a feed with recent items, open an article, and switch to Reader View once. Compare the newest articles' times with the deploy time (2026-09-23T00:56:05Z, in $HOME/.cache/myfeeder-phase01/deploy-time.txt)."
    expected: "The three-panel reader loads with your usual theme. Articles fetched after the deploy appear in the list and open in the reading pane. Reader View works. Nothing looks or behaves differently from 0.1.23."
    why_human: "ROADMAP SC3 says new articles appear in the reader as before. The API shows 2 post-rollout articles and the new bundle is served, but UI regressions from the React 19.3 / TanStack Query 5.103 / DOMPurify 3.4.15 refresh cannot be detected with grep or curl. This check was deferred from the 01-04 plan's <human-check>."
  - test: "Confirm the phase's judgment-tier prohibitions (listed in the report's Prohibitions section): no push/tag/image/deploy before your 'approve', no force-push or --no-verify, no production Postgres change, no npm install before your version approval, and no tests weakened."
    expected: "You agree with the verifier's evidence-based verdict (all held). The verdict is non-authoritative."
    why_human: "Judgment-tier prohibitions need a human decision. Temporal ordering (approval before action) is recorded only in SUMMARYs and evidence files written by the executor."
---

# Phase 1: Dependency Upgrade Verification Report

**Phase goal:** myfeeder runs in production on the current patch/GA dependency line and behaves exactly as before
**Verified:** 2026-09-23T01:30:00Z
**Status:** human_needed
**Re-verification:** No. This is the initial verification.

## Goal Achievement

Everything the phase can prove automatically, I re-checked myself instead of relying on the SUMMARYs:

- **Build and tests:** I ran one full Docker-backed `./gradlew cleanTest build`, then `npx tsc -b`, `npm test`, `npm outdated` and `npm audit`.
- **Dependencies:** I resolved `runtimeClasspath`.
- **Release:** I inspected the release checkout and origin.
- **Production (read-only):** I queried `/api/version`, `/`, `/api/feeds`, the deployment, the pod image digest, Helm history and the pod logs.
- **Soak:** I re-ran the plan's soak check over the evidence files, and took a fresh feed-health snapshot about 30 minutes after rollout.

The one remaining item is visual: confirming that the reader UI works as before.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | **SC1:** `./gradlew build` on Boot 4.0.8 / Spring AI BOM 2.0.1 / Spring Cloud 2025.1.3 passes every backend test (UPG-01) | ✓ VERIFIED | My run of `DOCKER_HOST=… ./gradlew cleanTest build` exited 0 at 2026-09-23T01:27Z. It produced 31 `TEST-*.xml` files: 162 tests, 0 skipped, 0 failures, 0 errors. `MyfeederApplicationTests` (Testcontainers) ran for 7.3s, and all 6 repository/migration classes ran. The baseline was 30 classes and 160 tests with 0 skipped. |
| 2 | **SC2:** frontend deps are on latest minor/patch, no major bumps, react-router on v6, and `npm test` + `npx tsc -b` pass (UPG-02) | ✓ VERIFIED | My runs: `npx tsc -b` exits 0, and vitest reports 13 files / 47 tests passed. `npm outdated` shows current == wanted for every package; only cross-major versions remain available. react-router-dom is 6.30.6 and typescript is 5.9.3. |
| 3 | **SC3:** the upgraded release is deployed to k3s, starts with clean logs, and feeds keep polling so new articles appear in the reader as before (UPG-03) | ? NEEDS HUMAN (automated parts verified) | Deploy, clean logs and polling are verified (truths 17-21). The API's `articles-after.json` shows 2 items fetched after rollout. Only the visual "appear in the reader as before" part needs a human (see Human Verification). |
| 4 | build.gradle.kts declares Boot 4.0.8, springAiVersion 2.0.1, springCloudVersion 2025.1.3 | ✓ VERIFIED | build.gradle.kts lines 3, 28 and 29 |
| 5 | D-03: nothing else moved (axion 1.21.1, wrapper 9.4.1, rome/rome-modules 2.1.0, readability4j 1.0.8) | ✓ VERIFIED | build.gradle.kts lines 5 and 42-44, and `gradle-9.4.1-bin.zip` in the wrapper properties. `git diff 8777658 HEAD` (excluding .planning) touches only 6 files. |
| 6 | No `org.springframework.boot` artifact resolves to 4.1.x | ✓ VERIFIED | My `runtimeClasspath` resolution: `spring-boot:4.0.8`, `spring-ai-anthropic:2.0.1`, `spring-cloud-commons:5.0.3`, and 0 matches for 4.1.x |
| 7 | The bump diff touches only the three version lines plus reactor-netty-http, and the BOM import block/order is unchanged | ✓ VERIFIED | `git show 2bdd267 -- build.gradle.kts`: 3 lines replaced and 2 added (a comment and the dependency). The `dependencyManagement` block has no diff. |
| 8 | D-01: the outbound transport stays Reactor Netty (`ReactorClientHttpRequestFactory`), and the test was seen RED first | ✓ VERIFIED | `outboundTransportIsReactorNetty` passed in my full run. `task1-red.log` shows it FAILED before the dependency was added. |
| 9 | reactor-netty-http is declared with no version, and there is no spring-webflux | ✓ VERIFIED | build.gradle.kts line 38. Resolved `reactor-netty-http -> 1.3.7`, with 0 webflux, 0 httpclient5 and 0 jetty-client. |
| 10 | D-02: the 5s/30s timeouts under `spring.http.clients.*` bind to `HttpClientSettings`, and the test was seen RED first | ✓ VERIFIED | application.yaml lines 6-9 use `clients:`. `outboundTimeoutsFromMainApplicationYamlBind` loads the main yaml from disk and passed in my run. `task2-red.log` shows it FAILED earlier. |
| 11 | D-02 is its own commit | ✓ VERIFIED | `fce474b` contains only application.yaml and the test. The bump commit `2bdd267` is separate. |
| 12 | Root CLAUDE.md documents Boot 4.0.8, the Reactor Netty transport and the `spring.http.clients.*` keys, and the user's hunks are untouched and uncommitted | ✓ VERIFIED | The committed CLAUDE.md contains all the required strings, 0 copies of the stale JDK claim, and the original line-40 text. The working-copy `git diff -U0` equals the pre-task snapshot. No phase commit has a `.claude/` path. |
| 13 | The frontend dependency name set is unchanged and no major (or 0.x minor) was crossed | ✓ VERIFIED | The package.json diff changes only version ranges. `~5.9.3` and `^6.30.6` are kept. |
| 14 | npm audit shows 0 high and 0 critical | ✓ VERIFIED | My `npm audit`: `{"moderate":2,"high":0,"critical":0}`. The two moderates are react-router v6 advisories, accepted in T-01-06. |
| 15 | The installed/committed lockfile is byte-identical to the user-approved lockfile (D-04) | ✓ VERIFIED | `shasum -a 256 -c npm-approved.sha256` returns OK for both files at HEAD. That the approval came before the install is a judgment-tier prohibition; see below. |
| 16 | `--no-ff` merge on main; tree equals main^2; code paths equal the verified SHA | ✓ VERIFIED | Release checkout: `29da9aa` has parents `02f74a1` and `388d48d`. The code-path diff 388d48d..29da9aa is empty. HEAD on sbartram/main has the same code as 29da9aa. |
| 17 | Tag v0.1.24 and main are on origin, and image :0.1.24 is in the registry | ✓ VERIFIED | `ls-remote`: `refs/heads/main` = 29da9aa and `refs/tags/v0.1.24` (tag object 58824e7) peels to 29da9aa. The running pod's imageID is `myfeeder@sha256:f1517e2d…`, which equals the recorded `docker push` digest, so the image was pulled from the registry. |
| 18 | The k3s deployment rolled out 0.1.24 and `/api/version` reports 0.1.24 | ✓ VERIFIED | Live: deploy image `registry.bartram.org/bartram/myfeeder:0.1.24`, 1/1 ready, pod `myfeeder-59d488df89-2cjnf` Running with 0 restarts. `/api/version` returns `{"version":"0.1.24",…}`. Helm revision 17 is deployed. |
| 19 | Clean startup: started line present, no failed-to-start banner, no ERROR line | ✓ VERIFIED | Live pod log (2h window, 135 lines): `Started MyfeederApplication in 9.856 seconds`, 0 ` ERROR `, 0 `APPLICATION FAILED`. The only WARNs are 2 `Failed to poll feed` lines, both on feeds that were already failing. |
| 20 | The new frontend bundle is live | ✓ VERIFIED | Live `/` serves `assets/index-DmtnXdgs.js`. Before the deploy it served `index-BQIrFRxz.js` (`bundle-before.txt`). |
| 21 | Feeds poll as before: every previously healthy feed has lastPolledAt after the deploy with errorCount 0, and the pod registered every feed | ✓ VERIFIED | Re-running the plan's soak check over the evidence gives exit 0: `{"healthy":40,"total":46,"clean":40,"only304":18,…all failure classes empty}`. My own live snapshot at 01:26Z shows 40/40 previously healthy feeds with lastPolledAt after the deploy and errorCount 0 (22 with a 200 after the deploy). The log has `Registered polling tasks for 46 feeds`, and 46 == feeds-before length. |
| 22 | STACK.md records the upgraded versions, keys and transport | ✓ VERIFIED | STACK.md contains Boot 4.0.8 (2 places, 0 copies of 4.0.3 left), Spring AI 2.0.1, Resilience4j 2025.1.3, `spring.http.clients` and `reactor-netty-http`. |

**Score:** 21/22 truths verified (0 present-but-behavior-unverified). One truth (SC3) is waiting on human visual confirmation of the reader UI.

### Prohibitions (all judgment-tier; non-authoritative LLM verdict, human review recommended)

| Prohibition | Verdict | Evidence |
|-------------|---------|----------|
| MUST NOT make tests pass by deleting, @Disabled-ing or weakening tests (01-01, 01-03) | Held | 33 test-named files now vs 32 at 8777658, with no deletions. `grep @Disabled src/test` finds nothing. The phase's src/test diff only adds the new test. |
| MUST NOT commit or discard the user's CLAUDE.md / .claude edits, or stage .claude/ (01-01, 01-03) | Held | The user-hunk diff is identical to the snapshot, `.claude/` paths across phase commits = 0, and those files are still modified in the working tree. |
| MUST NOT install or commit npm versions before approval (01-02) | Held (per record) | The lockfile hash equals the approved hash. The approval ("approved") is recorded only in the SUMMARY. |
| MUST NOT push/tag/image/deploy before `approve` (01-03, 01-04) | Held (per record) | `preflight.txt` (00:41Z) shows main unpushed. The deploy happened at 00:56Z. The approval is recorded in the SUMMARY. |
| MUST NOT force-push, use --no-verify or disableChecks, or move a tag | Held | origin/main is a fast-forward (02f74a1 is an ancestor of 29da9aa), and the tag peels to the merge commit. The command flags themselves can't be checked after the fact. |
| MUST NOT modify production Postgres | Held (per record) | There is no Flyway migration in the phase diff, and no FLUSHALL or rollback ran according to the SUMMARY. |
| .serena/project.yml was not discarded without the user's choice (01-03) | Held (per record) | The release checkout is clean. The user chose "restore" after the byte-identity check (SUMMARY). |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `build.gradle.kts` | Target BOMs + reactor-netty-http | ✓ VERIFIED | Contains `id("org.springframework.boot") version "4.0.8"` and the version-less reactor-netty-http line |
| `src/test/java/org/bartram/myfeeder/config/HttpClientConfigurationTest.java` | Transport and timeout regression tests | ✓ VERIFIED | 51 lines with 2 real assertions. Both pass in the full suite. |
| `src/main/resources/application.yaml` | `clients:` timeouts | ✓ VERIFIED | Lines 6-9 |
| `CLAUDE.md` | Updated docs | ✓ VERIFIED | Lines 7, 36, 149 and 168 committed |
| `src/main/frontend/package.json` / `package-lock.json` | In-major refresh | ✓ VERIFIED | react-router-dom `^6.30.6`, lock `node_modules/react-router-dom` 6.30.6 |
| `.planning/codebase/STACK.md` | Resolved versions | ✓ VERIFIED | See truth 22 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| build.gradle.kts | Boot HTTP-client detection | reactor-netty-http on the runtime classpath | ✓ WIRED | Resolved 1.3.7. The detection result is asserted by `outboundTransportIsReactorNetty`. |
| application.yaml | HttpClientSettings | `spring.http.clients.*` binding | ✓ WIRED | `outboundTimeoutsFromMainApplicationYamlBind` passes against the real main yaml |
| HttpClientConfigurationTest | src/main/resources/application.yaml | YamlPropertySourceLoader over FileSystemResource | ✓ WIRED | Test line 41 |
| package-lock.json | Gradle npmInstall/npmBuild → jar | Gradle task inputs | ✓ WIRED | The live bundle `index-DmtnXdgs.js` equals the bundle from the 01-02 vite build |
| sbartram/main | main (release checkout) | `--no-ff` merge | ✓ WIRED | 29da9aa^2 = 388d48d, with identical code paths |
| main | tag v0.1.24 on origin | axion release + gitPushRelease | ✓ WIRED | The tag peels to 29da9aa |
| registry image 0.1.24 | deploy/myfeeder | deploy.sh → Helm `app.image.tag` | ✓ WIRED | Helm rev 17, pod imageID digest = pushed digest |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Produces Real Data | Status |
|----------|------|--------|--------------------|--------|
| Production feed polling | Feed.lastPolledAt / errorCount | FeedFetcher over Reactor Netty → Postgres | Yes: 40/40 previously healthy feeds re-polled after the deploy, 22 of them with 200 responses | ✓ FLOWING |
| Production articles | `/api/articles` items | Poll → Postgres | Yes: 2 items fetched after rollout (at soak time) | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full backend suite on the upgraded tree | `DOCKER_HOST=… ./gradlew cleanTest build` | exit 0; 31 classes / 162 tests / 0 skipped / 0 failures / 0 errors | ✓ PASS |
| Resolved dependency line | `./gradlew -q dependencies --configuration runtimeClasspath` | boot 4.0.8, ai 2.0.1, cloud-commons 5.0.3, reactor-netty-http 1.3.7, no webflux, no 4.1.x | ✓ PASS |
| Frontend type-check and tests | `npx tsc -b`; `npm test` | exit 0; 13 files / 47 tests passed | ✓ PASS |
| In-major gate | `npm outdated --json` | 0 packages with current != wanted | ✓ PASS |
| Audit gate | `npm audit --json` | high 0, critical 0, moderate 2 (react-router v6) | ✓ PASS |
| Prod version | `curl /api/version` | `{"version":"0.1.24"}` | ✓ PASS |
| Prod polling health | `curl /api/feeds` compared with feeds-before.json | 40/40 healthy feeds polled after the deploy, errorCount 0 | ✓ PASS |
| Soak check (plan's script over evidence) | node soak script | exit 0, all failure classes empty | ✓ PASS |

The build did not modify `src/main/frontend` (git status is empty for that path).

### Probe Execution

Step 7c: no `scripts/*/tests/probe-*.sh` exist, and the plans declare no probes. Skipped.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| UPG-01 | 01-01, 01-03 | Backend on Boot 4.0.8 / Spring AI BOM 2.0.1 / Spring Cloud 2025.1.3 with all backend tests passing | ✓ SATISFIED | Truths 1 and 4-12 |
| UPG-02 | 01-02, 01-03 | Frontend deps on latest minor/patch (no major bumps); `npm test` and `npx tsc -b` pass | ✓ SATISFIED | Truths 2 and 13-15 |
| UPG-03 | 01-03, 01-04 | Released and deployed to k3s, starts cleanly, feeds polling as before | ✓ SATISFIED (automated); reader UI check pending | Truths 16-21; human item 1 |

All three IDs appear in the plan frontmatter and in REQUIREMENTS.md, which maps them to Phase 1. No requirements are orphaned.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (phase files) | — | TBD/FIXME/XXX/TODO | none | No debt markers in build.gradle.kts, application.yaml or the new test |
| `src/main/resources/application.yaml` | 4-5 | The comment says read-timeout "bounds" thread time, but on Reactor Netty it is an idle-between-reads timeout, not a total deadline (review WR-01) | ⚠️ Warning | The docs overstate the D-02 protection. A slow-drip remote can still hold a thread. Behavior is no worse than before (it used to be unbounded). |
| `src/test/java/.../HttpClientConfigurationTest.java` | 31-35 | The transport test doesn't load the main yaml, so a `spring.http.clients.imperative.factory` override wouldn't be caught (review WR-02) | ⚠️ Warning | The guard is narrower than CLAUDE.md claims. Today it has no effect, because there is no such override. |
| `build.gradle.kts` / `CLAUDE.md` | 37 / 168 | Says "Pins", but the dependency only makes Reactor Netty the detected factory (review IN-01) | ℹ️ Info | A future httpclient5/jetty on the classpath would win detection. The test would catch that. |
| FeedFetcher (not a phase file) | — | Timeouts that now fire surface as an unmapped `ResourceAccessException` (500) on subscribe (review IN-02) | ℹ️ Info | A consequence of the intended D-02 change; the gap already existed |

### Notes on "behaves exactly as before"

- **Intended, documented behavior change (D-02):** the outbound timeouts now actually apply. In production, feed 13 (hnrss.org) failed with `connection timed out after 5000 ms`. It was already failing before the deploy (errorCount 16 → 17), so this is not a regression of a healthy feed.
- **Pre-existing failing feeds are unchanged:** feed 31 (over 10 MiB cap) went 56 → 57, and feeds 22, 11, 8 and 48 did not change.

### Human Verification Required

#### 1. Reader UI works as before on 0.1.24

**Test:** Open http://192.168.44.204. Load the feed tree, open a feed with recent items and open an article. Toggle Reader View once. Compare the newest article times with the deploy time, 2026-09-23T00:56:05Z.
**Expected:** The three-panel reader loads with your theme. Articles fetched after the deploy appear and open in the reading pane, and Reader View renders. Nothing differs from 0.1.23.
**Why human:** SC3's "new articles appear in the reader as before" is a visual check. UI regressions from React 19.3, TanStack Query 5.103 or DOMPurify 3.4.15 can't be grepped.

#### 2. Confirm the judgment-tier prohibition verdicts

**Test:** Review the Prohibitions table above.
**Expected:** You agree that all of them held, in particular that nothing was published before your "approve" and nothing was installed before your npm approval.
**Why human:** Approval-before-action ordering is recorded only in executor-written SUMMARYs and evidence files.

### Gaps Summary

There are no gaps. Every automated must-have is verified with evidence I gathered myself:

- the upgraded backend passes the full Docker-backed suite on the target BOM line and keeps the Reactor Netty transport, with bound timeouts
- the frontend is on the latest in-major versions and type-checks and tests clean
- v0.1.24 is tagged, pushed, in the registry and running in production with a clean startup
- every previously healthy feed is still polling

The phase is waiting on the human reader-UI check. The two review warnings (WR-01 and WR-02) are about documentation and test accuracy. They don't block the goal, but a follow-up should address them.

---

_Verified: 2026-09-23T01:30:00Z_
_Verifier: Claude (gsd-verifier)_
