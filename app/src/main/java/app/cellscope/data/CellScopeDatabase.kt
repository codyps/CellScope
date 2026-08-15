package app.cellscope.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSample(sample: BatterySample)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGap(gap: TimelineGap): Long

    @Query("SELECT * FROM battery_samples WHERE wallTimeMs >= :sinceMs ORDER BY wallTimeMs")
    fun observeSamplesSince(sinceMs: Long): Flow<List<BatterySample>>

    @Query("SELECT * FROM timeline_gaps WHERE COALESCE(endedAtMs, :nowMs) >= :sinceMs ORDER BY startedAtMs")
    fun observeGapsSince(sinceMs: Long, nowMs: Long): Flow<List<TimelineGap>>

    @Query("SELECT * FROM battery_samples ORDER BY wallTimeMs DESC LIMIT 1")
    suspend fun latestSample(): BatterySample?

    @Query("SELECT * FROM timeline_gaps WHERE endedAtMs IS NULL AND reason = :reason ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun openGap(reason: String): TimelineGap?

    @Query("UPDATE timeline_gaps SET endedAtMs = :endedAtMs WHERE id = :id AND endedAtMs IS NULL")
    suspend fun finishGap(id: Long, endedAtMs: Long)

    @Query("SELECT COUNT(*) FROM battery_samples")
    fun observeSampleCount(): Flow<Long>

    @Query("DELETE FROM battery_samples")
    suspend fun deleteSamples()

    @Query("DELETE FROM timeline_gaps")
    suspend fun deleteGaps()
}

@Database(
    entities = [BatterySample::class, TimelineGap::class],
    version = 2,
    exportSchema = true,
)
abstract class CellScopeDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS battery_samples_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        wallTimeMs INTEGER NOT NULL,
                        elapsedRealtimeMs INTEGER NOT NULL,
                        levelPercent REAL,
                        chargeCounterUah INTEGER,
                        currentNowUa INTEGER,
                        currentAverageUa INTEGER,
                        energyCounterNwh INTEGER,
                        voltageMv INTEGER,
                        temperatureDeciC INTEGER,
                        status INTEGER NOT NULL,
                        plugSource INTEGER NOT NULL,
                        health INTEGER NOT NULL,
                        isPresent INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO battery_samples_new (
                        id, wallTimeMs, elapsedRealtimeMs, levelPercent, chargeCounterUah,
                        currentNowUa, currentAverageUa, energyCounterNwh, voltageMv,
                        temperatureDeciC, status, plugSource, health, isPresent
                    ) SELECT
                        id, wallTimeMs, elapsedRealtimeMs, levelPercent, chargeCounterUah,
                        currentNowUa, currentAverageUa, energyCounterNwh, voltageMv,
                        temperatureDeciC, status, plugSource, health, isPresent
                    FROM battery_samples
                """.trimIndent())
                db.execSQL("DROP TABLE battery_samples")
                db.execSQL("ALTER TABLE battery_samples_new RENAME TO battery_samples")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_samples_wallTimeMs ON battery_samples (wallTimeMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_samples_elapsedRealtimeMs ON battery_samples (elapsedRealtimeMs)")
                db.execSQL("DROP TABLE IF EXISTS recording_sessions")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS timeline_gaps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startedAtMs INTEGER NOT NULL,
                        endedAtMs INTEGER,
                        reason TEXT NOT NULL,
                        details TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_gaps_startedAtMs ON timeline_gaps (startedAtMs)")
            }
        }

        fun create(context: Context): CellScopeDatabase = Room.databaseBuilder(
            context,
            CellScopeDatabase::class.java,
            "cellscope.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}
