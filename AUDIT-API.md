# Audit des API — 13/09/2026

Ce que le mod aurait pu utiliser au lieu de ses huit mixins et de son script de
structures, vérifié **jar par jar** sur l'instance de Jérôme et sur le jar 26.2,
jamais de mémoire. Chaque affirmation porte la ligne de `javap`, de bytecode ou
de JSON qui la prouve.

Aucune ligne de code n'a été écrite dans cette session.

---

## Le tableau

| Problème | Ce qu'on fait aujourd'hui | L'alternative | Ce qu'elle coûterait |
| --- | --- | --- | --- |
| **Brouillard invisible sous Sodium** | mixin `FogRenderer.setupFog` à `RETURN`, priorité par défaut | **Aucune API** (ni Sodium ni Iris n'en exposent), mais Sodium s'injecte **au même endroit** et l'ordre se règle : `priority` **inférieure** à 1000 | **Rien.** Un attribut, zéro dépendance |
| Brouillard sous un **shaderpack** Iris | — | aucune : le pack décide s'il lit `fogStart`/`fogEnd` | `IrisApi.isShaderPackInUse()` permet au moins de le détecter, au prix d'une dépendance douce |
| **Emprise creuse + `beard_thin`** | `.nbt` à liste creuse, terrain aplani au bruit | `projection: terrain_matching` (= `GravityProcessor(WORLD_SURFACE_WG, −1)`) | mauvais outil : il **drape colonne par colonne**. Vanilla ne l'emploie que pour des rues et des dalles plates, jamais un bâtiment |
| **Décor tiré au hasard** | tirage figé dans `builder.js`, donc **identique dans tous les mondes** | `minecraft:rule` + `random_block_match`, tiré sur la **position absolue** | un `processor_list` de plus, zéro dépendance. Déplace une partie du script vers des données |
| **Remplacement selon le terrain** | rien | `minecraft:rule` + `location_predicate` (teste le bloc **déjà dans le monde**) | idem — pure donnée |
| **`structure_void` impossible** | liste de blocs creuse dans le `.nbt` | `block_ignore` sur `minecraft:structure_void` : ça marche | rien, mais **aucun gain** : on a déjà le résultat depuis l'étape 13 |
| **Sanctuaires en bordure de biome** | rien | **pas l'option B.** Le bord se décide dans `Structure`, pas dans `StructurePlacement` : il faut un `StructureType` custom (ou un mixin) qui échantillonne plusieurs points | du vrai code de worldgen, et un type de structure maison dans le monde sauvegardé |
| **`exclusion_zone` limitée à un `other_set`** | option A, manoir seulement | option B : `StructurePlacementType` custom — faisable, `hasStructureChunkInRange` public | ~150 lignes, aucune dépendance. **Personne dans l'instance ne le fait** |
| **Production des `.nbt`** | `tools/structure/builder.js` | Fabric Data Generation API : **ne produit que du JSON**, aucun provider de template | le script reste. La datagen ne prendrait que les 4 JSON de worldgen |
| **Exclusivité d'enchantements** (3 mixins) | `areCompatible` détourné aux 3 appelants | `EnchantmentEvents.ALLOW_ENCHANTING` prend bien un `ItemStack`… mais est branché sur `canEnchant` / `isPrimaryItem`, **pas** sur la compatibilité mutuelle | ne répond pas à la question posée. Les mixins restent |
| **Durabilité pilotée par la config** | `ItemStackMixin` sur `getMaxDamage` / `isDamageableItem` | `DefaultItemComponentEvents.MODIFY` pose `MAX_DAMAGE` par item | perdrait `/mastersword reload` (les composants par défaut sont figés une fois) et ne couvre pas `isDamageableItem` |
| **Vague de lumière** (2 mixins) | `Player.attack` + `ServerPlayer.swing` | `AttackEntityCallback`, `ServerLivingEntityEvents`… | aucun ne porte la **charge** d'attaque, aucun ne se déclenche sur un balayage à vide côté serveur |
| **Verrou anti-arbres** (2 mixins) | `TreeFeature` / `AbstractHugeMushroomFeature` / `FallenTreeFeature` à `HEAD` | `PlacementModifierType` custom + **écrasement** de `dark_forest_vegetation.json` | écraser un fichier vanilla (D&T et Trek écrivent déjà dans `data/minecraft/`), et ne couvrirait **que** cette feature |
| **Config** | `Codec` + JSON maison | Cloth Config | un mod de plus, **et Mod Menu n'est pas installé** : l'écran serait inatteignable |
| **Barre de durabilité fausse en multi** | rien (§6 le dit) | ForgeConfigAPIPort : il **synchronise** vraiment le fichier serveur → client | une dépendance dure pour ~40 lignes qu'on sait écrire avec `fabric-networking-api-v1` |

---

## 1. Le brouillard sous Sodium et Iris

### 1.1 Aucune API déclarée — confirmé

`net.caffeinemc.mods.sodium.api` contient 52 classes : `config/…` (35),
`vertex/…` (13), `util/Color*`, `texture/SpriteUtil`, `math/MatrixHelper`,
`memory/MemoryIntrinsics`, `blockentity/BlockEntityRenderHandler`. **Zéro
brouillard.**

`net.irisshaders.iris.api.v0` contient 7 classes, dont l'interface centrale :

```
public interface net.irisshaders.iris.api.v0.IrisApi {
  public static IrisApi getInstance();
  public abstract boolean isShaderPackInUse();
  public abstract boolean isRenderingShadowPass();
  public abstract IrisApiConfig getConfig();
  public abstract float getSunPathRotation();
  public abstract void assignPipeline(RenderPipeline, IrisProgram);
  …
}
```

Pas de brouillard non plus. Balayage des 42 jars de l'instance : **aucune classe
nommée `*Fog*` dans un paquet `api/`**, et **exactement trois mods** référencent
`net/minecraft/client/renderer/fog/FogRenderer` — Iris, Sodium, et nous.

### 1.2 Ce qui était établi est faux, et c'est ce qui débloque

`SPEC.md` §5 et le journal du 12/09 disent : « le rappel de Sodium renvoie un
`Vector4f` : il n'injecte **pas** au même endroit ». Le pool de constantes de
`net/caffeinemc/mods/sodium/mixin/core/render/world/FogRendererMixin.class` dit
le contraire :

```
#84 = Utf8   Lorg/spongepowered/asm/mixin/injection/Inject;
#85 = Utf8   method
#86 = Utf8   setupFog
#87 = Utf8   at
#88 = Utf8   Lorg/spongepowered/asm/mixin/injection/At;
#89 = Utf8   value
#90 = Utf8   RETURN
#92 = Utf8   Lcom/llamalad7/mixinextras/sugar/Local;
#97 = Utf8   Lorg/spongepowered/asm/mixin/Mixin;
#98 = Utf8   Lnet/minecraft/client/renderer/fog/FogRenderer;
```

Soit `@Mixin(FogRenderer.class) @Inject(method = "setupFog", at = @At("RETURN"))`
— **notre point d'injection, au mot près**.

Le `Vector4f` n'est que le **générique** du `CallbackInfoReturnable` :

```
#81 = Utf8  Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable<Lorg/joml/Vector4f;>;
```

C'est un reste d'une signature plus ancienne de Minecraft ; les génériques sont
effacés à l'exécution, donc le mixin s'applique quand même. En 26.2 :

```
public net.minecraft.client.renderer.fog.FogData setupFog(
    net.minecraft.client.Camera, int, net.minecraft.client.DeltaTracker,
    float, net.minecraft.client.multiplayer.ClientLevel);
```

Sodium récupère le `FogData` par le sucre `@Local` de MixinExtras, et en recopie
**huit champs** dans son propre `FogParameters` :

```
 5: aload 7   getfield FogData.color  → Vector4f.x / .y / .z / .w
39: getfield  FogData.environmentalStart
44: getfield  FogData.environmentalEnd
49: getfield  FogData.renderDistanceStart
54: getfield  FogData.renderDistanceEnd
57: invokespecial FogParameters."<init>":(FFFFFFFF)V
```

### 1.3 Le chemin complet, jusqu'au shader

`FogParameters` → `UniformBufferManager.update(ChunkRenderMatrices, FogParameters)`,
qui appelle les huit accesseurs dans l'ordre (`red`, `green`, `blue`, `alpha`,
`environmentalStart`, `environmentalEnd`, `renderStart`, `renderEnd`) → uniforme
→ `assets/sodium/shaders/include/fog.glsl` :

