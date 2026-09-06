# The Master Sword — spécification

Document vivant. Il décrit **ce qu'on construit** et **les décisions prises**.
Les instructions de travail permanentes sont dans `CLAUDE.md`, les pièges de la
machine et de Minecraft 26.2 dans `../SETUP-MC-MODDING.md`.

Statut : *spécification validée, aucun code écrit.*
Dernière mise à jour : 06/09/2026 (2ᵉ passe de relecture par Jérôme).

Conventions de ce fichier :

- ✅ **Confirmé** — validé par Jérôme, ne pas changer sans le lui demander.
- 🔷 **Proposé** — choix de Claude Code, modifiable librement, à valider en passant.
- ❓ **Ouvert** — décision à prendre, listée en §8.

Toute valeur numérique de ce document est un **défaut de configuration** : elle
est ajustable dans le fichier de config (§6), sauf mention contraire.

---

## 1. Concept

Mod Fabric pour Minecraft 26.2. Une épée nommée **The Master Sword** apparaît
plantée dans un bloc, sur un petit décor (racines / pierres), en génération
naturelle. La retirer est un événement : tant qu'elle est en place, un brouillard
signale et garde le lieu.

Référence d'ambiance : le socle de la Master Sword dans *Zelda*. On ne copie
aucun asset, on reproduit l'idée.

---

## 2. L'item

### 2.1 Statistiques ✅

Identiques à l'épée en netherite.

| Propriété | Valeur | Source |
| --- | --- | --- |
| Dégâts d'attaque | ceux de `NETHERITE_SWORD` | à recopier depuis vanilla, jamais de mémoire |
| Vitesse d'attaque | idem | idem |
| Durabilité max | idem (2031 en 1.x — **à vérifier en 26.2**) | `DataComponents.MAX_DAMAGE` |
| Tier / niveau d'outil | netherite | `ToolMaterial` |

🔷 Rareté `EPIC` (nom violet), item non consumé par le feu (`fire_resistant`),
pas d'empilement. Une épée légendaire qui brûle dans la lave serait frustrante.

### 2.2 Réparation ✅

- **Autorisée** par combinaison de **deux Master Swords** : enclume, table de
  craft, meule.
- **Interdite** par matériau : aucun lingot, netherite ou autre item ne répare
  l'épée.

Concrètement, ne pas déclarer de `repair_ingredient` suffit : les trois
mécaniques de combinaison de deux items identiques ne passent pas par
l'ingrédient de réparation. Il n'y a donc **rien à bloquer** — c'est une
simplification par rapport à la version précédente de cette spec, qui interdisait
toute réparation et demandait un mixin sur l'enclume.

Trois conséquences du comportement vanilla, à vérifier en jeu et à accepter ou
corriger :

- La **meule** retire les enchantements en réparant. C'est vanilla ; comme
  l'épée porte potentiellement tous les enchantements d'épée (§2.4), l'échange
  est déjà punitif en soi. 🔷 On laisse tel quel.
- La **table de craft** retire aussi les enchantements et donne le bonus de
  durabilité habituel (+5 %).
- L'**enclume** conserve et fusionne les enchantements, et applique son coût en
  niveaux croissant, jusqu'au fameux « Trop coûteux ! ».

Réparer suppose de posséder **deux** épées, donc d'avoir trouvé deux structures
(§4.1). La réparation reste rare par construction ; la voie normale est la
régénération temporelle (§2.3).

🔷 Détail à traiter à l'implémentation : quand deux épées fusionnent, le
composant de régénération (§2.3) doit être recombiné. On garde le **plus récent**
des deux « derniers coups portés » — le choix conservateur, sinon fusionner une
épée fraîchement utilisée avec une épée au repos effacerait la pénalité.

### 2.3 Régénération de durabilité ✅

- L'épée récupère **toute** sa durabilité après **2 jours + 2 nuits** écoulés
  *sans usage au combat*.
- Le sommeil en lit compte dans le décompte (donc on suit le temps du monde, pas
  le temps réel ni le nombre de ticks joués).
- Toute utilisation au combat remet le compteur à zéro.
- La régénération est **progressive**, pas un palier : la durabilité remonte en
  continu à mesure que le temps passe.

Modèle retenu :

- 2 jours + 2 nuits = **2 cycles complets** = **48 000 ticks** de temps de monde
  (configurable).
