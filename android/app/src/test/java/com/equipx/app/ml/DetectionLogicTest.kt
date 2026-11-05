package com.equipx.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionLogicTest {
    private val classes = List(10) { "dev$it" }

    private fun info(channelsFirst: Boolean, normalized: Boolean, anchors: Int = 6) = ModelInfo(classes, 640, channelsFirst, anchors, normalized)

    /** Builds a flat output with the given anchors (cx,cy,w,h in model units, class, score). */
    private fun output(info: ModelInfo, vararg a: List<Number>): FloatArray {
        val out = FloatArray(info.channels * info.numAnchors)
        a.forEachIndexed { i, v ->
            val vals = FloatArray(info.channels)
            for (k in 0..3) vals[k] = v[k].toFloat()
            vals[4 + v[4].toInt()] = v[5].toFloat()
            for (c in 0 until info.channels) {
                out[if (info.channelsFirst) c * info.numAnchors + i else i * info.channels + c] = vals[c]
            }
        }
        return out
    }

    @Test fun decodesAllFourConventions() {
        for (cf in listOf(true, false)) for (norm in listOf(true, false)) {
            val i = info(cf, norm)
            val k = if (norm) 1f else 640f
            val out = output(i, listOf(0.5f * k, 0.5f * k, 0.2f * k, 0.4f * k, 3, 0.9f))
            val r = YoloDecoder.decode(out, i)
            assertEquals("cf=$cf norm=$norm", 1, r.size)
            assertEquals(3, r[0].classIndex)
            assertEquals(0.4f, r[0].box.left, 1e-4f)
            assertEquals(0.3f, r[0].box.top, 1e-4f)
            assertEquals(0.6f, r[0].box.right, 1e-4f)
            assertEquals(0.7f, r[0].box.bottom, 1e-4f)
        }
    }

    @Test fun confidenceThresholdFilters() {
        val i = info(true, true)
        val out = output(i, listOf(0.5, 0.5, 0.2, 0.2, 1, 0.49f), listOf(0.2, 0.2, 0.1, 0.1, 2, 0.5f))
        val r = YoloDecoder.decode(out, i)
        assertEquals(listOf(2), r.map { it.classIndex })
    }

    @Test fun nmsIsClassWise() {
        val i = info(false, true)
        val out = output(
            i,
            listOf(0.5, 0.5, 0.4, 0.4, 0, 0.9f),
            listOf(0.51, 0.5, 0.4, 0.4, 0, 0.8f), // same class, overlaps: suppressed
            listOf(0.5, 0.5, 0.4, 0.4, 1, 0.7f),  // other class, same place: kept
            listOf(0.9, 0.9, 0.1, 0.1, 0, 0.6f),  // same class, disjoint: kept
        )
        val r = YoloDecoder.decode(out, i)
        assertEquals(3, r.size)
        assertEquals(listOf(0, 1, 0), r.map { it.classIndex })
        assertEquals(0.9f, r[0].score, 1e-6f)
    }

    @Test fun iouBasics() {
        val a = NormalizedBox(0f, 0f, 0.5f, 0.5f)
        assertEquals(1f, YoloDecoder.iou(a, a), 1e-6f)
        assertEquals(0f, YoloDecoder.iou(a, NormalizedBox(0.6f, 0.6f, 1f, 1f)), 1e-6f)
        assertEquals(1f / 3f, YoloDecoder.iou(a, NormalizedBox(0.25f, 0f, 0.75f, 0.5f)), 1e-4f)
    }

    @Test fun letterboxUnmapsWidePortraitAndLandscape() {
        // Landscape 640x480 into 640: scale 1, pad Y 80.
        val lb = Letterbox(640, 480, 640)
        val b = lb.unmap(NormalizedBox(0f, 80f / 640, 1f, 560f / 640))
        assertEquals(0f, b.top, 1e-4f); assertEquals(1f, b.bottom, 1e-4f)
        assertEquals(0f, b.left, 1e-4f); assertEquals(1f, b.right, 1e-4f)
        // Portrait 480x640: pad X 80; a box in the padding clamps to 0.
        val p = Letterbox(480, 640, 640).unmap(NormalizedBox(0.05f, 0.25f, 0.5f, 0.75f))
        assertEquals(0f, p.left, 1e-4f)
        assertEquals(0.5f * 640 - 80f, p.right * 480, 1e-2f)
    }

    @Test fun modelInfoParsesNotebookJson() {
        fun json(shape: String, units: String) = """{"classes":["a","b"],"input":{"shape":[1,640,640,3],"layout":"NHWC","dtype":"float32"},
            "output":{"shape":$shape,"box_units":"$units"}}"""
        val cf = ModelInfo.parse(json("[1,6,8400]", "normalized 0-1 of input size"))
        assertTrue(cf.channelsFirst); assertTrue(cf.boxesNormalized); assertEquals(8400, cf.numAnchors); assertEquals(640, cf.inputSize)
        val af = ModelInfo.parse(json("[1,8400,6]", "pixels of input size (640)"))
        assertFalse(af.channelsFirst); assertFalse(af.boxesNormalized); assertEquals(8400, af.numAnchors)
    }

    @Test fun modelInfoParsesRealExportJson() {
        val m = ModelInfo.parse("""{"model": "equipx_detector.tflite", "task": "detect", "num_classes": 10,
 "classes": ["digital_multimeter","analog_multimeter","digital_clamp_meter","digital_oscilloscope","function_generator","dc_power_supply","soldering_station","lcr_meter","insulation_tester","breadboard_trainer"],
 "input": {"shape": [1, 3, 640, 640], "layout": "NCHW", "dtype": "float32", "normalization": "pixel/255.0 -> [0,1]; letterbox to 640; RGB"},
 "output": {"shape": [1, 14, 8400], "dtype": "float32", "layout": "channels_first", "box_format": "cx,cy,w,h", "box_units": "normalized", "nms": "not applied"}}""")
        assertEquals(10, m.numClasses); assertEquals(640, m.inputSize); assertEquals(8400, m.numAnchors)
        assertTrue(m.inputChannelsFirst); assertTrue(m.channelsFirst); assertTrue(m.boxesNormalized)
    }

    @Test fun modelInfoInfersLayoutsFromShapes() {
        val nchw = ModelInfo.parse("""{"classes":["a"],"input":{"shape":[1,3,320,320]},"output":{"shape":[1,5,100]}}""")
        assertTrue(nchw.inputChannelsFirst); assertEquals(320, nchw.inputSize)
        val nhwc = ModelInfo.parse("""{"classes":["a"],"input":{"shape":[1,320,320,3]},"output":{"shape":[1,100,5],"layout":"anchors_first"}}""")
        assertFalse(nhwc.inputChannelsFirst); assertFalse(nhwc.channelsFirst); assertEquals(100, nhwc.numAnchors)
    }

    @Test fun inputPackingNchwVsNhwc() {
        // 2x2 "bitmap": ARGB ints with distinct channel values.
        fun px(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        val pixels = intArrayOf(px(10, 20, 30), px(40, 50, 60), px(70, 80, 90), px(100, 110, 120))
        fun pack(cf: Boolean) = java.nio.FloatBuffer.allocate(12).also { InputPacking.pack(pixels, cf, it) }.array()
        val nchw = pack(true)
        assertEquals(listOf(10, 40, 70, 100, 20, 50, 80, 110, 30, 60, 90, 120).map { it / 255f }, nchw.toList())
        val nhwc = pack(false)
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120).map { it / 255f }, nhwc.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun modelInfoRejectsMismatchedShape() {
        ModelInfo.parse("""{"classes":["a"],"input":{"shape":[1,640,640,3]},"output":{"shape":[1,7,100]}}""")
    }

    private fun det(id: String, conf: Float = 0.8f) = Detection(id, id, conf, NormalizedBox(0.1f, 0.1f, 0.9f, 0.9f))

    @Test fun smootherNeedsThreeOfFive() {
        val s = TemporalSmoother()
        assertTrue(s.update(listOf(det("a")), 0).isEmpty())
        assertTrue(s.update(emptyList(), 100).isEmpty())
        assertTrue(s.update(listOf(det("a")), 200).isEmpty())
        assertEquals(listOf("a"), s.update(listOf(det("a")), 300).map { it.deviceId }) // 3 hits in 4 frames
    }

    @Test fun smootherIgnoresSingleFrameFlicker() {
        val s = TemporalSmoother()
        repeat(4) { assertTrue(s.update(emptyList(), it * 100L).isEmpty()) }
        assertTrue(s.update(listOf(det("a")), 500).isEmpty())
    }

    @Test fun smootherHidesAfterOneSecond() {
        val s = TemporalSmoother()
        for (t in 0..2) s.update(listOf(det("a")), t * 100L)
        assertEquals(1, s.update(emptyList(), 700).size)   // within hold
        assertEquals(1, s.current(1200).size)              // 1000 ms after last seen (200) is not yet exceeded
        assertTrue(s.update(emptyList(), 1300).isEmpty())  // > 1000 ms
    }

    @Test fun smootherResetClears() {
        val s = TemporalSmoother()
        for (t in 0..2) s.update(listOf(det("a")), t * 100L)
        s.reset()
        assertTrue(s.current(300).isEmpty())
    }
}
