# Portails

Un portail relie deux endroits du jeu, le plus souvent le monde normal et le Level 0. On voit l'autre côté à travers son cadre, par ses deux faces, et on le traverse en marchant, sans écran de chargement. Le code vit dans `core/portal/` et dans une partie des mixins de `mixin/`. Le système est actif par défaut et se coupe par une [option](../reference/OPTIONS.md#portails).

## En jeu

### La baguette

La baguette de portail (`portal_wand`, onglet créatif du mod) est le seul moyen de créer un portail. Un clic droit dans le vide ouvre l'écran des réglages. Un clic droit sur un bloc pose à côté un bloc portail avec ces réglages, et la baguette ne s'use pas. La pose fait entendre un portail qui s'ouvre.

Le bloc portail est invisible, sans collision et sans item. Seul son cadre compte. Les réglages vivent dans le composant `gtgs_backrooms:portal_settings` de la baguette, affiché dans l'info-bulle, puis dans le bloc. Ils partent au serveur à la fermeture de l'écran, s'ils ont changé. Une commande donne aussi une baguette réglée :

```text
/give @s gtgs_backrooms:portal_wand[gtgs_backrooms:portal_settings={width:4,height:3,transmits_light:false}]
```

Debout, le regard traverse le bloc portail et vise de l'autre côté. Accroupi, il s'arrête sur le plan. Un clic droit avec la baguette copie alors les réglages du portail. Un clic gauche casse le bloc, qui ne rend rien et retire ses portails des deux côtés.

### Réglages

| Réglage | Écran | Valeurs | Défaut | Effet |
|---|---|---|---|---|
| `width`, `height` | Largeur, Hauteur | 1 à 8 | 1 × 2 | Taille du cadre, en blocs. |
| `transmits_light` | Lumière des blocs | passe, bloquée | passe | La lumière des blocs traverse, d'un niveau de moins. |
| `double_sided` | Faces | deux, avant seule | deux | Avec la face avant seule, il n'y a rien de dos. On passe au travers du plan sans traverser. |
| `one_way`, `fleeting_exit` | Retour | aller-retour, aller simple, sortie fugace | aller-retour | En aller simple, aucun retour n'est posé. Une sortie fugace n'existe que sous le regard. |
| `exit` | Sortie | `straight`, `north`, `east`, `south`, `west`, `floor`, `ceiling` | `straight` | Un point cardinal tourne le cadre d'arrivée vers lui. `floor` le couche au sol, on en sort vers le haut ; `ceiling` le couche au plafond, on en tombe. |
| `exit_scatter` | Arrivée au hasard | 0 à 64 | 0 | Rayon, en blocs, d'un tirage au sort des X/Z de l'arrivée. L'arrivée la plus proche est cherchée depuis le point tiré. |
| `flush` | Plan | milieu, bord du bloc | milieu | Au bord, le plan passe sur la face arrière des blocs du cadre. Le cadre se pose alors à ras d'un mur, d'un sol ou d'un plafond. |
| `upside_down` | Arrivée | droite, tête en bas | droite | On voit l'autre côté à l'envers et on en ressort renversé. Le retour est retourné de même. |
| `placement` | Pose | `look`, `standing`, `floor`, `ceiling` | `look` | Pose selon le regard, ou imposée quel que soit le regard. |
| `destination` | Destination | automatique, une dimension | automatique | Toute dimension du serveur, datapacks compris. |
| `mark` | Fixer la sortie ici | bloc et regard du joueur | aucune | Sortie fixée là où se tient le joueur. |

### Pose

Le cadre monte de la hauteur réglée depuis le bloc et s'étend de part et d'autre de sa colonne. Une largeur paire prend une colonne de plus d'un côté. La pose est refusée si le cadre traverserait un bloc. Rien n'est exigé derrière le plan, si bien qu'un cadre se pose contre un mur, sous un toit ou sur un sol plein.

La face avant regarde le joueur qui pose le bloc. Posé en regardant à plus de 60° vers le bas, le cadre se couche au sol, face vers le haut, et on y tombe. Vers le haut, il se couche au plafond, face vers le bas, et on y monte. Un cadre couché se pose au bloc visé, sa largeur le long de l'axe est-ouest et sa hauteur le long de l'axe nord-sud.

### Destination

Par défaut, un portail posé hors du Level 0 mène au layer 0 du Level 0, près des mêmes X/Z. Le cadre d'arrivée se pose dans la cellule ouverte la plus proche où il tient sur toute sa largeur, avec un bloc libre devant et derrière chaque colonne. Couché, il cherche une place où toute son empreinte est libre. À défaut, il se pose au centre de la cellule ouverte la plus proche, face à un bloc libre.

Un portail posé dans le Level 0 ramène à la surface de l'Overworld, aux mêmes X/Z. Le cadre d'arrivée se pose sur le haut réel du sol, au demi-bloc près sur une dalle, un chemin ou un tapis.

Une destination choisie suit l'échelle des coordonnées comme vanilla, soit un huitième dans le Nether. À ciel ouvert, l'arrivée se pose sur le haut du sol. Sous un plafond, elle se pose sur le premier sol où l'on tient debout sous la hauteur du bloc, le cadre entier hors des blocs et des fluides, ou au-dessus à défaut. La recherche lit la colonne du point de départ, puis jusqu'à seize colonnes autour, comme vanilla pour ses portails.

Sans sol dans ce rayon, comme dans le vide de l'End ou sur un lac de lave, l'arrivée se pose au point de départ sur une plateforme d'obsidienne. La place du cadre et du corps est dégagée. On n'arrive donc jamais dans un bloc, dans un fluide ou au-dessus du vide.

La sortie fixée retient la dimension, le bloc des pieds et le regard du joueur. On ressort sur ce bloc dans cette direction, sauf sens de sortie imposé. Elle l'emporte sur la destination, et changer de destination l'oublie. Le serveur ne l'accepte qu'à trois blocs près de la position du joueur. Avec un rayon de tirage, l'arrivée se pose sur un sol près d'un point tiré autour d'elle.

### Traverser

Le portail est double face, comme un trou dans l'espace. Chaque côté tient deux portails dos à dos, et chaque face mène à la face correspondante de l'arrivée. Il ne disparaît pas quand on en fait le tour. Un portail dont l'entrée ou l'arrivée est couchée n'a que sa face avant, car de dos on viendrait du sol ou on ressortirait dans le plafond.

Traverser ne fait pas tourner : entré vers le sud, on ressort vers le sud. On ressort à l'image exacte du point d'entrée, à 2 cm près. Avec un sens de sortie imposé, on ressort vers ce point cardinal par la face avant, et vers le point opposé par la face arrière. La main tenue et les animations continuent, et en se retournant on voit d'où l'on vient.

Un joueur franchit tout cadre aux yeux. Sa tête et ses épaules doivent passer l'ouverture, bordée de petites parois d'un demi-bloc. Un cadre trop bas l'arrête au front, un cadre surélevé se saute, un cadre étroit se passe aligné. Les autres entités franchissent un cadre debout aux pieds et un cadre couché aux yeux.

Un cadre au sol se franchit en tombant, et un cadre au plafond en montant, la tête la première. La sortie garde la place du point de traversée. Tombé par un sol, on ressort à la hauteur où les yeux ont passé le plan, avec son élan.

D'une orientation à une autre, vitesse et regard tournent avec le portail. Tombé dans un sol et sorti d'un mur, on file hors du cadre en regardant où l'on va. La chute n'est gardée que si le bas reste le bas, d'un sol à un plafond. Tombé de vingt blocs dans un sol et sorti d'un mur, on file à l'horizontale sans dégâts à l'atterrissage.

Le corps sort debout, du bon côté. Sous un cadre au plafond, il pend tout entier sous le plan, et sur un cadre au sol, il se tient au-dessus. Sorti d'un mur après un cadre couché ou tête en bas, ses pieds restent dans la hauteur de l'ouverture.

Minecraft ne penche jamais la caméra du joueur. À la traversée d'un portail qui tourne autour d'un axe horizontal, elle part du roulis de l'image vue par le cadre et se redresse en moins d'une demi-seconde (`CrossingRoll`). Elle glisse en même temps de la place où le cadre montrait l'œil à celle où il ressort. Par un cadre tête en bas, ce roulis est un demi-tour.

### Les blocs derrière le plan

Un bloc derrière le plan ne gêne pas. Une entité engagée dans un cadre ouvert ne heurte plus les blocs tout entiers derrière le plan, dans l'ouverture. Elle n'y étouffe pas et ne les voit pas en voile à l'écran. Un cadre contre un mur ou sur un sol plein se traverse donc comme un trou, et les blocs autour de l'ouverture restent.

