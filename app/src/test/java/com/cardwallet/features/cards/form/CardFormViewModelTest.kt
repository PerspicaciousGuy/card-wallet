package com.cardwallet.features.cards.form

import androidx.lifecycle.SavedStateHandle
import com.cardwallet.domain.CardType
import com.cardwallet.features.cards.CardVmTestHarness
import com.cardwallet.features.cards.CardVmTestHarness.Companion.newCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardFormViewModelTest {
    private val harness = CardVmTestHarness()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun editing(viewModel: CardFormViewModel) = viewModel.state.value as CardFormUiState.Editing

    @Test
    fun `new card starts from the payment template`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            val viewModel = CardFormViewModel(repo, SavedStateHandle())

            val state = editing(viewModel)
            assertFalse(state.isEdit)
            assertEquals(CardType.PAYMENT, state.type)
            assertTrue(state.fields.any { it.label == "Number" && it.isMasked })
            assertTrue(state.fields.any { it.label == "CVV" && it.isMasked })
        }

    @Test
    fun `switching type re-templates only while fields are untouched`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            val viewModel = CardFormViewModel(repo, SavedStateHandle())

            viewModel.onTypeSelect(CardType.LOYALTY)
            assertTrue(editing(viewModel).fields.any { it.label == "Member ID" })

            viewModel.onFieldValueChange(0, "12345")
            viewModel.onTypeSelect(CardType.TRAVEL)
            val state = editing(viewModel)
            assertEquals(CardType.TRAVEL, state.type)
            assertEquals("12345", state.fields[0].value)
        }

    @Test
    fun `save validates title and at least one field value`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            val viewModel = CardFormViewModel(repo, SavedStateHandle())

            viewModel.onSave()
            advanceUntilIdle()
            val state = editing(viewModel)
            assertTrue(state.hasTitleError)
            assertTrue(state.hasFieldsError)
            assertFalse(state.isSaved)
            assertTrue(harness.dao.rows.isEmpty())
        }

    @Test
    fun `valid save creates the card and drops blank fields`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            val viewModel = CardFormViewModel(repo, SavedStateHandle())

            viewModel.onTitleChange("HDFC Debit")
            viewModel.onFieldValueChange(0, "4111 1111")
            viewModel.onSave()
            advanceUntilIdle()

            assertTrue(editing(viewModel).isSaved)
            assertEquals(1, harness.dao.rows.size)
            val saved =
                repo.entries.value
                    .orEmpty()
                    .filterIsInstance<com.cardwallet.data.repo.VaultEntry.Readable>()
                    .single()
                    .card
            assertEquals("HDFC Debit", saved.title)
            // Template ships 5 payment fields; only the one with a value survives.
            assertEquals(1, saved.fields.size)
            assertEquals("Number", saved.fields.single().label)
        }

    @Test
    fun `edit mode loads the card and save updates it`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            val created = repo.create(newCard("Original"))
            val viewModel =
                CardFormViewModel(repo, SavedStateHandle(mapOf("cardId" to created.id)))
            advanceUntilIdle()

            val loaded = editing(viewModel)
            assertTrue(loaded.isEdit)
            assertEquals("Original", loaded.title)

            viewModel.onTitleChange("Renamed")
            viewModel.onSave()
            advanceUntilIdle()

            assertTrue(editing(viewModel).isSaved)
            assertEquals("Renamed", repo.get(created.id)?.title)
        }
}
