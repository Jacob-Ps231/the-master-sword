// Draws the shrine map item: vanilla explorer-map paper, our own emblem.
//
// 26.3 turned every explorer map into an item of its own, so the map the
// cartographer sells needs a texture like woodland_mansion_map's: the same
// rolled paper, with a pictogram drawn in the window vanilla uses (x 5..12,
// y 4..12 of the 16x16). The paper and frame are taken from the vanilla
// texture so ours sits beside it without looking foreign; only the window is
// repainted, and the colours are the ones sampled from Jerome's artwork.
//
// Run from the repository root:
//   node tools/icon/shrine_map_item.js "<path to minecraft-merged.jar extract>"
// The default path is the Loom cache of this machine.

const fs = require('fs');
const path = require('path');
const { read, write } = require('./png.js');

const VANILLA = process.argv[2]
	|| 'C:/Temp/mapitems/assets/minecraft/textures/item/woodland_mansion_map.png';

const PALETTE = {
	K: '000000', // outline
	V: '6242fc', // violet highlight
	B: '3532b8', // blue
	D: '1f1e78', // dark blue
	G: 'fdd028', // gold
	W: 'e5e7f6', // blade, lit side
	S: 'a2a5c6', // blade, shaded side
	s: '8a8a90', // stone, light
	m: '616161', // stone, mid
	p: 'ebd7b2', // the parchment behind the emblem
};

const ROWS = [
	'..KKKK..',
	'.KDVVDK.',
	'..KBBK..',
	'KBVGGVBK',
	'.KKWSKK.',
	'..KWSK..',
	'.KssWSsK',
	'KsmmmmsK',
	'KKKKKKKK',
];

const base = read(VANILLA);
if (base.w !== 16 || base.h !== 16) {
	throw new Error(`expected a 16x16 base, got ${base.w}x${base.h}`);
}

const pixels = Buffer.from(base.px);
const set = (x, y, hex) => {
	const o = (y * 16 + x) * 4;
	pixels[o] = parseInt(hex.slice(0, 2), 16);
	pixels[o + 1] = parseInt(hex.slice(2, 4), 16);
	pixels[o + 2] = parseInt(hex.slice(4, 6), 16);
	pixels[o + 3] = 255;
};

ROWS.forEach((row, ry) => {
	if (row.length !== 8) {
		throw new Error(`row ${ry} is ${row.length} wide, expected 8`);
	}
	[...row].forEach((cell, rx) => {
		const hex = cell === '.' ? PALETTE.p : PALETTE[cell];
		if (!hex) {
			throw new Error(`unknown colour '${cell}' at ${rx},${ry}`);
		}
		set(5 + rx, 4 + ry, hex);
	});
});

const out = path.join('src', 'main', 'resources', 'assets', 'mastersword', 'textures', 'item', 'shrine_map.png');
write(out, 16, 16, pixels);
console.log(`wrote ${out}`);