Un cadre au sol devient ainsi une trappe. Il s'ouvre sous qui s'y tient, ou sous qui y tombe avec son élan.

On ne sort jamais dans un mur, car engagé de biais, on bute sur ce qui est de l'autre côté. Sur un bord, le centre hors de l'ouverture, on bute sur le plan comme sur une paroi mince, même pour un cadre en l'air. On n'entre que si son centre passe dans la largeur du cadre, et de dos, rien n'arrête. Pour un cadre couché, qui arrive de côté n'entre qu'au-dessus de l'ouverture.

### Arrivée au sol

Qui sort d'un cadre d'arrivée au sol ne s'y pose pas, même avec un bloc sous le cadre. Il y retombe aussitôt et repart par le retour, tant qu'il ne s'en écarte pas.

Un joueur entré par un cadre debout jaillit du cadre au sol de toute la vitesse de sa marche, et retomberait aussitôt dedans. Il est poussé dans le sens de son regard à la sortie, juste assez pour que son corps retombe tout entier au-delà du bord du cadre devant lui (`TeleportExecutor.playerExitVelocity`). Ses yeux, en haut du cadre debout, sortent souvent à l'arrière du cadre couché, loin de ce bord : quand la poussée la plus forte, l'élan d'un sprint, ne suffit pas, un petit saut allonge son temps en l'air, au plus celui d'un saut vanilla. Le client calcule la même poussée que le serveur pour sa prédiction. D'un cadre au sol à un autre, l'élan reste horizontal et rien ne s'ajoute. Les mobs et les objets ne sont pas poussés.

Une entité autre qu'un joueur attend dix ticks après une traversée avant de pouvoir repasser. Pendant ce délai, aucun cadre ne s'ouvre sous elle : retombée plus tôt sur son cadre d'arrivée, elle s'y tient jusqu'à la fin du délai, puis y tombe. Sans cela, elle passerait sous le plan sans traverser.

### Ce qui passe à travers

On voit l'autre côté avec ce qui s'y passe, portails compris. L'eau et le verre s'y voient comme sans portail, et de l'eau ou du verre posés devant le cadre le laissent voir par transparence. En troisième personne, la caméra qui suit le joueur reste de l'autre côté tant que son recul passe par le cadre.

Un objet jeté ou une flèche tirée dans le cadre ressort de l'autre côté, comme un mob qui s'y engage. Un véhicule traverse avec tout ce qui le monte, joueurs compris.

Le son de l'autre côté vient du cadre, et ses particules se voient dans la vue. Un mob hostile qui voit un joueur par le portail vient le chercher en le traversant, comme un mob frappé à travers lui. Si le portail la transmet, une torche éclaire l'autre côté d'un niveau de moins, comme à travers un bloc d'air. La lumière du ciel ne passe pas.

Viser à travers le cadre vise de l'autre côté, et le contour du bloc visé s'y dessine. On y frappe, mine, pose et utilise, et le clic molette prend le bloc ou la créature. Aucun écran ne s'ouvre et aucune monture ne se monte à travers un portail. Un portail fermé ou invisible ne laisse rien passer, lumière comprise.

### Retour et sortie fugace

À l'arrivée, le bloc pose un portail de retour, inverse exact de l'aller, et le repose s'il disparaît. En aller simple, il n'y en a pas.

Une sortie fugace est un retour qui n'existe que sous le regard. Pour chaque joueur, elle disparaît une demi-seconde après qu'il a cessé de la regarder. Elle n'est alors ni dessinée, ni heurtée, ni visée, et il la traverse comme de l'air. Elle revient dès qu'il la revoit à l'écran, troisième personne comprise.

Quand plus aucun joueur ne la regarde, elle disparaît pour tous. Elle revient au prochain joueur qui passe l'entrée, mais un objet, un mob ou un spectateur ne la rouvre pas. Une sortie que personne n'a encore vue attend.

Seul le regard compte, pas la distance. Se tenir tout près sans la regarder ne la garde pas, la regarder de loin la garde. Qui vient de sortir par elle a une seconde pour se retourner. Qui passe en marchant droit devant lui la trouve donc disparue, alors que qui passe à reculons peut revenir.

### Commande de développement

En environnement de développement, `/backrooms portal list` liste les portails chargés et le nombre de chunks distants suivis. `/backrooms portal clear` retire les portails chargés. Ceux d'un bloc sont aussitôt reposés par leur bloc.

## Fonctionnement

### Organisation du code

| Paquet de `core/portal/` | Contenu |
|---|---|
| `entity/` | `PortalFacing`, `BackroomsPortalEntity`, `BackroomsPortalEntityRegistry`. |
| `block/` | Bloc portail et sa block entity, réglages, cadre (`PortalArea`), destination (`PortalTargets`), retour (`PortalWayBack`). |
| `item/`, `client/wand/` | Baguette et son écran. |
| `light/` | Lumière transmise. |
| `transition/` | Préchargement, téléportation, collision. |
| `interaction/`, `client/interaction/` | Actions à travers le cadre. |
| `propagation/` | Regard et poursuite des mobs. |
| `sync/` | Payloads et synchronisation côté serveur. |
| `client/world/` | Mondes distants, index des portails, entités à cheval, paliers de détail. |
| `client/view/` | Rendu des vues. |
| `client/transition/` | Prédiction, bascule, continuité des entités. |
| `client/sound/` | Son à travers le cadre. |

Aucun mixin n'injecte dans `WorldRenderer.setupTerrain` ni dans `renderLayer`, que les mods de rendu réécrivent.

### Géométrie (`PortalFacing`)

Chaque portail a une base orthonormée directe : largeur W, hauteur H et normale N = W × H, tournée vers le côté d'entrée. Le passage vers l'arrivée est une rotation de 180° autour de H. Ce qui est devant la source se retrouve derrière la destination, et la droite reste la droite. `facingDestination` est donc l'orientation du portail de retour, normale vers la sortie.

La face arrière (`opposite()`) a le même plan et la normale opposée. Elle mène à la face arrière de l'arrivée par la même transformation. Une entité portail n'a qu'une face, et un bloc à deux faces en pose donc deux, dos à dos.

Chaque orientation a sa retournée (`flipped()`), un demi-tour autour de la normale, soit douze orientations en tout. La retournée ne sert qu'au passage d'une arrivée tête en bas. Le cadre lui-même se pose avec l'orientation droite (`upright()`).

### Serveur

#### Bloc et destination

`PortalWandItem` pose le bloc comme vanilla pose un bloc, composants recopiés dans la block entity. Chaque refus rend un échec, pour que le clic ne passe pas à l'usage dans le vide. Le serveur ne pose les réglages reçus que si la main désignée tient une baguette.

La block entity `BackroomsPortalBlockEntity` garde les réglages, seuls envoyés aux clients, et pose ses portails. Elle en pose quatre à deux faces, deux à une face, sans retour en aller simple. Elle remplace un portail qui ne correspond plus aux réglages, repose un retour disparu après deux vérifications espacées, et retire tout quand le bloc est cassé. Relu à une autre taille, le cadre d'arrivée garde son pied.

`PortalTargets.resolve` cherche l'arrivée chunk par chunk. Elle s'arrête au premier chunk qui n'est pas entier, que le bloc charge par un ticket. Elle reprend au tick suivant avec le même tirage, si bien que le serveur ne génère jamais un chunk sur son thread pour elle. Dans le Level 0, l'arrivée est un calcul pur sur le labyrinthe généré (`LevelZeroArrival`), dans un rayon de 24 cellules.

`BackroomsPortalEntity` porte les données synchronisées par `DataTracker` : orientations, arrivée, taille, visibilité, ouverture et lumière. `toDestination` et `fromDestination` passent un point d'un côté à l'autre. Toutes les 100 ticks, `BackroomsPortalEntityRegistry` retire les portails dont le bloc a disparu, si son chunk est chargé.

#### Préchargement

`PortalRelevance` précharge et synchronise l'arrivée de tout portail à portée de rendu, 64 blocs par défaut, quelle que soit la direction du regard. Le regard qui revient sur un portail trouve ainsi l'autre côté prêt. Le rendu demande en plus que le portail soit devant le joueur, sauf à portée d'activation, 16 blocs par défaut.

`DestinationLoader` tient chaque fenêtre d'arrivée chargée par un ticket, partagé entre portails. À moins de 8 blocs du portail, ce ticket couvre la distance de vue du serveur, et la traversée ne déclenche aucune génération. La fenêtre reste chargée dix secondes après que plus personne n'est à portée.

