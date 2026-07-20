package com.cardwallet.features.cards.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.cardwallet.data.clipboard.SecretClipboard
import com.cardwallet.features.cards.CardVmTestHarness
import com.cardwallet.features.cards.CardVmTestHarness.Companion.newCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardDetailViewModelTest {
    private val harness = CardVmTestHarness()

    private class RecordingClipboard : SecretClipboard {
        val copies = mutableListOf<Pair<String, String>>()
        var clearCount = 0

        override fun copy(
            label: String,
            value: String,
        ) {
            copies += label to value
        }

        override fun clearNow() {
            clearCount++
        }
    }

    private val clipboard = RecordingClipboard()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the card and toggles per-field reveal`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            val created = repo.create(newCard("Bank card"))
            val viewModel =
                CardDetailViewModel(
                    repo,
                    clipboard,
                    SavedStateHandle(mapOf("cardId" to created.id)),
                )

            viewModel.state.test {
                val loaded = expectMostRecentItem() as CardDetailUiState.Loaded
                assertEquals("Bank card", loaded.card.title)
                assertTrue(loaded.revealedIndices.isEmpty())

                viewModel.toggleReveal(0)
                assertEquals(
                    setOf(0),
                    (expectMostRecentItem() as CardDetailUiState.Loaded).revealedIndices,
                )

                viewModel.toggleReveal(0)
                assertTrue(
                    (expectMostRecentItem() as CardDetailUiState.Loaded).revealedIndices.isEmpty(),
                )
            }
        }

    @Test
    fun `copy sends the true value to the clipboard without revealing`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            val created = repo.create(newCard("Bank card"))
            val viewModel =
                CardDetailViewModel(
                    repo,
                    clipboard,
                    SavedStateHandle(mapOf("cardId" to created.id)),
                )

            viewModel.state.test {
                expectMostRecentItem()
                // Copying emits NO state change — the value must not reveal.
                viewModel.copyField(0)
                assertEquals(listOf("Number" to "4111"), clipboard.copies)
                val state = viewModel.state.value as CardDetailUiState.Loaded
                assertTrue(state.revealedIndices.isEmpty())
            }
        }

    @Test
    fun `confirmed delete removes the card and lands on Missing`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            val created = repo.create(newCard("Bank card"))
            val viewModel =
                CardDetailViewModel(
                    repo,
                    clipboard,
                    SavedStateHandle(mapOf("cardId" to created.id)),
                )

            viewModel.state.test {
                expectMostRecentItem()
                viewModel.requestDelete()
                assertTrue(
                    (expectMostRecentItem() as CardDetailUiState.Loaded).isConfirmingDelete,
                )

                viewModel.confirmDelete()
                assertEquals(CardDetailUiState.Missing, expectMostRecentItem())
                assertTrue(harness.dao.rows.isEmpty())
            }
        }
}
