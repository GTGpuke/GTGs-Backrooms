# Concurrents — GTG's Backrooms

> **Document de conception interne** : il nomme d'autres projets. À garder hors du dépôt public, et sans aucune
> reprise de code ni mention de ces projets dans le mod (`QUALITE_ET_PRESETS.md` §3.0).
>
> État des lieux au **28 octobre 2026**, à partir d'une recherche web : pages CurseForge et Modrinth des projets,
> sites miroirs (Modpack Index, Mado Hosting, 9Minecraft), article du blog CurseForge sur les mods d'horreur, et
> Wikipédia. Elle ne couvre pas tout (projets privés, non publiés ou peu référencés) : l'absence d'un projet ici ne
> prouve pas qu'il n'existe pas. Les détails viennent des pages publiques des projets ; ce qui n'y figurait pas est
> marqué « non indiqué ». Les nombres de téléchargements sont ceux affichés le jour de la recherche.
>
> Ampleur : la seule recherche « the backrooms » renvoie **127 projets** sur CurseForge (mods), sans compter les
> modpacks, Modrinth et Bedrock.

---

## 1. En bref

- Les mods Backrooms sont **nombreux**, et les meilleurs sont **sérieux** : niveaux générés, IA d'entités qui entend
  et fouille, néons qui clignotent, portails depuis l'Overworld, voire monstres qui entendent le chat vocal.
- **Le plus joué** est *Backrooms Found Footage* de SpacePotatoee : environ **2,6 millions** de téléchargements sur
  Modrinth et **1,1 million** sur CurseForge. Il utilise **Veil** pour ses shaders et effets de caméra, et en paie le
  prix : incompatible avec Sodium, Iris et le mode graphique « Fabuleux », problèmes sur **GPU intégrés** et
  **macOS**. C'est exactement ce que notre conception cherche à éviter.
- Plusieurs fonctionnalités que l'on croyait originales **existent déjà ailleurs**, au moins en partie : santé
  mentale (très courante), entité invincible qui écoute et fouille, monstres qui entendent le micro, options
  d'accessibilité (réduction des flashs et des mouvements), inventaire mis de côté à l'entrée.
