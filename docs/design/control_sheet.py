# -*- coding: utf-8 -*-
"""Pocket CCD 控件规范表（v2，2026-10-23）：尺寸 / 层级 / 状态 / 按压反馈一览。"""
from PIL import Image, ImageDraw, ImageFont
import math

S = 2
W, H = 760, 1080
img = Image.new("RGB", (W * S, H * S), (11, 12, 14))
d = ImageDraw.Draw(img)
F = r"C:\Windows\Fonts"
f_l = ImageFont.truetype(F + r"\consola.ttf", 9 * S)
f_m = ImageFont.truetype(F + r"\consola.ttf", 11 * S)
f_b = ImageFont.truetype(F + r"\consola.ttf", 13 * S)
f_cn = ImageFont.truetype(F + r"\msyh.ttc", 11 * S)
f_cn_s = ImageFont.truetype(F + r"\msyh.ttc", 9 * S)
f_cn_b = ImageFont.truetype(F + r"\msyhbd.ttc", 13 * S)


def px(v):
    return int(round(v * S))


def rr(x, y, w, h, fill=None, radius=0, outline=None, width=1):
    d.rounded_rectangle([px(x), px(y), px(x + w) - 1, px(y + h) - 1], radius=px(radius), fill=fill, outline=outline, width=width)


def label(x, y, s, font=None, color=(120, 124, 130), sp=0.0):
    font = font or f_l
    cx = px(x)
    for ch in s:
        d.text((cx, px(y)), ch, font=font, fill=color)
        cx += d.textlength(ch, font=font) + sp


def section(y, title, detail):
    d.line([(px(24), px(y)), (px(W - 24), px(y))], fill=(255, 255, 255, 18), width=1)
    label(24, y + 8, title, f_b, (231, 232, 234), sp=px(0.5))
    label(W - 24 - d.textlength(detail, font=f_l) / S, y + 10, detail, f_l, (104, 108, 114))


INK = (231, 232, 234)
INK2 = (162, 165, 170)
INK3 = (108, 112, 118)
GOLD = (231, 179, 122)
GOLD_DIM = (150, 116, 80)


def bolt(cx, cy, h=18, color=INK2):
    p = [(cx + .10 * h, cy - .46 * h), (cx - .26 * h, cy + .06 * h), (cx - .02 * h, cy + .06 * h),
         (cx - .14 * h, cy + .46 * h), (cx + .26 * h, cy - .08 * h), (cx + .02 * h, cy - .08 * h)]
    d.polygon([(px(a), px(b)) for a, b in p], fill=color)


def grid(cx, cy, size=16, color=INK2):
    x0, y0 = cx - size / 2, cy - size / 2
    for i in (1, 2):
        d.line([(px(x0 + size * i / 3), px(y0)), (px(x0 + size * i / 3), px(y0 + size))], fill=color, width=1)
        d.line([(px(x0), px(y0 + size * i / 3)), (px(x0 + size), px(y0 + size * i / 3))], fill=color, width=1)
    rr(x0, y0, size, size, radius=1, outline=color, width=1)


def sliders(cx, cy, size=16, color=INK2):
    x0, y0 = cx - size / 2, cy - size / 2
    for i, t in enumerate((.25, .5, .75)):
        y = y0 + size * t
        d.line([(px(x0), px(y)), (px(x0 + size), px(y))], fill=color, width=1)
        kx = x0 + size * (.62 if i == 0 else .38 if i == 1 else .55)
        d.ellipse([px(kx - 1.6), px(cy - 8 + size * t - 1.6), px(kx + 1.6), px(cy - 8 + size * t + 1.6)], fill=color)


def icon_box(cx, cy, ico, active=False, pressed=False, disabled=False):
    """48dp 热区 / 40dp 视觉圆。"""
    r = 24
    if pressed:
        rr(cx - r, cy - r, 48, 48, fill=(20, 22, 25), radius=24)
    if active:
        d.ellipse([px(cx - 20), px(cy - 20), px(cx + 20), px(cy + 20)], fill=(43, 37, 29))
    a = 0.35 if disabled else 1.0
    col = tuple(int(c * a) for c in (GOLD if active else INK2))
    if ico == "flash":
        bolt(cx, cy, color=col)
    elif ico == "grid":
        grid(cx, cy, color=col)
    elif ico == "tune":
        sliders(cx, cy, color=col)
    if pressed:
        d.ellipse([px(cx - 20), px(cy - 20), px(cx + 20), px(cy + 20)], fill=(24, 25, 28))


