# The Master Sword — spécification

Document vivant. Il décrit **ce qu'on construit** et **les décisions prises**.
Les instructions de travail permanentes sont dans `CLAUDE.md`, les pièges de la
machine et de Minecraft 26.2 dans `../SETUP-MC-MODDING.md`.

Statut : **le plan est terminé — étapes 1 à 10 faites.** L'épée, sa
configuration, sa régénération, le cumul d'enchantements, la vague de lumière, le
socle, la génération, le brouillard et les finitions. La suite est une v2, dont
les intentions sont notées en §4.1 et §4.2.
Dernière mise à jour : 12/09/2026.

Conventions de ce fichier :

- ✅ **Confirmé** — validé par Jérôme, ne pas changer sans le lui demander.
- 🔷 **Proposé** — choix de Claude Code, modifiable librement, à valider en passant.
- ❓ **Ouvert** — décision à prendre, listée en §9.

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

Valeurs confirmées le 06/09/2026 sur le jar 26.2. Elles sont toutes posées d'un
coup par `new Item.Properties().sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)`, qui
est public — inutile de les recopier à la main.

| Propriété | Valeur | Composant |
| --- | --- | --- |
| Dégâts d'attaque | **7.0** (3.0 de base + 4.0 du netherite) → 8 affiché | `ATTRIBUTE_MODIFIERS` |
| Vitesse d'attaque | **-2.4** → 1,6 attaque/s | `ATTRIBUTE_MODIFIERS` |
| Durabilité max | **2031** | `MAX_DAMAGE` |
| Enchantabilité | **15** | `ENCHANTABLE` |
| Dégâts par coup | 1 | `WEAPON` |
| Minage | toiles 15.0, `SWORD_EFFICIENT` 1.5, 2 de durabilité par bloc | `TOOL` |

Il n'existe plus de classe `SwordItem` en 26.2 : une épée est un `Item` nu dont
les `Properties` portent tous les composants.

Ajouts propres au mod : rareté `EPIC` (nom violet) et `fireResistant()` — comme
l'épée en netherite vanilla, qui a déjà cette dernière. Pas d'empilement, posé
d'office par `durability(...)`.

#### La texture — contrat à respecter

**Jérôme dessine la version définitive.** Celle en place est la mienne, tirée de
son proto ; elle tient le rôle en attendant. Pour la remplacer, il suffit
d'écraser le fichier — aucun code ni JSON à toucher.

| Contrainte | Valeur |
| --- | --- |
| Chemin | `src/main/resources/assets/mastersword/textures/item/master_sword.png` |
| Taille | 64×64 (carré ; 16×16 ou 32×32 marchent aussi) |
| Format | PNG RGBA, fond transparent |
| **Orientation** | **diagonale, pointe en haut à droite** |

L'orientation n'est pas une préférence : `minecraft:item/handheld` ajoute une
rotation Z de **55°** en `thirdperson_righthand` et **25°** en
`firstperson_righthand`. Les sprites vanilla sont dessinées à ~45°, et c'est la
somme qui donne une épée bien tenue. Une sprite verticale finit vers 145°,
pointée par-dessus l'épaule — il faudrait alors un modèle custom avec ses
propres `display`, au lieu d'hériter de `handheld`.

À savoir : 64×64 est quatre fois la résolution des items vanilla, donc l'épée
paraît plus finement détaillée que ses voisines dans l'inventaire.

### 2.2 Réparation ✅

- **Autorisée** par combinaison de **deux Master Swords** : enclume, table de
  craft, meule.
- **Interdite** par matériau : aucun lingot, netherite ou autre item ne répare
  l'épée.

**Implémenté à l'étape 2.** La mise en œuvre est plus subtile que « ne rien
déclarer », parce que `.sword(ToolMaterial.NETHERITE, ...)` pose lui-même
`REPAIRABLE` sur le tag du lingot de netherite, via `applyCommonProperties`.

La solution retenue : `component()` chaîne des `components.set(type, value)`,
donc **le dernier appel gagne**. On écrase avec un ensemble vide, ce qui rend
`Repairable.isValidRepairItem` faux pour tout matériau :

```java
.component(DataComponents.REPAIRABLE, new Repairable(HolderSet.empty()))
```

Les trois mécaniques de combinaison de deux exemplaires, elles, ne consultent
jamais `REPAIRABLE` — vérifié dans `AnvilMenu.createResult()`,
`GrindstoneMenu.mergeItems()` et `RepairItemRecipe.canCombine()`, qui ne testent
que « même item » plus `MAX_DAMAGE`/`DAMAGE`. Neutraliser `REPAIRABLE` interdit
donc le lingot et **rien d'autre**.

Confirmé en jeu le 06/09/2026, les quatre cas :

| Test | Résultat |
| --- | --- |
| Enclume, deux Master Swords | ✅ répare |
| Table de craft, deux Master Swords | ✅ répare |
| Meule, deux Master Swords | ✅ répare |
| Enclume, Master Sword + lingot de netherite | ✅ **refusé**, slot de résultat vide |
| *Contrôle* : enclume, épée netherite + lingot | ✅ répare toujours — vanilla intact |

Le test de contrôle compte autant que les autres : il prouve qu'on a neutralisé
la réparation par matériau **sur notre item seulement**, sans toucher au reste
du jeu.

Trois conséquences du comportement vanilla, à accepter ou corriger :

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

🔷 Détail à traiter à l'étape 4 : quand deux épées fusionnent, le composant de
régénération (§2.3) doit être recombiné. On garde le **plus récent** des deux
« derniers coups portés » — le choix conservateur, sinon fusionner une épée
fraîchement utilisée avec une épée au repos effacerait la pénalité.

**Tranché à l'étape 4 : on garde le comportement par défaut, sans aucun mixin.**

| Chemin | Ce qu'il fait de la pile | Composant de régénération |
| --- | --- | --- |
| `AnvilMenu.createResult` | `input.copy()` | celui de **gauche survit** |
| `GrindstoneMenu.mergeItems` | `input.copyWithCount(n)` | celui de **gauche survit** |
| `RepairItemRecipe.assemble` | `new ItemStack(first.getItem())` | **perdu** → absent → au repos |

`ItemStack.copy()` recopie **tous** les composants sans filtrage, donc les deux
premiers chemins conservent déjà l'horodatage de l'épée de gauche : il n'y avait
rien à faire pour eux.

Le 🔷 « garder le plus récent des deux » est **abandonné**. Le tenir exigerait
trois mixins — aucune API Fabric ne couvre la fusion d'items, les 1778 classes de
l'API ont été passées en revue — dont un visant un appel **par ordinal** dans
`AnvilMenu.createResult`, qui casserait à la prochaine version de Minecraft en
faisant crasher le jeu au démarrage (`defaultRequire: 1`). L'enjeu réel est nul :
fusionner exige deux épées, donc deux structures (§4.1), et l'opération répare
déjà la durabilité.

### 2.3 Régénération de durabilité ✅

- L'épée récupère **toute** sa durabilité après **2 jours + 2 nuits** écoulés
  *sans usage au combat*.
- Le sommeil en lit compte dans le décompte (donc on suit le temps du monde, pas
  le temps réel ni le nombre de ticks joués).
- Toute utilisation au combat remet le compteur à zéro.
- La régénération est **progressive**, pas un palier : la durabilité remonte en
  continu à mesure que le temps passe.

**Implémenté à l'étape 4.**

#### ⚠️ Correction : le sommeil n'avance pas `getGameTime()`

Ce document affirmait que « le sommeil avance `getGameTime()` d'un coup, donc la
règle *le lit compte* tombe gratuitement ». **C'était faux en 26.2.**

Le cycle jour/nuit a été sorti de `LevelData` : il vit dans un
`net.minecraft.world.clock.ServerClockManager`. Dormir appelle
`moveToTimeMarker(...)`, qui n'avance que `ClockInstance.totalTicks`, un champ
privé du gestionnaire d'horloges, disjoint de `LevelData.gameTime`.
`Level.getDayTime()` et `setDayTime()` **n'existent plus**.
`ServerLevel.tickTime()` fait `setGameTime(getGameTime() + 1)` et rien d'autre :
`getGameTime()` compte les ticks **joués** et ne saute jamais.

On suit donc **`Level.getOverworldClockTime()`**, l'horloge du monde — ce qui est
d'ailleurs la lecture littérale de cette spec, qui parle de jours et de nuits.

**`getOverworldClockTime()` et non `getDefaultClockTime()`** : ce dernier passe
par `dimensionType().defaultClock()`, un `Optional` vide dans une dimension sans
cycle. Il renverrait **0 au Nether**, et le compteur repartirait à zéro à chaque
portail. Confirmé en jeu : `/time add` au Nether répond « There is no default
clock in dimension minecraft:the_nether ». L'horloge de l'Overworld, elle,
continue d'avancer là-bas : `MinecraftServer.tickServer()` appelle
`clockManager.tick()` une fois par tick serveur, globalement, pas par dimension.

