# Rendu

Le moteur de shaders du mod donne à chaque dimension son propre rendu, appelé look. Il patche les shaders vanilla au
chargement et ajoute un post-traitement masqué par le stencil des portails. Le code est dans `core/render/`, le module
de compatibilité avec Iris dans `compat/iris/`.

## En jeu

Un profil de look donne à une dimension une teinte, une désaturation, un grain et, au besoin, un vacillement de la
lumière. Il lui donne aussi sa courbe de lumière, son brouillard, des néons qui éclairent et un post-traitement.
Aujourd'hui, seul le Level 0 a un profil.

Le rendu d'un pixel dépend de la dimension qu'il montre, pas de celle où se trouve le joueur.

- Dans le Level 0, tout l'écran porte le look du Level 0, main comprise. L'interface reste vanilla.
- Depuis l'Overworld, un portail vers le Level 0 montre le look dans son cadre seulement. Le reste de l'écran est
  strictement vanilla.
- Depuis le Level 0, un portail vers l'Overworld montre l'Overworld vanilla, même dans une vue imbriquée.
- Une dimension sans profil n'a aucun look : l'Overworld, le Nether, l'End et les dimensions des autres mods.

La traversée d'un portail n'a pas de transition de look. Comme le look suit le pixel, il n'y a aucune couture à masquer.

Sous un pack de shaders, le rendu du mod remplace le pack tant que le joueur est dans le Level 0. Le pack reprend en
sortant, sans recompilation. Les profils et les patchs se rechargent avec les ressources (F3+T), sans relancer le client.

## Fonctionnement

### Deux étages

Le moteur tient en deux étages, dans le stencil existant des portails, sans G-buffer ni rendu différé.

1. L'injection. Les shaders vanilla du monde sont patchés au chargement. La couleur finale de chaque fragment passe
   par `gtgs_applyLook` (`shaders/include/look.glsl`), qui applique le look courant et la lumière des néons. Quand le
   look vaut 0, la couleur ressort inchangée.
2. Le post masqué. En fin de rendu du monde, `LookPost` dessine un triangle plein écran par look visible. Le test de
   stencil limite chaque passe aux pixels de son look.

Le moteur reprend la lightmap par monde vu (`ViewLightmaps`), le brouillard par vue et le stencil des portails.
L'injection passe par le même point que la découpe des vues au plan du portail (`MixinShaderStage`).

### Profil de look

Un profil est un fichier `assets/gtgs_backrooms/look_profiles/<nom>.json`. Il se lit en `LookProfile`, une simple
donnée, rangée par dimension dans `LookProfiles`. Un fichier invalide est refusé et journalisé. Si deux profils visent
la même dimension ou le même identifiant, le second est ignoré.

| Champ | Rôle | Plage |
|-------|------|-------|
| `dimension` | Dimension du profil, obligatoire | Identifiant |
| `id` | Look porté par le stencil et l'uniforme `gtgs_LookId`, obligatoire | 1 à 7 |
| `tint` | Teinte et force de l'étalonnage, `[r, g, b, force]` | Couleur de 0 à 4, force de 0 à 1 |
| `desaturation`, `grain`, `flicker` | Désaturation, grain, vacillement de la lumière de l'écran | 0 à 1 |
| `lightmap` | `floor`, `warmth`, `max_brightness` | 0 à 1, plancher sous le plafond |
| `fog` | `color`, `start`, `end` | `end` au-delà de `start` |
| `post` | `vignette`, `chromatic`, `bloom` (booléen), `bloom_threshold`, `bloom_strength` | 0 à 1, force du bloom de 0 à 2 |
| `neons` | `block`, `radius`, `color`, `strength`, `flickering`, `dead` | Portée de 0,5 à 64, force de 0 à 2, parts de somme au plus 1 |

Une section absente garde le comportement vanilla. Le profil du Level 0 met à zéro le vacillement de l'écran et les
parts de tubes qui vacillent ou meurent. La mécanique est complète, et relever ces valeurs suffit.

