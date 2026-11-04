package com.equipx.app.data

import com.equipx.app.domain.ActivityEvent
import com.equipx.app.domain.Badge
import com.equipx.app.domain.EngineResult
import com.equipx.app.domain.MissionRules
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single entry point for every award path: records an event through the points/badge engine,
 * advances the active mission, and queues new badges for the toast host.
 *
 * All events go through one unbounded channel read by one consumer in an app-lifetime scope, so they are applied
 * strictly in call order and never cancelled when a screen goes away.
 */
class ActivityTracker(
    private val progress: ProgressRepository,
    private val content: ContentRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private class Pending(val event: ActivityEvent, val result: CompletableDeferred<EngineResult>)

    private val channel = Channel<Pending>(Channel.UNLIMITED)
    private val _pendingBadges = MutableStateFlow<List<Badge>>(emptyList())

    /**
     * Badges unlocked but not yet acknowledged by the toast host, oldest first. Lives as long as the app, so toasts
     * survive activity recreation; the host calls [acknowledgeBadge] once a badge has been shown.
     */
    val pendingBadges: StateFlow<List<Badge>> = _pendingBadges.asStateFlow()

    init {
        scope.launch {
            runCatching { startup() }
            for (p in channel) {
                try {
                    p.result.complete(process(p.event))
                } catch (t: Throwable) {
                    p.result.completeExceptionally(t)
                }
            }
        }
    }

    /** Fire and forget; survives the calling screen going away. Events are applied in call order. */
    fun post(event: ActivityEvent) {
        postForResult(event)
    }

    /** Queues [event] immediately (synchronously, in call order) and returns its eventual result. */
    fun postForResult(event: ActivityEvent): Deferred<EngineResult> {
        val p = Pending(event, CompletableDeferred())
        channel.trySend(p)
        return p.result
    }

    suspend fun record(event: ActivityEvent): EngineResult = postForResult(event).await()

    fun acknowledgeBadge(badge: Badge) {
        _pendingBadges.update { list -> list.filterNot { it == badge } }
    }

    private fun announce(result: EngineResult) {
        if (result.newBadges.isEmpty()) return
        _pendingBadges.update { list -> list + result.newBadges.filter { it !in list } }
    }

    /** Silent migration, then award any mission whose tasks are all done but which was never awarded. */
    private suspend fun startup() {
        progress.migrate()
        val missions = content.getMissions().getOrNull().orEmpty()
        if (missions.isEmpty()) return
        val results = progress.applyMissionStep { st ->
            val complete = missions.filter { MissionRules.isComplete(it, st.doneTasks[it.id].orEmpty()) }.map { it.id }
            MissionStep(
                if (st.activeMissionId in complete) st.copy(activeMissionId = null) else st,
                complete,
            )
        }
        results.forEach(::announce)
    }

    private suspend fun process(event: ActivityEvent): EngineResult {
        val result = progress.record(event)
        announce(result)
        if (event !is ActivityEvent.MissionCompleted) advanceMission(event)
        return result
    }

    private suspend fun advanceMission(event: ActivityEvent) {
        val missions = content.getMissions().getOrNull().orEmpty()
        if (missions.isEmpty()) return
        // Task progress and the mission award happen in one storage edit: no window where one is lost.
        val results = progress.applyMissionStep { st ->
            val id = st.activeMissionId ?: return@applyMissionStep MissionStep(st)
            val mission = missions.firstOrNull { it.id == id } ?: return@applyMissionStep MissionStep(st)
            val done = st.doneTasks[id].orEmpty()
            val next = MissionRules.advance(mission, done, event, active = true)
            if (next == done) return@applyMissionStep MissionStep(st)
            val updated = st.copy(doneTasks = st.doneTasks + (id to next))
            if (MissionRules.isComplete(mission, next)) MissionStep(updated.copy(activeMissionId = null), listOf(id))
            else MissionStep(updated)
        }
        results.forEach(::announce)
    }

    /** Makes [missionId] the active mission (only one at a time). */
    suspend fun activateMission(missionId: String) {
        progress.updateMissionState { it.copy(activeMissionId = missionId) }
    }
}
