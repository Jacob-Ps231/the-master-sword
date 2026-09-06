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
- Utiliser des **sous-agents** pour explorer le code source de Minecraft et de
  Fabric (API, exemples, conventions) avant d'écrire du code neuf.
- Utiliser un **sous-agent de vérification** dédié qui relit le code avant tout
  commit.
- Sur ce qui n'est pas confirmé dans `SPEC.md`, **proposer plutôt qu'imposer**.

## Tenue des fichiers

- `SPEC.md` se met à jour **au fil du projet** : marquer les étapes faites dans
  le plan (§8), fermer les questions tranchées (§9) en reportant la réponse dans
  la section concernée, noter les décisions structurantes dans le journal (§10).
  Une spec confirmée (✅) ne change pas sans l'accord de Jérôme.
- `CLAUDE.md` (ce fichier) reste court : uniquement des instructions
  permanentes. Tout ce qui décrit le mod va dans `SPEC.md`.
- Ce qu'on découvre sur Minecraft 26.2 ou sur la machine et qui vaudrait pour
  **n'importe quel mod** va dans `../SETUP-MC-MODDING.md`, pas ici.
