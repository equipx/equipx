package com.equipx.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses every bundled content file from the filesystem and checks cross references. */
class BundledContentTest {
    private val dir = File("src/main/assets/content")

    private fun devices() = ContentParser.parseIndex(File(dir, "index.json").readText()).devices.map {
        ContentParser.parseDevice(File(dir, "${it.id}.json").readText())
    }

    @Test
    fun indexListsTenDevices() {
        assertEquals(10, ContentParser.parseIndex(File(dir, "index.json").readText()).devices.size)
    }

    @Test
    fun quizCorrectIndexInRange() {
        devices().forEach { d ->
            d.quizzes.forEach { q ->
                q.questions.forEach { qq ->
                    assertTrue("${d.id}/${q.id}/${qq.id} correctIndex ${qq.correctIndex}", qq.correctIndex in qq.options.indices)
                }
            }
        }
    }

    @Test
    fun procedureStepPartsExist() {
        devices().forEach { d ->
            val ids = d.parts.map { it.id }.toSet()
            d.procedures.forEach { p ->
                p.steps.forEach { assertTrue("${d.id}/${p.id}: ${it.partId}", it.partId in ids) }
            }
        }
    }

    @Test
    fun faultCorrectOptionExists() {
        devices().forEach { d ->
            d.faults.forEach { f ->
                assertTrue("${d.id}/${f.id}: ${f.correctOptionId}", f.options.any { it.id == f.correctOptionId })
            }
        }
    }
}
