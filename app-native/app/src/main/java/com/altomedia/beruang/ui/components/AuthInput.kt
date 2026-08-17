package com.altomedia.beruang.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.ui.theme.Border
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted

/** Single-line input mirroring `.auth-input` (border 2px, radius 12, focus yellow). */
@Composable
fun AuthInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = Int.MAX_VALUE,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) },
        singleLine = true,
        textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandYellow,
            unfocusedBorderColor = Border,
            focusedBorderWidth = 2.dp,
            unfocusedBorderWidth = 2.dp,
            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.White,
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
    )
}
