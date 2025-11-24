package com.samae.photodrop.ui

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _currentPhotoIndex = MutableStateFlow(0)
    val currentPhotoIndex: StateFlow<Int> = _currentPhotoIndex.asStateFlow()

    private val _permissionNeededForDelete = MutableStateFlow<IntentSender?>(null)
    val permissionNeededForDelete: StateFlow<IntentSender?> = _permissionNeededForDelete.asStateFlow()

    init {
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _photos.value = repository.getPhotos()
        }
    }

    fun swipeLeft(photo: Photo) {
        viewModelScope.launch {
            val intentSender = repository.deletePhoto(photo)
            if (intentSender != null) {
                _permissionNeededForDelete.value = intentSender
            } else {
                // Deletion successful or not needed (e.g. app owns file)
                removePhotoFromList(photo)
            }
        }
    }

    fun swipeRight(photo: Photo) {
        // Keep the photo, just remove from view
        removePhotoFromList(photo)
    }

    private fun removePhotoFromList(photo: Photo) {
        val currentList = _photos.value.toMutableList()
        currentList.remove(photo)
        _photos.value = currentList
    }

    fun onPermissionResult(resultOk: Boolean) {
        if (resultOk) {
            // If permission granted, we assume the system handled the deletion or we might need to retry.
            // For MediaStore.createDeleteRequest, the system handles it.
            // We should remove the photo from the list if we are sure.
            // However, to be safe, we can just reload or remove the top photo.
            // Since we don't track *which* photo was pending deletion easily here without more state,
            // let's assume the current top photo was the one.
             val currentList = _photos.value
             if (currentList.isNotEmpty()) {
                 // The photo that triggered this was likely the one we tried to delete.
                 // But wait, swipeLeft is called, then we get intent.
                 // If we remove it immediately, the UI updates.
                 // If we wait, the user sees the dialog.
                 // Let's remove it from the list *after* confirmation for better UX?
                 // Or maybe we already removed it?
                 // In swipeLeft, if intentSender != null, we set state. We did NOT remove it yet.
                 // So now we remove it.
                 // But we need to know WHICH one.
                 // For simplicity, let's just reload photos to sync with source of truth.
                 loadPhotos()
             }
        }
        _permissionNeededForDelete.value = null
    }
}
