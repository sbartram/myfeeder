---
status: testing
phase: 06-thumbs-feedback
source: [06-VERIFICATION.md]
started: 2026-09-27T04:40:00Z
updated: 2026-09-27T04:40:00Z
---

## Current Test

number: 1
name: Pressed-state styling and toolbar wrap
expected: |
  With ./gradlew bootTestRun and npm run dev, open an article and press 👍 Up, then narrow the reading pane.
  👍 Up turns the theme accent color and weight 600 while 👎 Down stays plain; the toolbar wraps to a second row instead of clipping.
awaiting: user response

## Tests

### 1. Pressed-state styling and toolbar wrap
expected: 👍 Up turns the theme accent color and weight 600 while 👎 Down stays plain; the toolbar wraps to a second row instead of clipping
result: [pending]

### 2. Narrow picker walkthrough (scored article with 3+ matched topics: d, Shift+D, 2, Enter; reopen, Esc; narrow the pane)
expected: Picker opens under 👎 Down with the first checkbox focused; label reads '{name} only' and the toast starts '👎 Narrowed'; Esc closes without clearing the selection; long names truncate at 12em with an ellipsis and the toolbar wraps
result: [pending]

### 3. Picker with a 40-character topic name at the 240px minimum width
expected: The name wraps inside its 1fr grid column and never overlaps the right-aligned '{n}% match'
result: [pending]

### 4. Live vote-feedback loop with scored articles
expected: (1) u on a multi-topic article toasts like '👍 Rust +1.8', badge updates, Why row reads 'Rust  90% × +21.8 (+20 +1.8 learned)', a second u toasts 'Vote removed · Rust −1.8'; (2) on /priority only the voted row's badge changes, nothing moves, '↻ Ranking changed — refresh' lights; (3) voting on a scored no-match article toasts 'No topics matched', the pane line appears, Create topic from article opens Interests with an empty-name draft described by the title at ±20; (4) Interests shows 'Learned from votes … · Effective weight …' per topic
result: [pending]

### 5. Effect toast and no-match strip wrap
expected: A toast listing three 40-character topic names with limit notes wraps inside its 360px max-width without clipping; the 'No topics matched — Create topic from article' strip wraps with no horizontal scroll in the narrowest reading pane
result: [pending]

### 6. Why panel with a long topic name and a learned part
expected: The '(+20 +1.8 learned)' label wraps inside the 1fr label column and never overlaps the points column
result: [pending]

### 7. Concurrent votes on one article (two terminals: curl PUT {vote:1} and PUT {vote:-1} on the same id in a tight loop, then GET)
expected: Exactly one article_feedback row, holding the vote of whichever request committed last; no duplicate-key error
result: [pending]

## Summary

total: 7
passed: 0
issues: 0
pending: 7
skipped: 0
blocked: 0

## Gaps
