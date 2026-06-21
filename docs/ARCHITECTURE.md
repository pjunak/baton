# Baton — Architecture & Design

**Baton** is a native Android client for the self-hosted [music](https://github.com/pjunak/music)
backend: a controller ("conductor's baton") that drives a playback session from your phone,
and optionally an audio output (the phone as a speaker).

Like the server, Baton is **generic**: it bakes in *no* connection details. Anyone who runs
their own `music` instance can point the app at it. The server URL is entered at first launch
and stored on-device — nothing about a specific instance is compiled in.

---

## 1. Relationship to the `music` backend

Baton is a pure client of the `music` HTTP + WebSocket API. The contract lives in that repo:

- **Sync protocol & state shape:** `backend/app/sync/protocol.py` (`PlayerState`, the `Action` union).
- **WebSocket endpoint & auth:** `backend/app/sync/router.py` (`/api/ws`, session-cookie auth on upgrade).
- **Output/reconcile model:** `clients/README.md` (how a client decides what to play).
- **Library/auth/devices HTTP surface:** `backend/app/api/*.py`.

The server is the **single source of truth**: it holds the canonical `PlayerState` and is the sole
writer. Clients *reconcile* — the app renders state and sends typed actions; it never invents state.
This mirrors the backend's own "server-as-reducer" design almost 1:1.

---

## 2. Scope

### v1 — in
- **Controller / console:** transport (play/pause/skip/seek), master volume, loop & shuffle, the
  live queue (view/reorder/remove), now-playing.
- **Library browse & play:** tree/folders/search; play a track, folder, or playlist; enqueue.
- **Session surface (consume, don't author):** switch active **mode**, fire **cues**, fire/loop
  **SFX** from the active mode's soundboards, apply **EQ presets**, fire/cancel **interrupts**.
- **Devices/outputs:** see connected devices, toggle which are active outputs, per-device volume trim.
- **Phone as a speaker:** the phone itself can be an audio output (Media3 + a media service).
- **In-app self-update** from GitHub Releases.

### v1 — explicitly out (use the web app)
- **Authoring** of any kind: creating/editing soundboards, EQ presets, modes, cues; library
  metadata edits, uploads, file moves, cleanup. The app *consumes* authored content. A
  **"Open web app"** button (Chrome Custom Tab) covers the rare on-phone authoring need.

### Non-negotiable constraints
1. **Generic — no baked-in connection address.** Server URL is 100% runtime config. The only
   compiled-in URL is the *update source* (the app's own repo), and that is a build-time field a
   fork can repoint.
2. **HTTPS only.** The app connects exclusively to servers with a valid, system-trusted TLS cert.
   No cleartext HTTP, no self-signed trust in v1. (LAN/self-signed users put a reverse proxy or
   real cert in front — the setup the `music` README already assumes.)
3. **Always authenticated — no guest mode.** The app always logs in as the operator. One WS code
   path (cookie always sent); the speaker role registers as a normal authenticated device.

---

## 3. Tech stack (locked)

| Concern | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3) |
| Architecture | MVVM + unidirectional data flow; Coroutines + Flow |
| DI | **Hilt** |
| REST | OkHttp + Retrofit |
| Real-time | OkHttp `WebSocket` — **same `OkHttpClient` + `CookieJar` as REST** |
| JSON | kotlinx.serialization (sealed `Action` hierarchy ↔ `type`-discriminated union) |
| Playback | Media3 / ExoPlayer in a `MediaSessionService`; SoundPool for one-shot SFX |
| Images | Coil 3 (uses the shared OkHttp client) |
| Storage | DataStore (settings) + Android Keystore-encrypted session token; Room only if/when offline caching is added |
| Navigation | Navigation Compose (type-safe routes) |
| Updater | GitHub Releases API + OkHttp download + `PackageInstaller` |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`) |
| CI | GitHub Actions (`ci.yml` + `release.yml`) |
| Quality | Spotless (ktlint) + detekt; JUnit + Turbine + MockWebServer; Compose UI test |
| SDK | minSdk 33 (Android 13); compile/target 35 (Android 15) — 36-ready once AGP supports it |
| appId | `eu.junak.baton` *(proposed)* |

---

## 4. Module layout

```
app/                 Compose UI, screens, ViewModels, navigation, theme, DI entry points
core-model/          PlayerState, Track, the Action sealed hierarchy (mirrors protocol.py).
                     Plain Kotlin — kept portable in case of a future KMP/iOS lift.
core-network/        Shared OkHttpClient + encrypted CookieJar, Retrofit services, DTOs,
                     server-URL resolution, the auth/session repository.
core-sync/           SyncClient: the WS connection → StateFlow<PlayerState> + send(Action).
                     Reconnect/backoff, the register handshake.
feature-playback/    MediaSessionService + the PlayerState→ExoPlayer reconciler (speaker role).
feature-update/      GitHub Releases check → download → PackageInstaller.
```

---

## 5. Connection & auth

### Setup wizard (URL → credentials)
1. **Server URL.** User types e.g. `music.example.com`. The app:
   - **Normalizes:** trim; default scheme to `https://`; **reject** non-HTTPS with a clear message;
     strip trailing slash; **preserve any sub-path** (store a *base URL*, append `/api/...` to it,
     so reverse-proxy sub-path mounts work).
   - **Probes** `GET <base>/api/health` → expects `{"status":"ok"}`. Confirms reachability + TLS
     before asking for a password; surfaces actionable errors (DNS / TLS / unreachable / not-a-music-server).
2. **Credentials.** `POST <base>/api/auth/login` `{username,password}` → server sets the
   `music_session` cookie. The login is the authoritative validation.

A **"Change server"** action in Settings re-runs this wizard.

### The central plumbing: one client, one cookie jar
A single `OkHttpClient` carries a **persistent `CookieJar`**, and is reused for:
- Retrofit (all REST), and
- the OkHttp `WebSocket`.

Because the server authenticates `/api/ws` by reading the `music_session` cookie **on the upgrade
request**, sharing the client means login automatically authenticates the socket. This is the one
piece of plumbing that makes auth "just work" everywhere — get it right once.

### Token storage & expiry
- The session is a **30-day server-side token** (configurable; revocable from the web Sessions UI).
  The app stores **only that token**, encrypted with an Android Keystore-derived key — it **never
  stores the password**.
- On 401 / expiry, the app drops the token and re-prompts login (URL is remembered). The WS
  re-checks expiry per action server-side; the app treats an `error` frame mentioning expiry the
  same as a 401.

### Transport
- HTTPS only → `usesCleartextTraffic=false` (default), no custom network-security config.
- WS URL derived from the base: `https→wss`, same host/path + `/api/ws`.

---

## 6. Sync layer (`core-sync`)

`SyncClient` owns the WebSocket lifecycle and exposes `StateFlow<PlayerState>` + `connectionState`:

1. Connect to `<wss-base>/api/ws` with the shared (authenticated) client.
2. Receive `state_snapshot` → seed state.
3. Send `register` with a **stable, persisted `client_id`** (UUID minted once, stored in DataStore)
   and a device name, so the phone appears in the operator's device list and its output designation
   sticks across reconnects.
4. Apply every `state_changed`. Handle `sfx_fired` (speaker role) and `error` (surface as a toast).
5. Reconnect on close with exponential backoff; re-run from step 2.

Mutations are sent as typed `Action`s (`send(action)`), e.g. `pause`, `resume`, `ambient_play_track`,
`ambient_skip_next`, `ambient_seek`, `set_volume`, `ambient_set_queue`, `set_active_mode`,
`fire_cue`, `fire_sfx`, `start_loop`/`stop_loop`, `fire_interrupt_*`, `cancel_interrupt`,
`set_active_outputs`, `set_device_volume`. (Full set: `protocol.py`.)

Lifetime: in the **media service** when acting as a speaker (survives backgrounding); otherwise an
app/ViewModel-scoped holder while the UI is foreground.

---

## 7. Library & queue data

`PlayerState.ambient.queue` / `history` are **id lists**. To render them without N+1 calls, Baton
relies on a new batch endpoint in `music` (see §11):

- `GET /api/library/tracks?ids=1,2,3` → `list[TrackOut]` — resolve a whole queue/history in one trip.

Browsing uses `GET /api/library/tree?path=`, `GET /api/library/folders` (full hierarchy for the
client-side tree), and `GET /api/library/search?q=&limit=&offset=&sort=&order=` (paginated).
Cover art: `GET /api/library/tracks/{id}/cover` via Coil.

---

## 8. Speaker role (`feature-playback`)

A `MediaSessionService` hosts an ExoPlayer and subscribes to `StateFlow<PlayerState>`. When this
device is "on" (in `active_output_device_ids`, or a local override the user controls):

- Compute the active lane per `clients/README.md`: `interrupt` overrides `ambient`;
  `playing = interrupt ? true : is_playing`; load `<base>/api/library/tracks/{id}/stream`; **seek
  only on track change or a >~1.5 s jump** (don't re-seek every frame).
- **SFX (`sfx_fired`)** layered over the music: **SoundPool** for one-shots (cached to disk on first
  play), a small secondary ExoPlayer for **looping** SFX.
- The service provides audio focus, lock-screen/notification transport, and headset-button handling.
- **No position reporting in v1** (the reference client doesn't either — avoids scrub fights).

---

## 9. Screens

1. **Setup** — server URL → credentials (§5).
2. **Console (home)** — now-playing, transport, seek, master volume, loop/shuffle, live queue,
   "playing from" source.
3. **Library** — browse tree/folders + search; play/enqueue track, play folder/playlist. Read-only.
4. **Session** — active mode picker, cues, soundboards (fire/loop SFX), EQ presets, interrupts.
5. **Devices** — connected outputs, toggle active, per-device trim, toggle *this phone* as a speaker
   (local on/off + local volume).
6. **Settings** — server (change/forget), account & active sessions, this-device name, speaker prefs,
   check-for-updates, **Open web app**.

---

## 10. In-app updater (`feature-update`)

GitHub Releases as the artifact host; the app self-updates (no Play Store):

- **Source:** `BuildConfig.UPDATE_REPO` (build-time field, default `pjunak/baton`; a fork repoints it).
- **Check:** `GET https://api.github.com/repos/{UPDATE_REPO}/releases/latest`; compare to
  `BuildConfig.VERSION_CODE`. Unauthenticated (public repo) → 60 req/hr/IP, ample for launch checks.
- **Download:** OkHttp streams the `.apk` asset to `cacheDir` with in-app progress.
- **Install:** `PackageInstaller` session API. Requires `REQUEST_INSTALL_PACKAGES` + a one-time
  "allow installs from Baton" system toggle.
- **UX:** check on launch; offer Install / Skip-this-version / Later.

---

## 11. Required `music` backend change

**Batch track-metadata endpoint** (separate small PR in `pjunak/music`):

```
GET /api/library/tracks?ids=1,2,3  →  list[TrackOut]
```
- ~15 lines in `backend/app/api/library.py`; additive, low risk.
- Resolves queue/history in one round trip; the web app benefits equally.
- Access level matches the single-track endpoint (`OptionalUser`) for consistency.
- Ship with a pytest case; CI (`ruff` + `pytest`) gates it.

---

## 12. Build, signing & release

- **Gradle Kotlin DSL + version catalog.** `versionName` from the git tag (`v1.2.3` → `1.2.3`);
  `versionCode = major*10000 + minor*100 + patch`.
- **`ci.yml`** (push/PR to `main`): Spotless/detekt + `./gradlew lint testDebugUnitTest`.
- **`release.yml`** (tag `v*`): verify → decode keystore from `KEYSTORE_BASE64` + write
  `keystore.properties` from secrets → `./gradlew assembleRelease` → attach signed APK to a GitHub
  Release with generated notes.
- **Secrets:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. (Local equivalents
  are git-ignored.) Keep one **upload key** forever for a clean future Play migration (Play App Signing).
- **Manifest permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `REQUEST_INSTALL_PACKAGES`.

---

## 13. Deferred / phase 2

- **Self-signed / private-CA HTTPS** via trust-on-first-use (show fingerprint, pin).
- **QR pairing:** web Settings renders a QR of the server URL (skip typing); ambitiously, a
  short-lived pairing token so the app gets a session with no typing (backend feature).
- **Server-hosted update feed** — only needed if `baton` ever goes private.
- **Multi-server** switching; **Room** offline caching of library/playlists; **crash reporting**
  (e.g. ACRA → a `music` endpoint).
