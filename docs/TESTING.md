# Tests — GTG's Backrooms

## Vue d'ensemble

| Niveau | Outil | Commande | Rapport |
|--------|-------|----------|---------|
| Unitaire | JUnit 5 sous le classloader Fabric (`fabric-loader-junit`) | `./gradlew test` | `build/reports/tests/test/index.html` |
| Couverture unitaire | JaCoCo | généré par `test` | `build/reports/jacoco/test/html/index.html` |
| En jeu | Fabric GameTest (serveur headless, sous agent JaCoCo) | `./gradlew runGameTest` | `build/junit.xml` + console |
| Couverture fusionnée et verrou | JaCoCo (unitaire + en jeu) : 100 % des instructions et des branches | `./gradlew jacocoCoverageVerification` (lancé par `build`) | `build/reports/jacoco/full/html/index.html` |
| Conventions du code | Checkstyle (`config/checkstyle/`) | `./gradlew checkstyleMain checkstyleTest checkstyleGametest` (lancés par `build`) | `build/reports/checkstyle/*.html` |
| Contenu du JAR | tâche `verifyJarContents` | `./gradlew verifyJarContents` (lancée par `build`) | console |
| Performance | Benchmark déterministe | `./gradlew benchmarkLevelZeroGeneration` | console |

Les échecs de tests font échouer le build, et la couverture aussi : voir Couverture.
`./gradlew build` enchaîne tout cela (`check`) : rien ne se construit sans ces vérifications.

---

## Couverture

`./gradlew jacocoCoverageVerification` fusionne l'exécution des tests unitaires (`build/jacoco/test.exec`) et celle des
tests en jeu (`build/jacoco/gametest.exec`, l'agent JaCoCo accompagne le serveur GameTest) et **échoue si une seule
instruction ou une seule branche du code mesuré n'a été prise par aucun des deux**. `./gradlew build` l'enchaîne
après `test` et `runGameTest`. Une branche qu'aucun cas réel ne prend (garde côté client d'un code qui ne tourne que
sur le serveur, condition déjà garantie par une autre) se retire ; une décision pure enfouie dans un code d'état
(échéance d'un journal, tirage pondéré) s'extrait en fonction testable, sans changer ce qu'elle décide.

Code mesuré : tout le mod sauf ce qui exige un contexte OpenGL ou un client, hors de portée de JUnit comme du serveur
GameTest, et les mixins (tissés dans les classes vanilla). La liste exacte est `coverageExcludes` dans `build.gradle` :

| Exclu | Pourquoi |
|-------|----------|
| `PortalStencilRenderer`, `FrameAreaRenderer`, `HandMask`, `PortalClipping`, `ViewLightmaps`, `portal/client/world/**` (`RemoteWorld`, `RemoteViewRenderer`, `ViewSectionWalker`, `RemoteParticles`, `RemoteWorldRegistry`, `RemoteSyncReceiver`, `ClientPortalIndex`, `ClientPortalLight`, `PortalSightReporter`, `StraddlingEntityRenderer`) | appels GL, `WorldRenderer`, `MinecraftClient` |
| `ShaderPackPortalRenderer`, `compat/**` (`IrisCompat`, `IrisApiBridge`) | appels GL et rendu complet d'un `WorldRenderer` ; colle vers le mod de shaders Iris, absent des tests (`IrisCompatTest` vérifie seulement que rien ne manque sans lui) |
| `portal/client/transition/**`, `portal/client/interaction/**`, `portal/client/sound/FrameSoundPlayer`, `portal/client/sound/SoundAnchors`, `portal/client/entity/**`, `portal/client/wand/**` | bascule de monde client, visée et clics du client, son rejoué par le moteur de son du client et sources OpenAL replacées après une traversée, renderer d'entité, écran de la baguette de portail (widgets du client ; ce qu'il envoie, le payload et sa réception, est testé) |
| `level0/client/**`, `core/perf/client/**`, `GtgsBackroomsClientMod` | modèles, hooks client, point d'entrée client |
| `mixin/**` | mesurés à travers les classes vanilla qu'ils modifient |

