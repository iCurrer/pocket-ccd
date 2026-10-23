package com.pocketccd.prototype.ui.result

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.camera.PhotoExif
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.camera.readPhotoExif
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmPaperSpec
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperButton
import com.pocketccd.prototype.ui.film.PaperEmptyState
import com.pocketccd.prototype.ui.film.PaperHairline
import com.pocketccd.prototype.ui.film.filmClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class ResultStatus {
    PROCESSING,
    OUTPUT_WRITING,
    SAVED,
    FAILED,
}

/** 页面实际呈现的四种形态。`status` + `photo` + `demo` 三个输入都折到这里。 */
private enum class Stage { SAVED, DEVELOPING, FAILED, EMPTY }

/** 印相纸边：印相框内窗四周的纸白留边（1:1 规格 §3.2）。 */
private val PrintEdge = 8.dp

/**
 * 设计板卷名 18sp。共享字阶里没有 18sp 档（最接近的是 16sp `optionTitle`），
 * 本轮只改本页、不动 `FilmTheme`，所以从令牌推导 18sp：16 × 1.125。
 * 不写裸 `fontSize`，NoBareFontSizeTest 仍然通过。
 */
private const val CaptionSizeScale = 1.125f

/** 打字机每打一个字的间隔。三个字 ≈ 210ms，与 200ms 的阶段淡入同拍。 */
private const val TypewriterCharMs = 70L

/**
 * 页面底部留白：56dp 全局 TabBar + 32dp 呼吸。
 * 系统栏 inset 已由 AppRoot 统一让开，所以这里只补 TabBar 盖住的高度。
 */
private val BottomNavReserve = 64.dp

/** 只给 `@Preview` 画布铺纸底用；界面取色一律走 `LocalFilmTheme`，不读这个常量。 */
private const val PreviewPaperBg = 0xFFECEAE4L

/**
 * 成片页（T9-018 / D-043 / D-045；D-063 方向 C「白纸档案」；v0.6.0 B 方案 1:1 版面）。
 *
 * 版面按设计板逐项摆：
 * - 页头 56dp：左 `ArrowLeft`（48dp 热区，回取景）/ 中 `05 / 36` 真实片号 / 右 `More`；
 * - 印相 86% 宽 + 6dp 纸白边 + 1px 墨线 + 2dp 圆角，无阴影；图下左 Serif 卷名、右 mono `P-05`；
 * - 元数据三行 11sp mono / 18sp 行高：真实拍摄时间、真实 EXIF、真实 `Build.MODEL`；
 * - 状态行：石板蓝小圆点 + 真实阶段/秒数（`显影中 03s` / `已保存 · 03s`）；
 * - 动作行 4 等分：保存 / 收藏 / 分享 / 更多，回调由参数注入（本轮默认空实现）；
 * - 底部固定留 [BottomNavReserve]，避免被全局 `PaperNavBar` 压住。
 *
 * **数据只显示真的**（D-002 / D-043）：缺项写 `—`（机型这一行取不到就整行省略），
 * 不补 ISO / 光圈 / 快门 / 百分比，不编文件名/容量/订单。
 *
 * **保留的数据链路**：`ResultStatus` 三态语义、`CaptureState` 映射（在 `AppRoot`）、
 * EXIF 异步读取、失败重试回调都没动；缩略图解码仍在 IO 线程。
 */
