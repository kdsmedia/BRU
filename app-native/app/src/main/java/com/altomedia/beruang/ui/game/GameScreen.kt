package com.altomedia.beruang.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted

/**
 * Game hub — placeholder for upcoming games. Full games will be added later.
 */
@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(BgBody),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Game",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = TextMain,
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = BrandYellow,
                    modifier = Modifier.size(96.dp),
                )
                Text(
                    "GAME SEGERA HADIR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = TextMain,
                )
                Text(
                    "Game seru sedang dalam pengembangan.",
                    color = TextMuted,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
