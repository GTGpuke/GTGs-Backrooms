# Moteur natif Rust : son, monstres, physique et voix — GTG's Backrooms

> **Document de conception interne** : il nomme des projets de référence (The Last of Us Part II, Rapier, Vosk,
> Whisper, Simple Voice Chat). À garder hors du dépôt public. Aucun code de ces projets n'est recopié dans le nôtre
> (une bibliothèque s'embarque telle quelle, décision 2 du §11), et aucun n'est nommé dans le mod, hors du module
> `compat/` du chat vocal (`QUALITE_ET_PRESETS.md` §3.0).
>
> Idées de Pierre pour des fonctionnalités de `GAMEPLAY.md` (monstres, micro, items physiques, cachettes) et de
> `MOTEUR_AUDIO.md` (acoustique), avec une piste technique : les calculer dans une bibliothèque native écrite en
> Rust. Les parties **« Vision »** reprennent ses idées sans rien retirer ; une formulation qu'une règle ou une
> décision existante remplace est conservée et annotée. Les parties **« Notes »** la rattachent aux documents
> compagnons, à ce qui est déjà livré (`docs/systemes/AUDIO.md`) et aux règles du projet. Documents compagnons :
> `GAMEPLAY.md`, `MOTEUR_AUDIO.md`, `QUALITE_ET_PRESETS.md`, `MOTEUR_SHADERS.md`.
>
> **Statut : le moteur natif en Rust est décidé** (6 novembre 2026, §11) : la performance d'abord, et le rendu
> sonore le plus proche de *The Last of Us Part II*, avec les fonctionnalités de ce document. Il remplace la note de
> `GAMEPLAY.md` §6.3 (« qu'après mesure ») : les mesures du moteur Java deviennent la référence à battre, plus une
> condition. Le détail de chaque fonctionnalité reste une proposition, validée à son tour ; les décisions et ce qui
> reste ouvert sont au §11.

---

## 1. La vision

**Vision**

L'objectif est de faire ressentir dans Minecraft ce que *The Last of Us Part II* fait si bien : **un monde qui sonne
vrai, et des ennemis qui l'écoutent.**

Chaque bruit a une origine physique : un pas sur du gravier, une bouteille lancée, une porte qui grince, la voix du
joueur dans son micro. Ce bruit se propage réellement dans le monde. Il résonne dans une cathédrale de pierre, il est
étouffé par une pièce tapissée de laine, il contourne un mur en passant par une porte ouverte. Les monstres
l'entendent exactement comme le joueur, avec les mêmes imprécisions.

Le projet repose sur quatre moteurs. Ils ne sont pas indépendants : ils partagent les mêmes données et s'alimentent
les uns les autres.

| Moteur | Langage | Rôle |
|---|---|---|
| **Acoustique** | Rust | Propagation, occlusion, réverbération, matériaux |
| **Physique** | Rust (Rapier) | Objets 3D avec vraie physique, impacts sonores |
| **IA des monstres** | Rust (perception, déplacement) + Java (décision) | Chasse, traque, recherche, coordination |
| **Reconnaissance vocale** | Rust (whisper-rs / Vosk) | Comprendre et entendre le joueur |

**Notes**

- **Périmètre : les Backrooms seulement** (`GAMEPLAY.md` §1.1, `MOTEUR_AUDIO.md` §1.1). Le traitement d'un son dépend
  de la dimension où il est émis ; dans l'Overworld, le son, la physique des objets, les monstres et le micro restent
  strictement vanilla. Les exemples de ce document (cathédrale, laine, gravier) illustrent le principe ; dans le mod,
  ils s'appliquent aux matériaux des niveaux (§3.2).
- **Le niveau 0 n'a pas d'entités** (`GAMEPLAY.md` §6) : les monstres, leur ouïe et leur traque vivent dans les
  niveaux habités. Les tests et bancs avec monstres se jouent dans un niveau habité ou dans le niveau de test réservé
  au développement (`MOTEUR_AUDIO.md` §10.3).
- **Où en est chaque moteur dans le projet** :

| Moteur | État dans le projet | Documents |
|---|---|---|
| Acoustique | **Livré en Java** (`core/audio/`) : profil par dimension, EFX, occlusion, réverb par rayons ou d'après la génération, sons à travers les portails, bourdonnement des néons. Le natif en reprend le calcul ; le Java reste le repli | `MOTEUR_AUDIO.md`, `docs/systemes/AUDIO.md` |
| Physique | À faire, dans le natif : le moteur le plus performant au meilleur rendu, au lieu de la physique **légère et maison** de la vision (décision 3 du §11) | `GAMEPLAY.md` §9.4 |
| Monstres | À faire, après un niveau habité et son premier monstre | `GAMEPLAY.md` §7, §7.1, §7.3, §9.6 |
| Voix | Sans chat vocal : détection locale, un **vrai ou faux** au serveur. Avec Simple Voice Chat : niveau de la voix et mots, par son module `compat/` (décision 5 du §11) | `GAMEPLAY.md` §7.2 |

- **Le langage** : Rust, décidé (décision 1 du §11). Ses bibliothèques (Rapier, whisper-rs, Vosk) s'embarquent
  telles quelles, sous licence permissive (décision 2).
- **Le mot « IA »** n'apparaît jamais dans le mod (code, interface, journal) : on y parle de **comportement** et de
  **sens**.

---

## 2. Pourquoi ces moteurs fonctionnent ensemble

**Vision**

Tout le projet s'articule autour de deux idées.

**Une seule copie du monde côté Rust.** Le moteur natif garde un miroir des blocs autour des joueurs. Au chargement,
il reçoit les chunks proches. Ensuite, il ne reçoit plus que les blocs modifiés. Ce miroir unique sert à la fois aux
raycasts acoustiques, aux collisions physiques et au pathfinding des monstres. Les données traversent la frontière
Java/Rust **une seule fois, et sont utilisées trois fois.** C'est ce qui rend l'architecture rentable : le coût de
JNI est payé une fois pour trois moteurs. *(Aligné : une copie **par côté**, voir les notes.)*

**Un seul registre de matériaux.** Chaque bloc a un matériau, avec des propriétés acoustiques (absorption,
transmission) et des propriétés physiques (friction, rebond, densité). Le même matériau détermine comment une pièce
résonne, comment un objet rebondit et quel bruit il fait en tombant.

La boucle de gameplay qui en découle :

```
  Joueur parle / marche / lance un objet
                 │
                 ▼
   ┌──────────────────────────────┐
   │   Événement sonore           │  ← volume, position, matériau
   │   (pas, impact physique,     │
   │    voix détectée au micro)   │
   └──────────────┬───────────────┘
                  │
                  ▼
   ┌──────────────────────────────┐
   │   Moteur acoustique (Rust)   │  ← miroir du monde + matériaux
   │   propagation par zones,     │
   │   portails, occlusion        │
   └───────┬──────────────┬───────┘
           │              │
           ▼              ▼
   Ce que le JOUEUR   Ce que les MONSTRES
   entend (OpenAL)    entendent (perception)
                          │
                          ▼
              ┌────────────────────────┐
              │  IA : mémoire, carte   │
              │  de recherche, décision│
              └───────────┬────────────┘
                          ▼
              ┌────────────────────────┐
              │  Pathfinding (Rust)    │
              │  escalade, sauts...    │
              └────────────────────────┘
```

Concrètement, quand le joueur lance une bouteille pour faire diversion, **la physique** calcule sa trajectoire et
l'impact. **Le registre de matériaux** donne le son du verre sur la pierre et le volume de l'impact. **Le moteur
acoustique** propage ce bruit dans les couloirs. **L'IA** entend le bruit venir de la porte la plus proche et va
voir, pendant que le joueur passe dans son dos. Aucun de ces moteurs ne produirait ce moment seul.

**Notes**

- **Deux côtés, deux copies.** Ce que le joueur entend se calcule **chez le client** (sources OpenAL, réglages EFX
  posés sur le thread du son). Ce que les monstres perçoivent, la physique et les déplacements se calculent **chez le
  serveur**, qui fait autorité (`GAMEPLAY.md` §16). Il faut donc une copie du monde de chaque côté : la règle « une
  traversée, trois usages » vaut côté serveur (perception, physique, déplacements). En solo, le serveur intégré
  tourne dans le même processus mais garde son propre monde : ses deux copies restent séparées.
- **Côté client, le monde est déjà copié deux fois** : pour la lumière cuite (`LightCopy`) et pour l'acoustique
  (`SolidityIndex`, lue par fenêtres `SolidityWindow`). Les partager est une piste en Java, même sans moteur natif, à
  mesurer.
- **Le registre de matériaux est une donnée**, comme les profils de look et d'acoustique : une table chargée avec les
  ressources, jamais une interface. Les deux côtés lisent la même table : un datapack qui l'affine côté serveur
  l'envoie aux clients à la connexion, comme la graine du layout (`LevelZeroLayoutSync`). Elle est distincte de la
  matière visuelle des blocs (`x_s.png`, `x_n.png`), liée à elle par le bloc.
- **Les monstres n'entendent jamais les sons d'hallucination**, joués pour un seul joueur côté client : ils entendent
  la **réaction** du joueur, qui sursaute, court ou crie (`MOTEUR_AUDIO.md` §1.1 point 6, `GAMEPLAY.md` §15.2).
- **Règle d'équité** (`QUALITE_ET_PRESETS.md` §11) : ce qu'un monstre perçoit se calcule côté serveur, à qualité
  fixe, sans dépendre du palier audio ou visuel d'aucun client.
- L'exemple de la bouteille est déjà dans la vision : objets comme appâts (`GAMEPLAY.md` §9.4, §15.3, §15.10).

---

## 3. Le moteur acoustique (cœur du projet)

### 3.1 Principe d'intégration

**Vision**

On ne remplace pas le moteur audio de Minecraft. Minecraft joue ses sons avec OpenAL Soft via LWJGL, qui fournit
l'extension **EFX** : filtres (passe-bas, passe-haut, passe-bande) et réverbération paramétrable par « slots d'effets
auxiliaires ».

La répartition est donc la suivante. **Rust calcule** les paramètres acoustiques de chaque source et de chaque zone.
**Java applique** ces paramètres aux sources OpenAL que Minecraft crée déjà. Résultat : tous les sons du jeu, y
compris ceux des autres mods, profitent automatiquement du système. *(Aligné : seulement les sons émis dans une
dimension qui a un profil audio ; les sons des autres mods y passent aussi, `MOTEUR_AUDIO.md` §9.2. Dans l'Overworld,
rien ne change.)*

**Notes**

- C'est déjà le principe du moteur livré : il ne remplace aucun son, il branche ceux du moteur vanilla sur la réverb
  et le filtre de leur dimension (`docs/systemes/AUDIO.md`).
- Règles en place, que tout calcul natif respecte : tous les appels OpenAL par source se font **sur le thread du
  son** ; seules les sources marquées par le moteur (émises dans les Backrooms) sont touchées ; deux envois
  auxiliaires se négocient **à la création du contexte** (`MOTEUR_AUDIO.md` §6.2) ; coupe-circuit et repli sans EFX
  (§12 du même document).

### 3.2 Le système de matériaux acoustiques

**Vision**

Chaque bloc est associé à un matériau. Un matériau décrit le comportement du son sur trois bandes de fréquences
(graves, médiums, aigus), parce que c'est ce qui fait la différence à l'oreille : une pièce en pierre « brille » dans
les aigus, une pièce en bois sonne plus chaude, la laine avale tout.

Chaque matériau a trois propriétés :

- **Absorption (α)** : la part de l'énergie absorbée quand le son touche la surface. C'est elle qui règle la durée de
  la réverbération.
- **Transmission** : la part du son qui traverse le bloc. C'est elle qui règle l'occlusion.
- **Diffusion** : à quel point le son est éparpillé plutôt que réfléchi net. Elle règle la densité et la « douceur »
  de la réverbération.

Valeurs de départ, à calibrer à l'oreille :

| Matériau | α graves | α médiums | α aigus | Transmission | Caractère |
|---|---|---|---|---|---|
| Pierre, deepslate, béton | 0,02 | 0,03 | 0,05 | très faible | Très réverbérant, aigus brillants |
| Métal (fer, cuivre) | 0,01 | 0,02 | 0,02 | très faible | Réverbérant, résonant |
| Verre | 0,30 | 0,10 | 0,04 | moyenne | Réfléchit les aigus, laisse passer les graves |
| Bois (planches) | 0,15 | 0,10 | 0,08 | faible | Chaleureux, réverbération modérée |
| Terre, herbe, sable | 0,15 | 0,40 | 0,60 | très faible | Mat, absorbe les aigus |
| Laine, tapis | 0,10 | 0,45 | 0,70 | faible | Étouffe fortement |
| Neige | 0,40 | 0,70 | 0,90 | faible | Silence feutré |
| Feuillage | 0,10 | 0,30 | 0,50 | élevée | Diffuse, laisse passer |
| Eau | 0,01 | 0,01 | 0,02 | spéciale | Mode sous-marin à part |

Le registre est **extensible** : les blocs des autres mods reçoivent un matériau par défaut selon leur type de son
Minecraft (`SoundType`). Un fichier de configuration ou un datapack peut ensuite les affiner.

**Notes**

- **Les matériaux des Backrooms passent en premier**, ce sont eux qu'on entend. Valeurs de départ, les nôtres, à
  régler à l'oreille comme celles du tableau :

| Matériau des Backrooms | α graves | α médiums | α aigus | Transmission | Caractère |
|---|---|---|---|---|---|
| Moquette humide (sol du niveau 0) | 0,05 | 0,25 | 0,55 | très faible | Mate, avale les pas |
| Papier peint sur cloison | 0,10 | 0,05 | 0,04 | faible | Réfléchit, garde la réverb des couloirs |
| Dalles de plafond | 0,30 | 0,65 | 0,75 | faible | Absorbe les médiums et les aigus |
| Néon (tube, grille) | 0,02 | 0,03 | 0,04 | moyenne | Petite surface, compte peu |

- **Ce qui existe** : chaque bloc est plein, partiel ou de l'air (`OcclusionTrace`), et le profil donne le poids d'un
  mur et d'un bloc partiel (`occlusion.wall`, `occlusion.partial_block`). Le registre de matériaux en est
  l'extension, par bande.
- **Trois bandes, mais deux réglages par filtre** : le passe-bas d'EFX n'a qu'un gain et un gain des aigus, le
  passe-bande un gain des graves et un gain des aigus. Les trois bandes se ramènent donc à deux réglages pour
  l'occlusion ; elles servent pleinement à la réverb (rapport des aigus de la réverb standard, rapport des graves de
  la réverb EAX).
- En Yarn 1.21.1, le type de son d'un bloc s'appelle `BlockSoundGroup`. Le matériau par défaut des blocs d'autres
  mods ne sert que dans les dimensions profilées.

### 3.3 Détection des pièces et calcul de la réverbération

**Vision**

C'est la fonction qui distingue une cathédrale d'un placard, et une salle en pierre d'un salon en laine.

**Étape 1 : délimiter la pièce.** Depuis la position de l'auditeur, on fait un remplissage par diffusion (*flood
fill*) des blocs d'air, avec une limite (par exemple 32 000 blocs). On obtient le **volume V** de la pièce et la
liste de ses **surfaces** : chaque face de bloc solide exposée à l'air compte pour 1 m², avec son matériau.

