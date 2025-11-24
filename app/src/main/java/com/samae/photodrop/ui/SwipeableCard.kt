package com.samae.photodrop.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.samae.photodrop.data.Photo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun SwipeableCard(
    photo: Photo,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .rotate(rotation.value)
            .pointerInput(Unit) {
                val widthPx = size.width.toFloat()
                detectSwipe(
                    onSwipeLeft = {
                        // Trigger callback immediately, no animation here
                        onSwipeLeft()
                    },
                    onSwipeRight = {
                        // Trigger callback immediately, no animation here
                        onSwipeRight()
                    },
                    onDrag = { change, dragAmount ->
                        // Direct snap - no coroutine launch for every drag event
                        offsetX.snapTo(offsetX.value + dragAmount.x)
                        offsetY.snapTo(offsetY.value + dragAmount.y)
                        rotation.snapTo(offsetX.value / 60)
                    },
                    onDragEnd = {
                        scope.launch {
                            val width = size.width.toFloat()
                            val threshold = width * 0.20f // Slightly higher threshold for better control
                            
                            if (offsetX.value.absoluteValue < threshold) {
                                // Snap back to center - fast animation
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 200,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                )
                                offsetY.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(200))
                                rotation.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(200))
                            } else {
                                // Swipe out - VERY fast animation
                                val targetX = if (offsetX.value > 0) width * 2f else -width * 2f
                                offsetX.animateTo(
                                    targetValue = targetX,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 150,
                                        easing = androidx.compose.animation.core.FastOutLinearInEasing
                                    )
                                )
                                // Call action based on direction
                                if (offsetX.value > 0) onSwipeRight() else onSwipeLeft()
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTap()
                    }
                )
            }
            .pointerInput(Unit) {
                if (photo.isVideo) {
                    detectTapGestures(
                        onPress = {
                            isPlaying = true
                            tryAwaitRelease()
                            isPlaying = false
                        }
                    )
                }
            }
    ) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (photo.isVideo && isPlaying) {
                    VideoPlayer(
                        uri = photo.uri,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (photo.isVideo) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                // Metadata Overlay (Top Gradient)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(photo.dateAdded * 1000)),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(photo.dateAdded * 1000)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "${String.format("%.1f", photo.size / (1024f * 1024f))} MB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.weight(1f))
                        if (photo.isVideo) {
                            Text(
                                text = DateUtils.formatElapsedTime(photo.duration / 1000),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                // Swipe Indicators
                // Swipe Indicators
                val swipeThreshold = maxWidthPx * 0.15f
                val swipeProgress = (offsetX.value.absoluteValue / swipeThreshold).coerceIn(0f, 1f)
                
                if (offsetX.value > 0) {
                    // KEEP Indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(32.dp)
                            .rotate(-15f)
                            .graphicsLayer { alpha = swipeProgress }
                            .background(
                                color = Color.Green.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Favorite, // Or Check
                                contentDescription = "Keep",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "KEEP",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                } else if (offsetX.value < 0) {
                    // DELETE Indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(32.dp)
                            .rotate(15f)
                            .graphicsLayer { alpha = swipeProgress }
                            .background(
                                color = Color.Red.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "DELETE",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectSwipe(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown()
            var dragAmount = Offset.Zero
            do {
                val event = awaitPointerEvent()
                val changes = event.changes
                val change = changes.firstOrNull()
                if (change != null) {
                    val changeAmount = change.positionChange()
                    dragAmount += changeAmount
                    onDrag(change, changeAmount)
                    change.consume()
                }
            } while (changes.any { it.pressed })
            
            onDragEnd()
        }
    }
}
