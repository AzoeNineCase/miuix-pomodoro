"""拟合卡片内部重绘场的偏移：用 saturate(1.5) 后的模型匹配内部采样点。

用法: python aurora_inner.py
"""

from PIL import Image

import aurora_fit as A

NATIVE = r"composeApp/build/screenshots/desktop-timer-aurora.png"

# 卡片内部采样点（css 坐标，避免文字/圆环）：(x, y, 参考色)
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


def main():
    img = Image.open(NATIVE).convert("RGB")
    px = img.load()
    best = None
    for ox in range(-700, 700, 20):
        for oy in range(-700, 700, 20):
            e = 0
            for (x, y) in INNER:
                c = saturate(A.color_at(x, y, ox, oy), 1.5)
                got = px[x * 2, y * 2]
                e += sum(abs(c[i] - got[i]) for i in range(3))
            if best is None or e < best[0]:
                best = (e, ox, oy)
    e0, ox0, oy0 = best
    for ox in range(ox0 - 24, ox0 + 25, 4):
        for oy in range(oy0 - 24, oy0 + 25, 4):
            e = 0
            for (x, y) in INNER:
                c = saturate(A.color_at(x, y, ox, oy), 1.5)
                got = px[x * 2, y * 2]
                e += sum(abs(c[i] - got[i]) for i in range(3))
            if e < e0:
                e0, ox0, oy0 = e, ox, oy
    print(f"best inner offset = ({ox0}, {oy0})  err={e0 / len(INNER):.1f}")
    print("对比：期望内部偏移 = 基础偏移 (-31.5, -139.8)")
    for (x, y) in INNER:
        c = saturate(A.color_at(x, y, ox0, oy0), 1.5)
        got = px[x * 2, y * 2]
        print(f"  ({x:4d},{y:4d}) model=({c[0]:5.0f},{c[1]:5.0f},{c[2]:5.0f}) img={got}")


if __name__ == "__main__":
    main()
