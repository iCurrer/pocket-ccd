package com.pocketccd.prototype.ui.film

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * D-063「白纸档案」共用纸面组件。
 *
 * 这些组件全部从 [LocalFilmTheme] 取纸面令牌：不要在这里写死墨色/圆角/字号，
 * 否则取景页（黑闸门）与纸面页会重新分叉。尺寸只允许走 spec.metrics / spec.type。
 */

/** 1px 规则线；默认用当前页面的 hairline 色。 */
@Composable
fun PaperHairline(
    modifier: Modifier = Modifier,
    color: Color? = null,
    startInset: Dp = 0.dp,
    endInset: Dp = 0.dp,
) {
    val c = color ?: LocalFilmTheme.current.palette.hairline
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startInset, end = endInset)
            .height(1.dp)
            .background(c),
    )
}

/**
 * 纸面页头：Serif 标题 + mono 副题 + 返回键（48dp 热区）。
 * 不自行吃 status bar insets：外层 AppRoot 已经统一处理。
 */
@Composable
fun PaperPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = spec.metrics.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack == null) {
                Spacer(Modifier.width(48.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .filmClickable(
                            onClick = onBack,
                            scaleTo = 0.97f,
                            onClickLabel = "返回",
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    FilmIcon(
                        kind = FilmIconKind.Close,
                        tint = p.textSecondary,
                        iconSize = 16.dp,
                        strokeWidth = 1.5.dp,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = title,
                    color = p.textPrimary,
                    fontFamily = spec.type.titleFamily,
                    fontSize = spec.type.sheetTitle,
                    lineHeight = spec.type.sheetTitleLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = p.textTertiary,
                        fontFamily = spec.type.monoFamily,
                        fontSize = spec.type.mono,
                        lineHeight = spec.type.monoLineHeight,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                    )
                }
            }
            if (trailing == null) {
                Spacer(Modifier.width(48.dp))
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing() }
            }
        }
        PaperHairline()
    }
}

/** 编号小节标题：`01  保存与输出`。 */
@Composable
fun PaperSectionLabel(
    index: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = spec.metrics.pageMargin,
                    end = spec.metrics.pageMargin,
                    top = 24.dp,
                    bottom = 8.dp,
                ),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = index,
                color = p.textTertiary,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.mono,
                lineHeight = spec.type.monoLineHeight,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = p.textPrimary,
                fontFamily = spec.type.titleFamily,
                fontSize = spec.type.optionTitle,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        PaperHairline(startInset = spec.metrics.pageMargin, endInset = spec.metrics.pageMargin)
    }
}

/** 索引页签：选中 = 2dp 墨线 + 主文字，未选中 = 次级文字。 */
@Composable
fun PaperIndexTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val color by animateColorAsState(
        targetValue = if (selected) p.textPrimary else p.textTertiary,
        animationSpec = tween(spec.motion.stateMs),
        label = "paper-tab-color",
    )
    Column(
        modifier = modifier
            .height(44.dp)
            .filmClickable(
                onClick = onClick,
                scaleTo = 0.97f,
                role = Role.Tab,
                onClickLabel = text,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.label,
            lineHeight = spec.type.labelLineHeight,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(if (selected) 20.dp else 0.dp)
                .height(2.dp)
                .background(if (selected) p.textPrimary else Color.Transparent),
        )
    }
}

/** 纸面主/次按钮：主 = 墨底纸白字；次 = 1px 墨线透明底；幽灵 = 下划线文字。 */
@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    secondary: Boolean = false,
    enabled: Boolean = true,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(spec.metrics.cardRadius)
    val bg = when {
        !enabled -> p.controlFill
        primary -> p.textPrimary
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> p.textTertiary
        primary -> p.appBg
        else -> p.textPrimary
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (secondary && enabled) {
                    // 1px 次按钮描边必须能读成控件边界（≥3:1），hairline(12%) 只够做分隔线。
                    Modifier.border(1.dp, p.textSecondary, shape)
                } else {
                    Modifier
                },
            )
            .filmClickable(
                onClick = onClick,
                enabled = enabled,
                scaleTo = 0.98f,
                onClickLabel = text,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontFamily = spec.type.bodyFamily,
            fontSize = spec.type.value,
            lineHeight = spec.type.labelLineHeight,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            textDecoration = if (!primary && !secondary) TextDecoration.Underline else null,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** 设置页开关：40×22 墨轨 + 纸白滑块；整行 48dp 可点。 */
@Composable
fun PaperSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val track by animateColorAsState(
        targetValue = if (checked) p.accent else p.hairlineStrong,
        animationSpec = tween(spec.motion.stateMs),
        label = "paper-switch-track",
    )
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 22f else 2f,
        animationSpec = tween(spec.motion.stateMs),
        label = "paper-switch-thumb",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .filmClickable(
                onClick = { onCheckedChange(!checked) },
                scaleTo = 0.99f,
                role = Role.Switch,
                onClickLabel = label,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = p.textPrimary,
            fontFamily = spec.type.bodyFamily,
            fontSize = spec.type.body,
            lineHeight = spec.type.bodyLineHeight,
        )
        Box(
            Modifier
                .width(44.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(track),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = thumbOffset.dp)
                    .size(18.dp)
                    .background(p.sheetBg, CircleShape),
            )
        }
    }
}

/** 纸面空态：一句说明 + 一个明确动作。 */
@Composable
fun PaperEmptyState(
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spec.metrics.pageMargin, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = p.textPrimary,
            fontFamily = spec.type.titleFamily,
            fontSize = spec.type.optionTitle,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            color = p.textSecondary,
            fontFamily = spec.type.bodyFamily,
            fontSize = spec.type.body,
            lineHeight = spec.type.bodyLineHeight,
            textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            PaperButton(text = action, onClick = onAction, secondary = true)
        }
    }
}