```glsl
float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance,
                      float environmentalStart, float environmantalEnd,
                      float renderDistanceStart, float renderDistanceEnd) {
    return max(linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd),
               linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd));
}
```

C'est **exactement la formule de vanilla**, avec le même `max` entre les deux
paires de bornes que le journal du 12/09 avait relevé. Sodium ne fabrique pas son
brouillard : il recopie le nôtre… s'il le voit.

Et Iris **dépend de Sodium pour ça**. Son uniforme `fogEnd`, en entier :

```
private static float lambda$addFogUniforms$9();
   0: invokestatic  Minecraft.getInstance()
   3: getfield      Minecraft.gameRenderer
   6: checkcast     class net/caffeinemc/mods/sodium/client/util/FogStorage
   9: invokeinterface FogStorage.sodium$getFogParameters()
  14: invokevirtual FogParameters.environmentalEnd()
  17: freturn
```

Le mixin `FogRenderer` d'Iris, lui, s'injecte à `HEAD` et à `RETURN` mais ne lit
que la **couleur** (`CapturedRenderingState.setFogColor(F,F,F)`).

### 1.4 Donc c'est un problème d'ordre, et l'ordre se règle

Le `FogData` retourné **est** celui que Sodium capture. Bytecode de `setupFog` :

