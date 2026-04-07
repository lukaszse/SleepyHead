package com.example.androidapp.framework.bootstrap

import android.app.Application

/**
 * Custom [Application] subclass that holds [AppDependencies] as a singleton.
 *
 * This ensures the composition root (and the [HrViewModel] it creates)
 * survives Activity recreation. When a [ForegroundService] keeps the process
 * alive during sleep, the ViewModel's coroutines continue running because
 * their scope is tied to the process lifetime — not the Activity lifecycle.
 *
 * Registered in `AndroidManifest.xml` via `android:name`.
 */
class SleepyHeadApplication : Application() {

    /**
     * Application-scoped composition root.
     * Initialised once in [onCreate] and shared by all components.
     */
    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        dependencies = AppDependencies(applicationContext)
    }
}

