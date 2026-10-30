# Tests

## Vue d'ensemble

`./gradlew build` lance toutes les vérifications avant d'assembler le JAR, par la tâche `check`. Un test rouge, une
violation Checkstyle, une couverture incomplète ou un JAR qui contient autre chose que le mod font échouer le build.

| Famille | Commande | Rapport | Dans `build` |
|---------|----------|---------|--------------|
| Tests unitaires | `./gradlew test` | `build/reports/tests/test/index.html` | oui |
| Empreintes et vecteurs communs | inclus dans `test` | message d'échec du test | oui |
| Tests en jeu (GameTest, serveur) | `./gradlew runGameTest` | `build/junit.xml`, `build/run/gameTest/logs/` | oui |
| Tests du client | `./gradlew runClientTest verifyClientTests` | `build/client-junit.xml`, `build/run/clientTest/logs/` | oui |
| Benchmark de génération | `./gradlew benchmarkLevelZeroGeneration` | console | non |
| Checkstyle | `./gradlew checkstyleMain checkstyleTest checkstyleGametest` | `build/reports/checkstyle/` | oui |
| Contenu du JAR | `./gradlew verifyJarContents` | console | oui |
| Verrou de couverture | `./gradlew jacocoCoverageVerification` | console | oui |
| Couverture fusionnée | `./gradlew jacocoFullReport` | `build/reports/jacoco/full/html/index.html` | non |
| Couverture de tout le code | `./gradlew jacocoAllReport` | `build/reports/jacoco/all/html/index.html` | non |

`test` produit aussi le rapport de couverture des seuls tests unitaires, dans `build/reports/jacoco/test/html/`. Les
tests en jeu et les tests du client tournent sous l'agent JaCoCo. Leur couverture s'ajoute à celle des tests unitaires.

## Couverture

`jacocoCoverageVerification` exige 100 % des instructions et des branches sur le code mesuré. Elle fusionne trois
exécutions : `build/jacoco/test.exec`, `gametest.exec` et `clienttest.exec`. Une seule instruction ou une seule branche
que rien ne prend fait échouer le build.

Le code hors mesure est listé dans `coverageExcludes` (`build.gradle`). Il s'agit de la colle OpenGL et OpenAL du
client, des mixins et des modules de compatibilité. Cette liste se réduit à mesure que les tests du client couvrent ces
classes : une exclusion se retire dès que sa classe atteint 100 % par l'ensemble des tests.

| Exclusion | Raison |
|-----------|--------|
| `core/portal/client/view/` : `PortalStencilRenderer`, `ShaderPackPortalRenderer`, `ShaderPackState`, `PortalClipping`, `FrameAreaRenderer`, `HandMask`, `ViewLightmaps` | Appels GL, rendu complet d'un `WorldRenderer` |
| `core/portal/client/` : `world/**`, `transition/**`, `interaction/**`, `entity/**`, `wand/**`, `sound/FrameSoundPlayer`, `sound/SoundAnchors` | Mondes et renderers distants, bascule de monde, visée et clics, widgets, moteur de son du client |
| `core/render/client/**` | Colle GL du moteur de shaders |
| `core/audio/client/**` | Colle OpenAL et EFX, sources du moteur de son vanilla, thread de calcul du son |
| `backrooms/level0/client/**`, `core/perf/client/**`, `GtgsBackroomsClientMod` | Modèles, hooks et point d'entrée du client |
| `compat/**` | Le mod visé est absent des tests |
| `mixin/**` | Tissés dans les classes vanilla |

La logique pure du client reste mesurée : `PortalViewTransform` pour la caméra transposée, `ViewCone` pour le volume
visible, `ShaderClipInjector` pour le plan de découpe, `PortalSound` pour le son à travers le cadre. Côté audio,
l'occlusion, la réverb, les rayons, les enveloppes et les perturbations sont mesurés.

