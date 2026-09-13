// A read-only Anvil (.mca) reader, just enough to look at what Jérôme built in
// game and compare it with what the script generated.
//
// Written for one job: when a shrine is edited by hand in game, read the world
// back and say precisely what changed, instead of guessing from a screenshot.
// The .nbt the builder produces and the chunks the game writes use the same tag
// format, so nbt.js does the decoding; everything here is the container around
// it, plus the packed-palette encoding sections use for their blocks.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { decode, plain } = require('./nbt.js');

const SECTOR = 4096;

/** Decodes one chunk out of a region file, or null if the slot is empty. */
function readChunk(buffer, index) {
  const offset = buffer.readUInt32BE(index * 4) >>> 8;
  const sectors = buffer.readUInt8(index * 4 + 3);
  if (offset === 0 || sectors === 0) return null;

  const start = offset * SECTOR;
  const length = buffer.readUInt32BE(start);
  const compression = buffer.readUInt8(start + 4);
  const payload = buffer.subarray(start + 5, start + 4 + length);

  // 1 gzip, 2 zlib, 3 stored. Anything else is a version we have not met.
  let raw;
  if (compression === 1) raw = zlib.gunzipSync(payload);
  else if (compression === 2) raw = zlib.inflateSync(payload);
  else if (compression === 3) raw = payload;
  else throw new Error(`compression inconnue: ${compression}`);

  return plain(decode(raw));
}

/** Every chunk of a region file, with its chunk coordinates. */
function* chunks(file) {
  const buffer = fs.readFileSync(file);
  if (buffer.length < SECTOR * 2) return;
  const name = path.basename(file).split('.');   // r.X.Z.mca
  const regionX = Number(name[1]), regionZ = Number(name[2]);
  for (let i = 0; i < 1024; i++) {
    let chunk;
    try { chunk = readChunk(buffer, i); } catch { continue; }
    if (!chunk) continue;
    yield { x: regionX * 32 + (i % 32), z: regionZ * 32 + Math.floor(i / 32), chunk };
  }
}

/**
 * Unpacks one section's blocks into a (x, y, z) -> name map, in world
 * coordinates.
 *
 * Since 1.16 the indices are packed into longs WITHOUT straddling them: each
 * long holds floor(64 / bits) entries and the leftover high bits are padding.
 * Getting that wrong shears the whole section diagonally, which is at least
 * obvious when it happens.
 */
function readSection(section, chunkX, chunkZ, into) {
  const states = section.block_states;
  if (!states || !states.palette || !states.palette.length) return;
  // The whole state, not just the name: a fluid's `level` is the difference
  // between a still pool and something the game is still moving.
  const palette = states.palette;
  const baseY = section.Y * 16;
  const put = (i, state) => {
    if (state.Name === 'minecraft:air') return;
    into.set(`${chunkX * 16 + (i & 15)},${baseY + (i >> 8)},${chunkZ * 16 + ((i >> 4) & 15)}`, state);
  };

  if (palette.length === 1) {
    for (let i = 0; i < 4096; i++) put(i, palette[0]);
    return;
  }

  const bits = Math.max(4, 32 - Math.clz32(palette.length - 1));
  const perLong = Math.floor(64 / bits);
  const mask = (1n << BigInt(bits)) - 1n;
  const data = states.data;
  for (let i = 0; i < 4096; i++) {
    const value = BigInt.asUintN(64, BigInt(data[Math.floor(i / perLong)]));
    const index = Number((value >> BigInt((i % perLong) * bits)) & mask);
    put(i, palette[index] ?? { Name: 'minecraft:air' });
  }
}

/** Reads a box of the world: (x,y,z) -> {Name, Properties}, air omitted. */
function readBox(regionDir, min, max) {
  const blocks = new Map();
  const files = fs.readdirSync(regionDir).filter((f) => f.endsWith('.mca'));
  for (const file of files) {
    for (const { x, z, chunk } of chunks(path.join(regionDir, file))) {
      if (x * 16 > max.x || x * 16 + 15 < min.x) continue;
      if (z * 16 > max.z || z * 16 + 15 < min.z) continue;
      for (const section of chunk.sections ?? []) {
        if (section.Y * 16 > max.y || section.Y * 16 + 15 < min.y) continue;
        readSection(section, x, z, blocks);
      }
    }
  }
  for (const key of [...blocks.keys()]) {
    const [bx, by, bz] = key.split(',').map(Number);
    if (bx < min.x || bx > max.x || by < min.y || by > max.y || bz < min.z || bz > max.z) blocks.delete(key);
  }
  return blocks;
}

/** Every block entity of the given id, with its position. */
function findBlockEntities(regionDir, id) {
  const found = [];
  for (const file of fs.readdirSync(regionDir).filter((f) => f.endsWith('.mca'))) {
    for (const { chunk } of chunks(path.join(regionDir, file))) {
      for (const entity of chunk.block_entities ?? []) {
        if (entity.id === id) found.push(entity);
      }
    }
  }
  return found;
}

module.exports = { chunks, readBox, findBlockEntities };
