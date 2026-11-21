"""把「渲染进图里的假透明棋盘格」切成真正的 alpha 通道。

生成式出图给的 `background: transparent` 并不总是真透明 —— 常见情况是把 PS 风格的
棋盘格**画进** RGB 像素里。这种图直接塞进 Compose 会带一块灰格子，所以必须自己抠。

抠法只有一条原则：**只填与画面边界连通的背景**。
物体内部的高光、金色反光、白字都跟边界不连通，所以不会被误伤 —— 这比按亮度阈值抠
安全得多（按阈值抠会把金带的镜面高光一起吃掉，边缘全是锯齿洞）。

用法：
    python tools/cutout.py in.png out.png [--erode 1] [--thresh 80]
"""
import argparse
import sys

from PIL import Image, ImageDraw, ImageFilter

SENTINEL = (255, 0, 255)


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("src")
    p.add_argument("dst")
    p.add_argument("--thresh", type=int, default=80,
                   help="洪泛的颜色阈值（RGB 三通道差之和）。棋盘两色差 ~71，"
                        "取 80 只够跨过格子边界，够不到金色标签")
    p.add_argument("--erode", type=int, default=1,
                   help="轮廓腐蚀像素数。0.5–1px 的混色白边靠它去掉")
    args = p.parse_args()

    im = Image.open(args.src).convert("RGB")
    w, h = im.size

    # 从四角各灌一次：万一物体贴边形成两个连通域，四角能覆盖到。
    work = im.copy()
    for seed in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        if work.getpixel(seed) != SENTINEL:
            ImageDraw.floodfill(work, seed, SENTINEL, thresh=args.thresh)

    # 底色是否真是棋盘格：整幅被填满说明图里没有主体，别硬抠。
    px = work.load()
    bg = 0
    for y in range(0, h, 4):
        for x in range(0, w, 4):
            if px[x, y] == SENTINEL:
                bg += 1
    ratio = bg / ((w // 4) * (h // 4))
    if ratio > 0.995:
        print(f"拒绝：{ratio:.1%} 被判定为背景，图里可能没有主体", file=sys.stderr)
        return 1

    alpha = Image.new("L", (w, h), 255)
    ap = alpha.load()
    for y in range(h):
        for x in range(w):
            if px[x, y] == SENTINEL:
                ap[x, y] = 0

    if args.erode > 0:
        alpha = alpha.filter(ImageFilter.MinFilter(args.erode * 2 + 1))
    # 轻微模糊给边缘一点抗锯齿，硬切在深色机身上会看到台阶。
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.6))

    out = im.convert("RGBA")
    out.putalpha(alpha)
    out.save(args.dst)
    print(f"{args.dst}  {w}x{h}  背景占比 {ratio:.1%}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
