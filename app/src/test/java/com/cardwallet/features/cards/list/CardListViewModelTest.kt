package com.cardwallet.features.cards.list

import app.cash.turbine.test
import com.cardwallet.domain.CardField
import com.cardwallet.domain.CardType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardListViewModelTest {
    private val harness = CardVmTestHarness()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `favorites come first, then most recently updated`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            repo.create(newCard("Old plain"))
            harness.clock.now += CLOCK_STEP_MILLIS
            repo.create(newCard("New plain"))
            harness.clock.now += CLOCK_STEP_MILLIS
            repo.create(newCard("Favorite", isFavorite = true))

            val viewModel = CardListViewModel(repo)
            viewModel.state.test {
                val content = mostRecentContent()
                assertEquals(
                    listOf("Favorite", "New plain", "Old plain"),
                    content.cards.map { it.title },
                )
            }
        }

    @Test
    fun `type filter narrows the list`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            repo.create(newCard("Bank card", type = CardType.PAYMENT))
            repo.create(newCard("Passport", type = CardType.TRAVEL))

            val viewModel = CardListViewModel(repo)
            viewModel.state.test {
                viewModel.onFilterChange(CardType.TRAVEL)
                assertEquals(listOf("Passport"), mostRecentContent().cards.map { it.title })
            }
        }

    @Test
    fun `search matches titles and field labels but never values`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            repo.create(
                newCard(
                    "Bank card",
                    fields = listOf(CardField(label = "IBAN", value = "SECRET-777", isMasked = true)),
                ),
            )

            val viewModel = CardListViewModel(repo)
            viewModel.state.test {
                viewModel.onQueryChange("iban")
                assertEquals(1, mostRecentContent().cards.size)

                viewModel.onQueryChange("SECRET-777")
                assertTrue(mostRecentContent().cards.isEmpty())
            }
        }

    @Test
    fun `empty vault and empty search results are distinguished`() =
        runTest {
            val repo = harness.unlockedRepository(this)
            val viewModel = CardListViewModel(repo)
            viewModel.state.test {
                assertTrue(mostRecentContent().isVaultEmpty)

                repo.create(newCard("Bank card"))
                viewModel.onQueryChange("zzz")
                val content = mostRecentContent()
                assertTrue(content.cards.isEmpty())
                assertFalse(content.isVaultEmpty)
            }
        }

    private fun app.cash.turbine.TurbineTestContext<CardListUiState>.mostRecentContent(): CardListUiState.Content =
        expectMostRecentItem() as CardListUiState.Content

    private companion object {
        const val CLOCK_STEP_MILLIS = 1_000L
    }
}
