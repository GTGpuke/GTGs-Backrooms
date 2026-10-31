# Décisions

Décisions de l'auteur encore en vigueur, de la plus ancienne à la plus récente. Une décision remplacée n'apparaît que
sous sa forme finale. Un nouvel écart entre le [guide](GUIDE.md) et le code s'ajoute ici, à sa date.

## 15 octobre 2026

- Gradle tourne sous Java 21 avec Loom 1.17.21, la dernière lignée de Loom qui l'accepte. Passer à Loom 1.18 ou plus
  imposerait Java 25 au démon, CI comprise ([installation](dev/INSTALLATION.md)).
- Un test en échec fait échouer le build. Les tests unitaires tournent sous le classloader Fabric, sur la vraie
  initialisation du mod ([tests](dev/TESTS.md)).
- Le chargeur minimal déclaré reste Fabric Loader 0.16.9 (`fabricloader >= 0.16.9` dans `fabric.mod.json`).
- Le mélange des graines du Level 0 (`StageRandom.mix`) garde sa faible diffusion, car le corriger changerait tous les
  mondes existants. Aucun test ne prend donc de petits entiers consécutifs comme graines ([Level 0](systemes/LEVEL0.md)).
- Les couches mortes du Level 0 sont retirées : détails de surface, structures rares, bloc plinthe. Les structures
  manuelles repartiront de `LevelZeroSectorRoomKind`.

## 16 octobre 2026

- Les portails sont dessinés par le mod lui-même, par stencil et monde distant, plutôt que par une bibliothèque externe.
  Le prototype n'est pas repris : le système est réécrit ([portails](systemes/PORTAILS.md)).

## 18 octobre 2026

- La porte des Backrooms est retirée. Le seul cadre est le bloc portail, avec ses réglages.

## 20 octobre 2026

- Les anciennes specs, qui bâtissaient le rendu sur une bibliothèque tierce et une autre arborescence, sont retirées avec
  l'étude des dépendances. Le [guide](GUIDE.md) les remplace.
- La licence reste propriétaire, tous droits réservés. Le passage en GPL-3 est reporté jusqu'à une décision explicite.
- L'interface graphique de configuration est reportée. Les options se lisent en `-D` ([options](reference/OPTIONS.md)).
- Pas de couches `api/` et `impl/`, pas de profils en interfaces : aucune abstraction sans deuxième implémentation réelle.
- Écarté : un framebuffer par vue, composé ensuite, parce qu'il coûterait un rendu de plus par vue. Hors du chemin sous un
  pack de shaders, les vues se dessinent dans le framebuffer du jeu, sous stencil.

## 21 octobre 2026

- Le code se range en quatre paquets : `core/` pour le socle, `backrooms/` pour le contenu, `compat/` et `mixin/`. Le
  contenu de la Gang et l'Arbre Maudit sont retirés avec l'ancien paquet `feature/` ([architecture](systemes/ARCHITECTURE.md)).
- Le bloc portail n'a pas d'item. La baguette est la seule façon de créer un portail, et elle copie les réglages d'un
  portail visé.
- Le Level 0 calcule lui-même où un portail y arrive (`LevelZeroArrival`), et `PortalTargets` l'interroge.

## 22 octobre 2026

- `PortalFacing` compte douze orientations : les six axes et leurs retournées, pour une arrivée tête en bas (réglage
  `upside_down`).
- Les blocs derrière le plan d'un portail ouvert n'existent plus pour l'entité engagée dans le cadre. Un cadre se pose
  donc contre un mur ou sur un sol plein.
- Un cadre au sol est une trappe, arrivées comprises. Qui sort d'une arrivée au sol en est gardé (`ArrivalGuard`),
  garde retirée le 30 octobre 2026.
- La traversée d'un cadre couché se décide aux yeux, au sol comme au plafond.

## 23 octobre 2026

- Sans sol sûr à l'arrivée, comme un lac de lave ou le vide de l'End, le portail pose une plateforme d'obsidienne. Une
  arrivée ne se fait jamais dans des blocs.

## 25 octobre 2026

- Toute optimisation suit une règle : aucune régression d'image, de fonctionnalité ou de qualité. Seule exception, les
  paliers de détail des vues, avec la pleine qualité de près.
- Un portail posé vers une zone jamais générée attend quelques ticks que ses chunks soient prêts avant de devenir
  portail. Le serveur ne gèle plus, et la recherche d'arrivée synchrone est retirée ([portails](systemes/PORTAILS.md)).
- La fenêtre de l'autre côté reste synchronisée pour tout portail à moins de 64 blocs, quel que soit le regard. Le
  regard qui revient trouve la vue prête, au prix de la mémoire du client.
- Un monde distant qu'aucun portail à portée ne montre depuis 60 s est libéré
  (`gtgsbackrooms.portal.remoteWorldReleaseSeconds`). Ce choix est préféré à garder ses chunks plus longtemps.
- Une vue cachée derrière un mur n'est pas dessinée. La preuve se fait sur le processeur seul (`FrameOcclusion`), sans
  rendu conditionnel d'OpenGL.
