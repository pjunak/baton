package eu.junak.baton.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.network.ServerConfig
import eu.junak.baton.core.network.auth.AuthRepository
import eu.junak.baton.core.sync.SyncClient
import eu.junak.baton.feature.update.UpdateState
import eu.junak.baton.feature.update.Updater
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings: shows the connected server and signed-in account, offers the
 * "open the web app" authoring fallback, and owns sign-out (which tears down the
 * socket, revokes the session server-side, and forgets the server URL so the app
 * returns to first-launch setup).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverConfig: ServerConfig,
    private val syncClient: SyncClient,
    private val updater: Updater,
) : ViewModel() {

    data class UiState(
        val serverUrl: String? = null,
        val username: String? = null,
        val signingOut: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState(serverUrl = serverConfig.baseUrlOrNull()?.toString()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** In-app updater state + actions, delegated to the app-scoped [Updater]. */
    val updateState: StateFlow<UpdateState> = updater.state

    fun checkForUpdate() = updater.check()

    fun downloadUpdate(available: UpdateState.Available) = updater.download(available)

    fun installUpdate(apk: File) = updater.install(apk)

    init {
        viewModelScope.launch {
            val user = authRepository.currentUser()
            _ui.update { it.copy(username = user?.username) }
        }
    }

    fun signOut(onDone: () -> Unit) {
        if (_ui.value.signingOut) return
        _ui.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            syncClient.disconnect()
            authRepository.logout()
            serverConfig.forget()
            onDone()
        }
    }
}
