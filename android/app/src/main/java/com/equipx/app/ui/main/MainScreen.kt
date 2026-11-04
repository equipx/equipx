package com.equipx.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.home.HomeScreen
import com.equipx.app.ui.learn.LearnScreen
import com.equipx.app.ui.missions.MissionsScreen
import com.equipx.app.ui.profile.ProfileScreen
import com.equipx.app.ui.scan.ScanScreen
import com.equipx.app.ui.theme.EquipXColors

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Scan("Scan", Icons.Outlined.CameraAlt),
    Learn("Learn", Icons.Outlined.MenuBook),
    Missions("Missions", Icons.Outlined.EmojiEvents),
    Profile("Profile", Icons.Outlined.Person),
}

@Composable
fun MainScreen(
    onOpenExplorer: (String) -> Unit,
    onOpenDevice: (String) -> Unit,
    onOpenProcedure: (String, String) -> Unit,
    onOpenQuiz: (String, String) -> Unit,
    onOpenCredits: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(Tab.Home.ordinal) }
    val tab = Tab.entries[selected]
    Scaffold(
        containerColor = EquipXColors.Canvas,
        // Each tab's own TopAppBar handles the status-bar inset; NavigationBar handles the bottom one.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                HairlineDivider()
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.shadow(8.dp)) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == tab,
                            onClick = { selected = t.ordinal },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EquipXColors.Accent,
                                selectedTextColor = EquipXColors.Accent,
                                unselectedIconColor = EquipXColors.TextSecondary,
                                unselectedTextColor = EquipXColors.TextSecondary,
                                indicatorColor = EquipXColors.AccentTint,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).background(EquipXColors.Canvas)) {
            when (tab) {
                Tab.Home -> HomeScreen(
                    onScan = { selected = Tab.Scan.ordinal },
                    onBrowse = { selected = Tab.Learn.ordinal },
                    onOpenDevice = onOpenDevice,
                    onOpenMissions = { selected = Tab.Missions.ordinal },
                )
                Tab.Scan -> ScanScreen(onOpenExplorer = onOpenExplorer)
                Tab.Learn -> LearnScreen(onOpenDevice = onOpenDevice)
                Tab.Missions -> MissionsScreen(
                    onGoToScan = { selected = Tab.Scan.ordinal },
                    onOpenExplorer = onOpenExplorer,
                    onOpenProcedure = onOpenProcedure,
                    onOpenQuiz = onOpenQuiz,
                )
                Tab.Profile -> ProfileScreen(onOpenCredits = onOpenCredits, onLoggedOut = onLoggedOut)
            }
        }
    }
}
