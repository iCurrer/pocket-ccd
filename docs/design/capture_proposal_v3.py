# -*- coding: utf-8 -*-
"""Pocket CCD 取景页 v3（高级感方向）—— 412x915dp / 2x。

相对 v2 的修正：
- 取景窗按真机实测比例（354x472dp），去掉"手机壳"式外描边，只留 1px 内高光；
- 右侧变焦从"三颗描边圆钮"改成文字档位轨（更像相机设置，不像 App 按钮）；
- 底盖从"浅灰板"改成与机身同色系渐变 + 一条顶部高光；快门缩小、滚花变细；
- 顶栏图标去掉圆框，用 17dp 幽灵图标；胶卷 pill 32dp 高。
"""
from PIL import Image, ImageDraw, ImageFont
import math

S = 2
W, H = 412, 915
img = Image.new("RGB", (W * S, H * S), (11, 12, 14))
d = ImageDraw.Draw(img)
F = r"C:\Windows\Fonts"
f_cn = ImageFont.truetype(F + r"\msyh.ttc", 11 * S)
f_cn_s = ImageFont.truetype(F + r"\msyh.ttc", 9 * S)
f_cn_b = ImageFont.truetype(F + r"\msyh.ttc", 12 * S)
f_mono_s = ImageFont.truetype(F + r"\consola.ttf", 9 * S)
f_mono = ImageFont.truetype(F + r"\consola.ttf", 12 * S)
f_mono_m = ImageFont.truetype(F + r"\consola.ttf", 11 * S)


def px(v):
    return int(round(v * S))


def blend(base, top, a):
    return tuple(int(base[i] + (top[i] - base[i]) * a) for i in range(3))


BG = (11, 12, 14)
INK = (231, 232, 234)
INK2 = (162, 165, 170)
INK3 = (108, 112, 118)
GOLD = (231, 179, 122)
GOLD_DIM = (150, 116, 80)
RED = (190, 68, 52)


def vgrad(x, y, w, h, top, bottom, radius=0):
    layer = Image.new("RGB", (px(w), px(h)), top)
    dd = ImageDraw.Draw(layer)
    for i in range(px(h)):
        t = i / max(1, px(h) - 1)
        dd.line([(0, i), (px(w), i)], fill=tuple(int(top[k] + (bottom[k] - top[k]) * t) for k in range(3)))
    mask = Image.new("L", (px(w), px(h)), 0)
    md = ImageDraw.Draw(mask)
    if radius:
        md.rounded_rectangle([0, 0, px(w) - 1, px(h) - 1], radius=px(radius), fill=255)
    else:
        md.rectangle([0, 0, px(w) - 1, px(h) - 1], fill=255)
    img.paste(layer, (px(x), px(y)), mask)


def spaced(x, y, s, font, fill, sp=0.0):
    cx = px(x)
    for ch in s:
        d.text((cx, px(y)), ch, font=font, fill=fill)
        cx += d.textlength(ch, font=font) + sp


def tw(s, font, sp=0.0):
    return sum(d.textlength(ch, font=font) + sp for ch in s) / S


def dot(cx, cy, r, fill):
    d.ellipse([px(cx - r), px(cy - r), px(cx + r), px(cy + r)], fill=fill)


def rrect(x, y, w, h, fill=None, radius=0, outline=None, width=1):
    d.rounded_rectangle([px(x), px(y), px(x + w) - 1, px(y + h) - 1], radius=px(radius),
                        fill=fill, outline=outline, width=width)


# ---------- top bar (center y=80) ----------
cy = 80


def bolt(cx, cy, h=18, color=INK2):
    p = [(cx + 0.10 * h, cy - 0.46 * h), (cx - 0.26 * h, cy + 0.06 * h),
         (cx - 0.02 * h, cy + 0.06 * h), (cx - 0.14 * h, cy + 0.46 * h),
         (cx + 0.26 * h, cy - 0.08 * h), (cx + 0.02 * h, cy - 0.08 * h)]
    d.polygon([(px(a), px(b)) for a, b in p], fill=color)


def grid(cx, cy, size=16, color=INK2):
    x0, y0 = cx - size / 2, cy - size / 2
    for i in (1, 2):
        d.line([(px(x0 + size * i / 3), px(y0)), (px(x0 + size * i / 3), px(y0 + size))], fill=color, width=1)
        d.line([(px(x0), px(y0 + size * i / 3)), (px(x0 + size), px(y0 + size * i / 3))], fill=color, width=1)
    d.rounded_rectangle([px(x0), px(y0), px(x0 + size), px(y0 + size)], radius=px(1), outline=color, width=1)


