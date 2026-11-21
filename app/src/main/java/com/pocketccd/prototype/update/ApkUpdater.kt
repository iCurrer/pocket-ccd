package com.pocketccd.prototype.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.pocketccd.prototype.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 应用内更新时的那几步：下载 → 校验 → 交给系统安装器（D-092）。
 *
 * ## 为什么不直接用系统的 DownloadManager
 *
 * DownloadManager 能把下载交给别的进程（应用退到后台也不断），代价是**进度只能靠
 * 轮询 ContentProvider**、失败原因被压成一个 `reason` 整数、而且它会往用户的通知栏里
 * 挂一条"正在下载" —— 一个相机应用为了自己升级在通知栏常驻，不值。这里只有 45 MB、
 * 只在一个设置页里发生，前台 `HttpURLConnection` 加一个协程就够了。
 *
 * ## 状态为什么放在单例里
 *
 * 下载要几十秒，用户完全可能点完就切到相册再切回来。状态挂在 Composable 的
 * `remember` 上会在离开设置页时被连根拔掉 —— 进度条归零、文件却还在下。
 * 放在 object 里，界面来去自由，下载不受影响。
 *
 * ## 装之前为什么要自己验一遍
 *
 * 系统安装器对坏包只会说"解析软件包时出现问题"，看不出是哪一步坏的。更麻烦的是
 * 国内网络环境下 GitHub 的下载域名可能被中间设备拦成 **HTTP 200 的 HTML 错误页** ——
 * 那种东西存成 `.apk` 以后跟正常包一样大摇大摆躺在缓存里。所以下载完必须：
 *
 * 1. 核对字节数与 Release 页公布的 `size` 一致；
 * 2. 让 `PackageManager` 真去解析这个包（HTML 在这一步会解析失败）；
 * 3. 核对包名是本应用、版本号比当前装的高。
 *
 * 三条任意一条不过就当场失败并说明原因，不把烂摊子丢给系统安装器。
 */
object ApkUpdater {

    private const val TAG = "ApkUpdater"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val BUFFER_BYTES = 64 * 1024

    /** 进度回调节流：每 120 ms 最多发一次，否则 45 MB 会往 Compose 里灌上千次重组。 */
    private const val PROGRESS_INTERVAL_MS = 120L

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** 下载目录：`cacheDir/updates/`，与 FileProvider 的 `<cache-path name="updates">` 对应。 */
    private const val UPDATE_DIR = "updates"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    /**
     * 下好、还没装上的那个包。
     *
     * 单独存一份是为了"授权回来接着装"：用户被弹去系统设置允许安装未知应用，回来时
     * 文件必须还在 —— 让他为了一个权限重新下 45 MB 是不能接受的。
     */
    @Volatile
    private var pendingFile: File? = null

    /** 正在等的那个版本号，只用于界面文案（"安装 0.9.1"）。 */
    @Volatile
    private var pendingVersion: String = ""

    private var downloadJob: Job? = null

    // ---------------------------------------------------------------------
    // 下载
    // ---------------------------------------------------------------------

    /**
     * 开始下载 [release] 的 APK。[release.apk] 为空时什么都不做（界面根本不会给按钮）。
     *
     * 全程不抛异常：所有失败都变成 [InstallState.Failed]，因为调用方是 Compose 的
     * 点击回调，那里没有地方接异常。
     */
    fun download(context: Context, release: ReleaseInfo) {
        val asset = release.apk ?: return
        if (downloadJob?.isActive == true) return

        val app = context.applicationContext
        _state.value = InstallState.Downloading(received = 0, total = asset.bytes)

        downloadJob = scope.launch {
            var target: File? = null
            try {
                target = prepareTarget(app, asset)
                val received = fetch(asset, target)

                _state.value = InstallState.Verifying
                verify(app, target, asset, received)

                pendingFile = target
                pendingVersion = release.version
                _state.value = InstallState.Ready(version = release.version)
                // "一键"的真正含义在这里：下完自动弹系统安装器。用户点一次，
                // 之后只面对系统自己的确认框。
                //
                // 必须切回主线程再起 Activity：Android 10 起对后台启动界面有限制，
                // 从 IO 线程起 Activity 在部分 ROM 上会被静默丢弃。
                withContext(Dispatchers.Main) { installPending(app) }
            } catch (cancel: CancellationException) {
                target?.delete()
                _state.value = InstallState.Idle
                throw cancel
            } catch (error: Throwable) {
                Log.w(TAG, "update download failed", error)
                target?.delete()
                pendingFile = null
                _state.value = InstallState.Failed(describe(error))
            }
        }
    }