Trois conséquences assumées : l'horloge est **pausable** (gamerule
`doDaylightCycle` — pas de jour qui passe, pas de régénération), elle n'est **pas
monotone** (`/time set` peut la faire reculer, d'où un clamp), et elle peut avoir
un `rate` ≠ 1.

#### Le modèle

- **La guérison ne démarre qu'en dessous de la moitié de la durabilité**
  (`regen_starts_below`, défaut 0.5) — ajouté après essai en jeu, la remontée
  permanente se voyait trop. Au-dessus du seuil l'épée s'use comme n'importe
  quelle autre ; en dessous, elle se met à guérir et remonte **jusqu'à 100 %**.
  Le seuil est un déclencheur, pas un plafond.

  ⚠️ Subtilité qui rend le seuil réel : le composant sert aussi de drapeau
  « guérison engagée ». `markCombatUse` ne réécrit donc l'horodatage **que si le
  composant existe déjà** — sinon frapper un mob avec une épée à 90 % l'armerait
  et relancerait la guérison en contournant le seuil.
- 2 jours + 2 nuits = **2 cycles** = **48 000 ticks d'horloge**, configurable par
  `full_regen_days` (défaut 2.0). Un jour = `SharedConstants.TICKS_PER_GAME_DAY`
  = 24 000.
- Composant custom `mastersword:last_combat_use`, un `long` : l'instant
  d'horloge du dernier coup porté. **Absent = épée au repos.**
- Durabilité rendue = `maxDamage × écoulé / 48 000`, soit **1 point toutes les
  24 ticks** d'horloge pour une épée à 2031. Progressif, pas de palier.

  ⚠️ Le temps converti en durabilité est décompté **arrondi au plafond**. Un
  point coûte `48 000 / 2031 = 23,63` ticks : arrondir au plancher rendrait
  0,63 tick de crédit à chaque point, sans jamais le reprendre.

  **L'erreur est bornée par le coût d'un point.** Elle reste donc négligeable
  tant qu'un point coûte beaucoup de ticks, et enfle quand il n'en coûte qu'un
  ou deux. Mesuré par simulation tick par tick :

  | `max_durability` / `full_regen_days` | ticks par point | plancher | plafond |
  | --- | --- | --- | --- |
  | 2031 / 2 j — **les défauts** | 23,6 | −2,68 % | **+1,55 %** |
  | 2031 / 1 j | 11,8 | −6,91 % | +1,55 % |
  | 2031 / 0,2 j | 2,4 | −15,35 % | +26,94 % |
  | 2031 / 0,1 j | 1,2 | −15,33 % | +69,25 % |
  | 65535 / 1,5 j | 0,55 | −39,32 % | +82,04 % |
  | 65535 / 0,1 j | 0,04 | −49,38 % | +1,17 % |

  Aucun des deux arrondis n'est bon partout — le plafond est même *pire* que le
  plancher dans la bande 1–3 ticks par point. Le plafond a été retenu parce
  qu'il se trompe **en ralentissant**, jamais en accélérant, et qu'il est juste
  là où ça compte : aux valeurs par défaut. Règle sûre : garder
  `max_durability ≤ full_regen_ticks / 20` maintient le biais sous 5 % (les
  défauts sont à 23,6, donc largement dedans).

  Supprimer complètement la dérive demanderait de stocker aussi la durabilité de
  départ — deux champs au lieu d'un, ou un horodatage encodant « quand l'épée
  sera pleine ». Aux valeurs par défaut ça représente 37 secondes réelles sur
  40 minutes de jeu : non rentable pour l'instant, mais c'est la voie si les
  configurations rapides devaient être supportées proprement.
- Recalcul paresseux dans `inventoryTick(ItemStack, ServerLevel, Entity,
  EquipmentSlot)` — **serveur uniquement**, le type du paramètre le garantit.
  Une épée dans un coffre ne tick pas, mais le calcul étant différentiel elle
  rattrape tout son retard au premier tick après avoir été reprise. Vérifié en
  jeu.
- On ne réécrit l'horodatage que **quand on a soigné quelque chose**, et
  seulement du temps réellement converti : réécrire à chaque tick perdrait le
  reste de la division et resynchroniserait la pile au client 20 fois par
  seconde.
- **Le composant est retiré à durabilité pleine.** Sans ça, une épée réparée
  puis laissée dix jours dans un coffre banquerait dix jours de crédit et se
  régénérerait d'un coup à la première éraflure.
- « Usage au combat » = `postHurtEnemy`, appelé après application des dégâts.
  Casser un bloc n'est pas du combat mais consomme quand même de la durabilité —
  cohérent avec vanilla, et vérifié en jeu.
- **Tirer** la vague de lumière (§3) remet le compteur à zéro, qu'elle touche ou
  non : c'est un usage au combat, et elle coûte déjà un point de durabilité.

### 2.4 Enchantements ✅

- Tous les enchantements d'épée sont applicables **simultanément**, y compris
  ceux que vanilla déclare mutuellement exclusifs (Sharpness + Smite + Bane of
  Arthropods, et tout autre groupe d'exclusivité touchant l'épée).
- Enchantable **à la table d'enchantement** et **à l'enclume** (livre + épée),
  sans restriction.

**Prérequis posé à l'étape 2 : l'épée doit être dans `#minecraft:swords`.**
C'est le seul tag vanilla qui contienne `netherite_sword`, et **tous** les
enchantements d'épée y aboutissent par la chaîne `enchantable/*` — vérifié
fichier par fichier dans le jar :

| Enchantement | `supported_items` | résout vers |
| --- | --- | --- |
| Sharpness | `#enchantable/sharp_weapon` | → `melee_weapon` → `#swords` |
| Smite, Bane of Arthropods | `#enchantable/weapon` | → `sharp_weapon` → `#swords` |
| Looting, Knockback | `#enchantable/melee_weapon` | → `#swords` |
| Fire Aspect | `#enchantable/fire_aspect` | → `melee_weapon` → `#swords` |
| Sweeping Edge | `#enchantable/sweeping` | → `#swords` |
| Unbreaking, Mending | `#enchantable/durability` | → `#swords` |

Le composant `ENCHANTABLE` (15) ne suffit **pas** : il donne l'enchantabilité,
pas l'éligibilité, qui passe par `Enchantment.canEnchant` →
`supportedItems().contains(...)`. Sans le tag, l'épée n'accepterait aucun
enchantement, et le mixin d'exclusivité de l'étape 5 travaillerait dans le vide.

Le même tag conditionne l'**attaque tournoyante** : `Player` teste
`getItemInHand(MAIN_HAND).is(ItemTags.SWORDS)`. Sans lui, §2.1 « stats
identiques à l'épée netherite » serait faux.

D'où le fichier `src/main/resources/data/minecraft/tags/item/swords.json`
(`tags/item/` au **singulier**). Les tags fusionnent entre packs par défaut :
déclarer notre seule épée ne remplace pas la liste vanilla.

#### Le cumul — implémenté à l'étape 5

**L'exclusivité est portée par l'enchantement, jamais par l'item :**

```java
public static boolean areCompatible(Holder<Enchantment> enchantment, Holder<Enchantment> other) {
    return !enchantment.equals(other)
        && !enchantment.value().exclusiveSet.contains(other)
        && !other.value().exclusiveSet.contains(enchantment);
}
```

Statique, deux `Holder`, **aucun `ItemStack`**. Aucune API Fabric ne permet de la
lever par item — recherche exhaustive sur les 61 jars : `EnchantmentEvents.MODIFY`
peut bien réécrire l'`exclusiveSet` via `builder.exclusiveWith(...)`, mais il est
déclenché une fois par enchantement au chargement des registres et produit un
`record` immuable partagé par tout le jeu. Il lèverait la règle pour **toutes**
les épées, exactement comme le ferait un datapack surchargeant
`tags/enchantment/exclusive_set/damage.json`.

D'où trois mixins, aux trois seuls appelants de `areCompatible` du jeu entier :

| Mixin | Couvre | La pile vient de |
| --- | --- | --- |
| `AnvilMenuMixin` | enclume | `getSlot(INPUT_SLOT)` — API publique, pas de `@Local` |
| `EnchantmentHelperMixin` | table, `EnchantWithLevelsFunction`, `EnchantmentsByCost`, `enchantItem` | paramètre de `selectEnchantment` |
| `EnchantCommandMixin` | `/enchant` | variable locale |

⚠️ **`filterCompatibleEnchantments` sert aussi à autre chose.** `areCompatible`
commence par `!enchantment.equals(other)` : elle déclare donc un enchantement
**incompatible avec lui-même**, et ce filtre est du même coup ce qui empêche la
boucle de tirage de repiocher le même. Le sauter entièrement donnerait
« Tranchant IV, Tranchant IV ». Le mixin ne l'ignore pas, il le remplace par le
retrait du seul doublon.

⚠️ Effet de bord assumé à l'enclume : renvoyer `true` fait aussi sauter le
`price++` que vanilla applique par paire incompatible. C'est correct — cette
majoration est la pénalité d'un enchantement gaspillé, or ici il est appliqué.

Aucun conflit avec la Fabric API : ses propres mixins visent `canEnchant` et
`isPrimaryItem`, des instructions différentes dans les mêmes méthodes.

Confirmé en jeu le 09/09/2026, avec le test de contrôle qui compte : la Master
Sword cumule Tranchant et Châtiment à l'enclume, à la table et par `/enchant`,
et **une épée en netherite refuse toujours**.

Deux conséquences à connaître, relevées à la relecture :

- **La levée est totale, pas ciblée.** Ce n'est pas « Tranchant + Châtiment » qui
  est débloqué, c'est *tout* groupe d'exclusivité — y compris ceux qui ne
  concernent pas une épée. Sans effet pratique aujourd'hui, mais à garder en tête
  si un enchantement moddé arrive un jour.
- **La table devient nettement plus généreuse**, au-delà de la seule règle.
  Vanilla vide le pool de candidats agressivement après chaque tirage ; désormais
  un seul élément en sort par tour. L'épée sortira couramment de la table avec
  toute la famille de dégâts d'un coup.

Le coût à l'enclume, lui, ne baisse jamais par rapport à vanilla : la majoration
sautée est remplacée par le tarif réel de l'enchantement, qui lui est supérieur
ou égal. Aucun « Trop coûteux » n'est esquivé.

---

## 3. Attaque chargée — vague de lumière ✅

Quand le joueur frappe avec la barre d'attaque pleine, l'épée projette une vague
de lumière devant lui.

**Implémentée à l'étape 6.** Elle part **à tout balayage à pleine charge, y
compris dans le vide** — c'est ce qui en fait une vraie attaque à distance.

| Paramètre | Défaut | Clé de config |
| --- | --- | --- |
| Condition | barre d'attaque à 100 % | — |
| **Joueur à pleine vie** | requis | `requires_full_health` |
| Cooldown | 15 s | `cooldown_seconds` |
| Condition de durabilité de l'épée | **aucune** | — |
| Portée | 12 blocs | `range` |
| Vitesse | 1,2 bloc/tick | `speed` |
| Dégâts | 60 % des dégâts de mêlée | `damage_ratio` |
| Largeur | 1,5 bloc, traverse plusieurs entités | `width` |
| Coût en durabilité | 1 point | `durability_cost` |

Le cooldown s'affiche tout seul en surcouche grise sur l'icône :
`getCooldowns().addCooldown(stack, ticks)` côté serveur suffit,
`ServerItemCooldowns` envoie le paquet et le HUD vanilla dessine. **Zéro code
client.**

#### ⚠️ La charge d'attaque n'est pas lisible là où on l'attend

`Player.attack(Entity)` appelle `onAttack()` — donc `attackStrengthTicker = 0` —
**avant** de calculer `fullStrengthAttack`, et bien avant de passer la main à
l'item :

| Ligne | |
| --- | --- |
| 950 | `attackStrengthScale = getAttackStrengthScale(0.5F)` |
| **953** | **`onAttack()` → le compteur est remis à zéro** |
| 956 | `fullStrengthAttack = attackStrengthScale > 0.9F` |
| 988 | `itemAttackInteraction(...)` → d'où partent `hurtEnemy` / `postHurtEnemy` |

Depuis `postHurtEnemy`, `getAttackStrengthScale(0.5F)` renvoie ~**0,04** pour une
épée à 1,6 de vitesse d'attaque : le test `> 0.9F` y serait **toujours faux**.

D'où **deux** points d'accroche, parce que les deux gestes empruntent des chemins
serveur différents :

| Geste | Paquet | Mixin | Charge au moment du hook |
| --- | --- | --- | --- |
| Balayage à vide | `ServerboundSwingPacket` | `ServerPlayerMixin` sur `swing(...)` **HEAD** | encore lisible — le reset est à la fin de la méthode |
| Balayage qui touche | `ServerboundAttackPacket`, traité **avant** le swing | `PlayerAttackMixin`, `@Local(index = 7) fullStrengthAttack` | déjà nulle, d'où l'emprunt du local vanilla |

Ils ne peuvent pas tirer deux fois pour un même clic : quand l'attaque touche, le
paquet d'attaque est traité en premier et remet le compteur à zéro, donc le
paquet de swing qui suit voit une charge nulle. Le cooldown sert de seconde
barrière.

`@Local(index = …)` et non `ordinal` : le slot 8 est réutilisé par deux
variables et le slot 13 par deux autres, ce qui rend le comptage par type
fragile.

#### L'entité

`LightWaveEntity extends Projectile` **directement**, comme `LlamaSpit` — pas
`ThrowableProjectile` ni `AbstractHurtingProjectile` : leur `applyInertia()` est
`private` (traînée et accélération impossibles à annuler), et surtout leur
`tick()` fait `setPos(hitResult.getLocation())` au premier impact, ce qui
collerait la vague sur sa première cible au lieu de la traverser.

`ProjectileUtil.getManyEntityHitResult(..., ClipContext.Block.COLLIDER)` fait
tout le travail : `COLLIDER` traverse l'herbe haute, les fleurs et l'eau — leur
forme de collision est vide — et s'arrête sur la pierre. Un `IntOpenHashSet`
d'IDs déjà touchés garantit une seule blessure par entité, même quand la vague
met plusieurs ticks à traverser un mob.

Dégâts par `damageSources().indirectMagic(this, owner)` : le joueur passé en
`causingEntity` suffit pour le butin, les advancements et l'agro, tout en
laissant le recul partir de la vague et non du joueur.

**Aucun code réseau.** `Projectile.getAddEntityPacket` transmet déjà l'ID du
tireur et `ClientboundAddEntityPacket` porte le vecteur de mouvement.

Rendu par un quad plat texturé, **couché parallèlement au sol** et orienté par la
trajectoire (pas un billboard face caméra), en
`RenderTypes.entityTranslucentEmissive` pour qu'il brille dans le noir.

⚠️ Deux renommages 26.2 : `RenderType` vit dans
`net.minecraft.client.renderer.rendertype` et **ses fabriques statiques sont dans
`RenderTypes`** (pluriel) ; le rendu passe par `SubmitNodeCollector.submit(...)`,
`render(...)` n'existe plus.

⚠️ **La largeur ne s'obtient qu'avec la bonne surcharge.** La forme courte de
`getManyEntityHitResult` **ignore l'`AABB` pour le test de touche réel** et
retombe sur `computeMargin`, qui vaut 0 aux deux premiers ticks et plafonne à
0,3 : la vague serait une **ligne** et `width` ne servirait à rien. C'est la
surcharge à neuf paramètres, avec la marge passée explicitement, qui en fait un
balayage — et elle ajoute un contrôle de ligne de vue, donc rien n'est touché à
travers un mur.

#### Deux conséquences assumées ✅

**La vague ignore l'armure et le bouclier.** `indirect_magic` porte les tags
`bypasses_armor` et `bypasses_shield`. Soixante pour cent des dégâts de mêlée qui
passent à travers tout, c'est beaucoup en PvP — assumé le 10/09/2026, c'est le
privilège d'une arme légendaire. Elle n'est en revanche **pas** réduite par
Protection contre les projectiles (`is_projectile` n'est pas dans ses tags), et
les sorcières y résistent à 15 % (`witch_resistant_to`).

**Miner avec l'épée tire une vague.** Le client envoie un paquet de swing pour
**tout** clic gauche, démarrage d'un cassage de bloc compris. Le filtre de charge
élimine les coups suivants — le compteur est remis à zéro à chaque tick de
minage — et le cooldown borne le reste, mais ça coûte un point de durabilité
toutes les quinze secondes passées à miner. Assumé le 10/09/2026.

---

## 4. La structure

### 4.1 Répartition ✅

- Génère **uniquement** en biome Dark Forest.
- **Une seule** structure par occurrence contiguë de Dark Forest — donc une
  épée par biome, au maximum.
- Aucune autre structure à moins de **120 blocs** en surface. Les structures
  souterraines sont ignorées pour ce calcul.
  ⚠️ **Tenu pour le manoir seulement**, depuis l'étape 8. `exclusion_zone`
  n'accepte qu'**un** `other_set`, donc l'option A retenue ne couvre que
  `minecraft:woodland_mansions`. Le **portail en ruine**, seule autre structure
  de surface possible en Dark Forest, peut apparaître plus près — c'est le
  compromis assumé de l'option A ci-dessous. Et **aucune structure ajoutée par
  un autre mod n'est vue** : chaque `structure_set` tire sa grille
  indépendamment et rien ne teste la collision entre sets — sur les 20 sets
  vanilla, un seul déclare une exclusion. L'exclusion étant à sens unique, c'est
  toujours notre épée qui cède ; nous ne faisons jamais disparaître la structure
  d'autrui. Lever complètement la limite demanderait l'option B.

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
forêt. 🔷 La valeur retenue est **72**, celle du paragraphe de décision ci-dessus
et du journal du 06/09 — une mention « 80 par défaut » traînait ici, reste d'avant
cette décision, corrigée le 12/09. Rendre `spacing` configurable supposerait le
placement custom de l'option B : un JSON de worldgen ne lit pas la config du mod.

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

**A est en place depuis le 12/09/2026.** Si B est repris un jour, une idée est
apparue en chemin qui vaut mieux que la liste d'exclusions prévue ici : plutôt
que d'énumérer des `other_set`, **itérer sur tous les `structure_set` du
registre** et refuser notre chunk si l'un d'eux a un candidat trop proche.
`hasStructureChunkInRange` étant public, c'est faisable sans mixin, et ça couvre
d'un coup le portail en ruine **et les structures des autres mods**, sans avoir à
connaître leurs identifiants. Prévoir un garde-fou : céder devant tout ferait
disparaître l'épée dans un monde très chargé en mods de structures — n'exclure
que les sets dont les structures sont en `surface_structures`, ou renoncer à
l'exclusion après un nombre d'essais.

#### La règle de biome : toute l'emprise, pas un point ✅ — **fait (étape 16)**

Vanilla ne juge le biome d'une structure que sur **une cellule de 4×4×4**, celle
du centre de la pièce de départ, quelle que soit la largeur de la structure. Pour
une clairière de 17 blocs de côté, tout ce qui est à plus de 2 blocs du centre
n'est jamais regardé — d'où l'épée plantée au bord d'une rivière, mesurée à
**38 %** des sanctuaires à moins de 16 blocs d'un autre biome (§9).

🔷 **Décision de Jérôme le 14/09/2026 : exiger que l'emprise entière soit en Dark
Forest.** Mise en œuvre par un mixin sur `Structure.generate`
(`StructureBiomeMarginMixin`), la règle elle-même dans `ShrineStructure`, partagée
avec le scan qui la mesure — les deux ne peuvent donc pas diverger.

Effet mesuré, sur les mêmes deux seeds qu'avant le correctif :

| Seed | Sanctuaires | En bordure | Avant |
| --- | --- | --- | --- |
| 6431809503670250688 | 158 (−29 %) | **19,0 %** | 42,9 % |
| 20260914 | 203 (−23 %) | **14,8 %** | 34,2 % |

⚠️ **L'épée est donc plus rare d'un quart**, c'est le prix assumé, et c'est le
mot *maximum* du premier paragraphe de §4.1 qui l'autorise.

Trois effets de bord vérifiés à la relecture, tous bénins :

- **`/place structure mastersword:master_sword` continue de poser le sanctuaire
  n'importe où.** Le mixin réutilise le prédicat de biomes que lui passe
  l'appelant, et `PlaceCommand` en passe un toujours vrai.
- **`/locate` reste juste.** Le cache de `StructureCheck` est alimenté par
  `findValidGenerationPoint`, jamais par `generate` : il mémorise la réponse de
  vanilla, que le mixin ne touche pas. La commande charge donc environ un tiers
  de chunks en plus avant de tomber sur un vrai sanctuaire, et c'est tout.
- **Les mondes déjà générés gardent leurs sanctuaires** : ils sont écrits dans le
  NBT des chunks. Seuls les chunks neufs subissent la règle.

⚠️ **Piège à ne pas refaire** : ne pas tester `StructureStart.getBoundingBox()`.
Elle passe par `adjustBoundingBox`, qui **gonfle l'emprise de 12 blocs** dans
chaque direction dès que `terrain_adaptation` n'est pas `NONE` — la nôtre est
`beard_thin`. Le premier essai testait donc 41×41 et ne gardait que 82
sanctuaires sur 224. C'est `start.getPieces()` qu'il faut.

### 4.2 Forme ✅ (« bloc ou demi-bloc + petit décor »)

**Le socle est fait (étape 7).**

- Un **socle** : bloc custom `master_sword_pedestal`, en pierre sombre, forme de
  demi-dalle épaisse avec une fente centrale. Il porte un `BlockEntity` qui
  connaît l'état de l'épée. Dureté 25 / résistance 1200, pioche obligatoire,
  `noOcclusion()` puisque la forme est en deux boîtes (1→15 sur 4 de haut, puis
  3→13 sur 8). Modèle JSON texturé en `mossy_stone_bricks` (socle) / `stone_bricks` (fût) /
  `chiseled_stone_bricks` (fente) — le deepslate d'origine jurait avec la ruine
  de pierre grise de l'étape 8. Une texture propre viendra à l'étape 10 sans
  toucher au code.
- Le socle porte une propriété **`facing`** sur quatre directions, posée comme un
  four ou une table de craft : la lame fait face au joueur au moment de la pose.
  La pierre étant symétrique, l'orientation ne se voit **que** dans l'épée —
  c'est ce qui permet d'aligner une rangée de socles aux lames différemment
  tournées. `rotate` et `mirror` sont implémentés, sans quoi un socle placé dans
  une structure NBT ignorerait la rotation de celle-ci (étape 8).
- L'épée est **rendue par le BlockEntity** (ni cadre d'item, ni entité posée) :
  plantée verticalement, pointe dans la fente, **immobile**. Le rendu réutilise
  le modèle d'item, donc la texture de Jérôme et le reflet d'enchantement
  suivent tout seuls.

🔷 Détail cosmétique connu, non corrigé : la lumière de l'épée est échantillonnée
à la position du socle, alors que la lame occupe surtout le bloc du dessus. Ne se
voit que si les deux cases ont des niveaux de lumière différents.

⚠️ Le modèle d'item est une plaque fine : une épée immobile est vue **de profil**,
donc presque comme un trait, depuis l'axe perpendiculaire à sa lame. Aucune
orientation fixe n'évite ça sous tous les angles. Les deux sorties, si ça gêne :
lui rendre une rotation lente, ou lui donner un vrai modèle 3D à l'étape 10.

⚠️ Piège d'orientation, trouvé en jeu puis mesuré : une sprite d'épée court en
diagonale, **pointe en haut à droite**, soit 45°. Mais la transformation `FIXED`
du modèle d'item applique un demi-tour autour de Y, qui reflète ça à **135°** —
d'où une rotation de 45° qui couche la lame à l'horizontale au lieu de la
redresser. La bonne valeur est **135°**, qui amène la pointe à 270° : droit vers
le bas.
- Décor : une **clairière forestière 17×17×13**, posée par un
  `single_pool_element` et donc éditable en jeu au block-editor. Validée par
  Jérôme le 13/09/2026, au terme de trois essais en jeu.

  - **pas de lumière au sol, et pas de lanterne** (décision du 13/09) — les
    `shroomlight` faisaient tache dans la pénombre, et la référence à lanternes
    de la cascade a été écartée pour la même raison. Seuls le lichen lumineux sur
    la roche et un rare `firefly_bush` éclairent quelque chose ;
  - **emprise creuse** : seules les colonnes du décor sont listées, tout le reste
    garde le sol de la forêt. C'est ce qui a réglé le « c'est trop carré » ;
  - un **gradin unique** : le tablier est **à fleur** de l'herbe, l'anneau de
    demi-dalles une demi-marche plus haut, la terrasse un bloc au-dessus, et le
    sommet du socle à **2 blocs** du sol ;
  - le **socle est en (8, 2, 8)** ;
  - le décor : deux arches de 3 de haut, un pan de mur brisé, neuf moignons, des
    amas de **racines de palétuvier**, trois **gros champignons**, des lianes, et
    **cinq chênes noirs plantés par la structure elle-même** (troncs 2×2,
    couronne large) pour remettre de la canopée au-dessus du sanctuaire ;
  - une **prairie** de vraie herbe, fougères, grandes fougères, muguet, bleuets
    et buissons, posée en `y=1` **sur** le sol de la forêt.

  Le relief vient de la **matière** — pierre moussue, taillée, fissurée, ciselée,
  gravier, andésite — tirée par un hasard déterministe, plus moussue au centre et
  plus délavée au bord.

⚠️ **`y=0` est le bloc de surface, pas l'air au-dessus.** `JigsawPlacement`
ancre la pièce avec `box.minY() + getGroundLevelDelta() == firstFreeHeight`, et
`StructurePoolElement.getGroundLevelDelta()` vaut **1** : le `y=0` du template
**remplace la motte**. Poser une fleur en `y=0`, ou y écrire de l'air, creuse un
trou d'un bloc dans le tapis forestier — ce qu'un essai en jeu a montré, semé
tout autour de la terrasse. Tout ce qui doit se poser *sur* le sol va en `y=1`.

#### Ce qui fait pousser un arbre dans une structure de surface

`surface_structures` passe avant `vegetal_decoration`, donc les arbres sont posés
**après** la structure. La formule retenue à l'étape 8 — « aucun bloc de
`#minecraft:dirt` » — était le bon réflexe et la mauvaise liste. La chaîne
réelle, relue dans le jar 26.2 :

`configured_feature/dark_forest_vegetation` est tiré **16 fois par chunk** sur la
heightmap `OCEAN_FLOOR` ; avec une chance de **0,667** il appelle
`placed_feature/dark_oak_leaf_litter`, dont le seul filtre est
`would_survive(dark_oak_sapling)` → `VegetationBlock.canSurvive` → le bloc
**sous** la position doit être dans **`#minecraft:supports_vegetation`**, soit
**onze blocs** : `dirt`, `coarse_dirt`, `rooted_dirt`, `mud`,
`muddy_mangrove_roots`, `moss_block`, `pale_moss_block`, `grass_block`,
`podzol`, `mycelium`, `farmland`.

D'où le chiffre qui décide : **une case de terre à découvert vaut ~4 % de risque
d'arbre**. ⚠️ **Chiffre corrigé le 13/09/2026 : c'est ~5,8 %, pas 4 %.** Le
`random_selector` ne tire pas un arbre deux fois sur trois mais **92 % du
temps** — `dark_oak_leaf_litter` en fait les deux tiers, mais
`birch_leaf_litter`, `fancy_oak_leaf_litter` et le défaut `oak_leaf_litter` sont
**aussi** des features `minecraft:tree`, et seuls les deux champignons géants
(7,4 %) et les deux troncs couchés (0,4 %) n'en sont pas. Et l'arbre ne fait pas
que pousser : son tronc **retourne en terre les 4 cases sous lui** et se penche
jusqu'à 2 blocs en montant — mettre la terre loin du socle ne protège donc pas
le socle.

Quatre parades existent, toutes vérifiées. **C'est la quatrième qui est retenue
depuis l'étape 13** ; les autres restent disponibles et servent encore là où
elles tombent bien :

| Parade | Coût | Ce qu'elle autorise |
| --- | --- | --- |
| **Sol non porteur** — *retenue pour la clairière* | rien | pierre, `moss_carpet`, `leaf_litter`, feuilles, `mangrove_roots` |
| **Eau au-dessus** | rien | **n'importe quel sol** : `dark_forest_vegetation` — le `placed_feature` extérieur, celui des 16 tirages, et non `dark_oak_leaf_litter` qui ne porte que le prédicat — filtre sur `surface_water_depth_filter` à `max_water_depth: 0` et rejette toute colonne où `WORLD_SURFACE − OCEAN_FLOOR > 0` |
| **Abri au-dessus** | un linteau | herbe et fougères dans les creux : la heightmap ne retient que le sommet bloquant |
| **Verrou Java** — *retenue depuis l'étape 13* | un mixin, voir §4.4 | **tout** : herbe, terre, podzol, fougères, fleurs, n'importe où |

À proscrire : retirer la feature du biome par `BiomeModifications`, qui
supprimerait les chênes noirs de **toute** la forêt.

Deux relevés qui servent à choisir les blocs : `LeafLitterBlock` **surcharge** le
`canSurvive` de `VegetationBlock` et ne demande qu'une face solide — c'est la
seule plante qui tienne sur la pierre ; et un petit champignon exige
`#overrides_mushroom_light_requirement` dessous (podzol, mycélium) **ou** une
luminosité < 13, que le plein jour d'une clairière ne donne pas — d'où les gros
champignons, qui n'ont aucune règle de survie.

#### Plusieurs variantes de ruine

Le `template_pool` accepte une liste d'`elements` avec un `weight` chacun, et le
jigsaw applique une rotation aléatoire à la pièce tirée : ajouter une variante ne
demande **aucun code**, seulement un `.nbt` de plus et une entrée dans le pool.

L'outillage a été découpé pour ça à l'étape 11 :

| Fichier | Rôle |
| --- | --- |
| `tools/structure/nbt.js` | lecteur/écrivain NBT, sans dépendance |
| `tools/structure/builder.js` | canevas de blocs, tirage déterministe, **et les auto-contrôles** |
| `tools/structure/variants/<id>.js` | une variante : ses dimensions, sa graine, son décor |
| `tools/structure/build_structure.js` | le lanceur : construit chaque variante, l'écrit, **la relit et la contrôle** |

🔷 **Une seule variante**, décidée le 13/09/2026 : `clearing` — Clairière
forestière, **17×17×13**, socle en **(8, 2, 8)**, validée en jeu.

⚠️ **La Cascade secrète est abandonnée.** Quatre versions ont été essayées en
jeu, et aucune n'a convaincu Jérôme : « le résultat n'est pas top ». Ce que
chacune a coûté vaut d'être noté, parce que le savoir reste utile même si la
variante ne reste pas.

| Version | Pourquoi elle a échoué |
| --- | --- |
| Chute verticale de 6 | « une colonne d'eau qui coule, ça ne fait pas très naturel » |
| Fente à deux étages | « ça fait plutôt un étage » — deux sources murées empilées se lisent comme une seule |
| **Capture** du build de Jérôme | les trois quarts d'une capture sont le **terrain** du monde, et un terrain ne se transplante pas : posé ailleurs, aplani par `beard_thin`, il ressort en pierre suspendue en l'air |
| Déversoir **généré** | correct techniquement, mais le rendu n'y était toujours pas |

Ce que l'épisode a appris, et qui survit à la variante :

- **le niveau d'un écoulement est sa distance à la source**, un par bloc
  (`dropOff` vaut 1 pour l'eau), et il passe à 8 quand la case du dessous est
  vide. Relevé sur le build de Jérôme, pas sur la doc ;
- **une case n'est `falling` que si elle est alimentée par le dessus** — entre
  deux marches, elle l'est par le côté ;
- un `.nbt` ne peut donc contenir qu'une eau **déjà au repos**, et les seuls
  états dont je sache le prouver seul restent la source et la chute pleine ;
- `tools/structure/anvil.js` et `capture.js` sont nés là : ils lisent un monde
  sauvegardé, retrouvent le socle et relisent ce qui l'entoure. Ils ne servent
  plus à produire une variante, mais ils restent le moyen de **savoir ce qu'une
  génération a réellement produit** plutôt que de le deviner sur une capture
  d'écran.

#### L'eau d'un `.nbt` ne coule pas : il faut poser l'équilibre soi-même

`SinglePoolElement` place ses blocs avec les flags **18**
(`UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`) — **pas** `UPDATE_NEIGHBORS` — et
`ProtoChunk.setBlockState` ne programme aucun tick de fluide. L'eau du fichier
reste donc **figée** jusqu'à ce qu'un joueur touche un bloc voisin, et là **tout
se réveille d'un coup**. Écrire « une source en haut, elle coulera bien » donne
une cascade qui ne coule pas, puis qui se réorganise dans le dos du joueur.

Trois relevés permettent d'écrire directement l'état que le jeu recalculerait :

- `LiquidBlock` (constructeur) : `level=0` = source, `level=1..7` = écoulement
  horizontal, **`level=8` = `getFlowing(8, true)`**, l'eau qui tombe, pleine.
- `FlowingFluid.spread` : une case **non-source** ne s'étale sur les côtés que si
  `!isWaterHole(...)`, et `isWaterHole` renvoie **vrai** dès que la case du
  dessous porte le même fluide → **une colonne qui tombe au-dessus d'eau ne
  s'élargit jamais**.
- La même ligne : une **source** s'étale *toujours*. Elle n'est sûre que **murée**.

D'où la recette de la cascade :

| Élément | État posé | Pourquoi il ne bouge pas |
| --- | --- | --- |
| Lèvre de chute | `water` **source**, cube plein sur les 4 côtés et dessous | ne peut aller que vers le bas, et le bas est déjà de l'eau |
| Colonne | `water[level=8]` de haut en bas | eau en dessous → `isWaterHole` → aucun étalement |
| Bassin | **sources** à ras bord d'une cuvette étanche | aucun voisin horizontal n'est de l'air |

⚠️ « Murer » est plus strict que « bloquer le mouvement » :
`FlowingFluid.canPassThroughWall` teste l'**identité** avec le cube plein
(`targetShape == Shapes.block()`), donc une dalle, un escalier ou un muret
laissent passer l'eau bien qu'ils bloquent le mouvement — et un cube plein
*waterloggable* (racines de palétuvier, feuilles) ne la retient pas davantage, il
se remplit. Le script tient donc **deux** ensembles distincts.

**Ce que chaque variante doit tenir**, sous peine de casser en silence une
mécanique décidée ailleurs dans ce document. Les auto-contrôles de `builder.js`
sont rejoués **sur chaque fichier produit**, relu depuis le disque.

| Invariant | Pourquoi | Se voit comment si c'est raté |
| --- | --- | --- |
| Le socle porte `shrine: 1b` dans son `nbt` | c'est le seul endroit d'où vient le drapeau (§5) | **aucun brouillard**, jamais, sans un mot dans le log |
| Le socle porte aussi `sword` et `fog_consumed: 0b` | l'épée est plantée dès la génération, sans code | socle vide à la sortie de terre |
| **Rien d'écrit en `y=0`** qui ne soit un bloc plein voulu | `y=0` est le bloc de surface : une plante ou de l'air y creuse un trou dans le tapis forestier | des cuvettes d'un bloc semées autour du décor |
| Le **socle est dans le disque gardé** | c'est la seule chose que le verrou Java doit absolument couvrir | un tronc contre l'épée |
| Toute case de `#minecraft:supports_vegetation` est **comptée** — sous abri, sous l'eau, ou hors du disque | depuis l'étape 13 le verrou remplace la palette ; le compte dit ce qui survivrait si le mixin ne s'appliquait pas | un chêne noir sur la butte, ce qui est voulu, ou sur la terrasse, ce qui ne l'est pas |
| Tout bloc de la palette est **classé** dans `BLOCKS_MOTION` ou `FREE_OF_MOTION` | un bloc non classé est lu « n'abrite pas » et « ne mure pas l'eau » — du bon côté, mais en silence | rien, jusqu'au jour où ça compte |
| Toute **source d'eau** a un cube plein ou de l'eau sur ses 4 côtés **et dessous** | une source s'étale toujours, et `spread` essaie le bas en premier | la berge noyée, au premier bloc cassé par le joueur |
| Toute case **`level=8`** a de l'eau juste **en dessous** et juste **au-dessus** | `isWaterHole` est ce qui empêche une chute de s'élargir ; le dessus est ce qui l'alimente | la chute s'élargit en nappe, ou s'assèche |
| **Aucun `level` entre 1 et 7**, et aucune case d'eau sur le bord de l'emprise | les états d'écoulement horizontal ne se figent pas ; hors emprise il y a du terrain, pas de l'air | l'eau fuit dans la forêt |
| Clairière assez large pour un tronc 2×2 | le dark oak est un arbre à quatre pieds | un tronc contre le socle |
| Aucun bloc **isolé en plein air** | une erreur d'ordre dans un triplet de coordonnées | un bloc en lévitation, repéré des semaines après |
| **Colonne du socle dégagée** | la lame est dessinée dans la case au-dessus de la pierre | l'épée enterrée |
| Tout petit champignon a du **podzol ou du mycélium** dessous | sinon il lui faut une luminosité < 13, que le plein jour ne donne pas | le décor se vide au premier bloc cassé à côté |

Le `template_pool` ne valide rien de tout ça : une variante fautive se génère
sans erreur, et c'est en jeu, des semaines après, qu'on s'en aperçoit.

### 4.3 Retrait et remise de l'épée ✅ — **fait (étape 7)**

- Clic droit sur le socle à main vide → l'épée est retirée et donnée au joueur.
  **Aucune condition** : pas de niveau requis, pas de quête, pas de prérequis.
- Le brouillard disparaît **définitivement** à ce moment (§5).
- L'épée peut être **remise** dans le socle (clic droit avec l'épée en main).
  Elle s'y replante et se rend visible, mais **le brouillard ne revient pas**.
- Le socle reste en place dans tous les cas, vide ou occupé.
- Le socle est cassable à la **pioche en diamant** (tag `needs_diamond_tool`), pas
  à n'importe quelle pioche : `requiresCorrectToolForDrops()` seul, sans tag de
  palier, laisse une pioche en bois suffire.
- Le socle accepte **n'importe quelle épée** de `#minecraft:swords`, pas
  seulement l'Épée de Légende (demande de Jérôme, 11/09/2026) : un socle avec une
  épée en fer ou en diamant est une décoration utile. L'Épée de Légende est dans
  ce tag depuis l'étape 2, donc aucun cas particulier ; les épées d'autres mods
  correctement taguées passent aussi. Tout ce qui n'est pas une épée retombe sur
  le comportement normal de l'objet en main.

  Deux garde-fous que ça impose, invisibles au test :
  - la **régénération** ne tourne que si la pile est l'Épée de Légende, sans quoi
    le socle poserait le composant d'horodatage sur une épée en fer et se
    mettrait à la réparer ;
  - le drapeau **« brouillard consommé »** ne bascule que quand c'est l'Épée de
    Légende qui sort.
- Si l'inventaire est plein au moment du retrait, l'épée est posée **sur le
  socle** (`Block.popResource` à la position du bloc) et non aux pieds du joueur.
  `placeItemBackInInventory` passe par `Player.drop`, qui la fait tomber dans les
  herbes hors du champ de vision : jamais détruite, mais introuvable.

#### Sons, particules et advancement ✅ — **faits (étape 10)**

- **Au retrait de l'Épée de Légende, et d'elle seule** : le bruit de pierre
  habituel, doublé d'un `BEACON_ACTIVATE`, et une gerbe d'`END_ROD` et de
  `TOTEM_OF_UNDYING`. Une épée en fer garde le son sobre — la cérémonie est pour
  la légende, pas pour le mobilier.
- **À la remise** : exactement le même son que pour n'importe quelle épée. Une
  note ajoutée pour la seule Épée de Légende a été essayée puis retirée le
  12/09/2026 — remettre l'épée n'est pas un événement, et le carillon s'entendait
  comme un défaut.
- **En continu, deux effets aux propriétaires distincts**, et c'est ce partage
  qui compte : les étincelles d'`END_ROD` appartiennent à **l'épée**, donc elles
  apparaissent sur n'importe quel socle qui la porte, y compris chez soi ; le
  `BEACON_AMBIENT` rare appartient au **lieu**, donc il ne sonne que sur un
  sanctuaire non réclamé, à la même condition que le brouillard. Sans cette
  séparation, replanter l'épée à la base ferait ronronner un socle décoratif pour
  toujours.
- **Advancement `mastersword:drawn_from_the_stone`** — « Tirée de la pierre »,
  cadre `challenge`, rangé sous `minecraft:adventure/root`. Il repose sur un
  **déclencheur maison** (`mastersword:drawn_from_stone`) et non sur
  `inventory_changed`, qui se serait déclenché sur un `/give` ou en créatif. Le
  Java ne dit que « une épée a été tirée d'un socle » ; c'est le JSON qui dit
  laquelle compte, comme le fait `UsedTotemTrigger` en vanilla.

⚠️ Piège payé comptant sur ce point : **`Inventory.add` vide la pile qu'on lui
passe** (`copyAndClear`, ou `setCount(0)` en créatif) et `ItemStack.getItem()`
répond `AIR` dès que le compte est à zéro. Tester « est-ce l'Épée de Légende ? »
*après* l'avoir donnée au joueur répond donc toujours non, et sautait en silence
la totalité du bloc ci-dessus. L'identité se lit **avant** la remise au joueur, et
l'advancement reçoit une copie.

Conséquence : l'état persistant du socle a besoin de **trois** informations
distinctes — « une épée est-elle posée ? », « le brouillard a-t-il déjà été
consommé ? » et, depuis l'étape 9, « ce socle est-il celui que la génération a
posé ? ». La deuxième est irréversible, la troisième ne s'écrit qu'une fois, dans
le `.nbt` de la structure. Les trois sont dans le `BlockEntity` (`sword` par
`ItemStack.OPTIONAL_CODEC`, `fog_consumed` et `shrine` en booléens) et partent au
client par `getUpdateTag` / `getUpdatePacket`, sans paquet custom.

⚠️ La `SavedData` globale que §5 annonçait **n'a finalement pas été écrite** :
l'étape 9 a réglé le problème autrement, par un drapeau `shrine` que seul le
`.nbt` de la structure pose. Casser le socle du sanctuaire fait perdre le
drapeau, donc le brouillard ne revient pas — et un socle posé à la main n'en a
jamais eu. Voir §5.

Le socle est cassable à la pioche et se ramasse. S'il est cassé alors que l'épée
est dedans, l'épée tombe au sol — depuis `PedestalBlockEntity.preRemoveSideEffects`,
le hook d'où le lutrin lâche son livre, et **non** depuis
`affectNeighborsAfterRemoval` qui tourne une fois le bloc déjà parti. Casser le
socle ne compte pas comme dégainer : le drapeau de brouillard ne bouge pas.

**L'épée continue de se régénérer dans le socle** (choix de Jérôme, 11/09/2026) :
`Item.inventoryTick` n'est jamais appelé pour une pile tenue par un
`BlockEntity`, donc le socle pilote lui-même `MasterSwordItem.regenerate`, qui
renvoie désormais un booléen pour ne resynchroniser que quand la durabilité a
réellement bougé.

⚠️ Le chaînage des deux clics droits tient à **une seule valeur** :
`ServerPlayerGameMode` n'appelle `useWithoutItem` que si `useItemOn` a renvoyé un
`InteractionResult.TryEmptyHandInteraction`. `PASS` **ne retombe pas** dessus.
Tous les cas que le socle ne revendique pas renvoient donc
`TRY_WITH_EMPTY_HAND` — c'est aussi le défaut vanilla, et c'est ce qui fait que
le socle se comporte comme un coffre : son interaction l'emporte sur la pose d'un
bloc contre lui, et s'accroupir contourne les deux.

---

### 4.4 Le verrou contre les arbres ✅ — **fait (étape 13)**

Les étapes 11 et 12 ont passé beaucoup d'énergie à choisir des blocs qui ne
portent pas d'arbre. Le résultat tenait, mais il coûtait au sanctuaire **chaque
brin d'herbe** : un sol qui ne pouvait être que minéral, et c'est ce qui faisait
lire les deux variantes comme un rectangle pavé posé dans la forêt. Jérôme a
demandé « il n'y a pas un autre moyen, comme ça je peux utiliser plus de blocs ».
Il y en avait un.

**`ShrineGuard.vetoes(WorldGenLevel, BlockPos)`** répond « cet arbre est-il dans
un sanctuaire ? », et trois mixins `HEAD` annulables l'interrogent :
`TreeFeature`, `AbstractHugeMushroomFeature` et `FallenTreeFeature` — les trois
classes que `dark_forest_vegetation` peut poser. La chaîne est publique de bout
en bout :

```java
region.getLevel().structureManager().forWorldGenRegion(region)   // le seul handle d'une feature
      .startsForStructure(SectionPos.of(pos), shrine)            // références du chunk
StructureStart.getBoundingBox()
```

C'est le chemin de vanilla lui-même : `ChunkGenerator.applyBiomeDecoration` — la
méthode dans laquelle nos mixins s'exécutent — construit le même gestionnaire
scopé à la région et appelle `startsForStructure` pour chaque structure, à chaque
étape de décoration.

Quatre choix, tous payés par un essai en jeu :

| Choix | Pourquoi |
| --- | --- |
| Un **disque**, pas la boîte | interdire les arbres sur tout le carré creuse un **trou carré dans la canopée** : le même défaut, déplacé du sol vers le ciel |
| Rayon **constant de 5 blocs** | deux tentatives dimensionnées sur l'emprise ont laissé un anneau d'herbe nue entre le sanctuaire et la forêt. Le verrou ne protège que la terre autour de l'épée ; la pierre, l'eau et la maçonnerie ne portent de toute façon pas d'arbre |
| **`y` ignoré** | un tronc est refusé dans toute la colonne, quelle que soit l'altitude choisie par la heightmap |
| **Worldgen seulement** (`instanceof WorldGenRegion`) | une pousse que le **joueur** fait grandir à l'os passe par le `ServerLevel` : il peut planter son arbre dans le sanctuaire s'il veut. Vérifié en jeu le 13/09 |

Garde-fou : la région ne détient ses chunks qu'à distance 1 au statut `FEATURES`
(`ChunkPyramid` : `addRequirement(STRUCTURE_STARTS, 8)` et `(CARVERS, 1)`), et
lui en demander un plus loin **lève** au lieu de renvoyer `null`. Le verrou
renonce au-delà de 1 — inatteignable avec les features vanilla, dont l'origine
est toujours dans le chunk central, mais un autre mod plaçant un arbre plus loin
ferait planter la génération, et le plantage aurait notre nom dessus.

🔷 **Portée réelle du verrou.** Le raisonnement « le décor se défend seul » vaut
pour la clairière : son tablier de pierre tient entièrement dans le disque, et
la prairie autour pousse sur le sol de la forêt, où un arbre est le bienvenu. Il
ne valait **pas** pour la cascade, dont la butte était en herbe par conception —
la relecture du 13/09 l'avait relevé, et la variante a été abandonnée depuis.

---

### 4.5 La carte du cartographe ✅ — **faite (étape 17)**

#### Décisions de Jérôme (17/09/2026) ✅

| Point | Décision |
| --- | --- |
| Niveau | **3** (apprenti), et l'échange doit être **proposé à coup sûr**. D'abord demandé au niveau 4, déplacé au niveau 3 le même jour pour s'en tenir aux données, sans Java |
| Nombre d'offres au niveau 3 | **`amount` 4**, confirmé par Jérôme le 17/09/2026 :  les quatre échanges du niveau (boussole, carte des océans, carte des chambres des épreuves, notre carte). Avec 3 tirages, notre carte ne sortirait que 3 fois sur 4 |
| Prix | **celui du manoir** : 14 émeraudes + 1 boussole contre une carte vierge, 12 utilisations, 30 XP, `reputation_discount` 0,2 (même mécanique de réduction) |
| Icône | **une icône à nous**, fournie par Jérôme (générée à part) : 8 × 8 px RGBA, comme `textures/map/decorations/woodland_mansion.png`. **Reportée** le 17/09/2026 (Jérôme) : la carte sort d'abord avec la croix rouge vanilla, voir plus bas |
| Teinte de la carte | **celle du manoir** (5393476), choisie par Jérôme le 17/09/2026 |
| Après le retrait de l'épée | **comportement vanilla**, rien à coder : une carte achetée pointe toujours au même endroit, les suivantes vont plus loin |

#### Ce que vanilla fait en 26.2 (lu le 17/09/2026)

- Les échanges sont **des données** : `data/minecraft/villager_trade/cartographer/<niveau>/<nom>.json`.
  La carte du manoir (`5/emerald_and_compass_woodland_mansion_map.json`) :
  `wants` 14 émeraudes, `additional_wants` boussole, `gives` `minecraft:map`,
  puis `given_item_modifiers` = `exploration_map` (`destination` tag de
  structures, `decoration`, `search_radius` 100) → `set_name` → `filtered` qui
  jette l'échange si la carte n'a pas de `map_id` (rien trouvé).
- Chaque niveau = **un seul** `trade_set` (`VillagerProfession.tradeSetsByLevel`,
  `Int2ObjectMap<ResourceKey<TradeSet>>`). `data/minecraft/trade_set/cartographer/level_N.json`
  tire `amount: 2` échanges dans le tag `#minecraft:cartographer/level_N`.
- **Le tirage** (`AbstractVillager.addOffersFromItemListingsWithoutDuplicates`,
  lu dans les sources) : on copie la liste du tag, puis tant qu'il manque des
  offres, on **retire** un échange au hasard ; s'il donne `null` (carte sans
  structure trouvée), il est perdu et on en tire un autre. `allow_duplicates`
  vaut `false` par défaut. `amount` est un `NumberProvider` lu tel quel.
