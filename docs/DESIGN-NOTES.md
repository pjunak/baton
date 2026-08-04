# Design notes and UI backlog

Baton is a single-operator remote used one-handed in dim tabletop sessions. Glanceability,
connection honesty, and reliable controls matter more than decorative density.

## Design principles

1. **Glanceable first.** The Console should reveal what is playing and whether the room is active
   without close reading.
2. **Fail loud, succeed quiet.** Healthy connectivity stays unobtrusive; reconnecting or failed
   state is prominent and disables mutations that would silently no-op.
3. **Consume, do not author.** Destructive and authoring workflows stay in the web app; see
   [ADR-0006](DECISIONS.md#adr-0006--consume-dont-author-web-app-fallback).
4. **Accent conveys state.** Dynamic Material color is welcome, but warning/error colors and
   active-mode tint are semantic rather than decorative.
5. **Background audio is a first-class route.** The same speaker experience must work for the
   phone speaker, wired output, Bluetooth headphones, and Bluetooth speakers.

## Implemented baseline

- Large Console artwork plus Library and queue thumbnails.
- A dominant docked play/pause control with secondary transport controls.
- Opt-in Keep Console awake behavior.
- Offline-disabled mutations and a prominent reconnect/disconnect banner.
- Lifecycle-aware Console ticker and controller socket lifetime.
- Screen-off foreground playback while the local speaker role is enabled.
- Honest loading, empty, error, and retry states for Library data.
- State-driven shuffle/repeat treatments and a smooth dead-reckoned seek display.

## Prioritized backlog

### P1 — accessibility and input semantics

- Run a manual TalkBack and Switch Access pass on a physical phone. Verify traversal order,
  adjustable seek/volume actions, disabled-state announcements and contrast, and the output-sheet
  pane transition. The custom controls, output switches, navigation tabs, artwork descriptions,
  long-press actions, 48dp interaction regions, and user-visible string resources are implemented.

### P2 — queue and library clarity

- Add queue tap-to-jump and drag-to-reorder. Rows currently support removal and clearing only.
- Visually separate Library actions (Up, Play this folder) from folders and tracks.
- Revisit the four-state repeat cycle in user-visible copy. Current icons and accessibility labels
  distinguish off, continue, repeat all, and repeat one, but the behavior remains dense.

### P3 — consistency and responsive layout

- Extract repeated section headers/list-row conventions and centralize the 8/16/24 spacing scale.
- Add the Baton name/mark to setup and finish the adaptive launcher/wordmark treatment.
- Design and test a landscape/large-screen Console, likely placing artwork and controls side by
  side instead of stretching the portrait list.

### P4 — test and static-analysis coverage

- Restore Compose UI tests once the AGP task/tooling path is reliable.
- Evaluate detekt and formatting enforcement without duplicating compiler or Android lint checks.

## Deferred product questions

- Private-CA pairing, QR setup, multiple servers, offline caching, and crash reporting remain
  phase-two capabilities; see [ARCHITECTURE.md](ARCHITECTURE.md#deferred-work).
