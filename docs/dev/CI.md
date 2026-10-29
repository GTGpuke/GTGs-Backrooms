# Intégration continue

## Workflows

| Workflow | Fichier | Déclencheur |
|----------|---------|-------------|
| CI | `.github/workflows/ci.yml` | Push et pull request sur `master`, lancement manuel |
| Release | `.github/workflows/release.yml` | Tag `v*.*.*`, lancement manuel avec le nom du tag |
| Dependabot | `.github/dependabot.yml` | Une fois par mois |

### CI

Un seul job tourne sur `ubuntu-latest`, avec Java 21 (Temurin) et 60 minutes au plus. Il n'y a pas de matrice : le mod
ne vise qu'une version de Minecraft et un JDK. Un nouveau push sur la même branche annule le run en cours. Seul
`master` écrit dans le cache Gradle, les autres branches le lisent.

| Étape | Commande ou action |
|-------|--------------------|
| Validation du wrapper Gradle | `gradle/actions/wrapper-validation` |
| Écran virtuel et rendu logiciel | Installation de `xvfb`, `libgl1-mesa-dri`, `libglx-mesa0` et `libegl1` |
| Conventions du code | `./gradlew checkstyleMain checkstyleTest checkstyleGametest` |
| Tests unitaires | `./gradlew test` |
| Tests en jeu | `./gradlew runGameTest` |
| Tests du client | `./gradlew runClientTest verifyClientTests`, sous `xvfb-run` |
| Couverture | `./gradlew jacocoFullReport jacocoAllReport jacocoCoverageVerification`, sous `xvfb-run` |
| Build et contenu du JAR | `./gradlew build`, sous `xvfb-run` |
| Benchmark | `./gradlew benchmarkLevelZeroGeneration` |
| Publication | Artefacts `gtgs_backrooms-<sha>` et `rapports-<sha>` |

Chaque commande Gradle reçoit `--no-daemon --console=plain --warning-mode all`. Le client de test tourne sous Xvfb,
sur un écran de 1280 × 720, avec le rendu logiciel de Mesa (`LIBGL_ALWAYS_SOFTWARE=1`). La couverture et le build
dépendent des tests du client, d'où `xvfb-run` sur ces étapes aussi.

Chaque étape est une tâche Gradle qui se lance aussi en local. La CI ne fait rien de plus que `./gradlew build`, le
rapport fusionné et le benchmark, mais sur une machine neuve. Les tests en jeu y sont rejoués plusieurs fois sur le
même monde, ce qui a des conséquences pour leur écriture (voir [TESTS.md](TESTS.md#tests-en-jeu-gametest)).

L'artefact `gtgs_backrooms-<sha>` contient les JAR. L'artefact `rapports-<sha>` est publié même en cas d'échec. Il
contient les rapports Checkstyle, les rapports des tests unitaires, les trois rapports de couverture, `build/junit.xml`,
`build/client-junit.xml` et les journaux du serveur et du client de test. Les deux artefacts sont gardés 14 jours.

### Release

1. Le workflow lit le tag, celui du push ou celui saisi au lancement manuel, et en tire la version sans le `v`.
2. Il lance `./gradlew clean build` sous `xvfb-run`, avec toutes les vérifications.
3. Il vérifie que cette version égale `mod_version` de `gradle.properties`. Il vérifie aussi que
   `gtgs_backrooms-<version>.jar` et `gtgs_backrooms-<version>-sources.jar` existent.
4. Il extrait les notes de version de `CHANGELOG.md`, à la racine du dépôt. Les notes sont le texte compris entre
   `## [<version>]` et la section suivante. Sans cette section, elles renvoient au CHANGELOG.
5. Il crée la release GitHub avec les deux JAR. Un tag qui contient `-`, comme `v0.11.0-beta.1`, donne une
   pré-release.

### Dependabot

Dependabot ouvre une pull request par action GitHub à mettre à jour, une fois par mois, avec l'étiquette `ci`. Les
dépendances Gradle suivent la version de Minecraft : Fabric, Yarn et Loom se mettent à jour à la main.

## Créer une release

1. Dans `CHANGELOG.md`, écrire la section `## [x.y.z] - AAAA-MM-JJ`. La release en tire ses notes.
2. Dans `gradle.properties`, passer `mod_version` à `x.y.z`.
3. Committer, pousser, puis poser et pousser le tag :

```bash
git add gradle.properties CHANGELOG.md
git commit -m "chore(release): version x.y.z"
git push origin master
git tag vx.y.z
git push origin vx.y.z
```

Le workflow Release échoue si le tag ne correspond pas à `mod_version`.

## Déboguer un job rouge

1. Dans l'onglet Actions, ouvrir le run, le job, puis l'étape en échec.
2. Télécharger l'artefact `rapports-<sha>` et lire le rapport de l'étape.
3. Reproduire en local avec la même commande Gradle.
4. Si le cache Gradle semble en cause, le supprimer dans Actions, Caches.

| Étape en échec | Où lire |
|----------------|---------|
| Conventions du code | `build/reports/checkstyle/` |
| Tests unitaires | `build/reports/tests/test/index.html` |
| Tests en jeu | `build/junit.xml` pour les messages, `build/run/gameTest/logs/` pour le journal |
| Tests du client | `build/client-junit.xml`, `build/run/clientTest/logs/` |
| Couverture | `build/reports/jacoco/full/html/index.html` |

Les captures d'un test du client en échec ne sont pas dans l'artefact. Rejouer le test en local pour les obtenir. Un
test qui ne rougit qu'en CI peut venir de Mesa, pilote OpenGL strict, ou du rejeu des tests en jeu sur un monde déjà
joué. Les deux cas sont décrits dans [TESTS.md](TESTS.md).
