# GTG's Backrooms — Guide de conception et de développement

*Fabric 1.21.1 — Document de référence du projet — 19 octobre 2026*

Ce document est le **guide de travail du projet**. Il s'adresse à l'auteur et aux sessions de développement, qui doivent le suivre toutes, en complément des règles du projet existantes, qui gardent la priorité sur les règles de code. Il résume la vision, les règles d'or, l'état réel du projet, l'architecture cible des deux nouveaux systèmes (rendu et audio) **adaptée à ce qui existe déjà**, la méthode de réécriture « clean room », la feuille de route par versions livrables et les critères mesurables du moteur, les fonctionnalités attendues, les fonctionnalités d'imbrication, la cohabitation avec les packs de shaders, les mods dont la compatibilité est à surveiller, le passage sous GPL-3 et les pistes à explorer.

Emplacement : `docs/GUIDE.md`, référencé depuis les règles du projet (ligne d'onboarding et section « Guide et nouveaux systèmes ») et `docs/INDEX.md`.

Il a été rédigé à partir de l'état public du dépôt (`README.md`, règles du projet, `docs/PORTALS.md`) au 19 octobre 2026, puis confronté au dépôt complet le 20 octobre 2026 : les sections 2, 3.2, 7 et 8 et la feuille de route ont été corrigées, et les écarts relevés sont consignés en section 2.4. Toute affirmation sur l'existant reste à revérifier dans le dépôt avant d'agir.

Note : les sections juridiques décrivent des pratiques courantes et ne constituent pas un avis juridique.

---

## 0. Règles d'or

Ces règles priment sur tout le reste du guide. En cas de doute, c'est ici qu'on revient.

1. **Le projet existant est la base.** Le dépôt contient un système de portails complet et un Level 0 fonctionnel, avec leurs conventions (règles du projet), leur documentation (`docs/`), leurs tests et leur verrou de couverture. Tout ce qui est décrit ici s'adapte à cette structure, jamais l'inverse. Avant toute modification : lire les règles du projet, `docs/ARCHITECTURE.md`, `docs/PORTALS.md` et `docs/TESTING.md`, relever les conventions, puis s'y conformer. Les schémas de ce guide sont des cibles indicatives.

2. **Étendre avant de réécrire.** Le système de portails (`core/portal/`) ne se réécrit pas : il s'étend pour exposer ce dont le rendu et l'audio ont besoin. Toute refonte d'un module existant est proposée à l'auteur, avec les compromis, et validée avant d'être entreprise.

3. **Code original uniquement.** Aucun code tiers n'est repris, traduit ou paraphrasé, quelle que soit sa licence (règle déjà inscrite dans les règles du projet). Les principes sont libres, l'implémentation est la nôtre. Méthode clean room obligatoire (section 4). Exception encadrée : pour **s'intégrer** à un mod tiers (le mod de shaders, le mod qui dessine le terrain, un mod de chat vocal), lire son API et ses points d'accroche est nécessaire et autorisé ; en copier l'implémentation ne l'est pas.

4. **Un profil par monde, jamais un état global.** Le rendu et l'audio dépendent du monde rendu ou émetteur, jamais de la dimension du joueur (section 3.1). Le projet le fait déjà pour la lightmap et le brouillard des vues ; on généralise.

5. **Zéro couture.** Une fonctionnalité visuelle ou sonore doit être correcte à travers un portail, dans une vue imbriquée, et au passage d'un monde à l'autre (`ClientPortalCrossing`). Sinon elle n'est pas terminée.

6. **Rien n'est terminé sans sa définition de « terminé ».** Chaque fonctionnalité a ses critères (section 5), vérifiés en jeu, et passe `./gradlew clean build --warning-mode all` sans warning : Checkstyle, tests unitaires, tests en jeu, couverture à 100 % des instructions et des branches, contenu du JAR.

7. **Tout état modifié est restauré.** OpenGL, OpenAL, état global de Minecraft : ce qu'une vue ou une source change, elle le remet, comme le fait déjà `PortalStencilRenderer` pour le brouillard, l'éclairage des entités et la couleur de shader.

8. **Rien dans les chemins chauds.** Aucune allocation dans les méthodes tick/render, pas de concaténation de chaînes, mesure par le moniteur de performance (`core/perf/`) dès le premier jalon.

9. **Documenter en même temps que coder.** `docs/` reflète l'état réel : `ARCHITECTURE.md`, `INDEX.md`, `CHANGELOG.md`, et un document par système (`PORTALS.md` existe ; `RENDERING.md` et `AUDIO.md` à créer sur le même modèle). Un changement sans sa documentation n'est pas terminé.

10. **L'auteur décide.** Les sessions de développement proposent, expliquent les compromis et implémentent ce qui est validé. Elles ne prennent pas seules de décision structurante : licence, dépendance, découpage, suppression de fonctionnalité, modification de la génération verrouillée du Level 0 (`LevelZeroGoldenTest`).

11. **Le code et ses preuves sont la source de vérité.** Ce guide décrit l'intention et les règles ; le dépôt (tests, captures de référence, mesures, documentation à jour) décrit la réalité. Quand ils divergent, on ne force pas le code à suivre un guide périmé : on consigne l'écart, on décide avec l'auteur, et on corrige le guide ou le code. Aucune décision du guide n'est tenue pour acquise sans sa preuve dans le dépôt, et aucun scénario n'est annoncé « faisable » avant d'avoir été prototypé.

---

## 1. Vision et périmètre

Un mod qui reproduit les backrooms dans Minecraft avec le plus de fidélité possible, composé de trois systèmes moteur conçus ensemble :

| Système | Rôle | État |
|---|---|---|
| **Portails** | Passage sans coupure Overworld ↔ Level 0 (et niveau ↔ niveau à venir), vues imbriquées, interaction, lumière et son à travers le cadre | Fonctionnel, testé, documenté (`docs/PORTALS.md`) |
| **Rendu** | Pipeline d'éclairage et de post-processing par monde, visible à travers les portails | À écrire ; direction ouverte (section 3.2, septième inconnue) |
| **Audio** | Propagation, occlusion et réverbération par monde, audibles à travers les portails | À écrire, sur la base du relais et du rejeu au cadre existants |

Objectif central : le joueur ne doit jamais percevoir de couture. Chaque monde a son identité visuelle et sonore, et les portails sont des fenêtres honnêtes sur l'autre côté.

Hors périmètre technique de ce guide : contenu des niveaux, lore, entités, gameplay.

---

## 2. État réel du projet (base de travail)

Ce que les nouveaux systèmes doivent connaître et réutiliser. Références : `README.md`, règles du projet, `docs/PORTALS.md`.

### 2.1 Structure et conventions

- Racine : `com.gtgpuke.gtgsbackrooms`, `mod_id` `gtgs_backrooms`. Points d'entrée `GtgsBackroomsMod` (commun) et `GtgsBackroomsClientMod` (client).
- `core/` : le socle du mod, `ModConstants` (`MOD_ID`, `LOGGER`), `ModConfig` (options lues en `-D`), `registry/` (un registre par type : blocs, items, onglet, payloads…), `perf/` et `portal/` (le système de portails, dont la baguette). `backrooms/` : le contenu des Backrooms, `level0/`. Puis `mixin/` (commun) et `mixin/client/` (le paquet de compatibilité a été retiré le 28 octobre 2026 au soir, section 2.4).
- Conventions : commentaires, logs et messages en français, sources ASCII ; texte visible via `Text.translatable()` avec clés dans `en_us.json` **et** `fr_fr.json` ; ordre des imports fixé ; logs par `ModConstants.LOGGER` ; pas d'abstraction sans deuxième implémentation réelle ; pas de dossier vide.
- Qualité : JUnit 5 sous le classloader Fabric, tests en jeu (`src/gametest/`, mod séparé), JaCoCo à 100 % des instructions et des branches hors colle OpenGL client, Checkstyle, `verifyJarContents` (aucune dépendance embarquée, aucun test dans le JAR), `MixinTargetsTest` (chaque cible de mixin se charge).
- Documentation : `docs/ARCHITECTURE.md`, `docs/INDEX.md`, `docs/PORTALS.md`, `docs/TESTING.md`, `docs/CHANGELOG.md`, `docs/AUDIT.md`, `docs/SETUP.md`, et les consignes de session (onboarding, avant push, vérification finale).
- Licence actuelle : **tous droits réservés**, `LICENSE` copiée dans les JAR. Le passage en GPL-3 est traité en section 9.

### 2.2 Ce que le système de portails fait déjà (et que le rendu et l'audio réutilisent)

**Rendu des vues** (`core/portal/client/view/`, `client/world/`, `mixin/client/`)

- Sans pack de shaders, les vues se dessinent **au milieu de `WorldRenderer.render`**, après les blocs opaques, les entités et les block entities, avant tout ce qui est translucide (`PortalStencilRenderer`, `MixinWorldRenderer`). Le monde du joueur se fond ensuite par-dessus (eau, verre, particules, nuages, pluie). Sous un pack de shaders, elles se dessinent au contraire **après l'image** du monde du joueur, chacune par le rendu complet de son renderer dans le pipeline du pack pour sa dimension (`ShaderPackPortalRenderer`, pack reconnu par `ShaderPackState`).
- Masque par **stencil** : sans pack, la valeur du stencil est le niveau d'imbrication (0 monde du joueur, 1 vue, 2 vue imbriquée) ; sous un pack, une valeur par cadre et un bit haut pour l'imbrication. Un seul niveau d'imbrication supporté ; au-delà, cadre sombre. L'attachement de profondeur du framebuffer principal est remplacé par un attachement profondeur + stencil (`MixinFramebuffer`).
- **Projection du joueur partout**, caméra transposée (`PortalViewTransform`) ; découpe au plan d'arrivée par `gl_ClipDistance`, uniforme `backrooms_ClipPlane` injecté au chargement dans chaque vertex shader vanilla (`ShaderClipInjector`, `MixinShaderStage`, `MixinShaderProgram`). Un shader sans cet uniforme coupe la découpe le temps de son dessin. Quand un autre mod dessine le terrain ou sous un pack (shaders qui ignorent `gl_ClipDistance`), la vue se coupe par une projection à plan proche oblique (`ObliqueProjection`), seulement s'il y a quelque chose à couper entre la caméra de la vue et le plan d'arrivée, et les entités à cheval sont rognées au plan sur le processeur (`ClippedVertexConsumers`).
- **Un `WorldRenderer` par monde distant** (`RemoteWorld`) et des vues secondaires pour les portails qui ramènent dans le monde du joueur ; sections choisies par la vue elle-même (`ViewSectionWalker`, `ViewCone`), rien dans `setupTerrain` ni `renderLayer`.
- **Lightmap par monde vu**, une fois par tick (`ViewLightmaps`) ; **brouillard par monde** (`RemoteViewRenderer.applyFogColor`). Ce sont déjà deux morceaux de « profil par monde ».
- Traversée prédite côté client (`ClientPortalCrossing.switchWorld`) : échange du monde principal et d'un monde distant **sans passer par `joinWorld`**, avant chaque `GameRenderer.renderWorld`. Tout ce qui tient un état par monde doit suivre cet échange.
- Coupe-circuit (`PortalRenderState`) : stencil refusé ou exception → rendu distant coupé pour la session, cadres sombres.
- Un mod qui réécrit le pipeline de terrain est **supporté** : la vue détecte sa grille sans portée (`RemoteViewRenderer.foreignTerrain`), lui passe la lumière des chunks distants par la voie vanilla (`readLightData`) et le laisse choisir et dessiner ses sections ; vérifié sur le banc de production. Un pack de shaders est reconnu sans nommer de mod (`ShaderPackState` : au milieu du rendu du monde du joueur, les programmes que vanilla donne aux entités et aux translucides sont d'une classe du pack et non `ShaderProgram`) ; le module qui lisait pour cela l'API publique du mod de shaders, seul mod alors nommé dans le code, a été retiré le 28 octobre 2026 au soir (section 2.4) ; sans pack, vues identiques à vanilla ; avec un pack, voir la section 7.

**Son** (`core/portal/client/sound/`, `mixin/MixinPlayerManager`, `MixinServerWorld`, `mixin/client/MixinClientWorld`)

- **Relais serveur** : sons, évènements de monde et particules d'une dimension partent, enveloppés avec leur dimension (`portal_remote_entity`), aux joueurs dont la fenêtre synchronisée contient leur chunk.
- **Rejeu au cadre** : côté client, tout son positionné d'un monde distant passe par les portails du monde du joueur qui mènent à ce monde ; il est rejoué au point du cadre le plus proche de l'image de la source, avec le volume qui lui reste après le trajet jusqu'au cadre (`FrameSoundPlayer`, `PortalSound`), puis le moteur de son atténue du cadre à l'oreille. Un son du monde du joueur à plus de 64 blocs passe de même par un portail intra-dimension.
- Les sons de suivi d'entité (wagonnet, abeille) ne traversent pas.

**Lumière** (`core/portal/light/`, `mixin/MixinWorld`, `MixinChunkBlockLightProvider`)

- La lumière des blocs traverse un portail ouvert qui la transmet, d'un niveau de moins, par des **sources virtuelles** publiées sur le monde et lues par le moteur de lumière, serveur et client (`PortalLightTransmission`, `VirtualLightSources`, `ClientPortalLight`). La lumière du ciel ne passe pas.

**Mesure** (`core/perf/`)

- Sections nommées `client.portal.render`, `.view`, `.terrain`, `.entities`, `.lightmap`, `.straddling`, `.world_create`, etc., et côté serveur `portal.teleport.player`, `portal.sync.tick`, etc. Les nouveaux systèmes ajoutent les leurs (`client.render.*`, `client.audio.*`, `audio.relay.*`). Le moniteur mesure le thread de rendu, pas le GPU (`docs/PORTALS.md`) : les passes de rendu exigent une mesure GPU (requêtes de temps GL, lecture différée d'une image) à ajouter à `core/perf/` dès la preuve de concept (section 4.7).

### 2.3 Conséquences pour les nouveaux systèmes

- Le rendu avancé **s'insère dans l'architecture stencil existante** (section 3.2) ; le rendu dans des framebuffers séparés n'est utilisé que là où il est indispensable (cohabitation avec un pack de shaders, section 7).
- L'audio **étend `FrameSoundPlayer` et le relais existants** (section 3.3) au lieu de créer un second chemin pour les sons distants.
- Les nouveaux états par monde (lumières, post-process, environnement acoustique) suivent `ClientPortalCrossing.switchWorld` comme la lightmap.
- Les mixins nouveaux vont dans `mixin/` et `mixin/client/`, sont couverts par `MixinTargetsTest`, et n'injectent rien dans `setupTerrain` ni `renderLayer`.
- La colle OpenGL et OpenAL reste minimale, isolée et nommée ; toute la logique (sélection de profil, culling de lumières, calcul de filtres, paramètres de réverbération, routage) est pure et testée à 100 %. `docs/TESTING.md` précise que la colle **OpenAL** client sera exemptée comme la colle OpenGL.

### 2.4 Écarts relevés le 20 octobre 2026 (règle d'or 11)

Confrontation de la première version de ce guide au dépôt complet :

- Les vues sous un mod qui réécrit le terrain étaient données comme non supportées : elles le sont, sans module dédié (section 2.2) ; la V7 est franchie.
- Le cas C de la section 7 (Overworld sous le pack vu depuis le Level 0) était donné comme non réaliste : il est réalisé par `ShaderPackPortalRenderer`, et le cas B l'est par le même chemin ; la V8 est franchie pour l'essentiel.
- Le G-buffer par injection (section 3.2) ne peut atteindre ni les shaders du mod de terrain (chargés par son propre chargeur, pas par `ShaderStage`) ni ceux d'un pack : septième inconnue, décision ouverte.
- Un module de compatibilité avec le mod de shaders existe (retiré le 28 octobre 2026 au soir, rétabli le 29 avec un autre rôle : rendre les Backrooms par nos shaders sous un pack ; la détection d'un pack reste générique, par `ShaderPackState`, voir les dernières puces) ; les vues sous pack se dessinent après l'image, pas au milieu de `WorldRenderer.render` ; le stencil sous pack n'est pas le niveau d'imbrication ; la découpe sous pack passe par la projection oblique et le rognage sur le processeur.
- `PortalFacing` a six orientations, dont haut et bas, pas seulement un demi-tour horizontal.
- Le passage sous GPL-3 n'est pas décidé (section 9).
- Le 22 octobre 2026, à la demande de l'auteur : `PortalFacing` compte douze orientations (les six, puis leurs retournées,
  pour une arrivée tête en bas, réglage `upside_down`) ; un bloc tout entier derrière le plan d'un portail ouvert n'existe
  plus pour l'entité engagée dans le cadre (`PortalCollision.tunnel`, collisions, étouffement, voile de mur), un cadre se
  pose donc contre un mur ou sur un sol plein et un cadre au sol est une trappe ; qui sort d'un cadre d'arrivée au sol en
  est gardé (`ArrivalGuard`). La traversée d'un cadre couché se décide aux yeux, au plafond comme au sol.
- Le 21 octobre 2026, à la demande de l'auteur, l'arborescence a changé : le système de portails, les registres (ex-`init/`), la configuration et la mesure sont sous `core/`, le contenu Gang et Arbre Maudit est retiré avec `feature/`, et le bloc portail ne se crée plus qu'avec la baguette, sans item à lui (plus de réglages proposés dans un onglet) ; la
  baguette copie les réglages d'un portail visé, et sa pose fait entendre un portail qui s'ouvre (premier son du mod,
  `ModSounds`). L'arrivée dans le Level 0 est calculée par le Level 0 (`backrooms/level0/LevelZeroArrival`), que
  `PortalTargets` interroge. La cible de la section 3.4 suit : `render/` et `audio/` iront sous `core/`, à côté de `portal/` dont ils consomment l'API.
- Les anciennes specs (`docs/backrooms/1 - Arborescence`, `3 - Portails`, la spec `4` des shaders sur une bibliothèque de rendu tierce, `to-check`, `docs/DEPENDENCIES.md`), qui bâtissaient le rendu sur une bibliothèque tierce et une autre arborescence, ont été retirées : ce guide les remplace. Restent la spec du générateur du Level 0, sa roadmap et les règles de contenu.
- Le 26 octobre 2026, à la demande de l'auteur : une vue lointaine (un bloc y fait moins de 6 pixels à l'écran, ou le
  cadre est au-delà de la distance de rendu des vues, jusqu'à 128 blocs) n'est plus qu'une **image figée**, dessinée de
  temps en temps dans un framebuffer à elle puis collée sur le cadre (`FrozenViews`). C'est un framebuffer de vue séparé
  hors du chemin sous pack, que la section 3.5 et les règles du projet écartent : l'écart est accordé pour ce seul palier, sur le
  chemin sans pack. La vue y est le rendu de la vue lui-même, avec la projection du joueur et sous le stencil de son
  propre framebuffer (portails imbriqués compris) ; ce n'est ni un post-process par vue ni une composition, et le rendu
  des vues vivantes reste dans le framebuffer principal.
- Le 27 octobre 2026, l'auteur a ajouté `docs/backrooms/MOTEUR_SHADERS.md` (v7) et `docs/backrooms/MOTEUR_AUDIO.md` (v4),
  qui **font foi** sur `docs/backrooms/backrooms-mod-plan.md`, donc sur les sections 3.2, 3.3, 3.4, 4.4, 4.6, 5.2, 5.3,
  6 et 7 de ce guide, qui en dérivent. Ce qui change : le rendu tient en deux étages (injection par fragment dans les
  shaders vanilla patchés par enveloppe de `main`, puis un post masqué par le stencil), sans G-buffer, sans éclairage
  différé, sans ombres ni reflets ; la preuve de concept V1 et la feuille de route V0 à V8 sont remplacées par les phases
  des deux spécifications, le premier jalon commun (un néon du Level 0 vu et entendu par un portail depuis l'Overworld,
  synchrone, le reste de l'Overworld vanilla) et le test décisif (un cri à travers deux portails) ; le profil unique
  `WorldProfile` devient deux données, `LookProfile` et `AudioProfile`, l'Overworld n'ayant jamais de profil ; le
  glossaire (4.4) suit les spécifications (`LookProfile`, `LookState`, `Cue`, `NeonSchedule`, `FlashLimiter`…) ; le
  traitement d'un son dépend de la seule dimension où il est émis (un son de l'Overworld reste vanilla même entendu
  par un cadre : plus de « part du slot du monde de l'auditeur » ni d'écho des pas depuis l'Overworld, et aucun effet
  caméra plein écran dans une dimension vanilla) ; la table de matériaux partagée n'existe plus ; `client.look.*` pour
  les sections de mesure du rendu. Décisions de l'auteur, le même jour : **le mod de shaders et le mod qui dessine le
  terrain restent supportés** (les spécifications les bloquaient ; les vues sous pack et sous mod de terrain existent et
  restent), et dans les Backrooms le rendu du mod doit remplacer celui du pack (cas A de la section 7, à prototyper dans
  le module de compatibilité du mod de shaders ; livré le 29 octobre 2026, dernière puce) ; voir grand sur le reste ;
  aucune régression ; structure du dépôt respectée (`core/render/`, `core/audio/`, le paquet de compatibilité au sommet,
  colle dans `*/client/`). Écarts consignés aux spécifications : la consigne « tracer l'inspiration »
  (nommer le projet de référence dans `docs/RENDERING.md` et `docs/AUDIO.md`) ne s'applique pas, la règle du dépôt
  (aucun autre mod nommé hors des modules de compatibilité) prime ; `LookTransition` (interpolation du profil du joueur sur 0,3 s à la traversée) n'est pas
  réalisée, le look suivant le pixel et non le joueur (aucune couture par construction, règle d'or 5) ; les sept
  identifiants de look du stencil (`0x1C`) remplacent le bit unique `0x80` de la spécification, dont le chemin sous pack
  se sert déjà ; le document du rendu s'appelle `docs/RENDERING.md` (la spécification dit `docs/RENDER.md` en un
  endroit). Le premier jalon livré (phases 0 à 4 du rendu) est décrit dans `docs/RENDERING.md`.
- Le 27 octobre 2026, après le jalon : phase 5 livrée (post masqué par les bits de look du stencil, framebuffers
  déclarés en données, programme `look_post` du mod). Le remplacement du pack par le pipeline vanilla dans les
  dimensions à look (décision du matin) a été prototypé par le pipeline que le mod de shaders crée par dimension et
  **retiré** : il rend bien le Level 0 par nos shaders quand le joueur y est, mais le mod de terrain n'a qu'un format de
  maillage pour tout le client et deux pipelines dans une même image (une vue du Level 0 depuis l'Overworld sous le pack) dessinent le
  terrain en éclats ; une bascule par traversée reconstruirait le terrain et recompilerait le pack au retour (la section
  7.3, cas A, supposait le contraire). Livré à la place : sous un pack, le look entier de la dimension du joueur se pose
  sur l'image du pack, avant les vues. Décision à prendre par l'auteur : garder ce mode (sans coupure, le pack éclaire),
  ou le pipeline vanilla dans les Backrooms au prix d'une coupure à chaque traversée sous un pack. Tranché le
  29 octobre 2026 : le pipeline vanilla dans les Backrooms, pour toute l'image (dernière puce).
- Le 27 octobre 2026, soir : phases 6 à 8 livrées (néons qui éclairent par bloc d'uniformes, qualité légère ou complète,
  bloom, repli de compilation). Écarts aux spécifications : l'état de chaque néon est calculé sur le processeur
  (`NeonSchedule`, une fois par néon retenu et par image) et envoyé avec lui dans le bloc d'uniformes, au lieu d'un
  jumeau GLSL de la fonction de hachage (section 6.8) : une seule implémentation à tester, et l'audio lira la même ; la
  qualité se choisit par option ou d'après la chaîne du pilote (`GL_RENDERER`), pas par une mesure de temps d'image ;
  le bloom n'est masqué qu'à la composition (il peut déborder vers l'intérieur d'un cadre, jamais sur les pixels
  vanilla). Correction du même soir : les shaders vanilla n'étaient en fait jamais patchés en production (fabrique à
  cache de `GameRenderer` ignorée par `LookResources`) ; le journal compte désormais les étages patchés compilés par le
  pilote à chaque rechargement, et le banc le vérifie (`docs/RENDERING.md`, Tests).
- Le 27 octobre 2026, soir, jugement de l'auteur sur le rendu livré (« ça marche pour un POC mais c'est loin d'être
  bien ») et consignes : **le vacillement et la variation des tubes sont mis de côté** (profil du Level 0 à zéro,
  `NeonSchedule` gardé sans être branché ; la liaison son-image des sections 6.5 à 6.10 de `MOTEUR_AUDIO.md` attend) ;
  le rendu doit être **optimisé et mesuré** (temps GPU par `GL_TIME_ELAPSED`, aucune allocation par image : fait, voir
  `docs/RENDERING.md`) ; le **moteur audio** démarre par l'acoustique (phase 5 : `AudioProfile`, EFX, occlusion) avec
  des **sons par défaut** (l'auteur se moque des fichiers son pour l'instant). Écarts à `MOTEUR_AUDIO.md` consignés
  dans `docs/AUDIO.md` (Limites) : occlusion par un rayon droit sur le thread de rendu (ni `WorldSnapshot` ni thread de
  calcul), réverb constante par dimension (ni rayons ni acoustique dérivée de la génération), un seul envoi auxiliaire,
  segment source → cadre non évalué, bourdonnement dans le seul monde du joueur, `core/audio/` plutôt que
  `client/audio/` (structure du dépôt).
- Le 28 octobre 2026, demande de l'auteur (« je veux que tout soit fait ») : phases 7 puis 6 de `MOTEUR_AUDIO.md`
  livrées (`docs/AUDIO.md`). Écarts qui restent : **l'acoustique dérivée de la génération (section 6.4) est impossible
  côté client**, qui n'a pas la graine du monde (vanilla n'envoie qu'une graine hachée) ; `"acoustics": "generated"`
  vaut les rayons avec leur cache par cellule (plan B de l'hypothèse H9, section 16), une acoustique calculée par le
  serveur restant possible plus tard. Le `WorldSnapshot` de la section 6.3 est une copie **incrémentale** par sections
  (deux par tick, sections changées recopiées) plutôt qu'une copie d'un bloc autour de l'auditeur ; un résultat
  calculé avec trop de rayons hors de la copie est jeté (section 12, « snapshot périmé »). Les rayons ne traversent
  qu'un cadre et seulement si l'autre côté est copié (le monde distant d'une vue). Le second envoi porte la réverb de
  base de la salle de l'auditeur, pas celle de sa cellule. La direction perçue (P3) n'est pas faite.
- Le 28 octobre 2026, même demande : phases 1 à 4 de `MOTEUR_AUDIO.md` (liaison son-image) livrées, et le look dans
  les vues de portail sous un pack (plan B de `MOTEUR_SHADERS.md`, section 15, H8). Écarts : l'état des néons reste
  calculé sur le processeur et envoyé dans le bloc des néons (pas de jumeau GLSL ni de vecteurs de test Java/GLSL,
  section 6.8 : une seule implémentation, que le son lit aussi) ; les perturbations s'appliquent de même sur le
  processeur au remplissage du bloc ; pas de cache des enveloppes sur disque (section 6.6) ; le tremblement de caméra
  (`camera_shake`) n'est pas fait, un lien qui le demande est refusé ; le plafond de clignotement porte sur l'effet de
  toutes les perturbations ensemble, pour tout l'écran, sans pondération par vue (section 11) ; le décalage son-image
  n'est pas mesuré par capture audio-vidéo (section 10.3). Le vacillement des tubes reste à zéro dans le profil du
  Level 0 (décision de l'auteur du 27 octobre) : la mécanique est branchée côté son (volume, claquements) et côté image,
  et ne fait rien tant que `flickering` et `dead` valent zéro.
- Le 28 octobre 2026, soir, même demande (« toutes les fonctionnalités prévues » des deux spécifications). Écarts
  **levés** : jumeau GLSL du calendrier des néons (`neon_schedule.glsl`, hachage entier de 32 bits des deux côtés,
  573 vecteurs communs écrits par un programme indépendant, vérifiés en test pour Java et sur le processeur graphique
  pour GLSL ; la face du tube suit son état et les perturbations dans le shader ; un écart coupe le jumeau et la face
  suit la valeur envoyée, section 12 de `MOTEUR_AUDIO.md`) ; enveloppes calculées en arrière-plan et gardées sur disque
  (6.6) ; tremblement de caméra (`camera_shake`, option coupée par défaut, jamais dans une dimension vanilla) et courbes
  des réactions (`easing`) ; plafond de clignotement pondéré par la surface d'écran de chaque vue (section 11) ;
  **acoustique dérivée de la génération** (6.4) : le serveur envoie au client la seule graine du layout du Level 0
  (`LevelZeroLayoutSync`), la forme de la salle se lit dans le layout et les rayons reprennent la main là où la copie
  du monde contredit la génération (H9 mesurée : même réglage pour 70 % de 40 sons, même ouverture pour tous) ; deux
  envois auxiliaires demandés à la création du contexte OpenAL ; repli par le volume seul sans EFX (9.3) ; milieu sous
  l'eau ; direction perçue (P3, livrée mais laissée à zéro dans le profil du Level 0 : la spécification la veut validée
  à l'oreille d'abord) ; rayons de débogage ; un son passe deux cadres (portail vu à travers un autre) et le **test
  décisif** (section 12.1 de `MOTEUR_SHADERS.md`) passe au banc ; évènement public des portails
  `ClientPortalCrossing.WORLD_SWITCHED` (règle d'or 2 : un point d'accès, pas une couche). Écarts **nouveaux** : céder la place à
  un autre moteur acoustique (9.1) se décide à ses objets EFX dans le contexte (un filtre, un effet ou un slot qui
  n'est pas des nôtres), pas par son identifiant de mod : la règle du dépôt interdit de nommer un autre mod hors
  d'un module de compatibilité, et le bloquer par `breaks` dans `fabric.mod.json` le nommerait (décision de l'auteur à
  prendre : module de compatibilité, ou cession seule) ; sous un pack de shaders, une vue compte pour l'écran entier dans le
  plafond de clignotement (plus prudent, jamais plus permissif) ; un portail imbriqué vers un **troisième** monde restait
  sombre et muet, le serveur n'envoyant au client que l'autre côté des portails du monde du joueur (le test décisif
  s'est donc joué avec deux cadres vers le Level 0 ; limite levée la nuit même, par une extension de la synchronisation,
  dernière puce) ; l'occlusion d'un son à deux cadres ne compte pas le segment entre les deux cadres.
- Le 28 octobre 2026, nuit, revue des recommandations de `docs/backrooms/backrooms-mod-plan.md` (demande de l'auteur,
  après les deux moteurs). **Faites** : accessibilité (10.3 : « Réduire les flashs » coupe aussi le vacillement des
  tubes, options `gtgsbackrooms.look.reduceNoise` pour le bruit visuel et `gtgsbackrooms.audio.reduceBass` pour les
  graves) ; pré-chauffage des réverbs (10.1) ; export de l'état d'une image par commande de debug (10.2,
  `/backrooms_state`) ; cohabitation avec un pack (7.5) : option `auto` ou `off`, plage de versions du mod de shaders
  supportée et message hors de la plage (retiré avec le module de compatibilité le 28 octobre 2026 au soir, rétabli
  avec lui le 29 : `IrisVersion`, message traduit à la connexion, `IrisVersionNotice`). **Écarts** : l'option s'appelle
  `gtgsbackrooms.portal.shaderCohabitation` (espace des options des portails, qu'elle règle) et non `render.` ; le mode `views_vanilla` n'existe pas (deux pipelines dans
  une image dessinent le terrain en éclats, essai du 27 octobre ; dans les Backrooms, depuis le 29, toute l'image est
  vanilla, vues comprises). **Non faites, à décider par l'auteur** : conflits durs
  par `breaks` dans `fabric.mod.json` (8.5 ; ils nommeraient un autre mod hors d'un module de compatibilité) ; lumières
  dynamiques transmises par un cadre (6.1) : la spécification interdit toute réaction visuelle sur un pixel vanilla
  (Overworld), il ne resterait que les portails entre deux dimensions des Backrooms, une seule aujourd'hui, et il
  faudrait un plan de coupe par lumière pour ne pas éclairer derrière le cadre ; budget dynamique (10.1 : la qualité se
  choisit au pilote, écart déjà consigné) ; second niveau d'imbrication (refonte des portails, règle d'or 2) ; « entendre
  le Level 0 avant de voir le cadre » (10.3 : le bourdonnement porte à seize blocs, la vue à 64) ; captures de référence
  et scènes par commande dans le dépôt (10.2 : elles demandent un client, elles vivent au banc). **Écartées par les
  spécifications** : G-buffer et éclairage indirect, rendu différé « fin », reflets écran, ombre du cadre, réverbération
  par convolution, table de matériaux partagée. **Déjà là** : HRTF (option vanilla `directionalAudio`, « Directional Audio », par OpenAL Soft),
  rayons qui traversent un cadre, ambiances positionnelles, un seul évènement de bascule (`WORLD_SWITCHED`), aucun
  rechargement de shader à la traversée. Le contrôle d'état GL a révélé, le même soir, que le look par vue sous un pack
  (plan B) effaçait les vues depuis le matin : corrigé, mesure refaite (`docs/RENDERING.md`).
- Le 28 octobre 2026, nuit, décision de l'auteur : le mod ne nomme aucun autre mod et n'a aucune autre dépendance de
  mod (précisée le 29 : les modules de compatibilité restent permis, dernière puce). Le même soir, le module de compatibilité qui lisait l'API publique du mod de shaders et sa dépendance de
  compilation ont été **retirés** : il n'y a plus de paquet de compatibilité (trois paquets : `core/`, `backrooms/`,
  `mixin/`), et l'exception qui permettait de nommer ce mod dans ce seul module n'existe plus. Un pack de shaders est
  désormais reconnu sans nommer de mod, par `ShaderPackState` (paquet `core/portal/client/view/`) : au milieu du rendu
  du monde du joueur, juste avant la couche translucide (là où les portails se dessinent), les programmes que vanilla
  donne aux entités et aux translucides (`GameRenderer.getRenderTypeEntitySolidProgram`,
  `getRenderTypeTranslucentProgram`) sont d'une classe du pack et non `ShaderProgram` ; vérifié au banc (pack actif,
  coupé puis rallumé en cours de partie : mêmes images qu'avec l'ancienne détection). Le contrôle de la plage de
  versions du mod de shaders et son message traduit ont disparu avec le module ; l'option
  `gtgsbackrooms.portal.shaderCohabitation` (`auto`/`off`) reste. Le mod ne dépend plus que de Fabric Loader et de
  Fabric API. Ce guide désigne désormais les autres mods par leur famille (section 8) ; les conflits durs par `breaks`
  restés à décider (8.5, puces précédentes) nommeraient un autre mod et n'ont plus d'exception possible, et les modules
  de compatibilité qu'il envisage encore (sections 3.2, 4.1, 5.3, 8 et 9.3) relèvent de cette décision.

- Le 28 octobre 2026, nuit, même demande (« tout doit marcher ») : un portail vu à travers un autre vers un troisième
  monde n'est plus sombre. Le serveur ajoute aux fenêtres synchronisées d'un joueur celle de l'arrivée de tout portail
  visible situé dans la fenêtre d'un monde distant qu'il voit, s'il mène ailleurs que dans son monde
  (`PortalViewTracker.seenThroughAnother`) : une extension de la sélection des fenêtres, pas une refonte (règle d'or 2),
  bornée à un niveau d'imbrication et à la carte du client. Vérifié par un test en jeu (Overworld → Nether → End) et au
  banc (vue de l'End dans la vue du Level 0) ; le son à deux cadres en profite.
- Le 29 octobre 2026, décisions de l'auteur : **les modules de compatibilité sont permis** (un autre mod se nomme dans
  son module `compat/<mod>/`, et nulle part ailleurs ; aucune dépendance de mod à l'exécution hors Fabric Loader et
  Fabric API), et **sous un pack de shaders, le Level 0 se voit avec le rendu du mod** (cas A de la section 7). Livré :
  `compat/iris` (mixins à part, `gtgs_backrooms.compat.mixins.json`, appliqués par `CompatMixinPlugin` seulement si le
  mod de shaders est là dans la plage vérifiée, `IrisVersion`, de 1.8.8 à 1.8.14-beta.1 ; le mod de shaders et le mod de
  terrain en `modCompileOnly`, jamais embarqués ni requis). Tant que le joueur est dans une dimension à look, le
  gestionnaire de pipelines du mod de shaders rend son pipeline vanilla pour tout ce qui se dessine, vues comprises :
  une image n'a qu'un format de maillage du terrain (deux pipelines dans une image dessinaient le terrain en éclats,
  essai du 27 octobre). À chaque bascule, les réglages globaux du pipeline quitté sont gardés et ceux de l'autre
  reposés, les shaders de sections du mod de terrain rechargés et le terrain reconstruit ; le pipeline du pack reste en
  cache. Mesuré au banc : image de 96 à 117 ms en entrant dans le Level 0, de 52 ms en revenant, aucune recompilation,
  terrain intact ; le Level 0 sous le pack a l'image du Level 0 sans pack (écart moyen 1,7 à 2,2/255). Écarts à la
  section 7.3 : l'Overworld vu depuis le Level 0 est vanilla (sans le pack) tant que le joueur y est, et le Level 0 vu
  depuis l'Overworld reste rendu par le pack, le look posé dessus ; la bascule a un coût (reconstruction du terrain),
  que la section supposait nul. Option `gtgsbackrooms.look.replaceShaderPack` (vrai par défaut). La détection d'un pack
  reste générique (`ShaderPackState`).
- Le 29 octobre 2026, suite, décisions de l'auteur sur les points restés ouverts :
  - **Pas de `breaks`** : un mod en conflit se rend compatible (module `compat/<mod>/` si besoin), il n'est jamais
    bloqué. Seule exception assumée, le mod de shaders : dans les Backrooms, le rendu du mod remplace le pack, sans
    coupure, et le pack revient dans les mondes vanilla (`compat/iris`, puce précédente ; la bascule coûte encore une
    image d'environ 100 ms et quelques images de terrain en reconstruction : à rendre invisible).
  - **Lumières dynamiques transmises par un cadre** (6.1) : retenues, entre deux dimensions des Backrooms seulement
    (aucun pixel vanilla touché) ; à réaliser.
  - **Budget dynamique** (10.1) : gardé en piste, pas utile aujourd'hui.
  - **Second niveau d'imbrication** : abandonné ; un portail dans un portail suffit (5.1 bis, 10.1).
  - **Tests** : la priorité est d'avoir tout le code couvert, en tests unitaires, d'intégration et de non-régression,
    au lieu d'une vérification des shaders par la CI seule (« je suis seul et je dois pouvoir te faire confiance »).
    Constat du même jour : le verrou de couverture à 100 % ne mesure que le code hors des exclusions de `build.gradle`
    (rendu et son côté client, mixins, compatibilité), soit environ la moitié des lignes du mod ; l'autre moitié n'était
    vérifiée qu'au banc, à la main. La règle « colle minimale exclue » est remplacée par la couverture de tout le code
    (`docs/TESTING.md`).
  - Livré le même jour : le harnais des tests du client (vrai client, fenêtre cachée, erreurs OpenGL relevées) et dix
    tests. Ils ont trouvé deux défauts, corrigés : le brouillard des Backrooms ne prenait pas la couleur de son profil
    (vanilla le mêlait au ciel noir ; cadre lointain sombre, image figée noire), et, sous Linux, la fermeture des
    tampons différés des vues provoquait des centaines de milliers d'erreurs OpenGL. Un troisième, propre aux pilotes
    Mesa (image figée après une traversée), reste ouvert à la demande de l'auteur (« plus tard » ; `docs/PORTALS.md`,
    limites connues). L'auteur ne veut pas de Docker sur sa machine : le chemin Linux se rejoue par la CI.

---

## 3. Principes d'architecture

### 3.1 Un profil par monde, jamais un état global

Chaque dimension est associée à un **profil** qui regroupe ce qui la distingue. Le rendu et l'audio interrogent le profil du **monde rendu ou émetteur**, jamais celui où se trouve le joueur. Le profil est une donnée (record, chargé depuis les ressources du mod), pas une hiérarchie d'interfaces : deux profils réels existent dès le départ (Overworld vanilla, Level 0), ce qui respecte la règle « pas d'abstraction sans deuxième implémentation ».

```java
// Donnee, pas abstraction : un record par dimension, lu depuis les ressources.
record WorldProfile(
    LightingSettings lighting,   // differe ou vanilla, lumieres dynamiques, ombres
    PostSettings post,           // chaine de post-process, vide pour l'Overworld
    AudioSettings audio,         // environnement acoustique, slots EFX, materiaux
    AtmosphereSettings atmo      // brouillard, ciel, lightmap (deja par monde), couleurs
) {}
```

Le piège à éviter : `if (joueurDansLeLevel0) activer()`. Ça casse dès qu'on regarde à travers un portail.

### 3.2 Rendu : s'insérer dans le stencil, pas le remplacer

Le projet dessine chaque vue dans le framebuffer principal, sous un masque de stencil, avec la projection du joueur et une caméra transposée. Le rendu avancé s'y greffe :

1. **G-buffer par injection de shaders.** `ShaderClipInjector` ajoute déjà un uniforme et une sortie aux vertex shaders vanilla au chargement. Le même mécanisme, étendu aux fragment shaders, ajoute des sorties MRT (normales, matériau, émissif) écrites dans des attachements supplémentaires du framebuffer principal (`MixinFramebuffer` en ajoute déjà un). Aucun shader vanilla n'est remplacé : ils sont augmentés. Les sorties MRT ne sont actives que pendant les couches opaques et cutout, les entités et les block entities ; avant les couches translucides, les particules, la météo, les nuages, la main et l'interface, les tampons supplémentaires sont désactivés (sélection des draw buffers ou masque de couleur par attachement), sinon les translucides écriraient des normales fausses par-dessus les opaques.
2. **Passe d'éclairage par niveau de stencil.** Après les blocs opaques, les entités et les block entities d'un monde (le monde du joueur, ou une vue avant que sa zone ne reprenne sa profondeur dans `PortalStencilRenderer`), une passe plein écran limitée par le test de stencil (égal au niveau courant) et le ciseau du cadre lit le G-buffer et les matrices **de cette vue** (caméra transposée, projection du joueur) et écrit la couleur éclairée selon le profil du monde de la vue. Les lumières sont celles du monde de la vue, transformées dans son espace caméra.
3. **Post-process par niveau de stencil.** En fin d'image, avant que le stencil ne soit relâché, une chaîne par monde s'applique aux pixels de son niveau. Les effets locaux (tonemap, grain, teinte, exposition) respectent strictement le masque. Les effets qui s'étalent (bloom) peuvent déborder du cadre : c'est un choix de design par effet, documenté.
4. **Exposition et états temporels par monde.** Tout état qui persiste d'une image à l'autre (adaptation, moyenne mobile, bruit animé) est tenu par monde et suit `ClientPortalCrossing.switchWorld`.
5. **Translucides et particules** restent en rendu direct ; s'ils doivent recevoir les lumières dynamiques, ils lisent une petite grille de lumières en uniforme (chemin « forward » léger).

Ce qui ne change pas : la projection du joueur partout, la découpe `gl_ClipDistance`, l'ordre vanilla des couches, les coupe-circuits de `PortalRenderState`. Au passage d'un monde à l'autre, le niveau 0 du stencil prend le profil du monde d'arrivée et la vue de retour celui du monde quitté : la transition est sans couture par construction.

Le rendu de vues dans un framebuffer dédié (hors du framebuffer principal) est réservé au cas d'un pack de shaders (section 7) : il coûte un second chemin de composition et on ne le paie que là.

**C'est le plus gros pari technique du plan, et il se lève par une preuve de concept bloquante (V1, section 4.6) avant toute ligne d'éclairage.** Le mécanisme d'injection est déjà éprouvé dans ce dépôt (`ShaderClipInjectorTest` couvre 54 des 55 vertex shaders du jar), mais le G-buffer ajoute sept inconnues à vérifier des deux côtés d'un cadre et au Seam :

1. le framebuffer principal vanilla, son redimensionnement et sa copie à l'écran, qui ignorent les attachements supplémentaires ;
2. les modes graphiques, **Fabulous** en particulier, qui rendent les translucides dans des framebuffers séparés et les composent par une chaîne de post vanilla ;
3. les post-effets vanilla (spectateur, F4) qui copient le framebuffer principal ;
4. les shaders qui `discard`, qui écrivent la profondeur, ou que Fabric API ou un autre mod enregistre en plus de ceux du jar ;
5. la cohérence formats × profondeur × stencil × ciseau entre le niveau 0 et une vue, dans la même image ;
6. le Seam : les états par monde échangés dans l'image où `switchWorld` a lieu ;
7. **les shaders qui ne passent pas par `ShaderStage`** : le mod de terrain charge les siens par son propre chargeur, et un pack de shaders remplace ceux de vanilla ; l'injection ne les atteint pas, donc le G-buffer tel que décrit ne couvre que le chemin vanilla pur. Trois issues, à trancher après la preuve de concept : un pipeline propre limité au chemin vanilla et documenté comme tel ; un module de compatibilité qui accroche le chargeur de shaders du mod de terrain (lecture d'API tierce, règle d'or 3) ; ou l'abandon du pipeline propre au profit des packs de shaders du joueur, qui rendent déjà le Level 0 (section 7), la piste rendu se réduisant alors aux profils, à la lightmap et au brouillard.

Tant que ces sept points ne sont pas verts, on revoit cette section, on n'écrit pas d'éclairage.

### 3.3 Audio : étendre le rejeu au cadre et le relais

- Chaque source sonore est **taguée avec son monde d'origine**. Pour un son rejoué par `FrameSoundPlayer`, c'est le monde distant d'où il vient ; pour un son du monde du joueur, c'est ce monde.
- La **réverbération est routée par source** vers le slot EFX du monde d'origine (une source peut envoyer vers plusieurs slots : celui de son monde, et une part de celui du monde de l'auditeur quand elle a traversé un cadre).
- La **position virtuelle** (image de la source par le passage, `toDestination`) donne la direction ; l'**atténuation** garde la distance de chemin déjà calculée par `PortalSound` (source → cadre, puis cadre → oreille) ; l'ouverture (taille apparente, angle) ajoute un filtre passe-bas.
- Les **rayons** d'occlusion et de réverbération parcourent le monde de la source puis, s'ils touchent un cadre, le monde d'arrivée après transposition, avec les mondes distants déjà chargés côté client (`RemoteWorldRegistry`).
- Le **relais serveur** existant (`relayToViewers`) reste le canal : on y ajoute, si besoin, ce que le traitement avancé exige (par exemple le matériau du bloc émetteur), rien de plus.

### 3.4 Où placer les nouveaux modules

Cible indicative, à ajuster au dépôt réel lors de l'audit (section 11) :

```
com/gtgpuke/gtgsbackrooms/
├── core/
│   ├── portal/        existant - a etendre par des points d'acces, pas a reecrire
│   ├── render/        NOUVEAU : profils, gbuffer (injection), lumieres, passes, post
│   │   ├── profile/   WorldProfile et chargement
│   │   ├── light/     lumieres dynamiques, culling, grille
│   │   ├── pass/      passe d'eclairage, chaines de post, par niveau de stencil
│   │   ├── shader/    injection MRT (meme famille que ShaderClipInjector)
│   │   └── client/    colle GL (minimale, nommee, exemptee de couverture)
│   ├── audio/         NOUVEAU : environnements, routage EFX, rayons, materiaux
│   │   ├── profile/   AudioSettings, materiaux par bloc (tags)
│   │   ├── route/     SourceRoute : monde d'origine, cadre, chemin, filtres
│   │   ├── trace/     rayons d'occlusion et de rebond, traversee des cadres
│   │   └── client/    colle OpenAL (minimale, nommee, exemptee de couverture)
│   ├── registry/      existant - un registre par type (dont ModSounds)
│   └── perf/          existant - nouvelles sections
├── backrooms/
│   └── level0/        existant - contenu, configure les systemes par donnees
├── (compatibilite)    paquet retire le 28 octobre 2026 : aucun autre mod nomme ni requis
└── mixin/, mixin/client/   existants - nouveaux mixins au meme endroit
```

Règles de dépendance :

- `render/` et `audio/` consomment l'API de `portal/` (vues, `PortalViewTransform`, `toDestination`/`fromDestination`, `RemoteWorldRegistry`, événement de bascule) ; ils n'en modifient pas les internes. Si une information manque, `portal/` l'expose, sans changer sa logique.
- Ni `render/` ni `audio/` ne dépendent du contenu (`backrooms/`, dont le Level 0) ; le contenu les configure par données (profils, tags de blocs).
- Un module de compatibilité traduit : il appelle le mod tiers et nos propres API, sans logique métier du mod (`compat/iris` ne décide que du pipeline du mod de shaders, section 2.4).

### 3.5 Choix écartés, et pourquoi (pour ne pas les reproposer)

| Idée de la première version du guide | Décision | Raison |
|---|---|---|
| Un framebuffer par vue, post-process par vue puis composition | Écartée comme défaut ; sous un pack de shaders, la vue est un rendu complet dans le pipeline du pack, après l'image (section 7.3) ; seule exception, l'image figée d'une vue lointaine (section 2.4, 26 octobre 2026), rendue de temps en temps, jamais à chaque image | Le projet dessine déjà les vues dans le framebuffer principal sous stencil, avec la projection du joueur et la découpe par shaders. Un second chemin de composition coûterait un rendu de plus par vue et dupliquerait ce qui marche |
| Séparation systématique `api/` et `impl/` par système | Écartée | Règle du dépôt : pas de couche d'abstraction sans deuxième implémentation réelle. Une API publique ne se crée que quand un second consommateur existe |
| `WorldProfile` comme hiérarchie d'interfaces | Remplacée par un record de données chargé depuis les ressources | Même règle ; deux profils réels (Overworld, Level 0) suffisent et se décrivent en données |
| Règles du projet fournies comme fichier complet | Remplacées par un bloc à insérer (section 4.2) | Celles du dépôt sont riches et font autorité sur les règles de code ; on les complète |
| Interface graphique de configuration dès le départ | Reportée | Le projet lit ses options en `-D` (`ModConfig`) ; une interface viendra si elle est utile aux joueurs, avec clés `en_us` et `fr_fr` |

---

## 4. Méthode de réécriture « clean room »

Objectif : un code **neuf et original**, en structure et en implémentation, à partir d'une compréhension forte des principes. La règle « aucun code ni ressource repris d'un projet tiers » est déjà dans les règles du projet ; voici comment la tenir en pratique dans les sessions de développement.

### 4.1 Deux phases strictement séparées

**Phase A — Étude (lecture seule).** Une session dédiée lit un projet de référence et rédige un **document de conception en langage naturel** dans `docs/` (par exemple `docs/design/RENDERING-NOTES.md`, `docs/design/AUDIO-NOTES.md`) : principes et algorithmes en prose, points d'accroche dans Minecraft (quelle méthode, pourquoi là), pièges connus, ordres de grandeur. Interdit : copier des extraits de code, des noms de classes, de méthodes, de champs ou de packages.

**Phase B — Implémentation (sans source tierce).** Une **nouvelle session**, sans code de référence dans son contexte, implémente à partir des notes de conception, de `docs/ARCHITECTURE.md`, de ce guide et des conventions du dépôt.

Ne jamais mélanger les deux phases dans une même session.

**Cas particulier : intégration à un mod tiers** (mod de shaders, mod de rendu du terrain, mod de chat vocal). Lire l'API publique et les points d'accroche du mod cible est nécessaire pour écrire un module de compatibilité ; c'est une lecture d'interface, pas de conception. Le module compile contre le mod (`modCompileOnly`), n'en embarque rien (`verifyJarContents`) et ne reprend aucun code. Depuis le 28 octobre 2026 au soir, le mod n'a plus aucun module de compatibilité ni aucune autre dépendance de mod (section 2.4).

### 4.2 Ajouts aux règles du projet (bloc à insérer, pas un remplacement)

```markdown
## Guide et nouveaux systemes (rendu, audio)
- Lire `docs/GUIDE.md` au debut de chaque session ; ses regles d'or (section 0) s'appliquent.
- Le systeme de portails (`core/portal/`) est la base : l'etendre par des points
  d'acces (methodes publiques, evenements), pas par des couches d'abstraction, et ne pas
  en reecrire la logique. Toute refonte est proposee et validee avant.
- Un profil par monde (`WorldProfile`, donnee, pas interface) : aucun test sur la dimension
  du joueur pour decider d'un comportement de rendu ou d'audio.
- Le rendu avance s'insere dans le stencil existant (`PortalStencilRenderer`) : G-buffer
  par injection de shaders (meme famille que `ShaderClipInjector`), passes limitees par le
  niveau de stencil. Pas de framebuffer de vue separe hors du chemin sous pack de shaders.
- L'audio etend `FrameSoundPlayer`, `PortalSound` et le relais serveur existants.
- Sessions d'implementation : aucun code source d'un autre mod dans le contexte ; les
  notes de conception `docs/design/*.md` sont la seule source autorisee. Lire l'API d'un
  mod tiers n'est permis que pour ecrire un module de compatibilite.
- Si tu te souviens d'une implementation existante d'une fonctionnalite, n'en reprends
  que le principe ; reecris a partir de notre architecture (profils par monde, passes
  par niveau de stencil, routage par source).
- Chaque fonctionnalite a une definition de "termine" (`docs/GUIDE.md`, section 5) et
  n'est livree qu'une fois verifiee en jeu et le build complet vert.
- Nommage : vocabulaire du glossaire (`docs/GUIDE.md`, section 4.4) pour les concepts
  nouveaux ; les termes deja en place dans `portal/` sont conserves.
- Colle OpenGL et OpenAL : minimale, isolee dans `*/client/`, nommee dans `docs/TESTING.md` ;
  tout le reste est couvert a 100 %.
- Chaque systeme a son document : `docs/RENDERING.md`, `docs/AUDIO.md`, sur le modele de
  `docs/PORTALS.md` (en jeu, architecture, protocole, limites connues, tests).
```

### 4.3 Garantir l'originalité en pratique

- **Partir de l'existant.** Les nouveaux systèmes se greffent sur une architecture déjà originale (stencil par niveau, découpe par injection, rejeu au cadre). C'est la première garantie : ils ne peuvent pas ressembler à des bibliothèques conçues pour un seul monde.
- **Architecture d'abord.** Compléter `docs/ARCHITECTURE.md` (où s'insèrent `render/`, `audio/` et le paquet de compatibilité) **avant** la phase d'étude.
- **Vocabulaire propre** (section 4.4).
- **Convergences inévitables, assumées.** Certains points d'injection n'ont qu'un emplacement sensé dans Minecraft (création d'une source OpenAL, chargement d'un shader, fin de `WorldRenderer.render`). Deux implémentations indépendantes y injectent au même endroit : contrainte du moteur, pas copie.
- **Audit de fin de jalon (optionnel).** Une session en lecture seule compare le code produit aux projets de référence et signale les ressemblances trop étroites ; ce qui est signalé est réécrit en phase B. Cette session ne modifie jamais le code.
- **Historique Git propre**, commits atomiques : la trace de la démarche.

### 4.4 Glossaire (concepts nouveaux ; les termes de `portal/` restent)

| Terme | Signification |
|---|---|
| **WorldProfile** | Donnée par dimension : éclairage, post-process, acoustique, atmosphère |
| **ViewLevel** | Niveau de stencil d'une vue (0, 1, 2, puis 3 si un deuxième niveau d'imbrication arrive), déjà la convention de `PortalStencilRenderer` |
| **LightPass** | Passe d'éclairage plein écran d'un niveau de stencil |
| **PostChain** | Chaîne de post-process d'un monde, appliquée à son niveau |
| **AcousticZone** | Région d'un monde partageant un même environnement réverbérant |
| **SourceRoute** | Chemin d'une source vers l'auditeur : monde d'origine, cadre traversé, distance, filtres, slots |
| **Seam** | Instant de l'échange de monde principal (`ClientPortalCrossing.switchWorld`) |

Termes existants à conserver tels quels : portail, cadre, face, arrivée, vue, vue secondaire, vue imbriquée, monde distant, sortie fugace, plan de découpe, image (d'un point par le passage).

### 4.5 Garantir que ça marche

1. **Jalons courts et visibles en jeu**, dans l'ordre de la feuille de route (section 4.6) : la preuve de concept du G-buffer (V1) avant toute lumière, une lumière avant plusieurs, la réverbération par monde avant les rayons, les rayons dans un monde avant les rayons à travers un cadre. Chaque jalon produit quelque chose que l'auteur voit ou entend.
2. **Définition de « terminé » par fonctionnalité** (section 5), build complet vert.
3. **Tests unitaires** sur tout ce qui est pur : sélection de profil, culling de lumières, matrices par vue, filtres et atténuations, paramètres EFX, routage, parcours de rayons (y compris la transposition par `PortalFacing`).
4. **Tests en jeu** pour ce qui est serveur : relais enrichi, matériaux, états par dimension.
5. **Overlays de debug** (comme `-PportalDebugRendering`) : lumières, zones, rayons sonores, routage par source, niveaux de stencil.
6. **RenderDoc** pour le rendu, **moniteur de performance** pour tout : sections `client.render.*` et `client.audio.*` dès le premier jalon, budgets fixés.
7. **Scènes de test** dans un monde dédié (portail incliné, boucle, pièce réverbérante, lumière traversante), chargeable par `-PquickPlayWorld`.
8. **Matrice de compatibilité** (section 8) à chaque version majeure.

### 4.6 Feuille de route par versions livrables

Le périmètre complet (sections 5 à 7) reste la cible : rien n'est retiré, tout est ordonné. Chaque version est livrable seule sur `master`, build vert, documentation à jour, et n'ouvre la suivante qu'une fois sa porte de sortie franchie. Les deux pistes, rendu et audio, ne partagent que les points d'accès de `portal/`, le record `WorldProfile`, la table des matériaux et les sections du moniteur : ces **contrats se fixent en V0**, et les deux pistes peuvent ensuite avancer en parallèle dans des sessions de développement séparées. Une seule piste fusionne à la fois.

| Version | Contenu | Porte de sortie |
|---|---|---|
| **V0 Consolidation** | Audit du dépôt à jour ; guide, règles du projet, `ARCHITECTURE.md` ; captures de référence des scènes de test ; mesure CPU **et GPU** du rendu distant actuel ; contrats partagés (`WorldProfile` minimal, table des matériaux vide, sections perf) ; passage GPL-3 | Build vert, captures stockées, budgets chiffrés, aucune empreinte de `LevelZeroGoldenTest` changée |
| **V1 Preuve de concept rendu** (jalon bloquant) | Attachement MRT ; une donnée écrite par injection dans un fragment shader et relue par une passe de debug au niveau 0, puis au niveau 1 ; vérifiée au Seam, en Fast/Fancy/Fabulous, en spectateur, au redimensionnement | Les six points de la section 3.2 verts des deux côtés d'un cadre ; sinon retour sur 3.2 |
| **V2 Première lumière** | Une lumière ponctuelle dans le Level 0, puis quelques-unes ; culling par cadre ; lightmap et brouillard pilotés par profil ; pas d'ombres, pas de post | Budget tenu sur le banc de `docs/PORTALS.md` (12 chunks) |
| **V3 Audio de base** (parallèle à V1–V2) | `AudioSettings` par monde, slots EFX, routage par monde d'origine pour les sources du joueur et celles de `FrameSoundPlayer`, repli sans EFX | Réverbération différente de chaque côté d'un cadre ; un son distant garde celle de son monde |
| **V4 Audio physique** | Occlusion par rayons et matériaux, puis rebonds, puis rayons à travers un cadre, position virtuelle, filtre d'ouverture | Overlay des rayons ; transpositions testées unitairement |
| **V5 Post-process et imbrication** | Chaînes de post par niveau, exposition par monde, lumières dynamiques transmises, matériaux partagés, lumières réactives, ombres | Aucun saut mesurable au Seam (captures avant/après) |
| **V6 Portails avancés** | Niveau ↔ niveau, intra-dimension libre, rotation libre, échelle, miroirs, deuxième imbrication, création par données (section 5.1 bis) | `PortalFacingTest` étendu ; vue, collision et son cohérents sur chaque cas |
| **V7 Mod de terrain** | Livrée sans module dédié : détection générique du terrain étranger, lumière par `readLightData`, projection oblique | Franchie : vues identiques à vanilla sur le banc (`docs/PORTALS.md`, limites connues) |
| **V8 Mod de shaders** | Livrée : détection générique du pack (`ShaderPackState`), `ShaderPackPortalRenderer` (cas B et C de la section 7, par le pipeline du pack) et cas A (`compat/iris`, 29 octobre 2026 : le rendu du mod remplace le pack dans les Backrooms) | Chaque cas restant prototypé et mesuré avant d'être annoncé |

L'ordre de V5 à V8 est ajustable selon les besoins du contenu (un Level 1 peut appeler V6 plus tôt) ; la porte de V1 ne l'est pas.

### 4.7 Critères mesurables du moteur

La couverture à 100 % est la règle du dépôt pour tout ce qui n'est pas de la colle GL ou AL, et elle reste ; mais elle ne mesure ni le rendu ni le son. Chaque version ajoute donc des critères chiffrés, comparés au banc de `docs/PORTALS.md` (table avant/après) :

- **CPU** : sections du moniteur par passe et par niveau de stencil, par source audio et par lot de rayons.
- **GPU** : requêtes de temps GL par passe (lecture différée pour ne pas bloquer), budget par passe et par image à la résolution du banc ; à ajouter à `core/perf/` en V0.
- **Mémoire** : VRAM des attachements et framebuffers (taille × format), tampons des renderers de vue (déjà suivis), nombre d'objets GL, slots et effets EFX.
- **Charge** : lumières par vue, sources traitées, rayons par seconde, allocations par image (zéro attendu).
- **Dégradation** : comportement sans EFX, sans MRT, avec stencil refusé ; chaque chemin de repli exercé par un test ou une scène.
- **Non-régression visuelle et sonore** : captures de référence comparées avec tolérance ; paramètres EFX attendus par scène.
- Les exemptions de couverture (colle GL et AL) sont nominatives dans `docs/TESTING.md`, jamais implicites.

### 4.8 Organisation des sessions de travail sur ce chantier

Ce que les sessions de développement font bien ici : tenir tout le dépôt en tête, lancer le build complet et lire ses rapports, écrire mixins et tests dans les conventions du projet, mener deux pistes en parallèle dans des sessions séparées, et lire une capture d'écran qu'on leur donne en fichier. La quantité de code n'est pas le facteur limitant.

Ce qu'elles ne voient pas : l'écran en direct, le GPU, le ressenti. **La boucle de validation humaine en jeu est le goulot d'étranglement**, pas l'écriture. D'où :

- une session = un jalon, jamais « V1 à V8 » ;
- chaque jalon produit quelque chose que l'auteur vérifie en moins de cinq minutes : scène de test chargée par `-PquickPlayWorld`, commande, consigne précise de ce qu'il faut regarder ou écouter ;
- chaque vérification laisse une preuve lisible par la session de développement : capture PNG nommée, export d'état d'image par commande de debug, lignes du moniteur, et pour le rendu une capture RenderDoc dont l'export texte des passes se lit ;
- les symptômes se décrivent précisément (« les bords du cadre scintillent au niveau 1 quand je recule ») ; une capture vaut mieux qu'un adjectif.

---

## 5. Fonctionnalités à implémenter

Chaque fonctionnalité a sa **définition de « terminé » (DoD)**. « Existe » renvoie au système de portails actuel.

### 5.1 Portails : inventaire de l'existant et besoins d'exposition

**Inventaire des fonctionnalités existantes** (référence : `docs/PORTALS.md` ; les tests qui les verrouillent y sont listés). Toute évolution du rendu ou de l'audio doit les laisser intactes : c'est la définition de « terminé » implicite de chaque jalon.

| Fonctionnalité | Où |
|---|---|
| Rendu de l'autre côté sans flash ni image noire, par les deux faces | `PortalStencilRenderer`, `FrameAreaRenderer` |
| Vues imbriquées (un niveau), vue secondaire pour les portails qui ramènent dans le monde du joueur | `PortalStencilRenderer`, `RemoteWorldRegistry` |
| Portails inter-dimensions (Overworld ↔ Level 0) et intra-dimension (son, vue) | `PortalTargets`, `BackroomsPortalBlockEntity` |
| Découpe au plan d'arrivée (clipping frontal) | `ShaderClipInjector`, `PortalClipping` |
| Culling par le volume visible à travers le cadre | `ViewCone`, `ViewSectionWalker` |
| Plusieurs mondes chargés côté client | `RemoteWorld`, `RemoteWorldRegistry` |
| Chunks, blocs, entités et météo de la destination envoyés par le serveur | `PortalViewTracker`, `RemoteEntityView`, payloads `portal_remote_*` |
| Traversée sans écran de chargement, prédite par le client, confirmée par le serveur | `ClientPortalCrossing`, `TeleportExecutor` |
| Continuité physique : élan, regard, animations, collision à travers le cadre | `TeleportExecutor`, `PortalCollision`, `EntityHandover` |
| Objets, mobs et projectiles qui traversent ; mobs qui voient et poursuivent à travers | `TeleportExecutor`, `PortalPursuit` |
| Interaction à travers le cadre : viser, frapper, miner, poser, clic molette | `RemoteTargeting`, `RemoteActions`, `RemoteInteractionHandler`, `RemoteMiningTracker` |
| Synchronisation multijoueur, spectateurs, changement de dimension vanilla | `sync/`, `PortalSight` |
| Création : bloc portail et réglages (taille, lumière, faces, aller simple, sortie fugace, sens de sortie, arrivée au hasard, plan au milieu ou au bord du bloc), baguette qui les règle et pose, commandes de dev | `block/`, `PortalSettings`, `PortalWandItem`, `client/wand/`, `DebugPortalCommand` |
| Atmosphère distante : ciel, brouillard, lightmap, météo, particules de l'autre monde | `RemoteViewRenderer`, `ViewLightmaps`, `RemoteParticles` |
| Son, particules, lumière des blocs et regard des mobs qui traversent | `FrameSoundPlayer`, `RemoteParticles`, `PortalLightTransmission`, `PortalPursuit` |
| Entités à cheval sur un cadre dessinées en deux moitiés ; troisième personne derrière un cadre | `StraddlingEntityRenderer`, `ThirdPersonPortal` |
| Coupe-circuit et mesure | `PortalRenderState`, `core/perf/` |

**Ce que les nouveaux systèmes ont besoin de voir exposé**

| Besoin | État | À faire |
|---|---|---|
| Transformation d'un point ou d'une direction par le passage | Existe (`toDestination`, `fromDestination`, `PortalFacing`) | Exposer aux rayons audio |
| Caméra et plan de découpe d'une vue | Existe (`PortalViewTransform`) | Exposer matrices inverses par vue pour les passes |
| Niveau de stencil courant et ciseau du cadre | Existe (interne à `PortalStencilRenderer`) | Point d'accroche « après la géométrie opaque d'une vue » pour la `LightPass` |
| Événement de bascule de monde principal | Existe (`ClientPortalCrossing.switchWorld`) | Notifier les états par monde (exposition, bruit, zones) |
| Mondes distants chargés | Existe (`RemoteWorldRegistry`) | Lecture pour les rayons audio et les lumières vues |
| Son distant rejoué au cadre | Existe (`FrameSoundPlayer`, `PortalSound`) | Accrocher le routage EFX, la position virtuelle, le filtre d'ouverture |
| Relais serveur des sons | Existe (`relayToViewers`) | Enrichir si nécessaire (matériau émetteur) |
| Lumière des blocs transmise | Existe (`PortalLightTransmission`) | Compléter par les lumières dynamiques transmises (section 6) |
| Deuxième niveau d'imbrication | Limite connue (un niveau) | Hors périmètre initial ; les passes doivent accepter N niveaux |

### 5.1 bis Évolutions du système de portails (cibles, absentes du dépôt aujourd'hui)

Le système actuel relie l'Overworld et le Level 0 par des cadres verticaux posés par le bloc portail ; l'entité portail connaît douze orientations axiales (`PortalFacing` : six, dont haut et bas, et leurs retournées pour une arrivée tête en bas) et le passage tourne d'une orientation à l'autre, sans angle libre ni échelle. La vision du projet (niveau ↔ niveau, géométrie non euclidienne) demande ces extensions, à jalonner séparément et à faire **avant** ou **avec** le rendu et l'audio selon les besoins de chaque jalon. Chacune touche `PortalFacing`, `PortalViewTransform`, `TeleportExecutor`, `PortalCollision` et les tests qui les verrouillent (144 paires d'orientations dans `PortalFacingTest`).

| Fonctionnalité | DoD |
|---|---|
| Portails niveau ↔ niveau | Deux dimensions backrooms reliées ; `PortalTargets` généralisé par dimension ; sync et relais inchangés |
| Portails intra-dimension à placement libre | Boucles, couloirs infinis, pièces plus grandes que leur extérieur ; vue secondaire déjà existante réutilisée ; collision et son cohérents |
| Rotation libre du passage | Passage avec une rotation quelconque (pas seulement 180° autour de H) ; portails inclinés ou au sol ; prédiction client et détection serveur généralisées (les portails ne sont plus supposés verticaux) |
| Échelle | Passage avec facteur d'échelle ; vitesse, portée, son (hauteur) et lumière adaptés ; aucune sortie dans un mur |
| Miroirs | Optionnel ; même machinerie de vue avec une réflexion, écriture correcte des faces |
| Deuxième niveau d'imbrication | Niveau de stencil 3 ; dégradation propre au-delà ; budget mesuré |
| Création par données | Portails définis en datapack ou par API Java (position, orientation, destination, réglages), en plus du bloc portail |
| Rendu de l'atmosphère distante | Existe (ciel, brouillard, lightmap) ; vérifier avec rotation et échelle |

### 5.2 Système de rendu

**Infrastructure**

| Fonctionnalité | DoD |
|---|---|
| Attachements MRT du framebuffer principal | Créés, redimensionnés avec la fenêtre, libérés ; coupe-circuit si le pilote refuse |
| Injection de sorties G-buffer dans les fragment shaders vanilla | Tous les shaders de terrain, d'entités et de block entities du jar couverts (test sur le jar, comme `ShaderClipInjectorTest`) |
| Gestionnaire de shaders propres (passes) | Chargement depuis les ressources, `#include`, defines et variantes, rechargement par resource reload, erreurs loguées sans crash |
| Framebuffers additionnels en données | Cibles intermédiaires des passes (demi-résolution pour le bloom, par exemple) décrites en JSON : formats, taille relative, redimensionnement |
| Chaîne de post-process en données | Étapes nommées (passe, copie, masque par niveau, cible), ordonnées, par profil ; une chaîne vide pour l'Overworld |
| Uniform buffers / SSBO | Blocs partagés : matrices par vue, liste de lumières, paramètres de profil ; mis à jour sans allocation |
| Profils par monde en données | Un fichier par dimension, chargé au démarrage, rechargeable, valeurs par défaut sûres |
| Passes limitées par niveau de stencil | Aucune fuite hors du cadre pour les effets locaux ; coût mesuré par niveau |
| Restauration d'état GL | État identique avant et après chaque passe (test de debug qui le vérifie en dev) |
| Overlay de debug | Niveaux de stencil, lumières, G-buffer visualisable |
| Éditeur en jeu | Optionnel, dev seulement (`isDevelopmentEnvironment()`) : réglage des profils, des lumières et des passes en direct, rechargement des shaders |

**Éclairage**

| Fonctionnalité | DoD |
|---|---|
| Passe d'éclairage différé par niveau | Lumières du monde de la vue, dans l'espace caméra de la vue ; correct au niveau 0, 1 et 2 |
| Lumières ponctuelles et de zone (néons) | Ajout/retrait à chaud, attachées à un bloc, une entité ou une position ; culling par cadre |
| Ombres | Au moins une lumière à ombre par vue, optionnelles au-delà ; budget mesuré |
| Lumières attachées aux entités | Suivent l'interpolation affichée (`lerp(tickDelta, …)`), jamais la position du tick |
| Volumétrique léger | Brouillard éclairé par monde, désactivable |
| Matériaux par bloc | Émissif, rugosité, réflectivité via tags ; **partagés avec l'audio** (section 6.3) |
| Lightmap et brouillard par monde | Existent ; pilotés par le profil (courbe, teinte, scintillement) |

**Effets**

| Fonctionnalité | DoD |
|---|---|
| Chaîne de post par monde | Tonemap, grain, teinte, vignette, aberration, VHS ; chacun activable par profil |
| Exposition adaptative par monde | Aucun saut mesurable au Seam (capture avant/après identique à la tolérance près) |
| Effets caméra (état du joueur) | Appliqués à tout l'écran, montée progressive près d'un cadre (hystérésis) |
| Scintillement et pannes | Pilotés par le jeu, synchronisés avec l'audio (section 6.3) |
| Particules éclairées | Lues par le chemin forward léger |
| Système de particules avancé | Émetteurs en données (forme, durée, courbes), particules éclairées et éventuellement émissives, vivant dans le contenu de particules de leur monde (`RemoteParticles`) |
| Modèles animés | Optionnel : squelettes et animations par clés pour les entités spéciales, rendu compatible avec le G-buffer |
| Animations et easing | Timeline et courbes pour les transitions d'effets (montée d'un effet caméra, extinction d'une zone) ; utilitaire partagé par rendu et audio |
| Couleurs et thèmes | Palette par profil (teinte des néons, du brouillard, du tonemap) ; une palette par niveau |

### 5.3 Système audio

**Infrastructure**

| Fonctionnalité | DoD |
|---|---|
| Accès aux sources OpenAL | Chaque source créée par le moteur de son est interceptée ; aucun son non positionné (UI, musique) n'est traité |
| EFX | Détection, initialisation, repli propre si indisponible (son vanilla inchangé) |
| Slots multiples | Un jeu de slots par monde (décroissances courtes à longues) ; sources routées par monde d'origine |
| Filtres par source | Passe-bas direct et par send, gain, atténuation de chemin |
| Traitement asynchrone | Rayons hors du thread de rendu, résultats appliqués sans allocation par image |
| Liste d'inclusion des sons | Quels sons sont traités et lesquels sont exemptés (interface, musique, sons globaux), par tags ou par données ; `/playsound` traité comme les autres |
| Configuration | Propriétés `-D` comme `ModConfig` pour les toggles ; profils en données pour les valeurs par monde |
| Debug | Rayons, rebonds, occlusion, routage par source, visibles en jeu |

**Physique**

| Fonctionnalité | DoD |
|---|---|
| Occlusion par rayons | Source → oreille avec facteurs par bloc ; variation douce |
| Matériaux (absorption, réflectivité, occlusion) | Table par tags, partagée avec le rendu |
| Rebonds multiples | Estimation de taille et de dureté de volume → paramètres de réverbération |
| Direction perçue | Le son vient de l'ouverture, pas du mur |
| Réévaluation continue | Boucles et sons longs recalculés périodiquement |
| Position caméra | Tous les calculs depuis la caméra, comme le reste du projet |
| Milieux | Sous l'eau et autres, filtrage adapté |
| Absorption de l'air | Hautes fréquences atténuées avec la distance |
| Voix | Point d'accroche pour un mod de chat vocal (sources traitées comme les autres), via un module de compatibilité (à écrire) |

---

## 6. Fonctionnalités d'imbrication entre systèmes

### 6.1 Rendu × Portails

- Passes par niveau de stencil : chaque vue éclairée et post-traitée selon son monde, dans le framebuffer principal (3.2).
- Exposition et tonemap par monde : pas de saut au Seam.
- **Lumières dynamiques transmises** : comme la lumière des blocs (`PortalLightTransmission`), une lumière proche d'un cadre crée une lumière virtuelle de l'autre côté, image par le passage, atténuée ; les néons du Level 0 éclairent l'herbe devant le portail.
- Ombre du cadre : la lumière d'un monde projette au moins l'ombre du cadre de l'autre côté (version simple), ou une vraie ombre à travers (version avancée).
- Effets qui débordent (bloom) : décision par effet ; les effets locaux ne fuient jamais.
- **Marge d'échantillonnage** : les effets qui lisent des pixels voisins (SSAO, bloom, flous) ne doivent pas tirer de pixels hors du niveau de la vue ; soit le ciseau est élargi d'une marge et les pixels hors masque sont clampés, soit l'échantillonnage vérifie le niveau de stencil (ou un masque équivalent) avant de lire.
- **Brouillard cohérent** : le brouillard d'une vue est celui de son monde (déjà le cas), et aucune passe de post ne réapplique un brouillard par-dessus à la composition ; vérifier le fondu du monde du joueur par-dessus la vue (translucides) après l'ajout des passes.
- Résolution adaptative des passes par vue selon la taille du cadre à l'écran.
- Reflets écran (SSR) par vue : un sol mouillé reflète ce qu'on voit par un cadre.

### 6.2 Audio × Portails

- `SourceRoute` complète le rejeu au cadre : position virtuelle pour la direction, distance de chemin pour le gain, filtre d'ouverture, slots du monde d'origine et part du monde de l'auditeur.
- Rayons qui traversent : occlusion et rebonds continuent dans le monde d'arrivée, transposés.
- Mon propre écho : près d'un cadre, les pas du joueur reviennent avec la réverbération de l'autre monde.
- Acoustique non euclidienne : dans une boucle intra-dimension, les rayons revenus par le cadre donnent une réverbération cohérente avec la géométrie impossible.
- Ambiances positionnelles (bourdonnement, ventilation, gouttes) : des sources placées dans le monde, jamais globales ; elles traversent donc les cadres par le chemin existant.
- Hystérésis près d'un cadre pour tout effet lié à l'état du joueur.

### 6.3 Rendu × Audio

- **Table de matériaux unique** par bloc (tags) : émissif et rugosité pour l'œil, absorption et occlusion pour l'oreille.
- Lumières réactives : scintillement des néons et grésillement issus du même générateur de bruit.
- Panne couplée : une zone qui s'éteint change d'acoustique (ventilation coupée).
- `AcousticZone` et zones d'atmosphère définies par les mêmes volumes.
- Overlay commun : rayons audio dessinés avec les lumières.

### 6.4 Les trois ensemble : le Seam

- Un seul événement de bascule (`switchWorld`) écouté par rendu, audio et gameplay.
- Au Seam : aucun rechargement de shader, aucune coupure audio, aucune exposition brutale.
- No-clip : entrée par chute à travers le sol avec glitch visuel et sonore contrôlés par les deux systèmes.

### 6.5 Idées de niveaux rendues possibles par l'imbrication

Courtes pistes de contenu qui exploitent directement les trois systèmes.

- Un couloir qui boucle sur lui-même (portail intra-dimension), dont l'écho revient de derrière le joueur.
- Une pièce dont la lumière se voit depuis l'Overworld avant que le cadre soit identifiable (lumière dynamique transmise).
- Un niveau sans lumière où seule l'acoustique guide le joueur (navigation à l'oreille).
- Des portails à échelle réduite : une maquette où le son devient aigu et lointain.
- Un niveau « mouillé » : reflets au sol, réverbération longue, néons qui grésillent sous l'eau.
- Une sortie fugace (existe) couplée à un son qui ne s'entend que tant qu'on la regarde.

