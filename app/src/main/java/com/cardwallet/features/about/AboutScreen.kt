package com.cardwallet.features.about

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import com.cardwallet.BuildConfig
import com.cardwallet.R
import com.cardwallet.features.settings.components.SettingRow
import com.cardwallet.features.settings.components.SettingsSection
import com.cardwallet.ui.glass.LiquidButton
import com.cardwallet.ui.theme.WalletTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private const val GITHUB_URL = "https://github.com/PerspicaciousGuy/card-wallet"
private const val LICENSE_URL = "$GITHUB_URL/blob/main/LICENSE"
private const val KYANT_URL = "https://github.com/Kyant0/AndroidLiquidGlass"

/**
 * Pushed from Settings, not a tab (F7). Carries the open-source obligations:
 * the project link, our licence, and the Apache-2.0 attribution for the
 * liquid-glass components vendored from Kyant's catalog.
 */
@Composable
fun AboutScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    val backgroundColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    // Sibling capture, not ancestor capture — see SettingsContent for why.
    val backdrop = rememberLayerBackdrop()

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(backdrop)
                .background(backgroundColor),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = spacing.md),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = spacing.sm)) {
                LiquidButton(onClick = onClose, backdrop = backdrop) {
                    Text(
                        stringResource(R.string.back),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = spacing.md),
            )
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs, bottom = spacing.lg),
            )

            SettingsSection(stringResource(R.string.about_section_project)) {
                SettingRow(
                    title = stringResource(R.string.about_source),
                    subtitle = stringResource(R.string.about_source_note),
                    onClick = { openUrl(GITHUB_URL) },
                )
                SettingRow(
                    title = stringResource(R.string.about_license),
                    subtitle = stringResource(R.string.about_license_note),
                    onClick = { openUrl(LICENSE_URL) },
                )
                SettingRow(
                    title = stringResource(R.string.settings_version),
                    subtitle = BuildConfig.VERSION_NAME,
                )
            }

            SettingsSection(stringResource(R.string.about_section_privacy)) {
                SettingRow(
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.about_privacy_detail),
                )
            }

            SettingsSection(stringResource(R.string.about_section_credits)) {
                SettingRow(
                    title = stringResource(R.string.about_kyant),
                    subtitle = stringResource(R.string.about_kyant_note),
                    onClick = { openUrl(KYANT_URL) },
                )
            }

            Text(
                text = stringResource(R.string.about_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = spacing.sm),
            )

            Spacer(
                Modifier
                    .navigationBarsPadding()
                    .height(spacing.xl),
            )
        }
    }
}
