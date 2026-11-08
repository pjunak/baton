package eu.junak.baton.feature.playback

import eu.junak.baton.core.model.PlayerState

/**
 * Android's audio-focus contract for the phone speaker: when another app takes
 * the audio for good, or headphones are unplugged, stop and stay stopped until
 * someone deliberately plays again.
 *
 * ExoPlayer pauses itself on those events, but [PlaybackService] re-applies the
 * server's play state on every broadcast and would take the audio back at the
 * next track. This holds the yield until a deliberate play: a local play
 * (notification, lock screen, headset), a fresh start of server playback
 * (paused → playing, from any remote), or a new speaker role.
 *
 * What happens to the server depends on the active mode. With one output the
 * phone IS the room, so the server pauses too and its clock stops with the
 * sound. With several outputs the room keeps playing and only this phone goes
 * quiet.
 */
internal class AudioYield {
    var yielded: Boolean = false
        private set

    private var lastServerPlaying: Boolean? = null

    /** Android took the audio away. Returns true when the server should pause too. */
    fun onAudioLost(state: PlayerState?): Boolean {
        yielded = true
        return state != null && !state.multipleOutputs && state.isPlaying
    }

    /** Feed each canonical play state; returns whether the phone should play. */
    fun onState(serverPlaying: Boolean): Boolean {
        if (lastServerPlaying == false && serverPlaying) yielded = false
        lastServerPlaying = serverPlaying
        return serverPlaying && !yielded
    }

    /** A deliberate local play reclaims the audio. */
    fun onLocalPlay() {
        yielded = false
    }

    /** The speaker role ended; the next one starts fresh. */
    fun reset() {
        yielded = false
        lastServerPlaying = null
    }
}
