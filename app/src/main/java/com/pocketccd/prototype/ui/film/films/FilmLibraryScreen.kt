package com.pocketccd.prototype.ui.film.films

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pocketccd.prototype.fotufilm.FilmStock
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmPaperSpec
import com.pocketccd.prototype.ui.film.FilmRoundButton
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperButton
import com.pocketccd.prototype.ui.film.PaperEmptyState
import com.pocketccd.prototype.ui.film.PaperHairline
import com.pocketccd.prototype.ui.film.PaperPageHeader
import com.pocketccd.prototype.ui.film.filmClickable
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
 * 胶卷库：视觉方向 C「白纸档案」的**索引卡**（D-063 / `docs/paper-archive-ui-spec.md` §5.4）。
 *
 * 整页是一张纸：`appBg` 打底，页头一条 1px 墨线，内容只有两类东西——
 *
 * 1. **已装卷 = 一张索引卡**。顶部 `CURRENT` 黑带（纸白 12sp mono + READY/FULL），
 *    下面左边是胶卷筒、右边是卷名/画幅/剩余/帧号/36 帧点阵，底部两个纸面按钮。
 *    上一版这里是「浅色金属面板 + 私配色 + 大投影」，它靠反色和亮度抢注意力；
 *    纸面方向里，层级只由 1px 规则线和纸色明度差承担（§1.1）。
 * 2. **全部胶卷 = 索引卡列表**。每行左 84×108 真实画格（有真实成片就显示，没有就画
 *    胶卷筒），右侧卷名、张数、上次拍摄、装卷状态。行与行之间只有一条 hairline，
 *    没有卡片底、没有圆角、没有阴影。
 *
 * 画格里放什么：**该卷最新一张真实成片**；这卷还没拍过就画胶卷筒。原型的风景画是
 * "这卷大概长这样"的示意，我们没法凭空生成，也不该编（D-002）。
 *
 * 不显示的字段（D-050）：`.fswp` 文件名、pack 体积、`4096×3072` 这类像素尺寸。
 * 它们的共同点是"真实，但不参与任何决定"。留下的都是用户会用的：卷名、张数、
 * 上次拍摄日期、剩余张数、`05 / 36`、36 帧点阵。画幅（`4:3`）保留 —— 它由真实宽高
 * 约分得来，回答"这卷拍出来什么形状"，不是内部参数。
 *
 * 原型里有而这里没有的字段，都是因为没有数据源：`¥78`、`2027.08 到期`、`ISO 400`、
 * `C-41`、`彩色负片/黑白/电影卷` 分类、品牌文案。pack 格式里没有 ISO 与工艺字段，
 * 从卷名反推（`GOLD 200` → 200）属于猜。
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

    // 2026-10-23：点选胶卷**不再立刻切回取景器**。留在本页把"装卷"演完：
    // 先让卡片进入「装卷中…」（900ms），已装卷大卡用滑入/淡入换卷，用户再自己
    // 点「继续拍摄」回去。理由：换卷是有实体的动作，秒切等于什么都没发生。
    var installingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(installingId) {
        if (installingId != null) {
            kotlinx.coroutines.delay(900)
            installingId = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg),
    ) {
        // 页头自带 24dp 内容边距与底部 hairline，外层不要再套横向 padding。
        PaperPageHeader(
            title = "胶卷库",
            subtitle = "FILM STOCK",
            onBack = onBack,
            trailing = {
                FilmRoundButton(
                    icon = FilmIconKind.ImageMultiple,
                    onClick = onOpenGallery,
                )
            },
        )

        if (stocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                PaperEmptyState(
                    title = "还没有胶卷",
                    // 空着的唯一原因是应用目录里没有能解析的胶卷文件，所以把真实原因和
                    // 该做的事一起写出来，而不是留一句"暂无数据"。
                    body = "应用里还没有可用的胶卷文件。\n点下面的按钮重新扫描一次。",
                    action = "重新扫描",
                    onAction = onRefresh,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "loaded") {
                Box(
                    modifier = Modifier.padding(
                        start = spec.metrics.pageMargin,
                        end = spec.metrics.pageMargin,
                        top = 16.dp,
                    ),
                ) {
                    // 换卷动画：旧卷淡出下滑、新卷淡入上滑。不是"卡片闪一下"。
                    AnimatedContent(
                        targetState = active?.id,
                        transitionSpec = {
                            (fadeIn(tween(spec.motion.sheetEnterMs)) +
                                slideInVertically(tween(spec.motion.sheetEnterMs)) { it / 8 }) togetherWith
                                (fadeOut(tween(spec.motion.pageExitMs)) +
                                    slideOutVertically(tween(spec.motion.pageExitMs)) { -it / 10 })
                        },
                        label = "loaded-roll-change",
                    ) { id ->
                        val shown = stocks.firstOrNull { it.id == id } ?: active
                        if (shown != null) {
                            LoadedRollCard(
                                stock = shown,
                                shotsTaken = if (shown.id == active?.id) shotsTaken else 0,
                                rollCapacity = rollCapacity,
                                onResume = onBack,
                                onNewRoll = onNewRoll,
                            )
                        }
                    }
                }
            }

            item(key = "all-head") {
                LibrarySectionHead(title = "全部胶卷", count = stocks.size)
            }

            itemsIndexed(items = stocks, key = { _, stock -> stock.id }) { index, stock ->
                StockIndexRow(
                    stock = stock,
                    loaded = stock.id == active?.id,
                    usage = usage[stock.id],
                    onClick = {
                        // 只装卷，不跳页。装卷动画由上面的 installingId 驱动，
                        // 回取景器留给「继续拍摄」—— 那是一次明确的用户动作。
                        if (installingId == null && stock.id != active?.id) {
                            installingId = stock.id
                            onSelect(stock.id)
                        }
                    },
                    installing = installingId == stock.id,
                )
                if (index != stocks.lastIndex) {
                    PaperHairline(
                        startInset = spec.metrics.pageMargin,
                        endInset = spec.metrics.pageMargin,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 小节标题
// ---------------------------------------------------------------------------

/**
 * `全部胶卷` + 右侧真实卷数 + 底部 hairline。
 *
 * 不套 [com.pocketccd.prototype.ui.film.PaperSectionLabel]：那个组件带 `01 / 02`
 * 编号，本页只有"已装卷"和"全部胶卷"两段，第一段由黑带 `CURRENT` 自报家门，
 * 给第二段单挂一个 `01` 会读成错号。样式（Serif 标题 + mono 小字 + hairline）
 * 与它保持一致。
 */
@Composable
private fun LibrarySectionHead(title: String, count: Int) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(Modifier.fillMaxWidth()) {
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
                text = title,
                color = p.textPrimary,
                fontFamily = spec.type.titleFamily,
                fontSize = spec.type.optionTitle,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$count 款",
                color = p.textTertiary,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.monoSmall,
            )
        }
        PaperHairline(startInset = spec.metrics.pageMargin, endInset = spec.metrics.pageMargin)
    }
}

// ---------------------------------------------------------------------------
// 已装卷：纸面索引卡 + CURRENT 黑带
// ---------------------------------------------------------------------------

/**
 * 索引卡：`sheetBg` 抬起的纸面 + 1px hairline 边 + 0 阴影。
 *
 * 卡内只有一条"反色"：顶部 28dp 的 `CURRENT` 黑带。它是全页唯一一块实心墨，
 * 用来标"这张卡是当前装着的卷"，和纸面列表区分开，而不靠投影或金属渐变。
 */
@Composable
private fun LoadedRollCard(
    stock: FilmStock?,
    shotsTaken: Int,
    rollCapacity: Int,
    onResume: () -> Unit,
    onNewRoll: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shots = shotsTaken.coerceIn(0, rollCapacity)
    val full = rollCapacity > 0 && shots >= rollCapacity
    val remaining = (rollCapacity - shots).coerceAtLeast(0)
    val shape = RoundedCornerShape(spec.metrics.cardRadius)
    // 黑带上的纸白字：墨底纸白是纸面方向唯一的反色搭配。
    val onInk = p.sheetBg

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.sheetBg)
            .border(1.dp, p.hairline, shape),
    ) {
        // CURRENT 黑带
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(p.textPrimary)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CURRENT",
                color = onInk,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.label,
                letterSpacing = 0.18.em,
            )
            Spacer(Modifier.weight(1f))
            RollStateMark(full = full, tint = onInk)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            FilmCanister(
                label = stock?.label,
                capacity = rollCapacity,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stock?.label ?: "没有可用的胶卷",
                    color = p.textPrimary,
                    fontFamily = spec.type.titleFamily,
                    fontSize = spec.type.optionTitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stock?.let { "${aspectLabel(it.width, it.height)} 画幅" } ?: "—",
                    color = p.textTertiary,
                    fontFamily = spec.type.monoFamily,
                    fontSize = spec.type.mono,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    // 整页唯一回答"还能不能拍"的数字，所以给 value 一档（14sp）。
                    text = "剩余 $remaining 张",
                    color = if (full) p.textSecondary else p.textPrimary,
                    fontFamily = spec.type.bodyFamily,
                    fontSize = spec.type.value,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "%02d / %02d".format(shots, rollCapacity),
                    color = p.textSecondary,
                    fontFamily = spec.type.monoFamily,
                    fontSize = spec.type.mono,
                    letterSpacing = 0.08.em,
                )
            }
        }

        FrameDots(
            shots = shots,
            capacity = rollCapacity,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaperButton(
                text = "继续拍摄",
                onClick = onResume,
                primary = true,
                modifier = Modifier.weight(1f),
            )
            PaperButton(
                text = "开新卷",
                onClick = onNewRoll,
                secondary = true,
                // 一张都没拍时开新卷没有意义，点了只会让人怀疑自己按了什么。
                enabled = shots > 0,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * `READY` / `FULL`：黑带上的状态。
 *
 * 只用纸白一种颜色 + 实心/空心两种方块做双编码 —— 黑带上放不下第二个可读色
 * （石板蓝、错误红在墨底上都不到 4.5:1），文字本身已经说清了状态。
 */
@Composable
private fun RollStateMark(full: Boolean, tint: Color) {
    val spec = LocalFilmTheme.current
    val dotShape = RoundedCornerShape(1.dp)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .then(
                    if (full) {
                        Modifier.border(1.dp, tint, dotShape)
                    } else {
                        Modifier.background(tint, dotShape)
                    },
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (full) "FULL" else "READY",
            color = tint,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.label,
            letterSpacing = 0.14.em,
        )
    }
}

/**
 * 36 格帧点阵：已曝光墨色、未曝光墨 15%（规格「36 帧点阵列」）。
 *
 * 它比进度条更能读出"还剩几格"，也是原型里最像胶片的东西之一。进度条已删：
 * 它和点阵是同一个事实的两种画法（D-050：真实 ≠ 该显示）。
 */
@Composable
private fun FrameDots(
    shots: Int,
    capacity: Int,
    modifier: Modifier = Modifier,
) {
    if (capacity <= 0) return
    val p = LocalFilmTheme.current.palette
    val dotShape = RoundedCornerShape(1.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(capacity) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(
                        color = if (i < shots) p.textPrimary else p.textPrimary.copy(alpha = 0.15f),
                        shape = dotShape,
                    ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 全部胶卷：索引卡行
// ---------------------------------------------------------------------------

/**
 * 一行索引卡。**没有卡片底**：行与行之间靠 hairline 与 12dp 呼吸分开，
 * 圆角/投影在这页不存在（§1.1 纸面页无阴影）。
 *
 * 整行可点（热区 = 84×108 画格那一行，远超 48dp），点下去只装卷，不跳页。
 */
@Composable
private fun StockIndexRow(
    stock: FilmStock,
    loaded: Boolean,
    usage: StockUsage?,
    onClick: () -> Unit,
    installing: Boolean,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spec.metrics.pageMargin)
            .filmClickable(
                onClick = onClick,
                // 列表行只叠色不缩放：整行跟着手指抖会读成"页面在晃"。
                scaleTo = 1f,
                overlayAlpha = 0.05f,
                haptic = false,
                onClickLabel = "装入这卷",
            )
            .padding(vertical = 12.dp),
    ) {
        StockFrame(stock = stock, usage = usage)

        Spacer(Modifier.width(12.dp))
        // SpaceBetween + heightIn(min) 而不是写死 108dp：内容短就压到底部对齐画格，
        // 内容长（卷名两行）就让行自己长高。
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 108.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = stock.label,
                        color = p.textPrimary,
                        fontFamily = spec.type.titleFamily,
                        fontSize = spec.type.optionTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        // 原型这里是「¥78」。我们不经手销售，但"这卷拍过几张"是真数据。
                        text = usage?.shots?.takeIf { it > 0 }?.let { "$it 张" } ?: "—",
                        color = p.textTertiary,
                        fontFamily = spec.type.monoFamily,
                        fontSize = spec.type.mono,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    // 原型这里是一句品牌文案（"柔和肤色与细腻颗粒…"），是编的。
                    // 换成本机真实记录：这卷上次拍是什么时候。
                    text = when {
                        usage == null || usage.shots <= 0 -> "这卷还没拍过"
                        usage.lastCapturedAt != null -> "上次拍摄 ${monthDay(usage.lastCapturedAt)}"
                        else -> "拍摄日期未知"
                    },
                    color = p.textSecondary,
                    fontFamily = spec.type.bodyFamily,
                    fontSize = spec.type.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                LoadStateLabel(loaded = loaded, installing = installing)
            }
        }
    }
}

