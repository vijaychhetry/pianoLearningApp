# Opus 5 plan review — keyboard guide and export

Reviewer: Claude Opus 5 (`claude-opus-5-thinking-xhigh`), 2026-09-19.
Range reviewed: `1ba45de..1d5320b` plus the live Kotlin the plan would touch.

**Verdict: Yes with fixes.** The diagnosis and API research were accepted. Four critical plan bugs were corrected in the same documents before any app code.

| ID | Finding | Plan change |
| --- | --- | --- |
| C1 | Dropdown offered C2–C7; detector only hears A2–F6 | `selectableMidi()` is 27 whites in 45..89; C2 used only on the mini-map |
| C2 | Jump-after-complete blocked by runner/VM/screen | Dropped. Dropdown disables until Redo |
| C3 | `jumpTo` did not reset the press; held note could sample the new key | Runner resets debouncer + `onRelease`; finished keys are cleared |
| C4 | 36 × 9 dp keys made the target unreadable | Mini-map + C3–C5 zoom; name above the key |

Important follow-ups also written into the plan: note-name copy, `useProfile(profile, notes)`, append-only logs, geometry unit tests, replace `AC-LEARN-06`.
