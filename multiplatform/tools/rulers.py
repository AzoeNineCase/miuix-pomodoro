"""元素边界标尺：沿指定列/行扫描亮度跃变（边框、色块、文字行），
并自动配对原生/网页两张图的边界位置，报告每处偏移。

用法:
    python rulers.py <A.png> <B.png> col <x> [thr=8]
    python rulers.py <A.png> <B.png> row <y> [thr=8]
    python rulers.py <img.png> col <x> [thr=8]          # 单图模式
"""

import sys

import numpy as np
from PIL import Image


def load(path):
    return np.asarray(Image.open(path).convert("L"), dtype=np.int16)


def edges_1d(profile, thr):
    """返回 [(pos, delta)]，pos 为跃变位置，delta 为带符号的亮度差（已合并邻近）。"""
    d = np.diff(profile)
    out = []
    i = 0
    n = len(d)
    while i < n:
        if abs(d[i]) >= thr:
            j = i
            while j + 1 < n and abs(d[j + 1]) >= thr and d[j + 1] * d[i] > 0:
                j += 1
            # 取该段内绝对值最大的位置
            k = i + int(np.argmax(np.abs(d[i:j + 1])))
            out.append((k + 1, int(d[k])))
            i = j + 1
        else:
            i += 1
    return out


def match(a, b):
    """为 A 的每个边界找最近的 B 边界，报告偏移。"""
    rows = []
    used = set()
    for pos, delta in a:
        best = None
        for bi, (bpos, bdelta) in enumerate(b):
            if bi in used:
                continue
            dist = abs(bpos - pos)
            if best is None or dist < best[0]:
                best = (dist, bi, bpos, bdelta)
        if best is None:
            rows.append((pos, delta, None, None))
            continue
        dist, bi, bpos, bdelta = best
        used.add(bi)
        if dist > 14:
            rows.append((pos, delta, None, bpos))
        else:
            rows.append((pos, delta, bpos - pos, bdelta))
    for bi, (bpos, bdelta) in enumerate(b):
        if bi not in used:
            rows.append((None, None, None, bpos))
    return rows


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 1
    single = len(sys.argv) == 4
    a_path = sys.argv[1]
    if single:
        img = load(a_path)
        axis, coord = sys.argv[2], int(sys.argv[3])
        thr = 8
        prof = img[:, coord] if axis == "col" else img[coord, :]
        for pos, delta in edges_1d(prof, thr):
            print(f"{axis}={pos:5d}  Δ={delta:+4d}")
        return 0

    b_path = sys.argv[2]
    axis, coord = sys.argv[3], int(sys.argv[4])
    thr = int(sys.argv[5]) if len(sys.argv) > 5 else 8
    A, B = load(a_path), load(b_path)
    if axis == "col":
        pa, pb = A[:, coord], B[:, coord]
    else:
        pa, pb = A[coord, :], B[coord, :]
    ea, eb = edges_1d(pa, thr), edges_1d(pb, thr)
    name_a = a_path.replace("\\", "/").split("/")[-1]
    name_b = b_path.replace("\\", "/").split("/")[-1]
    print(f"== {axis}={coord} thr={thr}")
    print(f"   {name_a}: {len(ea)} edges   {name_b}: {len(eb)} edges")
    for pos, delta, off, bdelta in match(ea, eb):
        if pos is None:
            print(f"   (只有 B) B={bdelta:5d}")
        elif off is None:
            print(f"   A={pos:5d} Δ={delta:+4d}  (B 无对应, 最近 {bdelta})")
        elif off == 0:
            print(f"   A={pos:5d} B={pos + off:5d}  =0  Δ={delta:+4d}")
        else:
            print(f"   A={pos:5d} B={pos + off:5d}  {off:+d}  Δ={delta:+4d}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
