package com.pocketccd.prototype.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pocketccd.prototype.R
import com.pocketccd.prototype.camera.ImprintSettings
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconButton
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.glass.GlassSurface
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.consolePanel
import com.pocketccd.prototype.ui.theme.glass
import com.pocketccd.prototype.update.ApkAsset
import com.pocketccd.prototype.update.ApkUpdater
import com.pocketccd.prototype.update.InstallState
import com.pocketccd.prototype.update.ReleaseInfo
import com.pocketccd.prototype.update.UpdateCache
import com.pocketccd.prototype.update.UpdateCheck
import com.pocketccd.prototype.update.UpdateChecker
import com.pocketccd.prototype.update.formatBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 设置页（D-091 重排）。
 *
 * ## 为什么重排
 *
 * 上一版是"四组 + 每行之间一条分隔线 + 组标题后面再接一条分隔线"：一屏里 12 条横线，
 * 线又和卡片的描边同色同粗，读者分不清哪条是"分组"、哪条是"两行之间"。结果是**没有主次** ——
 * 页面上找不出第一个该看的东西。
 *
 * 这一版只做三件事：
 *
 * 1. **线全部去掉**。分组靠"标题 + 间距"，行与行靠留白，不再用规则线切。
 * 2. **每页只有一个视觉重点**：版本号。它是这一页唯一的大字（`numericLarge`），
 *    "检查更新"紧贴在它右边 —— 用户点进设置，八成就是为了看一眼版本、看看有没有新版。
 * 3. **分组标题降为标记**：一条 3×12dp 的黄铜短线 + 11sp 灰字。它看起来像丝印，
 *    不像标题，不会和正文抢注意力。
 *
 * 数据全部真实：胶卷数、成片数、版本号都来自运行期，没有任何"待接入"的假入口。
 */
