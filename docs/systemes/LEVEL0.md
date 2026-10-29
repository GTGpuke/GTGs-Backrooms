# Level 0

Le Level 0 est la dimension `gtgs_backrooms:backrooms_level_0`. C'est un labyrinthe sans fin, généré de façon
déterministe et verrouillée par des empreintes. Le code est dans `backrooms/level0/`.

## En jeu

Le Level 0 aligne des couloirs et des salles de papier peint jauni, sur une moquette humide, sous des dalles de plafond.
Des néons en quinconce éclairent environ une cellule ouverte sur quatre. Cinq layers s'empilent, chacun avec son propre
labyrinthe. Rien n'y apparaît naturellement.

Deux biomes cosmétiques changent la palette sans toucher au labyrinthe.

- Le biome de base a des murs jaunis, une moquette humide et les néons de la trame.
- Le biome rouge a des murs blancs, une moquette rouge et aucun néon. Il n'existe que sur les layers 1 et 3, par
  régions de 48 cellules, et reste rare.

Les murs portent une plinthe au ras du sol. Quelques-uns ont un interrupteur ou une prise. De grandes pièces ouvrent
le labyrinthe, et de rares couloirs se resserrent à un bloc de large.

En solo, l'écran F3 affiche le layer courant et le nombre de secteurs en cache.

### Accès

Un portail posé hors du Level 0 mène par défaut au layer 0, près des mêmes X/Z. Un portail posé dans le Level 0 ramène
à la surface. Le détail est dans [PORTAILS.md](PORTAILS.md).

Une commande y mène aussi :

```mcfunction
/execute in gtgs_backrooms:backrooms_level_0 run teleport @s 1 1 1
```

Le point (1, 1, 1) est le centre de la cellule d'origine du layer 0. La zone de 5 × 5 cellules autour de l'origine est
toujours ouverte, sur chaque layer. Le layer n se rejoint en Y = 10 × n + 1, par exemple Y = 21 pour le layer 2.

## Fonctionnement

### Dimension

| Élément | Valeur |
|---------|--------|
| Dimension | `gtgs_backrooms:backrooms_level_0`, générateur `gtgs_backrooms:backrooms_level_zero` (`LevelZeroChunkGenerator`) |
| Type de dimension | `gtgs_backrooms:backrooms_level_0_type` : Y de 0 à 63, sans ciel, avec plafond, lumière ambiante nulle, heure fixe |
| Effets de dimension | `gtgs_backrooms:backrooms_level_0`, enregistrés côté client par `LevelZeroEffects` |
| Biome | `gtgs_backrooms:backrooms_level_0` : sans pluie, sans feature, sans apparition de créature |

Le générateur n'a ni surface, ni grottes, ni apparition d'entités. Tout s'écrit pendant `populateNoise`.

### Pile verticale

Cinq layers de dix blocs s'empilent à partir de Y = 0 (`LevelZeroLayerStackLayout`).

| Layer | Sol | Air jouable | Plafond | Bedrock entre deux layers |
|-------|-----|-------------|---------|---------------------------|
| 0 | 0 | 1 à 4 | 5 | 6 à 9 |
| 1 | 10 | 11 à 14 | 15 | 16 à 19 |
| 2 | 20 | 21 à 24 | 25 | 26 à 29 |
| 3 | 30 | 31 à 34 | 35 | 36 à 39 |
| 4 | 40 | 41 à 44 | 45 | 46 à 49 |

Les Y 50 à 63 restent vides. Minecraft impose une hauteur de dimension multiple de 16, d'où 64 blocs.

### Génération

`LevelZeroChunkGenerator` orchestre la génération. Pour chaque layer, il dérive une graine, demande le layout du chunk,
puis le fait écrire. Il ne décide lui-même ni topologie, ni lumière, ni détail.

| Étape | Paquet de `generation/` | Rôle |
|-------|-------------------------|------|
| Coordonnées | `coord/` | Cellule de 3 × 3 blocs, conversions entre blocs, cellules et chunks, pile des layers |
| Graines | `noise/` | Graine du layout tirée du `NoiseConfig` de la dimension, puis une graine par layer, et le hachage `StageRandom` dont dérive tout tirage |
| Secteur | `sector/` | Labyrinthe brut de 240 × 135 cellules, gardé dans un cache LRU de 256 secteurs |
| Layout | `layout/` | Fenêtre de traversabilité du chunk, puis évaluation de chaque cellule |
| Écriture | `write/` | Pose des blocs, colonne par colonne |

`LevelZeroSectorGenerator` superpose des mini-labyrinthes, tracés par l'algorithme de Prim. Il tamponne ensuite des
salles rectangulaires, à piliers et polygonales. La graine du layout ne lit jamais directement la graine du monde : elle
passe par le dériveur nommé `gtgs_backrooms:backrooms_level_zero_layout`, ce qui isole le Level 0 des autres
générateurs.

