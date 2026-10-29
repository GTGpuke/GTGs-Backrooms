# Vérification finale avant commit

Prompt à donner tel quel à l'assistant avant un commit ou un push. Il reprend `BEFOREPUSH.md` et le complète par tout ce
que les passes précédentes ont appris. Chaque case se coche **après** avoir exécuté la commande et lu sa sortie, jamais
de mémoire.

**Périmètre** : les fichiers non commités (par défaut), ou « tout le projet » si je le précise ici : `__________`.

---

## 0. Règles absolues, pendant toute la vérification

- Ne jamais commiter ni pousser. À la fin : `git add -A`, c'est moi qui commite.
- Ne jamais accepter l'EULA Mojang (donc jamais `runServer`).
- Ne pas toucher à la génération du Level 0 sans demande explicite.
- Ne pas modifier mes specs : `docs/BACKROOMS_CONTENT_RULES.md`, `docs/backrooms/**`, `INDEX.md`. Une
  incohérence s'y signale, elle ne s'y corrige pas. `docs/GUIDE.md` se corrige, écart consigné dans `docs/DECISIONS.md` (règle d'or 11).
- Un autre mod ne se nomme que dans son module de compatibilité (`compat/<mod>/`), ses messages (clés `compat.*`) et la
  doc qui décrit ce module (décision du 29 octobre 2026) ; nulle part ailleurs dans le code, les tests ou la doc, et
  aucun nom ou structure de code calqué sur un autre mod. Les noms à chercher sont dans la mémoire de l'assistant
  (« Pas d'autre mod ») ; ils ne s'écrivent pas dans le dépôt, pas même ici.
- Client de développement :
  - ne rien lancer si un client de GTGpuke tourne déjà ;
  - les scripts ne visent et n'arrêtent que le PID qu'ils ont lancé, et aucun autre processus n'est tué ;
  - les touches sont postées à la fenêtre du banc (PostMessage), jamais au premier plan ;
  - `run/options.txt` est remis tel qu'il était, et les captures F2 sont déplacées hors de `run/screenshots`.
- Un problème trouvé se corrige avant de passer au point suivant. Après la dernière correction, le build complet est
  refait en entier.
- Le rapport est en français et honnête : ce qui est prouvé, ce qui ne l'est pas. Jamais « parfait » ni « plus aucun bug »
  sans preuve.
- Une décision qui me revient (comportement, contenu, suppression d'une fonctionnalité) se pose comme question ; elle ne
  se tranche pas en silence.

## 1. Préparer

- [ ] Lire `CLAUDE.md` (règles **et** section « Pièges »), puis `docs/dev/TESTS.md`, puis `docs/systemes/PORTAILS.md` si les portails
      sont touchés, puis `docs/systemes/LEVEL0.md`, qui sert de référence de performance.
- [ ] Lister le périmètre avec `git status --short`, `git diff --stat` et `git diff --cached --stat`.
- [ ] Pour chaque fichier du périmètre : le relire en entier, puis suivre ses appelants et ses appelés touchés par le
      changement.
- [ ] Sur « tout le projet », relire par zones : serveur des portails, client des portails, Level 0, contenu, tests, doc.
      Des relectures en parallèle sont permises, mais chaque remarque se vérifie dans le code avant d'être corrigée.

## 2. Revue du code

### 2.1 Conventions
- [ ] Commentaires, logs et messages d'erreur en français, avec majuscule et point final. Sources en ASCII, sans accents,
      et Javadoc sur chaque type public (Checkstyle le vérifie).
- [ ] Tout texte visible en jeu passe par `Text.translatable()`, avec sa clé dans `en_us.json` **et** `fr_fr.json`.
- [ ] Imports : `java`, puis tiers (`com.mojang`, `com.llamalad7`, `org.*`), puis `net.minecraft`, `net.fabricmc`,
      `com.gtgpuke`. Aucun import en `*`.
- [ ] Logs uniquement via `ModConstants.LOGGER` ; aucun `System.out` hors benchmark.
- [ ] Aucun TODO ni FIXME, aucun dossier vide, aucun `.gitkeep`, aucun `package-info.java` d'une ligne.
- [ ] Aucun import `net.minecraft.client` dans le code commun (hors `client/` et `mixin/client/`).

### 2.2 Conception et code mort
- [ ] Le code est simple et lisible ; aucune abstraction spéculative (interface, builder ou étape sans deuxième
      implémentation réelle) ; pas d'over-engineering.
- [ ] Pas de code mort :
  - [ ] aucun membre privé, paramètre ou constante inutilisé ;
  - [ ] aucune méthode d'accessor ou d'invoker de mixin jamais appelée ;
  - [ ] aucune branche défensive inatteignable (la couverture des branches la révèle) ;
  - [ ] aucune trace d'une fonctionnalité retirée (chercher ses noms).
- [ ] La Javadoc et les commentaires disent ce que fait le code, sans mentionner une fonctionnalité retirée ni une règle
      changée.

### 2.3 Chemins chauds (tick, rendu, par joueur, par portail, par chunk)
- [ ] Aucune allocation dans les méthodes de tick et de rendu. Pièges fréquents :
  - `ArrayList.addAll`, qui copie la liste en tableau ;
  - chaînes de `Vec3d` ;
  - lambdas qui capturent une variable ;
  - itérateurs et `entrySet` ;
  - boxing, varargs ;
  - `String.format` et concaténation, noms de sections perf compris.
- [ ] Une allocation n'est admise que sur un événement rare (réception d'un paquet, configuration). Elle est alors
      bornée, et son commentaire le dit.

### 2.4 État et cycle de vie
- [ ] Tout état statique par joueur ou par monde s'oublie :
  - côté serveur : à la déconnexion (`DISCONNECT`), à l'arrêt du serveur (`SERVER_STOPPED`, pour qu'une partie solo ne
    garde pas la précédente), au changement de monde vanilla (`AFTER_PLAYER_CHANGE_WORLD`) et au respawn
    (`AFTER_RESPAWN`) ;
  - côté client : en fin de session (`endPortalSession`).
- [ ] Aucune référence gardée vers un ancien monde, renderer ou joueur (fuite mémoire).
- [ ] Une ressource hors du tas Java (tampons de dessin) est libérée ou reprise.

### 2.5 Concurrence
- [ ] Moteur de lumière serveur : il tourne sur son propre fil, donc ses lectures doivent y être sûres (tables
      immuables, `volatile`).
- [ ] Récepteurs réseau : ceux de Fabric tournent déjà sur le fil principal ; ne jamais les envelopper dans
      `client.execute(...)`.
- [ ] Tout ce qui touche au rendu reste sur le fil de rendu.

### 2.6 Rendu client
- [ ] L'état GL est remis en place, y compris quand une exception survient (`finally`) : culling, depth mask, couleur
      de shader, stencil, pile model-view (push et pop équilibrés), découpe.
- [ ] Les échanges de `client.world`, `worldRenderer` et du contenu du gestionnaire de particules sont rendus dans un
      `finally`.
- [ ] Une décision de rendu se prend sur la position affichée (`lerp` de `tickDelta`), jamais sur celle du tick.
- [ ] Un renderer de vue tique lui-même (`WorldRenderer.tick`) : vanilla ne tique que le renderer du joueur.

### 2.7 Réseau et sécurité
- [ ] Le serveur valide tout ce que le client envoie :
  - l'entité ou le portail existe dans le monde du joueur, et il est à portée ;
  - le cooldown est respecté ;
  - les nombres sont finis et bornés (vitesse, angles) ;
  - la taille des listes est plafonnée dans le codec ;
  - le mode de jeu est autorisé (spectateur refusé).
- [ ] Un nouveau payload est enregistré dans `ModNetworking`, a un test d'aller-retour de son codec dans
      `PortalPayloadsTest` et une ligne dans le tableau de `docs/systemes/PORTAILS.md`.

### 2.8 Mixins
- [ ] `@Shadow` ne vise que des membres déclarés dans la classe cible (pas les membres hérités).
- [ ] La cible se charge (`MixinTargetsTest`).
- [ ] Le mixin reste inerte quand `ModConfig.ENABLE_PORTAL_FEATURE` est faux.
- [ ] Aucune classe de test dans le paquet `mixin`.
- [ ] Un champ protégé se lit par un accessor, pas par réflexion.

### 2.9 Compatibilité des données
- [ ] Un ajout au NBT ou à un codec prend une valeur par défaut quand le champ manque, et les anciennes sauvegardes se
      chargent.
- [ ] Les identifiants de registre et de payload restent stables.
- [ ] Un bloc ou un item retiré devient de l'air, avec les avertissements de vanilla ; c'est documenté.
- [ ] Une copie de bloc (clone, structure, choix du bloc avec ses données) ne duplique pas l'état propre à l'original
      (`removeFromCopiedStackNbt`, lecture d'une copie dans `readNbt`).

### 2.10 Pièges connus
- [ ] Reprendre la liste « Pièges » de `CLAUDE.md` et vérifier un par un ceux qui touchent le diff.

## 3. Tests

- [ ] Chaque comportement nouveau ou changé a un test unitaire ou un test en jeu, et son assertion peut échouer.
- [ ] Tester les tests par mutation, pour chaque règle nouvelle :
  1. sauvegarder le fichier ;
  2. y injecter une seule erreur ;
  3. lancer les tests concernés ;
  4. constater l'échec ;
  5. restaurer le fichier, puis vérifier avec `cmp` qu'il est identique à la sauvegarde.

  Une mutation qui survit appelle un test. Le rapport donne le nombre de mutations détectées sur le total.
- [ ] Tests en jeu :
  - attendre une condition (`createAndAdd`) au lieu de compter les ticks ; une vérification définitive se fait une seule
    fois (`createAndAddReported`) ;
  - nettoyer dans un `try/finally`, et ajouter un `@AfterBatch` pour tout lot qui peut expirer ;
  - générer l'arrivée d'avance (`GameTestServerWorlds.pregenerateArrival`) ;
  - ne dépendre ni du nombre de chunks que vanilla propose, ni de l'ordre des lots ;
  - déconnecter les joueurs (`respawnPlayer` appelé directement exige `networkHandler.player`) ;
  - remettre l'état du monde (météo, gamerules, chunks forcés) ;
  - un joueur simulé ne bouge pas tout seul : le faire bouger quand le serveur juge sur un déplacement.
- [ ] Les messages d'assertion sont en français et finissent par un point.
- [ ] Une nouvelle classe utilitaire entre dans `UtilityClassesTest`, et toute nouvelle classe non client a un test de
      comportement.
- [ ] Un test sensible au timing tourne deux fois de suite, vert les deux fois.
- [ ] Le message d'échec d'un test en jeu se lit dans `build/junit.xml`, pas dans le journal.

## 4. Build et outils

```bash
./gradlew clean build --warning-mode all
```
- [ ] `BUILD SUCCESSFUL`, aucun `warning:` de javac, aucun warning de dépréciation Gradle.
- [ ] Tests unitaires : nombre total et 0 échec (`build/reports/tests/test/index.html`). Tests en jeu : « All N required
      tests passed ».
- [ ] Couverture fusionnée (unitaires et en jeu) à 100 % des instructions **et** des branches. Une ligne manquée se lit
      dans `build/reports/jacoco/full/html/index.html` et se couvre par un test, jamais par une exclusion.
- [ ] Checkstyle main, test et gametest à zéro, et `verifyJarContents` vert (le JAR ne contient que le mod).

```bash
./gradlew benchmarkLevelZeroGeneration
```
- [ ] Les trois chiffres restent dans l'ordre de grandeur de `docs/systemes/LEVEL0.md` (layout et écriture : 0,129 ms par chunk) ;
      un écart s'explique.

```bash
git diff --check; git diff --cached --check
grep -rn "TODO\|FIXME" src/main/java --include=*.java
grep -rn "System.out" src/main/java --include=*.java
grep -rn "import net.minecraft.client" src/main/java --include=*.java | grep -v "/client/\|/mixin/client/"
grep -rniE "<noms de la mémoire « Pas d'autre mod »>" src docs CLAUDE.md README.md
find src -type d -empty; find src -name ".gitkeep"
```
- [ ] Toutes ces commandes ne renvoient rien. Exceptions : les specs de GTGpuke (`BACKROOMS_CONTENT_RULES.md`,
      `INDEX.md`, `docs/backrooms/**`), et les modules de compatibilité (`compat/<mod>/`, leurs clés `compat.*`, leur
      dépendance de compilation et la doc qui les décrit).

## 5. Vérification en jeu (dès qu'un mixin, du code client ou le rendu est touché)

- [ ] Vérifier qu'aucun client de GTGpuke ne tourne.
- [ ] Le client démarre jusqu'à l'écran titre, sans crash ni erreur de mixin. Le build vert ne suffit pas : les tests en
      jeu ne chargent jamais les classes client.
- [ ] Une scène de banc (monde « Portal Lab ») par changement client :
  - [ ] Si le chargement rapide s'arrête sur l'écran « Worlds using Experimental Settings », le confirmer par TAB, TAB,
        Entrée postés à la fenêtre du banc.
  - [ ] Captures par `PrintWindow`, ou par F2 postée si elles sortent blanches (écran verrouillé ou en veille).
  - [ ] Lire le journal de debug du serveur pour suivre ses décisions (traversées, disparitions).
  - [ ] Marches par `tp ^ ^ ^0.15` à tangage 0, sans entrer dans un mur.
  - [ ] Selon ce qui est touché, vérifier aussi : en troisième personne (F5), juste après une traversée, et après un
        changement de dimension vanilla.
- [ ] Remettre `run/options.txt`, n'arrêter que son propre PID, et déplacer les captures F2 hors de `run/screenshots`.

## 6. Level 0

- [ ] `LevelZeroGoldenTest` est vert, empreintes inchangées. Un changement voulu exige les trois : l'annonce dans le
      CHANGELOG, les empreintes régénérées et mon accord.
- [ ] Les sels de `StageRandom.Stage` et l'algorithme de `LevelZeroSectorGenerator` ne sont pas touchés.

## 7. Ressources et traductions

- [ ] `ModRegistriesTest` est vert : blockstate, modèle de bloc, modèle d'item, loot table et traductions EN et FR pour
      chaque bloc et chaque item.
- [ ] Les clés EN et FR sont les mêmes. Un item de bloc lit la clé `block.*` : pas de clé `item.*` pour lui.
- [ ] Aucun fichier orphelin : texture, modèle, blockstate, loot table ou clé de langue que rien n'utilise.
- [ ] Les JSON de worldgen sont valides (`dirt_provider` dans un `minecraft:tree`).

## 8. Documentation

- [ ] `CHANGELOG.md`, section `[Unreleased]` : une entrée par changement visible, correction ou retrait (Added,
      Changed, Removed, Fixed).
- [ ] `docs/systemes/PORTAILS.md` : comportement, tableaux des classes, tableau des payloads, limites connues et listes de tests.
- [ ] `docs/dev/TESTS.md` : listes des tests et exclusions de couverture, à jour des classes ajoutées.
- [ ] `docs/reference/CONTENU.md`, `docs/reference/OPTIONS.md`, `docs/systemes/` et `README.md` si la structure, le
      contenu ou les commandes changent.
- [ ] `CLAUDE.md` : structure, commandes, et un piège par leçon nouvelle apprise pendant la passe.
- [ ] Les nombres et constantes cités dans la doc (payloads, réglages, intervalles, distances) sont ceux du
      code.
- [ ] Une fonctionnalité retirée n'est plus citée, sauf dans l'historique.

## 9. Nettoyage et git

- [ ] `git status --short` ne montre aucun fichier temporaire (`.bak`, `.tmp`, `golden_*.txt`, scripts ponctuels) ni dossier
      vide. Le dossier `run/` est tel qu'il était (options, captures).
- [ ] Faire `git add -A`, puis vérifier avec `git status --short`. **Pas de commit.**

## 10. Rapport final

```text
╔══════════════════════════════════════════════════════╗
║  VÉRIFICATION FINALE — GTG's Backrooms               ║
╠══════════════════════════════════════════════════════╣
║  Build          : ✅/❌  (warnings : 0)              ║
║  Tests unitaires: ✅/❌  (N tests, 0 échec)          ║
║  Tests en jeu   : ✅/❌  (N tests)                   ║
║  Couverture     : ✅/❌  (instructions / branches)   ║
║  Mutations      : X détectées sur Y                  ║
║  Revue du code  : ✅/❌                              ║
║  Client en jeu  : ✅/❌/non requis                   ║
║  Level 0        : ✅/❌  (empreintes inchangées)     ║
║  Ressources     : ✅/❌                              ║
║  Performance    : ✅/❌  (ms par chunk)              ║
║  Documentation  : ✅/❌                              ║
║  VERDICT        : PRÊT À COMMITER / BLOQUÉ           ║
╚══════════════════════════════════════════════════════╝
```

Puis, en quelques lignes chacun :
1. **Corrigé pendant la vérification** : quoi, pourquoi, et le test qui le prouve.
2. **Preuves** : les commandes lancées et les lignes clés de leur sortie.
3. **Vérifié en jeu** : les scènes et ce qu'elles ont montré.
4. **Non vérifié ou limites** : ce qui n'a pas pu être prouvé, sans l'enjoliver.
5. **À décider par GTGpuke** : les points qui lui reviennent (specs, comportements, suppressions).
