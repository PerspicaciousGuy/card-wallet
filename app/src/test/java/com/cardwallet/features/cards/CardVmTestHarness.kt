package com.cardwallet.features.cards

import com.cardwallet.data.crypto.TimeSource
import com.cardwallet.data.crypto.VaultCipher
import com.cardwallet.data.db.CardDao
import com.cardwallet.data.db.CardRecord
import com.cardwallet.data.repo.CardRepository
import com.cardwallet.data.session.SessionStateHolder
import com.cardwallet.domain.CardColorToken
import com.cardwallet.domain.CardField
import com.cardwallet.domain.CardType
import com.cardwallet.domain.NewCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.Json

/** Shared wiring for ViewModel tests: a REAL repository over fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class CardVmTestHarness {
    class FakeDao : CardDao {
        val rows = LinkedHashMap<String, CardRecord>()

        override suspend fun getAll(): List<CardRecord> = rows.values.toList()

        override suspend fun upsert(record: CardRecord) {
            rows[record.id] = record
        }

        override suspend fun deleteById(id: String) {
            rows.remove(id)
        }

        override suspend fun deleteAll() {
            rows.clear()
        }
    }

    class FakeClock(
        var now: Long = 1_000_000L,
    ) : TimeSource {
        override fun nowMillis() = now
    }

    val dao = FakeDao()
    val clock = FakeClock()
    val session = SessionStateHolder(clock)

    fun unlockedRepository(scope: TestScope): CardRepository {
        val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        val repository =
            CardRepository(
                dao = dao,
                cipher = VaultCipher(),
                session = session,
                json = Json,
                time = clock,
                io = dispatcher,
                appScope = CoroutineScope(scope.backgroundScope.coroutineContext + dispatcher),
            )
        session.unlock(ByteArray(DEK_SIZE) { (it + 1).toByte() })
        scope.advanceUntilIdle()
        return repository
    }

    companion object {
        const val DEK_SIZE = 32

        fun newCard(
            title: String,
            type: CardType = CardType.PAYMENT,
            isFavorite: Boolean = false,
            fields: List<CardField> =
                listOf(CardField(label = "Number", value = "4111", isMasked = true)),
        ) = NewCard(
            type = type,
            title = title,
            fields = fields,
            color = CardColorToken.BLUE,
            isFavorite = isFavorite,
        )
    }
}
