package com.pocketccd.prototype.ui.film.films

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.fotufilm.FilmStock
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmRoundButton
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import java.io.File

/**
 * 胶卷库（T9-016 / D-043）：版式照抄原型 `films.html`，**字段全部换成真的**。
 *
 * 原型里有而这里没有的东西，都是因为没有数据源：
 * - `¥78`、`2027.08 到期` —— 我们不经手销售，价格和保质期无处可查；
 * - `ISO 400`、`C-41`、`彩色负片 / 黑白 / 电影卷` 分类 —— pack 格式里没有 ISO 与工艺字段，
 *   从卷名反推（`GOLD 200` → 200）属于猜，D-002 不允许；
 * - 品牌故事文案（"暖调肤色 · C-41 · 36 张"）—— 同上，是我们编的。
 *
 * 留下来的是每一条都能在设置页或电脑上对上的：卷名、帧尺寸、pack 文件名与体积、
 * 是否已装卷、本卷已拍几张。**卷数不写死 6**，设备上推了几卷就显示几卷。
 */
@Composable
fun FilmLibraryScreen(
    stocks: List<FilmStock>,
    selectedStockId: String?,
    shotsTaken: Int,
    rollCapacity: Int,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onNewRoll: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val active = stocks.firstOrNull { it.id == selectedStockId } ?: stocks.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg)
            .padding(horizontal = spec.metrics.gutterWide),
    ) {
        LibraryHeader(stockCount = stocks.size, onBack = onBack)

        if (stocks.isEmpty()) {
            EmptyLibrary(onRefresh = onRefresh)
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "loaded") {
                LoadedCard(
                    stock = active,
                    shotsTaken = shotsTaken,
                    rollCapacity = rollCapacity,
                    onResume = onBack,
                    onNewRoll = onNewRoll,
                )
            }

            item(key = "all-head") {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "全部胶卷",
                        color = p.ink,
                        fontSize = spec.type.optionTitle,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${stocks.size} 款",
                        color = p.inkGhost,
                        fontSize = spec.type.mono,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            items(items = stocks, key = { it.id }) { stock ->
                StockRow(
                    stock = stock,
                    loaded = stock.id == active?.id,
                    onClick = {
                        // 装卷后立刻回取景器：换卷的目的就是接着拍，
                        // 留在库里看一遍列表没有意义。
                        onSelect(stock.id)
                        onBack()
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(stockCount: Int, onBack: () -> Unit) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(spec.metrics.topBarHeight + 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilmRoundButton(
            icon = FilmIconKind.Close,
            onClick = onBack,
            modifier = Modifier.padding(start = 0.dp),
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "胶卷库",
                color = p.inkStrong,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "FILM STOCK",
                color = p.inkGhost,
                fontSize = spec.type.monoSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.16.em,
            )
        }
        Text(
            text = if (stockCount > 0) "$stockCount 卷" else "空",
            color = p.inkMuted,
            fontSize = spec.type.mono,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(8.dp))
    }
}

/**
 * 已装卷大卡。状态文案由真实数据驱动：
 * - `READY` = 本卷没拍满，可以按快门；
 * - `FULL` = 本卷 36 张已满，取景页的快门此时是禁用的（`canShoot=false`），
 *   所以这里必须给出"开新卷"，否则用户会卡在一个拍不动的界面上。
 */
@Composable
private fun LoadedCard(
    stock: FilmStock?,
    shotsTaken: Int,
    rollCapacity: Int,
    onResume: () -> Unit,
    onNewRoll: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shots = shotsTaken.coerceIn(0, rollCapacity)
    val full = shots >= rollCapacity
    val remaining = (rollCapacity - shots).coerceAtLeast(0)
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.controlFill)
            .border(spec.metrics.hairline, p.hairlineStrong, shape)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LOADED · 已装卷",
                color = p.accent,
                fontSize = spec.type.monoSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.14.em,
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(full = full)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stock?.label ?: "没有可用的胶卷",
            color = p.inkStrong,
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.02.em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // 真实参数：帧尺寸来自 pack 头部，文件名是磁盘上那个文件。
            text = stock?.let { "${it.sizeLabel} · ${it.file.name}" } ?: "—",
            color = p.inkFaint,
            fontSize = spec.type.mono,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "剩余",
                color = p.inkFaint,
                fontSize = spec.type.body,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$remaining",
                color = if (full) p.error else p.inkStrong,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "张",
                color = p.inkFaint,
                fontSize = spec.type.body,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "%02d / %02d".format(shots, rollCapacity),
                color = p.inkMuted,
                fontSize = spec.type.mono,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(8.dp))
        RollProgress(shots = shots, capacity = rollCapacity, full = full)

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryButton(
                label = "继续拍摄",
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = onResume,
            )
            LibraryButton(
                label = "开新卷",
                primary = false,
                // 一张都没拍时开新卷没有意义，点了只会让人怀疑自己点了什么。
                enabled = shots > 0,
                modifier = Modifier.weight(1f),
                onClick = onNewRoll,
            )
        }
        if (shots > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "开新卷只把本卷计数归零，已拍的照片不受影响。",
                color = p.inkGhost,
                fontSize = spec.type.monoSmall,
            )
        }
    }
}

