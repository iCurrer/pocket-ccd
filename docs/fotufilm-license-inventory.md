# Fotufilm 移植 License / 来源清单

**日期：** 2026-10-18  
**源码基线：** `DhaliwalX/fotufilm-engine` commit `2d07c48c14199be24972db760a7631dc95f74012`  
**用途：** Pocket CCD Android 原生移植的合规清单；不是法律意见。

---

## 1. 代码

| 内容 | 位置 | License | 义务 |
|---|---|---|---|
| 引擎核心 | `Sources/FotufilmCore` | Apache-2.0 | 保留 LICENSE/NOTICE；修改文件标注；专利/商标条款 |
| Halide 适配 | `Sources/FotufilmHalide` | Apache-2.0 | 同上 |
| C API | `Sources/CFotufilmHost/include/fotufilm.h` | Apache-2.0 | 同上 |
| Android kernel 适配 | `FotufilmHalideAndroid.cpp`、`FotufilmHalideAndroidGPU.cpp` | Apache-2.0 | 同上 |
| CLI | `Sources/fotufilm` | Apache-2.0 | 仅构建期使用也建议保留 notices |
| 编辑器 glyph | `shared/Glyphs/` | CC BY-SA 4.0 | 如使用需署名 + ShareAlike；建议 Pocket CCD 不用 |

## 2. 胶片 profiles

- 47 个正式 profile：`Sources/FotufilmCore/Stocks/*.json`；
- License：**CC BY-SA 4.0**；
- 必须：
  - credit **Fotufilm**，链接 <https://fotufilm.com>；
  - 保留 **MUAStudio Inc.** 版权声明；
  - 标注是否修改；
  - 修改后的 profile 必须以 CC BY-SA 4.0 或兼容许可分发。
- **渲染出的照片/视频不受 ShareAlike 影响**（`LICENSING.md` 明确）。

### 对 Android 移植的影响

如果 App 直接打包 profile JSON：

- profile 文件本身随 App 分发，需要 CC BY-SA 合规；
- App 代码可以是 Apache/闭源，但 profile 文件是独立 CC BY-SA 资产，需可获取/可替换/保留 notices；
- 不要用 DRM/技术措施限制用户复制这些 CC BY-SA 文件（否则可能违反 ShareAlike）。

如果 App 打包的是**由 profile 生成的 LUT/pack**：

- LUT/pack 很可能是 profile 的 adaptation，建议按 CC BY-SA 4.0 分发；
- 需要署名、链接、修改说明；
- 需要法务确认 ShareAlike 对 App 整体代码不传染（通常资产独立，但需正式意见）。

## 3. 第三方

| 组件 | License | 处理 |
|---|---|---|
| Halide | MIT | 在 App 的 open-source notices 中完整保留版权/许可；AOT 二进制携带 Halide runtime |
| rawtoaces-data camera profiles | Apache-2.0 | 保留 LICENSE/声明 |
| colour-science xenon spectrum | BSD-3-Clause | 完整保留 notice |
| 其他 browser/video 依赖 | 见 `THIRD_PARTY_NOTICES.md`、`licenses/` | 若使用对应模块才需要 |

## 4. 商标

- **Fotufilm**、**MUA Studio** 是项目品牌；不能用它们暗示 Pocket CCD 是官方产品；
- Kodak、Fujifilm、Ilford、CineStill、Lomography、instax 等是各自商标；
- 胶片名称可在“描述/兼容”语境下使用，需加商标声明；
- 不要把“Kodak Gold 200”做成 App 名称/图标。

## 5. 建议的 App 内声明

```text
Film simulation engine: Fotufilm
https://fotufilm.com
Copyright MUAStudio Inc.
Licensed under Apache-2.0; film profiles under CC BY-SA 4.0.
Pocket CCD is not affiliated with or endorsed by Fotufilm or MUAStudio.
Film names are trademarks of their respective owners.
```

## 6. 仍需确认

- LUT/pack 的 CC BY-SA 传染范围（法务）；
- 是否可以把 CC BY-SA profile 作为 App 内置资产（可获取/可替换方式）；
- 是否只选取部分 stock 以降低合规/包体；
- App 是否开源；
- 是否需要提供 profile 下载/导出以符合 ShareAlike。
