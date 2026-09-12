// Builds the structure the Master Sword generates in: a small mossy ruin with
// the pedestal at its centre, sword already planted.
//
// Written as code rather than saved from a structure block so it stays
// reproducible and reviewable in diffs. Reopening it in game at a structure
// block and saving over it still works -- that is what SPEC 4.2 asks for.
//
// Two things drive the design, both learned in game:
//
// 1. The floor is entirely stone, never dirt or grass. surface_structures runs
//    BEFORE vegetal_decoration, so trees are placed after the structure and
//    will happily grow straight through it -- carving air above changes
//    nothing, the tree comes later. Denying them soil to root in is what works.
// 2. The clearing is wider than the decoration needs. A dark oak trunk is 2x2
//    and the canopy is broad, so a narrow ring still lets a neighbouring trunk
//    clip the pedestal.
const fs = require('fs');
const path = require('path');
const { encode, decode, plain, b, i, s, list, comp } = require('./nbt.js');

const OUT = path.join(__dirname, '../../src/main/resources/data/mastersword/structure/master_sword.nbt');
const DATA_VERSION = 4903; // 26.2, from the client's version.json world_version

const W = 9, H = 6, D = 9;
const CX = 4, CZ = 4;     // pedestal column
const RADIUS = 4.3;       // floor disc

// Deterministic so the file is reproducible: same seed, same ruin.
let seed = 0x5eed1e;
const rand = () => {
  seed = (seed * 1664525 + 1013904223) >>> 0;
  return seed / 0x100000000;
};
const pick = (table) => {
  const total = table.reduce((a, e) => a + e[1], 0);
  let r = rand() * total;
  for (const [name, w] of table) { if ((r -= w) < 0) return name; }
  return table[table.length - 1][0];
};

// Weathered stone, mossier towards the middle. No block here belongs to
// #minecraft:dirt, so nothing can take root in the ruin.
const INNER = [
  ['minecraft:mossy_cobblestone', 6],
  ['minecraft:mossy_stone_bricks', 5],
  ['minecraft:stone_bricks', 2],
  ['minecraft:cracked_stone_bricks', 2],
  ['minecraft:cobblestone', 2],
  ['minecraft:andesite', 1],
];
const OUTER = [
  ['minecraft:mossy_cobblestone', 5],
  ['minecraft:cobblestone', 4],
  ['minecraft:andesite', 3],
  ['minecraft:stone', 2],
  ['minecraft:gravel', 2],
  ['minecraft:cracked_stone_bricks', 1],
];
// The corners and rim, where the ruin fades into the forest floor. They are
// paved too: the whole footprint must be stone. An earlier version left these
// cells bare for a ragged look, which put grass back inside the structure and
// handed a dark oak somewhere to root 4 blocks from the pedestal. The ragged
// edge now comes from the material, never from a hole.
const EDGE = [
  ['minecraft:gravel', 5],
  ['minecraft:andesite', 4],
  ['minecraft:cobblestone', 3],
  ['minecraft:stone', 3],
  ['minecraft:mossy_cobblestone', 2],
];

// --- palette --------------------------------------------------------------
const palette = [];
const stateIndex = (name, props) => {
  const key = name + '|' + JSON.stringify(props || {});
  let idx = palette.findIndex((p) => p.key === key);
  if (idx < 0) {
    const entry = { key, tag: { Name: s(name) } };
    if (props) entry.tag.Properties = comp(Object.fromEntries(
      Object.entries(props).map(([k, v]) => [k, s(String(v))]),
    ));
    palette.push(entry);
    idx = palette.length - 1;
  }
  return idx;
};

const blocks = [];
const put = (x, y, z, name, props, nbt) => {
  const entry = { pos: list('int', [i(x), i(y), i(z)]), state: i(stateIndex(name, props)) };
  if (nbt) entry.nbt = nbt;
  blocks.push(comp(entry));
};

