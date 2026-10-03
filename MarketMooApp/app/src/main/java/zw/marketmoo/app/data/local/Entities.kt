package zw.marketmoo.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus { PENDING, SYNCED, FAILED }

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey val id: String,
    val type: String,
    val animal: String,
    val date: String,
    val cost: Double?,
    val notes: String,
    val status: String = SyncStatus.PENDING.name,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "listings")
data class ListingEntity(
    @PrimaryKey val id: String,
    val species: String,
    val breed: String,
    val sex: String,
    val ageMonths: Int,
    val qty: Int,
    val priceUsd: Double,
    val ward: String,
    /** Precise pin: stays on the phone and is only sent to the server for the owner. */
    val lat: Double,
    val lon: Double,
    val phone: String,
    /** Resized photo kept on the phone until it can be uploaded; the upload happens after the text record syncs. */
    val photoPath: String? = null,
    val own: Boolean = true,
    val status: String = SyncStatus.PENDING.name,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** The farmer's outline (one per farmer). The pin used by Farm Insights is the centre of this shape. Stays private. */
@Entity(tableName = "farm_boundaries")
data class FarmBoundaryEntity(
    @PrimaryKey val id: String,
    val geojson: String,
    val areaHa: Double,
    val centroidLat: Double,
    val centroidLon: Double,
    val source: String,
    val status: String = SyncStatus.PENDING.name,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Operation log used by the sync worker (idempotent: the id is a client-generated UUID). */
@Entity(tableName = "sync_ops")
data class SyncOp(
    @PrimaryKey val id: String,
    val entity: String,
    val entityId: String,
    val payload: String,
    val status: String = SyncStatus.PENDING.name,
    val attempts: Int = 0,
    val lastError: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