- On stocke sur l'`ItemStack`, dans un composant custom, le temps de monde
  (`level.getGameTime()`) du dernier coup porté. Le sommeil avance
  `getGameTime()` d'un coup, donc la règle « le lit compte » tombe gratuitement.
- Durabilité rendue = `maxDamage × (ticks écoulés / 48 000)`, plafonnée à neuf.
  Soit ~1 point toutes les 24 ticks pour une épée à 2031 de durabilité.
- Recalcul paresseux dans `inventoryTick` (ou son équivalent 26.2), pas de tâche
  planifiée. L'épée régénère donc **dans l'inventaire du joueur** ; 🔷 elle
  régénère aussi posée dans un coffre, puisque le calcul est différentiel et se
  rattrape à la prochaine lecture.
- « Usage au combat » = dégât infligé à une entité (`hurtEnemy` / équivalent).
  🔷 Casser un bloc avec l'épée ne compte pas comme combat, mais consomme quand
  même de la durabilité — cohérent avec vanilla.
- ❓ La vague de lumière (§3) qui touche une cible remet-elle le compteur à
  zéro ? 🔷 Oui : c'est un usage au combat.

### 2.4 Enchantements ✅

- Tous les enchantements d'épée sont applicables **simultanément**, y compris
  ceux que vanilla déclare mutuellement exclusifs (Sharpness + Smite + Bane of
  Arthropods, et tout autre groupe d'exclusivité touchant l'épée).
- Enchantable **à la table d'enchantement** et **à l'enclume** (livre + épée),
  sans restriction.

🔷 Mise en œuvre : les exclusivités vanilla sont des tags
(`data/minecraft/tags/enchantment/exclusive_set/...`). On ne peut pas les vider
sans casser toutes les autres épées. Il faut donc un point d'accroche qui teste
« la pile est-elle une Master Sword ? » avant d'appliquer l'exclusivité —
vraisemblablement un mixin sur la vérification de compatibilité. **Emplacement
exact à trouver par sous-agent** (`javap` / `genSources`), pas de mémoire.

À couvrir aussi : la table d'enchantement propose des lots d'enchantements
compatibles entre eux ; lever l'exclusivité côté compatibilité ne garantit pas
que la table en propose plusieurs d'un coup. Le résultat attendu — les cumuler
via des livres à l'enclume — fonctionnera de toute façon.

---

## 3. Attaque chargée — vague de lumière ✅

Quand le joueur frappe avec la barre d'attaque pleine, l'épée projette une vague
de lumière devant lui.

| Paramètre | Défaut | Statut |
| --- | --- | --- |
| Condition | barre d'attaque à 100 % | ✅ |
| Cooldown | **15 s** (300 ticks) | ✅ minimum 15 s |
| Condition de durabilité | **aucune** | ✅ |
| Portée | 12 blocs | 🔷 |
| Vitesse | 1,2 bloc/tick | 🔷 |
| Dégâts | 60 % des dégâts de mêlée de l'épée | 🔷 |
| Largeur de la vague | ~1,5 bloc, traverse plusieurs entités | 🔷 |
| Coût en durabilité | 1 point, en plus du coup lui-même | 🔷 |

🔷 Le cooldown s'affiche avec la surcouche de cooldown vanilla sur l'icône de
l'item (`ItemCooldowns`), pour qu'il soit lisible sans deviner.

🔷 Implémentation : une entité projectile custom, rendue par un quad texturé
émissif plutôt qu'un modèle 3D. Elle traverse les blocs non solides, s'arrête sur
un bloc plein, et n'inflige ses dégâts qu'une fois par entité.

---

## 4. La structure

### 4.1 Répartition ✅

- Génère **uniquement** en biome Dark Forest.
- **Une seule** structure par occurrence contiguë de Dark Forest — donc une
  épée par biome, au maximum.
- Aucune autre structure à moins de **120 blocs** en surface. Les structures
  souterraines sont ignorées pour ce calcul.

Mise en œuvre : structure JSON vanilla (`worldgen/structure` + `structure_set`),
sans code pour le placement de base. **Vérifié dans le jar 26.2 le 06/09/2026 —
les JSON de worldgen sont bien présents dans `minecraft-common.jar`, sous
`data/minecraft/worldgen/`.**

#### Comment vanilla rend le manoir rare

