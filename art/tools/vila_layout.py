"""Planta da Vila Aurora (mapa 0) em blocos de cor, a partir das coordenadas do código (Game.kt / Renderer.kt)."""
import sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]
MW, MH = 1600, 1100
IW, IH = 1920, 1080
S = 0.9
OX, OY = 24, 80


def P(x, y):
    return (OX + x * S, OY + y * S)


def R(x0, y0, x1, y1):
    a, b = P(x0, y0), P(x1, y1)
    return [a[0], a[1], b[0], b[1]]


F = 'C:/Windows/Fonts/'
f_title = ImageFont.truetype(F + 'segoeuib.ttf', 34)
f_b = ImageFont.truetype(F + 'segoeuib.ttf', 19)
f = ImageFont.truetype(F + 'segoeui.ttf', 17)
f_s = ImageFont.truetype(F + 'segoeui.ttf', 13)

C = dict(
    bg='#1E2430', grama='#8DAA55', vila='#D8C39A', caminho='#A98C5E', praca='#9E978A', fonte_borda='#7D776B',
    agua='#4FA3D1', casa='#C0504D', muro='#5B574F', pilar='#3E3B35', saida='#F2A93B', portal='#8E5BE0',
    spawn='#FFFFFF', grid=(0, 0, 0, 30),
)
NPCS = [  # Game.kt changeMap(0)
    ('Curandeira', 800, 400, '#F48FB1'),
    ('Mercador', 520, 480, '#FFD54F'),
    ('Ferreiro', 1080, 480, '#FF7043'),
    ('Mestre de Classes', 520, 760, '#5C6BC0'),
    ('Teleportadora', 1080, 760, '#26C6DA'),
]
HOUSES = [(230, 260), (620, 210), (980, 210), (1370, 260), (230, 960), (1370, 960), (640, 1000), (960, 1000)]
L, T, Rr, B = 70, 110, MW - 70, MH - 50          # buildTown
CX, CY = 800, 590                                 # praça / fonte
GAP0, GAP1 = MH / 2 - 90, MH / 2 + 90              # abertura dos muros

im = Image.new('RGBA', (IW, IH), C['bg'])
d = ImageDraw.Draw(im, 'RGBA')

# grama (mapa inteiro) e área de areia da vila
d.rectangle(R(0, 0, MW, MH), fill=C['grama'])
d.rounded_rectangle(R(L - 6, T - 6, Rr + 6, B + 6), radius=27, fill=C['vila'])
d.rectangle(R(0, MH / 2 - 70, L + 10, MH / 2 + 70), fill=C['vila'])
d.rectangle(R(Rr - 10, MH / 2 - 70, MW, MH / 2 + 70), fill=C['vila'])


# caminhos de terra (quadráticas iguais a dirtPath, largura 56)
def quad(x0, y0, x1, y1, bend, n=240):
    mx, my = (x0 + x1) / 2 + bend, (y0 + y1) / 2 - bend * 0.5
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * mx + t * t * x1
        y = (1 - t) ** 2 * y0 + 2 * (1 - t) * t * my + t * t * y1
        pts.append(P(x, y))
    return pts


paths = [quad(CX, CY, 0, MH / 2, 0), quad(CX, CY, MW, MH / 2, 0)]
paths += [quad(CX, CY, x, y + 6, 40 if i % 2 == 0 else -40) for i, (x, y) in enumerate(HOUSES)]
w = int(56 * S)
for pts in paths:
    for p in pts:
        d.ellipse([p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2], fill=C['caminho'])

# praça
d.ellipse(R(CX - 370, CY - 270, CX + 370, CY + 270), fill=C['praca'], outline=C['fonte_borda'], width=int(12 * S))

# muros (com abertura nas saídas) e pilares
mw = int(16 * S)
for wy in (T, B):
    d.line([P(L, wy), P(Rr, wy)], fill=C['muro'], width=mw)
for wx in (L, Rr):
    d.line([P(wx, T), P(wx, GAP0)], fill=C['muro'], width=mw)
    d.line([P(wx, GAP1), P(wx, B)], fill=C['muro'], width=mw)
    for py in (GAP0, GAP1):
        d.rectangle(R(wx - 13, py - 13, wx + 13, py + 13), fill=C['pilar'])

# casas (planta: alicerce x±64, de y-70 até y+3; porta voltada para baixo)
for i, (x, y) in enumerate(HOUSES):
    d.rectangle(R(x - 64, y - 70, x + 64, y + 3), fill=C['casa'], outline='#7A2E2C', width=2)
    d.rectangle(R(x - 12, y - 6, x + 12, y + 3), fill='#5D3A1A')
    cx, cy = P(x, y - 34)
    d.text((cx, cy), f'Casa {i + 1}', font=f_b, fill='white', anchor='mm')

# fonte
d.ellipse(R(CX - 80, CY - 44, CX + 80, CY + 20), fill=C['fonte_borda'])
d.ellipse(R(CX - 70, CY - 38, CX + 70, CY + 8), fill=C['agua'])
d.text(P(CX, CY - 15), 'Fonte', font=f_b, fill='white', anchor='mm')