# ---------------- 1. icon button ----------------
section(28, "ICON BUTTON", "48 hit / 40 circle / 18 glyph")
icon_box(70, 96, "flash")
icon_box(160, 96, "flash", pressed=True)
icon_box(250, 96, "flash", active=True)
icon_box(340, 96, "flash", disabled=True)
for i, t in enumerate(("default", "pressed", "active", "disabled")):
    w = d.textlength(t, font=f_l) / S
    label(70 + i * 90 - w / 2, 126, t, f_l, INK3)

# ---------------- 2. shutter ----------------
section(168, "SHUTTER", "88 well / 72 ring / 0.94 press")


def shutter(cx, cy, pressed=False):
    r_well = 44
    d.ellipse([px(cx - r_well), px(cy - r_well), px(cx + r_well), px(cy + r_well)], fill=(8, 9, 10))
    rad = 36 * (0.94 if pressed else 1)
    # ring
    d.ellipse([px(cx - rad), px(cy - rad), px(cx + rad), px(cy + rad)], fill=(60, 52, 42))
    for i in range(90):
        a = i * 2 * math.pi / 90
        r0, r1 = rad - 6, rad - 1
        col = (255, 255, 255, 26) if i % 2 == 0 else (0, 0, 0, 60)
        d.line([(px(cx + r0 * math.cos(a)), px(cy + r0 * math.sin(a))),
                (px(cx + r1 * math.cos(a)), px(cy + r1 * math.sin(a)))], fill=col, width=1)
    d.ellipse([px(cx - rad), px(cy - rad), px(cx + rad), px(cy + rad)],
              outline=(231, 179, 122, 255 if pressed else 200), width=1)
    face = int(rad - 6)
    for i in range(face, 0, -1):
        t = i / face
        col = (int(242 - 24 * (1 - t)), int(238 - 26 * (1 - t)), int(230 - 28 * (1 - t)))
        d.ellipse([px(cx - i), px(cy - i), px(cx + i), px(cy + i)], fill=col)
    d.ellipse([px(cx - 4), px(cy - 4), px(cx + 4), px(cy + 4)], fill=(190, 68, 52))


shutter(110, 250)
shutter(250, 250, pressed=True)
label(78, 306, "default", f_l, INK3)
label(216, 306, "pressed · 1dp travel", f_l, INK3)
d.text((px(340), px(240)), "按下：缩放 0.94 + 下压 1dp\n+ 金环 30%→62% + 面暗 6%\n+ KEYBOARD_TAP 轻震", font=f_cn_s, fill=INK3)

# ---------------- 3. panel key ----------------
section(336, "PANEL KEY", "64 hit / 48 row / 11 label")


def panel_key(cx, cy, text, pressed=False):
    if pressed:
        rr(cx - 32, cy - 24, 64, 48, fill=(26, 28, 31), radius=10)
    # gallery glyph
    rr(cx - 10, cy - 16, 20, 15, radius=2, outline=INK2, width=1)
    d.ellipse([px(cx - 6), px(cy - 12), px(cx - 3), px(cy - 9)], fill=INK2)
    d.line([(px(cx - 8), px(cy - 5)), (px(cx + 8), px(cy - 5))], fill=INK3, width=1)
    w = d.textlength(text, font=f_cn_s) / S
    label(cx - w / 2, cy + 8, text, f_cn_s, INK3)


panel_key(90, 400, "相册")
panel_key(230, 400, "相册", pressed=True)
label(66, 430, "default", f_l, INK3)
label(206, 430, "pressed", f_l, INK3)

# ---------------- 4. text buttons ----------------
section(456, "BUTTON · Primary / Secondary / Ghost", "48 high · radius 12")


