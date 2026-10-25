# Changelog

Toutes les évolutions notables de GTG's Backrooms sont documentées ici.
Format : [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/). Versions : [SemVer](https://semver.org/lang/fr/).

---

## [Unreleased]

### Added
- **Arrivées de référence** (`PortalArrivalReferenceGameTests`, garde-fou G3 du bilan de `docs/revue/`) : huit cas fixes
  sur le terrain naturel du serveur de test, dont l'arrivée calculée par la 0.10.0 est figée dans le test. Tout
  remaniement de la recherche d'arrivée (résolution asynchrone, point d'accès des destinations) qui en change une fait
  échouer le build, sauf décision annoncée. Le terrain de chaque cas est généré d'abord, chunk par chunk dans un ordre
  fixe : vanilla pose les features des chunks voisins en parallèle, dans un ordre qui change d'un lancement à l'autre,
  et une feature qui déborde sur le chunk d'à côté (colonnes de basalte des deltas, arbres) donne un terrain différent
  d'un monde neuf à l'autre, à graine égale ; sans cette préparation, un des huit cas changeait d'arrivée une fois sur
  deux (relevé du 25 octobre 2026).

### Changed
- **Traversée décidée aux yeux pour les joueurs, et bords du cadre qui bloquent** (décisions de l'auteur du 25 octobre
  2026) : un joueur franchissait un cadre debout aux pieds, et passait donc un cadre d'un bloc de haut la tête à travers
  le haut du cadre ; le point qui décide de sa traversée est désormais ses yeux quel que soit le cadre (prédiction
  client, vérification et secours du serveur, découpe du modèle), et les quatre bords du cadre sont pour lui de petites
  parois (`PortalCollision.frameEdges`, un demi-bloc hors de l'ouverture, du plan à un demi-bloc derrière) : tête et
  épaules doivent passer l'ouverture, un cadre trop bas l'arrête au front, un cadre surélevé se saute, un cadre étroit
  se passe aligné en glissant le long du bord. Mobs, objets et projectiles gardent leur règle (les pieds pour un cadre
  debout, les yeux pour un cadre couché, l'ouverture fermée sur un bord).
- **Fenêtre gardée pour tout portail à portée de rendu, quel que soit le regard** (C60 du bilan de `docs/revue/`,
  décision de l'auteur du 25 octobre 2026) : au-delà de 16 blocs, le serveur ne synchronisait l'autre côté d'un portail
  que si le joueur le regardait, et retirait sa fenêtre cinq secondes après que le regard s'en était détourné ; le
  regard qui revenait trouvait le brouillard le temps de la renvoyer. La fenêtre, et l'arrivée que le chargeur tient
  chargée, sont désormais tenues pour tout portail à moins de 64 blocs (`PortalRelevance.isInRange`), devant comme dans
  le dos ; le rendu, lui, ne dessine toujours que les portails devant le joueur (ou à moins de 16 blocs). Coût : les
  chunks et les entités des fenêtres de portails dans le dos. Mesuré sur le banc de production (scène « dos tourné à
  trois portails à portée », dix secondes passé le délai de grâce) : réseau et tick serveur inchangés une fois les
  fenêtres envoyées (0 Kio/s, 3 ms par tick, avant comme après) ; le client garde les chunks de ces fenêtres (quelques
  Mio par fenêtre de 49 chunks) au lieu de les recevoir de nouveau au prochain regard.
- **Monde quitté allégé après un délai sans retour** (C63 du bilan de `docs/revue/`, décision de l'auteur du 25 octobre
  2026) : un monde distant (celui d'un portail regardé, ou le monde quitté par une traversée, avec tous ses chunks, ses
  entités et son renderer) n'était rendu qu'à la déconnexion ou au changement de dimension vanilla. Quand plus aucun
  portail à portée ne montre une dimension depuis `gtgsbackrooms.portal.remoteWorldReleaseSeconds` (60 s par défaut,
  jamais moins que les 5 s du déchargement de sa fenêtre), le serveur demande au client de libérer ce monde
  (`portal_remote_dimension_release`) ; la dimension garde son index et est annoncée de nouveau avant tout nouvel envoi.
  Un retour après ce délai voit la vue se reconstruire le temps de renvoyer la fenêtre, un retour avant reste sans
  couture. Vérifié en production (mods de rendu du terrain et de shaders, pack actif) : traversée vers
  l'Overworld, 75 s à 300 blocs de tout portail, libération envoyée une fois à 60 s, puis au retour devant le portail
  l'End annoncé de nouveau, ses 121 chunks renvoyés, son monde recréé chez le client (8 ms) et sa vue reconstruite, sans
  erreur. Le client vide le monde libéré avant de le jeter (`RemoteWorld.clear` : chaque chunk déchargé avec sa lumière,
  ses entités et ses particules retirées, 7 ms une fois) et en détruit la lightmap : un vidage du tas du client de banc,
  relu jusqu'aux racines du GC, montrait le monde quitté encore retenu après la libération avec tous ses chunks (1 056
  références directes), par trois chemins. Le nôtre, les particules qu'un monde devenu principal gardait après les avoir
  cédées, est corrigé, comme le champ où vanilla garde le monde de création du joueur
  (`AbstractClientPlayerEntity.clientWorld`, final, que vanilla ne relit pas), désormais suivi à chaque traversée. Les
  deux autres sont hors du mod : le contexte de rendu que Fabric API ajoute au renderer vanilla et l'état de chargement
  vanilla du dernier changement de dimension ; ils ne gardent plus qu'un monde vide (12 références directes, aucun
  chunk), jusqu'au prochain changement de dimension vanilla.
- **Pose d'un portail vers une zone jamais générée, sans gel du serveur** (C56 du bilan de `docs/revue/`) : la recherche
  d'arrivée lisait jusqu'à seize colonnes autour du point de départ, et générait sur le thread serveur chaque chunk
  manquant (0,3 s par chunk du Nether sur le PC de référence, 0,7 à 2,9 s sur un portable, pour tous les joueurs). Le
  bloc demande désormais ces chunks un à un par un ticket (`PortalTargets.resolveLoadedOnly`, `SEARCH_TICKET`), ceux que
  la recherche synchrone lirait et pas d'autres (la colonne, et les cellules de son cadre quand elles débordent sur le
  chunk d'à côté), et rappelle à chaque tick jusqu'à ce que le chunk soit entier : la même arrivée qu'avant
  (`PortalArrivalReferenceGameTests` le vérifie), quelques ticks plus tard, le temps que vanilla génère le chunk sur ses
  threads de travail. Entre la pose et la résolution, le cadre n'est pas encore un portail : un joueur qui le passe
  aussitôt le traverse sans changer de monde. Décision de l'auteur du 25 octobre 2026. Rien ne change vers le Level 0
  (calcul pur, sans chunk). Un chunk ne compte comme chargé que s'il est entier et accessible (`getWorldChunk`) : côté
  serveur, `World.isChunkLoaded` répond vrai dès qu'un ticket donne son niveau au chunk, avant toute génération, et une
  première version qui s'y fiait attendait un tick puis générait quand même (sonde du 25 octobre 2026). Mesuré sur le
  banc de production du PC de référence (mods de rendu du terrain et de shaders, pack actif, scènes du banc
  de performance, mêmes réglages avant et après) : pire tick serveur de la scène « pose vers une zone neuve » de 25 à
  103 ms sur trois passes, contre 424 ms ; FPS, coût du rendu des vues et mémoire des autres scènes inchangés à la
  variance près. Ce qui reste dans ce pire tick comprend le chargement vanilla des chunks du joueur téléporté en zone
  neuve et la fin des chunks (conversion, lumière) sur le thread serveur.

### Fixed
- **Mur visible au moment de passer un cadre plaqué contre lui** (signalé par l'auteur le 25 octobre 2026) : contre un
  cadre au bord du bloc posé sur un mur (ou au sol sur un sol plein), la face du bloc est dans le plan du portail, et la
  zone du cadre ne l'emportait sur elle au test de profondeur que par un décalage de polygone de quelques unités. De
  près, l'imprécision des positions du terrain dépasse ce décalage de plusieurs centaines d'unités : dans les derniers
  centimètres avant de passer, le mur s'affichait à la place de la vue (mesuré sur le banc de production, un mod de
  rendu du terrain sans pack de shaders : mur plein écran à 20, 12, 6, 5,5 et 5,1 cm du plan, vue intacte à 30 cm ;
  après correction, la vue à toutes ces distances, et les poses à 30 cm, 1,5 m et 3 m identiques à l'image d'avant). La
  zone est désormais avancée d'un dixième de millimètre vers la caméra (`FrameAreaRenderer`) : ses bords ne bougent que
  d'un dixième de pixel vus à 30 cm (un millimètre suffisait aussi, mais déplaçait les bords d'un pixel par endroits).
  Et sous 5,2 cm du plan, le polygone qui referme la zone, découpée 2 mm au-delà du plan proche du jeu, était plus loin
  que la face du mur : il se marque désormais sans test de profondeur (il ne couvre que ce que la caméra voit à travers
  le cadre ; seul un bloc remplaçable, herbe ou neige, laissé dans l'ouverture pourrait se trouver devant lui, sur ces 2
  mm).
- **Portail noir en troisième personne** (signalé par l'auteur le 25 octobre 2026, sans pack de shaders, vers le Level
  0) : le serveur ne synchronisait l'autre côté d'un portail à plus de 16 blocs que s'il était devant le joueur, alors
  que la caméra à la troisième personne, en vue de face, regarde à l'opposé du joueur, et voit donc des portails dans
  son dos. Leur vue n'avait aucun chunk et prenait la couleur du brouillard de leur dimension, noire dans le Level 0.
  Reproduit sur le banc de production avec le JAR d'avant la correction (portail à 25 blocs dans le dos du joueur, vue
  de face : rectangle noir, aucun chunk envoyé tant que le joueur reste à plus de 16 blocs), et corrigé par la fenêtre
  gardée pour tout portail à portée de rendu (C60, plus haut) : même scène, vue dessinée, la fenêtre part dès la pose.
- **Moniteur de performance qui faussait ses mesures** : il lisait la charge CPU du processus et du système par un appel
  natif bloquant (`getProcessCpuLoad`, 50 à 60 ms) sur le thread de rendu et sur le thread serveur, à chaque résumé.
  Chaque résumé comptait ainsi une image lente et un pic de tick qui n'existaient pas sans lui (relevé de
  `docs/revue/BILAN-CORRECTIONS.md`, PERF 3.8). La charge est désormais relevée par un thread démon à chaque intervalle
  (`ModPerformanceMonitor.CpuSampler`), lancé avec le moniteur et arrêté avec lui ; les threads de jeu ne lisent plus
  que la dernière valeur, inconnue (`n/a`) avant le premier relevé. Format des résumés inchangé.

---

## [0.10.0] - 2026-10-23

Le mod devient *GTG's Backrooms*, sous licence propriétaire. La baguette de portail, seule façon d'en créer un, pose
des blocs portail double face aux réglages par portail (taille, faces, retour, sortie fugace, sens de sortie, arrivée au
hasard, destination, sortie fixée, pose) : portails debout, au sol, au plafond ou tête en bas, traversée d'un cadre
posé contre un bloc, lumière et météo à travers le cadre, compatibilité avec les packs de shaders et les mods de
rendu. La porte des Backrooms et le badge de la Gang sont retirés.

