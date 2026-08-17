package com.altomedia.beruang.data

import com.altomedia.beruang.ui.auth.AuthUser

/**
 * Singleton access point for the Supabase client + auth state.
 *
 * Fully implemented in the data-layer step (Supabase config, nodes key-value
 * repository, realtime, storage). Kept as a stub here so the scaffold compiles.
 */
object SupabaseProvider {
    val auth: Any get() = error("SupabaseProvider not initialized yet — see data-layer step")

    suspend fun refreshSession() {}
    fun currentAuthUser(): AuthUser? = null
}
