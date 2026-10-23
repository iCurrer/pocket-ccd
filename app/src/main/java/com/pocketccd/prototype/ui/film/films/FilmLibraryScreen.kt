package com.pocketccd.prototype.ui.film.films

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pocketccd.prototype.fotufilm.FilmStock
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmPaperSpec
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperEmptyState
import com.pocketccd.prototype.ui.film.PaperHairline
import com.pocketccd.prototype.ui.film.filmClickable
import java.io.File

/**
 * 某款胶卷在本机上的使用记录。用来回答「这卷我拍过吗、拍了多少」。
 *
 * 只统计**照片墙上真实存在的成片**，不推断；[latestThumbnail] 取该卷最新一张的
 * 缩略图，用作 CURRENT 卡与列表行的画格。
 */
data class StockUsage(
    val shots: Int,
    val latestThumbnail: File?,
    /** 该卷最新一张的拍摄时间。1:1 版面里显示成 `2026.10.20`。 */
    val lastCapturedAt: Long? = null,
)

/**
 * 纸面页底部为 `PaperNavBar` 预留的高度：56dp 导航条 + 24dp 呼吸 + 系统栏余量（§1）。
 *
 * AppRoot 把 TabBar 叠在内容之上，页面不自己留白的话最后一行会被压住。
 */
private val BOTTOM_CONTENT_INSET = 88.dp

/**
 * 胶卷库：方向 C「白纸档案」1:1 版面（B 方案，`docs/paper-archive-1to1-layout.md` §2/§5）。
 *
 * 整页结构只有三样东西，自上而下：
 *
 * 1. **页头**：左 `Film Library`（Serif 大标题），右 `+`（48dp 热区，接 [onRefresh] 真实重扫）。
 *    没有返回键 —— 纸面页统一由 TabBar + 系统返回离开（本页 `BackHandler` → [onBack]）；
 *    取景页仍是启动页，不在这页之上再开一条"回取景"的按钮，见 1:1 版面规格 §2。
 * 2. **CURRENT 卡**：整页唯一的墨黑块（ink 底、纸白字、2dp 圆角、无阴影）。左边一格
 *    真实成片缩略图（没拍过就画这卷的胶卷筒），右边卷名、`05 / 36`、36 帧点阵、
 *    `READY/FULL`。它只负责"现在装着哪卷、还剩几格"，不是按钮 —— 旧版的
 *    「继续拍摄 / 开新卷」在 1:1 版面上没有位置（§5 只列了这些内容）。
 * 3. **胶卷列表**：56×72 真实缩略图 + 卷名 14sp + `N 张 · 2026.10.20` mono + 右箭头；
 *    行高 ≥72dp，行间 1px hairline，没有卡片底、没有圆角大卡（纸面页靠规则线与留白分层）。
 *
 * **只列真实 pack**：列表数据完全来自 `FilmStockCatalog.scan` 的结果，没有 Fuji/Ilford
 * pack 就不出现，不补"敬请期待"式假卷（§5.4 / §9 数据诚实红线）。卷名同样不补品牌：
 * pack 里只有 `GOLD 200`，就不写成 `KODAK GOLD 200`。
 *
 * 字号取令牌：页头标题走 `spec.type.sheetTitle`（`FilmPaperSpec` 已按 1:1 设计板抬到
 * 26sp / 32 行高）；列表副行设计稿是 10sp，落到 `spec.type.monoSmall`（11sp，令牌下限）。
 * 不写裸 `fontSize`——`NoBareFontSizeTest` 会拦。
 *
 * 底部留 88dp：AppRoot 在纸面页底部叠了 `PaperNavBar`（56dp + 24dp 呼吸 + 系统栏），
 * 内容不提前留白就会被 TabBar 压住（§1）。
 *
 * 画格里放什么：**该卷最新一张真实成片**；没拍过就画胶卷筒。原型的风景画是"这卷大概
 * 长这样"的示意，我们没法凭空生成，也不该编（D-002）。
 *
 * 不显示的字段（D-050）：`.fswp` 文件名、pack 体积、`4096×3072` 像素尺寸、
 * 画幅比例。它们真实，但没有一条参与用户的决定。
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

    // 1:1 版面没有返回键：纸面页之间由 TabBar 平级切换，系统返回在同一层收口。
    // 这里显式接一下，保证本页在返回栈上的行为不依赖外层实现（与照片墙一致）。
    BackHandler { onBack() }

    // 点选胶卷**不立刻切页**：先在本页把"装卷"演完（900ms），CURRENT 卡用淡入/上滑换卷。
    // 之后由用户自己离开（系统返回 / TabBar）—— 换卷是有实体的动作，秒切等于什么都没发生。
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
        LibraryHeader(onRefresh = onRefresh)

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
            // 88dp = PaperNavBar 的 56 + 24 呼吸 + 系统栏余量（§1）。
            contentPadding = PaddingValues(bottom = BOTTOM_CONTENT_INSET),
        ) {
            item(key = "current") {
                Box(
                    modifier = Modifier.padding(
                        start = spec.metrics.pageMargin,
                        end = spec.metrics.pageMargin,
                        top = 20.dp,
                        bottom = 8.dp,
                    ),
                ) {
                    // 换卷：旧卷淡出、新卷淡入上滑（240ms / 页退出 120ms，走 motion 令牌）。
                    AnimatedContent(
                        targetState = active?.id,
                        transitionSpec = {
                            (fadeIn(tween(spec.motion.sheetEnterMs)) +
                                slideInVertically(tween(spec.motion.sheetEnterMs)) { it / 8 }) togetherWith
                                (fadeOut(tween(spec.motion.pageExitMs)) +
                                    slideOutVertically(tween(spec.motion.pageExitMs)) { -it / 10 })
                        },
                        label = "current-roll-change",
                    ) { id ->
                        val shown = stocks.firstOrNull { it.id == id } ?: active
                        if (shown != null) {
                            CurrentRollCard(
                                stock = shown,
                                usage = usage[shown.id],
                                shotsTaken = if (shown.id == active?.id) shotsTaken else 0,
                                rollCapacity = rollCapacity,
                            )
                        }
                    }
                }
            }

            itemsIndexed(items = stocks, key = { _, stock -> stock.id }) { index, stock ->
                FilmRow(
                    stock = stock,
                    active = stock.id == active?.id,
                    usage = usage[stock.id],
                    installing = installingId == stock.id,
                    onClick = {
                        // 只装卷，不跳页。已装卷那行点下去没有意义，直接吞掉。
                        if (installingId == null && stock.id != active?.id) {
                            installingId = stock.id
                            onSelect(stock.id)
                        }
                    },
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
// 页头
// ---------------------------------------------------------------------------

/**
 * 页头：`Film Library` + 右侧 `+`。高 64dp、左右 24dp、下接 1px hairline（§2）。
 *
 * 不用 [com.pocketccd.prototype.ui.film.PaperPageHeader]：那个组件给返回键固定留了
 * 48dp 缩进，而 1:1 版面的标题要贴左边 24dp；这页也没有返回键。
 */
