# Qualité visuelle, presets et menu — GTG's Backrooms

> **Document de conception interne** : il nomme des projets de référence. À garder hors du dépôt public, et sans
> aucune reprise de code ni mention de ces projets dans le mod (`QUALITE_ET_PRESETS.md` §3.0).
>
> Document de conception : paliers de qualité (Low, Medium, High, Ultra expérimental), presets visuels et sonores,
> menu de réglages dans le menu pause, et techniques nécessaires pour atteindre le visuel cible. Il **prolonge**
> `MOTEUR_SHADERS.md` et `MOTEUR_AUDIO.md` sans les remplacer : leurs règles (périmètre « Backrooms seulement »,
> exigences absolues, budgets relatifs au vanilla, hypothèses à valider) s'appliquent ici telles quelles.
>
> **Statut : feuille de route de conception.** Les hypothèses propres à ce document sont au §16.

---

## 1. Objectifs

1. **Atteindre le visuel de référence** (image 1) : un Level 0 éclairé par ses dalles de néons, lumière douce et
   chaude, rebonds de lumière jaune sur les murs, coins assombris, légère brume au loin.
2. **Des ombres et un éclairage pixelisés** (image 2), alignés sur la grille des textures en **32×32 pixels par
   bloc**, dans l'esprit des ombres « blocky » de Vibrant Visuals.
3. **Quatre paliers de qualité**, en tête du menu : **Low**, **Medium**, **High**, **Ultra (expérimental)** — ce
   dernier avec la partie ray tracing, la meilleure qualité, cinématique.
4. **Des presets visuels et sonores** propres aux moteurs du mod.
5. **Un menu dans le menu pause**, petit carré à la manière de Distant Horizons, **visible seulement dans les
   Backrooms**.
6. **Des lumières de zone (area lights) dynamiques, identiques pour tous les joueurs d'un serveur**, avec toutes les
   possibilités visuelles de Veil, réimplémentées.
7. **Low tourne sur la machine de référence** de `MOTEUR_SHADERS.md` §16.1 (PC de bureau à GPU intégré).

---

## 2. Ce que demandent les deux images

### 2.1 Image 1 : le visuel cible

| Ce qu'on voit                                         | Technique nécessaire                                         | Palier minimal |
| ----------------------------------------------------- | ------------------------------------------------------------ | -------------- |
| Dalles de plafond qui éclairent une zone, pas un point | **Lumières de zone rectangulaires** (area lights)            | Medium         |
| Murs jaunes qui teintent la lumière ambiante          | **Illumination globale** (un rebond)                         | Medium (cuite) |
| Coins et jonctions mur-plafond assombris              | **Occlusion ambiante**                                       | Low (cuite)    |
| Ombres douces des murets et des piliers               | **Ombres de lumières de zone**, douces                       | High           |
| Légère brume, profondeur                              | **Brouillard** teinté, volumétrique en haut de gamme         | Low            |
| Couleurs chaudes, contraste doux, aspect « filmé »    | **Étalonnage** (LUT) et **tone mapping**                     | Low            |
| Moquette et papier peint qui accrochent la lumière    | **Matériaux** (normales, rugosité)                           | High           |

**Point clé : le Level 0 n'a pas de soleil.** Toute sa lumière vient de ses néons. Il n'y a donc **pas besoin de
carte d'ombre solaire** (shadow map), la technique la plus coûteuse des shaderpacks. Tout repose sur des lumières de
zone et un peu de rebond, ce qui est beaucoup plus abordable.

### 2.2 Image 2 : les ombres pixelisées

- Ombres et lumière **quantifiées sur la grille des texels** du bloc : chaque « pixel » d'ombre a la taille d'un
  pixel de texture.
- Pour des textures 32×32, la grille est de **32 pas par bloc** (Vibrant Visuals expose le même réglage :
  `texel_size`, 16 par défaut, 32 pour des textures 32×32).
- La même grille s'applique à **toutes** les composantes d'éclairage (ombres, occlusion, rebond, lumière directe)
  pour un rendu cohérent.

### 2.3 Remarque de direction artistique

L'image 1 est plus réaliste que le principe initial « Minecraft, mais étrange » de `MOTEUR_SHADERS.md` §1. Les deux se
concilient ainsi : **l'éclairage** vise l'image 1, **la matière** reste celle du jeu (textures pixel 32×32, ombres
pixelisées). Le joueur doit voir un Minecraft éclairé comme une photo, pas un autre jeu.

---

## 3. Références : apprendre de ce qui existe, écrire notre propre version

### 3.0 Règle : chaque partie est notre propre version

Même règle que pour les moteurs (`MOTEUR_SHADERS.md` §3, `MOTEUR_AUDIO.md` §3), **quelle que soit la licence** de la
référence, permissive comprise :

1. **On ne reprend aucun code**, ni Java, ni GLSL, ni fragment de shader, ni table de valeurs, ni structure de
   classes, ni fichier de données. Pas même « pour tester ».
