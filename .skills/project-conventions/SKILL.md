---
name: project-conventions
description: "Rappelle les conventions du projet GTG's Backrooms (Fabric 1.21.1) : mod id, structure des packages, nommage, patterns Registry.register(), imports, séparation client/serveur, règles de génération et de qualité. Déclenche pour 'conventions', 'structure', 'règles', 'comment ajouter', 'architecture' ou 'comment organiser'."
---

# Conventions du projet GTG's Backrooms (Fabric 1.21.1)

Source de vérité : `CLAUDE.md` (racine) et `docs/systemes/ARCHITECTURE.md` (dont le tableau « Où ranger quoi »).

## Identifiants clés

| Constante | Valeur |
|-----------|--------|
| MOD_ID | `gtgs_backrooms` |
| Package racine | `com.gtgpuke.gtgsbackrooms` |
| Minecraft / Yarn | `1.21.1` / `1.21.1+build.3` |
| Fabric Loader / API | `0.19.5` / `0.116.17+1.21.1` |
| Fabric Loom / Gradle | `1.17.21` / `9.8.1` |
| Java | `21` (toolchain et JVM Gradle) |
| Tests | JUnit `5.11.4` + `fabric-loader-junit`, JaCoCo, GameTest |

## Structure des packages

```text
com.gtgpuke.gtgsbackrooms/
|- GtgsBackroomsMod.java        <- ModInitializer (registerContent + hooks)
|- GtgsBackroomsClientMod.java  <- ClientModInitializer
|- core/                        <- le socle du mod
|  |- ModConstants.java, ModConfig.java
|  |- registry/                 <- ModBlocks, ModItems, ModBlockEntities, ModEntities, ModDataComponents,
|  |                               ModSounds, ModChunkGenerators, ModCreativeTab, ModNetworking
|  |- perf/                     <- moniteur opt-in (+ client/)
|  `- portal/                   <- portails (block, item, entity, transition, sync, interaction, client...), voir docs/systemes/PORTAILS.md
|- backrooms/                   <- le contenu des Backrooms
|  |- BackroomsConstants.java
|  `- level0/                    <- dont LevelZeroArrival : ou un portail arrive dans le labyrinthe
|     |- biome/  block/  client/model/
|     `- generation/  (LevelZeroChunkGenerator ; coord/ noise/ sector/ layout/ write/)
`- mixin/                       <- communs + client/
```

Tests : `src/test/java/` et `src/gametest/java/.../gametest/` reprennent la même arborescence.

Où va un nouvel objet : sa classe dans le paquet de son domaine (`backrooms/<niveau>/{block,item,entity}/` pour le
contenu d'un niveau, `core/<système>/{block,item,entity}/` pour un système du socle, comme la baguette dans
`core/portal/item/`), son enregistrement dans `core/registry/`. Pas de dossier vide, pas de `.gitkeep`, pas de
`package-info.java` : un package n'existe que s'il contient du code utilisé.

## Nommage

| Type | Convention | Exemple |
|------|-----------|---------|
| Classe | PascalCase | `LevelZeroBlockWriter` |
| Méthode / variable | camelCase | `wallpaperFaceMask` |
| Constante | UPPER_SNAKE_CASE | `REGION_MARGIN_CELLS` |
| Identifiant registre | snake_case | `level_zero_wallpaper_adaptive` |
| Clé de langue | `block.<mod_id>.<id>` / `item.<mod_id>.<id>` / `itemGroup.<mod_id>.<id>` | `block.gtgs_backrooms.level_zero_wallpaper` |

## Enregistrement Fabric

```java
/** Baguette de portail : pose des blocs portail aux reglages qu'elle porte, choisis dans son ecran, sans s'user. */
public static final PortalWandItem PORTAL_WAND = register("portal_wand", new PortalWandItem(
        new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON).component(ModDataComponents.PORTAL_SETTINGS, PortalSettings.DEFAULT)));
```

Le champ contient l'objet (pas de `.get()`). Tout nouvel item va aussi dans `ModCreativeTab.ENTRIES` ;
`ModRegistriesTest` le vérifie. Le bloc portail n'a pas d'item : la baguette le pose.

## Imports

`java` → tiers (`com.mojang`, `com.llamalad7`, `org.*`) → `net.minecraft` → `net.fabricmc` → `com.gtgpuke`, aucun wildcard,
ordre alphabétique dans chaque groupe (Checkstyle).

## Règles de qualité

- Commentaires, logs (`ModConstants.LOGGER`) et messages d'erreur en français, majuscule, point final, ASCII.
- Texte visible en jeu via `Text.translatable`, traduit en EN **et** FR (vérifié par `ModRegistriesTest`).
- Code client uniquement sous `client/` ou `mixin/client/`.
- Aucune allocation ni concaténation de chaîne dans les méthodes tick/render.
- Pas d'abstraction spéculative : interface, stage ou builder seulement avec un second usage réel.
- Génération du Level 0 déterministe et verrouillée par `LevelZeroGoldenTest` ; sels de `StageRandom` figés ;
  toute évolution voulue régénère les empreintes **et** passe par `CHANGELOG.md`.
- Toute modification touchant `backrooms/level0/` relit d'abord `docs/systemes/LEVEL0.md` (pipeline de génération)
  et, pour une évolution de spec, les specs de l'auteur dans `docs/backrooms/`.
- Toute modification touchant `core/portal/` lit d'abord `docs/systemes/PORTAILS.md`.
- Textures des blocs du Level 0 en 32x32 ; loot tables dans `data/<modid>/loot_table/blocks/`.
- Les murs du Level 0 sont fixes ; les colonnes enfouies restent en `minecraft:bedrock`.

## Après chaque ajout

- `lang/en_us.json` et `lang/fr_fr.json`
- `CHANGELOG.md` et la doc du contenu (`docs/reference/CONTENU.md`, `docs/reference/OPTIONS.md`, `docs/systemes/` pour un niveau)
- un test de comportement dans `src/test/` ou `src/gametest/` (pas de test par réflexion)
- `./gradlew build --warning-mode all` vert : Checkstyle, tests unitaires, tests en jeu, couverture (100 % des instructions et des branches), contenu du JAR
