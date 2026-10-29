# Architecture — GTG's Backrooms

## Stack technique

| Couche | Technologie |
|--------|-------------|
| Jeu | Minecraft 1.21.1 (Yarn mappings 1.21.1+build.3) |
| Mod loader | Fabric Loader 0.19.5 |
| API | Fabric API 0.116.17+1.21.1 |
| Langage | Java 21 |
| Build | Gradle 9.8.1 (wrapper) + Fabric Loom 1.17.21 |
| Tests | JUnit 5.11 + fabric-loader-junit, JaCoCo, Fabric GameTest |
| CI/CD | GitHub Actions |

Aucune dépendance runtime externe en dehors de Fabric API. Le cache de secteurs utilise
`java.util.LinkedHashMap` plutôt que Guava pour rester sans dépendance.

---

## Arborescence des packages

```text
com.gtgpuke.gtgsbackrooms/
|
|-- GtgsBackroomsMod.java        <- ModInitializer : registerContent() + hooks serveur + flag portail
|-- GtgsBackroomsClientMod.java  <- ClientModInitializer : modèle adaptatif, écran de la baguette, portails (chunks distants, rendu)
|
|-- core/                               <- le socle du mod, sur lequel repose le contenu
|   |-- ModConstants.java               <- MOD_ID, MOD_NAME, LOGGER
|   |-- ModConfig.java                  <- options lues au démarrage via -D (debug, portails)
|   |-- registry/                       <- un registre par type, champs static final
|   |   |-- ModBlocks.java
|   |   |-- ModItems.java               <- la baguette, puis l'item de chaque bloc du Level 0 (le bloc portail n'en a pas)
|   |   |-- ModBlockEntities.java
|   |   |-- ModEntities.java            <- type de l'entity portail, toujours enregistré
|   |   |-- ModDataComponents.java      <- composant des réglages de portail
|   |   |-- ModSounds.java              <- sons, déclarés dans assets/gtgs_backrooms/sounds.json
|   |   |-- ModChunkGenerators.java
|   |   |-- ModCreativeTab.java         <- ENTRIES : contenu ordonné de l'onglet du mod
|   |   `-- ModNetworking.java          <- types de payloads
|   |-- perf/ModPerformanceMonitor (+ client/ClientPerformanceMonitorHook, client/FrameStateCommand : /backrooms_state, etat de l'image au journal)
|   |-- portal/                         <- portails : block, item, entity, transition, sync, interaction, propagation, light, client, debug (voir §Portails)
|   |-- audio/                          <- moteur audio : profile/ (acoustique par dimension), acoustic/ (dimension d'émission, occlusion, copie du monde et rayons, forme générée, direction perçue, réglages de réverb, cadres traversés), cue/ (liaison son-image : horloge commune, liens, enveloppes, courbes, tremblement, perturbations, plafond de clignotement), client/ (colle OpenAL EFX, modes du moteur, routage des sons, copie du monde, thread de calcul, bourdonnement des néons, sons liés, rayons de débogage) (voir docs/AUDIO.md)
|   `-- render/                         <- moteur de shaders : patch/ (enveloppe de main des shaders vanilla), profile/ (look par dimension), state/ (pile des looks), light/ (lightmap, brouillard, néons : calendrier, jumeau GLSL vérifié sur les mêmes vecteurs, sélection, carte), post/ (bits du stencil), fbo/ (framebuffers déclarés), quality/ (légère ou complète), client/ (colle GL : ressources, index des néons, face des néons dans l'atlas, bloc d'uniformes, post et bloom, compilation à blanc, vérification du jumeau) (voir docs/RENDERING.md)
|
|-- backrooms/                          <- le contenu des Backrooms
|   |-- BackroomsConstants.java         <- clé de dimension, identifiants, échelle de cellule
|   `-- level0/
|       |-- LevelZeroArrival                     <- où un portail arrive dans le labyrinthe (demandé par core/portal)
|       |-- LevelZeroRoomShape                   <- forme d'une salle lue dans le layout (acoustique générée, pour core/audio)
|       |-- LevelZeroLayoutSync, LevelZeroLayoutPayload <- graine du layout envoyée au client à la connexion
|       |-- client/LevelZeroEffects, client/LevelZeroAcoustics <- effets de dimension du look ; forme des salles donnée au moteur audio
|       |-- biome/LevelZeroSurfaceBiome          <- palettes cosmétiques par région et par layer
|       |-- block/LevelZeroWallpaperBlock        <- mur adaptatif (face_mask 0-15)
|       |-- client/model/                        <- LevelZeroWallpaperBakedModel + handler
|       `-- generation/
|           |-- LevelZeroChunkGenerator          <- orchestre les 5 layers
|           |-- coord/   LevelZeroCoords, LevelZeroLayerStackLayout, LevelZeroVerticalSlice
|           |-- noise/   StageRandom, LevelZeroSeedResolver
|           |-- sector/  LevelZeroSectorGenerator, LevelZeroSectorData, LevelZeroSectorCache, LevelZeroSectorRoomKind
|           |-- layout/  LevelZeroLayout, LevelZeroRegionWalkability, LevelZeroCellEvaluator,
|           |            LevelZeroTopology, LevelZeroLargeRooms, LevelZeroLightPattern, LevelZeroCorridorGaps,
|           |            LevelZeroCellEvaluation, LevelZeroCellContext, enums et masques
|           `-- write/   LevelZeroBlockPalette, LevelZeroBlockWriter, LevelZeroWallSampler,
|                        LevelZeroWallProps, LevelZeroDebugPalette
|
|-- compat/iris/                        <- module de compatibilite avec le mod de shaders Iris : dans les Backrooms, le rendu du mod remplace le pack (mixins filtres par CompatMixinPlugin)
`-- mixin/                              <- communs (relais de blocs, chunks tenus, sons et particules, collision, mobs, lumière, météo) + client/ (rendu des portails)
```

Où ranger quoi :

| Ajout | Classe de l'objet | Enregistrement |
|-------|-------------------|----------------|
| Bloc, item ou entity d'un niveau des Backrooms | `backrooms/<niveau>/{block,item,entity}/` | `core/registry/Mod{Blocks,Items,Entities}` |
| Objet d'un système du socle (portails…) | `core/<système>/{block,item,entity}/` (la baguette : `core/portal/item/`) | idem |
| Son | — (un `SoundEvent` n'a pas de classe à lui) | `core/registry/ModSounds`, déclaré dans `assets/gtgs_backrooms/sounds.json` avec son sous-titre |
| Payload réseau | dans le paquet de son système (`core/portal/sync/`) ou de son contenu (`backrooms/level0/LevelZeroLayoutPayload`) | `core/registry/ModNetworking` |
| Système transverse (rendu, audio, `docs/GUIDE.md`) | `core/<système>/` | — |
| Look d'une dimension | — (donnée : `assets/gtgs_backrooms/look_profiles/<dimension>.json`) | chargé avec les ressources (`core/render/client/LookResources`) ; ses effets de dimension enregistrés par le contenu (`backrooms/<niveau>/client/`) sous l'identifiant `effects` de son type de dimension |
| Patch de shaders vanilla | — (donnée : `assets/gtgs_backrooms/shader_patches/<nom>.json`, bibliothèques dans `assets/gtgs_backrooms/shaders/include/`) | idem |
| Acoustique d'une dimension | — (donnée : `assets/gtgs_backrooms/audio_profiles/<dimension>.json`) | chargée avec les ressources (`core/audio/client/AudioResources`) ; son bourdonnement est un son du mod (`sounds.json`, `ModSounds`) ; une forme générée (`"acoustics": "generated"`) est donnée par le contenu (`GeneratedAcoustics.register`, depuis `backrooms/<niveau>/client/`) |
| Lien son-image | — (donnée : `assets/gtgs_backrooms/sound_cues/<nom>.json`) | chargé après les sons (`core/audio/client/CueResources`) |

Aucun paquet n'existe sans code : un paquet `item/` de niveau naît avec son premier item.

Dépendances : le contenu (`backrooms/`) s'appuie sur le socle (`core/`) ; le socle ne connaît du contenu que ce qu'il
enregistre (`core/registry/`) et l'endroit où ses portails mènent : `PortalTargets` demande au Level 0 où arriver
(`LevelZeroArrival`) et lit sa clé de dimension (`BackroomsConstants`). Un niveau de plus s'ajoute là, sans autre lien.

Les systèmes de rendu (`core/render/`, `docs/RENDERING.md`) et d'audio (`core/audio/`, `docs/AUDIO.md`) s'insèrent
selon `docs/GUIDE.md`, section 3.4 : ils consomment l'API de `portal/` (vues, transformations par le passage, mondes
distants, bascule de monde), qui leur expose des points d'accès sans réécriture de sa logique (le look d'une vue est
empilé par `PortalStencilRenderer` et `ShaderPackPortalRenderer`, les bits de look du stencil écrits avec la zone de
chaque vue ; `ClientPortalCrossing.WORLD_SWITCHED` à chaque bascule de monde ; `PortalSound` et `SoundAnchors` pour le
son rejoué au cadre). Le contenu les configure par données (`look_profiles/`, `audio_profiles/`, `sound_cues/`) et
enregistre ce qui le concerne (`backrooms/level0/client/LevelZeroEffects`, `LevelZeroAcoustics`). Aucun de ces paquets n'existe tant qu'il
n'a pas de code : pas de dossier vide.

Les tests en jeu vivent dans un source set séparé, `src/gametest/java/com/gtgpuke/gtgsbackrooms/gametest/`, rangé par
domaine comme le code (`support/`, `backrooms/level0/`, `core/portal/`) : un mod
`gtgs_backrooms_gametest` chargé par `runGameTest` seulement, jamais dans le JAR (`verifyJarContents` le vérifie).

---

## Pipeline de génération du Level 0

```text
NoiseConfig ──LevelZeroSeedResolver──> seed de layout ──> seed par layer (0..4)
                                                                │
   LevelZeroSectorCache (LRU 256) ◄── LevelZeroSectorGenerator  │  secteur 240x135 cellules :
          │   traversabilité + type de salle par cellule        │  mini-labyrinthes (Prim) + salles
          ▼                                                     ▼
   LevelZeroRegionWalkability : fenêtre du chunk + marge de 2 cellules
          │
          ▼
   LevelZeroCellEvaluator (par cellule 3x3 du chunk)
      grande pièce ─> biome ─> connexions/topologie ─> néon ─> gap de couloir ─> motif 3x3
          │
          ▼
   LevelZeroLayout (cellules évaluées du chunk) ──> LevelZeroBlockWriter
                                                      bedrock inter-layer, sol, air|mur, plafond|néon
                                                      murs visibles : LevelZeroWallSampler (exposition, faces blanches)
```

Points clés :

- **Déterminisme** : tout dérive de `StageRandom.mix(seed, stage, x, z)`. Les sels sont figés.
  `LevelZeroGoldenTest` verrouille les empreintes de génération pour trois seeds et cinq layers.
- **Marge de région** : la fenêtre de traversabilité déborde de deux cellules autour du chunk, ce qui
  rend topologie, néons et gaps identiques des deux côtés d'une frontière de chunk.
- **Biomes sans seed** : `LevelZeroSurfaceBiome` ne dépend pas du seed monde, le client recalcule
  la palette et les détails muraux sans synchronisation.
- **Pile verticale** : cinq layers de dix blocs (sol 0, air 1-4, plafond 5, bedrock 6-9), dimension de 64 blocs.
- **Un seul chemin d'écriture** : `LevelZeroBlockWriter.writeColumn` alimente aussi bien le chunk que
  l'échantillon de colonne demandé par le moteur, via l'interface fonctionnelle `BlockSink`.

---

## Rendu client du Level 0

Le mur visible est toujours `level_zero_wallpaper_adaptive`. Sa propriété `face_mask` (4 bits) dit quelles
faces montrent la variante blanche ; elle est calculée par le générateur et relue par
`LevelZeroWallpaperBakedModel`, qui ajoute en overlay les plinthes (ligne basse de chaque layer) et les
rares interrupteurs ou prises (`LevelZeroWallProps`, déterministes). Aucune block entity, aucun bloc
supplémentaire dans le monde.

---

## Portails

`core/portal/` relie le monde normal et le Level 0 par un bloc portail : entity portail persistante,
block entity qui garde ses réglages (taille, lumière, plan au milieu ou au bord du bloc, arrivée droite ou tête en bas), trouve seule sa destination
et pose deux faces dos à dos de chaque côté (un portail double face), baguette qui pose des blocs portail aux réglages
choisis dans son écran (`item/PortalWandItem`, `client/wand/`), seule façon d'en créer un, téléportation par traversée du
plan (joueurs prédits par le client, objets et mobs transposés par le serveur), collision à travers le cadre et blocs
cachés derrière son plan à qui s'y engage (`transition/PortalCollision`), garde d'une arrivée au sol
(`transition/ArrivalGuard`), synchronisation serveur → client des chunks et des entités de l'autre dimension
(payloads `portal_remote_*`, suivi vanilla des entités), rendu client du monde distant à travers le cadre (stencil,
caméra transposée, plan de découpe dans les shaders, un niveau de portails imbriqués), interaction à travers le
cadre (`interaction/`), et ce qui traverse un portail ouvert sans être une entité : sons et particules relayés
(`MixinPlayerManager`, `MixinServerWorld`, rejoués côté client par `client/sound` et `RemoteParticles`), regard et
poursuite des mobs (`propagation/`), lumière des blocs (`light/`, sources virtuelles lues par le moteur de lumière).
Actif par défaut, `ModConfig.ENABLE_PORTAL_FEATURE` coupe tout (bloc inerte, mixins inertes). Conception, protocole et limites : [PORTALS.md](PORTALS.md).

---

## Cycle de vie

```text
1. Fabric charge le mod ; les mixins communs et client s'appliquent (sans effet si portails désactivés).
2. onInitialize() : registerContent() (générateurs de chunks -> composants -> blocs -> block entities -> entity -> items -> onglet -> payloads),
   hooks de tick serveur (perf), vidage du cache de secteurs à l'arrêt, runtime portail si activé.
3. onInitializeClient() : hook FPS, modèle adaptatif, écran de la baguette, renderer et récepteurs des portails.
4. Chargement du monde : le datapack déclare la dimension, qui référence le codec du générateur.
```

---

## Séparation client / serveur

| Règle | Détail |
|-------|--------|
| Code commun | `core/` (hors `client/`) : `ModConstants`, `ModConfig`, `registry/`, `perf/ModPerformanceMonitor`, `portal/{block,item,entity,transition,sync,interaction,propagation,light,debug}` ; `backrooms/level0/{biome,block,generation}`, `mixin/*` (hors `client/`) |
| Code client | sous-packages `client/` et `mixin/client/` uniquement ; jamais de `MinecraftClient` ailleurs |
| Point d'entrée | `ClientModInitializer` séparé, déclaré dans `fabric.mod.json` |

---

## Conventions

| Type | Convention | Exemple |
|------|------------|---------|
| Classe | PascalCase | `LevelZeroBlockWriter` |
| Méthode / variable | camelCase | `wallpaperFaceMask` |
| Constante | UPPER_SNAKE_CASE | `REGION_MARGIN_CELLS` |
| Identifiant registre | snake_case | `level_zero_wallpaper_adaptive` |
| Clé de langue | `block.<mod_id>.<id>` (reprise par l'item du bloc), `item.<mod_id>.<id>` pour un item sans bloc | `block.gtgs_backrooms.level_zero_wallpaper` |
| Commentaires et logs | français, majuscule, point final, ASCII | `// Le cache evince les plus anciens.` |
| Imports | `java` → tiers (`com.mojang`, `com.llamalad7`, `org.*`) → `net.minecraft` → `net.fabricmc` → `com.gtgpuke` | — |

Pas de couche d'indirection sans second usage réel : une classe = une responsabilité lisible,
pas de stage/builder/façade spéculatifs.