Dans le code hors mesure, une règle se garde par une mutation détectée : cassée exprès, elle doit faire échouer un test.
Une seule ne l'est pas, faute d'effet qu'un test puisse voir sans dépendre du minutage : après l'ajout de tampons de
construction à une réserve (`BuildBuffers`), une tâche en attente est relancée par tampon ajouté. Sans cela, une file
déjà pleine se viderait au rythme de l'ancienne réserve.

`./gradlew jacocoAllReport` mesure tout le code, exclusions comprises : 86,5 % des instructions et 77,8 % des branches.
Les mixins y comptent pour zéro, car JaCoCo ne voit pas les classes tissées.

### Combler un trou

- Lire la ligne rouge ou jaune dans le rapport fusionné (`./gradlew jacocoFullReport`). La console tronque le ratio à
  la précision du minimum et affiche « 0.9 » pour 0,998.
- Écrire le test qui prend la ligne, plutôt qu'exclure la classe.
- Retirer une branche qu'aucun cas réel ne prend, comme une condition déjà garantie par une autre.
- Extraire en fonction testable une décision pure enfouie dans du code d'état, sans changer ce qu'elle décide.
- Ne pas brancher sur `world.isClient()` dans le code commun. Le serveur de test n'a pas de client, et la branche
  client ne serait jamais prise. Le côté se choisit par `PortalCollision.Worlds.side`, que le client implémente.

Les classes utilitaires, au constructeur privé qui lève, sont couvertes par `UtilityClassesTest`. Ce test les énumère :
une nouvelle classe utilitaire s'y ajoute.

## Tests unitaires

JUnit 5 tourne sous le classloader de Fabric (`fabric-loader-junit`). Les registres n'y sont pas gelés et les mixins de
Fabric API s'appliquent. Les tests se répartissent en deux familles.

- La logique pure n'initialise pas Minecraft et tourne en quelques secondes. Coordonnées, hachage, secteurs, layout et
  transformations de portail en font partie.
- Ce qui touche aux blocs, aux items ou aux registres appelle `MinecraftTestBootstrap.ensure()` dans un `@BeforeAll`.
  Le helper démarre Minecraft une fois par JVM, puis exécute le vrai `onInitialize()` du mod.

```java
@BeforeAll
static void bootstrap() {
    MinecraftTestBootstrap.ensure();
}
```

Lancer un sous-ensemble :

```bash
./gradlew test --tests "*LevelZeroLayoutTest*"
./gradlew test --tests "com.gtgpuke.gtgsbackrooms.core.registry.*"
```

L'arborescence de `src/test/java/com/gtgpuke/gtgsbackrooms/` suit celle du code.

| Paquet | Ce qui est vérifié |
|--------|--------------------|
| `backrooms/level0/generation/` | Coordonnées, graines (`StageRandomTest`), secteurs et leur cache, layout, écriture des blocs, générateur sur un vrai `ProtoChunk`. Invariants : origine ouverte, frontières de chunk identiques des deux côtés, néons jamais adjacents, gaps jamais consécutifs. |
| `backrooms/level0/` | Biomes de surface, mur adaptatif et ses seize variantes, arrivée d'un portail dans le Level 0, forme des salles pour l'audio, codec de la graine du layout |
| `core/portal/` | Douze orientations et leurs 144 paires sans miroir, réglages du bloc et leur NBT, baguette, traversée, collision et blocs cachés derrière le plan, garde d'arrivée, payloads, fenêtre de chunks et fenêtre en cône, poursuite des mobs, sources de lumière virtuelles |
| `core/portal/client/` | Caméra transposée et roulis, volume visible, plan injecté dans 54 des 55 vertex shaders vanilla, projection oblique, preuve qu'un cadre est caché, paliers de détail, terrain simplifié, image figée, quadrilatères rognés au plan, son à travers le cadre |
| `core/render/` | Inventaire des 110 shaders de base de 1.21.1, patch des 55 fragment shaders, profils de look, pile des looks, lightmap, brouillard, calendrier et carte des néons, bits du stencil, framebuffers déclarés, qualité |
| `core/audio/` | Profils audio, occlusion par voxels, réverb, dimension d'émission, copie du monde, rayons et leur cache, direction perçue, horloge et enveloppes des sons liés, perturbations, plafond de clignotement, tremblement |
| `core/registry/`, `core/perf/`, `core/` | Identifiants, ressources de chaque bloc et item, sons et sous-titres, traductions anglaises et françaises identiques en clés, dimension chargée depuis le datapack, onglet créatif, options `-D`, moniteur de performance |
| `compat/` | Plage de versions acceptée par le module de compatibilité, module inactif sans son mod |
| racine | `MixinTargetsTest`, `UtilityClassesTest`, point d'entrée du mod |