`data/minecraft/worldgen/structure_set/woodland_mansions.json` :

```json
{
  "placement": {
    "type": "minecraft:random_spread",
    "salt": 10387319,
    "separation": 20,
    "spacing": 80,
    "spread_type": "triangular"
  },
  "structures": [{ "structure": "minecraft:mansion", "weight": 1 }]
}
```

Deux filtres se cumulent, et c'est le cumul qui fait la rareté :

1. **La grille.** Le monde est découpé en cellules de `spacing` × `spacing`
   chunks — ici **80 × 80 chunks, soit 1280 × 1280 blocs**. Chaque cellule
   produit **un seul** chunk candidat, tiré au sort dans la cellule à partir de
   la seed et du `salt`. `separation` réserve une marge de 20 chunks au bord de
   la cellule, ce qui garantit un écart minimal entre deux candidats voisins ;
   `spread_type: triangular` recentre le tirage, donc les candidats tombent
   plutôt au milieu des cellules qu'au bord.
2. **Le biome.** Le candidat n'aboutit que si son chunk est dans le tag
   `#minecraft:has_structure/woodland_mansion` = `dark_forest` + `pale_garden`.
   S'il tombe ailleurs, **la cellule ne produit rien** : pas de repli, pas de
   deuxième tirage.

D'où la réponse à « pourquoi je n'en vois jamais deux dans la même forêt » : une
cellule de 1280 blocs de côté est beaucoup plus large qu'une occurrence typique
de Dark Forest, donc une forêt tombe presque toujours dans une seule cellule, et
n'a droit qu'à un candidat. La contrepartie est que la plupart des Dark Forests
n'ont **aucun** manoir : le candidat de leur cellule est tombé dans une plaine.

Ordres de grandeur des autres `structure_set` vanilla, pour situer :

| Structure | `spacing` | `separation` |
| --- | --- | --- |
| Manoir | 80 | 20 |
| Portail en ruine | 40 | 15 |
| Village | 34 | 8 |
| Monument | 32 | 5 |
| Cité antique | 24 | 8 |

#### Ce qu'on en fait

🔷 **On calque le manoir, en décalant légèrement : `spacing: 72`,
`separation: 20`, `spread_type: triangular`, `salt` différent.** C'est le
réglage qui produit le comportement observé en jeu (« jamais deux dans la même
forêt »), donc le plus sûr moyen d'obtenir « une par biome maximum ».

Pourquoi 72 et non 80 : à `spacing` identique, nos cellules s'aligneraient
exactement sur celles du manoir, et `triangular` recentre les deux tirages vers
le milieu de la cellule. Les deux candidats seraient donc corrélés en position,
et l'exclusion (ci-dessous) annulerait souvent l'épée dans les forêts à manoir —
exactement là où on aimerait la trouver. 72 désynchronise les grilles à densité
comparable.

**Ajouter ce `structure_set` ne retire aucun manoir.** Deux `structure_set` sont
deux grilles indépendantes : le chunk candidat se calcule à partir de la seed,
du `salt`, de `spacing` et de `separation` de ce set-là, et de rien d'autre. Le
set vanilla `woodland_mansions` n'a pas de champ `exclusion_zone` et ne
référence rien de notre mod ; retirer le mod laisserait les manoirs aux mêmes
coordonnées.

Conséquence à assumer, c'est le mot *maximum* de la spec qui l'autorise : la
plupart des Dark Forests n'auront pas d'épée. Baisser `spacing` dans la config
(§6) la rend plus fréquente, au prix du risque d'en voir deux dans une grande
forêt. 🔷 `spacing` reste donc configurable, avec 80 par défaut.

#### Les 120 blocs : `exclusion_zone` existe en vanilla

C'est le point que je donnais pour le plus risqué. Il l'est beaucoup moins : le
format expose déjà un champ `exclusion_zone`, utilisé par les avant-postes
pillards pour se tenir à l'écart des villages.

```java
public record ExclusionZone(Holder<StructureSet> otherSet, int chunkCount)
// chunkCount : Codec.intRange(1, 16)
```

**L'exclusion est à sens unique** : `applyInteractionsWithOtherStructures` est
évaluée sur *notre* placement et ne peut interdire que *notre* chunk. Quand les
deux candidats sont trop proches, c'est l'épée qui cède et le manoir qui passe.
Même schéma qu'en vanilla, où les avant-postes s'écartent des villages sans
jamais empêcher un village d'apparaître.

