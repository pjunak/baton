# Design Notes & UI Backlog

A living record of UI/UX decisions and a prioritized backlog from design critique. Baton is a
**single-operator remote** used at tabletop RPG sessions — often a **dim room**, **glanceable**,
**one-handed**. That context drives the priorities below more than generic "make it pretty."

Status legend: ⏳ planned · 🚧 in progress · ✅ done.

---

## Design principles

1. **Glanceable first.** The Console must answer "is it playing? what's playing?" from across a
   table without focus. Big focal play/pause, cover art, large type.
2. **Fail loud, succeed quiet.** A *good* connection is unremarkable; a *bad* one must be obvious
   and must disable controls that would silently no-op.
3. **Consume, don't author** (see [ADR-0006](DECISIONS.md)). Destructive/authoring paths stay in the
   web app.
4. **Material 3 + dynamic color**, light/dark. Reserve accent color for meaning (state), not chrome.

---

## Backlog (prioritized)

### P1 — glanceability core
- ⏳ **Cover art on the Console now-playing** (large), and as thumbnails on Library/queue rows.
  Backend serves `GET /api/library/tracks/{id}/cover`; load via Coil on the shared OkHttp client.
- ⏳ **Dominant play/pause.** Filled primary button, ~72dp — the one control findable without
  looking. Demote skip/shuffle/repeat to secondary weight.
- ⏳ **Keep screen awake on Console** (`FLAG_KEEP_SCREEN_ON` via a `DisposableEffect`), ideally a
  Settings toggle. The remote shouldn't sleep mid-encounter.

### P2 — connection honesty
- ⏳ **Disable transport unless `Connected`.** Dim controls during `Connecting`/`Disconnected` so
  taps don't silently no-op.
- ⏳ **Elevate the bad status.** De-emphasize "● Connected" to `onSurfaceVariant`; make
  disconnected/reconnecting prominent (color + label), since that's the only status worth noticing.

### P3 — consistency & polish
- ⏳ **Shared UI components + spacing scale.** Extract `SectionHeader`, a common list row, and an
  8/16/24 spacing scale; today each screen re-declares helpers (pairs with the `TrackRepository`
  DRY cleanup from code review).
- ⏳ **Repeat mode clarity.** The off→follow→queue→track cycle exposes backend jargon ("Follow").
  Keep the caption; consider labeling the icon or trimming to off/all/one with "Follow" behind a
  long-press.
- ⏳ **Queue interactions.** Tap-to-jump and drag-to-reorder (reorder is a stated v1 goal); today
  rows only support remove (✕).
- ⏳ **Library hierarchy.** Visually separate *actions* (Up, "Play this folder") from *content*
  (folders, tracks) — a subtle header or grouping.

### P4 — onboarding & accessibility
- ⏳ **Setup wizard warmth.** App name + one-line "Connect to your music server" to orient; it's
  currently a bare centered form.
- ⏳ **Accessibility.** Use `onSurface`/`onSurfaceVariant` for text (reserve `primary` for the
  status dot); add slider semantics labels; alt text on cover art when added. AMOLED-friendly dark.

---

## What works (keep)

- Honest empty/loading/error states in Library.
- Clean sectioned Settings.
- State-driven UI with the active-mode tint on shuffle/repeat.
- Dead-reckoned seek bar (smooth without flooding the socket).

---

## Open questions

- **Branding:** no logo/wordmark yet. Worth a minimal mark + adaptive launcher icon before the first
  on-phone test build.
- **Landscape / large screens:** untested; the Console list should adapt (cover + controls side by
  side) eventually.