Un joueur qui change de dimension laisse un ticket de départ de dix secondes sur le côté quitté (`holdDeparture`). Vanilla retire ses tickets aussitôt. Sans ce relais, le côté quitté se déchargerait en quelques ticks, et ses entités reviendraient sous de nouveaux identifiants.

#### Téléportation

`TeleportExecutor` applique les traversées annoncées par le client, décrites plus bas, et détecte lui-même celles qui ne sont pas annoncées. Il transpose les objets, mobs et projectiles dont le déplacement du tick franchit le plan, dans le cadre élargi de trois blocs. Au point de passage, le centre doit être en face de l'ouverture et la boîte doit toucher le cadre. Sans cela, l'entité ressortirait dans le mur qui encadre l'arrivée.

Les entités transposées prennent un cooldown de 10 ticks. Entre dimensions, elles passent par `Entity.teleportTo`, et dans le même monde par un simple repositionnement. Un saut de plus de 8 blocs, comme un `/tp` ou un respawn, n'est jamais une traversée. Une entité neuve a en effet sa position précédente à l'origine du monde.

Un véhicule emmène tout ce qui le monte (`executeTree`). Les joueurs descendent d'abord, car un `teleportTo` vanilla les ferait passer par l'écran de chargement. Ils traversent par leur propre bascule, menée par le serveur, puis remontent sur leur monture, retrouvée par son UUID. La traversée d'un joueur monté se lit sur le trajet de son véhicule, qui ne bouge qu'aux paquets de son client. Dans le même monde, le client reçoit lui-même les passagers et la position du véhicule, par `EntityPassengersSet` et `VehicleMove`.

#### Collision

Une entité est engagée quand son point de traversée est devant le plan, en face de l'ouverture, et que sa boîte touche le cadre. `PortalCollision.withOtherSide` lui fait alors heurter les blocs de l'autre côté, ramenés derrière le plan. On bute ainsi sur les murs qui encadrent l'arrivée, et on monte sur un sol plus haut comme sur une marche.

Le client applique cette règle à son joueur, avec ses mondes distants, et le serveur aux autres entités. Le serveur ne l'applique pas aux joueurs, car il refuserait des pas que le client aurait faits librement. Le client et le serveur tiennent chacun leur suivi des engagements, choisi par `Worlds.side`.

`PortalCollision.tunnel` retire les blocs cachés derrière le plan de toutes les collisions de blocs de l'entité, par `MixinBlockCollisionSpliterator`. Le contrôle des pas d'un joueur par le serveur passe par là, si bien que le serveur l'applique aussi aux joueurs. `MixinEntity` et `MixinInGameOverlayRenderer` les retirent de même de l'étouffement et du voile de mur.

L'engagement est retenu dix ticks après le passage du plan (`CROSSING_TICKS`). Pendant ce temps, la face arrière du cadre traversé ne compte pas, car elle cacherait le côté d'où l'on vient. Du côté d'entrée, une entité dont le point de traversée est hors de l'ouverture bute sur l'ouverture fermée (`closedOpening`). Un joueur dont les épaules dépassent bute sur les bords du cadre (`frameEdges`) et glisse le long jusqu'à s'aligner.

Une entité dont le cooldown de portail vanilla court (`hasPortalCooldown`) ne s'engage dans aucun cadre (`PortalCollision.engages`) : ses blocs cachés restent là, comme sa traversée est refusée.

#### Lumière transmise

Toutes les 2 ticks, `PortalLightTransmission` fait une source virtuelle de chaque bloc du cadre d'un portail ouvert, visible et réglé pour la lumière. Sa lumière est celle du bloc correspondant de l'autre côté, moins un niveau. Les sources sont publiées sur le monde (`MixinWorld`) avant d'être signalées au moteur de lumière, qui les lit sur son propre thread. Le client fait de même pour ses mondes (`ClientPortalLight`).

Une source pas encore appliquée est signalée de nouveau. Le cadre d'un portail compté pour la première fois l'est aussi, car la lumière sauvée avec un chunk peut venir d'une source éteinte depuis. Les deux côtés d'une paire s'éclairent l'un l'autre, et sans vraie source, chaque aller-retour coûte deux niveaux.

### Protocole de synchronisation

Vanilla n'envoie au client que la dimension où il se trouve. `PortalViewTracker` envoie donc lui-même les chunks, les blocs et les entités de l'autre côté.

| Payload | Sens | Contenu | Quand |
|---|---|---|---|
| `portal_remote_dimension` | S → C | dimension, type, index | Une fois par dimension et par vue du joueur. L'index désigne ensuite la dimension sur un octet. |
| `portal_remote_chunk` | S → C | dimension, `ChunkData`, `LightData` | Chunk entré dans la fenêtre, au plus 8 par joueur et par passage toutes les 5 ticks ; près d'un cadre, au plus 24 à chaque tick tant que sa fenêtre n'est pas toute envoyée. |
| `portal_remote_chunk_unload` | S → C | index, chunk | Chunk sorti de la fenêtre. |
| `portal_remote_dimension_release` | S → C | index | Dimension que plus aucun portail ne montre depuis `remoteWorldReleaseSeconds`. Le client jette son monde distant. |
| `portal_remote_block_update` | S → C | index, position, `BlockState` | Bloc changé dans un chunk déjà envoyé, dédupliqué par tick. |
| `portal_remote_entity` | S → C | index, paquet vanilla | Suivi des entités, sons, particules, fissures, block entities et météo de la dimension. |
| `portal_remote_block_breaking` | S → C | dimension, position, étape | Fissures d'un bloc miné à travers un portail, pour le mineur. |
| `portal_teleport` | S → C | dimension, type, position, regard, vitesse | Traversée faite ou confirmée, ou prédiction refusée. |
| `portal_cross` | C → S | portail, position, vitesse, regard | La caméra du client a franchi le plan. |
| `portal_entity_action` | C → S | portail, dimension, entité, action | Clic sur une entité à travers un portail. |
| `portal_block_action` | C → S | portail, dimension, `BlockHitResult`, action | Clic sur un bloc à travers un portail. |
| `portal_wand_settings` | C → S | main, réglages | Fermeture de l'écran de la baguette, si les réglages ont changé. |
| `portal_sight` | C → S | monde, sorties fugaces vues, 64 au plus | Quand la liste change, au plus toutes les 4 ticks, et au moins chaque seconde. |

Les annonces de dimension d'un joueur repartent de zéro à chaque changement de monde. Une dimension libérée garde son index et sera annoncée de nouveau avant tout envoi. `portal_remote_entity` encode son paquet avec le codec `PlayStateFactories.S2C`, sur les registres de la connexion.

#### Fenêtre synchronisée

Toutes les 5 ticks, le serveur relève pour chaque joueur les portails à portée qui mènent ailleurs. Pour chaque dimension, il garde un foyer, le portail le plus proche, et une fenêtre de chunks autour de son arrivée. Le rayon vaut `preloadRadiusChunks`, 3 par défaut soit 7 × 7 chunks. Les fenêtres des autres portails s'ajoutent si elles tiennent dans la carte du client.

À moins de 8 blocs d'un portail, la traversée est imminente. La fenêtre couvre alors ce que vanilla enverrait au joueur en sortant : sa distance de vue, bornée par celle du serveur, et l'anneau qui la borde, sans lequel les dernières sections visibles ne se construisent pas (`nearRadius`). À 8 chunks de vue, cela fait 19 × 19 chunks, jamais moins que le rayon de préchargement plus 2, et jamais plus que ce que la carte du client tient autour du foyer. Tant que des chunks de la fenêtre attendent leur envoi, la vue du joueur se met alors à jour à chaque tick, 24 chunks au plus par tick, les plus proches de l'arrivée d'abord (`NEAR_CHUNKS_PER_TICK`) : la fenêtre est chez lui avant qu'il passe, même en marchant droit vers le cadre sans s'arrêter, et même si le serveur tique moins vite. Ensuite, elle revient à un passage toutes les 5 ticks.

À plus de 8 blocs d'un cadre d'arrivée debout, la fenêtre garde ce compte de chunks mais suit l'axe de ce que montre le cadre (`RemoteChunkWindow.collectCone`). Elle prend le carré de 3 × 3 de l'arrivée, puis, anneau par anneau, les chunks du coin de vue au-delà du cadre. Chacun vient avec ses quatre voisins, sans lesquels le client ne construit pas ses sections. Un joueur loin à côté d'une face, ou derrière elle, n'a pas de fenêtre pour elle, et une arrivée couchée garde le carré.

