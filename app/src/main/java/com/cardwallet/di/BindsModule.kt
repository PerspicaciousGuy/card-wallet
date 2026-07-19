package com.cardwallet.di

import com.cardwallet.data.clipboard.SecretClipboard
import com.cardwallet.data.clipboard.SensitiveClipboard
import com.cardwallet.data.crypto.DataStorePinAttemptStore
import com.cardwallet.data.crypto.PinAttemptStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds
    @Singleton
    abstract fun bindPinAttemptStore(impl: DataStorePinAttemptStore): PinAttemptStore

    @Binds
    @Singleton
    abstract fun bindSecretClipboard(impl: SensitiveClipboard): SecretClipboard
}
