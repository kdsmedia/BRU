package com.altomedia.beruang.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.query.Columns

/**
 * One row of the `nodes` table.
 *   nodes(path TEXT PRIMARY KEY, value JSONB, ts BIGINT)
 *
 * `value` is stored as a raw JSON element so the same key-value store can hold
 * any shape (numbers, strings, booleans, nested objects) — exactly like the web
 * app's adapter.
 */
@Serializable
data class NodeRow(
    val path: String,
    val value: JsonElement? = JsonNullMark,
    val ts: Long = 0,
)

// Sentinel: kotlinx.serialization can't hold a real null for a non-nullable
// JsonElement, so absent values use a marker that we translate to null on read.
private val JsonNullMark = JsonPrimitive("___null___")

/** A path reference — the Kotlin analogue of `ref(db, path)`. */
data class NodeRef(val path: String)

/**
 * Supabase `nodes` key-value repository. A faithful Kotlin port of the web
 * app's Firebase→Supabase adapter (`ref/get/set/push/update/remove/onValue/
 * runTransaction`) so all data paths and semantics stay identical.
 *
 * Table: nodes(path text primary key, value jsonb, ts bigint)
 * RPC:   cas_update(path, expected_ts, new_value, new_ts) -> int
 */
object NodesRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val pg get() = SupabaseProvider.postgrest

    // ---- ref helpers -------------------------------------------------
    fun ref(path: String = "") = NodeRef(path)

    // ---- read --------------------------------------------------------
    /** Reconstruct the nested value at [path] (null if absent). */
    suspend fun readValue(path: String): JsonElement? {
        // path.eq.<path> OR path.like.<path>/%  (do NOT escape %; it is a wildcard)
        val rows = pg.from("nodes").select(Columns.list("path", "value")) {
            filter {
                or {
                    eq("path", path)
                    like("path", "$path/%")
                }
            }
        }.decodeList<NodeRow>()
        return buildTree(rows, path)
    }

    suspend fun get(r: NodeRef): JsonElement? = readValue(r.path)

    // ---- write -------------------------------------------------------
    /** set(): delete existing node + descendants, then write flattened leaves. */
    suspend fun set(r: NodeRef, value: JsonElement?) {
        pg.from("nodes").delete {
            filter {
                or {
                    eq("path", r.path)
                    like("path", "${r.path}/%")
                }
            }
        }
        if (value == null) return
        val leaves = LinkedHashMap<String, JsonElement>()
        flatten(value, r.path, leaves)
        writeLeaves(leaves)
    }

    /** update(): upsert flattened leaves WITHOUT deleting (merge semantics). */
    suspend fun update(r: NodeRef, patch: JsonObject) {
        val leaves = LinkedHashMap<String, JsonElement>()
        flatten(patch, r.path, leaves)
        writeLeaves(leaves)
    }

    /** push(): generate a sortable id, set the child, return its ref. */
    suspend fun push(r: NodeRef, value: JsonElement): NodeRef {
        val id = genId()
        val child = NodeRef("${r.path}/$id")
        set(child, value)
        return child
    }

    suspend fun remove(r: NodeRef) {
        pg.from("nodes").delete {
            filter {
                or {
                    eq("path", r.path)
                    like("path", "${r.path}/%")
                }
            }
        }
    }

    // ---- atomic transaction (cas_update) -----------------------------
    data class CasResult(val committed: Boolean, val value: JsonElement?)

    /**
     * Optimistic compare-and-swap transaction mirroring the JS `runTransaction`.
     * Reads the current leaf value + ts, applies [transform], then commits via
     * the `cas_update` RPC. Retries up to 8 times on concurrent changes.
     */
    suspend fun runTransaction(r: NodeRef, transform: (JsonElement?) -> JsonElement?): CasResult {
        val table = pg.from("nodes")
        repeat(8) {
            val row = runCatching {
                table.select(Columns.list("path", "value", "ts")) {
                    filter { eq("path", r.path) }
                    limit(1)
                }.decodeSingle<NodeRowSafe>().takeIf { it.path != null }
            }.getOrNull()
            val cur = row?.value
            val curTs = row?.ts ?: 0L
            val newVal = transform(cur)
                // returning null/undefined aborts the transaction (JS parity)
                ?: return CasResult(false, null)
            val ts = System.currentTimeMillis()
            val res = runCatching {
                pg.rpc(
                    function = "cas_update",
                    parameters = buildJsonObject {
                        put("p_path", r.path)
                        put("p_expected_ts", curTs)
                        put("p_new_value", newVal)
                        put("p_new_ts", ts)
                    },
                ).decodeAs<Int>()
            }.getOrNull()
            if (res == 1) return CasResult(true, newVal)
        }
        return CasResult(false, null)
    }

    // ---- internals ---------------------------------------------------
    private suspend fun writeLeaves(leaves: Map<String, JsonElement>) {
        if (leaves.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows = leaves.map { (p, v) ->
            NodeRow(path = p, value = v, ts = now)
        }
        pg.from("nodes").upsert(rows) { onConflict = "path" }
    }

    // Flatten a JSON value into {path: leafValue} entries (objects recurse).
    private fun flatten(el: JsonElement, prefix: String, out: MutableMap<String, JsonElement>) {
        when (el) {
            is JsonObject -> {
                if (el.isEmpty()) { out[prefix] = JsonNullMark; return }
                el.forEach { (k, v) ->
                    val p = if (prefix.isEmpty()) k else "$prefix/$k"
                    flatten(v, p, out)
                }
            }
            else -> out[prefix] = el
        }
    }

    // Reconstruct a nested object from rows whose paths begin with [prefix].
    private fun buildTree(rows: List<NodeRow>, prefix: String): JsonElement? {
        var exact: JsonElement? = null
        // Mutable nested map built up leaf-by-leaf, then frozen to JsonObject.
        val root: MutableMap<String, Any?> = LinkedHashMap()
        for (r in rows) {
            val p = r.path
            val v = r.value?.takeUnless { it == JsonNullMark } ?: JsonNullMark
            if (prefix.isNotEmpty()) {
                if (p == prefix) { exact = v; continue }
                if (!p.startsWith("$prefix/")) continue
            }
            val rel = if (prefix.isEmpty()) p else p.substring(prefix.length + 1)
            val parts = rel.split('/')
            var cur: MutableMap<String, Any?> = root
            for (i in 0 until parts.size - 1) {
                @Suppress("UNCHECKED_CAST")
                cur = cur.getOrPut(parts[i]) { LinkedHashMap<String, Any?>() } as MutableMap<String, Any?>
            }
            cur[parts.last()] = v
        }
        exact?.let { return it }
        return if (root.isEmpty()) null else freeze(root)
    }

    // Recursively convert the mutable builder into immutable JsonObject/JsonElement.
    private fun freeze(node: Map<String, Any?>): JsonElement {
        val out = LinkedHashMap<String, JsonElement>()
        node.forEach { (k, v) ->
            out[k] = when (v) {
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") freeze(v as Map<String, Any?>)
                is JsonElement -> v
                null -> JsonNullMark
                else -> JsonPrimitive(v.toString())
            }
        }
        return JsonObject(out)
    }

    // Sortable push-id (time-ordered), like the web app's _genId().
    private fun genId(): String {
        val t = System.currentTimeMillis().toString(36).padStart(9, '0')
        val r = java.util.Random().nextLong().toString(36).trimStart('-').padStart(6, '0').take(6)
        return "$t-$r"
    }

    // A nullable-safe projection used only inside runTransaction.
    @Serializable
    private data class NodeRowSafe(val path: String? = null, val value: JsonElement? = null, val ts: Long = 0)
}