/**
 * `装卷` / `已装卷` / `装卷中…`。
 *
 * 「装卷中…」的呼吸动画是整页唯一的循环动效（900ms 一个来回，`tween(450)` 往返）。
 * **文字本身不动**：纸面上把 12sp 文字压到 45% 透明度只有 2:1 左右，读不出来；
 * 呼吸放在前面那颗 4dp 方点上，文字保持正文对比度。
 */
@Composable
private fun LoadStateLabel(loaded: Boolean, installing: Boolean) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(spec.metrics.chipRadius)

    val transition = rememberInfiniteTransition(label = "load-breath")
    val pulse by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(450), repeatMode = RepeatMode.Reverse),
        label = "load-breath-alpha",
    )

    val active = loaded || installing
    val text = when {
        installing -> "装卷中…"
        loaded -> "已装卷"
        else -> "装卷"
    }
    val textColor = if (active) p.accent else p.textTertiary
    val dotColor = if (active) p.accent else p.textTertiary

    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (active) p.accent.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (active) p.accent.copy(alpha = 0.40f) else p.hairline,
                shape = shape,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    color = when {
                        installing -> dotColor.copy(alpha = pulse)
                        loaded -> dotColor
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(1.dp),
                )
                .then(
                    if (loaded || installing) Modifier else Modifier.border(1.dp, dotColor, RoundedCornerShape(1.dp)),
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = textColor,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.monoSmall,
            maxLines = 1,
        )
    }
}

