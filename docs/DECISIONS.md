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

- Palier Low de `QUALITE_ET_PRESETS`, première brique : le look d'une dimension tient dans une LUT 3D, appliquée dans
  l'injection en fin de calcul du fragment (§5.7), après la lumière des néons et non plus avant. La lumière jaune des
  tubes est désaturée et teintée avec le reste, et une couleur poussée au-delà de 1 est bornée avant l'étalonnage. La
  lumière des néons se calcule au centre du texel (§5.4), aux deux qualités actuelles ([rendu](systemes/RENDU.md)).
- Palier Low, lumière cuite (§4.2, §5.5) : la zone porte aussi la lumière directe des dalles et leurs ombres, pas seulement
  le rebond, et elle remplace la lightmap sur le terrain. Tout reste en forward, sans G-buffer, conforme à la règle du
  27 octobre et au repli de la spec pour l'hypothèse Q1. Le son se règlera plus tard (demande de l'auteur).
- Palier High sans G-buffer : le cache de surfaces remplace les ombres DDA par fragment du repli de l'hypothèse Q1. Le Level 0
  est statique : une lightmap au pixel de texture, calculée une fois par le processeur graphique, donne le même résultat
  pour deux lectures par fragment, sans rendu différé. Le relief vient de la luminance des textures, en attendant de vrais
  matériaux.
- Le Level 0 n'a plus de brouillard (demande de l'auteur) : la spec en prévoyait un, teinté. La profondeur vient de la lumière,
  et les dalles gardent un halo.
- Écart à `QUALITE_ET_PRESETS` §5.4, décision de l'auteur du 1er novembre 2026 : seules les ombres portées sont pixelisées. La
  lumière reste lisse, et seule une faible part du dégradé (`pixel_light`) garde la grille, avec un léger grain de pixels dans le
  noir (`dark_pixels`). Le §5.4 appliquait la grille à toute la lumière.
- Le Level 0 n'a pas de panne pour le moment (demande de l'auteur du 1er novembre 2026) : tous les néons éclairent, et le noir
  n'existe que là où aucune dalle n'arrive. `neons.blackout` reste au calendrier et au profil, à 0.
- Une dalle n'éclaire que par la face du bas de son bloc, comme un quad orienté avec une distance et un angle (demande de
  l'auteur du 1er novembre 2026). C'est un écart aux lumières de zone exactes de `QUALITE_ET_PRESETS` §4.4 : la chute en carré de la
  distance d'un rectangle physique brûlait le haut des murs et laissait le sol terne.
- Écart à `QUALITE_ET_PRESETS` §5.3, décision de l'auteur du 1er novembre 2026 : les corps portent une ombre dès le palier High, pas
  seulement en Ultra.
