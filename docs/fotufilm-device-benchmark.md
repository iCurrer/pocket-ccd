# Fotufilm 真机基准（PJZ110）

这份文档记录 **FOT-004 / FOT-005 的真机验收数据**，以及量这些数据时踩到的三个坑。
数字全部来自 `FilmSelfTest` 写出的 `fotufilm-selftest.json`，原始文件在
`build/fotufilm-ci/fotufilm-selftest-benchmark.json`（不入库）。

## 1. 环境

| 项 | 值 |
|---|---|
| 设备 | OnePlus `PJZ110`（`OP5D0DL1`），序列号 `3B1F65E8WTX7PGAW` |
| 系统 | API 37 / Android 17 |
| ABI | `arm64-v8a`（`abilist` 只有这一项） |
| CPU | 8 核 |
| 内存 | MemTotal 11,392,100 kB（≈10.9 GiB），跑测时 MemAvailable ≈2.23 GB |
| 应用堆上限 | `Runtime.maxMemory()` = 256 MB |
| 构建类型 | **`benchmark`**（非 debuggable，debug 签名，未混淆） |
| `.so` | `libfotufilm_android.so` 5,874,424 B（CI run `39198895398`） |
| pack | FSWP v2，4000×3000，Gold200 / Portra400 / TriX400 三卷 |

## 2. 结果

单次运行、未取多次平均，所以带热漂移（见 §3.3）。

| pack | 总耗时 | 内核 | 编码 | 填充 | 峰值 RSS | 内核吞吐 |
|---|---|---|---|---|---|---|
| Gold200 4000×3000 | **1,969 ms** | 1,705 | 189 | 71 | 374 MB | 7.0 Mpix/s |
| Portra400 4000×3000 | **3,225 ms** | 2,694 | 377 | 146 | 373 MB | 4.5 Mpix/s |
| TriX400 4000×3000 | **2,805 ms** | 2,280 | 365 | 152 | 408 MB | 5.3 Mpix/s |
| Bitmap 路径（Gold200） | **2,987 ms** | 2,452 | 373 | 152 | 463 MB | 4.9 Mpix/s |

- 一律 **24 条带子**（`stripRows=128`，halo=55…62，tile 高度 238–252 行）。
- 三个 pack 的 `nonFinite` 全为 **0**。
- 峰值 RSS 373–463 MB，对比设备 10.9 GB 物理内存有充足余量；`maxHeapMb` 只有 256 MB，
  但 direct buffer 与 Halide scratch 走的是 native 堆，不占 Java 堆，所以没触发 OOM。
- 编码阶段内部拆分（Gold200）：平面拷贝 24 ms / 逐像素统计+编码 315 ms / `setPixels` 写回 25 ms。
  **逐像素那一遍是编码的全部成本**，拷贝与写回可以忽略。

**结论：12MP 一帧 2.0–3.2 秒，瓶颈是 Halide 内核（占 80–85%），不是 Kotlin 侧。**

## 3. 三个会让人得出错误结论的坑

### 3.1 debuggable 的包量不出真实性能，差 5–7 倍

第一次量是在 `debug` 构建上做的，得到「编码 1.9–2.5 秒、和内核一样贵」，于是差点去优化
`LinearColor.encode`。实际换成非 debuggable 的 `benchmark` 构建后，同一个编码阶段变成
**189–377 ms**。

| pack | debug 编码 | benchmark 编码 | 倍数 |
|---|---|---|---|
| Gold200 | 1,931 ms | 189 ms | 10.2× |
| Portra400 | 2,523 ms | 377 ms | 6.7× |
| TriX400 | 2,549 ms | 365 ms | 7.0× |

`adb shell cmd package compile -m speed -f` 强制 AOT **没有用**（重跑后 `stats` 仍是
2,500 ms 上下）—— debuggable 进程的优化限制不在 AOT 上，改 AOT 编译过滤器解决不了。

