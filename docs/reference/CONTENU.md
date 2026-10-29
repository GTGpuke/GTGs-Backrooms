# Contenu

Le mod enregistre huit blocs, huit items et une entité, tous sous l'espace de noms `gtgs_backrooms`. Les registres sont
dans `core/registry/`. L'onglet créatif *GTG's Backrooms* (`gtgs_backrooms:main`) liste tous les items, la baguette de
portail en tête.

## Blocs

Les blocs sont enregistrés dans `ModBlocks`. Les textures des blocs du Level 0 font 32 × 32 pixels.

| Identifiant | Classe | Réglages copiés | Rôle |
|-------------|--------|-----------------|------|
| `level_zero_wallpaper` | `Block` | `end_stone` | Papier peint jauni, bloc décoratif |
| `level_zero_wallpaper_aged` | `Block` | `end_stone` | Papier peint blanc, dont le modèle sert aux faces blanches du mur adaptatif |
| `level_zero_wallpaper_adaptive` | `LevelZeroWallpaperBlock` | `end_stone` | Mur que le générateur pose sur toute colonne visible du Level 0 |
| `level_zero_damp_carpet` | `Block` | `yellow_wool` | Moquette humide, sol du biome de base |
| `level_zero_damp_carpet_aged` | `Block` | `yellow_wool` | Moquette rouge, sol du biome rouge |
| `level_zero_ceiling_tile` | `Block` | `calcite` | Dalle de plafond |
| `level_zero_fluorescent_light` | `Block` | `sea_lantern`, luminance 15 | Néon, au centre du plafond d'une cellule éclairée |
| `backrooms_portal` | `BackroomsPortalBlock` | Aucun | Bloc portail, posé par la baguette |

Chaque bloc a un blockstate, un modèle de bloc et, sauf le bloc portail, un item du même identifiant. En survie, un bloc
cassé donne son propre item, avec deux exceptions. Le mur adaptatif et le papier peint blanc donnent
`level_zero_wallpaper`.

### Mur adaptatif

