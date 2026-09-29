# Stack Research: v0.3.0 Engagement Learning

**Domain:** Implicit-feedback (engagement) learning added to an existing query-time interest blend in a self-hosted, single-user feed reader
**Researched:** 2026-09-29
**Confidence:** HIGH for the verdict (zero new dependencies) and the integration points. Both come from reading this repo's code, `package.json`, `node_modules` and `build.gradle.kts`. MEDIUM for the browser-API facts. Those come from MDN plus Chromium and Mozilla tracker posts that agree with each other. The research-plan seam rates single web sources LOW, so each browser fact below was checked against a second source.

> Scope: this covers only what is new for engagement learning. The existing stack (Boot 4.0.8, Spring AI 2.0.1, Spring Cloud 2025.1.3, TypeSafe starter 0.1.0, React 19.3, TanStack Query 5.103, Zustand 5.0.15, Vite 8, Vitest 4.1, jsdom 29.1) is in `.planning/codebase/STACK.md` and in the v0.2.1 archive. It is not re-researched here.

## Verdict (read this first)

1. **Zero new dependencies: YES.** Backend, frontend and tests all need nothing new: no Maven artifact, no npm package, no dev tool, no BOM change and no version bump. Every capability maps onto something already on the classpath or in `node_modules`:
   - storage: a Flyway V7 migration plus a `JdbcClient` store
   - learning: an extra CTE in `InterestScoreQueries.LEARNED_CTE` with extra named params
   - capture: a plain `fetch` through `src/api/client.ts` plus server-side hooks in existing services
   - tests: Vitest, RTL, user-event 14.6 and Testcontainers
2. **Most engagement is captured server-side, with no frontend change.** Every save path already ends in one backend method:
   - star: `s` key and ★ button both call `PATCH /api/articles/{id}` → `ArticleService.updateState`
   - board add: 📋 Board, 🔖 Read Later and `b` all call `POST /api/boards/{id}/articles` → `BoardService.addArticle`
   - Raindrop: `v` and 💧 both call `POST /api/articles/{id}/raindrop` → `RaindropService.saveToRaindrop`

   Record the engagement in those three methods. **Only "opened the original" needs a new endpoint**, because it happens entirely in the browser.
3. **Every "open original" path is a programmatic `window.open` today, so capture can be complete.** The frontend has no `<a>` elements at all (checked with `grep '<a '` over `src/components`). There are exactly three open sites:
   - `ReadingPane.handleOpenOriginal` (the ↗ Open Original button)
   - `useKeyboardShortcuts` case `'o'`
   - `ReadingPane.handleContentClick`, which intercepts links inside the article body. Those links usually point elsewhere; see Stack Patterns.

   Because they are `<button>`s, middle-click, Cmd/Ctrl-click and the context menu's "Open link in new tab" don't open anything. So no uncapturable path exists. **Keep it that way.** Don't turn the Open Original control into an `<a href>`, because that adds the context-menu path, which can't be observed.
4. **Use a plain JSON `fetch` (optionally `keepalive: true`), not `navigator.sendBeacon`.** `window.open(..., '_blank', 'noopener')` opens a new tab and does not unload the reader page. The unload problem that sendBeacon solves therefore doesn't arise. sendBeacon would also break the project's JSON-only anti-CSRF convention: Chrome throws `SecurityError` for a Blob of type `application/json`. jsdom 29.1.1 doesn't implement it either.

## Recommended Stack

### Core Technologies (all existing; new usage only)

