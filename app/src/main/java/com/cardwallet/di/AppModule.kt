package com.cardwallet.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.cardwallet.data.crypto.TimeSource
import com.cardwallet.data.db.CardDao
import com.cardwallet.data.db.VaultDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
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

    /** App-lifetime scope for singletons that observe session state. */
    @Provides
    @Singleton
    @Named("app")
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideVaultDatabase(
        @ApplicationContext context: Context,
    ): VaultDatabase = Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db").build()

    @Provides
    fun provideCardDao(database: VaultDatabase): CardDao = database.cardDao()

    /** Strict by default: unknown keys in a decrypted payload are corruption, not noise. */
    @Provides
    @Singleton
    fun provideJson(): Json = Json
}