La logique client pure reste mesurée et testée : `PortalViewTransform` (plan de découpe, caméra transposée), `ViewCone`
(volume visible à travers le cadre), `ShaderClipInjector` (contre 54 des 55 vertex shaders vanilla du jar du jeu,
`blit_screen` exclu), `PortalRenderState`, `ThirdPersonPortal` (recul de la caméra à travers le cadre), `PortalSound`
(portée et volume du son à travers le cadre, meilleur cadre ouvert et visible vers le monde du son). Le rendu exclu se valide en jeu (`docs/PORTALS.md`, section Tests).
La colle OpenAL du système audio à venir (`docs/GUIDE.md`, section 5.3) sera exemptée de la même façon, nominativement
dans `coverageExcludes` ; sa logique (routage par source, filtres, paramètres de réverbération) reste mesurée.

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
| Client (logique pure) | `PortalViewTransformTest`, `CrossingRollTest`, `ViewConeTest`, `ShaderClipInjectorTest`, `PortalRenderStateTest`, `RemoteViewRendererTest`, `ThirdPersonPortalTest`, `LazyVertexBuffersTest`, `ObliqueProjectionTest`, `ViewRenderStartTest`, `IrisCompatTest` | plan de découpe en espace caméra, caméra et regard transposés, roulis de la caméra du joueur à la traversée (un quart de tour d'un sol à un mur, regard de côté ; un demi-tour par un cadre d'arrivée retourné ; aucun entre cadres debout, en travers de l'axe ou du sol au plafond ; redressement en cube du temps restant, en moins d'une demi-seconde ; glissement de l'image de l'œil vers l'œil, rien pour un écart négligeable), rotation d'un portail sur les 144 paires et roulis de la caméra d'une vue (d'un sol à un mur, puis retour au haut du joueur ; aucun entre deux cadres debout), rotation recopiée, volume visible à travers le cadre (et sans limite), volume entre la caméra et le cadre (bloc coupé au plan du cadre pour les six orientations : un mur qui borde le cadre ne compte pas), recul de la caméra à la troisième personne à travers le cadre ou au-dessus du linteau, injection du plan dans 54 des 55 vertex shaders vanilla (`blit_screen` exclu), garde de rendu et numéro d'image, coupure du rendu distant, ancrage de la grille de sections, drapeau des objets GL différés des renderers de vue, projection à plan proche oblique (point du plan à -1, devant coupé, coin lointain gardé, écran inchangé, caméra déjà du bon côté : projection telle quelle), paramètres et drapeau du début de rendu à blanc d'un renderer de vue, détection du mod de shaders Iris (absent des tests : rien de chargé, aucun pack actif), quadrilatères rognés au plan sur le processeur (`ClippedVertexConsumersTest` : entier, rien, moitié avec attributs interpolés, pentagone en deux quadrilatères, triangle, format inconnu transmis tel quel) |
| Portails | `PortalFacingTest`, `BackroomsPortalEntityTest`, `BackroomsPortalBlockTest`, `PortalSettingsTest`, `PortalWayBackTest`, `PortalWandItemTest`, `PortalTargetsTest`, `LevelZeroArrivalTest`, `VirtualLightSourcesTest`, `PortalRelevanceTest`, `TeleportExecutorTest`, `PortalCollisionTest`, `ArrivalGuardTest`, `PortalPayloadsTest`, `PortalViewTrackerTest`, `RemoteChunkWindowTest`, `RemoteEntityViewTest`, `PortalSoundTest`, `PortalPursuitTest` | rotation sans miroir sur les 144 paires des douze orientations (dont les retournées, tête en bas), du sol au plafond une translation (cap et vitesse gardés), cadre couché en face de son ouverture dans ses deux axes, traversée d'un cadre couché aux yeux, regard devenu vertical qui garde le haut de l'image, point de sortie qui garde la place du point de traversée puis le corps du côté de la sortie et dans l'ouverture (debout, tête en bas comprise), chute gardée seulement si le bas reste le bas, sortie imposée qui ne tourne pas, faces arrière qui transposent comme les faces avant, NBT, bloc invisible visé seulement accroupi, réglages (format, dont pose, destination et sortie fixée sauvées et relues, illisibles oubliées, bornes, piles, colonnes, centre et pied du cadre, debout ou couché, plan au milieu ou au bord du bloc, bloc ancre retrouvé depuis le seul centre, sens de sortie tout droit, cardinal, par le sol ou le plafond, écart tiré au sort nul sans rayon et borné par lui), cadre d'arrivée qui tient sur toute sa largeur avec ses deux faces libres, sources de lumière immuables, traversée du plan (le centre en face de l'ouverture, la boîte touchant le cadre ; ni apparition ni saut de position), face franchie d'après le sens du déplacement, boîtes à cheval sur le cadre d'entrée ou d'arrivée à la position affichée, boîtes refaites quand la taille change, côté de sortie et image d'une boîte d'un portail à l'autre, entité engagée dans un cadre, blocs cachés derrière le plan dans l'ouverture prolongée (douze orientations), paroi du plan pour qui touche un bord du côté d'entrée seulement, ouverture fermée (paroi d'un bord, sol d'une garde), portails et côté venus des mondes branchés, treize payloads dont un paquet d'entité vanilla enveloppé et la libération d'une dimension, les portails vus et les réglages de la baguette, info-bulle de la baguette et traduction de chaque clé qu'elle et son écran emploient, retour lu dans les réglages (`PortalWayBack`), paquets relayés aux joueurs qui ont une dimension à portée, fenêtre de chunks et sa boîte d'entités ; pertinence d'un portail : à portée quelle que soit la direction du regard pour la synchronisation, devant le joueur ou à portée d'activation pour le rendu ; volume du son après le trajet et sortie par le cadre où le joueur voit l'autre côté, point de passage du regard dans le cadre, point d'approche et alignement sur l'ouverture, cooldown des joueurs ; bords d'un cadre pour un joueur : quatre parois d'un demi-bloc autour de l'ouverture, coins compris, du plan à un demi-bloc derrière ; traversée d'un joueur aux yeux, dans la hauteur du cadre (au-dessus du haut, pas de traversée ; un objet passe à toute hauteur des pieds), et de même face à l'ouverture d'arrivée |

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
donné) puis guettent ses chunks au lieu de compter les ticks. Une tâche `createAndAdd` est rejouée à chaque tick tant qu'elle
lève, jusqu'au délai du test : elle sert à attendre une condition. Une vérification définitive va dans `createAndAddReported`,
jouée une fois, qui fait échouer le test aussitôt ; son nettoyage se fait en `try/finally`, et ce qu'une attente expirée
laisserait derrière elle, dans un `@AfterBatch` du lot.