Trois limites, vérifiées dans les sources décompilées :

- `chunkCount` est plafonné à **16 chunks (256 blocs)**. Nos 120 blocs = 7,5
  chunks → `chunk_count: 8` (128 blocs), ça passe largement.
- Le champ ne prend **qu'un seul** `other_set`. Pas de liste.
- Le record est marqué **`@Deprecated`** en 26.2. Toujours fonctionnel et
  toujours utilisé par vanilla, mais susceptible de bouger.

Or, en Dark Forest, **seules deux structures vanilla peuvent apparaître en
surface** (vérifié en listant les 34 tags `has_structure` qui contiennent
`dark_forest`, `#is_forest` ou `#is_overworld`) :

| Structure | Surface ? | Traitement |
| --- | --- | --- |
| Manoir | oui | à exclure |
| Portail en ruine (`#is_forest`) | oui | à exclure |
| Mine abandonnée | non | ignoré par la spec |
| Forteresse (stronghold) | non | ignoré par la spec |
| Trial chamber | non | ignoré par la spec |

Il en faut donc **deux**, et vanilla n'en donne qu'une. 🔷 Deux options :

- **A — tout en JSON.** `exclusion_zone` sur `minecraft:woodland_mansions`
  seulement, et on accepte qu'un portail en ruine puisse être à moins de 120
  blocs. Zéro code. Le portail en ruine est un petit décor, la gêne est faible.
- **B — placement custom.** Un `StructurePlacementType` custom qui étend le
  `random_spread` avec une **liste** de zones d'exclusion et une distance en
  blocs plutôt qu'en chunks. Vérifié faisable :
  `ChunkGeneratorStructureState#hasStructureChunkInRange(Holder<StructureSet>, int, int, int)`
  est **public**, donc appelable depuis un mod sans mixin, et
  `StructurePlacementType` est une interface publique à `MapCodec`. C'est aussi
  ce qui permettrait de lire `spacing` depuis la config (§6, limite n° 3).

🔷 Recommandation : **A pour l'étape 8, B si le test en jeu montre que ça gêne.**
Commencer par du JSON pur, et ne passer au code que si nécessaire.

### 4.2 Forme ✅ (« bloc ou demi-bloc + petit décor »)

🔷 Proposition :

- Un **socle** : bloc custom `master_sword_pedestal`, en pierre sombre, forme de
  demi-dalle épaisse avec une fente centrale. Il porte un `BlockEntity` qui
  connaît l'état de l'épée.
- L'épée est **rendue par le BlockEntity** (ni cadre d'item, ni entité posée) :
  plantée verticalement, légère oscillation, quelques particules.
- Décor : 5×5 à 7×7 blocs — racines, mousse, pierres moussues, un soupçon de
  lumière au sol. Une structure NBT posée par un `single_pool_element`, pour
  rester éditable en jeu au block-editor.

### 4.3 Retrait et remise de l'épée ✅

- Clic droit sur le socle à main vide → l'épée est retirée et donnée au joueur.
  **Aucune condition** : pas de niveau requis, pas de quête, pas de prérequis.
- Le brouillard disparaît **définitivement** à ce moment (§5).
- L'épée peut être **remise** dans le socle (clic droit avec l'épée en main).
  Elle s'y replante et se rend visible, mais **le brouillard ne revient pas**.
- Le socle reste en place dans tous les cas, vide ou occupé.

🔷 Conséquence : l'état persistant du socle a besoin de **deux** informations
distinctes — « une épée est-elle posée ? » et « le brouillard a-t-il déjà été
consommé ? ». La seconde est irréversible.

🔷 Le socle est cassable à la pioche et se ramasse. S'il est cassé alors que
l'épée est dedans, l'épée tombe au sol. Le drapeau « brouillard consommé » suit
alors le monde, pas le bloc (§5).

---

## 5. Le brouillard ✅

- Présent **uniquement** tant que l'épée n'a jamais été retirée de sa structure.
- S'intensifie à l'approche. Rayon extérieur : **50 blocs**.
- Disparaît **définitivement** au premier retrait — remettre l'épée ne le
  ramène pas.

🔷 Proposition :