| Technology | Version (current in repo) | New usage in v0.3.0 | Why this, not something new |
|------------|---------------------------|---------------------|-----------------------------|
| PostgreSQL + Flyway | `postgres:latest` (Testcontainers/compose), Flyway via Boot 4.0.8 | `V7__article_engagement.sql`: `article_engagement(article_id BIGINT REFERENCES article(id) ON DELETE CASCADE, kind VARCHAR(16) NOT NULL CHECK (kind IN ('OPEN','STAR','BOARD','RAINDROP')), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY (article_id, kind))` | Mirrors the `article_feedback` shape and cascade in V6. With the composite PK, `INSERT … ON CONFLICT DO NOTHING` is idempotent and race-safe on its own, needing no read-then-write. Keeping one row per kind (instead of one row per article with `GREATEST`) keeps "opened and saved" explainable and lets an un-star remove just the STAR row if requirements want that. "Strongest engagement counts once" is a `MAX(weight)` per article in the CTE; it is not a storage rule. Retention never deletes articles (it only strips content), so rows survive until a feed is deleted, which matches how votes behave. |
| Spring `JdbcClient` | Spring Framework 7 (Boot 4.0.8) | `ArticleEngagementStore` (`record(articleId, kind)`, `delete(articleId, kind)`, `find(articleId)`) | This is the same idiom as `ArticleFeedbackStore`. Spring Data JDBC repositories handle composite keys and upserts poorly. The project already uses `JdbcClient` stores for everything under interest scoring. |
| `InterestScoreQueries.LEARNED_CTE` | existing SQL constant | Add an `engaged` CTE: per article, `MAX(CASE kind WHEN 'OPEN' THEN :openWeight ELSE :saveWeight END)`, `WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = e.article_id)` (thumbs override). Join to SCORED `article_topic_score` with the same hinge. `eff` gets separate `thumbs_learned` and `engagement_learned` columns, each clamped by its own cap, then combined before the existing sign clamp and ±50 range. | The whole learned model is derived at query time. Engagement is just another row source with a fractional vote. No scheduler, no event, no write to `interest_topic`. The existing `learnedSql(...)` binder gains `openWeight`, `saveWeight` and `engagementCap`. |
| `MyfeederProperties.Interest.Blend` | existing `@ConfigurationProperties` | New `engagement.open-weight`, `engagement.save-weight`, `engagement.cap` next to `learn-rate` and `learned-cap` | Same query-time tuning model (D-14): the values live in committed yaml, the replay script tunes them, and there are no Helm or env overrides. Starting values are for calibration to set; only the ordering constraints are fixed: `0 < open < save < 1` (fractions of one thumbs vote) and `engagement.cap < learned-cap` (20). |
| Spring MVC | Boot 4.0.8 | One new endpoint for opens. Recommended: `PUT /api/articles/{id}/engagement/open`, no body, 204. PUT is never a CORS simple request, so it needs no content-type guard (the same reasoning as the existing `DELETE /feedback`). Alternative: `POST /api/articles/{id}/engagement` with `consumes = APPLICATION_JSON_VALUE` and `{kind:"OPEN"}`, following the `PUT /feedback` convention. | A bodyless idempotent PUT fits an idempotent insert. A plain same-origin `fetch` works either way. A form-POST or sendBeacon-style request is exactly what the JSON-only or non-simple-method rule is there to block. |

### Supporting Libraries

**None added.** The browser APIs involved are all built in:

| API | Where | Purpose | When to Use |
|-----|-------|---------|-------------|
| `window.open(url, '_blank', 'noopener')` | existing, 3 call sites | Opens the original | Put it behind one shared helper, `openOriginal(article)`. The button and `o` call it; it calls `window.open` **synchronously first**, which keeps the user-activation gesture, then fires the record call. Never `await` the POST before opening, or the popup blocker kills the open. |
| `fetch(..., { method: 'PUT', keepalive: true })` | new `articlesApi.recordOpen(id)` in `src/api/articles.ts`, via a `client.ts` helper | Records the open | `keepalive` is optional insurance for the rare case where the user closes the reader tab within milliseconds. It has shipped in Chrome and Safari for years and in Firefox since 133 (Nov 2024). The body limit is 64 KiB (ours is ~0 bytes). |
| TanStack Query `useMutation` | existing | `useRecordOpen()` in `hooks/useArticles.ts` (or a new `useEngagement.ts`) | On success, invalidate only `['article', id]` so the badge and "Why N?" refresh. Do **not** invalidate the Priority list query: Priority is frozen while triaging (Phase 5). Follow the thumbs-vote pattern, which sets the "Ranking changed" hint (WR-05). Swallow errors silently; there's no toast, because an open should never nag. |
| React `onAuxClick` | React DOM 19.3 (supported) | Only if an `<a>` is ever introduced | Not needed with the current `<button>` UI. If a link is added later, handle `onClick` (any modifiers) plus `onAuxClick` with `e.button === 1`. Right-click also fires `auxclick` in some browsers, so filter on the button. Accept that context-menu opens are invisible. |