@Composable
fun SettingsScreen(
    stockCount: Int,
    photoCount: Int,
    versionName: String,
    modifier: Modifier = Modifier,
    onRefreshStocks: () -> Unit = {},
    /** 片边 / 日期后背的当前状态（D-081）。 */
    imprint: ImprintSettings = ImprintSettings(),
    onToggleFilmEdge: () -> Unit = {},
    onToggleDateBack: () -> Unit = {},
    /**
     * 诊断入口：跑一遍引擎自测并显示样张。**只有 debug / benchmark 包才传**（D-075）——
     * 发布包里这一行为 null，界面上不会出现"开发者工具"。
     */
    onRunSelfTest: (() -> Unit)? = null,
    /**
     * 内测凭证的一行摘要（`2026-11-11 激活`），null = 这版没有闸门（debug 包直接放行）。
     * 只展示，不提供"退出激活"—— 内测用户没有理由自己把相机锁上。
     */
    betaActivation: String? = null,
) {
    val p = MaterialTheme.glass
    var licensesOpen by remember { mutableStateOf(false) }

    if (licensesOpen) {
        LicensesScreen(onBack = { licensesOpen = false })
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update = remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    // 下载状态放在单例里（见 ApkUpdater 的注释）：离开设置页再回来，进度不会归零。
    val install by ApkUpdater.state.collectAsState()

    // 从"允许安装未知应用"的系统设置回来时接着装。只在这一种状态下自动接力：
    // 其他状态（Ready）如果也自动装，用户取消过安装、回到应用就会被反复弹框。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (ApkUpdater.state.value == InstallState.NeedPermission &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            ApkUpdater.installPending(context)
        }
    }

    // 检查只由用户点按触发：没有启动自检、没有轮询。
    //
    // 但"点一下 = 一次请求"这个等式不成立：GitHub 未认证接口按**出口 IP** 给 60 次/小时，
    // 手机在运营商 CGNAT 或校园网下与整栋楼共用这个额度。所以结论缓存在
    // [UpdateCache] 里（两分钟），连点不再发请求；被限流期间也不再发，直接回倒计时。
    val updateCache = remember(context) { UpdateCache(context) }
    val check: () -> Unit = {
        if (update.value != UpdateUi.Checking) {
            update.value = UpdateUi.Checking
            scope.launch {
                update.value = when (val result = UpdateChecker.check(versionName, updateCache)) {
                    is UpdateCheck.UpToDate -> UpdateUi.Latest
                    is UpdateCheck.Available -> UpdateUi.Available(result.release)

                    is UpdateCheck.NoRelease -> UpdateUi.NoRelease
                    is UpdateCheck.Limited -> UpdateUi.Limited(result.retryAtEpochMs)
                    is UpdateCheck.Failed -> UpdateUi.Failed(result.message)
                }
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = GlassSpace.page, end = GlassSpace.page, top = 14.dp),
        ) {
            Text(text = "设置", style = GlassType.title, color = p.onGlass)
            Spacer(Modifier.height(3.dp))
            // 副标题写**真实数据**而不是"本机数据与声明"：一句话交代这台机器上有什么，
            // 比一句什么也没说的分类名有用。
            Text(
                text = "$stockCount 支胶卷 · $photoCount 张成片",
                style = GlassType.caption,
                color = p.onGlassMuted,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassSpace.page),
        ) {
            // ---- 01 成像 ----
            SectionLabel(title = "成像")
            SettingsCard {
                ToggleRow(
                    title = "片边",
                    subtitle = "成片合成到 135 半格胶片条上，不裁切画面",
                    checked = imprint.filmEdge,
                    onToggle = onToggleFilmEdge,
                )
                RowGap()
                ToggleRow(
                    title = "日期后背",
                    subtitle = "拍摄日期印在画面右下角，只影响新拍的照片",
                    checked = imprint.dateBack,
                    onToggle = onToggleDateBack,
                )
                RowGap()
                InfoRow(title = "片边画幅", value = "半格 135 · 24×18")
            }

            // ---- 02 胶卷 ----
            SectionLabel(title = "胶卷")
            SettingsCard {
                LinkRow(
                    title = "重新扫描胶卷",
                    value = "已安装 $stockCount 支",
                    onClick = onRefreshStocks,
                )
                RowGap()
                StackedRow(title = "胶卷位置", value = "Android/data/…/files/film/")
            }

            // ---- 03 关于 ----
            SectionLabel(title = "关于")
            SettingsCard {
                SettingsVersionBlock(
                    versionName = versionName,
                    update = update.value,
                    install = install,
                    onCheck = check,
                    onOpenRelease = { url -> openInBrowser(context, url) },
                    onDownload = {
                        val current = update.value
                        if (current is UpdateUi.Available) ApkUpdater.download(context, current.release)
                    },
                    onCancelDownload = { ApkUpdater.cancel() },
                    onInstall = { ApkUpdater.installPending(context) },
                    onRequestPermission = { ApkUpdater.requestInstallPermission(context) },
                )
                RowGap()
                InfoRow(title = "本机照片", value = "$photoCount 张")
                if (betaActivation != null) {
                    RowGap()
                    InfoRow(title = "内测凭证", value = betaActivation)
                }
                RowGap()
                LinkRow(
                    title = "开源与素材声明",
                    value = stringResource(R.string.settings_licenses_value),
                    onClick = { licensesOpen = true },
                )
            }

            if (onRunSelfTest != null) {
                SectionLabel(title = "诊断")
                SettingsCard {
                    LinkRow(
                        title = "运行成像自测",
                        value = "引擎 · 分块 · 样张",
                        onClick = onRunSelfTest,
                    )
                    RowGap()
                    StackedRow(title = "自测做什么", value = "一致性 / 耗时 / NaN / 样张")
                }
            }

            Spacer(Modifier.height(GlassSpace.inner))
            // 说明放在页脚、且**不做卡片**：它是脚注，不是入口。做成卡片会让人以为能点。
            //
            // 这段是**对外的隐私口径**：D-091 加了"检查更新"、D-092 加了"下载并安装"，
            // 两句都得写进去。少一句就是对着一个会联网的 App 说它不联网。
            Text(
                text = "照片只写在本机（应用私有目录 + 系统相册），不会上传到任何地方。" +
                    "只有你点「检查更新」时才会访问 GitHub 读取版本号，" +
                    "点「下载并安装」时才会从 GitHub 下载安装包。",
                style = GlassType.caption,
                color = p.onGlassMuted,
            )

            Spacer(Modifier.height(GlassSpace.dockReserve))
        }
    }
}