`MixinTargetsTest` charge la cible de chaque mixin déclaré, communs et client. Charger la cible applique le mixin : un
`@Shadow` faux fait échouer le build au lieu de planter le client au lancement. Le test refuse aussi tout `@Redirect`,
que le MixinExtras d'un Fabric Loader plus ancien ne sait pas lire.

## Empreintes et vecteurs communs

Trois tests figent une sortie dont tout changement se voit en jeu. Un échec signale soit une régression à corriger,
soit une évolution voulue. Dans ce second cas, recopier les nouvelles valeurs et l'annoncer dans
[CHANGELOG.md](../../CHANGELOG.md).

### Génération du Level 0

`LevelZeroGoldenTest` hache toute la génération logique d'une zone de 7 × 7 chunks : cellules, exposition, masques de
papier peint, détails muraux. Il le fait pour trois graines et cinq layers, soit quinze empreintes.

- Régression : corriger le code.
- Évolution voulue : relancer le test, recopier les valeurs dans `goldenFingerprints()` et l'annoncer dans le
  CHANGELOG. Les mondes existants changent.

Les règles qui protègent la génération sont dans [LEVEL0.md](../systemes/LEVEL0.md).

### Shaders patchés

`PatchedShadersGoldenTest` compile chaque shader de base de 1.21.1 comme le jeu, le coupe au plan des portails, puis le
patche comme le mod. L'empreinte de chaque source modifiée est dans `src/test/resources/patched_shaders_1_21_1.json`.
Un changement d'un patch, d'une bibliothèque GLSL ou de l'injecteur du plan fait échouer le test. Le message donne la
table recalculée, à recopier dans le fichier.

### Calendrier des néons

Le calendrier des néons a deux implémentations : `NeonSchedule` en Java et `shaders/include/neon_schedule.glsl` en
GLSL. Les deux sont confrontées au même fichier, `src/test/resources/neon_schedule_vectors.json`, écrit par un programme indépendant de
Java. Ses 573 vecteurs donnent position, graine, tick et parts, puis le caractère et l'intensité attendus.

`NeonScheduleTest` vérifie Java à chaque build. Le shader se vérifie sur le processeur graphique par l'option
`gtgsbackrooms.look.verifyNeonShader=<chemin du fichier>`, que les tests du client posent. Le journal écrit alors
« N vecteur(s) identiques au fichier commun et a Java ». Changer l'un des deux sans l'autre fait échouer l'une des
deux vérifications.

## Tests en jeu (GameTest)

Le source set `src/gametest` forme un mod séparé, `gtgs_backrooms_gametest`. Seuls `runGameTest` et `runClientTest`
le chargent, et rien n'en entre dans le JAR du mod. Chaque classe de tests serveur est déclarée dans l'entrée
`fabric-gametest` de `src/gametest/resources/fabric.mod.json`.

Le serveur de test vanilla impose un monde plat avec l'Overworld, le Nether et l'End, sur la graine 0. Il ignore les
dimensions de datapack : le Level 0 n'y existe pas, et le Nether sert de dimension distante. Le feu, les ticks
aléatoires et l'apparition des créatures y sont coupés. Le cooldown de traversée y vaut trois ticks, contre zéro par
défaut, pour exercer le refus sous cooldown.

