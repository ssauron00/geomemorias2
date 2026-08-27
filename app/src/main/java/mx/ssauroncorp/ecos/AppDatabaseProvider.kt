package mx.ssauroncorp.ecos

import android.content.Context
import androidx.room.Room

/**
 * AppDatabaseProvider — singleton de Room (device-only, sin nube).
 * Centraliza el acceso a la BD para evitar múltiples instancias.
 */
object AppDatabaseProvider {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, AppDatabase.NAME
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
}
