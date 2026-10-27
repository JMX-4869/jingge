# -*- coding: utf-8 -*-
"""鲸歌 —— 图标生成：黑胶 + 唱臂，另有通知栏/动作图标。无外部素材。"""
import os
from PIL import Image, ImageDraw

BG = (10, 12, 16, 255)
PANEL = (14, 18, 24, 255)
INK = (88, 224, 200, 255)
W = (255, 255, 255, 255)
S = 192
SS = 4


def px(v):
    return v * SS


def launcher(size):
    im = Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, im.width - 1, im.height - 1],
                        radius=int(44 * SS), fill=BG)

    cx, cy, r = 96, 96, 52
    d.ellipse([px(cx - r), px(cy - r), px(cx + r), px(cy + r)], fill=PANEL)
    for ratio, a in ((0.82, 10), (0.64, 8)):
        rr = r * ratio
        d.ellipse([px(cx - rr), px(cy - rr), px(cx + rr), px(cy + rr)],
                  outline=(*INK[:3], a), width=int(px(2)))
    d.ellipse([px(cx - r), px(cy - r), px(cx + r), px(cy + r)],
              outline=(*INK[:3], 255), width=int(px(3.5)))
    tr = 14
    d.ellipse([px(cx - tr), px(cy - tr), px(cx + tr), px(cy + tr)], fill=INK)
    hr = 3.6
    d.ellipse([px(cx - hr), px(cy - hr), px(cx + hr), px(cy + hr)], fill=PANEL)

    # 唱臂
    d.line([px(160), px(30), px(132), px(64)], fill=INK, width=int(px(5)))
    d.line([px(132), px(64), px(118), px(81)], fill=INK, width=int(px(3.4)))
    d.line([px(113), px(76), px(119), px(84)], fill=INK, width=int(px(6)))
    d.ellipse([px(153), px(23), px(167), px(37)], fill=INK)

    im = im.resize((size, size), Image.LANCZOS)
    return im


def round_mask(im):
    size = im.width
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out


for dens, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                   ("xxhdpi", 144), ("xxxhdpi", 192)):
    os.makedirs("res/mipmap-" + dens, exist_ok=True)
    base = launcher(size)
    base.save("res/mipmap-%s/ic_launcher.png" % dens)
    round_mask(base).save("res/mipmap-%s/ic_launcher_round.png" % dens)


def white_canvas(w, h):
    im = Image.new("RGBA", (w * 8, h * 8), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def save_white(name, draw_fn, w=24, h=24):
    im, d = white_canvas(w, h)
    draw_fn(d, 8)
    im.resize((w * 3, h * 3), Image.LANCZOS).save("res/drawable-xxhdpi/%s.png" % name)


def tri(d, k, x, y, size, left):
    if left:
        d.polygon([(x * k, y * k), ((x + size) * k, (y + size / 2) * k),
                   (x * k, (y + size) * k)], fill=W)
    else:
        d.polygon([((x + size) * k, y * k), (x * k, (y + size / 2) * k),
                   ((x + size) * k, (y + size) * k)], fill=W)


def icon_prev(d, k):
    d.rounded_rectangle([4.5 * k, 5 * k, 7 * k, 19 * k], radius=1.2 * k, fill=W)
    tri(d, k, 8, 5, 14, True)


def icon_next(d, k):
    d.rounded_rectangle([17 * k, 5 * k, 19.5 * k, 19 * k], radius=1.2 * k, fill=W)
    tri(d, k, 2, 5, 14, False)


def icon_play(d, k):
    d.polygon([(7.5 * k, 4.5 * k), (19 * k, 12 * k), (7.5 * k, 19.5 * k)], fill=W)


def icon_pause(d, k):
    d.rounded_rectangle([6.5 * k, 4.5 * k, 10 * k, 19.5 * k], radius=1.4 * k, fill=W)
    d.rounded_rectangle([14 * k, 4.5 * k, 17.5 * k, 19.5 * k], radius=1.4 * k, fill=W)


def icon_close(d, k):
    d.line([(7 * k, 7 * k), (17 * k, 17 * k)], fill=W, width=int(2 * k))
    d.line([(17 * k, 7 * k), (7 * k, 17 * k)], fill=W, width=int(2 * k))


def icon_stat(d, k):
    cx, cy, r = 12 * k, 12 * k, 8.4 * k
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=W, width=int(1.6 * k))
    d.ellipse([cx - r * 0.52, cy - r * 0.52, cx + r * 0.52, cy + r * 0.52],
              outline=(255, 255, 255, 170), width=int(1.0 * k))
    h = r * 0.16
    d.ellipse([cx - h, cy - h, cx + h, cy + h], fill=W)


save_white("ic_prev", icon_prev)
save_white("ic_next", icon_next)
save_white("ic_play", icon_play)
save_white("ic_pause", icon_pause)
save_white("ic_close", icon_close)
save_white("stat_music", icon_stat)

print("icons ok")