- **Le manoir est « systématique » par construction** : le tag du niveau 5 ne
  contient que deux échanges, tirés deux à deux. Le niveau 4 en contient **16**
  (cadre + 15 bannières). Au niveau 3, les cartes océan et chambres des épreuves
  ne sont **pas** garanties : 2 tirages sur 3 échanges (avec la boussole),
  chacune sort 2 fois sur 3, et au moins une des deux sort toujours.
- **Garantir notre carte au niveau 3, en données seulement** : ajouter l'échange
  au tag `cartographer/level_3` (`"replace": false`, additif) **et** remplacer
  `data/minecraft/trade_set/cartographer/level_3.json` avec `amount` 4.
  Ce remplacement d'un fichier vanilla est le seul point fragile : un autre mod
  ou datapack qui le remplace aussi l'emporte ou perd selon l'ordre de chargement.
  Le datapack expérimental `trade_rebalance` ne touche pas au cartographe.
- `ExplorationMapFunction` : `destination` est un `TagKey<Structure>`,
  `decoration` un `Holder<MapDecorationType>` (défaut : le manoir). La recherche
  (`ChunkGenerator.findNearestMapStructure`) compte `search_radius` en
  **cellules** de placement — 100 cellules × 72 chunks chez nous — et passe par
  la vraie génération des *starts* : **le mixin de bordure de biome (§4.1)
  s'applique**, la carte ne vise jamais un sanctuaire refusé.
