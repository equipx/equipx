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
import com.equipx.app.data.AuthRepository
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(val busy: Boolean = false, val error: String? = null, val done: Boolean = false)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
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
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val vm = appViewModel { LoginViewModel(it.authRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val onDone by rememberUpdatedState(onLoggedIn)
    LaunchedEffect(state.done) { if (state.done) onDone() }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
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
        Text("Sign in to continue", style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
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
        PrimaryButton(
            text = if (state.busy) "Signing in..." else "Sign in",
            onClick = { vm.login(email, password) },
            enabled = !state.busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Callout(
            title = "Demo accounts",
            text = "Student: student@equipx.app / student123\nTeacher: teacher@equipx.app / teacher123",
        )
    }
}
