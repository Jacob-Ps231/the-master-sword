// Converts reference.png into the item texture.
//
// Three things matter here, and the resizing is the least of them.
//
// 1. Alpha must end up binary. item/handheld builds the item's geometry by
//    extruding the sprite's opaque pixels, so a soft edge does not read as a
//    soft edge -- it reads as extra quads jutting out around the blade.
// 2. The average must be alpha-premultiplied. Transparent pixels usually carry
//    black RGB; averaging raw colour would drag a dark fringe into every edge.
// 3. The palette must be quantised. This is what actually cures the blur. The
//    reference is a smooth-shaded illustration: downscaled untouched it came
//    out with 2205 distinct colours for 2661 opaque pixels -- 83%, with 87% of
//    the palette used exactly once. That is the opposite of pixel art, and it
//    reads as mush in game. Minecraft item textures live on a couple of dozen
//    flats, so the colour cloud is cut down to COLOURS.
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./png.js');

const SRC = path.join(__dirname, 'reference.png');
const OUT = path.join(__dirname, '../../src/main/resources/assets/mastersword/textures/item/master_sword.png');

const N = 64;              // sprite size
const COLOURS = 24;        // palette size after quantisation
const MARGIN = 1;          // keeps the extruded geometry clear of the sprite edge
const ALPHA_CUT = 0.42;    // coverage above which an output pixel is kept

// --- area-average downscale, alpha-premultiplied ---------------------------
function downsample(img, size, margin, cut) {
  const { width: SW, height: SH, data } = img;
  let minX = SW, minY = SH, maxX = -1, maxY = -1;
  for (let y = 0; y < SH; y++) {
    for (let x = 0; x < SW; x++) {
      if (data[(y * SW + x) * 4 + 3] >= 128) {
        if (x < minX) minX = x; if (x > maxX) maxX = x;
        if (y < minY) minY = y; if (y > maxY) maxY = y;
      }
    }
  }
  const cw = maxX - minX + 1, ch = maxY - minY + 1;
  const box = size - margin * 2;
  const scale = Math.min(box / cw, box / ch);
  const dw = Math.max(1, Math.round(cw * scale));
  const dh = Math.max(1, Math.round(ch * scale));
  const offX = Math.floor((size - dw) / 2), offY = Math.floor((size - dh) / 2);
  const out = Buffer.alloc(size * size * 4, 0);

  for (let y = 0; y < dh; y++) {
    for (let x = 0; x < dw; x++) {
      const sx0 = minX + (x * cw) / dw, sx1 = minX + ((x + 1) * cw) / dw;
      const sy0 = minY + (y * ch) / dh, sy1 = minY + ((y + 1) * ch) / dh;
      let r = 0, g = 0, b = 0, aSum = 0, wSum = 0;
      for (let sy = Math.floor(sy0); sy <= Math.min(SH - 1, Math.ceil(sy1) - 1); sy++) {
        const wy = Math.min(sy + 1, sy1) - Math.max(sy, sy0);
        if (wy <= 0) continue;
        for (let sx = Math.floor(sx0); sx <= Math.min(SW - 1, Math.ceil(sx1) - 1); sx++) {
          const wx = Math.min(sx + 1, sx1) - Math.max(sx, sx0);
          if (wx <= 0) continue;
          const w = wx * wy, o = (sy * SW + sx) * 4, a = data[o + 3] / 255;
          r += data[o] * a * w; g += data[o + 1] * a * w; b += data[o + 2] * a * w;
          aSum += a * w; wSum += w;
        }
      }
      if (!wSum || aSum / wSum < cut) continue;
      const o = ((y + offY) * size + (x + offX)) * 4;
      out[o] = Math.round(r / aSum);
      out[o + 1] = Math.round(g / aSum);
      out[o + 2] = Math.round(b / aSum);
      out[o + 3] = 255;
    }
  }
  return { width: size, height: size, data: out };
}

// --- median cut: split the colour cloud along its widest axis --------------
function quantise(img, k) {
  const px = [];
  for (let i = 0; i < img.data.length; i += 4) {
    if (img.data[i + 3]) px.push([img.data[i], img.data[i + 1], img.data[i + 2]]);
  }
  if (!px.length) return { img, palette: 0 };

  const spanOf = (box, c) => {
    let lo = 255, hi = 0;
    for (const p of box) { if (p[c] < lo) lo = p[c]; if (p[c] > hi) hi = p[c]; }
    return hi - lo;
  };

  let boxes = [px];
  while (boxes.length < k) {
    let pick = -1, bestScore = -1, pickAxis = 0;
    boxes.forEach((box, i) => {
      if (box.length < 2) return;
      for (let c = 0; c < 3; c++) {
        // weight by population so big flats keep their own entries
        const score = spanOf(box, c) * box.length;
        if (score > bestScore) { bestScore = score; pick = i; pickAxis = c; }
      }
    });
    if (pick < 0) break;
    const box = boxes[pick];
    box.sort((a, b) => a[pickAxis] - b[pickAxis]);
    const mid = box.length >> 1;
    boxes.splice(pick, 1, box.slice(0, mid), box.slice(mid));
  }

  const palette = boxes.filter((b) => b.length).map((b) => {
    const s = b.reduce((a, p) => [a[0] + p[0], a[1] + p[1], a[2] + p[2]], [0, 0, 0]);
    return s.map((v) => Math.round(v / b.length));
  });

  for (let i = 0; i < img.data.length; i += 4) {
    if (!img.data[i + 3]) continue;
    let best = palette[0], bd = Infinity;
    for (const p of palette) {
      const d = (img.data[i] - p[0]) ** 2 + (img.data[i + 1] - p[1]) ** 2 + (img.data[i + 2] - p[2]) ** 2;
      if (d < bd) { bd = d; best = p; }
    }
    img.data[i] = best[0]; img.data[i + 1] = best[1]; img.data[i + 2] = best[2];
  }
  return { img, palette: palette.length };
}

// --- run -------------------------------------------------------------------
const src = decode(fs.readFileSync(SRC));
const small = downsample(src, N, MARGIN, ALPHA_CUT);
const { img, palette } = quantise(small, COLOURS);

// drop pixels the alpha cut left dangling with no orthogonal neighbour
let pruned = 0;
for (let pass = 0; pass < 2; pass++) {
  const doomed = [];
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const i = (y * N + x) * 4;
      if (!img.data[i + 3]) continue;
      const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].filter(([ox, oy]) => {
        const nx = x + ox, ny = y + oy;
        return nx >= 0 && ny >= 0 && nx < N && ny < N && img.data[(ny * N + nx) * 4 + 3];
      }).length;
      if (n === 0) doomed.push(i);
    }
  }
  for (const i of doomed) { img.data[i + 3] = 0; pruned++; }
}

fs.writeFileSync(OUT, encode(img));

let kept = 0;
for (let i = 3; i < img.data.length; i += 4) if (img.data[i]) kept++;
console.log(`source ${src.width}x${src.height} -> ${N}x${N}`);
console.log(`pixels opaques: ${kept}, palette: ${palette} couleurs, isolés retirés: ${pruned}`);
console.log('écrit:', path.relative(process.cwd(), OUT));
