package com.pocketccd.prototype.ui.discover

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.gallery.PhotoWallItem
import com.pocketccd.prototype.ui.gallery.rememberThumbnail
import com.pocketccd.prototype.ui.glass.GlassDivider
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.glass

/**
 * 发现页：**只放能算出来的事实**。
 *
 * 旧版这里叫"内容社区"，但因为应用没有网络权限、也没有内容源，实际只能显示
 * 照片数 / 胶卷数 / 进度三个数字（README 自己写的是"脚手架"）。换皮时干脆把它
 * 定位成"本机档案概览"：三项统计都从真实文件算，加一段说明为什么这里永远不会有
 * 在线内容 —— 一个诚实的空页好过一个假装有内容的页（D-002）。
 */
@Composable
fun DiscoverScreen(
    photoCount: Int,
    favoriteCount: Int,
    stockCount: Int,
    storageBytes: Long,
    modifier: Modifier = Modifier,
    recentPhotos: List<PhotoWallItem> = emptyList(),
) {
    val p = MaterialTheme.glass

    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = GlassSpace.page, end = GlassSpace.page, top = 10.dp, bottom = 6.dp),
        ) {
            Text(text = "本机档案", style = GlassType.title, color = p.onGlass)
            Text(text = "本机累计 · 不上传", style = GlassType.caption, color = p.onGlassMuted)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassSpace.page),
            verticalArrangement = Arrangement.spacedBy(GlassSpace.gap),
        ) {
            // 读数直接排在页面上，不再套一层玻璃卡片：这页只有一组数，
            // 卡片不会增加层级，只会把四个数字关进另一块圆角矩形。
            Column(Modifier.fillMaxWidth().padding(top = GlassSpace.micro)) {
                Text(text = "现存成片", style = GlassType.badge, color = p.onGlassMuted)
                Spacer(Modifier.height(GlassSpace.micro))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$photoCount",
                        style = GlassType.numericHero,
                        color = p.onGlass,
                    )
                    Spacer(Modifier.width(GlassSpace.tight))
                    Text(text = "张", style = GlassType.label, color = p.accentBright)
                }
                Spacer(Modifier.height(GlassSpace.inner))
                GlassDivider()
                Spacer(Modifier.height(GlassSpace.inner))
                Row(Modifier.fillMaxWidth()) {
                    Stat("收藏", "$favoriteCount")
                    Stat("胶卷", "$stockCount 支")
                    Stat("占用", humanBytes(storageBytes))
                }
            }

            if (recentPhotos.isNotEmpty()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "最近成片",
                        style = GlassType.badge,
                        color = p.onGlassMuted,
                    )
                    Spacer(Modifier.height(GlassSpace.tight))
                    // 四张一排的固定画幅。只用 weight 的话，只有一张照片时它会拉满
                    // 整个屏幕宽 —— 一张缩略图变成一块广告牌（旧截图就是这样）。
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val tile = (maxWidth - 12.dp) / 4f
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            recentPhotos.take(4).forEach { item ->
                                RecentFrame(item, Modifier.width(tile))
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth()) {
                GlassDivider()
                Spacer(Modifier.height(GlassSpace.inner))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(GlassIconKind.Info, p.accent, size = 16.dp)
                    Spacer(Modifier.width(GlassSpace.tight))
                    Text(
                        text = "只写在本机",
                        style = GlassType.cardTitle,
                        color = p.onGlass,
                    )
                }
                Spacer(Modifier.height(GlassSpace.tight))
                Text(
                    text = "照片存两份：应用私有目录一份，系统相册一份。应用没有网络权限，" +
                        "不会上传，也没有在线内容。",
                    style = GlassType.caption,
                    color = p.onGlassMuted,
                )
            }

            Spacer(Modifier.height(GlassSpace.dockReserve))
        }
    }
}

@Composable
private fun RecentFrame(item: PhotoWallItem, modifier: Modifier = Modifier) {
    val p = MaterialTheme.glass
    val bitmap = rememberThumbnail(item.imagePath)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(p.surface),
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
            GlassIcon(GlassIconKind.Gallery, p.onGlassMuted, size = 16.dp)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(label: String, value: String) {
    val p = MaterialTheme.glass
    Column(Modifier.weight(1f)) {
        Text(text = label, style = GlassType.badge, color = p.onGlassMuted)
        Spacer(Modifier.height(3.dp))
        Text(text = value, style = GlassType.numericLarge, color = p.onGlass)
    }
}

private fun humanBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%d KB".format(bytes / 1024)
    else -> "$bytes B"
}
