# 其他机型适配与分发方案

**日期：** 2026-10-20
**目标：** 把 Pocket CCD 从"只在 `PJZ110` 上能拍的开发机产物"变成"别人装上就能拍"的 APK。
**触发：** 「其他手机怎么办呢 / 其他手机安装了也能用」

---

## 0. 结论先行

现在这一个 APK 拿给别人装，会撞上**两道硬墙**，只修一道没用：

| 关卡 | 现状 | 后果 | 状态 |
|---|---|---|---|
| **① pack 不在包里** | pack 只能 `adb push` 到 `Android/data/<pkg>/files/film/` | 胶卷库 0 卷 → 快门永久置灰，等于装了个打不开的相机 | **本次已修**：pack 内置进 assets，首启自动落盘 |
| **② pack 基准尺寸太窄** | 曾是 `4096×3072` | 最大 JPEG 为 `4000×3000 / 4080×3060` 的机型（12MP 主流档）**一张也拍不出来** | **已修**：pack 重产为 `4000×3000`（2026-10-20 22:52 换入 `prebuilt/`） |
| ③ ABI | 只有 `arm64-v8a` | 2016 年前的 32 位老机装不上/装了没有内核 | 暂不处理（minSdk 29 已经挡掉绝大多数） |
| ④ 签名 | 只有 debug 签名 | 能装，但带 `android:debuggable`，部分系统的"未知来源"提示更凶 | 用 `benchmark` 变体分发（见 §5） |

一句话：→ **两道墙都已拆掉，并且真机验证过**（2026-10-20 深夜，`PJZ110`）：
装 `app-benchmark.apk` → 冷启动 3 卷 → 快门 → 成片 `3000×4000` 正立、进系统相册。
剩下的 ③④ 只影响覆盖面（32 位老机 / 签名提示），不影响"能不能拍"。

---

## 1. 机型分档与 pack 基准尺寸

pack 里的扩散 / 晕光 / 颗粒半径是「毫米 × 该帧 pixels-per-mm」烘成的常数（D-037）。
所以一份 pack 只能服务「采集尺寸 ≥ 它」的机型，多出来的部分中心裁切 —— **裁切不改
像素密度，缩放会**。这条约束决定了下面的表：

| 设备最大 JPEG | 典型机型 | 对 `4096×3072`（现在） | 对 `4000×3000`（建议） |
|---|---|---|---|
| `4096×3072` | 一加 13 / `PJZ110`（实测） | 精确 | 中心裁切，丢 1.5% 线性 |
| `4000×3000` | 12MP 名义档，**极常见** | **太小，拍不了** | 精确 |
| `4080×3060` | 部分华为 / 荣耀 | **太小，拍不了** | 中心裁切，丢 ≈1% |
| `4160×3120` | 13MP 档 | **太小，拍不了** | 中心裁切，丢 ≈2% |
| `4608×3456` | 16MP | 裁 11% | 裁 13% |
| `3264×2448` | 8MP 老机 | **太小** | **太小**（需要第二档） |
| 前置 / 超广角 | 普遍 ≤ `2560×1920` | **太小** | **太小** |

**结论：单份 pack 的最优基准是 `4000×3000`。** 它比 `4096×3072` 覆盖面大得多，
代价只是 `PJZ110` 这类 12.58MP 机型裁掉 1.5% 线性 —— 肉眼不可见。

`PJZ110` 自己会从"精确"退化成"裁切"，但这是**为分发必须付的税**：只服务一台机器的
pack，装到别的机器上一张都拍不出来。

> 表里的"典型机型"是按公开传感器规格归纳的，不是逐机型实测。新机型上手前仍应跑
> `adb shell dumpsys media.camera | grep -i "jpg\|jpeg"` 或看
> `adb logcat -s PocketCcdCamera` 的 `attached=` 一行。

---

## 2. 本次已落地的改动（不需要新 pack）

### 2.1 pack 内置进 APK，首次启动落盘