**Étape 2 : calculer le temps de réverbération** pour chaque bande de fréquences, avec la formule de Sabine :

```
T60 = 0,161 × V / Σ (surface × α)
```

Pour les pièces très absorbantes, on utilise la formule d'Eyring, plus juste quand l'absorption moyenne est élevée :

```
T60 = 0,161 × V / ( −S × ln(1 − ᾱ) )
```

Quelques exemples de ce que ça donne : une grande salle en pierre donne plusieurs secondes de réverbération,
brillante. La même salle tapissée de laine tombe à une fraction de seconde. Une petite pièce en bois sonne courte et
chaude. Le joueur entend la différence immédiatement, et le résultat est **émergent** : il dépend uniquement de ce
que le joueur a construit.

**Étape 3 : traduire en paramètres EFX.** Le T60 des médiums devient le *decay time*. Le rapport T60 aigus / T60
médiums donne le *HF ratio* (une pièce en terre perd ses aigus vite, une pièce en métal les garde). La taille de la
pièce règle le délai des premières réflexions. La diffusion moyenne des matériaux règle la *diffusion* et la
*density*.

**Étape 4 : l'ouverture vers l'extérieur.** Des rayons lancés depuis l'auditeur mesurent quelle part s'échappe vers
le ciel. Une pièce sans plafond ou une clairière perd presque toute sa réverbération. Si le flood fill dépasse sa
limite, l'endroit est traité comme un grand espace ouvert.