Le rapport par test est `build/junit.xml`. Les messages d'échec y sont, pas dans le journal
`build/run/gameTest/logs/latest.log`.

### Outils

| Classe de `support/` | Rôle |
|----------------------|------|
| `GameTestPlayers` | Joueurs simulés. `connect` ouvre une vraie connexion sur un canal en mémoire, `tick` fait tourner le gestionnaire réseau et acquitte les chunks, `receive` fait entrer un payload comme venu du client, `sent` rend les paquets envoyés au joueur. |
| `GameTestServerWorlds` | Nether de test, dimension absente par `nowhere`, génération d'avance par `pregenerate` et `pregenerateArrival` (11 × 11 chunks), chunks forcés, Level 0 prêté par un autre monde sous sa clé |
| `GameTestArrivals` | Arrivée calculée d'un trait : la recherche chunk par chunk du jeu, relancée avec le même tirage après chaque chunk généré sur place |

Les tests qui posent des blocs, des entités ou des joueurs utilisent l'arène `gtgs_backrooms_gametest:test_arena`, de
24 × 8 × 24 blocs. La structure vide de Fabric ne fait qu'un bloc, et des tests voisins se marcheraient dessus. Dans
l'arène, le sol est en y = -1 relatif, et un plafond de barrières la ferme.

### Classes

| Classe | Ce qui est vérifié |
|--------|--------------------|
| `backrooms/level0/LevelZeroGameTests` | JSON de la dimension décodé puis réencodé, chunk d'origine sur les cinq layers, mur adaptatif posé |
| `core/portal/BackroomsPortalBlockGameTests` | Arrivée cherchée vers une zone jamais générée sans génération sur le thread serveur, copie d'un bloc, sens de sortie, pose couchée, portail à une face et aller simple, sortie fugace, portails reposés quand un réglage change, bloc cassé, dimension absente |
| `core/portal/PortalLightGameTests` | Lumière des blocs transmise d'un niveau de moins par un portail ouvert, visible et réglé pour elle, puis éteinte avec lui |
| `core/portal/PortalWandGameTests` | Réglages de la baguette, pose et refus, copie des réglages d'un portail visé, son et évènement de pose, plan au bord du bloc |
| `core/portal/PortalGameTests` | Traversée d'un joueur et d'un objet, collision à travers le cadre, bords du cadre, payload de chunk |
| `core/portal/PortalSyncGameTests` | Fenêtre de chunks et fenêtre en cône, envoi à chaque tick près du cadre, entités, blocs et météo relayés, monde distant libéré, chunks tenus après une traversée (proposés deux fois, retirés au client), monde quitté ni déchargé ni renvoyé tant qu'un portail le montre, puis libéré sans portail de retour, bloc changé dans le tick de la traversée envoyé avant la bascule, monde quitté oublié quand le retour mène hors de la carte du client, annonces de traversée du client, spectateurs, deux dimensions dans une même vue |
| `core/portal/PortalInteractionGameTests` | Coups, clics, pose et minage à travers le cadre, en créatif et en survie, avec chaque refus |
| `core/portal/PortalPropagationGameTests` | Sons, évènements et particules relayés, mob qui voit, poursuit et suit sa cible à travers le cadre, chaque règle de la poursuite |
| `core/portal/PortalRidingGameTests` | Bateaux, passagers et cavaliers qui traversent ensemble, vers un autre monde ou dans le même |
| `core/portal/PortalOrientationGameTests` | Les 144 paires d'orientations avec un joueur, un objet par chacune des six normales, chute sans dégâts quand le bas change, arrivée tête en bas |
| `core/portal/PortalLyingGameTests` | Chute dans un cadre au sol et sortie sous un cadre au plafond, montée inverse, traversée aux yeux d'un joueur |
| `core/portal/PortalArrivalReferenceGameTests` | Huit arrivées de référence figées sur le terrain du serveur de test. Un remaniement de la recherche qui en change une échoue. |
| `core/portal/PortalDestinationGameTests` | Arrivée sous un plafond hors de la lave, plateforme dans le vide de l'End, échelle du Nether, sortie fixée, recherche chunk par chunk (`resolveLoadedOnly`) |
| `core/portal/PortalTunnelGameTests` | Blocs cachés derrière le plan, engagement retenu, garde d'arrivée sur un cadre au sol, traversée d'un joueur dans la hauteur du cadre |

