package com.gaurav.grab.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workId: String, val url: String, val title: String, val platform: String, val thumbnail: String,
    val formatLabel: String, val sizeMb: Double, val progress: Int, val status: String, val filePath: String,
    val createdAt: Long, val speedKbps: Double = 0.0, val etaSec: Int = -1
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun observeAll(): Flow<List<DownloadEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: DownloadEntity): Long
    @Query("UPDATE downloads SET progress = :progress, status = :status, speedKbps = :speedKbps, etaSec = :etaSec, filePath = CASE WHEN :filePath = '' THEN filePath ELSE :filePath END WHERE workId = :workId")
    suspend fun updateState(workId: String, progress: Int, status: String, filePath: String = "", speedKbps: Double = 0.0, etaSec: Int = -1)
    @Query("UPDATE downloads SET status = 'FAILED' WHERE status = 'RUNNING'") suspend fun markOrphansFailed()
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM downloads WHERE status != 'RUNNING'") suspend fun clearAll()
}

@Database(entities = [DownloadEntity::class], version = 2, exportSchema = false)
abstract class GrabDatabase : RoomDatabase() { abstract fun downloadDao(): DownloadDao }