const dist = (x, z) => Math.hypot(x - CX, z - CZ);

// --- floor, then air above it ---------------------------------------------
const floor = [];
let ground = 0, air = 0;
for (let z = 0; z < D; z++) {
  for (let x = 0; x < W; x++) {
    const d = dist(x, z);
    put(x, 0, z, pick(d <= 2.2 ? INNER : d <= RADIUS ? OUTER : EDGE));
    floor.push([x, z]);
    ground++;
    for (let y = 2; y < H; y++) { put(x, y, z, 'minecraft:air'); air++; }
  }
}

// --- y=1: moss carpet scattered over the stone, air elsewhere -------------
let carpet = 0;
for (const [x, z] of floor) {
  if (x === CX && z === CZ) continue;          // pedestal
  if (dist(x, z) > 1.2 && rand() < 0.28) { put(x, 1, z, 'minecraft:moss_carpet'); carpet++; }
  else { put(x, 1, z, 'minecraft:air'); air++; }
}

// The pedestal carries its block entity straight in the file -- the same way
// vanilla chests carry their loot table. This is what puts the sword in the
// stone at world generation rather than needing code to fill it afterwards.
put(CX, 1, CZ, 'mastersword:master_sword_pedestal', { facing: 'north' }, comp({
  id: s('mastersword:master_sword_pedestal'),
  sword: comp({ id: s('mastersword:master_sword'), count: i(1) }),
  fog_consumed: b(0),
}));

// --- assemble -------------------------------------------------------------
const root = comp({
  size: list('int', [i(W), i(H), i(D)]),
  entities: list('end', []),
  blocks: list('compound', blocks),
  palette: list('compound', palette.map((p) => comp(p.tag))),
  DataVersion: i(DATA_VERSION),
});

fs.mkdirSync(path.dirname(OUT), { recursive: true });
const buf = encode(root);
fs.writeFileSync(OUT, buf);

// --- read it back, so a malformed file cannot pass silently ---------------
const back = plain(decode(fs.readFileSync(OUT)));
const pedestal = back.blocks.find((bl) => back.palette[bl.state].Name.startsWith('mastersword:'));
const DIRT = ['minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt',
  'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block'];
const problems = [];
if (JSON.stringify(back.size) !== JSON.stringify([W, H, D])) problems.push('size');
if (back.DataVersion !== DATA_VERSION) problems.push('DataVersion');
if (!pedestal) problems.push('socle absent');
else {
  if (JSON.stringify(pedestal.pos) !== JSON.stringify([CX, 1, CZ])) problems.push('position du socle');
  if (back.palette[pedestal.state].Properties?.facing !== 'north') problems.push('facing');
  if (pedestal.nbt?.sword?.id !== 'mastersword:master_sword') problems.push('épée absente du block entity');
}
const soil = back.palette.map((p) => p.Name).filter((n) => DIRT.includes(n));
if (soil.length) problems.push(`sol enracinable: ${soil.join(', ')}`);
// Every cell of the footprint must be paved. A gap is not a cosmetic detail:
// it is bare ground inside the structure, and a tree will find it.
const paved = new Set(back.blocks.filter((bl) => bl.pos[1] === 0).map((bl) => `${bl.pos[0]},${bl.pos[2]}`));
if (paved.size !== W * D) problems.push(`emprise non pavée: ${paved.size}/${W * D} cases`);

const kinds = back.palette.map((p) => p.Name.replace('minecraft:', ''));
console.log(`${buf.length} octets | ${blocks.length} blocs (${ground} sol, ${carpet} tapis, ${air} air)`);
console.log(`palette (${kinds.length}) : ${kinds.join(', ')}`);
console.log(problems.length ? `RELECTURE KO: ${problems.join(' | ')}` : 'relu depuis le disque: conforme, et aucun bloc enracinable');
console.log('écrit:', path.relative(process.cwd(), OUT));
