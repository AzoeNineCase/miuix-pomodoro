"""在指定行带内查找“深色文字列”的 x 区间（text_rows 的横向版本）。

用法:
    python text_cols.py <img.png> <y0> <y1> <x0> <x1> [thr=100] [minh=1]

输出: 每行一个 `x0..x1 (w=.., cx=..)`。
"""

import sys

from PIL import Image


def main():
    img = Image.open(sys.argv[1]).convert("L")
    y0, y1, x0, x1 = (int(v) for v in sys.argv[2:6])
    thr = int(sys.argv[6]) if len(sys.argv) > 6 else 100
    minh = int(sys.argv[7]) if len(sys.argv) > 7 else 1
    px = img.load()
    runs = []
    start = None
    for x in range(x0, x1):
        cnt = 0
        for y in range(y0, y1):
            if px[x, y] < thr:
                cnt += 1
                if cnt >= minh:
                    break
        dark = cnt >= minh
        if dark and start is None:
            start = x
        elif not dark and start is not None:
            runs.append((start, x - 1))
            start = None
    if start is not None:
        runs.append((start, x1 - 1))
    for a, b in runs:
        print(f"x0={a} x1={b} (w={b - a + 1}, cx={(a + b) / 2:.1f})")


if __name__ == "__main__":
    main()