### Pièges

Attendre et vérifier :

- Attendre un état, jamais un nombre de ticks. Le serveur de test tique sans attendre, alors qu'un chunk demandé par
  ticket met le temps réel de sa génération. Générer l'arrivée d'avance, puis guetter ses chunks par
  `PortalViewTracker.watchedChunkCount() > 0` ou `getChunkManager().getWorldChunk(x, z) != null`.
- `createAndAdd` rejoue sa tâche à chaque tick tant qu'elle lève : c'est une attente. Une vérification définitive va
  dans `createAndAddReported`, jouée une fois. Nettoyer en `try/finally`, et dans un `@AfterBatch` ce qu'une attente
  expirée laisserait.
- Après l'échec d'une tâche `createAndAddReported`, les suivantes s'enchaînent quand même. `build/junit.xml` garde le
  message de la dernière vérification ratée, pas de la première.
- Entre une action et ce que le serveur en juge en fin de tick, une traversée par exemple, placer une attente. Sinon
  l'action suivante efface la première avant le jugement.
- Une exception autre qu'un échec de test fait planter tout le serveur. Un serveur planté n'écrit pas
  `build/junit.xml` : un script doit aussi lire le code de sortie.

Monde et entités :

- Le dossier `build/run/gameTest/` n'est vidé que par `clean`. La CI rejoue les tests en jeu trois fois sur le même
  monde et mesure la couverture au deuxième passage. Un test doit donc tenir sur un monde déjà joué. Pour rejouer en
  local : `./gradlew runGameTest`, puis `./gradlew jacocoFullReport jacocoCoverageVerification`, sans `clean`.
- La génération vanilla change d'un monde neuf à l'autre près des bords de chunk, car les features des voisins se
  posent en parallèle. Un test qui fige une arrivée prépare son terrain chunk par chunk au statut `FEATURES`, dans un
  ordre fixe.
- Un chunk forcé ne suit ses entités qu'après quelques ticks. Attendre `shouldTickEntity` avant d'y faire arriver une
  entité.
- Une entité qui change de monde garde son UUID : la retrouver par `getEntity(uuid)`. Une entité sauvée par un
  lancement précédent peut traîner dans le monde.
- Un mob `setAiDisabled(true)` n'a plus de gravité. Pour un corps qui tombe sans dériver, prendre un porte-armure.
- Un changement de bloc de l'autre côté n'est relayé que dans un chunk déjà envoyé au joueur. Attendre le
  `RemoteChunkPayload` de ce chunk avant de changer le bloc.

Joueurs simulés :

- Un joueur tout juste connecté est invulnérable 60 ticks, décomptés par `GameTestPlayers.tick`.
- Sa distance de vue est de deux chunks, moins que la fenêtre synchronisée d'un portail.
- Il ne suit une entité vanilla qu'une fois ses chunks reçus et sa position redonnée par
  `getChunkManager().updatePosition`.
- `GameTestPlayers.sent` consomme ce qu'il rend. Une attente puis une vérification qui lisent le même type doivent
  accumuler leurs lectures.
- Un test qui attend la perte d'une sortie fugace renvoie les rapports de regard à chaque tick, comme un vrai client.
- `PlayerManager.respawnPlayer` appelé directement ne met pas à jour `networkHandler.player`. Le faire à la main, sinon
  le nouveau joueur reste en ligne après la déconnexion.

## Tests du client

