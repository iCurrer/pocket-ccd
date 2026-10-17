# Pocket CCD — 设计令牌（V2 玩具相机方向）

**日期：** 2026-10-17  
**状态：** `PROVISIONAL` — 结构已集中，视觉值待真机视觉评审替换  
**代码：** `app/src/main/java/com/pocketccd/prototype/ui/theme/Tokens.kt`、`Type.kt`、`Theme.kt`  
**配色研究：** [`color-direction.md`](color-direction.md)

> V2 方向：**黄油塑料机身 + 奶油面板 + 薄荷按钮 + 珊瑚快门 + 深青取景窗**。
> 不再用“奶油网页 + 细字列表”，而是让每个页面都像一台实体玩具相机的一部分。

## 1. 颜色

| 令牌 | 值 | 用途 | 状态 |
|---|---|---|---|
| `Background` | `#E6F0EA` | 桌面底色（浅薄荷） | 临时 |
| `BackgroundElevated` | `#D9E7E0` | 桌面分层 | 临时 |
| `Surface` | `#FFF4E2` | 奶油面板 | 临时 |
| `SurfaceHigh` | `#F3DFC2` | 面板阴影/次级面 | 临时 |
| `SurfaceHighest` | `#EAD2B0` | 高亮面 | 临时 |
| `Outline` | `#C8B79E` | 细边框 | 临时 |
| `OutlineSoft` | `#E2D2BC` | 弱边框 | 临时 |
| `TextPrimary` | `#2F2923` | 主文字/机刻字 | 临时 |
| `TextSecondary` | `#5B4F43` | 次级文字 | 临时 |
| `TextTertiary` | `#7A6B5C` | 提示/非关键 | 临时 |
| `Accent` | `#E76F51` | 珊瑚快门 | 临时 |
| `AccentPressed` | `#C8553D` | 快门按压态 | 临时 |
| `AccentSoft` | `#26E76F51` | 强调容器 | 临时 |
| `AccentText` | `#B84F38` | 奶油底上的珊瑚文字（4.58:1） | 临时 |
| `OnAccent` | `#2B1710` | 珊瑚色上的深色文字（5.51:1） | 临时 |
| `Error` | `#B84A3A` | 错误 | 临时 |
| `OnError` | `#FFF9F0` | 错误色上的文字 | 临时 |
| `Warning` | `#B8862F` | 处理中 | 临时 |
| `LensGlass` | `#143433` | 镜头玻璃 | 临时 |
| `LensRing` | `#6FB7A4` | 镜头薄荷环 | 临时 |
| `LensBlue` | `#2E5E59` | 镜头玻璃高光 | 临时 |
| `LensBlueDeep` | `#143C39` | 镜头玻璃过渡 | 临时 |
| `LensCore` | `#071B1A` | 镜头中心 | 临时 |
| `Flash` | `#F7D774` | 黄油闪光灯窗 | 临时 |
| `CameraPanelTop` | `#F2C14E` | 机身黄油亮部 | 临时 |
| `CameraPanelBottom` | `#DFA92F` | 机身黄油暗部 | 临时 |
| `CameraPanelHighlight` | `#FFF2C7` | 机身内高光 | 临时 |
| `ViewfinderTop` | `#173B3A` | 取景窗/LCD 深青 | 临时 |
| `ViewfinderBottom` | `#0C2423` | 取景窗暗部 | 临时 |
| `ViewfinderInk` | `#D8F1E6` | 取景窗文字 | 临时 |
| `ShutterOuter` | `#FFF4E2` | 快门奶油外环 | 临时 |
| `ShutterInner` | `#E76F51` | 快门珊瑚面 | 临时 |
| `ShutterInnerPressed` | `#C8553D` | 快门按压面 | 临时 |
| `ShutterHighlight` | `#CCFFFFFF` | 快门高光 | 临时 |
| `Mint` | `#9FD8C4` | 薄荷物理按钮 | 临时 |
| `MintDeep` | `#6FB7A4` | 薄荷按钮暗部 | 临时 |
| `Butter` | `#F7D774` | 黄油按钮/细节 | 临时 |

### 1.1 拍摄页红色变体

按参考提示词，拍摄页单独使用高饱和复古红机身；其余页面仍是黄油/奶油 V2 材质。

| 令牌 | 值 | 用途 |
|---|---|---|
| `RetroRed` | `#F12D27` | 拍摄页全屏红塑料 |
| `RetroRedLight` | `#FF5A45` | 红色亮部/顶部高光 |
| `RetroRedDeep` | `#C91E1A` | 红色暗部/翻转键 |
| `RetroInk` | `#2A0B09` | 深色外轮廓/文字 |
| `RetroBlack` | `#101010` | 取景器内部纯黑 |
| `RetroOrange` | `#F28C28` | 橙色快门面 |
| `RetroOrangeDeep` | `#D86F18` | 橙色快门暗部 |
| `RetroGreen` | `#7FD6A4` | 闪光灯/绿色状态灯 |
| `RetroGreenDeep` | `#4FAF7C` | 闪光灯关闭态 |
| `RetroCream` | `#FFF3E0` | 品牌字/图标 |