### Added
- **Baguette de portail** (`portal_wand`, en tête de l'onglet créatif), seule façon de créer un portail : clic droit
  dans le vide, un écran règle chacun des réglages d'un portail (largeur, hauteur, lumière, faces, retour, sens de
  sortie, arrivée au hasard, plan), toutes leurs combinaisons comprises ; clic droit sur un bloc, elle pose un bloc
  portail réglé ainsi, comme vanilla pose un bloc, sans s'user ; accroupi, clic droit sur un portail, elle prend ses
  réglages. Les réglages choisis partent au serveur à la fermeture de l'écran (payload `portal_wand_settings`), qui ne
  les pose que sur la baguette de la main désignée. Le bloc portail n'a pas d'item : cassé, il ne rend rien. Trois
  tests en jeu (`PortalWandGameTests`) et des tests unitaires.
- **Premier son du mod** : poser un portail avec la baguette fait entendre un portail qui s'ouvre, sous-titré
  (`ModSounds.PORTAL_WAND_PLACE`, déclaré dans `sounds.json`).
- **Plan au bord du bloc** (réglage `flush`, composant, NBT et info-bulle) : le plan du cadre passe sur la face arrière
  de ses blocs, à l'opposé de sa face avant, au lieu de leur milieu, et le portail ne semble plus couper un bloc en
  deux. Un cadre debout se pose alors contre un mur, un cadre couché à ras du sol ou du plafond ; le cadre d'arrivée
  suit le même plan, la lumière transmise se lit juste devant le plan, la plaque visée accroupi se colle à la face
  arrière. Sans le champ, une sauvegarde garde le plan au milieu.
- **Roulis de la caméra à la traversée** : d'un sol à un mur (un portail qui tourne autour d'un axe horizontal), la
  caméra du joueur part du roulis de l'image vue par le cadre et se redresse en moins d'une demi-seconde, au lieu de
  se redresser d'un coup (`CrossingRoll`, `MixinCamera`). Vérifié au banc en rafale d'images.
- **Traverser un cadre posé contre un bloc** : les blocs tout entiers derrière le plan d'un portail ouvert, dans son
  ouverture, n'existent plus pour l'entité engagée dans le cadre (son point de traversée devant le plan, en face de
  l'ouverture, sa boîte contre le cadre) : ni collision (déplacement, place libre, contrôle des pas d'un joueur par le
  serveur), ni étouffement, ni voile du bloc à l'écran (`PortalCollision.tunnel`, `MixinBlockCollisionSpliterator`,
  `MixinEntity`, `MixinInGameOverlayRenderer`). Un cadre se pose et se traverse contre un mur, un plafond ou un sol
  plein ; la tête entre dans un cadre au plafond collé au toit. L'engagement tient dix ticks une fois le point de
  traversée passé derrière le plan, le temps de la bascule ; la face arrière d'un cadre double face ne cache pas le
  côté d'où l'on vient. La pose n'exige plus aucun bloc libre derrière le cadre. Tests en jeu (`PortalTunnelGameTests`).
- **Baguette : pose, destination et sortie fixée** (réglages `placement`, `destination` et `mark`, composant, NBT,
  info-bulle, écran) : la pose se règle (selon le regard, comme avant, ou imposée : debout, au sol, au plafond) ; la
  destination se choisit parmi les dimensions du serveur (automatique par défaut : le Level 0, et la surface depuis le
  Level 0), les X/Z suivant l'échelle de coordonnées comme vanilla, l'arrivée cherchée sur le haut du sol, ou dans le
  Nether sur le premier sol où l'on tient debout, le cadre hors des blocs et des fluides, jusqu'à seize colonnes autour ;
  sans sol à portée (le vide de l'End, un lac de lave), sur une plateforme d'obsidienne posée au point de départ, la
  place dégagée : on n'arrive jamais dans des blocs ; « Fixer la sortie ici » retient l'endroit où l'on se tient (dimension, bloc, regard) : le portail y mène, et
  l'arrivée au hasard tire autour de ce point. Le serveur oublie une destination qu'il n'a pas et ne fixe une sortie que
  là où se tient le joueur. Un bloc relu vers une autre destination ou une autre sortie fixée cherche de nouveau son
  arrivée. Tests unitaires et en jeu (`PortalDestinationGameTests`).
- **Bord d'un portail qui fait paroi** : du côté d'entrée d'un portail ouvert, qui ne peut pas le traverser
  correctement (son point de traversée hors de l'ouverture, sur un bord) bute sur le plan comme sur une paroi mince,
  même pour un cadre en l'air : son corps ne passe plus à moitié dans le portail, et un corps sur le bord d'un cadre au
  sol se pose sur son plan. De dos, on passe toujours au travers. Joueur (par son client), mobs, objets et projectiles
  (par le serveur), comme les blocs de l'autre côté ; un portail fermé n'arrête rien (`PortalCollision.walls`).
- **Trappe et garde d'arrivée** : un cadre au sol s'ouvre sous qui s'y tient, même posé sur un sol plein, et l'on y
  tombe avec son élan. Qui sort d'un cadre d'arrivée au sol en est gardé (`ArrivalGuard`) : il s'y pose, même
  au-dessus du vide, sans repartir aussitôt par le portail de retour, le temps d'en descendre : la garde tombe quand il
  en descend, s'accroupit (on reprend ainsi une arrivée au sol, volontairement) ou change de monde, et une seconde
  après qu'il s'y est posé (qui y reste ou y marche sans y prendre garde y retombe). Le serveur refuse la traversée annoncée d'un
  cadre gardé et ne la mène pas lui-même. Remplace la descente accroupie dans une arrivée au sol, qui ne servait plus.
- **Arrivée tête en bas** (réglage `upside_down`, composant, NBT, info-bulle, écran de la baguette) : le cadre
  d'arrivée est retourné ; on y voit l'autre côté tête en bas, on en ressort renversé, et la caméra se redresse d'un
  demi-tour de roulis en moins d'une demi-seconde (`CrossingRoll`) ; couché, l'arrivée fait un demi-tour sur place. Le
  retour est retourné de même. `PortalFacing` compte douze orientations : les six droites, dont les ordinaux et les
  noms ne changent pas, puis leurs retournées ; le cadre d'arrivée lui-même se pose comme le droit. La colonne de
  droite de l'écran de la baguette devient Retour, Sortie, Arrivée.
- **Portails au sol et au plafond** : le bloc portail se couche quand on le pose en regardant franchement vers le bas
  (cadre au sol, face au haut : on y tombe) ou vers le haut (cadre au plafond, face au bas : on y monte), là où le
  cadre tient (derrière son plan, un bloc ne gêne pas). Le réglage `exit` prend `floor` et `ceiling` : le cadre
  d'arrivée se couche au sol (on en sort vers le haut) ou au plafond (on en tombe), dans le Level 0 (au sol du layer
  ou sous son plafond, là où son empreinte est libre) comme à la surface (relevé de deux blocs au plafond). Tout droit,
  un cadre au sol mène à un cadre au plafond et inversement, sans faire tourner (l'orientation « plafond » a pris les
  axes de « sol » à la manière du nord et du sud : le passage est une translation). Couché, un cadre n'est en face de
  son ouverture que dans sa largeur et sa hauteur : qui arrive de côté ne bute pas sur l'autre côté avant le bord. La
  traversée d'un cadre couché se décide aux yeux (au sol, le corps a déjà passé le plan quand la caméra le passe ; au
  plafond, la tête y entre la première), et la sortie garde la place de ce point : tombé par un sol, on ressort de
  l'autre côté à la hauteur où les yeux ont passé le plan, avec son élan ; collision à travers le cadre, annonces du
  client vérifiées par le serveur et secours du serveur suivent la même règle. Le secours du serveur fait aussi passer un joueur qui s'arrête
  juste après le plan (il attendait qu'il bouge encore : qui tombe dans un cadre au sol touche le fond avant la fin du
  délai laissé à son client). Tests unitaires et trois tests en jeu (`PortalLyingGameTests`).
- **Attelages à travers les portails** : un véhicule (bateau, wagonnet, monture) emmène tout ce qui le monte, joueurs
  compris, vers une autre dimension comme dans le même monde ; un cheval monté dans un bateau garde son cavalier.
  En selle, c'est le serveur qui mène la bascule (le client ne prédit pas) : le joueur descend, traverse par son
  propre chemin et remonte sur son véhicule retrouvé de l'autre côté ; dans le même monde, son client reçoit
  lui-même ses passagers et la position de son véhicule. Vers une dimension absente, rien ne part et chacun
  reste assis. Cinq tests en jeu (`PortalRidingGameTests`).
- **Sens de sortie et arrivée au hasard**, deux réglages de plus du bloc portail (composant `portal_settings`,
  info-bulle, NBT) : `exit` impose le point cardinal dans lequel on ressort du cadre d'arrivée (`north`, `east`,
  `south`, `west` ; `straight` par défaut, on continue dans la direction d'entrée), `exit_scatter` tire au sort les
  X/Z de l'arrivée dans un rayon donné autour de ceux du bloc (jusqu'à 64 blocs ; la cellule libre la plus proche du
  Level 0 ou la surface du monde normal sont cherchées de là). Un bloc relu avec un autre sens de sortie tourne son
  cadre d'arrivée sur son pied et repose ses portails ; si ce cadre est couché avant ou après, le bloc retire ses
  portails et cherche de nouveau son arrivée, comme à la pose. Tests unitaires et en jeu.
- **Bloc portail** (`backrooms_portal`), qui remplace pour l'instant la porte des Backrooms : un bloc invisible et
  traversable qui pose un portail plein cadre, le plan au milieu du bloc, face avant vers le joueur. Posé hors du Level 0
  il y mène, posé dedans il ramène à la surface ; il ne se vise qu'accroupi, pour le casser ou en copier les réglages
  avec la baguette. Les clients connaissent ses réglages, pas sa destination. Copié par `/clone` ou une structure, il garde la destination de l'original et pose ses propres portails, sans
  toucher à ceux de l'original.
