"""采样若干坐标的像素值（多图对比）。

用法: python sample.py <imgA.png> [imgB.png ...] "<x,y> <x,y> ..."
"""

import sys

from PIL import Image


def main():
    args = sys.argv[1:]
    imgs = [a for a in args if a.lower().endswith(".png")]
    rest = [a for a in args if not a.lower().endswith(".png")]
    if not imgs or not rest:
        print(__doc__)
        return 1
    pts = []
    for tok in rest[0].split():
        x, y = tok.split(",")
        pts.append((int(x), int(y)))
    for path in imgs:
        img = Image.open(path).convert("RGB")
        name = path.replace("\\", "/").split("/")[-1]
        vals = " ".join(f"({x},{y})={img.getpixel((x, y))}" for x, y in pts)
        print(f"{name:30s} {vals}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
