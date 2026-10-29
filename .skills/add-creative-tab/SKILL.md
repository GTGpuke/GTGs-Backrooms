---
name: add-creative-tab
description: "Ajouter ou modifier un onglet créatif dans GTG's Backrooms (Fabric 1.21.1). Déclenche pour 'onglet créatif', 'creative tab', 'catégorie d'items', 'nouvel onglet', 'déplacer item dans tab', 'changer l'icône du tab'."
---

# Skill — Ajouter / Modifier un Creative Tab (Fabric 1.21.1)

## Quand utiliser ce skill

- "Ajoute un item à l'onglet du mod"
- "Change l'icône de l'onglet"
- "Crée une catégorie séparée pour [type de contenu]"

---

## L'onglet du mod

**Fichier :** `src/main/java/com/gtgpuke/gtgsbackrooms/core/registry/ModCreativeTab.java`

Le mod a un seul onglet, *GTG's Backrooms* (`ModCreativeTab.TAB`, identifiant `gtgs_backrooms:main`), construit à
partir de la liste ordonnée `ENTRIES` : la baguette de portail en tête, puis les blocs du Level 0. Le bloc portail n'a
pas d'item : on crée un portail avec la baguette.

### Ajouter un item

Ajouter `ModItems.MY_ITEM` à `ENTRIES`, à sa place dans l'ordre. `ModRegistriesTest` exige que chaque item du mod y
figure une fois ; `ModCreativeTabTest` vérifie l'ordre affiché.

**Pas de `.get()`** — les champs `ModItems.*` sont directement les instances d'`Item`.

### Changer l'icône

```java
.icon(() -> new ItemStack(ModItems.LEVEL_ZERO_WALLPAPER))
```

`ModCreativeTabTest` vérifie l'icône : l'ajuster avec.

---

## Créer un second onglet

Seulement si le contenu le justifie (un second onglet pour un ou deux items n'aide pas le joueur). Un champ de plus dans
`ModCreativeTab`, sur le modèle de `TAB` :

```java
/** Items de l'onglet des niveaux, dans l'ordre. */
public static final List<Item> LEVEL_ENTRIES = List.of(ModItems.SOME_ITEM);

/** Onglet des niveaux. */
public static final ItemGroup LEVEL_TAB = Registry.register(
        Registries.ITEM_GROUP,
        Identifier.of(ModConstants.MOD_ID, "levels"),
        FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup." + ModConstants.MOD_ID + ".levels"))
                .icon(() -> new ItemStack(ModItems.SOME_ITEM))
                .entries((context, output) -> LEVEL_ENTRIES.forEach(output::add))
                .build());
```

Puis : adapter `ModRegistriesTest` (chaque item dans un onglet, une fois) et `ModCreativeTabTest` (nombre d'onglets),
et ajouter la clé `itemGroup.gtgs_backrooms.levels` en EN et FR. `ModCreativeTab.register()` est déjà appelé par
`GtgsBackroomsMod.registerContent()`.

---

## Ajouter des items dans un onglet vanilla

Pour injecter des items dans un onglet vanilla (outils, combat, etc.) sans créer d'onglet :

```java
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;

// Dans ModCreativeTab.register() :
ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ModItems.MY_ITEM));
```

---

## Checklist finale

- [ ] Item ajouté à `ENTRIES` (ou à la liste de son onglet)
- [ ] `FabricItemGroup.builder()` utilisé, `.displayName(Text.translatable(...))`
- [ ] Champs `ModItems.*` utilisés **sans** `.get()`
- [ ] Clé de langue EN + FR (nouvel onglet)
- [ ] `ModRegistriesTest` et `ModCreativeTabTest` verts
- [ ] `docs/reference/CONTENU.md` et `CHANGELOG.md` mis à jour
- [ ] `./gradlew build --warning-mode all` passe
- [ ] Vérifié en jeu (onglet visible en mode créatif)
