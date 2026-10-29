# Documentation de GTG's Backrooms

GTG's Backrooms est un mod Fabric pour Minecraft 1.21.1. Il ajoute le Level 0 des Backrooms, des portails qu'on
traverse sans coupure, et deux moteurs maison pour l'image et le son.

## Le projet

| Document | Contenu |
|----------|---------|
| [GUIDE.md](GUIDE.md) | Vision, règles d'or, principes d'architecture, glossaire, suite du travail |
| [DECISIONS.md](DECISIONS.md) | Les décisions de l'auteur, datées, avec leur raison |
| [CHANGELOG.md](../CHANGELOG.md) | Les changements, version par version |

## Les systèmes

| Document | Contenu |
|----------|---------|
| [systemes/ARCHITECTURE.md](systemes/ARCHITECTURE.md) | Paquets, où ranger quoi, cycle de vie, séparation client et serveur |
| [systemes/PORTAILS.md](systemes/PORTAILS.md) | Portails : pose, traversée, synchronisation, rendu des vues, interaction à travers le cadre |
| [systemes/RENDU.md](systemes/RENDU.md) | Moteur de shaders : look par dimension, néons, post-traitement, packs de shaders |
| [systemes/AUDIO.md](systemes/AUDIO.md) | Moteur audio : acoustique par dimension, occlusion, réverb, son lié à l'image |
| [systemes/LEVEL0.md](systemes/LEVEL0.md) | Le Level 0 : dimension, génération verrouillée, arrivée des portails |

## Développer

| Document | Contenu |
|----------|---------|
| [dev/INSTALLATION.md](dev/INSTALLATION.md) | Prérequis, premier lancement, commandes Gradle |
| [dev/TESTS.md](dev/TESTS.md) | Tests unitaires, tests en jeu, tests du client, couverture, benchmark |
| [dev/CONTRIBUER.md](dev/CONTRIBUER.md) | Git, commits, vérifications avant de pousser, style du code |
| [dev/CI.md](dev/CI.md) | Workflows GitHub Actions et publication d'une version |
| [dev/DEPANNAGE.md](dev/DEPANNAGE.md) | Problèmes courants et leur solution |

## Référence

| Document | Contenu |
|----------|---------|
| [reference/CONTENU.md](reference/CONTENU.md) | Blocs, items et entités du mod |
| [reference/OPTIONS.md](reference/OPTIONS.md) | Toutes les options `-D` et leurs équivalents Gradle |

## Specs de l'auteur

Ces documents décrivent ce que le mod doit devenir. Ils ne se modifient qu'à la demande de l'auteur.

- [BACKROOMS_CONTENT_RULES.md](BACKROOMS_CONTENT_RULES.md) : règles de contenu et direction artistique des Backrooms.
- [backrooms/](backrooms/) : specs des niveaux et gabarit des structures manuelles du Level 0.
- [dimensions/](dimensions/) : type de dimension multicouche du Level 0, en JSON de référence.