- **Portails double face** : chaque côté tient deux portails dos à dos dans le même plan, chaque face menant à la face
  correspondante de l'autre côté. On voit et on traverse le portail par ses deux faces, il ne disparaît plus en tournant
  autour, et traverser ne fait plus tourner (on ressort dans la direction où l'on marchait).
- **Réglages par portail**, portés par la baguette (composant `gtgs_backrooms:portal_settings`, info-bulle) et gardés
  par le bloc : largeur et hauteur du cadre (1 à 8 blocs), passage de la lumière, faces et
  retour (voir ci-dessous). La pose est refusée si le cadre traverserait un bloc ;
  dans le Level 0, le cadre d'arrivée se pose là où il tient sur toute sa largeur, ses deux faces sur un passage libre.
- **Portail à une face** (réglage `double_sided`) : seule la face avant existe, de chaque côté ; de dos, rien ne se
  voit et on passe au travers. **Portail aller simple** (réglage `one_way`) : rien n'est posé à l'arrivée pour revenir.
  L'info-bulle de la baguette dit les deux réglages. Un portail que son bloc
  ne tient plus pour sien (face ou retour retiré par les réglages dans un chunk alors déchargé) est retiré par le
  registre.
- **Posture d'attaque des mobs qui poursuivent** à travers un portail : bras levés, arc bandé, regard fixé sur le
  joueur là où il se voit par le cadre ; ils n'attaquent qu'une fois passés de son côté. La posture passe le portail
  avec eux, sans retomber le temps de la traversée.
- **Entités et joueur à cheval sur un portail, des deux côtés à la fois** : celui qui traverse est dessiné en deux
  moitiés, chacune coupée au plan dans le monde où elle se trouve, la partie pas encore passée de ce côté, la partie
  passée de l'autre, quel que soit l'angle de vue (de face, de dos, de côté, à la troisième personne). Une entité
  vivante détruite en franchissant un portail (elle allait passer le plan en face de l'ouverture, vers l'arrière de la
  face) reste affichée jusqu'à l'arrivée de sa copie de l'autre côté, qui la remplace
  aussitôt (aucun double resté en arrière) et se dessine des deux côtés jusqu'à être sortie du cadre d'arrivée. La
  coupe se décide sur la position affichée à chaque image, comme la bascule du joueur : il ne disparaît plus l'image
  du passage. Comme pour la traversée, seul ce qui passe par l'ouverture est de l'autre côté : une entité qui longe le
  bord du cadre reste entière de son côté, et un joueur à cheval sur un bloc portail ne se voit de l'autre côté que
  pour la partie passée, même par la caméra à la troisième personne passée derrière le cadre. Une entité téléportée
  par un portail de son propre monde passe le portail telle qu'elle est affichée, au lieu de glisser d'un bout à
  l'autre : même à moins de huit blocs, sa nouvelle position part aux clients en absolu, et non en déplacement relatif.
- **Lumière transmise** : la lumière des blocs passe par un portail ouvert et visible réglé pour elle, d'un niveau de
  moins de l'autre côté, serveur comme client (sources de lumière virtuelles lues par le moteur de lumière). La lumière
  du ciel ne passe pas. Un portail qui apparaît, ou revient avec son chunk, fait revérifier tout son cadre : la lumière
  sauvée avec le chunk ne survit pas à une source éteinte depuis.
- Collision à travers les portails ouverts : une entité engagée dans le cadre heurte aussi les
  blocs de l'autre côté (murs qui encadrent l'arrivée, sol plus haut monté comme une marche) ; personne
  ne ressort dans un bloc. Client pour son joueur, serveur pour les mobs, objets et projectiles.
- Caméra à la troisième personne à travers un portail : juste après une traversée, tant que le
  recul de la caméra passe par le cadre, l'image entière est rendue de l'autre côté, d'où la caméra voyait la scène.