```
 29: new           class net/minecraft/client/renderer/fog/FogData
 36: astore        10          ← variable locale 10
…
152: aload         10
154: areturn                   ← le même objet
```

Les trois configs de mixins (`mastersword.client.mixins.json`,
`sodium-common.mixins.json`, `mixins.iris.json`) ne déclarent **aucune
`priority`** : toutes à 1000. Or, dans sponge-mixin 0.17.4 :

```
public int compareTo(MixinInfo other);
   // priorités égales  → Integer.compare(this.order, other.order)
   // sinon             → this.priority < other.priority ? -1 : 1
```

et `TargetClassContext.applyMixins()` passe un `SortedSet` à
`MixinApplicatorStandard.apply(SortedSet)`, qui l'itère dans cet ordre croissant.
**La priorité la plus basse est appliquée en premier**, et un `@Inject` à
`RETURN` insère son appel juste avant le `areturn` — donc le mixin appliqué en
premier s'exécute en premier.

À priorité égale, l'ordre tombe sur `order`, c'est-à-dire l'ordre d'enregistrement
des configurations : un hasard. **Le fait que le brouillard soit invisible est la
preuve que ce hasard tombe aujourd'hui du côté de Sodium.**

> ⚠️ Une priorité **plus haute** aggraverait le problème. Si c'est ce qui a été
> essayé, c'est l'explication de « piste écartée ».

**Le correctif tient en un attribut :**

```java
@Mixin(value = FogRenderer.class, priority = 500)
```

Ce que je ne peux pas prouver sans lancer le jeu : que le rendu redevient
correct. Deux façons de le vérifier :

- lancer avec `-Dmixin.debug.export=true` et lire
  `.mixin.out/class/net/minecraft/client/renderer/fog/FogRenderer.class` — l'ordre
  des deux appels y est écrit noir sur blanc ;
- ou simplement marcher jusqu'au sanctuaire, Sodium et Iris en place.

**Réserve honnête sur Iris** : ce correctif rend le brouillard au **terrain de
Sodium**. Sous un shaderpack, les uniformes `fogStart` / `fogEnd` sont *offerts*
au pack, qui reste libre de calculer son propre brouillard atmosphérique. Là,
aucune API n'y peut rien — mais `IrisApi.getInstance().isShaderPackInUse()`
permettrait au moins de le savoir.

---

## 2. La génération de structures

### 2.1 Comment font les autres

Dix mods de structures inspectés dans l'instance.

| Mod | Code Java | Dépendances | `structure_set` | `processor_list` | `exclusion_zone` |
| --- | --- | --- | --- | --- | --- |
| dungeons-and-taverns 5.3.2 | **0 classe** | `fabric-resource-loader-v0` | 32 | 63 | **13 sets** |
| D&T desert / jungle / nether / stronghold / mansion | **0 classe** | idem | 0–1 | 1–6 | 0 |
| trek B0.6.2 | **0 classe** | idem | 21 | 45 | **4 sets** |
| illager_arena | 1 classe | `fabric-api` | 1 | 0 | 0 |
| illagerwarship | 1 classe | `fabric-api` | 1 | 0 | 0 |
| illager_war_trireme | 1 classe | `fabric-api` | 1 | 0 | 0 |
| end_villager_outpost | 1 classe | `fabric-api` | 1 | 0 | 0 |

**Personne ne dépend d'une bibliothèque de placement.** Recherche du littéral
`StructurePlacement` dans le contenu décompressé des **42 jars** de l'instance :
**zéro occurrence**. (Contrôle de la méthode : la même recherche sur
`random_spread` répond sur 9 jars, dont le nôtre — le balayage fonctionne.)
Autrement dit, **l'option B de §4.1 n'est faite par personne**, et les six jars
de Dungeons & Taverns comme les 12 Mo de Trek sont de purs datapacks.

