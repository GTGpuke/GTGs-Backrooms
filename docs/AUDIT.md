# Audit complet — GTG's Backrooms

**Date :** 2026-10-15 · **Version produite :** 0.8.0 · **Périmètre :** tout le dépôt (build, sources, tests, ressources, docs)
**Méthode :** lecture intégrale du code (≈ 11 500 lignes main, 3 300 lignes test), empreintes de génération
avant/après, benchmark avant/après sur la même machine, suite de tests réécrite.

> Audit daté : il décrit le dépôt du 2026-10-15 (0.8.0) et n'est pas tenu à jour. L'arborescence a changé depuis
> (`core/` et `backrooms/`, contenu de la Gang et de l'Arbre Maudit retiré, le 2026-10-21) : la référence actuelle est
> `docs/ARCHITECTURE.md`.

---

## 1. Résumé

| Axe | Avant | Après |
|-----|-------|-------|
| Outillage | Loom 1.9-SNAPSHOT, Loader 0.16.9, API 0.102, Gradle 9.3, tests `ignoreFailures=true` filtrés par grep en CI | Loom 1.17.21, Loader 0.19.5, API 0.116.17, Gradle 9.8.1, `fabric-loader-junit`, JaCoCo, GameTest, un échec de test casse le build |
| Structure | 122 `.gitkeep` de dossiers vides, `backrooms/level/level0`, `perf/section`, `creative/`, `network/`, `package-info` partout, restes Forge (`mods.toml`), assets 1.21.4 ignorés | arbre plat et réel, un package par responsabilité, aucun fichier mort |
| Génération Level 0 | 7 stages + 2 façades + builder + composite + décorateur de profiling, 4 surcharges de `sampleLight`, 11 champs par cellule dupliqués dans 10 tableaux par chunk | 5 classes lisibles + 1 writer, **sortie identique** (60 empreintes) |
| Bugs | frontières de chunk incohérentes, flag `noiseGeometry` ignoré, plinthe invisible, Y hérités (63) dans le mur et le modèle, version absente des logs, porte sans dimension lue comme `minecraft:` | corrigés |
| Performance (layout + écriture, ms/chunk, 5 layers) | 0,842 | **0,129** (6,5×) |
| Tests | 460 dont 332 paramétrés sur une seule classe ; une vingtaine de tests par réflexion (« le constructeur existe ») | 173 tests de comportement + 1 test en jeu + 15 empreintes ; couverture ≈ 90 % des packages de génération |
| Warnings javac | 0 déclarés mais `-Xlint:all` jamais vérifié en CI | 0, vérifié |

---

## 2. Constats et décisions

### 2.1 Outillage et build

| Constat | Décision |
|---------|----------|
| `fabric-loom 1.9-SNAPSHOT` : version mouvante, non reproductible. | Loom **1.17.21** (dernière lignée qui tourne sur une JVM 21 ; Loom ≥ 1.18 impose Java 25 pour Gradle). Gradle 9.8.1. |
| Loader 0.16.9 / API 0.102 : 14 versions de retard sur 1.21.1. | Loader 0.19.5, API 0.116.17+1.21.1 ; `fabricloader >= 0.16.9` conservé dans `fabric.mod.json`. |
| `test.ignoreFailures = true`, CI qui `grep` les XML pour détecter les échecs. | Supprimé : un test rouge casse `./gradlew test`. |
| Tests limités à la logique pure, par peur du bootstrap Minecraft (docs disaient « failures Bootstrap tolérées »). | `fabric-loader-junit` : les tests tournent sous le classloader Fabric, `MinecraftTestBootstrap.ensure()` exécute le vrai `onInitialize()`. Blocs, items, palette, writer et **générateur sur un vrai `ProtoChunk`** sont testés. |
| Aucune mesure de couverture. | JaCoCo (XML, CSV, HTML), code client et mixins exclus. |
| Aucun test en jeu. | Fabric GameTest : `./gradlew runGameTest` charge la dimension et vérifie la pile des 5 layers. Ajouté à la CI et à la release. |
| 13 blocs `if (project.hasProperty(...)) vmArg ...` dupliqués entre `runClient` et le benchmark. | Une map de propriétés, appliquée aux deux. |
| Dépendances `suggests` vers deux mods tiers, sans effet. | Retirées ; l'étude `docs/DEPENDENCIES.md` a été retirée à son tour le 2026-10-20 (`docs/GUIDE.md` fait référence). |
| `META-INF/mods.toml` (Forge), `atlases/blocks.json` (atlas jamais référencé), `assets/.../items/*.json` (format 1.21.4, ignoré en 1.21.1), dossiers `.github/java-upgrade` et `.github/modernize` d'un outil IDE, répertoires `com/ net/ org/` de `.class` à la racine. | Supprimés (les deux dossiers `.github/*` non versionnés et `tools/` n'ont pas pu être supprimés par l'agent : à faire à la main). |

### 2.2 Structure du code

| Constat | Décision |
|---------|----------|
| 122 `.gitkeep` matérialisant l'arborescence spéculative v4 (`system/sanity/client`, `shader/uniform/provider`…). | Supprimés : un package n'existe que s'il contient du code. |
| ~25 `package-info.java` d'une ligne. | Supprimés ; `docs/ARCHITECTURE.md` décrit les packages. |
| `backrooms/level/level0/`, `perf/section/`, `creative/`, `network/`, `feature/gang/item/gang_badge/`, `feature/gang/network/c2s/` (payload S2C rangé en c2s). | `backrooms/level0/`, `perf/`, `init/` regroupe onglet et payloads, features aplaties. |
| `GangBadgeActivatePacket` et l'ancienne classe de tests en jeu du contenu Gang : classes vides dépréciées « conservées pour éviter les conflits git ». | Supprimées. |
| Fichier des règles du projet mal nommé. | Renommé, réécrit. |

### 2.3 Génération du Level 0 : intention

Le cœur (secteur de 240×135 cellules : mini-labyrinthes de Prim superposés puis salles tamponnées ;
cellules 3×3 ; néons en quinconce ; biomes par région de 48 cellules ; gaps de couloir rares) est sain et
a été **conservé à l'identique**. Les problèmes étaient autour.

| Constat | Décision |
|---------|----------|
| Enchaînement `LevelZeroLayout → LevelZeroRegionGrid → LegacyRegionLayoutStage → RegionLayoutBuilder → LegacyLayoutPipeline → 7 stages`, chaque étape avec sa propre interface générique (`LevelZeroCellStage<T>`, `LevelZeroRegionStage<T>`), ses records de contexte, et des surcharges jamais appelées (`sampleLight` ×4, constructeurs « legacy »). | Remplacé par `LevelZeroLayout` (fenêtre + évaluation du chunk), `LevelZeroCellEvaluator`, `LevelZeroTopology`, `LevelZeroLargeRooms`, `LevelZeroLightPattern`, `LevelZeroCorridorGaps`. Même logique, un seul chemin. |
| `LevelZeroChunkSlice` recopiait 10 tableaux de 256 valeurs par chunk à partir de ≤ 49 cellules ; `LevelZeroLayout` dupliquait encore 5 de ces tableaux. | Le layout stocke les ≤ 49 évaluations de cellules ; `cellAt(localX, localZ)` indexe la cellule. |
| **Bug de frontière** : la fenêtre de traversabilité couvrait exactement les cellules du chunk ; au bord, les voisins hors fenêtre étaient vus comme des murs. Topologie (cul-de-sac au lieu de couloir), arbitrage des gaps et lumière des cellules de bord dépendaient du chunk depuis lequel on les évaluait. | Marge de **2 cellules** (nécessaire à l'arbitrage des gaps qui regarde les voisins des voisins). Testé : `LevelZeroLayoutTest.chunkBordersAreSeamless` compare l'évaluation complète des cellules partagées. Effet visible : des gaps peuvent apparaître sur les couloirs traversant une frontière ; néons, murs, salles et biomes inchangés (empreintes h1/h3/h4 identiques). |
| `hasWinningLightNeighbor` : arbitrage de proximité entre néons évalué pour chaque cellule éclairée (8 voisins × topologie + biome). Démonstration : la trame en quinconce (rangs 0/2, parité alternée) ne produit **jamais** deux candidats adjacents, donc la fonction retournait toujours `false`. | Supprimé. Test `LevelZeroLightPatternTest.noAdjacentLights` verrouille la propriété. |
| `LevelZeroLegacyLayoutPipeline(LIGHT_INTERVAL)` forçait `noiseGeometryEnabled = true` : le flag `-Dgtgsbackrooms.levelZero.noiseGeometry` documenté dans `build.gradle`, `SETUP.md` et testé « par défaut » n'avait aucun effet en jeu. | Le flag alimente `LevelZeroCellEvaluator` ; sa valeur par défaut (true) garde la génération identique. |
| `LevelZeroLightStage(lightInterval)` ignorait son paramètre ; `roomKind` passé à `sampleCandidate` jamais lu ; `LevelZeroCellTopology.JUNCTION` et `ALCOVE` jamais produits ; `LevelZeroCellTag` redondant avec la topologie ; `LevelZeroVerticalLayout` (Y 60-67 de l'ancienne dimension monocouche) et `LevelZeroVerticalSlice.legacySingleLayer()` encore utilisés par le mur et le modèle client. | Supprimés. Le mur et le modèle lisent `LevelZeroLayerStackLayout`. |
| Couche `surface details` : résolveur, profils, variantes connectées, flag `ENABLE_LEVEL_ZERO_SURFACE_DETAILS = false`, résolveur qui renvoyait un `none()` neuf par colonne. Jamais rendue en jeu. | Supprimée (≈ 350 lignes + 2 tests). |
| Couche `structures` rares : 5 classes de footprints/rôles/points de gameplay, uniquement rendue en béton de debug. La roadmap prévoit un autre système (prefabs manuels). | Supprimée (≈ 400 lignes + 2 tests). `LevelZeroSectorRoomKind` (la vraie donnée) est conservé et exposé par `LevelZeroCellEvaluation`. |
| Plinthe : bloc `level_zero_baseboard` + item + `BakedModel` + handler, alors que la plinthe est rendue en overlay sur le mur depuis la 0.7. Le bloc posé à la main ne définissait jamais son `face_mask` → **invisible et sans collision**. | Supprimé (bloc, item, 3 fichiers d'assets, 2 clés de langue). Les modèles `level_zero_baseboard_{north,…}` restent utilisés par l'overlay. Les blocs existants en monde disparaissent au chargement. |
| `LevelZeroWallpaperBlock.onBlockAdded` sondait l'air à `Y = 63` (constante héritée) et le biome du layer 0 quel que soit le Y. | Sonde à la hauteur du bloc, layer déduit du Y. Testé avec un `BlockView` factice. |
| Résolution par colonne : `LevelZeroResolvedColumn` (9 champs) + `LevelZeroColumnCoordinates` + `LevelZeroColumnMaterial` + `LevelZeroCellState` + profils → ≈ 8 allocations par colonne ; `wallProps`, `structure`, `surfaceDetails` résolus pour chaque colonne alors qu'ils n'alimentent que des stages de debug. | `LevelZeroBlockWriter.writeColumn` écrit directement depuis l'évaluation de cellule ; les détails muraux ne sont résolus que si le debug est actif. |
| `LevelZeroLayoutSampler` recréait pour **chaque** lecture de traversabilité un `LevelZeroRegionGrid` + sampler + 3 stages (5 objets) ; appelé 4 à 52 fois par colonne de mur. | `LevelZeroWallSampler` lit la fenêtre du layout (marge comprise) et ne retombe sur le cache de secteurs que hors marge. |
| Masque de papier peint : 4 sondes de 12 blocs par mur visible (biome de la première colonne d'air). Les régions de biome font 48 cellules : la sonde ne peut changer de région que si une frontière est à ≤ 4 cellules. | Court-circuit : si aucune frontière de région n'est à portée sur un axe, le résultat est le biome propre. Prouvé équivalent par `LevelZeroWallSamplerTest.shortcutMatchesFullProbe` (sonde complète recalculée) et par les empreintes h3. |
| Cache de secteurs : `ConcurrentHashMap` plafonné à 1024 **sans éviction** : une fois plein, chaque nouveau secteur (≈ 1 à 3 ms de Prim sur 32 400 cellules) était regénéré à chaque chunk ; clé = hash 64 bits (collision possible) ; secteur stocké en `enum[]` (≈ 160 Ko). | `LevelZeroSectorCache` : LRU borné à 256, clé `(seed, x, z)`, type de salle en `byte[]` (≈ 65 Ko). Une lecture de secteur par chunk (`SectorLookup` mémorise le dernier secteur) au lieu d'une par cellule. |
| `LevelZeroProfiledWriteStage` concaténait `baseSectionName + ".write_chunk_column"` **par colonne et par stage**, même monitoring désactivé (≈ 9 000 chaînes par chunk). | Supprimé ; les scopes de performance sont au niveau chunk/layout/secteur, noms constants. |
| `LevelZeroSurfaceBiome.sampleAtCell` appelait `values()` (clone de tableau) deux fois par cellule. | Tableau `VALUES` mis en cache. |
| `LevelZeroSectorGenerator` allouait `new int[8]` à chaque itération de la boucle de Prim. | Hoisté hors de la boucle. |
| Constructeur du générateur de secteur à 19 paramètres positionnels. | `LevelZeroSectorGenerator.Settings` (record) avec `Settings.LEVEL_ZERO`. |

**Limitation connue, non corrigée (volontairement) :** `StageRandom.mix` suivi de `new Random(seed)` (qui ne
garde que 48 bits) a une diffusion faible : deux seeds de layer ne différant que par leurs bits 0 à 15
donnent le **même** secteur. En jeu les seeds de layer sont des `long` aléatoires dérivés par Minecraft
(toutes les positions de bits varient), donc sans impact ; mais ne pas utiliser de petits entiers
consécutifs comme seeds de test. Corriger le mélange changerait tous les mondes existants : non fait.

### 2.4 Rendu client

| Constat | Décision |
|---------|----------|
| `LevelZeroWallpaperBakedModel` : 14 champs de modèles, 3 `switch` par direction dupliqués, repli sur la slice héritée. | `DirectionalModels` (record nord/sud/ouest/est) ×3, `LevelZeroWallpaperBlock.maskFor(direction)`, lecture de la pile canonique. Handler générique `bakeDirectional`. |
| `LevelZeroBaseboardBakedModel` + handler pour le bloc plinthe supprimé. | Supprimés. |

### 2.5 Portails (expérimental, ≈ 2 400 lignes, flag off)

Prototype fonctionnel mais fragile : échange du monde client courant pendant la frame
(`MinecraftClientAccessor`), déplacement temporaire de la position du joueur pour centrer le
`WorldRenderer` distant, stencil ajouté à **tous** les framebuffers par mixin. Conservé tel quel, derrière
`ModConfig.ENABLE_PORTAL_FEATURE`, avec les corrections suivantes :

| Constat | Décision |
|---------|----------|
| `PortalStencilRenderer.renderAllVisiblePortals` parcourait **toutes les entités du monde à chaque frame** même feature désactivée. | Retour immédiat si le flag est faux. |
| `MixinFramebuffer` attachait un stencil à chaque framebuffer même feature désactivée (coût GPU et risque de conflit avec un mod de rendu). | Retour immédiat si le flag est faux. |
| 3 loggers `LoggerFactory.getLogger(Classe)` au lieu du logger commun. | `ModConstants.LOGGER`. |
| `ClientWorldRegistry.init()/start()/stop()` vides. | Supprimés. |
| Texture d'entity `textures/entity/backrooms_portal.png` inexistante. | Texture vanilla (le renderer ne l'échantillonne jamais). |
| `BackroomsDoorBlockEntity.readNbt` : `Identifier.tryParse("")` renvoie `minecraft:` et non `null`, une porte sans cible se chargeait avec une dimension fantôme. | Test de présence de la clé avant parsing (trouvé par le nouveau test NBT). |
| Ordre des imports non conforme dans ~12 fichiers portail non modifiés. | Laissé tel quel (cosmétique) ; corrigé dans les fichiers touchés. |

Recommandation (octobre 2026) : choisir entre (a) des portails rendus par le mod lui-même (stencil et vue distante) ou (b) une bibliothèque de rendu externe.

**Décision 0.9.0** : option (a), par une réécriture complète et non une reprise du prototype : géométrie du
passage corrigée (rotation de 180°, plus de demi-tour face au portail de sortie), porte autonome avec portail
de retour, synchronisation serveur → client des chunks distants (le prototype ne marchait qu'en solo),
plus de déplacement du joueur pendant la frame (substitution de caméra par mixin), projection à plan proche
oblique (remplacée après 0.9.0 par une découpe dans les shaders, `gl_ClipDistance`), lightmap recalculée par dimension, coupe-circuit en
cas d'échec GL. Détail : `PORTALS.md`.

### 2.6 Features gameplay, config, perf

| Constat | Décision |
|---------|----------|
| `ModConfig.ENABLE_GANG_BADGE` jamais lu ; `ENABLE_LEVEL_ZERO_SURFACE_DETAILS` et `DEBUG_LEVEL_ZERO_STRUCTURES` sans objet. | Supprimés ; parsing extrait en `parseBoolean/parseInt` testables. |
| Log de démarrage `"Initialisation de {} v{}"` avec `MOD_ID` à la place de la version. | Version lue via `FabricLoader`. |
| `ModNetworking.send` : wrapper d'une ligne autour de `ServerPlayNetworking.send`. | Supprimé ; payload en singleton `INSTANCE`. |
| `ModCreativeTab` : liste d'items dans une méthode privée → impossible à vérifier. | `ENTRIES` publique ; `ModRegistriesTest` vérifie que **tous** les items du mod y sont, sans doublon. |
| `ModPerformanceMonitor` : correct mais 289 lignes ; `isEnabled()` figé rendait le chemin actif intestable. | Condensé (≈ 230 lignes), `enabled` modifiable à chaud (package-private) ; chemins actif et inactif testés. |
| Lang : clés `config.*` orphelines (pas d'écran de config). | Supprimées ; test d'égalité des jeux de clés EN/FR. |
| Loot table manquante pour le mur adaptatif. | Ajoutée (donne `level_zero_wallpaper`). |

---

## 3. Vérification de non-régression

**Empreintes de génération** : avant la refonte, un test temporaire a calculé 4 hashs par (seed, layer) sur
3 seeds × 5 layers × 49 chunks (≈ 184 000 colonnes) :

- h1 : traversabilité, néon, biome, grande pièce, type de salle par bloc ;
- h2 : topologie, connexions, gaps, motif 3×3, traversabilité locale, sur les cellules intérieures (≥ 2 cellules du bord) ;
- h3 : exposition et masques de papier peint par colonne ;
- h4 : détails muraux (plinthes, éléments) par colonne de mur visible.

Après la refonte, les **60 valeurs sont identiques**. Les seules différences possibles sont donc sur les
cellules de bord de chunk (bug de frontière corrigé), par construction exclues de h2.

Le test permanent `LevelZeroGoldenTest` (15 empreintes sur l'évaluation complète, bord compris) verrouille
l'état 0.8.0.

**Test en jeu** : `LevelZeroGameTests` charge la dimension depuis le datapack sur un vrai serveur et vérifie
sol/air/plafond/bedrock des 5 layers au chunk d'origine.

---

## 4. Performance

Protocole : 3 seeds, chunks de rayon 6 (169 par seed, 507 au total), 5 layers, 2 passes de chauffe,
moyenne de 5 passes mesurées, même machine, exécutions séquentielles sous le classloader Fabric.

| Mesure (ms par chunk, 5 layers) | Avant (0.7.0) | Après (0.8.0) | Gain |
|---------------------------------|---------------|---------------|------|
| Layout, secteurs froids (génération de secteur incluse) | 0,279 | 0,187 | 1,5× |
| Layout, secteurs chauds | 0,027 | 0,016 | 1,7× |
| **Layout + écriture des 256 colonnes** (≈ coût de `populateNoise`) | **0,842** | **0,129** | **6,5×** |

Le gain vient de l'écriture des murs : exposition et masque de faces lus dans la fenêtre du layout au lieu
d'allocations et de lectures de cache répétées, court-circuit des sondes de biome, suppression des
résolveurs de debug et des concaténations par colonne. Le secteur froid reste dominé par l'algorithme de
Prim (inchangé) ; le cache LRU garantit maintenant qu'il n'est payé qu'une fois par secteur.

À l'échelle du jeu : un chunk complet du Level 0 coûte ≈ 0,13 ms de CPU hors moteur, soit un budget
négligeable même avec une distance de rendu élevée ; la génération n'est plus un facteur de lag.

---

## 5. Tests

| | Avant | Après |
|-|-------|-------|
| Nombre | 460 (dont 332 cas paramétrés de `PortalFacingTest`) | 173 + 1 test en jeu |
| Tests par réflexion (`getDeclaredConstructor`, `getDeclaredField`) | ≈ 25 | 0 |
| Registres réels (blocs, items, codec, onglet, ressources) | non | oui |
| Générateur sur un `ProtoChunk` | non | oui (`populateNoise`, `getColumnSample` identique, déterminisme) |
| Frontières de chunk | traversabilité seulement | évaluation complète des cellules |
| Verrou de génération | non | 15 empreintes |
| Couverture lignes | non mesurée | config 100 %, secteur 99 %, biome 93 %, layout 92 %, write 92 %, coord 91 %, init 87 % ; portail 4-24 % (runtime serveur/client), perf ≈ 90 % |

Les packages faiblement couverts sont le runtime des portails (serveur et rendu, non testables sans
monde) et les points d'entrée Fabric.

---

## 6. Ce qui reste à faire (hors périmètre de la refonte)

1. **Portails** : décision prise et implémentée en 0.9.0 (voir §2.5 et `PORTALS.md`) ; reste la validation du
   rendu en jeu (`./gradlew runClient`), impossible hors d'un client graphique.
2. **Passe visuelle du Level 0** en jeu (roadmap) : les toggles `-PlevelZeroDebugMicroGeometry` et
   `-PlevelZeroDebugWallProps` servent à ça.
3. **Structures manuelles / prefabs** : repartir de `LevelZeroSectorRoomKind`, pas de la couche supprimée.
4. **Supprimer à la main** les restes non versionnés que l'agent n'a pas pu effacer : `.github/java-upgrade/` et
   `.github/modernize/` (outillage d'IDE auto-ignoré). Le script obsolète `tools/check_portal_contract.sh` a été retiré.
5. Optionnel : harmoniser l'ordre des imports des fichiers portail non touchés.

---

## 7. Fichiers supprimés (récapitulatif)

Sources : `backrooms/level/**` (remplacé par `backrooms/level0/**`), `creative/`, `network/`, `perf/section/`,
`feature/*/item/**`, `feature/gang/network/**`, `GangBadgeActivatePacket`, l'ancienne classe de tests en jeu du contenu Gang,
tous les `package-info.java`, tous les `.gitkeep`.
Ressources : `META-INF/mods.toml`, `atlases/blocks.json`, `items/*.json` (13), `blockstates/level_zero_baseboard.json`,
`models/block/level_zero_baseboard.json`, `models/item/level_zero_baseboard.json`.
Docs : `docs/audit/*` (ère Forge), un dossier de `docs/backrooms/` qui dupliquait à l'identique la spécification de `3 - Portails/`.