Chaque chunk du cône a son propre ticket côté serveur, relâché cinq secondes après que plus personne ne le veut. Un portail vu à travers un portail du joueur ajoute la fenêtre carrée de son arrivée (`seenThroughAnother`), s'il mène ailleurs que dans le monde du joueur. Un troisième monde se voit ainsi à travers un portail imbriqué.

Les déchargements partent avant les chargements, puis les entités des chunks envoyés sont alignées. Une dimension que plus aucun portail ne montre garde sa fenêtre cinq secondes, pour qu'un demi-tour ne renvoie pas tout. Son monde distant est libéré chez le client après `remoteWorldReleaseSeconds`.

#### Entités et blocs

Le serveur réutilise le suivi vanilla des entités. Un `RemoteEntityView` par joueur et par dimension s'ajoute aux auditeurs de chaque entité de la fenêtre. Il reçoit les mêmes paquets que les joueurs de la dimension et les enveloppe dans `portal_remote_entity`. Il envoie aussi la pluie et l'orage bruts de la dimension, lus par `WorldAccessor`, car `getThunderGradient` les multiplie.

Une entité qui arrive par un portail est suivie au même tick (`onEntityArrived`). Une entité qui quitte son monde est effacée aussitôt chez ceux qui la voyaient (`onEntityUnloaded`). Vanilla leur enverrait sinon une destruction sans enveloppe, que le client appliquerait à son monde principal. Comme chez vanilla, un spectateur n'est montré qu'aux spectateurs.

Un changement de bloc est relayé dans un chunk actif par `MixinServerChunkManager`. Dans un chunk seulement chargé, au bord de la fenêtre, vanilla ne prévient personne, et `MixinWorld` le relaie. Les sons, évènements de monde et particules partent de même aux joueurs dont la fenêtre contient leur chunk. Le client applique fissures et données de block entity au monde distant, et non au monde du joueur comme vanilla.

#### Traversée et réception

À la traversée, vanilla ne renvoie pas les chunks que le client tient déjà, pendant 100 ticks (`MixinChunkDataSender`). Vanilla propose un même chunk deux fois : quand il suit le joueur, puis quand le ticket du joueur rend le chunk actif, dans un monde déjà visité dont les chunks sont restés chargés. Un chunk tenu l'est pour chaque proposition. Un chunk tenu qui change part quand même, et un chunk que vanilla retire au client (le joueur s'éloigne) n'est plus tenu. Tant qu'un chunk attend son tour chez vanilla, aucun changement de bloc ne lui est envoyé, car vanilla compte l'envoyer entier. Après tout changement de monde, la vue du joueur repart au tick suivant.