@Composable
private fun StatusBadge(full: Boolean) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(3.dp)
    Text(
        text = if (full) "FULL" else "READY",
        color = if (full) p.error else p.inkMuted,
        fontSize = spec.type.monoSmall,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.12.em,
        modifier = Modifier
            .clip(shape)
            .background(if (full) p.error.copy(alpha = 0.14f) else p.chipFill)
            .border(
                spec.metrics.hairline,
                if (full) p.error.copy(alpha = 0.5f) else p.hairline,
                shape,
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun RollProgress(shots: Int, capacity: Int, full: Boolean) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val fraction = if (capacity <= 0) 0f else shots.toFloat() / capacity

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(p.hairline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (full) p.error else p.accent),
        )
    }
}

@Composable
private fun StockRow(
    stock: FilmStock,
    loaded: Boolean,
    onClick: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (loaded) p.controlFill else p.chipFill.copy(alpha = 0.18f))
            .border(
                spec.metrics.hairline,
                if (loaded) p.accent.copy(alpha = 0.55f) else p.hairline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stock.label,
                    color = p.inkStrong,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loaded) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "已装卷",
                        color = p.accent,
                        fontSize = spec.type.monoSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${stock.sizeLabel} · ${fileSizeLabel(stock.file.length())}",
                color = p.inkMuted,
                fontSize = spec.type.mono,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stock.file.name,
                color = p.inkGhost,
                fontSize = spec.type.monoSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (loaded) {
            FilmIcon(kind = FilmIconKind.Check, tint = p.accent, iconSize = 16.dp)
        } else {
            FilmIcon(kind = FilmIconKind.ChevronRight, tint = p.inkFaint, iconSize = 16.dp)
        }
    }
}

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(p.controlFill)
                .border(spec.metrics.hairline, p.hairline, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            FilmIcon(kind = FilmIconKind.Filmstrip, tint = p.inkMuted, iconSize = 22.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "还没有胶卷",
            color = p.ink,
            fontSize = spec.type.optionTitle,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // 这页空着唯一的原因是 pack 没推进去，所以直接把该做的事写出来，
            // 而不是留一句"暂无数据"。
            text = "把 .fswp 推到应用私有目录 film/ 之后\n点下面的按钮重新扫描",
            color = p.inkFaint,
            fontSize = spec.type.body,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        LibraryButton(
            label = "重新扫描",
            primary = false,
            modifier = Modifier.width(132.dp),
            onClick = onRefresh,
        )
    }
}

@Composable
private fun LibraryButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(10.dp)
    val fill = when {
        !enabled -> p.controlFill.copy(alpha = 0.5f)
        primary -> p.accent
        else -> p.controlFill
    }
    val textColor = when {
        !enabled -> p.inkGhost
        primary -> p.onAccent
        else -> p.inkStrong
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(fill)
            .border(
                spec.metrics.hairline,
                if (primary) p.accent else p.hairlineStrong,
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 真实文件体积。pack 约 2–4 MB，用 MB 更好读；小于 1 MB 才退回 KB。 */
private fun fileSizeLabel(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "%d KB".format(bytes / 1024)
}

private fun previewStock(id: String, label: String, w: Int, h: Int) = FilmStock(
    id = id,
    label = label,
    file = File("$id.fswp"),
    width = w,
    height = h,
    maxHalo = 64,
)

@Preview(widthDp = 380, heightDp = 820, showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun FilmLibraryPreview() {
    FilmTheme {
        FilmLibraryScreen(
            stocks = listOf(
                previewStock("gold200", "GOLD 200", 4096, 3072),
                previewStock("portra400", "PORTRA 400", 4096, 3072),
                previewStock("trix400", "TRI-X 400", 4000, 3000),
            ),
            selectedStockId = "gold200",
            shotsTaken = 12,
            rollCapacity = 36,
            onBack = {},
            onSelect = {},
            onNewRoll = {},
            onRefresh = {},
        )
    }
}

@Preview(widthDp = 380, heightDp = 820, showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun FilmLibraryEmptyPreview() {
    FilmTheme {
        FilmLibraryScreen(
            stocks = emptyList(),
            selectedStockId = null,
            shotsTaken = 0,
            rollCapacity = 36,
            onBack = {},
            onSelect = {},
            onNewRoll = {},
            onRefresh = {},
        )
    }
}