/**
 * 分组标记：一条黄铜短线 + 一个灰字标签。
 *
 * 它替代了旧版的"01 成像 ————"：那条长横线实际上是分隔线的另一种写法，
 * 而且和卡片描边撞在一起，读者根本不会把它当成"组标题"。
 */
@Composable
private fun SectionLabel(title: String) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(p.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = title, style = GlassType.badge, color = p.onGlassMuted)
    }
}

/** 一组设置行的容器。**内部没有分隔线**，行距就是分隔。 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(
        shape = GlassShape.card,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = GlassSpace.inner, vertical = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * 行间距。
 *
 * 用它而不是分隔线：浅色面板上一条 1dp 的灰线会和卡片描边同色，视觉上等于
 * "这张卡被切成了几块"。留白同样能分组，而且不增加元素。
 */
@Composable
private fun RowGap() = Spacer(Modifier.height(2.dp))

@Composable
private fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = GlassType.body,
            color = p.onGlass,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(GlassSpace.tight))
        Text(
            text = value,
            style = GlassType.numeric,
            color = p.onGlassMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        GlassIcon(GlassIconKind.Chevron, p.onGlassMuted, size = 15.dp)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = GlassType.body,
            color = p.onGlass,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(GlassSpace.tight))
        Text(text = value, style = GlassType.numeric, color = p.onGlassMuted, maxLines = 1)
    }
}

/** 值比较长的行（路径）：值换到标题下面，右对齐会被挤成省略号。 */
@Composable
private fun StackedRow(title: String, value: String) {
    val p = MaterialTheme.glass
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = title, style = GlassType.body, color = p.onGlass)
        Spacer(Modifier.height(3.dp))
        Text(text = value, style = GlassType.numeric, color = p.onGlassMuted)
    }
}

/**
 * 带开关的一行。
 *
 * 整行可点（不只是 Switch 那一小块 32dp 的热区）：设置页里"点标题没反应"是最常见的
 * 小挫败。Switch 自己也会吃掉点击，不会重复切换。
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                interactionSource = null,
                role = Role.Switch,
                indication = null,
                onValueChange = { onToggle() },
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = GlassType.body, color = p.onGlass)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = GlassType.caption, color = p.onGlassMuted)
        }
        Spacer(Modifier.width(12.dp))
        InstrumentSwitch(checked)
    }
}

/**
 * 版本号 + 检查更新 + 下载安装（D-092）。
 *
 * 这一块是整页的视觉重点：版本号是唯一的大字，动作紧贴在它右边（不是一个孤零零的
 * "关于"列表项）。用户进设置十有八九是为了看它。
 *
 * 发现新版本后，下面接一条**更新条**：左边写版本与体积，右边一颗动作键。整块只保留
 * 一个动作 —— "下载并安装"，下载中变成"取消"，下好后变成"安装"。不做"先下载、
 * 再另找地方安装"的两段式，那种设计会让人以为下载完就装上了。
 */
