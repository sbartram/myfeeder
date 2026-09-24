---
phase: 04-scoring-pipeline-backfill-sweep
reviewed: 2026-09-24T02:29:01Z
depth: standard
files_reviewed: 41
files_reviewed_list:
  - CLAUDE.md
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/App.css
  - src/main/frontend/src/components/InterestsDialog.test.tsx
  - src/main/frontend/src/components/InterestsDialog.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java
  - src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java
  - src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java
  - src/main/java/org/bartram/myfeeder/event/ArticlesIngestedEvent.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - src/main/java/org/bartram/myfeeder/repository/ArticleScoreStore.java
  - src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java
  - src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java
  - src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java
  - src/main/java/org/bartram/myfeeder/service/FeedPollingService.java
  - src/main/java/org/bartram/myfeeder/service/InterestRescoreService.java
  - src/main/java/org/bartram/myfeeder/service/InterestScoringListener.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
  - src/main/java/org/bartram/myfeeder/service/RescoreCount.java
  - src/main/java/org/bartram/myfeeder/service/ScoringFailure.java
  - src/main/java/org/bartram/myfeeder/service/ScoringQueue.java
  - src/main/resources/application.yaml
  - src/test/java/org/bartram/myfeeder/config/TypeSafeConfigTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestRescoreApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestRescoreControllerTest.java
  - src/test/java/org/bartram/myfeeder/integration/JevResilienceTest.java
  - src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java
  - src/test/java/org/bartram/myfeeder/repository/ArticleScoreStoreTest.java
  - src/test/java/org/bartram/myfeeder/scheduler/InterestScoringSweepTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleScoringFlowTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleScoringServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java
  - src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestRescoreServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/ScoringIsolationTest.java
  - src/test/java/org/bartram/myfeeder/service/ScoringQueueTest.java
  - src/test/resources/application.yaml
findings:
  critical: 0
  warning: 3
  info: 9
  total: 12
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-09-24T02:29:01Z
**Depth:** standard
**Files Reviewed:** 41
**Status:** issues_found

## Summary

I reviewed the Phase 4 scoring pipeline end to end: the ingest event, the listener, the queue and executor, the scorer, the store SQL, the sweep, Re-score, the status counts, the Jev client changes (D-14/D-15), the Resilience4j aspect order and YAML (D-05..D-07, D-17), the truncate fix (D-13), and the Interests dialog footer.

The core design holds up:
- One `ELIGIBLE` / `NEEDS_SCORING` predicate is shared by every query.
- `SCORED` is write-once and enforced in SQL (`ON CONFLICT ... WHERE status = 'FAILED'`).
- Transient vs permanent classification matches D-19. I checked it against the SDK's real exception hierarchy: `TypeSafeApiTimeoutException` extends `TypeSafeApiConnectionException`, and `TypeSafeOverloadedException` extends `TypeSafeInternalServerException`.
- The listener and publisher cannot reach the poll's `errorCount` bookkeeping.
- The aspect orders really put the breaker outside the retry. `JevResilienceTest` asserts this against the shipped YAML.

I found no blockers. There are three warnings:
1. A Re-score race can leave an article permanently scored against the rubric the user just replaced.
2. The new destructive, billed `POST /api/interest/rescore` takes no body, so any web page can trigger it with a CORS "simple" request.
3. The status poll runs indefinitely in exactly the states where nothing can drain.

