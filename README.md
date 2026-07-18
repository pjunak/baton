# Baton

A native Android **remote control** (and optional **speaker**) for the self-hosted
[`music`](https://github.com/pjunak/music) backend — a single-operator music orchestrator for
tabletop RPG sessions. Baton is the *conductor's baton*: drive the session's playback from your
phone, and optionally let the phone itself be one of the audio outputs.

Like the server, Baton is **generic** — it bakes in *no* connection details. Point it at your own
`music` instance at first launch; nothing about a specific server is compiled in.

> **Status: early development.** The core remote-control loop works against a live server. The
> speaker role, the session/devices surfaces, the in-app updater, and CI are not built yet. See
> [Implementation status](#implementation-status).

---

## What works today

| Area | State |
|---|---|
| **First-launch setup** (server URL → credentials, HTTPS-only, reachability probe) | ✅ Working |
| **Console** — connection status, now-playing, play/pause, skip, **seek**, **shuffle/repeat**, **live queue** (remove/clear) | ✅ Working |
| **Library** — browse folder tree, debounced search, play track/folder, enqueue | ✅ Working |
| **Settings** — account + **sign-out**, server + "Open web app", app version | ✅ Working |
| **Session** — modes, cues, soundboard (tap = fire, hold = loop), EQ presets, interrupts | ✅ Working |
| **Devices + phone-as-speaker** (server-owned per-device volume, Media3, media-style notification) | ✅ Working |
| **In-app updater** (GitHub Releases → system installer) | ✅ Working |
| **CI + signed release** (GitHub Actions) | ✅ Working (needs the keystore secrets — see §12) |

---

## Requirements

- A running **`music`** server reachable over **HTTPS** with a system-trusted certificate
  (LAN/self-signed users put a reverse proxy or real cert in front — the setup the `music` README
  already assumes).
- **Android 13+** (`minSdk 33`).
- **Android Studio** (latest stable) to build — see the build note below.

## Build & run

This project runs on a deliberately current toolchain (AGP 9 / Gradle 9 / Kotlin 2.2). Two
build-workflow caveats are load-bearing:

1. **Build with Run ▶, "Build → Build APK(s)", or `:app:assembleDebug` — *not* "Make Project"
   (Ctrl+F9).** AGP 9 dropped the `androidTestClasses` anchor task that "Make Project" still
   requests, so Make fails at task selection. (The `androidTest` variant was removed from `:app`
   for the same reason — Compose UI tests return in a later pass.)
2. Command-line builds need `JAVA_HOME` pointing at a JDK 17+ (Android Studio's bundled
   `jbr` works: `C:\Program Files\Android\Android Studio\jbr`).

Typical loop: open in Android Studio → let it sync → **Run ▶** on an emulator or device → the app
opens to the setup wizard.

## Configuration

There is nothing to configure at build time. On first launch the app asks for your server URL
(it normalizes to `https://`, rejects cleartext, preserves any reverse-proxy sub-path, and probes
`/api/health`), then your credentials. The session is a revocable server-side token stored
encrypted on-device — the password is never stored. "Sign out" (Settings) forgets the server and
returns to setup.

The only compiled-in URL is the *update source* (the app's own repo), a build-time field a fork can
repoint — not the server you control.

---

## Architecture

Baton is a thin, reactive client of the `music` HTTP + WebSocket API. The server is the **single
source of truth** (it holds the canonical `PlayerState` and is the sole writer); the app renders
that state and sends typed `Action`s back — it never invents state.

```
app/             Compose UI, ViewModels, navigation, theme, DI entry points
core-model/      PlayerState, Track, the Action sealed hierarchy (mirrors the backend protocol).
                 Pure Kotlin/JVM — portable for a possible future KMP lift.
core-network/    One shared OkHttpClient + encrypted CookieJar, Retrofit services, DTOs,
                 server-URL resolution, the auth/session repository.
core-sync/       SyncClient: the WebSocket → StateFlow<PlayerState> + send(Action), with
                 reconnect/backoff and the register handshake.
feature-playback/ Foreground service + Media3 MediaSession: PlayerState→ExoPlayer reconciler
                  (speaker role) with a media-style notification whose transport routes to the
                  server (lock screen / media buttons control the room, not the local mirror).
feature-update/   GitHub Releases check → download-with-progress → system installer
                  (FileProvider + ACTION_VIEW). Silent check on launch badges the Settings tab.
```

The one piece of plumbing that makes auth "just work": the **same** `OkHttpClient` + `CookieJar`
serves both REST and the WebSocket, so logging in (which sets the `music_session` cookie) also
authenticates the socket the server reads that cookie on at upgrade.

Full design: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** ·
Key decisions: **[docs/DECISIONS.md](docs/DECISIONS.md)** ·
UI backlog: **[docs/DESIGN-NOTES.md](docs/DESIGN-NOTES.md)**

## Tech stack

Kotlin 2.2 · Jetpack Compose (Material 3, dynamic color) · Hilt · Coroutines/Flow (MVVM) ·
OkHttp + Retrofit · kotlinx.serialization · Coil 3 · Media3 · AGP 9.2 / Gradle 9.4 ·
version catalog (`gradle/libs.versions.toml`) · minSdk 33 / compile+target 35.

## Distribution

GitHub Releases as the artifact host, with an in-app updater (system installer via
FileProvider) — no Play Store for now (a single upload key is kept so a future Play App
Signing migration stays clean). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) §10/§12.

## License

TBD.
