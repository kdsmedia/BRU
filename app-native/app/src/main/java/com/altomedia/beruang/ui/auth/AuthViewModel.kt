package com.altomedia.beruang.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.SupabaseProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Live auth state holder. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
)

/**
 * Tracks the Supabase auth session. Implemented fully in the data-layer step.
 */
class AuthViewModel : ViewModel() {
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser

    init {
        viewModelScope.launch {
            SupabaseProvider.auth.refreshSession()
            SupabaseProvider.currentAuthUser()?.let { _currentUser.value = it }
        }
    }

    fun setUser(user: AuthUser?) {
        _currentUser.value = user
    }
}