def sliders(cx, cy, size=16, color=INK2):
    x0, y0 = cx - size / 2, cy - size / 2
    for i, t in enumerate((0.25, 0.5, 0.75)):
        y = y0 + size * t
        d.line([(px(x0), px(y)), (px(x0 + size), px(y))], fill=color, width=1)
        kx = x0 + size * (0.62 if i == 0 else 0.38 if i == 1 else 0.55)
        dot(kx, y, 1.6, color)


bolt(28, cy)
grid(74, cy)
sliders(384, cy)

pill_w, pill_h = 128, 32
pill_x, pill_y = (W - pill_w) / 2, cy - pill_h / 2
pill_bg = blend(BG, (255, 255, 255), 0.055)
rrect(pill_x, pill_y, pill_w, pill_h, fill=pill_bg, radius=16)
# top inner highlight on pill
d.line([(px(pill_x + 16), px(pill_y + 0.9)), (px(pill_x + pill_w - 16), px(pill_y + 0.9))],
       fill=blend(pill_bg, (255, 255, 255), 0.10), width=1)
dot(pill_x + 14, cy, 2.5, GOLD)
spaced(pill_x + 25, pill_y + 10.5, "GOLD 200", f_mono_s, blend(INK, BG, 0.14), sp=px(0.7))
cxx = pill_x + pill_w - 14
d.line([(px(cxx), px(cy - 2.5)), (px(cxx + 3.2), px(cy)), (px(cxx), px(cy + 2.5))], fill=INK3, width=1)

# ---------- viewfinder 354x472 at y=150 ----------
vw, vh = 354, 472
vx, vy = (W - vw) / 2, 150
rrect(vx, vy, vw, vh, fill=(3, 3, 4), radius=16)
# window inner top highlight (window is cut into body)
d.line([(px(vx + 18), px(vy + 0.9)), (px(vx + vw - 18), px(vy + 0.9))],
       fill=blend((3, 3, 4), (255, 255, 255), 0.10), width=1)
d.line([(px(vx + 18), px(vy + vh - 0.9)), (px(vx + vw - 18), px(vy + vh - 0.9))],
       fill=(0, 0, 0), width=1)

# corner brackets (thin, rounded caps, inset 16)
ins, arm = 16, 18
bcol = blend((3, 3, 4), (255, 255, 255), 0.55)
for ax, ay, sx, sy in [(vx + ins, vy + ins, 1, 1), (vx + vw - ins, vy + ins, -1, 1),
                       (vx + ins, vy + vh - ins, 1, -1), (vx + vw - ins, vy + vh - ins, -1, -1)]:
    d.line([(px(ax), px(ay)), (px(ax + arm * sx), px(ay))], fill=bcol, width=2)
    d.line([(px(ax), px(ay)), (px(ax), px(ay + arm * sy))], fill=bcol, width=2)
    dot(ax, ay, 0.7, bcol)

# reticle: 8dp ticks + 2dp gold dot
mcx, mcy = vx + vw / 2, vy + vh / 2
cg = blend((3, 3, 4), (255, 255, 255), 0.42)
for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
    d.line([(px(mcx + dx * 4), px(mcy + dy * 4)), (px(mcx + dx * 12), px(mcy + dy * 12))], fill=cg, width=1)
dot(mcx, mcy, 1.6, GOLD)

# right rail: text zoom stops + EV (tap targets invisible, 48dp)
rail_right = vx + vw - 18
stops = [("1×", True), ("3.2×", False)]
yy = vy + vh / 2 - 36
for label, active in stops:
    f = ImageFont.truetype(F + r"\consola.ttf", 13 * S)
    w = d.textlength(label, font=f) / S
    x = rail_right - w
    d.text((px(x), px(yy)), label, font=f, fill=INK if active else blend((3, 3, 4), INK, 0.38))
    if active:
        dot(rail_right + 6, yy + 6, 1.8, GOLD)
    yy += 34
d.line([(px(rail_right - 14), px(yy - 6)), (px(rail_right + 6), px(yy - 6))],
       fill=blend((3, 3, 4), (255, 255, 255), 0.16), width=1)
yy += 6
f_ev = ImageFont.truetype(F + r"\consola.ttf", 11 * S)
ev = "±   +0.3"
w = d.textlength(ev, font=f_ev) / S
d.text((px(rail_right - w), px(yy + 2)), ev, font=f_ev, fill=blend((3, 3, 4), INK, 0.62))