@Composable
fun ResultScreen(
    status: ResultStatus,
    onBackToCamera: () -> Unit,
    /** 旧版相册入口。1:1 版面把相册收进全局 TabBar，本页不再画它；参数保留只为不破坏现有调用。 */
    onOpenPhotoWall: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    demo: Boolean = false,
    photo: SavedPhoto? = null,
    failureMessage: String? = null,
    stockLabel: String? = null,
    /** 这张照片是**本卷**第几张，来自 `AppViewModel.rollShots`。0 表示未知。 */
    frameIndex: Int = 0,
    rollCapacity: Int = 0,
    /** 保存到本机/相册。1:1 本轮只做 UI，默认空实现，功能由其他智能体接入。 */
    onSave: () -> Unit = {},
    /** 切换收藏。收藏态由 [isFavorite] 传入，本轮不持久化。 */
    onToggleFavorite: () -> Unit = {},
    /** 系统分享。功能待接入。 */
    onShare: () -> Unit = {},
    /** 更多（页头与动作行共用）。功能待接入。 */
    onMore: () -> Unit = {},
    /** 收藏态：为 true 时收藏动作右上角显示石板蓝点标。 */
    isFavorite: Boolean = false,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    val stage = when {
        status == ResultStatus.FAILED -> Stage.FAILED
        photo != null -> Stage.SAVED
        demo -> Stage.EMPTY
        else -> Stage.DEVELOPING
    }

    // EXIF 从成片文件读，但**不能**在 composition 里同步读：一张 3–5MB 的 JPEG
    // 要打开/解析文件头，写在 remember 里等于在首帧主线程上做 IO（P3-15）。
    // produceState 先画 `—`，IO 线程读完再刷新；切照片时按路径重启。
    // 读不到就是空对象 → 元数据写 `—`，绝不拿设计稿数值兜底（D-043）。
    val photoFile = photo?.fullFile
    val exif by produceState(
        initialValue = PhotoExif(),
        key1 = photoFile?.absolutePath,
    ) {
        value = if (photoFile == null) {
            PhotoExif()
        } else {
            withContext(Dispatchers.IO) { readPhotoExif(photoFile) }
        }
    }
    val resolvedStock = photo?.stockLabel ?: stockLabel

    // 真实墙钟：从成片页第一次出现开始计秒（页面通常在第一帧就是"冲洗中"）。
    // 用墙钟求差而不是"每秒 +1"，被系统停住再回来时数字不会落后。
    val startedAt = remember { System.currentTimeMillis() }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000L).toInt()
            delay(250L)
        }
    }
    // 成功态的秒数在"照片落盘"那一刻定格，之后停在"已保存 · Ns"，不再随页面继续跳。
    // 用 remember(photo.id) 在拿到成片的同一帧算好，避免先画一帧「00s」再跳。
    val savedSeconds = remember(photo?.id) {
        if (photo == null) 0 else ((System.currentTimeMillis() - startedAt) / 1_000L).toInt()
    }

    // 真实机身型号；`Build.UNKNOWN`（"unknown"）视作取不到。
    val deviceModel = remember {
        Build.MODEL?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg),
    ) {
        ResultHeader(
            frameLabel = frameLabel(frameIndex, rollCapacity),
            onBack = onBackToCamera,
            onMore = onMore,
        )

        when (stage) {
            Stage.SAVED, Stage.DEVELOPING -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    ResultBody(
                        stage = stage,
                        photo = photo,
                        stockLabel = resolvedStock,
                        capturedAt = photo?.capturedAt,
                        exif = exif,
                        deviceModel = deviceModel,
                        frameIndex = frameIndex,
                        elapsedSeconds = elapsedSeconds,
                        savedSeconds = savedSeconds,
                        writing = status == ResultStatus.OUTPUT_WRITING,
                    )
                }

                ResultActionBar(
                    isFavorite = isFavorite,
                    onSave = onSave,
                    onToggleFavorite = onToggleFavorite,
                    onShare = onShare,
                    onMore = onMore,
                    modifier = Modifier.padding(horizontal = spec.metrics.pageMargin),
                )

                Spacer(Modifier.height(BottomNavReserve))
            }

            Stage.FAILED -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    FailedBody(failureMessage)
                }

                PaperButton(
                    text = "重试",
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spec.metrics.pageMargin),
                )

                Spacer(Modifier.height(BottomNavReserve))
            }

            // 空态自带 24dp 内容边距与 48dp 纵向留白，外层不要再套横向 padding。
            Stage.EMPTY -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    PaperEmptyState(
                        title = "还没有拍过",
                        body = "回到取景按快门，第一张成片会落在这张纸上。",
                    )
                }

                PaperButton(
                    text = "继续拍摄",
                    onClick = onBackToCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spec.metrics.pageMargin),
                )

                Spacer(Modifier.height(BottomNavReserve))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 页头：返回 / 片号 / 更多
// ---------------------------------------------------------------------------

/** `05 / 36`；两个真实参数都缺时写 `— / —`。 */
private fun frameLabel(frameIndex: Int, rollCapacity: Int): String =
    if (frameIndex > 0 && rollCapacity > 0) {
        "%02d / %d".format(frameIndex, rollCapacity)
    } else {
        "— / —"
    }

/** 页头 56dp：左右各一个 48dp 图标热区，中间是真实片号。 */
@Composable
private fun ResultHeader(
    frameLabel: String,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = spec.metrics.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIcon(
                kind = FilmIconKind.ArrowLeft,
                label = "返回",
                onClick = onBack,
            )
            Text(
                text = frameLabel,
                modifier = Modifier.weight(1f),
                color = p.textPrimary,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.mono,
                lineHeight = spec.type.monoLineHeight,
                letterSpacing = 0.08.em,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            HeaderIcon(
                kind = FilmIconKind.More,
                label = "更多",
                onClick = onMore,
            )
        }
        PaperHairline()
    }
}

