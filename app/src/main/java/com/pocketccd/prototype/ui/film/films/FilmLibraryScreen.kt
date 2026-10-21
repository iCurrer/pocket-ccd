package com.pocketccd.prototype.ui.film.films

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * 某款胶卷在本机上的使用记录。用来回答「这卷我拍过吗、拍了多少」。
 *
 * 只统计**照片墙上真实存在的成片**，不推断；[latestThumbnail] 取该卷最新一张的
 * 缩略图，用作列表左侧的画格。
 */
data class StockUsage(
    val shots: Int,
    val latestThumbnail: File?,
    /** 该卷最新一张的拍摄时间，用来顶替原型那句品牌文案。 */
    val lastCapturedAt: Long? = null,
)

/**
 * 胶卷库（T9-016 / D-043）。视觉基线：`docs/reference/pages/films.html`。
 *
 * 原型里有两处「非文字」的视觉，是第一版漏掉后显得廉价的根因，这一版都补了：
 *
 * 1. **已装卷卡是浅色金属面板**（`linear-gradient(135deg,#F2F4F5,#DCE0E3,#B4BAC0)`
 *    + 白描边 + 大投影 + 右上白色光斑 + 深色文字）。整页只有这一块是亮的，
 *    它承担全部"实体感"，其余都是近黑底。第一版做成了半透明深色，于是整页一个调子。
 * 2. **左侧 84×108 的画格**。原型用 CSS 画天空渐变 + 太阳光晕 + 山脊剪影 + 颗粒，
 *    占每行 40% 的面积。第一版把它换成了文字，行就退化成了列表项。
 *
 * 画格里放什么：**该卷最新一张真实成片**；这卷还没拍过就画胶卷筒。
 * 原型的风景画是"这卷大概长这样"的示意，我们没法凭空生成，也不该编
 * （D-002）。真拍过的片子比示意更有用，还能顺带回答"拍过几张"。
 *
 * 原型里有而这里没有的字段，都是因为没有数据源：`¥78`、`2027.08 到期`、
 * `ISO 400`、`C-41`、`彩色负片/黑白/电影卷` 分类、品牌文案。pack 格式里没有
 * ISO 与工艺字段，从卷名反推（`GOLD 200` → 200）属于猜。
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
    onOpenGallery: () -> Unit = {},
    usage: Map<String, StockUsage> = emptyMap(),
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
        LibraryHeader(
            stockCount = stocks.size,
            onBack = onBack,
            onOpenGallery = onOpenGallery,
        )

        if (stocks.isEmpty()) {
            EmptyLibrary(onRefresh = onRefresh)
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        ) {
            item(key = "loaded") {
                LoadedPanel(
                    stock = active,
                    shotsTaken = shotsTaken,
                    rollCapacity = rollCapacity,
                    onResume = onBack,
                    onNewRoll = onNewRoll,
                )
            }

            item(key = "all-head") {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "全部胶卷",
                        color = p.inkStrong,
                        fontSize = spec.type.optionTitle,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.02.em,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${stocks.size} 款",
                        color = p.textTertiary,
                        fontSize = spec.type.monoSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            items(items = stocks, key = { it.id }) { stock ->
                StockCard(
                    stock = stock,
                    loaded = stock.id == active?.id,
                    usage = usage[stock.id],
                    rollCapacity = rollCapacity,
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
private fun LibraryHeader(
    stockCount: Int,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    // 原型是「左右各一个圆钮 + 居中标题」。第一版做成左对齐标题 + 右侧数字，
    // 视觉重心歪到左边，后面的大卡再亮也压不住。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(spec.metrics.topBarHeight + 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilmRoundButton(icon = FilmIconKind.Close, onClick = onBack)
            FilmRoundButton(icon = FilmIconKind.ImageMultiple, onClick = onOpenGallery)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "胶卷库",
                color = p.inkStrong,
                fontSize = spec.type.sheetTitle,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
            )
            Text(
                text = if (stockCount > 0) "FILM STOCK" else "NO STOCK",
                color = p.textTertiary,
                fontSize = spec.type.label,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.24.em,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 已装卷：浅色金属面板
// ---------------------------------------------------------------------------

/**
 * 金属面板配色。**不入主题令牌**：这是整页唯一的反色（浅底深字）面板，
 * 只在"已装卷"这个位置成立，做成全局令牌会让别处被误用。
 */