所以：**任何性能结论必须出自 `benchmark` 构建**（定义见 `app/build.gradle.kts`）。
`FilmSelfTestActivity` 对 release 直接 `finish()`，但放行 `benchmark`，否则这个入口在
唯一该用的构建里恰好是关着的。

### 3.2 屏幕熄灭会让进程被冻结，报告写不出来

前一次跑完却拿不到报告，`generatedAt` 还是上一次的。logcat 里是这一串：

```
ActivityTaskManager: Displayed com.pocketccd.prototype/.fotufilm.FilmSelfTestActivity
WindowManager: state from READY_TO_SHOW to NO_SURFACE; reason: destroySurface
OplusHansManager: freeze uid: 10464 com.pocketccd.prototype pids: [3343] scene: |StrictMode-3|LcdOff
```

屏幕自动熄灭 → 应用回到后台 → 被厂商的内存管理冻结 → 后台线程停在半路。
跑之前先 `adb shell svc power stayon true`，跑完再 `svc power stayon false` 恢复。

### 3.3 热漂移能吃掉 50%，同一 pack 两次能差 1 秒

同一个 Gold200：作为第一项跑是 **1,969 ms**（内核 1,705），作为最后一项的 Bitmap 路径再跑
一次是 **2,987 ms**（内核 2,452）。中间只隔了另外两个 pack。logcat 里也有对照：

```
OplusDexSceneManager: Abort first-use by temperature policy, threshold: 38.0
pkg: com.pocketccd.prototype
```

**所以这张表只能读作区间 2.0–3.2 s，不能读作"某卷比某卷快"。** 要比较 pack 之间的差异，
需要每卷至少 3 次、随机顺序、中间留冷却时间，这属于 FOT-011 的活儿。

## 4. 正确性证据

耗时之外，这次自测顺带证明了引擎链路是通的，不只是"能跑完不崩"：

**① 黑白卷输出 R=G=B 精确相等。** TriX400 是黑白胶卷，其输出统计三个通道
`min`/`max`/`mean` 的通道间最大差是 **0.000000**；而 Gold200 / Portra400 / example 三个
彩色卷分别是 0.1355 / 0.1366 / 0.1280。如果内核在乱出数据，不可能恰好对黑白卷给出
逐位相等的三通道。

**② Bitmap 路径输出范围合理。** 输入是"上暗下亮的渐变 + 高光点"，输出
`min≈0.03`、`max≈0.95`、`mean (0.487, 0.458, 0.400)`，既没有全黑也没有削顶到 1.0。
`sRGB ↔ 线性` 若用反，这里会表现为整体发灰或大面积过曝。

**③ 分块与整帧没有接缝。** 见下一节。

## 5. 分块几何：没有接缝，但颗粒相位跨带子不连续

用 96×64 的 pack（halo=1）拿整帧结果作参考，分别用 **8 行**和 **10 行**两种带子高度渲染，
逐行统计差异分布：

| 带子高度 | 首条带子 | 之后每一行 | 差异热点落在带子边界 |
|---|---|---|---|
| 8 行 | 第 0–7 行 **0 处差异** | 约 109 处/行 | **0 处** |
| 10 行 | 第 0–9 行 **0 处差异** | 约 109 处/行 | **0 处** |

三条推论：

1. **不是几何错**。合成纹理含 8 像素周期的棋盘，行错位 1 行会产生几十级的差异；
   实测 `maxAbsDiff` 只有 **3**，任何行/列错位都被排除。
2. **不是接缝**。接缝的特征是差异集中在带子边界那 1–2 行；实测差异是**每条带子的每一行
   都均匀存在**，两套带子高度下都没有任何一行超过中位数的 3 倍。
3. **是内核的颗粒随机状态在每次调用后推进**。只有第一次调用（第一条带子）与整帧参考
   逐位相同，之后的每次调用都拿到了不同的颗粒相位。改带子高度只是移动了分界，差异总量
   基本不变（6148 → 5987 色阶样本），这也符合"相位不同"而非"几何错"。