**Les échos.** En extérieur, si de grandes surfaces réfléchissantes sont présentes et lointaines (falaise, mur d'un
canyon, façade de bâtiment), on ajoute des réflexions à long délai. Un cri dans un ravin revient en écho.

**Notes**

- **Le remplissage ne convient pas aux Backrooms** : leurs espaces ne se ferment pas. Le labyrinthe du niveau 0 est
  d'un seul tenant sur des milliers de blocs (98,5 % des blocs ouverts sur une carte de 768 blocs de côté, génération
  actuelle) : un remplissage y atteint toujours sa limite, et tout serait « grand espace ouvert ».
- **Ce que le moteur mesure déjà** : le **libre parcours moyen** d'un son avant un mur, par rayons, ou d'après la
  génération pour le niveau 0 (`LevelZeroRoomShape`, colonnes ouvertes atteignables à 16 blocs), et l'ouverture (part
  des rayons qui sortent sans mur). Il en tire huit réglages de réverb (quatre tailles, deux ouvertures,
  `ReverbPresets`).
- **Sabine et Eyring s'écrivent avec ce libre parcours moyen** l = 4V/S, sans pièce fermée : T60 ≈ 0,04 × l / ᾱ
  (Sabine), T60 ≈ 0,04 × l / −ln(1 − ᾱ) (Eyring), l en blocs (un bloc vaut un mètre). Avec les matériaux, chaque
  rayon relève l'α des faces qu'il touche, bande par bande : on obtient ᾱ par bande, et un T60 continu au lieu des
  huit réglages. C'est l'extension proposée.
- **Le niveau 0 garde l'acoustique d'après sa génération** (`MOTEUR_AUDIO.md` §6.4, hypothèse H9) : sa nouvelle
  génération doit continuer de donner les pièces et leurs ouvertures, et `LevelZeroRoomShape` la suivre.
- **Les échos d'extérieur** ne valent que pour un niveau qui a des extérieurs : une donnée de son profil audio. Le
  niveau 0 n'en a pas.

### 3.4 Les fonctionnalités inspirées de TLOU2

**Vision**

#### Occlusion
Quand un mur plein sépare la source de l'auditeur, le son est atténué et assombri. Un rayon direct de la source à
l'auditeur traverse les blocs, et la **transmission de chaque bloc traversé** est cumulée, bande par bande. On
l'applique avec un filtre passe-bas EFX (*gain* et *gainHF*). Un mur de laine épais coupe presque tout. Une vitre
laisse passer une voix étouffée. Une haie de feuillage change à peine le son.

#### Obstruction et diffraction par les portails
C'est la fonction la plus marquante de TLOU2 : **le son contourne les obstacles.** Quand un ennemi parle dans la
pièce voisine, on l'entend venir **de la porte**, pas à travers le mur.

Le flood fill découpe le monde en **zones acoustiques**, et les ouvertures entre zones deviennent des **portails**
(portes, fenêtres, trous, trappes). On obtient un graphe : zones reliées par des portails. Pour chaque source, on
cherche le meilleur chemin dans ce graphe (Dijkstra, coût = distance + atténuation). La source est alors
repositionnée **virtuellement** sur le dernier portail avant l'auditeur, avec une atténuation qui dépend de l'angle
de contournement : plus le chemin tourne, plus le son perd d'aigus. *(Aligné sur le vocabulaire du projet :
**pièces** et **ouvertures**, voir les notes.)*

On compare ensuite ce chemin indirect avec le son direct à travers le mur, et on garde le plus fort, ou on mélange
les deux.

#### Portails dynamiques
Une porte, une trappe ou un portillon a un état. **Ouvert**, c'est un portail plein. **Fermé**, c'est une paroi avec
la transmission de son matériau (une porte en bois laisse passer un peu de son, une porte en fer presque rien). Quand
le joueur ferme la porte derrière lui, le bruit de la horde devient sourd instantanément, et les monstres l'entendent
moins bien aussi.

#### Réverbération couplée entre pièces
Depuis un petit couloir, un coup de feu tiré dans une grande salle voisine arrive **avec la réverbération de la
grande salle**, à travers la porte. Pour ça, on utilise deux slots auxiliaires EFX : un pour la réverbération de la
zone de l'auditeur, un pour celle de la zone de la source. Le second est envoyé avec un niveau proportionnel à la
taille du portail qui les relie.

#### Transitions fluides
En passant d'une grotte à l'extérieur, la réverbération ne bascule pas d'un coup : tous les paramètres sont
interpolés sur environ 300 ms. C'est indispensable pour éviter les « sauts » audibles et pour que l'espace se sente
continu.

#### Atténuation et absorption de l'air
Plus le son vient de loin, plus il perd ses aigus, comme dans la réalité. Une explosion lointaine devient un
grondement sourd. On utilise pour ça le facteur d'absorption de l'air d'EFX, combiné à la courbe d'atténuation de
distance.

#### Bruits de pas selon le matériau et l'allure
Le son et surtout le **volume** d'un pas dépendent du matériau du sol et de l'allure : courir sur du gravier est très
bruyant, s'accroupir sur de la laine est presque silencieux. Le même volume est envoyé à la perception des monstres,
et ça devient du **gameplay d'infiltration** : le joueur apprend à choisir où il marche.

#### Masquage par l'ambiance
La pluie, l'orage, une cascade ou une machine bruyante **masquent** les autres sons. Côté joueur, le mix reste
lisible. Côté monstres, leur seuil d'écoute augmente. Se déplacer pendant l'orage devient une vraie stratégie.

#### Mode sous-marin
Sous l'eau, on applique un filtre passe-bas global, une réverbération courte et dense, et une transmission très
réduite des sons extérieurs.

#### Mix dynamique (optionnel)
Les sons sont priorisés, et les sons secondaires sont réduits (*ducking*) lors d'une explosion proche, avec
éventuellement un sifflement d'oreille temporaire. Ça donne de l'impact sans saturer le mix.

