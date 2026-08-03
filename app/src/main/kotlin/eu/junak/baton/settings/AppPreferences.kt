package eu.junak.baton.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Small, app-local preferences that affect Baton's UI rather than its server contract. */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _keepConsoleAwake = MutableStateFlow(
        preferences.getBoolean(KEY_KEEP_CONSOLE_AWAKE, false),
    )

    /** Opt-in because keeping the display lit is one of the app's largest battery costs. */
    val keepConsoleAwake: StateFlow<Boolean> = _keepConsoleAwake.asStateFlow()

    fun setKeepConsoleAwake(enabled: Boolean) {
        if (_keepConsoleAwake.value == enabled) return
        preferences.edit().putBoolean(KEY_KEEP_CONSOLE_AWAKE, enabled).apply()
        _keepConsoleAwake.value = enabled
    }

    private companion object {
        const val PREFERENCES_NAME = "baton_ui_preferences"
        const val KEY_KEEP_CONSOLE_AWAKE = "keep_console_awake"
    }
}
