# 红色玩具相机视觉调研与修正

**日期：** 2026-10-17  
**触发反馈：** 主页红色太丑；品牌区“AI 痕迹”重；多余线条多；转场时出现长方形边角闪烁；系统顶栏和主题不同色。

---

## 1. 调研结论

### 1.1 红不该是荧光红

参考 [Kodak Red](https://color-register.org/color/kodak-red)：

- 注册值 `#E30613`；
- 白字对比度约 4.9:1，黑字约 4.3:1；
- 描述是“intensely saturated red”，适合 logo/强调，但整屏使用会非常抢眼。

[Palette Vault 的 Y2K 色彩分析](https://palettevault.github.io/colors/y2k/) 也指出：

- Y2K 的核心是“高明度 + 高饱和 + 光泽”，不是平涂；
- 全强度使用会变成“pastiche/costume”；
- 更耐看的做法是“一个高饱和色 + 克制的中性色”，或者用同色系的深色变体承载文字。

### 1.2 参考产品

[Kodak Charmera](https://petapixel.com/2025/09/09/the-kodak-charmera-is-a-tiny-digital-keychain-camera-sold-in-blind-boxes/) 这类 Y2K 钥匙扣数码相机，核心不是“红色越多越好”，而是：

- 小体积、厚塑料；
- 黑镜头/黑屏幕形成深色块；
- 红、奶油、绿、橙只占小面积；
- 品牌字小、压印感强，不是大标题。

## 2. 选型

| 角色 | 旧值 | 新值 | 原因 |
|---|---|---|---|
| 机身红 | `#F12D27` | `#D9382E` | 降低荧光感，偏 vintage tomato red |
| 红色亮部 | `#FF5A45` | `#EE5B4A` | 高光收敛，不刺眼 |
| 红色暗部 | `#C91E1A` | `#A8231C` | 更深，增加塑料厚度 |
| 奶油 | `#FFF3E0` | `#F7E9D7` | 暖一点，降低纯白感 |
| 薄荷绿 | `#7FD6A4` | `#8FC7A8` | 降饱和，避免红绿互补过冲 |
| 橙色 | `#F28C28` | `#E8872E` | 偏焦糖橙，不是交通锥橙 |
| 墨色 | `#2A0B09` | `#1E1210` | 更中性，减少红黑对比的生硬 |

**对比度：**

| 组合 | 对比度 | 用途 |
|---|---:|---|
| `#F7E9D7` on `#D9382E` | 3.86:1 | 品牌 logo 大字/非正文 |
| `#F7E9D7` on `#A8231C` | 6.02:1 | 深红上的文字 |
| `#1E1210` on `#8FC7A8` | 9.17:1 | 绿键图标 |
| `#1E1210` on `#E8872E` | 7.00:1 | 橙键图标 |
| `#D9382E` on `#12100F` | 4.12:1 | 红/黑边界，非正文 |

结论：奶油品牌字只用于 logo 大字；小字不放在亮红上。

## 3. 品牌区与线条修正

旧首页品牌区的问题：

- 品牌 20sp + 4sp 字距，像网页标题；
- `PULL DOWN · SHOOT` 是多余的说明文字；
- 机身外再套一圈白色边线、闪光窗中间再加一条线、镜头里再叠弧线/圆环；
- 结果不是“玩具相机”，而是“用线条堆出来的 UI”。

修正：

- 品牌改为 16sp、1.5sp 字距，只保留 `POCKET CCD`；
- 删除 `PULL DOWN · SHOOT`；
- 删除机身外圈白色边线；
- 删除闪光窗中间的分割线；
- 删除镜头内部弧线，只保留黑色镜筒 + 绿色镜圈 + 玻璃 + 一个小高光；
- 保留物理按键、阴影、材质渐变，不靠装饰线。

## 4. 系统顶栏

之前 `Surface` 固定用 `MaterialTheme.colorScheme.background`（奶油），而首页/拍摄页是红色，导致状态栏区域露出奶油色。

修正：

- `PocketCcdApp` 根据目的地选择 `Surface` 颜色：
  - 首页/拍摄页 → `RetroRed`；
  - 照片墙/成片/设置 → `Background`（浅薄荷）；
- 用 `WindowCompat.getInsetsController` 动态切换系统栏图标：
  - 红页 → 浅色图标；
  - 浅色页 → 深色图标；
- `themes.xml` 启动背景改为红色，避免冷启动闪奶油色。

## 5. 转场长方形闪烁

原因判断：

- 旧方案是 `AnimatedContent` 两个页面 + `sharedBounds` overlay；
- 首页是 36dp 圆角红板，拍摄页是全屏红色容器；
- Home 退出/Camera 进入时，overlay 层可能以矩形 bounds 出现，边角先闪一下；
- 加上底层 `Surface` 曾是奶油色，矩形边角更明显。

先做的临时修正：

- `sharedBounds` 增加 36dp `OverlayClip` 和 `zIndexInOverlay=1f`；
- 红页 `Surface` 改红，避免闪奶油矩形。

最终修正（当前实现）：

- 不再让 Home/Camera 是两个页面；`CameraStageScreen` 用**同一个** `ToyCameraBody`；
- `Animatable<Float>` 直接由拖拽驱动，机身、快门、闪光灯、品牌、镜头/取景器在同一个布局里插值；
- `SharedTransitionLayout`/`sharedBounds` 从 Home/Camera 路径移除，闪角来源消失；
- 只保留 Gallery/Result/Settings 的轻量 `AnimatedContent` 淡入淡出。

## 6. 仍未验证

- 没有设备/AVD，以上只做了代码和构建验证，**没有真机截图或录屏**；
- 转场是否彻底不再闪角，需要真机慢放确认；
- 系统栏图标切换在 Android 10–17 上的行为需要实机确认；
- 新红色是否仍偏艳，需要真机户外/夜间各看一次。