@Composable
internal fun SettingsVersionBlock(
    versionName: String,
    update: UpdateUi,
    install: InstallState,
    onCheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val p = MaterialTheme.glass

    // 限流倒计时：只有这个状态才每秒重组一次，其它状态一次都不 tick。
    // 倒计时用 wall clock 现算（不递减计数）：熄屏、切后台、改系统时间都不会让它走偏。
    val limited = update as? UpdateUi.Limited
    val nowMs by produceState(System.currentTimeMillis(), limited) {
        if (limited == null) return@produceState
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remainingMs = limited?.let { (it.retryAtEpochMs - nowMs).coerceAtLeast(0L) }

    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = "版本", style = GlassType.caption, color = p.onGlassMuted)
                Spacer(Modifier.height(2.dp))
                Text(text = versionName, style = GlassType.numericLarge, color = p.onGlass)
            }
            Spacer(Modifier.width(GlassSpace.tight))
            UpdateAction(
                update = update,
                remainingMs = remainingMs,
                onCheck = onCheck,
                onOpenRelease = onOpenRelease,
            )
        }
        when (update) {
            is UpdateUi.Available -> UpdatePanel(
                release = update.release,
                install = install,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onInstall = onInstall,
                onRequestPermission = onRequestPermission,
                onOpenRelease = { onOpenRelease(update.release.htmlUrl) },
            )

            is UpdateUi.Failed -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "检查失败：${update.message}",
                    style = GlassType.caption,
                    color = p.error,
                )
            }

            is UpdateUi.Limited -> {
                Spacer(Modifier.height(8.dp))
                // 这段要说清"不是你的错"，否则用户会以为是自己点太多次点坏的。
                // 事实是：额度按**出口 IP** 计，同一 Wi-Fi / 同一运营商下的所有人共享它。
                Text(
                    text = "GitHub 的检查额度用尽了：60 次/小时，按网络出口 IP 算 —— " +
                        "同一个 Wi-Fi 或运营商下的其他人也在消耗它。",
                    style = GlassType.caption,
                    color = p.accentBright,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        remainingMs == null -> "稍后可以再试。"
                        remainingMs <= 0L -> "现在可以再试了。"
                        else -> "还有 ${countdownPhrase(remainingMs)}可以再试。"
                    },
                    style = GlassType.caption,
                    color = p.onGlassMuted,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "或者直接去 Release 页看新版，浏览网页不占这个额度。",
                        style = GlassType.caption,
                        color = p.onGlassMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(GlassSpace.tight))
                    UpdateChip(
                        text = "打开页面",
                        primary = false,
                        enabled = true,
                        onClick = { onOpenRelease(UpdateChecker.RELEASES_PAGE) },
                    )
                }
            }

            UpdateUi.NoRelease -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "仓库里还没有发布过版本",
                    style = GlassType.caption,
                    color = p.onGlassMuted,
                )
            }

            else -> Unit
        }
    }
}

/**
 * 更新条：一行版本信息 + 一颗**通栏**动作键（下载中换成进度线 + 取消）。
 *
 * 为什么动作键通栏、而不是贴在右侧当第二颗胶囊：上面那行已经有一颗"0.10.0 ›"
 * 的胶囊了，再在它下面挂第二颗同形状的胶囊，读者会以为两者是并列的两个入口。
 * 通栏键在视觉上明确是"这一步该做的事"，而且手掌大小的热区比 44dp 的胶囊好点。
 *
 * 只在这份 Release **确实挂了 APK** 时给下载键。没挂包时退回说明文字与页面入口 ——
 * 一颗点了没反应的下载键比没有按钮更糟。
 *
 * [install] 里记着"已下好的版本"，如果它和当前发现的版本不是同一个（用户查到了
 * 0.9.2，缓存里躺的是 0.9.1 的包），状态退化成初始态，避免装错版本。
 */
