---
phase: 03-interest-model-schema-rubric-editor
reviewed: 2026-09-23T21:19:09Z
depth: standard
files_reviewed: 52
files_reviewed_list:
  - CLAUDE.md
  - src/main/frontend/src/api/client.test.ts
  - src/main/frontend/src/api/client.ts
  - src/main/frontend/src/api/interest.ts
  - src/main/frontend/src/App.css
  - src/main/frontend/src/App.tsx
  - src/main/frontend/src/components/InterestsDialog.test.tsx
  - src/main/frontend/src/components/InterestsDialog.tsx
  - src/main/frontend/src/components/SettingsDialog.test.tsx
  - src/main/frontend/src/components/SettingsDialog.tsx
  - src/main/frontend/src/components/TopicRow.test.tsx
  - src/main/frontend/src/components/TopicRow.tsx
  - src/main/frontend/src/hooks/useInterest.ts
  - src/main/frontend/src/queryClient.test.ts
  - src/main/frontend/src/queryClient.ts
  - src/main/frontend/src/utils/interest.test.ts
  - src/main/frontend/src/utils/interest.ts
  - src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java
  - src/main/java/org/bartram/myfeeder/controller/InterestController.java
  - src/main/java/org/bartram/myfeeder/controller/InterestPreviewController.java
  - src/main/java/org/bartram/myfeeder/controller/InterestStatusController.java
  - src/main/java/org/bartram/myfeeder/controller/ProfileUpdateRequest.java
  - src/main/java/org/bartram/myfeeder/controller/TopicPreviewRequest.java
  - src/main/java/org/bartram/myfeeder/controller/TopicRequest.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClient.java
  - src/main/java/org/bartram/myfeeder/integration/JevApiClientImpl.java
  - src/main/java/org/bartram/myfeeder/model/InterestProfile.java
  - src/main/java/org/bartram/myfeeder/model/InterestTopic.java
  - src/main/java/org/bartram/myfeeder/repository/InterestProfileRepository.java
  - src/main/java/org/bartram/myfeeder/repository/InterestTopicRepository.java
  - src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java
  - src/main/java/org/bartram/myfeeder/service/InterestPreviewService.java
  - src/main/java/org/bartram/myfeeder/service/InterestQuestions.java
  - src/main/java/org/bartram/myfeeder/service/InterestService.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatus.java
  - src/main/java/org/bartram/myfeeder/service/InterestStatusService.java
  - src/main/java/org/bartram/myfeeder/service/TopicPreviewResponse.java
  - src/main/resources/db/migration/V6__interest_scoring.sql
  - src/test/java/org/bartram/myfeeder/controller/InterestApiIntegrationTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/InterestPreviewControllerTest.java
  - src/test/java/org/bartram/myfeeder/integration/CalibrationStats.java
  - src/test/java/org/bartram/myfeeder/integration/CalibrationStatsTest.java
  - src/test/java/org/bartram/myfeeder/integration/InterestCalibrationSpikeTest.java
  - src/test/java/org/bartram/myfeeder/repository/InterestTopicRepositoryTest.java
  - src/test/java/org/bartram/myfeeder/repository/V6InterestScoringMigrationTest.java
  - src/test/java/org/bartram/myfeeder/service/ArticleStateBuilderTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestJudgeRequestTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestPreviewServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestQuestionsTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestServiceTest.java
  - src/test/java/org/bartram/myfeeder/service/InterestStatusServiceTest.java
findings:
  critical: 1
  warning: 5
  info: 6
  total: 12
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-09-23T21:19:09Z
**Depth:** standard
**Files Reviewed:** 52
**Status:** issues_found

## Narrative Findings (AI reviewer)

## Summary

