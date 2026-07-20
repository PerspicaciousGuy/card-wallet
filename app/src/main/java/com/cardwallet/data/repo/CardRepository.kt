package com.cardwallet.data.repo

import com.cardwallet.data.crypto.EncryptedPayload
import com.cardwallet.data.crypto.TimeSource
import com.cardwallet.data.crypto.VaultCipher
import com.cardwallet.data.db.CardDao
import com.cardwallet.data.db.CardRecord
import com.cardwallet.data.session.SessionState
import com.cardwallet.data.session.SessionStateHolder
import com.cardwallet.domain.Card
import com.cardwallet.domain.NewCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * A row after decryption. F3.3: a record that fails to decrypt or validate is
 * surfaced as [Unreadable] — never partially rendered, never silently dropped,
 * and never able to take the rest of the vault down with it.
 */
sealed interface VaultEntry {
    data class Readable(
        val card: Card,
    ) : VaultEntry

    data class Unreadable(
        val id: String,
    ) : VaultEntry
}

/**
 * The vault's data spine (plan §7 Phase 2): encrypt-on-write with a fresh nonce
 * per write, decrypt-and-validate-on-read, and a decrypt-once-per-unlock
 * in-memory cache (F3.4) that is cleared the instant the session locks. The
 * payload carries the full Card INCLUDING its id; a payload/row id mismatch
 * fails closed (defeats ciphertext row-swapping). Main-safe throughout.
 */
@Singleton
class CardRepository
    @Inject
    constructor(
        private val dao: CardDao,
        private val cipher: VaultCipher,
        private val session: SessionStateHolder,
        private val json: Json,
        private val time: TimeSource,
        @param:Named("io") private val io: CoroutineDispatcher,
        @param:Named("app") appScope: CoroutineScope,
    ) {
        private val _entries = MutableStateFlow<List<VaultEntry>?>(null)

        /** Decrypted session cache. Null = locked or not yet loaded (an honest
         *  Loading state for the UI); empty list = a genuinely empty vault. */
        val entries: StateFlow<List<VaultEntry>?> = _entries.asStateFlow()

        init {
            appScope.launch {
                session.state.collect { state ->
                    when (state) {
                        SessionState.Locked -> _entries.value = null
                        SessionState.Unlocked -> refresh()
                    }
                }
            }
        }

        suspend fun refresh() =
            withContext(io) {
                val key = requireKey()
                _entries.value = dao.getAll().map { decode(it, key) }
            }

        fun get(id: String): Card? =
            _entries.value
                .orEmpty()
                .filterIsInstance<VaultEntry.Readable>()
                .firstOrNull { it.card.id == id }
                ?.card

        suspend fun create(new: NewCard): Card =
            withContext(io) {
                require(new.title.isNotBlank()) { "card title must not be blank" }
                val now = nowIso()
                val card =
                    Card(
                        id = UUID.randomUUID().toString(),
                        type = new.type,
                        title = new.title.trim(),
                        fields = new.fields,
                        color = new.color,
                        isFavorite = new.isFavorite,
                        createdAt = now,
                        updatedAt = now,
                    )
                persist(card)
                card
            }

        suspend fun update(card: Card): Card =
            withContext(io) {
                require(card.title.isNotBlank()) { "card title must not be blank" }
                val updated = card.copy(title = card.title.trim(), updatedAt = nowIso())
                persist(updated)
                updated
            }

        suspend fun remove(id: String) =
            withContext(io) {
                requireKey()
                dao.deleteById(id)
                _entries.value = _entries.value.orEmpty().filterNot { it.idOrNull() == id }
            }

        /** F6.9: wipes every row and the cache. Intentionally works while the
         *  key state is being torn down — erasure must not require a key. */
        suspend fun eraseAll() =
            withContext(io) {
                dao.deleteAll()
                _entries.value = null
            }

        private suspend fun persist(card: Card) {
            val key = requireKey()
            val sealed = cipher.encrypt(key, json.encodeToString(Card.serializer(), card).encodeToByteArray())
            dao.upsert(
                CardRecord(
                    id = card.id,
                    schemaVersion = PAYLOAD_SCHEMA_VERSION,
                    createdAt = card.createdAt,
                    updatedAt = card.updatedAt,
                    nonce = Base64.getEncoder().encodeToString(sealed.nonce),
                    ciphertext = Base64.getEncoder().encodeToString(sealed.ciphertext),
                ),
            )
            _entries.value =
                _entries.value.orEmpty().filterNot { it.idOrNull() == card.id } +
                VaultEntry.Readable(card)
        }

        private fun decode(
            record: CardRecord,
            key: SecretKey,
        ): VaultEntry =
            try {
                val plain =
                    cipher.decrypt(
                        key,
                        EncryptedPayload(
                            Base64.getDecoder().decode(record.nonce),
                            Base64.getDecoder().decode(record.ciphertext),
                        ),
                    )
                val card = json.decodeFromString(Card.serializer(), plain.decodeToString())
                require(card.id == record.id) { "payload/row id mismatch" }
                VaultEntry.Readable(card)
            } catch (_: GeneralSecurityException) {
                VaultEntry.Unreadable(record.id)
            } catch (_: SerializationException) {
                VaultEntry.Unreadable(record.id)
            } catch (_: IllegalArgumentException) {
                VaultEntry.Unreadable(record.id)
            }

        /** Fail securely (plan §3 rule 7): no key ⇒ no operation, loudly. */
        private fun requireKey(): SecretKey = checkNotNull(session.dekOrNull()) { "vault is locked" }

        private fun nowIso(): String = Instant.ofEpochMilli(time.nowMillis()).toString()

        private fun VaultEntry.idOrNull(): String? =
            when (this) {
                is VaultEntry.Readable -> card.id
                is VaultEntry.Unreadable -> id
            }

        companion object {
            /** Version of the ENCRYPTED payload shape; bump on Card schema changes. */
            const val PAYLOAD_SCHEMA_VERSION = 1
        }
    }