- Aucun projet trouvé (c'est-à-dire **identifié dans cet état des lieux** ; la recherche ne couvre pas tout) ne réunit **ce que fait GTG's Backrooms** : portails sans coupure qu'on traverse et à travers
  lesquels on voit et entend, rendu propre à la dimension qui laisse l'Overworld vanilla, son et image liés, cadre de
  prison et perception.
- Mais aujourd'hui, ces différences n'existent **que dans les documents**, à part les portails. Les concurrents, eux,
  sont **publiés et joués**.

---

## 2. Les concurrents directs (mods Java)

### 2.1 Backrooms Found Footage (SpacePotatoee) — le plus joué

| Élément               | Ce qu'indiquent ses pages (Modrinth et CurseForge)                                                       |
| --------------------- | ------------------------------------------------------------------------------------------------------- |
| Type                  | Mod **Fabric**, par SpacePotatoee (CurseForge publié par Luidy)                                        |
| Version de Minecraft  | **1.20.1** ; dernier fichier du 24 mai 2026 (`spb-revamped-1.20.1-1.2.0`)                               |
| Popularité            | Environ **2,6 M** téléchargements et 1 700 abonnés sur Modrinth ; environ **1,1 M** sur CurseForge      |
| Licence               | LGPL-3.0 (code source public)                                                                           |
| Niveaux               | Level 0, Level 1 « The Habitable Zone », Level 2 « Pipe Dreams », Level 324 « The Forgotten Station », The Poolrooms, The Infinite Grass Field ; il faut les traverser pour s'échapper |
| Entités et IA         | **Skinwalker** qui prend l'apparence (le skin) d'un joueur ; pas d'apparition naturelle en solo ; IA non détaillée. Selon le blog CurseForge, il **imite la voix des amis** avec Simple Voice Chat |
| Visuel                | **Shaders et effets de caméra propres, faits avec Veil** (Veil intégré, son pack de ressources « deferred » doit rester actif) |
| Son                   | Non détaillé ; **Simple Voice Chat obligatoire**                                                        |
| Accès                 | Se faire étouffer assez longtemps (noclip)                                                              |
| Objets, inventaire    | **Inventaire sauvegardé avant l'entrée**, restauré par une commande (`/gimmemyinventoryback`)          |
| Commandes             | `/skinwalker`, `/release`, `/backroomsevent`, `/gimmemyinventoryback`, `/level`                         |
| Santé mentale         | Non indiqué                                                                                             |
| Multijoueur           | Oui (le skinwalker en a besoin)                                                                         |
| **Limites déclarées** | **Incompatible Sodium et Iris** ; ne pas utiliser « Fabuleux » ; **problèmes sur GPU intégrés** ; **ne fonctionne pas sur macOS** |

**Ce qu'on en retient** :

- La preuve que **le look « found footage » fait le succès** d'un mod Backrooms.
- La preuve que **bâtir sur Veil donne vite un beau rendu, mais ferme la porte aux petites configs, à macOS et à
  Sodium**. Notre palier Low sur GPU intégré, l'OpenGL 3.2 et les trois modes graphiques testés sont une vraie
  différence auprès de ce public.
- L'inventaire mis de côté existe, mais **rendu par commande** : notre colis « =) » automatique va plus loin.
- Son skinwalker exploite déjà le chat vocal : là encore, à soigner plutôt qu'à présenter comme une première.

### 2.2 Backrooms: Project NoClip (Extonic) — la meilleure IA trouvée

| Élément               | Ce qu'indique sa page                                                                                   |
| --------------------- | ------------------------------------------------------------------------------------------------------- |
| Type                  | Mod **Fabric** (Fabric API requise), par Extonic ; **bêta**                                             |
| Version de Minecraft  | **26.1.2** (dernier fichier ; historique en 26.1)                                                       |
| Popularité            | Non indiqué sur les pages consultées                                                                    |
| Niveaux               | Level 0 (The Lobby), Level 1 (Parking Zone), Level 2 (Pipe Dreams), **générés procéduralement** ; les couloirs de maintenance du Level 1 mènent au Level 2 |
| Entités et IA         | **Bacteria** (chasse de salle en salle, suit les bruits, continue de chercher après avoir perdu le joueur de vue), **Duller** (se cache derrière les murs et attire les joueurs), **Smiler** (préfère l'obscurité, poursuit dès qu'il repère un joueur) ; systèmes de traque, de recherche, d'ouïe et de navigation |
| Son                   | Sons propres ; **Simple Voice Chat en option : les entités entendent le micro des joueurs** selon le volume, la distance, le chuchotement, les murs et les réglages du serveur |
| Visuel                | Néons qui clignotent, zones sombres et claires, **pannes de courant** au Level 1, textures, modèles et animations propres ; aucun moteur de rendu indiqué |
| Accès                 | **Portails instables** qui apparaissent au hasard dans l'Overworld ; boussole craftable pour trouver une entrée |
| Objets                | Caisses de ravitaillement ; boussole ; **boîtiers électriques qui rétablissent le courant** ; voitures, tuyaux, flaques, portes, zones de maintenance |
| Navigation            | Menu de répertoire des niveaux (test et voyage)                                                         |
| Santé mentale         | Non indiqué                                                                                             |
| Multijoueur           | Pris en charge, avec options de configuration serveur                                                   |

**Ce qu'on en retient** : c'est la référence à dépasser côté IA des entités. Il fait déjà entendre le chat vocal aux
monstres (proposition `GAMEPLAY.md` §15.3) : une preuve que les joueurs aiment l'idée, et une raison de soigner notre
version plutôt que de la présenter comme une première. Il est aussi **à jour sur les versions récentes** (26.1.2).

### 2.3 Liminality: The Backrooms — le plus proche de notre ton

| Élément               | Ce qu'indique sa page (via Mado Hosting)                                                                |
| --------------------- | ------------------------------------------------------------------------------------------------------- |
| Type                  | Mod **NeoForge**, nom en jeu « Backrooms: Level Zero » ; auteur non indiqué                             |
| Version de Minecraft  | **1.21.1**                                                                                              |
| Popularité            | 562 téléchargements                                                                                     |
| Niveaux               | Level 0 seul, en **quatre chapitres** reliés, une évasion finale, et une limite de **10 minutes** de jeu actif avant la fin |
| Entités et IA         | **Bacteria** qui **écoute, enquête, traque, poursuit et fouille** ; **impossible à tuer** ; s'il attrape le joueur, retour au dernier point de passage du chapitre |
| Visuel                | Cinématiques à la première personne, **lampe torche** (touche F), **effets « found footage »**, interrupteur du post-traitement |
| Accessibilité         | **Réduction des mouvements, réduction des flashs, intensité des effets**                               |
| Son                   | **Ambiance directionnelle** et bourdonnement des néons plus discret                                     |
| Accès                 | S'étouffer dans un bloc (noclip en trois secondes), ou marcher sur une surface instable rare ; commande avec triches activées |
| Santé mentale         | Non indiqué                                                                                             |

**Ce qu'on en retient** : petit projet, mais **le plus proche de notre ton** : entité invincible pilotée par l'ouïe,
look found footage, options d'accessibilité. Nos idées « monstres invincibles », « plafond de clignotement » et
« réduction des mouvements » ne sont donc pas uniques ; notre différence est dans le **moyen de faire fuir chaque
monstre**, les **cachettes**, les **trois sens**, et l'intégration avec les portails et le son.

### 2.4 The Backrooms (LudoCrypt) — l'ancêtre

| Élément               | Ce qu'indique sa page CurseForge                                                                        |
| --------------------- | ------------------------------------------------------------------------------------------------------- |
| Type                  | Mod **Fabric**, par LudoCrypt                                                                          |
| Version de Minecraft  | **1.16.5** (et 1.15.2) ; dernière mise à jour le **18 septembre 2020** ; la page annonce une **refonte complète** |
| Popularité            | Environ **352 500** téléchargements                                                                     |
| Niveaux               | Level 0 (cinq variantes : normal, délabré, Redrooms, pointillés, pointillés rouges), Level 1 (normal, lumières éteintes), Level 2 (trois variantes de couloirs), Level 3 |
| Entités               | **Aucune** : seulement les entités vanilla                                                              |
| Accès                 | S'étouffer, ou rater un lancer de perle de l'Ender (faible chance de noclip)                            |
| Son                   | Musiques d'ambiance par niveau                                                                          |

**Ce qu'on en retient** : la référence historique de l'**ambiance pure**, sans monstres. Une refonte annoncée : à
surveiller.

### 2.5 Backrooms Volume 3 MOD (EufoniaStudio) — le plus de contenu

| Élément               | Ce qu'indique sa page CurseForge                                                                        |
| --------------------- | ------------------------------------------------------------------------------------------------------- |
| Type                  | Mod **Fabric**, par EufoniaStudio ; tous droits réservés                                               |
| Version de Minecraft  | **1.21.1** ; mise à jour le 28 août 2026                                                               |
| Popularité            | Environ 7 350 téléchargements                                                                          |
| Niveaux               | Version gratuite : Level 0, Poolrooms, Smiler Room, Fun Level, Run For Your Life ; **20+ niveaux** annoncés sur un serveur Patreon ; génération procédurale, et un mode « classique » à plans fixes |
| Entités               | **25+ entités** annoncées (Smilers, Skin Stealers, Parking Lurker, Bone Stalker, Death Angel…) ; IA non détaillée |
| Accès                 | Un **lobby** relié à chaque niveau ; sorties placées différemment à chaque partie ; portails vers des **instances** nouvelles ou partagées et persistantes, selon le mode de l'hôte |
| Objets                | Menu de chargement (touche G), **énigmes** par niveau pour trouver la sortie                             |
| Multijoueur           | LAN en version gratuite ; serveur privé sur abonnement Patreon ; mode équipe ou solo, garder l'inventaire à la mort ou non |

**Ce qu'on en retient** : un modèle **économique** (serveur Patreon) et une réponse concrète à notre question
« niveaux partagés ou instances ? » (`GAMEPLAY.md` §14, point 5) : il propose **les deux**, au choix de l'hôte.

### 2.6 Minecraft Backrooms (NeoForge) — santé mentale et entités variées

| Élément               | Ce qu'indique sa page Modrinth                                                                          |
| --------------------- | ------------------------------------------------------------------------------------------------------- |
| Type                  | Mod **NeoForge** ; licence MIT                                                                          |
| Version de Minecraft  | **1.21.1** et 1.20.6 ; mise à jour le mois dernier                                                     |
| Popularité            | Environ 1 200 téléchargements                                                                          |
| Niveaux               | Ancienne version : Backrooms, Parking Garage, Pipe Dreams, Office, Poolrooms (générés). Refonte : Level 0 seul, **génération réellement infinie** |
| Entités               | Huit : Shadows (rapides, aveuglent), Tricks (forme de joueur), Smilers (fuient la lumière), Arms (crient près des joueurs), Handy (ralenti par la rouille), Windows (rendent fou), Eyes (amicaux, coûtent de la santé mentale), Pools Guardian (offre une sortie) |
| Santé mentale         | **Oui** : baisse près des menaces                                                                       |
| Accès et sortie       | S'étouffer dans un mur ou du sable (survie seulement) ; dans la refonte, **portes de sortie** loin dans les Backrooms |

### 2.7 The Accurate Backrooms (ShadyRaspberry872)

| Élément               | Ce qu'indique sa page CurseForge                                                                        |
| --------------------- | ------------------------------------------------------------------------------------------------------- |
| Type                  | Mod **Forge**, équipe de 7 personnes                                                                    |
| Version de Minecraft  | **1.19.2** et 1.18.2 ; dernière mise à jour le **1er novembre 2023** (bêta 2.0.2)                       |
| Popularité            | Environ **171 000** téléchargements                                                                    |
| Contenu               | Plusieurs niveaux générés aléatoirement, entités hostiles, **barre de santé mentale** à effets négatifs, objets d'aide, **bases, avant-postes et communautés** |
| Accès                 | Noclip par des zones instables ou en s'étouffant                                                       |

**Ce qu'on en retient** : l'idée de **communautés et d'avant-postes** dans les Backrooms ; projet populaire mais
**à l'arrêt depuis 2023**.

### 2.8 Autres mods Java repérés, lus en détail

| Projet                                   | Auteur, chargeur, version                    | Téléchargements | Points notables                                                                       |
| ---------------------------------------- | -------------------------------------------- | --------------- | ------------------------------------------------------------------------------------- |
| The Backrooms: Found Footage             | not_CrispyBacon, NeoForge 1.21.1             | ~1 500          | Level 0, Habitable Zone, Fun et Fun+, Run for your life, The End ; six entités ; multijoueur « parfaitement synchronisé » ; pré-alpha |
| Backrooms: The Complex                   | MCreator, NeoForge 1.21.1                    | ~3 400          | Level 0 et 1 ; **aucun monstre** ; pas de santé mentale ; noclip activable ou portail ; exploration entre amis |
| The Backrooms Update                     | MC_CREATIONS, NeoForge 1.21.1                | ~4 900          | Dimension liminale infinie à plusieurs niveaux ; **portails de forme libre** alimentés par redstone ; config des « zones fines » |
| Uniks Backrooms                          | Unikich, Forge 1.20.1                        | ~460            | Levels 0, 1, 2, 3 et 5 ; huit entités ; **santé mentale** qui tue à zéro (4 minutes) ; eau d'amande ; entrée en tombant de 100 blocs ; sorties propres à chaque niveau |
| MCrBackrooms+                            | TygrysGames, Forge 1.20.1 (Fabric expérimental) | ~3 000       | Level 0 ; une entité ; **santé mentale** de 0 à 10 ; eau d'amande ; arrêté depuis 3 ans |
| Backrooms Mod « Liminal Structures »     | NeoForge 1.21.1                              | ~340            | 19 salles du Level 0 posées dans l'End ; **aucune entité ni santé mentale** ; lampe torche qui vacille ; bloc ordinateur fonctionnel |
| Backrooms N' Things                      | NeoForge 1.21.1                              | ~350            | Huit entités ; blocs, nourriture, combinaisons ; **recommande des shaderpacks** (Photon, Solas, Insanity) |
| Liminalness                              | denialpan, NeoForge 1.21.1                   | ~1 200          | Dimension infinie extensible par datapacks ; entrée par un lit dans le noir ; sortie par des blocs à particules ou la mort |
| Immersive Backrooms                      | crafter312, Fabric 1.20.4                    | ~440            | Level 0 ; néons qui vacillent ; bourdonnement ; **portail multibloc** et chambres de retour |
| Capybara's Liminal Spaces                | Forge 1.20.1                                 | ~3 700          | Salles aléatoires, plusieurs zones ; entrée par un vieil ordinateur et un échange avec un villageois |
| Baka's Backrooms Experience              | Joebaka, Forge 1.20.1                        | ~570            | Contenu du Level 0 (moisissure, « dust bunny ») ; **écosystème partiel** ; pour la construction de cartes, pas la survie |
| The Backrooms Untold & FNAF              | OPowerU89_, Forge 1.20.1                     | 15              | Blocs seulement pour l'instant                                                        |

### 2.9 Autres mods Java repérés (pages non lues en détail)

Vus dans la liste CurseForge « the backrooms » ou dans la recherche ; à examiner un par un si besoin.

| Projet                                   | Auteur, chargeur, version (selon la liste)   | Téléchargements | Description courte                                         |
| ---------------------------------------- | -------------------------------------------- | --------------- | ---------------------------------------------------------- |
| The Backrooms Ultimate Edition           | Elias_Lucky, Forge 1.12.2 (2023)             | ~251 000        | Niveaux d'après les informations du wiki                   |
| The True Backrooms                       | platinumrbx, Forge 1.18.2 (2022)             | ~201 500        | Noclip en s'étouffant vers une zone désolée                |
| Into The True Backrooms                  | JoxlOMG, Forge 1.20.1 (2024)                 | ~118 900        | Les Backrooms dans Minecraft                               |
| The Backrooms Mod                        | Loxor04, Forge 1.20.1 (2024)                 | ~102 100        | Ajoute les Backrooms                                       |
| The backrooms, blocks and more           | Trytole, Forge 1.20.1                        | ~23 300         | Blocs                                                      |
| the bedrockrooms mod                     | cozocozo, Forge 1.20.1 (2026)                | ~10 800         | Ressources, dimensions et entités                          |
| The Blockrooms [Backrooms] [Horror]      | Thehoneychurch, Forge 1.20.1 (23 octobre 2026) | ~9 500        | « Vous n'êtes pas seul » ; très récent                     |
| Behind the Cubes [Backrooms]             | diamond_ilya, NeoForge 1.21.1 (2026)         | ~8 500          | Niveaux reliés aux mécaniques et objets uniques            |
| The Immersive Backrooms                  | ProtonGames, Forge 1.20.1 (2026)             | ~7 700          | Ajoute les Backrooms                                       |
| No-Clip [The Backrooms]                  | CatlynnSuperSigma, 26.2 (2026)               | ~5 400          | Mod simple, d'autres niveaux prévus                        |
| Inside the backrooms blocks              | Kostyaniche_games, Forge 1.20.1              | ~4 700          | Blocs, sans dimension                                      |
| Escape The Backrooms (ETB)               | Dra_Ada, Forge 1.20.1                        | ~4 200          | Objets, monstres et blocs                                  |
| Escape The Backrooms – Soundtracks / Entity | Minecraft_HomRoma, NeoForge 26.1.2        | ~3 000 / ~1 600 | Musiques de niveaux ; 15 entités tirées du jeu             |
| The ASYNC Files – The Backrooms          | Noish, Forge 1.20.1                          | ~2 500          | Blocs et constructions liminales                           |
| M.F.T.B – Monsters from the backrooms    | Jack_Pulya, Forge 1.20.1                     | ~1 400          | Sept monstres                                              |
| Backrooms Volume 3, Meultis Backrooms, Old Backrooms, Eternal Pools (Poolrooms), Caverooms, The Looped Rooms, The Backworld, Backrooms Pisodo, The Minecraft (wikidot) Backrooms, Squeaky Backrooms, Faithful Backrooms, Boxy's Backrooms, Extractrooms, MineRooms (blocs MCreator) | Divers | Non lus | Repérés dans la recherche |

Liste de la première version de ce document, conservée : Uniks Backrooms, The Accurate Backrooms, Backrooms Volume 3
MOD, Backrooms: The Complex, MCrBackrooms+, Minecraft Backrooms, Meultis Backrooms, BACKCRAFT - The Ultimate
Backroom (modpack), Backrooms Next (BackroomsMC, modpack), Into the Backrooms: Found Footage Horror (modpack),
Liminalness, Backrooms Dimension (Bedrock). Ceux qui ont été lus en détail ont désormais leur fiche ci-dessus ou
ci-dessous.

---

## 3. Les modpacks

### 3.1 Backrooms: Found Footage (onr23) — le modpack populaire

| Élément               | Ce qu'indique sa page                                                     |
| --------------------- | ------------------------------------------------------------------------- |
| Type                  | **Modpack** Fabric, par onr23, sur Modrinth                               |
| Version de Minecraft  | **1.20.1**                                                                |
| Popularité            | Environ **607 000 téléchargements**, 223 abonnés                          |
| Présentation          | « The Best Backrooms Experience for Minecraft », aventure et multijoueur  |
| Contenu               | Non indiqué sur la page consultée (mods, shaders, caméra, entités)        |
| Licence               | MIT                                                                       |

**Ce qu'on en retient** : l'approche « modpack » assemble des mods existants. Son succès montre la demande ; sa
limite structurelle est que les pièces ne sont pas conçues pour fonctionner ensemble.

### 3.2 Autres modpacks lus en détail

| Modpack                         | Version, chargeur | Téléchargements | Points notables                                                                     |
| ------------------------------- | ----------------- | --------------- | ----------------------------------------------------------------------------------- |
| Backrooms Next (BackroomsMC)    | 1.20.1 Fabric     | ~37 400         | Bâti sur le mod de SpacePotatoee : Level 0 à 2, Poolrooms, plaine infinie ; shaders « found footage » via Veil ; **chat vocal intégré** ; entrée par une petite salle et un bouton ; **incompatible Sodium, OptiFine** |
| The Ultimate Backrooms          | 1.20.1 Fabric     | ~330            | Créatures qui traquent, chassent ou tendent des embuscades ; rareté des ressources ; bêta |
| Funny Backrooms                 | 1.20.1 Fabric     | ~50             | Mod de SpacePotatoee + **Sound Physics Remastered**, Presence Footsteps, Simple Voice Chat, Emotecraft ; incompatible Sodium, Iris et « Fabuleux » |

**Ce qu'on en retient** : la plupart des modpacks reposent sur le mod de SpacePotatoee, complété par des mods de son
(dont Sound Physics Remastered) et le chat vocal. Ils héritent de ses incompatibilités.

### 3.3 Autres modpacks repérés (non lus)

SpacePotato Backrooms Revamped, SpacePotato's Backrooms Found Footage, Backrooms Found Footage (Unofficial), HORROR -
Into The Backrooms - Found Footage (dweller), Backrooms: Lost in Horror [Found Footage], Backrooms - The Ultimate
Bodycam Horror Experience, The Endless Halls, Backrooms Uncovered, The Backrooms Survival, The Backrooms Challenge,
The Backrooms Craftified, Agony: The Backrooms, Backrooms Essentials, Lost in the Backrooms, Blockrooms Stream, KC's
The Backrooms Found Footage, Beyond Reality Liminal Space Pack, BACKCRAFT - The Ultimate Backroom.

---

## 4. Bedrock

| Add-on                          | Auteur            | Version Bedrock     | Téléchargements | Points notables                                                                 |
| ------------------------------- | ----------------- | ------------------- | --------------- | ------------------------------------------------------------------------------- |
| Backrooms Dimension (Preview)   | WanMine (Italie)  | 26.10 et 26.20, Preview avec API bêta | ~81 200 | Level 0 seul ; **Bacteria** qui erre, s'alerte, poursuit, **réagit au son** et perd la trace, plus agressif quand la santé mentale baisse, **un par joueur** ; **santé mentale** (lumière qui la rend, obscurité qui la draine), qui agit sur le brouillard, les hallucinations, les sons et l'entité ; néons qui vacillent, salles « glitchées » ; entrée et sortie par commande, ou porte de sortie rare |
| The Backrooms (dimension)       | Mint Studios      | 26.3 et 26.30       | ~197 500        | Trois niveaux ; entité Hound ; sortie à 5 % d'apparition                        |

**Ce qu'on en retient** : sur Bedrock, la **santé mentale qui modifie le brouillard, les sons et le comportement du
monstre** existe déjà, avec un monstre **par joueur**. Proche de notre duo santé mentale et hallucinations.

---

## 5. Hors Minecraft : Escape the Backrooms

| Élément          | Ce qu'indique Wikipédia                                                                    |
| ---------------- | ------------------------------------------------------------------------------------------ |
| Développeurs     | Fancy Games et Blackbird Interactive ; éditeur Secret Mode ; Unreal Engine 4               |
| Sorties          | Accès anticipé le 11 août 2022 ; version complète Windows le 23 octobre 2025 ; PS5 et Xbox Series le 28 mai 2026 (Game Pass) |
| Jeu              | **Plus de 30 niveaux** ; coopération **jusqu'à 4 joueurs** ; objets (lampe torche, nourriture) ; **santé mentale** dont la perte cause des **hallucinations** et peut tuer ; entités (Smilers, skin stealers, hounds) le plus souvent évitables ; difficulté de facile à cauchemar |
| Réception        | 6/10 chez GameSpot (« boucle de jeu clairsemée ») ; joueurs largement positifs (contenu, **design sonore**, multijoueur) ; plus de **30 000 joueurs simultanés** en moins de deux jours après la version complète |

**Ce qu'on en retient** : jeu autonome sur les Backrooms, c'est la **référence d'expérience** que les joueurs ont en
tête (ambiance, niveaux, coopération, son), plutôt qu'un concurrent direct ; plusieurs mods Minecraft en reprennent d'ailleurs les entités et les musiques. Sa critique
principale, une boucle de jeu clairsemée, est exactement ce que notre tranche verticale doit éviter.

---

## 6. Comparaison avec GTG's Backrooms

| Capacité                                                   | Found Footage (SpacePotatoee) | Project NoClip | Liminality | Volume 3 | GTG's Backrooms |
| ---------------------------------------------------------- | ----------------------------- | -------------- | ---------- | -------- | --------------- |
| Niveaux générés                                            | Oui (6)                       | Oui (3)        | Non (Level 0 en chapitres) | Oui (5 gratuits, 20+ Patreon) | Level 0 généré ; autres prévus |
| IA d'entités (ouïe, recherche, traque)                     | Non détaillée                 | **Oui**        | **Oui**    | Non détaillée | Prévue (`GAMEPLAY.md` §7) |
| Monstre invincible                                         | Non indiqué                   | Non indiqué    | **Oui**    | Non indiqué | Prévu, avec un **moyen de le faire fuir** par monstre |
| Cachettes                                                  | Non indiqué                   | Non indiqué    | Non indiqué | Non indiqué | Prévues (`GAMEPLAY.md` §9) |
| Entités qui exploitent le chat vocal                       | Oui (skinwalker)              | **Oui** (ouïe du micro) | Non indiqué | Non indiqué | Proposition (`GAMEPLAY.md` §15.3) |
| Santé mentale et hallucinations                            | Non indiqué                   | Non indiqué    | Non indiqué | Non indiqué | Prévues ; **courantes ailleurs** (§2.6, §2.7, §4, §5) |
| Néons qui clignotent                                       | Non indiqué                   | Oui (et pannes) | Oui (bourdonnement) | Non indiqué | Prévus, **liés au son** (`MOTEUR_AUDIO.md`) |
| Shaders, effets de caméra                                  | **Oui (Veil)**                | Non indiqué    | **Oui** (found footage, post désactivable) | Non indiqué | Prévus, **moteur maison par palier** (`QUALITE_ET_PRESETS.md`) |
| Fonctionne sur GPU intégré, macOS, avec Sodium             | **Non** (déclaré)             | Non indiqué    | Non indiqué | Non indiqué | **Visé** : Low sur GPU intégré, OpenGL 3.2 ; Sodium toléré |
| Options d'accessibilité (flashs, mouvements)               | Non indiqué                   | Non indiqué    | **Oui**    | Non indiqué | Prévues (`MOTEUR_AUDIO.md` §11, preset Confort) |
| Portails depuis l'Overworld                                | Non (noclip par étouffement)  | Oui (instables, téléportation) | Non (étouffement, surface instable) | Lobby et portails vers des instances | **Oui, sans coupure** : on voit, entend, interagit et traverse (déjà construit) |
| Rendu propre à la dimension, Overworld vanilla             | Non indiqué                   | Non indiqué    | Non indiqué | Non indiqué | Prévu (`MOTEUR_SHADERS.md`) |
| Look qui suit les vues de portail                          | Non                           | Non            | Non        | Non      | Prévu |
| Acoustique par dimension, à travers les portails           | Non (Sound Physics dans certains modpacks, global) | Non indiqué | Ambiance directionnelle | Non indiqué | Prévue (`MOTEUR_AUDIO.md`) |
| Son et image liés (une seule source de vérité)             | Non indiqué                   | Non indiqué    | Non indiqué | Non indiqué | Prévu |
| Paliers de qualité intégrés, du GPU intégré au ray tracing | Non                           | Non indiqué    | Interrupteur du post | Non indiqué | Prévus (`QUALITE_ET_PRESETS.md`) |
| Perception (touche V)                                      | Non                           | Non            | Non        | Non      | Prévue |
| Inventaire mis de côté à l'entrée                          | **Oui, rendu par commande**   | Non indiqué    | Non indiqué | Option de l'hôte (garder l'inventaire à la mort) | Prévu, **rendu automatiquement** (colis « =) ») |
| Cadre de prison, outil de modération                       | Non (commandes d'admin seulement) | Non        | Non        | Non      | Prévu |
| Instances ou niveaux partagés                              | Non indiqué                   | Non indiqué    | Non (solo) | **Les deux, au choix** | À trancher (`GAMEPLAY.md` §14) |
| Jeu de base strictement vanilla                            | Non (noclip par étouffement)  | Non (portails aléatoires dans l'Overworld) | Non (étouffement, surfaces instables) | Non indiqué | **Règle absolue** (`GAMEPLAY.md` §1.1) |
| Version de Minecraft                                       | 1.20.1                        | 26.1.2         | 1.21.1     | 1.21.1   | 1.21.1 |
| Publié et joué                                             | **Oui (~3,7 M)**              | **Oui**        | Oui (petit) | Oui     | **Non, pas encore** |

Colonne Found Footage (modpack onr23) de la première version : niveaux, IA, néons, rendu et son « non indiqués » sur
sa page ; publié et joué (~607 000 téléchargements). Il repose sur le mod de SpacePotatoee (§3.2).

---

## 7. Ce que ça implique

1. **Notre différence est l'intégration**, pas une fonctionnalité isolée : portails, rendu, son et gameplay conçus
   ensemble. Aucun concurrent trouvé ne le fait.
2. **Le seul avantage déjà réel est le système de portails.** Tout le reste est à prouver.
3. **La référence à égaler côté IA est Project NoClip** : ouïe, recherche après perte de vue, chat vocal. Nos monstres
   doivent faire au moins aussi bien.
4. **Livrer vite une tranche verticale montrable** (`GAMEPLAY.md` §16) : un Level 0 où le doute s'installe, un niveau
   habité, le tout visible à travers un portail sans coupure. Ce serait déjà quelque chose qu'aucun autre mod ne propose.
5. **Même règle que pour les autres références** : on observe ce qui plaît aux joueurs, on n'en reprend ni code, ni
   ressources, ni textes. Les noms propres au vocabulaire des Backrooms (entités, niveaux, objets) sont autorisés
   (`GAMEPLAY.md` §15.9) ; les noms des projets concurrents, eux, n'apparaissent pas dans le mod.
6. **Le marché valide le look « found footage »** (le mod le plus joué en a fait son identité) **et punit les petites
   configs** (il ne tourne ni sur GPU intégré, ni sur macOS, ni avec Sodium). Notre palier Low et notre compatibilité
   sont un **argument concret** auprès de ce public, à mettre en avant.
7. **Ce qui n'est pas une différence** : la santé mentale, les entités invincibles qui écoutent, les néons qui
   clignotent, les options d'accessibilité, l'inventaire mis de côté. On les fait **mieux**, pas **en premier**.
8. **Ce qui reste une vraie différence** : portails sans coupure et vues à travers le cadre, jeu de base intact (tous
   les concurrents modifient l'Overworld : étouffement, portails aléatoires), son et image liés, perception,
   hallucinations distinguables par la perception, cadre de prison et colis automatique.
9. **L'entrée par étouffement est la norme** : notre entrée réservée aux admins (prison) est un choix fort, à
   assumer, qui protège le jeu de base mais rend le mod moins découvrable par les joueurs seuls.
10. **Versions** : les mods les plus actifs suivent déjà 26.x (Project NoClip, No-Clip). Rester sur 1.21.1 est
    cohérent pour le serveur, mais à garder en tête si le mod est un jour publié largement (`QUALITE_ET_PRESETS.md`
    §3.3 sur Vulkan).

---

## 8. Positionnement retenu

**Objectif** : non pas le mod qui a le plus de fonctionnalités, mais **la meilleure expérience Backrooms dans
Minecraft**. Les fonctionnalités communes (santé mentale, monstres invincibles, néons qui clignotent, accessibilité,
inventaire mis de côté) sont faites **mieux**, pas en premier.

**Priorités décidées**, dans l'ordre (`GAMEPLAY.md` §1) :

1. **Le plus immersif** : face au mod le plus joué, c'est notre terrain (portails sans coupure, jeu de base intact,
   son et image liés, aucun signe de mod).
2. **Le plus effrayant** : **monstres qui font vraiment peur dès le niveau 1**. Référence à égaler côté IA : Project
   NoClip (§2.2) ; référence de ton : Liminality (§2.3).
3. **Le plus impressionnant techniquement** : au service des deux premiers.

**Hiérarchie de travail qui en découle** :

1. une expérience de peur exceptionnelle, avec une identité claire ;
2. des portails et une présentation visuelle qui la renforcent ;
3. une IA crédible qui crée de la tension et des décisions ;
4. des performances et une accessibilité solides (notre argument face à SpacePotatoee, §2.1) ;
5. beaucoup de contenu, progressivement.

**Critère** : chaque grosse avancée technique doit améliorer quelque chose que le joueur ressent ou voit en jeu.

**Démonstration signature** (`GAMEPLAY.md` §16) : depuis le niveau 0, une présence vue par un portail dans le
niveau 1, entendue de la bonne direction, puis la traversée sans coupure. Aucun projet identifié dans cet état des
lieux ne peut montrer ça.

**Niveau de confiance** : ce document est une **étude de positionnement**, pas une preuve de supériorité. Il existe un
public important (bien étayé) ; notre architecture peut nous différencier (crédible) ; être le meilleur mod Backrooms
ne pourra se juger qu'avec une expérience jouable à comparer.

---

## 9. À refaire régulièrement

- Revérifier ces projets avant chaque grande étape (nouvelles versions, nouvelles fonctionnalités).
- Compléter les lignes « non indiqué » en lisant les pages complètes des projets des §2.9 et §3.3.
- Ajouter les nouveaux projets repérés.
- Surveiller en priorité : la **refonte annoncée de LudoCrypt** (§2.4), **Project NoClip** (actif, versions récentes),
  **The Blockrooms** (mis à jour le 23 octobre 2026) et le mod de **SpacePotatoee** (le plus joué).

---

## Sources

- Backrooms Found Footage (SpacePotatoee) : https://modrinth.com/project/minecraft-found-footage et
  https://www.curseforge.com/minecraft/mc-mods/backrooms-found-footage
- Backrooms: Project NoClip : https://www.curseforge.com/minecraft/mc-mods/backrooms-project-noclip et
  https://www.modpackindex.com/mod/162506/backrooms-project-noclip
- Liminality: The Backrooms : https://www.mado-hosting.com/minecraft-mods/liminality-the-backrooms
- The Backrooms (LudoCrypt) : https://www.curseforge.com/minecraft/mc-mods/the-backrooms
- Backrooms Volume 3 MOD : https://www.curseforge.com/minecraft/mc-mods/backrooms-volume-3-mod
- Minecraft Backrooms : https://modrinth.com/mod/minecraft-backrooms (et https://modrinth.com/project/EvgQT2ca)
- The Accurate Backrooms : https://www.curseforge.com/minecraft/mc-mods/the-accurate-backrooms
- The Backrooms: Found Footage (not_CrispyBacon) : https://www.curseforge.com/minecraft/mc-mods/the-backrooms-found-footage
- Backrooms: The Complex : https://www.mado-hosting.com/minecraft-mods/backroomsthecomplex (et
  https://www.modpackindex.com/mod/148606/backrooms-the-complex)
- The Backrooms Update : https://www.curseforge.com/minecraft/mc-mods/minecraft-backrooms-update/files/8522677
- Uniks Backrooms : https://www.curseforge.com/minecraft/mc-mods/uniks-backrooms
- MCrBackrooms+ : https://modrinth.com/mod/mcrbackrooms+
- Backrooms Mod « Liminal Structures » : https://modrinth.com/project/3oaousel
- Backrooms N' Things : https://modrinth.com/project/yZIOAtve
- Liminalness : https://modrinth.com/mod/liminalness
- Immersive Backrooms : https://www.curseforge.com/minecraft/mc-mods/backroomsmod
- Capybara's Liminal Spaces : https://www.mado-hosting.com/minecraft-mods/capybaras-liminal-spaces
- Baka's Backrooms Experience : https://www.curseforge.com/minecraft/mc-mods/bakas-backrooms-experience
- The Backrooms Untold & FNAF : https://www.curseforge.com/minecraft/mc-mods/the-backrooms-untold-fnaf
- Liste CurseForge « the backrooms » : https://www.curseforge.com/minecraft/search?class=mc-mods&page=1&pageSize=20&sortBy=relevancy&search=the+backrooms
- Backrooms: Found Footage (modpack onr23) : https://minecraftbible.com/modpacks/backroomsff
- Backrooms Next : https://modrinth.com/project/REzz58bs
- The Ultimate Backrooms : https://modrinth.com/project/ibOyED5s
- Funny Backrooms : https://modrinth.com/project/yrdHZ9EX
- BACKCRAFT : https://www.curseforge.com/minecraft/modpacks/backcraft-the-ultimate-backroom
- Backrooms Dimension (Bedrock, WanMine) : https://www.curseforge.com/minecraft-bedrock/addons/backrooms-dimension
- The Backrooms (Bedrock, Mint Studios) : https://mcpedl.com/the-backrooms-dimension/
- Blog CurseForge, mods d'horreur 2026 : https://blog.curseforge.com/5-best-minecraft-horror-mods-on-curseforge-to-play-right-now/
- Escape the Backrooms : https://en.wikipedia.org/wiki/Escape_the_Backrooms

---

## Historique des révisions

- **v1** : premier état des lieux (Project NoClip, modpack Found Footage, liste de projets repérés, Escape the
  Backrooms, comparaison, implications).
- **v2** : recherche approfondie : mod Found Footage de SpacePotatoee (le plus joué, bâti sur Veil, limites
  déclarées), Liminality, LudoCrypt, Backrooms Volume 3, Minecraft Backrooms, The Accurate Backrooms détaillés ;
  douze autres mods lus en détail ; liste CurseForge (127 projets) ; modpacks lus en détail ; deux add-ons Bedrock ;
  Escape the Backrooms détaillé ; comparaison élargie à quatre concurrents ; implications 6 à 10 ; contenu de la v1
  conservé.
- **v3** : positionnement retenu (§8) : priorités décidées (immersif, puis effrayant dès le niveau 1, puis technique),
  hiérarchie de travail, critère pour la technique, démonstration signature, niveau de confiance ; formulation « aucun
  projet identifié dans cet état des lieux » (§1) ; « À refaire régulièrement » devient le §9.
- **v4** : §7 point 5 : les noms du vocabulaire des Backrooms sont autorisés ; code, ressources et textes restent non
  repris, les noms des projets concurrents restent absents du mod.
