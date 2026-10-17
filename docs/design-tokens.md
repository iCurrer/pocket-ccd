# Pocket CCD — 设计令牌（阶段 1 临时版）

**日期：** 2026-10-17  
**状态：** `PROVISIONAL` — 结构已集中，视觉值待真机视觉评审替换  
**代码：** `app/src/main/java/com/pocketccd/prototype/ui/theme/Tokens.kt`、`Type.kt`、`Theme.kt`

> 方向：哑光黑机身、少量细线、单一橙红强调色。避免大面积发光、泛滥玻璃拟态和多层卡片。

## 1. 颜色

| 令牌 | 值 | 用途 | 状态 |
|---|---|---|---|
| `Background` | `#070708` | 页面底色 | 临时 |
| `BackgroundElevated` | `#0C0C0E` | 轻微分层 | 临时 |
| `Surface` | `#121214` | 卡片/控件底 | 临时 |
| `SurfaceHigh` | `#19191C` | 高一级表面 | 临时 |
| `SurfaceHighest` | `#202024` | 高亮表面 | 临时 |
| `Outline` | `#2B2B30` | 边框 | 临时 |
| `OutlineSoft` | `#1E1E22` | 弱边框 | 临时 |
| `TextPrimary` | `#E7E2D8` | 主文字 | 临时 |
| `TextSecondary` | `#8E887D` | 次级文字 | 临时 |
| `TextTertiary` | `#5C574F` | 提示/机刻字 | 临时 |
| `Accent` | `#E8562F` | 快门/强调 | 临时 |
| `AccentPressed` | `#C44524` | 按下态 | 临时 |
| `AccentSoft` | `#26E8562F` | 强调容器 | 临时 |
| `OnAccent` | `#1A0804` | 强调色上的文字 | 临时 |
| `Error` | `#D98B80` | 错误 | 临时 |
| `LensGlass` | `#060607` | 镜头玻璃 | 临时 |
| `LensRing` | `#3D3D42` | 镜头环 | 临时 |
| `LensBlue` | `#1D2A38` | 镜头玻璃高光 | 临时 |
| `LensBlueDeep` | `#091018` | 镜头玻璃过渡 | 临时 |
| `LensCore` | `#050A0E` | 镜头中心 | 临时 |
| `Flash` | `#E8DFC8` | 闪光灯点 | 临时 |
| `CameraPanelTop` | `#161619` | 机身上缘 | 临时 |
| `CameraPanelBottom` | `#0B0B0D` | 机身下缘 | 临时 |
| `CameraPanelHighlight` | `#18FFFFFF` | 机身内高光/内边框 | 临时 |
| `ViewfinderTop` | `#071012` | 取景框上缘 | 临时 |
| `ViewfinderBottom` | `#020405` | 取景框下缘 | 临时 |
| `ShutterOuter` | `#29292E` | 快门金属外环 | 临时 |
| `ShutterInner` | `#E8562F` | 快门橙红面 | 临时 |
| `ShutterInnerPressed` | `#C44524` | 快门按压面 | 临时 |
| `ShutterHighlight` | `#66FFFFFF` | 快门高光 | 临时 |

**对比度检查（待真机/工具复测）：**
- `TextPrimary` on `Background`：预计 > 12:1；
- `TextSecondary` on `Background`：预计 > 7:1；
- `TextTertiary` on `Background`：仅用于机刻字/非关键提示，需复测是否 ≥ 4.5:1；
- `OnAccent` on `Accent`：需复测 ≥ 4.5:1。

## 2. 间距

| 令牌 | 值 | 用途 |
|---|---|---|
| `xxs` | 2dp | 细分隔 |
| `xs` | 4dp | 图标与文字 |
| `sm` | 8dp | 紧凑组 |
| `md` | 12dp | 常规组 |
| `lg` | 16dp | 机身内边距 |
| `xl` | 24dp | 页面边距 |
| `xxl` | 32dp | 大块分隔 |
| `xxxl` | 48dp | 大留白 |

## 3. 圆角

| 令牌 | 值 | 用途 |
|---|---|---|
| `xs` | 4dp | 小装饰/缩略图 |
| `sm` | 8dp | 小控件 |
| `md` | 12dp | 卡片 |
| `viewfinder` | 6dp | 取景框（相机取景框不应过度圆角） |
| `lg` | 20dp | 大面板块 |
| `xl` | 28dp | 机身（首页紧凑态与拍摄页共用同一圆角） |
| `pill` | 100dp | 胶囊按钮 |

