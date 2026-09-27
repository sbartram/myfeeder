# Phase 06 — UI Review

**Audited:** 2026-09-27
**Baseline:** 06-UI-SPEC.md (approved 2026-09-26)
**Screenshots:** not captured (code-only audit as instructed; human UAT 06-UAT.md passed 7/7 visual checks)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Every contract string matches; unsaved-edit learned line adds a cap suffix the contract omits, and the picker keeps generic "Cancel" |
| 2. Visuals | 3/4 | Structure matches the contract; the popover anchors `left: 0` to the group with a viewport-based max-width, so it can overflow a narrow reading pane on the right |
| 3. Color | 4/4 | All new rules use theme variables; accent limited to the 3 declared elements |
| 4. Typography | 4/4 | Only 12/13px and 400/600 in new rules; tabular numerals where declared |
| 5. Spacing | 4/4 | Only 4/8/16px tokens in new rules; declared exceptions honored |
| 6. Experience Design | 3/4 | Error, empty and focus states are complete; `aria-controls` points at an absent id while closed, and error toasts are announced politely through `role="status"` |

**Overall: 21/24**

---

## Top 3 Priority Fixes

1. **WARNING: popover overflow in a narrow pane.** `.narrow-picker` uses `left: 0; max-width: min(320px, calc(100vw - 32px))` (App.css:412-423). The clamp is based on the viewport width, not the pane width. When the toolbar wraps and `.feedback-group` lands near the pane's right edge, the 240px-minimum box can run past the reading pane or be clipped by it. UAT tested only the default toolbar position. **Fix:** add a `right: 0` fallback (flip alignment when `getBoundingClientRect().right` exceeds the pane width), or clamp against the pane with a container query.
2. **WARNING: error toasts announced politely.** Toast.tsx:37 puts `role="status"` on the whole `.toast-container`, as the contract says. That also makes vote-failure toasts ("Couldn't save your vote…") polite, even though they report a reverted state. **Fix:** put `role="alert"` on individual `toast-error` items (the container keeps `role="status"`).
3. **WARNING: stale `aria-controls`.** FeedbackBar.tsx:88 sets `aria-controls="narrow-picker"` at all times, but `#narrow-picker` only exists while the picker is open, so the reference dangles in the default state. **Fix:** `aria-controls={narrowOpen ? 'narrow-picker' : undefined}`.

Minor recommendations:

4. TopicRow.tsx:236-237 prints `{capSuffix}` in the "updates when you save" variant. The contract (Layout, Topic editor, unsaved base edit) specifies `Learned from votes {learned} · Effective weight updates when you save` with no suffix. The suffix is harmless and arguably informative, but it isn't in the contract. Either drop it or amend the spec.
5. NarrowPicker.tsx:138 keeps the plain "Cancel" label that the checker flagged as non-blocking. "Keep current topics" would be more specific.
6. `.vote-btn[aria-pressed='true']` (App.css:401) comes after `.toolbar-btn:hover` (App.css:227), so a pressed button has no hover change. The only hover affordance is the cursor. Consider `.vote-btn[aria-pressed='true']:hover { text-decoration: underline; }` or a subtle opacity shift.
7. `.reading-toolbar` has no `align-items: center`, and `.feedback-group` does. Mixed-height children (a truncated `inline-block` name inside the narrow toggle) may sit a pixel off the other buttons' baseline. This was not observable without screenshots.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)
- Vote labels, titles, and aria-labels match exactly (FeedbackBar.tsx:64-81).
- Narrow labels `Narrow…`, `{name} only`, `{a}, {b} only`, `{k} of {n} topics` and `No topics` match (utils/feedback.ts:40-46), using U+2026.
- Picker heading, zero-checked hint, empty list and the singular/plural Apply label match (NarrowPicker.tsx:106-145).
- Toast leads, the `formatDelta` sign handling, limit notes, `+N more`, unscored/no-match/under-0.1 variants all match the contract table (utils/feedback.ts:54-119).
- The three error strings match (hooks/useFeedback.ts:31-35), and `inlineError` meta is set (line 49).
- The shortcut overlay entries match (ShortcutOverlay.tsx:13-14).
- Deviations: the cap suffix in the unsaved-edit learned line (TopicRow.tsx:237), and the generic "Cancel".