- Ce qui traverse un portail ouvert, en plus des entités. **Son** : sons et
  évènements de monde d'une dimension relayés à ceux qui la voient, rejoués côté client au point du cadre d'arrivée,
  volume réduit par le trajet jusqu'au cadre. **Particules** : chaque monde distant a les
  siennes (entités, paquets, évènements, effets d'ambiance des blocs), dessinées dans la vue. **Mobs** : un mob qui
  cherche un joueur le voit aussi par les portails ouverts, avec ses propres règles de ciblage, marche jusqu'au cadre,
  traverse et le cible de l'autre côté ; un mob frappé à travers un portail fait de même. **Clic molette** : choisit le
  bloc ou la créature de l'autre côté.
- `MixinTargetsTest` charge la cible de chaque mixin pendant le build : un mixin client cassé fait échouer le build au
  lieu de planter le client au lancement.
- Mesure de la traversée (`-PdebugPerformanceMonitor=true`) : à chaque bascule de monde, le client journalise
  `[perf] traversee: vers <dimension> bascule=<ms> pic=<ms> lentes=<n>/119 chunks=<n>` (durée de la bascule, pire
  image et images de plus de 50 ms sur les 120 suivantes, chunks reçus pendant ce temps), repris au F3 en solo. Côté
  serveur, les sections `portal.teleport.player` (dont `portal.teleport.vanilla`), `portal.sync.tick` (dont
  `portal.sync.chunks` et `portal.sync.entities`), `portal.destination.tick` et `portal.light` (lumière transmise ; `portal.light.client` pour la passe du client, en solo) entrent dans le résumé `[perf] serveur:`, et le journal de traversée indique combien de chunks de l'arrivée le client tenait déjà.
- **Sortie fugace** (réglage `fleeting_exit` du bloc portail) : la sortie, le portail de retour à
  l'arrivée, n'existe que sous le regard. Pour chaque joueur, elle disparaît quand il ne la regarde plus depuis une
  demi-seconde (ni dessinée, ni heurtée, ni visée : il la traverse comme de l'air) et revient dès qu'il la revoit, tant
  qu'elle existe ; elle disparaît pour tous (`ExitLost`, sauvé avec le bloc, oublié par une copie ou une nouvelle
  destination) quand, après avoir été vue, plus aucun joueur ne la regarde pendant une demi-seconde, et revient au
  prochain joueur qui passe l'entrée (pas pour un objet, un mob ou un spectateur). Seul le regard compte, pas la
  distance ; une sortie que personne n'a encore vue attend, et qui vient de sortir par elle a une seconde pour se
  retourner. Qui passe en marchant droit devant lui tourne le dos à l'arrivée : elle a disparu quand il se
  retourne ; qui passe à reculons, ou la garde en vue, peut revenir. Ce que chaque joueur voit, son client le juge et le
  rapporte au serveur (`portal_sight`) : les sorties dessinées à son écran sans cube opaque devant (le verre, les
  feuilles, une porte laissent voir), troisième personne et champ de vision réglé compris ; un joueur dont le client ne
  rapporte rien (mod absent, rendu distant coupé, rapport trop vieux ou d'un autre monde) ne voit rien. Sans effet sur un
  aller simple, qui n'a pas de sortie.
- **Météo dans les vues** : la pluie et la neige de l'autre côté se dessinent dans le cadre et tombent comme celles du
  monde du joueur, avec leurs éclaboussures au sol et le bruit de la pluie, qui ressort par le portail. Chaque vue suit
  la météo de sa propre dimension, envoyée par le serveur avec sa fenêtre : la pluie de l'Overworld se voit depuis le
  Level 0 ou le Nether, qui n'en ont pas.

### Changed
- **Arborescence du code** : quatre paquets sous la racine. `core/` est le socle : `ModConstants`, `ModConfig`
  (ex-`config/`), `registry/` (ex-`init/`, un registre par type, sons compris), `perf/` et `portal/`
  (ex-`backrooms/portal/`, la baguette dans `portal/item/`) ; `backrooms/` est le contenu (`level0/`, qui calcule
  lui-même où un portail arrive chez lui : `LevelZeroArrival`, sorti de `PortalTargets`) ; puis `compat/`
  et `mixin/`. Tests unitaires et en jeu suivent la même arborescence ; `docs/ARCHITECTURE.md` dit où ranger un objet,
  un son ou un payload (« Où ranger quoi »), et les skills du projet (`.skills/`) suivent. Identifiants de registre,
  composants et sauvegardes inchangés : les mondes existants ne voient pas la différence.
- **Un seul onglet créatif**, *GTG's Backrooms* (l'onglet *GTG's Backrooms - Portails* disparaît) : la baguette de
  portail en tête, puis les blocs du Level 0, à l'icône du papier peint.
- **Guide du projet** : `docs/GUIDE.md` (règles d'or, état réel du dépôt, architecture cible du rendu et de l'audio,
  méthode clean room, feuille de route par versions, cohabitation avec les packs de shaders, compatibilité, licence)
  devient le document de référence, confronté au dépôt le 2026-10-20 (écarts consignés dans sa section 2.4). Il est
  référencé depuis `CLAUDE.md`, `docs/INDEX.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md` et les prompts.
- **Performance des portails** (passe complète, mesures dans `docs/PORTALS.md`) :
  - la lightmap d'un monde vu par un portail n'est plus recalculée deux fois par vue et par image (celle du monde vu,
    puis celle du joueur), mais une fois par tick client dans une texture propre à ce monde, comme vanilla la sienne
    (`ViewLightmaps`) ; une vue du monde du joueur garde sa lightmap. Par vue et par image, c'était deux calculs de
    256 points, un millier d'objets temporaires et deux envois de texture au pilote ;
  - les tampons de sommets d'un renderer de vue ne réservent leurs objets GL qu'au premier usage : sa grille de sections,
    à la distance de rendu du joueur, en compte des milliers dont une vue n'en construit qu'une petite part, et douze
    objets GL par section dans la même image faisaient l'à-coup de l'apparition d'une dimension vue par un portail
    (`LazyVertexBuffers`, `MixinVertexBuffer`) ;
  - les payloads fréquents de la synchronisation (`portal_remote_entity`, `portal_remote_block_update`,
    `portal_remote_chunk_unload`) désignent la dimension par l'index de son annonce, un octet, au lieu de son
    identifiant (de 20 à 33 octets) : pour un paquet de déplacement d'entité, c'était plus de la moitié du paquet ;
  - chaque vue se dessine sous un ciseau d'écran, le rectangle de son cadre à l'écran (coupé avec celui de la vue qui
    la contient pour une vue imbriquée) : le GPU ne traite aucun fragment hors du cadre, pour le ciel, les nuages, le
    terrain, les entités et la pluie de la vue, avant même le stencil ;
  - les moitiés d'entités à cheval sur un portail ne sont cherchées que pour les portails à portée de la caméra qui
    dessine leur monde, pour ce rendu comme pour le dessin vanilla qu'il remplace, et rien n'est touché quand aucun
    portail n'est à portée : un monde plein de portails lointains coûtait cela à chaque image ;
  - mesuré sur le banc (tableau dans `docs/PORTALS.md`) : à-coup de création d'un monde distant 47 → 25 ms et d'une
    vue secondaire 72 → moins de 11 ms, pire image 114 → 85 ms, lightmap des vues retirée du profil (22 à 36 ms/s),
    coût au repos divisé par deux, paquets d'entité 34 → 15 octets et de bloc 30 → 6 octets ; le coût CPU d'une vue
    stable est inchangé (rendu vanilla de la vue).
- **Moniteur de performance** : le résumé client donne l'image la plus longue et le nombre d'images de plus de 50 ms de
  l'intervalle, et ses propres sections (`client.*`, dont le rendu des portails par vue, la création des mondes distants
  et des vues secondaires, le chargement des chunks distants) ; chaque section donne aussi ce qu'elle a alloué ; le
  résumé serveur donne les paquets et octets envoyés par type de payload des portails (`PortalNetwork`, qui encode une
  fois de plus chaque payload quand le moniteur est actif). La section `portal.light.client` devient `client.portal.light`.
- **Nom** : le projet devient *GTG's Backrooms*, de GTGpuke. Identifiant du mod `gtgs_backrooms`, paquet
  `com.gtgpuke.gtgsbackrooms`, classes `GtgsBackroomsMod` et `GtgsBackroomsClientMod`, propriétés système
  `gtgsbackrooms.*`, onglet créatif *GTG's Backrooms* (identifiant `gtgs_backrooms:main`). Changement incompatible :
  un monde créé avant ne retrouve ni les blocs, ni les items, ni les portails du mod (vanilla les remplace par de
  l'air), et son Level 0 repart d'une dimension neuve.
- **Licence** : la licence MIT est remplacée par une licence propriétaire, tous droits réservés à GTGpuke
  (`LICENSE`). Jouer avec un JAR publié par l'auteur reste permis ; copier, modifier ou redistribuer le code ou les
  ressources demande son accord écrit. Le texte de la licence voyage dans le JAR du mod et dans celui des sources
  (`LICENSE_gtgs_backrooms`).
- Moins d'objets créés à chaque image et à chaque passage : transformations d'un portail à l'autre composante par
  composante (résultats identiques au bit près), pertinence d'un portail calculée sans vecteur de regard, rapport de vue
  sans liste neuve. Clés de langue `item.*` des items de bloc retirées (le jeu lit celle du bloc) ; clé du biome du
  Level 0 ajoutée.
- Une traversée ne compte que si, au moment où ses pieds passent le plan, le centre de l'entité est en face de
  l'ouverture et sa boîte touche le cadre, au lieu d'une marge fixe d'un demi-bloc autour du centre : passer à côté du
  cadre ressortait dans le mur qui encadre l'arrivée, et le contourner en frôlant son bord faisait traverser ou buter
  sur l'autre côté. La collision à travers le cadre suit la même règle ; celui qui entre vraiment, même de biais près
  du bord, bute toujours sur les murs qui encadrent l'arrivée.
- Le type d'entité portail est enregistré dans `core/registry/ModEntities`, comme les autres registres. Le cooldown des joueurs
  est tenu par `TeleportExecutor` (plus de `TpCooldownTracker`), `PortalSound` rejoint `client/sound`, et la
  transposition d'un point d'un côté à l'autre est portée par le portail (`toDestination`, `fromDestination`).
- Chemins chauds sans allocation évitable : dimension d'arrivée et orientations du portail en cache, pertinence d'un
  portail calculée sur les composantes, fenêtres de chunks voulues en ensembles ordonnés (plus de recherche linéaire),
  brouillard du ciel des vues sans lambda par image, retour immédiat du rendu et de la visée sans portail chargé.
- Couverture verrouillée à 100 % des branches, en plus des instructions : les branches qu'aucun cas réel ne prenait sont
  retirées (gardes redondantes), les décisions pures (tirage pondéré du biome, échéance des résumés de performance)
  extraites et testées sans changer ce qu'elles décident ; empreintes du Level 0 inchangées.
- Tests en jeu : joueurs simulés dont on lit les paquets reçus (sons, évènements et particules relayés, chunks tenus
  jamais renvoyés, objet de l'arrivée apparu chez le joueur), aides partagées (`GameTestServerWorlds`), règles complètes
  de la poursuite des mobs, bloc inerte sans portails, retour du Level 0 prêté suivi puis retiré avec son bloc.
- Les portails se dessinent avant la couche translucide du monde : l'eau, le verre, les
  particules, les nuages et la pluie devant un cadre laissent voir l'autre côté par transparence au lieu de le cacher.
- Stencil des portails par niveau d'imbrication : chaque portail marque sa zone, dessine sa
  vue puis rend à sa zone sa vraie profondeur et le niveau courant ; plus de valeur par portail ni de bit d'imbrication.
- Découpe des vues de portail par les shaders : chaque vertex shader vanilla reçoit au chargement
  un uniforme `backrooms_ClipPlane` et écrit `gl_ClipDistance[0]` ; pendant une vue, le plan du portail d'arrivée coupe
  terrain, entités, ciel et nuages avec la projection du joueur. Plus de projection à plan proche oblique pour le rendu vanilla (précision de
  profondeur dégradée aux angles rasants, repli sans découpe au ras du plan).
- Culling des vues par le volume visible à travers le cadre (pyramide caméra → arêtes du cadre d'arrivée) : sections,
  entités et portails imbriqués hors de ce volume ne sont plus dessinés.
- Le chargeur d'arrivée garde la fenêtre d'un portail dix secondes après que plus personne ne la regarde : un coup d'œil
  ailleurs ne déchargeait plus rien (le journal montrait des chargements et déchargements du Level 0 toutes les deux secondes).

### Removed
- **Badge de la Gang**, **Casse-croûte Maudit** et **Arbre Maudit** (tronc, feuilles, pousse, planches, configured
  feature `cursed_tree`, entrées du mod dans les tags `minecraft:logs`), avec leur payload, leurs ressources, leurs
  tests et le paquet `feature/` : leurs blocs disparaissent des mondes existants et leurs items des inventaires.
- **Anciennes specs** remplacées par `docs/GUIDE.md` : `docs/backrooms/1 - Arborescence`, `3 - Portails`,
  `4 - Veil Shaders`, `to-check` et `docs/DEPENDENCIES.md`. Restent `docs/backrooms/backrooms-level0-pipeline-v6.md`,
  `backrooms-level0-roadmap.md` et `level0-manual-structures-template.json` (générateur du Level 0, verrouillé) et
  `docs/BACKROOMS_CONTENT_RULES.md` (direction artistique).
- **Porte des Backrooms** (`backrooms_door`), en attendant de revenir sur la base du bloc portail : ses blocs
  disparaissent des mondes existants, et les portails qu'elle avait posés se retirent seuls, leur bloc propriétaire
  n'existant plus.

### Fixed
- **Entité coupée par un cadre au sol ou au plafond** : le rendu qui dessine en deux moitiés une entité à cheval sur un
  portail décidait sur ses pieds, la traversée sur ses yeux pour un cadre couché. Sur un cadre au sol, dès que les pieds
  passaient le plan, l'entité était dessinée entière jusqu'au passage des yeux : ses jambes pendaient sous un cadre en
  l'air, et dans un cadre posé sur un bloc elles s'enfonçaient dans le bloc sans paraître de l'autre côté (le modèle
  semblait disparaître). Le rendu décide désormais au point de traversée, comme la traversée
  (`BackroomsPortalEntity.crossingPointFaces`). De même, vue de l'autre monde, une entité qui s'enfonce dans un cadre
  d'arrivée au sol pour prendre le retour sort désormais du portail de départ jusqu'au passage de ses yeux
  (`crossingPointFacesArrival`).
- **CI** : la CI rejoue les tests en jeu trois fois sur le même monde (étapes tests en jeu, couverture, `build`), et la
  couverture se mesure sur le deuxième passage. Le test de l'arrivée sans sol cherchait le vide de l'End là où le
  passage précédent avait posé ses plateformes d'obsidienne : la recherche s'arrêtait à la première colonne et la
  couverture tombait sous 100 %. Il rend désormais le vide à ses points de l'End avant de s'en servir. Le témoin du test
  de traversée vers le Nether sans renvoi des chunks tenus ne voyait que deux chunks (portée par défaut d'un client)
  quand le portail en synchronise jusqu'à cinq autour de l'arrivée : selon la course entre les deux envois, l'attente ne
  pouvait plus aboutir (échec d'une CI, monde déjà joué). Il voit désormais toute la fenêtre.
- **Bloc visé à travers un cadre posé contre lui** : le regard touchait, au plan même, le bloc que le portail cache
  derrière lui, et le contour de ce bloc s'affichait au lieu de viser l'autre côté. Un bloc caché par le portail
  n'arrête plus le regard (`RemoteTargeting`), à la première comme à la troisième personne.
- **Caméra à la troisième personne arrêtée par un cadre posé contre un mur** : vanilla l'arrête au premier bloc que
  touchent ses huit rayons décalés, ce bloc caché derrière le plan, voire un peu avant lui ; elle ne passait pas le
  portail. Le passage se décide sur un rayon qui ignore les blocs cachés par le portail, et le recul de l'autre côté
  part un peu devant le plan d'arrivée (`PortalStencilRenderer`).
- **Vue d'un portail immobile pendant le balancement de la marche**, sous un pack de shaders : le mod de shaders
  porte le balancement de la vue dans la matrice de vue du joueur, alors que vanilla le met dans la projection, et
  les vues, construites depuis la seule rotation de leur caméra, ne balançaient pas avec le cadre. Elles prennent ce
  que la matrice de vue du joueur ajoute à sa rotation (`ShaderPackPortalRenderer`). Mesuré au banc en reculant face à
  un cadre : l'écart de la vue dans le cadre est le même que sans pack (0,057 contre 0,052 de plage), contre 0,098
  sans la correction.
- **Main traversée par la vue d'un cadre tout proche**, en première personne sous un pack de shaders : le pack dessine
  la main à sa vraie profondeur, que le plan d'un cadre à moins d'un demi-bloc de la caméra bat, et la vue, dessinée
  après l'image, la recouvrait juste avant la traversée. Les pixels de la main prennent un bit du stencil pendant son
  dessin (`HandMask`, `MixinHeldItemRenderer`) ; aucun cadre ne s'y marque et l'image gardée du joueur y est remise.
  Sans pack, vanilla dessine déjà la main par-dessus tout. Vérifié au banc, avec un mod de rendu et un pack de shaders.
- **Cadre à ras d'un bloc qui scintillait** (plan au bord du bloc sur un sol plein, par exemple) : la marque du cadre
  dans le stencil et la face du bloc qu'elle touche se disputaient la profondeur. La marque est tirée vers la caméra le
  temps de se poser (`glPolygonOffset`, `FrameAreaRenderer.mark`). Vérifié au banc, avec et sans pack de shaders.
- **Élan perdu en sautant dans un cadre au sol** : la sortie plaçait les pieds à leur image, plus d'un bloc et demi sous
  le plan quand les yeux décident ; elle garde maintenant la place du point qui décide (voir *Portails au sol et au
  plafond*), et un cadre posé sur un sol plein s'ouvre sous qui y tombe (voir *Trappe et garde d'arrivée*).
- **Écran qui tournait d'un tour complet à la traversée de presque tout portail** : le roulis de départ se lisait
  sur un quaternion sans en fixer le signe ; or un quaternion et son opposé sont la même rotation, et le lacet du
  joueur, jamais ramené à un tour, change ce signe à chaque tour sur soi-même. Un roulis nul valait alors un tour
  entier, que la caméra déroulait en une demi-seconde. Le roulis est pris entre -π et π
  (`PortalViewTransform.crossingRoll`) ; test sur les 36 paires d'orientations, une grille de regards et des lacets
  cumulés. Vérifié au banc avant et après : d'un cadre sud à un cadre sud, lacet -180, l'image passait à l'envers
  et reste droite ; du sol à un mur, le roulis voulu est toujours là.
- **Plantage au démarrage avec Fabric Loader 0.19.2** (`ClassCastException` dans MixinExtras, transformation de
  `WorldRenderer` refusée) : compilé contre le Mixin du développement, le point d'injection d'un `@Redirect` est un
  tableau, que le MixinExtras 0.5.4 embarqué par ce chargeur lit comme un seul élément. Les quatre `@Redirect`
  disparaissent : le ciel d'une vue passe par `@ModifyExpressionValue` (rien d'alloué à chaque image), les tampons de
  sommets différés par `@WrapOperation` (l'appel d'origine reste évité), et `MixinTargetsTest` refuse tout
  `@Redirect`. Vérifié en production, avec Fabric API seul, jusqu'en jeu devant un portail : Loader 0.19.2 avec
  Fabric API 0.116.11 (la configuration du plantage) et Loader 0.16.9, le minimum déclaré.
- **Test en jeu intermittent** (`crossingADimensionPortalMovesThePlayerToTheNetherWithoutResendingItsChunks`, une
  fois sur six) : il comparait les chunks du Nether au début et à la fin, alors qu'un chunk qui change pendant qu'il est
  tenu (un feu qui naît et s'éteint) repart à bon droit. La CI a montré qu'une empreinte des blocs, même relevée à
  chaque tick, ne suffit pas : un bloc peut changer puis revenir dans le même tick (une case de lave qui se vide et
  se remplit), et le serveur renvoie alors le chunk sans qu'aucune empreinte ne voie de différence. Le test place
  désormais un témoin dans le Nether, à l'arrivée : vanilla lui envoie chaque bloc signalé, et seul un chunk signalé
  depuis le passage peut être renvoyé ; il vérifie aussi que vanilla repropose des chunks tenus sans les renvoyer.
- **Portails d'une orientation à une autre** (changement de gravité : sol ou plafond vers un mur, mur vers sol ou
  plafond), vérifiés au banc en survie et sur les 36 paires en tests en jeu :
  - la vue à travers un tel portail était penchée (lacet et tangage seuls, sans roulis) : la caméra d'une vue prend
    maintenant la rotation complète du portail (`PortalViewTransform.aim`), vues imbriquées et pack de shaders compris ;
  - tombé de haut dans un sol et sorti d'un mur, on prenait les dégâts de la chute en touchant terre : la chute n'est
    gardée que si le bas reste le bas ;
  - un regard devenu vertical prenait un cap au hasard : il garde le haut de l'image ;
  - sorti d'un plafond après un mur, la tête entrait dans le plafond ; sorti d'un mur après un cadre couché, la tête
    pouvait sortir au-dessus de l'ouverture : le corps est tenu du bon côté et dans l'ouverture ;
  - la face arrière d'un portail couché menait dans le sol ou le plafond : un portail couché n'a que sa face avant ;
  - un sens de sortie imposé pouvait tourner dans le Level 0 pour tenir dans la cellule la plus proche : le cadre se
    pose plus loin, là où il tient tel quel.
- **Sons coupés à la traversée** : rien ne les arrêtait (ni la musique), mais les sons positionnés encore joués
  (un disque, une ambiance longue) restaient à leurs coordonnées du monde quitté, inaudibles depuis l'autre monde.
  Chaque son garde sa vraie source et, après une traversée (prédite ou menée par le serveur, dans le même monde ou
  vers un autre), est replacé sans relance : à sa place dans le monde du joueur, au cadre par lequel il voit le monde
  du son, ou tu faute de chemin, et retrouvé là où il en est au retour (`SoundAnchors`).
- **Dimension noire sous un pack de shaders** : après une traversée, dès que le portail sortait de l'écran, des
  pans entiers du monde (toute la dimension dans le Level 0) passaient au noir, ou au brouillard sous un ciel. La
  passe finale du pack laisse l'écriture de la profondeur coupée ; une vue dessinée la rétablissait, mais sans vue à
  l'écran l'image suivante n'effaçait plus sa profondeur et le pack prenait ce qu'il restait pour du ciel. Les masques
  d'écriture sont maintenant rétablis à la fin de chaque image sous pack (`PortalStencilRenderer.renderAfterWorld`).
- **Couleurs de l'autre dimension sous un pack de shaders** : après une traversée, les entités, le joueur et sa main
  restaient éclairés comme dans la dimension quittée (vu de l'End vers le monde normal), et une vue dessinait ses
  entités et son ciel avec les programmes de la dimension du joueur. Le pack tient un pipeline par dimension et en
  nomme les programmes pareil d'un pipeline à l'autre ; vanilla redonne à tout programme un étage déjà compilé sous le
  même nom : le pipeline créé pour une vue, pendant que celui du joueur vivait encore, recevait les 71 programmes
  compilés pour l'autre dimension. Un étage n'est plus redonné qu'aux programmes chargés par la même fabrique de
  ressources que lui (`MixinShaderProgram`, `ShaderStageOrigin`) ; un rechargement vanilla en partage une pour tous
  ses programmes et garde son partage.
- **Nuages des vues avec un mod de terrain** : un mod de rendu qui remplace les nuages les dessine depuis la caméra du
  joueur, pas depuis la position que la vue lui passe : dans une vue, les nuages se plaçaient par rapport au joueur,
  dans l'autre monde (trop bas, ou vus de l'autre côté de leur couche quand l'arrivée est au-dessus d'eux). La caméra
  du joueur prend la position et le monde de la vue le temps du dessin des nuages (`RemoteViewRenderer.renderClouds`).
- **Nuages qui sautent à la traversée** : chaque renderer de vue tenait sa propre horloge, qui place les nuages ; devenu
  celui du joueur à la traversée, il les montrait ailleurs. Les horloges des vues restent à l'heure de celle du joueur
  (`RemoteWorld.tickClock`).
- **Animaux qui sautent à la traversée** : après une traversée, le serveur renvoie sous le même identifiant les entités
  que le joueur voyait déjà par le portail, et vanilla remplace leur copie par une neuve (membres au repos, âge à zéro,
  une image en glissant depuis l'origine du monde). La reprise de l'état visible ne jouait que pendant une fenêtre de
  bascule, parfois refermée avant leur arrivée ; elle joue maintenant à chaque remplacement au même identifiant
  (`EntityHandover.replaceSameCopy`).
- **Ciel délavé et nuages absents dans les vues sous un pack de shaders** : la projection à plan proche oblique incline
  son plan lointain, et le pack, qui reconstruit ciel, nuages et brouillard depuis la profondeur par la projection
  inverse, faisait repartir derrière la caméra une partie des rayons du ciel. Elle ne sert plus que lorsqu'un bloc ou
  une entité se trouve entre la caméra de la vue et le plan d'arrivée (`ViewCone.intersectsBeforeFrame`) ; sinon la
  projection du joueur donne la même image, que le pack dessine comme son propre monde. Même règle pour un mod qui
  dessine le terrain sans pack.
- **Blocs transparents opaques après avoir coupé un pack de shaders** : couper (ou activer) un pack recharge le
  renderer du joueur, et seulement lui ; ceux des vues gardaient leurs sections construites pour l'état d'avant
  (feuilles et verre aux pixels transparents remplis de couleur, dans la vue puis dans le monde rejoint par le portail,
  jusqu'à un rechargement des ressources). Les renderers des vues se rechargent maintenant avec celui du joueur
  (`MixinWorldRenderer`, `RemoteWorldRegistry.reloadViewRenderers`), tampons GL différés comme à leur création.
- **Cape étirée au passage d'un portail** : le point d'ancrage de la cape du joueur, qui suit sa position avec retard,
  restait de l'autre côté jusqu'au tick suivant ; la cape s'étirait vers l'ancienne position sur les images d'entre-deux.
  Il passe le portail avec le joueur, transposé comme lui (`ClientPortalCrossing.carryCape`), et repart de la position
  d'arrivée quand c'est le serveur qui mène la bascule.
- **Sons trop faibles à travers un portail** (la pluie de l'autre côté presque inaudible) : le trajet de la source au
  cadre retranchait un seizième de volume par bloc, quel que soit le volume : un son de pluie (0,2) s'éteignait à trois
  blocs du cadre. Il retranche maintenant la part de la portée du son parcourue, comme le moteur de son atténue tout son
  sur sa portée (`PortalSound.volumeThroughPortal`), test unitaire à l'appui.
- **Éclair noir ou blanc à la traversée d'un portail à sortie fugace** : le retour d'une sortie fugace perdue de vue
  n'est reposé par le serveur qu'à l'annonce du passage par l'entrée, alors que le client a déjà basculé : le temps d'un
  aller-retour réseau, le monde d'arrivée n'avait pas de retour chez le joueur, qui voyait en se retournant ce qu'il y a
  derrière le cadre (mur ou vide en noir, ciel en blanc). Au passage prédit d'une telle entrée, le client pose à
  l'arrivée un retour prédit pour chaque face, à la place exacte du vrai et fermé à la traversée, qui s'efface à
  l'arrivée du vrai (`ClientPortalIndex.predict`, `ClientPortalCrossing`) ; jamais pour un aller simple.
- **Blocs au bord d'une vue** : un bloc changé dans un chunk chargé mais pas encore actif n'arrivait jamais dans la
  vue d'un portail (vanilla ne prévient ses écouteurs que dans un chunk actif). Regardé de loin, le ticket de l'arrivée
  a le rayon de la fenêtre synchronisée : son anneau extérieur restait figé ; et près de l'arrivée, un bloc changé
  pendant le chargement se perdait. Le changement est maintenant relayé (`MixinWorld`), test en jeu à l'appui.
- **Mods de rendu** : un mod qui réécrit `WorldRenderer.setupTerrain` (pipeline de terrain remplacé) faisait refuser
  notre mixin et planter le jeu au lancement (une injection dans une méthode réécrite est refusée, quel que soit
  `require`). Une vue de portail prépare maintenant son terrain elle-même (grille, chunk builder, graphe de culling
  vanilla, ce que faisait `setupTerrain` avec la position du joueur substituée par mixin) : plus rien dans
  `setupTerrain`, plus de substitution, et les évènements de rendu du monde de Fabric ne sont plus déclenchés pour une
  vue avec un contexte vide (un mod qui y lisait la projection coupait le rendu distant dès la première vue). Le mixin
  de `WorldRenderer` passe en priorité 1001 : son injection dans `scheduleChunkRender`, que ces mods réécrivent aussi,
  s'applique à leur version. La lumière d'un chunk distant passe par le `readLightData` du gestionnaire de paquets, comme
  celle d'un chunk vanilla : un tel mod ne construisait jamais les sections des chunks reçus par les portails (vues noires,
  sections proches manquantes après une traversée). Quand il dessine le terrain, une vue lui laisse choisir ses sections et
  se dessine sous une projection à plan proche oblique (`ObliqueProjection`), ses shaders ignorant la découpe des nôtres.
  Un mod de shaders pose en tête de `WorldRenderer.render` un état par renderer qu'attendent ensuite `renderSky` et
  `renderLayer`, et le défait à chaque retour de `render` : un renderer de vue n'y passait jamais et la première vue
  échouait chez lui (rendu distant coupé). Avant de dessiner, un renderer de vue passe maintenant par `render` et s'y
  arrête à la première instruction vanilla (`ViewRenderStart`, `MixinWorldRenderStart`) : sans pack actif, les vues
  sont celles de vanilla. Avec un pack actif, les vues ressortaient noires : elles se dessinent maintenant après
  l'image du monde, chacune par un rendu complet de son renderer dans le pipeline du pack, sous le stencil de son
  cadre, l'image du joueur remise autour (`ShaderPackPortalRenderer`, module `compat/iris` qui lit l'API publique
  du mod de shaders Iris, seul mod nommé dans le code). Terrain, entités, ciel, nuages, météo et ombres de l'autre
  monde y sont éclairés par le pack, avec un niveau de portails imbriqués. Les entités à cheval sur un portail s'y
  dessinent en deux moitiés : les shaders du pack ignorant la distance de découpe, chaque moitié est rognée au plan sur
  le processeur (`ClippedVertexConsumers`). Un cadre derrière l'eau ou le verre n'est pas dessiné (`docs/PORTALS.md`,
  Limites connues).
- **Multijoueur et réseau** :
  - un bloc changé dans un chunk que le client tenait déjà au moment d'une traversée (un autre joueur, l'eau, la
    redstone) restait faux chez lui jusqu'au déchargement du chunk : vanilla ne relaie rien à un chunk qu'il compte
    envoyer, et ce chunk-là n'était plus envoyé. Il repart désormais ;
  - les chunks et les blocs encore en route pendant une traversée prédite par le client étaient perdus (trous ou blocs
    faux juste après le passage, surtout en ligne) ; une traversée refusée par le serveur laissait, deux secondes durant,
    des entités de ce monde affichées pour de bon (objets, mobs morts) ;
  - les données d'une block entity (texte d'un panneau, motifs d'une bannière, tête d'un joueur) ne suivaient pas un
    changement vu à travers un portail : elle s'y affichait vide ;
  - la portée d'une action à travers un portail se mesurait au plan infini du cadre : un client modifié, près du plan
    mais loin à côté du cadre, agissait de l'autre côté. Elle se mesure au cadre lui-même ;
  - un spectateur était montré par les portails aux autres joueurs (vanilla ne le montre qu'aux spectateurs), et
    perdait une sortie fugace en passant près d'elle : il ne la garde ni ne la perd plus ;
  - un rapport de regard arrivé après un changement de monde comptait pour le nouveau : il porte désormais son monde ;
  - les fissures d'un bloc miné à travers un portail restaient si le mineur se déconnectait, et un mineur mort
    continuait de miner ; elles se voient aussi, comme vanilla, de qui regarde par un portail ;
  - sur un serveur lent, le portail de retour clignotait juste après une traversée : la vue d'un joueur qui vient de
    passer repart au tick suivant ;
  - le regard porté à travers un portail de son propre monde pouvait viser son propre corps.
- Une entité dont le modèle touchait déjà le plan d'un portail, sa boîte de collision pas encore, restait dessinée tout
  entière dans son monde (un bras ou un objet tenu dépassait derrière le cadre, au lieu de passer dans la vue) : la
  coupe au plan part du modèle (la boîte élargie d'un bloc pour une entité vivante, d'un demi-bloc sinon), à l'entrée
  comme à l'arrivée.
- **Sortie fugace** : un seul rapport « rien de vu » la perdait, une image sans elle autour d'une traversée la faisait
  parfois disparaître sous les yeux du joueur, et un passage de côté, qui la fait sortir de l'écran, la perdait avant
  qu'on ait pu se retourner. Elle dépendait aussi de la distance : à plus de 16 blocs d'elle, en vol notamment, un
  joueur ne la faisait jamais disparaître. Elle ne dépend plus que du regard, joueur par joueur : elle disparaît après
  une demi-seconde sans que personne la voie, et qui vient d'en sortir a une seconde pour se retourner vers elle.
- Les renderers des vues de portail gardaient, une fois jetés, leurs tampons de dessin alloués hors du tas Java (une
  trentaine de Mio chacun) : chaque changement de monde après avoir vu des portails en perdait autant. Ils servent
  désormais au renderer suivant.
- Les chunks qu'un joueur tient après une traversée valaient jusqu'à ce que vanilla les propose, même après une
  réapparition ou un changement de dimension vanilla : des chunks pouvaient ne jamais arriver (trous près du portail).
  Ils ne valent plus que 100 ticks, et s'oublient aux changements de monde vanilla.
- Un spectateur qui frappait à travers un portail suivait la créature (caméra vanilla) et se retrouvait téléporté dans
  son monde pendant l'action ; un spectateur n'agit plus sur rien à travers un portail.
- L'annonce de traversée du client acceptait une vitesse ou un regard non finis ou démesurés, qui pouvaient couper le
  contrôle vanilla des déplacements trop rapides : elle est refusée.
- Une entité retirée de l'autre côté (morte, dans une autre dimension) restait affichée dans la vue jusqu'à cinq ticks :
  sa destruction part désormais au même tick.
- Un changement de monde vanilla ou une réapparition à moins de huit blocs pouvaient être pris pour un passage de
  portail ; les listes de travail de la synchronisation retenaient, en solo, les mondes de la partie précédente.
- Tests en jeu : un joueur réapparu par `respawnPlayer` restait dans le monde après sa déconnexion (la connexion
  suivait l'ancien joueur) et faussait les tests suivants qui comptent les joueurs ; le test de traversée entre
  dimensions ne dépend plus du nombre de chunks que vanilla repropose en 60 ticks. Des vérifications qui ne pouvaient
  pas échouer (arrêt du minage, cochon, refus hors de portée, cooldown, portail invisible) testent maintenant ce
  qu'elles annoncent ; les lots qui peuvent expirer retirent ce qu'ils posent, et l'arrivée générée d'avance couvre la
  fenêtre d'un joueur près du portail (11 x 11 chunks).
- Coups et minage à travers un portail passaient outre les gardes de vanilla : on minait en mangeant ou bouclier levé,
  et l'on frappait pendant le délai d'attaque ou à cheval. Ils les respectent désormais, comme dans le monde du joueur.
- Côté client, un portail dont la taille ou l'orientation arrivait après son apparition gardait des boîtes de
  détection à la taille par défaut : elles se refont désormais à chaque changement.
- Un enderman (ou tout mob que sa cible met en colère) frappé à travers un portail restait en colère et plus rapide
  quand sa poursuite finissait sans traversée : il lâche désormais cette cible comme vanilla.
- Les sons de l'autre côté ne passaient pas par un portail sans retour (bloc réglé en aller simple, portail invoqué
  seul) : le client cherchait leur chemin parmi les portails du monde du son, c'est-à-dire les retours. Il le cherche
  désormais parmi les portails par lesquels le joueur voit l'autre côté, retour ou non ; un joueur derrière une face
  n'entend plus rien par elle.
- Une entité ou un joueur qui passait d'un portail vers une dimension que le joueur voit par un portail (de
  l'Overworld vers le Level 0 sous ses yeux) restait figé dans le cadre jusqu'à un quart de seconde, avant que sa copie ne sorte de
  l'autre côté : le serveur n'envoyait la nouvelle copie qu'au prochain alignement de la fenêtre distante, tous les
  cinq ticks. Elle part désormais au même tick que la destruction de l'ancienne, comme vanilla l'envoie aux joueurs de
  la dimension.
- Une entité qui traversait un bloc portail pouvait être transposée par la mauvaise des deux faces dos à dos (la plus
  proche, toutes deux au même endroit) : sa nouvelle copie apparaissait en miroir, tournée, et ses moitiés se
  dessinaient du mauvais côté. La face franchie se reconnaît désormais au sens de son déplacement.
- Un mob qui poursuivait un joueur à travers un portail et l'abordait de biais pouvait longer le cadre sans passer par
  l'ouverture, son chemin coupant le plan à côté : il vient désormais d'abord en face d'elle, puis la traverse en ligne
  droite, sans s'arrêter devant.
- Un mob qui poursuivait un joueur à travers un portail marquait un temps d'arrêt une fois passé : sa copie neuve
  repartait sans chemin, le temps que ses objectifs la relancent. Elle reprend désormais aussitôt sa course vers le
  joueur, posture d'attaque comprise.
- Portail « épais » en tournant autour : sa zone de vue était une boîte de 20 cm derrière le plan, nécessaire seulement
  quand la caméra touche le plan dans l'encadrement. Vue de biais, ses flancs montraient l'autre côté à côté du cadre,
  et en passant d'une face à l'autre la boîte basculait d'un coup de l'autre côté du plan. Le portail est désormais
  plat partout ailleurs : de profil, il n'est plus qu'un trait, et rien ne saute en passant d'une face à l'autre.
- Une entité qui venait d'apparaître juste derrière un portail pouvait être téléportée sans avoir bougé : avant son
  premier tick, sa position précédente est l'origine du monde, et le trajet de l'une à l'autre coupait le plan. Le
  serveur ignore désormais, pour les entités comme pour les joueurs, un déplacement de plus de huit blocs en un tick.
- Chunks absents à la sortie de certains portails, jusqu'au prochain mouvement : les vues de portail laissent au renderer
  du monde distant leur liste de sections, limitée au cadre, et vanilla ne refait la sienne qu'après un changement de
  son graphe ou un regard tourné de deux degrés. Depuis les portails double face, la traversée ne fait plus tourner le
  joueur : celui qui s'arrêtait juste après ne voyait que ce que montrait le portail. Le renderer promu refait sa liste
  dès l'image suivante.
- Joueur vu « à l'envers » à la troisième personne en sortant d'un portail (son visage à la place de sa nuque, quelques
  images) : à cheval sur le plan d'arrivée, il était coupé par la découpe de la vue, et son modèle, dessiné sans
  élimination des faces arrière, montrait son intérieur. Chacune de ses moitiés se dessine désormais de son côté (voir
  les entités à cheval sur un portail) ; dans une vue vue à travers une autre vue, l'entité engagée dans le cadre
  d'arrivée se dessine entière.
- Côté quitté déchargé à chaque changement de dimension : vanilla retire les tickets du joueur dès qu'il change de
  monde, et la fenêtre du portail de retour ne prenait le relais qu'à la passe suivante du chargeur (jusqu'à dix ticks).
  Entre-temps, les chunks qu'il voyait se déchargeaient puis se rechargeaient (sauvegarde et relecture côté serveur à
  chaque traversée), et leurs entités revenaient sous de nouveaux identifiants : en double chez le client
  (`Duplicate entity UUID`), où la nouvelle copie, refusée par l'index, restait affichée figée, et les portails
  fantômes s'accumulaient au même endroit. Un ticket de départ garde désormais le côté quitté chargé dix secondes, comme
  une arrivée imminente (distance de vue du serveur, au moins la fenêtre synchronisée élargie), et une copie renvoyée sous un autre identifiant remplace l'ancienne.
- À-coups à la traversée, surtout à la troisième personne : la vue secondaire du monde du joueur (portails qui y
  ramènent, vus dans un autre portail) était détruite à chaque traversée puis recréée dès qu'il en fallait une, soit
  un `WorldRenderer` entier (une grille de sections et leurs tampons) créé et détruit à chaque passage. À la troisième
  personne, la caméra restée derrière le cadre voit aussitôt le portail franchi, qui montre le monde d'arrivée : chaque
  traversée la recréait. Chaque monde garde désormais sa vue secondaire d'une traversée à l'autre, ses sections suivant
  toujours celles du monde ; seule la première d'une dimension se crée, une fois par partie (mesuré à 12 chunks : pic de
  97 ms et 12 à 32 ms de bascule à chaque traversée auparavant).
