package com.equipx.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses the real bundled missions.json and checks cross references against device content. */
class BundledMissionsTest {
    private val dir = File("src/main/assets/content")

    @Test fun bundledMissionsParseAndReferenceRealContent() {
        val file = File(dir, "missions.json")
        assertTrue("missing ${file.absolutePath}; run npm run sync", file.exists())
        val missions = ContentParser.parseMissions(file.readText())
        assertTrue(missions.isNotEmpty())
        val devices = ContentParser.parseIndex(File(dir, "index.json").readText()).devices
            .associate { it.id to ContentParser.parseDevice(File(dir, "${it.id}.json").readText()) }
        missions.forEach { m ->
            assertTrue("${m.id} has tasks", m.tasks.isNotEmpty())
            m.deviceId?.let { assertTrue("${m.id} device", it in devices) }
            assertEquals("${m.id} unique task ids", m.tasks.size, m.tasks.map { it.id }.toSet().size)
            m.tasks.forEach { t ->
                val d = devices[t.deviceId]
                assertTrue("${m.id}/${t.id} device ${t.deviceId}", d != null)
                when (t.type) {
                    "scan" -> Unit
                    "identify_part" -> assertTrue("${m.id}/${t.id} part", d!!.parts.any { it.id == t.partId })
                    "complete_procedure" -> assertTrue("${m.id}/${t.id} procedure", d!!.procedures.any { it.id == t.procedureId })
                    "pass_quiz" -> assertTrue("${m.id}/${t.id} quiz", d!!.quizzes.any { it.id == t.quizId })
                    else -> throw AssertionError("unknown task type ${t.type}")
                }
            }
        }
    }

    @Test fun missionDeviceIdOptionalAndMinScoreDefaults() {
        val ms = ContentParser.parseMissions(
            """{"missions":[{"id":"x","title":"X","tasks":[{"id":"t","type":"pass_quiz","deviceId":"d","quizId":"q"}]}]}""",
        )
        assertEquals(null, ms[0].deviceId)
        assertEquals(70, ms[0].tasks[0].minScorePct)
    }
}
