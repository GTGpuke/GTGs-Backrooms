# Audit pré-push

À exécuter avant tout push ou avant de déclarer une feature terminée. Exécute réellement chaque commande,
montre la sortie, corrige avant de passer au point suivant. Ne dis jamais « c'est prêt » sans ces preuves.

## 1. Build propre

```bash
./gradlew clean build --warning-mode all
```
- [ ] `BUILD SUCCESSFUL`, aucun `warning:` javac, aucun warning Gradle de dépréciation. Le build enchaîne `check` :
      Checkstyle, tests unitaires, tests en jeu, couverture, contenu du JAR ; les points suivants disent quoi lire s'il échoue.

## 2. Tests

```bash
./gradlew test
```
- [ ] Tous verts. Rapport : `build/reports/tests/test/index.html`, couverture : `build/reports/jacoco/test/html/index.html`.
- [ ] Si `LevelZeroGoldenTest` échoue : soit régression à corriger, soit évolution voulue → empreintes régénérées **et** CHANGELOG.
- [ ] Toute nouvelle classe non client a au moins un test de comportement ; une classe utilitaire s'ajoute à `UtilityClassesTest`.

```bash
./gradlew runGameTest
```
- [ ] Vert, toujours : les tests en jeu couvrent le serveur des portails et le contenu.

```bash
./gradlew jacocoCoverageVerification
```
- [ ] Vert : 100 % des instructions et des branches du code mesuré (unitaire + en jeu). Rapport fusionné :
      `build/reports/jacoco/full/html/index.html`.
      Une instruction non couverte se lit dans ce rapport ; on écrit le test, on n'exclut pas la classe.

## 3. Code

```bash
./gradlew checkstyleMain checkstyleTest checkstyleGametest
grep -rn "import net.minecraft.client" src/main/java --include=*.java | grep -v "/client/\|/mixin/client/"
grep -rn "TODO\|FIXME" src/main/java --include=*.java
```
- [ ] Checkstyle vert (ASCII, imports ordonnés sans wildcard, logger unique, Javadoc des types publics, mise en forme) ;
      les deux greps ne renvoient rien.
- [ ] Commentaires et logs en français, majuscule, point final, ASCII.
- [ ] Aucune allocation ni concaténation de chaîne dans les méthodes tick/render.
- [ ] Aucune abstraction spéculative (interface ou builder à une seule implémentation).

## 4. Ressources

- [ ] `ModRegistriesTest` vert : blockstate, modèle de bloc, modèle d'item, loot table et traductions EN/FR pour tout bloc et item.
- [ ] JSON de worldgen valides (`dirt_provider` dans `minecraft:tree`).
- [ ] Aucun fichier orphelin (texture ou modèle sans référence).
- [ ] `./gradlew verifyJarContents` vert : le JAR ne contient que le mod (ni dépendance embarquée, ni test en jeu).

## 5. Performance

```bash
./gradlew benchmarkLevelZeroGeneration
```
- [ ] Les trois chiffres restent dans l'ordre de grandeur de `docs/systemes/LEVEL0.md` (performance) ; expliquer tout écart.

## 6. Documentation

- [ ] `CHANGELOG.md` à jour (section `[Unreleased]` ou version).
- [ ] Doc concernée à jour : `docs/systemes/` (portails, rendu, audio, Level 0, architecture), `docs/reference/CONTENU.md`,
      `docs/reference/OPTIONS.md`, `docs/dev/TESTS.md`.
- [ ] `README.md` si la structure ou les commandes ont changé.

## 7. Nettoyage

```bash
git status --short
```
- [ ] Aucun fichier temporaire (`golden_*.txt`, scripts one-shot, `.bak`, `.tmp`), aucun dossier vide.

## 8. Rapport

```text
╔══════════════════════════════════════════════╗
║  RAPPORT PRÉ-PUSH — GTG's Backrooms          ║
╠══════════════════════════════════════════════╣
║  Build        : ✅/❌  (warnings : 0)        ║
║  Tests        : ✅/❌  (X tests, 0 échec)    ║
║  GameTest     : ✅/❌/non requis             ║
║  Code         : ✅/❌                        ║
║  Ressources   : ✅/❌                        ║
║  Performance  : ✅/❌  (ms/chunk)            ║
║  Documentation: ✅/❌                        ║
║  VERDICT      : PRÊT À PUSH / BLOQUÉ         ║
╚══════════════════════════════════════════════╝
```
