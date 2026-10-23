# 2026-10-23 夜间：视觉方向 C「白纸档案」落地（v0.5.0）

**包：** `app/build/outputs/apk/debug/app-debug.apk`（debug 签名，可直接覆盖安装；versionCode 8 / 0.5.0）
**决策：** D-063（`DECISION_LOG.md`）｜ **规格：** `docs/paper-archive-ui-spec.md`
**基线：** `1ed3ff1`（v0.4.1）

---

## 1. 一句话

取景页是**黑闸门**（近黑 + 纸白刻字，去香槟金/红点强调）；按下快门后，成片/照片墙/胶卷库/设置/声明
全部变成**相纸**（#ECEAE4 + 墨 + 石板蓝）。不是主题切换，是"在黑暗里拍、在纸上归档"的材质分工。
相机、冲洗、落盘、EXIF、卷计数、系统相册同步的逻辑零改动。

## 2. 改了什么（按文件）

| 区域 | 文件 | 内容 |
|---|---|---|
| 主题 | `ui/film/FilmTheme.kt` | 新增 `FilmGateSpec`（取景黑闸门）、`FilmPaperSpec`（纸面四页）、`FilmMotion`、`FilmType` 字体族/行高、纸面 metrics |
| 组件 | `ui/film/PaperComponents.kt`（新） | `PaperPageHeader` / `PaperHairline` / `PaperSectionLabel` / `PaperIndexTab` / `PaperButton` / `PaperSwitch` / `PaperEmptyState` |
| 主题接线 | `ui/AppRoot.kt`、`MainActivity.kt`、`ui/theme/Theme.kt` | 按目的地切换 Film spec + M3 亮/暗兜底；系统栏图标按页面明暗与相册灯箱切换；取景页单独套 GateSpec |
| 取景 | `FilmCaptureScreen` / `FilmControls` / `FilmInteractions` / `FilmViewfinder` | 胶卷胶囊→下划线 tab；工具条激活白 12%；底盖纯黑平色 + 1px 缝；快门纸白环/墨点；对焦/EV/变焦改纸白；Toast 深底纸白 + liveRegion；顶栏 44→48dp |
| 成片 | `ResultScreen.kt` | 相纸页 + `PrintFrame`（88% 宽 / 6dp 纸白边 / 1px 墨线 / 无阴影）；真实 EXIF 三行；打字机显影（真实阶段 + 秒数，无百分比/无扫描带）；纸面主次按钮；缩略图解码移出主线程 |
| 照片墙 | `PhotoWallScreen.kt` / `PhotoViewer.kt` | 接触印相 3 列 + 帧号 + 卷头规则线 + 索引页签；查看器黑底灯箱（铺满系统栏）+ 纸面 caption；帧号=组内曝光顺序 |
| 胶卷库 | `FilmLibraryScreen.kt` | 纸面索引卡 + `CURRENT` 黑带；去金属渐变/浅金属面板；保留胶卷筒 Canvas、36 帧点阵、900ms 装卷动画；删 `.fswp`/像素尺寸/体积等内部参数 |
| 设置/声明 | `SettingsScreen.kt` / `LicensesScreen.kt` / `strings.xml` | 版权页（01/02 编号 + ColophonRow + 1px 线）；声明页 01–04；补声明覆盖层 BackHandler；修正"仅本应用"→"本应用 + 系统相册" |
| 预览入口 | `FilmPreviewActivity.kt` | 改用 GateSpec，避免调试入口看到旧金色 |

## 3. 验证（只到"编译 + 单测"）

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` → **BUILD SUCCESSFUL**
- 单测 **61 / 61**（6 个 suite：GalleryReconciler 12、PhotoExif 8、AppState 16、GalleryFilter 22、InteractiveIconLabels 2、NoBareFontSize 1）
- 静态检查：`ui/` 下无裸 `fontSize`（除 `ui/theme/Type.kt`）；纸面四页无可写死 `Color(0x…)`、无 `shadow`/`Brush`/渐变；未新增依赖、字体资源或权限
- **没有真机证据**：本机无设备，未安装、未截图、未跑 Accessibility Scanner。v0.4.x 的最后真机截图仍是 2026-10-21 的旧视觉。

## 4. 必须真机看的（按优先级）

1. **明暗极性**：黑闸门 → 纸面成片是否像同一个 App（转场 160ms + 8dp 上移），而不是两个应用。
2. **纸面可读性**：纸底 #ECEAE4 上 textTertiary #61676C、hairline 12%、次按钮 1px 墨线；太阳下/低亮度下是否够。
3. **取景页**：纸白刻线压在白天亮画面上是否可读；去金后是否还像一台相机；快门墨点是否仍有"可按"感。
4. **相册查看器**：黑底是否真的铺满状态栏/手势条（AppRoot 对 GALLERY 不再统一吃 insets）；返回后系统栏图标是否恢复深色。
5. **相纸页 200% 字体 / 小屏**：`PaperPageHeader` 固定 64dp + `maxLines=1` 可能裁切；成片页可滚，其它页需看是否溢出。
6. **胶卷库**：36 帧点阵在 360dp 屏的密度；`CURRENT` 黑带 + READY/FULL 双编码；纸色胶卷筒描边在 3x 屏是否过细。
7. **照片墙**：接触印相 3 列 + 帧号在 360dp 屏的可读性；帧号是"组内曝光顺序"（同款卷换卷会合组）。
8. **工具条**：3 个真实光学档位在 360dp 屏刚好排下；若某机报 4–5 档，仍可能超宽（旧版已有问题）。

## 5. 已知偏差 / 未做

- 相册查看器帧号没有持久化"卷内第几张"，用组内曝光顺序代替（组内最早=01）；这是诚实口径，不是真实卷片号。
- 卷容量 `36` 在 `AppRoot` 与照片墙各有一份常量，改容量要同步两处。
- 照片墙空态"去取景"复用 `onBack`：从成片/胶卷库进入相册时会回来源页，不是直达取景。
- 设置页没有布尔型真实设置项，所以没有硬放 `PaperSwitch`（避免假开关）。
- 横屏/旋转、200% 字体、多机型、App 内删除、真机性能仍未验证。
- `PaperPageHeader` 返回图标用 X（Close）而不是箭头；`FilmIcons` 暂无左箭头。

## 6. 回滚

```powershell
cd 'D:\Android\pocket ccd'
git checkout 1ed3ff1
$env:JAVA_HOME='D:\software\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

数据不受影响：覆盖安装保留 `Android/data/com.pocketccd.prototype/files/`；不要卸载。
