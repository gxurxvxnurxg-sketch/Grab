package com.gaurav.grab.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workId: String,
    val url: String,
    val title: String,
    val platform: String,
    val thumbnail: String,
    val formatId: String,
    val formatLabel: String,
    val mediaType: String,
    val sizeMb: Double,
    val downloadedMb: Double,
    val speedText: String,
    val etaText: String,
    val progress: Int,
    val status: String,
    val filePath: String,
    val createdAt: Long
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Query("UPDATE downloads SET progress = :progress, downloadedMb = :dlMb, speedText = :speed, etaText = :eta, status = :status, filePath = :filePath WHERE workId = :workId")
    suspend fun updateLiveProgress(workId: String, progress: Int, dlMb: Double, speed: String, eta: String, status: String, filePath: String = "")

    @Query("UPDATE downloads SET workId = :newWorkId, status = :status, speedText = :speed WHERE id = :id")
    suspend fun updateWorkForResume(id: Long, newWorkId: String, status: String, speed: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}

@Database(entities = [DownloadEntity::class], version = 2, exportSchema = false)
abstract class GrabDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
