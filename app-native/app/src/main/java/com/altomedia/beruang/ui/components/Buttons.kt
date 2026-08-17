package com.altomedia.beruang.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.ui.theme.BrandRed
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.BtnPrimary
import com.altomedia.beruang.ui.theme.ErrorRed
import com.altomedia.beruang.ui.theme.RadiusBtn
import com.altomedia.beruang.ui.theme.TextMain

/** Primary yellow button with optional loading spinner (mirrors .btn-primary). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(RadiusBtn),
        colors = BtnPrimary(),
        contentPadding = PaddingValues(vertical = 14.dp),
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = TextMain,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Secondary outline button (mirrors .btn-sec / .btn-main secondary). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(RadiusBtn),
        border = BorderStroke(1.dp, if (danger) BrandRed else Color(0xFFE2E8F0)),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (danger) ErrorRed else TextMain)
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        }
        Text(text, color = if (danger) ErrorRed else TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
