# Portails — GTG's Backrooms

Les portails relient le monde normal et le Level 0 à travers un **bloc portail** : on voit l'autre
côté à travers le cadre (blocs, entités, block entities, et les portails qui s'y trouvent), par ses deux faces, on le
traverse en marchant dans les deux sens, on en ressort de l'autre côté sans tourner ; objets, mobs et projectiles
passent aussi, la lumière des blocs aussi si le portail la transmet, et l'on peut frapper, miner ou poser à travers le
cadre. Tout le système vit dans `core/portal/` et ses mixins ; il est actif par défaut
(`ModConfig.ENABLE_PORTAL_FEATURE`, `-Dgtgsbackrooms.portal.enablePortalFeature=false` pour le couper). La porte des
Backrooms, retirée pour l'instant, reviendra sur la base du bloc portail.

---

## En jeu

1. Prendre la **Baguette de portail** (`portal_wand`, en tête de l'onglet créatif *GTG's Backrooms*), seule façon de
   créer un portail. Clic droit dans le vide : un écran règle chacun des réglages ci-dessous. Clic droit sur un bloc :
   elle pose à côté, comme un bloc, un **bloc portail** réglé ainsi, sans s'user. Il ne se voit pas et ne bloque rien :
   seul le portail compte, plein cadre, le plan au milieu du bloc (ou sur sa face arrière, réglage `flush`).
   Sa face avant regarde le joueur qui l'a posé. Posé en regardant franchement vers le bas (plus de 60°), le cadre se
   **couche au sol**, face au haut : on y tombe ; vers le haut, il se couche au plafond, face au bas : on y monte. Un
   cadre couché se pose au bloc visé, sa largeur le long de l'est-ouest et sa hauteur le long du nord-sud depuis le
   bloc. Derrière le plan, rien n'est exigé : un cadre se pose contre un mur, sous un toit ou sur un sol plein, et ce
   bloc se traverse (point 4).
2. **Réglages**, portés par la baguette (composant `gtgs_backrooms:portal_settings`, affichés dans l'info-bulle) et
   gardés par le bloc : largeur et hauteur du cadre (1 à 8 blocs, un sur deux par défaut), passage de la lumière des
   blocs (oui par défaut), faces (`double_sided`, deux par défaut), retour (`one_way`, aller-retour par défaut), sortie
   (`fleeting_exit`, durable par défaut), sens de sortie (`exit` : tout droit par défaut, ou `north`, `east`, `south`,
   `west`, le cadre d'arrivée regardant dans ce sens quel que soit celui de l'entrée ; `floor` le couche au sol, on en
   sort vers le haut, `ceiling` au plafond, on en tombe), tirage au sort de l'arrivée
   (`exit_scatter` : 0 par défaut, sinon rayon en blocs, jusqu'à 64, autour du point de départ (les X/Z du bloc, ou la
   sortie fixée), l'arrivée la plus proche étant cherchée de là) et place du plan (`flush` : au milieu du bloc par défaut ; au bord, le plan passe sur la
   face arrière des blocs du cadre, à l'opposé de sa face avant, et le cadre ne semble plus couper un bloc en deux : un
   cadre debout se pose alors à ras d'un mur, un cadre couché à ras du sol ou du plafond ; le cadre d'arrivée suit le
   même plan) et sens de l'arrivée (`upside_down` : droite par défaut ; tête en bas, le cadre d'arrivée est retourné,
   on y voit l'autre côté à l'envers et on en ressort renversé, la caméra se redressant aussitôt ; le retour est
   retourné de même, et le cadre d'arrivée lui-même se pose comme le droit), pose (`placement` : selon le regard par
   défaut ; ou imposée, debout, au sol ou au plafond, quel que soit le regard), destination (`destination` : automatique
   par défaut, sinon une dimension du serveur, Overworld, Nether, End, Level 0 ou une dimension d'un datapack) et sortie
   fixée (`mark` : le bouton « Fixer la sortie ici » de l'écran retient l'endroit où se tient le joueur, sa dimension, le
   bloc de ses pieds et la direction de son regard ; le portail y mène et l'on en ressort dans cette direction, sauf sens
   de sortie imposé ; elle l'emporte sur la destination, qui prend sa dimension ; changer de destination l'oublie, et le
   serveur ne la prend que là où se tient le joueur, à trois blocs près). Toutes les combinaisons s'obtiennent par
   l'écran : le retour y est un seul choix (aller-retour, aller simple, sortie fugace), une sortie fugace n'ayant pas
   de sens en aller simple. Les réglages partent au serveur à la fermeture de l'écran, s'ils ont changé ; une commande
   donne aussi une baguette réglée, par exemple
   `/give @s gtgs_backrooms:portal_wand[gtgs_backrooms:portal_settings={width:4,height:3,transmits_light:false}]`.
   Le bloc portail n'a pas d'item : seule la baguette le pose. Accroupi, il se vise : clic droit avec la baguette, elle
   prend ses réglages (pour en poser un pareil ou le refaire autrement) ; clic gauche, il se casse et ne rend rien. Poser
   un portail fait entendre un portail qui s'ouvre (`ModSounds.PORTAL_WAND_PLACE`, sous-titré).
   Le cadre monte de la hauteur réglée depuis le bloc et s'étend de part et d'autre de sa colonne (une largeur paire
   prend une colonne de plus d'un côté) ; la pose est refusée si le cadre traverserait un bloc.
3. Posé **hors du Level 0**, il mène au layer 0 du Level 0, près des mêmes X/Z : dans la cellule ouverte la plus
   proche où le cadre d'arrivée tient sur toute sa largeur avec un bloc libre devant et derrière chaque colonne (couché,
   là où toute son empreinte est libre, au sol du layer ou sous son plafond), à
   défaut au centre de la cellule ouverte la plus proche, face à un bloc libre. Posé **dans le Level 0**, il ramène à
   la surface du monde normal aux mêmes X/Z, posé sur le haut réel du sol : sur une dalle, un chemin ou un tapis, le
   cadre d'arrivée se pose dessus, au demi-bloc près. C'est la destination automatique ; une **destination choisie** mène
   à sa dimension, les X/Z suivant l'échelle de coordonnées des deux dimensions comme vanilla (un huitième dans le
   Nether) : à ciel ouvert, sur le haut réel du sol ; dans une dimension à plafond (le Nether), sur le premier sol plein
   sous la hauteur du bloc où l'on tient debout, le cadre entier hors des blocs et des fluides, ou au-dessus à défaut ; la
   colonne du point de départ d'abord, puis jusqu'à seize colonnes autour, comme vanilla pour ses portails. Sans sol dans
   ce rayon (le vide de l'End, un lac de lave du Nether), le cadre d'arrivée se pose au point de départ sur une
   plateforme d'obsidienne, la place du cadre et du corps dégagée : on n'arrive jamais dans des blocs, ni dans un fluide,
   ni au-dessus du vide. Une **sortie fixée** mène au bloc où elle a été marquée (ou, avec un rayon de
   tirage, à un sol près d'un point tiré autour d'elle).
4. Le portail est **double face**, comme un trou dans l'espace : chaque côté tient deux portails dos à dos dans le
   même plan, et chaque face mène à la face correspondante de l'arrivée. On le voit et on le traverse par ses deux
   faces, il ne disparaît jamais en tournant autour, et traverser ne fait pas tourner : on entre en allant vers le sud,
   on ressort en allant vers le sud (sauf sens de sortie imposé par le réglage `exit` : on ressort alors vers ce point
   cardinal, par la face avant ; par la face arrière, vers le point opposé). Un cadre couché se franchit aux yeux : au
   sol, on y tombe, et le corps a déjà passé le plan quand la caméra le passe ; au plafond, on y monte, la tête la
   première. Un joueur franchit tout cadre aux yeux, debout compris : sa tête et ses épaules doivent passer
   l'ouverture, dont les quatre bords sont de petites parois (un demi-bloc hors de l'ouverture) ; un cadre trop bas
   l'arrête au front, un cadre surélevé se saute, un cadre étroit se passe aligné (décision du 25 octobre 2026). La sortie garde la place de ce point (tombé par un sol, on ressort à la hauteur où les yeux ont passé le
   plan, avec son élan) ; tout droit, on tombe d'un cadre au sol pour sortir d'un cadre au plafond, et inversement. D'une orientation à une autre (**changement de gravité** : tomber dans un sol et sortir
   d'un mur, monter dans un plafond et sortir d'un mur, marcher dans un mur et sortir du sol ou du plafond), vitesse et
   regard tournent avec le portail : on file hors du cadre d'arrivée et on regarde où l'on va. La chute en cours n'est
   gardée que si le bas reste le bas (du sol au plafond) : tombé de vingt blocs dans un sol et sorti d'un mur, on
   file à l'horizontale sans dégâts à l'atterrissage. Un regard qui devient vertical (on tombe en regardant
   l'horizon, on sort en regardant le ciel) garde le haut de l'écran là où il était : l'image ne tourne pas. Minecraft
   ne penche pas la caméra du joueur : à la traversée d'un portail qui tourne autour d'un axe horizontal, elle part du
   roulis de l'image vue par le cadre et se redresse en moins d'une demi-seconde (`CrossingRoll`), en glissant de la
   place où le cadre montrait l'œil à celle où il ressort ; par un cadre d'arrivée **tête en bas**, ce roulis est un
   demi-tour (couché, l'arrivée fait un demi-tour sur place). Le corps reste debout et sort du bon côté : sous un
   cadre au plafond, il pend tout entier sous le plan ; sur un cadre au sol, il se tient au-dessus ; sorti d'un mur
   après un cadre couché ou tête en bas, ses pieds restent dans la hauteur de l'ouverture. Un portail dont l'entrée ou
   l'arrivée est couchée n'a que sa face avant : de dos, on viendrait du sol ou on ressortirait dans le plafond.
   **Un bloc derrière le plan ne gêne pas** : qui s'engage dans un cadre ouvert (son point de traversée devant le plan,
   en face de l'ouverture, sa boîte contre le cadre) ne heurte plus les blocs tout entiers derrière le plan dans
   l'ouverture, n'y étouffe pas et ne les voit pas en voile à l'écran ; il le reste dix ticks une fois ce point passé
   derrière le plan, le temps de la bascule. Un cadre posé contre un mur, sous un toit (la tête y entre en sautant) ou
   sur un sol plein se traverse comme si le bloc n'y était pas ; autour de l'ouverture, les blocs restent. Un cadre au
   sol est ainsi une **trappe** : il s'ouvre sous qui s'y tient, ou y tombe avec son élan. Qui sort d'un **cadre
   d'arrivée au sol** en est gardé : il s'y pose, même au-dessus du vide, au lieu de repartir aussitôt par le retour ;
   la garde tombe dès qu'il en descend, s'accroupit (c'est ainsi qu'on reprend une arrivée au sol) ou change de
   monde, et une seconde après qu'il s'y est posé (`ArrivalGuard.GRACE_TICKS`) : qui y reste ou y marche sans y prendre
   garde y retombe. Le répit court de la pose, pas de la sortie : qui en jaillit avec son élan retombe dessus avant. On ne sort jamais dans un mur : engagé de biais dans le cadre, on bute sur ce qui
   est de l'autre côté. Qui ne peut pas traverser correctement, le centre hors de l'ouverture (sur un bord), bute sur
   le plan comme sur une **paroi mince**, même pour un cadre en l'air : son corps ne passe pas à moitié dans le portail,
   et sur le bord d'un cadre au sol on se pose sur le plan. De dos, rien n'arrête : on passe au travers sans traverser. On n'entre que si son centre passe dans la largeur du cadre (couché, dans sa largeur et sa
   hauteur, toutes deux horizontales : qui arrive de côté n'entre qu'au-dessus de l'ouverture) : contourner le portail
   en frôlant son bord ne fait ni traverser, ni buter sur ce qui est de l'autre côté.
   Réglé **à une face**, seule la face avant existe, de chaque côté : de dos il n'y a rien, on passe au travers du plan
   sans traverser. Réglé **aller simple**, rien n'est posé à l'arrivée : une fois passé, on ne revient pas par là.
   Réglé **à sortie fugace**, la sortie (le retour, à l'arrivée) n'existe que sous le regard. Pour chaque joueur, elle
   disparaît quand il ne la regarde plus depuis une demi-seconde : ni dessinée, ni heurtée, ni visée, il la traverse
   comme de l'air ; elle revient dès qu'il la revoit à son écran (troisième personne comprise, rien si un mur la cache),
   tant qu'elle existe. Elle disparaît pour tous quand, après avoir été vue, plus aucun joueur ne la regarde pendant
   une demi-seconde, et revient au prochain joueur qui passe l'entrée (un objet, un mob ou un spectateur ne la rouvre
   pas) : elle existe alors de nouveau pour tous, sous le regard. Le serveur ne la repose qu'à l'annonce du passage, que
   le client a déjà fait (traversée prédite) : le client pose aussitôt à l'arrivée un retour prédit pour chaque face, à la
   place exacte du vrai et fermé à la traversée, jusqu'à l'arrivée du vrai (deux secondes au plus) ; sans lui, le joueur
   qui se retourne voyait, le temps d'un aller-retour réseau, ce qu'il y a derrière le cadre (un éclair noir ou blanc). Seul le regard compte, pas la distance : se tenir tout
   près d'elle sans la regarder ne la garde pas, la regarder de loin la garde. Une sortie que personne n'a encore vue
   attend. Qui vient de sortir par elle a une seconde pour se retourner (passé de côté, il l'a juste à côté de lui, hors
   de l'écran). Qui passe en marchant droit devant lui tourne le dos à l'arrivée : elle a disparu quand il se retourne ;
   qui passe à reculons, ou la garde en vue, peut revenir.
   On voit l'autre côté dans le cadre, avec ce qui s'y passe (mobs, joueurs, objets au sol, coffres) et les
   autres portails qui s'y trouvent ; l'eau et le verre s'y voient comme sans portail, et l'eau ou le verre posés
   devant le cadre le laissent voir par transparence. On le traverse en marchant : pas d'écran de chargement, pas
   de saut, la main tenue et les animations continuent, et en se retournant on voit d'où l'on vient. En troisième
   personne, la caméra qui suit le joueur reste de l'autre côté tant que son recul passe par le cadre. Un objet jeté ou une flèche tirée dans le cadre ressort de l'autre
   côté ; un mob qui s'y engage aussi.
5. Viser à travers le cadre vise de l'autre côté : le contour du bloc visé s'y dessine, clic gauche frappe ou
   mine, clic droit utilise ou pose, clic molette choisit le bloc ou la créature. Pas d'écran (coffre, four) ni de
   monture à travers un portail.
6. Un portail ouvert laisse passer le **son** (on entend ce qui se passe de l'autre côté, venant du cadre), les
   **particules** (flammes, fumée, coups, blocs cassés, dans la vue), le **regard des mobs** (un mob hostile qui voit un
   joueur par le portail vient le chercher en le traversant, et un mob frappé à travers lui aussi) et, s'il la
   transmet, la **lumière des blocs** : une torche d'un côté éclaire l'autre côté, d'un niveau de moins, comme à
   travers un bloc d'air. La lumière du ciel ne passe pas.
7. Pour casser le bloc, s'accroupir : accroupi, le regard s'arrête sur le plan du portail ; debout, il passe à travers.
   Casser le bloc retire ses portails ; il ne rend rien (la baguette en pose un autre). Un portail dont le bloc a disparu se
   retire seul dès que le chunk du bloc est chargé ; ceux d'une ancienne porte des Backrooms disparaissent ainsi.

Commande de développement (`isDevelopmentEnvironment()` seulement) : `/backrooms portal list`, `/backrooms portal clear`.

Mesurer les portails (`-PdebugPerformanceMonitor=true`, résumé toutes les `-PperformanceLogIntervalSeconds=N` secondes,
10 par défaut) : à chaque bascule de monde, le client journalise une ligne
`[perf] traversee: vers <dimension> bascule=<ms> pic=<ms> lentes=<n>/119 chunks=<n>` (durée de la bascule elle-même,
pire image et images de plus de 50 ms sur les 120 suivantes, chunks vanilla et distants reçus pendant ce temps), reprise
au F3 en solo. Le résumé `[perf] client:` donne les FPS, l'image la plus longue et le nombre d'images de plus de 50 ms de
l'intervalle, puis le top des sections client (`client.portal.render` : tout le rendu des portails d'une image ;
`client.portal.view` (une vue, ou son rendu complet sous un pack de shaders), `.terrain`, `.entities`, `.lightmap`, `.render_start` (debut de rendu a blanc du renderer) par vue ; `.straddling` les moitiés d'entités dans le monde du
joueur ; `.world_create` et `.mirror_create` la création d'un monde distant ou d'une vue secondaire ; `.chunk_load`,
`.tick`, `.sight`, `.light`), chacune avec ses appels, sa durée, son pire appel et ce qu'elle a alloué. Le résumé
`[perf] serveur:` donne le temps de tick, le top des sections serveur (`portal.teleport.player`, `portal.sync.tick`,
`portal.destination.tick`, `portal.light`), et `reseau=` : paquets et octets envoyés par type de payload des portails
depuis le dernier résumé (`PortalNetwork`, taille encodée avant compression) ; le journal de traversée indique combien de
chunks de l'arrivée le client tenait déjà (ceux-là ne lui sont pas renvoyés).

Mesures de la passe de performance (banc reproductible : Overworld plat, distance de rendu 12, 854×480, moniteur actif,
RTX 5070 Ti et i5-13600KF ; médianes sur 7 à 12 secondes par scène ; les durées sont celles du thread de rendu, le GPU
n'y figure pas) :

| Scène | Avant | Après |
|---|---|---|
| Portail hors de vue, quarante vaches derrière lui (`client.portal.render`) | 8,3 ms/s (4,5 µs par image), `.straddling` 6,4 ms/s | 4,5 ms/s (2,2 µs par image), `.straddling` 0,4 ms/s |
| Une vue stable sur le Level 0 | 187 µs par image, 749 FPS, `.lightmap` 22 ms/s | 185 µs par image, 810 FPS, `.lightmap` hors du top |
| Vue de retour sur l'Overworld, quarante vaches, portail imbriqué (trois vues) | 1 067 µs par image, 548 FPS, `.lightmap` 36 ms/s | 1 000 µs par image, 595 FPS, `.lightmap` hors du top |
| Apparition d'un bloc portail devant le joueur, vers un Level 0 jamais vu | 145 µs par image, 1 037 FPS | 118 µs par image, 1 124 FPS |
| Création d'un monde distant (Level 0, puis Overworld) / d'une vue secondaire, dans l'image | 47 ms, 34 ms / 72 ms | 18 à 25 ms, moins de 11 ms / moins de 11 ms |
| Pire image du banc / images de plus de 50 ms sur le banc | 114 ms / 15 | 85 ms / 8 |
| Réseau : paquet d'entité / de bloc (chunks inchangés, 21,5 Kio par chunk de l'Overworld avant compression) | 34 o / 30 o | 15 o / 6 o |

Le coût CPU d'une vue stable est inchangé : il tient au rendu vanilla de la vue (brouillard et ciel, entités), pas au
mod ; les gains sont sur les à-coups, la lightmap, le repos, le réseau et, par le ciseau, sur le GPU.

Passe du 25 octobre 2026 (plan de `docs/revue/BILAN-CORRECTIONS.md`), sur le banc de production du même PC (mods
de rendu du terrain et de shaders, pack actif, scènes de dix secondes enchaînées par une fonction de tick, mêmes
réglages avant et après) :

| Scène | Avant | Après |
|---|---|---|
| Pose d'un bloc portail vers une zone du Nether jamais générée : pire tick serveur (C56, recherche d'arrivée chunk par chunk) | 424 ms | 25 à 103 ms (trois passes) |
| Une vue stable vers l'End, trois portails en vue, cadre 8 × 8, retour sur la vue stable : FPS, coût des vues, mémoire | — | inchangés à la variance près |
| Dos tourné à trois portails à portée, passé le délai de grâce (C60, fenêtres gardées) : réseau, tick serveur | 0 Kio/s, 3,0 ms | 0 Kio/s, 2,9 ms |

---

## Architecture

```text
core/portal/
|-- entity/       PortalFacing (geometrie du passage), BackroomsPortalEntity (+Registry ; type dans core/registry/ModEntities)
|-- block/        BackroomsPortalBlock, BackroomsPortalBlockEntity, PortalSettings, PortalExit (sens de sortie), PortalArea,
|                 PortalTargets (aiguillage : Level 0 par backrooms/level0/LevelZeroArrival, surface), PortalWayBack (retour)
|-- item/         PortalWandItem (baguette de portail, seule facon d'en creer un ; le bloc portail n'a pas d'item)
|-- light/        PortalLightTransmission, VirtualLightSources, VirtualLightView (lumiere des blocs a travers le cadre)
|-- transition/   PortalRelevance, DestinationLoader, TeleportExecutor, PortalCollision (collision a travers le cadre, blocs
|                 caches derriere le plan), ArrivalGuard (cadre d'arrivee au sol garde pour qui en sort)
|-- interaction/  RemoteInteractionHandler, RemoteMiningTracker (actions a travers un portail, serveur)
|-- propagation/  PortalPursuit, PortalPursuer, PortalPursuitGoal (regard et poursuite des mobs a travers le cadre)
|-- sync/         RemoteDimensionPayload, RemoteChunkPayload, RemoteChunkUnloadPayload, RemoteDimensionReleasePayload,
|                 RemoteBlockUpdatePayload, RemoteEntityPacketPayload, RemoteBlockBreakingPayload, PortalTeleportPayload, PortalCrossPayload,
|                 PortalEntityActionPayload, PortalBlockActionPayload, PortalSightPayload, PortalWandSettingsPayload,
|                 RemoteChunkWindow, PortalViewTracker,
|                 RemoteEntityView, PortalSight (portails que chaque joueur voit, rapportes par son client), PortalNetwork (envoi
|                 des payloads, mesure de ce qui part quand le moniteur de performance est actif)
|-- client/
|   |-- world/       RemoteWorld, RemoteViewRenderer, ViewSectionWalker, RemoteParticles, RemoteWorldRegistry, RemoteSyncReceiver,
|   |                ClientPortalIndex, ClientPortalLight (lumiere transmise, cote client), PortalSightReporter (portails vus),
|   |                StraddlingEntityRenderer (entities a cheval sur un portail, en deux moities), ClippedVertexConsumers
|   |                (moities rognees au plan sur le processeur, sous un pack de shaders)
|   |-- sound/       FrameSoundPlayer (son d'un monde distant rejoue au cadre d'arrivee), PortalSound (portee, volume, meilleur
|   |                cadre), SoundAnchors (vraie source de chaque son joue, replace apres une traversee)
|   |-- view/        PortalRenderState, PortalViewTransform, ViewCone, PortalClipping, ShaderClipInjector, FrameAreaRenderer, PortalStencilRenderer,
|   |                ThirdPersonPortal (camera a la troisieme personne derriere un portail), ViewLightmaps (lightmap de chaque monde vu, une
|   |                fois par tick), LazyVertexBuffers (tampons GL d'un renderer de vue reserves au premier usage),
|   |                ObliqueProjection (plan proche oblique, s'il y a quelque chose a couper), ViewRenderStart (debut de rendu a
|   |                blanc d'un renderer de vue), ShaderPackPortalRenderer (vues sous un pack de shaders), ShaderStageOrigin
|   |                (fabrique qui a fourni un etage de shader compile), CrossingRoll (roulis et glissement de la camera du
|   |                joueur a la traversee), HandMask (pixels de la main du joueur gardes des vues, sous un pack de shaders)
|   |-- transition/  ClientPortalCrossing (prediction et bascule de monde sans coupure), EntityHandover (continuite des entities recreees)
|   |-- interaction/ RemoteTargeting (visee a travers le cadre), RemoteActions (clics)
|   `-- entity/      BackroomsPortalEntityRenderer (cadre sombre de repli)
`-- debug/        DebugPortalCommand
mixin/MixinServerChunkManager          <- relais des changements de blocs (chunk actif)
mixin/MixinWorld                       <- sources de lumiere virtuelles du monde ; relais des changements de blocs d'un chunk
                                          seulement charge (bord de la fenetre), que vanilla tait
mixin/MixinChunkDataSender             <- chunks deja tenus par le client non renvoyes apres une traversee
mixin/EntityAccessor                   <- changer le monde d'une entity sans respawn, ecouteur de deplacement
mixin/ServerChunkLoadingManagerAccessor, EntityTrackerAccessor <- brancher un joueur sur le suivi vanilla des entities d'une autre dimension
mixin/EntityTrackerEntryAccessor       <- position absolue aux clients apres un saut par un portail du meme monde
mixin/WorldAccessor                    <- pluie et orage bruts d'une dimension, envoyes a ceux qui la voient par un portail
mixin/MixinPlayerManager, MixinServerWorld <- sons, evenements de monde et particules relayes a ceux qui voient la dimension
mixin/MixinEntity                      <- collision a travers les portails ouverts (blocs de l'autre cote et sol d'une garde
                                          d'arrivee ajoutes au deplacement) ; pas d'etouffement dans un bloc cache par le cadre
mixin/MixinBlockCollisionSpliterator   <- blocs caches derriere le plan retires de toutes les collisions de blocs d'une entity engagee
mixin/MixinActiveTargetGoal, ActiveTargetGoalAccessor, MixinMobEntity, MixinZombieAttackGoal <- regard des mobs par les portails, poursuite a travers
mixin/MixinWorld, MixinChunkBlockLightProvider, ChunkLightProviderAccessor <- sources de lumiere virtuelles d'un monde, ajoutees aux blocs lumineux
mixin/client/MixinWorldRenderer        <- rendu des portails avant la couche translucide (stencil relache en fin d'image) ou, sous un pack de
                                          shaders, apres l'image (effacement de l'ecran epargne au rendu complet d'une vue), ciel d'une vue rapporte
                                          a sa camera, particules des mondes distants confiees a leur contenu, sections repercutees sur la vue
                                          secondaire, entities a cheval sur un portail laissees a StraddlingEntityRenderer, renderers des vues
                                          recharges avec celui du joueur (pack de shaders active ou coupe, mode graphique) ; rien dans
                                          setupTerrain ni renderLayer, que les mods de rendu reecrivent
mixin/client/MixinClientWorld          <- sons des mondes distants rejoues par le portail qui les rapproche du joueur, copie perimee
                                          d'une entity retiree avant l'ajout de sa remplacante, ou dont la remplacante au meme identifiant reprend l'etat
                                          (EntityHandover.replaceStaleCopy, replaceSameCopy)
mixin/client/MixinVertexBuffer         <- objets GL des tampons de sommets crees pendant la creation d'un renderer de vue reserves au premier usage
mixin/client/MixinWorldRenderStart     <- arrete WorldRenderer.render a sa premiere instruction pendant le debut de rendu a blanc d'un renderer de vue ;
                                          au meme point, pour le monde du joueur, releve le pack de shaders actif pour l'image
mixin/client/MixinShaderStage, MixinShaderProgram <- plan de decoupe des vues ajoute aux vertex shaders au chargement, transmis a chaque shader active ;
                                          etage deja compile redonne seulement aux programmes charges par la meme fabrique (ShaderStageOrigin)
mixin/client/MixinGameRenderer         <- prediction du franchissement avant chaque rendu, visee a travers le cadre apres le raycast
mixin/client/MixinMinecraftClient      <- clics (frapper, miner, utiliser, clic molette) detournes quand le joueur vise a travers un portail
mixin/client/MixinClientPlayNetworkHandler <- nettoyage vanilla de l'ancien monde pendant une bascule, renderer vanilla rendu avant une connexion ou un respawn dans une autre dimension, sons de suivi des entities distantes coupes
mixin/client/MixinFramebuffer          <- attachement profondeur + stencil
mixin/client/MixinSoundSystem         <- vraie source de chaque son que le moteur se met a jouer (SoundAnchors)
mixin/client/MixinCamera              <- roulis restant d'une traversee pose sur la camera du joueur apres son orientation
mixin/client/MixinInGameOverlayRenderer <- pas de voile de mur a l'ecran pour un bloc cache par le cadre ou la tete s'engage
mixin/client/MixinHeldItemRenderer     <- pixels de la main marques dans le stencil pendant son dessin, sous un pack (HandMask)
mixin/client/*Accessor                 <- updateChunks/renderLayer/renderEntity/scheduleChunkRender/applyFrustum, grille de sections et graphe de
                                          culling (ce que fait setupTerrain, refait par la vue ; setupTerrain lui-meme pour la version
                                          d'un autre mod qui dessine le terrain), readLightData du gestionnaire de paquets, Frustum, Camera
                                          (et hauteur des yeux lissee), MinecraftClient, lightmap, gestionnaire de paquets, renderer d'un
                                          ClientWorld, particules, balancement des membres (LimbAnimator), couleur du brouillard
                                          (BackgroundRenderer, rendue aux nuages apres les vues), moteur de son (sources
                                          jouees et gain d'un volume, pour replacer les sons apres une traversee), monde d'un joueur
                                          client (champ final, suivi a chaque traversee pour ne pas garder vivant le monde quitte)
```

### Géométrie (`PortalFacing`)

Chaque portail a une base orthonormée directe (largeur W, hauteur H, normale N = W x H). La normale
pointe vers le **côté d'entrée** : on se tient en +N, on avance vers -N. Le passage vers le portail
d'arrivée est une **rotation de 180° autour de H** : ce qui est devant la source est derrière la
destination, la droite reste la droite, et entrer dans l'un fait sortir de l'autre le long de +N'.
`facingDestination` est donc toujours l'orientation du portail de retour (normale vers la sortie). La face arrière
d'un portail (`opposite()`, même plan, normale opposée) vers la face arrière de l'arrivée fait exactement la même
transformation : deux portails dos à dos de chaque côté font un trou qui se traverse dans les deux sens. Chaque
orientation a sa retournée (`flipped()`, douze orientations en tout) : même plan, même normale, W et H inversées, un
demi-tour autour de la normale. Un cadre d'arrivée retourné (réglage `upside_down`) fait ressortir tête en bas : le
haut de la source devient le bas de l'arrivée. Elle ne sert qu'au passage : le cadre lui-même, son centre et son pied,
se pose avec l'orientation droite (`upright()`).

### Serveur

| Classe | Rôle |
|--------|------|
| `BackroomsPortalBlock` | Bloc invisible, sans collision, orienté vers le joueur qui le pose, ou couché au sol ou au plafond quand il regarde franchement vers le bas ou le haut ; ne se vise qu'accroupi (`ShapeContext.isDescending`), sinon le regard passe et vise par le portail : une plaque au milieu du bloc, ou contre sa face arrière quand le plan est au bord du bloc. Refuse la pose si le cadre traverserait un bloc. |
| `PortalWandItem`, `PortalWandSettingsPayload` | Baguette de portail, seule façon de créer un portail : porte le composant `portal_settings` ; sur un bloc, pose un bloc portail comme vanilla pose un bloc (place libre, état de pose du bloc, écriture, composants recopiés dans la block entity, évènement de monde), avec le son d'un portail qui s'ouvre, sans s'user, et rend un échec à chaque refus pour que le clic ne passe pas à l'usage dans le vide ; sur un portail (visé accroupi), prend ses réglages et le dit au joueur. Reçoit `portal_wand_settings` : ne pose les réglages que si la main désignée tient une baguette. |
| `BackroomsPortalBlockEntity` | Garde les réglages (composant de la baguette qui le pose, NBT, envoyés seuls aux clients), résout sa destination (`PortalTargets`) et pose ses portails : à deux faces (par défaut), quatre, à l'entrée et à l'arrivée une face avant et une face arrière dos à dos, chaque face menant à la face correspondante de l'autre côté ; à une face, la seule face avant de chaque côté ; aller simple, rien à l'arrivée ; à sortie fugace, toutes les 4 ticks, retire les retours (`ExitLost`) quand, après avoir été vus, plus aucun joueur de leur monde ne les regarde depuis une demi-seconde (`PortalSight`), et les repose aussitôt qu'un joueur repasse l'entrée (`onPlayerCrossed`, appelé par `TeleportExecutor`, pas pour un spectateur) ; `ExitLost` n'est relu que pour une sortie fugace. Le chunk d'arrivée se charge par un ticket, sans bloquer le serveur, et les retours y sont posés dès qu'il est là. Remplace un portail qui ne correspond plus (bloc tourné, réglages ou destination relus), repose un retour disparu après deux vérifications espacées, les retire tous quand le bloc est cassé. Relu à une autre taille, le cadre d'arrivée garde son pied et son centre suit la nouvelle taille. |
| `PortalSettings`, `PortalExit`, `PortalArea` | Réglages d'un portail (largeur et hauteur de 1 à 8, lumière transmise, faces `double_sided`, retour `one_way`, sortie `fleeting_exit`, sens de sortie `exit` tout droit, cardinal, par le sol ou par le plafond, rayon `exit_scatter` du tirage au sort de l'arrivée, plan `flush`, arrivée `upside_down` ; format du composant, en NBT sur le réseau) ; blocs occupés par un cadre, son centre et son pied (plan au milieu des blocs ou sur leur face arrière, `flush`, une colonne de plus d'un côté pour une largeur paire), et son bloc ancre retrouvé depuis son seul centre (côté client). |
| `PortalTargets`, `LevelZeroArrival`, `PortalMark`, `PortalPlacement` | Aiguillage de la destination (`PortalTargets`) : sortie fixée (`PortalMark`, sur son bloc ou tirée au sort autour), sinon dimension choisie ou automatique, X/Z à l'échelle des deux dimensions ; hors du Level 0, arrivée sur le haut du sol à ciel ouvert ou sur le premier sol où l'on tient debout sous le départ dans une dimension à plafond, le cadre entier hors des blocs et des fluides, colonne de départ puis anneaux jusqu'à seize colonnes (`arrive`) ; sans sol, plateforme d'obsidienne au point de départ, place dégagée (`onPlatform`). Pose du bloc selon le réglage `placement` (`PortalPlacement`). Arrivée dans le Level 0, que le Level 0 calcule lui-même (`backrooms/level0/LevelZeroArrival`) : cellule ouverte la plus proche où le cadre tient sur toute sa largeur, un bloc libre devant et derrière chaque colonne (labyrinthe réellement généré, zone sûre de l'origine et micro-géométrie comprises), sortie tout droit (à l'opposé de l'entrée) ou dans le sens imposé par le réglage `exit`, si elle tient ; à défaut, centre de la cellule ouverte la plus proche, face à un bloc libre. Overworld : surface à l'aplomb. Dans les deux mondes, l'aplomb est celui du bloc, ou des X/Z tirés au sort dans le rayon `exit_scatter` autour de lui (tirage à la résolution, une fois par bloc). |
| `PortalLightTransmission` | Toutes les 2 ticks, pour chaque monde (serveur, et client par `ClientPortalLight`) : chaque bloc du cadre d'un portail ouvert, visible et qui transmet la lumière devient une source virtuelle de la lumière du bloc correspondant de l'autre côté, moins un niveau. Les sources, immuables (`VirtualLightSources`), sont publiées sur le monde (`MixinWorld`) **avant** d'être signalées au moteur, qui les lit sur son propre thread (`MixinChunkBlockLightProvider`) ; une source publiée mais pas encore appliquée (chunk qui vient de charger) est signalée de nouveau, et tout le cadre d'un portail compté pour la première fois (apparu, ou revenu avec son chunk) l'est aussi, la lumière sauvée avec le chunk pouvant venir d'une source éteinte depuis. Les deux côtés d'une paire s'éclairent l'un l'autre : sans vraie source, chaque aller-retour coûte deux niveaux et tout s'éteint. |
| `BackroomsPortalEntity` | Données canoniques synchronisées par `DataTracker` (orientations, arrivée, taille, visibilité, ouverture, lumière transmise), bloc propriétaire en NBT, boîtes de détection et de traversée en cache ; passage d'un point d'un côté à l'autre (`toDestination`, `fromDestination`). |
| `BackroomsPortalEntityRegistry` | Portails serveur chargés, tous mondes confondus, lus par index par la téléportation, le préchargement et la synchronisation ; toutes les 100 ticks, retire ceux dont le bloc propriétaire a disparu, ne les tient plus pour siens ou est dans une dimension absente (seulement si le chunk du bloc est chargé). |
| `PortalRelevance` | Deux critères. À portée (64 blocs, la distance de rendu), quelle que soit la direction du regard : le préchargement de l'arrivée et la synchronisation de la fenêtre, pour que le regard qui revient sur un portail trouve l'autre côté au lieu du brouillard (décision C60 du 25 octobre 2026). À portée et devant le joueur, ou à portée d'activation (16 blocs) quelle que soit la direction : le rendu. |
| `DestinationLoader` | Un ticket de chunk par fenêtre d'arrivée d'un portail à portée d'un joueur (quelle que soit la direction de son regard), partagé entre portails ; à moins de 8 blocs d'un portail, il couvre toute la distance de vue du serveur. La fenêtre reste chargée dix secondes après que plus personne n'est à portée son portail, plus longtemps que la fenêtre du client : un coup d'œil ailleurs ne décharge rien. Chargement asynchrone. Un joueur qui change de dimension laisse en plus un **ticket de départ** sur le côté quitté (distance de vue du serveur, dix secondes) : vanilla retire ses tickets aussitôt, et sans relais ce côté se déchargeait en quelques ticks, avant que la fenêtre du portail de retour ne le reprenne. |
| `TeleportExecutor` | **Joueurs** : applique les franchissements annoncés par le client (`portal_cross`) après vérification (point qui décide de la traversée près du cadre : les yeux pour un joueur ou un cadre couché, les pieds sinon ; vitesse et regard finis et bornés ; cadre qui n'est pas l'arrivée gardée du joueur), en s'alignant sur lui sans paquet de téléportation vanilla (le joueur est déjà de l'autre côté et garde son élan), et en secours détecte lui-même le passage des pieds entre deux ticks (segment position précédente → courante ; au point de passage, le centre de l'entité doit être en face de l'ouverture et sa boîte toucher le cadre : passer à côté ressortirait dans le mur qui encadre l'arrivée) avec cinq ticks de grâce, joués même si le joueur s'est arrêté depuis (pour un cadre couché, le passage des yeux ; jamais par son arrivée gardée) ; un changement de dimension suit la séquence vanilla sans paquet de respawn. **Objets, mobs, projectiles** : pour chaque portail, les entités de sa boîte de traversée (cadre élargi de trois blocs, la vitesse d'une flèche) dont le déplacement du tick a franchi le plan, hors de leur arrivée gardée, sont transposées (`Entity.teleportTo` entre dimensions, repositionnement sinon) avec le cooldown de portail vanilla. La sortie (`exitPoint`) garde la place du point qui décide (les yeux pour un cadre couché), le corps debout sous lui, puis le tient du côté de la sortie et dans l'ouverture (pendu sous un plafond, au-dessus d'un sol, les pieds dans la hauteur d'un mur quand le bas n'est plus le bas). Qui sort d'un cadre d'arrivée au sol en est gardé (`ArrivalGuard.arrive`). Joueurs, portails et passagers sont exclus de ce chemin : un passager suit son véhicule. **Attelages** (`executeTree`) : un véhicule racine (bateau, wagonnet, monture) emmène tout ce qui le monte ; vanilla fait changer de monde ses passagers et les remet en selle sur sa copie, et les joueurs, qu'un `teleportTo` vanilla ferait passer par l'écran de chargement, descendent d'abord, traversent par leur propre chemin (bascule menée par le serveur : en selle, le client ne prédit pas) et remontent sur ce qu'ils montaient, retrouvé de l'autre côté par son UUID. La traversée d'un joueur monté se détecte sur le trajet de son véhicule racine entre deux ticks (mené par un client, il ne bouge qu'à ses paquets : `prevX` n'en voit rien) ; dans le même monde, où le véhicule garde son instance, le client du joueur reçoit lui-même ses passagers (`EntityPassengersSet`) et la position de son véhicule (`VehicleMove`, la seule qu'il accepte pour un véhicule qu'il mène). Vers une dimension absente, rien ne part et chacun remonte où il était. |
| `PortalViewTracker` | Synchronisation serveur → client des chunks, des blocs et des entités (voir ci-dessous). |
| `PortalSight`, `PortalSightPayload` | Les sorties fugaces que chaque joueur voit, rapportées par son client (`portal_sight`, 64 au plus), et sa dernière arrivée par un portail : passé par l'entrée d'un bloc, il ressort par sa sortie et la voit encore une seconde, le temps de se retourner ; repasser par une sortie fugace n'en donne pas. Seul le regard compte : un rapport fait dans un autre monde que celui du joueur est ignoré, un rapport de plus de 40 ticks ne compte plus, un joueur sans rapport ne voit rien ; oublié à la déconnexion, au changement de monde vanilla, au respawn et à l'arrêt du serveur. `seesAny` juge une sortie pour son bloc, `sees` un portail pour la traversée détectée par le serveur, qui ne fait passer une sortie fugace qu'à qui la voit. |
| `RemoteEntityView` | Par joueur et par dimension distante : envoie la pluie et l'orage bruts de cette dimension (`WorldAccessor` : `getThunderGradient` les multiplie) quand ils changent, branche le joueur comme auditeur du suivi vanilla (`EntityTracker.listeners`) de chaque entité de sa fenêtre, lui envoie les paquets d'apparition, enveloppe tout ce que le suivi émet ensuite dans `portal_remote_entity`, et débranche ce qui sort de la fenêtre (destruction envoyée). Une entité arrivée par un portail dans la fenêtre est suivie tout de suite (`PortalViewTracker.onEntityArrived`), sans attendre l'alignement périodique : sa copie part au même tick que la destruction de l'ancienne. Une entité qui quitte son monde (morte, retirée, chunk déchargé, autre dimension) est effacée de même chez ceux qui la voyaient (`onEntityUnloaded`) : vanilla leur envoie sa destruction sans enveloppe, que le client appliquerait à son monde principal. Comme vanilla (`canBeSpectated`), un joueur spectateur n'est montré qu'aux spectateurs. |
| `PortalCollision` | Collision à travers les portails ouverts. Une entité est **engagée** dans un cadre quand son point de traversée (les yeux pour un joueur ou un cadre couché, les pieds sinon) est devant le plan, en face de l'ouverture, et que sa boîte touche le cadre. Elle heurte alors aussi les blocs de l'autre côté, au-delà du plan d'arrivée, ramenés de ce côté derrière le plan (`withOtherSide`, par `MixinEntity` sur `findCollisionsForMovement`, le pas comme la marche montée) : on bute sur les murs qui encadrent l'arrivée, on monte sur un sol plus haut comme sur une marche, personne ne ressort dans un bloc. Le client l'applique à son joueur (index des portails et mondes distants), le serveur aux mobs, objets et projectiles, pas aux joueurs (il refuserait des pas que le client, sans monde distant, aurait faits librement). Les blocs de son côté tout entiers derrière le plan, dans l'ouverture prolongée, **n'existent plus pour elle** (`tunnel`, `hides`) : retirés de toutes ses collisions de blocs (`MixinBlockCollisionSpliterator` : déplacement, place libre, poussée hors des blocs, contrôle des pas d'un joueur par le serveur, qui l'applique donc aussi aux joueurs), de l'étouffement (`MixinEntity` sur `isInsideWall`) et du voile de mur à l'écran (`hiddenFrom`, `MixinInGameOverlayRenderer`). L'engagement est retenu (`CROSSING_TICKS`, dix ticks, oublié par `tick`) tant qu'elle reste en face de l'ouverture : son point de traversée vient de passer le plan et la bascule suit ; la face arrière du cadre traversé (même centre, dos à dos) ne compte pas pendant ce temps, elle cacherait le côté d'où l'on vient. Du côté d'entrée, une entité dont le point de traversée est hors de l'ouverture (sur un bord) et dont la boîte touche le cadre bute sur l'ouverture fermée (`walls`, `closedOpening` : du plan à un demi-bloc derrière), même pour un cadre en l'air ; un joueur engagé dont la tête ou les épaules dépassent de l'ouverture bute sur les bords du cadre (`frameEdges` : quatre parois d'un demi-bloc hors de l'ouverture, du plan à un demi-bloc derrière) et glisse le long jusqu'à s'aligner, un cadre surélevé se sautant ; de dos, rien ; un portail fermé n'arrête rien ; comme les blocs de l'autre côté, le client l'applique à son joueur, le serveur aux autres entités. Le cadre d'arrivée gardé d'une entité (`ArrivalGuard`) ne l'engage pas et se ferme sous elle de la même façon. Chaque côté tient son suivi sur son fil (`Side`, choisi par `Worlds.side` : le client le sien pour ses mondes). |
| `ArrivalGuard` | Garde d'arrivée au sol : qui sort d'un cadre au sol (traversée vue par le serveur ou prédite par le client ; après une bascule menée par le serveur, le cadre sur lequel le client est posé, `arriveOnFrame`) en est gardé ; ses portails ne le font ni traverser ni tomber, un sol mince ferme leur ouverture sous lui, même au-dessus du vide. La garde tombe au tick suivant dès qu'il ne se tient plus au-dessus de l'ouverture, s'accroupit, change de monde ou disparaît ; toute autre traversée la remplace ou la lève. |
| `RemoteInteractionHandler` | Actions à travers un portail : valide portail (devant le joueur, son cadre lui-même à portée : près du plan mais loin à côté du cadre, rien), dimension et cible (à portée du point où le regard ressort), puis exécute l'action vanilla (`attack`, `interact`, `onUse`/`useOnBlock`) avec le joueur **transposé** de l'autre côté le temps de l'appel, écouteur de déplacement coupé pour que le gestionnaire d'entités ne le voie pas bouger. |
| `RemoteMiningTracker` | Minage à travers un portail, compté par le serveur seul : progression par tick (`calcBlockBreakingDelta`), fissures envoyées au joueur, aux joueurs de la dimension d'arrivée et, relayées, à ceux qui la voient par un portail, casse comme `tryBreakBlock` (événements Fabric compris), abandon si le client lâche, si le bloc disparaît, si le joueur n'est plus à portée ou meurt ; fissures effacées s'il se déconnecte. Créatif : casse immédiate. |

### Protocole de synchronisation

Vanilla n'envoie au client que la dimension où il se trouve. Pour montrer l'autre côté, le serveur
envoie lui-même les chunks, les blocs et les entités de la destination :

| Payload | Contenu | Quand |
|---------|---------|-------|
| `portal_remote_dimension` | dimension + type de dimension + index | une fois par dimension distante et par vue du joueur (sa synchronisation repart de zéro à chaque changement de monde) ; l'index, l'ordre des annonces de la vue, désigne la dimension dans les payloads fréquents (un octet au lieu de l'identifiant), le client gardant la dernière dimension annoncée sous chaque index |
| `portal_remote_chunk` | `ChunkData` + `LightData` vanilla | chunk entré dans la fenêtre voulue, au plus 8 par joueur toutes les 5 ticks |
| `portal_remote_chunk_unload` | index de dimension + position du chunk | chunk sorti de la fenêtre voulue ; une dimension que plus aucun portail ne montre est gardée 5 secondes avant d'être déchargée (un demi-tour ne renvoie pas tout) |
| `portal_remote_dimension_release` | index de dimension | plus aucun portail à portée ne montre cette dimension depuis `remoteWorldReleaseSeconds` (60 s par défaut, jamais moins que le délai de grâce), bien après le déchargement de sa fenêtre : le client jette son monde distant (chunks, entités, renderer, vues secondaires de ce monde) ; la dimension garde son index et est annoncée de nouveau avant tout nouvel envoi (décision C63 du 25 octobre 2026) |
| `portal_remote_block_update` | index de dimension + position + `BlockState` | changement de bloc dans un chunk déjà envoyé (relayé par `MixinServerChunkManager.markForUpdate`, et par `MixinWorld` dans un chunk seulement chargé, dédupliqué par tick) ; suivi, comme chez vanilla, des données de sa block entity (texte d'un panneau, motifs d'une bannière) par `portal_remote_entity` |
| `portal_remote_entity` | index de dimension + un paquet de jeu vanilla (`EntitySpawn`, `EntityS2C`, `EntityTrackerUpdate`, équipement, passagers, effets, animations, destruction…, pluie et orage de la dimension), encodé avec le codec `PlayStateFactories.S2C` des registres de la connexion | tout ce que le suivi vanilla d'une entité de la fenêtre émet, ainsi que les sons, évènements de monde, particules et fissures de la fenêtre (`relayToViewers`), et les données d'une block entity qui change ; le client l'applique à son monde distant comme à son propre monde (les données d'une block entity et les fissures, que vanilla poserait sur le monde du joueur, directement au monde distant) |
| `portal_remote_block_breaking` | dimension, position, étape (-1 efface) | fissures d'un bloc miné à travers un portail, pour le mineur (les autres les reçoivent comme vanilla, relayées) |
| `portal_teleport` | dimension, type, position, regard, vitesse | le joueur vient de passer un portail : le client bascule sur son monde distant (ou aligne sa position s'il avait déjà prédit) ; sert aussi à ramener une prédiction refusée |
| `portal_cross` (client → serveur) | UUID du portail, position, vitesse, regard | la caméra du client a franchi le plan ; le serveur vérifie la plausibilité et applique exactement cet état |
| `portal_entity_action` (client → serveur) | portail, dimension, entité, frapper ou utiliser | clic sur une entité vue à travers un portail |
| `portal_block_action` (client → serveur) | portail, dimension, `BlockHitResult`, début/abandon du minage ou utilisation | clic sur un bloc vu à travers un portail |
| `portal_wand_settings` (client → serveur) | main (principale ou autre), réglages d'un portail (format du composant) | à la fermeture de l'écran de la baguette, si les réglages ont changé ; le serveur les pose sur la baguette de cette main |
| `portal_sight` (client → serveur) | monde du client, portails que la caméra voit (UUID, 64 au plus) ; un rapport fait dans un autre monde que celui où le serveur tient le joueur est ignoré | toutes les 4 ticks si la liste change, au changement de monde (après une image du nouveau), au moins chaque seconde ; rien sans rendu distant |

Toutes les 5 ticks, pour chaque joueur : portails de son monde à sa portée (64 blocs, quelle que soit la direction du regard) menant **ailleurs** →
par dimension distante, un **foyer** (le portail le plus proche) et la fenêtre de chunks
(`PORTAL_PRELOAD_RADIUS_CHUNKS`, 3 par défaut = 7 x 7 chunks, +2 quand le joueur est à moins de 8 blocs du portail) autour de son arrivée, plus celles des
autres portails qui tiennent dans la carte client (`RemoteChunkWindow.fitsAround`). Les déchargements
partent avant les chargements ; les entités de la boîte englobant les chunks envoyés sont ensuite alignées. Une
dimension que plus aucun portail à portée ne montre garde sa fenêtre 5 secondes (un pas au-delà ne renvoie pas tout),
puis son monde distant est libéré chez le client après `remoteWorldReleaseSeconds` (60 s par défaut).
À moins de 8 blocs d'un portail, le serveur charge l'arrivée sur toute sa distance de vue (`DestinationLoader`) : la
traversée ne déclenche pas de génération. Au passage, les chunks que le client tient déjà ne lui sont pas renvoyés par
vanilla (`MixinChunkDataSender`) : ils sont à jour et déjà construits, le voisinage immédiat ne clignote ni ne ralentit.
Ils ne valent que 100 ticks : un chunk que vanilla ne propose qu'ensuite (hors de la distance de vue) a pu être oublié
par le client, il part comme les autres. Un chunk tenu qui change part aussi : tant qu'un chunk attend son tour chez
vanilla, aucun changement de bloc ne lui est envoyé (vanilla compte l'envoyer entier) ; de même pour un chunk dont un
changement n'était pas encore relayé au moment du passage. Pendant une traversée prédite que le serveur n'a pas encore
confirmée, le client applique au monde qui vient de devenir le sien les chunks et les blocs encore en route, que le serveur
compte comme reçus. Changement de dimension vanilla ou respawn : les deux côtés repartent de zéro, et plus rien n'est
tenu. Après tout changement de monde, la vue du joueur repart au tick suivant, sans attendre le passage des 5 ticks :
les copies que la bascule efface, dont le client diffère le retrait d'une dizaine de ses ticks, sont renvoyées à temps
même par un serveur lent.
Côté client, les récepteurs s'exécutent tels que Fabric les appelle, sur le thread de rendu et dans l'ordre des paquets : les
différer avec `client.execute` les ferait passer derrière les paquets vanilla déjà en file (`ReentrantThreadExecutor`), et
`portal_teleport` arriverait après les premiers chunks du nouveau monde.

### Client

| Classe | Rôle |
|--------|------|
| `RemoteParticles` | Particules d'un monde distant : le gestionnaire de particules vanilla est unique (atlas, fabriques), seul son contenu (monde et files) est échangé avec celui du monde distant le temps d'y ajouter, d'y faire vivre ou d'y dessiner ses particules. Tout le code vanilla sert tel quel, les mondes ne se mélangent jamais ; à la traversée, le contenu du monde d'arrivée devient celui du gestionnaire et celui de l'ancien monde le suit. |
| `RemoteWorldRegistry` | Un `RemoteWorld` par dimension annoncée (et, pendant une traversée prédite pas encore confirmée, celui qui vient de devenir principal, pour les chunks et les blocs encore en route), plus les **vues secondaires** (créées à la demande pour les portails qui ramènent dans le monde principal, une par monde, gardées quand leur monde devient distant et reprises quand il redevient principal) ; tout est jeté quand vanilla change le monde principal, un monde à la fois quand le serveur le libère (`portal_remote_dimension_release`), et les tampons de dessin d'un renderer libéré (hors du tas Java, jamais rendus par vanilla) servent au suivant. Tique les entités des mondes distants et l'horloge de chaque vue à chaque tick client. |
| `PortalSightReporter` | À chaque tick, juge les sorties fugaces que le joueur regarde : celle derrière laquelle la caméra est passée en troisième personne, et celles dessinées à la dernière image dont l'un des neuf points du cadre se voit sans cube plein et opaque devant (verre, feuilles, dalles, portes laissent voir ; parcours bloc par bloc, sans allocation). Une sortie fugace hors de son regard depuis plus d'une demi-seconde lui est cachée (une seconde de répit après un passage de portail) ; dans le champ, le rendu l'essaie à chaque image et la montre dès qu'elle se voit (`revealed`). Envoie au serveur les sorties vues quand la liste change (au plus toutes les 4 ticks), au changement de monde (après une image du nouveau, jamais avec la liste de l'ancien) et au moins chaque seconde ; sans rendu distant (stencil refusé, rendu coupé), le joueur ne voit aucune sortie fugace et rien ne part. |
| `ClientPortalIndex` | Portails chargés par monde client (principal ou distant), tenus à jour par les événements de chargement d'entités : les parcours par image (visibilité, prédiction de traversée, visée) lisent cette liste au lieu de toutes les entités. Dans le monde du joueur, `portals` ne rend que ceux qui existent pour lui, sans les sorties fugaces qu'il ne regarde pas (ni dessinées, ni traversées, ni heurtées, ni visées, et les sons n'y passent pas) ; `allPortals` les rend toutes, pour juger le regard et pour la lumière transmise. Un portail attendu du serveur peut y être prédit (`predict`, retour d'une sortie fugace au passage de son entrée) : il compte comme les autres, s'efface à l'arrivée du vrai (même centre, même orientation) ou de lui-même au bout de son délai. |
| `StraddlingEntityRenderer` | Entités à cheval sur un portail, joueur compris, dessinées en deux moitiés coupées au plan (seuls les portails à portée de la caméra, la distance de rendu des portails plus 16 blocs, comptent, pour ce rendu comme pour le dessin vanilla qu'il remplace : au-delà, le portail n'est pas dessiné et rien de ce qu'il coupe ne se voit) : dans le monde du joueur après les vues (ou dans une vue qui couvre l'écran), la partie devant la face qui les coupe et la partie sortie d'une arrivée, puis la partie encore derrière l'arrivée d'un portail de ce monde, ramenée devant son cadre ; dans la vue d'un portail, la partie passée de ce qui est engagé dans son cadre, transposée. Une entité est à cheval dès que son modèle touche le plan : sa boîte de collision élargie de tous côtés d'un demi-bloc (la marge que vanilla prend pour savoir si une entité est à l'écran), d'un bloc pour une entité vivante (membres qui balancent, épée levée, nage et vol plané). Décisions sur la position affichée de l'image ; rotation du passage précalculée pour chaque paire d'orientations, recherches dans des boîtes en cache, sans allocation propre par image (la recherche d'entités passe par l'index d'entités vanilla). Sous un pack de shaders, dont les shaders ignorent la distance de découpe, chaque moitié est rognée au plan sur le processeur (`ClippedVertexConsumers` : chaque quadrilatère est coupé avant d'être écrit, un triangle ou un pentagone restant s'écrit en quadrilatères) ; dans le rendu complet d'une vue à travers un cadre, la projection à plan proche oblique de la vue coupe déjà au plan d'arrivée. |
| `RemoteViewRenderer` | Dessin d'un `RemoteWorld` depuis la caméra transposée, avec la projection du joueur et le brouillard de sa dimension (couleur comprise, `applyFogColor` : la vue ne se fond pas dans le brouillard du monde du joueur, une bande noire sur l'horizon de l'Overworld vu du Level 0) : ciel (shader des positions), couches de blocs, entités, block entities, fissures de minage, contour du bloc visé, particules, nuages, pluie et neige (`renderWeather` vanilla sur le renderer de la vue), le tout sous le plan de découpe du portail d'arrivée (`PortalClipping`). Les sections dessinées viennent d'un parcours synchrone propre à la vue (`ViewSectionWalker`), limité au frustum et au volume visible à travers le cadre (`ViewCone`) ; entités et portails imbriqués passent le même volume. La grille de sections du renderer est ancrée sur l'arrivée du portail, pas sur la caméra, et ne se recentre que si l'ancrage s'en éloigne de plus de deux chunks ; une seule vue par renderer et par image place la grille et fait avancer le graphe de culling vanilla (ce que fait `setupTerrain`, refait par la vue autour de son ancrage : le `setupTerrain` vanilla centre sur le joueur, les mods de rendu le réécrivent, et Fabric y déclenche ses évènements de rendu du monde avec un contexte que seul le rendu du joueur prépare), pour garder ce graphe chaud en vue d'une traversée. Quand un autre mod dessine le terrain (grille vanilla créée sans portée, `foreignTerrain`), la vue lui laisse choisir et dessiner ses sections (son `setupTerrain`, son `renderLayer`) et se dessine tout entière sous une projection à plan proche oblique (`ObliqueProjection`) : ses shaders ignorent la découpe des nôtres. Sous un pack de shaders, la vue est au contraire un rendu complet de son renderer (`renderWhole`), que le pack dessine en entier. |
| `RemoteWorld` | `ClientWorld` + `WorldRenderer` dédié ; état du monde distant (chunks, lumière, paquets d'entités, données des block entities et fissures, que vanilla poserait sur le monde du joueur, heure du monde principal, météo de sa propre dimension reçue du serveur), le dessin étant confié à `RemoteViewRenderer`. Le ciel vanilla se rapporte à la caméra de la vue (positions remplacées dans `renderSky` : couleur du ciel, plan sombre sous l'horizon) et se dessine, comme vanilla, avec le shader des positions posé juste avant `renderSky`. Charge les chunks comme `ClientPlayNetworkHandler` (sections, heightmaps, lumière, statut de sections), applique les paquets d'entités avec le gestionnaire de paquets temporairement rebranché sur lui, tique ses entités (interpolation, animations) et, tant qu'une vue le montre, ses particules et les effets d'ambiance de ses blocs ; ses sons sont rejoués au cadre (`FrameSoundPlayer`). Il suit l'heure et la météo du monde principal (communes au serveur). Une vue secondaire partage un monde que vanilla entretient déjà (celui du joueur, ou un monde distant qui l'a été) et ne possède que son renderer, dont les sections à reconstruire suivent celles du renderer de son monde ; elle reste liée à son monde quand il devient distant. La lumière d'un chunk distant passe par le `readLightData` du gestionnaire de paquets, le monde du gestionnaire basculé le temps de l'appel : un mod qui suit l'arrivée des chunks par là (un mod de rendu ne construit une section qu'après sa lumière) voit les chunks distants comme les autres. |
| `FrameAreaRenderer` | Zone de vue d'un portail : le cadre, plat. Quand la caméra est dans l'encadrement, à moins de 20 cm du plan, une boîte mince (le cadre prolongé de 20 cm derrière son plan), découpée en logiciel contre le plan proche de la caméra et refermée par un polygone : elle couvre exactement ce que la caméra voit à travers, sans plein écran forcé ni vide. Sa marque dans le stencil (`mark`) est tirée vers la caméra (`glPolygonOffset`) : un cadre posé à ras d'un bloc ne dispute plus la profondeur à la face de ce bloc (scintillement). Contre un cadre plaqué sur un mur (ou au sol sur un sol plein), la face du bloc est dans le plan : de près, l'imprécision des positions du terrain dépasse ce décalage, et la zone est donc aussi avancée d'un dixième de millimètre vers la caméra (ses bords bougent d'un dixième de pixel vus à 30 cm) ; le polygone qui referme la boîte, 2 mm au-delà du plan proche du jeu, se marque sans test de profondeur. Sans cela, le mur apparaissait à la place de la vue dans les derniers centimètres avant de passer. Ailleurs, une boîte donnerait au portail une épaisseur (ses flancs montraient la vue à côté du cadre, et elle basculait d'un coup de l'autre côté du plan en passant d'une face à l'autre) : vu de profil, le cadre plat n'est plus qu'un trait. Elle se dessine sans élimination des faces arrière, le temps de son seul dessin : les couches de terrain vanilla supposent cette élimination active sans la rétablir, et une vue dessinée sans elle montrerait les deux faces de la glace, du verre et de l'eau (deux fois plus opaques qu'en vrai). |
| `PortalStencilRenderer` | Au milieu de `WorldRenderer.render`, après les blocs opaques, les entités et les block entities et avant tout ce qui est translucide (chaque vue se dessine sous un ciseau d'écran, le rectangle de son cadre à l'écran, coupé avec celui de la vue qui la contient pour une vue imbriquée : le GPU ne traite aucun fragment hors du cadre, quelle que soit la couche, avant même le stencil ; un cadre qui touche le plan proche de la caméra n'en a pas) : l'eau, le verre, les parties translucides des entités, les particules, les nuages et la pluie du monde du joueur se fondent ensuite par-dessus la vue. Les vues ont leurs propres tampons de dessin (ceux du monde principal attendent encore ses couches translucides), la matrice de vue empilée par vanilla est neutralisée le temps des portails, et brouillard et éclairage des entités du monde du joueur sont rétablis après. La valeur du stencil est le **niveau d'imbrication** (0 le monde du joueur, 1 une vue, 2 une vue imbriquée). Les portails se dessinent un par un, du plus proche au plus lointain : la zone de vue passe du niveau courant au suivant là où elle n'est cachée par rien, sa profondeur est poussée au plan lointain, le fond prend la couleur de brouillard de la vue, la vue distante est dessinée depuis la caméra transposée (lightmap recalculée pour cette dimension), puis ses **portails imbriqués** (sous le plan de découpe de la vue qui les contient), et enfin la zone reprend sa vraie profondeur (la face la plus proche) et redescend au niveau courant. Deux portails l'un derrière l'autre ne se mélangent pas : le plus proche cache l'autre. En troisième personne, quand le recul de la caméra traverse un portail (`ThirdPersonPortal`, juste après une traversée), l'image entière est la vue de l'autre côté depuis l'image de la caméra, reculée comme vanilla contre les blocs de là-bas : tout l'écran passe au niveau 1, la vue s'y dessine sans plan de découpe ni volume limité, avec ses portails imbriqués, et le stencil refuse la suite du rendu du monde du joueur jusqu'à la fin de l'image. |
| `ViewLightmaps` | Lightmap de chaque monde vu par un portail : une texture par monde, calculée par le gestionnaire vanilla (même algorithme) une fois par tick client, liée aux shaders le temps de dessiner la vue, puis la lightmap du joueur reprend. Une vue secondaire montre le monde du joueur et garde sa lightmap. Avant, chaque vue recalculait la lightmap du monde vu puis celle du joueur à chaque image (deux fois 256 points et un millier d'objets temporaires par vue et par image, deux envois de texture au pilote). Détruites à la fin de session et au changement de dimension vanilla. |
| `ObliqueProjection` | Projection dont le plan proche est remplacé par le plan d'arrivée en espace caméra (méthode de Lengyel) : un point du plan prend la profondeur du plan proche, ce qui est devant est coupé par le pilote, le coin lointain garde la sienne, l'écran ne change pas. Sert à une vue dont le terrain est dessiné par un autre mod, dont les shaders ignorent `gl_ClipDistance`, et seulement quand un bloc ou une entité se trouve entre la caméra de la vue et le plan d'arrivée (`ViewCone.intersectsBeforeFrame`, bloc portail et portail d'arrivée exceptés) : sans rien à couper, la projection du joueur donne la même image, et son plan lointain n'est pas incliné (un pack de shaders qui reconstruit ciel, nuages et brouillard depuis la profondeur par la projection inverse y fait repartir derrière la caméra une partie des rayons du ciel : ciel délavé, nuages absents). Le frustum de culling garde la projection du joueur (`Frustum` exige une sous-matrice orthogonale). |
| `ViewRenderStart`, `MixinWorldRenderStart` | Début de rendu à blanc d'un renderer de vue : avant de dessiner une vue, son renderer passe par `WorldRenderer.render` avec les paramètres du rendu du joueur, et le mixin l'arrête à la première instruction vanilla, après les handlers que d'autres mods insèrent en tête (priorité 1100 : appliqué après eux, son retour anticipé n'est pas vu par leurs injections en fin de méthode, qui défont ce que la tête a posé). Les mods qui posent un état par renderer au début d'un rendu (pipeline d'un mod de shaders, contexte des évènements de rendu de Fabric) le posent ainsi pour le renderer de la vue, qu'attendent les méthodes de rendu qu'elle appelle ensuite. Une fois par renderer et par image. |
| `ShaderPackPortalRenderer`, `IrisCompat` | Vues sous un pack de shaders (mod Iris, détecté par son API publique dans `compat/iris`, le seul endroit du mod qui le nomme) : après l'image du monde du joueur, que le pack a composée et écrite à l'écran, chaque cadre visible prend une valeur à lui dans le stencil là où il passe le test de profondeur de l'image, et y écrit sa propre profondeur (les cadres s'occultent entre eux quel que soit l'ordre) ; puis, cadre par cadre, la zone repart du plan lointain, l'écran est gardé dans une texture, la vue est un rendu complet de son renderer (`WorldRenderer.render`, `RemoteViewRenderer.renderWhole`) où le pack déroule tout son pipeline pour la dimension de la vue, et l'image gardée est remise partout sauf dans le cadre, la passe finale du pack ayant écrit la vue sur tout l'écran, sans test de stencil. Pour ce rendu complet : la caméra du joueur prend la position et le monde de la vue (le pack y lit sa position de caméra et l'œil dans l'eau), l'entité caméra du client devient le portail d'arrivée (le pack y lit la luminosité ambiante et la couleur du ciel dans le monde courant), la caméra de la vue suit le joueur à la troisième personne (toutes les entités dessinées, pas de main), le renderer de la vue emprunte les tampons de dessin du client (le pack groupe les entités dans ceux-là et ne vide que ceux-là), les masques d'écriture sont remis (la passe finale du pack laisse la profondeur en lecture seule), la pile de matrices est laissée à l'identité (le rendu vanilla y empile lui-même sa vue), la projection est à plan proche oblique s'il y a quelque chose à couper entre la caméra de la vue et le plan d'arrivée (celle du joueur sinon, voir `ObliqueProjection`) et l'effacement de l'écran en tête du rendu est passé. Le pack porte le balancement de la vue en marchant dans la matrice de vue du joueur, et non dans la projection comme vanilla : ce que cette matrice ajoute à la rotation de sa caméra (`VIEW_EXTRA`) s'applique aussi à la matrice de vue de chaque vue, sans quoi l'autre côté resterait immobile dans un cadre qui balance. Un niveau de portails imbriqués suit le même chemin dans la vue : la zone d'un cadre imbriqué prend le bit haut du stencil là où elle passe la profondeur de la vue (dessinée avec la projection de la vue), la vue imbriquée se rend sous ce stencil depuis une seconde image gardée, puis la zone reprend la profondeur du cadre et perd le bit. En troisième personne derrière un portail, tout l'écran est ce rendu, effacement compris, puis les portails que voit cette caméra, dont celui que le joueur vient de franchir, où il se voit. Les entités à cheval sur un portail se dessinent en deux moitiés : celle du monde du joueur au milieu de son rendu, dans le pipeline du pack, celle de l'autre côté dans le rendu complet de la vue. Un cadre derrière l'eau ou le verre n'est pas dessiné. Le pack tient un pipeline par dimension et en nomme les programmes pareil d'un pipeline à l'autre, alors que vanilla redonne à tout programme un étage déjà compilé sous le même nom : un pipeline créé pendant qu'un autre vit (première vue d'une dimension, traversée) recevait les étages de l'autre dimension (entités, main, ciel éclairés comme de l'autre côté). `MixinShaderProgram` ne redonne un étage qu'aux programmes chargés par la même fabrique de ressources que lui (`ShaderStageOrigin`, retenue par `MixinShaderStage`) ; un rechargement vanilla charge tous ses programmes par une seule fabrique et garde son partage. |
| `HandMask` | Main du joueur sous un pack de shaders : le pack la dessine à sa vraie profondeur, et le plan d'un cadre à moins d'un demi-bloc de la caméra passe devant elle ; la vue, dessinée après l'image, la recouvrait. Pendant le dessin de la main (`MixinHeldItemRenderer`), ses pixels prennent un bit du stencil ; aucun cadre ne s'y marque, l'image gardée du joueur y est remise, et le bit s'efface en fin d'image. Sans pack, vanilla dessine la main après le monde, par-dessus tout. |
| `LazyVertexBuffers`, `MixinVertexBuffer` | Pendant la création d'un renderer de vue, ses tampons de sommets (ciel, et les quatre de chacune des milliers de sections de sa grille) ne réservent leurs objets GL qu'au premier usage : une vue n'en construit qu'une petite part, et douze objets GL par section dans la même image faisaient l'à-coup de l'apparition d'une dimension. Le renderer vanilla du joueur n'est pas concerné ; un renderer de vue promu principal à la traversée crée les siens au fil des chunks reçus. |
| `PortalViewTransform` | Caméra distante = image de la caméra par le passage, **roulis compris** (`aim` : rotation de la caméra source suivie de celle du portail, posée sur le quaternion de la caméra et ses trois directions ; d'un sol à un mur, un lacet et un tangage seuls pencheraient l'image ; sous un pack, la caméra du joueur la reçoit le temps du rendu complet d'une vue) ; matrice de vue construite comme `GameRenderer` ; **plan de découpe** du portail d'arrivée exprimé dans l'espace caméra des shaders. La projection reste celle du joueur pour le culling et, sauf terrain dessiné par un autre mod ou pack de shaders (`ObliqueProjection`, au prix de la précision de profondeur loin du plan), pour le dessin. |
| `ShaderClipInjector`, `PortalClipping` | Au chargement, chaque vertex shader qui place ses sommets par `ModelViewMat` reçoit un uniforme `backrooms_ClipPlane` et écrit `gl_ClipDistance[0]` (`MixinShaderStage`) ; pendant le dessin d'une vue, `GL_CLIP_DISTANCE0` est actif et chaque shader activé reçoit le plan courant (`MixinShaderProgram`), un shader sans l'uniforme coupant la découpe le temps de son dessin. Pile de deux plans : la vue, puis la vue imbriquée ; ou, hors des vues imbriquées, la moitié d'une entité à cheval sur un portail (`StraddlingEntityRenderer`). Dans une vue imbriquée, les entités engagées dans le cadre d'arrivée, à cheval sur son plan, se dessinent après les autres, découpe suspendue (`PortalClipping.suspend`) : il n'y a plus de plan pour leur autre moitié, et coupées, leurs modèles sans élimination des faces arrière montreraient leur intérieur. |
| `ViewCone` | Pyramide caméra → arêtes du cadre d'arrivée, fermée par le plan du cadre : rien hors de ce volume n'apparaît dans la zone de vue, sections, entités et portails imbriqués sont écartés avant d'être dessinés. Sans limite quand la caméra elle-même est passée de l'autre côté (troisième personne). La partie de la pyramide en deçà du cadre, entre la caméra et son plan, dit s'il y a quelque chose à couper (`intersectsBeforeFrame`) : la boîte testée est d'abord coupée au plan du cadre, dont la normale suit toujours un axe, si bien qu'un mur ou un sol qui borde le cadre ne compte pas. |
| `PortalRenderState` | Garde de réentrance et numéro d'image, caméras des vues, coupe-circuit : stencil refusé par le pilote ou exception pendant un rendu distant → rendu distant coupé pour la session, portails en cadres sombres. |
| `RemoteTargeting` | Après le raycast vanilla de chaque image : si le regard traverse un cadre avant sa cible, rayon transposé dans la vue et poursuivi sur la portée restante (blocs puis entités, sauf le joueur lui-même, que montre un portail de son propre monde) ; vanilla reçoit une cible manquée. |
| `RemoteActions` | Clics quand une cible existe de l'autre côté : frappe et utilisation envoyées au serveur, minage annoncé (début, abandon) sans prédiction, délais vanilla (4 ticks entre utilisations, 5 entre blocs cassés en créatif) ; sinon vanilla garde la main (coup dans le vide, manger). |

Une entité portail n'a **qu'une face** : vue de dos, elle n'est ni dessinée ni traversable (un bloc portail à deux faces en pose deux, dos à dos). Après les portails, le monde du
joueur reprend son brouillard, son éclairage d'entités et une couleur de shader blanche : sa couche translucide, ses
particules, la main et les objets de l'interface ne prennent pas la teinte de la dimension vue.

### Traversée sans coupure

Le changement de dimension vanilla envoie un paquet de respawn : nouveau `ClientWorld`, écran « chargement
du terrain », rendu reconstruit. Ici rien de tout cela :

1. **Prédiction** (`ClientPortalCrossing`, au début de chaque `GameRenderer.renderWorld`) : si les yeux du
   joueur, à la position affichée, ont franchi le plan d'un portail depuis l'image
   précédente, hors de son arrivée gardée, le client transpose lui-même position, regard et
   vitesse, échange le monde principal et le monde distant déjà chargé (`RemoteWorldRegistry.swapMainWorld`),
   déplace son joueur d'un `ClientWorld` à l'autre et envoie `portal_cross` avant son prochain paquet de
   déplacement. L'image suivante est déjà celle de l'autre côté, lightmap recalculée pour sa dimension (vanilla ne la
   recalcule qu'au tick). L'ancien monde et son renderer deviennent un monde distant : le portail de retour montre
   d'où l'on vient sans rien recharger.
2. **Serveur** (`TeleportExecutor`) : la demande est acceptée si le portail est dans le monde du joueur, hors
   cooldown, hors de son arrivée gardée, et si la position annoncée est à moins de 3 blocs de celle connue du serveur et
   ses yeux à moins de 1,5 bloc de la boîte du cadre.
   Le changement de dimension suit alors la séquence de `ServerPlayerEntity.teleportTo` sans paquet de respawn,
   précédée de `portal_teleport`. Une demande refusée renvoie `portal_teleport` avec l'état serveur : le client
   revient en arrière, dans le monde que le flux vanilla n'a jamais quitté (rien à y rebrancher ni à y nettoyer). En secours, le serveur détecte aussi lui-même le passage des yeux entre deux ticks, mais
   laisse cinq ticks au client pour annoncer le sien : la prédiction gagne toujours quand le monde distant est là.
   Aucun délai entre deux traversées : seule la confirmation du serveur est attendue avant la prédiction suivante.
   Le joueur ressort exactement là où il est entré, à 2 cm près : aucun décalage à l'arrivée. Après une prédiction,
   le serveur ne renvoie pas de téléportation vanilla (elle ramènerait le joueur là où il était un tick plus tôt et
   annulerait son élan) : il s'aligne et confirme par `portal_teleport` ; le client ignore une confirmation identique
   à sa prédiction (à 30 cm près, le serveur ayant pu le relever d'un sol irrégulier). Un saut de position
   (commande `/tp`, respawn, plus de 8 blocs d'un coup) n'est jamais une traversée, ni pour le serveur ni pour la prédiction.
3. **Flux de paquets** : tant que le serveur n'a pas confirmé, les paquets vanilla continuent d'alimenter
   l'ancien monde (le gestionnaire de paquets ne change de monde qu'à la confirmation). Vanilla nettoie alors
   l'ancien monde du joueur : `MixinClientPlayNetworkHandler` ignore ces déchargements de chunks (pour garder la
   vue derrière soi) et applique les destructions d'entités à l'ancien monde, jusqu'au recentrage du suivi de
   chunks dans le nouveau monde. Comme le client garde ses mondes distants, le serveur lui retire d'abord les entités
   qu'il y suivait (destructions enveloppées, dimension d'arrivée comprise, envoyées avant que la destination ne suive
   le joueur), puis le rebranche sur les entités de l'ancien monde comme sur n'importe quelle dimension distante.
   Le client diffère de dix ticks le retrait de ces copies (`EntityHandover.deferRemoval`) : le serveur les renvoie
   presque aussitôt par l'autre voie, au même identifiant, et la copie renvoyée remplace l'ancienne sans trou. Retirées
   tout de suite, elles manqueraient quelques ticks : le portail de retour, juste derrière le joueur, disparaîtrait.
   Le même identifiant suppose que le serveur n'a pas recréé l'entité entre-temps : le ticket de départ
   (`DestinationLoader.holdDeparture`) garde chargé le côté quitté. Si une copie arrive tout de même sous un autre
   identifiant, l'ancienne est retirée avant que vanilla ne l'indexe (`MixinClientWorld.addEntity`,
   `EntityHandover.replaceStaleCopy`) : l'index des identifiants uniques refuserait la nouvelle, qui resterait affichée,
   figée, sans jamais être mise à jour ni retirée.
4. **Continuité du joueur** : le joueur reste le même objet (marche, balancement de la
   vue, objet tenu continuent). Le portail le tourne souvent d'un demi-tour : tout ce qui s'interpole passe avec lui,
   valeur courante et précédente ensemble, du même angle (regard, tête, corps et balancement du bras qui tient l'objet,
   sans quoi la main rattraperait le demi-tour en quelques images), ramené au plus court pour que le lacet reste continu,
   et les positions d'interpolation du modèle vu en troisième personne. Le serveur ne renvoie pas de paquet de vitesse
   après la traversée : arrivé un tick plus tard, il écraserait celle que le client a depuis (un saut rejoué).

Les **autres entités** ne sont pas prédites : le serveur transpose seul objets, mobs et projectiles d'après leur
déplacement du tick, en les retirant d'un monde et en les recréant dans l'autre. Chez celui qui regarde, la nouvelle
copie reprend l'état visible de l'ancienne (`EntityHandover`, même identifiant unique, retrait proche d'un portail
ouvert vers une autre dimension) : âge (animations d'attente), balancement des membres et du bras, blessure, tête et
corps tournés avec le portail, cape d'un joueur, et position affichée, d'où elle rejoint celle du serveur en trois
ticks comme tout déplacement reçu ; sans cela, une copie neuve repartirait membres au repos et sauterait d'un coup à
la position du serveur. Les copies recréées autour d'un joueur qui change lui-même de monde reprennent de même leur état :
le serveur renvoie sous le même identifiant les entités que le joueur voyait déjà par le portail, vanilla remplace la
copie, et la nouvelle reprend l'état de l'ancienne quel que soit le moment où elle arrive (`EntityHandover.replaceSameCopy`).

Une entité à cheval sur un portail, le joueur compris, se dessine en deux moitiés, chacune coupée au plan dans le
monde où elle se trouve (`StraddlingEntityRenderer`). Coupée par une face de son monde (engagée dans le cadre, son point
de traversée (les yeux pour un joueur ou un cadre couché, les pieds sinon), encore devant et en face de l'ouverture, comme pour la
traversée : sur un cadre au sol, les pieds d'une entité passent le plan bien avant ses yeux ; une entité qui longe le bord reste entière de son
côté), vanilla ne la dessine pas (`MixinWorldRenderer.renderEntity`) : sa partie devant la face se dessine
dans son monde, coupée au plan, sa partie passée dans la vue du portail, transposée, la découpe de la vue ne gardant
que ce qui est au-delà du plan d'arrivée. Juste après sa traversée, son centre est passé : le plan d'arrivée la coupe,
sa partie sortie se dessine dans son monde, sa partie encore derrière devant le cadre d'entrée, dans le monde d'où
elle vient, transposée et coupée à son plan. Une entité de l'autre monde qui prend le retour d'un portail (elle entre
dans son cadre d'arrivée, du côté de la sortie) se dessine de même devant le cadre d'entrée, sa partie passée sortant
du portail, tant que son point de traversée (les yeux pour un joueur ou une arrivée couchée) n'a pas passé le plan. Chaque décision porte sur la position affichée à l'image, interpolée
entre deux ticks comme vanilla la dessine, et non sur celle du tick, qui a jusqu'à un tick d'avance : la bascule du
joueur se décide de même, sinon il disparaîtrait l'image de son passage.

Le client montre les entités avec un peu de retard (l'interpolation, environ un bloc à la marche) : quand le serveur
téléporte une entité, sa copie affichée n'a pas encore atteint le plan. Sa destruction est donc différée
(`EntityHandover.applyDestroys`, à l'écoute des destructions vanilla comme distantes) jusqu'à l'arrivée de sa copie de
l'autre côté, qui la remplace aussitôt (le serveur l'envoie au même tick, y compris vers une dimension vue par un
portail). Seule une entité vivante qui, à sa vitesse, passe le plan en face de l'ouverture, vers l'arrière d'une
face, d'ici quelques ticks est gardée : la face franchie se reconnaît ainsi, les deux faces d'un bloc portail étant au même
endroit, et ce qui meurt, est ramassé ou longe le cadre part aussitôt. La nouvelle, reprise à la position affichée de l'ancienne, est souvent encore
derrière le plan d'arrivée, et reste une arrivée (`EntityHandover.arrivalPortal`), dessinée des deux côtés, jusqu'à
être sortie du cadre. Une entité téléportée par un portail de son propre monde passe le portail telle qu'elle est
affichée (`EntityHandover.onPositionPacket`) au lieu de glisser d'un bout à l'autre en trois ticks, puis en sort
comme une arrivée, chaque partie de son côté. Vanilla n'envoie une position absolue qu'au-delà de huit blocs : en deçà,
le serveur marque le suivi de l'entité comme après une monture (`PortalViewTracker.onEntityJumped`), et son prochain
envoi est absolu.

### Entités à travers les portails

Le serveur ne réécrit pas le suivi des entités : il réutilise celui de vanilla. Pour chaque entité de la fenêtre
d'un joueur, un `RemoteEntityView` (une implémentation de `PlayerAssociatedNetworkHandler` par joueur et par
dimension) est ajouté aux auditeurs du `EntityTracker` vanilla de cette entité. Le joueur reçoit alors les mêmes
paquets que les joueurs présents dans la dimension, enveloppés avec leur dimension (`portal_remote_entity`), et
son client les applique à son monde distant avec le gestionnaire de paquets temporairement rebranché dessus :
les mêmes classes d'entités, la même interpolation, les mêmes animations. Les mondes distants sont tiqués à chaque
tick client (`ClientWorld.tickEntities`) ; leurs particules vivent dans leur propre contenu (`RemoteParticles`) et
leurs sons sont rejoués au cadre (`FrameSoundPlayer`). Le rendu reprend l'ordre de vanilla : couches opaques, entités et block
entities (dispatchers configurés sur la vue le temps du rendu), couches translucides.

### Portails imbriqués et vue secondaire

Chaque portail visible dans une vue (dans son frustum et dans le volume visible à travers son cadre) est à son tour
dessiné, **un niveau plus loin** : sa zone passe du niveau 1 au niveau 2 du stencil là où elle passe le test de profondeur contre
la vue, sous le plan de découpe de celle-ci, sa profondeur est poussée au plan lointain, et sa propre vue est rendue
depuis la caméra transposée deux fois, avant que le cadre intérieur ne reprenne sa profondeur (un autre portail
imbriqué derrière lui n'y sera pas marqué). Au-delà de ce niveau, un portail est transparent. Quand la destination
d'un portail (direct ou imbriqué) est la dimension **du joueur**, la vue est une **vue secondaire** du monde
principal : un second `WorldRenderer` sur le même `ClientWorld`, dont les sections à reconstruire suivent celles du
renderer de ce monde (`MixinWorldRenderer.scheduleChunkRender`, d'après le monde du renderer, rendu de portail compris).
Elle reste liée à son monde : quand le joueur traverse, celle du monde quitté attend son retour (ses sections suivent
toujours celles du monde, devenu distant) et celle du monde d'arrivée, s'il en a déjà eu une, resert telle quelle. Créer
puis détruire un `WorldRenderer` à chaque traversée (une grille entière de sections et leurs tampons) figeait l'image,
surtout à la troisième personne, où la caméra restée derrière le cadre voit aussitôt le portail franchi, qui montre le
monde d'arrivée. Les portails intra-dimension se
dessinent donc aussi. Un portail imbriqué dont le chunk d'arrivée n'est pas disponible dans la vue (hors fenêtre
synchronisée, hors distance de rendu) reste un cadre sombre.

Chaque vue choisit elle-même ses sections (`ViewSectionWalker`) : le culling asynchrone de vanilla
(`ChunkRenderingDataPreparer`) tient un seul graphe par renderer, le recalcule en arrière-plan quand la caméra change
de section et filtre entre-temps l'ancien graphe par le nouveau frustum ; deux vues du même monde dans une image
alterneraient des listes incomplètes et des pans de la vue clignoteraient. La grille de sections d'un renderer est ancrée
sur l'arrivée du portail et ne se recentre que si un ancrage s'en éloigne de plus de deux chunks : la vue d'un portail
ne se redécoupe pas quand le joueur marche, et le monde qu'on vient de quitter garde sa grille telle quelle.
Le parcours d'une vue remplace la liste de sections du renderer : à la traversée, le renderer promu est marqué pour que
vanilla refasse la sienne à l'image suivante (`ClientPortalCrossing.switchWorld`). Vanilla ne la refait qu'après un
changement de son graphe ou un regard tourné de deux degrés ; un joueur qui ressortait sans tourner la tête et
s'arrêtait ne voyait que ce que montrait le portail, jusqu'à son prochain mouvement.

### Interaction à travers un portail

1. **Visée** (`RemoteTargeting`, après `GameRenderer.updateCrosshairTarget`) : le rayon du regard est testé
   contre le cadre de chaque portail visible ; s'il en traverse un avant la cible vanilla, il repart du point
   transposé dans la vue (un millième de bloc devant le plan d'arrivée) sur la portée restante, contre les blocs
   (`World.raycast`) puis les entités de la vue. `MinecraftClient.crosshairTarget` devient une cible manquée :
   vanilla ne touche plus au bloc caché par le cadre.
2. **Contour** : la vue dessine le contour du bloc visé (`WorldRenderer.drawShapeOutline`, car Fabric API intercepte `drawBlockOutline` hors de `render`) quand c'est elle
   que le regard traverse, si vanilla dessine lui-même les contours dans cette image.
3. **Clics** (`RemoteActions`, en tête de `doAttack`, `handleBlockBreaking`, `doItemUse`) : frappe et utilisation
   partent au serveur avec portail, dimension et cible ; le minage annonce son début et son abandon, le serveur
   fait le reste ; le minage vanilla d'un bloc du monde du joueur est annulé quand il vise à travers un portail.
   Sans cible de l'autre côté, vanilla garde la main (coup dans le vide, manger, lancer).
4. **Serveur** (`RemoteInteractionHandler`) : portail dans le monde du joueur, visible, menant à la dimension
   annoncée, plan devant le joueur à portée de bloc ; cible à portée (critères vanilla, +1 bloc de marge) du point
   où le regard ressort ; `canPlayerModifyAt` pour les blocs. L'action vanilla s'exécute avec le joueur **transposé**
   (position et regard dans la dimension d'arrivée, écouteur de déplacement coupé puis rétabli) : recul, orientation
   de pose, portée se calculent comme s'il était sur place. Frappe : `PlayerEntity.attack`, son joué au joueur.
   Utilisation d'entité : créatures seulement (ni monture, ni véhicule). Utilisation de bloc : `onUseWithItem`,
   `onUse` (sauf blocs à block entity, dont l'écran se fermerait aussitôt), puis `useOnBlock` avec un
   `ItemUsageContext` du monde d'arrivée, main principale puis secondaire.

### Ce qui traverse un portail ouvert

Fermé (`teleportable` faux) ou invisible, un portail ne laisse rien passer, lumière comprise.

- **Son** (`MixinPlayerManager`, `FrameSoundPlayer`, `MixinClientWorld`) : les sons et
  évènements de monde (`sendToAround`) et les particules diffusées (`ServerWorld.spawnParticles`) d'une dimension
  partent aussi, enveloppés avec leur dimension, aux joueurs dont la fenêtre synchronisée contient leur chunk. Côté
  client, tout son positionné d'un monde distant (paquet du serveur, feu, lave, entité) passe par les portails du
  monde du joueur qui mènent au monde du son, ceux par lesquels il voit l'autre côté, qu'ils aient un retour ou non
  (aller simple, portail invoqué seul) : il est rejoué au point du cadre le plus proche de la source vue par ce cadre
  (son image, derrière le plan), avec le volume qui lui reste après le trajet jusqu'au cadre (la part de sa portée parcourue, seize blocs pour un son faible,
  portée de 16 à 64 blocs, `PortalSound`), si le joueur est devant la face ; le moteur de son atténue ensuite du
  cadre à l'oreille. Un son du monde du joueur à plus de 64 blocs passe de même par un portail
  intra-dimension. Un son d'un monde distant sans chemin jusqu'au joueur n'est pas joué. Les sons qui suivent une
  entité depuis son apparition (wagonnet, abeille) ne passent pas. Chaque son positionné que le moteur joue garde sa
  vraie source (`SoundAnchors`, `MixinSoundSystem`) : après une traversée, les sons encore joués (un disque, une
  ambiance longue) sont replacés sans relance, à leur place s'ils sont dans le monde du joueur, au cadre par lequel
  il voit le monde du son sinon, ou tus faute de chemin, et retrouvés là où ils en sont quand il revient ; les sons
  qui suivent une entité se replacent eux-mêmes et ne sont pas ancrés.
- **Particules** (`RemoteParticles`, `MixinWorldRenderer`) : chaque monde distant a son propre contenu de
  particules ; celles de ses entités, de ses paquets (coups critiques, explosions) et de ses évènements (bloc cassé)
  y naissent, avec les règles de vanilla (portée de 32 blocs, réglage des particules) autour de la caméra de la
  dernière vue. Tant qu'une vue de ce monde a été dessinée depuis moins de deux secondes, ses particules vivent à
  chaque tick et les effets d'ambiance de ses blocs (flammes, fumée, gouttes, cendres) sont tirés comme vanilla
  autour du joueur ; elles se dessinent dans la vue après les couches translucides, sous le plan de découpe.
- **Regard des mobs** (`PortalPursuit`, `PortalPursuitGoal`, `MixinActiveTargetGoal`, `MixinMobEntity`, `MixinZombieAttackGoal`). Un mob qui
  cherche un joueur et n'en trouve aucun dans son monde regarde aussi par les portails ouverts à sa portée de
  poursuite : il applique à chaque joueur de l'autre côté ses propres règles de ciblage (difficulté, créatif,
  équipe, colère des mobs neutres, discrétion), puis mesure distance et vue par le cadre (deux lancers de rayon, un
  de chaque côté). S'il voit un joueur, son objectif de poursuite (ajouté aux mobs qui ciblent des joueurs ou se
  vengent) lui donne la posture d'attaque (bras levés, arc bandé), le fait fixer le joueur là où il le voit par le cadre
  et marcher d'abord en face de l'ouverture, puis deux blocs derrière le plan, en ligne droite à travers elle (de
  biais, son chemin couperait le plan à côté du cadre) ; il traverse, et de l'autre côté le joueur devient sa vraie cible : il
  n'attaque qu'une fois passé. Sa copie neuve repart aussitôt vers le joueur (`PortalPursuit.resumeAfterCrossing` :
  cible, chemin, posture), sans attendre que ses objectifs la relancent ; le client lui garde la posture de l'ancienne.
  Vanilla baisse les bras d'un zombie les cinq premiers ticks d'une attaque : celui qui a déjà la posture quand son
  attaque commence (copie neuve de l'autre côté, ou passage de la poursuite à une cible de son côté) la garde.
  Un mob frappé à travers un portail fait de même : une cible dans un autre monde n'est jamais gardée telle quelle,
  vanilla y mesurerait des distances sans aucun sens. Les mobs à cerveau (piglins, villageois, warden) n'ont pas
  d'objectifs de ciblage et ne sont pas concernés.
- **Attelages** (`TeleportExecutor.executeTree`) : un véhicule et tout ce qui le monte traversent ensemble, joueurs
  compris, vers une autre dimension comme dans le même monde ; un cheval monté dans un bateau garde son cavalier. Le
  cooldown du véhicule (vanilla) et celui du joueur empêchent l'aller-retour immédiat.
- **Clic molette** (`RemoteActions.pick`) : le choix de bloc vanilla s'exécute avec la cible et le monde de
  l'autre côté à la place de ceux du joueur (bloc, données de block entity en créatif avec Ctrl, œuf de créature).

---

## Configuration (`ModConfig`, propriétés `-D`)

| Propriété | Défaut | Rôle |
|-----------|--------|------|
| `gtgsbackrooms.portal.enablePortalFeature` | `true` | Coupe tout (le bloc portail reste inerte, les mixins ne font rien). |
| `gtgsbackrooms.portal.preloadRadiusChunks` | `3` | Rayon de la fenêtre chargée et synchronisée autour de chaque arrivée (7 x 7 chunks). |
| `gtgsbackrooms.portal.renderDistanceBlocks` | `64` | Distance de rendu et de synchronisation d'un portail. |
| `gtgsbackrooms.portal.activationRadiusBlocks` | `16` | En deçà, le rendu dessine un portail quelle que soit la direction du regard ; au-delà, seulement devant le joueur, jusqu'à `renderDistanceBlocks`. Le préchargement de l'arrivée et la synchronisation de la fenêtre, eux, tiennent jusqu'à la plus grande de ces deux distances quelle que soit la direction du regard (décision C60). |
| `gtgsbackrooms.portal.teleportCooldownTicks` | `0` | Délai minimal entre deux traversées d'un joueur ; zéro par défaut, on enchaîne aller et retour librement (les objets et mobs gardent un cooldown vanilla fixe de 10 ticks). |
| `gtgsbackrooms.portal.remoteWorldReleaseSeconds` | `60` | Secondes sans aucun portail à portée vers une dimension distante avant que le client n'en libère le monde (mémoire rendue ; un retour après ce délai voit la vue se reconstruire, le temps de renvoyer la fenêtre). Jamais moins que les 5 s du déchargement de la fenêtre ; une valeur très grande revient à ne jamais libérer. |
| `gtgsbackrooms.portal.debugRendering` | `false` | Une ligne de log par seconde et par portail rendu (chunks chargés, entités, état du renderer). |

`./gradlew runClient -PportalPreloadRadiusChunks=2 -PportalDebugRendering=true` passe ces propriétés au client de dev.

---

## Limites connues

- **Un niveau d'imbrication** : un portail vu à travers un portail est dessiné, un portail vu à travers celui-là est transparent.
- **Météo** : les éclaboussures et le bruit de la pluie ne se tirent qu'autour de la caméra de la vue d'un monde distant ; dans la vue d'un portail qui ramène dans le monde du joueur, la pluie tombe sans éclaboussures.
- **Troisième personne** : la vue de l'autre côté dure tant que le recul de la caméra passe par le cadre ; regard incliné, il sort par le haut d'un cadre au bout d'un à deux blocs et la caméra revient dans le monde du joueur, derrière le cadre (sans quoi le joueur, de l'autre côté, ne serait visible qu'à travers l'ouverture). Une entité à cheval sur un cadre est dessinée des deux côtés ; dans une vue vue à travers une autre vue, qui n'a qu'un plan de découpe, celle qui sort de l'arrivée de cette vue se dessine entière, dans le cadre, et celle qu'un autre portail coupe aussi. Le temps du passage, et dès que son modèle touche le plan (à environ un bloc d'un cadre pour une créature), elle n'a pas d'ombre au sol, et ce qui dépasse de l'ouverture derrière le plan (une entité plus large que le cadre) ne se voit pas.
- **Collision** : les blocs de l'autre côté ne comptent que pour une entité engagée dans le cadre. Les blocs de ce côté cachés derrière le plan le sont pour ses collisions, l'étouffement et le voile de mur à l'écran seulement : un fluide derrière le plan reste (on y nage), et les effets de contact d'un bloc (cactus, toile d'araignée, buisson de baies, feu) aussi. Un mod qui remplace le calcul des collisions de blocs sans passer par leur parcours vanilla ne voit pas les blocs cachés.
- **Sons** : un son qui suit une entité depuis son apparition (wagonnet, abeille) ne traverse pas ; un son relayé l'est si son chunk est dans la fenêtre synchronisée du joueur, la portée réelle se décide ensuite côté client.
- **Mobs** : seuls les mobs à objectifs (zombies, squelettes, araignées, mobs neutres en colère…) voient et poursuivent à travers un portail ; un mob à distance (squelette) traverse au lieu de tirer à travers le cadre.
- **Interaction** : pas d'écran (coffre, four, enclume…) ni de monture à travers un portail, et rien pour un spectateur ; le balayage d'épée est évalué dans le monde du joueur ; les sons et particules du coup partent dans le monde du joueur, seul le son de frappe lui est rejoué.
- **Une grille par dimension distante** : la grille de sections d'un renderer est ancrée sur l'arrivée du portail visible le plus proche. Un second portail vers la même dimension dont l'arrivée est à plus de deux chunks de là ne montre que ce que cette grille couvre (rien si elle est hors de portée), et la reprend dès qu'il devient le plus proche.
- **Noms au-dessus des têtes** : le temps qu'une entité nommée (un autre joueur) traverse, sa moitié transposée garde l'orientation et la distance de la caméra de son propre côté : son nom peut tourner ou disparaître une fraction de seconde.
- **Sortie fugace** : le regard est celui que rapporte le client ; un client sans le mod, ou sans rendu distant (stencil refusé, rendu coupé), ne voit aucune sortie fugace : il ne la garde pas et ne la traverse pas. Seuls les cubes pleins et opaques cachent un cadre : une sortie cachée par une entité, une plante ou un bloc partiel, ou par un bloc entre les neuf points testés du cadre, compte comme vue ; vue à travers un autre portail, elle ne compte pas. Elle ne disparaît qu'après une demi-seconde sans regard : une image isolée sans elle (pendant une traversée) ne la perd pas, un coup d'œil plus bref la garde. Une sortie que personne n'a encore vue reste ; relue après un redémarrage du serveur, elle attend de nouveau un regard. Cachée à un joueur, elle existe encore pour le serveur : la lumière et les entités la traversent, et le répit d'une seconde après un passage de portail vaut, côté client, pour toutes les sorties fugaces du monde d'arrivée.
- **Portails qui se recouvrent** : poser un bloc portail exactement au point d'arrivée d'un autre superpose leurs portails, et le joueur peut être renvoyé d'un côté à l'autre. Décaler l'un des deux.
- **Arrivée dans l'Overworld** : un bloc posé dans le Level 0 pose son cadre d'arrivée sur la surface aux mêmes X/Z, sans vérifier qu'il y tient (relief, arbre) ; le chunk de cette surface se charge à la pose, et se génère s'il ne l'a jamais été (un à-coup). Un cadre d'arrivée couché au plafond y est relevé de deux blocs, pour que qui en tombe ait les pieds au-dessus du sol.
- **Cadres couchés** : un cadre au sol est une trappe, arrivées comprises (celles du Level 0 et de la surface, posées sur un sol plein) : qui en sort en est gardé, mais un autre joueur, un mob ou un objet qui y marche y tombe. La garde tient une seconde une fois posé sur le cadre, sauts compris, tant qu'on reste au-dessus de l'ouverture : pour reprendre son arrivée plus tôt, s'accroupir, ou en descendre et y revenir. Un objet ou un mob immobile sur deux cadres au sol qui se renvoient l'un à l'autre fait l'aller-retour, une fois par seconde environ. La caméra du joueur, sans roulis dans Minecraft, ne roule qu'à la traversée, le temps de se redresser : la main tenue, dessinée à part, reste droite pendant ce temps. Dans le Level 0 (quatre blocs d'air), un cadre au plafond ne se rejoint qu'en volant ou en s'élevant : un saut n'y porte pas les pieds. Les mobs n'y cherchent pas de chemin : ils y tombent s'ils marchent dessus. Le serveur ne vérifie pas que le corps d'un joueur engagé dans un cadre reste hors des blocs de l'autre côté (il fait confiance à son client).
- **Plan au bord du bloc** : un cadre plaqué contre un mur ou un sol plein se traverse comme un trou dans le bloc (voir *Collision*) ; vu de l'autre côté du mur, rien ne montre le portail.
- **Destination choisie** : la recherche d'un sol lit les chunks jusqu'à seize colonnes autour du point de départ. Le bloc les demande un à un par un ticket et attend que chacun soit entier avant de résoudre (`PortalTargets.resolveLoadedOnly`, un essai par tick) : vers un endroit jamais visité, le cadre ne devient portail que quelques ticks après la pose, le temps que vanilla génère chaque chunk lu sur ses threads de travail, sans à-coup du serveur. Sans sol à portée, la plateforme d'obsidienne modifie le monde d'arrivée. À ciel ouvert, comme pour la surface, le cadre se pose sur le haut du sol (eau comprise) sans vérifier qu'il y tient (arbre, relief).
- **Arrivée tête en bas** : seule la caméra du joueur se renverse puis se redresse ; la main tenue reste droite pendant le demi-tour, et une entité qui arrive par un cadre retourné ressort debout, tournée comme le veut le passage.
- **Caméra contre le bord d'un cadre** : à moins de 5 cm du plan et juste à côté de l'ouverture, le plan proche de la projection coupe le cadre plat, et une mince bande de la vue manque près du bord de l'écran ; dans l'encadrement, le cadre devient une boîte qui couvre ce cas.
- **Grands cadres dans le Level 0** : faute de cellule où le cadre tient sur toute sa largeur dans le rayon de recherche, il se pose au centre de la cellule ouverte la plus proche et peut entrer dans les murs ; plus haut que quatre blocs, il entre dans le plafond.
- **Lumière** : seule la lumière des blocs passe, pas celle du ciel ; une lumière retirée s'éteint en quelques passes (deux niveaux par aller-retour entre les deux côtés).
- **Traversée** : la prédiction client n'a lieu que si le monde distant est déjà chargé ; en sprintant vers un portail qu'on vient de poser, c'est le serveur qui bascule, avec un tick de latence. À portée de rendu (64 blocs), l'autre côté se synchronise quelle que soit la direction du regard (décision C60) : on peut s'engager dans un portail à reculons ou de côté, et le regard qui revient sur un portail trouve sa vue prête. En selle (bateau, monture, wagonnet), c'est toujours le serveur qui bascule, avec un tick de latence ; le client, que le serveur fait descendre puis remonter, affiche un instant l'aide « appuyez sur Maj pour descendre », comme à une montée.
- **Mods de rendu** : un mod qui remplace le pipeline de terrain (sections, `setupTerrain` et `renderLayer` réécrits, grille vanilla créée sans portée) ne fait plus planter le jeu (rien n'est injecté dans ce qu'il réécrit) et les vues se dessinent avec son terrain : la lumière des chunks distants lui parvient par la voie vanilla (`readLightData`), son `setupTerrain` choisit les sections de la vue, et une projection à plan proche oblique coupe au plan d'arrivée ce que ses shaders ne couperaient pas, quand quelque chose se trouve entre la caméra de la vue et ce plan ; vérifié sur le banc de production, vues identiques à vanilla à quelques nuances près. Non vérifié avec lui : ses options de rendu (occlusion, éclairage lissé propre), et la précision de profondeur loin du plan (scintillement possible de faces lointaines qui se touchent). Le mod de shaders Iris sans pack actif : vues identiques à vanilla (son état par renderer est posé par le début de rendu à blanc, `ViewRenderStart`). Avec un pack actif, les vues se dessinent après l'image du monde, chacune par un rendu complet de son renderer dans le pipeline du pack (`ShaderPackPortalRenderer`, `compat/iris`) : terrain, entités, ciel, nuages, météo et ombres de l'autre monde, éclairés par le pack avec la caméra de la vue ; vérifié sur le banc de production avec deux packs répandus. Ce chemin dessine un niveau de portails imbriqués, comme le chemin vanilla (le joueur se voit par le cadre en troisième personne) ; les entités à cheval sur un portail s'y dessinent en deux moitiés, rognées au plan sur le processeur (un mod de terrain peut signaler une fois dans le journal que ces tampons n'ont pas son chemin d'écriture rapide) ; ses limites : une entité à cheval ne projette pas d'ombre le temps de la traversée, un cadre derrière l'eau ou le verre n'est pas dessiné (le pack compose ses surfaces translucides dans son propre pipeline, avant sa passe finale), quand un bloc ou une entité se trouve entre la caméra de la vue et le plan d'arrivée, la projection oblique qui le coupe délave le ciel de la vue et en ôte les nuages, l'œil du pack (luminosité ambiante, adaptation) est pris au portail d'arrivée, ses effets temporels (TAA, flou de mouvement) voient deux caméras par image dans une vue de son propre monde, un pack sans programme `final` recopie sa couleur sur tout l'écran (l'image du joueur est remise autour du cadre dans tous les cas), et chaque vue coûte un rendu complet du pack (un pipeline par dimension vue, avec ses tampons). Un mod qui dessine lui-même du terrain lointain et suit le monde courant par les chemins vanilla (`joinWorld`) ne voit pas une traversée par portail, qui échange le monde sans passer par là : il peut continuer à dessiner la dimension quittée jusqu'au prochain changement de monde vanilla. Les familles de mods qui ne touchent pas au rendu du terrain (lumière dynamique, minimap, enregistrement de parties, cache de chunks, VR en mode écran, culling d'entités, optimisations serveur) passent le banc sans écart.
- **Mémoire** : un `WorldRenderer` supplémentaire par dimension distante, plus une vue secondaire par dimension où un portail a ramené le joueur dans son propre monde. Chacun tient une trentaine de Mio de tampons de construction hors du tas Java (un tampon de construction, deux de réserve, les tampons d'entités ; repris par le renderer suivant quand il est libéré) et une grille de sections à la distance de rendu du joueur (quelques Mio sur le tas à 12 chunks), dont seules les sections construites ont des objets GL. Gardés jusqu'à la déconnexion, au changement de dimension vanilla, ou jusqu'à ce que plus aucun portail à portée ne montre leur dimension depuis `remoteWorldReleaseSeconds` (60 s par défaut) : le serveur fait alors libérer le monde distant, et un retour plus tard voit la vue se reconstruire (décision C63 du 25 octobre 2026). Le client le vide avant de le jeter (chunks, lumière, entités, particules, lightmap). Le contexte de rendu que Fabric API ajoute au renderer vanilla et l'état de chargement vanilla du dernier changement de dimension peuvent encore garder l'objet monde, vide, jusqu'au prochain changement de dimension vanilla.
- **Première vue d'une dimension** : la première fois qu'une dimension est vue dans une partie (monde distant), ou qu'elle a besoin d'une vue secondaire (portail imbriqué qui y ramène, troisième personne juste après la première traversée vers elle), son `WorldRenderer` se crée dans l'image : un à-coup unique, et une image où cette vue n'a encore rien de construit (couleur de son brouillard, noir dans le Level 0). De même la première fois qu'une vue secondaire sert loin de là où elle a déjà servi.

---

## Tests

| Niveau | Tests |
|--------|-------|
| Unitaires | `PortalFacingTest` (rotation, pas de miroir, entrée = sortie, sur les 144 paires des douze orientations ; faces arrière qui transposent comme les faces avant ; orientation retournée qui fait ressortir tête en bas), `BackroomsPortalEntityTest` (dont point de traversée devant l'ouverture, les pieds pour un cadre debout et les yeux pour un cadre au sol, de même au cadre d'arrivée pour qui prend le retour, sortie fugace sauvée et dernier regard du joueur côté client, distance signée sans allocation, boîtes de traversée et d'arrivée en cache et refaites quand la taille change, boîte à cheval sur le cadre d'entrée ou d'arrivée, décalée à la position affichée, face franchie par une trajectoire qui passe en face de l'ouverture à bref délai, centre de cadre), `BackroomsPortalBlockTest` (bloc invisible, cadre et réglages venus de la baguette, sauvegarde, sortie perdue relue pour une sortie fugace seulement), `PortalSettingsTest` (format, dont pose, destination et sortie fixée, sortie fixée en marchant, pose relue par son nom, bornes, piles, colonnes, centre et pied du cadre, plan au bord du bloc, bloc ancre retrouvé depuis le centre dans chaque orientation), `PortalWayBackTest` (retour lu dans les réglages et redonné), `PortalWandItemTest` (info-bulle de la baguette : réglages, dont pose, destination et sortie fixée, puis usage ; nom d'une dimension sans traduction ; chaque clé de l'info-bulle, de l'écran et du message traduite en EN et FR), `LevelZeroArrivalTest` (cadre qui tient sur toute sa largeur, couloir barré, repli, cadres couchés, sortie imposée), `PortalTargetsTest` (tirage au sort, relevé d'un cadre au plafond à la surface), `VirtualLightSourcesTest`, `PortalRelevanceTest` (dont le regard donné en angles), `TeleportExecutorTest` (traversée du plan, le centre en face de l'ouverture et la boîte touchant le cadre ; ni apparition ni saut de position ; cooldown des joueurs ; annonce à la vitesse et au regard finis et bornés), `PortalCollisionTest` (côté de sortie d'une boîte, image d'une boîte d'un portail à l'autre, entité engagée, cadre couché engagé aux yeux, blocs cachés derrière le plan dans l'ouverture prolongée et nulle part ailleurs, dans les douze orientations, paroi du plan pour qui touche un bord du côté d'entrée seulement, ouverture fermée d'un bord ou d'une garde, portails et côté venus des mondes branchés), `ArrivalGuardTest` (garde posée à la sortie d'un cadre au sol, remplacée par une autre arrivée au sol, levée par une arrivée debout ou quand on n'est plus au-dessus de l'ouverture, suivie par identité : deux entités de même identifiant ne se confondent pas), `ThirdPersonPortalTest` (recul de la caméra à travers le cadre, au-dessus du linteau, portail le plus proche), `PortalPayloadsTest` (douze payloads, dont les réglages de la baguette avec leur main, dont un paquet d'entité vanilla enveloppé, les actions à travers un portail et les portails vus, plafonnés), `PortalViewTrackerTest` (paquets relayés), `RemoteChunkWindowTest`, `RemoteEntityViewTest` (boîte de la fenêtre refaite seulement quand une borne bouge), `PortalViewTransformTest` (plan de découpe en espace caméra, caméra transposée), `ViewConeTest` (volume visible à travers le cadre, sans limite), `ShaderClipInjectorTest` (54 des 55 vertex shaders vanilla du jar, `blit_screen` exclu, un seul plan en tête de `main`), `PortalRenderStateTest` (garde de rendu, numéro d'image, coupure du rendu distant), `RemoteViewRendererTest` (ancrage de la grille), `PortalSoundTest` (portée, volume après le trajet, sortie par le cadre où le joueur voit l'autre côté), `PortalPursuitTest` (point de passage du regard dans le cadre, point d'approche en face de l'ouverture, point visé) |
| En jeu (`runGameTest`) | `PortalSyncGameTests` (fenêtre de chunks et entités vers le Nether, entité distante retirée effacée au même tick, chunks tenus valables 100 ticks après une traversée, annonce refusée sous cooldown ou à la vitesse impossible, météo du Nether envoyée avec elle puis à chaque changement seulement, entité arrivée par un portail envoyée au même tick, traversée entre dimensions sans renvoi des chunks tenus qui n'ont pas changé, oubliés une fois tous consommés, propres à chaque joueur et à leur dimension, côté quitté gardé chargé avec les mêmes entités, annonces client, marche détectée par le serveur, changements de monde vanilla, portails orphelins, commande de debug, chunk tenu qui change renvoyé, spectateur montré aux seuls spectateurs, texte d'un panneau et fissures de l'autre côté relayés, vue qui repart au tick suivant un passage), `PortalInteractionGameTests` (coups, tonte, clics, porte ouverte et fermée, pose et minage à travers le cadre, fissures effacées quand le minage cesse, quand le mineur se déconnecte ou meurt, refus dont le spectateur, le cochon sellé et un joueur loin à côté du cadre), `BackroomsPortalBlockGameTests` (bloc portail : copie qui garde la destination et pose ses propres portails, réglages seuls envoyés aux clients, une face sans rien derrière, aller simple sans retour, sortie fugace qui attend un premier regard, tenue ensuite par le regard d'un joueur même de loin et perdue après une demi-seconde sans regard même tout près (jamais reposée d'elle-même, neuve pour une copie, reposée fugace à la relecture), rouverte par le prochain joueur qui passe l'entrée (pas par un objet, un spectateur ni un portail qui n'est pas l'une des entrées du bloc ; sortie encore là pas reposée), un rapport vide isolé, un rapport d'un autre monde ignoré, un spectateur ou un joueur sans rapport ne la gardant pas, un rapport trop vieux ne comptant plus, traversée par le seul joueur qui la voit (un portail libre marqué fugace, jamais), une seconde pour se retourner pour qui sort par elle seule (pas en repassant par elle, ni après un changement de monde vanilla ou un respawn), portail que son bloc ne tient plus pour sien retiré, pose face au joueur avec les réglages de l'item, cadre bloqué refusé, visé seulement accroupi, quatre portails dont deux faces de chaque côté, retours inverses exacts, bloc tourné ou relu avec d'autres réglages, traversée par la face arrière, retirés avec lui, destination absente, arrivée posée sur le haut réel du sol (dalle, tapis, eau), portails perdus, Level 0 prêté, portails désactivés) ; `PortalLightGameTests` (seuls les portails ouverts, visibles et réglés pour la lumière éclairent l'autre côté, la plus forte l'emporte, source pas encore appliquée signalée de nouveau ; cadre d'un portail qui apparaît revérifié, lumière fantôme éteinte ; portail double face 4 x 4 éclairé des deux côtés, éteint avec sa lumière ; plan au bord du bloc : le bloc devant son plan éclairé), `PortalWandGameTests` (réglages reçus pour la seule main qui tient la baguette ; pose d'un bloc portail réglé, son entendu par un témoin, évènement de pose entendu par un capteur sculk, baguette gardée en survie, refus en échec (cadre bloqué, place prise, hors du monde) ; copie des réglages d'un portail visé, message au joueur, sans joueur aussi ; bloc cassé qui ne rend rien ; plan au bord du bloc : cadre debout posé contre un mur, centre sur la face arrière, plaque visée accroupi, cadre couché à ras d'un sol plein, arrivée décalée d'un demi-bloc) ; `PortalPropagationGameTests` (sons, évènements et particules relayés au joueur qui regarde ; un husk voit un joueur par le cadre, traverse, le cible et repart aussitôt vers lui, mais rien par un portail fermé, hors de portée ou masqué ; une cible dans un autre monde devient une poursuite ; chaque règle d'une poursuite vérifiée seule) ; `PortalGameTests` : traversée avec un joueur serveur (position, regard) ; un objet lancé dans le cadre ressort de l'autre côté avec sa vitesse transposée et le cooldown vanilla ; un saut de moins de huit blocs part aux clients en position absolue, un marqueur que vanilla ne suit pas traverse aussi ; un objet poussé de biais dans le cadre bute sur le mur de l'autre côté, au milieu rien ne l'arrête, ni derrière le plan, ni portail fermé, ni portail vers une dimension absente ; sur le bord, le centre hors de l'ouverture, il bute sur le plan, tout à côté rien, de dos rien, portail fermé rien ; `RemoteChunkPayload` d'un vrai chunk octet pour octet ; `PortalTunnelGameTests` (un corps sur le bord d'un cadre au sol en l'air se pose sur son plan, à côté il tombe, au milieu il passe ; un corps qui tombe sur un cadre posé sur la pierre passe avec son élan, la pierre à côté le porte encore ; un objet passe un mur par le cadre plaqué contre lui, un autre bute à côté ; un corps engagé n'est pas dans le mur qu'il traverse, sauf portail fermé ; engagement retenu le plan passé, sans la face arrière, avec un autre cadre au même centre ou derrière, perdu hors de la largeur, avec un cadre retiré, dans un autre monde et après dix ticks ; garde d'arrivée au-dessus du vide, qui tient plus que le cooldown, même poussé à travers le plan, levée accroupi ou en descendant du cadre ; joueur posé sur un cadre au sol gardé, annonce refusée, descente silencieuse sans traversée, garde levée en changeant de monde, annonce jugée aux yeux acceptée) |
| Non couvert automatiquement | le rendu (stencil par niveau, découpe par les shaders, brouillard, lightmap, entités, particules, portails imbriqués, transparence, troisième personne), la continuité des animations, le son côté client et l'interaction se valident en jeu : `./gradlew runClient`, poser un bloc portail, regarder à travers par ses deux faces (un mob de l'autre côté bouge), jeter un objet dedans, frapper ou miner à travers, traverser, revenir |
| Reproduction en jeu | `./gradlew runClient -PquickPlayWorld="Nom du monde" -PportalDebugRendering=true` charge un monde solo directement ; en cas de gel, `jstack` sur le PID `devlaunchinjector` donne la pile du thread de rendu |
