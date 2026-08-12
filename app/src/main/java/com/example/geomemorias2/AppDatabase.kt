package com.example.geomemorias2

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Reminder::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "geomemorias2.db"
    }
}