- Menu de `QUALITE_ET_PRESETS` §9 : l'icône n'a aucun texte (demande de l'auteur). Écarts assumés : les presets sonores et la
  qualité sonore attendront, le son se réglant plus tard ; pas de réglage des hallucinations, qui n'existent pas encore.
- L'Ultra vise l'image de référence de l'auteur du 1er novembre 2026 (`docs/interne`), mesurée : presque toute sa lumière est
  indirecte, ses sombres restent saturés, ses textures calmes. Écarts à `QUALITE_ET_PRESETS` assumés : plus de grille des texels en
  Ultra (§5.4, sous tous les presets, le réglage ne vaut que pour les autres paliers) ; contraste des textures baissé (`traced.detail`,
  §2.3 : la matière reste en pixels, mais moins marquée) ; la lumière directe à l'image et les rebonds ont des gains séparés, ce qui n'est
  plus physique et donne le rendu voulu. La palette du profil change à tous les paliers : Low et Ultra gardent la même (§5.7).
- Le jaune du Level 0 vient de sa lumière, sans filtre (demandes de l'auteur des 1er et 2 novembre 2026 : « un filtre jaune partout », puis
  « la lumière de couleur qui éclaire de la bonne couleur les blocs autour » et la teinte du mur de l'image de référence). Ses néons sont
  blancs. La courbe de teinte par luminance, essayée avant, remplaçait 85 % de la couleur de chaque pixel, et a été retirée. Néons blancs
  et rebonds seuls ne suffisaient pas : l'image avait la même teinte partout (49,6 du sombre au clair), alors que celle de référence va de
  43 à 57. Deux réglages du profil donnent cet écart par le chemin de la lumière : la couleur des rebonds (`bounce_color`), et une limite de
  la lumière d'une surface (`light_limit`), qui la fait blanchir sous une dalle. Réponse
  de film `[0.95, 1.0, 1.08]`. Les teintes suivent l'image de référence, mais pas ses niveaux : calée sur eux, l'image était 24 % plus claire
  que l'ancien look, et moins contrastée (« bien plus lumineux ») ; l'exposition de l'Ultra passe de 0,7 à 0,47, et ses reflets de 1,0 à 1,4 pour garder leur force. Le tout est un peu sous
  l'ancien look, au même contraste, à la demande de l'auteur : une lumière blanche paraît plus forte qu'une lumière jaune de même niveau.
  Pour le même motif (« le reste manque de jaune », 2 novembre 2026), le jaune du papier peint doit se voir sur le plafond et la moquette :
  rebonds plus colorés (`[1.03, 1.0, 0.72]`), lumière directe de l'Ultra à 1,1 et rebonds à 1,1, et une limite qui ne se tasse qu'à partir
  des trois quarts, sans quoi elle blanchit le sol. Le papier peint, qui tirait vers le vert, passe de 195,191,98 à 199,189,102. La moquette passe de 119,108,70 à 147,134,73 en moyenne, moins orangée et plus claire, et les plinthes,
  neutres, viraient au mauve à côté du jaune sans filtre : elles passent à 233,221,160. Les quatre paliers ont les mêmes teintes par bande
  (§5.7) ; High, Medium et Low gardent des sombres plus sombres, leurs rebonds étant ceux de la zone.
- Un corps prend la lumière du décor (demande de l'auteur du 2 novembre 2026) : même limite, même exposition, et ce que la pièce lui renvoie, par
  un rayon par fragment. La lumière blanche et fixe qu'il recevait ne suivait ni l'exposition ni les rebonds.
- Les trois niveaux de lumière doivent donner la même valeur (demande de l'auteur du 2 novembre 2026 : « la différence du calcul de lumière se
  voit encore trop »). Mesuré sur le même pixel : la zone valait 0,55 des tuiles sur ce qui ne vit que de rebonds, et la lightmap 3,7 fois dans
  le sombre, presque blanche. La zone gardait les réglages du palier actif à sa création : passée en Ultra en jeu, elle restait aux rebonds de
  High. Un changement de palier la recuit désormais, et elle vaut 0,86 à 0,95 des tuiles, sans gain ; la lightmap suit une courbe et une
  couleur relevées sur les tuiles, en Ultra. Filmé ensuite image par image en marchant (« de loin on remarque le changement de lumière
  quand on avance ») : une surface sans tuile était 12,5 niveaux sous sa valeur finale, et la zone ne comptait que deux rebonds quand les
  tuiles estiment les suivants. Sous la lumière tracée, la zone en compte une part mesurée (1,55 du second, puis 2 une fois corrigé le
  défaut des frontières de sections), et une surface neuve garde
  les rebonds de la zone au lieu d'un gain fixe : écarts de -4,3 et -0,2 niveau. Le vacillement de torche de vanilla, tiré à chaque tick
  pour la lumière de bloc, entre dans la lightmap, donc dans la lumière lointaine des zones peu éclairées (la lumière forte y est
  saturée) : un monde à courbe ne l'a plus. Son effet n'est pas mesuré : les sauts de 3 à 4 % par tick d'abord relevés dans le film de
  marche venaient du mouvement de la caméra, et caméra fixe, la grande salle ne varie que de 0,03 % avant comme après.
- Menu simplifié (demande de l'auteur du 2 novembre 2026) : les styles se réduisent à Fidèle, VHS (refait, plus convaincant) et
  Cinématique ; Pixel, Réaliste et Confort disparaissent (Confort doublait les options de confort, qui restent). Les ombres pixelisées
  valent oui (32) ou non : la taille 16 disparaît, et un réglage enregistré à 16 vaut 32. La partie avancée n'a plus de bouton qui
  coupe tout : le palier désactivé ne se choisit plus que par l'option de lancement. Écart avec le document de qualité, qui liste six
  styles : la décision de l'auteur prime.
- Style VHS refait une seconde fois (demande de l'auteur du 2 novembre 2026 : un 4:3 à l'écran, mais « la partie distordue, ce n'est pas
  les bords de l'écran mais les bords de l'image », pas de gris, un vrai aspect found footage). Compris ainsi : le cadre reste un rectangle
  droit, c'est l'objectif du caméscope qui courbe l'image vers ses bords. Le principe vient de ce que montrent les vidéos du genre, faites
  d'images de synthèse passées sur une vraie cassette au magnétoscope ; tout est écrit pour ce moteur, en une passe. Les défauts qui
  demanderaient l'image précédente (peignes de l'entrelacement en mouvement, exposition automatique qui pompe) ne sont pas faits : le
  post n'a pas d'historique. Le tremblé à l'épaule bouge l'image dans le cadre, pas la caméra du joueur, et suit l'option sans secousse.
  Ensuite (même jour) : bords du cadre droits, et traits blancs (pertes, tirets) sur un tiers de la hauteur d'une ligne ; le reste
  des défauts, grain des sombres compris, garde sa force (des défauts tous adoucis ne convenaient pas à l'auteur). Puis un entre-deux,
  à sa demande : le bruit des sombres reste entier, le reste à mi-chemin, et le tremblé à l'épaule passe au tiers, les lignes
  glissant beaucoup moins. Puis encore moins de tremblements : tremblé à l'épaule de 0,4 pixel, lignes qui glissent à peine, et
  l'entrelacement ne déplace plus les lignes que d'un cinquième de ligne d'une image à l'autre (une demi-ligne faisait sautiller les
  bords horizontaux).
- En quittant les Backrooms, le style revient à Fidèle (demande de l'auteur du 2 novembre 2026). C'est une règle des réglages, prise sur
  la dimension du joueur, pas une règle de rendu : chaque pixel garde le look de la dimension qu'il montre. Le palier et les options de
  confort restent, réglages de performance et d'accessibilité.
- Zone : une section relisait une voisine déjà finie, avec tous ses rebonds, au lieu de son état d'avant (trouvé le 2 novembre 2026 en
  cherchant les défauts de Low et Medium dans les grandes pièces : une bande claire au plafond, le long de chaque frontière de sections).
  Corrigé, la part des rebonds suivants de la zone revient à celle des rebonds tracés (2, au lieu de 1,55, mesurée avec le défaut).
  L'Ultra recapturé ne change que dans un couloir lointain et sombre (4 niveaux sur un bloc de 64 pixels) ; écart signé nul.
- Ultra figé et paliers alignés sur lui (demande de l'auteur du 2 novembre 2026 : « aligner toutes les qualités en dessous avec ce que
  représente l'Ultra », « le Ultra tu le fige »). Conforme au document de qualité (« Low et Ultra ont la même palette : seul le réalisme
  de la lumière change »). Les paliers prennent les réglages de l'Ultra (`detail`, `exposure`, `direct` dans `baked_light`, rebonds 0,95,
  pied de courbe nul, épaule 0,8, relief 0,6), la zone compte partout les rebonds suivants, les textures sont calmées partout (couleur du
  bloc lue dans le plus petit niveau de sa texture : la table des matières n'existe qu'en Ultra), et High perd la courbe qui creusait ses
  ombres et la double occlusion de ses rebonds. L'exposition des paliers vaut 0,63 contre 0,6 en Ultra : leurs rebonds, ceux de la zone,
  rendent un peu moins que les rebonds tracés. L'Ultra recapturé est identique à sa référence (écart signé nul, pire bloc de 64 pixels
  à 0,16 niveau, le grain du film). Les réglages de la section `traced` restent écrits en entier : un changement des valeurs de base ne le
  touche pas.
- La lumière est calculée jusqu'à 128 blocs (choix de l'auteur du 2 novembre 2026, entre trois correctifs proposés, après « la distance de
  rendu des area light », pour tous les paliers). Au-delà de 48 blocs, la lightmap recalée virait au brun sur les murs, plus sombres d'un
  tiers que de près en Low. Un loin prend le relais de la zone jusqu'à 128 blocs : la lumière directe cuite, deux fois moins fine à
  l'horizontale, avec les rebonds d'une pièce moyenne mesurée dans la zone ; au-delà, la pièce moyenne seule remplace la lightmap. L'Ultra
  ne change qu'au-delà de 40 blocs (recapture contre sa référence : écart signé nul, seul un couloir lointain change, 4 niveaux sur un bloc
  de 64 pixels, et prend le ton des murs proches). Écart au document de qualité, qui ne prévoit pas de troisième niveau de lumière cuite.
