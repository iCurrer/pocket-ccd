package com.pocketccd.prototype.navigation

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketccd.prototype.camera.CameraHandle
import com.pocketccd.prototype.camera.CameraState
import com.pocketccd.prototype.camera.CameraUiState
import com.pocketccd.prototype.camera.CaptureState
import com.pocketccd.prototype.camera.CaptureTimeoutException
import com.pocketccd.prototype.camera.CapturedFrame
import com.pocketccd.prototype.camera.ImprintSettings
import com.pocketccd.prototype.camera.PhotoStore
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.camera.StockPrefs
import com.pocketccd.prototype.camera.decodeJpeg
import com.pocketccd.prototype.camera.finalRotationFor
import com.pocketccd.prototype.camera.frameServesPack
import com.pocketccd.prototype.camera.landscapeNormalized
import com.pocketccd.prototype.fotufilm.FilmEngine
import com.pocketccd.prototype.fotufilm.FilmPack
import com.pocketccd.prototype.fotufilm.FilmStock
import com.pocketccd.prototype.fotufilm.FilmStockCatalog
import com.pocketccd.prototype.fotufilm.FotufilmNative
import com.pocketccd.prototype.ui.films.FilmCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 应用级状态。导航（[AppUiState]）与拍摄（[CaptureState]）分开：
 * 一次冲洗要 2–5 秒，这期间用户可以来回翻页，导航状态不能被冲洗进度绑住。
 *
 * ## 相对上一版的结构变化（D-070）
 *
 * **"一卷 36 张"整套模型被移除**，不是"保留但不锁快门"：
 * - `rollStartedAt` / `startNewRoll` / 36 格容量 / 满卷禁快门 / "换卷"确认框全部删除；
 * - 计数只剩一种口径：**这一支胶卷在本机累计拍过多少张**（按 `stockId` 从现存成片
 *   聚合），以及本机总张数；
 * - 换胶卷 = 直接装另一支，不重置任何计数，也不删任何照片。
 *
 * 为什么连"留着格子但不锁快门"也不做：复审（2026-11-01）已经证明这个计数是
 * "存活照片数"，从相册删一张计数就倒退 —— 只要界面上还有 36 个格子，它就在暗示
 * 一个不成立的事实。去掉格子，进度只需要说"拍过几张"。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val photoStore = PhotoStore(application)
    private val stockDirectory = FilmStockCatalog.directory(application)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _capture = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val capture: StateFlow<CaptureState> = _capture.asStateFlow()

    private val _roll = MutableStateFlow<List<SavedPhoto>>(emptyList())
    val roll: StateFlow<List<SavedPhoto>> = _roll.asStateFlow()

    /** 成片占用的字节数（私有目录里的 full 副本）。发现页显示真实占用。 */
    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private val _stocks = MutableStateFlow<List<FilmStock>>(emptyList())

    /** 上次装的那支卷（D-089 追加）。落盘是为了"每次开相机不用重选"。 */
    private val stockPrefs = StockPrefs(application)

    private val _selectedStockId = MutableStateFlow(stockPrefs.loadSelectedStockId())
    private val _camera = MutableStateFlow<CameraState>(CameraState.Pending)

    /**
     * 装卷时因为设备拍不了而被自动换掉的提示。
     *
     * 旧版是**静默**换卷（复审 P3-12）：用户选了 A，绑定后发现这台机器给不了 A 需要的
     * 分辨率，代码悄悄换成 B，界面上没有任何交代。现在换成一句话交回 UI，
     * 由取景页弹一次提示后清空。
     */
    private val _stockSwitchedNotice = MutableStateFlow<String?>(null)
    val stockSwitchedNotice: StateFlow<String?> = _stockSwitchedNotice.asStateFlow()

    /**
     * 请求相机层重绑的信号，自增即一次重绑。
     *
     * 只用在一处：快门超时（[CaptureTimeoutException]）。超时说明这条会话可能已经废了
     * （转屏重建、被别的客户端抢走），只把 UI 从"正在冲洗"里放出来还不够 ——
     * 再按一次快门会立刻撞同一个坑。重绑之后拿到的是新会话，重试才有意义。
     */
    private val _rebindRequests = MutableStateFlow(0)
    val rebindRequests: StateFlow<Int> = _rebindRequests.asStateFlow()

    val cameraUi: StateFlow<CameraUiState> =
        combine(_stocks, _selectedStockId, _camera, _capture) { stocks, selected, camera, capture ->
            buildCameraUi(stocks, selected, camera, capture)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CameraUiState(),
        )

    /**
     * 胶卷库要的每卷一行：[filmCards]。
     *
     * 三个字段的来源刻意分开，因为它们的**可信度不一样**：
     * - `supported` / `reason` 来自相机报告的采集尺寸集合，是硬件事实；
     * - `shots` 来自现存成片按 `stockId` 聚合 —— 这是"这支卷在本机拍过几张"，
     *   不是"本卷还剩几张"。用户从系统相册删一张，它会减一；
     * - `latestThumbnail` 是那一卷最新成片的缩略图。没拍过就为 null，界面画胶卷筒，
     *   绝不用一张渐变占位图冒充"这卷拍出来长这样"（D-002）。
     *
     * 相机还 [CameraState.Pending] 时**不判不支持**：那时尺寸集合是空集，
     * 若照实判会让整页在冷启动的几百毫秒里全是灰的。未知不等于不可用。
     */
    val filmCards: StateFlow<List<FilmCard>> =
        combine(_stocks, _camera, _roll) { stocks, camera, roll ->
            val bound = camera as? CameraState.Bound
            val sizes = bound?.supportedSizes.orEmpty()
            stocks.map { stock ->
                val shots = roll.filter { it.stockId == stock.id }
                FilmCard(
                    id = stock.id,
                    label = stock.label,
                    // 这是 pack 的校准尺寸，不是成片尺寸 —— 成片尺寸见 CameraUiState.frameLabel。
                    frameLabel = stock.sizeLabel,
                    // 任何一支卷在任何一台设备上都能拍：给不到满幅就走原生直通（D-098），
                    // 亮度/尺度都正确，只是像素少一点。旧版在这里按"尺寸 ≥ pack"过滤，
                    // 8MP 机型会看到整库都是灰的。
                    supported = bound == null || sizes.isNotEmpty(),
                    reason = if (bound != null && sizes.isEmpty()) {
                        "相机没有报告可用的采集尺寸"
                    } else {
                        null
                    },
                    shots = shots.size,
                    latestThumbnail = shots
                        .maxByOrNull { it.capturedAt }
                        ?.thumbnailFile
                        ?.absolutePath,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 上一次快门用的取景器句柄，供详情页「重试」复用。 */
    private var lastTaker: (suspend () -> CapturedFrame)? = null

    /** 上一次快门的贴花设置，供「重试」原样复用（不能套用重试时的当前设置）。 */
    private var lastImprint: ImprintSettings = ImprintSettings()

    /** 防连点。`_capture.isBusy` 的检查与赋值之间隔着一次协程调度，只靠状态判断有窗口。 */
    private val capturing = AtomicBoolean(false)

    /** 对账防重入。回前台与进照片墙两条路径可能同时触发。 */
    private val reconciling = AtomicBoolean(false)

    /** 对账期间又收到了一次请求：当前这轮结束后必须补跑，不能丢。 */
    private val reconcilePending = AtomicBoolean(false)

    private companion object {
        /** 手测时 `adb logcat -s PocketCcdCapture` 能看到每次冲洗的尺寸/耗时/非有限值。 */
        const val TAG = "PocketCcdCapture"
    }

    init {
        refreshStocks()
        refreshRoll()
        // 把历史成片补进系统相册。早先的版本只写私有目录，那些照片在相册里根本
        // 不存在（`Android/data` 被媒体扫描明确排除），这里补一次；已经写过的跳过。
        viewModelScope.launch(Dispatchers.IO) { photoStore.syncToGallery() }
    }

    // ---- 导航 ----

    fun onEvent(event: AppEvent) {
        _state.value = reduce(_state.value, event)
    }

    // ---- 胶卷 ----

    fun refreshStocks() {
        viewModelScope.launch {
            val stocks = withContext(Dispatchers.IO) {
                // 内置 pack 先落盘再扫描：第一次启动 / 重装后 files/film 是空的，
                // 不补这一步别人装完只能看到"没有可用的胶卷"。
                FilmStockCatalog.provisionBundled(getApplication(), stockDirectory)
                FilmStockCatalog.scan(stockDirectory)
            }
            _stocks.value = stocks
            if (stocks.none { it.id == _selectedStockId.value }) {
                _selectedStockId.value = stocks.firstOrNull()?.id
            }
        }
    }

    /**
     * 装卷。胶卷库点某一卷时走这里。
     *
     * **装卷不再需要"换卷"这个动作**（D-070）：换一支卷不会重置任何计数，
     * 它只是换一个 `stockId` 过滤器。已经拍下的照片按各自的 stockId 归属，
     * 切回去能看到那支卷原来的张数。
     */
    fun selectStock(id: String) {
        if (_stocks.value.any { it.id == id }) {
            _selectedStockId.value = id
            // 只有"用户明确装了一支"才落盘。相机绑定后的自动换卷不写（见 StockPrefs）。
            stockPrefs.saveSelectedStockId(id)
            _stockSwitchedNotice.value = null
        }
    }

    /** 取景页弹过提示之后调用。 */
    fun consumeStockNotice() {
        _stockSwitchedNotice.value = null
    }

    // ---- 相机 ----

    fun onCameraBound(handle: CameraHandle) {
        // 每次绑定都刷新快门句柄：重绑后旧 ImageCapture 已经解绑，详情页「重试」
        // 若还拿着旧句柄，只会得到 "Not bound to a valid Camera"（review P2）。
        lastTaker = handle::takeJpeg
        _camera.value = CameraState.Bound(handle.supportedSizes, handle.hasFlash)
        // **不再自动切卷**。旧逻辑在"这台设备给不了这卷的尺寸"时把用户选中的卷换掉，
        // 因为那时尺寸不够等于拍不了。D-098 之后任何尺寸都能出片（不够就按原生尺寸），
        // 自动切卷只会凭空夺走用户的选择 —— 换卷是用户的动作，不是我们的。
    }

    fun onCameraReleased() {
        _camera.value = CameraState.Pending
    }

    fun onCameraError(message: String) {
        _camera.value = CameraState.Unavailable(message)
    }

    // ---- 快门 ----

    /**
     * 拍一张并冲洗。
     *
     * [taker] 由相机页传入（`CameraHandle::takeJpeg`），ViewModel 因此不认识 CameraX。
     * 调用方应先于导航调用本方法：这里会**同步**把状态置为 [CaptureState.Capturing]，
     * 详情页推入时读到的就已经是"正在冲洗"，不会先闪一下上一张的旧结果。
     */
    fun capture(taker: suspend () -> CapturedFrame, imprint: ImprintSettings = ImprintSettings()) {
        lastTaker = taker
        lastImprint = imprint
        val stock = cameraUi.value.activeStock
        if (stock == null) {
            _capture.value = CaptureState.Failed("没有可用的胶卷", null)
            return
        }
        if (!capturing.compareAndSet(false, true)) return

        _capture.value = CaptureState.Capturing(stock)

        viewModelScope.launch {
            try {
                val frame = taker()
                withContext(Dispatchers.Default) {
                    _capture.value = CaptureState.Developing(stock)
                    val developed = develop(
                        frame.jpeg,
                        stock,
                        frame.rotationDegrees,
                        frame.sensorMountRotation,
                    )
                    try {
                        _capture.value = CaptureState.Saving(stock)
                        val saved = photoStore.save(
                            bitmap = developed.bitmap,
                            stock = stock,
                            // 快门那一刻的时间，不是落盘时间（冲洗要 2–4 秒）。
                            // 这个值同时决定文件名/排序/DATE_TAKEN/详情页的拍摄时间。
                            capturedAt = frame.takenAt,
                            renderMillis = developed.renderMillis,
                            strips = developed.strips,
                            // 落盘只补**设备握持角**这一步。
                            //
                            // 两个角度不是同一件事，不能相减：
                            // - 解码只把竖置帧归一化回**传感器帧**（`appliedRotationDegrees`），
                            //   转完仍是"相机芯片看到的样子"，还不是用户看到的样子；
                            // - 正立要在它之上再叠握持角。
                            //
                            // 但也**不能无条件叠**：握持角是 `(SENSOR_ORIENTATION - displayRotation)`，
                            // 而 displayRotation 在锁横版的界面里会说谎。所以由 develop 统一
                            // 收敛成 `developed.rotationDegrees` —— 横帧只接受 0/180，
                            // 90/270 一律收回 0（见 [finalRotationFor]），成品不会再被翻成竖版。
                            rotationDegrees = developed.rotationDegrees,
                            // P1-14：把相机原始帧里读到的四项带进落盘这一步。
                            // 晚一步就没了 —— 成片的像素已经重新编码过，EXIF 只剩这里的值。
                            exif = frame.exif,
                            imprint = imprint,
                        )
                        _capture.value = CaptureState.Done(saved)
                        _roll.update { listOf(saved) + it }
                        _storageBytes.update { it + saved.fullFile.length() }
                    } finally {
                        // save 抛异常时也要回收：12MP 的 ARGB 是 48MB，
                        // 一次泄漏就足够让下一次拍摄 OOM。
                        developed.bitmap.recycle()
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "capture failed on ${stock.label}", error)
                // 超时不是"数据坏了"，是"这条会话不再回话"。放掉 UI 状态还不够，
                // 得把相机会话换掉，否则重试只会再撞一次同样的 5 秒。
                if (error is CaptureTimeoutException) _rebindRequests.update { it + 1 }
                _capture.value = CaptureState.Failed(
                    message = error.message ?: error.javaClass.simpleName,
                    stock = stock,
                )
            } finally {
                capturing.set(false)
            }
        }
    }

    fun retry() {
        val taker = lastTaker
        if (taker == null) {
            _capture.value = CaptureState.Failed("相机已断开，请重新进入拍摄页", null)
            return
        }
        // 重试用上一次快门的贴花设置：失败后跑去设置里关掉片边，不该改变这张的含义。
        capture(taker, lastImprint)
    }

    fun refreshRoll() {
        viewModelScope.launch {
            val photos = withContext(Dispatchers.IO) { photoStore.list() }
            _roll.value = photos
            _storageBytes.value = withContext(Dispatchers.IO) { photoStore.usedBytes() }
        }
    }

    /**
     * 和系统相册对账：用户在**系统相册**里删掉的成片，照片墙也要跟着消失。
     *
     * 触发点两处：回到前台（用户多半是切去相册删完再切回来）、进照片墙之前。
     *
     * 与旧版的差别：旧版在 `reconciling` 为 true 时**直接 return**，于是
     * "进照片墙"那次请求若正好撞上"回前台"那轮就被静默丢弃，已删照片要挂到下一次
     * resume 才消失（旧复审 P2#16）。现在改成记账 + 补跑一轮。
     */
    fun reconcileGallery() {
        if (!reconciling.compareAndSet(false, true)) {
            reconcilePending.set(true)
            return
        }
        viewModelScope.launch {
            try {
                do {
                    reconcilePending.set(false)
                    val report = withContext(Dispatchers.IO) { photoStore.reconcileWithGallery() }
                    if (report.removed > 0) {
                        _roll.value = withContext(Dispatchers.IO) { photoStore.list() }
                        _storageBytes.value = withContext(Dispatchers.IO) { photoStore.usedBytes() }
                    }
                } while (reconcilePending.get())
            } finally {
                reconciling.set(false)
            }
        }
    }

    /** 冲洗本体：解码（必要时转正/裁切/原生直通）→ 引擎 → 成品位图。跑在 [Dispatchers.Default] 上。 */
    private fun develop(
        jpeg: ByteArray,
        stock: FilmStock,
        rotationDegrees: Int,
        sensorMountRotation: Int?,
    ): Developed {
        val target = Size(stock.width, stock.height)
        val frame = decodeJpeg(
            bytes = jpeg,
            target = target,
            stockLabel = stock.label,
            sensorMountRotation = sensorMountRotation,
            deviceRotationDegrees = rotationDegrees,
        )
        val source = frame.bitmap
        // 画布跟**帧**同尺寸，不是跟 pack 同尺寸：帧比 pack 小时走原生直通（D-098），
        // 引擎按传入的行列数解算空间参数，输出必须与输入同大小。
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        return try {
            FilmPack.fromFile(stock.file).use { pack ->
                val engine = FilmEngine(pack, shoulderKnee = stock.shoulderKnee)
                check(engine.isUsable()) {
                    "原生内核不可用：${FotufilmNative.loadFailure?.message ?: "Halide 内核未就绪"}"
                }
                val result = engine.render(source, output)
                // 落盘角度：**这里是唯一一处**把"归一化角 + 握持角"收敛成成品的旋转。
                //
                // 冲洗用的帧是横的（归一化之后必然如此，竖版帧要么已转正要么已报错），
                // 所以成品也必须是横的：`rotationDegrees` 只允许决定 0/180，90/270
                // 一律收回 0。真机上就交过一张横持拍、冲洗成 4000×3000、落盘又转 90°
                // 变成 3000×4000 的躺倒竖版照片 —— 一加 13 正常、另一台必现。
                val composedRotation = finalRotationFor(
                    frameIsLandscape = source.width >= source.height,
                    requestedRotation = rotationDegrees,
                )
                val clamped = composedRotation != ((rotationDegrees % 360) + 360) % 360
                val finalWidth =
                    if (composedRotation % 180 == 0) output.width else output.height
                val finalHeight =
                    if (composedRotation % 180 == 0) output.height else output.width
                Log.i(
                    TAG,
                    "developed ${stock.label} ${source.width}x${source.height} " +
                        "in ${result.totalMillis}ms / ${result.strips} strips / " +
                        "nonFinite=${result.nonFinite} / " +
                        "frame=${frame.sourceWidth}x${frame.sourceHeight}" +
                        " / 归一化=${frame.appliedRotationDegrees}°" +
                        " / 握持=$rotationDegrees°" +
                        " / 落盘=$composedRotation°" +
                        (if (clamped) "（握持角会把横版翻成竖版，已收回）" else "") +
                        frame.describeCrop()?.let { " / $it" }.orEmpty() +
                        " / final=${finalWidth}x$finalHeight",
                )
                Developed(
                    renderMillis = result.totalMillis,
                    strips = result.strips,
                    bitmap = output,
                    rotationDegrees = composedRotation,
                )
            }
        } catch (error: Throwable) {
            output.recycle()
            throw error
        } finally {
            source.recycle()
        }
    }

    private class Developed(
        val renderMillis: Long,
        val strips: Int,
        val bitmap: Bitmap,
        /** 落盘真正要转的角度（已按"横版构图不可翻成竖版"收敛，见 [finalRotationFor]）。 */
        val rotationDegrees: Int,
    )

    private fun buildCameraUi(
        stocks: List<FilmStock>,
        selectedId: String?,
        camera: CameraState,
        capture: CaptureState,
    ): CameraUiState {
        val supported = (camera as? CameraState.Bound)?.supportedSizes.orEmpty()
        val active = stocks.firstOrNull { it.id == selectedId } ?: stocks.firstOrNull()
        // 报尺寸一律报「长边×短边」：竖置帧转 90° 后像素一个不少，
        // 直接印 3072×4096 会让用户以为比 4000×3000 差。
        val largest = supported.maxByOrNull { it.width.toLong() * it.height }?.landscapeNormalized()

        // 请求 pack 的尺寸，让 CameraX 挑最接近的一档：设备有精确档就直接给精确的，
        // 没有也会给"最接近的更高档"，再由 decodeJpeg 中心裁切；比 pack 还小的档
        // 走原生直通（D-098），不再被挡。
        //
        // **只要知道是哪卷就下发**，不再等"已验证能服务"。旧逻辑在相机还是 Pending 时
        // 传 null，于是第一次绑定无约束、拿到真实尺寸后再按 pack 尺寸重绑一次 ——
        // 每次冷启动/回取景页都绑两次，中间预览黑一下，而且 handle 已置空、canShoot
        // 还是 true，按快门静默无反应。
        val programmed = active?.let { Size(it.width, it.height) }

        // 画幅标注必须说实话：设备给不到 pack 满幅时，成片就是设备尺寸（D-098），
        // 这时还写 4000×3000 就是编数据。
        val frameLabel = when {
            active == null -> null
            largest == null -> active.sizeLabel
            frameServesPack(largest, Size(active.width, active.height)) -> active.sizeLabel
            else -> "${largest.width}×${largest.height}"
        }

        val reason = when {
            stocks.isEmpty() -> "没有可用的胶卷"
            camera is CameraState.Pending -> "正在启动相机"
            camera is CameraState.Unavailable -> camera.reason
            supported.isEmpty() -> "相机没有报告可用的采集尺寸"
            capture.isBusy -> "正在冲洗上一张"
            else -> null
        }

        return CameraUiState(
            stocks = stocks,
            activeStock = active,
            camera = camera,
            canShoot = reason == null,
            blockReason = reason,
            frameLabel = frameLabel,
            largestSupported = largest,
            programmedSize = programmed,
        )
    }
}
