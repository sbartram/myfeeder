# Handoff — design-review fixes execution paused

**Date:** 2026-07-04
**Branch:** `design-review-fixes` (branched from `main` at `000cea9`)
**Latest commit:** `a8c6168` — "refactor: handle OpmlParseException in GlobalExceptionHandler"
**Stopped at:** User said "stop execution now" immediately after Task 3 of 10 passed both reviews. Tasks 1–3 complete and committed; Tasks 4–10 not started.

## What this work is

A design review of the backend against *A Philosophy of Software Design* (Ousterhout) and *Effective Java* (Bloch) — using the user's wiki notes at `~/Dropbox/kb/tech/wiki/a-philosophy-of-software-design.md` and `~/Dropbox/kb/tech/wiki/effective-java.md` — produced 10 fix tasks. The full plan with complete code for every task is the durable artifact:

- **Plan:** `docs/superpowers/plans/2026-07-04-design-review-fixes.md` (committed in `96064df`)
- **Task state:** `docs/superpowers/plans/2026-07-04-design-review-fixes.md.tasks.json` (statuses 1–3 = completed; the file has uncommitted working-tree edits — commit or keep updating it as tasks finish)

## Execution method (user's explicit instructions)

- **Subagent-driven development** (superpowers skill), one implementer + spec reviewer + code-quality reviewer per task.
- **Model constraint from user:** implementation subagents ONLY on `sonnet` (metadata `modelTier: mechanical` → tasks 4, 8) or `opus` (`modelTier: standard` → tasks 5, 6, 7, 9, 10); reviewers on opus. **Never fable.**
- Execution was serialized deliberately: all tasks share one working tree and commit with `git add -A`, so parallel implementers would sweep each other's changes.
- Native task list (TaskList) mirrors the plan; task descriptions carry `json:metadata` fences with files/verifyCommand/acceptanceCriteria.

## State of tasks

| # | Task | Status | Commits |
|---|------|--------|---------|
| 1 | Remove dead code | done, both reviews passed | `ec68cfd`, `6114546` |
| 2 | NotFoundException → 404 | done, both reviews passed | `96064df` |
| 3 | Centralize OPML error handling | done, both reviews passed | `a8c6168` |
| 4 | Parser types → records | **next up** (sonnet) | — |
| 5 | Extract FeedFetcher (charset fix) | blocked by 4 (opus) | — |
| 6 | Backoff actually engages | blocked by 5 (opus) | — |
| 7 | After-commit feed events | blocked by 1✓, 6 (opus) | — |
| 8 | Typed request DTOs | blocked by 2✓, 7 (sonnet) | — |
| 9 | Raindrop resilience → client bean | unblocked, independent (opus) | — |
| 10 | Pagination factory + items rename | unblocked, independent (opus) | — |

## Facts that live only in this conversation

- **LSP diagnostics are noise:** the IDE/serena language server reports hundreds of "method setX undefined" / "blank final field" errors on Lombok-generated code. They are false positives — `./gradlew test` compiles and passes. Do not "fix" them.
- **Reviewer follow-ups already applied:** Task 1's implementer initially deleted two markRead tests; they were re-added against the surviving 3-arg signature in `6114546`. Task 3's quality reviewer left two optional-polish notes (assert the ServletException cause is "boom" in `shouldPropagateUnexpectedErrorsAsServerErrors`; IOException now maps to 500 — deemed correct). No action required; noted in case the final whole-branch review re-raises them.
- **Plan-doc hygiene:** Task 2's commit swept the plan docs into `96064df` via `git add -A` (fine — intended to be committed). Task 3's implementer deliberately left the then-dirty `.tasks.json` unstaged; expect it dirty between tasks.
- **Review verdicts were unanimous approvals**; no open findings anywhere.
- **Explicitly excluded from scope** (per the design review's own recommendations, user accepted): no entity→response-DTO layer; no markRead endpoint split.
- Behavior changes shipped so far: missing path entities now 404 (was 400/409); OPML parse errors return 400 + ProblemDetail detail message; unexpected import errors now 500 (was swallowed 400).

## How to resume

1. Read the plan doc, then `TaskList` / `.tasks.json` for current statuses.
2. Continue the loop at **Task 4** (sonnet implementer): full task text is in the plan doc — paste it into the implementer prompt; never make subagents read the plan file themselves.
3. Per task: implementer → spec reviewer (opus) → quality reviewer (opus) → mark completed in both TaskUpdate and `.tasks.json`.
4. After Task 10: final whole-branch review (opus), then the finishing-a-development-branch skill. Merge to main with `--no-ff` only when the user asks; never push unless asked.
5. Verify commands: backend `./gradlew test` (Docker must be running); frontend (Task 10 only) `cd src/main/frontend && npx tsc -b && npm test` (plain `tsc --noEmit` false-passes).

## Suggested skills

- `superpowers-extended-cc:subagent-driven-development` — resume the execution loop
- `superpowers-extended-cc:finishing-a-development-branch` — after all 10 tasks
- `superpowers-extended-cc:requesting-code-review` — final whole-branch review template