---

## 7. Cohabitation avec un pack de shaders

Question posée : un joueur utilise un pack de shaders dans l'Overworld ; en passant dans le Level 0, le shaderpack doit se désactiver **sans friction** et laisser la place à notre pipeline, et l'autre côté du portail doit rester visible **avec son rendu**. Voici ce qui est réaliste, ce qui ne l'est pas, et dans quel ordre.

### 7.1 Comment fonctionne le mod de shaders (à vérifier sur la version visée)

- Le mod de shaders gère **un pipeline par dimension** : à chaque image, il choisit le pipeline de la dimension de `client.world` (préparation interne du pipeline), le crée la première fois (compilation des programmes, un à-coup) et le **garde en cache** pour la suite. Sans pack actif, le pipeline est un pipeline « vanilla » trivial. Un shaderpack peut déclarer des dossiers par dimension (`dimension.properties`) ; une dimension inconnue retombe sur le dossier par défaut. Au moins un mod tiers propose déjà de désactiver les shaders dans certaines dimensions ou d'en changer par dimension : ce point d'accroche existe et fonctionne.
- Le mod de shaders **remplace les shaders vanilla** par les programmes du pack : `ShaderClipInjector` ne les touche pas ; la découpe d'une vue passe alors par la projection à plan proche oblique et par le rognage des entités sur le processeur (section 2.2).
- Le mod de shaders rend le monde dans **ses propres framebuffers** (G-buffers, ombres, composites) puis une **passe finale** écrit dans le framebuffer principal. Pendant le rendu du monde, le stencil du framebuffer principal n'est pas celui dans lequel on dessine. Les vues se dessinent donc **après la passe finale**, chacune par le rendu complet de son renderer dans le pipeline du pack pour sa dimension, sous le stencil de son cadre, l'image du joueur gardée puis remise autour (`ShaderPackPortalRenderer`). Les pièges rencontrés sont listés dans les règles du projet (pack de shaders).
- Le mod de shaders **exige un mod de rendu du terrain**, qui réécrit le pipeline de terrain ; les vues sont supportées avec lui (section 2.2).
- Le mod de shaders expose une petite API publique (pack actif ou non, passe d'ombre en cours, ouverture de son écran, activation globale avec rechargement) ; la sélection du pipeline par dimension est interne. Un pack est reconnu sans elle et sans nommer de mod (`ShaderPackState`, section 2.4) ; le module `compat/iris` lit la sélection interne (gestionnaire de pipelines, réglages globaux du pipeline) pour le cas A, dans une plage de versions vérifiée.

### 7.2 Prérequis : les vues sous le mod de terrain

Franchi : `RemoteViewRenderer` détecte le terrain dessiné par un autre mod et le pilote sans module dédié (section 2.2). Il n'y a pas de module de compatibilité dédié à ce mod, et rien ne l'exige tant qu'un besoin propre à ce mod n'apparaît pas (section 3.2, septième inconnue).

### 7.3 Les trois cas

**Cas A — Passer de l'Overworld (sous un pack de shaders) au Level 0 (notre pipeline) : réalisé le 29 octobre 2026 (`compat/iris`).**
Tant que le joueur est dans une dimension à look, le gestionnaire de pipelines du mod de shaders rend son pipeline vanilla pour tout ce qui se dessine, vues comprises ; la bascule se fait au rendu du monde du joueur, réglages globaux de chaque pipeline gardés et reposés, terrain reconstruit (image de 96 à 117 ms en entrant, 52 ms en revenant), pipeline du pack repris du cache sans recompilation. Écarts au texte ci-dessous, écrit avant le prototype : le mod de terrain n'a qu'un format de maillage à la fois, d'où un seul pipeline par image (l'Overworld vu depuis le Level 0 est vanilla) et la reconstruction du terrain à chaque bascule ; hors de la plage vérifiée, le module ne s'applique pas (message traduit à la connexion) et le pack rend aussi le Level 0. Texte d'origine : aujourd'hui le pack rend aussi le Level 0 (un pack sans dossier dédié à cette dimension y applique son dossier par défaut), et c'est ce que l'auteur teste ; couper le pack dans nos dimensions n'a de sens qu'avec un pipeline propre à y mettre (section 3.2). Si l'option est retenue : Le module de compatibilité du mod de shaders (retiré le 28 octobre 2026 au soir, section 2.4) fait choisir à ce mod, pour nos dimensions, son pipeline **vanilla** au lieu du pipeline du pack. Comme le mod de shaders sélectionne le pipeline à chaque image d'après `client.world`, et que `ClientPortalCrossing` échange ce monde avant le rendu, l'image qui suit le Seam est déjà rendue sans le pack : nos shaders vanilla augmentés (découpe, G-buffer) et nos passes s'appliquent. Le retour vers l'Overworld reprend le pipeline du pack, déjà en cache : pas de recompilation, pas d'à-coup. À prévoir : **pré-chauffer** le pipeline de l'Overworld si c'est la première dimension visitée dans la partie (par exemple à la création du monde distant, qui a déjà un à-coup assumé), et vérifier les états que le mod de shaders garde entre deux images (ombres, captures de matrices) au changement de pipeline. Ce point d'accroche est interne au mod de shaders : le module est écrit pour une plage de versions précise, détecte une incompatibilité au démarrage et se désactive proprement avec un message.

