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
import com.pocketccd.prototype.camera.decodeJpeg
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
    private val _selectedStockId = MutableStateFlow<String?>(null)
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
            val largest = sizes.maxByOrNull { it.width.toLong() * it.height }
            stocks.map { stock ->
                val serves = sizes.servingSizeFor(stock)
                val shots = roll.filter { it.stockId == stock.id }
                FilmCard(
                    id = stock.id,
                    label = stock.label,
                    frameLabel = stock.sizeLabel,
                    supported = bound == null || serves != null,
                    reason = when {
                        bound == null -> null
                        serves != null -> null
                        largest != null ->
                            "这台设备只能给 ${largest.width}×${largest.height}"
                        else -> "相机没有报告可用的采集尺寸"
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
        // 自动落到一个**当前采集尺寸能服务**的卷上：精确相等最好，更大则中心裁切，
        // 更小则拍不了。宁可切卷，也不要偷偷缩放（D-037）。
        val current = _stocks.value.firstOrNull { it.id == _selectedStockId.value }
        if (current == null || handle.supportedSizes.servingSizeFor(current) == null) {
            val replacement = _stocks.value
                .firstOrNull { handle.supportedSizes.servingSizeFor(it) != null }
            if (replacement != null && replacement.id != _selectedStockId.value) {
                _selectedStockId.value = replacement.id
                // 只有"用户明确装过一支、但这台机器给不了"才提示；
                // 冷启动时 current == null 属于初始化，不打扰用户。
                if (current != null) {
                    _stockSwitchedNotice.value =
                        "这台设备的采集尺寸拍不了「${current.label}」，已换成「${replacement.label}」"
                }
            }
        }
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
                    val developed = develop(frame.jpeg, stock, frame.rotationDegrees)
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
                            // 解码阶段若已经为了"转置→横向"转过一次，这里必须减掉，
                            // 否则两处都按"转到正立"处理，成品会多转 90/180°。
                            rotationDegrees = (
                                frame.rotationDegrees - developed.appliedRotationDegrees + 360
                                ) % 360,
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

    /** 冲洗本体：解码（必要时裁切）→ 引擎 → 成品位图。整个函数跑在 [Dispatchers.Default] 上。 */
    private fun develop(jpeg: ByteArray, stock: FilmStock, rotationDegrees: Int): Developed {
        val target = Size(stock.width, stock.height)
        val frame = decodeJpeg(jpeg, target, stock.label)
        val source = frame.bitmap
        val output = Bitmap.createBitmap(stock.width, stock.height, Bitmap.Config.ARGB_8888)
        return try {
            FilmPack.fromFile(stock.file).use { pack ->
                val engine = FilmEngine(pack, shoulderKnee = stock.shoulderKnee)
                check(engine.isUsable()) {
                    "原生内核不可用：${FotufilmNative.loadFailure?.message ?: "Halide 内核未就绪"}"
                }
                val result = engine.render(source, output)
                Log.i(
                    TAG,
                    "developed ${stock.label} ${source.width}x${source.height} " +
                        "in ${result.totalMillis}ms / ${result.strips} strips / " +
                        "nonFinite=${result.nonFinite} / rotate=$rotationDegrees°" +
                        frame.describeCrop()?.let { " / $it" }.orEmpty(),
                )
                Developed(
                    renderMillis = result.totalMillis,
                    strips = result.strips,
                    bitmap = output,
                    appliedRotationDegrees = frame.appliedRotationDegrees,
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
        /** 解码阶段已经转过的角度，落盘旋转要把它减掉（否则转两遍）。 */
        val appliedRotationDegrees: Int,
    )

    /**
     * 这份采集尺寸能否服务这卷胶卷。
     *
     * - **相等**：直接冲洗，零损失。
     * - **更大**：中心裁切到 pack 尺寸。裁切不改变 pixels-per-mm，pack 的空间参数
     *   （扩散/晕光/颗粒半径）依然成立，只是取景范围变窄 —— 这是同一份 pack 能服务
     *   多种机型的唯一正确办法。
     * - **更小**：不行。绝不缩放：缩放会改 pixels-per-mm，等于换了光学系统。
     */
    private fun Size.canServe(stock: FilmStock): Boolean =
        width >= stock.width && height >= stock.height

    /** 能服务 [stock] 的最小采集尺寸。挑最小那档：内存与耗时都正比于像素数。 */
    private fun Set<Size>.servingSizeFor(stock: FilmStock): Size? =
        filter { it.canServe(stock) }.minByOrNull { it.width.toLong() * it.height }

    private fun buildCameraUi(
        stocks: List<FilmStock>,
        selectedId: String?,
        camera: CameraState,
        capture: CaptureState,
    ): CameraUiState {
        val supported = (camera as? CameraState.Bound)?.supportedSizes.orEmpty()
        val active = stocks.firstOrNull { it.id == selectedId } ?: stocks.firstOrNull()
        val largest = supported.maxByOrNull { it.width.toLong() * it.height }
        val serving = active?.let { supported.servingSizeFor(it) }

        // 请求 pack 的尺寸，让 CameraX 挑最接近的一档：设备有精确档就直接给精确的，
        // 没有也会给"最接近的更高档"，再由 decodeJpeg 中心裁切。
        //
        // **只要知道是哪卷就下发**，不再等"已验证能服务"。旧逻辑在相机还是 Pending 时
        // 传 null，于是第一次绑定无约束、拿到真实尺寸后再按 pack 尺寸重绑一次 ——
        // 每次冷启动/回取景页都绑两次，中间预览黑一下，而且 handle 已置空、canShoot
        // 还是 true，按快门静默无反应。
        val programmed = active?.let { Size(it.width, it.height) }

        val reason = when {
            stocks.isEmpty() -> "没有可用的胶卷"
            camera is CameraState.Pending -> "正在启动相机"
            camera is CameraState.Unavailable -> camera.reason
            supported.isEmpty() -> "相机没有报告可用的采集尺寸"
            serving == null && active != null && largest != null ->
                "「${active.label}」需要 ${active.sizeLabel}，" +
                    "这台设备只能给 ${largest.width}×${largest.height}"
            serving == null -> "没有与相机分辨率匹配的胶卷"
            capture.isBusy -> "正在冲洗上一张"
            else -> null
        }

        return CameraUiState(
            stocks = stocks,
            activeStock = active,
            camera = camera,
            canShoot = reason == null,
            blockReason = reason,
            largestSupported = largest,
            programmedSize = programmed,
        )
    }
}
