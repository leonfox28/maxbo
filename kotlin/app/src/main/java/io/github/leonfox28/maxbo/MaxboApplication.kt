package io.github.leonfox28.maxbo

import android.app.Application

class MaxboApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.create(this)
    }
}