**Cas B — Depuis l'Overworld sous un pack de shaders, voir le Level 0 dans le cadre : réalisé, par le pipeline du pack.**
La vue du Level 0 est un rendu complet de son renderer dans le pipeline du pack pour cette dimension, après la passe finale, composé sous le stencil du cadre, l'image du joueur gardée puis remise autour (`ShaderPackPortalRenderer`). Limites constatées (`docs/PORTALS.md`, limites connues) : un cadre derrière l'eau ou le verre n'est pas dessiné ; une entité à cheval ne projette pas d'ombre ; le ciel de la vue est délavé quand un obstacle derrière l'arrivée impose la projection oblique ; l'œil du pack est pris au portail d'arrivée ; chaque vue coûte un rendu complet du pack. La variante « vue rendue par notre pipeline, composée après le pack » reste possible si la section 3.2 aboutit : framebuffer dédié, nos shaders augmentés et nos passes, masque par la géométrie du cadre (`FrameAreaRenderer`) testée contre la profondeur du pack, puis profondeur du cadre écrite pour la main et le reste ; translucides devant le cadre passés derrière la vue, effets du pack qui ne traversent pas le cadre.

**Cas C — Depuis le Level 0, voir l'Overworld dans le cadre avec le shaderpack : réalisé.**
Ce que la première version de ce guide tenait pour non réaliste (exécuter le pipeline complet du pack pour une caméra secondaire) est ce que fait `ShaderPackPortalRenderer` : le mod de shaders tient un pipeline par dimension et le choisit d'après `client.world` en tête de `WorldRenderer.render` ; le monde, le renderer, la caméra et l'entité caméra du client sont ceux de la vue le temps de son rendu complet, puis rendus. Mêmes limites que le cas B. Le pipeline d'une dimension vue est créé à sa première vue (compilation des programmes, un à-coup, puis cache) ; un étage de shader déjà compilé n'est redonné qu'aux programmes chargés par la même fabrique de ressources (`MixinShaderProgram`), sans quoi le second pipeline recevait les programmes du premier.

