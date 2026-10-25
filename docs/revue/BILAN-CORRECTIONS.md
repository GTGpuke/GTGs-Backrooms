# Revue de GTG's Backrooms : bilan unique

> Revue faite du 24 au 25 octobre 2026, sur `master` au commit `d331d53` (version 0.10.0). Ce document est le seul de
> `docs/revue/` : il réunit le bilan des corrections et tout ce qui le fonde (constats, mesures, analyses, données de
> référence, pistes et protocole de test).
>
> **Rien n'est implémenté.** Ce bilan est un plan : le code ne change que sur décision explicite de l'auteur, lot par
> lot.
>
> Ce document ne nomme aucun autre mod que Iris (règle de `docs/prompt/FINALCHECK.md`).

## Sommaire

| Partie | Contenu |
|---|---|
| [BILAN](#bilan--le-bilan-des-corrections) | Les 83 corrections (C01 à C83) avec leur verdict, leur condition et leur lot ; les garde-fous ; les décisions de l'auteur ; ce qui est écarté ; l'ordre de mise en œuvre ; où chaque point des autres parties est traité |
| [REVUE](#revue--la-revue-du-projet) | Les incohérences relevées, avec leur preuve (fichier et ligne) ; les versions des outils et le plan de mise à jour ; les pistes pour un projet plus professionnel |
| [PERF](#perf--le-diagnostic-de-performance-des-portails) | Les conditions du banc et ses scènes (S0 à S7, R0 à R6) ; les mesures ; les diagnostics ; les pistes classées ; comment rejouer le banc |
| [NR](#nr--la-non-régression-des-correctifs-proposés) | La référence mesurée ; les angles morts ; chaque proposition passée au crible ; les garde-fous G1 à G12 ; l'ordre des lots ; la lecture stricte et l'oracle visuel (scènes V0 à V7) |
| [REF](#ref--les-références-de-la-version-0100) | Les exclusions de couverture, les règles d'architecture, les classes utilitaires, les chemins chauds, les octets des payloads, les sauvegardes et les nombres de tests de la 0.10.0 |
| [EXPLO](#explo--exploration--carte-graphique-threads-lod-et-visibilité-des-portails) | Le rendu actuel ; les portails noirs ; la carte graphique (GPU1 à GPU7) ; les threads et les langages ; le LOD ; les vues cachées ; le protocole de test (campagnes A à M) ; les décisions |

**Renvois.** « PERF 3.2 » désigne la section 3.2 de la partie PERF, « REVUE 1.3.3 » le point 3 de la section 1.3 de la
partie REVUE, et « NR 3.8 (4) » le point 4 de la section 3.8 de la partie NR. Dans une partie, « section 3.2 » ou
« (3.2) » renvoie à la même partie. GUIDE, CLAUDE.md, PORTALS.md, TESTING.md et les autres noms de fichiers désignent
ceux du projet.

**Identifiants.** C01 à C83 : corrections (BILAN 3). G1 à G12 : garde-fous (BILAN 2, NR 4.2). GPU1 à GPU7 : pistes pour
la carte graphique (EXPLO 3.3). S0 à S7 et R0 à R6 : scènes du banc de performance, sur la puce Intel et sur la RTX
(PERF 1). V0 à V7 : scènes du banc visuel (NR 5.2). A à M : campagnes du protocole de test (EXPLO 7.3).

---

## BILAN — Le bilan des corrections

> Bilan du 25 octobre 2026. Il réunit, en une seule liste cohérente, chaque correction relevée dans les cinq parties
> qui suivent. Chaque correction y reçoit un verdict selon la règle de l'auteur : aucune régression visuelle, de
> fonctionnalité ou de qualité. Les parties REVUE à EXPLO gardent le détail et les preuves ; le bilan donne la liste,
> le verdict, la condition et l'ordre.

### BILAN — L'essentiel

1. **D'abord les garde-fous (lot 0).** Le build ne voit aujourd'hui ni l'image, ni le JAR de production, ni les
   sauvegardes, ni le format réseau, ni un garde-fou qu'on débrancherait (NR, section 2). Les douze garde-fous G1 à G12
   se posent avant tout changement qu'ils protègent. Une bonne part de leurs données est déjà relevée (REF).
2. **Les corrections les plus importantes** :
   - le gel du serveur de 0,7 à 2,9 s à la pose d'un portail vers une zone neuve (C56) : le défaut le plus grave pour
     les joueurs ;
   - la CI qui annonce « zéro warning javac » sans le vérifier (C13) ;
   - l'API de Loom dépréciée (C14), préalable à toute montée d'outils ;
   - 44 % des lignes hors de la mesure de couverture, alors que la règle ne permet d'exclure que la colle GL (C22,
     C49) ;
   - `.vscode/settings.json` absent du dépôt, alors que CLAUDE.md compte dessus (C37) ;
   - le moniteur de performance, qui fausse ses propres mesures (C77) ;
   - le jeu qui tourne sur la puce Intel de ce portable, deux à trois fois moins vite (C36).
3. **Les portails noirs et le retour lent dans le champ** viennent surtout du serveur, pas du rendu :
   - le serveur envoie 8 chunks toutes les 250 ms ;
   - il retire une fenêtre 5 s après le dernier regard, au-delà de 16 blocs ;
   - la fenêtre de 7 × 7 chunks est plus petite que ce que montre le cadre.

   Les remèdes changent l'image (en mieux) : décision de l'auteur (C60, C61). Ce qui est exact se fait sans attendre
   cette décision : créer le monde distant à l'avance (C64), ne plus dessiner une vue cachée derrière un mur (C69).
4. **La règle de l'auteur trie chaque correction en six verdicts** (section 1.2). Celles qui ne changent rien
   d'observable entrent dans les lots « sans régression ». Celles qui changent ce que voit ou peut faire le joueur, même
   en mieux, attendent une décision explicite (section 4). Sur 83 corrections :
   - 20 sûres et 37 sûres sous condition, qui ne changent rien d'observable ;
   - 12 changements voulus ;
   - 7 décisions de l'auteur ;
   - 4 recherches ;
   - 3 qui mêlent deux verdicts : C53 (métadonnées et icône), C55 (fichier de configuration) et C56 (arrivée asynchrone
     et son délai).

   S'y ajoutent les douze garde-fous, 17 propositions écartées et 4 constats sans correction.
5. **Ce qui ne se corrige pas sans régression est dit, avec la raison** (section 5). C'est le cas du portage de
   Minecraft : parité, pas identité.

---

### BILAN 1. La règle commune

#### BILAN 1.1 Ce qui ne doit pas régresser

| Dimension | Ce qui compte | Oracle |
|---|---|---|
| **Qualité** | Checkstyle, javac sans avertissement, tests unitaires (749 exécutions) et en jeu (99), couverture à 100 % du périmètre mesuré, contenu du JAR, cibles des mixins, empreintes du Level 0 (15) | Le build complet, vert en 5 min 30 (NR, section 1) ; G8 pour qu'aucun garde-fou ne se débranche |
| **Fonctionnalités** | Tout ce qui est observable en jeu (pose, destination, apparition, traversée, vue, son, interaction, commande) ; les versions de Minecraft, du chargeur et de Fabric API acceptées ; la relecture des mondes sauvés ; le dialogue client-serveur ; les machines où le build se lance | Tests unitaires et en jeu, liste « En jeu » de `docs/PORTALS.md`, G1 à G4, G7 |
| **Visuels** | Toute image qui s'écarte d'une référence au-delà du bruit mesuré, sur les trois chemins de rendu : vanilla, mod de terrain, pack de shaders | Banc G6 sur le chemin vanilla, avec son critère : aucun pixel à plus de 10 niveaux de chacune des références (NR 5.2) ; G11 sur le banc de l'auteur pour les deux autres chemins |

Principes (NR 4.1) :
- la référence est relevée avant chaque lot ;
- un garde-fou est écrit et vert sur le code actuel avant le changement qu'il protège ;
- un oracle ne bouge jamais dans la PR du code qu'il juge ;
- un test existant n'est jamais affaibli ;
- un lot = une branche = une PR, CI verte ;
- on mesure avant et après sur la même machine, et on ne conclut rien sous 10 % d'écart.

#### BILAN 1.2 Les six verdicts

| Verdict | Sens | Entre dans un lot ? |
|---|---|---|
| **Sûr** | Rien d'observable ne change ; les garde-fous existants suffisent | Oui |
| **Sûr si** | Rien d'observable ne change, à la condition indiquée, posée avant | Oui, après la condition |
| **Voulu** | Change volontairement ce que voit ou peut faire le joueur ou le développeur, souvent en mieux | Seulement après décision explicite de l'auteur, annoncé dans le CHANGELOG |
| **Recherche** | Pas un correctif : prototype et mesures d'abord ; une option désactivée par défaut au mieux | Non, tant que le prototype n'a pas été validé |
| **Décision** | Relève de l'auteur sans être un changement de jeu : specs de l'auteur, workflow, réglages GitHub, publication, version visée | Selon la décision |
| **Écarté** | Pas fait ; la raison est donnée (section 5) | Non |

Chaque lot se termine par `./gradlew clean build --warning-mode all` sans avertissement, la doc et le CHANGELOG à
jour (règle d'or 9 de `docs/GUIDE.md`), et `docs/prompt/FINALCHECK.md`.

---

### BILAN 2. Les garde-fous, posés en premier

| | Garde-fou | Ce qu'il empêche | Données déjà relevées | Source | Lot |
|---|---|---|---|---|---|
| G1 | Sauvegardes de référence 0.10.0 relues champ par champ, et structure de test en jeu | Un monde existant qui se relit autrement | Quatre sauvegardes en SNBT (REF 6) ; leur relecture reste à essayer | NR 4.2, 4.4 | 0 |
| G2 | Octets de référence des payloads, puis numéro de protocole (une fonctionnalité nouvelle, à décider) | Un client et un serveur qui ne se comprennent plus | Douze identifiants et octets de onze payloads (REF 5) | NR 4.2 | 0 |
| G3 | Arrivées de référence (test en jeu, graine 0) | Une destination qui change après un remaniement | À relever sur le code actuel | NR 4.2 | 0 |
| G4 | Pas de génération synchrone à la pose (test en jeu, assertion de structure, sans chronomètre) | Le retour du gel serveur | Après C56 | NR 4.2 ; PERF 3.2 | 4 |
| G5 | Zéro allocation mesurée, et relevé statique du bytecode | Des allocations qui reviennent dans les chemins chauds | 32 méthodes, méthodes mémoïsées à nommer (REF 4) | NR 4.2 ; REF 4 | 6 |
| G6 | Banc client scripté : chiffres (scènes S0 à S7, R0 à R6) et captures (scènes V0 à V7) | Une régression d'image ou de performance côté client | Bruit mesuré et critère (NR 5.2) ; scripts hors du dépôt | NR 4.2, 5.2 ; PERF 5 | 3 |
| G7 | Essai en production : JAR construit, chargeur minimal déclaré (0.16.9) puis courant, jusqu'à un monde chargé | Un JAR de production qui ne démarre pas | — | NR 4.2 ; REVUE 2.2 | 2, 7 |
| G8 | Contrôle des garde-fous : graphe du build, planchers de tests, liste des exclusions de couverture, passage canari | Un garde-fou débranché en silence | Liste de 29 classes et deux paquets, planchers 749 et 99, forme de `build/junit.xml` (REF 1 et 7) | NR 4.2 ; REF 1, 7 | 0 |
| G9 | Mutation testing, en rapport | Remanier en se croyant protégé par des tests qui n'assertent rien | Outil à valider avec le chargeur de tests Fabric | NR 4.2 ; REVUE 3.9 | 0 |
| G10 | Tests d'architecture | Une dépendance interdite qui réapparaît | Les cinq règles sont vraies aujourd'hui ; listes blanches (REF 2) | NR 4.2 ; REVUE 3.3 ; REF 2 | 0 |
| G11 | Matrice de compatibilité manuelle : vanilla, mod de terrain, Iris avec deux packs | Une régression avec un autre mod de rendu | Banc de l'auteur | NR 4.2 ; GUIDE §8 | chaque PR client |
| G12 | Rejeu explicite des tests en jeu sur un monde déjà joué | La perte de cette robustesse, qui ne tient qu'à un effet de bord de la CI | — | NR 4.2, 3.8 (4) ; REVUE 1.3.6 | 2 |

---

### BILAN 3. Le catalogue unique

Colonnes :
- **Prio.** : P1 à traiter en premier, P2 cohérence et qualité, P3 finition ;
- **Lot** : celui de la section 6 ; « déc. » signifie après la décision de l'auteur (section 4).

#### BILAN 3.1 Code et commentaires

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C01 | Javadoc du biome rouge : « néons rares » alors que sa densité lumineuse est nulle. Corriger le commentaire, pas la valeur | REVUE 1.1.1 | P2 | Sûr | Les empreintes du Level 0 ne bougent pas | 1 |
| C02 | Javadoc orpheline de `MixinWorldRenderer` : la remettre au-dessus de `backrooms$mirrorChunkRender` | REVUE 1.1.2 | P3 | Sûr | — | 1 |
| C03 | Commande de debug : `Text.translatable` et clés `en_us` et `fr_fr` | REVUE 1.1.3 ; NR 3.2 | P2 | Voulu | La console d'un serveur dédié passe à l'anglais. Le test en jeu vérifie la clé et ses arguments, pas seulement qu'un message existe ; un test vérifie les clés dans les deux langues | 1, déc. |
| C04 | Allocation d'`ArrivalGuard.tick` : l'heure de pose dans un champ mutable | REVUE 1.1.5 ; REF 4 | P3 | Sûr si | Tests existants verts sans modification (classe couverte à 100 %, `PortalTunnelGameTests`) ; G5 | 1 |
| C05 | Allocation de `PortalViewTransform.aim` : un `Vec2f` par vue et par image | PERF 3.9 ; REF 4 | P3 | Sûr si | Classe mesurée ; G5 ; G6 | 6 |
| C06 | Autres allocations du mod : `client.portal.sight`, `client.portal.straddling`, `portal.destination.tick` | PERF 3.9 | P3 | Sûr si | Extraire d'abord la logique dans une classe mesurée (C49) ; G5 ; G6 | 6 |

#### BILAN 3.2 Tests

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C07 | Assertion vide `mixedColumns >= 0` : écrire la vraie attente | REVUE 1.2.1 | P2 | Sûr si | Relever la valeur réelle sur le code actuel, puis l'écrire telle quelle | 1 |
| C08 | Branche morte `hasWinningNeighbor` | REVUE 1.2.2 | P2 | Sûr si | La retirer seule : un test vert prouve qu'elle était morte | 1 |
| C09 | « 9 x 9 chunks » au lieu de 11 × 11 dans `GameTestServerWorlds` | REVUE 1.2.3 | P3 | Sûr | — | 1 |
| C10 | Réflexion des tests en jeu remplacée par des accesseurs du mod de test | REVUE 1.2.4 | P3 | Sûr si | Un accesseur faux échoue bruyamment ; `MixinTargetsTest` ne lit pas la configuration du mod de test. Si c'est trop coûteux, garder la réflexion | 1 |
| C11 | Test unitaire du répit d'`ArrivalGuard` | REVUE 1.2.5 | P3 | Sûr | — | 1 |
| C12 | `UtilityClassesTest` : découvrir les 72 classes dans le bytecode au lieu d'en lister 55 à la main, en gardant les constructeurs qui refusent l'appel | REVUE 3.9 ; NR 3.8 (6) ; REF 3 | P2 | Sûr si | Plancher de 72 classes et classes connues ; vérifier dans la suite complète | 1 |

#### BILAN 3.3 Build, CI et publication

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C13 | `-Werror` sur javac | REVUE 1.3.1 | P1 | Sûr | Zéro avertissement vérifié aujourd'hui | 1 |
| C14 | API de lancement de Loom par propriétés (`vmArg`, `programArgs`, `runDir` dépréciés) | REVUE 1.3.2 | P1 | Sûr si | `build/junit.xml` présent avec 99 tests ; cooldown de 3 ticks des tests en jeu gardé ; `runClient -P…` et `quickPlayWorld` essayés | 2 |
| C15 | Bornes de `fabric.mod.json` : ne pas utiliser `loader_version` (le minimum passerait de 0.16.9 à 0.19.5) ; Fabric API minimale = la plus ancienne essayée, sinon `*` | REVUE 1.3.3 ; NR 5.3 (1) | P2 | Sûr si | G7. Relever un minimum est Voulu (C15 b, section 4) | 2 |
| C16 | Actions GitHub en v7, v6, v6, v7 et v3 | REVUE 1.3.5 | P2 | Sûr si | Regarder d'abord sur GitHub les PR de Dependabot, ouvertes ou fermées ; une action par PR ; notes de version relues ; artefacts JAR et rapports présents | 2 |
| C17 | Triple passage des tests en jeu en CI : l'écrire comme une règle, ou le remplacer par une étape explicite | REVUE 1.3.6 ; NR 3.8 (4) | P3 | Sûr si | G12 | 2 |
| C18 | Configuration cache | REVUE 1.3.7, 3.7 | P3 | Sûr si | Mode avertissement, puis bloquant ; CI verte à chaque étape | 2 |
| C19 | Build plus maintenable : `build-logic`, moins d'`afterEvaluate`, catalogue de versions, Dependabot pour Gradle | REVUE 3.7 | P2 | Sûr si | G8 avant et après. Dependabot doit ignorer Minecraft, Yarn, Fabric Loader, Fabric API et Loom, qui suivent la version de Minecraft (`.github/dependabot.yml` le dit) | 2 |
| C20 | Nouvelles règles Checkstyle (noms, `MissingOverride`, `JavadocStyle`, indentation…) et analyseur de nullité | REVUE 3.10 | P2 | Sûr si | Seulement des règles sans violation, ou des réécritures strictement équivalentes ; toute branche ajoutée reste couverte | 2 |
| C21 | Publier la 0.10.0 par un tag | REVUE 1.3.4 | P2 | Décision | Sans risque : le workflow refait `clean build` ; un tag poussé publie (action de l'auteur) | 1 |

#### BILAN 3.4 Documentation et règles

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C22 | Couverture : nommer les exclusions classe par classe (29 classes et deux paquets, les mêmes 87 fichiers) ; TESTING.md nomme chaque classe exclue (10 n'y sont pas) ; objectif chiffré publié | REVUE 1.4.1, 3.4 ; REF 1 | P1 | Sûr | Mesure inchangée, vérifiée fichier par fichier ; G8 (c) | 0 |
| C23 | CONTRIBUTING : largeur de ligne 180, pas 130 ; exception de la réflexion pour les classes utilitaires, comme TESTING.md | REVUE 1.4.2, 1.2.4 | P2 | Sûr | — | 1 |
| C24 | Workflow Git décrit (branches, messages) contre celui pratiqué | REVUE 1.4.3, 3.12 | P2 | Décision | Appliquer la règle, ou l'assouplir dans CONTRIBUTING | — |
| C25 | Autres mods nommés hors des exceptions de FINALCHECK : skill `add-dependency`, `docs/AUDIT.md`, GUIDE hors de sa section 8 | REVUE 1.4.4 | P2 | Sûr | — | 1 |
| C26 | Skills : `add-dependency` recommande ce que `verifyJarContents` refuse ; `pre-push-audit` est plus vieux que `BEFOREPUSH.md` | REVUE 1.4.5 | P2 | Sûr | — | 1 |
| C27 | `docs/AUDIT.md` : le marquer historique ; CLAUDE.md n'en fait plus le « dernier audit » ; un nouvel audit après la 0.10.0 | REVUE 1.4.6, 3.13 | P2 | Sûr | — | 1 |
| C28 | Specs de l'auteur : `backrooms-mod-plan.md` (archive ou retrait), section 7 de `BACKROOMS_CONTENT_RULES.md`, titre de `backrooms-level0-pipeline-v6.md` | REVUE 1.4.7 à 1.4.9, 3.13 | P2 | Décision | Claude ne modifie pas ces fichiers | — |
| C29 | README : un nombre de tests vérifiable (298 méthodes, 749 exécutions, 99 tests en jeu), ou aucun | REVUE 1.4.10 | P3 | Sûr | — | 1 |
| C30 | Traductions françaises : accent de « Néon », casse des noms | REVUE 1.4.11 ; NR 3.1 | P3 | Voulu | Texte visible en jeu | 1, déc. |
| C31 | GUIDE §2.2 (« au-delà, cadre sombre ») contre PORTALS.md et le code (transparent) | REVUE 1.4.12 | P3 | Sûr | Corriger GUIDE, ou consigner l'écart dans sa section 2.4 | 1 |
| C32 | CLAUDE.md : le socle dépend aussi de `BackroomsConstants` ; « quatre `VertexBuffer` » par section au lieu de cinq | REVUE 1.1.4 ; PERF 3.4 | P3 | Sûr | — | 1 |
| C33 | Pièges de CLAUDE.md : les garder dans CLAUDE.md, ou les importer par `@docs/PIEGES.md` ; jamais un simple renvoi | REVUE 3.13 ; NR 3.8 (5) | P2 | Sûr | — | 1 |
| C34 | Catalogue des tests écrit une seule fois (TESTING.md) ; PORTALS.md y renvoie | REVUE 3.13 | P2 | Sûr si | Aucune information perdue au passage | 1 |
| C35 | Dossier de décisions (`docs/decisions/`, une fiche datée par décision) | REVUE 3.13 | P2 | Décision | — | — |
| C36 | Documenter le choix de la carte graphique sur les portables : réglage « Hautes performances » de Windows, ou `SHIM_MCCOMPAT` en dev (`docs/SETUP.md`, `docs/TROUBLESHOOTING.md`) ; une ligne pour les joueurs ; le coût d'un grand cadre sur une puce intégrée | PERF 3.1, 3.6 ; EXPLO 3.2 | P1 | Sûr | Vérifier d'abord si le launcher officiel a déjà son propre profil graphique | 1 |

#### BILAN 3.5 Environnement de développement

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C37 | Commiter `.vscode/settings.json`, compilation automatique coupée | REVUE 1.5.1, 3.15 | P1 | Sûr | — | 1 |
| C38 | Critère de JVM du démon Gradle : 21 tant que Loom reste en 1.17 ; 25 seulement avec Loom 1.18+ et la CI | REVUE 1.5.2, 3.15 ; NR 5.3 (2) | P2 | Sûr si | Un critère 25 dès maintenant empêcherait le build là où seul Java 21 est installé (Écarté, section 5) | 1 |
| C39 | Correctifs des JDK de cette machine (21.0.12.1, 25.0.4.1) | REVUE 1.5.3, 2.3 (A1) | P3 | Sûr | Installation sur la machine, hors dépôt | 7 |
| C40 | Téléchargement automatique de la toolchain manquante | REVUE 3.15 | P3 | Décision | Ajoute un accès réseau au build | — |

#### BILAN 3.6 Outils et versions

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C41 | Gradle 9.8.2 | REVUE 2.1, 2.3 (A3) | P2 | Sûr | Validation du wrapper | 7 |
| C42 | Loom 1.19.3, plugin `net.fabricmc.fabric-loom-remap`, Gradle sous Java 25 | REVUE 2.2, 2.3 (A3) | P2 | Sûr si | G7, G8 ; CI avec Java 25 et 21 ; C38 passé à 25 dans le même lot | 7 |
| C43 | Fabric Loader 0.20.1 (Mixin 0.17.5) | REVUE 2.3 (A4) ; NR 3.4 | P2 | Sûr si | G7 avec le chargeur minimal déclaré, sans quoi ne pas monter ; relever le minimum est Voulu (C15 b) | 7 |
| C44 | JUnit 5.14.4, ou 6.1.3 si `fabric-loader-junit` suit | REVUE 2.3 (A4) | P2 | Sûr si | G8 : au moins 749 exécutions et 99 tests en jeu | 7 |
| C45 | Checkstyle 14.3.0 | REVUE 2.3 (A4) | P2 | Sûr si | Configuration corrigée, aucune règle désactivée | 7 |
| C46 | JaCoCo épinglé à 0.8.15 | REVUE 2.3 (A4) | P3 | Sûr | — | 7 |

#### BILAN 3.7 Structure

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C47 | Source sets client et commun séparés | REVUE 3.1 | P1 | Sûr si | `MixinTargetsTest` lit les deux configurations ; la sortie client reste mesurée ; `verifyJarContents` adapté ; G7, G8. Le test d'architecture G10 donne déjà la même garantie au build sans déplacer de fichier. Refonte : validation de l'auteur | 5 |
| C48 | Socle et contenu : registres hors du socle, point d'accès des destinations du portail | REVUE 3.2, 1.1.4 | P2 | Sûr si | Aucun résultat attendu de `PortalTargetsTest`, `LevelZeroArrivalTest` et des tests en jeu modifié ; G3, G10. Refonte du socle : validation de l'auteur (règle d'or 2) | 5 |
| C49 | Rendre la frontière de la colle GL structurelle : extraire la logique pure des classes client exclues, ranger la vraie colle à part | REVUE 3.4, 1.4.1 | P1 | Sûr si | Une classe par PR, avec ses tests ; G6. Les classes qui servent aussi les chemins « mod de terrain » et « pack de shaders » seulement avec G11 avant et après | 5 |
| C50 | Découper les plus gros fichiers | REVUE 3.5 | P3 | Sûr si | Serveur : G9 avant. Client : G6 et G11 avant et après, un découpage par PR. Refonte : validation de l'auteur | 5 |
| C51 | Ranger `sync/payload/` et `mixin/accessor/` | REVUE 3.6 | P3 | Sûr si | G8 (exclusions) ; G7 (point d'entrée client) ; références de la doc | 5 |
| C52 | Ressources produites par le générateur de données de Fabric | REVUE 3.8 | P3 | Sûr si | Chaque JSON généré sémantiquement identique à l'actuel | 5 |
| C53 | Métadonnées : `contact` dans `fabric.mod.json` (Sûr) ; une icône (asset de l'auteur, sous `assets/gtgs_backrooms/`) ; publication sur les plateformes de mods | REVUE 3.11 | P2 | Sûr, et Décision pour l'icône et la publication | — | 1 |
| C54 | Dépôt GitHub : protection de `master`, format des messages, modèles d'issue et de PR | REVUE 3.12 | P3 | Décision | Réglages hors du dépôt | — |
| C55 | Fichier de configuration pour les joueurs (distances, nombre de vues) | REVUE 3.14 ; PERF 3.4 | P3 | Sûr si, et Décision | Valeurs par défaut identiques, `-D` prioritaire, fichier absent égal à aujourd'hui. GUIDE §3.5 a reporté l'interface graphique : à rouvrir avant une publication publique | 5 |

#### BILAN 3.8 Performance serveur et réseau

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C56 | Résolution asynchrone de l'arrivée : tickets et reprise, layouts du Level 0 sur un thread de travail | PERF 3.2 ; NR 3.6, 5.3 | P1 | Sûr si, et Voulu pour le délai | Noyau de recherche synchrone gardé comme oracle ; G3 inchangé ; G4. Le calcul du Level 0 peut tourner à part (vérifié). Le cadre ne devient portail qu'une fois les chunks prêts : décision de l'auteur | 4, déc. |
| C57 | Mise en paquets des chunks des fenêtres sur un thread de travail | EXPLO 4.2 | P3 | Sûr si | Copie sous le verrou du serveur ; ordre des envois gardé, vérifié par un test | 4 |
| C58 | Chunks du monde quitté gardés plus longtemps et déclarés « tenus » | PERF 3.7 | P2 | Sûr si | Arbitré avec C63 (mémoire contre réseau) ; tests en jeu des chunks tenus étendus à la nouvelle durée | 6 |
| C59 | Cooldown de traversée par défaut non nul | PERF 3.7 ; NR 5.4 | P3 | Voulu | On ne pourrait plus enchaîner aller et retour | déc. |
| C60 | Garder la fenêtre de tout portail à portée de rendu, quel que soit le regard : retour instantané dans le champ | EXPLO 2 (cas 2), 6.3, 8 | P2 | Voulu | Plus de noir au retour ; coût réseau et mémoire chiffré (campagnes C, J, K) | déc. |
| C61 | Synchroniser plus tôt, centre d'abord, en cône, et les arrivées des portails imbriqués | EXPLO 2 (cas 1, 3, 5), 8 | P2 | Voulu | Moins de noir à l'approche ; réseau et mémoire chiffrés | déc. |

#### BILAN 3.9 Performance client et rendu

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C62 | Brouillard et ciel des vues : réutiliser le résultat à entrées identiques (même monde, position exacte, heure, aucun chunk reçu entre-temps) | PERF 3.3 ; NR 5.3 (3) | P2 | Sûr si | Test d'exactitude ; G6. Toute variante approchée (au plus 1/255 par canal) est Voulu (C62 b) | 6 |
| C63 | Monde quitté allégé après un délai sans retour, documenté comme réglage de mémoire | PERF 3.5 ; NR 5.3 (4) | P2 | Voulu | Un retour tardif voit la vue se reconstruire, contre des centaines de Mo rendus | déc. |
| C64 | Créer le monde distant à l'avance, dès le rayon d'activation | PERF 3.10 ; EXPLO 2 (cas 4) ; NR 5.3 (5) | P3 | Sûr si | Mémoire des portails jamais regardés mesurée ; G6 (apparition d'un portail) | 6 |
| C65 | Budget de vues et niveau d'imbrication en option, désactivée par défaut | PERF 3.4 ; NR 3.8 (2) | P2 | Sûr si | Défaut identique à aujourd'hui. L'option activée change l'image : Voulu | 6 |
| C66 | Grille de vue réduite à ce que la fenêtre peut montrer | PERF 3.4 | P2 | Recherche | Prototype sur les trois chemins de rendu (G11) | — |
| C67 | Grand cadre : pré-passe de profondeur (GPU2), pour ne plus dessiner le monde du joueur derrière le cadre | PERF 3.6 ; EXPLO 3.3 | P3 | Sûr si | Image identique (V5, campagne F) ; gain d'au moins 10 % sur la puce Intel, sinon abandon | 6 |
| C68 | Grand cadre, autres variantes (monde du joueur coupé d'une autre façon) | PERF 3.6 | P3 | Recherche | G6, G11 ; derrière une option | — |
| C69 | Vue cachée derrière un mur : preuve par tranche pleine sur le processeur, et rendu conditionnel d'OpenGL (GPU1) | EXPLO 6.2 | P2 | Sûr si | Identique au pixel près (campagne D, scènes V0 à V7) ; aucune image de retard ; effets de bord gardés (vie du monde distant, graphe gardé chaud, sections reconstruites, rapport des portails dessinés) ; extension et stencil vérifiés sur chaque carte | 6 |
| C70 | LOD des portails par paliers : le palier 100 % est le rendu d'aujourd'hui (tout portail à moins de 16 blocs) ; paliers inférieurs (sans imbrication, sans effets, entités limitées, distance de vue réduite, fenêtre réseau réduite) | EXPLO 5 | P3 | Voulu | Seuils et hystérésis fixés par l'auteur ; zéro couture (aucun changement de palier à moins de 16 blocs) ; sorties fugaces, son et traversée intacts | déc. |
| C71 | Vue lointaine en résolution réduite (GPU4), image réutilisée ou imposteur au-delà de 64 blocs (GPU5) ; image d'attente faite de la dernière image connue, ou du ciel seul sans tampon | EXPLO 2, 3.3, 5.2 | P3 | Recherche | Rouvre le choix écarté du framebuffer par vue (GUIDE 3.5) : décision de l'auteur. Le ciel seul n'a pas besoin de tampon, mais change l'image (Voulu) | — |
| C72 | Un second niveau d'imbrication : au-delà d'un niveau, le cadre est aujourd'hui transparent | EXPLO 2 (cas 6) | P3 | Recherche | Coût d'une vue de plus par portail imbriqué ; un niveau de stencil de plus ; G6, G11 | — |
| C73 | Distance de rendu des portails plus grande que 64 blocs (le terrain du joueur va jusqu'à 192 blocs à la distance 12) | EXPLO 2 (cas 7) ; PERF 3.4 | P3 | Voulu | Un portail lointain se voit au lieu de disparaître, contre plus de vues, de réseau et de mémoire ; à combiner avec le LOD (C70) | déc. |
| C74 | Cadres plus grands que 8 × 8 | EXPLO 5.3 | P3 | Voulu | Fenêtre, recherche d'arrivée, collisions, lumière transmise, étages du Level 0 à revoir | déc. |
| C75 | Cadre trop haut pour un étage du Level 0 : le refuser ou le réduire à l'arrivée | PERF 3.12 | P3 | Voulu | Seules les nouvelles arrivées changent ; empreintes intactes ; attentes de `LevelZeroArrivalTest` étendues, pas réécrites | déc. |
| C76 | Message en jeu quand le rendu tourne sur une puce intégrée, ou réglage de Windows écrit avec l'accord du joueur | EXPLO 3.2 | P3 | Voulu | Texte traduit ; le réglage de Windows touche au système | déc. |

#### BILAN 3.10 Mesure et outillage

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C77 | Moniteur de performance : lire la charge processeur hors des threads de jeu | PERF 3.8 | P1 | Sûr si | API et format gardés ; échantillonneur injectable, déterministe en test ; thread démon arrêté avec le moniteur | 3 |
| C78 | Mesure du temps GPU par vue et par passe (GPU3) | EXPLO 3.3 ; GUIDE 4.7 | P2 | Sûr si | Extension `ARB_timer_query` présente : Minecraft crée un contexte OpenGL 3.2 | 3 |
| C79 | Outils du protocole de test : compteur d'images incomplètes par vue, vidage d'images en debug, temps jusqu'à une fenêtre complète, part de noir dans la zone du cadre | EXPLO 7.2, 7.5 | P3 | Sûr si | Options de debug désactivées par défaut | 3 |
| C80 | Scripts du banc (pilotage, captures, comparateur, résumé du journal) versés au dépôt | PERF 5 ; NR 4.2 (G6) | P2 | Décision | — | 3 |
| C81 | Campagnes A à M du protocole de test, sur ce PC et sur le banc de l'auteur | EXPLO 7.3, 7.4 | P2 | Sûr | Mesures seulement | 3 à 6 |

#### BILAN 3.11 Noms Mojang et portage

| ID | Correction | Source | Prio. | Verdict | Condition | Lot |
|---|---|---|---|---|---|---|
| C82 | Noms de Mojang sur 1.21.1 | REVUE 2.3 (B) ; NR 3.7 | P2 | Sûr si | G7 ; empreintes identiques ; chaînes qui échappent au renommage (réflexion des tests, cibles de mixins, accesseurs nommés, doc) | 8 |
| C83 | Portage vers 1.21.11, puis 26.x | REVUE 2.2, 2.3 (C, D) ; NR 3.7 | P2 | Voulu | Parité, pas identité : oracles inchangés, recette de PORTALS.md, G1 à G3, G6, G7, G11 ; tests en jeu côté client dès 1.21.11. Version visée : décision de l'auteur | 8, déc. |

#### BILAN 3.12 Constats sans correction

| Constat | Source | Pourquoi rien n'est à corriger |
|---|---|---|
| La traversée elle-même est rapide (bascule de 0,2 à 1,8 ms) | PERF 2.5 | Aucun défaut |
| Mémoire retenue après la déconnexion | PERF 3.11 | Le contexte de rendu de Fabric API garde le dernier monde ; aucune chaîne ne passe par le mod |
| Bande sombre de l'horizon de l'Overworld vu du Level 0 (scène V4) | NR 5.2 | Déjà décrite dans `docs/PORTALS.md` ; elle fait partie de la référence |
| Allocations de vanilla rejouées pour les vues (`displayTicks`, chaînes `"Sampler" + i`) | PERF 3.9 | Code de vanilla ; les vues ne font que l'appeler |

---

### BILAN 4. Les décisions de l'auteur

| Décision | Corrections | Ce qu'elle tranche |
|---|---|---|
| Changements visibles de texte | C03, C30 | Console d'un serveur dédié en anglais ; noms français corrigés |
| Délai d'apparition d'un portail | C56 | Plus de gel du serveur, contre un cadre qui ne devient portail qu'une fois les chunks prêts |
| Données des portails | C60, C61, C58 contre C63 | Retour instantané et moins de noir, contre réseau et mémoire ; monde quitté allégé ou chunks tenus plus longtemps |
| Image approchée | C62 b, C65 activée, C70, C71 | Brouillard à 1/255 près ; budget de vues ; paliers du LOD ; framebuffer par vue à rouvrir |
| Portée des vues | C73 | Voir les portails de plus loin, contre plus de vues, de réseau et de mémoire |
| Jeu | C59, C74, C75 | Cooldown par défaut ; cadres plus grands que 8 × 8 ; cadres trop hauts pour le Level 0 |
| Compatibilité | C15 b, C43 | Relever le chargeur minimal ou la Fabric API minimale écarte des joueurs |
| Carte graphique | C36, C76 | Doc seule, message en jeu, ou réglage de Windows |
| Options pour les joueurs | C55 | Un fichier de configuration lu au démarrage ; GUIDE §3.5 a reporté l'interface graphique |
| Version de Minecraft visée | C82, C83 | Jusqu'où porter, et quand ; selon le public visé et Iris |
| Refontes du socle | C47, C48, C50 | Règle d'or 2 : proposer, puis valider |
| Dépôt, publication, specs | C24, C28, C35, C40, C53, C54, C21, C80 | Workflow Git, specs de l'auteur, dossier de décisions, toolchain téléchargée, icône et publication, réglages GitHub, tag, scripts du banc |

---

### BILAN 5. Ce qui est écarté, et pourquoi

| Proposition | Raison | Source |
|---|---|---|
| Brouillard figé au tick | Le rendu décide sur la position affichée (CLAUDE.md) ; couture à la bascule | NR 3.6 |
| Brouillard partagé entre les vues, ou pris au tick puis interpolé, présenté comme sans écart | Change l'image : chaque vue a sa caméra, et la couleur dépend de la caméra, de l'heure et des chunks reçus. La variante approchée reste possible comme Voulu (C62 b) | NR 5.3 (3) |
| Monde quitté vidé d'un coup | Un retour rapide verrait un monde vide : couture | NR 3.6 |
| Création du monde distant étalée sur plusieurs images | Vue vide ou incomplète à ses premières images | NR 5.3 (5) |
| Budget de vues coupé d'office | Change l'image sans décision ; remplacé par une option (C65) | NR 3.8 (2) |
| `loader_version` dans `fabric.mod.json` | Relève le minimum de 0.16.9 à 0.19.5 | NR 5.3 (1) |
| Critère de JVM du démon à 25 dès maintenant | Exige un JDK 25 partout, CI comprise | NR 5.3 (2) |
| Tests en jeu joués une seule fois en CI, sans étape de rejeu | Perd la robustesse sur un monde déjà joué | NR 3.8 (4) |
| Pièges de CLAUDE.md déplacés derrière un simple renvoi | Les sessions ne les liraient plus | NR 3.8 (5) |
| `UtilityClassesTest` sans constructeurs qui refusent l'appel | Perd une garantie testée | NR 3.8 (6) |
| Parcours des sections d'une vue en arrière-plan | Une image de retard : vues qui clignotent (piège de CLAUDE.md) | EXPLO 4.2 |
| Chunks reçus appliqués hors du thread de rendu | Rompt le chemin `readLightData` des mods de terrain | EXPLO 4.2 |
| Requête d'occlusion lue à l'image suivante | Une image de retard au retour d'un portail dans le champ ; acceptable seulement en option | EXPLO 6.2 |
| Rayons vers une grille de points pour décider du rendu | Un interstice entre deux points ferait disparaître une vue visible | EXPLO 6.2 |
| Culling et dessin pilotés par le GPU (GPU6), logique des portails sur le GPU (GPU7) | OpenGL 4.3, absent de macOS ; réécriture du terrain vanilla ; rien de lourd à calculer | EXPLO 3.3 |
| Code natif (JNI), appel natif de Java (FFM), API vectorielle | Aucun calcul lourd à accélérer ; FFM exige Java 22 et l'API vectorielle reste en incubation ; bibliothèques natives par plateforme | EXPLO 4.3 |
| LOD par maillages simplifiés dans une vue | Un moteur de terrain lointain, en conflit avec les mods de terrain | EXPLO 5.3 |

---

### BILAN 6. L'ordre de mise en œuvre

| Lot | Contenu | Porte de sortie (en plus du build complet vert) |
|---|---|---|
| **0. Protections** | G1, G2 (octets), G3, G8 (a, b, c) avec C22, G10, rapport G9 | Garde-fous verts sur le code actuel ; références versionnées |
| **1. Sans risque** | C01, C02, C04, C07 à C12, C13, C23, C25 à C27, C29, C31 à C34, C36, C37, C38, C53 (contact). Après décision : C03, C30, C21 | Planchers G8 tenus ; la seule modification de test (C03) justifiée assertion par assertion |
| **2. Build et CI** | C14, C15, C16 (une action par PR), C17 avec G12, C18, C19, C20 ; G7 mis en place | G8 complet, canari compris ; `junit.xml` présent ; `runClient` et `quickPlayWorld` essayés |
| **3. Mesure fiable** | C77, C78, C79 ; puis nouvelle référence du banc G6 ; C80 sur décision ; campagnes de référence (C81) | Plus d'image lente fantôme sur S0 ; références G6 enregistrées |
| **4. Gel serveur** | C56 (après décision sur le délai), C57 ; G4 | G3 inchangé, G4 vert, tests de `PortalTargets` intacts |
| **5. Structure** | C47 à C52, C55 : une PR chacun ; C47, C48 et C50 après validation | G8 (c) mis à jour ; G6 et G11 pour le client ; G9 relu pour le serveur ; G7 pour les source sets et les déplacements |
| **6. Performance client** | C05, C06, C58, C62, C64, C65, C67, C69 ; G5 | G5 ; G6 (critère de NR 5.2) ; G11 ; aucun chiffre du banc en recul |
| **7. Outils** | C39, C41 à C46, un par PR | G7 (chargeur minimal et courant) ; G8 ; empreintes inchangées |
| **8. Noms Mojang, puis portage** | C82 ; C83 après décision | Empreintes identiques ; 749 et 99 tests verts ; G1 à G3 ; G6 ; G7 ; G11 ; recette de PORTALS.md |
| **Après décision** | C03, C30, C59, C60, C61, C62 b, C63, C70, C71, C74, C75, C76, C73, C15 b, C83, et les recherches C66, C68, C72 | Pour chaque changement voulu : annonce au CHANGELOG ; nouvelles références G6 si l'image change ; tests étendus, jamais affaiblis |

**Revenir en arrière** (NR 4.4) : chaque lot est fait de commits atomiques, un `git revert` défait une PR, et un tag
marque la fin de chaque lot. On ne retire jamais la lecture d'une ancienne clé NBT. Une régression trouvée en jeu
devient d'abord un test ou une scène du banc qui échoue, ensuite seulement un correctif.

---

### BILAN 7. Ce que ce bilan ne garantit pas

- **L'équivalence visuelle** ne vaut que pour les scènes et la tolérance du banc G6, sur le chemin vanilla de ce PC.
  Le mod de terrain et le pack de shaders passent par G11, à la main, sur le banc de l'auteur.
- **Le ressenti** (fluidité, latence d'une traversée) reste un jugement humain.
- **Les chiffres de performance** de ce portable sont bruyants (jusqu'à ±25 %) : seule une tendance nette et répétée
  compte.
- **Le portage** vise la parité, pas l'identité.
- **Les gains annoncés** pour les pistes non prototypées (C56, C57, C62, C64, C67, C69…) sont des hypothèses, jusqu'à
  leur mesure (règle d'or 11).

---

### BILAN — Annexe : où chaque point des autres parties est traité

Table produite depuis les colonnes « Source » de ce bilan. Un point des autres parties absent de cette table n'est pas
traité, et c'est une erreur du bilan. Chaque point numéroté de REVUE (sections 1 à 3) et chaque diagnostic de
PERF (section 3) y figure.

| Point | Traité par |
|---|---|
| REVUE 1.1.1 | C01 |
| REVUE 1.1.2 | C02 |
| REVUE 1.1.3 | C03 |
| REVUE 1.1.4 | C32, C48 |
| REVUE 1.1.5 | C04 |
| REVUE 1.2.1 | C07 |
| REVUE 1.2.2 | C08 |
| REVUE 1.2.3 | C09 |
| REVUE 1.2.4 | C10, C23 |
| REVUE 1.2.5 | C11 |
| REVUE 1.3.1 | C13 |
| REVUE 1.3.2 | C14 |
| REVUE 1.3.3 | C15 |
| REVUE 1.3.4 | C21 |
| REVUE 1.3.5 | C16 |
| REVUE 1.3.6 | C17, G12 |
| REVUE 1.3.7 | C18 |
| REVUE 1.4.1 | C22, C49 |
| REVUE 1.4.2 | C23 |
| REVUE 1.4.3 | C24 |
| REVUE 1.4.4 | C25 |
| REVUE 1.4.5 | C26 |
| REVUE 1.4.6 | C27 |
| REVUE 1.4.7 | C28 |
| REVUE 1.4.8 | C28 |
| REVUE 1.4.9 | C28 |
| REVUE 1.4.10 | C29 |
| REVUE 1.4.11 | C30 |
| REVUE 1.4.12 | C31 |
| REVUE 1.5.1 | C37 |
| REVUE 1.5.2 | C38 |
| REVUE 1.5.3 | C39 |
| REVUE 2.1 | C41 |
| REVUE 2.2 | C42, C83, G7 |
| REVUE 2.3 | C39, C41, C42, C43, C44, C45, C46, C82, C83 |
| REVUE 3.1 | C47 |
| REVUE 3.2 | C48 |
| REVUE 3.3 | G10 |
| REVUE 3.4 | C22, C49 |
| REVUE 3.5 | C50 |
| REVUE 3.6 | C51 |
| REVUE 3.7 | C18, C19 |
| REVUE 3.8 | C52 |
| REVUE 3.9 | C12, G9 |
| REVUE 3.10 | C20 |
| REVUE 3.11 | C53 |
| REVUE 3.12 | C24, C54 |
| REVUE 3.13 | C27, C28, C33, C34, C35 |
| REVUE 3.14 | C55 |
| REVUE 3.15 | C37, C38, C40 |
| PERF 2.5 | constat |
| PERF 3.1 | C36 |
| PERF 3.2 | C56, G4 |
| PERF 3.3 | C62 |
| PERF 3.4 | C32, C55, C65, C66, C73 |
| PERF 3.5 | C63 |
| PERF 3.6 | C36, C67, C68 |
| PERF 3.7 | C58, C59 |
| PERF 3.8 | C77 |
| PERF 3.9 | C05, C06, constat |
| PERF 3.10 | C64 |
| PERF 3.11 | constat |
| PERF 3.12 | C75 |
| PERF 5 | C80, G6 |
| EXPLO 2 | C60, C61, C64, C71, C72, C73 |
| EXPLO 3.2 | C36, C76 |
| EXPLO 3.3 | C67, C71, C78, écarté |
| EXPLO 4.2 | C57, écarté |
| EXPLO 4.3 | écarté |
| EXPLO 5 | C70 |
| EXPLO 5.2 | C71 |
| EXPLO 5.3 | C74, écarté |
| EXPLO 6.2 | C69, écarté |
| EXPLO 6.3 | C60 |
| EXPLO 7.2 | C79 |
| EXPLO 7.3 | C81 |
| EXPLO 7.4 | C81 |
| EXPLO 7.5 | C79 |
| EXPLO 8 | C60, C61 |
| NR 3.1 | C30 |
| NR 3.2 | C03 |
| NR 3.4 | C43 |
| NR 3.6 | C56, écarté |
| NR 3.7 | C82, C83 |
| NR 3.8 | C12, C17, C33, C65, G12, écarté |
| NR 4.2 | C80, G1, G2, G3, G4, G5, G6, G7, G8, G9, G10, G11, G12 |
| NR 4.4 | G1 |
| NR 5.2 | G6, constat |
| NR 5.3 | C15, C38, C56, C62, C63, C64, écarté |
| NR 5.4 | C59 |
| REF 1 | C22, G8 |
| REF 2 | G10 |
| REF 3 | C12 |
| REF 4 | C04, C05, G5 |
| REF 7 | G8 |

---

## REVUE — La revue du projet

> Revue du 24 octobre 2026. **Rien n'a été corrigé** : chaque point attend la décision de l'auteur. C'est un relevé de
> travail : à mettre à jour à mesure que les points sont traités.
>
> Avant toute mise en œuvre, lire la partie NR. Elle donne l'ordre des lots, les garde-fous à poser avant chaque
> changement, la référence mesurée à ne pas perdre, et la reformulation de trois propositions de cette partie (1.3.6,
> 3.9, 3.13) qui, telles qu'écrites, feraient perdre une qualité existante. Sa section 5 (25 octobre) exige en plus
> qu'aucune fonctionnalité ni aucune image ne change. Elle a corrigé ici les propositions 1.3.3 et 3.15, et met à part,
> pour décision de l'auteur, celles qui changent volontairement ce que voit ou peut faire le joueur (1.1.3, 1.4.11, un
> minimum de chargeur ou de Fabric API relevé en 1.3.3 et 2.3, et le portage). Les données de référence de la 0.10.0
> sont dans la partie REF. Les pistes de rendu demandées ensuite (carte graphique, LOD, vues cachées) sont dans la
> partie EXPLO.

### REVUE — Comment lire cette partie

- **P1** : à traiter en premier (une règle du projet n'est pas tenue, ou un risque réel). **P2** : cohérence et qualité.
  **P3** : confort, finition.
- Chaque point donne le constat, la preuve (fichier et ligne) et une proposition. Les propositions structurantes sont à
  valider avant d'être faites (règle d'or 10 de `docs/GUIDE.md`).
- Méthode : lecture intégrale du code, des tests, des tests en jeu, de la doc et des specs ; contrôles automatisés
  (annexe B) ; versions relevées le 24 octobre 2026 sur les dépôts officiels (Maven de Fabric, Maven Central, services
  Gradle, Adoptium, manifeste Mojang, GitHub, Modrinth).
- Non vérifié à la date de cette revue : aucun build n'avait été lancé ; les PR Dependabot éventuellement ouvertes sur
  GitHub n'ont pas été consultées (CLI `gh` absente) ; rien n'avait été essayé en jeu. Depuis, le build complet a été
  lancé (référence de NR 1) et les portails mesurés en jeu (partie PERF).

---

### REVUE — Les dix premières actions que je proposerais

Cette liste classe par importance, pas par ordre d'exécution. L'ordre sûr est celui des lots (BILAN 6, NR 4.3) : les
garde-fous d'abord (lot 0), les montées d'outils au lot 7, chacune sous ses garde-fous (G7, G8).

1. **Mettre à jour la chaîne d'outils sans changer de Minecraft** (section 2.3, étape A) : gain immédiat, risque faible.
2. **Migrer les appels dépréciés de Loom** dans `build.gradle` (`vmArg`, `programArgs`, `runDir`), préalable au point 1.
3. **Faire échouer la compilation sur un avertissement javac** : la CI annonce « zéro warning javac » sans le vérifier.
4. **Séparer le code client du code commun à la compilation** (source sets séparés de Loom).
5. **Ramener les exclusions de couverture à la vraie colle GL** : 44 % des lignes du mod sont aujourd'hui hors mesure.
6. **Écrire les règles d'architecture en tests** (socle sans contenu, commun sans client, GL seulement dans la colle).
7. **Choisir la version de Minecraft visée**, et passer d'abord aux noms Mojang sur 1.21.1 (section 2.3, étape B).
8. **Corriger les petites incohérences de code et de tests** (sections 1.1 et 1.2), une heure de travail.
9. **Remettre la doc d'équerre** (CONTRIBUTING, skills, AUDIT, `.vscode/settings.json`).
10. **Publier la 0.10.0** : aucun tag n'existe, le workflow de release n'a donc jamais tourné pour elle.

---

### REVUE 1. Incohérences relevées

#### REVUE 1.1 Code et commentaires

1. **P2. Le biome rouge n'a aucun néon, sa Javadoc dit « néons rares ».**
   [LevelZeroSurfaceBiome.java:18-19](../../src/main/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/biome/LevelZeroSurfaceBiome.java#L18-L19) :
   densité lumineuse `0/1`, donc `keepsStripedLight` rend toujours faux. Corriger le commentaire, pas la valeur (la
   changer déplacerait les empreintes de `LevelZeroGoldenTest`).
2. **P3. Javadoc orpheline dans `MixinWorldRenderer`.**
   [MixinWorldRenderer.java:179-197](../../src/main/java/com/gtgpuke/gtgsbackrooms/mixin/client/MixinWorldRenderer.java#L179-L197) :
   deux Javadoc empilées au-dessus de `backrooms$reloadViews` ; la première décrit `backrooms$mirrorChunkRender`, qui se
   retrouve sans commentaire. Déplacer le bloc au-dessus de la bonne méthode.
3. **P2. Messages de la commande de debug en texte brut.**
   [DebugPortalCommand.java:53-68](../../src/main/java/com/gtgpuke/gtgsbackrooms/core/portal/debug/DebugPortalCommand.java#L53-L68) :
   `Text.literal("... portail(s) charge(s) ...")`. CLAUDE.md impose `Text.translatable()` et des clés EN et FR pour tout
   texte visible en jeu. Comme les sources sont en ASCII, le message s'affiche en plus sans accents. Passer par des clés
   de traduction (les accents sont permis dans les fichiers de langue). Changement visible : la console d'un serveur
   dédié affichera ces messages en anglais. À décider par l'auteur (NR 5.4).
4. **P3. Le socle dépend du contenu un peu plus que ce que dit CLAUDE.md.** CLAUDE.md : « `LevelZeroArrival` : seul
   lien, avec les registres, du socle vers le contenu ». En réalité
   [PortalTargets.java](../../src/main/java/com/gtgpuke/gtgsbackrooms/core/portal/block/PortalTargets.java) importe aussi
   `BackroomsConstants` (la clé du monde du Level 0). Soit corriger la phrase, soit inverser la dépendance (section 3.2).
5. **P3. Une allocation dans un tick.**
   [ArrivalGuard.java](../../src/main/java/com/gtgpuke/gtgsbackrooms/core/portal/transition/ArrivalGuard.java) (`tick`,
   commit `d331d53`) crée un nouveau `Guard` quand l'entité se pose. C'est une fois par arrivée, donc négligeable,
   mais c'est une exception à la règle « aucune allocation dans tick/render » : soit la noter, soit tenir l'heure de
   pose dans un champ mutable.

#### REVUE 1.2 Tests

1. **P2. Une assertion qui ne vérifie rien.**
   [LevelZeroWallSamplerTest.java:63](../../src/test/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/generation/write/LevelZeroWallSamplerTest.java#L63) :
   `assertTrue(mixedColumns >= 0)` est toujours vrai. La couverture est atteinte, rien n'est vérifié. Écrire la valeur
   attendue, ou au moins `> 0` si la scène le garantit.
2. **P2. Un test vérifie un arbitrage qui n'existe plus.**
   [LevelZeroCorridorGapsTest.java:55-56](../../src/test/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/generation/layout/LevelZeroCorridorGapsTest.java#L55-L56)
   et `hasWinningNeighbor` (ligne 134) parlent d'« arbitrage de voisin » ;
   [LevelZeroCorridorGaps.java:41](../../src/main/java/com/gtgpuke/gtgsbackrooms/backrooms/level0/generation/layout/LevelZeroCorridorGaps.java#L41)
   dit qu'aucun arbitrage entre voisins n'est nécessaire. La branche du test qui l'accepte est morte : la retirer.
3. **P3. Nombre de chunks faux dans une Javadoc de test.**
   [GameTestServerWorlds.java:78](../../src/gametest/java/com/gtgpuke/gtgsbackrooms/gametest/support/GameTestServerWorlds.java#L78) :
   « 9 x 9 chunks », alors qu'`ARRIVAL_RADIUS = 5` (ligne 44) en génère 11 × 11.
4. **P3. De la réflexion dans les tests, contre CONTRIBUTING.** CONTRIBUTING demande des tests « pas de réflexion ».
   Trois fichiers en utilisent : `UtilityClassesTest` (constructeurs privés), `PortalPropagationGameTests` (champ
   protégé des objectifs d'un mob) et `GameTestServerWorlds` (monde du Level 0 prêté au serveur de test). Les deux
   derniers pourraient passer par des accesseurs (mixin ou access widener) propres au mod de test. Le premier garde sa
   réflexion : c'est elle qui vérifie que le constructeur refuse l'appel, et `docs/TESTING.md` l'admet pour les classes
   utilitaires. Seule sa liste à la main peut céder la place à une découverte (section 3.9, et NR 3.8).
5. **P3. Le répit de la garde d'arrivée n'a pas de test unitaire.** `ArrivalGuard.GRACE_TICKS` et `landedAt` ne sont
   vérifiés que par `PortalTunnelGameTests`. `ArrivalGuardTest` pourrait couvrir la logique pure (posée, répit écoulé).

#### REVUE 1.3 Build, CI et publication

1. **P1. La CI annonce « zéro warning javac » sans le vérifier.** L'étape s'appelle « Conventions du code
   (Checkstyle, zero warning javac) », mais [build.gradle:300-304](../../build.gradle#L300-L304) n'a pas `-Werror`. Un
   avertissement de javac s'affiche sans faire échouer le build, et `--warning-mode all` ne concerne que les
   dépréciations de Gradle. Ajouter `-Werror`, ou renommer l'étape.
2. **P1. Le build utilise une API de Loom dépréciée.** Loom 1.17 a déprécié l'ancienne API des configurations de
   lancement. `build.gradle` appelle `vmArg` (lignes 43, 67, 69), `programArgs` (ligne 46) et `runDir =` (ligne 51), tous
   marqués `@Deprecated` dans `RunConfigSettings`. Il faut passer à l'API par propriétés (`jvmArguments`, etc.) avant
   une montée de Loom qui les retirerait.
3. **P2. `fabric.mod.json` accepte n'importe quelle Fabric API.**
   [fabric.mod.json](../../src/main/resources/fabric.mod.json) déclare `"fabric-api": "*"` : une Fabric API trop ancienne
   pour les API utilisées passerait la résolution et planterait plus tard. De même, `processResources` transmet
   `loader_version` et `minecraft_version`, mais `fabric.mod.json` écrit ses bornes en dur (`>=0.16.9`, `~1.21.1`).

   **Proposition corrigée le 25 octobre** (NR 5.3) :
   - ne pas utiliser `loader_version` pour la borne du chargeur. Elle vaut 0.19.5 : la borne passerait de `>=0.16.9`
     à `>=0.19.5`, et les joueurs sous un chargeur plus ancien ne pourraient plus lancer le mod. Retirer
     `loader_version` de la liste, ou ajouter une propriété dédiée au minimum. `~${minecraft_version}` peut servir :
     il donne la même borne ;
   - pour Fabric API, déclarer la plus ancienne version essayée avec succès en production (G7), pas la version de
     compilation ; tant qu'aucune n'est essayée, garder `*`.
4. **P2. Aucun tag de release, alors que le workflow de release se déclenche sur les tags.** `git tag` est vide.
   Le CHANGELOG date la 0.10.0 du 2026-10-23, mais elle n'a jamais été publiée par `release.yml`. Le commit `d331d53`
   a en plus retouché l'entrée 0.10.0 au lieu de `[Unreleased]`, ce qui se justifie tant que rien n'est tagué. Taguer
   `v0.10.0` une fois la version arrêtée.
5. **P2. Les actions GitHub ont deux à trois versions majeures de retard** (section 2.1). Dependabot les suit tous
   les mois : soit ses PR attendent, soit elles ont été fermées. À regarder sur GitHub.
6. **P3. La CI rejoue trois fois les tests en jeu.** Étapes `runGameTest`, puis `jacocoFullReport` (qui en dépend), puis
   `build`. CLAUDE.md présente ce triple passage comme un fait ; il a une vertu (un test doit tenir sur un monde déjà
   joué), mais il coûte du temps de CI. Si le but est la robustesse, le dire et le garder. Une seule invocation `build`
   n'est acceptable qu'avec une étape explicite qui rejoue les tests en jeu sur le monde déjà joué (NR 4.2, G12) :
   sinon, cette robustesse se perd en silence.
7. **P3. La configuration cache est coupée** (`org.gradle.configuration-cache=false`). Plusieurs tâches maison ne la
   supportent pas en l'état (lecture de `configurations` et de closures du script à l'exécution). Loom 1.18+ améliore
   son support. Voir section 3.7.

#### REVUE 1.4 Documentation et règles

1. **P1. La règle de couverture et la pratique divergent.** GUIDE.md et CLAUDE.md : « Colle OpenGL et OpenAL :
   minimale, isolée, nommée dans TESTING.md ; tout le reste est couvert à 100 % ». En réalité, les exclusions de
   [build.gradle:131-150](../../build.gradle#L131-L150) retirent **10 096 lignes sur 22 802 (44 %)**. Le détail est dans
   l'annexe A. Les mixins (2 032 lignes) sont une exclusion classique. Mais `core/portal/client/world/**` entier
   (3 501 lignes) contient de la logique pure : le parcours en largeur de `ViewSectionWalker`, la sélection de
   `ClientPortalIndex`, les décisions de `PortalSightReporter` et `StraddlingEntityRenderer`. Il contient aussi deux
   classes qui ont des tests unitaires mais ne sont pas mesurées (`ClippedVertexConsumers`, `RemoteViewRenderer`).
   `client/transition/**` (982 lignes) est surtout de la logique. Proposition en section 3.4.
2. **P2. Largeur de ligne contradictoire.** CONTRIBUTING (« lignes ≤ 130 caractères ») contre Checkstyle
   (`LineLength` à 180). Garder la valeur que l'outil impose et corriger la phrase.
3. **P2. Le workflow Git décrit n'est pas celui pratiqué.** CONTRIBUTING décrit des branches `feature/xxx` et
   `fix/xxx`, et des messages `type(scope): message`. L'historique est fait de commits directs sur `master`, sans portée,
   et parfois sans information (`fix: fixing shits`, `feat: dodo`, `feat: pas mal d'upgrade en pagaille...`). Soit
   appliquer la règle (protection de branche, PR, vérification du message), soit l'assouplir dans CONTRIBUTING.
4. **P2. Des autres mods nommés hors des exceptions de FINALCHECK.** FINALCHECK n'autorise que les specs de l'auteur
   et la section 8 de GUIDE.md. Hors de ces exceptions :
   - `.skills/add-dependency/SKILL.md`, 30 mentions, qui sert en plus d'exemple de code ;
   - `docs/AUDIT.md`, 2 mentions ;
   - GUIDE.md hors de sa section 8 : lignes 23, 170, 239, 539, 544, 728 et 732.

   Le code, les tests, README et CLAUDE.md sont propres (Iris excepté, comme accordé).
5. **P2. Le skill `add-dependency` contredit le build.** Il recommande `include` (jar-in-jar) ; `verifyJarContents`
   refuse toute dépendance embarquée. Le skill `pre-push-audit` est plus ancien que `docs/prompt/BEFOREPUSH.md`, qui
   fait autorité. Mettre les deux au niveau, ou retirer `pre-push-audit` au profit du prompt.
6. **P2. L'« audit complet » de référence date de deux versions.** CLAUDE.md renvoie à `docs/AUDIT.md` (2026-10-15,
   version 0.8.0). Depuis : 0.9.0, 0.10.0, changement d'arborescence, retrait de contenu. L'audit reste juste comme
   historique et comme référence de performance, mais n'est plus le « dernier audit complet ». Le dire en tête, et en
   faire un nouveau après la 0.10.0.
7. **P2. Deux versions du guide coexistent.** `docs/backrooms/backrooms-mod-plan.md` (ajouté par `d331d53`) est le
   brouillon du 19 octobre de GUIDE.md. Il garde l'ancienne arborescence (`init/`, `feature/`, `backrooms/portal/`) et
   « 36 paires d'orientations » (144 aujourd'hui). `docs/INDEX.md` ne le cite pas. Ce sont des specs de l'auteur, je
   ne fais que signaler. Décider s'il s'agit d'une archive (et le dire en tête), ou le retirer.
8. **P2. `BACKROOMS_CONTENT_RULES.md` contient des consignes techniques contraires au projet.** C'est un document de
   game design écrit pour une autre plateforme de mods et pour une version future de Minecraft. Sa section 7, « Notes
   techniques pour Claude Code », prescrit d'embarquer des dépendances (jar-in-jar), un fichier `mods.toml` et un
   portail tiers optionnel. Tout cela contredit `verifyJarContents`, la pile Fabric et le système de portails déjà
   construit. INDEX.md le présente comme « règles de contenu et direction artistique ». Risque : une session qui en
   suivrait la section 7 à la lettre. Proposition : une ligne en tête du fichier (par l'auteur) disant que seules les
   sections de contenu font foi.
9. **P3. Titre et nom de fichier de la spec du pipeline ne s'accordent pas.**
   `docs/backrooms/backrooms-level0-pipeline-v6.md` a pour titre « (v2) ».
10. **P3. Le nombre de tests du README n'est pas tenu à jour.** « ~500 tests JUnit 5 » : le dépôt compte 298 méthodes
    `@Test`/`@ParameterizedTest` et 99 tests en jeu ; le chiffre de 500 ne vaut qu'en comptant les invocations
    paramétrées. Écrire un chiffre vérifiable, ou ne pas en donner.
11. **P3. Traductions françaises.**
    [fr_fr.json:10](../../src/main/resources/assets/gtgs_backrooms/lang/fr_fr.json#L10) : « Neon du Niveau 0 » sans
    accent. Les noms de blocs ont une majuscule à chaque mot (« Papier Peint du Niveau 0 »), celui de la baguette non
    (« Baguette de portail »). L'usage français est le second. Changement visible des noms en jeu : à décider par
    l'auteur (NR 5.4).
12. **P3. Au-delà d'un niveau d'imbrication, GUIDE et PORTALS ne disent pas la même chose.** Relevé le 25 octobre.
    [GUIDE.md](../GUIDE.md) (section 2.2) écrit « Un seul niveau d'imbrication supporté ; au-delà, cadre sombre ».
    [PORTALS.md](../PORTALS.md) écrit « Au-delà de ce niveau, un portail est transparent », et le code lui donne
    raison : une vue imbriquée ne cherche pas de portail plus profond, et le renderer d'entité ne dessine rien tant que
    le rendu distant marche. Le cadre sombre ne vaut que pour un portail imbriqué dont l'arrivée n'a pas de données.
    Corriger la phrase de GUIDE, ou la consigner dans sa section 2.4 (règle d'or 11).

#### REVUE 1.5 Environnement de développement (cette machine)

1. **P1. `.vscode/settings.json` est absent du dépôt.** CLAUDE.md compte dessus pour couper la compilation automatique
   de l'extension Java, et `.gitignore` le laisse passer (`!.vscode/settings.json`), mais il n'a jamais été commité.
   Sur un clone neuf, l'extension peut écrire dans `build/classes/java/main` des classes « Unresolved compilation
   problems » qui font planter le client.
2. **P2. Le démon Gradle tourne sur le JDK 25** (`JAVA_HOME = C:\Program Files\Java\jdk-25.0.2`). La toolchain trouve le
   Temurin 21 pour compiler et tester, mais le démon tourne sur 25. Ce n'est pas un problème avec Gradle 9.8 ; c'est même
   ce que demandera Loom 1.18+ (section 2.2). Mais rien ne le fixe : sur une autre machine, le démon prendra le JDK de
   `JAVA_HOME`, quel qu'il soit. Voir section 3.15 (critères de JVM du démon : 21 tant que Loom reste en 1.17).
3. **P3. Les JDK installés ont du retard de correctifs** : Temurin 21.0.8 contre 21.0.12.1, JDK 25.0.2 contre 25.0.4.1.

---

### REVUE 2. Mises à jour : outils, JDK, Minecraft

#### REVUE 2.1 Relevé des versions (24 octobre 2026)

| Outil | Dans le projet | Dernière version | Sans changer de Minecraft ? |
|---|---|---|---|
| JDK de compilation (toolchain) | 21 | 21.0.12.1 (correctif) ; LTS la plus récente : 25.0.4.1 ; dernière version : 27 | Oui pour les correctifs. Minecraft 1.21.x vise Java 21 : garder 21 tant qu'on reste en 1.21.x |
| JDK du démon Gradle | celui de `JAVA_HOME` (25.0.2 ici) | 25.0.4.1 | Oui |
| Gradle (wrapper) | 9.8.1 | 9.8.2 | Oui |
| Fabric Loom | 1.17.21 (id `fabric-loom`) | 1.19.3 | Oui, mais Loom 1.18+ exige de lancer Gradle sous Java 25 |
| Fabric Loader | 0.19.5 | 0.20.1 (Mixin 0.17.5, ASM, tiny-remapper) | Oui |
| Fabric API | 0.116.17+1.21.1 | dernière pour 1.21.1 ✓ (0.165.0 pour 26.3) | Déjà à jour |
| Yarn | 1.21.1+build.3 | dernière pour 1.21.1 ✓ ; Yarn s'arrête à 1.21.11+build.6 | Déjà à jour |
| Iris (compilation seulement) | 1.8.8+1.21.1 | dernière stable pour 1.21.1 ✓ (1.8.14-beta.1 existe) | Déjà à jour |
| JUnit | 5.11.4 | 5.14.4 (branche 5) ; 6.1.3 (branche 6) | Oui |
| Checkstyle | 10.21.1 | 14.3.0 | Oui (quatre versions majeures : relire les notes de version) |
| JaCoCo | non épinglé (défaut de Gradle) | 0.8.15, le défaut de Gradle 9.8.2 | Oui |
| `actions/checkout` | v4 | v7 | Oui |
| `actions/setup-java` | v4 | v6 | Oui |
| `gradle/actions` | v4 | v6 | Oui |
| `actions/upload-artifact` | v4 | v7 | Oui |
| `softprops/action-gh-release` | v2 | v3 | Oui |
| **Minecraft** | **1.21.1** (août 2024) | **26.3** (15 septembre 2026), 26.4 en snapshot | **Non : c'est un portage** |

Versions de Minecraft publiées depuis 1.21.1 : 1.21.2 à 1.21.11 (dernière en 2025-12-09), puis la numérotation par
année : 26.1 (2026-03-24), 26.1.1, 26.1.2, 26.2 (2026-06-16), 26.3 (2026-09-15).

#### REVUE 2.2 Ce que ces versions impliquent

- **Loom 1.18 et après** remplacent leur bibliothèque native par l'API FFM de Java 25 : Gradle doit tourner sous
  Java 25. La toolchain de compilation peut rester à 21. Cette machine a déjà le JDK 25. La CI doit installer les deux
  (`setup-java` accepte plusieurs versions), ou laisser Gradle télécharger la toolchain.
- **Loom 1.14** a introduit deux identifiants de plugin : `net.fabricmc.fabric-loom-remap` pour les versions
  obfusquées (1.21.1 en est une), `net.fabricmc.fabric-loom` pour les versions non obfusquées (26.x). `fabric-loom`
  reste pour la compatibilité. Passer à `net.fabricmc.fabric-loom-remap` dès maintenant prépare la suite.
- **Loom 1.17** a déprécié l'ancienne API des configurations de lancement (point 1.3.2). **Loom 1.18** lance par
  défaut les configurations d'IDE par les tâches Gradle et améliore la configuration cache.
- **Fabric Loader 0.20** met à jour Mixin (0.17.5). Le piège de `@Redirect` (CLAUDE.md) vient d'un écart de versions
  de Mixin et MixinExtras entre chargeurs. Après la mise à jour, il faut rejouer l'essai d'un chargeur ancien en
  production, puisque `fabric.mod.json` accepte `>=0.16.9`.
- **JUnit 6** exige Java 17 et retire des API dépréciées de la branche 5. Vérifier qu'il s'accorde avec
  `fabric-loader-junit` ; sinon, 5.14.4.
- **Minecraft 26.x** : Java 25 obligatoire (le manifeste de 26.1 et de 26.3 demande la version 25). Le jeu n'est plus
  obfusqué : il n'y a plus de mappings Yarn, le code s'écrit avec les noms de Mojang, et Loom saute le remapping.
  **1.21.11** est la dernière version obfusquée, encore sous Java 21, et la dernière couverte par Yarn.
- **Tests en jeu côté client** : l'API `fabric-client-gametest-api-v1` est absente de Fabric API 0.116.17+1.21.1. Elle
  est présente dans les versions pour 1.21.11 et 26.3 (vérifié dans leurs POM). Ce serait la première façon
  d'automatiser le test du rendu des portails (captures comparées), aujourd'hui « non couvert automatiquement ».
- **Coût d'un portage** : le rendu des portails tient à l'intérieur de `WorldRenderer.render` (injections, shaders,
  tampons de sommets, framebuffers, `RenderSystem`). Minecraft a profondément réécrit sa couche de rendu entre 1.21.2
  et 1.21.11 (pipelines de rendu, abstraction du GPU, rendu des entités par états extraits). Une grande partie de
  `core/portal/client/` et de `mixin/client/` serait à réécrire, avec un nouveau relevé des pièges de CLAUDE.md. La
  génération du Level 0 (logique pure, `StageRandom` maison) devrait garder ses empreintes, à vérifier au premier
  build.

#### REVUE 2.3 Plan de mise à jour proposé, par étapes livrables

- **Étape A : outils, sans changer de Minecraft (faible risque, sous les garde-fous G7 et G8 de NR 4.2).**
  1. Correctifs JDK : Temurin 21.0.12.1 et JDK 25.0.4.1.
  2. Migrer les appels dépréciés de Loom (`vmArg`, `programArgs`, `runDir`).
  3. Monter Gradle en 9.8.2, Loom en 1.19.3 (plugin `net.fabricmc.fabric-loom-remap`), et faire tourner le démon sous
     Java 25 (section 3.15), dans le même lot que la CI : avant Loom 1.18, le critère du démon reste 21.
  4. Monter Fabric Loader en 0.20.1 (seulement après un essai en production avec le chargeur minimal déclaré, G7 :
     NR 3.4), JUnit (5.14.4 ou 6.1.3), Checkstyle 14.3.0 (règles renommées ou retirées à relire), et épingler JaCoCo
     0.8.15.
  5. Monter les actions GitHub en v7/v6/v6/v7/v3, avec Java 25 en plus de 21 dans la CI si Loom passe en 1.18+
     (point 3).

  Porte de sortie : `./gradlew clean build --warning-mode all` vert, empreintes du Level 0 inchangées, essai en jeu
  rapide (portail, traversée, vue).
- **Étape B : passer aux noms Mojang sur 1.21.1 (mécanique, gros diff).** Loom sait utiliser les mappings officiels de
  Mojang pour 1.21.1 et fournit une tâche de migration des noms (`migrateMappings`, à confirmer dans la doc Fabric le
  moment venu). Le renommage est ainsi découplé du portage : on vérifie que tout marche avec les nouveaux noms avant de
  changer de version. CLAUDE.md, PORTALS.md et les commentaires citent des noms Yarn (`WorldRenderer`, `ClientWorld`…) :
  à traduire en même temps.
- **Étape C : 1.21.1 → 1.21.11 (Java 21, encore obfusqué).** C'est le vrai portage du rendu, par jalons :
  1. compilation ;
  2. génération et serveur (tests en jeu) ;
  3. vues de portail ;
  4. pack de shaders.

  On y gagne les tests en jeu côté client.
- **Étape D : 1.21.11 → 26.x (Java 25, non obfusqué).** Plugin `net.fabricmc.fabric-loom`, plus de remapping, toolchain
  25.

Décision à prendre par l'auteur : jusqu'où aller, et quand. Le critère principal est la version jouée par le public
visé et la disponibilité de Iris pour cette version.

---

### REVUE 3. Pistes pour un projet plus professionnel

#### REVUE 3.1 Séparer client et commun à la compilation (P1)

Aujourd'hui, la règle « code client uniquement sous `client/` ou `mixin/client/` » n'est vérifiée qu'à l'exécution (un
serveur dédié qui charge une classe client plante). Loom propose des source sets séparés
(`splitEnvironmentSourceSets()`, code client sous `src/client/java`, mixins client dans leur propre configuration). Une
référence du commun vers le client devient alors une **erreur de compilation**. Il faudrait déplacer la soixantaine de
fichiers client et adapter les exclusions de couverture ; les tests en jeu ne changent pas. Le test d'architecture de la
section 3.3 donne la même garantie au build sans déplacer de fichier (la règle tient aujourd'hui : REF 2).

#### REVUE 3.2 Inverser la dépendance du socle vers le contenu (P2)

`core/` (le socle) connaît le contenu :
- `ModBlocks` enregistre `LevelZeroWallpaperBlock` ;
- `ModChunkGenerators` enregistre `LevelZeroChunkGenerator` ;
- `PortalTargets` code la paire automatique Level 0 ↔ Overworld et appelle `LevelZeroArrival`.

Deux mouvements rendraient le socle réutilisable pour les futurs niveaux, sans couche d'abstraction :
- **Les registres hors du socle** : `registry/` à la racine du mod (`com.gtgpuke.gtgsbackrooms.registry`), comme
  couche de composition. Ou bien chaque domaine enregistre ses propres objets.
- **Un point d'accès dans le portail** : le contenu déclare sa destination automatique et sa recherche d'arrivée au
  démarrage (une méthode d'enregistrement appelée par `GtgsBackroomsMod`). C'est exactement « étendre par des points
  d'accès » (GUIDE, règle d'or 2), et ce sera nécessaire au premier niveau 1 (portails niveau ↔ niveau, GUIDE 5.1 bis).

#### REVUE 3.3 Écrire les règles d'architecture en tests (P1)

Plusieurs règles du projet ne sont écrites qu'en prose. Une bibliothèque de tests d'architecture, en dépendance de test
seulement (jamais dans le JAR), les ferait tenir au build :
- `core..` ne dépend pas de `backrooms..`, sauf une liste blanche explicite ;
- le commun ne dépend pas de `..client..` (inutile si 3.1 est fait) ;
- `RenderSystem`, `GlStateManager` et les classes `GL*` n'apparaissent que dans les classes de colle GL nommées ;
- les classes de `mixin..` ne sont référencées que par la configuration Mixin ;
- aucune classe hors `ModConstants` ne crée de logger (aujourd'hui une regex Checkstyle).

Vérifié le 25 octobre sur le bytecode : les cinq règles sont vraies aujourd'hui. Les listes blanches exactes sont dans
REF 2 : cinq dépendances du socle vers le contenu, sept classes de colle GL, et 19 interfaces d'accès des mixins. Écrire
ces tests ne demande aucun changement du code.

#### REVUE 3.4 Rendre la frontière « colle GL » structurelle (P1)

C'est la conséquence du point 1.4.1. Trois propositions :
- **Extraire la logique pure des classes exclues** vers des classes mesurées : parcours de `ViewSectionWalker`,
  sélection de `ClientPortalIndex`, décisions de `PortalSightReporter`, `EntityHandover`, `ClientPortalCrossing`
  (prédiction) et `StraddlingEntityRenderer`, calculs de `RemoteViewRenderer` (`gridCenter`, `arrivalObstructed`).
  Plusieurs ont déjà des tests qui ne comptent pas. Les classes qui servent aussi les chemins « mod de terrain » et
  « pack de shaders » ne se touchent qu'avec la matrice de compatibilité sur le banc de l'auteur (NR 5.3, G11).
- **Ranger la vraie colle dans des paquets dédiés** (par exemple `client/gl/`) et exclure par paquet. La liste de 18
  motifs de `build.gradle` est fragile : une classe ajoutée dans `client/world/` sort de la mesure sans que personne le
  décide. Vérifié le 25 octobre : 29 classes nommées et deux paquets excluent exactement les mêmes 87 fichiers. Passer à
  une liste explicite ne change donc rien à la mesure (REF 1).
- **Se fixer un objectif chiffré** : hors mixins, la part exclue passe de 8 000 lignes environ à la seule colle GL et
  OpenAL, et TESTING.md la publie.

#### REVUE 3.5 Découper les plus gros fichiers par responsabilité (P3, à valider)

Neuf classes dépassent 500 lignes :

| Classe | Lignes |
|---|---|
| `PortalStencilRenderer` | 961 |
| `RemoteViewRenderer` | 822 |
| `BackroomsPortalEntity` | 752 |
| `TeleportExecutor` | 750 |
| `BackroomsPortalBlockEntity` | 743 |
| `PortalViewTracker` | 616 |
| `RemoteWorld` | 600 |
| `ShaderPackPortalRenderer` | 587 |
| `StraddlingEntityRenderer` | 503 |

Ce ne serait pas une couche d'abstraction, seulement de la cohésion. Exemples de découpage :
- la géométrie du cadre (boîtes, distances, « en face de l'ouverture ») hors de l'entité portail ;
- la détection, l'exécution et la validation des annonces client séparées dans `TeleportExecutor` ;
- dans `BackroomsPortalBlockEntity`, la pose des faces, la sortie fugace et la sauvegarde séparées.

C'est une refonte d'un module du socle : à proposer et valider avant (GUIDE, règle d'or 2).

#### REVUE 3.6 Ranger les paquets les plus chargés (P3)

- **`core/portal/sync/`** : 17 fichiers dont 12 payloads. Les ranger dans un `sync/payload/`.
- **Les mixins** : 45 fichiers à plat. Ranger les accesseurs (`*Accessor`) dans `mixin/accessor/` et
  `mixin/client/accessor/`.
- **Les paquets d'une seule classe** (`portal/item`, `portal/debug`, `client/wand`, `client/entity`, `perf/client`,
  `level0/block`, `level0/biome`) : acceptables, à garder seulement s'ils sont appelés à grandir.

#### REVUE 3.7 Un script de build plus maintenable (P2)

- Sortir les tâches maison (`verifyJarContents`, couverture fusionnée, injection de l'agent JaCoCo, benchmark) de
  `build.gradle` vers un build inclus (`build-logic/`) avec des classes de tâches typées et testables.
- Les rendre compatibles avec la configuration cache, puis l'activer (builds incrémentaux plus rapides).
- Éviter les `afterEvaluate` quand Loom permet la configuration paresseuse.
- Catalogue de versions (`gradle/libs.versions.toml`) : une seule source pour les versions. Dependabot sait alors
  proposer les mises à jour de JUnit, Checkstyle et JaCoCo (aujourd'hui il ne suit que les actions GitHub).

#### REVUE 3.8 Générer les ressources (P3)

Les 51 fichiers JSON de ressources (états de blocs, modèles, langue, tags…) sont écrits à la main. Le générateur de
données de Fabric les produirait depuis le code : cohérence garantie entre registres et ressources, et
`ModRegistriesTest` plus simple. La langue française peut rester écrite à la main.

#### REVUE 3.9 Tests (P2)

- **Mutation testing** sur les paquets purs (génération, `PortalFacing`, transition). La couverture à 100 % dit que le
  code est exécuté, pas qu'il est vérifié. Le point 1.2.1 le montre : une assertion vide suffit à couvrir.
- **`UtilityClassesTest`** tient à la main une liste de 55 classes et utilise la réflexion pour couvrir des
  constructeurs privés qui lèvent une exception. Ces constructeurs portent une garantie (« le constructeur refuse
  l'appel ») : les vider la ferait perdre (NR 3.8). Ne remplacer que la liste à la main, par une découverte des classes
  sur le classpath. Vérifié le 25 octobre : le bytecode contient 72 classes utilitaires. Les 17 que la liste oublie sont
  des classes client, toutes conformes à la règle. La découverte ajoute donc 17 vérifications sans en ôter (REF 3).
- **Les accès des tests en jeu** : remplacer la réflexion par des accesseurs du mod de test (point 1.2.4).

#### REVUE 3.10 Qualité du code vérifiée par les outils (P2)

- **javac** : `-Werror` (point 1.3.1).
- **Checkstyle**, règles absentes aujourd'hui :
  - les noms (`TypeName`, `MethodName`… avec un motif qui accepte le `$` des méthodes de mixin) ;
  - `MissingOverride`, `EmptyCatchBlock`, `FallThrough`, `MissingSwitchDefault`, `InnerAssignment` ;
  - `JavadocStyle`, pour la règle « point final » ;
  - l'indentation.
- **Nullité vérifiée par un analyseur statique** : le code utilise déjà `org.jetbrains.annotations.Nullable` 58 fois,
  qu'aucun outil ne vérifie.

#### REVUE 3.11 Métadonnées et distribution (P2)

- `fabric.mod.json` : `contact` (sources, suivi des problèmes), `icon` (aucune icône dans `assets/gtgs_backrooms/`),
  minimum de Fabric API (point 1.3.3).
- Publier la 0.10.0 par un tag (point 1.3.4). Éventuellement publier aussi sur les plateformes de mods depuis le
  workflow de release, avec un plugin Gradle de publication.

#### REVUE 3.12 Dépôt GitHub (P3)

Protection de `master` (CI verte obligatoire, PR), vérification du format des messages de commit (point 1.4.3), et
modèles d'issue et de PR si des joueurs doivent remonter des problèmes.

#### REVUE 3.13 Documentation (P2)

- **Les « Pièges » de CLAUDE.md** (environ 240 lignes, chargées à chaque session) seraient plus lisibles dans un
  `docs/PIEGES.md` rangé par système. Mais un simple renvoi ferait perdre ce savoir aux sessions qui ne suivent pas le
  lien : CLAUDE.md est chargé d'office, pas `docs/`. Les garder dans CLAUDE.md, ou les importer depuis CLAUDE.md par
  `@docs/PIEGES.md`, qui ne réduit pas le contexte (NR 3.8).
- **Le catalogue des tests** est écrit deux fois : TESTING.md, et une cellule de tableau géante dans PORTALS.md. Une
  seule source, l'autre renvoie.
- **Un dossier de décisions** (`docs/decisions/`, une fiche datée par décision) remplacerait les décisions dispersées
  dans GUIDE §2.4, le CHANGELOG et CLAUDE.md : GPL reportée, pipeline Iris séparé essayé puis retiré, pas de module
  dédié au mod de terrain, etc.
- **Statut des anciens documents** : AUDIT.md à marquer comme historique (point 1.4.6) ; statut de
  `backrooms-mod-plan.md` et de la section 7 de `BACKROOMS_CONTENT_RULES.md` à décider par l'auteur (points 1.4.7 et
  1.4.8).

#### REVUE 3.14 Configuration pour les joueurs (P3, décision)

Toutes les options passent par des propriétés `-D` (`ModConfig`). C'est pratique en dev, mais inaccessible à un
joueur. Un fichier de configuration, lu par `ModConfig` au démarrage avec les `-D` en surcharge, rendrait réglables les
distances de rendu et d'activation des portails. GUIDE §3.5 a reporté l'interface graphique de configuration ; un
fichier lu au démarrage en est un premier pas, plus petit. À rouvrir avant une première publication publique.

#### REVUE 3.15 Environnement de développement reproductible (P1)

- Commiter `.vscode/settings.json` avec la compilation automatique coupée (point 1.5.1).
- Fixer la JVM du démon Gradle : `./gradlew updateDaemonJvm` écrit `gradle/gradle-daemon-jvm.properties`. Toute machine
  et la CI lancent alors Gradle sous la même version. **Proposition corrigée le 25 octobre** (NR 5.3) : le critère est
  21 tant que Loom reste en 1.17. Un critère 25 exigerait un JDK 25 partout, CI comprise (elle n'installe que le 21),
  alors que CLAUDE.md promet qu'un seul JDK suffit. Il ne passe à 25 qu'avec la montée de Loom 1.18+, que ce dernier
  exige. La toolchain de compilation reste à 21.
- Optionnel : laisser Gradle télécharger la toolchain manquante (plugin de résolution de toolchains), pour qu'un
  clone neuf compile sans installation manuelle de Java 21.

---

### REVUE — Annexe A : ce que la couverture ne mesure pas

Lignes de source (commentaires compris) exclues par `coverageExcludes`, sur 22 802 lignes dans `src/main/java` :

| Exclusion | Lignes |
|---|---|
| `core/portal/client/world/**` (11 classes) | 3 501 |
| `mixin/**` (45 classes) | 2 032 |
| `core/portal/client/transition/**` | 982 |
| `client/view/PortalStencilRenderer` | 961 |
| `client/view/ShaderPackPortalRenderer` | 587 |
| `core/portal/client/interaction/**` | 436 |
| `backrooms/level0/client/**` | 306 |
| `client/view/FrameAreaRenderer` | 292 |
| `core/portal/client/wand/**` | 243 |
| `client/view/ViewLightmaps` | 128 |
| `client/sound/SoundAnchors` | 119 |
| `client/view/PortalClipping` | 100 |
| `client/sound/FrameSoundPlayer` | 97 |
| `GtgsBackroomsClientMod` | 84 |
| `compat/**` | 64 |
| `client/entity/**` | 63 |
| `client/view/HandMask` | 62 |
| `core/perf/client/**` | 39 |
| **Total** | **10 096 (44 %)** |

### REVUE — Annexe B : contrôles automatisés faits pour cette revue

- **Doc contre code** : chaque identifiant en forme de classe cité entre backticks dans CLAUDE.md, README.md,
  `docs/*.md`, `docs/prompt/*.md` et `.skills/*` a été comparé aux classes du mod, de Minecraft 1.21.1 (jar mappé
  Yarn) et de Fabric API. Seuls restent des noms historiques (AUDIT, CHANGELOG : normal), des exemples fictifs des
  skills et des concepts futurs de GUIDE (`WorldProfile`…). **Aucune référence morte dans la doc vivante.**
- **Dépendances entre paquets** : `core` → `backrooms` (point 1.1.4) ; commun → client : seul
  `GtgsBackroomsClientMod` importe du client (normal).
- **Divers** : aucun `TODO`/`FIXME` ; `@Nullable` vient toujours de `org.jetbrains.annotations` (58 imports) ;
  `@SuppressWarnings` : 24 `"try"` (portées du moniteur de performance) et 3 `"unchecked"` ; deux `catch
  (RuntimeException)`, tous deux dans les coupe-circuits du rendu (voulus) ; aucun access widener.
- **Volumes** : `src/main` 177 fichiers et 22 802 lignes ; `src/test` 68 fichiers et 7 445 lignes, 298 méthodes de
  test ; `src/gametest` 15 fichiers et 7 040 lignes, 99 tests en jeu.
- **Mentions d'autres mods** : relevées par fichier (point 1.4.4), sans les recopier ici.

---

## PERF — Le diagnostic de performance des portails

> Banc du 24 octobre 2026, sur un PC portable plus modeste que le PC de développement habituel. **Aucun code ni aucune
> configuration du projet n'a été modifié** : les mesures viennent du moniteur de performance du mod
> (`-PdebugPerformanceMonitor=true`), des compteurs de Windows, de `nvidia-smi` et de Java Flight Recorder attaché au
> client par `jcmd`.
>
> Pistes relues le 25 octobre avec une exigence plus forte : aucune fonctionnalité ni aucune image ne change (NR 5).
> Celles de 3.3, 3.5 et 3.10 sont corrigées. Celles qui changent volontairement ce que voit ou peut faire le joueur
> attendent la décision de l'auteur : 3.2 (délai d'apparition), la variante approchée de 3.3, la grille réduite de 3.4,
> 3.5, 3.6 (sauf la pré-passe de profondeur, qui vise une image identique), le cooldown de 3.7 et 3.12.
>
> Les pistes demandées ensuite par l'auteur sont explorées dans la partie EXPLO, avec un protocole de test complet :
> carte graphique, autres threads et langages, LOD, portails noirs, vues cachées.

### PERF — L'essentiel

1. **Sur ce PC, le jeu tourne par défaut sur la puce graphique intégrée (Intel UHD), pas sur la RTX 3050.** Comparé à la
   RTX, il fait deux à trois fois moins d'images par seconde (254 à 332 contre 769, sans portail) et le serveur intégré
   tique deux à trois fois plus lentement. La puce intégrée partage l'enveloppe de puissance du processeur. Ce n'est pas
   un défaut du mod, mais c'est la première chose qu'un joueur sur portable rencontrera.
2. **Le serveur se fige de 0,7 à 2,9 s quand un portail pose son arrivée dans une zone jamais générée.**
   `PortalTargets.arrive` charge les chunks de façon synchrone sur le thread serveur. En multijoueur, tout le monde le
   ressent. C'est le défaut le plus grave relevé.
3. **Une vue coûte entre 0,15 et 0,35 ms de thread de rendu par image** pour un cadre 1×2 : 1,3 à 2,9 fois le banc de
   référence de `PORTALS.md`. Trois portails en vue font perdre 37 % (RTX) à 43 % (iGPU) des images par seconde. Un
   grand cadre 8×8 en fait perdre 76 % sur l'iGPU, mais seulement 30 % sur la RTX : c'est la bande passante de la puce
   intégrée qui limite.
4. **Chaque vue alloue environ 70 Ko par image** (16 à 91 Mo/s selon les FPS et le nombre de vues). Environ 70 % de ce
   qui passe par le code du mod vient du brouillard et de la couleur du ciel, recalculés par vanilla à chaque image et
   pour chaque vue.
5. **La mémoire monte par paliers et n'est rendue qu'à la déconnexion.** Elle prend +110 Mo au premier monde distant,
   puis +240 à +330 Mo au premier aller-retour. Avec quatre mondes vus, le client atteint 2,2 à 2,6 Go de mémoire
   privée, sur une machine de 8 Go qui n'a plus que 300 à 700 Mo de libre.
6. **Réseau.** Faire apparaître un portail envoie 121 chunks : 1,8 Mo vers le Level 0, 3,6 à 3,8 Mo vers le Nether
   (avant compression). C'est la fenêtre de 11 × 11 chunks d'un joueur à moins de 8 blocs du portail, comme sur ce
   banc ; de plus loin, elle fait 7 × 7 chunks, soit 49. Une traversée envoie environ 1,2 Mo par le portail, plus
   environ 400 chunks par la voie vanilla. Une vue stable ne coûte presque rien.
7. **La traversée elle-même est excellente.** La bascule prend 0,2 à 1,8 ms. La pire image qui suit fait 27 à 68 ms, et
   il n'y a aucune image lente sur la RTX.
8. **Le moniteur de performance fausse ses propres mesures.** Il lit la charge processeur par un appel natif bloquant
   sur les threads de rendu et serveur, ce qui crée une image de 50 à 60 ms à chaque résumé.

---

### PERF 1. Conditions de mesure

| Élément | Valeur |
|---|---|
| Machine | Dell G15 5511, Intel Core i5-11400H (6 cœurs, 12 threads), **8 Go de RAM**, Windows 11 (build 26200) |
| GPU | Intel UHD Graphics (intégré, OpenGL 3.2, pilote 32.0.101.7085) et NVIDIA GeForce RTX 3050 Laptop 4 Go (pilote 610.60), commutés par Optimus. « iGPU » dans la partie PERF, « puce Intel » dans les autres parties. OpenGL 3.2 est la version du contexte que Minecraft 1.21.1 demande |
| Charge de fond | Chrome, Discord, VS Code, Claude, agents Dell : mémoire engagée à 16,4 Go sur une limite de 18,8 Go ; **300 à 700 Mo de RAM libre** pendant le banc |
| JVM du client | Temurin 21.0.8, sans `-Xmx` : tas maximal par défaut de 1 980 Mo (un quart de la RAM) |
| Démon Gradle | JDK 25.0.2, `-Xmx4G` (`gradle.properties`), au repos pendant le banc |
| Client | 854×480, distance de rendu 12, simulation 12, graphismes Fancy, VSync coupée, FPS illimités, son à 10 % |
| Monde | « Portal Lab » : Superflat, graine 12345, créatif, midi figé, sans météo ni apparition de mobs |
| Mesures | moniteur du mod toutes les 5 s, mémoire et CPU du processus, compteurs GPU de Windows par processus, `nvidia-smi`, JFR (réglage `profile`), histogrammes de tas (`jcmd GC.class_histogram`) |
| Pilotage | commandes de chat et touches postées à la fenêtre du banc (PostMessage), comme le demande `docs/prompt/FINALCHECK.md` |

Deux sessions ont été mesurées : l'une sur l'iGPU (le défaut de ce PC), l'autre sur la RTX, forcée par la variable
d'environnement `SHIM_MCCOMPAT=0x800000001` au lancement (aucun réglage système modifié).

Les scènes portent les identifiants du banc, repris par la partie NR (« S0 à S7 », « R0 à R6 ») :

| iGPU | RTX | Scène |
|---|---|---|
| S0, S0b | R0 | Référence sans portail ; S0b 16 minutes plus tard, après trois mondes vus |
| S1a | R1a | Apparition d'un portail 1 × 2 vers le Level 0, à 4 blocs |
| S1b | R1b | Vue stable de ce portail |
| S1c | — | La même vue, après les traversées |
| S2 | — | Portail dans le dos, à moins de 16 blocs |
| S3 | — | Joueur à 120 blocs du portail |
| S4 | R4 | Boucle de traversées (20 tentées sur l'iGPU, 8 sur la RTX) |
| S5a | — | Apparition de deux portails, vers le Nether et vers l'Overworld |
| S5b | R5b | Vue stable des trois portails |
| S6 | R6 | Cadre 8 × 8 qui couvre l'écran |
| S7 | — | Pose d'un portail vers le Nether dans une zone neuve |

**Limites à garder en tête :**
- Le moniteur ajoute lui-même une image lente par résumé (section 3.8) : une « lente » par intervalle de 5 s est un
  artefact.
- Deux scènes de référence prises à 16 minutes d'écart sur l'iGPU donnent 254 puis 332 FPS : la machine est bruyante
  (charge de fond, température, puce partagée avec le bureau). Les écarts de FPS se lisent entre scènes voisines. Le
  temps passé dans le code des portails (ms/s du moniteur) est plus stable.
- En solo, le « réseau » est la taille encodée des payloads du mod, avant compression. Les paquets de chunks vanilla
  n'y sont pas comptés.
- Client de développement (classes non remappées, JIT froid au premier usage) : les premières fois coûtent plus cher
  qu'ensuite.

### PERF 2. Résultats

#### PERF 2.1 Images par seconde et temps de rendu des portails

« Rendu portails » est la section `client.portal.render` : tout le rendu des portails, vues comprises, en
millisecondes par seconde de jeu, puis par image.

| Scène | iGPU : FPS médians | iGPU : rendu portails | RTX : FPS médians | RTX : rendu portails |
|---|---|---|---|---|
| Référence, aucun portail (S0, S0b ; R0) | 254 (332 plus tard) | 1,2 ms/s | **769** | 1,5 ms/s |
| Une vue 1×2 vers le Level 0, à 4 blocs (S1b ; R1b) | 236,5 (−7 %) | 132 ms/s, **0,54 ms/image** | 560 (−27 %) | 142 ms/s, **0,25 ms/image** |
| La même vue, après des allers-retours (S1c) | 204 | 226 ms/s, 1,1 ms/image | — | — |
| Trois portails (Level 0, Nether, Overworld vers lui-même) (S5b ; R5b) | **145 (−43 %)** | 394 ms/s, 2,7 ms/image | 482 (−37 %) | 296 ms/s, 0,61 ms/image |
| Un cadre 8×8 qui couvre l'écran (S6 ; R6) | **80,5 (−76 %)** | 435 ms/s, 5,4 ms/image | 536 (−30 %) | 214 ms/s, 0,40 ms/image |
| Portail dans le dos, à moins de 16 blocs (S2) | 285 | 8 ms/s | — | — |
| Joueur à 120 blocs du portail (S3) | 296 | 4,6 ms/s | — | — |
| Boucle de traversées (S4 ; R4) | 188 | 134 ms/s | 556 | 63 ms/s |

Repères du banc de `PORTALS.md` (RTX 5070 Ti, i5-13600KF) : 185 µs par image pour une vue stable, 1 000 µs pour trois
vues. Sur ce portable, la même vue coûte 1,3 fois plus sur la RTX et 2,9 fois plus sur l'iGPU.

**Nombre de vues par image** (appels de `client.portal.view` divisés par les images) : 1 avec un portail. Avec les trois
portails : 4,1 sur l'iGPU et 2,9 sur la RTX. La vue de l'Overworld sur lui-même en imbrique d'autres, et son arrivée,
tirée au sort, n'était pas la même dans les deux sessions.

Coût d'**une vue** (section `client.portal.view`, par appel) :
- cadre 1×2 : 0,35 ms sur l'iGPU, 0,145 ms sur la RTX ;
- cadre 8×8 : 4,9 ms sur l'iGPU, 0,28 ms sur la RTX.

#### PERF 2.2 Mémoire

Mémoire privée du processus client (tas Java, tampons natifs et pilote compris) :

| État | iGPU | RTX |
|---|---|---|
| Écran titre, au démarrage | 1 363 Mo | — |
| Dans le monde, aucun portail | 1 567 Mo | 1 897 Mo (un monde distant du Nether déjà créé à la connexion) |
| Une vue sur le Level 0 | 1 676 Mo (**+109 Mo**) | 1 951 Mo |
| La même scène, après des allers-retours | 2 010 Mo (**+334 Mo**) | 2 393 Mo (+240 Mo sur les trois premières traversées, puis stable) |
| Trois portails (trois mondes distants et une vue secondaire) | 2 214 Mo | — |
| Tous les portails retirés | 2 212 Mo (rien n'est rendu) | — |
| Fin de session, quatre mondes vus | **2 580 Mo** (tas : 652 Mo vivants après GC) | — |
| Écran titre, après déconnexion | 2 078 Mo (tas : 342 Mo vivants après GC) | — |

Mémoire graphique du processus :

| État | iGPU (mémoire partagée) | RTX (mémoire dédiée) |
|---|---|---|
| Aucun portail | 190 Mo | 96 Mo |
| Une vue | 193 Mo | 100 Mo |
| Trois portails | 383 Mo | 102 Mo |
| Cadre 8×8 | 383 Mo | 116 Mo |
| Après des allers-retours | 340 à 430 Mo | 286 Mo |

Histogramme du tas avec trois portails (iGPU) :
- 4 `WorldRenderer` (le joueur et trois vues), 4 `RemoteWorld`, 3 `ClientWorld` ;
- **42 500 `BuiltChunk`** : 15 000 pour la grille du joueur, **15 000 pour la seule vue secondaire de l'Overworld**,
  10 000 pour le Nether et 2 500 pour le Level 0 ;
- **213 145 `VertexBuffer`** (10 Mo d'objets Java ; leurs objets GL sont différés pour les vues, `MixinVertexBuffer`).

GC : sur une vue stable, 11 collections en 40 s, 44 ms de pause au total (médiane 2,9 ms). Le GC n'est pas un goulot
aujourd'hui, mais l'allocation des vues le sollicite d'autant plus que les FPS sont hauts.

#### PERF 2.3 Réseau

Taille encodée des payloads du mod, avant compression :

| Événement | Envoyé | Volume |
|---|---|---|
| Apparition d'un portail vers le Level 0 | 121 `portal_remote_chunk` | 1 777 Kio (14,7 Kio par chunk) |
| Apparition d'un portail vers le Nether | 121 `portal_remote_chunk` | 3 622 à 3 842 Kio (30 à 32 Kio par chunk) |
| Vue stable sur le Level 0 | quelques `portal_remote_entity` | ≈ 0 |
| Vue stable sur le Nether | `portal_remote_block_update` (lave, feu), environ 25 par seconde | 0,25 Kio/s |
| Une traversée | 110 à 121 chunks par le portail, et environ 400 chunks vanilla | 1,2 à 1,3 Mo par le portail ; la part vanilla n'est pas mesurée par le moniteur (de l'ordre de 4 Mo d'après la taille moyenne des chunks) |
| Joueur hors de portée | 121 `portal_remote_chunk_unload` | 1 Kio |

Le journal de traversée compte environ 500 chunks reçus par passage (1 000 au premier). Ceux que le portail avait déjà
envoyés (« chunks tenus ») ne sont pas renvoyés ; tous les autres le sont, comme pour un changement de dimension vanilla.

#### PERF 2.4 Serveur intégré

| Scène | Tick médian | Pire tick |
|---|---|---|
| Référence (iGPU) | 6,75 ms | 20 ms (dont l'artefact du moniteur) |
| Référence (RTX) | **2,3 ms** | 7,9 ms |
| Pose du premier portail vers le Level 0 (iGPU, S1a) | 6,3 ms | **1 173 ms** |
| Pose de deux portails, vers le Nether et vers l'Overworld (iGPU, S5a) | 12,6 ms | **2 908 ms** (« Can't keep up! Running 2860ms behind ») |
| Pose d'un portail vers le Nether dans une zone neuve (iGPU, JIT chaud, S7) | 47 ms | **661 ms** |
| Vue stable, une à trois fenêtres chargées | 6,5 à 8,4 ms | 18 à 36 ms |
| Traversée (`portal.teleport.player`) | — | 24 ms (iGPU), 61 ms (RTX) |

Hors de ces poses, les sections serveur du mod restent sous 1,3 ms/s (`portal.sync.tick`, `portal.light`,
`portal.destination.tick`).

#### PERF 2.5 Traversées

| | iGPU (12 traversées) | RTX (8 traversées) |
|---|---|---|
| Bascule de monde | 0,18 à 1,51 ms | 0,23 à 1,84 ms |
| Pire image sur les 120 suivantes | 29 à 68 ms | 27 à 41 ms |
| Images de plus de 50 ms | 0 à 1 sur 119 (l'artefact du moniteur) | 0 sur 119 |
| Chunks reçus | 512 à 1 016 | 504 à 1 000 |

Sur l'iGPU, 12 des 20 traversées tentées ont abouti. Après la 12e, le script a raté un passage, puis a marché dans le
mauvais sens. Je l'attribue au script (dérive de position avec une marche minutée), pas au mod : sur la RTX, 8 sur 8
ont abouti. Ce n'est pas prouvé.

---

### PERF 3. Diagnostic

#### PERF 3.1 Le jeu tourne sur la puce intégrée (environnement, P1 pour les joueurs)

- **Constat.**
  - Les compteurs de Windows montrent le client sur l'Intel UHD : 175 à 430 Mo de mémoire partagée, rien en dédié.
    `nvidia-smi` ne voit aucun processus, et le F3 affiche « Intel(R) UHD Graphics ».
  - Forcé sur la RTX, le client passe de 254–332 à 769 FPS sans portail, et le serveur intégré de 6,75 à 2,3 ms par
    tick.
  - Sur la RTX, le GPU reste à 11–15 % : l'image est limitée par le processeur.
- **Cause.** Optimus attribue la puce intégrée à tout `java.exe` qu'il ne connaît pas. Le Temurin du client de dev n'a
  pas de profil NVIDIA. La puce intégrée partage la puissance et la chaleur du processeur : quand elle travaille, les
  cœurs ralentissent, serveur intégré compris.
- **Pistes.**
  - Pour le développement : choisir « Hautes performances » pour le `java.exe` de Temurin dans les paramètres
    graphiques de Windows, ou lancer avec `SHIM_MCCOMPAT=0x800000001`. Le documenter dans `docs/SETUP.md` et
    `docs/TROUBLESHOOTING.md`.
  - Pour les joueurs : une ligne dans la doc ; le launcher Minecraft a son propre profil, à vérifier.

#### PERF 3.2 Gel du serveur à la pose d'un portail vers une zone neuve (P1)

- **Constat.** Pires ticks de 1 173 ms (premier portail vers le Level 0), 2 908 ms (Nether et Overworld) et 661 ms
  (Nether, zone neuve, JIT déjà chaud).
- **Cause.** Prouvée par le code et cohérente avec JFR :
  - [PortalTargets.java:160-179](../../src/main/java/com/gtgpuke/gtgsbackrooms/core/portal/block/PortalTargets.java#L160-L179)
    parcourt des anneaux de colonnes jusqu'à 16 blocs. Pour chacune, il appelle
    `world.getChunk(...)` (ligne 170), qui génère le chunk complet de façon synchrone sur le thread serveur. Dans une
    dimension à plafond comme le Nether, cela fait plusieurs chunks et leurs voisins.
  - Vers le Level 0, `LevelZeroArrival` calcule des layouts jusqu'à 24 cellules. C'est du calcul pur, mais sur le
    thread serveur : `level0.layout.generate` monte jusqu'à 459 ms pendant l'apparition, JIT froid compris.
  - `PORTALS.md` cite déjà cette limite (« un à-coup possible à la pose »). Sur ce PC, l'à-coup dure une à trois
    secondes, ce qui dépasse largement l'à-coup.
- **Piste.** Résoudre l'arrivée de façon asynchrone :
  - poser un ticket sur les chunks de recherche, puis reprendre la résolution quand ils sont prêts ;
  - le bloc sait déjà réessayer tous les `TARGET_RETRY_INTERVAL_TICKS` (100) tant qu'il n'a pas de destination : il
    suffirait de ne déclarer la recherche finie qu'avec des chunks déjà chargés ;
  - pour le Level 0, passer le calcul des layouts sur un thread de travail (le générateur est déterministe et pur).
    Vérifié le 25 octobre : `LevelZeroArrival.find` ne lit du monde que sa graine, crée son propre cache de layout, et
    le cache de secteurs partagé est synchronisé (le générateur s'en sert déjà depuis plusieurs threads).
  - Un test en jeu peut verrouiller la règle par une assertion de structure, sans chronomètre (NR 4.2, G4) : au tick de
    la pose vers une zone neuve, le chunk d'arrivée n'est pas encore chargé. Le serveur de test tique sans attendre, et
    une durée de tick y serait fragile. Au banc, le critère reste qu'aucun tick ne dépasse 50 ms à la pose.
- **Changement visible** (NR 5.4) : aujourd'hui, le portail existe dès le tick de la pose, après le gel. Ensuite, il
  n'existera qu'une fois les chunks d'arrivée prêts. Entre les deux, un joueur qui passe le cadre le traverse sans
  changer de monde. À décider par l'auteur.

#### PERF 3.3 Brouillard et ciel recalculés à chaque image, pour chaque vue (P2)

- **Constat.** JFR sur une vue stable : 80 % de toute l'allocation du processus (client et serveur intégré) sont des
  `Vec3d`. Parmi les allocations
  dont la pile passe par le mod, environ 70 % viennent de deux endroits :
  - `RemoteViewRenderer.prepareFog` →
    [`BackgroundRenderer.render`](../../src/main/java/com/gtgpuke/gtgsbackrooms/core/portal/client/world/RemoteViewRenderer.java#L165-L172)
    → `CubicSampler.sampleColor` ;
  - `RemoteViewRenderer.renderView` → `renderSky` → `ClientWorld.getSkyColor` → `CubicSampler.sampleColor`.
- **Cause.** Vanilla échantillonne 216 points de biome (cube de 6×6×6) avec plusieurs `Vec3d` par point, une fois par
  image pour le monde du joueur. Chaque vue refait le calcul à chaque image. D'où environ 70 Ko par vue et par image, et
  autant de temps CPU.
- **Piste**, corrigée le 25 octobre (NR 5.3) :
  - Ne pas figer la couleur au tick : la règle de CLAUDE.md veut que le rendu décide sur la position affichée, et la
    règle d'or 5 interdit toute couture.
  - Les deux solutions proposées d'abord changent encore l'image. Une couleur partagée entre les vues d'un monde serait
    prise à la caméra d'une seule d'entre elles. Une couleur prise au tick puis interpolée s'écarterait du calcul
    direct. Le calcul vanilla dépend en effet de la position de la caméra à chaque image, de l'heure à chaque tick et
    des chunks reçus.
  - Variante sans écart : réutiliser le résultat seulement pour des entrées identiques (même monde, même position
    exacte de la caméra, même heure, aucun chunk reçu entre-temps). Elle sert à une caméra immobile, le cas mesuré
    ici.
  - Toute variante approchée (au plus 1/255 par canal) change l'image : à décider par l'auteur (NR 5.4).
  - Gain attendu de la variante sans écart : l'allocation d'une vue stable ; rien quand la caméra bouge.

#### PERF 3.4 Le coût d'une vue croît avec la grille et l'imbrication (P2)

- **Constat.**
  - Trois portails donnent 2,9 à 4,1 vues par image, parce que la vue de l'Overworld sur lui-même en imbrique d'autres.
  - Une vue secondaire tient une grille de sections à la distance de rendu du joueur : 15 000 `BuiltChunk` pour
    l'Overworld (25×25 chunks sur 24 sections), 75 000 `VertexBuffer` Java.
  - Écart avec CLAUDE.md, qui écrit « chaque BuiltChunk crée quatre VertexBuffer » : l'histogramme en compte cinq
    (213 145 pour 42 500 sections), un par couche de blocs en 1.21.1 (solid, cutout_mipped, cutout, translucent,
    tripwire).
  - Sur l'iGPU, la scène à trois portails perd 43 % des FPS.
- **Pistes.**
  - Un budget de vues par image, ou un niveau d'imbrication réglable, coupé d'office sur une machine lente : par exemple
    pas d'imbrication sous un seuil de FPS, avec hystérésis. **Reformulé** (NR 3.8, point 2) : une option désactivée par
    défaut, ou dont la valeur par défaut reproduit le comportement actuel ; la couper d'office change l'image, ce qui
    est un changement voulu (NR 5.4).
  - Une grille de vue réduite à ce que la fenêtre synchronisée peut montrer. La fenêtre fait 11×11 chunks à moins de
    8 blocs du portail (7×7 de plus loin), alors que la grille de vue en fait 25×25 à distance 12.
  - Exposer `renderDistanceBlocks` et une limite de vues aux joueurs : aujourd'hui, ce ne sont que des `-D`.

#### PERF 3.5 La mémoire monte par paliers et ne redescend qu'à la déconnexion (P2)

- **Constat.**
  - +109 Mo au premier monde distant, puis +240 à +334 Mo au premier aller-retour, à scène identique. La mémoire graphique
    double sur la RTX (100 → 286 Mo), et la même vue coûte ensuite deux fois plus de temps de rendu (132 → 226 ms/s sur
    l'iGPU).
  - Retirer tous les portails ne rend rien.
  - Après quatre mondes vus, le client occupe 2,6 Go privés, sur un PC de 8 Go qui n'a plus que quelques centaines de Mo
    de libre.
- **Cause probable** (observée, mécanisme non tracé jusqu'au bout) : à la traversée, le monde quitté devient le monde
  distant avec son renderer. Il garde sa grille et ses sections construites à pleine distance, et vraisemblablement ses
  chunks. C'est voulu pour un retour sans couture (`PORTALS.md`, « Mémoire : gardés jusqu'à la déconnexion »), mais rien
  ne le ramène ensuite à la taille d'une vue.
- **Pistes.**
  - Après un délai sans retour, ramener le monde quitté à la fenêtre du portail : sections hors de la fenêtre rendues au
    GPU, chunks déchargés.
  - Libérer un monde distant dont aucun portail visible n'est resté pendant N minutes.
  - Le documenter comme réglage mémoire.
- **Changement visible** (NR 5.3 et 5.4) : aujourd'hui, un retour est sans couture quel que soit le délai. Avec ces
  pistes, un retour tardif verrait la vue se reconstruire. C'est un arbitrage entre mémoire et continuité, à décider par
  l'auteur.

#### PERF 3.6 Grand cadre sur puce intégrée (P3)

- **Constat.** Un cadre 8×8 qui couvre l'écran : 80 FPS sur l'iGPU (−76 %), 536 sur la RTX (−30 %).
- **Cause probable.** La zone du cadre est dessinée deux fois : le monde du joueur dessous, puis la vue par-dessus sous
  stencil.
  Sur une puce intégrée limitée en bande passante, le coût suit la surface du cadre à l'écran.
- **Piste.** Couper le dessin du monde du joueur derrière la zone du cadre quand celui-ci couvre une grande partie de
  l'écran, ou en faire une option joueur (section 4). Ou simplement le documenter. Une variante vise une image
  identique : écrire d'abord la profondeur du plan du cadre, pour que ce qui est derrière ne soit plus dessiné
  (EXPLO 3.3, GPU2).

#### PERF 3.7 Réseau (P2)

- **Constat.**
  - À l'apparition : 1,8 à 3,8 Mo par portail, avant compression.
  - À la traversée : environ 1,2 Mo par le portail et environ 400 chunks vanilla, soit plusieurs mégaoctets par passage
    et par joueur.
  - Un joueur qui fait des allers-retours devant un portail en génère plusieurs par seconde. Le serveur paie
    `portal.sync.chunks` (jusqu'à 22 ms par tick) et l'encodage des chunks vanilla.
- **Pistes.**
  - Garder côté client les chunks du monde quitté au-delà de la seule fenêtre du portail, et les déclarer « tenus » au
    serveur. Ce serait le même mécanisme que les chunks tenus actuels, avec une durée bornée et en échange de mémoire
    (voir 3.5 : à arbitrer).
  - Limiter la fréquence des traversées d'un même joueur par le cooldown (aujourd'hui 0 tick par défaut). Un défaut non
    nul empêcherait d'enchaîner aller et retour : changement voulu, à décider par l'auteur (NR 5.4).

#### PERF 3.8 Le moniteur de performance fausse ses mesures (P1 pour l'outillage)

- **Constat.**
  - À chaque résumé (toutes les N secondes), une image de 50 à 60 ms et un pic de tick serveur.
  - JFR attribue 9 échantillons natifs au thread de rendu et 17 au thread serveur dans
    `OperatingSystemImpl.getProcessCpuLoad0()`, appelé par `ModPerformanceMonitor$Snapshot.capture` (ligne 458), depuis
    `onClientTick` et `logServerSummary`.
- **Conséquence.** Les colonnes `pic=` et `lentes=` du moniteur, et le pire tick serveur, comptent cet artefact.
  L'écrasante majorité des « images lentes » de ce banc en vient. Les chiffres « pire image / images de plus de 50 ms »
  de `PORTALS.md` en contiennent probablement aussi.
- **Piste.** Lire la charge processeur sur un thread à part (un échantillonneur démon), ou ne plus la lire. Ce n'est
  jamais actif chez un joueur (option de debug), mais l'outil de mesure doit être neutre.

#### PERF 3.9 Petites allocations dans les chemins chauds (P3)

La règle « aucune allocation dans tick/render » (CLAUDE.md) n'est pas tenue partout. Les montants sont faibles :

| Section | Allocation | Remarque |
|---|---|---|
| `client.portal.sight` | environ 33 octets par appel, même sans portail | rapport de regard du client |
| `client.portal.straddling` | environ 0,9 Ko par appel quand un portail est proche, même hors de vue ; environ 5 octets loin de tout portail | moitiés d'entités |
| `RemoteWorld.displayTicks` | une lambda par bloc tiré | `ClientWorld.randomBlockDisplayTick` de vanilla, rejoué pour le monde distant |
| Dessins du cadre et des couches de vue | chaînes `"Sampler" + i` | `ShaderProgram.initializeUniforms` de vanilla, à chaque dessin : chaque dessin ajouté par les portails en ajoute |
| `portal.destination.tick` (serveur) | environ 60 octets par appel | |
| `PortalViewTransform.aim` | un `Vec2f` par vue et par image | relevé le 25 octobre dans le bytecode : `remoteLook`, puis `PortalFacing.transformLook`, rend un regard neuf |

#### PERF 3.10 Création d'un monde distant dans l'image (P3)

Créer le monde distant et son renderer prend 112 ms sur l'iGPU et 50 ms sur la RTX, en une seule image (18 à 25 ms sur
le PC de référence). Piste, corrigée le 25 octobre (NR 5.3) : préparer le monde distant dès que le portail entre dans le
rayon d'activation, plutôt qu'à la première vue. L'étaler sur plusieurs images laisserait la vue vide ou incomplète à
ses premières images.

#### PERF 3.11 Mémoire retenue après la déconnexion : pas le mod

- **Constat.** À l'écran titre, une minute après la déconnexion, le tas garde vivants un `ClientWorld`, son
  `ClientPlayNetworkHandler`, son `ClientPlayerEntity` et 1 238 chunks client. Après la session suivante, ce n'est plus
  que celui de la nouvelle session (609 chunks) : la rétention est bornée à un monde.
- **Preuve.** Le profileur de fuites de JFR (`OldObjectSample`, chemin vers les racines du GC) remonte à chaque fois
  `MinecraftClient.worldRenderer` → `WorldRenderContextImpl` → `world`. C'est le contexte de rendu de Fabric API, qui
  garde le dernier monde jusqu'à la prochaine image dans un monde. Aucune chaîne ne passe par du code du mod.

#### PERF 3.12 Hors performance, vu pendant le banc

Un cadre 8×8 vers le Level 0 montre, dans le haut de la vue, la couche du dessus : murs coupés et dessous des sols. Un
étage du Level 0 n'a que 4 blocs d'air, et un cadre plus haut voit à travers le plafond. Le problème est visuel ;
`LevelZeroArrival` pourrait refuser ou réduire un cadre qui ne tient pas dans un étage. Cela changerait les nouvelles
arrivées de ces cadres dans le Level 0 : à décider par l'auteur (NR 5.4).

---

### PERF 4. Pistes classées

| Priorité | Piste | Effet attendu | Section |
|---|---|---|---|
| P1 | Résolution asynchrone de l'arrivée (tickets et reprise, layouts du Level 0 hors du thread serveur), après décision de l'auteur sur le délai d'apparition | Plus de gel d'une à trois secondes à la pose | 3.2 |
| P1 | Charge processeur du moniteur lue hors des threads de jeu | Mesures fiables (plus d'images lentes fantômes) | 3.8 |
| P1 | Documenter le choix du GPU sur les portables (Optimus) | Jusqu'à trois fois les FPS | 3.1 |
| P2 | Brouillard et ciel des vues réutilisés à entrées identiques ; une variante approchée seulement sur décision de l'auteur | L'allocation d'une vue stable | 3.3 |
| P2 | Budget de vues et d'imbrication en option, par défaut identique à aujourd'hui ; grille de vue réduite en recherche | −40 % de FPS évitables sur machine lente ; moins de mémoire | 3.4 |
| P2 | Monde quitté allégé seulement après un délai sans retour, la fenêtre du portail toujours gardée ; changement visible, sur décision de l'auteur | Centaines de Mo rendus | 3.5 |
| P2 | Chunks du monde quitté gardés et déclarés tenus (à arbitrer avec la mémoire) | Moins de réseau à la traversée | 3.7 |
| P3 | Allocations résiduelles (regard, moitiés d'entités, nombre de dessins, regard transposé des vues) | Règle « zéro allocation » tenue | 3.9 |
| P3 | Création du monde distant anticipée (pas étalée) | Plus d'image de 50 à 110 ms à la première vue | 3.10 |
| P3 | Options joueur : distance des vues, nombre de vues, imbrication | Réglable sur une petite machine | 3.4, 3.6 |

Chaque piste est à valider par l'auteur avant d'être faite (GUIDE, règle d'or 10). L'ordre sûr, les garde-fous à poser
avant chaque piste et les conditions de non-régression sont dans la partie NR ; l'ordre par correction, en BILAN 6.
Après chaque changement, rejouer le même banc (section 5) pour comparer.

---

### PERF 5. Rejouer ce banc

1. Monde : « Portal Lab » est gardé dans `run/saves/`. Il se lance par
   `ORG_GRADLE_PROJECT_quickPlayWorld="Portal Lab" ./gradlew runClient -PdebugPerformanceMonitor=true -PperformanceLogIntervalSeconds=5`.
   L'écran « Experimental Settings » se confirme par TAB, TAB, Entrée.
2. Options : VSync coupée, FPS illimités, `pauseOnLostFocus:false` (sinon le jeu se met en pause dès que la fenêtre
   perd le focus), distance 12. `run/options.txt` a été retiré après le banc, comme il était avant.
3. GPU : ajouter `SHIM_MCCOMPAT=0x800000001` à l'environnement pour forcer la RTX.
4. Scène : `/tp @s 0.5 -60 0.5 180 0`, puis `/setblock 0 -60 -4 gtgs_backrooms:backrooms_portal[facing=south]`. Ajouter
   `{Width:8,Height:8}`, `{Destination:"minecraft:the_nether"}` ou `{Destination:"minecraft:overworld",ExitScatter:40}`
   selon la scène.
5. Mesures complémentaires sans toucher au code :
   - JFR : `jcmd <pid> JFR.start settings=profile duration=40s filename=...`, puis
     `jfr view allocation-by-site`, `jfr view native-methods` ;
   - histogramme : `jcmd <pid> GC.class_histogram` ;
   - fuites : `JFR.dump path-to-gc-roots=true`.
6. Le résumé par scène se lit dans le journal `[perf]` (client toutes les 5 s, serveur avec `reseau=`, une ligne
   `traversee:` par passage).

Les scripts de pilotage (touches postées à la fenêtre du banc, captures par `PrintWindow`, échantillons mémoire et
GPU) et l'outil qui résume le journal par scène ont servi à ce banc. Ils ne sont pas dans le dépôt et peuvent y être
ajoutés si l'auteur le souhaite.

---

## NR — La non-régression des correctifs proposés

> Analyse du 24 octobre 2026, complétée le 25 octobre (section 5). Elle passe chaque proposition des parties REVUE et
> PERF au crible de la qualité qui existe déjà. La section 5 la relit avec une exigence plus forte : aucune
> fonctionnalité ni aucune image ne change. La partie donne ensuite le plan qui empêche toute régression. Les données
> de référence relevées sur la 0.10.0 sont dans la partie REF. **Rien n'est implémenté.**
>
> Conclusion courte :
> - **La plupart des propositions sont non régressives**, à condition d'être faites dans un ordre précis et derrière les
>   bons garde-fous.
> - **Six sont à reformuler**, parce que, telles qu'écrites, elles entrent en conflit avec une règle ou une qualité du
>   projet (section 3.8).
> - **Lue strictement, sans changement de fonctionnalité ni d'image** (section 5) :
>   - cinq propositions de REVUE et PERF, telles qu'écrites, auraient changé une image, les versions acceptées pour
>     les joueurs ou les machines où le build se lance ; elles sont corrigées (5.3) ;
>   - dix propositions ou variantes changent volontairement ce que voit ou peut faire le joueur ; elles sortent des
>     lots « sans régression » et attendent la décision de l'auteur (5.4).
> - **Le portage de Minecraft ne peut pas être sans régression par construction** : on vise la parité, prouvée par des
>   oracles.
> - **Le projet a aujourd'hui neuf angles morts** (section 2), que le plan comble avant tout changement risqué.

### NR 1. La référence : ce qui ne doit pas régresser

Mesurée sur ce PC le 24 octobre 2026, avant tout changement :

| Garde-fou | État de référence |
|---|---|
| `./gradlew clean build --warning-mode all` | **vert**, en 5 min 30 |
| Checkstyle (main, test, gametest) | 0 violation (`maxWarnings = 0`) |
| javac `-Xlint:all` | **0 avertissement** : main recompilé avec `--rerun`, test et gametest aussi |
| Dépréciations Gradle | aucune signalée |
| Tests unitaires | **749 exécutions** (298 méthodes, paramétrées comprises), 0 échec |
| Tests en jeu | **99 sur 99** |
| Couverture fusionnée | 100 % des instructions et des branches du périmètre mesuré, qui représente 56 % des lignes (44 % sont exclues, voir section 2) |
| `verifyJarContents` | vert |
| `MixinTargetsTest` | chaque cible se charge, aucun `@Redirect` |
| `LevelZeroGoldenTest` | 15 empreintes inchangées |
| Benchmark de génération (ce PC) | layout froid 0,319 ms/chunk, chaud 0,021, layout + écriture 0,198 (0,129 sur le PC de référence) |
| Banc client des portails | chiffres de la partie PERF, scènes S0 à S7 (iGPU, la puce Intel) et R0 à R6 (RTX), définies en PERF 1 |
| Contrats de comportement | `docs/PORTALS.md` (en jeu, protocole, limites), pièges de `CLAUDE.md`, règles d'or de `docs/GUIDE.md` |

La qualité se juge sur neuf dimensions, chacune avec son oracle :

| Dimension | Oracle |
|---|---|
| (a) Garde-fous du build | Checkstyle, javac, couverture, contenu du JAR, cibles de mixins |
| (b) Comportement serveur | tests unitaires et tests en jeu |
| (c) Comportement et image côté client | aujourd'hui : rien d'automatique |
| (d) Compatibilité des sauvegardes | NBT, composants, identifiants |
| (e) Compatibilité réseau | format des payloads |
| (f) Performance | banc et moniteur |
| (g) Règles du dépôt | ASCII, traductions, aucun autre mod nommé, documentation |
| (h) Compatibilité en production | chargeurs plus anciens, Iris, mods de terrain |
| (i) Robustesse sur un monde déjà joué | triple passage des tests en jeu en CI |

### NR 2. Les angles morts des garde-fous actuels

Ce qu'un changement peut casser aujourd'hui **sans que le build ne rougisse** :

1. **Le client** : 10 096 lignes sur 22 802 sont hors couverture, dont tout `client/world/` et `client/transition/`, les
   renderers et les mixins. Une régression d'image, de bascule ou d'entité à cheval sur un portail passe le build.
2. **Le lancement de dev** : les propriétés `-P…` transmises à `runClient` et le chargement rapide `quickPlayWorld` ne
   sont exercés par aucun test.
3. **Le JAR de production** : le remapping, la refmap, le chargeur minimal déclaré (`>=0.16.9`) et les versions de Mixin
   et de MixinExtras ne sont essayés qu'à la main. L'épisode `@Redirect` de CLAUDE.md en est la preuve.
4. **Les sauvegardes** : le format est testé par morceaux (`PortalSettingsTest` « le format est stable », relectures
   NBT), mais aucune sauvegarde réelle d'une version publiée ne sert de référence. Celles de la 0.10.0 sont relevées
   depuis (REF 6), pas encore dans les tests.
5. **Le réseau entre versions** : `PortalPayloadsTest` vérifie l'aller-retour, pas les octets. Aucun numéro de protocole
   n'existe : un client et un serveur de versions différentes du mod se connectent, puis échouent au décodage sans
   message clair. Les octets de la 0.10.0 sont relevés depuis (REF 5), pas encore dans les tests.
6. **La performance** : aucun budget automatique, et le moniteur fausse lui-même ses « images lentes » (PERF 3.8).
7. **Les autres mods** (Iris, mods de terrain) : matrice manuelle seulement.
8. **Les garde-fous eux-mêmes** : si un remaniement du build débranche une vérification (couverture retirée de `check`,
   une classe qui glisse dans un chemin exclu), le build reste vert, et même plus vert qu'avant. Les listes et planchers
   qui permettront de le vérifier sont relevés (REF 1 et 7).
9. **La robustesse sur monde déjà joué** : elle ne tient qu'à un effet de bord de la CI, qui relance les tests en jeu
   trois fois sans `clean`.

---

### NR 3. Chaque proposition passée au crible

Les verdicts :
- **Sans risque** : aucun comportement touché, les garde-fous actuels suffisent.
- **Sûr si…** : non régressif à la condition indiquée.
- **À reformuler** : la proposition telle qu'écrite entre en conflit avec une qualité existante ; la version non
  régressive est donnée.
- **Parité** : la régression est possible par construction ; seule la parité se prouve.

Pour chaque proposition, la liste donne : son origine (la partie et le point entre parenthèses), ce qui peut régresser,
puis la condition ou le garde-fou nécessaire (G1 à G12, définis en section 4).

#### NR 3.1 Commentaires, documentation, tests

**Sans risque :**
- **Javadoc du biome rouge, Javadoc orpheline de `MixinWorldRenderer`, « 9 x 9 » de `GameTestServerWorlds`**
  (REVUE 1.1.1, 1.1.2, 1.2.3). Rien ne peut régresser : ce sont des commentaires, et les sources restent en ASCII
  (Checkstyle). Les empreintes ne bougent pas : on corrige le commentaire, pas la densité.
- **Accent de « Néon » dans `fr_fr.json`** (REVUE 1.4.11). Seul le texte affiché change. `ModRegistriesTest` vérifie la
  présence des clés, pas leur texte. Sans risque pour la qualité, mais c'est un texte visible qui change : décision de
  l'auteur (5.4).
- **Documentation : CONTRIBUTING, skills, en-tête d'AUDIT, phrase de CLAUDE.md sur le socle, nombre de `VertexBuffer`**
  (REVUE 1.4.x). Rien côté code. Ne pas toucher aux specs de l'auteur (`BACKROOMS_CONTENT_RULES.md`,
  `docs/backrooms/**`, `INDEX.md`) : on signale, on ne corrige pas.

**Sûr si… :**
- **Assertion `mixedColumns >= 0` remplacée par une vraie attente** (REVUE 1.2.1). Le test peut rougir si l'attente est
  fausse. Il faut d'abord relever la valeur réelle avec le code actuel, puis l'écrire telle quelle. La qualité monte
  (une assertion vide devient réelle).
- **Branche morte `hasWinningNeighbor` retirée** (REVUE 1.2.2). Si la branche n'est pas morte, le test rougit et
  révèle une contradiction avec le commentaire de `LevelZeroCorridorGaps`. Il faut la retirer seule, sans toucher au
  reste de l'assertion : un test vert prouve que la branche était morte.
- **Test unitaire du répit d'`ArrivalGuard`** (REVUE 1.2.5). Ne peut que renforcer les tests.
- **Allocation de `ArrivalGuard.tick` retirée** (REVUE 1.1.5). Le comportement de la garde en dépend. Ce cas est déjà
  protégé : `ArrivalGuard` est couvert à 100 %, et les gardes sont vérifiées par `PortalTunnelGameTests`. Les tests
  existants doivent rester verts sans être modifiés.
- **Réflexion des tests en jeu remplacée par des accesseurs** (REVUE 1.2.4). Un accesseur faux casse le test en jeu
  (échec bruyant), mais `MixinTargetsTest` ne lit que la configuration du mod principal. Si le coût d'un mixin de test
  dépasse le gain, garder la réflexion.

#### NR 3.2 Commande de debug en texte traduit (REVUE 1.1.3)

Verdict : **sûr si…** Trois points sont vérifiés dans le code :

1. **Le test casse en l'état.** `theDebugCommandListsThenClearsLoadedPortals` (PortalSyncGameTests) compare le texte
   français littéral (`"portail(s) charge(s)"`, `"portail(s) supprime(s)"`). Fabric API charge les traductions `en_us`
   des mods côté serveur (`ServerLanguageUtil` dans `fabric-resource-loader-v0`). Une fois traduit, le message vu par le
   test serait anglais, et le test rougirait.
2. **L'adaptation ne doit pas affaiblir le test.** Il faut vérifier la clé de traduction et ses arguments (portail libre,
   coordonnées du bloc, totaux), pas seulement « un message existe ».
3. **Il faut un test des clés.** Les nouvelles clés doivent exister en `en_us` et `fr_fr`, sinon un oubli en français
   passerait en silence : `ModRegistriesTest` ne connaît que les blocs et les items.

Changement de comportement à accepter : la console d'un serveur dédié affichera ces messages en anglais. C'est un
changement visible, à décider par l'auteur (5.4).

#### NR 3.3 Build et CI

| Proposition | Ce qui peut régresser | Condition, garde-fou | Verdict |
|---|---|---|---|
| `-Werror` (REVUE 1.3.1) | Le build, si un avertissement existe | **Vérifié : 0 avertissement aujourd'hui.** Une montée de dépendance qui introduit des dépréciations fera rougir le build : c'est voulu | **Sans risque** |
| API de lancement de Loom par propriétés (REVUE 1.3.2) | `-Dfabric-api.gametest.report-file` perdu : plus de `build/junit.xml`, et la CI ne le verrait pas (`if-no-files-found: ignore`). Cooldown de 3 ticks des tests en jeu perdu : des tests en dépendent et rougiraient. Propriétés de `runClient` et `quickPlayWorld` : **aucun test** | Après migration : `build/junit.xml` présent avec 99 tests ; `runClient -PdebugPerformanceMonitor=true` écrit des lignes `[perf]` ; `quickPlayWorld` charge le monde (G8) | **Sûr si…** |
| Fabric API minimale dans `fabric.mod.json` (REVUE 1.3.3) | Un minimum trop haut empêche le jeu de démarrer (le test en jeu rougit) ; trop bas, rien ne le détecte. **Proposition corrigée le 25 octobre** (5.3) : un minimum pris à la version de compilation écarterait des joueurs pour qui le mod marche peut-être ; et « utiliser les variables » de `processResources` relèverait le chargeur minimal de 0.16.9 à 0.19.5 | La plus ancienne Fabric API essayée avec succès (G7), sinon garder `*` ; garder `fabricloader >=0.16.9` | **Sûr si…** |
| Tag `v0.10.0` (REVUE 1.3.4) | Rien : le workflow refait `clean build` (la référence) | La section `## [0.10.0]` du CHANGELOG sert de notes de version | **Sans risque** |
| Actions GitHub en v7, v6, v6, v7, v3 (REVUE 1.3.5) | La CI casse (entrées renommées, cache) ou perd des artefacts en silence (`if-no-files-found: ignore` pour les rapports) | Une action par PR ; vérifier la présence des artefacts JAR et rapports, et la durée | **Sûr si…** |
| Tests en jeu joués une seule fois en CI (REVUE 1.3.6) | **La robustesse sur monde déjà joué** (angle mort 9) disparaît | Voir 3.8 | **À reformuler** |
| Configuration cache (REVUE 1.3.7, 3.7) | Échec bruyant si une tâche n'est pas compatible, ou valeurs figées à la configuration | D'abord en mode avertissement, puis bloquant, CI verte à chaque étape | **Sûr si…** |
| `build-logic`, catalogue de versions, Dependabot Gradle (REVUE 3.7) | **Un garde-fou débranché en silence** (`check.dependsOn` perdu) : le build devient plus vert | **G8** obligatoire avant et après | **Sûr si…** |

#### NR 3.4 Montées d'outils (REVUE 2.3, étape A)

| Proposition | Ce qui peut régresser | Condition, garde-fou | Verdict |
|---|---|---|---|
| Correctifs JDK 21.0.12.1 et 25.0.4.1 | Presque rien (correctifs de sécurité et de bogues) | Build complet | **Sans risque** |
| Gradle 9.8.2 | Presque rien (correctif) | Build, validation du wrapper | **Sans risque** |
| Loom 1.19.3, plugin `net.fabricmc.fabric-loom-remap` | Il faut Gradle sous Java 25 (CI à adapter). Le remapping change (tiny-remapper, sélecteurs de lambdas de mixin), donc **le JAR de production** aussi, ce que les tests de dev ne voient pas. Les configurations de lancement deviennent des tâches | **G7** (test de fumée en production) ; G8 ; voir aussi l'API de lancement en 3.3 | **Sûr si…** |
| Fabric Loader 0.20.1 (Mixin 0.17.5) | **La promesse `fabricloader >=0.16.9`** : compiler contre un Mixin plus récent a déjà cassé un chargeur ancien (`@Redirect`). Les tests de dev ne le voient pas | **G7 avec le chargeur minimal déclaré** ; sans cet essai, ne pas monter. Relever le minimum serait un changement voulu (5.4) | **Sûr si…** |
| JUnit 6.1.3 | Compatibilité de `fabric-loader-junit` non vérifiée. Risque silencieux : **moins de tests découverts** | **G8, plancher de 749 exécutions et de 99 tests en jeu** ; sinon 5.14.4 | **Sûr si…** |
| Checkstyle 14.3.0 | Modules renommés ou retirés, nouveaux défauts : échecs bruyants (`maxWarnings 0`) | Corriger la configuration, sans désactiver de règle | **Sûr si…** |
| JaCoCo épinglé à 0.8.15 | Les filtres d'une nouvelle version ne font que retirer des instructions comptées | Build complet | **Sans risque** |

#### NR 3.5 Structure (REVUE 3.x)

| Proposition | Ce qui peut régresser | Condition, garde-fou | Verdict |
|---|---|---|---|
| Logique sortie des classes exclues de la couverture (REVUE 3.4, PERF 3.9) | Le code déplacé devient mesuré, donc exige ses tests : protecteur. Risque silencieux : **un changement des motifs d'exclusion** fait sortir des classes de la mesure | Une classe à la fois, avec ses tests ; **G8, liste des classes exclues comparée à celle de TESTING.md** ; G6 pour le comportement client | **Sûr si…** |
| Source sets client et commun séparés (REVUE 3.1) | **`MixinTargetsTest` ne lit que `gtgs_backrooms.mixins.json`** : une configuration client séparée échapperait au test en silence. Les classes client mesurées (`ViewCone`, `PortalViewTransform`…) **sortiraient de la couverture en silence** si `classDirectories` n'inclut pas la sortie client. `verifyJarContents` refuserait la nouvelle configuration (bruyant) | Adapter `MixinTargetsTest` aux deux configurations ; G8 (liste des classes mesurées, plancher de tests) ; G7 | **Sûr si…** |
| Registres hors du socle et point d'accès des destinations (REVUE 3.2) | Les tests qui appellent `PortalTargets` sans l'enregistrement auraient une destination fausse. Ordre d'initialisation. Les identifiants de registre ne changent pas, donc les sauvegardes non plus | Enregistrer dans `GtgsBackroomsMod.onInitialize` et dans l'amorce de test. **Ne modifier aucun résultat attendu** de `PortalTargetsTest`, `LevelZeroArrivalTest` et des tests en jeu, qui servent d'oracle. G3, G10 | **Sûr si…** |
| Tests d'architecture (REVUE 3.3) | Rien à l'exécution ; seulement des faux positifs | Liste blanche initiale à jour | **Sans risque** |
| Gros fichiers découpés (REVUE 3.5) | Classes **serveur** : protégées par la couverture et les tests en jeu, mais couvert ne veut pas dire vérifié. Classes **client** (`PortalStencilRenderer`, `RemoteViewRenderer`, `ShaderPackPortalRenderer`, `StraddlingEntityRenderer`, `RemoteWorld`) : **aucune protection automatique** | Serveur : **G9** avant, pour connaître la force des tests. Client : **G6 et G11** avant et après, un découpage par PR | **Sûr si…** (serveur) ; **risqué sans G6** (client) |
| Déplacements de paquets (`sync/payload/`, `mixin/accessor/`) (REVUE 3.6) | Chaînes qui nomment des classes : configuration Mixin (`MixinTargetsTest`, bruyant), `UtilityClassesTest` (bruyant), points d'entrée de `fabric.mod.json`. **Le point d'entrée client n'est testé par aucune CI.** Les motifs d'exclusion de la couverture (silencieux), la documentation | G8 (exclusions), G7 (point d'entrée client), contrôle des références de la doc (REVUE, annexe B) | **Sûr si…** |
| `UtilityClassesTest` simplifié, constructeurs vides (REVUE 3.9) | Perte d'une garantie testée (« le constructeur refuse l'appel », par réflexion). Checkstyle garde « classe finale et constructeur privé » | Décision de l'auteur : c'est un changement de garantie, pas un bogue | **À reformuler** (voir 3.8) |
| Nouvelles règles Checkstyle, analyseur de nullité (REVUE 3.10) | Échecs bruyants seulement | Les corriger, ne pas les taire | **Sans risque** |
| Ressources générées par datagen (REVUE 3.8) | Un JSON généré qui diffère de l'actuel : modèle, état de bloc ou tag faux, **visible en jeu seulement** | Comparer le contenu JSON (sémantique) de chaque fichier généré à l'existant avant de basculer. `ModRegistriesTest` et `ModDatapackLoadingTest` restent | **Sûr si…** |
| Fichier de configuration pour les joueurs (REVUE 3.14) | Les valeurs par défaut ; **la priorité des `-D`** (la CI règle le cooldown des tests en jeu par `-D`) ; un fichier absent | Défauts identiques (`ModConfigTest`), `-D` prioritaire sur le fichier (test), fichier absent égal à aujourd'hui | **Sûr si…** |
| Icône et contact dans `fabric.mod.json` (REVUE 3.11) | `verifyJarContents` refuse une icône à la racine du JAR (bruyant) | La mettre sous `assets/gtgs_backrooms/` | **Sans risque** |
| Pièges de CLAUDE.md déplacés dans `docs/PIEGES.md` (REVUE 3.13) | **Les sessions d'IA ne liraient plus les pièges** : CLAUDE.md est chargé d'office, pas `docs/` | Voir 3.8 | **À reformuler** |
| `.vscode/settings.json` (REVUE 1.5.1, 3.15) | Rien côté code | Build complet | **Sans risque** |
| Critères de JVM du démon (REVUE 1.5.2, 3.15) | **Proposition corrigée le 25 octobre** (5.3) : un critère « Java 25 » exigerait un JDK 25 sur chaque machine et dans la CI, qui n'installe que le 21 ; le build ne se lancerait plus avec Java 21 seul, ce que promet CLAUDE.md | Critère 21 tant que Loom reste en 1.17 ; 25 seulement dans le lot qui monte Loom en 1.18+ et adapte la CI | **Sûr si…** |

#### NR 3.6 Performance (PERF)

| Proposition | Ce qui peut régresser | Condition, garde-fou | Verdict |
|---|---|---|---|
| Résolution asynchrone de l'arrivée (PERF 3.2) | **Le moment** où le portail apparaît : aujourd'hui au tick de la pose (après le gel), ensuite quand les chunks d'arrivée sont prêts ; entre les deux, le cadre n'est pas encore un portail (5.4). **Le résultat** (même arrivée qu'avant ?). Les tickets (fuite de chunks chargés). Les tests en jeu bornés en ticks. **Trois fichiers de tests en jeu appellent `PortalTargets.resolve` et `arrive` de façon synchrone** | Garder **le noyau de recherche synchrone et pur, inchangé**, oracle de ces tests ; n'ajouter qu'une couche qui attend les chunks. **G3** (arrivées de référence relevées avant) ; **G4** (aucune génération synchrone, aucun ticket restant). Allonger une borne de ticks est permis, affaiblir une assertion non | **Sûr si…**, puis décision de l'auteur sur le délai d'apparition (5.4) |
| Charge processeur du moniteur lue hors des threads de jeu (PERF 3.8) | `ModPerformanceMonitorTest` (format `cpuProc=n/a cpuSys=n/a`, `Snapshot.capture(null)`) ; un thread qui survit dans les tests ; la couverture du code du thread | Garder l'API et le format ; un échantillonneur injectable et déterministe en test ; thread démon arrêté avec le moniteur | **Sûr si…** |
| Choix du GPU documenté (PERF 3.1) | Rien | — | **Sans risque** |
| Brouillard et ciel des vues une fois par tick (PERF 3.3) | **Conflit avec CLAUDE.md** : « une décision de rendu se prend sur la position affichée, jamais sur celle du tick ». **Conflit avec la règle d'or 5** (zéro couture). La couleur, échantillonnée à la position de la caméra, marcherait par paliers de 50 ms, avec un saut possible à la bascule | Voir 3.8, corrigé en 5.3 : seule une réutilisation à entrées identiques ne change aucune image | **À reformuler** |
| Budget de vues, imbrication réglable, grille réduite (PERF 3.4) | Une imbrication coupée change l'image : le cadre imbriqué devient transparent, comme au-delà d'un niveau aujourd'hui (`docs/PORTALS.md`), ou sombre si on le remplit. **Une grille réduite touche des pièges connus** : la détection d'un mod de terrain par `getViewDistance() == 0`, le rendu complet sous Iris (`renderWhole`), `ViewSectionWalker` | Le défaut doit rester le comportement actuel ; une option seulement ; la grille réduite est une **recherche** à prototyper sur les trois chemins de rendu (G11) | **À reformuler** |
| Monde quitté ramené à la fenêtre, mondes inutilisés libérés (PERF 3.5) | **Zéro couture** : un retour rapide verrait un monde vidé (image noire, chunks manquants) ; même reformulé, un retour tardif se reconstruit (5.3) | Voir 3.8 et 5.4 | **À reformuler**, puis décision de l'auteur |
| Chunks du monde quitté gardés plus longtemps (PERF 3.7) | La mémoire (contraire de la proposition précédente) ; un chunk périmé. Les trois tests en jeu des chunks tenus protègent le serveur | Arbitrer avec la proposition précédente (on ne peut pas maximiser les deux) ; étendre les tests en jeu des chunks tenus à la nouvelle durée | **Sûr si…** (après arbitrage) |
| Allocations résiduelles (PERF 3.9) | Le comportement de classes **client non mesurées** (`PortalSightReporter`, `StraddlingEntityRenderer`, `RemoteWorld`). `PortalViewTransform.aim`, ajouté depuis à la liste, est dans une classe mesurée | D'abord extraire la logique, mesurée (3.5) ; **G5** (zéro allocation) ; G6 | **Sûr si…** |
| Création du monde distant étalée ou anticipée (PERF 3.10) | Images vides au début d'une vue si on étale ; mémoire pour des portails jamais regardés si on anticipe | Corrigé en 5.3 : anticiper seulement ; prototype ; G6 sur « apparition d'un portail » | **Sûr si…** (anticipation, prototype validé) |
| Options pour les joueurs (PERF 3.4, 3.6) | Rien si les valeurs par défaut sont celles d'aujourd'hui | Tests des défauts | **Sans risque** |
| Monde du joueur non dessiné derrière un grand cadre (PERF 3.6) | Ordre des translucides, entités à cheval sur le cadre, troisième personne, chemin Iris | Recherche ; G6 et G11. La variante par pré-passe de profondeur (EXPLO 3.3, GPU2) vise une image identique : prouvée par G6 sur V5, elle reste dans les lots | **Risqué**, à prototyper |
| Cooldown de traversée par défaut non nul (PERF 3.7) | **Le jeu** : un joueur ne peut plus enchaîner aller et retour. Les tests en jeu règlent déjà leur cooldown par `-D` (3 ticks) | Garder 0 par défaut ; une autre valeur s'annonce dans le CHANGELOG | **Changement voulu**, hors des lots sans régression (5.4) |
| Cadre trop haut pour un étage du Level 0 (PERF 3.12) | **Les nouvelles arrivées** dans le Level 0 changent (voulu). Les portails existants gardent leur arrivée sauvée (`DestX/Y/Z`). Les empreintes ne bougent pas (génération intacte) | Changement voulu : annoncé dans le CHANGELOG, attentes de `LevelZeroArrivalTest` étendues, pas réécrites | **Changement voulu**, hors des lots sans régression (5.4) |

#### NR 3.7 Noms de Mojang et portage (REVUE 2.3, étapes B, C, D)

- **Noms de Mojang sur 1.21.1** : **sûr si…** C'est mécanique, mais quatre familles de chaînes échappent au renommage
  automatique et cassent :
  - les noms de champs lus par réflexion dans les tests en jeu (`"worlds"`, `"goalSelector"`) ;
  - les cibles de mixins écrites en chaînes (`@Inject(method = …)`, `@At(target = …)`) ;
  - les `@Accessor` nommés ;
  - les noms vanilla cités dans CLAUDE.md, PORTALS.md et les commentaires.

  Ce qui protège déjà :
  - `MixinTargetsTest` et les tests en jeu (bruyants) ;
  - **les empreintes du Level 0, qui doivent rester identiques** : seuls les noms changent ;
  - les clés NBT et de traduction, qui sont des chaînes et ne sont pas renommées.

  Il faut ajouter **G7**, puisque le remapping de production change.
- **Portage 1.21.1 → 1.21.11 → 26.x** : **parité**. La couche de rendu de Minecraft a été réécrite : une régression
  visuelle est possible par construction. On prouve la parité par :
  - les oracles inchangés : empreintes, 749 tests unitaires, 99 tests en jeu ;
  - la liste « En jeu » de PORTALS.md, prise comme recette ;
  - les captures de référence G6 et les chiffres du banc ;
  - les tests en jeu côté client, disponibles à partir de 1.21.11, ajoutés pendant le portage.

#### NR 3.8 Les six propositions reformulées pour être non régressives

Les parties REVUE et PERF portent désormais ces versions reformulées. Les points 1 et 3 ont encore été corrigés le 25
octobre (5.3), qui l'emporte. Les lignes « À reformuler » des tableaux 3.3 à 3.6 décrivent la proposition telle qu'elle
était d'abord écrite.

1. **Brouillard et ciel (PERF 3.3).** Ne pas figer la couleur au tick. Deux solutions qui ne régressent pas :
   - ne la calculer **qu'une fois par image et par monde distant**, partagée entre ses vues, ce qui garde le principe de
     la position affichée ;
   - ou l'échantillonner **au tick puis l'interpoler par `tickDelta`** entre deux échantillons.

   La preuve exigée :
   - un test unitaire de la fonction extraite, qui compare la valeur mise en cache à la valeur directe sur un trajet de
     caméra (écart d'au plus 1/255 par canal) ;
   - G6 : captures identiques à la tolérance près, à la bascule, en vue imbriquée, caméra dans l'eau ou la lave, sous
     cécité ou obscurité, dans le brouillard épais du Nether.

   **Proposition corrigée le 25 octobre (5.3)** : ces deux solutions changent encore l'image, même si l'écart reste
   faible. Seule une réutilisation à entrées identiques n'en change aucune. Le reste passe en décision de l'auteur
   (5.4).
2. **Budget de vues (PERF 3.4).** Une option **désactivée par défaut**, ou dont la valeur par défaut reproduit le
   comportement actuel (un niveau d'imbrication). La réduction de la grille est un sujet de recherche, pas un correctif :
   elle passe par un prototype validé sur le chemin vanilla, avec un mod de terrain, et sous Iris (G11).
3. **Monde quitté allégé (PERF 3.5).** N'alléger qu'après un délai sans retour, et seulement loin de tout portail vers
   ce monde. Le délai est à mesurer : bien au-delà d'un aller-retour réel. Garder toujours la fenêtre du portail.
   Prouver :
   - un retour avant le délai est identique à aujourd'hui (G6) ;
   - un retour après le délai se comporte comme l'apparition d'un portail (comportement existant, déjà mesuré).

   **Proposition corrigée le 25 octobre (5.3)** : ce second cas change l'image. Aujourd'hui, un retour est sans couture
   quel que soit le délai ; après la proposition, un retour tardif verrait la vue se reconstruire. C'est un arbitrage
   entre mémoire et continuité, à décider par l'auteur (5.4).
4. **Tests en jeu joués une seule fois en CI (REVUE 1.3.6).** Seulement si une étape **explicite** rejoue les tests en
   jeu sur le monde déjà joué (G12). Sinon, garder le triple passage et l'écrire comme une règle, pas comme un effet de
   bord.
5. **Pièges de CLAUDE.md (REVUE 3.13).** Soit les garder dans CLAUDE.md, soit les importer depuis CLAUDE.md par
   `@docs/PIEGES.md` (Claude Code charge les imports, mais le coût en contexte reste le même). Un simple renvoi en prose
   fait perdre ce savoir aux sessions qui ne suivent pas le lien.
6. **`UtilityClassesTest` (REVUE 3.9).** Garder les constructeurs qui refusent l'appel (garantie actuelle), et ne
   remplacer que la **liste à la main** par une découverte des classes, sans perte de garantie.

---

### NR 4. Le plan anti-régression

#### NR 4.1 Principes

1. **La référence d'abord.** Elle est relevée (section 1). On la relève de nouveau au début de chaque lot.
2. **Le garde-fou avant le changement qu'il protège.** Il est écrit et vert **sur le code actuel**, dans une PR
   distincte, avant la PR qui change le comportement. Un garde-fou écrit après le changement ne prouve rien.
3. **Un oracle ne bouge pas dans la PR du code qu'il juge** (empreintes, arrivées de référence, octets des payloads,
   sauvegardes de référence). S'il doit bouger, c'est voulu, justifié et annoncé dans le CHANGELOG, dans une PR à part.
4. **Un test existant n'est jamais affaibli.** Toute modification de test dans un lot se justifie assertion par
   assertion : une borne de ticks peut s'allonger, une attente ne peut pas disparaître.
5. **Un lot, une branche, une PR**, CI verte, et un seul sujet. Les montées d'outils une à une.
6. **Mesurer avant et après sur la même machine, dans la même heure.** Le bruit va jusqu'à ±25 % sur un portable
   chargé : pas de conclusion sur moins de 10 %.

#### NR 4.2 Les garde-fous à ajouter

| | Garde-fou | Ce qu'il empêche | Protège | Comment |
|---|---|---|---|---|
| **G1** | **Sauvegardes de référence 0.10.0** | Un monde existant qui ne se relit plus ou se relit autrement | REVUE 3.2, 3.5, 3.6 ; PERF 3.12 ; noms Mojang ; portage | NBT d'un bloc portail, d'une entité portail et d'une baguette (le composant de réglages y est sauvé en NBT), écrits par la 0.10.0 et versionnés dans les tests ; relecture comparée champ par champ. En jeu : une structure de test contenant un bloc portail sauvé par la 0.10.0, qui doit reposer les mêmes portails. Sauvegardes relevées : REF 6 |
| **G2** | **Octets de référence des payloads** et numéro de protocole | Un client et un serveur qui ne se comprennent plus | Toute PR qui touche `sync/` ; noms Mojang ; portage | Un test qui encode chaque payload d'exemple et compare aux octets versionnés (en plus de l'aller-retour). Un changement voulu va avec un numéro de protocole incrémenté. Ce numéro, vérifié à la connexion avec un message clair, est une fonctionnalité nouvelle, à décider. Octets relevés : REF 5 |
| **G3** | **Arrivées de référence** (test en jeu) | Une destination différente après un remaniement | PERF 3.2 ; REVUE 3.2 ; PERF 3.12 | Cas fixes (graine 0 du serveur de test) : Level 0 prêté, colonne du Nether, vide de l'End (plateforme), surface, sortie fixée, tirage au sort. L'arrivée attendue (position, orientation) est relevée sur le code actuel |
| **G4** | **Pas de génération synchrone à la pose** (test en jeu, après PERF 3.2) | Le retour du gel serveur | PERF 3.2 | Au tick de la pose vers une zone jamais générée, le chunk d'arrivée n'est pas encore chargé ; la destination arrive ensuite ; plus aucun ticket de recherche une fois résolue. Une assertion structurelle, sans chronomètre (le serveur de test tique sans attendre) |
| **G5** | **Zéro allocation mesurée** (tests unitaires) | Le retour d'allocations dans les chemins chauds (règle de CLAUDE.md) | PERF 3.3, 3.9 ; découpages | `ThreadMXBean.getThreadAllocatedBytes` autour des fonctions pures chaudes, après échauffement : attendu 0 octet. Limite : l'analyse d'échappement du JIT peut masquer une allocation, pas en inventer une. En complément, un relevé statique du bytecode, qui doit nommer les méthodes mémoïsées : REF 4 |
| **G6** | **Banc client scripté** : chiffres et captures | Une régression d'image ou de performance côté client (angle mort 1) | Toute PR qui touche `client/`, `mixin/client/` ou le rendu ; montées d'outils ; portage | Le monde « Portal Lab », rejoué par touches postées à la fenêtre du banc : les chiffres sur les scènes S0 à S7 et R0 à R6 de PERF 1, les captures sur les scènes V0 à V7 (5.2). HUD caché (F1), captures `PrintWindow`, comparées aux références avec une tolérance par pixel. Chiffres du moniteur comparés à la référence (±10 %), **après** la correction de PERF 3.8, sans quoi les « images lentes » ne veulent rien dire. Sur le PC de l'auteur (référence de PORTALS.md) et, si possible, sur ce portable. Les scripts sont à verser au dépôt, décision de l'auteur (voir `docs/TESTING.md`). Protocole, bruit mesuré et critère d'acceptation : section 5.2 |
| **G7** | **Test de fumée en production** | Un JAR de production qui ne démarre pas (remapping, refmap, chargeur ancien, point d'entrée client) | Loom, Loader, Mixin, noms Mojang, source sets séparés, déplacements de paquets, portage | Loom 1.17 a déjà les tâches `ClientProductionRunTask` et `ServerProductionRunTask`. Le client de production (qui contient le serveur intégré, donc aussi les mixins communs) se lance avec le JAR construit, **avec le chargeur minimal déclaré puis avec le chargeur courant**, jusqu'au chargement d'un monde, puis le journal est lu (aucune erreur de mixin). En CI Linux sous écran virtuel, ou en local. Pas de serveur de production : il faudrait accepter l'EULA, ce que FINALCHECK interdit |
| **G8** | **Contrôle des garde-fous** | Un garde-fou débranché ou affaibli en silence (angle mort 8) | `build-logic`, montées d'outils, source sets séparés, couverture, API de lancement | (a) le graphe de `./gradlew build --dry-run` contient `checkstyleMain`, `checkstyleTest`, `checkstyleGametest`, `test`, `runGameTest`, `jacocoCoverageVerification`, `verifyJarContents` ; (b) planchers : au moins 749 exécutions unitaires, 99 tests en jeu, `build/junit.xml` présent ; (c) la liste des classes exclues de la couverture est égale à la liste approuvée de TESTING.md ; (d) à chaque modification du build, un passage « canari » en local : une branche jetable qui introduit une violation de chaque sorte (branche non couverte, ligne non ASCII, `@Redirect`, fichier parasite dans le JAR, clé de traduction manquante, empreinte modifiée) doit faire échouer le build. Données : REF 1 et 7 |
| **G9** | **Mutation testing** (rapport, pas un verrou au départ) | Remanier en croyant être protégé par des tests qui n'assertent rien | Découpage des classes serveur, extraction de logique client | Sur les paquets purs (génération, `PortalFacing`, transition, logique de synchronisation) : les mutants survivants désignent les tests à renforcer **avant** le remaniement |
| **G10** | **Tests d'architecture** | Une dépendance interdite qui réapparaît | Structure | REVUE 3.3. Les cinq règles sont vraies aujourd'hui ; listes blanches : REF 2 |
| **G11** | **Matrice de compatibilité** (manuelle) | Une régression avec un mod de terrain ou un pack de shaders | Rendu des vues, montées de Loader et de Loom, portage | Vanilla, mod de terrain, Iris avec deux packs, sur le banc de l'auteur (GUIDE section 8) ; une ligne par PR client |
| **G12** | **Rejeu explicite sur monde déjà joué** | La perte de la robustesse sur monde déjà joué | Toute modification de la CI | Une étape de CI qui relance `runGameTest` sur le dossier de run laissé par le passage précédent, au lieu d'un effet de bord |

#### NR 4.3 L'ordre des lots, et la porte de sortie de chacun

La liste complète, correction par correction, est en BILAN 6, qui reprend ce tableau.

| Lot | Contenu | Porte de sortie (en plus du build complet vert) |
|---|---|---|
| **0. Protections** | G1, G2 (octets), G3, G8 (a, b, c), G10, rapport G9. Aucun comportement touché | Garde-fous verts **sur le code actuel** ; références versionnées |
| **1. Sans risque** | Commentaires, documentation, corrections de tests (3.1), `-Werror`, `.vscode/settings.json`, critère de JVM du démon à 21 (5.3), contact, icône (fichier fourni par l'auteur), tag `v0.10.0`. Après décision de l'auteur (5.4) seulement : commande de debug traduite (3.2) et noms français corrigés | Planchers G8 tenus ; la seule modification de test (commande de debug) justifiée assertion par assertion |
| **2. Build et CI** | API de lancement de Loom, Fabric API minimale (la plus ancienne essayée, sinon `*` : 5.3), actions une à une, configuration cache (avertissement puis bloquant), `build-logic` et catalogue | G8 complet, canari compris ; `junit.xml` présent ; `runClient` avec `-P…` et `quickPlayWorld` vérifiés |
| **3. Mesure fiable** | Moniteur sans lecture bloquante (PERF 3.8), mesure du temps GPU (EXPLO 3.3, GPU3), puis nouvelle référence du banc G6 | Images lentes du banc S0 revenues à 0 ; références G6 enregistrées |
| **4. Gel serveur** | Résolution asynchrone de l'arrivée (PERF 3.2), après décision de l'auteur sur le délai d'apparition (5.4) | G3 inchangé, G4 vert, tests de `PortalTargets` intacts |
| **5. Structure** | Exclusions de couverture resserrées classe par classe, source sets séparés, registres et point d'accès, déplacements de paquets, découpages : une PR chacun | G8 (c) mis à jour à chaque fois ; G6 et G11 pour tout ce qui touche le client ; G9 relu pour le serveur ; G7 pour les source sets et les déplacements |
| **6. Performance client** | Brouillard et ciel réutilisés à entrées identiques (5.3), allocations résiduelles, chunks tenus plus longtemps, création anticipée du monde distant (5.3). Pistes exactes de l'exploration : vues cachées (preuve par tranche et GPU1), pré-passe de profondeur du grand cadre (GPU2). Après décision de l'auteur (5.4) seulement : brouillard et ciel approchés, monde quitté allégé. Puis les recherches (budget de vues, grille, grand cadre) derrière une option désactivée par défaut | G5 ; G6 (captures et chiffres, critère de 5.2) ; G11 ; aucune image différente au-delà de la tolérance, aucun chiffre du banc en recul |
| **7. Outils** | Correctifs JDK et Gradle, Loom 1.19.3, Loader 0.20.1, JUnit, Checkstyle, JaCoCo : un par PR | G7 (chargeur minimal et courant), G8 ; empreintes inchangées |
| **8. Noms Mojang, puis portage** | Étapes B, C, D ; le portage après décision de l'auteur (5.4) | Empreintes identiques ; 749 et 99 tests verts ; G1, G2, G3 ; G7 ; recette de PORTALS.md ; G6 et G11 ; tests en jeu côté client ajoutés dès 1.21.11 |

#### NR 4.4 Si une régression passe malgré tout

- **Revenir en arrière** : chaque lot est fait de commits atomiques. On fait `git revert` de la PR, et un tag marque la
  fin de chaque lot.
- **Sauvegardes** : on ne retire jamais la lecture d'une ancienne clé NBT ou d'un ancien champ de composant (G1). Une
  version précédente qui relit un monde sauvé par une version plus récente doit ignorer les champs inconnus, pas les
  perdre. C'est à vérifier dans G1 pour le bloc portail.
- **Après coup** : une régression trouvée en jeu devient d'abord un test (ou une scène G6) qui échoue, ensuite seulement
  un correctif. C'est la règle de FINALCHECK (« Un problème trouvé se corrige avant de passer au point suivant »),
  étendue au garde-fou.

#### NR 4.5 Ce que ce plan ne peut pas garantir

- **L'équivalence visuelle** ne vaut que pour les scènes et la tolérance de G6. Le ressenti (fluidité, latence d'une
  traversée) reste un jugement humain, sur le banc de l'auteur.
- **La compatibilité avec les autres mods** reste manuelle (G11) et limitée aux mods et versions essayés.
- **Les chiffres de performance** restent bruyants sur un portable chargé : seule une tendance nette et répétée compte.
- **Le portage** vise la parité, pas l'identité ; les tests en jeu côté client n'arrivent qu'à partir de 1.21.11.

---

### NR 5. Lecture stricte : aucune fonctionnalité ni aucune image ne change

> Ajout du 25 octobre 2026. Les sections 3 et 4 jugent les propositions contre la qualité existante. L'auteur demande
> plus : qu'aucune fonctionnalité ni aucun visuel ne régresse. Cette section relit chaque proposition sous cette
> exigence. Elle corrige celles qui, telles qu'écrites, la violaient, et met à part celles qui changent volontairement
> ce que voit le joueur. Les parties REVUE et PERF portent désormais les versions corrigées.

#### NR 5.1 Ce qui compte comme une régression

- **Fonctionnalité** : tout ce qui est observable en jeu, et ce qui permet au mod de tourner.
  - En jeu : pose, destination et apparition des portails, traversées, vues, sons, interactions, commandes.
  - Les versions de Minecraft, du chargeur et de Fabric API que `fabric.mod.json` accepte : un joueur qui lance le mod
    aujourd'hui doit pouvoir le lancer demain.
  - La relecture des mondes sauvés, et le dialogue entre client et serveur.
  - Pour le développement : les machines où le build se lance.

  Oracles : tests unitaires et en jeu, la liste « En jeu » de `docs/PORTALS.md`, G1 à G4, G7.
- **Visuel** : toute image qui s'écarte d'une référence au-delà du bruit mesuré (5.2). Il y a trois chemins de rendu :
  vanilla, avec un mod de terrain, sous un pack de shaders. Le banc de ce portable ne couvre que le premier ; les deux
  autres passent par G11, sur le banc de l'auteur.
- **Un changement voulu n'est pas une régression, mais il ne passe pas pour une correction.** Tout ce qui change ce
  que voit ou peut faire le joueur, même en mieux, sort des lots « sans régression » et attend l'auteur (5.4).

#### NR 5.2 L'oracle visuel, mesuré

Le banc G6 tourne sur ce portable : puce intégrée, 854 × 480, monde « Portal Lab », HUD masqué (F1), VSync coupée,
distance de rendu 12, graphismes détaillés. L'heure est figée à midi et la météo au beau. Il rejoue neuf scènes par
commandes :

| Scène | Contenu |
|---|---|
| V0 | Monde sans portail |
| V1 | Portail 1 × 2 vers le Level 0, à quatre blocs |
| V2 | Le même, de près |
| V3 | Troisième personne, le joueur à cheval sur le plan |
| V3b | Porte-armure immobile à cheval sur le plan |
| V4 | Depuis le Level 0, face au portail de retour |
| V5 | Cadre 8 × 8 |
| V6 | Trois portails : Level 0, Nether, sortie fixée dans l'Overworld |
| V7 | Les mêmes, de loin |

Quatre passages sur le code actuel ont été comparés deux à deux, pixel par pixel. L'écart est le plus grand des trois
canaux, sur 255 :

| Scène | Écart maximal | Pixels à plus de 10 niveaux |
|---|---|---|
| V0 | 0 | 0 |
| V1 | 2 | 0 |
| V2 | 5 | 0 |
| V3 | 199 | 52 037 en tout, tous dans la zone animée du joueur (x 392 à 467, y 217 à 387) |
| V3b | 3 (deux passages) | 0 |
| V4 | 9 | 0 |
| V5 | 1 | 0 |
| V6 | 4 | 0 |
| V7 | 3 | 0 |

- **Critère d'acceptation** : après un changement, aucun pixel ne s'écarte de plus de 10 niveaux de chacune des
  références, la zone animée de V3 exceptée. V3b couvre le même cas, à cheval sur le plan, sans animation.
- **Les références ne valent que pour cette machine et ces options.** Elles sont gardées hors du dépôt. Sur le banc de
  l'auteur, il faut les refaire avant de s'en servir.
- **Dans V4, le haut de l'ouverture est presque noir.** C'est la bande sombre de l'horizon de l'Overworld vu du Level 0,
  déjà décrite dans `docs/PORTALS.md` (`RemoteViewRenderer`, brouillard de la dimension de la vue). Elle fait partie de
  la référence.
- **Ce que le banc ne voit pas** :
  - un mod de terrain, un pack de shaders ;
  - l'eau, la lave et les effets de statut ;
  - une traversée en mouvement.

  Pour une proposition qui touche ces cas, il faut ajouter la scène avant le changement, ou passer par G11.

#### NR 5.3 Propositions corrigées

Telles qu'elles étaient écrites, ces cinq propositions changeaient quelque chose.

1. **Bornes de `fabric.mod.json` (REVUE 1.3.3).**
   - Problème : `processResources` transmet `loader_version`, qui vaut 0.19.5. « Utiliser ces variables » écrirait
     `fabricloader >=0.19.5` au lieu de `>=0.16.9`, et un joueur sous un chargeur 0.16.9 à 0.19.4 ne pourrait plus
     lancer le mod. De même, une Fabric API minimale prise à la version de compilation (0.116.17) écarterait les
     versions 0.116.0 à 0.116.16 sans preuve qu'elles ne marchent pas.
   - Correction : garder `>=0.16.9`, en retirant `loader_version` des propriétés transmises ou en ajoutant une propriété
     dédiée au minimum. `~${minecraft_version}` donne la même borne qu'aujourd'hui. Pour Fabric API, déclarer la plus
     ancienne version essayée avec succès en production (G7) ; tant qu'aucune ne l'est, garder `*`.
2. **Critère de JVM du démon Gradle (REVUE 1.5.2 et 3.15).**
   - Problème : un critère « Java 25 » exigerait un JDK 25 sur chaque machine (ou son téléchargement par Gradle, qu'il
     faudrait configurer) et dans la CI, qui n'installe que le 21. Le build ne se lancerait plus là où seul Java 21 est
     installé, alors que CLAUDE.md promet qu'un seul JDK suffit.
   - Correction : critère 21 tant que Loom reste en 1.17. Passer à 25 seulement dans le lot qui monte Loom en 1.18 ou
     plus, avec la CI et la doc.
3. **Brouillard et ciel des vues (PERF 3.3 ; reformulation 1 de 3.8).**
   - Problème : les deux solutions reformulées changent encore l'image. Vanilla échantillonne 216 points de biome à la
     position de la caméra. La couleur du brouillard dépend aussi de l'heure du jour, qui change à chaque tick
     (`getSkyAngle` ignore la fraction d'image : lu dans le jar de 1.21.1), et des biomes, qu'un chunk arrivé change.
     - Une couleur partagée entre les vues d'un même monde distant serait prise à la caméra d'une seule vue. Or chaque
       vue a la sienne : deux portails vers le même monde, ou une vue imbriquée.
     - Échantillonnée au tick puis interpolée, elle s'écarterait du calcul direct, qui suit la caméra à chaque image.
     - Même « échantillonner puis assombrir », mathématiquement égal pour l'Overworld, n'est pas égal au bit près en
       virgule flottante.
   - Correction : la seule variante sans écart réutilise un résultat pour des entrées identiques. Il faut le même monde,
     la même position exacte de la caméra, la même heure, et aucun chunk reçu entre-temps. Elle sert à une caméra
     immobile : pendant un tick quand l'heure tourne, tout le temps quand elle est figée. Toute variante approchée (au
     plus 1/255 par canal, invisible à l'œil mais pas identique) va en 5.4.
4. **Monde quitté allégé (PERF 3.5 ; reformulation 3 de 3.8).**
   - Problème : « un retour après le délai se comporte comme l'apparition d'un portail ». Aujourd'hui, un retour est
     sans couture quel que soit le délai ; avec la proposition, un retour tardif verrait la vue se reconstruire.
   - Correction : ce n'est pas une optimisation sans régression, c'est un arbitrage entre mémoire et continuité (5.4).
     Garder plus longtemps les chunks du monde quitté (PERF 3.7) ne change aucune image et reste dans les lots.
5. **Création du monde distant (PERF 3.10).**
   - Problème : l'étaler sur plusieurs images laisse la vue vide ou incomplète à ses premières images.
   - Correction : seulement l'anticiper, en créant le monde distant quand le portail entre dans le rayon d'activation,
     avant la première vue. Le coût mémoire des portails jamais regardés est à mesurer.

Les autres propositions ne changent rien de visible si elles sont faites sous leurs garde-fous. Trois précisions :

- **Résolution asynchrone de l'arrivée (PERF 3.2)** : le calcul du Level 0 peut passer sur un thread de travail sans
  changer son résultat. Vérifié en lecture :
  - `LevelZeroArrival.find` ne lit du monde que sa graine, à lire sur le thread serveur ;
  - il crée son propre cache de layout ;
  - le cache de secteurs partagé est synchronisé, et le générateur s'en sert déjà depuis plusieurs threads.

  En revanche, le moment où le portail apparaît change (5.4).
- **Logique sortie des classes client, découpages, déplacements (REVUE 3.4 à 3.6)** : G6 ne couvre que le rendu
  vanilla. Certaines classes servent aussi les chemins « mod de terrain » et « pack de shaders » : `RemoteViewRenderer`,
  `ShaderPackPortalRenderer`, `PortalStencilRenderer`, et ce qu'elles appellent. Elles ne se touchent qu'avec G11 sur le
  banc de l'auteur, avant et après.
- **Ressources générées, nouvelles règles Checkstyle (REVUE 3.8, 3.10)** : un JSON généré doit être identique,
  sémantiquement, à celui d'aujourd'hui. Une violation se corrige par une réécriture strictement équivalente, et toute
  branche ajoutée reste couverte.

#### NR 5.4 Changements voulus : à décider par l'auteur

Ces propositions changent volontairement ce que voit ou peut faire le joueur, souvent en mieux, mais elles changent
quelque chose. Chacune ne se fait qu'après une décision explicite de l'auteur, et s'annonce dans le CHANGELOG. Les
pistes de la partie EXPLO qui changent l'image ou le jeu s'y ajoutent selon la même règle : paliers du LOD, fenêtres
gardées, synchronisation plus tôt, en cône ou plus rapide, imposteur, message ou réglage de Windows pour la carte
graphique. Ses pistes exactes (vues cachées, pré-passe de profondeur) restent dans les lots (lot 6) :

| Proposition | Ce qui change pour le joueur |
|---|---|
| Commande de debug traduite (REVUE 1.1.3) | En jeu, le texte gagne ses accents. La console d'un serveur dédié l'affiche en anglais, au lieu du français sans accents d'aujourd'hui |
| Noms français corrigés (REVUE 1.4.11) | « Néon » prend son accent ; la casse des noms de blocs change, si l'auteur l'harmonise |
| Résolution asynchrone de l'arrivée (PERF 3.2) | Plus de gel du serveur à la pose. En échange, le cadre ne devient portail qu'une fois les chunks d'arrivée prêts, et non au tick de la pose : un joueur qui passe le cadre aussitôt le traverse sans changer de monde |
| Cadre trop haut pour un étage du Level 0 (PERF 3.12) | Les nouvelles arrivées dans le Level 0 d'un cadre de plus de quatre blocs changent ; les portails existants gardent leur arrivée sauvée |
| Brouillard et ciel des vues approchés (PERF 3.3) | Couleur des vues à au plus 1/255 près par canal |
| Cooldown de traversée par défaut non nul (PERF 3.7) | On ne peut plus enchaîner aller et retour par un même portail |
| Monde quitté allégé après un délai (PERF 3.5) | Un retour tardif voit la vue se reconstruire, en échange de centaines de Mo rendus |
| Fabric API ou chargeur minimal relevés (REVUE 1.3.3, 2.3) | Les joueurs sous une version plus ancienne ne peuvent plus lancer le mod |
| Portage de Minecraft (REVUE 2.3, étapes C et D) | Parité, pas identité : l'image change avec la couche de rendu de Minecraft |
| Grand cadre, grille de vue réduite (PERF 3.6, 3.4) | Recherches qui changent l'image par construction ; seulement derrière une option désactivée par défaut. La pré-passe de profondeur du grand cadre (GPU2), qui vise une image identique, n'en fait pas partie |

#### NR 5.5 Vérifications faites le 25 octobre

Toutes en lecture, sur le code de `d331d53` : le bytecode compilé, le jar de Minecraft mappé et les sorties des codecs
du mod. Les outils de relevé étaient jetables et ont été retirés ; rien n'a été modifié dans le dépôt. Les données sont
dans la partie REF.

- **Exclusions de couverture** : 29 classes nommées et deux paquets excluent exactement les mêmes 87 fichiers que les
  18 motifs actuels. Passer à une liste explicite (REVUE 3.4) ne change donc pas la mesure. TESTING.md ne nomme que 19
  de ces classes.
- **Architecture** : les cinq règles de REVUE 3.3 sont vraies aujourd'hui. Le socle a cinq dépendances vers le contenu,
  et sept classes appellent OpenGL directement.
- **Classes utilitaires** : le bytecode en contient 72, et `UtilityClassesTest` en liste 55. Les 17 autres sont des
  classes client, toutes conformes à la règle. Une découverte automatique n'ôte rien et ajoute 17 vérifications.
- **Chemins chauds** : le relevé ne trouve aucune allocation en régime établi dans 32 méthodes de collision, traversée,
  garde, pertinence et vue. Les boîtes et la destination du portail sont mémoïsées, et G5 devra nommer ces méthodes. Il
  a trouvé une allocation de plus, à chaque image : un `Vec2f` par vue dans `PortalViewTransform.aim` (ajoutée à
  PERF 3.9).
- **Réseau** : les douze identifiants, et les octets de onze payloads, sont relevés pour G2.
- **Sauvegardes** : quatre sauvegardes de référence sont relevées pour G1 (bloc configuré, bloc neuf, baguette, données
  de l'entité). Leur relecture reste à essayer.
- **Brouillard** : la couleur vanilla dépend de la position de la caméra à chaque image, de l'heure à chaque tick et
  des chunks reçus (5.3, point 3).
- **Level 0** : la recherche d'arrivée peut tourner hors du thread serveur (5.3, précisions).

---

## REF — Les références de la version 0.10.0

> Relevées le 25 octobre 2026, par lecture du bytecode compilé et des sorties des codecs du mod. Les outils de relevé
> étaient jetables ; ils ont été retirés, et **rien n'a été modifié dans le dépôt**. Ces données sont les oracles des
> garde-fous de NR 4.2, à verser dans les tests le moment venu. Une donnée de cette partie ne se modifie jamais. Un
> changement voulu s'ajoute à côté, daté et justifié.

### REF 1. Exclusions de la couverture (G8 c)

Les 18 motifs actuels de `coverageExcludes` (`build.gradle`) excluent exactement **87 fichiers `.class`**. On obtient
les mêmes 87 fichiers en nommant **deux paquets entiers et 29 classes**, chacune avec ses classes internes (`Nom` et
`Nom$*`). La comparaison, fichier par fichier, ne montre aucun écart.

- Paquets : `mixin/**`, `compat/**`.
- Racine : `GtgsBackroomsClientMod`.
- `backrooms/level0/client/model/` : `LevelZeroWallpaperBakedModel`, `LevelZeroWallpaperModelHandler`.
- `core/perf/client/` : `ClientPerformanceMonitorHook`.
- `core/portal/client/entity/` : `BackroomsPortalEntityRenderer`.
- `core/portal/client/interaction/` : `RemoteActions`, `RemoteTargeting`.
- `core/portal/client/sound/` : `FrameSoundPlayer`, `SoundAnchors`.
- `core/portal/client/transition/` : `ClientPortalCrossing`, `EntityHandover`.
- `core/portal/client/view/` : `FrameAreaRenderer`, `HandMask`, `PortalClipping`, `PortalStencilRenderer`,
  `ShaderPackPortalRenderer`, `ViewLightmaps`.
- `core/portal/client/wand/` : `PortalWandScreen`.
- `core/portal/client/world/` : `ClientPortalIndex`, `ClientPortalLight`, `ClippedVertexConsumers`,
  `PortalSightReporter`, `RemoteParticles`, `RemoteSyncReceiver`, `RemoteViewRenderer`, `RemoteWorld`,
  `RemoteWorldRegistry`, `StraddlingEntityRenderer`, `ViewSectionWalker`.

La table « Exclu » de `docs/TESTING.md` nomme 19 de ces 29 classes. Elle ne cite pas les 10 suivantes, couvertes
seulement par un motif de paquet : `ClippedVertexConsumers`, `ClientPortalCrossing`, `EntityHandover`, `RemoteActions`,
`RemoteTargeting`, `BackroomsPortalEntityRenderer`, `PortalWandScreen`, `LevelZeroWallpaperBakedModel`,
`LevelZeroWallpaperModelHandler`, `ClientPerformanceMonitorHook`. Elle cite aussi `FrameSoundPlayer` et `SoundAnchors`
par leur chemin, pas par leur seul nom. Pour que G8 (c) compare la liste du build à celle de TESTING.md, la table doit
nommer chaque classe par son nom simple.

### REF 2. Règles d'architecture (G10), vraies sur ce code

Les cinq règles de REVUE 3.3 tiennent aujourd'hui. Il n'y a donc aucun code à changer pour les écrire en tests : il
suffit des listes blanches ci-dessous.

- **Socle vers contenu** : exactement cinq dépendances.
  - `core/portal/block/PortalTargets` vers `backrooms/BackroomsConstants` et `backrooms/level0/LevelZeroArrival` ;
  - `core/registry/ModBlocks` et `core/registry/ModItems` vers `backrooms/level0/block/LevelZeroWallpaperBlock` ;
  - `core/registry/ModChunkGenerators` vers `backrooms/level0/generation/LevelZeroChunkGenerator`.
- **Commun vers client** : aucune référence, annotations comprises (cibles des mixins). Le commun, c'est tout ce qui
  n'est ni sous un paquet `client/` ni `GtgsBackroomsClientMod`. Il ne touche jamais à `net.minecraft.client`,
  `com.mojang.blaze3d`, `org.lwjgl`, `net.fabricmc.fabric.api.client`, ni aux classes client du mod.
- **Appels OpenGL directs** (`RenderSystem`, `GlStateManager`, `org.lwjgl.opengl`) :
  - sept classes : `FrameAreaRenderer`, `HandMask`, `PortalClipping`, `PortalStencilRenderer`,
    `ShaderPackPortalRenderer`, `RemoteViewRenderer`, `StraddlingEntityRenderer` ;
  - deux mixins client : `MixinFramebuffer`, `MixinVertexBuffer`.

  Aucun appel OpenAL n'existe encore.
- **Mixins** : hors de leur paquet, le code ne référence que 19 interfaces d'accès, jamais une classe de mixin.
  - Commun : `ActiveTargetGoalAccessor`, `EntityAccessor`, `EntityTrackerAccessor`, `EntityTrackerEntryAccessor`,
    `ServerChunkLoadingManagerAccessor`, `WorldAccessor`.
  - Client : `BackgroundRendererAccessor`, `BuiltChunkStorageAccessor`, `CameraAccessor`,
    `ClientPlayNetworkHandlerAccessor`, `ClientWorldAccessor`, `FrustumAccessor`, `LightmapTextureManagerAccessor`,
    `LimbAnimatorAccessor`, `MinecraftClientAccessor`, `ParticleManagerAccessor`, `SoundManagerAccessor`,
    `SoundSystemAccessor`, `WorldRendererAccessor`.
- **Journal** : seul `ModConstants` crée un logger (`LoggerFactory`). Aucun `System.out`, `System.err` ni
  `printStackTrace` dans le mod.

Une règle sur le bytecode doit lire aussi les annotations. Sinon, un mixin commun qui viserait une classe client
passerait inaperçu.

### REF 3. Classes utilitaires (REVUE 3.9)

Le bytecode contient **72 classes utilitaires**. Une classe utilitaire est ici une classe concrète, hors mixins et hors
classes générées par le compilateur (les `$1` des `switch` sur une énumération), dont chaque champ et chaque méthode
sont statiques. `UtilityClassesTest` en énumère 55 à la main. Les 17 autres sont des classes client exclues de la
couverture :

- `LevelZeroWallpaperModelHandler`, `ClientPerformanceMonitorHook` ;
- `RemoteActions`, `RemoteTargeting`, `FrameSoundPlayer`, `SoundAnchors`, `ClientPortalCrossing`, `EntityHandover` ;
- `FrameAreaRenderer`, `HandMask`, `PortalStencilRenderer`, `ViewLightmaps` ;
- `ClientPortalIndex`, `PortalSightReporter`, `RemoteSyncReceiver`, `RemoteWorldRegistry`, `StraddlingEntityRenderer`.

Les 72 passent la vérification actuelle du test : classe finale, un seul constructeur, privé, qui lève
`UnsupportedOperationException`. Leurs initialiseurs statiques s'exécutent sans erreur dans la JVM des tests unitaires.
Ce point a été vérifié par ce seul test, pas dans la suite complète.

Une découverte par le bytecode n'ôte donc aucune garantie et en ajoute 17. Son propre plancher (au moins 72 classes, et
quelques classes connues) empêche qu'elle passe à vide.

### REF 4. Chemins chauds (G5)

Le relevé statique couvre 32 méthodes appelées à chaque tick ou à chaque image, et toutes les méthodes du mod qu'elles
appellent. Il cherche : `new`, tableau, `invokedynamic`, boxing, itérateur, et les appels vanilla connus pour rendre un
objet neuf (arithmétique de `Vec3d`, `Box`, `BlockPos`).

| Classe | Méthodes |
|---|---|
| `BackroomsPortalEntity` | `frameContains`, `withinFrameWidth`, `withinArrivalWidth`, `facesOpening`, `sourceFrameStraddles`, `arrivalFrameStraddles`, `squaredDistanceToFrame`, `signedDistance`, `centeredAt`, `crossingHeight`, `crossingPointFaces`, `crossingPointFacesArrival`, `enteredBy` |
| `PortalCollision` | `tunnel`, `hides`, `hiddenFrom`, `engages`, `walls` |
| `TeleportExecutor` | `crossedPortalPlane`, `crossedThisTick` |
| `ArrivalGuard` | `guards`, `released` |
| `PortalRelevance` | `isRelevant` |
| `ViewCone` | `set`, `unbounded`, `intersects`, `intersectsBeforeFrame` |
| `PortalViewTransform` | `updatePlanes`, `copyRotation`, `portalRotation`, `positionMatrix`, `clipPlane` |

Le relevé ne trouve aucune allocation en régime établi dans ces méthodes, sous la limite donnée en fin de liste. Il faut
pourtant savoir ceci :

- **Mémoïsé** : cinq méthodes n'allouent que si leurs entrées changent : `destinationPos()` (cache vidé par le
  `DataTracker`), `detectionBox()` et `crossingBox()` (refaites quand la position change), `arrivalBox()` et
  `arrivalCrossingBox()`. Une analyse statique ne distingue pas cette branche d'une allocation à chaque appel : G5 doit
  nommer ces méthodes.
- **Sur événement** : `PortalCollision.remember` crée un `Engagement` quand un corps entre dans un cadre.
  `ArrivalGuard.tick` crée un `Guard` à la pose (REVUE 1.1.5). `PortalViewTransform.crossingRoll` crée trois
  quaternions par traversée.
- **Hors du chemin normal** : `PortalFacing.opposite` contient un `new MatchException`. C'est la branche impossible de
  son `switch`, ajoutée par le compilateur.
- **Trouvé en plus, à chaque image** : `PortalViewTransform.aim`, appelé par chaque vue à chaque image
  (`PortalStencilRenderer.aimCamera`), passe par `remoteLook`, puis par `PortalFacing.transformLook`, qui rend un
  `Vec2f` neuf. Cela fait un petit objet par vue et par image, ajouté depuis au tableau de PERF 3.9.
- **Limite** : un appel vanilla qui alloue sans figurer dans la liste du relevé passe inaperçu.

### REF 5. Payloads (G2)

Les douze identifiants : `portal_remote_dimension`, `portal_remote_chunk`, `portal_remote_chunk_unload`,
`portal_remote_block_update`, `portal_remote_entity`, `portal_remote_block_breaking`, `portal_teleport`, `portal_cross`,
`portal_entity_action`, `portal_block_action`, `portal_sight`, `portal_wand_settings` (espace de noms
`gtgs_backrooms`).

Octets de référence, en hexadécimal, pour un exemple fixe de chacun. `RemoteChunkPayload` est à part : il ne porte que
des formats vanilla (`ChunkData`, `LightData`) après sa dimension et ses coordonnées. Notation des exemples :
- UUID A = `7b0e4c1a-5d2f-4e3b-9a8c-1f2e3d4c5b6a` ;
- UUID B = `0f1e2d3c-4b5a-4968-8776-655443322110` ;
- Nether = `minecraft:the_nether` (dimension et type).

- **`RemoteDimensionPayload`** (Nether, type Nether, index 300) :
  ```
  146d696e6563726166743a7468655f6e6574686572146d696e6563726166743a7468655f6e6574686572ac02
  ```
- **`RemoteChunkUnloadPayload`** (index 1, chunk -12, 7) : `01f4ffffff7f`
- **`RemoteBlockUpdatePayload`** (index 2, bloc -3, 11, 44, `oak_slab[type=top]`) : `02ffffff400002c00b9b57`. L'état
  voyage par son numéro brut. L'exemple prend un bloc vanilla, dont le numéro ne dépend pas des blocs du mod.
- **`RemoteEntityPacketPayload`** (index 3, paquet vanilla de vitesse de l'entité 42 : 0,25, -0,5, 1,0) :
  `035a2a07d0f0601f40`
- **`RemoteBlockBreakingPayload`** (Nether, bloc -7, 2, 157, étape -1) :
  ```
  146d696e6563726166743a7468655f6e6574686572fffffe400009d002ff
  ```
- **`PortalTeleportPayload`** (Nether, type Nether, position -7,05, 65, 30,5, lacet -90, tangage 12,5, vitesse 0,1,
  -0,08, 0) :
  ```
  146d696e6563726166743a7468655f6e6574686572146d696e6563726166743a7468655f6e6574686572c01c33333333
  33334050400000000000403e800000000000c2b40000414800003fb999999999999abfb47ae147ae147b000000000000
  0000
  ```
- **`PortalCrossPayload`** (UUID A, position 1,5, 2, -3,25, vitesse 0, 0,42, 0,28, lacet 45, tangage -10) :
  ```
  7b0e4c1a5d2f4e3b9a8c1f2e3d4c5b6a3ff80000000000004000000000000000c00a0000000000000000000000000000
  3fdae147ae147ae13fd1eb851eb851ec42340000c1200000
  ```
- **`PortalEntityActionPayload`** (UUID A, Nether, entité 1234, `ATTACK`) :
  ```
  7b0e4c1a5d2f4e3b9a8c1f2e3d4c5b6a146d696e6563726166743a7468655f6e6574686572d20900
  ```
- **`PortalBlockActionPayload`** (UUID A, Nether, visée du point -7, 2,5, 157,25 sur la face ouest du bloc -7, 2, 157,
  `START_MINING`) :
  ```
  7b0e4c1a5d2f4e3b9a8c1f2e3d4c5b6a146d696e6563726166743a7468655f6e6574686572fffffe400009d002040000
  00003f0000003e8000000000
  ```
- **`PortalSightPayload`** (Nether, UUID A et B) :
  ```
  146d696e6563726166743a7468655f6e6574686572027b0e4c1a5d2f4e3b9a8c1f2e3d4c5b6a0f1e2d3c4b5a49688776
  655443322110
  ```
- **`PortalWandSettingsPayload`** :
  - autre main, réglages 3 × 5, sans lumière, une face, aller simple, sortie durable, sortie par le plafond, rayon 40,
    plan au bord, tête en bas, pose au sol, vers le Nether, sortie fixée dans l'Overworld en 40, -60, 40 face au nord.
    Les réglages voyagent en NBT, et l'ordre de leurs champs suit celui de `NbtCompound` :
    ```
    000a0800046578697400076365696c696e670100076f6e655f77617901010005666c7573680108000b64657374696e61
    74696f6e00146d696e6563726166743a7468655f6e657468657203000c657869745f736361747465720000002801000c
    646f75626c655f736964656400030005776964746800000003080009706c6163656d656e740005666c6f6f7201000f74
    72616e736d6974735f6c696768740001000b7570736964655f646f776e010a00046d61726b0b0003706f730000000300
    000028ffffffc400000028080006666163696e6700056e6f72746808000964696d656e73696f6e00136d696e65637261
    66743a6f766572776f726c64000300066865696768740000000500
    ```
  - main principale, réglages par défaut (aucun champ écrit) : `010a00`.

### REF 6. Sauvegardes (G1)

Quatre sauvegardes écrites par le code de la 0.10.0, en SNBT ordonné. Elles ont été relevées, mais **leur relecture n'a
pas été essayée**. C'est la première vérification de G1.

- **Bloc portail configuré** : format d'un chunk sauvé, `createNbtWithIdentifyingData`, relu par
  `BlockEntity.createFromNbt`. Bloc en 4, 10, -2, face avant à l'est. Chaque réglage est loin de sa valeur par défaut :
  3 × 4, sans lumière, une face, sortie fugace, sortie vers l'est, rayon 12, plan au bord, tête en bas, pose au
  plafond, vers le Nether, sortie fixée dans l'End en 100, 50, -20 face à l'ouest. L'arrivée est dans le Nether en
  12,5, 70, -3,5, face à l'est. Les portails tenus (`EntryFront`, `ReturnFront`) et `ExitLost` ont été ajoutés à la
  main, comme les pose un monde serveur :
  ```
  {
      DestDim: "minecraft:the_nether",
      DestFacing: "EAST",
      DestX: 12.5d,
      DestY: 70.0d,
      DestZ: -3.5d,
      Destination: "minecraft:the_nether",
      DoubleSided: 0b,
      EntryFront: [I; 286331153, 572670771, -2075896491, 1431655765],
      Exit: "east",
      ExitLost: 1b,
      ExitScatter: 12,
      FleetingExit: 1b,
      Flush: 1b,
      Height: 4,
      Mark: {
          dimension: "minecraft:the_end",
          facing: "west",
          pos: [I; 100, 50, -20]
      },
      OneWay: 0b,
      Placement: "ceiling",
      ReturnFront: [I; 1717986918, 2004306056, -1717982550, -1431655766],
      TransmitsLight: 0b,
      UpsideDown: 1b,
      Width: 3,
      id: "gtgs_backrooms:backrooms_portal",
      x: 4,
      y: 10,
      z: -2
  }
  ```
- **Bloc portail neuf** : en 0, 64, 0, réglages par défaut, sans destination :
  ```
  {
      DoubleSided: 1b,
      Exit: "straight",
      ExitScatter: 0,
      FleetingExit: 0b,
      Flush: 0b,
      Height: 2,
      OneWay: 0b,
      Placement: "look",
      TransmitsLight: 1b,
      UpsideDown: 0b,
      Width: 1,
      id: "gtgs_backrooms:backrooms_portal",
      x: 0,
      y: 64,
      z: 0
  }
  ```
- **Baguette de portail** : `ItemStack.encode`, relue par `ItemStack.fromNbt`, avec les mêmes réglages que le bloc
  configuré :
  ```
  {
      components: {
          "gtgs_backrooms:portal_settings": {
              destination: "minecraft:the_nether",
              double_sided: 0b,
              exit: "east",
              exit_scatter: 12,
              fleeting_exit: 1b,
              flush: 1b,
              height: 4,
              mark: {
                  dimension: "minecraft:the_end",
                  facing: "west",
                  pos: [I; 100, 50, -20]
              },
              placement: "ceiling",
              transmits_light: 0b,
              upside_down: 1b,
              width: 3
          }
      },
      count: 1,
      id: "gtgs_backrooms:portal_wand"
  }
  ```
- **Entité portail**, données du mod seulement (`writeCustomDataToNbt`). Le NBT complet d'une entité ne s'écrit pas
  sans monde dans un test unitaire : l'API d'attachements de Fabric, tissée dans `Entity.writeNbt`, lit le monde. Le
  portail est au nord, et il arrive retourné vers l'est dans le Level 0 en 1,5, 1, -1,25. Il mesure 2 × 3. Il est
  fermé, visible, laisse passer la lumière, et sa sortie est fugace. Son bloc propriétaire est dans l'Overworld en 4,
  65, -9 :
  ```
  {
      DestinationDim: "gtgs_backrooms:backrooms_level_0",
      DestinationX: 1.5d,
      DestinationY: 1.0d,
      DestinationZ: -1.25d,
      FacingDestination: "EAST_UPSIDE_DOWN",
      FacingSource: "NORTH",
      Fleeting: 1b,
      Height: 3.0f,
      OwnerDim: "minecraft:overworld",
      OwnerPos: 1374389497921L,
      Teleportable: 0b,
      TransmitsLight: 1b,
      Visible: 1b,
      Width: 2.0f
  }
  ```

### REF 7. Nombre de tests (G8 b)

- **Tests unitaires** : 749 exécutions, comptées en additionnant `tests` moins `skipped` dans les
  `build/test-results/test/TEST-*.xml`. Un cas paramétré compte une fois par jeu de valeurs.
- **Tests en jeu** : 99. `build/junit.xml` emboîte deux `<testsuite>` et ne porte aucun total. Il faut donc compter
  ses `<testcase>`.
- **Contrôle du graphe** (G8 a) : un contrôle par `gradle.taskGraph.whenReady` ne doit agir que si `:build` est dans
  le graphe et qu'aucune tâche n'a été retirée par `-x`. Sinon, un `build -x check` fait à dessein deviendrait
  impossible. Dans la CI, seule l'étape `build` contient `:build`.

---

## EXPLO — Exploration : carte graphique, threads, LOD et visibilité des portails

> **La demande de l'auteur (25 octobre 2026)** :
> - calculer les portails sur la carte graphique ;
> - déporter du calcul dans un autre langage, sur un autre thread ou sur la carte graphique ;
> - un LOD : moins de qualité de loin, 100 % de près, y compris dans un seul très grand portail ;
> - plus de portails noirs vus de loin ;
> - ne plus dessiner un portail qu'on ne regarde pas ou qui est caché par un mur, mais le remettre instantanément dès
>   qu'il revient dans le champ de vision.
>
> **Méthode et limites** :
> - Étude par lecture du code, de la doc et du jar de Minecraft mappé, avec les mesures de la partie PERF.
> - **Rien n'est implémenté, aucune piste n'est prototypée.** Selon la règle d'or 11 de `docs/GUIDE.md`, aucune n'est
>   « faisable » avant son prototype ; chaque gain annoncé ici est une hypothèse, à mesurer par le protocole de la
>   section 7.
> - L'exigence de NR 5 s'applique : ce qui change l'image ou le comportement est un changement voulu, à décider par
>   l'auteur.
> - Le mod de physique cité en exemple par l'auteur n'est pas nommé ici (règle de `docs/prompt/FINALCHECK.md`), et son
>   code n'a pas été lu (règle d'or 3). Seul son principe est étudié : un calcul lourd déporté hors du thread du jeu.

### EXPLO — L'essentiel

1. **Les portails sont déjà dessinés par la carte graphique.**
   - Le GPU dessine tout ce qui se voit d'une vue : terrain, entités, ciel, masque du stencil, découpe au plan
     d'arrivée.
   - Le processeur prépare ce dessin : choix des sections, appels de dessin, géométrie des entités. Sur ce PC, cela fait
     0,15 à 0,35 ms par vue et par image.
   - Sur ce portable, le vrai problème est *quelle* carte graphique : le jeu tourne par défaut sur la puce Intel
     (section 3.2).
2. **Un autre langage n'apporterait rien aujourd'hui.**
   - Le calcul propre aux portails pèse quelques microsecondes. Ce qui coûte, c'est le rendu vanilla de l'autre monde,
     qu'aucun code natif ne remplace.
   - Java 21 n'offre ni l'appel natif moderne (finalisé en Java 22) ni l'API vectorielle, encore en incubation.
   - Le principe du mod de physique convient à des milliers de corps calculés à chaque tick, pas à ce profil (section 4).
3. **D'autres threads, oui, mais surtout côté serveur** : la recherche d'arrivée (gel de 0,7 à 2,9 s) et la mise en
   paquets des chunks envoyés aux vues. Côté client, le parcours des sections d'une vue doit rester dans l'image :
   asynchrone, il ferait clignoter la vue (piège déjà documenté dans `CLAUDE.md`).
4. **Un portail s'affiche mal dans huit cas**, et la distance elle-même n'en cause presque aucun (section 2). Six
   donnent du noir, la couleur de brouillard du Level 0 tant que rien n'est dessiné :
   - données pas encore reçues : le serveur envoie 8 chunks toutes les 250 ms ;
   - données retirées après 5 s de regard ailleurs ;
   - fenêtre de 7 × 7 chunks plus petite que ce que montre le cadre ;
   - première vue d'une dimension ;
   - portail imbriqué sans données ;
   - rendu distant coupé pour la session.

   Les deux autres rendent le portail transparent : au-delà de 64 blocs, il disparaît ; au-delà d'un niveau
   d'imbrication aussi.
5. **Ne pas dessiner un portail caché, et le remettre aussitôt** (section 6) :
   - **de dos et hors du champ** : déjà fait, à chaque image, donc déjà instantané ;
   - **derrière un mur**, deux moyens exacts, sans aucune image de retard :
     - une preuve sur le processeur : une tranche pleine de cubes opaques entre la caméra et le cadre. Elle évite tout
       le travail de la vue ;
     - le rendu conditionnel d'OpenGL, pour les cas que la preuve ne tranche pas : la carte graphique saute d'elle-même
       une vue dont la marque n'a couvert aucun pixel.

     Une requête d'occlusion lue à l'image suivante, elle, est refusée : elle aurait une image de retard ;
   - **la vraie cause d'un retour lent est côté serveur** : au-delà de 16 blocs, la fenêtre de données est retirée
     après 5 s sans regard, puis renvoyée.
6. **Le LOD est possible**, par paliers choisis sur la taille du cadre à l'écran plus que sur la distance (section 5).
   - Le palier « 100 % » est strictement le rendu d'aujourd'hui, pour tout portail proche ou traversable.
   - Chaque palier inférieur change l'image : changement voulu, réglé par l'auteur.
   - Pour un seul très grand portail, le levier n'est pas le LOD. C'est l'arrêt du double dessin derrière le cadre (une
     pré-passe de profondeur), qui vise une image identique.
7. **Le protocole de test** (section 7) compte treize campagnes, de la distance au grand cadre, avec des mesures de
   performance et de qualité et des critères chiffrés.

---

### EXPLO 1. Ce que fait le mod aujourd'hui (lu dans le code)

| Étape | Où | Ce qui se passe | Qui travaille |
|---|---|---|---|
| Choix des portails à dessiner | `PortalStencilRenderer.collectVisiblePortals` | Portail visible, vu de face, à moins de 64 blocs (`renderDistanceBlocks`). Au-delà de 16 blocs, seulement s'il est devant le regard (`PortalRelevance`). Dans le frustum (boîte du cadre). Trié du plus proche au plus lointain, 32 au plus | processeur, à chaque image |
| Marque du cadre | `markArea`, `FrameAreaRenderer` | La zone du cadre passe au niveau suivant du stencil, là où elle passe le test de profondeur contre le monde du joueur | GPU |
| Fond de la vue | `renderView` | La zone prend la couleur de brouillard de la vue : là où rien ne se dessine, on voit cette couleur, noire dans le Level 0 | GPU |
| Vue | `RemoteViewRenderer`, `ViewSectionWalker` | Sections choisies dans le volume visible par le cadre, dessinées par vanilla depuis la caméra transposée, coupées au plan d'arrivée dans les shaders | processeur pour la préparation, GPU pour le dessin |
| Imbrication | `renderNestedPortals` | Un niveau. Un portail imbriqué dont le chunk d'arrivée manque est un cadre noir ; au-delà d'un niveau, il est transparent | idem |
| Pack de shaders | `ShaderPackPortalRenderer` | Chaque vue est un rendu complet du pack, après l'image | idem |
| Données | serveur, `PortalViewTracker` | Recalcul toutes les 5 ticks. Fenêtre de 7 × 7 chunks autour de l'arrivée, 11 × 11 à moins de 8 blocs. Au plus 8 chunks complets par joueur et par passe, du centre vers l'extérieur. Fenêtre retirée 100 ticks (5 s) après le dernier regard | serveur |
| Repli | `BackroomsPortalEntityRenderer` | Si le rendu distant est coupé (stencil refusé, exception), chaque portail devient un cadre noir pour la session | processeur et GPU |

Ce que cela coûte sur ce PC (PERF 2) :
- 0,15 à 0,35 ms de thread de rendu par vue 1 × 2 ;
- trois portails : −37 % (RTX) à −43 % (puce Intel) d'images par seconde ;
- un cadre 8 × 8 : −76 % sur la puce Intel, −30 % sur la RTX ;
- environ 70 Ko alloués par vue et par image ;
- 112 ms pour créer le monde distant à sa première vue.

Le mod contient déjà un test de visibilité sur le processeur : `PortalSightReporter.inSight`. Il lance des rayons de
la caméra vers une grille de points du cadre, à travers les cubes opaques. Il ne sert qu'aux sorties fugaces, une fois
par tick.

---

### EXPLO 2. Les portails noirs : causes et remèdes

| # | Quand | Ce qu'on voit | Cause dans le code | Remèdes possibles | Ce que le remède change |
|---|---|---|---|---|---|
| 1 | Un portail entre en portée (64 blocs, ou devant soi au-delà de 16) | La zone prend la couleur de brouillard (noire vers le Level 0, celle du ciel vers l'Overworld), puis la vue se remplit du centre vers les bords | Le serveur recalcule les fenêtres toutes les 5 ticks et envoie au plus 8 chunks par passe : 49 chunks en environ 1,5 s, 121 en environ 3,8 s. S'y ajoute la construction des sections chez le client | (a) Synchroniser avant la distance de rendu (anneau d'anticipation, par exemple 64 + 32 blocs). (b) Envoyer le chunk central et ses voisins dès le tick où le portail devient pertinent. (c) Envoyer d'abord les chunks que le cadre montre | Moins de noir. Plus de réseau et de mémoire pour des portails qu'on ne regardera peut-être pas |
| 2 | Le regard revient après plus de 5 s ailleurs, au-delà de 16 blocs | Comme en 1 | `UNLOAD_GRACE_TICKS = 100` : la fenêtre est retirée chez le client, puis renvoyée | Garder la fenêtre de tout portail à portée de rendu, quel que soit le regard, ou un délai bien plus long | Retour instantané. Coûte le réseau des blocs et des entités de fenêtres non regardées, et quelques Mo de mémoire par portail (49 chunks) |
| 3 | Ce que le cadre montre au-delà de la fenêtre | La couleur de brouillard au-delà de 3 chunks de l'arrivée (5 de près) | La fenêtre est un carré autour de l'arrivée, alors que de loin le cadre montre un cône étroit et profond | Une fenêtre en cône : les chunks que le cadre montre depuis le joueur, plus loin dans l'axe et moins sur les côtés, pour le même nombre de chunks | L'autre côté se voit plus loin ; il faut recalculer quand le joueur bouge |
| 4 | Première vue d'une dimension | Une image vide (noire dans le Level 0) et un à-coup de 112 ms | Le `WorldRenderer` se crée dans l'image | Le créer à l'avance, dès le rayon d'activation (NR 5.3, point 5) | Plus de noir ; un peu de mémoire |
| 5 | Portail vu dans une vue | Un cadre noir | Le chunk de son arrivée manque : sa fenêtre n'est pas synchronisée | Synchroniser aussi l'arrivée des portails visibles dans une vue proche | Plus de réseau |
| 6 | Imbrication au-delà d'un niveau | Le cadre est transparent : on voit le décor derrière | Un seul niveau de stencil | Un niveau de plus (coût multiplié), ou un imposteur (section 5) | Coût, ou image figée |
| 7 | Au-delà de 64 blocs | Le portail disparaît (rien n'est dessiné, on voit le décor derrière le cadre), puis la vue apparaît d'un coup à 64 blocs | `renderDistanceBlocks = 64`, alors que le terrain du joueur va jusqu'à 192 blocs (distance 12) | Un palier de LOD lointain (section 5), ou une distance de rendu plus grande | Plus de coût, ou une image réduite |
| 8 | Rendu distant coupé | Tous les portails en cadres noirs pour la session | Coupe-circuit sur exception ou stencil refusé (`PortalRenderState`) | Lire la cause dans le journal. Le banc doit vérifier que chaque carte accepte le stencil | — |

À noter :
- **En attendant les données**, la couleur de brouillard est la réponse de vanilla à un chunk pas encore chargé. Dans
  le Level 0, sans ciel, elle est noire.
- **Il y a deux autres choses à mettre à la place**, et elles changent l'image (NR 5.4) :
  - la dernière image connue de la vue, ce qui exige un tampon hors écran : c'est un choix écarté par `docs/GUIDE.md`
    3.5, à rouvrir par l'auteur ;
  - le ciel seul, sans terrain.
- **Le brouillard n'est pas en cause aux réglages du banc.** La vue pose le brouillard du joueur (distance de rendu,
  192 blocs à la distance 12) depuis sa caméra transposée, aussi loin de l'arrivée que le joueur l'est du portail. À
  une petite distance de rendu (2 à 6 chunks), il peut couvrir tout ce que montre un cadre lointain : campagne M.

---

### EXPLO 3. Calcul sur la carte graphique

#### EXPLO 3.1 Ce qui y est déjà

Tout le dessin des vues :
- la rastérisation du terrain, des entités, du ciel, des nuages et de la météo ;
- les tests de profondeur et de stencil ;
- la découpe au plan d'arrivée par `gl_ClipDistance` (injectée dans les shaders vanilla par `ShaderClipInjector`) ;
- sous un pack, tout le pipeline du pack.

Le processeur garde trois choses :
- la préparation des appels de dessin, section par section et couche par couche ;
- la géométrie des entités, que vanilla écrit sur le processeur à chaque image ;
- la couleur du brouillard et celle du ciel, 216 points de biome pour chacune, par vue et par image.

Il ne reste donc pas de « calcul de portail » lourd à déplacer vers le GPU. Ce qui peut y passer, ce sont des décisions :
quelle vue dessiner, et quels pixels ne pas dessiner deux fois (3.3).

#### EXPLO 3.2 Quelle carte graphique

Le mod ne peut pas changer la carte graphique d'un processus déjà lancé : Windows (Optimus) la choisit au démarrage
de `java.exe`.

| Option | Effet | Limites |
|---|---|---|
| Doc (`docs/SETUP.md`, `docs/TROUBLESHOOTING.md`) : réglage « Hautes performances » de Windows pour le `java.exe` du jeu | ×2,3 à ×3 d'images par seconde sur ce PC, et un serveur intégré deux à trois fois plus rapide (mesuré) | Aucune ; le joueur doit lire la doc |
| Message en jeu quand le rendu tourne sur une puce intégrée (lue dans `GL_RENDERER`) | Le joueur l'apprend sans chercher | Heuristique sur le nom de la puce. Message traduit (`en_us`, `fr_fr`). Changement visible voulu |
| Écrire la préférence de Windows pour le `java.exe` en cours (`HKCU\Software\Microsoft\DirectX\UserGpuPreferences`, valeur `GpuPreference=2;`), avec l'accord explicite du joueur | Effective au lancement suivant | Windows seulement. Touche au système, pour tout ce qui utilise ce Java. Décision de l'auteur |

**Qualité** : le rendu est le même sur les deux cartes, avec les mêmes réglages. Les pilotes peuvent différer de
quelques niveaux de couleur ; ce n'est pas mesuré (campagne L).

#### EXPLO 3.3 Pistes pour la carte graphique

Elles sont numérotées GPU1 à GPU7, pour ne pas les confondre avec les garde-fous G1 à G12 (NR 4.2).

Minecraft 1.21.1 crée un contexte OpenGL 3.2 core, et c'est la version que rapporte la puce Intel de ce PC (PERF 1). Une
fonction d'OpenGL 3.3 ou plus récente n'y est donc disponible que par son extension, à vérifier sur chaque carte.

| # | Piste | Principe | Gain attendu sur ce PC | Image | Compatibilité | Verdict |
|---|---|---|---|---|---|---|
| GPU1 | Rendu conditionnel des vues cachées | Une requête d'occlusion posée sur la marque du cadre, qui compte les pixels marqués : `GL_SAMPLES_PASSED` (OpenGL 1.5, donc disponible), ou `GL_ANY_SAMPLES_PASSED` par l'extension `ARB_occlusion_query2`. Puis `glBeginConditionalRender` (OpenGL 3.0) autour de tout le dessin de la vue : le GPU saute la vue si aucun pixel n'a été marqué, dans la même image, sans que le processeur attende | Le travail GPU des vues cachées (sommets, fragments). Rien côté processeur | Identique au pixel près : la vue n'aurait rien dessiné, le stencil l'en empêchant | Minecraft exige OpenGL 3.2 ; macOS va jusqu'à 4.1. Vanilla et mod de terrain : leurs appels de dessin sont conditionnés aussi, à vérifier. Sous un pack : à vérifier (effacements, copies, passe d'ombre du pack) | **À prototyper en premier** |
| GPU2 | Pré-passe de profondeur du cadre | Avant le terrain du joueur, écrire la profondeur du plan du cadre (même géométrie que sa marque). Ce qui est derrière échoue au test de profondeur et n'est plus dessiné pour rien. La marque et la vue ne changent pas | Pour un grand cadre sur la puce Intel, une part des 76 % perdus : c'est la bande passante qui limite | Vise l'identité. Exceptions possibles : ce qui se dessine sans test de profondeur derrière le cadre (contour des entités en surbrillance, à vérifier) | Injection dans `WorldRenderer.render` avant le terrain, jamais dans `renderLayer` (réécrit par les mods de terrain). Chemin vanilla et mod de terrain ; sous un pack, la vue se dessine après l'image | **À prototyper** |
| GPU3 | Mesure du temps GPU | `glQueryCounter` par vue et par passe, lu une image plus tard. OpenGL 3.3 : sur le contexte 3.2 de Minecraft, par l'extension `ARB_timer_query`, à vérifier sur chaque carte | Aucun : c'est l'outil de mesure qui manque (`docs/GUIDE.md` 4.7) | Aucune | Partout | **Préalable aux tests** |
| GPU4 | Vue en résolution réduite | Dessiner une vue lointaine dans une texture plus petite, puis la composer sous le stencil | Le travail GPU des vues lointaines | Changement voulu : vue plus floue | Contredit le choix écarté de GUIDE 3.5 (un framebuffer par vue), à rouvrir par l'auteur | Recherche |
| GPU5 | Image de vue réutilisée (imposteur) | Garder l'image d'une vue lointaine et la recomposer quelques images, ou la montrer au-delà de la distance de rendu | Presque tout le coût d'une vue lointaine | Changement voulu : image figée, parallaxe fausse en mouvement | Comme GPU4 | Recherche |
| GPU6 | Culling et dessin pilotés par le GPU | Un compute shader choisit les sections et écrit des commandes de dessin indirectes | Les appels de dessin | Aucune si c'est juste | OpenGL 4.3 : absent de macOS. Cela réécrit le rendu du terrain vanilla. Un mod de terrain remplace déjà ce rendu par le sien, et les vues s'en servent quand il est installé (`foreignTerrain`) | Écarté |
| GPU7 | Calcul GPU pour la logique des portails | Collisions, traversées, pertinence en compute shader | Rien : ces calculs pèsent quelques microsecondes, et l'aller-retour GPU vers processeur coûte plus | — | OpenGL 4.3 | Écarté |

---

### EXPLO 4. Autre langage, autre thread

#### EXPLO 4.1 Ce qui pèse vraiment

| Calcul | Coût mesuré ou estimé | Thread aujourd'hui |
|---|---|---|
| Recherche d'arrivée à la pose (chunks générés sur place) | Gel de 0,7 à 2,9 s | serveur |
| Mise en paquets des chunks des fenêtres (`portal.sync.chunks`) | Jusqu'à 22 ms par tick pendant une synchronisation | serveur |
| Collisions, traversée, garde, pertinence | Quelques microsecondes par entité | serveur et client |
| Préparation d'une vue (sections, appels de dessin, entités) | 0,15 à 0,35 ms par vue et par image | rendu |
| Brouillard et ciel d'une vue | Une part de la ligne précédente, et 70 % des allocations qui passent par le mod | rendu |
| Construction des maillages des sections | En arrière-plan | threads de travail de vanilla (`ChunkBuilder`, un par renderer de vue) |
| Création d'un monde distant | 112 ms en une image (puce Intel) | rendu |

#### EXPLO 4.2 Autres threads

| Piste | Résultat identique ? | Risque | Verdict |
|---|---|---|---|
| Serveur : recherche d'arrivée hors du thread (PERF 3.2) | Oui, sauf le moment d'apparition | Le cadre devient portail plus tard (NR 5.4) | Décision de l'auteur |
| Serveur : mise en paquets des chunks sur un thread de travail. Copie des sections sous le verrou du serveur, sérialisation à part, envoi dans l'ordre | Oui, si l'ordre est gardé | Un changement de bloc relayé avant son chunk serait perdu, et deux threads lisent le monde | À prototyper, avec un test de l'ordre |
| Client : application des chunks reçus hors du thread de rendu | Non garanti | Rompt le chemin `readLightData` dont dépendent les mods de terrain (CLAUDE.md) | Écarté |
| Client : parcours des sections d'une vue en arrière-plan | Non | Une image de retard, d'où des vues qui clignotent : c'est exactement le culling asynchrone de vanilla, écarté pour cette raison (CLAUDE.md) | Écarté |
| Client : brouillard et ciel calculés en arrière-plan | Non | Une image de retard | Écarté ; seule la réutilisation à entrées identiques reste (NR 5.3) |
| Client : préparation du monde distant hors de l'image | En partie | Les objets GL se créent sur le thread de rendu ; seuls les objets Java peuvent partir | Remplacé par la création anticipée |

#### EXPLO 4.3 Un autre langage

| Langage ou API | Possible sur Minecraft 1.21.1 ? | Ce qu'il apporterait ici |
|---|---|---|
| C, C++ ou Rust par JNI | Oui, avec une bibliothèque native par plateforme (Windows, Linux, macOS ; x64 et arm64) chargée au lancement. `verifyJarContents` la refuserait aujourd'hui | Rien : aucun calcul lourd du mod à accélérer. Chaque appel coûte des dizaines de nanosecondes plus la copie des données. Un plantage natif fait tomber le jeu sans exception Java, et des antivirus se méfient des bibliothèques natives |
| API d'appel natif de Java (FFM) | Non : finalisée en Java 22. Minecraft 1.21.1 tourne sous Java 21, où elle exige `--enable-preview`, que les launchers ne passent pas | Possible seulement après un portage vers une version sous Java 25 (26.x) ; le verdict ne change pas pour autant |
| API vectorielle de Java (SIMD) | Non : en incubation, elle exige `--add-modules` | — |
| GLSL (shaders) | Oui, déjà utilisé : le plan de découpe est injecté dans les shaders vanilla | C'est le seul « autre langage » utile ici. GPU1 et GPU2 n'en demandent pas de nouveau : ils passent par l'API d'OpenGL et les shaders existants |
| Kotlin, Scala | Oui | Même JVM, aucun gain |

**Le principe du mod de physique cité** sert quand trois conditions se réunissent :
1. un calcul lourd et continu, indépendant du rendu vanilla : des milliers de corps et de contacts par tick ;
2. des données qui restent du côté natif ou sur le GPU ;
3. peu d'allers-retours avec le jeu.

Le calcul des portails ne remplit aucune des trois. Si un jour un niveau a besoin d'une simulation lourde (un fluide,
des milliers d'objets), le principe redevient pertinent pour ce système-là.

---

### EXPLO 5. LOD des portails

#### EXPLO 5.1 Choisir le palier

- **La distance seule trompe.** Un cadre 8 × 8 à 40 blocs couvre plus de pixels qu'un 1 × 2 à 10 blocs. Le palier se
  choisit sur l'empreinte du cadre à l'écran (le rectangle que `screenRect` calcule déjà pour le ciseau) et sur la
  distance.
- **Palier 100 %** : tout portail à moins de 16 blocs (rayon d'activation : on peut le traverser sous peu), ou dont le
  rectangle couvre une part de la hauteur de l'écran à fixer. C'est strictement le rendu d'aujourd'hui, au pixel près.
- **Hystérésis** : on descend d'un palier à un seuil plus bas que celui où l'on remonte (par exemple 20 % d'écart), et
  jamais deux changements en peu d'images. Sinon le palier oscille et l'image saute.
- **Zéro couture** (règle d'or 5) : le cadre qu'on traverse est toujours à 100 %, puisqu'on le traverse à moins de
  16 blocs. Aucun changement de palier ne coïncide avec une traversée.

#### EXPLO 5.2 Leviers, du moins visible au plus visible

| Levier | Gain attendu | Ce qui se voit | Remarques |
|---|---|---|---|
| Pas de portail imbriqué dans une vue lointaine | Fort sur la scène à trois portails (2,9 à 4,1 vues par image, ramenées vers 3) | Les cadres imbriqués de cette vue deviennent transparents, comme au-delà d'un niveau aujourd'hui | — |
| Ni particules, ni météo, ni nuages dans une vue lointaine | Processeur | Ces effets disparaissent de la vue | — |
| Entités limitées dans une vue lointaine (proches de l'arrivée, ou aucune) | Processeur : vanilla écrit la géométrie des entités sur le processeur à chaque image | Des entités disparaissent de la vue | Les moitiés d'entités à cheval sur le cadre se gardent : elles touchent le monde du joueur |
| Distance de rendu de la vue réduite (sections à moins de R chunks de l'arrivée) | Processeur et GPU | Le brouillard arrive plus tôt dans la vue | C'est le LOD de vanilla lui-même (la distance de rendu) |
| Fenêtre réseau réduite pour un portail lointain (3 × 3 chunks) | Réseau, et fenêtre complète plus tôt (9 chunks en deux passes, au lieu de sept passes pour 49) | Moins de profondeur visible dans le cadre | Côté serveur |
| Résolution réduite (GPU4) | GPU | Vue plus floue | Tampon hors écran |
| Une image sur deux (GPU5) | Processeur et GPU divisés par deux | La vue saccade, et sa parallaxe est fausse en mouvement | Tampon hors écran |
| Imposteur au-delà de 64 blocs (GPU5) | Montre quelque chose là où rien n'apparaît aujourd'hui | Image figée | Tampon hors écran. Variante sans tampon : ciel et brouillard seuls |

#### EXPLO 5.3 Un seul très grand portail

- **Le LOD n'y gagne rien quand on est près.** Choisi sur l'empreinte à l'écran, il reste à 100 % devant un grand
  cadre. Pour un grand cadre, le coût vient de trois choses :
  1. le double dessin : le monde du joueur derrière le cadre, puis la vue par-dessus. Le levier est GPU2 ;
  2. la largeur du cône, donc plus de sections. Le levier est la distance de rendu de la vue ;
  3. une fenêtre de données assez large pour ce que montre le cadre.
- **Au-delà de 8 × 8** (`PortalSettings.MAX_SIZE`), c'est un changement de fonctionnalité, qui touche :
  - la fenêtre synchronisée ;
  - la recherche d'arrivée (le cadre doit y tenir) ;
  - les collisions ;
  - la lumière transmise, qui dépend de chaque bloc du cadre ;
  - les étages du Level 0, hauts de quatre blocs (PERF 3.12).

  Décision de l'auteur.
- **Un LOD par distance à l'intérieur d'une même vue** (sections lointaines simplifiées) exigerait des maillages
  simplifiés. Vanilla n'en a pas : ce serait un moteur de terrain lointain, en conflit avec les mods de terrain. Sa
  version réaliste est la distance de rendu de la vue, avec son brouillard.

#### EXPLO 5.4 Ce que le LOD ne doit jamais toucher

- **La traversée, les collisions et la prédiction** : la prédiction exige le monde distant chargé, et un LOD n'en
  retire aucune donnée à moins de 16 blocs.
- **Les sorties fugaces** : le serveur garde une sortie fugace tant que le client la rapporte vue. Le rapport part des
  portails dessinés, filtrés par `inSight` (`PortalSightReporter`). Un palier qui ne dessine plus un cadre doit
  continuer de le rapporter comme dessiné, sans quoi une sortie fugace lointaine disparaîtrait.
- **Le son à travers les cadres** : il dépend de `visible()`, pas du dessin, donc aucun effet. La lumière transmise est
  côté serveur, aucun effet non plus.
- **Les deux chemins de rendu** : un palier doit exister sur le chemin vanilla et sous un pack, ou se couper sous un
  pack.

---

### EXPLO 6. Ne pas dessiner ce qu'on ne voit pas, et le remettre aussitôt

#### EXPLO 6.1 Aujourd'hui

| Cas | Comportement | Retour dans le champ |
|---|---|---|
| Portail de dos | Pas dessiné (`signedDistance <= 0`) | Instantané : testé à chaque image |
| Hors du frustum | Pas dessiné (boîte du cadre contre le frustum, à chaque image) | Instantané, tant que les données sont là. Au-delà de 16 blocs, le serveur les retire 5 s après le dernier regard : retour lent (section 2, cas 2) |
| Derrière un mur, dans le champ | **Dessiné entièrement**, processeur et GPU ; le stencil n'en laisse rien voir | — |
| Au-delà de 64 blocs | Pas dessiné, pas synchronisé | Apparition d'un coup à 64 blocs |

#### EXPLO 6.2 Le portail derrière un mur

| Méthode | Exacte ? | Retard | Ce qu'elle économise | Verdict |
|---|---|---|---|---|
| **Preuve par tranche pleine** (processeur) : chercher, entre la caméra et le cadre, une tranche du volume qui les relie dont chaque bloc touché est un cube opaque. Si elle existe, aucun rayon ne passe, et le cadre est caché | Oui, et prudente : elle ne déclare caché que ce qui l'est. Un cas qu'elle ne sait pas trancher (escaliers, dalles, entités) est dessiné comme aujourd'hui | Aucun : refaite à chaque image | Tout le travail de la vue, processeur et GPU | **À prototyper.** Trois précautions : un bloc occultant doit être déjà construit à l'écran (`WorldRenderer.isRenderingReady`), sinon on verrait à travers un chunk tout juste chargé ; un chunk absent compte comme vide ; une tranche ne passe jamais par le bloc de la caméra (en spectateur, on voit à travers le bloc où l'on est) |
| **Rendu conditionnel** (GPU1) | Oui, au pixel près | Aucun | Le GPU seulement | **À prototyper**, en complément de la preuve |
| Requête d'occlusion lue à l'image suivante | Non | Une image : un portail qui sort de derrière un coin montrerait une image de couleur de brouillard | Processeur et GPU | **Refusée** : le retour ne serait plus instantané. Acceptable seulement en option |
| Rayons vers une grille de points du cadre (comme `inSight`) | Non : un interstice entre deux points peut faire déclarer caché un cadre visible | Aucun | Processeur et GPU | **Refusée** pour le rendu. Elle reste juste pour les sorties fugaces, où une erreur ne coûte qu'un tick |
| Ensemble des sections visibles de vanilla | Non : calculé en arrière-plan, il peut avoir une image de retard | Une image possible | Processeur et GPU | Recherche |

**Ce qu'il faut garder quand une vue n'est pas dessinée** (preuve par tranche), pour que le retour soit identique :
- la vie du monde distant : particules et effets d'ambiance tournent 2 s après la dernière vue
  (`VIEW_RECENT_TICKS = 40`) ;
- le graphe de culling gardé chaud en vue d'une traversée (`placeTerrain` de la première vue de chaque renderer) ;
- les sections à reconstruire après un changement de bloc : sinon, au retour, quelques images montreraient des blocs
  périmés ;
- le rapport des portails dessinés (`PortalSightReporter.noteDrawn`).

Le rendu conditionnel garde tout cela d'office, puisque le processeur fait tout son travail. La preuve par tranche doit
le faire explicitement, pour tout portail à portée d'activation.

#### EXPLO 6.3 Ce que veut dire « instantané »

**Définition proposée** : dès la première image où le cadre entre dans le champ (ou sort de derrière le mur), sa zone
est identique, à la tolérance du banc (NR 5.2), à ce qu'elle serait si le portail n'avait jamais quitté le champ.

Il faut pour cela :
1. les données présentes chez le client : garder les fenêtres à portée de rendu, quel que soit le regard (section 2,
   cas 2) ;
2. les sections construites (6.2) ;
3. une décision prise dans l'image même : frustum, preuve par tranche, rendu conditionnel.

Aujourd'hui, les points 2 et 3 tiennent pour le frustum ; le point 1 tombe au-delà de 16 blocs et de 5 s.

---

### EXPLO 7. Protocole de test : performance et qualité

#### EXPLO 7.1 Principes

- **Une référence avant chaque prototype**, sur la même machine et dans la même heure (NR 4.1). Trois passages au moins.
  Aucune conclusion en dessous de 10 % d'écart : le bruit de ce portable va jusqu'à ±25 %.
- **Passages de performance et passages de qualité séparés** : capturer des images coûte du temps et fausserait la
  performance.
- **Cartes et résolutions** : puce Intel et RTX ; 854 × 480 (banc actuel) et 1920 × 1080 ; distance de rendu 6, 12 et
  16.
- **Chemins de rendu** : vanilla sur ce PC. Mod de terrain et pack de shaders (deux packs) sur le banc de l'auteur
  (garde-fou G11, NR 4.2).
- **Scènes pilotées par commandes**, comme le banc visuel actuel. Les mouvements réguliers de caméra passent par des
  blocs de commande à répétition, une téléportation par tick : rotation de 5° par tick, avance de 0,2 (marche), 0,28
  (course) ou 0,55 (vol) bloc par tick. Aucun code du mod n'est nécessaire pour cela.
- **Mesures GPU** : elles exigent GPU3, qui n'existe pas encore.

#### EXPLO 7.2 Mesures

| Mesure | Outil | Existe ? |
|---|---|---|
| Images par seconde, 1 % le plus lent, temps d'image (médiane, 95e et 99e centiles, maximum) | Moniteur `[perf]`, après la correction de PERF 3.8 | Oui |
| Temps processeur par vue et par passe | Sections `client.portal.*` du moniteur | Oui |
| Temps GPU par vue et par passe | Requêtes de temps (GPU3) | **À écrire** |
| Tick serveur (moyenne, pire) | Moniteur, côté serveur | Oui |
| Octets et chunks par portail ; temps jusqu'à une fenêtre complète | Moniteur (`reseau=`) et journal | Octets oui ; temps **à écrire** |
| Mémoire privée, tas, mémoire graphique, objets GL | Compteurs de Windows, `jcmd`, moniteur | Oui |
| Allocations par image | JFR | Oui |
| Charge du GPU | Compteurs de Windows « GPU Engine » | Oui |
| Écart au pixel avec la référence à 100 % (écart maximal par canal, pixels à plus de 10 niveaux) | Comparateur du banc visuel | Oui |
| **Part de noir** dans la zone du cadre : pixels égaux à la couleur de brouillard, ou sous un seuil de luminance, sur une scène dont l'état stable n'est pas noir | Analyse des captures, la zone étant connue par la scène | **À écrire** |
| **Images jusqu'à complétude** : entre l'entrée dans le champ et la première image identique à l'état stable | Capture image par image (enregistrement vidéo de la fenêtre, ou vidage d'images par une option de debug) | **À écrire** |
| Scintillement : écart entre deux images successives, caméra immobile | Capture image par image | **À écrire** |
| Saut de palier : écart maximal entre deux images successives pendant une approche lente | Capture image par image | **À écrire** |
| Couture : bord du cadre, images juste avant et juste après une traversée | Captures | Oui |

#### EXPLO 7.3 Campagnes

| # | Scène | Variantes | Ce qu'on mesure | Ce qu'on attend |
|---|---|---|---|---|
| A | Distance | Cadre 1 × 2 vers le Level 0, le Nether, l'Overworld ; joueur face au cadre à 8, 16, 24, 32, 48, 64, 65, 96, 128 et 192 blocs | Temps par vue, part de noir, écart à la référence 100 % | À 16 blocs ou moins : identique. Une fois la fenêtre complète : aucun noir là où l'état stable n'en a pas |
| B | Approche | Marche, course, vol, de 200 blocs à 2 blocs | Première image utile, images jusqu'à complétude, sauts de palier, réseau | Pas de saut de palier au-delà du seuil fixé ; complétude dans le délai fixé par l'auteur |
| C | Demi-tour | Regard détourné 1, 4, 6, 10, 30 et 60 s, à 10, 20, 40 et 60 blocs, puis retour en 4 ticks | Images incomplètes au retour | Instantané : 0 image incomplète, à toute durée. Aujourd'hui, on attend des images incomplètes au-delà de 5 s et de 16 blocs |
| D | Mur | Portail derrière un mur plein, un mur de verre, des feuilles, une porte qui s'ouvre, un coin longé en courant | Temps processeur et GPU d'une vue cachée, avant et après GPU1 et la preuve par tranche ; image ; images de retard à l'apparition | Image identique au pixel près ; 0 image de retard ; vue cachée presque gratuite |
| E | Plusieurs portails | 1, 3, 8, 16, 32 et 33 portails visibles ; autant cachés derrière un mur | Images par seconde, temps par vue | Le plafond de 32 tient ; les vues cachées ne coûtent plus |
| F | Grand cadre | 1 × 2, 2 × 3, 4 × 4 et 8 × 8, à 2, 4, 8, 16 et 32 blocs, jusqu'au plein écran ; puce Intel et RTX | Images par seconde, temps GPU, avec et sans GPU2 | Image identique (V5 du banc) ; gain mesuré, sinon GPU2 est abandonnée |
| G | Imbrication | Portail vu par un portail ; arrivée imbriquée avec et sans données ; trois portails (V6) | Part de noir, vues par image | Plus de cadre noir faute de données, si la piste 5 du tableau de la section 2 est retenue |
| H | Première vue d'une dimension | Level 0, Nether, End | À-coup, images vides | Plus d'image vide, avec la création anticipée |
| I | Traversée | Marche, course, vol ; cadres debout et couchés ; LOD actif | Images autour de la bascule | Zéro couture : aucun changement de palier à moins de 16 blocs, image de la bascule identique à la référence |
| J | Mémoire et durée | 30 min, 4 dimensions, allers-retours | Mémoire privée, tas, mémoire graphique, objets GL | Aucune hausse par rapport à la référence, au-delà de ce que coûtent les fenêtres gardées (chiffré) |
| K | Réseau et serveur | Apparition, retour du regard, traversée ; pose vers une zone neuve | Octets ; tick serveur à la pose et pendant la synchronisation | Coût des fenêtres gardées et de l'anticipation chiffré ; plus de gel si l'arrivée asynchrone est retenue |
| L | Carte graphique | Tout le banc visuel sur la puce Intel, puis sur la RTX | Écart entre les deux cartes | Répond à la question de la qualité selon la carte |
| M | Petite distance de rendu | Distance 2, 4 et 6 chunks ; portail à 32 et 60 blocs | Part de noir due au brouillard | Mesure le cas où le brouillard de la vue couvre ce que montre un cadre lointain |

#### EXPLO 7.4 Critères d'acceptation

| Fonction | Critère |
|---|---|
| Palier 100 % du LOD | Identique au banc (NR 5.2), à toute distance sous le seuil |
| Rendu conditionnel (GPU1) | Identique au pixel près sur la campagne D et les scènes V0 à V7. Temps GPU d'une vue cachée sous 10 % de celui d'une vue visible |
| Preuve par tranche | Identique au pixel près. Coût par portail et par image sous un seuil à fixer (de l'ordre de la dizaine de microsecondes). Sorties fugaces inchangées (tests en jeu existants) |
| Pré-passe de profondeur (GPU2) | Identique sur V5 et la campagne F. Gain d'au moins 10 % sur la puce Intel pour un cadre 8 × 8, sinon abandon |
| Retour instantané | 0 image incomplète (campagne C) ; coût en réseau et en mémoire des fenêtres gardées chiffré |
| Fin du noir | Aucun pixel de couleur de brouillard dans la zone une fois la fenêtre complète (A, B). Première image utile en moins d'un délai à fixer par l'auteur |
| Paliers inférieurs du LOD | Écart permis fixé par l'auteur, palier par palier. Saut de palier sous un seuil. Aucune différence de jeu : sorties fugaces, sons, traversée |
| Toute piste de performance | Gain d'au moins 10 % sur la scène visée ; aucun recul ailleurs |

#### EXPLO 7.5 Ce qu'il faut avant de lancer ces tests

- **Des outils à écrire, donc du code**, après accord de l'auteur :
  - les requêtes de temps GPU dans `core/perf/` (GPU3) ;
  - un compteur d'images incomplètes par vue (sections manquantes ou pas encore construites) ;
  - le vidage d'images en mode debug, ou un enregistrement vidéo de la fenêtre ;
  - le temps jusqu'à une fenêtre complète, côté serveur.
- **Les scripts du banc**, gardés hors du dépôt aujourd'hui : à verser au dépôt, décision de l'auteur.
- **Le monde du banc**, avec ses blocs de commande pour les trajectoires.
- **Le banc de l'auteur**, pour le mod de terrain et le pack de shaders.

---

### EXPLO 8. Décisions qui reviennent à l'auteur

1. **Garder les fenêtres de tous les portails à portée de rendu, quel que soit le regard**, pour un retour instantané :
   réseau et mémoire, contre des images noires au retour.
2. **Synchroniser plus tôt, en cône, ou plus vite** : réseau, contre du noir à l'approche.
3. **Rouvrir le choix écarté du framebuffer par vue** (GUIDE 3.5), pour la résolution réduite, le rafraîchissement
   réduit et l'imposteur.
4. **Les paliers du LOD** : leurs seuils, et s'ils sont des options désactivées par défaut (NR 5.4).
5. **Des cadres plus grands que 8 × 8.**
6. **Le choix de la carte graphique** : doc seule, message en jeu, ou préférence de Windows écrite avec l'accord du
   joueur.
7. **L'ordre des prototypes.** Proposé :
   1. GPU3 (mesurer) ;
   2. la preuve par tranche et GPU1 (vues cachées, exacts) ;
   3. les fenêtres gardées (retour instantané) ;
   4. GPU2 (grand cadre) ;
   5. puis le LOD.

   Dans les lots (BILAN 6, NR 4.3) : GPU3 avec le lot 3 (mesure fiable) ; la preuve par tranche, GPU1 et GPU2, exacts,
   avec le lot 6 (performance client) ; les fenêtres gardées et les paliers du LOD après décision de l'auteur (NR 5.4).
