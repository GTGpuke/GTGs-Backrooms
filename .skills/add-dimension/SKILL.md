---
name: add-dimension
description: "Ajouter une dimension au mod GTG's Backrooms (Fabric 1.21.1). Déclenche pour 'dimension', 'monde', 'portail', 'téléportation', 'monde custom', 'realm', 'dimension custom'."
---

# Skill — Ajouter une Dimension (Fabric 1.21.1)

## Quand utiliser ce skill

- "Ajoute une dimension [nom]"
- "Crée un monde custom [description]"
- "Nouveau portail vers [destination]"

---

## Étapes

### 1. Définir les paramètres

| Paramètre | Valeur |
|-----------|--------|
| `DIMENSION_ID` | `backrooms_level_1`, `backrooms_level_2` |
| Biome | custom ou vanilla (`minecraft:plains`, etc.) |
| Type de dimension | normal, nether-like, end-like |
| Portail | bloc de portail custom ou téléportation par commande |
| Génération | flat, void, custom chunk generator |

---

### 2. Fichiers JSON (data pack)

Les dimensions dans Fabric sont entièrement définies par des fichiers JSON dans `data/`.

**Structure :**
```
data/gtgs_backrooms/
├── dimension/
│   └── backrooms_level_1.json           ← définit la dimension
└── dimension_type/
    └── backrooms_level_1_type.json      ← type (lumière, brouillard, hauteur)
```

---

### 3. dimension_type JSON

**Fichier :** `data/gtgs_backrooms/dimension_type/backrooms_level_1_type.json`

```json
{
  "ultrawarm": false,
  "natural": true,
  "coordinate_scale": 1.0,
  "has_skylight": true,
  "has_ceiling": false,
  "ambient_light": 0.0,
  "monster_spawn_light_level": {
    "type": "minecraft:uniform",
    "value": { "min_inclusive": 0, "max_inclusive": 7 }
  },
  "monster_spawn_block_light_limit": 0,
  "piglin_safe": false,
  "bed_works": true,
  "respawn_anchor_works": false,
  "has_raids": false,
  "min_y": -64,
  "height": 384,
  "logical_height": 384,
  "infiniburn": "#minecraft:infiniburn_overworld",
  "effects": "minecraft:overworld"
}
```

---

### 4. dimension JSON

**Fichier :** `data/gtgs_backrooms/dimension/backrooms_level_1.json`

```json
{
  "type": "gtgs_backrooms:backrooms_level_1_type",
  "generator": {
    "type": "minecraft:noise",
    "biome_source": {
      "type": "minecraft:fixed",
      "biome": "minecraft:plains"
    },
    "settings": "minecraft:overworld"
  }
}
```

---

### 5. Y mener

Le système de portails (`core/portal/`) mène aujourd'hui du monde normal au Level 0 et retour : sa destination est
aiguillée par `core/portal/block/PortalTargets`, et c'est le Level 0 qui sait où l'on arrive chez lui
(`backrooms/level0/LevelZeroArrival`). Mener à un nouveau niveau : sa propre classe d'arrivée dans son paquet
(`backrooms/<niveau>/`), et une branche de plus dans l'aiguillage de `PortalTargets`, sans réécrire sa logique (règle
d'or de `docs/GUIDE.md`), après avoir lu `docs/systemes/PORTAILS.md`. En attendant, `/execute in gtgs_backrooms:backrooms_level_1 run teleport @s 0 64 0` suffit pour
tester.

---

### 6. Enregistrement automatique

Les dimensions JSON dans `data/` sont automatiquement reconnues par Minecraft/Fabric.
Aucune registration Java supplémentaire n'est nécessaire pour une dimension basique.

Pour un chunk generator custom (comme `LevelZeroChunkGenerator`), il faut enregistrer son CODEC dans
`core/registry/ModChunkGenerators.java`, sur le modèle de celui du Level 0 :
```java
Registry.register(
        Registries.CHUNK_GENERATOR,
        Identifier.of(ModConstants.MOD_ID, BackroomsConstants.LEVEL_ONE_CHUNK_GENERATOR),
        LevelOneChunkGenerator.CODEC);
```

Le générateur d'un niveau et ses aides vivent dans `backrooms/<niveau>/generation/` (comme
`backrooms/level0/generation/`), ses blocs dans `backrooms/<niveau>/block/` ; les identifiants partagés dans
`backrooms/BackroomsConstants`.

---

## Checklist finale

- [ ] `data/gtgs_backrooms/dimension_type/my_dim_type.json`
- [ ] `data/gtgs_backrooms/dimension/my_dim.json`
- [ ] Biome custom si nécessaire
- [ ] Accès par les portails si souhaité : classe d'arrivée du niveau et branche dans `PortalTargets`, après lecture de `docs/systemes/PORTAILS.md`
- [ ] Chunk generator custom enregistré dans `core/registry/ModChunkGenerators.java` si nécessaire
- [ ] Lang key pour le nom de la dimension
- [ ] Document du niveau dans `docs/systemes/` (sur le modèle de `LEVEL0.md`) + `CHANGELOG.md` mis à jour
- [ ] Testé en jeu : `/execute in gtgs_backrooms:my_dim run teleport @s 0 64 0`
