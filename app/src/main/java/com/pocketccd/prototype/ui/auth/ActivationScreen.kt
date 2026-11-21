package com.pocketccd.prototype.ui.auth

import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.BuildConfig
import com.pocketccd.prototype.auth.ActivateOutcome
import com.pocketccd.prototype.auth.ActivationClient
import com.pocketccd.prototype.auth.ActivationStore
import com.pocketccd.prototype.auth.CodeFormat
import com.pocketccd.prototype.auth.DeviceFingerprint
import com.pocketccd.prototype.ui.findActivityOrNull
import com.pocketccd.prototype.ui.glass.GlassButton
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.consolePanel
import com.pocketccd.prototype.ui.theme.consoleRecess
import com.pocketccd.prototype.ui.theme.glass
import kotlinx.coroutines.launch

/**
 * **内测激活页**：没有凭证时挡在整个 App 前面的唯一一屏。
 *
 * ## 为什么是全屏页而不是 `Dialog`
 *
 * 观感是弹窗（暖白机身 + 浮起的面板），实现是全屏分支。三条实现理由：
 *
 * 1. 拍摄页锁 `SENSOR_LANDSCAPE`（D-083），方向是按目的地设的。`Dialog` 自带独立
 *    window，转屏时序会跟 Activity 打架，表现为卡片横躺或闪一下。
 * 2. `Dialog` 排在自己的 Activity 之上，相机权限弹窗的层级会变得不可预期。
 * 3. 全屏分支能**完全不启动相机**（`PocketCcdRoute` 在挂载任何副作用之前就返回），
 *    没激活的人不会消耗一次相机绑定。
 *
 * ## 一次激活，之后永久离线
 *
 * 激活成功后这里就不再出现：`ActivationStore` 有 token 即视为通过，服务端不可达
 * 也不影响拍照。这是相机 App 的底线 —— 不能因为后端挂了就让用户拍不了照。
 */
@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { ActivationStore(context) }
    val scope = rememberCoroutineScope()
    val device = remember { DeviceFingerprint.of(context) }

    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var whyOpen by rememberSaveable { mutableStateOf(false) }

    // 第 13 条 UI 红线：任何界面都不许横躺。冷启动时窗口可能还带着上一次拍摄页的
    // 横屏状态（`requestedOrientation` 不跟着进程走），所以这里显式钉竖屏。
    val activity = remember(context) { context.findActivityOrNull() }
    LaunchedEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun submit() {
        if (busy || !CodeFormat.isComplete(code)) return
        busy = true
        error = null
        scope.launch {
            when (val result = ActivationClient.activate(code, device)) {
                is ActivateOutcome.Granted -> {
                    store.save(result.token, code, System.currentTimeMillis())
                    onActivated()
                }

                is ActivateOutcome.Denied -> {
                    error = result.reason.message
                    busy = false
                }

                is ActivateOutcome.Unreachable -> {
                    // 不显示原始异常：`message` 里可能是 SocketTimeoutException 之类，
                    // 对用户没有可操作性，真正定位靠 logcat 的 tag。
                    error = "连不上服务器，检查网络后重试"
                    busy = false
                }
            }
        }
    }

    ActivationPage(
        code = code,
        busy = busy,
        error = error,
        whyOpen = whyOpen,
        onCodeChange = {
            code = it
            if (error != null) error = null
        },
        onSubmit = ::submit,
        onToggleWhy = { whyOpen = !whyOpen },
        modifier = modifier,
    )
}

/**
 * 无状态版式：只吃参数、只抛回调。
 *
 * 拆出来是为了能进 `ScreenshotTest`（截图回归不能真的发网络请求），也让"按下激活后
 * 界面长什么样"能单独摆出来看。
 *
 * ## D-094：版式重做，顺手修掉"内容浮在半空"的 bug
 *
 * 上一版的骨架是 `BoxWithConstraints` + 一个固定落款 `align(BottomCenter)`，中间用
 * `heightIn(min = maxHeight - 172.dp)` 让表单居中。172dp 是**手写的估算值**，而落款
 * 的真实高度会随字体缩放、机型、版本号长度变化 —— 估小了落款压住"这是什么？"那张卡，
 * 估大了整块内容往下沉。截图里那一大截"下半屏全空"的来源就在这里。
 *
 * 新版把落款**放回列里**：`weight(1f)` 的滚动区 + 列尾的落款。滚动区高度由 Compose
 * 算，不再有魔法数字；落款永远在最底、永远不覆盖内容，键盘弹起时它跟着上移。
 *
 * 排版同时改成**居中单列**：整屏只有一个动作（输入 → 激活），左对齐的多层卡片读起来
 * 像一张表单，居中才是"一台机器在等你插入凭证"。
 */
