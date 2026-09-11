# The Master Sword — instructions de travail

Mod Fabric pour Minecraft 26.2 : une épée légendaire plantée dans un socle,
générée en Dark Forest, gardée par un brouillard tant qu'elle n'est pas retirée.

## Les deux fichiers à lire

- **`SPEC.md`** — ce qu'on construit : specs confirmées, décisions techniques,
  plan d'implémentation, questions ouvertes. C'est la référence du projet.
- **`../SETUP-MC-MODDING.md`** — les pièges de cette machine et de Minecraft
  26.2 (versions Fabric vérifiées, erreurs de la doc officielle, méthode
  `javap`). À lire avant d'écrire du code Minecraft.

## Méthode de travail

- Toujours passer par le **mode plan** avant d'implémenter quoi que ce soit.
- **Ne rien coder de mémoire** sur les API Minecraft ou Fabric. Vérifier à
  `javap` sur les jars du cache Loom, ou dans les sources de `genSources`, avant
  d'écrire. Le savoir issu des versions 1.x est largement faux depuis 26.1.
- Sur ce qui n'est pas confirmé dans `SPEC.md`, **proposer plutôt qu'imposer**.

## Sous-agents : puissants et coûteux

Ils ont attrapé de vrais bugs — le tag `#swords` manquant, une affirmation
inversée sur le clamp de durabilité, une clé de config sans aucun effet. Mais ils
représentent **80 % de la consommation de tokens** du projet : chacun repart de
zéro, relance des dizaines de `javap` et regrepe les sources décompilées.

D'où quatre règles.

**Explorer soi-même par défaut.** Un sous-agent d'exploration seulement quand la
question résiste à quelques `javap`, à `../SETUP-MC-MODDING.md` et aux sources
décompilées. Une signature à confirmer, un nom de classe à retrouver : ça se fait
directement.

**Demander des conclusions, pas des dumps.** Les prompts qui disent « donne le
code intégral / verbatim / en entier » produisent des rapports de 60 à 75 Ko.
Demander la signature exacte et la réponse à la question posée suffit presque
toujours, et coûte cinq fois moins.

**Faire relire ce qui le mérite.** Le sous-agent de vérification avant commit
reste **obligatoire** pour : mixins, arithmétique, code réseau, persistance,
worldgen, et tout ce qui touche à la durabilité ou aux dégâts. Il est **inutile**
pour une texture, de la documentation, une traduction ou une clé de config
triviale — dans ces cas, vérifier soi-même (build, chargement en jeu, en-tête du
fichier) et **le dire explicitement** dans le message de rapport.

**Choisir le modèle selon l'enjeu.** Exploration, recherche de signature,
inventaire de fichiers : **Sonnet** — même travail, environ cinq fois moins lourd
sur le quota. Relecture avant commit dans les catégories obligatoires ci-dessus :
**Opus**, parce que repérer une absence (le tag `#swords`) ou une clé branchée en
apparence seulement (`light_wave.width`) est ce qui se dégrade en premier sur un
modèle plus léger. Un agent d'exploration rend **la ligne `javap` brute**, jamais
un résumé en prose : une signature inventée se voit alors au premier coup d'œil,
au lieu de me faire coder faux et reboucler — ce qui coûterait plus cher que
l'économie.

## Sessions

Repartir d'une **session neuve à chaque étape** du plan. Une conversation longue
renvoie tout son contexte à chaque tour, ce qui coûte cher pour rien : `SPEC.md`
est écrit précisément pour qu'une session neuve reprenne sans rien perdre.

## Tenue des fichiers

- `SPEC.md` se met à jour **au fil du projet** : marquer les étapes faites dans
  le plan (§8), fermer les questions tranchées (§9) en reportant la réponse dans
  la section concernée, noter les décisions structurantes dans le journal (§10).
  Une spec confirmée (✅) ne change pas sans l'accord de Jérôme.
- `CLAUDE.md` (ce fichier) reste court : uniquement des instructions
  permanentes. Tout ce qui décrit le mod va dans `SPEC.md`.
- Ce qu'on découvre sur Minecraft 26.2 ou sur la machine et qui vaudrait pour
  **n'importe quel mod** va dans `../SETUP-MC-MODDING.md`, pas ici.
