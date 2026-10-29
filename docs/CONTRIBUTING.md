# Contribuer — GTG's Backrooms

Le projet est sous licence propriétaire, tous droits réservés (`LICENSE`) : une contribution extérieure n'est acceptée
qu'avec un accord écrit de l'auteur sur les droits qui s'y attachent.

## Workflow Git

```text
master        ← stable, releases taguées
feature/xxx   ← une branche par feature
fix/xxx       ← une branche par correctif
```

Commits : `type(scope): message` avec `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `chore`.

```text
feat(level0): ajoute un second motif de gap de couloir
fix(wallpaper): recalcule le masque de faces a la bonne hauteur
test(generation): verrouille les empreintes du layer 4
```

---

## Checklist avant de push

- [ ] `./gradlew clean build --warning-mode all` : succès, **zéro warning**
- [ ] `./gradlew test` : vert (JaCoCo généré)
- [ ] `./gradlew runGameTest` : vert (le build l'enchaîne de toute façon)
- [ ] Couverture fusionnée : 100 % des instructions et des branches (`jacocoCoverageVerification`, dans le build)
- [ ] Checkstyle vert (`config/checkstyle/`, dans le build) et JAR sans dépendance ni test (`verifyJarContents`, dans le build)
- [ ] `LevelZeroGoldenTest` : si les empreintes changent, c'est voulu et c'est dans le CHANGELOG
- [ ] Nouveau contenu = test de comportement (pas de réflexion), clés EN **et** FR, modèle + texture + loot table
- [ ] `docs/CHANGELOG.md` et la doc métier concernée (`BLOCKS.md`, `ITEMS.md`, `DIMENSIONS.md`) à jour

Le détail pas à pas est dans `docs/prompt/BEFOREPUSH.md`.

---

## Style

| Règle | Détail |
|-------|--------|
| Encodage | UTF-8 ; sources Java en ASCII (pas d'accents dans le code ni les commentaires) |
| Indentation | 4 espaces, lignes ≤ 130 caractères |
| Imports | `java` → tiers (`com.mojang`, `com.llamalad7`, `org.*`) → `net.minecraft` → `net.fabricmc` → `com.gtgpuke`, aucun wildcard |
| Javadoc | classes publiques et méthodes non triviales, en français |
| Commentaires et logs | français, majuscule initiale, point final, via `ModConstants.LOGGER` |
| Allocation | aucune dans les méthodes tick/render ; noms de sections perf en constantes |
| Abstraction | pas d'interface, de stage ou de builder sans second usage réel |
| Génération | déterministe : seeds via `StageRandom`, sels figés |

---

## Règles non négociables

1. Un champ `static final` par enregistrement dans `core/registry/` ; la classe de l'objet dans le paquet de son domaine
   (tableau « Où ranger quoi » de `docs/ARCHITECTURE.md`).
2. Code client uniquement sous `client/` ou `mixin/client/`.
3. Tout texte visible en jeu passe par `Text.translatable` et existe en EN et FR.
4. Aucune évolution de la génération du Level 0 sans mise à jour des empreintes et du CHANGELOG.
5. Pas de dossier vide, pas de `.gitkeep`, pas de code « pour plus tard » sans utilisation.
6. Un autre mod ne se nomme que dans son module de compatibilité (`compat/<mod>/`), seul endroit qui en lit l'API, et
   dans ce qui le sert : ses messages (clés de langue `compat.*`) et la documentation qui le décrit ; ailleurs, aucun.
   À l'exécution, aucune dépendance de mod hors Fabric Loader et Fabric API, rien d'embarqué : un module de
   compatibilité compile contre son mod (`modCompileOnly`) et ne s'applique que s'il est là.
7. Aucun code ni aucune ressource repris d'un projet tiers, quelle que soit sa licence (`LICENSE`).
