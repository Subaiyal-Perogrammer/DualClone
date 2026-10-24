package com.dualclone.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * One clone record. Each record owns exactly one BlackBox virtual user
 * ([engineUserId]); that user's virtual data directory is the clone's own,
 * separate from every other clone and from the original app.
 */
@Entity(tableName = "clones")
data class CloneRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val customName: String? = null,
    val engineUserId: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayName: String get() = customName?.takeIf { it.isNotBlank() } ?: label
}

@Dao
interface CloneDao {
    @Query("SELECT * FROM clones ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CloneRecord>>

    @Query("SELECT * FROM clones ORDER BY createdAt ASC")
    suspend fun all(): List<CloneRecord>

    @Query("SELECT * FROM clones WHERE id = :id")
    suspend fun byId(id: Long): CloneRecord?

    @Query("SELECT COALESCE(MAX(engineUserId), 0) FROM clones")
    suspend fun maxUserId(): Int

    @Insert suspend fun insert(record: CloneRecord): Long
    @Update suspend fun update(record: CloneRecord)
    @Delete suspend fun delete(record: CloneRecord)
}

@Database(entities = [CloneRecord::class], version = 1, exportSchema = false)
abstract class CloneDatabase : RoomDatabase() {
    abstract fun cloneDao(): CloneDao

    companion object {
        @Volatile private var instance: CloneDatabase? = null
        fun get(context: Context): CloneDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CloneDatabase::class.java, "dualclone.db")
                .build().also { instance = it }
        }
    }
}