- `MapDecorationType(assetId, showOnItemFrame, mapColor, explorationMapElement,
  trackCount)` est un record public, dans `BuiltInRegistries.MAP_DECORATION_TYPE`.
  La **teinte** de la carte vient de `mapColor` — `red_x` n'en a pas.
- Côté client, **rien à écrire** : `MapRenderer` prend le sprite `assetId` dans
  l'atlas `map_decorations`, dont la source `directory` `map/decorations` n'a
  pas de filtre de namespace.
- Les commandes (`/data modify entity … Offers`) ne modifient qu'**un**
  villageois déjà présent : utiles pour tester, pas pour le mod.

#### Ce qui est fait (étape 17)

| Fichier | Rôle |
| --- | --- |
| `registry/ModMapDecorations.java` | type `mastersword:shrine` : sprite vanilla `minecraft:red_x`, teinte du manoir, mêmes drapeaux que le manoir. Enregistré dans `onInitialize` avant les datapacks |
| `villager_trade/cartographer/3/emerald_and_compass_shrine_map.json` | l'échange, calqué sur celui du manoir |
| `tags/worldgen/structure/on_shrine_maps.json` | la destination : `mastersword:master_sword` |
| `minecraft/tags/villager_trade/cartographer/level_3.json` | ajout additif au niveau 3 |
| `minecraft/trade_set/cartographer/level_3.json` | **remplace** vanilla, `amount` 4 |
| `lang/*.json` | `filled_map.mastersword.shrine` : « Carte du bosquet sacré » / « Sacred Grove Map » |

Un type de décoration à nous **dès maintenant**, alors que le sprite est
vanilla : c'est lui qui porte la teinte. Le jour où l'icône arrive, il suffit
de déposer `assets/mastersword/textures/map/decorations/shrine.png` et de
remplacer `Identifier.withDefaultNamespace("red_x")` par `MasterSwordMod.id("shrine")`.

**Limites relevées par la relecture Opus (17/09/2026), acceptées**

- **Une carte qui ne trouve rien fait perdre une offre.** Le tirage en retire un
  autre s'il en reste (`offersFound` n'avance pas, lu l. 281-287) — mais à 4
  tirages pour 4 échanges, il n'en reste aucun : ce cartographe garde **3 offres**
  au niveau 3, pour toujours. Vaut aussi si la carte des océans ou des chambres
  échoue.
- **La garantie tombe sans bruit** si un autre mod ou datapack ajoute un échange
  au tag du niveau 3 (5 échanges pour 4 tirages), ou remplace lui aussi le
  `trade_set`.
- **Les cartographes déjà au niveau 3** d'un monde existant ne changent pas.
- **Une carte par sanctuaire** : `StructureStart.getMaxReferences()` vaut 1, et
  `skip_existing_chunks` est vrai — chaque carte vendue « consomme » le sien, la
  suivante vise le plus proche encore libre. C'est le comportement vanilla voulu.
- **Retirer le mod d'un monde** laisse dans les cartes vendues un identifiant de
  décoration inconnu (le composant l'enregistre par nom).
- **Coût** : le cache `StructureCheck` croit valide un sanctuaire que notre
  mixin refuse ; chaque recherche recharge ces chunks jusqu'à
  `STRUCTURE_STARTS`. Pire cas théorique : 40 401 cellules sur le thread
  serveur, comme la carte du manoir. En pratique la recherche s'arrête au premier
  anneau qui contient un sanctuaire.

**Testé en jeu par Jérôme le 17/09/2026** : 4 offres à chaque cartographe, la carte mène au sanctuaire. **Non mesurés** : la distance de la carte et le temps de gel quand le
cartographe passe au niveau 3.

## 5. Le brouillard ✅

✅ **L'ordre d'injection est fixé depuis le 13/09/2026**, et avec Sodium et Iris
installés mais **shaders coupés**, le brouillard est là — vérifié en jeu par
Jérôme.

⚠️ **Ce que cet essai ne prouve pas.** La configuration « Sodium installé +
shaders coupés » n'avait **jamais été testée avant** le correctif : le premier
constat d'incompatibilité s'était fait shaders chargés. L'essai ne départage donc
pas « le correctif a réparé le chemin Sodium » de « Sodium ne bloquait rien ici,
le shaderpack était le seul blocage ». Ce qui tranche est ailleurs, et ne dépend
pas de l'essai : le **témoin sur le bytecode fusionné** (voir plus bas) montre
qu'à priorité égale Sodium photographiait bien le `FogData` avant notre passage.
Réserve à garder : ce témoin tournait avec 3 mods, l'instance de Jérôme en a 42,
et l'ordre à priorité égale dépend de l'ordre de chargement des mods — il a donc
pu tomber de l'autre côté chez lui. Dans tous les cas, **compter sur cet ordre
était compter sur le hasard** ; il est désormais explicite.

⚠️ **Reste absent sous un shaderpack chargé, et c'est définitif** — voir la fin
de cette section. Ce n'est pas la même cause, et rien de ce que le mod peut
écrire n'y changera quoi que ce soit.

**C'était bien un conflit d'ordre entre deux mixins sur la même méthode**, et la
raison donnée ici pour écarter cette piste était fausse. Elle disait : « le
rappel de Sodium renvoie un `Vector4f`, il n'injecte donc pas au même endroit ».
Le pool de constantes de
`net/caffeinemc/mods/sodium/mixin/core/render/world/FogRendererMixin.class` dit
`@Mixin(FogRenderer.class)` et `@Inject(method = "setupFog", at = @At("RETURN"))`
— **notre point d'injection exactement**. Le `Vector4f` n'était que le
**générique** de son `CallbackInfoReturnable`, reste d'une signature plus
ancienne de Minecraft ; les génériques étant effacés à l'exécution, le mixin
s'applique quand même. ⚠️ **Le générique d'un `CallbackInfoReturnable` n'est pas
une preuve de la méthode visée** — seul le champ `method` de l'annotation l'est.

Sodium photographie le `FogData` par un `@Local` de MixinExtras et en recopie
huit champs dans son `FogParameters`, que ses shaders lisent avec la formule de
vanilla (`assets/sodium/shaders/include/fog.glsl` : même `max` entre les deux
paires de bornes). Iris lit **ce même** `FogParameters` pour son uniforme
`fogEnd`. Sodium ne fabrique donc pas son brouillard : il recopie le nôtre, à
condition de passer **après** nous.

**Correctif : `@Mixin(value = FogRenderer.class, priority = 500)`.** Une priorité
**basse** est appliquée **en premier**, donc s'exécute en premier — voir §5 de
`../SETUP-MC-MODDING.md`. À 1000 partout, l'ordre tombait sur l'ordre
d'enregistrement des configs, c'est-à-dire le hasard, et il tombait du mauvais
côté. Monter la priorité aurait aggravé le défaut : c'est probablement ce qui
avait fait écarter la piste.

#### Sodium Extra : compatible, vérifié le 13/09/2026

Ajouté à l'instance et passé au témoin. **Il ne peut pas écraser notre
brouillard**, et pas pour la raison qu'on croirait : son `postFogSetup` n'est pas
ancré à `RETURN` comme les autres mais à

```java
@At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F",
    opcode = 181 /* PUTFIELD */, ordinal = 0, shift = At.Shift.AFTER)
```

c'est-à-dire **avant** le `aload` qui précède le `areturn`, donc avant tout
handler `RETURN`. Sa `priority = 1300` — la plus haute des quatre — n'y change
rien : la priorité ne départage que des handlers partageant le **même** ancrage.
Ordre relevé sur le bytecode fusionné, à quatre mods :

```
  9 iris$setupLegacyWaterFog          (HEAD)
 21 sodium-extra$resetFogEnvironment  (HEAD)
212 sodium-extra$postFogSetup         ← ancré au PUTFIELD
241 mastersword$thickenNearShrine     ← nous
255 sodium$storeFogParameters         ← la photo de Sodium
282 iris$render
285 areturn
```

Comme `FogHandler.apply` n'écrit que par `Math.min`, on rabat depuis ce que
Sodium Extra a laissé, quel que soit son réglage. Conséquence à assumer : **un
joueur qui coupe le brouillard dans Sodium Extra verra quand même celui du
sanctuaire.** Son `ProtectedFogType` (`BLINDNESS`, `DARKNESS`, `LAVA`,
`POWDER_SNOW`, `WATER`) ne nous couvre pas, mais l'ordre suffit.

Vérifié aussi que **notre brouillard ne déclenche pas son « Fog Occlusion »** :
aucune classe de Sodium Extra ne lit `environmentalEnd` / `environmentalStart`
pour le culling, qui est piloté par la distance réglée par le joueur. Sinon le
terrain aurait disparu au-delà de notre portée réduite — sans rien pour le
masquer sous shaders.

⚠️ **Et l'ordre entre Iris et Sodium a changé entre deux essais** (Iris avant
Sodium à deux mods, après à quatre), uniquement parce qu'un mod s'est ajouté :
les deux sont à 1000 et se départagent sur l'ordre d'enregistrement. Illustration
directe de pourquoi notre priorité est écrite plutôt que subie. Sans effet ici :
Iris ne capture que la couleur, via `cir.getReturnValue()`, donc le même objet
déjà teinté.

#### Sous un shaderpack : limite acceptée, pas contournable

Le correctif ci-dessus rend le brouillard au terrain de **Sodium**. Sous un
**shaderpack Iris chargé**, il reste absent, et la cause est ailleurs : le pack
ne calcule pas le brouillard à partir de ce que le jeu lui donne.

Mesuré sur celui de Jérôme, **Complementary Reimagined r5.8.1**. Sa fonction de
brouillard, en entier (`shaders/lib/atmospherics/fog/mainFog.glsl`) :

```glsl
void DoFog(inout vec4 color, …) {
    #ifdef CAVE_FOG          DoCaveFog(color, lViewPos);
    #ifdef ATMOSPHERIC_FOG   DoAtmosphericFog(color, playerPos, lViewPosAtm, VdotS);
    #ifdef BORDER_FOG        DoBorderFog(color, skyFade, …);
    if (isEyeInWater == 1) DoWaterFog(…); else if (== 2) DoLavaFog(…); else if (== 3) DoPowderSnowFog(…);
    if (blindness > 0.00001) DoBlindnessFog(color, lViewPos);
    if (darknessFactor > 0.00001) DoDarknessFog(color, lViewPos);
}
```

Les identifiants **`fogEnd`, `fogStart`, `fogDensity`, `fogMode` et `fogShape`
n'apparaissent nulle part dans le pack** — zéro occurrence sur l'ensemble des
fichiers. Il ne les ignore pas : il n'y a aucun chemin de code. `fogColor` est
bien déclaré (`lib/uniforms.glsl:56`) et reçoit donc notre teinte, mais il ne
sert qu'à la lave, la neige poudreuse, l'eau et le Nether — jamais au brouillard
de surface. En surface à l'air libre, les deux seuls états du jeu que le pack
lit sont `blindness` et `darknessFactor`, et tous deux **assombrissent**
(`color *= 1.0 - fog`) au lieu de brumer.

**Il n'y a donc aucune valeur à bouger.** Ce n'est plus une question d'API : ni
Sodium ni Iris n'en exposent (vérifié classe par classe dans `sodium.api`, 52
classes, et `iris.api.v0`, 7 — relevé complet dans `AUDIT-API.md`), et même une
API n'y changerait rien, puisque le pack ne pose pas la question.

