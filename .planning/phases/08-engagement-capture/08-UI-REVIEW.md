# Phase 08 — UI Review

**Audited:** 2026-09-30
**Baseline:** abstract 6-pillar standards (no UI-SPEC.md); 08-CONTEXT.md decisions D-01..D-04, D-10..D-12; existing theme tokens (App.css / themes.ts)
**Screenshots:** not captured (no dev server on :3000, :5173 or :8080), so this is a code-only audit
**Interaction captures:** off (workflow.ui_interaction_capture is false)

Scope: the `Engaged: … · Forget` line in `ScoreRow.tsx`, its CSS in `App.css:504-528`, and the Open Original entry points routed through `useOpenOriginal` (`ReadingPane.tsx:117,185,217`, `useKeyboardShortcuts.ts:175`).

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Clear and concise; mixed grammatical forms ("on a board" vs verbs), and the bare "Forget" does not say what it forgets |
| 2. Visuals | 3/4 | Forget reuses the `why-toggle` class, so a destructive action looks identical to a disclosure toggle |
| 3. Color | 3/4 | Token-only (no hardcoded colors); `--text-muted` on the label and separators may be low-contrast on light themes |
| 4. Typography | 4/4 | Inherits the row's size (`font-size: inherit`) and adds no new sizes or weights |
| 5. Spacing | 3/4 | Separators are literal `" · "` text in spans instead of CSS gap, which is inconsistent when the row wraps |
| 6. Experience Design | 3/4 | An immediate delete with no undo (by design, D-03), no pending text, and the Open Original button stays enabled with no feedback when there is no URL |

**Overall: 19/24**

---

## Top 3 Priority Fixes

1. **WARNING: Forget looks like "Why N?"** (`ScoreRow.tsx:72`, `className="toolbar-btn why-toggle"`). The user impact: an irreversible delete with no undo shares its styling with a harmless disclosure toggle sitting in the same row, so a misclick is easy. The fix: give it its own class, e.g. `engagement-forget`, with the same link style plus a hover color of `var(--danger)` (or the theme's negative token, as used for `.weight-negative`) and an underline on hover.
2. **WARNING: there is no pending feedback on Forget** (`ScoreRow.tsx:74`). The button is disabled but its text stays "Forget". Elsewhere the app changes the label while a request runs (`ReadingPane.tsx:183` shows "🗑 Removing…"). The fix: `{forget.isPending ? 'Forgetting…' : 'Forget'}`.
3. **WARNING: Open Original does nothing when there is no URL** (`useEngagement.ts:20`, where an empty `url` returns early). Both buttons (`ReadingPane.tsx:185,217`) stay enabled, so a click does nothing and gives no feedback. The fix: `disabled={!article.url?.trim()}` on both buttons, plus a `title="No original link"`.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)
- WARNING: the labels in `ScoreRow.tsx:8-13` mix forms: "opened", "starred", "saved to Raindrop" are verbs and "on a board" is a prepositional phrase. So "Engaged: opened, on a board" reads unevenly. Consider "boarded" (08-CONTEXT lists it as an option) or "added to a board".
- WARNING: the visible text "Forget" is ambiguous. The aria-label "Forget engagement" (`ScoreRow.tsx:73`) is good for accessibility and satisfies label-in-name, but sighted users get no tooltip. Add `title="Forget engagement (removes it from ranking signals)"`.
- Pass: the "Engaged:" prefix clearly scopes the list, and D-02 wording is followed exactly.

### Pillar 2: Visuals (3/4)
- WARNING: Forget and "Why N?" share `toolbar-btn why-toggle` (`ScoreRow.tsx:60,72`), so a disclosure action and a destructive action have identical affordances. See Fix 1.
- Pass: an unscored but engaged article renders the line alone. The leading separator is correctly gated on `scored` (`ScoreRow.tsx:67`), so the row never starts with a stray "·".
- Pass: the ↗ Open Original button is unchanged in appearance. Routing it through the hook is invisible to the user, as intended.

### Pillar 3: Color (3/4)
- Pass: only theme tokens are used (`App.css:522-528`: `--text-muted`, `--text-secondary`, `--text-primary`) and the phase adds no hardcoded colors.
- WARNING: `.engagement-label` and `.chip-sep` use `--text-muted`. On the three light themes, muted text at the row's reduced size is at risk of falling below WCAG AA 4.5:1. This needs verifying with a contrast check per theme in `themes.ts`. If any theme fails, use `--text-secondary` for the label.
- No hover or danger color distinguishes Forget (see Pillar 2).

### Pillar 4: Typography (4/4)
- Pass: `.why-toggle { font-size: inherit }` keeps Forget at the row size, and the phase introduces no new font sizes or weights. The label inherits from `.score-row`.

### Pillar 5: Spacing (3/4)
- WARNING: separators are text nodes (`<span className="chip-sep"> · </span>`, `ScoreRow.tsx:56,67,70`), not a flex `gap`. When the row wraps in a narrow reading pane, a "·" can end up at the start or end of a line, and the spacing depends on whitespace collapsing. This matches the existing chip pattern, so it is a minor issue. A fix would be `gap` on `.score-row` with `::before` separators.
- Pass: the phase adds no arbitrary px values.

### Pillar 6: Experience Design (3/4)
- By design (D-03, user decision): Forget deletes immediately with no confirm and no undo. This is acceptable because the data is low-stakes and a later open or save records engagement again. It is still the main reason Fix 1 (a distinct affordance) matters.
- WARNING: there is no pending label (Fix 2). Errors fall through to the global MutationCache toast, which is good.
- WARNING: when there is no URL, the button is a silent no-op (Fix 3).
- Pass: `window.open` runs synchronously before the fire-and-forget PUT (`useEngagement.ts:22-27`), so popup blockers do not trigger. Errors are swallowed as D-12 specifies. Only the by-id query is invalidated (D-06), so the Engaged line appears after an open without refetching the list.
- Pass: all three entry points share one helper (D-10). In-body links and Copy Link are untouched (CAPT-07).
- Note: after an Open Original, the Engaged line updates only once the PUT and refetch finish. Nothing shows it is pending, which is acceptable for a passive signal.

---

## Files Audited
- src/main/frontend/src/components/ScoreRow.tsx
- src/main/frontend/src/hooks/useEngagement.ts
- src/main/frontend/src/components/ReadingPane.tsx (lines 105-120, 180-217)
- src/main/frontend/src/hooks/useKeyboardShortcuts.ts (line 175)
- src/main/frontend/src/App.css (lines 504-536)
- .planning/phases/08-engagement-capture/08-CONTEXT.md
