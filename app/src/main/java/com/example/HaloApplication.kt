package com.example

import android.app.Application
import android.os.StrictMode
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
        com.halo.app.di.ManualDependencyGraph.init(this)
        
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
        
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "braintool_db"
        ).build()
        memoryRepository = MemoryRepository(database.memoryDao())
    }
}