**Le précédent qui tranche : Atmospherics fait pareil.** Le mod d'effets
atmosphériques de l'instance ne touche à **aucune** classe de
`net/minecraft/client/renderer/fog/` : son brouillard (`airHaze`) est fait de
**particules** — `AmbientMistEmitter`, `AmbientMistParticle`, `AirHazeRuntime`,
donc la route « dessiner soi-même ». Et sous shaderpack, il **s'éteint**, à trois
endroits indépendants :

```
AmbientMistEmitter / AmbientFlowMixin :
    invokestatic  AtmosphericsClient.isShaderPackActive()Z
    ifeq  <suite>
    return                         ← pack actif : rien n'est émis
AmbientMistParticle : rendu conditionné à !isShaderPackActive() && airHazeEnabled
```

Un mod dont c'est le métier entier, par un auteur qui va jusqu'à désactiver
automatiquement le « Fog Occlusion » de Sodium Extra
(`FogConfig.sodiumFogOcclusionAutoDisabled`), renonce sous shaders. La limite
n'est donc pas la nôtre.

Sa détection, en revanche, est à retenir : `FabricLoader.isModLoaded("iris")`
puis `Class.forName("net.irisshaders.iris.api.v0.IrisApi")` →
`getInstance()` → `isShaderPackInUse()`, **entièrement par réflexion**, mise en
cache et revérifiée périodiquement. **Aucune dépendance de compilation.** Le jour
où le mod voudrait prévenir le joueur que son shaderpack masque le brouillard du
sanctuaire, c'est gratuit.

🔷 **Décision de Jérôme, 13/09/2026 : on en reste là.** Sous shaderpack, le
brouillard appartient au pack. Les deux seules suites possibles étaient un
brouillard dessiné en géométrie propre — indépendant du moteur, mais un chantier
entier au rendu incertain — ou un effet **Cécité** à l'approche, qui est un
changement de gameplay et que le reste de cette section refuse explicitement.



- Présent **uniquement** tant que l'épée n'a jamais été retirée de sa structure.
- S'intensifie à l'approche. Rayon extérieur : **50 blocs**.
- Disparaît **définitivement** au premier retrait — remettre l'épée ne le
  ramène pas.

**Implémenté à l'étape 9**, et **sans une ligne de code réseau** : le socle
envoie déjà tout son état sauvegardé au client depuis l'étape 7
(`getUpdateTag` → `saveCustomOnly`), donc les deux drapeaux dont le brouillard a
besoin sont dans le `BlockEntity` que le client tient en main.

#### Quel socle mérite un brouillard

Le socle porte un troisième champ, **`shrine`**, écrit **uniquement par le `.nbt`
de la structure** — il n'a aucun setter en Java. Le brouillard est dû si
`shrine && !fog_consumed`.

Ce drapeau remplace la `SavedData` globale que ce document proposait, et il
règle un cas que la `SavedData` ne voyait pas : sans lui, replanter l'épée dans
un socle posé chez soi lèverait un brouillard permanent sur la base. Il règle
aussi celui qu'elle visait — casser le socle du sanctuaire fait perdre le
drapeau avec lui, donc le brouillard ne revient pas — sans rien avoir à
persister à part le socle lui-même. Décidé avec Jérôme le 12/09/2026.

⚠️ Conséquence : un sanctuaire généré **avant** l'étape 9 n'a pas le drapeau et
reste sans brouillard. Le champ absent vaut `false`, donc rien ne casse.

#### Le rendu

- Effet purement client, calculé à chaque image : distance de la caméra au
  sanctuaire dû le plus proche → densité interpolée. À 50 blocs, 0 % ; à
  **10 blocs**, densité maximale. Les deux rayons sont configurables, ainsi que
  `visibility` — ce qu'on voit encore au cœur du brouillard, gardé à 12 blocs
  pour que le socle reste visible et cliquable quand on est dessus.

  ⚠️ **La fermeture est géométrique, et sa rampe est en racine carrée.** Deux
  essais en jeu ont été nécessaires, chacun corrigeant une erreur de fait.

  D'abord, un brouillard se lit comme un **rapport** de distances, pas comme une
  différence : en interpolant droit vers `visibility`, on laissait la portée à 91
  blocs quand le joueur était à 20 blocs du socle — la moitié de la courbe, et
  rien de visible sous une canopée où on ne voit déjà pas à 30 blocs. Tout
  l'effet se tassait sur les dix derniers blocs. D'où une division de la portée à
  chaque pas plutôt qu'une soustraction, et l'abandon de la courbe quadratique
  qui aggravait le même défaut.

  Ensuite, **la valeur qu'on divise vaut 1024, pas la distance de rendu.**
  `EnvironmentAttributes.FOG_END_DISTANCE` a **1024** pour défaut, et **aucun
  biome vanilla ne le surcharge** — les deux marais ne touchent qu'au brouillard
  *sous-marin* ; la distance de rendu, elle, vit dans l'**autre** paire de bornes
  du shader, que celui-ci combine par un `max`.
  Diviser 1024 demande une rampe plus rapide que diviser 192 : sans la racine
  carrée, la portée restait à 36 blocs avec le joueur à 20 blocs, soit une
  brume légère et non un lieu gardé. La racine est l'inverse exact du carré par
  lequel cette courbe avait commencé.

  Portée obtenue, aux valeurs par défaut : **111 blocs à 40, 44 à 30, 22 à 20, 16
  à 15, 12 au socle** — l'épée passe de 36 % à 92 % de brume entre 40 et 20
  blocs, culmine vers 15, puis se dégage un peu à l'arrivée. Le sanctuaire sort
  du brouillard au lieu d'y être noyé. Validé en jeu par Jérôme le 12/09/2026.
- La densité est **lissée sur le temps de jeu**, environ une seconde de fondu,
  sur le modèle du brouillard de pluie vanilla. Elle sert trois fois : pas
  d'apparition en une image quand le sanctuaire entre dans les chunks chargés,
  pas de saut quand on tire l'épée, pas de clignotement si un chunk se recharge.
- Teinte gris-vert froide (`#8FA89A` par défaut), cohérente avec le Dark Forest.
- Le client tient la liste des socles des chunks chargés, alimentée par
  `ClientBlockEntityEvents`. 50 blocs tiennent dans 4 chunks, donc le chargement
  normal suffit à toute distance de rendu jouable — c'est le bénéfice secondaire
  du passage de 100 à 50 blocs, plus besoin d'un paquet à longue portée.

⚠️ **Le brouillard ne peut qu'ajouter à celui du jeu, jamais l'éclaircir.** Les
distances passent par un `Math.min` contre ce que vanilla a posé, et la
recoloration est **suspendue** sous Cécité, sous Obscurité et devant une barre de
boss : y étaler un gris-vert pâle rendrait une partie de la vue que ces effets
sont censés retirer. Tête sous l'eau ou dans la lave, le brouillard du fluide
fait autorité et le nôtre s'efface.

---

## 6. Configuration ✅

Un fichier de configuration permet d'ajuster les stats, les zones de spawn, le
brouillard, etc.

**Posé à l'étape 3.**

- **`config/mastersword.json`**, lu par un `Codec` + `JsonOps`, sans dépendance
  externe (ni Cloth Config, ni owo, ni MidnightLib) : une dépendance de plus à
  faire suivre à chaque version de Minecraft, pour un fichier plat d'une
  trentaine de valeurs. Le `Codec` plutôt que du Gson brut parce qu'il distingue
  **clé absente** de **valeur à zéro**, ce qu'un champ primitif Gson ne permet
  pas.
- Généré avec ses valeurs par défaut au premier lancement, et réécrit normalisé
  à chaque chargement (les clés manquantes réapparaissent).
- Rechargeable par `/mastersword reload`, pour ce qui peut l'être — voir les
  limites ci-dessous.

⚠️ **Deux codecs, construits depuis la même liste de champs.** À l'encodage,
`optionalFieldOf(nom, défaut)` **omet** la clé quand la valeur égale le défaut :
le premier fichier généré ne contenait que son commentaire, sans une seule clé à
éditer. La lecture utilise donc `optionalFieldOf` (un fichier partiel se charge),
l'écriture `fieldOf` (tout est écrit). Un booléen bascule entre les deux.

⚠️ **`intRange` et `floatRange` rejettent** une valeur hors bornes, ils ne la
ramènent pas dans l'intervalle. Mais le repli est plus fin que « tout aux
défauts » : `optionalFieldOf` renvoie une erreur **accompagnée d'un partiel**, et
`resultOrPartial` le récupère. Mesuré sur
`{"attack_damage": 12.0, "max_durability": -5, "unbreakable": true}` → seul
`max_durability` retombe à 2031, les deux autres valeurs sont conservées, et
l'avertissement nomme la clé fautive. Jamais un crash, et jamais une valeur
aberrante qui atteindrait le jeu.

`attack_speed` est borné à **-3.9** et non -4.0 : -4.0 annulerait exactement
l'attribut `ATTACK_SPEED`, la barre d'attaque ne se remplirait plus jamais et
l'attaque chargée de §3 deviendrait inatteignable.

⚠️ **JSON n'accepte pas de commentaires.** Le fichier porte un tableau
`"_comment"` en tête, écrit à chaque sauvegarde et ignoré à la lecture, qui dit
lui-même quelles clés se rechargent à chaud.

Découpage prévu :

| Section | Contenu |
| --- | --- |
| `item` ✔ | `attack_damage`, `attack_speed`, `max_durability`, `full_regen_days`, `regen_starts_below`, `unbreakable`, `repairable_with_netherite_ingot` |
| `light_wave` ✔ | `enabled`, `cooldown_seconds`, `range`, `speed`, `damage_ratio`, `width`, `durability_cost`, `requires_full_health` — toutes relues à chaud |
| `structure` | activée, `spacing`, `separation`, distance minimale aux autres structures (120), biome cible |
| `fog` ✔ | `enabled`, `outer_radius` (50), `inner_radius` (10), `visibility` (12), `color` (`#RRGGBB`) — relue à chaque image |

**Trois limites à connaître avant de promettre « tout est configurable à
chaud »**, les deux premières désormais vérifiées et traitées :

1. ✔ **La durabilité max est un composant de données** (`DataComponents.MAX_DAMAGE`),
   figé sur la pile à sa création. `ItemStackMixin` intercepte donc
   `ItemStack.getMaxDamage()I` en `HEAD` — c'est le **seul** point à couvrir :
   `getDamageValue`, `isBarVisible`, `getBarWidth` et `getBarColor` passent
   toutes par lui. `unbreakable` intercepte en plus `isDamageableItem()Z` pour
   faire disparaître la barre.
   ⚠️ **Baisser `max_durability` sous les dégâts déjà encaissés perd ces dégâts
   en quelques secondes.** `getDamageValue()` fait
   `clamp(stocké, 0, getMaxDamage())` : avec 1500 de dégâts stockés et
   `max_durability` ramené à 100, il renvoie **100**, donc `dégâts == max` —
   l'épée paraît **entièrement cassée**.
   La lecture seule est réversible, mais le premier `setDamageValue` grave la
   valeur écrêtée. Et depuis l'étape 4 ce n'est plus « au prochain coup » :
   **la régénération écrit toute seule**, après `fullRegenTicks / maxDamage`
   ticks, soit ~480 ticks (24 s) dans l'exemple ci-dessus. `setDamageValue` ne
   consulte pas `isDamageableItem()`, il écrit toujours. La fenêtre pour revenir
   en arrière se compte donc en secondes. Le fichier de config le dit lui-même
   dans son `_comment`.
   ⚠️ `unbreakable: true` **empêche de fusionner deux épées** : `AnvilMenu` et
   `GrindstoneMenu` refusent un item qui se déclare non endommageable. La
   réparation par combinaison de §2.2 et le cumul d'enchantements par fusion
   deviennent donc impossibles tant que l'option est active.
2. ✔ **Les attributs** (dégâts, vitesse) et le composant `REPAIRABLE` sont posés
   à l'enregistrement de l'item, donc lus une seule fois au démarrage.
   Modifiables par config, appliqués au **redémarrage** — pas par `/reload`.
   C'est pourquoi `MasterSwordConfig.load()` doit précéder `ModItems.register()`
   dans `onInitialize`.
3. **La worldgen est data-driven** : `spacing` / `separation` sont lus dans le
   JSON au chargement du monde. Les rendre configurables suppose soit un
   `StructurePlacement` custom qui lit la config (cohérent avec ce que demande
   déjà §4.1), soit d'accepter que ces valeurs ne s'appliquent qu'aux **chunks
   non encore générés**. Dans tous les cas, changer ces valeurs sur un monde
   existant ne déplace rien de déjà généré.

🔷 Portée serveur / client : `item`, `light_wave` et `structure` font autorité
côté serveur ; `fog` est purement client (chacun règle sa lisibilité). En
multijoueur, le client n'impose rien sur le **gameplay**.

⚠️ **Mais pas sur l'affichage.** `onInitialize` tourne aussi côté client, donc
chaque client charge *son* `mastersword.json`, et `/mastersword reload` ne
recharge que le serveur. La barre de durabilité étant dessinée à partir du
`max_durability` **du client**, un client mal réglé affiche une barre fausse — et
un `unbreakable: true` local masquerait la barre pendant que le serveur continue
d'user l'épée. Les dégâts réels restent serveur : c'est un décalage d'affichage,
pas une triche. À régler quand un paquet de synchronisation existera — l'étape 9
en aura un de toute façon pour le brouillard.

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
├─ config/MasterSwordConfig  Codec, chargement, sauvegarde normalisée ✔ créé
├─ command/MasterSwordCommand  /mastersword reload ✔ créé
├─ registry/                 ModItems ✔, ModItemIds ✔, ModComponents ✔,
│                            ModEntityTypes ✔, ModBlocks ✔, ModBlockEntities ✔,
│                            ModCriteria ✔
├─ item/MasterSwordItem      ✔ régénération ; l'attaque chargée à l'étape 6
├─ block/PedestalBlock ✔, PedestalBlockEntity ✔ socle
├─ entity/LightWaveEntity    ✔ créée
├─ advancement/DrawnFromStoneTrigger ✔ déclencheur maison
├─ worldgen/                 StructurePlacement custom (le reste en JSON data/)
└─ mixin/                    ItemStackMixin ✔ durabilité configurable,
                             AnvilMenuMixin ✔, EnchantmentHelperMixin ✔,
                             EnchantCommandMixin ✔ exclusivité levée,
                             ServerPlayerMixin ✔, PlayerAttackMixin ✔ vague

