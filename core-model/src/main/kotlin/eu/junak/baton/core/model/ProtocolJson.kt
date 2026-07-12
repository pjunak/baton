package eu.junak.baton.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * The canonical JSON configuration for the music sync protocol — the single
 * source of truth for how these models map to the wire. Used by both the REST
 * converter and the WebSocket codec so the two transports agree.
 *
 * - **Naming:** Kotlin properties are camelCase; the wire is snake_case
 *   ([JsonNamingStrategy.SnakeCase]), matching the backend's pydantic models.
 * - **Discriminator:** polymorphic [Action] / [ServerMessage] are tagged with a
 *   `type` key, matching the backend's discriminated unions.
 * - **encodeDefaults:** on, so the server always receives explicit values
 *   (e.g. `volume`, `return_to_ambient`) rather than relying on its own defaults.
 * - **ignoreUnknownKeys:** on, so a newer server adding fields never breaks an
 *   older client.
 * - **coerceInputValues:** on, so an unknown *enum value* from a newer server
 *   (loop/shuffle/crossfade churn is real — "weighted" already came and went)
 *   coerces to the property's default instead of throwing, which would make
 *   every subsequent `state_changed` unparseable and freeze the client on
 *   stale state. Requires enum-typed properties to declare defaults — they do.
 */
@OptIn(ExperimentalSerializationApi::class)
val ProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    classDiscriminator = "type"
    namingStrategy = JsonNamingStrategy.SnakeCase
}
