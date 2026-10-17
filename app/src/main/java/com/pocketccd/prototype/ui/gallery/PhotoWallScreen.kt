package com.pocketccd.prototype.ui.gallery

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.components.ToyCameraBody
import com.pocketccd.prototype.ui.components.ToyRoundButton
import com.pocketccd.prototype.ui.components.ToyScreen
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType

/**
 * App-owned film roll. It receives only this app's captures; it never scans
 * the system gallery. Runtime has no captures yet, so the caller may pass an
 * empty list. Preview uses synthetic contact-sheet frames.
 */
@Composable
fun PhotoWallScreen(
    onBack: () -> Unit,
    photos: List<PhotoWallItem> = emptyList(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketCcdColors.Background)
            .padding(PocketCcdSizes.cameraPanelMargin),
        contentAlignment = Alignment.Center,
    ) {
        ToyCameraBody(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PocketCcdSpacing.md),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToyRoundButton(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.photo_wall_back),
                        size = 38.dp,
                        color = PocketCcdColors.Mint,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = PocketCcdColors.TextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(PocketCcdSpacing.sm))
                    Text(
                        text = stringResource(R.string.photo_wall_title),
                        style = PocketCcdType.Brand,
                        color = PocketCcdColors.TextPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = photos.size.toString().padStart(2, '0'),
                        style = PocketCcdType.MonoLabel,
                        color = PocketCcdColors.TextSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                ToyScreen(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (photos.isEmpty()) {
                        EmptyFilmRoll(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(PocketCcdSpacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(PocketCcdSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(PocketCcdSpacing.sm),
                        ) {
                            items(items = photos, key = { it.id }) { photo ->
                                PhotoPrintTile(photo = photo)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFilmRoll(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(PocketCcdSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FilmLeader()
        Spacer(modifier = Modifier.height(PocketCcdSpacing.lg))
        Text(
            text = stringResource(R.string.photo_wall_empty_title),
            style = PocketCcdType.MonoLabel,
            color = PocketCcdColors.ViewfinderInk,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(PocketCcdSpacing.xs))
        Text(
            text = stringResource(R.string.photo_wall_empty_body),
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.ViewfinderInk.copy(alpha = 0.62f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FilmLeader() {
    Canvas(modifier = Modifier.size(width = 108.dp, height = 56.dp)) {
        val paper = Color(0xFFF3E5CB)
        drawRect(
            color = paper,
            topLeft = Offset(0f, size.height * 0.18f),
            size = Size(size.width, size.height * 0.64f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
        val hole = 4.dp.toPx()
        listOf(0.1f, 0.28f, 0.46f, 0.64f, 0.82f, 0.98f).forEach { fraction ->
            drawRect(
                color = paper,
                topLeft = Offset(size.width * fraction - hole / 2, size.height * 0.06f),
                size = Size(hole, hole),
            )
            drawRect(
                color = paper,
                topLeft = Offset(size.width * fraction - hole / 2, size.height * 0.88f),
                size = Size(hole, hole),
            )
        }
    }
}

@Composable
private fun PhotoPrintTile(photo: PhotoWallItem) {
    val shape = RoundedCornerShape(PocketCcdRadius.xs)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFFFFFBF2))
            .border(
                width = PocketCcdSizes.hairline,
                color = PocketCcdColors.Outline,
                shape = shape,
            )
            .padding(PocketCcdSpacing.xs),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(PocketCcdRadius.xs))
                    .background(Brush.linearGradient(photo.colors)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                            ),
                        ),
                )
            }
            Spacer(modifier = Modifier.height(PocketCcdSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = photo.filter,
                    style = PocketCcdType.MonoMicro,
                    color = PocketCcdColors.TextSecondary,
                )
                Text(
                    text = photo.dateLabel,
                    style = PocketCcdType.MonoMicro,
                    color = PocketCcdColors.TextTertiary,
                )
            }
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
        filter = "DAY",
        dateLabel = "10.17",
        colors = listOf(Color(0xFFC9B18C), Color(0xFF6E7F6A)),
    ),
    PhotoWallItem(
        id = "preview-02",
        filter = "DAY",
        dateLabel = "10.17",
        colors = listOf(Color(0xFFB7A49A), Color(0xFF4D5B59)),
    ),
    PhotoWallItem(
        id = "preview-03",
        filter = "NIGHT",
        dateLabel = "10.16",
        colors = listOf(Color(0xFF8D8B86), Color(0xFF343D3D)),
    ),
    PhotoWallItem(
        id = "preview-04",
        filter = "DAY",
        dateLabel = "10.16",
        colors = listOf(Color(0xFFD1A37A), Color(0xFF5B4A3F)),
    ),
    PhotoWallItem(
        id = "preview-05",
        filter = "FLASH",
        dateLabel = "10.15",
        colors = listOf(Color(0xFFC8A78E), Color(0xFF5E4A47)),
    ),
    PhotoWallItem(
        id = "preview-06",
        filter = "DAY",
        dateLabel = "10.14",
        colors = listOf(Color(0xFF9FAE9A), Color(0xFF43544B)),
    ),
)

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFE6F0EA)
@Composable
private fun PhotoWallPreview() {
    PocketCcdTheme {
        PhotoWallScreen(onBack = {}, photos = PreviewPhotos)
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFE6F0EA)
@Composable
private fun EmptyPhotoWallPreview() {
    PocketCcdTheme {
        PhotoWallScreen(onBack = {}, photos = emptyList())
    }
}