### 7.4 Résumé

| Situation | Résultat | État (vérifié au banc de production, ou à prototyper) |
|---|---|---|
| Sans mod de shaders (vanilla, ou mod de terrain seul) | Vues et passage sans couture (profils par monde : à venir) | Oui, vérifié |
| Overworld sous un pack → entrée dans le Level 0 | Le rendu du mod remplace le pack (pipeline vanilla du mod de shaders), terrain reconstruit à la bascule | Oui, vérifié (29 octobre 2026) |
| Overworld sous un pack, Level 0 vu dans le cadre | Vue rendue par le pack, composée après sa passe finale | Oui, vérifié ; limites de la section 7.3 |
| Level 0, Overworld vu dans le cadre | Overworld vanilla dans le cadre (une image, un pipeline) ; option `replaceShaderPack` coupée : sous le pack | Oui, vérifié ; limites de la section 7.3 |
| Level 0 → retour dans l'Overworld | Pack repris sans recompilation, terrain reconstruit | Oui, vérifié : pipeline du pack en cache, image de 52 ms |

### 7.5 Décisions de produit

- Option `gtgsbackrooms.render.shaderCohabitation` : `auto` (ci-dessus), `views_vanilla` (vues toujours vanilla, même sous un pack), `off` (sous un pack, cadres sombres, comportement actuel).
- Contrôle d'une plage de versions supportée du mod de shaders : pour le seul module `compat/iris` (`IrisVersion`) ; hors de la plage, ses mixins ne s'appliquent pas, un message traduit prévient le joueur à chaque connexion (`IrisVersionNotice`) et le pack rend aussi les Backrooms. Les vues sous pack n'en dépendent pas : un pack est reconnu par une détection générique (`ShaderPackState`).
- `docs/RENDERING.md` décrit les trois cas et leurs limites avec les mêmes mots que ce guide, pour que la documentation joueur et la documentation technique disent la même chose.

