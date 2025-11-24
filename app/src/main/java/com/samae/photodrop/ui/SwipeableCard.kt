package com.samae.photodrop.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
    modifier: Modifier = Modifier
) {
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .rotate(rotation.value)
            .pointerInput(Unit) {
                detectSwipe(
                    onSwipeLeft = {
                        scope.launch {
                            offsetX.animateTo(-size.width.toFloat() * 1.5f)
                            onSwipeLeft()
                        }
                    },
                    onSwipeRight = {
                        scope.launch {
                            offsetX.animateTo(size.width.toFloat() * 1.5f)
                            onSwipeRight()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                            rotation.snapTo(offsetX.value / 60)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value.absoluteValue < size.width / 4) {
                                offsetX.animateTo(0f)
                                offsetY.animateTo(0f)
                                rotation.animateTo(0f)
                            } else {
                                if (offsetX.value > 0) {
                                    offsetX.animateTo(size.width.toFloat() * 1.5f)
                                    onSwipeRight()
                                } else {
                                    offsetX.animateTo(-size.width.toFloat() * 1.5f)
                                    onSwipeLeft()
                                }
                            }
                        }
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
                if (offsetX.value > 0) {
                    Text(
                        text = "KEEP",
                        color = Color.Green,
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(32.dp)
                            .rotate(-15f)
                    )
                } else if (offsetX.value < 0) {
                    Text(
                        text = "DELETE",
                        color = Color.Red,
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(32.dp)
                            .rotate(15f)
                    )
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
