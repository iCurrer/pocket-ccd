"""把 docs/artwork/film/<stock>.png 转成 App 用的 drawable-nodpi 资源。

为什么需要这一步，而不是直接把原图丢进 res/：

1. **原图 1024×1536 / 1.8MB 一张**，8 张就是 14MB，而 App 里它最大只显示
   84dp 高（胶卷库大卡）。按 360×540 存、再用 WebP q92 编码，
   8 张合计约 0.34MB（同内容 PNG 是 2.25MB）。
2. **原图的透明留边不一致不行**：主体在 1024×1536 画布里只占 530×882，
   直接 `ContentScale.Fit` 会缩到比槽位小一圈，产品照变成"小图标"。
   这里按 bbox 裁紧后**补成精确 2:3**，槽位是 2:3，Fit 后正好对位。
3. 8 张必须**同一套裁剪参数**，否则某支会忽大忽小 —— bbox 相同是前提，脚本会断言。

**为什么是 WebP 而不是 PNG**：PNG 是无损的，这种带噪点的产品照压不下去
（8 张 2.25MB，占 release 包 5%）；有损 WebP 带 alpha 通道，q92 下肉眼无差、
体积只有 15%。Android 从 API 18 起原生支持带透明的 WebP，minSdk 29 无忧。
**同一资源名不能同时留 .png 和 .webp**（aapt 会冲突），所以脚本会清掉旧 PNG。

用法：
    python tools/make_film_artwork.py
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "docs" / "artwork" / "film"
DST = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

# 质量取 92：再低会在标签大字边缘和金带的镜面高光上看出块状噪点。
WEBP_QUALITY = 92

STOCKS = [
    "gold200",
    "portra400",
    "ektar100",
    "trix400",
    "acros100",
    "provia100f",
    "velvia50",
    "cinestill800t",
]

# @3x 槽位是 300×450；留 1.2 倍余量给更大的展示位（胶卷库大卡 84dp）。
OUT_W, OUT_H = 360, 540


def main() -> None:
    DST.mkdir(parents=True, exist_ok=True)
    boxes = {}
    for stock in STOCKS:
        src = SRC / stock / f"{stock}.png"
        im = Image.open(src).convert("RGBA")
        boxes[stock] = im.getbbox()

    # bbox 必须一致：不一致说明某张图的主体大小漂了，进 App 会忽大忽小。
    # 但允许 ±2px —— 抠图阈值会让边缘那一像素在连/不连之间摆动（provia100f 就是这样），
    # 那不是比例漂移。超过容差才是真问题，必须回去修图。
    xs0 = [b[0] for b in boxes.values()]
    ys0 = [b[1] for b in boxes.values()]
    xs1 = [b[2] for b in boxes.values()]
    ys1 = [b[3] for b in boxes.values()]
    spread = max(max(xs0) - min(xs0), max(ys0) - min(ys0),
                 max(xs1) - min(xs1), max(ys1) - min(ys1))
    if spread > 2:
        raise SystemExit(f"bbox 差异 {spread}px 超出容差，先修图再打包：{boxes}")
    # 取并集：宁可多带一格透明，也不能把某一张的主体切掉。
    box = (min(xs0), min(ys0), max(xs1), max(ys1))
    if spread > 0:
        print(f"bbox 有 {spread}px 差异，已取并集 {box}")

    for stock in STOCKS:
        im = Image.open(SRC / stock / f"{stock}.png").convert("RGBA").crop(box)
        # 补成精确 2:3（以高度为准扩宽，居中）—— 直接 resize 会压扁圆柱。
        target_w = round(im.height * 2 / 3)
        if target_w < im.width:
            raise SystemExit(f"{stock} 比 2:3 更宽，裁剪会切掉罐身，需要人工确认")
        canvas = Image.new("RGBA", (target_w, im.height), (0, 0, 0, 0))
        canvas.paste(im, ((target_w - im.width) // 2, 0), im)
        canvas = canvas.resize((OUT_W, OUT_H), Image.LANCZOS)

        out = DST / f"film_{stock}.webp"
        canvas.save(out, "WEBP", quality=WEBP_QUALITY, method=6)
        print(f"{out.name:33s} {out.stat().st_size / 1024:6.1f} KB")

    # 清掉旧 PNG：同资源名 .png + .webp 同时存在时 aapt 会报重复资源，
    # 而且镜像里谁赢不确定 —— 宁可显式删掉。
    for stale in DST.glob("film_*.png"):
        stale.unlink()
        print(f"删除旧资源 {stale.name}")

    total = sum((DST / f"film_{s}.webp").stat().st_size for s in STOCKS)
    print(f"{'TOTAL':33s} {total / 1024:6.1f} KB")


if __name__ == "__main__":
    main()
