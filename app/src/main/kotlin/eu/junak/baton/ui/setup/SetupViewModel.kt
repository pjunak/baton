package eu.junak.baton.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.network.ServerConfig
import eu.junak.baton.core.network.auth.AuthRepository
import eu.junak.baton.core.network.auth.LoginResult
import eu.junak.baton.core.network.auth.ProbeResult
import eu.junak.baton.core.network.url.ServerUrl
import eu.junak.baton.core.network.url.UrlValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the two-step setup wizard: enter server URL (normalize → HTTPS-only →
 * `/api/health` probe → remember it), then credentials (login → token stored by
 * the cookie jar). On success the caller navigates to the console.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverConfig: ServerConfig,
) : ViewModel() {

    enum class Step { URL, CREDENTIALS }

    data class UiState(
        val step: Step = Step.URL,
        val url: String = "",
        val username: String = "",
        val password: String = "",
        val busy: Boolean = false,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun onUrlChange(value: String) = _ui.update { it.copy(url = value, error = null) }
    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, error = null) }
    fun back() = _ui.update { it.copy(step = Step.URL, error = null) }

    fun submitUrl() {
        when (val validation = ServerUrl.normalize(_ui.value.url)) {
            is UrlValidation.Invalid -> _ui.update { it.copy(error = validation.reason) }
            is UrlValidation.Valid -> {
                _ui.update { it.copy(busy = true, error = null) }
                viewModelScope.launch {
                    when (val probe = authRepository.probe(validation.baseUrl)) {
                        is ProbeResult.Reachable -> {
                            serverConfig.setBaseUrl(validation.baseUrl)
                            _ui.update { it.copy(busy = false, step = Step.CREDENTIALS) }
                        }
                        is ProbeResult.Unreachable ->
                            _ui.update { it.copy(busy = false, error = probe.message) }
                    }
                }
            }
        }
    }

    fun submitLogin(onConnected: () -> Unit) {
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(_ui.value.username, _ui.value.password)) {
                is LoginResult.Success -> {
                    _ui.update { it.copy(busy = false) }
                    onConnected()
                }
                LoginResult.InvalidCredentials ->
                    _ui.update { it.copy(busy = false, error = "Invalid username or password.") }
                is LoginResult.Error ->
                    _ui.update { it.copy(busy = false, error = result.message) }
            }
        }
    }
}