src/client/java/re/jerome/mastersword/client/
├─ MasterSwordClient         point d'entrée client (ClientModInitializer) ✔ créé
├─ PedestalRenderer         ✔ créé (+ PedestalRenderState)
├─ LightWaveRenderer      ✔ créé (+ LightWaveRenderState)
├─ FogHandler               ✔ créé
└─ mixin/FogRendererMixin   ✔ créé — config client séparée
```

Les mixins **client** ont leur propre `mastersword.client.mixins.json`, dans
`src/client/resources/`, déclaré dans `fabric.mod.json` avec
`{"config": …, "environment": "client"}`. Les source sets étant séparés, un mixin
visant une classe `@Environment(CLIENT)` ne peut pas vivre dans la config
commune : elle est chargée aussi côté serveur, où la classe cible n'existe pas.

Enregistrements dans des classes à **initialisation statique** — le mémo §8
insiste sur l'ordre d'initialisation, et une particule doit être enregistrée des
deux côtés.

Deux pièges relevés à la relecture de l'étape 2, à garder en tête pour la suite :

- **Un mixin ne doit pas référencer `ModItems`.** L'enregistrement de l'épée est
  un effet de bord du `<clinit>` de cette classe : un mixin qui la touche
  déclencherait l'initialisation à un moment imprévu. Tester plutôt
  `stack.is(ModItemIds.MASTER_SWORD)` — `ModItemIds` n'a aucun effet de bord.
- **L'item est un `Item` nu aujourd'hui.** Les étapes 4 (`inventoryTick` pour la
  régénération) et 6 (hook d'attaque chargée) imposeront de passer à
  `MasterSwordItem extends Item`. Changement d'une ligne, mais à ne pas oublier.
  Le nom de classe doit finir par `Item`, sans quoi le constructeur vanilla
  logge une erreur en développement.

### 7.4 À vérifier avant d'écrire du code

Aucun de ces points ne doit être codé de mémoire. Sous-agent + `javap` +
`genSources`, comme le veut `CLAUDE.md`.

1. ~~Stats exactes de `NETHERITE_SWORD` et forme de `ToolMaterial`~~ — **fait le
   06/09/2026**, voir §2.1.
2. Comment déclarer un `DataComponentType` custom et le persister sur un
   `ItemStack` ; comment vanilla fusionne les composants à l'enclume (§2.2).
   (La moitié « enclume » est faite : `AnvilMenu.createResult()` lu, voir §2.2.)
3. Le hook d'attaque côté item en 26.2 (`hurtEnemy` existe-t-il encore ? sous
   quelle signature ?), comment lire la charge d'attaque du joueur, et l'API de
   cooldown d'item.
4. Où vanilla teste l'exclusivité entre enchantements.
5. ~~Que l'absence d'ingrédient de réparation n'empêche pas la combinaison de
   deux items identiques~~ — **fait le 06/09/2026**, et corrigé : ce n'est pas
   l'absence qui compte, il faut écraser `REPAIRABLE`. Voir §2.2.
6. ~~Format 26.2 de `structure_set` et espacement inter-sets~~ — **fait le
   06/09/2026**, voir §4.1. ~~Reste à vérifier : le format de `template_pool` et
   la pose d'une structure NBT en 26.2.~~ — **fait le 12/09/2026** (étape 8) :
   `.nbt` sous `data/<ns>/structure/`, `DataVersion` 4903, et **`processors`
   obligatoire** dans un `single_pool_element`. Détails dans
   `../SETUP-MC-MODDING.md` §4.
7. ~~L'API de rendu du brouillard côté client en 26.2 et la façon propre de la
   moduler depuis un mod~~ — **fait le 12/09/2026** (étape 9). Il n'y a **aucune
   API Fabric** : les 66 jars du cache ne contiennent pas une classe qui mentionne
   `Fog`. Le point d'accroche est `FogRenderer.setupFog`, **public** et qui
   **renvoie** le `FogData` de l'image ; un `@Inject` en `RETURN` suffit, sans
   variable locale ni ordinal. Détails dans `../SETUP-MC-MODDING.md` §4.
8. ~~Rendu d'un `BlockEntity` et d'une entité projectile plate~~ — **fait les
   10 et 11/09/2026**. Les deux suivent le même patron : `createRenderState` /
   `extractRenderState` / `submit`. Un item se dessine par
   `ItemModelResolver.updateForTopItem(..., null, 0)` puis
   `ItemStackRenderState.submit(...)`, avec le garde `isEmpty()` de vanilla.
9. ~~`SavedData` en 26.2 : signature, `Codec`, et enregistrement par dimension~~
   — **répondu le 12/09/2026**, et **pas utilisé** : l'étape 9 s'en est passée
   (§5). La réponse est notée pour le jour où elle servira :
   `new SavedDataType<>(Identifier, Supplier<T>, Codec<T>, DataFixTypes)` puis
   `ServerLevel.getDataStorage().computeIfAbsent(TYPE)`. ⚠️ `DimensionDataStorage`
   **n'existe plus** en 26.2, c'est `SavedDataStorage`.

---

## 8. Plan d'implémentation 🔷

Chaque étape se termine par un build vert et, quand c'est visible, un test en
jeu. Sous-agent de vérification avant chaque commit.

| # | Étape | Contenu | État |
| --- | --- | --- | --- |
| 1 | Squelette | wrapper Gradle copié, `fabric.mod.json`, `LICENSE` MIT, `.gitattributes`, build vide qui se lance | **fait** 06/09/2026 |
| 2 | L'item nu | épée aux stats netherite, texture, modèle, réparation par combinaison vérifiée | **fait** 06/09/2026 |
| 3 | Config | fichier JSON, `Codec`, chargement, `/mastersword reload`, mixin `getMaxDamage` | **fait** 06/09/2026 |
| 4 | Régénération | composant custom, décompte sur l'horloge du monde, remise à zéro au combat | **fait** 07/09/2026 |
| 5 | Enchantements | mixin d'exclusivité, test Sharpness + Smite | **fait** 09/09/2026 |
| 6 | Vague de lumière | entité, rendu, dégâts, cooldown 15 s, équilibrage | **fait** 10/09/2026 |
| 7 | Socle | bloc, BlockEntity, rendu de l'épée plantée, retrait et remise | **fait** 11/09/2026 |
| 8 | Génération | structure NBT, `structure_set` calqué sur le manoir, `exclusion_zone` | **fait** 12/09/2026 |
| 9 | Brouillard | drapeau `shrine`, courbe 50 → 10 blocs, disparition définitive | **fait** 12/09/2026 |
| 10 | Finitions | sons, particules, advancement de retrait, traductions fr/en, modèle 3D de l'épée **(fait 11/09/2026)** | **fait** 12/09/2026 |
| 11 | Décor, variante 1 | clairière forestière 13×13 à la place de la ruine plate, outillage découpé pour plusieurs variantes | **fait** 12/09/2026 |
| 12 | Décor, variante 2 | cascade secrète, second élément du `template_pool` | **fait** 12/09/2026 |
| 13 | Lever la contrainte des arbres | verrou Java (§4.4), emprise creuse, refonte de la clairière | **fait** 13/09/2026 |
| 14 | Cascade abandonnée | quatre essais, aucun convaincant ; le mod ne garde qu'une variante | **fait** 13/09/2026 |
| 15 | Bordure de biome, mesure | instrument `/mastersword scan`, taux établi sur 487 sanctuaires (§9) | **fait** 14/09/2026 |
| 16 | Bordure de biome, correctif | mixin sur `Structure.generate` : toute l'emprise en Dark Forest (§4.1) | **fait** 14/09/2026 |
| 17 | Carte du cartographe | échange garanti au niveau 3, type de décoration à nous sur le sprite vanilla `red_x` (§4.5) | **fait** 17/09/2026 |

Les étapes 11 et 12 sont venues **après** la fin du plan initial : le mod était
complet à l'étape 10, elles ne touchent que l'apparence de la structure.

La config passe en étape 3, avant tout ce qui a des valeurs à régler : la
brancher après coup obligerait à repasser sur chaque fichier.

---

## 9. Questions ouvertes ❓

| # | Question | Impact |
| --- | --- | --- |
| 1 | Perdre les enchantements à la meule / table de craft est-il acceptable ? 🔷 oui, c'est vanilla | §2.2 |
| 2 | Le dépôt est-il destiné à être publié (GitHub, Modrinth) ? | README, bloc `contact` de `fabric.mod.json`, icône du mod |
| 3 | ~~Le sanctuaire tombe trop souvent en bordure de biome~~ — **fermée le 14/09/2026.** Mesuré à 38 % sur 487 sanctuaires, corrigé en exigeant l'emprise entière (§4.1), re-mesuré à 17 %. | §4.1 |
| 4 | ~~Une carte menant au sanctuaire, vendue par le cartographe~~ — **fermée le 17/09/2026.** Faite à l'étape 17, en données plus un type de décoration ; l'icône propre est reportée. ⚠️ Reste à mesurer en jeu la distance (le sanctuaire est plus rare d'un quart depuis l'étape 16) et le temps de gel à la montée de niveau. | §4.5 |

### Question 3 — la cause, corrigée le 14/09/2026

**`Structure.isValidBiome` ne teste qu'un seul point** — `getNoiseBiome` sur le
`QuartPos` de la position du stub, soit **une cellule de 4×4×4 blocs**. Ni
l'emprise, ni le voisinage. Ça, c'est établi et ça ne bouge pas.

⚠️ **En revanche, « le coin nord-ouest du chunk » écrit ici le 13/09 est faux**,
et c'est une erreur qui envoyait droit dans le mur — c'est elle qui faisait de
« recentrer l'ancre » une piste crédible. Le coin nord-ouest n'est que
l'**argument** `position` que `JigsawStructure.findGenerationPoint` passe à
`JigsawPlacement.addPieces` ; le `GenerationStub` que celle-ci renvoie est bâti
sur `(maxX + minX) / 2` et `(maxZ + minZ) / 2` de l'emprise de la pièce de
départ, donc sur le **centre de l'emprise**. Pour notre clairière de 17×17, ça
tombe 8 blocs à l'intérieur, c'est-à-dire au centre du chunk.

**Vérifié sur le sanctuaire connu** : socle à −4295 / −5356, dans le chunk qui
commence à −4304 / −5360, point testé à **−4296 / −5352** = `minBlockX + 8`.
Le jeu juge donc le biome **au milieu du sanctuaire**, et ne regarde rien de ce
qui l'entoure à moins de 8 blocs.

Le scan porte désormais un **contrôle** : il recalcule le test de vanilla à ce
point et le compare au verdict du jeu sur chaque candidat. Un seul désaccord
invalide tout le rapport, et le rapport le dit en toutes lettres.

⚠️ **L'option B de §4.1 ne corrigerait pas ça.** Un `StructurePlacement` choisit
des *chunks* et s'écarte d'autres *sets* ; la marge au bord de biome se décide
dans `Structure`. Ne pas partir là-dessus en croyant faire d'une pierre deux
coups.

### Question 3 — le taux, mesuré le 14/09/2026

**Deux instruments indépendants, qui se recoupent.**

- `tools/structure/biome_edge.js` lit un **monde sauvegardé** : il trouve les
  socles (en ignorant ceux posés à la main, `shrine: 0`) et donne pour chacun la
  distance au premier biome étranger, plus une carte des biomes.
- `/mastersword scan [rayon]` (`worldgen/ShrineScan.java`, **dev uniquement**)
  interroge le **générateur**, sans générer un seul chunk : il énumère les chunks
  candidats de la grille et demande à `Structure.findValidGenerationPoint` — donc
  au jeu lui-même — s'il les accepterait. En headless :
  `MASTERSWORD_SCAN=60 ./gradlew runServer`, qui scanne, écrit
  `run/mastersword-scan-<seed>.txt` et arrête le serveur.

⚠️ **La pré-génération prévue ici est abandonnée**, pour une raison
d'arithmétique : une cellule de placement fait 72×72 chunks et ne donne **qu'un**
candidat, le plus souvent hors Dark Forest. Cent sanctuaires auraient coûté de
l'ordre du **million de chunks générés**. Le scan en donne 224 en 80 secondes, sans un chunk généré.

**Recoupement des deux outils** : sur « New World (1) », le scan retrouve le
sanctuaire connu à −4296 / −5352 et annonce **4 blocs d'une rivière**, ce que
`biome_edge.js` lit dans les chunks sauvegardés par un chemin tout autre.
⚠️ **Le chiffre de « 8 blocs » écrit ici le 13/09 était faux** : c'est 4.

**Le taux, sur deux seeds et 487 sanctuaires** (rayon 60 cellules, soit ±69 000
blocs autour de l'origine ; distance prise depuis le centre de l'emprise) :

| Seed | Sanctuaires | À moins de 16 blocs d'un autre biome |
| --- | --- | --- |
| 6431809503670250688 (monde de Jérôme) | 224 | 96 — **42,9 %** |
| 20260914 (monde neuf) | 263 | 90 — **34,2 %** |

L'observation de Jérôme est donc **confirmée et chiffrée** : entre un tiers et
40 % des sanctuaires sont collés à une bordure. Le voisin est une **rivière** dans
30 % des cas, une autre forêt (bouleaux, pale garden) dans 40 %, une plage ou un
rivage dans 11 %.

**Ce que chaque règle d'acceptation donnerait**, évaluée sur les mêmes candidats
dans la même passe (moyenne des deux seeds) :

| Règle | Sanctuaires gardés | Restant en bordure |
| --- | --- | --- |
| **vanille** — une cellule, au centre du sanctuaire | 100 % | 38 % |
| **emprise entière en Dark Forest** (toutes ses cellules de biome) | **74 %** | **17 %** |
| marge de 8 blocs autour du sanctuaire | 86 % | 28 % |
| marge de 16 blocs | 62 % | 0 % |
| marge de 24 blocs | 47 % | 0 % |
| marge de 32 blocs | 33 % | 0 % |

Deux lectures à ne pas rater :

- **Il n'y a rien à recentrer.** C'était la piste la plus évidente des trois
  notées le 13/09, et elle repose entièrement sur le « coin nord-ouest » qui était
  faux : le point testé est déjà le centre du sanctuaire. Le sanctuaire n'est pas
  mal placé dans son chunk, il est dans une forêt trop petite ou trop découpée.
- Les lignes « marge ≥ 16 » affichent 0 % de bordure **par construction** — la
  règle est la mesure. Ce qu'elles disent de vrai est leur **coût** : une marge de
  32 blocs supprimerait les deux tiers des sanctuaires.

**Tranché le 14/09/2026 : l'emprise entière.** Jérôme a choisi cette ligne du
tableau ; la mise en œuvre et son effet mesuré sont en §4.1. Le paragraphe qui
suit est ce qui était sur la table au moment du choix, gardé parce que la piste
« déplacer » n'a jamais été mesurée et reste ouverte si la raréfaction gêne.

🔷 ~~Reste à trancher : quelle règle, et rejeter ou déplacer.~~ L'emprise entière
est le seul compromis intéressant du tableau — elle divise les bordures par plus
de deux en ne coûtant qu'un quart des sanctuaires — mais toutes les règles
ci-dessus **rejettent** le candidat, ce qui raréfie l'épée alors que §4.1 note
déjà que la plupart des Dark Forests n'en ont pas. Une piste non mesurée serait
de **déplacer** le sanctuaire dans son chunk vers un point qui a de la marge, au
lieu de renoncer : un `StructureType` maison le permet, `findGenerationPoint`
étant libre de choisir sa position.

Note de placement relevée en chemin, utile à qui écrira le correctif : le point
testé se trouve à **±8 blocs** du coin nord-ouest du chunk, le signe dépendant de
la **rotation** tirée pour la pièce de départ. Le sanctuaire n'est donc pas
toujours centré dans son chunk : selon la rotation, il déborde de 8 blocs au nord
ou à l'ouest.

---

## 10. Journal des décisions

| Date | Décision |
| --- | --- |
| 17/09/2026 | **Étape 17 : le cartographe niveau 3 vend à coup sûr la carte du sanctuaire.** Quatre fichiers de données et **une** classe Java. Les données suivent exactement ce que la question 4 avait instruit : échange calqué sur le manoir, ajout additif au tag du niveau 3, `trade_set` du niveau 3 remplacé avec `amount` 4. La classe est venue d'un choix de Jérôme en cours de route : **l'icône maison est reportée**, la carte sort avec la croix rouge vanilla **mais avec la teinte du manoir** — or la teinte est portée par le `MapDecorationType`, et celui de `red_x` n'en a pas. D'où un type `mastersword:shrine` qui pointe sur le sprite vanilla ; le jour de l'icône, ce sera un PNG et un identifiant, **sans code client** (l'atlas `map_decorations` ramasse tous les namespaces, lu dans les sources). ⚠️ Ma question à Jérôme parlait de « la croix rouge du manoir » : faux, l'icône du manoir est un pictogramme de manoir ; les deux réponses (croix rouge, teinte du manoir) ne se conciliaient qu'avec ce type à nous. Vérifié aussi, et reporté en §4.5 : la recherche de la carte passe par la vraie génération des *starts*, donc **notre mixin de bordure s'applique** et la carte ne peut pas viser un sanctuaire refusé. Contrôles : build vert, serveur de dev démarré sans erreur de chargement des registres (un échange invalide y aurait fait échouer le démarrage). **Dégât collatéral** : ce démarrage passait par le lanceur headless du scan, qui nomme son rapport d'après la graine — le rapport brut du 14/09 (`run/mastersword-scan-20260914.txt`, hors git) a été écrasé ; ses chiffres restent dans §9. **Testé en jeu par Jérôme** : 4 offres à chaque cartographe, la carte fonctionne ; distance et temps de gel non mesurés. |
| 14/09/2026 | **Étape 16 : le sanctuaire exige désormais que toute son emprise soit en Dark Forest.** Jérôme a choisi cette ligne du tableau mesuré le matin même : bordures ramenées de 38 % à 17 %, au prix d'un quart des sanctuaires. Deux portes se sont fermées d'elles-mêmes avant d'écrire : **`JigsawStructure` est `final`** (pas de `StructureType` maison qui en hériterait) et **`Structure.isValidBiome` est `private static` sans référence vers la structure** (impossible d'y limiter l'effet au nôtre). Reste `Structure.generate`, dont la signature porte déjà le `Holder<Structure>`, le `BiomeSource`, le `RandomState` et le prédicat de biomes valides : un `@Inject` à `RETURN` qui renvoie `INVALID_START`, c'est-à-dire exactement le chemin d'échec que vanilla emprunte quand son propre test de biome échoue. La règle vit dans `ShrineStructure` et **le scan l'appelle littéralement** : mesure et mise en œuvre ne peuvent pas diverger. **Le contrôle du scan a attrapé la première version.** Elle testait `StructureStart.getBoundingBox()`, qui passe par `adjustBoundingBox` et **gonfle l'emprise de 12 blocs** dans chaque direction dès que `terrain_adaptation` n'est pas `NONE` — la nôtre est `beard_thin`. Le mixin jugeait donc un carré de 41×41 au lieu de 17×17 et ne gardait que 82 sanctuaires sur 224 ; le rapport a annoncé 76 désaccords avec le jeu et s'est déclaré nul, ce qui est précisément ce pour quoi le contrôle avait été écrit une heure plus tôt. Corrigé en `start.getPieces()`. **Vérification de bout en bout** : le scan reproduit alors la prédiction au sanctuaire près — **158 gardés et 19,0 % en bordure** sur la seed de Jérôme là où la règle promettait 158 et 19,0 %, **203 et 14,8 %** sur la seconde — et le contrôle passe à zéro désaccord sur 27 951 candidats. L'export de bytecode prévu au plan n'a pas été fait : `mixins.json` étant en `required: true`, un injecteur qui ne trouve pas sa cible empêche le serveur de démarrer, et le contrôle prouve le comportement, pas seulement la présence. **Essai en jeu le 14/09** : Jérôme a visité **cinq ou six sanctuaires dans plusieurs mondes** et valide — « légèrement mieux, ça spawn un peu moins en bordure ». ⚠️ **Cet essai confirme le sens, pas l'ampleur** : à 5 ou 6 sanctuaires, la règle de 17 % en donne environ un en bordure et l'ancienne de 38 % environ deux — l'écart est trop petit pour se sentir, et « légèrement mieux » ne contredit donc pas la mesure. À retenir surtout : **un sanctuaire sur six reste au bord, c'est le résultat attendu et non un échec** ; en voir un ne rouvre rien. Ce qui n'a toujours pas été éprouvé, c'est la **raréfaction d'un quart**, qui ne se sent qu'en jouant longtemps — et c'est pour elle que la piste « déplacer le sanctuaire dans son chunk plutôt que d'y renoncer » (§9) reste ouverte. |
| 14/09/2026 | **Le taux de sanctuaires en bordure est mesuré, et la pré-génération prévue pour y arriver est abandonnée.** SPEC prévoyait de pré-générer des chunks de Dark Forest et de relancer `biome_edge.js` dessus. C'était sans issue, pour une raison d'arithmétique : un candidat par cellule de 72×72 chunks, presque toujours hors Dark Forest, donc de l'ordre du **million de chunks** pour cent sanctuaires. Le placement se calcule **sans générer un seul chunk** — `getPotentialStructureChunk`, `isStructureChunk` et surtout `Structure.findValidGenerationPoint` sont publics, `GenerationContext` a un constructeur public, et `BiomeSource.getNoiseBiome` + `RandomState.sampler()` donnent les biomes. D'où `worldgen/ShrineScan.java` : 224 sanctuaires en 80 secondes là où la pré-génération aurait tourné des heures. **Rien de la décision vanilla n'y est réimplémenté** : le verdict vient de `findValidGenerationPoint` avec le vrai prédicat (`structure.biomes()::contains`, lu au bytecode de `ChunkGenerator`), et un second appel avec `b -> true` ne sert qu'à récupérer l'ancre et l'emprise des candidats refusés, pour noter les règles alternatives. **Recoupement obligatoire avant d'exploiter le moindre chiffre** : sur le monde de Jérôme, le scan retrouve le sanctuaire connu et annonce 4 blocs d'une rivière, ce que `biome_edge.js` lit dans les chunks sauvegardés par un chemin tout autre — au passage, **les « 8 blocs » notés ici le 13/09 étaient faux**. Résultat, sur deux seeds et 487 sanctuaires : **34 à 43 % à moins de 16 blocs d'un autre biome**, rivière dans 30 % des cas. Jérôme avait raison, et le chiffre est maintenant un chiffre. **Trois pièges payés comptant, tous versés au mémo machine.** (1) `getPotentialStructureChunk(seed, x, z)` prend des **coordonnées de chunk**, pas un index de cellule — elle fait le `floorDiv` par `spacing` elle-même ; lui passer l'index ne plante pas, ça replie les 441 cellules sur les quatre autour de l'origine, et le premier rapport annonçait alors **deux biomes pour le monde entier** et zéro sanctuaire. Ce sont ces deux biomes qui ont trahi le bug, pas une erreur. (2) **Sur le serveur dédié de dev, aucune commande console ne passe** — toutes échouent sans trace, `list` et `seed` vanilla compris, vérifié avec l'arbre de commandes du mod retiré du build ; d'où le déclenchement par `ServerLifecycleEvents.SERVER_STARTED` et `MASTERSWORD_SCAN`. (3) Le **watchdog tue le serveur à 60 s de tick** : un scan large doit partir sur un autre thread même quand personne ne joue. **Et la relecture Opus a défait une affirmation que cette spec donnait pour établie** : le point que `isValidBiome` teste n'est **pas** le coin nord-ouest du chunk. Ce coin n'est que l'argument passé à `JigsawPlacement.addPieces` ; le `GenerationStub` qui en revient est bâti sur `(maxX + minX) / 2` de l'emprise de la pièce de départ, soit le **centre du sanctuaire**. Confondre l'argument et le résultat rendait « recentrer l'ancre » crédible, alors qu'il n'y a rien à recentrer — et la mesure le confirmait déjà sans qu'on sache pourquoi, la règle « centre du chunk » ne changeant rien. Le scan porte désormais un **contrôle** : il recalcule le test de vanilla au point du stub et le compare au verdict du jeu sur chaque candidat — **zéro désaccord sur 27 951 candidats**, deux seeds. La même relecture a aussi montré que la règle « emprise entière » n'échantillonnait que 5 cellules sur 25 à 36 ; elle les parcourt toutes depuis. **Le correctif n'est pas choisi** — le tableau des règles est en §9. |
| 13/09/2026 | **Audit des API, et le brouillard réparé sous Sodium.** L'audit (`AUDIT-API.md`, 42 jars de l'instance et le jar 26.2 passés à `javap`) a surtout servi à **défaire une affirmation de cette spec**. §5 disait que Sodium n'injectait pas au même endroit que nous, preuve à l'appui : « son rappel renvoie un `Vector4f` ». Faux — c'était le **générique de son `CallbackInfoReturnable`**, reste d'une signature plus ancienne, effacé à l'exécution ; le champ `method` de son annotation dit `setupFog` et son `@At` dit `RETURN`. **Un générique ne prouve pas la méthode visée**, et cette erreur de lecture avait fait classer la fonctionnalité comme irréparable, en écartant nommément la seule piste qui marchait. Le correctif est **un attribut** : `priority = 500`. Une priorité **basse** est appliquée en premier donc **s'exécute** en premier (`MixinInfo.compareTo` trie croissant, `applyMixins()` parcourt le `SortedSet` dans cet ordre, un `@Inject` à `RETURN` insère avant le `areturn`) — monter la priorité aurait aggravé le défaut, ce qui explique probablement l'échec de la tentative d'alors. **Vérifié sur le bytecode fusionné, avec témoin** : Sodium 0.9.1 et Iris 1.11.2 déposés dans `run/mods` (possible sans remapping, il n'y a plus d'`intermediary` depuis 26.1), `-Dmixin.debug.export=true`, et les deux exports lus à `javap`. Sans priorité : `iris$render`, `sodium$storeFogParameters`, **puis** `mastersword$thickenNearShrine` — Sodium photographiait le brouillard avant qu'on y touche. Avec : `mastersword$thickenNearShrine` **en tête**, Sodium en dernier. Pas de sous-agent de relecture : l'unique risque du changement était le **sens** du tri, et le témoin le tranche mieux qu'une lecture. **Essai en jeu le 13/09** — Sodium et Iris installés, shaders coupés, le brouillard est là. ⚠️ Mais **cet essai ne valide pas le correctif** : la même configuration n'avait jamais été testée *avant*, le premier constat s'étant fait shaders chargés. Seul le témoin sur le bytecode établit que Sodium photographiait avant nous, et il tournait avec 3 mods là où l'instance en a 42 — l'ordre à priorité égale dépendant du chargement des mods, il a pu tomber autrement chez Jérôme. Ce qui reste acquis sans réserve : l'ordre n'est plus laissé au hasard. **Toujours absent sous shaderpack**, et le relevé de son pack a clos la question : `Complementary Reimagined r5.8.1` ne contient **aucune occurrence** de `fogEnd`, `fogStart`, `fogDensity`, `fogMode` ni `fogShape` — son brouillard de surface est entièrement le sien, et les deux seuls états du jeu qu'il lit à l'air libre (`blindness`, `darknessFactor`) assombrissent au lieu de brumer. Aucune valeur à bouger, donc aucune API n'y aurait changé quoi que ce soit. Jérôme tranche : **on en reste là** ; les deux suites possibles étaient un brouillard dessiné en géométrie propre ou un effet Cécité, écartées pour leur coût et pour le gameplay. Leçon transposable, versée au mémo : **un shaderpack est du GLSL en clair — `grep -rn fogEnd .` avant de promettre quoi que ce soit de « compatible shaders »**. Trois autres résultats de l'audit, non faits : aucun mod de structures de l'instance n'utilise de bibliothèque de placement ni de `StructurePlacement` custom (l'option B de §4.1 n'est faite **par personne**) ; **l'option B ne corrigerait pas les bordures de biome**, car `Structure.isValidBiome` ne teste **qu'un point**, au coin nord-ouest du chunk — mesuré sur le seul sanctuaire généré de nos mondes, à 8 blocs d'une rivière ; et le vrai manque de notre approche est le processor `minecraft:rule`, dont le `random_block_match` tire sur la **position absolue** (`Mth.getSeed(pos)`) alors que le tirage de `builder.js` est figé dans le `.nbt` — tous nos sanctuaires sont identiques au bloc près. |
| 13/09/2026 | **La Cascade secrète est abandonnée ; le mod ne garde que la clairière.** Quatre versions essayées en jeu, aucune convaincante — le détail est en §4.2. Deux leçons valent plus que la variante. **Un terrain ne se transplante pas** : la capture du build de Jérôme était aux trois quarts du sol naturel, et posée sur un autre monde, aplani par `beard_thin`, elle est ressortie en pierre suspendue en l'air. Et **l'eau obéit à une règle simple que je n'avais pas su lire** : le niveau d'un écoulement est sa distance à la source, un par bloc, et une case n'est `falling` que si elle est alimentée par le dessus. Les deux ont été trouvées en **lisant le monde sauvegardé**, pas la documentation : `tools/structure/anvil.js` (lecteur de région Anvil) et `capture.js` sont nés de là et restent, non plus pour produire une variante mais pour savoir ce qu'une génération a vraiment produit. Le `template_pool` ne contient plus qu'un `element`. |
| 13/09/2026 | **Étape 13 : la contrainte des arbres est levée, et les deux variantes sont refaites.** L'essai en jeu de l'étape 12 était sans appel — « trop carré », la cascade « pas jolie », un « mur de 4 blocs » au nord — et les trois défauts avaient la même cause : le sol ne pouvait être que minéral, et le `.nbt` remplissait sa boîte d'air et de pierre. **Trois leviers, tous vérifiés avant d'écrire.** (1) Un **verrou Java** (§4.4) remplace la palette : trois mixins annulent tout arbre, champignon géant ou tronc couché dont l'origine tombe dans un disque autour du socle. (2) La **liste de blocs d'un `.nbt` peut être creuse** — `placeInWorld` itère la liste et n'exige aucune couverture, vanilla le fait lui-même (`taiga_decoration_1` ne liste que 22 de ses 36 cases) — donc l'emprise a enfin une silhouette. `structure_void` aurait été **posé tel quel** : le jigsaw n'ajoute que `BlockIgnoreProcessor.STRUCTURE_BLOCK`. (3) Deux blocs couleur terre non enracinables trouvés en chemin, `dirt_path` et `packed_mud`. **L'erreur de fond, trouvée par la relecture Opus : `y=0` est le bloc de surface, pas l'air au-dessus** — `JigsawPlacement` ancre sur `box.minY() + getGroundLevelDelta()` avec un delta de **1**. Toute la prairie que je posais en `y=0` *remplaçait* la motte : l'essai en jeu montrait des cuvettes d'un bloc semées autour de la terrasse. Décor remonté en `y=1`, remplissage d'air démarré à `y=1`. **Second chiffre corrigé : le `random_selector` tire un arbre à 92 %, pas 2/3** — `birch_leaf_litter`, `fancy_oak_leaf_litter` et le défaut `oak_leaf_litter` sont aussi des features `minecraft:tree` — soit **5,8 %** de risque par case de terre à découvert et non 4 %. **Trois réglages demandés par Jérôme en jeu** : le disque du verrou resserré deux fois, jusqu'à un **rayon constant de 5** (le dimensionner sur l'emprise laissait un anneau d'herbe nue entre le sanctuaire et la forêt) ; **cinq chênes noirs plantés par la clairière elle-même**, pour remettre de la canopée ; et la cascade refaite en **deux étages** d'après une référence, parce qu'une colonne d'eau isolée « ne fait pas très naturel ». Le deux-étages tient sans un seul état d'écoulement intermédiaire parce que **chaque étage a sa source murée**, la vasque intermédiaire servant de quatrième mur à la seconde. **Relecture Opus, deux passes.** La première a bloqué le commit sur trois points, tous corrigés : le `y=0`, les champignons géants et troncs couchés que `TreeFeature` ne voyait pas, et `clearSkyOverBuiltColumns` qui écrivait en `y=0`. La seconde a validé l'eau à deux étages cellule par cellule, et corrigé deux affirmations : « une source s'étale toujours latéralement » est **incomplet** (`spread` sort tôt si elle peut couler vers le bas, sauf à partir de trois sources voisines — notre contrôle est donc plus strict que le jeu, volontairement), et le commentaire qui prétendait que le rayon du script « reflète exactement » celui du Java, alors qu'il est recopié à la main. Clairière validée par Jérôme le 13/09. |
| 12/09/2026 | **Étape 12 : la Cascade secrète, seconde variante.** Un affleurement mousseux 15×15×11, deux chutes, un bassin, le socle sur un îlot. **Aucune ligne de Java** : un `variants/waterfall.js` de plus, un `.nbt` de plus, un second `element` dans le `template_pool` — exactement ce que le découpage de l'étape 11 promettait. **Le point qui a demandé le travail : l'eau d'un `.nbt` ne coule pas.** `SinglePoolElement` place en flags **18**, sans `UPDATE_NEIGHBORS`, et `ProtoChunk.setBlockState` ne programme aucun tick de fluide — l'eau reste figée jusqu'à ce qu'un joueur casse un bloc à côté, et là tout se réveille. D'où le choix d'**écrire l'équilibre** plutôt qu'une eau à faire couler : lèvres murées, colonnes en `level=8` toujours au-dessus d'eau, cuvette étanche. Trois relevés le fondent (`LiquidBlock.stateCache` pour la correspondance des `level`, `FlowingFluid.spread` pour « une source s'étale toujours », `isWaterHole` pour « une chute au-dessus d'eau ne s'élargit jamais »). Gain de passage : le `surface_water_depth_filter` de `dark_forest_vegetation` rejette toute colonne mouillée, donc **le fond du bassin a droit à de la vraie terre** — 37 cases de mousse, podzol, terre grossière et argile, les seules des deux fichiers. Quatre contrôles ajoutés à `builder.js`, et celui de la terre détendu pour accepter l'eau comme couverture. **Relecture Opus** : verdict favorable, et deux prises qui ne mordaient pas encore mais qui auraient mordu à la variante suivante. **« Bloquer le mouvement » n'est pas « retenir l'eau »** — `canPassThroughWall` teste l'*identité* avec le cube plein, donc dalles, escaliers et murets laissent fuir un bassin tout en passant le contrôle ; d'où un ensemble `FULL_CUBE` distinct, dont sont aussi exclus les cubes *waterloggables* qui se remplissent au lieu de retenir. Et le contrôle de source ne regardait **que les 4 côtés**, alors que `spread` essaie le bas en premier. Elle a aussi corrigé une erreur de fait que j'avais propagée jusque dans le mémo machine : le `surface_water_depth_filter` est sur `dark_forest_vegetation`, pas sur `dark_oak_leaf_litter` qui ne porte que le prédicat de plant ; et le tag `#supports_vegetation` compte **onze** blocs, pas treize. Enfin, la falaise est **plafonnée à 4 sur sa rangée arrière** : au ras du bord d'emprise, `beard_thin` ne construit rien derrière, et une paroi de 8 aurait été un mur nu planté dans la forêt. **Choix de Jérôme** : le bassin reste à **un bloc de profondeur** — creuser aurait voulu dire monter les berges d'un cran, et le profil bas prime. |
| 12/09/2026 | **Étape 11 : la ruine plate devient une clairière forestière.** D'après un rendu fourni par Jérôme ; la Cascade secrète suivra comme seconde variante. **Aucune ligne de Java**, un `.nbt` de plus et un `template_pool` repointé. L'outillage est découpé — `builder.js` (canevas, tirage, **auto-contrôles**) + `variants/<id>.js` + un lanceur — pour que la cascade ne soit qu'un fichier de plus, et les contrôles sont rejoués **par fichier**, comme §4.2 le demandait. **Le vrai apport de l'étape est une correction de fait** : la règle « aucun bloc de `#minecraft:dirt` » de l'étape 8 était la mauvaise liste. Le verrou est `#minecraft:supports_vegetation` (**onze** blocs), lu par `would_survive(dark_oak_sapling)` sur la heightmap `OCEAN_FLOOR`, tiré **16 fois par chunk** avec 2/3 de chance — soit **~4 % de risque d'arbre par case de terre à découvert**, et un tronc qui **retourne en terre les 4 cases sous lui** en se penchant de 2 blocs. Trois parades vérifiées, dont deux nouvelles : l'**abri** (la heightmap ne voit que le sommet bloquant) et l'**eau** (`surface_water_depth_filter` à `max_water_depth: 0` rejette toute colonne mouillée) — cette dernière rendra un vrai sol de terre à la cascade. Le tout reporté dans `../SETUP-MC-MODDING.md`, où la vieille formule était aussi écrite. **Deux retours de Jérôme en jeu** : plateforme **descendue** de deux gradins à un seul (sommet du socle à 2 blocs du sol, non 4), et **toute l'herbe retirée** — puisqu'elle exigeait des abris, autant supprimer la terre et faire le vert aux **blocs de feuilles**, tapis de mousse, litière et racines de palétuvier. Le fichier ne contient donc plus **aucun** bloc enracinable, et l'invariant devient trivial. **Relecture Opus** : a attrapé les champignons — un petit `red_mushroom` sur la pierre à ciel ouvert exige une luminosité < 13, or il fait 15 en plein jour, donc il survit à la génération puis **disparaît au premier bloc cassé à côté** ; remplacés par des **gros champignons** (`mushroom_stem` + `red_mushroom_block`), qui n'ont aucune règle de survie. Elle a aussi trouvé un moignon posé sur une **demi-dalle** du bord de terrasse, donc en lévitation d'un demi-bloc, et relevé que j'écrivais « treize blocs » pour une liste qui en compte onze. Trois contrôles ajoutés dans la foulée : aucun bloc isolé en plein air, colonne du socle dégagée, et pavage exigé **bloquant** et non plus seulement « non-air ». Deux bugs trouvés avant elle en relisant les cartes couche par couche : les touffes de feuilles étaient indexées `[x, y, z]` au lieu de `[x, z, y]`, l'une au sol et l'autre **flottant à `y=7`**. |
| 12/09/2026 | **Étape 10 faite : le plan est terminé.** Sons, particules, advancement et traductions, **sans un seul asset nouveau** — que des sons et des particules vanilla, faute de pouvoir composer un `.ogg`. Les effets continus se partagent selon leur propriétaire : les étincelles suivent **l'épée** (n'importe quel socle), le bourdonnement suit **le lieu** (sanctuaire non réclamé, même condition que le brouillard), sans quoi replanter l'épée chez soi ferait ronronner un socle décoratif à jamais. L'advancement repose sur un **déclencheur maison** enregistré dans `BuiltInRegistries.TRIGGER_TYPES` — public, donc sans mixin, `CriteriaTriggers.register` étant privé — parce qu'`inventory_changed` se serait déclenché sur un `/give` ou en créatif. Le Java reste générique, le JSON choisit l'épée qui compte : c'est la forme d'`UsedTotemTrigger`. **Pas de sous-agent de relecture**, aucune catégorie obligatoire de `CLAUDE.md` n'étant touchée ; vérifié par le build, la validation des JSON, le compte d'advancements chargés (1689 contre 1688 en vanilla, preuve que le nôtre est parsé) et l'essai en jeu. ⚠️ L'essai a justement trouvé ce que la relecture aurait vu : **`Inventory.add` vide la pile qu'on lui passe**, et `ItemStack.getItem()` répond `AIR` à compte nul, donc tester « est-ce l'Épée de Légende ? » après l'avoir donnée au joueur sautait en silence le son, la gerbe **et** l'advancement. L'identité se lit désormais avant la remise. Second retour de Jérôme : la note ajoutée à la pose est retirée, le son redevient celui de toutes les épées. |
| 12/09/2026 | **Étape 9 faite : le brouillard.** Deux surprises, toutes deux dans le sens du moins de code. **Aucun paquet réseau n'a été écrit** : `getUpdateTag` renvoie `saveCustomOnly` depuis l'étape 7, donc l'état complet du socle était déjà côté client — la « synchro serveur → client » annoncée au plan était faite d'avance. Et **la `SavedData` de §5 a été abandonnée** au profit d'un drapeau **`shrine`**, posé uniquement par le `.nbt` de la structure et sans setter en Java (choix de Jérôme). Elle ne voyait pas le cas qui compte : replanter l'épée dans un socle posé chez soi aurait levé un brouillard permanent sur la base. Le drapeau règle les deux, et meurt avec le socle qu'on casse. Côté rendu, **Fabric n'a aucune API de brouillard** — les 66 jars du cache passés en revue, zéro classe qui mentionne `Fog` — donc un mixin sur `FogRenderer.setupFog`, public et qui **renvoie** le `FogData` : injection en `RETURN`, ni `@Local` ni ordinal, une config de mixins client séparée pour les source sets. Tout est en `Math.min` contre ce que vanilla a posé : le brouillard ne peut qu'ajouter. **Relecture Opus** : a attrapé un bug qui rendait la fonctionnalité **totalement inopérante**. Filtrer sur `isShrine()` dans l'écouteur `BLOCK_ENTITY_LOAD` ne marche pas — Fabric tire l'événement depuis `LevelChunk.setBlockEntity`, au `Map.put`, soit l'offset 5 de `lambda$replaceWithPacketData$0`, alors que `loadWithComponents` ne lit le NBT qu'à l'offset 52 : au moment de l'événement **tous les champs valent encore leur défaut**. Vérifié au bytecode avant correction. Le tri se fait désormais par image, après chargement. Deux autres prises au passage : la recoloration n'était pas gardée et **rendait de la vue sous Cécité** (les distances, elles, étaient inattaquables), et `remove(clé)` au déchargement pouvait effacer l'entrée du socle **suivant** à la même position, vanilla posant le nouveau avant de retirer l'ancien. **Deux essais en jeu pour calibrer la courbe**, chacun sur une erreur de fait plutôt que de code. Un brouillard se lit comme un **rapport** de distances : interpoler droit laissait la portée à 91 blocs quand le joueur était à 20 blocs du socle, invisible sous la canopée — d'où une division de la portée à chaque pas, et l'abandon de la courbe quadratique de §5 qui aggravait le même défaut. Puis **`FOG_END_DISTANCE` vaut 1024 et non la distance de rendu** (celle-ci est dans l'autre paire de bornes du shader, combinée par un `max`), et aucun biome vanilla ne la surcharge : diviser 1024 demande une rampe en **racine carrée** — l'inverse exact du carré de départ — sans quoi l'épée restait brumée à 55 % seulement à 20 blocs. Relevé au passage et corrigé dans le code comme dans le mémo : le shader lit `d <= fogStart` comme **aucun** brouillard, donc un `start` qui dépasse `end` ne l'affaiblit pas, il le supprime. Rendu validé par Jérôme le 12/09/2026. |
| 12/09/2026 | **Étape 8 faite : la structure se génère.** Tout en données, **zéro ligne de Java** — c'est l'option A de §4.1. Quatre fichiers : le `structure_set` (spacing **72**, separation 20, `triangular`, salt 48271393, `exclusion_zone` vers `minecraft:woodland_mansions` à `chunk_count: 8` = 128 blocs), la `structure` jigsaw de surface, le `template_pool` à un seul `single_pool_element`, et un tag de biome sur `minecraft:dark_forest`. Le `.nbt` est **produit par script** (`tools/structure/`, avec un encodeur NBT maison, relu après écriture) plutôt qu'enregistré depuis un structure block : il reste diffable et reproductible, et rouvrable en jeu pour retouche. **Le socle porte ses données de `BlockEntity` dans le `.nbt`** — comme les coffres vanilla portent leur loot table — donc l'épée est plantée dès la génération, sans une ligne de code. Deux pièges payés comptant. **`processors` est obligatoire** dans un `single_pool_element` : l'omettre ne fait rien au démarrage mais **crashe à la création du monde** (`No key processors in MapLike`). Et **`surface_structures` s'exécute avant `vegetal_decoration`** : les arbres sont posés *après* la structure et poussaient au travers — creuser de l'air n'y change rien, seul un sol non enracinable le fait. D'où un sol **100 % pierre** (aucun bloc de `#minecraft:dirt`) et une clairière élargie à 9×9, un tronc de dark oak faisant 2×2. Le décor gagne au passage 8 blocs différents au lieu de 2, par tirage pondéré déterministe. Socle re-texturé en `stone_bricks` / `mossy_stone_bricks` / `chiseled_stone_bricks` : le deepslate jurait avec la ruine grise (§4.2 mis à jour). Contradiction de §4.1 levée : `spacing` vaut **72**, et il ne sera configurable qu'avec l'option B. **Relecture Opus** : a attrapé une contradiction entre le commentaire et le code — le bord irrégulier sautait des cases au hasard, qui gardaient l'herbe d'origine et laissaient **6 carrés 2×2 de sol nu dans l'emprise**, le plus proche à 4,3 blocs du socle, de quoi enraciner le dark oak qu'on prétendait avoir exclu. Corrigé : l'emprise 9×9 est **entièrement pavée**, l'irrégularité vient de la matière et non de trous, et le script vérifie les 81 cases à chaque génération. §4.1 reformulée dans la foulée : les 120 blocs ne sont tenus que pour le manoir, ni pour le portail en ruine ni pour les structures des autres mods. |
| 11/09/2026 | **Apparence de l'épée : modèle 3D maison v3, 22 cubes** (avance sur l'étape 10). Le mod embarque **uniquement des assets originaux** : `models/item/master_sword_3d.json` + sa texture 64×64, sculptés dans Blockbench via son plugin MCP. Proportions relevées sur une référence fournie par Jérôme : **lame 69 % de la longueur totale, garde 10 %, manche 21 %** — les versions précédentes plafonnaient à 58 % de lame pour une garde deux fois trop large. La lame gagne sa longueur en descendant le pommeau à **y = −4** : le format autorise −16..32, et passer sous zéro rallonge sans rien sacrifier. Deux erreurs corrigées au passage, toutes deux répétées : **les ailes de la garde pointent vers le bas**, pas vers le haut ; et un motif peint sur la face de la lame est **invisible**, car l'arête centrale est en relief en z et le recouvre — il va sur l'arête. L'or est réduit à la gemme de garde et deux accents de ricasso, le reste est violet et vert. Les v1 (13 cubes) et v2 (26) sont conservées dans `tools/model/`. ⚠️ **Le modèle de Moubarack sort du dépôt** : il est livré comme **resource pack séparé** (`../MasterSword-Moubarack-Mauve/` aux couleurs d’origine, `-Bleu/` pour la version recolorée) qui surcharge `master_sword_3d` au même chemin — pack activé, on voit Moubarack ; désactivé, le modèle maison. Rien à modifier dans le mod pour basculer, et **la question §9 n°2 est close : le dépôt est publiable**. `pack_format` de 26.2 = **88** (`resource_major` du `version.json` client). `BLADE_DOWN` **reste à 135** : l'avoir passé à 180 pour un modèle vertical avait cassé le rendu de toutes les épées vanilla, que le socle accepte aussi via `#swords` — c'est au modèle de se plier à la convention du sprite, pas au socle de se plier à un modèle. |
| 11/09/2026 | **Étape 7 faite.** `PedestalBlock extends BaseEntityBlock` + `PedestalBlockEntity`, rendu de l'épée par `BlockEntityRendererRegistry` (Fabric : `BlockEntityRenderers` n'a pas de `register` en 26.2). Deux pièges trouvés en lisant le bytecode vanilla plutôt qu'en supposant : `useWithoutItem` n'est atteint que si `useItemOn` renvoie `TRY_WITH_EMPTY_HAND`, **`PASS` ne retombe pas dessus** ; et les contenus se lâchent depuis `BlockEntity.preRemoveSideEffects`, appelé par `LevelChunk.setBlockState`, et non depuis `affectNeighborsAfterRemoval` où vanilla ne fait qu'avertir les voisins. Choix de Jérôme : **le socle soigne l'épée**, d'où `MasterSwordItem.regenerate` rendu public et renvoyant un booléen pour ne resynchroniser que sur changement réel. Textures vanilla pour l'instant. Pas de section de config : le socle n'a aucune valeur à régler. |
| 11/09/2026 | Ajustements après essai en jeu. **Orientation** : propriété `facing` sur quatre directions, pour aligner une rangée de socles aux lames différemment tournées — la pierre est symétrique, l'orientation ne se voit que dans l'épée. **N'importe quelle épée** de `#minecraft:swords` est acceptée, d'où deux garde-fous sur la régénération et sur le drapeau de brouillard (§4.3). L'épée est **immobile et plantée** au lieu de flotter en tournant : la bonne rotation est **135°** et non 45°, la transformation `FIXED` appliquant déjà un demi-tour autour de Y. Inventaire plein au retrait : l'épée est posée sur le socle et non aux pieds du joueur. |
| 06/09/2026 | Spécification initiale rédigée. `CLAUDE.md` allégé : les specs vivent ici. |
| 10/09/2026 | **Étape 6 faite.** `LightWaveEntity extends Projectile`, tirée par **deux** mixins — `ServerPlayerMixin` sur `swing` pour le balayage à vide, `PlayerAttackMixin` avec `@Local` sur `fullStrengthAttack` pour le coup qui touche. Motif : `Player.attack` appelle `onAttack()` — donc remet `attackStrengthTicker` à zéro — **avant** de calculer `fullStrengthAttack`, donc la charge est illisible depuis `postHurtEnemy`. Rendu par un quad plat émissif, couché parallèlement au sol. Section `light_wave` dans la config. Question ouverte n° 1 fermée : **tirer** la vague remet le compteur de régénération à zéro. |
| 10/09/2026 | Corrections de relecture sur l'étape 6. **`width` ne servait à rien** : la surcharge courte de `getManyEntityHitResult` ignore l'`AABB` pour le test de touche et retombe sur `computeMargin` (≤ 0,3), la vague était une ligne — passée à la surcharge à neuf paramètres. **La condition de pleine vie refusait le tir à 19,6 PV** alors que le HUD montre dix cœurs pleins : comparaison passée en `Mth.ceil`, comme le HUD. Plus le culling du quad (2,8 blocs contre une hitbox de 0,6), `shouldBeSaved → false` avec plafond de 200 ticks, et l'exclusion explicite du lanceur. Sons d'impact ajoutés au passage, son du tir monté à 1,5. Deux conséquences assumées par Jérôme : la vague ignore armure et bouclier, et miner avec l'épée en tire une. |
| 10/09/2026 | Après essai en jeu, deux ajustements demandés par Jérôme : la régénération ne démarre plus qu'**en dessous de 50 % de durabilité** (`regen_starts_below`, déclencheur et non plafond — elle remonte ensuite jusqu'à 100 %), et la vague exige que le **joueur soit à pleine vie** (`requires_full_health`). Le seuil a demandé de ne réécrire l'horodatage dans `markCombatUse` que si le composant existe déjà, sans quoi un coup porté à 90 % de durabilité aurait armé la guérison en contournant le seuil. Vague repassée à l'horizontale. |
| 09/09/2026 | **Étape 5 faite.** Exclusivité entre enchantements levée pour la seule Master Sword, par trois mixins aux trois seuls appelants de `areCompatible` : `AnvilMenuMixin`, `EnchantmentHelperMixin`, `EnchantCommandMixin`. Aucune API Fabric ne pouvait le faire par item — `EnchantmentEvents.MODIFY` est global. Table **et** enclume couvertes (§2.4). Piège évité : `filterCompatibleEnchantments` empêche aussi de repiocher deux fois le même enchantement, donc le mixin le remplace au lieu de le sauter. Testé en jeu avec le contrôle sur l'épée netherite, qui refuse toujours. |
| 07/09/2026 | **Étape 4 faite.** Composant `mastersword:last_combat_use` (un `long`, `persistent` + `ignoreSwapAnimation`), régénération dans `inventoryTick`, remise à zéro dans `postHurtEnemy`, clé `full_regen_days` dans la config. **Correction majeure de §2.3** : dormir n'avance pas `getGameTime()` en 26.2 — le cycle jour/nuit est passé dans `ServerClockManager`, on suit `getOverworldClockTime()`. La fusion de deux épées garde le comportement par défaut, sans mixin (§2.2). Testé en jeu : `/time add`, le lit, le recul de l'horloge, la reprise depuis un coffre. **Corrections de relecture** : temps consommé arrondi au **plafond** et non au plancher — le plancher rendait la régénération 2,68 % trop rapide aux défauts et 49 % en config extrême (mesuré par simulation tick par tick) ; et la fenêtre de réversibilité de `max_durability` n'est plus « jusqu'au prochain coup » mais quelques secondes, puisque la régénération écrit désormais toute seule. |
| 06/09/2026 | **Étape 3 faite.** `config/mastersword.json` lu par `Codec` + `JsonOps`, section `item` seulement — une clé qui ne fait rien est pire qu'une clé absente, les autres sections viendront avec leurs étapes. `/mastersword reload` via `CommandRegistrationCallback`. `ItemStackMixin` sur `getMaxDamage()I` et `isDamageableItem()Z`. `MasterSwordItem` créée dès maintenant pour que le mixin teste un `instanceof` sans réveiller le `<clinit>` de `ModItems`. Deux pièges rencontrés : `optionalFieldOf` omet les valeurs par défaut à l'encodage (d'où deux codecs), et `CommandSourceStack.hasPermission(int)` n'existe plus en 26.2. |
| 06/09/2026 | Texture repassée en **64×64** d'après le proto de Jérôme (garde ailée, losanges dorés, manche tressé), redressée à 45° parce que `handheld` ajoute 55°. Jérôme reprend la texture définitive de son côté ; le contrat de remplacement est en §2.1. Question ouverte n° 1 (textures) fermée. |
| 06/09/2026 | **Étape 2 faite.** Item `mastersword:master_sword` enregistré, stats netherite via `Properties.sword(...)`, rareté EPIC, texture 16×16 dessinée, traductions fr/en, injection dans l'onglet créatif **Combat** juste après l'épée en netherite (`CreativeModeTabEvents.modifyOutputEvent`, et non `ItemGroupEvents` qui n'existe pas en 26.2). Réparation par matériau neutralisée par un `Repairable(HolderSet.empty())` — voir §2.2, `sword()` la posait d'office. Testé en jeu : onglet, enclume, meule, table de craft. **Correctif après relecture** : l'épée n'était dans aucun tag, donc n'acceptait aucun enchantement et perdait l'attaque tournoyante — ajout à `#minecraft:swords`, voir §2.4. Enchantements et sweep confirmés en jeu ensuite. |
| 06/09/2026 | **Étape 1 faite.** Squelette Fabric 26.2 qui build (`BUILD SUCCESSFUL`, `mastersword-0.1.0.jar`). Package `re.jerome.mastersword` et `group=re.jerome`, alignés sur Argilus. Source sets séparés `src/main` / `src/client`. Versions revérifiées à la source : loader **0.19.5**, fabric-api **0.159.0+26.2**, loom 1.17.19. Licence MIT `Copyright (c) 2026 Jerome`. Dépôt git local initialisé, sans remote. |
| 06/09/2026 | `spacing` fixé à **72** et non 80 : à `spacing` égal, nos cellules s'alignent sur celles du manoir et `triangular` corrèle les deux tirages, ce qui ferait annuler l'épée par l'exclusion précisément dans les forêts à manoir. Confirmé au passage que l'exclusion est à sens unique et qu'aucun manoir n'est perdu. |
| 06/09/2026 | Génération élucidée sur le jar 26.2 (§4.1). La rareté du manoir vient de `spacing: 80` (cellules de 1280 blocs) **cumulé** au filtre de biome : on calque ce réglage, avec un `salt` différent. `exclusion_zone` existe en JSON vanilla (plafond 16 chunks, un seul `other_set`, `@Deprecated`) et couvre les 120 blocs sans code. Seules deux structures de surface partagent le Dark Forest : manoir et portail en ruine. Le `StructurePlacement` custom passe de « probablement nécessaire » à « repli si le test en jeu déçoit ». |
| 06/09/2026 | Relecture Jérôme. **Réparation autorisée** par combinaison de deux épées (enclume, meule, craft), interdite par matériau — remplace l'interdiction totale, et supprime le mixin d'enclume prévu. Enchantement table + enclume. Régénération **progressive**. Vague de lumière : **cooldown 15 s**, plus aucune condition de durabilité. Retrait sans condition ; remise possible, brouillard définitivement perdu. Licence **MIT**. Une épée par biome max. Distance aux autres structures **300 → 120 blocs**. Brouillard **100 → 50 blocs**. Ajout d'un **fichier de configuration** (§6) et de l'étape 3 correspondante. |