**Notes**

- **Vocabulaire** : dans ce projet, un **portail** est un portail du mod (`docs/systemes/PORTAILS.md`), et une
  **zone** est déjà la zone de lumière cuite (`LightZone`). Pour l'acoustique, on dit **pièce** (un espace) et
  **ouverture** (porte, fenêtre, trou, trappe). Les portails du mod sont des arêtes de plus dans le graphe des pièces
  : un son passe par le cadre et se rejoue au cadre, avec l'acoustique de sa dimension d'émission (`MOTEUR_AUDIO.md`
  §7, `FrameSoundPlayer`).
- **État de chaque fonctionnalité** :

| Fonctionnalité | État | Où |
|---|---|---|
| Occlusion | **Livrée** : un rayon droit, 64 pas au plus, blocs pleins ou partiels ; gain et aigus d'un passe-bas, sous le plafond du profil. La transmission par matériau en est l'extension | `AUDIO.md`, Occlusion |
| Obstruction et diffraction | **En partie** : en qualité complète, quatre détours à deux blocs de la source, et la source perçue au point de passage ; le profil du niveau 0 laisse cette part à zéro jusqu'à l'écoute de l'auteur. Le graphe des pièces et des ouvertures est l'extension ; dans le niveau 0, la génération le donne sans un rayon (cellules et leurs connexions) | `AUDIO.md`, Occlusion |
| Ouvertures dynamiques | À faire. Le niveau 0 n'a pas de porte (porte abandonnée le 18 octobre 2026) : pour les niveaux qui en ont | — |
| Réverbération couplée | À faire. Elle utilise les deux envois prévus (« proche » et « de la salle », `MOTEUR_AUDIO.md` §6.2) ; les slots restent des réglages partagés par toutes les sources, jamais un slot par pièce. Aujourd'hui, la réverb suit la pièce **de la source** | `MOTEUR_AUDIO.md` §6.2 |
| Transitions fluides | Prévu comme repli de l'hypothèse H5 (lissage des réglages EFX sur quelques centaines de millisecondes), sur le thread du son | `MOTEUR_AUDIO.md` §16 |
| Absorption de l'air | **Livrée** dans le profil (`air_absorption`), palier complet | `AUDIO.md` |
| Pas selon le matériau et l'allure | À faire. C'est le « niveau de bruit du joueur » de la vision ; l'allure (course, accroupi) se lit côté serveur, jamais d'après le client | `GAMEPLAY.md` §15.3 |
| Masquage par l'ambiance | À faire. Dans les Backrooms, la source de masquage permanente est le bourdonnement des néons, que le serveur sait calculer à l'identique (`NeonSchedule`, déterministe) ; pluie et orage seulement dans un niveau qui en a | `MOTEUR_AUDIO.md` §6.8 |
| Mode sous-marin | **Livré** en partie : sous l'eau, le milieu du profil s'ajoute à l'occlusion | `AUDIO.md` |
| Mix dynamique | À faire, avec les presets **Streamer** et **Nuit** (compression de dynamique) ; le sifflement d'oreille est coupé par les presets **Confort** et **Accessibilité** | `QUALITE_ET_PRESETS.md` §7, §8 |

### 3.5 Performances

**Vision**

Les raycasts et les flood fill tournent dans des threads Rust (via `rayon`), **jamais sur le thread de rendu ni sur
le tick du serveur**. Les zones acoustiques sont **mises en cache** et recalculées seulement quand un bloc change à
l'intérieur ou sur leur bord. La propagation de chaque source est mise à jour environ 10 fois par seconde, et
l'interpolation masque la différence. On applique aussi un niveau de détail : les sources lointaines ou peu
importantes reçoivent moins de rayons. Enfin, le nombre de slots EFX disponibles dépend de la configuration d'OpenAL
Soft, et il faut le vérifier au démarrage pour s'adapter.

**Notes**

- **Budgets en place** (`MOTEUR_AUDIO.md` §10.1 et §17.3, objectifs à valider) : thread principal sous 0,5 ms par
  tick en moyenne, et au plus 0,2 ms au-dessus du vanilla dans le niveau 0 sans monstre ; une évaluation par rayons
  sous 1 ms en qualité légère ; l'acoustique d'après la génération sous 0,01 ms par cellule en cache. Toute version
  native se juge contre ces chiffres, sur la machine de référence.
- **Ce qui tourne déjà ainsi** : un thread de calcul à lui (`GTG's Backrooms acoustics`), files bornées, objets
  recyclés, résultat gardé par cellule de quatre blocs (`AcousticCache`), recalculé toutes les dix ticks tant que le
  son joue (deux fois par seconde, pas dix : à mesurer avant de changer), et priorité des sons (`MOTEUR_AUDIO.md`
  §17.2).
- **La machine cible** est un PC à GPU intégré, aux cœurs partagés avec la construction des chunks : un pool de
  threads de la taille du processeur (comme celui de `rayon` par défaut) lui coûte des à-coups. Nombre de threads
  borné.
- **Paliers** (`QUALITE_ET_PRESETS.md` §8, §10) : chaque fonctionnalité nouvelle dit à quel palier elle s'ouvre (Low,
  Medium, High, Ultra) ; ce qu'un palier coupe n'est jamais l'identité sonore des Backrooms (`MOTEUR_AUDIO.md`
  §17.1).

---

## 4. Le moteur physique des objets

**Vision**

Les objets 3D (bouteilles, briques, caisses, débris) sont simulés avec **Rapier**, un moteur physique Rust mature et
rapide. *(Remplace la physique légère et maison de `GAMEPLAY.md` §9.4 : décision 3 du §11. Rapier est le candidat
naturel en Rust ; le moteur se choisit au banc, voir les notes.)*

**Collisions avec le monde.** Elles s'appuient sur le miroir du monde partagé : seuls les blocs proches des objets
actifs sont transformés en collisionneurs. **Les matériaux** fournissent la friction, le rebond et la densité : une
bouteille glisse sur la glace et rebondit à peine sur la terre.

**Multijoueur.** La simulation tourne **côté serveur**, qui fait autorité. Les clients reçoivent positions et
rotations, puis interpolent pour un mouvement fluide. Les objets immobiles sont mis en sommeil pour ne rien coûter.
Le **rendu** reste côté Java : Rust donne la transformation de chaque objet, Java le dessine.

**Le lien avec le son.** Chaque contact génère un **événement d'impact** qui contient la force de l'impact, le
matériau de l'objet et celui de la surface. On en déduit le son joué (verre sur pierre, bois sur bois) et son
**volume**. Cet événement entre ensuite dans le moteur acoustique comme n'importe quel bruit : le joueur l'entend
résonner dans la pièce, et **les monstres l'entendent aussi**. Lancer un objet pour attirer un ennemi ailleurs
devient une mécanique naturelle, sans code spécifique.

**Notes**

- **Déjà dans la vision** (`GAMEPLAY.md` §9.4) : items en 3D au sol, même jetés, ramassés par clic droit, sans
  ramassage automatique dans les Backrooms ; calcul côté serveur, interpolé côté client ; nombre d'objets actifs
  plafonné par zone ; un objet au repos ne coûte rien ; chaque choc fait un bruit que les monstres entendent
  (§15.10).
- **Les portails du mod** : un objet qui traverse un cadre change de monde avec lui (`TeleportExecutor`, qui emmène
  déjà objets et projectiles). Les collisions suivent les règles du cadre : collision à travers le cadre et blocs
  cachés derrière le plan pour ce qui s'engage (`PortalCollision`). Vanilla y passe par `BlockCollisionSpliterator` ;
  une physique hors de Minecraft, native ou non, doit les reproduire, sinon un objet rebondit sur un mur que le
  joueur traverse.