/**
 * 84×108 的画格：**该卷最新一张真实成片**；没拍过就画这卷本身（胶卷筒）。
 *
 * 有图时保留上下两排齿孔 —— 它是"这是一格胶片"的物证；没有图时纸色打底、墨色筒身。
 * 上一版叠了一道自下而上的暗渐变，那是为了在近黑底上救文字；现在是纸面 + 白边，
 * 渐变属于规格禁止项（§1.1），已删。
 */
@Composable
private fun StockFrame(stock: FilmStock, usage: StockUsage?) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(spec.metrics.cardRadius)
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
            // 未拍过时是一块纸色凹格，墨色胶卷筒压在上面才读得出来；
            // 有照片时这张底就被图盖住，只在图缝里露出胶片黑。
            .background(if (bitmap != null) p.textPrimary else p.appBg)
            .border(1.dp, p.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            SprocketRow(modifier = Modifier.align(Alignment.TopCenter))
            SprocketRow(modifier = Modifier.align(Alignment.BottomCenter))
        } else {
            FilmCanister(label = stock.label, capacity = 0)
        }
    }
}

/** 原型 `.sprocket`：`repeating-linear-gradient`，4px 孔 / 6px 间隔。这里画成墨色方块。 */
@Composable
private fun SprocketRow(modifier: Modifier = Modifier) {
    val p = LocalFilmTheme.current.palette
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
                color = p.textPrimary,
                topLeft = Offset(x, 0f),
                size = Size(hole.coerceAtMost(size.width - x), size.height),
            )
            x += pitch
        }
    }
}

