"""
╔══════════════════════════════════════════════════════════════════════╗
║  番茄钟 Pomodoro Timer · Miuix × 二次元 风格 v3.1                    ║
║  ──────────────────────────────────────────────────────────────────  ║
║  设计语言: HyperOS / Miuix Squircle + 二次元萌系装饰                  ║
║  特性:                                                               ║
║    · 环形进度 + 双环装饰                                               ║
║    · 高密度粒子飘浮 (星星/花瓣/音符)                                   ║
║    · 呼吸动画 + 涟漪效果                                               ║
║    · 隐藏式色彩面板 + 文字颜色切换                                      ║
║    · 镂空风格按钮                                                      ║
║    · 自定义音效 + 背景图                                               ║
║    · JSON 持久化设置                                                   ║
║  技术栈: Python 3.x + Tkinter + PIL/Pillow (可选)                     ║
╚══════════════════════════════════════════════════════════════════════╝
"""

import tkinter as tk
from tkinter import filedialog, messagebox, colorchooser
import math
import os
import random
import json
from pathlib import Path

# ══════════════════════════════════════════════════════════════
#  可选依赖: Pillow (图片背景 + 模糊效果)
#  无 Pillow 时仅禁用图片背景功能, 不影响核心计时。
# ══════════════════════════════════════════════════════════════
try:
    from PIL import Image, ImageTk, ImageEnhance, ImageFilter
    PIL_OK = True
except ImportError:
    PIL_OK = False


# ══════════════════════════════════════════════════════════════
#  配色系统 (Miuix / HyperOS 灵感)
#
#  层级: bg → surface → surface_hi → text → accent
#  所有颜色值为 6位十六进制 (#rrggbb)
# ══════════════════════════════════════════════════════════════

class Colors:
    """
    配色方案容器

    层级说明:
      bg         最底层背景 (渐变/图片绘制在此之上)
      surface    卡片/面板背景
      surface_hi 悬停/高亮时的表面色
      text       主文字 (最高对比度)
      text_sec   次要文字
      text_dim   弱化文字 (装饰/提示)
      accent     强调色 (按钮/进度条)
      accent_dim 强调色弱化版 (标签/背景填充)
      ring_bg    进度环背景色
      shadow     阴影色
      border     边框色
    """

    # 浅色主题: 暖灰背景, 避免纯白刺眼
    LIGHT = {
        "bg": "#F0F0F5", "surface": "#FFFFFF", "surface_hi": "#F5F5FA",
        "text": "#1A1A2E", "text_sec": "#6B7280", "text_dim": "#A0AEC0",
        "accent": "#FF6B6B", "accent_dim": "#FFE8E8",
        "ring_bg": "#E2E8F0", "shadow": "#CBD5E1", "border": "#E2E8F0",
        "success": "#34C759", "warning": "#FF9F0A",
    }

    # 深色主题: 纯黑背景, OLED 理念
    DARK = {
        "bg": "#0A0A0A", "surface": "#1A1A1E", "surface_hi": "#2A2A2E",
        "text": "#F0F0F5", "text_sec": "#9CA3AF", "text_dim": "#4B5563",
        "accent": "#FF6B6B", "accent_dim": "#2D1A1A",
        "ring_bg": "#2A2A2E", "shadow": "#000000", "border": "#333338",
        "success": "#30D158", "warning": "#FFD60A",
    }


# ══════════════════════════════════════════════════════════════
#  渐变主题 & 文字颜色预设
# ══════════════════════════════════════════════════════════════

class Theme:
    """渐变主题: 3 个 RGB 锚点色 + emoji 图标"""
    def __init__(self, name, colors, emoji=""):
        self.name = name
        self.colors = colors   # [(r,g,b), (r,g,b), (r,g,b)]
        self.emoji = emoji

# 8 种精选渐变, 从暖色到冷色覆盖常见审美
THEMES = [
    Theme("落日",   [(255,94,58),  (255,154,0),  (255,206,68)],  "🌅"),
    Theme("海洋",   [(56,130,246), (0,180,216),  (72,202,228)],  "🌊"),
    Theme("森林",   [(34,139,34),  (72,187,120), (134,227,156)], "🌿"),
    Theme("薰衣草", [(138,92,180), (168,130,210),(200,170,230)], "💜"),
    Theme("午夜",   [(30,30,60),   (50,45,100),  (80,70,150)],   "🌙"),
    Theme("樱花",   [(255,182,193),(255,143,163),(230,100,140)],  "🌸"),
    Theme("极光",   [(16,52,67),   (53,167,130), (130,255,200)],  "✨"),
    Theme("琥珀",   [(180,83,9),   (234,140,30), (253,186,116)],  "🔥"),
]


class TextColorPreset:
    """文字颜色预设: 一组三级文字色"""
    def __init__(self, name, text, text_sec, text_dim):
        self.name = name
        self.text = text
        self.text_sec = text_sec
        self.text_dim = text_dim

# 5 种文字配色, 适配不同背景
TEXT_PRESETS = [
    TextColorPreset("默认",   "#1A1A2E", "#6B7280", "#A0AEC0"),
    TextColorPreset("暖调",   "#3D2B1F", "#8B7355", "#C4B5A0"),
    TextColorPreset("冷调",   "#1E3A5F", "#5B8DB8", "#93C5FD"),
    TextColorPreset("柔和",   "#4A4A5A", "#8E8E9E", "#BEBECE"),
    TextColorPreset("高对比", "#000000", "#4A4A4A", "#808080"),
]


# ══════════════════════════════════════════════════════════════
#  二次元颜文字 & 状态语
# ══════════════════════════════════════════════════════════════

WORK_KAOMOJI = [
    "加油哦 (ง •_•)ง", "专注中... ♪(´▽`)", "冲鸭！ᕤ(ò_óˇ)ᕤ",
    "认真ing (•̀ᴗ•́)و", "效率满满 ✨", "全力以赴！(ﾉ>ω<)ﾉ",
    "专心专心 ( •̀ ω •́ )✧", "冲冲冲！٩(◕‿◕｡)۶",
]

BREAK_KAOMOJI = [
    "休息一下 (◕‿◕✿)", "喝杯水~ (◠‿◠)", "伸个懒腰 ☆⌒(*^-゜)v",
    "放松放松 ～(´▽`～)", "深呼吸... (｡･ω･｡)", "小憩片刻 (￣▽￣)~*",
]

IDLE_KAOMOJI = "等待开始... (´・ω・`)"


# ══════════════════════════════════════════════════════════════
#  工具函数: 颜色 & 数学 (纯函数, 无 GUI 状态依赖)
# ══════════════════════════════════════════════════════════════

