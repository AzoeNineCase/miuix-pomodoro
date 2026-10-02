"""在指定列带内查找“深色文字行”的 y 区间，用于对齐两图的行基线。

用法:
    python text_rows.py <img.png> <x0> <x1> [thr=110] [minw=3]

输出: 每行一个 `y0..y1 (h=.., cy=..)`，minw 为行内至少多少个像素低于阈值。
"""

import sys

from PIL import Image


def main():
    img = Image.open(sys.argv[1]).convert("L")
    x0, x1 = int(sys.argv[2]), int(sys.argv[3])
    thr = int(sys.argv[4]) if len(sys.argv) > 4 else 110
    minw = int(sys.argv[5]) if len(sys.argv) > 5 else 3
    px = img.load()
    runs = []
    start = None
    for y in range(img.height):
        cnt = 0
        for x in range(x0, x1):
            if px[x, y] < thr:
                cnt += 1
                if cnt >= minw:
                    break
        dark = cnt >= minw
        if dark and start is None:
            start = y
        elif not dark and start is not None:
            runs.append((start, y - 1))
            start = None
    if start is not None:
        runs.append((start, img.height - 1))
    for a, b in runs:
        print(f"y0={a} y1={b} (h={b - a + 1}, cy={(a + b) / 2:.1f})")


if __name__ == "__main__":
    main()