- Lumière de l'Ultra plus rapide à suivre un bloc, et ombres des corps refaites (demande de l'auteur du 3 novembre 2026 : la vraie raison
  du retard, puis « applique toutes les corrections que tu proposes »). Le retard venait d'un nombre fixe de tuiles par image (96), alors
  que la carte avait de la marge : le budget est maintenant un temps, 1,5 ms par image, mesuré sur le processeur et la carte graphique
  (horodatages lus sans attendre), et le balayage des rebonds va du plus proche au plus loin. Ne recalculer que les tuiles proches d'un
  changement a été écarté : un rayon porte à 24 blocs et le second tour relit le premier, un changement touche presque tout le cache, et
  les rebonds doivent rester identiques au bit près à un recalcul complet. Les ombres des corps : silhouette d'humanoïde animée comme le
  modèle de vanilla marche, puis, à la demande de l'auteur le même jour (« quelque chose qui colle au modèle, même cubique, là c'est trop
  rond »), les cubes mêmes du modèle de chaque créature, posés par les appels de son dessin, sans les cubes que sa texture laisse
  transparents ni ceux contenus dans un autre ; seize par corps au plus. Les couches ajoutées au modèle (armure, objet tenu) n'en font
  pas partie. Pour garder le coût des cylindres (plus de boîtes, et des ombres plus grandes, donc plus de rayons arrêtés), la grille des
  blocs ne se parcourt plus qu'une fois par dalle, vers son centre, tant que ce centre est visible : un rayon vers le bord d'une dalle
  à demi cachée par un mur compte alors comme s'il passait, ce qui ne change l'ombre d'un corps qu'au bord exact de l'ombre d'un mur.
  Écart à la règle « aucune allocation par image » : poser un corps rejoue les appels de son dessin, et le `setupTransforms` de vanilla
  alloue le quaternion de sa rotation (32 octets par corps et par image, mesurés) et relit le nom d'un corps qui en porte un. Recopier
  la pose de chaque créature pour l'éviter reprendrait le code de vanilla ; seul le joueur, toujours là, se tourne sans lui quand il est
  debout ou en marche, sans quoi il allouait 770 octets par image. Ombre de contact à tous les paliers sur la
  lumière qui revient de la pièce (Low et Medium n'avaient aucune ombre de corps, seulement le disque de vanilla), et disque de vanilla
  retiré là où ces ombres se dessinent. L'Ultra recapturé ne change que par l'ombre du joueur lui-même, désormais en cubes.
- Le loin se cuit comme la zone, sans valeur propre au Level 0 dans le shader (demande de l'auteur du 2 novembre 2026 : « fais en sorte
  que ça soit intelligent, pour ne pas avoir à le refaire plus tard », et la différence se voyait encore de loin). Ses rebonds étaient
  une pièce moyenne fixe, mesurée une fois dans le Level 0 et écrite dans le shader commun : elle aurait été fausse dans toute autre
  dimension, et à 10 à 25 % près surface par surface dans le Level 0 même. Le loin calcule maintenant ses deux rebonds avec la
  vraie géométrie (480 sections, 7,6 s de cuisson au lieu de 1,1 s, hors du fil de rendu) ; la pièce moyenne, au-delà de 128 blocs,
  se mesure sur ce qui est cuit. Elle ne vaut qu'une salle bien éclairée en moyenne : le quart le plus clair des sections, choisi sur
  mesure en deux salles (la moitié la plus claire donnait 0,59 à 1,01 de la zone, le quart 0,69 à 1,17).
- Moquette sans motif répété (demande de l'auteur du 2 novembre 2026 : « le pattern se répète trop »). Quatre textures tirées au hasard par
  bloc, sans rotation : l'épaisseur de la moquette (`gtgs_enterPile`) suppose la texture posée dans un sens fixe sur chaque face, et un
  modèle tourné lui ferait lire une autre texture de l'atlas. La couleur moyenne ne change pas (147,1 ; 134,5 ; 73,1), ni le look.
- Murs crème et lumière qui descend jusqu'au pied des murs (demande de l'auteur du 2 novembre 2026 : « la couleur des murs plus crème, un
  jaune plus clair », « la lumière éclaire plus fort, jusqu'en bas des murs, comme une vraie lampe, sans que les lumières saturent entre
  elles »). Mesuré contre l'image de référence : ses murs à mi-ombre sont aussi saturés que les nôtres, ce sont ses murs bien éclairés
  (180 à 200) qui sont crème, alors que les nôtres plafonnaient vers 150. Le papier peint passe de 199,189,102 à 213,204,121 ; en Ultra,
  l'exposition passe de 0,47 à 0,6, la lumière directe de 1,1 à 1,3, les rebonds de 1,1 à 0,95 (les sombres restent sombres) et les
  reflets de 1,4 à 1,1 (même force). La limite empêche deux dalles de saturer. Moyenne de l'image de 99 à 114, contraste gardé (2,58 à
  2,62) ; la référence est à 118 et 2,38. High et Low ne changent que par le papier peint (moyennes de 101 à 105 et de 103 à 107).
  L'état d'avant est gardé à part pour y revenir.
