package com.altomedia.beruang.data

import io.github.jan_supabase.realtime.RealtimeChannel
import io.github.jan_supabase.realtime.eventPostgresChangeFlow
import io.github.jan_supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Realtime subscription over the `nodes` table, mirroring the web app's
 * `onValue(ref, cb)`. Re-reads the path (debounced ~120ms) whenever a matching
 * row changes, so consumers always see a fresh reconstructed tree.
 *
 * Strategy: subscribe to Postgres Changes for descendants (`path LIKE 'p/%'`)
 * and the exact leaf (`path = 'p'`); on any event, debounce-re-read. This is
 * the same end-to-end behaviour as the JS adapter, which likewise re-reads the
 * full path on every realtime tick (it does not apply the payload diff).
 *
 * Usage: keep the returned [NodeSubscription] and call [NodeSubscription.cancel]
 * when the screen leaves composition (the analogue of the JS `off()`).
 */
object RealtimeRepository {

    private const val DEBOUNCE_MS = 120L

    /** A live, debounced view of the value at [path]. */
    fun watch(path: String, scope: CoroutineScope): NodeSubscription {
        val flow = MutableStateFlow<JsonElement?>(null)
        val channelName = "rt:${path.hashCode()}-${path.length}"

        // Initial read.
        val initJob = scope.launch { flow.value = NodesRepository.readValue(path) }

        val channel: RealtimeChannel = SupabaseProvider.realtime.channel(channelName)
        var debounceJob: Job? = null
        val schedule = {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(DEBOUNCE_MS)
                flow.value = NodesRepository.readValue(path)
            }
        }

        val collectors = mutableListOf<Job>()
        // Descendants: INSERT/UPDATE/DELETE on path LIKE 'p/%'.
        for (evt in listOf("INSERT", "UPDATE", "DELETE")) {
            collectors += scope.launch {
                runCatching {
                    channel.eventPostgresChangeFlow(evt) {
                        schema = "public"; table = "nodes"; filter = "path=like.${path}/%"
                    }.collect { schedule() }
                }
            }
        }
        // Exact leaf: UPDATE on path = 'p'.
        collectors += scope.launch {
            runCatching {
                channel.eventPostgresChangeFlow("UPDATE") {
                    schema = "public"; table = "nodes"; filter = "path=eq.${path}"
                }.collect { schedule() }
            }
        }

        runCatching { channel.connect() }

        return NodeSubscription(
            path = path,
            flow = flow.asStateFlow(),
            scope = scope,
            channel = channel,
            jobs = listOf(initJob) + collectors,
        )
    }
}

/** Holds a realtime subscription. Cancel to tear down the channel + coroutines. */
class NodeSubscription(
    val path: String,
    val flow: StateFlow<JsonElement?>,
    private val scope: CoroutineScope,
    private val channel: RealtimeChannel?,
    private val jobs: List<Job>,
) {
    val value: JsonElement? get() = flow.value

    /** Force an immediate re-read (used after manual writes / pull-to-refresh). */
    fun refreshNow() {
        scope.launch { flow.value = NodesRepository.readValue(path) }
    }

    fun cancel() {
        jobs.forEach { it.cancel() }
        runCatching {
            channel?.let { SupabaseProvider.realtime.removeChannel(it) }
        }
    }
}
