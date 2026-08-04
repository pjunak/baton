# Baton

Native Android remote control and optional audio-output client for the sibling
`music` server. The server owns playback state; Baton renders that state and
sends typed actions. Keep server URLs and credentials runtime-configured.

## Read first

- [`README.md`](README.md) for supported features and build caveats.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for system design and flows.
- [`docs/DECISIONS.md`](docs/DECISIONS.md) for durable design decisions.
- The sibling [`music/AGENTS.md`](../music/AGENTS.md) and
  [`music/clients/README.md`](../music/clients/README.md) before changing the
  HTTP, WebSocket, device, or playback protocol.

## Commands

Run from PowerShell. Command-line Gradle needs JDK 17+; Android Studio's bundled
JBR is suitable.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug test lintDebug
```

Useful narrower checks:

```powershell
.\gradlew.bat :core-model:test :core-sync:test
.\gradlew.bat :app:assembleDebug
```

Use Android Studio Run or `assembleDebug`; do not use **Make Project** while
AGP 9 lacks the `androidTestClasses` anchor task expected by that action.

## Architecture

```text
app/               Compose UI, navigation, ViewModels, theme, DI
core-model/        Pure Kotlin models and Action protocol
core-network/      Retrofit/OkHttp, encrypted cookies, auth, URL resolution
core-sync/         WebSocket registration, reconnect, StateFlow reconciliation
feature-playback/  Media3 foreground service and speaker-role reconciliation
feature-update/    GitHub Release discovery, download, installer handoff
docs/              Architecture, decisions, and design notes
```

Module dependencies point inward: feature and app modules may depend on core
modules; core modules must not depend on Android UI or feature modules.

## Protocol boundaries

- `music` is the single source of truth. Do not create a competing local
  playback state machine in ViewModels or Compose state.
- `core-model` mirrors the server protocol and stays pure Kotlin/JVM. Coordinate
  wire-shape changes with the sibling server and cover serialization.
- REST and WebSocket traffic must share the same `OkHttpClient` and encrypted
  `CookieJar`; the authenticated session cookie must reach the socket upgrade.
- Use a stable client ID for device identity. Treat output activation,
  output-by-default designation, and per-device volume as separate concepts.
- Reconnect by registering again and reconciling the latest snapshot. Never
  replay stale mutating actions automatically.
- Keep the phone-speaker role optional. Remote-control behavior must work when
  local audio output is disabled or unavailable.

## Android conventions

- Kotlin, Compose Material 3, Hilt, coroutines/Flow, and version-catalog
  dependencies are the established stack.
- ViewModels own orchestration; composables render state and emit events.
- Long-running playback belongs in `feature-playback` and its foreground
  service, not in an Activity or composable lifecycle.
- MediaSession transport controls route to the server so hardware buttons,
  notification controls, and the room stay synchronized.
- Preserve HTTPS-only setup, reverse-proxy subpaths, encrypted session storage,
  and the rule that passwords are never persisted.
- Keep user-visible text in Android resources and provide accessible labels for
  icon-only controls.

## Testing and completion

- Pure protocol, reducer, and synchronization behavior belongs in JVM tests.
- Add module-local tests for non-trivial state transitions and serialization.
- Run the narrowest affected tests during development, then
  `.\gradlew.bat assembleDebug test lintDebug` before handoff.
- For protocol changes, verify against a live `music` instance: login,
  reconnect, transport, seek, queue, modes, devices, and optional speaker
  playback as applicable.
- Update README or architecture documentation when commands, modules, protocol
  assumptions, or supported behavior change.

Do not commit signing keys, server addresses, credentials, or generated local
configuration. Do not commit, push, publish a release, or change signing
configuration unless explicitly requested.
