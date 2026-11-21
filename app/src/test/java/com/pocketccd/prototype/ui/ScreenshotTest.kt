package com.pocketccd.prototype.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.pocketccd.prototype.ui.auth.ActivationPage
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
import com.pocketccd.prototype.ui.glass.GlassTab
import com.pocketccd.prototype.ui.settings.LicensesScreen
import com.pocketccd.prototype.ui.settings.SettingsScreen
import com.pocketccd.prototype.ui.settings.SettingsVersionBlock
import com.pocketccd.prototype.ui.settings.UpdateUi
import com.pocketccd.prototype.ui.theme.ConsoleColor
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.consolePanel
import com.pocketccd.prototype.ui.theme.consolePress
import com.pocketccd.prototype.update.ApkAsset
import com.pocketccd.prototype.update.InstallState
import com.pocketccd.prototype.update.ReleaseInfo
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
                    // 包装图按 pack id 查：截图里要能看到真图而不是占位暗盒。
                    stockId = "gold200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(
                        LensStop(1f, 24, optical = true),
                        LensStop(3f, 70, optical = true),
                    ),
                    // 主长焦档（D-088）：该档右上角会标 T。
                    lensTelephoto = LensStop(3f, 70, optical = true),
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
                    // 包装图按 pack id 查：截图里要能看到真图而不是占位暗盒。
                    stockId = "gold200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(
                        LensStop(1f, 24, optical = true),
                        LensStop(3f, 70, optical = true),
                    ),
                    // 主长焦档（D-088）：该档右上角会标 T。
                    lensTelephoto = LensStop(3f, 70, optical = true),
                    imprintLabel = "135 半格 · 日期",
                    stockDetail = "4000×3000",
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-landscape")
    }

    /**
     * 横版·不可拍（D-089）：快门要灰着但**还在原位**，原因写在状态条里，
     * 闪光灯不可用要能一眼看出来（灰键 + "无闪光灯"）。
     */
    @Test
    @Config(qualifiers = "w804dp-h361dp-land-xxhdpi")
    fun cameraConsoleBlocked() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "VELVIA 50",
                    stockDetail = "4096×3072",
                    shotsWithStock = 0,
                    flashOn = false,
                    flashAvailable = false,
                    gridOn = false,
                    canShoot = false,
                    blockReason = "「VELVIA 50」需要 4096×3072，这台设备只能给 4000×3000",
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(LensStop(1f, 24, optical = true)),
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-console-blocked")
    }

    /** 横版·没有相机权限：取景窗还在（深色画框），里面是说明 + 唯一出口。 */
    @Test
    @Config(qualifiers = "w804dp-h361dp-land-xxhdpi")
    fun cameraConsoleNoPermission() {
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
        capture("camera-console-no-permission")
    }

    /**
     * 横版·EV 微调展开（点顶栏那颗 EV 键）：刻度下方多出 −/0/+ 三个键。
     * 重点看快门被压缩后**没有溢出**、也没有被盖住。
     */
    @Test
    @Config(qualifiers = "w804dp-h361dp-land-xxhdpi")
    fun cameraConsoleEvOpen() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "PORTRA 400",
                    stockDetail = "4000×3000",
                    shotsWithStock = 7,
                    flashOn = true,
                    flashAvailable = true,
                    gridOn = true,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 2,
                    lensStops = listOf(
                        LensStop(1f, 24, optical = true),
                        LensStop(3f, 70, optical = true),
                    ),
                    evInitiallyOpen = true,
                    imprintLabel = "日期后背",
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-console-ev-open")
    }

    /** 横版·抽屉展开：相册/胶卷/发现/设置。浅色面板，跟机身同一套材质。 */
    @Test
    @Config(qualifiers = "w804dp-h361dp-land-xxhdpi")
    fun cameraConsoleSwitcher() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    // 包装图按 pack id 查：截图里要能看到真图而不是占位暗盒。
                    stockId = "gold200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(LensStop(1f, 24, optical = true)),
                    switcherInitiallyOpen = true,
                    viewfinder = { modifier -> FakeViewfinder(modifier) },
                )
            }
        }
        capture("camera-console-switcher")
    }

    /** EV 面板展开：这是"面板压住变焦轨"那个 bug 的现场，必须能看到。 */
    @Test
    fun cameraEvPanel() {
        compose.setContent {
            PocketCcdTheme {
                CameraScreen(
                    stockName = "GOLD 200",
                    // 包装图按 pack id 查：截图里要能看到真图而不是占位暗盒。
                    stockId = "gold200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 2,
                    lensStops = listOf(
                        LensStop(1f, 24, optical = true),
                        LensStop(3f, 70, optical = true),
                    ),
                    // 主长焦档（D-088）：该档右上角会标 T。
                    lensTelephoto = LensStop(3f, 70, optical = true),
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
                    // 包装图按 pack id 查：截图里要能看到真图而不是占位暗盒。
                    stockId = "gold200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = false,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(
                        LensStop(1f, 24, optical = true),
                        LensStop(3f, 70, optical = true),
                    ),
                    // 主长焦档（D-088）：该档右上角会标 T。
                    lensTelephoto = LensStop(3f, 70, optical = true),
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
                GlassBackdrop {
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

    /**
     * 横版成片页（D-089 修正）：从快门进来的详情页必须留在横版，否则转屏会把
     * 正在飞的那张掐掉。重点看：照片是否等比吃满左半、右侧参数会不会被压缩到看不清、
     * 三个动作键是否都在屏内。
     */
    @Test
    @Config(qualifiers = "w804dp-h361dp-land-xxhdpi")
    fun detailLandscape() {
        // 横拍成片是 4000×3000，用它验 4:3 的画框（fakePhoto 本身是竖片）。
        val photo = fakePhoto().copy(width = 4000, height = 3000)
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
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
        capture("detail-landscape")
    }

    /** 横版成片页·冲洗失败：重试键 + 说明必须在右栏里放得下。 */
    @Test
    @Config(qualifiers = "w804dp-h361dp-land-xxhdpi")
    fun detailLandscapeFailed() {
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
                    PhotoDetailScreen(
                        status = DetailStatus.Failed,
                        stockLabel = "ACROS 100 II",
                        failureMessage = "Camera is closed.",
                        onBack = {},
                        onRetry = {},
                    )
                }
            }
        }
        capture("detail-landscape-failed")
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
    /**
     * 底部 dock 选中**中间**那格：胶囊必须落在第 2 格正中。
     *
     * 单独出一张图是因为上一版的胶囊位置靠等宽推算（不测量），算错一格的偏移
     * 在"选中第一格"时是看不出来的（偏移量为 0），必须选一个非零索引才验得出来。
     */
    @Test
    fun navTabs() {
        compose.setContent {
            PocketCcdTheme {
                Box(Modifier.fillMaxSize().background(ConsoleColor.body)) {
                    GlassNavBar(
                        active = GlassTab.Films,
                        onSelect = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp),
                    )
                }
            }
        }
        capture("nav-tabs")
    }

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
                        versionName = "0.9.0-beta1",
                        // debug 包里才有的诊断入口：截图里也要能看到它。
                        onRunSelfTest = {},
                    )
                }
            }
        }
        capture("settings")
    }

    /**
     * 检查更新的四种状态（D-091）。
     *
     * 这几态只在联网时出现，而联网恰恰是最难在测试里复现的部分 —— 不截图留档的话，
     * "发现新版本"那一行的金色、"检查失败"那一行的砖红，都只能靠真机碰运气。
     * 六块叠在一张图上，是为了让**右边缘不随状态跳动**这件事一眼可见。
     */
    @Test
    fun settingsUpdateStates() {
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
                    ) {
                        val noApk = ReleaseInfo(
                            tag = "v0.10.0",
                            version = "0.10.0",
                            htmlUrl = "https://example.com",
                        )
                        val withApk = noApk.copy(
                            apk = ApkAsset(
                                name = "pocket-ccd-0.10.0.apk",
                                url = "https://example.com/a.apk",
                                bytes = 45_789_583L,
                            ),
                        )
                        SettingsVersionBlock(
                            versionName = "0.9.0-beta1",
                            update = UpdateUi.Idle,
                            install = InstallState.Idle,
                            onCheck = {},
                            onOpenRelease = {},
                            onDownload = {},
                            onCancelDownload = {},
                            onInstall = {},
                            onRequestPermission = {},
                        )
                        SettingsVersionBlock(
                            versionName = "0.9.0-beta1",
                            update = UpdateUi.Checking,
                            install = InstallState.Idle,
                            onCheck = {},
                            onOpenRelease = {},
                            onDownload = {},
                            onCancelDownload = {},
                            onInstall = {},
                            onRequestPermission = {},
                        )
                        SettingsVersionBlock(
                            versionName = "0.9.0-beta1",
                            update = UpdateUi.Latest,
                            install = InstallState.Idle,
                            onCheck = {},
                            onOpenRelease = {},
                            onDownload = {},
                            onCancelDownload = {},
                            onInstall = {},
                            onRequestPermission = {},
                        )
                        // 发现新版：可下载（带体积）
                        SettingsVersionBlock(
                            versionName = "0.9.0-beta1",
                            update = UpdateUi.Available(withApk),
                            install = InstallState.Idle,
                            onCheck = {},
                            onOpenRelease = {},
                            onDownload = {},
                            onCancelDownload = {},
                            onInstall = {},
                            onRequestPermission = {},
                        )
                        // 没挂附件：退回页面
                        SettingsVersionBlock(
                            versionName = "0.9.0-beta1",
                            update = UpdateUi.Available(noApk),
                            install = InstallState.Idle,
                            onCheck = {},
                            onOpenRelease = {},
                            onDownload = {},
                            onCancelDownload = {},
                            onInstall = {},
                            onRequestPermission = {},
                        )
                        SettingsVersionBlock(
                            versionName = "0.9.0-beta1",
                            update = UpdateUi.NoRelease,
                            install = InstallState.Idle,
                            onCheck = {},
                            onOpenRelease = {},
                            onDownload = {},
                            onCancelDownload = {},
                            onInstall = {},
                            onRequestPermission = {},
                        )
                        SettingsVersionBlock(
                            versionName = "0.9.0-beta1",
                            update = UpdateUi.Failed("网络不可达"),
                            install = InstallState.Idle,
                            onCheck = {},
                            onOpenRelease = {},
                            onDownload = {},
                            onCancelDownload = {},
                            onInstall = {},
                            onRequestPermission = {},
                        )
                    }
                }
            }
        }
        capture("settings-update-states")
    }

    /**
     * 下载/安装的四种中间态（D-092）。
     *
     * 这四态全都发生在"用户已经跨过联网那一关"之后，但每一态都可能把人卡住：
     * 下载中要能取消、下好要能装、没权限要能去授权、失败要能重试。单独截一张，
     * 是为了看清进度线的高度、动作键在长文案旁边会不会挤成一团。
     */
    @Test
    fun settingsInstallStates() {
        val withApk = ReleaseInfo(
            tag = "v0.10.0",
            version = "0.10.0",
            htmlUrl = "https://example.com",
            apk = ApkAsset(
                name = "pocket-ccd-0.10.0.apk",
                url = "https://example.com/a.apk",
                bytes = 45_789_583L,
            ),
        )
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
                    ) {
                        val cases = listOf(
                            InstallState.Downloading(18_600_000L, 45_789_583L),
                            InstallState.Ready("0.10.0"),
                            InstallState.NeedPermission,
                            InstallState.Failed("下载不完整（12.0 MB / 43.7 MB）"),
                        )
                        cases.forEach { state ->
                            SettingsVersionBlock(
                                versionName = "0.9.0-beta1",
                                update = UpdateUi.Available(withApk),
                                install = state,
                                onCheck = {},
                                onOpenRelease = {},
                                onDownload = {},
                                onCancelDownload = {},
                                onInstall = {},
                                onRequestPermission = {},
                            )
                        }
                    }
                }
            }
        }
        capture("settings-install-states")
    }

    @Test
    fun discoverScreen() {
        val recent = fakePhoto()
        compose.setContent {
            PocketCcdTheme {
                GlassBackdrop {
                    DiscoverScreen(
                        photoCount = 42,
                        favoriteCount = 7,
                        stockCount = 8,
                        storageBytes = 1_240_000_000L,
                        recentPhotos = listOf(
                            PhotoWallItem(
                                id = "recent",
                                stockLabel = "GOLD 200",
                                capturedAt = 1_700_000_000_000,
                                imagePath = recent.thumbnailFile.absolutePath,
                            ),
                        ),
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
                        PhotoViewer(
                            photos = listOf(item),
                            initialIndex = 0,
                            favoriteIds = emptySet(),
                        )
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
                    // 包装图按 pack id 查：截图里要能看到真图而不是占位暗盒。
                    stockId = "gold200",
                    shotsWithStock = 12,
                    flashOn = false,
                    flashAvailable = true,
                    gridOn = true,
                    canShoot = true,
                    blockReason = null,
                    permissionGranted = true,
                    exposureProfile = ExposureProfile(-6, 6, 1f / 3f, 0),
                    evIndex = 0,
                    lensStops = listOf(
                        LensStop(1f, 24, optical = true),
                        LensStop(3f, 70, optical = true),
                    ),
                    // 主长焦档（D-088）：该档右上角会标 T。
                    lensTelephoto = LensStop(3f, 70, optical = true),
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
                    // 包装图按 pack id 查：截图里要能看到真图而不是占位暗盒。
                    stockId = "gold200",
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

    /**
     * 材质条：抬高 vs 按下（D-090）。
     *
     * 这一条专门盯**内阴影有没有真的画出来**。按下态在静态截图里抓不到（要真按住），
     * 而 `BlurMaskFilter` 在某些渲染路径上是"画不出来但不报错"的 —— 那种失败只有看图
     * 才发现。左边是静止的面板，右边是按下到底（`pressed = { 1f }`）的同一块面板：
     * 右图内沿必须有一圈由边向心的暗带，且上沿比下沿深。
     */
    @Test
    fun materialStates() {
        compose.setContent {
            PocketCcdTheme {
                Box(
                    Modifier.fillMaxSize().background(ConsoleColor.body),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        listOf(0f, 1f).forEach { amount ->
                            Box(
                                Modifier
                                    .size(132.dp)
                                    .consolePanel(
                                        color = ConsoleColor.panelLight,
                                        shape = GlassShape.panel,
                                        elevation = 8.dp,
                                    )
                                    .consolePress(GlassShape.panel, pressed = { amount }),
                            )
                        }
                    }
                }
            }
        }
        capture("material-states")
    }

    /**
     * 内测激活页（D-093）：空态。
     *
     * 这是内测用户看到的**第一屏**，所以值得单独盯：暖光层会不会把标题洗淡、
     * 输入框的"凹槽"读不读得出来、通栏键在 411dp 上会不会贴到卡片边缘。
     */
    @Test
    fun activationEmpty() {
        compose.setContent {
            PocketCcdTheme {
                ActivationPage(
                    code = "",
                    busy = false,
                    error = null,
                    whyOpen = false,
                    onCodeChange = {},
                    onSubmit = {},
                    onToggleWhy = {},
                )
            }
        }
        capture("activation-empty")
    }

    /** 错误态：码被占用。看的是朱红提示有没有把按钮往下挤、以及它与输入框的距离。 */
    @Test
    fun activationError() {
        compose.setContent {
            PocketCcdTheme {
                ActivationPage(
                    code = "PP1SR5XK",
                    busy = false,
                    error = "这个码已经在别的设备上用过了",
                    whyOpen = false,
                    onCodeChange = {},
                    onSubmit = {},
                    onToggleWhy = {},
                )
            }
        }
        capture("activation-error")
    }

    /** 说明展开 + 正在核对：看长文案会不会把版式顶出屏幕、按钮的禁用态是否还读得出。 */
    @Test
    fun activationWhyOpenBusy() {
        compose.setContent {
            PocketCcdTheme {
                ActivationPage(
                    code = "PP1SR5XK",
                    busy = true,
                    error = null,
                    whyOpen = true,
                    onCodeChange = {},
                    onSubmit = {},
                    onToggleWhy = {},
                )
            }
        }
        capture("activation-why-open")
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
