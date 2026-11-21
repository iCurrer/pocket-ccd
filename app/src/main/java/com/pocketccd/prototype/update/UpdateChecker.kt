package com.pocketccd.prototype.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Release 里挂的一个 APK 附件。
 *
 * [bytes] 来自 GitHub 的 `size` 字段，下载时拿它做两件事：进度条的百分比分母、
 * 以及**校对实际收到的字节数**（截断的包必须在校验阶段就拒掉，否则用户会看到
 * 系统安装器报"解析软件包时出现问题"，而那个提示根本指不出是下载坏了）。
 *
 * [bytes] 允许为 0：走 atom 通道时我们只拿到 tag，没拿到附件体积。下载端会退化成
 * "读 Content-Length"，读不到就只显示已下载字节数，不做字节数对账。
 */
data class ApkAsset(
    val name: String,
    val url: String,
    val bytes: Long,
)

/**
 * GitHub Release 里的一条发布。
 *
 * [tag] 保留原始写法（`v0.9.1`），[version] 是去掉前缀后的可比较版本（`0.9.1`）。
 * 两者都带进界面：tag 是仓库里的真名，version 是拿来比大小的 —— 只存一个就会在
 * 某个地方被迫做字符串裁剪，而裁剪点散落在界面里迟早出错。
 *
 * [apk] 为 null 不是错误：Release 可以只发说明不挂包。那种情况下界面退回
 * "打开 Release 页"，而不是给一颗点了没反应的下载按钮。
 */
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val htmlUrl: String,
    val publishedAt: String? = null,
    val apk: ApkAsset? = null,
)

/** 一次检查的结局。没有"检查中"——那是界面状态，不是结果。 */
sealed interface UpdateCheck {
    data class UpToDate(val currentVersion: String) : UpdateCheck

    data class Available(val currentVersion: String, val release: ReleaseInfo) : UpdateCheck

    /**
     * 仓库里一条 Release 都没有（`/releases` 返回空数组）。
     *
     * 这**不是失败**：请求成功了，答案是"没有发布版本"。区分出来是因为界面要给中性文案，
     * 而不是把"还没发过版"染成红色错误 —— 项目刚开源时这一屏就会是这个状态。
     */
    data object NoRelease : UpdateCheck

    /**
     * GitHub API 的额度用尽了（403/429）。
     *
     * 这**不能**用"过一会儿再试"打发：未认证额度是 60 次/**小时**/IP，而手机在
     * 运营商 CGNAT、校园网、公司网络下与成百上千人共用同一个出口 IP —— 别人用光了，
     * 你就是 0 次，等到下一个整点也未必回来。所以带上服务端给的恢复时刻
     * [retryAtEpochMs]，界面显示确切倒计时，而不是让用户对着"稍后重试"反复空点。
     */
    data class Limited(val retryAtEpochMs: Long) : UpdateCheck

    data class Failed(val message: String) : UpdateCheck
}

