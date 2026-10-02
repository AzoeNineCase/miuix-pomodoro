"""裁剪两张截图的同一区域并纵向拼图（原生在上、网页在下），用于放大排查细节。

用法:
    python crop_pair.py <A.png> <B.png> <x0> <y0> <x1> <y1> <out.png> [scale=1]

可先画几条参考线：拼图左侧每 100px 标一根短横线（便于对齐读数）。
"""

import sys

from PIL import Image, ImageDraw


def main():
    a_path, b_path = sys.argv[1], sys.argv[2]
    x0, y0, x1, y1 = (int(v) for v in sys.argv[3:7])
    out = sys.argv[7]
    scale = float(sys.argv[8]) if len(sys.argv) > 8 else 1.0

    a = Image.open(a_path).convert("RGB").crop((x0, y0, x1, y1))
    b = Image.open(b_path).convert("RGB").crop((x0, y0, x1, y1))
    if scale != 1.0:
        size = (int(a.width * scale), int(a.height * scale))
        a = a.resize(size, Image.LANCZOS)
        b = b.resize(size, Image.LANCZOS)

    gap = 6
    canvas = Image.new("RGB", (a.width, a.height * 2 + gap), (255, 0, 0))
    canvas.paste(a, (0, 0))
    canvas.paste(b, (0, a.height + gap))

    d = ImageDraw.Draw(canvas)
    # 每 50 源像素（缩放后 50*scale）一根刻度线：左缘 24px，上块白线、下块黑线
    step = int(50 * scale)
    for i in range(0, canvas.height, step):
        color = (255, 255, 0) if i < a.height else (255, 0, 255)
        d.line([(0, i), (24, i)], fill=color, width=1)
    canvas.save(out)
    print(f"saved {out}  ({canvas.width}x{canvas.height}, 上=原生 下=网页, 线距 {50}px@src)")


if __name__ == "__main__":
    main()