@Composable
private fun LibraryHeader(onRefresh: () -> Unit) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = spec.metrics.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Film Library",
                color = p.textPrimary,
                fontFamily = spec.type.titleFamily,
                fontSize = spec.type.sheetTitle,
                lineHeight = spec.type.sheetTitleLineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RescanButton(onClick = onRefresh)
        }
        PaperHairline()
    }
}

/**
 * 页头右侧的 `+`。它接的是已有的 [onRefresh]（真实重扫目录），不是新的导入功能。
 *
 * `FilmIconKind` 里没有加号，所以就地画两条 1.5dp 线（18dp 视觉 / 48dp 热区，§2/§3），
 * 不为了一个 `+` 去改共享图标表（那会让 `InteractiveIconLabelsTest` 的语义映射多一项）。
 */
@Composable
private fun RescanButton(onClick: () -> Unit) {
    val p = LocalFilmTheme.current.palette

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .filmClickable(
                onClick = onClick,
                scaleTo = 0.97f,
                onClickLabel = "重新扫描胶卷",
            )
            .semantics { contentDescription = "重新扫描胶卷" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 1.5.dp.toPx()
            drawLine(
                color = p.textPrimary,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = stroke,
            )
            drawLine(
                color = p.textPrimary,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = stroke,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// CURRENT 卡（墨黑）
// ---------------------------------------------------------------------------

/**
 * 墨黑 CURRENT 卡：ink 底、纸白字、2dp 圆角、无阴影、无渐变（§5.2）。
 *
 * 版式两行：上面 `CURRENT` + `READY/FULL`，下面左缩略图、右卷名 / `05 / 36` / 36 帧点阵。
 * 它是**信息卡**：1:1 版面里没有「继续拍摄 / 开新卷」按钮，所以这里也没有任何可点区域，
 * 不会出现"卡片能点但不知道点了会去哪"的隐藏交互。
 */
@Composable
private fun CurrentRollCard(
    stock: FilmStock?,
    usage: StockUsage?,
    shotsTaken: Int,
    rollCapacity: Int,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shots = shotsTaken.coerceIn(0, rollCapacity)
    val full = rollCapacity > 0 && shots >= rollCapacity
    val shape = RoundedCornerShape(spec.metrics.cardRadius)
    // 墨底上唯一的文字色：纸白。60% 的那两处是标签，正文用满值。
    val onInk = p.sheetBg

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.textPrimary)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CURRENT",
                color = onInk.copy(alpha = 0.62f),
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.label,
                letterSpacing = 0.18.em,
            )
            Spacer(Modifier.weight(1f))
            RollStateMark(full = full, tint = onInk)
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RollThumb(
                stock = stock,
                usage = usage,
                width = 72.dp,
                height = 92.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // 真实卷名（pack 里没有品牌字段，所以不写 KODAK）。
                    text = stock?.label ?: "—",
                    color = onInk,
                    fontFamily = spec.type.titleFamily,
                    fontSize = spec.type.optionTitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "%02d / %02d".format(shots, rollCapacity),
                    color = onInk.copy(alpha = 0.70f),
                    fontFamily = spec.type.monoFamily,
                    fontSize = spec.type.mono,
                    letterSpacing = 0.08.em,
                )
                Spacer(Modifier.height(10.dp))
                FrameDots(
                    shots = shots,
                    capacity = rollCapacity,
                    exposed = onInk,
                    rest = onInk.copy(alpha = 0.25f),
                )
            }
        }
    }
}

