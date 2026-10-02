"""极光背景拟合：按 CSS 定义渲染 .aurora-bg > i（135° 线性底 + 4 椭圆径向光斑），
对给定 (层偏移, 相位) 计算理论色，并与网页/原生截图采样点对比，找出原生实际相位。

用法:
    python aurora_fit.py                # 用内置采样点对比 web/native 理论值
"""

import math

# 页面 CSS 参数（css px；视口 1280×800）
W, H = 1280.0, 800.0
LW, LH = W * 1.8, H * 1.8
LIN = [(0.0, (0x08, 0x0A, 0x1C)), (0.52, (0x14, 0x10, 0x2E)), (1.0, (0x0B, 0x10, 0x30))]
BLOBS = [
    (0.18, 0.22, 0.58, (124, 77, 255), 0.60),
    (0.82, 0.16, 0.48, (0, 188, 212), 0.42),
    (0.72, 0.82, 0.52, (52, 130, 255), 0.50),
    (0.26, 0.84, 0.46, (236, 72, 153), 0.36),
]
STOP = 0.62


def lin_color(px, py):
    # 135deg：轴长 w·|sin|+h·|cos|、过中心；d=(0.7071,0.7071)
    d = 0.70710678
    length = LW * d + LH * d
    cx, cy = LW / 2, LH / 2
    sx, sy = cx - d * length / 2, cy - d * length / 2
    t = ((px - sx) * d + (py - sy) * d) / length
    t = min(max(t, 0.0), 1.0)
    for i in range(len(LIN) - 1):
        t0, c0 = LIN[i]
        t1, c1 = LIN[i + 1]
        if t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return tuple(c0[k] + (c1[k] - c0[k]) * f for k in range(3))
    return LIN[-1][1]


def color_at(css_x, css_y, ox, oy):
    px, py = css_x - ox, css_y - oy
    col = list(lin_color(px, py))
    for fx, fy, r, bc, a in BLOBS:
        ccx, ccy = LW * fx, LH * fy
        rx, ry = LW * r, LH * r
        rr = math.hypot((px - ccx) / rx, (py - ccy) / ry)
        alpha = a * max(0.0, 1.0 - rr / STOP)
        for k in range(3):
            col[k] = col[k] * (1 - alpha) + bc[k] * alpha
    return col


# (css 点, 原生 RGB, 网页 RGB) —— 均取自极光用例的纯背景采样
POINTS = [
    ((700, 265), (20, 16, 46), (44, 28, 95)),
    ((1100, 400), (23, 34, 80), (21, 17, 47)),
    ((1125, 450), (25, 40, 91), (19, 15, 44)),
    ((1050, 600), (30, 58, 123), (17, 14, 42)),
    ((1075, 775), (32, 66, 138), (21, 19, 52)),
    ((1200, 400), (22, 34, 78), (19, 15, 44)),
    ((1150, 200), (17, 16, 47), (22, 18, 51)),
    ((700, 795), (27, 39, 91), (35, 17, 46)),
    ((1050, 792), (31, 64, 133), (20, 18, 50)),
]


def err(ox, oy, idx):
    e = 0.0
    for (x, y), a, b in POINTS:
        c = color_at(x, y, ox, oy)
        ref = a if idx == 0 else b
        e += sum(abs(c[k] - ref[k]) for k in range(3))
    return e / len(POINTS)


def main():
    print("=== 逐点对比 @step2 (-31.5,-139.8) ===")
    for (x, y), a, b in POINTS:
        c = color_at(x, y, -31.5, -139.8)
        print(f"({x:4d},{y:4d}) model=({c[0]:5.0f},{c[1]:5.0f},{c[2]:5.0f})  native={a}  web={b}")
    print()
    print("=== 步进相位候选（step→偏移 css）===")
    for k in range(0, 131, 1):
        s = k / 65.0
        ox = -1024 * s
        oy = -128 - 384 * s
        if k <= 8 or k % 10 == 0:
            print(f"step {k:3d}  ox={ox:8.1f} oy={oy:8.1f}  err_native={err(ox, oy, 0):6.1f}  err_web={err(ox, oy, 1):6.1f}")
    print()
    print()
    print("=== 细化搜索 网页偏移 ===")
    best = None
    for ox in range(-300, 100, 5):
        for oy in range(-400, 100, 5):
            e = err(ox, oy, 1)
            if best is None or e < best[0]:
                best = (e, ox, oy)
    e, ox, oy = best
    print(f"best web offset: ox={ox} oy={oy} err={e:.2f}")
    print(f"  s_from_x = {-ox / 1024:.4f}   s_from_y = {(-128 - oy) / 384:.4f}")
    for (x, y), a, b in POINTS:
        c = color_at(x, y, ox, oy)
        print(f"    ({x:4d},{y:4d}) model=({c[0]:5.0f},{c[1]:5.0f},{c[2]:5.0f}) web={b}")




if __name__ == "__main__":
    main()
