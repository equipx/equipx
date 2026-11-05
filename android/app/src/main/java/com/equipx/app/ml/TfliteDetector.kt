package com.equipx.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * YOLO TFLite detector for the 10 lab devices. Float32 RGB/255 input (letterboxed, NCHW planar or NHWC per [ModelInfo.inputChannelsFirst]), `[1,14,N]` or `[1,N,14]`
 * output, threshold + class-wise NMS here, then temporal smoothing. Called on the CameraX analysis executor.
 */
class TfliteDetector(
    context: Context,
    private val info: ModelInfo,
    /** Class index -> deviceId, in `labels.txt` order. */
    private val deviceIds: List<String> = info.classes,
    private val displayNames: Map<String, String> = emptyMap(),
    private val confidence: Float = YoloDecoder.DEFAULT_CONFIDENCE,
    private val minFrameIntervalMs: Long = 100L,
    modelAsset: String = DetectorFactory.MODEL_ASSET,
    useGpu: Boolean = true,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : Detector {

    private val lock = Any()
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val smoother = TemporalSmoother()
    private var lastRunMs = Long.MIN_VALUE
    private var closed = false

    private val size = info.inputSize
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(4 * size * size * 3).order(ByteOrder.nativeOrder())
    private val outputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(4 * info.channels * info.numAnchors).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(size * size)
    private val canvasBitmap: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        require(deviceIds.size == info.numClasses) { "labels (${deviceIds.size}) != model classes (${info.numClasses})" }
        val model = loadModel(context, modelAsset)
        interpreter = createInterpreter(model, useGpu)
    }

    private fun loadModel(context: Context, asset: String): MappedByteBuffer =
        context.assets.openFd(asset).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).use { it.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength) }
        }

    private fun createInterpreter(model: MappedByteBuffer, useGpu: Boolean): Interpreter {
        if (useGpu) {
            try {
                if (CompatibilityList().use { it.isDelegateSupportedOnThisDevice }) {
                    val delegate = GpuDelegate()
                    val gpu = Interpreter(model, Interpreter.Options().addDelegate(delegate))
                    if (runOnce(gpu)) {
                        gpuDelegate = delegate
                        Log.i(TAG, "TFLite using GPU delegate")
                        return gpu
                    }
                    gpu.close(); delegate.close()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "GPU delegate unavailable, using CPU", t)
            }
        }
        val cpu = Interpreter(model, Interpreter.Options().setNumThreads(4))
        require(runOnce(cpu)) { "model failed a warm-up inference" }
        Log.i(TAG, "TFLite using CPU")
        return cpu
    }

    private fun runOnce(i: Interpreter): Boolean = try {
        inputBuffer.rewind(); outputBuffer.rewind()
        i.run(inputBuffer, outputBuffer)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "warm-up inference failed", t)
        false
    }

    override fun detect(frame: Frame): List<Detection> = synchronized(lock) {
        val interp = interpreter
        if (closed || interp == null) return emptyList()
        val now = clockMs()
        if (lastRunMs != Long.MIN_VALUE && now - lastRunMs < minFrameIntervalMs) return toDetections(smoother.current(now))
        val bitmap = frame.bitmapProvider?.invoke() ?: return toDetections(smoother.current(now))
        lastRunMs = now
        return try {
            val upright = fillInput(bitmap, frame.rotationDegrees)
            inputBuffer.rewind(); outputBuffer.rewind()
            interp.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            val out = FloatArray(info.channels * info.numAnchors).also { outputBuffer.asFloatBuffer().get(it) }
            val raw = YoloDecoder.decode(out, info, confidence)
            val letterbox = Letterbox(upright.first, upright.second, size)
            val dets = raw.map { c ->
                val id = deviceIds[c.classIndex]
                Detection(displayNames[id] ?: id, id, c.score, letterbox.unmap(c.box))
            }
            smoother.update(dets, now)
        } catch (t: Throwable) {
            Log.w(TAG, "inference failed", t)
            smoother.current(now)
        }
    }

    private fun toDetections(l: List<Detection>) = l

    /** Letterboxes [src] (rotated upright by [rotation]) into the input buffer. Returns upright (w, h). */
    private fun fillInput(src: Bitmap, rotation: Int): Pair<Int, Int> {
        val swap = rotation == 90 || rotation == 270
        val uw = if (swap) src.height else src.width
        val uh = if (swap) src.width else src.height
        val s = minOf(size.toFloat() / uw, size.toFloat() / uh)
        val m = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postRotate(rotation.toFloat())
            postScale(s, s)
            postTranslate(size / 2f, size / 2f)
        }
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(src, m, paint)
        canvasBitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        inputBuffer.rewind()
        InputPacking.pack(pixels, info.inputChannelsFirst, inputBuffer.asFloatBuffer())
        return uw to uh
    }

    override fun reset() = synchronized(lock) { smoother.reset(); lastRunMs = Long.MIN_VALUE }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        interpreter?.close(); interpreter = null
        gpuDelegate?.close(); gpuDelegate = null
        canvasBitmap.recycle()
    }

    private companion object { const val TAG = "TfliteDetector" }
}
