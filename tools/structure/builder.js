// Shared machinery for the mod's structure .nbt files: a tiny block canvas, a
// deterministic random source, and the self-checks that every variant has to
// pass before its file is allowed on disk.
//
// Split out of build_structure.js when a second variant appeared. SPEC 4.2 had
// already flagged the reason: the checks below are the only thing standing
// between a pretty ruin and a silently broken one, and they have to run on
// EVERY file produced, not once for the whole run.
//
// --- why a tree cannot grow in the ruin, verified rather than remembered ----
//
// Trees in a dark forest come from configured_feature/dark_forest_vegetation, a
// random_selector placed SIXTEEN times per chunk on the OCEAN_FLOOR heightmap.
// Their only filter is block_predicate_filter/would_survive on a dark oak
// sapling, which resolves to VegetationBlock.canSurvive: the block BELOW the
// position must be in #minecraft:supports_vegetation.
//
// The selector draws a tree **92% of the time**, not the two thirds this comment
// claimed until 13/09: dark_oak_leaf_litter is 2/3 of it, but birch_leaf_litter,
// fancy_oak_leaf_litter and the default oak_leaf_litter are all
// "type": "minecraft:tree" as well, and only the two huge mushrooms (7.4%) and
// the two fallen trunks (0.4%) are not.
//
// So a single exposed cell of soil is a **~5.8% chance of a tree**
// (16 x 0.92 / 256 cells), not 4%. Twenty of them is better than one tree per
// world -- and DarkOakTrunkPlacer.placeBelowTrunkBlock then turns four cells of
// whatever is under the trunk into dirt, while the trunk leans up to two blocks
// sideways as it rises.
//
// The way out is the heightmap: it reports the first cell above the topmost
// motion-blocking block. Put anything solid above a cell of soil and the game
// never tests the soil at all -- it tests the block on top, which supports
// nothing. Hence COVERED soil is free of charge, and exposed soil is forbidden.
// Both are checked below.
const fs = require('fs');
const path = require('path');
const { encode, decode, plain, b, i, s, list, comp } = require('./nbt.js');

const DATA_VERSION = 4903; // 26.2, from the client's version.json world_version

// #minecraft:supports_vegetation, resolved from minecraft-common.jar 26.2:
// #dirt + #mud + #moss_blocks + #grass_blocks + farmland. These eleven are the
// entire list of blocks a dark oak can root on -- an earlier version of this
// script used a hand-written list that was close but written from memory.
const SUPPORTS_VEGETATION = new Set([
  'minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt',
  'minecraft:mud', 'minecraft:muddy_mangrove_roots',
  'minecraft:moss_block', 'minecraft:pale_moss_block',
  'minecraft:grass_block', 'minecraft:podzol', 'minecraft:mycelium',
  'minecraft:farmland',
]);

// Blocks that hide what is underneath them from the heightmap. Deliberately an
// allowlist: a block wrongly believed to block motion would let a tree through,
// so anything unclassified makes the check fail loudly instead of passing.
//
// The rule is BlockBehaviour.BlockStateBase.blocksMotion() -- everything solid
// except cobweb and bamboo sapling -- and "solid" is calculateSolid(): a
// collision box averaging 0.729 across its three sides, or a full block tall. A
// bottom slab qualifies; a carpet, a plant or a lily pad does not.
const BLOCKS_MOTION = new Set([
  'minecraft:stone', 'minecraft:andesite', 'minecraft:gravel',
  'minecraft:cobblestone', 'minecraft:mossy_cobblestone',
  'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks',
  'minecraft:cracked_stone_bricks', 'minecraft:chiseled_stone_bricks',
  'minecraft:stone_brick_slab', 'minecraft:mossy_stone_brick_slab',
  'minecraft:cobblestone_slab', 'minecraft:mossy_cobblestone_slab',
  'minecraft:stone_brick_stairs', 'minecraft:mossy_stone_brick_stairs',
  'minecraft:stone_brick_wall', 'minecraft:mossy_stone_brick_wall',
  'minecraft:mangrove_roots', 'minecraft:dark_oak_leaves',
  'minecraft:dark_oak_log', 'minecraft:red_mushroom_block',
  'minecraft:mushroom_stem',
  // Two thick boxes rather than a full cube, but solid either way.
  'mastersword:master_sword_pedestal',
  // Natural floors, for a pool bottom seen through the water.
  'minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt',
  'minecraft:podzol', 'minecraft:moss_block', 'minecraft:clay',
  'minecraft:grass_block', 'minecraft:mycelium', 'minecraft:sand',
]);

