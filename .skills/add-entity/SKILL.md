---
name: add-entity
description: "Ajouter une entité/mob au mod GTG's Backrooms (Fabric 1.21.1). Déclenche pour 'mob', 'entité', 'monstre', 'boss', 'NPC', 'créature', 'animal', 'pet', 'entity'."
---

# Skill — Ajouter une Entité / Mob (Fabric 1.21.1)

## Quand utiliser ce skill

- "Ajoute un mob [nom]"
- "Crée une entité [description]"
- "Nouveau boss [nom]"

---

## Étapes

### 1. Définir les paramètres

| Paramètre | Valeur |
|-----------|--------|
| `ENTITY_ID` | `smiler`, `hound` |
| `ClassName` | `SmilerEntity`, `HoundEntity` |
| Domaine | le niveau où elle vit (`backrooms/level0/`), ou un système du socle (`core/portal/` pour l'entity portail) |
| Catégorie | `SpawnGroup.CREATURE` / `MONSTER` / `AMBIENT` / `WATER_CREATURE` |
| HP | `20.0f` (10 cœurs) |
| Taille | `dimensions(0.6f, 1.95f)` (humanoïde) |

---

### 2. Créer la classe entité

Sa classe va dans le paquet de son domaine (tableau « Où ranger quoi » de `docs/systemes/ARCHITECTURE.md`) :
`backrooms/<niveau>/entity/` pour une créature d'un niveau, `core/<système>/entity/` pour une entité d'un système du
socle (comme `core/portal/entity/BackroomsPortalEntity`).

**Fichier :** `src/main/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/entity/SmilerEntity.java`

```java
package com.gtgpuke.gtgsbackrooms.backrooms.level0.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

/**
 * Smiler du Level 0 : [description].
 */
public final class SmilerEntity extends PathAwareEntity {

    public SmilerEntity(EntityType<? extends SmilerEntity> type, World world) {
        super(type, world);
    }

    /** Attributs du smiler : vie, vitesse, degats. */
    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }
}
```

---

### 3. Enregistrer le type dans `core/registry/ModEntities.java`

Le registre existe déjà (il tient le type de l'entity portail) : ajouter un champ.

```java
/** Smiler du Level 0. */
public static final EntityType<SmilerEntity> SMILER = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(ModConstants.MOD_ID, "smiler"),
        EntityType.Builder.create(SmilerEntity::new, SpawnGroup.MONSTER).dimensions(0.6f, 1.95f).build());
```

Puis, dans `ModEntities.register()` (déjà appelé par `GtgsBackroomsMod.registerContent()`), ses attributs :

```java
FabricDefaultAttributeRegistry.register(SMILER, SmilerEntity.createAttributes());
```

---

### 4. Créer le renderer (CLIENT uniquement)

Le renderer va dans le sous-paquet `client/` de son domaine :
`src/main/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/client/entity/SmilerEntityRenderer.java`
(comme `core/portal/client/entity/BackroomsPortalEntityRenderer`).

```java
package com.gtgpuke.gtgsbackrooms.backrooms.level0.client.entity;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.entity.SmilerEntity;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/** Rendu du smiler. */
public final class SmilerEntityRenderer extends MobEntityRenderer<SmilerEntity, SmilerEntityModel> {

    private static final Identifier TEXTURE = Identifier.of(ModConstants.MOD_ID, "textures/entity/smiler.png");

    public SmilerEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new SmilerEntityModel(context.getPart(SmilerEntityModel.LAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(SmilerEntity entity) {
        return TEXTURE;
    }
}
```

Enregistrer dans `GtgsBackroomsClientMod.onInitializeClient()` :
```java
EntityRendererRegistry.register(ModEntities.SMILER, SmilerEntityRenderer::new);
```

Le code client (renderer, modèle) est exclu de la couverture : l'ajouter à `coverageExcludes` dans `build.gradle` et à
la liste `coverageExcludes` de `build.gradle` (`docs/dev/TESTS.md`). Tout le reste (attributs, objectifs, comportement) se teste.

---

### 5. Fichiers ressources

- `textures/entity/smiler.png` — texture originale (64×32 ou 64×64)
- `lang/en_us.json` : `"entity.gtgs_backrooms.smiler": "Smiler"`
- `lang/fr_fr.json` : traduction FR

---

## Checklist finale

- [ ] `backrooms/<niveau>/entity/SmilerEntity.java`
- [ ] `core/registry/ModEntities.java` — champ `static final`, attributs dans `register()`
- [ ] `backrooms/<niveau>/client/entity/SmilerEntityRenderer.java`, enregistré dans `onInitializeClient()`
- [ ] `textures/entity/smiler.png`
- [ ] Clés EN + FR
- [ ] `docs/reference/CONTENU.md` + `CHANGELOG.md` mis à jour
- [ ] Tests de comportement (unitaires ou en jeu) ; exclusion de couverture du seul code client
- [ ] `./gradlew build --warning-mode all` passe
