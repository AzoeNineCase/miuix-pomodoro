"""对每个步进相位 k 生成极光模型并与截图在“净背景遮罩”内比对，找两图各自的真实相位。

用法: python aurora_phase.py
"""

from PIL import Image

import aurora_fit as A

NATIVE = r"composeApp/build/screenshots/desktop-timer-aurora.png"
WEB = r"C:\Users\simla\AppData\Local\Temp\shot\web-timer-aurora.png"

# 净背景遮罩（图像 px，2560×1600）：排除 rail(x<176)、顶栏(y<176)、两张卡片、迷你按钮
EXCLUDE = [
    (0, 0, 176, 1600),        # 左栏
    (0, 0, 2560, 176),        # 顶栏
    (664, 224, 2072, 502),    # 模式卡片
    (664, 566, 2072, 1566),   # 计时卡片
]


def excluded(x, y):
    for x0, y0, x1, y1 in EXCLUDE:
        if x0 <= x < x1 and y0 <= y < y1:
            return True
    return False


def mask_points(step=6):
    pts = []
    for y in range(180, 1600, step):
        for x in range(180, 2560, step):
            if not excluded(x, y):
                pts.append((x, y))
    return pts


def offset_of(k):
    s = k / 65.0
    return -1024 * s, -128 - 384 * s


def main():
    na = Image.open(NATIVE).convert("RGB")
    we = Image.open(WEB).convert("RGB")
    pa, pw = na.load(), we.load()
    pts = mask_points()
    print(f"mask points: {len(pts)}")

    results = []
    for k in range(131):
        ox, oy = offset_of(k)
        en = ew = 0
        for x, y in pts:
            c = A.color_at(x / 2.0, y / 2.0, ox, oy)
            cn = pa[x, y]
            cw = pw[x, y]
            en += sum(abs(c[i] - cn[i]) for i in range(3))
            ew += sum(abs(c[i] - cw[i]) for i in range(3))
        results.append((k, en / len(pts), ew / len(pts)))
    results.sort(key=lambda r: r[1])
    print("按原生误差排序（前 6）：")
    for k, en, ew in results[:6]:
        print(f"  step {k:3d}  err_native={en:6.2f}  err_web={ew:6.2f}")
    best_web = sorted(results, key=lambda r: r[2])[:6]
    print("按网页误差排序（前 6）：")
    for k, en, ew in best_web:
        print(f"  step {k:3d}  err_native={en:6.2f}  err_web={ew:6.2f}")

    print("自由偏移细搜（原生，20px 粗搜 → 5px 细搜）：")
    best = None
    for ox in range(-900, 200, 20):
        for oy in range(-700, 200, 20):
            e = 0
            for x, y in pts:
                c = A.color_at(x / 2.0, y / 2.0, ox, oy)
                cn = pa[x, y]
                e += sum(abs(c[i] - cn[i]) for i in range(3))
            if best is None or e < best[0]:
                best = (e, ox, oy)
    e0, ox0, oy0 = best
    for ox in range(ox0 - 20, ox0 + 21, 5):
        for oy in range(oy0 - 20, oy0 + 21, 5):
            e = 0
            for x, y in pts:
                c = A.color_at(x / 2.0, y / 2.0, ox, oy)
                cn = pa[x, y]
                e += sum(abs(c[i] - cn[i]) for i in range(3))
            if e < e0:
                e0, ox0, oy0 = e, ox, oy
    print(f"  best native offset=({ox0},{oy0}) err={e0 / len(pts):.2f}")
    print(f"  s_from_x={-ox0 / 1024:.4f}  s_from_y={(-128 - oy0) / 384:.4f}")


if __name__ == "__main__":
    main()