// Blocks that are known NOT to block motion. Together with the set above this
// covers everything we place; a block in neither is a block nobody has thought
// about, and the check says so.
const FREE_OF_MOTION = new Set([
  'minecraft:air', 'minecraft:moss_carpet', 'minecraft:leaf_litter',
  'minecraft:short_grass', 'minecraft:fern', 'minecraft:bush',
  'minecraft:firefly_bush', 'minecraft:red_mushroom', 'minecraft:brown_mushroom',
  'minecraft:vine', 'minecraft:glow_lichen', 'minecraft:water',
  'minecraft:lily_pad', 'minecraft:large_fern', 'minecraft:tall_grass',
  'minecraft:lily_of_the_valley', 'minecraft:azure_bluet',
  'minecraft:cornflower', 'minecraft:blue_orchid', 'minecraft:poppy',
  'minecraft:white_tulip', 'minecraft:wildflowers', 'minecraft:flowering_azalea',
  'minecraft:seagrass', 'minecraft:pink_petals',
]);

const WATER = 'minecraft:water';

// Holding water back is a stricter test than blocking motion. FlowingFluid
// .canPassThroughWall l. 208 is `if (targetShape == Shapes.block()) return
// false;` -- an identity check against the full cube -- so a slab, a stair or a
// wall lets the fluid straight through even though all three block motion. And
// a waterloggable full cube (mangrove roots, leaves) does not hold it either: it
// just fills up. Hence a separate, smaller set for anything that walls water in.
const FULL_CUBE = new Set([
  'minecraft:stone', 'minecraft:andesite', 'minecraft:gravel',
  'minecraft:cobblestone', 'minecraft:mossy_cobblestone',
  'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks',
  'minecraft:cracked_stone_bricks', 'minecraft:chiseled_stone_bricks',
  'minecraft:dark_oak_log', 'minecraft:red_mushroom_block', 'minecraft:mushroom_stem',
  'minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt',
  'minecraft:podzol', 'minecraft:moss_block', 'minecraft:clay',
  'minecraft:grass_block', 'minecraft:mycelium', 'minecraft:sand',
  // Deliberately absent: dirt_path, whose collision box is 15/16 tall and so
  // fails the identity test against Shapes.block() -- water runs straight
  // through a path block, however solid it looks.
]);

/** A block canvas with a deterministic random source. */
class Structure {
  constructor({ width, height, depth, seed }) {
    this.w = width; this.h = height; this.d = depth;
    this.cells = new Map();
    this.seed = seed >>> 0;
  }

  // Same LCG as the single-variant script: the file stays reproducible, so a
  // diff on the .nbt means someone changed the design, never the weather.
  rand() {
    this.seed = (this.seed * 1664525 + 1013904223) >>> 0;
    return this.seed / 0x100000000;
  }

  chance(p) { return this.rand() < p; }

  /** Weighted draw from [[name, weight], ...]. */
  pick(table) {
    const total = table.reduce((a, e) => a + e[1], 0);
    let r = this.rand() * total;
    for (const [name, weight] of table) { if ((r -= weight) < 0) return name; }
    return table[table.length - 1][0];
  }

  inBounds(x, y, z) {
    return x >= 0 && x < this.w && y >= 0 && y < this.h && z >= 0 && z < this.d;
  }

  key(x, y, z) { return `${x},${y},${z}`; }

  /** Places a block, replacing whatever was there. */
  put(x, y, z, name, props, nbt) {
    if (!this.inBounds(x, y, z)) throw new Error(`hors emprise: ${x},${y},${z} (${name})`);
    this.cells.set(this.key(x, y, z), { x, y, z, name, props, nbt });
  }

  get(x, y, z) { return this.cells.get(this.key(x, y, z)); }

  /** True when nothing has been placed yet, air included. */
  isEmpty(x, y, z) { return !this.cells.has(this.key(x, y, z)); }

