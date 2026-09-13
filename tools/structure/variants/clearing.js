// Variant 1 — Clairière forestière.
//
// A low stone terrace with the sword on top, broken arches and stumps around
// it, roots creeping in from the edge, and the forest floor left to be the
// forest floor.
//
// Two things changed at step 13, and both come from outside this file:
//
// 1. **The ground is no longer ours.** ShrineGuard now keeps dark oaks out of
//    the shrine, so there is nothing left to defend against and no reason to
//    pave anything. Every cell we do not list is a cell the game leaves alone --
//    grass, dirt, leaf litter, the lot -- so the shrine is *built on* the forest
//    floor instead of replacing a square of it. That single change is what cures
//    "it looks too square": there is no longer an edge to see.
// 2. **Real plants.** Grass, ferns and flowers need #supports_vegetation under
//    them, which is exactly what the untouched forest floor provides.
//
// The stone stays for what stone is for: the terrace, the arches, the stumps.
const { b, i, s: str, comp } = require('../builder.js');

const W = 17, H = 13, D = 17;
const CX = 8, CZ = 8;          // pedestal column

const APRON = 4.4;             // the terrace foot, y = 0
const STEP = 3.8;              // half-slab ring, y = 1
const UPPER = 2.8;             // the top of the mound, y = 1
const MEADOW = 7.0;            // how far the planted decor reaches

const dist = (x, z) => Math.hypot(x - CX, z - CZ);

// --- materials -------------------------------------------------------------
const TERRACE = [
  ['minecraft:mossy_stone_bricks', 6],
  ['minecraft:mossy_cobblestone', 4],
  ['minecraft:stone_bricks', 3],
  ['minecraft:cracked_stone_bricks', 2],
  ['minecraft:chiseled_stone_bricks', 1],
];
const APRON_STONE = [
  ['minecraft:mossy_cobblestone', 6],
  ['minecraft:mossy_stone_bricks', 3],
  ['minecraft:cobblestone', 3],
  ['minecraft:cracked_stone_bricks', 2],
  ['minecraft:andesite', 1],
];
const STEP_RIM = [
  ['minecraft:mossy_stone_brick_slab', 5],
  ['minecraft:stone_brick_slab', 2],
];
const STUB = [
  ['minecraft:mossy_stone_bricks', 5],
  ['minecraft:cracked_stone_bricks', 3],
  ['minecraft:mossy_cobblestone', 3],
  ['minecraft:stone_bricks', 2],
  ['minecraft:chiseled_stone_bricks', 1],
];
// Everything here roots in the forest floor we did not touch.
const PLANTS = [
  ['minecraft:short_grass', 10],
  ['minecraft:fern', 7],
  ['minecraft:bush', 3],
  ['minecraft:lily_of_the_valley', 2],
  ['minecraft:azure_bluet', 1],
  ['minecraft:cornflower', 1],
  ['minecraft:firefly_bush', 1],
];
const TALL_PLANTS = [['minecraft:large_fern', 3], ['minecraft:tall_grass', 2]];

const LEAVES = { persistent: 'true', distance: '7', waterlogged: 'false' };

// --- the ruin, laid out by hand --------------------------------------------
// Doorways left standing, three blocks tall so they top out level with the
// sword rather than over it.
const ARCHES = [
  { jambs: [[5, 2], [8, 2]], lintel: [[6, 2], [7, 2]] },
  { jambs: [[13, 6], [13, 9]], lintel: [[13, 7], [13, 8]] },
];
// Leftover masonry standing on the forest floor: [x, z, height].
const STUMPS = [
  [2, 5, 2], [2, 6, 1], [2, 7, 2],       // a broken stretch of wall
  [11, 2, 1], [3, 10, 1], [14, 9, 2], [9, 14, 1], [5, 13, 2], [10, 14, 2],
];
// Foliage capping a stump, [x, z, y] -- y last, same reading order as STUMPS.
const LEAF_CAPS = [[11, 2, 1], [14, 9, 2], [5, 13, 2]];
// Roots crawling in from the corners of the meadow.
// Every cell of `high` must also be in `low`, or the upper root floats a
// block above the turf with nothing under it.
const ROOT_CLUSTERS = [
  { low: [[3, 3], [4, 3], [3, 4], [4, 4], [5, 3]], high: [[4, 3], [4, 4], [5, 3]] },
  { low: [[12, 12], [13, 12], [12, 13], [13, 13], [11, 12]], high: [[12, 12], [13, 12], [11, 12]] },
  { low: [[12, 4], [13, 4], [13, 5]], high: [[13, 4]] },
  { low: [[3, 12], [4, 12], [4, 13]], high: [[4, 12]] },
];
const VINES = [[5, 1, 1, 'south'], [8, 1, 1, 'south'], [14, 1, 6, 'west'], [14, 1, 9, 'west']];
// Huge mushrooms: full blocks with no survival rule, unlike a small one on open
// ground which would want a light level the daylight never leaves it.
const BIG_MUSHROOMS = [[1, 8], [15, 7], [8, 15], [7, 1], [1, 12]];
// Our own dark oaks, at the rim: the guard clears the shrine of trees, which
// the decor needed, but it also left the clearing visible from a long way off.
// [x, z, height] -- the trunk is 2x2 and grows towards +x/+z.
const OAKS = [[1, 2, 8], [13, 1, 7], [0, 12, 7], [14, 13, 8], [6, 15, 7]];