Les options de confort retirent une partie du profil au chargement (`LookProfile.comfort`). Les effets de dimension
viennent du contenu. `LevelZeroEffects` déclare ceux du Level 0 : ni ciel, ni nuages, et le brouillard du profil.

### Patch des shaders

Le patch enveloppe la `main` de chaque shader ciblé. La `main` vanilla devient `gtgs_vanilla_main`, et une nouvelle
`main` l'appelle entre le code des injections. Le corps vanilla n'est jamais réécrit.

Un patch est un fichier `assets/gtgs_backrooms/shader_patches/<nom>.json`, lu en `ShaderPatch`. Son champ `stage` vaut
`vertex` ou `fragment`. Les champs `targets` et `excludes` désignent les shaders par des motifs à `*`, et une exclusion
l'emporte. Le champ `imports` liste les bibliothèques insérées en tête, et `inject` les injections.

| Point d'injection | Effet |
|-------------------|-------|
| `head` | Code inséré après la `#version` active |
| `main_before` | Code joué avant la `main` vanilla |
| `main_after` | Code joué après la `main` vanilla |
| `output` | Fragment seulement : la sortie passe par la fonction nommée dans `function` |

Le mod livre deux patchs, qui visent `rendertype_*` et `particle` sauf le texte, l'interface, le contour des entités et
les nuages. `world_fragment.json` insère `neon_schedule.glsl` et `look.glsl`, puis fait passer la sortie par
`gtgs_applyLook`. `world_vertex.json` ajoute cinq sorties aux vertex shaders.

- `gtgs_ViewPos` : la position en espace caméra.
- `gtgs_WorldRel` : la position relative à la caméra, dans les axes du monde.
- `gtgs_Uv` et `gtgs_Normal` : les coordonnées de texture et la normale.
- `gtgs_Terrain` : le drapeau du terrain, en `flat`.

Le code d'une injection peut contenir des jetons, remplacés selon ce que le shader déclare. `${position}` vaut
`Position + ChunkOffset` dans un shader du terrain, `Position` ailleurs. `${uv}` vaut `UV0`, ou `vec2(-1.0)` sans lui.
`${normal}` vaut `Normal`, ou `vec3(0.0)`. `${terrain}` vaut `1.0` dans un shader du terrain, `0.0` ailleurs.

Un fragment ne reçoit son patch que si le vertex du même programme a reçu le sien (`PatchedVertexStages`). Sinon, il
lirait des entrées que rien n'écrit, et le programme ne se lierait plus. Il reste alors vanilla.

### Lightmap et brouillard

La courbe du profil s'applique à chaque point de la lightmap, une fois par tick et par monde (`DimensionLightmap`). Le
plancher relève le noir, le plafond borne la lumière, et la chaleur monte le rouge en baissant le bleu. Chaque vue
reçoit la lightmap du monde qu'elle montre.

Le brouillard du profil remplace celui de vanilla sur le terrain (`DimensionFog`). Vanilla garde la main quand la caméra
est dans un fluide ou la neige poudreuse, sous cécité ou obscurité, ou sous un brouillard épais imposé.

### Néons

Chaque monde client tient l'index de ses néons (`NeonIndex`, `NeonMap`). Il se remplit à l'arrivée de chaque chunk, en
ne parcourant que les sections dont la palette contient le bloc de néon. Il suit ensuite chaque `setBlockState` du monde
client et oublie les colonnes déchargées.

Pour chaque vue, `NeonLights` retient les néons les plus proches de sa caméra, huit ou trente-deux selon la qualité. Il
les envoie dans un bloc d'uniformes `std140`, `gtgs_NeonBlock`, avec leur position, leur portée, leur couleur et leur
état du moment. Il y a un tampon par niveau de vue, et rien n'est alloué par image.

Chaque fragment ajoute la lumière des néons retenus, qui décroît avec le carré de la distance rapportée à la portée.
Cette lumière est multiplicative. Elle relève la couleur déjà éclairée par la lightmap, sans éclairer le noir complet.
La somme est plafonnée à +80 %, pour qu'un couloir dense ne vire pas au blanc.

#### Calendrier des néons