Fabric API n'a pas de tests en jeu côté client pour 1.21.1. Le mod de test porte donc son propre harnais, dans le
paquet `gametest/client/`. `./gradlew runClientTest` lance un vrai client dans `build/run/clientTest/`.

- La fenêtre est cachée (`MixinClientTestWindow`). `-PclientTestVisible=true` la montre.
- `options.txt` est réécrit à chaque lancement : distance de rendu 8, fenêtre de 854 × 480, pas de pause à la perte du
  focus.
- Le son passe par le périphérique nul d'OpenAL Soft (`ALSOFT_DRIVERS=null`). EFX y est complet et rien n'est audible.
- Le monde (`ClientTestWorld`) est un monde plat neuf à chaque lancement : graine 0, créatif, paisible, sans cycle du
  jour, météo, créatures ni tick aléatoire. Le joueur, neuf à chaque lancement, apparaît toujours au point d'apparition
  (`spawnRadius` à 0), devant le cadre des tests : apparu au hasard derrière lui, à moins de 8 blocs, sa première mise
  en place l'aurait fait traverser.
- Le lancement pose `remoteWorldReleaseSeconds=5` et `verifyNeonShader`. Les autres options du mod passent comme au
  client de développement, par exemple `-PportalLod=false` (voir [OPTIONS.md](../reference/OPTIONS.md)).

Le client entre dans le monde, joue chaque test, écrit `build/client-junit.xml` après chaque test, puis se ferme.
`verifyClientTests` fait échouer le build si un test a échoué ou si rien n'a été rapporté. Pour ne jouer que certains
tests, passer leurs noms du rapport : `-PclientTestOnly=wand_screen,third_person_crossing`.

### Écrire un scénario

Un test est un `ClientScenario` : une suite d'étapes jouées tick après tick. Une étape agit une fois, puis attend sa
condition dans une limite de ticks.

| Étape | Effet |
|-------|-------|
| `run(quoi, action)` | Action jouée une fois. `ClientTestContext.onServer` joue du code sur le serveur intégré et attend sa fin. |
| `check(quoi, vérification)` | Vérification jouée une fois, qui lève `AssertionError` en cas d'échec |
| `await(quoi, condition, ticks)` | Attente bornée, 200 ticks par défaut |
| `frames(n)` | Attente de n images, jusqu'à vingt ticks par image pour un rendu logiciel lent |
| `ticks(n)` | Attente de n ticks du client |
| `capture(quoi, lecteur)` | Capture de l'image suivante, HUD masqué. L'image d'un échec est gardée dans `build/run/clientTest/captures/`. |
| `cleanup(action)` | Nettoyage joué après le test, qu'il réussisse ou non |

Une nouvelle classe de scénarios s'ajoute à la liste de `ClientTestMod.onInitializeClient`.

### Erreurs OpenGL

Pendant les tests, `ClientTestGlErrors` remplace la sortie de débogage de vanilla par celle du pilote, en mode
synchrone (KHR_debug). Toute erreur OpenGL pendant un test le fait échouer, avec la pile d'appels qui l'a provoquée.
Les erreurs relevées au chargement ou après le dernier test sont rapportées sous leur propre nom.

La suppression du tampon nul compte aussi comme une erreur (`MixinClientTestGlStateManager`). Sous Linux, vanilla vide
un tampon avant de le supprimer, et vider le tampon nul y est une erreur. Les autres pilotes l'ignorent en silence. Un
pilote strict comme Mesa, celui de la CI, signale ce qu'un pilote tolérant laisse passer.

### Classes

| Classe | Tests | Ce qui est vérifié |
|--------|-------|--------------------|
| `LevelZeroClientTests` | `level_zero_look_neons_and_hum` | Look du Level 0 dans les shaders, néons envoyés, brouillard de la couleur du profil, jumeau GLSL vérifié sur le processeur graphique, graine du layout reçue, bourdonnement routé par le moteur audio, image teintée |
| `EngineRulesClientTests` | `engine_rules_in_level_zero` | Règles qu'aucune image ne montre : seuil du bloom dans l'uniforme, framebuffer recréé avec son stencil, deux dessins plein écran sur deux textures, volume lu sur la source OpenAL, son rejoué traité sur sa nouvelle source |
| `PortalViewClientTests` | `portal_view_and_seamless_crossings`, `remote_world_released_and_forgotten`, `crossing_backwards_through_an_unseen_frame`, `hand_keeps_the_players_look` | Vue du Level 0 dans un cadre de l'Overworld, aller et retour à pied sans couture, sans chunk reçu ni section manquante après la bascule, rien de proche qui manque après un demi-tour, une section rendue à reconstruire l'est dans chaque renderer après chaque bascule, tampons de construction du jeu au renderer du joueur et deux à la vue qu'il remplace, monde distant libéré puis oublié par les index, renderer fermé sans erreur OpenGL, entrée à reculons dans un cadre jamais regardé sans section manquante ni chunk reçu, main au look du monde du joueur devant un cadre (bits de look du stencil relus, main affichée puis cachée) |
| `PortalFeaturesClientTests` | neuf tests | Paliers de détail de 6 à 90 blocs, objet qui traverse, troisième personne, clics à travers le cadre, écran de la baguette, vue imbriquée vers l'End et `/backrooms_state`, cri qui perturbe les néons, son entendu et occlusion à travers le cadre |

Une traversée est sans couture si l'écart entre deux images de la bascule reste sous 1,5 fois le médian des six
images voisines de chaque côté, plus 1. La marche continue douze images après la bascule, et l'image doit encore
changer : un rendu figé échoue. Après la bascule, aucun chunk ne doit arriver dans le monde du joueur (le client les
tient tous), et aucune section à dessiner ne doit manquer à moins de 48 blocs, la fin du brouillard du Level 0 : plus
loin, le renderer d'une vue devenu celui du joueur finit sa file avec ses deux tampons de construction. Après l'aller, le joueur fait demi-tour vers le
cadre d'où il vient : à moins de 48 blocs (le brouillard du Level 0), rien ne doit manquer pendant vingt images. Chaque image du relevé note aussi son temps, les
chunks reçus et le passage du ramasse-miettes, journalisés autour de la bascule. Ces temps sont gonflés par la capture
de chaque image, qui attend le processeur graphique : ils se lisent pour comparer, pas comme ceux d'une partie.

### Fin de partie

Après le dernier test, le client quitte le monde comme « Sauvegarder et quitter ». La fin de partie compte comme un
test, `end_of_game`. Elle échoue si le serveur intégré ne s'arrête pas, ou si une erreur OpenGL survient à la fermeture
des renderers. Elle échoue aussi si la partie tient encore quelque chose : monde client, mondes distants, index des
néons, copie du monde pour le son ou fenêtres du thread de calcul du son.

### Arrêt bloqué

Des gardes empêchent un lancement de rester suspendu.

| Garde | Délai | Effet |
|-------|-------|-------|
| Entrée dans le monde | 2 400 ticks | Test `entree dans le monde de test` en échec, client fermé |
| Chien de garde | 5 minutes sans progrès | Test `blocage` en échec, arrêt du client avec le code 3 |
| Sortie du monde | 3 600 ticks | `end_of_game` en échec si le serveur intégré tourne encore |
| Fermeture du client | 3 minutes après le rapport | Test `arret du client` en échec, arrêt forcé avec le code 4 |

Avant chaque arrêt forcé, le journal reçoit la pile de chaque thread. Il reçoit aussi, pour chaque dimension du serveur
intégré, les chunks à décharger et les tickets restants par type.

Un blocage reste inexpliqué. Le serveur intégré tourne sans fin dans `MinecraftServer.shutdown`, au déchargement des
chunks, sans code du mod dans sa pile. Il touche environ trois lancements sur quarante. Voir les limites connues de
[PORTAILS.md](../systemes/PORTAILS.md).

### En CI

Le client tourne sous Xvfb avec le rendu logiciel de Mesa (`LIBGL_ALWAYS_SOFTWARE=1`). Le chemin Linux et Mesa se
vérifie par la CI, décrite dans [CI.md](CI.md).

## Checkstyle

`checkstyleMain`, `checkstyleTest` et `checkstyleGametest` appliquent `config/checkstyle/checkstyle.xml` aux trois
source sets. Aucun avertissement n'est toléré (`maxWarnings = 0`). Les règles portent sur les sources ASCII, l'ordre des
imports, le journal, la Javadoc des types publics, les classes utilitaires et la mise en forme. Elles sont décrites dans
[CONTRIBUER.md](CONTRIBUER.md#style-du-code).

Les paquets d'un mod tiers ne sont dans aucun groupe d'imports et vont en dernier, après `com.gtgpuke`. Deux exceptions
sont déclarées dans `config/checkstyle/suppressions.xml` : `ModConstants` crée le logger, et le benchmark écrit ses
chiffres sur la console. Les rapports sont `build/reports/checkstyle/main.html`, `test.html` et `gametest.html`.

## Contenu du JAR

`verifyJarContents` ouvre le JAR remappé et échoue s'il contient autre chose que le mod. Seuls sont admis :

- `fabric.mod.json`, `pack.mcmeta`, `gtgs_backrooms.mixins.json` et `gtgs_backrooms.compat.mixins.json`
- le manifeste et la licence `LICENSE_gtgs_backrooms`
- `assets/gtgs_backrooms/`, `data/gtgs_backrooms/` et `data/minecraft/tags/`
- les classes de `com/gtgpuke/gtgsbackrooms/`, hors `gametest/`

Une classe tierce, un test en jeu ou une clé `jars` dans `fabric.mod.json` fait échouer le build.

## Benchmark

`benchmarkLevelZeroGeneration` est un test JUnit marqué `benchmark`, exclu de `test`. Il tourne sous le classloader de
Fabric, sans JaCoCo, et se relance à chaque appel.

```bash
./gradlew benchmarkLevelZeroGeneration
./gradlew benchmarkLevelZeroGeneration -PlevelZeroPerfBudgetMsPerChunk=2.0
```

Il mesure trois coûts sur trois graines et 169 chunks par graine, soit 507 chunks par passe.

- Le layout à secteurs froids, génération des secteurs comprise.
- Le layout à secteurs chauds.
- Le coût complet : layout et écriture des 256 colonnes sur les cinq layers.

`-PlevelZeroPerfBudgetMsPerChunk` fait échouer la tâche si le coût complet dépasse ce budget, en millisecondes par
chunk. Les chiffres de référence sont dans [LEVEL0.md](../systemes/LEVEL0.md).

## Écrire un nouveau test

Choisir la famille :

- Logique pure ou registres : test unitaire dans `src/test/java`, au même paquet que la classe testée.
- Comportement du serveur dans un vrai monde : test en jeu, classe déclarée dans l'entrée `fabric-gametest`.
- Ce que montre une image ou ce que fait le son du client : scénario du client, ajouté à `ClientTestMod`.

Règles :

- Un comportement par méthode, avec un message d'assertion en français qui dit ce qui devrait être vrai.
- Tester ce que le code fait. Pas de test par réflexion du type « le constructeur existe », sauf pour les classes
  utilitaires, qui s'ajoutent à `UtilityClassesTest`.
- Chaque instruction et chaque branche nouvelles du code mesuré doivent être prises par un test (voir
  [Couverture](#couverture)).
- Les tests de génération restent déterministes : graines fixes, zones fixes.
- Aucune classe de test dans le paquet `mixin`. Mixin refuse de charger les classes de son paquet.
- Pour simuler un clic sur un bloc sans joueur, passer un `ItemUsageContext`, dont la position est le bloc visé. Un
  `ItemPlacementContext` donne la place où l'on poserait, devant ce bloc.
