package com.equipx.app.ui.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.theme.EquipXColors

@Composable
fun MissionsScreen() {
    Column(Modifier.fillMaxSize()) {
        EquipXTopBar(title = "Missions")
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Missions are coming soon", style = MaterialTheme.typography.titleSmall)
                Text("Scan, explore and quiz your way through guided tasks.", style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary)
            }
        }
    }
}
