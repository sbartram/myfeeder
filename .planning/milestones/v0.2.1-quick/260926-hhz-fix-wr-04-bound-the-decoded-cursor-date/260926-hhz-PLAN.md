---
phase: quick-260926-hhz
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/java/org/bartram/myfeeder/controller/PriorityPage.java
  - src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java
  - src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
  - src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java
autonomous: true
requirements: [PRIO-06]

estimate:
  tokens: 30000
  raw_tokens: 30000
  tasks: 1
  confidence: low

must_haves:
  truths:
    - "GET /api/articles/priority?before=<cursor whose date is Long.MIN_VALUE micros and whose id names an existing article> returns 404 with detail 'Priority cursor not recognized' instead of a 500 from Postgres (WR-04)"
    - "PriorityPage.decodeCursor throws NotFoundException(UNREADABLE_CURSOR) for Long.MIN_VALUE and Long.MAX_VALUE micros, and for dates 1 microsecond outside 0001-01-01T00:00:00Z..9999-12-31T23:59:59.999999Z"
    - "Cursors dated exactly 0001-01-01T00:00:00Z and 9999-12-31T23:59:59.999999Z still round-trip through encodeCursor/decodeCursor (the bound is inclusive)"
    - "An out-of-range cursor never reaches PriorityService (the controller rejects it at decode time)"
    - "The existing Priority cursor tests (round trip, pinned wire format, ranked walk, score-drop walk, missing cursor 404) still pass"
  artifacts:
    - path: "src/main/java/org/bartram/myfeeder/controller/PriorityPage.java"
      provides: "decodeCursor rejects decoded dates outside 0001-01-01T00:00:00Z..9999-12-31T23:59:59.999999Z with the fixed-text 404"
      contains: "9999-12-31T23:59:59.999999Z"
    - path: "src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java"
      provides: "Long.MIN_VALUE/Long.MAX_VALUE micros in unreadableCursorIsNotFound, plus inclusive-edge and 1-microsecond-outside cases"
      contains: "Long.MIN_VALUE"
    - path: "src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java"
      provides: "WebMvc test: out-of-range cursor date is 404 with the fixed detail and never calls the service"
    - path: "src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java"
      provides: "Postgres-backed reproduction of WR-04: existing article id + Long.MIN_VALUE micros is a 404"
  key_links:
    - from: "ArticleController.priority (before query param)"
      to: "PriorityPage.decodeCursor"
      via: "decodeCursor(before) before priorityService.page(...)"
      pattern: "PriorityPage.decodeCursor\\(before\\)"
    - from: "PriorityPage.decodeCursor"
      to: "GlobalExceptionHandler NotFoundException -> 404"
      via: "throw new NotFoundException(UNREADABLE_CURSOR)"
      pattern: "NotFoundException\\(UNREADABLE_CURSOR\\)"
---

<objective>
Close 05-REVIEW WR-04: a Priority cursor whose decoded date is outside Postgres' timestamptz range passes `PriorityPage.decodeCursor`, passes `existsById`, then fails inside `InterestScoreQueries.priorityPageAfter` with a `DataAccessException` ("timestamp out of range") that `GlobalExceptionHandler` does not map, so the client gets a 500 and the server logs an ERROR stack trace. That breaks the decode contract ("anything it cannot read is a 404 with fixed text") and the R4 restart signal (`PriorityList` restarts only on 404).

Fix exactly as the review recommends: after decoding, reject any date before 0001-01-01T00:00:00Z or after 9999-12-31T23:59:59.999999Z (both edges inclusive and valid) with the existing `NotFoundException(UNREADABLE_CURSOR)`, the same path every other malformed cursor takes. TDD: failing tests first (RED commit), then the fix (GREEN commit).

Purpose: a crafted cursor (the app has no auth) must get the documented fixed-text 404, never a 500.
Output: bounded `decodeCursor` + unit, WebMvc and Postgres-backed tests.
</objective>

<execution_context>
@/Users/scottb/orca/workspaces/myfeeder/main/.claude/gsd-core/workflows/execute-plan.md
@/Users/scottb/orca/workspaces/myfeeder/main/.claude/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@CLAUDE.md
@src/main/java/org/bartram/myfeeder/controller/PriorityPage.java
@src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java

<interfaces>
Current state, extracted so the executor does not need to explore.

