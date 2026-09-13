// Mesure si les sanctuaires générés tombent en bordure de biome.
//
// Écrit pour répondre à une observation de Jérôme -- « elle est trop souvent en
// bord de biome, rivière ou mer » -- autrement qu'à l'œil. Le monde sauvegardé
// contient les biomes, donc la question se mesure au lieu de se discuter.
//
// Deux passes, parce que la mémoire ne suit pas autrement : d'abord les socles,
// et seulement ensuite les biomes autour de chacun. Lire les biomes de toutes
// les régions d'un grand monde d'un coup, c'est des dizaines de millions de
// cellules pour n'en exploiter que quelques centaines.
//
//   node tools/structure/biome_edge.js "<monde>/dimensions/minecraft/overworld/region"
//
// Les cellules de biome font 4x4x4 blocs depuis 1.18 : toutes les distances
// ci-dessous sont donc arrondies à 4 blocs près, ce qui suffit largement pour
// dire « au bord » ou « au milieu ».
const fs = require('fs');
const path = require('path');
const { chunks } = require('./anvil.js');

const PEDESTAL_ID = 'mastersword:master_sword_pedestal';
const TARGET_BIOME = 'minecraft:dark_forest';
const MAP_RADIUS = 10;      // en cellules, soit 40 blocs de chaque côté
const SEARCH_RADIUS = 64;   // en cellules, soit 256 blocs

/**
 * Dépaquette les biomes d'une section.
 *
 * Même encodage que les blocs (indices empaquetés dans des longs, sans
 * chevauchement), mais 64 entrées au lieu de 4096 et un minimum de **1** bit
 * par indice au lieu de 4 -- c'est la seule différence, et l'oublier décale
 * tout sans rien casser de visible.
 */
function readBiomes(section, chunkX, chunkZ, into) {
	const b = section.biomes;
	if (!b || !b.palette || !b.palette.length) return;

	const baseY = section.Y * 4;
	// index i : x + z*4 + y*16, en cellules de 4 blocs
	const put = (i, name) => {
		into.set(`${chunkX * 4 + (i & 3)},${baseY + (i >> 4)},${chunkZ * 4 + ((i >> 2) & 3)}`, name);
	};

	if (b.palette.length === 1) {
		for (let i = 0; i < 64; i++) put(i, b.palette[0]);
		return;
	}

	const bits = Math.max(1, 32 - Math.clz32(b.palette.length - 1));
	const perLong = Math.floor(64 / bits);
	const mask = (1n << BigInt(bits)) - 1n;
	for (let i = 0; i < 64; i++) {
		const value = BigInt.asUintN(64, BigInt(b.data[Math.floor(i / perLong)]));
		put(i, b.palette[Number((value >> BigInt((i % perLong) * bits)) & mask)] ?? '?');
	}
}

/** Passe 1 : les socles, et combien de chunks ont vraiment été générés. */
function findPedestals(regionDir) {
	const files = fs.readdirSync(regionDir).filter((f) => f.endsWith('.mca'));
	const pedestals = [];
	let generated = 0;

	for (const file of files) {
		let iterator;
		try { iterator = chunks(path.join(regionDir, file)); } catch { continue; }
		for (const { chunk } of iterator) {
			generated++;
			for (const entity of chunk.block_entities ?? []) {
				if (entity.id !== PEDESTAL_ID) continue;
				// shrine vaut 1 pour un socle posé par la structure, 0 pour un socle
				// que le joueur a posé lui-même -- seuls les premiers mesurent la
				// génération.
				pedestals.push({ x: entity.x, y: entity.y, z: entity.z, shrine: entity.shrine === 1 });
			}
		}
	}

	return { regions: files.length, generated, pedestals };
}

