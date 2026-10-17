package com.pocketccd.prototype.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.BuildConfig
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType

private enum class FilterOption(val label: String) {
    DAYLIGHT("DAYLIGHT"),
    FLASH("FLASH"),
    NIGHT("NIGHT"),
}

/**
 * Settings prototype. It only changes local UI state; no persistence, camera,
 * storage or network access yet.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf(FilterOption.DAYLIGHT) }
    var showDiagnostics by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PocketCcdColors.Background)
            .padding(
                horizontal = PocketCcdSpacing.xl,
                vertical = PocketCcdSpacing.md,
            ),
    ) {
        SettingsTopBar(onBack = onBack)
        Spacer(modifier = Modifier.height(PocketCcdSpacing.lg))

        SettingsSection(title = stringResource(R.string.settings_section_photos)) {
            SettingsRow(
                label = stringResource(R.string.settings_save_location),
                value = stringResource(R.string.settings_save_location_value),
            )
            FilterSelector(
                selected = selectedFilter,
                onSelect = { selectedFilter = it },
            )
            SettingsRow(
                label = stringResource(R.string.settings_quality),
                value = stringResource(R.string.settings_quality_value),
            )
        }

        Spacer(modifier = Modifier.height(PocketCcdSpacing.lg))

        SettingsSection(title = stringResource(R.string.settings_section_about)) {
            SettingsRow(
                label = stringResource(R.string.settings_version),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            SettingsRow(
                label = stringResource(R.string.settings_privacy),
                value = stringResource(R.string.settings_privacy_value),
            )
        }

        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(PocketCcdSpacing.lg))
            SettingsSection(title = stringResource(R.string.settings_section_developer)) {
                SettingsRow(
                    label = stringResource(R.string.settings_diagnostics),
                    value = stringResource(R.string.settings_diagnostics_value),
                    onClick = { showDiagnostics = true },
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.settings_footer),
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.TextTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PocketCcdSpacing.md),
            textAlign = TextAlign.Center,
        )
    }

    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_diagnostics_title),
                    style = PocketCcdType.MonoLabel,
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_diagnostics_body,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = PocketCcdType.MonoMicro,
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
private fun SettingsTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.settings_back),
                tint = PocketCcdColors.TextSecondary,
                modifier = Modifier.size(PocketCcdSizes.icon),
            )
        }
        Text(
            text = stringResource(R.string.settings_header),
            style = PocketCcdType.MonoLabel,
            color = PocketCcdColors.TextPrimary,
            modifier = Modifier.align(Alignment.Center),
        )
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
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.TextTertiary,
        )
        Spacer(modifier = Modifier.height(PocketCcdSpacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PocketCcdSizes.hairline)
                .background(PocketCcdColors.OutlineSoft),
        )
        content()
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(
                    enabled = onClick != null,
                    role = Role.Button,
                    onClick = { onClick?.invoke() },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextSecondary,
            )
            Spacer(modifier = Modifier.width(PocketCcdSpacing.md))
            Text(
                text = value,
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PocketCcdSizes.hairline)
                .background(PocketCcdColors.OutlineSoft),
        )
    }
}

@Composable
private fun FilterSelector(
    selected: FilterOption,
    onSelect: (FilterOption) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_default_filter),
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextSecondary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = PocketCcdSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PocketCcdSpacing.md),
        ) {
            FilterOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(PocketCcdRadius.xs))
                        .clickable(
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .padding(vertical = PocketCcdSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .border(
                                width = PocketCcdSizes.hairline,
                                color = if (selected == option) {
                                    PocketCcdColors.AccentText
                                } else {
                                    PocketCcdColors.Outline
                                },
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected == option) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PocketCcdColors.AccentText),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(PocketCcdSpacing.xs))
                    Text(
                        text = option.label,
                        style = PocketCcdType.MonoMicro,
                        color = if (selected == option) {
                            PocketCcdColors.TextPrimary
                        } else {
                            PocketCcdColors.TextTertiary
                        },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PocketCcdSizes.hairline)
                .background(PocketCcdColors.OutlineSoft),
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFF4EFE6)
@Composable
private fun SettingsScreenPreview() {
    PocketCcdTheme {
        SettingsScreen(onBack = {})
    }
}
