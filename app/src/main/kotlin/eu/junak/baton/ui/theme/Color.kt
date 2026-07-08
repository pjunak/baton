package eu.junak.baton.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app-wide "active / on" accent — matches the web client's `--accent` (#6aa6ff).
 * Used for every engaged-state cue (shuffle/repeat modes, the selected nav tab, the
 * "this phone is an output" icon) so "on" reads the same everywhere, independent of
 * the wallpaper-derived Material You `primary`, which can come out muted/grey.
 */
val ActiveAccent = Color(0xFF6AA6FF)
