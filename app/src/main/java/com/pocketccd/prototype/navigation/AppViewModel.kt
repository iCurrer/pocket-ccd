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
import com.pocketccd.prototype.camera.CapturedFrame
import com.pocketccd.prototype.camera.PhotoStore
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.camera.decodeJpeg
import com.pocketccd.prototype.fotufilm.FilmEngine
import com.pocketccd.prototype.fotufilm.FilmPack
import com.pocketccd.prototype.fotufilm.FilmStock
import com.pocketccd.prototype.fotufilm.FilmStockCatalog
import com.pocketccd.prototype.fotufilm.FotufilmNative
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
 * 一次冲洗要 2–3 秒，这期间用户可以来回翻页，导航状态不能被冲洗进度绑住。
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

    /** 本卷起点。UI 只关心"这卷拍了几张"，所以起点本身留在内部。 */
    private val _rollStartedAt = MutableStateFlow(photoStore.rollStartedAt())

    /**
     * 本卷已拍张数。**不是**照片库总数——库里有 35 张，不代表这卷只剩 1 张。
     * 计数器、进度条、满卷判断都以它为准。
     */
    val rollShots: StateFlow<Int> =
        combine(_roll, _rollStartedAt) { roll, startedAt ->
            roll.count { it.capturedAt > startedAt }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    private val _stocks = MutableStateFlow<List<FilmStock>>(emptyList())
    private val _selectedStockId = MutableStateFlow<String?>(null)
    private val _camera = MutableStateFlow<CameraState>(CameraState.Pending)

    val cameraUi: StateFlow<CameraUiState> =
        combine(_stocks, _selectedStockId, _camera, _capture) { stocks, selected, camera, capture ->
            buildCameraUi(stocks, selected, camera, capture)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CameraUiState(),
        )

    /** 上一次快门用的取景器句柄，供成片页 RETRY 复用。 */
    private var lastTaker: (suspend () -> CapturedFrame)? = null

    /**
     * 防连点。`_capture.isBusy` 的检查与赋值之间隔着一次协程调度，只靠状态判断会有窗口。
     */
    private val capturing = AtomicBoolean(false)

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
            val stocks = withContext(Dispatchers.IO) { FilmStockCatalog.scan(stockDirectory) }
            _stocks.value = stocks
            if (stocks.none { it.id == _selectedStockId.value }) {
                _selectedStockId.value = stocks.firstOrNull()?.id
            }
        }
    }

    /**
     * 装卷。胶卷库（T9-016）点某一卷时走这里。
     *
     * 已删除 `nextStock()`：取景页的卷名 chip 改为打开胶卷库（它自带 `ChevronRight`，
     * 视觉本来就是"进入"）。循环切卷在超过两卷后只能靠记忆数点击次数。
     */
    fun selectStock(id: String) {
        if (_stocks.value.any { it.id == id }) _selectedStockId.value = id
    }

    /** 换卷：计数器归零，照片库不动。相机页底部的「换卷」磁贴走这里。 */
    fun newRoll() {
        _rollStartedAt.value = photoStore.startNewRoll()
    }

    // ---- 相机 ----

    fun onCameraBound(handle: CameraHandle) {
        _camera.value = CameraState.Bound(handle.supportedSizes, handle.hasFlash)
        // 自动落到一个**当前采集尺寸能服务**的卷上：精确相等最好，更大则中心裁切，
        // 更小则拍不了。宁可切卷，也不要偷偷缩放（D-037）。
        val current = _stocks.value.firstOrNull { it.id == _selectedStockId.value }
        if (current == null || handle.supportedSizes.servingSizeFor(current) == null) {
            _selectedStockId.value = _stocks.value
                .firstOrNull { handle.supportedSizes.servingSizeFor(it) != null }?.id
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
     * 成片页推入时读到的就已经是"正在冲洗"，不会先闪一下上一张的旧结果。
     */
    fun capture(taker: suspend () -> CapturedFrame) {
        lastTaker = taker
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
                            capturedAt = System.currentTimeMillis(),
                            renderMillis = developed.renderMillis,
                            strips = developed.strips,
                            rotationDegrees = frame.rotationDegrees,
                        )
                        _capture.value = CaptureState.Done(saved)
                        _roll.update { listOf(saved) + it }
                    } finally {
                        // save 抛异常时也要回收：12MP 的 ARGB 是 48MB，
                        // 一次泄漏就足够让下一次拍摄 OOM。
                        developed.bitmap.recycle()
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "capture failed on ${stock.label}", error)
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
        capture(taker)
    }

    fun refreshRoll() {
        viewModelScope.launch {
            _roll.value = withContext(Dispatchers.IO) { photoStore.list() }
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
                val engine = FilmEngine(pack)
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
                Developed(result.totalMillis, result.strips, output)
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
        // 只要当前采集尺寸能服务这卷，这个请求就不会把会话带偏到拍不了的尺寸上。
        // 用 `active?.let` 而不是 `if (active != null)`：后者会被编译器判定为恒真，
        // 因为 serving 由 active 派生，"能服务"本身就蕴含"有活跃卷"。
        val programmed = active?.let { stock ->
            if (serving != null) Size(stock.width, stock.height) else null
        }

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
