package com.altomedia.beruang.data

import com.altomedia.beruang.ui.auth.AuthUser
import io.github.jan_supabase.auth.auth
import io.github.jan_supabase.auth.providers.builtin.Email
import io.github.jan_supabase.auth.user.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Auth layer — Kotlin port of the web app's auth adapter.
 *
 * Email/password auth on Supabase, where the email is the synthetic form
 * `<phoneDigits>@beruang.phone` (see [phoneToSyntheticEmail]).
 */
object AuthRepository {

    /** Strip non-digits and map to the synthetic email used by auth. */
    fun phoneToSyntheticEmail(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return "${digits}@beruang.phone"
    }

    /** Wrap a Supabase user into the app shape (uid/email/displayName/photoUrl). */
    fun wrapUser(u: UserInfo?): AuthUser? {
        if (u == null) return null
        val meta = u.userMetadata
        return AuthUser(
            uid = u.id.toString(),
            email = u.email,
            displayName = meta?.get("display_name")?.toString()?.trim('"') ?: u.email?.substringBefore('@'),
            photoUrl = meta?.get("photo_url")?.toString()?.trim('"'),
        )
    }

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        SupabaseProvider.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String): AuthUser? = withContext(Dispatchers.IO) {
        val res = SupabaseProvider.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        wrapUser(res)
    }

    /** Update profile metadata (display name / photo url). */
    suspend fun updateProfile(displayName: String? = null, photoUrl: String? = null) =
        withContext(Dispatchers.IO) {
            SupabaseProvider.auth.updateUser {
                data = buildJsonObject {
                    if (displayName != null) put("display_name", displayName)
                    if (photoUrl != null) put("photo_url", photoUrl)
                }
            }
        }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { SupabaseProvider.auth.signOut() }
    }

    /** Current session user, or null if not signed in. */
    fun currentUser(): AuthUser? = wrapUser(SupabaseProvider.auth.currentUserOrNull())
}
