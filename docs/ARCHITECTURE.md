# Baton architecture

Baton is a native Android controller and optional audio output for the self-hosted
[`music`](https://github.com/pjunak/music) server. The server owns playback state; Baton renders
snapshots, sends typed actions, and can mirror the active audio lane through Media3.

This document describes the implementation that exists today. Product and design work that is
not built belongs in [DESIGN-NOTES.md](DESIGN-NOTES.md); durable trade-offs belong in
[DECISIONS.md](DECISIONS.md).

## System boundary

The sibling `music` repository is the protocol authority:

- `backend/app/sync/protocol.py` defines `PlayerState`, messages, and actions.
- `backend/app/sync/router.py` owns WebSocket registration, authorization, and dispatch.
- `clients/README.md` defines output reconciliation and per-device volume semantics.
- `backend/app/api/` defines authentication, library, mode, device, and update-facing HTTP APIs.

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
- **Library:** full folder hierarchy, folder contents, debounced search, play/enqueue, and cover
  thumbnails. Authoring stays in the web app.
- **Session:** active mode, cues, soundboards and loops, EQ presets, and interrupts.
- **Settings:** General / Playback / Updates subtabs for account, server/web link, Keep Console
  awake, app version, and updater state.
- **Devices:** a Console modal listing connected devices, live output activation, and canonical
  per-device volume. The phone row controls the local speaker service as well as server membership.

Queue ids are batch-resolved through `GET /api/library/tracks?ids=...`; folder navigation combines
`GET /api/library/folders` with `GET /api/library/tree?path=...`. These endpoints are already part
of the sibling server contract.

## Speaker role

`PlaybackController` owns the app-scope local on/off flag. Enabling it starts `PlaybackService` as
a media-playback foreground service and adds this stable device id to the server's live output
set. Because local audio is the real gate, the controller reasserts membership after reconnect.

`PlaybackService` contains:

- one ExoPlayer for the active music/interrupt lane;
- a MediaSession whose play, pause, seek-next, and seek-previous commands send server actions;
- a media-style notification with metadata, artwork, transport, and a Stop speaker action;
- transient `MediaPlayer` instances for overlapping `sfx_fired` events;
- Media3 audio focus, becoming-noisy handling, and network wake mode.

The reconciler follows the server contract: an interrupt overrides ambient playback; a track
change loads and seeks; a changed `position_epoch` applies a deliberate seek; same-epoch state
updates do not chase the materialized server clock. Protocol-v2 absolute device volume applies to
music and SFX, with the legacy master-times-trim projection retained for old servers. Baton does
not currently send output position reports.

## Updater

`feature-update` checks `BuildConfig.UPDATE_REPO` through the GitHub Releases API. A silent launch
check only badges Settings when a newer version exists. Manual Settings actions expose checking,
release notes, download progress, install readiness, and errors.

The APK is streamed into `cacheDir/updates` and handed to the system installer through a
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

- `.github/workflows/ci.yml` runs `assembleDebug test lintDebug` for pushes and pull requests.
- `.github/workflows/release.yml` derives version name/code from a `v*` tag, materializes signing
  configuration from `KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS`, and
  `KEYSTORE_KEY_PASSWORD`, builds the minified release APK, and publishes a GitHub Release.
- Without `keystore.properties`, a local release build deliberately falls back to debug signing.

## Deferred work

The maintained UI/accessibility backlog is in [DESIGN-NOTES.md](DESIGN-NOTES.md). Larger deferred
capabilities are:

- private-CA/self-signed certificate trust;
- QR or short-lived-token pairing;
- multi-server switching and offline library caching;
- crash reporting;
- a server-hosted update feed if the Baton repository becomes private.