private object Metal {
    val fill = listOf(Color(0xFFF2F4F5), Color(0xFFDCE0E3), Color(0xFFB4BAC0))
    val border = Color.White.copy(alpha = 0.5f)
    val inkStrong = Color(0xFF17191B)
    val inkBody = Color.Black.copy(alpha = 0.55f)
    val inkMono = Color.Black.copy(alpha = 0.45f)
    val inkFaint = Color.Black.copy(alpha = 0.40f)
    val track = Color.Black.copy(alpha = 0.15f)
    val progress = Color(0xFFB4703A)
    val darkPill = Color.Black.copy(alpha = 0.85f)
    val outline = Color.Black.copy(alpha = 0.15f)
}

@Composable
private fun LoadedPanel(
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
    val shape = RoundedCornerShape(24.dp)
    val glowRadius = 110.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 投影在 clip 之前打，否则会被裁剪掉。原型是 0 18px 44px rgba(0,0,0,.5)。
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(shape)
            .background(Brush.linearGradient(Metal.fill, Offset.Zero, Offset.Infinite))
            .drawBehind {
                // 右上一个虚化的白色光斑，原型靠 blur-2xl + 半透明白圆实现。
                // Compose 的径向渐变到此为止就够了：不需要真模糊，只要亮度不均。
                val r = glowRadius.toPx()
                val c = Offset(size.width - 30.dp.toPx(), -14.dp.toPx())
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        center = c,
                        radius = r,
                    ),
                    radius = r,
                    center = c,
                )
            }
            .border(1.dp, Metal.border, shape)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LOADED · 已装卷",
                color = Metal.inkMono,
                fontSize = spec.type.label,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.22.em,
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(full = full)
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            FilmCanister(
                label = stock?.label,
                capacity = rollCapacity,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stock?.label ?: "没有可用的胶卷",
                    color = Metal.inkStrong,
                    fontSize = spec.type.sheetTitle,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.01).em,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    // 真实参数：帧尺寸来自 pack 头部，文件名是磁盘上那个文件。
                    // 原型这里是「暖调肤色 · C-41 · 36 张」——工艺和卖点我们无从得知。
                    text = stock?.file?.name ?: "—",
                    color = Metal.inkBody,
                    fontSize = spec.type.label,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetalPill(
                        text = stock?.sizeLabel ?: "—",
                        filled = true,
                        dark = Metal.darkPill,
                        textColor = Color.White.copy(alpha = 0.85f),
                    )
                    MetalPill(
                        text = "4:3",
                        filled = false,
                        dark = Metal.track,
                        textColor = Metal.inkBody,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = "剩余", color = Metal.inkBody, fontSize = spec.type.label)
            Spacer(Modifier.width(5.dp))
            Text(
                text = "$remaining",
                color = if (full) Color(0xFFB84A3A) else Metal.inkStrong.copy(alpha = 0.85f),
                fontSize = spec.type.optionTitle,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "张",
                color = Metal.inkBody,
                fontSize = spec.type.label,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "%02d / %02d".format(shots, rollCapacity),
                color = Metal.inkFaint,
                fontSize = spec.type.label,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.10.em,
            )
        }

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Metal.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (rollCapacity <= 0) 0f else shots.toFloat() / rollCapacity)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (full) Color(0xFFB84A3A) else Metal.progress),
            )
        }

        Spacer(Modifier.height(6.dp))
        FrameDots(shots = shots, capacity = rollCapacity)

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton(
                label = "继续拍摄",
                primary = true,
                icon = FilmIconKind.Camera,
                modifier = Modifier.weight(1f),
                onClick = onResume,
            )
            PanelButton(
                label = "开新卷",
                primary = false,
                // 一张都没拍时开新卷没有意义，点了只会让人怀疑自己按了什么。
                enabled = shots > 0,
                modifier = Modifier.weight(1f),
                onClick = onNewRoll,
            )
        }
    }
}

@Composable
private fun StatusBadge(full: Boolean) {
    val spec = LocalFilmTheme.current
    val shape = RoundedCornerShape(999.dp)
    val dot = if (full) Color(0xFFB84A3A) else Color(0xFFE7B37A)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Metal.darkPill)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(dot),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (full) "FULL" else "READY",
            color = dot,
            fontSize = spec.type.label,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.14.em,
        )
    }
}

