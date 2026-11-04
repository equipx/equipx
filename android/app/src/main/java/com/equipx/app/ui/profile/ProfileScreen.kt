package com.equipx.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipx.app.LocalAppContainer
import com.equipx.app.domain.Role
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ListRow
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.SecondaryButton
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onOpenCredits: () -> Unit, onLoggedOut: () -> Unit) {
    val c = LocalAppContainer.current
    val user by c.authRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val points by c.progressRepository.points.collectAsStateWithLifecycle(initialValue = 0)
    val viewed by c.progressRepository.viewedParts.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        EquipXTopBar(title = "Profile")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(user?.name ?: "Guest", style = MaterialTheme.typography.titleLarge)
                user?.let { Pill(if (it.role == Role.TEACHER) "Teacher" else "Student") }
            }
            Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
            ListRow(title = "Points", meta = "$points", icon = Icons.Outlined.Star)
            ListRow(title = "Parts viewed", meta = "${viewed.size}", icon = Icons.Outlined.Visibility)
            ListRow(title = "Credits & licenses", meta = "3D models and open-source software", icon = Icons.Outlined.Info, onClick = onOpenCredits)
            SecondaryButton(
                text = "Log out",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        c.authRepository.logout()
                        onLoggedOut()
                    }
                },
            )
        }
    }
}
