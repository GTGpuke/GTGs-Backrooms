# Assets et production par IA — GTG's Backrooms

> **Document de conception interne** : il nomme des outils et projets tiers. À garder hors du dépôt public, et sans
> aucune reprise de code ni mention de ces projets dans le mod (`QUALITE_ET_PRESETS.md` §3.0).
>
> Objet : tout ce qu'il faut **produire** (blocs décoratifs, meubles, éléments d'architecture, monstres, animations)
> plutôt que **développer**, et la façon de le produire **par IA**, sans maîtriser Blockbench, en gardant un style et
> une technique cohérents : **32×32 pixels par bloc**, aspect blocky, et matériaux complets (brillant, mat, creux,
> normal maps…) au format de matériaux des moteurs (`QUALITE_ET_PRESETS.md` §5.6).
>
> Statut : feuille de route. Les outils cités ont été vérifiés sur leurs pages publiques le 28 octobre 2026 ; leurs
> prix, licences et fonctions peuvent changer.

---

## 1. Principe : la cohérence avant la quantité

> **Règle n° 1 du projet pour les assets : la cohérence et le style sont ultra importants.** Tout outil, toute voie de
> production et tout asset qui ne les garantit pas est écarté, même s'il est plus rapide.

Produire des centaines d'objets par IA donne vite un mélange de styles. La cohérence ne vient pas de l'outil, mais de
**règles écrites avant de produire**, que chaque outil et chaque prompt respectent :

1. une **bible de style** (§4) : palette, grille, densité de pixels, proportions, règles de modélisation ;
2. une **bibliothèque de matériaux** (§5) : chaque surface du jeu est l'un de ces matériaux, avec ses valeurs fixées ;
3. des **kits modulaires** (§6) : un même élément décliné en variantes, plutôt que des objets uniques sans lien ;
4. un **validateur automatique** (§9) qui refuse tout asset hors règles, comme Checkstyle pour le code.

L'IA produit ; les règles garantissent que tout se ressemble.

---

## 2. Contraintes techniques de Minecraft 1.21.1 à respecter

### 2.1 Modèles de blocs (JSON)

- Un modèle de bloc est un ensemble de **boîtes** (éléments) dont les coordonnées vont de **-16 à 32** (un bloc = 0 à 16).
  Un modèle peut donc déborder du bloc, mais **la collision reste celle du bloc** : les gros meubles sont des
  **multiblocs** (§2.4).
- Chaque élément ne peut tourner que sur **un seul axe**, par pas de **22,5°** (-45°, -22,5°, 0, 22,5°, 45°).
- Coordonnées et UV sur la grille du mod : **1 texel = 1/32 de bloc = 0,5 unité de modèle**. Toutes les dimensions sont
  des multiples de 0,5 (§4.2).
- Les modèles statiques sont **cuits dans le maillage des chunks** : coût quasi nul à l'affichage, mais chaque
  élément ajoute des faces. Budget d'éléments par objet (§4.4), surtout pour la cible PC de bureau.

### 2.2 Pentes

| Inclinaison                     | Méthode                                                                                     |
| ------------------------------- | ------------------------------------------------------------------------------------------- |
| 22,5° et 45°                    | Modèle JSON : éléments tournés avec mise à l'échelle (`rescale`)                            |
| Toute autre pente (rampes 1:2, 1:3, 1:4 de parking, pentes douces) | **Modèle généré par code** : faces arbitraires émises par l'API de rendu de Fabric (maillage personnalisé), pas de JSON |
| Marches et escaliers            | Modèles JSON classiques                                                                     |

- **Collision** : une pente se marche comme un escalier si sa forme de collision est une suite de marches fines ; à
  définir pour que le joueur et les monstres glissent sans à-coups.
- Les pentes générées par code restent **cuites** dans les chunks : aucun coût supplémentaire à l'image.

### 2.3 Objets muraux fins (plinthes, prises, interrupteurs, cadres)

- Le mod a **déjà** un système de **plinthes et petits éléments muraux rendus côté client** (README du dépôt). Les
  nouveaux éléments fins s'y rattachent plutôt que de devenir des blocs à part.
- Sinon, bloc fin orienté (`facing`) collé au mur, sans collision ou avec une collision mince.

### 2.4 Gros objets

- Bureaux, lits, canapés, machines : **multiblocs** (un bloc maître + blocs de remplissage) pour que la collision
  suive l'objet.
- **Jamais de block entity** pour un objet statique : un rendu par block entity coûte à chaque image. Les block
  entities sont réservées à ce qui bouge ou s'anime (ventilateur, porte qui s'ouvre, écran animé).

### 2.5 Textures

- **32×32 par face de bloc**, pour tout. Ne jamais mélanger 16 et 32 dans un même usage : la pixelisation des ombres
  suit la grille des textures (`QUALITE_ET_PRESETS.md` §5.4).
- Chaque texture a ses deux cartes de matériau (§5) : `_n` (normale, occlusion, relief) et `_s` (lissé, reflet,
  porosité, émission).
- Textures animées (néons qui grésillent, écrans) : animation par images de Minecraft (`.mcmeta`), en 32×32 par image.

### 2.6 Monstres et animations

- Modèles d'entités et animations faits dans Blockbench, exportés vers **notre propre format et notre propre
  lecteur d'animations**. Règle du projet : aucune bibliothèque tierce mentionnée dans le mod, donc pas de
  bibliothèque d'animation externe ; le lecteur est un petit composant maison (images clés, interpolation, mélange
  de deux animations).