- La moquette a une épaisseur (demande de l'auteur du 1er novembre 2026) : sa hauteur est le bruit de sa texture, le regard y entre et
  s'arrête au premier pixel qui dépasse. `x_n.png` porte la hauteur en alpha, `x_s.png` ne porte plus que le lissage : une part renvoyée
  par matière n'avait pas d'effet visible, et la table n'a que quatre valeurs par pixel.
- Le cache de surfaces passe de 64 à 128 Mio, et ses rebonds de 8 à 16 Mio (décision prise en corrigeant les carrés de chunks, le
  1er novembre 2026) : une grande salle ouverte du Level 0 demande dix-huit mille tuiles, le cache en avait seize mille. Réduire le rayon
  des tuiles aurait changé l'image jusqu'à 24 blocs.
- Écart à `QUALITE_ET_PRESETS` §5.5 : la lumière d'une surface a des niveaux qui se fondent selon la distance (tuiles, zone, loin, pièce
  moyenne depuis le 2 novembre 2026), parce que le passage d'un niveau à l'autre faisait s'allumer les néons au loin.
- La dalle de plafond a un rail métallique mat, très légèrement surélevé (demandes de l'auteur du 2 novembre 2026 : un rail qui reflétait la pièce,
  essayé d'abord, ne lui convenait pas, « mat normalement »). Le relief passe par la matière, sans géométrie : un modèle à rails multiplierait
  les faces de tous les plafonds du Level 0 pour moins de deux centimètres, sur des faces trop fines pour que la lumière s'y lise. Le rail
  passe de deux pixels à un sur chaque bord, plus proche de l'image de référence, où il est fin et plus clair que la dalle.
- La matière d'un bloc s'écrit dans ses textures (demande de l'auteur du 1er novembre 2026, `QUALITE_ET_PRESETS` §5.6) : la moquette
  est mate à tous les paliers, le reflet de sol humide n'existe plus, et seul le sombre du motif du papier peint renvoie un peu les
  dalles, son fond restant mat et rugueux. « Le dessous » du motif a été compris comme le papier qu'il recouvre. Écarts : le reflet
  de la spec, tracé vers ce que voit la surface, est remplacé par un lobe vers les dalles et des rayons de visibilité, parce que des
  rayons sur une matière aussi peu lisse font des points brillants isolés ; les reflets commencent à High, Medium ne lisant pas les
  matières ; un seul fichier de propriétés ne porte que deux valeurs, le lissage et la part renvoyée. La table de l'atlas et ses
  deux fichiers par texture restent dans un moteur sans G-buffer.
- Écart à la spec : le palier Ultra trace sa lumière sans calcul GPU ni G-buffer. Les rebonds se rangent sur les surfaces, en
  GLSL 150 : il ne demande pas OpenGL 4.3, n'est jamais grisé, et n'a
  ni rendu réduit, ni TAA, ni accumulation à l'écran. Il n'a pas non plus de coupe-circuit à part : un shader refusé coupe ses
  rebonds pour la session. Auto se décide d'après `GL_RENDERER` seul, sans test de
  performance. Les paliers Medium et High n'ont pas les lumières par tuiles ni le SSAO de la spec : le cache de surfaces les
  remplace.
- Écart à la spec : la cuisson va jusqu'à sept sections de côté, et une seule zone existe à la fois : une vue loin de la
  caméra garde la lightmap.

- Entré en marchant par un cadre debout et sorti d'un cadre au sol, le joueur est poussé dans le sens de son regard pour
  retomber au-delà du bord du cadre devant lui, au lieu de retomber dedans. Un petit saut complète la poussée quand il
  sort loin de ce bord ([portails](systemes/PORTAILS.md)).
