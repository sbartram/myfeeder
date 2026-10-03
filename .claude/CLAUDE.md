<!-- GSD:project-start source:PROJECT.md -->

## Project

**myfeeder** — no active milestone (v0.3.0 Engagement Learning shipped 2026-10-02 as releases v0.3.0/v0.3.1 and archived in `.planning/milestones/`, after v0.2.1 Interest Ranking; start the next with `/gsd-new-milestone`). Project context and roadmap live in `.planning/` (`PROJECT.md`, `ROADMAP.md`, `STATE.md`, `MILESTONES.md`); research in `.planning/research/`; codebase map in `.planning/codebase/`. Stack, conventions and architecture are in the root `CLAUDE.md`.

**Core Value:** Unread articles I care about most appear at the top of a Priority view, ranked by a score that reflects my stated interests and my thumbs up/down feedback, without ever breaking or slowing feed polling.

<!-- GSD:project-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.

When a plan fixes an item tracked elsewhere (`deferred-items.md`, a `.planning/debug/` session, a todo, a UAT gap), mark that artifact resolved in the same commit, or `/gsd-complete-milestone`'s audit reports it as still open.
<!-- GSD:workflow-end -->
