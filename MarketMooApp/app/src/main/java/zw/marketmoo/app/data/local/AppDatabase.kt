package zw.marketmoo.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import zw.marketmoo.app.security.KeystoreBackedPassphrase

@Database(entities = [RecordEntity::class, ListingEntity::class, SyncOp::class, FarmBoundaryEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
    abstract fun listings(): ListingDao
    abstract fun sync(): SyncDao
    abstract fun boundary(): BoundaryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE listings ADD COLUMN photoPath TEXT") }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `farm_boundaries` (`id` TEXT NOT NULL, `geojson` TEXT NOT NULL, `areaHa` REAL NOT NULL, `centroidLat` REAL NOT NULL, `centroidLon` REAL NOT NULL, `source` TEXT NOT NULL, `status` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun create(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(KeystoreBackedPassphrase.get(context))
            return Room.databaseBuilder(context, AppDatabase::class.java, "marketmoo.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
