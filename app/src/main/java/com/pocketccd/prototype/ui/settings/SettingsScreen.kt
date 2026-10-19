package com.pocketccd.prototype.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.BuildConfig
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.components.ToyCameraBody
import com.pocketccd.prototype.ui.components.ToyRoundButton
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType

private enum class FilterOption(val labelRes: Int) {
    DAYLIGHT(R.string.settings_filter_daylight),
    FLASH(R.string.settings_filter_flash),
    NIGHT(R.string.settings_filter_night),
}

/**
 * Settings inside the same red toy-camera frame, but with app-scale content:
 * 24sp page title, 15/12sp rows, grouped cards, 44dp segmented control, and a
 * scrollable list. No more 8sp web/LCD rows.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    stockCount: Int = 0,
    onRefreshStocks: () -> Unit = {},
) {
    var selectedFilter by remember { mutableStateOf(FilterOption.DAYLIGHT) }
    var showDiagnostics by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PocketCcdColors.Background)
            .padding(PocketCcdSizes.cameraPanelMargin),
        contentAlignment = Alignment.Center,
    ) {
        ToyCameraBody(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = PocketCcdSpacing.md,
                        vertical = PocketCcdSpacing.sm,
                    ),
            ) {
                SettingsHeader(onBack = onBack)
                Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PocketCcdSpacing.md),
                ) {
                    item {
                        SettingsSection(title = stringResource(R.string.settings_section_photos)) {
                            SettingsRow(
                                title = stringResource(R.string.settings_save_location),
                                subtitle = stringResource(R.string.settings_save_location_value),
                                value = stringResource(R.string.settings_save_location_value_short),
                            )
                            Hairline()
                            FilterSetting(
                                selected = selectedFilter,
                                onSelect = { selectedFilter = it },
                            )
                            Hairline()
                            SettingsRow(
                                title = stringResource(R.string.settings_quality),
                                subtitle = stringResource(R.string.settings_quality_subtitle),
                                value = stringResource(R.string.settings_quality_value),
                            )
                            Hairline()
                            // pack 走 adb push 进应用私有目录，点了才重扫 —— 用户往
                            // 目录里补卷之后不用杀进程重进。
                            SettingsRow(
                                title = stringResource(R.string.settings_film_roll),
                                subtitle = stringResource(R.string.settings_film_roll_hint),
                                value = stockCount.toString(),
                                onClick = onRefreshStocks,
                            )
                        }
                    }
                    item {
                        SettingsSection(title = stringResource(R.string.settings_section_about)) {
                            SettingsRow(
                                title = stringResource(R.string.settings_version),
                                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            )
                            Hairline()
                            SettingsRow(
                                title = stringResource(R.string.settings_privacy),
                                subtitle = stringResource(R.string.settings_privacy_note),
                                value = stringResource(R.string.settings_privacy_value),
                            )
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        item {
                            SettingsSection(
                                title = stringResource(R.string.settings_section_developer),
                            ) {
                                SettingsRow(
                                    title = stringResource(R.string.settings_diagnostics),
                                    subtitle = stringResource(R.string.settings_diagnostics_value),
                                    onClick = { showDiagnostics = true },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_diagnostics_title),
                    style = PocketCcdType.RowTitle,
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_diagnostics_body,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = PocketCcdType.RowSubtitle,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) {
                    Text(
                        text = stringResource(R.string.settings_close),
                        style = PocketCcdType.MonoLabel,
                        color = PocketCcdColors.AccentText,
                    )
                }
            },
        )
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToyRoundButton(
            onClick = onBack,
            contentDescription = stringResource(R.string.settings_back),
            size = 40.dp,
            color = PocketCcdColors.Mint,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = PocketCcdColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(PocketCcdSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_title),
                style = PocketCcdType.SettingsTitle,
                color = PocketCcdColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = PocketCcdType.RowSubtitle,
                color = PocketCcdColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = PocketCcdType.SectionLabel,
            color = PocketCcdColors.TextTertiary,
            modifier = Modifier.padding(start = PocketCcdSpacing.xs),
        )
        Spacer(modifier = Modifier.height(PocketCcdSpacing.xs))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFFFFBF4),
            border = BorderStroke(1.dp, PocketCcdColors.OutlineSoft),
            shadowElevation = 2.dp,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = PocketCcdSpacing.lg,
                vertical = PocketCcdSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = PocketCcdType.RowTitle,
                color = PocketCcdColors.TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = PocketCcdType.RowSubtitle,
                    color = PocketCcdColors.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(modifier = Modifier.width(PocketCcdSpacing.md))
            Text(
                text = value,
                style = PocketCcdType.RowSubtitle,
                color = PocketCcdColors.TextSecondary,
            )
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(PocketCcdSpacing.xs))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PocketCcdColors.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FilterSetting(
    selected: FilterOption,
    onSelect: (FilterOption) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = PocketCcdSpacing.lg,
                vertical = PocketCcdSpacing.md,
            ),
    ) {
        Text(
            text = stringResource(R.string.settings_default_filter),
            style = PocketCcdType.RowTitle,
            color = PocketCcdColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PocketCcdColors.SurfaceHigh)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            FilterOption.entries.forEach { option ->
                val isSelected = selected == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isSelected) {
                                PocketCcdColors.Accent
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        style = PocketCcdType.RowSubtitle,
                        color = if (isSelected) {
                            PocketCcdColors.OnAccent
                        } else {
                            PocketCcdColors.TextSecondary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PocketCcdSpacing.lg)
            .height(PocketCcdSizes.hairline)
            .background(PocketCcdColors.OutlineSoft),
    )
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFD9382E)
@Composable
private fun SettingsScreenPreview() {
    PocketCcdTheme {
        SettingsScreen(onBack = {})
    }
}