/** Passe 2 : les biomes autour d'un socle, à sa hauteur. */
function biomesAround(regionDir, pedestal) {
	const cells = new Map();
	const minChunkX = Math.floor((pedestal.x - SEARCH_RADIUS * 4) / 16);
	const maxChunkX = Math.floor((pedestal.x + SEARCH_RADIUS * 4) / 16);
	const minChunkZ = Math.floor((pedestal.z - SEARCH_RADIUS * 4) / 16);
	const maxChunkZ = Math.floor((pedestal.z + SEARCH_RADIUS * 4) / 16);

	for (const file of fs.readdirSync(regionDir).filter((f) => f.endsWith('.mca'))) {
		// r.X.Z.mca couvre 32x32 chunks : sauter les fichiers hors de portée évite
		// de décompresser un monde entier pour un socle.
		const name = path.basename(file).split('.');
		const regionX = Number(name[1]), regionZ = Number(name[2]);
		if (regionX * 32 > maxChunkX || regionX * 32 + 31 < minChunkX) continue;
		if (regionZ * 32 > maxChunkZ || regionZ * 32 + 31 < minChunkZ) continue;

		let iterator;
		try { iterator = chunks(path.join(regionDir, file)); } catch { continue; }
		for (const { x, z, chunk } of iterator) {
			if (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ) continue;
			for (const section of chunk.sections ?? []) readBiomes(section, x, z, cells);
		}
	}

	return cells;
}

/** Distance de Chebyshev à la première cellule connue qui n'est pas le biome cible. */
function distanceToEdge(cells, pedestal) {
	const cy = Math.floor(pedestal.y / 4);
	const cx0 = Math.floor(pedestal.x / 4), cz0 = Math.floor(pedestal.z / 4);

	for (let r = 1; r <= SEARCH_RADIUS; r++) {
		for (let dx = -r; dx <= r; dx++) {
			for (let dz = -r; dz <= r; dz++) {
				// Seulement l'anneau de rayon r, pas le carré plein.
				if (Math.max(Math.abs(dx), Math.abs(dz)) !== r) continue;
				const value = cells.get(`${cx0 + dx},${cy},${cz0 + dz}`);
				if (value !== undefined && value !== TARGET_BIOME) return { blocks: r * 4, biome: value };
			}
		}
	}

	return null;
}

/** La carte, parce qu'un chiffre seul ne dit pas si le bord est un liseré ou un demi-plan. */
function drawMap(cells, pedestal) {
	const cy = Math.floor(pedestal.y / 4);
	const cx0 = Math.floor(pedestal.x / 4), cz0 = Math.floor(pedestal.z / 4);
	const legend = new Map();
	const letters = 'abcdefghijklmnopqrstuvwxyz';
	const rows = [];

	for (let dz = -MAP_RADIUS; dz <= MAP_RADIUS; dz++) {
		let row = '';
		for (let dx = -MAP_RADIUS; dx <= MAP_RADIUS; dx++) {
			if (dx === 0 && dz === 0) { row += 'S'; continue; }
			const value = cells.get(`${cx0 + dx},${cy},${cz0 + dz}`);
			if (value === undefined) row += ' ';
			else if (value === TARGET_BIOME) row += '#';
			else {
				if (!legend.has(value)) legend.set(value, letters[legend.size] ?? '?');
				row += legend.get(value);
			}
		}
		rows.push(row);
	}

	return { rows, legend };
}

const regionDir = process.argv[2];
if (!regionDir) {
	console.error('usage: node biome_edge.js "<monde>/dimensions/minecraft/overworld/region"');
	process.exit(2);
}

const { regions, generated, pedestals } = findPedestals(regionDir);
console.log(`régions lues     : ${regions}`);
console.log(`chunks générés   : ${generated}`);
console.log(`socles trouvés   : ${pedestals.length} (dont ${pedestals.filter((p) => p.shrine).length} générés)`);
console.log('');

// Un socle posé à la main ne dit rien du placement : on ne mesure que les autres.
for (const pedestal of pedestals.filter((p) => p.shrine)) {
	const cells = biomesAround(regionDir, pedestal);
	const edge = distanceToEdge(cells, pedestal);
	console.log(`socle ${pedestal.x} ${pedestal.y} ${pedestal.z}`);
	console.log(`   biome sur place : ${cells.get(`${Math.floor(pedestal.x / 4)},${Math.floor(pedestal.y / 4)},${Math.floor(pedestal.z / 4)}`)}`);
	console.log(`   bord le plus proche : ${edge ? `${edge.blocks} blocs, vers ${edge.biome}` : `> ${SEARCH_RADIUS * 4} blocs`}`);
	const { rows, legend } = drawMap(cells, pedestal);
	for (const row of rows) console.log(`   ${row}`);
	console.log(`   # = ${TARGET_BIOME}, S = le socle`);
	for (const [biome, letter] of legend) console.log(`   ${letter} = ${biome}`);
	console.log('');
}
