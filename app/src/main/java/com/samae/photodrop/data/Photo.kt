package com.samae.photodrop.data

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long
)
