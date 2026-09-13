# Baton architecture

Baton is a native Android controller and optional audio output for the self-hosted
[`music`](https://github.com/pjunak/music) server. The server owns playback state; Baton renders
snapshots, sends typed actions, and can mirror the active audio lane through Media3.

This document describes the implementation that exists today. Product and design work that is
not built belongs in [DESIGN-NOTES.md](DESIGN-NOTES.md); durable trade-offs belong in
[DECISIONS.md](DECISIONS.md).

## System boundary

The sibling `music` repository is the protocol authority:

- `crates/music-protocol` defines `PlayerState`, messages, and actions.
- `crates/music-server` owns WebSocket registration, authorization, and dispatch;
  `crates/music-application/src/playback` owns canonical state transitions.
- `clients/README.md` defines output reconciliation and per-device volume semantics.
- `crates/music-server` defines authentication, library, mode, device, and update-facing HTTP APIs.

Baton is always an authenticated operator client. It does not expose a guest mode and does not
author library files, modes, cues, soundboards, or presets; Settings links to the web app for those
jobs. The optional phone-speaker role remains local and can be switched off without affecting
remote-control behavior.

## Modules

```text
app/               Compose UI, navigation, ViewModels, theme, DI, preferences
core-model/        Pure Kotlin protocol models and the sealed Action hierarchy
core-network/      URL handling, Retrofit APIs, shared OkHttp client, encrypted cookies
core-sync/         WebSocket registration/reconnect and StateFlow reconciliation
feature-playback/  Foreground ExoPlayer service, MediaSession, notification, SFX
feature-update/    GitHub Release discovery, APK download, installer handoff
```

Dependencies point inward: feature and app modules may depend on core modules; core modules do not
depend on Android UI. `core-model` stays Android-free so serialization and protocol behavior remain
cheap JVM tests.

## Configuration and authentication

The setup wizard accepts a server URL and operator credentials:

1. The URL is normalized to HTTPS, trailing slashes are removed, reverse-proxy subpaths are
   preserved, and `GET /api/health` verifies reachability.
2. Login establishes the server's HTTP-only session cookie.
3. A single `OkHttpClient` and encrypted persistent `CookieJar` serve both Retrofit and the
   WebSocket upgrade, so the authenticated cookie follows both transports.

The password is never persisted. Sign-out stops local speaker playback, clears the stored server
and session, and returns to setup. A 401 or WebSocket session-loss error follows the same re-login
path. Cleartext HTTP and custom certificate trust are intentionally unsupported.

## Synchronization and lifecycle

`SyncClient` exposes the latest `PlayerState`, connection status, server errors, and SFX events as
flows. A connection performs this sequence:

1. Open `<base>/api/ws` with the shared authenticated client.
2. Accept `state_snapshot`.
3. Register the stable persisted `client_id`, device name, and protocol version 2.
4. Replace local state on every newer `state_changed` revision.
5. Reconnect with bounded exponential backoff and register again.

ViewModels project this state and send typed `Action`s; they do not maintain another playback
truth. The Console seek position is the only dead-reckoned display value and snaps back to server
state.

`ConnectionCoordinator` keeps the socket connected while the Activity is started. A
controller-only app disconnects after a short background grace period. If the phone-speaker role
is enabled, the socket remains connected in the background so screen-off playback continues on
the phone speaker, wired audio, Bluetooth headphones, or Bluetooth speakers.

## Screens and data flow

- **Setup:** HTTPS server URL, health probe, and operator login.
- **Console:** cover art, now-playing metadata, seek, transport, shuffle/repeat, queue jumping,
  reordering, removal and clearing, plus the output picker. Starting playback with no active output
  routes to that picker instead of sending an inaudible resume. Mutating controls disable offline.
- **Library:** full folder hierarchy, persistent breadcrumbs, folder/search Back history, saved
  scroll positions, refresh/retry, debounced search, play/enqueue, and cover thumbnails. A local
  `LibraryBrowser` owns browsing history and cancels superseded loads; request generations also
  reject late responses. `SavedStateHandle` persists bounded navigation/scroll state, while a small
  in-memory content cache speeds return visits. This is UI state, not a playback reducer or an
  offline library. Authoring stays in the web app.
- **Session:** active mode, cues, soundboards and loops, EQ presets, and interrupts.
- **Settings:** General / Playback / Updates subtabs for account, server/web link, Keep Console
  awake, app version, and updater state. Tabs support taps and horizontal paging.
- **Devices:** a Console modal listing connected devices, live output activation, and canonical
  per-device volume. Selection is single-output by default; users can opt into multiple outputs in
  the modal. When exactly one output is active, its volume is also available beside the Console
  speaker button. The phone row controls the local speaker service as well as server membership.

The main shell saves each tab's Compose state and shows a metadata-only mini-player outside
Console. Its subscription does not start the Console seek ticker. Library taps still play
immediately; long press/overflow opens an action sheet, and a completed row swipe requests enqueue.
Gesture completion state is never persisted or replayed. Playback starts without an output open
the output picker and require a fresh tap after selection; enqueue does not require an output.
Socket acceptance is reported as a request sent, with playback and queue state still reconciled
from the server.

Queue drag gestures belong to the list so they survive scrolling the original row out of view.
A floating row and insertion marker preview the destination; edge holding scrolls the viewport.
Only release sends the move. Cancellation, disconnect, or a changed canonical queue discards the
drag, and dispatch checks the original queue again. Accessible move buttons use the same server
action path.

Queue ids are batch-resolved through `GET /api/library/tracks?ids=...`; folder navigation combines
`GET /api/library/folders` with `GET /api/library/tree?path=...`. These endpoints are already part
of the sibling server contract.

## Speaker role

`PlaybackController` projects the server's output membership into the foreground service.
The phone switch requests a membership change; playback starts after server confirmation.
Removal by any controller stops music and all SFX. Disconnect revokes audio permission,
and reconnect waits for a fresh snapshot without replaying an output-selection mutation.
An output-by-default designation can restore membership through server registration.
`SyncClient.liveState` supplies this current-connection snapshot separately from the last
known state retained for display while offline. SFX preparation rechecks permission before
starting audio, so a delayed prepare callback cannot revive a removed output.

`PlaybackService` contains:

- one ExoPlayer for the active music/interrupt lane;
- a permanently installed Media3 PCM processor for the active mode's ordered preset rack;
- a MediaSession whose play, pause, seek-next, and seek-previous commands send server actions;
- a media-style notification with metadata, artwork, transport, and a Stop speaker action;
- transient `MediaPlayer` instances for overlapping `sfx_fired` events;
- Media3 audio focus, becoming-noisy handling, and network wake mode.

The reconciler follows the server contract: an interrupt overrides ambient playback; a track
change loads and seeks; a changed `position_epoch` applies a deliberate seek; same-epoch state
updates do not chase the materialized server clock. Protocol-v2 absolute device volume applies to
music and SFX, with the legacy master-times-trim projection retained for old servers. Baton does
not currently send output position reports.

Effect-aware playback resolves `active_preset_ids` through the active mode's guest-readable preset
manifest endpoint. It caches manifests by mode and `preset_revision`, flattens multiple racks in
canonical active-id/declaration order, and fails dry on a load error or mode transition. The fixed
PCM stage supports the server's current graphic EQ, low/high/band-pass, delay, distortion, tremolo,
and reverb effects. Interrupt tracks bypass the ambient rack and transient SFX remain dry, matching
the web engine's routing. The single-player native lane still cuts between tracks; rendering the
canonical `crossfade_ms` remains a separate dual-ambient-player capability.

## Updater

`feature-update` checks `BuildConfig.UPDATE_REPO` through the GitHub Releases API. A silent launch
check only badges Settings when a newer tested Android build exists. Manual Settings actions expose checking,
release notes, download progress, install readiness, and errors.

The APK is streamed into `cacheDir/updates`, checked against its published size and SHA-256,
and verified for package identity/build number before reaching the system installer through a
`FileProvider`. Installation requires the one-time Android permission to install unknown apps.
The server address is never compiled into the APK; only the public update repository is.

## Build, signing, and release

The version catalog is the source of dependency and SDK versions. The current baseline is AGP
9.3.1, Gradle 9.6.1, Kotlin 2.4.10, JDK 17 bytecode, minSdk 33, and compile/target SDK 37.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug test lintDebug
```

Use Android Studio Run, Build APK(s), or `assembleDebug`. Android Studio's Make Project action is
not supported while it expects the removed `androidTestClasses` anchor task.

- `.github/workflows/ci.yml` runs the debug build, unit tests and lint for pull requests;
  main pushes additionally run `lintRelease assembleRelease` in the same Gradle invocation.
- Main signing uses the existing `KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`,
  `KEYSTORE_KEY_ALIAS` and `KEYSTORE_KEY_PASSWORD`; CI always removes temporary key files.
- A separate, dependent publication job uses only the already tested APK and GitHub's
  temporary `contents: write` token. A failed verification cannot publish. Releases are
  draft until the uploaded asset's digest is confirmed; reruns cannot replace released
  bytes, and older builds cannot take over the latest-release pointer.
- `scripts/tested_release.py` assigns `100000 + ci.yml run_number` as Android versionCode
  and uses a full-commit release tag. The app reads that number from the APK asset name,
  checks its commit against the tag, and ignores the display version when deciding
  availability. Keep the workflow sequence/offset stable to preserve Android upgrade order.
- The tag's `v0.3.7` prefix bridges older semver-only updaters without requiring a new
  signing identity. No automatic download or installation is introduced.
- Without `keystore.properties`, a local release build deliberately falls back to debug signing.

## Deferred work

The maintained UI/accessibility backlog is in [DESIGN-NOTES.md](DESIGN-NOTES.md). Larger deferred
capabilities are:

- private-CA/self-signed certificate trust;
- QR or short-lived-token pairing;
- multi-server switching and offline library caching;
- crash reporting;
- a server-hosted update feed if the Baton repository becomes private.
