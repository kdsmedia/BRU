package com.altomedia.beruang.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.AuthRepository
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
 * Tracks the Supabase auth session. On init it refreshes/import the stored
 * session and exposes the current user (or null) for the navigation graph.
 */
class AuthViewModel : ViewModel() {
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser

    init {
        viewModelScope.launch {
            SupabaseProvider.refreshSession()
            _currentUser.value = AuthRepository.currentUser()
        }
    }

    /** Update the exposed user (called after login/register/logout). */
    fun setUser(user: AuthUser?) {
        _currentUser.value = user
    }
}
