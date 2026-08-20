package com.altomedia.beruang.data

import com.altomedia.beruang.ui.auth.AuthUser
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
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

    /** Outcome of [register]: either the signed-in user or an error message. */
    data class RegisterResult(val user: AuthUser? = null, val error: String? = null)

    /**
     * Full registration flow — direct port of the web `handleRegister`:
     *   signUp → updateProfile(name, default avatar) → ensureWalletExists →
     *   write users/{uid} node → apply referral (optional).
     * Post-signup data writes are best-effort: anything missed here is healed
     * by [bootstrapAdminAndCheckBlock] on the next login.
     */
    suspend fun register(
        name: String,
        phoneRaw: String,
        password: String,
        referral: String?,
    ): RegisterResult = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext RegisterResult(error = "Nama lengkap wajib diisi")
        val digits = phoneRaw.filter { it.isDigit() }
        if (digits.length < 8) return@withContext RegisterResult(error = "Nomor HP tidak valid")
        if (password.length < 6) return@withContext RegisterResult(error = "Sandi minimal 6 karakter")
        val email = phoneToSyntheticEmail(phoneRaw)
        val user = try {
            signUp(email, password)
        } catch (e: Throwable) {
            val msg = e.message.orEmpty()
            return@withContext RegisterResult(
                error = if (Regex("already registered|already been registered|exists", RegexOption.IGNORE_CASE).containsMatchIn(msg))
                    "Nomor HP sudah terdaftar. Silakan Masuk." else msg,
            )
        } ?: return@withContext RegisterResult(error = "Pendaftaran gagal")
        // signUp does not always leave a live session behind — sign in
        // explicitly so currentUser() works immediately afterwards.
        if (currentUser() == null) runCatching { signIn(email, password) }
        runCatching { updateProfile(displayName = name, photoUrl = AppConstants.DEFAULT_AVATAR) }
        runCatching { WalletRepository.ensureWalletExists(user.uid, name) }
            .onFailure { android.util.Log.e("AuthRepository", "ensureWalletExists failed for ${user.uid}", it) }
        runCatching {
            NodesRepository.update(
                NodesRepository.ref(Paths.user(user.uid)),
                buildJsonObject {
                    put("username", name)
                    put("photo", AppConstants.DEFAULT_AVATAR)
                    put("phone", digits)
                    put("uid", user.uid)
                },
            )
        }.onFailure { android.util.Log.e("AuthRepository", "user node write failed for ${user.uid}", it) }
        if (!referral.isNullOrBlank()) {
            runCatching { applyReferral(user.uid, referral.trim()) }
        }
        RegisterResult(user = currentUser() ?: user)
    }

    /** Look up a uid by 6-digit account id (referral code == account id). */
    suspend fun findUidByAcctId(acctId: String): String? {
        val idx = NodesRepository.readValue(Paths.accountIndex())?.asObject() ?: return null
        return idx.entries.firstOrNull { it.value.asString() == acctId }?.key
    }

    /** Apply referral linking + bonuses for a freshly registered user. */
    private suspend fun applyReferral(newUid: String, referralCode: String) {
        val referrerUid = findUidByAcctId(referralCode) ?: return
        if (referrerUid == newUid) return
        NodesRepository.set(NodesRepository.ref("users/$newUid/referredBy"), JsonPrimitive(referrerUid))
        NodesRepository.set(NodesRepository.ref("users/$referrerUid/referrals/$newUid"), JsonPrimitive(true))
        WalletRepository.awardPoints(referrerUid, AppConstants.REFERRAL_BONUS_REFERRER, "referral dari $referralCode")
        WalletRepository.awardPoints(newUid, AppConstants.REFERRAL_BONUS_NEW_USER, "bonus referral")
    }

    /**
     * Post-login bootstrap — direct port of `bootstrapAdminAndCheckBlock`:
     *   1. blocked users are signed out
     *   2. admin phones are elevated (role admin, tier Master, fixed acctId)
     * Returns false if the user is blocked (caller should sign out + show msg).
     */
    suspend fun bootstrapAdminAndCheckBlock(user: AuthUser): Boolean = withContext(Dispatchers.IO) {
        val blocked = NodesRepository.readValue(Paths.blocked(user.uid))?.asBoolean() == true
        if (blocked) {
            signOut()
            return@withContext false
        }
        var uData = NodesRepository.readValue(Paths.user(user.uid))?.asObject()
        var phone = uData?.str("phone")?.filter { it.isDigit() }
        if (phone.isNullOrBlank() && user.email != null) {
            phone = Regex("^(\\d+)@").find(user.email)?.groupValues?.get(1)
        }
        // Self-heal: guarantee the wallet (6-digit account id) and the user
        // node exist, even if the register-time writes failed or the account
        // predates them. Without this a new user never gets an account id.
        runCatching { WalletRepository.ensureWalletExists(user.uid, user.displayName ?: "Pengguna") }
            .onFailure { android.util.Log.e("AuthRepository", "wallet heal failed for ${user.uid}", it) }
        if (uData == null || uData.str("username") == null) {
            runCatching {
                NodesRepository.update(
                    NodesRepository.ref(Paths.user(user.uid)),
                    buildJsonObject {
                        put("username", user.displayName ?: "Pengguna")
                        put("photo", user.photoUrl ?: AppConstants.DEFAULT_AVATAR)
                        if (!phone.isNullOrBlank()) put("phone", phone)
                        put("uid", user.uid)
                    },
                )
            }.onFailure { android.util.Log.e("AuthRepository", "user heal failed for ${user.uid}", it) }
            uData = NodesRepository.readValue(Paths.user(user.uid))?.asObject()
        }
        val isAdmin = phone != null && AppConstants.Admin.PHONES.contains(phone)
        if (isAdmin) {
            val w = NodesRepository.readValue(Paths.wallet(user.uid))?.asObject()
            val patch = buildJsonObject {
                put("role", "admin")
                put("tier", "Master")
                if (w?.str("acctId") != AppConstants.Admin.ACCT_ID) put("acctId", AppConstants.Admin.ACCT_ID)
            }
            NodesRepository.update(NodesRepository.ref(Paths.wallet(user.uid)), patch)
            NodesRepository.set(NodesRepository.ref(Paths.accountIndex(user.uid)), JsonPrimitive(AppConstants.Admin.ACCT_ID))
            NodesRepository.update(NodesRepository.ref(Paths.user(user.uid)), buildJsonObject {
                put("role", "admin")
                put("username", AppConstants.Admin.NAME)
                put("phone", phone)
            })
            if (user.displayName != AppConstants.Admin.NAME) {
                runCatching { updateProfile(displayName = AppConstants.Admin.NAME) }
            }
        }
        true
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

    /** Update the user's auth email. Supabase will send a confirmation to the
     *  new address; the change only takes effect after the user confirms it. */
    suspend fun updateEmail(newEmail: String) = withContext(Dispatchers.IO) {
        SupabaseProvider.auth.updateUser { email = newEmail }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { SupabaseProvider.auth.signOut() }
    }

    /** Current session user, or null if not signed in. */
    fun currentUser(): AuthUser? = wrapUser(SupabaseProvider.auth.currentUserOrNull())
}