Les quatre petits mods sont **architecturalement identiques au nôtre** :
`minecraft:jigsaw`, `size: 1`, un `single_pool_element` `rigid`, un `.nbt`. Une
seule différence, et elle est inutile : ils passent tous un `processors` en ligne

```json
"processors": { "processors": [
  { "processor_type": "minecraft:block_ignore",
    "blocks": [ { "Name": "minecraft:structure_block" } ] } ] }
```

alors que `SinglePoolElement.getSettings` ajoute déjà
`BlockIgnoreProcessor.STRUCTURE_BLOCK` d'office (bytecode, offset 39), puis
`JigsawReplacementProcessor.INSTANCE` (offset 77), puis notre liste, puis les
processors de la **projection** (offset 117).

Deux idées à prendre chez eux :

- **Trek range ses structures par rareté, pas une par set** : `overworld/very_common`,
  `common`, `medium`, `rare`, `very_rare`, chacun un `structure_set` avec 13 à 16
  structures pondérées. Pertinent le jour où le mod aura une seconde variante.
- **Deux champs du codec vanilla qu'on n'utilise pas** : `frequency` et
  `frequency_reduction_method`. La liste complète des champs de
  `minecraft:random_spread`, relevée dans les codecs, est : `salt`, `frequency`,
  `frequency_reduction_method`, `exclusion_zone`, `locate_offset` (hérités de
  `StructurePlacement`) plus `spacing`, `separation`, `spread_type`.

Usage d'`exclusion_zone` : **1 set vanilla sur 20** (`pillager_outposts`), 13
chez D&T, 4 chez Trek. Exemples : `nova_structures:shrines` s'écarte de
`minecraft:strongholds` à `chunk_count: 15`, `pale_residence` de
`woodland_mansions` à 10, les sets de Trek des `villages` à 6. Le record reste
`@Deprecated` en 26.2 (`Deprecated: true` dans le class file de
`StructurePlacement$ExclusionZone`) et toujours limité à **un seul** `other_set`.

### 2.2 Les processors

Types employés, tous mods confondus :

| Type | Occurrences |
| --- | --- |
| `rule` | 131 |
| `protected_blocks` | 18 |
| `capped` | 9 |
| `block_ignore` | 5 |
| `gravity` | **1** |

Les onze types vanilla, relevés dans `StructureProcessorTypes` :
`blackstone_replace`, `block_age`, `block_ignore`, `block_rot`, `capped`,
`gravity`, `jigsaw_replacement`, `lava_submerged_block`, `nop`,
`protected_blocks`, `rule`.

#### `gravity` — non, et pour une raison précise

`GravityProcessor.processBlock`, en clair :

```java
newY = level.getHeight(heightmap, info.pos().getX(), info.pos().getZ())
     + this.offset
     + templateRelativePos.getY();
```

(bytecode : `getHeight` à l'offset 78, `+ offset` à 87, `+ templateRelativePos.getY()`
à 114 ; les noms viennent de la `LocalVariableTable`.)

La hauteur est relue **à la verticale de chaque bloc**. Ce n'est pas une chute,
c'est un **drapé** : une terrasse plate suivrait toutes les bosses du sol, et
tout ce qui a du relief serait cisaillé colonne par colonne.

Vanilla le confirme par l'usage : `projection: "terrain_matching"` **est**
`new GravityProcessor(Heightmap.Types.WORLD_SURFACE_WG, -1)` (static de
`StructureTemplatePool$Projection`), et il n'apparaît que dans **18 des 188**
`template_pool` du jeu :

```
village/{plains,desert,savanna,snowy,taiga}/streets.json
village/…/terminators.json
pillager_outpost/feature_plates.json
```

Des rues et des dalles. **Aucun bâtiment.**

Et il ne remplacerait pas `beard_thin` : `Beardifier` est une
`DensityFunction` (`implements DensityFunctions$BeardifierOrMarker`) évaluée à
l'étape de **bruit**, à partir de la boîte englobante des pièces, donc bien avant
le placement où tournent les processors. Le beard aplanirait d'abord, la gravité
draperait ensuite sur ce plat. Pour l'essayer il faudrait passer
`terrain_adaptation` à `none` — un autre projet.

#### `rule` — **le vrai trou de notre approche**

Deux capacités qu'on n'a pas.

**(1) Le tirage est refait à chaque sanctuaire.** `RuleProcessor.processBlock`
commence par :

```
2: invokevirtual StructureBlockInfo.pos()
5: invokestatic  Mth.getSeed(Vec3i)
8: invokestatic  RandomSource.create(J)
```

