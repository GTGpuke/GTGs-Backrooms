# CLAUDE.md — GTG's Backrooms

> Lu automatiquement à chaque session. Règles obligatoires du projet.
> Index de la doc : `docs/INDEX.md` — Guide du projet : `docs/GUIDE.md` (règles d'or, principes, suite du travail) — Décisions de l'auteur : `docs/DECISIONS.md`
> Onboarding complet : `.prompts/NOKNOWLEDGE.md` — Audit pré-push : `.prompts/BEFOREPUSH.md` — Vérification finale avant commit : `.prompts/FINALCHECK.md`

## Stack

Minecraft **1.21.1** / Yarn `1.21.1+build.3` / Fabric Loader **0.19.5** / Fabric API **0.116.17** / Fabric Loom **1.17.21** / Gradle **9.8.1** (wrapper) / Java **21** (toolchain et JVM Gradle)
`mod_id` : `gtgs_backrooms` — package racine : `com.gtgpuke.gtgsbackrooms`

## Commandes

```bash
./gradlew test                          # JUnit 5 sous le classloader Fabric + rapport JaCoCo (build/reports/jacoco)
./gradlew runGameTest                   # Tests en jeu (serveur headless, sous agent JaCoCo) : Level 0, portails, contenu
./gradlew jacocoCoverageVerification    # Couverture fusionnee unitaire + en jeu : echoue sous 100 % des instructions ou des branches (lance par build)
./gradlew checkstyleMain checkstyleTest checkstyleGametest   # Conventions du code (config/checkstyle), lances par build
./gradlew verifyJarContents             # Le JAR ne contient que le mod : ni dependance embarquee, ni test (lance par build)
./gradlew benchmarkLevelZeroGeneration  # Benchmark déterministe layout + écriture (option -PlevelZeroPerfBudgetMsPerChunk=X)
./gradlew runClient                     # Client de dev ; toggles : -PdebugPerformanceMonitor=true -PportalFeature=false -PportalDebugRendering=true ...
./gradlew runClient -PquickPlayWorld=MonMonde    # Idem, en chargeant directement un monde solo (nom avec espace sous Git Bash : ORG_GRADLE_PROJECT_quickPlayWorld="New World" ./gradlew runClient)
./gradlew build                         # JAR dans build/libs/, apres check : Checkstyle, tests, tests en jeu, couverture, contenu du JAR
```

Un seul JDK suffit : Java 21. Si `JAVA_HOME` pointe sur un autre JDK, Gradle détecte le 21 installé (toolchain).

## Structure (voir docs/systemes/ARCHITECTURE.md)

Quatre paquets sous la racine (à côté des deux points d'entrée) : `core/` (le socle), `backrooms/` (le contenu),
`compat/`, `mixin/`. Où ranger un nouvel objet, un son, un payload : tableau « Où ranger quoi » de `docs/systemes/ARCHITECTURE.md`.

- `core/` : `ModConstants`, `ModConfig` (options lues en `-D`) et `registry/` : registres (`Registry.register` dans des
  champs `static final`), un par type (blocs, items, block entities, entities, composants, sons, onglet créatif,
  payloads). Le bloc portail n'a pas d'item : la baguette le pose.
- `core/portal/` : portails (bloc portail double face et ses réglages, plan au milieu ou au bord du bloc, baguette (`item/`), seule façon d'en créer un, debout, au sol ou au plafond, entity, téléportation de tout ce qui traverse, attelages compris, collision à travers le cadre, blocs derrière le plan cachés à qui s'engage dans le cadre (cadre contre un mur, trappe au sol), garde d'une arrivée au sol, arrivée tête en bas, synchronisation des chunks et des entités, rendu stencil avec un niveau d'imbrication et la météo de l'autre côté, paliers de détail des vues lointaines (vue allégée, terrain simplifié, image figée jusqu'à 128 blocs) et fenêtre en cône de loin, interaction à travers le cadre, sons, particules et regard des mobs qui le traversent), actifs par défaut (`ModConfig.ENABLE_PORTAL_FEATURE`). Lire `docs/systemes/PORTAILS.md` avant d'y toucher.
- `core/perf/` : moniteur de performance opt-in.
- `core/audio/` : moteur audio maison (`docs/systemes/AUDIO.md`) : profil acoustique par dimension (`profile/`, `assets/gtgs_backrooms/audio_profiles/`), dimension d'émission, occlusion, rayons, forme générée d'un niveau et réglages de réverb (`acoustic/`), liaison son-image (`cue/` : horloge commune, enveloppes, perturbations, plafond de clignotement, `assets/gtgs_backrooms/sound_cues/`), colle OpenAL EFX, routage des sons et bourdonnement des néons (`client/`). La forme générée du Level 0 vient du contenu (`backrooms/level0/LevelZeroRoomShape`, graine du layout envoyée par `LevelZeroLayoutSync`).
- `core/render/` : moteur de shaders maison (`docs/systemes/RENDU.md`) : shaders vanilla patchés au chargement par enveloppe de `main` (`patch/`, patchs déclarés dans `assets/gtgs_backrooms/shader_patches/`), profil de look par dimension (`profile/`, `assets/gtgs_backrooms/look_profiles/`), pile des looks par vue (`state/`), courbe de lightmap, brouillard et néons du profil (`light/`, calendrier `NeonSchedule` et son jumeau GLSL `shaders/include/neon_schedule.glsl`, tenus d'accord par `src/test/resources/neon_schedule_vectors.json`), bits de look du stencil (`post/`), colle GL (`client/`).
- `backrooms/level0/generation/` : `coord` → `noise` → `sector` → `layout` → `write`, orchestrés par `LevelZeroChunkGenerator`.
- `backrooms/level0/client/model/` : rendu adaptatif du mur (client uniquement).
- `backrooms/level0/LevelZeroArrival` : où un portail arrive dans le Level 0 ; seul lien, avec les registres, du socle vers le contenu.
- `compat/` : modules de compatibilité, seuls endroits du code qui nomment un autre mod et en lisent l'API (décisions
  de l'auteur des 28 et 29 octobre 2026). `compat/iris/` : dans une dimension à look, le rendu du mod remplace le pack de
  shaders actif (pipeline vanilla d'Iris tant que le joueur y est, réglages globaux de ses pipelines gardés et reposés,
  terrain reconstruit à la bascule) ; ses mixins (`gtgs_backrooms.compat.mixins.json`) ne s'appliquent que si Iris est
  là, dans la plage vérifiée (`CompatMixinPlugin`, `IrisVersion`). Iris et Sodium en `modCompileOnly` (maven Modrinth),
  jamais embarqués ni requis.
- Hors de `compat/`, aucun autre mod nommé (sauf ses messages, clés de langue `compat.*`, et la doc qui décrit son
  module) et aucune dépendance de mod à l'exécution hors Fabric Loader et Fabric API ; un pack de shaders se reconnaît
  au milieu du rendu du joueur (`ShaderPackState`), un mod qui dessine le terrain à sa grille vanilla vide
  (`RemoteViewRenderer.foreignTerrain`). Le terme d'IA n'apparaît pas dans le mod.
- `mixin/` : commun (relais des changements de blocs, chunks tenus non renvoyés, sons et particules relayés, collision à travers le cadre, blocs cachés derrière le plan (collisions, étouffement), regard et poursuite des mobs, sources de lumière virtuelles des portails, accesseurs du suivi vanilla des entities et de la météo brute d'un monde) et `client/` (rendu des portails, découpe dans les shaders, monde distant, entités à cheval sur un portail, copies d'entités périmées remplacées, monde de création du joueur suivi aux traversées, générations des sections de terrain (preuve d'occlusion des vues), météo et particules coupées dans une vue allégée, visée et clics à travers le cadre, sons replacés après une traversée, roulis de la caméra à la traversée, voile de mur levé dans un bloc caché, main gardée des vues sous un pack).
- `src/gametest/` : tests en jeu, mod séparé `gtgs_backrooms_gametest` chargé par `runGameTest` seulement (jamais dans le JAR, `verifyJarContents` le vérifie).

## Règles de code

- Commentaires, logs, messages d'erreur : **en français**, majuscule initiale, point final. Pas d'accents dans le code (sources ASCII).
- Contenu visible en jeu : `Text.translatable()` + clés dans `en_us.json` **et** `fr_fr.json` (test `ModRegistriesTest` le vérifie).
- Imports : `java` → bibliothèques tierces (`com.mojang`, `com.llamalad7` (MixinExtras), `org.*`) → `net.minecraft` → `net.fabricmc` → `com.gtgpuke`, aucun wildcard.
- Logs via `ModConstants.LOGGER` uniquement ; aucun `System.out` hors benchmark.
- Aucune allocation dans les méthodes tick/render ; pas de concaténation de chaînes dans les chemins chauds (y compris les noms de sections perf).
- Pas de couche d'abstraction sans deuxième implémentation réelle : pas d'interface/stage/builder spéculatif.
- Pas de dossier vide ni de `.gitkeep`, pas de `package-info.java` d'une ligne.
- Licence propriétaire, tous droits réservés (`LICENSE`, copiée dans les JAR) : aucun code ni aucune ressource repris
  d'un projet tiers, quelle que soit sa licence.

## Guide et nouveaux systèmes (rendu, audio)

- Lire `docs/GUIDE.md` au début de chaque session ; ses règles d'or s'appliquent, et un écart entre le guide et le code
  se consigne dans `docs/DECISIONS.md` au lieu de forcer le code à suivre le guide.
- Le système de portails (`core/portal/`) est la base : l'étendre par des points d'accès (méthodes publiques,
  évènements), pas par des couches d'abstraction, et ne pas en réécrire la logique. Toute refonte est proposée et validée
  avant.
- Un profil par monde, donnée, pas interface (`LookProfile` pour le rendu, `AudioProfile` pour l'audio, deux fichiers
  liés par la dimension) : aucun test sur la dimension du joueur pour décider d'un comportement de rendu ou d'audio ;
  le rendu d'un pixel dépend de la dimension qu'il montre, le traitement d'un son de la dimension où il est émis.
- `docs/interne/` : documents de conception de l'auteur (moteurs, qualité et presets, gameplay, production), source de
  vérité, sauf là où ils reprennent un texte plus ancien qu'une décision de `docs/DECISIONS.md`, qui prime alors. Ils
  ne s'indexent jamais sans l'accord de l'auteur, et aucun autre fichier ne cite les projets de référence
  qu'ils nomment (code, commentaires, JAR, interface, crédits, doc publique), modules `compat/` mis à part.
- Les deux moteurs suivent `docs/interne/MOTEUR_SHADERS.md`, `docs/interne/MOTEUR_AUDIO.md` et
  `docs/interne/QUALITE_ET_PRESETS.md`, et les décisions de l'auteur (`docs/DECISIONS.md`). Le rendu
  tient en deux étages dans le stencil existant (`PortalStencilRenderer`) : injection par fragment dans les shaders
  vanilla (`core/render/patch/`, même famille que `ShaderClipInjector`) et post masqué par les bits de look du stencil ;
  pas de G-buffer ni de rendu différé. Pas de framebuffer de vue séparé hors du chemin sous pack de shaders
  (`ShaderPackPortalRenderer`) et de l'image figée d'une vue lointaine (`FrozenViews`, écart accordé le 26 octobre 2026).
  Iris et Sodium restent supportés (décision de l'auteur) ; dans les Backrooms, le rendu du mod remplace le pack
  (`compat/iris`, livré le 29 octobre 2026).
- L'audio étend `FrameSoundPlayer`, `PortalSound` et le relais serveur existants.
- Sessions d'implémentation : aucun code source d'un autre mod dans le contexte ; les notes de conception
  `docs/interne/` sont la seule source autorisée. Lire l'API d'un mod tiers (ses signatures, jamais son code source)
  n'est permis que pour écrire un module `compat/`.
- Si tu te souviens d'une implémentation existante d'une fonctionnalité, n'en reprends que le principe ; réécris à partir
  de notre architecture (profils par monde, passes par niveau de stencil, routage par source).
- Chaque fonctionnalité a une définition de « terminé » (`docs/GUIDE.md`, section « Définition de « terminé » ») et n'est livrée qu'une fois vérifiée
  en jeu et le build complet vert.
- Nommage : vocabulaire du glossaire (`docs/GUIDE.md`, section « Glossaire ») pour les concepts nouveaux ; les termes déjà en place
  dans `portal/` sont conservés.
- Colle OpenGL et OpenAL : minimale, isolée dans `*/client/`, nommée dans `docs/dev/TESTS.md` ; tout le reste est couvert à
  100 %.
- Chaque système a son document dans `docs/systemes/` (`PORTAILS.md`, `RENDU.md`, `AUDIO.md`, `LEVEL0.md`) : en jeu,
  fonctionnement, réglages (`docs/reference/OPTIONS.md`), limites, tests.

## Génération du Level 0 : règle d'or

La génération est **déterministe et verrouillée** par `LevelZeroGoldenTest` (empreintes par seed et layer).
Toute modification qui change ces empreintes modifie les mondes existants : elle doit être voulue,
annoncée dans `CHANGELOG.md`, et les empreintes doivent être régénérées (relancer le test, copier les valeurs).
Les sels de `StageRandom.Stage` et l'algorithme de `LevelZeroSectorGenerator` ne se modifient jamais à la légère.

## Pièges Fabric / Yarn 1.21.1

- `Identifier.of(ns, path)` ; `Item.Settings` ; `.maxCount()` ; `hasGlint()` ; `finishUsing()` ; `TypedActionResult` ; `World`/`ServerWorld`/`PlayerEntity`.
- `dirt_provider` obligatoire dans un `minecraft:tree` configured_feature.
- `-Dfile.encoding=COMPAT` dans `org.gradle.jvmargs` reste nécessaire sur Windows avec un chemin accentué.
- Les `assets/<ns>/items/*.json` n'existent qu'à partir de 1.21.4 : en 1.21.1 seuls `models/item/*.json` comptent.
- Tests : `MinecraftTestBootstrap.ensure()` dans `@BeforeAll` pour tout ce qui touche blocs, items ou registres ; inutile pour la logique pure.
- `ItemPlacementContext.getBlockPos()` rend la place où l'on poserait (devant le bloc visé s'il n'est pas remplaçable), pas
  le bloc visé : en jeu, `Item.useOnBlock` reçoit un `ItemUsageContext`, dont la position est le bloc visé. Un test qui
  simule un clic sur un bloc sans joueur passe un `ItemUsageContext` (constructeur protégé : sous-classe anonyme). Un bloc
  visé hors du monde, vide et remplaçable, reçoit la pose à sa place, où `setBlockState` échoue.
- `ServerPlayerEntity.sendMessage(texte, true)` (barre d'action) part en `GameMessageS2CPacket` marqué `overlay`, pas en
  `OverlayMessageS2CPacket` (celui de `/title actionbar`). Un bloc sans item se déclare `dropsNothing()` (aucune loot table
  lue) ; un item retiré du registre disparaît des inventaires au chargement (« Tried to load invalid item » dans le journal).
- `DataTracker` n'a pas de handler `double` : une coordonnée précise se stocke en `LONG` (`Double.doubleToRawLongBits`), un `FLOAT` perd le centimètre loin de l'origine (le serveur GameTest tourne vers x = -1.4e7).
- `World.getTopY` renvoie `getBottomY()` si le chunk n'est pas chargé : charger le chunk avant de lire une hauteur de surface.
- `ChunkPos` n'a pas de `PACKET_CODEC` en 1.21.1 : `PacketCodecs.VAR_LONG.xmap(ChunkPos::new, ChunkPos::toLong)`.
- `Frustum` exige une projection dont la sous-matrice 3x3 est orthogonale : `coverBoxAroundSetPosition` recule le frustum le long de la troisième ligne et boucle sans fin avec un plan proche oblique (gel du client). Les vues de portail ne donnent jamais de projection oblique au `Frustum` : le culling garde la projection du joueur, et le dessin aussi, le plan du portail d'arrivée se coupant par `gl_ClipDistance` dans les shaders (`ShaderClipInjector`, `PortalClipping`), sauf quand un autre mod dessine le terrain ou sous un pack de shaders, où le dessin seul passe par `ObliqueProjection` (voir plus bas).
- Le culling vanilla (`ChunkRenderingDataPreparer`) est asynchrone et tient un seul graphe par `WorldRenderer` : deux caméras sur un même renderer dans une image alternent des listes de sections incomplètes (flicker). Les vues de portail choisissent leurs sections elles-mêmes (`ViewSectionWalker`) et n'appellent `setupTerrain` qu'une fois par renderer et par image.
- `Entity.setPos` prévient le gestionnaire d'entities : déplacer temporairement un joueur vers une zone non chargée de son monde le sort de sa section et de son suivi. Couper l'écouteur (`setChangeListener(EntityChangeListener.NONE)`) le temps de l'appel, puis le rétablir après avoir remis la position.
- `ClientWorld.getEntities()` du monde principal est la seule liste que vanilla tique : un `ClientWorld` secondaire doit appeler lui-même `tickEntities()` ; ses sections ne tiquent qu'après `loadChunkFromPacket` (`resetChunkColor` → `startTicking`).
- Les récepteurs `ClientPlayNetworking` de Fabric s'exécutent déjà sur le thread de rendu, dans l'ordre des paquets. Ne pas les envelopper dans `client.execute(...)` : appelé depuis une tâche en cours, `ReentrantThreadExecutor` le remet en fin de file, derrière les paquets vanilla déjà reçus (un chunk du nouveau monde appliqué à l'ancien → `IndexOutOfBoundsException`, déconnexion).
- Tests en jeu : le serveur GameTest n'a ni Level 0 ni client ; le Nether sert de dimension distante, les joueurs sont connectés
  par `GameTestPlayers` (vrai `onPlayerConnect` sur canal en mémoire, `setOnGround(true)` sinon le minage est cinq fois plus lent),
  les payloads client s'injectent par `onCustomPayload`, et les tests qui posent quoi que ce soit utilisent l'arène
  `gtgs_backrooms_gametest:test_arena` (24 blocs) : la structure vide de Fabric fait un bloc et les voisins se marchent dessus.
  Le dossier de run `build/run/gameTest` n'est vidé que par `clean` (un build complet repart d'un monde neuf) et le serveur de test tique sans attendre : générer
  l'arrivée d'avance (`GameTestServerWorlds.pregenerate`) et guetter les chunks du Nether (`PortalViewTracker.watchedChunkCount() > 0`) plutôt que compter les ticks. Un bateau embarque de lui-même les créatures qui le touchent.
  Un joueur simulé ne suit une entité vanilla qu'une fois ses chunks reçus (`GameTestPlayers.tick`) et sa position
  redonnée (`getChunkManager().updatePosition`, ce que fait un vrai client en bougeant) ; l'apparition arrive groupée
  dans un `BundleS2CPacket`.
- Tests en jeu : les arènes ont un plafond de barrières que la heightmap voit (`getTopY` y renvoie le plafond), un chunk forcé
  ne suit ses entités qu'après quelques ticks (attendre `shouldTickEntity` avant d'y faire arriver une entité), et le dossier de
  run n'est vidé que par `clean` : une entité sauvée par un lancement précédent peut s'y trouver. Une entité qui change de
  monde garde son UUID : la retrouver par `getEntity(uuid)` plutôt que par une recherche dans une boîte. Les messages des
  échecs sont dans `build/junit.xml`, pas dans le journal. `TimedTaskRunner.createAndAdd` rejoue sa tâche à chaque tick
  tant qu'elle lève (une attente) ; une vérification définitive va dans `createAndAddReported` (jouée une fois, échec
  immédiat), nettoyage en `try/finally`, et ce qu'une attente expirée laisserait dans un `@AfterBatch`. Un échec de
  `createAndAddReported` n'arrête pas la suite : les tâches suivantes s'enchaînent (une attente réussie passe à la
  suivante dans le même tick, une tâche rapportée aussi) et le message gardé dans `build/junit.xml` est celui de la
  dernière vérification ratée, pas de la première. Entre une action (un corps poussé à travers un plan) et ce que le
  serveur en juge en fin de tick (sa traversée), placer une attente, sinon l'action suivante l'efface avant le jugement.
- Mixins : `@Shadow` ne voit que les membres déclarés dans la classe cible, pas ceux hérités (`ClientPlayNetworkHandler.client`
  est déclaré dans `ClientCommonNetworkHandler`) ; l'erreur ne sort qu'au chargement de la cible, donc au lancement du client
  pour un mixin client. `MixinTargetsTest` charge la cible de chaque mixin déclaré et fait échouer le build à la place. Aucune
  classe de test dans le paquet `mixin` : Mixin refuse de charger les classes de son paquet.
- Mixins : aucun `@Redirect`. Compilé contre le Mixin de Loader 0.19.5, son point d'injection est un tableau, que le
  MixinExtras 0.5.4 de Loader 0.19.2 lit comme un seul élément : la cible n'est pas transformée et le jeu plante au
  démarrage (`ClassCastException`), même sans autre mod. `@WrapOperation` fait le même travail, mais son appel
  d'origine reçoit ses arguments dans un tableau alloué : dans un chemin de rendu ou de tick, `@ModifyExpressionValue`
  (ou `@ModifyArg` pour remplacer un argument, un prédicat par exemple) quand l'appel d'origine peut s'exécuter. `MixinTargetsTest` refuse tout `@Redirect`. Un chargeur plus ancien s'essaie en
  production, avec le chargeur, Mixin et MixinExtras de sa version sur le classpath (fichier `.json` de la version sur
  le maven de Fabric).
- Mixins et mods de rendu : une injection dans une méthode déjà réécrite (`@Overwrite`) par un autre mod est une erreur
  critique quel que soit `require` (le jeu ne démarre pas) ; ne rien injecter dans `WorldRenderer.setupTerrain` ni
  `renderLayer`, que les mods de rendu réécrivent. Fabric déclenche ses évènements de rendu du monde dans `setupTerrain`
  avec un contexte préparé par le seul `render` du joueur : appeler `setupTerrain` sur un renderer de vue les tire à
  vide (NPE chez un mod qui lit la projection). Une vue place sa grille et fait avancer le graphe de culling elle-même
  (`RemoteViewRenderer.placeTerrain`). Pour essayer un mod tiers en dev : son JAR dans `run/mods` (Loader le remappe),
  jamais dans le dépôt ; un mod dont les mixins ne tiennent pas en dev Yarn se teste en production (voir la mémoire).
- Chunks distants et mods de rendu : un mod de rendu ne construit une section qu'après la lumière de son chunk passée par
  `ClientPlayNetworkHandler.readLightData` ; un chunk dont la lumière est appliquée à la main n'existe pas pour lui (vue
  noire, sections proches manquantes après une traversée). `RemoteWorld.loadChunk` passe par cette méthode, le monde du
  gestionnaire basculé le temps de l'appel. Un tel mod crée la grille vanilla avec une distance de vue nulle
  (`BuiltChunkStorage.getViewDistance() == 0`, la distance du renderer restant celle des options) : c'est le signe qu'il
  dessine le terrain (`RemoteViewRenderer.foreignTerrain`) ; ses shaders ignorent `gl_ClipDistance`, la vue coupe alors
  par une projection à plan proche oblique (`ObliqueProjection`), pour tout ce qu'elle dessine (une seule échelle de
  profondeur). Un mod de shaders pose en tête de `WorldRenderer.render` un état *par renderer* (son pipeline),
  l'attend dans `renderSky`/`renderLayer` et le défait à chaque `return` de `render` : un renderer de vue doit passer
  par `render` avant de dessiner (`ViewRenderStart`), arrêté à la première instruction vanilla par
  `MixinWorldRenderStart` (priorité 1100 : appliqué après ces mods, son `return` ajouté n'est pas vu par leurs
  injections `@At("RETURN")` ; un arrêt en `HEAD` ou une priorité plus basse les laisse défaire l'état posé).
- Pack de shaders : passes différées, puis passe finale écrite dans la texture de couleur de l'écran par un
  framebuffer à lui, sans profondeur ni stencil (copie directe sans programme `final`) : une vue dessinée au milieu de
  `render` n'est qu'une surface de plus, et rien de ce qu'écrit sa passe finale ne respecte le stencil. Les vues se
  dessinent après l'image (`PortalStencilRenderer.renderAfterWorld`, injection `TAIL` appliquée après son
  `@At("RETURN")`), chacune par le rendu complet de son renderer : il tient un pipeline par dimension (`client.world`
  lu en tête de `render`), lit la position de la caméra du joueur (`gameRenderer.getCamera()`) pour ses shaders,
  n'efface que ses tampons de couleur (la profondeur vient du `RenderSystem.clear` vanilla, épargné pendant une vue par
  un masque nul, `@ModifyArg`), ne dessine pas la main d'une caméra détachée, et ses framebuffers de géométrie attachent la texture
  profondeur-stencil de l'écran. L'image du joueur est remise autour du cadre depuis une copie (`glCopyTexSubImage2D`),
  stencil `NOTEQUAL`. Rendu complet d'un renderer de vue (`WorldRenderer.render` appelé par le mod) : il empile
  lui-même sa matrice de vue sur la pile de `RenderSystem` (la laisser à l'identité, sinon les entités tournent
  deux fois et disparaissent), il lit son frustum dans un champ posé avant lui par `setupFrustum` (sinon NPE chez
  un mod de terrain), et sous un pack le mod de shaders groupe les entités dans les tampons du client
  (`client.getBufferBuilders()`) et ne vide que ceux-là (le renderer de vue les emprunte le temps du rendu), lit la
  luminosité ambiante à `client.cameraEntity` dans `client.world` (le portail d'arrivée tient ce rôle), et sa passe
  finale laisse `depthMask(false)` : remettre les masques avant le rendu, sans quoi l'effacement n'efface rien et la
  profondeur de l'image du joueur masque la vue ; et les remettre en fin d'image même sans vue dessinée, sinon
  l'effacement de l'image suivante n'efface pas la profondeur (après une traversée : monde noir ou noyé de brouillard). Le pack se reconnaît sans le nommer, au milieu du `render`
  du joueur, juste avant les portails (`ShaderPackState`, injection avant la couche translucide) : les programmes que
  vanilla donne aux entités et aux translucides (`GameRenderer.getRenderTypeEntitySolidProgram`,
  `getRenderTypeTranslucentProgram`) sont alors d'une classe à lui. En tête de `render` ou à sa première instruction,
  il ne les a pas encore remplacés (vérifié au banc) ; ses programmes ne passent pas par `ShaderProgram.bind`, et les
  programmes du mod (chargés par Fabric API) sont eux aussi d'une sous-classe : seuls ces accesseurs, à ce point,
  disent le pack. Le rendu complet d'une vue
  repasse par les injections du mod dans `render` (muettes pendant `PortalRenderState.isRendering()`) et déclenche
  `WorldRenderEvents.END` de Fabric (le moniteur de perf ne le compte pas comme une image). Les shaders du pack ignorent
  `gl_ClipDistance` : une entité à cheval sur un portail est rognée au plan sur le processeur (`ClippedVertexConsumers`),
  et un mod de terrain peut écrire une fois dans le journal qu'un tel tampon n'a pas son chemin d'écriture rapide (il
  repasse par celui de vanilla). Le mod de shaders tient un pipeline par identifiant de dimension, créé à la demande, et
  remet ses compteurs d'animation à zéro à chaque création ; un pipeline à part pour une vue du
  monde du joueur, sous une autre clé, a été essayé puis retiré : avec une entité à cheval, cette vue perdait du terrain.
  Il nomme pareil les programmes de chacun de ses pipelines, et `ShaderProgram.loadShader` redonne tout étage déjà
  compilé sous le même nom (`ShaderStage.Type.getLoadedShaders()`, cache statique) : seul, le mod n'a jamais deux pipelines
  vivants ; une vue ou une traversée en fait vivre deux, et le second recevait les étages du premier (entités et main
  éclairées comme dans l'autre dimension). `MixinShaderProgram` ne redonne un étage qu'à la fabrique qui l'a chargé.
  Projection à plan proche oblique et pack : son plan lointain est incliné, et pour une partie des pixels de ciel
  (profondeur 1) la projection inverse rend un point derrière la caméra ; le pack y calcule ciel et nuages à l'envers
  (vue délavée, sans nuages). Aucune échelle du plan ne règle tous les rayons : l'oblique ne sert que s'il y a un bloc
  ou une entité entre la caméra de la vue et le plan d'arrivée (`RemoteViewRenderer.arrivalObstructed`).
  Nuages d'un mod de rendu : il les dessine depuis `gameRenderer.getCamera()`, pas depuis la position reçue par
  `renderClouds` ; la caméra du joueur prend celle de la vue le temps du dessin. Chaque `WorldRenderer` tient son
  horloge (`ticks`, qui place les nuages) : celles des vues suivent celle du joueur, sinon les nuages sautent à la
  traversée. Couper ou activer un pack (comme F3+A ou le mode graphique) ne recharge que le renderer du joueur
  (`WorldRenderer.reload()`) : ceux des vues suivent (`MixinWorldRenderer`), sinon leurs sections gardent le format de
  sommets d'avant (feuilles et verre opaques).
- Sortie fugace et traversée prédite : le serveur supprime le retour d'une sortie fugace perdue de vue et ne le repose
  qu'à l'annonce du passage par l'entrée, après la bascule du client ; un retour prédit (`ClientPortalIndex.predict`)
  tient sa place chez le client, sinon le joueur qui se retourne voit ce qu'il y a derrière le cadre.
- Monde client gardé après une traversée par portail : le joueur garde à part le monde où vanilla l'a créé
  (`AbstractClientPlayerEntity.clientWorld`, final, que vanilla ne relit pas : `ClientPortalCrossing` le suit par un
  accesseur `@Mutable`), l'état de chargement du gestionnaire (`ClientPlayNetworkHandler.worldLoadingState`, vidé
  seulement à la déconnexion) et le contexte de rendu que Fabric API ajoute à chaque `WorldRenderer` (posé au seul rendu
  de ce renderer) gardent le monde de la dernière entrée vanilla. Un monde distant libéré se vide donc avant d'être jeté
  (`RemoteWorld.clear`, avant de détacher son renderer, que les déchargements préviennent). Pour trouver ce qui retient
  un objet : vidage du tas du client de banc (`jmap -dump:live`), puis plus court chemin depuis les racines du GC.
- Sections de terrain vanilla : `WorldRenderer.isRenderingReady` dit qu'une section a été construite une fois, pas
  qu'elle contient le dernier changement de ses blocs ; une reconstruction lancée avant un changement peut finir après
  lui, et une section vide reçoit ses données depuis un thread de travail. Ce qui se décide sur ce qui est affiché passe
  par `SectionGenerations` (génération demandée à `BuiltChunk.scheduleRebuild(Z)`, construite notée par la tâche qui a
  copié les blocs). La face commune à deux blocs pleins ne se dessine pas : un bloc plein n'en cache ce qui est derrière
  lui que si la case devant lui est libre (`FrameOcclusion`).
- Géométrie posée dans le plan d'une face de bloc (zone d'un cadre plaqué sur un mur ou posé sur un sol plein) : un
  `glPolygonOffset` de quelques unités suffit de loin, pas de près, où l'imprécision des positions du terrain (en
  flottants, relatives à leur section) vaut des centaines d'unités de profondeur ; à quelques centimètres de la caméra,
  la face du bloc l'emporte. Avancer la géométrie d'une distance fixe vers la caméra (`FrameAreaRenderer.TOWARD_CAMERA`,
  un dixième de millimètre : un millimètre déplaçait déjà ses bords d'un pixel par endroits).
- Rendu au milieu de `WorldRenderer.render` (les portails passent avant la couche translucide) : les couches de terrain et
  d'entités vanilla supposent l'élimination des faces arrière active, `RenderPhase.ENABLE_CULLING` ne l'active pas ; un
  `disableCull` laissé actif fait dessiner les deux faces de l'eau et du verre (opacité doublée). Ne couper le culling
  que le temps d'un dessin. Les tampons d'entités du monde principal tiennent encore ses couches translucides à ce
  moment-là : une vue dessine dans les siens (`VertexConsumerProvider.immediate`), avec la matrice de vue empilée par
  vanilla neutralisée, puis rend brouillard et éclairage d'entités du monde du joueur.
- Changement de dimension d'un joueur : le serveur retire chez lui les copies d'entités qu'il ne suit plus de la même
  façon et les renvoie aussitôt par l'autre voie ; un retrait immédiat côté client fait disparaître quelques ticks le
  portail de retour juste derrière le joueur (`EntityHandover.deferRemoval` diffère le retrait).
- Un joueur qui change de dimension perd aussitôt ses tickets de chunk : sans autre ticket, le côté quitté se décharge en
  quelques ticks et ses entités reviennent sous de nouveaux identifiants (`DestinationLoader.holdDeparture` le garde
  chargé). Côté client, `EntityIndex` refuse une copie dont l'UUID est déjà présent (`Duplicate entity UUID`) : elle
  reste affichée sans jamais être mise à jour (`EntityHandover.replaceStaleCopy` retire l'ancienne d'abord). Les bancs
  en `forceload` ne voient pas ce déchargement.
- Entités : avant son premier tick, une entité qui vient d'apparaître a `prevX/prevY/prevZ` à l'origine du monde (un
  trajet de là coupe n'importe quel plan : `TeleportExecutor.crossedThisTick` ignore les sauts de plus de 8 blocs).
  Côté client, une entité s'affiche environ un bloc derrière le serveur (interpolation) : téléportée par un portail,
  sa copie n'a pas encore atteint le plan, et la nouvelle, reprise à cette position, reste derrière le plan d'arrivée
  (`EntityHandover` garde l'ancienne jusqu'à l'arrivée de la nouvelle, dessinée ensuite des deux côtés par
  `StraddlingEntityRenderer`). Une décision de rendu se prend sur la position affichée (`lerp(tickDelta, lastRenderX,
  getX())`), jamais sur celle du tick, qui a jusqu'à un tick d'avance, et au point de traversée, comme la traversée
  (`crossingPointFaces`, `crossingPointFacesArrival` : les yeux pour un joueur ou un cadre couché, les pieds sinon),
  jamais aux pieds d'office : sur un cadre au sol, les pieds d'une entité passent le plan bien avant ses yeux.
- Moteur de lumière (sources virtuelles, celles de la lumière transmise par les portails) : `ChunkBlockLightProvider.getLightSourceLuminance` est l'entrée des sources
  des mises à jour (`checkBlock`) ; l'éclairage initial d'un chunk lit `getLuminance()` sans elle, et la lumière sauvée
  avec un chunk revient telle quelle (re-signaler ce qui manque ou ce qui a pu s'éteindre). Le moteur serveur
  tourne sur son propre thread (lectures thread-safe obligatoires), et `checkBlock` sur les cellules changées suffit à
  propager ou éteindre une source virtuelle, serveur comme client.
- Météo : `World.getThunderGradient` rend l'orage déjà multiplié par la pluie, alors que le paquet vanilla
  (`THUNDER_GRADIENT_CHANGED`) porte la valeur brute (`WorldAccessor`). Vanilla ne tique que le renderer du joueur :
  celui d'une vue doit tiquer lui-même (`WorldRenderer.tick`), sinon sa pluie reste figée, et `client.worldRenderer`
  est déjà celui de la vue pendant qu'elle se dessine.
- Renderers : `WorldRenderer.close()` ne libère que ses post-effets ; les tampons de son `BufferBuilderStorage` (une
  trentaine de Mio hors du tas Java) ne sont jamais rendus. Un renderer de vue jeté passe par `RemoteWorld.release`, qui
  garde ses tampons pour le suivant.
- Spectateur : `ServerPlayerEntity.attack` d'un spectateur appelle `setCameraEntity`, qui le téléporte vers la cible,
  dans son monde ; toute action d'un spectateur à travers un portail est refusée. Vanilla ne suit un joueur spectateur
  que pour les spectateurs (`canBeSpectated`) : une vue de portail fait de même, et un spectateur ne compte pas pour une
  sortie fugace. La traversée d'un portail ne passe pas
  par `teleportTo` : l'événement Fabric `AFTER_PLAYER_CHANGE_WORLD` ne signale que les changements de monde vanilla
  (`AFTER_RESPAWN` une réapparition), et `ENTITY_UNLOAD` part juste avant que vanilla ne retire le suivi d'une entité.
- Changements de blocs : `World.setBlockState` ne prévient ses écouteurs (`updateListeners`, donc `markForUpdate`)
  que dans un chunk actif (`getLevelType().isAfter(BLOCK_TICKING)`) ; vanilla n'envoie à ses joueurs que des chunks
  actifs. Un ticket de rayon r n'active que les chunks à moins de r : une vue reçoit aussi l'anneau seulement chargé,
  dont les changements passent par `MixinWorld`.
- Envoi des chunks : tant qu'un chunk attend son tour dans le `ChunkDataSender` d'un joueur, vanilla ne lui envoie aucun
  changement de bloc (`isTracked` exclut `isInNextBatch`), comptant l'envoyer entier : un chunk qu'on lui épargne (tenu
  après une traversée, `MixinChunkDataSender`) et qui change doit partir quand même.
- `ChunkBuilder$BuiltChunk$RebuildTask` reçoit une région nulle pour une section vide (`ChunkRendererRegionBuilder.build`
  rend `null` quand tout est vide) ; et un mod de rendu du terrain ne passe pas par elle. Ce qui doit connaître les
  blocs d'un monde client (index des néons) se relève sur les chunks eux-mêmes : `ClientChunkEvents.CHUNK_LOAD` de
  Fabric (fin de `loadChunkFromPacket`, mondes distants compris), `ChunkSection.hasAny` (la palette, sans parcourir
  les blocs), puis `ClientWorld.setBlockState(BlockPos, BlockState, int, int)`, par où passent paquets, mondes distants
  et prédiction du joueur.
- `ClientPlayNetworkHandler.onBlockEntityUpdate` applique les données à `client.world`, pas au monde du gestionnaire :
  rebrancher le gestionnaire sur un monde distant ne suffit pas, le monde distant les applique lui-même.
- Tests en jeu : un client rapporte les sorties fugaces qu'il voit quand cela change (au plus tous les quatre ticks) et au
  moins chaque seconde (`PortalSightReporter`) ; un test qui attend une sortie fugace perdue renvoie ses rapports à chaque
  tick, comme un vrai client. Une exception autre qu'un échec de test dans une tâche (un `List.of` avec l'identifiant
  d'un portail déjà disparu, `NullPointerException`) fait planter tout le serveur de test.
- Rendu des vues de portail : vanilla ne recalcule la lightmap qu'une fois par tick (`tick()` la marque, `update()` la
  refait) ; la recalculer par image coûte 256 points et un millier de `Vector3f` par appel, et l'envoi de la texture
  attend les dessins en cours. `BlockBufferBuilderPool` (tampons de construction) n'est pas thread-safe : deux
  `ChunkBuilder` ne peuvent pas partager le même pool. Chaque `BuiltChunk` crée quatre `VertexBuffer` (douze objets GL)
  à la construction de la grille : une grille à 12 chunks de distance en crée 30 000 pour un monde de 4 sections de
  haut, 180 000 pour 24 (`MixinVertexBuffer` les diffère pour les renderers de vue).
- Banc multijoueur : une partie solo ouverte en LAN reste en mode en ligne (`IntegratedServer.setupServer`) ; un second
  client de dev, sans session Mojang, y est refusé dès la connexion. Le multijoueur se vérifie par les tests en jeu
  (`GameTestPlayers`).
- Caméra d'une vue : un lacet et un tangage ne suffisent pas quand le portail tourne autour d'un axe horizontal (d'un sol
  à un mur) ; `Camera.getRotation()` et ses trois directions (`getHorizontalPlane`, `getVerticalPlane`,
  `getDiagonalPlane`) sont des champs mutables que le rendu lit : `PortalViewTransform.aim` y pose la rotation
  complète (roulis), à recopier aussi dans la caméra du joueur quand elle prend la place d'une vue sous un pack.
  Un quaternion et son opposé sont la même rotation, et le lacet du joueur n'est jamais ramené à un tour : un angle
  lu dans un quaternion (`2 atan2(z, w)`) se prend avec `w` positif, sinon un roulis nul vaut un tour entier.
- Tests en jeu : un joueur tout juste connecté ne prend aucun dégât pendant 60 ticks (`joinInvulnerabilityTicks`,
  décompté par `playerTick`, donc par `GameTestPlayers.tick`) ; un objet perd 2 % de vitesse par tick, gravité ou non.
- Tests en jeu : un mob `setAiDisabled(true)` n'a plus de gravité (vanilla ne le fait plus bouger) ; pour un corps qui
  tombe sans dériver, un porte-armure. Un joueur monté ne bouge qu'à la réception des paquets de son client : le trajet
  de son véhicule ne se lit pas dans `prevX` (`TeleportExecutor` le suit d'un tick à l'autre). Un serveur de test qui
  plante n'écrit pas `build/junit.xml` : un script qui lit ce rapport doit tenir le code de sortie pour un échec.
- Tests en jeu : un bloc peut changer puis revenir dans le même tick (une case de lave qui se vide et se remplit) ; le
  serveur le voit (`markForUpdate`), une empreinte des blocs relevée entre deux ticks jamais. Pour savoir si un chunk a
  été signalé, un joueur témoin qui le suit reçoit de vanilla chaque bloc changé pendant le tick (`BlockUpdateS2CPacket`,
  `ChunkDeltaUpdateS2CPacket`), une fois le chunk reçu. Le serveur de test coupe déjà le feu, les ticks aléatoires et
  l'apparition des mobs, sur une graine fixe (0) : un test instable y dépend du minutage, pas du terrain.
- Tests en jeu : la CI les rejoue trois fois sur le même monde (étapes tests en jeu, couverture, `build`, sans `clean`)
  et mesure la couverture sur le deuxième passage : un test tient sur un monde déjà joué (il remet ce qu'il suppose,
  comme le vide de l'End), et une branche que seul un monde neuf fait prendre fait tomber la couverture de la CI.
  Rejouer en local : `runGameTest` puis `jacocoFullReport jacocoCoverageVerification`, sans `clean`. Un joueur simulé a
  la portée de vue par défaut d'un client, deux chunks (`SyncedClientOptions.createDefault`) : moins que la fenêtre
  d'un portail (`DestinationLoader.syncRadius`).
- Tests du client : le serveur envoie un son aussitôt, les blocs changés seulement en fin de tick ; un test qui pose des
  blocs puis joue un son attend que le client ait les blocs. Le délai de libération d'un monde distant y vaut 5 s
  (`remoteWorldReleaseSeconds`), et après le dernier test le client quitte le monde : la fin de partie est un test
  (`end_of_game`). Une règle de rendu ou d'audio qu'aucune image ne montre se lit dans l'état (uniforme, sampler,
  `alGetSourcef`, diagnostic `AcousticRouter.describe`), jamais à l'œil.
- Tests en jeu : `GameTestPlayers.sent` consomme ce qu'il rend. Une attente puis une vérification qui lisent le même
  type doivent accumuler ce qu'elles lisent, sinon la vérification ne voit plus ce que l'attente a déjà lu (et un envoi
  fautif passe inaperçu).
- Tests en jeu : un changement de bloc de l'autre côté n'est relayé (`RemoteBlockUpdatePayload`) que dans un chunk déjà
  envoyé au joueur (`PortalViewTracker.onBlockChanged`) ; un test qui change un bloc attend d'abord le `RemoteChunkPayload`
  de son chunk, pas seulement l'annonce de la dimension (envoyée avec le premier chunk prêt) : sur le monde neuf d'un
  `clean build`, le chunk du bloc part quelques ticks plus tard et le bloc changé arrive dans le chunk entier, sans relais.
- Tests en jeu : `PlayerManager.respawnPlayer` appelé directement ne met pas à jour `networkHandler.player` (vanilla le
  fait à la demande de respawn) ; sans cela, la déconnexion retire l'ancien joueur et le nouveau reste dans le monde,
  compté parmi les joueurs des tests suivants.
- Collisions de blocs : toutes les requêtes passent par `BlockCollisionSpliterator` (déplacement, place libre, poussée
  hors des blocs, contrôle des pas d'un joueur par le serveur) ; un bloc qu'on y retire l'est pour toutes, mais pas pour
  les effets de contact (`onEntityCollision` : cactus, toile) ni les fluides. Le serveur refuse le pas d'un joueur qui
  entre dans un bloc qu'il voit (ramené en arrière) : un bloc caché au client doit l'être au serveur, et encore un peu
  après le plan (l'engagement retenu de `PortalCollision.tunnel`), sinon le pas qui passe le plan est refusé.
  L'étouffement (`Entity.isInsideWall`) et le voile de mur à l'écran (`InGameOverlayRenderer.getInWallBlockState`, huit
  points autour des yeux) se décident chacun de leur côté.
- Cadre double face : passé le plan de la face avant, on est devant la face arrière (même centre, normale opposée) ;
  « le portail où l'entity est engagée » doit exclure la face arrière de celui qu'elle traverse, qui cache le côté d'où
  elle vient. La caméra ne passe jamais le plan avant la bascule : un œil dans un bloc caché derrière le plan y lit une
  lumière nulle (image noire sous un pack de shaders) ; d'où la traversée aux yeux des cadres couchés.
- Pack de shaders et balancement : vanilla met le balancement de la vue en marchant dans la projection
  (`GameRenderer.renderWorld`), le pack le porte dans la matrice de vue passée à `WorldRenderer.render` (projection
  sans balancement, mesuré au banc) ; une vue construite depuis la seule rotation de sa caméra reste immobile dans un
  cadre qui balance (`ShaderPackPortalRenderer.VIEW_EXTRA` lui reporte l'écart).
- Pack de shaders et main : le pack dessine la main à sa vraie profondeur, et le plan d'un cadre à moins d'un demi-bloc
  de la caméra la bat ; une vue dessinée après l'image la recouvrait (`HandMask` marque ses pixels dans le stencil).
- Code commun mesuré : pas de branche sur `world.isClient()` (le serveur de test n'a pas de client, la branche client
  ne serait jamais prise) ; le côté se choisit par `PortalCollision.Worlds.side`, que le client implémente
  (`ClientPortalIndex`). JaCoCo affiche un ratio sous le minimum tronqué à la précision du minimum (« 0.9 » pour 0,998) :
  les lignes manquantes se lisent dans le rapport fusionné (`./gradlew jacocoFullReport`, `build/reports/jacoco/full`).
- Suivi par entity : `Entity.equals` compare les identifiants réseau, que deux entités de mondes différents (un monde
  distant du client) peuvent partager : une table par entity est une `IdentityHashMap`, une liste tenue à côté se vide
  par identité. Un tick la parcourt par index : l'itérateur d'une `IdentityHashMap` alloue une entrée par élément
  (`PortalCollision.Side`).
- Chunks côté serveur : `World.isChunkLoaded` (`ServerChunkManager`) ne regarde que le niveau du holder (ticket ≤ 33) :
  vrai dès qu'un ticket est posé, avant toute génération, et la première lecture d'un bloc génère alors le chunk sur le
  thread serveur. Un chunk entier et accessible se teste sans attendre par `getChunkManager().getWorldChunk(x, z) != null`
  (`ChunkHolder.getOrNull(FULL)`). Un ticket de rayon 0 demande le statut entier ; vanilla génère sur ses threads de
  travail et finit le chunk (conversion, lumière) par des tâches du thread serveur exécutées dans le temps libre du tick.
  Le serveur de test tique en une fraction de milliseconde (mille ticks en 0,15 s au repos) : un chunk demandé par
  ticket y met des milliers de ticks à être entier (le temps mural de sa génération, 0,3 s pour un chunk du Nether sur
  le PC de référence) ; attendre son état, pas un nombre de ticks, avec une limite de ticks large.
- Génération vanilla non déterministe près des bords de chunks : charger un chunk entier demande les features de ses huit
  voisins, posées par les threads de travail dans un ordre qui change d'un lancement à l'autre ; une feature qui déborde
  sur le chunk d'à côté (colonnes de basalte des deltas, arbres) donne un terrain différent d'un monde neuf à l'autre,
  à graine égale. Un test qui fige une arrivée prépare son terrain chunk par chunk au statut `FEATURES` dans un ordre
  fixe (`PortalArrivalReferenceGameTests.prepare`).
- Framebuffers : `Framebuffer.delete()`, comme sa création et `resize`, laisse lié le framebuffer par défaut (0), et la
  création comme `resize` activent le test de profondeur (`GlStateManager._enableDepthTest`) ; au milieu
  d'une image, relier celui du jeu (`client.getFramebuffer().beginWrite(false)`), sinon le reste de l'image se dessine à
  côté de l'écran (journal de débogage des portails : « Framebuffer lie 0 »). Une vue capturée sur un rectangle de l'écran
  (`FrozenViews`) garde la projection du joueur et décale la fenêtre de rendu (`glViewport` accepte une origine
  négative) : une projection décentrée donnée au `Frustum` retomberait dans le piège de `coverBoxAroundSetPosition`.
- Parcours des sections d'une vue : sa caméra est en deçà du plan d'arrivée, à la distance du joueur au cadre, et le
  volume visible (`ViewCone`) est fermé par ce plan ; un parcours parti de la caméra n'atteint pas le cadre (fond noir de
  loin). `ViewSectionWalker` part alors des sections du cadre (`ViewCone.startsAtFrame`).
- Shaders vanilla : la source donnée au pilote (`ShaderStage.load`, interceptée par `MixinShaderStage`) a ses
  `#moj_import` déjà résolus par `GlImportProcessor` : bibliothèques insérées, leur `#version` mis en commentaire,
  directives `#line` posées. Un patch analyse cette source-là (commentaires blanchis sans déplacer un caractère) et
  s'insère après la seule `#version` active. Les `#moj_import` ne résolvent que `minecraft:shaders/include/` : une
  bibliothèque du mod s'insère par le patch, lue par la fabrique de ressources qui charge le programme (`loadShader`),
  à cache que vanilla pose sur le gestionnaire du rechargement (`GameRenderer.CachedResourceFactory`, à déballer :
  `original()`, ce n'est pas un `ResourceManager`), le même que notre écouteur de rechargement reçoit après ; les
  shaders de l'écran de chargement arrivent avant, par la fabrique du pack vanilla, sans énumération. Un patch jamais
  appliqué ne se voit pas à l'œil (lightmap, brouillard et post du profil suffisent à changer l'image) : le journal
  dit « N étage(s) de shaders patché(s) compilé(s) par le pilote » à chaque rechargement, et un programme patché a
  l'uniforme `gtgs_LookId` (emplacement ≥ 0). `ShaderProgram.bind` est appelé à chaque dessin : un uniforme ne se
  renvoie que si sa version a changé (`LookState.version`). Bloc d'uniformes (UBO, `layout(std140)`) : son index se
  lie une fois par programme (`glUniformBlockBinding`) à un point de liaison fixe, où `glBindBufferBase` pose le tampon
  de la vue courante ; vanilla 1.21.1 n'en utilise aucun. Un `${position}` dans le code d'une injection vaut
  `Position + ChunkOffset` dans un shader qui déclare `ChunkOffset` (terrain), `Position` sinon. Le stencil des portails
  porte le niveau dans `0x03` et le look dans `0x1C` : tout test de niveau masque avec `StencilLookMask.LEVEL_MASK`, un
  effacement n'écrit que les bits du masque d'écriture courant (`glStencilMask(0xFF)` avant), INCR et DECR du niveau
  laissent les bits de look, qu'une seconde passe sur la zone (REPLACE, masque `LOOK_MASK`) écrit pour la vue.
- Pack de shaders et pipeline vanilla du mod de shaders : il tient un pipeline par dimension, en cache, et chaque
  pipeline pose a sa creation des reglages globaux (format des maillages de sections du mod de terrain, AO separe...)
  qui ne sont pas reposes quand un pipeline en cache redevient courant. Deux pipelines dans une meme image (le pack pour
  le monde du joueur, vanilla pour une vue) dessinent le terrain en eclats (maillages d'un format, shader de l'autre).
  Solution livree le 29 octobre 2026 (`compat/iris`) : tant que le joueur est dans une dimension a look, Iris rend son
  pipeline vanilla pour tout ce qui se dessine, vues comprises (`PipelineManager.preparePipeline` en tete, pipeline
  courant pose par accesseur) ; a chaque bascule, les reglages de `WorldRenderingSettings` du pipeline quitte sont
  gardes et ceux de l'autre reposes, le compteur de rechargement des shaders de sections de Sodium avance, et le
  renderer du joueur se recharge (ceux des vues suivent). Le pipeline du pack reste en cache : aucune recompilation au
  retour (mesure : 52 ms d'image au retour, contre environ 1 s pour couper et rallumer le pack). Un pipeline vanilla
  construit par le mod pose ses reglages a sa construction ; `destroyPipeline` d'Iris (pack change, recharge) le detruit
  avec les siens. Depuis l'Overworld, une vue du Level 0 reste rendue par le pack, le look pose dessus
  (`LookPost.renderUnderShaderPackView`). La bascule ne se fait qu'au rendu du joueur, jamais au milieu d'une vue.
  Ordre des imports : les paquets d'un mod tiers (`net.irisshaders`, `net.caffeinemc`) ne sont dans aucun groupe de
  Checkstyle et vont en dernier, apres `com.gtgpuke`. Le greffon de mixins tourne pendant le demarrage de Mixin : il ne
  touche aucune classe du mod vise (version lue par Fabric Loader, `IrisVersion`).
- Moteur de son vanilla : `SoundSystem.play` attend la création de la source (`join`) et la retient dans `sources`
  avant de rendre la main : à la fin de `play`, la `SourceManager` d'un son se lit dans cette table, et
  `SourceManager.run(consumer)` exécute sur le thread du son, seul endroit des appels OpenAL par source. Le contexte
  OpenAL est courant pour tout le processus (`alcMakeContextCurrent` dans `SoundEngine.init`, sur le thread de rendu) :
  les objets EFX (slots, effets, filtres) se créent où l'on veut, et disparaissent avec lui à `close` (identifiants à
  oublier, jamais à réutiliser). `AL.getCapabilities().ALC_EXT_EFX` dit si EFX existe ; les envois auxiliaires se lisent
  par `alcGetInteger(device, ALC_MAX_AUXILIARY_SENDS)` (OpenAL Soft en donne deux sans les demander). Une source
  copie les réglages d'un filtre quand on le lui attache (`AL_DIRECT_FILTER`) : après `alFilterf`, le reposer. Une
  table par `SoundInstance` à clés faibles perd ses entrées dès que vanilla lâche le son et que le ramasse-miettes
  passe, avant une revue périodique : ce qu'elle tenait par son (un filtre) n'est jamais rendu ; clés fortes, revue
  d'après la table du moteur. `SoundSystem.reloadSounds` (F3+T, changement de périphérique) arrête toutes les sources
  puis ferme et recrée le contexte : tout ce qui se souvient d'un son en cours s'oublie à `SoundEngine.close`.
  `alGetError` rend l'erreur au premier qui la lit : une erreur laissée par le mod est journalisée par vanilla comme
  sienne (`AlUtil.checkErrors`) ; la lire après chaque série d'appels EFX.
- OpenAL Soft et EFX : le filtre direct d'une source ne se relit pas (`alGetSourcei(AL_DIRECT_FILTER)` lève une
  erreur) ; un autre moteur qui travaille dans le contexte se reconnaît à ses objets (`alIsFilter`, `alIsEffect`,
  `alIsAuxiliaryEffectSlot` sur les identifiants, qu'OpenAL Soft donne à partir de 1 ; sans erreur pour un nom inconnu,
  et `alIsFilter(0)` vrai : le filtre nul). Le nombre d'envois auxiliaires se demande à la création du contexte
  (`ALC_MAX_AUXILIARY_SENDS` dans les attributs d'`alcCreateContext`, `MixinSoundEngine`), il ne se change pas après.
- `/playsound` n'est pas un son du monde : le paquet part aux seuls joueurs visés, sans monde ni relais par les
  portails. Un son du monde au banc : un bloc musical sous une tête qui porte le son (`note_block_sound`), déclenché
  par un bloc de redstone posé à côté.
- Jumeau GLSL d'un hachage Java sur `int` : en GLSL 150, `uint` (multiplications modulo 2^32 comme Java) ; le `>>>` de
  Java est le `>>` de GLSL sur `uint`, le `>>` sur `int` est arithmétique des deux côtés ; aucun flottant dans une
  décision (un `fract(sin())` diffère d'un pilote à l'autre). Les deux se tiennent par un fichier de vecteurs communs
  écrit par un programme indépendant (`neon_schedule_vectors.json`).
- État GL de repos : entre deux dessins, vanilla laisse le test de profondeur coupé (chaque `RenderLayer` le coupe en
  sortie) et l'écriture de la profondeur active ; une passe en fin de `renderWorld` rend cet état. Sous un pack, après
  l'image du pack, le test de profondeur est actif ; dans le cadre d'une vue sous un pack, l'écriture de la profondeur
  est coupée et le test de stencil de la vue posé (`GL_EQUAL` sur sa valeur) : une passe au milieu d'une vue ne le
  coupe jamais, la suite (vues imbriquées, image du joueur remise autour du cadre en `GL_NOTEQUAL`) en dépend ; coupé,
  l'image du joueur recouvre l'écran entier et les vues disparaissent (bug du look par vue, 28 octobre 2026). Une passe
  se vérifie au banc par `-PlookCheckGlState=true` (`GlStateCheck` : état relu au pilote avant et après elle). Le
  masque d'écriture du stencil vaut tous les bits à 1 par défaut (`glGetInteger` rend -1), 0xFF pour le mod : même effet
  sur un stencil de huit bits.
- Copie du monde client pour un autre thread : le client n'a pas la graine du monde (`GameJoinS2CPacket` porte une
  graine hachée), rien ne se recalcule de la génération côté client. Classer un bloc (`isOpaqueFullCube`,
  `getCollisionShape`) coûte bien plus que le lire : 4096 blocs classés un par un valaient 0,7 ms par section, classés
  par état distinct (quelques-uns par section) beaucoup moins. `ChunkSection.isEmpty()` dit qu'une section n'a aucun
  bloc non vide : une copie partagée suffit. Une section copiée se remplace entière, jamais en place (un autre thread
  la lit). `ChunkManager.getWorldChunk(x, z)` rend `null` pour un chunk non chargé, sans le charger.
- Sons et ressources : les jeux de sons (`SoundManager.get`) n'existent qu'après le rechargement du gestionnaire de sons ;
  un écouteur de rechargement qui les lit déclare `ResourceReloadListenerKeys.SOUNDS` dans ses dépendances Fabric. Les
  variantes d'un `WeightedSoundSet` sont privées (`sounds`, accesseur) ; `Sound.getLocation()` est déjà le chemin du
  fichier (`sounds/.../x.ogg`, à donner tel quel à `ResourceManager.getResource`, pas à `Sound.FINDER` une seconde fois),
  et `OggAudioStream` (jorbis) le décode en 16 bits dans l'ordre natif, par `read(taille)` jusqu'à un tampon vide. Un
  renvoi vers un autre évènement (`"type": "event"`) est un conteneur anonyme qui ne dit pas sa cible : ses variantes
  s'énumèrent par un `Random` à tirage fixe (une valeur par unité de poids), pas par des graines voisines
  (`Random.create(0..15)` donne seize fois le même premier `nextInt(4)`). `AL_SEC_OFFSET` se lit sur le thread du son
  (`SourceManager.run`), jamais ailleurs.
- Banc : une fonction de datapack qui nomme un bloc ou une entité du mod ne se charge pas du tout sur un client sans le
  mod (une commande inconnue invalide la fonction entière) : ces commandes vont dans une fonction à part, appelée par
  `function` (l'appel échoue seul). Un monde du banc se rejoue sans le mod par une copie dont `level.dat` perd la
  dimension du mod (`WorldGenSettings.dimensions`) et son pack (`DataPacks`), le joueur remis dans l'Overworld
  (`make_vanilla_world.py` du scratchpad, `nbtlib`).
- Dessin plein écran : `ShaderProgram.initializeUniforms` alloue douze noms de samplers (`"Sampler" + i`) à chaque
  appel, et le tessellator vanilla un tampon par dessin : une passe par image passe par un `VertexBuffer` persistant et
  `addSampler` sous un nom constant, seulement quand la texture change (`FullscreenTriangle`). Une seule requête
  `GL_TIME_ELAPSED` peut être ouverte à la fois : les sections GPU ne s'imbriquent jamais, et rien sous un pack de
  shaders, qui dessine ses vues au milieu des nôtres (`GpuTimer`).
- L'extension Java de VSCode compile avec JDT dans `build/classes/java/main` (même dossier que Gradle) ; avec un modèle de projet périmé, elle y écrit des classes `Unresolved compilation problems` qui font planter le client au chargement. L'autobuild est coupé dans `.vscode/settings.json` ; ne jamais le réactiver sans un import Gradle sain (JDK 21).

## Avant de push

Suivre `.prompts/BEFOREPUSH.md` : `./gradlew clean build --warning-mode all` sans warning (il enchaîne Checkstyle,
tests unitaires, tests en jeu, verrou de couverture à 100 % des instructions et des branches, contrôle du contenu
du JAR), CHANGELOG et docs à jour. Toute instruction nouvelle hors colle OpenGL
client doit être couverte par un test unitaire ou un test en jeu, sinon le build échoue (`docs/dev/TESTS.md`, section Couverture).
