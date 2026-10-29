---
name: add-item
description: "Ajouter un nouvel item au mod GTG's Backrooms (Fabric 1.21.1). Utilise ce skill dès qu'on veut créer un item, outil, arme, consommable, ou objet. Déclenche pour 'ajoute', 'crée', 'nouveau' + 'item', 'objet', 'outil', 'arme', 'nourriture', 'carte', 'clé', 'épée', 'pioche', 'hache', 'arc'."
---

# Skill — Ajouter un Item (Fabric 1.21.1)

## Quand utiliser ce skill

- "Ajoute un item [nom]"
- "Crée un objet [description]"
- "Nouveau [outil/arme/consommable] appelé [nom]"
- "Je veux un item qui [comportement]"

---

## Étapes

### 1. Définir les paramètres

| Paramètre | Exemples |
|-----------|---------|
| `ITEM_ID` (snake_case) | `almond_water`, `level_key`, `exit_map` |
| `ClassName` (PascalCase) | `AlmondWaterItem`, `LevelKeyItem`, `ExitMapItem` |
| Domaine | un niveau (`backrooms/level0/`) ou un système du socle (`core/portal/`) |
| Stack max | `1` (unique) ou `64` (stackable) |
| Rareté | `Rarity.COMMON`, `Rarity.UNCOMMON`, `Rarity.RARE`, `Rarity.EPIC` |
| Comportement custom | tooltip, glint, durabilité, nourriture, etc. |
| Nom EN / Nom FR | "Almond Water" / "Eau d'Amande" |

---

### 2. Créer la classe item (si elle a un comportement)

Sa classe va dans le paquet de son domaine (tableau « Où ranger quoi » de `docs/systemes/ARCHITECTURE.md`) :

- objet d'un niveau des Backrooms : `backrooms/<niveau>/item/` (par exemple `backrooms/level0/item/AlmondWaterItem.java`) ;
- objet d'un système du socle : `core/<système>/item/` (la baguette de portail : `core/portal/item/PortalWandItem.java`).

Le paquet `item/` d'un niveau naît avec son premier item : pas de dossier vide.

**Fichier :** `src/main/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/item/AlmondWaterItem.java`

```java
package com.gtgpuke.gtgsbackrooms.backrooms.level0.item;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Eau d'amande du Level 0 : [description courte].
 */
public final class AlmondWaterItem extends Item {

    private static final String TOOLTIP = "tooltip." + ModConstants.MOD_ID + ".almond_water";

    public AlmondWaterItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable(TOOLTIP).formatted(Formatting.GRAY));
    }

    // Override hasGlint() → true pour le glint enchantement.
    // Override finishUsing() pour l'effet à la consommation.
    // Override use() pour le clic droit, useOnBlock() pour le clic sur un bloc.
}
```

**Pour un item simple sans comportement custom :** pas de classe, `new Item(...)` directement dans `ModItems`.

---

### 3. Enregistrer dans ModItems.java

**Fichier :** `src/main/java/com/gtgpuke/gtgsbackrooms/core/registry/ModItems.java`

```java
/** Eau d'amande du Level 0 : [description]. */
public static final AlmondWaterItem ALMOND_WATER = register("almond_water", new AlmondWaterItem(
        new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));
```

**Important :**
- Le champ contient directement l'objet, pas un `RegistryObject` : `ModItems.ALMOND_WATER`, jamais `.get()`.
- `register(name, item)` est l'aide privée de `ModItems` (`Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), item)`).

---

### 4. Ajouter à l'onglet créatif

**Fichier :** `src/main/java/com/gtgpuke/gtgsbackrooms/core/registry/ModCreativeTab.java`

Ajouter `ModItems.ALMOND_WATER` à la liste `ENTRIES` : l'onglet du mod se construit à partir d'elle.
`ModRegistriesTest` vérifie que chaque item du mod y figure une fois.

---

### 5. Créer le modèle JSON

