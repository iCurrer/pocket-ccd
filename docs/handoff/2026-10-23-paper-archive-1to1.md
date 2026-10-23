# 2026-10-23 夜间：B 方案 1:1 版面落地（v0.6.0，UI 层）

**包：** `dist/pocketccd-0.6.0-paper-archive-1to1.apk`（debug 签名，可直接覆盖 0.5.0）
**决策：** D-064 ｜ **规格：** `docs/paper-archive-1to1-layout.md` ｜ **前置：** D-063 / v0.5.0
**用户选择：** B —— 视觉 1:1；新增功能由其他智能体接入。

---

## 1. 这轮做了什么（只 UI）

- **底部四 Tab**：相册 / 胶卷 / 发现 / 设置，纸面页平级切换；相机页仍无 TabBar；相册查看器
  打开时自动隐藏。新增 `DISCOVER` 目的地与发现页脚手架（只显示真实统计：
  照片数 / 胶卷数 / 本卷进度），不预置内容。
- **成片页 1:1**：返回 + `05 / 36` + 更多页头；86% 印相 + 卷名 + `P-05`；三行真实元数据
  （日期 / 真实 EXIF / 真实 `Build.MODEL`，缺项 `—`）；状态行；4 动作行
  （保存 / 收藏 / 分享 / 更多）。
- **照片墙 1:1**：`Photo Wall` + 档案图标；真实卷名索引页签；3 列连续接触印相（列距 8 /
  行距 14 / 帧号 11sp / 石板蓝收藏方块）。
- **胶卷库 1:1**：`Film Library` + `+`（真实重扫）；墨黑 `CURRENT` 卡（真实缩略图 /
  `05 / 36` / 36 帧点阵 / READY/FULL）；真实卷索引行。
- **设置 1:1**：`设置` + 四分类手风琴（保存与输出 / 相机与拍摄 / 胶卷管理 / 关于）；
  相机权限行跳系统详情页；真实 BuildConfig 页脚；声明覆盖层保留。
- **主题**：`FilmPaperSpec.sheetTitle` 26sp / 32 行高；新增 `PaperNavBar`；`FilmIcons`
  新增 `ArrowLeft / More / Heart / Share / Save / Compass`。

## 2. 功能待办（其他智能体接）

| 入口 | 当前状态 | 接入点 |
|---|---|---|
| 成片页 保存/收藏/分享/更多 | UI 已画，回调为空 | `ResultScreen(onSave/onToggleFavorite/onShare/onMore/isFavorite)` |
| 发现页内容 | 只显示真实统计 | `DiscoverScreen` 页脚前插入内容源 |
| 滤镜/拍摄模式 | 未做 | 需要新决策（D-041 仍挡着风格面板） |
| Fuji/Ilford 等新胶卷 | 未做 | 加真实 `.fswp` pack；列表自动显示 |
| 设置子页/账户 | 未做 | 分类行展开现有真实行；新设置项需真实持久化 |
| 分享/删除/App 内删除 | 未做 | 分享可用系统 Intent；删除需同时处理 MediaStore（D-047） |
| 取景页设计板 01 | 未重排 | 仍是 v0.5.0 黑闸门；如要 1:1 需另开一轮 |

## 3. 验证

- `:app:assembleDebug` + `:app:testDebugUnitTest` → **BUILD SUCCESSFUL**
- JVM 单测 **64 / 64**（AppStateTest 16→19，新增 DISCOVER / 平级 Tab 用例）
- 未新增依赖、字体资源、权限；纸面页无写死颜色/阴影/渐变；无裸 `fontSize`
- **真机未验**：无设备，未安装、未截图、未跑 Accessibility Scanner

## 4. 真机必看

1. 底部 TabBar 高度、系统手势条 inset、页面 88dp 留白是否被压/留太多。
2. 成片页：`05 / 36` 与 `P-05` 的真实性；动作行 4 等分在 360dp 屏；88dp 与 TabBar 重叠。
3. 照片墙：3 列在 360dp 屏的列宽/帧号/收藏方块；页签横向滚动。
4. 胶卷库：墨黑 CURRENT 卡与纸面列表的层次；`+` 图标描边；56×72 缩略图；36 点阵密度。
5. 设置：手风琴展开高度动画、权限行跳转返回刷新、页脚版本块。
6. 发现页：真实统计数字与空态；Tab 切换是否跟手。
7. 相册查看器：黑底是否铺满系统栏；打开/关闭 TabBar 显隐是否闪。
8. 200% 字体 / 横屏 / 多机型。

## 5. 已知偏差

- 设计板 10sp → 11sp（字号地板 11sp，不能破）。
- 照片墙帧号是真实卷分组内的曝光序号；切“全部”时按卷重置。
- 胶卷库 CURRENT 卡无「继续拍摄」按钮（系统返回回取景；TabBar 没有相机页）。
- 发现页只显示真实统计，不写“敬请期待”式假内容。
- 取景页未按设计板 01 重排（不在本轮附图范围）。

## 6. 回滚

```powershell
cd 'D:\Android\pocket ccd'
git checkout ef50bc1   # v0.5.0
$env:JAVA_HOME='D:\software\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

## 7. v0.6.1 反馈修正（2026-10-23 23:12）

用户安装 v0.6.0 后反馈四项，已修：

1. **TabBar 黑底**：`PaperNavBar` 在 `FilmTheme(FilmPaperSpec)` 分支之外，读到了默认暗色令牌；
   已单独包 `FilmTheme(FilmPaperSpec)`。
2. **返回逻辑**：纸面页 `NavigateBack` 统一回 CAMERA，不再回上一个 Tab；查看器/声明覆盖层仍先关。
3. **成片页**：印相 86%→92%、纸边 6→8dp、加 2dp 极轻投影；EXIF 行拼成
   `ISO · 快门 · 光圈`（只拼真实项）；底部预留 88→64dp。
4. **设置页脚**：改成设计板的左对齐 `Version / Build / Author` 三行，标签左、值右（mono）；
   作者无真实来源显示 `—`。
5. 各纸面页底部留白统一 88→64dp。

**包：** `dist/pocketccd-0.6.1-paper-archive-1to1.apk`（41,023,826 B，
SHA-256 `9B284E70…26B865`，versionCode 10 / 0.6.1，debug 签名）。
**验证：** `assembleDebug` + `assembleBenchmark` + 64/64 JVM 单测；**真机仍未验**（无设备）。