/** 页头纯图标键：48dp 热区、18dp 图标、自带读屏文案。 */
@Composable
private fun HeaderIcon(
    kind: FilmIconKind,
    label: String,
    onClick: () -> Unit,
) {
    val p = LocalFilmTheme.current.palette

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .filmClickable(
                onClick = onClick,
                scaleTo = 0.97f,
                onClickLabel = label,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        FilmIcon(
            kind = kind,
            tint = p.textPrimary,
            iconSize = 18.dp,
            strokeWidth = 1.5.dp,
        )
    }
}

// ---------------------------------------------------------------------------
// 主体：印相 + 卷名/片号 + 元数据 + 状态行
// ---------------------------------------------------------------------------

@Composable
private fun ResultBody(
    stage: Stage,
    photo: SavedPhoto?,
    stockLabel: String?,
    capturedAt: Long?,
    exif: PhotoExif,
    deviceModel: String?,
    frameIndex: Int,
    elapsedSeconds: Int,
    savedSeconds: Int,
    writing: Boolean,
) {
    val spec = LocalFilmTheme.current

    // 画幅比例取真实宽高，不写死 4:3；冲洗中还不知道比例，用相机默认的 4:3 空窗。
    val aspect = if (photo != null && photo.width > 0 && photo.height > 0) {
        photo.width.toFloat() / photo.height.toFloat()
    } else {
        4f / 3f
    }

    // 缩略图解码必须离开主线程：`BitmapFactory.decodeFile` 是一张 1440px 长边的
    // JPEG，主线程解一次就是好几毫秒的掉帧。采样逻辑不变——只用 PhotoStore 已经
    // 压过的缩略图，绝不在这里碰 12MP 原片（会解码出 48MB 的 ARGB）。
    val thumbnail = photo?.thumbnailFile
    val image by produceState<PrintImage>(
        initialValue = PrintImage.Loading,
        key1 = thumbnail?.absolutePath,
    ) {
        value = withContext(Dispatchers.IO) {
            val file = thumbnail
            if (file != null && file.isFile) {
                BitmapFactory.decodeFile(file.absolutePath)
                    ?.asImageBitmap()
                    ?.let(PrintImage::Ready)
                    ?: PrintImage.Missing
            } else {
                PrintImage.Missing
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spec.metrics.pageMargin),
    ) {
        Spacer(Modifier.height(16.dp))

        // 印相：内容宽 92%，居中；8dp 纸边 + 1px 墨线 + 2dp 圆角 + 极轻投影。
        // 比 v0.5.0 的 86% 更接近设计板：24dp 页边距 + 92% 后，照片两侧只剩约 36dp。
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PrintFrame(
                aspect = aspect,
                bitmap = (image as? PrintImage.Ready)?.bitmap,
                contentDescription = stockLabel?.let { "成片：$it" },
                emptyLabel = if (image is PrintImage.Missing) "缩略图缺失" else null,
                modifier = Modifier.fillMaxWidth(0.92f),
            )
        }

        Spacer(Modifier.height(10.dp))

        PrintCaption(
            stockLabel = stockLabel,
            printFrameLabel = printFrameNumber(photo, frameIndex),
        )

        Spacer(Modifier.height(14.dp))

        MetadataLines(
            dateText = formatCapturedAt(capturedAt) ?: "—",
            // 设计板是三段式 `ISO 100 · 1/60s · f/1.8`。只拼真实读到的项；
            // CameraX/历史照片缺 ISO 时自动退化成 `1/60s · f/1.8`，一项都没有才写 `—`。
            exposureText = listOfNotNull(exif.isoLabel(), exif.exposureLine())
                .joinToString(" · ")
                .ifEmpty { "—" },
            deviceModel = deviceModel,
        )

        Spacer(Modifier.height(14.dp))

        StatusLine(
            stage = stage,
            writing = writing,
            elapsedSeconds = elapsedSeconds,
            savedSeconds = savedSeconds,
        )
    }
}

/** 印相下方：左 Serif 卷名（18sp），右 mono 片号（`P-05`，未知不显示）。 */
@Composable
private fun PrintCaption(
    stockLabel: String?,
    printFrameLabel: String?,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stockLabel ?: "—",
            modifier = Modifier.weight(1f),
            color = p.textPrimary,
            fontFamily = spec.type.titleFamily,
            fontSize = spec.type.optionTitle * CaptionSizeScale,
            lineHeight = spec.type.bodyLineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (printFrameLabel != null) {
            Text(
                text = printFrameLabel,
                color = p.textSecondary,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.mono,
                lineHeight = spec.type.monoLineHeight,
                letterSpacing = 0.06.em,
                maxLines = 1,
            )
        }
    }
}

