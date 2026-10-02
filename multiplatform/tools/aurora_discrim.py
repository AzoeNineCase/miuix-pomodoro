"""快速判别：用少量采样点对比两候选偏移（TopStart vs 居中）与当前截图。

用法: python aurora_discrim.py
"""

from PIL import Image

import aurora_fit as A

NATIVE = r"composeApp/build/screenshots/desktop-timer-aurora.png"

# 判别点（css）：避开 rail(x<88)、顶栏(y<88)、卡片(332..1036 × 224..1566)
PTS = [(200, 400), (200, 600), (200, 750), (150, 300), (150, 500), (150, 700), (250, 780)]

CANDIDATES = {
    "TopStart + step0 (0,-128)": (0, -128),
    "Centered + step0 (-512,-448)": (-512, -448),
    "Centered + step2 (-543.5,-459.8)": (-543.5, -459.8),
}


def main():
    img = Image.open(NATIVE).convert("RGB")
    px = img.load()
    for name, (ox, oy) in CANDIDATES.items():
        err = 0
        print(f"--- {name}")
        for (x, y) in PTS:
            c = A.color_at(x, y, ox, oy)
            got = px[x * 2, y * 2]
            e = sum(abs(c[i] - got[i]) for i in range(3))
            err += e
            print(f"   ({x:4d},{y:4d}) model=({c[0]:5.0f},{c[1]:5.0f},{c[2]:5.0f}) img={got}  err={e}")
        print(f"   总误差 {err}")


if __name__ == "__main__":
    main()
