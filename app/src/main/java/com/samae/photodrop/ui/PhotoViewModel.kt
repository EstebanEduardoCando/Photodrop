package com.samae.photodrop.ui

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samae.photodrop.data.Folder
import com.samae.photodrop.data.Photo
import com.samae.photodrop.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _pendingDeletes = MutableStateFlow<List<Photo>>(emptyList())
    val pendingDeletes: StateFlow<List<Photo>> = _pendingDeletes.asStateFlow()

    private val _permissionNeededForDelete = MutableStateFlow<IntentSender?>(null)
    val permissionNeededForDelete: StateFlow<IntentSender?> = _permissionNeededForDelete.asStateFlow()

    init {
        loadFolders()
        loadPhotos()
    }

    fun loadFolders() {
        viewModelScope.launch {
            _folders.value = repository.getFolders()
        }
    }

    fun selectFolder(folder: Folder?) {
        _selectedFolder.value = folder
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _photos.value = repository.getPhotos(_selectedFolder.value?.id)
        }
    }

    fun swipeLeft(photo: Photo) {
        // Add to pending deletes instead of deleting immediately
        val currentPending = _pendingDeletes.value.toMutableList()
        currentPending.add(photo)
        _pendingDeletes.value = currentPending
        removePhotoFromList(photo)
    }

    fun swipeRight(photo: Photo) {
        // Keep the photo, just remove from view
        removePhotoFromList(photo)
    }

    fun confirmDeletes() {
        viewModelScope.launch {
            val photosToDelete = _pendingDeletes.value
            if (photosToDelete.isEmpty()) return@launch

            val intentSender = repository.deletePhotos(photosToDelete)
            if (intentSender != null) {
                _permissionNeededForDelete.value = intentSender
            } else {
                // Deletion successful or not needed
                _pendingDeletes.value = emptyList()
            }
        }
    }

    fun undoDelete() {
        // Restore the last deleted item or all?
        // Usually undo is for the last action.
        // But here we have a "Trash" queue.
        // Let's restore the last one added to pending.
        val currentPending = _pendingDeletes.value.toMutableList()
        if (currentPending.isNotEmpty()) {
            val photoToRestore = currentPending.removeLast()
            _pendingDeletes.value = currentPending
            
            // Add back to photos list at the top?
            val currentPhotos = _photos.value.toMutableList()
            currentPhotos.add(0, photoToRestore)
            _photos.value = currentPhotos
        }
    }

    private fun removePhotoFromList(photo: Photo) {
        val currentList = _photos.value.toMutableList()
        currentList.remove(photo)
        _photos.value = currentList
    }

    fun onPermissionResult(resultOk: Boolean) {
        if (resultOk) {
            // Permission granted, photos should be deleted by system (Android 11+)
            // or we might need to retry (Android 10).
            // For simplicity, we assume success and clear pending.
            _pendingDeletes.value = emptyList()
            // Reload to sync
            loadPhotos()
        }
        _permissionNeededForDelete.value = null
    }
}