---

## 8. Mods dont la compatibilité est à surveiller

Classés par risque. « Conflit » : coexistence improbable ; « à tester » : intégration possible avec travail. Les limites déjà documentées dans `docs/PORTALS.md` sont le point de départ.

### 8.1 Rendu — risque élevé

| Famille de mods | Risque | Note |
|---|---|---|
| **Mod de rendu du terrain** | Supporté (banc) | Réécrit le terrain ; les vues le pilotent sans module dédié ; prérequis du mod de shaders |
| **Mod de shaders** | Supporté avec pack (banc, deux packs) | Section 7 ; pack reconnu par `ShaderPackState` ; dans les Backrooms, le rendu du mod remplace le pack (`compat/iris`, cas A, 29 octobre 2026) |
| **API de rendu de blocs pour le mod de terrain** | À tester | Utile aux mods de rendu de blocs |
| **Mod de rendu lointain** | À tester | Terrain lointain, suit `joinWorld` : ne voit pas une traversée par portail (limite connue) |
| **Rendu très intrusif du terrain** | Conflit probable | Rendu très intrusif |
| **Mod d'élimination des entités cachées** | Faible (banc actuel sans écart) | Doit ignorer les entités visibles par un cadre si des faux négatifs apparaissent |
| **Mod de textures connectées** | Faible | Textures connectées |
| **Bibliothèques de post-process tierces** | À tester | Chaînes concurrentes en fin d'image |
| **Mod de VR** | Conflit probable (mode VR) | Mode écran passe le banc ; stéréo × vues non prévu |
| **Mod d'enregistrement de parties** | Faible (banc actuel) | Caméras libres : toutes les vues suivent la caméra, déjà le cas |
| **Caméras libres et caméras tierces** | À tester | Idem |
| **Mod de physique des entités** | À tester | Rendu d'entités physiques |
| **Mods de particules d'ambiance** | Faible | Particules supplémentaires, à éclairer par le chemin forward et à placer dans le contenu de particules de leur monde |
| **Mods de lumière dynamique** | Faible (banc actuel) | Redondants avec nos lumières ; prévoir une désactivation dans nos dimensions |

