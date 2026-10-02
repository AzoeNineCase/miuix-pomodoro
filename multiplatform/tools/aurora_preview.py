"""把极光模型按指定偏移渲染成小图，与截图裁剪并排，用于人工确认相位。

用法: python aurora_preview.py <out.png>
"""

import sys

from PIL import Image, ImageDraw

import aurora_fit as A

NATIVE = r"composeApp/build/screenshots/desktop-timer-aurora.png"
WEB = r"C:\Users\simla\AppData\Local\Temp\shot\web-timer-aurora.png"

# 抽样区域（图像 px）：计时卡片区域
REGION = (700, 600, 2040, 1520)
STEP = 16


def model_region(ox, oy):
    w = (REGION[2] - REGION[0]) // STEP
    h = (REGION[3] - REGION[1]) // STEP
    img = Image.new("RGB", (w, h))
    px = img.load()
    for j in range(h):
        y = REGION[1] + j * STEP
        for i in range(w):
            x = REGION[0] + i * STEP
            c = A.color_at(x / 2.0, y / 2.0, ox, oy)
            px[i, j] = tuple(max(0, min(255, int(round(v)))) for v in c)
    return img


def crop_of(path):
    img = Image.open(path).convert("RGB")
    c = img.crop(REGION)
    return c.resize((c.width // STEP, c.height // STEP), Image.NEAREST)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "preview.png"
    tiles = [
        ("native", crop_of(NATIVE)),
        ("web", crop_of(WEB)),
        ("model base (-31.5,-139.8)", model_region(-31.5, -139.8)),
    ]
    w, h = tiles[0][1].size
    canvas = Image.new("RGB", ((w + 6) * len(tiles), h + 18), (20, 20, 20))
    d = ImageDraw.Draw(canvas)
    for i, (name, img) in enumerate(tiles):
        canvas.paste(img, (i * (w + 6), 18))
        d.text((i * (w + 6) + 4, 4), name, fill=(255, 255, 255))
    canvas.save(out)
    print("saved", out, canvas.size)


if __name__ == "__main__":
    main()