- Un joueur traverse un cadre à ses yeux, quel que soit le cadre. Les quatre bords du cadre l'arrêtent comme de petites
  parois, alors que mobs, objets et projectiles gardent leur règle.
- Écarté : créer le monde distant avant le premier regard, car la fenêtre gardée à 64 blocs le fait déjà naître.

Écartés à la revue du même jour, au nom de la règle « aucune régression » :

| Écarté | Raison |
|---|---|
| Brouillard pris au tick, partagé entre les vues ou interpolé | Il change l'image et crée une couture à la bascule. Une variante approchée à 1/255 reste possible sur décision. |
| Monde quitté vidé d'un coup | Un retour rapide verrait un monde vide. |
| Création d'un monde distant étalée sur plusieurs images | La vue serait vide ou incomplète à ses premières images. |
| Budget de vues imposé d'office | Il change l'image sans décision. Il ne peut venir que par une option. |
| `loader_version` dans `fabric.mod.json` | Il relèverait le chargeur minimal de 0.16.9 à 0.19.5. |
| Démon Gradle sous Java 25 dès maintenant | Il exigerait un JDK 25 partout, CI comprise. |
| Tests en jeu joués une seule fois en CI | La robustesse sur un monde déjà joué serait perdue. |
| Pièges des règles du projet déplacés derrière un renvoi | Les sessions ne les liraient plus. |
| `UtilityClassesTest` sans les constructeurs qui refusent l'appel | Une garantie testée serait perdue. |
| Parcours des sections d'une vue en arrière-plan | L'image de retard fait clignoter les vues. |
| Chunks reçus appliqués hors du thread de rendu | Le chemin `readLightData` des mods de terrain serait rompu. |
| Requête d'occlusion lue à l'image suivante | Une image de retard au retour d'un portail dans le champ. Acceptable en option seulement. |
| Rayons vers une grille de points pour décider du rendu | Un interstice entre deux points ferait disparaître une vue visible. |
| Culling et logique des portails sur le GPU | OpenGL 4.3 manque sous macOS, et rien de lourd n'est à calculer. |
| Code natif, appels natifs de Java, API vectorielle | Aucun calcul lourd n'est à accélérer, et l'API vectorielle reste en incubation. |

## 26 octobre 2026

- Les paliers de détail d'une vue se choisissent sur la taille d'un bloc à l'écran, et remplacent les seuils du
  25 octobre. Tant qu'un bloc fait au moins 24 pixels, la vue garde la pleine qualité : on traverse donc toujours un
  cadre en pleine qualité.
- En dessous, la vue s'allège : ni météo ni particules, entités figées. Ses sections se simplifient seulement sous
  12 pixels, seuil choisi par l'auteur sur captures. Les portails imbriqués suivent les mêmes règles.
- Sous 6 pixels, la vue devient une image figée, gardée jusqu'à 128 blocs. Cette image a son propre framebuffer, par un
  écart accordé à la règle « pas de framebuffer de vue séparé » ([portails](systemes/PORTAILS.md)).
- À plus de 8 blocs d'un cadre d'arrivée debout, le serveur envoie les chunks dans l'axe de ce que le cadre montre. Le
  carré de 7 × 7 chunks autour de l'arrivée reste de près et pour une arrivée couchée.

## 27 octobre 2026

- Les specs de l'auteur pour le rendu et l'audio font foi sur le plan général du mod.
- Le rendu tient en deux étages dans le stencil existant : look injecté par fragment, puis post-traitement masqué. Pas de
  G-buffer, ni d'éclairage différé, ni d'ombres, ni de reflets. Remplace la question du G-buffer, laissée ouverte le
  20 octobre ([rendu](systemes/RENDU.md)).
- Chaque dimension des Backrooms a deux profils en données, `LookProfile` et `AudioProfile`, et l'Overworld n'en a
  aucun. Un son se traite selon sa seule dimension d'émission : un son de l'Overworld reste vanilla, même entendu par un
  cadre ([audio](systemes/AUDIO.md)).
- Le mod de shaders et le mod qui dessine le terrain restent supportés, alors que les specs les écartaient.
- La structure du dépôt prime sur celle des specs : `core/render/`, `core/audio/` plutôt que `client/audio/`, et la colle
  dans `*/client/`.
- Écart aux specs : la documentation ne nomme pas le projet de référence, car aucun autre mod n'est nommé hors de son
  module de compatibilité.
- Écart aux specs : pas de transition de look à la traversée. Le look suit le pixel et non le joueur, donc la traversée
  n'a pas de couture à masquer.
- Écart aux specs : le stencil porte sept identifiants de look (`0x1C`) au lieu d'un seul bit `0x80`, que le chemin sous
  un pack utilise déjà.
- Écart aux specs : la qualité du rendu se choisit d'après la chaîne du pilote (`GL_RENDERER`), pas par une mesure du
  temps d'image.
- Écart aux specs : le bloom n'est masqué qu'à la composition. Il peut déborder vers l'intérieur d'un cadre, jamais sur
  un pixel vanilla.
