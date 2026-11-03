"""从 Material Symbols Rounded 官方 SVG 生成 Android vector drawable。

用途：把 ui/glass 的手画图标换成正版 Material Symbols（Apache-2.0）。
不引 material-icons-extended：那套包几十 MB，而 release 没开 R8，图标类会全量留下。

**关键陷阱（踩过一次，全 App 图标变空白）**：Material Symbols 的 viewBox 是
`0 -960 960 960`（原点在左上方的负 Y 方向），而 Android vector 的视口永远是
`(0,0)-(w,h)`、没有 viewBox 原点这一说。直接照抄 viewport 会把所有路径画到画布
上方 —— 编译不报错、aapt2 也不报错，运行时就是一片空白。所以负原点的图标必须用
`<group android:translateX/Y>` 平移到 0,0。

运行：
    python tools/gen_glass_icons.py            # 生成
    python tools/gen_glass_icons.py --check    # 只校验 bbox 是否落在视口内
输出：
    app/src/main/res/drawable/ic_glass_<kind>.xml
"""

import os
import re
import sys
import urllib.request

BASE = "https://cdn.jsdelivr.net/npm/@material-symbols/svg-400/rounded/"
OUT = os.path.join("app", "src", "main", "res", "drawable")

# kind -> (material symbols 名, 有没有 -fill 变体)
ICONS = {
    "Gallery": ("photo_library", False),
    "Film": ("theaters", False),
    "Aperture": ("photo_camera", True),
    "Sparkle": ("auto_awesome", True),
    "Gear": ("settings", False),
    "Flash": ("flash_on", False),
    "FlashOff": ("flash_off", False),
    "Grid": ("grid_on", False),
    "Close": ("close", False),
    "Back": ("arrow_back_ios_new", False),
    "Chevron": ("chevron_right", False),
    "Share": ("ios_share", False),
    "Heart": ("favorite", True),
    "Refresh": ("refresh", False),
    "Info": ("info", False),
    "Warning": ("warning", False),
    "Check": ("check", False),
    "Zoom": ("search", False),
    "Exposure": ("exposure", False),
    "Add": ("add", False),
    "Remove": ("remove", False),
}

PATH_RE = re.compile(r"<path\b([^>]*)/?>", re.S)
D_RE = re.compile(r'\bd="([^"]+)"')
RULE_RE = re.compile(r'fill-rule="(evenodd|nonzero)"')
VIEWBOX_RE = re.compile(r'viewBox="([^"]+)"')

TOKEN_RE = re.compile(
    r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?"
)
ARGS = {"M": 2, "L": 2, "T": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "A": 7, "Z": 0}


def fetch(name: str) -> str:
    with urllib.request.urlopen(BASE + name + ".svg", timeout=30) as response:
        return response.read().decode("utf-8")


def parse_paths(svg: str):
    paths = []
    for attrs in PATH_RE.findall(svg):
        d = D_RE.search(attrs)
        if not d:
            continue
        rule = RULE_RE.search(attrs)
        paths.append((d.group(1), rule.group(1) if rule else None))
    return paths


def flatten_points(d: str):
    """保守地取路径上的所有点（含贝塞尔控制点），用于算 bbox。"""
    tokens = TOKEN_RE.findall(d)
    points = []
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    index = 0
    command = None
    while index < len(tokens):
        token = tokens[index]
        if token.isalpha():
            command = token
            index += 1
            if command in "Zz":
                cur = start
                continue
        if command is None:
            raise SystemExit("路径以数字开头：" + d[:40])
        upper = command.upper()
        count = ARGS[upper]
        if count == 0:
            continue
        args = [float(tokens[index + i]) for i in range(count)]
        index += count
        relative = command.islower()
        if upper in ("M", "L", "T"):
            x, y = args
            cur = (cur[0] + x, cur[1] + y) if relative else (x, y)
            if upper == "M":
                start = cur
                command = "l" if relative else "L"
        elif upper == "H":
            cur = (cur[0] + args[0], cur[1]) if relative else (args[0], cur[1])
        elif upper == "V":
            cur = (cur[0], cur[1] + args[0]) if relative else (cur[0], args[0])
        elif upper in ("C", "S", "Q"):
            for offset in range(0, count, 2):
                x, y = args[offset], args[offset + 1]
                point = (cur[0] + x, cur[1] + y) if relative else (x, y)
                points.append(point)
            cur = points[-1]
        elif upper == "A":
            x, y = args[5], args[6]
            cur = (cur[0] + x, cur[1] + y) if relative else (x, y)
        points.append(cur)
    return points