La graine est la **position absolue dans le monde**. Un `random_block_match`
donne donc un décor différent à chaque occurrence, alors que le tirage de
`builder.js` est figé dans le `.nbt` : **tous nos sanctuaires, dans tous les
mondes, sont identiques au bloc près**.

**(2) Le remplacement peut lire le terrain.** `ProcessorRule.test` évalue
`locPredicate.testAgainstWorldState(level, pos, random)` — le bloc **déjà présent
dans le monde** à cette position. D&T s'en sert exactement comme ça :

```json
{ "input_predicate":    { "predicate_type": "minecraft:block_match",
                          "block": "minecraft:mossy_cobblestone" },
  "location_predicate": { "predicate_type": "minecraft:block_match",
                          "block": "minecraft:water" },
  "output_state":       { "Name": "minecraft:dark_oak_planks" } }
```

(`nova_structures/worldgen/processor_list/mansion_overhaul_generic_degradation.json` :
la pierre moussue devient du plancher là où le manoir tombe dans l'eau.)

Les prédicats disponibles : `always_true`, `block_match`, `blockstate_match`,
`random_block_match`, `random_blockstate_match`, `tag_match`, plus un
`position_predicate` (`axis_aligned_linear_pos`, `linear_pos`) et un
`block_entity_modifier` (D&T s'en sert pour poser des `suspicious_sand` avec leur
table de butin).

#### `block_ignore` et `structure_void` — ça marche, mais ça n'apporte rien

`BlockIgnoreProcessor.processBlock` renvoie `null` — donc supprime l'entrée — si
le bloc est dans la liste (bytecode : `ImmutableList.contains` puis
`aconst_null; areturn`). Un `block_ignore` sur `minecraft:structure_void`
fonctionnerait donc.

Et l'affirmation de l'étape 13 est confirmée : dans tout le paquet
`templatesystem`, **une seule** classe mentionne `Blocks.STRUCTURE_VOID`
(`JigsawReplacementProcessor`, et seulement pour le `final_state` d'un bloc
jigsaw) ; `BlockIgnoreProcessor.STRUCTURE_BLOCK` ne contient que
`Blocks.STRUCTURE_BLOCK`. Sans processor, un `structure_void` serait posé tel
quel.

Mais depuis l'étape 13 la liste creuse donne déjà le résultat : ce serait du
confort d'écriture, pas une capacité.

#### Trois autres, jamais évalués

`capped` (un délégué + un `IntProvider` de limite — « exactement N blocs
transformés »), `block_age` et `block_rot` (usure), `protected_blocks`.

### 2.3 Le placement en bordure — **mesuré**

Quatre mondes lus avec `tools/structure/anvil.js`, **187 215 chunks générés**,
à la recherche des `block_entity` d'id `mastersword:master_sword_pedestal` :

| Monde | Régions | Chunks générés | Socles |
| --- | --- | --- | --- |
| instance / `Hey` | 697 | 161 700 | 0 |
| instance / `TEST` | 26 | 8 201 | 0 |
| instance / `New World` | 16 | 3 961 | 0 |
| dev / `New World` | 39 | 7 163 | 1, `shrine: 0` → **posé à la main** |
| dev / `New World (1)` | 73 | 6 190 | **1, `shrine: 1`** |

Les 161 700 chunks de `Hey` ne prouvent rien : ils ont été générés avant que le
mod n'entre dans l'instance. **Il n'existe donc qu'un seul sanctuaire généré à
mesurer**, et c'est la faiblesse de ce point : n = 1.

Sur ce seul échantillon, Jérôme a raison. Socle en `-4295 / 65 / -5356`, carte des
biomes à `y = 65`, une case par cellule de 4 blocs, 84 blocs de côté :

```
#####################
#####################
#####################
#####################
#####################
#####################
#####################
####################a
#############aa#####a
############aaaa###aa
##########S#aaaaaaaaa
###########aaaaaaaaaa
##########aaaaaaaaaaa
##########aaaaaaaaaaa
####aa##aaaaaaaaaaaaa
###aaaaaaaaaaaaaaaaaa
###aaaaaaaaaaaaaa####
####aaaaaaaaaa#######
####aaaaaaaaa########
a#aaaaaaaa###########
#aaaaaaaa############

#  minecraft:dark_forest      a  minecraft:river      S  le socle
```

**8 blocs** entre le socle et la rivière, et tout le quadrant sud-est est de la
rivière — dans une Dark Forest qui fait pourtant ~370 blocs de côté.

**Et le mécanisme explique pourquoi c'est normal.** `Structure.isValidBiome`
teste **un seul point** :

```
 1: invokevirtual GenerationStub.position()
17/24/31: BlockPos.getX/getY/getZ → QuartPos.fromBlock
44: invokevirtual BiomeSource.getNoiseBiome(IIILClimate$Sampler;)
47: invokeinterface Predicate.test
```

Une cellule de 4 × 4 × 4 blocs, et rien d'autre. Ni l'emprise, ni le voisinage.
Pire : `JigsawStructure.findGenerationPoint` construit ce point comme

```
37: invokevirtual ChunkPos.getMinBlockX()
42: invokevirtual ChunkPos.getMinBlockZ()
45: invokespecial BlockPos."<init>":(III)V
```

soit le **coin nord-ouest du chunk**, pas son centre. Le biome est donc validé sur
un coin, et la structure se construit jusqu'à 16 blocs plus loin.

**Conséquence directe pour l'option B : elle ne réglerait pas ça.** Un
`StructurePlacement` choisit des *chunks* et s'écarte d'autres *sets* ; la marge
au bord de biome se décide dans `Structure`. Corriger les bordures veut dire un
`StructureType` maison (ou un mixin sur `isValidBiome`) qui échantillonne
plusieurs points autour de l'ancre — c'est un autre chantier que celui décrit en
§4.1.

Si Jérôme veut une vraie mesure plutôt qu'un échantillon : pré-générer quelques
milliers de chunks de Dark Forest et relancer le scan. Les scripts sont écrits.

**Et l'option B reste faisable pour ce qu'elle promet vraiment** (plus d'une
exclusion, une distance en blocs, un `spacing` lu dans la config) :

```
public boolean hasStructureChunkInRange(Holder<StructureSet>, int, int, int);   // public
public static final Registry<StructurePlacementType<?>> STRUCTURE_PLACEMENT;    // public
public boolean applyInteractionsWithOtherStructures(ChunkGeneratorStructureState, int, int);  // surchargeable
```

`RandomSpreadStructurePlacement` a un constructeur public à 8 arguments : on en
hérite, on surcharge `applyInteractionsWithOtherStructures`, on enregistre le
type. Aucune dépendance externe. Mais personne dans l'instance ne le fait, et ça
ne résout pas le défaut que Jérôme a signalé.

### 2.4 La production des `.nbt`

La Fabric Data Generation API v1 expose onze providers :

```
FabricAdvancementProvider          FabricLanguageProvider
FabricBlockLootSubProvider         FabricLootTableSubProvider
FabricCodecDataProvider            FabricModelProvider
FabricDynamicRegistryProvider      FabricRecipeProvider
FabricEntityLootSubProvider        FabricSoundsProvider
FabricTagsProvider
```

Les deux génériques écrivent du **JSON**, et rien d'autre :

```
private JsonElement FabricCodecDataProvider.convert(Identifier, T, DynamicOps<JsonElement>);
private static <E> CompletableFuture<?> FabricDynamicRegistryProvider.writeToPath(
        Path, CachedOutput, DynamicOps<JsonElement>, Encoder<E>, E, ResourceCondition[]);
```

**Il n'existe aucun provider de template de structure.** La datagen ne produit
pas de NBT.

Elle pourrait reprendre nos quatre JSON de worldgen, le tag de biome,
l'advancement, la loot table et les traductions — écrits contre les vrais codecs
au lieu d'être tapés à la main, ce qui est un petit gain réel. Elle ne toucherait
pas à `clearing.nbt`. **Le script reste le bon outil pour le `.nbt`**, et la
question se réduit à : veut-on décrire la worldgen à deux endroits pour quatre
petits fichiers ? Non.

---

## 3. Les huit mixins

| Mixin | Ce qu'il contourne | API ? |
| --- | --- | --- |
| `AnvilMenuMixin` | `Enchantment.areCompatible` à l'enclume | **non** — voir ci-dessous |
| `EnchantmentHelperMixin` | `filterCompatibleEnchantments` à la table | **non** |
| `EnchantCommandMixin` | `EnchantmentHelper.isEnchantmentCompatible` dans `/enchant` | **non** |
| `ItemStackMixin` | `getMaxDamage` / `isDamageableItem` depuis la config | **à moitié** |
| `PlayerAttackMixin` | lire `fullStrengthAttack` au moment du coup | **non** |
| `ServerPlayerMixin` | le balayage à vide côté serveur | **non** |
| `TreeFeatureMixin` + `FeaturePlacementMixins` | annuler un arbre selon la position | **non** |
| `FogRendererMixin` | le brouillard | **non** (mais §1) |

### Les trois mixins d'enchantement

`EnchantmentEvents.ALLOW_ENCHANTING` existe bel et bien dans
`fabric-item-api-v1`, et — contrairement à `MODIFY` — **il reçoit la pile** :

```
public interface EnchantmentEvents$AllowEnchanting {
  TriState allowEnchanting(Holder<Enchantment>, ItemStack, EnchantingContext);
}
```

Mais il répond à une autre question. Fabric le branche sur, pool de constantes à
l'appui :

| Mixin de Fabric | Appel redirigé |
| --- | --- |
| `fabric/mixin/item/AnvilMenuMixin` | `Enchantment.canEnchant(ItemStack)Z` |
| `fabric/mixin/item/EnchantCommandMixin` | `Enchantment.canEnchant(ItemStack)Z` |
| `fabric/mixin/item/EnchantmentHelperMixin` | `Enchantment.isPrimaryItem(ItemStack)Z` |

« Cet enchantement va-t-il sur cet item ? » — pas « ces deux enchantements
peuvent-ils coexister ? », qui est `areCompatible`. L'événement ne passe jamais
par là. **Les trois mixins restent nécessaires**, et §2.4 avait raison sur le
fond même si la raison invoquée (`MODIFY` est global) n'était que la moitié de
l'histoire.

Détail rassurant au passage : Fabric API mixe dans **exactement les trois mêmes
classes** que nous. Terrain balisé.

### `ItemStackMixin`

`DefaultItemComponentEvents.MODIFY` permet de poser `DataComponents.MAX_DAMAGE`
par item à la construction du registre :

```
public interface DefaultItemComponentEvents$ModifyContext {
  void modify(Predicate<Item>, ModifyConsumer);
  default void modify(Item, Consumer<DataComponentMap$Builder>);
  …
}
```

C'est l'API propre pour **la valeur**. Elle coûte deux choses : les composants par
défaut sont figés une fois pour toutes, donc `/mastersword reload` ne les
rechargerait plus ; et elle ne couvre pas `isDamageableItem()`, qui est le second
point d'accroche du mixin.

`CustomDamageHandler.hurtAndBreak(ItemStack, int, LivingEntity, EquipmentSlot, Runnable)`
existe aussi, mais il gouverne les dégâts **encaissés**, pas la durabilité
maximale.

Verdict : garder le mixin, en sachant qu'une moitié d'API existe.

### La vague de lumière

`fabric-events-interaction-v0` offre `AttackEntityCallback.interact(Player, Level,
InteractionHand, Entity, EntityHitResult)`, `AttackBlockCallback`,
`ItemEvents.USE` / `USE_ON`, `PlayerBlockBreakEvents` ;
`fabric-entity-events-v1` offre `ServerLivingEntityEvents`,
`ServerEntityCombatEvents`, `ServerPlayerEvents`.

**Aucun ne porte la charge d'attaque**, et aucun ne se déclenche sur un balayage
à vide côté serveur. Le motif de §3 — `onAttack()` remet `attackStrengthTicker` à
zéro avant que `fullStrengthAttack` ne soit calculé — reste sans réponse d'API.

### Le verrou anti-arbres

`fabric-biome-api-v1` se résume à `BiomeModifications` / `BiomeSelectors` :
ajouter ou retirer des features **par biome**, globalement. Rien de positionnel.

Côté vanilla, les quinze types de modificateurs de placement sont : `biome`,
`block_predicate_filter`, `count`, `count_on_every_layer`, `environment_scan`,
`fixed_placement`, `height_range`, `heightmap`, `in_square`, `noise_based_count`,
`noise_threshold_count`, `random_offset`, `rarity_filter`,
`surface_relative_threshold_filter`, `surface_water_depth_filter`. **Aucun ne
connaît les structures.**

Une alternative existe pourtant, et elle mérite d'être nommée pour être écartée :
`BuiltInRegistries.PLACEMENT_MODIFIER_TYPE` est public et
`PlacementContext.getLevel()` renvoie un `WorldGenLevel`, donc on pourrait
enregistrer un modificateur maison et l'ajouter à
`data/minecraft/worldgen/placed_feature/dark_forest_vegetation.json` — cinq
modificateurs aujourd'hui. Mais cela veut dire **écraser un fichier vanilla**
(le dernier mod chargé gagne, et D&T comme Trek écrivent déjà dans
`data/minecraft/`), et ne couvrirait que cette feature-là, alors que le mixin sur
`TreeFeature` attrape **n'importe quel arbre de n'importe quel mod**. Le mixin
est le meilleur outil.

---

## 4. La configuration

### Cloth Config — non

Présent dans l'instance (`cloth-config-26.2.155.jar`,
`depends: {fabricloader, minecraft}`), il apporte AutoConfig
(`AutoConfig.register(Class<T>, ConfigSerializer$Factory<T>)`, sérialiseurs Gson,
Jankson, TOML4j, YAML) et surtout un **écran de configuration**.