  /** Name of the block at a cell, 'minecraft:air' for an untouched one. */
  nameAt(x, y, z) { return this.get(x, y, z)?.name ?? 'minecraft:air'; }

  /** Highest y carrying something other than air, -1 if the column is bare. */
  topOf(x, z) {
    for (let y = this.h - 1; y >= 0; y--) {
      const at = this.get(x, y, z);
      if (at && at.name !== 'minecraft:air') return y;
    }
    return -1;
  }

  /**
   * A vine or a glow lichen only survives while one of its declared faces has
   * something to cling to, so the support is checked here rather than discovered
   * in game. `face` is the direction of the block it hangs on.
   */
  putMultiface(name, x, y, z, face) {
    // VineBlock has no `down`: PROPERTY_BY_DIRECTION filters Direction.DOWN out,
    // so vine[down=true] is not a state and the palette entry would be rejected.
    if (name === 'minecraft:vine' && face === 'down') {
      throw new Error(`liane par le bas en ${x},${y},${z} : vine n'a pas de propriété down`);
    }
    const offsets = { north: [0, 0, -1], south: [0, 0, 1], west: [-1, 0, 0], east: [1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };
    const [dx, dy, dz] = offsets[face];
    const support = this.nameAt(x + dx, y + dy, z + dz);
    if (!BLOCKS_MOTION.has(support)) {
      throw new Error(`${name} sans appui en ${x},${y},${z} vers ${face} (${support})`);
    }
    this.put(x, y, z, name, { [face]: 'true' });
  }

  putVine(x, y, z, face) { this.putMultiface('minecraft:vine', x, y, z, face); }

  /**
   * A dark oak, built into the file rather than left to the world.
   *
   * ShrineGuard clears the shrine of trees, which is what the decor needed --
   * but it also left the clearing bare enough to spot from a long way off, and
   * the sanctuary is supposed to be hidden. Growing a few of our own at the rim
   * gives the canopy back without giving the ground back.
   *
   * Shaped after vanilla's: a 2x2 trunk (DarkOakTrunkPlacer places the origin,
   * east, south and south-east) under a wide, flat crown.
   */
  plantDarkOak(x, z, base, height) {
    const crownY = base + height - 1;
    if (!this.inBounds(x + 1, crownY + 2, z + 1)) return false;
    for (const [tx, tz] of [[x, z], [x + 1, z], [x, z + 1], [x + 1, z + 1]]) {
      for (let y = base; y <= crownY; y++) this.put(tx, y, tz, 'minecraft:dark_oak_log', { axis: 'y' });
    }

    const leaves = { persistent: 'true', distance: '7', waterlogged: 'false' };
    const centreX = x + 0.5, centreZ = z + 0.5;
    for (const [dy, radius] of [[0, 3.2], [1, 3.2], [2, 2.1]]) {
      const y = crownY + dy;
      for (let lz = Math.floor(centreZ - radius); lz <= centreZ + radius; lz++) {
        for (let lx = Math.floor(centreX - radius); lx <= centreX + radius; lx++) {
          if (!this.inBounds(lx, y, lz)) continue;
          if (Math.hypot(lx - centreX, lz - centreZ) > radius - this.rand() * 0.9) continue;
          if (!this.isEmpty(lx, y, lz)) continue;
          this.put(lx, y, lz, 'minecraft:dark_oak_leaves', leaves);
        }
      }
    }
    return true;
  }

  /**
   * Clears the sky over the columns the variant actually built in, and lists
   * nothing anywhere else.
   *
   * A .nbt's block list is sparse: placeInWorld iterates the list and never
   * requires the volume to be covered, so a cell nobody listed is a cell the
   * game does not touch. Vanilla ships files like that -- taiga_decoration_1
   * lists 22 of its 36 cells.
   *
   * That is the whole cure for "it looks too square". Filling the box with air
   * stamped a rectangle of cleared ground into the forest whatever shape the
   * decor had; now the footprint is whatever silhouette the variant drew, and
   * the trees, the slope and the leaf litter around it stay exactly as they
   * were.
   *
   * Within an occupied column every empty cell is still filled, openings under
   * an arch included -- otherwise the terrain would show through the decor.
   */
  clearSkyOverBuiltColumns() {
    const built = new Set();
    for (const cell of this.cells.values()) built.add(`${cell.x},${cell.z}`);
    for (const key of built) {
      const [x, z] = key.split(',').map(Number);
      // Never y=0. JigsawPlacement anchors the template with
      // `box.minY() + getGroundLevelDelta() == firstFreeHeight` and the delta is
      // 1, so **template y=0 is the surface block itself**, not the air above
      // it. Writing air there scoops the turf out of the forest floor and
      // leaves a one-block pit -- which is exactly what the first in-game trial
      // of step 13 showed, scattered all around the terrace.
      for (let y = 1; y < this.h; y++) {
        if (this.isEmpty(x, y, z)) this.put(x, y, z, 'minecraft:air');
      }
    }
  }

  /** Assembles the structure NBT root, building the palette as it goes. */
  toNbt() {
    const palette = [];
    const indexOf = (name, props) => {
      const key = name + '|' + JSON.stringify(props || {});
      let idx = palette.findIndex((p) => p.key === key);
      if (idx < 0) {
        const tag = { Name: s(name) };
        if (props) {
          tag.Properties = comp(Object.fromEntries(
            Object.entries(props).map(([k, v]) => [k, s(String(v))]),
          ));
        }
        palette.push({ key, tag });
        idx = palette.length - 1;
      }
      return idx;
    };

    const blocks = [];
    // Sorted so the file is byte-stable whatever order the variant built in.
    const sorted = [...this.cells.values()].sort((a, c) => a.y - c.y || a.z - c.z || a.x - c.x);
    for (const cell of sorted) {
      const entry = {
        pos: list('int', [i(cell.x), i(cell.y), i(cell.z)]),
        state: i(indexOf(cell.name, cell.props)),
      };
      if (cell.nbt) entry.nbt = cell.nbt;
      blocks.push(comp(entry));
    }

    return comp({
      size: list('int', [i(this.w), i(this.h), i(this.d)]),
      entities: list('end', []),
      blocks: list('compound', blocks),
      palette: list('compound', palette.map((p) => comp(p.tag))),
      DataVersion: i(DATA_VERSION),
    });
  }
}

/**
 * Reads the file back from disk and checks everything a template_pool will
 * happily accept but a player would only discover weeks later, in game.
 * Returns the list of problems -- empty means the file is sound.
 */
function verify(buffer, spec) {
  const back = plain(decode(buffer));
  const problems = [];
  const nameOf = (block) => back.palette[block.state].Name;

  if (JSON.stringify(back.size) !== JSON.stringify([spec.width, spec.height, spec.depth])) {
    problems.push(`taille ${back.size.join('x')} au lieu de ${spec.width}x${spec.height}x${spec.depth}`);
  }
  if (back.DataVersion !== DATA_VERSION) problems.push(`DataVersion ${back.DataVersion}`);

  // --- the pedestal, and the three fields nothing else can supply -----------
  const pedestal = back.blocks.find((bl) => nameOf(bl).startsWith('mastersword:'));
  if (!pedestal) {
    problems.push('socle absent');
  } else {
    const want = [spec.pedestal.x, spec.pedestal.y, spec.pedestal.z];
    if (JSON.stringify(pedestal.pos) !== JSON.stringify(want)) {
      problems.push(`socle en ${pedestal.pos.join(',')} au lieu de ${want.join(',')}`);
    }
    if (back.palette[pedestal.state].Properties?.facing !== spec.pedestal.facing) {
      problems.push(`facing du socle: ${back.palette[pedestal.state].Properties?.facing}`);
    }
    if (pedestal.nbt?.sword?.id !== 'mastersword:master_sword') problems.push('épée absente du block entity');
    if (pedestal.nbt?.fog_consumed !== 0) problems.push('fog_consumed absent ou déjà consommé');
    // Without this byte the pedestal generates as an ordinary one and no fog
    // ever appears -- a failure invisible everywhere except in game.
    if (pedestal.nbt?.shrine !== 1) problems.push('drapeau shrine absent');
  }

  // --- everything that matters is inside the guarded disc -------------------
  // Trees are no longer kept out by the palette but by ShrineGuard, which vetoes
  // any tree whose origin falls in a disc of five blocks around the middle of the
  // structure. Deliberately far smaller than the footprint: sizing it to the box
  // left a ring of bare grass between the shrine and the forest. The outer band
  // of each variant is now meant to grow trees, so soil out there is counted
  // rather than refused; what must stay inside the disc is the sword itself.
  //
  // The radius is copied from ShrineGuard.RADIUS by hand -- nothing here reads
  // the Java, so moving one does NOT move the other. Kept in step manually.
  const centreX = (spec.width - 1) / 2;
  const centreZ = (spec.depth - 1) / 2;
  const radius = 5;                                          // ShrineGuard.RADIUS
  const guarded = (x, z) => (x - centreX) ** 2 + (z - centreZ) ** 2 <= radius ** 2;

  // A dark oak trunk is 2x2 and leans up to two blocks as it rises, so being
  // just inside the disc is not enough for the sword itself.
  if (!guarded(spec.pedestal.x, spec.pedestal.z)) {
    problems.push(`socle hors du disque gardé — un tronc pourrait pousser contre l'épée`);
  }

  const column = new Map();
  const solid = new Set();
  const cells = new Map();
  for (const bl of back.blocks) {
    const key = `${bl.pos[0]},${bl.pos[2]}`;
    if (!column.has(key)) column.set(key, []);
    column.get(key).push({ y: bl.pos[1], name: nameOf(bl) });
    cells.set(bl.pos.join(','), { name: nameOf(bl), props: back.palette[bl.state].Properties });
    if (nameOf(bl) !== 'minecraft:air') solid.add(bl.pos.join(','));
  }
  const nameAt = (x, y, z) => cells.get(`${x},${y},${z}`)?.name;

  // --- nothing floats alone in the air --------------------------------------
  // A lintel spans a gap and a vine hangs, so "must rest on something" would
  // cry wolf; but a block with air below AND air on all four sides is a typo in
  // a coordinate triple, and that is exactly how it looks in game -- once you
  // finally notice it, weeks later.
  for (const pos of solid) {
    const [x, y, z] = pos.split(',').map(Number);
    // y=0 rests on the terrain, which is below the template and therefore never
    // in this set: a block down there cannot float, whatever its neighbours are.
    // Nor can a block at y=1 whose column lists nothing at y=0 -- the surface
    // block is right underneath it, we simply chose not to replace it. That is
    // the normal case for everything planted straight on the forest floor.
    if (y === 0) continue;
    if (y === 1 && !solid.has(`${x},0,${z}`) && !cells.has(`${x},0,${z}`)) continue;
    const touching = [[0, -1, 0], [1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, 1, 0]]
      .some(([dx, dy, dz]) => solid.has(`${x + dx},${y + dy},${z + dz}`));
    if (!touching) problems.push(`bloc isolé en plein air en ${x},${y},${z} (${back.palette[back.blocks.find((bl) => bl.pos.join(',') === pos).state].Name})`);
  }

  // --- the sword needs the cell above the pedestal ---------------------------
  // The blade is drawn by the block entity in the space above the stone; a block
  // left there buries it.
  for (let y = spec.pedestal.y + 1; y < spec.height; y++) {
    if (solid.has(`${spec.pedestal.x},${y},${spec.pedestal.z}`)) {
      problems.push(`colonne du socle obstruée en y=${y}`);
    }
  }

  // --- soil is allowed now, but only where the guard reaches ----------------
  // Until step 13 an exposed block of #supports_vegetation was a hard error,
  // because the palette was the only thing standing between the shrine and a
  // dark oak. ShrineGuard holds that line now, so soil is ordinary decor -- and
  // the check turns into the one question the guard cannot answer for itself:
  // is this cell actually inside the disc it protects?
  //
  // Cells that are sheltered anyway -- under a lintel, or under water -- are
  // still counted, because they are safe whatever happens to the guard. The
  // count is printed rather than enforced: it is a useful measure of how much
  // of the decor would survive the mixin failing to apply.
  let soil = 0, sheltered = 0, exposed = 0;
  for (const [key, cells] of column) {
    cells.sort((a, c) => a.y - c.y);
    const [cx, cz] = key.split(',').map(Number);
    for (const cell of cells) {
      if (!SUPPORTS_VEGETATION.has(cell.name)) continue;
      soil++;
      const above = cells.filter((other) => other.y > cell.y);
      // Water shelters by a different route from a solid block: the OUTER placed
      // feature -- dark_forest_vegetation, the one drawn 16 times a chunk, not
      // dark_oak_leaf_litter which only carries the sapling predicate -- runs a
      // surface_water_depth_filter at max_water_depth 0, which throws out any
      // column where WORLD_SURFACE (water included) sits above OCEAN_FLOOR
      // (water excluded). A wet cell is never even tested for a tree.
      if (above.some((other) => BLOCKS_MOTION.has(other.name) || other.name === WATER)) sheltered++;
      else if (!guarded(cx, cz)) exposed++;
    }
  }

  // --- small mushrooms need more than a floor -------------------------------
  // MushroomBlock.canSurvive wants #overrides_mushroom_light_requirement below
  // (mycelium, podzol, the two nyliums) or a raw brightness under 13. Daylight
  // in a clearing is 15, so a mushroom on paving survives generation and then
  // disappears the first time a neighbouring block changes -- decor that empties
  // itself, weeks later, for no visible reason. The huge mushroom blocks have no
  // survival rule at all and are the way to put one out in the open.
  const LIGHT_PROOF = new Set(['minecraft:podzol', 'minecraft:mycelium']);
  for (const [key, cells] of column) {
    for (const cell of cells) {
      if (cell.name !== 'minecraft:red_mushroom' && cell.name !== 'minecraft:brown_mushroom') continue;
      const below = cells.find((other) => other.y === cell.y - 1);
      if (!below || !LIGHT_PROOF.has(below.name)) {
        problems.push(`champignon en ${key} y=${cell.y} sans podzol dessous — il sautera au premier voisin modifié`);
      }
    }
  }

  // --- the water has to be written already at rest ---------------------------
  // The jigsaw places blocks with flags 18 (UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)
  // and ProtoChunk.setBlockState schedules no fluid tick, so the water in a .nbt
  // is frozen -- until a player disturbs a neighbouring block, which wakes every
  // source at once. The only safe file is one already holding the equilibrium
  // the game would compute, and these are the three rules that give it:
  //
  //  - FlowingFluid.spread l. 137: a SOURCE spreads sideways, so it is only safe
  //    when all four horizontal neighbours are already occupied. Vanilla is
  //    slightly kinder than that -- l. 123-133, a source that CAN flow down
  //    returns early and skips the sideways pass unless it has three or more
  //    source neighbours -- so this check is stricter than the game. Deliberately:
  //    the kind exception needs a source under a source, and the day a variant
  //    widens a cleft to three or builds a 2x3 basin, the strict rule is the one
  //    that still holds;
  //  - the same line for a non-source: it spreads sideways only when
  //    !isWaterHole, and isWaterHole (l. 314) is true as soon as the cell below
  //    already holds the same fluid -- a falling column over water never widens;
  //  - LiquidBlock's stateCache: level 0 is the source, 1..7 are the horizontal
  //    flowing states, 8 is falling-and-full. We write 0 and 8 only; anything in
  //    between is a state the game maintains, not one we can freeze.
  //
  // A CAPTURED variant is exempt from the three rules below, and only from
  // those. They exist because I could not compute a fluid's equilibrium by hand,
  // so the file was restricted to the two states I could prove. A capture has no
  // such problem: the game itself ticked that water to rest before it was read,
  // so its horizontal flowing levels ARE the equilibrium, and forbidding them
  // would be forbidding the only thing that makes a cascade look like water.
  // Everything else -- the pedestal, the flag, the floating blocks, the guarded
  // disc -- is checked exactly the same way.
  let sources = 0, falling = 0, flowing = 0;
  for (const [pos, cell] of cells) {
    if (cell.name !== WATER) continue;
    const [x, y, z] = pos.split(',').map(Number);
    const level = Number(cell.props?.level ?? 0);

    if (level > 0 && level < 8) {
      flowing++;
      if (!spec.captured) {
        problems.push(`eau en écoulement horizontal en ${pos} (level=${level}) — état instable, n'écrire que 0 ou 8`);
      }
      continue;
    }
    if (x === 0 || x === spec.width - 1 || z === 0 || z === spec.depth - 1) {
      problems.push(`eau au bord de l'emprise en ${pos} — elle fuira dans le terrain`);
    }

    if (level === 0) {
      sources++;
      if (spec.captured) continue;
      for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const neighbour = nameAt(x + dx, y, z + dz);
        if (neighbour !== WATER && !FULL_CUBE.has(neighbour)) {
          problems.push(`source d'eau non murée en ${pos} vers ${dx},${dz} (${neighbour ?? 'hors emprise'}) — elle s'étalera au premier tick`);
        }
      }
      // spread() l. 123 tries DOWN before anything else, so the floor of the
      // basin matters as much as its walls.
      const below = nameAt(x, y - 1, z);
      if (below !== WATER && !FULL_CUBE.has(below)) {
        problems.push(`source d'eau sans fond en ${pos} (${below ?? 'hors emprise'}) — elle s'écoulera vers le bas`);
      }
    } else {
      falling++;
      if (spec.captured) continue;
      if (nameAt(x, y - 1, z) !== WATER) {
        problems.push(`chute en ${pos} sans eau en dessous — elle s'étalera sur les côtés`);
      }
      if (nameAt(x, y + 1, z) !== WATER) {
        problems.push(`chute en ${pos} non alimentée par le dessus — elle s'assèchera`);
      }
    }
  }