The info items cover dead config defaults, operational side effects of the locked D-05/D-07/D-19 decisions (reported as consequences, not as bugs in the decisions), and small robustness gaps.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Re-score does not re-judge an article that is mid-call; it is stored write-once with the replaced rubric

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java:65-96` (with `src/main/java/org/bartram/myfeeder/repository/ArticleScoreStore.java:112-147`)

**Issue:** The scorer snapshots the profile and topics (lines 66-67) before a `judge()` call. With the 30s timeout and 3 retry attempts, that call can take up to about 93s. It then writes unconditionally (line 96). The failing sequence, which D-04 encourages (save first, then Re-score):
1. Article X has no row, or a FAILED row, and is in flight with profile v1 / old topic set.
2. The user saves profile v2 (or edits or deletes topics).
3. The user confirms Re-score, and `deleteRescoreScope` runs.
4. X's call returns, and `writeScored` INSERTs a SCORED row stamped v1.

X had no SCORED row at delete time, so Re-score never touched it. SCORED is write-once, so X now keeps the replaced rubric's score indefinitely. The only fix is another Re-score, and the same race can recur. INT-05 promises that after Re-score, in-window unread articles are re-judged against the current rubric; that promise does not hold for in-flight articles. The stored `profile_version` shows the mismatch, but nothing acts on it.

**Fix:** After `judge()` returns, discard the result if the rubric changed during the call. Write no row and use no attempt, so the sweep re-picks the article with the current rubric:
```java
// after the judge() try/catch, before isValid/write
InterestProfile nowProfile = interestService.getProfile();
List<InterestTopic> nowTopics = interestService.listTopics();
if (!sameRubric(profile, topics, nowProfile, nowTopics)) { // profile version + topic {id -> version} sets
    log.debug("Rubric changed while scoring article {}; leaving it for the sweep", articleId);
    return;
}
```
An equivalent option is to guard the SQL: add `AND (SELECT version FROM interest_profile WHERE id = 1) IS NOT DISTINCT FROM :profileVersion` to the `INSERT ... SELECT` in `writeScored`. Topic versions would also need checking.

### WR-02: `POST /api/interest/rescore` is a bodyless destructive POST, so any website can trigger it cross-site and cause re-billing

**File:** `src/main/java/org/bartram/myfeeder/controller/InterestRescoreController.java:23-26`; `src/main/frontend/src/api/interest.ts:63`

**Issue:** The endpoint takes no body, and the frontend sends it with no `Content-Type` (`apiPost` omits the header when `body` is undefined, per `client.ts:47`). A body-less POST is a CORS "simple request": browsers send it cross-origin without a preflight. The app has no authentication, so any page the user visits can fire it, for example with an auto-submitting `<form method="post" action="http://<myfeeder-host>/api/interest/rescore">`. Each hit deletes every in-window SCORED and FAILED row, and the sweep then re-bills one Jev call per article. Repeated hits keep the billing going.

The other interest mutations (`PUT /profile`, `POST /topics`, `POST /preview`) require a JSON `@RequestBody`. A cross-site form cannot send `application/json`, so those endpoints are not exposed this way. This is the first endpoint where a simple cross-site request has a direct cost consequence.

**Fix:** Require a JSON body. That forces a CORS preflight for cross-origin callers, which the server does not approve:
```java
public record RescoreRequest(boolean confirm) {}

@PostMapping(value = "/rescore", consumes = MediaType.APPLICATION_JSON_VALUE)
public RescoreCount rescore(@RequestBody RescoreRequest request) {
    if (!request.confirm()) throw new IllegalArgumentException("confirm must be true");
    return service.rescore();
}
```
```ts
rescore: () => apiPost<RescoreCount>('/interest/rescore', { confirm: true }),
```
Alternatively, reject requests whose `Sec-Fetch-Site` is `cross-site`.

### WR-03: The status poll never stops when nothing can drain (unconfigured, cold start, breaker open)

**File:** `src/main/frontend/src/hooks/useInterest.ts:20`; `src/main/frontend/src/components/InterestsDialog.tsx:205-206`

**Issue:** `refetchInterval` polls every 15s whenever `eligibleUnscored > 0`. The server counts `eligibleUnscored` over eligible articles regardless of configuration or cold start (`ArticleScoreStore.counts`). With no TypeSafe key, or in cold start, that count is every unread in-window article, and it never drains because the sweep and scorer are gated off. So the dialog re-runs `/status`, including the full `COUNT ... FILTER` over unread articles, every 15s for as long as it stays open.

The dialog itself hides the "N waiting" line in exactly those states (line 205-206: `configured === true && coldStart === false`). The two gates disagree, and the hook's docstring ("polls every 15s only while articles are waiting to be scored") does not hold. The same happens while the breaker is OPEN. The existing test `statusPollsOnlyWhileArticlesAreWaiting` only covers the configured, warm case.

**Fix:** Poll only when the waiting line is shown and something can drain:
```ts
refetchInterval: (query) => {
  const s = query.state.data
  return s && s.configured && !s.coldStart && s.eligibleUnscored > 0 ? 15_000 : false
},
```
Add test cases for `{configured: false, eligibleUnscored: 5}` and `{coldStart: true, eligibleUnscored: 5}` that assert a single fetch after advancing 15s.

## Info

### IN-01: `sweepDelay` / `sweepInitialDelay` Java defaults are dead; the `@Scheduled` placeholders have no fallback

**File:** `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java:43-44`; `src/main/java/org/bartram/myfeeder/scheduler/InterestScoringSweep.java:41-42`

**Issue:** Nothing reads `Interest.sweepDelay` or `Interest.sweepInitialDelay`. The sweep resolves `${myfeeder.interest.sweep-delay}` and `${myfeeder.interest.sweep-initial-delay}` directly, with no default. The `Duration.ofMinutes(2)` / `ofMinutes(1)` defaults look authoritative but have no effect. Startup fails with an unresolvable placeholder if a config source omits the keys (for example a profile-specific YAML that redefines `myfeeder.interest`).

**Fix:** Either delete the two fields, or add defaults to the placeholders that match them: `${myfeeder.interest.sweep-delay:PT2M}` and `${myfeeder.interest.sweep-initial-delay:PT1M}`.

### IN-02: `ScoringQueue.submit` releases the id only on `TaskRejectedException`

**File:** `src/main/java/org/bartram/myfeeder/service/ScoringQueue.java:60-66`

**Issue:** If `executor.execute` throws any other `RuntimeException` (for example `IllegalStateException` from an uninitialized executor), two things go wrong. The id stays in `inFlight` until restart, so the sweep can never re-enqueue it. The loop also aborts, so the remaining ids in the batch are silently skipped.

**Fix:** Catch `RuntimeException`, remove the id, and count it as dropped. The javadoc already promises "never blocks or throws".

### IN-03: Consequence of D-05 + D-07: slow-call detection and preview latency now span the whole retry chain

**File:** `src/main/resources/application.yaml` (jev `slow-call-duration-threshold: 15s`, `timeout: 30s`, `circuit-breaker-aspect-order: 1`)

**Issue:** With the breaker outside the retry, one breaker outcome covers up to 3 × 30s attempts plus 1s + 2s of backoff, or Retry-After waits of up to 10s each. Two effects follow:
- A timed-out attempt followed by a successful retry is now recorded as a successful slow call, not a failure.
- Two capped 429 waits alone push a call past the 15s slow threshold.

D-06's rationale ("must stay below the timeout") was written for per-attempt timing. The user-facing `POST /api/interest/preview` can also block the request thread for about 93s before returning 503. These results follow from locked decisions and are not defects in them. Record them in the Phase 7 tuning notes, and consider a shorter per-call budget for the preview if the UI wait proves painful.

### IN-04: Consequence of D-19: an article that consistently times out is retried every sweep with no bound

**File:** `src/main/java/org/bartram/myfeeder/service/ScoringFailure.java:30-38`; `src/main/java/org/bartram/myfeeder/service/ArticleScoringService.java:76-86`

**Issue:** A timeout is transient: no row, no attempt. A request that deterministically hangs for one specific article is therefore retried 3 times on every 2-minute sweep, indefinitely. Newest-first ordering keeps selecting it while it stays in the window. If timed-out requests are billed server-side, this costs about 2,000 calls per day per poison article. The breaker only pauses this when such articles make up a large share of calls. This is documented behavior. Consider a Phase 7 safeguard, such as a separate transient counter or a per-article cooldown.

### IN-05: The Re-score confirmation gates on `isFetching`, so a background refetch hides it

**File:** `src/main/frontend/src/components/InterestsDialog.tsx:258-260`

**Issue:** `useRescoreCount` has `staleTime: 0`, and the QueryClient keeps `refetchOnWindowFocus` enabled. Returning to the tab while the confirmation is open therefore swaps it for "Counting articles…". That includes the in-flight "Re-scoring…" state and any mutation error. Nothing is double-posted, but the pending and error state disappears temporarily.

**Fix:** Use `count.isPending` (first load) for the placeholder, or set `refetchOnWindowFocus: false` on this query. Remounting already guarantees a fresh count (D-02).

### IN-06: The scoring executor interrupts an in-flight, possibly billed Jev call on shutdown

**File:** `src/main/java/org/bartram/myfeeder/config/InterestScoringConfig.java:30-39`

**Issue:** By default `waitForTasksToCompleteOnShutdown` is false, so every deploy or restart interrupts the running call. That article is judged again after startup. Consider `setWaitForTasksToCompleteOnShutdown(true)` with `setAwaitTerminationSeconds(35)` (about one attempt timeout), or accept the cost and document it.

### IN-07: `truncate` throws for `max == 1`, and the `cut <= 0` guard is misleading

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java:69-73`

