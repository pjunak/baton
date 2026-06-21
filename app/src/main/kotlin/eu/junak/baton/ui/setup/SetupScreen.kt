package eu.junak.baton.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SetupScreen(
    onConnected: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Connect to your music server",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(24.dp))

            when (ui.step) {
                SetupViewModel.Step.URL -> {
                    OutlinedTextField(
                        value = ui.url,
                        onValueChange = viewModel::onUrlChange,
                        label = { Text("Server address") },
                        placeholder = { Text("music.example.com") },
                        singleLine = true,
                        enabled = !ui.busy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::submitUrl,
                        enabled = !ui.busy && ui.url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continue")
                    }
                }

                SetupViewModel.Step.CREDENTIALS -> {
                    OutlinedTextField(
                        value = ui.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ui.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !ui.busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.submitLogin(onConnected) },
                        enabled = !ui.busy && ui.username.isNotBlank() && ui.password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sign in")
                    }
                    TextButton(onClick = viewModel::back, enabled = !ui.busy) {
                        Text("Use a different server")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (ui.busy) {
                CircularProgressIndicator()
            }
            ui.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
