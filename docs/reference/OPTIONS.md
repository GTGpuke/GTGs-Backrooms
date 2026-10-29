# Options

Les options du mod sont des propriétés système Java, lues par `core/ModConfig`. Chacune a une valeur par défaut
jouable : un joueur n'a rien à régler.

Au client de développement, une option passe par son nom Gradle :

```bash
./gradlew runClient -PportalLod=false
```

Avec le JAR installé, la propriété s'ajoute aux arguments de la JVM, par exemple dans le profil du lanceur. Un serveur
dédié la reçoit sur sa ligne de commande :

```bash
java -Dgtgsbackrooms.portal.lod=false -jar <serveur>.jar
```

## Général

Les options se lisent une fois, au démarrage. En changer demande donc de relancer le jeu ou le serveur.

| Type | Valeur lue | Valeur absente ou vide |
|------|------------|------------------------|
| Booléen | `true` active, quelle que soit la casse, et toute autre valeur coupe | Défaut |
| Entier | Nombre décimal ; une valeur illisible prend le défaut | Défaut |
| Texte | Valeur sans ses espaces de début et de fin | Défaut |

En solo, le serveur intégré partage la JVM du client : une seule ligne suffit. Sur un serveur dédié, une option du
Level 0 ou des portails compte côté serveur, côté client ou des deux côtés. On les donne donc au serveur et aux
clients, avec les mêmes valeurs. Les options du rendu et de l'audio ne comptent que sur le client.

Les noms `-P` de `passthroughProperties`, dans `build.gradle`, passent aux tâches `runClient`, `runClientTest` et
`benchmarkLevelZeroGeneration`. Ils ne passent ni à `runServer` ni à `runGameTest`. D'autres options Gradle n'ont pas
de propriété du mod.

| Option Gradle | Effet |
|---------------|-------|
| `-PquickPlayWorld=<monde>` | `runClient` charge directement ce monde solo |
| `-PclientTestOnly=<test1>,<test2>` | `runClientTest` ne joue que ces tests |
| `-PclientTestVisible=true` | `runClientTest` garde la fenêtre du client visible |
| `-PlevelZeroPerfBudgetMsPerChunk=<ms>` | Le benchmark du Level 0 échoue au-delà de ce coût moyen par chunk |

Les tâches de test posent elles-mêmes quelques propriétés.

| Tâche | Propriété | Valeur | Raison |
|-------|-----------|--------|--------|
| `runGameTest` | `gtgsbackrooms.portal.teleportCooldownTicks` | `3` | Les tests en jeu exercent le délai entre deux traversées, nul par défaut |
| `runGameTest` | `fabric-api.gametest.report-file` | `build/junit.xml` | Rapport des tests en jeu, propriété de Fabric API |
| `runClientTest` | `gtgsbackrooms.portal.remoteWorldReleaseSeconds` | `5` | Un monde distant sans portail se libère au plancher du délai |
| `runClientTest` | `gtgsbackrooms.look.verifyNeonShader` | Chemin de `src/test/resources/neon_schedule_vectors.json` | Le client confronte le jumeau GLSL du calendrier des néons aux vecteurs communs |
| `runClientTest` | `gtgsbackrooms.clientTest.report` | `build/client-junit.xml` | Rapport des tests du client |

Les tests eux-mêmes sont décrits dans [TESTS.md](../dev/TESTS.md).

## Level 0

| Propriété `-D` | Option `-P` | Défaut | Effet |
|----------------|-------------|--------|-------|
| `gtgsbackrooms.levelZero.noiseGeometry` | `-PlevelZeroNoiseGeometry` | `true` | Active les resserrements rares des couloirs droits, à un bloc de large. |
| `gtgsbackrooms.levelZero.debugMicroGeometry` | `-PlevelZeroDebugMicroGeometry` | `false` | Montre les resserrements : sol en béton blanc, blocs fermés en béton gris. |
| `gtgsbackrooms.levelZero.debugWallProps` | `-PlevelZeroDebugWallProps` | `false` | Pose chaque interrupteur en bloc vert et chaque prise en bloc rouge, d'une nuance par face. |