def lerp(a, b, t):
    """线性插值: 在 a~b 之间按比例 t(0~1) 取值"""
    return a + (b - a) * t

def lerp_color(c1, c2, t):
    """RGB 颜色线性插值, c1/c2 为 (r,g,b) 元组"""
    return tuple(int(lerp(c1[i], c2[i], t)) for i in range(3))

def rgb_hex(r, g, b):
    """RGB → 十六进制颜色字符串, 自动 clamp 到 0-255"""
    return f"#{max(0,min(255,r)):02x}{max(0,min(255,g)):02x}{max(0,min(255,b)):02x}"

def hex_rgb(h):
    """十六进制颜色 → RGB 元组"""
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def darken(hex_c, f=0.85):
    """颜色变暗, f: 0.0=纯黑, 1.0=不变"""
    return rgb_hex(*(int(v * f) for v in hex_rgb(hex_c)))

def lighten(hex_c, f=0.15):
    """颜色变亮, f: 0.0=原色, 1.0=纯白"""
    r, g, b = hex_rgb(hex_c)
    return rgb_hex(int(r+(255-r)*f), int(g+(255-g)*f), int(b+(255-b)*f))


# ══════════════════════════════════════════════════════════════
#  Canvas 绘图工具
# ══════════════════════════════════════════════════════════════

def squircle(cv, x1, y1, x2, y2, r, **kw):
    """
    绘制 Squircle 圆角矩形 (Miuix 风格)

    用 12 个控制点构成多边形, smooth=True 使其平滑,
    效果介于圆角矩形和超椭圆之间。
    """
    pts = [
        x1+r,y1, x2-r,y1, x2,y1, x2,y1+r,
        x2,y2-r, x2,y2, x2-r,y2, x1+r,y2,
        x1,y2, x1,y2-r, x1,y1+r, x1,y1,
    ]
    return cv.create_polygon(pts, smooth=True, **kw)


def arc_ring(cv, cx, cy, r, thick, start_deg, end_deg, fill, tags=""):
    """
    绘制环形弧段 (进度条)

    将弧度拆成 N 个小四边形, 每段 ~3°, 保证平滑。
    角度约定: 0°=3点钟方向, -90°=12点钟方向。
    """
    if end_deg <= start_deg:
        return
    segs = max(int((end_deg - start_deg) / 3), 1)
    ri, ro = r - thick, r
    for i in range(segs):
        a1 = math.radians(start_deg + (end_deg-start_deg)*i/segs)
        a2 = math.radians(start_deg + (end_deg-start_deg)*(i+1)/segs)
        pts = [
            cx+ro*math.cos(a1), cy+ro*math.sin(a1),
            cx+ro*math.cos(a2), cy+ro*math.sin(a2),
            cx+ri*math.cos(a2), cy+ri*math.sin(a2),
            cx+ri*math.cos(a1), cy+ri*math.sin(a1),
        ]
        cv.create_polygon(pts, fill=fill, outline="", tags=tags)


def _init_canvas_widget(widget, parent, colors, w, h, **extra_kw):
    """
    初始化 Canvas 按钮的通用样式 (消除白边)

    所有自定义按钮 (IconButton/HollowButton/PillButton/TextLink) 的 Canvas
    都需要相同的透明背景设置, 此函数统一处理, 避免重复代码。

    参数:
      widget:  待初始化的 Canvas 实例
      parent:  父容器
      colors:  当前配色方案
      w, h:    尺寸
      extra_kw: 其他 tk.Canvas 参数
    """
    parent_bg = parent.cget("bg") if hasattr(parent, "cget") else colors.get("bg", "#FFFFFF")
    widget.configure(
        bg=parent_bg, bd=0, borderwidth=0,
        highlightthickness=0,
        highlightbackground=parent_bg,
        highlightcolor=parent_bg,
        relief=tk.FLAT,
        width=w, height=h,
        cursor="hand2",
        **extra_kw,
    )

def _sync_canvas_bg(widget, colors):
    """
    同步 Canvas 背景色到父容器 (主题切换时调用)

    修复 Bug: 主题切换后 Canvas 背景可能与新父容器色不一致,
    导致按钮周围出现白边。
    """
    parent_bg = widget.master.cget("bg") if widget.master else colors["bg"]
    widget.configure(
        bg=parent_bg,
        highlightbackground=parent_bg,
        highlightcolor=parent_bg,
    )


# ══════════════════════════════════════════════════════════════
#  组件: FloatingParticles (飘浮粒子系统)
#
#  二次元风格装饰: 星星、花瓣、音符缓慢飘浮。
#  每帧更新位置, 超出边界则重置到底部。
# ══════════════════════════════════════════════════════════════

class FloatingParticles:
    """飘浮粒子效果, 用 Canvas.create_text 绘制字符粒子"""

    CHARS = ["✦", "✧", "⋆", "˚", "·", "°", "♡", "♪", "☆", "✿"]

    def __init__(self, canvas, width, height, count=30):
        self.cv = canvas
        self.w, self.h = width, height
        self.tag = "particle"
        # 每个粒子: [x, y, 字体大小, vx, vy, 字符]
        self.particles = [self._new_particle() for _ in range(count)]

    def _new_particle(self):
        """生成随机粒子: 位置/速度/大小/字符均随机"""
        return [
            random.uniform(0, self.w),       # x
            random.uniform(0, self.h),       # y
            random.uniform(5, 12),           # 字体大小
            random.uniform(-0.3, 0.3),       # 水平速度
            random.uniform(-0.6, -0.15),     # 垂直速度 (负=向上)
            random.choice(self.CHARS),       # 字符
        ]

    def update(self, colors):
        """每帧调用: 移动粒子 → 边界重置 → 清除重绘"""
        self.cv.delete(self.tag)
        fg = colors["text_dim"]

        for p in self.particles:
            p[0] += p[3]  # x += vx
            p[1] += p[4]  # y += vy

            # 超出顶部或左右 → 重置到底部
            if p[1] < -20:
                p[1] = self.h + random.uniform(0, 50)
                p[0] = random.uniform(0, self.w)
            elif p[0] < -20 or p[0] > self.w + 20:
                p[0] = random.uniform(0, self.w)

            self.cv.create_text(
                p[0], p[1], text=p[5], fill=fg,
                font=("Segoe UI", int(p[2])), tags=self.tag,
            )

        # 粒子在背景之上, 其他元素之下
        self.cv.tag_lower(self.tag)
        self.cv.tag_lower("bg")


