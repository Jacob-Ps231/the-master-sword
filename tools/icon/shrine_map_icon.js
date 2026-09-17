// Draws the shrine's map decoration: the sword in its stone, 16x16.
//
// Redrawn by hand from Jerome's mock-up of 17/09/2026, with the colours
// sampled from it. The mock-up is not a texture: its "8x8" sprite is really
// about 11x25 cells on an irregular grid. The game stretches any square sprite
// over the same 8 map pixels, so 16x16 shows at vanilla size with a finer grain.
//
// Run from the repository root: node tools/icon/shrine_map_icon.js

const path = require('path');
const { write } = require('./png.js');

const PALETTE = {
	K: '0a0a1c', // outline
	V: '6242fc', // violet highlight
	B: '3532b8', // blue
	D: '1f1e78', // dark blue
	G: 'fdd028', // gold
	g: 'e3ab07', // dark gold
	W: 'e5e7f6', // blade, lit side
	S: 'a2a5c6', // blade, shaded side
	s: '66666a', // stone, light
	m: '505158', // stone, mid
	d: '3d3d3f', // stone, dark
};

const ROWS = [
	'......KKKK......',
	'.....KDVVDK.....',
	'.....KBDDBK.....',
	'......KBDK......',
	'......KDBK......',
	'..KKKKKBDKKKKK..',
	'.KBVDDDGGDDDVBK.',
	'.KDBVDDggDDVBDK.',
	'..KKK.KGGK.KKK..',
	'......KWSK......',
	'......KWSK......',
	'......KWSK......',
	'....KKKWSKKK....',
	'...KsmsWSsmsK...',
	'..KmsdmddmdsmK..',
	'..KKKKKKKKKKKK..',
];

const size = ROWS.length;
const pixels = Buffer.alloc(size * size * 4);
ROWS.forEach((row, y) => {
	if (row.length !== size) {
		throw new Error(`row ${y} is ${row.length} wide, expected ${size}`);
	}
	[...row].forEach((cell, x) => {
		if (cell === '.') {
			return;
		}
		const hex = PALETTE[cell];
		if (!hex) {
			throw new Error(`unknown colour '${cell}' at ${x},${y}`);
		}
		const o = (y * size + x) * 4;
		pixels[o] = parseInt(hex.slice(0, 2), 16);
		pixels[o + 1] = parseInt(hex.slice(2, 4), 16);
		pixels[o + 2] = parseInt(hex.slice(4, 6), 16);
		pixels[o + 3] = 255;
	});
});

const out = path.join('src', 'main', 'resources', 'assets', 'mastersword', 'textures', 'map', 'decorations', 'shrine.png');
write(out, size, size, pixels);
console.log(`wrote ${out}`);
