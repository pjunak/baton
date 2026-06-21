package eu.junak.baton

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/** Hilt application root. Plants Timber in debug builds. */
@HiltAndroidApp
class BatonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