Le client garde le monde quitté. La vue suivante du joueur tient pour envoyés les chunks que vanilla lui en avait donnés (`recordDeparture`) : ceux déjà post-traités qui n'attendent plus leur tour, les seuls que vanilla envoie. Les changements de blocs et de lumière du tick de la traversée, que vanilla n'envoie qu'en fin de tick, partent avec elle, avant l'annonce de la bascule : ils s'appliquent encore au monde quitté. Ensuite, leurs changements lui sont relayés, ils restent voulus tant qu'un portail montre encore ce monde (ceux que la carte du client tient autour du foyer de ce monde, qu'il recentre au besoin), et un retour ne les renvoie pas. Un bloc qui y change avant cette vue fait repartir son chunk. Sans portail vers ce monde, ils se déchargent chez le client après le délai de grâce, et le monde y est libéré après `remoteWorldReleaseSeconds`, comme toute dimension que plus rien ne montre.

Les récepteurs du client s'exécutent tels que Fabric les appelle, sur le thread de rendu et dans l'ordre des paquets. Les différer par `client.execute` les placerait derrière les paquets vanilla déjà en file. `portal_teleport` arriverait alors après les premiers chunks du nouveau monde.

### Mondes distants

`RemoteWorld` réunit un `ClientWorld` et un `WorldRenderer` propres à une dimension distante. Il charge les chunks comme vanilla. Leur lumière passe par `readLightData`, le monde du gestionnaire de paquets basculé le temps de l'appel, pour qu'un mod de rendu du terrain les voie comme les autres. Les paquets d'entités s'y appliquent avec le gestionnaire rebranché sur lui. Il suit l'heure du monde principal et reçoit la météo de sa propre dimension.

`RemoteWorldRegistry` tient ces mondes et les vues secondaires, et tique leurs entités. Tout est jeté quand vanilla change le monde principal, et un monde seul quand le serveur le libère. Un monde libéré est vidé avant d'être jeté, et ses tampons de dessin servent au renderer suivant.

`ClientPortalIndex` tient les portails chargés par monde client, que les parcours par image lisent. Dans le monde du joueur, `portals` omet les sorties fugaces qu'il ne regarde pas, et `allPortals` les rend toutes. `RemoteParticles` échange le contenu du gestionnaire de particules vanilla avec celui d'un monde distant, le temps de s'en servir.

### Rendu des vues

`PortalStencilRenderer` dessine les portails au milieu de `WorldRenderer.render`, après les blocs opaques et les entités, et avant les couches translucides. L'eau, le verre et les particules du monde du joueur se fondent ainsi par-dessus la vue. Les deux bits bas du stencil portent le niveau d'imbrication : 0 pour le monde du joueur, 1 pour une vue, 2 pour une vue imbriquée. Le reste du stencil est décrit avec [le rendu](RENDU.md).

Les portails se dessinent un par un, du plus proche au plus lointain :

1. La zone de vue passe au niveau suivant, là où rien ne la cache.
2. Sa profondeur est poussée au plan lointain, et le fond prend la couleur du brouillard de la vue.
3. La vue est dessinée depuis la caméra transposée, avec la lightmap de sa dimension.
4. Ses portails imbriqués suivent, sous le plan de découpe de la vue.
5. La zone reprend sa vraie profondeur et redescend au niveau courant.

Chaque vue se dessine sous un ciseau, le rectangle de son cadre à l'écran, et le GPU ne traite aucun fragment hors du cadre. Les vues ont leurs propres tampons de dessin, car ceux du monde principal attendent encore ses couches translucides. La matrice de vue empilée par vanilla est neutralisée le temps des portails. Après eux, le monde du joueur reprend son brouillard, son éclairage et une couleur de shader blanche.

#### Caméra et découpe

`RemoteViewRenderer` dessine la vue depuis la caméra transposée, avec la projection du joueur. Il prend le brouillard de la dimension vue, couleur comprise, pour que la vue ne se fonde pas dans celui du joueur. Le ciel se rapporte à la caméra de la vue, et le renderer de la vue tique lui-même sa pluie.

`PortalViewTransform` pose sur cette caméra la rotation complète, roulis compris, et ses trois directions. D'un sol à un mur, un lacet et un tangage seuls pencheraient l'image. `ViewLightmaps` calcule une lightmap par monde vu, une fois par tick.

Au chargement, `ShaderClipInjector` ajoute à chaque vertex shader vanilla un uniforme `backrooms_ClipPlane` et une écriture de `gl_ClipDistance[0]`. Pendant une vue, chaque shader reçoit le plan du portail d'arrivée, et ce qui est devant ce plan disparaît. Un shader sans l'uniforme suspend la découpe le temps de son dessin. `PortalClipping` tient une pile de deux plans, pour une vue puis une vue imbriquée, ou pour la moitié d'une entité à cheval.

La projection reste celle du joueur, pour le culling comme pour le dessin. Le `Frustum` vanilla boucle en effet sans fin avec une projection oblique. `ObliqueProjection` ne sert qu'aux shaders qui ignorent `gl_ClipDistance`, décrits plus bas.

#### Volume et sections

`ViewCone` est la pyramide qui va de la caméra aux arêtes du cadre d'arrivée, fermée par son plan. Sections, entités et portails imbriqués hors de ce volume ne sont pas dessinés. Sa partie en deçà du cadre dit s'il y a quelque chose à couper (`intersectsBeforeFrame`), sans compter un mur qui borde le cadre.

`ViewSectionWalker` choisit les sections de chaque vue par un parcours synchrone. Le culling asynchrone de vanilla ne tient qu'un graphe par renderer, et deux vues du même monde y alterneraient des listes incomplètes. Quand la caméra de la vue est en deçà du cadre, le parcours part des sections du cadre (`ViewCone.startsAtFrame`). Parti de la caméra, il n'atteindrait pas le cadre, et la vue lointaine ne montrerait que son ciel.

La grille de sections d'un renderer est ancrée sur l'arrivée du portail, et non sur la caméra. Elle ne se recentre que si l'ancrage s'éloigne de plus de deux chunks, si bien que la vue ne se redécoupe pas quand le joueur marche. Une seule vue par renderer et par image fait avancer le graphe de culling vanilla, qui reste chaud pour une traversée. À la traversée, le renderer promu est marqué pour que vanilla refasse sa propre liste de sections (`ClientPortalCrossing.switchWorld`).

Avant de dessiner, chaque renderer passe par `WorldRenderer.render`, arrêté dès sa première instruction vanilla (`ViewRenderStart`). `MixinWorldRenderStart` a la priorité 1100, si bien qu'il s'applique après les mods qui injectent en tête. Un mod qui pose un état par renderer en tête de rendu le pose ainsi pour la vue.

#### Zone du cadre

`FrameAreaRenderer` marque le cadre, plat, dans le stencil. Vu de profil, il n'est qu'un trait. Quand la caméra est dans l'encadrement à moins de 20 cm du plan, la zone devient une boîte mince, découpée contre le plan proche de la caméra. Elle couvre alors exactement ce que la caméra voit à travers.

La marque est tirée vers la caméra par `glPolygonOffset`, et avancée d'un dixième de millimètre. Un mur plaqué contre le cadre ne lui dispute ainsi pas la profondeur, même de près. La zone se dessine sans élimination des faces arrière, le temps de son seul dessin. Les couches de terrain vanilla supposent en effet cette élimination active, et l'eau ou le verre montreraient sinon leurs deux faces.

#### Troisième personne et repli

Quand le recul de la caméra traverse un portail, juste après une traversée, l'image entière devient la vue de l'autre côté (`ThirdPersonPortal`). La vue se dessine alors sans plan de découpe, et le stencil refuse le reste du monde du joueur.

Pendant la création d'un renderer de vue, ses tampons de sommets ne réservent leurs objets GL qu'au premier usage (`LazyVertexBuffers`). Une grille compte quatre tampons par section, et une vue n'en construit qu'une petite part.

`PortalRenderState` sert de coupe-circuit. Si le pilote refuse le stencil ou si un rendu distant lève une exception, le rendu distant est coupé pour la session. Les portails deviennent alors des cadres sombres.

### Occlusion des vues

`FrameOcclusion` prouve, à chaque image, qu'un cadre est caché derrière des blocs. Il cherche entre la caméra et le cadre une tranche d'un bloc d'épaisseur, pleine et opaque partout où passe le cône de vue. Chaque bloc doit aussi avoir une case libre devant lui, car vanilla ne dessine pas la face commune à deux blocs pleins. Le verre, les feuilles, les dalles et les entités ne prouvent rien.

La vue d'un cadre caché ne se dessine pas. Son renderer est seulement entretenu (`RemoteViewRenderer.keepWarm`), et elle revient dès l'image où le cadre se découvre.

Un bloc ne compte que si sa section est affichée à jour (`SectionGenerations`). `WorldRenderer.isRenderingReady` dit seulement qu'une section a été construite une fois. Chaque demande de reconstruction reçoit donc une génération, notée construite par la tâche qui a copié les blocs après elle.

### Paliers de détail des vues lointaines

`ViewDetail` choisit le palier d'une vue d'après la taille à l'écran d'un bloc juste derrière le cadre. La résolution et le zoom comptent donc d'eux-mêmes.

| Taille d'un bloc à l'écran | Palier |
|---|---|
| 24 pixels et plus | Pleine qualité, identique au rendu sans palier. |
| Moins de 24 pixels | Vue allégée : ni météo ni particules, entités figées, terrain simplifié. |
| Moins de 6 pixels | Image figée. |

Un palier remonte aussitôt, mais ne descend qu'une fois la taille passée 20 % sous le seuil. Une vue attend en plus vingt images après le dernier changement. Un portail imbriqué suit les mêmes règles depuis la caméra de sa vue, sans image figée.

`FrozenEntityLayers` écrit une fois la géométrie des entités dans des tampons du GPU, puis la redessine telle quelle. La prise se refait toutes les cinq secondes, ou dès que la caméra s'est déplacée d'un bloc.

`CoarseSectionMesher` simplifie le terrain section par section. Il ne garde que les faces visibles des cubes pleins et opaques, fusionnées en carrés de deux blocs sous 12 pixels, et de quatre sous 8 pixels. Une section qui contient d'autres blocs reste au maillage vanilla. `CoarseTerrain` construit au plus deux sections par image et par renderer.

`FrozenViews` capture la vue allégée dans un framebuffer, au double de la résolution du cadre, puis la colle sur le cadre. L'image se reprend toutes les cinq secondes, ou quand la caméra s'est déplacée d'un vingtième de sa distance au cadre. Une image incomplète se reprend après un quart de seconde.

Le portail prend son image d'avance à huit blocs de la distance de rendu des vues. Au-delà, et jusqu'à 128 blocs, il ne montre plus qu'elle. L'image est libérée après trente secondes sans usage. Le terrain simplifié et l'image figée n'existent que sans pack de shaders.

### Portails imbriqués et vue secondaire

Chaque portail visible dans une vue est dessiné à son tour, au niveau 2 du stencil, sous le plan de découpe de la vue qui le contient. Au-delà, un portail est transparent. Un portail imbriqué dont l'arrivée n'est pas synchronisée reste un cadre sombre.

Dans une vue imbriquée, les entités engagées dans le cadre d'arrivée se dessinent après les autres, découpe suspendue (`PortalClipping.suspend`). Il n'y a plus de plan pour leur autre moitié, et leurs modèles coupés montreraient leur intérieur.

Quand un portail mène à la dimension du joueur, sa vue est une vue secondaire. C'est un second `WorldRenderer` sur le `ClientWorld` du joueur, dont les sections à reconstruire suivent celles du renderer principal. Elle reste liée à son monde après une traversée et resert telle quelle au retour. Créer un `WorldRenderer` à chaque traversée figerait l'image, surtout en troisième personne.

### Traversée sans coupure

Un changement de dimension vanilla recrée le `ClientWorld`, affiche l'écran de chargement et reconstruit le rendu. La traversée d'un portail évite tout cela.

#### Prédiction par le client

À chaque image, `ClientPortalCrossing` regarde si les yeux du joueur ont franchi le plan d'un portail, à la position affichée. Si oui, le client transpose lui-même position, regard et vitesse. Il échange le monde principal avec le monde distant déjà chargé (`RemoteWorldRegistry.swapMainWorld`), puis envoie `portal_cross`. L'image suivante est déjà celle de l'autre côté, avec la lightmap de sa dimension. L'ancien monde devient un monde distant, et le portail de retour le montre sans rien recharger.

#### Arrivée déjà construite

Une vue ne construit que les sections que montre le cadre. À moins de 8 blocs d'un cadre vers une autre dimension, son renderer construit d'avance, les plus proches d'abord (`RemoteViewRenderer.prebuildArrival`), celles qui entourent l'arrivée dans tous les sens sur 4 sections (64 blocs), ce que le joueur voit en se retournant, puis celles qui sont devant la sortie jusqu'à la distance de vue, ce qu'il voit en sortant. En hauteur, de 2 sections sous l'arrivée jusqu'à la plus haute section non vide de la colonne, une colline ou une montagne au loin. Une section vide ne dessine rien et ne se construit pas d'avance. Trente-deux sections par image au plus, dans un chunk chargé entouré de ses quatre voisins : un client à peu d'images par seconde, machine chargée ou rendu logiciel, prépare quand même l'arrivée pendant son approche. Un parcours qui ne trouve plus rien à construire se repose 100 ms : sans cela, il se referait à chaque image tant que le joueur reste près du cadre. Un tel cadre hors du champ, dans le dos, sous les pieds ou sur le côté, ne se dessine pas, mais sa vue s'entretient et se construit de même, après les vues dessinées : on le passe sans l'avoir regardé. Rendu vanilla seulement : sous un pack de shaders, le pack dessine chaque vue en entier.

Un renderer de vue n'a que deux tampons de construction, quand vanilla en donne un par cœur à celui du joueur. À chaque tick, la réserve du renderer du joueur s'ajuste au compte de vanilla, et celle de chaque vue à deux (`BuildBuffers`) : le renderer d'une vue promu par une traversée construit ensuite au rythme de vanilla, et l'ancien, devenu une vue, rend les siens. La vue d'un cadre que le joueur peut passer, celle dont l'arrivée se construit d'avance, a le compte de vanilla, jusqu'à cinq secondes après qu'il s'en est éloigné (`prebuilding`) : elle prépare l'arrivée au rythme du renderer qu'elle deviendra, et l'ancien renderer du joueur, juste derrière lui après la traversée, garde ses tampons tant qu'il peut repasser. Une réserve ne se touche que dans la file de son chunk builder, sur les fils de travail, où ses tâches en cours lui rendent leurs tampons : des tampons s'y créent, des tampons libres s'y ferment, et aucune réserve ne change de chunk builder. Changer la réserve d'un chunk builder en pleine tâche, ou déplacer des tampons d'une réserve à l'autre depuis le fil de rendu, en viderait une pour de bon : ce renderer ne reconstruirait plus rien, pas même un bloc changé de l'autre côté d'un cadre. Les tampons d'un renderer de vue libéré ne servent au suivant qu'au bout de cinq secondes, le temps que ses dernières constructions rendent les leurs.

#### Confirmation par le serveur

Le serveur vérifie l'annonce. Le portail doit être dans le monde du joueur, hors cooldown et hors de son arrivée gardée. La position annoncée doit être à moins de 3 blocs de la sienne, et les yeux à moins de 1,5 bloc du cadre. Le changement de dimension suit alors la séquence vanilla, sans paquet de respawn.

Le serveur confirme par `portal_teleport` sans renvoyer de téléportation vanilla, qui ramènerait le joueur un tick en arrière. Le client ignore une confirmation identique à sa prédiction à 30 cm près. Une annonce refusée renvoie l'état du serveur, et le client revient dans le monde que le flux vanilla n'a jamais quitté. Si le client n'annonce rien dans les cinq ticks, le serveur bascule lui-même. Aucun délai n'est imposé entre deux traversées.

#### Flux de paquets

Tant que le serveur n'a pas confirmé, les paquets vanilla alimentent l'ancien monde. À la confirmation, vanilla nettoie l'ancien monde du joueur. `MixinClientPlayNetworkHandler` ignore alors ces déchargements, pour garder la vue derrière soi, et applique les destructions d'entités à l'ancien monde.

Le serveur retire puis renvoie les entités que le joueur voyait, par l'autre voie et sous le même identifiant. Le client diffère de dix ticks le retrait de ces copies (`EntityHandover.deferRemoval`). La copie renvoyée remplace ainsi l'ancienne sans trou, et le portail de retour juste derrière le joueur ne disparaît pas.

Le même identifiant suppose que le serveur n'a pas recréé l'entité, d'où le ticket de départ. Si une copie arrive sous un autre identifiant, l'ancienne est retirée avant que vanilla n'indexe la nouvelle (`replaceStaleCopy`). L'index des UUID refuserait sinon la nouvelle, qui resterait affichée, figée.

#### Continuité du joueur

Le joueur reste le même objet, si bien que la marche, le balancement de la vue et l'objet tenu continuent. Le portail le tourne souvent d'un demi-tour. Tout ce qui s'interpole tourne alors avec lui, valeur courante et précédente ensemble, par le plus court chemin. Le serveur ne renvoie pas de vitesse après la traversée, car elle écraserait celle du client.

`ClientPortalCrossing.WORLD_SWITCHED` part à la fin de chaque bascule par un portail, prédite ou confirmée. Les évènements vanilla de changement de monde ne partent pas dans ce cas. Le moteur [audio](AUDIO.md) s'en sert pour relancer ses calculs.

### Entités à travers les portails

Les autres entités ne sont pas prédites. Le serveur les transpose seul, en les retirant d'un monde pour les recréer dans l'autre. Chez celui qui regarde, la nouvelle copie reprend l'état visible de l'ancienne (`EntityHandover`). Elle garde l'âge, le balancement des membres, la tête et le corps tournés avec le portail, et la position affichée.

Le client affiche les entités avec environ un bloc de retard, à cause de l'interpolation. Une entité téléportée n'a donc pas encore atteint le plan à l'écran. Sa destruction est différée jusqu'à l'arrivée de sa copie de l'autre côté, que le serveur envoie au même tick. Seule une entité vivante qui, à sa vitesse, passe bientôt le plan en face de l'ouverture est ainsi gardée.

La copie, reprise à la position affichée, est souvent encore derrière le plan d'arrivée. Elle reste une arrivée, dessinée des deux côtés, jusqu'à en être sortie. Vanilla n'envoie une position absolue qu'au-delà de huit blocs. Pour un saut plus court par un portail du même monde, le serveur force le prochain envoi absolu (`onEntityJumped`).

`StraddlingEntityRenderer` dessine en deux moitiés toute entité à cheval sur un portail, joueur compris. Chaque moitié est coupée au plan dans son monde, et la moitié passée se dessine dans la vue, transposée. Une entité est à cheval dès que son modèle touche le plan, à un demi-bloc près, ou un bloc pour une entité vivante. Juste après une traversée, la partie encore derrière se dessine devant le cadre d'entrée.

Chaque décision porte sur la position affichée de l'image, et non sur celle du tick, qui a jusqu'à un tick d'avance. Le point de traversée décide, comme pour la traversée elle-même. Sur un cadre au sol, les pieds d'une entité passent en effet le plan bien avant ses yeux.

### Interaction à travers le cadre

Après le raycast vanilla, `RemoteTargeting` regarde si le rayon du regard traverse un cadre avant sa cible. Si oui, il repart du point transposé de l'autre côté, sur la portée restante, contre les blocs puis les entités. Vanilla reçoit une cible manquée et ne touche plus au bloc caché par le cadre. La vue dessine le contour du bloc visé par `drawShapeOutline`, car Fabric API intercepte `drawBlockOutline` hors de `render`.

`RemoteActions` envoie frappe, utilisation et minage au serveur, avec le portail, la dimension et la cible. Le minage annonce son début et son abandon, sans prédiction. Les délais vanilla s'appliquent : 4 ticks entre deux utilisations, 5 entre deux blocs cassés en créatif. Sans cible de l'autre côté, vanilla garde la main.

`RemoteInteractionHandler` vérifie que le portail est visible, devant le joueur et à portée. La cible doit être à portée du point où le regard ressort, et `canPlayerModifyAt` doit accepter un bloc. L'action vanilla s'exécute ensuite avec le joueur transposé dans la dimension d'arrivée, son écouteur de déplacement coupé. Recul, orientation de pose et portée se calculent ainsi comme s'il y était.

L'utilisation d'une entité se limite aux créatures, sans monture ni véhicule. L'utilisation d'un bloc saute `onUse` pour un bloc à block entity, dont l'écran se fermerait aussitôt. `RemoteMiningTracker` compte le minage sur le serveur seul et envoie les fissures à tous ceux qui voient le bloc. Il abandonne si le client lâche, si le bloc disparaît, ou si le joueur s'éloigne ou meurt.

Vanilla n'envoie pas à l'auteur d'une action le son et les éclats qu'elle produit (bloc posé, bloc cassé) : son client les a déjà joués en la prédisant. À travers un cadre, rien n'est prédit. Pendant une action à travers un cadre de son propre monde, ce que vanilla lui retient lui est donc envoyé (`RemoteInteractionHandler.sendWithheld`, appelé par `MixinPlayerManager`). Vers une autre dimension, le relais aux joueurs qui la voient le sert déjà, une seule fois.

### Son, particules et mobs

Côté client, un son d'un monde distant est rejoué au cadre des portails qui mènent à ce monde (`FrameSoundPlayer`). Il part du point du cadre le plus proche de l'image de la source, si le joueur est devant la face. Son volume est celui qui lui reste après le trajet jusqu'au cadre (`PortalSound`), sur une portée de 16 à 64 blocs. Un son sans chemin jusqu'au joueur n'est pas joué.

Un son peut passer deux cadres, par un monde intermédiaire que le client tient, et le plus court chemin l'emporte. Un son du monde du joueur à plus de 64 blocs passe de même par un portail qui reste dans la dimension. Chaque son garde sa vraie source (`SoundAnchors`). Après une traversée, les sons encore joués sont replacés sans relance, à leur place ou au cadre par lequel on entend leur monde. Le traitement acoustique est décrit avec [l'audio](AUDIO.md).