- Pose d'un bloc portail vers une arrivée jamais générée : le chunk d'arrivée, où se pose le retour, était chargé de
  façon synchrone et figeait le serveur le temps de le générer (plus d'une seconde dans un Level 0 neuf). Il est demandé
  par un ticket, et le retour est posé dès qu'il est là. La surface de l'Overworld, que vise un bloc posé dans le Level 0,
  se lit encore sur un chunk chargé à la pose.
- Vues de portail : la vérification des chunks voisins de chaque section relisait les quatre chunks pour chacune des
  sections d'une colonne, à chaque image (jusqu'à 40 % du thread de rendu devant un portail) ; elle est faite une fois
  par chunk et par vue.
- Nuages noirs en regardant un portail : après les vues de portail, la couleur du brouillard était rendue aux shaders
  mais pas à `BackgroundRenderer`, où les nuages la relisent ; ils prenaient le brouillard du dernier monde vu (le noir
  du Level 0).
- Une déconnexion, ou un respawn dans une autre dimension, après une traversée laissait en place le renderer du monde
  distant promu (deux constructions de sections en parallèle au lieu de toutes, pas de contour des entités en
  surbrillance) : le renderer vanilla reprend sa place avant que vanilla ne crée un monde, et la fin de partie se traite
  sur le thread de rendu (l'évènement de déconnexion de Fabric peut tourner sur le thread réseau, or les mondes distants
  libèrent des ressources de rendu).
