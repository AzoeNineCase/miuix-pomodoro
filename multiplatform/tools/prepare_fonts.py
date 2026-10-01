"""准备 Compose Multiplatform 工程的字体资源。

产物：
  1. composeResources/font/material_symbols_rounded.ttf  —— Material Symbols Rounded 子集（与网页版同形图标）
  2. composeResources/font/inter_*.ttf                   —— Inter 文本字体（400/500/600/700/800）
  3. commonMain/kotlin/com/example/pomodoro/ui/Symbols.kt —— 图标名 → 码位 映射

数据源：
  - google/material-design-icons 的 variablefont/MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf + .codepoints
  - Google Fonts 的 Inter（用旧 UA 请求 css2 拿 TTF 直链；拿到 woff2 时用 fontTools 解压）

运行：python prepare_fonts.py   （需要 fonttools；woff2 路线额外需要 brotli）
"""
import os
import pathlib
import re
import subprocess
import sys
import urllib.request

REPO = pathlib.Path(r"e:\ke\cook\multiplatform")
OUT_FONT = REPO / "composeApp/src/commonMain/composeResources/font"
OUT_KT = REPO / "composeApp/src/commonMain/kotlin/com/example/pomodoro/ui/Symbols.kt"
TMP = pathlib.Path(os.environ["TEMP"]) / "fonts"

PROXY = "http://127.0.0.1:10808"
UA_OLD = "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)"

# 网页 index.html + 本工程用到的全部图标
ICONS = [
    # 导航 / 顶栏
    "timer", "monitoring", "checklist", "settings", "eco", "dark_mode", "light_mode",
    # 计时页
    "psychology", "coffee", "self_improvement", "play_arrow", "pause", "replay",
    "skip_next", "play_circle",
    # 设置页
    "repeat", "notifications", "notification_add", "picture_in_picture",
    "swipe_vertical", "palette", "notifications_active", "info", "chevron_right",
    "remove", "add_circle_outline", "horizontal_rule", "drag_handle", "toggle_on",
    # 统计 / 待办
    "download", "add", "delete", "edit", "done", "done_all", "checklist_rtl",
    # 背景设置
    "shuffle", "upload", "check", "image", "link", "refresh",
    # 关于页 / 通用
    "arrow_back", "arrow_forward", "open_in_new", "close", "check_circle", "error",
    "warning", "help", "code", "favorite", "star", "lightbulb", "volume_up",
    "music_note", "more_horiz", "remove_circle", "list", "schedule", "bar_chart",
    "celebration", "hourglass_bottom", "sync", "tune", "content_copy", "language",
    "visibility", "visibility_off", "drag_indicator", "fullscreen", "filter_list",
    "sort", "expand_more", "expand_less", "chevron_left", "arrow_drop_down",
]


def log(*a):
    print(*a, flush=True)


def fetch(url, out: pathlib.Path, binary=True):
    req = urllib.request.Request(url, headers={"User-Agent": UA_OLD if "fonts.googleapis" in url else "curl/8"})
    proxy = urllib.request.ProxyHandler({"http": PROXY, "https": PROXY})
    opener = urllib.request.build_opener(proxy)
    with opener.open(req, timeout=120) as r:
        data = r.read()
    out.write_bytes(data) if binary else out.write_text(data.decode("utf-8"), encoding="utf-8")
    return data


