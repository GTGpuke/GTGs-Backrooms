# Onboarding — nouvel assistant sans contexte

Lis ces fichiers dans l'ordre avant de toucher au code.

## 1. Règles et vue d'ensemble
- `CLAUDE.md` (racine) — règles obligatoires, commandes, pièges.
- `README.md` — contenu du mod, structure, qualité.
- `docs/DECISIONS.md` — les décisions de l'auteur, datées, et leur raison.

## 2. Build
- `gradle.properties` — toutes les versions (MC 1.21.1, Loader 0.19.5, API 0.116.17, Loom 1.17.21, Java 21, JUnit 5.11.4).
- `build.gradle` — runs (`client`, `server`, `gametest`), toggles `-P`, tests + JaCoCo, benchmark.
- `src/main/resources/fabric.mod.json` — entrypoints `main`, `client`, `fabric-gametest`, mixins.

## 3. Code
- `core/ModConstants.java`, `core/ModConfig.java`.
- `GtgsBackroomsMod.java` → `registerContent()` ; `core/registry/*`.
- `backrooms/level0/generation/LevelZeroChunkGenerator.java` puis `layout/LevelZeroLayout.java`,
  `layout/LevelZeroCellEvaluator.java`, `write/LevelZeroBlockWriter.java`.

## 4. Architecture et tests
- `docs/systemes/ARCHITECTURE.md` — paquets, où ranger quoi, cycle de vie, conventions.
- `docs/systemes/LEVEL0.md` — la dimension et son pipeline de génération.
- `docs/dev/TESTS.md` — familles de tests, `MinecraftTestBootstrap`, empreintes, GameTest, benchmark.

## 5. Guide et specs Backrooms
- `docs/GUIDE.md` — vision, règles d'or, principes, glossaire, définition de « terminé », suite du travail : à lire
  au début de chaque session, il complète `CLAUDE.md`.
- Les specs de l'auteur dans `docs/backrooms/` et `docs/BACKROOMS_CONTENT_RULES.md`.
- `docs/systemes/PORTAILS.md` pour le système de portails tel qu'implémenté (lecture obligatoire avant de toucher
  `core/portal/`), puis `docs/systemes/RENDU.md` et `docs/systemes/AUDIO.md` pour les deux moteurs.

## Pièges à mémoriser

| Piège | Règle |
|-------|-------|
| API Yarn | `Identifier.of`, `Item.Settings`, `.maxCount`, `hasGlint`, `finishUsing`, `TypedActionResult`, `World`/`ServerWorld` |
| Worldgen | `dirt_provider` dans `minecraft:tree` ; `assets/<ns>/items/` ignoré en 1.21.1 |
| Windows | `-Dfile.encoding=COMPAT` dans `org.gradle.jvmargs` |
| Génération | déterministe et verrouillée par `LevelZeroGoldenTest` ; sels de `StageRandom` figés |
| Tests | `MinecraftTestBootstrap.ensure()` pour tout ce qui touche blocs/items/registres |
| Loom | ≥ 1.18 exige Java 25 pour Gradle : rester sur 1.17 tant que le projet est en Java 21 |

## Commandes

```bash
./gradlew test
./gradlew runGameTest
./gradlew checkstyleMain checkstyleTest checkstyleGametest
./gradlew benchmarkLevelZeroGeneration
./gradlew runClient
./gradlew clean build --warning-mode all
```
