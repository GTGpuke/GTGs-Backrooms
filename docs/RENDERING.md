# Rendu des Backrooms (moteur de shaders maison)

> Spécification de référence : `docs/backrooms/MOTEUR_SHADERS.md` (feuille de route et hypothèses). Ce document décrit
> ce qui est **livré**, phase par phase, et ce qui reste ouvert. Les écarts à la spécification sont consignés dans
> `docs/GUIDE.md`, section 2.4 (27 octobre 2026).

## En jeu

Chaque dimension qui a un **profil de look** (`assets/gtgs_backrooms/look_profiles/<dimension>.json`) a son rendu : une
teinte et une désaturation, un grain et un léger vacillement de la lumière, une courbe de lightmap (plancher, plafond,
chaleur) et un brouillard à elle (couleur et distances). Aujourd'hui le seul profil est celui du Level 0 (`level_0.json`).

Le look suit la règle unique de la spécification : **le rendu d'un pixel dépend de la dimension qu'il montre, pas de
celle où se trouve le joueur**.

- Dans le Level 0, tout l'écran a le look du Level 0, main comprise ; l'interface (barre d'outils, chat, F3) reste
  vanilla. **Sous un pack de shaders (Iris), le rendu du mod remplace le pack dans le Level 0** : tant que le joueur y
  est, tout se dessine par nos shaders, vues de portail comprises (l'Overworld vu de là est alors vanilla), et le pack
  reprend en sortant, sans recompilation (`compat/iris`, option `gtgsbackrooms.look.replaceShaderPack`).
- Depuis l'Overworld, un portail vers le Level 0 montre le look **dans le cadre seulement** ; le reste de l'écran, et
  tout l'Overworld sans portail, est strictement vanilla.
- Depuis le Level 0, un portail vers l'Overworld montre l'Overworld vanilla, même dans une vue imbriquée.
- Une dimension sans profil (Overworld, Nether, End, et toute dimension d'un autre mod) n'a aucun look : sa couleur
  ressort des shaders telle quelle.

Les profils et les patchs se rechargent avec les ressources (F3+T) : on règle un profil sans relancer le client.

Options (`ModConfig`, propriétés `-D`, passées au client de dev par `-P`) :

| Propriété | Défaut | Effet |
|-----------|--------|-------|
| `gtgsbackrooms.look.enabled` (`-PlookEnabled`) | `true` | Moteur de shaders. Coupé : aucun shader patché, aucun profil chargé, rendu vanilla partout (comparaison au banc). |
| `gtgsbackrooms.look.debugStencil` (`-PlookDebugStencil`) | `false` | En fin de rendu du monde, l'image est remplacée par le stencil : une couleur par niveau d'imbrication (gris, bleu, vert, rouge), éclaircie et jaunie par le look de chaque pixel. |
| `gtgsbackrooms.look.quality` (`-PlookQuality`) | `auto` | `light` : huit néons par vue, pas de bloom ; `full` : trente-deux néons, bloom ; `auto` : `full`, sauf sur une carte graphique intégrée (chaîne `GL_RENDERER` du pilote contenant `intel`, `llvmpipe` ou `swiftshader`), `light`. Le choix est journalisé (« [Look] Qualite ... »). |
| `gtgsbackrooms.look.replaceShaderPack` (`-PlookReplaceShaderPack`) | `true` | Sous un pack de shaders (Iris), dans une dimension à look, le rendu du mod remplace le pack. Coupée : le pack rend aussi les Backrooms, le look se pose sur son image. |
| `gtgsbackrooms.look.reduceNoise` (`-PreduceNoise`) | `false` | Confort : le look des Backrooms sans bruit visuel (ni grain, ni vacillement de la lumière de l'écran, ni aberration chromatique) ; teinte, lumière, brouillard, vignette et bloom restent. L'option `gtgsbackrooms.audio.reduceFlashes` coupe en plus le vacillement de la lumière et celui des tubes (leur part ramenée à zéro, bourdonnement compris). |
| `gtgsbackrooms.look.checkGlState` (`-PlookCheckGlState`) | `false` | Développement : chaque passe du moteur (post, post sous un pack, effacement du stencil, débogage du stencil, bloc des néons) relit l'état GL avant et après elle et journalise une fois chaque écart (« [Look] La passe ... ne rend pas l'etat GL tel quel »). Les lectures attendent le pilote : banc seulement. |
| `gtgsbackrooms.portal.shaderCohabitation` (`-PportalShaderCohabitation`) | `auto` | `auto` : sous un pack de shaders, les vues de portail se dessinent par le pack ; `off` : sous un pack, les cadres sont sombres (comme sans stencil). |
| `gtgsbackrooms.look.verifyNeonShader` (`-PlookVerifyNeonShader`) | vide | Développement : chemin du fichier des vecteurs communs du calendrier des néons ; au premier rechargement, le client vérifie que le jumeau GLSL les suit (« [Look] Jumeau GLSL du calendrier des neons : N vecteur(s) identiques ») et le coupe sinon. |

## Architecture

Deux étages, et c'est tout le moteur (spécification, section 5.2) :

1. **Étage 1, l'injection** : les fragment shaders vanilla sont patchés au chargement ; la couleur finale de chaque
   fragment passe par `gtgs_applyLook` (`assets/gtgs_backrooms/shaders/include/look.glsl`), qui applique teinte,
   désaturation, grain et vacillement du profil courant, ou rend la couleur inchangée quand le look vaut 0 (vanilla).
   Les **néons** passent par là aussi : les vertex shaders patchés sortent la position du fragment en espace caméra
   (`gtgs_ViewPos`), et un bloc d'uniformes (`gtgs_NeonBlock`, `std140`, lié une fois par programme au point 1) porte
   les néons les plus proches de la caméra de la vue (position en espace caméra et portée, couleur multipliée par la
   force et état du moment) ; chaque fragment ajoute la lumière de chacun (quadratique avec la distance). La **face**
   de chaque tube du terrain suit son état par le **jumeau GLSL du calendrier** (`neon_schedule.glsl`, le même hachage
   entier de 32 bits que `NeonSchedule`) : elle se reconnaît au rectangle de la texture du néon dans l'atlas des blocs
   (`gtgs_NeonFace`, tiré du modèle du bloc de néon du profil par `NeonFaces`) et au drapeau du terrain ; son bloc se
   retrouve depuis la caméra de la vue en bloc entier et reste (`gtgs_CameraBlock`, `gtgs_CameraFract` : aucune perte
   de précision loin de l'origine) ; l'horloge (`gtgs_NeonClock` : tick du monde dans la période de l'horloge commune,
   graine de la dimension) et les parts du profil (`gtgs_NeonShares`) sont celles de la vue. Les cris proches
   (**perturbations** de la liaison son-image, quatre au plus, dans le bloc des néons avec leur force) retirent à la
   face de chaque tube à leur portée ce que le son leur retire, tube par tube, y compris ceux que la liste des néons
   proches n'a pas retenus. Sans le jumeau (vérification ratée), la face d'un néon retenu suit l'état envoyé dans le
   bloc. Coût : une branche uniforme, quelques opérations par fragment, une boucle sur les néons retenus (huit ou
   trente-deux au plus, selon la qualité), et, sur la seule face d'un tube, le hachage et quatre perturbations.
2. **Étage 2, le post masqué** (`LookPost`) : en fin de rendu du monde, main comprise, l'image est copiée dans une
   texture (un blit vers le framebuffer `gtgs_backrooms:screen_copy`, déclaré en données), puis, pour chaque look vu
   dans l'image dont le profil a un post, un triangle plein écran est dessiné dans le framebuffer du jeu sous le test de
   stencil des bits de ce look : vignette et aberration chromatique (`look_post`, programme du mod, sans `discard` ni
   `gl_FragDepth` pour que le stencil rejette les autres pixels avant le fragment shader). En qualité complète, si un
   look vu demande le bloom, il se calcule d'abord sur la copie de l'écran : pixels au-dessus du seuil du profil à
   demi-résolution (`look_bloom_extract`, framebuffer `bloom_half`), flou séparable en deux passes à quart de résolution
   (`look_blur`, `bloom_quarter_a` et `_b`, neuf échantillons par passe, filtrage linéaire), puis le post ajoute le flou,
   pesé par la force du profil, sur les seuls pixels du look (deuxième sampler de `look_post`) : le bloom n'est masqué qu'à
   la composition, il peut donc déborder d'un cadre vers l'intérieur mais jamais sur les pixels vanilla. Rien sous un pack
   de shaders (sa passe finale ignore le stencil) ni quand le pilote refuse le stencil.

Ce qui existe déjà et que le moteur réutilise : la lightmap par monde vu (`ViewLightmaps`), le brouillard par vue
(`RemoteViewRenderer.prepareFog`), le stencil par niveau d'imbrication et le ciseau des cadres (`PortalStencilRenderer`),
le plan de découpe envoyé à chaque shader actif (`MixinShaderProgram`), l'injection dans les sources des shaders au
chargement (`MixinShaderStage`).

### Paquets (`core/render/`)

| Paquet | Classes | Rôle |
|--------|---------|------|
| `patch/` | `ShaderPatcher`, `ShaderPatch`, `ShaderPatchRegistry`, `InjectionPoint`, `ShaderKind` | Patch des shaders vanilla par **enveloppe de `main`** : la `main` vanilla est renommée `gtgs_vanilla_main`, une nouvelle `main` l'appelle entre le code des injections (`head` après `#version`, `main_before`, `main_after`, `output` : la sortie déclarée passe par une fonction). Patchs déclarés dans `assets/gtgs_backrooms/shader_patches/*.json` (étage, cibles par motif, exclusions, bibliothèques insérées en tête, injections). Logique pure, couverte à 100 %. |
| `profile/` | `LookProfile`, `LookProfiles` | Le profil d'une dimension (donnée, `record`) et leur registre par dimension ; identifiant de look de 1 à 7, 0 pour vanilla. Un fichier invalide est refusé au chargement et journalisé. |
| `state/` | `LookState` | La pile des looks du rendu : le monde du joueur au fond, celui de chaque vue de portail empilé le temps de la vue, vanilla hors du rendu du monde. Une version avance à chaque changement de sommet et à chaque image : un shader ne reçoit ses uniformes que si elle a changé. |
| `light/` | `DimensionLightmap`, `DimensionFog`, `NeonSchedule`, `NeonSelection`, `NeonMap` | La courbe du profil appliquée à chaque point de la lightmap (plancher, plafond, chaleur), une fois par tick et par monde ; la règle du brouillard du profil (terrain seulement, caméra hors des fluides, sans cécité ni brouillard épais) ; l'état de chaque néon d'après sa position et le temps du monde (mélange d'entiers de type splitmix : la part qui vacille, coupures de quatre ticks tirées au sort par créneau, la part morte, intensité jamais nulle) ; la liste bornée des néons les plus proches ; la carte des néons d'un monde, section par section (relevé d'un chunk, un bloc posé ou retiré, colonne déchargée, collecte à portée). Logique pure, couverte à 100 %. |
| `quality/` | `LookQuality` | Qualité légère ou complète : nombre de néons par vue et bloom, choisie par l'option ou d'après la chaîne du pilote. |
| `post/` | `StencilLookMask` | Disposition du stencil : niveau d'imbrication dans les bits `0x03`, look dans les bits `0x1C`. |
| `fbo/` | `FramebufferSpec` | Un framebuffer déclaré en données (`assets/gtgs_backrooms/framebuffers/*.json` : nom, échelle, `RGBA8`, profondeur, filtrage). |
| `client/` | `LookResources`, `LookFrame`, `LookUniformUploader`, `LookFog`, `LookDimensionEffects`, `LookPrograms`, `LookPost`, `FramebufferRegistry`, `FullscreenQuad`, `FullscreenTriangle`, `GpuTimer`, `StencilDebugOverlay`, `NeonIndex`, `NeonLights`, `ShaderCompileCheck`, `NeonFaces`, `NeonShaderCheck`, `GlStateCheck` | Colle client (exemptée de couverture, nommée dans `docs/TESTING.md`) : chargement des profils, patchs, bibliothèques et framebuffers depuis le gestionnaire de ressources derrière la fabrique des shaders vanilla, qualité choisie d'après le pilote, début et fin de look par image, envoi des uniformes, brouillard et effets de dimension d'un monde à look, programmes du mod (`look_post`, `look_bloom_extract`, `look_blur`, chargés avec les shaders vanilla par le chargeur de l'API Fabric), post masqué et bloom, framebuffers créés à la demande et recréés au seul redimensionnement, quad plein écran de débogage, triangle plein écran persistant du post (ses deux samplers posés sous leur nom constant, rien d'alloué par image), minuteries GPU (`GL_TIME_ELAPSED`, lues des images plus tard, jamais imbriquées, rien sous un pack), mode `debugStencil` ; l'index des néons de chaque monde client (relevé à l'arrivée de chaque chunk par les évènements de chunk de l'API Fabric, section par section, seules celles dont la palette contient le bloc étant parcourues ; suivi à chaque `setBlockState` d'un monde client par `MixinClientWorld` ; oublié avec la colonne ou le monde) ; le bloc d'uniformes des néons, rempli par vue (un tampon par niveau de vue, rien d'alloué par image ; néons retenus et perturbations de la dimension de la vue) ; la compilation à blanc d'un shader patché avant de le donner au jeu ; le rectangle de la texture du néon de chaque look dans l'atlas (`NeonFaces`, recalculé après chaque rechargement des modèles) ; la vérification du jumeau GLSL sur les vecteurs communs (`NeonShaderCheck` : un shader de test, un pixel par vecteur, relu et comparé ; un écart coupe le jumeau pour la session) ; le contrôle de développement de l'état GL autour de chaque passe (`GlStateCheck`). |

Le contenu déclare ce qui le concerne : `backrooms/level0/client/LevelZeroEffects` enregistre les effets de dimension
du Level 0 (`LookDimensionEffects`, sous l'identifiant `gtgs_backrooms:backrooms_level_0` que son type de dimension
déclare : pas de ciel ni de nuages, brouillard du profil), et `assets/gtgs_backrooms/look_profiles/level_0.json` porte
ses valeurs.

### Une image, de bout en bout

```
GameRenderer.renderWorld
  WorldRenderer.render (monde du joueur)                      MixinWorldRenderer, HEAD
    LookState.beginWorld(profil du monde du joueur, camera en bloc entier et reste, tick et temps du monde + fraction de
      tick : CueClock, l'horloge commune avec l'audio) ; stencil efface au niveau 0 + look du joueur (tous les bits) ;
      perturbations des sons lies mises a jour, plafond de clignotement sur la charge de l'image precedente
    NeonLights.beginWorld (neons proches de la camera, bloc d'uniformes)
    lightmap du joueur (courbe du profil : MixinLightmapTextureManager), brouillard (MixinBackgroundRenderer)
    couches opaques, entités : chaque ShaderProgram.bind envoie le look courant si sa version a changé
    portails (PortalStencilRenderer, s'il y en a de visibles) : stencil efface de nouveau au niveau 0 + look du joueur
      pour chaque vue : zone marquée au niveau n+1 + look de la dimension vue (INCR du niveau, REPLACE des bits de look)
        LookState.push(profil, camera et tick de la vue) ; NeonLights.push (neons et perturbations vus de la camera de la
          vue) ; part de l'ecran du cadre (plafond de clignotement) ; lightmap et brouillard de la vue ; dessin ; pop
      zone rendue au niveau n : les bits de look de la vue survivent
    translucide, particules, nuages, météo du monde du joueur
  main (le look du monde du joueur)
  LookPost : copie de l'ecran, bloom (qualite complete : extraction a demi-resolution, flou en deux passes a quart),
             puis un triangle plein ecran par look vu, sous le stencil de ses bits de look
  LookState.endWorld() : vanilla pour l'interface                  MixinGameRenderer, TAIL
```

Une image figée (`FrozenViews`) collée sur un cadre prend les bits de look de sa dimension là où elle se dessine
(`drawFrozen`), au niveau du monde du joueur : le post la traite avec la vue qu'elle remplace.

Coupe-circuits (spécification, section 10) : une source patchée que le pilote refuse reste vanilla, journalisée une
fois (`ShaderCompileCheck`) ; une exception pendant le post le coupe pour la session, l'état GL rendu (stencil,
profondeur, culling, framebuffer) et l'étage 1 des shaders gardé (`LookPost`) ; sans stencil (`PortalRenderState`),
ni post ni bits de look ; un framebuffer non déclaré est journalisé une fois et sa passe sautée (`FramebufferRegistry`).
Les mesures GPU (`GpuTimer`) n'ouvrent qu'une requête à la fois et rien sous un pack de shaders.

État GL rendu (spécification, section 10) : chaque passe rend l'état que son appelant tenait. Hors pack, en fin de
rendu du monde, c'est le repos de vanilla entre deux dessins (test de profondeur et stencil coupés, écriture de la
profondeur active) ; sous un pack, après une image du pack, le test de profondeur est actif, l'écriture de la
profondeur coupée dans le cadre d'une vue, et le test de stencil de la vue reste posé (la suite de la vue en dépend).
Créer ou redimensionner un framebuffer vanilla active le test de profondeur (`Framebuffer.resize`) : le registre le
recoupe. Le contrôle de développement (`GlStateCheck`, option `gtgsbackrooms.look.checkGlState`) relit au pilote, avant
et après chaque passe, stencil (test, fonction, référence, masques sur ses huit bits, opérations, valeur d'effacement),
profondeur (test, fonction, écriture), mélange, élimination des faces, ciseau, fenêtre de rendu, masques de couleur,
framebuffers liés, programme, unité de texture active, bloc d'uniformes lié ; tableaux et tampons de sommets et texture
liée ne sont pas comparés (vanilla les repose avant chaque dessin et délie les textures d'un programme à sa fin).

Sous un pack de shaders, les shaders du pack remplacent ceux de vanilla (l'étage 1 n'atteint rien) et sa passe finale
ignore le stencil. **Dans une dimension à look, le module `compat/iris` rend la main à nos shaders** : tant que le
joueur y est, le gestionnaire de pipelines d'Iris rend son pipeline vanilla pour tout ce qui se dessine (monde du joueur
et vues), si bien que l'étage 1, la lumière des néons, la lightmap, le brouillard et le post du profil s'appliquent
comme sans pack, et qu'aucune image ne mêle le format de maillage du terrain du pack et celui de vanilla (mêlés, ils
dessinaient le terrain en éclats). À la bascule (entrée ou sortie, traversée ou changement de monde vanilla), les
réglages globaux du pipeline quitté sont gardés et ceux de l'autre reposés, le mod de terrain recharge ses shaders de
sections et le terrain est reconstruit dans le nouveau format ; le pipeline du pack, gardé dans le cache d'Iris, n'est
pas recompilé. Hors de ces dimensions (Overworld sous le pack), une vue du Level 0 est rendue par le pack et reçoit le
look entier de sa dimension sur les pixels de son cadre (`LookPost.renderUnderShaderPackView`) ; avec l'option
`gtgsbackrooms.look.replaceShaderPack` coupée ou Iris hors de la plage vérifiée (`IrisVersion`, `>=1.8.8 <=1.8.14-beta.1`, la dernière version vérifiée), le
pack rend aussi le Level 0 et le look entier se pose sur son image (`LookPost.renderUnderShaderPack`, appelé par
`PortalStencilRenderer.renderAfterWorld`).

### Stencil

| Bits | Sens |
|------|------|
| `0x03` | Niveau d'imbrication (0 monde du joueur, 1 vue, 2 vue imbriquée) ; tous les tests de niveau masquent avec eux |
| `0x1C` | Identifiant du look de la dimension que le pixel montre (0 vanilla, 1 à 7) |
| `0x40`, `0x80` | Chemin sous un pack de shaders seulement : bit de la main (`HandMask`), bit d'un cadre imbriqué |

Invariant : en fin de rendu du monde, les bits de look d'un pixel valent le look de la vue la plus profonde réellement
visible sur ce pixel. Il tient parce qu'une vue n'écrit ses bits que sur les pixels qu'elle prend (ceux qui passent du
niveau n à n+1), qu'une vue imbriquée écrase ceux de ses propres pixels seulement, et que le retour de niveau (DECR) ne
touche pas aux bits de look. L'effacement se fait sous masque d'écriture `0xFF` (un effacement n'écrit que les bits du
masque courant), au début du rendu du monde (`LookFrame.beginWorld`, portail visible ou non : le post en dépend) et de
nouveau au début de la passe des portails. Mode `debugStencil` pour le voir.

## Patch des shaders : ce qui est vérifié

- **Inventaire versionné** des 110 shaders de base de Minecraft 1.21.1 (`src/test/resources/shader_inventory_1_21_1.json`) :
  pour chacun, étage, version, nombre de `main`, sorties déclarées, `discard`, directives, macros, bibliothèques
  importées, verdict. `ShaderPatcherTest` le recalcule sur le jar du jeu à chaque build, sources compilées comme le jeu
  les compile (`GlImportProcessor` vanilla : bibliothèques insérées, leur `#version` mis en commentaire, directives
  `#line`), et refuse tout écart : une mise à jour du jeu se voit.
- Les 55 fragment shaders déclarent une seule sortie `fragColor`, une seule `main`, aucune directive conditionnelle :
  tous patchables ; 39 font `discard`, gardé tel quel. Les 55 vertex shaders le sont aussi (18 importent `light.glsl`,
  dont les macros ne touchent ni sortie ni `main`).
- Une source hors signature n'est **pas** patchée (version autre que 150, deux `main`, directive conditionnelle, macro
  dans la déclaration de la sortie ou la signature de `main`, directive inconnue, deux sorties) : journalisée une fois,
  rendue vanilla. Jamais de patch « au mieux ». Un shader remplacé par un pack de ressources passe par les mêmes règles.
