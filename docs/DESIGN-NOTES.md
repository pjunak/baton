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
- Starting playback with no active output moves to Console and opens the output picker rather than
  silently advancing an inaudible server clock.
- The output picker defaults to one active speaker, offers multi-output as an explicit switch, and
  exposes the sole active speaker's volume directly in the Console header.
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
- Library's compact breadcrumb/Up/Refresh bar stays above the scrolling list. System Back returns
  through folder and search history, previews the destination during a predictive gesture, and
  leaves the current view intact when cancelled. The keyboard and action sheet dismiss first.
- Folder/search scroll positions survive return navigation, tab changes, and saved-state
  recreation. Refresh retains visible content and location, with an explicit Retry on errors;
  superseded loads cannot replace the newest view.
- Tap a track to play; long press or overflow opens Add to queue / Play as interrupt, with Open
  containing folder on search results. Play now is deliberately absent from that menu. A leftward
  row swipe requests enqueue once and returns the row to place; buttons and accessible actions
  provide the same operation. Playback/enqueue disable offline.
- A mini-player outside Console shows the current track; tap or swipe up to open Console. The
  existing dock remains the global play/pause control.
- The output picker has a close button and an upward-dismiss handle, separated from volume
  gestures. Settings subtabs support horizontal swipes.
- Queue dragging previews the drop slot and scrolls at the viewport edges. A changed queue or
  lost connection cancels the gesture; only a completed, still-current move is sent.
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

- Run `./gradlew :app:connectedDebugAndroidTest` (or `gradlew.bat` on Windows) on a connected test
  device for Library, queue, and mini-player gestures. These tests use fake data without a server
  login. The JVM suite also covers navigation persistence, loading races, action gating, and queue
  geometry. CI still needs an emulator runner before it can include the device suite.
- Extend device coverage to Settings paging, output-sheet dismissal, and responsive pane layouts.
  Emulator automation does not replace physical one-handed, TalkBack, or Switch Access acceptance.

Android lint now runs in CI alongside compilation and unit tests. Detekt and a separate formatter
are intentionally not added: at this project size they would mostly duplicate compiler/lint checks
and add another Kotlin-tooling compatibility surface. Revisit if modules or contributors multiply.

## Deferred product questions

- Private-CA pairing, QR setup, multiple servers, offline caching, and crash reporting remain
  phase-two capabilities; see [ARCHITECTURE.md](ARCHITECTURE.md#deferred-work).
