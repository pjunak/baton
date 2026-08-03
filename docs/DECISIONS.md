# Architecture Decision Records

Short, durable records of the **locked** decisions behind Baton — the *why*, so they aren't
re-litigated. Format: Context → Decision → Consequences. Newest concerns at the bottom.

For the full design these decisions live inside, see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## ADR-0001 — Generic client, runtime server config (no baked-in address)

**Context.** `music` is self-hosted; anyone can run an instance. A client hardcoded to one server
would fork per deployment.

**Decision.** Bake in *no* connection details. The server base URL is entered at first launch and
stored on-device. The only compiled-in URL is the *update source* (the app's own repo), a
build-time field a fork can repoint.

**Consequences.** A setup wizard and on-device URL store are required (`ServerConfig` /
`NetworkStore`). One published APK serves every operator. "Sign out" forgets the URL and returns to
setup.

---

## ADR-0002 — HTTPS only, system-trusted certificates

**Context.** The app carries a session cookie and credentials over the network.

**Decision.** Connect only to `https://` servers with a valid system-trusted cert. No cleartext, no
self-signed trust in v1. The URL normalizer defaults the scheme to `https` and rejects non-HTTPS.

**Consequences.** `usesCleartextTraffic=false` (default), no custom network-security config.
LAN/self-signed users front their server with a reverse proxy or real cert. Trust-on-first-use for
private CAs is explicitly deferred to phase 2.

---

## ADR-0003 — Always authenticated; one shared client + cookie jar for REST *and* WebSocket

**Context.** The backend authenticates the `/api/ws` upgrade by reading the `music_session`
**cookie** (HttpOnly; no bearer tokens). There is no guest operator.

**Decision.** No guest mode. A single `OkHttpClient` with a persistent, encrypted `CookieJar` is
shared by Retrofit and the OkHttp `WebSocket`. Login sets the cookie once; the socket is then
authenticated automatically.

**Consequences.** Auth is "get it right once" plumbing. The session token is stored
Keystore-encrypted via `SecureStore`/`NetworkStore` (SharedPreferences, not DataStore — the cookie
jar needs synchronous reads); the password is never stored. On 401/expiry the token is dropped and
login re-prompted (URL remembered).

---

## ADR-0004 — Server-as-reducer: render state, send Actions, never invent state

**Context.** The backend is itself a reducer — a canonical `PlayerState` mutated only by a typed
`Action` union, broadcast over the socket.

**Decision.** Mirror it 1:1. The app replaces its local `PlayerState` on every `state_changed` and
sends typed `Action`s for every mutation. The UI holds no authoritative playback state.

**Consequences.** `core-model` mirrors `protocol.py` (sealed `Action`, `type` discriminator).
ViewModels are thin projections + `send(Action)`. The *only* sanctioned local optimism is
dead-reckoning the seek position between server reports (snapped back on each report) — a UX
nicety, not a second source of truth.

---

## ADR-0005 — Distribute via GitHub Releases + in-app updater (not the Play Store, for now)

**Context.** A niche self-hosted tool; the operator already trusts the project. Play Store adds
review latency and account friction.

**Decision.** Publish signed APKs to GitHub Releases; the app self-updates via the Releases API.

**Consequences.** Needs `REQUEST_INSTALL_PACKAGES` + a one-time system "allow installs from Baton".
A single **upload key** is kept forever so a future Play App Signing migration stays clean.
Unauthenticated Releases API (public repo) is ample for launch-time checks.

**Amendment (as built).** The install step uses the system installer (`FileProvider` +
`ACTION_VIEW`) rather than the `PackageInstaller` session API — the confirmation UI it shows is
a feature for a sideloaded updater, not friction — and the version compare runs on the release
tag vs the installed `versionName` (both derive from the same git tag in CI, so they agree).

---

## ADR-0006 — Consume, don't author (web-app fallback)

**Context.** Authoring (creating soundboards/EQ/modes/cues, library metadata edits, uploads) is
rare-on-phone, complex, and already well served by the web SPA.

**Decision.** Baton *consumes* authored content (switch modes, fire cues/SFX, apply presets, play
library items) but does not create/edit it. A Settings "Open web app" button covers the rare
on-phone authoring need.

**Consequences.** Smaller surface, fewer destructive paths. The Library tab is read-only.

---

## ADR-0007 — Current toolchain with AGP built-in Kotlin

**Context.** The app targets a deliberately current stack (AGP 9.3 / Gradle 9.6 / Kotlin 2.4 /
Hilt 2.60).

**Decision.** Use AGP's built-in Kotlin support and modern DSL defaults. Android modules do not
apply the deprecated `org.jetbrains.kotlin.android` plugin; the pure JVM `core-model` module keeps
the standard Kotlin JVM plugin. Current Hilt and KSP versions work with this configuration.

**Consequences.** The temporary `android.newDsl=false`, `android.builtInKotlin=false`, unique-package,
and legacy R8 opt-outs are gone, avoiding an AGP 10 migration blocker. **Build with Run ▶ /
`assembleDebug`, never "Make Project"** — AGP 9 dropped the `androidTestClasses` anchor that Make
requests. Status: **accepted.**

---

## ADR-0008 — Add a batch track-metadata endpoint to the backend (fix at the source)

**Context.** `PlayerState.ambient.queue`/`history` are id lists. Rendering them one-`GET`-per-id is
an N+1.

**Decision.** Rather than work around it client-side, add `GET /api/library/tracks?ids=1,2,3 →
list[TrackOut]` to `music` (additive, guest-accessible like the single-track endpoint, shipped with
tests). Baton resolves a whole queue in one round trip.

**Consequences.** The endpoint is now part of the deployed sibling-server contract and benefits
the web app as well. Baton batch-resolves queue misses and preserves id order and duplicates;
failed metadata still degrades gracefully to `Track #<id>`.

---

## ADR-0009 — Lifecycle-aware controller connection; speaker playback is the exception

**Context.** Keeping the WebSocket, display clock, and screen active while Baton is a backgrounded
controller wastes battery. Disconnecting unconditionally would stop the foreground speaker role,
including screen-off Bluetooth playback.

**Decision.** Keep realtime sync connected while either the Activity is started or the local
speaker role is enabled. Give UI backgrounding a short grace period to absorb rotations and
overlays. Run the Console position ticker only while lifecycle-aware UI collectors exist. Keep
screen-on behavior opt-in and Console-scoped.

**Consequences.** A controller-only Baton disconnects shortly after backgrounding and reconciles
from a fresh snapshot on return. Speaker playback retains its foreground service, Media3 network
wake mode, and socket regardless of the active Android audio route, so wired and Bluetooth output
continue with the screen off.
