package com.pocketccd.prototype.ui.films

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.gallery.rememberThumbnail
import com.pocketccd.prototype.ui.glass.GlassChip
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconButton
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.glass.GlassSurface
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.glass

/**
 * 胶卷库里一行需要的数据。
 *
 * 刻意**不**直接传 `FilmStock` + `Set<Size>`：那会把 `android.util.Size`、
 * pack 尺寸协商这些相机内部概念漏进 UI，换 UI 时就得连带改数据层。
 * 是否支持、为什么不行，在 ViewModel 里算好再交过来。
 */
data class FilmCard(
    val id: String,
    val label: String,
    /** pack 的帧尺寸，如 `4000×3000`。这是真实的 pack 元数据，不是设计稿数字。 */
    val frameLabel: String,
    /** 这台设备能不能用这卷。false 时灰度显示并给出 [reason]。 */
    val supported: Boolean,
    val reason: String? = null,
    /** 这一支卷累计拍过多少张（卷模型移除后，这是唯一诚实的进度）。 */
    val shots: Int = 0,
    /** 该卷最新一张成片的缩略图；没拍过为 null。 */
    val latestThumbnail: String? = null,
)

/**
 * 胶卷库：装卷。
 *
 * 版式是「一张大的当前卷卡 + 下面的候选列表」，和旧版（纸面 CURRENT 卡 + 56×72 缩略图行）
 * 的层级一致，但材质换成玻璃、并补上旧版缺的三件事：
 * 1. **这台设备拍不了的卷灰掉并写明原因**（旧版能装进去，然后相机绑定时静默换卷，复审 P3-12）；
 * 2. 每卷真实拍过多少张（卷模型移除后不再有 36 格刻度可画）；
 * 3. 没拍过的卷不画示意风景，改画胶卷筒轮廓 —— 凭空渐变冒充成片违反 D-002。
 */
@Composable
fun FilmLibraryScreen(
    cards: List<FilmCard>,
    selectedId: String?,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
) {
    val p = MaterialTheme.glass
    val active = cards.firstOrNull { it.id == selectedId }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = GlassSpace.page, end = GlassSpace.page, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "胶卷", style = GlassType.title, color = p.onGlass)
                Text(
                    text = "${cards.size} 支可用 · 点一下即装卷",
                    style = GlassType.caption,
                    color = p.onGlassMuted,
                )
            }
            GlassIconButton(
                label = "重新扫描胶卷",
                onClick = onRefresh,
                icon = { tint -> GlassIcon(GlassIconKind.Refresh, tint) },
            )
        }

        if (cards.isEmpty()) {
            EmptyLibrary(onRefresh)
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = GlassSpace.page,
                end = GlassSpace.page,
                bottom = GlassSpace.dockReserve,
            ),
            verticalArrangement = Arrangement.spacedBy(GlassSpace.tight),
            modifier = Modifier.fillMaxSize(),
        ) {
            active?.let { current ->
                item(key = "current") {
                    CurrentStockCard(
                        card = current,
                        onOpenCamera = onOpenCamera,
                    )
                    Spacer(Modifier.height(GlassSpace.gap))
                }
            }
            items(items = cards, key = { it.id }) { card ->
                FilmRow(
                    card = card,
                    active = card.id == active?.id,
                    onClick = { if (card.supported && card.id != active?.id) onSelect(card.id) },
                )
            }
        }
    }
}