@Composable
private fun MetalPill(
    text: String,
    filled: Boolean,
    dark: Color,
    textColor: Color,
) {
    val spec = LocalFilmTheme.current
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = text,
        color = textColor,
        fontSize = spec.type.label,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.08.em,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (filled) dark else Color.Transparent)
            .border(1.dp, if (filled) Color.Transparent else Metal.outline, shape)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

/**
 * 每格一条的帧点阵（原型 `renderDots`：36 个 `h-[6px] flex-1` 的竖条，
 * 已曝光 `#B4703A`，未曝光 `rgba(0,0,0,.15)`）。
 *
 * 它比进度条更能读出"还剩几格"，也是原型里最像胶片的东西之一。
 */
@Composable
private fun FrameDots(shots: Int, capacity: Int) {
    if (capacity <= 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(capacity) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < shots) Metal.progress else Metal.track),
            )
        }
    }
}

@Composable
private fun PanelButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: FilmIconKind? = null,
    enabled: Boolean = true,
) {
    val spec = LocalFilmTheme.current
    val shape = RoundedCornerShape(12.dp)
    val fill = when {
        !enabled -> Color.Black.copy(alpha = 0.25f)
        primary -> Color(0xFF17191B)
        else -> Color.White.copy(alpha = 0.30f)
    }
    val textColor = when {
        !enabled -> Metal.inkFaint
        primary -> Color.White
        else -> Metal.inkBody.copy(alpha = 0.80f)
    }

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (primary) Color.Transparent else Metal.outline, shape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            FilmIcon(kind = icon, tint = textColor, iconSize = 14.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = textColor,
            fontSize = spec.type.label,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

// ---------------------------------------------------------------------------
// 胶卷筒插画
// ---------------------------------------------------------------------------

/**
 * 58×84 的胶卷筒（原型 `.film-canister`）：金属筒身 + 顶盖 + 底唇 + 奶油色标签。
 *
 * 总高 93dp 而非 84：原型靠 `::before` / `::after` 的负 top/bottom 溢出，
 * 这里直接把盖子算进测量高度，避免依赖"父容器不裁剪"这种隐式前提。
 */
@Composable
private fun FilmCanister(
    label: String?,
    capacity: Int,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val bodyW = 58.dp
    val bodyH = 84.dp
    val capH = 7.dp
    val capOver = 5.dp
    val lipH = 6.dp
    // 顶盖比筒身高出 5dp、底唇比筒身低 4dp，所以测量高度是 93 而不是 84。
    // 原型靠伪元素的负定位溢出，这里直接把溢出算进高度，免得依赖父容器不裁剪。
    val totalH = 93.dp

    Box(modifier = modifier.size(width = bodyW, height = totalH)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val s = bodyW.toPx() / 58f
            val bodyTop = capOver.toPx()

            // 筒身：横跨的高光，45% 处最亮（原型 90deg 三段渐变）。
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to Color(0xFF191C1F),
                    0.45f to Color(0xFF3B4045),
                    1f to Color(0xFF121416),
                ),
                topLeft = Offset(0f, bodyTop),
                size = Size(w, bodyH.toPx()),
                cornerRadius = CornerRadius(9f * s, 9f * s),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset(0f, bodyTop),
                size = Size(w, bodyH.toPx()),
                cornerRadius = CornerRadius(9f * s, 9f * s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )

            // 顶盖。原型是 #EDEFF1→#8D9298，但这块面板本身接近白，
            // 直接照抄的结果是"盖子消失、筒身像一块黑砖"（真机第一版）。
            // 所以压深一档并加一圈暗描边，让金属边在白底上也读得出来。
            val capInset = 8f * s
            val capTop = Offset(capInset, 0f)
            val capSize = Size(w - capInset * 2, capH.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFDBDFE3), Color(0xFF6E747A)),
                ),
                topLeft = capTop,
                size = capSize,
                cornerRadius = CornerRadius(3f * s, 3f * s),
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.28f),
                topLeft = capTop,
                size = capSize,
                cornerRadius = CornerRadius(3f * s, 3f * s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
            // 底唇：底边贴住 totalH，往上叠 2dp 压进筒身，接缝不会露缝。
            val lipInset = 14f * s
            val lipTop = Offset(lipInset, totalH.toPx() - lipH.toPx())
            val lipSize = Size(w - lipInset * 2, lipH.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFC6CBD0), Color(0xFF565C62)),
                ),
                topLeft = lipTop,
                size = lipSize,
                cornerRadius = CornerRadius(2f * s, 2f * s),
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.24f),
                topLeft = lipTop,
                size = lipSize,
                cornerRadius = CornerRadius(2f * s, 2f * s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }

        // 标签贴纸：left/right 6，top 22，bottom 17（相对筒身）。
        Column(
            modifier = Modifier
                .width(bodyW - 12.dp)
                .height(bodyH - 22.dp - 17.dp)
                .offset(x = 6.dp, y = capOver + 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFFEBD9B4), Color(0xFFD3B585))),
                )
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            val parts = (label ?: "—").trim().split(Regex("\\s+"), limit = 2)
            Text(
                text = parts.firstOrNull()?.uppercase() ?: "—",
                color = Color(0xFF2B2419),
                // micro-print: 仿真印刷标签，装饰，不承载信息
                fontSize = spec.type.microPrint,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // 第二行是卷名的后半段 + 真实卷容量。原型这里写「400 · 36」，
                // 400 是 ISO，我们没有这个字段；36 是真实的。
                text = listOfNotNull(
                    parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                    "$capacity",
                ).joinToString(" · "),
                color = Color(0xFF2B2419).copy(alpha = 0.70f),
                // micro-print: 仿真印刷标签，装饰，不承载信息
                fontSize = spec.type.microPrint,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.06.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 胶卷列表项
// ---------------------------------------------------------------------------

@Composable
private fun StockCard(
    stock: FilmStock,
    loaded: Boolean,
    usage: StockUsage?,
    rollCapacity: Int,
    onClick: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            // 原型 .film-card 是 #131518 实色，不是白 4% 的半透明。
            // 半透明在近黑底上会透出一层灰，行与行糊成一片。
            .background(Color(0xFF131518))
            .border(1.dp, if (loaded) p.accent.copy(alpha = 0.45f) else p.hairline, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        FilmFrame(stock = stock, usage = usage)

        Spacer(Modifier.width(12.dp))
        // SpaceBetween + heightIn(min) 而不是写死 108dp：写死高度时内容一旦
        // 多出几 dp 就会把底部那行按钮裁掉（真机第一次就踩到了）。
        // 现在内容短就靠 SpaceBetween 压到底部，内容长就让卡片自己长高。
        Column(
            modifier = Modifier.heightIn(min = 108.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = stock.label,
                        color = p.inkStrong,
                        fontSize = spec.type.optionTitle,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        // 原型这里是「¥78」。我们不经手销售，但"这卷拍过几张"是真数据。
                        text = usage?.shots?.takeIf { it > 0 }?.let { "$it 张" } ?: "—",
                        color = p.textTertiary,
                        fontSize = spec.type.label,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stock.file.name,
                    color = p.textTertiary,
                    fontSize = spec.type.label,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.04.em,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    // 原型这里是一句品牌文案（"柔和肤色与细腻颗粒…"），是编的。
                    // 换成本机真实记录：这卷上次拍是什么时候。没拍过就直说没拍过。
                    text = when {
                        usage == null || usage.shots <= 0 -> "这卷还没拍过"
                        usage.lastCapturedAt != null -> "上次拍摄 ${monthDay(usage.lastCapturedAt)}"
                        else -> "本机共 ${usage.shots} 张"
                    },
                    color = p.textSecondary,
                    fontSize = spec.type.body,
                    lineHeight = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BorderPill(text = stock.sizeLabel, color = p)
                    BorderPill(text = "$rollCapacity 张", color = p)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = fileSizeLabel(stock.file.length()),
                    color = p.textTertiary,
                    fontSize = spec.type.label,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.10.em,
                )
                LoadPill(loaded = loaded)
            }
        }
    }
}

