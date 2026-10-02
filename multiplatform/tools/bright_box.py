"""亮度阈值包围盒：提取比某亮度更亮的像素范围（用于量高对比度文字，忽略低对比发光）。

用法: python bright_box.py <imgA.png> [imgB.png ...] <x0> <y0> <x1> <y1> <L>
"""

import sys

import numpy as np
from PIL import Image


def main():
    args = sys.argv[1:]
    imgs = [a for a in args if a.lower().endswith(".png")]
    rest = [a for a in args if not a.lower().endswith(".png")]
    if not imgs or len(rest) < 5:
        print(__doc__)
        return 1
    x0, y0, x1, y1, lum = (int(v) for v in rest[:5])
    for path in imgs:
        a = np.asarray(Image.open(path).convert("L"), dtype=np.int16)[y0:y1, x0:x1]
        ys, xs = np.where(a >= lum)
        name = path.replace("\\", "/").split("/")[-1]
        if len(xs) == 0:
            print(f"{name:30s} 无亮于 {lum} 的像素")
            continue
        print(f"{name:30s} bbox=({x0 + xs.min()},{y0 + ys.min()})-({x0 + xs.max()},{y0 + ys.max()})  "
              f"w={xs.max() - xs.min() + 1} h={ys.max() - ys.min() + 1}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
