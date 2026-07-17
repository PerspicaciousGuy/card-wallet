package com.cardwallet.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.cardwallet.R
import com.cardwallet.features.cards.list.CardListScreen
import com.cardwallet.features.settings.SettingsScreen
import com.cardwallet.ui.components.WalletTab
import com.cardwallet.ui.components.WalletTabIcon
import com.cardwallet.ui.glass.LiquidBottomTab
import com.cardwallet.ui.glass.LiquidBottomTabs
import com.cardwallet.ui.theme.WalletTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private val TAB_ICON_SIZE = 26.dp

/**
 * App shell: the liquid-glass bottom navbar over the active tab's content.
 * Tabs are local UI state, not nav destinations — detail/form screens will be
 * real routes (Phase 3); the two top-level tabs switch in place.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(WalletTab.CARDS.index) }
    val tokens = WalletTheme.tokens

    Box(modifier.fillMaxSize()) {
        val backgroundColor = tokens.backdropBottom
        val backdrop =
            rememberLayerBackdrop {
                drawRect(backgroundColor)
                drawContent()
            }

        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(Brush.verticalGradient(listOf(tokens.backdropTop, tokens.backdropBottom))),
        ) {
            when (WalletTab.entries[selectedTab]) {
                WalletTab.CARDS -> CardListScreen()
                WalletTab.SETTINGS -> SettingsScreen()
            }
        }

        LiquidBottomTabs(
            selectedTabIndex = { selectedTab },
            onTabSelected = { selectedTab = it },
            backdrop = backdrop,
            tabsCount = WalletTab.entries.size,
            accentColor = tokens.glassAccent,
            containerColor = tokens.glassContainer,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        ) {
            WalletTab.entries.forEach { tab ->
                LiquidBottomTab(onClick = { selectedTab = tab.index }) {
                    val contentColor = MaterialTheme.colorScheme.onBackground
                    WalletTabIcon(
                        tab = tab,
                        tint = contentColor,
                        modifier = Modifier.size(TAB_ICON_SIZE),
                    )
                    BasicText(
                        text = stringResource(tab.labelRes()),
                        style =
                            TextStyle(
                                color = contentColor,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
                            ),
                    )
                }
            }
        }
    }
}

private fun WalletTab.labelRes(): Int =
    when (this) {
        WalletTab.CARDS -> R.string.tab_cards
        WalletTab.SETTINGS -> R.string.tab_settings
    }
