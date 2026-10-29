# Installation

## Prérequis

| Outil | Version | Vérification |
|-------|---------|--------------|
| JDK | 21, Temurin conseillé | `java -version` |
| Git | récent | `git --version` |
| Gradle | fourni par le wrapper (9.8.1) | rien à installer |

Un seul JDK suffit : Java 21 lance Gradle et compile le mod. La compilation passe par la toolchain Java 21. Si
`JAVA_HOME` désigne un autre JDK, Gradle cherche un JDK 21 déjà installé : dossiers d'Eclipse Adoptium, registre
Windows, `~/.jdks`. Il n'en télécharge aucun. Sans JDK 21, installer Temurin :

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

## Cloner et lancer

```bash
git clone https://github.com/GTGpuke/GTGs-Backrooms.git
cd GTGs-Backrooms
./gradlew runClient
```

Le premier lancement télécharge Minecraft, les mappings Yarn et Fabric, ce qui prend quelques minutes. Le client de
développement travaille dans `run/`.

Pour entrer directement dans un monde solo, passer son nom : `./gradlew runClient -PquickPlayWorld=MonMonde`. Sous Git
Bash, un nom qui contient une espace passe par une variable d'environnement :

```bash
ORG_GRADLE_PROJECT_quickPlayWorld="New World" ./gradlew runClient
```

## VS Code

Installer les extensions *Extension Pack for Java* et *Gradle for Java*. Les tâches de `.vscode/tasks.json` lancent
`runClient`, `runServer`, `build`, `test`, `runGameTest` et `genEclipseRuns`. Elles forcent `JAVA_HOME` sur un chemin de
Temurin 21 écrit en dur, à adapter à la machine. Ctrl+Shift+B lance `runClient`.

L'autobuild de l'extension Java est coupé dans `.vscode/settings.json` (`java.autobuild.enabled: false`). L'extension
compile avec son propre compilateur, Eclipse JDT, dans `build/classes/java/main`, le dossier de Gradle. Avec un modèle
de projet périmé, elle y écrit des classes en erreur, et le client plante au chargement. Ne réactiver l'autobuild
qu'après un import Gradle sain, l'extension tournant sur un JDK 21. Le remède à ce plantage est dans
[DEPANNAGE.md](DEPANNAGE.md).

## Commandes Gradle

| Commande | Effet |
|----------|-------|
| `./gradlew runClient` | Client de développement |
| `./gradlew runServer` | Serveur dédié, dans `run/server/` |
| `./gradlew test` | Tests unitaires et rapport de couverture |
| `./gradlew runGameTest` | Tests en jeu sur un serveur sans affichage |
| `./gradlew runClientTest verifyClientTests` | Tests du client, fenêtre cachée |
| `./gradlew checkstyleMain checkstyleTest checkstyleGametest` | Conventions du code |
| `./gradlew benchmarkLevelZeroGeneration` | Benchmark de génération du Level 0 |
| `./gradlew build` | Toutes les vérifications, puis `build/libs/gtgs_backrooms-<version>.jar` |
| `./gradlew clean build --warning-mode all` | Build complet depuis zéro, qui doit passer sans avertissement |

Le détail des tests, des rapports et de la couverture est dans [TESTS.md](TESTS.md).

## Options de développement

Les options du mod sont des propriétés système `-D`. En développement, `-P<nom>=<valeur>` les transmet au client, aux
tests du client et au benchmark, par exemple `./gradlew runClient -PportalFeature=false`. La liste des options et de
leurs noms Gradle est dans [OPTIONS.md](../reference/OPTIONS.md).

## Installer le mod

1. Lancer `./gradlew build`, ou prendre le JAR d'une release GitHub.
2. Copier `gtgs_backrooms-<version>.jar` dans le dossier `mods/` du serveur et de chaque client. Le JAR `-sources` ne
   s'installe pas.
3. Vérifier la présence de Fabric Loader 0.16.9 ou plus récent, de Fabric API pour 1.21.1 et de Java 21.
4. Redémarrer.
