---
name: add-sound
description: "Ajouter un son au mod GTG's Backrooms (Fabric 1.21.1). Déclenche pour 'son', 'audio', 'musique', 'bruit', 'ambiance', 'sfx', 'sound', 'play sound'."
---

# Skill — Ajouter un Son (Fabric 1.21.1)

## Quand utiliser ce skill

- "Ajoute un son [nom] qui joue quand [action]"
- "Crée un son custom [description]"
- "Le [item/bloc] doit faire un bruit [description]"

Un son joué dans un monde traverse déjà les portails (relais serveur, rejeu au cadre : `docs/systemes/PORTAILS.md`) : rien à
faire pour qu'on l'entende de l'autre côté. Le traitement avancé (environnements, occlusion) relève du futur système
audio (`docs/GUIDE.md`), pas d'un son isolé.

---

## Étapes

### 1. Préparer le fichier audio

- Format : **OGG Vorbis** (.ogg) — seul format supporté par Minecraft
- Sample rate : 44100 Hz recommandé
- Mono pour les sons positionnels in-world
- Création originale (aucune ressource reprise d'un projet tiers : licence du dépôt)
- Placer dans `src/main/resources/assets/gtgs_backrooms/sounds/<domaine>/`, rangé comme le code :

```
assets/gtgs_backrooms/sounds/
├── level0/
│   └── fluorescent_hum.ogg
└── portal/
    └── wand_configure.ogg
```

---

### 2. Créer/Mettre à jour sounds.json

**Fichier :** `src/main/resources/assets/gtgs_backrooms/sounds.json`

```json
{
  "block.level_zero_fluorescent_light.hum": {
    "subtitle": "subtitles.gtgs_backrooms.block.level_zero_fluorescent_light.hum",
    "sounds": [
      {
        "name": "gtgs_backrooms:level0/fluorescent_hum",
        "volume": 1.0,
        "pitch": 1.0,
        "weight": 1
      }
    ]
  }
}
```

La clé (`"block.level_zero_fluorescent_light.hum"`) est le chemin de l'identifiant du `SoundEvent`
(`gtgs_backrooms:block.level_zero_fluorescent_light.hum`).

---

### 3. Enregistrer le son dans `core/registry/ModSounds.java`

Le registre des sons existe (son premier son : `PORTAL_WAND_PLACE`, un portail qui s'ouvre) : ajouter un champ.

```java
/** Bourdonnement d'un neon du Level 0. */
public static final SoundEvent LEVEL_ZERO_FLUORESCENT_HUM = register("block.level_zero_fluorescent_light.hum");
```

`register(path)` est l'aide privée de `ModSounds` (`Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id))`) ;
`ModSounds.register()` est déjà appelé par `GtgsBackroomsMod.registerContent()`. `ModRegistriesTest` vérifie que chaque son
enregistré est déclaré dans `sounds.json` et que son sous-titre est traduit.

Un son peut aussi rejouer un son vanilla sans fichier audio : dans `sounds.json`, `{"name": "minecraft:<son>", "type": "event"}`.

---

### 4. Jouer le son en jeu

```java
// Cote serveur : tous les joueurs proches l'entendent, et ceux qui voient ce lieu par un portail aussi.
world.playSound(null, pos, ModSounds.LEVEL_ZERO_FLUORESCENT_HUM, SoundCategory.BLOCKS, 1.0f, 1.0f);
```

---

### 5. Traductions des sous-titres

```json
// en_us.json
"subtitles.gtgs_backrooms.block.level_zero_fluorescent_light.hum": "Fluorescent light hums",

// fr_fr.json
"subtitles.gtgs_backrooms.block.level_zero_fluorescent_light.hum": "Un néon bourdonne",
```

---

## Checklist finale

- [ ] Fichier `.ogg` placé dans `assets/gtgs_backrooms/sounds/<domaine>/`
- [ ] `sounds.json` créé/mis à jour
- [ ] `core/registry/ModSounds.java` — champ `static final` ajouté
- [ ] Son joué depuis le code approprié avec `world.playSound()`, couvert par un test
- [ ] Sous-titres ajoutés en EN + FR
- [ ] `CHANGELOG.md` mis à jour
- [ ] Testé en jeu (sous-titres visibles si activés, son entendu à travers un portail)
