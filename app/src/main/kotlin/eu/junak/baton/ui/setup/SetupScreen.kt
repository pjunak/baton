package eu.junak.baton.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.R
import eu.junak.baton.ui.theme.BatonSpacing

@Composable
fun SetupScreen(
    onConnected: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(BatonSpacing.Large),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(BatonSpacing.Small))
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(BatonSpacing.Large))

                when (ui.step) {
                    SetupViewModel.Step.URL -> {
                        OutlinedTextField(
                            value = ui.url,
                            onValueChange = viewModel::onUrlChange,
                            label = { Text(stringResource(R.string.setup_server_address)) },
                            placeholder = { Text(stringResource(R.string.setup_server_placeholder)) },
                            singleLine = true,
                            enabled = !ui.busy,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(BatonSpacing.Medium))
                        Button(
                            onClick = viewModel::submitUrl,
                            enabled = !ui.busy && ui.url.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_continue))
                        }
                    }

                    SetupViewModel.Step.CREDENTIALS -> {
                        OutlinedTextField(
                            value = ui.username,
                            onValueChange = viewModel::onUsernameChange,
                            label = { Text(stringResource(R.string.setup_username)) },
                            singleLine = true,
                            enabled = !ui.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = ui.password,
                            onValueChange = viewModel::onPasswordChange,
                            label = { Text(stringResource(R.string.setup_password)) },
                            singleLine = true,
                            enabled = !ui.busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Go,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(BatonSpacing.Medium))
                        Button(
                            onClick = { viewModel.submitLogin(onConnected) },
                            enabled = !ui.busy && ui.username.isNotBlank() && ui.password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.setup_sign_in))
                        }
                        TextButton(onClick = viewModel::back, enabled = !ui.busy) {
                            Text(stringResource(R.string.setup_different_server))
                        }
                    }
                }

                Spacer(Modifier.height(BatonSpacing.Medium))
                if (ui.busy) {
                    CircularProgressIndicator()
                }
                ui.error?.let { error ->
                    Spacer(Modifier.height(BatonSpacing.Small))
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