I reviewed the whole interest-model slice:
- the V6 schema, the entities and repositories
- `InterestService` (limits and version rules), `InterestQuestions` and `ArticleStateBuilder`
- the preview and status services and controllers, and the new `GlobalExceptionHandler` mappings
- the frontend Interests dialog, `TopicRow`, the hooks, the API client and the query client
- the committed CLAUDE.md changes (diff from `81c65a3^..HEAD`, excluding the user's uncommitted edits)

The backend validation and fixed-text error handling are careful. The exception mappings match CLAUDE.md, and the preview runs all its validation before `judge()`.

The main defect is in `ArticleStateBuilder.truncate`. The builder is shared by the preview and the Phase 4 scorer. Its word-boundary search has no lower bound, so a long summary with no whitespace near the end (CJK text, long URLs) can shrink to one or two characters plus an ellipsis. That silently corrupts what Jev judges.

On the frontend:
- Typed text is lost when the user edits a row while its save is in flight.
- A discarded draft whose save was already sent still gets created on the server.
- Global keyboard shortcuts (j/k/m/s/Tab/Esc) still act on the app behind the modal. This changes the preview target and blocks Tab navigation.
- A failed article fetch shows "loading article…" with no end.
- Our own 400/404 validation errors are reported to the user as Jev failures.

I did not re-report the items that were already deferred: WR-01 (IllegalArgumentException not on the jev breaker ignore list), the jsoup 1.11.2 CVE with its 50k cap, the 5s Jev timeout, and the "primarily about" help text.

## Critical Issues

### CR-01: `truncate` word-boundary search has no lower bound, so almost the whole summary can be dropped

**File:** `src/main/java/org/bartram/myfeeder/service/ArticleStateBuilder.java:56-74`
**Issue:** The loop `for (int i = max - 2; i > 0; i--)` takes the *last* whitespace anywhere in the first `max - 1` characters, however early it is.

Example: a summary of 1,600 characters whose only whitespace is at index 2 (Chinese, Japanese or Thai text, or `"Link: " + <long URL>`). It truncates to `"标题…"` or `"Link:…"`, so about 1,497 of the allowed 1,500 characters are thrown away. The fallback that cuts at `max - 1` only runs when there is *no* whitespace at all.

The builder is shared "verbatim" with the Phase 4 scorer (D-12), so these articles would be scored almost from the title alone. Nothing reports it. The existing tests (`ArticleStateBuilderTest` lines 74-112) only use whitespace-dense English-like input, so they don't catch it.
**Fix:** Only accept a whitespace cut near the limit, and otherwise hard-cut:
```java
static String truncate(String text, int max) {
    if (text.length() <= max) {
        return text;
    }
    int floor = max - 1 - Math.max(1, max / 10); // look back at most ~10% (150 chars)
    int cut = -1;
    for (int i = max - 2; i > floor && i > 0; i--) {
        if (Character.isWhitespace(text.charAt(i))) {
            cut = i;
            break;
        }
    }
    if (cut <= 0) {
        cut = max - 1;
        if (Character.isHighSurrogate(text.charAt(cut - 1))) {
            cut--;
        }
    }
    return text.substring(0, cut).stripTrailing() + '…';
}
```
Add a test with a CJK summary (for example `"标题 " + "字".repeat(2000)`) asserting that the result is at least `max - 1 - max/10` characters long.

## Warnings

### WR-01: Edits typed while a topic save is in flight are silently overwritten

**File:** `src/main/frontend/src/components/TopicRow.tsx:260-267`, `303-329`; `src/main/frontend/src/components/InterestsDialog.tsx:330-339`
**Issue:** The name, description and weight inputs stay enabled while `create`/`update` is pending. When the save resolves, `onSaved` → `markSaved` replaces the entire row (`name`, `description`, `weightText`, `weight`, `saved`) with the server's copy. Anything typed between clicking Save and the response is lost. The row then shows as clean, so the close guard doesn't warn either. With the Jev-free save this window is short, but it is real under a slow DB or network.
**Fix:** Either disable the three inputs and the slider while `saving`, or have `markSaved` update only `id` and `saved` and keep the current text:
```ts
const markSaved = (key: string, topic: InterestTopic) =>
  updateRow(key, (r) => ({
    ...r,
    id: topic.id,
    saved: { name: topic.name, description: topic.description, weight: topic.weight },
  }))
```
With this version, `isTopicDirty` still flags any text typed after the request was sent.

### WR-02: "Discard draft" while a create is pending leaves a server-side topic that the dialog doesn't show

**File:** `src/main/frontend/src/components/TopicRow.tsx:383-386`, `260-264`
**Issue:** "Discard draft" is not disabled while `create.isPending`. If you click Save topic and then Discard draft:
- `removeRow` unmounts the row, but the POST still completes and the topic is persisted.
- The hook-level `onSuccess` appends it to the `['interest','topics']` cache, but `TopicsSection` seeded its rows once and never re-reads the cache. The per-call `onSuccess: onSaved` doesn't run for an unmounted observer, and `markSaved` would find no matching key anyway.

So the user thinks they discarded the topic, but it exists and will be scored. The `N / 25` counter under-counts until the dialog is reopened.
**Fix:** Disable Discard draft while `saving` (`disabled={saving}`). Alternatively, have the discard path wait for the pending create to finish and then delete the returned id.

### WR-03: Global keyboard shortcuts stay active behind the aria-modal Interests dialog

**File:** `src/main/frontend/src/components/InterestsDialog.tsx:59`, `98-106` (interaction with `src/main/frontend/src/hooks/useKeyboardShortcuts.ts:49-56,170-177`)
**Issue:** The document-level shortcut handler only ignores INPUT/TEXTAREA/SELECT targets. When focus is on any dialog button (Save profile, Preview topic, Keep editing, and so on), which is where focus lands after every click:
- `Tab` is `preventDefault`-ed and cycles the panels behind the modal, so keyboard users can't tab through the dialog's controls.
- `j`/`k`/`n`/`p` change `selectedArticleId`. That silently switches the live preview target, and existing preview results go stale.
- `m`/`s`/`b`/`v` mark read, star or save the article behind the modal.
- `Esc` clears the preview target. The comment on line 59 acknowledges this but doesn't prevent it.

The dialog declares `aria-modal="true"` but doesn't behave as a modal.
**Fix:** Suppress global shortcuts while any modal is open. For example, return early in `handleKeyDown` when `(e.target as HTMLElement).closest('[aria-modal="true"]')` is non-null. Or pass an `enabled` flag or UI-store bit that MainLayout sets whenever `interestsOpen` is true. Bind Esc inside the dialog to `requestClose`.

### WR-04: A failed article fetch shows "Previewing against: loading article…" with no end

**File:** `src/main/frontend/src/components/InterestsDialog.tsx:361-369`
**Issue:** The fallback branch renders "loading article…" whenever `article.data` is falsy, including when `useArticle` has *failed*. For example, the selected article was deleted by retention (404), or the network failed. Preview stays enabled because `computePreviewBlock` only checks `selectedArticleId`. The user then gets a generic "Preview failed: Article not found…" instead of being told up front.
**Fix:** Add an `article.isError` branch, for example `Preview unavailable: couldn't load the selected article.`. Feed it into `computePreviewBlock` so the per-row Preview button is disabled with that reason.

### WR-05: The app's own 400/404 validation errors are reported as Jev failures, with wrong advice

**File:** `src/main/frontend/src/components/TopicRow.tsx:71-84`
**Issue:** `previewErrorMessage` maps every 400/422 to "Jev couldn't judge this article (…). Try rewording the description." But the 400s come from `InterestPreviewService` validation *before* Jev is called:
- "This article has no text to judge"
- "articleId is required"
- "description can be at most 500 characters"

For "no text to judge", rewording can never help. A 404 (article gone) falls to the generic "Try Preview topic again", which also can't succeed. Only 422 means Jev rejected the request (`handleJevRejected`).
**Fix:** Separate 400 from 422, and treat 404 on its own:
```ts
if (error.status === 422) return `Preview failed: Jev couldn't judge this article (${error.message}). Try rewording the description.`
if (error.status === 404) return 'Preview failed: the selected article no longer exists. Open another article.'
if (error.status === 400) return `Preview failed: ${error.message}.`
```

## Info

### IN-01: Version bumps are unsynchronized read-modify-writes

**File:** `src/main/java/org/bartram/myfeeder/service/InterestService.java:44-58`, `90-106`
**Issue:** `updateProfile` and `updateTopic` read the row, compute `version + 1` in memory and save, with no transaction or row lock. Two concurrent saves with different text (two tabs) can both write version N+1. Phase 4 staleness checks (`profile_version`/`topic_version`) would then treat two different texts as the same version. This matters less for a single-user app, but the version is the staleness key.
**Fix:** Bump the version in SQL (`UPDATE … SET version = version + 1 … WHERE id = ? AND profile_text <> ?`), or use `@Transactional` together with `SELECT … FOR UPDATE`.

### IN-02: `CallNotPermittedException` handler says "Jev" for any circuit breaker

**File:** `src/main/java/org/bartram/myfeeder/controller/GlobalExceptionHandler.java:82-88`
**Issue:** The handler is global. Raindrop currently converts breaker-open through its fallback, but any future `@CircuitBreaker` without a fallback would surface as "Jev is temporarily unavailable".
**Fix:** Branch on `ex.getCausingCircuitBreakerName()` and keep the Jev text only for `"jev"`, with a generic "Service temporarily unavailable" otherwise.

### IN-03: `formatSigned` shows a signed zero for small preview contributions

**File:** `src/main/frontend/src/utils/interest.ts:4-9`
**Issue:** `formatSigned(-0.04, 1)` returns `"−0.0"` and `formatSigned(0.04, 1)` returns `"+0.0"`, because the sign is chosen from the raw value but the magnitude is rounded. A small hinge × weight contribution renders as `= −0.0 pts`.
**Fix:** Round first, for example `const r = Number(n.toFixed(digits))`, then choose the sign from `r`.

### IN-04: CLAUDE.md package map was only partly updated

**File:** `CLAUDE.md:55-56` (committed HEAD)
**Issue:** The committed diff added the interest classes to the `model/`, `repository/` and `controller/` lines. The `service/` line still omits `InterestService`, `InterestPreviewService`, `InterestQuestions`, `ArticleStateBuilder`, `InterestStatusService`, `InterestStatus` and `TopicPreviewResponse`. The `integration/` line omits `JevApiClient`/`JevApiClientImpl`/`JevJudgment`/`JevNotConfiguredException`.
**Fix:** Add them to the two lines. The new "Interest Ranking" section already documents them.

### IN-05: Negative "no extra requests" assertion relies on a 50 ms sleep

**File:** `src/main/frontend/src/components/InterestsDialog.test.tsx:729`
**Issue:** `previewTriggersNoOtherRequests` waits a fixed 50 ms before asserting that no follow-up request fired. A slower CI runner could let a late invalidation or refetch land after the assertion, so the test would pass even though the bug exists.
**Fix:** After the preview result appears, wait until `queryClient.isFetching() === 0 && queryClient.isMutating() === 0`, rather than sleeping.

### IN-06: Preview `topicId` is accepted without validation

**File:** `src/main/java/org/bartram/myfeeder/service/InterestPreviewService.java:61`
**Issue:** `topicId` is only used to name the question key (`topic_<id>`). Any value is accepted, including negative or non-existent ids. It is harmless today because nothing is persisted, but it means the preview's "same key as scoring" claim isn't enforced.
**Fix:** Either document that the key name is cosmetic, or check `topicId == null || topicRepository.existsById(topicId)`.

---

_Reviewed: 2026-09-23T21:19:09Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