### Pillar 2: Visuals (3/4)
- The group follows `★ Star` in the toolbar (ReadingPane.tsx:159). The notice is placed between the toolbar and the content (ReadingPane.tsx:187).
- Focal points match the contract: the active vote is the only accent in the toolbar, Apply is the only filled shape, and Create is the only accent in the strip.
- Negative names carry a trailing U+2212, so sign is not conveyed by color alone (NarrowPicker.tsx:126).
- Deduction: the popover alignment risk (fix 1) and missing hover feedback on the pressed state (item 6).

### Pillar 3: Color (4/4)
- New rules at App.css:393-482 and 773-780 use only `var(--…)`. The only literal is the declared `rgba(0,0,0,0.3)` shadow, which is the existing menu value.
- Accent appears three times in new code (`.vote-btn[aria-pressed]`, `.btn-primary` Apply, `.feedback-create`), exactly the contract list.
- The narrowed label is `--text-secondary` at weight 400, not accent. The toast is success-colored for both votes.
- No `--bg-hover` misuse; hover uses `--hover-bg`.

### Pillar 4: Typography (4/4)
- New rules use 12px (title, key, match, hint, learned line) and 13px (option, empty, notice). The toolbar buttons inherit 12px.
- Weights are 600 for the active vote and picker title, and 400 for the narrowed label.
- `tabular-nums` appears on `.narrow-key`, `.narrow-match` and `.interests-learned`.
- Why rows stay em-based inside `.reading-content`.

### Pillar 5: Spacing (4/4)
- `.feedback-group` gap is 8. The picker insets are `8px 16px 4px` and `4px 16px`, and the footer is `8px 16px` with gap 8. The popover `margin-top` is 4.
- The notice uses `8px 16px`, the toolbar `row-gap` is 4, and the learned line margin is 4.
- The 240/320/12em measure limits are declared exceptions. No off-scale values.

### Pillar 6: Experience Design (3/4)
- Vote buttons are never disabled, and presses are serialized per the contract.
- The picker handles all of these: focus on the first checkbox when it opens, 1-9/Enter/Esc isolated with `stopPropagation`, Enter on buttons keeps native activation, outside mousedown scoped to `.feedback-group`, closing on article change or when the vote is no longer narrowable, and focus returning to the toggle or `.reading-content` (FeedbackBar.tsx:47-60, NarrowPicker.tsx:37-92).
- Apply is disabled at 0 checked. An unchanged set closes without a request, and all topics checked un-narrows.
- The learned-load error note is present (InterestsDialog.tsx:532-534). The learned line is absent while loading and on drafts (TopicRow.tsx:448).
- Deductions: the stale `aria-controls`, polite announcement of error toasts, and the pane-overflow risk.

Registry audit: shadcn not initialized, no third-party registries. Skipped.

---

## Files Audited
- src/main/frontend/src/components/FeedbackBar.tsx
- src/main/frontend/src/components/NarrowPicker.tsx
- src/main/frontend/src/components/FeedbackNotice.tsx
- src/main/frontend/src/components/TopicRow.tsx (LearnedLine)
- src/main/frontend/src/components/WhyBreakdown.tsx
- src/main/frontend/src/components/InterestsDialog.tsx (learned error)
- src/main/frontend/src/components/Toast.tsx
- src/main/frontend/src/components/ShortcutOverlay.tsx
- src/main/frontend/src/components/ReadingPane.tsx (placement)
- src/main/frontend/src/hooks/useFeedback.ts (error copy)
- src/main/frontend/src/utils/feedback.ts
- src/main/frontend/src/App.css (lines 219-227, 384-482, 729, 773-780)