    /** 用户点"取消"：停协程、删半个包、回到初始态。 */
    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
        pendingFile?.delete()
        pendingFile = null
        _state.value = InstallState.Idle
    }

    /**
     * 把已经下好的包交给系统安装器。
     *
     * 没有"安装未知应用"权限时**先不起安装器**：那种情况系统会直接弹一个"无法安装"，
     * 用户唯一的出路是自己去设置里翻三层菜单。这里改成先给出权限状态，界面显示
     * "去授权"，回来再自动接着装。
     */
    fun installPending(context: Context) {
        val file = pendingFile
        if (file == null || !file.exists()) {
            pendingFile = null
            _state.value = InstallState.Idle
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = InstallState.NeedPermission
            return
        }

        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file,
            )
        } catch (error: IllegalArgumentException) {
            // FileProvider 的 paths 里没配 cache/updates 就会到这里 —— 属于打包配置错误，
            // 但用户看到的必须是一句人话，而不是一个崩溃。
            Log.e(TAG, "update apk is outside provider paths", error)
            _state.value = InstallState.Failed("安装包不在可共享目录内")
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 不用 resolveActivity 先问一遍：Android 11 的包可见性会让它返回 null，
        // 而 startActivity 本身并不需要可见性。直接起，失败再说。
        val launched = runCatching { context.startActivity(intent) }
        if (launched.isSuccess) {
            _state.value = InstallState.Ready(pendingVersion)
        } else {
            // 极简 ROM / 阉割系统：没有 PackageInstaller 的 ACTION_VIEW。
            Log.e(TAG, "no installer activity", launched.exceptionOrNull())
            _state.value = InstallState.Failed("系统里没有找到安装器")
        }
    }

    /** 打开"允许安装未知应用"的系统页面。 */
    fun requestInstallPermission(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val withPackage = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(withPackage) }
            .recoverCatching { context.startActivity(fallback) }
            .isSuccess
        if (!opened) {
            _state.value = InstallState.Failed("打不开权限设置，请在系统设置里手动允许安装")
        } else {
            _state.value = InstallState.NeedPermission
        }
    }

    // ---------------------------------------------------------------------
    // 内部步骤
    // ---------------------------------------------------------------------

    private fun prepareTarget(app: Context, asset: ApkAsset): File {
        val dir = File(app.cacheDir, UPDATE_DIR)
        // 上一版留下的残包先清掉：45 MB 挂在缓存里没人会去想它，但它占着空间。
        dir.listFiles()?.forEach { it.delete() }
        if (!dir.exists() && !dir.mkdirs()) throw IOException("创建下载目录失败")

        // 空间检查要在写第一个字节之前做，否则下到 90% 才报 ENOSPC，
        // 用户白等一分钟。留 1.5 倍余量给安装器解包。
        val need = if (asset.bytes > 0) asset.bytes * 3 / 2 else 0L
        if (need > 0 && dir.usableSpace < need) {
            throw IOException("存储空间不足，需要 ${formatBytes(need)}")
        }

        val name = asset.name.substringAfterLast('/').ifBlank { "update.apk" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, name)
    }

    /**
     * 拉取并写入。返回实际写入的字节数（用来和 Release 公布的 `size` 对账）。
     *
     * `browser_download_url` 会 302 到 `objects.githubusercontent.com` 的签名地址，
     * `HttpURLConnection` 默认跟随同协议重定向，所以不需要手动处理 Location。
     */
    private fun fetch(asset: ApkAsset, target: File): Long {
        val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "${UpdateChecker.REPO_NAME}/${BuildConfig.VERSION_NAME}")
            // 不要 gzip：进度条的分母必须是**字节数**，压缩后的长度对不上文件大小。
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException(
                    when (code) {
                        403, 429 -> "GitHub 限流了，过一会儿再试"
                        404 -> "这个版本的安装包已经不在了"
                        in 500..599 -> "GitHub 暂时不可用（HTTP $code）"
                        else -> "下载失败（HTTP $code）"
                    },
                )
            }

            var written = 0L
            var lastReport = 0L
            // 走 atom 通道时 [ApkAsset.bytes] 是 0（feed 不含体积），这里用响应头补上，
            // 否则进度条永远只有"已下载 12.4 MB"而没有百分比。补不到就维持未知。
            val total = asset.bytes.takeIf { it > 0 } ?: connection.contentLengthLong
            val buffer = ByteArray(BUFFER_BYTES)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            _state.value = InstallState.Downloading(written, total)
                        }
                    }
                    output.flush()
                }
            }
            if (written == 0L) throw IOException("下载到的文件是空的")
            return written
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 三道校验，见类注释。
     *
     * 用 `PackageManager` 解析本地包不需要任何权限，也不联网 —— 它只是把 zip 里的
     * `AndroidManifest.xml` 读出来。
     */
    @Suppress("DEPRECATION")
    private fun verify(app: Context, file: File, asset: ApkAsset, received: Long) {
        if (asset.bytes > 0 && received != asset.bytes) {
            throw IOException("下载不完整（${formatBytes(received)} / ${formatBytes(asset.bytes)}）")
        }

        val archive = app.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("下载到的文件不是安装包，可能被网络代理拦截了")

        if (archive.packageName != app.packageName) {
            throw IOException("下载到的包不属于本应用（${archive.packageName}）")
        }

        val installed = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
        }.getOrDefault(0L)
        if (archive.longVersionCode <= installed) {
            throw IOException("下载到的版本号并不比当前安装的高")
        }
    }

    private fun describe(error: Throwable): String = when (error) {
        is IOException -> error.message ?: "下载失败"
        // 断网时 `HttpURLConnection` 抛的是 UnknownHostException 之类的运行时异常，
        // 文案要引导到"能做什么"上，而不是复述类名。
        else -> "网络中断，请检查连接后重试"
    }
}

/** 更新流程的界面状态。和 [UpdateCheck] 无关：那个是"有没有新版"，这个是"装到哪一步了"。 */
sealed interface InstallState {

    data object Idle : InstallState

    /**
     * [total] 可能为 0：Release 里没填 size，或者服务器没给 Content-Length。
     * 这时界面只显示已下载的字节数，不画百分比 —— 画一个永远 0% 的进度条更糟。
     */
    data class Downloading(val received: Long, val total: Long) : InstallState {
        val fraction: Float
            get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
        val known: Boolean get() = total > 0
    }

    data object Verifying : InstallState
    data object NeedPermission : InstallState
    data class Ready(val version: String) : InstallState
    data class Failed(val message: String) : InstallState
}

/** `45.8 MB` / `820 KB`。给界面用，也单测它（除零、负值、超大都要有确定答案）。 */
internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "未知大小"
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}
