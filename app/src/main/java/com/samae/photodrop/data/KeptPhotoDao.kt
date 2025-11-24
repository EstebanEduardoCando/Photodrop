package com.samae.photodrop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KeptPhotoDao {
    @Query("SELECT * FROM kept_photos")
    suspend fun getAll(): List<KeptPhoto>

    @Query("SELECT id FROM kept_photos")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: KeptPhoto)

    @Query("DELETE FROM kept_photos WHERE id = :id")
    suspend fun delete(id: Long)
}
