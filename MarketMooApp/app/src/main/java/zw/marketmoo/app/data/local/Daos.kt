package zw.marketmoo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY date DESC, updatedAt DESC")
    fun observeAll(): Flow<List<RecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE records SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)
}

@Dao
interface ListingDao {
    @Query("SELECT * FROM listings ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ListingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(l: ListingEntity)

    @Query("UPDATE listings SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("SELECT * FROM listings WHERE photoPath IS NOT NULL AND status = 'SYNCED'")
    suspend fun withPendingPhotos(): List<ListingEntity>

    @Query("UPDATE listings SET photoPath = NULL WHERE id = :id")
    suspend fun clearPhotoPath(id: String)
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(op: SyncOp)

    @Query("SELECT * FROM sync_ops WHERE status != 'SYNCED' ORDER BY createdAt")
    suspend fun outstanding(): List<SyncOp>

    @Query("SELECT * FROM sync_ops ORDER BY createdAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<SyncOp>>

    @Query("UPDATE sync_ops SET status = :status, attempts = attempts + :inc, lastError = :err WHERE id = :id")
    suspend fun update(id: String, status: String, inc: Int, err: String)

    @Query("SELECT payload FROM sync_ops WHERE entity = 'listing' AND entityId = :entityId ORDER BY createdAt LIMIT 1")
    suspend fun payloadFor(entityId: String): String?

    @Query("SELECT COUNT(*) FROM sync_ops WHERE status != 'SYNCED'")
    fun observePendingCount(): Flow<Int>
}
