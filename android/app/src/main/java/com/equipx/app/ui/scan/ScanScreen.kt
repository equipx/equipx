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

    Column(Modifier.fillMaxSize()) {
        EquipXTopBar(title = "Scan")
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
            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(32.dp), tint = EquipXColors.TextSecondary)
            Text("Camera access needed", style = MaterialTheme.typography.titleMedium)
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
    val vm = appViewModel { ScanViewModel(it.createDetector(), it.contentRepository) }
    val detections by vm.detections.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.reset() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(onFrame = vm::onFrame, modifier = Modifier.fillMaxSize())
        DetectionOverlay(
            detections = detections,
            nameFor = { names[it.deviceId] ?: it.label },
            onChipTap = { onOpenExplorer(it.deviceId) },
        )
        if (detections.isEmpty()) {
            Text(
                "Point the camera at a lab device",
                style = MaterialTheme.typography.labelLarge,
                color = EquipXColors.Text,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .border(1.dp, EquipXColors.Border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
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

@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    nameFor: (Detection) -> String,
    onChipTap: (Detection) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            detections.forEach { d ->
                drawRect(
                    color = EquipXColors.Accent,
                    topLeft = Offset(d.box.left * w, d.box.top * h),
                    size = Size((d.box.right - d.box.left) * w, (d.box.bottom - d.box.top) * h),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        detections.forEach { d ->
            Row(
                modifier = Modifier
                    .offset { IntOffset((d.box.left * w).roundToInt() + 8.dp.roundToPx(), (d.box.top * h).roundToInt() + 8.dp.roundToPx()) }
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .border(1.dp, EquipXColors.Border, RoundedCornerShape(6.dp))
                    .clickable { onChipTap(d) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(nameFor(d), style = MaterialTheme.typography.labelLarge, color = EquipXColors.Text)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Open 3D explorer", modifier = Modifier.size(16.dp), tint = EquipXColors.Accent)
            }
        }
    }
}