- Après un respawn ou une reconnexion, la prédiction de traversée du client restait coupée tant que le nouveau joueur
  n'avait pas l'âge de l'ancien à sa dernière traversée.
- Les portails dont le bloc propriétaire a disparu sont retirés même portails désactivés.
- Un portail fermé ne laisse passer ni clic, ni minage, ni son, d'aucun côté.
- L'ouverture d'un portail (`teleportable`) est synchronisée au client : un joueur qui avançait dans un portail fermé
  franchissait le plan d'un centimètre, son client basculait de l'autre côté et le serveur le renvoyait. La collision à
  travers le cadre et la reprise des entités suivent aussi l'état réel du portail.
- Une interaction à travers un portail fermé est refusée par le serveur, comme le client ne la propose pas.
- Eau, glace et verre teinté plus opaques à travers un portail : le rendu des portails coupait l'élimination des faces
  arrière, que les couches de terrain vanilla supposent active sans la rétablir ; les vues dessinaient les deux faces
  de chaque bloc translucide. Elle n'est plus coupée que le temps de dessiner les zones de cadre.
- Saut à la traversée : la main tenue rattrapait le demi-tour du portail en quelques images (balancement du bras non
  tourné), le corps se tordait vers la tête, le modèle vu en troisième personne bondissait (interpolation de rendu non
  transposée), et un paquet de vitesse du serveur, arrivé un tick plus tard, écrasait celle du joueur. Tous les angles
  passent avec leurs valeurs précédentes, ramenés au plus court, et le serveur n'envoie plus cette vitesse.
