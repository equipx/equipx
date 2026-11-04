package com.equipx.app.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import com.equipx.app.domain.DeviceSummary
import com.equipx.app.ui.components.DeviceIconTile
import com.equipx.app.ui.components.EquipXCard
import com.equipx.app.ui.components.ListRow
import com.equipx.app.ui.components.TabTitle
import com.equipx.app.ui.components.rememberReducedMotion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipx.app.ml.Detection
import com.equipx.app.ml.Frame
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.components.SecondaryButton
import com.equipx.app.ui.theme.EquipXColors
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ScanScreen(onOpenExplorer: (String) -> Unit) {
    val context = LocalContext.current
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPermission()) }
    var requestedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        requestedOnce = true
    }
    // Re-check after returning from system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasPermission() }

    Column(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        TabTitle("Scan", subtitle = "Identify lab equipment with the camera")
        if (granted) {
            CameraContent(onOpenExplorer)
        } else {
            val activity = context.findActivity()
            val permanentlyDenied = requestedOnce && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            PermissionPrompt(
                denied = requestedOnce,
                permanentlyDenied = permanentlyDenied,
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
            )
        }
    }
}

@Composable
private fun PermissionPrompt(
    denied: Boolean,
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(EquipXColors.AccentTint), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(36.dp), tint = EquipXColors.Accent)
            }
            Text("Camera access needed", style = MaterialTheme.typography.titleLarge)
            Text(
                when {
                    permanentlyDenied -> "Camera permission is turned off. Enable it in Settings to scan lab equipment."
                    denied -> "EquipX needs the camera to recognize lab equipment. Please allow access."
                    else -> "EquipX uses the camera to recognize the lab device in front of you. Nothing is recorded or uploaded."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = EquipXColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (permanentlyDenied) {
                PrimaryButton("Open settings", onClick = onOpenSettings)
            } else {
                PrimaryButton(if (denied) "Try again" else "Allow camera", onClick = onRequest)
            }
        }
    }
}

@Composable
private fun CameraContent(onOpenExplorer: (String) -> Unit) {
    val vm = appViewModel { ScanViewModel(it.createDetector(), it.contentRepository, it.activityTracker) }
    val detections by vm.detections.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.reset() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(onFrame = vm::onFrame, modifier = Modifier.fillMaxSize())
        ScanOverlay(
            detections = detections,
            devices = devices,
            onOpenDetected = { vm.onChipTapped(it.deviceId); onOpenExplorer(it.deviceId) },
            onChooseManually = { vm.onManualChoice(it); onOpenExplorer(it) },
        )
    }
}

@Composable
private fun CameraPreview(onFrame: (Frame) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentOnFrame by androidx.compose.runtime.rememberUpdatedState(onFrame)

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { a ->
                    a.setAnalyzer(analysisExecutor) { image ->
                        try {
                            currentOnFrame(
                                Frame(
                                    width = image.width,
                                    height = image.height,
                                    rotationDegrees = image.imageInfo.rotationDegrees,
                                    timestampNanos = image.imageInfo.timestamp,
                                )
                            )
                        } finally {
                            image.close()
                        }
                    }
                }
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
                // No back camera / camera busy: preview stays black; user can leave and return.
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Stateless scan overlay drawn over the camera: animated corner-bracket viewfinder, guidance text, the detected-device
 * card and the manual fallback sheet. Previewable without a camera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOverlay(
    detections: List<Detection>,
    devices: List<DeviceSummary>,
    onOpenDetected: (Detection) -> Unit,
    onChooseManually: (String) -> Unit,
    initialSheetOpen: Boolean = false,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(initialSheetOpen) }
    val detected = detections.firstOrNull()
    val summary = detected?.let { d -> devices.firstOrNull { it.id == d.deviceId } }

    Box(Modifier.fillMaxSize()) {
        Viewfinder(detected)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedContent(targetState = detected != null, label = "scan-status") { has ->
                if (has && detected != null) {
                    DetectedCard(
                        name = summary?.name ?: detected.label,
                        deviceId = detected.deviceId,
                        category = summary?.category.orEmpty(),
                        onOpen = { onOpenDetected(detected) },
                    )
                } else {
                    Text(
                        "Point the camera at a device",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.92f))
                    .clickable { sheetOpen = true }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.ViewList, contentDescription = null, tint = EquipXColors.Accent, modifier = Modifier.size(20.dp))
                Text("Choose device manually", style = MaterialTheme.typography.labelLarge, color = EquipXColors.Text)
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
        ) {
            DevicePickerContent(devices, onPick = { sheetOpen = false; onChooseManually(it) })
        }
    }
}

/** Manual fallback list. Opening a device here does not count as a scan (no points, no scan mission task). */
@Composable
fun DevicePickerContent(devices: List<DeviceSummary>, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text("Choose a device", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Opens the 3D explorer. Only a real scan earns scan points.",
            style = MaterialTheme.typography.bodySmall,
            color = EquipXColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            items(devices, key = { it.id }) { d ->
                ListRow(
                    title = d.name,
                    meta = d.category.ifBlank { null },
                    leading = { DeviceIconTile(d.id, d.category, size = 44.dp) },
                    onClick = { onPick(d.id) },
                    trailing = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = EquipXColors.TextSecondary, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun DetectedCard(name: String, deviceId: String, category: String, onOpen: () -> Unit) {
    EquipXCard(Modifier.fillMaxWidth(), elevation = 8.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DeviceIconTile(deviceId, category, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text("DEVICE DETECTED", style = MaterialTheme.typography.labelSmall, color = EquipXColors.Success)
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
            PrimaryButton("Open 3D", onClick = onOpen, icon = Icons.Outlined.ViewInAr)
        }
    }
}

/** Corner-bracket viewfinder: gently pulses while searching, snaps green around the detection once found. */
@Composable
private fun Viewfinder(detected: Detection?) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "pulse")
    val animatedPulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse-scale",
    )
    val pulse = if (reduced) 1f else animatedPulse
    val box = detected?.box
    val spec = tween<Float>(350)
    val l by animateFloatAsState(box?.left ?: 0.15f, spec, label = "l")
    val t by animateFloatAsState(box?.top ?: 0.22f, spec, label = "t")
    val r by animateFloatAsState(box?.right ?: 0.85f, spec, label = "r")
    val b by animateFloatAsState(box?.bottom ?: 0.68f, spec, label = "b")
    val color by animateColorAsState(if (detected != null) Color(0xFF3DDC97) else Color.White, label = "bracket")
    Canvas(Modifier.fillMaxSize()) {
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val hw = (r - l) / 2f * (if (detected != null) 1f else pulse)
        val hh = (b - t) / 2f * (if (detected != null) 1f else pulse)
        val left = (cx - hw) * size.width
        val right = (cx + hw) * size.width
        val top = (cy - hh) * size.height
        val bottom = (cy + hh) * size.height
        val arm = 36.dp.toPx()
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply { moveTo(x + dx * arm, y); lineTo(x, y); lineTo(x, y + dy * arm) }
            drawPath(p, color, style = stroke)
        }
        corner(left, top, 1f, 1f)
        corner(right, top, -1f, 1f)
        corner(left, bottom, 1f, -1f)
        corner(right, bottom, -1f, -1f)
    }
}