- Les shaders d'interface et de texte (`rendertype_text*`, `rendertype_gui*`), le contour des entités
  (`rendertype_outline`), les nuages et le ciel (`position*`, `blit_screen`) ne sont pas ciblés.
- Les vertex shaders du monde reçoivent cinq sorties de plus (patch `world_vertex.json`), calculées en fin de `main` :
  position en espace caméra (`gtgs_ViewPos`), position relative à la caméra dans les axes du monde (`gtgs_WorldRel`),
  coordonnées de texture (`gtgs_Uv`), normale (`gtgs_Normal`) et drapeau du terrain (`gtgs_Terrain`, `flat`). Jetons du
  code d'une injection, remplacés selon ce que le shader déclare : `${position}` vaut `Position + ChunkOffset` dans un
  shader qui déclare `ChunkOffset` (terrain), `Position` sinon ; `${uv}` vaut `UV0`, ou `vec2(-1.0)` sans lui (hors de tout rectangle de l'atlas) ;
  `${normal}` vaut `Normal`, ou `vec3(0.0)` ; `${terrain}` vaut `1.0` dans un shader du terrain, `0.0` sinon.
- **Jumeau GLSL du calendrier** : `NeonScheduleTest` vérifie `NeonSchedule` sur 573 vecteurs communs
  (`src/test/resources/neon_schedule_vectors.json`, écrits par un programme indépendant : position, graine, tick, parts
  ⇒ caractère, intensité) ; le client de développement vérifie `neon_schedule.glsl` sur les mêmes vecteurs, sur le
  processeur graphique (option `gtgsbackrooms.look.verifyNeonShader`). Le hachage est en entiers non signés de 32 bits
  des deux côtés (`int` de Java, `uint` de GLSL, multiplications modulo 2^32) : aucun flottant dans la décision.
- **Empreintes des sources patchées** (`PatchedShadersGoldenTest`, spécification section 11, « Golden ») : chaque shader
  de base de 1.21.1, compilé comme le jeu le compile, passe par le plan de découpe des portails puis par les patchs du
  mod, comme dans `MixinShaderStage` ; l'empreinte (SHA-256, seize chiffres) de chaque source changée est versionnée
  (`src/test/resources/patched_shaders_1_21_1.json`, 90 sources : les 72 étages du look et les vertex shaders coupés au
  plan des portails). Un changement de patch, de bibliothèque ou de l'injecteur se voit : le test échoue et donne la
  table recalculée, à recopier quand le changement est voulu. Fins de ligne ramenées à `\n` avant le calcul (même
  empreinte sous Windows et en CI).
- **Compilation à blanc** (`ShaderCompileCheck`) : chaque source patchée est compilée une première fois par le mod avant
  d'être donnée au jeu ; si le pilote la refuse (pilote strict, shader remplacé par un pack de ressources), le journal
  garde son message une fois (« [Look] Le shader ... patche ne compile pas ») et le jeu reçoit la source vanilla : un
  look manquant plutôt qu'un monde rose. Le bilan (« N étage(s) de shaders patché(s) compilé(s) par le pilote, M
  refusé(s) ») est journalisé à la fin de chaque rechargement : c'est la preuve que l'étage 1 est en place, l'image
  seule ne le dit pas (lightmap, brouillard et post changent l'image sans lui). Coût : une compilation de plus par
  shader au chargement, rien par image.
- Les ressources du moteur se chargent depuis le gestionnaire de ressources derrière la fabrique avec laquelle vanilla
  charge ses programmes (`GameRenderer.CachedResourceFactory`, à déballer : ce n'est pas un `ResourceManager`), au
  premier étage du rechargement : avant cette correction du 27 octobre 2026 au soir, aucun shader vanilla n'était
  patché en production.

## Hypothèses et compatibilité (état au 28 octobre 2026)

Hypothèses de la spécification (section 15) :

| # | État | Preuve ou manque |
|---|------|------------------|
| H1 | Tenue | Invariant des bits de look (`0x1C`, à la place du bit `0x80`) vérifié en mode `debugStencil` : vue imbriquée d'un autre look, cadres alignés, troisième personne, corps à cheval sur un plan (Tests) ; RenderDoc non utilisé (absent du banc). |
| H2 | Tenue, sauf la CI | Inventaire versionné des 110 shaders, 55 + 55 patchables, empreintes des 90 sources patchées ; compilation réelle par le pilote à chaque rechargement (72 étages, 0 refusé, PC de référence). La compilation hors GPU en CI (compilateur de référence Khronos) demande un outil ou une dépendance de plus : décision de l'auteur. |
| H3 | Tenue | Post en fin de `renderWorld`, main comprise, translucides composés : vérifié dans les trois modes graphiques (preuve « strictement vanilla »). |
| H4 | Tenue | Le stencil du framebuffer du jeu est intact à ce point (mode `debugStencil`, post masqué). |
| H5 | Ouverte | Coûts mesurés sur le PC de référence (854 × 480 et 1080p) ; aucune carte intégrée au banc. |
| H6 | Statuée | Sous le mod de terrain : étage 1 par le post (terrain et entités, sans lumière des néons), vues présentes. |
| H7 | Tenue (PC de référence) | Sans look vu, aucun post ; avec, un triangle par look sous le stencil (0,006 ms GPU). |
| H8 | Tenue depuis le 28 au soir | Post par vue sous un pack, sous le stencil exact de la vue, image du joueur remise autour ; la copie de l'écran n'est pas limitée au rectangle de la vue (coût par vue). Avant la correction du soir, il effaçait les vues (Tests). Depuis le 29, il ne sert plus que pour une vue des Backrooms vue de l'Overworld (dans les Backrooms, le rendu du mod remplace le pack). |

Matrice de la spécification (section 14.6), telle qu'elle est :

| Configuration | Ambiance | Étage 1, terrain | Étage 1, entités et particules | Post masqué | Vues de portail |
|---------------|----------|------------------|--------------------------------|-------------|-----------------|
| Vanilla | Oui | Oui | Oui | Oui | Oui |
| Mod de terrain seul | Oui | Par le post, sans lumière des néons | Par le post | Oui | Oui |
| Mod de shaders, pack actif, joueur dans le Level 0 | Celle du profil (nos shaders) | Oui | Oui | Oui | Oui, vanilla (pipeline vanilla d'Iris) |
| Mod de shaders, pack actif, joueur hors des Backrooms | Celle du pack | Par le post sur l'image du pack, chaque vue d'une dimension à look | Idem | Oui, sur l'image du pack | Oui, rendues par le pack (option `off` : cadres sombres) |
| Mod de shaders sans pack | Comme le mod de terrain seul | | | | |
| Mods sans rendu du terrain | Oui (rien de propre à eux) | Oui | Oui | Oui | Oui |

## Limites connues

- **Sous un pack de shaders** : dans le Level 0, nos shaders remplacent le pack (voir Architecture). Le prix d'une
  image unique par format de maillage : tant que le joueur est dans le Level 0, l'Overworld vu par un portail est
  vanilla (sans le pack) ; depuis l'Overworld, le Level 0 vu par un portail est rendu par le pack, le look posé dessus
  (lumière des néons et lightmap du profil absentes de cette vue). À chaque bascule, le terrain se reconstruit : une
  image de 96 à 117 ms en entrant, de 52 ms en sortant sur le PC de référence (contre environ 1 s pour couper puis
  rallumer le pack, qui le recompile), puis les sections réapparaissent en quelques images. Le module lit des classes
  internes d'Iris (gestionnaire de pipelines, réglages globaux), vérifiées de 1.8.8 à 1.8.14-beta.1 : hors de cette
  plage, il ne s'applique pas et le pack rend aussi le Level 0. Le premier essai du 27 octobre 2026 (pipeline vanilla
  pour la seule dimension du Level 0) dessinait le terrain en éclats dès qu'une vue mêlait les deux pipelines : d'où la
  règle d'un pipeline par image, selon le monde du joueur.
- **Néons après un rechargement des ressources (F3+T)** : l'index des néons relève les chunks à leur arrivée. Un profil
  rechargé qui change de bloc de néon, ou une dimension qui gagne un profil, ne vaut que pour les chunks reçus ensuite :
  ceux déjà chargés gardent l'ancien relevé jusqu'à leur rechargement (relevé à la revue du 29 octobre 2026 ; sans effet
  en jeu, où les profils ne changent pas).
- **Pack de shaders, détection** : hors du module `compat/iris`, le mod ne nomme aucun autre mod ; un pack se reconnaît, au milieu
  du rendu du monde du joueur, à ce que les programmes que vanilla donne aux entités et aux translucides ne sont plus
  ceux du jeu de base (`ShaderPackState`). En tête du rendu, le pack ne les a pas encore remplacés : ce qui se décide
  avant le relevé (début du look, entités dessinées avant les portails) lit celui de l'image précédente, d'où une image
  de retard à l'activation ou à la coupure d'un pack, sans effet visible au banc (pack coupé puis rallumé en cours de
  partie : mêmes images, à 1/255 près, que l'ancienne détection par l'API du mod de shaders). L'option
  `gtgsbackrooms.portal.shaderCohabitation=off` donne sous un pack des cadres sombres. Le mode « vues toujours
  vanilla sous un pack » du plan (`views_vanilla`) n'existe pas en dehors des Backrooms : deux pipelines dans une image
  dessinent le terrain en éclats (essai du 27 octobre 2026, ci-dessus) ; dans les Backrooms, tout est vanilla.
- **Avec un mod qui dessine le terrain** (ses propres shaders, reconnu à sa grille vanilla à distance nulle,
  `RemoteViewRenderer.foreignTerrain`), les shaders patchés n'atteignent pas les blocs : l'étage 1 (teinte,
  désaturation, grain, vacillement) passe alors dans le post, sous le stencil de chaque look, pour tout ce qui porte le
  look, terrain et entités pareil (`LookState.shadersCarryLook` à faux : les shaders patchés rendent vanilla) ; la
  **lumière des néons n'existe pas** dans ce cas (elle se calcule par fragment dans les shaders du terrain). Lightmap,
  brouillard du profil, vignette, aberration et bloom restent.
- **Lumière des néons** : multiplicative (elle relève la couleur déjà éclairée par la lightmap vanilla, elle n'éclaire
  pas le noir complet) : le tube émet déjà sa lumière de bloc vanilla, la lumière du moteur en fait des flaques plus
  franches et plus chaudes, qui s'éteignent avec lui. Les tubes voisins s'additionnent, la somme est plafonnée (+80 %)
  pour qu'un couloir dense ne vire pas au blanc ; les valeurs du profil du Level 0 (portée 8, force 0,6) sont un
  réglage de départ, à ajuster à l'œil (F3+T recharge le profil). Les néons retenus sont les plus proches de la caméra
  de chaque vue (huit ou trente-deux) ; au-delà, un tube brille sans éclairer autour de lui. Sous un pack, rien
  (shaders du pack).
- **Bloom** : masqué à la composition seulement ; au bord d'un cadre, le halo d'un tube de la vue peut déborder de
  quelques pixels vers l'intérieur du cadre (jamais sur les pixels vanilla). Sous un pack de shaders, il se calcule
  sur l'image du pack, par vue comme pour le monde du joueur (chemin du plan B).
- **Aberration chromatique** : ses échantillons décalés lisent la copie de l'écran sans vérifier le look du pixel voisin ;
  au bord d'un cadre, une frange de deux ou trois pixels mêle les deux côtés (spécification, section 6.1 du guide : marge
  d'échantillonnage). Accepté pour un décalage de 3 % au plus.
- Une vitre ou de l'eau du monde du joueur devant un cadre porte les bits de look de la vue derrière elle : le post à
  venir les traitera avec la vue. Comportement accepté par la spécification.
- **Transition à la traversée** : volontairement absente (le look suit le pixel, pas le joueur : aucune couture par
  construction). **Vacillement d'écran et variation des tubes** (part qui vacille, part morte) : mis de côté par
  l'auteur le 27 octobre 2026 au soir, profil du Level 0 à zéro ; la mécanique est complète (`NeonSchedule` et son
  jumeau `neon_schedule.glsl`, son et image d'accord) : relever les parts du profil suffit.
- **Face des néons** : reconnue à la texture du bloc de néon du profil ; un pack de ressources qui donne au néon une
  texture par face, ou un autre bloc qui partage sa texture, sort de cette règle (le second suivrait le calendrier). Le
  jumeau ne touche que le terrain : un néon tenu en main ou en entité suit l'état vanilla. Avec un mod qui dessine le
  terrain, ses shaders n'ont pas le jumeau (pas de face qui s'éteint) ; sous un pack, rien.

## Tests

- Unitaires : `ShaderPatcherTest` (inventaire, 110 shaders du jar patchés par enveloppe, points d'injection, sortie
  qualifiée, commentaires, refus par raison, bords du texte), `ShaderPatchTest` (lecture d'un patch, motifs, exclusions,
  refus), `ShaderPatchRegistryTest` (patch du mod sur les shaders du monde et eux seuls, fichiers invalides et
  bibliothèques absentes écartés, bibliothèque partagée lue une fois, refus journalisé une fois), `LookProfileTest`
  (profil du Level 0, valeurs par défaut, refus ; le bloc de néon du profil existe au registre et émet de la lumière),
  `LookProfilesTest` (registre, doublons), `LookStateTest` (pile, version, remises à zéro, bornes, looks vus dans
  l'image, compte des néons, étage 1 porté par les shaders ou par le post), `DimensionLightmapTest`, `DimensionFogTest`,
  `NeonScheduleTest` (vecteurs communs du jumeau GLSL, parts qui vacillent et mortes, coupures de quatre ticks, même
  résultat partout, jamais noir),
  `NeonSelectionTest` (liste bornée, triée), `NeonMapTest` (sections remplacées, bloc posé ou retiré, colonne
  déchargée, collecte à portée), `LookQualityTest` (option, carte intégrée), `StencilLookMaskTest`,
  `FramebufferSpecTest` (copie d'écran du mod, défauts, échelle, refus), `PortalRenderStateTest` (stencil tenu ou
  refusé), `MixinTargetsTest` (les mixins du moteur s'appliquent).
- En jeu, banc de production du 27 octobre 2026 au soir (PC de référence, 854 × 480, même JAR avec le moteur actif puis
  coupé par `gtgsbackrooms.look.enabled=false` ; nuages, particules, chat et main changent d'un lancement à l'autre et
  sont exclus des comparaisons). Deux preuves du mécanisme avant toute mesure d'image, parce que lightmap, brouillard
  et post changent l'image sans l'étage 1 (ce qui a masqué, le matin même, que les shaders n'étaient pas patchés) :
  - le journal dit « [Look] 72 etage(s) de shaders patche(s) compile(s) par le pilote, 0 refuse(s) » à chaque
    rechargement (36 programmes du monde, deux étages chacun), en rendu vanilla comme avec le mod de terrain et le mod
    de shaders installés, pack coupé ou actif ;
  - mode `debugStencil` dans le Level 0 sans portail visible : tout l'écran au niveau 0 avec le look 1 (gris jauni),
    main comprise ; avant la correction du soir, seule la main le portait.
  - Intérieur du Level 0 (`make_l0lab.py`, quatre poses, rendu vanilla) : 98 % des pixels changent ; moyenne RGB hors
    chat et main de (68,5 ; 60,6 ; 36,4) à (87,9 ; 75,5 ; 44,7) à la première pose, de (77,2 ; 71,8 ; 57,7) à
    (114,2 ; 102,0 ; 74,0) sous un tube : plus chaud, plus clair près des tubes. Tube au plafond (pose `l0_d`) :
    zone du tube de 106 à 183, plafond autour (quarante pixels sous lui) de 93 à 134, plafond loin de 70 à 99, et une
    carte de chaleur de la différence en halo radial autour du tube : la lumière des néons et le bloom. Vignette : le
    rapport coin / haut de l'image passe de 0,66 à 0,56.
  - Même intérieur avec le mod de terrain et le mod de shaders installés, pack coupé : étage 1 dans le post, moyenne de
    (68,5 ; 60,6 ; 36,4) sans look à (64,8 ; 56,3 ; 34,2), vignette identique au rendu vanilla (rapport 0,56) ; pas de
    lumière de néon (le terrain n'est pas dessiné par les shaders patchés), tube sous le seuil du bloom.
  - Level 0 vu par un portail depuis l'Overworld (`make_lodlab.py level0`, sept poses, rendu vanilla) : bande de sol et
    barre d'outils identiques au pixel près (0 pixel sur 33 600 et 17 520 aux six poses où rien d'autre ne bouge) ;
    hors du cadre, seules diffèrent des lignes d'un pixel sur des arêtes de blocs (caméra au sous-pixel entre deux
    lancements, écart max 77) et les particules.
  - Scène statique vers l'End (`make_lodlab.py static`, sans profil) : mêmes bandes identiques, mêmes arêtes.
  - Portail depuis l'Overworld avec le mod de terrain et le mod de shaders, pack coupé puis pack actif : le client
    démarre, les shaders patchés compilent, le cadre montre le Level 0, aucun éclat.
  - Même portail sous le pack, moteur actif puis coupé (28 octobre 2026 au matin, look dans les vues sous un pack) :
    les écarts tenaient dans le rectangle du cadre, cadre à (190 ; 194 ; 210) avec le look contre (136 ; 138 ; 146)
    sans. **Cette mesure était fausse** : le post de la vue coupait en sortie le test de stencil, et l'image du joueur
    remise autour du cadre recouvrait alors l'écran entier ; le cadre montrait le ciel derrière lui (190 ; 194 ; 210),
    et les vues suivantes (cadres alignés, vues imbriquées) disparaissaient aussi. Trouvé le soir même par le contrôle
    d'état GL (« La passe post d'une vue sous un pack ne rend pas l'etat GL tel quel : GL_STENCIL_TEST 1 devient 0 »),
    confirmé au banc (JAR du 25 octobre : vues présentes ; tous les JAR depuis le look par vue : vues absentes), corrigé
    (le post rend le test de la vue). Mesure refaite (scène des cas du stencil, mod de shaders et mod de terrain installés, pack actif, pose
    l0_a) : le cadre montre le Level 0, l'End derrière lui et la vue imbriquée ; avec le look (95,7 ; 85,8 ; 79,7)
    contre (94,5 ; 84,4 ; 80,1) moteur coupé, écart moyen 2,2 à 2,9 dans le cadre et jusqu'à 149 au halo du tube :
    sous le pack, le look d'une vue reste discret (le pack garde lumière et brouillard). Aucun écart d'état GL ensuite,
    sous le pack comme sans. Option `shaderCohabitation=off` : cadre sombre (44 ; 43 ; 43), « Cohabitation avec un pack
    de shaders coupee par l'option » au journal. Le post sous le pack coûte 0,12 à 0,19 ms de processeur par appel et
    alloue 0,3 Kio par appel (le chemin vanilla n'alloue rien : non élucidé, côté mod de shaders).
  - **Invariant du stencil** (spécification, section 11, cas manuels), 28 octobre 2026, mode `debugStencil`, rendu
    vanilla : depuis l'Overworld, un cadre vers le Level 0, et dans le Level 0 un cadre imbriqué vers l'Overworld ; un
    second cadre, plus grand, aligné derrière le premier, vers l'End ; un porte-armure à cheval sur le plan du premier,
    dans son ouverture ; poses à la première personne, à la troisième, puis de nouveau à la première. En fin d'image le
    niveau est revenu à 0 partout (le retour de niveau garde les bits de look) : le cadre porte le look du Level 0, la
    vue imbriquée vers l'Overworld le look 0 au milieu de lui, le porte-armure et le corps du joueur (troisième
    personne) devant le cadre le look 0, le cadre aligné derrière est couvert par le premier là où ils se recouvrent :
    les bits de look valent la vue la plus profonde réellement visible. Le cadre vers l'End, sans look, ne se distingue
    pas de l'Overworld dans ce mode (même look 0).
  - **« Strictement vanilla »** selon la définition de la spécification (28 octobre 2026) : la scène `proof` du banc
    (plateforme de l'Overworld avec quatre vaches immobiles, un porte-armure, un coffre, du verre, du verre teinté, des
    feuilles et de l'eau ; temps et météo figés ; nuages coupés, ils dérivent avec l'horloge de la machine d'un
    lancement à l'autre ; poses prises plus de vingt secondes après l'arrivée, les vingt premières voyant ciel et sol
    s'éclaircir lentement dans les deux clients ; 854 × 480) jouée avec le mod, puis le **même monde sans la dimension
    du mod** rejoué par un **client sans le JAR du mod** (Fabric API seul), dans les trois modes graphiques (rapide,
    joli, fabuleux). Hors du cadre du portail (que le mod seul dessine), de l'interface (barre d'outils avec la
    baguette, chat) et d'un feu de camp lointain (particules de fumée, aléatoires), **aucun pixel ne diffère de plus
    de 2/255** sur les six poses prises après l'arrivée, dans les trois modes ; les écarts de 1 à 2/255 touchent de
    0,001 % à 0,9 % des pixels selon la pose, sur les entités et les arêtes des blocs, jamais sur le ciel ni le sol
    uni : le pilote recompile les shaders vanilla enveloppés (étage 1, look à zéro) et arrondit autrement ici et là.
    Tolérance retenue et documentée : 2/255 (spécification, section 1.1). Chaîne `proof_chain.sh` du scratchpad,
    monde copié par `make_vanilla_world.py`, analyse par `proof_analyze.py`. Le cas « Overworld vu à travers un
    portail depuis les Backrooms » reste couvert par les bandes identiques au pixel près de la scène `level0`
    (plus haut).
  - **Nuages et main** (28 octobre 2026, nuit) : même preuve, nuages actifs et figés dans les deux clients par un mod de
    banc (horloge du renderer à zéro le temps de leur dessin), chat masqué, une épée en main ; seule la barre d'outils
    est retirée de la comparaison. Trois modes graphiques, poses d_1 à d_6 : bande des nuages (haut de l'écran)
    identique au pixel près partout ; main et bras identiques (au plus 1/255 sur quelques pixels) ; hors du cadre,
    au plus 2/255, sauf **un pixel** : sur l'épée tenue, à la frontière de deux texels de sa texture, le même dans
    toutes les poses et les trois modes (59/255, un texel voisin choisi). Il reste avec le look, les portails et
    l'audio coupés (`look.enabled`, `portal.enablePortalFeature`, `audio.enabled` à faux) : aucun des moteurs n'en est
    la cause ; cause la plus probable, non prouvée, la disposition de l'atlas des textures, où celles du mod décalent
    l'épée (l'échantillonnage tombe autrement à la frontière d'un texel). La pose d_0, prise dans le fondu de l'arrivée,
    reste hors comparaison. Chaîne `proof2_chain.sh`, scène `make_proof2.py`, analyse `proof2_analyze.py`.
  Coût, mesuré par le moniteur de performance (`-PdebugPerformanceMonitor=true`) sur la même scène, par image, à
  854 × 480 sur le PC de référence (spécification, section 9.3 ; les budgets de la section 9.1 sont donnés pour 1080p
  sur une carte intégrée, à mesurer là) :

  | Section | Moteur actif | Moteur coupé |
  |---------|--------------|--------------|
  | `client.look.world.gpu` (rendu du monde, étage 1 compris) | 0,138 ms | 0,117 ms |
  | `client.look.copy.gpu` (copie de l'écran) | 0,006 ms | — |
  | `client.look.bloom.gpu` | 0,013 ms | — |
  | `client.look.post.gpu` (un triangle par look) | 0,006 ms | — |
  | `client.look.post` (processeur, bloom compris) | 0,053 ms, 0 octet alloué | — |

  À 1920 × 1080, même scène et même PC (28 octobre 2026) : rendu du monde 0,253 ms GPU moteur actif contre 0,134 ms
  coupé (étage 1 ≈ 0,12 ms), copie 0,014 ms, bloom 0,017 ms, post 0,012 ms : 0,16 ms pour le moteur entier, l'étage 1
  un peu au-dessus de l'objectif de 0,1 ms de la spécification, le reste loin dessous ; sur une carte intégrée, à
  mesurer (aucune sur le PC de référence).
  L'étage 1 coûte donc une vingtaine de microsecondes GPU à 854 × 480, le post et le bloom une trentaine. Avant le tampon persistant, le post allouait 2,4 Kio par image
  (tessellator et noms de samplers de vanilla).
- **Rendu du mod à la place du pack** (29 octobre 2026, Iris et Sodium, pack actif, scène des cas du stencil sans
  barrière) : le joueur marche de l'Overworld au Level 0 à travers le cadre, puis revient à reculons. Dans le Level 0,
  l'image est celle de la même scène pack coupé (écart moyen 1,7 à 2,2/255, couleurs moyennes égales à 0,4 près ; les
  écarts restants sont les nuages de la vue de l'Overworld, qui dérivent) ; « [Look] Pack de shaders actif : dans les
  dimensions a look, le rendu du mod le remplace » au journal. Traversée vers le Level 0 : image de 96 ms (reconstruction
  du terrain), puis environ 900 images/s ; retour : image de 52 ms, aucun « Using shaderpack » (pas de recompilation),
  terrain de l'Overworld intact (pas d'éclats), pack de retour. Aucun écart d'état GL, aucune exception.
- Contrôle d'état GL, même jour (`gtgsbackrooms.look.checkGlState`) : sur le chemin vanilla, le post laissait le test de
  profondeur actif (il le croyait actif en entrant, alors que vanilla le laisse coupé entre deux dessins), et la création
  des framebuffers du bloom l'activait au milieu du post ; sous un pack, le post d'une vue coupait le test de stencil
  (voir plus haut) et ne rendait ni la fonction du stencil ni le test et l'écriture de la profondeur. Tout est corrigé :
  plus aucun écart, rendu vanilla, pack actif et mode de débogage.
- Jumeau GLSL, banc du 28 octobre 2026 (PC de référence, rendu vanilla, intérieur du Level 0) : « 72 etage(s) de
  shaders patche(s) compile(s) par le pilote, 0 refuse(s) » avec les cinq sorties de plus ; « Jumeau GLSL du calendrier
  des neons : 573 vecteur(s) identiques » ; au cri lié sous le tube regardé, la face du tube s'éteint à l'image (capture),
  puis revient ; quatre poses sans cri comparées à celles d'avant le jumeau : écart moyen 2/255 (grain), aucune zone
  changée.
- **Test décisif** (section 12.1 de la spécification), même jour : depuis l'Overworld, un cadre vers le Level 0, et dans
  le Level 0 un second cadre vers un autre point du Level 0, vu à travers le premier ; le cri lié joué quatre blocs
  derrière l'arrivée du second est entendu au premier cadre (deux cadres, `docs/AUDIO.md`) et le tube du plafond de la
  vue baisse pendant le cri (169 puis 132 de luminosité, 170 après) ; la vue imbriquée s'affiche dans le premier cadre
  (trop petite à 854 × 480 pour y mesurer la réaction).