- Effet purement client, calculé à chaque frame : distance du joueur au socle
  actif le plus proche → densité de brouillard interpolée. À 50 blocs, 0 % ; à
  **10 blocs**, densité maximale. 🔷 Courbe quadratique, plus dramatique que
  linéaire. Les deux rayons sont configurables.
- 🔷 Teinte gris-vert froide, légèrement lumineuse, cohérente avec le Dark
  Forest.
- Le client doit connaître la position et l'état des socles : le `BlockEntity`
  synchronise son état, et le client tient la liste des socles des chunks
  chargés. 50 blocs tiennent dans 4 chunks, donc le chargement normal suffit à
  toute distance de rendu jouable — c'est le bénéfice secondaire du passage de
  100 à 50 blocs, plus besoin d'un paquet de synchronisation à longue portée.
- L'état « brouillard consommé » est persistant côté monde : dans le
  `BlockEntity`, **et** dans une `SavedData` globale, puisque le socle peut être
  cassé et reposé (§4.3).

---

## 6. Configuration ✅

Un fichier de configuration permet d'ajuster les stats, les zones de spawn, le
brouillard, etc.

🔷 Proposition :

- **`config/mastersword.json`**, écrit à la main via un `Codec`, sans
  dépendance externe (ni Cloth Config, ni owo, ni MidnightLib) : une dépendance
  de plus à vérifier et à faire suivre à chaque version de Minecraft, pour un
  fichier plat d'une trentaine de valeurs.
- Généré avec ses valeurs par défaut et des commentaires au premier lancement.
- Rechargeable par commande `/mastersword reload` (permission opérateur), pour
  ce qui peut l'être — voir les limites ci-dessous.

Découpage prévu :

| Section | Contenu |
| --- | --- |
| `item` | dégâts, vitesse d'attaque, durabilité max, durée de régénération complète (ticks), réparation activée |
| `light_wave` | activée, cooldown, portée, vitesse, ratio de dégâts, largeur, coût en durabilité |
| `structure` | activée, `spacing`, `separation`, distance minimale aux autres structures (120), biome cible |
| `fog` | activé, rayon extérieur (50), rayon de densité maximale (10), couleur, intensité maximale |

**Trois limites à connaître avant de promettre « tout est configurable à
chaud »** — à confirmer par sous-agent, mais elles sont documentées dans
`../SETUP-MC-MODDING.md` §8 :

1. **La durabilité max est un composant de données** (`DataComponents.MAX_DAMAGE`),
   figé sur la pile au moment de sa création. Une valeur de config ne la suit pas
   à chaud sans mixin sur `ItemStack#getMaxDamage()`. 🔷 On accepte le mixin :
   c'est justement le genre de valeur qu'on veut pouvoir régler.
2. **Les attributs** (dégâts, vitesse) sont posés à l'enregistrement de l'item,
   donc lus une fois au démarrage. 🔷 Modifiables par config, appliqués au
   **redémarrage** — pas de `/reload`.
3. **La worldgen est data-driven** : `spacing` / `separation` sont lus dans le
   JSON au chargement du monde. Les rendre configurables suppose soit un
   `StructurePlacement` custom qui lit la config (cohérent avec ce que demande
   déjà §4.1), soit d'accepter que ces valeurs ne s'appliquent qu'aux **chunks
   non encore générés**. Dans tous les cas, changer ces valeurs sur un monde
   existant ne déplace rien de déjà généré.

🔷 Portée serveur / client : `item`, `light_wave` et `structure` font autorité
côté serveur ; `fog` est purement client (chacun règle sa lisibilité). En
multijoueur, le client n'impose rien sur le gameplay.

---

## 7. Décisions techniques

### 7.1 Identité

| Élément | Valeur | Statut |
| --- | --- | --- |
| Mod id | `mastersword` | ✅ |
| Nom affiché | The Master Sword | ✅ |
| Package racine | `re.jerome.mastersword` | ✅ |
| `group` Gradle | `re.jerome` | ✅ |
| `rootProject.name` | `mastersword` | ✅ |
| Version initiale | `0.1.0` | ✅ |
| Licence | **MIT**, `Copyright (c) 2026 Jerome` | ✅ |

`re.jerome` plutôt que `fr.jerome` : c'est la convention déjà en place dans
Argilus (`re.jerome.argilus`).

Identifiants prévus : `mastersword:master_sword` (item),
`mastersword:master_sword_pedestal` (bloc + BlockEntity),
`mastersword:light_wave` (entité), `mastersword:master_sword` (structure).