PriorityPage (controller package), relevant members:
- `static final String UNREADABLE_CURSOR = "Priority cursor not recognized";` (package-private, tests use it)
- `public static String encodeCursor(SortKey key)` builds `<Double.toString(score)>|<epochMicros>|<id>`, base64url without padding. It computes micros with `Math.multiplyExact`/`Math.addExact`, so it throws ArithmeticException for Long.MIN_VALUE micros. Tests that need Long.MIN_VALUE must build the raw text and base64url it, not call encodeCursor.
- `public static SortKey decodeCursor(String cursor)`: splits on `|` (limit -1), requires 3 parts, parses the score (rejects NaN), `long micros = Long.parseLong(parts[1])`, `long id = Long.parseLong(parts[2])`, then returns `new SortKey(score, Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id)`. Everything sits inside a try that catches `IllegalArgumentException | ArithmeticException | DateTimeException` and rethrows `new NotFoundException(UNREADABLE_CURSOR)`. `NotFoundException extends RuntimeException` (not IllegalArgumentException), so throwing it inside the try propagates unchanged, as the existing parts-length and NaN checks already do.

SortKey is `InterestScoreQueries.SortKey(double score, Instant date, long id)` (a record).

ArticleController.priority: `SortKey after = before != null ? PriorityPage.decodeCursor(before) : null;` then `PriorityPage.of(priorityService.page(after, safeLimit + 1), safeLimit)`. PriorityService.page checks `articleRepository.existsById(after.id())` (404 if missing), then calls `interestScoreQueries.priorityPageAfter(after, limit)`, which binds the date as `after.date().atOffset(ZoneOffset.UTC)` into `CAST(:cursorDate AS timestamptz)`.

Existing tests to extend:
- PriorityPageTest.unreadableCursorIsNotFound: loops over a `List<String> inputs` (uses the private helper `b64(String)` = base64url without padding of UTF-8 text) and asserts each throws NotFoundException whose message equals UNREADABLE_CURSOR and does not contain the input.
- ArticleControllerTest (@WebMvcTest, `@MockitoBean PriorityService priorityService`): `priorityUnreadableCursorIs404WithoutCallingTheService` performs `get("/api/articles/priority?before=12345")`, expects 404 and `jsonPath("$.detail").value("Priority cursor not recognized")`, then `verify(priorityService, never()).page(any(), anyInt())`.
- PriorityApiIntegrationTest (@SpringBootTest + TestcontainersConfiguration, MockMvc via webAppContextSetup): setUp deletes rows for PRIORITY_FEED_URL; helpers `insertFeed()` returns a feed id and `insertArticle(feedId, guid, publishedAt, read)` returns an article id. `missingCursorIs404` is the closest analog. It imports `get` and `status` statically but not `jsonPath`, and does not import Base64/StandardCharsets.
</interfaces>
</context>

<tasks>

