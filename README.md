# Baton

A native Android **remote control** (and optional **speaker**) for the self-hosted
[`music`](https://github.com/pjunak/music) backend — a single-operator music orchestrator for
tabletop RPG sessions. Baton is the *conductor's baton*: drive the session's playback from your
phone, and optionally let the phone itself be one of the audio outputs.

Like the server, Baton is **generic** — it bakes in *no* connection details. Point it at your own
`music` instance at first launch; nothing about a specific server is compiled in.

Server schema 11 changes session storage and requires one new sign-in after the
upgrade. Baton continues to use the configured opaque cookie for HTTP and
WebSocket connections; its native socket omits Origin. The server's new browser
Origin allowlist does not change Baton's wire messages or require an app-specific
origin setting.

> **Status:** the controller, optional speaker role, updater, CI, and signed-release pipeline are
> implemented. Remaining work is product polish and additional UI/static-analysis coverage; see
> [Design notes](docs/DESIGN-NOTES.md).

---

## What works today

| Area | State |
|---|---|
| **First-launch setup** (server URL → credentials, HTTPS-only, reachability probe) | ✅ Working |
| **Console** — connection status, now-playing, play/pause, skip, **seek**, **shuffle/repeat**, **live queue** (jump/reorder/remove/clear) | ✅ Working |
| **Library** — folder Back/breadcrumbs, saved browsing position, pull-to-refresh, debounced search, tap to play, swipe to enqueue, track action sheet | ✅ Working |
| **Settings** — General / Playback / Updates tabs, console-awake opt-in, account + **sign-out**, server + "Open web app" | ✅ Working |
| **Session** — modes, cues, soundboard (tap = fire, hold = loop), EQ presets, interrupts | ✅ Working |
| **Devices + phone-as-speaker** (single-output-first picker, direct Console volume, optional multi-output, native preset effects, Play prompts when no output is active, Media3 notification) | ✅ Working |
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

This project runs on a deliberately current toolchain (AGP 9 / Gradle 9 / Kotlin 2.4). Two
build-workflow caveats are load-bearing:

1. **Build with Run ▶, "Build → Build APK(s)", or `:app:assembleDebug` — *not* "Make Project"
   (Ctrl+F9).** AGP 9 dropped the `androidTestClasses` anchor task that "Make Project" still
   requests, so Make fails at task selection. Use the explicit Gradle tasks below for builds
   and device-backed Compose tests.
2. Command-line builds need `JAVA_HOME` pointing at a JDK 17+ (Android Studio's bundled
   `jbr` works: `C:\Program Files\Android\Android Studio\jbr`).

The full local/CI gate is:

```powershell
.\gradlew.bat assembleDebug test lintDebug
```

With an Android emulator or test phone connected, run the additional Compose gesture suite:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The gesture tests use local fake data and need no server login. They are separate from the
JVM/CI gate, which does not provision an emulator. Physical TalkBack and one-handed gesture
acceptance remain manual checks.

CI selects the JetBrains JDK 21 declared by the Gradle daemon toolchain, while
application bytecode targets Java 17. Pull requests run the debug build, unit tests
and lint. Each push to main also runs release lint, signs the APK with the existing
key, then publishes that tested commit as a GitHub Release. Failed checks publish
nothing. Reports and intermediate APKs are retained for 14 days; published APKs
remain available as release assets.

In **Settings → Updates**, Baton offers the newest tested build even when its
human-facing version number has not changed. Download/install still requires your
action and Android's confirmation. There is no personal access token for checking,
downloading or publishing these public APKs: CI uses GitHub's temporary job token.

The first commit build keeps a `v0.3.7-commit.<SHA>` tag so existing v0.3.6 installs
can discover it through their old updater. Subsequent checks compare Android build
numbers (`100000 + ci.yml run_number`), not version names. Keep that workflow and
number offset stable; a replacement pipeline must continue above all released
codes. Tags are created by CI, so a manual version tag is no longer a release step.
Release assets encode the build number and full commit, and downloaded bytes are
checked against GitHub's SHA-256 digest before handing the APK to Android.

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
                  (speaker role), mode-preset manifest cache + native PCM effect rack, and a
                  media-style notification whose transport routes to the server (lock screen /
                  media buttons control the room, not the local mirror).
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

Kotlin 2.4 · Jetpack Compose (Material 3, dynamic color) · Hilt · Coroutines/Flow (MVVM) ·
OkHttp + Retrofit · kotlinx.serialization · Coil 3 · Media3 · AGP 9.3.1 / Gradle 9.6.1 ·
version catalog (`gradle/libs.versions.toml`) · minSdk 33 / compile+target SDK 37.

## Distribution

GitHub Releases as the artifact host, with an in-app updater (system installer via
FileProvider) — no Play Store for now (a single upload key is kept so a future Play App
Signing migration stays clean). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#build-signing-and-release).

## License

This repository is not currently offered under an open-source license.