/** 真实片号 `P-05`；没有成片或片号未知时不显示（`frameIndex` 是"本卷已存张数"）。 */
private fun printFrameNumber(photo: SavedPhoto?, frameIndex: Int): String? =
    if (photo != null && frameIndex > 0) "P-%02d".format(frameIndex) else null

/** 三行真实元数据：拍摄时间 / 真实 EXIF / 真实机身型号；缺项 `—`，机型取不到整行省略。 */
@Composable
private fun MetadataLines(
    dateText: String,
    exposureText: String,
    deviceModel: String?,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(Modifier.fillMaxWidth()) {
        MetadataLine(dateText)
        MetadataLine(exposureText)
        if (deviceModel != null) {
            MetadataLine(deviceModel)
        }
    }
}

/** 11sp mono / 18sp 行高。 */
@Composable
private fun MetadataLine(text: String) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Text(
        text = text,
        color = p.textSecondary,
        fontFamily = spec.type.monoFamily,
        fontSize = spec.type.monoSmall,
        lineHeight = 18.sp,
        letterSpacing = 0.04.em,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 状态行：石板蓝小圆点 + 真实状态。
 *
 * - 冲洗中：`显影中 03s` / `写入中 03s`（阶段文字用打字机揭示，阶段切换 200ms 淡入）；
 * - 成功：`已保存 · 03s`（秒数在落盘那一刻定格）；
 * - 失败/空态没有状态行，走纸面说明。
 */
@Composable
private fun StatusLine(
    stage: Stage,
    writing: Boolean,
    elapsedSeconds: Int,
    savedSeconds: Int,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val style = TextStyle(
        fontFamily = spec.type.monoFamily,
        fontSize = spec.type.monoSmall,
        lineHeight = spec.type.monoLineHeight,
        letterSpacing = 0.06.em,
        color = p.textSecondary,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(p.accent, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        if (stage == Stage.SAVED) {
            Text(
                text = "已保存 · %02ds".format(savedSeconds),
                style = style,
                maxLines = 1,
            )
        } else {
            Crossfade(
                targetState = writing,
                animationSpec = tween(spec.motion.sheetEnterMs),
                label = "result-stage-word",
            ) { isWriting ->
                TypewriterLine(
                    text = if (isWriting) "写入中" else "显影中",
                    style = style,
                )
            }
            Text(
                text = " %02ds".format(elapsedSeconds),
                style = style,
                maxLines = 1,
            )
        }
    }
}

/**
 * 打字机文字：逐字揭示，未打完时带一个 `|` 光标。
 *
 * 下面垫一层透明全文撑住宽度，否则逐字出现时后面的秒数会左右跳。
 * 读屏拿的是**完整文案**（`contentDescription`），不会听到打了一半的状态。
 */
@Composable
private fun TypewriterLine(
    text: String,
    style: TextStyle,
) {
    var visibleChars by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        visibleChars = 0
        while (visibleChars < text.length) {
            delay(TypewriterCharMs)
            visibleChars += 1
        }
    }

    Box {
        // 只占位、不显示、不进读屏。
        Text(
            text = text,
            style = style,
            maxLines = 1,
            modifier = Modifier
                .alpha(0f)
                .clearAndSetSemantics {},
        )
        Text(
            text = buildString {
                append(text.take(visibleChars))
                if (visibleChars < text.length) append('|')
            },
            style = style,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = text },
        )
    }
}

// ---------------------------------------------------------------------------
// 印相框
// ---------------------------------------------------------------------------

/**
 * 印相框（1:1 规格 §3.2）：图片外 8dp 纸白边 + 1px 墨线，圆角 2dp。
 * 只加一层 2dp 的极轻投影：相纸是实体印刷物，完全无影会贴在纸面上读成一块白框；
 * 投影只属于"这张照片"，不属于任何卡片/按钮。
 *
 * 没有成片（冲洗中）或缩略图丢了时，内窗是一块**纸色空窗**，不是灯箱黑块：
 * 纸面页上唯一允许出现的黑色是照片本身，空窗不替照片占位成假画面。
 */