- Le vacillement et la variation des tubes sont mis de côté. Le profil du Level 0 les laisse à zéro, le code reste.
- Le moteur audio commence par l'acoustique, avec des sons vanilla par défaut. Les vrais fichiers son viendront après.

## 28 octobre 2026

- Pour l'acoustique générée du Level 0, le serveur envoie au client la seule graine du layout, jamais celle du monde
  (`LevelZeroLayoutSync`).
- Écart aux specs : la copie du monde pour les rayons se fait par sections, de façon incrémentale, plutôt que par un bloc
  copié autour de l'auditeur.
- Le moteur audio cède la place à un autre moteur acoustique quand il trouve ses objets EFX dans le contexte, sans
  chercher son identifiant de mod.
- Sous un pack de shaders, une vue compte pour l'écran entier dans le plafond de clignotement. C'est plus prudent,
  jamais plus permissif.
- Un portail vu à travers un autre ouvre son propre monde, même un troisième ([portails](systemes/PORTAILS.md)). Le
  serveur synchronise aussi son arrivée par `PortalViewTracker.seenThroughAnother`, une extension bornée à un niveau
  d'imbrication.
- L'option de cohabitation avec un pack s'appelle `gtgsbackrooms.portal.shaderCohabitation`, avec `auto` ou `off`. Le
  mode « vues toujours vanilla » n'existe pas, car deux pipelines dans une image dessinent le terrain en éclats.

## 29 octobre 2026

- Les modules de compatibilité sont permis. Un autre mod ne se nomme que dans son module `compat/<mod>/`, ses messages et
  la documentation qui le décrit. Précise la décision du 28 octobre, qui ne permettait de nommer aucun autre mod.
- Aucune dépendance de mod à l'exécution hors Fabric Loader et Fabric API. Un pack de shaders se reconnaît sans nommer
  de mod (`ShaderPackState`).
- Sous un pack, les Backrooms se voient avec le rendu du mod. Tant que le joueur est dans une dimension à look, le mod de
  shaders rend son pipeline vanilla pour toute l'image, vues comprises (`compat/iris`, option
  `gtgsbackrooms.look.replaceShaderPack`).
- Cette bascule remplace l'essai du 27 octobre, qui donnait le pipeline vanilla à la seule dimension du Level 0. Il a été
  retiré, car une vue mêlait alors deux pipelines dans une image ([rendu](systemes/RENDU.md)).
- Pas de `breaks` dans `fabric.mod.json` : un mod en conflit se rend compatible, il n'est jamais bloqué. Le pack de
  shaders cède seulement la place dans les Backrooms, et revient dans les mondes vanilla.
- Les lumières dynamiques transmises par un cadre sont retenues, entre deux dimensions des Backrooms seulement. Elles
  restent à réaliser.
- Le budget dynamique reste une piste, sans utilité aujourd'hui.
- Le second niveau d'imbrication est abandonné. Un portail dans un portail suffit.
- Tout le code doit être couvert, en tests unitaires, d'intégration et de non-régression, plutôt que par la seule
  vérification des shaders en CI. La règle qui excluait la colle mince de la couverture est remplacée
  ([tests](dev/TESTS.md)).
- L'image figée après une traversée sous Mesa est mise de côté, à reprendre plus tard.
- Pas de Docker sur la machine de l'auteur. Le chemin Linux se rejoue par la CI ([CI](dev/CI.md)).
- La documentation se range en `docs/systemes/`, `docs/dev/` et `docs/reference/`, avec ce journal ([index](INDEX.md)).
  L'audit du 15 octobre et le bilan de la revue sont retirés, ce qui en reste valable est ici.
- Les specs des deux moteurs, le plan général et les specs du générateur du Level 0 sont retirés de `docs/backrooms/`,
  en attendant leur nouvelle version.

## 30 octobre 2026

- Les nouveaux documents de conception de l'auteur font foi : moteur de shaders, moteur audio, qualité et presets,
  gameplay, production des assets, concurrents. Ils sont rangés dans `docs/interne/`.
- Aucun fichier suivi ne nomme un projet de référence, hors des modules de compatibilité.
- Là où ces documents reprennent un texte plus ancien que les décisions du 26 au 29 octobre (les sections 1 à 15 du
  moteur de shaders, une partie du moteur audio), ces décisions priment. Leur nouveau contenu fait foi : paliers de
  qualité, LOD, presets, gameplay, production.
- Les anciennes règles de contenu quittent la documentation publiée. La vision de jeu de l'auteur les remplace.
- Arrivée au sol : le joueur ne se pose plus sur le cadre d'où il sort. La garde d'arrivée est retirée : qui retombe sur
  un cadre d'arrivée au sol y repasse aussitôt, même avec un bloc sous le cadre, et fait des allers-retours tant qu'il ne
  s'en écarte pas ([portails](systemes/PORTAILS.md)).

## 31 octobre 2026

- Entré en marchant par un cadre debout et sorti d'un cadre au sol, le joueur est poussé dans le sens de son regard pour
  retomber au-delà du bord du cadre devant lui, au lieu de retomber dedans. Un petit saut complète la poussée quand il
  sort loin de ce bord ([portails](systemes/PORTAILS.md)).