@Composable
private fun BorderPill(text: String, color: com.pocketccd.prototype.ui.film.FilmPalette) {
    val spec = LocalFilmTheme.current
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = text,
        color = color.textTertiary,
        fontSize = spec.type.label,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, color.hairline, shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun LoadPill(loaded: Boolean) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = if (loaded) "已装卷" else "装卷",
        color = if (loaded) p.accent else Color(0xFFEDEFF2),
        fontSize = spec.type.label,
        modifier = Modifier
            .clip(shape)
            .background(
                if (loaded) p.accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.07f),
            )
            .border(
                1.dp,
                if (loaded) p.accent.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.12f),
                shape,
            )
            .padding(horizontal = 11.dp, vertical = 4.dp),
    )
}

/**
 * 84×108 的画格。有真实成片就显示该卷最新一张（裁切填满），没有就画胶卷筒。
 *
 * 原型这里的风景是 CSS 画的假场景，代表"这卷大概长这样"。我们既不能生成，
 * 也不该用一张假图冒充实拍（D-002），所以换成两个都能对上事实的东西：
 * 真拍过 → 你的片子；没拍过 → 这卷本身（胶卷筒）。
 */
@Composable
private fun FilmFrame(stock: FilmStock, usage: StockUsage?) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(12.dp)
    val thumb = usage?.latestThumbnail

    // 只解到 4 倍降采样：槽位 84×108，3 倍屏要 252×324 像素，
    // 原缩略图长边 1440。整张解出来是白占内存。
    val bitmap = remember(thumb?.path, thumb?.lastModified()) {
        thumb?.let {
            BitmapFactory.decodeFile(
                it.path,
                BitmapFactory.Options().apply { inSampleSize = 4 },
            )?.asImageBitmap()
        }
    }

    Box(
        modifier = Modifier
            .size(width = 84.dp, height = 108.dp)
            .clip(shape)
            .background(Color(0xFF0E1013)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 上下齿孔。压一道胶片边框，让"这是胶片"一眼看得出，
            // 而不是一张普通截图的缩略图。
            SprocketRow(modifier = Modifier.align(Alignment.TopCenter))
            SprocketRow(modifier = Modifier.align(Alignment.BottomCenter))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        ),
                    ),
            )
        } else {
            FilmCanister(label = stock.label, capacity = 0, modifier = Modifier.padding(top = 6.dp))
        }
        if (thumb == null) {
            Text(
                text = "未拍过",
                color = p.textTertiary,
                fontSize = spec.type.label,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

/** 原型 `.sprocket`：`repeating-linear-gradient`，4px 孔 / 6px 间隔。 */
@Composable
private fun SprocketRow(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val pitch = 10.dp.toPx()
        val hole = 4.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawRect(
                color = Color(0xE0060606),
                topLeft = Offset(x, 0f),
                size = Size(hole.coerceAtMost(size.width - x), size.height),
            )
            x += pitch
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
            color = p.textSecondary,
            fontSize = spec.type.body,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        DarkButton(label = "重新扫描", modifier = Modifier.width(132.dp), onClick = onRefresh)
    }
}

@Composable
private fun DarkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(p.controlFill)
            .border(spec.metrics.hairline, p.hairlineStrong, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = p.inkStrong, fontSize = spec.type.optionTitle)
    }
}

/** 真实文件体积。pack 约 2–4 MB，用 MB 更好读；小于 1 MB 才退回 KB。 */
private fun fileSizeLabel(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "%d KB".format(bytes / 1024)
}

/** `10.19`。只取月日：卷是按卷分组的，年份在库里没有信息量。 */
private fun monthDay(epochMillis: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%02d.%02d".format(c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}

private fun previewStock(id: String, label: String, w: Int, h: Int) = FilmStock(
    id = id,
    label = label,
    file = File("$id" + "__${w}x$h.fswp"),
    width = w,
    height = h,
    maxHalo = 64,
)

@Preview(widthDp = 380, heightDp = 900, showBackground = true, backgroundColor = 0xFF0B0C0E)
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
            usage = mapOf(
                "gold200" to StockUsage(shots = 12, latestThumbnail = null),
                "portra400" to StockUsage(shots = 3, latestThumbnail = null),
            ),
        )
    }
}

@Preview(widthDp = 380, heightDp = 900, showBackground = true, backgroundColor = 0xFF0B0C0E)
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