La fenêtre de traversabilité déborde de deux cellules autour du chunk. Topologie, néons et resserrements sont ainsi
identiques des deux côtés d'une frontière de chunk.

`LevelZeroCellEvaluator` évalue chaque cellule dans un ordre figé :

1. type de salle, lu dans le secteur ;
2. grande pièce : environ 31 % des cellules sont candidates, et un arbitrage entre voisines empêche deux grandes
   pièces de se toucher, même en diagonale ;
3. biome cosmétique ;
4. topologie : mur, couloir, angle, jonction en T, carrefour, cul-de-sac ou grande pièce ;
5. néon, sur la trame en quinconce filtrée par le biome ;
6. resserrement d'un couloir droit bordé de murs, jamais sur une cellule éclairée ;
7. motif des 3 × 3 blocs de la cellule.

L'ordre compte. La topologie dépend du marquage des grandes pièces, et un néon interdit le resserrement.

La trame des néons allume une cellule sur deux, sur les rangs 0 et 2 de chaque bloc de quatre rangs, avec un décalage.
Deux néons ne sont donc jamais voisins, même en diagonale. Une grande pièce peut aussi rester sans néon, par zones de
24 cellules.

`LevelZeroBlockWriter` écrit chaque colonne d'un layer de bas en haut. Il pose la bedrock entre deux layers, puis le
sol, dans la moquette du biome. Une colonne ouverte reçoit ensuite un plafond, avec un néon au centre d'une cellule
éclairée. Une colonne fermée reçoit un mur et un plafond. Le mur visible est le mur adaptatif, le mur enfoui est de la
bedrock. Le chunk et l'échantillon de colonne demandé par le moteur passent par la même écriture (`BlockSink`).

### Génération verrouillée

La génération est déterministe. Tout tirage dérive de `StageRandom.mix(graine, étape, x, z)`, avec un sel figé par
étape.

`LevelZeroGoldenTest` verrouille le résultat. Il calcule une empreinte de la génération d'une zone de 7 × 7 chunks,
pour trois graines et cinq layers. L'empreinte couvre tout ce que l'écriture lit : évaluation de chaque cellule,
exposition des murs, masque du papier peint et détails muraux.

Une empreinte qui change modifie les mondes existants. Le changement doit être voulu et annoncé dans le
[CHANGELOG](../../CHANGELOG.md). On relance alors le test et on recopie les nouvelles valeurs dans
`LevelZeroGoldenTest`. Les sels de `StageRandom.Stage` et l'algorithme de `LevelZeroSectorGenerator` ne se modifient
jamais à la légère.

### Arrivée d'un portail

Le système de portails demande au Level 0 où poser un cadre d'arrivée (`LevelZeroArrival`). La recherche se fait au
layer 0, près des X/Z de l'entrée. Elle part de la cellule de ces X/Z et s'élargit par anneaux, jusqu'à 24 cellules.

- Un cadre debout se pose dans la cellule ouverte la plus proche où il tient sur toute sa largeur. Chaque colonne du
  cadre doit avoir un bloc libre devant et derrière elle. L'orientation préférée passe d'abord, puis les trois autres.
- Avec un sens de sortie imposé, le cadre garde son orientation et se pose là où il tient tel quel.
- Un cadre couché se pose là où toute son empreinte est libre.
- À défaut, le cadre se pose au centre de la cellule ouverte la plus proche, face à un bloc libre.

Un cadre debout ou au sol s'ancre en Y = 1, au ras du sol du layer 0. Un cadre au plafond s'ancre en Y = 4, sous le
plafond. La recherche lit le labyrinthe réellement généré, avec la zone sûre de l'origine et les resserrements, et non
la traversabilité brute des cellules. Sans cellule ouverte dans le rayon, elle ne rend aucune arrivée.

### Mur adaptatif

Le générateur pose `level_zero_wallpaper_adaptive` sur toute colonne de mur visible. Sa propriété `face_mask`, sur
4 bits, dit quelles faces montrent la variante blanche. Aucune block entity ne s'y ajoute, donc rien en NBT ni sur le
réseau.

Une face est blanche quand la première colonne d'air en face d'elle est dans le biome rouge. La sonde porte à 12 blocs.
Sans colonne d'air à cette portée, le biome se lit à 6 blocs. `LevelZeroWallSampler` calcule ce masque à la génération,
et court-circuite la sonde loin de toute frontière de région. Un mur posé à la main recalcule son masque depuis le
monde, côté serveur.

Côté client, `LevelZeroWallpaperBakedModel` dessine chaque face jaunie ou blanche selon le masque. Il ajoute par-dessus
les détails muraux de `LevelZeroWallProps` :