### Development Tools (all existing)

| Tool | Purpose | Notes |
|------|---------|-------|
| Vitest 4.1 + RTL 16.3 + user-event 14.6.7 | Frontend tests for capture | `vi.spyOn(window, 'open').mockImplementation(() => null)` is already used in `useKeyboardShortcuts.test.ts`; `vi.spyOn(globalThis, 'fetch')` is already used in `FeedbackBar.test.tsx` and others. Assert the call order: open first, then fetch. `@testing-library/dom` 10.4.2 has **no** `fireEvent.auxClick`. If auxclick is ever needed, use `user.pointer({ keys: '[MouseMiddle]', target })` (user-event dispatches `auxclick` for non-primary buttons, verified in `system/pointer/mouse.js`) or `fireEvent(el, new MouseEvent('auxclick', { bubbles: true, button: 1 }))`. |
| Testcontainers Postgres | `@DataJdbcTest` for `ArticleEngagementStore` and the extended CTE | Unchanged. Flyway runs V7 at startup. |
| `InterestScoreQueriesTest` fixtures | Blend and learned model tests | The test yaml keeps its fixed values; add fixed engagement values in the test yaml, and add the matching keys to `application-dev.yaml`, so `DevProfileConfigTest` parity holds. |
| `scripts/interest-calibration-replay.sh` / `.sql` + `InterestCalibrationReplaySqlTest` | Calibration and the drift guard | Bash plus psql only. Extend the verbatim SQL and the drift guard to the new CTE and params. No new tooling. |

## Installation

```bash
# Nothing to install.
# Backend: no build.gradle.kts change.
# Frontend: no package.json change.
```

The only new files are a migration, a store, maybe an enum, and a hook or API function:

```
src/main/resources/db/migration/V7__article_engagement.sql
src/main/java/org/bartram/myfeeder/repository/ArticleEngagementStore.java
src/main/java/org/bartram/myfeeder/model/EngagementKind.java        (OPEN, STAR, BOARD, RAINDROP)
src/main/frontend/src/api/articles.ts                                (+ recordOpen)
src/main/frontend/src/hooks/useArticles.ts or useEngagement.ts       (+ useRecordOpen)
```

## Integration Points (where engagement is recorded)

| Signal | Hook point | Notes |
|--------|------------|-------|
| STAR | `ArticleService.updateState` when `starred` is `TRUE` | This method is not `@Transactional` today. Add `@Transactional` if the star write and the engagement insert must be atomic; otherwise insert after the save. Re-starring is a no-op (`ON CONFLICT DO NOTHING`). Un-star: requirements must decide between "keep" (positive-only, sticky) and "delete the STAR row" (reversible). The composite PK supports both. |
| BOARD | `BoardService.addArticle` | Covers 📋 Board, 🔖 Read Later (`getOrCreateByName` + `addArticle`) and `b`. The existing `existsByBoardIdAndArticleId` early return doesn't matter, because the engagement insert is idempotent anyway. |
| RAINDROP | `RaindropService.saveToRaindrop`, **after** `raindropApiClient.createBookmark` returns | Record only on success, so a breaker-open, not-configured or disabled failure never counts. Keep the insert outside the Resilience4j-annotated client bean. |
| OPEN | New `ArticleController` endpoint → `ArticleEngagementStore.record(id, OPEN)` | 404 via `NotFoundException` for a missing article. It is called from the shared `openOriginal` helper used by the ↗ button and `o`. |

