package com.cardwallet.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.cardwallet.R
import com.cardwallet.features.cards.list.CardListScreen
import com.cardwallet.features.settings.SettingsScreen
import com.cardwallet.ui.components.WalletTab
import com.cardwallet.ui.components.WalletTabIcon
import com.cardwallet.ui.glass.GlassIconButton
import com.cardwallet.ui.glass.LiquidBottomTab
import com.cardwallet.ui.glass.LiquidBottomTabs
import com.cardwallet.ui.theme.WalletTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private val TAB_ICON_SIZE = 26.dp
private val BAR_HEIGHT = 64.dp
private val ADD_ICON_SIZE = 22.dp

/**
 * App shell: the glass capsule (tabs only, F5.1) plus the detached circular
 * + button (F5.2) in one bottom row, both refracting the same page backdrop.
 */
@Composable
fun HomeScreen(
    onOpenCard: (String) -> Unit,
    onAddCard: () -> Unit,
    onChangePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                WalletTab.CARDS -> CardListScreen(onOpenCard = onOpenCard)
                WalletTab.SETTINGS -> SettingsScreen(onChangePin = onChangePin)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
                .height(BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedTab },
                onTabSelected = { selectedTab = it },
                backdrop = backdrop,
                tabsCount = WalletTab.entries.size,
                accentColor = tokens.glassAccent,
                containerColor = tokens.glassContainer,
                modifier = Modifier.weight(1f),
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

            Spacer(Modifier.width(tokens.spacing.sm))

            GlassIconButton(
                onClick = onAddCard,
                backdrop = backdrop,
                tint = tokens.glassAccent,
                contentDescription = stringResource(R.string.add_card),
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(BAR_HEIGHT),
            ) {
                PlusIcon(tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun PlusIcon(tint: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(ADD_ICON_SIZE)) {
        val stroke = Stroke(width = size.width * PLUS_STROKE_FRACTION, cap = StrokeCap.Round)
        drawLine(
            color = tint,
            start =
                androidx.compose.ui.geometry
                    .Offset(size.width / 2f, 0f),
            end =
                androidx.compose.ui.geometry
                    .Offset(size.width / 2f, size.height),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
        drawLine(
            color = tint,
            start =
                androidx.compose.ui.geometry
                    .Offset(0f, size.height / 2f),
            end =
                androidx.compose.ui.geometry
                    .Offset(size.width, size.height / 2f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
    }
}

private const val PLUS_STROKE_FRACTION = 0.12f

private fun WalletTab.labelRes(): Int =
    when (this) {
        WalletTab.CARDS -> R.string.tab_cards
        WalletTab.SETTINGS -> R.string.tab_settings
    }