Chaque monde distant a son propre contenu de particules (`RemoteParticles`), qui naît dans un rayon de 32 blocs autour de la caméra de la dernière vue. Il vit tant qu'une vue de ce monde a été dessinée depuis moins de deux secondes. Les particules se dessinent dans la vue après les couches translucides, sous le plan de découpe.

Un mob qui cherche un joueur et n'en trouve aucun dans son monde regarde aussi par les portails ouverts à sa portée (`PortalPursuit`). Il applique au joueur de l'autre côté ses propres règles de ciblage, puis mesure la vue par le cadre, d'un lancer de rayon de chaque côté. S'il le voit, son objectif de poursuite lui donne la posture d'attaque. Il marche d'abord en face de l'ouverture, puis en ligne droite à travers elle.

De l'autre côté, le joueur devient sa vraie cible, et sa copie neuve repart aussitôt vers lui (`resumeAfterCrossing`). Un mob frappé à travers un portail le poursuit de même. Une cible dans un autre monde n'est jamais gardée telle quelle, car vanilla y mesurerait des distances sans aucun sens.

### Sortie fugace

`PortalSightReporter` juge à chaque tick les sorties fugaces que le joueur regarde. Une sortie compte comme vue si l'un des neuf points de son cadre se voit sans cube plein et opaque devant. Elle compte aussi si la caméra est passée derrière elle en troisième personne. Le client rapporte cette liste au serveur par `portal_sight`.