**Issue:** When `max == 1`, `cut` becomes 0 and `text.charAt(cut - 1)` throws `StringIndexOutOfBoundsException`. The only caller passes 1500, so this is latent. After the D-13 change `cut` is either -1 or at least `max / 2`, so `cut <= 0` really means `cut < 0`.

**Fix:** Use `if (cut < 0)`, and guard `cut > 0` before calling `charAt(cut - 1)`, or assert `max >= 2`.

### IN-08: `ArticlesIngestedEvent.feedId` is never read

**File:** `src/main/java/org/bartram/myfeeder/event/ArticlesIngestedEvent.java:6`

**Issue:** The listener only uses `articleIds()`. This is harmless but unused. Keep it if Phase 5/7 logging will use it; otherwise drop it.

### IN-09: The project docs don't mention the new components; the dialog help text still says "primarily about"

**File:** `CLAUDE.md` (Package Structure / Key Behaviors); `src/main/frontend/src/components/InterestsDialog.tsx:32-33`

**Issue:**
- CLAUDE.md's package map and Key Behaviors don't list `ArticlesIngestedEvent`, `InterestScoringListener`, `ScoringQueue`, `ArticleScoringService`, `ArticleScoreStore`, `InterestScoringSweep`, `InterestScoringConfig` or `InterestRescoreController`, nor the rule that polling only publishes the event. OPS-03 is Phase 7, but the event-driven scoring hand-off is exactly the kind of "never call X directly" rule the file records for feed scheduling.
- `TOPICS_HELP` tells users to describe what an article is "primarily about", while the scorer sends the v2 "substantially about" wording. This is the deferred 03 copy fix, still open in a file this phase edited.

---

_Reviewed: 2026-09-24T02:29:01Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
