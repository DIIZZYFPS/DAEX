package com.daex.android.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class DaexDatabase : RoomDatabase() {
    abstract fun daexDao(): DaexDao

    companion object {
        @Volatile
        private var INSTANCE: DaexDatabase? = null

        fun getDatabase(context: Context): DaexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DaexDatabase::class.java,
                    "daex_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
