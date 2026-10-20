# Pocket CCD

一台**手机上的玩具数码相机**：打开就是全屏取景器，按下快门之后，这一帧才被"冲洗"成某个胶卷的样子。

界面是一套暗色胶片相机——近黑机身、香槟金等宽参数、金属取景器边框、按卷计数与换卷动作。
取景器里永远是**原色画面**：胶卷效果只出现在成片上，不预览、不加滤镜。

胶卷效果不是滤镜，而是一套**物理成像模型**——上游 [Fotufilm](https://github.com/DhaliwalX/fotufilm-engine)
把显影（develop）与印相（print）两段过程连同胶片/相纸的实测数据一起编译成 Halide 内核，
本仓库把这套引擎移植到 Android，并补上相机、分块渲染、方向补偿与落盘。

---

## 界面预览

<p align="center">
  <img src="docs/screenshots/readme/viewfinder.jpg" width="24%" alt="取景页：暗色胶片相机">
  <img src="docs/screenshots/readme/zoom-toast.jpg" width="24%" alt="点按变焦后的反馈">
  <img src="docs/screenshots/readme/films.jpg" width="24%" alt="胶卷库：金属面板 + 胶卷筒 + 真实画格">
</p>

左一：取景页（`PJZ110` 真机，`GOLD 200 · 4096×3072`，本卷 `5 / 36 EXP`）。
左二：点按变焦档位后的 Toast 反馈，倍率取实际生效值，不是按钮上写的值。
左三：胶卷库。整页唯一一块**反色金属面板**承载"已装卷"，下面是按卷分组的画格列表。

> 拍于 2026-10-19 / 10-20。变焦档位已由固定 `1x / 2x` 改为**该机真实光学档位 + 等效焦距**
> （一加 13 应为 `0.6× 15mm / 1× 23mm / 3.2× 73mm`，见 D-044）。
> 成片页 / 照片墙 / 设置页已重写为暗色，但这三页的真机截图仍待补（T9-018..020）——
> **没有真机截图的一律不算验证过**。

---

## 当前状态（2026-10-19 23:30）

| 能力 | 状态 | 证据 |
|---|---|---|
| 取景页（暗色胶片相机） | 完成 | 真机截图；CameraX `attached=4096x3072`，预览是真实画面 |
| 拍照 → 冲洗 → 落盘 → 系统相册 | 完成 | 真机端到端；`3072×4096 / 3.16 MB`，私有目录 + `Pictures/Pocket CCD/` 各一份 |
| 取景页交互（点按对焦 / 变焦 / 九宫格 / 闪白） | 完成 | 真机逐项验证，见 [`docs/interaction-spec.md`](docs/interaction-spec.md) |
| 成片方向 | 完成 | `capture rotation: sensor=90 display=0 -> 90°` |
| 成片页 / 照片墙 / 设置页暗色化 | **代码完成，待真机** | T9-018..020；`assembleDebug` + `AppStateTest` 12/12；无真机截图 |
| 真实镜头倍率 + 等效焦距 | **代码完成，待真机** | T9-021；D-044；镜头表尚未在 `PJZ110` 上打印过 |
| 12MP 引擎性能 | 已实测 | benchmark 包单帧 2.0–3.2 s，峰值 RSS 373–463 MB，`nonFinite=0` |
| 端到端冲洗耗时 | **超预算** | 成片实测 8.4–9.1 s，预算 2.0–3.2 s，原因未定位（B-11） |
| 胶卷库（`FILMS`，换卷页） | **代码完成，待真机** | T9-016；`AppStateTest` 15/15；卷数按实际 pack 渲染，无价格/到期日 |
| Rec.2020 色彩口径 | 未开始 | 当前为线性光 + sRGB 基色（FOT-006） |
| 冲洗进度条 / 取景框比例 | 未开始 | FOT-009 |

> 构建通过不等于已验证。未在真机执行过的项一律标 `NOT_RUN` / `BLOCKED`。
> 上面两行"待真机"就是字面意思：代码编译过、单测过，但**没人在手机上看过**。

---

## 管线：一张照片怎么变成胶片

```
[ 快门 ]
   │  CameraX ImageCapture，targetRotation 固定 ROTATION_0
   ▼
JPEG 4096×3072（传感器原始方向）                       约 3–4 MB
   │  decodeJpeg：尺寸适配到 pack 声明值
   │    相等 → 直接用；更大 → 中心裁切；更小 → 抛错（绝不缩放）
   ▼
Bitmap ARGB_8888 恰好 4096×3072                        48 MB
   │  LinearColor.decodeInto：sRGB → scene-linear RGB
   ▼
R / G / B 三个 float 平面（按行带切，不同时驻留）
   │  nativeProcessStrip × 24 条带，每条带上下各留 halo
   │  ← 参数来自 pack：config + 3×33³ LUT + mask/seed
   ▼
display-linear RGB float
   │  LinearColor.encode：linear → sRGB（4096 级查找表 + 插值）
   ▼
ARGB 像素 → Bitmap.setPixels
   ▼
Bitmap 4096×3072                                       48 MB
   │  PhotoStore：Matrix.postRotate(90°)，随后立即 recycle 原图
   ▼
3072×4096 → JPEG q92（约 3 MB）+ 1440 长边缩略图
   ▼
files/Pictures/pocket-ccd/{full,thumb}/   ← 照片墙数据源（权威副本）
   │  publishToGallery：文件流复制，不重新编码位图
   ▼
系统相册 Pictures/Pocket CCD/            ← 分享 / 查看 / 备份都走这里
```

五件容易被误解的事：

1. **取景器不做实时滤镜**（D-036）。预览就是普通相机画面，胶卷效果只出现在成品上。
   否则一台"玩具相机"会变成一台永远开着滤镜的普通相机。
2. **引擎的输入输出都是线性光，不是 sRGB**。Android AOT 的 5 个 kernel 都不是
   `ENCODE_OUT` 变体，上游注释写明 "other variants return display-linear light"，
   所以 sRGB 编解码必须由 Kotlin 侧完成（`LinearColor`）。
3. **冲洗之后才旋转**。pack 的空间参数按 4096×3072 烘成像素数，先转成竖图会直接失败；
   旋转放在效果烘焙之后，颗粒与晕光的尺度不受影响。
4. **尺寸只会被裁，不会被缩**。pack 的空间参数是「毫米 × 该帧 pixels-per-mm」，
   裁切不改像素密度所以依然成立；缩放会改密度，等于换了一套光学系统（D-037）。
   多机型适配全靠这一条 —— 一份 pack 服务多种设备的唯一正确做法，
   详见 [`docs/fotufilm-camera-capture.md`](docs/fotufilm-camera-capture.md) §2.2。
5. **成片写两份**。私有目录是权威副本（照片墙只读这里），系统相册是给外面看的。
   `Android/data/` 被媒体扫描明确排除，只写它等于"拍完只有本 App 看得到"。
   详见 [`docs/fotufilm-camera-capture.md`](docs/fotufilm-camera-capture.md) §10。

### 为什么必须分块（halo 不能省）

4096×3072 的单精度三平面约 **144 MB**，输入输出各一份就是 288 MB，再加 Halide 自己的
scratch。目标机（12GB 内存）常态只剩 2.4GB 可用，整帧做法会直接撞 LMK。

所以按 **128 行一条带子**处理。但扩散、晕光、MTF 都是有物理半径的卷积——一条带子
如果只看自己那 128 行，接缝处会明确断裂。halo 由 `FilmPack.maxHalo` 给出，
它在 native 侧用与内核**同一个** `ResolvedFrameParams` 推导，不是宿主侧重算的第二套公式。

实测：用 8 行与 10 行两种带高对比整帧，`maxAbsDiff=3/255`，`hotRowsOnSeam=0`。
真实差异来自**每次内核调用会推进颗粒随机状态**，登记为 FOT-015 待实拍确认。

---

## 界面与交互

### 打开即取景（D-039）

没有首页。`AppDestination` 里 `CAMERA` 就是根，启动直接进全屏取景器，返回键交给系统 `finish`。
代价是首次启动即申请相机权限——被拒绝时取景器显示空态，而不是把用户挡在一个假首页后面。

目前的五个目的地：

| 目的地 | 页面 | 状态 |
|---|---|---|
| `CAMERA` | 全屏取景器（根） | 已接管默认入口 |
| `RESULT` | 成片页：大图 + 真实 EXIF 参数网格 + `FRAME n / 36` | 暗色版代码完成，待真机 |
| `GALLERY` | 照片墙：按胶卷分组的 3 列缩略图 | 暗色版代码完成，待真机 |
| `FILMS` | 胶卷库：已装卷大卡 + 全部卷列表 + 装卷（T9-016） | 代码完成，待真机 |
| `SETTINGS` | 设置页（原型没有此页，按同一套令牌自绘） | 暗色版代码完成，待真机 |

取景页的卷名 pill 点进去就是 `FILMS`——它本来就带 `ChevronRight`，
之前却被接成"点一下切到下一卷"，卷多了要连点好几次才知道切到哪。现在改为进库挑选。

### 取景页里哪些是真的

- **参数 chip 只写能读到的值**：`LIVE`、`12.6MP`、`4:3`。设计稿上的曝光三要素
  （`1/125 · f/2.8 · ISO 400`）在 CameraX 里拿不到，所以不存在。
- **变焦档位来自设备本身**（D-044）：`getPhysicalCameraInfos()` + `getIntrinsicZoomRatio()`
  读出该机真实光学档，等效焦距按 `f × 43.2666 ÷ 传感器对角线` 现算。一加 13 通常给出
  `0.6× 15mm / 1× 23mm / 3.2× 73mm`。探不到物理镜头（单摄或厂商裁掉）就退回纯数码档，
  **不显示任何 mm**。
- **`3.2×` 的含义是"73mm 那颗镜头的倍率"，不是"当前帧一定来自它"**。没有公开 API 能查询
  当前成像的物理镜头，系统 HAL 自己决定。要自证只能读成片 EXIF 的 `FocalLength`
  （主摄约 6mm、长焦约 16mm），成片页把它当真实参数展示。
- **卷计数是真的**（D-040）：`PhotoStore.rollStartedAt()` 记录本卷起点，`capturedAt` 晚于它的
  才计入，满 36 张禁快门。**绝不能用照片库总数冒充本卷张数**——那是两回事。
- **对焦标签三态**：`AF` / `AF LOCK` / `AF FAIL`。原型永远写 `AF LOCK`，这里按
  `startFocusAndMetering` 的真实回调切换。

### 复刻时丢弃的东西

设计稿是从四页 HTML 原型里解出来的（归档在 [`docs/reference/pages/`](docs/reference/pages/)），
但只复刻排版语言，不复刻数据（D-043）。明确不做的：

| 原型内容 | 为什么不落 |
|---|---|
| `6000×4000 · DNG` | 本机只出 JPEG，`4096×3072`；写别的尺寸就是撒谎 |
| `¥78` / 到期日 / 品牌故事 / `ISO 400` / C-41 分类（胶卷库） | 价格与保质期无处可查；pack 格式里没有 ISO 与工艺字段，从卷名反推属于猜 |
| `冲扫中 72%`、`SCANNED BY 光屋 LAB`、订单号与物流 | 本地 8 秒即时冲洗，没有订单、没有进度源（D-042） |
| 预览上的实时 `sepia/saturate/hue-rotate` | 与 D-036 冲突；预览保持原色，只留一层不碰像素的取景器暗角（D-041） |
| `scale(1.28)` 假变焦 | 改用 `CameraControl.setZoomRatio`，并把**实际生效倍率**回报给 UI |

---

## FSWP pack 是什么

一个 pack 就是"一卷胶卷 + 一种相纸 + 一次曝光"的全部参数，纯小端序列化：

| 段 | 长度 | 内容 |
|---|---|---|
| 头 | 10 × int32 = 40 B | `FSWP` magic / 版本 / width / height / featureMask / seed / 4 个段长 |
| config | 18271 × float | 光学与工艺参数（扩散、晕光、MTF、颗粒、耦合…） |
| exposure | 143748 × float | 33³×4，曝光 LUT |
| film | 143748 × float | 33³×4，胶片 LUT |
| paper | 143748 × float | 33³×4，相纸 LUT |

- 后三段在读取时**补零到 147456**，与上游 Vulkan 侧的绑定长度、以及 `tools/vulkan-parity/fixture.h`
  的读法逐字节一致。
- `config` 的长度会与编译期常量 `FOTUFILM_FRAME_CONFIGURATION_COUNT` 比对，不一致就拒绝
  加载——这是 pack 与引擎版本不匹配最早的报警点。
- 12MP 的 pack 尾部还多 36–42 KB 附加段。顺序读取不受影响（总长校验用 `>=`）。

**一个 pack 只对头部记录的尺寸有效**（D-037）。扩散半径是"毫米 × 该帧 pixels-per-mm"，
换尺寸等于换了一套光学系统，所以绝不缩放。

---

## 开发与编译原理

### 技术栈

| 层 | 选型 | 备注 |
|---|---|---|
| UI | Kotlin 2.3.21 + Compose BOM 2026.08.00 | Material3；除 Compose 外没有第三方 UI 库 |
| 构建 | AGP 9.3.3 + Gradle 9.6.1 + JDK 21（Java target 17） | AGP 9 的 `compileSdk { version = release(37) }` 是新 DSL，与旧写法不兼容 |
| SDK | compileSdk 37 / targetSdk 36 / minSdk 29 | 目标机 API 37 |
| 相机 | CameraX 1.6.2 | 只用 Preview + ImageCapture，不引 video / extensions |
| 引擎 | Halide AOT（LLVM 22）+ NDK 28.2.13676358，arm64-v8a | 运行期不做 JIT，手机端只加载 `.so` |

### 三条构建链

一件事必须由三个不同的机器完成，不是随意分工：

| 产物 | 在哪跑 | 为什么不能换地方 |
|---|---|---|
| `*.fswp` pack | macOS（CI `packs` job） | `--dump-wasm-pack` 在 `#if canImport(ImageIO)` 块内，Linux 上整段被替换成 `fail("Image I/O requires macOS")`；上游 CI 也是 `macos-26` |
| `*.a` AOT kernels + `libfotufilm_android.so` | Linux（CI `android-native` job） | 需要完整 Halide（含 LLVM 22）构建，以及 NDK 交叉编译 |
| APK | 本机 Windows + Android Studio | 只消费 `prebuilt/`，不参与 native 编译 |

### 1. pack 生产（macOS）

```bash
swift build -c release --product fotufilm
fotufilm --dump-wasm-pack <out>.fswp --stock gold200 --pack-size 4096x3072
```

CI 里由 `.github/workflows/fotufilm-android-native.yml` 的 `packs` job 执行，`pack_size`
默认值取自目标机实测（见下文 D-037 一节）。pack 内容纯参数序列化，与平台无关，
所以能在 macOS 生成、在 Linux 与手机上下载使用。

### 2. AOT 内核与 `.so`（Linux）

`android-native` job 做四件事：

1. 按 pin 的 commit checkout 上游（`2d07c48c…`），子模块 Halide 一并拉齐；
2. 从源码编 Halide，`tools/generate_halide_android.cpp` **不传 `--host`** = 真 arm64 Android 目标，
   产出 5 个 `*.a`；
3. 跑上游 `tools/verify-android-aot.sh` 做 parity（64 个用例）。该脚本要调 CLI 生成
   `example.fswp`，而那条命令在 Linux 上是 `fail()` —— 用 `.github/scripts/fotufilm-pack-shim.sh`
   把请求解析到 macOS job 产出的同名 pack（pack 跨平台一致，所以这是等价替换）；
4. 交叉编译 `.so`，并做两条硬断言。

`native/CMakeLists.txt` 只做链接，不掺 Swift、不掺 Metal：把 5 个 AOT kernel 归档 +
上游 `FotufilmHalideAndroid.cpp` + `fotufilm_jni.cpp` 链成一个 `.so`。三个路径全部由调用方注入，
不写死绝对路径：

```
-DFOTUFILM_ROOT=<上游源码根，含 Sources/FotufilmHalide>
-DFOTUFILM_KERNELS=<generate_halide_android 的输出目录，含 *.a 与 *.h>
-DHALIDE_INCLUDE=<Halide 安装目录>/include
```

几个看起来多余、实际都救过命的开关：

| 开关 | 为什么 |
|---|---|
| `find_path(... NO_CMAKE_FIND_ROOT_PATH)` | NDK 的 toolchain 设了 `CMAKE_FIND_ROOT_PATH` 且 `MODE_INCLUDE=ONLY`，会把绝对路径**重新根化**成 `<sysroot>/home/runner/...`，导致头明明在磁盘上却一律落空——而 `file(GLOB)` 不受影响，于是症状是"`.a` 找得到、头找不到" |
| include 顺序：上游在前、生成目录垫底 | 生成目录里若也躺着一份同名头，顺序一变就会安静地选中它 |
| `-ferror-limit=0` | 默认 20 条；头文件层面出问题时，第一条真错误常被后续连带错误挤出前几屏，网页日志被截断后就再也找不到 |
| `-Wl,--no-undefined` | 宁可在链接期失败，也不要产出一个 `loadLibrary` 时才炸的 `.so` |
| `-fvisibility=hidden` | 只导出 `JNIEXPORT` 的 9 个符号，不把 Halide 内部符号泄出去 |

CI 对 `.so` 的两条硬断言（缺一条就可能"编出来了但手机加载不了"）：

```
llvm-readelf -h  → Machine: AArch64
llvm-nm -D       → 9 个 JNI 符号一个不少，且没有未解析的 halide_* 符号
```

`llvm-readelf -d` 的 NEEDED 只打印、不断言；链接用 `c++_static`，所以这个 `.so`
不依赖 `libc++_shared`。

9 个符号：`nativeAbiVersion` `nativeAvailable` `nativeOpenPack` `nativeReleasePack`
`nativePackInfo` `nativeMaxHalo` `nativeProcess` `nativeProcessStrip` `nativeLastError`。

> 曾经这里只断言了 8 个，漏掉 `nativeAvailable`；也曾经因为
> `std::vector<uint8_t> raw(size_t(length));` 被 C++ 解析成函数声明（most vexing parse）
> 而整轮红掉。两处都写在代码注释里。

### 3. APK（Windows）

`prebuilt/` 下放 CI 的产物，Gradle 只做一件事：把这个目录挂成 `jniLibs`。

```kotlin
sourceSets.getByName("main") {
    jniLibs.directories.add(rootProject.layout.projectDirectory.dir("prebuilt/fotufilm/lib").asFile.absolutePath)
}
```

放 `prebuilt/` 而不是 `build/`，因为 `./gradlew clean` 会清掉 `build/` 整个目录，
而重新产出一次要十几分钟。`prebuilt/` 本身被 `.gitignore` 忽略：体积、以及它们是
Apache-2.0 代码与 CC BY-SA 胶片数据的派生结果（分发口径见
[`docs/fotufilm-license-inventory.md`](docs/fotufilm-license-inventory.md)）。

目录不存在时构建**仍会成功**，只是运行时 `nativeAvailable()` 为 false、胶片功能自行降级。
这样干净克隆也能编过，代价是要拍胶片得先补 `prebuilt/`。

另有一个 `benchmark` 构建类型（非 debuggable、debug 签名、不混淆）。它存在的唯一理由：
debug 包是 debuggable 的，ART 不做完整优化，同一段编码循环实测慢 **5–7 倍**
（1931 ms vs 189 ms）。拿 debug 包的耗时当性能结论会得出完全错误的判断。

---

## 快速开始

前置：Android SDK、JDK 21、项目自带 Gradle wrapper（9.6.1）。

```bash
export JAVA_HOME="/d/software/Android/Android Studio/jbr"

./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 状态机 12 例
./gradlew assembleBenchmark      # 性能基准用（非 debuggable）
```

国内网络：`settings.gradle.kts` 优先用阿里云 Maven 镜像，Gradle wrapper 走腾讯云。
某个依赖仍解析失败时再单独调整，不要一次性堆叠镜像。

### 让相机能拍照

APK 只解决"能跑"，能拍照还需要**与这台设备分辨率精确相等的 pack**。pack 不进 APK，
也不进仓库：尺寸随设备最大 JPEG 变（换机型就要重产），且它是上游代码与 CC BY-SA 胶片数据的
派生结果，分发口径受限。首次补齐（`prebuilt/` 为空时）见
[`docs/github-actions-fotufilm.md`](docs/github-actions-fotufilm.md)，然后放进设备私有目录：

```bash
adb push prebuilt/fotufilm/packs/*__4096x3072.fswp \
  /sdcard/Android/data/com.pocketccd.prototype/files/film/
adb shell am force-stop com.pocketccd.prototype
```

`FilmStockCatalog` 会扫描该目录，并在相机绑定后自动选中尺寸精确匹配的卷。
目录由 App 自己 `mkdirs()` —— 用 `adb shell mkdir` 建的目录属主是 `shell`、模式 `drwxrws---`，
App 透过 FUSE 列不出内容且**不报错**（表现为 `packCount=0`）。

验证：

```bash
adb logcat -s PocketCcdCamera -s PocketCcdCapture      # 字段有节略
# camera bound: lens=1 target=null attached=4096x3072 effective=4096x3072 sensorOrientation=90 …
#   ↑ 首绑不约束尺寸（target=null），attached 才是真实值；下一次绑定才会带上 target=4096x3072
# capture rotation: sensor=90 display=0 -> 90°
# developed PORTRA 400 4096x3072 in 2xxx ms / 24 strips / nonFinite=0 / rotate=90°
```

---

## 目录结构

```
app/src/main/java/com/pocketccd/prototype/
├── camera/          CameraPreview（CameraX，只交出 CameraHandle）
│                    PhotoStore（全尺寸 + 缩略图，临时文件 rename；卷起点）
│                    LensInfo（物理镜头倍率 + 等效焦距）
│                    PhotoExif（读成片 JPEG 的焦距/光圈/快门/ISO）
│                    CaptureState / CapturedFrame
├── fotufilm/        FotufilmNative（9 个 external 逐条对齐）
│                    FilmPack（FSWP 解析）/ FilmStock（扫描卷）
│                    FilmEngine（分块渲染）/ LinearColor（sRGB ↔ linear）
│                    FilmSelfTestActivity（真机基准入口）
├── navigation/      AppViewModel（冲洗在 Dispatchers.Default 上）
└── ui/              AppRoot（相机句柄/变焦/闪白/Toast 的唯一真相）
                     film/（FilmTheme 令牌、手绘图标、取景器、控件、交互原语）
                     film/films/（胶卷库：已装卷大卡 + 全部卷列表）
                     result/ gallery/ settings/

app/src/test/        AppStateTest（导航状态机 12 例）
native/              fotufilm_jni.cpp + CMakeLists.txt（只链接，不处理色彩）
.github/workflows/   fotufilm-android-native.yml（packs + android-native 两个 job）
prebuilt/            CI 产物（.gitignore；gradlew clean 不会删）
docs/                管线、基准、许可、构建细节、UI 规格、交互对照
docs/reference/      设计稿归档（HTML 原件与四页）
docs/screenshots/    真机截图（迭代过程全留）；readme/ 是裁剪压缩后给文档用的
```

---

## 硬约束

- **D-037**：pack 尺寸 = 采集尺寸，精确相等，不允许缩放。
- **D-036 / D-041**：预览永远是原色。取景器上只有一层**不碰任何像素**的暗角，
  不套 `ColorMatrix` 近似色调——想看这卷拍出来什么样，只能看成片页。
- **D-044：不做物理镜头绑定**。pack 的扩散/晕光/颗粒半径按「毫米 × 该帧 pixels-per-mm」
  烘焙；主摄 1/1.4" 装 50MP 约 890 px/mm，长焦 1/1.95" 装 50MP 约 1240 px/mm，
  换物理镜头等于换像素密度，颗粒与晕光半径会小约 29%。`setZoomRatio` 走逻辑相机，
  像素密度恒等于主摄，pack 才成立。
- **首绑不约束尺寸**。先不传 `targetSize` → 读 `attachedSurfaceResolution` → 只下发已被
  证明支持的尺寸。反过来会把会话锁死在"最接近的更高档"，之后没有任何卷能匹配。
- **目标机实测**：`PJZ110`（OnePlus 13）后置镜头 CameraX 最大 JPEG 是 **4096×3072**，
  没有 4000×3000。请求 4000×3000 会被 `CLOSEST_HIGHER_THEN_LOWER` 换掉。
- **pack 基准尺寸要尽量低**：基准越高，能裁出它的机型越少。`4000×3000` 的覆盖面
  比 `4096×3072` 大得多 —— 后者会把所有正好 4000×3000 的机型直接排除，
  而前者只在 4096×3072 的机器上裁掉 1.5% 线性视野（详见 docs §2.2）。
- **方向**用 `CameraCharacteristics.SENSOR_ORIENTATION` 算，不用 CameraX 的
  `sensorRotationDegrees`（后者在 `targetRotation = ROTATION_0` 时返回 0，与实测矛盾）。
- 未实际执行的构建/测试/真机拍摄，不得标记为"通过"。

---

## 故障排查

| 现象 | 原因 |
|---|---|
| 快门置灰，提示`「GOLD 200」需要 W×H，这台设备只能给 A×B` | 采集尺寸小于 pack，裁都裁不出来；换小尺寸的 pack |
| 成片比取景器窄一点 | 发生过中心裁切（设备尺寸大于 pack）；日志里有 `→ crop WxH` |
| 卷名显示 `EXAMPLE 400` | CI 的 parity 夹具（96×64）被当成了卷；已有 1 MP 地板挡住 |
| `原生内核不可用：…` | `.so` 没打进 APK，或 ABI 不是 arm64-v8a |
| 冲洗 2–3 秒没进度、只有状态点 | 设计如此，FOT-009 待做 |
| 成片侧躺 | `SENSOR_ORIENTATION` 读不到（会打 warn 并跳过补偿） |
| 冲洗完没有文件 | 看 `PocketCcdCapture` 的 `capture failed`，通常是设备采集尺寸小于 pack |
| 耗时比文档慢 5–7 倍 | 在用 debug 包；换 `assembleBenchmark` |
| 按快门没反应，但 `LIVE` 还亮着 | 相机会话被系统或别的 App 抢走，`takePicture` 抛 `Not bound to a valid Camera`。CameraX 没有 `isBound`，探针是 `attachedSurfaceResolution` 是否抛异常；探到就自增 nonce 重绑 |
| 装完新包界面还是旧的 | `adb install -r` 之后没 `am force-stop`，`am start` 把内存里的旧进程拉回前台。必须 install → force-stop → `am start -W`（确认 `COLD`）→ 截图 |
| 截图里按钮点了没反应 | adb 截图是 1440×3168，看图时被缩放显示；量出来的坐标要换算回原分辨率再 `input tap` |

---

## 文档索引

| 文件 | 用途 |
|---|---|
| [`DEVELOPMENT_SPEC.md`](DEVELOPMENT_SPEC.md) | 产品意图与技术方向的基线 |
| [`DECISION_LOG.md`](DECISION_LOG.md) | 技术决策及理由（D-037、D-036 等） |
| [`TASK_BOARD.md`](TASK_BOARD.md) | 任务、前置、验收、证据 |
| [`PROJECT_STATE.md`](PROJECT_STATE.md) | 当前进度、阻塞、下一步 |
| [`TEST_PLAN.md`](TEST_PLAN.md) | 测试层级与性能协议 |
| [`docs/fotufilm-camera-capture.md`](docs/fotufilm-camera-capture.md) | 相机链路、D-037 的三道闸、方向补偿、手测清单 |
| [`docs/fotufilm-device-benchmark.md`](docs/fotufilm-device-benchmark.md) | 真机耗时/内存/正确性数据 |
| [`docs/github-actions-fotufilm.md`](docs/github-actions-fotufilm.md) | CI 两个 job 的触发与 artifact 说明 |
| [`docs/fotufilm-license-inventory.md`](docs/fotufilm-license-inventory.md) | 上游与胶片数据的许可口径 |
| [`docs/fotufilm-windows-build.md`](docs/fotufilm-windows-build.md) | WSL2 本地构建（备选路径） |
| [`docs/film-dark-ui-spec.md`](docs/film-dark-ui-spec.md) | 暗色胶片相机 UI 规格（M9 基线：令牌映射、组件方案、已知偏差） |
| [`docs/interaction-spec.md`](docs/interaction-spec.md) | 原型四页逐条交互对照（原型做法 / Compose 实现 / 能不能做） |
| [`docs/screenshots/`](docs/screenshots/) | 真机截图，按时间命名，迭代过程全留 |

### 证据标记

`[F]` 已核实事实 ｜ `[I]` 工程推断 ｜ `[H]` 待验证假设 ｜ `[D]` 决策

---

## 边界

- 不实现实时滤镜、不把预览伪装成成片；
- 不虚构胶卷价格、到期日、冲扫进度、订单号——排版可以借，数据必须能查；
- 变焦档位不冒充"当前帧就来自这颗镜头"，只声明它是这颗镜头的倍率；
- 不引入账号、云同步、分析 SDK，不上传任何照片；
- 不使用真实相机品牌 Logo/机型名作为产品名；
- 不把未验证的成像结论写成事实；
- 当前包名 `com.pocketccd.prototype` 为临时值，禁止发布。