@Composable
private fun UpdatePanel(
    release: ReleaseInfo,
    install: InstallState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val p = MaterialTheme.glass
    val asset: ApkAsset? = release.apk
    val state = when {
        asset == null -> InstallState.Idle
        install is InstallState.Ready && install.version != release.version -> InstallState.Idle
        else -> install
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        if (asset == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${release.version}：这个发布没有挂 APK 附件",
                    style = GlassType.caption,
                    color = p.accentBright,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(GlassSpace.tight))
                UpdateChip(text = "打开页面", primary = false, enabled = true, onClick = onOpenRelease)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "在浏览器里下载后手动安装，或者等下一次带包的发布。",
                style = GlassType.caption,
                color = p.onGlassMuted,
            )
            return@Column
        }

        Text(
            text = "${release.version} · ${formatBytes(asset.bytes)}",
            style = GlassType.numeric,
            color = p.accentBright,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = statusText(state),
            style = GlassType.caption,
            color = if (state is InstallState.Failed) p.error else p.onGlassMuted,
        )
        Spacer(Modifier.height(10.dp))

        val downloading = state as? InstallState.Downloading
        if (downloading != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadTrack(
                    fraction = if (downloading.known) downloading.fraction else null,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                UpdateChip(
                    text = "取消",
                    primary = false,
                    enabled = true,
                    onClick = onCancelDownload,
                )
            }
        } else {
            val label = when (state) {
                InstallState.Verifying -> "校验中"
                is InstallState.Ready -> "安装"
                InstallState.NeedPermission -> "去授权后安装"
                is InstallState.Failed -> "重试"
                else -> "下载并安装"
            }
            val action: () -> Unit = when (state) {
                is InstallState.Ready -> onInstall
                InstallState.NeedPermission -> onRequestPermission
                InstallState.Idle, is InstallState.Failed -> onDownload
                else -> ({})
            }
            UpdateButton(
                text = label,
                enabled = state !is InstallState.Verifying,
                onClick = action,
            )
        }
    }
}

private fun statusText(state: InstallState): String = when (state) {
    InstallState.Idle -> "装好即覆盖当前版本，成片与设置都保留"
    is InstallState.Downloading ->
        if (state.known) {
            "${formatBytes(state.received)} / ${formatBytes(state.total)} · " +
                "${(state.fraction * 100).toInt()}%"
        } else {
            "已下载 ${formatBytes(state.received)}"
        }

    InstallState.Verifying -> "正在校验安装包"
    is InstallState.Ready -> "安装包已就绪，确认后即可安装"
    InstallState.NeedPermission -> "需要允许本应用安装应用"
    is InstallState.Failed -> state.message
}

/**
 * 进度线。
 *
 * [fraction] 为 null 表示总大小未知 —— 这时**不画**进度（一条永远停在 0 的线看起来
 * 像卡死），只留文字里的已下载字节数。
 */
@Composable
private fun DownloadTrack(fraction: Float?, modifier: Modifier = Modifier) {
    val p = MaterialTheme.glass
    if (fraction == null) return
    // 6dp + 一圈描边：4dp 的纯色条在暖白底上几乎看不见，而"看不出有没有在动"
    // 和"卡住了"在用户眼里是同一件事。
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(3.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(p.accent),
        )
    }
}

/** 通栏动作键：黄铜描边 + 极淡底色。整条可点，热区高度 46dp。 */
@Composable
private fun UpdateButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .consolePanel(
                color = p.accent.copy(alpha = 0.20f),
                shape = GlassShape.chip,
                elevation = 3.dp,
                borderColor = p.accent,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = text,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = GlassType.label,
            color = if (enabled) p.accentBright else p.onGlassMuted,
        )
    }
}

/** 更新条上的动作键。与 [UpdateAction] 同一套材质，只是更小、更贴边。 */
@Composable
private fun UpdateChip(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .consolePanel(
                color = if (primary) p.accent.copy(alpha = 0.20f) else p.surfaceRaised,
                shape = GlassShape.chip,
                elevation = 3.dp,
                borderColor = if (primary) p.accent else p.border,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = text,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = GlassType.label,
            color = when {
                !enabled -> p.onGlassMuted
                primary -> p.accentBright
                else -> p.onGlass
            },
        )
    }
}

/**
 * 检查更新的动作件。
 *
 * 六态共用一个 48dp 高的热区，且**右对齐锚定**：状态切换时右边缘不动，只有左边缘
 * 随文案长短伸缩。用户的拇指不会因为一次网络请求落空，整行的对齐线也不会跳。
 *
 * 限流态**不可点**：此时每一次点按都是一次注定 403 的请求，点十下只会把"还剩多久"
 * 这个信息埋进十次失败里。所以按钮直接变成倒计时牌 —— 它此刻的职责是告知，不是动作。
 */
