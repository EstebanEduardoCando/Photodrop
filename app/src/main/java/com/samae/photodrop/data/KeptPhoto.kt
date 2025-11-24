package com.samae.photodrop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kept_photos")
data class KeptPhoto(
    @PrimaryKey val id: Long, // MediaStore ID
    val uri: String,
    val dateAdded: Long
)