/**
 * `READY` / `FULL`：墨底上的状态。
 *
 * 只用纸白一种颜色 + 实心/空心 4dp 方点做双编码：墨底上放不下第二个可读色
 * （石板蓝、错误红在 #16181A 上都不到 4.5:1），文字本身已经说清了状态。
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
            fontSize = spec.type.monoSmall,
            letterSpacing = 0.14.em,
        )
    }
}

/**
 * 36 格帧点阵：已曝光纸白、未曝光白 25%（§5.2）。
 *
 * 它比进度条更能读出"还剩几格"，也是整页最像胶片的东西。墨底与纸面两种底色各传一组
 * 颜色进来，组件本身不判断页面材质。
 */
@Composable
private fun FrameDots(
    shots: Int,
    capacity: Int,
    exposed: Color,
    rest: Color,
    modifier: Modifier = Modifier,
) {
    if (capacity <= 0) return
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
                        color = if (i < shots) exposed else rest,
                        shape = dotShape,
                    ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 胶卷列表行
// ---------------------------------------------------------------------------

/**
 * 一行胶卷：56×72 缩略图 + 卷名 14sp + `N 张 · 2026.10.20` mono + 右箭头（§5.3）。
 *
 * 没有卡片底：行与行之间只有一条 hairline（外面负责画），层级靠文号与留白。
 * 整行可点（热区 ≥72dp），点下去只装卷；「装卷中…」的呼吸点会顶掉箭头。
 */
@Composable
private fun FilmRow(
    stock: FilmStock,
    active: Boolean,
    usage: StockUsage?,
    installing: Boolean,
    onClick: () -> Unit,
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
                onClickLabel = if (active) "当前胶卷" else "装入这卷",
            )
            .padding(vertical = 8.dp)
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RollThumb(
            stock = stock,
            usage = usage,
            width = 56.dp,
            height = 72.dp,
        )

        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stock.label,
                color = p.textPrimary,
                fontFamily = spec.type.bodyFamily,
                fontSize = spec.type.value,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                // 真实张数 · 真实上次拍摄日期；没拍过就直说没拍过。
                text = rowMeta(usage),
                color = p.textTertiary,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.monoSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))
        if (installing) {
            InstallingMark()
        } else {
            FilmIcon(
                kind = FilmIconKind.ChevronRight,
                tint = p.textTertiary,
                iconSize = 16.dp,
                strokeWidth = 1.5.dp,
            )
        }
    }
}

/**
 * 「装卷中…」：900ms 一个来回的呼吸（`tween(450)` + Reverse）。
 *
 * **文字本身不动**：纸面上把 11sp 文字压到 45% 透明度只有约 2:1，读不出来；
 * 呼吸放在前面那颗 4dp 的点上，文字保持正文对比度。
 */
