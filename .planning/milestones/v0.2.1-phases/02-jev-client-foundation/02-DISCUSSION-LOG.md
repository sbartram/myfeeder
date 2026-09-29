# Phase 2: Jev Client Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md. This log keeps the alternatives that were considered.

**Date:** 2026-09-22
**Phase:** 02-jev-client-foundation
**Areas discussed:** Client API shape, Failure semantics
**Areas offered but not selected:** Live smoke proof, Deploy & secret handling (research defaults apply)

---

## Client API shape

### Method shape

| Option | Description | Selected |
|--------|-------------|----------|
| Thin pass-through | `judge(Map state, Map<String, Question>)`, generic; the scorer and preview build the questions | ✓ |
| Domain method now | `judgeArticle(ArticleInput, profile, topics)`; couples Phase 2 to types from Phase 3 | |
| You decide | | |

### Return type

| Option | Description | Selected |
|--------|-------------|----------|
| App-owned record | `JevJudgment(model, requestId, nouls, scores, usage)`; SDK types stop at the boundary | ✓ |
| SDK SystemOneResponse | Less code, but leaks the SDK into scoring code | |
| You decide | | |

### HTTP transport

| Option | Description | Selected |
|--------|-------------|----------|
| Reactor Netty, Jev-specific timeouts | Cloned builder with a 5s connect / 5s read timeout; one stack app-wide | ✓ |
| JDK factory (research Pattern A) | Verified code, but adds a second HTTP stack | |
| Inherit global 5s/30s | Simplest, but slow calls hold workers | |

### Input question types

| Option | Description | Selected |
|--------|-------------|----------|
| SDK Question types in | Callers use the Noul/Score builders | ✓ |
| App-owned question records | Mapped inside the client; more code | |

---

## Failure semantics

### Rejected key (401/403)

| Option | Description | Selected |
|--------|-------------|----------|
| Permanent, opens breaker | Not retried, counts toward the breaker, so scoring pauses on its own | ✓ |
| Treat as not-configured | Breaker-ignored; each article still makes a call on every sweep | |
| You decide | | |

### 429 backoff

| Option | Description | Selected |
|--------|-------------|----------|
| Honor Retry-After | Custom interval function, capped at about 10s; otherwise exponential | ✓ |
| Fixed exponential only | 1s then 2s; revisit if 429 storms appear | |

### Fallback

| Option | Description | Selected |
|--------|-------------|----------|
| Rethrow typed | Original TypeSafe, JevNotConfigured or CallNotPermitted exception propagates | ✓ |
| Map to app exceptions | JevTransient/JevPermanent hierarchy at the boundary | |
| No fallback method | Omit fallbackMethod | |

### Breaker config

| Option | Description | Selected |
|--------|-------------|----------|
| Research values | Window 20, min 10, 50%, 60s open, slow-call 3s | ✓ |
| Mirror Raindrop | 10 / 5 / 30s | |
| You decide | | |

### Keyless startup log

| Option | Description | Selected |
|--------|-------------|----------|
| One INFO line | "Jev not configured; interest scoring disabled" | ✓ |
| WARN line | More visible in the deploy log tail | |
| Silent | Matches Raindrop | |

---

## Claude's Discretion

- How the live smoke test is gated (research default: `@EnabledIfEnvironmentVariable`)
- Deploy scope for this phase and Helm `lookup` preservation (default: match Raindrop, verify with `helm template`)
- Class and package names; whether an identity fallback is omitted

## Deferred Ideas

- Phase 4: treat 401/403 and an open circuit as transient (no attempt consumed)
- Phase 4 or 7: preserve the key across `helm upgrade` runs without the variable (`lookup`)
- Phase 7: re-tune the breaker, retry and 429 cap