```
app/build.gradle.kts
  sourceSets.main.assets.srcDir("prebuilt/fotufilm/packs")
        ↓ 打包
APK/assets/*.fswp                    （3 个 pack 约 5.5 MB）
        ↓ 首次启动 / 重装后首次启动
FilmStockCatalog.provisionBundled()  → files/film/*.fswp
        ↓
FilmStockCatalog.scan()              → 胶卷库
```

几个刻意的设计：

- **运行期仍以 `files/film/` 为唯一事实来源**，assets 只是初装种子。开发者照旧可以
  `adb push` 覆盖，用户也可以换自己的 pack。
- **用 `bundled_packs_v1.txt` 记账**（每行 `文件名 字节数`）。下一版 pack 换了尺寸时，
  旧的内置文件会被清掉 —— 否则胶卷库里会永远多出几支"这台机器拍不了"的卷。
  只清**自己复制过**的，`adb push` 进来的不在账上，不会被误删。
- **同一支卷只留一个变体**。换尺寸时，升级上来的机器目录里还躺着上一版 `adb push` 的
  旧尺寸（不在清单上，上面那条清不掉），两支 `gold200` 并存会让胶卷库出现两个
  "GOLD 200"。所以再加一条：若某支卷已有内置变体，则删掉同支卷的其它变体
  （`__` 前的部分相同）。必须有内置变体顶上才删，避免删掉唯一一份。
- **先写 `.tmp` 再 `rename`**：中途被杀不会留下半个 pack 被 `scan()` 读到。
- 落了几个文件记一条日志：`FilmStockCatalog: 内置 pack 补齐 3 个，共 3 个`。

### 2.2 许可义务（不是可选项）

pack 是 film profile 的改编，按 **CC BY-SA 4.0** 分发（`docs/fotufilm-license-inventory.md` §2）。
内置进 APK 之后必须：

- 署名 **Fotufilm** + 链接 https://fotufilm.com，保留 **MUAStudio Inc.** 版权声明；
- 说明"pack 由 profile 生成，未修改参数"；
- 不得用技术手段阻止用户取出/替换这些文件（所以 `files/film/` 必须保持可写、可读）。

**已补（2026-10-20）**：设置页 → 关于 → 开源与素材声明（`ui/settings/LicensesScreen.kt`）。
四段内容：署名与修改声明、`files/film` 的实际路径与「可取出可替换、不加密不校验」、
第三方组件（Halide MIT / AndroidX·CameraX·Compose Apache-2.0 / rawtoaces / colour-science）、
商标与「渲染结果不受 ShareAlike 约束」。文案走 `stringResource`，措辞对照
`docs/fotufilm-license-inventory.md` §5。

### 2.3 顺带修掉的三条数据丢失路径

来自 `docs/reviews/2026-10-20-main-chain-review.md` 的 P0-1/2/3。它们和"给别人装"
直接相关：别人是**新装**，但只要他从旧版本升级、或者 MediaStore 抖一下，照片就会没。

| 编号 | 问题 | 修法 |
|---|---|---|
| P0-1 | 补传与对账并发，历史照片在补传进相册**之前**被判成"用户已删除"，且补传标记照样置位 → 永不恢复 | 对账开头加安全阀⓪：`gallery_backfill_done_v1` 为 false 时直接返回 `queryOk=false` |
| P0-2 | 空游标 = 清库：注释承诺"查询失败/返回空游标 → 整批不删"，代码只挡了 `null` | `GalleryReconciler.staleIds` 在索引全空时返回空列表；`PhotoStore` 空索引时 `queryOk=false` |
| P0-3 | 补传失败的照片不记 `gallery_missing_ids`，下一轮对账即删唯一副本 | 补传循环里每张都 `markPublished(id, published != null)` |

