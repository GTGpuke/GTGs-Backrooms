# Architecture

GTG's Backrooms est un seul mod Fabric, d'identifiant `gtgs_backrooms`. Son code vit sous le paquet
`com.gtgpuke.gtgsbackrooms` : deux points d'entrée et quatre paquets, `core/`, `backrooms/`, `compat/` et `mixin/`.

## Stack

Le mod vise Minecraft 1.21.1 avec les mappings Yarn, sur Fabric Loader et Fabric API, en Java 21. Il se construit avec
Gradle et Fabric Loom. Les versions exactes sont dans [INSTALLATION.md](../dev/INSTALLATION.md).

À l'exécution, le mod ne dépend que de Fabric Loader et de Fabric API. Le JAR n'embarque aucune dépendance ni aucun
test, ce que vérifie la tâche `verifyJarContents` ([TESTS.md](../dev/TESTS.md)).

## Points d'entrée

| Classe | Déclarée dans `fabric.mod.json` | Rôle |
|--------|---------------------------------|------|
| `GtgsBackroomsMod` | `main` | Enregistre le contenu, branche les hooks serveur et lance les portails |
| `GtgsBackroomsClientMod` | `client` | Branche le rendu, l'audio, le Level 0 et les portails côté client |

Deux fichiers déclarent les mixins. `gtgs_backrooms.mixins.json` couvre le paquet `mixin/`. Le fichier
`gtgs_backrooms.compat.mixins.json` couvre ceux des modules de compatibilité, filtrés par `CompatMixinPlugin`.

## Paquets

### `core/` : le socle

| Paquet | Contenu |
|--------|---------|
| `core/` | `ModConstants`, qui tient l'identifiant, le nom et le `LOGGER`, et `ModConfig`, qui lit les options `-D` ([OPTIONS.md](../reference/OPTIONS.md)) |
| `core/registry/` | Un registre par type : `ModBlocks`, `ModItems`, `ModBlockEntities`, `ModEntities`, `ModDataComponents`, `ModSounds`, `ModChunkGenerators`, `ModCreativeTab`, `ModNetworking` |
| `core/perf/` | Moniteur de performance, actif sur option ; son côté client et la commande `/backrooms_state` dans `client/` |
| `core/portal/` | Les portails, détaillés plus bas et dans [PORTAILS.md](PORTAILS.md) |
| `core/render/` | Le moteur de shaders : `patch/`, `profile/`, `state/`, `light/`, `post/`, `fbo/`, `quality/`, `client/` ([RENDU.md](RENDU.md)) |
| `core/audio/` | Le moteur audio : `profile/`, `acoustic/`, `cue/`, `client/` ([AUDIO.md](AUDIO.md)) |

Les portails sont la base des deux moteurs. Leurs sous-paquets se répartissent ainsi.

| Sous-paquet de `core/portal/` | Contenu |
|-------------------------------|---------|
| `block/` | Bloc portail et sa block entity, réglages `PortalSettings`, ciblage de l'arrivée par `PortalTargets` |
| `item/` | Baguette de portail (`PortalWandItem`) |
| `entity/` | Entité portail, registre serveur des portails chargés, orientations (`PortalFacing`) |
| `transition/` | Téléportation, préchargement de l'arrivée, collision à travers le cadre, garde d'une arrivée au sol |
| `sync/` | Payloads et suivi, côté serveur, de ce que chaque joueur voit à travers les cadres |
| `interaction/` | Coups, minage et pose à travers le cadre, côté serveur |
| `propagation/` | Regard et poursuite des mobs à travers les portails |
| `light/` | Lumière des blocs transmise par les portails, en sources virtuelles |
| `debug/` | Commande `/backrooms portal`, enregistrée en développement seulement |
| `client/` | Rendu des vues, mondes distants, traversée prédite, sons au cadre, écran de la baguette |

### `backrooms/` : le contenu

| Paquet | Contenu |
|--------|---------|
| `backrooms/` | `BackroomsConstants` : clé de la dimension du Level 0, identifiants, taille d'une cellule |
| `backrooms/level0/` | `LevelZeroArrival` pour l'arrivée d'un portail, `LevelZeroRoomShape` pour la forme des salles, `LevelZeroLayoutSync` et `LevelZeroLayoutPayload` pour la graine du layout envoyée au client |
| `backrooms/level0/biome/` | Biomes cosmétiques du Level 0 |
| `backrooms/level0/block/` | Mur adaptatif (`LevelZeroWallpaperBlock`) |
| `backrooms/level0/generation/` | Générateur de chunks et ses étapes `coord/`, `noise/`, `sector/`, `layout/`, `write/` ([LEVEL0.md](LEVEL0.md)) |
| `backrooms/level0/client/` | Effets de dimension du Level 0 et forme de ses salles donnée au moteur audio |
| `backrooms/level0/client/model/` | Modèle du mur adaptatif, avec plinthes, interrupteurs et prises |

### `compat/` : les modules de compatibilité

