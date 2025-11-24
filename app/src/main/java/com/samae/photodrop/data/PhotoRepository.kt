package com.samae.photodrop.data

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoRepository(private val context: Context) {

    suspend fun getFolders(): List<Folder> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<Folder>()
        val folderMap = mutableMapOf<String, Pair<String, Int>>()

        // Query Images
        queryFolders(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, folderMap)
        
        // Query Videos
        queryFolders(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, folderMap)

        folderMap.forEach { (id, pair) ->
            folders.add(Folder(id, pair.first, pair.second))
        }
        folders.sortedBy { it.name }
    }

    private fun queryFolders(collection: Uri, folderMap: MutableMap<String, Pair<String, Int>>) {
        val projection = arrayOf(
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(bucketIdColumn) ?: continue
                    val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"

                    val current = folderMap[bucketId]
                    if (current == null) {
                        folderMap[bucketId] = bucketName to 1
                    } else {
                        folderMap[bucketId] = bucketName to (current.second + 1)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getPhotos(bucketId: String? = null): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()

        // Query Images
        photos.addAll(queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, bucketId, false))
        
        // Query Videos
        photos.addAll(queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, bucketId, true))

        // Sort by date added descending
        photos.sortedByDescending { it.dateAdded }
    }

    private fun queryMedia(collection: Uri, bucketId: String?, isVideo: Boolean): List<Photo> {
        val photos = mutableListOf<Photo>()
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        
        if (isVideo) {
            projection.add(MediaStore.Video.Media.DURATION)
        }

        val selection = if (bucketId != null) "${MediaStore.MediaColumns.BUCKET_ID} = ?" else null
        val selectionArgs = if (bucketId != null) arrayOf(bucketId) else null
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val durationColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val size = cursor.getLong(sizeColumn)
                    val name = cursor.getString(nameColumn) ?: ""
                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    
                    photos.add(Photo(id, contentUri, dateAdded, isVideo, duration, size, name))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return photos
    }

    suspend fun deletePhotos(photos: List<Photo>): IntentSender? = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) return@withContext null
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uris = photos.map { it.uri }
                MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
            } else {
                for (photo in photos) {
                    context.contentResolver.delete(photo.uri, null, null)
                }
                null
            }
        } catch (e: SecurityException) {
            val recoverableSecurityException = e as? RecoverableSecurityException
            if (recoverableSecurityException != null) {
                recoverableSecurityException.userAction.actionIntent.intentSender
            } else {
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    MediaStore.createDeleteRequest(context.contentResolver, photos.map { it.uri }).intentSender
                } else {
                    null
                }
            }
        }
    }
}