单测从 27 例涨到 **28 例**（`GalleryReconcilerTest` 11 → 12，`AppStateTest` 16 不变），
其中原来那条「空相册索引会删掉全部已发布的旧照片」断言的是**错误行为**，已改写成
「空相册索引一张都不删」，并补了一条"只有时间戳没有文件名时仍按时间判活"。

**这条的取舍要记住**：用户真把相册里本 App 的照片全删了，照片墙**不会**跟着清空。
宁可留一张幽灵照片，也不要清库。要彻底解决只能做 App 内删除（见 §6）。

---

## 3. 待办：重产 `4000×3000` 的 pack（需要你在网页点 CI）

本机产不了 pack：`fotufilm --dump-wasm-pack` 在 `main.swift` 里包在
`#if canImport(ImageIO)` 内，只有 macOS 有 ImageIO。

> **状态：已执行（2026-10-20 22:52）。** 用户下载的 `fotufilm-packs (2).zip` 里三份 pack
> 头部逐份校验为 `FSWP v2 4000x3000`（`mask` 0x2043e / 0x2063e / 0x200b6，`lut=33/143748`），
> 已替换 `prebuilt/fotufilm/packs/`，旧的 `__4096x3072` 三份备份在
> `build/artifacts/packs-4096x3072/`（`build/` 不进版本库）。下面的步骤留作下次换尺寸时照做。

**步骤（在 GitHub 网页操作，本机没有 `gh`）：**

1. Actions → `Fotufilm Android native POC` → Run workflow
2. `stocks` 保持 `gold200 portra400 trix400`
3. `pack_size` 填 **`4000x3000`**
4. 跑完（45–90 分钟）下载 artifact `fotufilm-packs`，解压得到 3 个
   `xxx__4000x3000.fswp`
5. 放进 `prebuilt/fotufilm/packs/`，**删掉**旧的 `__4096x3072` 三个
6. 重新 `assembleBenchmark` → 这就是发给别人的包

第 5 步的"删掉"很重要，但要理解它只会影响**你自己的机器**：别人的 `files/film/`
是这次新落盘的，只会有包里带的那些。你机器上已有的 `__4096x3072` 由清单清理掉。

> 如果暂时不想动 `PJZ110` 的精确匹配，可以两条路并行：本次先只发 `4000×3000`，
> 后面按 §4 做"多档 pack"，让同一台机器自动挑最合适的一档。

---

## 4. 可选：多档 pack（覆盖 8MP 老机与超广角）

单档 `4000×3000` 之后，还剩两批机器拍不了：8MP（`3264×2448`）和前置/超广角。
要覆盖它们，只能**再产一套更小的 pack**，绝不能运行时缩放。

代价是胶卷库会出现"同一支卷的两个尺寸"。真要做得体面，需要把
**「胶卷身份」和「pack 变体」拆开**：

- `FilmStock.filmId = id.substringBefore("__")`（`gold200`、`portra400`、`trix400`）
- 选中卷记 `filmId`；实例化时按「采集尺寸能服务」的变体里**面积最大**的那个
- 卷计数 / 胶卷库画格 / 照片落盘命名都按 `filmId` 归组（现有 `film_<ts>_<stockId>.jpg`
  里的 `stockId` 解析出 `filmId` 即可，老照片不需要迁移）
- 胶卷库只列当前设备能服务的变体，其余隐藏

代码量约 100 行，跨 `FilmStock` / `AppViewModel` / `AppRoot` / `FilmLibraryScreen`。
**在没有第二套 pack 之前不建议先做**——没东西可测。

---

## 5. 分发形态

| 方式 | 做法 | 适合 |
|---|---|---|
| **直接发 APK**（现在唯一可行） | 微信/QQ 传文件 → 手机上点开 → 允许"安装未知应用" | 几个朋友、自用备份 |
| 应用商店 | 需要正式 keystore + 隐私政策 + 软著 + 通过 CC BY-SA 资产审查 | 以后，先别想 |
| Google Play | 同上，且 targetSdk 要求逐年抬高 | 不推荐（用户群在国内） |