| Paquet | Contenu |
|--------|---------|
| `compat/` | `CompatClient`, seul point d'entrée client des modules, et `CompatMixinPlugin`, qui filtre leurs mixins |
| `compat/iris/` | Module du mod de shaders Iris : dans une dimension à look, le rendu du mod remplace le pack ([RENDU.md](RENDU.md)) |

Un module ne s'applique que si son mod est présent, dans la plage de versions vérifiée. Son mod est compilé en
`modCompileOnly`, jamais embarqué ni requis. Hors de `compat/`, le code ne nomme aucun autre mod.

### `mixin/` : les points d'accroche dans vanilla

| Paquet | Ce qu'il touche |
|--------|-----------------|
| `mixin/` | Relais des blocs, sons et particules vers les vues, chunks déjà tenus après une traversée, collision à travers le cadre, mobs qui poursuivent à travers un portail, lumière virtuelle, accesseurs |
| `mixin/client/` | Rendu des portails, découpe et patch des shaders, lightmap et brouillard du profil, monde distant, clics à travers le cadre, caméra, acoustique et replacement des sons, particules des vues allégées |

## Où ranger quoi

Les chemins de données partent de `src/main/resources/assets/gtgs_backrooms/`.

| Ajout | Classe ou donnée | Enregistrement |
|-------|------------------|----------------|
| Bloc, item ou entité d'un niveau | `backrooms/<niveau>/block/`, `item/` ou `entity/` | `core/registry/ModBlocks`, `ModItems` ou `ModEntities` |
| Objet d'un système du socle | `core/<système>/block/`, `item/` ou `entity/`, comme la baguette dans `core/portal/item/` | Idem |
| Block entity | À côté de son bloc | `core/registry/ModBlockEntities` |
| Composant de données | Paquet de son système, comme `PortalSettings` dans `core/portal/block/` | `core/registry/ModDataComponents` |
| Son | Aucune classe | `core/registry/ModSounds`, déclaré avec son sous-titre dans `sounds.json` |
| Payload réseau | Paquet de son système, comme `core/portal/sync/`, ou de son contenu, comme `backrooms/level0/` | `core/registry/ModNetworking` |
| Générateur de chunks | `backrooms/<niveau>/generation/` | `core/registry/ModChunkGenerators` |
| Système transverse | `core/<système>/` | Aucun |
| Look d'une dimension | Donnée `look_profiles/<dimension>.json` | Chargé avec les ressources par `LookResources` |
| Effets de dimension d'un niveau | `backrooms/<niveau>/client/` | Sous l'identifiant `effects` de son type de dimension |
| Patch de shaders vanilla | Donnée `shader_patches/<nom>.json`, bibliothèques GLSL dans `shaders/include/` | Chargé par `LookResources` |
| Programme de shaders du mod | Donnée `shaders/core/<nom>.json` | Chargé avec les shaders vanilla par `LookPrograms` |
| Framebuffer d'une passe | Donnée `framebuffers/<nom>.json` | Chargé par `LookResources` |
| Acoustique d'une dimension | Donnée `audio_profiles/<dimension>.json` | Chargée par `AudioResources` |
| Forme générée d'un niveau | `backrooms/<niveau>/client/` | `GeneratedAcoustics.register`, pour un profil `"acoustics": "generated"` |
| Lien son-image | Donnée `sound_cues/<nom>.json` | Chargé après les sons par `CueResources` |
| Mixin | `mixin/` ou `mixin/client/` | `gtgs_backrooms.mixins.json` |
| Module de compatibilité | `compat/<mod>/`, ses mixins dans `compat/<mod>/mixin/` | `gtgs_backrooms.compat.mixins.json` et `CompatClient` |
| Option | Champ de `core/ModConfig` | Son `-P` dans `passthroughProperties` de `build.gradle`, sa ligne dans [OPTIONS.md](../reference/OPTIONS.md) |

Un paquet naît avec son premier code. Le premier item d'un niveau crée donc son paquet `item/`.

## Dépendances entre paquets

Le contenu s'appuie sur le socle. Le socle ne connaît du contenu que ce qu'il enregistre dans `core/registry/` et
l'endroit où mènent ses portails. `PortalTargets` reconnaît le Level 0 à sa clé de dimension, tirée de
`BackroomsConstants`. Il demande alors au Level 0 où arriver, par `LevelZeroArrival`. Un nouveau niveau se branche au
même endroit, sans autre lien.

Les moteurs de rendu et d'audio consomment l'API des portails sans en réécrire la logique. Ils passent par des points
d'accès publics.

- `PortalStencilRenderer` et `ShaderPackPortalRenderer` empilent le look de chaque vue et écrivent ses bits dans le
  stencil.
- L'évènement `ClientPortalCrossing.WORLD_SWITCHED` signale chaque bascule de monde.
- `PortalSound` et `SoundAnchors` rejouent un son au cadre et le replacent après une traversée.