@Composable
private fun UpdateAction(
    update: UpdateUi,
    remainingMs: Long?,
    onCheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    val p = MaterialTheme.glass
    val available = update is UpdateUi.Available
    val text = when (update) {
        UpdateUi.Idle -> "检查更新"
        UpdateUi.Checking -> "检查中"
        UpdateUi.Latest -> "已是最新"
        UpdateUi.NoRelease -> "暂无发布"
        is UpdateUi.Available -> update.release.version
        is UpdateUi.Limited ->
            if ((remainingMs ?: 1L) <= 0L) "重试" else "限流 ${countdownShort(remainingMs ?: 0L)}"

        is UpdateUi.Failed -> "重试"
    }
    // 倒计时归零就放行：否则用户盯着"0 秒"却点不动，只能切页再回来。
    val actionable = update !is UpdateUi.Checking &&
        (update !is UpdateUi.Limited || (remainingMs ?: 1L) <= 0L)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .consolePanel(
                color = if (available) p.accent.copy(alpha = 0.20f) else p.surfaceRaised,
                shape = GlassShape.chip,
                elevation = 3.dp,
                borderColor = if (available) p.accent else p.border,
            )
            .clickable(
                enabled = actionable,
                role = Role.Button,
                onClickLabel = text,
                onClick = {
                    when (update) {
                        is UpdateUi.Available -> onOpenRelease(update.release.htmlUrl)
                        is UpdateUi.Latest -> onCheck()   // 允许再点一次"再查一遍"
                        else -> onCheck()
                    }
                },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (update) {
            UpdateUi.Checking -> {
                Spinner()
                Spacer(Modifier.width(6.dp))
            }

            UpdateUi.Idle, is UpdateUi.Failed -> {
                GlassIcon(GlassIconKind.Refresh, p.onGlassMuted, size = 14.dp)
                Spacer(Modifier.width(6.dp))
            }

            UpdateUi.Latest -> {
                GlassIcon(GlassIconKind.Check, p.onGlassMuted, size = 14.dp)
                Spacer(Modifier.width(6.dp))
            }

            UpdateUi.NoRelease -> {
                GlassIcon(GlassIconKind.Info, p.onGlassMuted, size = 14.dp)
                Spacer(Modifier.width(6.dp))
            }

            is UpdateUi.Limited -> {
                GlassIcon(GlassIconKind.Info, p.onGlassMuted, size = 14.dp)
                Spacer(Modifier.width(6.dp))
            }

            is UpdateUi.Available -> {
                GlassIcon(GlassIconKind.Sparkle, p.accentBright, size = 14.dp)
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(
            text = text,
            style = if (available) GlassType.numeric else GlassType.label,
            color = when (update) {
                is UpdateUi.Available -> p.accentBright
                UpdateUi.Checking -> p.onGlassMuted
                // 限流态不可点，文字也要跟着退下去：一颗亮着的按钮配一句"点不了"最让人恼火。
                is UpdateUi.Limited -> p.onGlassMuted
                else -> p.onGlass
            },
        )
        if (available) {
            Spacer(Modifier.width(4.dp))
            GlassIcon(GlassIconKind.Chevron, p.accentBright, size = 13.dp)
        }
    }
}

/** 转圈：只在 `Checking` 态挂载，动画生命周期与状态同生共死。 */
@Composable
private fun Spinner() {
    val transition = rememberInfiniteTransition(label = "update-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "update-angle",
    )
    val p = MaterialTheme.glass
    GlassIcon(
        GlassIconKind.Refresh,
        p.onGlassMuted,
        size = 14.dp,
        modifier = Modifier.graphicsLayer { rotationZ = angle },
    )
}

@Composable
private fun InstrumentSwitch(checked: Boolean) {
    val p = MaterialTheme.glass
    val shape = RoundedCornerShape(6.dp)
    val knobOffset by animateFloatAsState(
        targetValue = if (checked) 20f else 0f,
        animationSpec = tween(150),
        label = "instrument-switch",
    )
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(shape)
            // 槽位：关=凹进去的深一档面板，开=黄铜。浅色主题下"关"如果还是亮面板，
            // 整颗开关会糊在纸面上，读起来像禁用而不是"没打开"。
            .background(if (checked) p.accent.copy(alpha = 0.88f) else p.surface)
            .border(1.dp, if (checked) p.accentDeep else p.border, shape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) p.onAccent else Color(0xFFFBFAF4))
                .border(
                    width = 1.dp,
                    color = if (checked) Color.Transparent else p.border,
                    shape = RoundedCornerShape(4.dp),
                ),
        )
    }
}