**发之前的三件事：**

1. 用 `assembleBenchmark` 的包（release 变体 + debug 签名，`debuggable=false`）。
   它比 debug 包小、也不带调试开关，装机体验正常。
2. 装完首启会往 `Android/data/com.pocketccd.prototype/files/film/` 落 3 个 pack（约 5.5 MB），
   **卸载会一并清掉**，重装重新落。
3. App 名/图标仍是 `Pocket CCD` 原型名，`applicationId = com.pocketccd.prototype`。
   给别人长期用之前应该改成正式包名（改了等于换 App，已拍的照片会留在旧包名下）。

> 已知摩擦：MIUI / ColorOS / HarmonyOS 对"未知来源 APK"有额外开关（"安装未知应用"
> 授权给微信/文件管理器），装不上时先查这个，不是包的问题。

---

## 6. 新机型上手验收清单

拿到一台新手机，按顺序跑，任何一步不过先别改代码：

| # | 动作 | 期望 | 不过时看什么 |
|---|---|---|---|
| 1 | `adb install -r app-benchmark.apk` | 装上 | ABI（`arm64-v8a` 是否存在）、minSdk（< 29 装不上） |
| 2 | 打开 App，允许相机 | 直接进取景器（D-039） | 权限页文案、`ON_RESUME` 重查（review P1-15） |
| 3 | `adb logcat -s PocketCcdCamera` | `camera bound: ... attached=WxH ... sensorOrientation=90/270` | 尺寸协商失败会打 `requestedSizes` 相关错误 |
| 4 | 看 `attached` 是否 ≥ pack | ≥ 才能拍 | 小于 → 换小 pack 或走 §4 多档 |
| 5 | 快门一张 | 成片正立、颗粒尺度正常 | 侧躺 → 传感器角算法；颗粒过粗/过细 → 裁切比例过大 |
| 6 | 系统相册里翻到 | `Pictures/Pocket CCD/` 有图 | MediaStore 写入失败 → `PocketCcdGallery` 日志 |
| 7 | 相册删一张 → 切回 App | 照片墙同步消失 | 新机型 MediaStore 的 trash 语义（review P1-7） |
| 8 | 按快门后 1 秒内切相册，再切回 | 要么正常出片，要么 5 秒后提示「相机没有响应」并自动重连 | 超时兜底（D-049）；若卡在「正在冲洗」不放 → 回归 |
| 9 | 设置页 → 开源与素材声明 | 四段文案完整、可滚动、返回正常 | `strings.xml` 的 `licenses_*` |

第 8 项的"卡死在正在冲洗"已修（D-049：`takeJpeg` 5 秒上限 + 超时重绑会话）。
仍**未修**的是 review P1-11：`ON_RESUME` 的会话探针只探一次、几乎必然误判，
表现是每次回前台整轮重绑（闪一下黑）。它不丢照片，优先级低于 P0-4，留着。

---

## 7. 风险与开放问题

- **裁切比例 > 5% 时取景与成片不一致**（16MP 机型裁 11–13%）。FOT-009 要做裁切框，
  在那之前这类机器的体验是"看到的多、拍到的少"。
- **老机冲洗可能更慢**：B-11 那条 8.4–9.1s 很可能是 debug 包的测量口径问题（review P1-8），
  但要先在自己机器上用 benchmark 包重测，才能判断要不要为性能排期。
- **内存**：`4096×3072` 的三份 float 平面约 151 MB，`4000×3000` 约 144 MB，
  4GB 内存机型靠 native 分配能撑住但余量不大。这也是降尺寸的一个附带好处。
- **`4000×3000` 是否真的是"最普遍的 12MP 档"**：这是按公开规格归纳的，没有逐机型实测。
  拿到真机前标 `[H]`。
- **没有真机可测**：本轮所有改动只经过 `assembleDebug` + 28 例单测，`adb devices` 是空的。
