package com.pocketccd.prototype.ui.gallery

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType

/**
 * App-owned photo wall. It must only receive records created by this app; it
 * never queries the system gallery. Runtime currently has no captures, so the
 * caller may pass an empty list. The preview shows the intended grid with
 * synthetic contact-sheet colors.
 */
@Composable
fun PhotoWallScreen(
    onBack: () -> Unit,
    photos: List<PhotoWallItem> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketCcdColors.Background),
    ) {
        PhotoWallHeader(
            count = photos.size,
            onBack = onBack,
        )
        if (photos.isEmpty()) {
            EmptyPhotoWall(modifier = Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = PocketCcdSpacing.lg,
                    end = PocketCcdSpacing.lg,
                    top = PocketCcdSpacing.sm,
                    bottom = PocketCcdSpacing.xl,
                ),
                horizontalArrangement = Arrangement.spacedBy(PocketCcdSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(PocketCcdSpacing.xs),
            ) {
                items(items = photos, key = { it.id }) { photo ->
                    PhotoTile(photo = photo)
                }
            }
        }
    }
}

@Composable
private fun PhotoWallHeader(
    count: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = PocketCcdSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.photo_wall_back),
                tint = PocketCcdColors.TextSecondary,
                modifier = Modifier.size(PocketCcdSizes.icon),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.photo_wall_title),
                style = PocketCcdType.MonoLabel,
                color = PocketCcdColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.photo_wall_owned_only),
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextTertiary,
            )
        }
        Text(
            text = count.toString().padStart(2, '0'),
            style = PocketCcdType.MonoLabel,
            color = PocketCcdColors.TextSecondary,
        )
    }
}

@Composable
private fun EmptyPhotoWall(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PocketCcdSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 58.dp)
                .clip(RoundedCornerShape(PocketCcdRadius.sm))
                .border(
                    width = PocketCcdSizes.hairline,
                    color = PocketCcdColors.Outline,
                    shape = RoundedCornerShape(PocketCcdRadius.sm),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PocketCcdSizes.hairline)
                    .align(Alignment.Center)
                    .background(PocketCcdColors.OutlineSoft),
            )
        }
        Spacer(modifier = Modifier.height(PocketCcdSpacing.lg))
        Text(
            text = stringResource(R.string.photo_wall_empty_title),
            style = PocketCcdType.MonoLabel,
            color = PocketCcdColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(PocketCcdSpacing.xs))
        Text(
            text = stringResource(R.string.photo_wall_empty_body),
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhotoTile(photo: PhotoWallItem) {
    val shape = RoundedCornerShape(PocketCcdRadius.sm)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(Brush.linearGradient(photo.colors))
            .border(
                width = PocketCcdSizes.hairline,
                color = PocketCcdColors.CameraPanelHighlight,
                shape = shape,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(PocketCcdSpacing.sm),
        ) {
            Text(
                text = photo.filter,
                style = PocketCcdType.MonoMicro,
                color = Color.White.copy(alpha = 0.9f),
            )
            Text(
                text = photo.dateLabel,
                style = PocketCcdType.MonoMicro,
                color = Color.White.copy(alpha = 0.65f),
            )
        }
    }
}

data class PhotoWallItem(
    val id: String,
    val filter: String,
    val dateLabel: String,
    val colors: List<Color>,
)

private val PreviewPhotos = listOf(
    PhotoWallItem(
        id = "preview-01",
        filter = "DAYLIGHT",
        dateLabel = "2026.10.17",
        colors = listOf(Color(0xFFC9B18C), Color(0xFF6E7F6A)),
    ),
    PhotoWallItem(
        id = "preview-02",
        filter = "DAYLIGHT",
        dateLabel = "2026.10.17",
        colors = listOf(Color(0xFFB7A49A), Color(0xFF4D5B59)),
    ),
    PhotoWallItem(
        id = "preview-03",
        filter = "NIGHT",
        dateLabel = "2026.10.16",
        colors = listOf(Color(0xFF8D8B86), Color(0xFF343D3D)),
    ),
    PhotoWallItem(
        id = "preview-04",
        filter = "DAYLIGHT",
        dateLabel = "2026.10.16",
        colors = listOf(Color(0xFFD1A37A), Color(0xFF5B4A3F)),
    ),
    PhotoWallItem(
        id = "preview-05",
        filter = "FLASH",
        dateLabel = "2026.10.15",
        colors = listOf(Color(0xFFC8A78E), Color(0xFF5E4A47)),
    ),
    PhotoWallItem(
        id = "preview-06",
        filter = "DAYLIGHT",
        dateLabel = "2026.10.14",
        colors = listOf(Color(0xFF9FAE9A), Color(0xFF43544B)),
    ),
)

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFF4EFE6)
@Composable
private fun PhotoWallPreview() {
    PocketCcdTheme {
        PhotoWallScreen(
            onBack = {},
            photos = PreviewPhotos,
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFF4EFE6)
@Composable
private fun EmptyPhotoWallPreview() {
    PocketCcdTheme {
        PhotoWallScreen(
            onBack = {},
            photos = emptyList(),
        )
    }
}