- Mêmes textures 32×32 et mêmes matériaux que les blocs.

### 2.7 Périmètre

- Tous ces assets **n'existent que dans les Backrooms** et sont **invisibles** hors mode développement (menu créatif,
  `/give`…) : `GAMEPLAY.md` §1.1 et §3.1.

---

## 3. Catalogue d'objets à produire

### 3.1 Éléments d'architecture (communs à plusieurs niveaux)

| Famille                 | Variantes à produire                                                                                               |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------ |
| **Plinthes**            | Droite, angle intérieur, angle extérieur, embout, raccord de porte ; bois peint, bois verni, PVC, carrelage, métal ; propre, éraflée, décollée, moisie |
| **Encadrements de porte** | Simple, double, avec imposte vitrée, coupe-feu, sans porte (passage vide), arraché ; bois, métal peint, aluminium |
| **Portes**              | Bois plein, alvéolaire, vitrée, coupe-feu avec barre, métallique de service, porte d'ascenseur, porte de placard ; ouverte, fermée, entrouverte, condamnée |
| **Demi-murs verticaux** | Épaisseurs 1/4, 1/2, 3/4 de bloc ; muret à hauteur d'appui avec main courante (image 1) ; cloison de bureau ; extrémités, angles, raccords |
| **Plafonds**            | Dalle de faux plafond pleine, perforée, tachée d'humidité, manquante (trou noir), soulevée, cassée pendante ; rail en T ; trappe de visite |
| **Néons et luminaires** | Dalle lumineuse 1×1 et 1×2 encastrée, tube nu, réglette, néon cassé, diffuseur fissuré, lampe d'urgence, bloc « sortie » lumineux, applique, ampoule nue suspendue |
| **Électricité murale**  | Interrupteurs simple, double, va-et-vient (allumé, éteint, cassé, pendant) ; prises simple, double, avec terre, arrachée ; boîte de dérivation ; tableau électrique ; thermostat ; détecteur de fumée ; alarme incendie |
| **Ventilation**         | Grille murale, grille de plafond, bouche ronde, conduit carré et rond (droit, coude, T), grille arrachée, conduit traversant (pour les créatures du niveau 0, `GAMEPLAY.md` §6) |
| **Pentes et rampes**    | 22,5°, 45°, rampes 1:2, 1:3, 1:4 ; béton, carrelage, moquette, métal strié ; avec et sans rebord ; virages de rampe de parking |
| **Escaliers**           | Béton, bois, métal, colimaçon ; rampes et garde-corps assortis                                                     |
| **Colonnes et piliers** | Carrés, ronds, en béton, peints, avec bande de signalisation, avec numéro de place de parking                      |
| **Fenêtres**            | Fausses fenêtres murées, store vénitien, vitre fissurée, hublot ; donnant sur le noir ou sur une lumière             |
| **Signalétique**        | Panneaux sortie, numéros d'étage, flèches, plans d'évacuation, panneaux de danger, numéros de chambre ; textes dans une langue inventée ou illisibles |

### 3.2 Tuyauterie et câblage

| Famille               | Variantes                                                                                                   |
| --------------------- | ----------------------------------------------------------------------------------------------------------- |
| **Tuyaux**            | Diamètres 1/8, 1/4, 1/2 bloc ; droit, coude, T, croix, réduction, bride ; cuivre, acier, PVC, fonte rouillée ; isolé (gaine), qui fuit (goutte animée) |
| **Accessoires**       | Vannes à volant, manomètres, robinets, purgeurs, colliers de fixation, compteurs                              |
| **Fils et câbles**    | Câble seul, faisceau, gaine, chemin de câbles, câble pendant du plafond, dénudé avec étincelle, rallonge au sol |
| **Électrique lourd**  | Armoires électriques, disjoncteurs, transformateurs, compteurs, leviers, interrupteurs à couteau             |

### 3.3 Mobilier, par style

| Style                        | Objets                                                                                                       |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------ |
| **Bureau des années 80-90**  | Bureau droit, bureau d'angle, caisson à tiroirs, chaise de bureau à roulettes, chaise visiteur, armoire de classement, étagère métallique, cloison de box, ordinateur à écran cathodique, clavier, téléphone filaire, imprimante, photocopieuse, fontaine à eau, distributeur automatique, porte-manteau, corbeille, plante en pot (vraie ou en plastique) |
| **Hôtel**                    | Lit simple et double, table de chevet, commode, armoire, fauteuil club, bureau d'écritoire, lampe de chevet, miroir, tableau encadré, chariot de ménage, comptoir d'accueil, cloche, présentoir à clés, lustre |
| **Domestique (appartement, pavillon)** | Canapé, table basse, téléviseur ancien, buffet, table de cuisine et chaises, réfrigérateur, cuisinière, évier, placards, lit d'enfant, baignoire, lavabo, toilettes, machine à laver |
| **Industriel et maintenance** | Établi, étagère de stockage, caisses en bois, palettes, fûts, extincteur, casiers métalliques (cachettes, `GAMEPLAY.md` §9), chariot, échelle, armoire à outils |
| **Parking**                  | Barrière levante, borne de paiement, butée de roue, plots, bandes peintes au sol, voitures (gros modèles, état abandonné) |
| **Piscine**                  | Échelle de bassin, plongeoir, bouée, chaise longue, carrelage de bord, grille d'évacuation, douche              |
| **Hôpital**                  | Lit médicalisé, paravent, chariot de soins, fauteuil roulant, potence, armoire à pharmacie                     |
| **Fête (niveau festif)**     | Ballons, guirlandes, table de buffet, gâteau, chapeaux, cotillons ; en version inquiétante                    |
| **Objets à ramasser**        | Eau, nourriture, lampe torche, piles, notes, clés, cassettes, radio : ce que le gameplay demande (`GAMEPLAY.md` §9, §11) |