`PortalSight` tient ces rapports par joueur, avec sa dernière arrivée par un portail. Un rapport qui vient d'un autre monde ou date de plus de 40 ticks est ignoré. Tout est oublié à la déconnexion, au changement de monde vanilla et au respawn. Le serveur ne fait passer une sortie fugace qu'à qui la voit (`PortalSight.sees`).

Toutes les 4 ticks, la block entity retire ses retours quand, après avoir été vus, plus aucun joueur ne les voit depuis une demi-seconde. Elle les repose dès qu'un joueur repasse l'entrée (`onPlayerCrossed`), jamais pour un spectateur.

Le serveur ne repose la sortie qu'à l'annonce du passage, après la bascule du client. Le client pose donc aussitôt un retour prédit à sa place exacte (`ClientPortalIndex.predict`). Ce retour s'efface à l'arrivée du vrai, ou au bout de deux secondes. Sans lui, le joueur qui se retourne verrait ce qu'il y a derrière le cadre.

### Pack de shaders et mods de rendu

Un mod de rendu du terrain se reconnaît à la grille vanilla qu'il crée vide (`RemoteViewRenderer.foreignTerrain`). La vue le laisse alors choisir et dessiner ses sections. Ses shaders ignorent `gl_ClipDistance`, si bien que la vue passe par une projection à plan proche oblique (`ObliqueProjection`). Cette projection ne sert que si un bloc ou une entité se trouve entre la caméra de la vue et le plan d'arrivée.

Un pack de shaders se reconnaît sans le nommer, au milieu du rendu du joueur (`ShaderPackState`). Sa passe finale ignore le stencil, si bien que les vues se dessinent après l'image du joueur (`ShaderPackPortalRenderer`). Chaque vue est un rendu complet de son renderer, où le pack déroule son pipeline pour la dimension vue. L'image du joueur, gardée dans une texture, est ensuite remise autour du cadre.

Pour ce rendu complet, le mod prête au pack l'état qu'il attend :

- la caméra du joueur prend la place et la rotation de celle de la vue ;
- l'entité caméra devient le portail d'arrivée, où le pack lit la luminosité ambiante ;
- le renderer de la vue emprunte les tampons de dessin du client, les seuls que le pack vide ;
- l'écriture de la profondeur est rétablie, car la passe finale du pack la laisse coupée ;
- le balancement que le pack porte dans la matrice de vue (`VIEW_EXTRA`) s'applique aussi à la vue.

Les entités à cheval sont rognées au plan sur le processeur (`ClippedVertexConsumers`). Les pixels de la main sont protégés par un bit du stencil (`HandMask`), car le pack la dessine à sa vraie profondeur et un cadre proche passerait devant elle. Un niveau de portails imbriqués suit le même chemin dans la vue.

Le pack tient un pipeline par dimension et nomme pareil les programmes de chacun. Vanilla redonne à tout programme un étage déjà compilé sous le même nom. `MixinShaderProgram` ne redonne donc un étage qu'aux programmes chargés par la même fabrique (`ShaderStageOrigin`), sans quoi une vue serait éclairée comme l'autre dimension.

Dans une dimension à look, le module de compatibilité rend la main aux shaders du mod, et les vues y suivent le chemin sans pack. Ce chemin est décrit avec [le rendu](RENDU.md). L'option `shaderCohabitation` remplace les vues par des cadres sombres sous un pack.

### Points d'accès

| Point d'accès | Usage |
|---|---|
| `ClientPortalCrossing.WORLD_SWITCHED` | Évènement à la fin de chaque bascule par un portail. |
| `PortalRenderState.setViewsSuppressed` | Coupe les vues de l'image, comme un rendu distant indisponible. |
| `SoundAnchors` | Vraie source de chaque son joué et point d'entrée dans le cadre. |
| `PortalSound` | Portée, volume après le trajet et meilleur cadre pour une source. |
| `LevelZeroArrival` | Arrivée dans le Level 0, fournie par le contenu au socle. |

## Réglages

