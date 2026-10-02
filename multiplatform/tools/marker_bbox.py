"""测量调试标记的精确位置：扫描纯色 (255,0,0)/(0,255,0)/(0,0,255) 的包围盒。

用法: python marker_bbox.py
"""

from PIL import Image

PATH = r"E:\ke\cook\multiplatform\composeApp\build\screenshots\desktop-timer-aurora.png"


def bbox(px, w, h, target, tol=10):
    x0 = y0 = 10 ** 9
    x1 = y1 = -1
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            c = px[x, y]
            if abs(c[0] - target[0]) <= tol and abs(c[1] - target[1]) <= tol and abs(c[2] - target[2]) <= tol:
                x0 = min(x0, x)
                x1 = max(x1, x)
                y0 = min(y0, y)
                y1 = max(y1, y)
    if x1 < 0:
        return None
    return (x0, y0, x1, y1)


img = Image.open(PATH).convert("RGB")
w, h = img.size
px = img.load()
print("size", img.size)
for name, t in (("red(rail)", (255, 0, 0)), ("green(topbar)", (0, 255, 0)), ("blue(card)", (0, 0, 255))):
    b = bbox(px, w, h, t)
    if b:
        print(f"{name}: img {b}  css ({b[0]/2:.1f},{b[1]/2:.1f})..({b[2]/2:.1f},{b[3]/2:.1f})")
    else:
        print(f"{name}: not found")
