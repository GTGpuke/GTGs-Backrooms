---
name: add-dependency
description: "Politique des dépendances de GTG's Backrooms (Fabric 1.21.1) : à l'exécution, le mod ne dépend que de Fabric Loader et de Fabric API et n'embarque rien ; un autre mod ne se nomme que dans son module de compatibilité (compat/). Utilise ce skill dès qu'on envisage d'ajouter une librairie, une dépendance Maven, un mod API ou une intégration avec un autre mod. Déclenche pour 'ajoute une lib', 'ajoute une dépendance', 'intègre', 'librairie', 'dépendance', 'API', 'framework', 'intégration', 'import externe', 'dependency', 'compatibilité avec un mod'."
---

# Skill — Dépendances (politique du projet)

## Règle

À l'exécution, le mod **ne dépend d'aucun autre mod** que la plateforme : Fabric Loader et Fabric API
(`fabric.mod.json`, `depends`), aucun JAR embarqué (Jar-in-Jar). Un autre mod **ne se nomme que dans son module de
compatibilité** (`compat/<mod>/`, décisions de l'auteur des 28 et 29 octobre 2026), qui compile contre lui
(`modCompileOnly`, maven Modrinth) et ne s'applique que s'il est là (`CompatMixinPlugin`). `verifyJarContents` vérifie
que le JAR ne contient que le mod.

Ce que le jeu de base fournit déjà (Gson, JOML, LWJGL dont OpenGL et OpenAL, Brigadier, SLF4J, Fastutil, Guava) se
sert directement, sans le déclarer.

## Compatibilité avec un autre mod

Hors d'un module `compat/`, elle se fait **sans le nommer**, par ce que vanilla expose :

- Un pack de shaders se reconnaît au milieu du rendu du joueur : les programmes que vanilla donne aux entités et aux
  translucides ne sont plus ceux du jeu de base (`ShaderPackState`).
- Un mod qui dessine le terrain se reconnaît à la grille vanilla qu'il crée vide (`RemoteViewRenderer.foreignTerrain`).
- Un autre moteur acoustique se reconnaît à ses objets EFX dans le contexte OpenAL (`EfxBackend`).
- Une injection ne vise jamais une méthode qu'un mod de rendu réécrit (voir les pièges de rendu des règles du projet).

Un module `compat/<mod>/` peut lire l'API du mod visé (ses signatures, jamais son code source) et y poser des mixins,
dans une configuration à part (`gtgs_backrooms.compat.mixins.json`) filtrée par `CompatMixinPlugin` sur la présence du
mod et sa plage de versions vérifiée. Exemple : `compat/iris/` (le rendu du mod remplace le pack dans les Backrooms).

Pour essayer un mod tiers en développement : son JAR dans `run/mods` (Loader le remappe), jamais dans le dépôt ni dans
`build.gradle`.

## Bibliothèque de test

Une bibliothèque qui ne sert qu'aux tests (jamais embarquée) se déclare en `testImplementation`, depuis Maven Central,
avec sa version dans `gradle.properties` :

```groovy
testImplementation "groupe:artefact:${version_propriete}"
```

## Checklist

- [ ] Aucun `modImplementation`, `modRuntimeOnly` ni `include` autre que Fabric Loader et Fabric API ; un `modCompileOnly` seulement pour un module `compat/`
- [ ] `fabric.mod.json` : `depends` limité à `fabricloader`, `minecraft`, `java`, `fabric-api`
- [ ] Aucun autre mod nommé hors de `compat/` (`grep -rniE` sur `src`, `docs` hors specs de l'auteur, `README.md`)
- [ ] `./gradlew clean build --warning-mode all` vert (dont `verifyJarContents`)