Sa propriété `face_mask`, de 0 à 15, dit quelles faces horizontales montrent la variante blanche. Le générateur la
calcule, et un mur posé à la main la recalcule depuis le monde. Plinthes, interrupteurs et prises sont dessinés par son
modèle côté client, sans bloc en plus. Le détail est dans [LEVEL0.md](../systemes/LEVEL0.md#mur-adaptatif).

### Bloc portail

Le bloc portail est invisible et sans collision. Il a le son du verre, une dureté et une résistance de 0,5, et les
pistons ne le déplacent pas. Sa propriété `facing` oriente la face avant du portail, selon six directions. Sa block entity,
`backrooms_portal` (`BackroomsPortalBlockEntity`), garde ses réglages et pose ses portails.

Il n'a pas d'item : seule la baguette le pose. Il ne se vise qu'accroupi, pour le casser ou en copier les réglages.
Cassé, il ne rend rien (`dropsNothing`) et ses portails disparaissent avec lui. Son fonctionnement complet est dans
[PORTAILS.md](../systemes/PORTAILS.md).

## Items

Les items sont enregistrés dans `ModItems`.

| Identifiant | Classe | Rôle |
|-------------|--------|------|
| `portal_wand` | `PortalWandItem` | Baguette de portail, seule façon de créer un portail |
| `level_zero_wallpaper` | `BlockItem` | Item du bloc |
| `level_zero_wallpaper_aged` | `BlockItem` | Item du bloc |
| `level_zero_wallpaper_adaptive` | `BlockItem` | Item du bloc |
| `level_zero_damp_carpet` | `BlockItem` | Item du bloc |
| `level_zero_damp_carpet_aged` | `BlockItem` | Item du bloc |
| `level_zero_ceiling_tile` | `BlockItem` | Item du bloc |
| `level_zero_fluorescent_light` | `BlockItem` | Item du bloc |

### Baguette de portail

La baguette se tient à un exemplaire par pile, de rareté peu commune. Sa classe est dans `core/portal/item/`, son écran
dans `core/portal/client/wand/`. Sa texture fait 16 × 16 pixels, sur le modèle `item/handheld`.

Clic droit dans le vide, elle ouvre son écran de réglages. Clic droit sur un bloc, elle pose un bloc portail réglé
ainsi, sans s'user. Accroupi, un clic droit sur un bloc portail copie ses réglages. Chaque pose fait entendre
`item.portal_wand.place`. Ce que fait chaque réglage est décrit dans [PORTAILS.md](../systemes/PORTAILS.md).

Les réglages vivent dans le composant `gtgs_backrooms:portal_settings` (`PortalSettings`). Un champ absent prend sa
valeur par défaut, et un champ hors bornes est refusé.

| Champ | Valeurs | Défaut |
|-------|---------|--------|
| `width` | 1 à 8 | 1 |
| `height` | 1 à 8 | 2 |
| `transmits_light` | booléen | `true` |
| `double_sided` | booléen | `true` |
| `one_way` | booléen | `false` |
| `fleeting_exit` | booléen | `false` |
| `exit` | `straight`, `north`, `east`, `south`, `west`, `floor`, `ceiling` | `straight` |
| `exit_scatter` | 0 à 64 | 0 |
| `flush` | booléen | `false` |
| `upside_down` | booléen | `false` |
| `placement` | `look`, `standing`, `floor`, `ceiling` | `look` |
| `destination` | identifiant d'une dimension | absent, pour la destination automatique |
| `mark` | objet `dimension`, `pos`, `facing` | absent |

Une commande donne une baguette déjà réglée :

```mcfunction
/give @s gtgs_backrooms:portal_wand[gtgs_backrooms:portal_settings={width:4,height:3,transmits_light:false}]
```

## Entités

Les entités sont enregistrées dans `ModEntities`. Le mod n'a pas encore de créature.

| Identifiant | Classe | Rôle |
|-------------|--------|------|
| `backrooms_portal` | `BackroomsPortalEntity` | Une face d'un portail, posée par un bloc portail |

### Entité portail

| Propriété | Valeur |
|-----------|--------|
| Type | `SpawnGroup.MISC`, boîte de 0,1 × 0,1, suivie à 8 chunks, rafraîchie toutes les 20 ticks |
| Renderer | `BackroomsPortalEntityRenderer` : un cadre sombre quand le rendu distant manque, rien sinon |
| Données synchronisées | Orientations d'entrée et d'arrivée, dimension et position d'arrivée, taille, visibilité, ouverture, lumière transmise, sortie fugace |
| Données serveur | Bloc portail propriétaire : dimension et position |

Une block entity de bloc portail crée ses portails : une ou deux faces dos à dos dans le cadre, autant à l'arrivée sauf
en aller simple. `BackroomsPortalEntity.builder(world)` crée un portail libre, sans bloc.

Un portail ne bouge jamais et ne peut pas être frappé. Toutes les 100 ticks, le registre serveur retire les portails
orphelins, si le chunk de leur bloc est chargé. Un portail est orphelin quand son bloc a disparu, ne le tient plus pour
sien, ou que sa dimension n'existe plus.

Le type reste enregistré même portails coupés. Un monde qui contient des portails se charge donc toujours.

## Autres enregistrements

| Registre | Identifiant | Rôle |
|----------|-------------|------|
| `ModBlockEntities` | `backrooms_portal` | Block entity du bloc portail |
| `ModDataComponents` | `portal_settings` | Réglages d'un portail, portés par la baguette |
| `ModChunkGenerators` | `backrooms_level_zero` | Générateur du Level 0 |
| `ModCreativeTab` | `main` | Onglet créatif, à l'icône du papier peint jauni |

Les sons sont enregistrés dans `ModSounds` et déclarés dans `assets/gtgs_backrooms/sounds.json`, chacun avec un
sous-titre traduit. Chacun renvoie à un son vanilla, en attendant le sien.

| Son | Rôle | Son vanilla joué |
|-----|------|------------------|
| `item.portal_wand.place` | Un portail s'ouvre sous la baguette | `block.end_portal_frame.fill` |
| `block.neon.hum` | Bourdonnement d'un néon | `block.beacon.ambient` |
| `block.neon.click_off` | Néon qui s'éteint | `block.lever.click` |
| `block.neon.click_on` | Néon qui se rallume | `block.lever.click` |
| `entity.howler.scream` | Cri d'une créature des Backrooms, lié à l'image | `entity.enderman.scream` |

## Ajouter un bloc

1. Sa classe, s'il en a une, va dans `backrooms/<niveau>/block/` ou `core/<système>/block/`, selon le tableau
   [Où ranger quoi](../systemes/ARCHITECTURE.md#où-ranger-quoi).
2. Déclarer un champ `static final` dans `ModBlocks`, puis son `BlockItem` dans `ModItems`, puis l'entrée dans
   `ModCreativeTab.ENTRIES`.
3. Créer `blockstates/<id>.json`, `models/block/<id>.json`, `models/item/<id>.json` et la texture.
4. Créer la loot table `data/gtgs_backrooms/loot_table/blocks/<id>.json`, sauf pour un bloc `dropsNothing()`.
5. Ajouter la clé `block.gtgs_backrooms.<id>` dans `en_us.json` et `fr_fr.json`. L'item du bloc la reprend : aucune clé
   `item.*` pour lui.
6. Mettre à jour le nombre de blocs et d'items attendu par `ModRegistriesTest`.
7. Lancer `./gradlew test`. `ModRegistriesTest` échoue tant qu'un fichier ou une traduction manque.

## Ajouter un item

1. Sa classe, s'il en a une, va dans `backrooms/<niveau>/item/` ou `core/<système>/item/`.
2. Déclarer un champ `static final` dans `ModItems`, puis l'entrée dans `ModCreativeTab.ENTRIES`.
3. Créer `models/item/<id>.json`, de parent `item/generated` ou `item/handheld`, et une texture de 16 × 16 pixels.
4. Ajouter la clé `item.gtgs_backrooms.<id>` en anglais et en français.
5. Mettre à jour le nombre d'items attendu par `ModRegistriesTest`.
6. Tester le comportement de l'item dans le paquet miroir de `src/test/java/` ou `src/gametest/java/`.

## Ajouter une entité

1. Sa classe va dans `backrooms/<niveau>/entity/` ou `core/<système>/entity/`.
2. Déclarer son `EntityType` en champ `static final` dans `ModEntities`. Une créature y enregistre aussi ses attributs.
3. Écrire son renderer dans le sous-paquet `client/entity/` de son domaine. L'enregistrer dans
   `GtgsBackroomsClientMod`, par `EntityRendererRegistry`.
4. Ajouter sa texture et la clé `entity.gtgs_backrooms.<id>` en anglais et en français.
5. Tester son comportement en test unitaire ou en jeu. Le renderer suit les règles du code client
   ([TESTS.md](../dev/TESTS.md)).

Pour chaque ajout, mettre à jour ce document et le [CHANGELOG](../../CHANGELOG.md).