### 8.2 Audio — risque élevé

| Famille de mods | Risque | Note |
|---|---|---|
| **Mods d'acoustique tiers** | Conflit | Mêmes points d'accroche ; `breaks` dans `fabric.mod.json` (il les nommerait, section 2.4), ou désactivation mutuelle |
| **Mods de chat vocal** | À tester | Sources de voix à router (par un module de compatibilité, à écrire) |
| **Mod d'ambiances sonores** | À tester | Ambiances globales qui ignorent les cadres |
| **Mod d'effets d'environnement** | À tester | Effets concurrents |
| **Mod de bruits de pas** | À tester | Pas à router |
| **Mods de sons supplémentaires** | Faible | Sons supplémentaires, à router |

### 8.3 Portails et mondes

| Famille de mods | Risque | Note |
|---|---|---|
| **Un autre mod de portails** | Conflit | Même domaine ; `breaks` (il le nommerait, section 2.4) |
| **Mods de minimap et de carte** | Faible (banc actuel) | Cartes par dimension ; vérifier après une traversée prédite |
| **Mod de cache de chunks** | Faible (banc actuel) | Cache de chunks ; vérifier le multi-mondes |
| **Mod technique du serveur** | Faible | Tick serveur |
| **Pré-générateurs de chunks** | Faible | Dimensions custom ; génération déterministe du Level 0 inchangée |