<task type="tracer" tdd="true">
  <name>Task 1: Out-of-range cursor date is a fixed-text 404 end to end (RED, then GREEN)</name>
  <files>src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java, src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java, src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java, src/main/java/org/bartram/myfeeder/controller/PriorityPage.java</files>
  <precondition>Docker is running (PriorityApiIntegrationTest uses Testcontainers Postgres), and the shell running Gradle does not export SPRING_AI_TYPESAFE_* or SPRING_PROFILES_ACTIVE=dev (CLAUDE.md Gotchas).</precondition>
  <behavior>
    - PriorityPageTest.unreadableCursorIsNotFound: the inputs list also contains b64("1|" + Long.MIN_VALUE + "|1") and b64("1|" + Long.MAX_VALUE + "|1"); each throws NotFoundException with message UNREADABLE_CURSOR that does not echo the input.
    - PriorityPageTest new test cursorDateOneMicrosecondOutsideTheBoundsIsNotFound: encodeCursor(new SortKey(1.0, Instant.parse("0001-01-01T00:00:00Z").minus(1, ChronoUnit.MICROS), 1)) and encodeCursor(new SortKey(1.0, Instant.parse("9999-12-31T23:59:59.999999Z").plus(1, ChronoUnit.MICROS), 1)) each decode to NotFoundException with message UNREADABLE_CURSOR.
    - PriorityPageTest new test cursorDateBoundsAreInclusive: SortKeys dated exactly Instant.parse("0001-01-01T00:00:00Z") and Instant.parse("9999-12-31T23:59:59.999999Z") round-trip through encodeCursor then decodeCursor and are equal to the original. This one passes before the fix too; it guards against an over-tight bound.
    - ArticleControllerTest new test priorityOutOfRangeCursorDateIs404WithoutCallingTheService: before = base64url (no padding) of "1.0|" + Long.MIN_VALUE + "|7"; expect status 404 and $.detail "Priority cursor not recognized"; verify priorityService.page is never called. Before the fix the mock returns an empty list, so this fails on the status assertion (200 vs 404).
    - PriorityApiIntegrationTest new test outOfRangeCursorDateIs404: insert a feed and one unread article (this is the review's exact reproduction: the id exists, so existsById passes); before = base64url (no padding) of "1.0|" + Long.MIN_VALUE + "|" + articleId; expect status 404 and $.detail "Priority cursor not recognized". Before the fix this reproduces WR-04: MockMvc surfaces the unmapped DataAccessException ("timestamp out of range") as an exception from perform(), because GlobalExceptionHandler has no mapping for it.
  </behavior>
  <action>
RED (write tests, no production change yet):
1. PriorityPageTest: add the two Long.MIN_VALUE / Long.MAX_VALUE entries to the inputs list of unreadableCursorIsNotFound (review's suggested inputs), using the existing b64 helper. Add the two new tests from the behavior block (cursorDateOneMicrosecondOutsideTheBoundsIsNotFound, cursorDateBoundsAreInclusive). Use literal Instant.parse values in the tests, not the production constants, so a wrong constant is caught. Add the ChronoUnit import.
2. ArticleControllerTest: add priorityOutOfRangeCursorDateIs404WithoutCallingTheService next to priorityUnreadableCursorIs404WithoutCallingTheService, mirroring its assertions. Build the cursor with java.util.Base64 getUrlEncoder().withoutPadding() over the UTF-8 text; do not use encodeCursor (it overflows at Long.MIN_VALUE). Add imports as needed.
3. PriorityApiIntegrationTest: add outOfRangeCursorDateIs404 next to missingCursorIs404, using insertFeed() and insertArticle(feedId, "wr04", Instant.now(), false), then the raw base64url cursor built the same way. Assert status 404 and the fixed detail (static-import MockMvcResultMatchers.jsonPath; add Base64 and StandardCharsets imports).
4. Run the verify command. Confirm the RED is the intended one: in PriorityPageTest, the Long.MIN_VALUE/Long.MAX_VALUE inputs and both 1-microsecond-outside cases fail on the isNotNull assertion (decode returned a SortKey instead of throwing), and cursorDateBoundsAreInclusive passes. ArticleControllerTest fails on the status assertion (expected 404, got 200). PriorityApiIntegrationTest.outOfRangeCursorDateIs404 fails because perform() throws with a "timestamp out of range" DataAccessException as the root cause. That is the WR-04 bug itself; record it as the reproduction. The two assertion failures above are the RED evidence. If any target case passes before the fix, or fails for another reason (compile error, context load failure, Docker unavailable), stop and fix the test. Do not proceed to GREEN.
5. Commit only the three test files: test(260926-hhz): out-of-range Priority cursor date is a 500, not the 404 (WR-04).

GREEN (per the 05-REVIEW WR-04 fix):
6. PriorityPage: add private static final Instant MIN_DATE = Instant.parse("0001-01-01T00:00:00Z") and MAX_DATE = Instant.parse("9999-12-31T23:59:59.999999Z"). In decodeCursor, compute the date as Instant.EPOCH.plus(micros, ChronoUnit.MICROS) into a local variable. Then, inside the existing try and before building the SortKey, throw new NotFoundException(UNREADABLE_CURSOR) when date.isBefore(MIN_DATE) or date.isAfter(MAX_DATE). The edges are inclusive. Reuse the existing constant and exception so the detail text and the 404 stay identical to every other unreadable cursor. Do not add a new exception type, a GlobalExceptionHandler mapping for DataAccessException, or a clamp: clamping would change the tuple the next page compares against and could skip rows.
7. Update the decodeCursor Javadoc with one or two sentences. Say that the decoded date must lie in 0001-01-01T00:00:00Z..9999-12-31T23:59:59.999999Z inclusive, and that anything outside is unreadable, because Postgres timestamptz cannot hold the far ends of the long-micros range (Long.MIN_VALUE is about 290308 BC) and binding one would fail in SQL as a 500 (WR-04). Leave encodeCursor, the wire format and the class-level Javadoc unchanged.
8. Run the verify command. All tests, new and existing, in the three classes must pass, including cursorRoundTripsExactly and cursorWireFormatIsPinned. Commit only PriorityPage.java: fix(260926-hhz): bound the decoded Priority cursor date to years 1..9999 (WR-04).

Stay on the current branch (sbartram/main). Do not check out other commits. Touch nothing outside the four listed files.
  </action>
  <verify>
    <automated>cd /Users/scottb/orca/workspaces/myfeeder/main && ./gradlew test --tests "org.bartram.myfeeder.controller.PriorityPageTest" --tests "org.bartram.myfeeder.controller.ArticleControllerTest" --tests "org.bartram.myfeeder.controller.PriorityApiIntegrationTest"</automated>
  </verify>
  <acceptance_criteria>
    - grep -c "9999-12-31T23:59:59.999999Z" src/main/java/org/bartram/myfeeder/controller/PriorityPage.java returns at least 1, and grep -c "0001-01-01T00:00:00Z" on the same file returns at least 1
    - grep -c "Long.MIN_VALUE" src/test/java/org/bartram/myfeeder/controller/PriorityPageTest.java and grep -c "Long.MAX_VALUE" on the same file each return at least 1
    - grep -c "outOfRangeCursorDateIs404" src/test/java/org/bartram/myfeeder/controller/PriorityApiIntegrationTest.java returns 1
    - grep -c "priorityOutOfRangeCursorDateIs404WithoutCallingTheService" src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java returns 1
    - git log --oneline -2 shows the test(260926-hhz) commit immediately before the fix(260926-hhz) commit
    - The verify command exits 0
  </acceptance_criteria>
  <done>A crafted cursor with an out-of-range date (Long.MIN_VALUE or Long.MAX_VALUE micros, or 1 microsecond past either edge) gets the same fixed-text 404 as any other unreadable cursor, at both the WebMvc and the real-Postgres level, and never reaches the service or SQL. Cursors dated exactly at 0001-01-01T00:00:00Z and 9999-12-31T23:59:59.999999Z still round-trip. RED and GREEN are separate commits, in that order.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| client -> GET /api/articles/priority | The `before` query param is untrusted opaque text from any client (the app has no auth) and is decoded into a SQL-bound sort tuple |
| PriorityPage.decodeCursor -> Postgres | The decoded date is bound as timestamptz, which holds a narrower range than Java Instant or long micros |

## STRIDE Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation Plan |
|-----------|----------|-----------|----------|-------------|-----------------|
| T-hhz-01 | Denial of Service | PriorityPage.decodeCursor -> InterestScoreQueries.priorityPageAfter | medium | mitigate | Reject decoded dates outside 0001-01-01T00:00:00Z..9999-12-31T23:59:59.999999Z with NotFoundException(UNREADABLE_CURSOR) before any service or SQL call, so a crafted cursor cannot raise an unmapped DataAccessException (500 plus an ERROR stack trace per request). Proven by PriorityApiIntegrationTest.outOfRangeCursorDateIs404 against real Postgres and by the WebMvc never-calls-the-service check. |
| T-hhz-02 | Information Disclosure | 404 ProblemDetail for a bad cursor | low | mitigate | Reuse the fixed UNREADABLE_CURSOR text. The existing unreadableCursorIsNotFound loop already asserts the message never contains the input, and it now covers the out-of-range inputs too. |
| T-hhz-03 | Denial of Service | Priority pagination for a legitimately stored out-of-range date | low | accept | If a malformed feed ever stored a published_at outside years 1..9999 (JSON Feed Instant.parse accepts +10000-..., ROME dates are lenient), a page ending exactly on that row would emit a cursor that now decodes as unreadable, so the client would restart from page 1 at that boundary. This is rare and only affects pagination, and it is the bound the review recommended. Clamping was rejected because it would change the compared tuple and could skip rows. Left for a follow-up if it is ever observed. |
</threat_model>

<verification>
Priority cursor regression (Docker required):

cd /Users/scottb/orca/workspaces/myfeeder/main && ./gradlew test --tests "org.bartram.myfeeder.controller.*" --tests "org.bartram.myfeeder.service.PriorityServiceTest" --tests "org.bartram.myfeeder.repository.InterestScoreQueriesTest"

This must pass. The rankedWalkCrossesIntoUnscoredWithNoDuplicates and cursorScoreDropMidWalkSkipsNoRow walks prove that every cursor the server actually serves still decodes under the new bound.
</verification>

<success_criteria>
- WR-04 closed: an out-of-range cursor date returns the documented fixed-text 404 (never a 500) and triggers the R4 restart on the client like any other unreadable cursor.
- decodeCursor accepts exactly the inclusive range 0001-01-01T00:00:00Z..9999-12-31T23:59:59.999999Z.
- TDD order visible in git: a test commit that fails, then the fix commit.
- No change to the wire format, encodeCursor, PaginatedResponse, GlobalExceptionHandler or any other endpoint.
</success_criteria>

<output>
Create `.planning/quick/260926-hhz-fix-wr-04-bound-the-decoded-cursor-date/260926-hhz-SUMMARY.md` when done, with `status: complete` in its frontmatter. Record the RED evidence: the failing assertions, plus the integration test's "timestamp out of range" reproduction.
</output>
