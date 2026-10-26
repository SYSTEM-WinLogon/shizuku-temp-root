from PIL import Image, ImageDraw
import os

S = 2048  # supersample
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

BG = (22, 107, 84, 255)
WHITE = (255, 255, 255, 255)

def sx(v):
    return int(v * S / 512)

# rounded square background
d.rounded_rectangle([0, 0, S - 1, S - 1], radius=sx(112), fill=BG)

# shackle (padlock arch)
cx, cy, r, w = sx(256), sx(216), sx(86), sx(34)
d.ellipse([cx - r - w // 2, cy - r - w // 2, cx + r + w // 2, cy + r + w // 2],
          outline=WHITE, width=w)
# cover lower half of the ring with background so only the arch shows
d.rectangle([0, cy, S, S], fill=BG)

# lock body
d.rounded_rectangle([sx(148), sx(258), sx(364), sx(432)], radius=sx(46), fill=WHITE)

# keyhole
d.ellipse([sx(230), sx(300), sx(282), sx(352)], fill=BG)
d.polygon([(sx(245), sx(342)), (sx(267), sx(342)), (sx(276), sx(398)), (sx(236), sx(398))], fill=BG)

img = img.resize((512, 512), Image.LANCZOS)
out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'icon')
os.makedirs(out_dir, exist_ok=True)
img.save(os.path.join(out_dir, 'icon_512.png'))

sizes = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for name, size in sizes.items():
    d2 = os.path.join(out_dir, 'mipmap-' + name)
    os.makedirs(d2, exist_ok=True)
    img.resize((size, size), Image.LANCZOS).save(os.path.join(d2, 'ic_launcher.png'))

print('ok')
