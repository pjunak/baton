# Baton

Native Android remote control and optional audio-output client for the sibling
`music` server. The server owns playback state; Baton renders that state and
sends typed actions. Keep server URLs and credentials runtime-configured.

## Read by task

- [`README.md`](README.md) for setup, supported features and build caveats.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) when changing module ownership or data flows.
- [`docs/DECISIONS.md`](docs/DECISIONS.md) when changing a documented design decision.
- The sibling [`music/AGENTS.md`](../music/AGENTS.md) and
  [`music/clients/README.md`](../music/clients/README.md) before changing the
  HTTP, WebSocket, device, or playback protocol.

## Commands

Use the Gradle wrapper (gradlew.bat in PowerShell, ./gradlew in a POSIX shell).
Command-line Gradle needs JDK 17+; Android Studio's bundled JBR is suitable.
Keep a working JAVA_HOME. The following is an example only when Java is missing
or incompatible; locate the installed JDK rather than assuming this path exists.

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

For prose or agent-guidance-only changes, review the diff, check local links,
and verify changed commands or contract claims. Runtime builds and operational
acceptance are required only for the affected behavior below. Reuse successful
checks on unchanged inputs; preserve complete CI and release gates.

- Pure protocol, reducer, and synchronization behavior belongs in JVM tests.
- Add module-local tests for non-trivial state transitions and serialization.
- Run the narrowest affected tests during development, then
  `.\gradlew.bat assembleDebug test lintDebug` for runtime/build changes before handoff.
- For protocol changes, use an isolated local/test `music` instance: login,
  reconnect, transport, seek, queue, modes, devices, and optional speaker
  playback as applicable. Record physical phone/speaker acceptance separately;
  JVM, emulator and server checks do not prove audible hardware playback.
- Update README or architecture documentation when commands, modules, protocol
  assumptions, or supported behavior change.

Do not commit signing keys, server addresses, credentials, or generated local
configuration. The global Codex instructions govern task commits. Never push,
publish a release, or change signing configuration unless explicitly requested.
