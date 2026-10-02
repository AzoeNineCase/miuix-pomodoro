"""沿水平/垂直方向输出两图彩色剖面，用于排查径向光晕/描边差异。

用法:
    python profile.py <A.png> <B.png> row <y> <x0> <x1> [step=8]
    python profile.py <A.png> <B.png> col <x> <y0> <y1> [step=8]
"""

import sys

from PIL import Image


def main():
    a_path, b_path = sys.argv[1], sys.argv[2]
    mode, fixed, p0, p1 = sys.argv[3], int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
    step = int(sys.argv[7]) if len(sys.argv) > 7 else 8
    a = Image.open(a_path).convert("RGB")
    b = Image.open(b_path).convert("RGB")
    center = (p0 + p1) // 2
    print(f"{'d':>5} {'A':>16} {'B':>16}  diff")
    for v in range(p0, p1 + 1, step):
        pos = (v, fixed) if mode == "row" else (fixed, v)
        pa = a.getpixel(pos)
        pb = b.getpixel(pos)
        d = max(abs(pa[i] - pb[i]) for i in range(3))
        print(f"{v - center:>5} {str(pa):>16} {str(pb):>16}  {d}")


if __name__ == "__main__":
    main()
