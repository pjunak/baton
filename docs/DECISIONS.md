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

## ADR-0007 — Current toolchain, with the Hilt × AGP-9 `newDsl` constraint

**Context.** The app targets a deliberately current stack (AGP 9.2 / Gradle 9.4 / Kotlin 2.2 /
Hilt 2.57).

**Decision.** Keep `android.newDsl=false` and `android.builtInKotlin=false` in `gradle.properties`.
The Hilt Gradle plugin is incompatible with AGP 9's new DSL (sync fails: `ApplicationExtensionImpl
cannot be cast to BaseExtension`); `newDsl=false` is Google's own recommended workaround.

**Consequences.** A handful of obsolete-variant-API deprecation warnings are expected and harmless
(`org.gradle.warning.mode=summary`). They clear when Hilt supports the new DSL (forced by AGP 10,
~late 2026). **Build with Run ▶ / `assembleDebug`, never "Make Project"** — AGP 9 dropped the
`androidTestClasses` anchor that Make requests. Status: **accepted-with-constraint; revisit at AGP 10.**

---

## ADR-0008 — Add a batch track-metadata endpoint to the backend (fix at the source)

**Context.** `PlayerState.ambient.queue`/`history` are id lists. Rendering them one-`GET`-per-id is
an N+1.

**Decision.** Rather than work around it client-side, add `GET /api/library/tracks?ids=1,2,3 →
list[TrackOut]` to `music` (additive, guest-accessible like the single-track endpoint, shipped with
tests). Baton resolves a whole queue in one round trip.

**Consequences.** A small upstream PR ([pjunak/music#10](https://github.com/pjunak/music/pull/10))
the web app benefits from too. Baton's `TrackRepository`/queue resolution depends on it being
deployed; absent, queue rows degrade gracefully to "Track #<id>".