  // --- every block in the palette has been thought about ---------------------
  // BLOCKS_MOTION and FREE_OF_MOTION are what the soil and water checks reason
  // with. A block in neither is not wrong by itself, but it is a block nobody
  // classified, and it will be read as "does not shelter" and "does not wall
  // water" by default -- which is the safe side, but silently.
  const unclassified = back.palette.map((p) => p.Name)
    .filter((name) => !BLOCKS_MOTION.has(name) && !FREE_OF_MOTION.has(name));
  if (unclassified.length) {
    problems.push(`bloc non classé dans BLOCKS_MOTION ni FREE_OF_MOTION: ${[...new Set(unclassified)].join(', ')}`);
  }

  const kinds = [...new Set(back.palette.map((p) => p.Name.replace('minecraft:', '')))];
  return { problems, stats: { blocks: back.blocks.length, soil, sheltered, exposed, sources, falling, flowing, kinds } };
}

/** Builds one variant, writes it, reads it back, reports. Returns true if sound. */
function build(variant, outDir) {
  const structure = new Structure({
    width: variant.width, height: variant.height, depth: variant.depth, seed: variant.seed,
  });
  variant.build(structure);
  structure.clearSkyOverBuiltColumns();

  const buffer = encode(structure.toNbt());
  const out = path.join(outDir, `${variant.id}.nbt`);
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(out, buffer);

  const { problems, stats } = verify(fs.readFileSync(out), variant);
  console.log(`\n${variant.id} — ${variant.title}`);
  console.log(`  ${buffer.length} octets | ${stats.blocks} blocs | ${variant.width}x${variant.height}x${variant.depth}`);
  console.log(`  terre: ${stats.soil} cases, ${stats.sheltered} à l'abri, ${stats.exposed} hors du disque gardé`);
  if (stats.sources || stats.falling || stats.flowing) {
    console.log(`  eau: ${stats.sources} sources, ${stats.falling} de chute, ${stats.flowing} d'écoulement`
      + (variant.captured ? ' (capturée : équilibre calculé par le jeu)' : ''));
  }
  console.log(`  palette (${stats.kinds.length}) : ${stats.kinds.join(', ')}`);
  console.log(`  écrit: ${path.relative(process.cwd(), out)}`);
  if (problems.length) {
    for (const problem of problems) console.log(`  RELECTURE KO: ${problem}`);
  } else {
    console.log('  relu depuis le disque: conforme');
  }
  return problems.length === 0;
}

module.exports = {
  Structure, build, verify, DATA_VERSION,
  SUPPORTS_VEGETATION, BLOCKS_MOTION, FREE_OF_MOTION,
  b, i, s, list, comp,
};
