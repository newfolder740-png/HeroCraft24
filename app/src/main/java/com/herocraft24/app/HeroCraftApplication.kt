package com.herocraft24.app

import android.app.Application
import com.herocraft24.core.data.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HeroCraftApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        preloadContentInBackground()
    }

    /**
     * Warms the shared content cache at app launch so opening the bestiary
     * later doesn't have to read 600+ monster JSON files from assets.
     */
    private fun preloadContentInBackground() {
        val repository = ContentRepository.get(this)
        applicationScope.launch {
            repository.initialize()
            for (monsterId in repository.getMonsterIds()) {
                repository.getMonster(monsterId)
            }
        }
    }
}