# saídas
for x0, x1, txt, ax in ((0, 60, 'Floresta', 'lm'), (MW - 60, MW, 'Campos de Aurora', 'rm')):
    d.rectangle(R(x0, MH / 2 - 70, x1, MH / 2 + 70), fill=C['saida'])
for (x, txt, ax) in ((10, '← Saída oeste', 'lm'), (MW - 10, 'Saída leste →', 'rm')):
    d.text(P(x, MH / 2 + 118), txt, font=f_b, fill='#3A2A00', anchor=ax)

# portais (Game.kt: x = 150 e w-150, y = h/2)
for x, dest in ((150, 'Floresta\n(Campo de Aprendizes)'), (MW - 150, 'Campos de Aurora')):
    cx, cy = P(x, MH / 2)
    r = 34 * S
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=C['portal'], outline='white', width=3)
    d.text((cx, cy + r + 6), 'Portal → ' + dest.split('\n')[0], font=f_s, fill='#2B1650', anchor='ma')

# ponto de chegada (changeMap: w/2, h/2+130)
sx, sy = P(MW / 2, MH / 2 + 130)
d.regular_polygon((sx, sy, 13), 4, rotation=45, fill=C['spawn'], outline='#333', width=2)
d.text((sx + 18, sy), 'Chegada', font=f_s, fill='#222', anchor='lm')

# NPCs
for nome, x, y, cor in NPCS:
    cx, cy = P(x, y)
    r = 13
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=cor, outline='#111', width=3)
    tw = d.textlength(nome, font=f)
    d.rounded_rectangle([cx - tw / 2 - 6, cy - r - 28, cx + tw / 2 + 6, cy - r - 5], radius=5, fill='#000000AA')
    d.text((cx, cy - r - 16), nome, font=f, fill='white', anchor='mm')

# corta o que passou da borda do mapa
m = Image.new('L', im.size, 0)
ImageDraw.Draw(m).rectangle(R(0, 0, MW, MH), fill=255)
base = Image.new('RGBA', im.size, C['bg'])
im = Image.composite(im, base, m)
d = ImageDraw.Draw(im, 'RGBA')

# grade de 100 unidades com coordenadas na borda
gl = Image.new('RGBA', im.size, (0, 0, 0, 0))
gd = ImageDraw.Draw(gl)
for gx in range(0, MW + 1, 100):
    gd.line([P(gx, 0), P(gx, MH)], fill=C['grid'], width=1)
    d.text((P(gx, 0)[0], OY - 4), str(gx), font=f_s, fill='#9AA4B5', anchor='mb')
for gy in range(0, MH + 1, 100):
    gd.line([P(0, gy), P(MW, gy)], fill=C['grid'], width=1)
im = Image.alpha_composite(im, gl)
d = ImageDraw.Draw(im, 'RGBA')
d.rectangle(R(0, 0, MW, MH), outline='#000', width=2)

# título e legenda
d.text((OX, 18), 'Vila Aurora — planta vista de cima', font=f_title, fill='white')
lx = OX + MW * S + 40
ly = 90
d.text((lx, ly), 'Legenda', font=f_b, fill='white')
ly += 36
items = [
    ('grama', 'Grama (fora dos muros; árvores aleatórias)'), ('vila', 'Chão da vila (areia)'),
    ('caminho', 'Caminhos de terra'), ('praca', 'Praça de lajotas'), ('agua', 'Fonte'),
    ('casa', 'Casas (8)'), ('muro', 'Muros / mureta de pedra'), ('pilar', 'Pilares das aberturas'),
    ('saida', 'Saídas do mapa'), ('portal', 'Portais'),
]
for k, txt in items:
    d.rectangle([lx, ly, lx + 26, ly + 20], fill=C[k], outline='#00000066')
    d.text((lx + 36, ly + 10), txt, font=f, fill='#E6E9EF', anchor='lm')
    ly += 32
d.regular_polygon((lx + 13, ly + 10, 11), 4, rotation=45, fill=C['spawn'])
d.text((lx + 36, ly + 10), 'Chegada do herói', font=f, fill='#E6E9EF', anchor='lm')
ly += 44
d.text((lx, ly), 'NPCs', font=f_b, fill='white')
ly += 32
for nome, x, y, cor in NPCS:
    d.ellipse([lx + 3, ly, lx + 23, ly + 20], fill=cor, outline='#111', width=2)
    d.text((lx + 36, ly + 10), f'{nome}  ({x}, {y})', font=f, fill='#E6E9EF', anchor='lm')
    ly += 30
ly += 18
for line in ('Mapa 1600 × 1100 unidades', 'Grade a cada 100 unidades', 'Oeste → Floresta (mapa 1)', 'Leste → Campos de Aurora (mapa 2)'):
    d.text((lx, ly), line, font=f_s, fill='#9AA4B5')
    ly += 22

im.convert('RGB').save(OUT)
print('ok', im.size)
