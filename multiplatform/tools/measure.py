"""量测两张截图的关键几何位置（用于对齐原生与网页版）。
用法: python measure.py <imgA> <imgB> [y] [x]
"""
import sys
from PIL import Image


def bbox_along_row(img, y, bg, tol=6):
    w = img.size[0]
    xs = [x for x in range(w) if sum(abs(a - b) for a, b in zip(img.getpixel((x, y))[:3], bg)) > tol]
    return (min(xs), max(xs)) if xs else None


def bbox_along_col(img, x, bg, tol=6):
    h = img.size[1]
    ys = [y for y in range(h) if sum(abs(a - b) for a, b in zip(img.getpixel((x, y))[:3], bg)) > tol]
    return (min(ys), max(ys)) if ys else None


def name_of(path):
    return path.replace("\\", "/").split("/")[-1]


def main():
    a_path, b_path = sys.argv[1], sys.argv[2]
    y = int(sys.argv[3]) if len(sys.argv) > 3 else 700
    x = int(sys.argv[4]) if len(sys.argv) > 4 else 1500
    for path in (a_path, b_path):
        img = Image.open(path).convert("RGB")
        bg = img.getpixel((3, 3))
        card = img.getpixel((img.size[0] // 2, y))
        row = bbox_along_row(img, y, bg)
        col = bbox_along_col(img, x, bg)
        print(f"{name_of(path):28s} 尺寸={img.size} 背景={bg} 行y={y} 内容x范围={row} 列x={x} 内容y范围={col}")
        # 采样几行像素值，便于确认颜色是否一致
        samples = {f"({px},{py})": img.getpixel((px, py)) for px, py in [(20, 20), (200, 700), (700, 700), (1280, 300), (1280, 880)]}
        print(f"    采样: {samples}")


main()