/** 检查更新的界面状态。与 [UpdateCheck] 的区别：这里多一个"正在检查"。 */
internal sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object Latest : UpdateUi

    /** 仓库一条 Release 都没有：中性状态，不染红。 */
    data object NoRelease : UpdateUi

    /** 发现新版：整条 [ReleaseInfo] 都留着，下载时要它的 APK 地址与体积。 */
    data class Available(val release: ReleaseInfo) : UpdateUi
    data class Failed(val message: String) : UpdateUi

    /**
     * GitHub 的 60 次/小时额度用尽。
     *
     * [retryAtEpochMs] 是服务端给的恢复时刻，界面按它现算倒计时 —— 存"还剩多少秒"
     * 会在熄屏、切后台、改系统时间之后变成一句谎话。
     */
    data class Limited(val retryAtEpochMs: Long) : UpdateUi
}

/** `12 分钟` / `48 秒`。分钟向上取整：显示"1 分钟"时绝不该还剩 30 秒就走了。 */
private fun countdownPhrase(remainingMs: Long): String {
    val seconds = (remainingMs / 1000).coerceAtLeast(0)
    return if (seconds >= 60) "${(seconds + 59) / 60} 分钟" else "$seconds 秒"
}

/** 按钮里的极简版：字数被 48dp 的胶囊卡着，省掉"分钟"两个字。 */
private fun countdownShort(remainingMs: Long): String {
    val seconds = (remainingMs / 1000).coerceAtLeast(0)
    return if (seconds >= 60) "${(seconds + 59) / 60}分" else "${seconds}秒"
}

private fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show() }
}

/**
 * 开源与素材声明（FOT-024）。
 *
 * 内容全部来自 `strings.xml` 的 `licenses_*`：CC BY-SA 4.0 的披露义务要求署名
 * 与修改说明可读，**不许**为了好看把这些句子改短。
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val p = MaterialTheme.glass
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = GlassSpace.page, end = GlassSpace.page, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                label = "返回",
                onClick = onBack,
                icon = { tint -> GlassIcon(GlassIconKind.Back, tint) },
            )
            Spacer(Modifier.width(GlassSpace.gap))
            Text(
                text = stringResource(R.string.licenses_title),
                style = GlassType.cardTitle,
                color = p.onGlass,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassSpace.page),
            verticalArrangement = Arrangement.spacedBy(GlassSpace.gap),
        ) {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = GlassType.caption,
                color = p.onGlassMuted,
            )
            LicenseSection(R.string.licenses_section_engine, R.string.licenses_engine_body)
            LicenseSection(R.string.licenses_section_packs, R.string.licenses_packs_body)
            LicenseSection(R.string.licenses_section_thirdparty, R.string.licenses_thirdparty_body)
            LicenseSection(R.string.licenses_section_trademark, R.string.licenses_trademark_body)
            Spacer(Modifier.height(GlassSpace.dockReserve))
        }
    }
}

@Composable
private fun LicenseSection(titleRes: Int, bodyRes: Int) {
    val p = MaterialTheme.glass
    GlassSurface(
        shape = GlassShape.card,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column {
            Text(text = stringResource(titleRes), style = GlassType.cardTitle, color = p.onGlass)
            Spacer(Modifier.height(GlassSpace.tight))
            Text(
                text = stringResource(bodyRes),
                style = GlassType.caption,
                color = p.onGlassMuted,
            )
        }
    }
}
