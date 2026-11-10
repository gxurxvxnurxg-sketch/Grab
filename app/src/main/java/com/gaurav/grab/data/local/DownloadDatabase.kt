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
    val formatLabel: String,
    val sizeMb: Double,
    val progress: Int,
    val status: String,
    val filePath: String,
    val createdAt: Long
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Query("UPDATE downloads SET progress = :progress, status = :status, filePath = :filePath WHERE workId = :workId")
    suspend fun updateState(workId: String, progress: Int, status: String, filePath: String = "")
}

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class GrabDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