Deux faits mesurés dans l'instance :

- **aucun des 42 mods ne déclare de dépendance à `cloth-config`** (balayage de
  tous les `fabric.mod.json`) — il est là sans être utilisé ;
- **Mod Menu n'est pas installé**, et l'écran d'AutoConfig s'atteint par Mod Menu
  (son entrypoint `modmenu` est `ClothConfigModMenuDemo`).

On ajouterait donc une dépendance dure, à faire suivre à chaque version de
Minecraft, pour un écran que personne ne peut ouvrir.

### ForgeConfigAPIPort — non, mais il désigne un vrai trou

Il apporte trois choses réelles :

1. TOML via `ForgeConfigSpec` ;
2. `net.neoforged.fml.config.ConfigWatcher implements Runnable` — rechargement
   quand le fichier change sur le disque ;
3. **la synchronisation serveur → client** :

```
fuzs/forgeconfigapiport/fabric/impl/network/ConfigSync.syncAllConfigs(ServerConfigurationPacketListener)
fuzs/forgeconfigapiport/fabric/impl/network/configuration/SyncConfig implements ConfigurationTask
fuzs/forgeconfigapiport/fabric/impl/network/payload/ConfigFilePayload(String fileName, byte[] contents)
```

Le serveur pousse son fichier de config de type `SERVER` à chaque client pendant
la phase de configuration. C'est **exactement** le trou que §6 signale :
« la barre de durabilité étant dessinée à partir du `max_durability` du client, un
client mal réglé affiche une barre fausse ».