/**
 * **检查更新：读 GitHub 仓库的 Release，两条通道。**
 *
 * 为什么不做自己的更新服务器：这个应用只有一个分发渠道（GitHub Releases），
 * 再架一层就是把"发布"这件事拆成两处，两处都会忘记同步。GitHub 的 Release 就是
 * 权威答案 —— 下面两条通道读的都是同一份数据，只是走的门不同。
 *
 * ## 通道一：`releases.atom`（默认，**不占 API 配额**）
 *
 * `https://github.com/{owner}/{repo}/releases.atom` 是 GitHub 给阅读器用的订阅源，
 * 走的是网页域名，**不计入 API 的 60 次/小时**。它给出 tag、发布时间、Release 页地址，
 * 但不给附件列表。
 *
 * 这正是我们要的东西：**判断"有没有新版"是高频动作，认附件是低频动作**。
 * 日常检查只读 feed，零配额；只有在 feed 说"确实有新版"时，才花 1 次配额去
 * `/releases/tags/{tag}` 补附件地址。补不到（限流/超时）也不影响结论 ——
 * 下载地址可以由 `releases/download/{tag}/app-release.apk` 拼出来，
 * 因为附件名由我们自己的发版脚本固定（见 [APK_ASSET_NAME]），而且**下载本身
 * 走 objects.githubusercontent.com，不吃 API 配额**。所以哪怕额度被榨干，
 * "检查 + 下载 + 安装"整条链路照样能用。
 *
 * ## 通道二：`/releases?per_page=1`（兜底）
 *
 * atom 在某些网络下会被中间设备拦掉。那时退到 API 列表端点。注意这里**只发 1 个
 * 请求**：旧实现先问 `/releases/latest`（只返正式版，内测阶段必然 404），404 后再问
 * 列表 —— 每次检查固定花 2 次配额，30 次点击就把一小时的额度打光。列表端点一次
 * 就能拿到含 pre-release 的最新一条，已经覆盖了 `latest` 的全部用途。
 *
 * ## 两条通道都失败时
 *
 * 把服务端给的 `X-RateLimit-Reset` / `Retry-After` 带回去，界面显示"还有 N 分钟"，
 * 并在 [UpdateCache] 里锁一段时间，避免用户继续发注定失败的请求。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    const val REPO_OWNER = "iCurrer"
    const val REPO_NAME = "pocket-ccd"

    /**
     * 发版脚本（`build/gh_release.py`）上传附件时用的固定名字。
     *
     * 这是 atom 通道能在"没有 API 配额"时仍给出可下载地址的前提。**改发版脚本里
     * 的附件名必须同步改这里**，否则拼出来的地址会 404。
     */
    internal const val APK_ASSET_NAME = "app-release.apk"

    /**
     * 仓库的 Release 列表页。
     *
     * 被限流时这是**唯一的出路**：浏览网页不走 API 配额，用户点两下就能自己看到
     * 有没有新版、手动下载。所以在限流状态里给一个直达链接，而不是让他干等倒计时。
     */
    const val RELEASES_PAGE = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

    private const val FEED_URL =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases.atom"
    private const val LIST_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=1"

    private fun tagUrl(tag: String) =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/tags/$tag"

    private fun downloadUrl(tag: String, assetName: String) =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/" +
            "${tag.replace("+", "%2B")}/${assetName.replace("+", "%2B")}"

    /**
     * 超时压得比较紧：这是用户主动点的一个按钮，几秒没回音就该告诉他"失败了"，
     * 而不是让他在设置页盯着一个转圈的图标。
     *
     * feed 的两次超时更短 —— 它只是"抄近路"，走不通就该尽快退到 API，
     * 不能让用户为一条备用通道多等两个 6 秒。
     */
    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 6_000
    private const val FEED_TIMEOUT_MS = 4_000

    /** 服务端没给恢复时刻时（头缺失／明显是坏数据）的保守等待。 */
    private const val LIMIT_FALLBACK_MS = 10 * 60_000L

    suspend fun check(currentVersion: String, cache: UpdateCache? = null): UpdateCheck =
        withContext(Dispatchers.IO) {
            // 1. 刚查过就用缓存：连点两下不再发请求（JSON 与 XML 两种结果都有效）。
            cache?.freshOutcome(currentVersion)?.let { return@withContext it }

            // 2. 主通道：atom。**这条路径不受 API 限流影响**，所以它排在限流锁前面 ——
            //    额度被别人耗光的时候，feed 往往还是通的，没有理由一起锁掉。
            if (cache?.feedIsDown() != true) {
                try {
                    return@withContext checkViaFeed(currentVersion, cache)
                } catch (error: Throwable) {
                    Log.w(TAG, "atom feed unavailable, falling back to api", error)
                    // 十分钟内不再试 feed：否则每次检查都要先白等 4 秒才落到 API。
                    cache?.rememberFeedDown()
                }
            }

            // 3. 落到 API 之前才看限流锁：这时点一次就是一次注定 403 的请求。
            val lockedUntil = cache?.limitUntil() ?: 0L
            if (lockedUntil > System.currentTimeMillis()) {
                return@withContext UpdateCheck.Limited(lockedUntil)
            }

            try {
                checkViaApi(currentVersion, cache)
            } catch (error: Throwable) {
                // 断网、DNS 失败、TLS 失败都到这里。**不区分**：用户能做的动作都是同一个
                // （稍后重试），分成五条不同的错误文案只会让人以为应用出了不同的毛病。
                Log.w(TAG, "check failed for $currentVersion", error)
                UpdateCheck.Failed(error.message ?: error.javaClass.simpleName)
            }
        }

    // ---------------------------------------------------------------------
    // 通道一：atom
    // ---------------------------------------------------------------------

    private fun checkViaFeed(currentVersion: String, cache: UpdateCache?): UpdateCheck {
        val response = get(FEED_URL, currentVersion, FEED_TIMEOUT_MS, FEED_TIMEOUT_MS)
        if (response.code !in 200..299) throw IOException("feed HTTP ${response.code}")
        val newest = parseAtom(response.body ?: "")
            ?: throw IOException("feed 里没有可解析的发布条目")

        if (compareVersions(newest.version, currentVersion) <= 0) {
            cache?.remember(newest)
            return UpdateCheck.UpToDate(currentVersion)
        }

        // 只在"确实有新版"时补一次附件信息 —— 这一发一天也用不到几次。
        val complete = enrichAssets(newest, currentVersion)
        cache?.remember(complete)
        return UpdateCheck.Available(currentVersion, complete)
    }

    /**
     * 用 `/releases/tags/{tag}` 把 feed 缺的附件列表补上。
     *
     * 补不到就原样返回：feed 已经给出了 tag 与页面地址，[parseAtom] 也按固定附件名
     * 拼好了下载地址。让"有新版"这个结论成立，比凑齐所有字段重要。
     */
    private fun enrichAssets(release: ReleaseInfo, currentVersion: String): ReleaseInfo = try {
        val response = get(tagUrl(release.tag), currentVersion, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS)
        if (response.code in 200..299) {
            fromJson(JSONObject(response.body ?: "")) ?: release
        } else {
            Log.w(TAG, "tags endpoint HTTP ${response.code}; keeping synthesized asset url")
            release
        }
    } catch (error: Throwable) {
        Log.w(TAG, "tags endpoint failed; keeping synthesized asset url", error)
        release
    }

    /**
     * 解析 `releases.atom`，取**第一条** entry。
     *
     * 结构（每条 entry）：
     * ```xml
     * <entry>
     *   <id>tag:github.com,2008:Repository/1424510124/v0.9.0-beta3</id>
     *   <updated>2026-11-11T08:46:30Z</updated>
     *   <link rel="alternate" type="text/html"
     *         href="https://github.com/iCurrer/pocket-ccd/releases/tag/v0.9.0-beta3"/>
     *   <title>v0.9.0-beta3</title>
     * </entry>
     * ```
     *
     * tag 从 `<id>` 尾部取而不是从 `<title>` 取：`<title>` 是 Release 的**标题**，
     * 发版时改成人话（"内测版 beta3"）就取不出版本号了；`<id>` 是 GitHub 自己生成
     * 的稳定标识，只可能是 `.../{repoId}/{tag}`。feed 自身也有一个 `<id>`
     * （以 `/releases` 结尾），所以只在 entry 内部读。
     *
     * 只解第一个 entry：feed 按发布时间倒序，第一条就是最新，后面的用不上。
     */
    internal fun parseAtom(body: String): ReleaseInfo? {
        val parser = try {
            XmlPullParserFactory.newInstance()
                .apply { isNamespaceAware = false }
                .newPullParser()
                .apply { setInput(StringReader(body)) }
        } catch (error: Exception) {
            Log.w(TAG, "atom feed is not parseable", error)
            return null
        }

        var event = try {
            parser.eventType
        } catch (error: Exception) {
            return null
        }

        var inEntry = false
        var tag: String? = null
        var html: String? = null
        var updated: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        tag = null
                        html = null
                        updated = null
                    }

                    "id" -> if (inEntry) {
                        val raw = parser.nextText().trim()
                        tag = raw.substringAfterLast('/').takeIf { it.isNotBlank() }
                    }

                    "updated" -> if (inEntry) updated = parser.nextText().trim()

                    "link" -> if (inEntry) {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href")
                        // rel 缺省时也当 alternate：atom 里 entry 的 link 就是它。
                        if ((rel == null || rel == "alternate") && !href.isNullOrBlank()) {
                            html = href
                        }
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == "entry") {
                    val found = tag
                    if (found != null) {
                        return ReleaseInfo(
                            tag = found,
                            version = found.trim().removePrefix("v").removePrefix("V"),
                            htmlUrl = html
                                ?: "https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$found",
                            publishedAt = updated,
                            apk = ApkAsset(
                                name = APK_ASSET_NAME,
                                url = downloadUrl(found, APK_ASSET_NAME),
                                // feed 不含体积。下载端会退化成读 Content-Length。
                                bytes = 0L,
                            ),
                        )
                    }
                    inEntry = false
                }
            }

            event = try {
                parser.next()
            } catch (error: Exception) {
                Log.w(TAG, "atom feed truncated", error)
                return null
            }
        }
        return null
    }

    // ---------------------------------------------------------------------
    // 通道二：REST 列表
    // ---------------------------------------------------------------------

    private fun checkViaApi(currentVersion: String, cache: UpdateCache?): UpdateCheck {
        val response = get(LIST_URL, currentVersion, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS)

        if (response.code == 403 || response.code == 429) {
            val retryAt = response.retryAt()
            cache?.rememberLimit(retryAt)
            return UpdateCheck.Limited(retryAt)
        }
        if (response.code !in 200..299) return UpdateCheck.Failed(httpMessage(response.code))

        // 空数组是"这个仓库还没发过版"，不是解析失败。
        if (response.body?.trim() == "[]") return UpdateCheck.NoRelease

        val newest = parseNewestOfList(response)
            ?: return UpdateCheck.Failed("GitHub 返回的内容无法解析")

        if (compareVersions(newest.version, currentVersion) <= 0) {
            cache?.remember(newest)
            return UpdateCheck.UpToDate(currentVersion)
        }
        cache?.remember(newest)
        return UpdateCheck.Available(currentVersion, newest)
    }

    // ---------------------------------------------------------------------
    // 纯函数部分（单测直接打这里，不发网络请求）
    // ---------------------------------------------------------------------

    /**
     * 版本比较：[a] 大于 [b] 返回正数。
     *
     * 规则按 semver 的常用子集：
     * - 先比数字段（`0.9` 与 `0.9.0` 相等，缺的段补 0）；
     * - 数字相同时**带预发布标记的更小**（`0.9.0-beta1 < 0.9.0`）；
     * - 预发布标记逐段比，数字段按数值比（`beta2 < beta10`，字符串比会得出反的结论）。
     *
     * 不追求完整 semver（不处理 `+build`、不校验合法性）：仓库里的 tag 由我们自己打，
     * 这里要的是"不会把 0.10 判成小于 0.9"这种致命错误。
     */
    fun compareVersions(a: String, b: String): Int {
        val left = splitVersion(a)
        val right = splitVersion(b)
        for (i in 0 until maxOf(left.core.size, right.core.size)) {
            val x = left.core.getOrElse(i) { 0 }
            val y = right.core.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        // 核心版本相同：没有预发布标记的那个更新。
        if (left.pre.isEmpty() && right.pre.isNotEmpty()) return 1
        if (left.pre.isNotEmpty() && right.pre.isEmpty()) return -1
        for (i in 0 until maxOf(left.pre.size, right.pre.size)) {
            val x = left.pre.getOrNull(i) ?: return -1
            val y = right.pre.getOrNull(i) ?: return 1
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val cmp = if (xn != null && yn != null) xn.compareTo(yn) else x.compareTo(y)
            if (cmp != 0) return cmp
        }
        return 0
    }

    /** `v0.9.1` / `0.9.1-beta2` → 核心数字段 + 预发布段。 */
    private fun splitVersion(raw: String): ParsedVersion {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        val dash = trimmed.indexOf('-')
        val core = (if (dash >= 0) trimmed.substring(0, dash) else trimmed)
        val pre = if (dash >= 0) trimmed.substring(dash + 1) else ""
        return ParsedVersion(
            core = core.split('.').map { it.trim().takeWhile(Char::isDigit).toIntOrNull() ?: 0 },
            pre = if (pre.isBlank()) emptyList() else pre.split('.', '-', '_', '+')
                .filter { it.isNotBlank() }
                // "beta1" 还要再拆成 "beta" + 1，否则 beta10 会被判成小于 beta2。
                .flatMap { token ->
                    Regex("\\d+|\\D+").findAll(token).map { it.value }.toList()
                },
        )
    }

    private class ParsedVersion(val core: List<Int>, val pre: List<String>)

    /** 解析 `/releases/latest` 或 `/releases/tags/{tag}` 的响应体。缺 tag 或 html_url 一律当失败，不猜。 */
    internal fun parseRelease(body: String): ReleaseInfo? = try {
        fromJson(JSONObject(body))
    } catch (error: JSONException) {
        Log.w(TAG, "release body is not JSON", error)
        null
    }

    /** 解析 `/releases?per_page=1` 的数组响应，取第一条；空数组返回 null。 */
    internal fun parseNewestOfList(result: HttpResult): ReleaseInfo? {
        val body = result.body ?: return null
        if (result.code !in 200..299) return null
        return try {
            val array = JSONArray(body)
            if (array.length() == 0) null else fromJson(array.getJSONObject(0))
        } catch (error: JSONException) {
            Log.w(TAG, "release list is not JSON", error)
            null
        }
    }

    private fun fromJson(json: JSONObject): ReleaseInfo? {
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val html = json.optString("html_url").takeIf { it.isNotBlank() } ?: return null
        return ReleaseInfo(
            tag = tag,
            version = tag.trim().removePrefix("v").removePrefix("V"),
            htmlUrl = html,
            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() },
            apk = pickApk(parseAssets(json.optJSONArray("assets"))),
        )
    }

    /**
     * 把一个 Release 的附件数组解析出来。
     *
     * 缺字段的条目直接丢掉（`browser_download_url` 是下载的唯一入口，没有它这条
     * 附件对我们没有任何用处），而不是用 `optString` 的默认空串去拼一个坏 URL ——
     * 坏 URL 会在点击后变成"下载失败"，用户会以为是网络问题。
     */
    private fun parseAssets(array: JSONArray?): List<ApkAsset> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("browser_download_url").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val name = item.optString("name").takeIf { it.isNotBlank() }
                ?: url.substringAfterLast('/')
            ApkAsset(name = name, url = url, bytes = item.optLong("size", 0L))
        }
    }

    /**
     * 从附件里挑出要装的那一个。
     *
     * 规则按"最不可能装错"排：
     * 1. 只要 `.apk`（`.aab` 装不上，`.txt`/`.zip` 更不行）；
     * 2. 排除名字里带 `debug` / `unsigned` 的 —— 一个 Release 里同时挂两个包时，
     *    先挂上去的往往是调试包，按顺序取第一个正好会取错；
     * 3. 剩下的按名字排序取第一个，保证**同一份 Release 每次挑到同一个包**。
     *    不排序的话顺序取决于 GitHub 返回的数组，而那个顺序没有承诺。
     */
    internal fun pickApk(assets: List<ApkAsset>): ApkAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val preferred = apks.filter { asset ->
            val name = asset.name.lowercase()
            "debug" !in name && "unsigned" !in name
        }
        return (preferred.ifEmpty { apks }).minByOrNull { it.name }
    }

    private fun httpMessage(code: Int): String = when (code) {
        404 -> "仓库还没有发布任何版本"
        in 500..599 -> "GitHub 暂时不可用（HTTP $code）"
        else -> "GitHub 返回 HTTP $code"
    }

    // ---------------------------------------------------------------------
    // 网络
    // ---------------------------------------------------------------------

    internal data class HttpResult(
        val code: Int,
        val body: String?,
        /** `X-RateLimit-Reset`，**epoch 秒**（GitHub 的原样单位）。0 = 没有这个头。 */
        val rateLimitResetAtEpochSeconds: Long = 0L,
        /** `Retry-After`，秒。0 = 没有这个头。 */
        val retryAfterSeconds: Long = 0L,
    ) {
        /**
         * 何时可以再试（epoch 毫秒）。
         *
         * 两个头都看：GitHub 限流给 `X-RateLimit-Reset`，少数中间层/网关限流给
         * `Retry-After`，取更晚的那个。头缺失或明显是坏数据（已过去、超过一小时）
         * 时退回 [LIMIT_FALLBACK_MS] —— 未认证窗口就是一小时，超过它的恢复时刻
         * 一定是错的，照着它显示会出现"还有 7 天"这种荒谬倒计时。
         */
        fun retryAt(now: Long = System.currentTimeMillis()): Long {
            val fromReset = if (rateLimitResetAtEpochSeconds > 0) {
                rateLimitResetAtEpochSeconds * 1000L
            } else {
                0L
            }
            val fromRetryAfter = if (retryAfterSeconds > 0) now + retryAfterSeconds * 1000L else 0L
            val candidate = maxOf(fromReset, fromRetryAfter)
            return if (candidate in (now + 5_000L)..(now + 3_600_000L)) {
                candidate
            } else {
                now + LIMIT_FALLBACK_MS
            }
        }
    }

    private fun get(
        url: String,
        currentVersion: String,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
    ): HttpResult {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub 强制要求 User-Agent，缺了直接 403。
            setRequestProperty("User-Agent", "$REPO_NAME/$currentVersion")
            instanceFollowRedirects = true
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResult(
                code = code,
                body = stream?.bufferedReader()?.use { it.readText() },
                rateLimitResetAtEpochSeconds =
                    connection.getHeaderField("X-RateLimit-Reset")?.trim()?.toLongOrNull() ?: 0L,
                retryAfterSeconds =
                    connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull() ?: 0L,
            )
        } finally {
            connection.disconnect()
        }
    }
}