**Fichier :** `src/main/resources/assets/gtgs_backrooms/models/item/almond_water.json`

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "gtgs_backrooms:item/almond_water"
  }
}
```

Pour un outil/arme (tenu à la main) : utiliser `"parent": "item/handheld"`.

> En 1.21.1, seul `models/item/<id>.json` compte : les définitions `assets/<ns>/items/*.json` n'existent qu'à partir de 1.21.4.

---

### 6. Ajouter la texture

**Fichier :** `src/main/resources/assets/gtgs_backrooms/textures/item/almond_water.png`

- Format : PNG, 16×16 px, RGBA, création originale (aucune ressource reprise d'un projet tiers : licence du dépôt).

---

### 7. Ajouter les traductions

**Fichier :** `src/main/resources/assets/gtgs_backrooms/lang/en_us.json`
```json
"item.gtgs_backrooms.almond_water": "Almond Water",
"tooltip.gtgs_backrooms.almond_water": "Tastes like safety."
```

**Fichier :** `src/main/resources/assets/gtgs_backrooms/lang/fr_fr.json`
```json
"item.gtgs_backrooms.almond_water": "Eau d'Amande",
"tooltip.gtgs_backrooms.almond_water": "Un goût de sécurité."
```

---

### 8. Ajouter une recette (optionnel)

Voir le skill `add-recipe` (dossier `data/gtgs_backrooms/recipe/`, au singulier en 1.21).

---

### 9. Écrire le test

**Fichier :** `src/test/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/item/AlmondWaterItemTest.java` (paquet miroir)

Un test de comportement par méthode (pas de test par réflexion), sous le classloader Fabric avec les vrais registres :

```java
@DisplayName("Eau d'amande")
class AlmondWaterItemTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensure();
    }

    @Test
    @DisplayName("L'eau d'amande s'empile par seize et n'est pas commune")
    void properties() {
        ItemStack stack = new ItemStack(ModItems.ALMOND_WATER);
        assertEquals(16, ModItems.ALMOND_WATER.getMaxCount(), "Pile de seize.");
        assertEquals(Rarity.UNCOMMON, stack.get(DataComponentTypes.RARITY), "Rarete UNCOMMON.");
    }
}
```

Un comportement qui demande un monde ou un joueur (clic droit, consommation) se teste en jeu, dans
`src/gametest/java/com/gtgpuke/gtgsbackrooms/gametest/<domaine>/` (classe déclarée dans `src/gametest/resources/fabric.mod.json`).
Modèle, texture, traductions EN et FR et onglet créatif sont vérifiés automatiquement par `ModRegistriesTest` et
`ModCreativeTabTest` (`src/test/java/com/gtgpuke/gtgsbackrooms/core/registry/`). La couverture fusionnée exige que chaque
instruction et chaque branche de la classe soient exécutées par un test unitaire ou un test en jeu (`docs/dev/TESTS.md`).

---

### 10. Mettre à jour la documentation

- [ ] `docs/reference/CONTENU.md` — ajouter la fiche de l'item
- [ ] `CHANGELOG.md` — ajouter sous `### Added`
- [ ] Build : `./gradlew build --warning-mode all`

---

## Checklist finale

- [ ] Classe dans `backrooms/<niveau>/item/` ou `core/<système>/item/` (ou pas de classe si item simple)
- [ ] `core/registry/ModItems.java` — champ `static final` ajouté
- [ ] `core/registry/ModCreativeTab.java` — item ajouté à `ENTRIES`
- [ ] `models/item/<id>.json` créé
- [ ] `textures/item/<id>.png` créé (16×16)
- [ ] `lang/en_us.json` et `lang/fr_fr.json` mis à jour
- [ ] Recette ajoutée (si applicable)
- [ ] Test de comportement ajouté (paquet miroir) ; `ModRegistriesTest` vert
- [ ] `docs/reference/CONTENU.md` et `CHANGELOG.md` mis à jour
- [ ] `./gradlew build --warning-mode all` passe sans erreur ni warning
