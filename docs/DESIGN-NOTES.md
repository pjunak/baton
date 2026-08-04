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
- Queue rows support exact tap-to-jump, drag reordering, accessible move actions, removal, and
  clearing without confusing duplicate track IDs.
- Library navigation and folder-level actions occupy a distinct control shelf above grouped
  folder and track content.
- The four Console end-of-queue states use explicit accessibility copy: off, continue into the
  library, repeat the whole queue, and repeat the current track.
- Shared section headers, track rows, and an 8/16/24 layout-spacing scale keep the compact screens
  visually consistent without conflating spacing with component dimensions.
- Setup reuses the adaptive Baton mark and wordmark; the launcher also provides round and
  monochrome layers for themed icons.
- Landscape phones and large windows split Console into now-playing and queue/control panes;
  portrait phones retain the focused stacked layout.

## Remaining validation

### Physical accessibility pass

- Run a manual TalkBack and Switch Access pass on a physical phone. Verify traversal order,
  adjustable seek/volume actions, disabled-state announcements and contrast, and the output-sheet
  pane transition. The custom controls, output switches, navigation tabs, artwork descriptions,
  long-press actions, 48dp interaction regions, and user-visible string resources are implemented.

### Device-backed UI coverage

- Add device-backed Compose UI tests when CI has an emulator or managed-device runner. Unit tests
  cover the responsive breakpoint policy, but gestures, pane layout, and TalkBack still need a
  real Compose host.

Android lint now runs in CI alongside compilation and unit tests. Detekt and a separate formatter
are intentionally not added: at this project size they would mostly duplicate compiler/lint checks
and add another Kotlin-tooling compatibility surface. Revisit if modules or contributors multiply.

## Deferred product questions

- Private-CA pairing, QR setup, multiple servers, offline caching, and crash reporting remain
  phase-two capabilities; see [ARCHITECTURE.md](ARCHITECTURE.md#deferred-work).