**对比度实测（2026-10-17，WCAG 公式）：**

| 前景/背景 | 对比度 |
|---|---:|
| `#2F2923` on `#FFF4E2` | 13.19:1 |
| `#2F2923` on `#F2C14E` | 8.56:1 |
| `#5B4F43` on `#FFF4E2` | 7.30:1 |
| `#5B4F43` on `#F2C14E` | 4.73:1 |
| `#B84F38` on `#FFF4E2` | 4.58:1 |
| `#D8F1E6` on `#173B3A` | 10.24:1 |
| `#2B1710` on `#E76F51` | 5.51:1 |
| `#2F2923` on `#9FD8C4` | 8.95:1 |
| `#FFF3E0` on `#F12D27` | 3.72:1（仅 20sp 粗体品牌大字） |
| `#FFF3E0` on `#C91E1A` | 5.20:1 |
| `#2A0B09` on `#F12D27` | 4.47:1 |
| `#2A0B09` on `#7FD6A4` | 10.48:1 |
| `#2A0B09` on `#F28C28` | 7.44:1 |
| `#F12D27` on `#101010` | 4.66:1 |

红色页面上的小字不使用 `RetroCream on RetroRed`（仅 3.72:1）；品牌 20sp 粗体可作为大字号通过。取景器内文字放在近黑背景上。

## 2. 形状与材质

| 令牌 | 值 | 用途 |
|---|---|---|
| `Radius.body` | 36dp | 玩具相机外壳 |
| `Radius.xl` | 28dp | 内面板 |
| `Radius.md` | 12dp | LCD/按钮面 |
| `Radius.viewfinder` | 8dp | 取景窗 |
| `Radius.pill` | 100dp | FILM 胶囊按钮 |
| 外壳 | 18dp 阴影 + 2dp 高光边 | 塑料厚度 |
| 面板 | 奶油渐变 + 1dp 内线 | 机身材质 |
| 按钮 | 6dp 阴影 + 2dp 白高光边 | 可按压塑料键 |

## 3. 间距与尺寸

| 令牌 | 值 | 用途 |
|---|---|---|
| `cameraBodyWidth` | 300dp | 首页机身宽 |
| `cameraBodyHeight` | 300dp | 首页机身高 |
| `cameraPanelMargin` | 12dp | 拍摄/照片墙/成片/设置外边距 |
| `cameraPanelPadding` | 12dp | 机身内边距 |
| `lensOuter` | 148dp | 首页镜头外环 |
| `lensInner` | 92dp | 首页镜头内环 |
| `lensCore` | 30dp | 镜头中心 |
| `shutter` | 72dp | 快门面 |
| `shutterOuter` | 92dp | 快门触摸区 |
| `shutterRing` | 84dp | 快门外环 |
| `sideControl` | 48dp | 侧控件 |
| `touchTarget` | 48dp | 最小触摸目标 |
| `pullThreshold` | 88dp | 首页上/下拉阈值 |
| `viewfinderPadding` | 14dp | 取景窗内边距 |
| `nameplateHeight` | 28dp | 型号铭牌 |
| `topBarHeight` | 44dp | 顶部控制行 |

## 4. 字体

| 样式 | 字号/行高 | 字重 | 用途 |
|---|---|---|---|
| `PocketCcdType.Brand` | 18/22sp，字距 3sp | Black | 机身品牌 |
| `PocketCcdType.MonoLabel` | 10/13sp，字距 1.2sp | Bold | 机刻字/按钮 |
| `PocketCcdType.MonoMicro` | 8/11sp，字距 0.7sp | Medium | 型号/LCD 小字 |
| `displayLarge` | 40/44sp | Black | 其他页面大标题 |
| `headlineMedium` | 26/32sp | Bold | 页面标题 |
| `bodyMedium` | 14/20sp | Normal | 正文 |

> 不再使用 Monospace 作为主要 UI 字体；机刻字用**加粗圆润无衬线 + 字距**模拟塑料压印，避免“网页仪表盘”感。

## 5. 动效

| 令牌 | 值 | 用途 |
|---|---|---|
| `FastMs` | 120ms | 按压反馈 |
| `BaseMs` | 220ms | 常规淡入淡出 |
| `SlowMs` | 320ms | 机身展开/收起 |

**无缝转场：** 首页紧凑机身与拍摄页机身共用 `ToyCameraBody` + `sharedBounds("camera-body")`，`RemeasureToBounds` 防止内容被拉伸，外围 chrome 淡入淡出。

## 6. 使用规则

- 页面不再使用“整页列表 + 分隔线”的网页布局；内容放在 `ToyCameraBody` / `ToyScreen` / 物理按钮里。
- 页面不得直接写颜色/间距数值；必须引用令牌。
- 新增令牌先在本文件登记，再改代码。
- 所有交互控件≥48dp 触摸区；正文对比度≥4.5:1；字号 200% 不截断。
- 当前颜色/字体是**工程占位**，不是最终品牌设计。
