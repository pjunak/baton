package eu.junak.baton.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import eu.junak.baton.core.model.Track
import eu.junak.baton.ui.theme.BatonTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MiniPlayerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tapAndUpwardSwipeOpenConsole() {
        var opens = 0
        compose.setContent {
            BatonTheme(dynamicColor = false) {
                MiniPlayer(Track(1, "rain.mp3", "Rain", "Artist", "", "", addedAt = "2026-09-10"), null) { opens++ }
            }
        }
        compose.onNodeWithText("Rain").performClick()
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onNodeWithText("Rain").performTouchInput {
            swipe(Offset(centerX, centerY), Offset(centerX, centerY - 200f), 500)
        }
        compose.runOnIdle { assertEquals(2, opens) }
    }
}
