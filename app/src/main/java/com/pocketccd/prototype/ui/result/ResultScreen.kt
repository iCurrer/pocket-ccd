package com.pocketccd.prototype.ui.result

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pocketccd.prototype.camera.PhotoExif
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.camera.readPhotoExif
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmPaperSpec
import com.pocketccd.prototype.ui.film.FilmRoundButton
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperButton
import com.pocketccd.prototype.ui.film.PaperEmptyState
import com.pocketccd.prototype.ui.film.PaperHairline
import com.pocketccd.prototype.ui.film.PaperPageHeader
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

/** 印相纸边：印相框内窗四周的纸白留边（规格 §4 `PrintFrame`）。 */
private val PrintEdge = 6.dp

/** 只给 `@Preview` 画布铺纸底用；界面取色一律走 `LocalFilmTheme`，不读这个常量。 */
private const val PreviewPaperBg = 0xFFECEAE4L

/** 打字机每打一个字的间隔。三个字 ≈ 210ms，与 200ms 的阶段淡入同拍。 */
private const val TypewriterCharMs = 70L

/**
 * 成片页（T9-018 / D-043 / D-045；视觉方向 C「白纸档案」D-063）。
 *
 * 整页是一张相纸：页头（Serif「成片」+ mono 真实卷名/片号）、居中的印相
 * （88% 宽、6dp 纸白边、1px 墨线、2dp 圆角、**无阴影**）、三行 colophon
 * （DATE / EXPOSURE / FILM，值来自真实文件与 EXIF）、底部墨色 `PaperButton`。
 *
 * **数据只显示真的**（D-002 / D-043）：
 * - 画幅比例取 [SavedPhoto.width]/[SavedPhoto.height]，不写死 4:3；
 * - colophon 全部来自 [readPhotoExif] 与 [SavedPhoto.capturedAt]，读不到显示 `—`；
 * - 副题的 `FRAME 05 / 36` 只在拿到真实本卷片号（[frameIndex]）时才拼；
 * - 不显示 `renderMillis` / `strips` / 文件路径 / 像素尺寸这类内部参数，
 *   也不编 ISO / 光圈 / 百分比。
 *
 * **保留的数据链路**：`ResultStatus` 三态语义、`CaptureState` 的映射（在 `AppRoot`）、
 * EXIF 异步读取、失败重试回调都没动，这次只换版式与样式。
 */