- Animations remises à zéro chez celui qui regarde un joueur ou un mob traverser : le serveur recrée l'entité dans
  l'autre monde ; la nouvelle copie reprend l'état visible de l'ancienne (membres, bras, âge, tête et corps tournés,
  cape, position affichée rejointe en trois ticks). Idem pour les entités qu'on retrouve après avoir soi-même traversé.
- Le portail de retour disparaissait quelques ticks juste derrière le joueur après une traversée : les copies d'entités
  que le serveur efface au changement de monde puis renvoie aussitôt sont retirées avec un délai, et remplacées sans trou.
- Plusieurs portails l'un derrière l'autre s'affichaient comme sur une même couche : la zone du plus lointain écrasait
  celle du plus proche dans le stencil, et deux portails imbriqués dans une même vue partageaient leur valeur.
- Bande noire sur l'horizon de l'Overworld vu depuis le Level 0 : la vue posait sa couleur de brouillard sans la donner
  aux shaders, et se fondait dans le brouillard du monde du joueur.
- Arrivée en Overworld sur une dalle, un chemin ou un tapis : le cadre flottait au-dessus, il se pose sur le haut réel
  de la collision du sol.
- Flicker dans les vues de portail et dans les portails imbriqués : chaque vue choisit désormais ses sections par un
  parcours synchrone qui lui est propre. Le culling asynchrone de vanilla tient un seul graphe par renderer et le
  recalcule en arrière-plan : deux vues du même monde dans une image (deux portails, un portail imbriqué, la vue
  secondaire) alternaient des listes de sections incomplètes.
- Sections en reconstruction permanente dans les vues : la grille de sections d'un renderer de vue est ancrée sur
  l'arrivée du portail et ne se recentre que si l'ancrage s'en éloigne de plus de deux chunks, au lieu de suivre la
  caméra transposée à chaque image (chaque recentrage remettait des colonnes entières à reconstruire).
- Flash de luminosité à la bascule : la lightmap est recalculée pour la nouvelle dimension dès l'image de la traversée
  (vanilla ne la recalcule qu'au tick ; au retour du Level 0, l'Overworld s'affichait une image de nuit).
- Après un respawn ou un changement de dimension vanilla consécutif à une traversée, les mises à jour de blocs partaient
  vers un renderer qui ne dessinait plus (bloc changé qui ne se redessinait pas, bloc cassé encore affiché) : le
  renderer principal reste celui avec lequel vanilla a créé le monde.
- Le relais des reconstructions de sections vers la vue secondaire suit le monde du renderer qui les programme, et non
  le renderer de `MinecraftClient`, substitué pendant le rendu des portails : rien ne part plus vers le mauvais monde, et
  les mises à jour de lumière faites pendant une vue sont relayées elles aussi.
- Traversée à reculons ou de côté : à moins de 16 blocs d'un portail (distance d'activation), l'autre côté se synchronise
  quelle que soit la direction du regard. Auparavant il fallait regarder le portail, et s'y engager sans l'avoir regardé
  aboutissait dans un monde pas encore chargé.
- Bascule client sans arrêt sur le plan : les positions et regards du tick courant et du tick précédent passent tous
  deux le portail, l'image en cours continue d'interpoler au lieu de figer le joueur sur le point de sortie un tick.

---

## [0.9.0] - 2026-10-16

Système de portails complet : porte des Backrooms, téléportation sans coupure (joueurs, objets, mobs, projectiles),
synchronisation des chunks et des entités de l'autre dimension, rendu du monde distant à travers le cadre avec un
niveau de portails imbriqués, et interaction à travers le cadre. Conception, protocole et limites dans [PORTALS.md](PORTALS.md).

### Added
- **Porte des Backrooms** (`backrooms_door`) en item, dans un nouvel onglet créatif *GTG's Backrooms - Portails*.
  Posée hors du Level 0, elle y mène (cellule réellement libre la plus proche des mêmes X/Z, sortie vers une
  cellule ouverte) ; posée dans le Level 0, elle ramène à la surface. Portail d'entrée devant la porte, portail
  de retour à l'arrivée ; les deux disparaissent avec la porte et se retirent seuls s'ils deviennent orphelins.