# ══════════════════════════════════════════════════════════════
#  按钮组件基类逻辑
#
#  IconButton / HollowButton / PillButton / TextLink 共享相同的
#  交互绑定 (Enter/Leave/Press/Release) 和 Canvas 初始化逻辑,
#  通过模块级函数 _init_canvas_widget / _sync_canvas_bg 消除重复。
# ══════════════════════════════════════════════════════════════

def _bind_button_events(widget, has_press=True):
    """
    为 Canvas 按钮绑定标准鼠标交互事件

    参数:
      widget: 按钮实例, 需有 _draw/_set/_hov/_prs/_release 属性
      has_press: 是否支持按压状态 (TextLink 不需要)
    """
    widget.bind("<Configure>", lambda e: widget._draw())
    if has_press:
        widget.bind("<Enter>",           lambda e: widget._set(True, False))
        widget.bind("<Leave>",           lambda e: widget._set(False, False))
        widget.bind("<Button-1>",        lambda e: widget._set(True, True))
        widget.bind("<ButtonRelease-1>", widget._release)
    else:
        # TextLink: 无按压状态, 释放时触发命令 (按住拖出后松开不触发)
        widget.bind("<Enter>",           lambda e: widget._set(True))
        widget.bind("<Leave>",           lambda e: widget._set(False))
        widget.bind("<Button-1>",        lambda e: None)
        widget.bind("<ButtonRelease-1>", widget._release_link)


