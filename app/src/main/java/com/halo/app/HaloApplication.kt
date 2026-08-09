package com.halo.app

import android.app.Application
import com.halo.app.di.ManualDependencyGraph

class HaloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the dependency graph which is our composition root
        ManualDependencyGraph.init(this)
    }
}