None of these touch Jev, the scoring queue, the sweep or `interest_topic`. Articles are not cached in Redis (the only `@Cacheable` is `raindrop-collections`), so there's no cache to evict.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Plain `fetch` (with optional `keepalive`) after a synchronous `window.open` | `navigator.sendBeacon` | Only if the reader page itself navigated away on open, which it never does here. Even then it would need a `text/plain` or form endpoint, because Chrome rejects `application/json` Blobs. That weakens the JSON-only CSRF rule. |
| Server-side capture of saves inside existing services | Client-side "engagement" calls after each save mutation | Never. It duplicates every save path, can drift from the real save outcome (e.g. a failed Raindrop call), and needs frontend work for signals the backend already sees. |
| Server-side capture of saves (direct store call) | Spring `ApplicationEvent` (`ArticleSavedEvent` + `@TransactionalEventListener`) | Only if a second consumer of "saved" events appears. The project uses events for feed scheduling because the scheduler is a separate lifecycle. Engagement is a single idempotent insert, so direct calls are simpler (CLAUDE.md §2). |
| One row per (article, kind) | One row per article, `kind = GREATEST(kind, EXCLUDED.kind)` | If "explain which engagements happened" and "un-star removes the save" are both dropped. It saves a `MAX()` in the CTE but loses information. |
| `<button>` + `window.open` for Open Original (unchanged) | `<a href target="_blank" rel="noopener noreferrer">` with `onClick` + `onAuxClick` | If native middle-click or Cmd-click on Open Original is wanted. The cost is that context-menu opens become uncapturable and `onClick` must not `preventDefault` modified clicks. |
| Derived engagement nudge in the existing `LEARNED_CTE` | Materialized per-topic engagement totals (a table or view refreshed on write) | Only at data sizes this single-user app won't reach. The derived model is what keeps every learned point reversible and explainable (Key Decision, Phase 6). |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `navigator.sendBeacon` | It solves unload, which doesn't happen here (new tab). A JSON Blob throws `SecurityError` in Chrome. It exposes no response. It isn't implemented in jsdom 29.1.1, so tests would need a mock. | `fetch` via `src/api/client.ts` (optionally `keepalive: true`) |
| `await`ing the record call before `window.open` | Once the async gap loses transient user activation, the popup blocker blocks the tab. `window.open` with `noopener` always returns `null`, so the failure can't even be detected. | Open synchronously, then fire and forget the record |
| Analytics or telemetry SDKs (PostHog, Plausible, OpenTelemetry web, `react-ga`, etc.) | This is one idempotent row per article per kind in the app's own Postgres. An SDK adds third-party traffic, bundle weight and a second data store for a single-user homelab app. | `article_engagement` + one endpoint |
| `visibilitychange`, `blur`, `pagehide` or dwell-time heuristics to infer "opened" | Dwell and selection were explicitly rejected as noise (PROJECT.md, v0.3.0). Tab-blur also fires on alt-tab. | Record only the explicit open action |
| Counting `handleContentClick` (in-body links) as opening the original | These links usually point to other pages (sources, related posts), not to the article | Count only the ↗ button and `o`. If the requirements want in-body links counted, count only `link.href === article.url` (normalized). |
| Capturing reader-view toggles or `GET /extracted-content` as engagement | Explicitly out of scope: reader view auto-enables for empty feed items | Nothing |
| Writing learned deltas into `interest_topic.weight` | Breaks the derived, reversible model and the "never writes topic weights" invariant (`ArticleFeedbackStore` javadoc) | Derive in `LEARNED_CTE` |
| Helm `--set` or env overrides for engagement weights and cap | D-14: tune only through committed yaml after a replay | `application.yaml` + `application-dev.yaml` |
| A Spring Data JDBC `CrudRepository<ArticleEngagement, …>` | Composite key plus upsert semantics are awkward. `JdbcClient` stores are the established interest-scoring idiom. | `ArticleEngagementStore` on `JdbcClient` |

## Stack Patterns by Variant

**If Jev is unconfigured or in cold start:**
- Still record engagement, because the rows are cheap and help once scoring starts. The learned CTE already joins only SCORED `article_topic_score`, so unscored engaged articles contribute nothing until they are scored. No special casing is needed.