// Moss and litter only sit right on a full block, never on a slab or a plant.
const COVERABLE = new Set([
  'minecraft:stone', 'minecraft:andesite', 'minecraft:gravel',
  'minecraft:cobblestone', 'minecraft:mossy_cobblestone',
  'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks',
  'minecraft:cracked_stone_bricks', 'minecraft:chiseled_stone_bricks',
  'minecraft:mangrove_roots',
]);

function build(s) {
  // --- 1. the shapes, drawn once ------------------------------------------
  // Precomputed rather than recomputed on demand: every call would otherwise
  // pull from the same random stream and the file would stop being reproducible.
  const noisyDisc = (radius, wobble) => {
    const set = new Set();
    for (let z = 0; z < D; z++) {
      for (let x = 0; x < W; x++) {
        if (dist(x, z) <= radius + (s.rand() - 0.5) * wobble) set.add(`${x},${z}`);
      }
    }
    return set;
  };

  const apron = noisyDisc(APRON, 1.4);
  const step = noisyDisc(STEP, 1.0);
  const upper = noisyDisc(UPPER, 0.8);
  const meadow = noisyDisc(MEADOW, 2.4);
  // The terrace has to be a terrace: no hole in the middle, no island outside.
  for (const key of upper) { step.add(key); apron.add(key); }
  for (const key of step) apron.add(key);

  // --- 2. the terrace ------------------------------------------------------
  // y=0 IS the surface block, so the apron replaces the turf and lies flush with
  // the forest floor; the step is half a block up, the top a block above that,
  // and the sword ends up **two** blocks above the ground -- a mound, not a
  // monument, which is what the in-game trial of 12/09 settled on.
  for (const key of apron) {
    const [x, z] = key.split(',').map(Number);
    s.put(x, 0, z, s.pick(APRON_STONE));
  }
  for (const key of step) {
    const [x, z] = key.split(',').map(Number);
    if (!upper.has(key)) s.put(x, 1, z, s.pick(STEP_RIM));
  }
  for (const key of upper) {
    const [x, z] = key.split(',').map(Number);
    s.put(x, 1, z, s.pick(TERRACE));
  }

  // The pedestal carries its block entity straight in the file -- the same way
  // vanilla chests carry their loot table. This is what puts the sword in the
  // stone at world generation rather than needing code to fill it afterwards.
  //
  // shrine is what tells the fog this pedestal is the real one (SPEC 5). Only a
  // generated pedestal carries it, so a pedestal the player places -- including
  // one they plant the Master Sword back into -- never raises a fog.
  s.put(CX, 2, CZ, 'mastersword:master_sword_pedestal', { facing: 'north' }, comp({
    id: str('mastersword:master_sword_pedestal'),
    sword: comp({ id: str('mastersword:master_sword'), count: i(1) }),
    fog_consumed: b(0),
    shrine: b(1),
  }));

  // --- 3. the ruin, standing on the floor ---------------------------------
  for (const arch of ARCHES) {
    for (const [x, z] of arch.jambs) for (let y = 0; y <= 2; y++) s.put(x, y, z, s.pick(STUB));
    for (const [x, z] of arch.lintel) s.put(x, 2, z, s.pick(STUB));
  }
  for (const [x, z, height] of STUMPS) {
    for (let y = 0; y < height; y++) s.put(x, y, z, s.pick(STUB));
  }
  for (const [x, z, y] of LEAF_CAPS) s.put(x, y, z, 'minecraft:dark_oak_leaves', LEAVES);
  for (const cluster of ROOT_CLUSTERS) {
    for (const [x, z] of cluster.low) s.put(x, 0, z, 'minecraft:mangrove_roots');
    for (const [x, z] of cluster.high) s.put(x, 1, z, 'minecraft:mangrove_roots');
  }
  for (const [x, y, z, face] of VINES) s.putVine(x, y, z, face);

  let mushrooms = 0;
  for (const [x, z] of BIG_MUSHROOMS) {
    if (mushrooms >= 3 || !s.isEmpty(x, 1, z) || !s.isEmpty(x, 2, z)) continue;
    s.put(x, 1, z, 'minecraft:mushroom_stem');
    // down: false leaves the pores showing under the cap, as on a vanilla one.
    s.put(x, 2, z, 'minecraft:red_mushroom_block', {
      up: 'true', down: 'false', north: 'true', south: 'true', east: 'true', west: 'true',
    });
    mushrooms++;
  }

  // --- 4. the meadow -------------------------------------------------------
  // ON the untouched ground, at y=1, never at y=0: JigsawPlacement anchors the
  // template so that **y=0 is the surface block itself**, and writing a plant
  // there replaces the turf instead of standing on it -- which is how the first
  // trial ended up with a pit under every flower. Grass, ferns and flowers all
  // want #supports_vegetation below them, and the forest floor at y=0 is it.
  let bushes = 0;
  for (const key of meadow) {
    const [x, z] = key.split(',').map(Number);
    if (!s.isEmpty(x, 0, z) || !s.isEmpty(x, 1, z)) continue;   // the terrace and the ruin
    const roll = s.rand();
    if (roll < 0.30) {
      s.put(x, 1, z, s.pick(PLANTS));
    } else if (roll < 0.36 && s.isEmpty(x, 2, z)) {
      const tall = s.pick(TALL_PLANTS);
      s.put(x, 1, z, tall, { half: 'lower' });
      s.put(x, 2, z, tall, { half: 'upper' });
    } else if (roll < 0.42 && bushes < 6) {
      s.put(x, 1, z, 'minecraft:dark_oak_leaves', LEAVES);
      if (s.chance(0.4) && s.isEmpty(x, 2, z)) s.put(x, 2, z, 'minecraft:dark_oak_leaves', LEAVES);
      bushes++;
    } else if (roll < 0.58) {
      s.put(x, 1, z, 'minecraft:leaf_litter', {
        facing: s.pick([['north', 1], ['east', 1], ['south', 1], ['west', 1]]),
        segment_amount: String(1 + Math.floor(s.rand() * 4)),
      });
    } else if (roll < 0.66) {
      s.put(x, 1, z, 'minecraft:moss_carpet');
    }
  }

  // --- 4b. our own canopy --------------------------------------------------
  for (const [x, z, height] of OAKS) s.plantDarkOak(x, z, 1, height);

  // --- 5. moss and litter over the stone -----------------------------------
  for (let z = 0; z < D; z++) {
    for (let x = 0; x < W; x++) {
      if (x === CX && z === CZ) continue;              // the sword's own column
      const top = s.topOf(x, z);
      if (top < 0 || top + 1 >= H) continue;
      if (!s.isEmpty(x, top + 1, z)) continue;
      if (!COVERABLE.has(s.nameAt(x, top, z))) continue;
      const roll = s.rand();
      if (roll < 0.42) {
        s.put(x, top + 1, z, 'minecraft:moss_carpet');
      } else if (roll < 0.55) {
        s.put(x, top + 1, z, 'minecraft:leaf_litter', {
          facing: s.pick([['north', 1], ['east', 1], ['south', 1], ['west', 1]]),
          segment_amount: String(1 + Math.floor(s.rand() * 4)),
        });
      }
    }
  }
}

module.exports = {
  id: 'clearing',
  title: 'Clairière forestière',
  width: W, height: H, depth: D,
  pedestal: { x: CX, y: 2, z: CZ, facing: 'north' },
  seed: 0xc1a1e2,
  build,
};
