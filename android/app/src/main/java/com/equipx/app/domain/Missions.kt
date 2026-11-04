package com.equipx.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class MissionTask(
    val id: String,
    /** `scan`, `identify_part`, `complete_procedure` or `pass_quiz`. */
    val type: String = "",
    val title: String = "",
    val deviceId: String = "",
    val partId: String? = null,
    val procedureId: String? = null,
    val quizId: String? = null,
    val minScorePct: Int = 70,
)

@Serializable
data class Mission(
    val id: String,
    val title: String = "",
    val description: String = "",
    /** Optional: cross-device missions omit it. */
    val deviceId: String? = null,
    val tasks: List<MissionTask> = emptyList(),
)

@Serializable
data class MissionsFile(val missions: List<Mission> = emptyList())

enum class TaskState { DONE, ACTIVE, LOCKED }

/** Persisted mission bookkeeping: which mission is active and the done task ids (in order) per mission. */
data class MissionState(
    val activeMissionId: String? = null,
    val doneTasks: Map<String, List<String>> = emptyMap(),
)

object MissionRules {
    const val TYPE_SCAN = "scan"
    const val TYPE_IDENTIFY = "identify_part"
    const val TYPE_PROCEDURE = "complete_procedure"
    const val TYPE_QUIZ = "pass_quiz"

    /** The first task not yet done, or null when the mission is finished. */
    fun currentTask(mission: Mission, done: List<String>): MissionTask? =
        mission.tasks.firstOrNull { it.id !in done }

    fun isComplete(mission: Mission, done: List<String>): Boolean =
        mission.tasks.isNotEmpty() && mission.tasks.all { it.id in done }

    fun taskStates(mission: Mission, done: List<String>): List<TaskState> {
        val current = currentTask(mission, done)
        return mission.tasks.map { t ->
            when {
                t.id in done -> TaskState.DONE
                t.id == current?.id -> TaskState.ACTIVE
                else -> TaskState.LOCKED
            }
        }
    }

    fun progressFraction(mission: Mission, done: List<String>): Float =
        if (mission.tasks.isEmpty()) 0f else mission.tasks.count { it.id in done }.toFloat() / mission.tasks.size

    /** True when [event] satisfies [task]. Quiz threshold is exact: correct/total >= minScorePct/100. */
    fun satisfies(task: MissionTask, event: ActivityEvent): Boolean = when (event) {
        is ActivityEvent.Scan -> task.type == TYPE_SCAN && task.deviceId == event.deviceId
        is ActivityEvent.PartViewed -> task.type == TYPE_IDENTIFY && event.fromExplorer &&
            task.deviceId == event.deviceId && task.partId == event.partId
        is ActivityEvent.ProcedureCompleted -> task.type == TYPE_PROCEDURE &&
            task.deviceId == event.deviceId && task.procedureId == event.procedureId
        is ActivityEvent.QuizCompleted -> {
            val a = event.attempt
            task.type == TYPE_QUIZ && task.deviceId == a.deviceId && task.quizId == a.quizId &&
                a.total > 0 && a.correct.toLong() * 100 >= task.minScorePct.toLong() * a.total
        }
        else -> false
    }

    /**
     * New done list after [event]. Only the current (first unfinished) task can advance, and only while
     * the mission is [active]; later tasks stay locked.
     */
    fun advance(mission: Mission, done: List<String>, event: ActivityEvent, active: Boolean): List<String> {
        if (!active) return done
        val task = currentTask(mission, done) ?: return done
        return if (satisfies(task, event)) done + task.id else done
    }
}
