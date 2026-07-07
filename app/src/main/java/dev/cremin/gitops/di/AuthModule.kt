package dev.cremin.gitops.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.cremin.gitops.data.auth.AuthStateStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthStateStore(@ApplicationContext context: Context): AuthStateStore =
        AuthStateStore(context)
}