@Composable
private fun CurrentStockCard(card: FilmCard, onOpenCamera: () -> Unit) {
    val p = MaterialTheme.glass
    GlassSurface(
        shape = GlassShape.panel,
        contentPadding = PaddingValues(18.dp),
        interactive = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenCamera,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "已装 · 点这张卡回取景", style = GlassType.caption, color = p.onGlassMuted)
                Spacer(Modifier.weight(1f))
                Text(text = card.frameLabel, style = GlassType.numeric, color = p.onGlassMuted)
            }
            Spacer(Modifier.height(GlassSpace.tight))
            Text(text = card.label, style = GlassType.title, color = p.onGlass)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (card.shots == 0) {
                    "这一支还没拍过"
                } else {
                    "用这一支拍过 ${card.shots} 张"
                },
                style = GlassType.caption,
                color = p.onGlassMuted,
            )
            Spacer(Modifier.height(GlassSpace.inner))
            Row(verticalAlignment = Alignment.Bottom) {
                StockFrame(
                    thumbnail = card.latestThumbnail,
                    size = 84.dp,
                )
                Spacer(Modifier.width(GlassSpace.inner))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (card.latestThumbnail != null) {
                            "画格是这一支最新的一张真实成片"
                        } else {
                            "拍第一张之后，这里会显示它的最新成片"
                        },
                        style = GlassType.caption,
                        color = p.onGlassMuted,
                    )
                    Spacer(Modifier.height(GlassSpace.gap))
                    if (!card.supported) {
                        Text(
                            text = card.reason ?: "这台设备拍不了这一支",
                            style = GlassType.caption,
                            color = p.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmRow(card: FilmCard, active: Boolean, onClick: () -> Unit) {
    val p = MaterialTheme.glass
    GlassSurface(
        shape = GlassShape.card,
        contentPadding = PaddingValues(12.dp),
        interactive = card.supported,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick.takeIf { card.supported },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StockFrame(
                thumbnail = card.latestThumbnail,
                size = 56.dp,
                dimmed = !card.supported,
            )
            Spacer(Modifier.width(GlassSpace.gap))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.label,
                        style = GlassType.cardTitle,
                        color = if (card.supported) p.onGlass else p.onGlassMuted,
                    )
                    if (active) {
                        Spacer(Modifier.width(GlassSpace.tight))
                        GlassIcon(GlassIconKind.Check, p.accent, size = 16.dp)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        !card.supported -> card.reason ?: "这台设备拍不了"
                        card.shots == 0 -> "${card.frameLabel} · 还没拍过"
                        else -> "${card.frameLabel} · 已拍 ${card.shots} 张"
                    },
                    style = GlassType.caption,
                    color = if (card.supported) p.onGlassMuted else p.error,
                )
            }
            if (card.supported && !active) {
                GlassIcon(GlassIconKind.Chevron, p.onGlassMuted, size = 16.dp)
            }
        }
    }
}

/**
 * 画格：有真实成片就显示缩略图，没拍过就画胶卷筒。
 *
 * 旧版在这里画"这卷大概长这样"的示意风景（D-002 已禁）：那是以假成像冒充真成像。
 * 胶卷筒是一个明确的**图标**，承诺的是"这是哪一支卷"，不是"拍出来什么样"。
 */
@Composable
private fun StockFrame(thumbnail: String?, size: androidx.compose.ui.unit.Dp, dimmed: Boolean = false) {
    val p = MaterialTheme.glass
    val bitmap = rememberThumbnail(thumbnail)
    val shape = RoundedCornerShape(size / 5)
    Box(
        modifier = Modifier
            .size(width = size * 0.78f, height = size)
            .clip(shape)
            .background(p.fill)
            .border(1.dp, p.border, shape)
            .then(if (dimmed) Modifier.alpha(0.45f) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CanisterGlyph(p.onGlassMuted)
        }
    }
}

/** 胶卷筒轮廓：一个圆角矩形 + 顶部卷轴 + 一条出片缝。 */
@Composable
private fun CanisterGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        // 筒身
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
        // 卷轴
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.14f),
            androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.14f),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
        // 出片缝
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(w * 0.34f, h * 0.50f),
            androidx.compose.ui.geometry.Offset(w * 0.66f, h * 0.50f),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit) {
    val p = MaterialTheme.glass
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = GlassSpace.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassIcon(GlassIconKind.Film, p.onGlassMuted, size = 34.dp)
        Spacer(Modifier.height(GlassSpace.gap))
        Text(text = "没有可用的胶卷", style = GlassType.cardTitle, color = p.onGlass)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "应用目录里没有能解析的 .fswp 文件。点下面的按钮重新扫描一次。",
            style = GlassType.caption,
            color = p.onGlassMuted,
        )
        Spacer(Modifier.height(GlassSpace.inner))
        GlassChip(text = "重新扫描", onClick = onRefresh, selected = true)
    }
}