- **Les matériaux** sont ceux du §3.2 : un seul registre pour le son et la physique.
- **Le choix du moteur** (décision 3 : le plus performant, au meilleur rendu) se fait au banc, sur nos cas : quelques
  dizaines d'objets par zone, collisions contre des blocs pleins et partiels, objets au repos la plupart du temps,
  machine cible à GPU intégré. Rapier (Rust) est le candidat naturel ; Jolt Physics (C++) est l'autre candidat
  sérieux, conçu pour de nombreux corps sur plusieurs cœurs, au prix d'une chaîne de compilation C++ de plus pour
  les cinq plateformes. Les deux se mesurent sur le même banc avant de choisir.

---

## 5. L'IA des monstres : chasse, traque et recherche

### 5.1 Répartition Rust / Java

**Vision**

La **décision** (que faire maintenant ?) est en Java, au plus près des entités et du monde de Minecraft. Elle est peu
coûteuse et facile à itérer. La **perception** (qu'est-ce que je vois, qu'est-ce que j'entends ?) et le
**déplacement** (comment j'y vais ?) sont en Rust, parce que ce sont des calculs lourds qui s'appuient sur le miroir
du monde et le moteur acoustique.

**Notes**

- **Tout se passe côté serveur** : décision, perception et déplacement (`GAMEPLAY.md` §16). C'est l'acoustique **du
  serveur** qui donne l'ouïe : celle d'après la génération est déterministe et donne au serveur le même résultat
  qu'aux clients ; ailleurs, des rayons sur la copie du serveur (`GAMEPLAY.md` §7, notes).
- **La vision du projet** : une **machine à états pilotée par les sens**, avec un profil de sens et de comportements
  par type de monstre (`GAMEPLAY.md` §7). Les états du §5.3 en sont la machine, le système d'utilité du §5.7 choisit
  l'action dans un état.
- **Ce qui manque ici et que la vision demande** : les monstres **entendent, voient et sentent** (odorat : une piste
  d'odeur qui s'estompe), suivent les **traces** de sang (§9.5), ne sont **pas tuables** mais ont **chacun un moyen
  de les faire fuir** (§7), ont des **besoins** hors chasse (faim, territoire, repos) et une part de **hasard
  pondéré** (§7.3), et sont **rares**, avec un budget de monstres par zone et par niveau (§7.3).

### 5.2 Perception

**Vision**

**L'ouïe** est fournie directement par le moteur acoustique. Pour chaque événement sonore, on calcule le niveau qui
arrive à chaque monstre, avec occlusion, portails et masquage. Le monstre entend si ce niveau dépasse son seuil.
Point essentiel : **la position qu'il perçoit est celle du portail par où le son arrive**, pas la position exacte de
la source. Comme un humain, il sait d'où vient le bruit, pas exactement où il est. Il se trompe, et le joueur peut
exploiter ces erreurs.

**La vue** est calculée par des raycasts dans un cône de vision. La détection dépend de la distance, du niveau de
lumière de Minecraft à la position du joueur, de sa posture (accroupi ou non) et du feuillage qu'on traverse. Elle
n'est pas instantanée : une jauge de suspicion se remplit plus ou moins vite selon ces facteurs.

**Notes**

- **« Portail » est ici une ouverture** (§3.4). Un son qui arrive par un portail du mod est perçu au cadre, comme le
  joueur l'entend (`FrameSoundPlayer`).
- **La lumière que lit le serveur** est celle du moteur de lumière vanilla : celle des blocs (néons compris) et les
  sources virtuelles des portails, plus la **lampe torche** pointée, que le serveur calcule (`GAMEPLAY.md` §7.1).
  Jamais la lumière cuite des shaders : elle n'existe que chez le client et dépend de son palier (règle d'équité).
- **Ce que l'ouïe entend aussi** : le **vrai ou faux** du micro, « le joueur parle en ce moment » (`GAMEPLAY.md`
  §7.2), et le bruit du joueur (§15.3). Un monstre qui **voit** un joueur se cacher, ou qui **entend parler** dans
  une cachette, l'attrape (§9.6).
- **Les Smilers** (§7.1) ont leur propre perception de la lumière : lumière pointée sur eux, agacement qui monte à
  chaque exposition, attaque dans le noir.
- **Feuillage** : dans les Backrooms, ce sont les cloisons, l'obscurité et les recoins des cachettes.

### 5.3 États de vigilance

**Vision**

Le monstre passe par cinq états. **Calme** : il patrouille ou erre, à l'écoute. **Suspicieux** : il a perçu un indice
(bruit, silhouette), il s'arrête, tourne la tête, s'approche prudemment. **Recherche** : il a perdu une cible ou suit
une piste, il fouille méthodiquement. **Traque** : il sait à peu près où est le joueur et s'approche en restant
discret, voire en le contournant. **Chasse** : contact établi, il poursuit, attaque et coordonne avec les autres.

Les transitions sont **progressives et réversibles** : un monstre peut abandonner une recherche infructueuse et
revenir au calme, mais en restant plus vigilant qu'avant pendant un moment.

**Notes**

- **Correspondance avec les comportements de la vision** (`GAMEPLAY.md` §7) : **lurk** (rôder) dans Calme et Traque,
  **fouille** dans Recherche, **embuscade** dans Traque, **chasse** et **course** dans Chasse.
- **À ajouter** : la **fuite**, quand le joueur trouve le moyen propre à chaque monstre (§7) ; les états propres à un
  type, comme la **rage** et l'**orbite autour d'une lumière** des Smilers (§7.1) ; les **besoins** qui occupent un
  monstre hors de la chasse (§7.3).

### 5.4 Mémoire et carte de recherche

**Vision**

Chaque monstre garde la **dernière position connue** du joueur, avec le moment où elle a été notée et sa fiabilité
(vu ou seulement entendu).

Quand il perd la trace, il utilise une **carte de probabilité de présence**. Au départ, la probabilité est concentrée
sur la dernière position connue. Avec le temps, elle **s'étend** le long des chemins praticables (le graphe de
navigation), puisque le joueur a pu aller dans toutes ces directions. Chaque zone que le monstre inspecte du regard
est **nettoyée**, sa probabilité retombe. Le monstre se dirige vers les zones les plus probables, en priorité les
**cachettes plausibles** : recoins, derrière les obstacles, pièces fermées.

Résultat : le monstre fouille comme quelqu'un qui réfléchit, pas au hasard. Et un nouveau bruit **réinjecte** de la
probabilité là où il a été entendu.

**Notes**

- **C'est la règle de la vision** : un monstre ne connaît jamais la position du joueur sans l'avoir perçue
  (`GAMEPLAY.md` §7.3).
- **Les cachettes sont des objets du jeu** (§9.6) : chacune a sa probabilité d'être trouvée, ajustée par type de
  monstre, et une cachette où le monstre a **vu** entrer le joueur est « grillée » pour lui. La carte de recherche
  les lit en priorité.
- **D'autres sources réinjectent de la probabilité** : les **traces** de sang et l'**odeur** (§9.5, §9.6), et
  l'**apprentissage** propre à chaque monstre (cachettes utilisées, directions de fuite, habitudes du joueur), qui
  s'efface avec le temps pour laisser une chance.

### 5.5 Coordination de groupe

**Vision**

Les monstres d'un même groupe **partagent** leur carte de recherche et **se répartissent** les zones à fouiller. En
traque et en chasse, ils prennent des rôles : l'un met la pression de face pendant qu'un autre **contourne** par un
autre chemin du graphe, ou qu'un troisième **attend en embuscade** sur une sortie probable.

