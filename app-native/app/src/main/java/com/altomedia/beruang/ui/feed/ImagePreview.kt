package com.altomedia.beruang.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * Full-screen image preview with pinch-to-zoom and pan. Tap the background or
 * the close button to dismiss. Shown over the feed when a post image is tapped.
 */
@Composable
fun ImagePreview(url: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val context = LocalContext.current

    // Reset transforms whenever a new image is shown.
    LaunchedEffect(url) {
        scale.snapTo(1f)
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(url) {
                // Detect taps on the backdrop (not consumed by the image zoom
                // gesture detector) to dismiss the preview.
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            contentDescription = "Pratinjau gambar",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    translationX = offsetX.value,
                    translationY = offsetY.value,
                )
                .pointerInput(url) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scope.launch {
                            val newScale = (scale.value * zoom).coerceIn(1f, 5f)
                            // Pan only when zoomed in beyond 1x.
                            val canPan = newScale > 1f
                            scale.snapTo(newScale)
                            if (canPan) {
                                offsetX.snapTo(offsetX.value + pan.x)
                                offsetY.snapTo(offsetY.value + pan.y)
                            } else {
                                offsetX.snapTo(0f)
                                offsetY.snapTo(0f)
                            }
                        }
                    }
                }
                .pointerInput(url) {
                    // Double-tap toggles zoom between 1x and 2.5x.
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch {
                                if (scale.value > 1f) {
                                    scale.animateTo(1f, tween(220))
                                    offsetX.animateTo(0f, tween(220))
                                    offsetY.animateTo(0f, tween(220))
                                } else {
                                    scale.animateTo(2.5f, tween(220))
                                }
                            }
                        },
                    )
                },
        )

        // Close button (top-end).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x66000000))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Tutup",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