def main():
    OUT_FONT.mkdir(parents=True, exist_ok=True)
    OUT_KT.parent.mkdir(parents=True, exist_ok=True)
    TMP.mkdir(parents=True, exist_ok=True)

    # ---------- 1) Material Symbols ----------
    var_ttf = TMP / "MaterialSymbolsRounded.ttf"
    cps_file = TMP / "codepoints"
    if not var_ttf.exists():
        log("下载 Material Symbols 可变字体…")
        fetch("https://raw.githubusercontent.com/google/material-design-icons/master/"
              "variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf", var_ttf)
    if not cps_file.exists():
        log("下载 codepoints…")
        fetch("https://raw.githubusercontent.com/google/material-design-icons/master/"
              "variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.codepoints", cps_file)

    cps = {}
    for line in cps_file.read_text(encoding="utf-8").splitlines():
        p = line.split()
        if len(p) == 2 and re.fullmatch(r"[0-9a-fA-F]{4,5}", p[1]):
            cps[p[0]] = int(p[1], 16)
    log(f"codepoints 条目数: {len(cps)}")

    missing = [n for n in ICONS if n not in cps]
    have = [n for n in ICONS if n in cps]
    log(f"清单 {len(ICONS)} 个，命中 {len(have)} 个，缺失: {missing}")

    static_ttf = TMP / "MaterialSymbolsRounded.static.ttf"
    if not static_ttf.exists():
        log("实例化静态字重 (wght=400, FILL=0, GRAD=0, opsz=24)…")
        subprocess.run([sys.executable, "-m", "fontTools.varLib.instancer", str(var_ttf),
                        "wght=400", "FILL=0", "GRAD=0", "opsz=24", "-o", str(static_ttf)], check=True)

    unicodes = ",".join(f"U+{cps[n]:04X}" for n in have)
    icon_out = OUT_FONT / "material_symbols_rounded.ttf"
    log("子集化图标字体…")
    subprocess.run([sys.executable, "-m", "fontTools.subset", str(static_ttf),
                    f"--unicodes={unicodes}", f"--output-file={icon_out}"], check=True)

    from fontTools.ttLib import TTFont
    f = TTFont(icon_out)
    cmap = f.getBestCmap()
    log(f"图标字体: {icon_out.stat().st_size/1024:.1f} KB, 字形 {len(f.getGlyphOrder())} 个")
    sample = have[:5] + have[-5:]
    for n in sample:
        log(f"  抽检 {n} -> U+{cps[n]:04X} 在 cmap 中: {cps[n] in cmap} (glyph={cmap.get(cps[n])})")

    # ---------- 2) Symbols.kt ----------
    lines = [
        "package com.example.pomodoro.ui",
        "",
        "/**",
        " * Material Symbols Rounded 图标码位表。",
        " *",
        " * 与网页 index.html 使用的同名图标完全一致（同一字体、同一 FILL/wght/GRAD/opsz 设置），",
        " * 字体已子集化到 composeResources/font/material_symbols_rounded.ttf。",
        " *",
        " * 用法：Text(SYMBOLS[\"timer\"].orEmpty(), fontFamily = AppFonts.symbols, fontSize = 24.sp)",
        " */",
        "val SYMBOLS: Map<String, String> = mapOf(",
    ]
    for n in sorted(have):
        lines.append(f'    "{n}" to "\\u{cps[n]:04X}",')
    lines.append(")")
    lines.append("")
    OUT_KT.write_text("\n".join(lines), encoding="utf-8")
    log(f"已写 {OUT_KT} ({len(have)} 个图标)")

    # ---------- 3) Inter ----------
    # 直接复用 android/ WebView 版内置的那 5 个 woff2（与网页/安卓版完全同一份文件），
    # 解压成 TTF 供 Compose 使用 —— 保证字形、字重、子集范围与参考实现一致。
    weights = {0: "regular", 1: "medium", 2: "semibold", 3: "bold", 4: "extrabold"}
    src_dir = REPO.parent / "android/app/src/main/assets/fonts"
    from fontTools.ttLib import woff2
    for idx, name in weights.items():
        src = src_dir / f"inter-latin-{idx}.woff2"
        if not src.exists():
            log(f"  [缺失] {src}")
            continue
        dst = OUT_FONT / f"inter_{name}.ttf"
        woff2.decompress(str(src), str(dst))
        log(f"  inter_{name}: {dst.name} {dst.stat().st_size/1024:.1f} KB (源 {src.name})")

    log("完成。font 目录：")
    for p in sorted(OUT_FONT.iterdir()):
        log(f"  {p.name:38s} {p.stat().st_size/1024:8.1f} KB")


if __name__ == "__main__":
    main()