// ---------------------------------------------------------------------------
// 胶卷筒插画（纸色体系）
// ---------------------------------------------------------------------------

/**
 * 58×93 的胶卷筒：墨色筒身 + 浅一档的顶盖/底唇 + 纸白标签。
 *
 * 上一版是"金属筒身 + 奶油标签 + 金色系印刷"，属于 D-063 明令去掉的金属/暖金。
 * 现在只用两个墨色阶（`textPrimary` 筒身、`ink` 盖与唇）和纸白标签：
 * **没有渐变**，边界靠 1px 描边而不是光影 —— 纸面页的层级规则同样
 * 适用于插画（§1.1）。
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
    val p = spec.palette
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

            // 筒身：平涂墨黑。
            drawRoundRect(
                color = p.textPrimary,
                topLeft = Offset(0f, bodyTop),
                size = Size(w, bodyH.toPx()),
                cornerRadius = CornerRadius(9f * s, 9f * s),
            )
            // 筒身外缘：1px 纸白描边。纸面上不靠投影立起来，靠一条亮边。
            drawRoundRect(
                color = p.sheetBg.copy(alpha = 0.20f),
                topLeft = Offset(0f, bodyTop),
                size = Size(w, bodyH.toPx()),
                cornerRadius = CornerRadius(9f * s, 9f * s),
                style = Stroke(width = 1.dp.toPx()),
            )

            // 顶盖：比筒身浅一档的墨（p.ink），加 1px 纸白描边区分边界。
            val capInset = 8f * s
            val capTop = Offset(capInset, 0f)
            val capSize = Size(w - capInset * 2, capH.toPx())
            drawRoundRect(
                color = p.ink,
                topLeft = capTop,
                size = capSize,
                cornerRadius = CornerRadius(3f * s, 3f * s),
            )
            drawRoundRect(
                color = p.sheetBg.copy(alpha = 0.35f),
                topLeft = capTop,
                size = capSize,
                cornerRadius = CornerRadius(3f * s, 3f * s),
                style = Stroke(width = 1.dp.toPx()),
            )

            // 底唇：底边贴住 totalH，往上叠 2dp 压进筒身，接缝不会露缝。
            val lipInset = 14f * s
            val lipTop = Offset(lipInset, totalH.toPx() - lipH.toPx())
            val lipSize = Size(w - lipInset * 2, lipH.toPx())
            drawRoundRect(
                color = p.ink,
                topLeft = lipTop,
                size = lipSize,
                cornerRadius = CornerRadius(2f * s, 2f * s),
            )
            drawRoundRect(
                color = p.sheetBg.copy(alpha = 0.30f),
                topLeft = lipTop,
                size = lipSize,
                cornerRadius = CornerRadius(2f * s, 2f * s),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        // 标签贴纸：left/right 6，top 22，bottom 17（相对筒身）。纸白底 + 墨字。
        Column(
            modifier = Modifier
                .width(bodyW - 12.dp)
                .height(bodyH - 22.dp - 17.dp)
                .offset(x = 6.dp, y = capOver + 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(p.sheetBg)
                .border(1.dp, p.hairline, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            val parts = (label ?: "—").trim().split(Regex("\\s+"), limit = 2)
            // 第二行原本写「400 · 36」，400 是 ISO —— pack 里没有这个字段，属于猜。
            // 现在只印卷名的后半段和真实卷容量；两者都没有就不印那一行。
            val secondLine = listOfNotNull(
                parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                capacity.takeIf { it > 0 }?.toString(),
            ).joinToString(" · ")

            Text(
                text = parts.firstOrNull()?.uppercase() ?: "—",
                color = p.textPrimary,
                // micro-print: 仿真印刷标签，装饰，不承载信息
                fontSize = spec.type.microPrint,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondLine.isNotBlank()) {
                Text(
                    text = secondLine,
                    color = p.textTertiary,
                    // micro-print: 仿真印刷标签，装饰，不承载信息
                    fontSize = spec.type.microPrint,
                    fontFamily = spec.type.monoFamily,
                    letterSpacing = 0.06.em,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 数据与预览
// ---------------------------------------------------------------------------

/**
 * 画幅（`4:3`）。像素尺寸 `4096×3072` 是内部参数、不上屏（D-050），
 * 但"这卷拍出来是什么形状"用户看得懂，所以用真实宽高约分后保留。
 */
