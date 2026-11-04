package com.equipx.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.equipx.app.LocalAppContainer
import com.equipx.app.domain.Device
import com.equipx.app.explorer.ASSET_BASE_URL
import com.equipx.app.explorer.ModelExplorerListener
import com.equipx.app.explorer.WebViewExplorerController
import com.equipx.app.explorer.WebViewModelExplorer
import com.equipx.app.ui.components.EmptyState
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.theme.EquipXColors

private enum class PaneStatus { Checking, Loading, Loaded, Error, NoModel }

/**
 * The 3D viewer with loading, error (retry) and "no model" states, reusable on any screen.
 *
 * The caller MUST give it a bounded, non-zero size through [modifier] (`fillMaxSize()` inside a sized parent,
 * `fillMaxWidth().height(..)` or `weight(1f)`), never wrap-content: the underlying WebView collapses to 0px otherwise.
 */
@Composable
fun ExplorerPane(
    device: Device?,
    controller: WebViewExplorerController,
    selectedPartId: String?,
    selectionToken: Int,
    onHotspotTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLoadedChange: (Boolean) -> Unit = {},
) {
    val content = LocalAppContainer.current.contentRepository
    var status by remember { mutableStateOf(PaneStatus.Checking) }
    var errorMessage by remember { mutableStateOf("") }
    var retryToken by remember { mutableIntStateOf(0) }
    val currentOnTap by rememberUpdatedState(onHotspotTap)
    val currentOnLoadedChange by rememberUpdatedState(onLoadedChange)

    val listener = remember {
        object : ModelExplorerListener {
            override fun onHotspotTap(partId: String) = currentOnTap(partId)
            override fun onModelLoaded() { status = PaneStatus.Loaded }
            override fun onModelError(message: String) { errorMessage = message; status = PaneStatus.Error }
        }
    }

    LaunchedEffect(device?.id, retryToken) {
        val d = device ?: return@LaunchedEffect
        status = PaneStatus.Checking
        val asset = d.model.asset ?: "models/${d.id}.glb"
        val hotspots = d.model.hotspots ?: "models/${d.id}.hotspots.json"
        if (!content.assetExists(asset)) {
            status = PaneStatus.NoModel
            return@LaunchedEffect
        }
        status = PaneStatus.Loading
        controller.loadModel(ASSET_BASE_URL + asset, ASSET_BASE_URL + hotspots, d.model.cameraOrbit)
    }

    LaunchedEffect(status) { currentOnLoadedChange(status == PaneStatus.Loaded) }

    // Re-applied when the model finishes loading so a selection survives (re)loads; the token re-focuses a re-selected part.
    LaunchedEffect(selectedPartId, selectionToken, status == PaneStatus.Loaded) {
        controller.selectPart(selectedPartId)
        selectedPartId?.let { controller.focusPart(it) }
    }

    Box(modifier.background(EquipXColors.Surface)) {
        WebViewModelExplorer(controller = controller, listener = listener, modifier = Modifier.fillMaxSize())

        when (status) {
            PaneStatus.Checking, PaneStatus.Loading -> Box(
                Modifier.fillMaxSize().background(EquipXColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = EquipXColors.Accent, strokeWidth = 2.dp)
                    Text("Loading 3D model", style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary)
                }
            }
            PaneStatus.Error -> ErrorState(
                message = errorMessage,
                title = "Could not load the 3D model",
                onRetry = { retryToken++ },
                modifier = Modifier.background(EquipXColors.Surface),
            )
            PaneStatus.NoModel -> EmptyState(
                title = "No 3D model for this device yet",
                message = "The parts and procedures are still available from the Learn tab.",
                modifier = Modifier.background(EquipXColors.Surface),
            )
            PaneStatus.Loaded -> Unit
        }
    }
}
