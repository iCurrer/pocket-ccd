# -*- coding: utf-8 -*-
"""从主人 2026-10-21 真机截图里量真实布局比例（不是猜）。"""
from PIL import Image
import numpy as np

p = r"D:\Android\pocket ccd\docs\screenshots\2026-10-21-restored-dark-ui.png"
im = Image.open(p).convert("RGB")
W, H = im.size
a = np.asarray(im).astype(np.int16)
print("size", W, H, "ratio", round(W / H, 4))

gray = a.mean(axis=2)

# 每 16 行平均亮度，找结构性亮/暗带
rows = gray.mean(axis=1)
print("\n-- row brightness (y, y/H, mean) every 1/60 --")
for i in range(0, 61):
    y = min(H - 1, int(H * i / 60))
    print(f"{y:5d} {i/60:5.3f} {rows[y]:7.1f}")

# 白色高亮元素：pill / 快门面 / 文字
bright = (gray > 200)
ys = np.where(bright.any(axis=1))[0]
print("\nbright rows range", ys.min(), ys.max())

# pill: 顶部 200 行内的白色连通区域
top = bright[:int(H * 0.12)]
cols = np.where(top.any(axis=0))[0]
if len(cols):
    print("top bright cols", cols.min(), cols.max())
    ytop = np.where(top.any(axis=1))[0]
    print("top bright rows", ytop.min(), ytop.max())

# 快门面：底部 45% 内的大块亮区
bottom = bright[int(H * 0.55):]
ys2, xs2 = np.where(bottom)
if len(ys2):
    print("bottom-bright bbox x", xs2.min(), xs2.max(), "y", ys2.min() + int(H * 0.55), ys2.max() + int(H * 0.55))

# 红色快门点
red = (a[:, :, 0] > 120) & (a[:, :, 1] < 110) & (a[:, :, 2] < 100) & (a[:, :, 0] - a[:, :, 1] > 60)
ys3, xs3 = np.where(red)
if len(ys3):
    print("red dot bbox x", xs3.min(), xs3.max(), "y", ys3.min(), ys3.max(), "center", (xs3.mean() / W, ys3.mean() / H))

# 金色元素
gold = (a[:, :, 0] > 150) & (a[:, :, 1] > 110) & (a[:, :, 2] > 60) & (a[:, :, 0] - a[:, :, 2] > 40)
ysg, xsg = np.where(gold)
if len(ysg):
    print("gold pixels", len(ysg), "y range", ysg.min(), ysg.max())

# 面板顶边：从 55% 往下找第一处亮度台阶
base = rows[int(H * 0.5):int(H * 0.6)].mean()
for y in range(int(H * 0.5), int(H * 0.95)):
    if rows[y] > base + 8:
        print("panel-ish step at y", y, "ratio", round(y / H, 4), "row", round(rows[y], 1), "base", round(base, 1))
        break

# 取景窗边界：中段最暗的宽行
mid = gray[int(H * 0.2):int(H * 0.5)]
darkcols = np.where((mid < 12).any(axis=0))[0]
print("dark window cols", darkcols.min(), darkcols.max(), "-> dp width ratio", round((darkcols.max() - darkcols.min()) / W, 4))
darkrows = np.where((gray[:, int(W * 0.45):int(W * 0.55)] < 12).any(axis=1))[0]
print("dark window rows", darkrows.min(), darkrows.max())