private fun aspectLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "—"
    val g = gcd(width, height)
    return "${width / g}:${height / g}"
}

private fun gcd(a: Int, b: Int): Int {
    var x = kotlin.math.abs(a)
    var y = kotlin.math.abs(b)
    while (y != 0) {
        val t = x % y
        x = y
        y = t
    }
    return x.coerceAtLeast(1)
}

/** `10.19`。只取月日：卷是按卷分组的，年份在库里没有信息量。 */
private fun monthDay(epochMillis: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%02d.%02d".format(
        c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.DAY_OF_MONTH),
    )
}

private fun previewStock(id: String, label: String, w: Int, h: Int) = FilmStock(
    id = id,
    label = label,
    // 预览不碰磁盘：不需要（也不该）出现 `.fswp` 文件名。
    file = File("preview-$id"),
    width = w,
    height = h,
    maxHalo = 64,
)

/** 预览里的"上次拍摄"时间：2026-10-19，用于让日期那行有真实排版。 */
private const val PREVIEW_CAPTURED_AT = 1_792_368_000_000L

@Preview(widthDp = 380, heightDp = 900, showBackground = true, backgroundColor = 0xFFECEAE4)
@Composable
private fun FilmLibraryPreview() {
    FilmTheme(FilmPaperSpec) {
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
                "gold200" to StockUsage(
                    shots = 12,
                    latestThumbnail = null,
                    lastCapturedAt = PREVIEW_CAPTURED_AT,
                ),
                "portra400" to StockUsage(
                    shots = 3,
                    latestThumbnail = null,
                    lastCapturedAt = PREVIEW_CAPTURED_AT,
                ),
            ),
        )
    }
}

@Preview(widthDp = 380, heightDp = 900, showBackground = true, backgroundColor = 0xFFECEAE4)
@Composable
private fun FilmLibraryEmptyPreview() {
    FilmTheme(FilmPaperSpec) {
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
