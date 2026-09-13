// Captures a shrine Jérôme has rebuilt by hand in game, so the variant can be
// regenerated from what he actually built.
//
//   node tools/structure/capture.js waterfall
//
// Why this exists. The water is the reason. A .nbt's water never flows -- the
// jigsaw places blocks without neighbour updates and schedules no fluid tick --
// so until now the variants only ever wrote the two states I could prove stable
// on paper: sources, and full falling columns. That rules out the one thing that
// makes a cascade look natural, which is a pool **overflowing**: the water
// spreading over its lip in the horizontal levels 1 to 7 before it drops.
//
// Those levels are perfectly stable when they are the equilibrium. I could not
// compute that equilibrium by hand. But the game computes it, live, the moment
// Jérôme builds the thing -- so reading his world back gives the exact states,
// already at rest, for free. The constraint turns inside out: instead of him
// accepting what I can freeze, he builds what he wants and I copy it.
//
// The snapshot lands in captured/<id>.json, as plain text, so it stays diffable
// and reviewable like every other file the tools produce.
const fs = require('fs');
const path = require('path');
const { readBox, findBlockEntities } = require('./anvil.js');

const REGION = path.join(__dirname, '../../run/saves/New World/dimensions/minecraft/overworld/region');
const OUT_DIR = path.join(__dirname, 'captured');

// What not to copy, and why.
//
// Ores: the stone matrix under the shrine, which every world regenerates on its
// own -- baking one world's coal into the template would be absurd.
const ORES = /_ore$|^minecraft:(ancient_debris|glowing_obsidian)$/;
// A trail ruins was generating against this shrine, and its palette pokes into
// the box along one edge. Nothing to do with the decor, and copying it would
// stamp somebody else's structure into ours. SPEC 4.1 already records that our
// exclusion_zone only holds for the woodland mansion.
const FOREIGN = new Set([
  'minecraft:white_terracotta', 'minecraft:cyan_terracotta',
  'minecraft:light_gray_terracotta', 'minecraft:light_gray_glazed_terracotta',
  'minecraft:blue_terracotta', 'minecraft:mud_bricks', 'minecraft:mud_brick_slab',
  'minecraft:packed_mud', 'minecraft:suspicious_gravel',
]);

// The surrounding forest. The capture box is square and the trees are not, so
// copying them bakes a clipped canopy into every world the shrine generates in --
// half a trunk here, a raft of leaves floating there. The forest grows its own
// back: ShrineGuard only clears five blocks around the sword.
const TREES = /_log$|_leaves$|_wood$/;

/** "minecraft:water[level=3]" -- one line per block, so a diff reads. */
function encodeState(state) {
  const props = state.Properties;
  if (!props || !Object.keys(props).length) return state.Name;
  const pairs = Object.keys(props).sort().map((k) => `${k}=${props[k]}`);
  return `${state.Name}[${pairs.join(',')}]`;
}

const snapshotHeight = (below, above) => below + above + 1;

function capture(id, radius = 10, below = 3, above = 10) {
  const pedestals = findBlockEntities(REGION, 'mastersword:master_sword_pedestal');
  if (pedestals.length !== 1) {
    throw new Error(`${pedestals.length} socle(s) dans le monde, il en faut exactement un`);
  }
  const p = pedestals[0];
  const min = { x: p.x - radius, y: p.y - below, z: p.z - radius };
  const max = { x: p.x + radius, y: p.y + above, z: p.z + radius };
  const world = readBox(REGION, min, max);

  const blocks = {};
  let dropped = 0, ores = 0, trees = 0;
  for (const [key, state] of world) {
    const [x, y, z] = key.split(',').map(Number);
    if (FOREIGN.has(state.Name)) { dropped++; continue; }
    if (TREES.test(state.Name)) { trees++; continue; }
    const name = ORES.test(state.Name) ? 'minecraft:stone' : state.Name;
    if (name !== state.Name) ores++;
    blocks[`${x - min.x},${y - min.y},${z - min.z}`] = encodeState({ ...state, Name: name });
  }

  // The sword is drawn in the cell above the stone, and anything standing over
  // that column buries it. The capture picked up a cobblestone two blocks up.
  const ped = { x: p.x - min.x, y: p.y - min.y, z: p.z - min.z };
  let overhead = 0;
  for (let y = ped.y + 1; y < snapshotHeight(below, above); y++) {
    if (delete blocks[`${ped.x},${y},${ped.z}`]) overhead++;
  }

  // Whatever the two filters above left hanging. A block with no listed
  // neighbour at all is leaf litter that was sitting on a canopy now gone, or
  // the last brick of the neighbouring ruin -- either way it would generate
  // floating in mid-air.
  let orphans = 0;
  for (const key of Object.keys(blocks)) {
    const [x, y, z] = key.split(',').map(Number);
    const touching = [[0, -1, 0], [0, 1, 0], [1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1]]
      .some(([dx, dy, dz]) => blocks[`${x + dx},${y + dy},${z + dz}`]);
    if (!touching && y > 0) { delete blocks[key]; orphans++; }
  }

  const snapshot = {
    id,
    note: 'Capturé depuis le monde de test ; voir capture.js pour ce qui est écarté.',
    size: [radius * 2 + 1, below + above + 1, radius * 2 + 1],
    pedestal: ped,
    blocks,
  };

  fs.mkdirSync(OUT_DIR, { recursive: true });
  const out = path.join(OUT_DIR, `${id}.json`);
  // One block per line: a diff then shows what moved, not one giant line.
  const lines = Object.keys(blocks).sort().map((k) => `    ${JSON.stringify(k)}: ${JSON.stringify(blocks[k])}`);
  fs.writeFileSync(out, `{\n  "id": ${JSON.stringify(id)},\n  "note": ${JSON.stringify(snapshot.note)},\n`
    + `  "size": ${JSON.stringify(snapshot.size)},\n  "pedestal": ${JSON.stringify(snapshot.pedestal)},\n`
    + `  "blocks": {\n${lines.join(',\n')}\n  }\n}\n`);

  console.log(`socle en jeu : ${p.x}, ${p.y}, ${p.z}`);
  console.log(`emprise      : ${snapshot.size.join('x')}, socle en ${Object.values(snapshot.pedestal).join(',')}`);
  console.log(`capturé      : ${Object.keys(blocks).length} blocs`);
  console.log(`écarté       : ${trees} blocs d'arbres voisins, ${dropped} d'une structure voisine, ${ores} minerais ramenés à la pierre`);
  console.log(`nettoyé      : ${overhead} bloc(s) au-dessus du socle, ${orphans} bloc(s) restés en l'air`);
  console.log(`écrit        : ${path.relative(process.cwd(), out)}`);
  return snapshot;
}

if (require.main === module) {
  capture(process.argv[2] || 'waterfall');
}

module.exports = { capture, encodeState };
