package com.samae.photodrop.data

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoRepository(private val context: Context) {

    suspend fun getPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photos.add(Photo(id, contentUri, dateAdded))
            }
        }
        photos
    }

    suspend fun deletePhoto(photo: Photo): IntentSender? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(photo.uri, null, null)
            null // Deletion successful
        } catch (e: SecurityException) {
            val recoverableSecurityException = e as? RecoverableSecurityException
            if (recoverableSecurityException != null) {
                recoverableSecurityException.userAction.actionIntent.intentSender
            } else {
                // If it's not recoverable (e.g. Android 10 without scoped storage properly handled or other issues),
                // we might need to handle it differently.
                // For Android 11+ (R), we use MediaStore.createDeleteRequest
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(photo.uri)).intentSender
                } else {
                    null // Cannot handle
                }
            }
        }
    }
}
