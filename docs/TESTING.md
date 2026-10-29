# Tests — GTG's Backrooms

## Vue d'ensemble

| Niveau | Outil | Commande | Rapport |
|--------|-------|----------|---------|
| Unitaire | JUnit 5 sous le classloader Fabric (`fabric-loader-junit`) | `./gradlew test` | `build/reports/tests/test/index.html` |
| Couverture unitaire | JaCoCo | généré par `test` | `build/reports/jacoco/test/html/index.html` |
| En jeu | Fabric GameTest (serveur headless, sous agent JaCoCo) | `./gradlew runGameTest` | `build/junit.xml` + console |
| Client | Vrai client du jeu, fenêtre cachée, harnais du mod de test (sous agent JaCoCo, erreurs OpenGL relevées) | `./gradlew runClientTest verifyClientTests` (lancés par `build`) | `build/client-junit.xml`, `build/run/clientTest/logs/`, `build/run/clientTest/captures/` |
| Couverture de tout le code | JaCoCo, sans aucune exclusion (unitaire + en jeu + client) | `./gradlew jacocoAllReport` | `build/reports/jacoco/all/html/index.html` |
| Couverture fusionnée et verrou | JaCoCo (unitaire + en jeu) : 100 % des instructions et des branches | `./gradlew jacocoCoverageVerification` (lancé par `build`) | `build/reports/jacoco/full/html/index.html` |
| Conventions du code | Checkstyle (`config/checkstyle/`) | `./gradlew checkstyleMain checkstyleTest checkstyleGametest` (lancés par `build`) | `build/reports/checkstyle/*.html` |
| Contenu du JAR | tâche `verifyJarContents` | `./gradlew verifyJarContents` (lancée par `build`) | console |
| Performance | Benchmark déterministe | `./gradlew benchmarkLevelZeroGeneration` | console |

Les échecs de tests font échouer le build, et la couverture aussi : voir Couverture.
`./gradlew build` enchaîne tout cela (`check`) : rien ne se construit sans ces vérifications.

---

## Couverture

**État au 29 octobre 2026, à lire d'abord.** Le verrou à 100 % ne porte que sur le code hors des exclusions de
`build.gradle` (`coverageExcludes`) : rendu et son côté client, mixins, compatibilité, soit environ la moitié des
lignes du mod (10 980 sur 21 777 lignes de code). Cette moitié n'était vérifiée qu'au banc, à la main. Décision de
l'auteur du même jour : tout le code doit être couvert, en tests unitaires, d'intégration et de non-régression. Les
tests du client (section suivante) mesurent désormais ce code : avec leurs treize premiers tests et la fin de partie,
la couverture de tout le mod, exclusions comprises, est de 86,5 % des instructions et 77,8 % des branches
(`./gradlew jacocoAllReport`), les mixins comptés à zéro. Une
exclusion se retire de `coverageExcludes` dès que sa classe atteint 100 % par l'ensemble des tests ; la liste ci-dessous
est donc une liste de travail, pas une exemption durable. Les mixins (tissés dans les classes vanilla, invisibles pour
JaCoCo tels quels) se mesureront par leurs classes cibles exportées (`-Dmixin.debug.export=true`).

