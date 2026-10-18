package org.batteryflow

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "samples")
data class Sample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val level: Int,
    val tempC: Float,
    val voltageMv: Int,
    val currentMa: Int,      // + charging, - discharging
    val status: Int,
    val health: Int,
    val plugged: Int,
    val screenOn: Boolean,
    val chargeMah: Int       // fuel-gauge remaining charge
)

@Dao
interface SampleDao {
    @Insert suspend fun insert(s: Sample)
    @Query("SELECT * FROM samples WHERE ts >= :since ORDER BY ts") fun since(since: Long): Flow<List<Sample>>
    @Query("SELECT * FROM samples ORDER BY ts DESC LIMIT 1") fun latest(): Flow<Sample?>
    @Query("DELETE FROM samples WHERE ts < :before") suspend fun prune(before: Long)
}

@Database(entities = [Sample::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): SampleDao
    companion object {
        @Volatile private var i: AppDb? = null
        fun get(c: Context): AppDb = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "battery.db").build().also { i = it }
        }
    }
}
