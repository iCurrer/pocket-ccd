package com.pocketccd.prototype.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.pocketccd.prototype.camera.ExposureProfile
import com.pocketccd.prototype.navigation.AppDestination
import com.pocketccd.prototype.navigation.showBottomTabs
import com.pocketccd.prototype.camera.LensStop
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.ui.camera.CameraScreen
import com.pocketccd.prototype.ui.detail.DetailStatus
import com.pocketccd.prototype.ui.discover.DiscoverScreen
import com.pocketccd.prototype.ui.gallery.PhotoViewer
import com.pocketccd.prototype.ui.detail.PhotoDetailScreen
import com.pocketccd.prototype.ui.films.FilmCard
import com.pocketccd.prototype.ui.films.FilmLibraryScreen
import com.pocketccd.prototype.ui.gallery.GalleryScreen
import com.pocketccd.prototype.ui.gallery.PhotoWallItem
import com.pocketccd.prototype.ui.glass.GlassBackdrop
import com.pocketccd.prototype.ui.glass.GlassNavBar
import com.pocketccd.prototype.ui.glass.LocalGlassBackdrop
import com.pocketccd.prototype.ui.glass.GlassTab
import com.pocketccd.prototype.ui.settings.LicensesScreen
import com.pocketccd.prototype.ui.settings.SettingsScreen
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 布局截图回归。
 *
 * 存在的理由：这一轮改动（图标、色板、拍摄页布局）全是"看"才能判断的，而 CI / 开发机
 * 上不一定有真机。用 Robolectric 的原生 Skia 把关键页面渲染成 PNG，至少能盯住
 * —— 图标有没有画出来（D-073 曾经整批画到视口外，编译期完全看不出来）
 * —— 顶栏在 360dp 上会不会把按钮挤出屏幕
 * —— 快门是否真的居中
 *
 * 产物：`app/build/screenshots/` 下的 PNG（`./gradlew :app:testDebugUnitTest`）。
 * **不是像素级快照测试**：没有断言基准图（玻璃的 RenderEffect 在模拟环境里不可靠），
 * 只产出图片供人看。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cameraScreen() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(LensStop(1f, 24), LensStop(3f, 70)),
                    // 贴花开着时 dock 上方那行状态字（D-081）。
                    imprintLabel = "135 半格 · 日期",
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-411dp")
    }

    /**
     * 横版拍摄页（D-083）：拍摄页被强制横屏，这是 FOA-AL00 的真实横屏尺寸
     * （1084×2412 @480dpi → 804 × 361dp）。重点看：取景窗是不是 4:3、dock 有没有被挤出去。
     */
    @Test
    @Config(qualifiers = "w804dp-h361dp-land-xxhdpi")
    fun cameraScreenLandscape() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(LensStop(1f, 24), LensStop(3f, 70)),
                    imprintLabel = "135 半格 · 日期",
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-landscape")
    }

    /** EV 面板展开：这是"面板压住变焦轨"那个 bug 的现场，必须能看到。 */
    @Test
    fun cameraEvPanel() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 2,
                    lensStops = listOf(LensStop(1f, 24), LensStop(3f, 70)),
                    evInitiallyOpen = true,
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-ev-panel")
    }

    /**
     * 上滑抽屉展开：相机页唯一的"去别的页面"入口（D-079）。
     * 手势本身没法在 JVM 里验，但**面板长什么样、会不会盖住 dock** 必须能看见。
     */
    @Test
    fun cameraSwitcher() {        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(LensStop(1f, 24), LensStop(3f, 70)),
                    switcherInitiallyOpen = true,
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-switcher")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun cameraScreenNarrow() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "CINEStill 800T",
                    shotsWithStock = 120,
                    flashOn = true,
                    flashAvailable = true,
                    gridOn = true,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 2,
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-narrow")
    }

    @Test
    fun galleryScreen() {
        val photos = listOf(
            PhotoWallItem("1", "GOLD 200", 1_700_000_000_000),
            PhotoWallItem("2", "PORTRA 400", 1_700_000_100_000),
            PhotoWallItem("3", "TRI-X 400", 1_700_000_200_000),
        )
        compose.setContent {
            PocketCcdTheme {
                // 真实页面永远生在 GlassBackdrop 里（底色 + 极光 + 内容图层）。
                // 单独渲染会让白字落在 Robolectric 的白色窗口底上 —— 整张图全白。
                GlassBackdrop {
                    GalleryScreen(
                        photos = photos,
                        favoriteIds = setOf("2"),
                        onToggleFavorite = {},
                        onOpenDetail = {},
                        onOpenCamera = {},
                        onRefresh = {},
                    )
                }
            }
        }
        capture("gallery")
    }

    @Test
    fun detailScreen() {
        val photo = fakePhoto()
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop(captureContent = false) {
                    PhotoDetailScreen(
                        status = DetailStatus.Saved,
                        photo = photo,
                        stockLabel = photo.stockLabel,
                        isFavorite = true,
                        galleryPublished = true,
                        onBack = {},
                        onRetry = {},
                        onToggleFavorite = {},
                        onShare = {},
                        onOpenInGallery = {},
                    )
                }
            }
        }
        capture("detail")
    }

    @Test
    fun filmsScreen() {
        val cards = listOf(
            FilmCard("gold200__4000x3000", "GOLD 200", "4000×3000", true, null, 12, null),
            FilmCard("portra400__4000x3000", "PORTRA 400", "4000×3000", true, null, 3, null),
            FilmCard("velvia50__4096x3072", "VELVIA 50", "4096×3072", false, "这台设备只能给 4096×3072", 0, null),
        )
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
                    FilmLibraryScreen(
                        cards = cards,
                        selectedId = "gold200__4000x3000",
                        onSelect = {},
                        onRefresh = {},
                        onOpenCamera = {},
                    )
                }
            }
        }
        capture("films")
    }

    /** 造一张真实的缩略图/全尺寸文件，让详情页的"玻璃压在照片上"是真的。 */
    private fun fakePhoto(): SavedPhoto {
        val directory = File("build/screenshots").apply { mkdirs() }
        val file = File(directory, "fake-photo.jpg")
        if (!file.exists()) {
            val image = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(image)
            canvas.drawColor(android.graphics.Color.rgb(48, 78, 120))
            canvas.drawCircle(300f, 280f, 160f, android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(240, 206, 140)
            })
            FileOutputStream(file).use { out -> image.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            image.recycle()
        }
        return SavedPhoto(
            id = "film_1700000000000_gold200__4000x3000",
            stockId = "gold200__4000x3000",
            stockLabel = "GOLD 200",
            fullFile = file,
            thumbnailFile = file,
            width = 3000,
            height = 4000,
            capturedAt = 1_700_000_000_000,
            renderMillis = 2_400,
            strips = 24,
        )
    }

    /** 带 TabBar 的完整纸面层：TabBar 是唯一真正采样"内容图层"的控件。 */
    @Test
    fun galleryWithTabBar() {
        val photos = listOf(
            PhotoWallItem("1", "GOLD 200", 1_700_000_000_000),
            PhotoWallItem("2", "PORTRA 400", 1_700_000_100_000),
            PhotoWallItem("3", "TRI-X 400", 1_700_000_200_000),
        )
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop(
                    overlay = {
                        GlassNavBar(
                            active = GlassTab.Gallery,
                            onSelect = {},
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 10.dp),
                        )
                    },
                ) {
                    GalleryScreen(
                        photos = photos,
                        favoriteIds = setOf("2"),
                        onToggleFavorite = {},
                        onOpenDetail = {},
                        onOpenCamera = {},
                        onRefresh = {},
                    )
                }
            }
        }
        capture("gallery-with-tabbar")
    }

    @Test
    fun settingsScreen() {
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
                    SettingsScreen(
                        stockCount = 8,
                        photoCount = 42,
                        versionName = "0.8.0",
                        // debug 包里才有的诊断入口：截图里也要能看到它。
                        onRunSelfTest = {},
                    )
                }
            }
        }
        capture("settings")
    }

    @Test
    fun discoverScreen() {
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
                    DiscoverScreen(
                        photoCount = 42,
                        favoriteCount = 7,
                        stockCount = 8,
                        storageBytes = 1_240_000_000L,
                    )
                }
            }
        }
        capture("discover")
    }

    /**
     * 相册 + 全屏查看器：这是"点开大图"那一刻的真实层级。
     *
     * 用的是生产代码里的同一个判断 `showBottomTabs(...)`（不是测试里另写一份），
     * 所以这张图能证明**TabBar 确实让位了**（D-077）。
     */
    @Test
    fun galleryViewer() {
        val file = fakePhoto()
        val item = PhotoWallItem(
            id = file.id,
            stockLabel = file.stockLabel,
            capturedAt = file.capturedAt,
            imagePath = file.thumbnailFile.absolutePath,
            fullImagePath = file.fullFile.absolutePath,
        )
        val viewerOpen = true
        val tabsVisible = showBottomTabs(AppDestination.GALLERY, viewerOpen)
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop(
                    captureContent = false,
                    overlay = {
                        if (tabsVisible) {
                            GlassNavBar(
                                active = GlassTab.Gallery,
                                onSelect = {},
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 10.dp),
                            )
                        }
                        CompositionLocalProvider(LocalGlassBackdrop provides null) {
                            PhotoViewer(
                                photos = listOf(item),
                                initialIndex = 0,
                                favoriteIds = emptySet(),
                            )
                        }
                    },
                ) {
                    GalleryScreen(
                        photos = listOf(item),
                        favoriteIds = emptySet(),
                        onToggleFavorite = {},
                        onOpenDetail = {},
                        onOpenCamera = {},
                        onRefresh = {},
                    )
                }
            }
        }
        capture("gallery-viewer")
    }

    @Test
    fun cameraBlocked() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    shotsWithStock = 0,
                    flashOn = false,
                    flashAvailable = false,
                    gridOn = false,
                    canShoot = false,
                    blockReason = "「VELVIA 50」需要 4096×3072，这台设备只能给 4000×3000",
                    permissionGranted = true,
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-blocked")
    }

    /** 权限被拒：这一屏是用户第一次打开应用最可能看到的，不能是空白或错位的。 */
    @Test
    fun cameraNoPermission() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    shotsWithStock = 0,
                    flashOn = false,
                    flashAvailable = false,
                    gridOn = false,
                    canShoot = false,
                    blockReason = "需要相机权限才能取景",
                    permissionGranted = false,
                    viewfinder = null,
                )
            }
        }
        capture("camera-no-permission")
    }

    /** 空相册：这是"第一次装完还没拍"时的真实首屏。 */
    @Test
    fun galleryEmpty() {
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
                    GalleryScreen(
                        photos = emptyList(),
                        favoriteIds = emptySet(),
                        onToggleFavorite = {},
                        onOpenDetail = {},
                        onOpenCamera = {},
                        onRefresh = {},
                    )
                }
            }
        }
        capture("gallery-empty")
    }

    /** 声明页：一长串正文，最容易出"文字压到卡片外/行距塌掉"的问题。 */
    @Test
    fun licensesScreen() {
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
                    LicensesScreen(onBack = {})
                }
            }
        }
        capture("licenses")
    }

    /** 320dp：小屏机的下限（旧 5" 机、分屏后的半屏）。顶栏最容易在这里崩。 */
    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun cameraSmall320() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "CINEStill 800T",
                    shotsWithStock = 120,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-320dp")
    }

    /** 480dp：大屏直板机。看的是"内容会不会散开、留白是否合理"。 */
    @Test
    @Config(qualifiers = "w480dp-h1000dp-xxhdpi")
    fun cameraWide480() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = true,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(LensStop(1f, 24), LensStop(3f, 70)),
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-480dp")
    }

    /** 平板/折叠展开：界面是竖屏布局，看它在宽屏上会不会把控件拉得不像话。 */
    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun cameraTablet800() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-800dp")
    }

    /** 取景器占位：没有相机时用一块渐变代替画面，只为看清浮层。 */
    @Composable
    private fun FakeViewfinder(modifier: Modifier) {
        Box(
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF3C5170), Color(0xFF14171C)),
                    ),
                ),
        )
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
