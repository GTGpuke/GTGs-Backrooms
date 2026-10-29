# GTG's Backrooms — Guide du projet

## Vision

GTG's Backrooms reproduit les Backrooms dans Minecraft 1.21.1, sous Fabric, avec le plus de fidélité possible. Le mod
repose sur trois systèmes conçus ensemble.

| Système | Rôle | État |
|---|---|---|
| [Portails](systemes/PORTAILS.md) | Passage sans coupure d'une dimension à l'autre, vue de l'autre côté avec un niveau d'imbrication, interaction, lumière et son à travers le cadre | Livré et testé |
| [Rendu](systemes/RENDU.md) | Look propre à chaque dimension (teinte, lightmap, brouillard, néons qui éclairent, post-traitement), visible à travers les portails | Livré, seul le Level 0 a un profil |
| [Audio](systemes/AUDIO.md) | Acoustique propre à chaque dimension (réverbération, occlusion, rayons), bourdonnement des néons, liaison du son et de l'image | Livré, avec des sons vanilla en attendant les vrais |

Le premier niveau, le [Level 0](systemes/LEVEL0.md), est un labyrinthe de cinq couches. Sa génération est déterministe
et verrouillée par des empreintes.

L'objectif central est qu'aucune couture ne se perçoive. Chaque monde a son identité visuelle et sonore, et un portail
est une fenêtre honnête sur l'autre côté. Cela vaut aussi avec un mod qui dessine le terrain ou sous un pack de shaders.
Dans les Backrooms, le rendu du mod remplace alors le pack
([compatibilité avec les packs de shaders](systemes/RENDU.md#packs-de-shaders)).

Hors périmètre : le contenu des niveaux, le lore, les entités et le gameplay. Ils suivent les
[règles de contenu](BACKROOMS_CONTENT_RULES.md) et les specs de l'auteur (`docs/backrooms/`).

## Règles d'or

Elles priment sur le reste du guide.

1. Le projet existant est la base. Tout ajout s'adapte à la structure, aux conventions et aux tests du dépôt, jamais
   l'inverse. Avant de toucher à un système, on lit son document et les règles du projet.
2. On étend avant de réécrire. Le système de portails s'étend par des points d'accès, méthodes publiques ou évènements,
   sans réécriture de sa logique. Toute refonte d'un module existant est proposée à l'auteur avec ses compromis, puis
   validée avant d'être entreprise.
3. Le code est original. Aucun code ni aucune ressource d'un projet tiers n'est repris, traduit ou paraphrasé, quelle que
   soit sa licence. Seule exception : lire l'API d'un autre mod pour écrire son module de compatibilité.
4. Chaque monde a son profil, jamais d'état global. Le rendu d'un pixel dépend de la dimension qu'il montre, le
   traitement d'un son de la dimension où il est émis. Aucun comportement ne se décide sur la dimension du joueur.
5. Aucune couture. Une fonctionnalité visuelle ou sonore est correcte à travers un portail, dans une vue imbriquée et
   pendant une traversée. Sinon, elle n'est pas terminée.
6. Rien n'est terminé sans sa définition de « terminé ». Une fonctionnalité n'est livrée qu'une fois vérifiée en jeu et
   le build complet vert, sans warning.
7. Tout état modifié est restauré. Ce qu'une passe ou une source change dans OpenGL, OpenAL ou l'état de Minecraft, elle
   le remet tel quel. Au banc, `GlStateCheck` le vérifie pour chaque passe du rendu.
8. Rien dans les chemins chauds. Aucune allocation ni concaténation de chaînes dans les méthodes de tick et de rendu.
   Chaque passe se mesure par le moniteur de performance (`core/perf/`), temps GPU compris.
9. La documentation suit le code. `docs/` décrit l'état réel : le document du système touché,
   l'[architecture](systemes/ARCHITECTURE.md), les [options](reference/OPTIONS.md) et le [CHANGELOG](../CHANGELOG.md).
   Un changement sans sa documentation n'est pas terminé.
10. L'auteur décide. Les sessions de développement proposent, expliquent les compromis et réalisent ce qui est validé.
    Licence, dépendance, découpage, suppression d'une fonctionnalité et génération verrouillée du Level 0
    (`LevelZeroGoldenTest`) reviennent à l'auteur.
11. Le code et ses preuves font foi. Les tests, les mesures et la documentation à jour disent la réalité, le guide dit
    l'intention. Un écart entre les deux se consigne dans [DECISIONS.md](DECISIONS.md) et se tranche avec l'auteur. On
    corrige ensuite le guide ou le code, et aucun scénario n'est annoncé faisable avant d'avoir été prototypé.

## Principes d'architecture

### Un profil par monde

Chaque dimension des Backrooms a deux profils, liés par la dimension. Le `LookProfile` règle le rendu, l'`AudioProfile`
règle l'acoustique. Ce sont des données lues dans `assets/gtgs_backrooms/look_profiles/` et `audio_profiles/`, pas des
interfaces. Une dimension sans profil, comme l'Overworld, reste strictement vanilla.

Le piège à éviter : `if (joueurDansLeLevel0) activer()`. Ce test casse dès qu'on regarde à travers un portail. Chaque
vue empile donc le look de son monde (`LookState`), et chaque son garde sa dimension d'émission, même rejoué au cadre.

### Un rendu dans le stencil existant

Le rendu tient en deux étages, dans le stencil des portails (`PortalStencilRenderer`). Le premier injecte le look dans
les fragment shaders vanilla, patchés au chargement comme `ShaderClipInjector` le fait pour la découpe. Le second est un
post-traitement limité aux pixels de chaque look par les bits du stencil. Il n'y a ni G-buffer ni rendu différé.

Une vue se dessine dans le framebuffer du jeu, avec la projection du joueur. Deux cas seulement ont un framebuffer de vue
à part : le rendu sous un pack de shaders, par `ShaderPackPortalRenderer`, et l'image figée d'une vue lointaine, par
`FrozenViews`. Le détail est dans le document du [rendu](systemes/RENDU.md).

### Un audio sur le relais et le rejeu au cadre

Le serveur relaie les sons d'une dimension aux joueurs qui en reçoivent la fenêtre par un portail. Le client rejoue
chaque son distant au point du cadre le plus proche (`FrameSoundPlayer`, `PortalSound`). Le moteur audio étend ce chemin
au lieu d'en ouvrir un second. Un son rejoué garde la dimension de sa vraie source, tenue par son ancre (`SoundAnchors`).

### Dépendances et emplacement des modules

- `core/render/` et `core/audio/` consomment l'API de `core/portal/` : vues, transformations par le passage, mondes
  distants. S'il leur manque une information, le système de portails l'expose sans changer sa logique.
- Tout état tenu par monde suit deux évènements des portails : la bascule du client,
  `ClientPortalCrossing.WORLD_SWITCHED`, et l'oubli d'un monde distant, `RemoteWorldRegistry.WORLD_FORGOTTEN`.
- Ni le rendu ni l'audio ne dépendent du contenu. Le contenu (`backrooms/`) les configure par données et enregistre ce
  qui le concerne.
- Un module de compatibilité (`compat/<mod>/`) traduit entre un autre mod et le nôtre, sans logique métier. C'est le
  seul endroit du code qui nomme un autre mod.
- La colle OpenGL et OpenAL reste mince, rangée dans `*/client/`. Toute la logique est pure et testée.
- Aucun mixin n'injecte dans `WorldRenderer.setupTerrain` ni `renderLayer`, que les mods de rendu réécrivent.
- Où ranger un objet, un son, un payload ou un profil : tableau « Où ranger quoi » de
  l'[architecture](systemes/ARCHITECTURE.md).

## Méthode clean room

Le code est neuf, en structure comme en implémentation. Les principes sont libres, l'implémentation appartient au mod.

- L'étude et l'implémentation se font dans des sessions séparées. Une session d'étude lit une référence et écrit des
  notes de conception en prose dans `docs/design/`, sans extrait de code ni nom de classe, de méthode ou de champ.
- Une session d'implémentation n'a aucun code source d'un autre mod dans son contexte. Ses sources sont les notes de
  `docs/design/`, l'architecture et les conventions du dépôt.
- Le souvenir d'une implémentation existante ne donne que son principe. On réécrit à partir de notre architecture :
  profils par monde, passes par niveau de stencil, routage par source.
- Pour un module de compatibilité, on lit les signatures de l'autre mod, jamais son code source. Le module compile
  contre lui (`modCompileOnly`) et n'en embarque rien, ce que `verifyJarContents` contrôle.
- Certains points d'injection n'ont qu'un emplacement sensé dans Minecraft. Deux implémentations indépendantes s'y
  rejoignent par contrainte du moteur, pas par copie.

## Glossaire

Ces termes servent pour tout concept nouveau des moteurs. Ceux des portails restent tels quels : portail, cadre, face,
arrivée, vue, vue secondaire, vue imbriquée, monde distant, fenêtre synchronisée, sortie fugace, plan de découpe, image
d'un point par le passage.

| Terme | Définition |
|---|---|
| Look | Rendu propre à une dimension : teinte, désaturation, grain, lightmap, brouillard, néons et post-traitement. |
| `LookProfile` | Look d'une dimension, en données (`look_profiles/<dimension>.json`). Sans profil, une dimension est vanilla. |
| `LookState` | Pile des looks de l'image en cours. Le monde du joueur est au fond, chaque vue empile le sien à son entrée. |
| Bits de look | Bits `0x1C` du stencil, qui portent le look du pixel. Les deux bits bas, `0x03`, portent le niveau de vue. |
| Étage 1 | Look appliqué à chaque fragment par les shaders vanilla patchés au chargement. |
| Post masqué | Second étage : vignette, aberration chromatique et bloom, limités aux pixels d'un look. |
| `AudioProfile` | Acoustique d'une dimension, en données (`audio_profiles/<dimension>.json`) : réverbération, occlusion, absorption de l'air. |
| Dimension d'émission | Dimension où un son est créé. Tout son traitement en dépend, même quand il est rejoué au cadre d'un portail. |
| `NeonSchedule` | Calendrier des néons : état de chaque tube selon sa position et le temps du monde. Son jumeau GLSL donne le même résultat. |
| `CueClock` | Horloge commune du son et de l'image : le temps du monde plus la fraction de tick. |
| `Cue` | Lien son-image : des sons qui déclenchent des réactions visuelles (`sound_cues/*.json`). |
| Enveloppe | Amplitude d'un son par fenêtres de 20 ms, précalculée, lue à sa position de lecture réelle. |
| Perturbation | Son lié en cours de lecture, avec sa dimension, sa position et son amplitude (`Disturbances`). |
| `FlashLimiter` | Plafond de clignotement : l'effet visuel final ne varie pas fortement plus de trois fois par seconde. |
| Palier de détail | Qualité d'une vue selon la taille d'un bloc à l'écran : pleine, allégée ou figée (`ViewDetail`). |
| Image figée | Vue lointaine dessinée de temps en temps dans une texture, puis collée sur le cadre (`FrozenViews`). |
| Bascule | Échange du monde principal du client pendant une traversée, annoncé par `ClientPortalCrossing.WORLD_SWITCHED`. |

## Définition de « terminé »

Une fonctionnalité est terminée quand tous ces critères sont remplis.

- Elle est vérifiée en jeu, avec une preuve lisible : capture, mesure du moniteur ou ligne du journal.
- Une modification du rendu se vérifie sur les trois chemins : vanilla, mod de terrain et pack de shaders.
- Elle est sans couture, à travers un portail, dans une vue imbriquée et pendant une traversée.
- `./gradlew clean build --warning-mode all` passe sans warning. Il enchaîne Checkstyle, les tests unitaires, en jeu et
  du client, le verrou de couverture et le contrôle du JAR.
- Le code nouveau est couvert par un test unitaire, un test en jeu ou un test du client ([tests](dev/TESTS.md)).
- Rien ne régresse, ni l'image, ni une fonctionnalité, ni la qualité du build. Un changement visible voulu s'annonce
  dans le CHANGELOG.
- Ses passes sont mesurées, en temps CPU et GPU, sans allocation par image ni par tick.
- Tout texte visible passe par `Text.translatable()`, avec ses clés dans `en_us.json` et `fr_fr.json`.
- La documentation est à jour : document du système, [options](reference/OPTIONS.md), [CHANGELOG](../CHANGELOG.md),
  et [DECISIONS.md](DECISIONS.md) pour une décision ou un écart.

## Choix écartés

L'historique de ces choix est dans [DECISIONS.md](DECISIONS.md).

- G-buffer, éclairage différé ou indirect, reflets écran, ombre du cadre : le rendu tient en deux étages, et l'injection
  n'atteint ni les shaders d'un mod de terrain ni ceux d'un pack.
- Réverbération par convolution et table de matériaux commune au son et à l'image : les specs de l'auteur les excluent.
- Un framebuffer par vue, composé ensuite : il coûterait un rendu de plus par vue et doublerait un chemin qui marche.
- Une bibliothèque de rendu tierce : le mod dessine lui-même ses vues, dans son stencil.
- Des couches `api/` et `impl/`, des profils en interfaces : aucune abstraction sans deuxième implémentation réelle.
- Un second niveau d'imbrication : un portail dans un portail suffit, et il faudrait refondre les portails.
- Un conflit déclaré par `breaks` : un mod en conflit se rend compatible, au besoin par un module `compat/<mod>/`.
- Une transition de look à la traversée : le look suit le pixel et non le joueur, il n'y a donc pas de couture à masquer.
- Deux pipelines du mod de shaders dans une même image : le terrain s'y dessine en éclats.
- Calcul sur le GPU, code natif ou appels natifs de Java : rien de lourd à calculer, et OpenGL 4.3 manque sous macOS.
- Parcours des sections d'une vue en arrière-plan : l'image de retard qu'il impose fait clignoter les vues.
- Interface graphique de configuration : reportée, les options se lisent en `-D` ([options](reference/OPTIONS.md)).

## Suite du travail

### À faire

- Lumières dynamiques transmises par un cadre. Elles sont validées entre deux niveaux des Backrooms seulement, jamais sur
  un pixel vanilla. Elles attendent un second niveau et demandent un plan de coupe par lumière, pour ne pas éclairer
  derrière le cadre.
- Bascule invisible sous un pack de shaders. En entrant dans les Backrooms, une image dure de 96 à 117 ms, et 52 ms en
  sortant. Le terrain se reconstruit ensuite en quelques images.
- Couverture de tout le code. Avec tous les tests, elle atteint 86,5 % des instructions et 77,8 % des branches, mixins
  comptés à zéro. Chaque exclusion de `coverageExcludes` se retire quand sa classe atteint 100 % ([tests](dev/TESTS.md)).
- Image figée après une traversée sous Linux, avec les pilotes Mesa. La cause n'est pas trouvée. La CI échoue sur le test
  `portal_view_and_seamless_crossings` tant que ce n'est pas corrigé.
- Index des néons après un rechargement des ressources (F3+T). Un profil qui change de bloc de néon ne vaut que pour les
  chunks reçus ensuite.
- Fermeture d'une partie solo. Trois fois sur une quarantaine de lancements des tests du client, le serveur intégré n'a
  pas fini de décharger ses chunks. La cause n'est pas établie.

### À écouter et à mesurer

- Direction perçue : livrée, laissée à zéro dans le profil du Level 0 jusqu'à l'écoute de l'auteur.
- Acoustique générée contre rayons : même réglage pour 70 % de 40 sons, l'oreille tranchera. L'absence de saut audible
  reste aussi à écouter.
- Banc audio d'ensemble avec trente néons, trois cris et un portail : pas encore fait. Le décalage entre le son et
  l'image n'est pas mesuré par une capture audio-vidéo.
- Coût du rendu sur une carte graphique intégrée : pas encore mesuré.
- Sons définitifs : le bourdonnement, les claquements et le cri sont des sons vanilla par défaut.

### Pistes

- Budget dynamique : réduire les passes et le nombre de néons quand l'image dépasse un seuil. Pour l'instant, la qualité
  se choisit d'après le pilote.
- Vacillement et variation des tubes : la mécanique est complète, le profil du Level 0 les laisse à zéro. Il suffit de
  relever ses parts, sur décision de l'auteur.

### Questions ouvertes pour l'auteur

- Passage en GPL-3. La licence reste propriétaire d'ici là.
- Textes visibles : commande de débogage traduite, ce qui ferait passer en anglais la console d'un serveur dédié, et
  accent du nom « Neon du Niveau 0 ».
- Règles de jeu : cooldown de traversée par défaut, nul aujourd'hui, cadres plus grands que 8 × 8 et cadres trop hauts
  pour un étage du Level 0.
- Portables à deux cartes graphiques : documentation seule, message en jeu ou réglage de Windows.
- Fichier de configuration pour les joueurs, lu au démarrage.
- Version de Minecraft visée : passage aux noms de Mojang, puis portage.
- Refontes du socle proposées : sources client et commun séparées, registres hors du socle, découpage des plus gros
  fichiers.