@Composable
fun ActivationPage(
    code: String,
    busy: Boolean,
    error: String?,
    whyOpen: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleWhy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = MaterialTheme.glass
    val complete = CodeFormat.isComplete(code)

    Box(modifier.fillMaxSize().background(p.background)) {
        // 机身顶部的一层暖光：整页平涂会让"这是一台机器"读成"这是一张白纸"。
        // 不参与布局，所以不会把内容推下去。
        //
        // 渐变终点必须是**同色的透明**（`background.copy(alpha = 0f)`），不能是
        // `Color.Transparent`：后者是透明黑，插值到暖白时会经过一段灰，在渐变末端
        // 留下一条能看见的水平分界线 —— 截图里那条"横带"就是它。
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(p.glowA.copy(alpha = 0.60f), p.background.copy(alpha = 0f)),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize().imePadding().statusBarsPadding()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // 滚动区的高度 = 视口减落款。`heightIn(min = 视口)` 交给内层，
                // 内层才有一个**确定的高度**可用 `Arrangement.Center` ——
                // `verticalScroll` 里的无限高约束会让 Center 静默失效。
                val viewport = maxHeight
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = viewport)
                            .padding(horizontal = GlassSpace.page),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // 光学居中：几何居中会把内容放得偏高，因为底部那两行小字撑不住重量。
                        // 顶上垫 56dp（居中后净下移 28dp），上下留白约 1.35:1，读起来才是"稳"。
                        Spacer(Modifier.height(56.dp))

                        BrandMark()
                        Spacer(Modifier.height(34.dp))

                        Text(
                            text = "内测凭证",
                            style = GlassType.title,
                            color = p.onGlass,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "这台设备还没激活。输入邀请码，激活一次之后不再联网。",
                            style = GlassType.body,
                            color = p.onGlassMuted,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(30.dp))

                        CodeCells(
                            code = code,
                            enabled = !busy,
                            onCodeChange = onCodeChange,
                            onDone = onSubmit,
                        )

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = codePreview(code),
                            style = GlassType.badge,
                            // 空态那行只是"格式说明"，不该和真读数抢注意力。
                            color = if (complete) p.accentBright else p.onGlassMuted.copy(alpha = 0.62f),
                        )

                        // 固定占位：没有错误时也占 24dp，否则出错时按钮会往下跳一格。
                        // 与上面那行格式提示之间留 6dp：两者都是小字，贴在一起会读成一行。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .height(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (error != null) {
                                Text(
                                    text = error,
                                    style = GlassType.caption,
                                    color = p.error,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        GlassButton(
                            text = if (busy) "正在核对…" else "激活",
                            primary = true,
                            enabled = complete,
                            busy = busy,
                            height = 52.dp,
                            textStyle = GlassType.action,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onSubmit,
                        )

                        Spacer(Modifier.height(16.dp))
                        WhyLink(open = whyOpen, onToggle = onToggleWhy)
                    }
                }
            }

            ActivationFooter()
        }
    }
}

/** 顶部品牌标记：一根黄铜短条 + 全大写小字。与设置页的分组标签同一个语汇。 */
@Composable
private fun BrandMark() {
    val p = MaterialTheme.glass
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(18.dp)
                .height(3.dp)
                .background(p.accentBright),
        )
        Spacer(Modifier.width(8.dp))
        Text("POCKET CCD", style = GlassType.badge, color = p.onGlassMuted)
    }
}

/**
 * 邀请码输入：**八个字符格**，四四分组。
 *
 * ## 为什么不用一个输入框
 *
 * 邀请码是 8 位 Crockford Base32，用户是从聊天记录里一位一位对着抄的。一个居中的
 * 输入框只能告诉他"还差几个字"；八个格能告诉他**差第几个**，而且天然对齐
 * `PCCD-XXXX-XXXX` 的分组读法。
 *
 * ## 实现：透明输入框在下，格子在上
 *
 * 真正的 `BasicTextField` 铺满整行、文字与光标全透明，只负责收键盘与插入删除；
 * 格子画在它上面。**格子不接点击**，所以点击会穿透到底层的输入框 —— 不需要额外
 * 的 `focusRequester`，也不会和 IME 的选择行为打架。
 *
 * 当前输入位是**凸起的一格**（唯一亮着的件），其余是凹槽：像面板上只有一格通电。
 */