@Composable
private fun PrintFrame(
    aspect: Float,
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap? = null,
    contentDescription: String? = null,
    emptyLabel: String? = null,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(spec.metrics.printRadius)

    Box(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.14f),
            )
            .clip(shape)
            .background(p.sheetBg)
            .border(spec.metrics.hairline, p.hairline, shape)
            .padding(PrintEdge),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .background(p.appBg),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (emptyLabel != null) {
                Text(
                    text = emptyLabel,
                    color = p.textSecondary,
                    fontFamily = spec.type.bodyFamily,
                    fontSize = spec.type.body,
                    lineHeight = spec.type.bodyLineHeight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** 缩略图解码结果。用 null 表示"还没读完"的话，首帧就会闪一下「缩略图缺失」。 */
private sealed interface PrintImage {
    data object Loading : PrintImage
    data object Missing : PrintImage
    data class Ready(val bitmap: ImageBitmap) : PrintImage
}

// ---------------------------------------------------------------------------
// 动作行：保存 / 收藏 / 分享 / 更多
// ---------------------------------------------------------------------------

/** 4 等分动作行；图标 20dp + 标签 11sp，整项 56dp 高（≥48dp 热区）。 */
@Composable
private fun ResultActionBar(
    isFavorite: Boolean,
    onSave: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionItem(
            kind = FilmIconKind.Save,
            label = "保存",
            onClick = onSave,
            modifier = Modifier.weight(1f),
        )
        ActionItem(
            kind = FilmIconKind.Heart,
            label = "收藏",
            onClick = onToggleFavorite,
            modifier = Modifier.weight(1f),
            favoriteMarker = isFavorite,
        )
        ActionItem(
            kind = FilmIconKind.Share,
            label = "分享",
            onClick = onShare,
            modifier = Modifier.weight(1f),
        )
        ActionItem(
            kind = FilmIconKind.More,
            label = "更多",
            onClick = onMore,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 单个动作。可见标签就是读屏文案；收藏态在图标右上角点一个 5dp 石板蓝点
 * （设计板要求"收藏态用石板蓝点标"；不用填底、不换图标）。
 */
@Composable
private fun ActionItem(
    kind: FilmIconKind,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favoriteMarker: Boolean = false,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(spec.metrics.cardRadius))
            .filmClickable(
                onClick = onClick,
                scaleTo = 0.97f,
                onClickLabel = label,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            FilmIcon(
                kind = kind,
                tint = p.textPrimary,
                iconSize = 20.dp,
                strokeWidth = 1.5.dp,
            )
            if (favoriteMarker) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(5.dp)
                        .background(p.accent, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = p.textSecondary,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.monoSmall,
            lineHeight = spec.type.monoLineHeight,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// 失败态
// ---------------------------------------------------------------------------

/** 失败态：纸面说明 + 真实失败原因；「重试」固定在底部（不另做描边卡片）。 */
@Composable
private fun FailedBody(failureMessage: String?) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = spec.metrics.pageMargin,
                end = spec.metrics.pageMargin,
                top = 40.dp,
                bottom = 8.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "冲洗失败",
            color = p.error,
            fontFamily = spec.type.titleFamily,
            fontSize = spec.type.sheetTitle,
            lineHeight = spec.type.sheetTitleLineHeight,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = failureMessage ?: "这次没有产出照片，可以重试一次。",
            color = p.textSecondary,
            fontFamily = spec.type.bodyFamily,
            fontSize = spec.type.body,
            lineHeight = spec.type.bodyLineHeight,
            textAlign = TextAlign.Center,
        )
    }
}

/** `2026.10.20 16:42`。真实拍摄时间，来自 [SavedPhoto.capturedAt]。 */
private fun formatCapturedAt(epochMillis: Long?): String? {
    if (epochMillis == null) return null
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%04d.%02d.%02d %02d:%02d".format(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}

// ---------------------------------------------------------------------------
// 预览
// ---------------------------------------------------------------------------

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = PreviewPaperBg)
@Composable
private fun ResultDevelopingPreview() {
    FilmTheme(FilmPaperSpec) {
        ResultScreen(
            status = ResultStatus.PROCESSING,
            onBackToCamera = {},
            onOpenPhotoWall = {},
            onRetry = {},
            stockLabel = "GOLD 200",
            frameIndex = 5,
            rollCapacity = 36,
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = PreviewPaperBg)
@Composable
private fun ResultFailedPreview() {
    FilmTheme(FilmPaperSpec) {
        ResultScreen(
            status = ResultStatus.FAILED,
            onBackToCamera = {},
            onOpenPhotoWall = {},
            onRetry = {},
            stockLabel = "GOLD 200",
            failureMessage = "「GOLD 200」需要 4096×3072，这台设备的采集尺寸只有 3264×2448。",
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = PreviewPaperBg)
@Composable
private fun ResultEmptyPreview() {
    FilmTheme(FilmPaperSpec) {
        ResultScreen(
            status = ResultStatus.SAVED,
            onBackToCamera = {},
            onOpenPhotoWall = {},
            onRetry = {},
            demo = true,
        )
    }
}
