package com.altomedia.beruang.ui.components

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.ui.theme.BrandRed
import com.altomedia.beruang.ui.theme.BrandYellow

/** Gradient "BERUANG" wordmark — mirrors .logo-text-lg / .gradient-text. */
@Composable
fun GradientLogo(modifier: Modifier = Modifier, size: Int = 32) {
    Text(
        text = "BERUANG",
        fontSize = size.sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(brush = Brush.horizontalGradient(listOf(BrandYellow, BrandRed))),
        modifier = modifier,
    )
}

/** Lightweight toast helper (mirrors the web `showToast`). */
@Composable
fun rememberToast(): (String) -> Unit {
    val context = LocalContext.current
    return { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
}

fun showToast(context: android.content.Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
