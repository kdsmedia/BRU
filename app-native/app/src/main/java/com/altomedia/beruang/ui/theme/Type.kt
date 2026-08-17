package com.altomedia.beruang.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Corner radii — mirrors the CSS border-radius values (cards 18-20px, etc.).
val RadiusCard = 18.dp
val RadiusCardLg = 20.dp
val RadiusSm = 12.dp
val RadiusPill = RoundedCornerShape(50)
val RadiusBtn = 14.dp
val RadiusFollow = 15.dp

// Typography. The web app uses 'Poppins'; we fall back to the system default
// sans-serif which keeps the same clean look without bundling a font file.
val BeruangTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextMain),
    bodyLarge = TextStyle(fontSize = 15.sp, color = TextMain),
    bodyMedium = TextStyle(fontSize = 14.sp, color = TextMain),
    bodySmall = TextStyle(fontSize = 13.sp, color = TextMuted),
    labelSmall = TextStyle(fontSize = 12.sp, color = TextMuted),
)
