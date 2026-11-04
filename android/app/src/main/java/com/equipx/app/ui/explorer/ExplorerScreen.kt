package com.equipx.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import com.equipx.app.LocalAppContainer
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.vector.ImageVector
import com.equipx.app.ui.components.EquipXCard
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipx.app.domain.Part
import com.equipx.app.explorer.rememberWebViewExplorerController
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.ListRow
import com.equipx.app.ui.components.NumberBadge
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.components.SecondaryButton
import com.equipx.app.ui.theme.EquipXColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    deviceId: String,
    initialPartId: String?,
    onBack: () -> Unit,
    onOpenPart: (deviceId: String, partId: String) -> Unit,
) {
    val vm = appViewModel(key = "explorer-$deviceId-$initialPartId") {
        ExplorerViewModel(deviceId, initialPartId, it.contentRepository, it.activityTracker)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val controller = rememberWebViewExplorerController()
    var modelLoaded by remember { mutableStateOf(false) }
    val device = state.device
    val parts = device?.parts.orEmpty().sortedBy { it.number }
    val prefs = LocalAppContainer.current.uiPrefsRepository
    // Starts as "dismissed" so the hint never flashes before the stored flag is read.
    val coachDismissed by prefs.explorerCoachDismissed.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        EquipXTopBar(title = device?.name ?: "3D Explorer", onBack = onBack)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ExplorerPane(
                device = device,
                controller = controller,
                selectedPartId = state.selectedPartId,
                selectionToken = state.selectionToken,
                onHotspotTap = vm::selectPart,
                onLoadedChange = { modelLoaded = it },
                modifier = Modifier.fillMaxSize(),
            )
            ExplorerOverlay(
                parts = parts,
                selectedPartId = state.selectedPartId,
                modelLoaded = modelLoaded,
                showCoach = !coachDismissed && modelLoaded && parts.isNotEmpty(),
                onReset = {
                    vm.clearSelection()
                    controller.resetCamera()
                },
                onOpenParts = vm::openPartsList,
                onPick = vm::selectPart,
                onDismissCoach = { scope.launch { prefs.dismissExplorerCoach() } },
            )
        }
    }

    if (state.partsListOpen) {
        ModalBottomSheet(
            onDismissRequest = vm::closePartsList,
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color.White,
        ) {
            PartsListContent(parts = parts, selectedPartId = state.selectedPartId, onPick = vm::selectPart)
        }
    }

    val sheetPartId = state.selectedPartId
    if (state.sheetOpen && sheetPartId != null) {
        ModalBottomSheet(
            onDismissRequest = vm::dismissSheet,
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color.White,
        ) {
            PartSheetContent(
                partId = sheetPartId,
                part = state.selectedPart,
                onFullDetails = { vm.dismissSheet(); onOpenPart(deviceId, sheetPartId) },
            )
        }
    }
}

/** Floating controls over the 3D view: reset/parts buttons (top right), coach mark and numbered part chips (bottom). */
@Composable
fun BoxScope.ExplorerOverlay(
    parts: List<Part>,
    selectedPartId: String?,
    modelLoaded: Boolean,
    showCoach: Boolean,
    onReset: () -> Unit,
    onOpenParts: () -> Unit,
    onPick: (String) -> Unit,
    onDismissCoach: () -> Unit,
) {
    Column(
        Modifier.align(Alignment.TopEnd).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (modelLoaded) FloatingControl(Icons.Outlined.CenterFocusStrong, "Reset view", onReset)
        if (parts.isNotEmpty()) FloatingControl(Icons.Outlined.FormatListNumbered, "Parts list", onOpenParts)
    }
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = showCoach,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            CoachMark(onDismissCoach, Modifier.padding(horizontal = 12.dp))
        }
        if (parts.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(parts, key = { it.id }) { p -> PartChip(p, selected = p.id == selectedPartId, onClick = { onPick(p.id) }) }
            }
        }
    }
}

@Composable
private fun FloatingControl(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = EquipXColors.Text)
        }
    }
}

@Composable
private fun PartChip(part: Part, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) EquipXColors.Accent else Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(if (selected) Color.White else EquipXColors.AccentTint),
                contentAlignment = Alignment.Center,
            ) { Text(part.number.toString(), style = MaterialTheme.typography.labelLarge, color = EquipXColors.Accent) }
            Text(
                part.name.ifBlank { part.id },
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else EquipXColors.Text,
                maxLines = 1,
            )
        }
    }
}

/** One-time hint; dismissal is remembered in DataStore. */
@Composable
private fun CoachMark(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    EquipXCard(modifier.fillMaxWidth(), elevation = 8.dp) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = EquipXColors.Accent)
            Text("Tap the numbers to learn each part", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "Got it",
                style = MaterialTheme.typography.labelLarge,
                color = EquipXColors.Accent,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDismiss).heightIn(min = 48.dp).padding(horizontal = 12.dp).wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun PartsListContent(parts: List<Part>, selectedPartId: String?, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text(
            "Parts",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(parts, key = { it.id }) { p ->
                ListRow(
                    title = p.name.ifBlank { p.id },
                    meta = p.function.ifBlank { null },
                    leading = { NumberBadge(p.number, highlighted = p.id == selectedPartId) },
                    onClick = { onPick(p.id) },
                )
            }
        }
    }
}

@Composable
internal fun PartSheetContent(partId: String, part: Part?, onFullDetails: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (part != null && part.number > 0) Pill("Part ${part.number}", background = EquipXColors.AccentTint, contentColor = EquipXColors.Accent)
        Text(part?.name?.ifBlank { null } ?: partId.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
        SheetSection("Function", part?.function)
        SheetSection("How to use", part?.usage)
        val safety = part?.safety.orEmpty()
        Callout(
            title = "Safety",
            text = if (safety.isEmpty()) "Follow your lab's safety rules and ask your instructor before using this part." else safety.joinToString("\n") { "• $it" },
        )
        if (part != null) PrimaryButton("Full details", onClick = onFullDetails, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SheetSection(title: String, body: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = EquipXColors.Accent)
        Text(body?.ifBlank { null } ?: "Details for this part are coming soon.", style = MaterialTheme.typography.bodyMedium)
    }
}