- une plinthe sur chaque face exposée de la rangée basse de chaque layer ;
- un interrupteur à deux blocs du sol, sur 4 colonnes exposées sur 1024 ;
- une prise à un bloc du sol, sur 6 colonnes exposées sur 1024.

Ces détails n'existent pas dans le monde. Le tirage est déterministe par colonne et ne dépend pas de la graine du monde.
`LevelZeroWallpaperModelHandler` installe ce modèle sur les 16 états du mur à chaque rechargement des ressources.

### Côté client

Le client n'a pas la graine du monde. Les biomes et les détails muraux n'en dépendent pas : il les recalcule seul.

À la connexion d'un joueur, le serveur lui envoie la graine du layout de chaque dimension dont le générateur est celui
du Level 0 (`LevelZeroLayoutSync`). Le client en tire la forme des salles pour l'acoustique générée
(`LevelZeroRoomShape`). Les colonnes ouvertes atteignables à 16 blocs autour d'un point forment la salle. Le moteur
audio en lit le libre parcours moyen d'un son et l'ouverture de la salle ([AUDIO.md](AUDIO.md)).

Le Level 0 a aussi un profil de look, `look_profiles/level_0.json` ([RENDU.md](RENDU.md)). Son profil audio,
`audio_profiles/level_0.json`, demande l'acoustique générée et le bourdonnement des néons.

## Performance

`./gradlew benchmarkLevelZeroGeneration` mesure la génération sur trois graines et 169 chunks par graine, avec les cinq
layers. Il fait deux passes de chauffe, puis la moyenne de cinq passes.

| Mesure, par chunk et pour cinq layers | PC de référence |
|---------------------------------------|-----------------|
| Layout, secteurs froids, génération des secteurs comprise | 0,187 ms |
| Layout, secteurs en cache | 0,016 ms |
| Layout et écriture des 256 colonnes | 0,129 ms |

Le dernier chiffre est proche du coût de `populateNoise`. Un chunk complet du Level 0 coûte donc environ 0,13 ms de
processeur hors moteur. L'option `-PlevelZeroPerfBudgetMsPerChunk=X` fait échouer la tâche au-delà de X ms par chunk.

## Réglages

Trois options règlent la génération : les resserrements des couloirs et deux affichages de débogage. Elles sont
décrites dans [OPTIONS.md](../reference/OPTIONS.md#level-0).

## Limites

- La destination automatique d'un portail mène toujours au layer 0. Les autres layers se rejoignent par une sortie
  fixée ou une commande.
- Aucune créature n'apparaît : le biome n'a pas d'apparition et le générateur ne crée aucune entité.
- Aucune structure faite à la main n'existe. Leur gabarit, `docs/backrooms/level0-manual-structures-template.json`,
  n'est pas lu par le jeu.

## Tests

- Les tests unitaires de `src/test/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/` suivent ses paquets : chaque
  étape de génération, le biome, le mur, l'arrivée, la forme des salles et le payload.
- `LevelZeroGoldenTest` verrouille les empreintes de génération.
- `LevelZeroGameTests` tourne sur un vrai serveur (`./gradlew runGameTest`). Le serveur de test ignore les dimensions
  de datapack : le test décode donc le JSON de la dimension avec les registres du serveur. Il génère ensuite le chunk
  d'origine et vérifie sol, air, plafond et bedrock sur les cinq layers.
- Le même fichier vérifie que seule une dimension du Level 0 envoie sa graine, et qu'un mur posé à la main recalcule
  ses faces.

Le fonctionnement des tests et de la couverture est dans [TESTS.md](../dev/TESTS.md).

## Fichiers

Les chemins `data/` et `assets/` partent de `src/main/resources/`.

| Fichier | Rôle |
|---------|------|
| `data/gtgs_backrooms/dimension/backrooms_level_0.json` | La dimension : son type, son générateur et son biome |
| `data/gtgs_backrooms/dimension_type/backrooms_level_0_type.json` | Le type de dimension |
| `data/gtgs_backrooms/worldgen/biome/backrooms_level_0.json` | Le biome unique de la dimension |
| `assets/gtgs_backrooms/look_profiles/level_0.json` | Le look du Level 0 |
| `assets/gtgs_backrooms/audio_profiles/level_0.json` | L'acoustique du Level 0 |
| `src/main/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/` | Le code du Level 0 |
| `docs/dimensions/backrooms-level0-dimension-type-multilayer.json` | Gabarit de référence du type de dimension à cinq layers, non chargé par le jeu |
| `docs/backrooms/level0-manual-structures-template.json` | Gabarit provisoire des futures structures manuelles, non chargé par le jeu |

Le gabarit de `docs/dimensions/` diffère du fichier actif. Ce dernier déclare les effets de dimension du mod et une
heure fixe.