Les options des portails, en `gtgsbackrooms.portal.*`, sont décrites dans la [référence des options](../reference/OPTIONS.md#portails).

## Limites

### Traversée

- La prédiction n'a lieu que si le monde distant est déjà chargé. En sprintant vers un portail qu'on vient de poser, c'est le serveur qui bascule, avec un tick de latence.
- En selle, c'est toujours le serveur qui bascule. Le client affiche un instant l'aide pour descendre, comme à une montée.
- L'arrivée n'est complète et construite d'avance que si le joueur s'approche du cadre. Arrivé en quelques ticks à moins de 8 blocs, téléporté ou bien plus vite qu'en sprint, il passe avant elle, et le reste arrive après la bascule, comme dans un monde vanilla.
- Près d'un cadre, la fenêtre suit la distance de vue du joueur : 361 chunks à 8 de vue, plus de mille à 16. Le monde quitté reste chez le client tant qu'un portail le montre, et ses chunks restent chargés sur le serveur, sans être actifs.
- Derrière la sortie, au-delà de 64 blocs, rien ne se construit d'avance : ce qui y est se construit au rythme de vanilla, quand le joueur se retourne. Devant, sur un client à très peu d'images par seconde (10, rendu logiciel), quelques sections au-delà de 70 blocs peuvent manquer un instant après le passage.
- Près d'un cadre, l'approche coûte un peu plus : la fenêtre et sa construction d'avance arrivent avant la bascule au lieu d'après. Mesuré sous charge (18 cœurs sur 20 occupés), le 99e centile des images de l'approche passe d'environ 16 à 17-21 ms.
- Les blocs cachés derrière le plan ne le sont que pour les collisions, l'étouffement et le voile de mur. Un fluide reste, et on y nage. Les effets de contact d'un cactus, d'une toile, d'un buisson de baies ou du feu restent aussi.
- Un mod qui calcule les collisions sans le parcours de blocs vanilla ne voit pas les blocs cachés.
- Le serveur ne vérifie pas que le corps d'un joueur engagé reste hors des blocs de l'autre côté. Il fait confiance au client.
- Un bloc portail posé exactement à l'arrivée d'un autre superpose leurs portails, et le joueur peut être renvoyé d'un côté à l'autre. Il faut décaler l'un des deux.
- Vu de l'autre côté du mur, rien ne montre un cadre plaqué contre lui.

### Cadres couchés

- Un cadre au sol est une trappe pour tout ce qui y marche, arrivées comprises, sauf pour qui en sort et en est gardé.
- Un objet ou un mob immobile entre deux cadres au sol qui se renvoient l'un à l'autre fait l'aller-retour, environ une fois par seconde.
- La main tenue, dessinée à part, reste droite pendant que la caméra se redresse.
- Une entité qui arrive par un cadre tête en bas ressort debout. Seule la caméra du joueur se renverse.
- Dans le Level 0, qui n'a que quatre blocs d'air, un cadre au plafond ne se rejoint qu'en volant.
- Les mobs ne cherchent pas de chemin vers un cadre couché. Ils y tombent s'ils marchent dessus.

### Arrivées

- À ciel ouvert, le cadre d'arrivée se pose sur le haut du sol, eau comprise, sans vérifier qu'il y tient. Un arbre ou le relief peut le traverser.
- À la surface, un cadre d'arrivée au plafond est relevé de deux blocs, pour que qui en tombe ait les pieds au-dessus du sol.
- Vers un endroit jamais visité, le cadre ne devient portail que quelques ticks après la pose, le temps que vanilla génère les chunks lus.
- Sans sol à portée, la plateforme d'obsidienne modifie le monde d'arrivée.
- Dans le Level 0, un grand cadre qui ne tient dans aucune cellule proche peut entrer dans les murs. Plus haut que quatre blocs, il entre dans le plafond.

### Sortie fugace

- Un client sans le mod, ou sans rendu distant, ne voit aucune sortie fugace. Il ne la garde pas et ne la traverse pas.
- Seuls les cubes pleins et opaques cachent un cadre. Une sortie cachée par une entité, une plante ou un bloc partiel compte comme vue.
- Une sortie vue à travers un autre portail ne compte pas comme vue.
- Un coup d'œil de moins d'une demi-seconde la garde, et une image isolée sans elle ne la perd pas.
- Relue après un redémarrage du serveur, une sortie attend de nouveau un premier regard.
- Cachée à un joueur, la sortie existe encore pour le serveur, et la lumière et les entités la traversent.

### Vues

- Un seul niveau d'imbrication est dessiné. Un portail vu à travers un portail imbriqué est transparent.
- Une dimension distante n'a qu'une grille de sections, ancrée sur le portail visible le plus proche. Un second portail vers la même dimension, à plus de deux chunks de là, ne montre que ce que cette grille couvre.
- Une arrivée qui ne tient pas dans la carte du client autour du foyer de sa dimension reste sombre.
- La pluie d'un monde distant n'éclabousse qu'autour de la caméra de la vue. Dans une vue secondaire, elle tombe sans éclaboussures.
- En troisième personne, regard incliné, le recul sort par le haut d'un cadre au bout d'un à deux blocs, et la caméra revient derrière le cadre.
- Une entité à cheval n'a pas d'ombre au sol pendant le passage. Ce qui dépasse de l'ouverture derrière le plan ne se voit pas.
- Le nom d'une entité qui traverse peut tourner ou disparaître une fraction de seconde.
- À moins de 5 cm du plan et juste à côté de l'ouverture, une mince bande de la vue manque au bord de l'écran.
- La première vue d'une dimension crée son renderer dans l'image, d'où un à-coup unique et une image encore vide.
- La fenêtre en cône suit la caméra de la vue avec un peu de retard, à huit chunks par passage au plus.

### Paliers de détail

- La preuve d'occlusion ne vaut qu'avec le rendu vanilla du terrain. Un mod de rendu du terrain ou un pack de shaders la coupe.
- Les entités figées avancent par sauts, et un nom au-dessus d'une tête reste tourné vers la caméra de la prise.
- Le terrain simplifié étire la texture de ses carrés et n'a pas d'occlusion ambiante. Il n'apporte aucun gain mesurable là où le terrain d'une vue coûte déjà peu.
- L'image figée ne se prend que cadre entier à l'écran. Un cadre jamais vu de plus près que la distance de rendu n'en a pas.
- Rien ne bouge dans l'image figée entre deux reprises, et ses bords mêlent d'un demi-pixel la couleur de fond.
- En 1280 × 720, le palier figé ne commence qu'au-delà d'environ 65 blocs. En pratique, l'image sert surtout de 64 à 128 blocs.

### Interaction, mobs et son

- Un spectateur ne peut rien faire à travers un portail.
- Le balayage d'épée est évalué dans le monde du joueur. Les sons et particules du coup y partent aussi, et seul le son de frappe est rejoué au joueur.
- Seuls les mobs à objectifs de ciblage voient à travers un portail, pas les piglins, les villageois ni le warden. Un squelette traverse au lieu de tirer.
- Un son qui suit une entité depuis son apparition, comme celui d'un wagonnet, ne traverse pas.
- Un son n'est relayé que si son chunk est dans la fenêtre synchronisée du joueur. Un son passe deux cadres au plus.
- `/playsound` n'est pas relayé, car son paquet part aux seuls joueurs visés, sans monde.
- Une lumière retirée s'éteint en quelques passes, deux niveaux par aller-retour.

### Mods de rendu et packs de shaders

- Loin du plan, la projection oblique perd de la précision de profondeur, et des faces lointaines peuvent scintiller.
- Sous un pack, une entité à cheval ne projette pas d'ombre pendant la traversée.
- Sous un pack, un cadre derrière l'eau ou le verre n'est pas dessiné, car le pack compose ses translucides avant sa passe finale.
- Sous un pack, quand la projection oblique sert, le ciel de la vue est délavé et sans nuages.
- Sous un pack, l'adaptation de l'œil est prise au portail d'arrivée. Les effets temporels voient deux caméras par image dans une vue du monde du joueur.
- Sous un pack, chaque vue coûte un rendu complet, avec un pipeline par dimension vue.
- Un mod qui dessine du terrain lointain et suit le monde courant par `joinWorld` ne voit pas une traversée par portail. Il peut dessiner la dimension quittée jusqu'au prochain changement de monde vanilla.

### Ressources

- Chaque vue appelle des fonctions vanilla qui allouent environ 16 Kio par image : brouillard, ciel, entités, préparation des sections. Le code du mod n'alloue rien sur le thread de rendu.
- Chaque dimension distante coûte un `WorldRenderer`, avec une trentaine de Mio de tampons hors du tas Java. Il est libéré après `remoteWorldReleaseSeconds` sans portail vers elle, et un retour voit la vue se reconstruire.
- Le contexte de rendu de Fabric API et l'état de chargement vanilla peuvent garder un monde libéré, vide, jusqu'au prochain changement de dimension vanilla.

### Problèmes ouverts

- Sous Linux avec les pilotes Mesa, l'image du joueur se fige après une traversée, puis ne change plus en marchant. La cause n'est pas trouvée, et la CI échoue sur `portal_view_and_seamless_crossings` tant que ce n'est pas corrigé.
- Rarement, à la fermeture d'une partie solo, le serveur intégré boucle dans `MinecraftServer.shutdown`, sans code du mod dans la pile. D'après le code, les tickets du mod ne sont pas en cause. Le test `end_of_game` journalise les tickets restants au prochain blocage.

## Tests

Le fonctionnement général des tests est décrit avec [les tests](../dev/TESTS.md).

Les tests unitaires couvrent la logique pure du système. On y trouve les 144 paires d'orientations de `PortalFacingTest`, la collision dans les douze orientations, la fenêtre en cône et la découpe ajoutée à 54 des 55 vertex shaders vanilla.

Les tests en jeu prennent le Nether comme dimension distante, car le serveur de test n'a pas de Level 0.

| Classe | Domaine |
|---|---|
| `BackroomsPortalBlockGameTests` | Pose, faces, aller simple, sortie fugace, relecture des réglages, retours. |
| `PortalWandGameTests` | Réglages reçus, pose, refus, copie, plan au bord du bloc. |
| `PortalGameTests`, `PortalTunnelGameTests` | Traversée, collision à travers le cadre, blocs cachés, arrivée au sol. |
| `PortalOrientationGameTests`, `PortalLyingGameTests` | Les 144 paires d'orientations, cadres couchés, arrivée tête en bas. |
| `PortalRidingGameTests` | Véhicules et passagers. |
| `PortalDestinationGameTests`, `PortalArrivalReferenceGameTests` | Recherche d'arrivée, sortie fixée, huit arrivées de référence figées. |
| `PortalSyncGameTests` | Fenêtre de chunks, entités, météo, chunks tenus, annonces de traversée. |
| `PortalInteractionGameTests`, `PortalPropagationGameTests` | Actions à travers le cadre, leur son et leurs éclats chez qui agit, sons et particules relayés, poursuite des mobs. |
| `PortalLightGameTests` | Lumière transmise. |

Les tests du client vérifient la vue du Level 0 dans un cadre et un aller-retour sans couture, mesuré d'image en image (`PortalViewClientTests`). `PortalFeaturesClientTests` vérifie les paliers de détail, la troisième personne, les clics, l'écran de la baguette, une vue imbriquée, le son à travers le cadre, le son et les particules de l'autre côté dans les deux sens d'une traversée, le son et les éclats d'un bloc cassé ou posé à travers un cadre vers le même monde, et l'arrivée au sol, où le joueur ne se pose jamais.

### Diagnostic

`./gradlew runClient -PportalDebugRendering=true` écrit une ligne par seconde et par portail rendu : chunks chargés, entités, état du renderer. En cas de gel, `jstack` sur le PID `devlaunchinjector` donne la pile du thread de rendu.

Le moniteur de performance (`-PdebugPerformanceMonitor=true`) journalise chaque traversée sur une ligne `[perf] traversee:`. Elle donne la durée de la bascule, la pire image et le nombre d'images lentes qui suivent. Le résumé du client détaille les sections `client.portal.*`, et celui du serveur les sections `portal.*` et le volume réseau de chaque payload.