Ces trois options agissent sur les chunks au moment où ils se génèrent. Les blocs ainsi posés restent dans le monde :
les options de débogage sont à réserver à un monde de test. Coupée, `noiseGeometry` donne une autre génération que
celle que verrouillent les empreintes ([LEVEL0.md](../systemes/LEVEL0.md#génération-verrouillée)).

## Portails

| Propriété `-D` | Option `-P` | Défaut | Effet |
|----------------|-------------|--------|-------|
| `gtgsbackrooms.portal.enablePortalFeature` | `-PportalFeature` | `true` | Active les portails ; coupés, le bloc portail reste inerte et les mixins ne font rien. |
| `gtgsbackrooms.portal.teleportCooldownTicks` | `-PportalTeleportCooldownTicks` | `0` | Ticks d'attente d'un joueur entre deux traversées. |
| `gtgsbackrooms.portal.activationRadiusBlocks` | `-PportalActivationRadiusBlocks` | `16` | En deçà de cette distance, un portail compte quelle que soit la direction du regard. |
| `gtgsbackrooms.portal.preloadRadiusChunks` | `-PportalPreloadRadiusChunks` | `3` | Rayon, en chunks, de la fenêtre chargée et synchronisée autour de chaque arrivée. |
| `gtgsbackrooms.portal.renderDistanceBlocks` | `-PportalRenderDistanceBlocks` | `64` | Distance au-delà de laquelle une vue de portail ne se rend plus. |
| `gtgsbackrooms.portal.remoteWorldReleaseSeconds` | Aucune | `60` | Secondes sans portail à portée avant que le serveur ne fasse libérer au client le monde distant d'une dimension. |
| `gtgsbackrooms.portal.occlusion` | `-PportalOcclusion` | `true` | Saute le dessin d'une vue dont le cadre est caché derrière des blocs pleins et opaques. |
| `gtgsbackrooms.portal.lod` | `-PportalLod` | `true` | Active les paliers de détail des vues lointaines. |
| `gtgsbackrooms.portal.shaderCohabitation` | `-PportalShaderCohabitation` | `auto` | Sous un pack de shaders, `auto` dessine les vues par le pack et `off` laisse les cadres sombres. |
| `gtgsbackrooms.portal.debugRendering` | `-PportalDebugRendering` | `false` | Écrit chaque seconde dans le journal l'état GL et une ligne par portail visible. |

Quelques précisions :

- Portails coupés, la baguette pose encore des blocs portail, inertes. Le type de l'entité portail reste enregistré, et
  un monde qui contient des portails se charge toujours.
- Les objets et les mobs gardent un délai fixe de 10 ticks entre deux traversées, quelle que soit l'option.
- Au-delà de `activationRadiusBlocks`, le rendu ne dessine que les portails devant le joueur. Le serveur précharge et
  synchronise l'arrivée jusqu'à la plus grande des deux distances, même dans le dos du joueur.
- Avec le rayon par défaut, la fenêtre fait 7 × 7 chunks. À moins de 8 blocs du portail, la fenêtre synchronisée
  s'élargit de 2 chunks.
- Au-delà de `renderDistanceBlocks`, une vue garde au plus l'image figée prise de plus près, jusqu'à 128 blocs.
- `remoteWorldReleaseSeconds` ne descend jamais sous 5 secondes. Une très grande valeur revient à ne jamais libérer.
  Après la libération, un retour vers le portail reconstruit la vue, le temps de renvoyer la fenêtre.
- Une vue cachée par l'occlusion garde son terrain à jour. Les options `occlusion` et `lod` se coupent pour comparer
  l'image avant et après.
- Les paliers de détail allègent une vue, simplifient son terrain, puis la figent en image, selon la taille d'un bloc à
  l'écran. Coupée, `lod` garde toute vue en pleine qualité.
- Pour `shaderCohabitation`, toute valeur autre que `off` vaut `auto`.

Le fonctionnement des portails est dans [PORTAILS.md](../systemes/PORTAILS.md).

## Rendu

| Propriété `-D` | Option `-P` | Défaut | Effet |
|----------------|-------------|--------|-------|
| `gtgsbackrooms.look.enabled` | `-PlookEnabled` | `true` | Active le moteur de shaders ; coupé, rien n'est patché ni chargé et le rendu reste vanilla partout. |
| `gtgsbackrooms.look.replaceShaderPack` | `-PlookReplaceShaderPack` | `true` | Sous un pack de shaders, dans une dimension à look, remplace le rendu du pack par celui du mod. |
| `gtgsbackrooms.look.quality` | `-PlookQuality` | `auto` | Qualité du moteur : `light`, `full` ou `auto`. |
| `gtgsbackrooms.look.reduceNoise` | `-PreduceNoise` | `false` | Confort : retire du look le grain, le vacillement de la lumière de l'écran et l'aberration chromatique. |
| `gtgsbackrooms.look.debugStencil` | `-PlookDebugStencil` | `false` | Remplace l'image par le stencil, une couleur par niveau d'imbrication. |
| `gtgsbackrooms.look.checkGlState` | `-PlookCheckGlState` | `false` | Vérifie que chaque passe du moteur rend l'état GL tel qu'elle l'a trouvé. |
| `gtgsbackrooms.look.verifyNeonShader` | `-PlookVerifyNeonShader` | Vide | Chemin des vecteurs communs du calendrier des néons, à confronter au jumeau GLSL. |

Quelques précisions :

- `replaceShaderPack` coupée, le pack rend aussi les Backrooms et le look se pose sur son image.
- La qualité `light` éclaire par huit néons par vue, sans bloom. La qualité `full` éclaire par trente-deux néons, avec
  le bloom.
- En `auto`, une carte graphique intégrée reçoit `light`, et toute autre `full`. La carte est jugée intégrée si la
  chaîne `GL_RENDERER` du pilote contient `intel`, `llvmpipe` ou `swiftshader`. Toute valeur inconnue vaut `auto`, et
  le journal note le choix.
- `reduceNoise` garde la teinte, la lumière, le brouillard, la vignette et le bloom.
- Dans `debugStencil`, les niveaux 0 à 3 sont gris, bleu, vert et rouge. Le look de chaque pixel les éclaircit et les
  jaunit.
- `checkGlState` relit l'état au pilote avant et après chaque passe, et journalise une fois chaque écart. Ces lectures
  attendent le pilote : l'option est réservée au développement.
- Avec `verifyNeonShader`, le client vérifie au premier rechargement que le jumeau GLSL suit les vecteurs. Il le coupe
  pour la session en cas d'écart.

Le fonctionnement du moteur est dans [RENDU.md](../systemes/RENDU.md).

## Audio

| Propriété `-D` | Option `-P` | Défaut | Effet |
|----------------|-------------|--------|-------|
| `gtgsbackrooms.audio.enabled` | `-PaudioEnabled` | `true` | Active le moteur audio ; coupé, les sons restent vanilla et les néons ne bourdonnent pas. |
| `gtgsbackrooms.audio.efx` | `-PaudioEfx` | `true` | Utilise l'extension EFX d'OpenAL ; coupée, l'occlusion se rend par le volume seul, sans réverb ni filtre. |
| `gtgsbackrooms.audio.quality` | `-PaudioQuality` | `auto` | Qualité du calcul par rayons : `light`, `full` ou `auto`. |
| `gtgsbackrooms.audio.reduceFlashes` | `-PreduceFlashes` | `false` | Confort pour les joueurs photosensibles : aucune réaction visuelle forte aux sons, aucun vacillement. |
| `gtgsbackrooms.audio.reduceBass` | `-PreduceBass` | `false` | Confort : retire une part des graves des sons émis dans une dimension à profil audio. |
| `gtgsbackrooms.audio.cameraShake` | `-PcameraShake` | `false` | Un cri lié fait trembler un peu la caméra du joueur qui est dans sa dimension. |
| `gtgsbackrooms.audio.debugRays` | `-PaudioDebugRays` | `false` | Dessine dans le monde les rayons et les chemins d'occlusion des derniers sons calculés. |
| `gtgsbackrooms.audio.compareGenerated` | `-PaudioCompareGenerated` | `false` | Lance aussi les rayons là où la forme générée suffit, et journalise l'accord des deux. |

Quelques précisions :

- Sans EFX, la liaison son-image continue.
- La qualité `light` lance seize rayons à deux rebonds, avec le seul chemin direct. La qualité `full` lance trente-deux
  rayons à quatre rebonds, avec le chemin direct et quatre détours. En `auto`, l'audio prend la qualité du moteur de
  shaders.
- `reduceFlashes` ne laisse aux réactions visuelles que des baisses douces. Elle coupe la pulsation de l'écran et le
  tremblement de la caméra. Ni la lumière de l'écran ni aucun tube ne vacille. Le son ne change pas.
- `reduceBass` retire environ 6 dB de graves : le filtre de chaque source passe de passe-bas à passe-bande. Elle exige
  EFX.
- Le tremblement de `cameraShake` n'a jamais lieu dans une dimension vanilla, ni avec `reduceFlashes`. Son amplitude
  suit l'option vanilla des effets de distorsion.
- Dans `debugRays`, un rayon jaune rebondit, un vert sort, un magenta passe par un cadre et un gris se perd hors de la
  copie du monde. Les chemins d'occlusion vont du vert au rouge selon ce qu'ils retirent.

Le fonctionnement du moteur est dans [AUDIO.md](../systemes/AUDIO.md).

## Diagnostic

| Propriété `-D` | Option `-P` | Défaut | Effet |
|----------------|-------------|--------|-------|
| `gtgsbackrooms.debugPerformanceMonitor` | `-PdebugPerformanceMonitor` | `false` | Active le moniteur de performance, dans le journal et l'écran F3. |
| `gtgsbackrooms.performanceLogIntervalSeconds` | `-PperformanceLogIntervalSeconds` | `10` | Secondes entre deux résumés du moniteur, une au moins. |

Côté serveur, un résumé donne le temps de tick, les sections les plus longues, les paquets et octets envoyés par
payload, la mémoire et la charge du processeur. Côté client, il donne les images par seconde, l'image la plus longue,
le nombre d'images lentes et les sections client les plus longues. Coupé, le moniteur ne coûte qu'un test de booléen
par appel.

Deux commandes complètent ces options.

- `/backrooms_state`, commande du client, écrit dans le journal l'état de la dernière image. Sa sortie est décrite dans
  [RENDU.md](../systemes/RENDU.md#diagnostic) et [AUDIO.md](../systemes/AUDIO.md#diagnostic).
- `/backrooms portal list` et `/backrooms portal clear` n'existent qu'en développement, au niveau de permission 2. La
  première liste les portails chargés et le nombre de chunks distants tenus par les clients. La seconde retire les
  portails chargés, mais un bloc portail repose les siens : seuls les portails libres disparaissent durablement.