### 7.2 Versions ✅

Vérifiées le 06/09/2026 sur la branche **`26.2`** de `FabricMC/fabric-example-mod`
(sa branche par défaut ; `main` n'existe pas), sur `meta.fabricmc.net` et sur le
Maven Fabric.

```properties
minecraft_version=26.2
loader_version=0.19.5
loom_version=1.17.19
fabric_api_version=0.159.0+26.2
```

- `loader_version` et `fabric_api_version` sont exactement ceux de l'example mod
  amont ; 0.19.5 est la dernière version de loader marquée `stable` (0.19.4 ne
  l'est pas).
- `loom_version` : l'example mod écrit `1.17-SNAPSHOT`, à ne pas recopier (c'est
  le dépôt de dev de FabricMC). La dernière stable publiée est **1.17.20**, mais
  on reste sur **1.17.19**, déjà éprouvée sur cette machine.

Gradle 9.5.1, JDK 25, pas de `mappings(...)` (Minecraft est désobfusqué depuis
26.1), plugin `net.fabricmc.fabric-loom`.

### 7.3 Architecture 🔷

Source sets séparés (`splitEnvironmentSourceSets()`) : le code client vit dans
`src/client`, invisible depuis `src/main`. Le compilateur attrape donc toute
classe client qui fuirait vers le code commun ou serveur, et Loom marque ces
classes `Fabric-Loom-Client-Only-Entries` dans le jar.

```
src/main/java/re/jerome/mastersword/
├─ MasterSwordMod            point d'entrée commun (ModInitializer) ✔ créé
├─ config/                   MasterSwordConfig (Codec), chargement, /reload
├─ registry/                 ModItems, ModBlocks, ModBlockEntities,
│                            ModEntities, ModComponents, ModParticles
├─ item/MasterSwordItem
├─ block/PedestalBlock, PedestalBlockEntity
├─ entity/LightWaveEntity
├─ worldgen/                 StructurePlacement custom (le reste en JSON data/)
└─ mixin/                    exclusivité d'enchantements, getMaxDamage

src/client/java/re/jerome/mastersword/client/
├─ MasterSwordClient         point d'entrée client (ClientModInitializer) ✔ créé
├─ PedestalRenderer
├─ LightWaveRenderer
└─ FogHandler
```

Enregistrements dans des classes à **initialisation statique** — le mémo §8
insiste sur l'ordre d'initialisation, et une particule doit être enregistrée des
deux côtés.

### 7.4 À vérifier avant d'écrire du code

Aucun de ces points ne doit être codé de mémoire. Sous-agent + `javap` +
`genSources`, comme le veut `CLAUDE.md`.

1. Stats exactes de `NETHERITE_SWORD` en 26.2 et forme de `ToolMaterial`.
2. Comment déclarer un `DataComponentType` custom et le persister sur un
   `ItemStack` ; comment vanilla fusionne les composants à l'enclume (§2.2).
3. Le hook d'attaque côté item en 26.2 (`hurtEnemy` existe-t-il encore ? sous
   quelle signature ?), comment lire la charge d'attaque du joueur, et l'API de
   cooldown d'item.
4. Où vanilla teste l'exclusivité entre enchantements.
5. Que l'absence de `repair_ingredient` n'empêche **pas** la combinaison de deux
   items identiques à l'enclume, à la meule et à la table de craft (§2.2).
6. ~~Format 26.2 de `structure_set` et espacement inter-sets~~ — **fait le
   06/09/2026**, voir §4.1. Reste à vérifier : le format de `template_pool` et
   la pose d'une structure NBT en 26.2.
7. L'API de rendu du brouillard côté client en 26.2 (elle bouge souvent) et la
   façon propre de la moduler depuis un mod.
8. Rendu d'un `BlockEntity` et d'une entité projectile plate — le mémo signale
   qu'un `RenderPipeline` n'a pas besoin d'être enregistré.
9. `SavedData` en 26.2 : signature, `Codec`, et enregistrement par dimension.

---

## 8. Plan d'implémentation 🔷

Chaque étape se termine par un build vert et, quand c'est visible, un test en
jeu. Sous-agent de vérification avant chaque commit.

