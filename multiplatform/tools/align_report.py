"""报告原生/网页截图的「分块最佳对齐位移」，用于定位几何错位（而非光栅化差异）。

思路：把画面按水平条带切块，对每块搜索使差异最小的整体位移 (dx, dy)（整数像素）。
若某条带的最佳位移明显非 0，说明该区块在原生与网页间存在系统性错位，应优先修。

用法: python align_report.py <native.png> <web.png> [bands=16] [max_shift=6]
"""

import sys

import numpy as np
from PIL import Image


def load(path):
    return np.asarray(Image.open(path).convert("L"), dtype=np.float32)


def best_shift(a, b, rng):
    """在 [-rng, rng] 内搜索使 a 与 b 差异最小的 (dx, dy)。"""
    h, w = a.shape
    m = rng
    core_a = a[m:h - m, m:w - m]
    best = (0, 0, float(mse(core_a, b[m:h - m, m:w - m])))
    for dy in range(-rng, rng + 1):
        for dx in range(-rng, rng + 1):
            if dx == 0 and dy == 0:
                continue
            core_b = b[m + dy:h - m + dy, m + dx:w - m + dx]
            v = mse(core_a, core_b)
            if v < best[2]:
                best = (dx, dy, float(v))
    return best


def mse(x, y):
    d = x - y
    return (d * d).mean()


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    a = load(sys.argv[1])
    b = load(sys.argv[2])
    if a.shape != b.shape:
        h = min(a.shape[0], b.shape[0])
        w = min(a.shape[1], b.shape[1])
        a, b = a[:h, :w], b[:h, :w]
    bands = int(sys.argv[3]) if len(sys.argv) > 3 else 16
    rng = int(sys.argv[4]) if len(sys.argv) > 4 else 6

    h = a.shape[0]
    step = h // bands
    name = sys.argv[1].replace("\\", "/").split("/")[-1]
    print(f"{name}: {a.shape[1]}x{a.shape[0]}, {bands} bands, rng=±{rng}")
    for i in range(bands):
        y0, y1 = i * step, min(h, (i + 1) * step)
        if y1 - y0 <= 2 * rng + 2:
            continue
        base = float(mse(a[y0 + rng:y1 - rng, rng:a.shape[1] - rng],
                         b[y0 + rng:y1 - rng, rng:a.shape[1] - rng]))
        dx, dy, v = best_shift(a[y0:y1], b[y0:y1], rng)
        flag = "  <-- 错位" if (abs(dx) > 0 or abs(dy) > 0) and base > 0.2 else ""
        print(f"  y={y0:5d}-{y1:5d}  best dx={dx:+d} dy={dy:+d} | 差 {base:6.2f} -> {v:6.2f}{flag}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