# ---------- bottom body panel from y=690 ----------
panel_y = 690
vgrad(0, panel_y, W, H - panel_y, (22, 24, 27), (14, 15, 17), radius=18)
d.line([(px(0), px(panel_y + 0.8)), (px(W), px(panel_y + 0.8))],
       fill=blend((22, 24, 27), (255, 255, 255), 0.12), width=1)
# a 1px dark line right above gives "cut" depth
d.line([(px(0), px(panel_y + 2)), (px(W), px(panel_y + 2))],
       fill=(0, 0, 0), width=1)

# counter row
spaced(24, panel_y + 18, "EXP", f_mono_s, blend((22, 24, 27), INK, 0.30), sp=px(0.8))
ctr = "05 / 36"
twc = d.textlength(ctr, font=f_mono) / S
d.text((px(W - 24 - twc), px(panel_y + 15)), ctr, font=f_mono, fill=blend((22, 24, 27), INK, 0.55))

# progress: at panel bottom area (y=851)
py = 851
d.line([(px(24), px(py)), (px(W - 24), px(py))], fill=blend((16, 17, 19), (255, 255, 255), 0.07), width=2)
d.line([(px(24), px(py)), (px(24 + (W - 48) * 5 / 36), px(py))], fill=GOLD_DIM, width=2)

# keys
key_y = 792


def key(cx, label, kind):
    f = ImageFont.truetype(F + r"\msyh.ttc", 9 * S)
    ic = blend((18, 20, 22), INK, 0.55)
    if kind == "gallery":
        rrect(cx - 9, key_y - 9, 18, 14, radius=2, outline=ic, width=1)
        dot(cx - 5, key_y - 4, 1.4, ic)
        d.line([(px(cx - 7), px(key_y + 3)), (px(cx + 7), px(key_y + 3))], fill=ic, width=1)
    else:
        rrect(cx - 10, key_y - 8, 20, 13, radius=2, outline=ic, width=1)
        for k in range(4):
            d.line([(px(cx - 8 + k * 5), px(key_y - 6)), (px(cx - 8 + k * 5), px(key_y - 4))], fill=ic, width=1)
            d.line([(px(cx - 8 + k * 5), px(key_y + 2)), (px(cx - 8 + k * 5), px(key_y + 4))], fill=ic, width=1)
    w = d.textlength(label, font=f) / S
    spaced(cx - w / 2, key_y + 14, label, f, blend((18, 20, 22), INK, 0.38), sp=px(0.8))


key(96, "相册", "gallery")
key(316, "换卷", "film")

# shutter (center 800)
scx, scy = W / 2, 800
well_r = 44
d.ellipse([px(scx - well_r - 6), px(scy - well_r - 6), px(scx + well_r + 6), px(scy + well_r + 6)],
          fill=(9, 10, 11))
d.ellipse([px(scx - well_r - 5.5), px(scy - well_r - 5.5), px(scx + well_r + 5.5), px(scy + well_r + 5.5)],
          outline=blend((9, 10, 11), (255, 255, 255), 0.05), width=1)
ring_r = 36
d.ellipse([px(scx - ring_r), px(scy - ring_r), px(scx + ring_r), px(scy + ring_r)],
          outline=blend((20, 22, 24), GOLD, 0.42), width=px(1.2))
for i in range(90):
    a = i * 2 * math.pi / 90
    r0, r1 = ring_r - 3.2, ring_r - 1.0
    col = blend((20, 22, 24), (255, 255, 255), 0.10 if i % 2 else 0.03)
    d.line([(px(scx + r0 * math.cos(a)), px(scy + r0 * math.sin(a))),
            (px(scx + r1 * math.cos(a)), px(scy + r1 * math.sin(a)))], fill=col, width=1)
face_r = 30
for i in range(face_r, 0, -1):
    t = i / face_r
    c = (int(233 - 18 * (1 - t)), int(227 - 20 * (1 - t)), int(216 - 22 * (1 - t)))
    d.ellipse([px(scx - i), px(scy - i), px(scx + i), px(scy + i)], fill=c)
dot(scx, scy, 4, RED)
# face top highlight
d.arc([px(scx - face_r + 2), px(scy - face_r + 2), px(scx + face_r - 2), px(scy + face_r - 2)],
      200, 340, fill=blend((235, 230, 220), (255, 255, 255), 0.5), width=1)

img.save(r"D:\Android\pocket ccd\docs\design\capture-proposal-v3.png")
print("saved")