### 8.4 Performance et infrastructure

| Famille de mods | Risque | Note |
|---|---|---|
| **Mod d'optimisation serveur** | Faible (banc actuel) | Vérifier relais, téléportation et blocs cachés derrière le plan d'un cadre (il peut remplacer le parcours vanilla des collisions de blocs) |
| **Mod de chargement asynchrone de chunks** | À tester | Chargement asynchrone, mondes multiples |
| **Mod réseau** | Faible | Réseau ; paquets de relais |
| **Mods d'optimisation mémoire** | Faible | Mémoire ; attachements MRT, renderers de vue |
| **Mod de limitation des images hors focus** | Faible | Rendu ralenti hors focus |
| **Fabric API** | Obligatoire | Suivre les versions ; ne pas appeler `setupTerrain` sur un renderer de vue (évènements Fabric) |
| **Interface de configuration et liste des mods** | Faible | Si une interface de config est ajoutée ; sinon rester en `-D` comme aujourd'hui |
| **Bibliothèque multi-chargeurs** | Faible | Seulement si un port NeoForge est un jour envisagé ; hors périmètre |

### 8.5 Stratégie

- Modules de compatibilité optionnels, `modCompileOnly`, chargés par détection ; `verifyJarContents` garantit qu'aucun n'est embarqué (`compat/iris` depuis le 29 octobre 2026 ; aucune dépendance de mod à l'exécution hors Fabric Loader et Fabric API, section 2.4).
- Conflits durs déclarés dans `fabric.mod.json` (`breaks`) avec message (ils nommeraient un autre mod, section 2.4).
- Matrice par version : vanilla seul, + mod de rendu du terrain, + mod de rendu du terrain + mod de shaders (modes A/B), + mod de chat vocal. Les JAR tiers vont dans `run/mods`, jamais dans le dépôt.
- Coupe-circuits comme `PortalRenderState` : rendu avancé ou audio avancé coupés pour la session si le pilote refuse ou si une exception survient, avec log.

---

## 9. Passage sous GPL-3

État actuel : licence propriétaire, tous droits réservés, `LICENSE` copiée dans les JAR, badge « All Rights Reserved » dans le README, règle « aucun code ni ressource tiers » dans les règles du projet. Le passage en **GPL-3.0-or-later** (ou GPL-3.0-only ; « or-later » est plus courant) est envisagé ; **décision reportée le 2026-10-20** : la licence actuelle reste jusqu'à une décision explicite de l'auteur, et cette section décrit la marche à suivre le jour venu.

Ce guide organise le travail ; il ne vaut pas validation juridique. La méthode clean room réduit le risque, elle ne le supprime pas. Avant la première publication sous GPL-3, un examen séparé est fait sur trois points : le code (origine, absence de reprise), les ressources (origine de chaque texture, son et texte) et les contributions externes éventuelles.

### 9.1 Ce que ça change, et ce que ça ne change pas

- Les **idées, principes et algorithmes** ne sont pas protégés par le droit d'auteur ; les réimplémenter est libre. L'**expression** (le code) l'est. Un code neuf, écrit à partir de principes compris, est une œuvre originale dont l'auteur a tous les droits : **aucune mention de tiers n'est requise.**
- Cette absence d'obligation tient tant qu'aucun code tiers n'est repris. La règle correspondante des règles du projet et la méthode de la section 4 la garantissent. Si un bloc tiers était un jour intégré, son attribution deviendrait obligatoire.
- La GPL-3 est compatible avec les licences des mods de référence (GPL-3, LGPL-3) : une ressemblance fortuite sur un utilitaire ne serait pas un problème de licence, seulement une occasion de réécrire.
- L'auteur garde le **droit d'auteur** et, en France, un droit moral inaliénable (paternité).
- **Forks** : un fork doit rester GPL-3 et conserver les mentions de copyright de l'auteur ; les versions déjà publiées restent GPL-3 pour toujours ; les versions futures peuvent changer de licence tant que l'auteur est seul titulaire des droits.
- **Contributions externes** : les contributions d'autres personnes leur appartiennent, sous GPL-3. Pour garder la liberté de changer de licence plus tard, faire signer un accord de contribution (CLA) ou n'accepter que des contributions mineures ; sinon, toute relicence demande l'accord de tous.

### 9.2 Opérations dans le dépôt

1. `LICENSE` : remplacer le texte propriétaire par le texte intégral de la GPL-3. Il est déjà copié dans les JAR : c'est exactement ce que la GPL demande.
2. `README.md` : badge et section « Licence » (`Copyright (C) 2025-2026 GTGpuke`, GPL-3.0-or-later, lien vers `LICENSE`, absence de garantie).
3. `fabric.mod.json` : `"license": "GPL-3.0-or-later"`, `"authors": ["GTGpuke"]`, lien vers les sources dans `contact`.
4. Règles du projet : remplacer « Licence propriétaire, tous droits réservés » par la nouvelle licence, en gardant « aucun code ni aucune ressource repris d'un projet tiers, quelle que soit sa licence ».
5. `docs/CHANGELOG.md` : entrée explicite du changement de licence.
6. En-têtes de fichiers : optionnels pour la GPL (le `LICENSE` et `fabric.mod.json` suffisent) ; si l'auteur en veut, une ligne ASCII en français, conforme à Checkstyle, dans chaque source.
7. Mention en jeu (programme interactif) : une ligne dans la description du mod (clés `en_us` et `fr_fr`) suffit en pratique.
8. Sources accessibles : dépôt public (déjà le cas) ; publier aussi le JAR `-sources` est une bonne pratique. Sur Modrinth et CurseForge, déclarer GPL-3 et lier le dépôt.
9. Aucune restriction supplémentaire (modpacks, redistribution, forks) : contraire à la GPL.

### 9.3 Dépendances et ressources

- Fabric Loader et Fabric API (Apache-2.0) : compatibles, déclarés en dépendances externes, non embarqués. `verifyJarContents` l'impose déjà : **le JAR ne contient que le mod, donc le seul titulaire à mentionner est l'auteur.**
- Minecraft et LWJGL : fournis par le jeu, pas embarqués ; pratique universelle pour les mods GPL.
- Modules de compatibilité : `modCompileOnly`, jamais embarqués (`compat/iris`, section 2.4).
- Ressources (textures, sons, modèles, textes) : créées par l'auteur, ou **CC0 uniquement** pour ce qui vient d'ailleurs (pas de CC-BY, pas de polices OFL si l'on veut aucune mention). Les textes des wikis backrooms sont sous licence avec attribution : écrire ses propres descriptions ; les concepts (Level 0, bourdonnement, moquette jaune) sont libres. Rien tiré de vidéos ou de jeux identifiés.

---

## 10. Pistes à explorer

### 10.1 Techniques

- **Deuxième niveau d'imbrication** (cible en 5.1 bis) : abandonné le 29 octobre 2026, un portail dans un portail suffit (section 2.4).
- **Éclairage indirect léger** : un rebond approximatif (sondes ou GI écran simplifié) change l'ambiance des couloirs à moquette plus que n'importe quel post-process.
- **Occlusion audio volumétrique** : une grille 3D de densité acoustique par chunk, précalculée, plus stable et moins coûteuse que des rayons seuls, et qui traverse les cadres naturellement.
- **Réverbération par convolution** par zone, à moyen terme.
- **HRTF** via OpenAL Soft pour le casque.
- **Budget dynamique** : résolution des passes et nombre de lumières réduits quand l'image dépasse un seuil (le moniteur de performance sait déjà mesurer). Gardé en piste le 29 octobre 2026 : pas utile aujourd'hui (la qualité se choisit d'après le pilote).
- **Pré-chauffage** : compiler shaders et créer slots EFX à la création d'un monde distant, là où un à-coup est déjà assumé.
- **Rendu différé « fin »** : séparer éclairage direct, indirect et émissif dans des sorties distinctes du G-buffer pour piloter les passes plus précisément (bloom seulement sur l'émissif, par exemple).
- **Charge des mondes clients** : les mondes distants tiqués à chaque tick client et leurs renderers pèsent sur le thread de rendu ; mesurer tôt ce que les lumières et les rayons audio y ajoutent, et déporter ce qui peut l'être (rayons, culling de lumières) hors de ce thread.

### 10.2 Architecture et outillage

- Tout en données et rechargeable à chaud : profils, lumières, zones, chaînes de post.
- Mode éditeur en jeu pour placer lumières et zones et voir le résultat immédiatement.
- Export d'état d'une image (vues, niveaux, lumières, sources, routage) par commande de debug, pour diagnostiquer un rapport.
- Scènes de test chargeables par commande.
- Tests de non-régression visuelle : captures de référence comparées après chaque changement de rendu.
- Versionner les profils et la config en données : migration automatique quand le format change, comme les empreintes de génération sont versionnées par `LevelZeroGoldenTest`.

### 10.3 Expérience

- Le son avant l'image : on doit entendre le Level 0 avant de voir le cadre.
- Cohérence des matériaux : moquette qui absorbe, carrelage qui résonne, néons qui grésillent, pour l'œil et l'oreille.
- Sobriété : la fidélité aux backrooms tient à la banalité inquiétante ; effets caméra rares et justifiés.
- Accessibilité : options pour réduire scintillements, bruit visuel et basses.

### 10.4 Pérennité

- Un mixin par fichier avec sa raison d'être (déjà la pratique) ; les mises à jour se traitent mixin par mixin.
- Modules de compatibilité versionnés et auto-désactivables (`compat/iris` : plage vérifiée, mixins filtrés, section 2.4).
- Documentation vivante : `GUIDE.md`, `ARCHITECTURE.md`, `RENDERING.md`, `AUDIO.md`, `PORTALS.md` tenus à jour ensemble.

---

## 11. Liste de contrôle de démarrage

- [x] Ce guide placé dans `docs/GUIDE.md`, ajouté à `docs/INDEX.md`, bloc de la section 4.2 inséré dans les règles du projet (2026-10-20)
- [x] **Audit de l'existant** sur le dépôt à jour : écarts consignés en section 2.4, guide corrigé (2026-10-20)
- [x] `docs/ARCHITECTURE.md` complété : renvoi à la section 3.4 ; les points d'accès de `portal/` se listent au premier besoin
- [x] `docs/TESTING.md` : exemption de couverture annoncée pour la colle OpenAL client
- [ ] Passage GPL-3 (section 9.2), **reporté** : `LICENSE`, `README.md`, `fabric.mod.json`, règles du projet, `CHANGELOG.md`
- [ ] Notes de conception `docs/design/RENDERING-NOTES.md` et `AUDIO-NOTES.md` produites par des sessions d'étude séparées
- [ ] `docs/RENDERING.md` et `docs/AUDIO.md` créés sur le modèle de `PORTALS.md`, remplis au fil des jalons
- [ ] Sections `client.render.*` et `client.audio.*` dans le moniteur de performance dès le premier jalon
- [ ] Scènes de test dans un monde dédié, **captures de référence stockées avant toute modification du rendu**
- [ ] Mesure GPU (requêtes de temps GL) ajoutée à `core/perf/`, coût actuel du rendu distant mesuré CPU et GPU
- [ ] Matrice de compatibilité définie (vanilla, mod de rendu du terrain, mod de rendu du terrain + mod de shaders, mod de chat vocal) et budgets de performance fixés (temps par passe et par niveau, coût des rayons)
- [ ] Contrats partagés fixés (V0) : `WorldProfile` minimal, table des matériaux, sections perf, points d'accès de `portal/`
- [ ] **V1, jalon bloquant** : attachement MRT ajouté au framebuffer principal, une donnée écrite par injection et visualisée par une passe de debug au niveau 0 puis au niveau 1 ; les six points de la section 3.2 vérifiés
- [ ] Premier jalon audio : une source d'un monde distant rejouée au cadre routée vers le slot EFX de son monde
- [ ] Décision sur le cas A (section 7.3) et sur l'option `shaderCohabitation` ; l'ordre mod de rendu du terrain → mod de shaders est sans objet, les deux sont supportés