Le contenu configure ces moteurs par des données : `look_profiles/`, `audio_profiles/` et `sound_cues/`. Il enregistre
lui-même ce qui le concerne, comme `LevelZeroEffects` et `LevelZeroAcoustics`.

## Cycle de vie

1. Fabric charge le mod et applique ses mixins. Ceux des portails ne font rien si les portails sont coupés. Ceux d'un
   module de compatibilité ne s'appliquent que si son mod est là.
2. `onInitialize()` s'exécute côté client comme côté serveur.
   - Le contenu s'enregistre dans cet ordre : générateurs de chunks, composants, sons, blocs, block entities, entités,
     items, onglet créatif, payloads. Le récepteur des réglages de la baguette suit, car la baguette se règle même
     portails coupés.
   - Les hooks serveur se branchent : moniteur de performance, cache des secteurs du Level 0 vidé à l'arrêt, graine du
     layout envoyée à chaque joueur qui se connecte.
   - Le registre serveur des portails démarre toujours, pour retirer les portails orphelins. Si les portails sont
     actifs, le préchargement, le suivi des vues, la téléportation, les sorties fugaces, l'interaction et la lumière
     transmise démarrent aussi. En développement, la commande `/backrooms portal` s'enregistre.
3. `onInitializeClient()` s'exécute sur le client seulement.
   - Il branche le moniteur de performance côté client, la commande `/backrooms_state` et le modèle du mur adaptatif.
   - Le moteur de shaders charge ses ressources, la face des néons, ses programmes et l'index des néons.
   - Le moteur audio charge ses profils et ses liens son-image, puis prépare la copie du monde pour ses rayons.
   - Le Level 0 enregistre ses effets de dimension et la forme de ses salles.
   - Les portails branchent l'écran de la baguette, le renderer de l'entité, leurs récepteurs et la traversée.
     Les modules de compatibilité s'initialisent ensuite.
   - Un tick client fait avancer portails, mondes distants, lumière et audio. Hors partie, ce tick oublie tout l'état
     des portails et des moteurs.
4. Au chargement d'un monde, le datapack du mod déclare la dimension du Level 0. Elle référence le codec du générateur
   `gtgs_backrooms:backrooms_level_zero`.

## Séparation client et serveur

Le mod tourne aussi sur un serveur dédié. Tout code qui importe une classe `net.minecraft.client` vit donc dans un
sous-paquet `client/`, dans `mixin/client/` ou dans un module `compat/<mod>/`. Les mixins client sont déclarés dans la
section `client` de leur fichier, et ceux des modules de compatibilité le sont tous.

Hors de `client/`, les paquets de `core/render/` et `core/audio/` sont de la logique pure, sans classe client. Elle se
teste sans contexte graphique ni sonore.

Le code commun couvert par les tests ne choisit pas son côté par `world.isClient()`. Le serveur de test n'a pas de
client, et une branche client n'y serait jamais prise. Le côté passe par `PortalCollision.Worlds.side`, que le client
implémente (`ClientPortalIndex`).

## Conventions de structure

- Chaque registre de `core/registry/` tient un champ `static final` par entrée. Sa méthode `register()` ne fait que
  forcer le chargement de la classe.
- Un objet se range dans le paquet de son domaine, selon le tableau [Où ranger quoi](#où-ranger-quoi).
- Pas de dossier vide, pas de `.gitkeep`, pas de `package-info.java` d'une ligne.
- Pas de couche d'abstraction sans deuxième implémentation réelle.
- Les tests unitaires de `src/test/java/` suivent l'arborescence des paquets du mod.
- Les tests en jeu vivent dans `src/gametest/java/com/gtgpuke/gtgsbackrooms/gametest/`, rangés par domaine :
  `support/`, `backrooms/level0/`, `core/portal/`, `client/`. Ils forment le mod `gtgs_backrooms_gametest`, jamais
  embarqué dans le JAR.
- Aucune classe de test dans le paquet `mixin`, car Mixin refuse de charger les classes de son paquet.

Les identifiants suivent une forme fixe.

| Élément | Forme | Exemple |
|---------|-------|---------|
| Identifiant de registre | `snake_case` | `level_zero_wallpaper_adaptive` |
| Clé de langue d'un bloc, reprise par son item | `block.gtgs_backrooms.<id>` | `block.gtgs_backrooms.level_zero_wallpaper` |
| Clé de langue d'un item sans bloc | `item.gtgs_backrooms.<id>` | `item.gtgs_backrooms.portal_wand` |
| Clé de langue d'une entité | `entity.gtgs_backrooms.<id>` | `entity.gtgs_backrooms.backrooms_portal` |
| Son | `<catégorie>.<objet>.<son>` | `block.neon.hum` |
| Sous-titre d'un son | `subtitles.gtgs_backrooms.<son>` | `subtitles.gtgs_backrooms.block.neon.hum` |

Les règles d'écriture du code, comme l'ordre des imports ou la langue des commentaires, sont dans
[CONTRIBUER.md](../dev/CONTRIBUER.md).