@Composable
private fun CodeCells(
    code: String,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val p = MaterialTheme.glass
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 6.dp
        // 中间那道缝比其它格大一点，读起来才是"两组四位"而不是"八个一位"。
        val groupGap = 12.dp
        val cellWidth = ((maxWidth - gap * 6 - groupGap) / 8).coerceIn(30.dp, 44.dp)
        // 比 4:3 更方一点：太瘦像输入框，太胖像按钮。1.18 是"冲压字符格"的比例。
        val cellHeight = cellWidth * 1.18f
        val shape = GlassShape.control

        BasicTextField(
            value = code,
            onValueChange = { onCodeChange(CodeFormat.sanitize(it)) },
            modifier = Modifier.matchParentSize(),
            enabled = enabled,
            singleLine = true,
            textStyle = GlassType.codeCell.copy(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until 8) {
                when {
                    i == 4 -> Spacer(Modifier.width(groupGap))
                    i > 0 -> Spacer(Modifier.width(gap))
                }
                val char = code.getOrNull(i)
                val isCursor = focused && enabled && i == code.length
                // 还没聚焦、也还没输入时，第一格挂一道极淡的金边：它是"从这里开始"的
                // 唯一的引导。自动弹键盘更"高效"，但一进页面就顶起半个屏幕不礼貌。
                val isStart = !focused && code.isEmpty() && i == 0
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(cellHeight)
                        .then(
                            if (isCursor) {
                                Modifier.consolePanel(
                                    color = p.surfaceRaised,
                                    shape = shape,
                                    elevation = 3.dp,
                                    borderColor = p.accentBright,
                                    bevel = 0.8f,
                                )
                            } else {
                                Modifier.consoleRecess(
                                    shape = shape,
                                    // 槽底用**机身色压半档**而不是更深的灰：
                                    // 八个深灰块在暖白底上会读成"八个空按钮"，
                                    // 而它只是八个凹进去的格。凹陷靠内阴影表达，不靠色差。
                                    color = p.surface.copy(alpha = 0.62f),
                                    depth = 3.dp,
                                    // 小格子上"下沿反光"几乎要关掉：环形的内阴影 + 一道亮底边，
                                    // 合起来读成"倒角凸台"而不是"凹槽"。凹槽的判据只有一条 ——
                                    // **上沿最暗、越往下越亮、底部不超过机身色**。
                                    // 40dp 的格子面积太小，内阴影占到 1/4，所以强度整体压到 0.28。
                                    shade = 0.28f,
                                    rimLight = 0f,
                                )
                            },
                        )
                        .then(
                            if (isStart) {
                                Modifier.border(
                                    width = 1.5.dp,
                                    color = p.accentBright.copy(alpha = 0.55f),
                                    shape = shape,
                                )
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (char != null) {
                        Text(
                            text = char.toString(),
                            style = GlassType.codeCell,
                            color = p.onGlass,
                        )
                    } else if (isCursor) {
                        // 自绘光标：系统光标是跟随透明文字的，看不见，也没法对齐格心。
                        Box(
                            Modifier
                                .width(1.5.dp)
                                .height(cellHeight * 0.46f)
                                .background(p.accentBright),
                        )
                    }
                }
            }
        }
    }
}

/** 下面那行灰字：把裸 8 位补成 `PCCD-XXXX-XXXX`，方便按组核对。 */
private fun codePreview(code: String): String =
    if (code.isEmpty()) "PCCD-········" else CodeFormat.display(code)

/**
 * 可折叠说明。
 *
 * 默认收起：首屏只该有一个动作用户要完成（输入码）。但"凭什么要我的码、上传了什么东西"
 * 必须能查到 —— 收在这一行字里，写的是实话，不是一段免责声明。
 *
 * D-094 起不再用卡片包：这一屏已经有一张"输入区"，再套一张卡会变成两层框，
 * 而它本来就只是一句补充说明。
 */
@Composable
private fun WhyLink(open: Boolean, onToggle: () -> Unit) {
    val p = MaterialTheme.glass
    val label = if (open) "收起说明" else "这是什么？"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onToggle)
                .semantics { contentDescription = label }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIcon(GlassIconKind.Info, p.accentBright, size = 15.dp)
            Spacer(Modifier.width(7.dp))
            Text(text = label, style = GlassType.label, color = p.accentBright)
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text = "内测阶段只发给愿意反馈问题的人。激活时上传三样东西：邀请码、" +
                    "设备的哈希值（不可反查）、机型与应用版本。不上传照片，不做行为追踪。",
                style = GlassType.caption,
                color = p.onGlassMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * 落款：联系方式与版本号。
 *
 * 位置在**列尾**而不是 `align(BottomCenter)` —— 它属于版式的最后一行，不是浮层。
 * 做成浮层就必须给中间的内容区减一个估算高度，那个估算就是上一版布局 bug 的根。
 */
@Composable
private fun ActivationFooter() {
    val p = MaterialTheme.glass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GlassSpace.page, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "没有邀请码？联系发放这个内测包的开发者。",
            style = GlassType.caption,
            color = p.onGlassMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME} · ${Build.MODEL}",
            style = GlassType.badge,
            color = p.onGlassMuted.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
    }
}
