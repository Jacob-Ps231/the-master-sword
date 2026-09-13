// Builds the structures the Master Sword generates in, one .nbt per variant.
//
// Written as code rather than saved from a structure block so they stay
// reproducible and reviewable in diffs. Reopening one in game at a structure
// block and saving over it still works -- that is what SPEC 4.2 asks for.
//
// Adding a variant means dropping a module in variants/ and listing it here.
// Everything else -- the block canvas, the deterministic draw, and above all
// the self-checks -- lives in builder.js and runs on EVERY file produced.
//
//   node tools/structure/build_structure.js
const path = require('path');
const { build } = require('./builder.js');

const OUT_DIR = path.join(__dirname, '../../src/main/resources/data/mastersword/structure');

const VARIANTS = [
  require('./variants/clearing.js'),
];

let sound = true;
for (const variant of VARIANTS) {
  if (!build(variant, OUT_DIR)) sound = false;
}

console.log(sound
  ? `\n${VARIANTS.length} variante(s) écrite(s), toutes conformes.`
  : '\nAu moins une variante est fautive : voir les lignes RELECTURE KO ci-dessus.');
process.exitCode = sound ? 0 : 1;
