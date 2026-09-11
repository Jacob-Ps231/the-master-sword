# Génération du sprite de l'épée

`master_sword.png` (64×64) n'est pas édité à la main : il est **produit depuis
`reference.png`**, l'image source haute résolution.

```bash
node tools/sprite/from_reference.js
```

Le script écrit
`src/main/resources/assets/mastersword/textures/item/master_sword.png`.
Pour changer le dessin, remplacer `reference.png` et relancer.

## Ce que fait le script, et pourquoi

Le redimensionnement est la partie facile. Deux détails comptent davantage.

**La palette doit être quantifiée.** C'est ce qui guérit vraiment le flou. La
référence est une illustration en dégradés : réduite telle quelle, elle donnait
**2205 couleurs pour 2661 pixels opaques** — 83 %, dont 87 % de la palette
utilisée une seule fois. C'est l'exact opposé du pixel art, et en jeu ça rend
une bouillie. Une texture d'item Minecraft vit sur une vingtaine d'aplats.

**L'alpha doit finir binaire.** `item/handheld` ne se contente pas d'afficher le
sprite : il **extrude sa géométrie depuis les pixels opaques**. Un bord adouci
n'y produit pas un bord adouci, mais des quads parasites autour de la lame.
Chaque pixel est donc forcé à tout ou rien, au seuil `ALPHA_CUT`.

**La moyenne doit être prémultipliée par l'alpha.** Les pixels transparents
portent presque toujours du RGB noir ; moyenner la couleur brute traînerait une
frange sombre sur tous les contours.

Le reste : recadrage sur le contenu réellement dessiné, mise à l'échelle à ratio
constant, marge de `MARGIN` pixels pour que la géométrie extrudée ne touche pas
le bord du sprite, puis retrait des pixels isolés que le seuil d'alpha aurait pu
laisser en suspension.

## Réglages

En haut de `from_reference.js` :

- `N` — taille du sprite (64).
- `COLOURS` — taille de la palette après quantification (24). **C'est le réglage
  qui gouverne la netteté**, plus encore que `N`.
- `MARGIN` — marge autour du dessin.
- `ALPHA_CUT` — couverture à partir de laquelle un pixel est conservé. Plus bas,
  la silhouette grossit ; plus haut, elle maigrit et les fins détails tombent.

`png.js` est un encodeur/décodeur PNG minimal (RGBA 8 bits), sans dépendance.

## Origine

`reference.png` a été **générée par Jérôme avec ChatGPT** : aucun asset tiers
n'entre dans le dépôt. Voir l'entrée du 11/09/2026 au journal de `SPEC.md`.