Ils se **signalent** les informations par des cris. Ces cris passent eux-mêmes par le moteur acoustique : le joueur
les entend et peut comprendre ce qui se prépare. C'est cette lisibilité qui rend l'IA de TLOU2 si crédible.

**Notes**

- **Les monstres sont rares**, parfois plusieurs, très rarement en même temps (`GAMEPLAY.md` §7.3) : le groupe vaut
  surtout pour une espèce qui vit en groupe, comme les Smilers qui **orbitent comme des loups** autour d'une lumière
  (§7.1).
- **Les cris** sont des sons du monde : acoustique de leur dimension, réactions visuelles par les `Cue` (néons qui
  vacillent), tremblement plafonné et coupé par Confort, jamais de tremblement dans l'Overworld, même pour un cri
  entendu à travers un portail (`MOTEUR_AUDIO.md` §1.1, §11).

### 5.6 Déplacements complexes

**Vision**

Le pathfinding de Minecraft est trop limité pour ça. Le moteur Rust fournit un **A\* hiérarchique**. Un graphe
grossier (par sections de chunks ou par zones) calcule les longs trajets rapidement, puis un chemin fin est calculé
bloc par bloc à proximité.

Chaque type de monstre a des **capacités de déplacement**, chacune avec un coût : marcher, sauter un trou, escalader
un mur, grimper une échelle, se faufiler dans un passage étroit, casser un bloc fragile. Un monstre agile prendra le
raccourci par le toit, un monstre lourd défoncera la porte. Les chemins sont calculés en arrière-plan et mis en
cache, et seules les portions touchées par un changement de blocs sont recalculées. *(Casser un bloc : les blocs
sont incassables pour les joueurs, mais les monstres peuvent agir sur eux ; la manière reste à concevoir : décision
4 du §11.)*

**Notes**

- **Les portails du mod sont des arêtes du graphe** : un monstre poursuit à travers un cadre, comme le font déjà les
  mobs vanilla (regard et poursuite à travers le cadre, `PortalPursuer`).