def text_btn(x, y, w, label_, primary=False, ghost=False, disabled=False, pressed=False):
    fill = (237, 239, 242) if primary else (255, 255, 255, 13)
    if ghost:
        fill = None
    if disabled:
        fill = (60, 62, 66)
    rr(x, y, w, 48, fill=fill, radius=12,
       outline=None if primary or ghost else (255, 255, 255, 36), width=1)
    if pressed:
        rr(x, y, w, 48, fill=(40, 42, 46), radius=12)
    col = (11, 12, 14) if primary and not disabled else (231, 232, 234)
    if disabled:
        col = (120, 123, 128)
    tw = d.textlength(label_, font=f_cn) / S
    label(x + (w - tw) / 2, y + 16, label_, f_cn, col)


text_btn(24, 500, 110, "继续拍摄", primary=True)
text_btn(148, 500, 110, "开新卷")
text_btn(272, 500, 90, "重拍", ghost=True)
text_btn(376, 500, 110, "禁用", disabled=True)
text_btn(500, 500, 110, "主按钮", primary=True, pressed=True)
for i, t in enumerate(("primary", "secondary", "ghost", "disabled", "pressed")):
    label(24 + i * 124, 556, t, f_l, INK3)

# ---------------- 5. chips / pill ----------------
section(586, "CHIP / PILL", "48 hit / 32 visual")


def chip(x, y, w, text, selected=False, pressed=False):
    bg = (45, 37, 28) if selected else (255, 255, 255, 13)
    rr(x, y, w, 32, fill=bg, radius=16)
    if pressed:
        rr(x, y, w, 32, fill=(43, 45, 49), radius=16)
    tw = d.textlength(text, font=f_cn_s) / S
    label(x + (w - tw) / 2, y + 9, text, f_cn_s, GOLD if selected else INK3)


chip(24, 630, 64, "全部")
chip(98, 630, 72, "★ 收藏", selected=True)
chip(180, 630, 88, "GOLD 200")
chip(278, 630, 72, "★ 收藏", selected=True, pressed=True)
label(24, 674, "unselected", f_l, INK3)
label(98, 674, "selected", f_l, INK3)
label(278, 674, "pressed", f_l, INK3)

# ---------------- 6. stepper rail ----------------
section(704, "RAIL · zoom stops / EV", "40 row / 64 min width / text only")


def rail(x, y, text, active=False):
    col = INK if active else INK3
    d.text((px(x), px(y)), text, font=f_m, fill=col)
    if active:
        d.ellipse([px(x + 44), px(y + 6), px(x + 48), px(y + 10)], fill=GOLD)


rail(60, 748, "1×", True)
rail(60, 786, "3.2×")
rail(200, 748, "±")
d.text((px(232), px(748)), "0.0", font=f_m, fill=INK3)
rail(200, 786, "±")
d.text((px(232), px(786)), "+0.3", font=f_m, fill=GOLD)
d.text((px(330), px(750)), "按下：0.96 缩放 + 5% 覆盖 + 轻震\n选中：160ms 颜色过渡", font=f_cn_s, fill=INK3)

# footer
d.line([(px(24), px(880)), (px(W - 24), px(880))], fill=(255, 255, 255, 18), width=1)
label(24, 896, "FEEDBACK", f_b, (231, 232, 234), sp=px(0.5))
d.text((px(24), px(920)),
       "所有可点控件：按下 = 缩放(0.96/0.97) + 覆盖(白5%/黑5%) + KEYBOARD_TAP；禁用 = 无反馈/35-40% 透明。\n"
       "列表行：只叠 4% 覆盖、不缩放、不震动（高频，避免一路嗡嗡响）。\n"
       "状态切换：颜色 160ms；按压到位：spring(0.62, 900) 约 90ms；无 Material ripple。\n"
       "热区：图标 48、文字键 48、chip 48、档位 40(宽≥64)、面板副键 64×48、快门 88。",
       font=f_cn_s, fill=INK3, spacing=px(1))

img.save(r"D:\Android\pocket ccd\docs\design\control-sheet-v2.png")
print("saved")