**If an article has a thumbs vote:**
- The `NOT EXISTS article_feedback` filter in the `engaged` CTE drops its engagement contribution entirely, so the vote overrides it. Removing the vote (DELETE `/feedback`) brings the engagement contribution back automatically. That falls out of the derived model.

**Gap discovery (engaged articles that matched no topic):**
- It is a read-only query: engaged, SCORED articles where no `article_topic_score.noul` exceeds 0.5 (hinge = 0), or which have zero topic rows. It feeds the existing "Create topic from article" draft. No new library; it's a new `JdbcClient` query and a reuse of the existing frontend draft component.

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| `INSERT … ON CONFLICT DO NOTHING` | PostgreSQL ≥ 9.5 | The repo uses `postgres:latest`; prod is external `pg.bartram.org`. The idiom is already used in V6-era stores. |
| `fetch` `keepalive` | Chrome/Edge, Safari, Firefox ≥ 133 | Optional. Where it's unsupported the flag is ignored, and the request still completes because the page doesn't unload. |
| React `onAuxClick` | React DOM 19.3.0 | Supported (`auxclick` is in React's event list). Only relevant if an `<a>` is introduced. |
| `auxclick` event | Baseline 2024 | Filter `button === 1`; right-click can also fire it. |
| `@testing-library/dom` 10.4.2 | — | No `fireEvent.auxClick` helper; use user-event `[MouseMiddle]` or a raw `MouseEvent`. |
| jsdom 29.1.1 | — | No `navigator.sendBeacon` (another reason not to use it). `window.open` exists and is already spied on in tests. |

## Sources

- **Codebase (HIGH, read directly):** `build.gradle.kts`, `src/main/frontend/package.json`, `ReadingPane.tsx` (open, star, board, Read Later and Raindrop handlers), `useKeyboardShortcuts.ts` (`o`, `s`, `v`, `b`), `hooks/useBoards.ts` (`useReadLater`), `api/client.ts`, `ArticleController`, `BoardService.addArticle`, `RaindropService.saveToRaindrop`, `ArticleService.updateState`, `ArticleFeedbackStore`, `InterestScoreQueries.LEARNED_CTE`, `MyfeederProperties` (`learnRate`, `learnedCap`), `V6__interest_scoring.sql`, `application.yaml` (retention only strips content), and `node_modules` checks for user-event `auxclick`, testing-library `auxClick` (absent) and jsdom `sendBeacon` (absent)
- [MDN: Navigator.sendBeacon()](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/sendBeacon): POST only, 64 KiB, boolean return, prefer `fetch` keepalive when properties or a response are needed. Seam tier LOW (single web source); cross-checked with the Chromium thread below, so MEDIUM
- [blink-dev: sendBeacon() with a non-CORS-safelisted Blob type](https://groups.google.com/a/chromium.org/g/blink-dev/c/dAfYF2gauw4) and [cypress#7115](https://github.com/cypress-io/cypress/issues/7115): Chrome throws for `application/json` Blobs. MEDIUM
- [MDN: RequestInit keepalive](https://developer.mozilla.org/en-US/docs/Web/API/RequestInit) + [Firefox 133 release notes for developers](https://developer.mozilla.org/en-US/docs/Mozilla/Firefox/Releases/133) + [Bugzilla 1923044](https://bugzilla.mozilla.org/show_bug.cgi?id=1923044): keepalive semantics, 64 KiB, Firefox 133 support. MEDIUM
- [MDN: Window.open()](https://developer.mozilla.org/en-US/docs/Web/API/Window/open): `noopener` returns `null`, and opening requires a user gesture. MEDIUM
- [MDN: auxclick event](https://developer.mozilla.org/en-US/docs/Web/API/Element/auxclick_event): non-primary buttons, Baseline 2024. MEDIUM
- [tmobile jest-jsdom-browser-compatibility](https://github.com/tmobile/jest-jsdom-browser-compatibility): jsdom lacks sendBeacon, confirmed by grepping jsdom 29.1.1 in `node_modules`. HIGH

---
*Stack research for: engagement-based implicit feedback in myfeeder (v0.3.0)*
*Researched: 2026-09-29*