@Composable
private fun InstallingMark() {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val transition = rememberInfiniteTransition(label = "install-breath")
    val pulse by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(450), repeatMode = RepeatMode.Reverse),
        label = "install-breath-alpha",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(p.accent.copy(alpha = pulse), RoundedCornerShape(1.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "装卷中…",
            color = p.accent,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.monoSmall,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// 缩略图 / 胶卷筒
// ---------------------------------------------------------------------------

/**
 * 真实成片缩略图；没有成片就画这卷的胶卷筒。
 *
 * 底色固定纸白（`sheetBg`）：在墨黑的 CURRENT 卡上它是一块"相纸窗"，在纸面列表里
 * 它是一格浅一档的相格——两种底色下墨色胶卷筒都读得出来。
 *
 * 只解到 4 倍降采样：CURRENT 槽位 72×92，3 倍屏也只要 216×276 像素，而缩略图长边
 * 1440，整张解出来是白占内存。
 */
@Composable
private fun RollThumb(
    stock: FilmStock?,
    usage: StockUsage?,
    width: Dp,
    height: Dp,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(spec.metrics.cardRadius)
    val thumb = usage?.latestThumbnail

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
            .size(width = width, height = height)
            .clip(shape)
            .background(p.sheetBg)
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
        } else {
            // 筒身宽度取"宽度 78%"和"高度 62.4%"的较小值：58:93 的比例在两种槽位里
            // 都不会顶到画格边缘。
            FilmCanister(
                label = stock?.label,
                capacity = 0,
                bodyWidth = minOf(width * 0.78f, height * 0.624f),
            )
        }
    }
}

/**
 * 胶卷筒插画：墨色筒身 + 浅一档的顶盖/底唇 + 纸白标签（58:93 的实物比例）。
 *
 * 金属渐变与奶油/金色标签在 D-063 已被去掉：纸面页的层级只允许 1px 描边与纸色差。
 * 全部尺寸从 [bodyWidth] 按 58 的基准等比推导，所以同一支筒能画进 56×72 的列表行，
 * 也能画进 72×92 的 CURRENT 卡。
 *
 * 标签在 [bodyWidth] < 52dp 时不画：9sp 的仿真印刷在 44dp 的筒身上只会剩下半截字，
 * 而它本来就是装饰、不承载信息。
 */
@Composable
private fun FilmCanister(
    label: String?,
    capacity: Int,
    bodyWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val bodyH = bodyWidth * (84f / 58f)
    val capH = bodyWidth * (7f / 58f)
    val capOver = bodyWidth * (5f / 58f)
    val lipH = bodyWidth * (6f / 58f)
    // 顶盖比筒身高出 5dp、底唇比筒身低 4dp，所以测量高度是 93/58 而不是 84/58。
    val totalH = bodyWidth * (93f / 58f)

    Box(modifier = modifier.size(width = bodyWidth, height = totalH)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val s = w / 58f
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

            // 底唇：底边贴住 totalH，往上叠一点压进筒身，接缝不会露缝。
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

        if (bodyWidth >= 52.dp) {
            // 标签贴纸：左右 6/58、上 22/58、下 17/58（相对筒身）。纸白底 + 墨字。
            Column(
                modifier = Modifier
                    .width(bodyWidth * (46f / 58f))
                    .height(bodyWidth * (45f / 58f))
                    .offset(x = bodyWidth * (6f / 58f), y = capOver + bodyWidth * (22f / 58f))
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
}

// ---------------------------------------------------------------------------
// 数据与预览
// ---------------------------------------------------------------------------

/**
 * 列表副行：`12 张 · 2026.10.20`。
 *
 * 张数是**这卷在本机拍过的张数**（`usage.shots`），不是容量；容量已经由 CURRENT 卡的
 * `05 / 36` 回答过了。没拍过就只说没拍过，不补一个 0。
 */
private fun rowMeta(usage: StockUsage?): String {
    val shots = usage?.shots ?: 0
    val date = usage?.lastCapturedAt?.let { captureDate(it) }
    return when {
        shots <= 0 -> "未拍过"
        date != null -> "$shots 张 · $date"
        else -> "$shots 张"
    }
}

/** `2026.10.20`。真实拍摄时间；年份保留 —— 胶卷是长期档案，不是当日流水。 */
private fun captureDate(epochMillis: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%04d.%02d.%02d".format(
        c.get(java.util.Calendar.YEAR),
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

/** 预览里的"上次拍摄"时间：2026-10-20，用于让日期那行有真实排版。 */
private const val PREVIEW_CAPTURED_AT = 1_792_454_400_000L

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
            shotsTaken = 5,
            rollCapacity = 36,
            onBack = {},
            onSelect = {},
            onNewRoll = {},
            onRefresh = {},
            usage = mapOf(
                "gold200" to StockUsage(
                    shots = 5,
                    latestThumbnail = null,
                    lastCapturedAt = PREVIEW_CAPTURED_AT,
                ),
                "portra400" to StockUsage(
                    shots = 12,
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