**这意味着**：`stripRows` 不同时，同一张照片的颗粒不会逐像素可复现；24 条带子之间颗粒相位
是不连续的。颗粒本身是高频噪声，相位跳变在肉眼上不成线，`maxAbsDiff=3`（1.2%）也低于
可察觉阈值，所以**当前不作为缺陷阻塞**，但它是一个真实的已知限制，需要在实拍样片上确认
是否出现可见的带间纹理差。已登记为 FOT-015。

同时这也说明一个测试上的局限：**parity 的 `maxAbsDiff` 容差 3 已经被颗粒相位占满**，
它不再具备探测 3 色阶级别几何误差的能力。真正在起作用的判据是"差异是否聚集在带子边界"，
也就是 `hotRowsOnSeam`，这个判据在两套带子高度下都通过了。

## 6. 尚未验证的部分

- **没有真实相机帧**。Bitmap 路径的输入是程序生成的渐变+高光图，虽然走的是
  `Bitmap → 8bit sRGB → 线性` 的真实解码链路，但不是相机出的图。真实照片的动态范围、
  噪声底、色彩分布都不同，可能改变内核耗时（内容复杂度会影响稀疏性）。
- **色彩口径只做到"线性 + sRGB 基色"**。`LinearColor.kt` 明确没做 Rec.2020 / Rec.709
  基色矩阵换算，那属于 FOT-006。在补完之前不能拿它做色彩比对。
- **Rec.2020 与非 8bit 输入**都没测。
- 单次测量，无平均值、无冷热分离（§3.3）。

## 7. 复现步骤

```bash
# 1) 构建非 debuggable 的基准包
export JAVA_HOME="/d/software/Android/Android Studio/jbr"
./gradlew :app:assembleBenchmark

# 2) 保持屏幕常亮，否则进程会被冻结（§3.2）
ADB="D:/software/Android/Sdk/platform-tools/adb.exe"
$ADB shell svc power stayon true

# 3) 安装并首次启动，让 App 自己建 pack 目录（属主必须是 App，见 §8）
$ADB install -r app/build/outputs/apk/benchmark/app-benchmark.apk
$ADB shell am start -n com.pocketccd.prototype/.fotufilm.FilmSelfTestActivity
sleep 8

# 4) 推 pack（Git Bash 必须关掉路径转换，否则 /sdcard 会被改写成本机路径）
export MSYS_NO_PATHCONV=1
for f in prebuilt/fotufilm/packs/*.fswp; do
  $ADB push "$f" /sdcard/Android/data/com.pocketccd.prototype/files/film/
done
$ADB push build/fotufilm-ci/film/example-negative-400__96x64.fswp \
  /sdcard/Android/data/com.pocketccd.prototype/files/film/

# 5) 重跑并取报告
$ADB shell am force-stop com.pocketccd.prototype
$ADB shell am start -n com.pocketccd.prototype/.fotufilm.FilmSelfTestActivity
sleep 25
$ADB pull /sdcard/Android/data/com.pocketccd.prototype/files/fotufilm-selftest.json \
  build/fotufilm-ci/fotufilm-selftest-benchmark.json

# 6) 恢复
$ADB shell svc power stayon false
```

`am start` 前必须 `force-stop`：Activity 还是同一个实例时 `am start` 只走
`onNewIntent`，`onCreate` 不会重跑，自测根本不会执行，看上去像"跑完了但报告没更新"。

## 8. pack 目录的属主

`/sdcard/Android/data/<pkg>/files/film` 如果是 `adb shell mkdir` 建的，属主是 `shell`、
模式 `drwxrws---`，App 走 FUSE 视角会被当成"其他身份"，`listFiles()` 返回 `null` ——
表现为 `packCount=0`，**而且不报错**（第一版就是这么踩的，靠 `chmod 0777` 才定位到）。

现在的做法是 `FilmSelfTest.findPacks()` 里先 `mkdirs()`，让 App 自己建目录，
属主即 App，之后 `adb push` 仍写得进去。所以**顺序很重要：先启动一次 App，再推 pack**。
