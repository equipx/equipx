package com.equipx.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipx.app.data.AuthMode
import com.equipx.app.data.AuthRepository
import com.equipx.app.data.remote.AuthException
import androidx.compose.material3.TextButton
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    /** Non-error hint, e.g. "check your email to confirm". */
    val info: String? = null,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    val mode: AuthMode = auth.mode

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(email: String, password: String) {
        if (_state.value.busy) return
        _state.update { LoginUiState(busy = true) }
        viewModelScope.launch {
            auth.login(email, password)
                .onSuccess { _state.update { LoginUiState(done = true) } }
                .onFailure { e -> _state.update { LoginUiState(error = e.message ?: "Login failed") } }
        }
    }

    fun register(email: String, password: String, name: String) {
        if (_state.value.busy) return
        _state.update { LoginUiState(busy = true) }
        viewModelScope.launch {
            auth.register(email, password, name)
                .onSuccess { _state.update { LoginUiState(done = true) } }
                .onFailure { e ->
                    val confirm = (e as? AuthException)?.kind == AuthException.Kind.CONFIRM_EMAIL
                    _state.update {
                        if (confirm) LoginUiState(info = e.message) else LoginUiState(error = e.message ?: "Sign-up failed")
                    }
                }
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val vm = appViewModel { LoginViewModel(it.authRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val onDone by rememberUpdatedState(onLoggedIn)
    LaunchedEffect(state.done) { if (state.done) onDone() }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    val remote = vm.mode == AuthMode.SUPABASE
    val signUp = remote && creating
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = EquipXColors.Accent,
        unfocusedBorderColor = EquipXColors.Border,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(EquipXColors.Canvas)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Welcome to EquipX", style = MaterialTheme.typography.headlineMedium)
        Text(if (signUp) "Create a student account" else "Sign in to continue", style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        if (signUp) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
        )
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = EquipXColors.Danger)
        }
        state.info?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary)
        }
        PrimaryButton(
            text = when {
                state.busy && signUp -> "Creating account..."
                state.busy -> "Signing in..."
                signUp -> "Create account"
                else -> "Sign in"
            },
            onClick = { if (signUp) vm.register(email, password, name) else vm.login(email, password) },
            enabled = !state.busy && email.isNotBlank() && password.isNotBlank() && (!signUp || (name.isNotBlank() && password.length >= 6)),
            modifier = Modifier.fillMaxWidth(),
        )
        if (remote) {
            TextButton(onClick = { creating = !creating }, enabled = !state.busy) {
                Text(if (signUp) "Already have an account? Sign in" else "New student? Create account")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (remote) {
            Callout(
                title = "Online mode",
                text = "Connected to the EquipX server. Your progress is saved on this phone and synced when you are online.",
            )
        } else {
            Callout(
                title = "Demo mode (offline accounts)",
                text = "Student: student@equipx.app / student123\nTeacher: teacher@equipx.app / teacher123",
            )
        }
    }
}
