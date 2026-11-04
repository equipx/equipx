package com.equipx.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentParserTest {

    private val indexJson = """
        { "devices": [ { "id": "digital_multimeter", "name": "Digital Multimeter", "category": "Measurement", "summary": "One line", "futureField": 1 } ] }
    """.trimIndent()

    private val deviceJson = """
        {
          "id": "digital_multimeter",
          "name": "Digital Multimeter",
          "category": "Measurement",
          "summary": "s",
          "overview": "o",
          "model": { "asset": "models/digital_multimeter.glb", "hotspots": "models/digital_multimeter.hotspots.json", "cameraOrbit": "0deg 75deg 0.45m", "credit": null },
          "layout": { "units": "m",
            "body": { "width": 0.09, "height": 0.18, "depth": 0.045, "cornerRadius": 0.008, "color": "#D9D7D2" },
            "parts": [ { "id": "lcd_display", "type": "screen", "face": "front", "position": [0, 0.05], "size": [0.07, 0.04, 0.003], "color": "#9FB3A5" } ] },
          "parts": [ { "id": "lcd_display", "number": 1, "name": "LCD Display", "function": "f", "usage": "u", "safety": ["Tip 1"] } ],
          "safetyRules": ["Rule 1"],
          "procedures": [ { "id": "p", "title": "T", "steps": [ { "text": "Step", "partId": "lcd_display" } ] } ],
          "faults": [ { "id": "f1", "symptom": "blank", "options": [ { "id": "a", "text": "A" } ], "correctOptionId": "a", "explanation": "e", "relatedPartIds": ["lcd_display"] } ],
          "quizzes": [ { "id": "q", "title": "Q", "timeLimitSec": null, "questions": [ { "id": "q1", "prompt": "?", "options": ["A","B"], "correctIndex": 1, "explanation": "x" } ] } ],
          "unknownTopLevel": true
        }
    """.trimIndent()

    @Test
    fun parsesIndexIgnoringUnknownKeys() {
        val index = ContentParser.parseIndex(indexJson)
        assertEquals(1, index.devices.size)
        assertEquals("Digital Multimeter", index.devices[0].name)
    }

    @Test
    fun parsesDevice() {
        val d = ContentParser.parseDevice(deviceJson)
        assertEquals("digital_multimeter", d.id)
        assertEquals("0deg 75deg 0.45m", d.model.cameraOrbit)
        assertNull(d.model.credit)
        assertEquals(0.05, d.layout.parts[0].position[1], 1e-9)
        assertEquals("LCD Display", d.parts[0].name)
        assertEquals("lcd_display", d.procedures[0].steps[0].partId)
        assertEquals(1, d.quizzes[0].questions[0].correctIndex)
        assertNull(d.quizzes[0].timeLimitSec)
    }

    @Test
    fun parsesMinimalDeviceWithDefaults() {
        val d = ContentParser.parseDevice("""{"id":"x","name":"X"}""")
        assertEquals(0, d.parts.size)
        assertNull(d.model.asset)
    }

    @Test
    fun parsesHotspots() {
        val h = ContentParser.parseHotspots(
            """{"deviceId":"d","hotspots":[{"partId":"a","number":1,"position":[0,0.05,0.0255],"normal":[0,0,1]}]}"""
        )
        assertEquals("a", h.hotspots[0].partId)
        assertEquals(3, h.hotspots[0].position.size)
    }
}
