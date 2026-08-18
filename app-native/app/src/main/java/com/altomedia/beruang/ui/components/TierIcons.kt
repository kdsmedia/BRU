package com.altomedia.beruang.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.altomedia.beruang.data.AppConstants

/**
 * Material icon matching each account tier, used by the tier pill on the
 * profile header and by the "Naik Kelas" (TierSheet) rows so every class shows
 * its own distinct icon instead of just the first letter of the name.
 */
fun tierIcon(name: String?): ImageVector = when (AppConstants.tier(name).name) {
    "Star" -> Icons.Filled.Star
    "Bronze" -> Icons.Filled.WorkspacePremium
    "Silver" -> Icons.Filled.Star
    "Gold" -> Icons.Filled.EmojiEvents
    "Master" -> Icons.Filled.MilitaryTech
    else -> Icons.Filled.Star
}