Chaque meuble existe en **trois états** au minimum : **neuf**, **usé**, **abîmé** (renversé, cassé, moisi). Les
Backrooms vivent de cette usure.

**Variantes contrôlées plutôt qu'objets différents** : un **modèle de base** par meuble, et des variantes appliquées
par règles : usure en **variante de texture** (calques du §4.3), pièces manquantes, tiroir ouvert, chaise renversée.
Une variante de texture suffit le plus souvent ; une **variante de géométrie** n'est ajoutée que si la silhouette
change vraiment, car chaque géométrie de plus coûte en production et en rendu. Les objets **interactifs ou animés**
(porte, casier, tiroir qu'on ouvre) restent des objets distincts des éléments purement décoratifs.

### 3.4 Monstres, faune et animations

| Famille            | À produire                                                                                                     |
| ------------------ | ------------------------------------------------------------------------------------------------------------ |
| **Gros monstres**  | Modèle, textures et matériaux ; animations : repos, marche, course, fouille, écoute, cri, attaque, fuite, disparition, embuscade (sortie de cachette) ; variantes selon le niveau |
| **Petite faune**   | Rat, chien et autres petites créatures (`GAMEPLAY.md` §8) ; animations simples (marche, course, manger, fuir) ; modèles très légers pour les nuées |
| **Effets du niveau 0** | Silhouettes et ombres dans les ventilations, plaque de plafond qui bouge, main qui dépasse : animations de blocs plutôt que d'entités (`GAMEPLAY.md` §6) |
| **Joueur**         | Poses propres au mod : ramper dans une crevasse, se cacher dans un casier, sous un bureau, retenir sa respiration |

---

## 4. Bible de style (à écrire en premier)

### 4.1 Palette

- Une **palette par niveau** (par exemple 32 à 48 couleurs), et une **palette commune** pour les matériaux partagés
  (métal, bois, plastique). Toute texture est quantifiée sur sa palette : aucun pixel hors palette.
- Le niveau 0 : jaunes et beiges de l'image de référence, gris de moquette, blancs cassés du plafond.

### 4.2 Grille et proportions

- **Grille de 1/32 de bloc** pour tout : dimensions, positions, UV.
- Échelle humaine : un joueur fait 1,8 bloc ; un plafond de bureau à 3 blocs ; une poignée de porte à 1 bloc ; une
  prise à 0,3 bloc du sol. Ces repères sont écrits une fois et réutilisés partout.
- Épaisseurs minimales : rien de plus fin qu'1 texel (1/32), sauf les fils et câbles (1 texel).

### 4.3 Règles de rendu des textures

- Lumière **dans les matériaux**, pas peinte dans l'albédo : pas d'ombres ni de reflets dessinés dans la couleur,
  puisque le moteur les calcule (sauf un très léger dégradé d'usure).
- Usure par **calques réutilisables** (salissure, rouille, humidité, éraflures) appliqués avec les mêmes règles partout.
- **Aspect blocky** : bords nets, pas d'anticrénelage, pas de dégradés lisses ; chaque pixel compte.

### 4.4 Budgets

| Type d'objet                 | Éléments (boîtes) max | Textures             |
| ---------------------------- | --------------------- | -------------------- |
| Élément mural fin            | 1 à 4                 | 1 jeu (32×32)        |
| Bloc d'architecture          | 1 à 8                 | 1 à 2 jeux           |
| Meuble                       | 8 à 32                | 1 à 4 jeux           |
| Gros meuble multibloc        | 32 à 64 au total      | jusqu'à 6 jeux       |
| Monstre                      | Selon la silhouette, os nommés selon la convention du lecteur d'animations | Atlas 64×64 à 128×128, densité 32 px/bloc |

Un « jeu » de textures = albédo + `_n` + `_s`.

**Ces budgets sont des points de départ, pas des valeurs figées.** Deux objets de 30 boîtes n'ont pas le même coût :
comptent aussi le nombre de **faces réellement visibles**, les matériaux, les animations et surtout le **nombre
d'exemplaires** dans une salle. Les budgets définitifs se fixent après mesure sur une **salle chargée** du niveau 0
sur la machine de référence (`MOTEUR_SHADERS.md` §16.1), lors du lot pilote (§11.1).

---

## 5. Bibliothèque de matériaux

Chaque surface est **l'un de ces matériaux**. Ses valeurs de matériau sont fixées une fois, et l'IA ne les invente
jamais. Format : celui des moteurs (`_n` = normale X et Y, occlusion, relief ; `_s` = lissé, reflet ou métal,
porosité ou diffusion sous la surface, émission).

| Matériau                   | Lissé           | Reflet / métal      | Porosité / diffusion | Relief (`_n`)                         | Émission   |
| -------------------------- | --------------- | ------------------- | -------------------- | ------------------------------------- | ---------- |
| Moquette                   | Très bas        | Bas                 | Porosité **haute**   | Fibres : bruit fin, faible profondeur | Non        |
| Papier peint               | Bas             | Bas                 | Porosité moyenne     | Motif légèrement en relief            | Non        |
| Peinture mate              | Bas             | Bas                 | Basse                | Plat                                  | Non        |
| Peinture satinée           | Moyen           | Bas                 | Basse                | Plat                                  | Non        |
| Plastique brillant         | **Haut**        | Bas                 | Nulle                | Plat, arêtes biseautées d'1 texel      | Non        |
| Bois brut                  | Bas             | Bas                 | Porosité haute       | Veines en creux                       | Non        |
| Bois verni                 | Haut            | Bas                 | Basse                | Veines peu marquées                   | Non        |
| Métal peint                | Moyen           | Bas                 | Basse                | Éclats en creux                       | Non        |
| Acier, chrome, cuivre      | Haut à très haut | **Métal prédéfini** | Nulle               | Rayures fines                         | Non        |
| Rouille                    | Très bas        | Bas                 | Porosité haute       | Granuleux                             | Non        |
| Béton                      | Bas             | Bas                 | Porosité haute       | Granuleux, fissures en creux          | Non        |
| Carrelage                  | **Haut**        | Bas                 | Nulle                | **Joints en creux**                   | Non        |
| Céramique sanitaire        | Très haut       | Bas                 | Nulle                | Plat                                  | Non        |
| Verre                      | Très haut       | Moyen               | Nulle                | Plat                                  | Non        |
| Caoutchouc                 | Bas             | Bas                 | Basse                | Stries                                | Non        |
| Diffuseur de néon          | Moyen           | Bas                 | **Diffusion** moyenne | Grille du diffuseur                  | **Oui** (suit `NeonSchedule`) |
| Voyant, LED, écran         | Haut            | Bas                 | Nulle                | Plat                                  | **Oui**    |
| Chair, peau de monstre     | Moyen           | Bas                 | **Diffusion** haute  | Plis, veines                          | Selon le monstre |

**Normales « blocky »** : une normale **par texel**, calculée à partir d'une carte de hauteur 32×32 (biseaux d'un
texel, creux des joints, rainures, rayures), jamais lissée. Résultat : un relief net, aligné sur les pixels, cohérent
avec les ombres pixelisées (`QUALITE_ET_PRESETS.md` §5.4). Les valeurs exactes (0 à 255 par canal) sont fixées dans
un fichier de matériaux versionné, lu par le générateur (§7.3) et par le validateur (§9).

### 5.1 Ce que le moteur lit, selon le palier

Produire des cartes de matériaux ne suffit pas : il faut que le moteur les **lise**, avec la même interprétation des
canaux que le générateur. Or, d'après `QUALITE_ET_PRESETS.md` §5.6, les cartes `_n` et `_s` ne sont lues **qu'à partir
du palier High** ; elles sont **ignorées en Low et en Medium**, et rien n'est encore implémenté (les deux moteurs sont
en phase 0). Conséquences :