- **Ce qu'un monstre casse aujourd'hui dans la vision** : les sources de lumière, pour les Smilers (un néon cassé
  s'éteint pour tous : lumière et bourdonnement, `GAMEPLAY.md` §7.1).
- **Dans le niveau 0** et les niveaux générés, le graphe grossier peut venir de la génération (cellules et
  connexions), comme l'acoustique.
- **Un bloc qu'un monstre change se recalcule partout où il compte** : lumière cuite et acoustique des clients,
  copies du serveur (perception, déplacements, physique), graphe des pièces. Peu de blocs modifiables, donc peu de
  recalculs (`GAMEPLAY.md` §9.2) : un monstre qui change beaucoup de blocs coûterait d'autant.

### 5.7 Décision

**Vision**

Un **système d'utilité** (*utility AI*) évalue en continu les actions possibles : fouiller ici, contourner, attendre
en embuscade, foncer, appeler le groupe, fuir. Chaque action reçoit un score selon la situation (état de vigilance,
carte de recherche, distance, santé, position des alliés), et le monstre prend la meilleure. On obtient des
comportements variés et adaptatifs, sans arbre de règles ingérable.

**Notes**

- **Imprévisibilité sans tricher** (`GAMEPLAY.md` §7.3) : prendre parfois la deuxième meilleure action, par un hasard
  pondéré, plutôt que toujours la meilleure ; des vraies IA « pour empêcher les joueurs de comprendre comment elles
  marchent ».
- **« Santé »** : les monstres ne sont pas tuables (§7) ; le score lit plutôt l'agacement (Smilers), les besoins et
  la présence du moyen qui fait fuir.

---

## 6. La reconnaissance vocale

**Vision**

Le micro du joueur est capté avec `cpal`, et le traitement est **entièrement local**, sans envoi de voix sur un
serveur externe. Il a deux usages.

**Le volume de la voix comme bruit dans le monde.** Parler, c'est faire du bruit. Le niveau sonore capté au micro est
converti en événement sonore à la position du joueur, puis propagé par le moteur acoustique. Chuchoter dans un
placard en laine est sans danger. Crier dans un hall de pierre attire tout le monde. Ça marche très bien avec un mod
de chat vocal comme *Simple Voice Chat*. *(Avec Simple Voice Chat, le niveau se tire du flux vocal que le serveur
reçoit déjà, dans le module `compat/` du chat vocal : chuchoter et crier s'y distinguent. Sans lui, la décision de
`GAMEPLAY.md` §7.2 reste : le niveau ne quitte pas le client, seul un **vrai ou faux** part au serveur. Décision 5 du
§11.)*

**La compréhension des mots.** Pour reconnaître des mots-clés ou des commandes, on utilise **Vosk** ou un petit
modèle **Whisper** (via `whisper-rs`). Ça permet des commandes vocales, des réactions de PNJ ou de monstres à des
mots précis, ou des mécaniques spéciales (un monstre attiré par son nom). Il vaut mieux privilégier de petits modèles
pour garder une latence faible sur CPU. *(Surtout pour l'intégration avec Simple Voice Chat : décision 5 du §11.)*

**Notes**

- **Sans chat vocal, la décision en place** (`GAMEPLAY.md` §7.2, §14 point 14) : micro activé, tout traité en local ;
  le serveur ne
  reçoit qu'un vrai ou faux, « le joueur parle en ce moment », envoyé quand il change ; sensibilité réglable dans le
  menu ; un tooltip à l'arrivée dans le niveau 1 et une mention claire dans les réglages ; micro écouté **seulement
  dans les Backrooms** ; un joueur sans micro garde son avantage (point 6 tranché). Le joueur s'en rend compte par
  l'**écho** de sa voix, l'acoustique du lieu appliquée localement.
- **Avec Simple Voice Chat** : vol de voix par certains monstres et écho entre joueurs (§7.2), plus le niveau de la
  voix et les mots reconnus (décision 5 du §11). Son code vit dans un module `compat/` à lui, comme Iris : ce module
  lit l'API du chat vocal et passe le son décodé au natif, qui ne nomme aucun mod. Le niveau se calcule côté
  serveur, qui a déjà le flux et fait foi. Pour les mots, reste à choisir où tourne la reconnaissance : au serveur,
  qui fait foi mais paie pour chaque joueur, ou chez le client, qui ne paie que pour lui mais dont le serveur doit
  croire le résultat.
- **La capture sans bibliothèque native** : l'OpenAL que Minecraft embarque par LWJGL sait capturer le micro
  (extension de capture d'OpenAL 1.1, `alcCaptureOpenDevice`). La détection vocale en vrai ou faux tient donc en
  Java. Hypothèse à vérifier sur les trois systèmes, permissions du micro de macOS comprises.
- **Les modèles de reconnaissance** pèsent de dizaines à centaines de Mio : à loger hors du JAR (§9). Ce sont des
  ressources de tiers, embarquées telles quelles (décision 2 du §11) ; leur licence se vérifie modèle par modèle.
- **Une piste compatible avec la décision** : des mots reconnus **localement** qui déclenchent des **hallucinations**
  du joueur (sons et images joués pour lui seul, `GAMEPLAY.md` §5) : rien ne part au serveur.

---

## 7. Architecture technique

### 7.1 Organisation du code Rust

**Vision**

Le projet Rust est un *workspace* découpé en plusieurs crates :

| Crate | Contenu |
|---|---|
| `world` | Miroir du monde, registre de matériaux |
| `acoustics` | Zones, portails, propagation, réverbération |
| `physics` | Intégration de Rapier, événements d'impact |
| `nav` | Graphe de navigation, A\* hiérarchique, capacités de déplacement |
| `perception` | Ouïe (via `acoustics`) et vue des monstres |
| `voice` | Capture micro, détection de volume, reconnaissance |
| `bridge` | Interface JNI, seul point de contact avec Java |

**Notes**

- **Côté Java, rien ne change d'architecture** : le calcul natif remplace l'intérieur des classes qui calculent
  (`AcousticEvaluator`, `OcclusionTrace`, puis perception et déplacements côté serveur), derrière les mêmes points
  d'accès. Profils, routage par dimension d'émission, application EFX sur le thread du son et relais des portails
  restent en Java (`MOTEUR_AUDIO.md` §5).
- **Une bibliothèque, deux usages** : le client charge `acoustics` et `voice`, le serveur `perception`, `nav`,
  `physics` et `acoustics` ; en solo, les deux dans le même processus, chacun avec sa copie (§2).

### 7.2 Communication Java ↔ Rust

**Vision**

On utilise **JNI** via le crate `jni`, puisque Minecraft tourne sur Java 21 et que l'API FFM n'y est pas encore
stable. Les appels sont **regroupés par lots** : à chaque tick, Java envoie une liste d'événements (blocs modifiés,
sons émis, positions des entités) et récupère une liste de résultats (paramètres audio, chemins, perceptions,
transformations physiques). Le travail lourd est **asynchrone** : Rust calcule dans ses threads, et Java récupère les
résultats prêts au tick suivant, sans jamais bloquer le jeu.

**Notes**

- Exact : l'API FFM n'est finale qu'à partir de Java 22, et Minecraft 1.21.1 tourne sur Java 21.
- **Aucune allocation par tick ni par image** (règle du projet) : les lots passent par des tampons directs
  réutilisés, jamais par des objets Java créés à chaque appel.

### 7.3 Robustesse

**Vision**

Chaque fonction exposée à Java est protégée par `std::panic::catch_unwind` : une erreur Rust devient un code d'erreur
et un message dans les logs Java, au lieu de faire planter le jeu. En cas d'échec du chargement natif, le mod se
désactive proprement et le jeu reste jouable.

**Notes**

- **`catch_unwind` n'attrape que les paniques** : une erreur de mémoire, un dépassement de pile ou un `abort` ferment
  le jeu quand même. D'où trois règles : garder le moteur Java comme **repli** (même résultat, moins rapide), une
  option pour couper le natif, et le **coupe-circuit** des moteurs (`MOTEUR_AUDIO.md` §12).
- **« Le mod se désactive »** : seul le calcul natif se coupe ; le mod continue sur son moteur Java.
- **Tests** : la logique Rust se teste par ses propres tests, à couverture complète comme le Java ; la colle JNI
  reste minimale, isolée et nommée dans `docs/dev/TESTS.md`, comme la colle OpenGL et OpenAL. Si le repli Java est
  gardé, Java et Rust se tiennent d'accord par un fichier de vecteurs communs, comme `NeonSchedule` et son jumeau
  GLSL.

### 7.4 Distribution dans le jar

**Vision**

La bibliothèque Rust est compilée en `cdylib` pour Windows x64, Linux x64, Linux arm64, macOS x64 et macOS arm64,
grâce à une CI GitHub Actions (avec `cargo-zigbuild` ou une machine par OS). Les binaires sont rangés dans les
ressources du jar. Au démarrage, le mod extrait celui qui correspond à la plateforme et le charge avec `System.load`.
Avant de publier, il faut vérifier les règles de CurseForge et Modrinth concernant les binaires natifs : publier le
code source facilite la validation.

**Notes**

- **Le contrôle du JAR** (`verifyJarContents`) refuse aujourd'hui toute dépendance embarquée. Avec le natif, il
  nomme un par un les binaires et leurs notices admis (décision 2 du §11), et refuse toujours tout autre mod.
- **Licences** : celle du mod reste propriétaire. Une bibliothèque liée ou embarquée a donc une licence permissive
  (MIT, Apache 2.0, BSD, zlib), jamais copyleft (GPL, ni LGPL liée statiquement, qui obligeraient à ouvrir le code),
  et sa notice va dans le JAR. Chacune se vérifie avant d'entrer, avec ses propres dépendances.
- **Le dépôt GitHub est déjà public** ; la licence reste propriétaire.
- **Points pratiques** : extraire dans un dossier propre à la version, vérifié par une empreinte ; sur macOS,
  signature et quarantaine d'une bibliothèque extraite ; sur Windows, antivirus qui bloquent une DLL écrite dans un
  dossier temporaire. Linux se vérifie par la CI, sans Docker.

---

## 8. Feuille de route

**Vision**

**Phase 1 : fondations.** Pont JNI, chargement natif multiplateforme, miroir du monde avec mises à jour
incrémentales, registre de matériaux. *C'est la base de tout le reste.*

**Phase 2 : acoustique.** Occlusion par matériaux, détection des pièces et réverbération (Sabine/Eyring), puis zones,
portails et diffraction, réverbération couplée, transitions. *C'est le premier résultat spectaculaire, audible par
tous les joueurs.*

**Phase 3 : perception et IA.** Ouïe des monstres branchée sur l'acoustique, vue, états de vigilance, mémoire, carte
de recherche, pathfinding hiérarchique, puis coordination de groupe.

**Phase 4 : physique.** Objets Rapier, synchronisation multijoueur, événements d'impact reliés au son et à la
perception. *C'est la phase où la diversion par objet lancé fonctionne.*

**Phase 5 : voix.** Volume du micro comme bruit dans le monde, puis reconnaissance de mots-clés.

Chaque phase produit un résultat jouable et s'appuie directement sur la précédente.

**Notes**

- **Une phase 0 de référence** : mesurer le moteur Java sur les bancs de `MOTEUR_AUDIO.md` (§10.3, §17.3), sur la
  machine de référence. Ce n'est plus une condition (décision 1 du §11) : c'est la référence que le natif doit
  battre, et le juge de chaque étape.
- **L'acoustique passe d'abord au natif à résultat égal** : le natif refait ce que fait le moteur livré, tenu
  d'accord avec lui par des vecteurs communs (§7.3), puis l'étend : registre de matériaux, T60 par le libre parcours
  moyen, graphe des pièces et des ouvertures (donné par la génération dans le niveau 0), réverb couplée, pas et
  masquage.
- **Perception et monstres** demandent un niveau habité et son premier monstre (`GAMEPLAY.md` §14 point 12), côté
  serveur, dans la **tranche verticale** de la vision : un niveau avec un gros monstre et un moyen de le faire fuir,
  un casier (`GAMEPLAY.md` §16).
- **Physique** : dans le natif, moteur choisi au banc (§4, décision 3 du §11). **Voix** : sans chat vocal, la
  décision du §7.2 (vrai ou faux, écho local) ; avec Simple Voice Chat, niveau et mots par son module `compat/`
  (décision 5).
- Chaque phase suit la définition de « terminé » du projet (`docs/GUIDE.md`) : vérifiée en jeu, build complet vert,
  mesurée, sans couture à travers un portail.

---

## 9. Risques identifiés

**Vision**

| Risque | Réponse |
|---|---|
| Coût des échanges JNI | Miroir du monde unique, mises à jour incrémentales, appels par lots |
| Crash natif qui ferme le jeu | `catch_unwind` systématique, désactivation propre en cas d'échec |
| Performance des raycasts | Threads dédiés, cache des zones, fréquence réduite + interpolation, niveaux de détail |
| Limites d'OpenAL EFX (nombre de slots) | Détection au démarrage, adaptation du nombre de réverbérations simultanées |
| Compatibilité avec les autres mods | Matériaux par défaut déduits du `SoundType`, configuration par datapack |
| Changements de Minecraft entre versions | Toute la logique est en Rust, indépendante de la version ; seule la couche Java est à adapter |

**Notes**

| Risque ajouté | Réponse |
|---|---|
| Un son vanilla touché (risque de régression n° 1 du moteur audio) | Traitement selon la dimension d'émission ; dimension inconnue traitée comme vanilla (`MOTEUR_AUDIO.md` §1.1) |
| Licence et dépendances | Licences permissives seulement, notices dans le JAR, binaires admis nommés par le contrôle du JAR (§7.4, décision 2 du §11) |
| Crash natif que `catch_unwind` n'attrape pas | Repli Java, option pour couper le natif, coupe-circuit (§7.3) |
| Copie du monde différente entre client et serveur | Une copie par côté ; ce que perçoit un monstre ne dépend que du serveur (§2) |
| Équité entre joueurs | Perception à qualité fixe côté serveur, indépendante des paliers (`QUALITE_ET_PRESETS.md` §11) |
| Confiance dans le micro | Sans chat vocal, décision du §7.2 : local, vrai ou faux, avis et indicateur (`GAMEPLAY.md` §7.2). Avec Simple Voice Chat, le flux que le joueur a déjà choisi d'envoyer, rien de plus |
| Cohérence à travers les portails du mod | Son, objets et monstres passent le cadre comme le reste : relais des sons, `TeleportExecutor`, `PortalCollision`, `PortalPursuer` |
| Taille du JAR | Modèles de reconnaissance hors du JAR (§6) |

---

## 10. En résumé

**Vision**

Ce projet n'est pas une collection d'effets isolés. C'est **un monde simulé que les joueurs et les monstres
perçoivent de la même manière.** Les matériaux définissent comment le monde sonne et réagit. L'acoustique transforme
chaque action en information. La physique crée des bruits crédibles. La voix du joueur devient un risque réel. Et
l'IA exploite tout ça pour chasser intelligemment.

Rust apporte exactement ce dont ce cœur de simulation a besoin : des performances natives, un parallélisme sûr et une
stabilité indispensable dans un environnement où un crash ferme le jeu.

**Notes**

- Dans GTG's Backrooms, ce monde simulé est celui **des Backrooms** : l'Overworld reste vanilla.
- Le cœur existe déjà en Java pour l'acoustique ; ce document en donne l'extension (matériaux, pièces et ouvertures,
  perception des monstres, objets, voix) et son passage au natif, décidé (§11).

---

## 11. Décisions du 6 novembre 2026

Réponses de Pierre aux points laissés ouverts par la v2, consignées aussi dans `docs/DECISIONS.md` (sans les noms
des projets de référence). Elles priment sur les passages plus anciens de `GAMEPLAY.md` et de
`QUALITE_ET_PRESETS.md` qu'elles contredisent.

1. **Le moteur natif est en Rust.** « Je veux prioriser la performance, mais aussi me rapprocher le plus du résultat
   du son de TLOU 2, en plus des features que j'ai demandé à rajouter, donc pour moi c'est non négociable. » La note
   de `GAMEPLAY.md` §6.3 (« qu'après mesure ») est remplacée : la phase 0 du §8 mesure la référence à battre. Le
   moteur Java livré reste le repli (§7.3).
2. **Les bibliothèques embarquées sont permises.** « Si une bibliothèque embarquée doit l'être, alors soit, mais je
   ne veux pas de dépendances ou le nom d'un autre mod Minecraft ; un outil extérieur dans un autre langage n'est pas
   un souci. » Conséquences : une bibliothèque s'embarque telle quelle, jamais recopiée ni paraphrasée dans notre
   code ; licence permissive seulement, notice dans le JAR, binaires admis nommés par le contrôle du JAR (§7.4).
   Toujours interdits : une dépendance à un autre mod Minecraft, et son nom hors de son module `compat/`. Remplace,
   pour le moteur natif, la règle « aucune notice de tiers » de `QUALITE_ET_PRESETS.md` §3.0.
3. **La physique des objets** : « ce qui a le plus de performance et le meilleur résultat », dans le natif.
   Remplace la physique légère et maison de `GAMEPLAY.md` §9.4. Le moteur se choisit au banc (§4).
4. **Les blocs** : incassables pour les joueurs, **sans contour de sélection** ; les monstres peuvent agir sur eux,
   la manière est à concevoir plus tard (§5.6). Aujourd'hui, ni l'un ni l'autre n'est dans le mod : les blocs du
   niveau 0 se cassent comme les blocs vanilla qu'ils copient, et le contour s'affiche.
5. **La voix et la reconnaissance des mots** : « c'est surtout pour l'intégration avec Simple Voice Chat ». Avec lui,
   le niveau de la voix (chuchoter, parler, crier) et les mots passent par son module `compat/` (§6). Sans lui, la
   décision de `GAMEPLAY.md` §7.2 reste : un vrai ou faux traité en local.
6. **La reconnaissance des mots** : voir la décision 5. La piste des hallucinations qui répondent à un mot reconnu
   localement (§6) reste ouverte, avec ou sans chat vocal.
7. **Le nom de ce document** : `MOTEUR_NATIF_RUST.md`.

**Restent ouverts**

- Le moteur physique, choisi au banc entre les candidats du §4.
- Comment les monstres agissent sur les blocs (casser, déplacer, défoncer), et lesquels.
- La vitre brisable des machines à café et à snacks (`GAMEPLAY.md` §9.3) : exception aux blocs incassables, ou objet
  plutôt que bloc ?
- Où tourne la reconnaissance des mots avec le chat vocal : au serveur ou chez le client (§6).
- Les renvois vers ce document depuis `GAMEPLAY.md` §6.3, §7, §9.4 et `MOTEUR_AUDIO.md` : ces deux documents ne sont
  pas modifiés sans l'accord de Pierre.

---

## Historique des révisions

- **v1** : idées de Pierre : moteur natif en Rust pour l'acoustique, la physique des objets, les monstres et la voix.
- **v2** (6 novembre 2026) : alignement sur les documents compagnons et le projet, sans rien retirer de la v1 :
  en-tête de document interne ; périmètre Backrooms seulement et niveau 0 sans entités (§1) ; une copie du monde par
  côté, registre de matériaux en données, sons d'hallucination et équité (§2) ; état de chaque fonctionnalité
  acoustique dans le moteur livré, matériaux des Backrooms, libre parcours moyen au lieu du remplissage, vocabulaire
  pièces et ouvertures, budgets et paliers (§3) ; physique et portails du mod (§4) ; monstres rattachés à
  `GAMEPLAY.md` §7, §7.1, §7.3, §9.5, §9.6 (§5) ; voix rattachée à la décision du §7.2 et capture par OpenAL (§6) ;
  architecture côté Java, limites de `catch_unwind`, tests et distribution (§7) ; phase 0 de mesure (§8) ; risques
  ajoutés (§9) ; points à trancher (§11). Les formulations remplacées par une règle ou une décision sont conservées
  et annotées.
- **v3** (6 novembre 2026) : décisions de Pierre sur les sept points (§11) : moteur natif en Rust, bibliothèques
  embarquées permises sous licence permissive, physique la plus performante choisie au banc, blocs incassables sans
  contour de sélection pour les joueurs, voix et mots par l'intégration du chat vocal. Statut, notes et risques mis
  à jour ; document renommé `MOTEUR_NATIF_RUST.md` (anciennement `conception-moteur-rust-minecraft.md`).
