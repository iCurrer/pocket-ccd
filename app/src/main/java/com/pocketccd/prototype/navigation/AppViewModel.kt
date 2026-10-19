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
    private var lastTaker: (suspend () -> ByteArray)? = null

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

    fun selectStock(id: String) {
        if (_stocks.value.any { it.id == id }) _selectedStockId.value = id
    }

    /** 切到下一卷。相机页的卷名 chip 点一下就走这里。 */
    fun nextStock() {
        val stocks = _stocks.value
        if (stocks.isEmpty()) return
        val index = stocks.indexOfFirst { it.id == _selectedStockId.value }
        _selectedStockId.value = stocks[(index + 1).mod(stocks.size)].id
    }

    // ---- 相机 ----

    fun onCameraBound(handle: CameraHandle) {
        _camera.value = CameraState.Bound(handle.supportedSizes, handle.hasFlash)
        // 自动落到一个**尺寸精确匹配**的卷上：pack 的像素半径不能换尺寸（D-037），
        // 所以这不是"选个差不多的"，而是找不到就明确没有。
        val current = _stocks.value.firstOrNull { it.id == _selectedStockId.value }
        if (current == null || !handle.supportedSizes.matches(current)) {
            _selectedStockId.value = _stocks.value.firstOrNull { handle.supportedSizes.matches(it) }?.id
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
    fun capture(taker: suspend () -> ByteArray) {
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
                val jpeg = taker()
                withContext(Dispatchers.Default) {
                    _capture.value = CaptureState.Developing(stock)
                    val developed = develop(jpeg, stock)
                    try {
                        _capture.value = CaptureState.Saving(stock)
                        val saved = photoStore.save(
                            bitmap = developed.bitmap,
                            stock = stock,
                            capturedAt = System.currentTimeMillis(),
                            renderMillis = developed.renderMillis,
                            strips = developed.strips,
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

    /** 冲洗本体：解码 → 引擎 → 成品位图。整个函数跑在 [Dispatchers.Default] 上。 */
    private fun develop(jpeg: ByteArray, stock: FilmStock): Developed {
        val expected = Size(stock.width, stock.height)
        val source = decodeJpeg(jpeg, expected)
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
                        "nonFinite=${result.nonFinite}",
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

    private fun Set<Size>.matches(stock: FilmStock): Boolean =
        any { it.width == stock.width && it.height == stock.height }

    private fun buildCameraUi(
        stocks: List<FilmStock>,
        selectedId: String?,
        camera: CameraState,
        capture: CaptureState,
    ): CameraUiState {
        val supported = (camera as? CameraState.Bound)?.supportedSizes.orEmpty()
        val active = stocks.firstOrNull { it.id == selectedId } ?: stocks.firstOrNull()
        val largest = supported.maxByOrNull { it.width.toLong() * it.height }
        val matches = active != null && supported.matches(active)

        val reason = when {
            stocks.isEmpty() -> "没有可用的胶卷"
            camera is CameraState.Pending -> "正在启动相机"
            camera is CameraState.Unavailable -> camera.reason
            supported.isEmpty() -> "相机没有报告可用的采集尺寸"
            !matches && active != null && largest != null ->
                "相机不支持 ${active.sizeLabel}，最大 ${largest.width}×${largest.height}"
            !matches -> "没有与相机分辨率匹配的胶卷"
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
        )
    }
}
