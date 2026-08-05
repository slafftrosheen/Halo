package com.example

import android.app.Application
import androidx.room.Room
import com.halo.app.data.AppDatabase
import com.halo.app.data.MemoryRepository

class HaloApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var memoryRepository: MemoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "braintool_db"
        ).build()
        memoryRepository = MemoryRepository(database.memoryDao())
    }
}
