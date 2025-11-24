package com.samae.photodrop.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.samae.photodrop.data.Photo
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    // Use mutableState for drag updates (no coroutines needed)
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragRotation by remember { mutableStateOf(0f) }
    
    // Use Animatable only for animations
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val animRotation = remember { Animatable(0f) }
    
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Combine drag and animation offsets
    val finalOffsetX = if (isDragging) dragOffsetX else animOffsetX.value
    val finalOffsetY = if (isDragging) dragOffsetY else animOffsetY.value
    val finalRotation = if (isDragging) dragRotation else animRotation.value

    BoxWithConstraints(
        modifier = modifier
            .offset { IntOffset(finalOffsetX.roundToInt(), finalOffsetY.roundToInt()) }
            .rotate(finalRotation)
            .pointerInput(Unit) {
                // Unified gesture handler - covers ENTIRE card area
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val downTime = System.currentTimeMillis()
                    var dragStarted = false
                    var totalDrag = Offset.Zero
                    
                    // Wait to see what gesture this is
                    var waitingForGesture = true
                    var pressJob: kotlinx.coroutines.Job? = null
                    
                    // Start long press timer for video
                    if (photo.isVideo) {
                        pressJob = scope.launch {
                            kotlinx.coroutines.delay(200) // Long press threshold
                            if (waitingForGesture) {
                                isPlaying = true
                            }
                        }
                    }
                    
                    do {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val change = changes.firstOrNull()
                        
                        if (change != null) {
                            val dragDelta = change.positionChange()
                            totalDrag += dragDelta
                            
                            // If moved more than threshold, it's a drag
                            if (totalDrag.getDistance() > 10f && !dragStarted) {
                                dragStarted = true
                                waitingForGesture = false
                                pressJob?.cancel()
                                isPlaying = false
                            }
                            
                            if (dragStarted) {
                                // Handle drag - FAST, direct state update
                                isDragging = true
                                dragOffsetX += dragDelta.x
                                dragOffsetY += dragDelta.y
                                dragRotation = dragOffsetX / 60
                                change.consume()
                            }
                        }
                    } while (changes.any { it.pressed })
                    
                    // Gesture ended
                    pressJob?.cancel()
                    val upTime = System.currentTimeMillis()
                    val pressDuration = upTime - downTime
                    
                    if (dragStarted) {
                        // Was a drag - handle swipe
                        scope.launch {
                            val width = size.width.toFloat()
                            val threshold = width * 0.20f
                            
                            // Copy current drag values to animatable
                            animOffsetX.snapTo(dragOffsetX)
                            animOffsetY.snapTo(dragOffsetY)
                            animRotation.snapTo(dragRotation)
                            
                            // Reset drag state
                            isDragging = false
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            dragRotation = 0f
                            
                            if (animOffsetX.value.absoluteValue < threshold) {
                                // Snap back to center
                                animOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 200,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                )
                                animOffsetY.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(200))
                                animRotation.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(200))
                            } else {
                                // Swipe out
                                val targetX = if (animOffsetX.value > 0) width * 2f else -width * 2f
                                animOffsetX.animateTo(
                                    targetValue = targetX,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 150,
                                        easing = androidx.compose.animation.core.FastOutLinearInEasing
                                    )
                                )
                                // Call action based on direction
                                if (animOffsetX.value > 0) onSwipeRight() else onSwipeLeft()
                            }
                        }
                    } else {
                        // Was a tap
                        isPlaying = false
                        
                        // Check for double tap
                        if (pressDuration < 300) {
                            // Wait to see if there's another tap
                            val secondDown = withTimeoutOrNull(300) {
                                awaitFirstDown()
                            }
                            
                            if (secondDown != null) {
                                // Double tap detected
                                onDoubleTap()
                                // Consume the second tap
                                do {
                                    val event = awaitPointerEvent()
                                } while (event.changes.any { it.pressed })
                            }
                        }
                    }
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
                val swipeThreshold = maxWidthPx * 0.15f
                val swipeProgress = (finalOffsetX.absoluteValue / swipeThreshold).coerceIn(0f, 1f)
                
                if (finalOffsetX > 0) {
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
                                imageVector = Icons.Default.Favorite,
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
                } else if (finalOffsetX < 0) {
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