- **L'albédo doit tenir seul.** En Low, sur la machine de référence, un joint en creux, une moquette poreuse ou un
  métal brillant n'existent que s'ils sont **lisibles dans la couleur elle-même**. Chaque asset est validé d'abord en
  Low avec l'albédo seul, puis en High avec ses cartes ; le rendu doit rester **cohérent** d'un palier à l'autre.
- **L'émission des néons** passe, en Low et en Medium, par le moteur (calendrier déterministe des néons et réaction
  `neon_face`, `MOTEUR_AUDIO.md` §6.8 et §6.10), pas par le canal d'émission de `_s`. Les deux chemins doivent donner
  **le même néon allumé**, et rester synchrones avec le son.
- **Une seule définition des canaux**, partagée par le générateur, le validateur et le moteur (le même fichier de
  matériaux versionné). Un canal interprété différemment d'un côté et de l'autre est un défaut bloquant.
- **Trois matériaux témoins**, testés de bout en bout dès que le palier High existe : **moquette** (relief discret,
  porosité forte, aucun reflet artificiel), **métal** (reflets et rugosité cohérents sous une lumière qui bouge),
  **néon** (émission synchronisée avec l'éclairage et le son). Avant High, ces trois matériaux sont vérifiés en Low avec
  l'albédo seul.
- **On peut produire les cartes dès maintenant** : elles ne coûtent rien en Low et en Medium, et seront prêtes quand
  High arrivera. Leur gain ne se verra qu'à ce moment-là.

---

## 6. Kits modulaires par niveau (15 premiers niveaux)

Les thèmes ci-dessous sont des **archétypes de travail** inspirés des niveaux les plus connus de l'imaginaire des
Backrooms. Les noms et la numérotation propres aux Backrooms (niveaux, entités, objets) font partie du vocabulaire
du thème et peuvent être utilisés (`GAMEPLAY.md` §15.9). Les **textes** du lore restent **les nôtres** : une partie du lore
communautaire est publiée sous des licences qui imposent des conditions de réutilisation.

| Niveau (travail) | Archétype                          | Kit spécifique (en plus du commun §3.1)                                                                 | Matériaux dominants              |
| ---------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------- | -------------------------------- |
| 0                | Bureaux jaunes infinis (existant)  | Papier peint (variantes, décollé, taché), moquette (humide, tachée), muret à main courante, dalles et néons, plinthes, prises, interrupteurs, grilles de ventilation | Papier peint, moquette, diffuseur |
| 1                | Parking et zones de maintenance, **premier niveau habité** | Piliers numérotés, rampes de parking, bandes au sol, butées, barrières, flaques, caisses, palettes, néons en réglette, armoires électriques (pannes), casiers, voitures abandonnées | Béton, métal peint, caoutchouc |
| 2                | Tunnels de tuyaux                  | Tuyaux de tous diamètres, vannes, manomètres, passerelles grillagées, vapeur, coursives étroites, portes de service | Acier, rouille, cuivre |
| 3                | Station électrique                 | Briques, transformateurs, câbles lourds, leviers, disjoncteurs, eau électrifiée, grilles de sécurité     | Brique, métal, caoutchouc |
| 4                | Bureaux abandonnés                 | Mobilier de bureau complet (§3.3), box, ordinateurs, fontaines, distributeurs, caméras murales, piles de papiers | Bois aggloméré, plastique, moquette |
| 5                | Hôtel inquiétant                   | Couloirs à moquette à motifs, boiseries, portes numérotées, chambres meublées, ascenseur, lustres, tableaux | Bois verni, tissu, laiton |
| 6                | Obscurité                          | Lampes d'urgence, blocs « sortie », bandes phosphorescentes, interrupteurs qui ne marchent pas            | Peinture mate, émissifs faibles |
| 7                | Océan intérieur                    | Pontons, bouées, cordages, échelles, épaves, phare, eau sombre                                          | Bois humide, métal rouillé |
| 8                | Grottes aménagées                  | Étais de mine, portes de décontamination, câbles d'éclairage, échelles, filets                          | Roche, métal, bâches |
| 9                | Pavillons de banlieue la nuit      | Mobilier domestique (§3.3), clôtures, boîtes aux lettres, lampadaires, voitures garées                  | Bois peint, brique, verre |
| 10               | Champs à perte de vue              | Blé, grange, éoliennes, route, piquets, épouvantails                                                    | Végétal, bois brut |
| 11               | Ville sans fin                     | Façades, vitrines, bancs, feux, panneaux, poubelles, abribus                                            | Béton, verre, métal peint |
| 12               | Piscines                           | Carrelage (variantes), bassins, échelles, plongeoirs, grilles d'évacuation, arches, douches             | Carrelage, céramique, eau |
| 13               | Appartements infinis               | Cuisines, salles de bain, chambres, couloirs d'immeuble, boîtes aux lettres, interphones                | Domestique, carrelage |
| 14               | Hôpital                            | Mobilier médical (§3.3), rideaux, portes battantes, chariots, signalétique d'hôpital                     | Vinyle, métal chromé, plastique |

Chaque kit reprend les **éléments communs** (§3.1, §3.2) dans la palette et les matériaux du niveau : une prise du
niveau 4 et une prise du niveau 13 sont le même modèle, avec un habillage différent.

**Ordre** : kit du niveau 0 et kit du niveau 1 d'abord (la tranche verticale, `GAMEPLAY.md` §16) ; les autres ensuite.

---

## 7. Chaîne de production par IA

### 7.1 Les outils disponibles (vérifiés le 28 octobre 2026)

| Outil                                   | Ce qu'il fait                                                                                     | Pour quoi chez nous                          |
| --------------------------------------- | ------------------------------------------------------------------------------------------------- | -------------------------------------------- |
| **Blockbench + extension MCP**          | Ajoute un serveur MCP à Blockbench : Claude pilote Blockbench (modèles, textures, animations). Extension par jasonjgardner, environ 390 étoiles, licence GPL-3.0 (de l'outil, pas des modèles produits). Un ensemble de 15 compétences l'accompagne : modélisation, animation, texturage, **matériaux PBR**, **conversion albédo vers PBR**, textures par images générées, textures animées, particules | **L'outil principal** : meubles, props, monstres, animations, avec Claude qui modélise et toi qui juges |
| Autres extensions MCP pour Blockbench    | Variantes plus jeunes (par exemple enfp-dev-studio, MIT, une seule version publiée)               | Solutions de repli                           |
| **Génération par code** (Claude écrit des générateurs) | Scripts qui produisent modèles JSON, états de blocs et textures à partir de paramètres, de la palette et de la bibliothèque de matériaux ; s'intègre à la génération de données de Fabric | **Toute l'architecture modulaire** : plinthes, prises, interrupteurs, dalles, encadrements, demi-murs, pentes, tuyaux, câbles |
| Générateurs d'images (pour l'albédo)    | Textures à partir d'une description                                                               | Pistes de style seulement : un modèle d'image tient mal une grille exacte de 32×32 ; toujours requantifier sur la palette |

**Recommandation : deux voies, chacune pour ce qu'elle fait le mieux, et toutes deux soumises à la même bible de style et à la même bibliothèque de matériaux.**

1. **Le code** pour tout ce qui est **modulaire et répétitif** : cohérence parfaite, variantes gratuites, et c'est
   le terrain où Claude est le plus fiable. C'est aussi la seule voie pour les pentes à angle libre (§2.2).
2. **Claude + Blockbench (MCP)** pour les **meubles, props complexes, monstres et animations** : tu décris, Claude
   construit dans Blockbench, tu regardes le résultat et tu corriges en mots.

**Écartés** : les générateurs « texte vers modèle » autonomes. Ils produisent vite, mais avec leur propre palette, leurs
propres proportions et sans nos cartes de matériaux : sur des centaines d'objets, le style dérive. Pour ce projet, **la
cohérence et le style passent avant la vitesse** : tout passe par nos règles, nos générateurs et Blockbench piloté par
Claude.

### 7.2 Brancher Claude à Blockbench

1. Installer **Blockbench (version bureau)**.
2. Dans Blockbench : *File > Plugins > Load Plugin from URL*, avec l'adresse de l'extension MCP indiquée sur sa page.
3. Régler le port et l'adresse dans les réglages de Blockbench (exemple de la page : `:3000/bb-mcp`).
4. Dans Claude Code : `claude mcp add blockbench --transport http http://localhost:3000/bb-mcp`.
5. Installer les compétences Blockbench associées (modélisation, texturage, matériaux PBR, animation).
6. Ajouter au `CLAUDE.md` du projet la **bible de style** (§4) et la **bibliothèque de matériaux** (§5) comme règles
   obligatoires de toute production. Ce `CLAUDE.md` vit dans le **dépôt de développement privé**, jamais dans le dépôt
   public (§12.3).

Précaution : l'extension exécute des actions dans Blockbench ; enregistrer son travail avant chaque session.

### 7.3 Générateurs par code

- **Générateur de modèles** : à partir d'une description paramétrique (type, dimensions sur la grille 1/32, variantes,
  orientation), produit les modèles JSON et les états de blocs. Une plinthe = un paramétrage, pas un fichier dessiné.
