# Pocket CCD — 设计令牌（阶段 1 临时版）

**日期：** 2026-10-17  
**状态：** `PROVISIONAL` — 结构已集中，视觉值待真机视觉评审替换  
**代码：** `app/src/main/java/com/pocketccd/prototype/ui/theme/Tokens.kt`、`Type.kt`、`Theme.kt`  
**配色研究：** [`color-direction.md`](color-direction.md)

> 方向：燕麦色纸面、灰绿玩具机身、深松绿取景框、陶土快门。低饱和、暖中性、单一强调色；避免纯黑高对比、发光和泛滥玻璃拟态。

## 1. 颜色

| 令牌 | 值 | 用途 | 状态 |
|---|---|---|---|
| `Background` | `#F4EFE6` | 页面底色（暖燕麦） | 临时 |
| `BackgroundElevated` | `#EDE6DA` | 轻微分层 | 临时 |
| `Surface` | `#FBF7EF` | 纸面/按钮帽 | 临时 |
| `SurfaceHigh` | `#E7DECD` | 高一级表面 | 临时 |
| `SurfaceHighest` | `#DED3C1` | 高亮表面 | 临时 |
| `Outline` | `#C9BEAC` | 边框 | 临时 |
| `OutlineSoft` | `#DED4C4` | 弱边框 | 临时 |
| `TextPrimary` | `#26241F` | 主文字（暖墨） | 临时 |
| `TextSecondary` | `#3F3C34` | 次级文字/机身铭牌 | 临时 |
| `TextTertiary` | `#6B6458` | 非关键提示 | 临时 |
| `Accent` | `#D46A4C` | 快门/大色块 | 临时 |
| `AccentPressed` | `#B9543A` | 按下态 | 临时 |
| `AccentSoft` | `#26D46A4C` | 强调容器 | 临时 |
| `AccentText` | `#A94A32` | 奶油底上的强调文字（4.94:1） | 临时 |
| `OnAccent` | `#1A0804` | 陶土色上的深色文字（如未来需要） | 临时 |
| `Error` | `#B85F4D` | 错误 | 临时 |
| `OnError` | `#FFF8EF` | 错误色上的文字 | 临时 |
| `LensGlass` | `#1A2421` | 镜头玻璃 | 临时 |
| `LensRing` | `#6E7D6B` | 镜头环 | 临时 |
| `LensBlue` | `#38514D` | 镜头玻璃高光 | 临时 |
| `LensBlueDeep` | `#16231F` | 镜头玻璃过渡 | 临时 |
| `LensCore` | `#0B1210` | 镜头中心 | 临时 |
| `Flash` | `#E8C56A` | 黄油色闪光灯窗 | 临时 |
| `CameraPanelTop` | `#B3C0AD` | 机身上缘（灰绿） | 临时 |
| `CameraPanelBottom` | `#96A790` | 机身下缘 | 临时 |
| `CameraPanelHighlight` | `#66FFFFFF` | 机身内高光/细边 | 临时 |
| `ViewfinderTop` | `#1B2523` | 取景框上缘（深松绿） | 临时 |
| `ViewfinderBottom` | `#0C1211` | 取景框下缘 | 临时 |
| `ViewfinderInk` | `#C6D0C4` | 取景框内文字/标记 | 临时 |
| `ShutterOuter` | `#E9DFCF` | 象牙金属外环 | 临时 |
| `ShutterInner` | `#D46A4C` | 快门陶土面 | 临时 |
| `ShutterInnerPressed` | `#B9543A` | 快门按压面 | 临时 |
| `ShutterHighlight` | `#AAFFFFFF` | 快门高光 | 临时 |

**对比度实测（2026-10-17，WCAG 公式）：**

| 前景/背景 | 对比度 |
|---|---:|
| `#26241F` on `#F4EFE6` | 13.54:1 |
| `#3F3C34` on `#F4EFE6` | 9.61:1 |
| `#6B6458` on `#F4EFE6` | 5.11:1 |
| `#3F3C34` on `#A9B7A5` | 5.25:1 |
| `#A94A32` on `#F4EFE6` | 4.94:1 |
| `#C6D0C4` on `#1B2523` | 9.90:1 |

`#D46A4C` 不作为奶油底上的小字颜色；强调文字用 `AccentText`。`#FFF8EF` on `#D46A4C` 仅 3.34:1，若未来在陶土色上放文字，用深色 `#1A0804`。

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
| `viewfinder` | 6dp | 取景框 |
| `lg` | 20dp | 大面板块 |
| `xl` | 28dp | 机身（首页紧凑态与拍摄页共用） |
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