@Composable
fun ResultScreen(
    status: ResultStatus,
    onBackToCamera: () -> Unit,
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
    // 读不到就是空对象 → colophon 显示 `—`，绝不拿设计稿数值兜底（D-043）。
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
    val actions = resultActions(
        stage = stage,
        onBackToCamera = onBackToCamera,
        onOpenPhotoWall = onOpenPhotoWall,
        onRetry = onRetry,
    )

    // 整页纸面可滚：200% 字体或小屏时内容变高，底部的操作必须还能滚到。
    // 页头也一起滚 —— 纸面页不需要常驻的标题栏。
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg)
            .verticalScroll(rememberScrollState()),
    ) {
        PaperPageHeader(
            title = "成片",
            // 缺失的项直接省略：不拿 `FRAME 00 / 0` 充数。
            subtitle = resultSubtitle(
                photo = photo,
                stockLabel = resolvedStock,
                frameIndex = frameIndex,
                rollCapacity = rollCapacity,
            ),
            onBack = onBackToCamera,
            trailing = {
                FilmRoundButton(
                    icon = FilmIconKind.ImageMultiple,
                    onClick = onOpenPhotoWall,
                )
            },
        )

        Spacer(Modifier.height(16.dp))

        when (stage) {
            // stage == SAVED 由 `photo != null` 推出，这里显式取一次带类型收窄的值。
            Stage.SAVED -> photo?.let { saved ->
                SavedPrint(
                    photo = saved,
                    stockLabel = resolvedStock,
                    exif = exif,
                    modifier = Modifier.padding(horizontal = spec.metrics.pageMargin),
                )
            }

            Stage.DEVELOPING -> DevelopingBody(
                status = status,
                modifier = Modifier.padding(horizontal = spec.metrics.pageMargin),
            )

            Stage.FAILED -> FailedBody(
                failureMessage = failureMessage,
                modifier = Modifier.padding(horizontal = spec.metrics.pageMargin),
            )

            // 空态自带 24dp 内容边距与 48dp 纵向留白，外层不要再套横向 padding。
            Stage.EMPTY -> PaperEmptyState(
                title = "还没有拍过",
                body = "回到取景按快门，第一张成片会落在这张纸上。",
            )
        }

        Spacer(Modifier.height(24.dp))

        BottomActions(
            primaryLabel = actions.primaryLabel,
            onPrimary = actions.onPrimary,
            secondaryLabel = actions.secondaryLabel,
            onSecondary = actions.onSecondary,
            modifier = Modifier.padding(horizontal = spec.metrics.pageMargin),
        )

        Spacer(Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------
// 页头副题 / 底部操作
// ---------------------------------------------------------------------------

/** `GOLD 200 · FRAME 05 / 36`；只有真实存在的项才拼进去，全缺就返回 null（页头不画副题）。 */
private fun resultSubtitle(
    photo: SavedPhoto?,
    stockLabel: String?,
    frameIndex: Int,
    rollCapacity: Int,
): String? {
    // 片号只在有真实成片时成立：冲洗中/失败时 frameIndex 是"本卷已存张数"，
    // 不是这张的序号，拼上去会把上一张的号说成这一张的。
    val frame = if (photo != null && frameIndex > 0 && rollCapacity > 0) {
        "FRAME %02d / %d".format(frameIndex, rollCapacity)
    } else {
        null
    }
    return listOfNotNull(stockLabel?.takeIf { it.isNotBlank() }, frame)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}

/** 一屏最多两个动作：主操作（墨底）在右，次操作（描边）在左。 */
private data class ResultAction(
    val primaryLabel: String,
    val onPrimary: () -> Unit,
    val secondaryLabel: String? = null,
    val onSecondary: (() -> Unit)? = null,
)

/**
 * 动作映射**沿用旧页面的回调归属**，只换文案与纸面按钮：
 * - 成片：主「继续拍摄」回相机，次「回相册」去照片墙（旧版「打开相册」）；
 * - 冲洗中：只留「继续拍摄」——旧版两个按钮都回相机，重复且没有第二个含义；
 * - 失败：主「重试」重跑这次冲洗，次「重拍」回取景重新拍；
 * - 空态：主「继续拍摄」回取景。
 */
private fun resultActions(
    stage: Stage,
    onBackToCamera: () -> Unit,
    onOpenPhotoWall: () -> Unit,
    onRetry: () -> Unit,
): ResultAction = when (stage) {
    Stage.SAVED -> ResultAction(
        primaryLabel = "继续拍摄",
        onPrimary = onBackToCamera,
        secondaryLabel = "回相册",
        onSecondary = onOpenPhotoWall,
    )

    Stage.DEVELOPING -> ResultAction(
        primaryLabel = "继续拍摄",
        onPrimary = onBackToCamera,
    )

    Stage.FAILED -> ResultAction(
        primaryLabel = "重试",
        onPrimary = onRetry,
        secondaryLabel = "重拍",
        onSecondary = onBackToCamera,
    )

    Stage.EMPTY -> ResultAction(
        primaryLabel = "继续拍摄",
        onPrimary = onBackToCamera,
    )
}

// ---------------------------------------------------------------------------
// 成片：印相 + colophon
// ---------------------------------------------------------------------------

@Composable
private fun SavedPrint(
    photo: SavedPhoto,
    stockLabel: String?,
    exif: PhotoExif,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    // 画幅比例取真实宽高，不写死 4:3；比例坏掉才退回 4:3。
    val aspect = if (photo.width > 0 && photo.height > 0) {
        photo.width.toFloat() / photo.height.toFloat()
    } else {
        4f / 3f
    }

    // 缩略图解码必须离开主线程：`BitmapFactory.decodeFile` 是一张 1440px 长边的
    // JPEG，主线程解一次就是好几毫秒的掉帧。采样逻辑不变——只用 PhotoStore 已经
    // 压过的缩略图，绝不在这里碰 12MP 原片（会解码出 48MB 的 ARGB）。
    val thumbnail = photo.thumbnailFile
    val image by produceState<PrintImage>(
        initialValue = PrintImage.Loading,
        key1 = thumbnail.absolutePath,
    ) {
        value = withContext(Dispatchers.IO) {
            if (!thumbnail.isFile) {
                PrintImage.Missing
            } else {
                BitmapFactory.decodeFile(thumbnail.absolutePath)
                    ?.asImageBitmap()
                    ?.let(PrintImage::Ready)
                    ?: PrintImage.Missing
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(0.88f)) {
                PrintFrame(
                    aspect = aspect,
                    bitmap = (image as? PrintImage.Ready)?.bitmap,
                    contentDescription = stockLabel?.let { "成片：$it" },
                    emptyLabel = if (image is PrintImage.Missing) "缩略图缺失" else null,
                )
                if (stockLabel != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stockLabel,
                        color = p.textPrimary,
                        fontFamily = spec.type.titleFamily,
                        fontSize = spec.type.optionTitle,
                        lineHeight = spec.type.bodyLineHeight,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Colophon(
            capturedAt = photo.capturedAt,
            exif = exif,
            stockLabel = stockLabel,
        )

        Spacer(Modifier.height(12.dp))

        // 「这张存到哪了」是真实状态（galleryUri 只在刚拍完那次有效），
        // 不显示 renderMillis / strips —— 重建的历史记录里它们都是 0，画出来就是假数。
        Text(
            text = if (photo.galleryUri != null) "已存入系统相册" else "已保存到本机",
            color = p.textTertiary,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.mono,
            lineHeight = spec.type.monoLineHeight,
            letterSpacing = 0.06.em,
        )
    }
}

/**
 * 印相框（规格 §4 `PrintFrame`）：图片外 6dp 纸白边 + 1px 墨线，圆角 2dp，**无阴影**。
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

/** 三行 colophon：DATE / EXPOSURE / FILM，值全是真实数据，缺项写 `—`。 */
@Composable
private fun Colophon(
    capturedAt: Long?,
    exif: PhotoExif,
    stockLabel: String?,
) {
    Column(Modifier.fillMaxWidth()) {
        ColophonRow(
            label = "DATE",
            value = capturedAt?.let(::formatCapturedAt) ?: "—",
        )
        ColophonRow(
            // `1/125 · f/1.6` 已经是真实 EXIF 的两项；ISO 不单列一行 ——
            // 设计稿里的 ISO/快门在这台机器上并不保证被写进文件（D-043）。
            label = "EXPOSURE",
            value = exif.exposureLine() ?: "—",
        )
        ColophonRow(
            label = "FILM",
            value = stockLabel?.takeIf { it.isNotBlank() } ?: "—",
        )
    }
}

/** 一行 colophon：左 14sp 正文标签，右 14sp mono 值，底部 1px hairline（规格 §4）。 */
@Composable
private fun ColophonRow(label: String, value: String) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = p.textSecondary,
                fontFamily = spec.type.bodyFamily,
                fontSize = spec.type.value,
                lineHeight = spec.type.bodyLineHeight,
            )
            Text(
                text = value,
                color = p.textPrimary,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.value,
                lineHeight = spec.type.labelLineHeight,
                letterSpacing = 0.02.em,
                // 等宽数字：时间/快门在换照片时不会左右跳。
                style = TextStyle(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        PaperHairline()
    }
}

/** `10.23 · 22:21`。真实拍摄时间，来自 [SavedPhoto.capturedAt]。 */
private fun formatCapturedAt(epochMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%02d.%02d · %02d:%02d".format(
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}

// ---------------------------------------------------------------------------
// 显影中：打字机状态 + 真实秒数
// ---------------------------------------------------------------------------

/**
 * 显影态（规格 §5.2.4 / §6「显影」）：
 *
 * - 先摆一个**同尺寸的未显影片框**（88% 宽、纸色空窗），位置不跳；
 * - 状态只有两段真实阶段：`显影中`（引擎渲染）→ `写入中`（JPEG/相册）；
 * - `已等待 03s` 是真实墙钟，不是假进度：引擎不给百分比就不编百分比；
 * - 阶段切换 200ms 淡入，文字用打字机逐字出现；**无扫描光带、无循环脉冲**。
 */
@Composable
private fun DevelopingBody(
    status: ResultStatus,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val writing = status == ResultStatus.OUTPUT_WRITING

    // 真实墙钟：起点是第一次组合的时刻，之后按 System.currentTimeMillis 求差，
    // 而不是"每秒 +1"的计数器 —— 页面被系统停住再回来时数字不会落后。
    val startedAt = remember { System.currentTimeMillis() }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000L).toInt()
            delay(250L)
        }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PrintFrame(
                aspect = 4f / 3f,
                // 未显影 = 纸色空窗，不画暗色灯箱、不画假画面。
                modifier = Modifier.fillMaxWidth(0.88f),
            )
        }

        Spacer(Modifier.height(24.dp))

        Crossfade(
            targetState = writing,
            animationSpec = tween(spec.motion.sheetEnterMs),
            label = "developing-stage",
        ) { isWriting ->
            TypewriterLine(
                text = if (isWriting) "写入中" else "显影中",
                style = TextStyle(
                    fontFamily = spec.type.monoFamily,
                    fontSize = spec.type.optionTitle,
                    lineHeight = spec.type.bodyLineHeight,
                    letterSpacing = 0.10.em,
                    color = p.textPrimary,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "已等待 %02ds".format(elapsedSeconds),
            color = p.textTertiary,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.mono,
            lineHeight = spec.type.monoLineHeight,
            letterSpacing = 0.06.em,
        )
    }
}

/**
 * 打字机文字：逐字揭示，未打完时带一个 `|` 光标。
 *
 * 读屏拿的是**完整文案**（`contentDescription`），不会听到打了一半的状态。
 */
@Composable
private fun TypewriterLine(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var visibleChars by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        visibleChars = 0
        while (visibleChars < text.length) {
            delay(TypewriterCharMs)
            visibleChars += 1
        }
    }
    val shown = buildString {
        append(text.take(visibleChars))
        if (visibleChars < text.length) append('|')
    }
    Text(
        text = shown,
        style = style,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier.semantics { contentDescription = text },
    )
}

// ---------------------------------------------------------------------------
// 失败 / 空态 / 底部操作
// ---------------------------------------------------------------------------

/** 失败态：纸面说明 + 真实失败原因；「重试」在底部操作里（不另做描边卡片）。 */
@Composable
private fun FailedBody(
    failureMessage: String?,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 8.dp),
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

/** 主操作在右（墨底纸白字），次操作在左（1px 墨线透明底）；两者都是 48dp 高。 */
@Composable
private fun BottomActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val spec = LocalFilmTheme.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spec.metrics.gutter),
    ) {
        if (secondaryLabel != null && onSecondary != null) {
            PaperButton(
                text = secondaryLabel,
                onClick = onSecondary,
                modifier = Modifier.weight(1f),
                primary = false,
                secondary = true,
            )
        }
        PaperButton(
            text = primaryLabel,
            onClick = onPrimary,
            modifier = Modifier.weight(1f),
        )
    }
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