2. **On apprend de ce qui existe** : on lit, on comprend la technique, on prend des notes **en mots**, on referme, et
   on écrit **notre version** à partir de nos besoins.
3. **Le mod est entièrement propriétaire** : tout son code et ses ressources sont écrits pour lui. Aucune notice de
   tiers n'est nécessaire, parce que rien n'est repris.
4. **Aucune mention d'autre projet** dans le code, les commentaires, le JAR, l'interface, les crédits ni la
   documentation publiée du mod. Les noms de référence n'apparaissent que dans **ces documents de conception
   internes**, à garder hors du dépôt public (le dépôt GitHub du mod est public).
5. **Les données dérivées sont aussi les nôtres** : par exemple, les tables d'ajustement des lumières de zone (LTC,
   §4.4) sont **calculées par notre propre outil d'ajustement**, pas récupérées toutes faites.
6. **Les techniques publiées** (articles, méthodes générales comme DDA, LTC, tone mapping, sondes d'éclairage) sont
   des connaissances, pas du code : on les implémente nous-mêmes.
7. **Les formats ouverts** de la communauté (comme la convention de matériaux du §5.6) sont des conventions, pas du
   code : les suivre ne demande ni de reprendre du code ni de mentionner un projet.
8. **Le but** : des parties qui **fonctionnent parfaitement ensemble** (portails, stencil, deux moteurs, gameplay),
   ce qu'aucune pièce reprise d'ailleurs ne ferait.

Ces règles s'appliquent aussi à l'agent de développement : son `CLAUDE.md` lui interdit d'aller chercher ou de copier
le code de ces références.

### 3.0.1 Ce qu'on apprend de chaque référence

| Référence                                   | Licence (pour mémoire)       | Ce qu'on en apprend                                                                                     | Usage                                |
| ------------------------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------------- | ------------------------------------ |
| **Veil** (FoundryMC)                        | LGPL-3.0                     | Types de lumières (point, zone, spot, directionnelle), rendu différé des lumières, ombres par **DDA** sur la grille de blocs, éditeur de lumières en jeu, post, framebuffers | **Apprendre, ne rien reprendre** |
| **RTX Remix pour Minecraft Java** (NVIDIA, bêta du 23 octobre 2026) | Apache 2.0 (code du mod) | Mod Fabric pour **1.21.1** (la même version que le mod) qui envoie la scène à un path tracer ; matériaux **LabPBR** ; lumière directionnelle pilotée par redstone ; outils de caméra et de capture | **Apprendre, ne rien reprendre** ; pas d'intégration du runtime (§3.2) |
| **Lumina** (seilotte)                       | MIT                          | Shaderpack Iris orienté performance (OpenGL 4.6, développé sur 1.21.11)                                  | **Apprendre, ne rien reprendre**     |
| **Lumin** (modpack d'optimisation, Fabric 26.2) | Selon chaque mod          | Sélection d'optimisations : Sodium, ImmediatelyFast, EntityCulling, More Culling ; shaderpacks rangés de « Potato » à « Ultra » | Inspiration (liste et paliers)       |
| **Vibrant Visuals** (Mojang)                | Propriétaire                 | Ombres `blocky_shadows` alignées sur les texels (`texel_size`), éclairage différé                       | **Référence de design**              |
| **Complementary Reimagined**                | Personnalisée, tous droits réservés | Profils « Potato » à « Ultra », éclairage coloré                                                | **Apprendre, ne rien reprendre**     |
| **Rethinking Voxels** (fork de Complementary) | Hérite de Complementary (à vérifier) | Voxelisation, lumière colorée, ombres de lumières tracées par rayons                             | **Apprendre, ne rien reprendre**     |
| **SEUS PTGI**                               | Propriétaire                 | Illumination globale tracée dans un volume de voxels                                                    | Référence de résultat seulement      |
| **Photon** (sixthsurge)                     | À vérifier                   | Architecture moderne de shaderpack                                                                      | **Apprendre, ne rien reprendre**     |

### 3.1 Conclusion : aucun pack à forker

Aucun pack existant ne gère les **vues de portail** (stencil, plusieurs mondes par image), et aucune pièce reprise
d'ailleurs ne s'emboîterait avec nos moteurs. La bonne approche reste celle de `MOTEUR_SHADERS.md` : **notre
moteur**, nourri de ce qu'on a **appris** de ces références, et **entièrement écrit par nous** (§3.0).

### 3.2 RTX Remix : inspiration, pas moteur de l'Ultra

Le mod de NVIDIA est une source précieuse (même version de Minecraft, Fabric, LabPBR, code sous Apache 2.0), mais
son runtime n'est pas une base pour l'Ultra :

- il exige une **GeForce RTX série 30 ou plus récente** et **Windows** ;
- il **remplace une bonne partie du rendu** du jeu, ce qui le met en conflit direct avec les vues de portail ;
- sa compatibilité avec d'autres mods est présentée comme **peu testée**.

À en retenir : la façon d'extraire la géométrie des chunks en 1.21.1, le format LabPBR, et ses outils de caméra.

### 3.3 Le passage de Minecraft à Vulkan

Mojang remplace OpenGL par **Vulkan** dans Java Edition, avec des tests annoncés pour l'été 2026, dans le cadre de
Vibrant Visuals. Le mod restant sur **1.21.1** (OpenGL), rien ne change aujourd'hui. Mais tout portage vers une version
récente demandera de **réécrire la partie GL** des moteurs. D'où une règle déjà posée et renforcée ici : **logique
séparée des appels graphiques**, derrière une petite couche d'abstraction (création de cibles, passes, programmes).

---

## 4. Les quatre paliers de qualité

### 4.1 Vue d'ensemble

| Élément                  | **Low**                                      | **Medium**                                         | **High**                                                    | **Ultra (expérimental)**                                   |
| ------------------------ | -------------------------------------------- | -------------------------------------------------- | ----------------------------------------------------------- | ---------------------------------------------------------- |
| Cible matérielle         | GPU intégré (machine de référence)           | GPU intégré récent, carte dédiée d'entrée de gamme  | Carte dédiée milieu de gamme                                | Carte dédiée haut de gamme                                  |
| OpenGL minimal           | 3.2                                          | 3.2                                                | 3.3                                                         | **4.3** (compute, SSBO) — pas de macOS                     |
| Chemin de rendu          | Forward (architecture actuelle)              | Forward + lumières par tuiles                      | **Différé** (G-buffer) pour l'opaque, forward pour le translucide | Différé + **tracé de rayons** dans un volume de voxels   |
| Lumières des néons       | Lightmap par dimension, courbe du profil     | **Lumières de zone** analytiques, les N plus proches, sans ombre | Lumières de zone (LTC), **ombres DDA douces**               | Lumières de zone tracées, ombres exactes                   |
| Illumination globale     | Cuite par cellule, dans une texture 3D de zone lue par l'injection (pas dans la lightmap, §5.5) | **Sondes cuites par cellule** (§5.5)               | Sondes cuites + rebond d'écran léger                        | **1 à 2 rebonds tracés**, accumulation temporelle          |
| Occlusion ambiante       | AO vanilla + coins cuits                     | AO cuite + SSAO demi-résolution                    | SSAO pleine qualité, alignée sur les texels                 | Occlusion tracée                                           |
| Ombres pixelisées        | Lumière et AO alignées sur les texels        | Oui                                                | Oui                                                         | Oui (désactivables pour un rendu « réaliste »)             |
| Matériaux (LabPBR)       | Non                                          | Non                                                | **Normales et rugosité**                                    | Normales, rugosité, émissif, réflexions tracées            |
| Brouillard               | Vanilla teinté                               | Teinté, par hauteur                                | **Volumétrique** basse résolution                           | Volumétrique, éclairé par les néons                        |
| Post                     | Étalonnage LUT seul, appliqué **dans l'injection** (aucun pass post, comme le veut `minimum`) | LUT + vignette                                     | LUT, tone mapping, bloom basse résolution                   | + profondeur de champ, flou de mouvement, grain filmique   |
| Anti-crénelage / échelle | Aucun                                        | Aucun                                              | FXAA                                                        | **TAA** et rendu à résolution réduite puis agrandi         |
| Vues de portail          | Plafonnées à Low                             | Plafonnées à Low ou Medium selon le LOD            | Plafonnées à Medium                                         | Plafonnées à High ; l'Ultra ne s'applique qu'à la vue du joueur |

Correspondance avec les paliers de `MOTEUR_SHADERS.md` §16.2 : **Low = `minimum`**, **Medium = `léger`**,
**High = `complet`**, **Ultra = nouveau**. Le palier `désactivé` reste disponible dans les réglages avancés (§10).

Les choix de `MOTEUR_SHADERS.md` « pas de rendu différé », « pas de compute » et « le joueur ne doit pas reconnaître
un shaderpack » décrivent **Low et Medium**. **High** (rendu différé) et **Ultra** (compute, OpenGL 4.3) les
dépassent volontairement pour atteindre le visuel de référence (§2.3) ; `MOTEUR_SHADERS.md` §1 le précise aussi.

### 4.2 Low : l'identité à coût quasi nul

Le palier de la machine de référence. C'est l'architecture actuelle de `MOTEUR_SHADERS.md` (phases 1 à 3) :
lightmap et brouillard par dimension, injection dans les shaders vanilla, **plus** :

- un **étalonnage par LUT** (une texture de correspondance des couleurs), partagé par tous les paliers (§5.7) ;
- **lumière et AO cuites par cellule** du Level 0, alignées sur les texels, stockées dans une texture 3D de zone lue
  par l'injection (et non dans la lightmap : voir la précision ci-dessous) ;
- aucun pass supplémentaire : la LUT elle-même est appliquée dans l'injection.

**Précision technique** : la lightmap de Minecraft est une petite table indexée par **niveau de lumière** (bloc et
ciel), pas par **position**. Elle ne peut donc pas porter un éclairage cuit par cellule. En Low, la lumière et l'AO
cuites sont stockées dans une **texture 3D de zone** (quelques cellules autour du joueur, basse résolution) lue par
l'**injection** (étage 1), avec l'arrondi au texel du §5.4 ; la lightmap garde seulement la courbe de la dimension.
De même, la LUT de Low est appliquée **dans l'injection**, fragment par fragment : il n'y a **aucun pass post** en Low,
conformément au palier `minimum` de `MOTEUR_SHADERS.md` §16.2.

### 4.3 Medium : les lumières de zone arrivent

- Les dalles de néons deviennent de **vraies lumières de zone**, calculées dans le shader des blocs pour les
  **N plus proches** de chaque tuile d'écran (forward par tuiles).
- **Sondes d'illumination cuites** par cellule : le rebond jaune des murs, sans calcul en temps réel.
- SSAO en demi-résolution.

### 4.4 High : le visuel de l'image 1

- **Rendu différé** pour l'opaque : l'injection écrit en plus normales et matériaux dans des cibles supplémentaires
  (hypothèse Q1). Le translucide reste en forward.
- Lumières de zone par **LTC** (*Linearly Transformed Cosines*, Heitz et al., 2016), la méthode de référence pour
  des rectangles lumineux exacts et peu coûteux.
- **Ombres douces** par **DDA** dans la grille de blocs (la technique de Veil, réimplémentée), échantillonnées sur
  la surface de la lumière.
- Brouillard volumétrique basse résolution, bloom basse résolution, tone mapping.

### 4.5 Ultra (expérimental) : le ray tracing

- **Volume de voxels** de la zone proche (le Level 0 est une grille de blocs : idéal pour le tracé), mis à jour par
  section modifiée.
- **Lumière directe, ombres, 1 à 2 rebonds et réflexions tracés** par compute shaders (OpenGL 4.3).
- **Accumulation temporelle et débruitage**, TAA, rendu à résolution réduite puis agrandi.
- Proposé **seulement si le matériel le permet** : grisé avec une explication sinon (macOS, OpenGL < 4.3, VRAM
  insuffisante).
- **Expérimental** : désactivé automatiquement par le coupe-circuit au moindre problème, retour à High.

---

## 5. Les briques clés

### 5.1 Lumières (toutes les possibilités de Veil, réimplémentées)

| Type             | Usage dans les Backrooms                                     | Paramètres                                                  |
| ---------------- | ------------------------------------------------------------ | ----------------------------------------------------------- |
| **Zone (area)**  | Dalles de néons, écrans, fenêtres                            | Position, largeur, hauteur, orientation, couleur, intensité, portée |
| **Point**        | Ampoules, lampes portées, effets                             | Position, rayon, couleur, intensité                         |
| **Spot**         | Lampe torche, phares, lumière qui fouille                    | Position, direction, angle, taille, couleur, intensité      |
| **Directionnelle** | Niveaux avec ciel ou soleil artificiel                     | Direction, couleur, intensité ; carte d'ombre dans ce seul cas |

- **Éditeur en jeu** (mode développement uniquement, comme celui de Veil) : poser, régler et sauvegarder des lumières.
- **Lumières déclarées en données** : un bloc de néon déclare sa lumière de zone (taille, couleur, intensité) dans un
  fichier, pas dans le code.
- **Budget** par palier : nombre maximal de lumières par tuile, puis par vue.

### 5.2 Lumières dynamiques identiques sur un serveur

Toutes les lumières doivent être **les mêmes pour tous les joueurs**, sans trafic réseau dédié :

| Source de la lumière                    | Comment elle arrive chez chaque client                                     | Paquets ajoutés |
| --------------------------------------- | -------------------------------------------------------------------------- | --------------- |
| Néons et blocs lumineux                 | Dérivées des **blocs**, déjà synchronisés avec les chunks                   | Aucun           |
| Clignotement, pannes, vacillement        | **`NeonSchedule`** déterministe (`MOTEUR_AUDIO.md` §6.8), même horloge pour tous | Aucun      |
| Perturbation par un cri                 | **`Disturbances`** déclenchées par le `Cue` du son (`MOTEUR_AUDIO.md` §6.9) | Aucun          |
| Lampe torche, lumière portée par une créature | **Données d'entité** synchronisées par le suivi vanilla                | Aucun           |
| Événement scénarisé (panne générale…)   | Un seul paquet d'événement, puis déterministe                               | Un par événement |

Les lumières vues **à travers un portail** suivent les mêmes règles, avec l'horloge et les signaux de **leur**
dimension (`MOTEUR_SHADERS.md` §7).

### 5.3 Ombres

- **Low et Medium** : pas d'ombre dynamique des lumières ; ombres et AO **cuites** par cellule.
- **High** : **DDA dans la grille de blocs** depuis le point éclairé vers plusieurs points de la lumière de zone ;
  la fraction de rayons libres donne une ombre douce. Seuls les blocs pleins occultent (limite assumée, comme dans
  Veil) ; les entités ne projettent pas d'ombre de lumière en High.
- **Ultra** : ombres tracées dans le volume de voxels, entités comprises par une représentation simplifiée.
- **Grille de blocs** : lue sur une copie compacte des sections proches, envoyée au GPU en texture 3D et mise à jour
  par section modifiée.

### 5.4 Ombres et éclairage pixelisés

- **Principe** : avant de calculer la lumière d'un point, sa position est **arrondie au centre de son texel** sur la
  face du bloc : `floor(position × N) / N + 0,5 / N`, avec **N = 32** pour des textures 32×32.
- **Appliqué à tout** : lumière directe, ombres, AO et sondes, pour que tout l'éclairage partage la même grille.
- **Réglage** : `Taille des pixels d'ombre` = 16, 32 (défaut, aligné sur les textures du mod), ou désactivé.
- **Coût** : quasi nul. L'arrondi se fait dans le shader, à tous les paliers.
- Mêmes précautions que Vibrant Visuals : **toutes les textures d'un même usage à la même résolution**, sinon la
  grille ne correspond plus aux pixels.

### 5.5 Illumination globale cuite par cellule

Le Level 0 est **généré de façon déterministe** et sa géométrie change rarement. On en profite, comme pour
l'acoustique (`MOTEUR_AUDIO.md` §6.4) :

- au chargement d'une cellule, le client **cuit** une petite grille de **sondes** d'éclairage (lumière reçue des
  néons et rebond sur les murs), mise en cache comme les secteurs de génération ;
- l'éclairage d'un point lit les sondes voisines : le rebond jaune de l'image 1 pour presque rien ;
- la contribution de chaque groupe de néons est **stockée à part**, pour que les clignotements de `NeonSchedule`
  modulent le rebond sans tout recuire (hypothèse Q4) ;
- une cellule modifiée par un joueur est recuite en arrière-plan ;
- **stockage** : une texture 3D de zone, lue par l'injection (Low, Medium) ou par la passe d'éclairage (High), jamais
  la lightmap vanilla, indexée par niveau de lumière et non par position (§4.2).

### 5.6 Matériaux

- Format **LabPBR**, le standard des shaderpacks, aussi lu par le mod de NVIDIA : une texture de normales et une de
  propriétés (rugosité, métal, émissif) à côté de chaque texture 32×32.
- Utilisés à partir de **High** ; ignorés en dessous, sans coût.

### 5.7 Étalonnage commun à tous les paliers

- Le « look » (teinte jaune, contraste doux) est défini par une **LUT** par dimension, appliquée en fin d'image à
  tous les paliers. **Low et Ultra ont la même palette** : seul le réalisme de la lumière change.
  *Précision* : « en fin d'image » vaut à partir de **Medium** (post masqué) ; en **Low**, la même LUT est appliquée
  **dans l'injection**, sans pass post (§4.2). Le résultat visuel est le même à quelques détails près (la LUT
  s'applique alors avant le mélange des translucides).
- **Tone mapping** (type AgX ou ACES) à partir de High, réglé pour que le résultat garde la palette de la LUT.

### 5.8 Brouillard et atmosphère

- Low et Medium : brouillard du profil de dimension (déjà prévu), teinté par la LUT.
- High et Ultra : brouillard **volumétrique** en basse résolution, éclairé par les lumières de zone, qui donne le
  halo des dalles et la profondeur de l'image 1.

---

## 6. Vues de portail et paliers

- **Chaque vue a son palier**, plafonné (tableau du §4.1) et dégradé par le LOD des vues.
- En High, le rendu différé d'une vue se fait **sous son ciseau et son stencil de niveau** : remplissage des cibles,
  puis passe d'éclairage masquée. Même discipline que le post masqué (`MOTEUR_SHADERS.md` §6.5).
- L'Ultra ne s'applique **qu'à la vue du joueur** : un volume de voxels par dimension vue serait hors budget.
- **Exigence absolue inchangée** : une vue vers l'Overworld reste strictement vanilla, quel que soit le palier.

---

## 7. Presets visuels

Un preset visuel règle le **style**, indépendamment du palier de qualité. Il ne change jamais ce qui est visible pour
le gameplay (§11).

| Preset                     | Pour                                     | Contenu                                                                                         |
| -------------------------- | ---------------------------------------- | ----------------------------------------------------------------------------------------------- |
| **Fidèle** (défaut)        | Le visuel cible                          | Image 1 : LUT jaune, ombres pixelisées 32, grain très léger                                      |
| **Pixel**                  | Le plus « Minecraft »                    | Ombres et lumière pixelisées accentuées, pas de flou ni de profondeur de champ                   |
| **Réaliste**               | Captures, contemplation                  | Ombres lissées (pixelisation coupée), matériaux et brouillard poussés (High et Ultra)            |
| **Found footage (VHS)**    | L'ambiance des vidéos des Backrooms       | Bruit de bande, légère déformation, balance des blancs approximative, horodatage optionnel        |
| **Cinématique**            | Vidéos, streams, bandes-annonces          | Bandes noires optionnelles, profondeur de champ, flou de mouvement, grain filmique (Ultra conseillé) |
| **Confort**                | Accessibilité, longues sessions           | Aucun flash, aucun grain, aucune aberration, aucun tremblement, plafond de clignotement renforcé  |

---

## 8. Presets sonores

| Preset                     | Pour                                     | Contenu                                                                                         |
| -------------------------- | ---------------------------------------- | ----------------------------------------------------------------------------------------------- |
| **Casque (HRTF)**          | Jeu au casque (recommandé)               | Spatialisation **binaurale** d'OpenAL Soft (HRTF) : on situe un monstre devant, derrière, au-dessus |
| **Haut-parleurs**          | Enceintes stéréo ou 5.1                  | Panoramique classique, réverb un peu réduite pour ne pas noyer la pièce                          |
| **Immersif** (défaut)      | Le rendu de référence                    | Acoustique complète du palier, échos réalistes des cris                                          |
| **Streamer**               | Streams et enregistrements               | **Compression de dynamique** : les cris restent effrayants sans saturer, cohabite avec le chat vocal |
| **Nuit**                   | Jouer à faible volume                    | Dynamique fortement réduite, sons faibles remontés                                              |
| **Accessibilité**          | Malentendants, jeu sans son              | Sous-titres étendus, **indicateurs visuels de direction** des sons importants, pas de tremblement |

Qualité sonore, alignée sur le palier visuel par défaut et réglable à part :

| Palier    | Acoustique (`MOTEUR_AUDIO.md` §17.1)                                                       |
| --------- | ------------------------------------------------------------------------------------------ |
| Low       | `minimum` : acoustique générée seule, un envoi, occlusion un chemin                        |
| Medium    | `léger` : rayons pour les cellules modifiées, deux envois                                  |
| High      | `complet` : rayons 32 / 4 rebonds, occlusion multi-chemins, absorption de l'air            |
| Ultra     | `complet` + **direction perçue** des sons par les réflexions, réévaluation des sons longs  |

Réglages de volume à part : bourdonnement et ambiance, créatures, hallucinations, interface.

---

## 9. Le menu (dans le menu pause)

### 9.1 Placement

- Un **petit bouton carré**, à la manière de Distant Horizons, dans le **menu pause**.
- **Visible seulement dans les Backrooms** (`GAMEPLAY.md` §3 et §1.1). Dans l'Overworld, le menu pause est vanilla.
- Ouvre un écran de réglages dans l'esthétique du mod.

### 9.2 Contenu, de haut en bas

1. **Qualité** : Low · Medium · High · Ultra (expérimental), plus un bouton **Auto**.
2. **Preset visuel** (§7).
3. **Preset sonore** (§8) et qualité sonore.
4. **Ombres pixelisées** : 16 · 32 · désactivées.
5. **Confort** : plafond de clignotement, réduction des mouvements, intensité des hallucinations visuelles (§11).
6. **Avancé** : chaque option individuelle, avec son **coût mesuré en direct** (temps GPU de la dernière seconde,
   issu du moniteur de performance), et le palier `désactivé`.

### 9.3 Comportement

- **Aperçu en direct** : le flou d'arrière-plan des menus de 1.21 est **coupé** tant que cet écran est ouvert, pour
  voir les changements sur le jeu derrière.
- **Changer de palier** peut recompiler des shaders et allouer des cibles : un court à-coup, acceptable dans le menu
  pause, **jamais** pendant une traversée de portail.
- **Auto** choisit un palier d'après le GPU (`GL_RENDERER`), la version d'OpenGL, la mémoire vidéo quand le pilote
  l'expose, et un **court test de performance** sur une scène du Level 0.
- **Ultra** est grisé avec une explication si le matériel ne le permet pas.
- Réglages stockés **côté client** ; le serveur peut imposer des bornes (§11).

---

## 10. Correspondance des paliers dans les trois documents

| Ici        | `MOTEUR_SHADERS.md` §16.2 | `MOTEUR_AUDIO.md` §17.1 |
| ---------- | ------------------------- | ----------------------- |
| —          | `désactivé` (Avancé)      | `désactivé` (Avancé)    |
| **Low**    | `minimum`                 | `minimum`               |
| **Medium** | `léger`                   | `léger`                 |
| **High**   | `complet`                 | `complet`               |
| **Ultra**  | nouveau (ce document)     | `complet` + extensions  |

---

## 11. Règle d'équité : les presets ne changent pas le gameplay

Dans un mod d'horreur multijoueur qui sert de prison, un réglage graphique ne doit **jamais** donner d'avantage :

- **même visibilité à tous les paliers** : obscurité, distance de brouillard effective, contraste des menaces. Low
  doit montrer les mêmes dangers qu'Ultra, avec moins de réalisme ;
- les presets **Confort** et **Accessibilité** réduisent l'inconfort (flashs, tremblements), pas la difficulté ;
- un indicateur visuel de son (Accessibilité) montre une **direction**, jamais une position exacte à travers les murs ;
- le serveur peut **verrouiller** certains réglages dans sa config (luminosité minimale, intensité des hallucinations).

---

## 12. Performance

| Palier | Machine de référence                         | Objectif (à valider, relatif au vanilla, `MOTEUR_SHADERS.md` §16.3) |
| ------ | -------------------------------------------- | ------------------------------------------------------------------- |
| Low    | GPU intégré type Intel UHD 620               | Budgets de `MOTEUR_SHADERS.md` §16.3                                |
| Medium | GPU intégré récent, ou GTX 1050              | Au plus 30 % de temps d'image en plus que Low sur la même machine    |
| High   | RTX 2060 / RX 6600 ou équivalent             | 60 images par seconde en 1080p dans une salle du Level 0             |
| Ultra  | RTX 3070 ou équivalent, OpenGL 4.3           | 30 à 60 images par seconde en 1080p avec rendu réduit et TAA ; expérimental |

Mesures avec le banc et la méthode de `MOTEUR_SHADERS.md` §9.3 et §16.3, brique par brique puis d'ensemble.

---

## 13. Ordre de travail

Ce document ne change pas l'ordre des moteurs : le premier jalon commun (`MOTEUR_SHADERS.md` §12.1) reste prioritaire.

| Étape | Contenu                                                                                     |
| ----- | ------------------------------------------------------------------------------------------- |
| 1     | **Low** : LUT, ombres et AO cuites alignées sur les texels (pixelisation dès le départ)      |
| 2     | **Menu** : bouton du menu pause, palier, presets, Auto (avec seulement Low disponible)       |
| 3     | **Medium** : lumières de zone par tuiles, sondes cuites par cellule                         |
| 4     | **Lumières dynamiques** : branchement sur `NeonSchedule` et `Disturbances`                  |
| 5     | **High** : rendu différé (injection en cibles multiples), LTC, ombres DDA, brouillard volumétrique, LabPBR |
| 6     | Presets visuels et sonores complets                                                         |
| 7     | **Ultra** : volume de voxels, tracé, accumulation et débruitage, TAA                        |

---

## 14. Robustesse

- **Repli par palier** : une erreur dans un palier fait redescendre au palier inférieur, jamais un écran cassé.
- **Ultra** sous coupe-circuit dédié, désactivé pour la session à la première erreur.
- Ressources (cibles, volumes, sondes) **libérées au changement de palier** et en quittant les Backrooms.
- En quittant les Backrooms, **tout** revient au vanilla (`GAMEPLAY.md` §1.1).

---

## 15. Tests

| Niveau      | Quoi                                                                                                   |
| ----------- | ------------------------------------------------------------------------------------------------------ |
| Unitaires   | Arrondi au texel (16, 32, faces dans les six directions, coordonnées négatives) ; DDA sur des grilles construites à la main ; cuisson des sondes d'une cellule connue (golden) |
| Manuel      | Low sur la machine de référence : aucun pass post (vérifié sous RenderDoc), LUT et éclairage cuit visibles par l'injection seule |
| Unitaires   | Correspondance des paliers entre les trois documents ; choix de l'Auto pour des profils matériels simulés |
| Manuel      | Comparaison à l'image 1 dans une salle du Level 0, à chaque palier ; comparaison à l'image 2 pour la pixelisation |
| Manuel      | Équité (§11) : mêmes menaces visibles dans une scène sombre, de Low à Ultra                            |
| Manuel      | Vues de portail à chaque palier ; vue vers l'Overworld strictement vanilla                             |
| Manuel      | Menu : visible seulement dans les Backrooms, aperçu en direct, changement de palier, Ultra grisé sans OpenGL 4.3 |
| Performance | Chaque palier sur sa machine de référence (§12)                                                        |

---

## 16. Hypothèses à valider

| #  | Hypothèse                                                                                     | Validation                                                     | Si elle tombe                                                    |
| -- | --------------------------------------------------------------------------------------------- | -------------------------------------------------------------- | ---------------------------------------------------------------- |
| Q1 | L'injection peut faire écrire aux shaders vanilla des **cibles multiples** (normales, matériaux) pour le rendu différé | Prototype sur `rendertype_solid` et `rendertype_cutout`, sous le stencil | High en forward avec lumières de zone par tuiles et ombres DDA par fragment |
| Q2 | Le rendu différé d'une vue sous son ciseau et son stencil tient dans le budget                 | Mesure avec un, puis deux portails                            | Vues plafonnées à Medium en High                                 |
| Q3 | Les ombres DDA douces tiennent en High (nombre d'échantillons par lumière)                    | Mesure dans une salle de 30 néons                             | Ombres de la lumière la plus proche seulement, ou ombres cuites  |
| Q4 | Les sondes cuites peuvent stocker les contributions par groupe de néons pour suivre les clignotements | Prototype d'une cellule, comparaison visuelle                 | Rebond fixe, clignotement appliqué à la lumière directe seulement |
| Q5 | La pixelisation donne le rendu de l'image 2 sur toutes les faces, sans artefacts aux arêtes   | Captures comparées à l'image 2                                 | Arrondi dans l'espace UV de la texture plutôt qu'en position      |
| Q6 | L'Ultra est atteignable en OpenGL 4.3 sur la machine de référence Ultra (§12)                  | Prototype : volume de voxels, lumière directe tracée           | Ultra limité à des réflexions et ombres tracées, sans rebonds     |
| Q7 | Le test de performance de l'Auto prédit correctement le bon palier                            | Comparaison avec le palier tenu sur plusieurs machines         | Auto basé sur le seul profil matériel, avec ajustement manuel     |
| Q8 | La LUT seule suffit à donner la palette de l'image 1 en Low                                   | Comparaison visuelle                                           | LUT et courbe de lightmap ajustées ensemble                      |

---

## 17. Checklist

- [ ] Menu visible **seulement dans les Backrooms** ; menu pause vanilla ailleurs
- [ ] Qualité tout en haut : Low, Medium, High, Ultra (expérimental), plus Auto
- [ ] Ultra grisé avec explication sans OpenGL 4.3 (dont macOS)
- [ ] Même palette à tous les paliers (LUT commune)
- [ ] Low sans aucun pass post : LUT et éclairage cuit appliqués dans l'injection, éclairage cuit en texture 3D de
  zone, jamais dans la lightmap (§4.2)
- [ ] Ombres et éclairage pixelisés sur la même grille, 32 par défaut
- [ ] Lumières dynamiques identiques pour tous, sans paquet dédié hors événements scénarisés
- [ ] Équité : aucun palier ni preset ne donne d'avantage de visibilité
- [ ] Vues de portail plafonnées ; vue vers l'Overworld strictement vanilla
- [ ] **Aucun code repris**, quelle que soit la licence : chaque partie est notre propre version (§3.0)
- [ ] Aucune mention d'autre projet dans le code, le JAR, l'interface, les crédits ni la documentation publiée
- [ ] Tables de données (LTC…) produites par nos propres outils
- [ ] Logique séparée des appels GL (anticipation de Vulkan)
- [ ] Repli d'un palier sur l'autre en cas d'erreur
- [ ] Hypothèses Q1 à Q8 statuées

---

## Sources

- Veil, page « Lights » du wiki : https://github.com/FoundryMC/Veil/wiki/Lights
- RTX Remix pour Minecraft Java : https://www.minecraft-france.fr/minecraft-java-rtx-remix-nvidia/ et
  https://outputlag.com/news/minecraft-java-finally-gets-nvidia-path-tracing-but-players-arent-sold-on-the-look/
- Lumina : https://github.com/seilotte/lumina
- Lumin (modpack) : https://www.curseforge.com/minecraft/modpacks/lumin
- Ombres de Vibrant Visuals : https://learn.microsoft.com/en-us/minecraft/creator/documents/vibrantvisuals/shadowscustomization
- Passage de Java Edition à Vulkan : https://www.tomshardware.com/video-games/after-17-years-minecraft-java-edition-starts-replacing-opengl-with-a-vulkan-multi-threaded-renderer-update-will-prove-a-boon-for-performance-and-modding
- Complementary Reimagined : https://minecraftbible.com/shaders/complementary-reimagined
- Rethinking Voxels : https://github.com/gri573/rethinking-voxels

---

## Historique des révisions

- **v1** : paliers Low à Ultra, analyse des images de référence, références et licences, lumières de zone
  dynamiques synchronisées, ombres pixelisées, illumination globale cuite par cellule, presets visuels et sonores,
  menu du menu pause, règle d'équité, hypothèses Q1 à Q8.
- **v2** : règle « notre propre version de chaque partie » (§3.0) : aucun code repris quelle que soit la licence,
  aucune mention d'autre projet hors de ces documents internes, tables de données produites par nos outils ;
  tableau des références réorienté vers ce qu'on apprend (§3.0.1), conclusion et checklist alignées.
- **v3** : passe de cohérence entre les quatre documents : Low sans aucun pass post, LUT appliquée dans l'injection
  (§4.1, §4.2, §5.7) ; éclairage cuit en texture 3D de zone, la lightmap vanilla étant indexée par niveau de lumière
  et non par position (§4.1, §4.2, §5.5) ; portée des choix de `MOTEUR_SHADERS.md` limitée à Low et Medium (§4.1) ;
  tests et checklist complétés ; mention « document interne ».