Mais les deux autres limites de §6 (attributs figés à l'enregistrement, worldgen
lue dans le JSON du datapack) sont des limites de Minecraft, qu'aucune
bibliothèque de config ne lève. Et la synchro tient en une quarantaine de lignes
avec `fabric-networking-api-v1`, dont on dépend déjà, sur le modèle du
`ConfigurationTask` ci-dessus.

Un seul mod de l'instance en dépend (Paraglider).

---

## Recommandation

**1. La priorité du mixin de brouillard. À faire.** Un attribut, zéro
dépendance, et c'est le seul défaut qu'un joueur voit. Le mécanisme est prouvé de
bout en bout ; il ne reste qu'à le confirmer en jeu, Sodium et Iris en place. Et
c'est l'occasion de corriger `SPEC.md` §5, qui affirme le contraire de ce que dit
le bytecode de Sodium.

**2. Un `processor_list` avec `rule` + `random_block_match`. À considérer.**
C'est le seul manque de fond de l'approche structures : aujourd'hui tous les
sanctuaires de tous les mondes sont identiques au bloc près, parce que le tirage
est figé dans le `.nbt`. Un processor le referait à chaque position. Pure donnée,
aucune dépendance — mais cela déplace une partie de `builder.js` vers du JSON, et
`builder.js` a des auto-contrôles que le JSON n'aura pas. À faire seulement si
Jérôme veut vraiment que deux sanctuaires diffèrent.

**3. Tout le reste : non.** Les huit mixins contournent des absences réelles, et
la seule demi-API disponible (`DefaultItemComponentEvents`) coûterait le
rechargement à chaud. Le script `.nbt` n'a pas de remplaçant. La config maison
tient, et les deux bibliothèques présentes dans l'instance n'apporteraient qu'un
écran inatteignable ou une dépendance dure.

**4. Deux choses à noter sans les faire tout de suite :**

- l'**option B ne corrige pas les bordures de biome** — le bord se décide dans
  `Structure.isValidBiome`, sur un point unique au coin du chunk, pas dans le
  `StructurePlacement`. Si les bordures gênent vraiment Jérôme, c'est là qu'il
  faut agir, et il faudrait d'abord mesurer sur plus d'un sanctuaire ;
- la **barre de durabilité fausse en multijoueur** est un vrai bug ouvert de §6,
  et il se règle avec `fabric-networking-api-v1`, sans ForgeConfigAPIPort.

**Le mod tourne aujourd'hui avec `fabric-api` pour seule dépendance. Rien dans cet
audit ne justifie de perdre ça.**
