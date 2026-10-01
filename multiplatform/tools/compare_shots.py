"""原生截图 vs 网页截图 逐像素比对。

用法:
    python compare_shots.py <native_dir> <web_dir> <out_dir> [pairs...]

默认比对这几对（页面-主题）：
    desktop-timer-dark / web-timer-dark
    desktop-timer-light / web-timer-light
    desktop-stats-dark / web-stats-dark
    desktop-settings-light / web-settings-light
    desktop-todos-light / web-todos-light

输出：
    - 控制台：每对的尺寸、平均差、最大差、>16 的像素占比、以及差异最大的 5 个行带（便于定位是哪个区块不一致）
    - 输出目录：<name>-diff.png（差值热力图）与 <name>-side.png（左右并排）
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops

PAIRS = [
    ("desktop-timer-dark", "web-timer-dark"),
    ("desktop-timer-light", "web-timer-light"),
    ("desktop-stats-dark", "web-stats-dark"),
    ("desktop-settings-light", "web-settings-light"),
    ("desktop-todos-light", "web-todos-light"),
]


def band_report(diff: Image.Image, bands: int = 20):
    """按水平条带统计差异，帮助定位不一致的区块"""
    w, h = diff.size
    step = max(1, h // bands)
    rows = []
    for top in range(0, h, step):
        box = (0, top, w, min(h, top + step))
        crop = diff.crop(box)
        stat = crop.resize((1, 1), Image.BOX)
        rows.append((top, stat.getpixel((0, 0))))
    rows.sort(key=lambda r: r[1], reverse=True)
    return rows[:5]


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 1
    native_dir = Path(sys.argv[1])
    web_dir = Path(sys.argv[2])
    out_dir = Path(sys.argv[3])
    out_dir.mkdir(parents=True, exist_ok=True)

    for native_name, web_name in PAIRS:
        npath = native_dir / f"{native_name}.png"
        wpath = web_dir / f"{web_name}.png"
        if not npath.exists() or not wpath.exists():
            print(f"[skip] {native_name}: 缺少文件 ({npath.exists()=} {wpath.exists()=})")
            continue

        a = Image.open(npath).convert("RGB")
        b = Image.open(wpath).convert("RGB")
        if a.size != b.size:
            print(f"[warn] {native_name}: 尺寸不同 原生={a.size} 网页={b.size}，按左上角裁剪到较小尺寸")
            w = min(a.size[0], b.size[0])
            h = min(a.size[1], b.size[1])
            a = a.crop((0, 0, w, h))
            b = b.crop((0, 0, w, h))

        diff = ImageChops.difference(a, b)
        gray = diff.convert("L")
        hist = gray.histogram()
        total = sum(hist)
        mean = sum(i * c for i, c in enumerate(hist)) / total
        maxv = max(i for i, c in enumerate(hist) if c)
        over16 = sum(hist[16:]) / total * 100
        over32 = sum(hist[32:]) / total * 100

        print(f"\n=== {native_name} vs {web_name} ({a.size[0]}x{a.size[1]}) ===")
        print(f"  平均差 {mean:6.2f} | 最大差 {maxv:3d} | >16 像素 {over16:5.2f}% | >32 像素 {over32:5.2f}%")
        for top, val in band_report(gray):
            print(f"    y={top:5d} 平均差 {val}")

        # 热力图（差异放大 4 倍便于观察）
        heat = gray.point(lambda v: min(255, v * 4))
        heat.save(out_dir / f"{native_name}-diff.png")

        # 并排图（原生 | 网页），缩放到一半宽
        half = (a.size[0] // 2, a.size[1] // 2)
        side = Image.new("RGB", (half[0] * 2, half[1]))
        side.paste(a.resize(half), (0, 0))
        side.paste(b.resize(half), (half[0], 0))
        side.save(out_dir / f"{native_name}-side.png")

    print(f"\n输出目录: {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
