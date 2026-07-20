package com.cardwallet.data.repo

import com.cardwallet.data.crypto.TimeSource
import com.cardwallet.data.crypto.VaultCipher
import com.cardwallet.data.db.CardDao
import com.cardwallet.data.db.CardRecord
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

private class FakeCardDao : CardDao {
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

private class FakeClock(
    var now: Long = 1_000_000L,
) : TimeSource {
    override fun nowMillis() = now
}

@OptIn(ExperimentalCoroutinesApi::class)
class CardRepositoryTest {
    private val dao = FakeCardDao()
    private val clock = FakeClock()
    private val session = SessionStateHolder(clock)

    // UnconfinedTestDispatcher: the documented pattern for components with
    // long-lived StateFlow collectors — emissions are delivered eagerly, so
    // lock()/unlock() effects are observable without dispatcher archaeology.
    private fun TestScope.repository(): CardRepository {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return CardRepository(
            dao = dao,
            cipher = VaultCipher(),
            session = session,
            json = Json,
            time = clock,
            io = dispatcher,
            appScope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
        )
    }

    private fun unlockedRepo(scope: TestScope): CardRepository {
        val repo = scope.repository()
        session.unlock(ByteArray(DEK_SIZE) { (it + 1).toByte() })
        scope.advanceUntilIdle()
        return repo
    }

    private fun paymentCard() =
        NewCard(
            type = CardType.PAYMENT,
            title = "HDFC Debit",
            fields =
                listOf(
                    CardField(label = "Number", value = "4111 1111 1111 1111", isMasked = true),
                    CardField(label = "CVV", value = "123", isMasked = true),
                ),
            color = CardColorToken.BLUE,
        )

    @Test
    fun `create round-trips through real encryption into the cache`() =
        runTest {
            val repo = unlockedRepo(this)
            val created = repo.create(paymentCard())
            advanceUntilIdle()

            val entry =
                repo.entries.value
                    .orEmpty()
                    .single() as VaultEntry.Readable
            assertEquals(created, entry.card)
            assertEquals(
                "4111 1111 1111 1111",
                repo
                    .get(created.id)
                    ?.fields
                    ?.first()
                    ?.value,
            )
        }

    @Test
    fun `stored row contains no plaintext card data`() =
        runTest {
            val repo = unlockedRepo(this)
            val created = repo.create(paymentCard())

            val row = dao.rows.getValue(created.id)
            val rawCiphertext = Base64.getDecoder().decode(row.ciphertext).decodeToString()
            listOf("HDFC", "4111", "123", "Number", "PAYMENT").forEach { marker ->
                assertFalse("'$marker' leaked into the row", rawCiphertext.contains(marker))
                assertFalse("'$marker' leaked into row metadata", row.nonce.contains(marker))
            }
        }

    @Test
    fun `update re-encrypts with a fresh nonce and bumps updatedAt`() =
        runTest {
            val repo = unlockedRepo(this)
            val created = repo.create(paymentCard())
            val nonceBefore = dao.rows.getValue(created.id).nonce

            clock.now += ONE_MINUTE_MILLIS
            val updated = repo.update(created.copy(title = "HDFC Platinum"))

            val row = dao.rows.getValue(created.id)
            assertNotEquals(nonceBefore, row.nonce)
            assertNotEquals(created.updatedAt, updated.updatedAt)
            assertEquals("HDFC Platinum", repo.get(created.id)?.title)
        }

    @Test
    fun `remove deletes the row and the cache entry`() =
        runTest {
            val repo = unlockedRepo(this)
            val created = repo.create(paymentCard())

            repo.remove(created.id)

            assertTrue(dao.rows.isEmpty())
            assertNull(repo.get(created.id))
        }

    @Test
    fun `locking clears the cache and unlocking reloads it from the database`() =
        runTest {
            val repo = unlockedRepo(this)
            val created = repo.create(paymentCard())

            session.lock()
            advanceUntilIdle()
            assertNull(repo.entries.value)

            session.unlock(ByteArray(DEK_SIZE) { (it + 1).toByte() })
            advanceUntilIdle()
            assertEquals(
                created,
                (
                    repo.entries.value
                        .orEmpty()
                        .single() as VaultEntry.Readable
                ).card,
            )
        }

    @Test
    fun `tampered ciphertext yields an Unreadable entry without sinking the vault`() =
        runTest {
            val repo = unlockedRepo(this)
            val healthy = repo.create(paymentCard())
            val victim = repo.create(paymentCard().copy(title = "Passport"))

            val row = dao.rows.getValue(victim.id)
            val corrupt = Base64.getDecoder().decode(row.ciphertext)
            corrupt[0] = (corrupt[0].toInt() xor 1).toByte()
            dao.rows[victim.id] = row.copy(ciphertext = Base64.getEncoder().encodeToString(corrupt))

            repo.refresh()
            advanceUntilIdle()

            val entries = repo.entries.value.orEmpty()
            assertTrue(entries.contains(VaultEntry.Unreadable(victim.id)))
            assertTrue(entries.contains(VaultEntry.Readable(healthy)))
        }

    @Test
    fun `a ciphertext swapped between rows fails closed`() =
        runTest {
            val repo = unlockedRepo(this)
            val a = repo.create(paymentCard())
            val b = repo.create(paymentCard().copy(title = "Passport"))

            val rowA = dao.rows.getValue(a.id)
            val rowB = dao.rows.getValue(b.id)
            dao.rows[b.id] = rowB.copy(nonce = rowA.nonce, ciphertext = rowA.ciphertext)

            repo.refresh()
            advanceUntilIdle()

            assertTrue(
                repo.entries.value
                    .orEmpty()
                    .contains(VaultEntry.Unreadable(b.id)),
            )
        }

    @Test
    fun `operations while locked fail loudly`() =
        runTest {
            val repo = repository()
            advanceUntilIdle()
            val thrown =
                try {
                    repo.create(paymentCard())
                    null
                } catch (e: IllegalStateException) {
                    e
                }
            assertTrue("expected IllegalStateException while locked", thrown != null)
        }

    @Test
    fun `blank titles are rejected at the repository boundary`() =
        runTest {
            val repo = unlockedRepo(this)
            val thrown =
                try {
                    repo.create(paymentCard().copy(title = "   "))
                    null
                } catch (e: IllegalArgumentException) {
                    e
                }
            assertTrue("expected IllegalArgumentException for blank title", thrown != null)
        }

    private companion object {
        const val DEK_SIZE = 32
        const val ONE_MINUTE_MILLIS = 60_000L
    }
}