- **Générateur de textures** : produit l'albédo 32×32 par règles (motif de base, palette du niveau, calques d'usure),
  puis la **carte de hauteur**, puis `_n` (normale par texel, occlusion, relief) et `_s` (valeurs du matériau). Les
  trois cartes sortent **ensemble** et restent cohérentes par construction.
- **Générateur de pentes** : maillages aux angles voulus, avec leur forme de collision en marches fines.
- Tous intégrés à la **génération de données** de Fabric : `./gradlew runDatagen` régénère tout, et le résultat est
  versionné comme le reste.

### 7.4 Monstres et animations

1. **Silhouette** : description écrite (comportement, sens, moyen de le faire fuir, `GAMEPLAY.md` §7) ; ébauches
   cherchées directement avec Claude dans Blockbench (plusieurs silhouettes, puis on choisit), toujours dans la bible
   de style.
2. **Modèle final** dans Blockbench, aux règles de la bible (grille, budget, os nommés selon notre convention).
3. **Textures et matériaux** : compétence albédo vers PBR, puis requantification sur la palette et valeurs de la
   bibliothèque.
4. **Animations** dans Blockbench, par Claude : repos, marche, course, fouille, écoute, cri, attaque, fuite.
   **Les animations font la peur** autant que le modèle : ralentis, arrêts brusques, têtes qui pivotent trop loin.
