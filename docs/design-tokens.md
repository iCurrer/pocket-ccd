# Pocket CCD — 设计令牌（阶段 1 临时版）

**日期：** 2026-10-17  
**状态：** `PROVISIONAL` — 结构已集中，视觉值待 T1-014 评审替换  
**代码：** `app/src/main/java/com/pocketccd/prototype/ui/theme/Tokens.kt`、`Type.kt`、`Theme.kt`

> 规范 §2.1 要求：未完成视觉原型前不要把全部数值当成最终设计。本文件区分“结构令牌”（已固定）与“视觉令牌”（临时值）。

## 1. 颜色

| 令牌 | 值 | 用途 | 状态 |
|---|---|---|---|
| `Background` | `#0B0B0D` | 页面底色 | 临时 |
| `BackgroundElevated` | `#151518` | 渐变/分层 | 临时 |
| `Surface` | `#1C1C21` | 卡片/机身 | 临时 |
| `SurfaceHigh` | `#26262C` | 高一级表面 | 临时 |
| `SurfaceHighest` | `#313138` | 高亮表面 | 临时 |
| `Outline` | `#3A3A42` | 边框 | 临时 |
| `OutlineSoft` | `#2A2A31` | 弱边框 | 临时 |
| `TextPrimary` | `#F4F1EA` | 主文字 | 临时 |
| `TextSecondary` | `#A9A49A` | 次级文字 | 临时 |
| `TextTertiary` | `#6F6A62` | 提示/禁用 | 临时 |
| `Accent` | `#FF5A36` | 快门/主 CTA | 临时 |
| `AccentPressed` | `#E04A2A` | 按下态 | 临时 |
| `AccentSoft` | `#33FF5A36` | 强调容器 | 临时 |
| `OnAccent` | `#2A0B04` | 强调色上的文字 | 临时 |
| `Error` | `#FFB4AB` | 错误 | 临时 |
| `LensGlass` | `#070708` | 镜头玻璃 | 临时 |
| `LensRing` | `#45454D` | 镜头环 | 临时 |
| `LensBlue` | `#24344A` | 镜头玻璃高光 | 临时 |
| `LensBlueDeep` | `#0B1520` | 镜头玻璃过渡 | 临时 |
| `LensCore` | `#07131F` | 镜头中心 | 临时 |
| `Flash` | `#FFF3D6` | 闪光灯点 | 临时 |

**对比度检查（待 T1-014 用工具复测）：**
- `TextPrimary` on `Background`：高对比，预计 > 12:1；
- `TextSecondary` on `Background`：预计 > 7:1；
- `TextTertiary` on `Background`：仅用于非关键提示，需复测是否 ≥ 4.5:1；
- `OnAccent` on `Accent`：需复测 ≥ 4.5:1（橙色按钮上的深色文字）。

## 2. 间距

| 令牌 | 值 | 用途 |
|---|---|---|
| `xxs` | 2dp | 细分隔 |
| `xs` | 4dp | 图标与文字 |
| `sm` | 8dp | 紧凑组 |
| `md` | 12dp | 常规组 |
| `lg` | 16dp | 卡片内边距 |
| `xl` | 24dp | 页面边距 |
| `xxl` | 32dp | 大块分隔 |
| `xxxl` | 48dp | 大留白 |

## 3. 圆角

| 令牌 | 值 | 用途 |
|---|---|---|
| `sm` | 8dp | 小控件 |
| `md` | 12dp | 缩略图 |
| `lg` | 20dp | 卡片 |
| `xl` | 28dp | 机身/大卡片 |
| `pill` | 100dp | 胶囊按钮 |

## 4. 尺寸

| 令牌 | 值 | 用途 |
|---|---|---|
| `touchTarget` | 48dp | 最小触摸目标 |
| `shutter` | 84dp | 快门按钮（阶段 2 使用） |
| `cameraBodyWidth` | 268dp | 首页机身插画宽 |
| `cameraBodyHeight` | 208dp | 首页机身插画高 |
| `lensOuter` | 132dp | 镜头外环 |
| `lensInner` | 84dp | 镜头内环 |
| `lensCore` | 30dp | 镜头中心 |
| `lensHighlightDot` | 12dp | 镜头高光点 |
| `galleryThumb` | 56dp | 图库缩略图 |
| `galleryLens` | 22dp | 图库占位镜头 |
| `icon` | 22dp | 顶栏图标 |
| `hintIcon` | 20dp | 下拉提示图标 |
| `flashDot` | 6dp | 闪光灯指示点 |
| `viewfinderWidth` / `viewfinderHeight` | 38dp / 24dp | 取景窗装饰 |
| `accentStripWidth` / `accentStripHeight` | 44dp / 4dp | 机身强调条 |
| `actionHeight` | 64dp | 底部按钮/卡片高度 |
| `ctaMinWidth` | 144dp | 主 CTA 最小宽度 |
| `hairline` | 1dp | 细边框 |
| `lensBorder` | 2dp | 镜头边框 |

## 5. 字体

| 样式 | 字号/行高 | 字重 | 用途 |
|---|---|---|---|
| `displayLarge` | 44/48sp | Bold | 品牌字标 |
| `headlineMedium` | 28/34sp | SemiBold | 页面标题 |
| `titleLarge` | 20/26sp | SemiBold | 区块标题 |
| `bodyLarge` | 16/24sp | Normal | 正文 |
| `bodyMedium` | 14/20sp | Normal | 次级正文 |
| `labelLarge` | 14/18sp | SemiBold | 按钮 |
| `labelSmall` | 11/14sp | Medium | 辅助标签（字距 0.8sp） |

字体族暂用系统 Sans；如果后续引入品牌字体，必须确认许可证并放入 `res/font`。

## 6. 动效

| 令牌 | 值 | 用途 |
|---|---|---|
| `FastMs` | 120ms | 点击反馈 |
| `BaseMs` | 200ms | 常规转场 |
| `SlowMs` | 320ms | 页面转场 |

阶段 1 只使用默认 `AnimatedContent` 转场；T1-007 再定义下拉转场曲线。必须支持系统“减少动态效果”。

## 7. 使用规则

- 页面不得直接写颜色/间距数值；必须引用 `PocketCcdColors`/`PocketCcdSpacing`/`PocketCcdRadius`/`PocketCcdSizes`。
- 新增令牌先在本文件登记，再改代码。
- 视觉评审后替换“临时”值，不改变令牌名称，避免页面重写。
- 所有交互控件≥48dp；正文对比度≥4.5:1；字号 200% 不截断。
- 当前颜色/字体是**工程占位**，不是最终品牌设计。
