package com.samae.photodrop.data

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long,
    val isVideo: Boolean = false,
    val duration: Long = 0,
    val size: Long = 0,
    val displayName: String = ""
)
