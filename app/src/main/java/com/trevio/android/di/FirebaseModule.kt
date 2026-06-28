package com.trevio.android.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        val auth = FirebaseAuth.getInstance()
        if (com.trevio.android.BuildConfig.DEBUG) {
            try {
                auth.useEmulator("10.0.2.2", 9099)
            } catch (_: Exception) { }
        }
        return auth
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        if (com.trevio.android.BuildConfig.DEBUG) {
            try {
                firestore.useEmulator("10.0.2.2", 8080)
                firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(false)
                    .build()
            } catch (_: Exception) { }
        }
        return firestore
    }

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions {
        val functions = FirebaseFunctions.getInstance()
        if (com.trevio.android.BuildConfig.DEBUG) {
            try {
                functions.useEmulator("10.0.2.2", 5001)
            } catch (_: Exception) { }
        }
        return functions
    }

    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
