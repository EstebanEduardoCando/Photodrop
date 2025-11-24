package com.samae.photodrop

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.samae.photodrop.data.UserPreferences
import com.samae.photodrop.ui.OnboardingScreen
import com.samae.photodrop.ui.PhotoViewModel
import kotlinx.coroutines.launch
import com.samae.photodrop.ui.SwipeableCard
import com.samae.photodrop.ui.theme.PhotodropTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PhotoViewModel by viewModels()

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onPermissionResult(result.resultCode == RESULT_OK)
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.loadFolders()
            viewModel.loadPhotos()
        }
    }

    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferences = UserPreferences(this)
        
        checkPermissions()

        setContent {
            PhotodropTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212) // Dark background
                ) {
                    val isFirstLaunch by userPreferences.isFirstLaunch.collectAsState(initial = null)

                    when (isFirstLaunch) {
                        true -> {
                            com.samae.photodrop.ui.OnboardingScreen(onFinished = {
                                androidx.lifecycle.lifecycleScope.launch {
                                    userPreferences.completeOnboarding()
                                }
                            })
                        }
                        false -> {
                            MainScreen(viewModel, ::checkPermissions, ::openSettings)
                        }
                        null -> {
                            // Loading state, keep splash or empty
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(viewModel: PhotoViewModel, onRetryPermissions: () -> Unit, onOpenSettings: () -> Unit) {
        val photos by viewModel.photos.collectAsState()
        val folders by viewModel.folders.collectAsState()
        val selectedFolder by viewModel.selectedFolder.collectAsState()
        val pendingDeletes by viewModel.pendingDeletes.collectAsState()
        val permissionIntent by viewModel.permissionNeededForDelete.collectAsState()
        val deletionSummary by viewModel.deletionSummary.collectAsState()

        var showFolderDropdown by remember { mutableStateOf(false) }
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

        LaunchedEffect(permissionIntent) {
            permissionIntent?.let { intentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                deleteLauncher.launch(request)
            }
        }

        LaunchedEffect(deletionSummary) {
            deletionSummary?.let { message ->
                snackbarHostState.showSnackbar(message)
                viewModel.clearDeletionSummary()
            }
        }

        Scaffold(
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showFolderDropdown = true }
                        ) {
                            Text(text = selectedFolder?.name ?: "All Photos")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Folder")
                        }
                        DropdownMenu(
                            expanded = showFolderDropdown,
                            onDismissRequest = { showFolderDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Photos") },
                                onClick = {
                                    viewModel.selectFolder(null)
                                    showFolderDropdown = false
                                }
                            )
                            folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text("${folder.name} (${folder.count})") },
                                    onClick = {
                                        viewModel.selectFolder(folder)
                                        showFolderDropdown = false
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                             // Android 14+ Manage Access
                             Icon(
                                 imageVector = Icons.Default.Settings,
                                 contentDescription = "Manage Access",
                                 modifier = Modifier
                                     .clickable { onOpenSettings() }
                                     .padding(8.dp)
                             )
                        }
                    }
                )
            },
            containerColor = Color.Transparent,
            floatingActionButton = {
                if (pendingDeletes.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Button(
                            onClick = { viewModel.undoDelete() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text("Undo")
                        }
                        
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.confirmDeletes() },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                            Spacer(Modifier.width(8.dp))
                            Text("Clean Up (${pendingDeletes.size})")
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF2C3E50), Color(0xFF000000))
                        )
                    )
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                if (photos.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No photos found.", color = Color.White)
                        Spacer(Modifier.padding(8.dp))
                        Button(onClick = onRetryPermissions) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Check Permissions / Reload")
                        }
                        Spacer(Modifier.padding(8.dp))
                        Button(onClick = onOpenSettings) {
                            Text("Manage Access")
                        }
                    }
                } else {
                    if (photos.size > 1) {
                        // Use key to ensure Compose treats this as a distinct item if needed, 
                        // though for the background card it's less critical.
                        key(photos[1].id) {
                            SwipeableCard(
                                photo = photos[1],
                                onSwipeLeft = {},
                                onSwipeRight = {},
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = 0.9f
                                        scaleY = 0.9f
                                        alpha = 0.5f
                                    }
                            )
                        }
                    }
                    
                    // CRITICAL FIX: Use key() to force recomposition when the top photo changes.
                    // This ensures the state (like swipe offset) is reset for the new photo.
                    key(photos[0].id) {
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
    }
}