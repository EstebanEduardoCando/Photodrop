package com.samae.photodrop

import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import com.samae.photodrop.ui.PhotoViewModel
import com.samae.photodrop.ui.SwipeableCard
import com.samae.photodrop.ui.theme.PhotodropTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PhotoViewModel by viewModels()

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onPermissionResult(result.resultCode == RESULT_OK)
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.loadPhotos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkPermissions()

        setContent {
            PhotodropTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(permissions)
        }
    }

    @Composable
    fun MainScreen(viewModel: PhotoViewModel) {
        val photos by viewModel.photos.collectAsState()
        val permissionIntent by viewModel.permissionNeededForDelete.collectAsState()

        LaunchedEffect(permissionIntent) {
            permissionIntent?.let { intentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                deleteLauncher.launch(request)
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (photos.isEmpty()) {
                Text("No photos found or loading...")
            } else {
                // Show cards in reverse order so the first one is on top
                // Actually, we just need to show the top one or a stack.
                // For simplicity, let's show the top card.
                // To make it look like a stack, we can render the second one behind.
                
                if (photos.size > 1) {
                    SwipeableCard(
                        photo = photos[1],
                        onSwipeLeft = {},
                        onSwipeRight = {},
                        modifier = Modifier.fillMaxSize().graphicsLayer { 
                            scaleX = 0.9f
                            scaleY = 0.9f
                            alpha = 0.5f
                        } // Static background card
                    )
                }
                
                SwipeableCard(
                    photo = photos[0],
                    onSwipeLeft = { viewModel.swipeLeft(photos[0]) },
                    onSwipeRight = { viewModel.swipeRight(photos[0]) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}