"""
Gera o fx spotlight dos ecrãs de introdução (res/drawable-nodpi/fx_spotlight.png).

No Figma, o "fx spotlight" é um círculo de 63 dp (#4A3C99, 63 % de opacidade) com um desfoque
PROGRESSIVO: raio 9 em cima e 100 em baixo (o Figma mede o desfoque em dobro: sigma 4,5 a 50 dp).
É isso que lhe dá o aspeto de holofote: a parte de cima quase nítida e a de baixo a espalhar-se.
O export em SVG do Figma não suporta desfoque progressivo (põe um desfoque uniforme), por isso a
imagem é calculada aqui: cada linha da imagem é desfocada com o sigma que lhe corresponde.

Para correr: python3 tools/bake_spotlight.py
"""
import os
import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

SCALE = 2                     # px por dp
DIAMETER = 63.0               # dp
SIGMA_TOP, SIGMA_BOTTOM = 4.5, 50.0   # dp (raio 9 e 100 do Figma)
OPACITY = 0.63
COLOR = (0x4A, 0x3C, 0x99)

# Área da imagem, em dp, relativa ao canto de cima à esquerda do círculo.
LEFT, TOP, WIDTH, HEIGHT = -152.0, -20.0, 367.0, 235.0

w, h = int(WIDTH * SCALE), int(HEIGHT * SCALE)
ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
# Coordenadas em dp (centro de cada píxel), com 4x4 sub-amostras para o círculo ter bordas suaves.
disk = np.zeros((h, w))
for sy in range(4):
    for sx in range(4):
        x = LEFT + (xs + (sx + 0.5) / 4) / SCALE
        y = TOP + (ys + (sy + 0.5) / 4) / SCALE
        disk += ((x - DIAMETER / 2) ** 2 + (y - DIAMETER / 2) ** 2) <= (DIAMETER / 2) ** 2
disk /= 16.0

# Sigma de cada linha (linear de cima para baixo do círculo; fora dele fica no valor da ponta).
row_y = TOP + (np.arange(h) + 0.5) / SCALE
t = np.clip(row_y / DIAMETER, 0.0, 1.0)
row_sigma = SIGMA_TOP + (SIGMA_BOTTOM - SIGMA_TOP) * t

levels = np.linspace(SIGMA_TOP, SIGMA_BOTTOM, 48)
blurred = [gaussian_filter(disk, s * SCALE, mode="constant") for s in levels]

out = np.zeros((h, w))
for r in range(h):
    s = row_sigma[r]
    i = min(np.searchsorted(levels, s), len(levels) - 1)
    j = max(i - 1, 0)
    if i == j:
        out[r] = blurred[i][r]
    else:
        f = (s - levels[j]) / (levels[i] - levels[j])
        out[r] = (1 - f) * blurred[j][r] + f * blurred[i][r]

alpha = np.clip(out * OPACITY, 0, 1)
rgba = np.zeros((h, w, 4), dtype=np.uint8)
rgba[..., 0], rgba[..., 1], rgba[..., 2] = COLOR
rgba[..., 3] = np.round(alpha * 255).astype(np.uint8)

here = os.path.dirname(os.path.abspath(__file__))
path = os.path.join(here, "..", "app", "src", "main", "res", "drawable-nodpi", "fx_spotlight.png")
Image.fromarray(rgba, "RGBA").save(path, optimize=True)
print("guardado:", os.path.normpath(path), w, "x", h, "| opacidade máxima:", round(alpha.max(), 3))
# Perfil vertical pelo centro, para conferir (dp a partir do topo do círculo).
cx = int((-LEFT + DIAMETER / 2) * SCALE)
for dp in (-5, 0, 5, 15, 31.5, 50, 63, 90, 120, 160):
    r = int((dp - TOP) * SCALE)
    print(f"  y={dp:6.1f} dp  opacidade={alpha[r, cx]:.3f}")
