package com.altomedia.beruang.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Convenience extensions to read/convert the dynamic JSON values stored in `nodes`. */

fun JsonObject?.str(key: String): String? = this?.get(key)?.jsonPrimitive?.contentOrNull
fun JsonObject?.strOr(key: String, default: String): String = this?.str(key) ?: default
fun JsonObject?.long(key: String): Long? = this?.get(key)?.jsonPrimitive?.longOrNull
fun JsonObject?.longOr(key: String, default: Long): Long = this?.long(key) ?: default
fun JsonObject?.int(key: String): Int? = this?.get(key)?.jsonPrimitive?.intOrNull
fun JsonObject?.intOr(key: String, default: Int): Int = this?.int(key) ?: default
fun JsonObject?.bool(key: String): Boolean? = this?.get(key)?.jsonPrimitive?.booleanOrNull
fun JsonObject?.boolOr(key: String, default: Boolean): Boolean = this?.bool(key) ?: default
fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

/** Number value of a leaf (handles int/long/double stored as JsonPrimitive). */
fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull
fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull
fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
fun JsonElement?.asBoolean(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

/** Convert this element to a JsonObject (null→empty). */
fun JsonElement?.asObject(): JsonObject =
    if (this is JsonObject) this else JsonObject(emptyMap())

/** Build a JsonObject from vararg pairs of (String, JsonElement|primitive). */
fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (k, v) ->
        when (v) {
            null -> {}
            is JsonElement -> put(k, v)
            is Number -> put(k, v)
            is Boolean -> put(k, v)
            is String -> put(k, v)
            is JsonObject -> put(k, v)
            is JsonArray -> put(k, v)
            else -> put(k, v.toString())
        }
    }
}