class IconButton(tk.Canvas):
    """
    圆形图标按钮 (Miuix 风格)

    状态: normal=无背景 → hover=浅灰背景 → pressed=深灰背景
    用法: IconButton(parent, icon="🌙", command=cb, colors=c, size=36)
    """

    def __init__(self, parent, icon="", command=None, colors=None, size=40, **kw):
        self.icon = icon
        self.cmd = command
        self.c = colors or Colors.LIGHT
        self.sz = size
        self._hov = self._prs = False
        super().__init__(parent, **kw)
        _init_canvas_widget(self, parent, self.c, size, size)
        _bind_button_events(self)

    def _set(self, hov, prs):
        self._hov, self._prs = hov, prs
        self._draw()

    def _release(self, e):
        """释放时: 仅在仍在按钮内时触发命令 (支持拖出取消)"""
        self._prs = False
        self._draw()
        if self._hov and self.cmd:
            self.cmd()

    def _draw(self):
        self.delete("all")
        s = self.sz
        if self._hov or self._prs:
            bg = self.c["border"] if self._prs else self.c["surface_hi"]
            squircle(self, 4, 4, s-4, s-4, s//3, fill=bg, outline="")
        self.create_text(
            s//2, s//2, text=self.icon,
            fill=self.c["text"], font=("Segoe UI Emoji", 14), anchor="center",
        )

    def set_icon(self, icon):
        self.icon = icon
        self._draw()

    def recolor(self, colors):
        self.c = colors
        _sync_canvas_bg(self, colors)
        self._draw()


class HollowButton(tk.Canvas):
    """
    镂空风格按钮 (Outline Button)

    默认: 透明填充 + accent 描边
    悬停: accent 填充 + 白色文字
    按压: 深色 accent 填充
    """

    def __init__(self, parent, text="", command=None, colors=None,
                 w=100, h=44, **kw):
        self.text = text
        self.cmd = command
        self.c = colors or Colors.LIGHT
        self.bw, self.bh = w, h
        self._icon = ""
        self._hov = self._prs = False
        super().__init__(parent, **kw)
        _init_canvas_widget(self, parent, self.c, w, h)
        _bind_button_events(self)

    def _set(self, hov, prs):
        self._hov, self._prs = hov, prs
        self._draw()

    def _release(self, e):
        """释放时: 仅在仍在按钮内时触发命令 (支持拖出取消)"""
        self._prs = False
        self._draw()
        if self._hov and self.cmd:
            self.cmd()

    def _draw(self):
        self.delete("all")
        w, h = self.bw, self.bh
        r = h // 2
        if self._hov or self._prs:
            bg = darken(self.c["accent"], 0.85) if self._prs else self.c["accent"]
            squircle(self, 2, 2, w-2, h-2, r, fill=bg, outline="")
            fg = "#FFFFFF"
        else:
            squircle(self, 2, 2, w-2, h-2, r,
                     fill="", outline=self.c["accent"], width=2)
            fg = self.c["accent"]
        label = f"{self._icon}  {self.text}" if self._icon else self.text
        self.create_text(
            w//2, h//2-1, text=label, fill=fg,
            font=("Microsoft YaHei UI", 12, "bold"), anchor="center",
        )

    def set_label(self, text, icon=None):
        self.text = text
        if icon is not None:
            self._icon = icon
        self._draw()

    def recolor(self, colors):
        self.c = colors
        _sync_canvas_bg(self, colors)
        self._draw()


class PillButton(tk.Canvas):
    """
    药丸形按钮 (Primary Button)

    完全圆角 (r = height/2), 带微弱阴影和按压缩缩放感。
    filled=True 为强调色实心, filled=False 为表面色次要按钮。
    """

    def __init__(self, parent, text="", command=None, colors=None,
                 w=160, h=52, filled=True, **kw):
        self.text = text
        self.cmd = command
        self.c = colors or Colors.LIGHT
        self.bw, self.bh = w, h
        self.filled = filled
        self._icon = ""
        self._hov = self._prs = False
        super().__init__(parent, **kw)
        _init_canvas_widget(self, parent, self.c, w, h)
        _bind_button_events(self)

    def _set(self, hov, prs):
        self._hov, self._prs = hov, prs
        self._draw()

    def _release(self, e):
        """释放时: 仅在仍在按钮内时触发命令 (支持拖出取消)"""
        self._prs = False
        self._draw()
        if self._hov and self.cmd:
            self.cmd()

    def _draw(self):
        self.delete("all")
        w, h = self.bw, self.bh
        r = h // 2  # 完全圆角 = 药丸形状

        if self.filled:
            bg = self.c["accent"]
            fg = "#FFFFFF"
            if self._prs:   bg = darken(bg, 0.80)
            elif self._hov: bg = darken(bg, 0.90)
        else:
            bg = self.c["surface_hi"]
            fg = self.c["text"]
            if self._hov or self._prs:
                bg = self.c["border"]

        # 微阴影 (向下偏移 2px)
        squircle(self, 3, 5, w-1, h+2, r, fill=self.c["shadow"], outline="")
        # 主体
        squircle(self, 2, 2, w-2, h-2, r, fill=bg, outline="")
        # 文字
        label = f"{self._icon}  {self.text}" if self._icon else self.text
        self.create_text(
            w//2, h//2-1, text=label, fill=fg,
            font=("Microsoft YaHei UI", 13, "bold"), anchor="center",
        )

    def set_label(self, text, icon=None):
        self.text = text
        if icon is not None:
            self._icon = icon
        self._draw()

    def recolor(self, colors):
        self.c = colors
        _sync_canvas_bg(self, colors)
        self._draw()


class TextLink(tk.Canvas):
    """轻量文字链接: 纯文字, 悬停时显示 accent_dim 背景"""

    def __init__(self, parent, text="", command=None, colors=None, w=80, h=40, **kw):
        self.text = text
        self.cmd = command
        self.c = colors or Colors.LIGHT
        self.bw, self.bh = w, h
        self._icon = ""
        self._hov = False
        super().__init__(parent, **kw)
        _init_canvas_widget(self, parent, self.c, w, h)
        _bind_button_events(self, has_press=False)

    def _set(self, hov):
        """更新悬停状态并重绘"""
        self._hov = hov
        self._draw()

    def _release_link(self, e):
        """释放时: 仅在仍在悬停区域内时触发命令 (按住拖出后松开不触发)"""
        self._draw()
        if self._hov and self.cmd:
            self.cmd()

    def _draw(self):
        self.delete("all")
        w, h = self.bw, self.bh
        if self._hov:
            squircle(self, 2, 2, w-2, h-2, h//2,
                     fill=self.c["accent_dim"], outline="")
        label = f"{self._icon}  {self.text}" if self._icon else self.text
        self.create_text(
            w//2, h//2-1, text=label, fill=self.c["accent"],
            font=("Microsoft YaHei UI", 12), anchor="center",
        )

    def set_label(self, text, icon=None):
        self.text = text
        if icon is not None:
            self._icon = icon
        self._draw()

    def recolor(self, colors):
        self.c = colors
        _sync_canvas_bg(self, colors)
        self._draw()


# ══════════════════════════════════════════════════════════════
#  组件: ColorPanel (隐藏式色彩面板)
#
#  两种模式:
#    "theme" → 切换渐变背景主题
#    "text"  → 切换文字颜色预设
#  点击展开, 选择后自动收起。
# ══════════════════════════════════════════════════════════════

class ColorPanel:
    """隐藏式色彩选择面板, 绘制在 Canvas 上"""

    def __init__(self, cv, colors, mode="theme",
                 on_pick=None, on_custom=None):
        self.cv = cv
        self.c = colors
        self.mode = mode        # "theme" 或 "text"
        self.on_pick = on_pick  # 选择回调 (index)
        self.on_custom = on_custom  # 自定义颜色回调 (仅 theme 模式)
        self.open = False
        # Tag 格式: "panel_theme" 或 "panel_text"
        # BUG FIX: 旧版 tag 为 "colorpanel_theme"/"colorpanel_text",
        #          与 _draw_ring 中的 tag_raise 不匹配, 已统一修正。
        self.tag = f"panel_{mode}"

    def toggle(self):
        """切换展开/收起状态"""
        self.open = not self.open
        self._show() if self.open else self._hide()

    def _show(self):
        """绘制面板内容 (色块按钮 + 可选自定义按钮)"""
        self._hide()
        y0 = 58

        # 根据模式准备色块数据: (颜色值, 名称)
        if self.mode == "theme":
            items = [(rgb_hex(*t.colors[0]), t.name) for t in THEMES]
        else:
            items = [(p.text, p.name) for p in TEXT_PRESETS]

        # 面板宽度: 每项 40px + 左右 padding + 可选自定义按钮
        pw = len(items) * 40 + 24
        if self.on_custom:
            pw += 40

        # 背景卡片
        squircle(self.cv, 12, y0, 12+pw, y0+44, 12,
                 fill=self.c["surface"], outline=self.c["border"], tags=self.tag)

        # 绘制色块按钮并绑定点击事件
        for i, (color, name) in enumerate(items):
            x, y, sz = 24 + i * 40, y0 + 10, 24
            if self.mode == "text":
                # 文字模式: 镂空色块 + "A" 字符
                bid = squircle(self.cv, x, y, x+sz, y+sz, 8,
                               fill="", outline=color, width=2, tags=self.tag)
                self.cv.create_text(x+sz//2, y+sz//2, text="A",
                                    fill=color, font=("Arial", 10, "bold"),
                                    tags=self.tag)
            else:
                bid = squircle(self.cv, x, y, x+sz, y+sz, 8,
                               fill=color, outline="", tags=self.tag)

            self.cv.tag_bind(bid, "<Button-1>",
                             lambda e, idx=i: self._pick(idx))
            self.cv.tag_bind(bid, "<Enter>",
                             lambda e: self.cv.config(cursor="hand2"))
            self.cv.tag_bind(bid, "<Leave>",
                             lambda e: self.cv.config(cursor=""))

        # 自定义颜色按钮 (仅主题模式)
        if self.on_custom:
            cx = 24 + len(items) * 40
            bid = squircle(self.cv, cx, y0+10, cx+24, y0+34, 8,
                           fill=self.c["border"], outline="", tags=self.tag)
            self.cv.create_text(cx+12, y0+22, text="+",
                                fill=self.c["text_sec"],
                                font=("Microsoft YaHei UI", 12, "bold"),
                                tags=self.tag)
            self.cv.tag_bind(bid, "<Button-1>", lambda e: self._custom())
            self.cv.tag_bind(bid, "<Enter>",
                             lambda e: self.cv.config(cursor="hand2"))
            self.cv.tag_bind(bid, "<Leave>",
                             lambda e: self.cv.config(cursor=""))

        # 确保面板在最顶层 (粒子之上, 文字之下)
        self.cv.tag_raise(self.tag)
        self.cv.tag_raise("text")

    def _hide(self):
        """删除面板所有元素"""
        self.cv.delete(self.tag)
        self.open = False

    def _pick(self, idx):
        """选择后隐藏面板并触发回调"""
        self._hide()
        if self.on_pick:
            self.on_pick(idx)

    def _custom(self):
        """打开系统颜色选择器"""
        self._hide()
        color = colorchooser.askcolor(title="选择自定义颜色")
        if color and color[1] and self.on_custom:
            self.on_custom(color[1])

    def recolor(self, colors):
        """主题切换时更新颜色"""
        self.c = colors
        if self.open:
            self._show()


# ══════════════════════════════════════════════════════════════
#  设置对话框
# ══════════════════════════════════════════════════════════════

class SettingsDialog:
    """模态设置面板: 时间设置 + 提示音效选择"""

    def __init__(self, parent, settings, sound_path, on_save, colors):
        self.cfg = settings.copy()
        self.sound_path = sound_path
        self.on_save = on_save
        self.c = colors

        # 创建模态对话框
        self.dlg = tk.Toplevel(parent)
        self.dlg.title("设置")
        self.dlg.geometry("400x460")
        self.dlg.resizable(False, False)
        self.dlg.transient(parent)
        self.dlg.grab_set()
        self.dlg.configure(bg=colors["bg"])

        # 居中于父窗口
        self.dlg.update_idletasks()
        x = parent.winfo_x() + (parent.winfo_width() - 400) // 2
        y = parent.winfo_y() + (parent.winfo_height() - 460) // 2
        self.dlg.geometry(f"400x460+{x}+{y}")

        self._build()

    def _build(self):
        c = self.c
        root = tk.Frame(self.dlg, bg=c["bg"])
        root.pack(fill=tk.BOTH, expand=True, padx=24, pady=24)

        # 标题
        tk.Label(root, text="设置", font=("Microsoft YaHei UI", 20, "bold"),
                 bg=c["bg"], fg=c["text"], anchor="w").pack(fill=tk.X, pady=(0,20))

        # 时间设置卡片
        card = tk.Frame(root, bg=c["surface"],
                        highlightbackground=c["border"], highlightthickness=1)
        card.pack(fill=tk.X, pady=(0,12))
        inner = tk.Frame(card, bg=c["surface"])
        inner.pack(fill=tk.BOTH, expand=True, padx=20, pady=16)

        items = [
            ("工作时长",   "work",                1, 90, "分钟"),
            ("短休息",     "short_break",         1, 30, "分钟"),
            ("长休息",     "long_break",          1, 60, "分钟"),
            ("长休息间隔", "long_break_interval",  1, 10, "个番茄"),
        ]

        self.vars = {}
        for i, (label, key, lo, hi, unit) in enumerate(items):
            row = tk.Frame(inner, bg=c["surface"])
            row.pack(fill=tk.X, pady=8)
            tk.Label(row, text=label, font=("Microsoft YaHei UI", 12),
                     bg=c["surface"], fg=c["text"], anchor="w").pack(side=tk.LEFT)
            tk.Label(row, text=unit, font=("Microsoft YaHei UI", 10),
                     bg=c["surface"], fg=c["text_sec"]).pack(side=tk.RIGHT, padx=(0,8))
            var = tk.IntVar(value=self.cfg[key])
            self.vars[key] = var
            tk.Spinbox(row, from_=lo, to=hi, textvariable=var, width=6,
                       font=("Microsoft YaHei UI", 12),
                       bg=c["surface"], fg=c["text"],
                       buttonbackground=c["surface_hi"],
                       relief=tk.FLAT, highlightbackground=c["border"],
                       highlightthickness=1).pack(side=tk.RIGHT)
            if i < len(items)-1:
                tk.Frame(inner, bg=c["border"], height=1).pack(fill=tk.X, pady=4)

        # 音效选择卡片
        snd = tk.Frame(root, bg=c["surface"],
                       highlightbackground=c["border"], highlightthickness=1)
        snd.pack(fill=tk.X, pady=(0,20))
        si = tk.Frame(snd, bg=c["surface"])
        si.pack(fill=tk.X, padx=20, pady=12)

        tk.Label(si, text="提示音效", font=("Microsoft YaHei UI", 12),
                 bg=c["surface"], fg=c["text"], anchor="w").pack(side=tk.LEFT)
        self.snd_lbl = tk.Label(
            si, text=os.path.basename(self.sound_path) if self.sound_path else "系统默认",
            font=("Microsoft YaHei UI", 10), bg=c["surface"], fg=c["text_sec"])
        self.snd_lbl.pack(side=tk.RIGHT, padx=(8,0))
        TextLink(si, text="选择", command=self._pick_snd,
                 colors=c, w=60, h=32).pack(side=tk.RIGHT)

        # 按钮区
        btns = tk.Frame(root, bg=c["bg"])
        btns.pack(fill=tk.X)
        TextLink(btns, text="取消", command=self.dlg.destroy,
                 colors=c, w=80, h=40).pack(side=tk.RIGHT, padx=(8,0))
        PillButton(btns, text="保存", command=self._save,
                   colors=c, w=100, h=40).pack(side=tk.RIGHT)

    def _pick_snd(self):
        ft = [("音频文件", "*.wav *.mp3 *.ogg"), ("所有文件", "*.*")]
        f = filedialog.askopenfilename(title="选择提示音效", filetypes=ft)
        if f:
            self.sound_path = f
            self.snd_lbl.config(text=os.path.basename(f))

    def _save(self):
        for k, v in self.vars.items():
            self.cfg[k] = v.get()
        self.on_save(self.cfg, self.sound_path)
        self.dlg.destroy()


# ══════════════════════════════════════════════════════════════
#  主应用: 番茄钟
#
#  架构:
#    单一 Canvas 承载所有视觉元素 (渐变/粒子/环形/文字)
#    按钮作为独立 Canvas 组件 place 在窗口上
#    动画通过 after() 循环驱动 (~20fps)
#    设置通过 JSON 持久化到 ~/.pomodoro_prefs.json
# ══════════════════════════════════════════════════════════════

class PomodoroTimer:
    """番茄钟主应用"""

    W, H = 440, 700  # 窗口尺寸

    def __init__(self, root):
        self.root = root
        self.root.title("番茄钟")
        self.root.geometry(f"{self.W}x{self.H}")
        self.root.resizable(False, False)

        # 状态初始化
        self.dark = False
        self.c = Colors.DARK if self.dark else Colors.LIGHT
        self.theme_idx = 0
        self.text_preset_idx = 0
        self.cfg = {"work": 25, "short_break": 5,
                    "long_break": 15, "long_break_interval": 4}
        self.time_left = 0
        self.running = False
        self.mode = "work"       # "work" / "short_break" / "long_break"
        self.count = 0           # 已完成番茄数
        self.tid = None          # 计时器 after ID
        self.bg_photo = None     # 保持图片引用, 防止 GC
        self.bg_path = None
        self.sound_path = ""
        self.pulse = 0           # 呼吸动画相位
        self.anim_id = None      # 动画 after ID
        self._auto_start_id = None  # 阶段完成后自动开始的 after ID

        self._load_prefs()

        # 构建 UI
        self._build_canvas()
        self._build_topbar()
        self._build_timer()
        self._build_controls()
        self._build_stats()
        self._build_particles()

        # 初始化主题 & 启动动画
        self.apply_theme(THEMES[0])
        self.reset()
        self._tick_anim()
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    # ── 持久化 ──────────────────────────────────

    def _prefs_path(self):
        """设置文件路径: ~/.pomodoro_prefs.json"""
        return Path.home() / ".pomodoro_prefs.json"

    def _load_prefs(self):
        """从 JSON 加载设置, 任何异常静默忽略"""
        try:
            p = self._prefs_path()
            if p.exists():
                d = json.loads(p.read_text(encoding="utf-8"))
                self.cfg.update(d.get("settings", {}))
                self.sound_path = d.get("sound", "")
                self.theme_idx = d.get("theme", 0)
                self.text_preset_idx = d.get("text_preset", 0)
                self.dark = d.get("dark", False)
                self.c = Colors.DARK if self.dark else Colors.LIGHT
                # 防止损坏的配置文件导致索引越界崩溃
                self.theme_idx = min(self.theme_idx, len(THEMES) - 1)
                self.text_preset_idx = min(self.text_preset_idx, len(TEXT_PRESETS) - 1)
        except Exception:
            pass

    def _save_prefs(self):
        """将当前设置序列化到 JSON"""
        try:
            d = {"settings": self.cfg, "sound": self.sound_path,
                 "theme": self.theme_idx, "dark": self.dark,
                 "text_preset": self.text_preset_idx}
            self._prefs_path().write_text(
                json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
        except Exception:
            pass

    # ── UI 构建 ─────────────────────────────────

    def _build_canvas(self):
        """主画布: 承载渐变背景/粒子/环形进度/文字"""
        self.cv = tk.Canvas(self.root, width=self.W, height=self.H,
                            highlightthickness=0)
        self.cv.pack(fill=tk.BOTH, expand=True)

    def _build_topbar(self):
        """
        顶部工具栏

        左侧: 🎨 主题色 | 🖌 文字色
        右侧: 🌙 深色 | ⚙ 设置 | 🖼 背景
        """
        c = self.c
        # 左侧: 隐藏式面板开关
        self.theme_btn = IconButton(self.root, icon="🎨",
                                    command=self._toggle_theme_panel,
                                    colors=c, size=36)
        self.theme_btn.place(x=16, y=12)

        self.text_color_btn = IconButton(self.root, icon="🖌",
                                         command=self._toggle_text_panel,
                                         colors=c, size=36)
        self.text_color_btn.place(x=56, y=12)

        # 面板实例 (初始隐藏)
        self.theme_panel = ColorPanel(
            self.cv, c, mode="theme",
            on_pick=self._switch_theme, on_custom=self._custom_color)
        self.text_panel = ColorPanel(
            self.cv, c, mode="text",
            on_pick=self._switch_text_preset)

        # 右侧按钮
        self.bg_btn = IconButton(self.root, icon="🖼",
                                 command=self._pick_bg, colors=c, size=36)
        self.bg_btn.place(x=self.W-52, y=12)

        self.set_btn = IconButton(self.root, icon="⚙",
                                  command=self._open_settings, colors=c, size=36)
        self.set_btn.place(x=self.W-96, y=12)

        self.dark_btn = IconButton(self.root, icon="🌙",
                                   command=self._toggle_dark, colors=c, size=36)
        self.dark_btn.place(x=self.W-140, y=12)

    def _build_timer(self):
        """中央计时器: 时间数字 → 模式标签 → 颜文字 → 番茄计数"""
        self.cx = self.W // 2
        self.cy = 290
        self.R = 120

        self.time_id = self.cv.create_text(
            self.cx, self.cy - 10, text="25:00",
            font=("Consolas", 56, "bold"),
            fill=self.c["text"], tags="text")

        self.mode_id = self.cv.create_text(
            self.cx, self.cy + 38, text="工作时间",
            font=("Microsoft YaHei UI", 14),
            fill=self.c["text_sec"], tags="text")

        self.kaomoji_id = self.cv.create_text(
            self.cx, self.cy + 60, text=IDLE_KAOMOJI,
            font=("Microsoft YaHei UI", 10),
            fill=self.c["text_dim"], tags="text")

        self.cycle_id = self.cv.create_text(
            self.cx, self.cy + 80, text="🍅 0 / 4",
            font=("Microsoft YaHei UI", 11),
            fill=self.c["text_dim"], tags="text")

    def _build_controls(self):
        """控制按钮: 开始(实心) | 重置(表面) | 跳过(镂空)"""
        c = self.c
        y = 490

        self.start_btn = PillButton(
            self.root, text="开始专注", command=self._toggle,
            colors=c, w=160, h=52, filled=True)
        self.start_btn.place(x=20, y=y)

        self.reset_btn = PillButton(
            self.root, text="重置", command=self.reset,
            colors=c, w=90, h=44, filled=False)
        self.reset_btn.place(x=190, y=y+4)

        self.skip_btn = HollowButton(
            self.root, text="跳过", command=self._skip,
            colors=c, w=90, h=44)
        self.skip_btn.place(x=290, y=y+4)

    def _build_stats(self):
        """底部统计: 已完成 / 当前模式 / 总时长"""
        c = self.c
        sy = 590
        self.stat_ids = {}
        for i, (label, val, key) in enumerate([
            ("已完成", "0", "done"),
            ("当前", "工作", "cur"),
            ("总时长", "0 分钟", "total"),
        ]):
            cx = 75 + i * 145
            self.stat_ids[key] = self.cv.create_text(
                cx, sy, text=val,
                font=("Microsoft YaHei UI", 24, "bold"),
                fill=c["text"], tags="text")
            self.cv.create_text(
                cx, sy + 32, text=label,
                font=("Microsoft YaHei UI", 10),
                fill=c["text_dim"], tags="text")

    def _build_particles(self):
        """初始化粒子系统 (30 个粒子)"""
        self.particles = FloatingParticles(self.cv, self.W, self.H, count=30)

    # ── 主题 & 颜色 ─────────────────────────────

    def apply_theme(self, theme):
        """应用渐变背景主题 (三色两段渐变)"""
        self.cv.delete("bg")
        self.bg_path = None

        cols = theme.colors
        strips = 120  # 渐变条数, 越多越平滑
        sh = self.H / strips

        for i in range(strips):
            t = i / strips
            # 两段线性插值: col0→col1 (前半), col1→col2 (后半)
            if t < 0.5:
                c = lerp_color(cols[0], cols[1], t * 2)
            else:
                c = lerp_color(cols[1], cols[2], (t - 0.5) * 2)
            # 深色模式: 降低亮度至 30%
            if self.dark:
                c = tuple(int(v * 0.3) for v in c)
            y1, y2 = i * sh, (i + 1) * sh
            hc = rgb_hex(*c)
            self.cv.create_rectangle(0, y1, self.W, y2,
                                     fill=hc, outline=hc, tags="bg")
        self.cv.tag_lower("bg")
        self._recolor_all()

    def _apply_image_bg(self, path):
        """应用图片背景: 自动缩放 + 深色适配 + 高斯模糊"""
        if not PIL_OK:
            messagebox.showwarning("提示", "需要 Pillow: pip install Pillow")
            return
        try:
            img = Image.open(path).resize((self.W, self.H),
                                           Image.Resampling.LANCZOS)
            if self.dark:
                img = ImageEnhance.Brightness(img).enhance(0.3)
            img = img.filter(ImageFilter.GaussianBlur(radius=3))
            self.bg_photo = ImageTk.PhotoImage(img)
            self.bg_path = path
            self.cv.delete("bg")
            self.cv.create_image(0, 0, anchor=tk.NW,
                                 image=self.bg_photo, tags="bg")
            self.cv.tag_lower("bg")
            self._recolor_all()
        except Exception as e:
            messagebox.showerror("错误", f"无法加载: {e}")

    def _switch_theme(self, idx):
        self.theme_idx = idx
        self.apply_theme(THEMES[idx])
        self._save_prefs()

    def _custom_color(self, hex_c):
        """自定义强调色 (从颜色选择器回调)"""
        self.c["accent"] = hex_c
        self.c["accent_dim"] = lighten(hex_c, 0.85)
        self._recolor_all()
        self._save_prefs()

    def _switch_text_preset(self, idx):
        """切换文字颜色预设"""
        self.text_preset_idx = idx
        p = TEXT_PRESETS[idx]
        self.c["text"] = p.text
        self.c["text_sec"] = p.text_sec
        self.c["text_dim"] = p.text_dim
        self._recolor_all()
        self._save_prefs()

    def _toggle_dark(self):
        """切换深色/浅色模式"""
        self.dark = not self.dark
        self.c = Colors.DARK if self.dark else Colors.LIGHT
        # 恢复当前文字颜色预设 (深色模式的基础色不同)
        p = TEXT_PRESETS[self.text_preset_idx]
        self.c["text"] = p.text
        self.c["text_sec"] = p.text_sec
        self.c["text_dim"] = p.text_dim
        self.dark_btn.set_icon("☀" if self.dark else "🌙")
        self.apply_theme(THEMES[self.theme_idx])
        self._save_prefs()

    def _toggle_theme_panel(self):
        """打开主题面板时关闭文字面板 (互斥)"""
        self.text_panel._hide()
        self.theme_panel.toggle()

    def _toggle_text_panel(self):
        """打开文字面板时关闭主题面板 (互斥)"""
        self.theme_panel._hide()
        self.text_panel.toggle()

    def _pick_bg(self):
        """选择自定义背景图片"""
        ft = [("图片", "*.jpg *.jpeg *.png *.bmp *.gif"), ("所有", "*.*")]
        f = filedialog.askopenfilename(title="选择背景图片", filetypes=ft)
        if f:
            self._apply_image_bg(f)

    def _recolor_all(self):
        """同步更新所有组件颜色 (主题/深色模式切换后调用)"""
        c = self.c
        self.cv.configure(bg=c["bg"])
        # 更新 Canvas 上的文字颜色
        for item_id, key in [
            (self.time_id, "text"), (self.mode_id, "text_sec"),
            (self.kaomoji_id, "text_dim"), (self.cycle_id, "text_dim"),
        ]:
            self.cv.itemconfig(item_id, fill=c[key])
        for kid in self.stat_ids.values():
            self.cv.itemconfig(kid, fill=c["text"])
        # 更新所有按钮
        for btn in [self.dark_btn, self.set_btn, self.bg_btn,
                    self.theme_btn, self.text_color_btn,
                    self.start_btn, self.reset_btn, self.skip_btn]:
            btn.recolor(c)
        # 更新面板
        self.theme_panel.recolor(c)
        self.text_panel.recolor(c)
        self.dark_btn.set_icon("☀" if self.dark else "🌙")
        self._draw_ring()

    # ── 动画 ────────────────────────────────────

    def _tick_anim(self):
        """
        动画主循环 (~20fps, 每 50ms 一帧)

        每帧: 更新呼吸相位(仅运行时) → 重绘环形进度 → 更新粒子
        """
        if self.running:
            self.pulse = (self.pulse + 0.08) % (2 * math.pi)
        # BUG FIX: 非运行时不再重置 pulse=0, 避免不必要的赋值
        # (pulse 仅在 running 时使用, 重置无实际意义)

        self._draw_ring()
        self.particles.update(self.c)
        self.anim_id = self.root.after(50, self._tick_anim)

    def _draw_ring(self):
        """
        绘制环形进度 (每帧重绘)

        层次 (从底到顶):
          外装饰虚线环 → 背景环 → 进度环 → 起点装饰圆点
        """
        self.cv.delete("ring")
        cx, cy, r, c = self.cx, self.cy, self.R, self.c

        # 外装饰虚线环 (每 8° 绘制 3° 短线, 增加精致感)
        dash_r = r + 12
        for deg in range(0, 360, 8):
            a1 = math.radians(deg - 90)
            a2 = math.radians(deg - 90 + 3)
            self.cv.create_line(
                cx + dash_r * math.cos(a1), cy + dash_r * math.sin(a1),
                cx + dash_r * math.cos(a2), cy + dash_r * math.sin(a2),
                fill=c["border"], width=1, tags="ring")

        # 背景环 (完整圆)
        thick = 8
        arc_ring(self.cv, cx, cy, r, thick, 0, 360, c["ring_bg"], tags="ring")

        # 进度环 (根据剩余时间计算角度)
        dur = self._duration()
        if dur > 0:
            prog = 1 - self.time_left / dur  # 0~1 进度
            end = 360 * prog
            if end > 0:
                if self.running:
                    # 呼吸效果: accent 色亮度周期变化
                    p = 0.85 + 0.15 * math.sin(self.pulse)
                    ri, gi, bi = hex_rgb(c["accent"])
                    col = rgb_hex(int(ri*p), int(gi*p), int(bi*p))
                else:
                    col = c["accent"]
                arc_ring(self.cv, cx, cy, r, thick,
                         -90, -90 + end, col, tags="ring")

        # 起点装饰圆点 (12点钟方向)
        dr = thick // 2 + 2
        self.cv.create_oval(
            cx-dr, cy-r-dr, cx+dr, cy-r+dr,
            fill=c["accent"], outline="", tags="ring")

        # 层级管理: 文字和面板始终在环形之上
        # BUG FIX: 旧版 tag 为 "colorpanel_theme"/"colorpanel_text",
        #          与 ColorPanel 实际创建的 tag "panel_theme"/"panel_text" 不匹配
        self.cv.tag_raise("text")
        self.cv.tag_raise("panel_theme")
        self.cv.tag_raise("panel_text")

    # ── 计时器逻辑 ──────────────────────────────

    def _duration(self):
        """当前模式的总秒数

        BUG FIX: 旧版使用无意义的恒等映射字典 {k: k}[self.mode],
                 直接用 self.cfg[self.mode] 即可。
        """
        return self.cfg[self.mode] * 60

    def reset(self):
        """重置计时器到当前模式的初始状态"""
        if self.tid:
            self.root.after_cancel(self.tid)
            self.tid = None
        if self._auto_start_id:
            self.root.after_cancel(self._auto_start_id)
            self._auto_start_id = None
        self.running = False
        self.time_left = self._duration()
        self._update_display()
        self.start_btn.set_label("开始专注", icon="▶")

    def _toggle(self):
        """切换 开始/暂停"""
        if self.running:
            self._pause()
        else:
            self._start()

    def _start(self):
        """开始计时"""
        if not self.running:
            self.running = True
            self.start_btn.set_label("暂停", icon="⏸")
            self._tick()

    def _pause(self):
        """暂停计时"""
        self.running = False
        if self.tid:
            self.root.after_cancel(self.tid)
            self.tid = None
        self.start_btn.set_label("继续", icon="▶")

    def _skip(self):
        """跳过当前阶段"""
        self._complete()

    def _tick(self):
        """每秒调用: 倒计时 → 到时则完成"""
        if not self.running:
            return
        if self.time_left <= 0:
            self._complete()
            return
        self.time_left -= 1
        self._update_display()
        self.tid = self.root.after(1000, self._tick)

    def _update_display(self):
        """刷新所有动态文字: 时间/模式/颜文字/统计"""
        m, s = divmod(self.time_left, 60)
        self.cv.itemconfig(self.time_id, text=f"{m:02d}:{s:02d}")

        modes = {"work": "工作时间", "short_break": "短休息", "long_break": "长休息"}
        self.cv.itemconfig(self.mode_id, text=modes[self.mode])

        # 运行时每秒随机切换颜文字, 停止时显示静态等待语
        if self.running:
            pool = WORK_KAOMOJI if self.mode == "work" else BREAK_KAOMOJI
            self.cv.itemconfig(self.kaomoji_id, text=random.choice(pool))
        else:
            self.cv.itemconfig(self.kaomoji_id, text=IDLE_KAOMOJI)

        self.cv.itemconfig(
            self.cycle_id,
            text=f"🍅 {self.count} / {self.cfg['long_break_interval']}")

        # 底部统计
        self.cv.itemconfig(self.stat_ids["done"], text=str(self.count))
        self.cv.itemconfig(self.stat_ids["cur"], text=modes[self.mode])
        total = self.count * self.cfg["work"]
        if total >= 60:
            h, m = divmod(total, 60)
            self.cv.itemconfig(self.stat_ids["total"], text=f"{h}h {m}m")
        else:
            self.cv.itemconfig(self.stat_ids["total"], text=f"{total} 分钟")

    def _complete(self):
        """
        阶段完成处理

        流程: 播放音效 → 切换模式 → 重置 → 1秒后自动开始下一阶段
        模式切换: work → short_break/long_break (根据间隔) → work
        """
        self.running = False
        self._play_sound()

        if self.mode == "work":
            self.count += 1
            # 每 N 个番茄后进入长休息, 否则短休息
            self.mode = ("long_break"
                         if self.count % self.cfg["long_break_interval"] == 0
                         else "short_break")
        else:
            self.mode = "work"

        self.reset()
        # 1 秒后自动开始下一阶段 (ID 保存到 _auto_start_id, 可被 reset/_on_close 取消)
        self._auto_start_id = self.root.after(1000, self._start)

    def _play_sound(self):
        """播放提示音: 优先自定义文件, 回退系统 bell"""
        if self.sound_path and os.path.exists(self.sound_path):
            try:
                if self.sound_path.lower().endswith(".wav"):
                    import winsound
                    winsound.PlaySound(self.sound_path,
                                       winsound.SND_FILENAME | winsound.SND_ASYNC)
                else:
                    os.startfile(self.sound_path)
                return
            except Exception:
                pass
        try:
            self.root.bell()
        except Exception:
            pass

    # ── 设置 & 关闭 ─────────────────────────────

    def _open_settings(self):
        """打开设置对话框"""
        SettingsDialog(self.root, self.cfg, self.sound_path,
                       on_save=self._on_saved, colors=self.c)

    def _on_saved(self, cfg, snd):
        """设置保存回调: 更新配置 → 重置计时器 → 持久化"""
        self.cfg = cfg
        self.sound_path = snd
        self.reset()
        self._save_prefs()

    def _on_close(self):
        """窗口关闭: 取消所有定时器 → 保存设置 → 销毁窗口"""
        for timer_id in (self.tid, self.anim_id, self._auto_start_id):
            if timer_id:
                self.root.after_cancel(timer_id)
        self._save_prefs()
        self.root.destroy()


# ══════════════════════════════════════════════════════════════
#  入口
# ══════════════════════════════════════════════════════════════

def self_check():
    """启动前检查环境, 输出兼容性信息"""
    issues = []
    try:
        r = tk.Tk(); r.destroy()
    except Exception as e:
        issues.append(f"Tkinter: {e}")
    if not PIL_OK:
        issues.append("Pillow not installed (image bg limited)")
    try:
        import winsound
    except ImportError:
        issues.append("winsound unavailable")
    if issues:
        print("[!] Issues:")
        for i in issues: print(f"  - {i}")
    else:
        print("[OK] All checks passed.")


def main():
    self_check()
    root = tk.Tk()
    # Windows DPI 感知: 避免高分屏模糊
    try:
        from ctypes import windll
        windll.shcore.SetProcessDpiAwareness(1)
    except Exception:
        pass
    PomodoroTimer(root)
    root.mainloop()


if __name__ == "__main__":
    main()
