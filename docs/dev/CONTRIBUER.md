# Contribuer

Le projet est sous licence propriétaire, tous droits réservés ([LICENSE](../../LICENSE)). Une contribution extérieure
n'est acceptée qu'avec un accord écrit de l'auteur sur les droits qui s'y attachent.

## Workflow Git

| Branche | Rôle |
|---------|------|
| `master` | Branche stable, d'où partent les releases taguées |
| `feature/<nom>` | Une branche par fonctionnalité |
| `fix/<nom>` | Une branche par correctif |

## Messages de commit

Format : `type(scope): message`. Les types sont `feat`, `fix`, `refactor`, `perf`, `test`, `docs` et `chore`.

```text
feat(level0): ajoute un second motif de gap de couloir
fix(wallpaper): recalcule le masque de faces a la bonne hauteur
test(generation): verrouille les empreintes du layer 4
```

## Avant de push

- [ ] `./gradlew clean build --warning-mode all` passe sans aucun avertissement, ni javac ni Gradle. Il enchaîne
      Checkstyle, tests unitaires, tests en jeu, tests du client, verrou de couverture et contenu du JAR.
- [ ] Si `LevelZeroGoldenTest` ou `PatchedShadersGoldenTest` a changé, c'est voulu, les empreintes sont recopiées et le
      CHANGELOG l'annonce.
- [ ] Tout nouveau contenu a un test de comportement, ses clés de langue en anglais et en français, son modèle, sa
      texture et sa loot table s'il rend quelque chose.
- [ ] [CHANGELOG.md](../../CHANGELOG.md) et la documentation du système touché sont à jour.

L'audit complet, pas à pas, est dans [.prompts/BEFOREPUSH.md](../../.prompts/BEFOREPUSH.md). Le détail des tests et
de la couverture est dans [TESTS.md](TESTS.md).

## Style du code

Les règles d'or et les principes d'architecture sont dans le [guide](../GUIDE.md). Les conventions ci-dessous
s'appliquent à tout le code. Checkstyle vérifie celles qu'un outil peut vérifier (voir [TESTS.md](TESTS.md#checkstyle)).

| Règle | Détail |
|-------|--------|
| Langue | Commentaires, logs et messages d'erreur en français, avec majuscule initiale et point final |
| Encodage | Fichiers en UTF-8, sources Java en ASCII : aucun accent dans le code ni dans les commentaires |
| Mise en forme | 4 espaces, 180 colonnes au plus, une instruction par ligne |
| Imports | `java`, puis bibliothèques tierces (`com.mojang`, `com.llamalad7`, `org.*`), `net.minecraft`, `net.fabricmc` et `com.gtgpuke`, sans wildcard |
| Javadoc | Chaque type public et chaque méthode non triviale, en français |
| Journal | `ModConstants.LOGGER` seulement, aucun `System.out` hors benchmark |
| Chemins chauds | Aucune allocation ni concaténation de chaînes dans les méthodes tick et render, noms de sections perf compris |
| Abstraction | Pas d'interface, de stage ni de builder sans deuxième implémentation réelle |
| Génération | Déterministe : graines tirées par `StageRandom`, sels figés |

## Règles non négociables

1. Chaque enregistrement est un champ `static final` d'un registre de `core/registry/`. La classe de l'objet va dans le
   paquet de son domaine (voir [ARCHITECTURE.md](../systemes/ARCHITECTURE.md)).
2. Le code client vit sous un paquet `client/` ou sous `mixin/client/`.
3. Tout texte visible en jeu passe par `Text.translatable` et existe en anglais et en français.
4. La génération du Level 0 ne change pas sans empreintes régénérées ni annonce dans le CHANGELOG.
5. Pas de dossier vide, pas de `.gitkeep`, pas de code écrit pour plus tard.
6. Un autre mod ne se nomme que dans son module `compat/<mod>/`, ses clés de langue `compat.*` et la documentation de
   ce module.
7. À l'exécution, aucune dépendance de mod hors Fabric Loader et Fabric API, et rien d'embarqué. Un module de
   compatibilité compile contre son mod (`modCompileOnly`) et ne s'applique que si ce mod est présent.
8. Aucun code ni aucune ressource repris d'un projet tiers, quelle que soit sa licence.
9. Aucun `@Redirect` dans un mixin. `MixinTargetsTest` le refuse.
