package com.altomedia.beruang.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.data.AuthRepository
import com.altomedia.beruang.ui.components.AuthInput
import com.altomedia.beruang.ui.components.GradientLogo
import com.altomedia.beruang.ui.components.PrimaryButton
import com.altomedia.beruang.ui.theme.ErrorRed
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import kotlinx.coroutines.launch

/** Auth screen — direct port of the web `#auth-view` (login + register tabs).
 *  [authVm] MUST be the nav host's instance — a screen-scoped default would
 *  create a second ViewModel and the root graph would never see the login. */
@Composable
fun LoginScreen(
    onAuthed: () -> Unit,
    authVm: AuthViewModel,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("login") }

    // login fields
    var loginPhone by remember { mutableStateOf("") }
    var loginPass by remember { mutableStateOf("") }
    // register fields
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var regReferral by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    // After a successful auth state change, the nav host swaps to MAIN.
    val user by authVm.currentUser.collectAsState()
    LaunchedEffect(user) { if (user != null) onAuthed() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GradientLogo(size = 28)
                Text(
                    "Terhubung & Berbagi.",
                    color = Color(0xFF666666),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
                )

                // Tabs (Masuk / Daftar) — mirrors .auth-tabs
                AuthTabs(tab = tab, onSelect = { tab = it; error = "" })

                if (tab == "login") {
                    AuthInput(
                        value = loginPhone,
                        onValueChange = { loginPhone = it.filter { c -> c.isDigit() } },
                        placeholder = "Nomor HP (contoh: 081234567890)",
                        keyboardType = KeyboardType.Number,
                        maxLength = 15,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    AuthInput(
                        value = loginPass,
                        onValueChange = { loginPass = it },
                        placeholder = "Sandi",
                        isPassword = true,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    PrimaryButton(
                        text = "Masuk",
                        onClick = {
                            if (loginPhone.isBlank() || loginPass.isBlank()) return@PrimaryButton
                            val digits = loginPhone.filter { it.isDigit() }
                            if (digits.length < 8) { error = "Nomor HP tidak valid"; return@PrimaryButton }
                            loading = true; error = ""
                            scope.launch {
                                val email = AuthRepository.phoneToSyntheticEmail(loginPhone)
                                val ok = runCatching { AuthRepository.signIn(email, loginPass) }.isSuccess
                                loading = false
                                if (!ok) {
                                    error = "Nomor HP atau sandi salah. Belum punya akun? Pilih Daftar."
                                } else {
                                    val u = AuthRepository.currentUser()
                                    if (u != null) {
                                        val allowed = AuthRepository.bootstrapAdminAndCheckBlock(u)
                                        if (!allowed) {
                                            error = "Akun Anda diblokir oleh admin."
                                            authVm.setUser(null)
                                        } else {
                                            authVm.setUser(u)
                                        }
                                    }
                                }
                            }
                        },
                        loading = loading,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                } else {
                    AuthInput(
                        value = regName,
                        onValueChange = { regName = it },
                        placeholder = "Nama Lengkap",
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    AuthInput(
                        value = regPhone,
                        onValueChange = { regPhone = it.filter { c -> c.isDigit() } },
                        placeholder = "Nomor HP (contoh: 081234567890)",
                        keyboardType = KeyboardType.Number,
                        maxLength = 15,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    AuthInput(
                        value = regPass,
                        onValueChange = { regPass = it },
                        placeholder = "Sandi",
                        isPassword = true,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    AuthInput(
                        value = regReferral,
                        onValueChange = { regReferral = it.filter { c -> c.isDigit() } },
                        placeholder = "Kode Referral (opsional)",
                        keyboardType = KeyboardType.Number,
                        maxLength = 6,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    PrimaryButton(
                        text = "Daftar",
                        onClick = {
                            loading = true; error = ""
                            scope.launch {
                                val res = AuthRepository.register(regName, regPhone, regPass, regReferral.takeIf { it.isNotBlank() })
                                loading = false
                                if (res.error != null) {
                                    error = res.error
                                } else {
                                    val u = res.user
                                    if (u != null) {
                                        val allowed = AuthRepository.bootstrapAdminAndCheckBlock(u)
                                        if (!allowed) {
                                            error = "Akun Anda diblokir oleh admin."
                                            authVm.setUser(null)
                                        } else {
                                            authVm.setUser(u)
                                        }
                                    }
                                }
                            }
                        },
                        loading = loading,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                Text(
                    error,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 10.dp),
                    minLines = 1,
                )
                Text(
                    "Masuk dengan nomor HP & sandi. Belum punya akun? Pilih Daftar.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** Login/Register tab switcher — mirrors `.auth-tabs`. */
@Composable
private fun AuthTabs(tab: String, onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        RowTabs(tab, onSelect)
    }
}

@Composable
private fun RowTabs(tab: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("login" to "Masuk", "register" to "Daftar").forEach { (id, label) ->
            TabItem(id = id, label = label, active = tab == id, onSelect = onSelect)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    id: String,
    label: String,
    active: Boolean,
    onSelect: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .background(
                if (active) Color.White else Color.Transparent,
                RoundedCornerShape(9.dp),
            )
            .noRippleClickable { onSelect(id) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) TextMain else TextMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )
