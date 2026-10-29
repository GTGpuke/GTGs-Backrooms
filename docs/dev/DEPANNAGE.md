# Dépannage

## Build

### `ClassNotFoundException` sur toutes les classes de test, sous Windows

Cause : le chemin du projet contient un accent, comme `Développement`. Java 21 écrit le fichier d'arguments du
classpath en UTF-8 et le relit en CP1252.

Solution : `-Dfile.encoding=COMPAT` dans `org.gradle.jvmargs`, déjà présent dans `gradle.properties`. Si le problème
revient, lancer `./gradlew --stop`, puis `./gradlew test`.

### `Dependency requires at least JVM runtime version X`

Cause : une version de Loom plus récente exige une JVM plus récente pour Gradle lui-même. Loom 1.17 tourne sur
Java 21.

Solution : ne monter `loom_version` qu'avec le JDK qu'il demande.

### `Plugin fabric-loom requires at least Gradle X`

Cause : le wrapper Gradle est trop ancien pour cette version de Loom.

Solution : mettre à jour `distributionUrl` dans `gradle/wrapper/gradle-wrapper.properties`.

### Avertissement javac inattendu

Cause : le build compile en `-Xlint:all`, sauf `processing`, `serial` et `classfile`. Ce dernier est coupé parce que
les jars de Guava et de Minecraft citent des annotations ErrorProne absentes.

Solution : corriger tout autre avertissement. Le build complet doit passer sans aucun.

### Le client plante au lancement : `java.lang.Error: Unresolved compilation problems`

Cause : l'extension Java de VS Code a écrit une classe en erreur dans `build/classes/java/main`, par-dessus la sortie
de Gradle. Son modèle de projet est périmé, ce que montrent les « cannot be resolved » du panneau Problems. Le
pourquoi est détaillé dans [INSTALLATION.md](INSTALLATION.md).

Solution : `./gradlew runClient` recompile avec javac. Si une classe douteuse reste, lancer
`./gradlew compileJava --rerun-tasks`. Pour retrouver un modèle sain, exécuter « Java: Clean Java Language Server
Workspace », puis réimporter le projet Gradle avec l'extension sur un JDK 21.

## Jeu

### Crash au chargement d'un monde : `Failed to load registries`

Cause : un JSON de génération du monde est invalide. Par exemple, `below_trunk_provider` n'existe pas en 1.21.1.

Solution : lire les lignes `ERROR` qui précèdent dans `run/logs/latest.log`. Un `minecraft:tree` exige
`dirt_provider`.

### Le mod n'apparaît pas

Cause : il manque le JAR, Fabric Loader, Fabric API ou Java 21.

Solution : JAR dans `mods/`, Fabric Loader 0.16.9 ou plus récent, Fabric API pour 1.21.1, Java 21.

### Texture violette sur un item

Cause : le modèle `assets/gtgs_backrooms/models/item/<id>.json` manque, ou son chemin de texture est faux. En 1.21.1,
le dossier `assets/<ns>/items/` n'est pas lu, car ce format n'existe qu'à partir de 1.21.4.

Solution : écrire le modèle d'item. `ModRegistriesTest` échoue si un modèle manque.

### Les plinthes autonomes d'un ancien monde ont disparu

Cause : le bloc `level_zero_baseboard` a été retiré du mod en 0.8.0 (voir [CHANGELOG.md](../../CHANGELOG.md)). Les
plinthes dessinées sur les murs ne sont pas concernées.

Solution : aucune, le bloc n'existe plus.

## Portails

### Le bloc portail ne fait rien

Cause possible : la fonction des portails est coupée par `-Dgtgsbackrooms.portal.enablePortalFeature=false`, ou
`-PportalFeature=false` en développement. Le bloc reste alors inerte.

Cause possible : le bloc n'a pas encore trouvé de destination. Hors du Level 0, la dimension
`gtgs_backrooms:backrooms_level_0` doit exister, donc le datapack du mod doit être chargé. Le bloc réessaie toutes les
cinq secondes.

Solution : rétablir l'option ou le datapack. Le bloc est invisible et ne se vise qu'accroupi, pour le casser par
exemple.

### La baguette refuse de poser un portail

Cause : le cadre, à la taille réglée, traverserait un bloc. Ou la place est prise par un bloc plein, ou elle est hors
du monde.

Solution : dégager la place ou réduire la taille dans l'écran de la baguette.

### La lumière ne passe pas par le portail

Cause : le portail est réglé sans lumière, fermé ou invisible. Ou la lumière vient du ciel : seule celle des blocs
passe, d'un niveau de moins que de l'autre côté.

Solution : activer la lumière dans l'écran de la baguette, puis reposer le portail.

### Le portail reste un cadre noir

Cause : le monde distant n'est pas encore arrivé, car les chunks partent par lots de 8 toutes les 5 ticks. Ou le chunk
d'arrivée est hors de la fenêtre synchronisée, pour un portail imbriqué lointain.

Solution : si cela dure, chercher `[Portal]` dans le journal. Un stencil refusé par le pilote ou une exception dans le
rendu distant coupe ce rendu pour la session (« Rendu distant coupe pour la session »). `-PportalDebugRendering=true`
écrit chaque seconde l'état de chaque vue visible : chunks, entités, renderer.

### Les entités de l'autre côté manquent ou restent figées

Cause : elles n'arrivent qu'avec la fenêtre de chunks synchronisée autour de l'arrivée. Hors de cette fenêtre, rien
n'est envoyé. Si elles apparaissent mais restent figées, le tick des mondes distants n'est plus appelé.

Solution : `-PportalDebugRendering=true` donne le nombre d'entités de chaque vue. Vérifier que `GtgsBackroomsClientMod`
appelle encore `RemoteWorldRegistry.tickRemoteWorlds`.

### Le joueur est renvoyé d'un côté à l'autre d'un portail

Cause : deux blocs portail se recouvrent. L'un est posé au point d'arrivée de l'autre, et chaque arrivée retombe dans
le cadre de l'autre.

Solution : décaler ou casser l'un des deux. Voir les limites connues de [PORTAILS.md](../systemes/PORTAILS.md).

### Des portails orphelins traînent dans un monde

Cause : leur bloc a disparu. Ils se retirent seuls dès que le chunk de leur bloc est chargé et que le bloc n'y est
plus.

Solution : en tant qu'opérateur, `/backrooms portal list` les liste et `/backrooms portal clear` les retire tous. Les
portails d'un bloc encore présent sont reposés par leur bloc : `clear` ne retire durablement que les portails libres.

### Le jeu gèle sans message

Cause connue : un `Frustum` nourri d'une projection oblique boucle sans fin dans `coverBoxAroundSetPosition`. Les vues
de portail ne lui en donnent jamais (voir [PORTAILS.md](../systemes/PORTAILS.md)).

Solution : prendre une pile d'exécution du client avant de le tuer. `jps -l` donne le PID de la ligne
`devlaunchinjector`, puis `jstack <PID> > dump.txt` écrit la pile. Le thread `Render thread` montre où ça boucle.

## Régressions du code des portails

Ces symptômes viennent d'une modification du code des portails. La solution désigne le code à revoir.

### Déconnexion « Network Protocol Error » en traversant

Cause : un chunk du nouveau monde a été appliqué à l'ancien `ClientWorld`. Un récepteur de payload a été différé
derrière les paquets vanilla.

Solution : les récepteurs client des portails restent synchrones, sans `client.execute` (voir
`RemoteSyncReceiver.init`).

### Objets de la barre ou de la main teintés quand un portail est à l'écran

Cause : le brouillard d'une vue est resté actif après le rendu des portails. Les objets de l'interface, dessinés en
profondeur, en prennent la couleur.

Solution : `PortalStencilRenderer` garde le brouillard du monde du joueur avant les vues (`saveMainFog`). Il doit le
rendre sur tous les chemins, exception comprise (`restoreMainFog`).

### Ciel noir à travers un portail vers une autre dimension

Cause : le dôme du ciel se dessine avec le shader courant, et son tampon ne porte que des positions. Si le dernier
shader posé attend aussi une couleur, celle-ci vaut noir.

Solution : `RemoteViewRenderer.render` pose `GameRenderer::getPositionProgram` avant `renderSky`. L'heure et la météo
ne sont pas en cause.

### Saccade ou recul à l'arrivée, élan perdu

Cause : le serveur a renvoyé une téléportation vanilla après une traversée prédite par le client. Elle ramène le
joueur un tick en arrière, et le client remet sa vitesse à zéro.

Solution : une traversée prédite passe par `placePlayer(..., true)` dans `TeleportExecutor`. Seul le secours du
serveur téléporte à la manière vanilla.

## Tests

### `Registry is already frozen`

Cause : un test enregistre du contenu sans passer par `MinecraftTestBootstrap.ensure()`, ou il est lancé hors de
Gradle, sans `fabric-loader-junit`.

Solution : ajouter le bootstrap et lancer par `./gradlew test`.

### `ExceptionInInitializerError` dans `DataComponentTypes` ou `Blocks`

Cause : le test touche à Minecraft sans bootstrap.

Solution : appeler `MinecraftTestBootstrap.ensure()` dans un `@BeforeAll`.

### `LevelZeroGoldenTest` échoue

Cause : la génération du Level 0 a changé.

Solution : corriger une régression, ou recopier les empreintes d'une évolution voulue et l'annoncer dans le CHANGELOG.
Voir [TESTS.md](TESTS.md#empreintes-et-vecteurs-communs).

### `runGameTest` échoue ou ne se termine pas

Cause : un test a échoué, ou le serveur de test a planté.

Solution : les messages d'échec sont dans `build/junit.xml`, le journal dans `build/run/gameTest/logs/latest.log`. Un
serveur planté n'écrit pas `build/junit.xml`. Le dossier de run n'est vidé que par `clean` : un monde déjà joué peut
garder une entité d'un lancement précédent.

### `verifyClientTests` : aucun rapport, ou aucun test joué

Cause : le client de test n'a pas démarré, ou `-PclientTestOnly` ne nomme aucun test existant.

Solution : lire `build/run/clientTest/logs/latest.log`. Les noms valides sont ceux de `build/client-junit.xml`, comme
`wand_screen`.

### Un test du client échoue sur une image

Cause : l'image ne montre pas ce que le test attend.

Solution : l'image fautive est gardée dans `build/run/clientTest/captures/`. Rejouer ce seul test avec
`-PclientTestOnly=<nom>`, et `-PclientTestVisible=true` pour voir la fenêtre.

### Le client de développement plante au lancement sur un mixin

Cause : un `@Shadow` vise un membre hérité, ou la cible d'un mixin ne se charge pas.

Solution : `./gradlew test` lance `MixinTargetsTest`, qui charge chaque cible et nomme le mixin fautif.
