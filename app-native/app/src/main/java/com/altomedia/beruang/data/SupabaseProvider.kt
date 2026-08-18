package com.altomedia.beruang.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton holding the configured Supabase client + convenience accessors.
 *
 * Mirrors the web app config:
 *   SUPABASE_URL      = https://jzyfxdysukzvnfllcbvq.supabase.co
 *   SUPABASE_ANON_KEY = sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx
 *   STORAGE_BUCKET    = "media"
 */
object SupabaseProvider {

    const val SUPABASE_URL = "https://jzyfxdysukzvnfllcbvq.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx"
    const val STORAGE_BUCKET = "media"

    private val _client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }

    val client: SupabaseClient get() = _client
    val auth get() = _client.auth
    val postgrest get() = _client.postgrest
    val realtime get() = _client.realtime
    val storage get() = _client.storage

    /** Refresh the current session (if any). Safe to call before reading state. */
    suspend fun refreshSession() = withContext(Dispatchers.IO) {
        runCatching { auth.refreshCurrentSession() }
    }
}
