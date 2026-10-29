---
name: add-block
description: "Ajouter un bloc au mod GTG's Backrooms (Fabric 1.21.1). Déclenche pour 'bloc', 'minerai', 'dalle', 'mur', 'escalier', 'porte', 'bloc décoratif', 'bloc fonctionnel', 'ore' ou 'block'."
---

# Skill — Ajouter un Bloc (Fabric 1.21.1)

## Quand utiliser ce skill

- "Ajoute un bloc [nom]"
- "Crée un minerai [nom]"
- "Nouveau bloc décoratif [description]"

## Étapes

### 1. Définir les paramètres

| Paramètre | Valeur |
|-----------|--------|
| `BLOCK_ID` | `level_zero_vent`, `level_one_pipe` |
| `ClassName` | `LevelZeroVentBlock`, `LevelOnePipeBlock` |
| Domaine | un niveau (`backrooms/level0/`) ou un système du socle (`core/portal/`) |
| Hardness | ex. `3.0f` |
| Blast resistance | ex. `3.0f` |
| Son | `BlockSoundGroup.STONE`, `WOOD`, `METAL`, `GLASS` |
| Drops | lui-même, silk touch, fortune, loot table |

### 2. Créer la classe bloc si un comportement custom est nécessaire

Sa classe va dans le paquet de son domaine (tableau « Où ranger quoi » de `docs/systemes/ARCHITECTURE.md`) :
`backrooms/<niveau>/block/` pour un bloc d'un niveau (comme `backrooms/level0/block/LevelZeroWallpaperBlock`),
`core/<système>/block/` pour un bloc d'un système du socle (comme `core/portal/block/BackroomsPortalBlock`).

**Fichier :** `src/main/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/block/LevelZeroVentBlock.java`

```java
package com.gtgpuke.gtgsbackrooms.backrooms.level0.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;

/**
 * Grille d'aeration du Level 0 : [description].
 */
public final class LevelZeroVentBlock extends Block {

    public LevelZeroVentBlock(AbstractBlock.Settings settings) {
        super(settings);
    }
}
```

Pour un bloc simple, utiliser directement `Block` dans `ModBlocks`.

### 3. Enregistrer le bloc dans `core/registry/ModBlocks.java`

```java
/** Grille d'aeration du Level 0. */
public static final LevelZeroVentBlock LEVEL_ZERO_VENT = register(
        "level_zero_vent",
        new LevelZeroVentBlock(AbstractBlock.Settings.copy(Blocks.IRON_BARS)));
```

`register(name, block)` est l'aide privée de `ModBlocks` (`Registry.register(Registries.BLOCK, ...)`).

### 4. Ajouter le `BlockItem` dans `core/registry/ModItems.java`

```java
public static final BlockItem LEVEL_ZERO_VENT = blockItem("level_zero_vent", ModBlocks.LEVEL_ZERO_VENT);
```

L'item du bloc lit les clés de langue du bloc (`block.*`) : pas de clé `item.*` pour lui (`ModRegistriesTest` le vérifie).

### 5. Créer les ressources

- `assets/<modid>/blockstates/level_zero_vent.json`
- `assets/<modid>/models/block/level_zero_vent.json`
- `assets/<modid>/models/item/level_zero_vent.json` (en 1.21.1, les définitions `items/*.json` n'existent pas encore)
- `assets/<modid>/textures/block/level_zero_vent.png` (création originale)
- `data/<modid>/loot_table/blocks/level_zero_vent.json`

### 6. Résolution des textures

- `16×16` pour les blocs standards.
- `32×32` pour les blocs du Level 0.

### 7. Finaliser

- Ajouter la clé `block.gtgs_backrooms.<id>` en EN et FR.
- Ajouter l'item à la liste `ENTRIES` de `core/registry/ModCreativeTab.java`.
- Mettre à jour `docs/reference/CONTENU.md` et `CHANGELOG.md`.
- Tester le comportement propre au bloc dans le paquet miroir de `src/test/java/` (ou en jeu).

## Checklist finale

- [ ] Classe dans `backrooms/<niveau>/block/` ou `core/<système>/block/` (ou pas de classe si bloc simple)
- [ ] Bloc enregistré dans `core/registry/ModBlocks.java`
- [ ] BlockItem enregistré dans `core/registry/ModItems.java`, ajouté à `ModCreativeTab.ENTRIES`
- [ ] Blockstate, modèle, item model, texture et loot table présents (`ModRegistriesTest` les vérifie)
- [ ] Traductions EN et FR présentes
- [ ] `docs/reference/CONTENU.md` et `CHANGELOG.md` mis à jour
- [ ] `./gradlew build --warning-mode all` passe
