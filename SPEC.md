# The Master Sword — spécification

Document vivant. Il décrit **ce qu'on construit** et **les décisions prises**.
Les instructions de travail permanentes sont dans `CLAUDE.md`, les pièges de la
machine et de Minecraft 26.2 dans `../SETUP-MC-MODDING.md`.

Statut : **étapes 1 à 7 faites** — l'épée, sa configuration, sa régénération, le
cumul d'enchantements, la vague de lumière et le socle. Restent la génération et
le brouillard (§8).
Dernière mise à jour : 11/09/2026.

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

**Le socle est fait (étape 7).**

- Un **socle** : bloc custom `master_sword_pedestal`, en pierre sombre, forme de
  demi-dalle épaisse avec une fente centrale. Il porte un `BlockEntity` qui
  connaît l'état de l'épée. Dureté 25 / résistance 1200, pioche obligatoire,
  `noOcclusion()` puisque la forme est en deux boîtes (1→15 sur 4 de haut, puis
  3→13 sur 8). Modèle JSON texturé en `polished_deepslate` / `deepslate_tiles` /
  `chiseled_deepslate` — une texture propre viendra à l'étape 10 sans toucher au
  code.
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
- Décor : 5×5 à 7×7 blocs — racines, mousse, pierres moussues, un soupçon de
  lumière au sol. Une structure NBT posée par un `single_pool_element`, pour
  rester éditable en jeu au block-editor.

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

Conséquence : l'état persistant du socle a besoin de **deux** informations
distinctes — « une épée est-elle posée ? » et « le brouillard a-t-il déjà été
consommé ? ». La seconde est irréversible. Les deux sont dans le `BlockEntity`
(`sword` par `ItemStack.OPTIONAL_CODEC`, `fog_consumed` en booléen) et partent
au client par `getUpdateTag` / `getUpdatePacket`, sans paquet custom.

⚠️ La `SavedData` globale annoncée en §5 **n'existe pas encore** : `fogConsumed`
ne vit que dans le `BlockEntity`, donc casser puis reposer un socle rendrait le
brouillard. Elle arrive à l'étape 9, quand il y aura un consommateur ; écrite
maintenant, elle serait du code que rien ne lit.

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
| `fog` | activé, rayon extérieur (50), rayon de densité maximale (10), couleur, intensité maximale |

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
│                            ModEntityTypes ✔, ModBlocks ✔, ModBlockEntities ✔
├─ item/MasterSwordItem      ✔ régénération ; l'attaque chargée à l'étape 6
├─ block/PedestalBlock ✔, PedestalBlockEntity ✔ socle
├─ entity/LightWaveEntity    ✔ créée
├─ worldgen/                 StructurePlacement custom (le reste en JSON data/)
└─ mixin/                    ItemStackMixin ✔ durabilité configurable,
                             AnvilMenuMixin ✔, EnchantmentHelperMixin ✔,
                             EnchantCommandMixin ✔ exclusivité levée,
                             ServerPlayerMixin ✔, PlayerAttackMixin ✔ vague

src/client/java/re/jerome/mastersword/client/
├─ MasterSwordClient         point d'entrée client (ClientModInitializer) ✔ créé
├─ PedestalRenderer         ✔ créé (+ PedestalRenderState)
├─ LightWaveRenderer      ✔ créé (+ LightWaveRenderState)
└─ FogHandler
```

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
   06/09/2026**, voir §4.1. Reste à vérifier : le format de `template_pool` et
   la pose d'une structure NBT en 26.2.
7. L'API de rendu du brouillard côté client en 26.2 (elle bouge souvent) et la
   façon propre de la moduler depuis un mod.
8. ~~Rendu d'un `BlockEntity` et d'une entité projectile plate~~ — **fait les
   10 et 11/09/2026**. Les deux suivent le même patron : `createRenderState` /
   `extractRenderState` / `submit`. Un item se dessine par
   `ItemModelResolver.updateForTopItem(..., null, 0)` puis
   `ItemStackRenderState.submit(...)`, avec le garde `isEmpty()` de vanilla.
9. `SavedData` en 26.2 : signature, `Codec`, et enregistrement par dimension.

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
| 8 | Génération | structure NBT, `structure_set` calqué sur le manoir, `exclusion_zone` | à faire |
| 9 | Brouillard | synchro serveur → client, courbe 50 → 10 blocs, disparition définitive | à faire |
| 10 | Finitions | sons, particules, advancement de retrait, traductions fr/en | à faire |

La config passe en étape 3, avant tout ce qui a des valeurs à régler : la
brancher après coup obligerait à repasser sur chaque fichier.

---

## 9. Questions ouvertes ❓

| # | Question | Impact |
| --- | --- | --- |
| 1 | Perdre les enchantements à la meule / table de craft est-il acceptable ? 🔷 oui, c'est vanilla | §2.2 |
| 2 | Le dépôt est-il destiné à être publié (GitHub, Modrinth) ? | README, bloc `contact` de `fabric.mod.json`, icône du mod |

---

## 10. Journal des décisions

| Date | Décision |
| --- | --- |
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