5. **Export** vers notre format, lu par notre lecteur d'animations (§2.6).
6. **Cri synchronisé** : l'animation de cri s'aligne sur l'enveloppe du son (`MOTEUR_AUDIO.md` §6.6), pour que la
   gueule s'ouvre au rythme du cri.

---

## 8. Ton rôle dans la chaîne

Sans être bon sur Blockbench, tu restes **le directeur artistique** :

- tu écris ou valides la **bible de style** et la **bibliothèque de matériaux** (une fois) ;
- tu **décris** chaque objet ou monstre (ce qu'il évoque, son état, son niveau) ;
- tu **regardes** le résultat en jeu, dans le Level 0 réel, avec les shaders, et tu demandes des corrections en mots ;
  plus précisément, **dans le niveau auquel l'asset est destiné** (le niveau 0 pour son kit, le niveau 1 pour le sien…),
  en Low puis en High (§5.1, §9.1) ;
- tu **tranches** quand deux propositions s'opposent.

L'IA ne juge pas bien ce qui fait peur ou ce qui « sonne » Backrooms : ce jugement reste le tien.

---

## 9. Validateur automatique

Un outil de vérification, lancé en CI comme Checkstyle, qui **refuse** tout asset hors règles :

- textures en **32×32** (ou multiples exacts pour les atlas d'entités), **aucun pixel hors palette** ;
- présence de `_n` et `_s` pour chaque texture, valeurs dans les plages du format, valeurs du matériau déclaré ;
- modèles : coordonnées dans les bornes, dimensions sur la grille 1/32, rotations autorisées, **budget d'éléments** ;
- nommage, états de blocs complets (orientations, variantes), traductions présentes ;
- aucun asset visible hors des Backrooms (onglet créatif, recettes, butins) hors mode développement (`GAMEPLAY.md` §3.1) ;
- **métadonnées nettoyées** : aucun bloc de texte ni information de logiciel dans les PNG et autres fichiers livrés
  (§12.1).

C'est ce qui permet de produire vite par IA sans dériver.

### 9.1 Validation en jeu (le validateur ne suffit pas)

Le validateur vérifie la technique ; il ne sait pas si un meuble est crédible dans une pièce, si deux textures jurent
ensemble ou si un monstre fait peur. Chaque asset passe donc **deux validations** :

| Validation automatique (§9)           | Validation en jeu (toi, §8)                        |
| ------------------------------------- | -------------------------------------------------- |
| Dimensions, UV et palette             | Cohérence du style avec le reste du niveau         |
| Présence et format des matériaux      | Aspect sous l'éclairage réel, en Low puis en High  |
| Budget de géométrie                   | Silhouette et lisibilité                           |
| États et variantes complets           | Crédibilité de l'usure                             |
| Métadonnées nettoyées (§12)           | Ambiance et qualité perçue                         |

**Critères supplémentaires pour les monstres** :

- silhouette **reconnaissable de loin** ;
- animation **lisible dans l'obscurité** ;
- **cri synchronisé** avec l'animation (`MOTEUR_AUDIO.md` §6.6) ;
- **aucun mouvement mécanique involontaire** (raideurs, glissements, boucles visibles) ; les mouvements étranges sont
  voulus, jamais accidentels.

Un asset n'entre dans le jeu qu'après les deux validations.

---

## 10. Propriété et licences

- **Le mod reste propriétaire** : aucun asset existant n'est repris d'un autre mod, pack de ressources ou modèle tiers ;
  tout est produit pour lui.
- Les **outils** ont leurs licences (GPL-3.0 pour l'extension MCP, par exemple) ; elles s'appliquent à l'outil, pas aux
  modèles et textures qu'on produit avec. À revérifier pour chaque outil.
- **Contenu généré par IA** : selon les pays, un contenu entièrement généré par IA peut n'être que faiblement protégé
  par le droit d'auteur ; les retouches et choix humains renforcent cette protection. Je ne suis pas juriste : à faire
  vérifier si le mod devient commercial.
- **Lore et noms** : notre propre lore (textes, descriptions) ; les noms du vocabulaire des Backrooms sont autorisés (§6).

---

## 11. Plan de production

| Étape | Contenu                                                                                     | Livrable                                       |
| ----- | ------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| 1     | Bible de style et bibliothèque de matériaux ; palettes des niveaux 0 et 1                    | Deux fichiers versionnés, lus par les outils   |
| 2     | Branchement de Claude à Blockbench ; règles dans `CLAUDE.md` ; validateur minimal            | Un premier objet produit et validé de bout en bout |
| 3     | Générateurs par code : plinthes, prises, interrupteurs, dalles, néons, encadrements, demi-murs | Kit d'architecture du niveau 0 complet          |
| 4     | Pentes (22,5°, 45°, rampes) et tuyaux, câbles                                               | Rampes de parking et tuyauterie du niveau 1    |
| 5     | Meubles du niveau 0 et du niveau 1 avec Claude dans Blockbench, trois états chacun           | Kits des niveaux 0 et 1                        |
| 6     | **Premier monstre du niveau 1** : modèle, matériaux, animations, cri synchronisé             | Monstre jouable dans la tranche verticale      |
| 7     | Petite faune, effets du niveau 0                                                            | Ambiance du niveau 0 et faune du niveau 1      |
| 8     | Kits des niveaux 2 à 14, au fil du développement des niveaux                                | Catalogue complet                              |

### 11.1 Lot pilote, avant toute production en série

Avant les étapes 3 à 8, la chaîne complète est **prouvée sur un petit lot**, après les étapes 1 et 2 :

1. **Mini-bible** : une palette, quelques matériaux, et **cinq objets représentatifs** : **plinthe**, **prise**,
   **néon**, **bureau**, **casier** (un élément mural fin, un élément électrique, un émissif, un meuble, un objet
   interactif qui sert de cachette).
2. **Production de bout en bout** : génération, export, intégration dans Minecraft, rendu avec les shaders, collisions.
3. **Performance et rendu dans une salle chargée** (pas un objet seul dans un monde vide), en Low sur la machine de
   référence ; fixation des budgets définitifs (§4.4).
4. **Correction des règles, pas des assets** : un défaut qui se répète se corrige dans le générateur, la bible de style
   ou la bibliothèque de matériaux, jamais objet par objet.
5. Seulement ensuite : kit complet du niveau 0, puis du niveau 1 et de son premier monstre (étapes 3 à 6).

Plus lent au départ, ce lot évite de retoucher des centaines d'assets parce qu'un défaut de style ou de rendu aurait
été découvert trop tard. Les **quinze kits** ne sont produits qu'une fois la méthode prouvée.

**Critère** (`GAMEPLAY.md` §16) : chaque lot d'assets doit améliorer quelque chose que le joueur voit en jeu ; on
produit pour un niveau qu'on construit, pas pour un catalogue abstrait.

---

## 12. Aucune trace de l'IA dans le mod

**Règle** : comme pour les projets de référence (`QUALITE_ET_PRESETS.md` §3.0), l'usage de l'IA n'est **ni mentionné,
ni visible, ni déductible** dans le mod. Il n'existe que dans ces documents de conception internes.
**Sauf déclaration exigée** : si une plateforme de publication ou une obligation applicable demande de déclarer
l'usage de l'IA, on le déclare (§12.4). Trois choses distinctes :

| Mesure                                          | Nature                                  |
| ----------------------------------------------- | --------------------------------------- |
| Aucune mention de l'IA dans le jeu              | Choix éditorial                         |
| Nettoyage des métadonnées techniques            | Hygiène de la chaîne de production      |
| Déclaration quand elle est exigée               | Obligation respectée, jamais contournée |

La qualité du résultat doit parler d'elle-même ; aucun risque n'est pris sur les conditions de publication.

### 12.1 Dans le mod livré

- **Aucune mention** de l'IA dans le JAR, l'interface, les crédits, les textes, les commentaires du code ni la
  documentation publiée.
- **Métadonnées nettoyées** : les fichiers PNG peuvent contenir des blocs de texte (logiciel, paramètres de
  génération) ; le validateur (§9) les **supprime** ou refuse le fichier. Même vérification pour les autres formats
  qui portent des métadonnées.
- **Fichiers sources de modélisation** (projets Blockbench) : gardés **hors du dépôt public** ; seuls les modèles et
  textures exportés sont livrés.
- **Noms de fichiers et identifiants** : selon la convention du projet, jamais un nom généré automatiquement.

### 12.2 Indéductible au rendu

C'est surtout une question de **qualité**, et les règles de ce document la servent déjà :

- la **bible de style** et la **bibliothèque de matériaux** donnent une signature unique et constante, loin des
  défauts typiques des contenus générés (incohérences, détails absurdes, style qui varie d'un objet à l'autre) ;
- **chaque asset est relu par toi en jeu** (§8) et corrigé jusqu'à ce qu'il semble fait à la main pour ce monde ;
- les **générateurs par code** produisent des résultats réguliers et intentionnels, comme un artiste avec un style
  défini ;
- **pas de texte ni d'image générés tels quels** : notes, lore, signalétique et textures passent toujours par la
  relecture et la palette.

### 12.3 Dans le dépôt (public)

Le dépôt GitHub du mod est **public**, et il montre **déjà** l'usage de l'IA :

- le fichier **`CLAUDE.md`** et le dossier **`.skills`** sont visibles à la racine du dépôt ;
- l'outil de développement ajoute par défaut une **mention de co-auteur** dans les messages de commit et une ligne
  « généré avec » dans les descriptions de pull request.

À faire :

1. **Désactiver ces mentions** dans les réglages de l'outil de développement (le réglage d'attribution des commits et
   des pull requests), au niveau du projet ou de l'utilisateur.
2. **Sortir `CLAUDE.md`, `.skills` et ces documents de conception du dépôt public** : soit un dépôt **privé** pour le
   développement, avec un dépôt public de publication qui ne contient que le mod ; soit ces fichiers ignorés par Git
   et gardés en local.
3. **L'historique Git garde les anciennes traces** (commits déjà publiés, fichiers déjà poussés). Les effacer demande
   de réécrire l'historique, ce qui est lourd et casse les copies existantes. L'option la plus propre est souvent de
   **passer le dépôt de développement en privé** et de publier depuis un nouveau dépôt propre.

### 12.4 Une précaution

Si le mod est un jour **publié sur une plateforme de mods**, vérifier ses règles : certaines plateformes demandent de
déclarer le contenu généré par IA. Cette règle de discrétion vaut pour le mod lui-même, pas pour une déclaration
qu'une plateforme exigerait.

---

## Sources

- Extension MCP pour Blockbench : https://github.com/jasonjgardner/blockbench-mcp-plugin
- Compétences Blockbench associées : https://skills.sh/jasonjgardner/blockbench-mcp-project
- Autre extension MCP : https://github.com/enfp-dev-studio/blockbench-mcp
- Format de matériaux (spécification des cartes `_n` et `_s`) : https://shaderlabs.org/wiki/LabPBR_Materials
- Modèles de blocs Java : https://minecraft.fandom.com/wiki/Model
- Archétypes de niveaux (référence d'expérience) : https://gameplay.tips/guides/escape-the-backrooms-all-levels-guide.html

---

## Historique des révisions

- **v1** : catalogue d'objets par famille et par niveau (15 kits), contraintes de Minecraft 1.21.1, bible de style,
  bibliothèque de matériaux, chaîne de production par IA (code, Claude et Blockbench, plus un outil écarté depuis), validateur, licences,
  plan de production.
- **v2** : retrait de l'outil de génération « texte vers modèle » autonome, qui ne garantit pas la cohérence du style ;
  la production repose sur deux voies (code, Claude dans Blockbench) ; règle n° 1 « cohérence et style » en tête (§1).
- **v3** : règle « aucune trace de l'IA dans le mod » (§12) : rien de mentionné, de visible ni de déductible ;
  métadonnées nettoyées par le validateur ; dépôt public à assainir (mentions de co-auteur, `CLAUDE.md`, `.skills`,
  historique) ; précaution sur les règles des plateformes.
- **v4** : intégration d'une revue externe : ce que le moteur lit selon le palier, albédo qui tient seul, néons par le
  moteur en Low et Medium, définition unique des canaux, trois matériaux témoins (§5.1) ; variantes contrôlées (§3.3) ;
  budgets à mesurer avant d'être figés (§4.4) ; validation en jeu et critères pour les monstres (§9.1) ; lot pilote de
  cinq objets avant toute production en série (§11.1) ; règle sur l'IA « sauf déclaration exigée » (§12).
- **v5** : vérification de cohérence : `CLAUDE.md` situé dans le dépôt privé (§7.2, cohérent avec §12.3) ; validation en
  jeu dans le niveau auquel l'asset est destiné, en Low puis en High (§8, cohérent avec §5.1 et §9.1) ; contrôle des
  métadonnées ajouté à la liste du validateur (§9, cohérent avec §9.1 et §12.1).
- **v6** : noms du vocabulaire des Backrooms (niveaux, entités, objets, numérotation) autorisés ; les textes du lore
  restent les nôtres (§6, §10).
