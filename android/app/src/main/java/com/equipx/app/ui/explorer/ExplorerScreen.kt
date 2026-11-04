package com.equipx.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

    Column(Modifier.fillMaxSize().background(Color.White)) {
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
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (parts.isNotEmpty()) SecondaryButton("Parts (${parts.size})", onClick = vm::openPartsList)
                if (modelLoaded) SecondaryButton("Reset view", onClick = {
                    vm.clearSelection()
                    controller.resetCamera()
                })
            }
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
            dragHandle = null,
        ) {
            PartSheetContent(
                partId = sheetPartId,
                part = state.selectedPart,
                onFullDetails = { vm.dismissSheet(); onOpenPart(deviceId, sheetPartId) },
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
            style = MaterialTheme.typography.titleMedium,
        )
        HairlineDivider()
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(parts, key = { it.id }) { p ->
                ListRow(
                    title = p.name.ifBlank { p.id },
                    meta = p.function.ifBlank { null },
                    leading = { NumberBadge(p.number, highlighted = p.id == selectedPartId) },
                    onClick = { onPick(p.id) },
                )
                HairlineDivider()
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
        if (part != null && part.number > 0) Pill("Part ${part.number}")
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
        Text(title, style = MaterialTheme.typography.labelMedium, color = EquipXColors.TextSecondary)
        Text(body?.ifBlank { null } ?: "Details for this part are coming soon.", style = MaterialTheme.typography.bodyMedium)
    }
}