`./gradlew jacocoCoverageVerification` fusionne l'exécution des tests unitaires (`build/jacoco/test.exec`), celle des
tests en jeu (`build/jacoco/gametest.exec`, l'agent JaCoCo accompagne le serveur GameTest) et celle des tests du client
(`build/jacoco/clienttest.exec`), et **échoue si une seule instruction ou une seule branche du code mesuré n'a été prise
par aucun d'eux**. `./gradlew build` l'enchaîne
après `test` et `runGameTest`. Une branche qu'aucun cas réel ne prend (garde côté client d'un code qui ne tourne que
sur le serveur, condition déjà garantie par une autre) se retire ; une décision pure enfouie dans un code d'état
(échéance d'un journal, tirage pondéré) s'extrait en fonction testable, sans changer ce qu'elle décide.

Code mesuré : tout le mod sauf ce qui exige un contexte OpenGL ou un client, hors de portée de JUnit comme du serveur
GameTest, et les mixins (tissés dans les classes vanilla). La liste exacte est `coverageExcludes` dans `build.gradle` :

| Exclu | Pourquoi |
|-------|----------|
| `PortalStencilRenderer`, `FrameAreaRenderer`, `HandMask`, `PortalClipping`, `ViewLightmaps`, `portal/client/world/**` (`RemoteWorld`, `RemoteViewRenderer`, `ViewSectionWalker`, `FrozenEntityLayers`, `CoarseTerrain` (dessin du terrain simplifie), `FrozenViews` (framebuffers et quad des images figees), `RemoteParticles`, `RemoteWorldRegistry`, `RemoteSyncReceiver`, `ClientPortalIndex`, `ClientPortalLight`, `PortalSightReporter`, `StraddlingEntityRenderer`) | appels GL, `WorldRenderer`, `MinecraftClient` |
| `ShaderPackPortalRenderer`, `ShaderPackState` | appels GL et rendu complet d'un `WorldRenderer` ; programmes de vanilla lus au milieu du rendu du joueur (un pack de shaders les remplace par les siens) |
| `compat/**` (`CompatMixinPlugin`, `IrisVersion`, `IrisVersionNotice`, `IrisCompat`, `PipelineSettings`, mixins du mod de shaders) | modules de compatibilité : le mod visé (Iris) est absent des tests ; vérifiés au banc de production (traversée sous un pack, image du Level 0 comparée à celle du pack coupé) |
| `portal/client/transition/**`, `portal/client/interaction/**`, `portal/client/sound/FrameSoundPlayer`, `portal/client/sound/SoundAnchors`, `portal/client/entity/**`, `portal/client/wand/**` | bascule de monde client, visée et clics du client, son rejoué par le moteur de son du client et sources OpenAL replacées après une traversée, renderer d'entité, écran de la baguette de portail (widgets du client ; ce qu'il envoie, le payload et sa réception, est testé) |
| `level0/client/**`, `core/perf/client/**`, `GtgsBackroomsClientMod` | modèles, hooks client (dont `LevelZeroAcoustics` : graine du layout reçue, forme des salles donnée au moteur audio ; `FrameStateCommand` : commande du client `/backrooms_state`, état de l'image lu dans les classes du client), point d'entrée client |
| `core/render/client/**` (`LookResources`, `LookFrame`, `LookUniformUploader`, `LookFog`, `LookDimensionEffects`, `LookPrograms`, `LookPost`, `FramebufferRegistry`, `FullscreenQuad`, `FullscreenTriangle`, `GpuTimer`, `StencilDebugOverlay`, `NeonIndex`, `NeonLights`, `ShaderCompileCheck`, `NeonFaces`, `NeonShaderCheck`, `GlStateCheck`) | colle du moteur de shaders : gestionnaire de ressources et rechargement, qualité lue dans la chaîne du pilote, uniformes GL, caméra et monde du client, effets de dimension, programmes du mod, post masqué et bloom (blit, stencil, triangle plein écran, framebuffers à échelle), framebuffers, quad plein écran, mode de débogage du stencil, index des néons par monde client (évènements de chunk, `ChunkSection`), bloc d'uniformes des néons (UBO), compilation à blanc d'un shader par le pilote, rectangle de la texture du néon dans l'atlas des blocs (modèles du client), vérification du jumeau GLSL sur le processeur graphique (shader de test, pixels relus), contrôle de développement de l'état GL autour des passes (lectures au pilote) |
| `core/audio/client/**` (`AudioResources`, `EfxBackend`, `AcousticRouter`, `NeonHum`, `BlockSolidity`, `SolidityIndex`, `AcousticWorker`, `CueResources`, `CueTracker`, `AdjustedSource`, `AcousticDebugRenderer`) | colle du moteur audio : gestionnaire de ressources, extension EFX d'OpenAL (slots, effets, filtres, erreurs), sources du moteur de son vanilla et son thread, instances de son du bourdonnement (monde du joueur et cadres des portails) et de leurs claquements, formes des blocs (`BlockState`), copie du monde client par sections (chunks, évènements de chunk), thread de calcul et ses files, décodeur Ogg et jeux de sons vanilla (enveloppes, cache sur disque), position de lecture OpenAL des sons liés, recherche des objets EFX d'un autre moteur, part de volume et décalage de position d'une source, tremblement posé sur la caméra du joueur, dessin des rayons de débogage |
| `mixin/**` | mesurés à travers les classes vanilla qu'ils modifient |

La logique client pure reste mesurée et testée : `PortalViewTransform` (plan de découpe, caméra transposée), `ViewCone`
(volume visible à travers le cadre), `ShaderClipInjector` (contre 54 des 55 vertex shaders vanilla du jar du jeu,
`blit_screen` exclu), `PortalRenderState`, `ThirdPersonPortal` (recul de la caméra à travers le cadre), `PortalSound`
(portée et volume du son à travers le cadre, meilleur cadre ouvert et visible vers le monde du son, par un cadre ou
par deux). Le rendu exclu se valide en jeu (`docs/PORTALS.md`, section Tests). La colle OpenAL du moteur audio est
exemptée de la même façon, nominativement dans `coverageExcludes` ; sa logique (dimension d'émission, occlusion,
paramètres de réverbération, rayons, forme générée, direction perçue, enveloppes, perturbations, plafond de
clignotement, tremblement) reste mesurée.

Les sources que le pilote reçoit sont tenues par leurs empreintes (`PatchedShadersGoldenTest`,
`src/test/resources/patched_shaders_1_21_1.json`) : chaque shader de base de 1.21.1, compilé comme le jeu, coupé au plan
des portails puis patché par le mod. Un changement voulu d'un patch, d'une bibliothèque GLSL ou de l'injecteur du plan
fait échouer ce test, qui donne la table recalculée : la recopier dans le fichier, comme les empreintes de génération
(`LevelZeroGoldenTest`), et le dire dans le CHANGELOG.

Le jumeau GLSL du calendrier des néons (`neon_schedule.glsl`) suit `NeonSchedule` par un fichier de vecteurs communs
(`src/test/resources/neon_schedule_vectors.json`, 573 vecteurs : position, graine, tick, parts, puis caractère et
intensité attendus), écrit par un programme indépendant de Java : `NeonScheduleTest` y confronte Java à chaque build, et
le client de développement y confronte le shader sur le processeur graphique (option
`gtgsbackrooms.look.verifyNeonShader=<chemin du fichier>`, journal « N vecteur(s) identiques »). Un changement de l'un
des deux sans l'autre fait échouer l'un des deux contrôles.

Les classes utilitaires (constructeur privé qui refuse l'appel) sont couvertes par `UtilityClassesTest`, qui les
énumère ; une nouvelle classe utilitaire s'y ajoute. Les tests en jeu tournent avec un cooldown de traversée de trois
ticks (`-Dgtgsbackrooms.portal.teleportCooldownTicks=3` dans la configuration `gameTest` de `build.gradle`) pour exercer
le refus sous cooldown, nul par défaut en jeu.

---

## Tests unitaires

### Deux familles

1. **Logique pure** (coordonnées, hash, secteurs, layout, biomes, détails muraux, portail) : aucune
   initialisation Minecraft, s'exécutent en quelques secondes.
2. **Avec registres réels** (blocs, items, palette, writer, générateur, ressources) : appeler
   `MinecraftTestBootstrap.ensure()` dans un `@BeforeAll`. Le helper démarre Minecraft une seule fois
   par JVM puis exécute le vrai `onInitialize()` du mod. Grâce à `fabric-loader-junit`, les registres ne
   sont pas gelés et les mixins de Fabric API s'appliquent.

```java
@BeforeAll
static void bootstrap() {
    MinecraftTestBootstrap.ensure();
}
```

### Ce qui est couvert

| Zone | Classes de test | Ce qui est vérifié |
|------|-----------------|--------------------|
| Coordonnées et pile verticale | `LevelZeroCoordsTest`, `LevelZeroLayerStackLayoutTest` | conversions, offsets de layer, bornes |
| Seeds | `StageRandomTest`, `LevelZeroSeedResolverTest` | formule figée, sels distincts, seeds de layer |
| Secteurs | `LevelZeroSectorGeneratorTest`, `LevelZeroSectorCacheTest` | déterminisme, salles, LRU |
| Layout | `LevelZeroLayoutTest`, `LevelZeroCellEvaluatorTest`, `LevelZeroTopologyTest`, `LevelZeroLargeRoomsTest`, `LevelZeroLightPatternTest`, `LevelZeroCorridorGapsTest`, `LevelZeroRegionWalkabilityTest`, `LevelZeroCellMicroPatternTest`, `LevelZeroCellConnectionsTest`, `LevelZeroTieBreakTest` | invariants : origine ouverte, cellules 3x3, **frontières de chunk identiques**, néons jamais adjacents, gaps jamais consécutifs |
| Biomes | `LevelZeroSurfaceBiomeTest` | régions stables, restriction par layer, densité lumineuse |
| Écriture | `LevelZeroBlockPaletteTest`, `LevelZeroBlockWriterTest`, `LevelZeroWallSamplerTest`, `LevelZeroWallPropsTest`, `LevelZeroDebugPaletteTest` | colonnes ouvertes/murs/néons, court-circuit du sampler = sonde complète, modes debug et leur palette |
| Blocs du Level 0 | `LevelZeroWallpaperBlockTest` | mur adaptatif : seize variantes de faces, papier jauni ou blanc selon le layer et la région |
| Générateur | `LevelZeroChunkGeneratorTest` | `populateNoise` sur un vrai `ProtoChunk`, `getColumnSample` identique au chunk, déterminisme |
| Non-régression | `LevelZeroGoldenTest` | empreintes de génération par seed et layer |
| Registres et ressources | `ModRegistriesTest`, `ModDatapackLoadingTest` | identifiants, items de blocs (le bloc portail n'en a pas), onglet complet, blockstates/modèles présents, loot tables de ceux qui rendent quelque chose, chaque son déclaré dans `sounds.json` avec son sous-titre, EN = FR, dimension Level 0 chargée depuis le datapack du mod |
| Config et perf | `ModConfigTest`, `ModPerformanceMonitorTest`, `PortalNetworkTest` | parsing, valeurs par défaut, mesure active (sections serveur et client, images lentes, allocation par section, paquets et octets par payload), charge CPU relevée par un thread démon (inconnue sans relevé ou sans extension JVM, lancé et arrêté avec le moniteur, boucle qui s'arrête à l'interruption), taille encodée d'un payload |
| Utilitaires et point d'entrée | `UtilityClassesTest`, `GtgsBackroomsModTest`, `ModCreativeTabTest` | classes finales non instanciables, portails activés ou non, branchements idempotents, onglet rempli (baguette en tête, sans bloc portail) |
| Mixins | `MixinTargetsTest` | chaque mixin déclaré (communs et client) s'applique à sa cible : les tests unitaires tournent côté client sous le chargeur Fabric, et charger la cible applique le mixin ; un mixin client cassé fait échouer le build au lieu de planter le client au lancement ; aucun `@Redirect`, que le MixinExtras d'un Fabric Loader plus ancien (0.19.2) ne sait pas lire |
| Client (logique pure) | `PortalViewTransformTest`, `CrossingRollTest`, `ViewConeTest`, `ShaderClipInjectorTest`, `PortalRenderStateTest`, `RemoteViewRendererTest`, `ThirdPersonPortalTest`, `LazyVertexBuffersTest`, `ObliqueProjectionTest`, `ViewRenderStartTest` | plan de découpe en espace caméra, caméra et regard transposés, roulis de la caméra du joueur à la traversée (un quart de tour d'un sol à un mur, regard de côté ; un demi-tour par un cadre d'arrivée retourné ; aucun entre cadres debout, en travers de l'axe ou du sol au plafond ; redressement en cube du temps restant, en moins d'une demi-seconde ; glissement de l'image de l'œil vers l'œil, rien pour un écart négligeable), rotation d'un portail sur les 144 paires et roulis de la caméra d'une vue (d'un sol à un mur, puis retour au haut du joueur ; aucun entre deux cadres debout), rotation recopiée, volume visible à travers le cadre (et sans limite), volume entre la caméra et le cadre (bloc coupé au plan du cadre pour les six orientations : un mur qui borde le cadre ne compte pas), recul de la caméra à la troisième personne à travers le cadre ou au-dessus du linteau, injection du plan dans 54 des 55 vertex shaders vanilla (`blit_screen` exclu), garde de rendu et numéro d'image, coupure du rendu distant, ancrage de la grille de sections, drapeau des objets GL différés des renderers de vue, projection à plan proche oblique (point du plan à -1, devant coupé, coin lointain gardé, écran inchangé, caméra déjà du bon côté : projection telle quelle), paramètres et drapeau du début de rendu à blanc d'un renderer de vue, quadrilatères rognés au plan sur le processeur (`ClippedVertexConsumersTest` : entier, rien, moitié avec attributs interpolés, pentagone en deux quadrilatères, triangle, format inconnu transmis tel quel), preuve qu'un cadre est caché derrière des blocs (`FrameOcclusionTest` : mur plein, troué dans le cône ou hors de lui, collé au plan, bloc pas encore dessiné, bloc plein devant le mur, mur épais, caméra dans un bloc, deux sens de chaque axe, sol et plafond, de biais à la face d'entrée, arête commune de deux cases), palier de détail d'une vue (`ViewDetailTest` : taille d'un bloc à l'écran selon la résolution, le zoom et la distance, paliers d'une vue et niveaux d'une section, descente avec ses marges et son délai), maillage du terrain simplifié (`CoarseSectionMesherTest` : section vide ou enfouie, six côtés en carrés, couloir à sa hauteur exacte, mur fusionné depuis son premier bloc, états jamais mêlés, carrés qui ne débordent pas, bloc impossible à dessiner, voisin lu dans la section d'à côté), image figée (`FrozenImageTest` : place d'un point dans le rectangle capturé à toute profondeur, rectangle décentré, reprise), départ du parcours des sections au cadre (`ViewConeTest`) |
| Moteur audio (logique pure) | `AudioProfileTest`, `AudioProfilesTest`, `ReverbParametersTest`, `OcclusionTraceTest`, `EmissionDimensionTest`, `SolidityWindowTest`, `SolidityGridTest`, `AcousticEvaluatorTest`, `AcousticCacheTest`, `ReverbPresetsTest`, `PortalPlanesTest`, `AudioQualityTest`, `GeneratedAcousticsTest`, `PerceivedDirectionTest`, `RayTraceTest`, `LevelZeroRoomShapeTest`, `LevelZeroLayoutPayloadTest` | profil du Level 0 (son de bourdonnement enregistré, acoustique générée, milieu sous l'eau, direction perçue), mode d'acoustique (`none`, `raycast`, `generated`, refus), défauts, refus ; registre, doublons ; réglages de réverb dans les plages de l'effet ; occlusion par parcours de voxels (axe, diagonale, haut et bas, plafond atteint tôt, trajet trop long), deux segments combinés et gains du filtre ; dimension d'émission (ancre, monde du joueur, relatif, inconnu) et salle de l'auditeur ; fenêtre de sections (bords sur chaque axe, index des blocs, recentrage) ; copie du monde (sections posées et oubliées, colonne déchargée, balayage autour de plusieurs ancres, fenêtre tirée) ; rayons (salle fermée, plein air, monde non copié perdu, mur d'un côté, cadre de portail vers le plein air ou vers une autre salle, détours par une ouverture, détours dans un mur ou bouchés, mur épais, bloc partiel contourné, nombre de chemins borné) ; cache par cellule (fraîcheur, remplacement, cellules de quatre blocs, résultat fiable) ; réglages de réverb (taille, ouverture, dérivés du profil) ; cadres traversés (rectangle, sens d'entrée, plus proche, liste bornée et copiée, transposition identique à celle des entités) ; qualité (option, alignée sur le rendu) ; forme générée par dimension (enregistrée, oubliée, accord avec la copie) ; direction perçue (chemin droit libre, détour qui gagne, part du profil, même distance) ; segments des rayons (capacité, types, occlusion) ; salle du Level 0 lue dans son layout (salle de l'origine, même graine même salle, mur et hors de l'air jouable, accord avec la copie, bloc posé ou mur cassé, cache borné) ; graine du layout (codec) |
| Liaison son-image (logique pure) | `CueClockTest`, `EnvelopeTest`, `CueTest`, `CueRegistryTest`, `DisturbancesTest`, `FlashLimiterTest`, `EasingTest`, `CameraShakeTest` | horloge du monde (fraction de tick, période, précision après des heures) ; enveloppe (silence, impulsion, rampe interpolée, voies entrelacées, sons minuscules, hors du son) ; enveloppe en octets pour le cache sur disque (aller-retour, fichier abîmé, nom par empreinte) ; lien du cri livré, défauts, refus (identifiant, sons, réactions, type inconnu, courbe inconnue, plages) ; registre (son réclamé par deux liens) ; perturbations (distance, amplitude, dimension, déplacement, fin, quatre au plus et la plus ancienne remplacée, réactions absentes) ; plafond de clignotement (trois variations fortes par seconde, adoucissement, option de confort, remise à zéro, charge de l'écran pondérée par la surface des vues) ; courbes (0 en 0, 1 en 1, bornes, forme) ; tremblement (borné, nul sans amplitude, le même partout au même instant) |
| Moteur de shaders (logique pure) | `ShaderPatcherTest`, `ShaderPatchTest`, `ShaderPatchRegistryTest`, `PatchedShadersGoldenTest`, `LookProfileTest`, `LookProfilesTest`, `LookStateTest`, `DimensionLightmapTest`, `DimensionFogTest`, `NeonScheduleTest`, `NeonSelectionTest`, `NeonMapTest`, `StencilLookMaskTest`, `FramebufferSpecTest`, `LookQualityTest` | inventaire versionné des 110 shaders de base de 1.21.1 (`src/test/resources/shader_inventory_1_21_1.json`) recalculé sur le jar du jeu, sources compilées par le `GlImportProcessor` vanilla ; les 55 fragment shaders patchés par enveloppe de `main` (bibliothèque en tête, main vanilla renommée, sortie passée par le look, `discard` gardé, idempotence) ; points d'injection dans l'ordre, sortie qualifiée, commentaires blanchis sans déplacer un caractère ; refus par raison (version, directives et macros, main, sortie) ; lecture d'un patch (motifs, exclusions, bibliothèques, injections) et d'un profil (profil du Level 0, défauts, refus) ; registre des patchs (shaders du monde et eux seuls, fichiers invalides écartés, bibliothèque partagée lue une fois) et des profils (doublons) ; pile des looks (version, remises à zéro, bornes, looks vus dans l'image) ; courbe de lightmap (plancher, plafond, chaleur) ; règle du brouillard ; état des néons (parts qui vacillent et mortes par position, coupures de quatre ticks, même résultat partout, intensité jamais nulle) ; liste bornée des plus proches ; carte des néons d'un monde (sections remplacées, bloc posé ou retiré, colonne déchargée, collecte à portée) ; bits du stencil ; framebuffer déclaré en données (copie d'écran du mod, défauts, échelle arrondie, refus) ; qualité (option, détection d'une carte intégrée) ; empreintes des 90 sources patchées ; profil sous les options de confort (sans flashs, sans bruit visuel) ; compteurs de la dernière image |
| Portails | `PortalFacingTest`, `BackroomsPortalEntityTest`, `BackroomsPortalBlockTest`, `PortalSettingsTest`, `PortalWayBackTest`, `PortalWandItemTest`, `PortalTargetsTest`, `LevelZeroArrivalTest`, `VirtualLightSourcesTest`, `PortalRelevanceTest`, `TeleportExecutorTest`, `PortalCollisionTest`, `ArrivalGuardTest`, `PortalPayloadsTest`, `PortalViewTrackerTest` (paquets relayés, portail vu à travers un autre), `RemoteChunkWindowTest`, `RemoteEntityViewTest`, `PortalSoundTest`, `PortalPursuitTest` | rotation sans miroir sur les 144 paires des douze orientations (dont les retournées, tête en bas), chaque coordonnée d'un point transformé aussi lue sans allocation, du sol au plafond une translation (cap et vitesse gardés), cadre couché en face de son ouverture dans ses deux axes, traversée d'un cadre couché aux yeux, regard devenu vertical qui garde le haut de l'image, point de sortie qui garde la place du point de traversée puis le corps du côté de la sortie et dans l'ouverture (debout, tête en bas comprise), chute gardée seulement si le bas reste le bas, sortie imposée qui ne tourne pas, faces arrière qui transposent comme les faces avant, NBT, bloc invisible visé seulement accroupi, réglages (format, dont pose, destination et sortie fixée sauvées et relues, illisibles oubliées, bornes, piles, colonnes, centre et pied du cadre, debout ou couché, plan au milieu ou au bord du bloc, bloc ancre retrouvé depuis le seul centre, sens de sortie tout droit, cardinal, par le sol ou le plafond, écart tiré au sort nul sans rayon et borné par lui), cadre d'arrivée qui tient sur toute sa largeur avec ses deux faces libres, sources de lumière immuables, traversée du plan (le centre en face de l'ouverture, la boîte touchant le cadre ; ni apparition ni saut de position), face franchie d'après le sens du déplacement, boîtes à cheval sur le cadre d'entrée ou d'arrivée à la position affichée, boîtes refaites quand la taille change, côté de sortie et image d'une boîte d'un portail à l'autre, entité engagée dans un cadre, blocs cachés derrière le plan dans l'ouverture prolongée (douze orientations), paroi du plan pour qui touche un bord du côté d'entrée seulement, ouverture fermée (paroi d'un bord, sol d'une garde), portails et côté venus des mondes branchés, treize payloads dont un paquet d'entité vanilla enveloppé et la libération d'une dimension, les portails vus et les réglages de la baguette, info-bulle de la baguette et traduction de chaque clé qu'elle et son écran emploient, retour lu dans les réglages (`PortalWayBack`), paquets relayés aux joueurs qui ont une dimension à portée, fenêtre de chunks et sa boîte d'entités, fenêtre en cône (compte du carré, chunks déjà voulus gratuits, profondeur dans l'axe, voisins, portée, cadre large vu de près, autre orientation, cadre retourné, caméra à côté ou derrière : rien) ; pertinence d'un portail : à portée quelle que soit la direction du regard pour la synchronisation, devant le joueur ou à portée d'activation pour le rendu ; volume du son après le trajet et sortie par le cadre où le joueur voit l'autre côté, point de passage du regard dans le cadre, point d'approche et alignement sur l'ouverture, cooldown des joueurs ; bords d'un cadre pour un joueur : quatre parois d'un demi-bloc autour de l'ouverture, coins compris, du plan à un demi-bloc derrière ; traversée d'un joueur aux yeux, dans la hauteur du cadre (au-dessus du haut, pas de traversée ; un objet passe à toute hauteur des pieds), et de même face à l'ouverture d'arrivée |

### Le test d'empreintes (`LevelZeroGoldenTest`)

Il hache, pour trois seeds et cinq layers, toute la génération logique d'une zone de 7x7 chunks
(évaluation des cellules, exposition, masques de papier peint, détails muraux). S'il échoue :

- régression → corriger ;
- évolution voulue de la génération → relancer, copier les nouvelles valeurs dans le test, et
  **l'annoncer dans le CHANGELOG** (les mondes existants changent).

### Lancer un sous-ensemble

```bash
./gradlew test --tests "*LevelZeroLayoutTest*"
./gradlew test --tests "com.gtgpuke.gtgsbackrooms.core.registry.*"
```

---

## Tests en jeu (GameTest)

Source set dédié `src/gametest` (Loom `fabricApi.configureTests`) : un mod séparé, `gtgs_backrooms_gametest`,
avec son `fabric.mod.json` et son entrypoint `fabric-gametest`, chargé par `runGameTest` seulement. Rien de tout cela
n'entre dans le JAR du mod, ce que `verifyJarContents` vérifie à chaque `build`. Le serveur de test vanilla force le
preset plat avec l'overworld, le Nether et l'End, sans les dimensions de datapack : le Level 0 n'y existe pas, et le
Nether tient lieu de dimension distante pour les portails. Le dossier de run (`build/run/gameTest/`) n'est vidé que par
`clean` : sur un dossier neuf (build complet), le Nether se génère, et le serveur de test tique sans attendre ; les tests qui ont besoin de
l'arrivée la génèrent d'avance (`GameTestServerWorlds.pregenerateArrival`, 11x11 chunks autour d'elle ; `pregenerate` pour un rayon
donné) puis guettent ses chunks au lieu de compter les ticks. Une arrivée se calcule d'un trait par `GameTestArrivals` : la
recherche chunk par chunk du jeu, relancée avec le même tirage après chaque chunk qu'elle demande, généré sur place. Une tâche `createAndAdd` est rejouée à chaque tick tant qu'elle
lève, jusqu'au délai du test : elle sert à attendre une condition. Une vérification définitive va dans `createAndAddReported`,
jouée une fois, qui fait échouer le test aussitôt ; son nettoyage se fait en `try/finally`, et ce qu'une attente expirée
laisserait derrière elle, dans un `@AfterBatch` du lot.

L'arborescence suit celle du code : `support/` (joueurs simulés, monde prêté), `backrooms/level0/`, `core/portal/`.

| Classe | Ce qui est vérifié |
|--------|--------------------|
| `backrooms/level0/LevelZeroGameTests` | JSON de la dimension décodé et réencodé avec les registres du serveur, chunk d'origine généré : sol, air, plafond, bedrock sur les cinq layers ; mur adaptatif posé (masque recalculé ou déjà juste) |
| `core/portal/BackroomsPortalBlockGameTests` | pose vers une zone jamais générée (garde-fou G4) : au tick de la pose le bloc attend le premier chunk de sa recherche par un ticket sans rien générer ; au tick suivant ce chunk a son niveau de ticket (`isChunkLoaded` vrai) sans être entier, et la recherche l'attend sans le générer elle-même ; puis le bloc résout la même arrivée que la recherche menée d'un trait par le test (`GameTestArrivals`) ; copie d'un bloc (clone, structure) : destination gardée, portails propres, ceux de l'original intacts ; réglages seuls envoyés aux clients ; sens de sortie imposé : cadre d'arrivée tourné vers l'est, à l'aplomb du bloc ; sortie par le plafond : cadre d'arrivée couché face au bas, relevé de deux blocs ; pose couchée (regard vers le bas : au sol, même sur un sol plein, vers le haut : au plafond, debout sans joueur) et plan visable d'un cadre couché ; arrivée tirée au sort : aux X/Z tirés dans le rayon, sans tourner ; relu avec un autre sens de sortie, le cadre d'arrivée tourne sur son pied, entrée et retour reposés ; couché avant ou après, le bloc retire ses portails et cherche de nouveau son arrivée ; une face : rien de dos, on passe au travers sans traverser ; aller simple : rien à l'arrivée, retours retirés à la relecture (dimension absente comprise) ; sortie fugace : jamais vue, elle attend un regard, même avec un joueur tout près ; vue, elle tient sous le regard d'un joueur, même de loin, et disparaît après une demi-seconde sans regard, même tout près (un rapport vide isolé ne la perd pas ; sauvée, jamais reposée par les vérifications des retours, neuve pour une copie du bloc ; un retour qui devient fugace à la relecture est reposé, l'entrée gardée) ; rouverte aussitôt par le prochain joueur qui passe l'entrée, à une face comme le bloc, puis de nouveau une seconde pour se retourner et le regard seul ; ni un objet, ni un spectateur, ni un portail qui n'est pas l'une des entrées du bloc ne la rouvrent, et une sortie encore là n'est pas reposée, un rapport fait dans un autre monde ignoré, un spectateur qui la regarde ou un joueur sans rapport ne la gardant pas, un rapport de plus de 40 ticks ne comptant plus ; seul le joueur qui la voit la traverse (un portail libre marqué fugace, jamais), et qui sort par elle a une seconde pour se retourner, pour elle seule et pas en repassant par elle, oubliée (avec son dernier rapport) au changement de monde vanilla et au respawn ; sans rien casser vers une dimension absente ; portail que son bloc ne tient plus pour sien retiré par le registre ; quatre portails (deux faces dos à dos de chaque côté), retours inverses exacts, même transformation par les deux faces, taille et lumière réglées ; bloc tourné ou relu avec une autre largeur, lumière, destination, dimension ou hauteur → portails reposés, relu au bord du bloc → plan d'arrivée reculé d'un demi-bloc sur son pied, relu tête en bas → cadre d'arrivée retourné sur place ; cassé → plus rien (retours compris), y compris sans block entity ; traversée par la face arrière ; pose face au joueur avec les réglages de l'item, cadre bloqué refusé, visé seulement accroupi ; cibles par dimension, sans destination ou vers une dimension absente, face perdue attendue puis reposée, retour disparu ou jamais posé, Level 0 prêté au serveur (retour suivi puis retiré avec le bloc), bloc inerte quand les portails sont désactivés ; arrivée posée sur le haut réel du sol (dalle, tapis, eau) |
| `core/portal/PortalLightGameTests` | lumière des blocs passée d'un niveau de moins par un portail ouvert, visible et réglé pour elle, la plus forte de deux l'emportant, rien par un portail fermé, invisible, réglé sans lumière, sans arrivée ou vers le noir ; source publiée mais pas encore appliquée signalée de nouveau ; cadre d'un portail qui apparaît revérifié (lumière fantôme éteinte) ; portail double face 4 x 4 éclairé des deux côtés puis éteint avec sa lumière ; plan au bord du bloc : le bloc devant son plan éclairé (chaque test dans son lot, loin du coin éclairé de l'arène) |
| `core/portal/PortalWandGameTests` | réglages de la baguette reçus pour la seule main qui la tient ; pose d'un bloc portail réglé, son d'un portail qui s'ouvre entendu par un témoin, évènement de pose entendu par un capteur sculk (lot à part, nettoyé par `@AfterBatch`), baguette gardée en survie, refus rendu en échec (cadre bloqué, place prise par un bloc plein, hors du monde) ; copie des réglages d'un portail visé, message au joueur, sans joueur aussi ; bloc cassé qui ne rend rien ; plan au bord du bloc : cadre debout posé contre un mur, centre sur la face arrière, plaque visée accroupi contre elle (au milieu là où n'est aucune block entity), cadre couché à ras d'un sol plein, arrivée en surface décalée d'un demi-bloc |
| `core/portal/PortalGameTests` | traversée d'un joueur dans le même monde (position, regard) ; objet lancé ; saut de moins de huit blocs envoyé en position absolue, marqueur sans suivi ; collision à travers le cadre (un objet poussé de biais bute sur le mur de l'autre côté, au milieu il passe ; rien derrière le plan, portail fermé, dimension absente, deux cadres engagés, joueur serveur ou portail d'un autre monde ignorés) ; bord du cadre (le centre hors de l'ouverture, l'objet bute sur le plan même sans rien de l'autre côté ; tout à côté, de dos ou portail fermé, rien) ; payload de chunk |
| `core/portal/PortalSyncGameTests` |  fenêtre de chunks envoyée à un joueur à portée d'un portail vers le Nether (foyer le plus proche, fenêtres partagées et fusionnées), entité distante retirée de son monde effacée chez lui au même tick, chunks tenus après une traversée valables 100 ticks, annonce de traversée refusée sous cooldown ou à la vitesse impossible, météo du Nether envoyée avec elle puis à chaque changement seulement, entités suivies, entité arrivée par un portail envoyée au même tick, blocs relayés, fenêtre gardée après un demi-tour à portée de rendu (trente blocs, passé le délai de grâce), déchargement une fois hors de portée ; dimension sans portail à portée : fenêtre déchargée après le délai de grâce, monde distant libéré chez le client après `remoteWorldReleaseSeconds` (une fois), puis annoncée de nouveau sous le même index et fenêtre renvoyée au retour du joueur ; traversée vers le Nether sans renvoi des chunks tenus qui n'ont pas changé (un témoin à l'arrivée reçoit de vanilla chaque bloc signalé, même revenu à l'identique dans le même tick : seul un chunk signalé depuis le passage repart, et vanilla repropose des chunks tenus sans les renvoyer) ; côté quitté gardé chargé après le départ du joueur, ses entités restant les mêmes (loin des chunks forcés de l'arène) ; annonce client vraisemblable, invraisemblable, sous cooldown, refusée (portail fermé, sans dimension, hors du cadre, en bateau) ; marche détectée par le serveur et remontée hors d'un sol irrégulier, franchissement abandonné si le portail disparaît, se ferme ou ne mène nulle part, joueur qui saute de position (un joueur en bateau, lui, traverse avec son bateau, bascule menée par le serveur) ; entités qui ne traversent pas (cooldown, bateau et passager déplacés par sauts de position, mort) ; changements de monde vanilla ; portails orphelins et chargement double ; commande de debug ; ticket élargi à l'approche ; portail invisible traversé sans vue ; chunk tenu qui change autour d'une traversée renvoyé par vanilla ; chunks tenus propres à chaque joueur et à leur dimension (un changement aux mêmes coordonnées d'une autre dimension n'en libère aucun) ; joueur spectateur montré par le portail aux seuls spectateurs ; texte d'un panneau écrit de l'autre côté et fissures de l'autre côté relayés ; vue d'un joueur qui vient de passer repartie au tick suivant ; deux dimensions dans une même vue (Nether et End) annoncées sous les index 0 et 1, que portent leurs blocs relayés, leur météo et leurs déchargements ; fenêtre en cône de loin (chunk au-delà du carré dans l'axe du cadre envoyé et tenu entier par son ticket tant qu'il est voulu, délai de grâce passé, rien sur le côté, aucune fenêtre vu de dos, ticket relâché après le délai de grâce) et arrivée couchée qui garde le carré ; bord de la fenêtre pris dans l'axe du cadre |
| `core/portal/PortalInteractionGameTests` | coups, tonte, clics, porte vanilla ouverte et fermée, cochon sellé jamais enfourché, spectateur refusé, fissures effacées quand le minage cesse ou que le mineur se déconnecte, mineur mort qui ne casse plus rien, rien de visé près du plan mais loin à côté du cadre, pose d'objets (main principale ou secondaire, bâton sans effet, tête sur un bloc musical) et minage à travers le cadre, en créatif et en survie, avec les refus (hors portée, derrière le portail, portail inconnu ou invisible, hors de la bordure du monde, épée en créatif, bloc de commande sauf opérateur, événement de casse, mode aventure, bloc disparu en cours de minage, portail à portée mais bloc hors portée, netherrack cassée sans récolte) |
| `core/portal/PortalPropagationGameTests` | sons, évènements de monde et particules relayés au seul joueur qui regarde l'arrivée ; un husk voit un joueur du Nether par le cadre, prend la posture d'attaque en chemin, traverse et le cible ; sa copie neuve repart aussitôt vers le joueur, posture comprise ; en l'air, sans chemin, elle le cible sans la posture ; la poursuite finie, il la quitte, et garde une cible de son côté ; un portail fermé, hors de portée de poursuite, vers une dimension absente, ou une vue masquée d'un côté ou de l'autre ne montre rien ; une cible dans un autre monde devient une poursuite, l'objectif de poursuite fait marcher le mob, d'abord en face de l'ouverture, puis aussitôt à travers, la cible le suit à la traversée ; qui reçoit l'objectif (husk, slime, golem de fer, pas le chat) ; chaque règle d'une poursuite (portail ouvert, visible, devant le mob, vers un autre monde, à portée ; cible vivante, de l'autre côté ; délai) écarte à elle seule |
| `core/portal/PortalRidingGameTests` | un bateau emmène sa vache dans le Nether (copies, vache toujours assise, position et vitesse transposées, cooldown) ; un bateau mené par un joueur, avec une vache, passe dans le Nether : le joueur descend, traverse par sa propre bascule (`portal_teleport`) et remonte sur la copie, dont son client reçoit les passagers avec l'apparition ; le bateau avance ensuite sous le cooldown du joueur sans rien déclencher ; dans le même monde, le joueur reste dans le même bateau et son client reçoit ses passagers et la position du bateau (`EntityPassengersSet`, `VehicleMove`) ; un cheval monté dans un bateau garde son cavalier de l'autre côté ; vers une dimension absente, un changement de bateau, un saut de position aller puis retour et un tick immobile ne sont pas des trajets, le bateau passe le plan comme de l'air et le joueur y reste assis |
| `core/portal/PortalOrientationGameTests` | les 144 paires d'orientations (droites et retournées) avec un joueur : on file hors du cadre d'arrivée, on regarde où l'on va, la chute n'est gardée que si le bas reste le bas, le corps sort du bon côté (pendu sous un plafond, au-dessus d'un sol, debout dans la hauteur d'un mur) et en face de l'ouverture ; un objet traverse seul chacune des six normales, un cadre sur deux retourné ; en survie, vingt blocs de chute renvoyés à l'horizontale ne blessent pas à l'atterrissage, une chute qui continue (du sol au plafond) si ; un cadre couché au sol, ou un cadre debout vers un sol, n'a que sa face avant, un cadre debout vers un mur garde ses deux faces ; un bloc à l'arrivée tête en bas pose ses portails d'entrée et de retour retournés, le cadre d'arrivée à sa place, et le joueur en ressort le regard renversé, debout dans l'ouverture |
| `core/portal/PortalLyingGameTests` | un porte-armure tombe dans un cadre au sol et ressort à l'aplomb sous un cadre au plafond, puis retombe au sol ; une plume qui monte entre dans un cadre au plafond et ressort d'un cadre au sol, en continuant de monter, cooldown armé ; un joueur simulé qui tombe dans un cadre au sol est rattrapé par le serveur (yeux passés, cinq ticks de grâce) et ressort sous le cadre au plafond, bascule reçue par son client ; la traversée se décide aux yeux pour les deux cadres couchés |
| `core/portal/PortalArrivalReferenceGameTests` | arrivées de référence (garde-fou G3 de `docs/revue/BILAN-CORRECTIONS.md`) : huit cas fixes en coordonnées absolues sur le terrain naturel du serveur de test (graine 0), Nether en zone neuve pour trois cadres, surface, vide de l'End, destination choisie, sortie fixée exacte et tirée au sort ; le terrain de chaque cas est généré d'abord, chunk par chunk dans un ordre fixe (vanilla pose les features des voisins en parallèle : sans cela, un monde neuf sur deux avait un autre terrain près des bords de chunks, et une autre arrivée) ; l'arrivée attendue de chacun (dimension, centre, orientation) est figée, relevée sur la 0.10.0, identique sur deux mondes neufs et un monde déjà joué ; un remaniement de la recherche qui en change une rougit |
| `core/portal/PortalDestinationGameTests` | dans une dimension à plafond (colonnes du Nether préparées), l'arrivée sur le premier sol sous le départ, pas dans une poche de lave, ni sous de la lave à hauteur de tête pour un cadre au sol, ni pour un cadre plus haut que la poche, au-dessus à défaut, mais pas un cadre dont le haut serait dans la lave ; dans le vide de l'End, une plateforme d'obsidienne au point de départ, la place dégagée (un bloc posé là compris), posée au-dessus de l'eau, une hauteur hors du monde ramenée dans sa hauteur logique ; vers le Nether, les X/Z au huitième (colonne préparée) ; vers une dimension absente, rien ; la sortie fixée l'emporte (sur son bloc, dans la direction du regard ; un sens imposé l'emporte sur le regard ; tirée au sort autour d'elle ; dans une dimension absente, rien) ; les réglages reçus : sortie fixée là où se tient le joueur prise avec la destination, destination inconnue oubliée, sortie déjà fixée gardée, nouvelle sortie loin du joueur ou dans un autre monde refusée, effacée ; pose imposée (au sol, au plafond, debout) quel que soit le regard ; bloc relu vers une autre sortie fixée, puis vers le monde normal : arrivée cherchée de nouveau ; chunk par chunk (`resolveLoadedOnly`) : une sortie fixée exacte sur un chunk chargé n'attend rien, sur un chunk jamais généré attend ce chunk ; tirée au sort loin de tout, la recherche s'arrête au premier chunk absent près de la marque ; dimension absente et Level 0 n'attendent rien ; à ciel ouvert, la colonne de départ (X/Z fois huit) attend son chunk ; sous un plafond, un cadre de huit au bord d'un chunk chargé attend le chunk voisin où il déborde, puis avance une fois celui-ci chargé |
| `core/portal/PortalTunnelGameTests` | bord d'un cadre au sol en l'air : un porte-armure le centre hors de l'ouverture se pose sur le plan, tout à côté il tombe au sol, au milieu il passe ; blocs cachés derrière le plan : un porte-armure qui tombe sur un cadre posé sur la pierre passe et ressort en tombant, son élan gardé, la pierre à côté le portant encore ; une plume passe un mur par le cadre plaqué contre lui, un bâton bute à côté ; un corps engagé n'est pas dans le mur qu'il traverse (bloc caché dans l'ouverture, pas à côté), sauf portail fermé, et un portail ne s'engage dans aucun autre ; engagement retenu une fois le plan passé sans que la face arrière compte, repris par un cadre au même centre qui n'est pas dos à dos ou par un cadre derrière, perdu hors de la largeur, avec un cadre retiré, dans un autre monde et après `CROSSING_TICKS` ; garde d'arrivée : un porte-armure ressorti d'un cadre au sol en l'air s'y pose et y tient plus que le cooldown, poussé à travers le plan ne repart pas, accroupi repart (avant la fin du répit) et se pose sur l'autre cadre, en descend puis y revient et repart, resté immobile sur son cadre y retombe une fois le répit passé, pas avant ; un joueur posé sur un cadre au sol en est gardé (aucun des cadres voisins ne le prend), son annonce de traversée est refusée, le serveur ne le fait pas passer quand il descend sans rien annoncer, la garde tombe quand il change de monde, puis une annonce les yeux juste passés, les pieds bien plus bas, est acceptée ; un joueur franchit tout cadre aux yeux (un objet aux pieds) : aligné, rien ne l'arrête ; une épaule qui dépasse bute sur le bord est ; les pieds sous le bord bas d'un cadre surélevé butent dessus ; les yeux au-dessus d'un cadre trop bas : ouverture fermée au front, et le serveur ne le fait pas traverser là où un objet passe |

Les joueurs simulés viennent de `GameTestPlayers` : connexion réelle au serveur sur un canal en mémoire (liste des
joueurs, suivi des chunks, événements de connexion), mode de jeu au choix, `setOnGround(true)` faute de client pour le
dire, `tick(player)` fait tourner son gestionnaire réseau en acquittant les lots de chunks (le serveur ne tique pas
lui-même ces connexions), et `receive(player, payload)` fait entrer un payload comme venu de son client. Les tests qui posent des blocs, des
entités ou des joueurs utilisent l'arène `gtgs_backrooms_gametest:test_arena` (24 x 8 x 24,
`src/gametest/resources/data/gtgs_backrooms_gametest/structure/test_arena.nbt`) :
la structure vide de Fabric fait un bloc et les tests voisins se marcheraient dessus. Dans l'arène, le sol est en
y = -1 relatif ; y = 0 est la première couche d'air.

```bash
./gradlew runGameTest
```

La tâche échoue si un test échoue. Dossier de run : `build/run/gameTest/` (logs dans `logs/latest.log`), rapport
par test dans `build/junit.xml`.

---

## Tests du client

Fabric API n'a pas de tests en jeu côté client pour 1.21.1 : le mod de test (`src/gametest`, jamais dans le JAR) porte
son propre harnais, dans `gametest/client/`. `./gradlew runClientTest` lance un vrai client (configuration Loom
`clientTest`, dossier `build/run/clientTest/`), fenêtre cachée (`MixinClientTestWindow` ; `-PclientTestVisible=true` la
montre), options figées (`options.txt` réécrit à chaque lancement : distance de rendu 8, sans pause à la perte du
focus) et son sur le périphérique nul d'OpenAL Soft (`ALSOFT_DRIVERS=null` : EFX complet, rien d'audible). Le client
crée un monde plat neuf (`ClientTestWorld` : graine 0, créatif, sans cycle du jour, météo, créatures ni tick
aléatoire), y entre, joue chaque test, écrit `build/client-junit.xml` et se ferme ; `verifyClientTests` fait échouer le
build si un test a échoué ou si rien n'a été rapporté. Un chien de garde écrit le rapport et arrête le client si plus
rien n'avance pendant cinq minutes.

Un test (`ClientScenario`) est une suite d'étapes jouées tick après tick : une action (sur le client, ou sur le
serveur intégré par `ClientTestContext.onServer`, qui attend sa fin), une attente bornée (`await`), un nombre d'images
(`frames`), une capture de l'image suivante lue par une vérification (`capture` ; l'image d'un échec est gardée dans
`captures/`), des nettoyages joués quoi qu'il arrive. La sortie de débogage du pilote (KHR_debug, synchrone) remplace
celle de vanilla le temps des tests (`ClientTestGlErrors`) : **toute erreur OpenGL pendant un test le fait échouer**,
avec la pile d'appels qui l'a provoquée ; la suppression du tampon nul compte aussi (`MixinClientTestGlStateManager` :
vanilla le vide d'abord sous Linux, une erreur que les autres pilotes taisent). Un pilote strict (Mesa, celui de la CI) signale ce qu'un pilote tolérant laisse
passer. `-PclientTestOnly=nom1,nom2` ne joue que ces tests ; les options du mod passent comme au client de
développement (`-PportalLod=false`...).

| Classe | Ce qui est vérifié |
|--------|--------------------|
| `LevelZeroClientTests` | Level 0 : look du profil dans les shaders (le bit du Level 0, pas seulement un look), néons envoyés, brouillard de la couleur exacte du profil, jumeau GLSL du calendrier des néons vérifié sur le processeur graphique contre le fichier de vecteurs communs (option `gtgsbackrooms.look.verifyNeonShader`, posée par `runClientTest`), graine du layout reçue (forme générée), bourdonnement des néons routé par le moteur audio (EFX prêt), image teintée du profil |
| `EngineRulesClientTests` | Règles des deux moteurs qu'aucune image ne montre à coup sûr, dans le Level 0 : seuil du bloom du profil dans l'uniforme du shader ; framebuffer recréé (redimensionné) avec son stencil pendant une image aux vues coupées (cohabitation avec un pack) ; deux dessins plein écran du même programme sur deux textures, son sampler sur la seconde ; volume posé sous une échelle lu sur la source OpenAL (0,5 × 0,5 = 0,25) ; même son arrêté puis rejoué, toujours traité et sur sa nouvelle source (le diagnostic du moteur audio marque « source périmée » un réglage resté sur l'ancienne) |
| `PortalViewClientTests` | Vue du Level 0 dans un cadre de l'Overworld (teinte jaune dans le cadre, rouge au moins égal au vert, ciel autour), puis aller et retour à pied **sans couture** : l'écart d'une image à la suivante, pour les deux images de la bascule, reste sous 1,5 fois le médian des six images voisines de chaque côté, plus 1 (mesure : 4,6 et 4,9/255 pour des voisines à 4,1) ; la marche continue douze images après la bascule, et l'image doit encore changer (un rendu figé échoue). Monde distant d'une vue libéré par le serveur une fois le portail retiré (`remoteWorldReleaseSeconds` à 5 s pour les tests du client), puis jeté par un changement de monde vanilla vers le Nether, vue ouverte : chaque fois, l'index des néons et la copie du monde pour le son l'oublient, et son renderer se ferme sans erreur OpenGL (tampons jamais remplis compris) |
| `PortalFeaturesClientTests` | Paliers de détail d'un cadre de 4×4 vu de 6, 30, 60 et 90 blocs (pleine qualité, vue allégée, image figée prise près du bord de la distance de rendu puis seule montrée au-delà) ; objet lancé à travers le cadre et repris de l'autre côté chez le client ; traversée en troisième personne (de dos, puis de face à reculons) ; clics à travers le cadre (bloc cassé, pierre posée dans le Level 0, bloc visé pris en main par le clic molette) ; écran de la baguette (chaque réglage changé une fois, sortie fixée puis oubliée, réglages reçus par le serveur comparés champ par champ) ; portail du Level 0 vers l'End vu à travers le cadre (vue imbriquée, troisième monde) et commande `/backrooms_state` ; cri qui perturbe les néons ; cri du Level 0 entendu à travers le cadre depuis l'Overworld ; occlusion d'un son du Level 0 entendu au cadre depuis l'Overworld (monde sans copie pour le son) : un cube de pierre posé dans l'Overworld aux coordonnées de la source ne compte pas (le trajet part du point du cadre où le son joue), un mur entre le cadre et l'auditeur compte |

Après le dernier test, le client quitte le monde comme « Sauvegarder et quitter », et la fin de partie compte comme un
test (`end_of_game`) : le serveur intégré s'arrête, puis plus rien de la partie n'est tenu (mondes distants, index des
néons et du son, fenêtres du thread de calcul du son), sans erreur OpenGL à la fermeture des renderers.

Un serveur intégré qui ne s'arrête pas après la sortie du monde fait échouer `end_of_game`, et un client qui ne se
ferme pas dans les trois minutes après le rapport fait échouer le lancement (« arrêt du client ») ; un test bloqué cinq
minutes arrête le client. Avant chacun de ces arrêts forcés, le journal reçoit la pile de chaque thread et, par
dimension du serveur intégré, les chunks à décharger et les tickets restants par type. Observé trois fois sur une
quarantaine de lancements le 29 octobre 2026 : le serveur tournait sans fin dans la boucle d'arrêt de vanilla
(`MinecraftServer.shutdown`, déchargement des chunks), sans code du mod dans sa pile. Cause non établie
(`docs/PORTALS.md`, limites connues).

En CI, le client tourne sous Xvfb avec le rendu logiciel de Mesa (`LIBGL_ALWAYS_SOFTWARE=1`, `.github/workflows/ci.yml`) :
ce chemin se vérifie par la CI.

## Conventions du code (Checkstyle)

`./gradlew checkstyleMain checkstyleTest checkstyleGametest` (dans `check`, donc dans `build`) applique
`config/checkstyle/checkstyle.xml` aux trois source sets : sources ASCII, imports ordonnés par groupes
(`java` → tiers → `net.minecraft` → `net.fabricmc` → `com.gtgpuke`) sans wildcard ni doublon, journal via
`ModConstants.LOGGER` uniquement, Javadoc sur chaque type public, classes utilitaires finales au constructeur privé,
mise en forme (accolades, espaces, une instruction par ligne, 180 colonnes). Zéro avertissement toléré (`maxWarnings = 0`).
Les deux exceptions assumées sont dans `config/checkstyle/suppressions.xml` : le logger est créé dans `ModConstants`,
le benchmark écrit ses chiffres sur la console. Rapports : `build/reports/checkstyle/{main,test,gametest}.html`.

---

## Contenu du JAR

`./gradlew verifyJarContents` (dans `check`) ouvre le JAR remappé et échoue s'il contient autre chose que le mod :
`fabric.mod.json`, `pack.mcmeta`, le fichier de mixins, le manifeste, la licence (`LICENSE_gtgs_backrooms`),
`assets/` et `data/` du mod, les tags `data/minecraft/tags/` et les classes `com/gtgpuke/gtgsbackrooms/` hors
`gametest/`. Une dépendance embarquée (classe tierce ou clé `jars` du `fabric.mod.json`) ou un test qui se glisserait
dans le JAR fait échouer le build.

---

## Benchmark

```bash
./gradlew benchmarkLevelZeroGeneration
./gradlew benchmarkLevelZeroGeneration -PlevelZeroPerfBudgetMsPerChunk=2.0
```

Mesure, sur 3 seeds et 169 chunks par seed (507 par passe) : le layout à secteurs froids (génération incluse), le
layout à secteurs chauds, et le coût complet layout + écriture des 256 colonnes x 5 layers. Le budget
optionnel porte sur ce dernier chiffre. Les chiffres de référence sont dans `docs/AUDIT.md`.

---

## Écrire un nouveau test

- Un comportement par méthode, un message d'assertion en français qui dit ce qui devrait être vrai.
- Pas de test par réflexion « le constructeur existe » pour une classe normale : tester ce que le code fait. Les
  classes utilitaires, elles, s'ajoutent à `UtilityClassesTest`.
- Chaque instruction et chaque branche nouvelles hors colle client doivent être prises par un test, sinon `build`
  échoue : chercher la ligne rouge ou jaune dans `build/reports/jacoco/full/html/index.html`
  et écrire le test qui la justifie, plutôt que d'exclure.
- Les tests de génération doivent rester déterministes : seeds fixes, zones fixes.
- Si une classe touche aux blocs ou aux registres : `MinecraftTestBootstrap.ensure()`.