L'arborescence suit celle du code : `support/` (joueurs simulés, monde prêté), `backrooms/level0/`, `core/portal/`.

| Classe | Ce qui est vérifié |
|--------|--------------------|
| `backrooms/level0/LevelZeroGameTests` | JSON de la dimension décodé et réencodé avec les registres du serveur, chunk d'origine généré : sol, air, plafond, bedrock sur les cinq layers ; mur adaptatif posé (masque recalculé ou déjà juste) |
| `core/portal/BackroomsPortalBlockGameTests` | pose vers une zone jamais générée (garde-fou G4) : au tick de la pose le bloc attend le premier chunk de sa recherche par un ticket sans rien générer ; au tick suivant ce chunk a son niveau de ticket (`isChunkLoaded` vrai) sans être entier, et la recherche l'attend sans le générer elle-même ; puis le bloc résout la même arrivée que la recherche synchrone ; copie d'un bloc (clone, structure) : destination gardée, portails propres, ceux de l'original intacts ; réglages seuls envoyés aux clients ; sens de sortie imposé : cadre d'arrivée tourné vers l'est, à l'aplomb du bloc ; sortie par le plafond : cadre d'arrivée couché face au bas, relevé de deux blocs ; pose couchée (regard vers le bas : au sol, même sur un sol plein, vers le haut : au plafond, debout sans joueur) et plan visable d'un cadre couché ; arrivée tirée au sort : aux X/Z tirés dans le rayon, sans tourner ; relu avec un autre sens de sortie, le cadre d'arrivée tourne sur son pied, entrée et retour reposés ; couché avant ou après, le bloc retire ses portails et cherche de nouveau son arrivée ; une face : rien de dos, on passe au travers sans traverser ; aller simple : rien à l'arrivée, retours retirés à la relecture (dimension absente comprise) ; sortie fugace : jamais vue, elle attend un regard, même avec un joueur tout près ; vue, elle tient sous le regard d'un joueur, même de loin, et disparaît après une demi-seconde sans regard, même tout près (un rapport vide isolé ne la perd pas ; sauvée, jamais reposée par les vérifications des retours, neuve pour une copie du bloc ; un retour qui devient fugace à la relecture est reposé, l'entrée gardée) ; rouverte aussitôt par le prochain joueur qui passe l'entrée, à une face comme le bloc, puis de nouveau une seconde pour se retourner et le regard seul ; ni un objet, ni un spectateur, ni un portail qui n'est pas l'une des entrées du bloc ne la rouvrent, et une sortie encore là n'est pas reposée, un rapport fait dans un autre monde ignoré, un spectateur qui la regarde ou un joueur sans rapport ne la gardant pas, un rapport de plus de 40 ticks ne comptant plus ; seul le joueur qui la voit la traverse (un portail libre marqué fugace, jamais), et qui sort par elle a une seconde pour se retourner, pour elle seule et pas en repassant par elle, oubliée (avec son dernier rapport) au changement de monde vanilla et au respawn ; sans rien casser vers une dimension absente ; portail que son bloc ne tient plus pour sien retiré par le registre ; quatre portails (deux faces dos à dos de chaque côté), retours inverses exacts, même transformation par les deux faces, taille et lumière réglées ; bloc tourné ou relu avec une autre largeur, lumière, destination, dimension ou hauteur → portails reposés, relu au bord du bloc → plan d'arrivée reculé d'un demi-bloc sur son pied, relu tête en bas → cadre d'arrivée retourné sur place ; cassé → plus rien (retours compris), y compris sans block entity ; traversée par la face arrière ; pose face au joueur avec les réglages de l'item, cadre bloqué refusé, visé seulement accroupi ; cibles par dimension, sans destination ou vers une dimension absente, face perdue attendue puis reposée, retour disparu ou jamais posé, Level 0 prêté au serveur (retour suivi puis retiré avec le bloc), bloc inerte quand les portails sont désactivés ; arrivée posée sur le haut réel du sol (dalle, tapis, eau) |
| `core/portal/PortalLightGameTests` | lumière des blocs passée d'un niveau de moins par un portail ouvert, visible et réglé pour elle, la plus forte de deux l'emportant, rien par un portail fermé, invisible, réglé sans lumière, sans arrivée ou vers le noir ; source publiée mais pas encore appliquée signalée de nouveau ; cadre d'un portail qui apparaît revérifié (lumière fantôme éteinte) ; portail double face 4 x 4 éclairé des deux côtés puis éteint avec sa lumière ; plan au bord du bloc : le bloc devant son plan éclairé (chaque test dans son lot, loin du coin éclairé de l'arène) |
| `core/portal/PortalWandGameTests` | réglages de la baguette reçus pour la seule main qui la tient ; pose d'un bloc portail réglé, son d'un portail qui s'ouvre entendu par un témoin, évènement de pose entendu par un capteur sculk (lot à part, nettoyé par `@AfterBatch`), baguette gardée en survie, refus rendu en échec (cadre bloqué, place prise par un bloc plein, hors du monde) ; copie des réglages d'un portail visé, message au joueur, sans joueur aussi ; bloc cassé qui ne rend rien ; plan au bord du bloc : cadre debout posé contre un mur, centre sur la face arrière, plaque visée accroupi contre elle (au milieu là où n'est aucune block entity), cadre couché à ras d'un sol plein, arrivée en surface décalée d'un demi-bloc |
| `core/portal/PortalGameTests` | traversée d'un joueur dans le même monde (position, regard) ; objet lancé ; saut de moins de huit blocs envoyé en position absolue, marqueur sans suivi ; collision à travers le cadre (un objet poussé de biais bute sur le mur de l'autre côté, au milieu il passe ; rien derrière le plan, portail fermé, dimension absente, deux cadres engagés, joueur serveur ou portail d'un autre monde ignorés) ; bord du cadre (le centre hors de l'ouverture, l'objet bute sur le plan même sans rien de l'autre côté ; tout à côté, de dos ou portail fermé, rien) ; payload de chunk |
| `core/portal/PortalSyncGameTests` |  fenêtre de chunks envoyée à un joueur à portée d'un portail vers le Nether (foyer le plus proche, fenêtres partagées et fusionnées), entité distante retirée de son monde effacée chez lui au même tick, chunks tenus après une traversée valables 100 ticks, annonce de traversée refusée sous cooldown ou à la vitesse impossible, météo du Nether envoyée avec elle puis à chaque changement seulement, entités suivies, entité arrivée par un portail envoyée au même tick, blocs relayés, fenêtre gardée après un demi-tour à portée de rendu (trente blocs, passé le délai de grâce), déchargement une fois hors de portée ; dimension sans portail à portée : fenêtre déchargée après le délai de grâce, monde distant libéré chez le client après `remoteWorldReleaseSeconds` (une fois), puis annoncée de nouveau sous le même index et fenêtre renvoyée au retour du joueur ; traversée vers le Nether sans renvoi des chunks tenus qui n'ont pas changé (un témoin à l'arrivée reçoit de vanilla chaque bloc signalé, même revenu à l'identique dans le même tick : seul un chunk signalé depuis le passage repart, et vanilla repropose des chunks tenus sans les renvoyer) ; côté quitté gardé chargé après le départ du joueur, ses entités restant les mêmes (loin des chunks forcés de l'arène) ; annonce client vraisemblable, invraisemblable, sous cooldown, refusée (portail fermé, sans dimension, hors du cadre, en bateau) ; marche détectée par le serveur et remontée hors d'un sol irrégulier, franchissement abandonné si le portail disparaît, se ferme ou ne mène nulle part, joueur qui saute de position (un joueur en bateau, lui, traverse avec son bateau, bascule menée par le serveur) ; entités qui ne traversent pas (cooldown, bateau et passager déplacés par sauts de position, mort) ; changements de monde vanilla ; portails orphelins et chargement double ; commande de debug ; ticket élargi à l'approche ; portail invisible traversé sans vue ; chunk tenu qui change autour d'une traversée renvoyé par vanilla ; chunks tenus propres à chaque joueur et à leur dimension (un changement aux mêmes coordonnées d'une autre dimension n'en libère aucun) ; joueur spectateur montré par le portail aux seuls spectateurs ; texte d'un panneau écrit de l'autre côté et fissures de l'autre côté relayés ; vue d'un joueur qui vient de passer repartie au tick suivant ; deux dimensions dans une même vue (Nether et End) annoncées sous les index 0 et 1, que portent leurs blocs relayés, leur météo et leurs déchargements |
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
