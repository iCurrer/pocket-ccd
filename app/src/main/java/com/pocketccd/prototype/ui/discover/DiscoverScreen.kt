package com.pocketccd.prototype.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.glass.GlassDivider
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.glass.GlassSurface
import com.pocketccd.prototype.ui.theme.GlassShape
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
) {
    val p = MaterialTheme.glass

    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = GlassSpace.page, end = GlassSpace.page, top = 10.dp, bottom = 6.dp),
        ) {
            Text(text = "发现", style = GlassType.title, color = p.onGlass)
            Text(text = "本机档案概览", style = GlassType.caption, color = p.onGlassMuted)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassSpace.page),
            verticalArrangement = Arrangement.spacedBy(GlassSpace.gap),
        ) {
            GlassSurface(
                shape = GlassShape.panel,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
            ) {
                Column {
                    Text(text = "拍过的", style = GlassType.caption, color = p.onGlassMuted)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$photoCount",
                            style = GlassType.numericLarge,
                            color = p.onGlass,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = "张成片", style = GlassType.caption, color = p.onGlassMuted)
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
            }

            GlassSurface(
                shape = GlassShape.card,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIcon(GlassIconKind.Info, p.onGlassMuted, size = 16.dp)
                        Spacer(Modifier.width(GlassSpace.tight))
                        Text(text = "为什么这里没有内容流", style = GlassType.cardTitle, color = p.onGlass)
                    }
                    Spacer(Modifier.height(GlassSpace.tight))
                    Text(
                        text = "这个应用没有申请网络权限，安装包里也没有任何在线内容源。" +
                            "所以这一页只显示从本机文件算出来的数字：张数、收藏数、" +
                            "已安装的胶卷数、照片占用的空间。",
                        style = GlassType.caption,
                        color = p.onGlassMuted,
                    )
                }
            }

            Spacer(Modifier.height(GlassSpace.dockReserve))
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