- **Synchronisation serveur → client** des chunks de l'autre dimension (payloads `portal_remote_dimension`,
  `portal_remote_chunk`, `portal_remote_chunk_unload`, `portal_remote_block_update`) : fenêtre de 7x7 chunks
  autour de chaque arrivée regardée, 8 chunks par joueur toutes les 5 ticks, changements de blocs relayés.
  Fonctionne en multijoueur (le prototype ne marchait qu'en solo).
- **Rendu du monde distant** à travers le cadre : stencil par portail, caméra transposée, lightmap recalculée
  pour la dimension distante, projection à plan proche oblique (rien n'est dessiné derrière le portail
  d'arrivée), profondeur du cadre restaurée après le rendu. Portails à sens unique (invisibles de dos).
- Coupe-circuit : stencil refusé par le pilote ou exception pendant un rendu distant → rendu distant coupé pour
  la session et portails en cadres sombres ; vérification que la substitution de caméra du mixin est active.
- **Traversée sans coupure** : le client prédit le passage dès que la caméra franchit le plan, bascule sur le
  monde distant déjà chargé (pas de paquet de respawn, pas d'écran de chargement, pas de rendu reconstruit) et
  garde l'ancien monde pour voir derrière soi ; le serveur applique l'état exact annoncé par le client après
  vérification (`portal_cross` → `portal_teleport`) ou le ramène en arrière.
- **Entités visibles à travers les portails** : le joueur est branché sur le suivi vanilla (`EntityTracker`) des entités
  de sa fenêtre distante ; leurs paquets lui parviennent enveloppés (`portal_remote_entity`) et son client les applique
  à son monde distant, tiqué à chaque tick (interpolation, animations). Entités, block entities et fissures de minage
  sont rendues dans la vue, dans l'ordre de vanilla.
- **Objets, mobs et projectiles traversent** les portails : transposition serveur d'après le déplacement du tick
  (`Entity.teleportTo` entre dimensions), vitesse et regard transposés, cooldown de portail vanilla.
- **Portails imbriqués** (un niveau) : les portails visibles dans une vue sont dessinés à leur tour (bit haut du
  stencil, projection de la vue qui les contient). Les portails qui ramènent dans la dimension du joueur (imbriqués
  ou intra-dimension) passent par une **vue secondaire** du monde principal : second `WorldRenderer` sur le même
  monde, sections à reconstruire répercutées depuis le renderer principal.
- **Interaction à travers le cadre** : visée transposée après le raycast vanilla (contour du bloc visé dans la vue),
  frappe, utilisation et minage envoyés au serveur (`portal_entity_action`, `portal_block_action`), qui valide portée
  et portail puis exécute l'action vanilla avec le joueur transposé de l'autre côté le temps de l'appel ; minage
  compté par le serveur avec fissures (`portal_remote_block_breaking`). Pas d'écran ni de monture à travers un portail.
- Vue distante complète : brouillard de la dimension depuis la caméra transposée, ciel (soleil, lune, étoiles), nuages,
  heure et météo alignées sur le monde principal (communes au serveur) ; le fond du cadre prend la couleur du brouillard.
- Zone de vue en boîte mince découpée contre le plan proche (plus de cadre plein écran forcé ni de vide au bord du
  portail) ; brouillard remis à zéro après le rendu des portails (plus de main ni d'objets teintés) ; ciel de la vue
  dessiné avec la projection normale.
- Tests : `BackroomsDoorGameTests` (porte → deux portails liés et propriétaires → cassée → aucun portail), `PortalGameTests`
  (traversée par un joueur serveur avec position, regard et cooldown ; objet lancé dans le cadre ressorti de l'autre côté ;
  payload d'un vrai chunk octet pour octet),
  `TeleportExecutorTest`, `BackroomsDoorTargetsTest`, `PortalRelevanceTest`, `PortalPayloadsTest`,
  `RemoteChunkWindowTest`, `PortalViewTransformTest` ; `PortalFacingTest` réécrit sur les 36 paires d'orientations.
- Textures 32x32 de la porte (`backrooms_door`, `backrooms_door_top`).

### Changed
- `ModConfig.ENABLE_PORTAL_FEATURE` vaut `true` par défaut (`-Dgtgsbackrooms.portal.enablePortalFeature=false` coupe tout).
- `ModConfig.PORTAL_PRELOAD_RADIUS_CHUNKS` vaut `3` par défaut (fenêtre de 7x7 chunks : les arrivées voisines tiennent dans la même vue).
- Arrivée sans saccade : après une prédiction client, le serveur s'aligne sans paquet de téléportation vanilla (plus de
  retour en arrière d'un tick ni de perte d'élan), et le client ignore une confirmation identique à sa prédiction.
- Plus de délai entre deux traversées (`teleportCooldownTicks` vaut 0 par défaut) ; objets et mobs gardent un cooldown fixe.
- Changement de dimension plus fluide : à moins de 8 blocs d'un portail, l'arrivée est chargée sur la distance de vue du
  serveur et la fenêtre synchronisée s'élargit ; au passage, les chunks que le client tient déjà ne sont pas renvoyés.
- Le ciel d'une vue se rapporte à sa caméra (plus de plan sombre tiré depuis la hauteur du joueur dans le Level 0).
- Couverture de tests verrouillée à 100 % des instructions et 96 % des branches du code mesuré : l'agent JaCoCo accompagne
  le serveur GameTest, `jacocoFullReport` fusionne tests unitaires et tests en jeu, `jacocoCoverageVerification` échoue
  sous ces seuils et `build` l'enchaîne. Hors mesure, listé dans `build.gradle` et `docs/TESTING.md` : la colle OpenGL du
  client et les mixins. Branches mortes retirées au passage (gardes sur des invariants : fenêtre d'arrivée toujours
  enregistrée, portails du registre toujours serveur, vue de joueur toujours inscrite, `switch` sans cas inatteignable).
- Tests en jeu déplacés dans un source set dédié `src/gametest` (Loom `fabricApi.configureTests`) : mod séparé
  `gtgs_backrooms_gametest` chargé par `runGameTest` seulement, dossier de run `build/run/gameTest`, arborescence par
  domaine (`support/`, `backrooms/level0/`, `backrooms/portal/`, `feature/`). Plus aucun
  test dans le JAR, ce que la tâche `verifyJarContents` vérifie à chaque `build` (JAR = le mod seul, sans dépendance embarquée).
- Conventions du code vérifiées par Checkstyle (`config/checkstyle/`) dans `check` : sources ASCII, ordre des imports sans
  wildcard, logger unique, Javadoc des types publics, classes utilitaires finales, mise en forme. Zéro avertissement toléré.
- CI : Checkstyle, tests unitaires, tests en jeu, couverture, `build` avec contrôle du JAR, benchmark, rapports publiés ;
  release : `clean build` complet avant publication ; Dependabot pour les actions GitHub.
- Tests en jeu étendus : synchronisation et traversée vers une autre dimension (le Nether sur le serveur de test),
  annonces client, marche détectée par le serveur, changements de monde vanilla, portails orphelins, commande de debug,
  interaction et minage à travers le cadre, objets, mur adaptatif, cas limites de la porte ; joueurs simulés connectés
  (`GameTestPlayers`), arène de test de 24 blocs. Cooldown de traversée de 3 ticks sur le serveur de test. Cas ajoutés
  ensuite : refus d'annonce (portail fermé, sans dimension, hors du cadre, en bateau), entités qui ne traversent pas,
  fenêtres partagées et fusionnées, bordure du monde, opérateur sur bloc de commande, netherrack sans récolte, porte
  tournée, portail de retour reposé.
- Tests unitaires étendus : classes utilitaires, état de rendu, transformation de vue, onglets, point d'entrée, NBT et
  tracker du portail, départage, connexions de cellules, biomes, détails muraux, writer en debug, secteur à remplissage total.
- Plus d'allocation par image ni par tick dans les portails : prédiction de traversée en primitifs, frustums réutilisés
  (`FrustumAccessor`), centre d'arrivée du portail en cache, boîte d'entités reconstruite seulement si la fenêtre change ;
  `ClientPortalIndex` remplace les parcours de toutes les entités par image ; le rendu des vues sort de `RemoteWorld`
  (`RemoteViewRenderer`).
- Code mort retiré : arbitrage entre gaps de couloir voisins (impossible par construction du mélange de hash, génération
  inchangée, empreintes identiques), gardes jamais atteintes dans les biomes, les détails muraux et la porte ;
  départage des grandes pièces partagé (`LevelZeroTieBreak`) ; `ModPerformanceMonitor` sans branche de concurrence ;
  `BackroomsDoorTargets.resolve` par clé de dimension.
- Documentation : plus aucune mention d'un mod tiers dans la doc du projet (`DEPENDENCIES.md`, `AUDIT.md`).
- Le dôme du ciel d'une vue se dessine avec le shader des positions, posé avant `renderSky` comme le fait vanilla : le
  ciel de l'overworld vu depuis une autre dimension n'est plus noir (il prenait le shader position-couleur laissé par le
  fond du cadre, dont l'attribut couleur absent vaut noir).
- La porte n'a plus de collision (le joueur qui en ressort n'est plus poussé par le bloc) et le bas du cadre se pose sur
  le sol trouvé devant elle (porte encastrée ou sur une marche comprises).
- **Géométrie du passage** (`PortalFacing`) : rotation de 180° autour de l'axe vertical du portail. Ce qui
  est devant la source est derrière la destination, entrer dans l'un fait sortir de l'autre, la droite reste la
  droite. Le prototype faisait ressortir le joueur face au portail de sortie.
- Téléportation : décidée uniquement sur une vraie traversée du plan depuis le côté d'entrée (plus de
  déclenchement par proximité), joueurs itérés sans copie, boîte de détection en cache.
- Préchargement : un ticket par fenêtre d'arrivée, partagé entre portails, chargement asynchrone (plus de
  chargement synchrone de 25 chunks sur le thread serveur).
- Entity portail : porte propriétaire en NBT ; `BackroomsPortalEntityRegistry` devient une liste itérée par index.
- Noms alignés sur Yarn : `ClientWorldRegistry` → `RemoteWorldRegistry`, `RemoteLevelRenderer` → `RemoteWorld`,
  `MixinLevelRenderer` → `MixinWorldRenderer`, `PortalDepthTracker` → `PortalRenderState`.
- Commande de dev réduite à `/backrooms portal list` et `/backrooms portal clear` (la porte remplace
  `spawn_test` et `spawn_door`).

### Removed
- Déplacement du joueur pendant la frame pour centrer le renderer distant (remplacé par une substitution de
  position dans `WorldRenderer.setupTerrain` par mixin), miroir des chunks du serveur intégré, `RemoteWorldFactory`,
  `RemoteWorldRenderInfo`, `GameRendererAccessor`, propriété `portalDebugThrowDuringRemoteRender`,
  `PortalFacing.transformYaw` (déprécié).

---


## [0.8.0] - 2026-10-15

Refonte complète du projet : structure, génération, tests, outillage et documentation.
Détail et mesures dans [AUDIT.md](AUDIT.md).

### Changed
- **Outillage** : Gradle 9.8.1, Fabric Loom 1.17.21, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1,
  JUnit 5.11.4, `fabric-loader-junit`, JaCoCo, run `gametest`. Un seul JDK (21) pour tout.
- **Structure** : `backrooms/level/level0` → `backrooms/level0` ; `creative/`, `network/` fusionnés dans `init/` ;
  `perf/section` → `perf` ; features aplaties (`feature/gang/GangBadgeItem`, `feature/cursed/CursedSnackItem`).
- **Génération Level 0** : le pipeline en stages/façades/builders est remplacé par cinq classes lisibles
  (`LevelZeroLayout`, `LevelZeroCellEvaluator`, `LevelZeroTopology`, `LevelZeroLightPattern`,
  `LevelZeroCorridorGaps`) et un writer unique. **Sortie identique** : vérifiée par empreintes sur
  3 seeds x 5 layers x 49 chunks (`LevelZeroGoldenTest`).
- **Performance de génération** : cache de secteurs LRU borné (plus de plafond qui désactivait le cache),
  secteurs 2,5x plus compacts, une lecture de secteur par chunk au lieu d'une par cellule, court-circuit du
  calcul des faces de papier peint hors frontière de région, suppression de la concaténation de chaînes et
  des allocations par colonne, suppression de l'arbitrage lumineux entre voisins (code mort démontré).
- **Rendu client** : `LevelZeroWallpaperBakedModel` lit la pile verticale canonique ; plus de constantes Y héritées (60-67).
- **Portails** : logger commun, feature-flag appliqué au mixin de framebuffer et au rendu stencil
  (plus aucun coût par frame quand désactivé), texture d'entity inexistante remplacée.
- **Config** : `-Dgtgsbackrooms.levelZero.noiseGeometry` est maintenant réellement appliqué (il était ignoré).
- **Documentation** : README, CLAUDE.md, docs techniques et skills réécrits sur l'état réel du code.

### Fixed
- **Frontières de chunk** : la fenêtre de traversabilité déborde de deux cellules autour du chunk.
  Topologie, néons et gaps de couloir sont désormais identiques de part et d'autre d'une frontière
  (auparavant les cellules de bord voyaient un mur fictif hors du chunk ; les gaps sur les couloirs
  traversant une frontière peuvent donc apparaître là où ils étaient absents).
- **Mur adaptatif posé à la main** : le recalcul du masque de faces échantillonnait Y=63 (ancienne géographie),
  il utilise la hauteur du bloc et le layer réel.
- **Version dans les logs** : le log de démarrage affichait le mod_id à la place de la version.
- **Compilation** : zéro warning javac.

### Removed
- Plinthe en bloc (`level_zero_baseboard`, item, modèle de bloc et de plinthe autonome) : rendue invisible et
  sans collision depuis le passage au rendu en overlay. **Les blocs déjà posés disparaissent au chargement.**
  Le rendu des plinthes sur les murs est inchangé.
- Couches sémantiques jamais rendues en jeu : `surface details` (gelée, flag toujours false) et
  `structures` rares (debug uniquement), avec leurs flags `surfaceDetails` et `debugStructures`.
- `ModConfig.ENABLE_GANG_BADGE` (jamais lu), stubs dépréciés, 122 `.gitkeep` de dossiers vides,
  `package-info.java` d'une ligne, `META-INF/mods.toml` (Forge), `atlases/blocks.json` (sans effet),
  `assets/.../items/*.json` (format 1.21.4, ignoré en 1.21.1), docs d'audit de l'ère Forge, spec dupliquée.

---

## [0.7.0] - 2026-04-21

### Changed
- **Backrooms Level 0** : génération structurée autour d'une pile verticale multi-layer déterministe
  (`LevelZeroLayerStackLayout`, `LevelZeroVerticalSlice`), slices seedées par layer.
- **Lumière Level 0** : trame globale en quinconce filtrée par biome ; grandes pièces parfois sombres.
- **Micro-géométrie Level 0** : gaps de couloir alignés sur les directions sémantiques.
- **Performance debug** : moniteur de performance opt-in client et serveur.
- **Tests** : extension de la couverture JUnit du Level 0.

---

## [0.6.0] - 2026-04-15

### Changed
- **Migration Forge → Fabric** : réécriture complète de Forge 62.0.x / MC 26.1 / Java 25 vers
  Fabric Loader 0.16.9 / Minecraft 1.21.1 / Java 21 (`fabric.mod.json`, `Registry.register`,
  `Item.Settings`, `Text.translatable`, réseau `ServerPlayNetworking`, mappings Yarn).
- **Encodage Windows** : `-Dfile.encoding=COMPAT` dans `org.gradle.jvmargs`.
- **`cursed_tree.json`** : `below_trunk_provider` remplacé par `dirt_provider`.

---

## [0.5.0] et antérieurs

Prototype Forge : Gang Badge, Casse-croûte Maudit, Arbre Maudit, première dimension Level 0.
