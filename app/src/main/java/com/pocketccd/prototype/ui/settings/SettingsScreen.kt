package com.pocketccd.prototype.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.BuildConfig
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.components.ToyCameraBody
import com.pocketccd.prototype.ui.components.ToyRoundButton
import com.pocketccd.prototype.ui.components.ToyScreen
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType

private enum class FilterOption(val label: String) {
    DAYLIGHT("DAY"),
    FLASH("FLASH"),
    NIGHT("NIGHT"),
}

/**
 * Settings shown as the camera's tiny LCD panel, not a web settings page.
 * Local UI state only; no persistence/camera/storage/network yet.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
                    .padding(PocketCcdSpacing.md),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToyRoundButton(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.settings_back),
                        size = 38.dp,
                        color = PocketCcdColors.Mint,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = PocketCcdColors.TextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(PocketCcdSpacing.sm))
                    Text(
                        text = stringResource(R.string.settings_header),
                        style = PocketCcdType.Brand,
                        color = PocketCcdColors.TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                ToyScreen(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(PocketCcdSpacing.sm),
                    ) {
                        LcdSection(title = stringResource(R.string.settings_section_photos)) {
                            LcdRow(
                                label = stringResource(R.string.settings_save_location),
                                value = stringResource(R.string.settings_save_location_value),
                            )
                            FilterSelector(
                                selected = selectedFilter,
                                onSelect = { selectedFilter = it },
                            )
                            LcdRow(
                                label = stringResource(R.string.settings_quality),
                                value = stringResource(R.string.settings_quality_value),
                            )
                        }
                        Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
                        LcdSection(title = stringResource(R.string.settings_section_about)) {
                            LcdRow(
                                label = stringResource(R.string.settings_version),
                                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            )
                            LcdRow(
                                label = stringResource(R.string.settings_privacy),
                                value = stringResource(R.string.settings_privacy_value),
                            )
                        }
                        if (BuildConfig.DEBUG) {
                            Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
                            LcdSection(title = stringResource(R.string.settings_section_developer)) {
                                Text(
                                    text = stringResource(R.string.settings_diagnostics),
                                    style = PocketCcdType.MonoMicro,
                                    color = PocketCcdColors.Butter,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(PocketCcdRadius.xs))
                                        .clickable(
                                            role = Role.Button,
                                            onClick = { showDiagnostics = true },
                                        )
                                        .padding(vertical = PocketCcdSpacing.sm),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.settings_footer),
                            style = PocketCcdType.MonoMicro,
                            color = PocketCcdColors.ViewfinderInk.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
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
private fun LcdSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.ViewfinderInk.copy(alpha = 0.55f),
        )
        Spacer(modifier = Modifier.height(PocketCcdSpacing.xs))
        content()
    }
}

@Composable
private fun LcdRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.ViewfinderInk.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.width(PocketCcdSpacing.sm))
        Text(
            text = value,
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.ViewfinderInk,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PocketCcdSizes.hairline)
            .background(PocketCcdColors.ViewfinderInk.copy(alpha = 0.12f)),
    )
}

@Composable
private fun FilterSelector(
    selected: FilterOption,
    onSelect: (FilterOption) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PocketCcdSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PocketCcdSpacing.xs),
    ) {
        FilterOption.entries.forEach { option ->
            val isSelected = selected == option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(PocketCcdRadius.xs))
                    .background(
                        if (isSelected) {
                            PocketCcdColors.Accent
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        },
                    )
                    .border(
                        width = PocketCcdSizes.hairline,
                        color = PocketCcdColors.ViewfinderInk.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(PocketCcdRadius.xs),
                    )
                    .clickable(
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = PocketCcdType.MonoMicro,
                    color = if (isSelected) {
                        PocketCcdColors.OnAccent
                    } else {
                        PocketCcdColors.ViewfinderInk
                    },
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PocketCcdSizes.hairline)
            .background(PocketCcdColors.ViewfinderInk.copy(alpha = 0.12f)),
    )
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFE6F0EA)
@Composable
private fun SettingsScreenPreview() {
    PocketCcdTheme {
        SettingsScreen(onBack = {})
    }
}