## 4. 尺寸

| 令牌 | 值 | 用途 |
|---|---|---|
| `touchTarget` | 48dp | 最小触摸目标 |
| `shutter` | 68dp | 快门按钮面 |
| `shutterOuter` | 88dp | 快门总触摸区 |
| `shutterRing` | 80dp | 快门金属环 |
| `sideControl` | 44dp | 闪光灯/切换按钮 |
| `topBarHeight` | 44dp | 拍摄页顶栏 |
| `nameplateHeight` | 28dp | 型号铭牌 |
| `cameraBodyWidth` | 236dp | 首页紧凑机身宽 |
| `cameraBodyHeight` | 184dp | 首页紧凑机身高 |
| `cameraPanelMargin` | 12dp | 拍摄页机身外边距 |
| `cameraPanelPadding` | 16dp | 机身内边距 |
| `viewfinderPadding` | 16dp | 取景框四周机身留白 |
| `viewfinderInnerBorder` | 1dp | 取景框内边框 |
| `lensOuter` | 112dp | 镜头外环 |
| `lensInner` | 68dp | 镜头内环 |
| `lensCore` | 24dp | 镜头中心 |
| `lensHighlightDot` | 10dp | 镜头高光点 |
| `galleryThumb` | 48dp | 图库缩略图 |
| `galleryLens` | 18dp | 图库占位镜头 |
| `icon` | 20dp | 顶栏图标 |
| `hintIcon` | 18dp | 下拉提示图标 |
| `flashDot` | 5dp | 闪光灯指示点 |
| `accentStripWidth` / `accentStripHeight` | 36dp / 3dp | 机身强调条 |
| `actionHeight` | 56dp | 底部控件高度 |
| `ctaMinWidth` | 120dp | 主 CTA 最小宽度 |
| `hairline` | 1dp | 细边框 |
| `lensBorder` | 1dp | 镜头边框 |
| `pullThreshold` | 88dp | 首页下拉触发阈值（待真机调参） |

## 5. 字体

| 样式 | 字号/行高 | 字重 | 用途 |
|---|---|---|---|
| `PocketCcdType.Brand` | 16/20sp，字距 4sp | Medium | 品牌字标 |
| `PocketCcdType.MonoLabel` | 9/12sp，字距 1sp | Normal | 机刻字/按钮 |
| `PocketCcdType.MonoMicro` | 8/10sp，字距 0.8sp | Normal | 型号/技术小字 |
| `displayLarge` | 40/44sp | Bold | 大标题（其他页面） |
| `headlineMedium` | 26/32sp | SemiBold | 页面标题 |
| `titleLarge` | 20/26sp | SemiBold | 区块标题 |
| `bodyLarge` | 16/24sp | Normal | 正文 |
| `bodyMedium` | 14/20sp | Normal | 次级正文 |
| `labelLarge` | 13/17sp | Medium | 按钮 |
| `labelSmall` | 11/14sp | Normal | 辅助文字 |

字体族：品牌/正文用系统 Sans；机刻字用系统 Monospace。若后续引入品牌字体，必须确认许可证并放入 `res/font`。

## 6. 动效

| 令牌 | 值 | 用途 |
|---|---|---|
| `FastMs` | 120ms | 点击反馈 |
| `BaseMs` | 220ms | 常规淡入淡出 |
| `SlowMs` | 320ms | 大面板转场 |

**无缝转场：** 首页紧凑机身与拍摄页机身使用同一个 `sharedBounds` key（`camera-body`），由 `SharedTransitionLayout` 负责边界/尺寸连续变化；外围 chrome 只做轻微淡入淡出。当前为代码级实现，尚未真机验证。

## 7. 使用规则

- 页面不得直接写颜色/间距数值；必须引用 `PocketCcdColors`/`PocketCcdSpacing`/`PocketCcdRadius`/`PocketCcdSizes`。
- 新增令牌先在本文件登记，再改代码。
- 视觉评审后替换“临时”值，不改变令牌名称，避免页面重写。
- 所有交互控件≥48dp 触摸区；正文对比度≥4.5:1；字号 200% 不截断。
- 当前颜色/字体是**工程占位**，不是最终品牌设计。