def bbox(paths):
    xs, ys = [], []
    for d, _ in paths:
        for x, y in flatten_points(d):
            xs.append(x)
            ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def vector_xml(svg: str) -> str:
    view_box = VIEWBOX_RE.search(svg)
    if not view_box:
        raise SystemExit("SVG 没有 viewBox")
    min_x, min_y, width, height = (float(v) for v in view_box.group(1).split())
    paths = parse_paths(svg)
    if not paths:
        raise SystemExit("SVG 里没有 path")

    body = []
    for d, rule in paths:
        fill_type = ' android:fillType="evenOdd"' if rule == "evenodd" else ""
        body.append(
            '        <path\n'
            '            android:fillColor="#FF000000"%s\n'
            '            android:pathData="%s" />' % (fill_type, d)
        )

    inner = "\n".join(body)
    if min_x != 0 or min_y != 0:
        # 负原点：必须平移，否则路径整个落在视口外（图标"不显示"就是这么来的）。
        inner = (
            '    <group\n'
            '        android:translateX="%g"\n'
            '        android:translateY="%g">\n' % (-min_x, -min_y)
            + inner
            + "\n    </group>"
        )

    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="%g"\n'
        '    android:viewportHeight="%g">\n' % (width, height)
        + inner
        + "\n</vector>\n"
    )


def write(kind: str, name: str, suffix: str) -> str:
    svg = fetch(name + suffix)
    xml = vector_xml(svg)
    file_name = (
        "ic_glass_" + re.sub(r"(?<!^)(?=[A-Z])", "_", kind).lower() + ("_fill" if suffix else "") + ".xml"
    )
    with open(os.path.join(OUT, file_name), "w", encoding="utf-8", newline="\n") as handle:
        handle.write(xml)
    return file_name


def check() -> int:
    """校验每个矢量：平移后的 bbox 必须落在视口内，且占据足够面积。"""
    failures = 0
    for file in sorted(os.listdir(OUT)):
        if not (file.startswith("ic_glass_") and file.endswith(".xml")):
            continue
        xml = open(os.path.join(OUT, file), encoding="utf-8").read()
        width = float(re.search(r'android:viewportWidth="([\d.]+)"', xml).group(1))
        height = float(re.search(r'android:viewportHeight="([\d.]+)"', xml).group(1))
        shift_x = re.search(r'android:translateX="(-?[\d.]+)"', xml)
        shift_y = re.search(r'android:translateY="(-?[\d.]+)"', xml)
        dx = float(shift_x.group(1)) if shift_x else 0.0
        dy = float(shift_y.group(1)) if shift_y else 0.0
        paths = [
            (d, None) for d in re.findall(r'android:pathData="([^"]+)"', xml)
        ]
        min_x, min_y, max_x, max_y = bbox(paths)
        min_x, max_x = min_x + dx, max_x + dx
        min_y, max_y = min_y + dy, max_y + dy
        inside = min_x >= -1 and min_y >= -1 and max_x <= width + 1 and max_y <= height + 1
        coverage = ((max_x - min_x) * (max_y - min_y)) / (width * height)
        # 只看"是否落在视口内"：细长图标（chevron/remove）面积天然很小，不能用覆盖率判失败。
        status = "ok" if inside and coverage > 0.01 else "FAIL"
        if status == "FAIL":
            failures += 1
        print(
            "%-28s bbox=(%6.1f,%6.1f)-(%6.1f,%6.1f) viewport=%gx%g coverage=%.0f%% %s"
            % (file, min_x, min_y, max_x, max_y, width, height, coverage * 100, status)
        )
    print("\nfailures:", failures)
    return failures


def main() -> None:
    if "--check" in sys.argv:
        raise SystemExit(1 if check() else 0)
    os.makedirs(OUT, exist_ok=True)
    for kind, (name, has_fill) in ICONS.items():
        print(kind, "->", write(kind, name, ""))
        if has_fill:
            print(kind, "fill ->", write(kind, name, "-fill"))
    print("\n校验：")
    check()


if __name__ == "__main__":
    main()
