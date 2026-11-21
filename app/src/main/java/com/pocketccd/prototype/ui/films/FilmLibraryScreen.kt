package com.pocketccd.prototype.ui.films

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.artwork.filmArtworkRes
import com.pocketccd.prototype.ui.glass.GlassChip
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconButton
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.glass.GlassSurface
import com.pocketccd.prototype.ui.theme.ConsoleColor
import com.pocketccd.prototype.ui.theme.ConsoleShape
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.consoleRecess
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
    /** 该卷最新一张成片的缩略图。**本页不用** —— 它只喂给拍摄页的相册钮（AppRoot → CameraScreen.lastThumbnail）。 */
    val latestThumbnail: String? = null,
)

/**
 * 胶卷库：装卷。
 *
 * 版式是**两列卡墙**，每支卷一张卡：大卡面 + 凹槽里的包装图。选中态靠三样东西
 * 同时表达（复审：旧版只有一圈细边 + 一颗 16dp 对勾，扫一眼分不出装着哪支）：
 * 1. 黄铜描边；
 * 2. 右上角压住包装图的黄铜对勾钉；
 * 3. 卡底的「使用中」黄铜短条。
 *
 * 拍不了的卷灰掉并写明原因（旧版能装进去，然后相机绑定时静默换卷，复审 P3-12）。
 * 画格里**只显示包装图** —— 旧版会换成这一卷最新成片的缩略图，已按需求取消：
 * 包装承诺的是"这是哪一支卷"，与画面无关，所以不违反 D-002。
 */
@Composable
fun FilmLibraryScreen(
    cards: List<FilmCard>,
    selectedId: String?,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val p = MaterialTheme.glass
    val usable = cards.count { it.supported }

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
                    text = "${cards.size} 支已安装 · $usable 支可用",
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = GlassSpace.page,
                end = GlassSpace.page,
                bottom = GlassSpace.dockReserve,
            ),
            horizontalArrangement = Arrangement.spacedBy(GlassSpace.gap),
            verticalArrangement = Arrangement.spacedBy(GlassSpace.gap),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = cards, key = { it.id }) { card ->
                StockCard(
                    card = card,
                    active = card.id == selectedId,
                    onClick = { onSelect(card.id) },
                )
            }
        }
    }
}

@Composable
private fun StockCard(card: FilmCard, active: Boolean, onClick: () -> Unit) {
    val p = MaterialTheme.glass
    val meta = when {
        !card.supported -> card.reason ?: "这台设备拍不了"
        card.shots == 0 -> "${card.frameLabel} · 还没拍过"
        else -> "${card.frameLabel} · 已拍 ${card.shots} 张"
    }
    val description = buildString {
        append(card.label).append('，').append(meta)
        if (active) append("，使用中")
    }

    GlassSurface(
        shape = GlassShape.card,
        contentPadding = PaddingValues(10.dp),
        interactive = card.supported && !active,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        onClick = onClick.takeIf { card.supported && !active },
        borderColor = if (active) p.accent else null,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column {
                StockArtwork(
                    card = card,
                    dimmed = !card.supported,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
                Spacer(Modifier.height(GlassSpace.tight))
                Text(
                    text = card.label,
                    style = GlassType.cardTitle,
                    color = if (card.supported) p.onGlass else p.onGlassMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    style = GlassType.caption,
                    color = if (card.supported) p.onGlassMuted else p.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) {
                    Spacer(Modifier.height(GlassSpace.tight))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(width = 3.dp, height = 12.dp)
                                .background(p.accent, RoundedCornerShape(1.5.dp)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "使用中",
                            style = GlassType.badge,
                            color = p.accentDeep,
                        )
                    }
                }
            }
            if (active) {
                LoadedBadge(Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

/**
 * 黄铜对勾钉：压在包装图右上角，和选中描边、卡底「使用中」短条一起构成选中态。
 * 外圈一圈 panelLight 是为了把它从深色的包装图角上"抬"出来 —— 黄铜贴深色包装
 * 会糊成一片，描一圈机身色它才是一颗钉。
 */
@Composable
private fun LoadedBadge(modifier: Modifier = Modifier) {
    val p = MaterialTheme.glass
    Box(
        modifier = modifier
            .size(22.dp)
            .background(p.accent, CircleShape)
            .border(1.5.dp, ConsoleColor.panelLight, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        GlassIcon(GlassIconKind.Check, p.accentDeep, size = 13.dp)
    }
}

/**
 * 包装图画格：2:3 竖向，装进凹槽（凹陷靠四壁，图要内缩 4dp，见 D-095）。
 *
 * 找不到包装图（用户自己 push 进 `files/film/` 的 pack）才回落到胶卷筒轮廓 +
 * 首字母 —— 不能是空白，也绝不画渐变冒充"这卷拍出来长这样"（D-002）。
 */
@Composable
private fun StockArtwork(card: FilmCard, dimmed: Boolean, modifier: Modifier = Modifier) {
    val p = MaterialTheme.glass
    val artworkRes = filmArtworkRes(card.id)
    Box(
        modifier = modifier.consoleRecess(
            shape = ConsoleShape.artwork,
            color = ConsoleColor.panel,
            depth = 4.dp,
            shade = 0.26f,
            rimLight = 0.6f,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val dim = if (dimmed) Modifier.alpha(0.45f) else Modifier
        if (artworkRes != null) {
            Image(
                painter = painterResource(artworkRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .then(dim),
            )
        } else {
            val tint = if (dimmed) p.onGlassMuted.copy(alpha = 0.55f) else p.onGlassMuted
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = dim,
            ) {
                CanisterGlyph(tint)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stockMonogram(card.label),
                    style = GlassType.badge,
                    color = tint,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun stockMonogram(label: String): String =
    label.split(' ')
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")

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
