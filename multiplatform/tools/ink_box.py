"""测量截图中某个区域内「非背景」像素的包围盒（文字/图标实际墨迹范围）。

用法: python ink_box.py <img.png> <x0> <y0> <x1> <y1> [thr=10]
输出: 墨迹 bbox（x0,y0,x1,y1）与宽高；多张图可依次传入，格式:
      python ink_box.py A.png B.png <x0> <y0> <x1> <y1> [thr]
"""

import sys
from collections import Counter

from PIL import Image


def ink_bbox(img, box, thr):
    x0, y0, x1, y1 = box
    crop = img.crop(box)
    px = list(crop.getdata())
    bg = Counter(px).most_common(1)[0][0]
    xs, ys = [], []
    for y in range(crop.height):
        for x in range(crop.width):
            p = crop.getpixel((x, y))
            if abs(p[0] - bg[0]) + abs(p[1] - bg[1]) + abs(p[2] - bg[2]) > thr:
                xs.append(x)
                ys.append(y)
    if not xs:
        return None
    return (x0 + min(xs), y0 + min(ys), x0 + max(xs), y0 + max(ys))


def main():
    args = sys.argv[1:]
    # 收集图片路径（参数里以 .png 结尾的）
    imgs = [a for a in args if a.lower().endswith(".png")]
    rest = [a for a in args if not a.lower().endswith(".png")]
    if len(imgs) < 1 or len(rest) < 4:
        print(__doc__)
        return 1
    x0, y0, x1, y1 = (int(v) for v in rest[:4])
    thr = int(rest[4]) if len(rest) > 4 else 10
    for path in imgs:
        img = Image.open(path).convert("RGB")
        bb = ink_bbox(img, (x0, y0, x1, y1), thr)
        name = path.replace("\\", "/").split("/")[-1]
        if bb is None:
            print(f"{name:30s} 区域内无墨迹")
        else:
            print(f"{name:30s} bbox=({bb[0]},{bb[1]})-({bb[2]},{bb[3]})  w={bb[2] - bb[0] + 1} h={bb[3] - bb[1] + 1}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
