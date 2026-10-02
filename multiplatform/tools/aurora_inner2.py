"""拟合卡片内部重绘场：saturate(1.5) + 缩放 k + 偏移 (ox,oy) 的模型匹配内部采样点。

用法: python aurora_inner.py
"""

from PIL import Image

import aurora_fit as A

NATIVE = r"composeApp/build/screenshots/desktop-timer-aurora.png"

# 卡片内部干净采样点（css 坐标，避开文字/圆环/按钮）
INNER = [
    (400, 400), (900, 400), (400, 700), (900, 700), (380, 620), (980, 620), (400, 300), (900, 300),
]


def saturate(c, sat):
    r, g, b = 0.213, 0.715, 0.072
    inv = 1 - sat
    cr, cg, cb = c
    return (
        (r * inv + sat) * cr + g * inv * cg + b * inv * cb,
        r * inv * cr + (g * inv + sat) * cg + b * inv * cb,
        r * inv * cr + g * inv * cg + (b * inv + sat) * cb,
    )


def score(px, k, ox, oy):
    e = 0.0
    for (x, y) in INNER:
        c = saturate(A.color_at(x * k, y * k, ox, oy), 1.5)
        got = px[x * 2, y * 2]
        e += sum(abs(c[i] - got[i]) for i in range(3))
    return e


def main():
    img = Image.open(NATIVE).convert("RGB")
    px = img.load()
    best = None
    for k in (0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.8, 2.0):
        for ox in range(-900, 900, 60):
            for oy in range(-900, 900, 60):
                e = score(px, k, ox, oy)
                if best is None or e < best[0]:
                    best = (e, k, ox, oy)
    e0, k0, ox0, oy0 = best
    for k in [k0 - 0.1, k0, k0 + 0.1]:
        if k <= 0:
            continue
        for ox in range(ox0 - 60, ox0 + 61, 15):
            for oy in range(oy0 - 60, oy0 + 61, 15):
                e = score(px, k, ox, oy)
                if e < e0:
                    e0, k0, ox0, oy0 = e, k, ox, oy
    print(f"best inner: scale={k0:.2f} offset=({ox0},{oy0})  err={e0 / len(INNER):.1f}")
    for (x, y) in INNER:
        c = saturate(A.color_at(x * k0, y * k0, ox0, oy0), 1.5)
        got = px[x * 2, y * 2]
        print(f"  ({x:4d},{y:4d}) model=({c[0]:5.0f},{c[1]:5.0f},{c[2]:5.0f}) img={got}")


if __name__ == "__main__":
    main()