| # | Étape | Contenu | État |
| --- | --- | --- | --- |
| 1 | Squelette | wrapper Gradle copié, `fabric.mod.json`, `LICENSE` MIT, `.gitattributes`, build vide qui se lance | **fait** 06/09/2026 |
| 2 | L'item nu | épée aux stats netherite, texture, modèle, réparation par combinaison vérifiée | à faire |
| 3 | Config | fichier JSON, `Codec`, chargement, `/mastersword reload`, mixin `getMaxDamage` | à faire |
| 4 | Régénération | composant custom, décompte du temps de monde, remise à zéro au combat, fusion à l'enclume | à faire |
| 5 | Enchantements | mixin d'exclusivité, test Sharpness + Smite | à faire |
| 6 | Vague de lumière | entité, rendu, dégâts, cooldown 15 s, équilibrage | à faire |
| 7 | Socle | bloc, BlockEntity, rendu de l'épée plantée, retrait et remise | à faire |
| 8 | Génération | structure NBT, `structure_set` calqué sur le manoir, `exclusion_zone` | à faire |
| 9 | Brouillard | synchro serveur → client, courbe 50 → 10 blocs, disparition définitive | à faire |
| 10 | Finitions | sons, particules, advancement de retrait, traductions fr/en | à faire |

La config passe en étape 3, avant tout ce qui a des valeurs à régler : la
brancher après coup obligerait à repasser sur chaque fichier.

---

## 9. Questions ouvertes ❓

| # | Question | Impact |
| --- | --- | --- |
| 1 | Textures faites main, ou placeholders en attendant ? | étape 2 |
| 2 | Une vague de lumière qui touche une cible remet-elle le compteur de régénération à zéro ? 🔷 oui | §2.3 |
| 3 | Perdre les enchantements à la meule / table de craft est-il acceptable ? 🔷 oui, c'est vanilla | §2.2 |
| 4 | Le dépôt est-il destiné à être publié (GitHub, Modrinth) ? | README, bloc `contact` de `fabric.mod.json`, icône du mod |

---

## 10. Journal des décisions

| Date | Décision |
| --- | --- |
| 06/09/2026 | Spécification initiale rédigée. `CLAUDE.md` allégé : les specs vivent ici. |
| 06/09/2026 | **Étape 1 faite.** Squelette Fabric 26.2 qui build (`BUILD SUCCESSFUL`, `mastersword-0.1.0.jar`). Package `re.jerome.mastersword` et `group=re.jerome`, alignés sur Argilus. Source sets séparés `src/main` / `src/client`. Versions revérifiées à la source : loader **0.19.5**, fabric-api **0.159.0+26.2**, loom 1.17.19. Licence MIT `Copyright (c) 2026 Jerome`. Dépôt git local initialisé, sans remote. |
| 06/09/2026 | `spacing` fixé à **72** et non 80 : à `spacing` égal, nos cellules s'alignent sur celles du manoir et `triangular` corrèle les deux tirages, ce qui ferait annuler l'épée par l'exclusion précisément dans les forêts à manoir. Confirmé au passage que l'exclusion est à sens unique et qu'aucun manoir n'est perdu. |
| 06/09/2026 | Génération élucidée sur le jar 26.2 (§4.1). La rareté du manoir vient de `spacing: 80` (cellules de 1280 blocs) **cumulé** au filtre de biome : on calque ce réglage, avec un `salt` différent. `exclusion_zone` existe en JSON vanilla (plafond 16 chunks, un seul `other_set`, `@Deprecated`) et couvre les 120 blocs sans code. Seules deux structures de surface partagent le Dark Forest : manoir et portail en ruine. Le `StructurePlacement` custom passe de « probablement nécessaire » à « repli si le test en jeu déçoit ». |
| 06/09/2026 | Relecture Jérôme. **Réparation autorisée** par combinaison de deux épées (enclume, meule, craft), interdite par matériau — remplace l'interdiction totale, et supprime le mixin d'enclume prévu. Enchantement table + enclume. Régénération **progressive**. Vague de lumière : **cooldown 15 s**, plus aucune condition de durabilité. Retrait sans condition ; remise possible, brouillard définitivement perdu. Licence **MIT**. Une épée par biome max. Distance aux autres structures **300 → 120 blocs**. Brouillard **100 → 50 blocs**. Ajout d'un **fichier de configuration** (§6) et de l'étape 3 correspondante. |
