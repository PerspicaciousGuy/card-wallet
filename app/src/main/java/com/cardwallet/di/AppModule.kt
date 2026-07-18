package com.cardwallet.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cardwallet.data.crypto.TimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Named
import javax.inject.Singleton

private val Context.vaultMetaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vault_meta",
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideVaultMetaDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.vaultMetaDataStore

    @Provides
    fun provideTimeSource(): TimeSource = TimeSource { System.currentTimeMillis() }

    @Provides
    @Named("io")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