L'état d'un néon dépend de sa position, de la graine de sa dimension et du tick du monde (`NeonSchedule`). La plupart
des tubes brillent sans faillir, et les parts du profil fixent ceux qui vacillent et ceux qui sont morts. Un tube qui
vacille a des coupures de quatre ticks, tirées au sort par créneau. Éteint, il garde 12 % de son intensité.

La même fonction existe en GLSL, dans `shaders/include/neon_schedule.glsl`. Les deux reposent sur un hachage entier de
32 bits, sans flottant dans la décision. L'image lit la version GLSL, le son la version Java.

La face de chaque tube du terrain suit son état par ce jumeau GLSL. Le shader la reconnaît au drapeau du terrain et au
rectangle de la texture du néon dans l'atlas (`gtgs_NeonFace`), recalculé par `NeonFaces` à chaque rechargement des
modèles. Il retrouve le bloc du tube depuis la caméra, donnée en bloc entier plus un reste. La précision ne se perd donc
pas loin de l'origine.

Les sons liés à l'image ajoutent jusqu'à quatre perturbations au bloc d'uniformes. Chacune retire à la face des tubes à
sa portée ce que le son leur retire, même aux tubes que la sélection n'a pas retenus (voir
[AUDIO.md](AUDIO.md#liaison-son-image)). Si le jumeau échoue à sa vérification, il est coupé pour la session. La face
d'un néon retenu suit alors l'état envoyé dans le bloc.

### Post masqué et bloom

En fin de rendu du monde, main comprise, `LookPost` copie l'image dans le framebuffer `gtgs_backrooms:screen_copy`. Il
dessine ensuite un triangle plein écran par look visible dont le profil a un post. Le stencil rejette les autres pixels
avant le fragment shader, et le programme `look_post` n'a donc ni `discard` ni `gl_FragDepth`.

Le post ajoute la vignette et l'aberration chromatique. En qualité complète, il ajoute le bloom, calculé sur la copie de
l'écran. Les pixels au-dessus du seuil sont extraits à demi-résolution, puis un flou séparable en deux passes les étale
à quart de résolution. Le post ajoute ce flou, pesé par la force du profil, sur les seuls pixels du look.

Les framebuffers se déclarent dans `assets/gtgs_backrooms/framebuffers/*.json` : nom, échelle, format `RGBA8`, profondeur
et filtrage. Ils se créent à la demande et ne se recréent qu'au redimensionnement. Le triangle plein écran est un tampon
persistant (`FullscreenTriangle`).

L'image figée d'une vue lointaine (`FrozenViews`) prend les bits de look de sa dimension. Le post la traite comme la vue
qu'elle remplace.

### Qualité

La qualité légère retient huit néons par vue, sans bloom. La qualité complète en retient trente-deux et dessine le
bloom. En mode `auto`, `LookQuality` choisit la qualité légère sur une carte graphique intégrée, reconnue à la chaîne
`GL_RENDERER` du pilote (`intel`, `llvmpipe`, `swiftshader`). Le choix est journalisé, et le calcul acoustique le suit
par défaut.

### Stencil

Le stencil des portails porte le niveau d'imbrication et le look de chaque pixel (`StencilLookMask`).

| Bits | Sens |
|------|------|
| `0x03` | Niveau d'imbrication : 0 pour le monde du joueur, 1 pour une vue, 2 pour une vue imbriquée |
| `0x1C` | Look de la dimension que montre le pixel : 0 pour vanilla, 1 à 7 sinon |
| `0x40`, `0x80` | Sous un pack de shaders seulement : main du joueur et cadre imbriqué |

Tout test de niveau masque avec `StencilLookMask.LEVEL_MASK`. Une vue n'écrit ses bits de look que sur les pixels
qu'elle prend, ceux qui passent du niveau n à n+1, et le retour de niveau les laisse. En fin d'image, les bits de look
d'un pixel valent donc le look de la vue la plus profonde qui y est visible.

L'effacement n'écrit que les bits du masque courant, d'où un masque `0xFF`. Il a lieu au début du rendu du monde, même
sans portail visible, puis au début de la passe des portails. L'option `debugStencil` affiche le stencil à la place de
l'image.

### Une image de bout en bout

1. En tête du rendu du joueur, `LookFrame` ouvre l'image. `LookState.beginWorld` pose le profil du monde du joueur, la
   caméra et l'horloge commune avec l'audio (`CueClock`). Le stencil est effacé au niveau 0 avec le look du joueur.
2. Les perturbations des sons liés sont mises à jour, et `NeonLights` remplit le bloc des néons proches du joueur.
3. La lightmap et le brouillard prennent les réglages du profil.
4. Les couches opaques et les entités se dessinent. `ShaderProgram.bind` ne renvoie le look que si sa version a changé.
5. Pour chaque portail visible, la zone du cadre passe au niveau n+1 et prend le look de la dimension vue.
   `LookState.push` et `NeonLights.push` posent le look et les néons de la vue, qui se dessine avec sa lightmap et son
   brouillard. Le niveau revient ensuite à n, et les bits de look restent.
6. Les translucides, les particules, les nuages et la météo se dessinent, puis la main.
7. `LookPost` copie l'écran, calcule le bloom et dessine un triangle par look visible.
8. `LookState.endWorld` remet le look vanilla pour l'interface.

### Coupe-circuits et état GL

Chaque coupe-circuit est journalisé une fois.

- Une source patchée que le pilote refuse reste vanilla (`ShaderCompileCheck`).
- Une exception pendant le post le coupe pour la session, et l'étage 1 reste en place.
- Sans stencil, il n'y a ni post ni bits de look.
- Un framebuffer non déclaré fait sauter sa passe.

Les mesures GPU (`GpuTimer`) n'ouvrent qu'une requête à la fois, et aucune sous un pack. Chaque passe rend l'état GL
que son appelant tenait. Hors pack, c'est l'état de repos de vanilla : test de profondeur et
stencil coupés, écriture de la profondeur active. Dans une vue sous un pack, le test de stencil de la vue doit rester
posé, car la suite de la vue en dépend. L'option `checkGlState` relit l'état au pilote autour de chaque passe et
journalise chaque écart.

Le code se range en `patch/`, `profile/`, `state/`, `light/`, `quality/`, `post/`, `fbo/` et `client/`. Tout sauf
`client/`, la colle OpenGL, est de la logique pure.

## Ce que vérifie le patch des shaders

`ShaderPatcherTest` relit les 110 shaders de base de Minecraft 1.21.1 dans le jar du jeu, compilés comme le jeu les
compile. Il les compare à l'inventaire `src/test/resources/shader_inventory_1_21_1.json`, si bien qu'une mise à jour du
jeu se voit. Les 55 fragment shaders ont une seule sortie `fragColor`, une seule `main` et aucune directive
conditionnelle. Tous sont patchables, comme les 55 vertex shaders, et les 39 qui font `discard` le gardent.

Une source hors de cette forme reste vanilla, journalisée une fois. C'est le cas d'une version autre que 150, de deux
`main` ou deux sorties, d'une directive conditionnelle ou inconnue, ou d'une macro dans la sortie ou la signature de
`main`. Il n'y a jamais de patch « au mieux », même pour un shader remplacé par un pack de ressources.

`PatchedShadersGoldenTest` passe chaque shader de base par la découpe des portails puis par les patchs. Il compare
l'empreinte des 90 sources changées à `src/test/resources/patched_shaders_1_21_1.json`. Un changement voulu se recopie
depuis la table que donne le test.

Au chargement, `ShaderCompileCheck` fait compiler chaque source patchée par le pilote avant de la donner au jeu. Une
source refusée est remplacée par la source vanilla : un look manque plutôt qu'un monde rose. Chaque rechargement
journalise `[Look] N etage(s) de shaders patche(s) compile(s) par le pilote, M refuse(s)`, et 72 étages portent le look
en 1.21.1. Cette ligne est la seule preuve que l'étage 1 est en place, car la lightmap et le post changent l'image sans
lui.

`NeonScheduleTest` vérifie `NeonSchedule` sur les 573 vecteurs de `src/test/resources/neon_schedule_vectors.json`,
écrits par un programme indépendant. Le client vérifie `neon_schedule.glsl` sur les mêmes vecteurs, sur le processeur
graphique, quand l'option `verifyNeonShader` donne le chemin du fichier.

## Compatibilité

### Packs de shaders

Sous un pack, les shaders du pack remplacent ceux de vanilla, et sa passe finale ignore le stencil. Le mod reconnaît un
pack sans nommer de mod (`ShaderPackState`). Juste avant la couche translucide, les programmes que vanilla donne aux
entités et aux translucides ne sont plus ceux du jeu.

Les vues de portail se dessinent alors par le pack, après son image. Leur fonctionnement et leurs limites sont décrits
dans [PORTAILS.md](PORTAILS.md). L'option `shaderCohabitation` des portails peut les couper, et les cadres restent alors
sombres.

### Module `compat/iris`

Tant que le joueur est dans une dimension à look, le gestionnaire de pipelines d'Iris rend son pipeline vanilla pour
tout ce qui se dessine, vues comprises (`MixinPipelineManager`). Le look du mod s'applique alors comme sans pack.

Une image n'utilise qu'un pipeline. Le mod de terrain qu'exige Iris n'a qu'un format de maillage à la fois, et deux
pipelines dans la même image dessinent le terrain en éclats. Dans le Level 0, l'Overworld vu par un portail est donc
vanilla.

Chaque pipeline d'Iris pose des réglages globaux à sa création, qu'un pipeline repris du cache ne repose pas. À chaque
bascule, le module garde ceux du pipeline quitté et repose ceux de l'autre (`PipelineSettings`). Le mod de terrain
recharge ses shaders de sections, et le terrain se reconstruit dans le nouveau format.

La bascule se fait au rendu du joueur, jamais au milieu d'une vue, après une traversée comme après un changement de
monde vanilla. Le pipeline du pack reste dans le cache d'Iris, sans recompilation. Sur la machine de référence, l'image
de bascule dure de 96 à 117 ms en entrant et 52 ms en sortant, contre environ 1 s pour couper puis rallumer le pack.

Depuis l'Overworld sous un pack, une vue du Level 0 reste rendue par le pack. Le post pose le look entier de sa
dimension sur les pixels du cadre (`LookPost.renderUnderShaderPackView`).

Le module ne s'applique que dans la plage d'Iris vérifiée, `>=1.8.8 <=1.8.14-beta.1` (`IrisVersion`). Hors de la plage,
un message traduit prévient le joueur à chaque connexion (`IrisVersionNotice`). Le pack rend alors aussi le Level 0, et
le post pose le look sur son image. L'option `replaceShaderPack` coupée donne le même résultat.

Les mixins du module sont déclarés dans `gtgs_backrooms.compat.mixins.json` et filtrés par `CompatMixinPlugin`. Iris et
Sodium sont compilés en `modCompileOnly`, jamais embarqués ni requis.

### Mod de terrain

Un mod qui dessine le terrain par ses propres shaders se reconnaît à sa grille vanilla de distance nulle. Les shaders
patchés n'atteignent pas ses blocs. L'étage 1 passe alors dans le post, pour le terrain comme pour les entités
(`LookState.shadersCarryLook`). La lumière des néons et la face des tubes manquent, le reste du look demeure.

Iris exige un tel mod. Dans le Level 0 sous un pack, le rendu est donc celui de ce cas.

### Résumé

| Configuration | Look | Lumière et face des néons | Vues de portail |
|---------------|------|---------------------------|-----------------|
| Vanilla, ou mods qui ne dessinent pas le terrain | Complet | Oui | Oui |
| Mod de terrain seul, ou Iris sans pack | Étage 1 par le post | Non | Oui |
| Pack actif, joueur dans le Level 0 | Celui du mod, étage 1 par le post | Non | Oui, vanilla |
| Pack actif, joueur hors des Backrooms | Posé par le post dans chaque vue d'une dimension à look | Non | Rendues par le pack |
| Pack actif, `replaceShaderPack` coupée ou Iris hors de la plage | Posé par le post sur l'image du pack | Non | Rendues par le pack |

## Réglages

Les options du moteur activent ou coupent le moteur, choisissent la qualité, règlent le confort et ouvrent les outils
de débogage. Leur liste est dans [OPTIONS.md](../reference/OPTIONS.md#rendu), et celle de la cohabitation des vues avec
un pack dans la section [Portails](../reference/OPTIONS.md#portails).

## Diagnostic

La commande du client `/backrooms_state` écrit dans le journal l'état de la dernière image et des sons traités. Sa
sortie est décrite dans [AUDIO.md](AUDIO.md#diagnostic).

## Limites

- Sous un pack, chaque bascule reconstruit le terrain, et les sections réapparaissent en quelques images.
- Hors des Backrooms, aucun mode ne rend les vues en vanilla sous un pack, car deux pipelines dans la même image
  dessineraient le terrain en éclats.
- Sous un pack, une vue du Level 0 vue de l'Overworld n'a ni la lumière des néons ni la lightmap du profil. Son post
  alloue environ 0,3 Kio par appel, d'origine non élucidée.
- Un pack se détecte au milieu du rendu du joueur. Ce qui se décide avant lit donc l'état de l'image précédente, d'où
  une image de retard à l'activation ou à la coupure d'un pack.
- L'index des néons relève les chunks à leur arrivée. Un profil rechargé qui change de bloc de néon ne vaut que pour les
  chunks reçus ensuite.
- Au-delà des néons retenus pour une vue, un tube brille sans éclairer autour de lui. La portée 8 et la force 0,6 du
  Level 0 sont un réglage de départ, à ajuster à l'œil.
- Le bloom n'est masqué qu'à la composition. Au bord d'un cadre, le halo d'un tube peut déborder de quelques pixels vers
  l'intérieur du cadre, jamais sur les pixels vanilla.
- L'aberration chromatique lit les pixels voisins sans vérifier leur look. Au bord d'un cadre, une frange de deux ou
  trois pixels mêle les deux côtés, ce qui est accepté pour un décalage de 3 % au plus.
- Une vitre ou de l'eau devant un cadre porte les bits de look de la vue derrière elle, et le post la traite avec la vue.
- La face d'un néon se reconnaît à sa texture. Un pack de ressources qui lui donne une texture par face sort de cette
  règle, et un bloc qui partagerait sa texture suivrait le calendrier. Un néon tenu ou porté par une entité suit l'état
  vanilla.
- Même moteurs coupés, un pixel d'une épée tenue peut différer d'un rendu sans le mod, à la frontière de deux texels.
  La cause la plus probable est la disposition de l'atlas, que les textures du mod décalent.

## Tests

La stratégie de test et la couverture sont décrites dans [TESTS.md](../dev/TESTS.md). La colle OpenGL de
`core/render/client/` y est nommée, et le reste du moteur est couvert à 100 %.

La logique pure a ses tests unitaires, de `ShaderPatcherTest` à `NeonMapTest`. `MixinTargetsTest` vérifie
que les mixins du moteur s'appliquent. Dans un vrai client, `LevelZeroClientTests` vérifie le look du Level 0, les néons
envoyés, la couleur du brouillard et le jumeau GLSL. `EngineRulesClientTests` vérifie le seuil du bloom, le stencil d'un
framebuffer recréé et le sampler du dessin plein écran. `PortalViewClientTests` vérifie le look dans un cadre et
l'absence de couture à la traversée.

Avant de mesurer une image à la main, on vérifie deux preuves : le bilan des étages patchés dans le journal, et le
look attendu sur tout l'écran en mode `debugStencil`. Hors du cadre d'un portail, un monde sans profil se rend comme sans le mod, à
2/255 près. Ces écarts touchent quelques arêtes et entités, car le pilote recompile les shaders enveloppés.

Sur la machine de référence en 1080p, le moteur entier coûte environ 0,16 ms de GPU par image, dont 0,12 ms pour
l'étage 1. Le post coûte environ 0,05 ms de processeur, sans allocation. Le coût sur une carte graphique intégrée reste
à mesurer.
