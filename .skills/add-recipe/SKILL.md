---
name: add-recipe
description: "Ajouter une recette au mod GTG's Backrooms (Fabric 1.21.1). Déclenche pour 'recette', 'craft', 'fabriquer', 'fondre', 'cuisiner', 'table de craft', 'fourneau', 'smithing', 'recipe'."
---

# Skill — Ajouter une Recette (Fabric 1.21.1)

## Quand utiliser ce skill

- "Ajoute une recette de craft pour [item]"
- "Le [item] se fabrique avec [ingrédients]"
- "Fais fondre [item] dans un fourneau"

---

## Types de recettes

| Type | JSON type | Usage |
|------|-----------|-------|
| Craft ordonné | `minecraft:crafting_shaped` | Table de craft avec pattern |
| Craft libre | `minecraft:crafting_shapeless` | Ordre non important |
| Fusion fourneau | `minecraft:smelting` | Fourneau standard |
| Fusion blast | `minecraft:blasting` | Blast furnace (minerais) |
| Fumage | `minecraft:smoking` | Smoker (nourriture) |
| Campfire | `minecraft:campfire_cooking` | |
| Smithing | `minecraft:smithing_transform` | Upgrade via enclume smithing |
| Stonecutter | `minecraft:stonecutting` | Tailleur de pierre |

---

## Emplacement des fichiers

```
data/gtgs_backrooms/recipe/
└── my_item.json
```

> **Important MC 1.21.1 :** le répertoire est `recipe/` (singulier), pas `recipes/`.

**Convention :** un fichier par item crafté. Le nom du fichier = l'ID de l'item résultant.

---

## Templates

### Craft ordonné (3x3)

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": [
    "ABA",
    "BCB",
    "ABA"
  ],
  "key": {
    "A": { "item": "minecraft:gold_ingot" },
    "B": { "item": "minecraft:diamond" },
    "C": { "item": "minecraft:nether_star" }
  },
  "result": {
    "id": "gtgs_backrooms:my_item",
    "count": 1
  }
}
```

### Craft libre (shapeless)

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    { "item": "minecraft:gold_ingot" },
    { "item": "minecraft:gold_ingot" },
    { "item": "minecraft:diamond" }
  ],
  "result": {
    "id": "gtgs_backrooms:my_item",
    "count": 1
  }
}
```

### Fusion fourneau

```json
{
  "type": "minecraft:smelting",
  "category": "misc",
  "ingredient": { "item": "gtgs_backrooms:raw_level_zero_ore" },
  "result": { "id": "gtgs_backrooms:level_zero_ingot" },
  "experience": 1.0,
  "cookingtime": 200
}
```

### Blast furnace (minerai)

```json
{
  "type": "minecraft:blasting",
  "category": "misc",
  "ingredient": { "item": "gtgs_backrooms:level_zero_ore" },
  "result": { "id": "gtgs_backrooms:level_zero_ingot" },
  "experience": 0.7,
  "cookingtime": 100
}
```

### Stonecutter

```json
{
  "type": "minecraft:stonecutting",
  "ingredient": { "item": "gtgs_backrooms:my_block" },
  "result": { "id": "gtgs_backrooms:my_slab" },
  "count": 2
}
```

---

## Checklist finale

- [ ] Fichier JSON créé dans `data/gtgs_backrooms/recipe/` (singulier)
- [ ] Champ `"result"` utilise `"id"` (pas `"item"`) — format MC 1.21.1
- [ ] Ingrédients corrects (vérifier les item IDs)
- [ ] Résultat correct (vérifier l'item ID)
- [ ] Testé en jeu : `/recipe give @p gtgs_backrooms:my_item`
- [ ] `docs/reference/CONTENU.md` mis à jour (mentionner la recette)
- [ ] `CHANGELOG.md` mis à jour
